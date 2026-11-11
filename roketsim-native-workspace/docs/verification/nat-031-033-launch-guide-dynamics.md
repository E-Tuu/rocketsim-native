# NAT-031–033 — Opt-in launch guide dynamics and comparison

Verification date: 2026-11-01. One combined implementation and acceptance record; all reported results below were actually executed.

## Baseline and source inventory

Clean exact accepted HEAD `d222fee4c13c9c555adc0d9c160687c5b7bcff5e`, starting branch `development/nat-026-1-endpoint-coincidence-fix`. Git root is `E-Tuu/rocketsim-native`; NAT-025C `ac4abb6ac86b41c8c869d8e60e5c79cbf85c8114`, NAT-026 `03450b2775700fcf726ea144ccd97bfc746e3afa`, MOTOR-001 `9dffbae6fa09bf805a7f5f23b79b010139159ace` and NAT-026.1 were checked with `merge-base --is-ancestor` (each exit 0). The absent new branch was created directly from accepted HEAD: `development/nat-031-033-launch-guide-dynamics`. No reset/rebase/cherry-pick/merge/push.

Actual local authorities inspected under `roketsim-core/src/main/java/org/roketsim/core`: `dynamics/{LaunchConditions3DOF,InitialStateBuilder,TranslationalState3DOF,TranslationalDynamicsEvaluator,TranslationalDynamicsModelProfile}`, `simulation/{PhysicsEvaluator3DOF,PhysicsEvaluationContext3DOF,PhysicsEvaluationResult3DOF,SimulationEngine3DOF,SimulationRunConfiguration3DOF,SingleStageVehicleRunConfiguration3DOF,SimulationRunLimits3DOF,FlightEventDetector3DOF,FlightRecorder3DOF,RecordedFlightSample3DOF,RecordedFlightData3DOF,SimulationExecution3DOF,SimulationResult3DOF,SimulationTerminationReason,FlightPhase3DOF,FlightPhaseClockEvaluator3DOF,MotorTimeBoundarySchedule3DOF,IntegrationStepAlignmentMode}`, `numerics/{ClassicalRK4Integrator3DOF,FixedStepConfig}`, `math/Vectors`, geometry/mass/CG/inertia result/request APIs and the MOTOR-001 catalog/import declarations and compatibility blockers. Relevant existing physics, event, simulation, motor-property, boundary and native-trajectory tests and J-010/J-011/NAT-026.1 verification records were inspected. No names/signatures were inferred from a remote revision.

| Required behavior | Existing owner | Decision |
|---|---|---|
| Direction, initial WORLD position/velocity | NAT-013 LaunchConditions/InitialStateBuilder | REUSE; original vector retained, no normalization |
| Free instantaneous force, mass/CG and environment | NAT-014/015 and NAT-023/024 | REUSE PhysicsEvaluator and explicit hardware arrays, no copied equations |
| Classical RK4 | NAT-017 | REUSE unchanged stepper and accepted endpoint, never k4 trial state |
| Reporting burnout/apogee/ground events | NAT-018 | REUSE unchanged detector; constrained pad recontact is a distinct new outcome, not ordinary GROUND |
| Free-flight terminal behavior/recorder | NAT-020 and NAT-019 | EXTEND engine with package-private accepted-state continuation; one common free-flight loop |
| Phase and motor clock | NAT-025 | REUSE explicit phase input and existing clock; no phase inference from motor status |
| Source step boundaries | NAT-026/026.1 | REUSE unchanged planner; new guide mode explicitly requires selected ALIGN mode |
| Pad support | This task | NEW ideal unilateral holding/release, no impact response |
| Guided translation/reaction | This task | NEW projection of accepted free acceleration, net reaction only |
| Liftoff/guide exit/recontact brackets | This task | NEW guide-local reintegration/bisection only; no NAT-027–030 framework |
| Competition diagnostics | This task | NEW separate named-metric/evidence checks, not a dynamics authority |

## Source evidence

Direct local PDF: `<local-reference-document>`, 66 pages, SHA-256 `c07b4ea7908f4d80a2af7977cbf936f76476b471428c4954a7c3194edbae4c5c`. Relevant text extracted and pages 21, 24 and 25 rendered with Poppler and visually inspected. Page 21 Figure 4.3 shows **600 cm nominal rod input**, **5 degrees from vertical**; §4.1.35 states **85 degrees above horizontal**. Page 24 §4.4.8/Table 4.3 confirms A1 15, A2 25, A3 30, A4 25, A5/A6/A7 20 m/s. Page 25 §4.5.15–19 requires two buttons, CG between them, committee-supplied buttons and a motor-region button between motor CG and body end. Structural reinforcement evidence is outside the numerical placement checks.

EK-3 and the four reviewed OpenRocket Java files were not found in the searched workspace/Downloads/Desktop locations. EK-3 M4/countersunk and launcher-profile statements and OpenRocket implementation descriptions are **owner-supplied reviewed evidence**, not claimed independently opened here. No unrelated/current remote OpenRocket version is substituted. Physical usable competition guide length and full profile dimensions remain unverified; the 6 m nominal figure is not usable-length certification.

## Owner clarification and deferred work

The owner explicitly approved stopping on return to the lower support with negative incoming axial velocity: localize the pre-impact bracket, retain time/position/velocity, report `IMPACT_DYNAMICS_UNSUPPORTED`, and do not model restitution, rebound, impulses, energy losses or post-impact motion. Initial stationary pad holding is distinct from recontact after liftoff. No state may be accepted below the support; this is not an ordinary landing or successful launch.

**GUIDE-6DOF-01:** Defer the forward-button-exit to final-contact-loss regime, rotation, individual reactions/moments, recollision and contact fidelity to 6DOF. The primary aft-contact threshold is geometric only.

**NUM-TF-01:** At real TEKNOFEST scenario validation (planned NAT-037/039), stress-test L1256/M1850/N3300 source-time grids over ignition times/steps. NAT-026.1's interior representability limitation is retained, not repaired here. Imported TEKNOFEST records remain catalog/curve-ready only; no runtime motor/installation/inertia evidence is invented.

## Public implementation and exact file inventory

New production files in `roketsim-core/src/main/java/org/roketsim/core/simulation/`:

| File/type | Responsibility |
|---|---|
| `LaunchGuideGeometry.java` | Immutable `(datumId, kind, topCoordinateM, sourceReference)`; declared usable two-button geometry versus a separate nominal rod input. Rod/tower kinds are reserved and rejected at execution. Explicit degree-to-ENU-vector adapter only. |
| `GuideAttachmentGeometry.java` | Immutable `(datumId, forwardContact0M, aftContact0M, sourceReference)`; no placement inference. |
| `LaunchGuideConstraintPolicy.java` | Exactly `GEOMETRIC_TWO_POINT_GUIDANCE` and `OPENROCKET_NOMINAL_ROD_COMPAT`. |
| `LaunchGuideRunConfiguration3DOF.java` | Opt-in composition `(vehicle, guide, attachments, policy, alignmentMode)`; vehicle is existing NAT-024 `SingleStageVehicleRunConfiguration3DOF`. |
| `LaunchGuideEvaluation.java` | Accepted point, unchanged unconstrained-physics reference, existing clock, guide coordinate/velocity, net axial applied force, actual constrained derivative and net ideal reaction. |
| `LaunchGuideTransitionResult.java` | Accepted RK4 point, lower/upper event times, threshold and accepted side. Kinds: liftoff, two-point end, nominal rod exit, pad recontact. No last-contact mechanical event. |
| `LaunchGuideBoundaryLocalizer.java` | Guide-only duration bisection/reintegration, not a scheduler/event framework. Public `guidanceEnd(start, maxStepSizeS, launch, targetTravelM, derivative, kind)`. |
| `LaunchGuideRunResult3DOF.java` | Immutable new wrapper over existing recorded data, applied-derivative samples, guide transitions, lifecycle outcome and optional free-flight termination reason. Separately named optional speed metrics. |
| `LaunchGuideSimulator3DOF.java` | Stateless public `run(LaunchGuideRunConfiguration3DOF)`; a fresh private execution state owns each run. Pad/guide prefix only, then existing engine continuation. |
| `LaunchGuideError.java` | Local `IllegalArgumentException` with `errorCode/fieldName/value`; no global exception redesign. |
| `Teknofest2026LaunchDiagnostics.java` | Separate category/metric/status checks, explicit placement/orientation evidence and source thresholds; never called to select a force law. |

One existing production modification: `SimulationEngine3DOF.java`. Its original free-flight loop is shared through package-private `continueFrom(...)`; the unchanged physics dispatch is package-visible to the guide executor. The three existing public `run` signatures and all accepted record schemas remain unchanged. Legacy runs pass a null first remainder and no new guide policy; their original candidate/event/endpoint/terminal arithmetic remains in the same single loop. New guide continuation transfers its accepted point, recorder, total advance count, hardware arrays, existing source schedule and optional original-step remainder. There is no copied free-flight lifecycle.

Additional files: `roketsim-core/src/test/java/org/roketsim/core/LaunchGuideTest.java` (20 new JUnit methods), `roketsim-core/verification/LaunchGuideJarConsumer.java` (standalone packaged-module consumer), and this record. **15 changed/added files total: 12 production, 2 verification/test, 1 document.** No existing test file is changed.

## Coordinate, physics and result contracts

WORLD remains ENU; the existing launch vector `u` is the sole directional authority and is never normalized/repaired. The adapter implements `[cos(e)sin(psi), cos(e)cos(psi), sin(e)]`, elevation above horizontal, azimuth clockwise from North. It returns an input vector, not an attitude state. The existing launch-direction `1e-8` acceptance contract is untouched, including preservation of accepted near-unit components.

Guide datum zero is its explicitly declared lower usable end. Initial contacts share that datum and satisfy `0 <= bAft0 < bForward0 < L`. Rocket travel `s` is translation from the accepted initial WORLD position, not absolute altitude or nose-tip x_geo. For diagnostic extraction, `s=dot(r-r0,u)/dot(u,u)` and axial velocity likewise divide by `dot(u,u)`; this extracts the coordinate coefficient without changing `u`. The continuous ideal motion is `r=r0+s*u`, `v=sDot*u`; component arithmetic retains ordinary RK4/vector roundoff, checked by the existing local algebra comparison convention. No accepted point is snapped/reprojected onto a manufactured timestamp or coordinate.

Primary thresholds are `L-bForward0` and the **geometric-only** `L-bAft0`. The primary event is `TWO_POINT_GUIDANCE_END`; its speed is velocity magnitude at the accepted boundary. The continuation classification is exactly `APPROXIMATE_AFTER_TWO_POINT_EXIT`. Remaining aft-button contact is ignored after this boundary; its diagnostic threshold is never emitted as an actual final-contact event.

The independent `OPENROCKET_NOMINAL_ROD_COMPAT` policy requires a `NOMINAL_ROD_INPUT` and no physical attachment geometry. Its event criterion is the **magnitude of displacement from the initial position**, including for accepted near-unit vectors. It does not substitute the primary button threshold. The fixture supplies 6.00 m explicitly; no production usable length defaults to 6 m. Its distinct event/metric is `NOMINAL_ROD_EXIT` / `openRocketNominalRodExitSpeedMS`. The other policy's metric stays empty in each result.

All instantaneous environment, wind, relative flow, thrust, motor time/mass/CG, auxiliary-inclusive rocket mass/CG and free dynamics come from the existing `PhysicsEvaluator3DOF`. Guided acceleration is literally `dot(aFree,u)*u`. Net constraint reaction is `mass*aApplied-Fnet`, not individual button loads. While stationary at the lower support and driven into it, the held derivative is exactly zero and the combined ideal support/guide reaction balances the applied force. Release requires actual positive net axial force and a positive projected free acceleration (equivalent physically; no force epsilon or tensile support is invented at floating-point sign ambiguity). A numerically inconsistent positive-force/nonpositive-acceleration pad snapshot is rejected explicitly.

The old `RecordedFlightSample3DOF.physics` remains the unchanged free-physics reference evaluated at its accepted point. It is **not relabelled as the applied constrained derivative**. The new wrapper's corresponding `guideSamples.appliedDerivative` and `netConstraintReactionWorldN` supply that distinction. Constructor checks enforce one-to-one point/physics correspondence and reject multiple/mixed-policy guidance ends. During free flight, applied derivative is the original free derivative and reaction is zero. Mass/CG are never duplicated as new physical authorities.

Initial velocity must be exactly zero in the new supported-pad API, direction must have positive WORLD-Z component, and the explicitly selected alignment mode must be `ALIGN_MOTOR_SOURCE_BOUNDARIES`. Other combinations are rejected, not silently repaired or switched. Legacy public APIs still support their full accepted inputs/modes unchanged. Missing usable geometry or primary contacts returns `NOT_EVALUABLE`, no trajectory and no invented exit metric. Datum mismatch, unsupported physical rod/tower kinds, bad dimensions/provenance and invalid engagement yield structured guide errors.

## Local numerical boundary and restart policy

Only launch-guide boundaries are localized; NAT-018's linear reporting-event policy and its estimated states remain unchanged. Existing source alignment partitions pad evaluation at accepted thrust/end and active explicit mass/CG knots. At a held pad point the accepted environment/wind are time invariant: net axial force is piecewise linear for explicit mass or quadratic for impulse-proportional mass. A fit to actual start/mid/end force evaluations is used only to locate an interior maximum that could otherwise hide a positive-force interval. All eligibility/sign decisions are reevaluated with the actual physics authority; no fitted thrust/mass law is used in accepted integration.

Liftoff uses bisection between a non-departing and departing held state. Guide exit uses repeated existing RK4 integrations from the same accepted start. Bisection is in finite double duration and stops when its midpoint can no longer lie strictly between the bounds (adjacent representable durations). The first post-boundary RK4 point is accepted; timestamps and source knots are never overwritten with an interpolated value. `timeQuantizationBoundS=max(upperTime-lowerTime, ulp(acceptedTime))` discloses the computed bracket/timestamp resolution. **It does not bound state-coordinate rounding, RK4 truncation or physical-model error**; the analytic V&V tolerances separately cover those effects.

The accepted release state is recorded once and passed unchanged to the existing free-flight loop. The remainder of the original step is executed under the released free RHS, then the selected source schedule continues. Position, velocity, time and the underlying mass/CG snapshot are continuous; acceleration/reaction can change at release. No impulse or angular velocity is added. Partial restart durations must actually reproduce their intended endpoint; nominal endpoint coincidence is handled consistently with NAT-026.1, while other unrepresentable restart/source intervals remain explicit errors. The acknowledged NAT-026.1 interior-boundary limitation is not patched here.

For return to the lower support, negative-travel RK4 trials are intercepted **before** upstream physics is requested below that support. Duration cutback retains the last nonpenetrating accepted RK4 point and advances safe prefixes as necessary; no negative accepted travel is clamped to zero. At contact or inability to advance further without penetration, incoming negative velocity and the localized pre-impact time/state/bracket are preserved, and the outcome is `IMPACT_DYNAMICS_UNSUPPORTED`. No post-impact state, restitution or energy loss is calculated. This applies while the guide constraint is active; after the explicitly approximate release, the existing free-flight ground handling is reused, not a new rail recollision solver.

Reporting burnout/apogee events reuse the existing detector during constrained advancement. Its GROUND estimate is not mislabelled as supported-pad recontact. After release, the same original terminal-ground candidate rejection/endpoint acceptance remains authoritative. Source knots do not become physical guide events. `maximumSteps` is carried through, never reset/increased: new-mode advancement candidates/partial accepted segments consume its budget; local root queries/reintegrations are numerical localization work, not additional lifecycle advancement counts. No-liftoff is a finite `NO_LIFTOFF_STEP_LIMIT` outcome, not an infinite loop.

## Competition and OpenRocket interpretation limits

Competition checks are independent of dynamics and always identify the checked metric. Missing placement/CG/orientation evidence or a missing requested exit metric returns `NOT_EVALUABLE`. The x_geo placement transform must be explicitly supplied as `x_geo=noseGuideCoordinate0-buttonGuideCoordinate0`; no motor length/rocket length/CG placement guess is made. Loaded CG and motor CG come from the initial accepted mass snapshot. Source wording does not specify boundary inclusivity for "between" checks; exact-boundary cases are reported `NOT_EVALUABLE` rather than silently certified. Declared orientation must exactly associate with the actual launch vector before the 85-degree rule is checked; no new angle tolerance/normalization is introduced. Reinforcement, actual M4 fit/profile compatibility and certification are not established by these numerical checks.

The owner-reviewed OpenRocket definition uses strict `>` displacement and step sampling after its 0.02 m liftoff flag; RoketSim uses physical support unloading and localized threshold crossing. Equal nominal length therefore does **not** establish equal sampling, liftoff times, force models, actual OpenRocket numeric parity or mechanical clearance. No OpenRocket execution was performed. The directly inspected specification's 6 m is a simulation input only. Steady accepted wind/zero-AoA drag/fixed launch-direction thrust and existing physical model domains remain limitations; no turbulence, weathercocking or attitude is added.

## Actual analytical V&V and category gates

The frozen constant-mass fixture uses existing NAT-014 with supplied mass 2 kg, thrust 30 N, gravity force -20 N and zero drag: axial net force 10 N and acceleration 5 m/s². Its actual new constrained RHS, unchanged RK4 and new localizer are exercised against independent `sqrt(2/5)` and `sqrt(10)` references, not a duplicated expected-value implementation. Unused environment/motor metadata in this synthetic snapshot is not represented as a new measured motor or full runtime constant-force catalog profile. Full native integration is separately exercised with the accepted F50 authorities.

| Nominal h (s) | Actual accepted 1 m time (s) | Actual speed (m/s) |
|---:|---:|---:|
| .5 | .6324555320336747 | 3.1622776601683737 |
| .25 | .6324555320336747 | 3.1622776601683737 |
| .125 | .6324555320336748 | 3.1622776601683737 |
| .0625 | .6324555320336748 | 3.1622776601683737 |

All pass the established local algebra convention `3e-14*max(1,abs(expected))`; constant-acceleration RK4 is already at the coordinate-roundoff plateau, not expected to exhibit further truncation-error reduction. Reported bracket resolution is 1.1102230246251565e-16 s, distinct from the total analytic error as explained above.

A separate test-only analytic `a=5+t^4` has `s=2.5*t^2+t^6/30`, `v=5*t+t^5/5`, target `s(1)=76/30` and `v(1)=5.2`. It demonstrates refinement beyond the constant-acceleration plateau without adding a production model:

| h | Accepted time | Absolute time error | Speed |
|---:|---:|---:|---:|
| .4 | 1.0000346151376809 | 3.461513768088231e-5 | 5.200381028864392 |
| .2 | 1.0000038461354497 | 3.846135449681043e-6 | 5.200036410175619 |
| .1 | 1.0000002403845434 | 2.403845433818219e-7 | 5.20000227564071 |
| .05 | 1.0000000150240382 | 1.5024038235722514e-8 | 5.200000142227562 |

Each refinement reduces error by more than fourfold; finest time error is below the existing RK4 `1e-7` local allclose convention. No historical/global tolerance changes.

| Mandatory category | Result | Executed evidence |
|---|---|---|
| PAD-01–07 | PASS | Insufficient thrust stays at nonzero initial position for 60 steps; post-burnout PAD clock, no fabricated exit; interior-step force release at unchanged pad state; hidden positive force maximum detected even with negative endpoints/midpoint; inclined gravity; supported recontact termination without penetration/velocity reset. |
| GUIDE-01–06 | PASS | Arbitrary ENU direction and lateral wind, zero intended transverse motion within established algebra roundoff, reaction closure, accepted near-unit vector unchanged, actual current mass/CG physics snapshots, four-stage mass-time queries, geometry/datum/null/nonfinite/unsupported mode rejection. |
| EXIT-01–08 | PASS | Explicit same-datum thresholds, one forward exit, geometric-only aft threshold, approximate continuation label, exact accepted-point transfer and independent first free RK4 step, disclosed event bracket, no impulse. |
| OR-01–06 | PASS (metric policy only) | Independent explicit 6 m nominal run; displacement-magnitude criterion including near-unit input; empty other-policy metric; repeat runs/data isolated; no physical usable-length or full OpenRocket parity claim. |
| Analytic reference | PASS | Frozen 2 kg/10 N/1 m fixture at four step sizes and independent nonlinear refinement table above. |
| REG-01–12 | PASS | All 580 unchanged prior Java cases plus 20 new cases, clean Java 17/JAR, packaged consumer, java.base, actual J-011 parity, unchanged MOTOR-001 and NAT-026/026.1 suites, no source data/tolerance changes, repeated new runs. |

## Full Java build, consumer and runtime independence

Windows 11 amd64, Temurin Java/javac **17.0.20.1**, Gradle Wrapper **8.12.1**. Core working directory `roketsim-native-workspace/roketsim-core`; established isolated build:

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
```

**Exit 0; 600 tests / 0 failures / 0 errors / 0 skips, 38 suites.** Exactly **580 pre-existing + 20 new**, including **19 unchanged MOTOR-001** and **18 unchanged NAT-026/026.1 boundary cases**. New focused runs also passed (initial 14 cases, expanded 20); no frozen expected value/tolerance was edited. Java 17 main/sources/Javadoc JARs built. Javadoc reached its nonblocking 100-warning display limit; no complete warning inventory claim. The final build log reports 8h25m03s elapsed wall time; no inference about CPU runtime or the cause of reconnection/idle intervals is made from that duration.

Standalone packaged-module consumer, using only the main JAR and no test classpath/Python:

```powershell
& '<JAVA_HOME>/bin/java.exe' --module-path '<local-evidence>' --add-modules org.roketsim.core verification/LaunchGuideJarConsumer.java
```

**GUIDE_JAR_CONSUMER: PASS / exit 0**. Its explicit synthetic usable guide `L=1.5`, `bForward0=.5`, `bAft0=.02`, F50, 85-degree direction and nonzero initial WORLD position produce:

- `twoPointGuidanceEndSpeedMS=14.250891267558607`, accepted time `.15316772581548294` s.
- Independent explicit nominal 6 m comparison: `openRocketNominalRodExitSpeedMS=36.11574638046541`, time `.35240646152478383` s.

These are demonstration outputs, not TEKNOFEST motor/launcher performance certification. Repeated primary consumer run equals its first result. `verifyProductionDependencies` and packaged JAR `jdeps --print-module-deps` pass, **java.base only**, exit 0. No Python, OpenRocket, Swing, JSON, native physics or other external production runtime dependency.

## Actual J-011 Python–Java parity

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS / exit 0**, real unchanged Python **3.12.4 / NumPy 2.5.2** against actual Java legacy execution. Three runs / two distinct configurations:

| Run | Samples | Events | Numeric | Exact | Accepted nonexact |
|---|---:|---:|---:|---:|---:|
| primary | 1584 | 3 | 147521 | 147511 | 10 |
| repeat | 1584 | 3 | 147521 | 147511 | 10 |
| fine | 3167 | 3 | 294740 | 294714 | 26 |

Distinct totals **442261 numeric / 442225 exact / 36 accepted nonexact**; including repeat 589782 / 589736 / 46. Maximum absolute **8.881784197001252e-16**, relative **4.3829501452845633e-16**, matching the accepted NAT-026.1 last-bit evidence under unchanged quantity-specific tolerances. No new trajectory tolerance, oracle, source fixture or output-protocol relaxation. This is historical free-flight parity; no Python or OpenRocket guide-mode parity is claimed.

Evidence outside Git: `<local-evidence>`; isolated build/XML `roketsim-nat031033-build`; raw Python JSONL/Java TSV, anchors, comparison summary and stderr in `roketsim-nat031033-trajectory`; PDF page renders in `roketsim-nat031033-sources`. After the reconnection status request, persisted source/test/document files and results were rechecked. All task-launched build/test/consumer/parity processes had completed with exit 0; fallback process inspection found no remaining Java/Python processes. No work was restarted, discarded or reset.

## Final integrity, residual limitations and commit

All in-scope gates pass. Scope intentionally excludes physical final-contact dynamics, off-axis/rotational states, torque, individual button forces, friction/compliance/bending, rail-profile/M4 fit certification, recovery, staging, attitude propagation and a universal event framework. Recontact terminates unsupported; physical usable competition rail geometry remains unknown; only the new supported zero-velocity/upward-guide/source-aligned combinations execute. Bracket localization is numerical reintegration of the accepted force models, not experimental launch validation. Existing model-domain errors propagate without clamping or source substitution.

The imported TEKNOFEST profiles are still source/catalog-ready only. This task does not supply their missing certification, ejection, mounting, mass/CG/inertia or grain-regression evidence. GUIDE-6DOF-01 and NUM-TF-01 above remain open. EK-3/OpenRocket direct-source/version limitations are not marked resolved. No physical or source-data approximation is promoted into an independently certified launcher or OpenRocket equivalence claim.

Unchanged: RK4/FixedStepConfig, PhysicsEvaluator/context/results, force equations, mass/CG/inertia authorities, MOTOR-001 production/tests/resources, NAT-025 clocks, NAT-026/026.1 planner, legacy event detector/terminal policy and result schemas, all 580 prior test sources/fixtures/tolerances, Python production/tests, ADRs and historical records. Only the engine's shared private/package continuation composition is extended as described. Final scope review confirms these 15 intended text files only; no generated build outputs, credentials or unrelated changes are included.

One local commit: `feat(simulation): add opt-in launch guide dynamics and comparison`. The commit adding this record is its final identity; exact SHA is reported after creation and is discoverable using `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/nat-031-033-launch-guide-dynamics.md`. No self-referential amend. Stage only the 15 scoped files and verify clean worktree after commit. No push/merge/history rewrite; no NAT-034/035/036 or 6DOF work started.

## NAT-031–033.1 corrective addendum — near-unit projection consistency

Date: 2026-11-01. Starting accepted HEAD `40f9e842e7d511b77888b91bed3d1fe13ab612d1` and clean tree were verified before creating `development/nat-031-033-1-projection-consistency`. This addendum supersedes only the original acceleration-projection expression above; the original implementation and verification narrative is retained as historical evidence.

The independent post-implementation audit identified a MINOR bounded physical-consistency issue: NAT-013 retains accepted near-unit directions, but the original guided acceleration was `(aFree dot u)*u` whereas coordinate/velocity extraction already divided by `u dot u`. With mass 2 kg, `aFree=(0,0,5)` and `u=(0,0,1.000000005)`, the original expression gives axial acceleration `5.00000005` instead of 5 m/s² and about `1e-7 N` unintended axial reaction. In general, the original reaction satisfies `R dot u=(Fnet dot u)*(u dot u-1)` apart from arithmetic rounding. The previous near-unit test checked direction preservation and nominal displacement, but not reaction transversality or projection idempotence.

The sole production correction is in `LaunchGuideEvaluation.evaluate`: divide the projected acceleration scalar by `Vectors.dot(u,u)` before multiplying by the unchanged launch vector. The result is `aGuide=((aFree dot u)/(u dot u))*u`. No normalization, force-authority change, new tolerance, engine/integrator/localizer modification or new policy is introduced. PAD holding and FREE_FLIGHT return behavior remain unchanged.

Four focused JUnit methods were added to `LaunchGuideTest`: above-unit direction, below-unit direction, oblique near-unit directions (both scales), and exact-unit behavior. Scales are `1 ± 5e-9`; oblique axes use `(0.6,0,0.8)` times the scale. Independently specified projection of `(0,0,5)` is `(0,0,5)` for the axial case and `(2.4,0,3.2)` for the oblique case. Tests check every component, projection idempotence, `reaction dot u = 0`, and unchanged input direction. Existing local `close` convention `3e-14*max(1,abs(expected))` is reused without alteration. The exact-unit axial result and zero reaction use exact equality. Existing primary/nominal policy, restart, recontact and legacy tests remain present and pass.

Executed commands use the Java 17 / Gradle 8.12.1 environment, isolated build directory and project/cache settings already recorded above. No clean build was repeated:

```powershell
# With the same JAVA_HOME and GRADLE_USER_HOME as above, from roketsim-core:
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --tests org.roketsim.core.LaunchGuideTest --offline --no-daemon
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
& '<JAVA_HOME>/bin/java.exe' --module-path '<local-evidence>' --add-modules org.roketsim.core verification/LaunchGuideJarConsumer.java
& '<JAVA_HOME>/bin/jdeps.exe' --print-module-deps '<local-evidence>'
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

| Actual correction verification | Result |
|---|---|
| Focused guide tests, inspected before full regression | 24 tests, zero failures/errors/skips; exit 0; Gradle 20 s |
| Full Java regression | 604 tests / 38 suites, zero failures/errors/skips; exit 0 |
| Java 17 assembly/JAR and production dependency check | PASS, Gradle 26 s, exit 0; existing Javadoc warnings remain nonblocking |
| Unchanged MOTOR-001 / NAT-026–026.1 suites | 19 / 18 tests, all pass |
| Packaged-module guide consumer | GUIDE_JAR_CONSUMER: PASS, exit 0 |
| Runtime jdeps | java.base only, exit 0 |
| Actual historical J-011 parity | NATIVE_TRAJECTORY_PARITY: PASS, exit 0 |

Consumer primary output remains speed `14.250891267558607` m/s, time `.15316772581548294` s; independent nominal output remains `36.11574638046541` m/s, time `.35240646152478383` s. These are synthetic demonstration outputs, not certified physical rail or exact OpenRocket validation.

Fresh J-011 evidence: primary and repeat each 147521 numerical / 147511 exact / 10 accepted nonexact; fine 294740 / 294714 / 26. Three runs, two distinct effective inputs: distinct 442261 / 442225 / 36; including repeat 589782 / 589736 / 46. Maximum absolute `8.881784197001252e-16`, relative `4.3829501452845633e-16`. Historical quantity-specific tolerances and fixtures are unchanged. This is legacy trajectory parity, not guide-mode Python/OpenRocket parity.

Fresh logs are `<local-evidence>`; fresh parity artifacts are in `roketsim-nat031033-1-trajectory` under the same directory. No correction build stalled or required a restart. The earlier 8h25m03s duration and unknown cause are not reinterpreted or concealed.

Final scope is exactly three files: `LaunchGuideEvaluation.java`, `LaunchGuideTest.java` and this verification record. No engine, integrator, event localizer, propulsion, source data, numerical tolerance or policy definitions changed. Original physical limitations and GUIDE-6DOF-01 / NUM-TF-01 remain open. No broader issue was discovered in this correction. One local commit: `fix(simulation): correct near-unit launch guide projection`; its exact identity is the commit containing this addendum (`git log -1 --format=%H -- <this record>`), also reported after commit. No push, merge, amend or next NAT task.
