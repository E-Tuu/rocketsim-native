# J-009 — PhysicsEvaluator 3DOF Migration

Doğrulama tarihi: 2026-10-19. J-009, kabul edilmiş Python NAT-015'in tek-nokta
physics orchestration sözleşmesinin Java 17 migration'ıdır. Yeni fizik, numerical
algorithm, simulation lifecycle veya trajectory yürütmesi değildir.

## Başlangıç, kaynak ve Git sınırı

Git root `OpenRocket`; temiz başlangıç dalı `migration/j-008-rk4-numerics`,
HEAD `bdd8568a6803821406fe50fd150d5e66b840dcfb`. Yerel ve uzak J-008 aynı
SHA'daydı. J-001–J-008, AUD-002 ve M-001 ancestry kontrolleri geçti. Yerel/uzak
J-009 dalı yoktu; doğrudan bu HEAD'den `migration/j-009-physics-evaluator`
oluşturuldu. Main uzak SHA'sı `96686c6c35c7f859f1849abe8bfe13d5879405d9`.

İncelenen kabul edilmiş otoriteler:

- `roketsim-native-engine/src/roketsim_native/simulation/physics.py` tamamı.
- `roketsim-native-engine/tests/unit/simulation/test_physics.py` tamamı.
- `docs/archive/nat/nat-015-physics-evaluator-3dof.md`, ADR-007 ve ADR-008.
- J-003–J-007 kaynak/API eşlemeleri, ilgili Java public API'ler ve J-008 doğrulama kaydı.
- Mevcut strict parity_contract, parity probe/runner'ları, integrity ve packaged-JAR consumer düzeni.

Python production/test, ADR/NAT ve önceki verification kayıtları değiştirilmedi.
Önceki Java production paketleri ve parity runner'ları değişmedi. Python kalıcı
kontrollü, versioned doğrulama referansıdır; Java runtime bağımlılığı/fallback
değildir. OpenRocket veya frontend fiziği kaynak alınmadı.

## Exact public API eşlemesi

Java kökü: `roketsim-core/src/main/java/org/roketsim/core/simulation/`.

| Python physics.py sorumluluğu | Java public tip / API |
|---|---|
| EnvironmentPositionMappingModel | Aynı isimli enum; LOCAL_ENU_VERTICAL_OFFSET; value() |
| PropulsionTimeline(ignition_time_s) | PropulsionTimeline(double ignitionTimeS) record |
| PhysicsEvaluationError | Aynı isimli IllegalArgumentException alt tipi; errorCode/fieldName/value |
| PhysicsEvaluationContext3DOF | Aynı isimli 14 alanlı record |
| PhysicsEvaluationResult3DOF | Aynı isimli 13 alanlı record |
| PhysicsEvaluator3DOF.evaluate | evaluate(double timeS, TranslationalState3DOF state, LaunchConditions3DOF launchConditions, PhysicsEvaluationContext3DOF context) |

Altı public tip, altı Java dosyasındadır; ek production sınıfı/helper/framework
yoktur. module-info.java yalnız simulation export ekler. Python keyword-only
imzalarının typed Java positional API'ye uyarlanması dışında sözleşme korunur.

### Context: 14 stored alan

resolvedGeometry, aerodynamicSurfaces, structuralMassProperties, motorInstallation,
motorPropertyModelProfile, basicDragModelProfile, translationalDynamicsModelProfile,
environmentPositionMappingModel, launchEnvironmentAltitudeM, propulsionTimeline,
atmosphereModel, airPropertiesCalculator, gravityModel, windModel.

Supplied upstream nesneleri doğrudan korunur; kopya configuration authority veya
default dependency üretilmez. Yalnız launchEnvironmentAltitudeM finite denetlenir;
negatif değer constructor'da yasaklanmaz. Geopotential height (m) anlamındadır,
generic MSL veya geometric altitude değildir. Mandatory parametreler için gizli
fallback yoktur; Python'un ek type validation yapmadığı alanlara yeni fiziksel
constructor validation'ı eklenmedi.

WindModel için iki mevcut final Java sınıfı ortak interface taşımadığından Object
alanı ve evaluator'da yalnız NoWindModel/ConstantWindModel dispatch'i kullanıldı.
Bu, görevde izin verilen dar dil uyarlamasıdır. Mevcut sınıflar değiştirilmedi,
yeni interface/policy registry eklenmedi. Null veya unsupported wind otomatik
NoWind'e dönüştürülmez; step 4'te açık IllegalArgumentException verir. Python'un
unsupported duck-typed wind nesnelerinin Java'da genel desteği iddia edilmez;
kabul edilmiş iki modelin gerçek sayısal davranışı karşılaştırılır.

### Result: 13 stored alan

environmentAltitudeM, motorTimeS, atmosphere, airProperties,
airMassVelocityWorldMS, relativeFlow, flightConditions, motorThrustState,
motorMassProperties, rocketMassProperties, basicDrag,
gravityAccelerationWorldMS2, dynamics.

İki scalar finite denetlenir. Üç stored WORLD vektörü mevcut Vector.of(...,3,name)
yoluyla bağımsız immutable kopyadır. Diğer accepted upstream snapshot'lar aynı
referanslarıyla saklanır. Thrust/mass/Mach/Re/q/Cd0/acceleration alias'ı, cached
derived value, timestep veya dynamicsInputs alanı yoktur. Geçici snapshot yalnız
evaluate çağrısının yerel değişkenidir. Evaluator parametresiz ve fieldsizdir.

## Mapping, selector ve timeline sözleşmeleri

WORLD local ENU: +X East, +Y North, +Z Up; BODY +Z longitudinal ve ayrı nose-tip/
aft-positive x_geo conventions aynen korunur. SI kullanılır. Yeni dönüşüm yoktur.

Önce raw time_s finite denetlenir. Mapping yalnız enum **identity** ile kabul
edilir. Python str/Enum olması bu modülde canonical-string eşitliği sağlamaz:
`local_enu_vertical_offset` String'i kabul edilmez. J-006'nın farklı profile
eşitliği buraya kopyalanmadı. Basic Drag kendi kabul edilmiş canonical-string
profile davranışını upstream evaluator'da korur.

Mapping işlem sırası: launchZ = launch.initialPosition.z; currentZ = state.position.z;
deltaZ = currentZ - launchZ; altitude = supplied launchEnvironmentAltitudeM + deltaZ.
Launch WORLD Z sıfır varsayılmaz. Abs/clamp/terrain/geometric-geopotential conversion
yoktur. Mapped değer accepted atmosferin geopotential-height girdisidir.

PropulsionTimeline yalnız finite ignitionTimeS saklar; negatif ignition geçerlidir.
Motor time tek kez `timeS - ignitionTimeS` ile hesaplanır ve aynı double thrust
ile property evaluator'ına gönderilir. Negatif simulation time, elapsed time
geçerliyse kabul edilir. Tam sıfır elapsed time geçerlidir; epsilon, rounding,
snapping, preignition zero-thrust veya held-mass fallback yoktur.

NAT-015-owned structured hatalar:

| Code | Field | Value |
|---|---|---|
| UNSUPPORTED_ENVIRONMENT_POSITION_MAPPING_MODEL | environment_position_mapping_model | supplied selector |
| INVALID_ENVIRONMENT_ALTITUDE_MAPPING | environment_altitude_m | derived nonfinite altitude |
| BEFORE_IGNITION_UNSUPPORTED | motor_time_s | finite negative elapsed time |

Raw NaN/±Infinity ve nonfinite motor-time subtraction generic finite-validation
yolundadır. Upstream atmosphere/flow/propulsion/mass/drag/dynamics hataları catch
edilmez veya PhysicsEvaluationError ile wrap edilmez. Dil uyarlaması: Python
ValueError ↔ Java IllegalArgumentException; structured category/code/field/value
ve generic bağlam eşlenir, dile özgü repr/message biçimi yeniden tanımlanmaz.

## Frozen 13-adım evaluation order

1. Local ENU signed environment mapping.
2. Supplied atmosphereModel.evaluate(mapped altitude), ardından airPropertiesCalculator.evaluate(atmosphere).
3. Supplied gravityModel.evaluate().
4. Supplied accepted windModel.evaluate().
5. RelativeFlowCalculator.evaluate(current velocity, evaluated air-mass velocity).
6. BasicFlightConditionsCalculator.evaluate(relativeFlow, atmosphere, airProperties, resolvedGeometry.referenceLengthM).
7. Tek motorTime hesaplaması, finite validation ve preignition rejection.
8. MotorThrustCurveEvaluator.evaluate(installed motor, motorTime).
9. MotorPropertyEvaluator.evaluate(installation, explicit motor profile, aynı motorTime).
10. RocketMassPropertiesCalculator.evaluate(supplied structure, completed motor properties).
11. BasicDragEvaluator.evaluate(resolved geometry, surfaces, flight conditions, air properties, explicit drag profile).
12. Yerel TranslationalDynamicsInputs assembly: completed total mass/thrust/relative flow/q/Aref/Cd0/gravity.
13. TranslationalDynamicsEvaluator.evaluate(original current state, original launch conditions, snapshot, explicit dynamics profile).

Formüller upstream modüllerde kalır. Flight-conditions Reynolds supplied reference
length, drag Reynolds ise upstream aerodynamic length otoritesini kullanır;
birleştirilmedi. Static CNa/CP/margin kritik yola eklenmedi. Basic Drag'ın kapalı
0<=Mach<=1 alanı, daha dar static-aero alanıyla kısıtlanmadı.

## Test-first ve kaynak A–H fixture'ları

İlk focused RED, altı J-009 tip henüz yokken compileTestJava missing-symbol
hatalarıyla exit 1 verdi. Uygulama sonrası aynı focused suite GREEN: **13 test**.
Test döngüleri birden çok boundary/field denetler; Java/Python count eşitliği
iddia edilmez. Context referans kimliği, result schema/copy, selector identity,
time/altitude validation, enum/string ayrımı, negative time, overflow, real F50,
wind, flow/drag/dynamics delegation ve determinism kontrol edildi.

| Anchor | Gerçek Python ve Java sonucu |
|---|---|
| A: launchZ=50, currentZ=173.25, launch altitude=1200 | 1323.25 m |
| B: launchZ=50, currentZ=20 | 1170.0 m |
| C: time=6.43, ignition=5 | motorTime=1.4299999999999997 s |
| D: time=ignition=5 | motorTime=0.0 s |
| E: time=4.999, ignition=5 | BEFORE_IGNITION_UNSUPPORTED; value=-0.001000000000000334 |
| F: elapsed=0.012 s | thrust=51.377 N; motor mass=0.08474793212175599 kg; total mass=0.6355969349495034 kg |
| F: final vertical derivative | 71.02602425460724 m/s² |
| G: state v=(3,4,5), NoWind | relative=(3,4,5) m/s |
| G: wind=(1,-2,0.5) | relative=(2,6,4.5) m/s |
| H: controlled mass=2, thrust=30, Vrel=0, q=0, A=.01, Cd0=.5, g=(0,0,-10) | NAT-014 dv/dt=(0,0,5) m/s² |

H değerleri test-only explicit snapshot'ta doğrulandı; final upstream sınıflarını
değiştirmek veya production dependency injection eklemek için kullanılmadı.
J-009'un gerçek assembly'si ayrıca completed real-chain outputs'tan aynı J-007
snapshot kurulup doğrudan dynamics sonucu karşılaştırılarak sınandı. H testi
fake full-chain parity olarak sayılmaz. Frozen fixture'lar production defaults
değildir. Kaynak mass fixture'larının abs=5e-11 assertion'ları korundu.

C'de elapsed time 1.43'e snap edilmedi: kaynak curve bu gerçek floating-point
subtraction için 1.5987211554602254e-14 N küçük tail thrust üretir; Java da aynısını
üretir. Bu bir discrepancy veya yeni burn policy değildir.

Java upstream sınıfları final olduğu için Python monkeypatch spy'larının birebir
portu yapılmadı. Açık production call order/catch yokluğu incelendi; ayrıca
observable priority testleri time→mapping→atmosphere→flow→motorTime→propulsion→
drag→dynamics sırasını çoklu-invalid girdilerle kontrol etti. Atmosphere-domain
hatasının kategorisi/metadata'sı aynen geldi. Testlerde instrumented upstream
exception instance'ı üretildiği iddia edilmez; identity-preserving propagation
evaluator'ın upstream çağrıları catch etmemesiyle korunur.

## Full-chain parite ve sayısal sözleşmeler

physics_evaluator_parity.py gerçek Python constructors ve PhysicsEvaluator3DOF'u;
PhysicsEvaluatorParityProbe gerçek Java üretim evaluator'ını kullanır. Beklenen
fizik Java sonucundan veya kopyalanmış formüllerden üretilmez. Probe parsing,
reflection, record modification ve protocol flattening yalnız test kaynaklarıdır.

**185 toplam / 174 distinct effective input; 109 başarı / 76 beklenen hata**.
**6745/6745 numeric exact**, bunun 5920'si scalar/component, 825'i shape metadata.
Accepted nonexact=0; max absolute=0; max relative=0. Complete nested result,
üç stored WORLD vector, atmosphere/air, flight conditions, thrust/impulse,
motor/rocket mass/CG, yedi drag katkısı/diagnostics/derived totals, force/derivative,
profile metadata/identity ve category/code/field/value karşılaştırıldı.

Collection; A–G anchors, negative ignition/time, nextafter ignition sınırı,
atmosphere layer/domain sınırları, independent launch/current positions,
steady signed wind/zero relative flow, low-Re continuation, canonical J-006 drag
profile seçimi, unsupported mapping/profile, nonfinite/shape, mapped overflow,
invalid reference length ve multiple-invalid priority içerir. 330 m/s örneği
static-aero sınırından yüksek Mach'ta derivative-critical yolun korunmasını sınar;
unsupported supersonic örneği accepted drag domain hatası verir.

Toleranslar quantity-specific mevcut kaynak/parity sözleşmeleridir: atmosphere
relative 2e-14; air 3e-15; flight conditions 1e-14; thrust interpolation 4e-15
(source node thrust exact); impulse/motor mass/rocket mass 3e-15; Basic Drag
3e-15; hesaplanmış J-007 force/velocity-rate absolute 2e-15. Metadata, mappings,
copy edilen vektörler/position-rate ve errors exact'tir. Yeni universal/global
tolerance yoktur. Exact-match sayacı toleranslı kabulden ayrı tutulur.
Sonlu collection'dan evrensel bitwise veya tüm platformlar için garanti çıkarılmaz.

`roketsim-core/verification/evidence/j009-nat015-anchors.jsonl` summary ve sekiz
complete raw input/Python/Java A–G observation'ını yuvarlamadan saklar. Genuine
unexplained parity finding oluşmadı; STOP sonrası gizli correction yapılmadı.

## Tam doğrulama ve bağımsızlık

Ortam: Windows 11 build 26200; Python 3.12.4; NumPy 2.5.2; Eclipse Temurin
Java/javac 17.0.20.1+1; checksum kontrollü Gradle Wrapper 8.12.1.

| Gate | Gerçek yürütme sonucu, exit 0 |
|---|---|
| Focused Java J-009 | 13 passed |
| Python NAT-015 focused | 21 passed |
| Full clean Java build/test | 404 passed; 0 failure/error/skipped; önceki 391 korundu |
| Full Python regression | 1491 passed |
| J-001 parity | 6/6 distinct |
| J-002 parity | 95 başarı + 53 hata + 7 trig; 155 distinct |
| J-003 parity | 2485 başarı + 138 hata; 2623/2622 distinct; 2475 exact başarı, 518/518 condition norm exact |
| J-004 parity | 92 başarı + 288 hata; 380/374 distinct; 5302/5302 numeric exact |
| J-005 parity | 505 başarı + 252 hata; 757/743 distinct; 1862/1862 numeric exact |
| J-006 parity | 236 başarı + 359 hata; 595/548 distinct; 3570/3570 numeric exact |
| J-007 parity | 118 başarı + 299 hata; 417/408 distinct; 2162/2162 numeric exact |
| J-008 parity | 89 başarı + 94 hata; 183/182 distinct; 3995/3995 numeric exact |
| J-009 parity | 109 başarı + 76 hata; 185/174 distinct; 6745/6745 numeric exact |
| Eski + yeni negative integrity | 85 passed; önceki 65 + yeni 20 |
| Independent packaged-JAR consumer | Önceki gruplar + PHYSICS_EVALUATOR_CONSUMER PASS |
| verifyProductionDependencies / runtimeClasspath | PASS / No dependencies |
| jdeps --print-module-deps | java.base |

J-002 trig maximum Python farkı 1.1102230246251565e-16 ve J-003'ün belgelenmiş
10 nonexact vakası eski toleransları içinde kaldı. Önceki sayısal davranışlar
ve toleranslar değişmedi; bunlar J-009 finding'i olarak gizlenmedi.

Yeni 20 integrity testi yalnız izole child-process çıktılarını kasıtlı bozar:
atmosphere pressure, mapped altitude, motor time, thrust, motor mass, rocket mass,
q, Cd0 ve velocity derivative component; error category/code/field; missing/
duplicate/extra field; truncated/empty output; empty case collection; child
process failure ve optimized Python. Her kasıtlı fault child exit 1 üretti;
parent beklenen nonzero exit ve ilgili diagnostic'i doğruladı. Tam altı-suite
çalışması **85 passed in 105.64 s**, exit 0 verdi. Önceki safeguards veya ortak
protocol değiştirilmedi; hiçbir üretim hesabı fault injection için değiştirilmedi.

### Komut kaydı

Engine dizininde:

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/unit/simulation/test_physics.py -p no:cacheprovider -q
.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q
```

Core dizininde (makine yolları yalnız execution record, build default'u değildir):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:/Users/ebrut/AppData/Local/Temp/roketsim-j001-gradle-cache'
.\gradlew.bat --version --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j009' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-cache' test --tests org.roketsim.core.PhysicsEvaluatorTest --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-final' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
$env:AUD002_BUILD_DIR='C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-final'
$env:AUD002_JAVA='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe'
..\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest verification/test_parity_integrity.py verification/test_propulsion_parity_integrity.py verification/test_aerodynamics_parity_integrity.py verification/test_dynamics_parity_integrity.py verification/test_numerics_parity_integrity.py verification/test_physics_evaluator_parity_integrity.py -p no:cacheprovider -q -s
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-consumer' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-consumer-cache' run '-PcoreJar=C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-final/libs/roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME/bin/jdeps.exe" --print-module-deps 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j009-final/libs/roketsim-core-0.1.0.jar'
```

Dokuz runner ayrı ayrı aynı Python -B ile ve yukarıdaki java.exe / j009-final
`--java` / `--build-dir` parametreleriyle çalıştırıldı: python_parity.py,
quaternion_parity.py, environment_parity.py, vehicle_parity.py (ek --group all),
propulsion_parity.py, aerodynamics_parity.py, dynamics_parity.py,
numerics_parity.py, physics_evaluator_parity.py. Her runner exit 0 verdi.
Raw execution logları TEMP/j009-<runner>.log içindedir; build/cache TEMP'tedir.

## Son kapsam ve sınırlamalar

Toplam 14 dosya: altı simulation production tipi, module export, PhysicsEvaluatorTest,
PhysicsEvaluatorParityProbe, physics_evaluator_parity.py, dedicated integrity
runner, anchor JSONL, consumer addition ve bu doğrulama kaydı. Önceki production
modülleri, Python ve historical kayıtlar için Git diff kontrolleri boştur.

Consumer ayrı Gradle projesidir ve yalnız paketlenmiş JAR'ı kullanır. Gerçek
explicit geometry/structure/motor/surfaces/context oluşturup F50 .012 s
değerlendirmesini, mapped altitude/motor time, upstream snapshot'lar ve dynamics
derivative'ini doğruladı. Python/OpenRocket/Swing/JSON veya harici physics runtime
dependency yoktur. Production module dependency yalnız java.base'dir.

Javadoc başarılıdır; varsayılan gösterim sınırında 100 missing-comment uyarısı
raporlandı. Nonblocking documentation borcudur; toplam uyarı envanteri olduğu
iddia edilmez. Uyarılar/testler bastırılmadı. Henüz tüm-domain veya platformlar
arası exhaustive parity iddiası yoktur.

RK4 stepping, callback adapter, integration/time advancement, events, history,
SimulationEngine/SimulationResult, trajectory, prelaunch/rail, recovery,
static-aero coupling, AoA/moments, attitude/6DOF ve frontend integration yoktur.
Bir sonraki milestone başlatılmadı.

Yayın politikası: bütün gate'ler tamamlandıktan ve secret/generated-file/scope
kontrolünden sonra tek descriptive J-009 commit'i; yalnız
migration/j-009-physics-evaluator normal push'u. Main/J-008 SHA'ları ve local/remote
J-009 SHA eşitliği ayrıca kontrol edilir. Gerçek milestone SHA ve push sonucu
final görev raporundadır; bu dosyayı içeren commit'in otoriter kimliği Git history'dir.
Self-referential SHA için ikinci commit/amend veya history rewrite yapılmaz.
