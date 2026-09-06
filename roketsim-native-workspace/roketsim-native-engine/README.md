# RoketSim Native Engine

RoketSim Native, bağımsız ve test edilebilir bir fizik motorudur. Ana RoketSim uygulamasının OpenRocket/Java tabanından, kullanıcı arayüzünden ve OpenRocket sınıflarından bağımsız geliştirilir.

Physics core iç birimleri yalnız SI'dır. OpenRocket Fizik ve Simülasyon Motoru Teknik Spesifikasyonu, fizik davranışı ve convention'lar için ana teknik kaynaktır. Eksik fizik bilgileri uygulama sırasında tahmin edilmeyecek; `TODO` veya `OPEN-PARITY` olarak izlenecektir.

Runtime physics path içinde AI/LLM/API çağrısı kullanılmaz. Normal fizik yürütmesi ağ bağlantısı gerektirmez. İleride Java uygulamasıyla motor-neutral ve sürümlenmiş bir JSON sözleşmesi üzerinden bağlantı kurulacaktır.

İlk roadmap:

Foundation → Environment → Propulsion/Mass → Aero → 2DOF → 3DOF → 6DOF

Henüz fizik implementasyonu başlamamıştır. Bu aşama yalnızca güvenli proje temeli, izlenebilir karar kayıtları ve sonraki geliştirme adımları için boş modül sınırlarını içerir.

