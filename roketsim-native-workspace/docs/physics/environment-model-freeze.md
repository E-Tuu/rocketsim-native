# Environment Source and Model Freeze

## Gate status

| Field | Result |
|---|---|
| Scope | NAT-009A Environment Source & Model Freeze |
| Technical authority | `OpenRocket_Fizik_Simulasyon_Motoru_Teknik_Spesifikasyon_v1.docx` local v1 |
| Production implementation | None |
| NAT-009A | **CLOSED / ACCEPTED — 2026-09-06 frozen kullanıcı kararları** |
| NAT-009B readiness | **GO — scalar conversion scope; aşağıdaki NAT-009B karar eki** |
| Blocking reason | B kapsamındaki datum bridge ve no-clamp kararları kullanıcı tarafından kapatıldı; sonraki gate maddeleri kendi kapsamlarında kalır |

**Güncel okuma kuralı:** Aşağıdaki ilk audit tarihsel kayıt olarak korunmuştur.
Son durum için belgenin sonundaki **NAT-009B — Frozen karar kapanışı** eki;
kaynak kanıtları için NAT-009A.2 ve blocker table kullanılmalıdır. Eski OPEN/RESOLVED satırları, özellikle
WIND-009 current-source atfı, kapanış ekinden bağımsız implementation izni değildir.

Bu kayıt local v1 içinde bulunan environment modellerini ayırır; eksik bilgiyi
genel fizik bilgisiyle tamamlamaz. `RESOLVED` yalnız raporda yeterince açık olan
kararı, `OPEN-PARITY` current-source eşdeğerlik boşluğunu, `TBD / MODEL DECISION`
ise implementation öncesi kapatılması gereken boşluğu gösterir.

## Source hierarchy and version rule

| Priority | Source tag | Use |
|---:|---|---|
| 1 | `[OR13.05]` | OpenRocket Technical Documentation v13.05; documented baseline |
| 2 | `[CURRENT-SRC]` | Stable 24.12 source/developer documentation; exact version must be recorded |
| 2a | `[CURRENT-SRC/UNRELEASED-26.XX]` | Unreleased/current-development behavior; stable baseline ile birleştirilmez |
| 3 | `[LITERATURE]` | NASA, NGA, NACA, MIT veya peer-reviewed/standard literature |
| 4 | `TBD / MODEL DECISION` | Local v1 tarafından çözülmemiş davranış |

`[OR-LIMITATION]` baseline kapsam sınırını, `[OUR-EXT]` ve
`[OUR-EXT-CANDIDATE]` ise baseline'dan ayrı gelişmiş seçenekleri gösterir.
Farklı sürümlerden seçilen davranışlar tek gizli modelde birleştirilemez.

## Environment family inventory

| Submodel | Baseline | Primary IDs | Status |
|---|---|---|---|
| Altitude/geopotential | AGL, MSL, ellipsoidal ve geopotential ayrı; `ATM-009` dönüşümü | CONV-018, ATM-009 | BLOCKED |
| Dry-air atmosphere | ISA/U.S. Standard profile, dry air | ATM-001..005 | BLOCKED — layer/constants data incomplete |
| Air properties | Density, sound speed, dynamic and kinematic viscosity | ATM-001, ATM-006..008 | PARTIAL |
| Constant gravity | `g_W=[0,0,-g0]` | GRAV-001 | RESOLVED |
| WGS84 normal gravity | Somigliana plus height correction | WGS-001..004 | PARTIAL — constants/height semantics incomplete |
| Earth rotation/Coriolis | Earth rotation in ENU and `-2 Ω × V` | GEO-001, WGS-005 | PARTIAL |
| Geodetic rates | Curvature radii and ENU-rate equations | GEO-002..003, WGS-006..008, GEO-ENV-001..003 | PARTIAL |
| Zero/steady wind | Zero fixture and ENU air-mass velocity formula | WIND-009 | RESOLVED for internal direction convention |
| Multi-level wind | Cartesian velocity-vector interpolation; nearest bound outside profile | WIND-010 | PARTIAL |
| Pink-noise wind | Two-pole scalar along-wind surrogate at fixed 0.05 s | WIND-001..008 | BLOCKED — time/seed state policy open |
| Humidity | Dry-air baseline; current 26.XX field `baseRelativeHumidity` | ATM10 | OPEN-PARITY |

## Top-level environment I/O

### Inputs

| Report ID | Input | Unit/frame | Use | Status |
|---|---|---|---|---|
| ENV01 | `time` | s | Wind/environment evaluation | RESOLVED |
| ENV02 | `position_world` | m, WORLD_ENU | Geodetic/wind profile query | RESOLVED |
| ENV03 | `velocity_world` | m/s, WORLD_ENU | Coriolis | RESOLVED; inertial-vs-rotating interpretation not stated |
| ENV04 | `latitude` | rad, geodetic | Gravity/Coriolis | RESOLVED |
| ENV05 | `longitude` | rad, geodetic | Geodetic/output | RESOLVED |
| ENV06 | `altitude_agl` | m | Wind/event/ground | PARTIAL — wind-model selection unresolved |
| ENV07 | `altitude_msl` | m | Atmosphere/wind | PARTIAL — ellipsoidal/MSL relation unresolved |
| ENV08 | `geopotential_height` | m | ISA layer selection | PARTIAL — conversion input unresolved |
| ENV18 | `wind_seed` | integer | Wind reproducibility | PARTIAL |
| GEO11 | `launch_site` | lat/lon/h | Local ENU origin | PARTIAL — height datum not frozen |
| WND03 | `mean_speed` | m/s | Steady/pink mean wind | RESOLVED |
| WND04 | `direction_from_north` | rad | Internal wind bearing | RESOLVED internally |
| WND06 | `turbulence_intensity` | dimensionless | Pink-noise scale | RESOLVED as an input; valid range absent |
| WND08 | `fixed_sample_period` | s | Integrator-independent sampling | Default constant 0.05 s is RESOLVED; state policy open |
| GRV01 | `gravity_profile` | enum | Constant/WGS84 selection | Option names not frozen as contract values |
| ATM11 | `atmosphere_profile` | enum | ISA/dry-air/current selection | Option names/defaults not frozen |
| SimulationInput | `launch`, `environment`, `simulation.seed` | structured | Site, model configuration, seed | Schema details TBD |

### Outputs

| Report ID | Output | Unit/frame | Producer | Status |
|---|---|---|---|---|
| ENV09 | `temperature` | K | Atmosphere | PLANNED |
| ENV10 | `pressure` | Pa | Atmosphere | PLANNED |
| ENV11 | `air_density` | kg/m^3 | Atmosphere | PLANNED |
| ENV12 | `speed_of_sound` | m/s | Atmosphere | PLANNED |
| ENV13 | `dynamic_viscosity` | Pa*s | Atmosphere | PLANNED |
| ENV14 | `kinematic_viscosity` | m^2/s | Atmosphere | PLANNED |
| ENV15 | `air_mass_velocity_world` | m/s, WORLD_ENU | Wind | PLANNED |
| ENV16 | `gravity_world` | m/s^2, WORLD_ENU | Gravity | PLANNED |
| ENV17 | `coriolis_world` | m/s^2, WORLD_ENU | Geodetic/Coriolis | PLANNED |
| ENV05/GEO01..09 | latitude, longitude, height/rates and curvature radii | SI/geodetic | Geodetic model | PARTIAL/PLANNED |

The following are explicitly outside the Environment output boundary:
`relative_air_velocity`, Mach, Reynolds, dynamic pressure, angle of attack and
aerodynamic coefficients. Local v1 assigns them to Relative Flow,
FlightConditions or Aerodynamics. `ATM-T09` crosses that boundary only as a V&V
chain; Environment supplies viscosity, not Reynolds.

## Altitude and height semantics audit

| Name | Meaning | Origin/reference | Unit | Used by | Conversion rule | Source/ID | Status |
|---|---|---|---|---|---|---|---|
| `altitude_agl` / `agl_altitude` | Above Ground Level | Local ground reference | m | Wind, events, ground; ground root uses it | No MSL/ground-elevation equation given | ENV06, GEO10, GND01 | PARTIAL |
| `altitude_msl` | Altitude relative to Mean Sea Level | MSL datum | m | Atmosphere and wind | No MSL-to-geometric/geopotential rule given | ENV07 | PARTIAL |
| Ellipsoidal height | Height relative to reference ellipsoid | WGS84 ellipsoid | m | Intended geodetic/gravity context | No geoid/MSL conversion given | CONV-018; GEO03 says “Elipsoidal/MSL” | BLOCKED |
| Geometric altitude `z` | `ATM-009` input symbol | Exact origin/datum not defined | m | Geopotential conversion | `h_geopot = R_e z/(R_e+z)` | ATM-009 | BLOCKED |
| `geopotential_height` / `geopotential_altitude` | ISA layer-selection coordinate | Derived from `z` using `R_e` | m | ISA atmosphere | ATM-009; `z` and numeric `R_e` unresolved | ENV08, ATM01, ATM-009 | BLOCKED |
| Local ground elevation | Ground datum needed to relate AGL to site altitude | Launch/site/terrain reference | m | AGL/event/ground | No equation or datum rule given | GEO11 plus GEO10 context | TBD / MODEL DECISION |
| Geodetic latitude `phi` | Ellipsoid-normal latitude | WGS84 | rad | WGS gravity, Earth rotation, geodetic rates | No geocentric conversion given or required by a frozen equation | ENV04, GEO01, WGS-002..007 | RESOLVED as geodetic |
| Geodetic longitude `lambda` | Geodetic longitude | WGS84/local site | rad | Geodetic state/output | Rate equation only | ENV05, GEO02, WGS-007 | PARTIAL |
| Geocentric quantities | Not defined as an environment coordinate in local v1 | — | — | — | — | No record | UNSPECIFIED |

### Required altitude answers

1. Atmosphere input: ENV07 routes MSL altitude to Atmosphere; ENV08/ATM01 route
   geopotential height to ISA layer selection. The exact MSL/ellipsoidal/
   geometric-to-geopotential chain is not frozen.
2. Ground/event logic: AGL (`ENV06`, `GEO10`, `GND01`).
3. Wind profile: both AGL and MSL are listed as wind consumers; `WIND-010` uses
   an undefined `h`. Model-specific altitude basis is unresolved.
4. WGS/geodetic gravity: `GRV03 height` and GEO03 “Elipsoidal/MSL profil
   yüksekliği” are listed. Exact datum is unresolved.
5. Geometric to geopotential: `h_geopot = R_e z/(R_e+z)` (ATM-009,
   `[LITERATURE/standard atmosphere]`). Exact meaning of `z` and numeric/source
   value of `R_e` are absent.
6. Below-zero altitude policy: not defined.
7. Upper altitude: local v1 states ISA and U.S. Standard Atmosphere temperature/
   pressure profiles agree through 32 km. It does not freeze 32 km as the
   supported upper-domain boundary or define behavior above it.
8. Cross-datum conversion: AGL/MSL/ellipsoidal/geometric/geopotential are required
   to remain distinct, but the full conversion graph is not provided.

## Atmosphere model freeze

### Baseline

Dry-air ISA/U.S. Standard Atmosphere layer model. Local v1 states humidity is
neglected in the OR13.05 baseline and temperature/pressure profiles agree to
32 km. Density is ideal-gas derived; sound speed uses calorically perfect air;
viscosity uses Sutherland's law; kinematic viscosity is `mu/rho`.

### Equation inventory

| ID | Purpose | Equation | Inputs | Output | Constants | Source tag | Status |
|---|---|---|---|---|---|---|---|
| ATM-001 | Density | `rho = p/(R_air T)` | `p,T` | `rho` | `R_air` | `[OR13.05 Eq. 4.1; specific-gas-constant form]` | Equation RESOLVED; numeric `R_air` TBD |
| ATM-002 | Hydrostatic relation | `dp = -g0 rho dh` | `rho,h` | `p(h)` | `g0` | `[OR13.05 Eq. 4.2]` | Equation RESOLVED; `h` datum tied to open altitude chain |
| ATM-003 | Layer temperature | `T(h)=T_b+L(h-h_b)` | `h,h_b,T_b,L` | `T` | Layer table | `[OR13.05-derived]` | BLOCKED — layer table absent |
| ATM-004 | Gradient-layer pressure | `p=p_b(T/T_b)^(-g0/(R_air L)), L!=0` | `h,T,T_b,p_b,L` | `p` | `g0,R_air`, layer data | `[OR13.05-derived]` | BLOCKED — constants/layers |
| ATM-005 | Isothermal-layer pressure | `p=p_b exp[-g0(h-h_b)/(R_air T_b)], L=0` | `h,h_b,T_b,p_b` | `p` | `g0,R_air`, layer data | `[OR13.05-derived]` | BLOCKED — constants/layers |
| ATM-006 | Speed of sound | `c=sqrt(gamma R_air T)` | `T` | `c` | `gamma,R_air` | `[LITERATURE-NASA]` | PARTIAL — `gamma` approximate, `R_air` numeric absent |
| ATM-007 | Dynamic viscosity | `mu=mu0(T/T0)^(3/2)(T0+S)/(T+S)` | `T` | `mu` | `mu0,T0,S` | `[LITERATURE-Sutherland/NASA]` | BLOCKED — constants absent |
| ATM-008 | Kinematic viscosity | `nu=mu/rho` | `mu,rho` | `nu` | None | `[LITERATURE]` | RESOLVED after dependencies |
| ATM-009 | Geopotential conversion | `h_geopot=R_e z/(R_e+z)` | `z` | `h_geopot` | `R_e` | `[LITERATURE/standard atmosphere]` | BLOCKED — input/reference constant undefined |

### Constants

| Constant/data | Local v1 value | Unit | Role | Status |
|---|---:|---|---|---|
| `g0` | 9.80665 | m/s^2 | Atmosphere hydrostatics and constant gravity | RESOLVED |
| `gamma_air` | approximately 1.4 | — | Sound-speed dry-air baseline | RESOLVED only as approximate baseline |
| Sea-level temperature | 288.15 | K | ATM-T01 expected value | V&V fixture; not declared as the full layer table |
| Sea-level pressure | 101325 | Pa | ATM-T02 expected value | V&V fixture; not declared as the full layer table |
| Sea-level density | approximately 1.225 | kg/m^3 | ATM-T03 expected value | Derived V&V expectation, not stored constant |
| `R_air` | Symbol only | J/(kg*K) | Density, pressure and sound speed | TBD / MODEL DECISION |
| `R_e` | Symbol only | m | ATM-009 | TBD / MODEL DECISION |
| `mu0` | Symbol only | Pa*s | Sutherland law | TBD / MODEL DECISION |
| `T0` | Symbol only | K | Sutherland reference temperature | TBD / MODEL DECISION; do not equate to ISA sea-level T without source |
| `S` | Symbol only | K | Sutherland constant | TBD / MODEL DECISION |
| Layer boundary heights | Not present | m geopotential | Layer selection | TBD / MODEL DECISION |
| Layer lapse rates | Not present | K/m | ATM-003/004/005 | TBD / MODEL DECISION |
| Layer base `T_b,p_b` | Not present | K, Pa | Recursive layer values | TBD / MODEL DECISION |

### Layer table

| Layer | Base height | Top height | Lapse rate | Base T | Base p | Source | Status |
|---|---|---|---|---|---|---|---|
| Not specified in local v1 | TBD | TBD | TBD | TBD | TBD | Source required | **BLOCKED** |

The report supplies the layer equations but no ISA layer records. It also does
not define inclusive/exclusive boundary-side selection. ATM-T06 requires
temperature/pressure continuity, but it does not fill those data gaps.

### Domain and out-of-domain policy

| Question | Local v1 result | Classification |
|---|---|---|
| Documented comparison range | ISA/U.S. Standard temperature and pressure agree through 32 km | Information, not a frozen implementation bound |
| Below-zero input | Not stated | TBD / MODEL DECISION |
| Above-domain input | Not stated | TBD / MODEL DECISION |
| Layer-boundary side | Not stated | TBD / MODEL DECISION |
| Non-finite/invalid domain | ATM12 reserves `atmosphere_validity`; exact guards/actions absent | PARTIAL |
| Humidity | Dry-air baseline; modern parity deferred | OR-LIMITATION / OPEN-PARITY |

### Atmosphere V&V fixtures

| Test ID | Input | Expected output | Tolerance | Source | Gate/stage |
|---|---|---|---|---|---|
| ATM-T01 | ISA sea level | `T=288.15 K` | Not frozen | Local v1 test matrix | NAT-009E |
| ATM-T02 | ISA sea level | `p=101325 Pa` | Not frozen | Local v1 test matrix | NAT-009E |
| ATM-T03 | ISA sea level | `rho approximately 1.225 kg/m^3` | Approximation shown; numeric tolerance absent | Local v1 test matrix | NAT-009E |
| ATM-T06 | Layer boundary | Temperature and pressure continuity | Not frozen | Local v1 test matrix | NAT-009E |
| ATM-T09 | Sutherland chain | `mu`, `nu` and later Reynolds finite/unit-consistent | Not frozen | Local v1 Appendix G.10 | NAT-009D/E for `mu,nu`; Reynolds closes in FlightConditions V&V |

No local-v1 fixture was found for speed of sound, viscosity numeric reference,
ATM-009 geopotential conversion or high-altitude reference points.

## Humidity and current-source parity

| Behavior | Version/source | Classification | Freeze result |
|---|---|---|---|
| Dry-air atmosphere | `[OR13.05]` | RESOLVED baseline | Humidity ignored |
| `baseRelativeHumidity` field | `[CURRENT-SRC/UNRELEASED-26.XX]` file format | OPEN-PARITY | Field existence recorded; exact thermodynamics absent |
| Density humidity correction | Current exact behavior not in local v1 | OPEN-PARITY | Do not merge into dry baseline |
| Sound-speed humidity correction | Current exact behavior not in local v1 | OPEN-PARITY | Do not merge into ATM-006 |
| Humid-air advanced model | Future option | OUR-EXT-CANDIDATE | Not planned until separately sourced |

Existing report item `OR-OPEN-ENV-001` keeps current relative-humidity
thermodynamics deferred; `OR-OPEN-ENV-002` keeps exact current sound-speed and
viscosity internals as validation work.

## Gravity, WGS84 and Coriolis freeze

### Model boundary

| Model | Equation ID | Equation | Inputs | Output | Frame | Constants | Source | Status |
|---|---|---|---|---|---|---|---|---|
| Constant gravity baseline | GRAV-001 | `g_W=[0,0,-g0]` | None beyond model selection | `g_W` | WORLD_ENU | `g0=9.80665 m/s^2` | `[OR13.05 baseline]` | RESOLVED |
| WGS eccentricity | WGS-001 | `f=1/(1/f); e^2=f(2-f)` | `1/f` | `f,e^2` | Scalar | `1/f` | `[LITERATURE/WGS84]` | RESOLVED |
| Somigliana surface gravity | WGS-002 | `g(phi,0)=g_e(1+k sin^2(phi))/sqrt(1-e^2 sin^2(phi))` | geodetic `phi` | `g(phi,0)` | Scalar magnitude | `g_e,k,e^2` | `[LITERATURE/Somigliana]` | BLOCKED — `g_e,k` values absent |
| WGS rotation parameter | WGS-003 | `m=Omega^2 a^2 b/GM` | WGS constants | `m` | Scalar | `Omega,a,b,GM` | `[LITERATURE/WGS84]` | PARTIAL — `b` value/derivation not frozen |
| Height correction | WGS-004 | `g(phi,h)=g(phi,0){1-[2/a](1+f+m-2f sin^2(phi))h+3h^2/a^2}` | `phi,h` | `g(phi,h)` | Scalar magnitude | `a,f,m` | `[LITERATURE/WGS84 height correction]` | BLOCKED — height datum unresolved |
| Gravity vector | GRV05 convention | `g_W=[0,0,-g]` | scalar `g` | `g_W` | WORLD_ENU | Model result | Detailed register | RESOLVED convention |
| Earth rotation vector | WGS-005 | `Omega_ENU=[0,Omega cos(phi),Omega sin(phi)]` | `phi` | `Omega_ENU` | WORLD_ENU | `Omega` | `[LITERATURE; ENU]` | RESOLVED equation |
| Coriolis acceleration | GEO-001 | `a_C=-2 Omega_E x V` | `Omega_ENU`, `velocity_world` | `a_C` | WORLD_ENU | `Omega` | `[LITERATURE]` | PARTIAL — velocity reference definition absent |
| Prime-vertical radius | GEO-002 | `N(phi)=a/sqrt(1-e^2 sin^2(phi))` | `phi,e^2,a` | `N(phi)` | Scalar | `a,e^2` | `[LITERATURE/WGS84]` | RESOLVED equation |
| Meridional radius | GEO-003 | `M(phi)=a(1-e^2)/(1-e^2 sin^2(phi))^(3/2)` | `phi,e^2,a` | `M(phi)` | Scalar | `a,e^2` | `[LITERATURE/WGS84]` | RESOLVED equation |
| Latitude rate | WGS-006 / GEO-ENV-001 | `phi_dot=v_N/(M(phi)+h)` | `v_N,M,h` | `phi_dot` | ENU/geodetic | WGS radii | `[LITERATURE]` | PARTIAL — height datum open |
| Longitude rate | WGS-007 / GEO-ENV-002 | `lambda_dot=v_E/[(N(phi)+h)cos(phi)]` | `v_E,N,h,phi` | `lambda_dot` | ENU/geodetic | WGS radii | `[LITERATURE]` | PARTIAL — height datum/polar policy open |
| Height rate | WGS-008 / GEO-ENV-003 | `h_dot=v_U` | `v_U` | `h_dot` | ENU/geodetic | None | `[LITERATURE]` | Equation RESOLVED; height type open |

Constant gravity and WGS84 gravity are explicit alternatives through GRV01;
they must not be blended by a hidden switch. Coriolis is a separately auditable
option/effect, not an implicit consequence of selecting constant gravity.

### Gravity and Earth constants

| Constant | Local v1 value | Unit | Status |
|---|---:|---|---|
| `g0` | 9.80665 | m/s^2 | RESOLVED |
| `Omega_E` | 7.292115e-5 | rad/s | RESOLVED |
| `a_WGS84` | 6378137.0 | m | RESOLVED |
| `1/f_WGS84` | 298.257223563 | — | RESOLVED |
| `GM_WGS84` | 3.986004418e14 | m^3/s^2 | RESOLVED |
| `b` | Not listed | m | TBD / MODEL DECISION |
| `g_e` | Symbol only | m/s^2 | TBD / MODEL DECISION |
| `k` | Symbol only | — | TBD / MODEL DECISION |

### Centrifugal double-count invariant

Local v1 explicitly states WGS normal gravity already includes Earth's
centrifugal contribution. Adding `-Omega x (Omega x r)` again is forbidden.
ENV-WARN-001, INV-003, GRV08, WV30, WARN-T04, GRV-T03 require an assertion/
fail-fast guard. This is an implementation invariant, not a user warning or
fallback.

Current exact WGS gravity and exact geodetic transform remain
`OR-OPEN-ENV-003` and `OR-OPEN-ENV-004` validation items.

## Wind model freeze

### Model inventory

| Model | Inputs | Output | Frame | Direction | Altitude basis | Algorithm/source | Status |
|---|---|---|---|---|---|---|---|
| Zero wind | None/model selection | `V_air,W=0` | WORLD_ENU | Not applicable | Not applicable | WIND-T01 | RESOLVED fixture |
| Steady wind | `U,psi` | `[U sin(psi),U cos(psi),0]` | WORLD_ENU | Toward bearing measured from North | None | WIND-009 `[CURRENT-SRC; ENU]` | RESOLVED internally |
| Multi-level wind | Levels and query height `h` | Interpolated Cartesian velocity | WORLD_ENU | Interpolate vectors, not angles | `h` not defined as AGL/MSL | WIND-010 `[CURRENT-SRC 24.12+]` | PARTIAL |
| Pink-noise wind | `time,U,sigma_u/ I_u,seed` | Scalar fluctuating speed then ENU vector | Along-wind; steady vector has Up=0 | Same internal `psi` | Not spatially resolved | WIND-001..008 `[OR13.05]` | BLOCKED — state/time policy |
| Vertical stochastic wind | — | — | — | — | — | L-WIND-001 | OR-LIMITATION; `V_z=0` in baseline pink noise |
| Three-component turbulence | — | — | — | — | — | L-WIND-002/003 | OUR-EXT-CANDIDATE |

### Wind direction semantics

`WND04` is named `direction_from_north`: the word “from” describes the angular
reference axis, not meteorological wind origin. WIND-009 and fixtures resolve
the internal meaning:

`V_wind,W = [U_n sin(psi), U_n cos(psi), 0]`

| `psi` | East | North | Up | Internal result |
|---:|---:|---:|---:|---|
| 0 | 0 | `U_n` | 0 | Toward North; WIND-T02 |
| `pi/2` | `U_n` | 0 | 0 | Toward East; WIND-T03 |

Thus internal `psi` is a **TOWARD bearing**, clockwise from North in WORLD_ENU.
The local v1 does not define conversion from a UI meteorological **FROM**
direction; that conversion remains a boundary OPEN-PARITY/MODEL DECISION.

### Multi-level interpolation

For adjacent levels `(h1,V1)` and `(h2,V2)`:

`f=(h-h1)/(h2-h1)` and `V(h)=(1-f)V1+fV2` (WIND-010).

| Policy item | Local v1 result | Status |
|---|---|---|
| Interpolated quantity | Cartesian velocity vectors, not direction angles | RESOLVED |
| Midpoint fixture | Cartesian vector midpoint | WIND-T06 |
| Below first level | Nearest bound retained | RESOLVED text |
| Above last level | Nearest bound retained | RESOLVED text |
| Warning terminology | WV09 says profile-outside “extrapolation”, APPROXIMATE/CONTINUE | OPEN terminology conflict with nearest-bound clamp |
| Altitude coordinate | `h` undefined; ENV06 AGL and ENV07 MSL both list wind | BLOCKED |
| Level ordering | Not specified | TBD / MODEL DECISION |
| Duplicate altitude | Division-by-zero/merge/reject behavior not specified | TBD / MODEL DECISION |
| Discontinuity policy | Not specified | TBD / MODEL DECISION |
| Vertical profile component | Not explicitly specified for multi-level records | TBD / MODEL DECISION |

### Pink-noise equations and constants

| ID | Purpose | Equation/algorithm | Source | Status |
|---|---|---|---|---|
| WIND-001 | IIR recurrence | `x_n=w_n-a1*x_(n-1)-a2*x_(n-2)-...` | `[OR13.05 Eq. 4.5]` | PARTIAL — text says two-pole; initialization/distribution absent |
| WIND-002 | Coefficients | `a0=1; a_k=[(k-1-alpha/2)/k]a_(k-1)`, `alpha=5/3` | `[OR13.05 Eq. 4.6]` | RESOLVED formula |
| WIND-003 | Turbulence intensity | `I_u=sigma_u/U` | `[OR13.05 Eq. 4.7]` | RESOLVED; zero-mean-speed policy absent |
| WIND-004 | Speed shorthand | `U_n=U+sigma_u*x_n` | `[OR13.05 section 4.1.2]` | Ambiguous whether `x_n` is normalized |
| WIND-005 | Kaimal spectrum context | Report equation | `[OR13.05 Eq. 4.3; Kaimal]` | Reference/validation context |
| WIND-006 | von Karman spectrum context | Report equation | `[OR13.05 Eq. 4.4; von Karman]` | Reference/validation context |
| WIND-007 | Normalize raw sequence | `x_norm,n=x_raw,n/2.252` | `[OR13.05 two-pole normalization]` | RESOLVED chain step |
| WIND-008 | Final fluctuating speed | `U_n=U+sigma_u*x_norm,n` | `[OR13.05 section 4.1.2]` | Preferred complete chain in local v1; exact parity still needs fixture |

| Constant | Value | Unit | Meaning | Status |
|---|---:|---|---|---|
| `alpha_pink` | 5/3 | — | Spectrum exponent | RESOLVED |
| `Delta t_wind` | 0.05 | s | Fixed wind sample interval | RESOLVED |
| `sigma_unscaled` | 2.252 | — | Raw two-pole sequence standard deviation | RESOLVED |
| White-noise distribution | Not stated | — | `w_n` generator | TBD / MODEL DECISION |
| Filter initial state | Not stated | — | Startup | TBD / MODEL DECISION |

Baseline limitations are explicit: one scalar along-wind fluctuation, no
vertical stochastic component, and no physical 3-D spatial turbulence field.

### RNG and integrator coupling audit

| Question | Local v1 answer | Freeze result |
|---|---|---|
| Does every environment evaluation advance RNG state? | Not stated | OPEN |
| Is sample selection a pure function of physical time? | WND08 says fixed period independent of integrator `dt`, but selection/index rule is absent | OPEN |
| Does repeated evaluation at the same `t` return the same wind? | Not stated | OPEN |
| Do RK4 k2/k3 evaluations at the same physical time share wind state? | Not stated | OPEN |
| Does changing timestep preserve the stochastic realization? | Not stated | OPEN |
| How is seed reproducibility guaranteed? | WIND-T04 requires same-seed deterministic sequence; API-INV-010 records seed metadata; state/reset mapping absent | PARTIAL |
| Current OpenRocket versus our deterministic policy | Exact current behavior not captured in local v1 | OPEN-PARITY |

**OPEN — STOCHASTIC TIME/SEED POLICY.** This is a blocker for NAT-009J and for
NAT-009K composition. No RNG or stateful sampling implementation may start
until evaluation, indexing, reset and reproducibility semantics are frozen.

## Geodetic and Earth-rotation audit

| Item | Equation/data | Inputs | Output | Source | Status |
|---|---|---|---|---|---|
| Local frame | WORLD_ENU: East, North, Up | Launch site | Local position/velocity | CONV-004, GEO04, GEO11 | Convention RESOLVED; construction transform open |
| Prime-vertical radius | GEO-002 | `phi,a,e^2` | `N(phi)` | `[LITERATURE/WGS84]` | Equation RESOLVED |
| Meridional radius | GEO-003 | `phi,a,e^2` | `M(phi)` | `[LITERATURE/WGS84]` | Equation RESOLVED |
| Latitude rate | WGS-006 / GEO-ENV-001 | `v_N,M,h` | `phi_dot` | `[LITERATURE]` | Height datum open |
| Longitude rate | WGS-007 / GEO-ENV-002 | `v_E,N,h,phi` | `lambda_dot` | `[LITERATURE]` | Polar/cosine guard threshold absent |
| Height rate | WGS-008 / GEO-ENV-003 | `v_U` | `h_dot` | `[LITERATURE]` | Equation RESOLVED; height type open |
| Earth rotation | WGS-005 | `phi,Omega` | `Omega_ENU` | `[LITERATURE; ENU]` | RESOLVED equation |
| Coriolis | GEO-001 | `Omega_ENU,V_world` | `coriolis_world` | `[LITERATURE]` | PARTIAL |
| Exact current transform | Not given | launch/site plus state | Geodetic/local conversion | OR-OPEN-ENV-004 | OPEN-PARITY |

GEO12 reserves a geodetic-validity result for pole, `cos(phi)` and domain
checks, but no threshold or action is frozen. Longitude is not needed by the
listed local normal-gravity/Coriolis equations, but remains geodetic state and
output. No geocentric coordinate model is defined.

## Validity and intentional limitations

| ID/topic | Condition/limitation | Classification | Action/status |
|---|---|---|---|
| Atmosphere upper/lower domain | Bounds and behavior absent | MODEL DECISION | BLOCKS NAT-009C/E |
| Dry-air baseline | Humidity neglected | OR-LIMITATION | Continue under declared profile |
| ATM12 | Layer/domain/finite validity field exists; rules absent | Implementation guard TBD | OPEN |
| WV09 | Wind profile outside range | MODEL_VALIDITY / APPROXIMATE | CONTINUE; text says nearest bound |
| L-WIND-001 | No vertical pink-noise component | OR-LIMITATION | `V_z=0` for baseline pink noise |
| L-WIND-002 | One scalar along-wind fluctuation | OR-LIMITATION | No `u',v',w'` field |
| L-WIND-003 | Pink-noise surrogate, not spatial atmosphere | OR-LIMITATION | Advanced weather/LES is OUR-EXT-CANDIDATE |
| GEO12 | Pole/cosine/domain validity reserved, no limits | MODEL DECISION | OPEN |
| ENV-WARN-001 / INV-003 / WV30 | Duplicate centrifugal contribution | IMPLEMENTATION ASSERTION | FAIL FAST |
| OR-OPEN-ENV-001 | Current humidity thermodynamics | OPEN-PARITY | Deferred; dry baseline not blocked |
| OR-OPEN-ENV-002 | Current sound/viscosity internals | OPEN-PARITY / VALIDATION | Requires reference capture |
| OR-OPEN-ENV-003 | Current WGS exact formula | OPEN-PARITY / VALIDATION | Requires reference capture |
| OR-OPEN-ENV-004 | Current exact geodetic transform | OPEN-PARITY / VALIDATION | Requires reference capture |

Warning, error, limitation and guard remain distinct: dry-air and pink-noise
scope are model limitations; profile-range handling is model validity;
duplicate centrifugal contribution is a fail-fast implementation assertion;
invalid input policy must be frozen before code.

## Version boundary

| Feature | OR13.05 | Stable/current baseline | Unreleased/current | OUR-EXT | Status |
|---|---|---|---|---|---|
| Atmosphere | Dry ISA/U.S. Standard equations | Current atmosphere/file format mentioned, exact internals not captured | `[CURRENT-SRC/UNRELEASED-26.XX]` may add humidity field | Humid/advanced atmosphere candidate | Dry baseline PARTIAL due data gaps |
| Humidity | Neglected | No stable thermodynamics frozen | `baseRelativeHumidity` field | Humid-air model candidate | OPEN-PARITY |
| Constant gravity | `g0` baseline | Gravity model selection exists in developer/file-format notes | None identified | — | RESOLVED baseline |
| WGS gravity | Not OR13 baseline in this record | Literature WGS model planned; current exact formula open | None identified | Higher-fidelity Earth model not proposed | PARTIAL |
| Coriolis/geodetic | Literature extension in local v1 | Current exact transform open | None identified | Advanced global/geocentric model candidate only | PARTIAL |
| Steady wind | Mean wind context | WIND-009 ENU formula | None identified | Vertical/3-D weather candidate | RESOLVED internally |
| Pink-noise | Two-pole scalar surrogate | Current PinkNoiseWindModel reportedly retains 0.05/2.252 constants | None identified | 3-D turbulence candidate | Algorithm PARTIAL; time policy OPEN |
| Multi-level wind | Not in OR13 baseline | Stable 24.12 adds profile/CSV; Cartesian interpolation | None identified | Advanced spatial weather candidate | PARTIAL |
| Vertical turbulence | Absent | Absent in baseline pink noise | None identified | OUR-EXT-CANDIDATE | OR-LIMITATION |
| Seed/reproducibility | Seed behavior not fully specified | Seed field/model and same-seed fixture | None identified | Deterministic time-indexed policy may become MODEL DECISION, not hidden extension | BLOCKED |

## Environment V&V inventory

| Test ID | Submodel | Input | Expected | Source | Implement at |
|---|---|---|---|---|---|
| ATM-T01 | Atmosphere | ISA sea level | 288.15 K | Test matrix | NAT-009E |
| ATM-T02 | Atmosphere | ISA sea level | 101325 Pa | Test matrix | NAT-009E |
| ATM-T03 | Atmosphere | ISA sea level | approximately 1.225 kg/m^3 | Test matrix | NAT-009E |
| ATM-T06 | Atmosphere layers | Layer boundary | T/p continuity | Test matrix | NAT-009E |
| ATM-T09 | Air properties | Sutherland reference | `mu,nu` finite/unit-consistent; later Re chain | Appendix G.10 | NAT-009D/E; Re in FlightConditions gate |
| WIND-T01 | Zero wind | Zero-wind config | `V_air=0` | Test matrix | NAT-009H |
| WIND-T02 | Direction | `psi=0` | North | Test matrix | NAT-009H |
| WIND-T03 | Direction | `psi=pi/2` | East | Test matrix | NAT-009H |
| WIND-T04 | Pink-noise | Same seed | Deterministic sequence | Test matrix | NAT-009J after time/seed freeze |
| WIND-T06 | Multi-level | Two-level midpoint | Cartesian vector midpoint | Test matrix | NAT-009I |
| GRV-T01 | WGS gravity | Equator and pole | Pole normal gravity greater | Appendix G.10 | NAT-009G |
| GRV-T02 | WGS gravity | Increased height | Normal gravity decreases | Appendix G.10 | NAT-009G |
| GRV-T03 | WGS guard | Duplicate centrifugal | WV30 assertion | Appendix G.10 | NAT-009G/K |
| WARN-T04 | WGS guard | WGS plus duplicate centrifugal | Implementation assertion | Test matrix | NAT-009G/K |
| GEO-T08 | Geodetic | Known ENU velocity | Hand-computed `phi_dot,lambda_dot,h_dot` | Appendix G.10 | NAT-009G |
| DYN-T03 | Gravity integration | Gravity only | Ballistic solution | Test matrix | NAT-009K plus future Dynamics V&V |
| OR-P03 | Crosswind parity | Crosswind rocket | Relative flow/weathercocking/trajectory parity | OR parity matrix | NAT-009K provides environment; closes later with FlightConditions/Dynamics |

No dedicated fixture ID is present for ATM-009, speed of sound, viscosity
numeric value, high-altitude atmosphere, WGS absolute reference values,
Coriolis vector, profile bound handling or stochastic same-time evaluation.

## Traceability matrix

Planned module names are proposals for traceability only; no package or source
file is created by NAT-009A.

| Requirement/equation/convention | Source tag | Planned code module | Planned NAT gate | V&V fixture | Status |
|---|---|---|---|---|---|
| CONV-001, internal SI | `[OR13.05]/local v1` | all environment modules | NAT-009B..K | Existing units tests plus each fixture | RESOLVED |
| CONV-004/014, WORLD_ENU environment vectors | local v1 | `environment/wind.py`, `gravity.py`, `geodetic.py` | NAT-009F..K | WIND-T02/03, GRV-T03 | RESOLVED convention |
| CONV-018, distinct altitudes | local v1 | `environment/altitude.py` | NAT-009B | New fixtures required | BLOCKED |
| ATM-009 | `[LITERATURE/standard atmosphere]` | `environment/altitude.py` | NAT-009B | Missing | BLOCKED |
| ATM-001..005 | `[OR13.05]` / derived | `environment/atmosphere.py` | NAT-009C | ATM-T01/02/03/06 | BLOCKED by constants/layers |
| ATM-006..008 | `[LITERATURE]` | `environment/air_properties.py` | NAT-009D | ATM-T09; missing sound/viscosity references | PARTIAL |
| ATM10 / humidity | `[CURRENT-SRC/UNRELEASED-26.XX]` | Future profile, not baseline | Deferred | None | OPEN-PARITY |
| GRAV-001 | `[OR13.05 baseline]` | `environment/gravity.py` | NAT-009F | DYN-T03 | RESOLVED |
| WGS-001..004 | `[LITERATURE/WGS84]` | `environment/gravity.py` | NAT-009G1 proposed | GRV-T01/02 | BLOCKED constants/height |
| GEO-001, WGS-005 | `[LITERATURE]` | `environment/coriolis.py` | NAT-009G2 proposed | New fixture required | PARTIAL |
| GEO-002..003, WGS-006..008 | `[LITERATURE/WGS84]` | `environment/geodetic.py` | NAT-009G2 proposed | GEO-T08 | PARTIAL |
| GEO-ENV-001..003 | `[LITERATURE/WGS84]` | Same geodetic implementation | NAT-009G2 proposed | GEO-T08 | Duplicate aliases of WGS-006..008; traceability cleanup needed |
| WIND-009 | `[CURRENT-SRC; ENU]` | `environment/wind.py` | NAT-009H | WIND-T01/02/03 | RESOLVED internal convention |
| WIND-010 | `[CURRENT-SRC 24.12+]` | `environment/wind_profile.py` | NAT-009I | WIND-T06 plus missing bound fixtures | PARTIAL |
| WIND-001..008 | `[OR13.05]` | `environment/pink_noise.py` | NAT-009J | WIND-T04 plus missing time fixtures | BLOCKED |
| ENV01..18 composition | local v1 | `environment/composition.py` | NAT-009K | Environment integration suite | PARTIAL |
| INV-003 / ENV-WARN-001 / WV30 | local v1 | gravity composition/validity | NAT-009G/K | GRV-T03, WARN-T04 | RESOLVED invariant |

## Proposed NAT-009 gate audit

| Gate | Audit result | Dependency/status |
|---|---|---|
| NAT-009A Source & Model Freeze | Correct scope | BLOCKED until NAT-009B-critical source gaps close |
| NAT-009B Altitude & Geopotential Foundation | Necessary and should remain first | NO-GO: `z`, `R_e`, datum conversion and below/above policy open |
| NAT-009C Dry-Air Atmosphere Core | Correct after B | NO-GO: ISA layer table and `R_air` absent |
| NAT-009D Air Properties | Correct after C | NO-GO: Sutherland constants and numeric sound fixture absent |
| NAT-009E Atmosphere V&V Gate | Correct after C/D | Add ATM-009, sound, viscosity and domain fixtures before start |
| NAT-009F Constant Gravity Baseline | Small and independent after frame foundation | Can proceed once NAT-009A policy allows; does not depend on atmosphere |
| NAT-009G WGS84 / Geodetic / Coriolis | **Too large** | Proposed split: G1 WGS84 gravity; G2 geodetic rates/ENU Earth rotation/Coriolis |
| NAT-009H Steady Wind Foundation | Correct | Internal toward-direction resolved; UI FROM conversion stays boundary-open |
| NAT-009I Multi-Level Wind | Correct after B/H | Needs altitude basis, ordering, duplicate/bound policies |
| NAT-009J Pink-Noise Wind | **Too large while policy open** | Proposed split: J1 filter/math parity; J2 time-indexed sampler/seed/reset determinism |
| NAT-009K Environment Composition & V&V | Correct final gate | Depends on all enabled submodels; cannot close with J policy unresolved |

Recommended order remains B -> C -> D -> E; F may be developed independently;
H follows direction freeze; I depends on B and H; G should split into G1/G2;
J should split into filter and deterministic sampler; K remains last. These are
plan recommendations only and are not added to the register.

## Open items before NAT-009B

| ID | Problem | Why it matters | Source checked | Needed decision/source | Blocks |
|---|---|---|---|---|---|
| ENV-OPEN-001 | `ATM-009` input `z` datum/type and numeric `R_e` are undefined | Wrong altitude datum changes every ISA layer lookup | Local v1 9.4, 46.3, CONV-018, Appendix B | Authoritative standard-atmosphere source plus explicit contract mapping | NAT-009B, C, E |
| ENV-OPEN-002 | GEO03 conflates “Elipsoidal/MSL”; no geoid or ground-elevation conversion | WGS gravity and atmosphere could consume different physical heights under one name | Local v1 ENV06..08, GEO03/10/11 | Freeze ellipsoidal, MSL and AGL datums and conversion ownership | NAT-009B, G, I, K |
| ENV-OPEN-003 | ISA layer table, `R_air`, layer base data and boundary-side policy absent | ATM-003..005 cannot be implemented traceably | Local v1 9.1, 46.3, Appendix B, ATM-T01..06 | Authoritative layer/constants source and exact domain policy | NAT-009C/E |
| ENV-OPEN-004 | Sutherland `mu0,T0,S` and numeric sound/viscosity fixtures absent | ATM-006/007 implementation would require guessed constants | Local v1 9.1, 46.3, Appendix B, ATM-T09 | NASA source values explicitly adopted into freeze | NAT-009D/E |
| ENV-OPEN-005 | Atmosphere below-zero, upper-domain and out-of-domain actions absent | Clamp/extrapolate/reject cannot be chosen safely | Local v1 domain text, ATM12, validity framework | Model-decision record tied to supported layer set | NAT-009B/C/E |
| ENV-OPEN-006 | Wind-profile altitude is ambiguous between AGL and MSL; level ordering/duplicates unspecified | WIND-010 can select/interpolate the wrong levels or divide by zero | ENV06/07, WIND-010, WND09..12 | Stable 24.12 source capture plus contract decision | NAT-009I |
| ENV-OPEN-007 | UI meteorological FROM to internal TOWARD conversion absent | Boundary could reverse wind by 180 degrees | WND04, WIND-009, WIND-T02/03 | Boundary contract/source decision | Future adapter; does not block internal NAT-009H |
| ENV-OPEN-008 | Pink-noise evaluation/index/reset/seed semantics are not frozen | RK4 call count can alter stochastic realization | WIND-001..008, WND01/07/08, WIND-T04, API-INV-010 | **OPEN — STOCHASTIC TIME/SEED POLICY** plus current-source parity fixture | NAT-009J/K |
| ENV-OPEN-009 | WGS `b`, `g_e`, `k` constants and WGS height datum are absent | WGS-002..004 cannot produce sourced numeric gravity | Appendix B, WGS-001..004, GRV02..04 | NGA/WGS constant adoption and height contract | NAT-009G1 |
| ENV-OPEN-010 | Coriolis velocity reference and polar validity thresholds are not explicit | Rotating-frame semantics/guards could diverge | ENV03, GEO-001, WGS-005..008, GEO12 | Literature/current-source freeze plus fixtures | NAT-009G2 |
| ENV-OPEN-011 | Detailed ID namespace `GEO01..GEO12` is reused by Geometry and Geodetic registers | Violates unambiguous source-to-code traceability and triggers WV31 risk | Sections 6.5 and Appendix G.3 | Rename/family-qualify geodetic register IDs without changing equations | NAT-009A traceability, NAT-009G |
| ENV-OPEN-012 | WIND-004 raw `x_n` and WIND-008 normalized `x_norm,n` both define `U_n` | Wrong chain changes turbulence standard deviation | Local v1 9.2 and 46.3 | Confirm authoritative OR13/current sequence; retain version separation | NAT-009J1 |
| OR-OPEN-ENV-001 | Exact current humidity thermodynamics | Current parity, not dry baseline | Local v1 open-equivalence table | Current version source/fixture | Deferred humidity profile |
| OR-OPEN-ENV-002 | Exact current sound-speed/viscosity internals | Current parity | Local v1 open-equivalence table | Current source/reference capture | NAT-009D/E parity |
| OR-OPEN-ENV-003 | Exact current WGS gravity formula | Current parity | Local v1 open-equivalence table | Version-pinned source/reference capture | NAT-009G1 |
| OR-OPEN-ENV-004 | Exact current geodetic transform | Current parity | Local v1 open-equivalence table | Version-pinned source/reference capture | NAT-009G2 |

## Final freeze decision

**NAT-009A: BLOCKED / OPEN**

**NAT-009B: NOT SAFE TO START**

Local v1 provides the environment family boundary, principal equations,
several constants, direction/frame conventions and a useful V&V inventory. It
does not provide enough authoritative detail to implement altitude conversion,
the ISA layers, air-property constants, WGS numeric gravity or deterministic
stochastic sampling without inventing behavior. The project register therefore
remains unchanged and NAT-009 is not marked complete.

## NAT-009A.2 — Source Gap Closure (2026-09-05)

### Kapsam, kanıt ve karar eksenleri

Bu ek ilk auditi silmez; source closure ile model adoption farklı durumlardır.
`RESOLVED (source)` incelenen sürümün davranışının belirlendiğini ifade eder;
Native runtime politikasının otomatik kabulü anlamına gelmez. Production kod,
model, fixture veya equation ID eklenmedi. Local v1 DOCX değiştirilmedi.

```text
PHYSICAL STANDARD  ->  RoketSim Native Model  <-  OpenRocket Parity
                      açık model kararları
```

OR13.05 önce incelendi; boşluklarda stable 24.12, fiziksel provenance için
resmi standartlar kullanıldı. Development/master kodu stable kaynağa eklenmedi.
24.12 etiketi GitHub commits API ile doğrulanan commit:
`133b558de556f0282f51c3f53f3b3fc633b4310a`.
Bu ekin bütün `SRC-*` kayıtları `[CURRENT-SRC/stable-24.12]` etiketlidir.
Kaynak bulguları statik incelemedir; Java parity testleri burada çalıştırılmadı.

| Evidence key / tag | Document veya source location | Version / identifier | Kısa kanıt |
|---|---|---|---|
| DOC `[OR13.05]` | [Technical documentation](https://openrocket.sourceforge.net/techdoc.pdf) | 2013-05-10; §4.1.1, Table 4.1, §4.1.2; basılı s.57–62 | Layer tablosu, dry air, sabit-zaman pink noise |
| STD-ATM `[LITERATURE]` | [U.S. Standard Atmosphere 1976](https://ntrs.nasa.gov/api/citations/19770009539/downloads/19770009539.pdf) | NOAA-S/T 76-1562 / NASA-TM-X-74335; s.8 Eq.(15)–(19) | MSL-zero geopotential, geometric Z, etkin radius |
| STD-VISC `[LITERATURE]` | [NASA/TP-2006-213486](https://ntrs.nasa.gov/api/citations/20060053240/downloads/20060053240.pdf?attachment=true) | Dynamic-viscosity bağıntısı | `mu=1.458e-6 T^(3/2)/(T+110.4)`; OR doğrusal yaklaşımından ayrı provenance |
| STD-WGS `[LITERATURE]` | [NGA WGS84 standard](https://earth-info.nga.mil/php/download.php?file=coord-wgs84) | NGA.STND.0036_1.0.0_WGS84, 2014-07-08; Tables 3.1/3.6, §4, Fig.4.2 | Defining/derived constants, geodetic height, centrifugal içeren normal gravity |
| STD-WGS-WEB `[LITERATURE]` | [NGA WGS84 definition](https://earth-info.nga.mil/?action=wgs84&dir=wgs84) | Defining parameters; erişim 2026-09-05 | a, 1/f, GM, Omega; geoid ve ellipsoid farklı yüzeyler |
| STD-ROT `[LITERATURE]` | [MIT Coriolis tutorial](https://live.ocw.mit.edu/courses/res-12-001-topics-in-fluid-dynamics-fall-2024/mitres_12_001_f24_essay3_pt1.pdf) | Fall 2024; Eq.(27)–(28), (43)–(44) | Coriolis hızının rotating-frame-relative olması |
| SRC-ATM | [ExtendedISAModel.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/models/atmosphere/ExtendedISAModel.java) | `getExactConditions`, `calculatePressure`, `basePressure` | Layer knots, constants, 1 m aşağıdan base-pressure recursion |
| SRC-CACHE | [InterpolatingAtmosphericModel.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/models/atmosphere/InterpolatingAtmosphericModel.java) | `computeLayers`, `getConditions` | 500 m cache ve gerçek public clamp |
| SRC-AIR | [AtmosphericConditions.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/models/atmosphere/AtmosphericConditions.java) | `getDensity`, `getMachSpeed`, `getKinematicViscosity` | R, gamma, doğrusal sound/viscosity |
| SRC-PINK | [PinkNoiseWindModel.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/models/wind/PinkNoiseWindModel.java) | constructor, `getWindVelocity`, `reset`, `clone`, `loadFrom` | Time sampling, seed XOR, rewind/replay, clone state |
| SRC-FILTER | [PinkNoise.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/util/PinkNoise.java) | constructor / `nextValue` | `java.util.Random.nextGaussian`, IIR history, warm-up |
| SRC-MULTI | [MultiLevelPinkNoiseWindModel.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/models/wind/MultiLevelPinkNoiseWindModel.java) | `addWindLevel`, `getWindVelocity`, `LevelWindModel.setAltitude` | MSL/AGL, sorted insertion, vector interpolation, mutable-level caveat |
| SRC-UI | [SimulationConditionsPanel.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/swing/src/main/java/info/openrocket/swing/gui/simulation/SimulationConditionsPanel.java) and [messages.properties](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/resources/l10n/messages.properties) | `addAverageWindSettings`; `simedtdlg.lbl.ttip.Winddirection` | Direct direction binding, degree display; 0 FROM North, 90 FROM East |
| SRC-STEP | [AbstractSimulationStepper.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/simulation/AbstractSimulationStepper.java) | `modelWindVelocity`, `modelAtmosphericConditions`, flight conditions | Altitude AGL+site; relative-flow chain ADDS returned OR wind vector |
| SRC-RK | [RK4SimulationStepper.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/simulation/RK4SimulationStepper.java) | `computeParameters`, Coriolis call | k2/k3 same time; Coriolis receives rocket velocity/world position |
| SRC-OPTIONS | [SimulationOptions.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/simulation/SimulationOptions.java) | constructor, `setRandomSeed`, `toSimulationConditions`, `clone` | Default random seed, model construction and cloning; reseed caveat |
| SRC-GRAV | [WGSGravityModel.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/models/gravity/WGSGravityModel.java) | surface/height gravity | Normal gravity numeric values and spherical height correction |
| SRC-GEO | [GeodeticComputationStrategy.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/util/GeodeticComputationStrategy.java) and [WorldCoordinate.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/main/java/info/openrocket/core/util/WorldCoordinate.java) | FLAT/SPHERICAL/WGS84; common Coriolis method | ENU, radius/rotation, Vincenty, unexplained east-sign reversal |
| TEST-WIND | [PinkWindModelTest.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/test/java/info/openrocket/core/models/wind/PinkWindModelTest.java), [MultiLevelWindModelTest.java](https://github.com/openrocket/openrocket/blob/133b558de556f0282f51c3f53f3b3fc633b4310a/core/src/test/java/info/openrocket/core/models/wind/MultiLevelWindModelTest.java) | same pinned version; duplicate/interpolation/clone tests | Coverage evidence; bit-exact stream veya clone RNG isolation kanıtı değildir |

### ISA layers ve atmosphere domain closure

DOC Table 4.1 aynen sayısal veri; sıcaklık K'ye, lapse K/m'ye yalnız birimsel
olarak çevrildi. Top h bir sonraki satırın base height'ıdır. Son satır yeni
bir extrapolation layer değil terminal knot'tur. Tablo density değeri vermez.

| Layer | Base h (geopotential m) | Top h (m) | T_b (K) | L (K/m) | p_b (Pa) | Source | Status |
|---|---:|---:|---:|---:|---:|---|---|
| 0 | 0 | 11000 | 288.15 | -0.0065 | 101325 | DOC Table 4.1 | RESOLVED source |
| 1 | 11000 | 20000 | 216.65 | 0 | 22632 | DOC Table 4.1 | RESOLVED source |
| 2 | 20000 | 32000 | 216.65 | 0.001 | 5474.9 | DOC Table 4.1 | RESOLVED source |
| 3 | 32000 | 47000 | 228.65 | 0.0028 | 868.02 | DOC Table 4.1 | RESOLVED source |
| 4 | 47000 | 51000 | 270.65 | 0 | 110.91 | DOC Table 4.1 | RESOLVED source |
| 5 | 51000 | 71000 | 270.65 | -0.0028 | 66.939 | DOC Table 4.1 | RESOLVED source |
| 6 | 71000 | 84852 | 214.65 | -0.002 | 3.9564 | DOC Table 4.1 | RESOLVED source |
| terminal 7 | 84852 | — | 186.95 | — | 0.3734 | DOC Table 4.1 | RESOLVED source |

| Axis | Kanıtlanan davranış | Native status |
|---|---|---|
| PHYSICAL VALIDITY | DOC §4.1.1'de 32 km, iki standardın T/p profillerinin aynı olduğu sınırdır; bütün atmosphere modellerinin fiziksel sonu değildir. Table 4.1 daha yukarı uzanır. Standard atmosphere gerçek günün hava durumu garantisi değildir | Altitude support profili ve accuracy bütçesi MODEL DECISION |
| OPENROCKET IMPLEMENTATION DOMAIN | SRC-ATM advertised max 84852 m; knots DOC ile aynı. Kodda >32 km için yaklaşık %5 fark TODO'su var | OR-LIMITATION; standard doğruluğu garantisi değil |
| Actual public path | SRC-CACHE DELTA=500 m, `ceil(84852/500)=170` samples: 0..84500 m. `altitude<=0` ilk; `altitude>=84500` son cached değer; arada T/p doğrusal interpolate | RESOLVED parity; Native clamp kararı yapılmadı |
| Exact layer selection | SRC-ATM ilk `layer[i+1]>altitude` aralığını seçer; tam iç knot üst layer'a gider | RESOLVED parity; Native endpoint kuralı ayrıca freeze edilmeli |
| Exact pressure construction | Üst base pressure `getExactConditions(layer[i]-1)` ile hesaplanır; DOC tablosu basınçlarının aynısı depolanmaz | 1 m aşağıdan recursion parity farkı; fizik standardına taşınmayacak bir adoption kararı henüz yok |
| Exact top caveat | Protected exact yöntem top knot'a kadar clamp edip `startLayer+1` okur; terminal knot'ta range riski. Normal public cache bu knot'u üretmez | Statik bulgu; Java reproduction yapılmadı |
| Equation branch | SRC-ATM isothermal branch için `abs(tempRate)<1e-6` kullanır | Local v1 exact L=0 formuyla karıştırılmaz; Native epsilon eklenmedi |
| Below/above/invalid Native input | Local v1 error/clamp/extrapolate seçmiyor; OR cache uygulaması physical validity değildir | ENV-OPEN-005 halen MODEL DECISION; B/C/E blocker |

### Atmosphere constants ve air-properties parity

| Constant / equation | Value | Unit | Used by / source | Status |
|---|---|---|---|---|
| R_air | 287.053 | J/(kg K) | SRC-AIR density/pressure | Exact OR değeri RESOLVED; authoritative-standard precision eşitliği bu auditte kapatılmadı |
| gamma | 1.4 | 1 | DOC symbols; SRC-AIR GAMMA | RESOLVED source |
| g0 | 9.80665 | m/s² | SRC-ATM; STD-ATM Eq.(17) | İki kaynak uyumlu |
| T_sea / p_sea | 288.15 / 101325 | K / Pa | DOC Table4.1; SRC-ATM | RESOLVED source |
| Default AtmosphericConditions temperature | 293.15 | K | SRC-AIR default constructor constant | ISA T_sea ile aynı değil; fark korunur |
| atmosphere effective R_e | 6356766 | m | STD-ATM Eq.(17)–(19) | RESOLVED physical source; OR atmosphere conversion uygulamaz |
| OR sound speed | `165.77+0.606*T` | m/s, T in K | SRC-AIR getMachSpeed | Lineer yaklaşım; local v1 ATM-006 sqrt(gamma R T) değil |
| OR dynamic viscosity numerator | `3.7291e-6+4.9944e-8*T` | Pa s, T in K | SRC-AIR getKinematicViscosity | Sutherland'ın doğrusal yaklaşımı; nu=bu değer/rho |
| Sutherland beta / S | 1.458e-6 / 110.4 | kg/(m s sqrt(K)) / K | STD-VISC | Physical provenance RESOLVED; OR'de mu0/T0/S seti bulunmadı |
| Sutherland equation | `mu=beta*T^(3/2)/(T+S)` | Pa s | STD-VISC | Local v1 ATM-007 ile referanslı biçimde eşdeğer |
| mu0,T0 parametrization | `mu0=beta*T0^(3/2)/(T0+S)` | Pa s / K | Yukarıdaki bağıntının cebirsel yeniden yazımı | T0 seçimi/numeric rounding MODEL DECISION; kaynakta olmayan bağımsız mu0 icat edilmedi |

SRC-AIR yorumları: sound yaklaşımı -30..30 °C'de 0.5 m/s, -55..30 °C'de
2 m/s doğruluk; viscosity linear approximation -40..40 °C. Bunlar runtime
reject guard değil yaklaşım yorumlarıdır. Nonlinear Sutherland'ın kabul edilecek
Native sıcaklık domain'i ve reference fixture hassasiyeti D/E öncesi açık.
DOC humidity'yi ihmal eder; stable incelenen air-properties yoluna unreleased
`baseRelativeHumidity` veya humidity correction eklenmedi. İlk auditin
unreleased humidity maddesi ayrı OPEN-PARITY olarak kalır.

### ATM-009 height semantics ve datum chain

**INPUT HEIGHT SEMANTICS:** STD-ATM s.8 Eq.(14)–(19) `Z` geometric altitude'tur;
referans mean sea level ve oradaki sıfır potansiyeldir. Bu standardın idealize
geometric altitude'ı WGS ellipsoidal height veya genel bir `position.z` değildir.
NASA [NDARC theory, Operation s.33](https://rotorcraft.arc.nasa.gov/Publications/files/NDARCTheory_v1_6_938.pdf)
de geometric input'u mean sea level üzerinde tanımlar.

**GEOPOTENTIAL HEIGHT SEMANTICS:** `H=Phi/g0'`; STD-ATM Eq.(18)
`H=Gamma*r0*Z/(r0+Z)`, `Gamma=g0/g0'=1 geopotential m/geometric m`.
SI metre sayılarıyla local v1 **ATM-009** `h_geopot=R_e*z/(R_e+z)` ile uyumlu.
Inverse STD-ATM Eq.(19): `Z=r0*H/(Gamma*r0-H)`; yeni local equation ID verilmedi.
`r0=6356766 m`, adopted sea-level gravity ile tutarlı **effective** radius'tur;
WGS a/b veya SRC-GEO mean Earth radius 6371000 ile değiştirilemez.

| Height | Reference / unit | Kullanım / conversion evidence | Durum |
|---|---|---|---|
| geometric Z (standard) | Idealized MSL; m | STD-ATM Eq.(18) -> H | RESOLVED physical semantics |
| geopotential H | MSL-zero geopotential; geopotential metre, SI numeric metre | DOC layer input; STD-ATM Eq.(15)/(18) | RESOLVED |
| launch-relative AGL | Launch origin/local ground; m | SRC-STEP position.z; wind/event yolu | Düz launch-relative anlam RESOLVED; terrain-following AGL değildir |
| OR MSL input | AGL + launch-site altitude; m | SRC-STEP atmosphere/wind calls | RESOLVED parity; bu yolda geopotential conversion yok |
| ellipsoidal/geodetic h | Reference ellipsoid normalinden; m | STD-WGS Fig.4.2 gravity datum | MSL ile özdeş değil |
| actual MSL/orthometric height | Gravity/geoid reference | NGA geoid ile ellipsoid ayrımı | Native vertical datum/geoid data contract henüz seçilmedi |

```text
standard geometric Z (idealized MSL) -- STD-ATM Eq.(18), r0 --> H --> DOC layers
launch-relative AGL + same-reference launch elevation -- SRC-STEP --> OR MSL input
ellipsoidal h -- [conversion/data contract NOT FROZEN] -- actual MSL / standard Z
AGL -- local v1 GEO10 / SRC-STEP --> ground/event reference; optional wind profile
```

Son şemadaki boşluk dönüşüm değildir; geoid modeli, terrain/datum veya h=MSL
varsayımı eklenmedi. Pure B helper'ın yalnız standard-Z kabul edip diğer
datumlardan dönüşümü kapsam dışı bırakması **öneridir**, henüz contract kararı değil.
`Z=-r0` / inverse `H=Gamma*r0` matematiksel singularity'dir; bu gözlem supported
physical domain veya exception policy yerine geçmez. NaN/inf NAT-004 policy'si
korunur; finite negatif/üst-sınır altitude seçimi ENV-OPEN-005'te açıktır.

### Wind FROM/TOWARD boundary closure

SRC-UI tooltip FROM North=0°, FROM East=90°; North referanslı clockwise bearing.
UI angle-unit katmanı degree display'i radian `Direction` alanına dönüştürür;
doğrudan binding'de **pi ekleme yoktur**. SRC-PINK pozitif speed U için
`W_OR=[U*sin(theta_from),U*cos(theta_from),0]` döndürür. SRC-STEP bunu rocket
velocity'ye **ekler**. Dolayısıyla bu OR output'u gerçek air-mass TOWARD velocity
olarak doğrudan Native contract'a kopyalanamaz.

Local v1 WIND-009 Native toward-angle convention'ı korunur:
`V_air=[U*sin(psi_toward),U*cos(psi_toward),0]`. Source/consumer zincirinden
çıkarılan future parity boundary: `V_air=-W_OR`; nonnegative bearing-speed
temsili için `psi_toward=(theta_from+pi) mod 2pi`. Bu formül OR UI'de mevcut
bir conversion diye sunulmaz; Native gerçek air-mass tanımı ile OR consumer'ın
eşdeğerliğinden türetilir. Yeni adapter implement edilmedi.

| External FROM | theta_from | Native air-mass WORLD_ENU / U | Direction |
|---|---|---|---|
| North | 0 | [0,-1,0] | South |
| East | pi/2 | [-1,0,0] | West |
| South | pi | [0,1,0] | North |
| West | 3pi/2 | [1,0,0] | East |

| Internal psi_toward | Native WORLD_ENU / U | Existing fixture |
|---|---|---|
| 0 | [0,1,0] | WIND-T02 |
| pi/2 | [1,0,0] | WIND-T03 |
| pi | [0,-1,0] | Yeni boundary fixture gerekli; ID atanmadı |
| 3pi/2 | [-1,0,0] | Yeni boundary fixture gerekli; ID atanmadı |

İlk auditin `WIND-009 [CURRENT-SRC] RESOLVED internal` satırı tek başına yanlış
parity beklentisi yaratır: local Native convention resolved, raw OR output ile
eşitlik **değil**. Negative instantaneous stochastic speed yönü ters çevirebilir;
signed scalar'ın her zaman pozitif wind magnitude olduğu varsayılmadı.

### Multi-level stable-24.12 closure

| Behavior | SRC-MULTI / TEST-WIND evidence | Status / Native boundary |
|---|---|---|
| Altitude basis | Default MSL; explicit AltitudeReference ile AGL seçimi, three-argument dispatch | Source RESOLVED; Native config seçimi I öncesi |
| Structure | Sorted list of altitude + own PinkNoiseWindModel | Source RESOLVED |
| Ordering / duplicate | addWindLevel binarySearch sorted insertion; same comparator altitude -> IllegalArgumentException | TEST-WIND duplicate test destekli |
| Mutated level | LevelWindModel.setAltitude yalnız field/event; sort veya duplicate guard yapmaz; sortLevels ayrı | Native untrusted input policy MODEL DECISION; insertion guarantee genellenmez |
| Interior | f=(h-h_low)/(h_high-h_low); instantaneous Cartesian vectors `(1-f)*W_low+f*W_high` | Direction angles interpolate edilmez; wrap problemi bu yolda yok |
| Bounds | Below first / above last -> outer level's time-dependent wind | Constant-in-height extension, zero veya slope extrapolation değil |
| Exact level / single | Exact level doğrudan; single tüm heights için aynı level | RESOLVED source |
| Empty | Explicit empty list -> Coordinate.ZERO; constructor normalde initial level ekler | Empty behavior RESOLVED |
| Vertical | Underlying pink model z=0; interpolated z=0 | Baseline limitation; vertical wind eklenmedi |
| Invalid | Finite altitude için genel guard yok; setters negative speed/std üzerinde düzeltme yapabilir; CSV parser ayrı errors üretir | Bunlar Native silent-fallback izni değildir; I contract validation açık |
| Seeds | Her level default constructor ile kendi random seed'ini alır | Global simulation seed ile deterministic profile iddiası desteklenmez |

### Pink-noise, PRNG ve RK4 coupling closure

DOC §4.1.2 Eq.(4.5)/(4.6) ve basılı s.62 algorithm:
Gaussian white input -> two-memory IIR -> unscaled result / 2.252 -> wind speed;
`U_n=U_mean+sigma*x_normalized,n`. Yerel WIND-004/WIND-008 iki U_n yazımı
bu sırayla okunmalıdır; sigma iki kez veya scaling atlanarak uygulanmaz.
Bu **DOCUMENTATION ISSUE** local ID'ler değiştirilmeden kaydedilir.

| Item | Source evidence | Closure |
|---|---|---|
| alpha / poles | SRC-PINK 5/3 / 2; DOC IIR | RESOLVED source |
| Frequency / period | DOC and SRC-PINK DELTA_T=0.05 s -> 20 Hz | RESOLVED; simulation timestep ile aynı parametre değil |
| Interpolation | Samples at n*0.05; adjacent scalar samples linear interpolate | RESOLVED DOC + SRC-PINK |
| PRNG | SRC-FILTER `java.util.Random.nextGaussian()` | Exact Java API identified; Python RNG eşdeğerliği/bitstream kanıtlanmadı |
| Filter initialization | Zero history; coefficient recurrence; 5*poles=10 warm-up nextValue calls | RESOLVED source; coefficient/order parity J1 fixture gerekli |
| Supplied seed | PinkNoiseWindModel(int) stores `seed_input XOR 0x7343AA03`; reset constructs Random(stored_seed) | RESOLVED source; mask fiziksel constant değil parity metadata |
| Default seed | Pink model no-arg constructor uses new Random().nextInt; simulation options also default random seed | Default runs aynı seed garantisi değil |
| Ownership | Each model owns lazy randomSource; first evaluation initializes two samples at t=0 | RESOLVED for independent model |
| Advancement | while time1+0.05 < requested time, shift/generate; t between endpoints interpolate | Physical-time bracket controls generation, her evaluation başına draw yok |
| Backward evaluation/reset | t<time1 resets randomSource and replays from same stored seed; t<0 throws | Same seed/replay source logic RESOLVED; performance limitation |
| Exact sample index | time1 repeated floating addition; strict `<` endpoint predicate | Integer floor sampler ile bit-exact parity varsayılmaz |
| Same-time | Unmodified independent model, same t -> same stored/interpolated result | Source-level RESOLVED; cross-language numeric fixture deferred J2 |
| k2/k3 | SRC-RK same physical t; same pink model configuration -> same scalar sample | Source-level RESOLVED, full multi-level wind height değişirse farklı olabilir |
| Changed timestep | Same model/seed and same queried t için scalar realization'ın sampling grid'i değişmez | Different query times/height trajectory doğal farklı değerlerdir; filter dt sabit |
| Clone/copy | SRC-PINK super.clone + loadFrom copies parameters; initialized randomSource reference deep-copied değil | RNG sharing riski OPEN-PARITY; parameter-only clone test bunu kapatmıyor |
| Reseed/restart | SimulationOptions setRandomSeed yalnız field değiştirir; existing model rebuild/reset etmez; toSimulationConditions wind clone eder | Restart/model life-cycle için end-to-end guarantee OPEN-PARITY |
| Multi-level reproducibility | Independent default seeds + clone chain | User seed -> all levels mapping Native MODEL DECISION |

**OPEN — STOCHASTIC TIME/SEED POLICY**, artık sampling period bilinmediği için
değil, Native sampler ownership, seed mapping, restart ve Java bit-parity hedefi
seçilmediği için kalır. J1/J2/K'yi etkiler; **B'yi etkilemez**. Desired Native
same-time invariant mevcut bağımsız scalar sampler davranışıyla uyumludur;
clone/restart caveat'leri sessizce kopyalanmayacak veya düzeltilmeyecektir.

### WGS constants / height / Coriolis closure

| Constant | Physical standard value / definition | OR24.12 parity | Status |
|---|---|---|---|
| a | 6378137 m; semi-major axis (STD-WGS-WEB) | SRC-GEO same | RESOLVED |
| 1/f | 298.257223563; inverse flattening | SRC-GEO 298.25722210088 | DIFFERENCE; Native adoption açık |
| b | a*(1-f), semi-minor axis; numeric value bu tabloda derived formda | SRC-GEO ellipsoid calculation | Definition RESOLVED; magic constant yok |
| e² | f*(2-f) | SRC-GRAV uses 0.00669437999013 | Definition RESOLVED; rounding/version boundary korunur |
| GM | 3.986004418e14 m³/s² including atmosphere | SRC-GRAV directly kullanmaz | STD-WGS defining constant RESOLVED |
| Omega | 7.292115e-5 rad/s | WorldCoordinate.EROT same | RESOLVED |
| g_e | 9.7803253359 m/s² (STD-WGS Table3.6) | 9.7803267714 | DIFFERENCE |
| k | 1.931852652458e-3; Somigliana constant (Table3.6) | 0.00193185138639 | DIFFERENCE |
| g_p | 9.8321849379 m/s² (Table3.6); k=(b*g_p-a*g_e)/(a*g_e) | No need to invent missing stored constant | Physical provenance/definition RESOLVED |
| mean spherical R | Not WGS semi-axis; OR WorldCoordinate.REARTH=6371000 m | Height correction and spherical geometry | OR approximation; not ATM-009 r0 |

STD-WGS §4/Fig.4.2 normal gravity height is geodetic height above ellipsoid,
not arbitrary MSL input. SRC-GRAV consumes WorldCoordinate altitude with no
explicit datum conversion; SRC-STEP world/launch height convention is generic
site altitude. WGS-001..004 physical model cannot silently use that raw MSL
quantity. OR height correction `g=g_surface*(R/(R+h))²` is spherical, whereas
local v1 WGS expansion is a separate physical model. G1 model/precision/datum
decision remains open even though missing source constants are identified.

**Implementation Guard / invariant:** STD-WGS §4 pp.4-6/4-7 explicitly separates
pure attraction from the centrifugal terms already in normal gravity. WGS
normal gravity kullanılıyorsa ikinci centrifugal acceleration eklenmez.
Bu Warning veya Model Limitation değil composition guard'dır (INV-003/WV30).

STD-ROT rotating-frame mechanics ile local v1 GEO-001/WGS-005:
`a_C=-2*Omega_W cross V_W`, V Earth-relative rotating-frame velocity; air-relative
ve inertial velocity değildir. WORLD_ENU bazında geodetic latitude phi için
`Omega_W=[0,Omega*cos(phi),Omega*sin(phi)]`. Bundan cebirsel olarak:
`a_C=2*Omega*[V_N*sin(phi)-V_U*cos(phi), -V_E*sin(phi), V_E*cos(phi)]`.
Bu çıkarım physical standard + NAT-006 bazından gelir; Java output'unun çevirisi değildir.
Equator'da sin=0; poles'da cos=0; cross product kendi başına division singularity
içermez. Geodetic longitude/pole-frame construction guard ayrı G2 konusudur.

SRC-RK Coriolis'e rocketVelocity ve rocketWorldPosition verir; FLAT strategy
zero döndürür. SRC-GEO common method `v_e=-velocity.x` kullanır; yukarıdaki
standart ENU ifadesine göre eastward input'un North/Up işaretleri ters çıkar.
Kod yorumunun kendisi x reversal gerekçesini kesinleştirmiyor. **OPEN-PARITY /
DOCUMENTATION-MODEL DIFFERENCE**: statik sign mismatch; Native fizik standardı
Java işaretine uydurulmadı, Java davranışının doğruluğu için yeni test yazılmadı.
Local tangent approximation ile uzun mesafe geodetic transport aynı şey değildir;
polar thresholds ve supported region G2 öncesi kararlaştırılmalıdır.

### GEO ID collision ve local v1 düzeltme önerileri

Local DOCX §6.5 ve Appendix G.3 tablo hücreleri yeniden okundu:
`GEO01..GEO12` **register** namespace'i iki ailede tekrar kullanılıyor.
Örnekler: GEO01 component_tree / latitude; GEO02 component_id / longitude;
GEO03 parent_id / height_msl; GEO10 instance_transforms / agl_altitude;
GEO11 transform_component_to_body / launch_site; GEO12 component_active /
geodetic_validity. Semantic olarak farklı kayıtlar; yalnız yazım hatası sayılıp
birbirine alias edilemez. Equation `GEO-001` ve `GEO-ENV-001` bundan ayrı ID'lerdir.

Çözüm önerisi: future mapping key `(source section, family, original ID)` olsun;
ör. `(6.5, Geometry, GEO01)` / `(G.3, Geodetic, GEO01)`. Bu audit evidence'ında
section kullanıldı; local equation/register ID yeniden numaralandırılmadı.
Kalıcı namespace düzeltmesi documentation issue olarak G2/K öncesi açık.

Local v1'e sonradan onaylı eklenmesi gerekenler: layer table/source precision;
ATM-009 Z/r0 definition ve contract/domain policy; ATM-006/007 fizik standardı
ile OR linear approximations farkı; WIND-009 FROM consumer sign boundary;
WIND-004/008 normalized-sample ayrımı; WGS constants/datum/parity farkları;
Coriolis east-sign mismatch; seed/clone/restart semantics; qualified GEO IDs.
Bu görevde ana teknik rapora hiçbiri yazılmadı.

### Updated traceability ve subgate recommendation

| Local ID / requirement | Physical / parity evidence | Planned gate | Existing V&V / eksik kanıt |
|---|---|---|---|
| CONV-018, ATM-009 | STD-ATM Eq.(15)–(19), SRC-STEP | B | Datum/domain/singularity fixtures henüz yok |
| ATM-001..005 | DOC Table4.1, SRC-ATM/CACHE/AIR | C/E | ATM-T01/02/03/06; exact-knot/cache differences için yeni parity fixture gerekli |
| ATM-006..008 | STD-VISC versus SRC-AIR | D/E | ATM-T09; numeric sound/mu reference ve domain fixtures gerekli |
| GRAV-001 | local v1 constant baseline | F | DYN-T03 ancak future dynamics ile tamamlanır |
| WGS-001..004, INV-003/WV30 | STD-WGS versus SRC-GRAV | G1/K | GRV-T01/02/03, WARN-T04 |
| GEO-001..003, WGS-005..008 | STD-ROT, STD-WGS versus SRC-GEO/RK | G2 | GEO-T08; eastward/pole/equator Coriolis parity fixture gerekli |
| WIND-009 | Local toward convention + SRC-UI/PINK/STEP consumer | H | WIND-T01/02/03; dört FROM boundary fixture gerekli |
| WIND-010 | SRC-MULTI, TEST-WIND | I | WIND-T06; bounds/duplicates/invalid/empty fixtures gerekli |
| WIND-001..008 | DOC §4.1.2, SRC-FILTER/PINK | J1 | Raw/filter/normalized fixture gerekli |
| Seed/time invariant | SRC-PINK/OPTIONS/MULTI/RK | J2 | WIND-T04; clone/restart/same-time fixture gerekli |
| ENV01..18 | Local v1; enabled submodels only | K | Composition V&V; future FlightConditions ayrımı korunur |

Önerilen split **uygun**: G1 WGS gravity, G2 geodetic/Coriolis; J1 filter parity,
J2 deterministic time/seed sampler. B->C->D->E; F bağımsız; H->I, I ayrıca altitude
contract'a bağlı. J1 fiziksel sampling/filter kaynağını, J2 yaşam döngüsünü kapatır;
stochastic multi-level acceptance I/J2 birlikte gerektirir. K yalnız etkin
modeller tamamlanınca kapanır. Yeni subgate register satırları eklenmedi.
Unreleased humidity/vertical-turbulence ayrı kalır; stable baseline'a karışmaz.

### Updated blocker table — current status overrides historical audit

| ID | Previous status | New status | Source / needed decision | Blocks gate |
|---|---|---|---|---|
| ENV-OPEN-001 | OPEN z/radius | RESOLVED physical relation; PARTIAL Native contract | STD-ATM: Z/r0 exact; explicit input contract adoption gerekli | B |
| ENV-OPEN-002 | OPEN datums | MODEL DECISION: ellipsoid != MSL; bridge veya explicit scope exclusion gerekli | STD-WGS, STD-ATM, SRC-STEP; ground/geoid data seçilmedi | B contract; G1/G2/I/K conversions |
| ENV-OPEN-003 | OPEN layers/R | RESOLVED source table and OR R; Native precision/boundary MODEL DECISION | DOC, SRC-ATM/AIR/CACHE; rounded table vs recursive base p | C/E (B'ye layer implementation bağımlılığı yok) |
| ENV-OPEN-004 | OPEN viscosity | PARTIAL: exact OR linear implementation + physical beta/S bulundu | STD-VISC/SRC-AIR; mu0/T0 parametrization/domain/reference accuracy | D/E |
| ENV-OPEN-005 | OPEN domain policy | MODEL DECISION — unresolved Native action | OR clamp belirlendi, fizik standardı error/clamp seçmiyor | **B/C/E** |
| ENV-OPEN-006 | OPEN multi-level | RESOLVED parity; MODEL DECISION validation/config | SRC-MULTI/TEST-WIND; finite/mutation/altitude-reference policy | I |
| ENV-OPEN-007 | OPEN FROM/TOWARD | RESOLVED source/consumer boundary; first-audit attribution corrected | SRC-UI/PINK/STEP; future adapter sign conversion | H boundary fixtures / future adapter; B değil |
| ENV-OPEN-008 | OPEN stochastic time | RESOLVED fixed-time; OPEN-PARITY clone/restart + MODEL DECISION seed ownership | DOC/PINK/FILTER/OPTIONS/MULTI/RK | J1/J2/K; B değil |
| ENV-OPEN-009 | OPEN WGS constants/datum | RESOLVED standards; MODEL DECISION OR differences/datum/height model | STD-WGS versus SRC-GRAV/GEO | G1 |
| ENV-OPEN-010 | OPEN Coriolis semantics | RESOLVED physical V/frame; OPEN-PARITY east-sign and polar policy | STD-ROT versus SRC-GEO/RK | G2 |
| ENV-OPEN-011 | OPEN collision | DOCUMENTATION ISSUE confirmed; section-qualified evidence used | Local v1 §6.5/G.3; canonical mapping approval needed | G2/K mapping; B değil |
| ENV-OPEN-012 | OPEN U_n scaling | RESOLVED source chain; DOCUMENTATION ISSUE local notation | DOC §4.1.2; SRC-FILTER/PINK | J1 traceability |
| OR-OPEN-ENV-001 | OPEN humidity | OPEN-PARITY unreleased; intentionally not mixed | Local v1 current notes; no current-development audit here | Deferred humidity only |
| OR-OPEN-ENV-002 | OPEN sound/viscosity | RESOLVED stable24.12; unreleased not asserted | SRC-AIR | D/E model/parity selection |
| OR-OPEN-ENV-003 | OPEN WGS formula | RESOLVED stable24.12; standard difference explicit | SRC-GRAV/STD-WGS | G1 model selection |
| OR-OPEN-ENV-004 | OPEN geodetic transform | RESOLVED stable strategy outline/constants; sign/datum scope open | SRC-GEO/STD-WGS/STD-ROT | G2 |

### NAT-009B minimum GO assessment / final gate

| Criterion | Result |
|---|---|
| Geometric/geopotential relation, Z, R_e source | PASS source closure |
| Relevant altitude types distinguished | PASS definitions |
| Native datum chain / explicit accepted-input contract sufficient | OPEN — standard-Z-only scope veya bridge henüz onaylanmadı |
| Finite negative / out-of-domain action decided | OPEN — source-backed OR behavior Native physical policy seçmez |
| Equation/constants known | PASS ATM-009 source; adoption/domain contract incomplete |
| Future-gate blockers kept separate | PASS — wind/RNG/WGS details alone do not block B |

**NAT-009A.2 source audit tamamlandı; freeze henüz implementation-ready değil.**
**NAT-009A: OPEN / BLOCKED. NAT-009B: NO-GO.**
Kritik kalan seçimler ENV-OPEN-001/002 contract ve ENV-OPEN-005 domain/error
policy'dir. Standard-Z-only B scope onaylanırsa WGS/geoid dönüşüm işleri
G/K'ye taşınabilir; otomatik scope/model kararı yapılmadı. Register değişmedi.

Regression: proje `.venv` interpreter'ı ile `python -m pytest tests -q`:
**150 passed in 2.73s**, exit code 0 (2026-09-05). Yeni runtime test eklenmedi.
NAT-003..008 mevcut testleri bu full-suite sonucuna dahildir.
Production `src` altındaki **8 Python dosyasının SHA-256 değerleri görev öncesi
ve sonrası birebir aynı**. Yeni dependency yok; pyproject/register/test kaynakları
değiştirilmedi. Yalnız bu freeze belgesi güncellendi.

## NAT-009B — Frozen karar kapanışı (2026-09-06)

NAT-009B kullanıcı talebi NAT-009A'yı CLOSED / ACCEPTED ilan eder ve önceki
belirsiz altitude ifadelerini aşağıdaki kararlarla geçersiz kılar. Tarihsel
audit silinmedi; A yeniden açılmadı.

- ENV-OPEN-001/002, B kapsamı: supplied scalar girdilerle `h_ellip=H_orth+N`,
  `AGL=H_orth-H_ground`; uyumlu vertical datum çağıranın sorumluluğunda.
- [MODEL-DECISION] Atmosfer köprüsü explicit fonksiyonda `Z_atm:=H_orth`.
  Bu bir evrensel fiziksel özdeşlik değildir.
- ENV-OPEN-005, B kapsamı: conversion hiçbir atmosphere domain clamp uygulamaz;
  negatif değerler ve 90 km geçerlidir. Exact sıfır payda ve non-finite
  girdi/sonuç `ValueError` üretir. Global tolerance eklenmez.
- ATM-009 / USSA76 Eq.(18)/(19): etkin radius `6_356_766.0 m`;
  WGS84 geometrik radius ile karıştırılmaz. Forward ve inverse uygulanır.
- `z_W` absolute altitude değildir. ECEF, geoid, terrain veya lookup yoktur.
  Dataclass/snapshot ve ikinci authoritative state gerekmediği için eklenmez.
- Production konumu `environment/altitude.py`; ALT-T01..ALT-T10 eşlemesi
  `tests/unit/environment/test_altitude.py` Türkçe docstring'lerindedir.
- Atmosphere domain/thermodynamics C/E; diğer açık WGS/wind/parity maddeleri
  önceki tabloda belirtilen future gate'lerde kalır. B'yi engellemezler.

NAT-009B doğrulaması: baseline **150 passed**; focused **57 passed**;
math/environment-adjacent **207 passed**; full suite **207 passed**.
**NAT-009B IMPLEMENTATION GATE: PASS.** Yeni dependency yok; mevcut math/units
production davranışı değiştirilmedi. NAT-009C'ye geçilmedi.

## NAT-009C — Dry-Air Atmosphere Core kabul kaydı (2026-09-06)

Frozen NAT-009C kullanıcı kararları bu provider için eski layer/domain
belirsizliklerini kapatır: USSA76 lower dry-air, geopotential input,
`[-5000, 84852] m` inclusive domain, T0=288.15 K, p0=101325 Pa,
g0=9.80665 m/s², R_air=287.053 J/(kg K). İlk base 0 m'dir; -5 km yeni
base değildir. Yedi lapse layer'ın sonraki T/p base'leri exact endpoint'ten
recursive türetilir; rounded OR basınç tablosu veya OR cache/clamp kullanılmaz.

`environment/atmosphere.py`: parametresiz `USStandardAtmosphere1976Lower`,
keyword-only `evaluate(geopotential_height_m=...)`, yalnız T/p/rho içeren
frozen slots `DryAirAtmosphereState`; layer metadata private immutable kalır.
Finite domain dışı girdi structured `AtmosphereDomainError(ValueError)`;
non-finite girdi mevcut NAT-004 `ValueError` yoludur. Clamp/extrapolation yok.
Result positivity/finite invariant'ları fallback olmadan denetlenir.

NAT-009C test ID'leri bu gate'in **ATM-T01..ATM-T16** matrisini ifade eder;
ilk source auditindeki fixture ID'leriyle (ör. viscosity ATM-T09) scope'suz
birleştirilmez. Kaynak audit kayıtları yeniden numaralandırılmadı.
`tests/unit/environment/test_atmosphere.py` docstring'leri gate eşlemesini içerir.
Referans bağıntılar testte 50-digit Decimal log-pressure hesabıyla doğrulanır;
production tablosu referans diye tekrar kullanılmaz. Exact knot dispatch için
tek dar private-call spy testi vardır; public layer index eklenmedi.

Baseline **207 passed**; focused **47 passed**; full suite **254 passed**
(207 mevcut + 47 yeni). **NAT-009C IMPLEMENTATION GATE: PASS.**
NAT-009B/math behavior değişmedi, dependency eklenmedi. Domain'i orkestrasyonun
nasıl ele alacağı sonraki katmana aittir; sound speed/viscosity NAT-009D'ye,
humidity/wind/gravity/geodesy diğer future gate'lere bırakıldı. NAT-009D başlamadı.
