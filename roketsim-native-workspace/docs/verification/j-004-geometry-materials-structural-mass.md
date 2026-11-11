# J-004 — Java Geometry, Materials ve Structural Mass Migration

## Kontrollü kaynak ve kalıcı Python politikası

Başlangıç Git root `OpenRocket`, temiz dal `migration/j-003-environment-relative-flow`,
HEAD `cc96f379c03d763bceab86193750854861041576` olarak doğrulandı. J-001
`6bbe1387c2f1d5308bde694385a3182d45982fb4` ve J-002
`7b9d65ea001cbfb54eb8bb7fcaa48f02429b5b45` ancestry kontrolü geçti.
Doğrudan bu HEAD'den `migration/j-004-geometry-materials-mass` dalı açıldı.
Main, uzak repository ve frontend değiştirilmedi.

Migration otoritesi `roketsim-native-engine/src/roketsim_native/` altındaki
`geometry/models.py`, `geometry/resolver.py`, `materials/models.py`,
`materials/catalog.py`, `mass/models.py`, `mass/structural.py` ve kabul edilmiş
testlerdir. `mass/total.py` taşınmadı. `tests/unit/geometry/`,
`tests/unit/materials/`, `tests/unit/mass/test_structural.py` ve
`test_mount_extension.py` doğrudan incelendi. Kontrollü vehicle-propulsion,
NAT-012A.0 geometri kaydı ve önceki Java doğrulamalarıyla sahiplik korundu.
ADR-007 ve tarihsel ADR'ler yeniden yazılmadı; OpenRocket hesabı kullanılmadı.

**Kalıcı proje kararı:** tam migration sonrasında Java tek aktif fizik motoru ve
yeni fizik geliştirme platformudur. Python engine, testler, sayısal baselines,
V&V ve tarihsel JSON/CLI artifacts kontrollü, versioned, read-only mühendislik
referansı olarak korunacaktır. Python gelecekte runtime fallback değildir.
J-004 bu dosyaları silmez, taşımaz, yeniden adlandırmaz veya değiştirmez;
Python archival restructuring bu pakette yapılmaz. Bugünkü Java migration'ının
tamamlanmış flight simulator olduğu iddia edilmez.

## Python → Java API eşlemesi

Java kaynak kökü `roketsim-core/src/main/java/org/roketsim/core/`.
Mevcut tek library içinde üç paket export edildi; yeni Gradle subproject yoktur.

| Python public contract | Java |
|---|---|
| geometry.models.ConicalNoseGeometry(length_m, construction_mode, wall_thickness_m) | geometry.ConicalNoseGeometry(lengthM, constructionMode, wallThicknessM) |
| CylindricalBodyGeometry(length_m, wall_thickness_m) | geometry.CylindricalBodyGeometry(lengthM, wallThicknessM) |
| TrapezoidalFinSetGeometry(fin_count, root_chord_m, tip_chord_m, semi_span_m, tip_leading_edge_offset_x_m, root_leading_edge_x_geo_m, thickness_m, cross_section, angular_arrangement) | geometry.TrapezoidalFinSetGeometry; aynı sıra, camelCase parametreler |
| MotorMountTubeGeometry(length_m, inner_diameter_m, wall_thickness_m, aft_recess_m) | geometry.MotorMountTubeGeometry(lengthM, innerDiameterM, wallThicknessM, aftRecessM) |
| CenteringRingPairGeometry(axial_thickness_m) | geometry.CenteringRingPairGeometry(axialThicknessM) |
| MotorAttachmentGeometry(mount_tube, centering_rings, motor_overhang_m) | geometry.MotorAttachmentGeometry(mountTube, centeringRings, motorOverhangM) |
| SingleStageRocketGeometry(airframe_diameter_m, nose, body, fins, motor_attachment, reference_geometry_policy) | geometry.SingleStageRocketGeometry; aynı sıra, camelCase |
| NoseConstructionMode | geometry.NoseConstructionMode: SOLID / HOLLOW_SHELL |
| ReferenceGeometryPolicy | geometry.ReferenceGeometryPolicy.MAXIMUM_DIAMETER; value()="maximum_diameter" |
| FinCrossSection | geometry.FinCrossSection.SQUARE; value()="square" |
| FinAngularArrangement | geometry.FinAngularArrangement.EQUALLY_SPACED; value()="equally_spaced" |
| GeometryResolver.resolve(*, rocket_geometry) | geometry.GeometryResolver.resolve(geometry) |
| ResolvedRocketGeometry | geometry.ResolvedRocketGeometry; **54 source alanının tamamı**, aynı sıra ve camelCase adlar |
| GeometryValidationError | geometry.GeometryValidationError; errorCode()/fieldName()/value() |
| materials.models.BulkMaterial(name, density_kg_m3) | materials.BulkMaterial(name, densityKgM3) |
| SingleStageRocketMaterials(nose, body, fins, motor_mount, centering_rings) | materials.SingleStageRocketMaterials; beş zorunlu atama |
| MaterialValidationError | materials.MaterialValidationError; errorCode()/fieldName()/value() |
| materials.catalog.CARDBOARD / POLYSTYRENE | materials.MaterialCatalog.CARDBOARD / POLYSTYRENE |
| mass.models.ComponentMassProperties(material, mass_kg, cg_x_geo_m) | mass.ComponentMassProperties(material, massKg, cgXGeoM) |
| StructuralMassProperties(nose, body, fins, motor_mount, centering_rings, structure_mass_kg, structure_cg_x_geo_m) | mass.StructuralMassProperties; beş katkı ve iki aggregate |
| StructuralMassPropertiesCalculator.evaluate(*, resolved_geometry, materials) | mass.StructuralMassPropertiesCalculator.evaluate(geometry, materials) |
| MassValidationError | mass.MassValidationError; errorCode()/fieldName()/value() |

Model/result nesneleri immutable record'dur; iç array/list yoktur. Ham geometry
constructor'larına yeni domain validation taşınmadı. Python'daki raw selector ve
count validation zamanlamasını korumak için bu alanlar Java'da `Object` taşır:
resolver enum instance'ını ve integral count tipini doğrular. String "SOLID" veya
"square" sessizce enum yapılmaz; 4.0 ve bool count reddedilir. Geçerli girdiler
immutable enum/integer değerleridir. Java integral wrapper'ları ve BigInteger,
Python int'e karşılık gelir; arbitrary count upper bound eklenmez. Double'a
çevrilemeyen büyük pozitif integer'ın Python OverflowError'ı Java ArithmeticException
ile aynı işlem noktasında temsil edilir; negatif büyük integer semantic hatadır.

Solid nose için `wallThicknessM=null`, shell için explicit positive kalınlık
gerekir. Typed nested component/material eksiklikleri default ile tamamlanmaz;
constructor argüman eksikliği compile-time, null component kullanımı programming
contract hatasıdır. Python raw assignment dataclass'ı gibi material assignment'a
ayrı bir null/domain framework eklenmemiştir.

## Koordinat, şekil, yerleşim ve otorite

- SI korunur. Nose tip x_geo=0; +x_geo nose→tail. BODY longitudinal z_B ve WORLD
  ENU ile otomatik özdeşlik veya transform eklenmez. Tek dış çap otoritesi
  `airframeDiameterM` nose base/body OD içindir; ikinci component çap kopyası yoktur.
- Body nose base'inde başlar. Fin root chord tamamen body üzerinde kalır; sınır
  eşitlikleri geçerlidir. Tip tail'i geçebilir, nose origin önüne geçemez.
  Triangular tip chord=0 geçerlidir; signed LE offset ileri sweep'i de korur.
- Nose solid cone veya lateral yüzeye normal kalınlıklı benzer inner-cone
  void çıkarımıdır. Shell thickness limit eşitliği reddedilir, bir temsil edilebilir
  adım altı ek clearance olmadan denenir. Shell centroid'i outer-minus-inner
  weighted volume hesabıdır; material CG veya aerodynamic CP hesabı değildir.
- Body hollow annulus'tur; radial thickness sıfırdan büyük ve radius'tan küçüktür.
  Finler uniform solid plate'tir; hacim per-fin trapezoid alanı × adet × kalınlıktır.
  Zero/negative veya cancellation ile sıfır volume onarılmaz.
- Mount iç/eş eksenli hollow tube'dur; outer diameter = inner diameter + 2*wall.
  Mount OD body ID'den strict küçüktür. Recess nonnegative; mount body içinde kalır.
  Tam iki identical annular ring mount'un iki ucundadır. 2*t=L dokunma sınırı
  geçerli, 2*t>L overlap hatasıdır. Radial boyutları resolver body/mount'tan türetir.
- Signed motor overhang yalnız rocket-side aft reference konumudur; pozitif aft,
  zero flush ve negatif recessed geçerlidir. Referans mount start önüne geçemez.
  Motor identity/fit, kütle, thrust veya runtime timeline bu geometri değildir.
- `overallLengthM` dış body/fin extent'i; `aerodynamicLengthM` aynı structural extent'i
  tüketir. Motor aft reference bu uzunlukları uzatmaz. MAXIMUM_DIAMETER policy
  yalnız tek çaplı dış airframe'i kullanır; fins/internal hardware referansa katılmaz.
  D_ref=L_ref=D ve A_ref=(pi/4)*D*D işlem sırasıyla korunur.
- Nose/body wetted/frontal alanları, fin planform/MAC/LE ve mid-chord sweep,
  MAC spanwise y/LE placement, Extended-Barrowman geometric aspect ratio ve local
  root body radius aynı resolver'dadır. Katsayı, CP, CNa, force veya moment taşınmadı.

Validation source sırası korunur: policy/cross-section/arrangement → finite dimensions
→ positive domain/count → axial layout/reference area → nose/body construction
ve üç volume/centroid → mount/ring dimensions/relations/volume/centroid → derived
aero geometry invariants. İlk domain sorunu sonraki raw finite input hatasını
gizlemek için erken raise edilmez; ilgili source finite pass'leri korunur.

## Malzeme ve structural mass sözleşmesi

Catalog source'u kabul edilmiş `materials/catalog.py`'dır: CARDBOARD adı
**Cardboard**, density **680 kg/m³**; POLYSTYRENE adı **Polystyrene**, density
**1050 kg/m³**. Bunlar frozen catalogue/source verisidir; yeni density araştırması
veya universal material sabiti iddiası değildir. Catalog component default'u olmaz.
Kullanıcı ayrıca explicit finite positive density'li BulkMaterial verebilir.
Name trim edilmez; yalnız boş/whitespace ad reddedilir. Python str.strip Unicode
semantiği NBSP/NEL/figure-space/narrow-NBSP dahil test edilir. Validation önce
finite density, sonra positive density, sonra name'dir. Finish/roughness çıkarılmaz.

Her component mass = supplied density × **resolved material volume**; CG doğrudan
resolved volume centroid'tir. Raw geometry yeniden çözülmez. Material nesnesinin
identity/provenance referansı ComponentMassProperties'te korunur.

Toplama sırası **nose + body + fins + motor_mount + centering_rings** olarak
left-associated kalır; compensated sum veya alternatif aggregation eklenmez.
Weighted moment aynı sıradadır ve aynı mass toplamına bölünür. Ring katkısı
zaten iki ring'in birleşik hacmidir; tekrar ikiyle çarpılmaz. Structure total
finite positive olmalı; weighted CG finite ve [0, overallLengthM] içinde olmalıdır.
İndividual finite centroid'e yeni extent testi eklenmedi: source aggregate
extent kuralını uygular. Bu model inertia veya motor içeren total rocket mass değildir.

Raw nonfinite input generic ValueError → Java IllegalArgumentException'dır.
Geometry/Material/MassValidationError code, field ve value metadata'sını korur.
Source'ta computed nonfinite volume'un generic veya structured oluşu hangi
check'ten geçtiğine bağlıdır ve aynı sıra korunur. Mass calculator'daki derived
nonfinite mass/CG ise structured MassValidationError'dır; generic'e dönüştürülmez.
Clamp, abs repair, epsilon, yeni clearance veya hidden default yoktur.

## Ara nicelik ve parity kanıtı

`verification/vehicle_parity.py` actual Python public API'lerini çağırır;
`VehicleParityProbe` yalnız test source set'indedir. Production testlerden import
yapmaz. Ortak test fixture'ı kabul edilmiş NAT-011 geometrisini explicit constructor
girdileriyle kurar; generic production preset yaratılmadı.

| Fixture niceliği | Kabul edilmiş sonuç |
|---|---:|
| Hollow nose volume / centroid | 9.172555380948023e-5 m³ / 0.2019996168861517 m |
| Body volume / centroid | 4.310265120725205e-4 m³ / 0.65 m |
| Fin-set volume / centroid | 0.0001872 m³ / 0.81 m |
| Mount volume / centroid | 1.1309733552923245e-5 m³ / 0.94 m |
| Ring-pair volume / centroid | 3.890077103307561e-5 m³ / 0.94 m |
| Nose/body/fins masses | 0.09631183149995425 / 0.29309802820931397 / 0.127296 kg |
| Mount/ring-pair masses | 0.007690618815987807 / 0.026452524302491415 kg |
| Structural mass / axial CG | 0.5508490028277474 kg / 0.6266199705547180 m |

Bu sayılar yalnız V&V fixture sonuçlarıdır. Parity sadece aggregate kıyaslaması
değildir: her başarılı geometry'nin 50 scalar ve üç enum sonucu, her mass case'de
bunlara ek beş material identity/density, beş mass, beş centroid, total mass,
weighted moment numerator ve CG karşılaştırılır. Source reference length ile
aerodynamic length ayrı kalır; source identity Java unit/consumer testinde korunur.

| Parity grubu | Başarı / hata case | Numerical quantities | Numerically exact |
|---|---:|---:|---:|
| Geometry | 27 / 122 | 1350 | 1350 |
| Materials/catalog | 7 / 15 | 8 | 8 |
| Structural mass | 58 / 151 | 3944 | 3944 |
| Birleşik | **92 / 288** | **5302** | **5302** |

Maximum measured relative fark **0.0**. Bu örneklem sonucudur; tüm double girdiler,
JVM/OS platformları için bitwise parity garantisi değildir. Error class/code/field/
value, generic hata field-token'ı, NaN/Inf metadata ve validation ordering de
karşılaştırıldı. Solid/shell, triangular/signed fins, counts, datum/extent,
mount/ring sınırları, finite underflow/overflow ve raw nonfinite durumlar kapsandı.
İlk parity aracı denemesinde devasa negatif integer metadata'sının float'a
çevrilmesi test-tool overflow'u oluşturdu; integer parse korunarak düzeltildi.
Üretim denklemi veya tolerance değiştirilmedi.

Test toleransları kaynak unit testlerinden alınır: reference fixture rel=2e-15,
geometry/aero/fin rel=3e-15, shell/body subtraction rel=2e-14, mount/rings rel=4e-15,
mass/CG fixture rel=3e-14. Sıfır midchord fixture için source abs=1e-15 korunur.
Yeni global tolerance yoktur. Hypot/atan2 ve işlem sırası source gibi kalmış;
incelenen başarı case'lerinde tüm bu sonuçlar da numerically exact çıkmıştır.

## Çalıştırılmış verification ve ortam

Windows 11 (10.0.26200), Python 3.12.4, NumPy 2.5.2,
Eclipse Temurin JDK/javac 17.0.20.1+1; Gradle Wrapper 8.12.1.
JDK ve Gradle cache seçimi process-local'dır; build.gradle değiştirilmedi.

| Gate | Sonuç |
|---|---:|
| A GeometryModelsTest | 3 passed |
| B GeometryResolverTest | 31 passed |
| C MaterialsTest | 15 passed |
| D StructuralMassTest | 23 passed |
| J-001/J-002/J-003 Java regression | 157 passed |
| Complete Java Core clean build | **229 passed**, 0 failure/error/skipped |
| Python geometry/materials/all mass tests | **325 passed** |
| Full Python repository | **1491 passed**, 0 failure/skipped |
| Independent Gradle JAR consumer | foundation/quaternion/environment/vehicle-structural PASS |
| runtimeClasspath / verifyProductionDependencies | no dependencies / PASS |
| jdeps --print-module-deps | java.base only |

İki temiz build'in runtime ve source JAR SHA-256 değerleri eşleşti:

```text
roketsim-core-0.1.0.jar
F7E3AF24215C1D67F8C9E17C16BF68EE9705881F7326015501897697294759B7
roketsim-core-0.1.0-sources.jar
ACD2FB5132F85C0D981600E6143D0238A5168078621F35BC489BCF57C423FA6D
```

Javadoc JAR build'i geçti; onun timestamp içeriği için byte-identical archive
iddiası/testi yapılmadı. Eski J-001 kaynaklarına ait 22 missing-Javadoc uyarısı
korundu; J-004 yeni warning eklemedi. Native mass/geometry fizik farkı veya
tolerance TBD'si bulunmadı.

## Build ortamı engeli ve yeniden çalıştırma

İlk A-grubu koşusunda eski `build` dizinindeki stale output temizliği başarısız
oldu. Dizin OneDrive ReadOnly/ReparsePoint olarak görüldü. Forced recursive
silme güvenlik denetimince reddedildi ve uygulanmadı. Physics/source değiştirerek
veya bu güvenlik reddini aşarak çözüm üretilmedi.

Test-only `verification/isolated-build.init.gradle` yalnız caller'ın açık verdiği
output dizinini seçer; normal Gradle build policy'sini veya production artifact'i
değiştirmez. Build ve project cache system TEMP'e taşınarak A grubu geçmeden
B'ye ilerlenmedi. Sonraki clean/build testleri gerçek JDK17 kaynak derlemeleridir.
Independent consumer da ayrı TEMP output/cache kullanır. Generated çıktılar
commit'e alınmaz. OneDrive'ın default build-dir temizleme sorunu ortam sınırlaması
olarak kalır; destructive cleanup yapılması J-004 için gerekli değildir.

Core dizininden, JAVA_HOME JDK17 ve mevcut doğrulanmış Gradle cache hazırken:

```powershell
$j004Build = Join-Path $env:TEMP 'roketsim-j004-verification-build'
$j004Cache = Join-Path $env:TEMP 'roketsim-j004-project-cache'
.\gradlew.bat -I verification/isolated-build.init.gradle "-PverificationBuildDir=$j004Build" --project-cache-dir "$j004Cache" clean build --offline --no-daemon
..\roketsim-native-engine\.venv\Scripts\python.exe -B verification/vehicle_parity.py --java "$env:JAVA_HOME\bin\java.exe" --build-dir "$j004Build" --group all
jdeps --print-module-deps "$j004Build\libs\roketsim-core-0.1.0.jar"
.\gradlew.bat -p verification/consumer -I "$PWD\verification\isolated-build.init.gradle" "-PverificationBuildDir=$env:TEMP\roketsim-j004-consumer-build" --project-cache-dir "$env:TEMP\roketsim-j004-consumer-cache" clean run "-PcoreJar=$j004Build\libs\roketsim-core-0.1.0.jar" --offline --no-daemon
```

Python engine dizininde `python -B -m pytest tests/unit/geometry tests/unit/materials
tests/unit/mass -p no:cacheprovider -q` ve `python -B -m pytest -p no:cacheprovider -q`
gerçek `.venv/Scripts/python.exe` ile çalıştırıldı. Bu scope all-mass regression
içinde mevcut total.py testlerini de çalıştırır; total.py migration yapılmış demek değildir.

## Scope sınırı ve kabul önerisi

Core production yalnız JDK/java.base kullanır; test probe, parity Python ve
Gradle verification araçları production JAR'a girmez. OpenRocket, Swing, frontend,
Python/JSON runtime veya network bağımlılığı yoktur. Python source/tests,
historical records/ADRs ve J-001/J-002/J-003 implementation diff'i boştur.

Geometri tek stage/tek çap/pointed conical nose/body/tek trapezoid fin seti ve
coaxial mount/tam iki ring ile sınırlıdır. Manufacturing clearance/retention,
genel component tree/staging, motor runtime/total mass, inertia, aero coefficients,
6DOF, dynamics/RK4/simulation, frontend ve installer eklenmedi. Yeni fizik kararı
veya unresolved numerical fark yoktur. Java Core geometry/material/structural mass
migration'ı için J-004 kabulü önerilir; J-005 bu çalışma kapsamında başlatılmaz.
