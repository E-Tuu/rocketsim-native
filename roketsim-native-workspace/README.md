# RoketSim Native

RoketSim Native, model roket simülasyonu için çevrimdışı çalışan ve bağımsız olarak
test edilebilen bir Python fizik motorudur. Fizik çekirdeği SI birimlerini kullanır;
çevre, araç, itki, aerodinamik, dinamik, sayısal yöntemler, olaylar ve simülasyon
yaşam döngüsü sorumluluklarını açık biçimde ayırır.

## Güncel Durum

İlk Native sabit adımlı 3DOF balistik yörünge temeli
`0b5aa09b76af2a25c96689c63474dd1e6ac396e5` commit'inde doğrulanmıştır. Kabul
edilmiş NAT-022 kilometre taşı, üretim zincirinin tamamını mock kullanmadan
çalıştırmış ve eksiksiz depo regresyon paketini başarıyla geçmiştir.

## Özellikler

- Yerel WORLD ENU koordinatları ile kabul edilmiş atmosfer, hava özelliği, yerçekimi,
  sabit rüzgâr, bağıl akış ve uçuş koşulu modelleri.
- Çözümlenmiş tek kademeli geometri, yapısal kütle, motor kurulumu ve kesin
  AeroTech F50-4T katalog tanımı.
- Kanonik itki eğrisi değerlendirmesi, impuls, motor kütle değişimi ve toplam roket
  kütlesi/CG birleşimi.
- Basic Drag V1 ile doğrusallaştırılmış statik kararlılık CNa, CP ve statik marjı.
- Sabit fırlatma yönlü itki kullanan noktasal kütle 3DOF öteleme dinamiği.
- Açık sabit-adım yapılandırması ve klasik dört aşamalı RK4 integrasyonu.
- BURNOUT, APOGEE ve terminal GROUND olaylarının konumlandırılması.
- Değiştirilemez yörünge/olay snapshot'ları, simülasyon yürütümü ve sonuç sorguları.

## Mimari

```text
Çevre
    ↓
Araç / İtki
    ↓
Aerodinamik
    ↓
Dinamik
    ↓
RK4
    ↓
Olaylar / Kaydedici
    ↓
Simülasyon Motoru
    ↓
Simülasyon Sonucu
```

## Doğrulanmış Demo

Kabul edilmiş dikey ve sıfır rüzgârlı demo, katalogdaki AeroTech F50-4T motorunu
kullanır. Burnout `1.430 s` anında, apogee yaklaşık `279.53 m` yükseklikte ve
`7.012 s` anında, terminal zemin geçişi ise yaklaşık `15.832 s` anında gerçekleşir.
Kilometre taşı denetimi **1431 başarılı, 0 başarısız, 0 atlanan** testle tamamlanmıştır.

## Hızlı Başlangıç

Depo kökünden, mevcut yapılandırılmış proje ortamını kullanın:

```powershell
cd roketsim-native-engine
.\.venv\Scripts\python.exe -m pytest -q
```

Depo şu anda komut satırı simülasyon uygulaması yerine bir fizik kütüphanesi ve
test paketi sunmaktadır.

## Testler

Birim regresyonları `roketsim-native-engine/tests/unit/` altında, tam zincir
doğrulaması ise `roketsim-native-engine/tests/integration/` altında bulunur.
Kilometre taşı denetimleri, Hızlı Başlangıç bölümünde gösterilen yapılandırılmış
pytest paketinin tamamını çalıştırır.

## Dokümantasyon

- [Çevre ve uçuş koşulları](docs/environment.md)
- [Araç, kütle ve itki](docs/vehicle-propulsion.md)
- [Aerodinamik](docs/aerodynamics.md)
- [Dinamik ve sayısal yöntemler](docs/dynamics-numerics.md)
- [Simülasyon motoru](docs/simulation-engine.md)
- [Doğrulanmış Native demo](docs/demo-validation.md)

Ayrıntılı tarihsel doğrulama ve provenans kayıtları
[`docs/archive/nat/`](docs/archive/nat/) altında korunur. Mimari karar kayıtları
[`docs/decisions/`](docs/decisions/) altında tutulmaya devam eder.

## Güncel V1 Sınırlamaları

- Simülasyon ignition anında başlar; pad reaksiyonu, Liftoff, fırlatma kılavuzu
  hareketi ve rail clear modellenmez.
- Recovery ve deployment modellenmediği için doğrulanmış demo balistik olarak alçalır.
- Dinamik yalnız öteleme 3DOF kapsamındadır; attitude ve aerodinamik momentler yoktur.
- Sayısal ilerletme, doğrusal bracket olay konumlandırmasıyla sabit adımlı RK4'tür.
- Basic Drag V1 kabul edilmiş sıfır-AoA subsonik kapsamındadır ve rüzgâr yalnız sabittir.
- Doğrulanmış temel, henüz OpenRocket yörünge paritesi iddiasında bulunmaz.

## Yol Haritası

- Fırlatma ve kılavuz fiziği
- Aerodinamik V2
- 6DOF dinamik
- Recovery ve deployment
- Gelişmiş çevre ve sayısal yöntemler
- OpenRocket parite kampanyası
- Frontend ve public entegrasyon yüzeyi
