# Uçuş, kılavuz ve kurtarma

## Amaç

Roketin konumunu ve hızını zamanla ilerletir. Kılavuzdan serbest uçuşa
geçişi, uçuş olaylarını, isteğe bağlı kurtarmayı ve sonlanmayı ele alır.

## Kullanılan Fiziksel Model

Yere bağlı eksenler doğu, kuzey ve yukarıdır (ENU). Konum metre, hız m/s
ve zaman saniye cinsindedir. Sabit birim itki yönü $\hat{\mathbf u}$ ile

$$
\dot{\mathbf r}=\mathbf v,\qquad
\dot{\mathbf v}=(T\hat{\mathbf u}+\mathbf F_D)/m+\mathbf g .
$$

$T$ ve sürükleme $\mathbf F_D$ newton, güncel kütle $m$ kilogramdır.
Yerçekimi ivmesi aşağı yönlü, sabit 9,80665 m/s²'dir. Klasik RK4,
her adımda dört türev değerlendirir. Aday durum
$y_{n+1}=y_n+h(k_1+2k_2+2k_3+k_4)/6$ bağıntısıyla bulunur.
Nominal adım $h$ saniye cinsindedir ve seçilen politika motor
sınırlarında adımı bölebilir.

İdeal kılavuz hareketi eksenle sınırlar. Serbest ivmenin kılavuz eksenine
izdüşümü kullanılır. Araç fırlatma desteğinde durgun başlar ve bileşke
kuvvetin eksenel bileşeni kılavuzun çıkışına yöneldiğinde hareket eder.
İki bağlantı noktalı modelde ön bağlantı kılavuzun üst ucuna
ulaştığında serbest uçuşa geçilir. Geçiş için ilgili adım
bölümü yeniden hesaplanır ve kalan süre serbest uçuşta ilerletilir.

## Mevcut Özellikler

- İtki kesimi (burnout) kaynak eğri sonunda gerçekleşir. Raporlanan
  tepe noktası (apogee), düşey hızın pozitiften sıfır veya negatife
  geçişinden tahmin edilir. Yer teması, fırlatma düzlemine aşağı
  yönde dönüşte belirlenir. Ara olay durumları zaman aralığının iki ucu arasında
  doğrusal interpolasyonla tahmin edilir.
- Kurtarma, kabul edilmiş irtifa örneklerine dayanır. İlk uygun örnek
  örneklenmiş en yüksek irtifanın **0,01 m'den fazla** altına düştüğünde
  tetiklenir. Açılma zamanı, tetikleme örneğine verilen gecikme eklenerek
  belirlenir. Yer teması önceliklidir. Pozitif itki altında açılma
  desteklenmez.
- Etkin kurtarmada gövde sürüklemesinin yerine, sabit etkin sürükleme
  alanı $(C_D A)_\mathrm{etkin}$ (m²) olan tek paraşütün kuvveti kullanılır.

$$
\mathbf F_D=-\tfrac12\rho(C_D A)_\mathrm{etkin}
|\mathbf v_\mathrm{bağıl}|\mathbf v_\mathrm{bağıl}.
$$

- Yer teması, kullanıcı iptali ve desteklenmeyen model ya da sayısal
  durumlar ayrı sonuçlar verir. İptal, kabul edilmiş uçuş geçmişini korur.

## Varsayımlar ve Sınırlamalar

İtki yönü sabittir. Dönme hareketi ve 6DOF çözülmez. Ön bağlantı
ayrıldıktan sonra arka bağlantının teması hesaba katılmaz. Kılavuza geri çarpma,
paraşüt şişmesi ve zemin çarpması sonrası hareket kapsam dışındadır.
Raporlanan tepe noktası ile kurtarma tetikleme örneği farklı kurallara
dayanır. Adım içindeki yer teması tahmini, son kabul edilmiş durumdan
ayrı tutulur. Temsil edilemeyen zaman sınırları sayısal hata olarak raporlanır.

## Planlanan Geliştirmeler

Gerçek kılavuzdan tam ayrılma, ayrıntılı kurtarma ve dönme hareketi
daha geniş fizik kapsamı için ayrı geliştirme ve doğrulama alanlarıdır.

[Uçuş olayları doğrulaması](verification/nat-037-n37-04b1-integrated-flight-semantics.md) ·
[Kurtarma doğrulaması](verification/nat-035-apogee-recovery.md)
