# NAT-039 independent L direct flight reference

**Recommendation** `NAT039_ACCEPTANCE_RECOMMENDED` for the frozen reporting-only trajectory comparison and deterministic checkpoints. Owner acceptance is pending.

`OPENROCKET_COMPARISON_DEFERRED_TO_APPLICATION_INTEGRATION`

## Fixture and independence

The only native case is `n37-03c:L:r1`, with saved source-aligned steps of 0.020 s and 0.010 s. WORLD position starts at (0, 0, 0) m, velocity at (0, 0, 20) m/s, and direction is fixed upward. Ignition is at zero, wind is zero and launch geopotential input is 1200 m. Guide and recovery are absent. The accepted L geometry, calculated structure and nominal installation are specified in the [B1 fixture record](nat-037-n37-04b1-integrated-flight-semantics.md#frozen-fixture-identity). The engine uses `TFL1256WS` Rev1, SHA-256 `756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd`, impulse-proportional mass and fixed midpoint motor CG. The source supplies a 530.6 mm nominal length and 3.905/1.905 kg loaded/propellant masses. The source midpoint is nominal.

The test-only [reference](../../roketsim-core/verification/nat039_l_reference.py) solves ż = v and v̇ = (thrust + signed drag) / total mass − g. It derives geometry and structure independently, parses raw ENG, integrates source trapezoids in decimal arithmetic and computes thrust, mass, atmosphere, air properties and Basic Drag without Java or the existing Python parity implementation. For this case v is also signed wind-relative velocity. The atmosphere query is 1200 + z and all reference evaluations remained in the first atmosphere layer and the supported Mach domain. The solver restarts at all 16 noninitial ENG knots, including 2.967 s burnout, and at the two low-Reynolds transitions and continuous apogee. Ground is the descending root after launch.

The saved and fresh full-regression B1 reports contained 5,735 identical ordered L run, event and sample lines for the two grids. The saved traces contain times and states, but omit checkpoint thrust, impulse and total mass. The [test-only exporter](../../roketsim-core/verification/Nat039SnapshotExport.java) reuses the frozen B1 fixture builder and control. Its four state checkpoints matched the saved values exactly before the supplemental fields were accepted. The rejected ground candidate is absent from the saved accepted history and is unavailable for interpolation decomposition. Saved B1 XML (yerel kanıt, bu depoda yok), Fresh B1 XML (yerel kanıt, bu depoda yok), Snapshots (yerel kanıt, bu depoda yok), Export command (yerel kanıt, bu depoda yok).

## Isolated reference and pre-comparison qualification

CPython 3.12.4 x64, SciPy **1.15.3 DOP853**, NumPy **2.2.6**. The [dependency lock](../../roketsim-core/verification/nat039-requirements.txt) records the PyPI wheel SHA-256 digests. Direct wheel downloads stalled or timed out. The mirror range downloads assembled to the official digests, and offline `pip install --require-hashes` and `pip check` passed in a temporary environment. The first sandbox bootstrap failed in ensurepip. The escalated bootstrap succeeded. The initial Java source-file launcher could not access the package-private test across class loaders. Compiling the exporter into the temporary classpath resolved it. Installation log (yerel kanıt, bu depoda yok), Failed bootstrap (yerel kanıt, bu depoda yok), Initial exporter failure (yerel kanıt, bu depoda yok).

The standalone qualification command opened no native input. The later comparison command reproduced the qualification JSON byte for byte before opening the native trace and snapshot export. Pre-comparison policy (yerel kanıt, bu depoda yok), Qualification log (yerel kanıt, bu depoda yok), Qualification JSON (yerel kanıt, bu depoda yok), Comparison log (yerel kanıt, bu depoda yok).

| Qualification | Result |
|---|---|
| Ballistic trajectory and roots | At 0.4 s, 1.4 m and 1.9 m/s. Analytical apex 0.6375 s and 1.625625 m. Descending ground root 1.275 s with −5.1 m/s. Largest absolute residual across these SI checks was 3.11e−15. |
| Constant quadratic drag | From rest at 3 m, with g = 8 m/s² and drag per unit mass coefficient 0.2 m⁻¹, the closed-form contact time and speed are 0.954659926806583 s and −5.28698699861385 m/s. Residuals −3.33e−16 s and +1.78e−15 m/s. A 0.25 s position check also passed. |
| Source and depletion | All 17 knots, including the implicit origin, passed exact checks. Total impulse 3650.044730405055 N·s. Loaded, intermediate at 1.4835 s, and depleted motor mass approximately 3.905, 2.818710177158778 and 2.000 kg. Largest mass residual 4.44e−16 kg. |
| Structure and total mass | Reference structure approximately 14.59653578196813 kg. Initial and depleted total mass approximately 18.50153578196813 and 16.59653578196813 kg. Largest residual against the independent tabulated anchors 3.02e−14 kg. |
| Atmosphere and force | At (z m, v m/s) of (0, 20), (500, −100), and (1000, 0), respectively, drag is −1.73591370201921, +37.7391225789885 and 0 N. Largest drag residual against the separate 50-digit Decimal formulation was 7.11e−15 N. Density and sound speed checks passed at all three states. |

**42 analytical checks passed.** The closed-form seams use a 1e−9 SI qualification target. Motor and mass anchors use the fixed fixture arithmetic allowance of 128 ULPs. Atmosphere and drag check against the separately formulated Decimal anchors within 256 ULPs. The 1e−7 SI tightest-adjacent reference stability gate checks refinement on the reference alone.

| Level | Relative / absolute solver setting | Max step (s) | Accepted reference steps | Apogee time (s) | Apogee height (m) | Ground time (s) | Ground vertical velocity (m/s) |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | 1e−9 / 1e−11 | 0.100 | 407 | 18.59678814338236 | 1658.5663927674996 | 38.120736583230126 | −150.94061962238567 |
| 2 | 1e−11 / 1e−13 | 0.050 | 790 | 18.596788143398296 | 1658.5663927675082 | 38.12073658324589 | −150.94061962238595 |
| 3 | 2.5e−13 / 2.5e−15 | 0.025 | 1558 | 18.596788143398317 | 1658.5663927675062 | 38.120736583246895 | −150.94061962238615 |

For state and event quantities the reported uncertainty is the three-level spread plus eight ULPs of the finest result. The spread includes the solver's dense output and root localization across levels. It is empirical and has no certified enclosure. Fine root residuals were approximately 1.45e−14 m/s at apogee and −2.00e−14 m at ground. The deterministic source and mass rows use an eight-ULP arithmetic uncertainty with the predeclared 128-ULP scalar criterion. Burnout time is an exact source timestamp. All trajectory tolerances were left unset before comparison.

## Native-reference comparison

Deltas are native minus reference. The powered checkpoint is 1.340 s, burnout is 2.967 s, and the first accepted coast samples at or after 10 s occur at 10.006999999999874 s and 10.00699999999985 s for the two grids. The JSON records exact input timestamps and full output precision. Full comparison JSON (yerel kanıt, bu depoda yok).

| Step (s) | Checkpoint | Quantity | Native | Reference | Signed difference | Absolute difference | Empirical reference uncertainty | Limit | Class |
|---:|---|---|---:|---:|---:|---:|---:|---:|---|
| 0.020 | powered | altitude (m) | 84.0999965567387 | 84.0999966521184 | -9.53797041347570e-8 | 9.53797041347570e-8 | 1.70530256582424e-13 | — | REPORTING_ONLY |
| 0.020 | powered | vertical speed (m/s) | 109.716717751376 | 109.716717794222 | -4.28464943524887e-8 | 4.28464943524887e-8 | 1.70530256582424e-13 | — | REPORTING_ONLY |
| 0.020 | powered | total mass (kg) | 17.5218025807727 | 17.5218025807727 | 0 | 0 | 2.84217094304040e-14 | 4.54747350886464e-13 | PASS |
| 0.020 | powered | thrust (N) | 1445.39967400000 | 1445.39967400000 | 0 | 0 | 1.81898940354586e-12 | 2.91038304567337e-11 | PASS |
| 0.020 | powered | impulse (N·s) | 1877.20210405584 | 1877.20210405584 | 0 | 0 | 1.81898940354586e-12 | 2.91038304567337e-11 | PASS |
| 0.020 | burnout | altitude (m) | 340.009804271153 | 340.009804438221 | -1.67068037626450e-7 | 1.67068037626450e-7 | 6.82121026329696e-13 | — | REPORTING_ONLY |
| 0.020 | burnout | vertical speed (m/s) | 187.968708803942 | 187.968708851748 | -4.78063668651885e-8 | 4.78063668651885e-8 | 3.97903932025656e-13 | — | REPORTING_ONLY |
| 0.020 | burnout | total mass (kg) | 16.5965357819681 | 16.5965357819681 | 0 | 0 | 2.84217094304040e-14 | 4.54747350886464e-13 | PASS |
| 0.020 | burnout | time (s) | 2.96700000000000 | 2.96700000000000 | 0 | 0 | 0 | 0 | PASS |
| 0.020 | coast | altitude (m) | 1287.07665833205 | 1287.07665876410 | -4.32052047472098e-7 | 4.32052047472098e-7 | 4.54747350886464e-12 | — | REPORTING_ONLY |
| 0.020 | coast | vertical speed (m/s) | 88.8144497824114 | 88.8144498137301 | -3.13187058509357e-8 | 3.13187058509357e-8 | 3.97903932025656e-13 | — | REPORTING_ONLY |
| 0.020 | apogee | time (s) | 18.5967881423684 | 18.5967881433983 | -1.02992814277059e-9 | 1.02992814277059e-9 | 1.59872115546023e-11 | — | REPORTING_ONLY |
| 0.020 | apogee | altitude (m) | 1658.56590197876 | 1658.56639276751 | -0.000490788750084903 | 0.000490788750084903 | 1.04591890703887e-11 | — | REPORTING_ONLY |
| 0.020 | ground | time (s) | 38.1207353541353 | 38.1207365832469 | -0.00000122911161781758 | 0.00000122911161781758 | 1.68256519827992e-11 | — | REPORTING_ONLY |
| 0.020 | ground | vertical speed (m/s) | -150.940596910078 | -150.940619622386 | 0.0000227123084357572 | 0.0000227123084357572 | 7.10542735760100e-13 | — | REPORTING_ONLY |
| 0.010 | powered | altitude (m) | 84.0999966452812 | 84.0999966521184 | -6.83721168570628e-9 | 6.83721168570628e-9 | 1.70530256582424e-13 | — | REPORTING_ONLY |
| 0.010 | powered | vertical speed (m/s) | 109.716717791155 | 109.716717794222 | -3.06732772514806e-9 | 3.06732772514806e-9 | 1.70530256582424e-13 | — | REPORTING_ONLY |
| 0.010 | powered | total mass (kg) | 17.5218025807727 | 17.5218025807727 | 0 | 0 | 2.84217094304040e-14 | 4.54747350886464e-13 | PASS |
| 0.010 | powered | thrust (N) | 1445.39967400000 | 1445.39967400000 | 0 | 0 | 1.81898940354586e-12 | 2.91038304567337e-11 | PASS |
| 0.010 | powered | impulse (N·s) | 1877.20210405584 | 1877.20210405584 | 0 | 0 | 1.81898940354586e-12 | 2.91038304567337e-11 | PASS |
| 0.010 | burnout | altitude (m) | 340.009804426298 | 340.009804438221 | -1.19228502626356e-8 | 1.19228502626356e-8 | 6.82121026329696e-13 | — | REPORTING_ONLY |
| 0.010 | burnout | vertical speed (m/s) | 187.968708848403 | 187.968708851748 | -3.34523519995855e-9 | 3.34523519995855e-9 | 3.97903932025656e-13 | — | REPORTING_ONLY |
| 0.010 | burnout | total mass (kg) | 16.5965357819681 | 16.5965357819681 | 0 | 0 | 2.84217094304040e-14 | 4.54747350886464e-13 | PASS |
| 0.010 | burnout | time (s) | 2.96700000000000 | 2.96700000000000 | 0 | 0 | 0 | 0 | PASS |
| 0.010 | coast | altitude (m) | 1287.07665873364 | 1287.07665876410 | -3.04555669572437e-8 | 3.04555669572437e-8 | 4.54747350886464e-12 | — | REPORTING_ONLY |
| 0.010 | coast | vertical speed (m/s) | 88.8144498115367 | 88.8144498137303 | -2.19367279896687e-9 | 2.19367279896687e-9 | 3.83693077310454e-13 | — | REPORTING_ONLY |
| 0.010 | apogee | time (s) | 18.5967881432414 | 18.5967881433983 | -1.56873625201115e-10 | 1.56873625201115e-10 | 1.59872115546023e-11 | — | REPORTING_ONLY |
| 0.010 | apogee | altitude (m) | 1658.56638255199 | 1658.56639276751 | -0.0000102155133845372 | 0.0000102155133845372 | 1.04591890703887e-11 | — | REPORTING_ONLY |
| 0.010 | ground | time (s) | 38.1207362505233 | 38.1207365832469 | -3.32723644191901e-7 | 3.32723644191901e-7 | 1.68256519827992e-11 | — | REPORTING_ONLY |
| 0.010 | ground | vertical speed (m/s) | -150.940613450056 | -150.940619622386 | 0.00000617233033040065 | 0.00000617233033040065 | 7.10542735760100e-13 | — | REPORTING_ONLY |

At apogee, applying the same linear chord to the independent reference endpoints gives the following decomposition. The remainder includes the native endpoint state error and event arithmetic.

| Step (s) | Chord height contribution (m) | Remaining height difference (m) | Chord time contribution (s) | Remaining time difference (s) |
|---:|---:|---:|---:|---:|
| 0.020 | −4.901124237e−4 | −6.763264082e−7 | +1.778428071e−9 | −2.808356214e−9 |
| 0.010 | −1.016793999e−5 | −4.757339411e−8 | +3.536371196e−11 | −1.922373372e−10 |

The ground time and speed residuals are combined differences. The interpolation-only contribution is **unavailable** because the rejected native endpoint is absent. The maximum same-time position and speed differences at the selected powered, burnout and coast checkpoints were 4.32e−7 m and 4.78e−8 m/s. The coarser apogee-height difference was 4.91e−4 m and is predominantly the chord effect. The two native grids provide a step comparison for L.

**Disposition** 10 deterministic comparisons `PASS`, 20 trajectory or event comparisons `REPORTING_ONLY`, zero failed or unresolved classification rows. The ground decomposition remains an explicit unavailable quantity. The comparison establishes measured residuals for the single frozen fixture. It supplies no absolute full-flight accuracy budget, measured-flight validation, recovery reference flight or OpenRocket equivalence.

Executable OpenRocket comparison is deferred by the owner. Future replay requires an exact build and hash, vehicle/configuration and source data hashes, matching initial state and mass properties, comparable forces and environment, explicit event definitions and a lossless export. Material mismatches belong in a cross-model benchmark.

NAT-039 verification completed for the bounded scope. Owner acceptance pending.
