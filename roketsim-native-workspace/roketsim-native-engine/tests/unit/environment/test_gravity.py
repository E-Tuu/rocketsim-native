"""NAT-009F GRAV-T01..T12: fixed gravity, ENU işareti ve mevcut vector contract."""

import inspect

import numpy as np
import pytest

from roketsim_native.environment import atmosphere, gravity
from roketsim_native.environment.gravity import ConstantGravityModel
from roketsim_native.math.frames import FRAME_AXES, ReferenceFrame
from roketsim_native.math.numerical import is_finite
from roketsim_native.math.vectors import as_vector, magnitude


MODEL = ConstantGravityModel()


def test_exact_world_vector():
    """GRAV-T01: Existing vector API ile exact frozen fiziksel bileşenler."""
    np.testing.assert_array_equal(MODEL.evaluate(), as_vector([0., 0., -9.80665], size=3))


def test_gravity_magnitude():
    """GRAV-T02: Ayrı magnitude state yok; norm vector foundation'dan türetilir."""
    assert magnitude(MODEL.evaluate()) == 9.80665


def test_enu_sign():
    """GRAV-T03: +z_W=Up ile downward gravity sign regression."""
    assert [axis.meaning for axis in FRAME_AXES[ReferenceFrame.WORLD_ENU]] == [
        "East", "North", "Up"
    ]
    east, north, up = MODEL.evaluate()
    assert east == 0.0
    assert north == 0.0
    assert up < 0.0
    assert up == -9.80665


def test_determinism():
    """GRAV-T04: Aynı ve farklı model instance'ları exact aynı sonucu üretir."""
    expected = MODEL.evaluate()
    for _ in range(5):
        np.testing.assert_array_equal(MODEL.evaluate(), expected)
        np.testing.assert_array_equal(ConstantGravityModel().evaluate(), expected)


def test_no_input_contract():
    """GRAV-T05: Bound evaluate imzasında hiçbir fiziksel girdi yoktur."""
    assert not inspect.signature(MODEL.evaluate).parameters
    with pytest.raises(TypeError):
        MODEL.evaluate(0.0)


def test_existing_vector_type():
    """GRAV-T06: Yeni wrapper yerine existing (3,) float64 ndarray."""
    result = MODEL.evaluate()
    assert type(result) is type(as_vector([0., 0., 0.], size=3))
    assert isinstance(result, np.ndarray)
    assert result.shape == (3,)
    assert result.dtype == np.float64


def test_shared_standard_gravity_authority(monkeypatch):
    """GRAV-T07: Test-only perturbation ortak authority tüketimini kanıtlar.

    Bu runtime configuration değildir; monkeypatch test sonunda geri alınır.
    """
    assert atmosphere.STANDARD_GRAVITY_M_S2 == 9.80665
    monkeypatch.setattr(atmosphere, "STANDARD_GRAVITY_M_S2", 2 * atmosphere.STANDARD_GRAVITY_M_S2)
    assert MODEL.evaluate()[2] == -atmosphere.STANDARD_GRAVITY_M_S2


def test_finite_output():
    """GRAV-T08: NAT-004 finite predicate bütün bileşenler için geçerlidir."""
    assert all(is_finite(value) for value in MODEL.evaluate())


def test_analytic_velocity():
    """GRAV-T09: Yalnız test-side v=v0+g*t; production dynamics değildir."""
    velocity_z_m_s = 0.0 + MODEL.evaluate()[2] * 2.0
    assert velocity_z_m_s == pytest.approx(-19.6133, rel=0, abs=1e-14)


def test_analytic_position():
    """GRAV-T10: Yalnız test-side z=z0+v0*t+g*t²/2."""
    position_z_m = 0.0 + 0.0 * 2.0 + 0.5 * MODEL.evaluate()[2] * 2.0**2
    assert position_z_m == pytest.approx(-19.6133, rel=0, abs=1e-14)


def test_scope_contract():
    """GRAV-T11: Public model yalnız evaluate; domain error/higher-fidelity API yok."""
    assert gravity.__all__ == ("ConstantGravityModel",)
    assert {name for name in dir(MODEL) if not name.startswith('_')} == {"evaluate"}


@pytest.mark.parametrize("option", ["gravity_magnitude", "latitude_rad", "altitude_m", "time_s", "velocity_world"])
def test_no_configuration_or_fallback(option):
    """GRAV-T12: Custom g ve future physical input'lar kabul edilmez."""
    assert not inspect.signature(ConstantGravityModel).parameters
    with pytest.raises(TypeError):
        ConstantGravityModel(**{option: 0.0})
    with pytest.raises(TypeError):
        MODEL.evaluate(**{option: 0.0})
    np.testing.assert_array_equal(MODEL.evaluate(), [0., 0., -9.80665])


def test_results_are_independent():
    """Caller mutation sonraki değerlendirmeyi veya başka result'u etkileyemez."""
    first = MODEL.evaluate()
    second = MODEL.evaluate()
    assert not np.shares_memory(first, second)
    first[:] = np.nan
    np.testing.assert_array_equal(second, [0., 0., -9.80665])
    np.testing.assert_array_equal(MODEL.evaluate(), second)
