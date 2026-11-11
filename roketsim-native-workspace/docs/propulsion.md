# Motor ve itki

## Amaç

Motorun uçuş boyunca ürettiği itkisini, birikimli impulsunu, kalan kütlesini
ve eksenel kütle merkezini hesaplar. Motorun araç üzerindeki nominal
konumunu da belirler.

## Kullanılan Fiziksel Model

Motor zamanı $t_m$, ateşlemeden itibaren geçen süredir (s). İtki $T(t_m)$
(N), kaynak eğrinin düğümleri arasında doğrusal değişir. Birikimli impuls
$J(t_m)=\int_0^{t_m}T(\tau)\,d\tau$ (N·s), tamamlanan ve kısmi
aralıkların alanlarından bulunur. Eğri sonu itki kesimini tanımlar.
Sonrasında itki sıfır, impuls toplam değerinde sabittir.

İmpulsa orantılı tüketim modelinde motor kütlesi kg cinsinden

$$
m(t_m)=m_\mathrm{dolu}-m_\mathrm{itici}\frac{J(t_m)}{J_\mathrm{toplam}} .
$$

$m_\mathrm{itici}$, kaynakta verilen toplam itici madde kütlesidir.
Sabit orta nokta modelinde motor kütle merkezi nominal motor uzunluğunun
yarısındadır. Araç üzerindeki eksenel konumu, bu noktanın montaj
referansına taşınmasıyla bulunur.

## Mevcut Özellikler

- RASP ENG biçiminde haricî eğri okuma ve kaynak kimliği. F50 kataloğu
  bu adayda kullanılabilir. Hazır TEKNOFEST Rev1 kataloğu üç özgün ENG
  kaynağının sınıf yolunda bulunmasını ve SHA-256 eşleşmesini gerektirir.
  Bu yayın ağacı bu üç kaynağı içerir. Haricî motor profili için uygun eğri,
  kütle, geometri, kurulum ve model seçimleri açıkça sağlanır.
- İmpulsa orantılı veya açık zaman eğrisiyle tanımlanan motor kütlesi.
  Eksenel kütle merkezi için sabit orta nokta veya açık zaman eğrisi.
- Motor ve yuva boyutlarından nominal eksenel konum, geçme uzunluğu ve
  radyal boşluk hesabı. Açık özellik eğrileri için zaman kapsamı ve
  kaynak bilgisi denetlenir.

## Varsayımlar ve Sınırlamalar

Uçuş modeli tek motor kullanır. İmpuls oranı, gerçek yanma geometrisinin
veya ölçülmüş kütle kaybı profilinin yerini tutan bir modeldir. Orta nokta
seçimi ölçülmüş motor kütle merkezi değildir. Nominal uyum, gerçek motor
ve araç montajının toleranslarını, fiziksel boşluklarını veya güvenliğini
doğrulamaz. Kaynak itki verisi uçuş sertifikasyonu sağlamaz.

## Planlanan Geliştirmeler

Ek motor verileri, ölçülmüş kütle geçmişleri ve montaj doğrulaması için
kaynak izlenebilirliğini koruyan genişletmeler değerlendirilecektir.

[ENG kaynak kaydı](verification/motor-001-teknofest-eng-import.md) ·
[Kaynaklı motor modeli doğrulaması](verification/nat-037-n37-02-source-backed-motor-runtime.md)
