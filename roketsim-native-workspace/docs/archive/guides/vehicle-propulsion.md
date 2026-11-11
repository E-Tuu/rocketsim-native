# Araç ve İtki

## Güncel V1 Kapsamı

- Roket domain'i, sivri konik burun, silindirik gövde, tek trapezoidal fin seti ve
  tek eş eksenli motor-mount assembly'sini kanonik x_geo üzerinde çözümler.
- Referans çapı, alanı ve uzunluğu; ıslak alanlar, fin tanımlayıcıları ve bileşen
  yerleşimi geometriye ait değerler olarak kalır.
- Yapısal kütle, kabul edilmiş malzeme hacimlerini, yoğunlukları ve bileşen CG'lerini
  statik yapı kütle özelliklerinde birleştirir.
- Motor kurulumu, radyal/eksenel uyumu doğrulamak ve motoru konumlandırmak için
  çözümlenmiş mount geometrisini ve katalog motor boyutlarını tüketir.
- `MotorDefinition`; doğrulanmış kimlik, boyutlar, kütleler, sertifikasyon metadata'sı,
  provenans ve kanonik itki örneklerinin değiştirilemez authority'sidir.
- Runtime itki ve kümülatif impuls, tam kanonik parçalı-doğrusal itki eğrisini kullanır.
- Motor kütlesi kabul edilmiş açık model profiliyle değişir; toplam roket kütlesi/CG,
  statik yapı ile değerlendirilmiş motor özelliklerini birleştirir.
- İlk demo, kesin kabul edilmiş sertifikasyon/kaynak değerleri ve single-use fiziksel
  mimarisiyle üretim kataloğundaki AeroTech F50-4T'yi kullanır.
- Kabul edilmiş burnout sınırı authority'si
  `MotorCurveStatistics.curve_end_time_s`'dir; F50-4T için ignition sonrası 1.430 s'dir.
- Kabul edilmiş demo araç lineage'i arşivlenmiş NAT-022 kanıtında belgelenmiştir.

## Kapsam Dışı / Planlanan

- Çoklu motorlar, motor cluster'ları ve staging ertelenmiştir.
- Gecikmeli veya sıralı ignition timeline'ları ertelenmiştir.
- Thrust-vector kontrolü ve gimbal davranışı ertelenmiştir.
- Gelişmiş motor kütlesi, CG ve inertia değişimi ertelenmiştir.
- Daha geniş doğrulanmış motor kataloğu entegrasyonu planlanmaktadır.
