# RoketSim Native

## RoketSim Native nedir?

RoketSim Native, roket uçuşunu hesaplayan bağımsız bir Java 17 fizik
çekirdeğidir. Bir roketin konumunu, hızını ve uçuş olaylarını, verilen
araç ve çevre koşulları altında simüle eder.

## Projenin amacı

Amaç, uçuşu etkileyen fiziksel varsayımları görünür kılmak ve aynı
girdilerle tekrarlanabilir sonuçlar üretmektir. Modeller uygulama
arayüzünden bağımsızdır. Böylece bir sonucun hangi araç geometrisine,
motor verisine ve çevre koşuluna dayandığı incelenebilir.

## Hangi fizik modelleri mevcut?

- **Geometri ve kütle.** Tek kademeli ve tek motorlu roketler için konik burun,
  silindirik gövde, bir trapez kanat takımı ve motor yuvası modellenir.
  Yapısal kütle, eksenel kütle merkezi ve uygun girdilerle atalet hesaplanabilir.
- **Motor ve itki.** Kaynaklı itki-zaman eğrilerinden itki, impuls, motor
  kütle tüketimi ve motor kütle merkezi değerlendirilir.
- **Atmosfer ve aerodinamik.** Kuru standart atmosfer, sabit rüzgâr ve
  bağıl hava akışı kullanılır. Gövde sürüklemesi ve statik stabilite
  ayrı değerlendirilir.
- **Uçuş.** Sabit itki yönüyle üç boyutlu öteleme, RK4 ile ilerletilir.
  İdeal kılavuz, serbest uçuş, isteğe bağlı paraşüt, yer teması ve
  kullanıcı iptali desteklenir.

## Geçerlilik sınırları

Basic Drag modeli Mach 0–1 aralığında, sınırlar dâhil, geçerlidir. Statik
basınç merkezi ve normal kuvvet eğimi (CP/CNa) hesabı Mach 0,8'in altındadır.
Atalet hesabı, dönme hareketi veya 6DOF uçuş çözümü sağlamaz. Süpersonik
aerodinamik, genel bileşen ağacı ve çarpma sonrası hareket kapsam dışındadır.

Doğrulamada kullanılan L/M/N araçları sentetiktir. Motor kaynak kayıtları
uçuş sertifikasyonu sağlamaz. Bu yayın ağacı üç özgün TEKNOFEST Rev1 eğrisini
içerir. Kaynağı izlenebilir haricî motor verileri genel motor modeliyle
kullanılabilir. Sayısal doğrulama belirtilen modelleri ve
senaryoları kapsar. OpenRocket eşdeğerliği henüz doğrulanmamıştır.

## Gelecek geliştirmeler

Uygulama ve kullanıcı arayüzü entegrasyonu, daha geniş araç bileşenleri,
genişletilmiş aerodinamik ve dönme dinamiği geliştirme alanlarıdır.
OpenRocket karşılaştırması uygulama entegrasyonu aşamasına ertelenmiştir.

## Fizik modülleri

- [Geometri, kütle ve atalet](docs/geometry-mass.md)
- [Motor ve itki](docs/propulsion.md)
- [Atmosfer ve aerodinamik](docs/aerodynamics-environment.md)
- [Uçuş, kılavuz ve kurtarma](docs/flight-simulation.md)

İkincil başvurular arasında [model sınırları ve mimari](docs/architecture.md),
[doğrulama ve tarihsel kayıtlar](docs/verification.md) ve
[yol haritası](docs/roadmap.md) bulunur.
