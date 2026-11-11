# NAT-037 / N37-03C Parametric Vehicle Integration V&V

**Original result:** BLOCKED on one frozen terminal-classification assertion. All six flight and cancellation smoke cases reached their target categories. The owner-authorized oracle correction and its separate verification are recorded below. Engineering acceptance remains an owner decision.

## Scope and evidence

The sole test addition is `roketsim-core/src/test/java/org/roketsim/core/simulation/ParametricSourceVehicleIntegrationTest.java`. Its fixture builders contain the frozen inputs, and the L/M/N oracle tables preserve the full-precision independent assertion literals. All vehicles and measurement records are TEST_ONLY synthetic fixtures for the supported single-stage, single-motor Java 17 3DOF path. Nominal fit and source-backed mass models provide no physical installation or certification evidence. N37-04 quantitative event and trajectory validation remains outside this milestone.

Provenance labels are **E** for EXISTING_SOURCE, **A** for ANALYTICAL_DERIVATION, and **S** for SYNTHETIC_TEST_CHOICE. Numeric inputs below are **S** except where marked.

## Frozen fixture inventory

Dimensions and axial locations are in metres, with aft-positive `x_geo` from the nose tip. The fins are square-section trapezoids at equal angular spacing. The reference uses maximum diameter. The three surface finishes are synthetic smooth at zero equivalent roughness.

| Input | L/r1 | M/r1 | N/r1 |
|---|---|---|---|
| Diameter | 0.160 | 0.200 | 0.240 |
| Nose length and wall | Solid 0.320, absent wall | Shell 0.400, 0.003 | Shell 0.500, 0.004 |
| Body length and wall | 1.600, 0.003 | 2.000, 0.003 | 2.400, 0.004 |
| Fin count, root, tip, semi-span | 3, 0.240, 0.120, 0.110 | 4, 0.300, 0.150, 0.150 | 4, 0.360, 0.180, 0.190 |
| Fin tip LE offset, root LE, thickness | 0.070, 1.550, 0.003 | 0.100, 1.800, 0.004 | 0.130, 2.350, 0.005 |
| Mount length, ID, wall, aft recess | 0.700, 0.080, 0.002, 0.010 | 1.050, 0.080, 0.002, 0.015 | 1.250, 0.104, 0.003, 0.020 |
| Thickness of each ring, motor overhang | 0.004, 0.005 | 0.005, 0.005 | 0.006, 0.008 |
| Nose/body/fins/mount/rings density, kg/m³ | 1800/4000/2000/2000/1800 | 650/900/1100/1200/700 | 500/1150/1450/1000/1250 |
| Replacements | None | Body mass 5 kg only, nose CG 0.310 m only | Body mass 9 kg and CG 1.750 m |
| Separate hardware | None | `m-ballast`, 32 kg at 1.30 m | `n-ballast`, 65 kg at 1.50 m |
| Included item | None | None | `n-electronics` belongs to the measured body. Its contribution is already included. |
| Launch ENU position, m | (0,0,0) | (100,-200,50) | (-40,25,20) |
| Fixed direction and initial velocity, m/s | (0,0,1), (0,0,20) | (0,0.6,0.8), (0,12,16) **[A]**. Guide starts stationary. | (0,0,1), (0,0,20) |
| Launch environment geopotential altitude and wind | 1200 m, no wind | 450 m, constant ENU (4,-2,0) m/s | 800 m, no wind |
| Nominal direct-run step and alignment | 0.020 s, source-aligned | 0.020 s, source-aligned | 0.020 s, source-aligned |

The L density twin changes only the body density to 4500 kg/m³. M guide uses `IDEAL_TWO_BUTTON_RAIL`, `GEOMETRIC_TWO_POINT_GUIDANCE`, datum `TEST_ONLY:M-guide`, top 1.50 m and initial forward/aft contacts 0.50/0.02 m, with the direct nominal step and source alignment. N recovery uses `LEGACY_FIXED_STEP` at 0.015625 s with drag area 0.80 m² and activation delay 6.0 s. N cancellation uses the direct run. Other guide and recovery options are absent. Ignition is 0 s except in the numerical rejection probe.

Common **E** policies are native Basic Drag and translational dynamics, the US Standard lower atmosphere, dry air, constant 9.80665 m/s² gravity, local ENU vertical offset, curve-end burnout, downward vertical-velocity crossing, launch WORLD-Z ground plane, linear event interpolation, and `REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT`. Fresh controls use monitoring values (1 ns, 10^12 ns, 1000 s, 100000 samples), deterministic clock increments of 10^6 ns, and test-only cancellation at three accepted segments in the cancellation variant or a 20,000-segment watchdog for other flight cases. The production run remains uncapped.

| ENG evidence **[E]** | L | M | N |
|---|---|---|---|
| Curve ID | `teknofest26:TFL1256WS:Rev1` | `teknofest26:TFM1850W:Rev1` | `teknofest26:TFN3300R-P:Rev1` |
| Diameter × length, m | 0.075 × 0.5306 | 0.075 × 0.9354 | 0.098 × 1.0721 |
| Initial/propellant mass, kg | 3.905 / 1.905 | 7.010 / 4.103 | 12.645 / 7.668 |
| Raw token and kind | `-` / UNKNOWN | `1000` / UNKNOWN | `P` / PLUGGED |
| ENG SHA-256 | `756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd` | `111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc` | `10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8` |

The existing source binding uses `RELOADABLE`, `ENG_INITIAL`, `ENG_PROPELLANT`, impulse-proportional mass and fixed midpoint CG. No explicit curves, ejection delay or certification evidence are supplied. The qualification records the unresolved physical installation datum. Configuration IDs are `n37-03c:<family>:r1` and `n37-03c:<family>:<variant>:r1`. Structural measurement references are separate for mass and CG. Ballast references are distinct `MODEL_BASED` evidence for the two fields. The L/M/N data, model selection and provenance stay associated with the exact prepared configuration returned alongside each result.

## Independent oracles and comparison policy

The expected literals were derived with 60-digit decimal arithmetic from the frozen decimal dimensions and Rev1 ENG text. Reference area is πR². Use the outer volume minus the inset inner cone for a hollow nose, and annular volumes for cylinders and rings. Integrating each fin chord strip supplies its volume and centroid. Component mass and moment are ρV and mx. Effective values replace only the measured fields before summing first moments. Mount fit uses aft reference, selected nominal motor length, radial clearance and the intersection of the motor and mount spans. Propulsion integrates the piecewise-linear ENG samples by trapezoids, including the accepted zero origin. Evaluate mass as initial mass minus propellant mass times impulse fraction. Vehicle first moment adds the effective structure, one installed motor and separately owned hardware.

| Oracle **[A]** | L | M | N |
|---|---|---|---|
| Reference area, m² | 0.0201061929829747 | 0.0314159265358979 | 0.0452389342116930 |
| Motor front/aft, m | 1.3844 / 1.915 | 1.4546 / 2.390 | 1.8159 / 2.888 |
| Radial clearance/engagement, m | 0.0025 / 0.5256 | 0.0025 / 0.9304 | 0.0030 / 1.0641 |
| Calculated structure mass / moment / CG | 14.5965357819681 / 13.5488628364735 / 0.928224548540561 | 4.99818283637129 / 7.42971100668993 / 1.48648243770209 | 11.7996026425540 / 21.7821478316940 / 1.84600689459991 |
| Effective structure mass / moment / CG | Same as calculated | 6.65615657148246 / 9.76098514419128 / 1.46645966623007 | 12.6143714791850 / 23.6172548539667 / 1.87224982972299 |
| Initial vehicle mass / CG | 18.5015357819681 / 1.08050172548146 | 45.6661565714825 / 1.41978903003806 | 90.2593714791850 / 1.67137949369342 |
| Depleted vehicle mass / CG | 16.5965357819681 / 1.01516744565326 | 41.5631565714825 / 1.37018253525204 | 82.5913714791850 / 1.60819353916942 |
| Total impulse, N·s | 3650.044730405055 | 7649.329 | 14325.010771 |

The test tables additionally hold all component volumes, masses, centroids and moments, plus the initial, midpoint, exact-knot, depleted and post-end checks. The observed results satisfied every scalar assertion. No independent numerical oracle is under review.

Use exact checks for ENG knot times and thrust, contractual zeros, unchanged fields, identities, and selected override values. Use the frozen 128 ULP budget for designated nonzero direct geometry and propulsion values, including metres converted from ENG millimetres. These use short floating-point expressions or trapezoid sums over at most 26 intervals with positive contributions. The 512 ULP budget covers subtractive cone/annulus volumes, component mass and centroid/first-moment checks, and structural and vehicle aggregates. In these fixtures shell subtraction amplifies upstream roundoff and centroid or moment arithmetic propagates it. The displayed nontrivial oracle literals carry at least 15 significant digits in the test. Both budgets scale with each expected magnitude, and every scalar failure would show absolute error, relative error where meaningful, and distance in expected ULPs. The L matched-state acceleration check uses the exact sign of the non-gravity force multiplied by the inverse-mass difference, with no additional magnitude allowance. J-011 uses its original tolerance definitions.

## Executed outcomes

| Case | Classification | Actual result |
|---|---|---|
| Geometry, installation and calculated/effective mass | TEST_PASS | Three family invocations, including component moments and exact preserved fields. |
| Source identity and motor/vehicle snapshots | TEST_PASS | Three family invocations. The ENG knot, independent interpolation and depletion checks passed. |
| L density, M CG-only and reordered overrides, N absorbed ownership | TEST_PASS | Three matched-state checks. The L prepared and direct engine histories also matched exactly in the smoke test. |
| Invalid preparation and incompatible M mount | TEST_PASS | Eight preparation invocations plus the mount test. |
| Direct Mach/static/atmosphere evaluator probes and scheduler representation | TEST_PASS | Two tests. The historical coincident-endpoint case passed in the full suite. |
| L / M / N direct | TEST_PASS | GROUND, respectively 1911 / 1390 / 1712 accepted segments. |
| M guide | TEST_PASS | GROUND, 1260 accepted segments, LIFTOFF then TWO_POINT_GUIDANCE_END. |
| N recovery | TEST_PASS | GROUND, 3049 accepted segments, PHYSICAL_APOGEE and RECOVERY_ACTIVATION present, final ACTIVE. |
| N cancellation | TEST_PASS | CANCELLED, USER_CANCELLED, three accepted segments and no ground event. |
| L nonadvancing time | TEST_PASS | NUMERICAL_FAILURE, MOTOR_BOUNDARY_NONADVANCING_TIME, zero accepted segments, one initial sample, no events. Ignition 1e16 s and step 0.020 s. |
| L initial 400 m/s run | TEST_FAIL | Frozen expectation MODEL_DOMAIN_FAILURE. Actual UNSUPPORTED with code UNSUPPORTED_MACH_REGIME. See below. |

### Preserved first failing case

`lBeyondMachOneHasFrozenGroundRunDomainOutcome`, fixture `n37-03c:L:supersonic:r1`. Reproduce by using the exact L direct configuration with initial WORLD velocity (0,0,400) m/s. The initial environment altitude is 1200 geopotential m and wind is zero. Basic Drag's (0\leq M\leq1) domain implies rejection of the initial state. The frozen oracle specifies the ground-run result category MODEL_DOMAIN_FAILURE and no ground event. The first failed assertion is in the new test at line 675.

The runtime rejected Mach `1.1916937132603589` with `AerodynamicEvaluationError` and code `UNSUPPORTED_MACH_REGIME`. The ground wrapper returned `UNSUPPORTED`. The result had zero accepted segments and empty sample, ground/flight event, guide and recovery lists. The focused XML retains the complete result. This is an enum mismatch, with no scalar absolute, relative or ULP difference and no numerical tolerance. The source trace is `BasicDragEvaluator.java:43` followed by `GroundRunExecution3DOF.java:123` (extracts the typed error code) and `:141` (classifies codes containing UNSUPPORTED before the generic model-domain classification).

The direct evaluator domain checks passed. At the original verification gate, the difference required owner review of the frozen outcome against the accepted ground-run mapping. The original test result and all raw evidence remain available in the historical run artifacts below.

## Verification artifacts

Artifacts are under `<local-evidence>`. The original wrapper ran Gradle 8.12.1 on Temurin Java 17 with fresh temporary caches and output directories and strict dependency verification. Only the installed pinned distribution was reused. Logs include the exact commands and exit codes.

| Gate | Evidence and outcome |
|---|---|
| Initial test compilation | `focused.log`, exit 1. TEST_NOT_EXECUTED. Corrected only the new test's record accessor from `ejectionMetadata()` to `ejection()` and aligned the converted geometry scalar assertions with the existing 128 ULP contract before any assertions ran. |
| Focused JUnit | `focused-r1.log`, `focused-r1-xml`. 28 executed, 27 passed, one failed, zero skipped. Exit 1. |
| Full Gradle/JUnit | `full.log`, `build/test-results/test`. 755 executed across 48 classes, 754 passed, one failed, zero skipped. All 727 historical tests passed. Overall command exit 1 because of the same frozen assertion. |
| Gradle assembly and resolved production dependencies | The full command used `--continue`. `assemble` and `verifyProductionDependencies` completed successfully. Main, source and Javadoc JARs are under `build/libs`. |
| Production module dependencies | `jdeps.log`. `java.base` only, exit 0. |
| Existing packaged consumers | Ground, LaunchGuide, PreparedSourceVehicle, RecoveryDrag, Recovery and SourceMotor all passed and exited 0 against the new Gradle main JAR. Each has its own `*JarConsumer.log`. |
| Actual J-011 | `parity.log` and `trajectory/comparison-summary.json`. PASS, exit 0, 147,521 primary + 294,740 fine-step = 442,261 distinct numeric comparisons. The repeat does not add distinct comparisons. Thirty-six distinct values differ within the unchanged tolerances. |

No test gate was blocked by the environment after the compilation correction. No frozen test was skipped, disabled or weakened after the first assertion failure.

## Repository integrity and recommendation

Branch `development/nat-035-apogee-recovery`, HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae`. The starting status and 511 SHA-256 fingerprints are in `<local-evidence>`. All 511 pre-existing fingerprints matched afterward. The exact delta is this record and the new parameterized test, both untracked. Final inventory is 15 tracked modifications, 41 untracked files and an empty index. `git diff --check` passed with line-ending notices on previously dirty files.

At the original gate, the recommendation was to hold engineering acceptance until the owner resolved the classification discrepancy. Quantitative guide, apogee, activation and touchdown timing and step-size sensitivity belong to N37-04.

## Owner-authorized terminal-classification oracle revision

The owner approved changing only the test's expected status from `MODEL_DOMAIN_FAILURE` to `UNSUPPORTED`. NAT-012a1 defines `UNSUPPORTED_MACH_REGIME` for Mach above 1 (`docs/archive/nat/nat-012a1-basic-drag.md:41`). The accepted NAT-036 policy preserves the underlying error code and distinguishes unsupported conditions from generic model-domain failures (`docs/verification/nat-036-ground-termination-progress.md:132`). `GroundRunExecution3DOF.java:123` extracts the aerodynamic code and line 141 maps it to `UNSUPPORTED` before the generic model-domain branch. This reproduces the original run's code and status. The earlier expectation was an incorrect oracle for that contract.

The unchanged `n37-03c:L:supersonic:r1` fixture still starts at 400 m/s vertical WORLD velocity. The corrected assertion requires `UNSUPPORTED`, the exact code `UNSUPPORTED_MACH_REGIME`, zero accepted segments, empty samples and flight/guide/recovery events, and no ground event. Configuration identity is still asserted. Production behavior, all synthetic inputs, analytical oracles and numerical tolerances are unchanged.

The original focused XML, focused command log and full command log remain in `<local-evidence>`. The preserved focused XML SHA-256 is `fff13f0cfeedad2de35430396f60c1e8881de4df49e731f7c6da8ecf9620401c`.

### Final verification

The fresh verification directory is `<local-evidence>`. Gradle 8.12.1 ran under Temurin Java 17.0.20.1 using the existing wrapper, dependency locks, strict dependency verification and repository isolation script. Only the pinned distribution was reused. The Gradle cache, project cache and build output were isolated from the previous run.

| Gate | Outcome and evidence |
|---|---|
| Focused N37-03C | `focused.log` and `focused-xml`. Exit 0, 28 executed and passed, zero failed, errors or skipped. The unchanged probe reported Mach `1.1916937132603589`, `UNSUPPORTED`, code `UNSUPPORTED_MACH_REGIME`, and zero accepted segments, samples and events. |
| Full Gradle test, assemble and dependency verification | `full.log` and `build/test-results/test`. Exit 0, 755 executed and passed across 48 classes, zero failed, errors or skipped. Main, source and Javadoc JARs are byte-identical to the original N37-03C run. Javadoc completed with warnings. |
| Packaged consumers and production dependencies | Ground, LaunchGuide, PreparedSourceVehicle, RecoveryDrag, Recovery and SourceMotor each passed against the new main JAR. All consumer exit codes were 0. `jdeps.log` records exit 0 and `java.base` only. Gradle's resolved production dependency gate passed. |
| Actual J-011 | `parity.log` and `trajectory/comparison-summary.json`. Exit 0 with unchanged tolerances. 147,521 primary plus 294,740 fine-step comparisons give 442,261 distinct numeric comparisons. Thirty-six distinct values differed within tolerance. The repeat run is excluded from the distinct count. |

All six frozen flight and cancellation smoke cases retained their expected categories. No verification failures remain.

The correction started and finished on `development/nat-035-apogee-recovery` at `4c31dba3be4a133529dcad07980dbe9da8387bae`, with 15 tracked modifications, 41 untracked files and an empty index. The pre-correction manifest is `<local-evidence>`. Of its 513 files, only the authorized test and this verification record changed. Reversing the single assertion hunk in memory reproduced the pre-correction test SHA-256. `git diff --check` passed.

N37-03C CORRECTED AND VERIFIED — ENGINEERING ACCEPTANCE PENDING
