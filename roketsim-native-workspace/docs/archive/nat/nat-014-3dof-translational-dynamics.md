# NAT-014 — 3DOF Translational Dynamics V1

Tarih: 2026-09-08. Başlangıç kabul edilmiş NAT-013 commit'i
`20a999d803d5ba4a4b21c3c84f79a86b71408ce8`; çalışma ağacı temiz ve remote
yoktu. Uygulama `nat-014-3dof-translational-dynamics` dalında yapılmıştır.

## NAT-013 ve vektör bağımlılığı

Kabul edilmiş `TranslationalState3DOF.position_world_m` /
`velocity_world_m_s` ile `LaunchConditions3DOF` initial position, initial
velocity ve validated `launch_direction_world_unit` sözleşmeleri aynen
kullanılır. NAT-013 direction girdisini normalize etmez ve defensive-copy +
read-only NumPy storage uygular. NAT-014 bu nesneleri yeniden tasarlamamış,
direction'ı tekrar normalize etmemiştir.

Yeni input/output vektörleri de accepted `math.vectors.as_vector(..., size=3)`
finite/copy semantics'ini kullanır ve stored/output kopyaları read-only yapar.
WORLD frame local ENU'dur: +x East, +y North, +z Up.

## Model profili ve authority sınırları

`TranslationalDynamicsModelProfile` frozen/slotted ve iki mandatory seçim taşır:

- `ThrustDirectionModel.FIXED_LAUNCH_DIRECTION`;
- `AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY`.

Default profile parametresi veya silent model substitution yoktur. Hazır
`NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE` sabiti explicit V1 seçimini temsil
eder; evaluator çağrısında profile yine mandatory'dir.

`TranslationalDynamicsInputs` frozen/slotted ephemeral transport snapshot'ıdır.
Mass, propulsion, relative flow, flight conditions, geometry, basic drag ve
environment tarafından zaten değerlendirilmiş sırasıyla mass, thrust, WORLD
relative velocity, q, reference area, Cd0 ve WORLD gravity acceleration değerlerini
taşır. Bunların physics authority'sini devralmaz veya hiçbirini yeniden hesaplamaz.
NAT-015 ileride bu snapshot'ı kuracaktır.

## Instantaneous point-mass denklemleri

V1 şu denklemleri uygular:

`F_thrust = thrust_N * launch_direction_world_unit`

`D = q * A_ref * Cd0`

Nonzero accepted `V_rel = V_rocket - V_airmass` için
`F_drag = -D * V_rel / |V_rel|`.

`F_gravity = mass_kg * gravity_acceleration_world_m_s2`

`F_net = F_thrust + F_drag + F_gravity`

`dr/dt = state.velocity_world_m_s`, `dv/dt = F_net / mass_kg`.

Instantaneous variable mass doğrudan denominator'da kullanılır. Ek `mdot*v`
terimi yoktur; supplied motor thrust propulsion momentum etkisinin otoritesidir.
Gravity direction hard-code edilmez; upstream WORLD acceleration aynen kullanılır.

## Exact zero-relative-flow davranışı

Epsilon/threshold yoktur. Exact zero `V_rel` ve `q=0` exact zero drag üretir.
Exact zero `V_rel` ile `q>0`, `INCONSISTENT_DRAG_INPUTS` structured hatasıdır.
Nonzero `V_rel` ile independently supplied `q=0` geçerlidir ve zero drag üretir.
Density veya q infer/recompute edilmez.

## Sonuç API'leri

`TranslationalForces3DOF` yalnız thrust, drag ve gravity contribution'larını
stored authority olarak taşır. `net_force_world_N` her erişimde bunların derived,
read-only toplamıdır. `TranslationalStateDerivative3DOF` yalnız position-rate ve
velocity-rate taşır. `TranslationalDynamicsResult` forces, derivative ve explicit
profile taşır; `acceleration_world_m_s2` ayrı stored authority değil, derivative
velocity-rate görünümüdür.

`TranslationalDynamicsEvaluator` parametresiz, slotted/stateless, keyword-only,
history/cache içermeyen deterministic instantaneous evaluator'dır. Timestep veya
integrasyon yapmaz ve girdileri değiştirmez.

Finite scalar domain ihlalleri `DynamicsEvaluationError` ve structured
`error_code`, `field_name`, `value` kullanır. Raw NaN/infinity generic
`ValueError` finite-validation yolundadır. Clamp, abs, fallback veya repair yoktur.

## Frozen V&V

| Fixture | Thrust force N | Drag force N | Gravity force N | Net force N | Acceleration m/s² |
| --- | --- | --- | --- | --- | --- |
| Ignition | (0,0,30) | (0,0,0) | (0,0,-20) | (0,0,10) | (0,0,5) |
| Powered ascent | (0,0,30) | (0,0,-0.5) | (0,0,-20) | (0,0,9.5) | (0,0,4.75) |
| Descent | (0,0,0) | (0,0,+0.5) | (0,0,-20) | (0,0,-19.5) | (0,0,-9.75) |
| True 3D | (6,0,8) | (-2.4,0,-3.2) | (0,0,-14.7) | (3.6,0,-9.9) | (2.4,0,-6.6) |

True-3D fixture fixed direction'ın WORLD z eksenine hard-code edilmediğini
doğrular. Ascent/descent fixtures drag yönünün signed relative flow ile otomatik
tersine döndüğünü gösterir.

## Scope ve test kanıtı

NAT-012B CNa, CP ve static margin V1 translation kuvvetlerine girmez; attitude/AoA
state'i yoktur. Atmosphere/altitude, wind/relative-flow, Mach/Re/q, Cd0, propulsion
curve/mass ve total mass evaluator içinde hesaplanmaz. PhysicsEvaluator NAT-015'e;
rail, timestep/Euler/RK4, events, attitude/quaternion/angular state, inertia,
moments ve lift/normal/side forces sonraki gate'lere ertelenmiştir.

Focused NAT-014: **43 passed**. All dynamics ve doğrudan NAT-013 regression:
**69 passed**. Gate talimatına göre full repository pytest çalıştırılmadı.
