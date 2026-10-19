# J-008 — Fixed-Step Numerical Foundation ve Classical RK4

Doğrulama tarihi: 2026-10-19. Bu paket kabul edilmiş NAT-016/NAT-017 Python
sözleşmelerinin bağımsız Java 17 migration'ıdır; yeni fizik veya simulation
orchestration değildir. Aşağıdaki sonuçlar gerçek çalıştırmalardan alınmıştır.

## Başlangıç ve kaynak otoritesi

Git root `OpenRocket`; başlangıç dalı `migration/j-007-3dof-dynamics`, temiz
HEAD `1f1b7eff30d66d348d66bea192b664f003efa51b`. Uzak J-007 aynı SHA'daydı;
yerel/uzak J-008 dalı yoktu. J-001–J-007, AUD-002 ve M-001 ancestor kontrolleri
geçti. Doğrudan bu commit'ten `migration/j-008-rk4-numerics` oluşturuldu.
Main uzak SHA'sı `96686c6c35c7f859f1849abe8bfe13d5879405d9` olarak kaydedildi.

Okunan otoriteler:

- `roketsim-native-engine/src/roketsim_native/numerics/fixed_step.py` ve `rk4.py`.
- `tests/unit/numerics/test_fixed_step.py` ve `test_rk4.py`.
- `docs/archive/nat/nat-016-fixed-step-numerical-foundation.md` ve `nat-017-classical-rk4-3dof.md`.
- ADR-007/ADR-008, J-007 verification kaydı, mevcut Java Vector/Vectors/
  NumericalValidation/FrameConventions ve dynamics state/derivative API'leri.
- Mevcut strict parity protocol, integrity testleri ve bağımsız JAR consumer düzeni.

Python üretim kodu/testleri kontrollü, versioned referans olarak aynen korunur;
Java runtime bağımlılığı veya fallback değildir. Önceki Java production
paketleri, ADR'ler, NAT arşivleri, tarihsel verification kayıtları ve mevcut
parity runner'ları değiştirilmedi. Frontend/OpenRocket kaynak alınmadı.

## Python → Java API eşlemesi

Java kökü `roketsim-core/src/main/java/org/roketsim/core/numerics/`.

| Python sorumluluğu | Java public API |
|---|---|
| fixed_step.NumericalIntegrationError | NumericalIntegrationError; errorCode(), fieldName(), value() |
| fixed_step.FixedStepConfig | FixedStepConfig(double stepSizeS) |
| fixed_step.IntegrationPoint3DOF | IntegrationPoint3DOF(double timeS, TranslationalState3DOF state) |
| fixed_step.TranslationalStateAlgebra3DOF | addScaledDerivative(state, derivative, scaleS) |
| rk4.DerivativeFunction3DOF | typed functional interface evaluate(double timeS, state) |
| rk4.ClassicalRK4Integrator3DOF | step(point, config, derivativeFunction) |

Altı public tip, altı normal Java kaynak dosyasındadır. Ek production sınıfı,
state/vector modeli veya framework yoktur. Yalnız numerics package export'u
module-info.java'ya eklendi. Private add/weighted/validation metotları doğrudan
aynı Python sorumluluğunun dil uyarlamasıdır; yeni calculation authority değildir.

Python keyword-only imzaları Java typed positional imzalardır. Mevcut J-007
immutable `TranslationalState3DOF` / `TranslationalStateDerivative3DOF` ve J-001
Vector kullanılır. Numeric String parsing yoktur; ADR-008 korunur.

## Fixed-step ve state algebra sözleşmesi

FixedStepConfig yalnız stepSizeS saklar. Finite ve >0 zorunludur. Sıfır,
negative zero ve negatif finite değerler INVALID_STEP_SIZE / step_size_s /
verilen değer ile NumericalIntegrationError üretir. Raw NaN/±Infinity generic
IllegalArgumentException ↔ Python ValueError yolundadır. Minimum/default/
maximum/clipping politikası yoktur; Double.MIN_VALUE ve Double.MAX_VALUE kabul
edilip aynen korunur. Bunlar önerilen fizik timestep'leri değildir.

IntegrationPoint yalnız timeS ve state saklar; time finite, negatif olabilir.
State referansı korunur; ek physics result, derivative veya step index yoktur.
Python point'in ek state coercion/validation yapmama davranışı değiştirilmedi;
Java imzası state türünü statik olarak sınırlar.

Algebra parameterless/stateless'tir. Her bileşende önce scale*rate, sonra
state bileşenine toplama yapılır. Scale finite olmak şartıyla pozitif/sıfır/
negatif olabilir. Önce position ve velocity sonuç dizileri hesaplanır, sonra
kabul edilmiş state constructor'ı validation/copy uygular. Overflow gizlenmez.
Zero scale bile yeni state ve bağımsız immutable vektörler üretir. Algebra time
ilerletmez, callback veya physics evaluator çağırmaz; Euler integrator değildir.

WORLD local ENU, BODY +Z longitudinal ve ayrı nose-tip/aft-positive x_geo
conventions değişmedi. Position m, velocity m/s, acceleration m/s², numerical
time/scale/step s'dir. WORLD z'den altitude çıkarılmaz. Yeni tolerans yoktur.

## RK4 algoritması, işlem sırası ve ownership

`t`, `h`, `halfH=h/2.0`, `halfTime=t+halfH`, `fullTime=t+h` bir kez hesaplanır.
Önce half_time_s, sonra full_time_s finite kontrolü yapılır. Overflow,
NONFINITE_RK4_STAGE_TIME ve ilgili field/value ile structured hatadır;
callback sayısı sıfırdır. Time repair veya timestep reduction yoktur.

Tam dört derivative çağrısı:

1. k1 = f(t, original).
2. state2 = algebra(original,k1,h/2); k2 = f(halfTime,state2).
3. state3 = algebra(original,k2,h/2); k3 = f(halfTime,state3).
4. state4 = algebra(original,k3,h); k4 = f(fullTime,state4).

Position-rate ve velocity-rate ayrı ayrı, bileşen bazında
`(((k1 + 2*k2) + 2*k3) + k4)/6` sırasıyla weighted derivative üretir.
Accepted derivative constructor'ının finite validation'ı kullanılır.
Next state = algebra(original,weighted,h); yeni point(fullTime,nextState)
döndürülür. Üç trial ve final olmak üzere dört state construction aynı algebra
yolundadır. Java 17 floating-point arithmetic kullanılır; FMA, regrouping,
compensated summation veya alternatif numerical algorithm yoktur.

K4 state4 yalnız trial state'tir; accepted endpoint değildir. Beşinci endpoint
derivative/physics çağrısı yapılmaz. Inputs değişmez; integrator field/cache/
history/stage counter saklamaz. Aynı callback davranışı ve input için sonuç
deterministic'tir. Test-only trace/counter üretim integrator'ına eklenmedi.

Upstream callback hataları yakalanmaz/wrap edilmez; her aşamada aynı Java
exception instance'ı yayılır. Java typed callback yanlış return türünü compile
time'da dışlar. Null return, stage isimli NullPointerException ile coercion
olmadan reddedilir; probe bunu Python TypeError kategorisiyle karşılaştırır.
Bu bir dil uyarlamasıdır, Python'un tüm dinamik türlerini Java'da taklit etme
iddiası değildir. Null callback kullanımında normal Java type-contract hatası
yayılır. Hata mesajının dile özgü repr biçimi değil, category/code/field/value
ve generic finite-validation bağlamı karşılaştırılır.

## Test-first ve frozen fixture kanıtı

Üretim sınıfları yokken focused RED: compileTestJava missing-symbol/package
hatalarıyla exit 1. Uygulamadan sonra aynı testler GREEN; beklenen test-first
aşaması dışında genuine Python–Java discrepancy oluşmadı.

FixedStepTest **7** + ClassicalRK4Test **11** = **18 yeni Java test**.
Döngüler çok sayıda boundary/alanı aynı test içinde kontrol eder; Python test
sayısıyla birebir eşitlik iddia edilmez. Coverage: min/max finite step,
raw nonfinite, negative time, record schema, zero/negative algebra, copies,
determinism, four-stage traces, original base, k4 ayrımı, her aşamada sentinel
identity/null return, callback öncesi time overflow, trial/weighted/final state
overflow, no clipping ve minimum-positive-step half-step underflow.

| Fixture | Sonuç |
|---|---|
| Algebra +0.5 | p=(3,4.5,6), v=(4.5,6,7.5) |
| Algebra 0 | Aynı sayısal değerler, yeni bağımsız state/vector |
| Algebra -0.5 | p=(-1,-0.5,0), v=(3.5,4,4.5) |
| Constant derivative | time=2.2, px=1.4, vx=5.6; dört çağrı |
| Constant acceleration | pz=108.75, vz=15 |
| Harmonic oscillator | x=0.9950041666666667, vx=-0.09983333333333334 |
| Stage spy | times=(2,2.2,2.2,2.4); k4 x=1.112, vx=0.07599999999999996 |
| Stage spy accepted endpoint | x=1.1157333333333335, vx=0.07119999999999993 |

Kaynak algebra assert_array_equal exact'tir. Kaynak RK4 vector fixture'ları
np.testing.assert_allclose default rtol=1e-7/atol=0; oscillator scalar fixture'ı
pytest.approx default relative=1e-6/absolute=1e-12 kullanır. JUnit fixture
assertion'ları bu kaynak sözleşmelerini korur. Yeni production tolerance yoktur.
Raw oscillator vx, metindeki yaklaşık -0.0998333333333333 değerine yuvarlanmadı.

## Gerçek Python–Java paritesi

numerics_parity.py gerçek Python production sınıflarını, NumericsParityProbe
gerçek Java production API'lerini çağırır. Expected RK4 formülü kopyalanmaz.
Synthetic constant/acceleration/oscillator/time-dependent derivative yalnız
test callback'idir. Probe parsing/reflection/sentinel/trace test kaynaklarındadır,
üretim JAR'ına girmez. Ortak protocol ve eski runner'lar değiştirilmedi.

**183 toplam / 182 distinct effective input; 89 başarı / 94 beklenen hata**.
Complete endpoint/record/vector schema, vector ordering, full callback trace,
callback count ve structured error metadata karşılaştırıldı. **3995/3995 exact**:
2823 scalar/component/time ve 1172 shape/callback-count metadata. Nonexact=0;
max absolute=0, max relative=0. Algebra/copy/time/shape/error değerleri exact;
RK4 state/stage bileşenleri kaynak allclose rtol=1e-7/atol=0, frozen oscillator
scalar'ları kaynak approx relative=1e-6/absolute=1e-12 ile kontrol edilir.
Exact-match sayacı toleranstan ayrı tutulur. Yeni global numerical policy yoktur.

`roketsim-core/verification/evidence/j008-frozen-numerics.json` dört RK4 frozen
fixture'ın complete effective input ve decoded Python/Java endpoint/stage
değerlerini yuvarlamadan saklar. Algebra fixture'ları focused exact testlerde
ve parity collection'da ayrıca bulunur. Expected overflow/invalid RuntimeWarning
çıktıları Python negative cases'te görüldü, bastırılmadı.

## Tam regression, integrity ve independence

Ortam: Windows 11 build 26200; Python 3.12.4; NumPy 2.5.2; Eclipse Temurin
Java/javac 17.0.20.1+1; checksum kontrollü Gradle Wrapper 8.12.1.

| Gate | Gerçek sonuç; exit 0 |
|---|---|
| Focused J-008 Java | 18 passed |
| Focused Python NAT-016/017 | 40 passed |
| Tam clean Java build/test | **391 passed**, 0 failure/error/skipped; önceki 373 korundu |
| Tam Python regression | **1491 passed** |
| J-001 parity | 6/6 distinct, passed |
| J-002 parity | 95 başarı + 53 hata + 7 trig; 155 distinct |
| J-003 parity | 2485 başarı + 138 hata; 2623/2622 distinct; 2475 exact başarı, 518/518 condition norm exact |
| J-004 parity | 92 başarı + 288 hata; 380/374 distinct; 5302/5302 exact numeric |
| J-005 parity | 505 başarı + 252 hata; 757/743 distinct; 1862/1862 exact numeric |
| J-006 parity | 236 başarı + 359 hata; 595/548 distinct; 3570/3570 exact numeric |
| J-007 parity | 118 başarı + 299 hata; 417/408 distinct; 2162/2162 exact numeric |
| J-008 parity | 89 başarı + 94 hata; 183/182 distinct; 3995/3995 exact numeric |
| Eski + yeni negative integrity | **65 passed**, önceki 51 + yeni 14 |
| Independent packaged JAR consumer | Önceki yedi grup + NUMERICS_RK4_CONSUMER PASS |
| verifyProductionDependencies/runtimeClasspath | PASS / No dependencies |
| jdeps --print-module-deps | **java.base** |

J-002 Math/StrictMath trig maximum Python farkı 1.1102230246251565e-16;
J-003'ün belgelenmiş 10 nonexact vakası eski kaynak toleransları içinde kaldı.
Bunlar yeni J-008 bulgusu değildir; önceki math/environment kodu veya toleransı
değiştirilmedi. Sonlu test koleksiyonundan evrensel bitwise eşdeğerlik veya
tüm platformlara ilişkin numerical-boundary garantisi çıkarılmaz.

Yeni 14 integrity kontrolü: wrong numerical value, wrong stage time, wrong
callback count, wrong error category/code/field, missing/extra/duplicate field,
truncated/empty output, empty case collection, failed child process ve Python
-O. Her kasıtlı fault child exit 1 üretti; parent test beklenen reddi doğruladı.
Üretim hesaplamaları değiştirilmedi. Tam 65-test integrity çalışması 81.40 s
sürdü, exit 0 verdi. Empty/output/process doğrulamaları ve optimized-mode
rejection korunur; test araçlarının sahte PASS üretmediği ayrıca sınandı.

### Tekrarlanabilir komutlar

Engine dizininde:

```powershell
.\.venv\Scripts\python.exe -B -m pytest tests/unit/numerics/test_fixed_step.py tests/unit/numerics/test_rk4.py -p no:cacheprovider -q
.\.venv\Scripts\python.exe -B -m pytest -p no:cacheprovider -q
```

Core dizininde; aşağıdaki makine yolları yalnız yürütme kaydıdır, production
veya build default'u değildir. Build/cache çıktıları TEMP içinde tutuldu.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:GRADLE_USER_HOME='C:/Users/ebrut/AppData/Local/Temp/roketsim-j001-gradle-cache'
.\gradlew.bat --version --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j008' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-cache' test --tests org.roketsim.core.FixedStepTest --tests org.roketsim.core.ClassicalRK4Test --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-final' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-final-cache' clean build dependencies --configuration runtimeClasspath --offline --no-daemon
$env:AUD002_BUILD_DIR='C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-final'
$env:AUD002_JAVA='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/java.exe'
..\roketsim-native-engine\.venv\Scripts\python.exe -B -m pytest verification/test_parity_integrity.py verification/test_propulsion_parity_integrity.py verification/test_aerodynamics_parity_integrity.py verification/test_dynamics_parity_integrity.py verification/test_numerics_parity_integrity.py -p no:cacheprovider -q -s
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" '-PverificationBuildDir=C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-consumer' --project-cache-dir 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-consumer-cache' run '-PcoreJar=C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-final/libs/roketsim-core-0.1.0.jar' --offline --no-daemon
& "$env:JAVA_HOME/bin/jdeps.exe" --print-module-deps 'C:/Users/ebrut/AppData/Local/Temp/roketsim-j008-final/libs/roketsim-core-0.1.0.jar'
```

Sekiz runner ayrı ayrı `..\roketsim-native-engine\.venv\Scripts\python.exe -B`
ile çalıştırıldı: python_parity.py, quaternion_parity.py, environment_parity.py,
vehicle_parity.py, propulsion_parity.py, aerodynamics_parity.py,
dynamics_parity.py, numerics_parity.py. Hepsinde `--java` yukarıdaki java.exe,
`--build-dir` yukarıdaki j008-final kullanıldı; vehicle için ek `--group all`.
Tam çıktıların geçici logları j008-<runner>.log olarak TEMP'te oluşturuldu.
JUnit XML sonuçlarından counts/failure/error/skipped ayrı ayrı okundu.

## Son kapsam, sınırlamalar ve publication politikası

Değişiklikler: altı numerics production dosyası; module-info.java'da tek export;
FixedStepTest, ClassicalRK4Test, NumericsParityProbe; numerics_parity.py ve
test_numerics_parity_integrity.py; frozen evidence JSON; consumer'a tek yeni
RK4 kontrol bölümü; bu kayıt. Toplam 15 dosya. Java/Python mevcut production
davranışı ve tarihsel kaynaklar için git diff kontrolleri boştu.

Independent consumer ayrı Gradle projesidir, yalnız paketlenmiş Core JAR'ını
kullanır. Constant-acceleration adımının sonucunu, dört çağrıyı ve input
immutability'yi doğruladı. Python, NumPy, OpenRocket, Swing, JSON/CLI, ağ
servisi veya başka harici runtime dependency yoktur.

Javadoc başarılıdır; varsayılan gösterim sınırında 100 missing-comment uyarısı
raporlandı. Bu nonblocking documentation borcudur, toplam eksik yorumların
tam envanteri değildir. Uyarı/test bastırma veya numerical repair yapılmadı.

PhysicsEvaluator/NAT-015 orchestration, state-altitude mapping, flight events,
history/trajectory, simulation engine, additional integrators, adaptive/error
estimate, timestep clipping, quaternion/6DOF, moments, frontend/JSON
integration uygulanmadı. Sonraki milestone başlatılmadı.

Yayın ancak tamamlanan tüm gate'lerden sonra, scope/secret/generated-output
kontrolüyle tek `feat(java): implement J-008 fixed-step numerics and rk4`
commit'i ve yalnız migration/j-008-rk4-numerics normal push'u olarak yapılır.
Main/J-007 SHA'ları publication öncesi/sonrası ve local/remote yeni SHA eşitliği
kontrol edilir. Gerçek commit SHA'sı ve push sonucu final görev raporunda
verilir; bu dosyayı içeren commit'in otoriter kimliği Git history'dir.
Self-referential SHA için ikinci commit/amend veya geçmiş değişikliği yapılmaz.
