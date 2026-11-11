# NAT-025A — Rigid-Body Inertia Foundations

## Kapsam ve Git başlangıcı

2026-10-31: başlangıç dalı `development/nat-025-flight-phase-propulsion-clock`, exact HEAD `e188790ff271ea04ddb52d3a87c4b905a78f76d2`, temiz çalışma ağacı doğrulandı. NAT-024 `9768e3f59f6f552c989da897bb28dff1ad269228`, NAT-023 `2fadedf78a4117e742f901073461c569a637d30a` ve J-011 `480f64840b92de87ee70a30a299fdb6ab169e0ef` geçmişte korunur. Yeni yerel dal: `development/nat-025a-rigid-body-inertia-foundations`.

Yalnız iki production tipi, bir focused test ve bu kayıt eklenir. Mevcut üretim/test/ADR/kabul kayıtları değiştirilmez. Bu bağımsız matematik yeteneği active 3DOF engine'e bağlanmaz; rotational dynamics değildir.

## Exact public API

`org.roketsim.core.mass`:

```java
public record InertiaTensor3D(
    double ixxKgM2, double iyyKgM2, double izzKgM2,
    double ixyKgM2, double ixzKgM2, double iyzKgM2
) {}

public final class ParallelAxisInertiaCalculator {
    public InertiaTensor3D shiftFromCentroid(
        InertiaTensor3D centroidalInertia, double massKg, Vector displacementBodyM
    );
}
```

Record compact canonical constructor ile doğrulanır. Altı scalar field, simetrik 3×3 tensorun bağımsız girdileridir: satırlar `[Ixx,Ixy,Ixz]`, `[Ixy,Iyy,Iyz]`, `[Ixz,Iyz,Izz]`. SI birimi kg*m². Off-diagonal işareti tensor işaretidir: `Ixy=-integral(x*y dm)`; pozitif ham product of inertia olarak yorumlanmaz.

BODY X/Y transverse, +Z longitudinal/thrust/roll eksenidir. Girdi tensor ve metre cinsindeki displacement aynı BODY-aligned eksenlerde olmalıdır. Nose-tip, aft-positive `x_geo` ayrı koordinattır; otomatik BODY Z eşlemesi yoktur. Referans noktası caller sözleşmesindedir, record'da ayrıca saklanmaz. WORLD frame'e rotation uygulanmaz.

## Matematik ve validation

`I_ref = I_centroid + m*((d dot d)*Identity - d*d^T)` doğrudan altı bileşenle uygulanır:

```text
Ixx_ref = Ixx + m*(dy*dy + dz*dz)
Iyy_ref = Iyy + m*(dx*dx + dz*dz)
Izz_ref = Izz + m*(dx*dx + dy*dy)
Ixy_ref = Ixy - m*dx*dy
Ixz_ref = Ixz - m*dx*dz
Iyz_ref = Iyz - m*dy*dz
```

Yeni mass/CG, orientation, matrix altyapısı veya aggregation yoktur. Calculator stateless; alan/cache/history veya input mutation yoktur. Sonuç yeni record'dur; mevcut immutable `Vector` tekrar kullanılır.

Constructor declaration order'da her component için finite kontrolü, ilk üç component için ayrıca negatif olmama kontrolü yapar. Sıfır ve negatif sıfır kabul edilir; finite off-diagonal değerlerin işareti serbesttir. Mevcut `NumericalValidation.requireFinite` ve normal `IllegalArgumentException` kullanılır; mesajlar reddedilen exact field adını içerir. PSD/triangle inequality/physical realizability sertifikası verilmez; fiziksel olmayan ama bu dar giriş sözleşmesini karşılayan tensorlar bilinçli olarak reddedilmez veya projekte edilmez.

Calculator sırası: null centroid → finite mass → mass>0 → null displacement → size=3 → altı dönüşüm → finite-derived kontrolü → validated record. Vector zaten finite bileşenleri garantiler; global vector policy tekrarlanmaz/değiştirilmez. Expanded işlemlerde oluşan infinity/NaN reddedilir. Örneğin küçük mass ile çarpılmadan önce overflow olan kare işlemi de reddedilir; alternatif arithmetic/clipping/fallback ile sessizce devam edilmez. Yeni epsilon veya production tolerance yoktur.

## Bağımsız analitik V&V

Beklenen fixture'lar testte doğrudan belirtilir; production calculator'dan beklenen sonuç türetilmez.

| Kontrol | Girdi ve bağımsız beklenti |
|---|---|
| Genel 3D | centroid diag(1,2,3), m=2, d=(1,2,3) → diag(27,22,13), offdiag(-4,-6,-12), exact |
| Axial ±Z | m=2, d=(0,0,±0.3) → ΔIxx=ΔIyy=0.18, ΔIzz=0; off-diagonaller değişmez |
| Zero shift | supplied altı bileşen aynı, yeni bağımsız record |
| Full-vector sign | d=(0.4,-0.6,0.8) ve -d aynı sonucu verir; tek eksen işareti farklı off-diagonal verebilir |
| Point mass | centroid zero, m=2, d=(1,2,3) → (26,20,10,-4,-6,-12), exact |
| Thin rod | BODY Z, m=2, L=3; centroid (1.5,1.5,0); endpoint shift z=1.5 → (6,6,0), exact |
| Hollow cylinder | m=2, L=1, Ro=0.10, Ri=0.08; centroid transverse≈0.17486666666666667, axial=0.0164; endpoint z=0.5 → transverse≈0.6748666666666667, axial değişmez |
| Coordinate-origin translation | centroid ve reference koordinatlarına aynı offset eklemek d'yi ve sonucu değiştirmez |

Rod referansı `m*L²/12`; annular cylinder axial momenti `m*(Ro²+Ri²)/2`, transverse momenti `m*(3*(Ro²+Ri²)+L²)/12` bağımsız analitik fixture açıklamalarıdır, production geometry evaluator değildir. Rounded decimal axial/cylinder beklentileri yalnız ilgili expected scalar'ın **bir ULP** aralığıyla karşılaştırılır; ondalık temsil ve son toplama rounding'i için yerel analitik gerekçedir. Diğer exact fixtures, invariance ve non-shifted bileşenler exact kontrol edilir. Global tolerance veya historical tolerance değiştirilmedi.

Negative coverage: altı component'in her birinde NaN/±Infinity; üç diagonal için negatif değer; valid signed off-diagonals ve zero; constructor error precedence; null tensor/vector; 0/negative/NaN/±Infinity mass; 0/2/4 boyutlu vector; vector'ın mevcut finite-error yolu; square/product/addition overflow; off-diagonal overflow; caller array/exported array mutation isolation; repeated/fresh calculator sonucu. En küçük pozitif mass zero displacement ile geçerlidir. Test, fiziksel admissibility kapsamı dışında bir tensorun kabulünü de açıkça doğrular.

## Gerçek seçici test ve build kanıtı

Ortam: Windows 11 amd64, Temurin java/javac **17.0.20.1**, runtime **17.0.20.1+1**; Gradle Wrapper **8.12.1**. Mevcut checksum/lock/configuration değiştirilmedi. Core dizininden:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir '<build-dir>' clean assemble test --tests org.roketsim.core.InertiaTensorFoundationsTest --tests org.roketsim.core.StructuralMassTest --tests org.roketsim.core.RocketMassTest --tests org.roketsim.core.AuxiliaryMassTest --tests org.roketsim.core.SingleStageVehicleConfigurationTest --tests org.roketsim.core.FlightPhaseClockTest --tests org.roketsim.core.PhysicsEvaluatorTest --tests org.roketsim.core.SimulationEngineTest --tests org.roketsim.core.NativeDemoTrajectoryTest --tests org.roketsim.core.GeometryModelsTest --tests org.roketsim.core.GeometryResolverTest verifyProductionDependencies --offline --no-daemon
```

Önce aynı isolated `test --tests org.roketsim.core.InertiaTensorFoundationsTest` focused koşusu: **14 passed / exit 0**. Son clean build ve seçilen regression sonuçları XML'den sayıldı:

| Suite | Passed |
|---|---:|
| InertiaTensorFoundationsTest | 14 |
| StructuralMassTest / RocketMassTest | 23 / 7 |
| AuxiliaryMassTest (NAT-023) | 26 |
| SingleStageVehicleConfigurationTest (NAT-024) | 18 |
| FlightPhaseClockTest (NAT-025) | 14 |
| PhysicsEvaluatorTest / SimulationEngineTest | 13 / 5 |
| NativeDemoTrajectoryTest | 9 |
| GeometryModelsTest / GeometryResolverTest | 3 / 31 |
| **Toplam** | **163** |

**14 yeni + 149 mevcut test; 0 failure/error/skipped, exit 0.** IN25A-T01–T15 focused schema/analytic/negative/integrity kontrolleriyle; T16 eski mass/geometry/simulation testleri ve trajectory checker ile kapsanır. Full repository audit veya full regression kampanyası yapılmadı.

Java17 clean compile, JAR/sources/Javadoc ve verifyProductionDependencies başarılı, exit 0. Javadoc mevcut gösterim limitinde 100 nonblocking warning verdi; tam warning sayımı iddiası değildir. Packaged JAR `javap` exact altı component constructor/accessor ve tek calculator metodunu doğruladı; `jdeps --print-module-deps` yalnız **java.base**. İlk kısıtlı araç çağrısında TEMP JAR/Python erişim hatası görüldü; uygun erişimle aynı inspection/version komutları kaynak değişmeden tekrarlandı ve exit 0 tamamlandı.

## Gerçek J-011 trajectory parity

Değiştirilmemiş checker ve gerçek Python **3.12.4 / NumPy 2.5.2** kullanıldı:

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS / exit 0**. 3 run / 2 distinct configuration. Primary/repeat 1584 sample, fine 3167 sample; her koşuda 3 event. Distinct primary+fine **442261 numeric comparison, 442225 exact, 36 accepted nonexact**; repeat dahil 46 nonexact. Max absolute fark **8.881784197001252e-16**, max relative **4.3829501452845633e-16**: kabul edilmiş upstream son-bit farkları, mevcut quantity-specific toleranslar içinde. Yeni anchor veya tolerance yoktur; evrensel bitwise eşitlik iddia edilmez. Yeni inertia calculator bu flight yolunda kullanılmaz.

Loglar `%TEMP%/roketsim-nat025a-focused.log`, `roketsim-nat025a-regression.log`, `roketsim-nat025a-trajectory.log`; JUnit XML/build ve trajectory JSON/trace'leri yukarıdaki TEMP dizinlerindedir, commit'e eklenmez.

## Sınırlar ve handoff

Tensor BODY-aligned matematiksel değerdir; WORLD rotational state veya fiziksel mass-distribution kanıtı değildir. Geometri, component mass, unknown auxiliary inertia, burning motor internal distribution, torque, angular acceleration/velocity ve quaternion propagation hesaplanmaz. Mass ve CG aynı olan iki cisim farklı inertia taşıyabilir.

Component orientation ve centerline dışı konum açık sonraki modellemeyi gerektirir. Tensorun BODY eksenlerine rotation'ı Parallel Axis Theorem'den ayrıdır. NAT-025B desteklenen component geometry ve açık auxiliary dimensions/motor inertia model seçimlerini; NAT-025C mevcut mass/CG otoritesiyle vehicle aggregation/integration'ı ele alacaktır. Bu görevde ikisi de uygulanmadı. Angular dynamics gelecekteki 6DOF kapsamıdır.

Nihai scope: iki production dosyası, `InertiaTensorFoundationsTest.java` ve bu kayıt. Mevcut Vector/Vectors/NumericalValidation/frame/quaternion, mass/CG, propulsion, physics, engine, events, RK4, configuration ve result şemaları değişmedi. Yeni runtime dependency yoktur; çözümlenmemiş implementation blocker yoktur.

Nihai yerel commit bu dosyayı ekleyen `feat(mass): add NAT-025A inertia tensor foundations` commit'idir; exact SHA final raporda ve repository kökünden `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-025a-rigid-body-inertia-foundations.md` ile bulunur. Self-referential SHA için amend yapılmaz. Push/merge ve main/historical branch değişikliği yapılmaz.
