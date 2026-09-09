# NAT-013 — Initial / Launch State V1

Tarih: 2026-09-08. Kabul edilmiş başlangıç commit'i
`26815b9e122dba9e0b48fd9a33d46c9a653268d9`; çalışma ağacı temiz ve remote
yoktu. Uygulama `nat-013-initial-launch-state` dalında yapılmıştır.

## Çözülen numerical-policy boşluğu

Önceki NAT-013 denemesi, kabul edilmiş bir unit-vector tolerans politikası
bulunmadığı için dosyaları değiştirmeden durmuştu. Bu retry için
`LAUNCH_DIRECTION_UNIT_NORM_ATOL = 1.0e-8` açıkça onaylanmıştır. Değer yalnız
NAT-013 launch-direction unit-norm doğrulamasına aittir; OpenRocket kaynaklı bir
fizik sabiti, global math toleransı veya normalization threshold'u değildir.
`math.vectors` değiştirilmemiş ve global/default tolerans eklenmemiştir.

Kabul koşulu `abs(magnitude(direction)-1) <= 1e-8`'dir. Tolerans içinde kabul
edilen bileşenler aynen korunur. Zero direction `INVALID_LAUNCH_DIRECTION`,
finite non-unit direction `NON_UNIT_LAUNCH_DIRECTION` structured hatasıdır.
NaN ve infinity mevcut generic `ValueError` finite-validation yolunu kullanır.
Hiçbir input normalize, clamp veya repair edilmez.

## WORLD ENU ve vektör otoritesi

WORLD frame mevcut local ENU sözleşmesini sürdürür: +x East, +y North, +z Up.
Yeni vektör sınıfı yoktur. Exact kabul edilmiş runtime API yeniden kullanılır:

- modül: `roketsim_native.math.vectors`;
- tip: bir boyutlu `numpy.ndarray`, `numpy.float64`;
- construction/finite doğrulama: `as_vector(..., size=3)`;
- norm: `magnitude(...)`;
- mevcut fakat bu gate'te normalization için kullanılmayan helpers:
  `normalize(..., atol=...)`, `is_near_zero(..., atol=...)`.

NumPy array primitive mutable olduğu için her domain alanı accepted constructor
semantics ile bağımsız kopyalanır ve saklanan kopyanın `writeable` flag'i false
yapılır. Caller array'i değiştirmek stored değeri etkilemez; stored array'e yazma
girişimi hata verir. Frozen dataclass tek başına yeterli sayılmamıştır.

## Domain sahipliği ve API

`TranslationalState3DOF` frozen/slotted ve yalnız şunları taşır:

- `position_world_m`: WORLD ENU metre;
- `velocity_world_m_s`: WORLD ENU metre/saniye.

State içinde time, motor time, mass/CG, propulsion, aero, environment, launch
direction, acceleration veya attitude yoktur.

`LaunchConditions3DOF` frozen/slotted ve defaultsuz mandatory
`initial_position_world_m`, `initial_velocity_world_m_s`,
`launch_direction_world_unit` girdilerini taşır. WORLD origin launch point diye
hard-code edilmez; zero/nonzero position ve velocity explicit olarak korunur.
Launch direction yalnız ilk launcher/rocket longitudinal reference'ıdır; attitude,
velocity policy veya rail constraint değildir.

`InitialStateBuilder` parametresiz, slotted/stateless ve keyword-only girdilidir.
Position ile velocity'yi yeni independent read-only state kopyalarına eşler.
Launch direction state'e kopyalanmaz. Zaman, environment, altitude, force,
acceleration, mass veya propulsion hesaplamaz.

## NAT-009 environment sahiplik audit'i

Mevcut `environment.altitude` açıkça local WORLD ENU `z_W` değerinin mutlak
yükseklik olmadığını belgeliyor. NAT-013 yeni altitude sınıfı, launch-site altitude
otoritesi, geometric/geopotential dönüşüm, atmosphere, gravity veya wind modeli
eklememiş ve NAT-009 production dosyalarını değiştirmemiştir. WORLD position'ın
mevcut NAT-009 altitude/environment girdilerine bağlanması NAT-015
`PhysicsEvaluator` entegrasyonuna ertelenmiştir.

## Fixture ve kapsam

Demo `(0,0,0) m` position, `(0,0,0) m/s` velocity ve `(0,0,1)` direction
değerleri yalnız explicit test fixture'ıdır; production defaults değildir.
Nonzero WORLD position, nonzero velocity ve arbitrary unit direction testlerle
doğrulanmıştır.

NAT-014 3DOF kuvvet/ivme ve thrust-direction policy; rail/rail-exit, integrasyon,
RK4, burnout/apogee/ground events; 6DOF attitude/quaternion/angular rates,
inertia/moments ve NAT-015 environment bridge ertelenmiştir.

## Test kanıtı

Focused NAT-013 suite: **26 passed**. Kapsam accepted NumPy vector reuse,
frozen/slotted sözleşmeler, tüm stored vector'larda defensive-copy/read-only,
caller mutation izolasyonu, zero/nonzero initial değerler, vertical/arbitrary unit
direction, `<=1e-8` kabul ve exact preservation, out-of-tolerance/zero structured
hatalar, non-finite generic hata, builder keyword-only/stateless/deterministic
davranışı ve yasak state alanlarını içerir.

Math ve environment production kodları değiştirilmediği için gate politikasına
uygun olarak historical dependency veya full repository pytest çalıştırılmadı.
