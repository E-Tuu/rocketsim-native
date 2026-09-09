# Demo Doğrulaması

## Güncel V1 Kapsamı

- NAT-022, gerçek kabul edilmiş production zincirini dikey ve sıfır rüzgârlı
  AeroTech F50-4T fixture'ıyla; mock physics, integrator, event veya engine
  kullanmadan çalıştırır.
- Birincil V&V fixture'ı açık 0.010 s sabit adım ve 5000 adımlık safety guard
  kullanır; bu değerlerin hiçbiri production default'u değildir.
- Event kronolojisi tam olarak `BURNOUT < APOGEE < GROUND` biçimindedir.
- İlk yukarı hareket, powered ascent, mass depletion, post-burn coast, localized
  apogee, ballistic descent ve terminal ground davranışlarının tamamı geçmiştir.
- Özdeş tekrarlı koşular deterministic olmuş; 0.010 s ile 0.005 s sensitivity
  karşılaştırması donmuş NAT-022 kabul politikasının içinde kalmıştır.

| Doğrulanmış büyüklük | Sonuç |
|---|---:|
| Başlangıç kütlesi | 0.6357490028277474 kg |
| F50-4T burnout | 1.430 s |
| Burnout WORLD z | 88.15789842420412 m |
| Burnout düşey hızı | 87.92821395634027 m/s |
| Apogee zamanı | 7.012303670973791 s |
| Apogee fırlatmaya göre yüksekliği | 279.5335684908227 m |
| Ground zamanı | 15.832349589466348 s |
| Ground tahminî düşey hızı | -49.190189996430696 m/s |
| Maksimum Mach | 0.2767317427 |
| Maksimum dinamik basınç | 4663.1955666 Pa |
| Maksimum hız | 92.8134640 m/s |
| Tam depo denetimi | 1431 başarılı, 0 başarısız, 0 atlanan |

Ayrıntılı fixture lineage'i, event-state semantiği ve adım duyarlılığı değerleri
[arşivlenmiş NAT-022 kaydında](archive/nat/nat-022-end-to-end-native-trajectory.md)
korunmaktadır.

**FIRST NATIVE 3DOF DEMO BASELINE: VERIFIED**

## Kapsam Dışı / Planlanan

- Liftoff, launch-guide ve rail-clear doğrulaması ertelenmiştir.
- Recovery ve deployment doğrulaması ertelenmiştir.
- 6DOF yörünge doğrulaması ertelenmiştir.
- Native temel, OpenRocket paritesi iddiasında bulunmaz; bu kampanya ertelenmiştir.
