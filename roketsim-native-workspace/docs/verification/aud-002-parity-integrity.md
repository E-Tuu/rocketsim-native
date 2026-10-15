# AUD-002 — Parite bütünlüğü: BEFORE kanıtı ve kontrollü duruş

Tarih: 2026-10-16 (Europe/Istanbul). Durum: REVIEW REQUIRED — NOT COMMITTED.

## Başlangıç

- Git kökü: `C:/Users/ebrut/OneDrive/Belgeler/ChatGPT/OpenRocket`.
- Başlangıç dalı: `migration/j-004-geometry-materials-mass`.
- Başlangıç HEAD: `307731f7356a9a4e1c37f3682592f95f20d8e99a`; çalışma ağacı temizdi.
- J-001/J-002/J-003/J-004 ancestry kontrolleri çıkış 0 ile doğrulandı.
- Oluşturulan dal: `audit/aud-002-parity-integrity`.
- Java/javac: Eclipse Temurin 17.0.20.1+1; Gradle Wrapper 8.12.1.
- Python 3.12.4 / NumPy 2.5.2; Windows 11 build 26200.

## Yetkili bulgular ve sınır

F1: `Vector.of` içindeki doğrudan `Double.parseDouble`, Python `as_vector` / NumPy dönüşümüyle aynı metin dilbilgisini kullanmıyor.
F3: parity runner `assert` kontrolleri optimize Python modunda kaldırılabiliyor.
F4: çıktı şekli, yinelenen alanlar ve toplam/benzersiz vaka raporlama eksikleri.

Bu kayıtta yalnız Phase A yürütüldü. Üretim düzeltmesi, F3/F4 düzeltmesi veya tolerans değişikliği yapılmadı. Yeni regresyonlar PASS elde etmek için değiştirilmedi.

## Test-first: özgün üç F1 regresyonu

Üretim kaynağı değiştirilmeden `VectorStringInputTest` eklendi.
Core dizininde, process-local JAVA_HOME ve GRADLE_USER_HOME ile:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME=Join-Path $env:TEMP 'roketsim-j001-gradle-cache'
.\gradlew.bat -I verification/isolated-build.init.gradle "-PverificationBuildDir=$env:TEMP\roketsim-aud002-before-build" --project-cache-dir "$env:TEMP\roketsim-aud002-before-cache" test --tests org.roketsim.core.VectorStringInputTest --offline --no-daemon
```

Gerçek sonuç: çıkış 1; 3 test çalıştı, 3 test başarısız oldu. `pythonDigitSeparator` gerçek IllegalArgumentException/NumberFormatException aldı; `1f` ve `0x1.0p0` ret testleri, Java girişleri kabul ettiği için AssertionFailedError aldı. Bu beklenen RED evresidir. Rapor: `C:/Users/ebrut/AppData/Local/Temp/roketsim-aud002-before-build/reports/tests/test/index.html`.

## Doğrudan Python–Java BEFORE karşılaştırması

Core dizinindeki gerçek komut:

```powershell
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/vector_string_evidence.py --java 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe' --build-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-before-build'
```

Çıkış 0, kanıt toplama işleminin tamamlandığı anlamındadır; parite PASS anlamına gelmez. Araç karşılaştırma assertion'ı içermez ve kendisini bir kabul kapısı olarak sunmaz. Java alt süreci çıkış 0 ile tamamlandı; beklenen domain hataları ayrı kayıtlar olarak yakalandı. Probe, UTF-8/Base64 ile taşınan metni değiştirmeden gerçek `Vector.of(new String[]{text})` metoduna verir. Python beklentisi gerçek `as_vector([text])` çağrısıdır. Aşağıdaki çıktıda özgün değerler ve tam hata mesajları bulunur; sonuçlar yuvarlanmadı.

```jsonl
{"python": "3.12.4 (tags/v3.12.4:8e8a4ba, Jun  6 2024, 19:30:16) [MSC v.1940 64 bit (AMD64)]", "numpy": "2.5.2", "platform": "Windows-11-10.0.26200-SP0", "command": ["C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot\\bin\\java.exe", "-cp", "C:\\Users\\ebrut\\AppData\\Local\\Temp\\roketsim-aud002-before-build\\classes\\java\\main;C:\\Users\\ebrut\\AppData\\Local\\Temp\\roketsim-aud002-before-build\\classes\\java\\test", "org.roketsim.core.VectorStringInputProbe", "MV8w", "MWY=", "MHgxLjBwMA==", "MQ==", "LTAuMA==", "LjU=", "MS4=", "MWUtMw==", "MV8yLjNfNGUrMF8y", "XzE=", "MV8=", "MV9fMA==", "MWVfMg==", "MWQ=", "MHgxMA==", "IDEuNSAJCg==", "", "IA==", "TmFO", "SW5maW5pdHk=", "LUluZmluaXR5", "MWUzMDk=", "MWUtNDAw", "NC45NDA2NTY0NTg0MTI0NjU0ZS0zMjQ=", "MS43OTc2OTMxMzQ4NjIzMTU3ZTMwOA==", "aW5m", "K0lORg==", "bmFu", "LU5hTg==", "MUY=", "MUQ=", "2aFf2aIu2aM=", "77yR77ySLu+8lQ==", "wqAxLjXCoA==", "woUxLjXChQ==", "HDEuNRw=", "ADEuNQA=", "4oCDMS414oCD", "McKgMg=="], "java_exit_code": 0}
{"input": "1_0", "python": {"status": "OK", "value": 10.0}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "1f", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "OK", "value": 1.0}}
{"input": "0x1.0p0", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "OK", "value": 1.0}}
{"input": "1", "python": {"status": "OK", "value": 1.0}, "java": {"status": "OK", "value": 1.0}}
{"input": "-0.0", "python": {"status": "OK", "value": -0.0}, "java": {"status": "OK", "value": -0.0}}
{"input": ".5", "python": {"status": "OK", "value": 0.5}, "java": {"status": "OK", "value": 0.5}}
{"input": "1.", "python": {"status": "OK", "value": 1.0}, "java": {"status": "OK", "value": 1.0}}
{"input": "1e-3", "python": {"status": "OK", "value": 0.001}, "java": {"status": "OK", "value": 0.001}}
{"input": "1_2.3_4e+0_2", "python": {"status": "OK", "value": 1234.0}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "_1", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "1_", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "1__0", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "1e_2", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "1d", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "OK", "value": 1.0}}
{"input": "0x10", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": " 1.5 \t\n", "python": {"status": "OK", "value": 1.5}, "java": {"status": "OK", "value": 1.5}}
{"input": "", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": " ", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "NaN", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got nan"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector[0] must be finite; got NaN"}}
{"input": "Infinity", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got inf"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector[0] must be finite; got Infinity"}}
{"input": "-Infinity", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got -inf"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector[0] must be finite; got -Infinity"}}
{"input": "1e309", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got inf"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector[0] must be finite; got Infinity"}}
{"input": "1e-400", "python": {"status": "OK", "value": 0.0}, "java": {"status": "OK", "value": 0.0}}
{"input": "4.9406564584124654e-324", "python": {"status": "OK", "value": 5e-324}, "java": {"status": "OK", "value": 5e-324}}
{"input": "1.7976931348623157e308", "python": {"status": "OK", "value": 1.7976931348623157e+308}, "java": {"status": "OK", "value": 1.7976931348623157e+308}}
{"input": "inf", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got inf"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "+INF", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got inf"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "nan", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got nan"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "-NaN", "python": {"status": "ERROR", "category": "ValueError", "message": "vector[0] must be finite; got nan"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector[0] must be finite; got NaN"}}
{"input": "1F", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "OK", "value": 1.0}}
{"input": "1D", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "OK", "value": 1.0}}
{"input": "\u0661_\u0662.\u0663", "python": {"status": "OK", "value": 12.3}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "\uff11\uff12.\uff15", "python": {"status": "OK", "value": 12.5}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "\u00a01.5\u00a0", "python": {"status": "OK", "value": 1.5}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "\u00851.5\u0085", "python": {"status": "OK", "value": 1.5}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "\u001c1.5\u001c", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "OK", "value": 1.5}}
{"input": "\u00001.5\u0000", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "OK", "value": 1.5}}
{"input": "\u20031.5\u2003", "python": {"status": "OK", "value": 1.5}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
{"input": "1\u00a02", "python": {"status": "ERROR", "category": "ValueError", "message": "vector must contain numerical values"}, "java": {"status": "ERROR", "category": "IllegalArgumentException", "message": "vector must contain numerical values"}}
```

## Yeni gözlemler ve durma nedeni

Üç özgün F1 farkı yeniden üretildi. Alt çizgili bilimsel gösterim ve Java-only `d/F/D` sonekleri aynı dönüşüm sorununun yakın örnekleridir.

Zorunlu yakın-girdi araştırmasında ek uyumsuzluklar ortaya çıktı:

- `\u00001.5\u0000` ve `\u001c1.5\u001c`: Python ValueError üretirken Java 1.5 kabul ediyor.
- `\u0661_\u0662.\u0663` ve `\uff11\uff12.\uff15`: Python sırasıyla 12.3 ve 12.5 kabul ederken Java reddediyor.
- NBSP, NEL ve EM SPACE ile çevrili 1.5: Python kabul ederken Java reddediyor.
- `inf`, `+INF`, `nan`: iki dil de reddediyor fakat Python finite-validation aşamasına ulaşırken Java numerical-conversion aşamasında reddediyor.

Kök neden hipotezi: `Double.parseDouble` yalnız decimal/underscore farkı değil, Java metin trim davranışı ve Unicode sayı/boşluk dilbilgisi bakımından da NumPy/Python dönüşümünden ayrılıyor. Java/Python çıktıları yukarıda korunmuştur. Unicode veya whitespace kabul kuralları varsayılarak yeni parser yazılmadı.

Görevdeki “For any additional or unexpected discrepancy: STOP and report it without fixing it” kuralı uygulanarak burada duruldu. Önerilen sonraki işlem: bu ek vakaların F1 düzeltme kapsamına açıkça dahil edilmesi ve Python kaynak davranışına dayalı, Unicode/control-character sınırları da test edilen dönüşüm tasarımının onaylanması. Accepted giriş kümesi sessizce daraltılmamalıdır.

## Yürütülmeyen kapılar

Üretim düzeltmesi ve AFTER karşılaştırması; J-002/J-003 paylaşılan giriş kontrolleri; F3/F4 hardening; negatif harness testleri; tam Java/Python regresyonları; dört parite paketi; bağımsız consumer ve jdeps bu çalışmada yürütülmedi. Önceki AUD-001 sonuçları yeni AUD-002 doğrulaması gibi sayılmadı. Derleme yalnız yeni üç RED testini çalıştırmak için yapıldı; tam kabul build'i değildir.

## Mevcut doğruluk ve tarihsel izlenebilirlik

Mevcut F1 sorunu doğrulanmış ve düzeltilmemiştir. Tarihsel ilk 15 J-004 başarısızlığın kimlikleri hâlâ açıklanmış değildir; bu yeni gözlemler onlara atanmaz ve eksik geçmiş yeniden oluşturulmuş sayılmaz. Tarihsel J-001–J-004 kayıtları değiştirilmedi.

## Dosya ve bağımlılık politikası

Yalnız yeni AUD-002 Java test/probe, Python kanıt aracı ve bu kayıt eklendi. Java üretim kaynakları, Python üretim kaynakları/referans testleri, fizik denklemleri, sayısal toleranslar ve bağımlılıklar değiştirilmedi. Commit/push yapılmadı; J-005 başlatılmadı.

## Yürütme ortamı notları

İlk evidence komutunda sandbox TEMP farklı bir konuma çözümlendiği için classpath bulunamadı (çıkış 1); yeniden çağrıda derlemenin gerçek mutlak TEMP yolu kullanıldı. Ayrı bir konsol araştırmasında Unicode metni cp1254 ile yazdırmak UnicodeEncodeError verdi (çıkış 1); `ascii()` ile aynı Python girdileri kayıpsız tekrar gözlendi. Bunlar parite PASS olarak değerlendirilmedi; kalıcı ortam ayarı değiştirilmedi.

---

## 2026-10-16 — Onaylanmış genişletilmiş F1 düzeltmesi ve tamamlanan doğrulama

Yukarıdaki BEFORE kaydı tarihsel olarak aynen korunmuştur; önceki duruşu anlatır.
Geliştirici Unicode rakam/boşluk, kontrol karakterleri ve özel sayı varyantlarını
aynı F1 düzeltmesinin kapsamına açıkça dahil ederek devamı onayladı. Bu bölüm
önceki “yürütülmedi” durumlarını günceller; geçmiş kayıtları yeniden yazmaz.

### Başlangıç ve araçlar

Devam başlangıcı: `audit/aud-002-parity-integrity`, HEAD
`307731f7356a9a4e1c37f3682592f95f20d8e99a`; yalnız önceden onaylı dört
untracked AUD-002 dosyası vardı. Yeni dal oluşturulmadı. Python kaynağı/testleri,
tarihsel ADR ve J-001–J-004 doğrulama kayıtları değiştirilmedi.

Java/javac: Temurin 17.0.20.1+1; Gradle Wrapper: 8.12.1;
Python: 3.12.4; NumPy: 2.5.2; Windows 11 build 26200.
Doğrudan ölçülen Python Unicode veritabanı **15.0.0**'dır.

### Genişletilmiş RED kanıtı

Üretim düzeltmesinden önce aynı Gradle komutu `roketsim-aud002-red2`
build ve `roketsim-aud002-red2-cache` cache dizinleriyle çalıştırıldı.
`VectorStringInputTest`: **24 test, 13 failed**, Gradle çıkış **1**.
Özgün üç regresyon korunmuştur; test kimlikleri ve hata mesajları
`roketsim-core/verification/evidence/aud002-red-tests.json` içinde saklanır.

`string_input_parity.py --evidence-only` ile gerçek Python ve henüz
düzeltilmemiş Java arasında **965 farklı girdi, 775 uyumsuzluk** kaydedildi.
Evidence-only komutu çıkış 0 üretir; bu bilerek yalnız kayıt toplama modudur,
parite kabulü değildir. Normal mod her uyumsuzlukta çıkış 1 üretir.

Tam BEFORE/AFTER kayıtları:

- `roketsim-core/verification/evidence/aud002-string-before.jsonl`
- `roketsim-core/verification/evidence/aud002-string-after.jsonl`

Her satır tam girdiyi, gerçek Python sonucunu, Java sonucunu, hata ailesi ve
aşamasını, finite değerlerde round-trip değeri ve `float.hex()` gösterimini
korur. JSON metinlerinde kontrol karakterleri kaçırılır; bu girdi değişikliği
değildir. Son satır ortam ve toplam/benzersiz vaka sayısını verir.
İki kayıt bağımsız olarak karşılaştırıldı: **965 girdinin ve Python
beklentisinin tamamı değişmemiştir**; AFTER satırlarının tamamı `match=true`.

### F1 kök neden ve üretim değişikliği

`Vector.of` String dalında yalnız doğrudan `Double.parseDouble(text)` çağrısı,
package-private `PythonNumericText.parse(text)` çağrısıyla değiştirildi.
Numeric/Boolean/array/List/shape/finite/immutability ve aritmetik dalları
değiştirilmedi. Yeni helper public API veya global tolerance değildir.

Helper'ın dilbilgisi:

1. Python referansından enumerate edilen Unicode 15.0 **68 decimal-zero
   bloğundaki 680 Nd rakam** ASCII ondalık karşılıklarına eşlenir.
2. Referans `as_vector` çağrılarının kabul ettiği **25 whitespace codepoint**
   açık tabloyla ASCII space'e eşlenir. Yalnız baştaki/sondaki bu boşluklar
   çıkarılır; iç boşluklar ve NUL/izin verilmeyen kontroller korunur ve reddedilir.
3. `digitpart := digit (['_'] digit)*`; decimal mantissa `digitpart`,
   `digitpart '.' [digitpart]` veya `'.' digitpart` olabilir. İsteğe bağlı
   ASCII işaret ve `[eE] [sign] digitpart` üssü desteklenir.
4. ASCII case-insensitive, isteğe bağlı işaretli `inf`, `infinity`, `nan`
   dönüştürülür; ardından mevcut Vector finite validation bunları reddeder.
5. Gramer doğrulandıktan sonra separator çıkarılır ve Java binary64 dönüşümü
   kullanılır. Hex ve Java f/F/d/D sonekleri kabul edilmez.

Unicode tabloları JDK Character/strip/trim varsayımlarından alınmadı:
Python `unicodedata.category/decimal` çıktısı ve gerçek `as_vector` çevresel
boşluk çağrılarıyla üretildi. Sabit hedef Unicode tablosu, JDK sürümüne bağlı
sessiz kabul-kümesi değişikliklerini önler. Bu fizik sabiti değildir.

### Kabul/ret matrisi

| Kategori | Gerçek Python referansı | AFTER Java |
|---|---|---|
| Decimal, işaret, noktalı/scientific gösterim | finite binary64 kabul | Değer ve hex eşit |
| Geçerli digit separator: `1_0`, `1_2.3_4e+0_2` | 10 / 1234 | Eşit |
| `_1`, `1_`, `1__0`, `1e_2` | conversion ValueError | Eşdeğer IllegalArgumentException/aşama |
| Java-only f/F/d/D ve hex floating syntax | conversion ValueError | Eşdeğer ret |
| Unicode Nd rakamları; Arabic/fullwidth ve supplementary-plane | kabul | 680 codepoint örneğinde eşit |
| Kabul edilen çevresel Unicode/ASCII whitespace | kabul | Eşit |
| NUL, U+001C–U+001F, zero-width space/BOM, iç boşluk | conversion ret | Eşdeğer ret |
| `inf`, `+INF`, `nan`, `-NaN`, Infinity varyantları | dönüşür, finite validation reddeder | Aynı aşama ve alan bağlamı |
| `-0.0` | negatif sıfır | İşaret biti korunur |
| `1e309` | infinity dönüşür, finite ret | Aynı aşama |
| `1e-400` | sıfıra underflow, kabul | Aynı değer |
| En küçük subnormal / en büyük finite test metni | finite kabul | Birebir eşit |
| Boş veya yalnız boşluk | conversion ret | Eşdeğer ret |

965 kayıt: **788 finite değer, 162 conversion hatası, 15 finite-validation
hatası**. Java/Python genel exception adları dil eşlemesi gereği farklıdır;
`ValueError` ↔ `IllegalArgumentException` korunur. Finite hata mesajının sayı
yazımı (`nan`/`NaN`, `inf`/`Infinity`) byte eşitliği iddia edilmez; ilgili alan,
doğrulama aşaması ve mesajın `must be finite` bağlamı eşleşir.

`Quaternion.of`, Quaternion norm, `ConstantWindModel`, `RelativeFlowCalculator`
ve `BasicFlightConditionsCalculator` için geçerli string girişleri ile invalid
ve nonfinite string reddi yeni Java testinde kontrol edildi. Normal numeric
girdiler bütün eski J-001–J-004 testleri ve pariteleriyle yeniden doğrulandı.

### F3/F4 düzeltmeleri

- Dört tarihsel runner, `parity_contract` import aşamasında optimize Python'ı
  guaranteed nonzero çıkışla reddeder. Mevcut sayısal assertion'lar korunur.
- J-001 tam çıktı boyutu kontrol edilir; özellikle 2D/4D fazladan alanlar reddedilir.
- J-002 tek satır, status, hata payload uzunluğu, norm dahil tam değer şekli ve
  trig çıktısının dört elemanı doğrulanır.
- J-003 mevcut satır/değer sayısına ek olarak her error payload'ın tam şekli
  ve toplam tamamlanan vaka sayısı doğrulanır.
- J-004 yinelenen anahtarlar dict oluşturulmadan reddedilir; eksik/beklenmeyen
  alanlar, hata payload şekli ve toplam tamamlanan vaka sayısı doğrulanır.
- Boş vaka koleksiyonu kabul edilmez. Java subprocess çıkış kontrolleri korunur.
- Toplam ve distinct effective inputs ayrı raporlanır. J-004'te default
  varyantlar fizik hesaplaması çağrılmadan dict üzerinde uygulanarak eşdeğer
  effective inputlar sayılır. Bu istatistik validasyon/hesaplama yerine geçmez.
- J-001–J-003'e mevcut default build yolunu koruyan isteğe bağlı `--build-dir`
  eklendi; bu, TEMP derlemelerini classpath monkeypatch olmadan doğrular.

### Negatif harness doğrulaması

`verification/test_parity_integrity.py`: **19 passed**, çıkış **0**.
İzole child process içinde yalnız Java probe çıktıları bozulur; gerçek Python
reference hesapları, üretim sonuçları, fixture beklentileri ve toleranslar
değiştirilmez. Her negatif child için çıkış **1** ve ilgili diagnostic doğrulandı:

| Hata enjeksiyonu | Runner / kontrol |
|---|---|
| Yanlış sayı | J-001 |
| 2D çıktıda fazladan alan / kesilmiş çıktı | J-001 |
| Norm çıktısında fazla değer / yanlış status / fazla hata alanı | J-002 |
| Yanlış hata kategorisi / eksik değer alanı | J-003 |
| Yanlış hata kategorisi | J-004 |
| Eksik, beklenmeyen veya yinelenen alan | J-004 |
| Kesilmiş satır kümesi / boş çıktı | J-004 |
| Boş/unexecuted vaka kümesi | Ortak count/validation kapısı |
| `python -O` | Dört runner'ın her biri |

İlk negatif test aracı denemesi **18 passed / 1 failed** verdi: fault injector
Java dışındaki NumPy/platform subprocess'lerini ve check_output/run çağrılarını
da saydığı için hedef üçüncü Java çıktısını bozmadı. Assertion gevşetilmedi.
Injector yalnız `org.roketsim.core.*` Java probe çağrılarını bir kez bozacak
şekilde daraltıldı; aynı 19 negatif test ardından geçti. Bu bir üretim/parite
uyumsuzluğu değil, yeni izole fault injection aracının doğrulanmasıdır.

### Çalıştırılan komutlar

Core dizininde ortak PowerShell değişkenleri:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:\Users\ebrut\AppData\Local\Temp\roketsim-j001-gradle-cache'
$audPython='..\roketsim-native-engine\.venv\Scripts\python.exe'
$audJava="$env:JAVA_HOME\bin\java.exe"
$audBuild='C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-final'
```

```powershell
.\gradlew.bat -I verification/isolated-build.init.gradle "-PverificationBuildDir=$audBuild" --project-cache-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
& $audPython -B verification/python_parity.py --java $audJava --build-dir $audBuild
& $audPython -B verification/quaternion_parity.py --java $audJava --build-dir $audBuild
& $audPython -B verification/environment_parity.py --java $audJava --build-dir $audBuild
& $audPython -B verification/vehicle_parity.py --java $audJava --build-dir $audBuild --group all
```

String evidence komutu, BEFORE için `roketsim-aud002-red2` ve
`--evidence-only`, AFTER için `roketsim-aud002-green` ve normal modla çalıştırıldı:

```powershell
& $audPython -B verification/string_input_parity.py --java $audJava --build-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-green'
```

Negatif testler repo kökünde:

```powershell
$env:AUD002_BUILD_DIR='C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-green'
$env:AUD002_JAVA='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
.\roketsim-native-workspace\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest roketsim-native-workspace/roketsim-core/verification/test_parity_integrity.py -p no:cacheprovider -q -s
```

Engine dizininde `.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q`.
Bağımsız consumer, Core dizininden:

```powershell
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" '-PverificationBuildDir=C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-consumer' --project-cache-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-consumer-cache' run '-PcoreJar=C:\Users\ebrut\AppData\Local\Temp\roketsim-aud002-final\libs\roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME\bin\jdeps.exe" --print-module-deps "$audBuild\libs\roketsim-core-0.1.0.jar"
```

### Sonuçlar

| Kapı | Gerçek sonuç | Çıkış |
|---|---|---|
| Genişletilmiş RED | 24 test, 13 failed; eski üretim kodu | 1, beklenen |
| String GREEN | Aynı 24 test geçti | 0 |
| Java J-001/J-002/J-003/J-004 | 25 / 62 / 70 / 72 eski test geçti | 0 |
| Tam Java Core | **253 tests; 0 failed/error/skipped** | 0 |
| Temiz Java 17 Gradle build | Başarılı, mevcut 22 missing-Javadoc uyarısı | 0 |
| Tam Python reference regression | **1491 passed**, 25.90 s | 0 |
| String paritesi | 965 / 965 distinct; BEFORE 775 fark → AFTER 0 fark | 0 |
| J-001 sayı/vektör paritesi | 6 / 6 distinct | 0 |
| J-002 quaternion paritesi | 95 success + 53 expected error + 7 trig; 155 / 155 distinct | 0 |
| J-003 environment paritesi | 2485 success + 138 expected error; 2623 / 2622 distinct | 0 |
| J-004 vehicle paritesi | 92 success + 288 expected error; 380 / 374 distinct | 0 |
| J-004 sayısal çıktılar | **5302 / 5302 birebir eşit** | 0 |
| Negatif harness kapıları | **19 passed**; her faulty child çıkış 1 | 0 |
| Bağımsız consumer | Foundation/quaternion/environment/structural PASS | 0 |
| Runtime dependency / jdeps | Harici dependency yok; yalnız `java.base` | 0 |

J-003'te mevcut 10 nonexact vaka aynı kaynak toleransları içinde kaldı:
iki atmosfer zinciri, nested-hypot tanısı ve Math/StrictMath tanıları.
Production conditions normları **518/518 birebir eşit**. Atmosfer zincirinde
en büyük göreli fark 3.8356762446777534e-16; quaternion trig farkı en çok
1.1102230246251565e-16. Bu eski farklar için formül/tolerans değiştirilmedi.

### Sınırlar, koruma ve kabul

Metin paritesi belirlenen Python/NumPy/Unicode sürümüne ve kayıtlı 965
temsilci/boundary girdiye dayanır; sınırsız uzunluktaki bütün olası metinler
ve bütün gelecekteki Unicode/NumPy sürümleri için evrensel kanıt iddiası yoktur.
Unicode Nd codepoint kümesinin tamamı ve Python whitespace kümesi tek tek
incelendi. Yeni Unicode sürümü için tablo değişikliği ayrı doğrulama gerektirir.

Yeni fizik, tolerans, public API veya runtime dependency eklenmedi.
Python üretim/referans testleri ve tarihsel J kayıtları unchanged.
CURRENT CORRECTNESS: onaylı F1/F3/F4 kapsamındaki kapılar geçti.
HISTORICAL TRACEABILITY: özgün 15 J-004 başarısızlığın geçmişi hâlâ açıklanmış
değildir; bu düzeltme o eksik kanıtı kapatmaz. J-005 başlatılmadı.

### Son inceleme eki — uzun geçerli metin ve nihai tekrar

Son sınır incelemesinde `as_vector(['0_' * 2000 + '1'])` doğrudan Python'da
`[1.0]` verdi (çıkış 0). Bu beklentiyle **üretim değişikliğinden önce** eklenen
`VectorStringInputTest.longDigitSeparators` testi, ilk regex tabanlı helper'da
`StackOverflowError` ile başarısız oldu (1 test / 1 failed, Gradle çıkış 1).
Bu, onaylı aynı F1 string lexical kapsamına girer; Python beklentisi değişmedi.

Decimal regex yerine yukarıda tanımlanan **aynı grameri iteratif okuyan**
`decimal/sign/digits/digit` private metotları kullanıldı. Geriye kalan special
number regex'i yalnız bounded `inf/infinity/nan` kelimelerini eşler; decimal
tekrar grameri içermez. Bu değişiklik uzun geçerli digitpart için regex yığın
bağımlılığını kaldırır; yeni tolerans veya kabul/ret politikası getirmez.
Özgün input, gerçek Python sonucu, her iki Java komutu, çıkış kodları ve
ilk sekiz StackOverflow stderr satırı
`verification/evidence/aud002-long-boundary.json` dosyasında korunur.
Önceki regex derlemesi bu probe'da çıkış 1, son iteratif derleme çıkış 0 ve
`OK\t1.0` verdi. Böylece bu ek vakanın BEFORE/AFTER kanıtı da saklanmıştır.

**Nihai tekrar**: yukarıdaki clean build ve dört runner komutu `final` yerine
`roketsim-aud002-final2` ve `roketsim-aud002-final2-cache` yollarıyla yeniden
çalıştırıldı. Tam Java Core **254 test, 0 failure/error/skipped**; string sınıfı
**25 passed** (önceki 24 + uzun sınır testi). 965-girdi string paritesi yeni
JAR'ın class çıktılarında yine 0 fark verdi; önceki BEFORE/AFTER JSONL kayıtları
değiştirilmedi. Nihai sayım **965 kayıtlı lexical vaka + 1 uzun sınır vakası**dır.

Dört parite paketi aynı 6 / 95+53 / 2485+138 / 92+288 sonuçları ve aynı mevcut
son-bit farklarıyla tekrar geçti; J-004 yine 5302 birebir eşit çıktı. Negatif
harness son `final2` çıktıları üzerinde **19 passed**, 35.81 s, çıkış 0;
her kasıtlı hatalı child yine çıkış 1. Bağımsız consumer komutu da `consumer2`,
`consumer2-cache` ve `final2` JAR yollarıyla tekrar geçti (çıkış 0).
Son `jdeps` çıktısı yalnız `java.base`; son runtimeClasspath boş.
Önceki tabloda 253 sayısı ilk GREEN derlemesini anlatır; nihai sayı **254**'tür.

Final kabul önerisi: onaylı AUD-002 F1/F3/F4 düzeltmeleri PASS. Yayımlama
öncesi dosya/sır kontrolü, yalnız bu görev dosyalarının staging'i ve yerel commit
sonrası dal/çalışma ağacı kontrolü ayrıca yapılmalıdır. Bu öneri tarihsel
15 başarısızlığın kök nedenini açıklamaz ve J-005 onayı değildir.
