# J-003 — Java Environment, Gravity, Wind V1 ve Relative Flow Migration

## Kaynak, dal ve kapsam

Git root `OpenRocket`; başlangıç dalı `migration/j-002-quaternion`, temiz HEAD
`7b9d65ea001cbfb54eb8bb7fcaa48f02429b5b45` doğrulandı. J-001
`6bbe1387c2f1d5308bde694385a3182d45982fb4` ancestor olarak korundu.
J-003 dalı doğrudan bu J-002 commit'inden `migration/j-003-environment-relative-flow`
adıyla açıldı. Main, frontend ve uzak repository değiştirilmedi.

Otorite `roketsim-native-engine/src/roketsim_native/` altındaki kabul edilmiş
Python implementation, ilgili testler ve `docs/archive/nat/` kayıtlarıdır.
`environment-model-freeze.md`, `nat-009e-atmosphere-vv.md` ve ADR-007 sınırları
korunur; OpenRocket implementation kullanılmadı. Python production/test,
J-001/J-002 math, frames, SI ve tarihsel ADR'ler değiştirilmedi. Yeni fizik yoktur.

## API eşlemesi

Java kaynak kökü `roketsim-core/src/main/java/org/roketsim/core/`.
Environment ve flightconditions mevcut tek Java 17 library'nin paketleridir;
yeni Gradle subproject veya runtime dependency eklenmedi.

| Python modülü / public API | Java karşılığı |
|---|---|
| environment/altitude.py: orthometric_from_ellipsoidal | environment.Altitude.orthometricFromEllipsoidal(h, N) |
| ellipsoidal_from_orthometric | Altitude.ellipsoidalFromOrthometric(H, N) |
| agl_from_orthometric | Altitude.aglFromOrthometric(H, ground) |
| orthometric_from_agl | Altitude.orthometricFromAgl(AGL, ground) |
| atmospheric_geometric_from_orthometric | Altitude.atmosphericGeometricFromOrthometric(H) |
| geopotential_from_geometric | Altitude.geopotentialFromGeometric(Z) |
| geometric_from_geopotential | Altitude.geometricFromGeopotential(H_gp) |
| environment/atmosphere.py: DryAirAtmosphereState | environment.DryAirAtmosphereState(temperatureK, pressurePa, densityKgM3) |
| USStandardAtmosphere1976Lower.evaluate | environment.USStandardAtmosphere1976Lower.evaluate(geopotentialHeightM) |
| AtmosphereDomainError | environment.AtmosphereDomainError; requested/minimum/maximumGeopotentialHeightM() |
| environment/air_properties.py: DryAirProperties | environment.DryAirProperties(speedOfSoundMS, dynamicViscosityPaS, kinematicViscosityM2S, specificHeatRatio) |
| DryAirPropertiesCalculator.evaluate | environment.DryAirPropertiesCalculator.evaluate(atmosphereState) |
| AirPropertiesDomainError | environment.AirPropertiesDomainError; fieldName()/value() |
| environment/gravity.py: ConstantGravityModel.evaluate | environment.ConstantGravityModel.evaluate() |
| environment/wind.py: NoWindModel.evaluate | environment.NoWindModel.evaluate() |
| ConstantWindModel(airmass_velocity_world_m_s).evaluate | environment.ConstantWindModel(airmassVelocityWorldMS).evaluate() |
| flight_conditions/relative_flow.py: RelativeFlowCalculator.evaluate | flightconditions.RelativeFlowCalculator.evaluate(rocketVelocityWorldMS, airmassVelocityWorldMS) |
| flight_conditions/basic.py: BasicFlightConditions | flightconditions.BasicFlightConditions(airspeedMS, mach, reynolds, dynamicPressurePa) |
| BasicFlightConditionsCalculator.evaluate | flightconditions.BasicFlightConditionsCalculator.evaluate(relativeVelocityWorldMS, atmosphereState, airProperties, referenceLengthM) |
| FlightConditionsDomainError | flightconditions.FlightConditionsDomainError; fieldName()/value() |

Java camelCase adları yalnız dil uyarlamasıdır. Vektör girişleri J-001
`Vector.of(..., 3, name)` yolunu kullanır; mevcut desteklenen input temsilleri ve
finite/shape denetimi korunur. Çıktılar immutable `Vector`; array çıkışları defensive
copy'dir. ConstantWindModel giriş ve her evaluate çıkışında independent vector
oluşturur. Calculator/provider'lar parametresiz ve stateless'tir.

Üç result record Python frozen/slotted dataclass'ları gibi yalnız snapshot'tır;
constructor'a yeni validation konulmadı. Domain doğrulaması ilgili calculator'a
aittir. Böylece tüketilmeyen basınç, T, mu veya gamma alanı upstream otoriteye
dönüştürülmez. Java scalar parametreler `double`; Python keyword-only çağrılar
Java'nın adlandırılmış method parametrelerine uyarlanmıştır, default eklenmemiştir.

## Model ve numerical contract

- Bütün iç birimler SI; WORLD ENU East/North/Up, BODY ve ayrı nose-tip x_geo
  metadata'sı değişmedi. Atmosfer absolute height API'si WORLD z değildir.
- h_ellip = H_orth + N; AGL = H_orth − H_ground. Uyumlu datum/girdi otoritesi
  caller'a aittir. Geoid/terrain lookup yapılmaz. Negatif AGL geçerlidir.
- Z_atm := H_orth yalnız kabul edilmiş Native MODEL DECISION köprüsüdür;
  ellipsoid/orthometric/geometric/geopotential datums evrensel olarak eşitlenmez.
- USSA76 dönüşümleri R*(Z/(R+Z)) ve R*(H/(R−H)) olarak ratio-first kalır.
  Exact denominator zero ve nonfinite input/output generic hatadır. Altitude
  katmanı atmosphere domain clamp'i yapmaz; matematiksel finite uçlar korunur.
- Atmosfer domain'i geopotential **[-5000, 84852] m**, uçlar dahildir. İç sınır
  upper layer'a aittir; terminal 84852 m yeni layer yaratmaz. İlk base 0 m,
  sonraki T/p base'leri önceki endpoint'ten recursive türetilir. Lapse exact sıfırsa
  exponential, diğer durumda power-law kullanılır; Python işlem sırası korunur.
- Geometric 86000 m → geopotential 84852.04584490575 m domain dışıdır.
  Geopotential 84852 m → geometric 85999.95290624202 m ayrı accepted sınırdır.
- Dry air yalnız supplied finite positive T/rho kullanır; pressure okunmaz.
  a=sqrt(gamma*R)*sqrt(T), mu=beta*sqrt(T)*(T/(T+S)), nu=mu/rho; factored
  arithmetic ve fixed gamma korunur. Atmosfer aralığı air calculator'a eklenmez.
- Gravity yalnız [0,0,−g0] m/s²; konum/zaman/custom-g girişi yoktur. Wind WORLD
  TOWARD air-mass hızıdır; dikey bileşen ve büyüklük korunur, normalization yoktur.
- V_relative_WORLD = V_rocket_WORLD − V_airmass_WORLD. Subtraction overflow
  nonfinite validation hatasıdır; relative-flow calculator norm üretmez.
- Flight conditions V/a, V*referenceLength/nu ve .5*rho*V*V sırasını korur.
  rho/a/nu/L önce finite, sonra positive doğrulanır. Zero speed geçerlidir fakat
  prerequisite denetimini atlamaz. Positive-speed derived underflow zero fallback
  değildir. T/p/mu/gamma bu calculator'da tüketilmez.
- `referenceLengthM` herhangi bir açık positive characteristic length'tir;
  gelecekteki drag evaluator'ın `aerodynamicLengthM` Reynolds hesabı değildir.

Python ValueError generic validation Java IllegalArgumentException'a eşlenir.
Finite semantic domain hataları kendi üç subclass'larıyla korunur; source'ta
bulunmayan error_code eklenmedi. Atmosphere error requested/min/max, diğer ikisi
field/value taşır. Raw NaN/Inf generic ailede kalır. Validation ordering korunur.
Java/Python float message formatting'i bire bir metin kontratı olarak sunulmaz.

## Sabitler ve provenance

| Java otoritesi | Değer | Kabul edilmiş kaynak |
|---|---|---|
| Altitude.USSA76_GEOPOTENTIAL_EARTH_RADIUS_M | 6356766 m | altitude.py, USSA76 Eq.17–19; WGS84 değil |
| USStandardAtmosphere1976Lower.STANDARD_SEA_LEVEL_TEMPERATURE_K | 288.15 K | atmosphere.py, USSA76 lower baseline |
| STANDARD_SEA_LEVEL_PRESSURE_PA | 101325 Pa | aynı kaynak |
| STANDARD_GRAVITY_M_S2 | 9.80665 m/s² | aynı kaynak; gravity tek shared authority kullanır |
| DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K | 287.053 J/(kg K) | aynı frozen Native kuru hava baseline |
| layer heights / lapse rates | 0/11/20/32/47/51/71/84.852 km; −.0065/0/.001/.0028/0/−.0028/−.002 K/m | atmosphere.py private accepted USSA76 layer data |
| DryAirPropertiesCalculator.DRY_AIR_SPECIFIC_HEAT_RATIO | 1.40 | air_properties.py fixed dry-air karar |
| SUTHERLAND_BETA / SUTHERLAND_CONSTANT_K | 1.458e-6 kg/(m s sqrt(K)); 110.4 K | air_properties.py, Sutherland frozen kaynak |

Fixtures production default yapılmadı; global tolerance eklenmedi.

## Hypot ve Math/StrictMath araştırması

İlk D-grubu testinde nested `Math.hypot(Math.hypot(-12,3),-4)`
12.999999999999998 verdi; Python `math.hypot(-12,3,-4)` exact 13 verir.
Bu fark exact direction-independence fixture'ını bozduğu için assertion
gevşetilmedi. Yalnız flight calculator private `hypot3`'ü, Python'ın stable tam 3D
Euclidean norm contract'ını power-of-two scaling, compensated square sum ve
FMA sqrt correction ile uygular. Exact zero branch dışında threshold veya
epsilon yoktur. J-001 naive vector magnitude ve quaternion norm değiştirilmedi.
512 seeded nontrivial vektör, source analytical fixtures ve large/small-scale
başarılı uçlarla toplam 518 condition normu actual Python'a sayısal olarak tam eşitti.
Bu örneklem, tüm platform/girdiler için bitwise equality iddiası değildir.

Math ve StrictMath exp/pow/sqrt ayrıca actual Python math ile 39 input'ta
karşılaştırıldı. −3900 m power-law argümanında Python pow=1.5576309277678835,
Math.pow=1.5576309277678833, StrictMath.pow=1.5576309277678835 bulundu.
11750 m isothermal exp argümanı −0.1182663396407345 için Python
0.8884593891450726, iki Java yolunda 0.8884593891450725 bulundu.
StrictMath her quantity'yi Python'a eşitlemez; implementation Math olarak kaldı.
En büyük diagnostic exp/pow absolute fark 2.220446049250313e-16; sqrt farkı sıfır.
İşlem sırası değişmedi, source test tolerance artırılmadı. İki atmosphere query'sindeki
son-bit farkı rho/nu/Re/q'ya beklenen biçimde taşındı ve ayrı ölçüldü.

## Çalıştırılmış doğrulama

Windows 11 (10.0.26200); Python 3.12.4 / NumPy 2.5.2;
Eclipse Temurin JDK/javac 17.0.20.1+1; Gradle Wrapper 8.12.1.
JDK/cache seçimi process-local; installation path build'e gömülmedi.

| Gate | Gerçek sonuç |
|---|---|
| A: AltitudeTest | 18 passed |
| B: AtmosphereAirTest | 32 passed |
| C: GravityWindTest | 8 passed |
| D: FlowConditionsTest | 12 passed, açıklanan nested-hypot düzeltmesinden sonra |
| J-001 FoundationTest | 25 passed |
| J-002 QuaternionTest | 62 passed |
| Complete Java Core clean build | 157 passed; 0 failure/error/skipped |
| Python environment + flight_conditions + atmosphere V&V | 358 passed |
| Full Python repository regression | 1491 passed; 0 failed/skipped |
| J-003 actual Python–Java parity | 2485 success + 138 error case passed |
| J-001 vector parity | 6 passed |
| J-002 quaternion parity | 95 success + 53 error case passed |
| Independent Gradle JAR consumer | foundation/quaternion/environment PASS |
| Gradle runtimeClasspath / verifyProductionDependencies | no dependencies / PASS |
| jdeps --print-module-deps | java.base only |

`verification/environment_parity.py` mevcut Python modüllerini çağırır ve test-only
`EnvironmentParityProbe` ile karşılaştırır. Bütün public operations, constants,
50 m domain grid'i, knot nextDown/exact/nextUp, singularities, computed overflow/
underflow, domain/raw-nonfinite error ayrımı ve error metadata test edilir.
Atmosphere/air/flight zincirinin 11 quantity'si ayrı karşılaştırılır; summary-only
parity yoktur. Shape/nonfinite vektörler unit ve parity testlerinde kapsanır.
2500'e yakın successful case'den **2475 case** numerically exact'tir;
kalanlar iki atmosphere query, diagnostic hypot ve Math/StrictMath son-bit farklarıdır.

Quantity-specific test contract'ları mevcut Python testlerinden alınır:
atmosphere rel=2e-14/abs=0, air rel=3e-15/abs=0, flight rel=1e-14/abs=0;
datum/flow/wind/gravity/constants exact numerical comparison. Java altitude
round-trip testleri mevcut rel=1e-14/abs=1e-12 ve 86 km fixture abs=3e-11'i kullanır.
Yuvarlanmış source table toleransı Java parity toleransı yapılmadı.

Zincirde maximum measured relative differences: T/a/mu/V/M/gamma sıfır;
p=1.8440342263535823e-16, rho=2.5321456459005487e-16,
nu=3.1123713484264934e-16, Re=2.4608629184765665e-16,
q=3.8356762446777534e-16. Maximum absolute p farkı 2.9103830456733704e-11 Pa.
Standalone air ve 518 başarılı flight-condition case'inin bütün sonuçları exact'tir.
Error case'lerde generic/subclass ayrımı, field ve structured value/bounds eşleşir.

## Yeniden çalıştırma ve sınırlar

JDK 17 ve mevcut wrapper/cache hazırlanmış ortamda Java core dizininden:

```powershell
.\gradlew.bat clean build --offline --no-daemon
.\gradlew.bat dependencies --configuration runtimeClasspath --offline --no-daemon
.\gradlew.bat -p verification/consumer clean run "-PcoreJar=$PWD\build\libs\roketsim-core-0.1.0.jar" --offline --no-daemon
jdeps --print-module-deps build/libs/roketsim-core-0.1.0.jar
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/environment_parity.py --java "$env:JAVA_HOME\bin\java.exe"
```

Python engine dizininden focused scope:

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/unit/environment tests/unit/flight_conditions tests/validation/test_atmosphere_vv.py -p no:cacheprovider -q
.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q
```

Parite aracı ve Python reference yalnız verification katmanındadır, production
JAR'a girmez. Core java.base dışında module/runtime dependency taşımaz;
OpenRocket/Swing/Python/JSON/CLI/network bağımlılığı yoktur. Historik J-001'in
22 missing-Javadoc uyarısı non-blocking olarak korunur; J-003 yeni warning eklemez.
Quaternion parity'deki expected overflow negative testinin NumPy RuntimeWarning'i
başarı çıktısı/fallback değildir.

Yeni model TBD'si yoktur. Platformlar arası son-bit farklılıkları için yukarıdaki
mevcut quantity contracts geçerlidir; tüm finite double girdilerde correct-rounding
veya bitwise parity garantisi verilmez. Higher atmosphere, humidity, variable gamma,
geoid/terrain, WGS84/Coriolis, wind shear/gust/turbulence/profile, aero, geometry,
mass, propulsion, dynamics, RK4 ve simulation migration sonraki kontrollü
paketlerdir. Bu çalışma bunları uygulamaz; J-004 başlatılmaz.
