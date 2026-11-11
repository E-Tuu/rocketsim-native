# NAT-025B — Structural & Motor Inertia Models

## Kapsam ve başlangıç

2026-10-31. Revize frozen kapsam: **B.1 mevcut structural component inertia** ve **B.3 motor inertia**. **B.2 ertelendi**; PrimitiveInertiaCalculator, reusable primitive API, auxiliary geometry/hardware modelleri yoktur. NAT-025C vehicle aggregation/integration ayrı görevdir ve başlatılmadı.

Başlangıç dalı `development/nat-025a-rigid-body-inertia-foundations`; exact temiz HEAD **`0aa3e9b70a13b229ebed3933824ce93916ecb3d7`**. NAT-025 `e188790ff271ea04ddb52d3a87c4b905a78f76d2`, NAT-024, NAT-023 ve J-011 ancestry içinde doğrulandı. İstenen yeni yerel dal: `development/nat-025b-component-inertia-models`. Accepted yerel SHA'ların remote'da bulunduğu varsayılmadı; push/merge yapılmaz.

## Eklenen production dosyaları ve exact API

`org.roketsim.core.mass` altında yalnız:

```java
public record StructuralComponentInertias(
    InertiaTensor3D nose, InertiaTensor3D body, InertiaTensor3D fins,
    InertiaTensor3D motorMount, InertiaTensor3D centeringRings
) {}
public final class StructuralInertiaCalculator {
    public StructuralComponentInertias evaluate(
        ResolvedRocketGeometry geometry, StructuralMassProperties masses
    );
}
```

`org.roketsim.core.propulsion` altında yalnız:

```java
public enum MotorInertiaModelKind { EXPLICIT_CURVE, RADIAL_GRAIN, UNIFORM_CYLINDER }
public sealed interface MotorInertiaModel permits MotorInertiaCurveModel,
    RadialGrainMotorInertiaModel, UniformCylinderMotorInertiaModel {}
public record MotorInertiaSample(double motorTimeS, InertiaTensor3D inertiaAtMotorCg) {}
public record MotorInertiaCurveModel(List<MotorInertiaSample> samples, String sourceReference)
    implements MotorInertiaModel {}
public record RadialGrainMotorInertiaModel(
    double dryCgFromFrontM, double dryTransverseInertiaKgM2, double dryAxialInertiaKgM2,
    int grainCount, double grainLengthM, double grainSeparationM,
    double firstGrainFrontFromMotorFrontM, double grainDensityKgM3,
    double grainOuterRadiusM, double grainInitialInnerRadiusM,
    double initialMassToleranceKg, double motorCgToleranceM, String sourceReference
) implements MotorInertiaModel {}
public record UniformCylinderMotorInertiaModel(double motorCgToleranceM)
    implements MotorInertiaModel {}
public record MotorInertiaResult(InertiaTensor3D inertiaAtMotorCg,
    MotorInertiaModelKind modelKind, String provenanceReference) {}
public final class MotorInertiaEvaluator {
    public MotorInertiaResult evaluate(MotorDefinition motor,
        MotorMassProperties motorMassProperties, MotorCurveStatistics motorStatistics,
        double motorTimeS, MotorInertiaModel model);
}
```

Toplam **10 yeni production dosyası**. Helper'lar bu dosyaların içinde private/package-private'dır; yeni framework veya public primitive evaluator yoktur. Structural sonuçta beş non-null tensor vardır; vehicle toplamı yoktur. Evaluator'lar field/cache/history tutmaz. Tüm tensorlar SI kg*m² ve standart negatif-integral off-diagonal convention kullanır.

## Structural geometry/mass otoriteleri

`GeometryResolver` ve `ResolvedRocketGeometry.source()`; nose normal thickness, airframe diameter, body inner diameter, mount/ring layout, fin root/tip/signed sweep/thickness ve equal angular arrangement otoriteleridir. `StructuralMassProperties` içindeki beş component mass/CG sonucu kullanılır. Density'den yeni component mass hesaplanmaz. Hollow nose'da yalnız accepted mass/accepted material volume ile uniform density çıkarılır.

BODY X/Y transverse, +Z longitudinal/thrust eksenidir. `x_geo` nose-tip origin, aft-positive ayrı koordinattır. Yerel centered axial fark için açık dönüşüm `z_B=-(x_geo-x_component_CG)` kullanılır; bir global frame reinterpretation yapılmaz. Eksenel PAT'de işaret karesi aynı olduğundan axial offset'in işareti tensoru değiştirmez. General orientation subsystem eklenmedi.

### Solid ve normal-shell conical nose

Solid cone kendi CG'sinde:

```text
Iz = 3*m*R²/10
Ix = Iy = 3*m*R²/20 + 3*m*L²/80
```

Normal shell için accepted resolver geometrisi aynen kullanılır: slant=`hypot(L,R)`, inner tip offset=`t*slant/R`, inner length=`L-offset`, inner radius=`R-t*slant/L`. Inner ve outer apex ortak değildir. Outer CG=`noseStart+3L/4`; inner CG=`noseStart+offset+3Li/4`. `rho=m_shell/V_material` ile hypothetical solid masses bulunur. Her solid cone'un kendi centroidal tensoru, mevcut NAT-025A PAT ile **supplied net-shell CG** noktasına taşınır; inner contribution outer'dan çıkarılır. Net mass/CG yeniden hesaplanmaz; thin-shell veya rod yaklaşımı yoktur. Nonfinite/negative derived moments reddedilir, clamp uygulanmaz.

### Body, mount ve iki ring

Annular cylinder: `Iz=m*(Ro²+Ri²)/2`, `Ix=Iy=m*(3*(Ro²+Ri²)+L²)/12`. Body ve rocket-side mount kendi accepted mass ve dimensions'larını kullanır; mount motorla birleştirilmez. Ring pair mass iki eşit parçaya ayrılır; her ring kendi start/end midpoint'inde short-annulus tensoruna sahiptir. Her biri supplied pair CG'ye PAT ile taşınır ve toplanır. Axial separation transverse inertia'yı artırır; longitudinal inertia değişmez. Tek ring'e indirgeme veya structure/motor double-counting yoktur.

### Trapezoidal fins — volume integration

Yerel polygon `(q,u)` koordinatlarında vertices `(0,0),(root,0),(sweep+tip,span),(sweep,span)`; q aft-positive, u body surface'den outward radial mesafedir. q koordinatları accepted fin-set axial CG'ye merkezlenir. Shoelace polygon second integrals doğrudan `E[(q-qCG)²]`, `E[u²]`, `E[u]` verir. Kalınlık tangential yönde uniform `t∈[-thickness/2,+thickness/2]`, `E[t²]=thickness²/12`.

`Q=E[(q-qCG)²]`, `S=Rbody²+2*Rbody*E[u]+E[u²]+thickness²/12` için eşit angular spacing ve N>=3 tam set simetrisi:

```text
Ix = Iy = m_fin_set*(Q+S/2)
Iz = m_fin_set*S
Ixy = Ixz = Iyz = 0
```

Bu, bütün fin setini cylinder olarak yaklaşık modellemek değildir; bireysel uniform trapezoidal volume integrallerinin eşit-açılı toplamının exact sadeleşmesidir. Signed sweep, tip chord=0 triangular limit, radial offset ve finite thickness korunur. Point mass/rod/aero CP-CNa değerleri kullanılmaz. N>=3 için toplam mass zaten bütün setin accepted mass'ıdır; ikinci kez N ile çarpılmaz.

## Motor model seçimi ve provenance

Model explicit sealed input'tur. Curve > grain > uniform evidence sırası otomatik routing/fallback değildir. MotorDefinition'a alan eklenmez; mass/CG profile'ları değişmez. Current mass/CG doğrudan `MotorMassProperties`'ten alınır; thrust, impulse, burn-rate veya ignition hesaplanmaz. `motorTimeS` elapsed, finite ve nonnegative'dir. Statistics'in accepted curve end'i finite/positive olmalıdır.

### Explicit curve

En az iki non-null immutable sample; ilk time=0, finite/nonnegative ve strictly increasing times; nonblank source; `List.copyOf` snapshot. Final time en az `MotorCurveStatistics.curveEndTimeS()` kadar coverage sağlamalıdır. Exact node'da source tensoru, arada altı component için lineer interpolation, final sonrasında terminal hold. Source string aynen korunur; blank olmayan identifier bağımsız doğrulama/üretici sertifikası değildir. NAT-025A'nın finite/nonnegative-diagonal tensor contract'ı korunur; source tensorlar için tamamlanmış PSD/physical-realizability sertifikası iddia edilmez. Interpolation arithmetic overflow reddedilir.

### Radial-grain model

Bir veya daha çok identical coaxial annular grain; fixed axial positions/length/outer radius/density, inner-channel radial burn, inhibited outer/end faces. BATES end burn, star grains, motion/deformation veya F50 grain inference yoktur. Motor-local origin front, positive aft; rocket x_geo ile otomatik özdeşlik yoktur.

Constructor finite/dimension/radius/tolerance/source kontrollerini yapar. Dry axisymmetric moments nonnegative ve `I_transverse >= I_axial/2` olmalıdır. Evaluation; dry CG/whole grain stack'in motor length'e ve grain diameter'ın motor diameter'a sığmasını doğrular. Initial modeled mass `N*rho*PI*(Ro²-Ri0²)*length`, accepted propellant mass ile explicit `initialMassToleranceKg` kullanılarak karşılaştırılır. Accepted propellant mass değiştirilmez.

`dry=initialMass-propellantMass`; `remaining=suppliedMass-dry`; `Ri²=Ro²-remaining/(N*rho*PI*length)`. Negative remaining reddedilir; initial mass üstü veya initial grain geometry'ye göre fark ancak explicit source-consistency tolerance içinde kabul edilir. Bu tolerance küçük source/rounding farkını sınıflandırır; değerler clamp edilmez/değiştirilmez. Derived radius square yine [0,Ro²] içinde olmalıdır. Gizli mass/radius tolerance yoktur.

Dry ve grain location'larından combined CG hesaplanıp **supplied local motor CG** ile explicit `motorCgToleranceM` üzerinden karşılaştırılır; accepted CG asla değiştirilmez. Her grain inertia ve dry inertia existing PAT ile bu accepted reference'a taşınır. Remaining=0 iken zero-mass grains atlanır; exact dry-CG eşleşmesinde sonuç dry tensorudur. Yakın ama explicit tolerance içindeki CG farkında PAT reference offset'i korunur. Sonuçta mass/CG duplicate authority saklanmaz. Stateless evaluator önceki çağrıyla mass-monotonicity karşılaştırmaz; consistency mevcut mass'ın initial/dry sınırlarıyla kontrolüdür.

### Uniform-cylinder approximation

Explicit `motorCgToleranceM` finite/nonnegative. `R=diameter/2`, `L=length`, `m=supplied current mass`; centroidal `Iz=mR²/2`, `Ix=Iy=m*(3R²+L²)/12`. Motor local CG, L/2 ile explicit tolerance içinde tutarlı olmalıdır. Silindir fiziksel midpoint'inden sessizce kaydırılmaz: kabul edilen küçük reference-CG farkında NAT-025A PAT ile accepted motor-CG noktasına referanslanır. Exact midpoint'te analitik centroidal formül aynen kalır. Bu sınır kontrolü gerçek mass-distribution CG'sinin ölçülmüş kanıtı değildir.

Provenance exact identifier: `MODEL-APPROXIMATION: uniform homogeneous motor cylinder`. Gerçek grain regression veya manufacturer source iddiası yoktur. Son code review'da bu reference-point farkı için açık PAT ve regression assertion eklendi; son clean build/test ve trajectory checker bu son kodla tekrarlandı. Physics oracle/fixture/tolerance değiştirilmedi.

## Bağımsız V&V kanıtı

`StructuralInertiaTest` ve `MotorInertiaTest` yeni testlerdir; historical testler değiştirilmedi. Structural comparison, mevcut GeometryResolverTest convention'ı **3e-14** ile yapılır; trig/quadrature reference'ın sıfıra cancellation residual'ı ilgili diagonal scale üzerinden aynı convention'la kontrol edilir. Yeni production tolerance yoktur. Source nodes, records, symmetry ve exact kaynak kimlikleri exact karşılaştırılır.

| Test | Bağımsız kanıt |
|---|---|
| Solid cone | L=.3,R=.05 için Ix/m=.00375, Iz/m=.00075 |
| Hollow nose | Normal thickness .002/.01; outer/inner cross-section integralleri gerçek shifted-tip boundary'de iki parçaya ayrılır; 3-point Gauss integration polynomial degree<=5 için exact, floating arithmetic hariç |
| Body/mount | Kabul edilmiş nominal radii/lengths ve supplied component mass ile bağımsız annular formulas |
| Ring pair | Iz yalnız radial moments; Ix'te ek `m_pair*((.12-.003)/2)²` separation katkısı |
| Fins | N=3/4/5/8 × sweep=-.04/+.05 × tip=0/.08 = **16 geometry case**; her finin span/chord/thickness hacmi bağımsız 3-node Gauss ve actual angular rotation ile entegre edilir; altı tensor component kontrol edilir |
| Fin thickness/radial | Sabit supplied mass ile thickness .003→.006 için Iz increment=`m*(.006²-.003²)/12`; transverse bunun yarısı; larger radial offset Iz'yi artırır |
| Structural mass authority | Supplied fin mass iki katına çıkarıldığında inertia iki kat, diğer component'ler aynı; density yeniden kullanılmaz |
| Curve | İki kaynak tensorun exact node/terminal identity'si ve altı interpolated term; caller-list mutation, invalid grids/source/coverage, nonfinite time |
| Two grains | Sentetik m_dry=.2, dryCG=.15, dry Ix=.001/Iz=.00002; N=2, Lg=.04, gap=.08, first front=.05, rho=1000, Ro=.02,Ri0=.01; grain CG'leri .07/.19 |
| Half burn | Remaining=initial/2; bağımsız Ri²=.00025, Ri=.015811388300841896; annular grain moments ve iki ayrı axial offset sum'ı kontrol edilir |
| Burnout | Remaining=0 ve accepted CG=dryCG → exact dry tensor (.001,.001,.00002,0,0,0); no zero-mass PAT call |
| Single grain | Aynı initial propellant volume, Lg=.08, center=.15; tek-grain boundary ve source curve exact coverage endpoint kontrolü |
| Uniform | F50 dimensions/supplied current mass, mass scaling ve exact midpoint; allowed offset için PAT reference increment; dış CG mismatch ret |

Initial grain test fixture kütlesi `2*1000*PI*(.02²-.01²)*.04` olarak bağımsız kurulmuştur; F50 ölçümü değildir. Testteki explicit mass/CG consistency toleransları caller input'tur, production default değildir.

Negatif testler null required references/tensors/list entries, NaN/±Infinity, zero/negative dimensions, radius order, grainCount, dry inertia admissibility, missing sources, source coverage, placement, mass/CG mismatch ve derived overflow içerir. Constructor/evaluator hataları normal IllegalArgumentException; başka modele fallback veya yeni exception hierarchy yoktur. Numeric geometry/mass inputs accepted authority assumption'ıyla tüketilir; yeni GeometryResolver veya MotorPropertyEvaluator yaratılmaz.

## Gerçek build ve seçici regression

Windows 11 amd64, Temurin java/javac **17.0.20.1**, runtime **17.0.20.1+1**, Gradle Wrapper **8.12.1**; Python trajectory reference **3.12.4 / NumPy 2.5.2**. Core dizininden:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir '<build-dir>' clean assemble test --tests org.roketsim.core.StructuralInertiaTest --tests org.roketsim.core.MotorInertiaTest --tests org.roketsim.core.InertiaTensorFoundationsTest --tests org.roketsim.core.StructuralMassTest --tests org.roketsim.core.RocketMassTest --tests org.roketsim.core.AuxiliaryMassTest --tests org.roketsim.core.SingleStageVehicleConfigurationTest --tests org.roketsim.core.FlightPhaseClockTest --tests org.roketsim.core.PhysicsEvaluatorTest --tests org.roketsim.core.SimulationEngineTest --tests org.roketsim.core.NativeDemoTrajectoryTest --tests org.roketsim.core.GeometryModelsTest --tests org.roketsim.core.GeometryResolverTest --tests org.roketsim.core.MotorPropertiesTest --tests org.roketsim.core.MotorCatalogTest --tests org.roketsim.core.MotorInstallationTest --tests org.roketsim.core.MotorThrustTest verifyProductionDependencies --offline --no-daemon
```

İlk focused koşu 7 structural+10 motor test ile exit 0 geçti. Son üç test eklenip yukarıdaki tam seçici koşu çalıştırıldı; ardından son uniform reference-point kontrolü sonrası aynı clean komut tekrarlandı. **Nihai XML sonucu**:

| Suite | Passed |
|---|---:|
| StructuralInertiaTest / MotorInertiaTest | 8 / 12 |
| InertiaTensorFoundationsTest (NAT-025A) | 14 |
| StructuralMassTest / RocketMassTest | 23 / 7 |
| AuxiliaryMassTest / SingleStageVehicleConfigurationTest / FlightPhaseClockTest | 26 / 18 / 14 |
| GeometryModelsTest / GeometryResolverTest | 3 / 31 |
| MotorCatalogTest / MotorInstallationTest / MotorPropertiesTest / MotorThrustTest | 12 / 9 / 7 / 7 |
| PhysicsEvaluatorTest / SimulationEngineTest / NativeDemoTrajectoryTest | 13 / 5 / 9 |
| **Toplam** | **218** |

**20 yeni + 198 mevcut, 0 failure/error/skipped; exit 0.** Clean Java17 compile/JAR/sources/Javadoc ve verifyProductionDependencies başarılı. Javadoc mevcut gösterim limitinde 100 nonblocking warning verdi; tam warning envanteri değildir. Packaged JAR javap API'leri doğruladı; jdeps `--print-module-deps` yalnız **java.base**, exit 0. Bağımlılık/build ayarları değiştirilmedi. Genel repository model audit/full test kampanyası yapılmadı.

## Gerçek J-011 trajectory parity

Değiştirilmemiş checker son build üzerinde çalıştırıldı:

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS, exit 0.** Son production değişikliğinden sonra gerçekten tekrarlandı: 3 run / 2 distinct configuration; primary/repeat 1584, fine 3167 sample; her birinde 3 event. Distinct primary+fine 442261 numeric comparison, 442225 exact, 36 accepted nonexact; repeat dahil 46 nonexact. Max absolute fark 8.881784197001252e-16, max relative 4.3829501452845633e-16: önceki kabul zincirindeki upstream son-bit farkları mevcut quantity-specific toleranslar içinde. Yeni trajectory anchor veya tolerance yoktur; evrensel bitwise eşitlik iddia edilmez. Bu regression eski flight yolunu doğrular; yeni inertia modelleri için Python migration/parity oracle'ı iddia edilmez.

Loglar `%TEMP%/roketsim-nat025b-focused.log`, `roketsim-nat025b-regression.log`, `roketsim-nat025b-final.log`, `roketsim-nat025b-trajectory-final.log`; build/XML/trajectory traces TEMP altındadır ve repository'ye eklenmez.

## Koruma, kalan sınırlar ve Git

Source curve provenance claimed input'tur; full physical realizability independently certified değildir. Radial-grain model internal-only annular regression ile sınırlıdır; gerçek motor grain geometry'si bilinmeden seçilemez. Uniform cylinder görünür approximation'dır. Component orientation/auxiliary geometry, vehicle tensor aggregation, changing motor mass/CG model, dynamics, angular acceleration/attitude, events ve simulation integration yoktur. Mevcut 3DOF hiçbir yeni tensoru tüketmez.

Nihai kapsam: 10 yeni production dosyası, iki focused test ve bu kayıt. Accepted Java/Python sources, test/oracle'lar, motor catalog, NAT-025A, ADR'ler ve geçmiş verification kayıtları başlangıç SHA'sına göre değiştirilmedi; tracked diff boş, yalnız bu 13 yeni dosya mevcut. Yeni model/formül veya eski API değişikliği gerektiren bir blocker gözlenmedi. Bu dosyayı ekleyen `feat(inertia): add NAT-025B structural and motor inertia models` yerel commit acceptance kimliğidir; exact SHA final raporda ve repository kökünden `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-025b-component-inertia-models.md` ile bulunur. Self-referential SHA için amend yapılmaz. Commit sonrası clean worktree final raporda doğrulanır. Push/merge yoktur, NAT-025C başlatılmaz.
