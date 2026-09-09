# NAT-022 — End-to-End Native 3DOF Trajectory V&V / First Demo Gate

Tarih: 2026-09-09. Exact accepted başlangıç NAT-021 commit'i
`e7b0f8308d1e23d14fc4229c3a14301f14d8d486`; çalışma ağacı temizdi ve remote
yoktu. Verification `nat-022-end-to-end-native-trajectory-vv` dalında yapıldı.
Production `src/roketsim_native/` altında hiçbir dosya değiştirilmedi.

## Amaç ve complete Native chain

NAT-022 yeni production physics eklemeden ilk gerçek complete Native trajectory'yi
integration/V&V olarak çalıştırır. Test mock veya stub kullanmadan şu accepted
zinciri bağlar:

1. NAT-013 `LaunchConditions3DOF`, `InitialStateBuilder`, translational state;
2. NAT-009 lower standard atmosphere, dry-air properties, constant gravity ve
   steady `NoWindModel`;
3. NAT-010A relative flow ve NAT-010B basic flight conditions;
4. NAT-011 geometry, structural mass, F50 installation, curve thrust/mass ve total
   rocket mass;
5. NAT-012A.1 Basic Drag V1;
6. NAT-014 translational dynamics;
7. NAT-015 `PhysicsEvaluator3DOF`;
8. NAT-016 fixed-step point/config ve NAT-017 classical RK4;
9. NAT-018 burnout/apogee/ground detection;
10. NAT-019 recorder, NAT-020 engine ve NAT-021 result facade.

NAT-012B static CNa/CP/margin derivative critical path'e sokulmamıştır. OpenRocket
Java trajectory parity'si bu gate'in acceptance authority'si değildir.

## Accepted demo fixture lineage

Repository'de consolidated production rocket definition yoktur. Test fixture,
NAT-011/NAT-012/NAT-015 test ve verification'larında kabul edilmiş exact domain
girdilerinden kurulmuştur; competing production definition eklenmemiştir:

- `SingleStageRocketGeometry`: 0.100 m airframe; 0.300 m hollow-shell conical nose
  (0.002 m wall); 0.700 m cylindrical body (0.002 m wall); four equally-spaced
  square trapezoidal fins (0.180/0.080 m chords, 0.120 m span, 0.050 m tip-LE
  offset, root LE x_geo 0.720 m, 0.003 m thickness); accepted 0.120 m motor mount,
  rings ve 0.005 m overhang; maximum-diameter reference policy.
- Materials: nose POLYSTYRENE; body/fins/mount/rings CARDBOARD. Accepted structural
  calculator sonucu kullanılır.
- Motor: production catalog `AEROTECH_F50_4T`, exact accepted installation ve
  `DEMO_MOTOR_PROPERTY_MODEL_PROFILE`.
- Surfaces: test-only zero-roughness synthetic smooth nose/body/fins assignment;
  `NATIVE_BASIC_DRAG_V1_PROFILE`.
- Environment: launch geopotential-height authority 1200.0 m; accepted
  `USStandardAtmosphere1976Lower`, `DryAirPropertiesCalculator`,
  `ConstantGravityModel`, `NoWindModel` ve local ENU vertical-offset mapping.
- Launch: WORLD origin `(0,0,0)`, zero initial velocity, direction WORLD `+z`.
- Timeline: explicit ignition time 0.0 s.
- Dynamics: `NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE`.
- Events: accepted curve-end burnout, WORLD-vz downward apogee, launch-z ground
  plane ve linear bracket localization policies.

F50 runtime burnout authority yalnız `MotorCurveAnalyzer` tarafından canonical
curve'den türetilen `MotorCurveStatistics.curve_end_time_s = 1.430 s` ve accepted
timeline ignition toplamıdır. Certification measured burn time, 1.301 s last
nonzero point, %5 end veya ejection delay kullanılmamıştır.

Primary `step_size_s=0.010` ve `maximum_steps=5000`; fine
`step_size_s=0.005` ve `maximum_steps=10000` değerleri yalnız
**[V&V FIXTURE — NOT A PRODUCTION DEFAULT]**'tır. İki coarse-step time toleransı
ve %1 apogee-height oranı yalnız **[NAT-022 V&V ACCEPTANCE POLICY]**'dir.

## Primary actual trajectory diagnostics

| Quantity | Actual accepted result |
|---|---:|
| Initial total rocket mass | 0.6357490028277474 kg |
| Initial motor mass | 0.0849 kg |
| Post-burn/final motor mass | 0.047 kg |
| Executed RK4 candidates | 1584 |
| Accepted samples | 1584 |
| Termination | `TERMINAL_EVENT` |
| Burnout time | 1.43 s |
| Burnout estimated WORLD z | 88.15789842420412 m |
| Burnout estimated vertical velocity | 87.92821395634027 m/s |
| Apogee time | 7.012303670973791 s |
| Apogee estimated WORLD z / launch-relative height | 279.5335684908227 m |
| Apogee estimated vertical velocity | 0.0 m/s |
| Ground time | 15.832349589466348 s |
| Ground estimated vertical velocity | -49.190189996430696 m/s |
| Ground interpolation fraction | 0.23495894666415096 |
| Last accepted sample time | 15.829999999999707 s |
| Last accepted WORLD z | 0.1155854393789068 m |
| Maximum recorded Mach | 0.27673174265848777 |
| Maximum recorded dynamic pressure | 4663.19556661582 Pa |
| Maximum recorded speed | 92.8134640033708 m/s |

Diagnostic maxima yalnız verification evidence'dır; `SimulationResult3DOF` API'sine
eklenmemiştir.

## Gate evidence

İlk propagated accepted sample `t=0.01 s`, WORLD z
`0.0006321201805853316 m` ve vertical velocity `0.23868281154447563 m/s` üretir.
Launch-direction displacement pozitiftir. Bu sonuç, pad/contact/Liftoff physics
eksik olsa da seçilen F50 demo approximation'ının ilk accepted stepte aşağı hareket
etmediğini kanıtlar; production Liftoff threshold veya patch eklenmemiştir.

Ignition ile burnout arasında positive thrust ve upward velocity taşıyan accepted
samples vardır. NAT-015-owned total mass daima pozitiftir, powered history
non-increasing'dir ve motor mass 0.0849 kg'dan 0.047 kg'a düşer. Burnout sonrası
ve apogee öncesi positive-vz coast samples vardır; dolayısıyla burnout apogee
değildir.

Apogee burnouttan sonra, launch/burnout WORLD z değerlerinin üstünde ve NAT-018
linear localization ile exact zero vertical velocity'dedir. Apogee ile ground
arasında negative-vz accepted samples ballistic descent'i kanıtlar. Recovery yoktur
ve yüksek ground vertical speed gate failure değildir.

Ground launch WORLD z=0 düzleminde, negative estimated vertical velocity ile
terminal occurrence'dır. Primary ground bracket interior'dır. Bu yüzden son
accepted sample `15.829999999999707 s` ve z `0.1155854393789068 m` iken localized
termination `15.832349589466348 s`'dir; event state accepted sample'a yükseltilmez
ve aşağı-ground candidate kaydedilmez. Bütün accepted sample z değerleri en az
launch z'dir.

Event history exact birer BURNOUT, APOGEE, GROUND içerir ve strict
`BURNOUT < APOGEE < GROUND` sırasındadır. Ground sonrası event yoktur. Sample times
strict artar; state ve inspected mass/q/Cd0/Mach/Re/gravity/thrust/acceleration
değerleri finite ve domain içindedir. Zero-wind vertical symmetry için x/y/vx/vy
normal floating-point precision'da sıfır kalır. Basic Drag V1 unsupported-Mach
failure oluşmamıştır.

Aynı primary configuration ikinci kez çalıştırıldığında termination reason, step ve
sample/event sayıları, event types/times/states ve bütün accepted point time/state
array'leri numerically identical olmuştur.

## Fixed-step sensitivity

| Metric | h=0.010 s | h=0.005 s | Difference / relative difference | Acceptance |
|---|---:|---:|---:|---:|
| Steps | 1584 | 3167 | — | both terminal |
| Samples | 1584 | 3167 | — | nonempty |
| Burnout time | 1.43 s | 1.43 s | 0 s | invariant |
| Apogee time | 7.012303670973791 s | 7.012358447782318 s | 0.00005477680852639111 s | ≤0.020 s |
| Ground time | 15.832349589466348 s | 15.832554278426914 s | 0.00020468896056513586 s | ≤0.020 s |
| Apogee height | 279.5335684908227 m | 279.54103485936486 m | 0.0026709382920917456% | ≤1% |

Fine run da exact birer burnout/apogee/ground, strict event order, upward first
step, no-below-ground accepted history ve terminal ground şartlarını geçmiştir.

## Known frozen V1 limitations

Simulation propulsion ignition'da başlar. Pad/contact reaction, Liftoff event,
launch guide/rail-clear, recovery/deployment yoktur; descent ballistiktir. Basic
Drag V1 yalnız accepted zero-AoA/subsonic scope'tadır. Model yalnız 3DOF translation
uygular; attitude, moments ve 6DOF yoktur. Wind yalnız steady modeldir. Integration
fixed-step classical RK4; event localization linear bracket'tır ve event
re-integration/dense output/root solve yoktur. OpenRocket trajectory parity iddiası
yoktur. Bunlar accidental omission değil frozen first-demo scope'udur.

## Test sonuçları ve sonuç

- Focused real NAT-022 integration: **9 passed**.
- All simulation regression: **97 passed**.
- Propulsion regression: **277 passed**.
- Aerodynamics regression: **106 passed**.
- Dynamics + numerics regression: **109 passed**.
- Full repository milestone audit: **1431 passed, 0 skipped, 0 failed**.

E2E-T01..T32 complete production chain, termination/result integrity, strict sample
ve event chronology, exact burnout, early/powered/coast/apogee/descent/ground/mass
semantics, symmetry/domain sanity, determinism, fine-step behavior, sensitivity,
zero production-physics change ve full audit ile karşılanmıştır.

FIRST NATIVE 3DOF DEMO BASELINE: VERIFIED

NAT-022 IMPLEMENTATION GATE: PASS
