# J-005 — Java Propulsion ve Runtime Mass Migration

## Kapsam ve kaynak otoritesi

Doğrulama tarihi: 2026-10-16. Temiz başlangıç dalı
`maintenance/m-001-numeric-input-cleanup`, HEAD
`74ac472f5659338a0d6a343c045e2e4da63206dc` idi. Bu commit'ten
`migration/j-005-propulsion-runtime-mass` açıldı. J-001, J-002, J-003,
J-004 ve AUD-002 commit'lerinin ancestry kontrolleri geçti.

Otorite, `roketsim-native-engine/src/roketsim_native/` altındaki kabul edilmiş
Python uygulaması ve testleridir. Python üretim kodu/testleri, tarihsel
doğrulamalar ve ADR-007/ADR-008 değiştirilmedi. Python kontrollü, versioned
doğrulama referansı olarak korunur; Java üretim kütüphanesinin runtime
bağımlılığı veya fallback motoru değildir. OpenRocket hesabı kullanılmadı.

İncelenen testler: `tests/unit/propulsion/test_motor_catalog.py`,
`test_motor_installation.py`, `test_thrust.py`, `test_motor_properties.py`
ve `tests/unit/mass/test_total.py`. Kaynak/provenance kayıtları:
`docs/archive/nat/nat-011c1-motor-catalog.md`,
`nat-011c2-motor-installation.md`, `nat-011c3a-thrust-impulse.md`,
`nat-011c3b-motor-mass-cg.md`, `nat-011c3c-total-rocket-mass.md`.

## Python → Java API eşlemesi

Java kaynak kökü `roketsim-core/src/main/java/org/roketsim/core/`.
Alan/metot adları snake_case yerine camelCase'dir; birimler değişmedi.

| Python kaynak ve sözleşme | Java karşılığı |
|---|---|
| propulsion/models.py: MotorType, MotorValidationError | propulsion: aynı isimler |
| ThrustSample, MotorMassSample, MotorCgSample | Aynı isimli immutable records |
| MotorCertificationReference, MotorDataProvenance, MotorDefinition | Aynı isimli immutable records; doğrulanan kurucu |
| propulsion/catalog.py: MotorCatalog | propulsion.MotorCatalog; immutable katalog ve ID lookup |
| AEROTECH_F50_4T, DEMO_MOTOR_CATALOG | MotorCatalogs sabitleri |
| propulsion/installation.py: MotorInstallation, MotorInstallationResolver.resolve, MotorInstallationError | Aynı isimler ve resolve |
| propulsion/thrust.py: PropulsionEvaluationError, MotorThrustState, MotorCurveStatistics | Aynı isimler |
| MotorThrustCurveEvaluator.evaluate, MotorCurveAnalyzer.analyze | Aynı isimli stateless hesaplayıcılar |
| propulsion/properties.py: MotorMassEvolutionModel, MotorCgEvolutionModel, MotorPropertyModelProfile | Aynı isimler |
| DEMO_MOTOR_PROPERTY_MODEL_PROFILE | MotorPropertyModelProfile sabiti |
| MotorMassProperties, MotorPropertyEvaluator.evaluate | Aynı isimler |
| mass/models.py: RocketMassProperties | mass.RocketMassProperties |
| mass/total.py: RocketMassPropertiesCalculator.evaluate | mass.RocketMassPropertiesCalculator.evaluate |

Python tuple eğrileri/katalog girdileri Java'da savunmalı kopyalanan immutable
`List` olarak temsil edilir. Bu bir Java container uyarlamasıdır: Python'un
ham tuple/list tip reddi Java'ya birebir taşındığı iddia edilmez. Eksik
opsiyonel eğriler `null` ile açıkça belirtilir; otomatik model seçimi yoktur.
Java typed referansları yanlış nesne tiplerini derleme sınırında kısıtlar;
raw List elemanları ayrıca doğrulanır. Enum/profile doğrulamaları korunur.
Python KeyError katalog lookup hatası Java NoSuchElementException ile temsil
edilir; ValueError ailesi IllegalArgumentException tabanıyla eşlenir.
Domain hataları errorCode/fieldName/value taşır; genel finite doğrulaması
genel sayısal doğrulama hatası olarak kalır. Sonuç kayıtlarına Python'da
bulunmayan ek fizik doğrulaması eklenmedi.

## Katalog ve fiziksel sahiplik

Tek katalog motoru `MotorCatalogs.AEROTECH_F50_4T`, kalıcı ID
`aerotech_f50_4t`: AeroTech F50-4T, SINGLE_USE, çap 0.029 m, uzunluk
0.098 m, başlangıç kütlesi 0.0849 kg, itici kütlesi 0.0379 kg ve nominal
ejection gecikmesi 4 s. 32 kanonik thrust örneğinin tamamı aynen taşındı.
İlk (0 s, 0 N) noktası yalnızca bu kabul edilmiş katalog girdisinin açık
kararıdır; genel ekleme kuralı değildir. Sıralama, smoothing veya resampling
yapılmaz. F50 kaynak mass/cg eğrileri ve bunların provenance alanları yoktur.

Üretici ürün kaynağı, NAR S&T F50 sertifikası ve NAR yayımlı RASP transcription
provenance metinleri Python katalogdan aynen korundu. URL'ler offline
metadata'dır; runtime ağ erişimi yoktur. Sertifika özeti (76.83 N·s,
53.73 N ortalama, 79.59 N tepe, 1.43 s) ölçüm/referans metadata'sıdır;
runtime thrust otoritesi yalnızca kanonik örnek eğrisidir.

## Yerleşim, thrust ve impulse

SI ve nose-tip kökenli pozitif nose→tail x_geo korunur. x_geo, BODY z_B ile
özdeşleştirilmez. J-004 resolved geometry, motor aft referansının otoritesidir.
Front = aft − motor length; nominal radial clearance çap farkının yarısıdır;
engagement, min(aft,mount end) − max(front,mount start) olarak hesaplanır.
Motor otomatik taşınmaz; signed overhang, tam sınır eşitliği, ön containment,
pozitif engagement ve structured hata sırası korunur. Kabul edilmiş fixture:
front ≈0.907 m, aft ≈1.005 m, clearance 0 m, engagement ≈0.093 m.

Motor zamanı ignition'dan geçen süredir; global simülasyon zamanı değildir.
Thrust parça-parça lineerdir, kaynak node zamanlarında örnek aynen döner.
Impulse, her segment için analitik trapez alanlarıyla kaynak işlem sırasına
göre biriktirilir. Endpoint sonrası thrust sıfır, impulse sabittir; timestep
history/cache yoktur. Tepe için ilk occurrence seçilir. %5 eşik crossing ve
plateau karakterizasyonu runtime thrust/depletion sınırı değildir. Düşük
thrust kuyruğunun tamamı impulse ve propellant hesabına katılır.

## Motor ve toplam kütle/CG

Profile açıkça seçilir. Demo: IMPULSE_PROPORTIONAL + FIXED_MIDPOINT.
Mass fraction = cumulative impulse / total impulse; motor mass = initial
mass − propellant mass × fraction. Thrust evaluator ve analyzer yeniden
kullanılır; bağımsız impulse integrasyonu kopyalanmaz. EXPLICIT_CURVE
mass/CG kaynakları kendi ayrı zaman grid'lerinde lineer interpolate edilir,
son örnekten sonra terminal değer tutulur. Eksik eğri fallback üretmez.
Midpoint local CG = length/2; installed CG = front + local CG. Sonuçların
finite/pozitif mass ve geçerli local CG koşulları korunur.

Toplam hesaplayıcı yalnızca tamamlanmış StructuralMassProperties ve
MotorMassProperties alır; geometry, zaman veya profile almaz. Kütle toplamı
ve ağırlıklı CG kaynak sırasındadır. Katılımcı CG'ler eşitse tam koordinat
özel durumu korunur. Finite/pozitif toplam kütle, finite CG ve kapalı
katılımcı-CG aralığı kontrol edilir. J-004 structural hesapları değiştirilmedi.

## F50 fixture ve parite kanıtı

| Motor zamanı (s) | Motor mass (kg) | Toplam mass (kg) | Toplam CG x_geo (m) |
|---|---|---|---|
| 0 | 0.0849 | 0.6357490028277474 | 0.6706064563777736 |
| 0.354 | 0.0724014701890071 | 0.6232504730167545 | 0.6648832361521045 |
| 1.4 | 0.047016141471977205 | 0.5978651442997246 | 0.6525224306832178 |
| 1.43 | 0.047 | 0.5978490028277474 | 0.6525142370178312 |

Python ve Java local/installed midpoint CG: 0.049/0.956 m.
Ham curve-derived total impulse iki dilde de 76.82838700000002 N·s;
arşivdeki 76.828387 referansına kaynak toleransı içinde uyar. Tepe 79.59 N,
zamanı 0.354 s; curve end 1.43 s; %5 effective end
1.3752712686567163 s. T(1.4) ≈2.181395348837211 N ve
J(1.4) ≈76.79566606976745 N·s; sıfıra kesilmemiş tail doğrulandı.

`roketsim-core/verification/propulsion_parity.py` gerçek Python production
fonksiyonlarını çağırır; Java çıktıları expected veri üretmez. Her record,
liste, katalog örneği ve metadata alanı ayrı flatten edilip karşılaştırılır.
Kaynak node'ları, iki yanındaki nextafter noktaları, ara noktalar, 0–1.6 s
grid'i, sentetik triangle/multi/plateau, bağımsız source grid'leri, eksik
eğriler, farklı profile'lar, taşma/alt taşma, sınır ve hata girdileri kapsanır.
Sentetik fixture'lar test içindir; üretim kataloguna eklenmez.

J-005: **505 başarılı + 252 beklenen hata = 757 vaka; 743 distinct effective
input; 1862/1862 sayısal karşılaştırma birebir eşit**. Tüm structured hata
code/field/value ve kategori kontrolleri geçti. Tekrarlı/default-equivalent
girdiler ayrı coverage gibi gösterilmez. Evrensel bitwise uyumluluk iddiası
yoktur. Gerçek parite çalıştırmalarında açıklanamayan mismatch oluşmadı;
dolayısıyla düzeltme için STOP kapısı tetiklenmedi.

`roketsim-core/verification/evidence/j005-f50-results.json`, 26 eşleşen seçili
F50/variant girdisinin Python ve Java ham sonuçlarını saklar. Her girdinin
tam variant tanımı bulunur; eşit-CG gibi sentetik contributor durumları
nominal uçuş fixture'ıyla karıştırılmamalıdır. Float çıktılar yuvarlanmadı.

### Kaynak assertion sözleşmeleri

Katalog/sabitler/metadata/kaynak örnekleri ve node thrust birebir kontrol
edilir. Installation clearance rtol 2e-15, diğer installation nicelikleri
3e-15; interpolated thrust 4e-15; impulse/statistics/motor properties/total
3e-15. Bu değerler kabul edilmiş Python testlerinden alınmıştır; mutlak
tolerans sıfırdır. Yeni global eşik veya gevşetilmiş tolerans yoktur.
Hata değerleri exact veya NaN-aware eşdeğerlik ile kontrol edilir.

## Yürütülen doğrulamalar

Ortam: Windows 11 build 26200, Python 3.12.4, NumPy 2.5.2,
Eclipse Temurin JDK/javac 17.0.20.1 (17.0.20.1+1), Gradle Wrapper 8.12.1.
Aşağıdaki tamamlanan gate komutlarının tamamı exit 0 verdi.

| Kontrol | Sonuç |
|---|---|
| A: katalog Java testleri | 12 geçti |
| B: installation Java testleri | 9 geçti |
| C: thrust Java testleri | 7 geçti |
| D: motor properties Java testleri | 7 geçti |
| E: total mass Java testleri | 7 geçti |
| Tam Java suite / clean build | 294 geçti; 0 failure/error/skipped; önceki 252 + yeni 42 |
| Focused Python propulsion + total | 312 geçti |
| Tam Python regression | 1491 geçti |
| J-001 parity | 6/6 distinct vaka geçti |
| J-002 parity | 95 başarı + 53 hata + 7 trig; 155 distinct geçti |
| J-003 parity | 2485 başarı + 138 hata; 2623/2622 distinct; 2475 exact başarı, 518/518 condition norm exact |
| J-004 parity | 92 başarı + 288 hata; 380/374 distinct; 5302/5302 numeric exact |
| J-005 parity | 505 başarı + 252 hata; 757/743 distinct; 1862/1862 numeric exact |
| Mevcut + J-005 integrity testleri | 19 + 10 = 29 geçti |
| Bağımsız Java JAR consumer | Önceki foundation/vehicle ve yeni runtime mass kontrolleri geçti |
| runtimeClasspath / jdeps | Harici dependency yok; yalnızca java.base |

J-003'ün mevcut 10 nonexact libm/diagnostic vakası mevcut assertion'lar içinde
kaldı; chain max relative fark 3.8356762446777534e-16. J-002 trig max fark
1.1102230246251565e-16. Önceki parity runner'ları ve toleransları değiştirilmedi.

### Tekrar üretme komutları

Engine dizininde (full regression için dosya listesi kaldırılır):

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/unit/propulsion/test_motor_catalog.py tests/unit/propulsion/test_motor_installation.py tests/unit/propulsion/test_thrust.py tests/unit/propulsion/test_motor_properties.py tests/unit/mass/test_total.py -p no:cacheprovider -q
.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q
```

Core dizininde kullanılan izole build ve cache yolları:

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:\Users\ebrut\AppData\Local\Temp\roketsim-j001-gradle-cache'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-final' --project-cache-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
```

Focused Java run'ları aynı Gradle düzeniyle `test --tests` kullanarak sırayla
MotorCatalogTest, MotorInstallationTest, MotorThrustTest, MotorPropertiesTest,
RocketMassTest için yürütüldü; tüm gruplar sonraki aşamadan önce geçti.

Her parity runner için aşağıdaki komut çalıştırıldı; runner isimleri
`python_parity.py`, `quaternion_parity.py`, `environment_parity.py`,
`vehicle_parity.py` (ek `--group all`) ve `propulsion_parity.py`:

```powershell
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/propulsion_parity.py --java 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe' --build-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-final'
$env:AUD002_BUILD_DIR='C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-final'
$env:AUD002_JAVA='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
..\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest verification/test_parity_integrity.py verification/test_propulsion_parity_integrity.py -p no:cacheprovider -q -s
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" '-PverificationBuildDir=C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-consumer' --project-cache-dir 'C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-consumer-cache' run '-PcoreJar=C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-final\libs\roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME\bin\jdeps.exe" --print-module-deps 'C:\Users\ebrut\AppData\Local\Temp\roketsim-j005-final\libs\roketsim-core-0.1.0.jar'
```

Makineye özel yollar yürütülen ortamın kaydıdır; üretim build ayarına
sabitlenmedi. Normal üretim bağımlılığı JDK'dir; Python yalnızca verification
tooling için kullanılır. Java module exports'e sadece propulsion eklendi.

## Parite aracı bütünlüğü

AUD-002 F3/F4 ortak korumaları değiştirilmeden kullanıldı. `python -O`
nonzero ile reddedilir; subprocess exit kodu, tam satır sayısı, her alanın
shape/key kümesi, duplicate/missing/unexpected keys ve boş vaka listesi
kontrol edilir. Yeni 10 negatif test: yanlış sayı, yanlış hata kategorisi,
eksik alan, duplicate alan, fazladan alan, truncated çıktı, boş çıktı, boş
vaka koleksiyonu, başarısız Java süreci ve optimized Python. Her kasıtlı
bozulma child exit 1 üretti; üst testler beklenen reddi doğrulayarak geçti.
Bu kasıtlı fault'lar gerçek fizik/parite discrepancy değildir.

## Sınırlar ve kalan uyarılar

Yeni motor, inertia, motor import, numeric text parsing, aero/dynamics,
RK4, event üretimi, simulation engine veya frontend adaptörü yoktur.
ADR-008 typed numeric sınırı korunur. Metadata string'leri fiziksel sayısal
girdi parser'ı değildir. Java Core OpenRocket/Swing/Python/JSON runtime veya
harici fizik kütüphanesine bağlı değildir. Python ve tarihsel kayıtlara
dokunulmadı; bağımsız consumer yalnızca yeni JAR ile çalıştı.

Javadoc build'i geçti; toplam 69 missing-Javadoc uyarısı var: önceki 22 ve
yeni 47. Bunlar nonblocking dokümantasyon borcudur; kontrat veya test
başarısızlığı değildir. Test edilmiş girdiler dışındaki evrensel sayısal
eşitlik ve Java'nın Python dinamik tip sistemini birebir taklit ettiği
iddia edilmez. J-006 başlatılmadı.
