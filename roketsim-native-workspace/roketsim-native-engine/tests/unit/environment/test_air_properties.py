"""NAT-009D AIR-T01..T18: acoustic/viscosity bağıntıları ve semantic sınırlar."""

from dataclasses import FrozenInstanceError, fields
from decimal import Decimal, localcontext
import inspect
import math
import sys

import pytest

from roketsim_native.environment import air_properties as module
from roketsim_native.environment import atmosphere
from roketsim_native.environment.air_properties import (
    AirPropertiesDomainError, DryAirProperties, DryAirPropertiesCalculator,
)
from roketsim_native.environment.atmosphere import DryAirAtmosphereState


CALCULATOR = DryAirPropertiesCalculator()
ATMOSPHERE = atmosphere.USStandardAtmosphere1976Lower()


def assert_equations(state):
    """Frozen bağıntıları production helper kullanmadan 50-digit Decimal ile doğrula."""
    properties = CALCULATOR.evaluate(atmosphere_state=state)
    with localcontext() as context:
        context.prec = 50
        temperature = Decimal(str(state.temperature_K))
        density = Decimal(str(state.density_kg_m3))
        sound = (Decimal("1.4") * Decimal("287.053") * temperature).sqrt()
        viscosity = Decimal("1.458e-6") * temperature ** Decimal("1.5") / (
            temperature + Decimal("110.4")
        )
        expected = (sound, viscosity, viscosity / density, Decimal("1.4"))
    for field, reference in zip(fields(properties), expected):
        actual = getattr(properties, field.name)
        assert math.isfinite(actual) and actual > 0
        assert actual == pytest.approx(float(reference), rel=3e-15, abs=0)
    return properties


def test_sea_level_sound():
    """AIR-T01: Frozen sea-level sound fixture."""
    state = ATMOSPHERE.evaluate(geopotential_height_m=0.0)
    assert assert_equations(state).speed_of_sound_m_s == pytest.approx(
        340.2940650819523, rel=3e-15, abs=0
    )


def test_sea_level_dynamic_viscosity():
    """AIR-T02: Frozen Sutherland sea-level fixture."""
    state = ATMOSPHERE.evaluate(geopotential_height_m=0.0)
    assert assert_equations(state).dynamic_viscosity_Pa_s == pytest.approx(
        1.789380278077583e-5, rel=3e-15, abs=0
    )


def test_sea_level_kinematic_viscosity():
    """AIR-T03: rho gerçek NAT-009C snapshot'ından alınır."""
    state = ATMOSPHERE.evaluate(geopotential_height_m=0.0)
    # Talepteki yaklaşık nu yerine gerçek C density'si; Decimal oracle da doğrulanır.
    result = assert_equations(state)
    assert result.kinematic_viscosity_m2_s == result.dynamic_viscosity_Pa_s / state.density_kg_m3


@pytest.mark.parametrize("temperature_K", [186.946, 216.65, 255.65, 270.65, 320.65, 500.0])
def test_sound_equation(temperature_K):
    """AIR-T04: Birden fazla layer sıcaklığı ve C aralığı dışı pozitif T."""
    result = CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(temperature_K, 1.0, 1.0))
    assert result.speed_of_sound_m_s == pytest.approx(
        math.sqrt(1.4 * 287.053 * temperature_K), rel=3e-15, abs=0
    )


@pytest.mark.parametrize("temperature_K", [186.946, 216.65, 255.65, 288.15, 320.65])
def test_sutherland_equation(temperature_K):
    """AIR-T05: İkinci production parametrizasyonu olmadan doğrudan bağıntı."""
    result = CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(temperature_K, 1.0, 1.0))
    assert result.dynamic_viscosity_Pa_s == pytest.approx(
        1.458e-6 * temperature_K ** 1.5 / (temperature_K + 110.4), rel=3e-15, abs=0
    )


@pytest.mark.parametrize("density_kg_m3", [1e-5, 0.2, 1.0, 2.0])
def test_kinematic_relation(density_kg_m3):
    """AIR-T06: Density pressure'dan hesaplanmaz, supplied rho doğrudan kullanılır."""
    state = DryAirAtmosphereState(288.15, 123.0, density_kg_m3)
    result = assert_equations(state)
    assert result.kinematic_viscosity_m2_s == result.dynamic_viscosity_Pa_s / density_kg_m3


@pytest.mark.parametrize("geopotential_height_m", [0.0, 5000.0, 15000.0, 40000.0, 60000.0])
def test_atmosphere_integration(geopotential_height_m):
    """AIR-T07: C -> D zinciri; thermodynamic snapshot değiştirilmez."""
    state = ATMOSPHERE.evaluate(geopotential_height_m=geopotential_height_m)
    before = (state.temperature_K, state.pressure_Pa, state.density_kg_m3)
    assert_equations(state)
    assert before == (state.temperature_K, state.pressure_Pa, state.density_kg_m3)


def test_lower_atmosphere_endpoint():
    """AIR-T08: -5 km C state'i, rounded fixture yerine exact-equation oracle."""
    state = ATMOSPHERE.evaluate(geopotential_height_m=-5000.0)
    assert state.temperature_K == pytest.approx(320.65, rel=0, abs=1e-12)
    assert_equations(state)


def test_upper_atmosphere_endpoint():
    """AIR-T09: 84852 m C state'inin gerçek density'siyle viscosity kontrolü."""
    assert_equations(ATMOSPHERE.evaluate(geopotential_height_m=84852.0))


@pytest.mark.parametrize("temperature_K", [0.0, -1.0])
def test_temperature_domain(temperature_K):
    """AIR-T10: Finite non-positive T domain hatasıdır."""
    with pytest.raises(AirPropertiesDomainError) as caught:
        CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(temperature_K, 1.0, 1.0))
    assert caught.value.field_name == "temperature_K"


@pytest.mark.parametrize("density_kg_m3", [0.0, -1.0])
def test_density_domain(density_kg_m3):
    """AIR-T11: Finite non-positive rho domain hatasıdır."""
    with pytest.raises(AirPropertiesDomainError) as caught:
        CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(288.15, 1.0, density_kg_m3))
    assert caught.value.field_name == "density_kg_m3"


@pytest.mark.parametrize("field_name", ["temperature_K", "density_kg_m3"])
@pytest.mark.parametrize("invalid", [math.nan, math.inf, -math.inf])
def test_nonfinite_prerequisites(field_name, invalid):
    """AIR-T12: Non-finite numeric hata, physical domain hatasından ayrıdır."""
    values = dict(temperature_K=288.15, pressure_Pa=1.0, density_kg_m3=1.0)
    values[field_name] = invalid
    with pytest.raises(ValueError, match=field_name) as caught:
        CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(**values))
    assert not isinstance(caught.value, AirPropertiesDomainError)


@pytest.mark.parametrize("field_name", ["temperature_K", "density_kg_m3"])
def test_structured_domain_error(field_name):
    """AIR-T13: Field/value ve strictly-positive public error sözleşmesi."""
    values = dict(temperature_K=288.15, pressure_Pa=1.0, density_kg_m3=1.0)
    values[field_name] = -3.0
    with pytest.raises(AirPropertiesDomainError) as caught:
        CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(**values))
    assert isinstance(caught.value, ValueError)
    assert caught.value.field_name == field_name
    assert caught.value.value == -3.0
    assert "strictly positive" in str(caught.value)


def test_result_contract():
    """AIR-T14: Frozen snapshot gamma'yı aynı kuru-hava authority'sinden sunar."""
    result = CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(288.15, 1.0, 1.0))
    assert isinstance(result, DryAirProperties)
    assert [field.name for field in fields(result)] == [
        "speed_of_sound_m_s", "dynamic_viscosity_Pa_s", "kinematic_viscosity_m2_s",
        "specific_heat_ratio",
    ]
    for field in fields(result):
        assert type(getattr(result, field.name)) is float
        with pytest.raises(FrozenInstanceError):
            setattr(result, field.name, 1.0)


@pytest.mark.parametrize("temperature_K, density_kg_m3", [(-288.15, 1.0), (288.15, -1.0), (0.0, 0.0)])
def test_no_hidden_repair(temperature_K, density_kg_m3):
    """AIR-T15: abs, epsilon veya fallback ile invalid input kurtarılmaz."""
    with pytest.raises(AirPropertiesDomainError):
        CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(temperature_K, 1.0, density_kg_m3))


def test_no_altitude_coupling():
    """AIR-T16: Parametresiz constructor, yalnız keyword-only atmosphere_state."""
    assert not inspect.signature(DryAirPropertiesCalculator).parameters
    parameters = inspect.signature(CALCULATOR.evaluate).parameters
    assert list(parameters) == ["atmosphere_state"]
    assert parameters["atmosphere_state"].kind is inspect.Parameter.KEYWORD_ONLY
    with pytest.raises(TypeError):
        DryAirPropertiesCalculator(gamma=1.5)
    with pytest.raises(TypeError):
        CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(288.15, 1.0, 1.0), altitude=0.0)


def test_scope_contract():
    """AIR-T17: Mach/Re/q/AoA result/API'ye eklenmez."""
    assert set(module.__all__) == {
        "DryAirPropertiesCalculator", "DryAirProperties", "AirPropertiesDomainError",
        "DRY_AIR_SPECIFIC_HEAT_RATIO", "SUTHERLAND_BETA", "SUTHERLAND_CONSTANT_K",
    }
    assert module.DRY_AIR_SPECIFIC_HEAT_RATIO == 1.4
    assert module.SUTHERLAND_BETA == 1.458e-6
    assert module.SUTHERLAND_CONSTANT_K == 110.4


def test_shared_gas_constant_authority(monkeypatch):
    """AIR-T18: Test-only perturbation ile C'nin tek R kaynağının tüketildiğini izle."""
    state = DryAirAtmosphereState(288.15, 1.0, 1.0)
    original = CALCULATOR.evaluate(atmosphere_state=state)
    monkeypatch.setattr(atmosphere, "DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K",
                        4 * atmosphere.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K)
    changed = CALCULATOR.evaluate(atmosphere_state=state)
    assert changed.speed_of_sound_m_s == pytest.approx(2 * original.speed_of_sound_m_s, rel=3e-15, abs=0)
    assert changed.dynamic_viscosity_Pa_s == original.dynamic_viscosity_Pa_s
    assert changed.kinematic_viscosity_m2_s == original.kinematic_viscosity_m2_s


@pytest.mark.parametrize("pressure_Pa", [0.0, -1.0, math.nan, math.inf])
def test_pressure_not_consumed(pressure_Pa):
    """Pressure validation C'ye aittir; D yalnız T/rho tüketir."""
    result = CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(288.15, pressure_Pa, 1.0))
    assert result == CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(288.15, 101325.0, 1.0))


def test_large_finite_temperature():
    """Cebirsel çarpanlama gereksiz intermediate overflow üretmez."""
    assert_equations(DryAirAtmosphereState(sys.float_info.max, 1.0, 1.0))


@pytest.mark.parametrize("temperature_K, density_kg_m3", [(288.15, math.ulp(0.0)), (math.ulp(0.0), 1.0)])
def test_unrepresentable_results_fail(temperature_K, density_kg_m3):
    """Float overflow/underflow sonuçları sıfır veya infinity snapshot olamaz."""
    with pytest.raises(ValueError) as caught:
        CALCULATOR.evaluate(atmosphere_state=DryAirAtmosphereState(temperature_K, 1.0, density_kg_m3))
    assert not isinstance(caught.value, AirPropertiesDomainError)
