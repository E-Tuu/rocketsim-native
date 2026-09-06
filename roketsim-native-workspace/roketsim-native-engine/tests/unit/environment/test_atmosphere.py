"""NAT-009C ATM-T01..T16: dry-air bağıntıları, recurrence ve domain sözleşmesi."""

from dataclasses import FrozenInstanceError, fields
from decimal import Decimal, localcontext
import inspect
import math

import pytest

from roketsim_native.environment import atmosphere as module
from roketsim_native.environment.atmosphere import (
    AtmosphereDomainError,
    DryAirAtmosphereState,
    USStandardAtmosphere1976Lower,
)


BOUNDARIES = (11000.0, 20000.0, 32000.0, 47000.0, 51000.0, 71000.0)
PROVIDER = USStandardAtmosphere1976Lower()


def reference_state(geopotential_height_m):
    """Frozen bağıntıları bağımsız 50-digit log-pressure integraliyle değerlendir.

    Production base tablosu/helper'ları kullanılmaz; test referansı her layer'ın
    hydrostatic log-pressure katkısını toplar. Tablo display rounding'i yoktur.
    """
    with localcontext() as context:
        context.prec = 50
        requested = Decimal(str(geopotential_height_m))
        temperature = Decimal("288.15")
        log_pressure_ratio = Decimal(0)
        gravity_over_gas = Decimal("9.80665") / Decimal("287.053")
        base = Decimal(0)
        for top, lapse in (
            ("11000", "-0.0065"), ("20000", "0"), ("32000", "0.001"),
            ("47000", "0.0028"), ("51000", "0"), ("71000", "-0.0028"),
            ("84852", "-0.002"),
        ):
            endpoint = min(requested, Decimal(top))
            lapse = Decimal(lapse)
            next_temperature = temperature + lapse * (endpoint - base)
            if lapse:
                log_pressure_ratio -= gravity_over_gas / lapse * (
                    next_temperature / temperature
                ).ln()
            else:
                log_pressure_ratio -= gravity_over_gas * (endpoint - base) / temperature
            temperature = next_temperature
            if requested <= Decimal(top):
                break
            base = Decimal(top)
        pressure = Decimal("101325") * log_pressure_ratio.exp()
        density = pressure / (Decimal("287.053") * temperature)
        return tuple(float(value) for value in (temperature, pressure, density))


def assert_reference(geopotential_height_m):
    """T/p/rho'yu strict float toleransıyla bağımsız referansla karşılaştır."""
    actual = PROVIDER.evaluate(geopotential_height_m=geopotential_height_m)
    expected = reference_state(geopotential_height_m)
    for value, reference in zip(
        (actual.temperature_K, actual.pressure_Pa, actual.density_kg_m3), expected
    ):
        assert value == pytest.approx(reference, rel=2e-14, abs=0.0)
    return actual


def test_sea_level_temperature():
    """ATM-T01: Sea-level sıcaklığı authoritative T0'dır."""
    assert PROVIDER.evaluate(geopotential_height_m=0).temperature_K == 288.15


def test_sea_level_pressure():
    """ATM-T02: Sea-level basıncı authoritative p0'dır."""
    assert PROVIDER.evaluate(geopotential_height_m=0).pressure_Pa == 101325.0


def test_sea_level_density():
    """ATM-T03: Density ayrı sabit değil ideal-gas sonucudur."""
    assert assert_reference(0.0).density_kg_m3 == pytest.approx(
        101325.0 / (287.053 * 288.15), rel=2e-15, abs=0.0
    )


def test_nonzero_lapse_layer():
    """ATM-T04: 5 km gradient layer power-law pressure branch'ini doğrular."""
    assert_reference(5000.0)


def test_isothermal_layer():
    """ATM-T05: 15 km exact sıfır lapse ve exponential pressure branch'i."""
    assert assert_reference(15000.0).temperature_K == pytest.approx(216.65, abs=1e-12, rel=0)


@pytest.mark.parametrize("boundary", BOUNDARIES)
def test_boundary_continuity(boundary):
    """ATM-T06: Her iki taraftaki en yakın float ile exact knot sürekliliği."""
    states = [assert_reference(value) for value in (
        math.nextafter(boundary, -math.inf), boundary, math.nextafter(boundary, math.inf)
    )]
    for state in states:
        assert state.temperature_K == pytest.approx(states[1].temperature_K, rel=2e-14, abs=0)
        assert state.pressure_Pa == pytest.approx(states[1].pressure_Pa, rel=2e-14, abs=0)


@pytest.mark.parametrize("boundary", BOUNDARIES)
def test_exact_boundary_selects_upper_base(boundary, monkeypatch):
    """ATM-T07: Süreklilik seçimi gizlediğinden yalnız dispatch'i dar bir spy ile izle.

    Public state'e layer index eklenmez; burada yalnız frozen exact-knot
    convention için private hesap çağrısının seçilen base'i gözlenir.
    """
    original = module._temperature_pressure
    selected_bases = []

    def observe(layer, geopotential_height_m):
        selected_bases.append(layer.base_geopotential_height_m)
        return original(layer, geopotential_height_m)

    monkeypatch.setattr(module, "_temperature_pressure", observe)
    first = PROVIDER.evaluate(geopotential_height_m=boundary)
    assert PROVIDER.evaluate(geopotential_height_m=boundary) == first
    assert selected_bases == [boundary, boundary]


@pytest.mark.parametrize("geopotential_height_m", (0.0, *BOUNDARIES, 84852.0))
def test_reference_layer_points(geopotential_height_m):
    """ATM-T08: Sekiz knot'ta frozen recurrence; rounded table tolerance kullanılmaz."""
    state = assert_reference(geopotential_height_m)
    assert state.density_kg_m3 == pytest.approx(
        state.pressure_Pa / (287.053 * state.temperature_K), rel=2e-15, abs=0
    )


def test_physical_sanity():
    """ATM-T09: T pozitif; p ve rho seçilmiş artan yüksekliklerde azalır."""
    points = (-5000, 0, 5000, 11000, 15000, 20000, 26000, 32000,
              40000, 47000, 49000, 51000, 60000, 71000, 80000, 84852)
    states = [PROVIDER.evaluate(geopotential_height_m=point) for point in points]
    for state in states:
        assert all(math.isfinite(value) and value > 0 for value in (
            state.temperature_K, state.pressure_Pa, state.density_kg_m3
        ))
    for lower, upper in zip(states, states[1:]):
        assert lower.pressure_Pa > upper.pressure_Pa
        assert lower.density_kg_m3 > upper.density_kg_m3


def test_lower_endpoint():
    """ATM-T10: -5 km dahil, ama referans base hâlâ 0 m."""
    assert assert_reference(-5000.0).temperature_K == pytest.approx(320.65, abs=1e-12, rel=0)


def test_upper_endpoint():
    """ATM-T11: Terminal knot son computational layer ile dahil edilir."""
    assert assert_reference(84852.0).temperature_K == pytest.approx(186.946, abs=1e-12, rel=0)


@pytest.mark.parametrize("requested", [-5000.0001, 84852.0001, 90000.0,
    math.nextafter(-5000.0, -math.inf), math.nextafter(84852.0, math.inf)])
def test_domain_error_contract(requested):
    """ATM-T12: Finite domain hatası requested/min/max metadata'sını korur."""
    with pytest.raises(AtmosphereDomainError) as caught:
        PROVIDER.evaluate(geopotential_height_m=requested)
    error = caught.value
    assert isinstance(error, ValueError)
    assert error.requested_geopotential_height_m == requested
    assert error.minimum_geopotential_height_m == -5000.0
    assert error.maximum_geopotential_height_m == 84852.0
    assert "U.S. Standard Atmosphere 1976 lower atmosphere" in str(error)
    assert repr(requested) in str(error)
    assert "[-5000.0, 84852.0]" in str(error)


@pytest.mark.parametrize("invalid", [math.nan, math.inf, -math.inf])
def test_nonfinite_input_validation(invalid):
    """ATM-T13: Non-finite girdiler domain kontrolünden önce NAT-004 yoluna gider."""
    with pytest.raises(ValueError, match="geopotential_height_m must be finite") as caught:
        PROVIDER.evaluate(geopotential_height_m=invalid)
    assert not isinstance(caught.value, AtmosphereDomainError)


@pytest.mark.parametrize("requested", [90000.0, -6000.0])
def test_no_clamp_or_extrapolation(requested):
    """ATM-T14: Boundary state döndürmek yerine explicit domain failure."""
    with pytest.raises(AtmosphereDomainError):
        PROVIDER.evaluate(geopotential_height_m=requested)


def test_result_contract():
    """ATM-T15: Yalnız üç float içeren frozen, slots snapshot."""
    state = PROVIDER.evaluate(geopotential_height_m=10000.0)
    assert isinstance(state, DryAirAtmosphereState)
    assert [field.name for field in fields(state)] == [
        "temperature_K", "pressure_Pa", "density_kg_m3"
    ]
    assert not hasattr(state, "__dict__")
    for field in fields(state):
        assert type(getattr(state, field.name)) is float
        with pytest.raises(FrozenInstanceError):
            setattr(state, field.name, 1.0)


def test_recursive_base_generation():
    """ATM-T16: Her sonraki public base, öncekinin tam endpoint bağıntısına uyar."""
    heights = (0.0, *BOUNDARIES, 84852.0)
    lapse_rates = (-0.0065, 0.0, 0.001, 0.0028, 0.0, -0.0028, -0.002)
    for base, top, lapse in zip(heights, heights[1:], lapse_rates):
        start = PROVIDER.evaluate(geopotential_height_m=base)
        end = PROVIDER.evaluate(geopotential_height_m=top)
        expected_temperature = start.temperature_K + lapse * (top - base)
        if lapse:
            expected_pressure = start.pressure_Pa * (
                expected_temperature / start.temperature_K
            ) ** (-9.80665 / (287.053 * lapse))
        else:
            expected_pressure = start.pressure_Pa * math.exp(
                -9.80665 * (top - base) / (287.053 * start.temperature_K)
            )
        assert end.temperature_K == pytest.approx(expected_temperature, rel=2e-14, abs=0)
        assert end.pressure_Pa == pytest.approx(expected_pressure, rel=2e-14, abs=0)


def test_frozen_constants_and_parameterless_api():
    """Frozen constants, keyword-only girdi ve constructor override yasağı."""
    assert module.STANDARD_SEA_LEVEL_TEMPERATURE_K == 288.15
    assert module.STANDARD_SEA_LEVEL_PRESSURE_PA == 101325.0
    assert module.STANDARD_GRAVITY_M_S2 == 9.80665
    assert module.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K == 287.053
    assert module.USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M == -5000.0
    assert module.USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M == 84852.0
    assert not inspect.signature(USStandardAtmosphere1976Lower).parameters
    parameter = inspect.signature(PROVIDER.evaluate).parameters["geopotential_height_m"]
    assert parameter.kind is inspect.Parameter.KEYWORD_ONLY
    assert parameter.default is inspect.Parameter.empty
    with pytest.raises(TypeError):
        USStandardAtmosphere1976Lower(temperature_K=300.0)
    with pytest.raises(TypeError):
        PROVIDER.evaluate(10000.0)


@pytest.mark.parametrize("invalid_tp", [(0.0, 1.0), (-1.0, 1.0), (1.0, 0.0),
    (1.0, -1.0), (math.nan, 1.0), (1.0, math.inf)])
def test_invalid_results_are_not_repaired(invalid_tp, monkeypatch):
    """Defect injection: hesap hatası snapshot veya fallback olarak dönmez."""
    monkeypatch.setattr(module, "_temperature_pressure", lambda *_: invalid_tp)
    with pytest.raises(ValueError):
        PROVIDER.evaluate(geopotential_height_m=0.0)
