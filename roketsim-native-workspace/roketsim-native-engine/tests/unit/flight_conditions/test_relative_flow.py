"""NAT-010A FLOW-T01..T18: WORLD çıkarımı, validation ve mutation contract."""

import inspect

import numpy as np
import pytest

from roketsim_native.environment.wind import ConstantWindModel, NoWindModel
from roketsim_native.flight_conditions import relative_flow
from roketsim_native.flight_conditions.relative_flow import RelativeFlowCalculator
from roketsim_native.math.frames import FRAME_AXES, ReferenceFrame
from roketsim_native.math.vectors import as_vector


CALCULATOR = RelativeFlowCalculator()


@pytest.mark.parametrize("rocket,airmass,expected", [
    pytest.param([10, 20, 30], [0, 0, 0], [10, 20, 30], id="FLOW-T01"),
    pytest.param([0, 100, 0], [0, 20, 0], [0, 80, 0], id="FLOW-T02"),
    pytest.param([0, 100, 0], [0, -20, 0], [0, 120, 0], id="FLOW-T03"),
    pytest.param([0, 100, 0], [10, 0, 0], [-10, 100, 0], id="FLOW-T04"),
    pytest.param([10, -20, 30], [-5, 4, 3], [15, -24, 27], id="FLOW-T05"),
    pytest.param([10, -20, 30], [10, -20, 30], [0, 0, 0], id="FLOW-T06"),
])
def test_analytic_flow(rocket, airmass, expected):
    """FLOW-T01..T06: Bağımsız analitik fixture'lar çıkarım işaretini korur."""
    np.testing.assert_array_equal(CALCULATOR.evaluate(
        rocket_velocity_world_m_s=rocket, airmass_velocity_world_m_s=airmass
    ), expected)


def test_enu_order():
    """FLOW-T07: Metadata ile ayırt edilebilir East/North/Up bileşenleri."""
    assert [axis.meaning for axis in FRAME_AXES[ReferenceFrame.WORLD_ENU]] == [
        "East", "North", "Up"
    ]
    np.testing.assert_array_equal(CALCULATOR.evaluate(
        rocket_velocity_world_m_s=[10, 20, 30], airmass_velocity_world_m_s=[1, 2, 3]
    ), [9, 18, 27])


def test_toward_sign():
    """FLOW-T08: NAT-009H Southward TOWARD çıktısı olduğu gibi çıkarılır."""
    airmass = ConstantWindModel(airmass_velocity_world_m_s=[0, -10, 0]).evaluate()
    np.testing.assert_array_equal(CALCULATOR.evaluate(
        rocket_velocity_world_m_s=[0, 0, 0], airmass_velocity_world_m_s=airmass
    ), [0, 10, 0])


@pytest.mark.parametrize("source", [[1, 2, 3], (1, 2, 3), np.array([1, 2, 3])])
def test_vector_representation(source):
    """FLOW-T09: Existing float64 (3,) ndarray; yeni wrapper yok."""
    result = CALCULATOR.evaluate(
        rocket_velocity_world_m_s=source, airmass_velocity_world_m_s=NoWindModel().evaluate()
    )
    assert type(result) is type(as_vector([0, 0, 0], size=3))
    assert result.shape == (3,)
    assert result.dtype == np.float64


@pytest.mark.parametrize("field", [
    pytest.param("rocket_velocity_world_m_s", id="FLOW-T10"),
    pytest.param("airmass_velocity_world_m_s", id="FLOW-T11"),
])
def test_inputs_unchanged(field):
    """FLOW-T10/T11: Read-only girdiler üzerinde bile inplace işlem yapılmaz."""
    inputs = dict(rocket_velocity_world_m_s=np.array([10., 20., 30.]),
                  airmass_velocity_world_m_s=np.array([1., -2., 3.]))
    original = inputs[field].copy()
    inputs[field].flags.writeable = False
    CALCULATOR.evaluate(**inputs)
    np.testing.assert_array_equal(inputs[field], original)


def test_result_independence():
    """FLOW-T12: Sonuç mutation'ı girdilere veya sonraki çağrıya yayılmaz."""
    rocket, airmass = np.array([10., 20., 30.]), np.array([1., -2., 3.])
    result = CALCULATOR.evaluate(
        rocket_velocity_world_m_s=rocket, airmass_velocity_world_m_s=airmass
    )
    assert not np.shares_memory(result, rocket)
    assert not np.shares_memory(result, airmass)
    result[:] = np.nan
    np.testing.assert_array_equal(rocket, [10, 20, 30])
    np.testing.assert_array_equal(airmass, [1, -2, 3])
    np.testing.assert_array_equal(CALCULATOR.evaluate(
        rocket_velocity_world_m_s=rocket, airmass_velocity_world_m_s=airmass
    ), [9, 22, 27])


@pytest.mark.parametrize("field", [
    pytest.param("rocket_velocity_world_m_s", id="FLOW-T13"),
    pytest.param("airmass_velocity_world_m_s", id="FLOW-T14"),
])
@pytest.mark.parametrize("invalid", [[1, 2], [1, 2, 3, 4], [[1, 2, 3]], 1.])
def test_invalid_shape(field, invalid):
    """FLOW-T13/T14: Her iki input için shape reddi generic ValueError'dır."""
    inputs = dict(rocket_velocity_world_m_s=[0, 0, 0], airmass_velocity_world_m_s=[0, 0, 0])
    inputs[field] = invalid
    with pytest.raises(ValueError, match=field) as caught:
        CALCULATOR.evaluate(**inputs)
    assert type(caught.value) is ValueError


@pytest.mark.parametrize("field", [
    pytest.param("rocket_velocity_world_m_s", id="FLOW-T15"),
    pytest.param("airmass_velocity_world_m_s", id="FLOW-T16"),
])
@pytest.mark.parametrize("invalid", [np.nan, np.inf, -np.inf])
@pytest.mark.parametrize("index", [0, 1, 2])
def test_nonfinite(field, invalid, index):
    """FLOW-T15/T16: Her input'un her bileşeni finite olmalıdır."""
    inputs = dict(rocket_velocity_world_m_s=[0, 0, 0], airmass_velocity_world_m_s=[0, 0, 0])
    inputs[field][index] = invalid
    with pytest.raises(ValueError, match=field) as caught:
        CALCULATOR.evaluate(**inputs)
    assert type(caught.value) is ValueError


def test_determinism():
    """FLOW-T17: Aynı input aynı exact çıktıyı verir; fiziksel state yoktur."""
    inputs = dict(rocket_velocity_world_m_s=[1.5, -2., 3.], airmass_velocity_world_m_s=[.5, 4., -1.])
    for _ in range(5):
        np.testing.assert_array_equal(CALCULATOR.evaluate(**inputs), [1., -6., 4.])


def test_public_contract():
    """FLOW-T18: Yalnız iki keyword input ve WORLD vector; scalar/BODY API yok."""
    assert not inspect.signature(RelativeFlowCalculator).parameters
    parameters = inspect.signature(CALCULATOR.evaluate).parameters
    assert tuple(parameters) == ("rocket_velocity_world_m_s", "airmass_velocity_world_m_s")
    assert all(p.kind is inspect.Parameter.KEYWORD_ONLY and p.default is inspect.Parameter.empty
               for p in parameters.values())
    assert relative_flow.__all__ == ("RelativeFlowCalculator",)
    assert {name for name in dir(CALCULATOR) if not name.startswith('_')} == {"evaluate"}
    with pytest.raises(TypeError):
        CALCULATOR.evaluate([0, 0, 0], [0, 0, 0])


def test_overflow_fails_without_fallback():
    """Finite input çıkarımı taşarsa non-finite sonuç sessizce döndürülmez."""
    maximum = np.finfo(np.float64).max
    with pytest.raises(ValueError, match="relative_velocity_world_m_s"):
        CALCULATOR.evaluate(
            rocket_velocity_world_m_s=[maximum, 0, 0],
            airmass_velocity_world_m_s=[-maximum, 0, 0],
        )
