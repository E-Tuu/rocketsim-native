# NAT-011C.3C — Toplam roket kütlesi ve CG

Tarih: 2026-09-07. Başlangıç C.3B `ef1b5d0`, temiz ağaç; `101ba43` atası
doğrulandı. Başlangıç full pytest 1037 PASS.

## Sahiplik ve kapsam

`mass.total` yalnız bir StructuralMassProperties ve bir hazır runtime
MotorMassProperties katkısını toplar. Static structure aggregate kendi
kütle/CG otoritesidir; motor runtime sonucu kendi kütle/x_geo CG otoritesidir.
Alt yapı bileşenleri, malzemeler, Geometry veya motor kaynak/model fiziği
yeniden değerlendirilmez. Zaman girdisi ve gizli kütle katkısı yoktur.

Parametresiz RocketMassPropertiesCalculator.evaluate(*, structural_properties,
motor_properties) frozen/slotted RocketMassProperties döndürür. Sonuç yalnız
total_mass_kg ve total_cg_x_geo_m içerir; input/profil/geometry kopyalanmaz.

M=m_S+m_M; x=(m_S*x_S+m_M*x_M)/M. x_geo burun ucunda sıfır, arkaya pozitiftir.
Eşit x_S=x_M durumunda matematiksel özdeşlik doğrudan kullanılarak aynı
koordinat exact korunur; bu yön seçimi, clamp veya farklı fizik değildir.

Sonlu/>0 toplam kütle ve sonlu toplam CG kontrol edilir. Kapalı contributor
span min(x_S,x_M)<=x<=max(x_S,x_M) doğrulanır. Bu ağırlıklı ortalama
invariant'ıdır, Geometry overall-length kontrolü değildir. Geometri girdisi
yoktur; negatif koordinatlar da matematiksel toplamada desteklenir.
Türetim hataları mevcut MassValidationError(error_code,field_name,value)
üretir; non-positive mass, non-finite mass/CG ve span ihlali onarılmaz.

Kabul edilmiş upstream katkılar pozitif kütlelidir. Hata testlerinde
tutarsız upstream sonuçlar enjekte edilerek türetim kontrolleri doğrulanır.
Calculator model_profile içeriğine bakmaz; hangi model sonucu ürettiyse
üretsin aynı hazır motor kütlesi/CG için sonuç aynıdır.

## Gerçek fixture entegrasyonu

GeometryResolver → StructuralMassPropertiesCalculator ve
MotorInstallationResolver → MotorPropertyEvaluator zincirleri kullanılır.
Yapı: 0.5508490028277474 kg, x_geo CG 0.6266199705547180 m.
Motor: F50 demo profili, sabit x_geo CG 0.956 m.

| Motor zamanı (yalnız upstream test girdisi) | Toplam kütle kg | Toplam x_geo CG m |
| --- | --- | --- |
| 0 s | 0.6357490028277474 | 0.6706064563777736 |
| 0.354 s | 0.6232504730167545 | 0.6648832361521045 |
| 1.400 s | 0.5978651442997246 | 0.6525224306832178 |
| 1.430 s | 0.5978490028277474 | 0.6525142370178312 |

Başlangıç ve terminal toplam kütle referansları exact karşılaştırılır;
denklem kaynaklı diğer referanslar strict floating-point toleranslıdır.
Toplam kütlenin hazır iki katkı toplamına exact eşitliği ayrıca doğrulanır.
1.400 s kuyruğu final state değildir. Aft motor kütlesinin azalması CG'yi
daha küçük x_geo'ya, yani öne taşır.

## Test eşlemesi

- RMASS-T01..04: frozen/slotted iki alanlı sonuç ve parametresiz/keyword-only API.
- T05..08: sum/weighted CG; alt bileşenlerden farklı hazır aggregate değerlerini
  tüketerek otorite sınırlarının davranışsal kanıtı.
- T09..10: motor kütle/CG profillerinden bağımsızlık.
- T11..14: exact eşit CG, iki sıralama, negatif koordinatlar, contributor span.
- T15..20: non-positive/non-finite türetimler ve span ihlali; structured hata,
  clamp/abs/repair yasağı.
- T21..28: dört F50 referans anında gerçek upstream entegrasyonu.
- T29..30: tam kuyruk ayrımı ve öne CG kayması.
- T31..32: input değişmezliği, geçmişsiz determinizm.
- T33..38: zaman/geometri/model fiziği veya genel katkı listesi API'si yoktur.

Mevcut API bilinçli olarak tek yapı + tek motor içindir. Çoklu motor,
staging/cluster mimarisi geldiğinde yeniden değerlendirilecektir. Inertia/PAT,
override, payload/recovery/ejection kütlesi, events, vector, dynamics/RK4 ve
NAT-012 uygulanmadı. Önceki fizik veya katalog verisi değiştirilmedi.

## Regresyon sonucu

Odaklı C.3C 35 PASS; tüm mass 90 PASS; propulsion 277 PASS. Geometry 184,
materials 13, flight_conditions 98, environment/V&V 260, math/V&V 148 PASS.
Full 1072 PASS = 1037 mevcut + 35 yeni. Dondurulmuş fizik sözleşmesinden
sapma yoktur; yeni bağımlılık yoktur.

NAT-011C.3C IMPLEMENTATION GATE: PASS.
