# NAT-037 / N37-04B1 Integrated Flight Analytical and Event-Semantics Verification

**Scope:** Test-only analytical oracles, event contracts, and native step-grid observations. Continuous full-flight error budgets and physical validation are outside this result.

## Frozen fixture identity

The only new Java source is `roketsim-core/src/test/java/org/roketsim/core/simulation/IntegratedFlightVerificationTest.java`. The accepted N37-03C fixture builders and [manifest](nat-037-n37-03c-parametric-vehicle-integration.md) remain unchanged. I compared the builder sections of the two test files independently of engine execution. They match exactly apart from the class qualifier on the hardware-evidence method reference. The sweep rebuilds the run record using every original field and replaces only `FixedStepConfig` with the requested candidate value. The recorded configuration IDs retain the frozen fixture revision. The sweep label distinguishes the step variants.

| Fixture field | L direct | M guided | N recovery |
|---|---|---|---|
| Airframe diameter, m | 0.160 | 0.200 | 0.240 |
| Nose type, length, wall, m | Solid, 0.320, absent | Shell, 0.400, 0.003 | Shell, 0.500, 0.004 |
| Body length and wall, m | 1.600, 0.003 | 2.000, 0.003 | 2.400, 0.004 |
| Fin count, root chord, tip chord, semi-span, m | 3, 0.240, 0.120, 0.110 | 4, 0.300, 0.150, 0.150 | 4, 0.360, 0.180, 0.190 |
| Fin tip LE offset, root LE, thickness, m | 0.070, 1.550, 0.003 | 0.100, 1.800, 0.004 | 0.130, 2.350, 0.005 |
| Mount length, ID, wall, recess, m | 0.700, 0.080, 0.002, 0.010 | 1.050, 0.080, 0.002, 0.015 | 1.250, 0.104, 0.003, 0.020 |
| Each ring thickness and motor overhang, m | 0.004, 0.005 | 0.005, 0.005 | 0.006, 0.008 |
| Nose/body/fins/mount/rings density, kg/m³ | 1800/4000/2000/2000/1800 | 650/900/1100/1200/700 | 500/1150/1450/1000/1250 |
| Structural selections | Calculated only | Body mass 5 kg only, nose CG 0.310 m only | Body mass 9 kg and CG 1.750 m |
| Hardware and ownership | Empty | `m-ballast`, 32 kg at 1.30 m | `n-ballast`, 65 kg at 1.50 m. `n-electronics` included in the body measurement. |
| Source ENG designation, revision | TFL1256WS, Rev1 | TFM1850W, Rev1 | TFN3300R-P, Rev1 |
| Nominal motor diameter/length, mm | 75/530.6 | 75/935.4 | 98/1072.1 |
| Loaded/propellant mass, kg | 3.905/1.905 | 7.010/4.103 | 12.645/7.668 |
| WORLD ENU launch position, m | (0,0,0) | (100,-200,50) | (-40,25,20) |
| Initial velocity, m/s, and fixed direction | (0,0,20), (0,0,1) | (0,0,0), (0,0.6,0.8) | (0,0,20), (0,0,1) |
| Geopotential launch altitude and wind | 1200 m, none | 450 m, constant ENU (4,-2,0) m/s | 800 m, none |
| Guide/recovery | Absent | Ideal two-button, `TEST_ONLY:M-guide`, top 1.50 m, forward/aft contacts 0.50/0.02 m. No recovery. | No guide. Area 0.80 m², delay 6.0 s. |
| Step alignment | Source-aligned | Source-aligned | Legacy fixed-step |

Fins remain square-section and equally spaced, reference geometry is maximum diameter, and all three surface finishes are smooth at zero equivalent roughness. Common policies are lower US Standard Atmosphere, dry air, constant gravity, local ENU vertical offset, Basic Drag and fixed-direction translational dynamics. The event profile remains curve-end burnout, downward vertical-velocity crossing, launch WORLD-Z ground reference, and linear event interpolation. Terminal policy remains reject interior candidate and accept endpoint. Ignition is zero in all twelve candidates. N37-03C TEST_ONLY design and measurement labels, separate ballast mass/CG evidence, and included-item identity are retained. Motor bindings use the original ENG source and checksums, reloadable type, impulse-proportional mass, fixed midpoint CG and nominal installation, with no explicit mass/CG curves or certification evidence. Raw ejection tokens remain `-`, `1000`, and `P`, respectively. No physical vehicle is represented.

The deterministic control uses the frozen monitoring values of 1 ns, 10^12 ns, 1000 s and 100000 samples, with 10^6 ns clock increments and the test-only 20000-segment watchdog. The separate cancellation case retains N's direct configuration at 0.020 s, with no guide or recovery, and compares cancellation at three committed segments with cancellation immediately before the fourth commit. The L negative probes retain the 400 m/s input and the 1e16 s ignition with 0.020 s step, respectively.

## Analytical and event-semantics matrix

| Check | Independent authority and result |
|---|---|
| Ballistic trajectory and NAT-018 apogee | PASS. With no drag and gravity 8 m/s², use z(t) = 5.1t − 4t² and vz(t) = 5.1 − 8t. The continuous apex is 0.6375 s at 1.625625 m. RK4 at dyadic 0.125 s endpoints agrees with the polynomial. The chord across 0.625 and 0.750 s reports the same apex time for linear vz but altitude 1.62 m. The derived chord deficit is 0.005625 m. |
| Ballistic ground | PASS. Positive root is 1.275 s, with vz = −5.1 m/s. The 1.25/1.375 s endpoint heights are 0.125/−0.55 m, giving chord fraction 5/27 and time 1.25 + 0.125 × 5/27. Chord velocity is 5.1 − 8 × chord time. Interior contact preserves the start as the last accepted sample. An independently supplied exact contact endpoint is retained with fraction 1. |
| Ballistic recovery detection seam | PASS. The analytic 1/16 s grid peaks at 0.625 s and 1.625 m. At 0.6875 s the altitude loss is 0.009375 m. The first strict loss exceeding 0.01 m is at 0.75 s. The existing seam's 1 s delay schedules 1.75 s. Only analytic points enter this monitor. There is no drag-free native flight mode. |
| Guide analytical regressions and M native handoff | PASS. Full regression reuses `LaunchGuideTest` constant-acceleration and nonlinear oracles. M has a 1.00 m target from top minus forward contact. The projected accepted exit reaches that target and matches the first free-flight point exactly. The next free-flight step consumes the reconstructed remainder. This is two-point guidance end. Aft clearance and continuous exact exit time remain outside the comparison. |
| Source knots, impulse, depletion and axial CG | PASS for all three Rev1 motors. Independently parse the original decimal ENG rows, include the implicit zero, and integrate each interval by trapezoid, including one independent partial-segment probe. Full impulses are 3650.044730405055, 7649.329, and 14325.010771 N·s. Every listed knot time and force is exact. Mass is loaded mass minus propellant mass times impulse fraction. Installed midpoint CGs are 1.6497, 1.9223, and 2.35195 m. The post-end probes have zero thrust and depleted mass. Successful aligned histories contain every eligible source endpoint. Burnout occurs once at 2.967, 6.750 and 4.613 s, respectively. |
| Native N recovery | PASS. Independent eligible-sample scan matches the first strict altitude-drop detection. The event named `PHYSICAL_APOGEE` holds that sample. Activation uses detection + 6 s, has zero thrust, and matches the first active accepted point. The terminal state remains ACTIVE. |
| Terminal ordering | PASS. Synthetic shared endpoint orders BURNOUT, APOGEE, GROUND and retains the endpoint. Pending coincident activation does not run. The under-thrust activation seam returns UNSUPPORTED with `RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST` and no activation. The full suite also retains the accepted ground-before-activation and coincident-ground-under-thrust regressions. |
| Accepted history and negative classifications | PASS. N cancellation preserves the exact three-segment prefix with no ground event. L Mach probe returns UNSUPPORTED / UNSUPPORTED_MACH_REGIME at Mach 1.1916937132603589 with empty history. L nonadvancement returns NUMERICAL_FAILURE / MOTOR_BOUNDARY_NONADVANCING_TIME with one initial sample, zero accepted segments and no events. Full regression retains the exact coincidence and unrepresentable-interior scheduler tests. |

Comparison allowances are local to these analytical seams. Ballistic RK4 uses 32 ULPs of `max(1, abs(expected))` to cover the short sequence of at most eleven weighted updates and cancellation in the polynomial. Analytic chord comparisons use eight ULPs on the same unit scale for the short endpoint arithmetic. The unit scale covers expected values near cancellation zero. Source arithmetic uses the existing N37-03C 128 ULP policy for the designated nonzero propulsion scalars. Decimal impulse accumulation and closed-form fixture calculations supply expected values. Source knots, terminal categories, ordering and exact boundary identities retain exact comparisons. These allowances do not apply to native continuous trajectories or J-011.

## Native candidate observations

All twelve candidates returned GROUND and had no failure code. Each retained one initial sample plus the reported accepted segment count. The full step sequence and event states are in the focused XML. Each `B1_SAMPLE` row includes accepted time, actual difference from the previous time, source-knot flag, guide/recovery boundary tags, phase, WORLD position, velocity and Mach. `B1_EVENTS`, `B1_GUIDE` and `B1_DEPLOYMENT` retain the event times, positions and velocities. N's sampled-peak analysis is in `B1_RECOVERY`. The terminal estimate is distinct from the last accepted sample in all twelve runs.

| Fixture / nominal step s | Segments | Observed min/max accepted Δt s, rounded | Noninitial source endpoints / shortened versus nominal proposed endpoint | Max accepted-sample Mach | Estimated ground time s / vertical velocity m/s |
|---|---:|---|---:|---:|---|
| L / 0.020 | 1911 | 0.001 / 0.020 | 16/15 | 0.565285888 | 38.120735354 / −150.940597 |
| L / 0.010 | 3818 | 0.001 / 0.010 | 16/16 | 0.565285888 | 38.120736251 / −150.940613 |
| L / 0.005 | 7633 | 1.77636e−15 / 0.005 | 16/16 | 0.565285888 | 38.120736516 / −150.940618 |
| L / 0.0025 | 15255 | 1.77636e−15 / 0.0025 | 16/16 | 0.565285888 | 38.120736561 / −150.940619 |
| M / 0.020 | 1260 | 5.32907e−15 / 0.020 | 23/23 | 0.383228315 | 24.980610207 / −103.485903 |
| M / 0.010 | 2510 | 5.32907e−15 / 0.010 | 23/23 | 0.383228401 | 24.980613648 / −103.485943 |
| M / 0.005 | 5008 | 5.32907e−15 / 0.005 | 23/23 | 0.383228716 | 24.980613760 / −103.485944 |
| M / 0.0025 | 10004 | 5.32907e−15 / 0.0025 | 23/23 | 0.383228716 | 24.980613817 / −103.485945 |
| N / 0.03125 | 1524 | 0.03125 / 0.03125 | 0/0 | 0.410022970 | 47.641899036 / −42.454725 |
| N / 0.015625 | 3049 | 0.015625 / 0.015625 | 0/0 | 0.409973928 | 47.643558646 / −42.454721 |
| N / 0.0078125 | 6098 | 0.0078125 / 0.0078125 | 0/0 | 0.409985194 | 47.645300595 / −42.454721 |
| N / 0.00390625 | 12197 | 0.00390625 / 0.00390625 | 0/0 | 0.409986093 | 47.648015602 / −42.454720 |

Each flight's NAT-018 report order is BURNOUT → APOGEE → GROUND. M's LIFTOFF and TWO_POINT_GUIDANCE_END precede burnout. Observed M exit times for the four grids are 0.2736673049094088, 0.27366730403976997, 0.2736673039854174 and 0.27366730398182665 s. The subsequent free-flight steps are 0.014332695090591152, 0.00433269596023006, 0.004332696014582638 and 0.0018326960181733742 s. These consume the reconstructed parent remainder. Neither the observed spread nor a collapsed timestamp bracket is an exit-time accuracy bound.

| N nominal step s | NAT-018 apogee estimate s | Sampled peak time s / WORLD Z m | Strict-drop detection s | Activation s |
|---:|---:|---|---:|---:|
| 0.03125 | 17.622069982 | 17.625 / 1272.209762467 | 17.6875 | 23.6875 |
| 0.015625 | 17.620486431 | 17.625 / 1271.897818877 | 17.671875 | 23.671875 |
| 0.0078125 | 17.620775732 | 17.6171875 / 1271.953275923 | 17.671875 | 23.671875 |
| 0.00390625 | 17.620755139 | 17.62109375 / 1271.949310483 | 17.66796875 | 23.66796875 |

N's six-second delay is on each dyadic grid, so activation changes the regime at a grid boundary without shortening a segment. Its legacy-mode burnout is a report inside a step. N's reported apogee estimate varies nonmonotonically, and its detection stays at the same sample time across two successive refinements. Neither is a continuous apex oracle.

**Numerical finding.** Several successful aligned cases accepted very short positive intervals to reach a nearby source knot exactly. For example L at 0.005 s advanced from 2.7039999999999984 to 2.704 s, and M at 0.020 s advanced from 4.413999999999994 to 4.414 s. These were representable and retained. The samples are evidence against interpreting nominal step as minimum actual step. No repair or failure reclassification was made. The source and recovery grids remain distinct. The observed Mach maxima describe accepted samples only, with no claim about RK stages or a continuous maximum.

## Executed verification and artifacts

Run directory `<local-evidence>`. Exact commands and exit codes are in the logs. Gradle 8.12.1 ran under Temurin Java 17 with strict dependency verification, existing locks, fresh isolated caches and the pinned installed distribution.

| Gate | Result |
|---|---|
| B1 focused | `focused.log` and `focused-xml/TEST-org.roketsim.core.simulation.IntegratedFlightVerificationTest.xml`. 21 executed, 21 passed, 0 failed, 0 skipped, 0 blocked. Exit 0. |
| Full Java | `full.log` and `build/test-results/test`. 776 executed across 49 classes, 776 passed, 0 failed, 0 skipped, 0 blocked. Includes all 755 prior tests and existing guide oracles. Exit 0. |
| Assembly and dependencies | `assemble` and `verifyProductionDependencies` completed. Main, sources and Javadoc JARs produced. Javadoc emitted warnings. `jdeps.log` reports `java.base` only, exit 0. |
| Packaged consumers | All six existing consumers passed against the new Gradle main JAR with exit 0. Separate `*JarConsumer.log` files retain results. |
| Actual J-011 parity | `parity.log`, `trajectory/comparison-summary.json`. Exit 0, 442,261 distinct comparisons (147,521 primary and 294,740 fine). Thirty-six distinct values differ within unchanged tolerances. The repeat run adds no distinct comparisons. |

## B2 reference qualification requirements

Proposed inputs are the pinned ENG bytes, exact N37-03C geometry/material/ownership definitions, initial states, atmosphere, guide and recovery settings above, together with approved numerical test grids. Independently review the force, mass, atmosphere and event equations and qualify a separate high-accuracy solver against the closed-form and independently calculated component cases. Demonstrate tolerance refinement and stable event brackets. Keep reference uncertainty separate from native state error, event interpolation, sampled trigger delay and timestamp quantization. For recovery, compare a trajectory conditioned on the native scheduled activation separately from the accepted-sample trigger assessment. Freeze allowable time, position and velocity errors with owner approval before using a native full-flight reference. Preserve the observed runs as evidence and use no recorded trajectory as its own oracle.

## Repository integrity

Starting and final branch `development/nat-035-apogee-recovery`, HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae`. All 513 pre-existing tracked and nonignored untracked file fingerprints match `<local-evidence>`. Only this record and the new test were added. Final inventory is 15 tracked modifications, 43 untracked files and an empty index. `git diff --check` passed with pre-existing line-ending notices. No production source, motor resource, accepted test, parity tolerance or historical verification record changed.

N37-04B1 ANALYTICAL / EVENT SEMANTICS VERIFIED — ENGINEERING ACCEPTANCE PENDING

N37-04B2 INDEPENDENT NUMERICAL REFERENCE — NOT YET AUTHORIZED
