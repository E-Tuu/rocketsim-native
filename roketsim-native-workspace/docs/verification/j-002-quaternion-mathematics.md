# J-002 — Java Quaternion Mathematics Migration

## Kaynak ve Git sınırı

J-001 finalization yeniden doğrulandı: Java25, Python77, vector parity6,
independent consumer ve java.base bağımsızlığı geçti. J-001 local commit:
`6bbe1387c2f1d5308bde694385a3182d45982fb4`.
J-002 dalı `migration/j-002-quaternion`, bu temiz commit'ten açıldı.
Python quaternion.py, test_quaternion.py ve validation/test_math_foundation.py
tek migration otoritesidir. OpenRocket implementation kullanılmadı.
ADR-001/003/007 ve önceki verification kayıtları değiştirilmedi.

## API eşlemesi

| Kabul edilmiş Python | Java |
|---|---|
| as_quaternion(value, name=...) | Quaternion.of(input[, name]) |
| quaternion_norm(q) | Quaternions.norm(q) |
| normalize_quaternion(q, atol=...) | Quaternions.normalize(q, atol) |
| conjugate(q) | Quaternions.conjugate(q) |
| multiply(q1, q2) | Quaternions.multiply(left, right) |
| inverse(q, atol=...) | Quaternions.inverse(q, atol) |
| from_axis_angle(axis, angle_rad, atol=...) | Quaternions.fromAxisAngle(axis, angleRad, atol) |
| rotate_body_to_world(q, v, atol=...) | Quaternions.rotateBodyToWorld(q, vectorBody, atol) |
| rotate_world_to_body(q, v, atol=...) | Quaternions.rotateWorldToBody(q, vectorWorld, atol) |
| to_rotation_matrix(q, atol=...) | Quaternions.toRotationMatrix(q, atol) |
| apply_increment_left(old, delta, atol=...) | Quaternions.applyIncrementLeft(old, delta, atol) |

Quaternion immutable record olarak dört finite double tutar. of() J-001 Vector
construction/shape doğrulamasını kullanır; as_quaternion gibi exactly dört bileşen
ister. toArray() her çağrıda independent array verir. Matrix derived double[3][3]
olarak her çağrıda yeni satırlar taşır; iç state array'i veya ikinci matrix authority
yoktur. J-001 vector/numerical/frame/SI behavior değiştirilmemiştir.

## Convention ve doğrulama

Representation [w,x,y,z], scalar-first, q_BW BODY→WORLD'dür. WORLD ENU; BODY z_B
longitudinal/thrust/roll; geometry nose-tip x_geo ayrı kalır. Angle radian'dır.
Hamilton operation order Python'daki left/right sırasıdır; multiply veya constructor
normalize etmez. Raw zero quaternion geçerli representation'dır.

normalize ve inverse zero/near-zero normu caller'ın explicit atol değeriyle reddeder.
Negative/non-finite atol generic validation hatasıdır; global/default tolerans yoktur.
Norm ve inverse squared-norm overflow/underflow kontrolü korunur; hypot veya repair
eklenmez. Finite/shape/math-domain ValueError Java IllegalArgumentException'a eşlenir.
J-001 helpers compatible olduğu yerde kullanılır.

fromAxisAngle önce angle finite validation, sonra axis normalize, sonra üç boyut
kontrolü yapar. Rotations orientation'ı Python gibi açıkça normalize eder; zero
orientation'a identity fallback yoktur. İşlem:
v_W=q_BW*v_B*inverse(q_BW); ters yön inverse(q_BW)*v_W*q_BW'dür.
applyIncrementLeft önce old ve delta'yı normalize eder, ardından
normalize(delta*old) üretir. q ile -q aynı orientation taşısa da raw data korunur.

## Test ve parity politikası

Java testleri Python fixture'larına traceable: input/shape/finite, identity,
normalize, conjugate, non-unit two-sided inverse, exact Hamilton fixture,
non-commutativity, altı right-hand quarter-turn, radian fixtures, üç eksenli
round-trip, norm preservation, q/-q ve scaled-q equivalence, matrix orthogonality/
determinant, matrix/quaternion parity, left increment ve input immutability.
Invalid atol tüm relevant işlemlerde parameterized denenir; exact threshold,
norm overflow/underflow, degenerate axes ve validation order ayrıca test edilir.

Python TEST_ATOL/VV_ATOL=1e-12 yalnız test acceptance'dır. Parity'de array
karşılaştırmaları mevcut V&V'nin atol=1e-12, rtol=0 politikasıyla; norm scalar'ları
mevcut pytest.approx ile; raw representation/conjugate/Hamilton çıktıları exact
numerical equality ile karşılaştırılır. Yeni threshold yoktur.

İlk Java test koşusundaki iki assertion defect'i düzeltildi: conjugate fixture
beklenen işaret typo'su ve JUnit'in +0/-0 ayrımı. Identity inverse testinde delta=0
NumPy exact numerical equality'sini korur; signed zero production'da değiştirilmedi.
Herhangi bir production denklem veya tolerance bu testler için değiştirilmedi.

verification/quaternion_parity.py tüm 11 actual Python fonksiyonunu Java probe ile
karşılaştırır; negative/domain case'lerde iki tarafın exception family ve alan
semantics'i kontrol edilir. Math ve StrictMath sin/cos sonuçları actual Python math
sonuçlarıyla ayrı karşılaştırılır. Math implementation seçimi değiştirilmez.

## Execution evidence

Platform Windows11; Python3.12.4 / NumPy2.5.2; Temurin JDK/javac17.0.20.1+1;
Gradle Wrapper8.12.1. JDK seçimi process-local'dır; build'e kurulum yolu yazılmaz.
Python quaternion unit + integrated V&V: 73 passed.
Python tüm math/unit/SI + math V&V regression: 150 passed.
Java17 offline build başarılı: FoundationTest25 + QuaternionTest62 = 87 passed,
0 failure/error/skipped. J-001 vector parity6 yeniden geçti.
Quaternion parity95 success + 53 error case eşleşti. Math/StrictMath sin/cos için
7 case geçti; Python'dan en büyük ölçülen fark 1.1102230246251565e-16 olup mevcut
VV_ATOL=1e-12 içindedir. Bu libm son-bit farkı için tolerance veya implementation
değiştirilmedi; bitwise eşitlik iddiası yoktur.
Max-finite norm negative case'inde NumPy overflow RuntimeWarning üretip accepted
ValueError verir; Java aynı invalid-result ailesiyle reddeder. Bu beklenen test
uyarısı bir successful result veya silent fallback değildir.
Bağımsız JAR consumer: INDEPENDENT_CONSUMER ve QUATERNION_CONSUMER PASS.
Gradle runtimeClasspath No dependencies; jdeps yalnız java.base.
Javadoc yalnız önceki J-001 kaynaklarının 22 missing-comment uyarısını verir;
J-002 public API Javadoc'u eklidir. Python production/test ve J-001 foundation
source'larının J-001 commit'ine göre diff'i boştur.

## Yeniden çalıştırma ve sınırlar

```powershell
.\gradlew.bat clean build
.\gradlew.bat dependencies --configuration runtimeClasspath
.\gradlew.bat -p verification/consumer clean run "-PcoreJar=$PWD\build\libs\roketsim-core-0.1.0.jar"
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/quaternion_parity.py --java "$env:JAVA_HOME\bin\java.exe"
```

Core production yalnız JDK gerektirir. Parity Python/probe araçları test-only'dır,
production JAR'a girmez. Consumer JAR'ı doğrudan kullanır, OpenRocket/Swing yoktur.
J-001'in 22 missing-Javadoc uyarısı contractual defect değildir; korunur.
Platformlar arası bitwise identity iddiası yoktur. Quaternion ODE, inertia,
angular-rate state/dynamics, integrator norm policy ve 6DOF bu gate'in kapsamı değildir.
QUAT-T04 long-integration norm preservation eski controlled kayıtta deferred kalır.
