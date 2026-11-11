# J-001 — Independent Java 17 Core Foundation

## Kaynak ve karar

Referans HEAD: `96686c6c35c7f859f1849abe8bfe13d5879405d9`.
Dal: `migration/j-001-java-fondation`; yazımı kullanıcı tarafından onaylıdır.
Otorite: Python math/numerical.py, math/vectors.py, math/frames.py,
units/policy.py ve ilgili unit testleri. ADR-007 yeni migration kararını kaydeder;
ADR-001/003 değişmez. Python production ve test dosyaları korunur.

## Java sözleşmesi

`NumericalValidation`: isFinite, requireFinite(value[, name]), isNearZero(value, atol).
`Vector`: genel boyutlu immutable finite double verisi; of(input),
of(input, size, name), size, get, toArray. Array giriş/çıkışı bağımsız kopyadır.
Java array/List sayısal verisi Python array/list/tuple sınırına karşılık gelir;
nested veri düzleştirilmez. Sıfır boyut Python gibi geçerlidir.
`Vectors`: magnitude, dot, normalize(vector, atol), cross3. Yalnız cross3 tam üç
bileşen ister. Zero/near-zero normalize reddedilir; tolerans zorunludur.
Raw non-finite, invalid shape ve math-domain hataları IllegalArgumentException
ile Python ValueError sözleşmesine eşlenir. Global tolerans yoktur.

Norm doğrudan squared-sum/sqrt kullanır; hipot ile overflow/underflow davranışı
sessizce değiştirilmez. NumPy BLAS ve Java toplama sonucu her keyfi vector için
bitwise özdeşlik iddiası yoktur. Mevcut pytest.approx scalar ve
numpy.testing.assert_allclose array sözleşmeleri parity aracında aynen kullanılır.
Cross fixture'ları exact array equality ile karşılaştırılır.

`ReferenceFrame`, `AxisConvention`, `FrameConventions`: WORLD ENU, BODY z_B
longitudinal/thrust/roll ve ayrı nose-tip x_geo metadata. Rotation/quaternion yoktur.
`UnitPolicy.CANONICAL_UNITS`: kabul edilmiş 18 SI metadata girdisi; conversion yoktur.

## Build ve bağımsızlık

Tek JPMS modülü org.roketsim.core, yalnız java.base. Production dependency yoktur.
Artifact org.roketsim:roketsim-core:0.1.0; sources/Javadoc ve Maven metadata üretilir.
JDK17 toolchain/release, UTF-8, reproducible archive order/timestamps ve pinned
JUnit5 test dependency kullanılır. Gradle Wrapper gerçek doğrulanmış Gradle8.12.1
distribution tarafından üretilir; wrapper JAR elle oluşturulmaz.
İlk bootstrap arşivi resmî indirme çok yavaş olduğu için Huawei distribution
aynasından alındı; resmî Gradle SHA-256 ile birebir eşleşmeden çalıştırılmadı.
Wrapper distribution URL'si services.gradle.org'dur; doğrulanmış arşiv yalnız
test ortamında cache'e kondu. Sistem-wide Gradle kurulmadı.
Gradle distribution resmî SHA-256:
`8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94`.
Üretilmiş Wrapper JAR resmî checksum'u:
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.

Bağımsız consumer ayrı Gradle build'idir; yalnız explicit coreJar dosyasını alır.
verification/python_parity.py yalnız developer V&V aracıdır, production JAR'a
ve runtime dependency graph'a girmez. Altı deterministic 2D/3D/4D case'in actual
Python çıktıları actual Java probe sonuçlarıyla karşılaştırılır.

## Doğrulama

Python reference: proje .venv, Python3.12.4, NumPy2.5.2, Windows11.
İlgili Python regression: 77 passed (2026-10-14).
Java: Temurin17.0.20.1+1, javac17.0.20.1. Eski Codex process PATH'i Java8'e
bakıyordu; test komutlarında yalnız process-local JAVA_HOME/PATH seçilir.
Sistem Java kurulumu değiştirilmez; kurulum yolu build dosyalarına gömülmez.
Wrapper offline clean build: başarılı; Java suite 25 tests, 0 failures/errors/skipped.
Gerçek Python/Java parity: 6 deterministic cases passed. Bağımsız consumer build/run:
INDEPENDENT_CONSUMER: PASS. runtimeClasspath: No dependencies; jdeps: java.base.
JUnit artifact kilitleri gradle.lockfile'da, SHA-256 verification metadata Gradle
tarafından üretilmiştir. Javadoc eksik public açıklama uyarıları build'i engellemez.

## Yeniden çalıştırma

JDK17 seçilmiş developer terminalinde roketsim-core dizininden:

```powershell
.\gradlew.bat clean build
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat -p verification/consumer run "-PcoreJar=$PWD\build\libs\roketsim-core-0.1.0.jar"
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/python_parity.py --java "$env:JAVA_HOME\bin\java.exe"
```

İki bağımsız jar task çalıştırmasının SHA-256 eşitliği kontrol edilerek arşiv
tekrar üretilebilirliği doğrulandı. Son binary JAR SHA-256:
`13fe2dd00aea6424f355818baebcc9ec66e390c08f148b31fed74f92bfb54fde`.
İlk dependency/bootstrap hazırlığından sonra
Java testleri ve consumer --offline ile çalıştırılmıştır. Bu developer dependency
edinimi Java Core'a network runtime bağımlılığı eklemez.

## Sınırlar

Quaternion J-002'ye aittir. Atmosphere, vehicle, mass, propulsion, aero, dynamics,
RK4, simulation ve frontend adapter uygulanmamıştır. License/publishing ve tüm
keyfi-boyut/platformlar için BLAS summation parity ileri migration denetimidir.
