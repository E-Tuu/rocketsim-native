# NAT-017 — Classical Fixed-Step RK4 Integrator 3DOF V1

Tarih: 2026-09-09. Başlangıç kabul edilmiş NAT-016 commit'i
`6abbb6ffb350a32adc6a764eb5631360781ad2b6`; çalışma ağacı temiz ve remote
yoktu. Uygulama `nat-017-classical-rk4-3dof` dalında mevcut tek
`roketsim_native.numerics` namespace'i içinde yapılmıştır.

## Algoritma ve sahiplik

`ClassicalRK4Integrator3DOF`, standard classical explicit fixed-step RK4'ün
Butcher katsayılarını (`1/2`, `1`, `1:2:2:1` ve `1/6`) aynen uygular. Bunlar
tunable production parametreleri değil, standard matematiksel algoritma
katsayılarıdır. Timestep yalnız mandatory `FixedStepConfig.step_size_s` ile gelir;
adaptive stepping, min/max dt, clipping ve end-time bilgisi yoktur.

`DerivativeFunction3DOF` yalnız keyword-only `(time_s, state) -> accepted
TranslationalStateDerivative3DOF` numerical ODE sözleşmesidir. PhysicsEvaluator,
rocket, geometry, mass, propulsion, atmosphere, wind veya aerodynamics authority'si
değildir ve RK4 modülü bunlara doğrudan bağlanmaz. Üst katman callable içinde her
stage için tam physics'i yeniden değerlendirebilir. Callable'ın upstream hataları
aynen yayılır; yanlış return type coercion edilmeden `TypeError` olur.

## Dört stage ve state cebiri

Tam olarak dört derivative çağrısı yapılır:

1. `k1 = f(t, y)`;
2. `k2 = f(t+h/2, y+h/2*k1)`;
3. `k3 = f(t+h/2, y+h/2*k2)`;
4. `k4 = f(t+h, y+h*k3)`.

İkinci, üçüncü ve dördüncü trial state'lerin tabanı daima özgün `y`'dir. Tüm trial
state'ler ve final state yalnız kabul edilmiş NAT-016
`TranslationalStateAlgebra3DOF.add_scaled_derivative` üzerinden kurulur. Final
accepted derivative, accepted NAT-014 derivative nesnesinde
`(k1 + 2*k2 + 2*k3 + k4)/6` olarak tutulur ve özgün state'e `h` ile uygulanır.

`t+h/2` ve `t+h` bir kez hesaplanır. Finite `t` ve `h` toplaması overflow ederek
non-finite stage time üretirse `NumericalIntegrationError` ve
`NONFINITE_RK4_STAGE_TIME` ile açıkça başarısız olur; clamp, dt reduction veya
fallback yoktur.

## K4 ve accepted endpoint ayrımı

`y4 = y+h*k3` yalnız dördüncü RK trial state'idir ve genellikle weighted
`y_next` ile aynı değildir. K4 hesabında yapılan physics evaluation accepted
endpoint physics authority'si değildir. NAT-017 `(t+h, y_next)` döndürür ve beşinci
endpoint derivative/physics çağrısı yapmaz. Endpoint physics gerekiyorsa daha üst
simulation orchestration katmanı açıkça değerlendirecektir.

## V&V ve sınırlar

- Constant derivative fixture: dört eş stage, `t=2.2`, position `(1.4,0,0)`,
  velocity `(5.6,0,0)`.
- Constant acceleration fixture: position `(0,0,108.75)`, velocity `(0,0,15)`;
  sonuç Euler adımından ayrılır.
- Harmonic oscillator fixture: `x=0.9950041666666667`,
  `vx=-0.0998333333333333`.
- Stage spy: times tam `2.0, 2.2, 2.2, 2.4`; state'ler özgün `y` tabanlıdır,
  k4 trial state accepted endpoint değildir ve beşinci çağrı yoktur.
- k1/k2/k3/k4'te sentinel upstream error family ve instance'ı aynen korunur.
- Input point/state/config değişmez; output yeni accepted state ve read-only NumPy
  vector semantics'i taşır.

NAT-017 event detection/interpolation, recorder/history, SimulationEngine,
PhysicsEvaluator adapter, endpoint physics evaluation, Euler/RK2/adaptive RK,
error estimation ve timestep clipping uygulamaz. Burnout, apogee, ground ve rail
events NAT-018 ve sonraki orchestration gates'e bırakılmıştır.

Focused NAT-017: **17 passed**. All numerics (NAT-016 + NAT-017): **40 passed**.
Doğrudan NAT-016 regression: **23 passed**. Public dynamics import/export
değişmediği için dynamics paketi yeniden çalıştırılmadı; gate talimatına göre full
repository pytest çalıştırılmadı.
