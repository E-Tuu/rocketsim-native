# NAT-021 — SimulationResult 3DOF V1

Tarih: 2026-09-09. Başlangıç kabul edilmiş NAT-020 commit'i
`4ad04f2d5af4f9b510ea11672a57d8598b265b14`; çalışma ağacı temizdi ve remote
yoktu. Uygulama `nat-021-simulation-result-3dof` dalında mevcut
`roketsim_native.simulation` namespace'i içindedir.

## Authority ve stored schema

Frozen/slotted `SimulationResult3DOF`, tamamlanmış accepted
`SimulationExecution3DOF` üzerinde immutable semantic query facade'ıdır. Tek stored
domain authority alanı `execution`'dır. `recorded_data`, `samples`, `events`,
`termination_reason` ve `steps_performed` execution veya execution'ın accepted
recorder snapshot'ından doğrudan forward edilir. Immutable tuple ve domain
nesneleri kopyalanmaz; ikinci bir sample/event/history authority'si kurulmaz.

NAT-018 `FlightEventType`, `FlightEventOccurrence3DOF` ve `event.is_terminal`;
NAT-019 `RecordedFlightSample3DOF` ile `RecordedFlightData3DOF`; NAT-020
`SimulationExecution3DOF` ile `SimulationTerminationReason` exact public API'leri
aynen kullanılır. Result simulation çalıştırmaz, physics/RK4/event detection
çağırmaz, recorder veya history'yi değiştirmez ve state/event interpolation yapmaz.

NAT-019 recorder seviyesinde empty snapshot geçerlidir. NAT-021 ise tamamlanmış
NAT-020 execution'ı temsil ettiğinden en az bir accepted initial sample ister;
empty sample history `EMPTY_RECORDED_SAMPLES` üretir. Fake initial sample eklenmez.

## Sample ve event sorguları

`initial_sample` exact `samples[0]`, `last_accepted_sample` exact `samples[-1]`'dir.
`final_sample` ve `final_state` bilinçli olarak yoktur: interior terminal occurrence
son accepted RK4 sample'dan sonra olabilir ve localized event state accepted
trajectory sample değildir.

Keyword-only `events_of_type(*, event_type)` bütün matching accepted occurrence'ları
recorded sırada tuple olarak döndürür. Sorting, dedupe veya first/last seçimi yoktur.
Named `burnout_event`, `apogee_event` ve `ground_event` accessors için zero match
`None`, one match exact accepted occurrence, multiple match
`AMBIGUOUS_EVENT_HISTORY` sonucudur.

`terminal_event` type'ı hard-code etmez; accepted `event.is_terminal` property
classification'ını kullanır. `TERMINAL_EVENT` reason tam bir terminal occurrence,
`MAXIMUM_STEPS_REACHED` reason ise sıfır terminal occurrence gerektirir. İhlaller
sırasıyla `TERMINATION_EVENT_MISSING`, `MULTIPLE_TERMINAL_EVENTS` veya
`UNEXPECTED_TERMINAL_EVENT` üretir. Bunlar physics validation değil execution/result
integrity kurallarıdır.

## Termination zamanı ve duration

Terminal execution'da `termination_time_s` accepted terminal occurrence zamanıdır;
maximum-step execution'da last accepted sample point zamanıdır. Interior terminal
için aşağıdaki distinction korunur:

- last accepted RK4 sample: örneğin `9.10 s`;
- localized physical terminal event: örneğin `9.163 s`;
- result termination time: `9.163 s`;
- `9.163 s`'de synthetic trajectory sample: yok.

Terminal exact accepted endpointte ise iki authority aynı sayısal zamanı taşıyabilir,
ama semantic olarak ayrı kalır. `simulation_duration_s` yalnız
`termination_time_s - initial_sample.point.time_s`'dir. Nonzero ignition/start time
desteklenir; termination initial time'dan önceyse abs/clamp olmadan
`INVALID_TERMINATION_TIME` üretilir.

`flight_time_s` bilinçli olarak yoktur. V1 simulation propulsion ignition'da başlar,
fakat pre-launch pad/contact support, LIFTOFF, launch-guide/rail constrained motion
ve RAIL_CLEAR accepted authority'leri henüz yoktur. Ignition genel olarak Liftoff
ile aynı fiziksel event değildir. Bu nedenle simulation duration tanımlıdır, flight
time değildir.

## Scope sınırı ve frozen V&V A–S

Altitude authority'leri (WORLD z, launch-relative height, geopotential ve MSL-benzeri
altitude) birleştirilmediği için `apogee_altitude_m` veya `maximum_altitude_m`
yoktur. Impact speed, maximum speed/acceleration/Mach/q gibi trajectory analytics;
CSV/JSON/plot/file/report; execution, physics, integration veya event detection da
NAT-021 scope'u dışındadır. NAT-022 end-to-end trajectory V&V uygulanmamıştır.

Frozen V&V A–S aşağıdaki evidence ile kapatılmıştır:

- A/B: execution/recorded data/sample/event identities exact forward edilir;
  `2.5, 2.6, 2.7 s` için initial/last exact first/last sample'dır.
- C: empty completed result explicit reddedilir.
- D: iki APOGEE generic query'de ikisi de existing sırada döner.
- E/F/G: named lookup yok/tek/ambiguous davranışı doğrulanır.
- H/I/J: terminal classification accepted property'den gelir; interior `9.10` /
  `9.163 s` ayrımı ve exact-endpoint `9.20 s` hali korunur.
- K/L/M/N: maximum-step, missing, multiple ve unexpected terminal integrity
  kuralları doğrulanır.
- O/P: `2.5 -> 12.75` duration `10.25 s`; interior `2.5 -> 10.08` duration
  `7.58 s`'dir ve last accepted `10.0 s` kullanılmaz.
- Q/R/S: `flight_time_s`, ambiguous final aliases ve altitude/impact/max analytics
  public API'de yoktur.

`SimulationResultError(ValueError)` yalnız NAT-021-owned altı structured code'u
taşır: `EMPTY_RECORDED_SAMPLES`, `TERMINATION_EVENT_MISSING`,
`MULTIPLE_TERMINAL_EVENTS`, `UNEXPECTED_TERMINAL_EVENT`,
`AMBIGUOUS_EVENT_HISTORY`, `INVALID_TERMINATION_TIME`; `error_code`, `field_name`
ve `value` korunur. Hidden clamp/fallback/repair yoktur.

Focused NAT-021: **14 passed**. All directly affected simulation: **97 passed**
(NAT-015 physics, NAT-018 events, NAT-019 recorder ve NAT-020 engine dahil).
Gate talimatıyla full repository pytest çalıştırılmadı.

NAT-021 IMPLEMENTATION GATE: PASS.
