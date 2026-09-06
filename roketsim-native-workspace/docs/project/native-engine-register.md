# Native Engine Register

- [x] NAT-000 Architecture freeze
- [x] NAT-001 Python environment
- [x] NAT-002 Repository foundation
- [x] NAT-003 SI policy
- [x] NAT-004 Numerical helpers
- [x] NAT-005 Vector foundation
- [x] NAT-006 Frame conventions
- [x] NAT-007 Quaternion
- [x] NAT-008 Math foundation V&V
- [ ] NAT-009 Environment engine
  - [x] NAT-009A Environment Source & Model Freeze
  - [x] NAT-009B Altitude & Geopotential Foundation
  - [x] NAT-009C Dry-Air Atmosphere Core
  - [x] NAT-009D Air Properties
  - [x] NAT-009E Atmosphere V&V (standard PASS; OpenRocket NOT_CAPTURED)
  - [x] NAT-009F Constant Gravity Baseline
  - [x] NAT-009H Steady Wind Foundation (demo path; G1/G2 deferred)
- [x] NAT-010A Relative Flow Foundation
- [x] NAT-010B Basic Flight Conditions
- [x] NAT-011A Minimal Single-Stage Rocket Geometry
- [x] NAT-011A.1 Geometry Construction & Material Volume Extension

NAT-010A: FLOW-001, WORLD/ENU SI hız çıkarımı; sahiplik `flight_conditions`.
Başlangıç `49410f7`: 410 test PASS. FLOW-T01..T18 ve overflow guard:
43 test PASS; environment/V&V 260 PASS, math/V&V 148 PASS, full 453 PASS.
Önceki production modülleri değişmedi. NAT-010B scalar flight conditions,
BODY transform ve composition implement edilmedi.

NAT-010B: `flight_conditions.basic`, yalnız V=||relative_velocity_W||,
M=V/a, Re=V*L_ref/nu, q=0.5*rho*V². Upstream rho/a/nu otoriteleri korunur;
L_ref açık karakteristik uzunluktur. Parametresiz calculator, immutable
dört-scalar snapshot, finite/positive validation; sıfır hız geçerlidir.
Tam 3D norm `math.hypot` ile hesaplanır; taşan/underflow ile invariant'ı
bozan sonuçlar generic ValueError üretir, fallback uygulanmaz.
Başlangıç `707e295`: 453 PASS. FC-T01..T20 ve edge testler: 55 PASS;
flight_conditions 98 PASS, environment/V&V 260 PASS, math/V&V 148 PASS,
full 508 PASS. Önceki production kodu/dependency değişmedi.
Mass/Propulsion/Aero, AoA/BODY flow, dynamics ve sonraki demo gate'leri
implement edilmedi. NAT-010B IMPLEMENTATION GATE: PASS.

NAT-011A: `geometry.models` immutable/slotted tasarımları,
`geometry.resolver` scalar/domain ve yerleşim doğrulamasını sahiplenir.
Nose-tip x_geo=0, +x_geo nose→tail; BODY/WORLD dönüşümü yoktur.
Tek airframe çapı D_ref/L_ref/A_ref'i belirler (demo model kararı).
Root chord tamamen body'ye bağlıdır; signed tip offset ve aft-overhang
korunur; overall length furthest downstream extent'tir. Clamp yoktur.
Fin count int>=3; bool/fractional adet sessizce dönüştürülmez.
Non-finite girdiler/derived numerical failure generic ValueError;
finite invalid geometry structured GeometryValidationError üretir.
Başlangıç `6fea345`: 508 PASS. GEO-T01..T25 ve edge testleri: 87 PASS;
flight_conditions 98 PASS, environment/V&V 260 PASS, math/V&V 148 PASS,
full 595 PASS. Önceki production modülleri/dependency değişmedi.
Mass/CG/inertia, propulsion, aero/Barrowman, component tree/staging ve
dynamics implement edilmedi. NAT-011A IMPLEMENTATION GATE: PASS.

NAT-011A.1, NAT-011A'nın onaylı schema evrimidir; ikinci construction source,
resolver veya resolved wrapper yoktur. Nose construction_mode ve wall_thickness_m
explicit; SOLID için None, HOLLOW_SHELL için lateral yüzeye normal pozitif
kalınlık zorunludur. Body explicit kalınlıklı hollow tube, fins uniform solid
plate'tir. Tek GeometryResolver üç material volume ve absolute x_geo volume
centroid'i aynı ResolvedRocketGeometry içine ekler. Volume centroid henüz CG
değildir; density/material catalog/mass yorumu NAT-011B'ye aittir.
Nose shell normal-offset iç koni çıkarımı, tube annulus ve trapezoid plate
denklemleri uygulanır; strict thickness limitleri, non-finite/invalid derived
değerler hata üretir; clamp, repair veya default thickness yoktur.
Başlangıç `9827531`: 595 PASS. Schema'ya uyarlanan eski geometry testleri
87 PASS (fiziksel assertions korunur); CGEO-T01..T28/edge 38 PASS;
geometry 125 PASS, flight_conditions 98 PASS, environment/V&V 260 PASS,
math/V&V 148 PASS; full 633 PASS. Reference/placement convention değişmedi.
Geometry dışı production/dependency değişmedi; NAT-011B başlatılmadı.
NAT-011A.1 IMPLEMENTATION GATE: PASS.
