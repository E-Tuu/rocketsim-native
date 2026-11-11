# ADR-008 — Java Core typed numeric giriş sınırı

Tarih: 2026-10-16. Durum: Kabul edildi; geliştiricinin M-001 kararı.

## Bağlam

Java Core gelecekte bağımsız fizik otoritesidir. Python, matematiksel ve fiziksel
hesaplamalar için kontrollü referans olarak korunur. AUD-002 sırasında Python/
NumPy string dönüşümü uyumu uygulanmış ve doğrulanmıştır; bu tarihsel sonuçlar
geçerlidir, ancak gelecekteki Core giriş sözleşmesini belirlemez.

## Karar

Core, String bileşenleri sayıya dönüştürmez; `Vector.of` bunları açıkça
`IllegalArgumentException` ile reddeder. Unicode/locale/CSV ve diğer metinsel
biçimlerin ayrıştırılması frontend, configuration loader veya ayrı adapter'ın
sorumluluğudur. Bu görev yeni bir parser veya adapter uygulamaz.

Mevcut public imzalar korunur. Numeric array/List, mevcut Vector, Number
wrapper'ları, mevcut Boolean→1/0 davranışı, shape ve finite validation ile
defensive-copy/immutability değişmez. Vector boyutu yalnız 3D ile sınırlandırılmaz.

Bu **onaylı, kasıtlı API-contract narrowing** işlemidir; Python–Java sayısal
parite hatası değildir. Python lexical API eşdeğerliği migration kabul kapsamından
çıkarılmıştır. Matematik/fizik denklemleri, toleranslar ve hata doğrulamaları
değişmez. ADR-007'nin bağımsız Core/migration kararları korunur; tarihsel ADR'ler
ve doğrulama kayıtları yeniden yazılmaz.

## Sonuçlar ve doğrulama

`PythonNumericText` ve obsolete executable lexical test araçları kaldırılır.
AUD-002 BEFORE/AFTER kanıtları ve Git geçmişi saklanır. Sayısal sözleşme testleri,
dört numeric/physical parity paketi ve AUD-002 F3/F4 negatif integrity kapıları
aktif kalır. String kullanan istemciler Core'u çağırmadan önce typed numeric
değer üretmelidir; sessiz fallback veya yeni global numerical policy yoktur.

Gerçek yürütme sonuçları: `../verification/m-001-numeric-input-cleanup.md`.
