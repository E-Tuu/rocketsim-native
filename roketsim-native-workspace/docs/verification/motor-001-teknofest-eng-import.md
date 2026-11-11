# MOTOR-001 — TEKNOFEST ENG import and source-aware schema

Date: 2026-11-01. Acceptance is for source catalog/schema/import only, not flight or inertia readiness.

## Git starting state and scope

The accepted NAT-026 base was verified as clean HEAD **03450b2775700fcf726ea144ccd97bfc746e3afa**, branch `development/nat-026-event-aware-step-boundaries`. Local status, branch, HEAD, log and origin (`E-Tuu/rocketsim-native`) were inspected before creating `development/motor-001-teknofest-eng-import` directly from that commit. NAT-025C and J-011 remain ancestors. This is one integrated schema/import acceptance commit, not separately committed phases.

Only five new catalog production files, one package export, three byte-identical ENG resources, their narrowly scoped `.gitattributes`, one focused test and this document are included. Actual local `MotorDefinition`, `MotorCatalogs`, `MotorInstallationResolver`, `MotorPropertyEvaluator`, `MotorInertiaEvaluator`, `MotorInertiaModel`, `MotorType` and certification contracts were inspected. They are unchanged. There is no alteration to NAT-023–026 mass, configuration, clock, inertia, scheduling or simulation authorities.

## Original source and complete ZIP manifest

Owner-supplied local file: `<local-source-archive>`.

**2,041,371 bytes; SHA-256 `467bd83b1b72c720305cdd22ec9b2f05aa91120b455988415a202915c9beb5de`.** The original archive/documents were not modified. Inventory and checksums were independently read from the actual ZIP, not downloaded substitutes. Drawing/image/workbook observations remain the accepted MOTOR-DATA-2026-A evidence, with filenames and original source units retained. All 22 members:

| ZIP member | Bytes | SHA-256 |
|---|---:|---|
| M1850 Motor Boyutları.pdf | 65943 | `aae43ac7f8a960d51b894a31db39963fef28519f41fb0d68bdc68b88e13f8804` |
| M1850 Motorunun Nozul Bölgesi Ölçüleri.pdf | 109676 | `ec63039efeb72ea5812edd4836db6d9b7998d576808fcb34f9c3faa7bd11a6d9` |
| M1850-1.jpg | 56154 | `00af629c8bb2c98a281d9bdd5ddeb380e706457ff3a54292d4209508f24b7a91` |
| M1850-2.jpg | 70900 | `36b6bb59d11c95cabe6964a7a7733e7c9261196b8f1a34112e0a38e097dcbf74` |
| M1850W P DWG.pdf | 171603 | `a62e2f700cefd14406516c8d2aacda74593cb5dfaf710ab41aa0ad7d9d1caf49` |
| N3300 Motor Boyutları.pdf | 36264 | `42ef6da49d7d99d631f3a80e549062238afea1b8be5c3c3712654a0993bf485c` |
| N3300 Motorunun Nozul Bölgesi Ölçüleri.pdf | 118630 | `e4bec49adbe99dccca160cd05c0be04c4e6317471a6231ae9a4640b9a9e354fa` |
| N3300-1.jpg | 64968 | `af598b85574efe0ec9756e45a2c996634ac818c158a4554b91666bf30820e4cd` |
| N3300-2.jpg | 68431 | `0c7df0ccc2973df7d7ff9537e0671598beea44ea9283189c5a5a1be5800a7646` |
| N3300R P DWG.pdf | 175990 | `40742fa22bffaaf1bfd9683552d7cedd26c87e5bd9d3e8b1412712e1ab7cfa50` |
| OpenRocket_Teknofest_Motorfile26.png | 64908 | `8b565872a799ee063975a2b8d1a018982ca289c54df356dd7b2ba88c0e7fdbf0` |
| RMS 75 1280 TO 10240.pdf | 501225 | `37e1c321a12bc68e20536bfa5b15c9e0b56a9f1ce35779fed0f3068d50e5305c` |
| RMS 98 2560 TO 20480.pdf | 507104 | `d16c60ea2cc70dd09d251fb19bbda7f861796180cb984873b9db48beb83c8291` |
| Teknofest26_L1256_Rev1.eng | 333 | `756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd` |
| Teknofest26_M1850_Rev1.eng | 287 | `111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc` |
| Teknofest26_N3300_Rev1.eng | 424 | `10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8` |
| Guncel_motor_verileri.xlsx | 37008 | `121afe2f5dd97c8a737ba3c952aba26730890171d14fdf0bfeded6de861a0372` |
| L1256 Motor Boyutları.pdf | 35023 | `925d0007b49d5b30dddef2f0226efe9664d775b7326eb2073d93e7c7fe7f5bf6` |
| L1256 Motorunun Nozul Bölgesi Ölçüleri.pdf | 133970 | `e9fcddea0099f290e93604ad8d9f793f16e8a2ae398bfe3ff112cb56658261a8` |
| L1256-1.jpg | 58250 | `ad90645c4183b787c99d6d013b8b25807195a6c325897ca451e500ca1ef5f8c5` |
| L1256-2.jpg | 70766 | `2921faf1230d059c52fc2c07a722137c298eb78521865133f981d9f76101b954` |
| L1256WS P DWG.pdf | 78802 | `cd57166ae0108f7f8fda92a30ce3daaec1e57a919d4110eccf143331debce7a5` |

Only the three ENG files are copied into `src/main/resources/org/roketsim/core/propulsion/catalog/teknofest2026/`. Their names, bytes, header tokens, whitespace, newline endings and checksums are unchanged. The local `.gitattributes` sets only these resource `*.eng` files to `-text`, preventing Git newline conversion. ZIP versus worktree versus packaged JAR byte equality was executed and passed. Source SHA is rechecked by the production profile loader; no original ZIP is required at runtime.

Git's whitespace check flags original L1256 CRLF bytes as trailing whitespace when text conversion is disabled. Those source bytes are intentionally retained, not cleaned to satisfy a formatting check. Code/documentation whitespace checks pass; ZIP/index/JAR byte identity is the source-preservation gate.

## Final public schema and APIs

New package: `org.roketsim.core.propulsion.catalog`, exported by the existing module. It is a separate interchange/catalog adapter; ADR-008 physics APIs still reject numeric strings. It uses only `java.base` and the existing immutable `ThrustSample` numeric value type.

| Production type | Public contract |
|---|---|
| `RaspEngReader` | Stateless `EngSourceFile read(String fileName, String revision, String sourceReference, byte[] bytes)` |
| `EngSourceFile` | Immutable final source snapshot, constructed by the reader: `fileName()`, `revision()`, `sourceReference()`, `sha256()`, defensive `sourceBytes()`, immutable `records()` and `commentLines()`. Content-based equality/hash include the original bytes. |
| `EngMotorRecord` | Record `(Header header, List<ThrustSample> listedSamples, int headerLine)`; immutable `normalizedSamples()`, independent `totalImpulseNS()`, `curveEndTimeS()` |
| `EngMotorRecord.Header` | Record `(List<String> tokens)` retains exactly seven original tokens; typed source-unit accessors for designation, nominal mm dimensions (and explicit metres conversion), raw delay, kg masses and unverified provider/manufacturer token. |
| `EngMotorRecord.EjectionMetadata` | Record `(String rawToken, EjectionInterpretation interpretation, List<Double> numericSourceOptionsS, boolean pluggedOption, String interpretationReference)` |
| `CatalogMotorProfile` | Record `(String profileId, Identity identity, HardwareConfiguration hardware, List<SourceCurve> thrustSources, List<MassObservation> massObservations, List<GeometryObservation> geometryObservations, List<CompatibilityIssue> compatibilityIssues)` |
| `Teknofest2026MotorCatalog` | `static List<CatalogMotorProfile> load()`; pinned `SOURCE_ZIP_SHA256`; loads exactly three source profiles, never registers runtime `MotorCatalogs` entries. |

Profile-owned immutable nested records (no extra physics services/framework):

- `IdentityReference(role, designation, sourceReference, relationshipConfidence)`.
- `Identity(physicalManufacturer, competitionDesignation, propellantFamily, sourceNames)`.
- `HardwareConfiguration(hardwareId, kind, sourceReference, qualification)`.
- `SourceCurve(curveId, source, entryIndex, ejectionMetadata, acceptanceStatus)`; `record()` selects its indexed parsed entry. Multiple distinct curve IDs/revisions can coexist without choosing a runtime authority.
- `MassObservation(quantity, massKg, sourceReference, assemblyScope, qualification)`.
- `GeometryObservation(quantity, value, sourceUnit, sourceReference, confidence, axialDatum, qualification)`.
- `CompatibilityIssue(code, blockedCapability, fieldName, detail)`.
- `CompatibilityResult(availableCapabilities, issues)`, returned by `currentCoreCompatibility()`.

The five `Readiness` values are `CATALOG_AVAILABLE`, `THRUST_SOURCE_VALIDATED`, `MOTOR_PHYSICS_READY`, `INSTALLATION_READY`, `INERTIA_READY`. Curve acceptance is explicitly `SOURCE_CURVE_VALIDATED` or `IMPORTED_NOT_ACCEPTED`; numerical source validation is not measured certification. Confidence distinguishes `VERIFIED_SOURCE_TEXT`, `SUPPORTED_INFERENCE`, `UNRESOLVED_MAPPING`, `CONFLICT`. Verified text does not verify assembly equivalence or mounting datums. Hardware kind is separate catalog metadata (`RELOADABLE`, `SINGLE_USE`, `UNKNOWN`), not a change to the runtime `MotorType` enum.

`EjectionInterpretation` values are `NUMERIC_SOURCE_TOKEN`, `NO_EJECTION_PLUGGED`, `MIXED_SOURCE_OPTIONS`, `UNRESOLVED_PROVIDER_TOKEN`; none selects a runtime physical ejection delay.

Lists/sets are defensive immutable snapshots; raw bytes are cloned on input and output. Caller IDs, names and valid source tokens are not silently renamed. Duplicate curve IDs are rejected. No selected default source, motor property profile or inertia approximation is inferred.

## Generic RASP interpretation and validation

Format authority: [ThrustCurve RASP format documentation](https://www.thrustcurve.org/info/raspformat.html), inspected for this task. Seven header fields are required; source dimensions are mm, masses kg, times seconds and thrust newtons. Semicolon comment lines and blank lines are preserved in the source snapshot. Multiple entries require a blank/comment separator. Space/tab separation and LF/CRLF/CR endings are supported. UTF-8 (including ASCII) is decoded strictly; optional UTF-8 BOM stays in original bytes. Other legacy encodings and inline comments are not claimed supported.

The importer adds exactly one internal `(0,0)` when the first listed time is positive. An explicit `(0,0)` is tolerated without duplication; `(0,positive)` is rejected. Source point ordering is never changed. Duplicate/decreasing/negative times, negative thrust, no-positive-thrust curves, intermediate zero-thrust termination, nonzero terminal thrust, missing samples and nonfinite/overflowed numeric values are rejected. Finite dimensions must be positive; loaded mass positive; consumable mass nonnegative and no greater than loaded mass. These are interchange checks, not new runtime motor policies.

Numeric text decoding is confined to the reader/source-header adapter: ASCII decimal/scientific syntax, no locale commas, Java suffixes, hex, underscores or numeric Unicode extensions. Generic parsing is independent of motor names. Header delay text is retained even when provider-specific. Invalid input raises contextual `IllegalArgumentException`, including source filename/line where applicable. No motor installation, thrust evaluator, mass evaluator or physics algorithm is called to normalize source data.

Independent source trapezoids use `J = sum(0.5*(F_previous+F_current)*(t_current-t_previous))`, starting at `(0,0)`. Derived overflow is rejected. No smoothing, sorting, scaling, resampling, tail truncation or Excel cached impulse is used.

## Three source profiles and impulse evidence

All source headers report provider **Teknofest26**. Physical manufacturer **AeroTech/RCS** is recorded from the supplied drawings/brochures, not copied from that provider field. Exact observed variants remain separate:

| Competition | Exact ENG | Reload drawing | Brochure image | Hardware family | Propellant label |
|---|---|---|---|---|---|
| L1256 | TFL1256WS | L1256WS-P | L1256WS-PS (`L1256-1.jpg`) | RMS-75/3840 | Super White Lightning |
| M1850 | TFM1850W | M1850W-P | M1850W-PS (`M1850-1.jpg`) | RMS-75/7680 | White Lightning |
| N3300 | TFN3300R-P | N3300R-P | N3300R-PS (`N3300-1.jpg`) | RMS-98/15360 | Redline |

These are observed associations, not proven TF/P/PS interchangeability or a canonical manufacturer-variant lookup. Each curve identity includes its exact ENG designation, Rev1, filename checksum and ZIP-member reference.

| ENG | Listed/normalized points | End (s) | Exact decimal-source impulse (N*s) | Importer double (N*s) |
|---|---:|---:|---:|---:|
| TFL1256WS | 16 / 17 | 2.967 | 3650.044730405055 | 3650.0447304050554 |
| TFM1850W | 23 / 24 | 6.750 | 7649.329000 | 7649.329000000001 |
| TFN3300R-P | 26 / 27 | 4.613 | 14325.010771 | 14325.010771000001 |

The focused test independently parses the original decimal sample tokens into `BigDecimal` and integrates them without calling production normalization/integration. All three source anchors match **exactly as decimal arithmetic**. Every parsed sample value/order also matches its original token conversion; checksums prove original data preservation.

Final double assertions reuse the accepted J-005 impulse **rtol=3e-15, atol=0**, as used by existing `MotorThrustTest` and `propulsion_parity.py`; allowances are respectively `1.0950134191215164e-11`, `2.2947987e-11`, `4.2975032313e-11` N*s. No global numerical policy or historical tolerance changes. An initial new-test output-ULP allowance was replaced with this stricter established impulse convention; there was no failed comparison or change to expected source values.

The curves retain the package's adjusted/rescaled source times, not a claim of new static-fire measurements. The accepted audit found workbook updated times derived from propellant-mass ratios and ENG time rounding. The N3300 cached spreadsheet calculation omits part of its tail; it is not an authority here. Full supplied ENG tails and terminal points are retained.

## Mass observations and geometry conflicts

Each profile retains 12 separately qualified mass observations (source totals/propellant, derived difference, workbook original/updated values, nozzle sheet, brochure and secondary screenshot). Source labels such as manufacturer or measured are not metrological certification.

| Competition | ENG total / propellant kg | Derived difference kg | Workbook original total / propellant kg | Updated total / propellant kg | Nozzle total kg | -PS brochure total / propellant kg |
|---|---:|---:|---:|---:|---:|---:|
| L1256 | 3.905 / 1.905 | 2.000 | 3.756 / 1.964 | 3.905 / 1.905 | 3.902 | 3.756 / 1.827 |
| M1850 | 7.010 / 4.103 | 2.907 | 6.693 / 3.951 | 7.010 / 4.103 | 7.010 | 6.871 / 3.979 |
| N3300 | 12.645 / 7.668 | 4.977 | 12.259 / 7.512 | 12.645 / 7.668 | 12.645 | 12.054 / 7.338 |

Workbook references retain sheet/cell `G6/J6/G7/J7`; its footnote says original manufacturer-labelled values came from ENG files, whose exact older revisions were not supplied. Secondary `*-2.jpg` screenshot propellant/reported motor-weight observations are L 1.827/3.756, M 4.122/6.032, N 7.512/12.054 kg; their loaded/dry assembly scope remains unresolved. Those source/assembly/version conflicts are not reconciled by overwriting current ENG values. L1256's nozzle-sheet versus ENG 0.003 kg discrepancy remains explicit. The dry difference is arithmetic, not measured dry-motor data.

| Competition | ENG nominal diameter/length mm | Longest labeled drawing span in / mm | ENG minus that span mm | Case diameter mm | Largest labeled flange diameter mm |
|---|---|---|---:|---:|---:|
| L1256 | 75 / 530.6 | 20.390 / 517.906 | 12.694 | 75.311 | 79.375 |
| M1850 | 75 / 935.4 | 36.328 / 922.7312 | 12.6688 | 75.311 | 79.375 |
| N3300 | 98 / 1072.1 | 41.711 / 1059.4594 | 12.6406 | 98.298 | 100.838 |

Source geometry observations retain inches from `* Motor Boyutları.pdf`, including other labeled axial spans, case/flange diameters and the 0.500-inch projection. The derived difference is marked `SUPPORTED_INFERENCE`, axial datum `UNRESOLVED`. The numerical proximity to 12.7 mm does not prove common reference planes. Largest labeled flange is not certification of the complete closure/nozzle envelope. No final mounting/installation geometry is created; interface/datum clarification remains necessary.

## Ejection, readiness and runtime compatibility

- L raw `-`: unresolved provider token, no invented delay.
- M raw `1000`: generic reader retains numeric source option 1000, but the TEKNOFEST profile explicitly marks its provider meaning unresolved; it is not accepted as a physical 1000-second delay.
- N raw `P`: recognized source plugged/no-ejection metadata. Existing numeric `MotorDefinition.ejectionDelayS` cannot express absence of ejection faithfully. Neither zero nor infinity is substituted.

Generic numeric delay lists and mixed plugged options (e.g. synthetic `3-5-P`) remain separate source options, not automatic recovery policy. Original tokens persist through contextual interpretation.

All three profiles return only **CATALOG_AVAILABLE + THRUST_SOURCE_VALIDATED**. The structured compatibility result explicitly withholds MOTOR_PHYSICS_READY, INSTALLATION_READY and INERTIA_READY, with codes/fields/details:

| Blocker code | Missing/unrepresentable requirement |
|---|---|
| IDENTITY_VARIANT_MAPPING_UNRESOLVED | Exact TF/P/PS motor/hardware association |
| RELOADABLE_TYPE_UNREPRESENTABLE | Existing runtime MotorType supports SINGLE_USE only |
| CERTIFICATION_REFERENCE_MISSING | Verified four measured certification quantities for this exact assembly/source |
| PHYSICAL_EJECTION_DELAY_UNREPRESENTABLE | Source metadata is not a verified finite physical delay |
| MOTOR_PROPERTY_PROFILE_NOT_SELECTED | Explicit runtime mass/CG policy; no verified source mass/CG curves or automatic fallback |
| INSTALLATION_DATUM_UNRESOLVED | Accepted mounting reference planes/full external envelope |
| INERTIA_INPUTS_UNAVAILABLE | Validated dry inertia/CG and regression or separately approved source/model inputs |

The source-only compatibility API returns rejection evidence, not a partially fabricated `MotorDefinition` or `MotorInstallation`. No runtime conversion adapter is exposed in this gate because required current-core semantics cannot be satisfied by these data. Future conversion requires separate validated inputs and an explicit reviewed adapter; bypassing frozen constructor checks is not permitted.

M1850's five forward 0.875-inch bore grains and one aft 1.000-inch bore grain are explicitly noted as incompatible with the accepted identical-grain radial model. No grain geometry is submitted to an evaluator. L/N also lack validated dry inertia/regression inputs. Missing inertia does not prevent catalog import; no UNIFORM_CYLINDER fallback, inferred regression, dry CG or measured tensor is created.

## Executed verification

Environment: Windows 11 amd64; Temurin Java/javac **17.0.20.1**, Gradle Wrapper **8.12.1**; accepted Python **3.12.4**, NumPy **2.5.2**. Core directory is `roketsim-native-workspace/roketsim-core`.

```powershell
$env:JAVA_HOME='<JAVA_HOME>'
$env:GRADLE_USER_HOME='<GRADLE_USER_HOME>'
.\gradlew.bat -I verification/isolated-build.init.gradle '-PverificationBuildDir=<build-dir>' --project-cache-dir <build-dir> --offline --no-daemon
```

**Exit 0, 575 passed / 0 failure / 0 error / 0 skip, 37 suites: 19 new cases + 556 historical tests.** Includes NAT-023/024/025/A/B/C/026, mass, motor, geometry, F50 trajectory, RK4, events, recorder and all other accepted Java regressions. Main, source and Javadoc JARs build successfully. Existing Javadoc output reaches 100 nonblocking warnings; no complete warning inventory claimed. After tightening the new import assertions to the existing `3e-15` impulse convention, `test verifyProductionDependencies` was rerun with the same settings, again 575 passed / exit 0. No source calculation or historical assertion changed.

`MotorEngImportTest` covers all three exact source identities/masses/units/bytes; independent decimal impulse; generic separate synthetic motor; implicit and explicit origin; all sample ordering/termination; whitespace/newline/BOM and multiple-record support; malformed headers, empty collections, invalid UTF-8, negative/nonfinite/hex/suffix/underscore values; overflow; raw delay tokens and sentinel interpretation; manufacturer/provider and P/PS/TF separation; mass conflicts; unresolved mounting/readiness rejection; immutable snapshots, deterministic reloads, multiple source curves/revisions, no automatic selection and F50 preservation.

Packaged-JAR module-path consumer executed independently using `--module-path .../libs/roketsim-core-0.1.0.jar --add-modules org.roketsim.core`: **PACKAGED_MODULE_CONSUMER: PASS / exit 0**. All three source resources load, retain pinned hashes and return two readiness capabilities. One rerun initially could not locate the temporary consumer source because separate tool invocations used different temporary directories; recreating it in the same invocation resolved that environment-only error, and the final JAR consumer passed without production changes. ZIP/worktree/JAR source bytes compared equal. `verifyProductionDependencies` passed; `jdeps --print-module-deps` returns **java.base only**, exit 0. No external runtime dependency.

### Actual J-011 Python–Java trajectory parity

```powershell
& '../roketsim-native-engine/.venv/Scripts/python.exe' -B verification/native_trajectory_parity.py --java '<JAVA_HOME>/bin/java.exe' --build-dir '<build-dir>' --artifacts-dir '<build-dir>'
```

**NATIVE_TRAJECTORY_PARITY: PASS / exit 0**, actual unmodified Python and Java engines. Three runs / two distinct effective configurations:

| Run | Samples | Events | Numeric | Exact | Accepted nonexact |
|---|---:|---:|---:|---:|---:|
| primary | 1584 | 3 | 147521 | 147511 | 10 |
| repeat | 1584 | 3 | 147521 | 147511 | 10 |
| fine | 3167 | 3 | 294740 | 294714 | 26 |

Distinct totals **442261 numeric / 442225 exact / 36 accepted nonexact**; including repeat 589782 / 589736 / 46. Maximum absolute `8.881784197001252e-16`, maximum relative `4.3829501452845633e-16`, matching accepted NAT-026 last-bit evidence and unchanged quantity-specific tolerances. No new trajectory tolerance, fixture or expected result. This verifies preserved F50 legacy flight behavior, not TEKNOFEST runtime flight readiness. Other Python regression/parity/integrity packages were not rerun; no such claim is made.

Logs and XML remain outside Git: `%TEMP%/roketsim-motor001-focused.log`, `roketsim-motor001-regression.log`, `roketsim-motor001-final-tests.log`, `roketsim-motor001-trajectory.log`; isolated build `roketsim-motor001-build`; raw parity traces, anchors and summary `roketsim-motor001-trajectory`.

## Final scope, limitations and local commit

No core motor, installation, mass/CG/inertia, numerical or flight algorithm/public signature is changed; only the catalog package is exported. No Python source/test, historical verification record, ADR, accepted constant/profile/tolerance or historical fixture is changed. The three provided files are bundled as immutable source resources; drawings/workbook/images are not copied into production. There is no source ZIP runtime dependency, importer network access, RSE/SQLite/bulk catalog, UI, new motor physics or recovery/event work.

Remaining owner/provider decisions are exact identity/assembly mappings, certified measurements, mounting datums/envelopes, ejection semantics, explicit runtime property policy and independently supported inertia/regression data. Catalog/import PASS intentionally leaves those capabilities blocked. No new audit cycle or resolution of MOTOR-DATA-2026-A conflicts is claimed.

One local commit message: `feat(motors): add TEKNOFEST ENG import and source-aware schema`. The commit adding this document is the final identity; exact SHA is reported after creation and discoverable via `git log -1 --format=%H -- roketsim-native-workspace/docs/verification/motor-001-teknofest-eng-import.md`. No self-referential amend. Stage only the 12 MOTOR-001 files, verify clean worktree after commit; no push, merge, reset or historical branch modification.
