# NAT-016 — Fixed-Step Numerical Foundation V1

Tarih: 2026-09-08. Başlangıç kabul edilmiş NAT-015 commit'i
`0293023bf79567312067605485fae0597bd2e398`; çalışma ağacı temiz ve remote
yoktu. Repository audit'inde mevcut bir numerical/integration namespace
bulunmadığı için tek yeni namespace `roketsim_native.numerics` oluşturuldu.
Uygulama `nat-016-fixed-step-numerical-foundation` dalında yapılmıştır.

## Kapsam ve sahiplik

NAT-016 yalnız fixed-step configuration, numerical `(t,y)` point ve ilerideki
RK4 ara state'leri için 3DOF state algebra sağlar. Bir integrator değildir;
PhysicsEvaluator veya derivative function çağırmaz, time ilerletmez, trajectory
üretmez ve accepted simulation step tanımlamaz.

Kabul edilmiş exact API'ler doğrudan yeniden kullanılır:

- NAT-013 `TranslationalState3DOF`;
- NAT-014 `TranslationalStateDerivative3DOF`.

Yeni state, derivative, vector veya tolerance policy yoktur. Sonuç state'i NAT-013
constructor'ı kurduğu için mevcut defensive-copy + read-only NumPy semantics'i
aynen korunur. Dynamics physics değiştirilmemiştir.

## FixedStepConfig

`FixedStepConfig(step_size_s)` frozen/slotted ve defaultsuzdur. `step_size_s`
finite ve strictly positive olmalıdır. Finite zero/negative değer structured
`NumericalIntegrationError(error_code="INVALID_STEP_SIZE", ...)`; NaN/infinity
generic `ValueError` yolundadır.

NAT-016 minimum, maximum, recommended veya default step tanımlamaz. Adaptive
reduction, clipping, end time veya final-step adjustment yoktur. En küçük positive
finite float'tan `1e308` değerine kadar positive finite test girdileri aynen kabul
edilip korunmuştur. `0.001` ve `0.01` yalnız test fixture'ıdır; production timestep
sabiti değildir.

## IntegrationPoint3DOF

`IntegrationPoint3DOF(time_s, state)` frozen/slotted, yalnız numerical `(t,y)`
temsilidir. `state` accepted `TranslationalState3DOF` nesnesidir. Time finite
olmalıdır fakat sign restriction yoktur; `time_s=-3.25` V&V fixture'ı geçerlidir.
Time fiziksel state'e eklenmemiştir. Step index, derivative, physics result veya
event bilgisi yoktur.

## TranslationalStateAlgebra3DOF

`TranslationalStateAlgebra3DOF` parametresiz, slotted/stateless, deterministic ve
keyword-only `add_scaled_derivative(*, state, derivative, scale_s)` sunar.
`scale_s` finite olmalıdır; positive, zero ve negative değerler matematikseldir.

Uygulanan tek cebir:

`new_position = state.position_world_m + scale_s * derivative.position_derivative_world_m_s`

`new_velocity = state.velocity_world_m_s + scale_s * derivative.velocity_derivative_world_m_s2`

Yeni accepted state döndürülür; state, derivative ve caller vector'ları mutasyona
uğramaz. Zero scale numerically aynı ama bağımsız yeni state ve vector değerleri
üretir.

`add_scaled_derivative` Euler değildir. Derivative hesaplamaz, PhysicsEvaluator
çağırmaz ve time ilerletmez. NAT-017 ileride bu primitive'i `y+h/2*k1`,
`y+h/2*k2`, `y+h*k3` ara state'leri için kullanacaktır; RK4 bu gate'te yoktur.

## Frozen V&V

Ortak state position `(1,2,3)`, velocity `(4,5,6)`; derivative position-rate
`(4,5,6)`, velocity-rate `(1,2,3)`:

- scale `+0.5`: position `(3,4.5,6)`, velocity `(4.5,6,7.5)`;
- scale `0`: aynı numerical values, yeni independent read-only state;
- scale `-0.5`: position `(-1,-0.5,0)`, velocity `(3.5,4,4.5)`;
- integration-point time `-3.25 s`: geçerli.

Clamp, repair veya epsilon uygulanmamıştır. Euler/midpoint/RK2/RK4, adaptive
stepping/error estimate, end-time/final-step policy, events, recorder/history ve
SimulationEngine ertelenmiştir.

Focused NAT-016 ve all numerics: **23 passed**. Doğrudan imported NAT-013/NAT-014
dynamics regression: **69 passed**. Gate talimatına göre full repository pytest
çalıştırılmadı.
