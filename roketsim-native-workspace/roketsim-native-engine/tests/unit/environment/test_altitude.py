"""NAT-009B ALT-T01..T10: datum işaretleri ve clampsiz scalar dönüşümler."""

import inspect
import math
import sys
from decimal import Decimal, localcontext

import pytest

from roketsim_native.environment import altitude


@pytest.mark.parametrize("geoid_undulation_m, expected", [(30.0, 970.0), (-30.0, 1030.0)])
def test_geoid_sign(geoid_undulation_m, expected):
    """ALT-T01: Pozitif N çıkarılır; negatif N yükseklik farkını ters çevirir."""
    assert altitude.orthometric_from_ellipsoidal(
        ellipsoidal_height_m=1000.0, geoid_undulation_m=geoid_undulation_m
    ) == expected


@pytest.mark.parametrize("geoid_undulation_m", [30.0, 0.0, -30.0])
def test_geoid_round_trip(geoid_undulation_m):
    """ALT-T02: Aynı datum ve N ile ellipsoidal round trip."""
    orthometric_height_m = altitude.orthometric_from_ellipsoidal(
        ellipsoidal_height_m=1234.5, geoid_undulation_m=geoid_undulation_m
    )
    assert altitude.ellipsoidal_from_orthometric(
        orthometric_height_m=orthometric_height_m, geoid_undulation_m=geoid_undulation_m
    ) == pytest.approx(1234.5, rel=1e-14, abs=1e-12)


@pytest.mark.parametrize("orthometric_height_m, expected", [(1200.0, 350.0), (800.0, -50.0)])
def test_agl_sign(orthometric_height_m, expected):
    """ALT-T03: Zeminin altındaki AGL sıfıra clamp edilmez."""
    assert altitude.agl_from_orthometric(
        orthometric_height_m=orthometric_height_m, ground_elevation_m=850.0
    ) == expected


@pytest.mark.parametrize("orthometric_height_m", [-100.5, 850.0, 1200.25])
def test_agl_round_trip(orthometric_height_m):
    """ALT-T04: Ground elevation sabitken orthometric round trip."""
    agl_altitude_m = altitude.agl_from_orthometric(
        orthometric_height_m=orthometric_height_m, ground_elevation_m=850.0
    )
    assert altitude.orthometric_from_agl(
        agl_altitude_m=agl_altitude_m, ground_elevation_m=850.0
    ) == pytest.approx(orthometric_height_m, rel=1e-14, abs=1e-12)


def test_explicit_atmosphere_bridge():
    """ALT-T05: RoketSim MODEL DECISION köprüsü orthometric girdiyi Z_atm yapar."""
    orthometric_height_m = altitude.orthometric_from_ellipsoidal(
        ellipsoidal_height_m=1264.5, geoid_undulation_m=30.0
    )
    assert altitude.atmospheric_geometric_from_orthometric(
        orthometric_height_m=orthometric_height_m
    ) == 1234.5


def test_geopotential_origin():
    """ALT-T06: Standardın geometric ve geopotential sıfırları örtüşür."""
    assert altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=0.0) == 0.0


def test_ussa_86_km_reference():
    """ALT-T07: Frozen sabit/equation için bağımsız yüksek hassasiyetli referans."""
    assert altitude.USSA76_GEOPOTENTIAL_EARTH_RADIUS_M == 6_356_766.0
    with localcontext() as context:
        context.prec = 50
        radius_m = Decimal("6356766")
        expected = float(radius_m * Decimal("86000") / (radius_m + Decimal("86000")))
    assert altitude.geopotential_from_geometric(
        atmospheric_geometric_altitude_m=86000.0
    ) == pytest.approx(expected, rel=0.0, abs=3e-11)


@pytest.mark.parametrize("atmospheric_geometric_altitude_m", [-1000.0, 0.0, 1000.0, 11000.0, 32000.0, 86000.0, 90000.0])
def test_geopotential_round_trip(atmospheric_geometric_altitude_m):
    """ALT-T08: Negatif ve 90 km dahil; tolerans yalnız test karşılaştırmasıdır."""
    geopotential_height_m = altitude.geopotential_from_geometric(
        atmospheric_geometric_altitude_m=atmospheric_geometric_altitude_m
    )
    assert altitude.geometric_from_geopotential(
        geopotential_height_m=geopotential_height_m
    ) == pytest.approx(atmospheric_geometric_altitude_m, rel=1e-14, abs=1e-12)


def test_no_atmosphere_domain_clamp():
    """ALT-T09: 90 km ve negatif yükseklik doğrudan bağıntıyla dönüştürülür."""
    radius_m = 6_356_766.0
    result = altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=90000.0)
    assert result == pytest.approx(radius_m * 90000.0 / (radius_m + 90000.0), rel=1e-14, abs=1e-12)
    assert result != altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=86000.0)
    negative = altitude.atmospheric_geometric_from_orthometric(orthometric_height_m=-1000.0)
    assert negative == -1000.0
    assert altitude.geopotential_from_geometric(
        atmospheric_geometric_altitude_m=negative
    ) == pytest.approx(radius_m * -1000.0 / (radius_m - 1000.0), rel=1e-14, abs=1e-12)


@pytest.mark.parametrize("function, kwargs", [
    (altitude.geopotential_from_geometric, {"atmospheric_geometric_altitude_m": -6_356_766.0}),
    (altitude.geometric_from_geopotential, {"geopotential_height_m": 6_356_766.0}),
])
def test_mathematical_singularities(function, kwargs):
    """ALT-T10: Exact singularity epsilon veya fallback olmadan hata verir."""
    with pytest.raises(ValueError, match="zero .* denominator"):
        function(**kwargs)


CONVERSIONS = [getattr(altitude, name) for name in altitude.__all__ if callable(getattr(altitude, name))]


@pytest.mark.parametrize("function", CONVERSIONS)
@pytest.mark.parametrize("invalid", [math.nan, math.inf, -math.inf])
def test_non_finite_inputs(function, invalid):
    """Her public girdide NAT-004 finite validation ve anlamlı label korunur."""
    parameters = inspect.signature(function).parameters
    for name in parameters:
        kwargs = dict.fromkeys(parameters, 1.0)
        kwargs[name] = invalid
        with pytest.raises(ValueError, match=name):
            function(**kwargs)


@pytest.mark.parametrize("function", CONVERSIONS)
def test_semantic_keyword_only_float_api(function):
    """Public scalar API açık metre isimleri kullanır; local z_W dönüşümü yoktur."""
    parameters = inspect.signature(function).parameters
    assert all(p.kind is inspect.Parameter.KEYWORD_ONLY for p in parameters.values())
    assert all(name.endswith("_m") for name in parameters)
    assert all(p.default is inspect.Parameter.empty for p in parameters.values())
    assert type(function(**dict.fromkeys(parameters, 1))) is float


@pytest.mark.parametrize("function, kwargs", [
    (altitude.orthometric_from_ellipsoidal, {"ellipsoidal_height_m": sys.float_info.max, "geoid_undulation_m": -sys.float_info.max}),
    (altitude.ellipsoidal_from_orthometric, {"orthometric_height_m": sys.float_info.max, "geoid_undulation_m": sys.float_info.max}),
    (altitude.agl_from_orthometric, {"orthometric_height_m": sys.float_info.max, "ground_elevation_m": -sys.float_info.max}),
    (altitude.orthometric_from_agl, {"agl_altitude_m": sys.float_info.max, "ground_elevation_m": sys.float_info.max}),
])
def test_non_finite_result_rejected(function, kwargs):
    """Finite girdilerin float taşması infinity olarak dışarı sızmaz."""
    with pytest.raises(ValueError, match="must be finite"):
        function(**kwargs)


@pytest.mark.parametrize("value", [sys.float_info.max, -sys.float_info.max])
def test_large_finite_geopotential_inputs(value):
    """Oran önce hesaplanarak gereksiz ara çarpım taşması önlenir."""
    assert math.isfinite(altitude.geopotential_from_geometric(atmospheric_geometric_altitude_m=value))
    assert math.isfinite(altitude.geometric_from_geopotential(geopotential_height_m=value))
