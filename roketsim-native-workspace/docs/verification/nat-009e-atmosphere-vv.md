# NAT-009E — Atmosphere Verification & Validation

Date: 2026-09-06. Tested production commit:
`32e25dd5bb6ac89db7f0861443aa18159dbee99e` (accepted NAT-009D).
Branch: `nat-009e-atmosphere-vv`. Production değişikliği yoktur.

## Source / evidence ayrımı

- [U.S. Standard Atmosphere 1976](https://ntrs.nasa.gov/api/citations/19770009539/downloads/19770009539.pdf):
  NOAA-S/T 76-1562 / NASA-TM-X-74335; height Eq.(18)/(19), lower atmosphere.
- [NASA/TM-2005-213659](https://ntrs.nasa.gov/api/citations/20050207438/downloads/20050207438.pdf):
  seven-layer standard atmosphere reference tablosu; T/p basılı değerleri.
- Frozen NAT-009B/C/D: fixed gas constant 287.053, radius 6356766 m,
  seven-layer recurrence, gamma 1.4 ve Sutherland beta/S.
  Bu gate bu lower dry-air baseline'ı doğrular; standardın variable molecular
  weight/upper-atmosphere ayrıntılarının implementasyonu veya validasyonu değildir.
- [PDAS BigTables](https://www.pdas.com/bigtables.html), Tables 1/2:
  geometric Z km; 0/5/10/15/20/50/70/80/85 km offline satırları,
  2026-09-06 kaynak denetimi. Native'den üretilmiş reference değildir.
- OpenRocket: local workspace inventory'de Java source, executable/JAR,
  deterministic harness veya captured numeric fixture bulunmadı.
  Önceki source audit notları numeric capture sayılmadı; yeni Java kurulmadı.

## Test architecture ve tolerance

`tests/validation/test_atmosphere_vv.py` yalnız test-side tuple fixtures,
public B/C/D API chain'i ve 50-digit Decimal log-pressure reference içerir.
Production private layer tablosu expected value üretmekte kullanılmaz.

| Evidence class | Comparison rule |
|---|---|
| ANALYTIC_NUMERICAL | Bağımsız frozen-equation Decimal reference; rel=2e-14, abs=0; identities rel=3e-15 |
| SOURCE_TABLE / frozen knots | T float roundoff için abs=1e-12; p son basılı basamağın yarısı |
| SOURCE_TABLE / NASA table | T abs=1e-12; p bir son basılı basamak 0.01 Pa (precision/rounding farkı), sea-level exact |
| SOURCE_TABLE / PDAS | T abs=0.0005 K; a abs=0.005 m/s; p/rho/mu/nu rel=5e-5; genel model-error budget değil |
| PARITY_TBD_AFTER_REFERENCE_CAPTURE | Capture yok; parity tolerance seçilmedi |

Boundary ±0.01 m: altı çıktı analytic reference ile; T değişimi maksimum
|L|=0.0065 K/m ve p log değişimi g/(R*Tmin) hydrostatic bound ile denetlenir.
Lapse derivative sürekliliği istenmez. Dense sweep 50 m spacing, bütün knots
ve exact endpoint'ler; pressure/density strictly decreasing, T monotonic değil.

## ATM-VV test mapping

T01 sea-level full chain; T02 geopotential knots/source table; T03 lower;
T04 upper/inverse-geometric endpoint; T05 coordinate round trip; T06 rounding;
T07 model bridge; T08 low/mid/high full chain; T09 dense positivity;
T10 pressure monotonicity; T11 density monotonicity; T12 continuity;
T13 ideal gas; T14 acoustic; T15 viscosity; T16 PDAS; T17 no-clamp;
T18 repeatability; T19 capture status; T20 persistent evidence completeness.

## 84.852 / 86 km rounded-coordinate boundary

Exact Z=86000 m -> H=84852.04584490575 m, yani C'nin exact üst sınırından
yaklaşık 0.045845 m yukarıdadır: C explicit AtmosphereDomainError verir.
Exact H=84852 m -> Z=85999.95290624202 m; başarılı upper integration fixture
bu inverse değeri kullanır. Published 84.852 km geopotential / 86 km geometric
etiketleri rounded descriptive equivalence'tır; exact numeric identity değildir.
Radius, equation ve domain değiştirilmedi; clamp/fallback eklenmedi.

## Evidence results

| Blocking evidence | Result |
|---|---|
| STANDARD_REFERENCE_VV | PASS — frozen lower dry-air equations/knots ve NASA source-table |
| INTEGRATION_VV | PASS — semantic bridge, coordinate round-trip, altı quantity |
| DENSE_DOMAIN_VV | PASS — 1799 nokta; finite/positive; strict decreasing p/rho |
| BOUNDARY_CONTINUITY | PASS — altı iç sınır; ±0.01 m |
| INTERNAL_CONSISTENCY | PASS — ideal gas, acoustic, viscosity identities bütün grid'de |
| REGRESSION | PASS — 305 mevcut test korunur |

PDAS: DOCUMENTED_DIFFERENCE

50 km geometric nu: Native `0.016590879579753467`, PDAS printed `0.016590` m²/s.
Ham fark `8.79579753467e-7 m²/s`, PDAS'a göre relative yaklaşık `5.3019e-5`;
önerilen `5e-5` table tolerance dışında. Diğer seçilen table alanları aynı
toleranslarla doğrulanır. Fark saklanmadı ve genel tolerans büyütülmedi.

Source closure: [PDAS atmos.zip](https://www.pdas.com/packages/atmos.zip),
`bigtables.py` v1.5 (2022 Mar 18), 2026-09-06 okundu; UTF-8 decoded source
SHA-256 `ECA87577139AC3B2845D1D4ECA91604AC278A491918979F2D2316BF88A9A3A28`.
`TransportRatios` dynamic ratio = mu/MUZERO, kinematic ratio = dynamic ratio/sigma;
Table2 kinematic value = ETAZERO * ratio. Constants: MUZERO=1.7894e-5,
ETAZERO=1.4607e-5, RHOZERO=1.225. Dolayısıyla source'un nu değeri doğrudan
mu/rho yerine `(mu/rho)*(ETAZERO*RHOZERO/MUZERO)` ölçeğini içerir.
Source ayrıca GMR için RSTAR=8314.32/MOLWT_ZERO=28.9644 kullanır;
Native R_air=287.053, PDAS density/sound referansları ise yuvarlanmıştır.
50 km regression testi ham mismatch'i korur, source-backed normalization ile
aynı table tolerance'ında açıklanabildiğini ve Native değerin bağımsız strict
analytic oracle'ı geçtiğini doğrular. Bu yalnız test-side evidence projection;
production düzeltmesi/parity adapter değildir. PDAS standardın üst otoritesi değildir.

OPENROCKET PARITY: NOT_CAPTURED

OR-OPEN-ENV-002 remains open. Hiçbir OpenRocket numeric value/delta veya MATCH
uydurulmadı. Önceki linear sound/viscosity, cache/clamp ve altitude semantics
source bulguları karşılaştırma öncesi configuration/coordinate audit gerektirir.
NOT_CAPTURED authoritative frozen-standard gate'i tek başına engellemez.

Baseline: **305 passed**. Focused: **50 passed**; environment (unit + E):
**205 passed**; full suite: **355 passed** (305 mevcut + 50 yeni).
Production bug/fix yok. Yeni model, dependency veya source API yok.
PDAS dışındaki unresolved numeric difference yok; OpenRocket numeric evidence
capture edilmediği için full OpenRocket parity closure iddia edilmez.
NAT-009E STANDARD V&V: PASS
NAT-009E IMPLEMENTATION GATE: PASS
No remote configured; Git sync incomplete. NAT-009F başlatılmadı.
