# NAT-024 — Single-Stage Vehicle Configuration ve Auxiliary Mass Flight Integration

## Başlangıç ve kapsam

Başlangıç dalı `development/nat-023-auxiliary-mass-cg`, exact HEAD `2fadedf78a4117e742f901073461c569a637d30a`; clean worktree, NAT-023 overload/test/kayıt dosyaları ve J-011 `480f64840b92de87ee70a30a299fdb6ab169e0ef` ancestry doğrulandı. Yeni dal doğrudan NAT-023'ten: `development/nat-024-single-stage-vehicle-configuration`. NAT-023 yerel commit olarak korundu; push yapılmaz.

Amaç, açık attached donanım girdilerini mevcut Java 3DOF flight zincirine bağlamaktır. Mass/CG denklemleri NAT-023'te, motor mass/CG zamanı mevcut propulsion otoritesinde kalır. Yeni geometry/material/mass evolution, pad/rail/recovery/staging/6DOF modeli yoktur.

## Exact yeni tipler ve API

```java
public record AttachedHardwareMass(String componentId, double massKg, double cgXGeoM) {}

public record SingleStageVehicleRunConfiguration3DOF(
    SimulationRunConfiguration3DOF baseRun,
    List<AttachedHardwareMass> attachedHardware
) {}
```

Production'da yalnız bu iki record eklendi. `AttachedHardwareMass`, null/blank `componentId` için normal `IllegalArgumentException` üretir; geçerli ID değiştirilmez. Numeric mass/CG validation yapmaz. Wrapper; null base/list/element ve exact Java String equality ile duplicate ID'yi alan/indis bağlamlı `IllegalArgumentException` ile reddeder. Aynı ID farklı mass/CG ile de duplicate'dir. `List.copyOf` ile order-preserving immutable snapshot tutulur; caller listesi değişmez. `baseRun` aynı kabul edilmiş nesne referansıdır; yeni deep-copy politikası yoktur.

Donanım, structure/motor içinde veya başka bir donanım kaydında zaten sayılmış fiziksel kütle olmamalıdır. Farklı ID'lerin aynı fiziksel nesneyi adlandırdığı program tarafından kesin tespit edilemez. Global registry, component tree, overlap/mass heuristic veya otomatik deduplication yoktur.

Mevcut public metotlar korunarak yalnız şu overload'lar eklendi:

```java
public PhysicsEvaluationResult3DOF evaluate(
    double timeS, TranslationalState3DOF state,
    LaunchConditions3DOF launchConditions, PhysicsEvaluationContext3DOF context,
    double[] auxiliaryMassesKg, double[] auxiliaryCgXGeoM
);

public SimulationExecution3DOF run(SingleStageVehicleRunConfiguration3DOF configuration);
```

`PhysicsEvaluationContext3DOF` 14, `SimulationRunConfiguration3DOF` 7, `PhysicsEvaluationResult3DOF` 13 ve `RocketMassProperties` 2 record component olarak aynen kaldı; mevcut constructor/accessor'lar değiştirilmedi. Execution/result/recorded-data şemaları da aynı. `javap` packaged JAR'da eski ve yeni public imzaları doğruladı.

## Tek physics ve lifecycle otoritesi

`PhysicsEvaluator3DOF.evaluatePoint` tek private pipeline'dır. Eski dört-argümanlı giriş legacy mass yolunu seçer. Yeni altı-argümanlı girişte iki array boşsa eski public metoda doğrudan delegasyon yapılır; diğer girdiler aynı pipeline'a açık auxiliary dispatch ile gider. Public yeni girişte null array sessizce legacy sayılmaz; NAT-023 shape validation'a ulaşır.

Sıra korunur: altitude mapping → atmosphere/air properties → gravity/wind → relative flow/basic conditions → ignition'dan motor zamanı → thrust → **aynı current motor zamanında mass/CG** → NAT-023 total mass/CG → Basic Drag → dynamics → mevcut nested result. Yeni mass denklemi kopyalanmadı; `RocketMassPropertiesCalculator.evaluate(structure, currentMotor, masses, cgs)` çağrılır. Ham upstream record'larına yeni validation yoktur.

`SimulationEngine3DOF.runInternal` tek accepted RK4/event/recorder loop'udur. Legacy `run(baseRun)` aynı private lifecycle'ı legacy dispatch ile kullanır. Wrapper'ın listesi boşsa **doğrudan `run(baseRun)`** çağrılır. Nonempty list bir kez, aynı sırada iki private run-local array'e çıkarılır. `physicsAt` sadece mevcut evaluator girişlerini seçer; initial point, dört RK4 stage, her accepted endpoint ve sonraki adımlar aynı auxiliary array'leri kullanır. Array'ler dışarı açılmaz ve run sonrasında engine field'ında tutulmaz.

RK4, event detector ve recorder değiştirilmedi. k4 trial ile accepted endpoint farklı kalır; recording için endpoint physics ayrıca değerlendirilir. Interior terminal candidate rejection / exact endpoint acceptance ve event-query/termination/duration anlamları korunur. Farklı fiziksel kütlelerde event zamanlarının eşitliği beklenmez. `flightTimeS` eklenmedi.

## Validation, sonuç ve sınırlar

- Shape/identity hataları normal `IllegalArgumentException`; numeric validation record'larda tekrarlanmaz.
- Numeric auxiliary hataları NAT-023 `MassValidationError` olarak code/field/value ile aynen yayılır: `INVALID_AUXILIARY_MASS`, `INVALID_AUXILIARY_CG`; indis sırası ve mass-before-CG korunur.
- Mevcut total-mass/CG derived errors ile upstream atmosphere/propulsion/aero/dynamics/numerics/engine hataları wrap edilmez. Accepted legacy evaluation ordering korunur; örneğin atmosphere hatası daha sonra değerlendirilmesi gereken auxiliary sayısal hatadan önce çıkar.
- `physics.rocketMassProperties()` tek recorded total mass/CG otoritesidir. Recorder'da formula veya ikinci scalar authority yoktur. Engine/evaluator stateless ve repeated-run history bağımsızdır.
- Bütün hardware attached ve zamandan bağımsızdır. Recovery hardware adı deployment veya mass removal tetiklemez; deployment/aerodynamics NAT-034/NAT-035'e ertelidir. Off-axis CG, inertia, moments, attitude, rail/pad, staging ve payload separation yoktur.

## V&V ve test eşlemesi

Yeni `SingleStageVehicleConfigurationTest`, eski fixture yardımcılarını tekrar kullanır; kabul edilmiş test/oracle'lar değiştirilmedi. RED'de eksik iki tip/overload nedeniyle **44 compile error, exit 1** görüldü; bu aşamada testler çalışmadı. İlk GREEN ve son seçici tekrarda **18 NAT-024 test** geçti.

| Frozen gruplar | Gerçek kontrol |
|---|---|
| CFG24-T01–T04 | Reflection ile exact yeni record field/type ve eski şemalar; public entrypoint'ler; list snapshot ve base referans sınırı |
| T05/T06 | Null/blank/duplicate identity ve null entry; record'ların kabul ettiği numeric-invalid değerlerin physics/engine üzerinden NAT-023 code/field/value ile reddi; ±Infinity dahil |
| T07/T15 | Empty wrapper ile bütün gerçek J-011 execution history'si **exact equals**; evaluator empty-array exact snapshot equality; mevcut NativeDemo testleri ve Python parity |
| T08/T09 | Aynı physical point'te NAT-023 sonucu ile nested mass/CG eşit; upstream atmosphere/flow/motor/drag ve thrust/drag force korunur; acceleration `(Fthrust+Fdrag)/m+g` ile kontrol edilir |
| T10 | Accepted explicit-source-curve test motoru, real RK4 ve evaluator ile dört stage ayrı current time/state'te yeniden kurulur; motor mass **ve CG** değişimi doğrulanır; engine endpoint'i bu bağımsız tek-step kurulumuyla exact eşit, k4 trial'dan farklı |
| T11 | Nonempty gerçek uçuşun **her** accepted sample'ında current motor ve total mass/CG doğrudan kendi accepted evaluator/calculator otoriteleriyle eşit; endpoint physics current accepted point'e ait |
| T12 | Nonempty flight'ta BURNOUT/APOGEE/GROUND query cardinality/order; terminal estimate vs last accepted; maximum-step ve nonzero-start duration; flightTimeS yokluğu |
| T13/T14 | Caller list mutation isolation, repeated runs exact/deterministic, farklı inventories bağımsız, sonrasında legacy run değişmemiş |
| T16 | Dar source diff; tek evaluator/engine/mass calculator, tek loop, yalnız iki yeni immutable record; yeni fizik/runtime dependency yok |

NAT-023'ün frozen analitik fixture'ları yeniden çalıştırılan `AuxiliaryMassTest` kapsamındadır: A için ≈0.80 kg / 0.542 kg·m / 0.6775 m; supplied motor snapshot B için ≈0.75 kg / 0.492 kg·m / 0.6560 m. NAT-024, bu testleri kopyalamaz: gerçek F50/source-curve motor snapshot'larının integration sonucunu NAT-023 otoritesiyle karşılaştırır. Mass/CG yeri ayrı kaldığından aynı toplam mass ile CG'nin kaydırılması current 3DOF acceleration'ı değiştirmez; bu kabul edilmiş moments/attitude yokluğudur.

Yeni single-point acceleration assertion'ı mevcut NAT-014/J-007/J-009 **atol=2e-15** sözleşmesini kullanır; yeni numerical tolerance yoktur. Legacy/empty execution, snapshot, stage endpoint ve current-authority karşılaştırmaları exact equality'dir. Nonempty trajectory legacy kütleden farklı olduğu için tarihsel event zamanlarına zorla eşitlenmez.

## Çalıştırılan seçici doğrulama

Ortam: Windows 11 amd64; Temurin java/javac **17.0.20.1+1**; Gradle Wrapper **8.12.1**; Python **3.12.4**, NumPy **2.5.2**. Mevcut locked JUnit ve production dependency politikası değişmedi.

Core dizininden, mevcut isolated-build ile:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir '<build-dir>' clean assemble test --tests org.roketsim.core.SingleStageVehicleConfigurationTest --tests org.roketsim.core.AuxiliaryMassTest --tests org.roketsim.core.RocketMassTest --tests org.roketsim.core.PhysicsEvaluatorTest --tests org.roketsim.core.SimulationEngineTest --tests org.roketsim.core.SimulationResultTest --tests org.roketsim.core.FlightRecorderTest --tests org.roketsim.core.FlightEventsTest --tests org.roketsim.core.NativeDemoTrajectoryTest --tests org.roketsim.core.MotorPropertiesTest --tests org.roketsim.core.ClassicalRK4Test verifyProductionDependencies --offline --no-daemon
```

| Suite / kapı | Gerçek sonuç |
|---|---|
| NAT-024 SingleStageVehicleConfigurationTest | 18 passed |
| NAT-023 AuxiliaryMassTest / RocketMassTest | 26 / 7 passed |
| PhysicsEvaluatorTest / SimulationEngineTest / SimulationResultTest | 13 / 5 / 6 passed |
| FlightRecorderTest / FlightEventsTest | 6 / 6 passed |
| NativeDemoTrajectoryTest / MotorPropertiesTest / ClassicalRK4Test | 9 / 7 / 11 passed |
| Seçilen toplam | **114 test, 0 failure/error/skipped**, exit **0**; XML raporlarından sayıldı |
| Clean Java17 assemble / test compile / verifyProductionDependencies | Başarılı, exit 0; JAR/sources/Javadoc üretildi |
| Packaged JAR javap / jdeps | İstenen public API'ler; runtime yalnız **java.base**, exit 0 |

Son test kaynağına kontrollü noktada thrust/drag kuvvetlerinin değişmediğini doğrudan gösteren assertion'lar eklendikten sonra aynı 11 sınıfın `test --tests ...` komutu tekrarlandı; sonuç yine **114 passed**, exit 0. Production kaynakları bu tekrar arasında değişmedi. Javadoc gösterim limitinde 100 documentation warning verdi; build'i engellemedi, tam warning envanteri iddiası değildir.

Değiştirilmemiş parite araçları, gerçek Python referansı ve aynı final Java sınıflarıyla `-B` kullanılarak çalıştırıldı:

```powershell
$nat024Python='../roketsim-native-engine/.venv/Scripts/python.exe'
$nat024Java='<JAVA_HOME>/bin/java.exe'
$nat024Build='<local-evidence>'
& $nat024Python -B verification/physics_evaluator_parity.py --java $nat024Java --build-dir $nat024Build
& $nat024Python -B verification/lifecycle_parity.py --java $nat024Java --build-dir $nat024Build
& $nat024Python -B verification/native_trajectory_parity.py --java $nat024Java --build-dir $nat024Build --artifacts-dir '<build-dir>'
```

| Legacy parite | Gerçek sonuç, hepsi exit 0 |
|---|---|
| J-009 PhysicsEvaluator | 185 total / 174 distinct; 109 success + 76 expected error; **6745/6745 numeric exact** |
| J-010 lifecycle A/B/C/D | 81 total/distinct; 51 success + 30 expected error; **7370/7370 numeric exact** |
| J-010 isolated legacy lifecycle spies | 13 total/distinct; **296/296 numeric exact**; call/order/interior-terminal/error akışı korunur |
| J-011 complete trajectory | 3 run / 2 distinct config; primary/repeat 1584, fine 3167 sample; her birinde 3 event; PASS |
| J-011 distinct primary+fine | **442261 numeric comparison, 442225 exact, 36 accepted nonexact**; numeric sayılar shape/index metadata'sını da içerir |

Trajectory'deki nonexact farklar önceki J-011 kabulündeki upstream son-bit farklarıdır; max absolute **8.881784197001252e-16**, mevcut quantity-specific toleranslar içinde. Position/velocity/time/event için yeni tolerans veya değişmiş anchor yoktur. Repeat dahil 46 accepted nonexact vardır; evrensel bitwise eşitlik iddia edilmez. Bu parity kapıları legacy yolu doğrular; yeni attached-hardware özelliğinin Python'da mevcut olduğu iddia edilmez.

Log ve generated trace'ler `<local-evidence>`, JUnit XML'leri final build'in `test-results/test/` dizinindedir; repository'ye eklenmez. Full repository test kampanyası veya genel model audit'i yapılmadı; historical parity/integrity araçları ve oracle'lar değiştirilmedi.

## Kabul, Git ve kalan sınırlar

Yalnız dört production dosyası, yeni focused test ve bu kayıt NAT-024 kapsamındadır. NAT-023 calculator/test/kayıtları, mevcut context/result/configuration şemaları, Python, ADR'ler, V2-000 ve tarihsel kaynaklar korunur. Tek pipeline/loop ve transient input dispatch dışında yeni mimari katman yoktur. Material bir uyumsuzluk veya açıklanamayan yeni parite farkı gözlenmedi.

Başlangıç commit'i yukarıda exact SHA ile verilmiştir. Nihai yerel acceptance commit'i bu dosyayı ekleyen commit'tir; kesin SHA final görev raporunda ve repository kökünden `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-024-single-stage-vehicle-configuration.md` ile bulunur. Self-referential commit SHA için ikinci commit/amend yapılmaz. İstenen mesaj: `feat(simulation): integrate NAT-024 single-stage vehicle configuration`. Push/merge/main veya J-011/NAT-023 history değişikliği yoktur. NAT-025 başlatılmaz.

Kabul önerisi: **NAT-024 PASS**; donanım inventory'si düşük-seviye physical ownership kanıtı değildir ve deployment/separation/attitude kapsamı değildir.
