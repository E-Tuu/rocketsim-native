# NAT-012B — Extended-Barrowman Static Stability V1

Tarih: 2026-09-08. Başlangıç kabul edilmiş NAT-012A.1 `43368eec36e85791ffdd4af1ee8f795de1c8436b`;
ağaç temiz, remote yoktu. Checkpoint 1 geometry descriptors ile linearized
static CNa/CP'yi; checkpoint 2 ayrı static-margin aggregation'ını uygular.

## Kaynak ve model kararı

Authority sırası OR13.05 teknik dokümantasyonu, audited OpenRocket source parity,
primary literature, sonra açık TBD/model decision'dır. OR13 genel sınıflamada
subsonic bölge yaklaşık M<.8, transonic yaklaşık .8–1.2'dir. Current OpenRocket
direct fin CNa'yı yaklaşık .9'a kadar kullanıp sonrasında continuation/interpolation
uygular. Native V1 transonic başlangıcından önce kesin olarak `0 <= M < .8`
ile durur; .8 dahil üstü `UNSUPPORTED_STATIC_STABILITY_MACH_REGIME` hatasıdır.
Java kodu doğrudan çevrilmemiş, verilen OR13 bağıntıları açık Native profile'a
aktarılmıştır.

OR13 fin CP routing'i M<=.5 quarter chord, .5<M<2 polynomial, M>2 empirical
endpoint modelidir. Native V1 yalnız ilk dalı ve polynomial'ın `.5<M<.8`
kısmını çalıştırır. M=2 polynomial construction boundary'sidir; runtime domain'i
genişletmez.

## Geometry authority

Ham `TrapezoidalFinSetGeometry.angular_arrangement` zorunludur; V1 yalnız
`EQUALLY_SPACED` sunar. Explicit azimuth placeholder'ı/default yoktur. Tek
GeometryResolver şu immutable resolved tanımları üretir:

- nose axial length;
- mid-chord sweep `atan2(d+(ct-cr)/2, span)`, LE sweep'ten ayrıdır;
- `y_MAC=span*(cr+2ct)/(3*(cr+ct))`;
- `x_MAC_LE=x_root_LE+d*y_MAC/span`;
- Extended-Barrowman `AR=2*span²/A_fin`;
- root'taki dış body radius; motor/mount/ring ölçülerinden türetilmez;
- angular arrangement.

100 mm fixture: mid-chord sweep yaklaşık sıfır, y_MAC=.0523076923076923 m,
x_MAC_LE=.7417948717948718 m, AR=1.8461538461538463, root radius=.05 m.
Önceki A.0/A.1 reference, aero length, wetted/base/fin alanları ve drag fiziği
değişmemiştir. Tapered/transition attachment ertelidir.

## Profil, sonuç ve evaluator

`StaticStabilityModelProfile` frozen/slotted ve evaluator için mandatory'dir:
Extended-Barrowman subsonic normal force, Mach-dependent Extended-Barrowman fin
CP, linearized-zero-AoA body treatment, exclusive maximum Mach .8. Gamma yoktur.

`StaticAeroContribution` yalnız `cna_per_rad`, `cp_x_geo_m` taşır.
`StaticAerodynamicProperties` yalnız nose, fin_set, total CNa, total CP ve profile
taşır. Fake zero body result, CG, static margin, force veya moment yoktur.
`StaticAerodynamicEvaluator` parametresiz/stateless, tüm girdileri keyword-only'dir;
accepted FlightConditions Mach'ını yeniden hesaplamadan tüketir. Actual AoA girişi
yoktur; sonuç dCN/dalpha'nın alpha->0 limitidir.

## Fizik

Pointed conical nose `CNa=2*A_frontal/A_ref`, `CP=2*L_nose/3` kullanır; fixture'da
alanlar eşit olduğu için CNa 2 çıkar fakat sabit kodlanmaz. Hollow/solid material
volume ve material centroid tüketilmez. Continuous constant-diameter cylinder'ın
classical alpha->0 linear CNa katkısı sıfırdır; finite-AoA Galejs body lift yoktur.

Single-fin direct subsonic CNa verilen mid-chord-sweep bağıntısıdır. OR13 Eq.3.53
eşit aralıklı N>=3 fin için `sum sin² Lambda=N/2` verir. Eq.3.54 fin-fin katsayısı
Ntot=1..4 için 1.000, 5=.948, 6=.913, 7=.854, 8=.810, >8=.750'dir. `N` mevcut
set fin sayısı, `Ntot` interference'a katılan toplam parallel fin sayısıdır.
V1'de tek fin seti bulunduğu için yalnız bu scope'ta Ntot=N; raw fin shape'te
Ntot alanı yoktur. Body-on-fin `1+r/(span+r)`; fixture'da 1.2941176470588236.
Fin-on-body interference uygulanmaz.

Fin CP M<=.5 için MAC quarter chord'dur. .5<M<.8 dalı, p(.5)=.25,
p'(.5)=0, p(2)=f(2), p'(2)=f'(2), p''(2)=p'''(2)=0 koşullarından verilen exact
u-polynomial representation'ı kullanır; rounded Java coefficient'i yoktur.
Total CNa nose+fin-set, total CP iki CNa'nın ağırlıklı x_geo ortalamasıdır.

## V&V

| Nicelik | M=0 | M=.75 |
| --- | ---: | ---: |
| Single-fin CNa/rad | 4.879478253472297 | 5.304704137592981 |
| Fin-set CNa/rad | 12.629237832516534 | 13.729822473770069 |
| Total CNa/rad | 14.629237832516534 | 15.729822473770069 |
| Fin CP fraction | .25 | .2887710236550541 |
| Fin CP x_geo (m) | .7758974358974359 | .78118620117551 |
| Total CP x_geo (m) | .6971650449840304 | .707290109576936 |

Compact test kapsamı: mandatory arrangement; mid-chord/LE distinction; MAC/AR/
radius; A.0/A.1 regression; explicit immutable profile/results/stateless API;
exact M domain; nose/cylinder policies; single-fin, N/2, N=1/2 rejection; N/Ntot;
tam Eq.3.54 tablosu; body-on-fin; quarter-chord; altı quintic boundary condition;
M=.75 polynomial; total weighting; determinism/no mutation ve scope protection.

Checkpoint-1 sonucu: focused `47 passed`; all aerodynamics `88 passed`; all
geometry `222 passed`. Bu aşamada full repository pytest çalıştırılmadı.

## Static margin sahipliği ve V&V

Nihai sahiplik kesin olarak şöyledir:

- CP → `StaticAerodynamicProperties` / Static Aerodynamics;
- CG → `RocketMassProperties` / Mass;
- Dmax → `ResolvedRocketGeometry.max_external_airframe_diameter_m` / Geometry;
- static margin → ayrı `StaticMarginCalculator`.

`StaticMarginCalculator` parametresiz/stateless ve üç girdisi keyword-only'dir.
Zaman, Mach, model profile veya motor girdisi yoktur. Immutable/slotted
`StaticMarginResult` yalnız `static_margin_calibers` saklar; CP ve CG'yi ikinci
otorite olarak kopyalamaz. x_geo nose-tip origin, aft-positive convention ile:

`static_margin_calibers=(CP_x_geo-CG_x_geo)/Dmax`.

Pozitif değer CP'nin CG'nin aft'ında, sıfır neutral ayrım, negatif değer CP'nin
CG'nin forward'ında olduğunu belirtir. Güvenli/güvensiz veya bir/iki-caliber
tasarım kabul politikası uygulanmaz.

Denominator kesinlikle `reference_length_m` değildir. Davranışsal testte aynı
geometry'nin reference length alanı NaN yapılınca margin değişmez; Dmax ikiye
katlanınca margin yarıya iner. Bu, iki değer mevcut MAXIMUM_DIAMETER profilinde
numerik eşit olsa bile fiziksel authority ayrımını kanıtlar.

CG=.650 m ve Dmax=.100 m için gerçek Part-1 sonuçları:

| Mach | CP x_geo (m) | Static margin (caliber) |
| ---: | ---: | ---: |
| 0 | .6971650449840304 | .471650449840304 |
| .75 | .707290109576936 | .5729010957693603 |

Ayrı fixtures pozitif, exact sıfır ve negatif işareti doğrular. Birden fazla
geçerli `RocketMassProperties` snapshot'ında yalnız CG değiştirilir; aynı exact
StaticAerodynamicProperties/CP nesnesi korunurken margin monoton değişir.
Non-finite raw CP/CG/Dmax generic ValueError; sonlu Dmax<=0 ve beklenmeyen
non-finite derived margin structured `AerodynamicEvaluationError` üretir.
Abs/clamp/epsilon/fallback yoktur.

## Nihai NAT-012 test sonucu

Nihai focused B (Part 1 + margin) `65 passed`; all aerodynamics `106 passed`;
all geometry `222 passed`. Bunların tamamı geçtikten sonra ertelenmiş full
repository pytest tam bir kez çalıştırıldı: `1216 passed in 4.59s`.
Bu milestone audit NAT-012A.0, NAT-012A.1 ve NAT-012B'yi tüm kabul edilmiş önceki
NAT fiziğiyle birlikte doğruladı.

## Ertelenenler

Post-demo: M>=.8 continuation, current-source .9–1.5 interpolation, supersonic
static stability, explicit azimuths ve N=1/N=2 directional stability, asymmetric
layouts, multiple interfering sets/true Ntot, tapered attachment, Galejs
finite-AoA body lift, nonlinear CN/stall, force vectors ve moments.

A.1 post-demo kararı da korunur: `FinCrossSection.ROUNDED` ve OR13 Eq.3.89
rounded-leading-edge pressure-drag routing'i M<.9, .9<M<1, M>1 için gelecektir;
burada uygulanmamıştır.

NAT-012B IMPLEMENTATION GATE: PASS.
NAT-012 MILESTONE AUDIT: PASS.
