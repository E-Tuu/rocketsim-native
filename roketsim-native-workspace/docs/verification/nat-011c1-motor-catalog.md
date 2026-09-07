# NAT-011C.1 — Motor katalog kaynak ve doğrulama kaydı

Tarih: 2026-09-07. Kabul edilmiş başlangıç: `88ab5bc`, temiz ağaç,
760 test PASS. Kapsam yalnız motor veri modeli ve katalogdur.

## Kaynak otoritesi

NAR certification/static-test > üretici yuvarlanmış katalog > üçüncü taraf
simülasyon verisi. Kaynaklar geliştirme sırasında incelendi; testler ve
production kodu çevrimdışıdır.

- [NAR S&T AeroTech F50 sertifikası ve RASP eki](https://www.thrustcurve.org/motors/cert/60c63bfcb5bc370004713e82/F50.pdf):
  NAR tarafından hazırlanmış belge, ThrustCurve üzerinde barındırılıyor.
  Test 1995-09-03, güncelleme 1/98; sayfa 1'de 4 saniyelik varyant için
  başlangıç kütlesi 84.9 g, propellant 37.9 g seçildi. Ölçülmüş referanslar
  76.83 N*s, 53.73 N, 79.59 N, 1.43 s'dir. Üstteki 80 N*s sınıf/certified
  değeri measured impulse yerine kullanılmaz. Nominal gecikme 4 s, ölçülen
  gecikme değildir. Sayfa 2, 2000-07-04 RASP aktarımındaki 31 thrust noktasını
  sağlar. Ekin 83.6 g header kütlesi, 4 s satırındaki 84.9 g ile farklıdır;
  header kütlesi alınmaz. Bu fark eğri noktalarını değiştirmek için gerekçe değildir.
- [AeroTech F50-4T ürün 65004](https://aerotech-rocketry.com/products/product_24cdff8c-59d9-6dcb-a66e-8bc9babb0c2c):
  29 mm × 98 mm, Blue Thunder, single-use, 4 s varyantı; yuvarlanmış 85 g ve
  38 g değerleri seçilmiş Native 84.9 g ve 37.9 g değerlerini değiştirmez.

Bu kayıt dondurulmuş tarihsel F50T kaynak profilidir; farklı üretim/eğri
varyantları sessizce birleştirilmez. SINGLE_USE fiziksel yeniden yüklenemeyen
motor mimarisidir; katalog nesnesinin tekrar kullanımını engellemez.

## Kanonik veri ve sahiplik

Kullanıcı desteklenen motorun balistik verisini elle girmek yerine katalogdan
`aerotech_f50_4t` seçer. İlk katalog tek kayıt içerir. Bilinmeyen ID KeyError,
tekrarlanan ID ValueError üretir; varsayılan motor yoktur.

Yalnız F50 katalog kaydına açık `(0.0 s, 0.0 N)` başlangıç noktası eklenir.
Sonraki 31 kaynak noktası birebir korunur: sıralama, smoothing, ölçekleme,
resampling veya generic endpoint insertion yoktur. Thrust curve sonraki
dynamics için veridir; certification summary yalnız V&V metadata'dır.

MotorDefinition/ThrustSample/certification/provenance/catalog frozen ve
slotted'dır; eğri ve katalog tuple'dır. Sonlu semantik hatalar structured
MotorValidationError, sayısal non-finite girdiler generic ValueError üretir.

## Test kanıtı

MOTOR-T01..19 veri sözleşmeleri/alan doğrulaması; T20..28 exact katalog,
32 nokta ve kaynak koruması; T29..31 test-only integral ve certification;
T32..36 katalog lookup/immutability; T37..39 non-finite/error/yuvarlama;
T40 scope protection. Parametrizasyon ve edge kontrolleriyle 97 test PASS.

Trapez integrali yalnız testte: 76.828387 N*s (analitik tolerans 1e-12 N*s).
76.83 N*s referansına fark 0.001613 N*s; iki ondalık kaynak hassasiyeti için
0.005 N*s mutlak tolerans kullanılır. Tepe 0.354 s'de 79.590 N; son nokta
(1.430 s, 0.000 N). Production impulse evaluator eklenmedi.

Regresyonlar: mass 55, materials 13, geometry 184, flight_conditions 98,
environment/V&V 260, math/V&V 148 PASS. Full: 857 PASS = 760 + 97.
Kabul edilmiş diğer production paketleri değiştirilmedi.

Installation/fit C.2, motor mass/CG C.3'e ertelidir. Runtime thrust
interpolation, depletion, total rocket mass/CG ve flight events yoktur.

NAT-011C.1 IMPLEMENTATION GATE: PASS.
