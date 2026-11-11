# NAT-025 — Flight Phase & Propulsion Status/Clock

## Başlangıç ve kapsam

2026-10-29: temiz çalışma ağacı ve exact NAT-024 HEAD `9768e3f59f6f552c989da897bb28dff1ad269228` doğrulandı. Başlangıç dalı `development/nat-024-single-stage-vehicle-configuration`; NAT-023 `2fadedf78a4117e742f901073461c569a637d30a` ancestry içinde. Yeni dal doğrudan bu HEAD'den: `development/nat-025-flight-phase-propulsion-clock`. NAT-023/024 yerel commit'leri korunur; push yapılmaz.

Amaç yalnız bağımsız, anlık phase/clock sınıflandırmasıdır. Mevcut simulation lifecycle'a bağlanmaz; yeni fizik, transition, scheduler veya event üretimi yoktur. Yalnız dört onaylı production dosyası, bir test ve bu kayıt eklenir; mevcut kaynaklar değiştirilmez.

## Exact API ve otoriteler

Hepsi `org.roketsim.core.simulation` paketinde:

```java
public enum FlightPhase3DOF {
    PAD_CONTACT, GUIDED_FLIGHT, FREE_FLIGHT, RECOVERY_DESCENT, LANDED
}
public enum PropulsionStatus3DOF {
    PRE_IGNITION, CURVE_ACTIVE, POST_BURNOUT
}
public record FlightPhaseClockState3DOF(
    FlightPhase3DOF flightPhase, PropulsionStatus3DOF propulsionStatus,
    double motorTimeS, double burnoutTimeS
) {}
public final class FlightPhaseClockEvaluator3DOF {
    public FlightPhaseClockState3DOF evaluate(
        double absoluteTimeS, FlightPhase3DOF flightPhase,
        PropulsionTimeline propulsionTimeline, MotorCurveStatistics motorStatistics
    );
}
```

Evaluator alan/cache/history tutmaz. Record yalnız snapshot'tır; ikinci validation veya clock politikası taşımaz. Phase açık girdidir ve aynen korunur. `PropulsionTimeline.ignitionTimeS()` tek ignition otoritesi; `MotorCurveStatistics.curveEndTimeS()` curve endpoint otoritesidir. Thrust ve motor mass/CG hesaplanmaz; mevcut evaluator otoriteleri korunur.

SI saniye ve kabul edilmiş double işlem sırasıyla:

```text
motorTimeS = absoluteTimeS - ignitionTimeS
burnoutTimeS = ignitionTimeS + curveEndTimeS
absoluteTimeS < ignitionTimeS → PRE_IGNITION
aksi halde absoluteTimeS < burnoutTimeS → CURVE_ACTIVE
aksi halde → POST_BURNOUT
```

Burnout alanı mutlak zamandır, duration değildir. Negatif motor zamanı ve negatif ignition geçerlidir. Ignition anı CURVE_ACTIVE; final zero-thrust curve endpoint anı POST_BURNOUT. CURVE_ACTIVE pozitif thrust garantisi değildir. 5% effective-burn istatistikleri, ejection delay ve event geçmişi sınıflandırmaya katılmaz. Tolerans, snapping, clamp veya boundary repair yoktur.

## Validation ve hata davranışı

Sıra: finite `absolute_time_s`; null `flight_phase`; null `propulsion_timeline`; null `motor_statistics`; ignition ve curve end okuma; finite/pozitif `curve_end_time_s`; motor-time subtraction ve finite `motor_time_s`; burnout addition ve finite `burnout_time_s`; burnout > ignition kontrolü; classification; result.

Mevcut `NumericalValidation.requireFinite` kullanılır. Tüm retler normal `IllegalArgumentException`; null kontrolleri de NPE üretmez. Alan bağlamı mesajdadır. Motor-time overflow, burnout overflow ve pozitif interval'in floating-point nedeniyle ignition'a collapse olması reddedilir; epsilon veya nextUp ile onarım yapılmaz. nextUp/nextDown yalnız test sınır girdileridir. Diğer statistics alanlarına yeni validation eklenmez.

Pre-ignition classifier için geçerlidir; mevcut PhysicsEvaluator'ın `BEFORE_IGNITION_UNSUPPORTED` davranışı değiştirilmedi. Status event occurrence değildir. FlightEventType yalnız BURNOUT/APOGEE/GROUND olarak kalır. Simulation/context/result/recorder şemalarına phase veya yeni clock alanı eklenmedi.

## Analitik V&V

Gerçek F50 katalog motorunun `MotorCurveAnalyzer` sonucu kullanıldı: curve end exact `1.43`.

| Fixture | Mutlak zamanlar | Status sırası | Clock kontrolü |
|---|---|---|---|
| A, ignition 0 | −0.20, 0, 0.50, computed 1.43, 2 | PRE, ACTIVE, ACTIVE, POST, POST | motor time girdiye exact eşit; burnout 1.43 |
| B, ignition 5 | 4.5, 5, 6, computed `5+1.43`, computed boundary+1 | PRE, ACTIVE, ACTIVE, POST, POST | subtraction/addition sonucu exact eşit |
| C, ignition −2 | −2.5, −2, −1, computed `−2+1.43`, boundary+1 | PRE, ACTIVE, ACTIVE, POST, POST | negatif mutlak ignition ve motor zamanı korunur |
| D/E | Beş phase × pre/active/post | 15 kombinasyonun tamamı | phase korunur; aynı zamanda clock/status eşit |
| F | Source başlangıcı ve 1.4 s tail | ACTIVE | t=0 gerçek thrust=0; 1.4 effective-burn sonrasıdır |

Üç ignition girdisinde exact boundary, immediately-before ve immediately-after test edildi. NaN/±Infinity absolute time ve curve end; zero/negative curve end; null'ların ve invalid curve end'in validation precedence'i; her iki subtraction overflow işareti; addition overflow; pozitif/negatif büyük ignition'da collapsed boundary; en küçük pozitif distinguishable interval; repeated/fresh evaluator determinism doğrulandı. Analitik testlerde yeni tolerans yoktur: enum identity ve aynı işlemlerle kurulan beklenen double değerleri exact karşılaştırılır.

## Çalıştırılan seçici doğrulama

Windows 11 amd64; Temurin java/javac 17.0.20.1 (runtime 17.0.20.1+1), Gradle Wrapper 8.12.1. Parite referansı Python 3.12.4 / NumPy 2.5.2. Wrapper checksum/configuration değiştirilmedi.

Core dizininde mevcut isolated-build prosedürü:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir '<build-dir>' clean assemble test --tests org.roketsim.core.FlightPhaseClockTest --tests org.roketsim.core.SingleStageVehicleConfigurationTest --tests org.roketsim.core.AuxiliaryMassTest --tests org.roketsim.core.RocketMassTest --tests org.roketsim.core.PhysicsEvaluatorTest --tests org.roketsim.core.SimulationEngineTest --tests org.roketsim.core.SimulationResultTest --tests org.roketsim.core.FlightRecorderTest --tests org.roketsim.core.FlightEventsTest --tests org.roketsim.core.NativeDemoTrajectoryTest --tests org.roketsim.core.MotorPropertiesTest --tests org.roketsim.core.ClassicalRK4Test verifyProductionDependencies --offline --no-daemon
```

Önce yalnız yeni test eklendi: aynı isolated test komutunun `test --tests org.roketsim.core.FlightPhaseClockTest` biçimi, production tipleri henüz yokken **66 compile error / exit 1** (beklenen RED; testler çalışmadı). Dört tip eklendikten sonra focused **14 passed / exit 0**. Son clean assemble ve seçici regression:

| Suite | Passed |
|---|---:|
| FlightPhaseClockTest (NAT-025) | 14 |
| SingleStageVehicleConfigurationTest (NAT-024) | 18 |
| AuxiliaryMassTest (NAT-023) / RocketMassTest | 26 / 7 |
| PhysicsEvaluatorTest (timeline validation ve pre-ignition dahil) | 13 |
| SimulationEngineTest / SimulationResultTest | 5 / 6 |
| FlightEventsTest / FlightRecorderTest | 6 / 6 |
| NativeDemoTrajectoryTest / MotorPropertiesTest / ClassicalRK4Test | 9 / 7 / 11 |
| **Toplam** | **128** |

JUnit XML: **0 failure/error/skipped**, exit **0**; 14 yeni + 114 mevcut regression. Ayrı timeline test sınıfı yoktur; mevcut PhysicsEvaluatorTest timeline kontrolleri çalıştırıldı. Build JAR/sources/Javadoc oluşturdu; verifyProductionDependencies başarılı. Javadoc mevcut gösterim limitinde 100 warning verdi, blocking değil; tam warning envanteri iddiası değildir. Packaged JAR `javap` exact evaluator/result API'sini doğruladı; `jdeps --print-module-deps` yalnız **java.base**, exit 0.

Değiştirilmemiş gerçek Python–Java J-011 trajectory checker çalıştırıldı:

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS, exit 0**. 3 run / 2 distinct configuration: primary/repeat 1584 sample ve fine 3167 sample; her run 3 event. Distinct primary+fine: 442261 numeric comparison, 442225 exact, 36 accepted nonexact; repeat dahil 46 nonexact. Max absolute fark 8.881784197001252e-16; önceki J-011 upstream last-bit farkları mevcut quantity-specific toleranslar içinde. Yeni tolerans, değişmiş physics anchor veya evrensel bitwise eşitlik iddiası yoktur. Bu mevcut flight yolunun regresyonudur, yeni NAT-025 için Python migration oracle'ı iddiası değildir.

Build/XML/RED/GREEN/trajectory logları ve generated traces TEMP altındadır; Git'e eklenmez. Tam repository test kampanyası veya yeni genel audit yapılmadı.

## Kaynak bütünlüğü ve kabul sınırı

PH25-T01–T14 yeni focused testin schema, boundary, independence, validation, overflow ve determinism kontrolleriyle; T15–T16 seçilen eski testler ve gerçek trajectory checker ile karşılanır. Mevcut tracked production/test/history dosyaları NAT-024 başlangıcına göre değişmedi. Özellikle PropulsionTimeline, MotorCurveStatistics, motor evaluators, events, PhysicsEvaluator, SimulationEngine, NAT-023/024 ve recorder/result şemaları aynı kaldı. Yeni dört production tipi dışında production abstraction yoktur. No-auxiliary ve attached-hardware uçuşları kendi mevcut regression testlerinden geçti.

Bu capability gelecekte NAT-026–030 tarafından kullanılabilir; şu an active simulation'a bağlı değildir. Pad/rail/recovery/staging/6DOF, failed-liftoff outcome, phase transitions, scheduling, dispatch/localization ve yeni warning politikası uygulanmadı. Çözümlenmemiş implementation sapması yoktur; fiziksel phase geçişlerinin bulunmaması onaylı kapsam sınırıdır.

Nihai yerel commit, bu kaydı ekleyen `feat(simulation): add NAT-025 flight phase and propulsion clock` commit'idir. Exact SHA final raporda ve repository kökünden `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-025-flight-phase-propulsion-clock.md` ile izlenir; self-referential SHA için amend/ikinci commit yapılmaz. Push/merge yapılmaz; NAT-026 başlatılmaz.
