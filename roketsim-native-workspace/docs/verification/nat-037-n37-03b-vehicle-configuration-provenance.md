# N37-03B Vehicle Configuration, Measured Mass/CG and Provenance

## Scope and Git baseline

Implementation verification for the additive source-backed, single-stage, single-motor translational 3DOF preparation path. The starting branch was `development/nat-035-apogee-recovery` at `4c31dba3be4a133529dcad07980dbe9da8387bae`. The starting tree had 14 tracked modifications, 33 untracked files and an empty index. The full initial inventory and 505 SHA-256 fingerprints are in `<local-evidence>`.

## Files and public API

Production paths are relative to `roketsim-core/src/main/java/org/roketsim/core/`.

| Path | Public change |
|---|---|
| `mass/StructuralMassPropertiesCalculator.java` | Added `StructuralMassProperties evaluateWithMeasurements(ResolvedRocketGeometry geometry, SingleStageRocketMaterials materials, List<StructuralMassOverride> overrides)`. The calculated and measured paths use the same aggregation. |
| `mass/StructuralMassOverride.java` | Added record `StructuralMassOverride(String componentId, Optional<Measurement> massKg, Optional<Measurement> cgXGeoM)` and nested record `Measurement(double value, String sourceReference)`. |
| `mass/VehicleConfigurationError.java` | Added a final `IllegalArgumentException` subtype with constructor `VehicleConfigurationError(String errorCode, String fieldName, String detail)` and three matching `String` accessors. |
| `simulation/PreparedSourceVehicle3DOF.java` | Added the preparation API and its immutable result association. |

The prepared vehicle exposes the following operations and nested types.

```java
static PreparedSourceVehicle3DOF prepare(
    String configurationId,
    String designSourceReference,
    SingleStageRocketMaterials materials,
    SourceMotorGroundRunConfiguration3DOF calculatedRun,
    List<StructuralMassOverride> overrides,
    List<IncludedItem> includedItems,
    List<HardwareEvidence> hardwareEvidence);

String configurationId();
String designSourceReference();
SingleStageRocketMaterials materials();
SourceMotorGroundRunConfiguration3DOF calculatedRun();
SourceMotorGroundRunConfiguration3DOF runConfiguration();
List<StructuralMassOverride> overrides();
List<IncludedItem> includedItems();
List<HardwareEvidence> hardwareEvidence();
CompletedRun runUntilGround(RunControl3DOF control);

enum Origin { CALCULATED, MEASURED, MODEL_BASED }
record IncludedItem(String itemId, String structuralOwnerId) {}
record HardwareEvidence(String componentId, Origin massOrigin, String massReference,
                        Origin cgOrigin, String cgReference) {}
// CompletedRun is final and produced by runUntilGround.
// Its public accessors are:
// PreparedSourceVehicle3DOF configuration();
// GroundRunResult3DOF result();
```

The focused tests are in `roketsim-core/src/test/java/org/roketsim/core/PreparedSourceVehicleTest.java`. The external module consumer is `roketsim-core/verification/PreparedSourceVehicleJarConsumer.java`.

## Data flow, ownership and validation

Preparation resolves the run's original geometry through `GeometryResolver`, then calculates structural properties using the supplied materials and `StructuralMassPropertiesCalculator`. Both authoritative records must equal their supplied calculated counterparts exactly. With no override, the exact original run object is retained.

Eligible owners are `structure:nose`, `structure:body`, `structure:fins`, `structure:motor-mount` and `structure:centering-rings`. An override replaces only its explicitly measured fields. It retains the component material and any calculated field that has no replacement. Both paths sum mass and first moment in the same fixed component order, then apply the existing structural total and CG checks. Measured CG uses the nose-tip origin and aft-positive `x_geo`, within the inclusive vehicle extent. The effective context retains the geometry, surfaces, installation, source selection, motor models, environmental inputs and run controls. The existing source engine path then evaluates the time-dependent motor mass, totals the vehicle mass and runs the flight.

`motor:installed` and the five structural IDs are reserved. Separate attached hardware contributes through the existing hardware list and has one matching evidence row per ID. Each row carries an origin and reference for mass and CG independently. An included item is already accounted for by its measured structural owner and adds no auxiliary mass. Its owner must supply both measured mass and measured CG. Duplicate claims, conflicting owners, reuse of reserved IDs and duplicate, missing or orphaned evidence are rejected. Detection is limited to declared identities and ownership. Equivalent physical items declared under unrelated IDs remain a caller inventory responsibility.

Validation runs in the following order.

1. Configuration ID and design reference, followed by required objects, lists, rows and measurement-option shape. Errors are `INVALID_CONFIGURATION_ID`, `MISSING_PROPERTY_PROVENANCE` and `MISSING_VEHICLE_INPUT`.
2. Required nested geometry and material inputs, existing geometry and calculated-mass validation, and exact equality of the supplied baseline records. Mismatch errors are `BASELINE_GEOMETRY_MISMATCH` and `BASELINE_STRUCTURAL_MASS_MISMATCH`.
3. Override rows in caller order. Validate target, duplicate target, and presence of at least one measured field. Validate mass value and reference before CG value and reference. Errors are `UNSUPPORTED_OVERRIDE_TARGET`, `DUPLICATE_OVERRIDE_TARGET`, `EMPTY_STRUCTURAL_OVERRIDE`, `INVALID_MEASURED_MASS`, `INVALID_MEASURED_CG` and `MISSING_PROPERTY_PROVENANCE`. Existing aggregation validation follows the selections.
4. Attached hardware claims and accepted auxiliary numeric checks, then included-item ownership and hardware evidence coverage. Relevant errors are `DUPLICATE_COMPONENT_ID`, `CONFLICTING_MASS_OWNERSHIP`, `INVALID_INCLUDED_ITEM_OWNER` and `INVALID_HARDWARE_EVIDENCE`. Auxiliary numeric errors use the existing `MassValidationError` codes and indexed fields.
5. Reuse the existing source context and run configuration constructors where a replacement is needed. Only successful preparation can reach execution and associate the resulting `GroundRunResult3DOF` with that exact prepared object, including cancellation and partial history.

The caller supplies the revisioned identity and references. The immutable prepared vehicle retains them, the measured fields, ownership, hardware evidence, selected motor source and explicit model qualifications. It uses no persistent hashing or source certification. This path supports mass/CG selection for translational 3DOF. It provides no inertia derived from the measurements. The TEKNOFEST and F50-equivalent source vehicles used for new verification are synthetic, with nominal installation and explicit modeled inputs.

## Independent numerical evidence

| Structural case | Expected mass (kg) | Expected first moment (kg·m) |
|---|---:|---:|
| Calculated baseline | 6 | 5.5 |
| Body mass only | 7 | 6.25 |
| Body CG only | 6 | 6 |
| Body mass and CG | 7 | 7 |
| Body mass and nose CG | 7 | 6.375 |

The body baseline contributes 2 kg at 0.75 m. The mass-only case selects 3 kg at the same CG. The CG-only case selects 1.0 m at the same mass. The combined case selects 3 kg at 1.0 m. Reversing the two override rows yields exactly the same structural record. With the combined structural case, a 0.25 kg hardware item at 0.5 m and 0.5 kg motor at 1.25 m give 7.75 kg at 1.0 m. With the motor depleted to 0.25 kg at 1.0 m, the expected total is 7.5 kg with a 7.375 kg·m first moment.

The focused suite also checks exact baseline rejection, validation precedence, ownership collisions, evidence coverage, the original run identity, unchanged aerodynamic and motor evaluations at equal states and times, synthetic flights across the three catalog profiles, guide/recovery/ground sequencing, and cancellation with an immutable configuration association.

## Executed verification

Temurin OpenJDK `17.0.20.1`. Verification outputs were isolated outside the repository at `<local-evidence>` (referred to below as `$out`). The locked JUnit Jupiter 5.10.0 and Platform 1.10.0 suite ran via the Platform 1.10.0 standalone console. The local Gradle distribution was unavailable and the wrapper remained at its distribution download. The gates below use direct Java 17 tooling and the repository parity script. Gradle tasks are not counted as executed.

| Gate | Result | Raw evidence |
|---|---|---|
| Java 17 production and test compilation | PASS | `$out/main.args`, `$out/test.args`, `$out/test-compile.log` |
| Focused N37-03B suite | PASS, 13/13, 0 failed or skipped | `$out/focused.log`, `$out/focused/TEST-junit-jupiter.xml` |
| Complete Java suite | PASS, 727/727, 0 failed or skipped, 47 distinct JUnit test classes | `$out/full.log`, `$out/full/TEST-junit-jupiter.xml` |
| Java 17 modular production JAR assembly | PASS | `$out/assembly.log`, `$out/libs/roketsim-core-0.1.0.jar` |
| External packaged Java consumers | PASS for prepared source, source motor, recovery and ground | `$out/PreparedSourceVehicleJarConsumer.log`, `$out/SourceMotorJarConsumer.log`, `$out/RecoveryJarConsumer.log`, `$out/GroundJarConsumer.log` |
| Production module dependencies | PASS, `java.base` only. The project declares only test dependencies. | `$out/jdeps.log`, `roketsim-core/build.gradle` |
| J-011 legacy Python/Java trajectory parity | PASS, 442,261 distinct numeric comparisons across two configurations, unchanged tolerances | `$out/parity.log`, `<local-evidence>` |

The full suite includes the source-backed, F50, motor importer, guide and NAT-035/036 regression classes. The recorded pre-task reference was 714 tests across 46 suites. J-011 compared 147,521 primary and 294,740 fine-step values. The repeated primary run exercised determinism and is excluded from the distinct count. Thirty-six distinct values differed within the existing tolerances. The largest absolute difference was `8.88178419700125e-16`.

Commands below use the isolated output directory, JDK tools on PATH, and the core project as the working directory. The argument files contain the full production and test source inventories. The main resources were copied to `$out/resources/main` for class-path execution and assembly.

```powershell
javac --release 17 -encoding UTF-8 -d "$out/classes/java/main" "@$out/main.args"
javac --release 17 -encoding UTF-8 -classpath "$out/classes/java/main;$junit" -d "$out/classes/java/test" "@$out/test.args"
java -jar $junit execute --class-path "$out/classes/java/main;$out/classes/java/test;$out/resources/main" --select-class org.roketsim.core.PreparedSourceVehicleTest --details summary --reports-dir "$out/focused"
java -jar $junit execute --class-path "$out/classes/java/main;$out/classes/java/test;$out/resources/main" --scan-class-path --fail-if-no-tests --details summary --reports-dir "$out/full"
jar "-J-Djava.io.tmpdir=$out/libs" --create --file "$out/libs/roketsim-core-0.1.0.jar" -C "$out/classes/java/main" . -C "$out/resources/main" .
jdeps --print-module-deps "$out/libs/roketsim-core-0.1.0.jar"
foreach ($name in 'PreparedSourceVehicle', 'SourceMotor', 'Recovery', 'Ground') {
    java --module-path "$out/libs/roketsim-core-0.1.0.jar" --add-modules org.roketsim.core "verification/${name}JarConsumer.java"
}
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java "$java/java.exe" --build-dir "$out" --artifacts-dir '<build-dir>'
```

The first JAR attempt hit a temporary-directory move error. Setting the JDK temporary directory to the isolated JAR output directory resolved the move. The successful assembly, consumers, dependency check, full regression and parity each returned exit code 0.

## Final repository integrity

Of the 505 pre-existing file hashes, 504 matched exactly. Only the authorized structural calculator changed. The comparison covers the protected source, previously dirty work, historical records, ENG files and parity fixtures. The final tree on the same branch and HEAD has 15 tracked modifications and 39 untracked files, with an empty index. The increment is one tracked modification and six new untracked files, including this report. No Git history or branch changes were made.

**Recommendation:** Submit N37-03B for owner engineering review based on the verified implementation and regression evidence.

**N37-03B IMPLEMENTATION VERIFIED — ENGINEERING ACCEPTANCE PENDING**
