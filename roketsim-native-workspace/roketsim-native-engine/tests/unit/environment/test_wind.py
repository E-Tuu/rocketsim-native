"""NAT-009H WIND-T01..T20: steady wind contract ve mutation izolasyonu."""

import inspect

import numpy as np
import pytest

from roketsim_native.environment import wind
from roketsim_native.environment.wind import ConstantWindModel, NoWindModel
from roketsim_native.math.frames import FRAME_AXES, ReferenceFrame
from roketsim_native.math.vectors import as_vector, magnitude


def test_no_wind_exact():
    """WIND-T01: Demo baseline exact sıfır WORLD vektörüdür."""
    np.testing.assert_array_equal(NoWindModel().evaluate(), [0., 0., 0.])


@pytest.mark.parametrize("source", [[5., -2., 0.], (5., -2., 0.), np.array([5, -2, 0])])
def test_constant_exact(source):
    """WIND-T02: Desteklenen input temsilleri fiziksel bileşenleri korur."""
    np.testing.assert_array_equal(
        ConstantWindModel(airmass_velocity_world_m_s=source).evaluate(), [5., -2., 0.]
    )


def test_enu_order():
    """WIND-T03: WORLD metadata ile East/North/Up sırası birlikte korunur."""
    assert [axis.meaning for axis in FRAME_AXES[ReferenceFrame.WORLD_ENU]] == [
        "East", "North", "Up"
    ]
    east, north, up = ConstantWindModel(airmass_velocity_world_m_s=[1., 2., 3.]).evaluate()
    assert (east, north, up) == (1., 2., 3.)


def test_toward_south():
    """WIND-T04: Southward hız FROM bearing gibi ters çevrilmez."""
    np.testing.assert_array_equal(
        ConstantWindModel(airmass_velocity_world_m_s=[0., -10., 0.]).evaluate(),
        [0., -10., 0.],
    )


def test_vertical_allowed():
    """WIND-T05: Saf Up hızı geçerlidir."""
    np.testing.assert_array_equal(
        ConstantWindModel(airmass_velocity_world_m_s=[0., 0., 3.]).evaluate(), [0., 0., 3.]
    )


@pytest.fixture(params=["zero", "constant"])
def model(request):
    return NoWindModel() if request.param == "zero" else ConstantWindModel(
        airmass_velocity_world_m_s=[5., -2., 3.]
    )


def test_determinism(model):
    """WIND-T06: Tekrarlanan çağrılar exact aynı fiziksel sonucu üretir."""
    expected = model.evaluate()
    for _ in range(5):
        np.testing.assert_array_equal(model.evaluate(), expected)


def test_constructor_copy():
    """WIND-T07: Caller array değişikliği model state'ine ulaşamaz."""
    source = np.array([5., -2., 3.])
    model = ConstantWindModel(airmass_velocity_world_m_s=source)
    source[:] = np.nan
    np.testing.assert_array_equal(model.evaluate(), [5., -2., 3.])


def test_evaluate_copy(model):
    """WIND-T08: Her result önceki result'tan ve model state'inden bağımsızdır."""
    first, second = model.evaluate(), model.evaluate()
    assert not np.shares_memory(first, second)
    first[:] = 999.
    np.testing.assert_array_equal(model.evaluate(), second)


def test_existing_vector(model):
    """WIND-T09: Yeni wrapper yok; existing (3,) float64 ndarray kullanılır."""
    result = model.evaluate()
    assert type(result) is type(as_vector([0., 0., 0.], size=3))
    assert result.shape == (3,)
    assert result.dtype == np.float64


@pytest.mark.parametrize("source", [[1, 2], [1, 2, 3, 4], [[1, 2, 3]], 1.])
def test_invalid_shape(source):
    """WIND-T10: Yanlış boyutlar flatten veya fallback olmadan reddedilir."""
    with pytest.raises(ValueError, match="airmass_velocity_world_m_s"):
        ConstantWindModel(airmass_velocity_world_m_s=source)


@pytest.mark.parametrize("value", [np.nan, np.inf, -np.inf])
@pytest.mark.parametrize("index", [0, 1, 2])
def test_nonfinite(value, index):
    """WIND-T11: Her bileşen existing finite validation yolundan geçer."""
    source = [0., 0., 0.]
    source[index] = value
    with pytest.raises(ValueError, match="airmass_velocity_world_m_s"):
        ConstantWindModel(airmass_velocity_world_m_s=source)


def test_no_speed_limit():
    """WIND-T12: Finite büyük hız meteorolojik gerekçeyle sınırlandırılmaz."""
    np.testing.assert_array_equal(
        ConstantWindModel(airmass_velocity_world_m_s=[500., 0., 0.]).evaluate(), [500., 0., 0.]
    )


def test_no_normalization():
    """WIND-T13: Hız yön vektörüne normalize edilmez."""
    assert magnitude(ConstantWindModel(airmass_velocity_world_m_s=[10., 0., 0.]).evaluate()) == 10.


def test_no_horizontal_projection():
    """WIND-T14: Karışık yatay/dikey hız bütün bileşenleriyle korunur."""
    np.testing.assert_array_equal(
        ConstantWindModel(airmass_velocity_world_m_s=[4., -2., -3.]).evaluate(), [4., -2., -3.]
    )


def test_no_wind_signature():
    """WIND-T15: Constructor ve bound evaluate parametresizdir."""
    assert not inspect.signature(NoWindModel).parameters
    assert not inspect.signature(NoWindModel().evaluate).parameters
    with pytest.raises(TypeError):
        NoWindModel(time_s=0.)


def test_constant_runtime_signature():
    """WIND-T16: Sabit model runtime fiziksel input kabul etmez."""
    model = ConstantWindModel(airmass_velocity_world_m_s=[0., 0., 0.])
    assert not inspect.signature(model.evaluate).parameters
    with pytest.raises(TypeError):
        model.evaluate(rocket_velocity=[1., 2., 3.])


def test_result_scope(model):
    """WIND-T17: Yalnız evaluate ve tek hız array'i; downstream state yok."""
    assert {name for name in dir(model) if not name.startswith('_')} == {"evaluate"}
    assert isinstance(model.evaluate(), np.ndarray)


def test_keyword_only_semantic_constructor():
    """WIND-T18: Tek zorunlu keyword WORLD hızıdır; FROM/angle API yok."""
    parameters = inspect.signature(ConstantWindModel).parameters
    assert tuple(parameters) == ("airmass_velocity_world_m_s",)
    parameter = parameters["airmass_velocity_world_m_s"]
    assert parameter.kind is inspect.Parameter.KEYWORD_ONLY
    assert parameter.default is inspect.Parameter.empty
    with pytest.raises(TypeError):
        ConstantWindModel([1., 2., 3.])
    with pytest.raises(TypeError):
        ConstantWindModel()


def test_public_exports():
    """WIND-T19: Public model yüzeyi yalnız iki steady model içerir."""
    assert wind.__all__ == ("NoWindModel", "ConstantWindModel")


@pytest.mark.parametrize("source", [[1, 2], [np.nan, 0., 0.]])
def test_existing_error_contract(source):
    """WIND-T20: Yeni domain hierarchy değil, exact generic ValueError."""
    with pytest.raises(ValueError) as caught:
        ConstantWindModel(airmass_velocity_world_m_s=source)
    assert type(caught.value) is ValueError
