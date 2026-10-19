# J-007 — Initial State ve 3DOF Translational Dynamics

Doğrulama tarihi: 2026-10-19. Bu paket kabul edilmiş Python NAT-013/NAT-014
sözleşmelerinin Java 17 dil migration'ıdır; yeni fizik veya trajectory yürütmesi
değildir. Tüm aşağıdaki kabul gate'leri gerçek çalıştırmayla geçti.

## Başlangıç, otorite ve Git sınırı

Git root `OpenRocket`, başlangıç dalı `migration/j-006-aerodynamics`, HEAD
`de79850ab052163e0a48513aacabf464a658a904` ve temiz çalışma ağacı doğrulandı.
Uzak J-006 aynı SHA'daydı; local/remote J-007 dalı yoktu. J-001–J-006,
AUD-002 ve M-001 ancestry kontrolleri geçti. Doğrudan kabul edilmiş J-006'dan
`migration/j-007-3dof-dynamics` oluşturuldu; main ve tarihsel dallara geçilmedi.

Otorite kaynakları:

- `roketsim-native-engine/src/roketsim_native/dynamics/initial_state.py`
- `roketsim-native-engine/src/roketsim_native/dynamics/translational.py`
- `tests/unit/dynamics/test_initial_state.py`, `test_translational.py`
- `docs/archive/nat/nat-013-initial-launch-state.md`, `nat-014-3dof-translational-dynamics.md`
- ADR-007/ADR-008, mevcut Java Vector/Vectors/NumericalValidation/FrameConventions ve önceki migration doğrulamaları.

Python production/test dosyaları, arşivler, ADR'ler ve geçmiş doğrulamalar
değiştirilmedi. Frontend/OpenRocket fiziği uygulanmadı veya referans alınmadı.
Python kontrollü verification referansı olarak kalır; Java runtime bağımlılığı
veya fallback değildir.

## Exact Python → Java sorumluluk eşlemesi

Java kökü: `roketsim-core/src/main/java/org/roketsim/core/dynamics/`.
Her public tip ayrı dosyadadır; ilave üretim helper sınıfı/framework yoktur.

| Python otoritesi | Java dosyası / API |
|---|---|
| initial_state.InitialStateValidationError | InitialStateValidationError.java; errorCode/fieldName/value |
| initial_state.TranslationalState3DOF | TranslationalState3DOF.java; positionWorldM, velocityWorldMS |
| initial_state.LaunchConditions3DOF | LaunchConditions3DOF.java; initialPositionWorldM, initialVelocityWorldMS, launchDirectionWorldUnit |
| initial_state.LAUNCH_DIRECTION_UNIT_NORM_ATOL | LaunchConditions3DOF.LAUNCH_DIRECTION_UNIT_NORM_ATOL |
| initial_state.InitialStateBuilder.build | InitialStateBuilder.java; build(launchConditions) |
| translational.ThrustDirectionModel | ThrustDirectionModel.java; FIXED_LAUNCH_DIRECTION |
| translational.AerodynamicForceModel | AerodynamicForceModel.java; BASELINE_CD0_DRAG_ONLY |
| translational.TranslationalDynamicsModelProfile | TranslationalDynamicsModelProfile.java |
| translational.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE | TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE |
| translational.DynamicsEvaluationError | DynamicsEvaluationError.java; errorCode/fieldName/value |
| translational.TranslationalDynamicsInputs | TranslationalDynamicsInputs.java; yedi alanlı snapshot |
| translational.TranslationalForces3DOF | TranslationalForces3DOF.java; üç stored contribution, netForceWorldN() derived |
| translational.TranslationalStateDerivative3DOF | TranslationalStateDerivative3DOF.java; positionDerivativeWorldMS, velocityDerivativeWorldMS2 |
| translational.TranslationalDynamicsResult | TranslationalDynamicsResult.java; forces, derivative, modelProfile; accelerationWorldMS2() derived |
| translational.TranslationalDynamicsEvaluator.evaluate | TranslationalDynamicsEvaluator.java; evaluate(state, launchConditions, inputs, modelProfile) |

Java records yalnız kabul edilmiş stored domain alanlarını taşır. Java
positional çağrılar Python keyword-only imzalarının dil uyarlamasıdır.
Stored vektörler mevcut immutable `Vector`'dır; alternatif Vector3 yoktur.
Numeric array/List girdilerine yönelik Object overload'ları aynı Vector.of
validation/copy yoluna delegasyon yapar; String bileşenler ADR-008 gereği
reddedilir. Scalar girdiler typed double'dır. Profile selector alanlarında
Object tutulması unsupported seçimlerin kabul edilmiş structured hata
yoluyla reddedilmesini sağlar; başarılı profil yalnız doğru enum'lardır.
Structured tuple-vector error değerleri immutable Java List ile temsil edilir.
Python'un tüm dinamik nesne tiplerinin Java typed API'de taklit edildiği
iddia edilmez. Hata mesajı repr/yazımı dile özgü olabilir; category/code/field/
value ve generic hata bağlamı karşılaştırılır.

## Frame, SI ve immutable state

WORLD local ENU: +X East, +Y North, +Z Up. BODY +Z longitudinal/thrust yönü
ve ayrı nose-tip origin/aft-positive x_geo conventions değiştirilmedi.
Position m, velocity m/s, acceleration m/s², force N, mass kg, q Pa,
reference area m², Cd0 boyutsuzdur. WORLD z mutlak veya geopotential altitude
değildir; atmosphere eşlemesi eklenmedi.

State yalnız position ve velocity taşır; launch state initial position,
initial velocity ve direction taşır. Tüm değerler explicit ve mandatory'dir.
Her vektör finite ve tam üç bileşenlidir; constructor'lar bağımsız kopyalar
üretir. Mutable caller array veya toArray çıktısı değiştirilince domain
değişmez. Builder yalnız yeni bağımsız position/velocity state üretir;
launch direction, time, acceleration, mass veya environment state'e eklenmez.

Launch norm mevcut Vectors.magnitude ile hesaplanır. Yerel NAT-013 politikası
`abs(norm-1) <= 1e-8`'dir. Norm tam sıfırsa INVALID_LAUNCH_DIRECTION,
finite nonzero norm tolerans dışındaysa NON_UNIT_LAUNCH_DIRECTION; field
launch_direction_world_unit ve verilen üç bileşen error value'dur.
Tolerans içinde kabul edilen yön normalize edilmez; bileşenleri korunur.
Raw nonfinite bileşenler generic validation yolundadır. Global tolerans
eklenmedi, mevcut Vectors veya norm algoritması değiştirilmedi.

## Explicit model ve evaluated snapshot sözleşmesi

NAT-014 selector'ları ordinary Enum'dur: evaluator identity kontrolü yapar.
`fixed_launch_direction` ve `baseline_cd0_drag_only` canonical string'leri
**kabul edilmez**. J-006'nın farklı str-enum davranışı buraya kopyalanmadı.
Unsupported seçimler sırasıyla UNSUPPORTED_THRUST_DIRECTION_MODEL /
thrust_direction_model ve UNSUPPORTED_AERODYNAMIC_FORCE_MODEL /
aerodynamic_force_model olarak, verilen selector value ile reddedilir.
Profil explicit verilmelidir; default/fallback yoktur.

TranslationalDynamicsInputs stored alanları:
massKg, thrustN, relativeVelocityWorldMS, dynamicPressurePa, referenceAreaM2,
dragCoefficientCd0, gravityAccelerationWorldMS2. Mass ve area finite/>0;
thrust, q ve Cd0 finite/>=0; vektörler finite 3D'dir. Validation sırası
Python gibi mass → thrust → relative vector → q → area → Cd0 → gravity'dir.
Multiple-invalid-girdi önceliği paritede ayrıca kontrol edildi.

Finite domain hataları: INVALID_MASS/mass_kg, INVALID_THRUST/thrust_N,
INVALID_DYNAMIC_PRESSURE/dynamic_pressure_Pa, INVALID_REFERENCE_AREA/
reference_area_m2, INVALID_DRAG_COEFFICIENT/drag_coefficient_cd0.
Raw NaN/infinity generic ValueError ↔ IllegalArgumentException yolundadır;
negatif değerler abs/clamp ile düzeltilmez. Bu snapshot q, Cd0, mass, gravity,
relative flow veya thrust hesaplamaz; mdot alanı yoktur.

## Instantaneous denklemler ve işlem sırası

1. Profile selector'ları doğrulanır.
2. `F_thrust = thrustN * supplied launchDirectionWorldUnit`.
3. `F_gravity = massKg * supplied gravityAccelerationWorldMS2`.
4. `relativeSpeed = magnitude(relativeVelocityWorldMS)`.
5. Norm tam sıfır ve q=0 ise drag tam zero vector'dür. Norm sıfır ve q>0 ise INCONSISTENT_DRAG_INPUTS / dynamic_pressure_Pa hatasıdır.
6. Diğer dalda `D=(q*Aref)*Cd0`, her bileşende `F_drag=(-D*v_rel_i)/relativeSpeed`.
7. Net her bileşende `(F_thrust+F_drag)+F_gravity` sırasıyla türetilir.
8. `acceleration_i=net_i/massKg`; `positionDerivative=state.velocity`.

Thrust direction rocket velocity'den seçilmez; gravity yönü/büyüklüğü sabit
kodlanmaz. Relative velocity state velocity'den bağımsızdır. q yeniden
hesaplanmaz; nonzero flow/q=0 geçerlidir. Epsilon/speed threshold veya mdot*v
yoktur. Nonfinite force/net girdileri mevcut finite-vector validation'dan
geçemez; net finite iken division sonucu acceleration nonfinite ise
INVALID_ACCELERATION / velocity_derivative_world_m_s2 ve üç bileşenli value
korunur. Underflow ile hesaplanan norm sıfır olduğunda kaynakta hangi dal
çalışıyorsa Java da onu çalıştırır; daha kararlı norm/fallback eklenmedi.

Forces yalnız thrust/drag/gravity saklar. Net her erişimde yeni immutable
3-vector'dür. Derivative yalnız iki rate saklar. Result yalnız forces,
derivative ve supplied profile saklar. Acceleration aynı derivative velocity
rate nesnesinin derived görünümüdür; ikinci stored authority değildir.
Builder/evaluator stateless, parametresiz ve deterministic'tir.

## Frozen NAT-014 kanıtı

| Fixture | Thrust N | Drag N | Gravity N | Net N | Acceleration m/s² |
|---|---|---|---|---|---|
| Ignition | (0,0,30) | (0,0,0) | (0,0,-20) | (0,0,10) | (0,0,5) |
| Ascent | (0,0,30) | (0,0,-0.5) | (0,0,-20) | (0,0,9.5) | (0,0,4.75) |
| Descent | (0,0,0) | (0,0,+0.5) | (0,0,-20) | (0,0,-19.5) | (0,0,-9.75) |
| True 3D | (6,0,8) | (-2.4,0,-3.2) | (0,0,-14.7) | (3.6,0,-9.9) | (2.4,0,-6.6) |

Bunlar kaynak testlerin atol=2e-15/rtol=0 fixture'larıdır; üretim defaults
değildir. `verification/evidence/j007-frozen-dynamics.json` dört girdinin
tam effective input, Python ve Java decoded sonuçlarını yuvarlamadan tutar.
Örneğin -14.700000000000001 gibi ham float değerler -14.7'ye çevrilmedi.

## Test-first ve gerçek parite

İlk RED çalıştırmasında henüz sınıflar yokken compileTestJava exit 1 verdi
(beklenen missing-symbol aşaması). Uygulamadan sonraki focused GREEN:
InitialStateTest 12 + TranslationalDynamicsTest 10 = **22 test geçti**.
Nested test döngüleri aynı test içinde birden çok alan/girdi kontrol eder;
bu yüzden Java/Python test sayılarının birebir aynı olduğu iddia edilmez.

`verification/dynamics_parity.py` beklenen sonuçları doğrudan gerçek Python
production constructor/builder/evaluator'larından alır. Formül kopyası veya
Java çıktısından expected üretimi yoktur. Test-only DynamicsParityProbe gerçek
Java üretim API'lerini çağırır. Tam stored ve derived alanlar, vector size ve
bileşen sırası, profile metadata/identity ve acceleration-view identity
karşılaştırılır. Transport parser/reflection üretim JAR'ına girmez.

Nihai J-007: **417 toplam, 408 distinct effective input; 118 başarılı,
299 beklenen hata**. **2162/2162 sayısal protokol değeri exact**:
1637 fiziksel scalar/component ve 525 ayrı exact shape metadata değeri.
Nonexact karşılaştırma 0, maksimum absolute/relative fark 0'dır.
Error category/code/field/value tam eşleşti. Şekil alanları ve kopyalanan
position rate exact; hesaplanmış force/velocity-rate için yalnız kaynak
NAT-014 atol=2e-15, rtol=0 kullanılır. Yeni/genişletilmiş tolerance yoktur.

Collection; zero/nonzero initial değerler, arbitrary unit direction,
near-unit preservation, sınır/nextafter, 1e-8 dışı direction, raw nonfinite,
yanlış vector shape, enum identity, frozen fixtures, farklı rocket/relative
velocity, q=0/q>0 dalları, farklı gravity yönü, Reynolds/aero hesabı yapmadan
supplied değer kullanımı ve overflow/underflow hata yollarını kapsar.
Genuine unexplained parity discrepancy oluşmadı; STOP sonrası gizli düzeltme
yapılmadı. Python'un beklenen overflow/invalid RuntimeWarning'leri negatif
vakalarda görüldü; kategoriler eşleşti, warning'ler bastırılmadı.

## Tam regression ve önceki parite zinciri

Ortam: Windows 11 build 26200; Python 3.12.4; NumPy 2.5.2;
Eclipse Temurin Java/javac 17.0.20.1+1; Gradle Wrapper 8.12.1.
Aşağıdaki tamamlanan gate'ler exit 0 verdi.

| Gate | Sonuç |
|---|---|
| Focused J-007 Java | 22 passed |
| Focused accepted Python NAT-013/014 | 69 passed |
| Tam Java clean build/test | **373 passed**, 0 failure/error/skipped; önceki 351 korundu |
| Tam Python regression | **1491 passed** |
| J-001 parity | 6/6 distinct, passed |
| J-002 parity | 95 başarı + 53 hata + 7 trig, 155 distinct, passed |
| J-003 parity | 2485 başarı + 138 hata, 2623/2622 distinct, 2475 exact başarı; condition norms 518/518 exact |
| J-004 parity | 92 başarı + 288 hata, 380/374 distinct, 5302/5302 numeric exact |
| J-005 parity | 505 başarı + 252 hata, 757/743 distinct, 1862/1862 numeric exact |
| J-006 parity | 236 başarı + 359 hata, 595/548 distinct, 3570/3570 numeric exact |
| J-007 parity | 118 başarı + 299 hata, 417/408 distinct, 2162/2162 numeric exact |
| Mevcut + yeni integrity suites | **51 passed** = önceki 39 + yeni 12 |
| Bağımsız packaged JAR consumer | Önceki gruplar + DYNAMICS_CONSUMER PASS |
| verifyProductionDependencies / runtimeClasspath | PASS / No dependencies |
| Runtime jdeps | **java.base** |

J-003'ün önceden kontrollü 10 nonexact libm/diagnostic vakası aynı toleranslar
içinde kaldı (chain max relative 3.8356762446777534e-16). J-002 trig max fark
1.1102230246251565e-16 idi. Bunlar J-007'de değiştirilmedi; yeni discrepancy
olarak gizlenmedi. Mevcut Vector/magnitude dahil J-001–J-006 üretim dosyaları
ve parity runner'ları değişmedi. Sınırlı test kümesinden evrensel bitwise
eşitlik veya tüm platformlar için norm-boundary eşdeğerliği çıkarılmaz.

### Verification-integrity negatif kontrolleri

Yeni `test_dynamics_parity_integrity.py` yalnız child-process output'unu
kasıtlı bozar. Yanlış numeric component, yanlış error category/code/field,
missing/extra/duplicate field, truncated/empty output, empty case collection,
child process failure ve Python -O ayrı kontrol edilir. **12 kontrolün
tamamında child exit 1** oldu; parent test beklenen reddi doğrulayarak geçti.
Tam 51-test çalıştırması exit 0 verdi. Bunlar genuine physics mismatch değil,
izole fault injection'dır. Shared parity_contract.py değiştirilmedi.

### Komutlar

Engine dizininde:

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/unit/dynamics/test_initial_state.py tests/unit/dynamics/test_translational.py -p no:cacheprovider -q
.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q
```

Core dizininde (makine yolları yalnız gerçek yürütme kaydı, build default'u değil):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:/Users/ebrut/AppData/Local/Temp/roketsim-j001-gradle-cache'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j007' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-cache' test --tests org.roketsim.core.InitialStateTest --tests org.roketsim.core.TranslationalDynamicsTest --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-final' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
$env:AUD002_BUILD_DIR='C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-final'
$env:AUD002_JAVA='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe'
..\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest verification/test_parity_integrity.py verification/test_propulsion_parity_integrity.py verification/test_aerodynamics_parity_integrity.py verification/test_dynamics_parity_integrity.py -p no:cacheprovider -q -s
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-consumer' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-consumer-cache' run '-PcoreJar=C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-final/libs/roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME/bin/jdeps.exe" --print-module-deps 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-final/libs/roketsim-core-0.1.0.jar'
```

Yedi parity runner `..\roketsim-native-engine\.venv\Scripts\python.exe -B`
ile, yukarıdaki `--java` java.exe ve
`--build-dir C:/Users/ebrut/AppData/Local/Temp/roketsim-j007-final` kullanılarak
ayrı çalıştırıldı: python_parity.py, quaternion_parity.py,
environment_parity.py, vehicle_parity.py (ek `--group all`),
propulsion_parity.py, aerodynamics_parity.py, dynamics_parity.py.

## Bağımsızlık, kapsam, sınırlamalar ve yayın kaydı

Independent consumer ayrı Gradle projesidir; yalnız paketlenmiş Core JAR'ını
alır. Yeni state builder ve explicit 3D evaluated snapshot ile dynamics API'sini
kullanıp frozen acceleration'ı ve ownership'i doğruladı. Python, NumPy,
OpenRocket/Swing, JSON runtime veya harici fizik dependency yoktur. Üretim
import'ları yalnız java.base ve mevcut Core math sınıflarıdır.

Javadoc build başarılı; varsayılan gösterim sınırında 100 missing-comment
uyarısı raporlandı. Uyarı/test bastırma yapılmadı. Bu dokümantasyon borcu
nonblocking'dir; toplam eksik yorum sayısının tam envanteri iddia edilmez.

Final scope: yukarıdaki 13 yeni production dosyası; module-info.java'ya
yalnız dynamics export; InitialStateTest.java, TranslationalDynamicsTest.java,
DynamicsParityProbe.java; dynamics_parity.py ve test_dynamics_parity_integrity.py;
j007-frozen-dynamics.json; bağımsız consumer'da dynamics bölümü; bu belge.
Python, ADR'ler, NAT arşivleri, önceki verification/production dosyaları
değişmedi. Build/cache çıktıları TEMP'te ve commit dışında kaldı.

NAT-015 PhysicsEvaluator, altitude mapping, motor-time/ignition orchestration,
wind/relative-flow hesaplaması, q/Cd0/mass recomputation, static-aero coupling,
force momentleri, attitude/6DOF, rail, Euler/RK4, timestep, events, history,
trajectory, JSON/frontend yoktur. Sonraki milestone başlatılmadı.

Yayın politikası: yalnız tüm gate'lerden geçen J-007 dosyaları tek
`feat(java): implement J-007 initial state and 3dof dynamics` milestone commit'ine
alınır; yalnız migration/j-007-3dof-dynamics dalı normal push ile yayımlanır.
Main/J-006 uzak SHA'ları publication öncesi/sonrası kontrol edilir; gerçek
milestone SHA ve remote SHA eşitliği final görev raporunda kaydedilir.
Bu dosyayı içeren commit'in otoriter kimliği Git history'dir; self-referential
SHA oluşturmak için ikinci commit/amend veya geçmiş değişikliği yapılmaz.
