# NAT-034 — Constant Recovery Drag Evaluator

Date: 2026-11-03. Scoped Java-only physics implementation under the owner-approved FROZEN v1.0 contract. Implementation PASS is not independent reviewer/project-owner acceptance or physical parachute certification.

## Starting state, ownership and exact scope

Verified clean local branch `development/nat-031-033-1-projection-consistency`, exact HEAD `06ee4d8b39da056c0e22c59b35ff00ce57809f85`. Its parent guide implementation `40f9e842e7d511b77888b91bed3d1fe13ab612d1` is an ancestor (exit 0). The absent dedicated branch was created directly from HEAD: `development/nat-034-constant-recovery-drag`. No old local work was discarded or replaced with remote content.

Inspected actual local `AerodynamicEvaluationError`, immutable `Vector`, `Vectors.magnitude`, module exports and the accepted NAT-031–033 verification/correction record. Existing error constructor `(String errorCode,String fieldName,Object value)` can represent this task without alteration. Aerodynamics is already exported; no module/build/API-schema change is necessary.

Exactly one new production file: `roketsim-core/src/main/java/org/roketsim/core/aerodynamics/RecoveryDragEvaluator3DOF.java`. Additional authorized files: `roketsim-core/src/test/java/org/roketsim/core/RecoveryDragTest.java`, `roketsim-core/verification/RecoveryDragJarConsumer.java`, and this verification record. Paths are relative to `roketsim-native-workspace/`.

```java
public final class RecoveryDragEvaluator3DOF {
    public Vector evaluate(double airDensityKgM3,
        Vector relativeVelocityWorldMS, double effectiveDragAreaM2);
}
```

The evaluator is stateless and has no fields/cache/history. It returns an independent immutable existing Core Vector; no result/configuration/provenance framework or new vector type is introduced. Density and relative velocity are supplied upstream results, not recomputed atmosphere/wind/relative-flow physics. Mass, gravity, motor models and launch direction are not inputs.

## Mathematical and input/output contract

One fully deployed parachute with constant explicitly supplied effective drag area:

```text
F_recovery = -0.5 * rho * CdA * |v_relative| * v_relative
```

Inputs are density kg/m³, effective area m², relative velocity m/s in WORLD ENU (+X East,+Y North,+Z Up). Output is WORLD force N opposing relative velocity. No reference-area conversion or inferred separate Cd is performed.

Validation order is density finite/nonnegative; velocity non-null/exactly three finite components; CdA finite/strictly positive; then derived arithmetic. All required inputs are validated before either zero shortcut. Exact density zero or three exact zero velocity components return a positive-zero vector, including otherwise valid large finite scalars/components that would overflow unnecessary arithmetic. Negative zero density is permitted by nonnegative semantics; zero CdA is never an activation flag.

Velocity is copied/revalidated through existing `Vector.of(...,3,...)`. Public immutable Vector construction already rejects nonfinite components, so such raw caller data fail before the typed method is entered; tests explicitly exercise that existing upstream boundary without reflection, fabricated mutable vectors or an unauthorized Object overload.

For remaining inputs the operation order is exactly:

```text
speed = Vectors.magnitude(relative)
scale = ((-0.5 * airDensityKgM3) * effectiveDragAreaM2) * speed
force[i] = scale * relative[i]
```

The existing norm rejects nonfinite speed before returning; NAT-034 translates that local failure into its required aerodynamic error while retaining the original cause/input vector. Scale and each force component are explicitly checked for finiteness before returning. No clipping, regrouping, normalization, compensated/stable replacement norm or epsilon is introduced.

## Error metadata and numerical limits

All NAT-034 evaluation failures use the existing `AerodynamicEvaluationError` (an IllegalArgumentException). No existing error class or upstream production behavior changes.

| Condition | errorCode | fieldName | value |
|---|---|---|---|
| Negative/nonfinite density | INVALID_RECOVERY_DENSITY | air_density_kg_m3 | Supplied double |
| Null/wrong-size velocity at typed evaluator boundary | INVALID_RECOVERY_RELATIVE_FLOW | relative_velocity_world_m_s | Supplied Vector or null; original validation cause retained |
| Nonpositive/nonfinite CdA | INVALID_RECOVERY_DRAG_AREA | effective_drag_area_m2 | Supplied double |
| Existing norm rejects derived nonfinite speed | NONFINITE_RECOVERY_FORCE | relative_velocity_world_m_s | Offending copied Vector, original norm exception retained; no fictitious returned speed value |
| Nonfinite scale | NONFINITE_RECOVERY_FORCE | recovery_force_scale | Actual derived double |
| Nonfinite force component | NONFINITE_RECOVERY_FORCE | recovery_force_world_N[i] | Actual derived component |

The Vector/norm/double foundations have finite-range and underflow limitations. A finite large vector can overflow the sum-of-squares norm even when its mathematical norm would be representable with a different algorithm; it is rejected under the frozen policy. Very small nonzero components can square to zero. Multiplication can underflow to signed zero. No universal nonzero-force or stable-norm guarantee is claimed. Intermediate nonfinite scale is rejected even if later regrouping could produce a finite mathematical expression. These are explicit numerical behavior, not physical velocity/density thresholds.

## Physical applicability and provenance boundary

The evaluator assumes a fully deployed parachute under a limited steady quadratic-drag model. The caller must retain CdA source, reference-area convention, measured/estimated/assumed basis, parachute-only versus assembly-effective scope and known applicability limits. A supplied finite CdA is not certified by this function. Unknown applicability is not universal validity; no Mach/Reynolds range, nominal Cd, inflation behavior or measured provenance is invented.

There is no activation/deployment logic, opening shock, reefing, tether dynamics, variable CdA, multiple systems, moment/attitude/6DOF or ground interaction. No body-plus-parachute composition. Attached recovery hardware mass remains governed by existing NAT-023/024; this force calculation changes no mass.

NAT-035/036 integration/deployment/phase/termination responsibilities require separate approved contracts. Their exact future APIs or physics are not defined here. No force is connected to PhysicsEvaluator or SimulationEngine, and no fake Cd0/BasicDragResult is produced. Existing C08 ground/root, C12 validity/warning design, C15 streamer/source questions are not closed.

## Independent focused V&V

All analytical fixtures below are synthetic verification inputs, not production presets or parachute measurements. Expected values are independently specified, not derived by calling the evaluator as its own oracle. No numerical tolerance is added: integer/binary-exact fixtures use exact existing Vector component equality; explicit zero-output tests compare arrays. Vector equality treats signed zeros as numerically equal, consistent with the existing Core contract.

| Case | Independent expectation / assertion |
|---|---|
| rho=2,CdA=3,v=(3,4,0) | Norm 5, force (-45,-60,0) N exactly |
| Reversed velocity | (45,60,0) N |
| Doubled velocity | (-180,-240,0) N, quadratic scaling |
| Doubled density or CdA | (-90,-120,0) N |
| v=(0,0,-4) | (0,0,48) N |
| v=(-2,3,-6) | Norm 7, force (42,-63,126) N |
| Suitable signed/oblique finite fixtures | F dot v < 0 |
| Exact zero flow | Exact zero even with valid maximum finite density/CdA |
| Exact zero density | Exact zero after all validation, including large finite velocity that otherwise overflows norm |
| Invalid density/area | Negative/domain boundaries, NaN and both infinities; exact exception class/code/field/value |
| Invalid velocity | Null, 0/2/4 components at evaluator; NaN and both infinities in each raw component rejected by existing Vector constructor |
| Multiple invalid inputs | Density before flow before CdA; zero shortcuts and norm arithmetic cannot bypass validation |
| Derived overflow | Norm, scale and force-component overflow separately rejected with metadata |
| v=(2^-200,0,0),rho=2,CdA=1 | Force (-2^-400,0,0) exactly, nonzero |
| v=(MIN_VALUE,0,0),rho=2,CdA=1 | Norm underflow gives zero force under existing arithmetic |
| rho=MIN_VALUE,CdA=MAX_VALUE,v=(1,0,0) | First multiplication -.5*MIN_VALUE rounds to -0; no regrouping rescues it |
| Immutability/repeatability | Caller/exported arrays cannot mutate input/results; repeated/fresh evaluators agree; no evaluator fields |

Test-first RED: new focused tests before production type existed produced **5 missing-symbol compile errors, exit 1, Gradle 14 s**. No tests executed; this is expected development evidence, not a successful comparison. Log `<local-evidence>`.

First actual focused run after implementation: **17 tests, 0 failure/error/skip, exit 0**, Gradle **16 s**; inspected XML suite time .138 s. Log `<local-evidence>`. No unexpected focused failure, source/fixture adjustment or tolerance relaxation occurred.

## Regression, packaging, parity and final scope

Environment: Windows 11 amd64, Temurin Java/javac **17.0.20.1**, Gradle Wrapper **8.12.1**; actual Python trajectory reference **3.12.4 / NumPy 2.5.2**. Existing isolated build/cache reused; no unnecessary clean, dependency update or repeated build restart. Core working directory `roketsim-native-workspace/roketsim-core`:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
# RED before production type existed; then the first GREEN, with distinct retained logs:
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --tests org.roketsim.core.RecoveryDragTest --offline --no-daemon
# Full regression and assembly after inspecting the focused result:
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
& '<JAVA_HOME>/bin/java.exe' --module-path '<local-evidence>' --add-modules org.roketsim.core verification/RecoveryDragJarConsumer.java
& '<JAVA_HOME>/bin/jdeps.exe' --print-module-deps '<local-evidence>'
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

| Actual executed gate | Result / exit code |
|---|---|
| Focused NAT-034 | 17 passed, zero failure/error/skip; exit 0, Gradle 16 s |
| Complete Java suite | **621 tests / 39 suites**, zero failure/error/skip; **604 prior + 17 new**, exit 0 |
| Java 17 assembly and production dependencies | Main/sources/Javadoc JARs, verifyProductionDependencies PASS; full command **27 s**, exit 0 |
| Existing MOTOR-001 suite | MotorEngImportTest 19 passed, unchanged |
| Existing NAT-026/026.1 suite | MotorStepBoundariesTest 18 passed, unchanged |
| Existing NAT-031–033.1 suite | LaunchGuideTest 24 passed, unchanged |
| New public API from packaged main JAR | **RECOVERY_DRAG_JAR_CONSUMER: PASS**, forceWorldN `[-45.0,-60.0,-0.0]`, correct zero-density and invalid-CdA metadata; exit 0 |
| Production runtime jdeps | **java.base only**, exit 0 |
| Actual historical J-011 trajectory parity | **NATIVE_TRAJECTORY_PARITY: PASS**, exit 0 |

Javadoc reaches the existing 100 nonblocking-warning display limit; this is not a full warning inventory or a new failure policy. No gate was suppressed. There was no unexpected implementation/regression/parity discrepancy requiring a corrective physics change.

Actual J-011 comparison, unmodified real Python and Java legacy execution:

| Run | Samples | Events | Numerical | Exact | Accepted nonexact |
|---|---:|---:|---:|---:|---:|
| primary | 1584 | 3 | 147521 | 147511 | 10 |
| repeat | 1584 | 3 | 147521 | 147511 | 10 |
| fine | 3167 | 3 | 294740 | 294714 | 26 |

Three runs / two distinct effective configurations. Distinct totals **442261 numerical / 442225 exact / 36 accepted nonexact**; repeat-inclusive 589782 / 589736 / 46. Maximum absolute **8.881784197001252e-16**, relative **4.3829501452845633e-16**, identical to prior accepted upstream last-bit evidence under unchanged quantity-specific tolerances. No trajectory state/time/event or source tolerance/fixture change. This gate preserves legacy behavior; new recovery physics is independently checked analytically, not claimed as a Python recovery migration or parachute flight validation.

Fresh logs outside Git: `<local-evidence>`. Full XML is in `<local-evidence>`; fresh raw trajectory traces/anchors/summary/stderr in `<local-evidence>`. Exit codes were observed from completed processes, not inferred from log text alone. No full Python or other historical subsystem parity/integrity suite rerun is claimed.

## Final integrity and local milestone commit

Only four new files are included: the independent production evaluator, focused test, packaged-JAR consumer and this record. Before staging, tracked diff and staged index were empty: all accepted existing Java/Python sources/tests, ADRs, model data/profiles, schemas, previous verification records, engine/RK4/event/guide/ground/BasicDrag behavior remained byte-for-byte unchanged in Git. New files were inspected for scope, whitespace, generated artifacts and sensitive content. Existing aerodynamics export suffices, so no module/runtime dependency change is present.

All scoped implementation gates passed. Physical applicability/data certification remains caller-owned and limited as stated; active recovery force selection/deployment/flight integration and NAT-035/036 remain deferred. The local commit message is `feat(aerodynamics): add NAT-034 constant recovery drag evaluator`. The commit introducing this record is its final identity, retrievable with `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-034-constant-recovery-drag.md`; exact SHA and post-commit worktree status are reported separately, without a self-referential amend. No push, merge, PR, history rewrite or next milestone is performed. Codex implementation PASS does not assert independent owner acceptance.
