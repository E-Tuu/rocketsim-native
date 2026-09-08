# NAT-012A.1 — Native Basic Drag V1

Tarih: 2026-09-08. Başlangıç `ea123b0`; çalışma ağacı temiz ve remote yoktu.
Kabul edilmiş NAT-012A.0 milestone baseline'ı 1121 PASS'tir. Bu gate'in açık
test politikası nedeniyle full tarihsel suite çalıştırılmadı; full audit NAT-012
milestone sonuna ertelendi.

## Sahiplik ve arayüz

`ResolvedRocketGeometry` fiziksel alan/uzunlukları, `SingleStageRocketAerodynamicSurfaces`
üç bağımsız eşdeğer pürüzlülüğü, `BasicFlightConditions` airspeed ve Mach'ı,
`DryAirProperties` kinematic viscosity ile specific-heat ratio'yu sahiplenir.
Aerodynamics bunları kopyalamaz. Gamma mevcut NAT-009D `1.4` otoritesinden
snapshot'a taşınır; `BasicDragModelProfile` içinde ikinci gamma yoktur.

A.0 result'ına iki ayrı anlam eklendi:

- `max_external_airframe_diameter_m`: dış silindirik airframe çapı; fin, motor,
  mount ve ring hariç.
- `axisymmetric_body_length_m`: nose + cylindrical body uzunluğu.

`reference_length_m`, `aerodynamic_length_m`, `axisymmetric_body_length_m`,
`max_external_airframe_diameter_m` ve fin MAC birbirinin yerine kullanılmaz.
Aft fin uzantısı aerodynamic length'i uzatabilir; axisymmetric body length'i
uzatmaz.

Public `BasicDragEvaluator` parametresiz/stateless'tir; tüm girdileri
keyword-only ve model profili zorunludur. `BasicDragResult` immutable/slotted
olup Re/Re_eval, üç skin-friction tanısı, yedi fiziksel katkı, exact toplam ve
profili taşır. Grouped friction/pressure/base değerleri yalnız derived property'dir.

## Açık V1 model politikası

- Boundary layer: `FULLY_TURBULENT`.
- Reynolds length: `AERODYNAMIC_LENGTH`.
- Low-Re continuation: yalnız correlation değerlendirmesinde `Re_eval=max(Re,1e4)`.
- Nose pressure: TD13 conical subsonic-to-sonic.
- Fin edge: TD13 `SQUARE`.
- Base drag: TD13 subsonic-to-sonic.
- Plume: `IGNORED`.
- Runtime Mach domain: kapalı `[0,1]`; daha yüksek Mach structured
  `UNSUPPORTED_MACH_REGIME` hatasıdır, clamp/extrapolation yoktur.

Mach 1 sınırında denklem değerlendirilmesi, sonic bölge transonik fidelity'sinin
bağımsız doğrulaması değildir. Roughness correlation bağımsız geçerlilik aralığı
TBD'dir; keyfi `k_s/L` clamp'i eklenmemiştir.

## Denklemler

Gerçek Reynolds `Re=V*L_aero/nu`; sıfır hızda tam sıfırdır. Smooth correlation:
`Cf=1/(1.50*ln(Re_eval)-5.6)^2`, ardından `1-0.10*M^2` düzeltmesi. Her nose/body/fin
için `k_s=0` roughness adayı sıfır; aksi halde
`0.032*(k_s/L_aero)^0.2*(1-0.10*M^2)`. Büyük aday seçilir; eşitlikte SMOOTH.

Body correction `f_B=L_axisymmetric/D_max`, `K_B=1+1/(2*f_B)`; radius bug veya
epsilon yoktur. Fin correction `K_F=1+2*t_fin/MAC`. Nose/body wetted alanları ve
finlerin iki geniş yüzü accepted Geometry'den gelir.

Conical nose pressure `C0=.8*sin(phi)^2`, `C1=sin(phi)`,
`d1=4/(gamma+1)*(1-C1/2)`, `a=C1-C0`, `b=d1/a`,
`Cd*=C0+a*M^b` bağıntısıdır. SQUARE fin LE stagnation katsayısı
`.85*(1+M^2/4+M^4/40)` ve sweep-cosine-squared kullanır. Fin TE ile airframe
base için `.12+.13*M^2` kullanılır. LE sweep TE base terimini etkilemez.
Airframe base alanı nozzle/motor/mount ile azaltılmaz; plume ignored'dır.

Saklanan yedi terim nose/body/fin friction, nose pressure, fin LE pressure,
fin TE base ve airframe base'dir. `total_cd0` tam olarak bu yedi değerin
toplamıdır. Drag force/vector, AoA, CP, CNa ve static margin yoktur.

## Sentetik V&V

Kabul edilmiş 100 mm A.0 fixture, tüm `k_s=0`, V=100 m/s, M=.3,
nu=1.5e-5 m²/s ve gamma=1.4 ile:

| Nicelik | Sonuç |
| --- | ---: |
| Re | 6666666.666666666 |
| Smooth Cf | 0.0030970969597129273 |
| Düzeltilmiş seçilen Cf | 0.003069223087075511 |
| K_B / K_F | 1.05 / 1.0439849624060151 |
| Nose/body/fin friction Cd | 0.019602822950748604 / 0.09023515876002 / 0.05091519406760164 |
| Nose / fin-LE pressure Cd | 0.021621978661408206 / 0.13580527568987105 |
| Fin-TE / airframe base Cd | 0.024146733317993406 / 0.1317 |
| Total C_D0 | 0.4740271634476429 |

Ayrı sentetik yüzey pürüzlülükleri nose=SMOOTH, body=ROUGHNESS_LIMITED,
fins=SMOOTH seçimini doğrular. Mach 0 ve 1 kabul, >1 hata; determinism ve input
immutability doğrulanmıştır.

## Test kapsamı ve sonuç

Compact parametrik kapsam: (1–2) yeni geometry anlamları ve uzunluk ayrımı;
(3) gamma authority; (4) mandatory profil; (5–7) Reynolds/smooth/roughness Cf;
(8–13) body/fin correction ile pressure/base terimleri; (14) plume ignored;
(15) exact yedi-terim toplam; (16) Mach sınırları; (17–18) smooth/rough V&V;
(19) determinism/no mutation; (20) force/AoA/CP/CNa/static-margin kapsam koruması.

Sonuçlar: odaklı NAT-012A.1 `26 passed`; tüm aerodynamics `41 passed`; tüm
geometry `218 passed`; değişen gamma arayüzünün doğrudan air-properties
regresyonu `51 passed`. Full pytest bilinçli olarak çalıştırılmadı.

Post-demo: `FinCrossSection.ROUNDED` henüz eklenmez. Gelecekte OR13 Eq. 3.89
rounded-leading-edge routing'i M<.9, .9<M<1 ve M>1 bölgeleriyle ele alınacaktır.
AIRFOIL, arbitrary-AoA drag, genişletilmiş transonic/supersonic drag ve
plume/nozzle interaction ertelidir. NAT-012B uygulanmamıştır.

NAT-012A.1 IMPLEMENTATION GATE: PASS.
