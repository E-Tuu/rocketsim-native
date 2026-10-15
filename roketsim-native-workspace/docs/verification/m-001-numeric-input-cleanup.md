# M-001 — Java Core numeric giriş sınırının sadeleştirilmesi

Tarih: 2026-10-16. Karar otoritesi: geliştiricinin onayladığı M-001 kapsamı;
`../decisions/ADR-008-typed-numeric-input-boundary.md`.

## Başlangıç ve kapsam

Git kökü `C:/Users/ebrut/OneDrive/Belgeler/ChatGPT/OpenRocket`;
başlangıç dalı `audit/aud-002-parity-integrity`, temiz HEAD
`8409a023eb18c013ab97e662928545ec4fec126b`. J-001, J-002, J-003, J-004 ve
AUD-002 ancestry doğrulandı. Bu tabandan
`maintenance/m-001-numeric-input-cleanup` oluşturuldu; main veya eski dallar
üzerinde çalışılmadı. J-005 başlatılmadı.

Bu, **onaylı API daraltmasıdır**, bir sayısal parite hatasını gizleyen test
değişikliği değildir. Python lexical API eşdeğerliği artık Java migration kabul
kriteri değildir. Python matematik/fizik referansı olmaya devam eder.

## Önceki ve yeni giriş sözleşmesi

| Girdi | AUD-002 | M-001 |
|---|---|---|
| Finite numeric array/List veya mevcut Vector | Kabul | Aynen korunur |
| Number wrapper'ları | `doubleValue()` | Aynen korunur |
| Boolean bileşen | true→1, false→0 | Aynen korunur |
| Numeric string array veya mixed numeric/String List | Python lexical dönüşümüne göre | String bileşen açıkça reddedilir |
| Numeric NaN / ±Infinity | Finite validation reddeder | Aynı validation ve alan bağlamı |
| Yanlış boyut / nested array / scalar | Reddedilir | Aynen korunur |
| Bağımsız kopya ve immutable değer | Korunur | Aynen korunur |

`Vector.of` public imzaları, boyutu açıkça belirleme seçeneği ve boyuttan bağımsız
vektör temsili değişmedi. Sıfır uzunluklu vektör davranışı da değişmedi; ret
kuralı String **bileşenler** içindir. Yeni String hatası
`IllegalArgumentException`, mesajı `<name> must contain numerical values;
String components are not supported` biçimindedir. Unicode, locale, CSV ve
diğer text-to-number işleri frontend/loader/input adapter sorumluluğundadır.
Yeni parser veya fallback eklenmedi; `Double.parseDouble` Core'a taşınmadı.

## Exact dosya değişiklikleri ve silme gerekçeleri

Core köküne göre:

| Dosya | İşlem / gerekçe |
|---|---|
| `src/main/java/org/roketsim/core/math/PythonNumericText.java` | Kaldırıldı; lexical uyumluluk üretim kapsamından çıktı |
| `src/main/java/org/roketsim/core/math/Vector.java` | Yalnız String dalı açık ret; sınıf açıklaması güncellendi |
| `src/test/java/org/roketsim/core/VectorStringInputTest.java` | 25 lexical-contract testi kaldırıldı; yeni onaylı sözleşmeyle çelişiyor |
| `src/test/java/org/roketsim/core/VectorStringInputProbe.java` | Yalnız kaldırılan lexical araçların probe'u; aktif numeric gate çağırmıyor |
| `verification/string_input_parity.py` | Obsolete executable lexical parity; dört numeric/physical runner'dan bağımsız |
| `verification/vector_string_evidence.py` | Obsolete lexical kanıt toplama executable'ı; çıktıları tarihsel olarak saklı |
| `src/test/java/org/roketsim/core/NumericInputContractTest.java` | 23 yeni numeric boundary regression testi |

Repo çapında referans araması, kaldırılan araçların birbirlerinden başka aktif
numeric gate bağımlılığı olmadığını doğruladı. Gradle, consumer veya dört parite
runner'ı bu araçları çağırmıyor. Silinen dosyalar eski Git commitlerinde
korunur; tarihsel bilgiler silinmedi.

## Downstream incelemesi

- `Quaternion.of` ortak Vector sınırını kullanır; normal numeric/quaternion
  girdileri korunur, String bileşenler artık reddedilir.
- `Quaternions` işlemleri typed Quaternion/Vector ve numeric array'ler kullanır;
  çarpım, normalize, frame ve rotation denklemleri değişmedi.
- `ConstantWindModel`, `RelativeFlowCalculator`, `BasicFlightConditionsCalculator`
  aynı Vector sınırını kullanır; numeric sonuçları ve String reddi yeni testte
  doğrulandı. İkinci relative-flow girdisi için de String ret testi vardır.
- Geometry/material/structural-mass modellerinde üretim değişikliği yoktur;
  scalar numeric ve accepted immutable domain nesneleri aynen tüketilir.
- `ParityProbe`, `QuaternionParityProbe`, `EnvironmentParityProbe` protokol
  metnini zaten **test adapter'ında** `double[]`/double'a çeviriyor.
  `VehicleParityProbe` de numeric transport'u test adapter'ında decode eder.
  Bu nedenle probe veya authoritative fixture değişikliği gerekmedi.
- Bağımsız `CoreConsumer` normal numeric nesnelerle foundation, quaternion,
  environment ve structural-mass kontrollerini aynen geçti.

## Test değişikliği ve bütünlük

Eski 25 lexical Java testi çıkarıldı; 23 yeni test eklendi. Eski 229
matematik/fizik Java testi korunur. Net Java sayısı **254 − 25 + 23 = 252**.
Yeni testler finite double/int array'ler, numeric Lists, sekiz Number wrapper
örneği, mevcut Vector, defensive copy, negatif sıfır/subnormal/finite sınırlar,
0–4 boyut, yanlış şekil, NaN/±Inf, açık String ve mixed List reddi,
Boolean davranışı ve ortak downstream sınırlarını kapsar.

Dört parity runner, `parity_contract.py` ve `test_parity_integrity.py`
**değiştirilmedi**. Böylece AUD-002 F3/F4 optimize-mode ret, tam output shape,
error category/payload kontrolü, missing/extra/duplicate field ret, boş/kesilmiş
sonuç ret, subprocess check ve total/distinct effective-input raporlaması
korundu. Toleranslar ve Python kaynaklı fizik beklentileri değiştirilmedi.

## Gerçek doğrulama ortamı ve komutlar

Windows 11 build 26200; Eclipse Temurin java/javac 17.0.20.1+1;
Gradle Wrapper 8.12.1; Python 3.12.4; NumPy 2.5.2.
Core dizininde process-local değişkenler:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:\Users\ebrut\AppData\Local\Temp\roketsim-j001-gradle-cache'
$mPython='..\roketsim-native-engine\.venv\Scripts\python.exe'
$mJava="$env:JAVA_HOME\bin\java.exe"
$mBuild='C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-focused'
```

```powershell
.\gradlew.bat -I verification/isolated-build.init.gradle "-PverificationBuildDir=$mBuild" --project-cache-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-focused-cache' test --tests org.roketsim.core.NumericInputContractTest --tests org.roketsim.core.FoundationTest --offline --no-daemon
& $mPython -B verification/python_parity.py --java $mJava --build-dir $mBuild
& $mPython -B verification/quaternion_parity.py --java $mJava --build-dir $mBuild
& $mPython -B verification/environment_parity.py --java $mJava --build-dir $mBuild
& $mPython -B verification/vehicle_parity.py --java $mJava --build-dir $mBuild --group all
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-final' --project-cache-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" '-PverificationBuildDir=C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-consumer' --project-cache-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-consumer-cache' run '-PcoreJar=C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-final\libs\roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME\bin\jdeps.exe" --print-module-deps 'C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-final/libs/roketsim-core-0.1.0.jar'
```

Engine dizininde `.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q`.
Negatif testler repo kökünde:

```powershell
$env:AUD002_BUILD_DIR='C:\Users\ebrut\AppData\Local\Temp\roketsim-m001-final'
$env:AUD002_JAVA='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
.\roketsim-native-workspace\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest roketsim-native-workspace/roketsim-core/verification/test_parity_integrity.py -p no:cacheprovider -q -s
```

## Gerçek sonuçlar

| Kapı | Sonuç | Çıkış |
|---|---|---|
| Yeni numeric boundary + J-001 focused | 23 + 25 = **48 passed** | 0 |
| J-001/J-002/J-003/J-004 eski Java testleri | **25 / 62 / 70 / 72 passed** | 0 |
| Tam Java Core clean build | **252 passed**, 0 failure/error/skipped | 0 |
| Tam Python regression | **1491 passed**, 19.37 s | 0 |
| J-001 numeric parity | 6 vaka, 6 distinct | 0 |
| J-002 quaternion parity | 95 success + 53 expected error + 7 trig; 155 distinct | 0 |
| J-003 environment parity | 2485 success + 138 expected error; 2623 total / 2622 distinct | 0 |
| J-004 geometry/material/mass parity | 92 success + 288 expected error; 380 total / 374 distinct | 0 |
| J-004 sayısal karşılaştırmaları | **5302/5302 birebir eşit** | 0 |
| Negative harness integrity | **19 passed**, 36.32 s | 0 |
| Bağımsız consumer | Dört consumer grubu PASS | 0 |
| RuntimeClasspath / jdeps | Harici dependency yok / yalnız `java.base` | 0 |
| Yeni JAR'da obsolete lexical sınıflar | `jar tf` ile 0 eşleşme | 0 |

Negatif harness çocukları wrong number/category, extra/missing/duplicate field,
truncation/empty output, empty cases ve dört `python -O` çalıştırmasını
beklenen **çıkış 1** ile reddetti; bu kasıtlı arızalar acceptance failure değildir.

Kabul edilen numeric/physical sonuçlar değişmedi: numeric conversion, validation
ve bütün fizik/aritmetik metotları kaynakta aynı kaldı ve dört parity paketi aynı
sonuçlarla geçti. Bu, bütün olası float girdiler için bitwise eşitlik iddiası
değildir. Önceden bilinen J-003 libm/nested-hypot tanı farkları aynı toleranslar
içinde kaldı; production condition normları 518/518 exact, atmosphere chain
max relative fark 3.8356762446777534e-16. Quaternion trig max farkı
1.1102230246251565e-16. Yeni sayısal/physical uyuşmazlık gözlenmedi.

## Tarihçe, sınırlar ve kabul önerisi

Python production/test ağacı, ADR-007, tarihsel J-001–J-004 ve AUD-002 kayıtları
ile `verification/evidence/` altındaki bütün BEFORE/AFTER/RED kanıtları
baseline'a göre değişmedi. Tarihsel lexical kanıtlar o commitin davranışını
anlatır; artık aktif kabul kapısı değildir. Eksik özgün 15 J-004 başarısızlığının
geçmişi bu görevle açıklanmış sayılmaz.

Mevcut 22 missing-Javadoc uyarısı devam eder; yeni dependency veya fizik modeli
yoktur. Object imzalarının korunması compile-time numeric typing sağlamaz;
String reddi runtime'da açıktır. Boolean davranışının ileride değiştirilmesi
bu kararın kapsamında değildir. String kullanan istemciler adapter'da açıkça
dönüştürülmüş numeric değer göndermelidir.

Kabul önerisi: **M-001 PASS**; yalnız görev dosyalarıyla commit/push yapılabilir.
main veya önceki dalların güncellenmesi ve J-005 çalışması yetkilendirilmedi.
