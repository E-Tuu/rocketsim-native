# NAT-020 — SimulationEngine 3DOF V1

Tarih: 2026-09-09. Başlangıç kabul edilmiş NAT-019 commit'i
`71a4ac188fec2fbbffd605d20ddb7118b7bc532f`; NAT-017 atası doğrulandı,
çalışma ağacı temiz ve remote yoktu. Uygulama `nat-020-simulation-engine-3dof`
dalında mevcut `roketsim_native.simulation` namespace'i içindedir.

## Orchestration ve accepted authority'ler

`SimulationEngine3DOF` yalnız orchestration/lifecycle katmanıdır. Her run içinde
fresh recorder, current point, step count, derivative adapter ve event context
kurar; engine instance'ında history/cache/current state tutmaz. Aynı deterministic
configuration ile yeniden kullanılabilir.

Accepted sorumluluklar değiştirilmeden bağlanır:

- NAT-013 `InitialStateBuilder` physical initial state'i kurar;
- NAT-015 `PhysicsEvaluator3DOF` her current/trial/endpoint state'in physics'ini
  ve accepted dynamics derivative'ini üretir;
- NAT-016 `FixedStepConfig` ve `IntegrationPoint3DOF` timestep/point authority'sidir;
- NAT-017 `ClassicalRK4Integrator3DOF` candidate state'i üretir;
- NAT-018 detector crossing/localization ve `event.is_terminal` authority'sidir;
- NAT-019 recorder accepted sample ve localized event stream'lerini kaydeder;
- propulsion `MotorCurveStatistics` burnout curve-end authority'sidir.

Yeni atmosphere, gravity, wind, flow, flight conditions, propulsion, mass, drag,
dynamics, RK4 veya event denklemi yoktur.

## Configuration ve initial point

`SimulationRunLimits3DOF.maximum_steps` mandatory true `int` ve en az 1'dir;
bool/float/string type error, nonpositive int structured `INVALID_MAXIMUM_STEPS`
üretir. Maximum steps computational candidate-step guard'dır; maximum time veya
default/recommended step count yoktur.

Run configuration tüm accepted dependencies'i mandatory taşır ve bağımsız
`initial_time_s` içermez. Initial time tam olarak
`physics_context.propulsion_timeline.ignition_time_s`'dır; V1 ignition'da başlar.
Event context engine içinde aynı launch conditions, aynı propulsion timeline,
supplied curve statistics ve event profile ile kurulur; ikinci ignition authority
yoktur.

Initial state yalnız `InitialStateBuilder.build()` ile üretilir. İlk accepted
`IntegrationPoint3DOF` ve onun exact time/state'inde NAT-015 endpoint physics'i
her RK4 işleminden önce açıkça değerlendirilip kaydedilir.

## RK4 adapter ve endpoint physics

Local keyword-only derivative callable her invocation'da NAT-015'i supplied
`(time_s,state)` üzerinde yeniden çalıştırır ve yalnız
`physics_result.dynamics.derivative` döndürür. Physics cache/reuse yoktur; her RK4
step tam dört stage physics evaluation yapar.

K4 physics `(t+h,y4)` üzerindedir ve `y4=y+h*k3`; accepted endpoint ise weighted
`(t+h,y_next)`'tir. Engine k4 sonucunu asla endpoint physics diye kaydetmez. Event
screening candidate acceptance'tan önce yapılır. Accepted her endpoint için NAT-015
ayrıca çağrılır ve ancak bu result point ile sample olarak kaydedilir.

Call-count authority:

- initial point: 1;
- normal veya terminal-exact-endpoint step: 4 stage + 1 endpoint;
- interior-terminal rejected candidate: 4 stage + 0 endpoint;
- N maximum/exact-terminal steps: `1+5N`;
- step N interior terminal: `5N`.

## Events ve termination

NAT-018 events kronolojik gelir; engine ilk `event.is_terminal` occurrence'ını
seçer. Terminal zamanı sonrasındaki events atılır, aynı terminal zamanındaki tüm
events NAT-018 supplied order'ıyla korunur.

Explicit V1 policy `REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT`:

- terminal alpha `<1`: computational RK4 step sayılır, candidate reddedilir,
  endpoint physics/sample yoktur; event estimated state sample'a yükseltilmez;
- terminal alpha `==1`: candidate accepted endpointtir, ayrı endpoint physics
  değerlendirilir, events ve sample kaydedilip terminal biter.

Epsilon/near-one yoktur. Interior event'e shortened RK4, alpha*h, root solver,
dense output veya substep uygulanmaz. Terminal event final permitted stepte olsa
`TERMINAL_EVENT`, step-limit sonucundan önce gelir.

Terminal yoksa Nth permitted candidate normal biçimde endpoint physics/events/
sample ile kabul edilir; sonra `MAXIMUM_STEPS_REACHED` normal lifecycle sonucu
döner. FixedStepConfig her RK4 çağrısına exact aynı object olarak iletilir; end
time/final-step clipping yoktur. `steps_performed` başarılı dönen RK4 candidate
sayısıdır; interior terminal candidate da sayılır.

Accepted NAT-018 `H0=0 -> H1<0` ground değildir. Engine ayrıca z/ground kontrolü,
clamp veya failed-liftoff patch'i eklemez. Pre-launch/liftoff/rail semantics
ertelenmiştir.

## Execution sonucu, error ve V&V

Frozen/slotted `SimulationExecution3DOF` yalnız `recorded_data`, termination reason
ve `steps_performed` taşır. Final state/position, terminal event ve max metrics gibi
NAT-021 result semantics eklenmez. Her run fresh `FlightRecorder3DOF` kullanır.

NAT-020 yalnız `INVALID_MAXIMUM_STEPS` ve
`UNSUPPORTED_TERMINAL_EVENT_HANDLING_MODEL` structured engine errors'ına sahiptir.
Initial physics, RK derivative physics, detector, endpoint physics ve recorder'dan
sentinel upstream error instance'ları wrapping olmadan aynen yayılmıştır.

Frozen V&V A–R:

- A/B: ignition `2.5 s` ilk point/physics time'ıdır; configte initial time yoktur.
- C/D: bir normal step → 2 samples, 6 physics calls; endpoint call k4'ten ayrı
  `candidate.state` kullanır.
- E: iki accepted steps → 3 samples, 11 calls.
- F: ilk-step interior terminal → 1 executed, yalnız initial sample, 5 calls.
- G: terminal exact endpoint → 2 samples, 6 calls.
- H: apogee/ground/burnout outputundan ground sonrası burnout atılır.
- I: eş-zaman burnout/apogee/ground üçü supplied sırada tutulur.
- J: final permitted step terminali limitten önce gelir.
- K: üç maximum steps → 4 samples, 16 calls.
- L: üçüncü-step interior terminal → 3 steps, 3 samples, 15 calls.
- M: estimated terminal state event stream'de kalır, trajectory point yapılmaz.
- N: aynı engine ile iki run bağımsız üçer-sample history üretir.
- O: her integrator call aynı FixedStepConfig identity'sini alır.
- P: detector her başarılı candidate için tam bir kez çağrılır.
- Q: beş orchestration boundary upstream exception identity'sini korur.
- R: launch plane'den aşağı candidate NAT-018 no-event ise engine tarafından
  ground diye onarılmaz ve normal kabul edilir.

Focused NAT-020: **25 passed**. All directly affected simulation: **83 passed**
(NAT-015 physics, NAT-018 events, NAT-019 recorder dahil). Direct NAT-017 RK4
regression: **17 passed**. Gate talimatıyla full repository pytest çalıştırılmadı.

NAT-021 SimulationResult, NAT-022 end-to-end trajectory V&V, pre-ignition/liftoff/
rail, recovery, adaptive/event integration, 6DOF ve output/export ertelenmiştir.

NAT-020 IMPLEMENTATION GATE: PASS.
