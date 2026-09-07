# Native Engine Register

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
