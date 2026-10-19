# J-010 — Flight Events, Recorder, Simulation Engine ve Result

Doğrulama tarihi: 2026-10-19. Bu kayıt yalnız NAT-018/019/020/021 dil
göçünü kapsar; NAT-022 tam trajectory V&V kampanyası J-011'e bırakılmıştır.

## Başlangıç ve kaynak otoritesi

Kabul edilmiş J-009 başlangıcı `e4b2b8ff74169068d8cdd5b309e1b685e85f90b3`;
başlangıç dalı `migration/j-009-physics-evaluator`, temiz çalışma ağacı ve
eşleşen origin dalı doğrulandı. Hedef `migration/j-010-simulation-lifecycle`
yerelde/remote'da bulunmadığından doğrudan bu commit'ten oluşturuldu.
J-001–J-009, AUD-002 ve M-001 ataları doğrulandı. Main'e geçilmedi;
başlangıç origin/main SHA'sı `96686c6c35c7f859f1849abe8bfe13d5879405d9`.

Otoriter Python kaynakları `roketsim-native-engine/src/roketsim_native/simulation/`
altındaki `events.py`, `recorder.py`, `engine.py`, `result.py` ve aynı
engine projesinin `tests/unit/simulation/test_events.py`, `test_recorder.py`,
`test_engine.py`, `test_result.py` dosyalarıdır. `docs/archive/nat/` altındaki
NAT-018–021 kayıtları ve ADR-007/ADR-008 incelendi; hiçbiri değiştirilmedi.
Python implementasyonu, testleri ve JSON/CLI geçmişi korunmuştur.

## Birebir public API eşlemesi

Java package: mevcut `org.roketsim.core.simulation`; module export zaten J-009'da
bulunduğundan `module-info.java` değiştirilmedi. Her isim aşağıda ayrı bir Java
kaynak dosyasıdır; yeni framework, dependency veya alternatif authority yoktur.

| Gate / Python sorumluluğu | Java public tipleri |
|---|---|
| A / events.py | FlightEventType, BurnoutDetectionModel, ApogeeDetectionModel, GroundReferenceModel, EventLocalizationModel, FlightEventDetectionProfile3DOF, FlightEventDetectionContext3DOF, FlightEventOccurrence3DOF, EventDetectionError, FlightEventDetector3DOF |
| B / recorder.py | RecordedFlightSample3DOF, RecordedFlightData3DOF, FlightRecordingError, FlightRecorder3DOF |
| C / engine.py | SimulationRunLimits3DOF, SimulationTerminationReason, TerminalEventHandlingModel, SimulationRunConfiguration3DOF, SimulationExecution3DOF, SimulationEngineError, SimulationEngine3DOF |
| D / result.py | SimulationResultError, SimulationResult3DOF |

Python frozen/slotted dataclass'ları Java record'larına; tuple stream'leri immutable
List'e çevrildi. Property erişimleri camelCase metotlarıdır. Enum `.value()` kaynak
metnini korur. Keyword-only Python çağrıları Java typed positional metotlardır.
Domain error'ları IllegalArgumentException alt sınıfları olup `errorCode()`,
`fieldName()`, `value()` taşır; kodlar ve snake_case field değerleri korunur.
Java typed/null/raw-generic sınırları Python TypeError'ın birebir exception sınıfı
değildir: compile-time tip reddi, NullPointerException veya ClassCastException ile
izah edilen dil adaptasyonudur. Bunlar sayısal domain eşdeğerliği iddiasına karıştırılmaz.
Run limit yalnız `int` alır; Boolean/float/text parsing eklenmez.

## Gate A — olaylar

Detector fieldless/stateless'tir. Profildeki ordinary Enum seçiciler exact enum
tipiyle doğrulanır; J-006 string eşdeğerliği buraya taşınmaz. Canonical string,
null ve yanlış seçiciler source error code/field/value ile reddedilir.

- Burnout yalnız `ignitionTimeS + curveEndTimeS`, `(start,end]` aralığındadır.
- Apogee yalnız `vz0 > 0 && vz1 <= 0`, alpha `vz0/(vz0-vz1)` kullanır.
- Ground launch WORLD-Z düzlemine göre `h0 > 0 && h1 <= 0` geçişidir.
- State tahmini component başına `a + alpha*(b-a)` sırasını korur.
- Zaman, alpha ve state derived finite invariant'ları kaynak hatalarını üretir.
- Çıktı zaman sıralıdır; exact tie önceliği BURNOUT/APOGEE/GROUND'dur.

NAT-018 A–K: F50 ignition .250 + curve end 1.430 = 1.680 s; 1.60–1.70
bracket alpha yaklaşık .8; exact endpoint bir kez; apogee +3→−1 alpha .75,
4.15 s, vz=0; ground Zlaunch=50, +.3→−.1 alpha .75, event Z=50;
ground endpoint bir kez ve zero→negative için event yok; genel interpolation
position (1,2,11), velocity (3,5,7); chronology, tie order ve immutable empty
çıktı doğrulandı. Aynı noktalar overflow/nonincreasing interval kontrolleriyle
`FlightEventsTest` içinde **6 test**, exit 0 verdi. Bu gate B'den önce çalıştırıldı.

## Gate B — pasif kayıt

Sample yalnız accepted point ve caller-supplied physics referanslarını taşır.
Recorder yalnız iki private append-only history tutar. Sample zamanı strictly
increasing; event zamanı globally nondecreasing'dir. Bütün incoming batch önce
doğrulanır; başarısız batch kısmen eklenmez. Eş zaman ve duplicate event'ler
verildiği sırada kalır. Snapshot yeni immutable container'dır; eski snapshot
değişmez, domain nesneleri kopyalanmaz ve recorder kapanmaz.

NAT-019 chronology, 0/.01/.02 sample dizisi, negatif ilk zaman, equal/reversed
sample reddi, equal-time supplied event sırası, internal/existing batch violation,
empty snapshot, caller collection isolation ve domain identity test edildi.
Raw generic yanlış sample/event elemanları ve null girdiler mutation öncesinde
reddedildi. `FlightRecorderTest`: **6 test**, exit 0; Gate C'den önce geçti.
Point/physics correspondence recorder tarafından kanıtlanamaz; NAT-015 provenance
modeli genişletilmedi. K4'ün accepted endpoint olduğu varsayılmadı.

## Gate C — yürütme

Engine'de field yoktur. Her run fresh local recorder/evaluator/integrator/detector
kurar. Initial state accepted builder'dan; initial time tek propulsion timeline'ın
ignition zamanından gelir. İlk gerçek physics sample stepping'den önce kaydedilir.
Callback her trial time/state için J-009'u yeniden değerlendirir ve yalnız dynamics
derivative döndürür. Aynı FixedStepConfig referansı kullanılır, adım kırpılmaz.
Candidate event screening sonrası kabul edilirse endpoint physics ayrıca hesaplanır.

Terminal classification occurrence `isTerminal()` otoritesidir. İlk terminal seçilir;
zamanı terminale eşit/önce olan supplied event'ler tutulur. Alpha<1 candidate'i
reddeder, endpoint physics/sample üretmez, executed-step sayılır. Alpha==1 exact
endpoint ayrı physics ve sample ile kabul edilir. Son izinli adımda terminalin
step guard'a önceliği vardır. Exception'lar sarılmaz.

`SimulationEngineTest`: **5 gerçek zincir testi**, exit 0. Nonzero ignition 2.5,
1/2/3 adım; tekrar run eşitliği ve fresh history; her sample'ın bağımsız gerçek
endpoint physics ile eşitliği; K4/endpoint ayrımı; source zero→negative ground
davranışı ve upstream atmosphere exception boundary test edildi. Gate D'den önce geçti.

Python monkeypatch fixture'larının Java final sınıflarına adaptasyonu yalnız
`verification/lifecycle-spies/` ve `lifecycle-recorder-failure/` altındadır.
Ayrı JVM classpath'i üretim engine bytecode'unu kullanır. PhysicsSpy ve DetectorSpy
source test fixture'larıdır, gerçek physics parity yerine sunulmaz. Integrator spy,
yalnız kendi sınıfını child loader ile orijinal compiled RK4 bytecode'undan yükleyip
aynı typed nesnelerle forward eder; RK4 denklemleri kopyalanmaz. Recorder failure
sınıfı yalnız kendi negatif JVM'inin başındadır. Hiçbiri Core JAR'a dahil değildir.

| Source lifecycle fixture | Sample | Executed step | Physics çağrısı | Detector / integrator |
|---|---:|---:|---:|---:|
| 1 normal | 2 | 1 | 6 | 1 / 1 |
| 2 normal | 3 | 2 | 11 | 2 / 2 |
| 3 maximum steps | 4 | 3 | 16 | 3 / 3 |
| İlk interior terminal | 1 | 1 | 5 | 1 / 1 |
| Exact endpoint terminal | 2 | 1 | 6 | 1 / 1 |
| Üçüncü interior terminal | 3 | 3 | 15 | 3 / 3 |

13 isolated senaryo ayrıca terminal sonrası event filtering, equal-time B/A/G,
initial/RK-stage/detector/endpoint/recorder hatalarının aynı instance olarak
yayılımını ve her integrator çağrısında exact config identity'yi doğrular.
1/2/3 normal ile third-interior gözlemleri sayısal olarak farklı K4/weighted
endpoint state'lerini gösterir. Tahmini event state sample yapılmaz.
NAT-020 A–R kapsamı bu testler, gerçek kısa zincir ve schema/scope assertions ile
izlenir; full NAT-022 trajectory sonucu veya başarı iddiası üretilmez.

## Gate D — sonuç facade'ı

Tek stored alan execution'dır. History, reason, steps ve ilk/son sample exact
forward edilir. Generic query bütün occurrence'ları supplied sırada döndürür.
Named query yoksa null, tekse aynı occurrence, çoksa AMBIGUOUS_EVENT_HISTORY'dir.
Terminal reason için tam bir terminal, maximum-steps için sıfır terminal gerekir.
Missing/multiple/unexpected, empty samples ve termination-before-initial kaynak
structured hataları korunur. Result fizik/detector/integrator/recorder çağırmaz.

NAT-021 A–S: 2.5/2.6/2.7 first/last/identity; boş completed result reddi;
duplicate APOGEE generic/ambiguous; absent/unique named lookups; terminal authority;
9.10 sample / 9.163 terminal ayrımı; 9.20 exact endpoint; maximum-step 6.0 ve
terminal=null; missing/multiple/unexpected integrity; 2.5→12.75 duration 10.25;
2.5→10.08 terminal duration 7.58 ve son sample 10.0 doğrulandı.
`SimulationResultTest`: **6 test**, exit 0.

`flightTimeS`, `finalState`, `finalSample`, altitude/impact/max-Mach/q analytics ve
export/plot API'leri yoktur. Ignition Liftoff değildir; prelaunch support, Liftoff,
guide/rail-clear henüz authority değildir. WORLD z, launch-relative height ve
geopotential altitude birleştirilmez. Duration abs/clamp olmadan termination−initial'dır.

## Parity ve test bütünlüğü

`verification/lifecycle_parity.py` expected sonuçları gerçek Python production
fonksiyonlarından alır; Java `LifecycleParityProbe` gerçek production A/B/C/D
sınıflarını çağırır. B başarısızlıklarında recorder'ın after snapshot'ı da compare
edilir. C gerçek geometry/mass/propulsion/environment/aero/physics/RK4 zinciridir;
13 isolated spy vakası ayrıca ve açık etiketle raporlanır.

Protocol status, bütün field set'i, duplicate/missing/extra, output length, child
exit, empty case collection ve -O reddi mevcut parity_contract ile korunur.
Category/code/field/value ve complete nested physics/state/profile yapılarına
bakılır; yalnız headline zaman veya sample sayısı karşılaştırılmaz.

Event scalar süre/alpha için kaynak pytest.approx kontratı, estimated state için
kaynak NumPy allclose kontratı; physics için J-009 quantity-specific kaynak
toleransları kullanılır. Shape/count değerleri exact'tir. Yeni tolerans yoktur.
Bu koleksiyonda tolerans kullanılarak kabul edilen nonexact değer çıkmamıştır.
Expected error-value sayıları da eşitlik/NaN eşdeğerliğiyle kontrol edilir; aşağıdaki
numeric sayaçlar successful-case sayılarıdır, error payload coverage sayacı değildir.

| Gate | Total / distinct | Başarılı / beklenen hata | Numeric / exact | Shape altkümesi | Max abs / relative |
|---|---:|---:|---:|---:|---:|
| A | 32 / 32 | 15 / 17 | 115 / 115 | 35 | 0 / 0 |
| B | 10 / 10 | 6 / 4 | 2788 / 2788 | 376 | 0 / 0 |
| C | 16 / 16 | 13 / 3 | 4135 / 4135 | 530 | 0 / 0 |
| D | 23 / 23 | 17 / 6 | 332 / 332 | 88 | 0 / 0 |
| C isolated spies | 13 / 13 | 8 normal/terminal + 5 propagated exception | 296 / 296 | boyut/call-count metadata içerir | 0 / 0 |

Gerçek A–D toplamı **81 distinct vaka**, **51 başarı / 30 beklenen hata**,
**7370 numeric exact**; bunun 1029'u shape metadata, 6341'i diğer scalar/component
değerleridir. Spy sonuçları bu gerçek physics sayısına eklenerek gizlenmez.
Finite örnek koleksiyonu universal bitwise/platform parity iddiası değildir.

Tam hassasiyet evidence: `roketsim-core/verification/evidence/j010-lifecycle-anchors.jsonl`
7 source-derived input/Python/Java/raw-wire kaydı; `j010-lifecycle-spies.jsonl`
13 isolated gözlemdir. Sayılar round edilmedi. Expected overflow vakasında Python
RuntimeWarning normaldir; structured error karşılaştırması geçer.

Yeni integrity suite 23 testtir: event time/alpha/state/count/type/order, sample
count/time, steps, endpoint physics, termination reason/duration, error category/
code/field; missing/duplicate/extra field; truncated/empty output; empty cases;
child failure ve optimized Python. Kasıtlı fault child exit 1 üretmeli, parent doğru
diagnostic'i de doğrulamalıdır. İlk geliştirme RED'i 18 pass/5 fail idi: beş
enjeksiyon yolu nested `execution.recordedData...` prefix'ini taşımıyordu. Test
doğru olarak 'Injection target missing' durumunu başarı saymadı. Yalnız bu yeni
fault-adapter hedefleri düzeltildi; production, Python expected veya tolerans
değişmedi. Son focused çalışma **23 passed**, tüm eski+yeni suite **108 passed**,
exit 0. Gerçek cross-language parity discrepancy oluşmadı.

## Tam regresyon ve ortam

Windows 11 10.0.26200 amd64; Python 3.12.4; NumPy 2.5.2;
Temurin JDK 17.0.20.1+1; Gradle Wrapper 8.12.1. Build/cache TEMP altında;
OneDrive source dizinine generated build/cache eklenmedi.

| Çalıştırılan gate | Sonuç (exit 0) |
|---|---|
| Focused Python NAT-018–021 | 76 passed |
| Full Python regression | 1491 passed, 0 failed, 0 skipped |
| Full Java clean build/test | 427 tests, 0 failure/error/skipped; önceki 404 + yeni 23 |
| J-001 parity | 6 / 6 distinct |
| J-002 parity | 95 başarı + 53 hata + 7 trig; 155 distinct |
| J-003 parity | 2485 başarı + 138 hata; 2623/2622 distinct; 2475 exact başarı; 518 condition norm exact |
| J-004 parity | 92 başarı + 288 hata; 380/374 distinct; 5302 numeric exact |
| J-005 parity | 505 başarı + 252 hata; 757/743 distinct; 1862 numeric exact |
| J-006 parity | 236 başarı + 359 hata; 595/548 distinct; 3570 numeric exact |
| J-007 parity | 118 başarı + 299 hata; 417/408 distinct; 2162 numeric exact |
| J-008 parity | 89 başarı + 94 hata; 183/182 distinct; 3995 numeric exact |
| J-009 parity | 109 başarı + 76 hata; 185/174 distinct; 6745 numeric exact |
| Önceki 85 + yeni 23 integrity | 108 passed |
| Packaged-JAR independent consumer | Önceki dokuz grup + LIFECYCLE_CONSUMER PASS |
| verifyProductionDependencies / runtimeClasspath | PASS / No dependencies |
| jdeps --print-module-deps | java.base |

J-002 max trig farkı 1.1102230246251565e-16 ve J-003'ün eski belgelenmiş 10
nonexact vakası kaynak toleransları içinde kaldı. Önceki sayısal davranış,
assertion, fixture ve toleransları değiştirilmedi.

## Yeniden çalıştırma komutları

Engine dizini:

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/unit/simulation/test_events.py tests/unit/simulation/test_recorder.py tests/unit/simulation/test_engine.py tests/unit/simulation/test_result.py -q -p no:cacheprovider
.\.venv\Scripts\python.exe -B -m pytest -q -p no:cacheprovider
```

Core dizini; aşağıdaki makine yolları çalıştırma kaydıdır, production default değildir:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:/Users/ebrut/AppData/Local/Temp/roketsim-j001-gradle-cache'
.\gradlew.bat --version --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j010-final' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j010-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
$env:AUD002_BUILD_DIR='C:/Users/ebrut/AppData/Local/Temp/roketsim-j010-final'
$env:AUD002_JAVA="$env:JAVA_HOME/bin/java.exe"
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/lifecycle_parity.py --java "$env:AUD002_JAVA" --build-dir "$env:AUD002_BUILD_DIR"
..\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest verification/test_parity_integrity.py verification/test_propulsion_parity_integrity.py verification/test_aerodynamics_parity_integrity.py verification/test_dynamics_parity_integrity.py verification/test_numerics_parity_integrity.py verification/test_physics_evaluator_parity_integrity.py verification/test_lifecycle_parity_integrity.py -q -s -p no:cacheprovider
.\gradlew.bat -p verification/consumer -I "$PWD/verification/isolated-build.init.gradle" '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j010-consumer' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j010-consumer-cache' run '-PcoreJar=C:/Users/ebrut/AppData/Local/Temp/roketsim-j010-final/libs/roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME/bin/jdeps.exe" --print-module-deps "$env:AUD002_BUILD_DIR/libs/roketsim-core-0.1.0.jar"
```

Önceki dokuz runner da aynı interpreter, `--java` ve `--build-dir` ile çalıştırıldı:
python_parity.py, quaternion_parity.py, environment_parity.py, vehicle_parity.py
(ek `--group all`), propulsion_parity.py, aerodynamics_parity.py,
dynamics_parity.py, numerics_parity.py, physics_evaluator_parity.py. Her biri exit 0.
Raw execution logları TEMP/j010-*.log altında; controlled anchor dosyaları yukarıdadır.

## Dosya kapsamı, sınırlar ve yayın

39 dosya: 23 yeni simulation production tipi; dört focused JUnit sınıfı ve
LifecycleParityProbe; iki Python verification dosyası; beş isolated spy kaynağı;
iki JSONL evidence; mevcut CoreConsumer'a J-010 ilavesi; bu kayıt.
Yeni production tiplerinin tamamı yukarıdaki birebir Python mapping'indedir.
Spy sınıf isimleri yalnız final Java sınıfları için test-only instrumentation
gereğidir; üretim architecture veya hesap authority'si değildir.

Exact path envanteri: mapping tablosundaki 23 tipin her biri Core'a göre
`src/main/java/org/roketsim/core/simulation/<TipAdı>.java` yolundadır.
Kalan Core-relative dosyalar:

```text
src/test/java/org/roketsim/core/FlightEventsTest.java
src/test/java/org/roketsim/core/FlightRecorderTest.java
src/test/java/org/roketsim/core/SimulationEngineTest.java
src/test/java/org/roketsim/core/SimulationResultTest.java
src/test/java/org/roketsim/core/LifecycleParityProbe.java
verification/lifecycle_parity.py
verification/test_lifecycle_parity_integrity.py
verification/lifecycle-spies/org/roketsim/core/LifecycleSpyProbe.java
verification/lifecycle-spies/org/roketsim/core/numerics/ClassicalRK4Integrator3DOF.java
verification/lifecycle-spies/org/roketsim/core/simulation/PhysicsEvaluator3DOF.java
verification/lifecycle-spies/org/roketsim/core/simulation/FlightEventDetector3DOF.java
verification/lifecycle-recorder-failure/org/roketsim/core/simulation/FlightRecorder3DOF.java
verification/evidence/j010-lifecycle-anchors.jsonl
verification/evidence/j010-lifecycle-spies.jsonl
verification/consumer/src/main/java/consumer/CoreConsumer.java
```

On altıncı non-production dosya workspace-relative
`docs/verification/j-010-simulation-lifecycle.md` kaydıdır. Yalnız mevcut
CoreConsumer değiştirildi; diğer 38 dosya yenidir. Silinen dosya yoktur.

Önceki Java production dosyaları, Python source/test, ADR, historical verification
ve archive dosyaları değişmedi. CoreConsumer yalnız packaged JAR'dan gerçek
F50/config oluşturup detector, recorder, short 3-step engine ve facade API'lerini
kullandı. Runtime Python, NumPy, Swing, OpenRocket, JSON, network veya harici
physics dependency yoktur. JSONL yalnız verification evidence'dır, runtime export değildir.

Javadoc başarılı; varsayılan gösterim sınırında 100 missing-comment uyarısı
nonblocking documentation borcudur, tüm warning envanteri iddiası değildir.
Henüz exhaustive veya cross-platform numerical parity iddiası yoktur.
Engine thread-safety veya recorder concurrent access kontratı eklenmedi.

NAT-022 full trajectory kampanyası, yeni flight phase, Liftoff, rail/prelaunch,
recovery, adaptive/dense/event-split integration, 6DOF/AoA/moments, analytics,
frontend veya export eklenmedi. J-011 başlatılmadı.

Yayın ancak tüm gate'lerden sonra tek J-010 commit'i ve yalnız
`migration/j-010-simulation-lifecycle` normal push'u olarak yapılır. Main ve
J-009 remote SHA'ları ile local/remote J-010 eşitliği ayrıca kontrol edilir.
Bu kaydı içeren commit'in exact SHA'sı Git history ve final görev raporundadır;
self-referential SHA için amend/ikinci commit yapılmaz.
