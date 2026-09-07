# NAT-011C.3B — Motor kütle ve CG evrimi

Başlangıç: `101ba43` (C.3A), temiz çalışma ağacı; `145b245` atası doğrulandı.
2026-09-07 başlangıç full pytest: 954 PASS. C.3A/C.2 fiziği değiştirilmez.

## Üç ayrı otorite

1. Motor Catalog kaynak zaman serilerini ve provenansı sahiplenir.
2. MotorPropertyModelProfile eksik veride veya kontrollü karşılaştırmalarda
   kullanılacak yöntemi açıkça seçer; MotorDefinition parçası değildir.
3. Propulsion runtime sonucu motor mass(t), yerel CG ve x_geo CG'yi verir.

MotorMassSample(time_s, mass_kg), MotorCgSample(time_s, cg_from_front_m)
frozen/slotted'dır. MotorDefinition'a zorunlu olarak belirtilen, None kabul
eden mass_curve/cg_curve alanları; provenansa aynı biçimde mass_curve_source
ve cg_curve_source eklendi. None doğrulanmış kaynak zaman serisi yok demektir;
sıfır değer veya otomatik fallback anlamına gelmez.

F50'nin iki kaynak eğrisi ve iki yeni kaynak referansı açıkça None'dır.
Doğrulanmış 0.0849 kg, 0.0379 kg, 0.098 m ve tüm thrust noktaları korunur.
Modelden üretilen sonuçlar asla kaynak tuple'larına yazılmaz.

## Kaynak veri sözleşmesi

Her kaynak eğrisi en az iki doğru türde örnek içeren tuple'dır; t=0'da başlar,
zamanı kesin artar ve en az thrust sonuna uzanır. Kütle pozitif ve artmayan
olmalıdır; ilk kütle initial_mass ile tam eşittir. CG motorun ön
yüzünden arkaya ölçülür ve [0,L] aralığındadır; monoton hareket şartı yoktur.
Kaynak referansı ile eğri varlığı birebir eşleşir; boş referans reddedilir.
Sonlu veri hataları MotorValidationError, ham non-finite sayılar generic
ValueError üretir. Kaynak yuvarlaması sessizce uzlaştırılmaz.

## Açık fizik politikası ve runtime

MotorMassEvolutionModel yalnız EXPLICIT_CURVE / IMPULSE_PROPORTIONAL;
MotorCgEvolutionModel yalnız EXPLICIT_CURVE / FIXED_MIDPOINT içerir.
DEMO_MOTOR_PROPERTY_MODEL_PROFILE açık olarak impuls-orantılı kütle ve sabit
orta nokta seçer. Kullanıcının seçimi kaynak mevcut olsa da uygulanır.
Explicit yöntem seçilip veri yoksa PropulsionEvaluationError oluşur; fallback
yapılmaz. Bilinmeyen model seçimi de reddedilir.

MotorPropertyEvaluator.evaluate(*, installation, model_profile, motor_time_s)
yalnız installation.motor'u kullanır; ikinci motor girdisi yoktur. Zaman
ateşlemeden beri geçen süredir. Parametresiz, geçmişsiz ve deterministiktir.

Explicit mass/CG her biri kendi bağımsız zaman grid'inde doğrusal değerlendirilir.
Exact düğüm değeri korunur, kendi eğrisi sonrasında terminal değer tutulur.
Thrust bitmiş olsa bile daha uzun explicit eğri değerlendirmesi devam eder.

Impuls modeli C.3A MotorThrustCurveEvaluator ve MotorCurveAnalyzer sonuçlarını
tüketir: f=J(t)/J_total, m=m_initial−m_propellant*f. İkinci itki interpolasyonu
veya impuls integrali yazılmadı. f sonlu ve [0,1] olmalıdır. Tam thrust kuyruğu
kütle azalmasına katılır; %5 etkin süre depletion sınırı değildir.

Midpoint yöntemi yerel CG=L/2; x_geo CG=installation.motor_front_x_geo_m+yerel CG.
Bu yalnız kabul edilmiş tek/eş eksenli profil içindir; BODY/WORLD dönüşümü yoktur.
MotorMassProperties frozen/slotted olarak mass_kg, cg_local_from_front_m,
cg_x_geo_m ve exact seçilmiş model_profile tutar. Kütle sonlu/>0, yerel CG
sonlu/[0,L], x_geo CG sonlu olmalıdır. Başarısız türetim mevcut
PropulsionEvaluationError üretir; clamp/abs/epsilon onarımı yoktur.

## V&V

| F50 zamanı (s) | Motor kütlesi (kg) |
| --- | --- |
| 0 | 0.0849 |
| 0.354 | 0.0724014701890071 |
| 1.400 | 0.047016141471977205 |
| 1.430 ve sonrası | 0.0470 |

Yerel midpoint CG 0.049 m; gerçek A.2/C.2 kurulumuyla x_geo CG 0.956 m.
%5 sonu → 1.400 s → curve_end sıralamasında kütle kesin azalmaktadır.

Sentetik explicit fixture t=.25'te (mass, local CG, x_geo CG)=
(.090 kg,.045 m,.945 m); t=.75'te (.070 kg,.055 m,.955 m).
Ayrı thrust/mass/CG grid'leri ve post-burn explicit değişimi ayrıca doğrulanır.
Kaynak eğrileri varken fallback yöntemlerini seçen iki karma profil ve demo
profili test edilir. Test-side C.3A sonuç enjeksiyonu mevcut evaluator/analyzer
çıktılarının tüketildiğini gösterir. Bozuk oran/sonuçlar onarılmadan reddedilir.

## Test eşlemesi ve sınır

- MPROP-T01..06: kaynak örnekleri, değişmezlik, sayı/alan doğrulaması.
- T07..23: açık None şeması, tuple/grid/coverage/değer/provenance ve F50 yoklukları.
- T24..33: enum/profil/API, zaman ve explicit-veri zorunluluğu.
- T34..41: kendi grid'inde explicit interpolasyon, exact/terminal ve post-burn.
- T42..49: C.3A delegasyonu, F50 mass ve tam kuyruk tüketimi.
- T50..59: midpoint/yerel dönüşüm, sentetik fixture, karma/açık model seçimi.
- T60..66: değişmezlik, determinizm, exact profil, hatalar ve kaynak yazmama.
- T67..68: yalnız motor; structural toplam/inertia/event/thrust-vector yok.

C.1 test uyarlaması yalnız yeni yetkilendirilmiş şema/exports alanlarıdır;
önceki fiziksel assertions korunur. Mevcut provenance testinin iki yeni alana
yayılması dört ek durum sağlar. C.3C roket toplamları uygulanmaz.

## Regresyon sonucu

Odaklı C.3B: 79 PASS; tüm propulsion 277 PASS; C.3A ayrıca 63 PASS, C.2
ayrıca 34 PASS. Geometry 184, materials 13, mass 55, flight_conditions 98,
environment/V&V 260, math/V&V 148 PASS. Full 1037 PASS = 954 mevcut + 79
yeni C.3B + 4 ek provenance durumu. Önceki fiziksel assertions korunmuştur.
Dondurulmuş sözleşmeden sapma veya yeni bağımlılık yoktur.

NAT-011C.3B IMPLEMENTATION GATE: PASS.
