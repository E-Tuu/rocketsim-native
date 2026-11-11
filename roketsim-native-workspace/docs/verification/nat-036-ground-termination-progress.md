# NAT-036 v1.0 - Ground contact, termination and progress monitoring

Implementation verification: **PASS - OWNER REVIEW REQUIRED - NOT COMMITTED**. This is executed implementation evidence, not automatic engineering acceptance. NAT-037 is not started.

## 1. Starting state and preserved work

2026-11-04 preflight: repository `<local-path>`, workspace `roketsim-native-workspace/`, branch `development/nat-035-apogee-recovery`, HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae`. The accepted NAT-035 v1.1 implementation was **uncommitted**, as stated by the owner: five tracked modifications and eight untracked files, empty index. HEAD is accepted NAT-034, not an invented NAT-035 commit.

Starting dirty files, relative to workspace:

- Tracked: `roketsim-core/src/main/java/org/roketsim/core/dynamics/TranslationalDynamicsEvaluator.java`; `simulation/PhysicsEvaluator3DOF.java`, `simulation/SimulationEngine3DOF.java`, `simulation/LaunchGuideSimulator3DOF.java` beneath that same Core package root; `roketsim-core/src/test/java/org/roketsim/core/SingleStageVehicleConfigurationTest.java`.
- Untracked: simulation `RecoveryConfiguration3DOF.java`, `RecoveryRunConfiguration3DOF.java`, `RecoveryFlightResult3DOF.java`, `RecoveryRuntime3DOF.java`; `src/test/java/org/roketsim/core/RecoveryIntegrationTest.java`, `src/test/java/org/roketsim/core/simulation/RecoveryTimingTest.java`; `verification/RecoveryJarConsumer.java`; `docs/verification/nat-035-apogee-recovery.md`.

All 13 files were copied with a SHA-256 manifest outside Git to `<local-evidence>` before edits. No reset, clean, stash, revert, commit, merge, rebase, push, branch switch or unrelated edit. Nine starting files remain byte-for-byte identical, including NAT-035 documentation/tests/result/configuration schemas, its consumer, PhysicsEvaluator and TranslationalDynamicsEvaluator. Four overlap integration points are extended: engine, guide executor, recovery runtime and narrow API inventory test. The earlier NAT-035 STOP evidence remains in its untouched record; this task's owner instruction identifies the corrected NAT-035 baseline as engineering accepted.

## 2. Actual authority inventory and integration

Sources under `roketsim-core/src/main/java/org/roketsim/core/`:

| Responsibility | Existing authority | NAT-036 action |
|---|---|---|
| Launch position/velocity/direction | dynamics/LaunchConditions3DOF, InitialStateBuilder | Reuse; ground reference remains launch WORLD Z |
| Instantaneous body/recovery physics | simulation/PhysicsEvaluator3DOF; dynamics/TranslationalDynamicsEvaluator | Reuse without modifying either file relative to task start |
| Recovery drag | aerodynamics/RecoveryDragEvaluator3DOF | Unchanged NAT-034 equation and validation |
| Numerical integration | numerics/ClassicalRK4Integrator3DOF, FixedStepConfig | Unchanged |
| Ground/burnout/apogee reporting | simulation/FlightEventDetector3DOF | Unchanged NAT-018 linear estimates |
| Legacy history | simulation/FlightRecorder3DOF and recorded result types | Unchanged; guide uses the existing recorder |
| Accepted recovery segments and samples | simulation/RecoveryRuntime3DOF | Add optional control checkpoints, prepared sample commit and disabled-recovery handling; keep one scheduler |
| Single free-flight loop | simulation/SimulationEngine3DOF.continueShared | Extend policy selection; no second flight loop |
| Pad/guide searches and handoff | simulation/LaunchGuideSimulator3DOF.Execution | Extend optional control and uncapped policy, hand off to same engine loop |
| Source time boundaries | simulation/MotorTimeBoundarySchedule3DOF | Unchanged NAT-026/026.1 |

Direct call path: `SimulationEngine3DOF.runUntilGround -> continueUntilGround -> continueShared -> RecoveryRuntime3DOF.advance -> existing ClassicalRK4Integrator3DOF -> existing physics`. Ground checks use `FlightEventDetector3DOF.detect(current, actualCandidate, eventContext)`.

Guide call path: `runUntilGround -> LaunchGuideSimulator3DOF.runUntilGround` (package-private) `-> existing Execution.run -> accepted guide exit -> SimulationEngine3DOF.continueUntilGround -> same continueShared`. The existing guided loop/searches are not copied. `GroundRunExecution3DOF` contains monitoring, counters, outcome mapping and history adaptation only: **no flight integration loop, ground interpolation or force calculation**.

## 3. New public API

Package `org.roketsim.core.simulation`:

```java
public GroundRunResult3DOF SimulationEngine3DOF.runUntilGround(
    UntilGroundRunConfiguration3DOF configuration, RunControl3DOF control);

public record UntilGroundRunConfiguration3DOF(
    SingleStageVehicleRunConfiguration3DOF vehicle,
    Optional<LaunchGuideRunConfiguration3DOF> launchGuide,
    Optional<RecoveryConfiguration3DOF> recovery,
    IntegrationStepAlignmentMode alignmentMode) {}
```

All configuration fields/Optionals are nonnull. A supplied guide must use the same vehicle and alignment mode; existing guide applicability validation still applies. Empty recovery means baseline aerodynamics, not CdA=0. Empty guide means direct free flight. No new duration, fake infinite limit, implicit 60 s cutoff, hidden step cap or automatically inferred recovery/guide geometry.

The composed legacy base configuration still carries its original `maximumSteps`, because its physical dependencies are reused without changing its frozen schema. **Only the new execution policy ignores that field**, both while guided and in free flight. All four existing public `run(...)` methods retain their original cap/termination behavior. The inventory assertion now expects five public engine methods, still checks zero engine/evaluator fields, retains all exact legacy method/return-type and positional-record-schema checks, and adds the exact new signature/return-type assertion. PhysicsEvaluator public inventory remains two.

```java
public final class RunControl3DOF {
    public RunControl3DOF(MonitoringPolicy policy,
        Consumer<Progress> progress, Consumer<Warning> warnings);
    public void requestCancellation();
    public boolean isCancellationRequested();

    public record MonitoringPolicy(long progressIntervalNanos,
        long wallWarningIntervalNanos, double airborneWarningIntervalS,
        long historyWarningEverySamples) {}
    public enum Status { RUNNING, GROUND, CANCELLED, UNSUPPORTED,
        MODEL_DOMAIN_FAILURE, NUMERICAL_FAILURE, RESOURCE_FAILURE,
        MONITORING_FAILURE }
    public record Progress(double simulatedFlightTimeS,
        long elapsedComputationNanos, long acceptedSegments,
        double groundRelativeAltitudeM, double verticalVelocityWorldMS,
        FlightPhase3DOF flightPhase,
        RecoveryFlightResult3DOF.State recoveryState, Status executionStatus) {}
    public record Warning(String code, String message,
        Progress observation, long recordedSamples) {}
}
```

The cancellation flag and single-run claim are atomic. Controls are single-use: repeated execution requires a fresh control. Only cancellation request/inspection are intended as cross-thread operations. Observers are synchronous on the simulation thread and must return promptly; they may request cancellation. A GUI can call requestCancellation safely from another thread, but no GUI is implemented. Package-private clock/checkpoint observer constructors are deterministic V&V seams, not new public physical extension points.

```java
public record GroundRunResult3DOF(
    RunControl3DOF.Status terminationReason,
    List<RecoveryFlightResult3DOF.Sample> samples,
    List<FlightEventOccurrence3DOF> events,
    List<RecoveryFlightResult3DOF.Event> recoveryEvents,
    List<LaunchGuideTransitionResult> guideTransitions,
    long acceptedSegments, long elapsedComputationNanos,
    Optional<FailureDetail> failure,
    Optional<RunControl3DOF.Progress> finalProgress) {
    public record FailureDetail(String code, String message,
        Map<String,String> details) {}
}
```

Lists/maps are immutable defensive snapshots. Derived accessors `lastAcceptedSample()`, `groundEvent()`, `estimatedGroundContactTimeS()`, `estimatedGroundContactPositionWorldM()`, `estimatedGroundContactVelocityWorldMS()` and `recoveryState()` reuse existing nested snapshots/events. Contact getters never recalculate a competing estimate. A GROUND result requires exactly one authoritative ground event. No ground event is fabricated for other outcomes. Empty history/progress is possible if model/applicability validation fails before an initial physics sample is available. Null configurations/control and invalid monitoring/configuration shape remain ordinary input exceptions.

## 4. Ground and initial-condition semantics

Flat ground is the original launch-position WORLD Z plane; progress height is accepted WORLD Z minus that plane. The detector owns the strict previous-height>0, candidate-height<=0 crossing. Starting on the plane is not a landing. Direct initial downward velocity on the plane, below-plane entry, or a first below-plane trial without accepted positive-height history is UNSUPPORTED; no fictitious ground event or penetrating accepted sample is inserted. The public direct entry starts at the launch position, so its initial relative height is necessarily zero; explicit below-plane restart validation is tested at the internal accepted-entry/trial boundary, not claimed as a new public restart API. Zero-velocity direct launches may rise normally; an unsupported first below-plane trial fails rather than landing. Supported pad contact remains held until liftoff or cooperative cancellation.

For the actual authorized interval, NAT-018 still computes lambda=h0/(h0-h1), interpolated time, WORLD position and velocity. It is **an estimated contact**, not a new ground root or rigid-body first-contact solution. Interior contact records the event and rejects the trial endpoint. Endpoint contact retains the existing endpoint acceptance policy. No clamping, bounce, impulse, artificial zero velocity, stationary sample or post-contact activation/integration.

The actual terminal event is committed before future observer notifications. Cancellation after committed GROUND cannot replace it. Cancellation observed before the commit checkpoint discards that trial and its uncommitted events. Last accepted sample can precede estimated contact time and remain above ground; final progress therefore describes the last accepted state, not a manufactured contact state. Phase remains the last accepted FREE_FLIGHT/RECOVERY_DESCENT (or guide phase on interruption), not a fabricated LANDED state.

## 5. NAT-035 preservation and ground precedence

The frozen monitor still tracks accepted eligible free-flight maximum WORLD Z/time, requires positive ascent history, and triggers on the first strict sample drop >0.01 m. Delay starts at that detection sample. No root search or NAT-018 apogee trigger is introduced. Configured delay/CdA validation and numerical timing errors remain. Missing recovery disables only recovery monitoring/activation, not baseline physics.

The same RecoveryRuntime handles body-only segments, pending activation and recovery segments. Its fixed per-segment aerodynamic boolean still applies to k1/k2/k3/k4, including pre-activation endpoint k4. The new mode prepares physics before the final commit checkpoint so cancellation or a model failure cannot append half a sample/activation. Only successful activation replaces boundary metadata at the same accepted position, velocity and time. No duplicate timestamp or extra accepted segment is created for metadata replacement.

Ground is screened on the **actual** shortened activation segment before trigger/activation. No discarded full-parent trial is used. Ground before/at activation wins. With no valid ground event, actual positive authoritative thrust still yields UNSUPPORTED with `RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST` and activation/detection/delay/thrust details; no activation event or parachute force. Otherwise NAT-034-only recovery proceeds. The new ground path is tested for exact sample/event/force equality with the accepted NAT-035 bounded path when that path's cap does not bind, including the discarded-parent-estimate regression.

## 6. Cancellation, counters, monitoring and resources

Cancellation checkpoints: before new free-flight segments, after candidate RK4 evaluation, after endpoint physics preparation and immediately before history/event/activation commitment; before guided loop iterations/trials, in guide force evaluations used by departure bisection, inside guide derivative evaluations/localization searches, and before guide sample commit. Trials never enter accepted history. A cooperative commit batch is not interrupted halfway; cancellation arriving after its last checkpoint is observed at the next safe checkpoint.

Accepted positive-duration segments use a checked long counter (`Math.incrementExact`); initial samples, same-time activation metadata updates and discarded localization/RK4 trials do not increment it. Guide-to-free handoff retains the same counter. Legacy int counters and attempted-step/cap policies are unchanged. Counter overflow fails before append. The actual int-index capacity of the in-memory history is checked as a controlled RESOURCE_FAILURE where possible; no arbitrary smaller cap, truncation or fake physical limit. Real JVM exhaustion may occur earlier and **OutOfMemoryError is not promised recoverable**. No flight history is silently removed.

Progress publishes only accepted states. SimulatedFlightTimeS is accepted absolute simulation time minus ignition time, including any supported pad waiting; it is not a new legacy flightTime/termination-duration authority. Elapsed computation is from System.nanoTime at observation, stored as nonnegative nanos; finite simulated/relative-height arithmetic and monotonic-clock range are checked. No percentage or ETA exists.

All four monitoring intervals are **explicit positive operational inputs**; no production defaults or physical validity thresholds. Notification checks occur once per accepted segment, rate-limited by progressIntervalNanos, plus initial/final notifications. Warnings are evaluated on those notifications and can therefore be later than their threshold:

- LONG_COMPUTATION: elapsed wall computation since prior warning >= supplied wallWarningIntervalNanos.
- LONG_OBSERVED_AIRBORNE_TIME: positive accepted ground-relative altitude and simulated interval since prior warning >= airborneWarningIntervalS.
- GROWING_FLIGHT_HISTORY: accepted-history sample growth since prior warning >= historyWarningEverySamples.

They state observations only, infer no defect/cause/ETA, and never change step size, aerodynamic regime or termination. Deterministic injected-clock tests verify cadence and exact trajectory equality with monitoring enabled/disabled. A wall-clock-based low-advancement diagnostic is not separately claimed. Warnings are delivered to the caller's observer rather than accumulated into an unbounded duplicate warning history.

Example **consumer-specific settings**, not defaults: progress1,000,000 ns, wall warning10,000,000,000 ns, airborne interval60 s, history interval10,000 samples. The 60 s setting is a **nonterminal observation cadence**, not a duration cutoff. Tests use other explicit settings, including injected nanos, and a flight of196.30116769768148 s succeeds.

Exceptions in observers yield distinct MONITORING_FAILURE with accepted history retained. A terminal GROUND result is not replaced by an exception/cancellation in its final observer. A blocking observer itself is not preempted: clients must keep observers short and hand off expensive UI work. Physics callbacks are not observer callbacks. Existing model errors are caught only at the new policy boundary, mapped to MODEL_DOMAIN_FAILURE/UNSUPPORTED/numerical outcomes with source exception/code/message retained. Genuine numeric/finite/event/time/counter errors are distinct; legacy methods still propagate their accepted exceptions unchanged.

## 7. Focused V&V coverage and analytic fixtures

New `GroundTerminationTest`:17 tests. New `simulation/GroundExecutionTest`:15 tests. Together32 new tests; no unrelated test removed, skipped, disabled or weakened. Existing API-inventory count/signature extension is authorized by NAT-036.

| Required group | Executed evidence |
|---|---|
| V36-A1..10 | Normal no-recovery flight, recovery flight, authoritative event/getter identity, interior rejected trial, endpoint acceptance, last sample/contact separation, nonzero WORLD origin and relative progress; independent constant-velocity/acceleration operator fixtures |
| V36-B11..16 |196.30116769768148 s/3927 accepted segments with composed legacy maximumSteps=1; no duration/ETA/percentage fields; progress rate limiting; warning-enabled trajectory and events exactly equal reference |
| V36-C17..22 | Pre-requested cancellation retains initial sample/zero segments; accepted-progress cancellation; another-thread latch handshake; guide-boundary-search cancellation via deterministic checkpoint; cancellation before commit discards trial/events; post-GROUND cancellation cannot replace it |
| V36-D23..29 | Pending ground, actual endpoint coincidence, authorized above-ground activation, discarded-parent-estimate regression, zero delay, under-thrust details, NAT-035 exact equivalence and unchanged fixed-regime tests |
| V36-E30..35 | Unsupported plane/downward and below-ground/no-positive-history conditions; actual collapsed/overflowed times; invalid interpolation; checked-counter overflow; history index capacity; distinct model-domain/monitoring errors; no fictitious ground |
| V36-F | Complete675-test regression, exact schemas/signatures,32 new tests, Java17 assembly/dependency gate, two packaged consumers, actual J-011 parity |

Independent mathematical fixtures, using the unchanged RK4 and detector rather than a second production physics authority:

- Constant velocity: start t5,z3,vz=-2, h2, zero acceleration -> RK4 endpoint z=-1; lambda0.75, estimated ground t6.5,z0,vz=-2 exactly. Interior endpoint is not accepted; last accepted remains t5,z3.
- Constant acceleration: start t5,z10,vz0,a=-2,h4 -> endpoint z=-6,vz=-8; lambda0.625, estimated ground t7.5,vz=-5 exactly. This intentionally differs from true constant-acceleration impact time5+sqrt(10): tests verify existing **linear ground estimation**, not root accuracy.
- Endpoint reference: start t5,z4,vz0,a=-2,h2 -> endpoint t7,z0,vz=-4 exactly; lambda1. Ground wins over pending activation and that endpoint is retained.

These operator/handler fixtures are synthetic, not claims that the production atmosphere/motor composition was replaced by a configurable constant-acceleration engine. Integer/binary-exact expectations use exact assertions; no new numerical tolerance is introduced. Existing physical trajectories and NAT-035 force-regime checks remain independently exercised. Guide recontact keeps NAT-031's unsupported pre-impact handling, not an ordinary free-flight landing.

## 8. Actual commands, logs and results

Environment: Temurin Java/javac17.0.20.1, Gradle8.12.1; Python3.12.4/NumPy2.5.2 for the actual historical trajectory oracle. Existing offline cache reused; separate NAT-036 isolated build/cache. Working directory `roketsim-native-workspace/roketsim-core`.

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --tests org.roketsim.core.GroundTerminationTest --tests org.roketsim.core.simulation.GroundExecutionTest --tests org.roketsim.core.RecoveryIntegrationTest --tests org.roketsim.core.simulation.RecoveryTimingTest --tests org.roketsim.core.SingleStageVehicleConfigurationTest --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
& '<JAVA_HOME>/bin/java.exe' --module-path '<local-evidence>' --add-modules org.roketsim.core verification/GroundJarConsumer.java
& '<JAVA_HOME>/bin/java.exe' --module-path '<local-evidence>' --add-modules org.roketsim.core verification/RecoveryJarConsumer.java
& '<JAVA_HOME>/bin/jdeps.exe' --print-module-deps '<local-evidence>'
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

Output redirection is to the individual log paths below, with each completed process exit code inspected. No unexpected test/build failure occurred in these NAT-036 executions; first compile and all focused expansions passed. No clean-build repetition, oracle regeneration or tolerance relaxation.

| Executed gate | Actual outcome | Log under <local-evidence> |
|---|---|---|
| Initial Java compile | exit0,15s | roketsim-nat036-compile.log |
| Focused first |66 passed:26 new+22 NAT-035+18 API; exit0,20s | roketsim-nat036-focused-1.log |
| Focused expanded |71 passed:31 new+22+18; exit0,17s | roketsim-nat036-focused-2.log |
| Focused final |**72 passed:32 new+22+18**, exit0,18s | roketsim-nat036-focused-3.log |
| Complete Java suite |**675 tests/43 suites,0 failures/errors/skips**;643 prior+32 new; exit0 | roketsim-nat036-regression.log |
| Java17 assembly/production dependencies | Main/sources/Javadoc JARs and verifyProductionDependencies PASS; full invocation25s,exit0 | same regression log |
| Guide/NAT-034/NAT-035/importer/boundary suites |24/17/22/19/18 passed within full suite | same full XML |
| New packaged consumer | GROUND_JAR_CONSUMER: PASS,exit0; direct/recovery, guide/recovery, pre-cancellation and legacy repeat | roketsim-nat036-consumer.log |
| Existing NAT-035 packaged consumer | RECOVERY_JAR_CONSUMER: PASS,exit0, unchanged source | roketsim-nat036-recovery-consumer.log |
| Runtime dependencies | java.base only,exit0 | roketsim-nat036-jdeps.log |
| Actual J-011 Python-Java trajectory | NATIVE_TRAJECTORY_PARITY: PASS,exit0 | roketsim-nat036-trajectory.log |

Full XML: `<local-evidence>`; HTML reports beneath the same build's `reports/tests/test/`. Existing Javadoc reaches its100 nonblocking-warning display limit; this is not a complete warning inventory or a suppressed failure. All gates passed without changing build/module/dependency declarations.

New consumer output: direct estimated ground26.851135328334358, last accepted26.839999999999517,1343 accepted segments; guided estimated ground26.850812767552632, last accepted26.849999999999515,1358 segments. The differing estimate and accepted timestamps are intentional and verified, not clamped impact states. Existing recovery consumer reports the same accepted detection/activation/termination values as the NAT-035 checkpoint.

Actual J-011 comparisons using unchanged real Python production/reference fixtures and Java legacy execution:

| Run | Samples | Events | Numeric | Exact | Accepted nonexact |
|---|---:|---:|---:|---:|---:|
| primary |1584|3|147521|147511|10|
| repeat |1584|3|147521|147511|10|
| fine |3167|3|294740|294714|26|

Two distinct configurations: **442261 comparisons/442225 exact/36 accepted upstream last-bit differences**. Repeat-inclusive589782/589736/46. Max absolute8.881784197001252e-16, relative4.3829501452845633e-16 under unchanged historical quantity-specific policies; legacy trajectory state/time/event checks pass. Raw traces, anchors, stderr and `comparison-summary.json`: `<local-evidence>`. This is legacy parity, not a claim that Python implements the new control/monitoring policy or that recovery is physically validated against OpenRocket.

## 9. Exact NAT-036 file scope, final Git and limitations

New files relative to workspace:

1. `roketsim-core/src/main/java/org/roketsim/core/simulation/UntilGroundRunConfiguration3DOF.java`
2. `roketsim-core/src/main/java/org/roketsim/core/simulation/RunControl3DOF.java`
3. `roketsim-core/src/main/java/org/roketsim/core/simulation/GroundRunResult3DOF.java`
4. `roketsim-core/src/main/java/org/roketsim/core/simulation/GroundRunExecution3DOF.java`
5. `roketsim-core/src/test/java/org/roketsim/core/GroundTerminationTest.java`
6. `roketsim-core/src/test/java/org/roketsim/core/simulation/GroundExecutionTest.java`
7. `roketsim-core/verification/GroundJarConsumer.java`
8. `docs/verification/nat-036-ground-termination-progress.md`

Four overlaps with preserved NAT-035 work:

- `roketsim-core/src/main/java/org/roketsim/core/simulation/SimulationEngine3DOF.java`
- `roketsim-core/src/main/java/org/roketsim/core/simulation/LaunchGuideSimulator3DOF.java`
- `roketsim-core/src/main/java/org/roketsim/core/simulation/RecoveryRuntime3DOF.java` (already untracked at start)
- `roketsim-core/src/test/java/org/roketsim/core/SingleStageVehicleConfigurationTest.java`

Final branch/HEAD unchanged. Combined NAT-035/NAT-036 tree: **5 tracked modifications+16 untracked files, empty index**. Nine other starting files are byte-identical to the snapshot manifest. No file removed; no Python, historical fixture/report, NAT-034/035 force law, mass/motor/atmosphere authority, global numerical tolerance, RK4, event detector, legacy recorder/result schema, ADR or runtime dependency changed. `git diff --check` passes. No commit/push/merge/rebase, no engineering-acceptance declaration.

Limitations retained: flat launch-plane/translational-point ground reference; linear estimated contact rather than a root; existing source-boundary representability limits; existing accepted-sample recovery sensitivity; ideal guide and unsupported recontact physics; finite JVM memory/int-index histories and checked long counters; cooperative cancellation cannot forcibly interrupt a blocking caller observer or guarantee recovery from every resource exhaustion. No terrain, first-airframe contact, impact/rebound, inflation/opening shock, new event framework, alternative integrator or NAT-037 work.
