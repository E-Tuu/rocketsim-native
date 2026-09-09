# Aerodinamik

## Güncel V1 Kapsamı

- Basic Drag V1, sıfır hücum açısındaki temel sürükleme katsayısını hesaplar.
- Referans alanı ve maksimum çap normalizasyon/geometri authority'leridir;
  aerodinamik uzunluk ise Reynolds karakteristik uzunluğudur.
- Sürükleme; burun/gövde/fin sürtünmesi, konik burun basıncı, kare fin leading-edge
  basıncı, fin trailing-edge base ve airframe base katkılarını içerir.
- Reynolds sayısı ve Mach kabul edilmiş uçuş koşullarından gelir; yüzey pürüzlülüğü
  burun, gövde ve finler için bağımsız seçilir.
- Doğrusallaştırılmış Static Stability V1, kabul edilmiş Extended-Barrowman routing'i
  ile konik burun ve eşit aralıklı fin setinin CNa ve x_geo basınç merkezini üretir.
- Statik marj; aerodinamik CP, kütlenin sahip olduğu CG ve geometrinin sahip olduğu
  maksimum çaptan ayrı bir hesaplayıcıyla bulunur.
- Basic Drag V1 `0 <= Mach <= 1`, Static Stability V1 ise `0 <= Mach < 0.8`
  aralığını destekler.
- İlk tam demo yörüngesi kabul edilmiş drag alanında kalmıştır; NAT-012 kilometre
  taşı regresyonları kalıcı test paketinin parçasıdır.

## Kapsam Dışı / Planlanan

- Tam OpenRocket aerodinamik paritesi sonraki bir doğrulama kampanyasıdır.
- Sonlu hücum açısı kuvvetleri, aerodinamik momentler ve doğrusal olmayan davranış
  ertelenmiştir.
- Galejs/body-lift yaklaşımı ertelenmiştir.
- Yuvarlatılmış ve airfoil fin basınç iyileştirmeleri ertelenmiştir.
- Genişletilmiş transonik ve süpersonik modeller ertelenmiştir.
- Plume etkileşimi ve gelişmiş interference etkileri ertelenmiştir.
- Bu uzantılar gelecekteki Aerodinamik V2 çalışmasına aittir.
