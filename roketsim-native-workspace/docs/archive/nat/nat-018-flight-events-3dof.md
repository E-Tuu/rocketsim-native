# NAT-018 — Burnout / Apogee / Ground Events 3DOF V1

Tarih: 2026-09-09. Başlangıç kabul edilmiş NAT-017 commit'i
`6e22e49bdae23970d924a5c152362ff36671482f`; çalışma ağacı temiz ve remote
yoktu. Uygulama `nat-018-flight-events-3dof` dalında mevcut
`roketsim_native.simulation` namespace'i içinde yapılmıştır.

## Amaç, kapsam ve authority

NAT-018 iki accepted `IntegrationPoint3DOF` arasında yalnız event algılar,
doğrusal olarak yerelleştirir, sınıflandırır ve kronolojik tuple halinde raporlar.
V1 kümesi tam olarak `BURNOUT`, `APOGEE`, `GROUND`'dur. Integrasyon, physics
değişikliği, recovery deployment, simulation stop, trajectory history veya event
response yoktur.

Accepted authority zinciri değiştirilmeden kullanılır:

- burnout: propulsion `MotorCurveStatistics.curve_end_time_s` ile NAT-015
  `PropulsionTimeline.ignition_time_s` toplamı;
- apogee: NAT-013 `TranslationalState3DOF.velocity_world_m_s[2]` WORLD vertical
  velocity;
- ground: NAT-013 `LaunchConditions3DOF.initial_position_world_m[2]` launch WORLD-z
  düzlemi;
- bracket uçları: NAT-016 `IntegrationPoint3DOF`.

F50 için runtime burnout authority tam canonical curve end `1.430 s`'dir.
`MotorCertificationReference.measured_burn_time_s` yalnız V&V metadata,
`1.301 s` son nonzero sample authority değil, %5 effective end yalnız
characterization ve `4.0 s` ejection delay ayrı motor-variant bilgisidir. Detector
thrust samples incelemez, thrust evaluator çağırmaz ve threshold/tolerance üretmez.

## Modeller ve crossing sözleşmeleri

Profile dört mandatory ve defaultsuz V1 politikası taşır:

- `PROPULSION_CURVE_END_TIME`;
- `WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING`;
- `LAUNCH_WORLD_Z_PLANE`;
- `LINEAR_BRACKET_INTERPOLATION`.

Burnout yalnız `t0 < ignition+curve_end <= t1` için raporlanır. Bu `(t0,t1]`
sözleşmesi endpoint event'ini bir kez verir ve sonraki intervalde tekrarlamaz.

Apogee yalnız `vz0 > 0` ve `vz1 <= 0` aşağı geçişidir; alpha
`vz0/(vz0-vz1)` olur. `0 -> positive` veya `negative -> negative` apogee değildir.

Ground yüksekliği yalnız `H = current_world_z - launch_world_z`'dir; NAT-009
atmospheric/geopotential altitude değildir. Ground yalnız `H0 > 0` ve `H1 <= 0`
geçişidir. `zero -> negative` V1 event'i değildir; endpoint duplicate'i önler ve
pre-launch/liftoff/guide semantics'e ertelenmiştir. `zero -> positive` launch
departure da ground değildir.

## Localization ve çıktı

Her detected event için aynı explicit lineer bracket modeli kullanılır:

`position_est = position0 + alpha*(position1-position0)`

`velocity_est = velocity0 + alpha*(velocity1-velocity0)`

Sonuç yeni accepted `TranslationalState3DOF` olduğundan defensive-copy/read-only
NumPy semantics korunur. Alanın adı bilerek `estimated_state`'tir: RK4 dense
output, root-solver sonucu, yeniden integre state veya authoritative endpoint
physics değildir. Alpha `[0,1]` dışına clamp edilmez; internal invariant hatası
`INVALID_EVENT_LOCALIZATION` üretir.

`FlightEventOccurrence3DOF.is_terminal` stored boolean değildir. Yalnız `GROUND`
True, burnout ve apogee False döndürür. Detector loop durdurmaz. Çoklu event'ler
artan time ile sıralanır; exact tie reporting sırası `BURNOUT`, `APOGEE`, `GROUND`.
Bu reporting policy fiziksel nedensellik önceliği değildir. Detector event geçmişi
ve `*_seen` state'i tutmaz.

## Error ve V&V

Finite semantic/model hataları structured `EventDetectionError` ile
`error_code`, `field_name`, `value` taşır. Equal/reversed bracket
`NON_INCREASING_EVENT_INTERVAL`; ignition + curve-end overflow
`NONFINITE_EVENT_BOUNDARY_TIME`; invalid alpha/state arithmetic
`INVALID_EVENT_LOCALIZATION` üretir. Dört unsupported model kendi explicit code'u
ile reddedilir. Epsilon, clamp, abs, fallback, root solve veya substep yoktur.

Frozen V&V A–K:

- A: F50 ignition `0.250 s` + curve end `1.430 s` = burnout `1.680 s`; bracket
  `1.60..1.70`, alpha `0.8`, nonterminal.
- B: burnout endpoint alpha `1`; sonraki intervalde duplicate yok.
- C: apogee `3 -> -1`, alpha `.75`, time `4.15`, estimated z `100.75`, vz `0`.
- D: `0 -> +2` ve `-1 -> -2` false apogee üretmez.
- E: positive -> endpoint zero alpha `1`; sonraki zero -> negative duplicate yok.
- F: launch-z `50`, `50.3 -> 49.9`, alpha `.75`, time `9.15`, estimated z `50`,
  terminal ground.
- G: endpoint-ground bir kez; sonraki zero -> negative duplicate yok.
- H: zero -> positive/negative ground değildir.
- I: alpha `.25` generic state sonucu position `(1,2,11)`, velocity `(3,5,7)`.
- J: `5.10` burnout önce `5.14` apogee; exact tie sırası burnout/apogee/ground.
- K: hiçbir crossing yoksa exact empty tuple `()`.

Focused NAT-018: **25 passed**. All directly affected simulation: **46 passed**.
Doğrudan propulsion `MotorCurveStatistics.curve_end_time_s` regression:
**63 passed**. Accepted dynamics/numerics/other physics imports veya exports
değişmedi; bu nedenle talimata göre onların suite'leri ve full repository pytest
çalıştırılmadı.

Recorder, SimulationEngine/Result, recovery, rail/liftoff events, trajectory
history, terrain, event-driven physics changes ve NAT-019 ertelenmiştir.

NAT-018 IMPLEMENTATION GATE: PASS.
