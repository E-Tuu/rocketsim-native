# Dinamik ve Sayısal Yöntemler

## Güncel V1 Kapsamı

- Değiştirilemez 3DOF öteleme durumu yalnız WORLD konumu ve hızını içerir.
- Fırlatma koşulları başlangıç WORLD konumunu, hızını ve doğrulanmış sabit fırlatma
  yönü birim vektörünü açıkça sağlar.
- Noktasal kütle dinamiği, anlık toplam kütleden ivmeyi hesaplamadan önce itkiyi,
  temel Cd0 sürüklemesini ve yerçekimini toplar.
- V1'de itki sabit WORLD fırlatma yönünü izler; sürükleme kabul edilmiş bağıl akışa
  zıt yöndedir.
- Sayısal temel; açık pozitif sabit-adım yapılandırmasına, `(t, y)` integrasyon
  noktalarına ve değiştirilemez durum cebirine sahiptir.
- Klasik açık RK4 tam dört stage değerlendirir ve her stage'de sağlanan callable
  üzerinden türevi yeniden hesaplar.
- k4 trial state kabul edilmiş endpoint değildir; endpoint physics simülasyon
  katmanı tarafından ayrıca değerlendirilir.
- Timestep her zaman açıktır; production default'u, clipping'i veya adaptive
  ayarlaması yoktur.
- Sabit türev, sabit ivme, osilatör, stage-spy ve tam Native yörünge V&V'si güncel
  sayısal kapsamı doğrular.

## Kapsam Dışı / Planlanan

- Dönme dinamiği, attitude/quaternion ilerletmesi ve tam 6DOF ertelenmiştir.
- Aerodinamik momentler ve inertia coupling ertelenmiştir.
- Adaptive timestep'ler ve gömülü hata kestiricileri ertelenmiştir.
- Dense output ile olay güdümlü adım bölme veya yeniden integrasyon ertelenmiştir.
- İlave gelişmiş sayısal integrasyon yöntemleri ertelenmiştir.
