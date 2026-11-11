# NAT-038 model applicability and sensitivity

**Recommendation** `NAT038_ACCEPTANCE_RECOMMENDED`\
**Owner acceptance** Pending.

## Campaign and provenance

The eight cases use the accepted N37-03C/r1 synthetic geometries, materials, motor installation and ENG mass/CG assumptions. L and M are direct runs with source alignment and 0.020 s nominal steps. N uses the 0.015625 s legacy fixed step, 0.80 m² recovery area and 6.0 s deployment delay. All cases use the same corresponding frozen launch direction, WORLD state, clock, ground rule and atmosphere/wind policies. M runs direct. Each case gets a fresh control and the existing test-only 20,000-segment watchdog. See the [frozen inventory](nat-037-n37-03c-parametric-vehicle-integration.md#frozen-fixture-inventory).

| Case | Configuration ID | Input selection |
|---|---|---|
| L baseline | `nat-038:L_BASE:r1` | Body density 4000 kg/m³ |
| M baseline | `nat-038:M_BASE:r1` | Ballast 32 kg at 1.300 m and WORLD ENU wind (4, −2, 0) m/s |
| N baseline | `nat-038:N_BASE:r1` | Launch atmosphere input 800 m geopotential |
| L density | `nat-038:L_DENSITY:r1` | Body density 4500 kg/m³ |
| M ballast | `nat-038:M_BALLAST:r1` | Existing separately owned ballast 36 kg at 1.300 m |
| M wind | `nat-038:M_WIND:r1` | WORLD ENU wind (8, −2, 0) m/s |
| N altitude | `nat-038:N_ALTITUDE:r1` | Launch atmosphere input 1300 m geopotential |
| L density refinement | `nat-038:L_DENSITY_FINE:r1` | Body density 4500 kg/m³ and source-aligned step 0.010 s |

Every perturbation is TEST_ONLY. Full source selection, configuration, mass ownership, matched-state residuals and flight snapshots are recorded in the focused JUnit XML (yerel kanıt, bu depoda yok).

## Eight-case result matrix

All eight cases reached `GROUND` with an empty failure field. Peak altitude is relative to the launch plane, and apogee is the separate NAT-018 interpolated estimate. Velocity vectors are WORLD ENU. Speed and Mach maxima are over accepted samples.

| Case | Accepted segments | Sampled peak m / s | NAT-018 apogee m / s | Estimated contact s | Estimated contact velocity m/s | Peak speed m/s / Mach |
|---|---:|---|---|---:|---|---|
| `L_BASE` | 1911 | 1658.565922 / 18.587000 | 1658.565902 / 18.596788 | 38.120735 | (0.000000, 0.000000, -150.940597) | 189.032625 / 0.565285888 |
| `M_BASE` | 1390 | 800.909047 / 14.350000 | 800.908815 / 14.343838 | 27.613279 | (1.546089, 72.863205, -114.884197) | 144.995939 / 0.434434048 |
| `N_BASE` | 3049 | 1251.897819 / 17.625000 | 1251.897673 / 17.620486 | 47.643559 | (0.000000, 0.000000, -42.454721) | 137.665749 / 0.409973928 |
| `L_DENSITY` | 1843 | 1539.547568 / 18.087000 | 1539.547400 / 18.089208 | 36.758357 | (0.000000, 0.000000, -148.676539) | 177.707857 / 0.531307593 |
| `M_BALLAST` | 1301 | 693.389274 / 13.570000 | 693.389192 / 13.569069 | 25.834233 | (1.313576, 73.601699, -108.600027) | 133.062287 / 0.399012879 |
| `M_WIND` | 1390 | 800.723730 / 14.350000 | 800.723573 / 14.341985 | 27.610226 | (3.094864, 72.831582, -114.867195) | 144.980740 / 0.434800598 |
| `N_ALTITUDE` | 3025 | 1258.076608 / 17.671875 | 1258.076511 / 17.673476 | 47.270813 | (0.000000, 0.000000, -43.523964) | 137.847822 / 0.412915715 |
| `L_DENSITY_FINE` | 3682 | 1539.547569 / 18.087000 | 1539.547508 / 18.089208 | 36.758359 | (0.000000, 0.000000, -148.676563) | 177.707858 / 0.531307593 |

| Case | Burnout s | WORLD position m | WORLD velocity m/s | Event fraction | Accepted interval min to max s, rounded | Source endpoints / shortened |
|---|---:|---|---|---:|---|---|
| `L_BASE` | 2.967 | (0.000000, 0.000000, 340.009804) | (0.000000, 0.000000, 187.968709) | 1.000000 | 0.001 to 0.02 | 16 / 15 |
| `M_BASE` | 6.75 | (101.296660, 338.870549, 555.388619) | (0.461464, 105.952633, 79.975513) | 1.000000 | 5.329070518200751e-15 to 0.02 | 23 / 23 |
| `N_BASE` | 4.613 | (-40.000000, 25.000000, 415.839781) | (0.000000, 0.000000, 136.003449) | 0.232000 | 0.015625 to 0.015625 | 0 / 0 |
| `L_DENSITY` | 2.967 | (0.000000, 0.000000, 320.788586) | (0.000000, 0.000000, 176.749165) | 1.000000 | 0.001 to 0.02 | 16 / 15 |
| `M_BALLAST` | 6.75 | (101.105261, 304.073973, 507.403650) | (0.392301, 99.528333, 70.650643) | 1.000000 | 5.329070518200751e-15 to 0.02 | 23 / 23 |
| `M_WIND` | 6.75 | (102.602332, 338.816427, 555.334933) | (0.924820, 105.937256, 79.961031) | 1.000000 | 5.329070518200751e-15 to 0.02 | 23 / 23 |
| `N_ALTITUDE` | 4.613 | (-40.000000, 25.000000, 416.116699) | (0.000000, 0.000000, 136.206157) | 0.232000 | 0.015625 to 0.015625 | 0 / 0 |
| `L_DENSITY_FINE` | 2.967 | (0.000000, 0.000000, 320.788586) | (0.000000, 0.000000, 176.749165) | 1.000000 | 0.001 to 0.01 | 16 / 16 |

L and M burnout states are accepted source endpoints. N's burnout estimate is interpolated, with the first later accepted state at 4.625 s. Source endpoints exclude the initial point. Small rounding offsets in the actual interval are retained by the XML report. The N runs use fixed stepping and have no source alignment counts.

| Case | Initial total kg / axial CG m | First sample at or after burnout total kg / axial CG m |
|---|---|---|
| `L_BASE` | 18.501535782 / 1.080501725 | 16.596535782 / 1.015167446 |
| `M_BASE` | 45.666156571 / 1.419789030 | 41.563156571 / 1.370182535 |
| `N_BASE` | 90.259371479 / 1.671379494 | 82.591371479 / 1.608193539 |
| `L_DENSITY` | 19.685287894 / 1.082876909 | 17.780287894 / 1.022146847 |
| `M_BALLAST` | 49.666156571 / 1.410141492 | 45.563156571 / 1.364021194 |
| `M_WIND` | 45.666156571 / 1.419789030 | 41.563156571 / 1.370182535 |
| `N_ALTITUDE` | 90.259371479 / 1.671379494 | 82.591371479 / 1.608193539 |
| `L_DENSITY_FINE` | 19.685287894 / 1.082876909 | 17.780287894 / 1.022146847 |

Effective structure mass and axial CG were approximately 14.596535782 kg / 0.928224549 m for baseline L and 15.780287894 kg / 0.942610509 m for density L. M and N retain their effective structures of 6.656156571 kg / 1.466459666 m and 12.614371479 kg / 1.872249830 m. Installed motor CG stayed at 1.6497, 1.9223 and 2.35195 m for L, M and N. Their loaded to depleted motor masses were 3.905 → 2.000 kg, 7.010 → 2.907 kg and 12.645 → 4.977 kg.

N baseline detected the first eligible sampled drop at 17.671875 s and activated at 23.671875 s. N altitude detected at 17.718750 s and activated at 23.718750 s. The sampled peak was at 17.625000 s and 17.671875 s, respectively. Both finished ACTIVE. An independent scan checked the strict drop greater than 0.01 m and scheduling from the detection sample.

## Matched-state analytical checks

| Probe | Independent expectation or invariant | Signed residual or observation |
|---|---|---|
| L body density | Annular volume from the 0.003 m wall, radius sum 0.157 m and 1.600 m length. Mass increment 1.1837521118726342 kg. At the 1.120 m centroid, first-moment increment 1.3258023652973505 kg·m. | Mass −2.22e−16 kg and moment −1.33e−15 kg·m. Geometry, thrust and matched-state drag equal. Vertical acceleration response residual −8.88e−16 m/s². |
| M ballast | 4 kg and 5.2 kg·m increments. | Mass exact, moment +9.77e−15 kg·m. Largest acceleration component residual 1.33e−15 m/s². Ownership and station retained. |
| M wind | Relative-flow delta (−4, 0, 0) m/s. Independent squared speed sums of 468 and 516 m²/s². | Flow delta exact. Pressure 274.46932452609883 → 302.62002447749353 Pa. Residuals +5.68e−14 and 0 Pa. Mass and thrust equal. |
| N atmosphere | At WORLD Z = 45 m, query 825 → 1325 geopotential m. | Offset exact. Density 1.130875206 → 1.076588204 kg/m³, temperature 282.7875 → 279.5375 K and pressure 91798.7951 → 86387.6747 Pa. Launch WORLD state, relative flow, gravity, mass and motor state equal. |
| M nose CG | 0.310 → 0.330 m. Expected first-moment change approximately 0.004897110694 kg·m using unchanged nose mass. | Structure residual −1.55e−16 kg·m and vehicle residual −1.93e−15 kg·m. Total mass and matched-state dynamics equal. |
| L body roughness | 0 → 0.000100 m at 150 m/s vertical, with unchanged nose and fin terms. | SMOOTH → ROUGHNESS_LIMITED. Body coefficient 0.0025899722 → 0.0043624335 and axial drag −90.436754 → −108.644742 N. Rough-branch coefficient residual zero. Axial force-change residual +3.55e−15 N. |
| L synthetic mass law | Decimal trapezoid integral of ENG for impulse-proportional baseline. Explicit linear mass 2.9525 kg halfway through the source duration. | At 1.4835 s, baseline 2.818710177158778 kg and alternative 2.9524999999999997 kg. Alternative scalar residual −4.44e−16 kg, total-mass delta residual −2.66e−15 kg, moment residual −1.69e−15 kg·m. Initial and depleted masses agree to the fixture-local arithmetic allowance. |
| L synthetic CG law | Installed shift 0, 0.010, 0.020 and 0.020 m at 0, 1.4835, 2.967 and 3.967 s. Vehicle first-moment change is the unchanged motor mass times the shift. | Installed shift residual magnitudes ≤2.23e−16 m and moment residual magnitudes ≤3.56e−15 kg·m. Total mass and matched-state dynamics equal. |

Exact checks cover preserved inputs and other exact invariants. The test records signed and relative residuals, expected-value ULPs and numerical bounds. For differences of already rounded aggregates, the bound uses the operand ULPs to account for cancellation. The established fixture-local 128 ULP direct motor-scalar allowance applies to the designated mass snapshots. These checks introduce no trajectory accuracy criterion. The current fixed-direction equations give unchanged forces and derivatives for axial-CG-only probes.

## Input response and step-size observations

Deltas are variant minus reference. Within-variant step delta is fine minus coarse.

| Metric | L density input at 0.020 s | L density input at 0.010 s | L density variant step delta |
|---|---:|---:|---:|
| Sampled peak altitude, m | −119.018354056 | −119.018823712 | +5.29148e−7 |
| NAT-018 apogee time, s | −0.507580008 | −0.507580008 | +1.65099e−9 |
| Estimated contact time, s | −1.362378114 | −1.362377606 | +1.40516e−6 |
| Peak accepted speed, m/s | −11.324767606 | −11.324767613 | +3.48415e−8 |
| Peak accepted Mach | −0.03397829416 | −0.03397829419 | +1.04957e−10 |
| Estimated contact vertical velocity, m/s | +2.264057844 | +2.264050057 | −2.43265e−5 |

The fine reference is the saved B1 `L/DIRECT/h=0.01` trace with ID `n37-03c:L:r1`. Its fixture source, baseline density, direct configuration, initial state, source identity and alignment agree with the frozen reference. Its sampled peak of 1658.5663924998644 m and speed of 189.03262512293827 m/s come from the sample trace, and the apogee and ground estimates come from its event record. The B1 trace has no recorded per-sample force, atmosphere, motor mass or vehicle mass/CG fields. Fine-step input deltas for those fields are unavailable. [B1 fixture definition](nat-037-n37-04b1-integrated-flight-semantics.md#frozen-fixture-identity), Saved trace (yerel kanıt, bu depoda yok).

M ballast reduced sampled peak by 107.520 m and estimated flight time by 1.77905 s in this pair. M wind changed estimated contact east position by +22.7023 m and east velocity by +1.54878 m/s. N altitude raised sampled peak by 6.17879 m, delayed sampled detection and activation by 0.046875 s, and changed estimated contact time by −0.372746 s. These are observed responses for the selected inputs and grids. Two steps provide no global convergence estimate.

The eight accepted histories are within the observed subsonic envelope. Basic Drag retains Mach 0–1, static CP/CNa retains the separate domain below 0.8, and the atmosphere queries retain the existing geopotential domain. The accepted histories provide sample maxima, while the evaluator enforces its domain at evaluation points. Unsupported, numerical, cancelled and preparation outcomes retain their existing typed classifications. The final sensitivity matrix had no such outcome.

## Verification and preserved first failure

The first full test run completed 792 tests with one failure. The midpoint assertion compared a decimal literal of 0.2653 m at ignition against the binary result of the Rev1 header conversion. The header is 530.6 mm, and the exact Java assertion now uses `(530.6 / 1000.0) / 2.0` as its independent source-derived expectation. The original mismatch was +5.551115123125783e−17 m. The literal synthetic CG sample remains 0.2653 m. The correction retains exact comparison and all frozen inputs. All eight flight results were identical between the initial and final XML outputs. Two later attempts encountered a sandbox Gradle-daemon connection failure before test execution. The escalated offline verification completed successfully. First failed XML (yerel kanıt, bu depoda yok), Initial build log (yerel kanıt, bu depoda yok), Sandbox log (yerel kanıt, bu depoda yok).

| Gate | Result |
|---|---|
| Focused Java 17 / Gradle 8.12.1 | 16 passed, 0 failed, 0 errors, 0 skipped. Eight flight cases and eight matched-state tests. |
| Full Java 17 / Gradle | 792 passed across 50 classes, 0 failed, 0 errors, 0 skipped. Assembly and production dependency tasks successful. |
| Packaged consumers | Six passed against the verified JAR. `jdeps --print-module-deps` reported `java.base`. |
| J-011 | Reused the accepted actual B1 evidence of 442,261 distinct comparisons, with 442,225 exact and 36 within the unchanged tolerances. The protected file hash comparison covers production sources and resources, Python reference and fixtures, and Java/Python parity runners. The main JAR matches B1 byte for byte. |

Focused Gradle log (yerel kanıt, bu depoda yok), Full Gradle log (yerel kanıt, bu depoda yok), JUnit and consumer artifacts (yerel kanıt, bu depoda yok), Accepted J-011 log (yerel kanıt, bu depoda yok).

## SHA-256 and Git integrity

- Branch `development/nat-035-apogee-recovery`, HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae`, empty index. Final inventory has 15 tracked modifications and 45 untracked files, of which two are the authorized NAT-038 additions. All 515 pre-NAT-038 tracked and nonignored untracked files match the saved manifest. Pre-task manifest (yerel kanıt, bu depoda yok).
- Manifest SHA-256 `0f04417b60163a09ab2907ad42292539790127ebb2098e07c62394aceebff1d3`.
- NAT-038 test SHA-256 `f754bd101e43ae58e973f17b5f62817bfc16577b40256ef9bc574fa522603099`.
- Main JAR SHA-256 `5de0b08bf34e7211bb94f87dba1e298c59e3d1d5e9145f973c9cd17388323baf`.
- Fine B1 XML SHA-256 `2639304b9cd7ad71c040b6144ddd842af65e41f01b7c57f928eea01c5f716deb`.

NAT-038 SENSITIVITY IMPLEMENTATION COMPLETE — OWNER ACCEPTANCE PENDING
