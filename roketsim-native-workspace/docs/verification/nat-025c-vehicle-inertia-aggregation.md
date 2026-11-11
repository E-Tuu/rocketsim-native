# NAT-025C — Vehicle Inertia Aggregation

## Git ve local API preflight

2026-10-31. Temiz başlangıç dalı `development/nat-025b-component-inertia-models`, exact HEAD **`74d2df60d62f9b0c79be3332fd04a63c4bb56af6`** doğrulandı. NAT-025A `0aa3e9b70a13b229ebed3933824ce93916ecb3d7`, NAT-025, NAT-024, NAT-023 ve J-011 ancestry korunur. Yerel SHA'ların remote'da olduğu varsayılmadı. API uyumu doğrulandıktan sonra doğrudan bu HEAD'den `development/nat-025c-vehicle-inertia-aggregation` oluşturuldu; push/merge yapılmaz.

| Doğrulanan mevcut otorite | Exact local API / kullanım |
|---|---|
| NAT-023/024 mass input | `mass.AttachedHardwareMass(String componentId,double massKg,double cgXGeoM)` |
| Accepted current vehicle snapshot | `simulation.PhysicsEvaluationResult3DOF.rocketMassProperties()` → `mass.RocketMassProperties.totalMassKg()/totalCgXGeoM()` |
| Accepted auxiliary path | `simulation.PhysicsEvaluator3DOF.evaluate(time,state,launch,context,double[] masses,double[] cgs)`; mevcut NAT-023 four-arg calculator overload'u kullanır |
| Structural input | `mass.StructuralMassProperties.nose()/body()/fins()/motorMount()/centeringRings()`; her `ComponentMassProperties` için `massKg()/cgXGeoM()`; aggregate `structureMassKg()/structureCgXGeoM()` |
| Structural inertia | `mass.StructuralComponentInertias` aynı beş tensor accessor'ını sağlar; fin-set/ring-pair zaten kendi group CG'lerinde aggregated |
| Motor installation | `propulsion.MotorInstallation.motor()/motorFrontXGeoM()/motorAftXGeoM()`; diğer accepted installation fields değiştirilmez |
| Explicit mass/CG policy | `propulsion.MotorPropertyModelProfile.massModel()/cgModel()`; constructor zorunlu accepted enum policies'i doğrular |
| Current motor | `propulsion.MotorPropertyEvaluator.evaluate(MotorInstallation,Object,double motorTimeS)` → `MotorMassProperties.massKg()/cgLocalFromFrontM()/cgXGeoM()` |
| Motor statistics | `propulsion.MotorCurveAnalyzer.analyze(MotorDefinition)` → accepted curve-end statistics |
| Motor inertia | `propulsion.MotorInertiaEvaluator.evaluate(MotorDefinition,MotorMassProperties,MotorCurveStatistics,double,MotorInertiaModel)` → `MotorInertiaResult.inertiaAtMotorCg()/modelKind()/provenanceReference()` |
| Tensor/translation | `mass.InertiaTensor3D` altı scalar; `ParallelAxisInertiaCalculator.shiftFromCentroid(InertiaTensor3D,double,math.Vector)` |

NAT-025 clock kodu da incelendi: NAT-025C ignition-time authority yaratmaz; çağıran aynı snapshot'ın nonnegative motor-local zamanını sağlar. Mevcut API'lere adapter, yeni snapshot tipi veya upstream schema değişikliği gerekmedi.

## Dört yeni production tipi — exact public API

Hepsi `org.roketsim.core.mass`; propulsion tipleri mevcut `org.roketsim.core.propulsion` paketinden import edilir.

```java
public record AttachedHardwareInertia(
    String componentId, InertiaTensor3D inertiaAtComponentCgBody,
    double cgBodyXM, double cgBodyYM, String provenanceReference
) {}
public record VehicleInertiaEvaluationRequest(
    StructuralMassProperties structuralMassProperties,
    StructuralComponentInertias structuralComponentInertias,
    MotorInstallation motorInstallation,
    MotorPropertyModelProfile motorPropertyModelProfile,
    MotorInertiaModel motorInertiaModel,
    double motorTimeS,
    List<AttachedHardwareMass> attachedHardwareMasses,
    List<AttachedHardwareInertia> attachedHardwareInertias,
    double authoritativeTotalMassKg, double authoritativeCgXGeoM,
    double massConsistencyToleranceKg, double cgConsistencyToleranceM
) {}
public record VehicleInertiaResult(
    double motorTimeS, double totalMassKg, double totalCgXGeoM,
    InertiaTensor3D inertiaAtVehicleCgBody,
    MotorInertiaModelKind motorInertiaModelKind,
    String motorInertiaProvenanceReference, List<String> includedComponentIds
) {}
public final class VehicleInertiaEvaluator {
    public VehicleInertiaResult evaluate(VehicleInertiaEvaluationRequest request);
}
```

Compact constructors null/finite/domain/string validation ve immutable list snapshots sağlar. Calculator'da mutable instance state/cache/history yoktur; yalnız altı reserved ID'lik private static final immutable list bulunur. Yeni constructor default motor modeli veya overload yoktur. Model/provenance mevcut motor inertia sonucundan değiştirilmeden kopyalanır.

## Included-component completeness

Her evaluation, beş mevcut structural group ve installed motoru birer kez içerir. Stable reserved IDs:

```text
structure:nose
structure:body
structure:fins
structure:motor-mount
structure:centering-rings
motor:installed
```

Attached masses otoritatif inventory'dir. Her mass ID'si için exact String equality ile bir inertia kaydı gerekir. Missing, unmatched, duplicate mass/inertia ID ve reserved-ID collision normal `IllegalArgumentException` ile componentId bağlamında reddedilir. Eşleme list index'iyle yapılmaz; inertia list order'ı sonucu değiştirmez. Sonuç sırası stable altı ID + mass-list order'ıdır; set yalnız gerçekten aggregated contributor'lardır.

İki liste boşsa auxiliary inertia gerekmez. Olmayan payload/avionics/recovery gibi isimler için default requirement veya zero tensor üretilmez. Sıfır tensor yalnız açıkça supplied tensor veya matematiksel toplamanın başlangıç identity'si olabilir; missing veri yerine konulmaz. Farklı ID'lerin aynı fiziksel hardware'i adlandırdığını matematikten kesin saptamak mümkün değildir; NAT-024 physical ownership/double-accounting sorumluluğu çağıranda kalır.

Attached tensor önceden component'in kendi CG'sinde BODY axes'e ifade edilmiş olmalıdır. Her iki transverse CG offset'i explicit field'dır; finite ve **tam sıfır** olmalıdır (signed zero kabul edilir). Nonzero offset epsilon/clamp ile onarılmaz. Nonblank provenance aynen korunur; certification veya full physical realizability kanıtı değildir. NAT-025A'nın finite/nonnegative-diagonal validation sınırı değişmez.

## Aynı-zaman motor ve accepted mass/CG otoritesi

`request.motorTimeS()` değişmeden hem MotorPropertyEvaluator hem MotorInertiaEvaluator'a geçer. MotorCurveAnalyzer aynı installed motor tanımından statistics üretir. Motor mass/CG veya thrust/impulse formülü NAT-025C içinde tekrarlanmaz; ignition çıkarımı yoktur. İlgili upstream model hataları başka modele fallback edilmeden yayılır.

Yalnız validation için ordered five-component mass sum ve weighted axial first moment hesaplanır. Five-component sum, accepted structural total ile `massConsistencyToleranceKg` üzerinden karşılaştırılır. Ardından aynı current motor ve included hardware mass/CG katkıları eklenerek contributor mass sum ve weighted CG, supplied authoritative totals ile karşılaştırılır. Bütün ara product/sum/difference ve derived CG finite olmalıdır. Toleranslar explicit finite/nonnegative request girdisidir; hidden global tolerance yoktur. Negatif/zero component mass ve nonfinite CG reddedilir.

Bu validation sonuçları yeni runtime mass/CG otoritesi değildir: **result totalMassKg ve totalCgXGeoM supplied authoritative scalar'ların aynısıdır**; tensor translation reference'ı da supplied CG'dir. Tolerance içindeki farklar normalize edilmez/değiştirilmez. Caller bu iki scalar'ı aynı NAT-024 vehicle-state snapshot'ından ve aynı attached inventory'den sağlamalıdır. Numeric consistency, kaynak/provenance veya state identity'sinin bağımsız sertifikasyonu değildir.

## BODY/x_geo ve aggregation

SI kg, m, kg*m²; x_geo nose-tip origin, aft-positive; BODY +Z forward/longitudinal. Current coaxial mapping açıkça:

```text
d_i_BODY = [0,0,authoritativeCgXGeoM - componentCgXGeoM]
I_vehicle = sum(PAT(I_i,m_i,d_i_BODY))
```

NAT-025A calculator her contribution için çağrılır. Fin set ve ring pair yeniden bölünmez. Altı tensor term'i sırayla finite kontrolüyle toplanır; off-diagonal component'ler atılmaz veya axisymmetry'ye zorlanmaz. Tensor rotation yapılmaz. Common axial origin translation relative distances'ı ve tensoru değiştirmez. Genel off-axis hardware veya gerçek 3D vehicle CG desteği iddia edilmez.

## Focused V&V ve ilk test bulgusu

Yeni `VehicleInertiaTest`, 15 JUnit method içinde VC25C-01–16'yı kapsar; VC25C-17–18 aşağıdaki regression/parity ile karşılanır. Existing test helpers/fixtures tekrar kullanılır, historical testler değiştirilmez.

| Frozen kontrol | Gerçek kanıt |
|---|---|
| 01 point-mass | Matematiksel fixture'da altı zorunlu contribution CG=0; iki ek 1kg contribution x=−1/+1, explicit zero tensors → exact (Ixx,Iyy,Izz)=(2,2,0), cross=0 |
| 02/03 once-only aggregation | Altı ayrı source tensor ve unit mass ile CG=0'da exact diagonal (21,27,33), cross (1.3125,2.625,5.25); motor mount ile motor ayrı ID |
| 04/05 dynamic snapshot | Existing synthetic source motor mass/CG profile ve explicit inertia curve; .25/.75 s mass .09/.07 kg, local CG .045/.055 m; değişen vehicle CG ve structural shifts bağımsız scalar sum ile kontrol edilir |
| 06 zero auxiliaries | Gerçek F50 request ve empty lists başarılı, tam altı ID; future optional isimler aranmaz |
| 07/14 complete coverage/full tensor | Avionics/recovery fixture mass'larıyla exact ID mapping; inertia list ters sırada da aynı sonuç; signed cross terms aynen kaynak toplamıdır |
| 08/09 missing/unmatched/duplicate | Mesajda componentId; duplicate mass farklı mass/CG ile de reddedilir; altı reserved collision ayrı kontrol edilir |
| 10 transverse boundary | ±nonzero, smallest nonzero double, NaN/±Infinity ret; no epsilon veya unspecified-offset default |
| 11/12 authority mismatch | Yanlış mass/CG, stale motor-time snapshot ve structural component/aggregate mass mismatch reddedilir; explicit tolerance içinde otoritatif değerler aynen korunur |
| 13 origin translation | Bütün component/motor/hardware axial coordinates ve reference +8m; exact same tensor |
| 15 domains/overflow | Null required refs/list entries; invalid scalar/tolerance/mass/CG; first-moment, contributor-sum, PAT square ve final tensor-sum overflow ret |
| 16 ownership/repeatability | Caller list mutation isolation; unmodifiable lists; repeated/fresh evaluator ve farklı requests bağımsız |
| Recorded snapshot | Gerçek NAT-024 configured engine run'ın her accepted sample'ındaki nested mass/CG/time aynen tüketilir; standalone missing-inertia hatası sonraki configured/legacy run sonuçlarını değiştirmez |

Point-mass zero source tensorları yalnız matematiksel fixture'dır; gerçek F50 source/geometry veya measured inertia iddiası değildir. Gerçek F50 request helper'ı authoritative totals'ı doğrudan mevcut NAT-024 PhysicsEvaluator overload'undan alır. Recorded-sample testi ise gerçek recorder snapshot'ını kullanır. İnertia oracle'ı under-test VehicleInertiaEvaluator'ı çağırarak kurulmaz; scalar axial-shift sum ve güvenilen upstream motor sonucu kullanılır. Dynamic motor curve axial term'i bağımsız .000125/.000175 fixture'larıyla ayrıca kontrol edilir.

İlk focused koşu **14 test / 1 failure / exit 1**: `twoIncludedHardwareEntriesMatchByExactIdNotPositionAndPreserveCrossTerms`, beklenen literal `0.001125`, gerçek `0.0011250000000000001`. Kaynak doubles `.000375 + .00075` toplamı ikinci değerdir; separately rounded decimal literal ilk değerdir. Bu yeni assertion, fiziksel cross-term korunumu gereğince **supplied iki source term'in exact toplamı** ile düzeltildi. Üretim kodu, historical fixture veya tolerance değiştirilmedi. RED XML `%TEMP%/roketsim-nat025c-initial-red.xml` olarak clean build dışında saklandı. Ek recorded-snapshot testiyle sonraki focused koşu **15 passed / exit 0**; aşağıdaki clean regression da aynı sonucu doğruladı.

Analitik nonexact scalar karşılaştırmalar mevcut `GeometryResolverTest.close(...,3e-14)` convention'ını kullanır. Toleranslı request fixture'ları bu convention'a göre explicit mass/CG consistency values sağlar; point fixture'lar tolerance=0 kullanır. Yeni global numerical policy yoktur; schema/IDs/source values ve uygun exact fixtures exact kontrol edilir.

## Çalıştırılan build ve regression

Windows 11 amd64; Temurin java/javac **17.0.20.1**, runtime **17.0.20.1+1**; Gradle Wrapper **8.12.1**; Python reference **3.12.4 / NumPy 2.5.2**. Core dizininden mevcut isolated-build:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir '<build-dir>' clean assemble test --tests org.roketsim.core.VehicleInertiaTest --tests org.roketsim.core.StructuralInertiaTest --tests org.roketsim.core.MotorInertiaTest --tests org.roketsim.core.InertiaTensorFoundationsTest --tests org.roketsim.core.StructuralMassTest --tests org.roketsim.core.RocketMassTest --tests org.roketsim.core.AuxiliaryMassTest --tests org.roketsim.core.SingleStageVehicleConfigurationTest --tests org.roketsim.core.FlightPhaseClockTest --tests org.roketsim.core.PhysicsEvaluatorTest --tests org.roketsim.core.SimulationEngineTest --tests org.roketsim.core.NativeDemoTrajectoryTest --tests org.roketsim.core.GeometryModelsTest --tests org.roketsim.core.GeometryResolverTest --tests org.roketsim.core.MotorPropertiesTest --tests org.roketsim.core.MotorCatalogTest --tests org.roketsim.core.MotorInstallationTest --tests org.roketsim.core.MotorThrustTest --tests org.roketsim.core.SimulationResultTest --tests org.roketsim.core.FlightEventsTest --tests org.roketsim.core.FlightRecorderTest --tests org.roketsim.core.ClassicalRK4Test verifyProductionDependencies --offline --no-daemon
```

| Suite | Passed |
|---|---:|
| VehicleInertiaTest | 15 |
| StructuralInertiaTest / MotorInertiaTest / InertiaTensorFoundationsTest | 8 / 12 / 14 |
| StructuralMassTest / RocketMassTest / AuxiliaryMassTest | 23 / 7 / 26 |
| SingleStageVehicleConfigurationTest / FlightPhaseClockTest | 18 / 14 |
| GeometryModelsTest / GeometryResolverTest | 3 / 31 |
| MotorPropertiesTest / MotorCatalogTest / MotorInstallationTest / MotorThrustTest | 7 / 12 / 9 / 7 |
| PhysicsEvaluatorTest / SimulationEngineTest / NativeDemoTrajectoryTest | 13 / 5 / 9 |
| SimulationResultTest / FlightEventsTest / FlightRecorderTest / ClassicalRK4Test | 6 / 6 / 6 / 11 |
| **Toplam** | **262** |

XML raporları: **15 yeni + 247 mevcut, 0 failure/error/skipped; exit 0**. Clean Java17 compile/JAR/sources/Javadoc ve verifyProductionDependencies başarılı. Javadoc mevcut gösterim limitinde 100 nonblocking warning verdi; tam warning envanteri iddiası değildir. Packaged JAR javap exact 5/12/7-component record şemalarını ve public evaluate imzasını doğruladı. `jdeps --print-module-deps` yalnız **java.base**, exit 0. Yeni runtime/build dependency yoktur. Genel model audit veya full repository kampanyası yapılmadı.

## Gerçek J-011 Python–Java trajectory parity

Değiştirilmemiş historical checker gerçek accepted Python engine ile çalıştırıldı:

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS / exit 0**. 3 run / 2 distinct configuration; primary/repeat 1584, fine 3167 sample; her koşuda 3 event. Distinct primary+fine 442261 numeric comparison, 442225 exact, 36 accepted nonexact; repeat dahil 46 nonexact. Max absolute 8.881784197001252e-16, max relative 4.3829501452845633e-16; mevcut upstream last-bit farkları kabul edilmiş quantity-specific toleranslar içinde. Yeni anchor/tolerance veya evrensel bitwise eşitlik iddiası yoktur. NAT-025C flight pipeline'a bağlanmadığından bu eski trajektori regresyonudur; yeni inertia capability için Python parity oracle'ı iddia edilmez.

Loglar `%TEMP%/roketsim-nat025c-focused.log`, `roketsim-nat025c-focused-green.log`, `roketsim-nat025c-regression.log`, `roketsim-nat025c-trajectory.log`; XML/build ve trajectory traces yukarıdaki TEMP dizinlerinde, commit dışında tutulur.

## Son scope ve bilinen sınırlar

Yalnız dört yeni production dosyası, `VehicleInertiaTest.java` ve bu kayıt eklenir. Mevcut NAT-023/024/025/A/B API'leri, mass/CG otoriteleri, Python/Java historical sources/testler, simulation/event/RK4/recorder/result davranışı ve eski verification kayıtları değiştirilmedi. B.2 primitive geometry, yeni component family, 3D CG, off-axis aggregation, rotation, staging veya rotational dynamics yoktur. Same-state snapshot ve source tensor doğruluğu caller sorumluluğundadır; numerical completeness/consistency fiziksel certification yerine geçmez. Çözümlenmemiş implementation blocker veya upstream API sapması gözlenmedi.

Yerel acceptance commit mesajı `feat(inertia): add NAT-025C vehicle inertia aggregation`. Bu kaydı ekleyen commit nihai kimliktir; exact SHA final raporda ve repository kökünden `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-025c-vehicle-inertia-aggregation.md` ile bulunur. Self-referential SHA için amend yapılmaz. Commit sonrası clean status final raporda doğrulanır. Push/merge/main veya historical branch değişikliği yapılmaz; NAT-026 başlatılmaz.
