# NAT-026.1 — IEEE-754 endpoint coincidence

Date: 2026-11-01. Scope: the owner-approved exact endpoint-coincidence correction only.

## Accepted starting state

Repository `E-Tuu/rocketsim-native`, clean branch `development/motor-001-teknofest-eng-import`, exact accepted MOTOR-001 HEAD **9dffbae6fa09bf805a7f5f23b79b010139159ace**. Status, branch, HEAD and log were inspected before editing; `git merge-base --is-ancestor 03450b2775700fcf726ea144ccd97bfc746e3afa HEAD` exited 0. The new local branch `development/nat-026-1-endpoint-coincidence-fix` was created directly from MOTOR-001, not from the older NAT-026 commit. No pre-existing changes, branch replacement or history rewrite.

## Reproduced failure and IEEE-754 explanation

The actual accepted planner was inspected. After finding the earliest boundary strictly after current time and no later than the proposed endpoint, it always calculated `boundary-current` and rejected a difference greater than nominal. This incorrectly treated the subtraction as the inverse of a rounded addition.

Actual Java 17 reference calculation:

```text
currentTimeS       = 0.142
nominalStepSizeS   = 0.01
boundary          = 0.152
boundary-current  = 0.010000000000000009 > nominal
current+nominal   = 0.152 == boundary
nominal hex       = 0x1.47ae147ae147bp-7
difference hex    = 0x1.47ae147ae148p-7
```

The represented gap and the nominal double differ, but rounded `current+nominal` already equals the accepted boundary. Recomputing that gap is unnecessary. The existing RK4 computes its full endpoint using the same `time+h` operation; it was inspected and not changed.

Before production editing, the corrected reference test was executed against the old implementation. **1 test / 1 failure / exit 1**, with actual error:

```text
java.lang.IllegalArgumentException: effective_step_size_s cannot safely represent
boundary_time_s=0.152 from current_time_s=0.142 within nominal_step_size_s=0.01
```

RED log: `%TEMP%/roketsim-nat0261-red.log`. RED XML was copied outside the clean-build directory to `%TEMP%/roketsim-nat0261-red.xml` before correction.

## Exact correction and files

Only production change, in `roketsim-core/src/main/java/org/roketsim/core/simulation/MotorTimeBoundarySchedule3DOF.java`, after existing input/proposed-endpoint validation and boundary search:

```java
double boundary=boundaryTimesS.get(low);
if(boundary==proposed) return nominalStepSizeS;
// Existing strictly-interior subtraction and safety checks follow unchanged.
```

Its method documentation/comment now describes endpoint retention. No API, source schedule, timestamp, arithmetic in RK4 or physical authority changes. There is no epsilon, approximate comparison, clipping, snapping or new production rounding helper.

| Classification | Result after correction |
|---|---|
| Next boundary equals validated `current+nominal` | Return original nominal double, without subtraction. Reference fixture returns **0.01**, full endpoint **0.152**. |
| Next boundary is strictly interior | Existing `boundary-current`, finite/positive/within-nominal checks and exact endpoint guard remain. |
| Boundary beyond endpoint, or none remains | Existing nominal return remains. |
| Boundary already reached | Existing strict-after search skips it; no zero step. |
| Nonfinite arithmetic or no progress | Existing rejection remains, before coincidence classification. |

Exactly three scoped files:

1. `roketsim-core/src/main/java/org/roketsim/core/simulation/MotorTimeBoundarySchedule3DOF.java`.
2. `roketsim-core/src/test/java/org/roketsim/core/MotorStepBoundariesTest.java`.
3. This additional verification record, `docs/verification/nat-026-1-endpoint-coincidence-fix.md`.

The focused NAT-026 test formerly requiring rejection of the exact reference fixture now requires nominal acceptance, as explicitly authorized by NAT-026.1. Its input values are unchanged. Five additional focused methods were added. No historical physical fixtures, numerical anchors or tolerances were changed. Historical NAT-026 and MOTOR-001 records remain unchanged and truthful about their then-current behavior.

## Focused coverage and numerical limitations

All **18 MotorStepBoundariesTest cases pass**, covering the original 13 cases with the one approved expectation correction, plus five new methods:

- Reference `.142/.01/.152`: `boundary-current > nominal`, yet returned step is exactly nominal and resulting endpoint exactly boundary.
- Coincident endpoint `.25+h=.375` for `h=nextDown(.125)`, `.125`, `nextUp(.125)`: preserve each original nominal double. Adjacent-value helpers are test inputs only, not a production rounding policy.
- Strict interior `.375` with current `0`, `.25`, `nextDown(.375)` and nominal `.5`: effective step positive, no greater than nominal, recomputed endpoint exactly boundary. Ordinary `.30/.10/.37` case also remains covered.
- No boundary in interval, already-reached boundary and duplicate explicit thrust/mass/CG knots: no zero or duplicate step.
- Existing NaN, both infinities, invalid step, boundary/proposed overflow and genuine no-progress (`1e20+1==1e20`) rejections remain.
- Actual F50 aligned `.01` run completes **220 attempted steps / 221 accepted samples**, reaches all 32 source nodes including `.152`, advances strictly, reports burnout once and repeats deterministically. The `.142` integration point is retained; it is not claimed to be a source knot. Previous `.1` F50 and synthetic aligned tests still pass.
- Existing complete legacy-versus-explicit-legacy equality, terminal-ground handling, negative ignition, source-policy grids and analytical/convergence tests remain intact.

**Unchanged strictly-interior representability limitation:** standalone schedule with ignition `.37` (first boundary `.37`), current `-1`, nominal `2` has proposed endpoint `1`. Its boundary is strictly interior, but `boundary-current=1.37` and `current+1.37=0.3700000000000001`, beyond `.37`. The existing endpoint guard still rejects explicitly; a regression asserts that rejection. This is a standalone arithmetic probe, not pre-ignition flight mechanics. Handling it by adjusting/subdividing a step or changing endpoint acceptance would require a broader separately approved numerical policy. No such correction was attempted. No universal support for all floating-point intervals is claimed.

## Full clean build and regression

Actual environment: Windows 11 amd64; Temurin Java/javac **17.0.20.1**, runtime **17.0.20.1+1**; Gradle Wrapper **8.12.1**. Commands from `roketsim-native-workspace/roketsim-core`:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
```

**Exit 0; 580 tests / 0 failures / 0 errors / 0 skips, 37 suites.** Breakdown: 18 focused boundary cases, 19 unchanged MOTOR-001 cases and 543 other historical Java cases. Relative to MOTOR-001's 575 cases, five cases are added and one existing boundary expectation is intentionally corrected. Source-import checksums, source-model/readiness tests and all other historical NAT regressions pass without modification.

Focused GREEN command used the same isolated settings with `test --tests org.roketsim.core.MotorStepBoundariesTest`; exit 0. The RED command selected only `org.roketsim.core.MotorStepBoundariesTest.coincidentNominalEndpointKeepsNominalEvenWhenDifferenceRoundsUp`; exit 1 before production correction.

Java 17 clean compilation and main/sources/Javadoc JAR build passed. Javadoc output reaches the existing 100 nonblocking-warning display limit; no complete warning inventory claimed. Production dependency check passes; packaged JAR `jdeps --print-module-deps` returns **java.base only**, exit 0. No new runtime dependency.

Build log: `%TEMP%/roketsim-nat0261-regression.log`; focused log `%TEMP%/roketsim-nat0261-focused.log`; XML `%TEMP%/roketsim-nat0261-build/test-results/test/`. All build outputs/caches remain outside Git using the established isolated procedure.

## Actual J-011 trajectory parity

Unmodified real Python–Java runner executed:

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS / exit 0**, actual Python **3.12.4 / NumPy 2.5.2**. Three runs / two distinct configurations:

| Run | Samples | Events | Numeric comparisons | Exact | Accepted nonexact |
|---|---:|---:|---:|---:|---:|
| primary | 1584 | 3 | 147521 | 147511 | 10 |
| repeat | 1584 | 3 | 147521 | 147511 | 10 |
| fine | 3167 | 3 | 294740 | 294714 | 26 |

Distinct totals: **442261 numeric / 442225 exact / 36 accepted nonexact**. Including repeat: 589782 / 589736 / 46. Maximum absolute difference **8.881784197001252e-16**, relative **4.3829501452845633e-16**, unchanged from accepted MOTOR-001 evidence. Existing quantity-specific tolerances/strict output protocol and all historical trajectory fixtures remain unchanged. This is legacy trajectory preservation, not a claim that aligned and legacy grids produce identical trajectories.

Raw Python/Java traces, error streams, anchors and summary: `%TEMP%/roketsim-nat0261-trajectory`; log `%TEMP%/roketsim-nat0261-trajectory.log`. No other Python regression/parity/integrity suite is claimed rerun.

## Final integrity and local commit

No modifications to RK4, FixedStepConfig, PhysicsEvaluator, SimulationEngine, motor definitions, mass/CG/inertia authorities, event detection, MOTOR-001 schema/import/resources/tests, accepted ADRs, historical verification records or Python production/tests. No public API, constant, numerical tolerance, source knot, physical model or execution mode is added. Only the endpoint-coincidence decision changes. No additional implementation blocker was observed; the unchanged interior-cancellation limitation is explicit above.

Local commit message: `fix(numerics): handle coincident NAT-026 motor boundaries`. The commit adding this document is the final correction identity; retrieve it with `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-026-1-endpoint-coincidence-fix.md`. Exact SHA and post-commit clean-tree status are reported separately, avoiding a self-referential amend. Only these three files are staged. No push, merge, reset or history rewrite; main, accepted MOTOR-001/NAT-026 branches and historical commits remain intact. NAT-027 is not started.
