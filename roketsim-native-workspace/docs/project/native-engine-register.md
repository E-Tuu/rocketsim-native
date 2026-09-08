# Native Engine Register

Kalıcı sayısal değer provenansı kuralı (NAT-012A.0 ve sonrası): her üretim
sayısı standart/evrensel sabit, kullanıcı/roket tasarım girdisi, doğrulanmış
katalog/kaynak verisi veya açık model parametresi/politikası olmalıdır.
Demo/test fixture değerleri genel üretim sabitine dönüştürülemez.

- [x] NAT-000 Architecture freeze
- [x] NAT-001 Python environment
- [x] NAT-002 Repository foundation
- [x] NAT-003 SI policy
- [x] NAT-004 Numerical helpers
- [x] NAT-005 Vector foundation
- [x] NAT-006 Frame conventions
- [x] NAT-007 Quaternion
- [x] NAT-008 Math foundation V&V
- [ ] NAT-009 Environment engine
  - [x] NAT-009A Environment Source & Model Freeze
  - [x] NAT-009B Altitude & Geopotential Foundation
  - [x] NAT-009C Dry-Air Atmosphere Core
  - [x] NAT-009D Air Properties
  - [x] NAT-009E Atmosphere V&V (standard PASS; OpenRocket NOT_CAPTURED)
  - [x] NAT-009F Constant Gravity Baseline
  - [x] NAT-009H Steady Wind Foundation (demo path; G1/G2 deferred)
- [x] NAT-010A Relative Flow Foundation
- [x] NAT-010B Basic Flight Conditions
- [x] NAT-011A Minimal Single-Stage Rocket Geometry
- [x] NAT-011A.1 Geometry Construction & Material Volume Extension
- [x] NAT-011B Materials + Derived Structural Mass & CG
- [x] NAT-011A.2 Motor Mount Assembly Geometry
- [x] NAT-011B.1 Motor Mount Structural Mass & CG Extension
- [x] NAT-011C.1 Motor Definition & Catalog
- [x] NAT-011C.2 Motor Installation
- [x] NAT-011C.3A Thrust Curve Evaluation, Impulse & Curve Statistics
- [x] NAT-011C.3B Motor Mass & CG Evolution
- [x] NAT-011C.3C Total Rocket Mass & CG Aggregation
- [x] NAT-012A.0 Aerodynamic Geometry & Surface Data Contract
- [x] NAT-012A.1 Native Basic Drag V1
- [x] NAT-012B Extended-Barrowman Static Stability V1
- [x] NAT-013 Initial / Launch State V1
- [x] NAT-014 3DOF Translational Dynamics V1
- [x] NAT-015 PhysicsEvaluator 3DOF V1

NAT-010A: FLOW-001, WORLD/ENU SI hız çıkarımı; sahiplik `flight_conditions`.
Başlangıç `49410f7`: 410 test PASS. FLOW-T01..T18 ve overflow guard:
43 test PASS; environment/V&V 260 PASS, math/V&V 148 PASS, full 453 PASS.
Önceki production modülleri değişmedi. NAT-010B scalar flight conditions,
BODY transform ve composition implement edilmedi.

NAT-010B: `flight_conditions.basic`, yalnız V=||relative_velocity_W||,
M=V/a, Re=V*L_ref/nu, q=0.5*rho*V². Upstream rho/a/nu otoriteleri korunur;
L_ref açık karakteristik uzunluktur. Parametresiz calculator, immutable
dört-scalar snapshot, finite/positive validation; sıfır hız geçerlidir.
Tam 3D norm `math.hypot` ile hesaplanır; taşan/underflow ile invariant'ı
bozan sonuçlar generic ValueError üretir, fallback uygulanmaz.
Başlangıç `707e295`: 453 PASS. FC-T01..T20 ve edge testler: 55 PASS;
flight_conditions 98 PASS, environment/V&V 260 PASS, math/V&V 148 PASS,
full 508 PASS. Önceki production kodu/dependency değişmedi.
Mass/Propulsion/Aero, AoA/BODY flow, dynamics ve sonraki demo gate'leri
implement edilmedi. NAT-010B IMPLEMENTATION GATE: PASS.

NAT-011A: `geometry.models` immutable/slotted tasarımları,
`geometry.resolver` scalar/domain ve yerleşim doğrulamasını sahiplenir.
Nose-tip x_geo=0, +x_geo nose→tail; BODY/WORLD dönüşümü yoktur.
Tek airframe çapı D_ref/L_ref/A_ref'i belirler (demo model kararı).
Root chord tamamen body'ye bağlıdır; signed tip offset ve aft-overhang
korunur; overall length furthest downstream extent'tir. Clamp yoktur.
Fin count int>=3; bool/fractional adet sessizce dönüştürülmez.
Non-finite girdiler/derived numerical failure generic ValueError;
finite invalid geometry structured GeometryValidationError üretir.
Başlangıç `6fea345`: 508 PASS. GEO-T01..T25 ve edge testleri: 87 PASS;
flight_conditions 98 PASS, environment/V&V 260 PASS, math/V&V 148 PASS,
full 595 PASS. Önceki production modülleri/dependency değişmedi.
Mass/CG/inertia, propulsion, aero/Barrowman, component tree/staging ve
dynamics implement edilmedi. NAT-011A IMPLEMENTATION GATE: PASS.

NAT-011A.1, NAT-011A'nın onaylı schema evrimidir; ikinci construction source,
resolver veya resolved wrapper yoktur. Nose construction_mode ve wall_thickness_m
explicit; SOLID için None, HOLLOW_SHELL için lateral yüzeye normal pozitif
kalınlık zorunludur. Body explicit kalınlıklı hollow tube, fins uniform solid
plate'tir. Tek GeometryResolver üç material volume ve absolute x_geo volume
centroid'i aynı ResolvedRocketGeometry içine ekler. Volume centroid henüz CG
değildir; density/material catalog/mass yorumu NAT-011B'ye aittir.
Nose shell normal-offset iç koni çıkarımı, tube annulus ve trapezoid plate
denklemleri uygulanır; strict thickness limitleri, non-finite/invalid derived
değerler hata üretir; clamp, repair veya default thickness yoktur.
Başlangıç `9827531`: 595 PASS. Schema'ya uyarlanan eski geometry testleri
87 PASS (fiziksel assertions korunur); CGEO-T01..T28/edge 38 PASS;
geometry 125 PASS, flight_conditions 98 PASS, environment/V&V 260 PASS,
math/V&V 148 PASS; full 633 PASS. Reference/placement convention değişmedi.
Geometry dışı production/dependency değişmedi; NAT-011B başlatılmadı.
NAT-011A.1 IMPLEMENTATION GATE: PASS.

NAT-011B başlangıç önkoşulu doğrulandı: `556a853`, temiz commit ve full
633 PASS. `materials` GEO14 uniform density'yi, Geometry GEO16 volume/volume
centroid'i sahiplenir. Cardboard=680 ve Polystyrene=1050 kg/m³ yalnız demo
katalog seçenekleridir; zorunlu nose/body/fins kullanıcı atamalarında default
yoktur. BulkMaterial construction sırasında finite/positive density ve
non-blank name doğrular; non-finite input generic ValueError'dır.
`mass.structural` MASS-001 m=rho*V, MASS-002 sum(m*x)/sum(m) uygular.
Component CG=volume centroid yalnız uniform-density component varsayımıyla
geçerlidir; x_geo nose-tip→tail kalır. Seçilen material provenance korunur,
volume yeniden hesaplanmaz/kopyalanmaz. Structure mass/CG motoru içermez.
Derived mass/CG invariant ihlalleri MassValidationError'dır; repair yoktur.
MAT-T01..08: 13 PASS; MASS-T01..28/edge: 31 PASS; geometry 125 PASS,
flight_conditions 98 PASS, environment/V&V 260 PASS, math/V&V 148 PASS,
full 677 PASS (633 existing + 44 yeni). Geometry ve diğer önceki production
kodları/dependency değişmedi. Propulsion, inertia, measured/override ve
NAT-011C implement edilmedi. NAT-011B IMPLEMENTATION GATE: PASS.

NAT-011A.2: başlangıç `bb1f2de` (ancestor `556a853`), temiz tree ve 677 PASS.
Tek SingleStageRocketGeometry source'una mandatory motor_attachment eklendi:
bir coaxial mount tube, tam iki identical centering ring ve signed overhang.
Motorun kendisi değildir; hiçbir motor markası/ölçüsü production default değil.
GeometryResolver body inner diameter, mount dış çapı, x_geo yerleşimleri,
rocket-side motor aft reference ve mount/ring material volume/centroid üretir.
Root/body ve önceki external geometry/reference/volume politikaları korunur.
Mount tamamen body içinde; radyal clearance strict pozitif, ring touching
geçerli, overlap geçersizdir. Negatif overhang >= -mount length geçerlidir.
Aft reference structural overall_length'i değiştirmez; installed motor
envelope/fit NAT-011C'ye aittir. Manufacturing tolerances/retention ertelidir.
Geçici gate sınırı: mount/rings geometrik olarak mevcut fakat NAT-011B
structural mass hâlâ yalnız nose/body/fins içerir. Mass production değişmedi;
bu eksik katkı NAT-011B.1'de kapatılacaktır. Mount material ataması yapılmadı.
MOUNT-T01..34: 59 PASS; geometry 184 PASS, materials 13 PASS, mass 31 PASS,
flight_conditions 98 PASS, environment/V&V 260 PASS, math/V&V 148 PASS,
full 736 PASS (677 existing + 59 yeni). Önceki testlerde yalnız required
schema/fixture uyarlaması yapıldı; fiziksel assertions korunur. Dependency yok.
NAT-011A.2 IMPLEMENTATION GATE: PASS.

NAT-011B.1 önkoşulu: accepted `4fcdf7b`, temiz tree, ancestor `bb1f2de`,
başlangıç full 736 PASS. A.2'nin geçici mount/ring mass eksikliği kapatıldı.
SingleStageRocketMaterials'a mandatory motor_mount ve centering_rings
seçimleri eklendi; default/katalog genişlemesi yoktur. Aynı generic component
result ve structural result şimdi beş yapı katkısını içerir. Ring pair tek
birleşik katkıdır. Gerçek motor/propellant yapısal kütleye dahil değildir.
Geometry tek material-volume/volume-centroid, Materials tek density otoritesi;
Mass yalnız MASS-001 rho*V ve MASS-002 beş katkının weighted CG'sini uygular.
Geometry production denklemleri değişmedi. Eski nose/body/fins sonuçları ve
üç-component referans değerleri testlerde korunur; toplamlar artık beşlidir.
SMEXT-T01..18/edge: 24 PASS; materials 13 PASS, mass 55 PASS, geometry
184 PASS, flight_conditions 98 PASS, environment/V&V 260 PASS, math/V&V
148 PASS; full 760 PASS (736 existing + 24 yeni). Mevcut structured material/
mass error sözleşmeleri korunur; repair yoktur. NAT-011C motor/propulsion,
time-varying rocket mass, inertia ve overrides ertelidir; dependency eklenmedi.
NAT-011B.1 IMPLEMENTATION GATE: PASS.

NAT-011C.1: başlangıç `88ab5bc`, temiz çalışma ağacı, full 760 PASS.
`propulsion.models/catalog` yalnız immutable/slotted motor verisi ve stable-ID
katalog erişimi sağlar. İlk katalog yalnız AeroTech F50-4T içerir; default motor
seçimi yoktur. NAR statik test kütleleri 0.0849/0.0379 kg otoritedir.
31 kaynak thrust noktası korunur; yalnız katalogda açık (0,0) eklenerek 32
nokta elde edilir. Test-only integral 76.828387 N*s, ölçülmüş referans 76.83 N*s.
Kaynaklar, RASP header kütle farkı ve yuvarlama kararı:
[NAT-011C.1 kaynak/V&V kaydı](../verification/nat-011c1-motor-catalog.md).
MOTOR-T01..40/edge: 97 PASS; mass 55, materials 13, geometry 184,
flight_conditions 98, environment/V&V 260, math/V&V 148 PASS.
Full 857 PASS (760 existing + 97 yeni). Önceki production modülleri değişmedi.
Motor installation C.2; motor mass/CG C.3; runtime interpolation, events ve
dynamics bu gate'te yoktur. Yeni dependency/runtime network yoktur.
NAT-011C.1 IMPLEMENTATION GATE: PASS.

NAT-011C.2: başlangıç `85f36f5`, temiz ağaç, full 857 PASS; ancestor
`88ab5bc` doğrulandı. `propulsion.installation` geçerli Geometry ve seçilmiş
MotorDefinition arasındaki nominal tek/eş eksenli ilişkiyi çözer.
Aft hazır Geometry referansıdır; front=aft-length. D_motor<=D_mount,
radial clearance=(D_mount-D_motor)/2, front>=mount_start ve gerçek interval
engagement>0 koşulları uygulanır. Eşit çap nominal uyumdur; tolerans modeli
yoktur. Üç overhang işareti desteklenir; sıfır/negatif örtüşme reddedilir.
Frozen/slotted sonuç exact motor nesnesini korur. Structured
MotorInstallationError uyumsuzluk/non-finite türetimleri bildirir; repair yoktur.
MINST-T01..40: 34 PASS; propulsion 131, geometry 184, mass 55, materials 13,
flight_conditions 98, environment/V&V 260, math/V&V 148 PASS.
Full 891 PASS (857 existing + 34 yeni). Geometry, katalog ve önceki production
fiziği değişmedi. C.3 itki/motor mass/CG ve sonraki total mass/events ertelidir.
[Kurulum sözleşmesi ve test eşlemesi](../verification/nat-011c2-motor-installation.md).
NAT-011C.2 IMPLEMENTATION GATE: PASS.

NAT-011C.3A: başlangıç `145b245`, temiz ağaç, full 891 PASS; katalog atası
`85f36f5` doğrulandı. `propulsion.thrust` kanonik eğriden parçalı doğrusal T(t),
tam/kısmi trapezlerle geçmişsiz J(t) ve tam eğri istatistikleri türetir.
motor_time_s ateşlemeden beri geçen süredir. Exact örnekler korunur; sonrasında
T=0, J=J_total. Certification dış V&V metadata'dır; runtime kaynak değildir.
%5 peak eşiği yalnız karakterizasyon: ilk giriş/son çıkış, earliest peak.
F50 J_total=76.828387 N*s; 1.400 s'de T=2.181395348837211 N ve
J=76.79566606976745 N*s. %5 sonrası kuyruk korunur. İleride burnout otoritesi
curve_end_time_s=1.430 s'dir; burada event implement edilmez.
THRUST-T01..45/edge: 63 PASS; propulsion 194, geometry 184, materials 13,
mass 55, flight_conditions 98, environment/V&V 260, math/V&V 148 PASS.
Full 954 PASS (891 existing + 63 yeni). Katalog, Installation, Geometry,
Mass ve önceki fizik değişmedi. C.3B mass/CG ve kaynak eğrileri, C.3C roket
toplamları ertelidir. Yeni dependency/network veya clamp/repair yoktur.
[Denklemler, test eşlemesi ve V&V](../verification/nat-011c3a-thrust-impulse.md).
NAT-011C.3A IMPLEMENTATION GATE: PASS.

NAT-011C.3B: başlangıç `101ba43`, temiz ağaç, full 954 PASS; `145b245` atası
doğrulandı. MotorDefinition/provenance yetkilendirilmiş optional-typed fakat
explicit mass/cg curve/source alanlarıyla genişletildi. F50'nin dört yeni
alanı None; doğrulanmış eski kütle/ölçü/thrust verileri değişmedi.
Kaynak verisi katalogda, açık model profili `propulsion.properties` içinde,
mass/CG sonucu runtime'dadır. Otomatik fallback/promotion veya kaynak yazımı
yoktur. Impuls modeli C.3A J(t)/J_total'ı tüketir; %5 sonrası kuyruk korunur.
Explicit kaynaklar kendi bağımsız grid'lerinde doğrusal değerlendirilir,
kendi bitişlerinden sonra hold edilir; post-burn kaynak değişimi desteklenir.
Demo profil impuls-orantılı mass + sabit midpoint CG seçer. F50 mass(1.400)
=0.047016141471977205 kg, terminal mass=0.047 kg; local CG=.049 m,
kurulu x_geo CG=.956 m. Toplam roket/structural aggregation uygulanmadı.
MPROP-T01..68/edge: 79 PASS; eski provenance testinin iki yeni alanıyla +4
durum. Propulsion 277, C.3A 63, C.2 34, geometry 184, materials 13, mass 55,
flight_conditions 98, environment/V&V 260, math/V&V 148 PASS.
Full 1037 PASS (954 + 79 + 4). C.3A/C.2 ve diğer fizik denklemleri korunur;
C.3C roket toplamları, inertia, events ve vector/dynamics ertelidir.
[Kaynak/politika ayrımı ve V&V](../verification/nat-011c3b-motor-mass-cg.md).
NAT-011C.3B IMPLEMENTATION GATE: PASS.

NAT-011C.3C: başlangıç `ef1b5d0`, temiz ağaç, full 1037 PASS; `101ba43`
atası doğrulandı. `mass.total` yalnız bir hazır StructuralMassProperties ve
bir runtime MotorMassProperties katkısını toplar. Zaman/model seçimi/geometri
girdisi yoktur; alt yapı veya motor fiziği yeniden hesaplanmaz.
M=m_S+m_M; x=(m_S*x_S+m_M*x_M)/M. Eşit contributor CG özdeşliği exact korunur.
Pozitif/sonlu total mass, sonlu CG ve kapalı contributor span kontrol edilir;
bu Geometry extent sınırı değildir. Mevcut MassValidationError kullanılır.
F50 ignition total=.6357490028277474 kg, CG=.6706064563777736 m;
curve-end total=.5978490028277474 kg, CG=.6525142370178312 m.
1.400 s kuyruk durumu final'den ayrıdır; aft motor kütlesi azalınca CG öne gider.
RMASS-T01..38: 35 PASS; mass 90, propulsion 277, geometry 184, materials 13,
flight_conditions 98, environment/V&V 260, math/V&V 148 PASS.
Full 1072 PASS (1037 existing + 35 yeni). Önceki fizik/katalog verileri
değişmedi. Tek yapı+tek motor API'si kasıtlıdır; cluster/staging gelecekte
yeniden ele alınacak. Inertia/PAT, events/dynamics ve NAT-012 uygulanmadı.
[Toplam kütle/CG doğrulama kaydı](../verification/nat-011c3c-total-rocket-mass.md).
NAT-011C.3C IMPLEMENTATION GATE: PASS.

NAT-012A.0: başlangıç `149f4f0`, temiz ağaç, full 1072 PASS. Ham Geometry
mandatory ReferenceGeometryPolicy.MAXIMUM_DIAMETER ve FinCrossSection.SQUARE
ile genişletildi; unsupported politikalar/kesitler eklenmedi.
Referans dış axisymmetric airframe çapıdır; fin span/iç mount/motor hariç.
Aerodynamic length mevcut external overall extent'ten tek kez türetilir;
motor aft reference uzatmaz. Nose/body wetted/frontal/base alanları, nose
fineness/half-angle, per-fin mevcut planform alanı/MAC/signed LE sweep ve
kesit aynı resolved Geometry'dedir. Yeni drag veya aero katsayısı yoktur.
SurfaceFinish ve SingleStageRocketAerodynamicSurfaces bağımsız, immutable
tasarım verisidir; material yoğunluğundan roughness seçilmez. Preset katalog yoktur.
100/150/200 mm tasarımlar reference length/area'nın sabit olmadığını doğrular.
Eski fixture'lara yalnız mandatory enum'lar eklendi; fiziksel assertions korunur.
AEROGEO-T01..28/edge 34 PASS, SURFACE-T01..14/scope 15 PASS; geometry 218,
aerodynamics 15, materials 13, mass 90, propulsion 277, flight_conditions 98,
environment/V&V 260, math/V&V 148 PASS. Full 1121 PASS (1072 + 49 yeni).
NAT-012A.1 drag/Re/Cf/Cd, NAT-012B CP/CNa/static margin ve dynamics ertelidir.
[Sahiplik, provenans kuralı ve analitik V&V](../verification/nat-012a0-aero-geometry-surfaces.md).
NAT-012A.0 IMPLEMENTATION GATE: PASS.

NAT-012A.1: başlangıç `ea123b0`, temiz ağaç; kabul edilmiş milestone
baseline'ı full 1121 PASS. `aerodynamics.drag` zorunlu immutable V1 profiliyle
sıfır-AoA C_D0 üretir. Geometry artık ayrıca dış axisymmetric airframe çapını
ve nose+body axisymmetric uzunluğunu sunar; reference/aerodynamic/body
uzunlukları ayrıdır. NAT-009D `DryAirProperties`, mevcut tek gamma=1.4
otoritesini snapshot alanı olarak sunar; aerodynamics gamma kopyası yoktur.
Re=V*L_aero/nu korunur, Cf için yalnız açık politika Re_eval=max(Re,1e4)
kullanılır. Fully-turbulent smooth ve component-bazlı roughness Cf, diameter
tabanlı body form correction, fin MAC thickness correction, conical nose
pressure, SQUARE fin LE pressure, fin TE base ve airframe base toplam yedi
fiziksel katkıdır. Plume IGNORED'dır. Mach [0,1] desteklenir; >1 extrapolate
edilmez. Smooth sentetik V&V: Re=6666666.666666666, Cf_corrected=
0.003069223087075511, C_D0=0.4740271634476429. Bağımsız roughness branch
seçimi doğrulandı. Odaklı 26, aerodynamics 41, geometry 218, doğrudan ilgili
air-properties 51 PASS. Kullanıcı politikası gereği full tarihsel suite
çalıştırılmadı; NAT-012 milestone audit'e ertelendi. Sonic yakın transonik
fidelity, roughness correlation geçerlilik aralığı, ROUNDED/AIRFOIL fin,
supersonic/transonic genişletme, plume/nozzle ve NAT-012B ertelidir.
[Denklemler, model politikası ve V&V](../verification/nat-012a1-basic-drag.md).
NAT-012A.1 IMPLEMENTATION GATE: PASS.

NAT-012B checkpoint 1: başlangıç `43368ee`, temiz ağaç, remote yok.
Geometry'de zorunlu `FinAngularArrangement.EQUALLY_SPACED`; resolved nose axial
length, mid-chord sweep, MAC span/x placement, Extended-Barrowman AR ve local
external body radius aynı authority'den türetilir. `aerodynamics.static_stability`
zorunlu immutable V1 profiliyle yalnız alpha->0 nose/fin-set CNa ve CP üretir.
Runtime domain `0 <= M < 0.8`; 0.8 ve üstü structured hata, continuation yoktur.
Conical nose CNa alan oranı ve CP=2L/3; continuous cylinder linear katkısı sıfırdır
ve fake result/Galejs finite-AoA term yoktur. Fin CNa direct subsonic mid-chord
sweep bağıntısı, equally-spaced N/2, tam OR13 Eq.3.54 Ntot tablosu ve body-on-fin
düzeltmesini kullanır. V1 tek fin setinde Ntot=N scope eşitliğidir; kavramlar
birleştirilmez. Fin CP M<=.5 quarter chord, .5<M<.8 exact six-boundary-condition
quintic'tir; M=2 yalnız polynomial construction endpoint'tir.
M=0: fin-set CNa=12.629237832516534, total CNa=14.629237832516534,
CP=.6971650449840304 m. M=.75: fin-set CNa=13.729822473770069,
total CNa=15.729822473770069, CP=.707290109576936 m.
Odaklı 47, aerodynamics 88, geometry 222 PASS. Full suite checkpoint 1'de
çalıştırılmadı. StaticMarginCalculator checkpoint 2'ye; transonic/supersonic,
true multi-set Ntot, explicit azimuth/asymmetry, Galejs/nonlinear, force/moment
ve dynamics post-demo'ya ertelidir.
[Checkpoint-1 sözleşmesi ve V&V](../verification/nat-012b-static-stability.md).
NAT-012B CHECKPOINT 1: PASS.

NAT-012B checkpoint 2/final: kabul edilmiş Part-1 `cd7fbf0` audit edildi;
domain `0<=M<.8`, direct subsonic fin CNa, M<=.5 quarter-chord ve .5<M<.8
quintic CP routing, ayrı N/Ntot kavramları ve force/moment/AoA yokluğu korundu.
Yeni ayrı `aerodynamics.static_margin` yalnız hazır StaticAerodynamicProperties
CP, RocketMassProperties CG ve ResolvedRocketGeometry Dmax tüketir:
`margin=(CP-CG)/Dmax`. `reference_length_m` tüketilmez; testte NaN ile
değiştirilirken sonuç sabit kalır, Dmax değişince denominator sonucu değiştirir.
Result tek signed `static_margin_calibers` alanıdır; safe/unsafe sınıflaması,
CG/zaman hesabı veya clamp yoktur. M=0/.75 ve CG=.650 m için sırasıyla
.471650449840304 ve .5729010957693603 caliber doğrulandı; pozitif/nötr/negatif
işaret ve çoklu immutable CG snapshot'ı geçti. Focused B 65, aerodynamics 106,
geometry 222 PASS. Ertelenmiş tek full NAT-012 milestone audit bir kez koşuldu:
1216 PASS. A.0/A.1/B ve tüm önceki NAT regresyonları birlikte geçti.
Transonic/supersonic, finite-AoA Galejs/nonlinear, explicit/asymmetric fins,
true multi-set Ntot, tapered attachment, force/moment ve ROUNDED drag gelecektir.
NAT-013 uygulanmadı.
NAT-012B IMPLEMENTATION GATE: PASS.
NAT-012 MILESTONE AUDIT: PASS.

NAT-013: başlangıç `26815b9e122dba9e0b48fd9a33d46c9a653268d9`, temiz
ağaç, remote yok. Önceki deneme unit-vector tolerans politikası bulunmadığı için
edit öncesi durmuştu; retry açık Native policy `1e-8` ile tamamlandı. Bu tolerans
yalnız launch-direction unit-norm validation'a aittir; global math'a eklenmedi ve
normalization uygulanmadı. Accepted `math.vectors` ndarray/float64,
`as_vector(size=3)` ve `magnitude` API'si yeniden kullanıldı. Frozen/slotted
`LaunchConditions3DOF` explicit WORLD ENU position, velocity, unit direction;
`TranslationalState3DOF` yalnız position+velocity taşır. Her stored ndarray
independent defensive copy ve read-only'dir. `InitialStateBuilder` parametresiz,
stateless, keyword-only olup direction'ı state'e kopyalamaz. WORLD origin default'u,
time/motor-time, mass/aero/environment/attitude alanı yoktur. NAT-009 altitude,
atmosphere, gravity ve wind otoritesi değiştirilmedi; WORLD-altitude köprüsü
NAT-015'e ertelendi. Focused NAT-013: 26 PASS. Math/environment production
değişmediğinden dependency ve full historical suite gate talimatına göre
çalıştırılmadı. NAT-014 dynamics uygulanmadı.
[Başlangıç-state doğrulama kaydı](../verification/nat-013-initial-launch-state.md).
NAT-013 IMPLEMENTATION GATE: PASS.

NAT-014: başlangıç `20a999d803d5ba4a4b21c3c84f79a86b71408ce8`, temiz
ağaç, remote yok. Accepted NAT-013 state/launch-condition ve ndarray/float64
defensive-copy/read-only sözleşmeleri değiştirilmeden tüketildi. Explicit
`FIXED_LAUNCH_DIRECTION` + `BASELINE_CD0_DRAG_ONLY` profile mandatory'dir.
Ephemeral `TranslationalDynamicsInputs` hazır mass, thrust, relative flow, q,
reference area, Cd0 ve WORLD gravity acceleration taşır; upstream physics'i
yeniden hesaplamaz. F_thrust=T*launch_direction, D=q*A*Cd0,
F_drag=-D*V_rel/|V_rel|, F_gravity=m*g_WORLD; net derived sum ve dv/dt=net/m,
dr/dt=velocity'dir. Exact zero-flow/q davranışı explicit; ek mdot*v terimi,
CNa/CP/AoA, rail, attitude veya integrasyon yoktur. Dört frozen ignition/ascent/
descent/true-3D fixture geçti. Focused NAT-014 43 PASS; all dynamics/NAT-013
regression 69 PASS. Gate talimatına göre full historical suite çalıştırılmadı.
NAT-015 PhysicsEvaluator uygulanmadı.
[3DOF dynamics doğrulama kaydı](../verification/nat-014-3dof-translational-dynamics.md).
NAT-014 IMPLEMENTATION GATE: PASS.

NAT-015: başlangıç `e995bf30c74d184e9e8e6ef48f6d50010c7aa5b3`, temiz
ağaç, remote yok; NAT-013 ancestry doğrulandı. `simulation.physics` yalnız current
time/state orchestration bridge'idir. Context accepted Geometry/surfaces/structure/
installation/profiles ile exact NAT-009 atmosphere, air-properties, gravity ve
wind nesnelerini defaultsuz bağlar. NAT-009C'nin public altitude semantiği olan
geopotential height korunur: launch değerine signed local ENU current-z minus
launch-z eklenir; conversion/origin assumption/clamp yoktur. Frozen sıra
atmosphere/air, gravity, wind, NAT-010A flow, NAT-010B conditions, timeline,
C.3A thrust, C.3B motor, C.3C total mass, A.1 drag ve NAT-014 dynamics'tir.
Aynı motor time thrust/property için kullanılır; pre-ignition fallback yoktur.
Temporary NAT-014 inputs result'ta saklanmaz. Static stability kritik yola girmez;
upstream error families aynen propagate edilir. V&V: altitude 1323.25/1170 m;
F50 .012 s thrust=51.377 N, motor mass=.08474793212175599 kg, total mass=
.6355969349495034 kg; zero-wind Vrel=(3,4,5); controlled dv/dt=(0,0,5).
Focused/new simulation 21 PASS; dynamics/NAT-013/014 regression 69 PASS. Full
historical suite gate talimatıyla çalıştırılmadı. Integration/RK4/events/rail/
recovery/6DOF ve NAT-016 uygulanmadı.
[PhysicsEvaluator doğrulama kaydı](../verification/nat-015-physics-evaluator-3dof.md).
NAT-015 IMPLEMENTATION GATE: PASS.
