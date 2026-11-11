# NAT-037 / N37-02.1 — Targeted F1/F2/F3 corrections

Status: CORRECTION VERIFIED — ENGINEERING ACCEPTANCE PENDING. This record supplements the N37-02 implementation report and independent audit; it does not rewrite their historical results or declare full NAT-037 accepted.

## Baseline and preservation

Branch `development/nat-035-apogee-recovery`, HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae`. Starting state: 11 tracked modifications, 30 untracked files, empty index. All 41 dirty files were backed up before edits to `<local-evidence>` with SHA-256 `manifest.json`.

Of those 41 files, 36 remain byte-identical. Five approved overlapping files changed: RuntimeMotorInstallation, RuntimePhysicsEvaluationContext3DOF, MotorTimeBoundarySchedule3DOF, RecoveryRuntime3DOF and LaunchGuideSimulator3DOF. Three previously clean tracked files changed for F3: CatalogMotorProfile, Teknofest2026MotorCatalog and MotorEngImportTest. Two focused test classes and this verification record were added. No existing work discarded; prior NAT-035/036 and N37-02 verification records retained unchanged. No stage, commit, reset, clean, stash, push, merge or rebase.

## F1 — Installation consistency

Root cause: the new runtime installation constructor validated only finite/sign/order properties, and its context did not bind it to the actual resolved geometry. Manually supplied records could bypass the nominal-fit resolver.

Correction:

- RuntimeMotorInstallation checks `motorFrontXGeoM == motorAftXGeoM - motor.lengthM()` using the existing resolver's arithmetic order. It does not use a separately rounded subtraction of the endpoints, normalize dimensions or add a tolerance. Invalid spans produce `RUNTIME_MOTOR_SPAN_MISMATCH`.
- RuntimePhysicsEvaluationContext3DOF obtains the expected installation from the unchanged `MotorInstallationResolver.resolveRuntime(resolvedGeometry, motor)` and compares front, aft, nominal clearance and engagement using exact numeric comparisons. Inconsistent declarations produce `RUNTIME_INSTALLATION_GEOMETRY_MISMATCH` with the specific field. Genuine diameter/mount failures retain existing resolver errors.
- Validation happens during context construction, before integration or run control is claimed. Public manual construction cannot bypass the context check.
- Legacy MotorInstallation, its resolver arithmetic and legacy constructor/error precedence remain unchanged. Resolver-generated installations use exactly the same aft-minus-length operation and remain valid.

This is nominal geometry consistency only. It does not resolve physical flange/nozzle clearance, source assembly identity or the TEKNOFEST axial datum. No new placement or physical tolerance policy is introduced.

## F2 — Numerical boundary failures

Root cause: the scheduler's plain IllegalArgumentException for genuine no-progress/unrepresentable intervals was classified by the existing ground execution adapter as MODEL_DOMAIN_FAILURE.

Correction:

- The scheduler has one private step-selection body. Public `effectiveStepSizeS` retains its exact plain exception classes/messages and arithmetic. A package-private `effectiveStepSizeForExecutionS` uses the same body but emits existing NumericalIntegrationError for specifically identified derived failures.
- Codes: `MOTOR_BOUNDARY_NONADVANCING_TIME`, `MOTOR_BOUNDARY_UNREPRESENTABLE_STEP`, `MOTOR_BOUNDARY_UNREPRESENTABLE_ENDPOINT`, `MOTOR_BOUNDARY_NONFINITE_TIME`.
- Raw time/step validation still uses the original conventions. There is no broad IllegalArgumentException reclassification.
- Ground-enabled recovery/free-flight and guide execution select the typed path; the existing GroundRunExecution3DOF mapping already maps NumericalIntegrationError to NUMERICAL_FAILURE and is unchanged.
- Legacy duration-limited methods and standalone public scheduling retain their previous behavior. Source knots, endpoint coincidence, representability rejection, arithmetic order and RK4 equations remain unchanged.
- No-progress fixture: ignition `1e16`, step `.02`, aligned source mode now returns NUMERICAL_FAILURE / MOTOR_BOUNDARY_NONADVANCING_TIME, preserves its one initial accepted sample and zero accepted segments, and creates no events. Guided mode similarly preserves the supported PAD_CONTACT sample.
- The previously documented `.37` boundary / current `-1` / nominal `2` standalone cancellation case still rejects rather than crossing or snapping. Only the execution failure representation changes. No numerical repair is claimed.

## F3 — Current versus historical catalog reporting

Root cause: currentCoreCompatibility exposed the old RELOADABLE_TYPE_UNREPRESENTABLE issue as a current blocker even after runtime RELOADABLE support existed.

Correction:

- Current compatibility filters that obsolete type blocker for RELOADABLE hardware. Source-only available capabilities remain exactly CATALOG_AVAILABLE and THRUST_SOURCE_VALIDATED for all three supplied profiles.
- Added `public CompatibilityResult historicalCoreCompatibility()` to CatalogMotorProfile. It exposes the full retained issue set with an explicit `HISTORICAL MOTOR-001 / legacy contract snapshot:` label on each detail. The original record components and constructors are unchanged.
- The retained TEKNOFEST SINGLE_USE-only statement itself is prefixed `HISTORICAL MOTOR-001, before N37-02:` so even direct record-component access does not misrepresent that statement as current.
- The original issue code and historical statement are retained, not deleted. Other identity, certification, model selection, installation and inertia gaps remain visible. No physical-flight readiness flag or new readiness promotion is introduced.
- MOTOR-001's current-readiness test is updated only to stop requiring the obsolete type blocker as current. The new tests explicitly require it in the historical report and verify both reports' immutability/readiness boundaries.

Original ENG bytes/hashes, identity aliases, masses and drawing observations are untouched. The only catalog fixture metadata change is the explicit historical qualification on the derived compatibility statement. L1256 `-` / UNKNOWN, M1850 `1000` / UNKNOWN and N3300 `P` / PLUGGED remain unchanged; no certification or physical model inputs are fabricated.

## Exact correction inventory and line references

Paths relative to `roketsim-native-workspace/roketsim-core`:

| File | Lines / correction |
|---|---|
| src/main/java/org/roketsim/core/propulsion/RuntimeMotorInstallation.java | 12–15: exact resolver-convention span check |
| src/main/java/org/roketsim/core/simulation/RuntimePhysicsEvaluationContext3DOF.java | 33–40: expected installation and field comparisons |
| src/main/java/org/roketsim/core/simulation/MotorTimeBoundarySchedule3DOF.java | 68–110: common arithmetic and narrowly typed execution errors |
| src/main/java/org/roketsim/core/simulation/RecoveryRuntime3DOF.java | 136,142,167–169: ground-enabled typed scheduling selection |
| src/main/java/org/roketsim/core/simulation/LaunchGuideSimulator3DOF.java | 181–183: ground-enabled typed scheduling selection |
| src/main/java/org/roketsim/core/propulsion/catalog/CatalogMotorProfile.java | 92–111: current/historical compatibility separation |
| src/main/java/org/roketsim/core/propulsion/catalog/Teknofest2026MotorCatalog.java | 86: label retained historical claim |
| src/test/java/org/roketsim/core/MotorEngImportTest.java | 161: authorized current-blocker expectation update |
| src/test/java/org/roketsim/core/SourceMotorCorrectionTest.java | new, 12 tests |
| src/test/java/org/roketsim/core/simulation/MotorBoundaryExecutionTest.java | new, 2 tests |

New document: `roketsim-native-workspace/docs/verification/nat-037-n37-02-1-motor-runtime-corrections.md`.

## Verification commands and logs

All Gradle commands from the core directory, using Java `<JAVA_HOME>`, existing Gradle cache `<local-evidence>`:

```powershell
.\gradlew.bat -I verification/isolated-build.init.gradle `
  '-PverificationBuildDir=<build-dir>' `
  --project-cache-dir <project-cache-dir> `
  <tasks> --offline --no-daemon
```

Focused tasks: `test --tests org.roketsim.core.SourceMotorCorrectionTest --tests org.roketsim.core.simulation.MotorBoundaryExecutionTest --tests org.roketsim.core.SourceMotorRuntimeTest --tests org.roketsim.core.MotorEngImportTest --tests org.roketsim.core.MotorStepBoundariesTest`.

- Initial focused run (before adding the two package-local arithmetic tests and guided failure test): BUILD SUCCESSFUL, exit 0, 26 s; log `roketsim-n37021-focused-1.log`.
- Final focused run: 76 tests / 5 suites / zero failures/errors/skips, exit 0, 17 s; log `roketsim-n37021-focused-2.log`.
- Full `assemble test verifyProductionDependencies`: 714 tests / 46 suites / zero failures/errors/skips, exit 0, 29 s; log `roketsim-n37021-regression.log`.
- All logs under `<local-evidence>`, with explicit PROCESS_EXIT entries. No failed test/build invocation occurred. Javadoc has 100 missing-documentation warnings; build passes.
- Full XML: `<local-evidence>`; HTML under corresponding reports/tests/test directory.

The 14 new tests cover span, clearance, engagement, actual mount/datum mismatch, valid resolver output, direct and guide no-progress classification, legacy ground-mode classification, preservation of genuine model-domain errors, retained unrepresentable-boundary behavior, derived overflow/raw-input distinction, current readiness and labeled historical evidence.

All 700 baseline tests still execute and pass (one authorized current/historical reporting assertion updated). Exact legacy schema and public signature tests, F50 anchors, source equivalence, NAT-035 timing/force regimes, NAT-036 cancellation/ground semantics, importer and boundary tests remain active.

Full suite detail (tests / failures / errors / skipped):

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
- `org.roketsim.core.simulation.MotorBoundaryExecutionTest`: 2 / 0 / 0 / 0
- `org.roketsim.core.simulation.RecoveryTimingTest`: 7 / 0 / 0 / 0
- `org.roketsim.core.SimulationEngineTest`: 5 / 0 / 0 / 0
- `org.roketsim.core.SimulationResultTest`: 6 / 0 / 0 / 0
- `org.roketsim.core.SingleStageVehicleConfigurationTest`: 18 / 0 / 0 / 0
- `org.roketsim.core.SourceMotorCorrectionTest`: 12 / 0 / 0 / 0
- `org.roketsim.core.SourceMotorRuntimeTest`: 25 / 0 / 0 / 0
- `org.roketsim.core.StructuralInertiaTest`: 8 / 0 / 0 / 0
- `org.roketsim.core.StructuralMassTest`: 23 / 0 / 0 / 0
- `org.roketsim.core.TranslationalDynamicsTest`: 10 / 0 / 0 / 0
- `org.roketsim.core.VehicleInertiaTest`: 15 / 0 / 0 / 0

## Packaged consumers, dependencies and parity

Main JAR: `<local-evidence>`. Sources and Javadoc JARs assembled. All 204 production sources match the sources JAR after newline normalization; packaged classes are version 61 (Java 17).

Executed for each SourceMotorJarConsumer, RecoveryJarConsumer and GroundJarConsumer:

```powershell
& '<JAVA_HOME>/bin/java.exe' `
 --module-path '<local-evidence>' `
 --add-modules org.roketsim.core verification/<Consumer>.java
```

All three exit 0; logs `roketsim-n37021-SourceMotorJarConsumer.log`, `roketsim-n37021-RecoveryJarConsumer.log`, `roketsim-n37021-GroundJarConsumer.log`. Ignoring the new explicit exit-code lines, all three outputs are identical to the pre-correction consumer logs, including source test-only ground times, accepted segment counts and recovery detection/activation times. Consumer source files unchanged.

Executed `jdeps --print-module-deps <main-JAR>`: `java.base`, exit 0, log `roketsim-n37021-jdeps.log`. Gradle production dependency verification also passes; no runtime dependency changes.

Actual J-011 command:

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py `
 --java '<JAVA_HOME>/bin/java.exe' `
 --build-dir '<build-dir>' `
 --artifacts-dir '<artifacts-dir>'
```

Exit 0, NATIVE_TRAJECTORY_PARITY: PASS. Log `roketsim-n37021-trajectory.log`; raw Java/Python outputs and comparison-summary.json retained in the artifacts directory. Python source and historical tolerance/oracle files unchanged.

| Run | Samples | Events | Comparisons | Exact | Nonexact within unchanged tolerances |
|---|---:|---:|---:|---:|---:|
| Primary | 1584 | 3 | 147521 | 147511 | 10 |
| Repeat | 1584 | 3 | 147521 | 147511 | 10 |
| Fine | 3167 | 3 | 294740 | 294714 | 26 |

Distinct configurations: 442261 comparisons, 442225 exact and 36 within unchanged tolerances; same totals as the pre-correction baseline. Including repeat: 589782 comparisons. Maximum absolute difference 8.881784197001252e-16, maximum relative difference 4.3829501452845633e-16.

All three ENG resource hashes remain the MOTOR-001 values and packaged bytes equal working resource bytes. No imported source data, mass observations, source identities or provenance rewritten. The unchanged TEST_ONLY vehicle fixtures do not establish physically sourced TEKNOFEST flight readiness.

## Final integrity and remaining limits

Legacy record schemas, legacy resolver arithmetic, motor/thrust/mass/CG equations, BasicDrag, gravity, RK4, NAT-035 trigger and regime rules, ground interpolation and cancellation algorithms are unchanged by the correction. No new physical model, integrator or flight loop. Existing API inventories pass; the only added public operation is historicalCoreCompatibility on the catalog record.

Nominal installation checks do not certify physical geometry/datum equivalence. Inertia, actual grain regression, certification evidence and TF/P/PS assembly equivalence remain unresolved. The NAT-026.1 interior representability limitation still produces explicit failure; it is not repaired here. Current source-only readiness is not an automatic physical-flight approval.

Final branch/HEAD unchanged; 14 tracked modifications and 33 untracked files, index empty. No commit/push/merge. Recommend independent engineering acceptance review of F1/F2/F3 closure; full NAT-037 remains pending. Do not begin N37-03.

N37-02.1 CORRECTION VERIFIED — ENGINEERING ACCEPTANCE PENDING
