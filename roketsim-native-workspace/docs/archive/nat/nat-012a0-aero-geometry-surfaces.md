# NAT-012A.0 — Aerodinamik geometri ve yüzey veri sözleşmesi

Tarih: 2026-09-07. Başlangıç `149f4f0`, temiz ağaç, full 1072 PASS.
Bu gate yalnız fiziksel şekil/tasarım ve geometrik türetimlerdir; drag hesabı yoktur.

## Sayısal değer provenansı — kalıcı proje kuralı

Bu gate ve sonrasında her üretim sayısı şu kategorilerden biriyle gerekçelendirilir:
evrensel/standart sabit; kullanıcı/roket tasarım girdisi; doğrulanmış katalog/kaynak
verisi; açık fizik modeli parametresi/politikası. Demo/test ölçüsü genel engine
sabitine dönüşmez. Geometrik formüllerdeki 2, 1/2, 2/3 gibi çarpanlar dondurulmuş
şekil bağıntılarının katsayılarıdır; pi matematik sabitidir. Yeni fizik preset'i yoktur.

## Bağımsız sahiplik

- Geometry: fiziksel şekil, ölçü, yerleşim, fin kesiti ve geometrik aero nicelikler.
- Materials: bulk material kimliği ve yoğunluğu.
- Aerodynamic Surfaces: dış finish ve eşdeğer aerodinamik pürüzlülük tasarım/kaynak verisi.
- Sonraki Aerodynamics: bu girdilerden katsayı hesapları; A.0 kapsamında değildir.

Ham roket geometrisinde reference_geometry_policy zorunludur. Yalnız
ReferenceGeometryPolicy.MAXIMUM_DIAMETER desteklenir: en büyük dış eksenel
simetrik airframe çapı. Tek çaplı profilde airframe_diameter_m kullanılır;
fin span, iç mount/rings ve seçilmiş motor çapı hariçtir. D_ref=L_ref ve
A_ref=pi*D_ref²/4 eski semantiği korunur. 0.100 m engine sabiti değildir.

Ham fin cross_section zorunludur; yalnız FinCrossSection.SQUARE desteklenir.
Bu kalınlık yönündeki kenar/kesit şeklidir, trapezoidal planformun kare olması
anlamına gelmez. ROUNDED/AIRFOIL placeholder'ları eklenmez.

## Tek resolver ve geometri bağıntıları

Yeni sonuçlar aynı ResolvedRocketGeometry içindedir. aerodynamic_length_m
mevcut overall_length_m ile aynı bir kez hesaplanan dış yapısal extent'i
kullanır. Fin aft uzantısı korunur; motor_aft_reference bunu uzatmaz.
reference_length ile aerodynamic_length ayrı niceliklerdir.

Konik nose: wetted=pi*R*hypot(L_n,R), frontal=pi*R²,
fineness=L_n/(2R), half-angle=atan2(R,L_n). Nose base diski wetted'a eklenmez.
Body wetted=2*pi*R*L_body; end face eklenmez. airframe_aft_base_area=pi*R²
yalnız dış airframe geometrik kesitidir; nozzle/plume/exposure çözümü değildir.

Fin planform alanı mevcut hacim hesabındaki per-fin alan değişkeninden
sunulur: (C_root+C_tip)*span/2. MAC=(2/3)*(C_root+C_tip−C_root*C_tip/(C_root+C_tip)).
LE sweep=atan2(signed offset,span). Yalnız açı saklanır; cos/cos² otoritesi yoktur.
Fin count mevcut tek adet otoritesidir; duplicate total-fin alanı eklenmez.

Yeni pozitif alan/uzunluk/oran sonuçları sonlu ve >0; nose angle (0,pi/2),
signed sweep sonlu olmalıdır. Yeni türetilmiş geçersizlikler mevcut structured
GeometryValidationError kullanır. Önceki geometri doğrulama yolları değiştirilmez.
Unsupported enum'lar tahmin edilmez; clamp/repair yapılmaz.

## Yüzey sözleşmesi

SurfaceFinish(name,equivalent_roughness_m) frozen/slotted'dır. Ad boş olamaz;
pürüzlülük sonlu ve >=0'dır. Sıfır geçerli; keyfi üst sınır yoktur. Non-finite
girdi generic ValueError, sonlu negatif/boş ad SurfaceValidationError
(error_code,field_name,value) üretir.

SingleStageRocketAerodynamicSurfaces nose/body/fins için üç zorunlu finish
seçimi tutar. Aynı immutable finish paylaşılabilir veya farklı seçimler yapılabilir.
Material'dan finish çıkarılmaz; yüzey seçimi density/mass değiştirmez.
Üretim roughness preset kataloğu yoktur; test sayıları sentetiktir.

## Analitik referans

| Nicelik | 100 mm tasarım fixture'ı |
| --- | --- |
| L_ref / A_ref | 0.100 m / 0.007853981633974483 m² |
| Aerodinamik uzunluk | 1.000 m |
| Nose wetted / body wetted | 0.04777390519679037 / 0.2199114857512855 m² |
| Nose frontal / airframe aft base | her biri 0.007853981633974483 m² |
| Nose fineness / half-angle | 3.0 / 0.16514867741462685 rad |
| Fin planform / MAC | 0.0156 m² / 0.13641025641025642 m |
| Fin LE sweep / kesit | 0.39479111969976155 rad / SQUARE |

150 mm ve 200 mm tasarımlar L_ref=.15/.20 m ve A_ref=pi*D²/4 sonucunu
verir. Büyük fin span/iç mount değişimleri referansa katılmaz. Fin aft uzantısı
1.04 m aerodynamic extent verir; motor aft referansını 10 m öteye taşımak
dış yapı uzunluğunu değiştirmez. Toleranslar denklem floating-point hassasiyetindedir.

## Test eşlemesi

AEROGEO-T01..09 enum/mandatory schema ve çok çaplı referans;
T10..13 ayrı uzunluklar ve extent sahipliği; T14..23 analitik nose/body/fin,
üçgen fin/signed sweep; T24..26 korunmuş hacim/centroid/mount/reference;
T27..28 türetilmiş invariant/değişmezlik. Unsupported enum ve non-finite angle
arızaları da test edilir.

SURFACE-T01..09 immutable/domain/zorunlu atama; T10..13 bağımsız/paylaşılan
finish ve material ayrımı; T14 üretim preset yasağı. SCOPE-T01..05 veri yüzeyinde
Reynolds/Cf/Cd/force/CP/CNa/dynamics yokluğu; T06 farklı tasarım çaplarıyla
fixture sabitinin üretim otoritesi olmamasının davranışsal kanıtıdır.

Eski fixture'lar yalnız iki mandatory enum için güncellendi. Eski fiziksel
assertions korunur; accepted result-fields testi yalnız yeni alanlarla genişletilir.
NAT-012A.1 drag/Cf/roughness/compressibility, NAT-012B CP/CNa/static margin,
nozzle/plume, kesit çeşitleri/preset kataloğu ve dynamics ertelidir.

## Regresyon sonucu

Odaklı aero-geometry 34 PASS, surfaces 15 PASS. Geometry toplam 218 PASS,
aerodynamics 15 PASS; materials 13, mass 90, propulsion 277, flight_conditions
98, environment/V&V 260, math/V&V 148 PASS. Full 1121 PASS = 1072 mevcut +49
yeni test. Önceki denklemler/katalog verileri değişmedi; izinli şema uyarlamaları
dışında sapma yoktur. Yeni bağımlılık yoktur.

NAT-012A.0 IMPLEMENTATION GATE: PASS.
