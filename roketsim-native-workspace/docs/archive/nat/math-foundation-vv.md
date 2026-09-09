# Mathematical Foundation V&V

## Scope

Bu kabul kaydı NAT-003..NAT-007 kapsamındaki SI policy, numerical guards,
vector foundation, frame conventions ve quaternion foundation'ı birlikte
doğrular. Yeni production physics veya math davranışı tanımlamaz.

Technical source: `OpenRocket_Fizik_Simulasyon_Motoru_Teknik_Spesifikasyon_v1.docx`

## Frozen conventions

- Physics core internal units SI; internal angle unit radian'dır.
- WORLD frame `WORLD_ENU`: East, North, Up.
- BODY eksenleri `x_B`, `y_B`, `z_B`; `z_B` longitudinal/thrust/roll eksenidir.
- Geometry `x_geo`, nose tip origin'inden nose -> tail yönünde artar ve `z_B` değildir.
- Quaternion scalar-first `[w, x, y, z]`; `q_BW` BODY -> WORLD yönündedir.
- Right-hand rotation ve `delta_q * q_old` soldan increment sırası korunur.
- Rotation matrix authoritative state değil, quaternion'dan türetilen gösterimdir.

## Acceptance checks

Convention audit, identity chain, BODY longitudinal-axis mapping, BODY/WORLD
round-trip, quaternion/matrix parity, norm preservation, proper rotation matrix,
left multiplication order, right-hand consistency, `q`/`-q` equivalence ve
hidden-mutation kontrollerinin tümü PASS.

## Report V&V fixture mapping

- QUAT-T01 — Identity quaternion -> vector unchanged: **PASS**
- QUAT-T02 — Known +90° axis rotation -> expected vector: **PASS**
- QUAT-T03 — BODY -> WORLD -> BODY round trip: **PASS**
- QUAT-T04 — Long integration quaternion norm preservation: **DEFERRED — requires integration layer**. Quaternion ODE, RK4/integrator ve timestep katmanı henüz mevcut değildir.

## Test result

- NAT-008 öncesi regression baseline: **134 passed**
- NAT-008 validation: **16 passed**
- Full suite: **150 passed**

## Final gate

**MATHEMATICAL FOUNDATION: ACCEPTED**
