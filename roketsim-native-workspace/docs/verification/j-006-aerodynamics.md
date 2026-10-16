# J-006 — Aerodynamics migration ve kontrollü semantic recovery

Tarih: 2026-10-16. **Son durum: tüm teknik kabul gate'leri geçti.**
İlk aşamada gerçek parite discrepancy'sinde duruldu; bu tarihsel kayıt aşağıda
korunur. Kullanıcının dar kapsamlı düzeltme onayından sonraki recovery ve
nihai kanıtlar son bölümde yer alır. Önceki STOP, başarılı nihai testlerle
gizlenmez; orijinal RED JSON değiştirilmedi.

## Git ve kaynak otoritesi

Temiz başlangıç `migration/j-005-propulsion-runtime-mass`, HEAD
`12b24733deeedef2aac41434d8134130a07e4389` doğrulandı. J-001–J-005,
AUD-002 ve M-001 ancestry kontrolleri geçti. Doğrudan bu commit'ten
`migration/j-006-aerodynamics` açıldı. Python kaynakları/testleri ve geçmiş
kayıtlar değiştirilmedi; main veya tarihsel dallar değiştirilmedi.

İncelenen otorite: `roketsim-native-engine/src/roketsim_native/aerodynamics/`
altındaki `surfaces.py`, `drag.py`, `static_stability.py`, `static_margin.py`;
`tests/unit/geometry/test_aerodynamic_geometry.py` ve
`tests/unit/aerodynamics/test_surfaces.py`, `test_basic_drag.py`,
`test_static_stability.py`, `test_static_margin.py`. NAT-012A.0, A.1 ve B
arşiv kayıtları okundu; OpenRocket uygulaması kullanılmadı.

## Sorumluluk / Java dosya eşlemesi

Java kökü `roketsim-core/src/main/java/org/roketsim/core/aerodynamics/`.
Her public tip ayrı dosyada; yeni factory/manager/framework yoktur.

| Python modülü | Aynı isimli Java tipleri |
|---|---|
| surfaces.py | SurfaceValidationError, SurfaceFinish, SingleStageRocketAerodynamicSurfaces |
| drag.py — selector'lar | BoundaryLayerModel, ReynoldsLengthPolicy, LowReynoldsContinuation, NosePressureModel, FinEdgeModel, BaseDragModel, PlumeTreatment, SkinFrictionBranch |
| drag.py — model/hesap | AerodynamicEvaluationError, BasicDragModelProfile, SkinFrictionEvaluation, BasicDragResult, BasicDragEvaluator |
| static_stability.py — selector'lar | StaticNormalForceModel, FinCenterOfPressureModel, BodyLiftTreatment |
| static_stability.py — model/hesap | StaticStabilityModelProfile, StaticAeroContribution, StaticAerodynamicProperties, StaticAerodynamicEvaluator |
| static_margin.py | StaticMarginResult, StaticMarginCalculator |

Python module profile sabitleri ilgili Java profile record'unda static final
alanlardır. Alan adları camelCase, SI birimleri aynıdır. Java positional
çağrılar Python keyword-only imzalarının dil uyarlamasıdır. Test adapter'ında
wire parsing/reflection vardır; üretim hesaplarında yoktur. İlave üretim
sınıfı/hesap otoritesi eklenmedi. `module-info.java` yalnız aero export ekler.

## Taşınan model politikaları ve denklemler

- Finish adı zorunlu ve boş olamaz; roughness finite ve >=0; nose/body/fin bağımsızdır, material density kullanılmaz.
- Drag: `Re=V*L_aero/nu`; BasicFlightConditions Reynolds/reference-length otoritesi yeniden yorumlanmaz. Yalnız friction evaluation `max(Re,1e4)` kullanır.
- Smooth Cf `1/(1.50*ln(Re_eval)-5.6)^2`; rough Cf sıfır roughness'ta sıfır, aksi halde `.032*(k/L_aero)^.2`; ikisine de `1-.10*M*M` uygulanır. Eşitlikte SMOOTH seçilir.
- Body correction `1+1/(2*(L_axisymmetric/Dmax))`; fin correction `1+2*t/MAC`. Alanlar J-004 resolved geometry'den tüketilir.
- Nose pressure: `C0=.8*sin(phi)^2`, `C1=sin(phi)`, `d1=4/(gamma+1)*(1-C1/2)`, `a=C1-C0`, `C*=C0+a*M^(d1/a)`; gamma J-003 air properties otoritesidir.
- Fin LE: `N*t*span/Aref * .85*(1+M*M/4+M^4/40)*cos(LE_sweep)^2`. Fin TE ve airframe base `.12+.13*M*M` kullanır. Plume IGNORED.
- Yedi katkı nose/body/fin friction, nose pressure, fin LE pressure, fin TE base, airframe base'dir. Java'daki yerel compensated accumulation Python 3.12 float sum davranışını hedefler; grouped toplamla değiştirilmez. Yeni global tolerance/policy değildir.
- Drag runtime alanı kapalı `0 <= Mach <= 1`; statik aero alanı `0 <= Mach < .8` ve .8 dahil değildir. Clamp/extrapolation yoktur.
- Statik nose CNa `2*A_frontal/Aref`, CP `(2/3)*L_nose`; single-fin CNa kabul edilmiş mid-chord-sweep sqrt bağıntısıdır. Set çarpanı `N/2`; fin-fin tablosu Ntot<=4:1, 5:.948, 6:.913, 7:.854, 8:.810, >8:.750; body-on-fin `1+r/(span+r)`.
- Fin CP Mach<=.5 için quarter MAC, .5<M<.8 için Python kaynak quintic u-polynomial'ı; M=2 yalnız polynomial construction endpoint'idir, runtime genişlemesi değildir. CNa ağırlıklı nose/fin toplam CP korunur.
- Margin `(CP_x_geo-CG_x_geo)/Dmax`; CP static aero, CG J-005 mass ve Dmax J-004 geometry otoritesidir. Aft-positive x_geo; sıfır/negatif sonuç veya tasarım kabul eşiği düzeltilmez.

## İlk aşamada gerçekleştirilen kontroller — tarihsel STOP kaydı

Ortam: Python 3.12.4 / NumPy 2.5.2 / Windows 11 build 26200;
Temurin Java/javac 17.0.20.1+1; Gradle Wrapper 8.12.1.

1. Test-first RED: eksik yeni Java sınıfları nedeniyle compileTestJava exit 1 (beklenen).
2. İlk derlemede finCount Object API'sine gerekli numeric/integral uyarlama yapıldı; bu gerçek parite öncesi normal geliştirme aşamasıdır, gizli parite düzeltmesi değildir.
3. Odaklı Java `AerodynamicsTest`: **35 geçti**, 0 failure/error/skipped, exit 0.
4. İlgili beş Python test dosyası: **144 geçti**, exit 0.
5. İlk gerçek J-006 paritesi: **487 toplam, 472 distinct effective input; 214 başarılı, 273 beklenen hata; 3176/3176 sayısal sonuç exact**, max relative fark 0, exit 0.
6. Ek selector-string sözleşmesi araştırması: **1 vaka, gerçek OK/ERROR uyuşmazlığı, exit 1**. STOP kuralı uygulandı. İlk parite setinin bu contract köşesini kapsamadığı açıkça kaydedilir; önceki başarı bu bulguyu geçersiz kılmaz.

İlk set geometri prerequisitelerini, rough/smooth dallarını, Re sınırlarını,
Mach .5/.8/1 ve nextafter sınırlarını, yedi Cd terimini, grouped diagnostics,
fin interference/CP fraction helper sonuçlarını, CNa/CP ve margin'i içerir.
Ham finite ihlalleri generic ValueError ailesi, domain hataları structured
category/code/field/value olarak karşılaştırılır. Toleranslar source testlerden:
drag 3e-15, static/diagnostics 4e-15, margin 5e-15, geometry 3e-15; mutlak
tolerans sıfır. Frozen Java drag ankrajları kaynak 5e-15 kullanır. Metadata ve
hata değerleri exact/NaN-aware kontrol edilir; gevşetme yapılmadı.

İlk setin birebir sonuçlarından biri J-005 mass ile M=.75 zincir margin'i:
0.5476767889371825 caliber (iki dilde aynı). Bu sınırlı mevcut kanıttır;
tam J-006 kabulü değildir.

## İlk STOP bulgusu J006-P01 — metadata selector eşitliği

Girdi: `BasicDragModelProfile` kabul edilmiş profile'dan yalnız
`boundary_layer_model="fully_turbulent"` olacak şekilde türetilir.

**Python:** `drag.py:49` selector'ı `(str, Enum)` tabanlıdır.
`drag.py:394` supported-profile kontrolü dataclass eşitliği kullanır.
Canonical string ve enum değeri eşit olduğundan girdi kabul edilir;
`total_cd0=0.4740271634476429` döner ve verilen string profile'da korunur.

**Java:** `BasicDragEvaluator.java:14–15` record `equals` kullanır;
String, BoundaryLayerModel enum nesnesine eşit değildir. Sonuç:
`AerodynamicEvaluationError`, code `UNSUPPORTED_BASIC_DRAG_PROFILE`,
field `model_profile`; value verilen profile'dır. Sayısal çıktı üretmez.

Exact input, tam raw Python field çıktısı, raw Java protocol çıktısı,
komut, sürümler ve exit 1
`roketsim-core/verification/evidence/j006-profile-finding.json` içinde korunur.
Bu yalnız sayısal tolerans farkı değil, geçerli girdinin yanlış reddidir.
Benzer eşitlik paterni StaticAerodynamicEvaluator'da da bulunur; diğer
selector'larda aynı sonucun oluştuğu henüz bağımsız çalıştırmayla kanıtlanmadı.

### STOP anındaki öneri — o aşamada uygulanmadı

Kullanıcı onayından sonra önce canonical metadata-string ve enum selector
eşdeğerliğini (drag'in yedi selector'ı, static'in üç selector'ı) ayrı RED
regresyonlarla doğrulamak; supported-profile kontrolünü Python'un kabul ettiği
metadata eşitliğine uyarlamak. Verilen profile sonuçta korunmalı, yanlış
string'ler reddedilmeye devam etmelidir. Numeric string parsing eklenmez;
ADR-008 ihlal edilmez. Denklem/sabit/tolerans değişikliği önerilmez.

**Bu düzeltme uygulanmadı; onay gerekiyor.** Eksik karşılaştırmaları geçmek
için fixture/assertion değiştirilmedi.

## İlk aşama komutları ve STOP anında çalıştırılmayan gate'ler

Core dizininde odaklı Java çalıştırması (JAVA_HOME Temurin17;
GRADLE_USER_HOME mevcut doğrulanmış temp cache):

```powershell
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j006' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-cache' test --tests org.roketsim.core.AerodynamicsTest --offline --no-daemon
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/aerodynamics_parity.py --java 'C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe' --build-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j006'
```

Engine dizininde focused pytest:

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/unit/geometry/test_aerodynamic_geometry.py tests/unit/aerodynamics/test_surfaces.py tests/unit/aerodynamics/test_basic_drag.py tests/unit/aerodynamics/test_static_stability.py tests/unit/aerodynamics/test_static_margin.py -p no:cacheprovider -q
```

Açık bulgu nedeniyle tam Java/Python regression, önceki J-001–J-005 parity
tekrarı, yeni fault-injection testleri, clean build/Javadoc, bağımsız consumer
ve jdeps acceptance gate'leri bu paket için **çalıştırılmadı**. Bunlar geçmiş
paket sonuçlarından PASS sayılmaz. Mevcut ortak parite korumaları yeniden
kullanıldı: -O reddi, exit-code, tam satır/alan sayısı, duplicate/missing/extra
key ve empty-case kontrolleri. Yeni araç için negatif test çalıştırması
tamamlanmadığından integrity kabulü henüz verilmez.

Üretim kaynakları yalnız mevcut aero kapsamına dayanır; harici runtime
dependency eklenmedi. Ancak bağımsızlık/build kabulü son gate'ler
çalıştırılmadan tamamlandı diye raporlanmaz. Yeni fizik, kuvvet vektörü,
moment, RK4, simulation engine, frontend veya J-007 yoktur. Tüm çalışma
dosyaları inceleme için commit edilmeden korunur.

## Onaylı recovery — 2026-10-16

### Devralınan çalışma ve yeniden üretim

Yeni dal açılmadı. Mevcut `migration/j-006-aerodynamics`, HEAD
`12b24733deeedef2aac41434d8134130a07e4389` ve J-005 ancestry yeniden doğrulandı.
Tek tracked değişiklik module-info aero export'uydu; diğer J-006 dosyaları
untracked olarak devralındı: 25 aero üretim tipi, iki Java test/probe dosyası,
parity runner, bu doğrulama kaydı ve orijinal RED kanıtı. İlgisiz değişiklik
yoktu. Önce kanıt ve tüm mevcut J-006 uygulama/test araçları incelendi.

Orijinal tek-vaka komutu yeniden exit 1 verdi: Python başarılı tam sonucu
döndürürken Java aynı `UNSUPPORTED_BASIC_DRAG_PROFILE`, `model_profile`
hatasını üretti. Kök neden doğrudan kaynakla doğrulandı: Python `(str, Enum)`
selector'larının canonical string ile eşitliği ve dataclass comparison;
Java record `equals`'unun farklı metadata nesne tiplerini eşit saymaması.

Python production evaluator'ıyla 10 selector × 10 girdi = 100 kontrol
yürütüldü. Yedi drag ve üç static selector için typed enum ve exact canonical
string kabul edildi (20); uppercase, leading/trailing whitespace, unknown,
empty, null, integer ve boolean reddedildi (80). Başarılı sonuçlar girdideki
metadata temsilini korudu. Bunlar
`roketsim-core/verification/evidence/j006-python-selector-contract.json`
içinde saklandı; beklenen değerler Java'dan türetilmedi.

### Test-first RED → GREEN ve tam düzeltme sınırı

`AerodynamicProfileTest` önce düzeltmesiz çalıştırıldı: 22 test, 11 beklenen
başarısızlık, exit 1. Bunlar 10 ayrı canonical-string kabul testi ve tüm
string'lerin birlikte kullanım testidir. Invalid-varyant ve numeric-policy
reddi testleri zaten geçiyordu. Önceki 35 J-006 testi kaldırılmadı.

Üretim düzeltmesi yalnız `BasicDragEvaluator` ve `StaticAerodynamicEvaluator`
girişindeki supported-profile kontrolüdür. Her selector açıkça doğru typed
enum **veya aynı enum'un exact canonical String değeri** ile karşılaştırılır.
Minimum Reynolds ve exclusive Mach limitinin tam kabul edilmiş değerleri
de ayrıca karşılaştırılır. Object/profile sınıf kontrolü korunur. Sayısal
hesaplama blokları değiştirilmedi. Generic enum parser, factory, adapter,
trim/case-fold/alias veya numeric string parsing eklenmedi. Record
`equals/hashCode` override edilmedi. Sonuç aynı supplied profile instance'ını
tutar; metadata normalize edilmez.

GREEN: 22 profile regresyonu ve 35 önceki aero testi = **57 test**, 0 hata,
exit 0. Regresyon sadece toplam Cd'yi değil, baseline fizik sonuç kaydının
tüm alanlarını ve supplied profile metadata/identity'sini karşılaştırır.
Yanlış profile sınıfları, yanlış string varyantları ve numeric policy'nin
iki yanındaki nextafter değerleri reddedilmeye devam eder.

Orijinal tam girdi ayrıca yeniden çalıştırıldı: **tam Python/Java field
kümesi ve tüm değerler exact eşit**, `total_cd0=0.4740271634476429`, exit 0.
Raw Java çıktısı ve iki dilin tam decoded sonucu ayrı
`j006-profile-resolution.json` dosyasındadır. Orijinal
`j006-profile-finding.json` SHA-256 öncesi/sonrası aynıdır:
`BE82232BF2546E24CFE6CAA67C34A7B76D330D1765909D2D758BB166153EC826`.

İlk clean build Javadoc yorumlarındaki `<` karakterlerini malformed HTML
olarak reddetti (4 diagnostic, exit 1). Yalnız iki yorum `{@code ...}` ile
işaretlendi; hiçbir işlem/sabit/test değişmedi. Ardından aynı clean build
tamamlandı. Bu belge biçimleme hatası fiziksel parite bulgusu değildir.

### Nihai parite sonuçları

Önceki 487 vaka aynen tutuldu, 108 selector/profile vakası eklendi.
Karşılaştırma/tolerans mantığı değiştirilmedi. Test wire adapter'ına yalnız
yanlış profile sınıfını gönderebilmek için iki profile marker eklendi;
üretim API'sine parser eklenmedi. Tam field seti ve metadata/error value
record'ları flatten edilerek karşılaştırılır.

| Paket | Toplam / distinct effective | Başarı / beklenen hata | Sayısal kanıt |
|---|---:|---:|---|
| J-001 | 6 / 6 | 6 başarı | Mevcut vector assertion'ları geçti |
| J-002 | 155 / 155 | 95 başarı + 53 hata + 7 trig | Trig max Python farkı 1.1102230246251565e-16 |
| J-003 | 2623 / 2622 | 2485 / 138 | 2475 exact başarılı vaka; 518/518 condition norm exact |
| J-004 | 380 / 374 | 92 / 288 | 5302/5302 numeric exact |
| J-005 | 757 / 743 | 505 / 252 | 1862/1862 numeric exact |
| J-006 | **595 / 548** | **236 / 359** | **3570/3570 numeric exact; nonexact 0; max relative/absolute fark 0** |

J-006 distinct effective sayısı, evaluator'ın eşdeğer saydığı default enum ve
canonical string override'larını aynı fizik/policy girdisi sayar; toplam
595 vaka ise farklı metadata temsilini ve hata sonuçlarını ayrı ayrı test
eder. Bu iki coverage sayısı birbirinin yerine kullanılmaz.

Yeni veya açıklanamayan parite discrepancy'si oluşmadı. J-003'ün zaten
kontrollü 10 nonexact libm/diagnostic vakası mevcut toleransları içinde aynı
kaldı; chain max relative fark 3.8356762446777534e-16. Önceki runner'lar,
beklenen sonuçlar veya toleranslar değiştirilmedi. J-002 overflow negatif
vakasında NumPy'nin beklenen RuntimeWarning'i görüldü; exit 0 ve hata
semantiği karşılaştırması geçti. Evrensel bitwise uyumluluk iddiası yoktur.

### Tam regression, integrity, consumer ve bağımlılıklar

| Gate | Gerçek sonuç / exit |
|---|---|
| Focused Python geometry aero + aero | 144 passed / 0 |
| Tam Python reference pytest | 1491 passed / 0 |
| Java clean build ve tüm testler | **351 passed**, 0 failure/error/skipped / 0 |
| Önceki Java regression | 294 test korundu; yeni 35 + 22 eklendi |
| AUD-002 + J-005 integrity | 19 + 10 geçti |
| Yeni J-006 integrity | 10 geçti |
| Birleşik integrity çalıştırması | **39 passed** / 0 |
| Bağımsız Java consumer | Önceki tüm gruplar ve AERODYNAMICS_CONSUMER PASS / 0 |
| verifyProductionDependencies / runtimeClasspath | PASS / No dependencies / 0 |
| Yeni JAR jdeps --print-module-deps | **java.base** / 0 |

Yeni integrity testleri yalnız child probe çıktısını bozar: yanlış sayı,
yanlış error category, missing/duplicate/unexpected field, truncated,
empty output, empty case collection, process failure, optimized Python.
Her kasıtlı hata child exit 1 ile yakalandı; parent testler beklenen reddi
doğrulayarak geçti. Bunlar gerçek fizik discrepancy'si değildir. Ortak
`parity_contract.py` ve geçmiş integrity testleri değiştirilmedi.

Bağımsız consumer yalnız yeni JAR ile derlenip çalıştı; explicit canonical
string profile ile drag, typed profile ile static CNa/CP ve J-005 total CG
ile margin hesaplarını doğruladı. Fizik kuvveti, moment veya trajectory
uygulaması eklenmedi. Üretim runtime yalnız JDK java.base'dir.

### Nihai komutlar ve ortam

Yukarıdaki focused komutlar tekrar çalıştırıldı. Tam Python için engine
dizininde `.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q`
çalıştırıldı. Ortam Python 3.12.4 / NumPy 2.5.2 / Windows 11 build 26200;
Java/javac Temurin 17.0.20.1+1, Wrapper 8.12.1 olarak yeniden doğrulandı.

Core dizinindeki kabul komutları (temp yolları yalnız yürütülen ortam kaydı):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:/Users/ebrut/AppData/Local/Temp/roketsim-j001-gradle-cache'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-final' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
$env:AUD002_BUILD_DIR='C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-final'
$env:AUD002_JAVA='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe'
..\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest verification/test_parity_integrity.py verification/test_propulsion_parity_integrity.py verification/test_aerodynamics_parity_integrity.py -p no:cacheprovider -q -s
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-consumer' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-consumer-cache' run '-PcoreJar=C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-final/libs/roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME/bin/jdeps.exe" --print-module-deps 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-final/libs/roketsim-core-0.1.0.jar'
```

Altı parity runner ayrı ayrı `--java` yukarıdaki JDK java.exe ve
`--build-dir C:/Users/ebrut/AppData/Local/Temp/roketsim-j006-final` ile
çalıştırıldı: `python_parity.py`, `quaternion_parity.py`,
`environment_parity.py`, `vehicle_parity.py --group all`,
`propulsion_parity.py`, `aerodynamics_parity.py`. Her biri exit 0 verdi.

### Son kapsam ve sınırlamalar

Git scope yalnız 25 yeni aero üretim tipi, module export, üç Java test/probe
dosyası, yeni aero parity/integrity araçları, üç kanıt JSON'u, bağımsız
consumer'ın aero kontrolleri ve bu kayıttır. Python kaynakları/testleri,
ADR-007/008, J-001–J-005 üretim sınıfları ve geçmiş doğrulamalar değişmedi.
Build çıktıları/cache'ler temp dizinlerinde, commit kapsamı dışındadır.

Clean build başarılıdır; Javadoc 100 missing-comment uyarısı raporladı
(varsayılan gösterim sınırı; tüm eksik yorumların kesin sayısı değildir).
Bu nonblocking dokümantasyon borcudur; hata bastırılmadı veya test kapatılmadı.
Port edilen zero-AoA drag/static modellerinin kabul edilmiş domain ve fidelity
sınırlamaları devam eder. Sonik sınırın denklemce kabulü transonik doğruluk
iddiası değildir; roughness bağımsız geçerlilik genişletmesi TBD olarak kalır.
Python dinamik tip sisteminin tüm olası nesneleri Java typed API'siyle
evrensel eşdeğer sayılmaz. Buradaki exact sonuç yalnız yürütülen girdilere
aittir. J-007 başlatılmadı.
