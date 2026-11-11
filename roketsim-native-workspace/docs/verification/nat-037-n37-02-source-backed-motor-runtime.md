# NAT-037 / N37-02 — Source-backed motor runtime integration

Status: IMPLEMENTATION VERIFIED; ENGINEERING ACCEPTANCE PENDING. This is N37-02 only, not complete NAT-037 acceptance. No commit, stage, push, merge, rebase, reset, stash or clean was performed.

## Starting state and preservation

- Branch: `development/nat-035-apogee-recovery`.
- HEAD: `4c31dba3be4a133529dcad07980dbe9da8387bae` (unchanged).
- Starting index empty; 5 tracked modifications and 16 untracked files (accepted NAT-035/036 working work).
- Before implementation all 21 dirty files were copied with SHA-256 manifest to `<local-evidence>`.
- Compared with that snapshot, only the approved overlapping engine, physics, guide, recovery runtime, ground execution and public API inventory files changed. The other 15 starting files remain byte-identical, including prior NAT-035/036 verification records, tests, consumers and TranslationalDynamicsEvaluator.
- Final index remains empty. Final state intentionally dirty; no milestone commit authorized. New and changed N37-02 files are listed below separately from the preserved NAT-035/036 work.

## Local API preflight and exact additions

The constructor inventory was delivered before edits. Existing authorities inspected: MotorDefinition (16 components), MotorInstallation (5), PhysicsEvaluationContext3DOF (14), propulsion evaluators/analyzer/resolver, source catalog/parser, motor boundary scheduler, guide lifecycle, recovery runtime and ground execution. Existing positional records and all previous public methods/return types remain intact. No dummy MotorDefinition/F50 or null motor slot is used for source runs.

New propulsion APIs (package `org.roketsim.core.propulsion`):

```java
public sealed interface MotorPhysicsInput {
    double diameterM(); double lengthM(); double initialMassKg(); double propellantMassKg();
    List<ThrustSample> thrustCurve();
    List<MotorMassSample> massCurve(); List<MotorCgSample> cgCurve();
}
public sealed interface InstalledMotorPhysicsInput {
    MotorPhysicsInput motor(); double motorFrontXGeoM();
}
public static MotorPhysicsInput MotorPhysicsInputs.fromLegacyMotor(MotorDefinition motor);
public static InstalledMotorPhysicsInput MotorPhysicsInputs.fromLegacyInstallation(MotorInstallation installation);
public record MotorEjectionMetadata(Kind kind, OptionalDouble delayS,
    Optional<String> rawSourceToken, String sourceReference) { /* Kind: TIMED, PLUGGED, UNKNOWN */ }
public record RuntimeMotorInstallation(MotorPhysicsInput motor, double motorFrontXGeoM,
    double motorAftXGeoM, double nominalRadialClearanceM, double axialEngagementLengthM) {}
public MotorThrustState MotorThrustCurveEvaluator.evaluateRuntime(MotorPhysicsInput motor, double motorTimeS);
public MotorCurveStatistics MotorCurveAnalyzer.analyzeRuntime(MotorPhysicsInput motor);
public MotorMassProperties MotorPropertyEvaluator.evaluateRuntime(
    InstalledMotorPhysicsInput installation, Object modelProfile, double motorTimeS);
public RuntimeMotorInstallation MotorInstallationResolver.resolveRuntime(
    ResolvedRocketGeometry geometry, MotorPhysicsInput motor);
```

Sealed permits: the motor interface accepts only the internal lazy legacy view and SourceBackedMotorProfile; the installed interface accepts the lazy legacy view and RuntimeMotorInstallation. The interface signatures above omit only Java's permits lists for readability. No mutable public implementations or unconstrained user physics callbacks are introduced.

```java
public record SourceBackedMotorProfile(
    CatalogMotorProfile catalogProfile, String selectedCurveId, MotorType motorType,
    CatalogMotorProfile.MassObservation initialMassObservation,
    CatalogMotorProfile.MassObservation propellantMassObservation,
    GeometryInterpretation geometryInterpretation, MotorEjectionMetadata ejection,
    Optional<CertificationEvidence> certificationEvidence,
    List<MotorMassSample> massCurve, Optional<String> massCurveSource,
    List<MotorCgSample> cgCurve, Optional<String> cgCurveSource,
    MotorPropertyModelProfile propertyModelProfile) implements MotorPhysicsInput {}
// Nested immutable carriers:
public record GeometryInterpretation(double diameterM, double lengthM,
    String sourceReference, String qualification) {}
public record CertificationEvidence(MotorCertificationReference measuredReference,
    String sourceReference) {}
```

Source validation has a local IllegalArgumentException subtype `SourceMotorValidationError` with `errorCode()` and `fieldName()`; this is not a new global error framework. Ejection metadata uses normal IllegalArgumentException for its own invariants.

New simulation APIs (package `org.roketsim.core.simulation`):

```java
public record RuntimePhysicsEvaluationContext3DOF(
    ResolvedRocketGeometry resolvedGeometry,
    SingleStageRocketAerodynamicSurfaces aerodynamicSurfaces,
    StructuralMassProperties structuralMassProperties,
    RuntimeMotorInstallation motorInstallation,
    MotorPropertyModelProfile motorPropertyModelProfile,
    BasicDragModelProfile basicDragModelProfile,
    TranslationalDynamicsModelProfile translationalDynamicsModelProfile,
    Object environmentPositionMappingModel, double launchEnvironmentAltitudeM,
    PropulsionTimeline propulsionTimeline, USStandardAtmosphere1976Lower atmosphereModel,
    DryAirPropertiesCalculator airPropertiesCalculator, ConstantGravityModel gravityModel,
    Object windModel) {}
public record SourceMotorGroundRunConfiguration3DOF(
    LaunchConditions3DOF launchConditions, RuntimePhysicsEvaluationContext3DOF physicsContext,
    FlightEventDetectionProfile3DOF eventDetectionProfile, FixedStepConfig fixedStepConfig,
    Object terminalEventHandlingModel, List<AttachedHardwareMass> attachedHardware,
    Optional<GuideConfiguration> launchGuide, Optional<RecoveryConfiguration3DOF> recovery,
    IntegrationStepAlignmentMode alignmentMode) {}
// Nested GuideConfiguration:
public record GuideConfiguration(LaunchGuideGeometry guide,
    GuideAttachmentGeometry attachments, LaunchGuideConstraintPolicy policy) {}
public GroundRunResult3DOF SimulationEngine3DOF.runSourceUntilGround(
    SourceMotorGroundRunConfiguration3DOF configuration, RunControl3DOF control);
public PhysicsEvaluationResult3DOF PhysicsEvaluator3DOF.evaluateRuntime(double timeS,
    TranslationalState3DOF state, LaunchConditions3DOF launchConditions,
    RuntimePhysicsEvaluationContext3DOF context, double[] auxiliaryMassesKg, double[] auxiliaryCgXGeoM);
public static MotorTimeBoundarySchedule3DOF MotorTimeBoundarySchedule3DOF.fromRuntimeContext(
    RuntimePhysicsEvaluationContext3DOF context);
```

These qualified declarations are an API inventory, not a compilable combined Java source file. Concrete source declarations are authoritative. Package-private PhysicsContextInputs3DOF and SimulationRunInputs3DOF (including GroundConfigurationInputs3DOF / GuideRunInputs3DOF views in the latter file) are adapters only, not new simulation loops. Engine now has six public operations: four preserved run signatures, preserved runUntilGround, and additive runSourceUntilGround. PhysicsEvaluator has its two original evaluate signatures plus distinct evaluateRuntime. Exact inventory assertions retained and extended.

## Data contract, validation and qualifications

- SINGLE_USE remains first enum value with `single_use`; RELOADABLE appended with `reloadable`. F50 record unchanged.
- Legacy adapters are lazy and nonvalidating: no eager property reads or input normalization; the existing numerical methods retain their validation sequence. Existing null-involving calls retain unambiguous legacy signatures.
- Source binding requires existing catalog identity, an explicitly selected accepted source curve, supported explicit hardware/type association, catalog-owned initial/propellant observations with appropriate roles, positive consistent masses, explicit positive finite geometry and source/qualification, matching original ejection token and semantics, optional containers, explicit model profile and complete selected explicit curves when required.
- Explicit mass/CG curves require paired source references, at least two increasing samples from zero through the selected thrust endpoint; mass is nonincreasing and starts at selected initial mass; CG lies within declared motor length. Existing finite sample validation is reused. Lists are defensive snapshots. Nullable absent mass/CG lists retain accepted numerical model conventions; absence never selects a fallback model.
- Context requires nonnull dependencies, finite environment altitude and agreement with the source profile's explicit model selection. Source flight configuration requires an actual SourceBackedMotorProfile, valid terminal policy and an immutable unique attached-mass inventory. There is no duration or hidden step cap.
- Unknown source assembly aliases are retained as `UNVERIFIED_ASSEMBLY_EQUIVALENCE` qualifications; they are not automatically resolved or used to import manufacturer physical measurements. This path runs the selected source/model interpretation, not an asserted TF/P/PS assembly equivalence.
- Missing certification is allowed on the new profile, reported as `NO_VERIFIED_CERTIFICATION_EVIDENCE_PROVIDED`; this does not assert the motor is uncertified. Legacy mandatory certification constructor unchanged. No certification measurements invented.
- Original catalog compatibility issue records are retained byte-for-byte, including the historical `RELOADABLE_TYPE_UNREPRESENTABLE` / SINGLE_USE-only statement. This is a conservative legacy-catalog compatibility snapshot, not the new research runtime gate; profiles explicitly qualify it as such. It is not evidence that the new enum lacks RELOADABLE.
- TIMED requires a finite nonnegative numeric delay; PLUGGED and UNKNOWN prohibit one. Original source token retained; legacy raw token absent. Ejection is metadata only and never drives NAT-035.
- Installation resolver shares old nominal-fit checks/arithmetic; dimensions/qualification are explicit. Nominal fit does not certify case flange, nozzle, closure envelope, actual hardware equivalence or axial-datum interpretation.

## Shared numerical and execution authority

1. Legacy evaluator methods adapt lazily into the same evaluateRuntime/analyzeRuntime bodies. No copied interpolation, impulse-prefix, depletion or CG equations.
2. Installed motor views supply selected motor/front coordinate to the original MotorPropertyEvaluator arithmetic. Same motorTime and selected curve feed thrust and cumulative/total impulse.
3. PhysicsEvaluator evaluates environment, air properties, gravity, wind/relative flow, flight conditions, motor time, shared motor kernels, accepted RocketMassPropertiesCalculator and existing drag/dynamics in the original order. No independent atmosphere or mass aggregation.
4. Source configuration derives statistics internally from the selected source; source boundary factory also derives statistics internally. Caller cannot provide unrelated statistics to that factory/run configuration. Switching curves updates thrust, statistics, cumulative impulse, impulse-proportional mass and scheduler knots together.
5. Source engine method performs initialization, then delegates to existing guide execution and `continueUntilGroundInputs` / `continueShared` with the existing RecoveryRuntime and GroundRunExecution. No separate recovery/flight loop or integrator.
6. NAT-035 strict accepted-sample 0.01 m altitude-drop trigger, detection-time delay, fixed RK4 force regime per segment, actual-positive-thrust rejection, ground precedence and single deployment retained. NAT-036 monitoring, cooperative cancellation, accepted history and uncapped ground policy reused. Guide projection, transition localization and approximate continuation retained.
7. Boundary effective-step arithmetic, BasicDrag, RecoveryDrag, RK4, ground interpolation, accepted mass/CG/inertia and Python reference sources unchanged. Known NAT-026.1 interior-boundary representability limitation remains; no new rounding policy.

## Source evidence and test-only vehicle interpretation

Original ZIP: `<local-source-archive>`.
SHA-256: `467bd83b1b72c720305cdd22ec9b2f05aa91120b455988415a202915c9beb5de`.
Existing bundled MOTOR-001 ENG resources and original catalog records unchanged. Provider Teknofest26 remains separate from AeroTech/RCS physical identity. Rev1, exact TF designation and source record remain reachable through selectedThrustSource(), without copied provenance authority.

| Profile | Source token / interpretation | Initial / propellant kg | ENG SHA-256 |
|---|---|---|---|
| TFL1256WS | `-` / UNKNOWN | 3.905 / 1.905 | 756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd |
| TFM1850W | `1000` / UNKNOWN | 7.010 / 4.103 | 111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc |
| TFN3300R-P | `P` / PLUGGED | 12.645 / 7.668 | 10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8 |

Existing MOTOR-001 impulse tests remain unchanged. L1256 alternate mass / approximately 3 g conflict retained rather than overwritten. All ejection source semantics remain explicit. Tests preserve identity aliases rather than treating them as resolved.

Runtime V&V uses explicitly TEST_ONLY geometry interpretations (ENG nominal dimensions), selected ENG masses and disclosed IMPULSE_PROPORTIONAL mass / FIXED_MIDPOINT CG assumptions. The three-motor packaged consumer uses a synthetic 0.2 m airframe, 0.4 m cone, 2 m body, explicit mount/fin/material inputs and 100 kg attached ballast, launched with direct 20 m/s vertical speed at WORLD (100,-200,50). These are stress/compatibility fixtures, not proposed real vehicles or sourced launch installations. The L1256 guide/recovery test similarly uses explicitly synthetic guide coordinates.

| Test-only source run | Estimated ground time s | Accepted segments |
|---|---:|---:|
| L1256 | 10.100843766013488 | 510 |
| M1850 | 16.501894763085424 | 834 |
| N3300 | 27.57413283107867 | 1393 |

No actual TEKNOFEST motor is declared physically flight-ready or inertia-ready. No new grain model, dry inertia, CG measurement, mounting datum, certification or ejection delay fabricated. M1850 nonidentical grains remain outside the identical radial-grain model; translational model-based execution does not solve that physical-data gap.

## Verification commands and actual results

Environment: Windows, Eclipse Adoptium Java 17.0.20.1, Gradle 8.12.1; Python 3.12.4 / NumPy 2.5.2 from existing project venv. Isolated outputs and caches avoid polluting repository.

All Gradle commands executed from `roketsim-native-workspace/roketsim-core`, with:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
# Common invocation:
.\gradlew.bat -I verification/isolated-build.init.gradle `
  '-PverificationBuildDir=<build-dir>' `
  --project-cache-dir <project-cache-dir> `
  <tasks> --offline --no-daemon
```

| Invocation | Evidence log under <local-evidence> | Result |
|---|---|---|
| compileJava | roketsim-n3702-compile-1.log | exit 0, 18 s |
| test --tests org.roketsim.core.SourceMotorRuntimeTest --tests org.roketsim.core.MotorCatalogTest --tests org.roketsim.core.MotorPropertiesTest --tests org.roketsim.core.SingleStageVehicleConfigurationTest | roketsim-n3702-focused-1.log | exit 0, 54 tests, 21 s |
| same focused command after extending fixtures | roketsim-n3702-focused-2.log | exit 0, 59 tests, 21 s |
| same focused command after additional validation tests | roketsim-n3702-focused-3.log | exit 0, 61 tests, 19 s |
| final same focused command | roketsim-n3702-focused-4.log | exit 0, 62 tests (25 new + 37 existing), 29 s |
| assemble test verifyProductionDependencies | roketsim-n3702-regression.log | BUILD SUCCESSFUL, 30 s; 700 tests / 44 suites, zero failures/errors/skips |

Full build completion is evidenced by its raw BUILD SUCCESSFUL log and complete XML reports; the original polling response was truncated and its process handle had already closed on recovery, so a separate original shell exit-code observation is not asserted for that invocation. No repeat full suite was needed. Javadoc produced 100 warnings (missing documentation); build succeeded. No test or compilation failures occurred; there is no invented RED-stage evidence. Two later inspection-only commands failed (sandbox TEMP path differed; PowerShell rejected shell-style brace expansion); corrected reads used explicit absolute paths / separate directories and had no source/build effects.

Reports: `<local-evidence>`; HTML at corresponding `reports/tests/test/index.html`.

25 new SourceMotorRuntimeTest methods cover exact legacy schemas/precedence/F50, types/tokens, profile validation/source checksums, optional certification, source observation roles, explicit curves/provenance/coverage, shared exact numerical equivalence, alternate curve consistency, source-boundary grids/negative ignition, direct full-history equality, guide/recovery full-history equality, real-source synthetic ground runs, cancellation, pending ground, zero-delay, under-thrust rejection, immutability and repeatability. Original 675 tests remain passing, with only authorized additive API inventory expectations updated.

Full-suite detail (tests / failures / errors / skipped):

- `org.roketsim.core.AerodynamicProfileTest`: 22 / 0 / 0 / 0
- `org.roketsim.core.AerodynamicsTest`: 35 / 0 / 0 / 0
- `org.roketsim.core.AltitudeTest`: 18 / 0 / 0 / 0
- `org.roketsim.core.AtmosphereAirTest`: 32 / 0 / 0 / 0
- `org.roketsim.core.AuxiliaryMassTest`: 26 / 0 / 0 / 0
- `org.roketsim.core.ClassicalRK4Test`: 11 / 0 / 0 / 0
- `org.roketsim.core.FixedStepTest`: 7 / 0 / 0 / 0
- `org.roketsim.core.FlightEventsTest`: 6 / 0 / 0 / 0
- `org.roketsim.core.FlightPhaseClockTest`: 14 / 0 / 0 / 0
- `org.roketsim.core.FlightRecorderTest`: 6 / 0 / 0 / 0
- `org.roketsim.core.FlowConditionsTest`: 12 / 0 / 0 / 0
- `org.roketsim.core.FoundationTest`: 25 / 0 / 0 / 0
- `org.roketsim.core.GeometryModelsTest`: 3 / 0 / 0 / 0
- `org.roketsim.core.GeometryResolverTest`: 31 / 0 / 0 / 0
- `org.roketsim.core.GravityWindTest`: 8 / 0 / 0 / 0
- `org.roketsim.core.GroundTerminationTest`: 17 / 0 / 0 / 0
- `org.roketsim.core.InertiaTensorFoundationsTest`: 14 / 0 / 0 / 0
- `org.roketsim.core.InitialStateTest`: 12 / 0 / 0 / 0
- `org.roketsim.core.LaunchGuideTest`: 24 / 0 / 0 / 0
- `org.roketsim.core.MaterialsTest`: 15 / 0 / 0 / 0
- `org.roketsim.core.MotorCatalogTest`: 12 / 0 / 0 / 0
- `org.roketsim.core.MotorEngImportTest`: 19 / 0 / 0 / 0
- `org.roketsim.core.MotorInertiaTest`: 12 / 0 / 0 / 0
- `org.roketsim.core.MotorInstallationTest`: 9 / 0 / 0 / 0
- `org.roketsim.core.MotorPropertiesTest`: 7 / 0 / 0 / 0
- `org.roketsim.core.MotorStepBoundariesTest`: 18 / 0 / 0 / 0
- `org.roketsim.core.MotorThrustTest`: 7 / 0 / 0 / 0
- `org.roketsim.core.NativeDemoTrajectoryTest`: 9 / 0 / 0 / 0
- `org.roketsim.core.NumericInputContractTest`: 23 / 0 / 0 / 0
- `org.roketsim.core.PhysicsEvaluatorTest`: 13 / 0 / 0 / 0
- `org.roketsim.core.QuaternionTest`: 62 / 0 / 0 / 0
- `org.roketsim.core.RecoveryDragTest`: 17 / 0 / 0 / 0
- `org.roketsim.core.RecoveryIntegrationTest`: 15 / 0 / 0 / 0
- `org.roketsim.core.RocketMassTest`: 7 / 0 / 0 / 0
- `org.roketsim.core.simulation.GroundExecutionTest`: 15 / 0 / 0 / 0
- `org.roketsim.core.simulation.RecoveryTimingTest`: 7 / 0 / 0 / 0
- `org.roketsim.core.SimulationEngineTest`: 5 / 0 / 0 / 0
- `org.roketsim.core.SimulationResultTest`: 6 / 0 / 0 / 0
- `org.roketsim.core.SingleStageVehicleConfigurationTest`: 18 / 0 / 0 / 0
- `org.roketsim.core.SourceMotorRuntimeTest`: 25 / 0 / 0 / 0
- `org.roketsim.core.StructuralInertiaTest`: 8 / 0 / 0 / 0
- `org.roketsim.core.StructuralMassTest`: 23 / 0 / 0 / 0
- `org.roketsim.core.TranslationalDynamicsTest`: 10 / 0 / 0 / 0
- `org.roketsim.core.VehicleInertiaTest`: 15 / 0 / 0 / 0

Packaged main JAR: `<local-evidence>` (plus assembled sources and Javadoc JARs).
Executed for each SourceMotorJarConsumer, RecoveryJarConsumer and GroundJarConsumer:

```powershell
& '<JAVA_HOME>/bin/java.exe' `
  --module-path '<local-evidence>' `
  --add-modules org.roketsim.core verification/<Consumer>.java
```

All three exited 0 and printed PASS. Logs `roketsim-n3702-SourceMotorJarConsumer.log`, `roketsim-n3702-RecoveryJarConsumer.log`, `roketsim-n3702-GroundJarConsumer.log`. Existing consumer files are unchanged from task start. Source consumer uses only public main-JAR APIs, with no legacy placeholder motor.

`jdeps --print-module-deps <main-JAR>` exited 0: `java.base` only, log `roketsim-n3702-jdeps.log`. Gradle verifyProductionDependencies passed. No runtime dependency or module export changes.

Actual parity command:

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py `
  --java '<JAVA_HOME>/bin/java.exe' `
  --build-dir '<build-dir>' `
  --artifacts-dir '<artifacts-dir>'
```

Exit 0; `NATIVE_TRAJECTORY_PARITY: PASS`. Log `roketsim-n3702-trajectory.log`, artifacts in directory above; existing Python reference, Java CLI verifier and historical tolerances unchanged.

| Run | Samples | Events | Numeric comparisons | Exact | Nonexact within existing tolerances |
|---|---:|---:|---:|---:|---:|
| primary | 1584 | 3 | 147521 | 147511 | 10 |
| repeat | 1584 | 3 | 147521 | 147511 | 10 |
| fine | 3167 | 3 | 294740 | 294714 | 26 |

Distinct two configurations: 442261 comparisons, 442225 exact, 36 within unchanged tolerances. Including repeat: 589782 comparisons, 589736 exact, 46 within tolerances. Maximum absolute difference 8.881784197001252e-16; maximum relative difference 4.3829501452845633e-16. This is tolerance-qualified Python/Java parity, not a claim of universal bitwise parity. Equivalent legacy/source Java fixtures separately assert exact object/numerical equality.

## Exact N37-02 change inventory

Paths below are relative to `roketsim-native-workspace/roketsim-core` unless prefixed otherwise.

New production files:

- `src/main/java/org/roketsim/core/propulsion/MotorPhysicsInput.java`
- `src/main/java/org/roketsim/core/propulsion/InstalledMotorPhysicsInput.java`
- `src/main/java/org/roketsim/core/propulsion/MotorPhysicsInputs.java`
- `src/main/java/org/roketsim/core/propulsion/MotorEjectionMetadata.java`
- `src/main/java/org/roketsim/core/propulsion/SourceMotorValidationError.java`
- `src/main/java/org/roketsim/core/propulsion/SourceBackedMotorProfile.java`
- `src/main/java/org/roketsim/core/propulsion/RuntimeMotorInstallation.java`
- `src/main/java/org/roketsim/core/simulation/PhysicsContextInputs3DOF.java`
- `src/main/java/org/roketsim/core/simulation/RuntimePhysicsEvaluationContext3DOF.java`
- `src/main/java/org/roketsim/core/simulation/SourceMotorGroundRunConfiguration3DOF.java`
- `src/main/java/org/roketsim/core/simulation/SimulationRunInputs3DOF.java`

Modified production files (RecoveryRuntime and GroundRunExecution were already untracked at task start):

- `src/main/java/org/roketsim/core/propulsion/MotorType.java`
- `src/main/java/org/roketsim/core/propulsion/MotorThrustCurveEvaluator.java`
- `src/main/java/org/roketsim/core/propulsion/MotorCurveAnalyzer.java`
- `src/main/java/org/roketsim/core/propulsion/MotorPropertyEvaluator.java`
- `src/main/java/org/roketsim/core/propulsion/MotorInstallationResolver.java`
- `src/main/java/org/roketsim/core/simulation/MotorTimeBoundarySchedule3DOF.java`
- `src/main/java/org/roketsim/core/simulation/PhysicsEvaluator3DOF.java`
- `src/main/java/org/roketsim/core/simulation/SimulationEngine3DOF.java`
- `src/main/java/org/roketsim/core/simulation/LaunchGuideSimulator3DOF.java`
- `src/main/java/org/roketsim/core/simulation/RecoveryRuntime3DOF.java`
- `src/main/java/org/roketsim/core/simulation/GroundRunExecution3DOF.java`

Tests/consumer/documentation:

- New `src/test/java/org/roketsim/core/SourceMotorRuntimeTest.java`.
- Modified `src/test/java/org/roketsim/core/SingleStageVehicleConfigurationTest.java`: additive exact public method inventory/signatures only; existing record/signature checks retained.
- New `verification/SourceMotorJarConsumer.java`.
- New `roketsim-native-workspace/docs/verification/nat-037-n37-02-source-backed-motor-runtime.md` (this record).

Protected existing records, MOTOR-001 parser/resources/catalog content, accepted equations, RK4, event detector, mass/inertia authorities and historical tests/oracles not changed. A temporary catalog wording edit was removed; original catalog HEAD bytes are restored, with no semantic source-data changes. Prior NAT-035/036 non-overlapping files preserved.

## Remaining limitations and engineering handoff

- Model-based research execution is supported, not verified physical installation or certification readiness. Approximate geometry/datum differences, alternate masses, assembly equivalence, real CG evolution and physical inertia still need owner-reviewed scenario evidence.
- `qualifications()` and source/model configuration must accompany any downstream scenario report; existing GroundRunResult schema is intentionally preserved and does not embed a new provenance ledger. The caller retains its immutable source run configuration.
- Legacy catalog compatibility snapshots are not recalculated by this task; their historical labels require interpretation as explained above.
- No source-backed inertia adapter, grain physics, general catalog migration, new recovery policy or broader numerical fix is implemented.
- Existing BasicDrag/atmosphere applicability and scheduler representability constraints still apply. Passing synthetic configurations does not establish arbitrary high-power motor/vehicle model applicability.
- Accepted NAT-035/036 work remains uncommitted with N37-02; no independent owner acceptance is inferred from this implementation verification.

Final integrity evidence: `<local-evidence>` and `roketsim-n3702-final-status.log`. Protected tracked-file assertions passed; `git diff --check` passed (only existing/configured LF-to-CRLF warnings, no whitespace errors).

Final branch/HEAD unchanged; index empty; 11 tracked modifications (including the pre-existing TranslationalDynamicsEvaluator change) and 30 untracked files, including preserved prior work. No commit/push/merge. Scope ends at N37-02.

N37-02 IMPLEMENTATION VERIFIED — ENGINEERING ACCEPTANCE PENDING
