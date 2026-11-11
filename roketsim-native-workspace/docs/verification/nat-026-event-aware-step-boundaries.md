# NAT-026 — Event-aware motor step boundaries

Date: 2026-11-01. Contract: owner-approved NAT-026 v1.1. Scope is numerical scheduling, not a motor import or physical model change.

## Git baseline and local API preflight

Repository `E-Tuu/rocketsim-native`; starting branch `development/nat-025c-vehicle-inertia-aggregation`; exact clean HEAD `ac4abb6ac86b41c8c869d8e60e5c79cbf85c8114`. Read-only status, branch, HEAD, log and remote inspections passed. Accepted J-011 `480f64840b92de87ee70a30a299fdb6ab169e0ef` remains an ancestor. Work branch was created directly from that NAT-025C HEAD: `development/nat-026-event-aware-step-boundaries`. No reset, rewrite, merge or remote operation.

The actual local production files below were inspected under `roketsim-core/src/main/java/org/roketsim/core/`, not inferred from remote revisions:

| Local source | Accepted API/ownership relevant to this implementation |
|---|---|
| `simulation/SimulationEngine3DOF.java` | Existing base-run and NAT-024 hardware-run entrypoints share one private lifecycle; no instance fields. |
| `simulation/SimulationRunConfiguration3DOF.java` | Seven-component record, explicit fixed step, limits, statistics and terminal policy; unchanged. |
| `numerics/ClassicalRK4Integrator3DOF.java` | `step(point, FixedStepConfig, derivative)`; four stages, full endpoint `t+h`, accepted state differs from k4 trial. Unchanged. |
| `numerics/FixedStepConfig.java` | Finite positive `stepSizeS`; unchanged. |
| `simulation/PhysicsEvaluator3DOF.java` | Existing four-/six-argument evaluations, accepted environment/motor/mass/drag/dynamics order; unchanged. |
| `simulation/PhysicsEvaluationContext3DOF.java` | Fourteen components including `motorInstallation`, `motorPropertyModelProfile`, `propulsionTimeline`; unchanged. |
| `simulation/PropulsionTimeline.java` | Sole finite `ignitionTimeS` authority; negative absolute ignition allowed; unchanged. |
| `propulsion/MotorDefinition.java` | Immutable `thrustCurve`, optional `massCurve` and `cgCurve`; no invented grids; unchanged. |
| `propulsion/MotorCurveStatistics.java` | `curveEndTimeS` is the source-end burnout authority, not the 5% effective end; unchanged. |
| `propulsion/MotorPropertyModelProfile.java` | Independent, explicit enum selections; unchanged. |
| `propulsion/MotorMassEvolutionModel.java` | `EXPLICIT_CURVE` versus `IMPULSE_PROPORTIONAL`; unchanged. |
| `propulsion/MotorCgEvolutionModel.java` | `EXPLICIT_CURVE` versus `FIXED_MIDPOINT`; unchanged. |
| `simulation/FlightEventDetector3DOF.java` | Burnout bracket `start < boundary <= end`; apogee/ground linear estimates; unchanged. |
| `simulation/FlightRecorder3DOF.java` | Accepted samples and ordered events retained by the existing lifecycle; unchanged. |
| `simulation/SimulationRunLimits3DOF.java` | `maximumSteps` bounds attempted integration steps, not nominal elapsed duration; unchanged. |

NAT-025 phase/clock implementation was also inspected: pre-ignition classification remains a standalone capability. NAT-026 does not connect it to flight mechanics or change negative motor-time rejection.

## Public additions and shared engine integration

Exactly two new production files in `org.roketsim.core.simulation`:

```java
public enum IntegrationStepAlignmentMode {
    LEGACY_FIXED_STEP,
    ALIGN_MOTOR_SOURCE_BOUNDARIES
}

public final class MotorTimeBoundarySchedule3DOF {
    public static MotorTimeBoundarySchedule3DOF fromContext(
        PhysicsEvaluationContext3DOF context, MotorCurveStatistics motorStatistics);
    public double effectiveStepSizeS(double currentTimeS, double nominalStepSizeS);
    public List<Double> boundaryTimesS();
}
```

The only existing production modification is `SimulationEngine3DOF.java`, adding:

```java
public SimulationExecution3DOF run(
    SimulationRunConfiguration3DOF configuration,
    IntegrationStepAlignmentMode alignmentMode);
```

The one-argument base run still calls the shared lifecycle with no schedule. Explicit `LEGACY_FIXED_STEP` delegates directly to it. Existing NAT-024 attached-hardware runs also retain their unscheduled path; no additional hardware-plus-alignment overload is introduced. Null mode is rejected with `IllegalArgumentException`; no implicit switch to alignment.

Aligned runs build one immutable, run-local schedule. The same single lifecycle selects a fresh `FixedStepConfig(effectiveStep)` before the unchanged RK4 call. Physics callbacks, four stage evaluations, accepted-endpoint evaluation, event detection, recording order, and interior-terminal candidate rejection/endpoint acceptance remain in their original common code. `stepsPerformed` still increments once per attempted RK4 step; limits are not enlarged. Additional accepted samples can consume the limit sooner in simulated time. The simulation starts at the original ignition timestamp, including valid negative absolute ignition.

## Source-boundary derivation and numerical policy

The schedule reads all accepted thrust source nodes and the supplied statistics' curve end. It includes mass nodes only for `EXPLICIT_CURVE` mass and CG nodes only for `EXPLICIT_CURVE` CG. An explicitly selected absent source curve is an error, never a fallback. Optional source grids may extend beyond the thrust end and remain scheduled. The schedule performs no thrust interpolation, integration, depletion or grain calculation.

Each motor-local source time maps to `ignitionTimeS + motorLocalSampleTimeS`; nonfinite sums fail. Absolute double values are sorted and exactly deduplicated, including coincident thrust/mass/CG/end nodes (signed zeros compare numerically equal). No tolerance is used. `boundaryTimesS()` returns an immutable list.

Binary search finds the first boundary strictly after the current time. If it is no later than `current + nominal`, the returned step is literally `boundary - current`; otherwise it is the nominal step. Current time and step must be finite, nominal must be positive, and proposed full endpoint must be finite and strictly advancing. A selected difference must be finite, positive and no greater than nominal. Its recomputed endpoint must equal the selected boundary exactly and strictly advance. Otherwise an explicit contextual `IllegalArgumentException` is raised. No epsilon, clipping, endpoint snapping, `nextUp`, retry or silently substituted step is used.

**Important representability limitation, deliberately tested:** with real F50 source nodes and Java 17, `current=.142`, `nominal=.01`, `boundary=.152`, the proposed `current+nominal` equals `.152`, but `boundary-current` is `.010000000000000009`, greater than nominal. The frozen literal-difference and `effective <= nominal` rules therefore cause an explicit error. Nominal hex is `0x1.47ae147ae147bp-7`; difference hex is `0x1.47ae147ae148p-7`. The implementation does not substitute nominal merely because its rounded endpoint reaches the boundary. Consequently not every positive nominal step/grid combination is supported in aligned mode; the usual legacy `.01`/`.005` trajectory gates remain legacy gates, not evidence of aligned-mode support for those grids. Any future exception to the literal rule requires owner review. Legacy behavior is unaffected.

## Focused V&V and development evidence

Test source: `roketsim-core/src/test/java/org/roketsim/core/MotorStepBoundariesTest.java` (13 JUnit methods; N26-T01–T17 grouped below).

| Contract tests | Executed evidence |
|---|---|
| T01 | Complete real F50 legacy execution equals explicit legacy execution exactly; exact enum values and null-mode rejection. |
| T02–T04 | Synthetic `.30 + .10` selects `.37-.30`; no interior boundary keeps nominal; starting exactly on `.37` advances normally. |
| T05 / T12 | Thrust/mass/CG duplicate knots and curve end deduplicate; independent `.5`, `.625`, `1`, `1.25` source nodes participate only under their selected explicit policies. Missing selected curve fails. |
| T06–T07 / T13 | Synthetic `.375/.75` and actual F50 all 32 thrust nodes reached; one burnout only; synthetic intermediate knot emits no event. F50 nominal `.1`, 40 steps produces 41 samples. |
| T08 | Negative ignition `-2`, nonnegative elapsed motor times, exact absolute burnout `-1.25`; same scheduler handles several synthetic grids. |
| T09–T10 | Nulls, NaN, both infinities, nonpositive step/end, boundary addition overflow, proposed endpoint overflow, no-progress at `1e20+1`, and the real F50 difference-exceeds-nominal case rejected. |
| T14 | Explicit limits of 8 and 40 attempted steps remain 8 and 40; sample counts 9 and 41. |
| T15 | Real synthetic low-thrust trajectory crosses ground before its first source knot. Legacy and aligned executions are equal; terminal ground is retained and the interior candidate is not recorded. Existing event/lifecycle suites also pass. |
| T16 | Repeated aligned executions equal; every accepted sample's physics equals fresh authoritative evaluation at its accepted time/state. Schedule list cannot be mutated. |
| T17 | Independent piecewise polynomial integral and nonlinear analytic reference below; no duplicated production flight pipeline used as oracle. |
| T18–T19 | Full Java suite and real unchanged J-011 Python–Java runner below. |

Before production additions, focused `test --tests org.roketsim.core.MotorStepBoundariesTest` failed compilation (exit 1, 15 missing-symbol/overload errors), as expected RED. First post-implementation run: 12 cases, 11 passed, 1 test-setup error (`VehicleTestData.replace` received a `double[]` for a record component declared as immutable `Vector`). That new fixture was corrected by constructing the existing `LaunchConditions3DOF`; no production formula or expected value changed. A real-F50 grid test brought the focused set to 13/13, exit 0. Final clean full suite also executes all 13 successfully.

`SingleStageVehicleConfigurationTest` has one necessary API-shape guard update: engine public-method count changes from 2 to exactly 3 and the precise new overload is checked; evaluator count stays 2. All original signatures, record shapes, numerical assertions and historical fixture values remain checked. No assertion/tolerance was relaxed to hide a physical discrepancy.

### Independently established numerical references

Test-only triangular source curve: nodes `(0,0),(.375,24),(.75,0)`; constant mass 2 and acceleration contribution -8. Analytic impulse 9 and first time moment `9*.375` give `x(1)=(9-9*.375)/2-4=-1.1875`. Unchanged RK4 with aligned nominal `.25`: **-1.1875**; unaligned `.25`: **-1.0833333333333335**. No production constant or new dynamics model is added by this analytic fixture.

Independent nonlinear test equation `y'=T(t)y`, `y(0)=1`, with triangular peak 2, gives `y(1)=exp(.75)=2.117000016612675`. Measured aligned absolute errors:

| Nominal step | Absolute error |
|---:|---:|
| .25 | 3.211836417329472e-4 |
| .125 | 2.814662414429847e-5 |
| .0625 | 1.493678741137927e-6 |
| .03125 | 8.162454712135059e-8 |

Each refinement improves by more than eightfold; finest error is within the established RK4 `np.assert_allclose` convention `rtol=1e-7`. Polynomial comparison uses that same relative convention. These are source-driven scheduler/RK4 mathematical checks, not experimental validation or a claim of aligned/legacy trajectory identity. No new global tolerance is introduced.

## Executed commands, build and full regression

Core working directory: `roketsim-native-workspace/roketsim-core`. Windows 11 amd64, Temurin Java/javac **17.0.20.1**, Gradle Wrapper **8.12.1**, Python reference **3.12.4**, NumPy **2.5.2**. Established isolated build, with all generated artifacts outside Git:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
```

**Exit 0; 556 tests, 0 failures/errors/skips, 36 suites: 13 new + 543 prior tests.** This includes all mass, inertia, NAT-023/024/025, propulsion, geometry, physics, dynamics, event, recorder, simulation, RK4 and J-011 trajectory tests, plus the remaining Java foundations. Clean compile, main JAR, sources JAR and Javadoc JAR succeed. Existing Javadoc output reaches 100 nonblocking warnings; this is not a complete warning inventory. Log: `%TEMP%/roketsim-nat026-regression.log`; XML: `%TEMP%/roketsim-nat026-build/test-results/test/`.

Packaged JAR `javap` confirms the exact new schedule signatures and all three engine entrypoints. `verifyProductionDependencies` passes; `jdeps --print-module-deps .../libs/roketsim-core-0.1.0.jar` returns only **java.base**, exit 0. No new runtime or build dependencies.

## Actual unchanged J-011 trajectory parity

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS / exit 0**, actual accepted Python engine versus actual Java legacy engine. Three runs / two distinct effective configurations:

| Run | Samples | Events | Numerical comparisons | Exact | Accepted nonexact |
|---|---:|---:|---:|---:|---:|
| primary | 1584 | 3 | 147521 | 147511 | 10 |
| repeat | 1584 | 3 | 147521 | 147511 | 10 |
| fine | 3167 | 3 | 294740 | 294714 | 26 |

Distinct configurations: **442261 numerical comparisons, 442225 exact, 36 accepted nonexact** (including repeat: 589782 / 589736 / 46). Maximum absolute difference `8.881784197001252e-16`; maximum relative `4.3829501452845633e-16`, matching the accepted preceding NAT-025C evidence. Upstream drag uses existing `rtol=3e-15`; the affected force/acceleration fields use existing `atol=2e-15`. No trajectory time/state/event tolerance was introduced. Primary termination `15.832349589466348`, fine `15.832554278426914`; all reporting events and sample structures pass the historical strict checker.

Raw Python JSONL, Java TSV, stderr, anchors and `comparison-summary.json` are retained in the above external artifacts directory; log `%TEMP%/roketsim-nat026-trajectory.log`. Parity harnesses/oracles, historical anchors and tolerances are unchanged. Other Python regression/parity packages and negative-integrity tools were not rerun; this record makes no claim that they were.

## Scope integrity, limitations and commit

Final scope: two new production files, one modified engine, one new focused test, one API-shape test update and this verification document. RK4, motor definitions/evaluators/statistics, all mass/CG/inertia authorities, physics evaluator, event detector, recorder, accepted record schemas, Python source/tests, ADRs and historical verification records are unchanged. One simulation lifecycle remains; no stage loop, physical equation or event logic was copied.

Alignment is opt-in scheduling, not adaptive error control or event-aware reintegration. Intermediate knots are not physical events; source discontinuities requiring one-sided stages remain future work. Aligned runs can have different states, event estimates and elapsed duration at a given step limit. There is no universal bitwise-equivalence or universal step/grid compatibility claim. The explicit floating-point limitation above is part of the implemented rejection behavior, not silently repaired.

MOTOR-DATA-2026-A remains separate source-audit evidence: TFL1256WS/TFM1850W/TFN3300R-P impulses `3650.044730405055`, `7649.329000`, `14325.010771` N*s are not catalog additions. N3300's truncated cached spreadsheet impulse is not a boundary authority. Geometry/datum differences (~12.7 mm), alternate mass observations/L1256 3 g discrepancy, motor classification, ejection metadata, provider aliases, missing regression/inertia and certification gaps remain future catalog/import concerns. No ENG import, source substitution or resolution is attempted here.

Local acceptance commit message: `feat(numerics): add NAT-026 motor source-boundary alignment`. The commit adding this document is the final identity, discoverable with `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-026-event-aware-step-boundaries.md`; the exact SHA and post-commit clean-tree check are reported separately to avoid a self-referential amend. Stage only the six scoped files; do not push, merge or change main/historical branches. NAT-027 and MOTOR-IMPORT are not started.
