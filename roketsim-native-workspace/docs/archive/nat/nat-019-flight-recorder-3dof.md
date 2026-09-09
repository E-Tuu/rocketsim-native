# NAT-019 — 3DOF Flight Recorder V1

Tarih: 2026-09-09. Başlangıç kabul edilmiş NAT-018 commit'i
`9ce96cdff86620abdfe18c9e4136e76085cf1ec6`; NAT-017 atası doğrulandı,
çalışma ağacı temiz ve remote yoktu. Uygulama `nat-019-flight-recorder-3dof`
dalında mevcut `roketsim_native.simulation` namespace'i içindedir.

## Pasif recording kapsamı

NAT-019 yalnız accepted trajectory sample'ları ile localized event occurrence'ları
iki ayrı append-only in-memory stream'de tutar. Physics hesaplamaz, state integre
etmez, event algılamaz, simulation loop kontrol etmez ve event response üretmez.

`RecordedFlightSample3DOF` yalnız accepted `IntegrationPoint3DOF point` ve accepted
`PhysicsEvaluationResult3DOF physics` alanlarını saklar. Time, position, velocity,
mass, thrust, Mach, Reynolds, q, Cd0 veya acceleration flatten edilmez; authority
accepted nesnelerde kalır. Nesneler immutable kabul edildiğinden gereksiz deep copy
yoktur ve exact object provenance korunur.

## Point / endpoint physics caller önkoşulu

Her recorded sample için `physics`, tam olarak `point.time_s` ve `point.state`
üzerinde değerlendirilmiş accepted endpoint physics olmalıdır. NAT-015 result
evaluation point provenance'ı taşımadığından recorder bu ilişkiyi bağımsız
kanıtlayamaz; NAT-020 SimulationEngine orchestration bunu garanti edecektir.
NAT-019 NAT-015 şemasını değiştirmez ve PhysicsEvaluator çağırmaz.

RK4 k4 physics endpoint physics değildir. K4 `(t+h, y4)` üzerinde ve
`y4=y+h*k3` trial state'iyle değerlendirilir; accepted endpoint `(t+h,y_next)`
genellikle farklıdır. Caller, sample kaydetmeden önce PhysicsEvaluator'ı accepted
endpointte açıkça yeniden değerlendirmelidir. Recorder k4 sonucunu kabul edilen
endpoint physics'e dönüştürmez veya doğrulamaya çalışmaz.

## İki ayrı stream ve chronology

`FlightRecorder3DOF` intentionally stateful ve yalnız `_samples`, `_events`
append-only listelerini tutar. Current state/physics/point, flight mode veya
event-seen flag yoktur.

İlk sample herhangi finite accepted point time'ında geçerlidir. Sonrakiler strict
artan olmalıdır. Equal/reversed time `NON_INCREASING_SAMPLE_TIME`; overwrite,
replacement, sorting ve failed-call mutation yoktur.

Event time'ları global non-decreasing olmalıdır; equal-time events geçerlidir ve
supplied order aynen korunur. Recorder NAT-018 tie priority'sini yeniden hesaplamaz
ve event type deduplication yapmaz. Her batch, mevcut son evente karşı ilk eleman
ve batch içi tüm adjacency doğrulandıktan sonra tek seferde append edilir. İhlal
`NON_MONOTONIC_EVENT_TIME` üretir ve batch'in hiçbiri eklenmez. Empty tuple no-op'tur.

Localized `FlightEventOccurrence3DOF.estimated_state` accepted RK4 trajectory
sample'ı değildir. Event kaydı sample stream'e synthetic point eklemez. Event
time'ının sample min/max aralığında olması, ground'un son event/sample olması veya
terminal ground'un recorder'ı kapatması şart değildir; bunlar lifecycle concerns'dür.

## Snapshot semantics

`snapshot()` her çağrıda internal listelerden yeni tuple'lar içeren frozen/slotted
`RecordedFlightData3DOF` üretir. Eski snapshot sonraki append'lerden etkilenmez.
Snapshot recorder'ı finalize/close/seal etmez; sonrasında sample/event kaydı devam
eder. Empty recorder için `samples=()` ve `events=()` geçerlidir.

## Error ve V&V

Recorder-owned finite chronology hataları yalnız structured
`FlightRecordingError(error_code, field_name, value)` ile iki code kullanır:
`NON_INCREASING_SAMPLE_TIME` ve `NON_MONOTONIC_EVENT_TIME`.

Frozen V&V A–M:

- A: sample times `0.00, 0.01, 0.02` supplied sırada.
- B/C: equal ve reversed sample time başarısız; history aynı kalır.
- D: burnout `1.43`, apogee `4.12`, ground `8.91` sırası korunur.
- E: üç event `4.0` zamanında valid ve supplied order korunur.
- F: mevcut burnout `1.4` sonrası `(apogee 4.0, ground 3.5)` batch'i atomik reddedilir.
- G: empty event tuple no-op.
- H: iki-sample snapshot iki kalırken sonraki snapshot üç sample taşır.
- I: fresh snapshot exact empty tuple'lardır.
- J: sample fields yalnız point ve physics'tir.
- K: `1.0/1.1` samples ve `1.05` apogee ayrı stream'lerde kalır.
- L: event estimated state farklı olsa da sample sayısı değişmez.
- M: snapshot sonrası record ve yeni snapshot geçerlidir.

Focused NAT-019: **12 passed**. Tüm directly affected simulation tests:
**58 passed**; bu koşu NAT-018 event ve NAT-015 physics regressions'ı da içerir.
Public upstream schema/export değişmediği için ayrı dependency rerun gerekmedi.
Gate talimatıyla full repository pytest çalıştırılmadı.

SimulationEngine/Result, terminal-ground lifecycle, file/CSV/JSON export,
plotting, persistence, resampling/downsampling, trajectory interpolation ve
NAT-020 ertelenmiştir.

NAT-019 IMPLEMENTATION GATE: PASS.
