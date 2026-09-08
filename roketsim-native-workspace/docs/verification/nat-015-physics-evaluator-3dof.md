# NAT-015 — PhysicsEvaluator 3DOF V1

Tarih: 2026-09-08. Başlangıç kabul edilmiş NAT-014 commit'i
`e995bf30c74d184e9e8e6ef48f6d50010c7aa5b3`; NAT-013
`20a999d803d5ba4a4b21c3c84f79a86b71408ce8` ancestry'de doğrulandı.
Çalışma ağacı temiz ve remote yoktu. Uygulama
`nat-015-physics-evaluator-3dof` dalında yapılmıştır.

## Orchestration/bridge sahipliği

`PhysicsEvaluator3DOF` `(time_s, state, static context)` girdisini current-state
instantaneous physics sonuçlarına ve NAT-014 derivative'ine bağlar. Parametresiz,
slotted/stateless, keyword-only, deterministic ve history/cache/timestep içermez.
Upstream formülleri sahiplenmez veya kopyalamaz.

Exact accepted API audit'i ve kullanım:

- NAT-009C `USStandardAtmosphere1976Lower.evaluate(geopotential_height_m=...)`
  -> `DryAirAtmosphereState`;
- NAT-009D `DryAirPropertiesCalculator.evaluate(atmosphere_state=...)`
  -> `DryAirProperties`;
- NAT-009F `ConstantGravityModel.evaluate()` -> WORLD acceleration vector;
- NAT-009H `NoWindModel` / `ConstantWindModel.evaluate()` -> WORLD airmass velocity;
- NAT-010A `RelativeFlowCalculator.evaluate(...)` -> WORLD `V_rocket-V_airmass`;
- NAT-010B `BasicFlightConditionsCalculator.evaluate(...)` -> V/M/Re/q;
- C.3A `MotorThrustCurveEvaluator` -> `MotorThrustState`;
- C.3B `MotorPropertyEvaluator` -> `MotorMassProperties`;
- C.3C `RocketMassPropertiesCalculator` -> `RocketMassProperties`;
- A.1 `BasicDragEvaluator` -> `BasicDragResult`;
- NAT-014 `TranslationalDynamicsEvaluator` -> `TranslationalDynamicsResult`.

Accepted domain/result nesnelerinin paralel kopyaları oluşturulmamıştır.

## Context ve policies

`PhysicsEvaluationContext3DOF` frozen/slotted, defaultsuz dependency/configuration
bundle'dır. Resolved Geometry, aerodynamic surfaces, structural mass, installed
motor, C.3B/A.1/NAT-014 profiles, position mapping, launch environment altitude,
propulsion timeline ve exact NAT-009 atmosphere/air/gravity/wind nesnelerini taşır.
Bu bundle bu nesnelerin physics authority'sini devralmaz.

`EnvironmentPositionMappingModel` V1'de yalnız
`LOCAL_ENU_VERTICAL_OFFSET` sunar; default yoktur. `PropulsionTimeline` mandatory,
finite `ignition_time_s` taşır.

## Environment mapping ve NAT-009 altitude semantiği

NAT-009C public atmosphere girdisi **geopotential height, metres** semantiğindedir.
Bu nedenle `launch_environment_altitude_m`, launch konumunda NAT-009C'nin beklediği
geopotential-height miktarıdır. NAT-015 yeni altitude type, MSL/geometric
reinterpretation veya geometric/geopotential dönüşüm eklemez.

Local WORLD ENU (+x East, +y North, +z Up) için:

`delta_z = state.position_world_m[2] - launch_conditions.initial_position_world_m[2]`

`environment_altitude_m = launch_environment_altitude_m + delta_z`

Launch WORLD origin'ın sıfır olduğu varsayılmaz; terrain/clamp/abs yoktur.
Pozitif fixture `1200 + (173.25-50) = 1323.25 m`, negatif fixture
`1200 + (20-50) = 1170 m` sonuçlarını verir. Mapped değer doğrudan accepted
NAT-009C API'sine iletilir; domain dışı durumda `AtmosphereDomainError` aynen
propagate edilir.

## Frozen evaluation order

Her çağrı current supplied state'ten şu sırayı yürütür:

1. local ENU altitude mapping;
2. atmosphere ve air properties;
3. gravity;
4. steady wind/airmass velocity;
5. NAT-010A relative flow;
6. NAT-010B flight conditions;
7. `motor_time_s = time_s - ignition_time_s`;
8. C.3A thrust;
9. C.3B motor mass/CG, aynı exact motor time ile;
10. C.3C total rocket mass/CG;
11. A.1 Basic Drag;
12. ephemeral NAT-014 `TranslationalDynamicsInputs` assembly;
13. NAT-014 force/derivative evaluation.

`time_s` finite olmak zorundadır fakat non-negative olmak zorunda değildir.
`time_s == ignition_time_s` motor time sıfır ile geçerlidir. Motor time negatifse
`BEFORE_IGNITION_UNSUPPORTED`; zero-thrust/held-mass fallback yoktur.
Pre-ignition/pre-launch timeline, guide ve event architecture ile post-demo tekrar
ele alınacaktır.

## Result ve authority korunumu

`PhysicsEvaluationResult3DOF` yalnız NAT-015 mapping outputs (`environment_altitude_m`,
`motor_time_s`), accepted upstream result objects, read-only airmass/relative-flow/
gravity vectors ve final NAT-014 result'ını taşır. Thrust/mass/Mach/Re/q/Cd0/
acceleration scalar aliases veya temporary `dynamics_inputs` saklanmaz.

NAT-015-owned stored NumPy vectors accepted defensive independent-copy + read-only
pattern'ını kullanır; caller/upstream arrays değiştirilmez. Upstream structured
errors körlemesine `PhysicsEvaluationError` ile wrap edilmez. Bu hata ailesi yalnız
NAT-015-owned mapping/timeline semantic hataları içindir.

## Critical-path sınırı

Static stability (`StaticAerodynamicEvaluator`, CNa, CP, static margin) derivative
kritik yoluna dahil değildir. A.1 drag Mach domain'inde geçerli bir derivative,
dar NAT-012B diagnostic domain'i nedeniyle durdurulmaz. Yalnız derivative-critical
physics PhysicsEvaluator3DOF V1'e dahildir.

Atmosphere, air properties, gravity, wind, relative flow, Mach/Re/q, thrust
interpolation, motor mass, total mass, drag correlations ve translational force
denklemleri kendi accepted modüllerinde kalır. Integrasyon/Euler/RK4, events,
rail, recovery, staging, finite-AoA/lift/side force/moment ve 6DOF uygulanmamıştır.

## V&V A-H

- A: nonzero launch z=50, current z=173.25 -> altitude 1323.25 m.
- B: nonzero launch z=50, current z=20 -> altitude 1170.0 m.
- C: time 6.43, ignition 5.00 -> motor time yaklaşık 1.43 s.
- D: time=ignition=5.0 -> exact motor time 0.0 s.
- E: 4.999 < 5.000 -> `BEFORE_IGNITION_UNSUPPORTED`; propulsion çağrılmadı.
- F: F50 motor time 0.012 s -> thrust exact 51.377 N; motor mass
  `0.08474793212175599 kg`; total mass `0.6355969349495034 kg`. Aynı exact
  0.012 s thrust ve property evaluator'larına iletildi.
- G: explicit `NoWindModel`, state velocity `(3,4,5)` -> NAT-010A relative flow
  `(3,4,5)`. Accepted nonzero `ConstantWindModel` ayrıca zero-wind hard-code
  olmadığını doğruladı.
- H: controlled accepted-output assembly mass=2, thrust=30, Vrel=0, q=0,
  A=.01, Cd0=.5, g=(0,0,-10) -> final NAT-014 velocity derivative `(0,0,5)`.

Focused NAT-015: **21 passed**. All new simulation tests: **21 passed**.
Doğrudan NAT-013/NAT-014 dynamics regression: **69 passed**. Gate talimatına göre
full repository pytest çalıştırılmadı.
