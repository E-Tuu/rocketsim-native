# NAT-011C.3A — İtki, impuls ve eğri istatistikleri

Tarih: 2026-09-07. Başlangıç `145b245`, temiz çalışma ağacı, full 891 PASS.
`85f36f5` katalog atası doğrulandı. Önceki katalog/kurulum ve diğer fizik
modülleri değiştirilmez; sahiplik `propulsion.thrust` içindedir.

## Model ve sınırlar

MotorDefinition.thrust_curve tek runtime veri otoritesidir. motor_time_s
ateşlemeden beri geçen süredir; ateşleme planlaması/global saat burada yoktur.
İtki parçalı doğrusaldır. Tam örnek zamanında saklanan örnek birebir döner;
yakın zaman toleransla örneğe yapıştırılmaz. Kaynak noktalar değiştirilmez.

Tam impuls tüm aralıkların 0.5*(T_i+T_j)*(t_j-t_i) toplamıdır. Kümülatif
impuls tamamlanmış alanlar ile aktif parçanın analitik trapezidir. Geçmiş
çağrılara veya timestep biriktirmesine bağlı değildir. Geçici prefix alanları
iki public hesapta aynı özel yardımcıyla türetilir; kalıcı cache/state yoktur.
Eğri sonunda J=J_total; sonrasında T=0 ve J=J_total. Bu açık fiziksel sınırdır.

Peak eşitliğinde ilk örnek seçilir. %5 eşiği 0.05*peak'tir; ilk >= bölgesine
giriş ve son çıkış parçalı doğrusal kesişimlerle bulunur. Eşiğe eşit örnek
zamanı korunur. Birden çok bölgede aralıkların süreleri toplanmaz: son−ilk
kullanılır. Etkin süre karakterizasyon/V&V istatistiğidir, itki kesmesi değildir.

Bu ayrım C.3A talebinde dondurulmuş Native semantiğidir. Bu gate yeni bir
OpenRocket parity capture yapmaz. İleride ilk-demo burnout event otoritesi
curve_end_time_s olacaktır; effective_burn_end_5pct_s değildir. Event nesnesi
veya event mantığı burada uygulanmaz.

Certification yalnız dış V&V referansıdır. Kaynak katalog provenansı
[C.1 kaydında](nat-011c1-motor-catalog.md) korunur. Kaynak/ölçüm yuvarlaması
ile aynı-denklem toleransları karıştırılmaz.

## F50 doğrulama değerleri

| Nicelik | Eğriden türetilen sonuç |
| --- | --- |
| Tam impuls | 76.828387 N*s |
| Peak / zamanı | 79.590 N / 0.354 s |
| Tam eğri sonu | 1.430 s |
| %5 eşik | 3.9795 N |
| %5 ilk giriş | 0.0009294820639585808 s |
| %5 son çıkış | 1.3752712686567163 s |
| %5 etkin süre | 1.3743417865927579 s |
| T(1.400 s) | 2.181395348837211 N |
| J(1.400 s) | 76.79566606976745 N*s |

1.400 s, etkin çıkıştan sonra ve tam eğri sonundan öncedir. İtki pozitif,
impuls hâlâ artmaktadır; %5 kuyruk kesmesi yoktur. Kaynak 76.83 N*s ile
karşılaştırma 0.005 N*s mutlak toleranslıdır; denklem karşılaştırmaları birkaç
floating-point yuvarlama birimi düzeyinde bağıl tolerans kullanır.

## Test eşlemesi

- THRUST-T01..05: frozen/slotted sonuçlar, parametresiz/keyword-only API.
- T06..09: negatif/non-finite zaman ayrımı, sıfır başlangıç.
- T10..15: tüm 32 exact örnek, yakın zaman snap yasağı, doğrusal ara değer,
  tam/kısmi trapez ve tam eğri sınırları.
- T16..20: monoton impuls, sonrasında sabit total, geçmişsiz determinizm.
- T21..30: analitik üçgen, earliest peak, ilk/son eşik, çoklu geçiş/plateau.
- T31..41: F50 tam eğri, %5 değerleri ve kuyruk regresyonu.
- T42..44: değiştirilmiş certification özetinden bağımsızlık, kaynak
  değişmezliği, structured hata ve overflow/underflow reddi.
- T45: yalnız skaler eğri fiziği ve istatistik public sözleşmesi.

Ham non-finite zaman generic ValueError; sonlu negatif zaman ve geçersiz
türetilmiş sonuç PropulsionEvaluationError(error_code, field_name, value).
Clamp, epsilon/abs onarımı, spline, smoothing veya resampling yoktur.

Motor mass/CG kaynakları ve evrimi C.3B; yapısal+motor toplamları C.3C'ye
ertelidir. Thrust vector, frames, ignition/burnout events, dynamics/RK4 yoktur.

## Regresyon sonucu

Odaklı C.3A 63 PASS; tüm propulsion 194 PASS. Geometry 184, materials 13,
mass 55, flight_conditions 98, environment/V&V 260, math/V&V 148 PASS.
Full 954 PASS = 891 mevcut + 63 yeni. Dondurulmuş modelden sapma yoktur.

NAT-011C.3A IMPLEMENTATION GATE: PASS.
