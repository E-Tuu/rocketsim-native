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

NAT-010A: FLOW-001, WORLD/ENU SI hız çıkarımı; sahiplik `flight_conditions`.
Başlangıç `49410f7`: 410 test PASS. FLOW-T01..T18 ve overflow guard:
43 test PASS; environment/V&V 260 PASS, math/V&V 148 PASS, full 453 PASS.
Önceki production modülleri değişmedi. NAT-010B scalar flight conditions,
BODY transform ve composition implement edilmedi.
