# J-011 — F50 3DOF uçtan uca trajectory doğrulaması

Tarih: 2026-10-19. Bu milestone yeni production physics uygulaması değildir.
Kapsam accepted Python NAT-022 demonstration'ının bağımsız Java 17 Core'da
tekrarlanması, tam history parity ve mevcut regresyon gate'leridir.

## Başlangıç ve otorite

Başlangıç/predecessor `migration/j-010-simulation-lifecycle`, local ve remote
HEAD `4399400c1c8f04b05d4f36b04a5f7d11e958d813`, çalışma ağacı temizdi.
J-001–J-010, AUD-002 ve M-001 ancestry doğrulandı. Hedef dal yerelde ve remote'da
yoktu; doğrudan bu SHA'dan `migration/j-011-native-trajectory-vv` oluşturuldu.
Main başlangıç SHA'sı `96686c6c35c7f859f1849abe8bfe13d5879405d9`.

Tam okunan birincil V&V kaynakları workspace içinde:

- `roketsim-native-engine/tests/integration/simulation/test_native_demo_trajectory.py`;
- `docs/archive/nat/nat-022-end-to-end-native-trajectory.md`.

NAT-022 accepted commit'i `0b5aa09b76af2a25c96689c63474dd1e6ac396e5` olan tarihsel
ilk demo gate'idir. Güncel accepted Python implementation, bu test ve archive
değiştirilmedi. Geometry/materials/structural mass, motor catalog/installation/
thrust/properties/total mass, environment/flow/conditions, surfaces/drag,
dynamics, physics evaluator, fixed-step/RK4, events/recorder/engine/result
kaynakları ile karşılık gelen Java API'leri incelendi. J-010 verification ve
mevcut parity/integrity/packaged-consumer altyapısı yeniden kullanıldı.
OpenRocket veya frontend physics implementation'ı otorite olarak kullanılmadı.

## Exact fixture ve Java construction mapping

Java fixture yalnız `src/test/java/org/roketsim/core/NativeDemoTrajectoryTest.java`
altındaki `configuration(step,maximumSteps)` metodundadır. Python parity runner
doğrudan accepted source test'in `demo_configuration()` metodunu çağırır;
ikinci Python physics oracle veya kopyalanmış beklenen trajectory formülü yoktur.

| Otorite | Explicit source girdisi / kullanılan Java API |
|---|---|
| Geometry | SingleStageRocketGeometry: D=.100; conical nose L=.300, HOLLOW_SHELL, normal wall=.002; cylindrical body L=.700, wall=.002 |
| Fins | TrapezoidalFinSetGeometry: 4; root/tip .180/.080; span .120; tip-LE offset .050; root-LE x_geo .720; thickness .003; SQUARE; EQUALLY_SPACED |
| Mount/rings | MotorMountTubeGeometry(.120,.029,.001,0); CenteringRingPairGeometry(.003); overhang .005 |
| Reference | ReferenceGeometryPolicy.MAXIMUM_DIAMETER; bütün derived geometry GeometryResolver'dan |
| Materials | nose POLYSTYRENE (1050 kg/m³); body/fins/mount/rings CARDBOARD (680 kg/m³); MaterialCatalog ve StructuralMassPropertiesCalculator |
| Propulsion | MotorCatalogs.AEROTECH_F50_4T; MotorInstallationResolver; MotorCurveAnalyzer; DEMO_MOTOR_PROPERTY_MODEL_PROFILE |
| Aero | test-only "NAT-022 synthetic smooth V&V surface", roughness 0; SurfaceFinish/SingleStageRocketAerodynamicSurfaces; NATIVE_BASIC_DRAG_V1_PROFILE |
| Environment | explicit geopotential launch reference 1200 m; USStandardAtmosphere1976Lower, DryAirPropertiesCalculator, ConstantGravityModel, NoWindModel, LOCAL_ENU_VERTICAL_OFFSET |
| Launch | LaunchConditions3DOF: WORLD position (0,0,0), velocity (0,0,0), direction (0,0,1); PropulsionTimeline ignition 0 |
| Dynamics | NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE |
| Events | curve-end burnout, downward WORLD-vz apogee, launch WORLD-z plane, LINEAR_BRACKET_INTERPOLATION |
| Lifecycle | REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT; gerçek SimulationEngine3DOF ve SimulationResult3DOF |

Prompt'taki mount "outer diameter .029" ifadesi yerine authoritative constructor
semantiği korunmuştur: `.029` **innerDiameterM**'dir; .001 wall ile dış çap .031 m
resolver tarafından türetilir. Bu source fixture'ın aynı fiziksel girdisidir;
yeni clearance veya ölçü dönüşümü uygulanmadı.

SI, WORLD ENU, nose-tip origin ve aft-positive x_geo korunur; x_geo BODY z_B ile
eşitlenmez. WORLD z ile geopotential altitude yeni bir authority altında birleştirilmez.
Burnout ignition+canonical curve end **1.430 s**'dir; effective 5% end,
certification süresi, son nonzero thrust veya ejection delay değildir.

Üç gerçek run: primary .010 s / 5000; aynı primary'nin deterministic repeat'i;
fine .005 s / 10000. Bunların tamamı NAT-022 V&V fixture'ıdır, production default
değildir. Total run sayısı 3, distinct effective configuration sayısı 2'dir.

## Dokuz E2E testi ve fiziksel invariant'lar

`NativeDemoTrajectoryTest` accepted dokuz Python testini eşler:

1. Gerçek terminal completion, limits/counts ve result identity.
2. Strict chronology; finite vectors/acceleration/thrust; positive mass;
   nonnegative Mach/Re/q/Cd0; accepted Mach domain; source 1e-12 absolute x/y symmetry.
3. Exact birer burnout/apogee/ground; strict event order; curve-end authority.
4. İlk propagated accepted sample'ın yukarı hareketi ve below-ground accepted sample olmaması.
5. Powered mass'ın pozitif/non-increasing olması; motor initial→final mass düşüşü.
6. Positive-vz coast, apogee zero-vz, higher apogee, negative-vz ballistic descent,
   launch-plane terminal ground ve interior-state/sample ayrımı.
7. Primary repeat'in exact execution/history eşitliği; ayrı snapshot ve immutable copy sınırları.
8. Source-only coarse/fine sensitivity: apogee ve ground time farkı ≤.020 s;
   height relative farkı ≤.01. Bunlar cross-language tolerans değildir.
9. Diagnostic summary; test-only maxima ve source anchor'lar.

Focused Java **9 passed**, failure/error/skipped 0; original focused Python
**9 passed in 56.13 s**, exit 0. Java clean full run da aynı dokuz testi yeniden
çalıştırır. E2E-T01–T32 bu testlerde karşılanır. Mock, stub, alternatif integrator,
fake history, liftoff patch veya ground clamp yoktur. Diagnostic maxima result
production API'sine eklenmedi.

## Gözlenen primary ve fine sonuçları

Aşağıdaki değerler hem gerçek Java hem güncel gerçek Python run'larından alınmıştır;
primary archive değerleri de aynıdır. Geçmiş sayılar Java sonucu olarak varsayılmadı.

| Quantity | Primary h=.010 | Fine h=.005 |
|---|---:|---:|
| Termination | TERMINAL_EVENT | TERMINAL_EVENT |
| Executed candidates / accepted samples | 1584 / 1584 | 3167 / 3167 |
| Initial total mass (kg) | 0.6357490028277474 | 0.6357490028277474 |
| Initial / final motor mass (kg) | .0849 / .047 | .0849 / .047 |
| First propagated time (s) | .01 | .005 |
| First propagated WORLD z (m) | .0006321201805853316 | .00001771927699704517 |
| First propagated vz (m/s) | .23868281154447563 | .035149055990906806 |
| Burnout time (s) | 1.43 | 1.43 |
| Burnout estimated WORLD z (m) | 88.15789842420412 | 88.16052999906755 |
| Burnout estimated vz (m/s) | 87.92821395634027 | 87.930142543516 |
| Apogee time (s) | 7.012303670973791 | 7.012358447782318 |
| Apogee WORLD z / launch-relative height (m) | 279.5335684908227 | 279.54103485936486 |
| Apogee estimated vz (m/s) | 0 | 0 |
| Ground time (s) | 15.832349589466348 | 15.832554278426914 |
| Ground estimated vz (m/s) | -49.190189996430696 | -49.19041488155015 |
| Ground alpha | .23495894666415096 | .5108556851678205 |
| Last accepted time (s) | 15.829999999999707 | 15.830000000001075 |
| Last accepted WORLD z (m) | .1155854393789068 | .1256458248026679 |
| Maximum recorded Mach | .27673174265848777 | .2767383448511422 |
| Maximum recorded q (Pa) | 4663.19556661582 | 4663.453415746794 |
| Maximum recorded airspeed (m/s) | 92.8134640033708 | 92.81567598141022 |

Her iki run'da BURNOUT<APOGEE<GROUND; yalnız GROUND terminal ve son event'tir.
Ground estimated z=0'dır; negative-vz descent recovery'sizdir. Interior ground
state'i sample'a yükseltilmez, rejected below-ground candidate history'ye girmez.
Burnout alpha primary'de .999999999999889, fine'da 1.6875389974302739e-12'dir;
near-one/near-zero snapping eklenmemiştir.

Source sensitivity gözlemleri:

- apogee time difference: .00005477680852639111 s ≤ .020;
- ground time difference: .00020468896056513586 s ≤ .020;
- height relative difference: .000026709382920917456 ≤ .01.

Java ve Python primary repeat'leri complete time/state/event history açısından
exact deterministic'tir. Java JUnit execution equality bütün stored physics'i de
kontrol eder; Python runner her repeated sample'ın tüm nested snapshot alanlarını
karşılaştırır. Raw primary/repeat trace SHA'ları da eşittir; checksum tek başına
parity yerine kullanılmamıştır.

## Tam history karşılaştırma protokolü

`NativeTrajectoryParityProbe` yalnız test transport'udur. HEADER, sıralı SAMPLE,
sıralı EVENT, RESULT ve END kayıtları verir. Her sample index'i ve bütün public
stored point/physics/profile alanları kontrol edilir. Net force, acceleration view,
friction/pressure/base totals gibi public derived değerler de eklenir. Event
estimated state, alpha, isTerminal ve facade summary/identity/queries karşılaştırılır.
Geometry/structure/surfaces/launch/statistics/policies header'ı da source-derived'dır.

`native_trajectory_parity.py` gerçek Python engine'i çalıştırır. Java stdout'u
test artifact dosyasına spool edilir; checker her satırı ve expected record'u
eş zamanlı tüketir. Hiçbir sample atlanmaz/downsample edilmez. Wrong record order,
index, key set, duplicate field, missing/extra/truncated/empty output ve child
exit kontrol edilir. İlk gerçek mismatch olursa run/index/field, full Python record,
raw Java record, difference/tolerance ve explicit input/header JSON kanıtı yazılıp
nonzero exit verilir; otomatik production fix yoktur.

Sample time/state, event değerleri, summary ve discrete metadata bu koleksiyonda
**exact** kontrol edilir. Yalnız `sample.physics.*` için mevcut J-009 upstream
quantity-specific toleransları yeniden kullanılır. Java/Python state'leri aynı
olduğu için bu snapshot kontrollerinin fizik girdileri aynıdır. Yeni trajectory
veya global tolerans yoktur; sensitivity eşikleri burada kullanılmaz.

| Run | Sample record | SAMPLE numeric / exact | Nested physics numeric | EVENT numeric / exact | Bütün numeric / exact / accepted nonexact |
|---|---:|---:|---:|---:|---:|
| Primary | 1584 | 147312 / 147302 | 131472 | 33 / 33 | 147521 / 147511 / 10 |
| Repeat | 1584 | 147312 / 147302 | 131472 | 33 / 33 | 147521 / 147511 / 10 |
| Fine | 3167 | 294531 / 294505 | 262861 | 33 / 33 | 294740 / 294714 / 26 |

Distinct primary+fine için **4751 sample**, **442261 numeric comparison**,
**442225 exact / 36 accepted nonexact** vardır. Repeat dahil 6335 sample ve
589782 numeric comparison, 589736 exact / 46 accepted nonexact'dir.
Numeric sayılar physical scalar/component ile numeric metadata'yı içerir;
primary shape/index altkümesi 20617, fine 41196'dır. Sample record başına
time+altı state component'i exact'tir; event'ler ve RESULT summary'nin 62 numeric
alanı da exact'tir. "Exact" burada kaynak testlerinin numeric equality anlamıdır;
universal bitwise/platform equivalence iddiası değildir.

## Son basamak farklarının incelemesi

Tam fark listesi `j011-trajectory-summary.json` içindedir. Üç effective sample'da
fark görülür: primary index 968 (t=9.679999999999838), fine index 458
(t=2.2899999999999734), fine index 2634 (t=13.170000000000659).

Kaynak `aerodynamics/drag.py` smooth-Cf paydasında `(1.50*log(Re)-5.6)**2`,
Java `BasicDragEvaluator` aynı yerde `Math.pow(...,2)` kullanır. Aynı gerçek Re
girdileriyle test-only scalar-library diagnostic çalıştırıldı. Log ve denominator
eşittir; square operasyonu bu üç girdide son basamakta ayrılır. Örneğin:

```text
Re = 1473092.0228790897
log (Python / Math / StrictMath) = 14.202874166595299
denominator = 15.704311249892948
Python **2 / math.pow = 246.62539183351421
Java Math.pow / StrictMath.pow = 246.6253918335142
```

Diğer iki input ve tüm actual değerler `j011-math-library-differences.jsonl`'dedir.
Bu diagnostic ikinci physics oracle değildir; yalnız kaynak satırındaki scalar
library çağrılarını gözlemler. Production Math/StrictMath, formül veya işlem sırası
değiştirilmedi. Cf farkları nose/body/fin friction ve derived friction total'a
yayılır. Fine index 2634'te Cd0, drag/net force ve acceleration/velocity-rate'de
de küçük fark vardır. State/trajectory/event sonucu değişmez.

Primary max absolute difference **8.326672684688674e-17**, relative
**3.922873245574419e-16**; fine max absolute **8.881784197001252e-16**, relative
**4.3829501452845633e-16**. BasicDrag değerleri mevcut rtol=3e-15; dynamics
force/acceleration mevcut atol=2e-15 sınırları içindedir. Bu bound'lar mevcut
J-009 quantity contract'larıdır; J-011 için artırılmadı. Açıklanamayan veya
onaysız tolerans gerektiren fark yoktur. Tam snapshot bitwise eşitliği iddia edilmez.

## Negatif bütünlük ve geliştirme notları

Dedicated suite actual fresh Python/Java full-run artifact'larını üretir, sonra
yalnız test transport'u bozar; Python expected sonuçları Java'dan türetilmez.
31 intentional-fault child'ı anlamlı diagnostic ile exit 1 vermeli; ayrıca -O
rejection test edilir. Missing/duplicate/reordered samples; first/middle/final
sample; time/z/vz/motor mass/thrust/Cd0/acceleration; üç event time'ı; event
type/order; terminal alpha; sample/step count; reason; missing/duplicate/extra
fields; truncated/extra/empty output; empty collection/cases ve process failure
kapsanır. Enjeksiyon kurulumu hatası başarı sayılmaz.

Focused sonuç: **32 passed in 131.97 s**, exit 0. "middle-sample" ve "world-z"
vakaları aynı orta-sample corruption'ını iki requirement adına sınar; 32 ayrı
physical scenario veya 32 distinct corruption iddiası yapılmaz.
Birleşik eski+yeni integrity sonucu: **140 passed in 287.74 s**, exit 0
(108 accepted + 32 yeni). Her intentional-fault child'ı exit 1 ve beklenen
comparison/process diagnostic'ini verdi; parent suite exit 0 ile tamamlandı.

İlk checker denemesinde primary history compare tamamlandıktan sonra JSON summary
sayacı NumPy int64 olduğu için serialization hatası verdi. Yalnız test sayaçları
Python int'e çevrildi; physical değer/assertion/tolerans değişmedi. Üç run yeniden
tam çalıştırılıp geçti. İlk pytest integrity denemesinde varsayılan sandbox TEMP
oluşturulamadı; testler başlamadı (32 setup error). Açık, yeni izole `--basetemp`
yoluyla testsiz geçiş değil, bütün testlerin gerçek yeniden çalışması yapıldı.
JShell diagnostic sandbox debug socket/preferences sınırında çalışmadı; bunun
yerine normal Java test probe'u ile diagnostic yürütüldü. Kabul gate'i olarak
başarısız JShell denemesi sayılmadı. Bu execution kayıtları TEMP loglarında korunur.

## Regresyon, paket ve ortam

Windows 11 10.0.26200 amd64; Python 3.12.4; NumPy 2.5.2;
Eclipse Temurin 17.0.20.1+1; Gradle Wrapper 8.12.1.

- Full Java clean build: **436 test, 0 failure/error/skipped** (427 accepted + 9 yeni).
- Full Python regression: **1491 passed in 35.80 s**, exit 0; archive'daki 1431
  daha eski snapshot'tır, test kaybı değildir.
- J-001–J-010 parity: güncel final build üzerinde on runner'ın tamamı exit 0 verdi.
- J-011 full trajectory parity: final build üzerinde üç run/two distinct config
  tekrar çalıştırıldı; yukarıdaki complete-history sayıları ve farklar aynıdır, exit 0.
- Independent consumer: packaged JAR ile gerçek primary run **1584 sample,
  terminal ground 15.832349589466348 s**, eski consumer grupları dahil PASS.
- RuntimeClasspath: **No dependencies**; verifyProductionDependencies PASS;
  `jdeps --print-module-deps`: **java.base**.
- J-010 ve J-011 production JAR SHA-256 eşit:
  `644ba8d983e642581c4d10345220df1a905de95afbcfef9a6de2e904790c2d55`.
  Production bytecode'u bile değişmemiştir; tests/probes consumer JAR'a dahil değildir.

| Yeniden çalıştırılan önceki paket | Actual sonuç |
|---|---|
| J-001 | 6/6 distinct numeric cases PASS |
| J-002 | 95 success + 53 error + 7 trig; 155 distinct PASS |
| J-003 | 2485 success + 138 error; 2623/2622 distinct; 2475 success exact; 518/518 condition norm exact |
| J-004 | 92 success + 288 error; 380/374 distinct; 5302/5302 numeric exact |
| J-005 | 505 success + 252 error; 757/743 distinct; 1862/1862 numeric exact |
| J-006 | 236 success + 359 error; 595/548 distinct; 3570/3570 numeric exact |
| J-007 | 118 success + 299 error; 417/408 distinct; 2162/2162 numeric exact |
| J-008 | 89 success + 94 error; 183/182 distinct; 3995/3995 numeric exact |
| J-009 | 109 success + 76 error; 185/174 distinct; 6745/6745 numeric exact |
| J-010 | A/B/C/D 81 distinct: 51 success + 30 error; 7370/7370 numeric exact; 13 isolated spies, 296/296 exact |

J-002'nin önceki trig max farkı 1.1102230246251565e-16 ve J-003'ün eski 10
nonexact vakası source toleransları içinde kaldı; bunlar yeni J-011 trajectory
farklarına karıştırılmadı. Önceki test/runner/fixture/tolerans değiştirilmedi.
Clean build'in Javadoc aşaması default gösterim sınırında 100 missing-comment
uyarısı verdi; bu nonblocking documentation borcudur, tüm warning envanteri
iddiası değildir. Testler veya warning'ler bastırılmadı.

## Evidence ve yeniden çalıştırma

Core `verification/evidence/` altında:

- `j011-trajectory-summary.json`: her run/kind için tam comparison sayaçları ve
  her accepted nonexact input/field/actual Python/Java/difference/tolerance.
- `j011-trajectory-anchors.jsonl`: 27 full-precision seçilmiş kayıt; her run için
  header, initial/first/middle/last sample, üç event ve result; her iki tarafın verisi.
- `j011-math-library-differences.jsonl`: üç source-grounded library diagnostic.
- `j011-raw-trace-manifest.json`: commit edilmeyen altı complete trace'in
  filename/byte/record count/SHA-256 bilgisi. Primary/repeat için 1590, fine için
  3173 record; sample sayıları yukarıda ayrıdır. Hash'ler field comparison'ın yerine geçmez.

Tam trace'ler TEMP `roketsim-j011-trajectory-acceptance` içinde yeniden üretilebilir;
yaklaşık 84 MB raw output Git'e eklenmez. Test fixture yalnız diagnostic maxima
hesaplar; engineering sonuçlarını sunan frontend/export functionality eklenmedi.

Engine dizininde çalıştırılan Python komutları:

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/integration/simulation/test_native_demo_trajectory.py -q -s -p no:cacheprovider
.\.venv\Scripts\python.exe -B -m pytest -q -p no:cacheprovider
```

Core dizininde (makine yolları execution record'dur, production default değildir):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:/Users/ebrut/AppData/Local/Temp/roketsim-j001-gradle-cache'
.\gradlew.bat --version --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j011' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-cache' test --tests org.roketsim.core.NativeDemoTrajectoryTest --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-final' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
$env:AUD002_BUILD_DIR='C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-final'
$env:AUD002_JAVA="$env:JAVA_HOME/bin/java.exe"
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/native_trajectory_parity.py --java "$env:AUD002_JAVA" --build-dir "$env:AUD002_BUILD_DIR" --artifacts-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-trajectory-acceptance'
..\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest verification/test_parity_integrity.py verification/test_propulsion_parity_integrity.py verification/test_aerodynamics_parity_integrity.py verification/test_dynamics_parity_integrity.py verification/test_numerics_parity_integrity.py verification/test_physics_evaluator_parity_integrity.py verification/test_lifecycle_parity_integrity.py verification/test_native_trajectory_parity_integrity.py -q -s -p no:cacheprovider --basetemp 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-integrity-all'
.\gradlew.bat -p verification/consumer -I "$PWD/verification/isolated-build.init.gradle" '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-consumer' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-consumer-cache' run '-PcoreJar=C:/Users/ebrut/AppData/Local/Temp/roketsim-j011-final/libs/roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME/bin/jdeps.exe" --print-module-deps "$env:AUD002_BUILD_DIR/libs/roketsim-core-0.1.0.jar"
```

Önceki on runner aynı interpreter/Java/build ile: python_parity.py,
quaternion_parity.py, environment_parity.py, vehicle_parity.py (ek `--group all`),
propulsion_parity.py, aerodynamics_parity.py, dynamics_parity.py,
numerics_parity.py, physics_evaluator_parity.py, lifecycle_parity.py.
Execution logları TEMP/j011-*.log içinde tutulur. Pytest basetemp yeniden çalıştırılırken
yeni ve yalnız bu testlere ait izole yol seçilmelidir; mevcut evidence silinmez.

Scalar diagnostic, normal test classpath ile
`org.roketsim.core.NativeTrajectoryParityProbe --math-diagnostic 1473092.0228790897 3882023.5915640704 2659090.423762035`
komutudur. Bu sonuçlar Python `math.log`, `d**2`, `math.pow(d,2)` ve `d*d`
observations ile karşılaştırılmıştır; runtime physics veya expected trajectory üretmez.

## Exact dosya kapsamı ve sınırlar

Core-relative eklenen dosyalar:

```text
src/test/java/org/roketsim/core/NativeDemoTrajectoryTest.java
src/test/java/org/roketsim/core/NativeTrajectoryParityProbe.java
verification/native_trajectory_parity.py
verification/test_native_trajectory_parity_integrity.py
verification/evidence/j011-trajectory-summary.json
verification/evidence/j011-trajectory-anchors.jsonl
verification/evidence/j011-math-library-differences.jsonl
verification/evidence/j011-raw-trace-manifest.json
```

Değiştirilen yalnız existing
`verification/consumer/src/main/java/consumer/CoreConsumer.java` dosyasıdır.
Workspace-relative eklenen kayıt `docs/verification/j-011-native-trajectory-vv.md`.
Toplam 10 dosya: dokuz yeni, bir consumer değişikliği. Silinen dosya yoktur.
Java production, Python source/test, frontend, build/dependency configuration,
ADR ve historical verification/acceptance kayıtları değişmez.

V1 sınırlamaları: ignition start pad/contact/Liftoff değildir; rail/guide/recovery
yoktur; descent ballistiktir; fixed-step RK4 ve linear event localization vardır;
zero-AoA Basic Drag domain'i aşılmaz; attitude/moments/6DOF veya yeni wind yoktur.
Bu fixture bütün roketler için accuracy, exhaustive cross-platform parity,
OpenRocket trajectory equivalence veya frontend readiness kanıtı değildir.

Yayın ancak tüm gate'ler tamamlanınca tek descriptive J-011 commit'i ve yalnız
`migration/j-011-native-trajectory-vv` normal push'u ile yapılır. Main ve predecessor
ref'leri ayrıca doğrulanır. Gerçek final SHA/push/worktree sonucu final görev
raporunda ve bu dosyayı içeren Git commit'inde bulunur; self-referential SHA için
history rewrite veya ikinci commit yapılmaz.

Bütün mandatory verification gate'leri tamamlandı. Scoped backend sonucu:

**Independent Java 17 RoketSim Native 3DOF V1 backend: verified against the accepted Python NAT-022 demonstration scope.**
