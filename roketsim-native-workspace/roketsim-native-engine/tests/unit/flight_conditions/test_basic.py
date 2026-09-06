"""NAT-010B FC-T01..T20: analitik koşullar, upstream sahiplik ve güvenli domain."""

from dataclasses import FrozenInstanceError, fields, replace
import inspect

import numpy as np
import pytest

from roketsim_native.environment.atmosphere import DryAirAtmosphereState, USStandardAtmosphere1976Lower
from roketsim_native.environment.air_properties import DryAirProperties, DryAirPropertiesCalculator
from roketsim_native.flight_conditions import basic
from roketsim_native.flight_conditions.basic import (
    BasicFlightConditions, BasicFlightConditionsCalculator, FlightConditionsDomainError,
)
from roketsim_native.flight_conditions.relative_flow import RelativeFlowCalculator


CALCULATOR = BasicFlightConditionsCalculator()


@pytest.fixture
def inputs():
    """Basit analitik fixture; tüketilmeyen alanlar equation otoritesi değildir."""
    return dict(relative_velocity_world_m_s=np.array([3., 4., 12.]),
                atmosphere_state=DryAirAtmosphereState(288., 100000., 2.),
                air_properties=DryAirProperties(260., 0.123, 0.00002),
                reference_length_m=0.2)


def test_full_3d_norm(inputs):
    """FC-T01: Dikey 12 m/s bileşeni normun parçasıdır."""
    assert CALCULATOR.evaluate(**inputs).airspeed_m_s == 13.


def test_zero_state(inputs):
    """FC-T02: Stationary no-wind state dört exact sıfır üretir."""
    inputs['relative_velocity_world_m_s'] = [0, 0, 0]
    assert CALCULATOR.evaluate(**inputs) == BasicFlightConditions(0., 0., 0., 0.)


def test_mach_equation(inputs):
    """FC-T03: Bağımsız analitik V/a beklentisi."""
    assert CALCULATOR.evaluate(**inputs).mach == pytest.approx(13. / 260., rel=1e-14)


def test_reynolds_equation(inputs):
    """FC-T04: Re yalnız verilen nu'yu kullanır; mu/rho yeniden türetilmez."""
    assert CALCULATOR.evaluate(**inputs).reynolds == pytest.approx(13. * 0.2 / 0.00002, rel=1e-14)


def test_pressure_equation(inputs):
    """FC-T05: q=0.5*rho*V² bağımsız analitik fixture."""
    assert CALCULATOR.evaluate(**inputs).dynamic_pressure_Pa == 169.


def test_upstream_integration():
    """FC-T06: C→D ve Relative Flow→B gerçek provider zinciri."""
    state = USStandardAtmosphere1976Lower().evaluate(geopotential_height_m=0.)
    properties = DryAirPropertiesCalculator().evaluate(atmosphere_state=state)
    relative = RelativeFlowCalculator().evaluate(
        rocket_velocity_world_m_s=[0, 120, 0], airmass_velocity_world_m_s=[0, 20, 0]
    )
    result = CALCULATOR.evaluate(relative_velocity_world_m_s=relative,
        atmosphere_state=state, air_properties=properties, reference_length_m=0.1)
    assert result.airspeed_m_s == 100.
    assert result.mach == pytest.approx(100. / properties.speed_of_sound_m_s, rel=1e-14)
    assert result.reynolds == pytest.approx(10. / properties.kinematic_viscosity_m2_s, rel=1e-14)
    assert result.dynamic_pressure_Pa == pytest.approx(5000. * state.density_kg_m3, rel=1e-14)
    assert result.mach == pytest.approx(0.2938634853, rel=0, abs=5e-11)
    assert result.reynolds == pytest.approx(684594.26, rel=0, abs=0.005)
    assert result.dynamic_pressure_Pa == pytest.approx(6124.9973, rel=0, abs=5e-5)


def test_direction_independence(inputs):
    """FC-T07: Eşit normdaki yönler attitude bağımlılığı üretmez."""
    expected = CALCULATOR.evaluate(**inputs)
    inputs['relative_velocity_world_m_s'] = [-12., 3., -4.]
    assert CALCULATOR.evaluate(**inputs) == expected


def test_length_linearity(inputs):
    """FC-T08: L yalnız Reynolds'u doğrusal değiştirir."""
    original = CALCULATOR.evaluate(**inputs)
    inputs['reference_length_m'] *= 2
    assert CALCULATOR.evaluate(**inputs) == replace(original, reynolds=2 * original.reynolds)


@pytest.mark.parametrize('field,factor', [
    pytest.param('mach', 2, id='FC-T09'),
    pytest.param('dynamic_pressure_Pa', 4, id='FC-T10'),
    pytest.param('reynolds', 2, id='FC-T11'),
])
def test_speed_scaling(inputs, field, factor):
    """FC-T09..11: Sabit atmosferde doğrusal ve karesel hız ölçekleri."""
    original = CALCULATOR.evaluate(**inputs)
    inputs['relative_velocity_world_m_s'] *= 2
    assert getattr(CALCULATOR.evaluate(**inputs), field) == factor * getattr(original, field)


def set_prerequisite(inputs, field, value):
    """Test girdisini immutable upstream state contract'ını bozmadan değiştir."""
    if field == 'density_kg_m3':
        inputs['atmosphere_state'] = replace(inputs['atmosphere_state'], **{field: value})
    elif field == 'reference_length_m':
        inputs[field] = value
    else:
        inputs['air_properties'] = replace(inputs['air_properties'], **{field: value})


@pytest.mark.parametrize('field', [
    pytest.param('density_kg_m3', id='FC-T12'),
    pytest.param('speed_of_sound_m_s', id='FC-T13'),
    pytest.param('kinematic_viscosity_m2_s', id='FC-T14'),
    pytest.param('reference_length_m', id='FC-T15'),
])
@pytest.mark.parametrize('value', [0., -1.])
def test_positive_domains(inputs, field, value):
    """FC-T12..15/17: Finite invalid alan/value structured domain hatasıdır."""
    set_prerequisite(inputs, field, value)
    with pytest.raises(FlightConditionsDomainError) as caught:
        CALCULATOR.evaluate(**inputs)
    assert caught.value.field_name == field
    assert caught.value.value == value
    assert field in str(caught.value)


@pytest.mark.parametrize('field', ['density_kg_m3', 'speed_of_sound_m_s',
    'kinematic_viscosity_m2_s', 'reference_length_m', 0, 1, 2])
@pytest.mark.parametrize('value', [np.nan, np.inf, -np.inf])
def test_nonfinite(inputs, field, value):
    """FC-T16: Non-finite input domain hatası değil generic ValueError'dır."""
    if isinstance(field, int):
        inputs['relative_velocity_world_m_s'][field] = value
    else:
        set_prerequisite(inputs, field, value)
    with pytest.raises(ValueError) as caught:
        CALCULATOR.evaluate(**inputs)
    assert type(caught.value) is ValueError


def test_result_contract(inputs):
    """FC-T18: Frozen/slots snapshot yalnız dört scalar alan içerir."""
    result = CALCULATOR.evaluate(**inputs)
    assert [f.name for f in fields(result)] == [
        'airspeed_m_s', 'mach', 'reynolds', 'dynamic_pressure_Pa'
    ]
    assert all(type(getattr(result, f.name)) is float for f in fields(result))
    with pytest.raises(FrozenInstanceError):
        result.mach = 999.
    assert not hasattr(result, '__dict__')


def test_immutability_determinism(inputs):
    """FC-T19: Read-only vector ve immutable upstream state değişmeden kalır."""
    vector = inputs['relative_velocity_world_m_s']
    original = vector.copy()
    vector.flags.writeable = False
    state, properties = inputs['atmosphere_state'], inputs['air_properties']
    expected = CALCULATOR.evaluate(**inputs)
    for _ in range(5):
        assert CALCULATOR.evaluate(**inputs) == expected
        assert BasicFlightConditionsCalculator().evaluate(**inputs) == expected
    np.testing.assert_array_equal(vector, original)
    assert state == DryAirAtmosphereState(288., 100000., 2.)
    assert properties == DryAirProperties(260., 0.123, 0.00002)


def test_scope_contract():
    """FC-T20: Yalnız keyword input'lar; AoA/BODY/aero API yok."""
    assert not inspect.signature(BasicFlightConditionsCalculator).parameters
    parameters = inspect.signature(CALCULATOR.evaluate).parameters
    assert tuple(parameters) == ('relative_velocity_world_m_s', 'atmosphere_state',
                                'air_properties', 'reference_length_m')
    assert all(p.kind is inspect.Parameter.KEYWORD_ONLY and p.default is inspect.Parameter.empty
               for p in parameters.values())
    assert set(basic.__all__) == {'BasicFlightConditions', 'BasicFlightConditionsCalculator',
                                'FlightConditionsDomainError'}
    assert {name for name in dir(CALCULATOR) if not name.startswith('_')} == {'evaluate'}


@pytest.mark.parametrize('invalid', [[1, 2], [1, 2, 3, 4], [[1, 2, 3]], 1.])
def test_vector_shape(inputs, invalid):
    """Existing vector validation shape'i zorunlu kılar; flatten yok."""
    inputs['relative_velocity_world_m_s'] = invalid
    with pytest.raises(ValueError, match='relative_velocity_world_m_s'):
        CALCULATOR.evaluate(**inputs)


def test_unconsumed_fields_are_not_authorities(inputs):
    """T/p/mu tüketilmez; nu ve rho supplied authority olarak kalır."""
    expected = CALCULATOR.evaluate(**inputs)
    inputs['atmosphere_state'] = replace(inputs['atmosphere_state'], temperature_K=np.nan, pressure_Pa=np.nan)
    inputs['air_properties'] = replace(inputs['air_properties'], dynamic_viscosity_Pa_s=np.nan)
    assert CALCULATOR.evaluate(**inputs) == expected


def test_no_upper_limit(inputs):
    """Finite büyük hız/uzunluğa artificial üst sınır uygulanmaz."""
    inputs['relative_velocity_world_m_s'] = [0, 0, 1000000.]
    inputs['reference_length_m'] = 1000000.
    result = CALCULATOR.evaluate(**inputs)
    assert result.airspeed_m_s == 1000000.
    assert result.reynolds == pytest.approx(1e12 / 0.00002, rel=1e-14)


@pytest.mark.parametrize('field,value', [('speed_of_sound_m_s', 5e-324),
    ('kinematic_viscosity_m2_s', 5e-324), ('reference_length_m', 1e308),
    ('density_kg_m3', 1e308)])
def test_derived_overflow(inputs, field, value):
    """Finite prerequisite taşan sonuç üretirse explicit numerical failure."""
    set_prerequisite(inputs, field, value)
    with pytest.raises(ValueError) as caught:
        CALCULATOR.evaluate(**inputs)
    assert type(caught.value) is ValueError


def test_positive_speed_underflow_is_not_zero_fallback(inputs):
    """Pozitif hızın underflow sonucu sahte stationary state olmasına izin yok."""
    inputs['relative_velocity_world_m_s'] = [1e-200, 0, 0]
    with pytest.raises(ValueError):
        CALCULATOR.evaluate(**inputs)


def test_zero_speed_still_validates_prerequisites(inputs):
    """Sıfır hız domain doğrulamasını atlamaz."""
    inputs['relative_velocity_world_m_s'] = [0, 0, 0]
    inputs['reference_length_m'] = 0.
    with pytest.raises(FlightConditionsDomainError):
        CALCULATOR.evaluate(**inputs)
