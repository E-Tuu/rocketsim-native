"""NAT-009B: SI metre cinsinden açık referanslı scalar yükseklik dönüşümleri.

[LITERATURE / geodesy]: h_ellip = H_orth + N; AGL = H_orth - H_ground.
Geoid undulation ve ground elevation girdilerinin uyumlu vertical datum'da
olması çağıranın sorumluluğudur; burada geoid/terrain lookup yapılmaz.
[MODEL-DECISION]: Z_atm := H_orth yalnız RoketSim atmosfer köprüsüdür;
iki koordinatın evrensel fiziksel özdeşliği anlamına gelmez.
[LITERATURE / U.S. Standard Atmosphere 1976], Eq.(18)/(19), local ATM-009:
H_gp = R*Z_atm/(R+Z_atm); Z_atm = R*H_gp/(R-H_gp).

Bu katman atmosfer modelinin geçerlilik aralığını uygulamaz ve clamp yapmaz.
Negatif yükseklikler korunur; singularity ve non-finite sonuçlar açık hata verir.
Local WORLD ENU z_W mutlak yükseklik değildir; bu API'nin girdisi değildir.
"""

from typing import Final

from roketsim_native.math.numerical import require_finite


__all__ = (
    "USSA76_GEOPOTENTIAL_EARTH_RADIUS_M",
    "orthometric_from_ellipsoidal",
    "ellipsoidal_from_orthometric",
    "agl_from_orthometric",
    "orthometric_from_agl",
    "atmospheric_geometric_from_orthometric",
    "geopotential_from_geometric",
    "geometric_from_geopotential",
)

# USSA76 Eq.(17)–(19) sabit etkin yarıçapı; WGS84 geometrik yarıçapı değildir.
USSA76_GEOPOTENTIAL_EARTH_RADIUS_M: Final[float] = 6_356_766.0


def orthometric_from_ellipsoidal(
    *, ellipsoidal_height_m: float, geoid_undulation_m: float
) -> float:
    """Ellipsoid yüksekliğinden N'yi çıkararak orthometric yüksekliği (m) bul."""
    ellipsoidal_height_m = float(require_finite(ellipsoidal_height_m, name="ellipsoidal_height_m"))
    geoid_undulation_m = float(require_finite(geoid_undulation_m, name="geoid_undulation_m"))
    return require_finite(ellipsoidal_height_m - geoid_undulation_m, name="orthometric_height_m")


def ellipsoidal_from_orthometric(
    *, orthometric_height_m: float, geoid_undulation_m: float
) -> float:
    """Orthometric yüksekliğe işaretli N'yi ekleyerek ellipsoid yüksekliğini (m) bul."""
    orthometric_height_m = float(require_finite(orthometric_height_m, name="orthometric_height_m"))
    geoid_undulation_m = float(require_finite(geoid_undulation_m, name="geoid_undulation_m"))
    return require_finite(orthometric_height_m + geoid_undulation_m, name="ellipsoidal_height_m")


def agl_from_orthometric(
    *, orthometric_height_m: float, ground_elevation_m: float
) -> float:
    """Aynı datum'daki ground elevation'ı çıkar; negatif AGL'yi (m) koru."""
    orthometric_height_m = float(require_finite(orthometric_height_m, name="orthometric_height_m"))
    ground_elevation_m = float(require_finite(ground_elevation_m, name="ground_elevation_m"))
    return require_finite(orthometric_height_m - ground_elevation_m, name="agl_altitude_m")


def orthometric_from_agl(
    *, agl_altitude_m: float, ground_elevation_m: float
) -> float:
    """İşaretli AGL ile uyumlu datum'daki ground elevation'ı topla (m)."""
    agl_altitude_m = float(require_finite(agl_altitude_m, name="agl_altitude_m"))
    ground_elevation_m = float(require_finite(ground_elevation_m, name="ground_elevation_m"))
    return require_finite(agl_altitude_m + ground_elevation_m, name="orthometric_height_m")


def atmospheric_geometric_from_orthometric(*, orthometric_height_m: float) -> float:
    """[MODEL-DECISION] H_orth -> Z_atm köprüsü (m); atmosfer domain kontrolü yok."""
    return float(require_finite(orthometric_height_m, name="orthometric_height_m"))


def geopotential_from_geometric(*, atmospheric_geometric_altitude_m: float) -> float:
    """USSA76 geometric Z_atm -> geopotential H_gp (m); Z_atm=-R singularity'dir."""
    atmospheric_geometric_altitude_m = float(require_finite(
        atmospheric_geometric_altitude_m, name="atmospheric_geometric_altitude_m"
    ))
    radius_m = USSA76_GEOPOTENTIAL_EARTH_RADIUS_M
    denominator_m = radius_m + atmospheric_geometric_altitude_m
    if denominator_m == 0.0:
        raise ValueError("atmospheric_geometric_altitude_m produces a zero geopotential denominator")
    # Önce oran: R*Z ara çarpımının gereksiz float taşmasını önler; clamp değildir.
    result = radius_m * (atmospheric_geometric_altitude_m / denominator_m)
    return require_finite(result, name="geopotential_height_m")


def geometric_from_geopotential(*, geopotential_height_m: float) -> float:
    """USSA76 geopotential H_gp -> geometric Z_atm (m); H_gp=R singularity'dir."""
    geopotential_height_m = float(require_finite(geopotential_height_m, name="geopotential_height_m"))
    radius_m = USSA76_GEOPOTENTIAL_EARTH_RADIUS_M
    denominator_m = radius_m - geopotential_height_m
    if denominator_m == 0.0:
        raise ValueError("geopotential_height_m produces a zero geometric denominator")
    result = radius_m * (geopotential_height_m / denominator_m)
    return require_finite(result, name="atmospheric_geometric_altitude_m")
