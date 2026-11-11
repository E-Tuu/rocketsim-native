# NAT-035 v1.1 — Targeted frozen-conformance correction

Current implementation verification: **all required corrective gates PASS; independent owner acceptance pending; NOT COMMITTED**. The earlier STOP / NOT ACCEPTED checkpoint is retained verbatim below as historical evidence. It describes the superseded root-based implementation and must not be read as the current algorithm. No claim that the earlier implementation passed is made.

## Starting state and authorization

Correction preflight on 2026-11-04: branch `development/nat-035-apogee-recovery`, HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae` (accepted NAT-034). The existing uncommitted NAT-035 work was present: four tracked production modifications and nine untracked files, with no staged changes. This correction preserves the conforming shared-engine/physics work; no reset, clean, stash, revert, commit, merge, rebase or push. The owner's targeted v1.1 correction explicitly supersedes the earlier RK4-root apogee design and approves the additive fourth engine entrypoint and narrow inventory-test change.

## FIX-35-01/02 — Actual sampled algorithm and timing

`RecoveryRuntime3DOF` now observes only free-flight entry and accepted nonterminal free-flight endpoints. Guided-prefix ascent and NAT-018 reporting events are not recovery triggers. Positive WORLD vertical velocity at eligible entry establishes ascent history; otherwise a later accepted positive-velocity free-flight sample establishes eligibility and starts the monitored maximum at that sample. Initial zero/negative velocity alone never triggers.

The runtime retains `maximumSampledAltitudeM` and `maximumSampledAltitudeTimeS`. A higher sampled WORLD Z updates both; an equal maximum retains its original timestamp. After positive eligible ascent, the first accepted sample satisfying exactly:

```java
currentAltitudeM < maximumSampledAltitudeM - 0.01
```

is the single recovery detection sample. There is no additional velocity sign, acceleration, epsilon or root condition. No iterative search or extra RK4 apogee trial occurs. `RecoveryApogeeLocalizer3DOF.java` was removed from the untracked implementation; no source/test reference remains. The ordinary candidate endpoint is preserved, not replaced or interpolated.

```text
apogeeDetectionTimeS = acceptedDetectionPoint.timeS()
activationTimeS = apogeeDetectionTimeS + explicitlyConfiguredDelayS
```

Delay is never measured from the sampled maximum or a physical root. Nonfinite arithmetic and positive delays that round to no advancement remain explicit `INVALID_RECOVERY_ACTIVATION_TIME` numerical failures. A private runtime `TimingFailure` replaces the obsolete dependency on the root-localizer exception; it does not introduce a global error architecture.

For compatibility with this uncommitted development API, `EventType.PHYSICAL_APOGEE` and `physicalApogee()` retain their names, with explicit Javadoc: they denote the **v1.1 sampled altitude-drop detection**, not a localized physical maximum. Event point and lower/upper timestamps all equal the accepted detection timestamp. The sampled maximum timestamp is retained by the per-run monitor, not represented as an exact physical apogee time. No new public API beyond the approved working design is added by this correction.

Zero-delay activation is checked at that same accepted detection boundary after ground screening, including when it is the last allowed positive-duration segment. It replaces that sample's physics/state metadata without duplicating its time, position or velocity. The segment arriving at the boundary used body aerodynamics throughout. Positive-delay samples remain PENDING/FREE_FLIGHT with baseline body drag; successful activation switches to ACTIVE/RECOVERY_DESCENT and NAT-034-only drag.

## FIX-35-03 — Additive API compatibility

The engine's four exact public run methods are:

```java
SimulationExecution3DOF run(SimulationRunConfiguration3DOF configuration)
SimulationExecution3DOF run(SimulationRunConfiguration3DOF configuration,
                           IntegrationStepAlignmentMode alignmentMode)
SimulationExecution3DOF run(SingleStageVehicleRunConfiguration3DOF configuration)
RecoveryFlightResult3DOF run(RecoveryRunConfiguration3DOF configuration)
```

`SingleStageVehicleConfigurationTest.exactNewAndPreservedSchemasAndPublicEntrypoints` now expects four engine public methods, still two evaluator methods and zero evaluator/engine fields. It individually checks every original engine signature and return type and the new recovery signature/return type. Existing exact record-schema assertions are unchanged (context14/run7/physics13/mass2/execution3/result1/sample2/data2). The original failing count guard is documented in the historical checkpoint; no unrelated legacy test or numerical tolerance was weakened.

## FIX-35-04 — Ground and force-regime ordering

Only `FlightEventDetector3DOF.detect(current, actualCandidate, eventContext)` on the authorized segment determines legacy ground reports. Its source and linear estimated-event semantics are unchanged. A valid ground crossing terminates with GROUND before sampled trigger handling or activation, including endpoint coincidence and positive thrust. Interior terminal candidates remain rejected; endpoint terminal candidates are recorded, per the existing policy. Estimated event time is not certified as an RK4 impact root.

Removed both `RECOVERY_GROUND_ORDER_UNRESOLVED` paths:

- A point's altitude at/below the plane alone no longer fabricates failure or a ground event. Required start-above/end-at-or-below crossing evidence comes from the existing detector on the executed interval. Without a verified report, activation proceeds to the actual motor-thrust check.
- No unused full-parent RK4 trial is evaluated to override a shorter activation-aligned segment. Its hypothetical ground estimate cannot force numerical failure. The regression fixture independently computes that full-parent estimate and shows it at/before activation while the authorized shorter endpoint remains above ground; the executed run activates at the shorter endpoint and later terminates normally with GROUND.

Actual authoritative thrust >0 at activation still rejects before recovery force is applied: outcome UNSUPPORTED, code `RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST`, activation/detection time, delay and motor thrust details. No automatic burnout postponement or inferred phase-label threshold.

The pre-existing fixed per-segment `active` boolean remains captured by the derivative callback: all k1/k2/k3/k4 evaluations ending at activation use body aerodynamics, and all four in the next segment use recovery aerodynamics. Actual acceleration/forces are stored in the typed recovery snapshot; no fake BasicDragResult, body/parachute addition, impulse or state discontinuity. Parent remainder/source alignment and one shared engine loop remain. No new ground solver, RK4 method, motor law, guide mechanics or recovery deployment during guided flight.

## Independent focused evidence

`RecoveryIntegrationTest` has **15 tests**, `simulation.RecoveryTimingTest` **7 tests**. Focused fixtures cover:

- Accepted positive-ascent eligibility, later ascent after zero/negative entry, maximum value/time retention, first qualifying detection, once-only trigger, no velocity-sign gate and no zero-velocity-touch trigger without altitude drop.
- Strict threshold comparisons at the actual representable boundary `2.0 - 0.01`, its `Math.nextUp` and `Math.nextDown`. This tests the frozen double expression without falsely assuming an exact binary representation of decimal 0.01 or adding a tolerance.
- Detection on the unchanged legacy accepted grid and unchanged NAT-018 APOGEE reports before activation, independently compared to original engine runs. No root trial state is accepted.
- Zero delay, explicit delay from detection, pending body physics, single activation, input/time validation and collapsed-positive-delay failure.
- Exact direct-RK4 equality for the segment arriving at activation and the next departing segment, proving fixed body/recovery regime selection including endpoint k4.
- NAT-034-only aerodynamic force including WORLD wind, existing motor and mass snapshots, no body double counting, under-thrust rejection and deterministic repeated runs.
- Actual detector-generated ground endpoint coincidence under thrust, authorized integration crossing before activation, ordinary ground while pending, below-plane point without crossing evidence, and the discarded-parent-estimate regression above.
- Normal launch-guide handoff, preserved guide prefix, no recovery on unreleased guide, legacy run/schema independence and budget/result immutability.

Native coast step sensitivity (delay 0.25 s), from actual test stdout:

| Nominal step, s | Accepted detection, s | Activation, s |
|---:|---:|---:|
| 0.08 | 2.0800000000000005 | 2.3300000000000005 |
| 0.04 | 2.000000000000001 | 2.250000000000001 |
| 0.005 | 1.9949999999999795 | 2.2449999999999797 |

These are detection-sample times, **not physical apogee roots**. Both maximum sampling and the first strict-drop sample depend on timestep/source alignment. This is frozen model behavior, not evidence of a universal root error bound, monotone convergence order or physical parachute validation. No new numerical tolerance is used for these comparisons.

## Executed verification and raw commands

Environment: Temurin Java/javac17.0.20.1, Gradle8.12.1, Python3.12.4, NumPy2.5.2. Existing isolated build/cache reused without unnecessary clean/repeated builds. Core directory `roketsim-native-workspace/roketsim-core`.

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --tests org.roketsim.core.RecoveryIntegrationTest --tests org.roketsim.core.simulation.RecoveryTimingTest --tests org.roketsim.core.SingleStageVehicleConfigurationTest --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
& '<JAVA_HOME>/bin/java.exe' --module-path '<local-evidence>' --add-modules org.roketsim.core verification/RecoveryJarConsumer.java
& '<JAVA_HOME>/bin/jdeps.exe' --print-module-deps '<local-evidence>'
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

All command output was redirected to separate logs under `<local-evidence>`; each completed process exit code was inspected.

| Execution | Actual result |
|---|---|
| First restricted focused invocation (`focused.log`) | Exit1, could not connect to local Gradle daemon; no tests executed. Rerun with local-daemon access. |
| First actual compile (`focused-execution.log`) | Exit1, two compile errors in new test helper using wrong Vector.of form; fixed helper to existing array API. No test execution claimed. |
| Focused after helper correction (`focused-2.log`) | 39/39 passed:15 integration+6 timing+18 existing vehicle; exit0,27s |
| Final focused after stronger ground fixtures (`focused-3.log`) | **40/40 passed**:15 integration+7 timing+18 existing vehicle; exit0,19s |
| Full Java regression (`regression.log`) | **643 tests /41 suites;643 passed,0 failures/errors/skips**, exit0,26s |
| NAT-034/NAT-031–033.1/MOTOR-001/NAT-026/026.1 | 17 recovery-drag /24 guide /19 importer /18 motor-boundary tests passed within full suite |
| Assembly/dependency gate | Main/sources/Javadoc JARs assembled; verifyProductionDependencies PASS, same full invocation exit0 |
| Packaged main-JAR consumer (`consumer.log`) | RECOVERY_JAR_CONSUMER: PASS, exit0; direct and guide-to-recovery paths, correct actual forces/model metadata and legacy repeat |
| Runtime dependency check (`jdeps.log`) | java.base only, exit0 |
| Actual J-011 trajectory parity (`trajectory.log`) | NATIVE_TRAJECTORY_PARITY: PASS, exit0; unchanged Python reference/historical fixtures and tolerances |

The consumer actually reports direct detection7.039999999999938, activation7.289999999999938, termination26.851135328334358; guided detection7.029999999999937, activation7.279999999999937, termination26.850812767552632 seconds. Different launch paths are not asserted physically identical.

Actual J-011 comparisons:

| Run | Samples | Events | Numeric comparisons | Exact | Accepted nonexact |
|---|---:|---:|---:|---:|---:|
| primary |1584|3|147521|147511|10|
| repeat |1584|3|147521|147511|10|
| fine |3167|3|294740|294714|26|

Two distinct configurations:442261 numerical/442225 exact/36 accepted last-bit differences. Repeat-inclusive:589782/589736/46. Maximum absolute8.881784197001252e-16, relative4.3829501452845633e-16. Legacy state/time/event comparisons pass unchanged strict policies; accepted upstream differences use historical quantity-specific tolerances. This does not claim Python recovery parity or OpenRocket parachute equivalence. Raw traces, stdout/stderr, anchors and comparison-summary.json are in `<local-evidence>`.

Full XML/reports: `<local-evidence>` and `reports/tests/test/`. Focused logs retain previous execution counts; full XML is the latest full suite. Existing Javadoc warning display reaches100 nonblocking warnings; no gate suppressed. The initial shell's Python Windows alias was unavailable, so the established reference venv executable was used; the failed alias attempt made no edits. Process inventory via CIM was unavailable in the restricted environment; all launched verification sessions themselves returned completed exit codes, not assumed success.

## Exact final file scope and integrity

Paths below relative to `roketsim-native-workspace/`.

Tracked modifications already present before correction, retained:

- `roketsim-core/src/main/java/org/roketsim/core/dynamics/TranslationalDynamicsEvaluator.java`
- `roketsim-core/src/main/java/org/roketsim/core/simulation/PhysicsEvaluator3DOF.java`
- `roketsim-core/src/main/java/org/roketsim/core/simulation/SimulationEngine3DOF.java`
- `roketsim-core/src/main/java/org/roketsim/core/simulation/LaunchGuideSimulator3DOF.java`

Newly authorized tracked-test correction:

- `roketsim-core/src/test/java/org/roketsim/core/SingleStageVehicleConfigurationTest.java`

Untracked working files retained/updated:

- `roketsim-core/src/main/java/org/roketsim/core/simulation/RecoveryConfiguration3DOF.java`
- `roketsim-core/src/main/java/org/roketsim/core/simulation/RecoveryRunConfiguration3DOF.java`
- `roketsim-core/src/main/java/org/roketsim/core/simulation/RecoveryFlightResult3DOF.java` (sampled-detection naming clarification)
- `roketsim-core/src/main/java/org/roketsim/core/simulation/RecoveryRuntime3DOF.java` (targeted algorithm/ordering correction)
- `roketsim-core/src/test/java/org/roketsim/core/RecoveryIntegrationTest.java`
- `roketsim-core/src/test/java/org/roketsim/core/simulation/RecoveryTimingTest.java`
- `roketsim-core/verification/RecoveryJarConsumer.java` (output labels now say detection)
- `docs/verification/nat-035-apogee-recovery.md`

Removed obsolete untracked production file: `roketsim-core/src/main/java/org/roketsim/core/simulation/RecoveryApogeeLocalizer3DOF.java` (never part of accepted HEAD).

Final working set:5 tracked modifications+8 untracked files; index empty. HEAD/branch unchanged. No Python source, historical acceptance document, fixture, ADR, RK4/event detector, NAT-034 equation, accepted profile or numerical tolerance modified. No dependency/module/build configuration change. No commit/push/merge; NAT-036 not started.

Remaining boundaries: v1.1 sampling delay/step sensitivity; existing NAT-026.1 representability limits; explicit nonrepresentable activation/remainder failures; linear ground estimates not true impact roots; single fully deployed constant-CdA parachute with caller-owned applicability/provenance; no inflation, opening shock, impact, body-plus-parachute or 6DOF physics. These corrections pass the executed implementation gates; owner acceptance is separate and still required.

---

# Historical checkpoint — superseded root-based implementation and STOP evidence

The following earlier record is preserved unchanged for audit history. Its root-localization algorithm, ambiguity failure policy, pending API decision and outstanding gates have been superseded by the correction and executions above.

# NAT-035 â€” Apogee-triggered recovery integration (uncommitted review checkpoint)

Status: **STOP / NOT ACCEPTED**. Implementation work and evidence are preserved. No commit, staging, publication or historical test modification. A required legacy regression fails an exact public-API inventory assertion; the compatibility decision below needs owner review before correction/continuation.

## Baseline and scope

Verified clean starting branch `development/nat-034-constant-recovery-drag`, exact HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae`. Created local branch `development/nat-035-apogee-recovery` directly from it. HEAD remains the baseline. Relevant actual engine, physics, dynamics, guide, event, recorder, result and numerical declarations were inspected; no repository-wide audit or external physics source substitution.

The working implementation adds an explicit recovery path while retaining existing public constructors/record components. Normal runtime states are NOT_TRIGGERED/PENDING/ACTIVE; outcomes and failures are separate. Existing NAT-034, Basic Drag, RK4, event detector, source alignment and historical reference data are unchanged.

## Blocking finding NAT035-API-01

Full Java regression: `SingleStageVehicleConfigurationTest.exactNewAndPreservedSchemasAndPublicEntrypoints()` failed at line 55:

```text
expected public SimulationEngine3DOF methods: 3
actual: 4
org.opentest4j.AssertionFailedError: expected: <3> but was: <4>
```

The additional method is `public RecoveryFlightResult3DOF run(RecoveryRunConfiguration3DOF configuration)`. All three original run methods remain, but the accepted API-inventory assertion fixes the total count at three (the NAT-026 comment explicitly accounts for its earlier numerical-mode addition). This guard was missed during preflight and exposed by full regression. No physical/numerical mismatch has been observed in the executed tests; that does not make the failed mandatory gate a PASS.

Original test/source/assertion is preserved without change. No correction to this finding was applied. Evidence: `<local-evidence>`, `roketsim-nat035-regression.log`, and `roketsim-nat035-build/test-results/test/TEST-org.roketsim.core.SingleStageVehicleConfigurationTest.xml` under that same explicit Temp root.

Minimum decisions:

1. **Recommended review option:** explicitly approve the additive fourth engine entrypoint and the narrowly corresponding API-inventory assertion update, while retaining all original signature/constructor/schema assertions and physics fixtures/tolerances. This is a compatibility-contract update, not a numerical tolerance change.
2. Alternatively approve a different thin opt-in public entry boundary delegating into the same engine without increasing its public-method count. No alternate engine/flight loop should be created. Public placement should be decided explicitly rather than relocated merely to evade the test.

After that decision, rerun the failing/focused tests and complete all outstanding regression/parity/consumer gates. The working implementation is not accepted solely because new focused tests pass.

## Working files and API (not an accepted freeze)

Modified existing production files under `roketsim-core/src/main/java/org/roketsim/core/`:

- `dynamics/TranslationalDynamicsEvaluator.java`: shared existing thrust/gravity and F/m arithmetic; nested `ForceEvaluation(forces,derivative)` and `evaluateWithAerodynamicForce(...)`, avoiding baseline-only profile metadata for recovery.
- `simulation/PhysicsEvaluator3DOF.java`: one private upstream calculation bundle in original evaluation order; legacy evaluate methods retain their schemas; package-private recovery dispatch bypasses BasicDrag only when recovery is selected.
- `simulation/SimulationEngine3DOF.java`: new proposed public recovery run overload and one private shared flight loop; per-run recovery branch handles accepted segments; existing public overloads retain baseline selection.
- `simulation/LaunchGuideSimulator3DOF.java`: `run(LaunchGuideRunConfiguration3DOF,RecoveryConfiguration3DOF)` reuses existing guided prefix and calls the same engine recovery continuation after exit. No guided deployment.

New production files under the simulation package:

- `RecoveryConfiguration3DOF(double effectiveDragAreaM2,double deploymentDelayS)`: finite positive CdA, finite nonnegative explicit delay.
- `RecoveryRunConfiguration3DOF(SingleStageVehicleRunConfiguration3DOF vehicle,RecoveryConfiguration3DOF recovery,IntegrationStepAlignmentMode alignmentMode)`: explicit direct-flight opt-in and immutable existing vehicle/configuration references.
- `RecoveryFlightResult3DOF`: immutable samples, legacy reports, recovery events, outcome, termination time, accepted-segment count, optional detailed failure and guide transitions. Nested typed Physics/Sample/Event/Failure and enums keep applied force/model/state distinct from baseline-only results.
- `RecoveryApogeeLocalizer3DOF`: bounded RK4 bracket reintegration and zero-endpoint confirmation, no general all-root isolation.
- `RecoveryRuntime3DOF`: package-private per-run state, timing and one-segment handling; no flight loop or global mutable state.

Additional new files: `src/test/java/org/roketsim/core/RecoveryIntegrationTest.java`, `src/test/java/org/roketsim/core/simulation/RecoveryTimingTest.java`, `verification/RecoveryJarConsumer.java`, and this record. All paths above are relative to `roketsim-native-workspace/roketsim-core/` unless stated otherwise.

## Working physical, event and recording behavior

Before activation the original body-drag evaluator/dynamics path is used. After activation, density/relative flow/current motor and mass/gravity are obtained from the same shared upstream composition; NAT-034 provides the sole aerodynamic force. No body term is added. Dynamics' own force-composition/division authority is reused; no fake Cd0 or BasicDragResult. New active snapshots have empty optional bodyDrag, CONSTANT_PARACHUTE_DRAG metadata and actual forces/derivative. Old result schemas remain unchanged.

Direct initial positive WORLD vz counts as eligible ascent. Guided ascent is not consumed as eligibility history; monitoring starts at the accepted free-flight entry and only positive free-flight states establish ascent. Initial zero/negative vz does not trigger. Existing NAT-018 reports remain independent and can include guided/touch apogees; recovery events are separately typed PHYSICAL_APOGEE and RECOVERY_ACTIVATION. Only the first physical trigger is consumed.

Positive-to-nonpositive candidate endpoints supply the supported bracket. If the endpoint is exactly zero, another pre-activation RK4 trial from it supplies a direction witness: positive means touch/no trigger, zero means explicit APOGEE_ZERO_PLATEAU_UNRESOLVED, negative permits localization. This is a bounded supported test, not a guarantee against multiple hidden crossings. Trials are not recorded or budgeted as accepted flight segments.

Localization bisects duration from the same accepted start and reintegrates using the unchanged classical RK4. It stops at adjacent representable durations and accepts the first nonpositive-velocity endpoint. It never sets velocity to zero or substitutes a linear NAT-018 state. A time that cannot advance or reproduce the supported bracket is an explicit numerical failure. The independent loop cap is `2*(Double.MAX_EXPONENT-Double.MIN_EXPONENT+Long.SIZE)` = **4218**, a conservative bound from the double exponent lattice/word size, not a physical time tolerance.

No global epsilon or root tolerance is introduced. The exposed bracket/one-ULP timestamp resolution does not bound RK4 state truncation or physical-model error. The constant-acceleration analytic test uses an explicit local eight-ULP allowance for short weighted RK4 polynomial arithmetic, separately from root convergence. This allowance does not modify any historical tolerance or establish an eight-ULP bound for arbitrary trajectories.

Scheduled activation uses trigger+delay, rejecting nonfinite arithmetic and positive delays that collapse onto trigger time. A positive-duration segment ending at activation uses baseline physics for all four callbacks; the next segment uses the active regime for all four callbacks. The regime is fixed once per segment, never selected by `time >= activation` inside the derivative. The same accepted point is retained at activation, with one sample whose post-transition physics/phase/state are updated rather than adding a duplicate timestamp. Pending remains FREE_FLIGHT, active becomes RECOVERY_DESCENT.

At activation actual authoritative motor thrust > 0 produces outcome UNSUPPORTED, failure `RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST`, with activation/trigger/delay/thrust details. No recovery force or activation event is produced. Last accepted pre-activation history is retained. No burnout-label shortcut or postponement.

Ground screening precedes activation. Verified endpoint ground wins even under thrust. Interior ground retains legacy estimated-event/rejected-candidate behavior; it is not claimed to be an exact integrated impact. If an unsplit legacy ground estimate orders ground before/at activation but the reintegrated activation endpoint remains above ground, the working path reports RECOVERY_GROUND_ORDER_UNRESOLVED instead of treating the estimate as exact or silently deploying. Its evidence includes both times. No new impact or ground-root model.

Accepted positive-duration segments consume the opt-in flight budget; localization/confirmation trials do not. Parent-step remainders are retained and source boundaries still come from NAT-026/026.1. Unrepresentable boundary/remainder remains explicit. Reaching the step cap stops execution before any further activation/advance operation; no budget is reset or expanded. Guided prefix points are preserved, with its accepted segment count carried into recovery; unreleased guide outcomes never activate recovery.

## Actually executed verification

Java 17 Temurin `<JAVA_HOME>`, Gradle Wrapper 8.12.1. No new dependency. Working directory: `roketsim-native-workspace/roketsim-core`.

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --tests org.roketsim.core.RecoveryIntegrationTest --tests org.roketsim.core.simulation.RecoveryTimingTest --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
```

| Execution | Actual result |
|---|---|
| First focused run | 19 tests passed, exit 0; Gradle 29 s; focused-1 log |
| Expanded focused run | 22 tests passed (19 integration + 3 timing), exit 0; Gradle 21 s; focused-2 log |
| Complete Java regression | **643 tests / 41 suites; 642 passed, 1 failure, 0 errors/skips; exit 1**, Gradle 31 s |
| Failing regression | Exact public method-count assertion, expected3/actual4; preserved above |
| Existing guide/MOTOR/boundary suites in full run | 24 / 19 / 18 passed |
| Main/sources/Javadoc JAR assembly | Tasks completed before failed test task; existing 100-warning Javadoc display limit, not a complete warning inventory |
| verifyProductionDependencies | Not reached after test failure; no PASS claim |
| Packaged recovery consumer | Source prepared, **not executed** after STOP |
| Runtime jdeps | **Not executed** |
| Actual J-011 or other Pythonâ€“Java parity / integrity gates | **Not executed** after STOP; no current parity/compatibility PASS claim |

Focused fixtures: analytic constant a=-5, initial vz10,z10 gives apogee t2,z20; exact endpoint zero/negative continuation; quadratic-velocity touch; cubic-velocity/zero plateau; initial zero/negative ascent exclusion; zero/positive delay; body physics while pending; exact direct-RK4 checks across both activation sides; NAT-034-only wind force; ground during delay; isolated accepted-ground endpoint precedence under positive thrust; ambiguous estimated-ground coincidence; detailed under-thrust rejection; invalid config/time overflow/collapsed delay; one deployment/repeatability/immutability; successful and unreleased guide paths; schema/legacy independence.

The isolated ground-precedence fixture supplies a synthetic accepted boundary/event to the terminal handler. It is a handler ordering test, not evidence of a fully integrated native trajectory landing at precisely that boundary. Actual estimated-ground coincidence is separately tested as unresolved rather than certified exact coincidence.

Step sensitivity output (same synthetic coast fixture, actual native physics): apogee h=.08 -> `1.9479018070007013`, h=.04 -> `1.9479018105892507`, reference h=.005 -> `1.947901812028644` seconds. Finer error decreases against this reference; no universal order/accuracy or physical validation claim.

Raw evidence in `<local-evidence>`: `roketsim-nat035-focused-1.log`, `roketsim-nat035-focused-2.log`, `roketsim-nat035-regression.log`, `roketsim-nat035-api-finding.json`; complete XML/reports under `roketsim-nat035-build/test-results/test/` and `reports/tests/test/`. No fixtures, historical tests, or tolerances were changed to hide the failure.

## Remaining gate

Resolve NAT035-API-01 before further implementation/correction. Full regression must pass and all required packaged consumer, dependency/jdeps and actual legacy parity/integrity checks must execute successfully. Review exact-zero confirmation horizon/multiple-crossing limitations, ground-order ambiguity and result/API boundaries independently. No claim of independent owner acceptance, OpenRocket equivalence or physical parachute certification. Current working files are available for review; nothing is staged or committed.
