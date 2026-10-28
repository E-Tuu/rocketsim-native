# V2-000 — Model Hazırlığı ve Fizik Boşlukları Denetimi

**Tarih:** 2026-10-28. **Durum:** proje sahibi tarafından kabul edilmiş V2-000 dokümantasyon baseline'ı. İki belge ve düzeltilmiş bağımlılık grafiği son incelemede onaylandı. Kabul yalnız dokümantasyon içindir; açık C01–C17 sorularını, kaynak sınırlamalarını veya sonraki mühendislik tasarım kapılarını kapatmaz; kod geliştirme yetkisi vermez.

## 1. Sonuç ve kapsam

Java V1, kabul edilmiş Python kapsamındaki tek motorlu, tek kademeli, sabit adımlı 3DOF balistik uçuş zincirini tamamlamıştır. J-011, F50 örneği için tam geçmiş paritesi, tekrarlanabilirlik ve iki adım boyutu ile duyarlılık kanıtı sağlar. Bu sonuç; pad teması, Liftoff, raydan çıkış, recovery, tumble, staging, genel araç desteği, uçuş deneyiyle doğrulama veya OpenRocket eşdeğerliği değildir.

Gerçekçi tek-kademeli uçuş fazlarının ilk engelleri: açık bir operasyonel/model geçerlilik zarfı, fizik değiştiren olayların işlenmesi, pad/guide kısıtları, recovery kuvveti ve tutarlı zemin sonlandırmasıdır. Tam bileşen ağacı, bütün atalet modelleri, türbülans ve staging bu ilk teslimin önkoşulu yapılmamalıdır. 6DOF daha sonraki ayrı karardır.

Kabul edilmiş yaklaşımlar hata olarak sınıflandırılmadı. Sabit fırlatma yönündeki itki ile açıkça seçilmiş motor mass/CG profilleri C02/C09 uyarınca korunur; kabul edilmiş local altitude map ve Java V1 exception davranışı C10/C12 uyarınca korunur. Doğrusal olay kestirimi de mevcut profilin bilinçli sınırıdır. Şartname farklarının kaydı silinmedi; bu turda kararlaştırılan kapsam ile hâlâ açık teknik sorular §6'da ayrıldı.

Tam yetenek ve bağımlılık kaydı: [Model Gap Matrix](v2-000-model-gap-matrix.md). Matrisin iki tablosu aynı kimlikle birlikte okunur; birinin tek başına okunması kapsam/domain bilgisini kaybettirir.

## 2. Git ve kaynak kimliği

| Denetim | Gözlenen durum |
|---|---|
| Git kökü | `C:/Users/ebrut/OneDrive/Belgeler/ChatGPT/OpenRocket` |
| Repository | `https://github.com/E-Tuu/rocketsim-native.git` |
| Kabul edilmiş kaynak dal | `migration/j-011-native-trajectory-vv` |
| Yerel HEAD ve denetim başlangıcındaki gerçek remote kaynak SHA | `480f64840b92de87ee70a30a299fdb6ab169e0ef` |
| Çalışma dalı | `development/v2-000-model-audit`, doğrudan bu SHA'dan oluşturuldu |
| Başlangıç çalışma ağacı | Temiz; beklenmeyen tracked/untracked dosya yok |
| Korunan yerel `main` ve `origin/main` | `96686c6c35c7f859f1849abe8bfe13d5879405d9` |
| İlk audit turunun işlem sınırı | Yalnız iki yeni V2-000 Markdown taslağı; o turda staging/commit/push yok; son kabul ve yayın yetkisi §11'de ayrıdır |

J-001–J-011, AUD-002 ve M-001 commitlerinin HEAD'in atası olduğu kontrol edildi. `git log` içindeki gerçek milestone SHA'ları kullanıldı; model geçmişi değiştirilmedi.

### Teknik şartname

Sahibinin sağladığı gerçek dosya okundu:

`C:/Users/ebrut/OneDrive/Masaüstü/OpenRocket_Fizik_Simulasyon_Motoru_Teknik_Spesifikasyon_v1.docx`

- SHA-256: `3361e2988db6b1f3d9428b0bee8d96af28afef2dc244e285d810da85d6bf7a13`.
- 1.021.503 byte; belge gövdesindeki 1.169 paragraf, 72 tablo ve 8 gömülü diyagram incelendi.
- Bölüm 1–54 ve Ek A–H erişilebilir; yalnız 6–43 ile sınırlı örnekleme yapılmadı. Sayfa numarası iddia edilmiyor; aşağıdaki atıflar bölüm ve denklem/kayıt kimliğidir.
- Kapak sürümü 1.0 ve 28 Ağustos 2026, kontrol tablosu 27 Ağustos; core metadata başlık/açıklaması v0.9.3, revision 15, değiştirilme 31 Ağustos. Ek C/D ve son geliştirme durumu da v0.9.3 der. İçindekiler A–G iken Ek H de vardır. **C01:** içerik erişimi tamdır; yayın/sürüm kimliği sahibince kesinleştirilmelidir.
- DOCX repository'de sürümlenen bir dosya değildir; mevcut yol ve hash bu taslağın dış kaynak kimliğidir. Dondurma öncesi kontrollü saklama/dağıtım kararı gerekir; bu görev dosyayı taşımadı veya kopyalamadı.

## 3. Yöntem, otorite ve kanıt sınırı

1. Java production dosyalarının tamamı, karşılık gelen Python production modülleri, NAT arşivi, ADR'ler ve J raporları okundu; public semboller ile gerçek hesaplama yolları izlendi.
2. Java/Python test dosyaları ve test kimlikleri envanterlendi; özellikle domain, kaynak otoritesi, RK4 stage/endpoint ayrımı, olaylar, recorder/result ve gerçek demo assertion'ları incelendi. Testlerin varlığı yeni test çalıştırması olarak sunulmadı.
3. Parite runner/probe sınırları, Python referans çağrıları, tolerans seçimi ve negatif bütünlük testleri incelendi. Tam uçuşun bütün sample/event alanlarını karşılaştıran J-011 yolu ayrıca okundu.
4. Şartname bir tasarım referansıdır. `FROZEN`, bir denklem listesi veya planlanmış V&V tablosu Java uygulaması ya da test yürütme kanıtı sayılmadı.
5. Öncelik: kabul edilmiş implementasyon ve kontrollü kararlar → kabul testleri/kanıtlar → açıkça kaynaklandırılmış fizik önerileri → TBD kararları. Şartnamedeki `OR13.05`, `CURRENT-SRC`, `LITERATURE`, `MODEL-DECISION`, `OR-LIMITATION`, `OUR-EXT`, `TBD` etiketleri korunur; bu rapor onları yeni bağımsız kaynak doğrulaması olarak yeniden adlandırmaz.

**Bu denetimde Java/Python testleri, Gradle build, consumer veya jdeps tekrar çalıştırılmadı.** Aşağıdaki yürütme sonuçları kabul edilmiş J-011 raporuna aittir. Güncel işlem kanıtı; salt-okunur Git/kaynak/DOCX incelemesi ve iki yeni dokümanın referans/kapsam kontrolüdür. Frontend veya OpenRocket fizik kodu uygulama otoritesi olarak kullanılmadı.

## 4. İzlenebilir kanıt kataloğu

Kısaltmalar bu dosya ile matriste ortaktır. `J` = `roketsim-core/src/main/java/org/roketsim/core/`; `P` = `roketsim-native-engine/src/roketsim_native/`; `JT` = `roketsim-core/src/test/java/org/roketsim/core/`; `PT` = `roketsim-native-engine/tests/`. Tümü `roketsim-native-workspace/` altındadır. `S` yukarıda hash'i verilen şartnamedir. Paketler gelecekteki sınıf önerisi değil, mevcut dosyalardır.

| Kanıt | Java konumu ve ilgili sembol | Python / kabul kaydı | İncelenen test/kanıt |
|---|---|---|---|
| E01 | `J/math/{Vector,Vectors,NumericalValidation,FrameConventions,Quaternions}.java`; `J/units/UnitPolicy.java` | `P/math/*.py`, `P/units/policy.py`; NAT math V&V, J-001/002, M-001, ADR-002/008 | `JT/{Foundation,NumericInputContract,Quaternion}Test.java`; PT math/validation |
| E02 | `J/geometry/GeometryResolver.java#resolve`, `ResolvedRocketGeometry`, `SingleStageRocketGeometry` | `P/geometry/{models,resolver}.py`; register NAT-010/011A/A.1; J-004 | `JT/Geometry{Models,Resolver}Test.java`; PT geometry |
| E03 | `J/materials/{BulkMaterial,MaterialCatalog}.java`; `J/mass/StructuralMassPropertiesCalculator.java#evaluate` | `P/materials/{models,catalog}.py`, `P/mass/{models,structural}.py`; NAT-011B/B.1; J-004 | `JT/{Materials,StructuralMass}Test.java`; PT materials/mass |
| E04 | `J/propulsion/{MotorDefinition,MotorCatalogs,MotorInstallationResolver}.java` | `P/propulsion/{models,catalog,installation}.py`; NAT-011C1/C2; J-005 | `JT/Motor{Catalog,Installation}Test.java` |
| E05 | `J/propulsion/{MotorThrustCurveEvaluator,MotorCurveAnalyzer,MotorPropertyEvaluator}.java`; `J/mass/RocketMassPropertiesCalculator.java` | `P/propulsion/{thrust,properties}.py`, `P/mass/total.py`; NAT-011C3A/B/C; J-005 | `JT/Motor{Thrust,Properties}Test.java`, `RocketMassTest.java`; PT propulsion/mass |
| E06 | `J/environment/{Altitude,USStandardAtmosphere1976Lower,DryAirPropertiesCalculator}.java` | `P/environment/{altitude,atmosphere,air_properties}.py`; NAT-009 environment freeze/009E; J-003 | `JT/{Altitude,AtmosphereAir}Test.java`; `PT/validation/test_atmosphere_vv.py` |
| E07 | `J/environment/{ConstantGravityModel,NoWindModel,ConstantWindModel}.java`; `J/flightconditions/{RelativeFlowCalculator,BasicFlightConditionsCalculator}.java` | P environment gravity/wind, flight_conditions; NAT-009, J-003 | `JT/{GravityWind,FlowConditions}Test.java`; PT environment/flight_conditions |
| E08 | `J/aerodynamics/{SurfaceFinish,SingleStageRocketAerodynamicSurfaces,BasicDragEvaluator,BasicDragModelProfile}.java` | `P/aerodynamics/{surfaces,drag}.py`; NAT-012A0/A1; J-006 | `JT/{Aerodynamics,AerodynamicProfile}Test.java`; PT aero basic_drag/surfaces |
| E09 | `J/aerodynamics/{StaticAerodynamicEvaluator,StaticStabilityModelProfile,StaticMarginCalculator}.java` | `P/aerodynamics/{static_stability,static_margin}.py`; NAT-012B; J-006 | `JT/AerodynamicsTest.java`; PT aero static_stability/static_margin |
| E10 | `J/dynamics/{LaunchConditions3DOF,InitialStateBuilder,TranslationalState3DOF}.java` | `P/dynamics/initial_state.py`; NAT-013; J-007 | `JT/InitialStateTest.java`; `PT/unit/dynamics/test_initial_state.py` |
| E11 | `J/dynamics/{TranslationalDynamicsInputs,TranslationalDynamicsEvaluator,TranslationalForces3DOF}.java` | `P/dynamics/translational.py`; NAT-014; J-007 | `JT/TranslationalDynamicsTest.java`; PT translational |
| E12 | `J/numerics/{FixedStepConfig,TranslationalStateAlgebra3DOF,ClassicalRK4Integrator3DOF}.java` | `P/numerics/{fixed_step,rk4}.py`; NAT-016/017; J-008 | `JT/{FixedStep,ClassicalRK4}Test.java`; PT numerics |
| E13 | `J/simulation/{PhysicsEvaluationContext3DOF,PhysicsEvaluator3DOF,PhysicsEvaluationResult3DOF,PropulsionTimeline}.java` | `P/simulation/physics.py`; NAT-015; J-009 | `JT/PhysicsEvaluatorTest.java`; `PT/unit/simulation/test_physics.py` |
| E14 | `J/simulation/FlightEventDetector3DOF.java#detect`, `FlightEventType`, `FlightEventOccurrence3DOF` | `P/simulation/events.py`; NAT-018; J-010 | `JT/FlightEventsTest.java`; PT events |
| E15 | `J/simulation/{FlightRecorder3DOF,RecordedFlightData3DOF}.java` | `P/simulation/recorder.py`; NAT-019; J-010 | `JT/FlightRecorderTest.java`; PT recorder |
| E16 | `J/simulation/SimulationEngine3DOF.java#run`, `SimulationRunConfiguration3DOF`, `SimulationExecution3DOF` | `P/simulation/engine.py`; NAT-020; J-010 | `JT/SimulationEngineTest.java`; PT engine |
| E17 | `J/simulation/SimulationResult3DOF.java` | `P/simulation/result.py`; NAT-021; J-010 | `JT/SimulationResultTest.java`; PT result |
| E18 | `JT/NativeDemoTrajectoryTest.java#configuration`, `NativeTrajectoryParityProbe.java` | `PT/integration/simulation/test_native_demo_trajectory.py#demo_configuration`; NAT-022; J-011 | `verification/native_trajectory_parity.py`, J-011 committed evidence |
| E19 | `roketsim-core/verification/parity_contract.py#keyed_output`, parity runner ve tüketici araçları | J-001–011, AUD-002/M-001 verification kayıtları | `test_*parity_integrity.py`; ayrıntılar §7 |
| E20 | `roketsim-core/{build.gradle,settings.gradle,gradle/wrapper/gradle-wrapper.properties}`, `src/main/java/module-info.java` | ADR-001–008; Python `pyproject.toml` | `verification/consumer/src/main/java/consumer/CoreConsumer.java`; J-011 dependency kanıtı |
| E21 | Java production JSON/CLI paketi yok | `P/integration/{cli,json_bridge,v11}.py`, `schemas/integration/{v1,v1.1}/`, INT geçmişi | `PT/unit/integration/{test_json_bridge,test_explicit_vehicle_v11}.py`; ADR-007/008 |

On kabul edilmiş subsystem runner'ı ve ayrıca J-011 trajectory runner'ı vardır; tam adları §10'da verilir.

## 5. Uygulanan davranış ve sınırlar

### Matematik, birimler ve durum

E01: SI metadata ve finite kontroller, boyuttan bağımsız immutable `Vector`, açık toleranslı normalize/dot/cross ve scalar-first BODY→WORLD quaternion cebiri uygulanmıştır. WORLD ENU, BODY +Z longitudinal/thrust ve nose-tip'ten arkaya artan ayrı `x_geo` korunur. Quaternion temeli yönelim veya açısal hız integrasyonu değildir. Birim/frame metadata'sı vektörün frame'ini derleme zamanında kanıtlamaz; domain/çağıran sözleşmesi sürer. ADR-008 ile String→number Core kapsamı dışında; eski lexical parite kayıtları yeni API otoritesi değildir.

`Vectors.magnitude` karelerin toplamı ve sqrt kullanır; finite bileşenlerin bütün büyüklüklerinde overflow/underflow'suz norm garantisi vermez. Flight-condition airspeed yolu kendi kaynak sözleşmesine göre ayrı 3D hypot kullanır. Yeni bir zero-flow/axis politikasını bu iki yol bitwise aynıymış gibi kurmak veya global norm algoritmasını bu audit içinde değiştirmek yetkili değildir.

### Geometri, malzemeler, kütle

E02/E03: tek çaplı conical nose (solid veya normal kalınlıklı shell), cylindrical shell body, eş aralıklı tek trapezoidal square fin set, coaxial mount, iki aynı ring ve signed overhang çözümlenir. Resolver hacim, centroid, Aref, Dmax, Lref ve aerodynamic length otoritesidir; aero ve mass bunları yeniden türetmez. Ham geometry record'larının bütün fiziksel doğrulamaları constructor'da yapılmaz; resolver zorunludur.

Homojen yoğunluklu beş structural katkı (`nose/body/fins/motorMount/centeringRings`) ve bir runtime motor toplamı vardır. Catalog `CARDBOARD=680`, `POLYSTYRENE=1050 kg/m³`; kullanıcı açık `BulkMaterial` da sağlayabilir. Katalog bileşen ataması veya yüzey pürüzlülüğü default'u değildir. Measured mass/CG override, payload/avionics/recovery kütle modeli, component tree/instances/active stage, inertia/PAT yoktur. Mevcut kütle modeli fixture için tam; keyfi gerçek araç kütle bütçesi için sınırlıdır.

### Propulsion

E04/E05: tek katalog motoru F50-4T; immutable `MotorDefinition` açık kaynaklı başka veri nesnesi kurmaya izin verir ama file importer/catalog genişliği değildir. 32 kanonik thrust noktası, piecewise-linear thrust, analitik trapez impuls, tam düşük-itki kuyruğu ve curve-end burnout otoritesi korunur. %5 effective burn karakterizasyonu runtime cutoff değildir. Certification ölçümleri runtime eğrinin yerine kullanılmaz; 4 s ejection delay metadata'sı ejection işleyicisi değildir.

Mass/CG profili açıkça seçilir: independent source-curve grid'leri ve terminal hold veya `IMPULSE_PROPORTIONAL`/`FIXED_MIDPOINT`. **C09 kararı:** mevcut explicit-source-curve ve açıkça seçilen approximate profiller korunur; yeni motor fiziği, otomatik fallback, sentetik ölçüm eğrisi veya değiştirilmiş depletion sabiti eklenmez. Kaynak/ölçüm eğrisi ile modelin ürettiği mass/CG sonucu ayrı otoritelerdir. F50'nin doğrulanmış source mass/CG eğrileri yoktur; demo sonuçları ölçülmüş eğriler değildir. Eş-eksenli motor mount containment/engagement hesaplanır; cant, retention, thrust offset, cluster ve delayed stage ignition yoktur. Tek structure+motor CG toplamı vardır, inertia yoktur.

### Environment ve airflow

E06/E07: datum yardımcıları geoid/ground girdilerini açık alır, coğrafi veri servisi yoktur. Dry USSA lower model geopotential `H ∈ [-5000, 84852] m`, yedi layer, `g0=9.80665 m/s²`, `R_air=287.053 J/(kg K)`; dry-air fixed gamma 1.4 ve Sutherland beta `1.458e-6`, S=110.4 mevcut kabul sabitleridir. Bu rapor yeni sabit önermemektedir. Geometrik 86000 m'nin dönüşümü tam model üst sınırına eşit değildir; model clamp yapmaz. Eski environment freeze dosyasının sonraki kabul ekleri okunmuştur; ilk BLOCKED kayıtları kapatılmış USSA/datum kararlarını yeniden açmak için kullanılmadı.

Gravity sabit WORLD `[0,0,-g0]`; rüzgâr ya sıfır ya açık finite 3D WORLD air-mass velocity'dir, meteorolojik FROM açısı değildir. Relative velocity `Vrocket−Vairmass`; basic koşullar airspeed/Mach/Re/q üretir. Basic koşulların Re'si `referenceLengthM`, drag'ınki `aerodynamicLengthM` kullanır; bunlar birleştirilemez. Türbülans, shear/multilevel wind, zamana bağlı rüzgâr, nem, WGS gravity, Coriolis, Earth rotation ve terrain yoktur.

E13'ün gerçek ortam eşlemesi `H_eval = H_launch + (z_world−z_launch)` değerini doğrudan geopotential input'a verir. Genel datum yardımcılarını çağırmaz; geometrik z farkı ile tam geopotential dönüşümünün eşit olduğu iddiası değildir. **C10 kararı:** kabul edilmiş mapping, geometric/geopotential ayrımı, domain kontrolleri ve hatalar korunur; clamp, extrapolation, regresyon-tabanlı atmosfer veya yeni atmosfer sabiti uygulanmaz. Yerel yaklaşımın applicability ve sensitivity soruları açık kalır; bu karar onları doğrulanmış veya sayısal olarak kapatılmış saymaz. Regresyon-tabanlı ve extrapolation-tabanlı atmosfer yaklaşımları yalnız ilk Java 3DOF V2 uçuş yeteneği V2-007 birleşik doğrulamasını geçtikten sonra değerlendirilebilecek **ertelenmiş araştırma adaylarıdır**; henüz doğrulanmış alternatif veya V2-006 uygulama teslimi değildir.

### Aerodinamik ve dynamics

E08: Basic Drag V1 yedi CD0 terimi, independent surface finishes, smooth/rough branch, açık `Re_eval≥10⁴` continuation, `0≤M≤1` ve plume ignored politikasıdır. Bu katsayı modeli keyfi AoA/lift/moment çözmez. Kabul edilmiş body correction `1+1/(2 fB)`; şartnamenin `1+2/fB` ifadesiyle C03 çatışması vardır.

E09: Extended-Barrowman `α→0` CNa/CP, conical nose ve tek eş-aralıklı fin set, mid-chord sweep/body-fin/fin-fin interference ve Mach-dependent CP ile sınırlıdır; **`0≤M<0.8`**, 0.8 hariçtir. Mach 1'e kadar drag desteği statik CP desteği sayılmaz. Static margin `(CP−CG)/Dmax`; herhangi bir tasarım güvenlik eşiği uygulanmaz. Bu hesaplar derivative-critical PhysicsEvaluator yolunda çalıştırılmaz; CP mevcut diye gerçek AoA veya attitude yoktur.

E10/E11: state yalnız WORLD position/velocity, launch conditions ayrıca sabit WORLD yönü içerir. Thrust `T u_launch`, drag `−q A Cd0 Vrel/|Vrel|`, gravity `m g`, acceleration `Fnet/m` ile hesaplanır. Exact zero relative flow ile q=0 drag sıfır; q>0 tutarsız girdidir. Yerel launch norm tolerance `1e-8` input'u normalize etmez. **C02 kararı:** kabul edilmiş fixed-launch-direction thrust aynen korunur. Velocity-aligned attitude, yapay weathercocking, angular state veya yeni bir 3DOF attitude yaklaşımı eklenmez; gerçek attitude ve rotational dynamics gelecek 6DOF programına ertelenir. Dolayısıyla bu 3DOF model rüzgârın çözdüğü gövde yönelimi, dinamik attitude kararlılığı veya tumble başlangıcı iddiasını desteklemez; crosswind/nonvertical uçuşun yalnız mevcut translational denklemleri çözülür. `mdot*v`, lift/side force, moment ve inertia yoktur. Uygulanabilirlik zarfı/sensitivity incelemesi bu modeli sessizce değiştirme yetkisi değildir.

### Numerics, lifecycle ve kayıt

E12/E13: klasik fixed-step RK4 dört stage'de bütün derivative-critical physics'i tekrar değerlendirir. Engine kabul edilen endpoint'te ayrıca physics hesaplar; k4 trial, weighted endpoint değildir. Adaptive hata kontrolü/dense output/root reintegration, thrust-knot veya burnout'a adım bölme yoktur. Pozitif finite timestep kabulü bir doğruluk garantisi veya global dt aralığı değildir.

E14/E16: yalnız BURNOUT, APOGEE, GROUND raporlanır. Apogee vz aşağı-crossing; ground launch-WORLD-z düzlemine pozitif yükseklikten aşağı crossing'dir. Olay state'i bracket'in altı bileşeninde doğrusal kestirimdir. Aynı zaman sırası BURNOUT/APOGEE/GROUND deterministik raporlama düzenidir; gelecekteki causal event scheduler/handler önceliği değildir. İç terminal event'te candidate reddedilir, endpoint sample üretilmez; tam endpoint'te kabul edilir.

Engine ignition anında başlar, preignition evaluator reddedilir; pad reaction/Liftoff/ON_GUIDE/rail clear yoktur. Başlangıç düzleminde z=0'dan negatif z'ye hareket ground crossing sayılmaz (`SimulationEngineTest#launchPlaneZeroToNegativeIsNotRepaired`). Bu accepted davranış gerçekçi failed-launch/ground semantics ihtiyacını gösterir; sessiz bir liftoff yamasıyla kapatılmamalıdır.

E15/E17: recorder iki pasif append-only immutable snapshot stream'i tutar, ara RK stages sample değildir. Result'ın tek stored authority'si execution'dır; termination zamanı terminal event'ten veya max-step için son accepted sample'dan türetilir. `lastAcceptedSample` iç-event kestiriminden farklıdır. `simulationDurationS` ignition-start süre; `flightTimeS`, `finalState`, `finalSample`, altitude/max analytics, warning/channel/branch registry yoktur. Fizik hataları çoğunlukla structured exception ile yukarı çıkar; merkezi validity/warning sonucu veya abort edilen run için partial FlightData sözleşmesi yoktur.

## 6. Kaynak/şartname çatışmaları ve onay gerektiren kararlar

C01–C17 kaynak izleri korunur. **C02/C09/C10 için koruma/erteleme yönü; C12 için V1 hatalarını koruma ve ayrıntılı tasarımı V2-001A incelemesine bırakma yönü proje sahibi tarafından kararlaştırıldı.** C14'ün yol haritası sırası ve staging/6DOF kapsamı da aşağıdaki açık owner onayıyla güncellendi. Bu, diğer kararları veya applicability/V&V sorularını kapatmaz; yeni implementasyon yetkisi vermez.

| ID / önem | Rakip kanıtlar | Sonuç ve gerekli karar |
|---|---|---|
| C01 / kaynak kontrolü | S kapak/control/core metadata/Ek C–D farklı sürüm ve tarihler (§2) | Hangi sürüm normatif? Dış DOCX hash'inin kontrollü saklanması ve supersession yöntemi onaylanmalı. İçerik erişimi engeli yok. |
| C02 / kararlaştırıldı; applicability açık | E11 `FIXED_LAUNCH_DIRECTION`; S §13/31 velocity/trajectory-aligned prose, §46.4 `3DOF-ATT-001` açıkça **Vrel** ekseni | Owner kararı: mevcut fixed-launch-direction 3DOF thrust korunur. Velocity-aligned attitude, yapay weathercocking, angular state veya yeni approximation yok. Gerçek attitude/rotational dynamics ayrı gelecek 6DOF programındadır. 3DOF applicability/sensitivity açıkça raporlanır; kaynak farkı saklanır, V1 bug sayılmaz. |
| C03 / P0 aero | E08 `BasicDragEvaluator`, NAT-012A1: `Kbody=1+1/(2 fB)`; S §46.7/Ek G `DRAG-014`: `1+2/fB` | Aynı adlandırılmış fineness için katsayılar farklı. V1 Python/Java aynı kabul modelini uygular. Şartname düzeltmesi mi Aero V2 profil kararı mı? Migration hatası diye değiştirilmez. |
| C04 / aero policy | E08 max(smooth,rough), Re floor'da correlation; S `DRAG-011/012` rough critical-Re routing ve low-Re sabit Cf | Kaynak etiketli alternatif continuation/branch modelleridir; kaynak/fixture kararı olmadan parity veya fizik eşdeğerliği ilan edilemez. |
| C05 / P0 lifecycle | E14/E16 ignition start, raporlayıcı linear events; S §17–18/Ek G EVT05/07, scheduler, contact/guide | Liftoff için 0.02 m, apogee için 0.01 m gibi S CURRENT-SRC eşikleri Native kararı değildir. Fiziksel faz geçişi ile raporlama olayını ayıran yeni sözleşme onaylanmalı. |
| C06 / P0 numerics | S §16/§G8 `INT-CTRL-002=max(t_event−t,0.001 s)`; event anında physics değiştirme şartı | 1 ms'den yakın olayı aşabilir. Minimum step, exact boundary ve repeated/equal-time olay önceliğinin çelişkisi kapatılmalı; Native RK4'e sabit taşınmaz. |
| C07 / P0 tumble | S §21/Ek G EVT08: gerçek α>17.5° ve CG>CP; `3DOF-ATT-001` Vrel-aligned eksen; E09/E10 gerçek α yok | Vrel-aligned approximasyonda α sıfırdır; statik margin tek başına tumble tetikleyicisi değildir. Empirik descent mode eligibility açıkça tanımlanmalı veya tumble ertelenmeli. 6DOF varmış gibi davranılamaz. |
| C08 / descent numerics | E12/E14 fixed RK4/linear ground; S §22 `GND-001/003`, Euler-style sabit-a kökü ve GND05 near-zero-a | Aynı kök modeli değildir. Yeni phase integrator/root policy ve uygun-kök/degenerate durum kararı gerekir. Mevcut estimated terminal ve accepted state ayrımı korunmalı. |
| C09 / kararlaştırıldı; veri geçerliliği açık | E05 explicit source curve veya açık profil; S §8/MIO motor fallback ve inertia silindir yaklaşımı | Owner kararı: mevcut source-curve ve açıkça seçilmiş approximate mass/CG profilleri korunur. Ölçüm/source-backed data ile modeled mass/CG ayrılır; otomatik fallback, sentetik measured curve, yeni motor fiziği veya depletion sabiti değişimi yok. F50 source mass/CG yokluğu devam eder; model fidelity kanıtı ayrıca gerekir. Inertia ayrı gelecek kapsamdır. |
| C10 / koruma kararı; applicability açık | E06 açık datumlar ve E13 local z→H offset; S CONV-018 ile Ek G GEO03'ün ellipsoidal/MSL ifadesi | Owner kararı: geometric/geopotential ayrımı, accepted local mapping, domain checks/errors korunur. Clamp/extrapolation/regression atmosferi veya yeni sabit uygulanmaz. Mapping applicability/sensitivity ve future terrain/geoid soruları açık; datumlar eşitlenmez. Regression/extrapolation atmosferleri ancak ilk Java 3DOF V2 uçuşu V2-007 birleşik doğrulamayı geçtikten sonra araştırılabilecek, henüz doğrulanmamış adaylardır. |
| C11 / margin | E09/NAT-012B Dmax; S STAB-001 genel Lref, FD15 reference diameter ifadeleri | Bugünkü tek çapta değerler çakışabilir; bu otorite eşitliği değildir. Yeni geometride normalization açıkça Dmax mı başka çap mı kararlaştırılmalı. |
| C12 / koruma ve tasarım sınırı kararlaştırıldı; detaylar açık | S §23/49 action aileleri (STRONG_WARNING dahil), CONV-017/FD missing NaN; E01 finite guards, E16 Java exceptions; tarihsel Python warning kavramları | Owner kararı: mevcut Java V1 exception davranışı aynen korunur. Invalid input, unsupported physical domain, model limitation, warning, numerical execution failure ve gelecekteki physical flight abort ayrı kavramlardır. Warning eşikleri/severity mapping/automatic continue/partial-result davranışı/yeni public API icat edilmez; bunlar V2-001A engineering design review konusudur. Python kavramları tarihsel kanıttır; Python runtime veya JSON bridge'i geri getirme gerekçesi değildir. |
| C13 / source pin | S CURRENT-SRC 26.XX/24.12 referansları ve OPEN-PARITY tabloları; sürüm SHA'sı yok | Bu belge current OpenRocket çalıştırma kanıtı değildir. NAT environment freeze'deki 24.12 `133b558de556f0282f51c3f53f3b3fc633b4310a` yalnız kendi tarihsel inceleme alanına aittir; tüm kaynaklara uygulanamaz. Yeni karşılaştırma exact SHA/ayar/capture gerektirir. |
| C14 / sıra ve kapsam onaylandı; diğer model ayrıntıları açık | S Ek D M4 rail+6DOF, M2 frontend; eski yerel altı-aşama önerisi; bu turdaki owner onayı | Owner'ın sekiz aşaması §8'de aynen kaydedildi; eski altı aşama güncel plan değildir. Öncelik tek motorlu/tek kademeli gerçekçi Java 3DOF V2; staging ve 6DOF ayrı gelecek programlardır. Component/configuration ayrıntıları V2-002 tasarımına bağlıdır; frontend kapsamı eklenmez. |
| C15 / empirical units | S §20/STR-006/007 ρm ve boyut taşıyan empirical constants; E03 bulk density kg/m³ | Streamer'da areal/bulk density karıştırılmamalı; kaynak birimi, deney alanı ve CdA belirsizliği kapanmadan material catalog'a bağlanamaz. S'nin yaklaşık hata aralığı Native deney doğrulaması değildir. |
| C16 / register identity | S §6/Ek G3'te GEO kimlik aileleri yeniden kullanılıyor; Ek G9 unique-ID hedefi | Atıflar bölüm+aile+kimlik ile nitelendi. Kaynak doküman bu görevde yeniden numaralanmadı; ileride kaynak kayıt kimliği temizlenmeli. |
| C17 / wind convention | E07 WORLD TOWARD vektörü; S §9/Ek G WND yön açıklamaları/meteorolojik girdi | FROM bearing dönüşümü, interpolation ve RNG örneklemesi future input/model kararıdır. İşaret, seed ve RK stage/retry değerlendirmelerinden bağımsız noise ownership dondurulmalı. |

### C12 — Kavramsal ayrım ve V2-001A inceleme sınırı

| Kavram | Bu kararla kaydedilen anlam / sınır |
|---|---|
| Invalid input | Geçersiz veri/şekil/finite veya semantik girdi; mevcut Java validation/exception yolu korunur. |
| Unsupported physical domain | Geçerli sayısal bir girdinin kabul modelinin desteklediği alan dışında kalması; mevcut domain checks/errors korunur. |
| Model limitation | Kabul edilmiş yaklaşımın fiziksel kapsam sınırı; otomatik yeni warning, hata veya devam kararı atanmaz. |
| Warning | Tarihsel Python/şartname kavramlarından yararlanılabilecek tanı; eşik, severity ve action mapping bu turda tanımlanmaz. |
| Numerical execution failure | Sayısal yürütmede taşma/nonfinite veya mevcut integrasyon hata koşulu; warning'e çevrilmez ve existing exception davranışı değiştirilmez. |
| Future physical flight abort | Gelecekte onaylanacak fiziksel/operasyonel uçuş sonlandırma koşulu; mevcut numerical failure veya GROUND terminal olayına eşitlenmez. |

Bu ayrım bir sınıf hiyerarşisi, yeni error-code listesi, public API, otomatik continue/abort politikası veya partial-result sözleşmesi değildir. Bu ayrıntılar ve optional missing-data ile required finite fizik girdisi ayrımı **V2-001A engineering design review** kapsamında açık kalır. Kabul edilmiş Python warning kavramları yalnız tarihsel tasarım kanıtıdır; Java Core'a Python runtime/JSON bridge bağımlılığı eklenmez. C12, mevcut result/termination/duration semantics'i değiştirmez.

## 7. Mevcut V&V'nin gerçekten kanıtladığı

Kaynak: [J-011 kabul kaydı](j-011-native-trajectory-vv.md), özellikle “Gerçek sonuçlar”, complete-history parity ve evidence bölümleri; baseline commit §2'deki SHA. Bunlar **V2-000'da yeniden yürütülmedi**.

| Kabul edilmiş kapı | Kayıtlı sonuç ve sınırı |
|---|---|
| Java clean build | 436 test; 0 failure/error/skipped. JDK Temurin 17.0.20.1+1, Gradle Wrapper 8.12.1, Windows 11 10.0.26200 amd64 |
| Python full regression | 1491 passed; Python 3.12.4, NumPy 2.5.2; tarihsel NAT-022'deki 1431 başka snapshot |
| Integrity | 140 passed; eski ve yeni fault-injection kapıları dahil |
| Distinct uçuşlar | h=0.010/5000 ve h=0.005/10000; primary repeat ile 3 run, **2 distinct configuration** |
| Tam geçmiş karşılaştırması | Distinct iki run: 4751 sample, 442261 numerical comparison; 442225 exact, 36 accepted nonexact. Sayı physical scalar/component ile numeric shape/index metadata'sını birlikte içerir. Repeat dahil 589782 comparison; 46 nonexact |
| Farkların anlamı | Mevcut quantity-specific source toleransları içindeki upstream physics son-bit farkları; position/velocity/time/event'e yeni tolerans tanımlanmadı. En büyük absolute fark 8.881784197001252e-16 |
| Independent consumer / runtime | Packaged JAR ile gerçek F50 run ve eski consumer grupları PASS; runtimeClasspath boş, jdeps yalnız java.base |
| Binary süreklilik | J-010/J-011 production JAR SHA-256 aynı: `644ba8d983e642581c4d10345220df1a905de95afbcfef9a6de2e904790c2d55` |

Primary fixture: D=0.100 m vehicle, explicit hollow nose/structure/mount, F50-4T, smooth synthetic surfaces, vertical WORLD +Z, zero initial velocity, zero wind, H_launch=1200 m, ignition=0, h=0.010 s. Geometry ve materyal değerleri `NativeDemoTrajectoryTest#configuration` / Python `demo_configuration` içinde **test verisidir**, production default değildir.

| Quantity | Kabul edilmiş primary sonuç |
|---|---|
| Başlangıç toplam kütle | 0.6357490028277474 kg |
| Burnout | 1.430 s; z=88.15789842420412 m; vz=87.92821395634027 m/s |
| Apogee | 7.012303670973791 s; launch-relative z=279.5335684908227 m |
| Ground estimate | 15.832349589466348 s; vz=−49.190189996430696 m/s |
| Sample/step sayısı | 1584 accepted sample, 1584 candidate step; BURNOUT < APOGEE < GROUND |
| Son accepted nokta | t=15.829999999999707 s; z=0.1155854393789068 m; ground estimate ile aynı sample değildir |
| Maksimumlar (test tanısı) | M=0.27673174265848777; q=4663.19556661582 Pa; airspeed=92.8134640033708 m/s |

Bu fixture gerçekçi kalkış/contact veya recovery/tumble modeli test etmez. h duyarlılığı, uçuş deneyindeki belirsizlik ya da genel sayısal doğruluk yüzdesi değildir. CP/CNa domain testleri subsonic statik modeli doğrular; dinamik yönelim kararlılığı kanıtlamaz. NAT-009E'nin standard-atmosphere referans testleri fiziksel kaynak karşılaştırması sağlar fakat weather modeli veya OpenRocket atmosphere capture kanıtı değildir; arşivde OpenRocket evidence `NOT_CAPTURED` olarak ayrılmıştır.

Parite altyapısı `parity_contract` ile `python -O` reddi, nonempty case set, duplicate/missing/extra field ve output length/shape kontrolü uygular; Java child exit kontrol edilir. J-011 checker bütün sample/event sırasını ve nested fizik snapshot'larını karşılaştırır. Negatif testler sayıyı, error category/code/field'i, chronology, truncated/empty output ve process failure'ı bozar. Bu mekanizmalar V2 için **yeniden kullanılmalı**, simülasyonun production bağımlılığı yapılmamalıdır. Sonlu fixture kapsamı evrensel bitwise eşitlik veya bütün hataların yokluğu kanıtı değildir. AUD-002/M-001 kayıtları, özgün 15 J-004 başarısızlığının tarihsel kök nedeninin eksik loglardan yeniden kurulamadığını açık tutar; V2-000 bunu kapanmış saymaz.

## 8. Proje sahibince onaylanan sekiz aşamalı V2 yol haritası

Bu turdaki açık owner onayı, önceki altı aşamalı önerinin yerini alır. Aşağıdaki kimlik, başlık ve sıra korunur; tablo kapsam genişletmez ve implementasyon yetkisi vermez. Öncelikli hedef **tek motorlu, tek kademeli gerçekçi Java 3DOF V2** uçuşudur. Staging ve 6DOF ayrı gelecek geliştirme kapsamlarıdır; J-011 Java V1 fiziği ve kabul kayıtları korunur.

| Sıra | Kabul edilen aşama | Bu uzlaştırma kaydındaki sınır / bağımlılık |
|---|---|---|
| 1 | V2-000 — Model Audit & Source Freeze | İki doküman ve düzeltilmiş bağımlılık grafiği V2-000 baseline'ı olarak kabul edildi; teknik şartnamenin C01 revision sorunu açık kalır. |
| 2 | V2-001 — Model Contracts, Validity & Provenance | C02/C09/C10 koruma kararları ve applicability soruları kaydedilir; C12 detayları V2-001A engineering design review'dadır. Yeni eşik/API/politika bu audit'te tanımlanmaz. |
| 3 | V2-002 — Vehicle Configuration & Mass Extensions | Sonraki flight/launch/recovery fazlarının ihtiyaç duyduğu vehicle configuration ve mass authority tasarımı; kapsam keyfi component family veya staging'e genişletilmez. |
| 4 | V2-003 — Flight Phases, Events & Numerical Integration | Pad/rail/recovery öncesinde phase/event/numerical boundary temeli; C05/C06/C08 gibi açık kararlar kendi tasarım kapılarında kalır. |
| 5 | V2-004 — Pad Contact, Liftoff & Launch Rail | Önceki model/configuration/event bağımlılıkları üzerine gerçekçi kalkış fazları; yeni constraint/eşik kararı bu tabloda icat edilmez. |
| 6 | V2-005 — Recovery, Descent & Ground | Önceki phase/event/launch sözleşmeleri üzerine recovery/descent/ground; C07 tumble ve C15 streamer soruları ilgili incelemeyi bekler, otomatik uygulanmış sayılmaz. |
| 7 | V2-006 — Model Fidelity & Realistic Data Profiles | Kabul modellerinin fidelity/veri ihtiyaçlarının kontrollü değerlendirmesi; C02/C09/C10 koruma kararlarını aşan yeni fizik yetkisi değildir. Regression/extrapolation atmosfer araştırması bu aşamanın teslimi değildir. |
| 8 | V2-007 — Integrated Flight Verification & Validation | İlk Java 3DOF V2 uçuş yeteneğinin birleşik doğrulaması; önceki aşamalardaki V&V'nin yerini almaz. Ancak bu kapı geçtikten sonra C10 atmosfer araştırma adayları ayrıca değerlendirilebilir. |

**Her aşama aynı dokuz başlıkla incelenecektir:** Architecture, API, Result Integrity, Event Query, Termination Semantics, Duration Semantics, Liftoff Scope, Error ve V&V. Bunlar inceleme başlıklarıdır; bu doküman yeni production sınıfı veya public API oluşturmaz.

Bağımlılık sırası: **model contracts/validity/provenance → vehicle configuration/mass → flight phases/events/numerics → pad/Liftoff/rail → recovery/descent/ground → fidelity/realistic data → integrated V&V**. Matrisin capability kayıtları korunur; önceki aşama atamaları bu sıraya göre düzeltilir. Staging/branches ve gerçek attitude/rotational dynamics artık bir V2 aşamasına teslim olarak atanmaz; ayrı gelecek kapsamlar olarak izlenir.

### Son bağımlılık bütünlüğü incelemesi

Matris Tablo B'nin 61 satırındaki ilişkiler **H (hard prerequisite), K (co-design/kavramsal veya sonraki tüketici), F (ertelenmiş/gelecek)** olarak ayrıldı. Aynı capability'nin mevcut V1 temeli, erken asgari sözleşmesi ve sonraki uygulaması doküman-içi kapsam etiketleriyle gösterilir; yeni capability ID, production class veya API type değildir. Geometri raw input → resolved layout/volume → aero descriptor sırasındadır; karşılıklı fiziksel uyum co-design olarak korunur. V2-001 validity/error/provenance tasarımı tamamlanmış V2-003 event handler, V2-002 configuration extension veya V2-006 advanced wind beklemez; bu parçalar sonraki tüketici/veri üreticileridir. V2-001 uncertainty planı da V2-007 final validation sonucunu beklemez; erken plan → ham ölçüm/veri kanıtı → uncertainty değerlendirmesi → final validation acceptance ayrımı yapılır.

V2-003 iç sırası: **3a event/tek-motor clock/result sözleşmeleri → 3b scheduler/adım sınırı sözleşmesi → 3c zamanlama kısmı ve 3d localization → 3e boundary continuation → 3f handler/phase entegrasyonu**. 3c/3d ortak boundary sözleşmesinden sonra kısmen paralel ilerleyebilir; tamamlanmış handler localization'ın önkoşulu değildir. Bunlar yeni milestone veya root/priority/epsilon politikası değildir; C05/C06/C08 inceleme kapıları açık kalır. Staging bağımlılıkları ve A05/D06/G06 6DOF co-design ilişkileri ertelenmiş ayrı kapsamda korunur.

Matrisin gerçek H hücrelerinden bağımsız topolojik kontrol: **61/61 capability, 74 kapsam düğümü, 176 hard kenar; 0 döngü, 0 eksik capability/kapsam referansı, 0 cross-milestone veya iç sıra tersliği**. Bu yalnız planın hard önkoşul bütünlüğüdür; model/source kararlarının veya fizik doğrulamasının kapandığı anlamına gelmez. Ayrıntılı sınıflama, scope tanımları ve değişen satır listesi [matrisin Tablo B ve V2-003 incelemesindedir](v2-000-model-gap-matrix.md).

C01 ayrımı korunur: Java kabul baseline'ı §2'deki exact J-011 SHA'dır; dış DOCX'in kapak 1.0 / metadata v0.9.3 kimlik farkı ve kontrollü source-version kararı ayrı açık konudur. H grafiği bu kaynakları eşitlemez veya C01'i kapatmaz. C01–C17 kayıtları, owner kararları, sekiz aşamanın kimlik/başlık/sırası ve dokuz inceleme başlığı bu turda değiştirilmedi.

Kaynağı doğrulanmamış yeni fizik sabiti, default, tolerance veya model eklenmez. Frontend ve installer işi yoktur. C10'daki regression/extrapolation atmosfer araştırmaları, V2-007 sonrası ayrı değerlendirme gerektirir; doğrulanmış kabul edilmez. Bu yol haritası onayı diğer C kayıtlarının model/source sorularını kapatmaz.

### Kararlaştırılan yönler ve açık inceleme konuları

- **C02:** fixed-launch-direction thrust korunacak; attitude/weathercocking/angular states 3DOF'a eklenmeyecek. Applicability ve sensitivity kanıtı açık, gerçek attitude ayrı 6DOF programında.
- **C09:** explicit-source-curve veya açıkça seçilmiş approximate motor mass/CG profilleri korunacak. Modeled sonuçlar ölçüm diye sunulmayacak; yeni motor fiziği/fallback/depletion sabiti yok.
- **C10:** datum ayrımı/local mapping/domain checks/errors korunacak. Mapping zarfı/sensitivity soruları açık; alternatif atmosphere research yalnız ilk V2 integrated verification sonrası.
- **C12:** Java V1 exceptions korunacak. Altı kavramsal kategori ayrılacak; thresholds/severity/action/partial-result/API ayrıntıları V2-001A incelemesini bekleyecek.
- **C14:** sekiz aşamalı sıra ve tek-stage/tek-motor önceliği onaylandı; staging/6DOF ayrı gelecek kapsamlar. Vehicle configuration ayrıntıları buradan çıkarılıp uydurulmaz.
- **Diğer açık kayıtlar:** C01/C13 source revision/pinning; C03/C04/C11 aero/source/normalization; C05/C06/C08 event/constraint/root semantics; C07/C15 tumble/streamer model applicability; C16 register identity; C17 future wind convention/noise ownership. Bu kayıtlar kapatılmadı.

## 9. V2 doğrulama yaklaşımı ve koruma

V1 için kabul edilmiş bağımsız modüller yeniden yazılmamalı: E01–E17'nin matematik, geometry/material-volume, mass, thrust/impulse, dry-air, relative-flow, katsayı ve RK4 otoriteleri kendi alanlarında korunur. Yeni fazlar yalnız açıkça onaylanan model/scheduler politikasıyla bunları tüketir. Yeni domain desteklenmiyorsa exception gizlenip “approximate” sonucu uydurulmaz.

Her alt kapı: (1) kaynak ve model-validity kararı, (2) analytik/denklem testleri, (3) intermediate/result/error testleri, (4) tek faz integration, (5) faz geçişi ve adverse cases, (6) frozen V1 tam regression ve mevcut parity/integrity/consumer/java.base kapıları. Yeni fizik için eski Python referansı uygulanmış sayılmaz; onaylı kaynak/analitik/deney referansı gerekir. Yeni toleranslar referans belirsizliği, arithmetic değerlendirme ve timestep sensitivity üzerinden miktar bazında önerilip onaylanır; bu rapor sayı koymaz.

Deployment-under-thrust, guide transient, zero/near-zero flow, failed liftoff, same-time events, tekrar edilen event step ve branch mass accounting öncelikli adverse fixtures olmalıdır. Stochastic wind eklenirse same-seed repeat, stage/retry çağrı sayısından bağımsız fiziksel noise timeline ve timestep sensitivity ayrıca gereklidir. OpenRocket karşılaştırması separate/pinned reference capture'dır; hiçbir OR kuvveti Native run'a karıştırılmaz.

Doküman-only koruma: mevcut Java/Python kaynakları, testler, ADR-001–008, NAT/J/AUD/M geçmişi ve J-011 baseline değiştirilmedi. Test/build çalıştırılmadı; yeni sabit/tolerans/preset yok. Son kabul bu iki doküman ile bağımlılık grafiğini kapsar; fizik implementasyonu, yeni API veya ilave model kabulü değildir.

## 10. Tam kaynak envanteri

### Java production — 144 dosya; module descriptor + 12 paket

`J` altında aşağıdaki adların her biri `.java` dosyasıdır; `module-info.java` ayrıca Core `src/main/java/` kökündedir. Production dosyalarının tamamı incelendi; tabloda gelecekteki sınıf önerisi yoktur.

| Paket / sayı | Dosyalar |
|---|---|
| math / 8 | AxisConvention, FrameConventions, NumericalValidation, Quaternion, Quaternions, ReferenceFrame, Vector, Vectors |
| units / 1 | UnitPolicy |
| environment / 10 | AirPropertiesDomainError, Altitude, AtmosphereDomainError, ConstantGravityModel, ConstantWindModel, DryAirAtmosphereState, DryAirProperties, DryAirPropertiesCalculator, NoWindModel, USStandardAtmosphere1976Lower |
| flightconditions / 4 | BasicFlightConditions, BasicFlightConditionsCalculator, FlightConditionsDomainError, RelativeFlowCalculator |
| geometry / 14 | CenteringRingPairGeometry, ConicalNoseGeometry, CylindricalBodyGeometry, FinAngularArrangement, FinCrossSection, GeometryResolver, GeometryValidationError, MotorAttachmentGeometry, MotorMountTubeGeometry, NoseConstructionMode, ReferenceGeometryPolicy, ResolvedRocketGeometry, SingleStageRocketGeometry, TrapezoidalFinSetGeometry |
| materials / 4 | BulkMaterial, MaterialCatalog, MaterialValidationError, SingleStageRocketMaterials |
| mass / 6 | ComponentMassProperties, MassValidationError, RocketMassProperties, RocketMassPropertiesCalculator, StructuralMassProperties, StructuralMassPropertiesCalculator |
| propulsion / 23 | MotorCatalog, MotorCatalogs, MotorCertificationReference, MotorCgEvolutionModel, MotorCgSample, MotorCurveAnalyzer, MotorCurveStatistics, MotorDataProvenance, MotorDefinition, MotorInstallation, MotorInstallationError, MotorInstallationResolver, MotorMassEvolutionModel, MotorMassProperties, MotorMassSample, MotorPropertyEvaluator, MotorPropertyModelProfile, MotorThrustCurveEvaluator, MotorThrustState, MotorType, MotorValidationError, PropulsionEvaluationError, ThrustSample |
| aerodynamics / 25 | AerodynamicEvaluationError, BaseDragModel, BasicDragEvaluator, BasicDragModelProfile, BasicDragResult, BodyLiftTreatment, BoundaryLayerModel, FinCenterOfPressureModel, FinEdgeModel, LowReynoldsContinuation, NosePressureModel, PlumeTreatment, ReynoldsLengthPolicy, SingleStageRocketAerodynamicSurfaces, SkinFrictionBranch, SkinFrictionEvaluation, StaticAeroContribution, StaticAerodynamicEvaluator, StaticAerodynamicProperties, StaticMarginCalculator, StaticMarginResult, StaticNormalForceModel, StaticStabilityModelProfile, SurfaceFinish, SurfaceValidationError |
| dynamics / 13 | AerodynamicForceModel, DynamicsEvaluationError, InitialStateBuilder, InitialStateValidationError, LaunchConditions3DOF, ThrustDirectionModel, TranslationalDynamicsEvaluator, TranslationalDynamicsInputs, TranslationalDynamicsModelProfile, TranslationalDynamicsResult, TranslationalForces3DOF, TranslationalState3DOF, TranslationalStateDerivative3DOF |
| numerics / 6 | ClassicalRK4Integrator3DOF, DerivativeFunction3DOF, FixedStepConfig, IntegrationPoint3DOF, NumericalIntegrationError, TranslationalStateAlgebra3DOF |
| simulation / 29 | ApogeeDetectionModel, BurnoutDetectionModel, EnvironmentPositionMappingModel, EventDetectionError, EventLocalizationModel, FlightEventDetectionContext3DOF, FlightEventDetectionProfile3DOF, FlightEventDetector3DOF, FlightEventOccurrence3DOF, FlightEventType, FlightRecorder3DOF, FlightRecordingError, GroundReferenceModel, PhysicsEvaluationContext3DOF, PhysicsEvaluationError, PhysicsEvaluationResult3DOF, PhysicsEvaluator3DOF, PropulsionTimeline, RecordedFlightData3DOF, RecordedFlightSample3DOF, SimulationEngine3DOF, SimulationEngineError, SimulationExecution3DOF, SimulationResult3DOF, SimulationResultError, SimulationRunConfiguration3DOF, SimulationRunLimits3DOF, SimulationTerminationReason, TerminalEventHandlingModel |

### Python production — 54 dosya

`P` kökünde `__init__.py`; aşağıdaki her pakette ayrıca `__init__.py` vardır. Bu dosyalar da sayıya dahildir. Bazı package docstring'lerindeki eski “henüz yok” ifadeleri gerçek alt modüllerin durumunu geçersiz kılmaz.

| Paket | Diğer dosyalar |
|---|---|
| math | frames.py, numerical.py, quaternion.py, vectors.py |
| units | policy.py |
| environment | altitude.py, atmosphere.py, air_properties.py, gravity.py, wind.py |
| flight_conditions | relative_flow.py, basic.py |
| geometry | models.py, resolver.py |
| materials | models.py, catalog.py |
| mass | models.py, structural.py, total.py |
| propulsion | models.py, catalog.py, installation.py, thrust.py, properties.py |
| aerodynamics | surfaces.py, drag.py, static_stability.py, static_margin.py |
| dynamics | initial_state.py, translational.py |
| numerics | fixed_step.py, rk4.py |
| simulation | physics.py, events.py, recorder.py, engine.py, result.py |
| integration | cli.py, json_bridge.py, v11.py |

Python integration mevcut tarihsel 1.0 preset / 1.1 explicit configuration/capabilities protokolüdür; Java Core'un runtime mimarisi değildir. Kalıcı Python source/tests/V&V referansı korunur; runtime retirement ve direct frontend consumer şartları ADR-007'de ayrı kapıdır. V2-000 bunları silme/taşıma yetkisi vermez.

### Testler, parite ve kontrollü kayıtlar

- `JT`: 40 dosyanın tamamı envanterlendi: 28 `*Test.java`, 11 `*Probe.java` ve `VehicleTestData.java`. İlgili assertion ve fixture kanıtları E01–E18'de adlandırıldı; bu sayı parametrik yürütme sayısı değildir.
- `PT`: 42 `test*.py` dosyasının tamamı envanterlendi. `unit/{math,environment,flight_conditions,geometry,materials,mass,propulsion,aerodynamics,dynamics,numerics,simulation,integration}/`, `unit/test_units.py`, `validation/{test_math_foundation,test_atmosphere_vv}.py` ve `integration/simulation/test_native_demo_trajectory.py`. Özellikle fizik domain ve scope assertion'ları §4'teki karşılıklarıyla incelendi.
- `roketsim-core/verification/`: `python_parity.py`, `quaternion_parity.py`, `environment_parity.py`, `vehicle_parity.py`, `propulsion_parity.py`, `aerodynamics_parity.py`, `dynamics_parity.py`, `numerics_parity.py`, `physics_evaluator_parity.py`, `lifecycle_parity.py`, `native_trajectory_parity.py`, `parity_contract.py`; sekiz `test_*parity_integrity.py`; isolated-build, consumer ve committed evidence/manifest kayıtları.
- `docs/archive/nat/` içindeki **22 Markdown**: native-engine-register, environment-model-freeze, math-foundation-vv; nat-009e; nat-011c1/c2/c3a/c3b/c3c; nat-012a0/a1/b; nat-013/014/015/016/017/018/019/020/021/022. NAT-010/011A/B ayrı yeni acceptance dosyaları icat edilmeden register/freeze içindeki gerçek kayıtlarıyla izlendi.
- `docs/decisions/ADR-001-native-engine-python.md`, `ADR-002-si-units.md`, `ADR-003-engine-neutral-contract.md`, `ADR-004-no-runtime-ai-in-physics.md`, `ADR-005-native-engine-offline.md`, `ADR-006-no-hidden-physics-flags.md`, `ADR-007-independent-java-core-migration.md`, `ADR-008-typed-numeric-input-boundary.md`.
- `docs/verification/` mevcut 13 kayıt: J-001–J-011 kabul raporlarının tamamı, aud-002-parity-integrity ve m-001-numeric-input-cleanup. Aktif subsystem dokümanları ve README yalnız bağlamdır; kod/acceptance yerine otorite sayılmadı.

## 11. Nihai dokümantasyon kabulü

**V2-000 dokümantasyon baseline'ı kabul edildi.** Proje sahibinin son onayı iki dokümanı, 61 capability kaydını ve düzeltilmiş hard prerequisite grafiğini (74 kapsam düğümü / 176 kenar) kapsar. C02/C09/C10/C12'nin sınırlandırılmış owner kararları, sekiz aşamalı yol haritası ve dokuz inceleme başlığı korunur. Applicability, sensitivity ve V2-001A tasarım soruları ile diğer çözülmemiş C01–C17 maddeleri açık kalır. Özellikle dış teknik şartnamenin **C01 kapak/metadata revision uyuşmazlığı kapatılmadı**; Java J-011 source baseline'ı ile DOCX sürüm kimliği eşitlenmedi. Kaynak sınırlamaları ve tarihsel audit/V&V kanıtları korunur. Kod geliştirme veya yeni fizik/model/API/sabit/tolerance değişikliği yetkisi verilmedi; bütün gerçekçi uçuş fazları hazır sayılmaz.

Yayın yetkisi: yalnız bu dosya ve `v2-000-model-gap-matrix.md` stage edilerek açıklayıcı bir V2-000 dokümantasyon kabul commit'i oluşturulur ve yalnız `development/v2-000-model-audit` dalına normal push yapılır. J-011 baseline commit'i ve migration dalı korunur; main değiştirilmez veya merge yapılmaz. Kabul commit SHA'sı ve yayın sonucu Git geçmişi/son görev raporunda kaydedilir; aşağıdaki önceki tur kayıtları o turların commit/push yapılmayan tarihsel durumunu anlatır.

İlk audit turunun son kontrolü: iki dokümanın yerel Markdown bağlantıları mevcut dosyalara çözümlendi; matrisin **61 ayrı capability** kaydı ile 61 planlama satırı birebir eşleşti. `git diff --exit-code <baseline> --` ile Java/Python source ve test ağaçları, ADR'ler ve NAT arşivi için değişiklik olmadığı doğrulandı. Stage alanı boş; `git status --short` yalnız bu iki yeni dosyayı gösterir. O turdaki salt-okunur `git ls-remote` tekrarı, remote J-011 ve main SHA'larının §2 ile aynı olduğunu ve V2-000 dalının remote'a yayımlanmadığını doğruladı. Bunlar repository/doküman kontrolleridir; fizik regresyon testi çalıştırma iddiası değildir.

Karar uzlaştırmasının son kontrolü: iki belgede aynı **8 aşama / 9 inceleme başlığı** ve **61 capability / 61 planlama kaydı** vardır; yerel bağlantılar geçerlidir. C01–C17'nin tamamı korunur; yalnız C02/C09/C10/C12 owner kararları ve ayrıca açıkça onaylanan C14 yol haritası kaydı değişti. Kaynak envanteri ve tarihsel V&V sayıları değiştirilmedi. Kabul edilmiş HEAD'e göre bütün tracked dosyalar aynıdır, staging boştur ve yalnız iki onaylı taslak untracked görünür. Bu turda test, build, consumer, jdeps veya remote sorgusu yeniden yürütülmedi; kod/test/public API, sabit/default/tolerance eklenmedi. Commit/push yapılmadı.
