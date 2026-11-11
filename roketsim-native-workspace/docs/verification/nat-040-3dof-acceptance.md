# NAT-040 fixed-direction 3DOF core engineering acceptance

**Recommendation** `NAT040_ENGINEERING_ACCEPTANCE_RECOMMENDED` for the supported working-tree candidate. Owner acceptance is pending. Publication remains pending an immutable snapshot.

`OPENROCKET_COMPARISON_DEFERRED_TO_APPLICATION_INTEGRATION`

## Requirement-to-evidence matrix

| Requirement | Accepted or fresh evidence | Supported claim | Limitation or unresolved issue |
|---|---|---|---|
| Vehicle geometry and mass | NAT-012/024, [N37-03C independent component oracles](nat-037-n37-03c-parametric-vehicle-integration.md#independent-oracles-and-comparison-policy) | One conical nose, cylindrical airframe, trapezoidal fin set, motor mount and centering ring pair, with explicit supported auxiliary hardware. Geometry-derived volume, mass and axial CG. | General component trees, other topologies and as-built dimensions are outside scope. |
| Source-backed propulsion | NAT-011, [N37-02 runtime and source evidence](nat-037-n37-02-source-backed-motor-runtime.md), B1 source anchors and NAT-039 L comparisons | Source identity, linear thrust, integrated impulse, explicit depletion model and fixed or supported explicit axial CG selection. | Source evidence supports configured inputs and models. Physical assembly equivalence, motor certification and independently measured internal mass evolution remain outside the evidence. |
| Installation | [N37-02.1 correction evidence](nat-037-n37-02-1-motor-runtime-corrections.md), N37-03C installation checks | Resolver checks nominal geometry, engagement, clearance and datum consistency. | Physical fit and flange/nozzle envelope require independent data. |
| Mass/CG override and ownership | NAT-023/024, [N37-03B](nat-037-n37-03b-vehicle-configuration-provenance.md) and N37-03C | Replacement semantics, structural versus installed/auxiliary ownership, evidence validation, and configuration identity with partial histories. | Calculated mass depends on geometry and material assumptions. Measured-mode claims depend on supplied provenance. Semantic overlap in undeclared inputs and inferred inertia are outside the checks. |
| Atmosphere and aerodynamics | NAT-009/012, N37-03C boundaries, NAT-038 matched-state atmosphere and finish, NAT-039 independent force anchors | Lower dry US Standard Atmosphere, accepted dry-air properties and Basic Drag, supported constant wind and reference mapping. | Atmosphere domain is −5000 to 84852 geopotential m. Basic Drag 0 ≤ Mach ≤ 1. Static CP/CNa is a separate diagnostic for 0 ≤ Mach < 0.8. No generalized transonic, supersonic or angular aerodynamics. |
| Translational force evaluation | NAT-013–015, [NAT-038 matched-state response](nat-038-model-applicability-sensitivity.md#matched-state-analytical-checks), NAT-039 | WORLD ENU fixed-direction thrust, gravity and drag coupled to current mass, including signed wind-relative flow. | Fixed direction and translational 3DOF. Axial CG alone affects mass-property reporting rather than attitude. No 6DOF. |
| RK4 integration | NAT-016–020 analytical seams, fresh J-011 and NAT-039 matched-time residuals | Accepted fixed-step RK4 and state/event recording behavior. | The L reference measures checkpoint errors at two selected grids. No global convergence order or absolute full-flight error guarantee. |
| Source-knot alignment | [NAT-026](nat-026-event-aware-step-boundaries.md), [NAT-026.1](nat-026-1-endpoint-coincidence-fix.md), B1 and N37-02.1 | Exact eligible source endpoints on verified representable grids and typed handling of nonadvancing or unrepresentable boundaries. | Timestamp and duration representability constrain usable grids. The verified offsets and grids define the evidence. |
| Launch guide | [NAT-031–033](nat-031-033-launch-guide-dynamics.md), [B1 guide handoff](nat-037-n37-04b1-integrated-flight-semantics.md#analytical-and-event-semantics-matrix) | Ideal two-button guide constraint, defined threshold, reintegrated handoff and consumption of remaining step. | Free flight after forward-button exit approximates remaining clearance. Real usable rail length and physical release accuracy remain unverified. |
| Apogee reporting | NAT-018, B1 analytical chord and NAT-039 reference/chord decomposition | Downward vertical velocity crossing yields a linear reporting estimate. L separates chord contribution from the remaining numerical error. | The event estimate differs from the continuous apex. Sampled recovery detection uses a distinct event rule. |
| Recovery | [NAT-034](nat-034-constant-recovery-drag.md), [NAT-035](nat-035-apogee-recovery.md), B1 and NAT-038 N observations | Single fixed-effective-area parachute, sampled altitude-drop trigger, configured delay, typed positive-thrust rejection and defined force regime. | Trigger uses the first eligible accepted drop strictly greater than 0.01 m. Deployment and steady drag use configured assumptions. No canopy certification or independent recovery trajectory in NAT-039. |
| Ground termination | [NAT-036](nat-036-ground-termination-progress.md), B1 and NAT-039 | Descending reference-point contact on the launch plane, linear event estimate and endpoint acceptance semantics. | No impact dynamics. The rejected L endpoint is absent, leaving the interpolation-only ground decomposition unavailable. |
| Cancellation and typed failures | NAT-036, N37-02.1 and B1 | Cooperative cancellation with accepted-prefix preservation and typed unsupported, model and numerical outcomes. | Operational faults and unsupported configurations retain their reported classification. |
| Parametric source-backed execution | N37-03C, [B1](nat-037-n37-04b1-integrated-flight-semantics.md) and NAT-038 | Three synthetic source-backed families exercise direct, guided and recovery execution under the frozen contracts. | No real-flight performance or arbitrary vehicle/grid claim. |
| Model sensitivity | [NAT-038](nat-038-model-applicability-sensitivity.md) | Eight controlled runs and eight matched-state checks isolate selected input and numerical-step responses. | Effects apply to the specified synthetic inputs and grids. Fine comparisons use fields available in the saved trace. |
| Independent numerical flight | [NAT-039](nat-039-independent-l-flight.md) | Qualified DOP853 reference, three-level refinement, 10 deterministic checkpoint passes and 20 quantified reporting-only state/event residuals for L. | Empirical reference uncertainty. No approved numerical accuracy limits for the trajectory, and no OpenRocket equivalence or measured-flight validation. |
| Public API and Java packaging | Preserved schema and legacy tests, fresh 792-test suite, Gradle assembly and six consumers | Java 17 public API and module artifacts usable by consumers. Runtime dependencies empty and `jdeps` reports `java.base`. | Publication identity still requires an immutable source snapshot. |

## Fresh final gates

| Gate | Result |
|---|---|
| Gradle 8.12.1 under Temurin 17.0.20.1 | Fresh `test`, `assemble`, `verifyProductionDependencies` completed with exit 0. 792 tests passed across 50 suites, zero failures, errors or skips. Javadoc emitted warnings and produced its JAR. |
| Packaged consumers | Ground, Launch Guide, Prepared Source Vehicle, Recovery Drag, Recovery and Source Motor all exited 0. |
| Dependency inspection | `jdeps --print-module-deps` exited 0 and reported `java.base`. |
| Fresh J-011 | Python 3.12.4 / NumPy 2.5.2 in the existing parity environment. Exit 0. 442,261 distinct comparisons, 442,225 exact, 36 within existing tolerances. Primary 147,521 and fine 294,740. The primary repeat remains separate. Maximum absolute residual 8.881784197001252e−16. |
| Independent reference | 42 analytical qualification checks and three refinement levels completed separately from the Java suite. No unresolved classification row, and the missing ground decomposition remains disclosed. |

Gradle log (yerel kanıt, bu depoda yok), Java XML and packaged output (yerel kanıt, bu depoda yok), Consumer and dependency logs (yerel kanıt, bu depoda yok), Fresh J-011 log (yerel kanıt, bu depoda yok), Fresh J-011 statistics (yerel kanıt, bu depoda yok).

| Artifact | SHA-256 |
|---|---|
| Main JAR | `5de0b08bf34e7211bb94f87dba1e298c59e3d1d5e9145f973c9cd17388323baf` |
| Sources JAR | `14f7e18941dfca21e8dbd667da1648565978625a26d0bb5049069b99e9ac16a3` |
| Javadoc JAR | `f3ee221ce166b458585167fdf9817cc7f1169dc4de74ef61acc44669f832ce88` |

## Candidate identity and owner decisions

The provisional candidate is the working-tree inventory in the original release-candidate manifest retained in the engineering workspace (SHA-256 recorded in the [public candidate manifest](publication-3dof-core-clean.json)). Branch `development/nat-035-apogee-recovery`, HEAD `4c31dba3be4a133529dcad07980dbe9da8387bae`. HEAD provides ancestry. The full tracked and nonignored untracked content, including the substantial previously accepted working-tree changes, identifies the candidate. The baseline has 472 tracked paths, 45 untracked paths and an empty index. The final inventory adds six authorized test/reference and verification artifacts. All 517 pre-task content hashes matched. The final external inventory includes the manifest's own SHA-256. Before inventory (yerel kanıt, bu depoda yok), Final inventory (yerel kanıt, bu depoda yok).

For publication, the owner still needs to approve the bounded engineering claim, decide the immutable Git snapshot and commit structure, verify that snapshot against the candidate manifest, and separately authorize tags, artifact publication and remote synchronization. OpenRocket replay is deferred to application integration under the NAT-039A comparability requirements.

NAT-039 / NAT-040 COMBINED VERIFICATION COMPLETE — OWNER ACCEPTANCE PENDING
