"""NAT-011A GEO-T01..T25: kanonik yerleşim ve demo geometry sözleşmesi."""

from dataclasses import FrozenInstanceError, fields, replace
import inspect
from math import pi

import pytest

from roketsim_native.geometry import models, resolver
from roketsim_native.geometry.models import (
    ConicalNoseGeometry, CylindricalBodyGeometry,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
)
from roketsim_native.geometry.resolver import (
    GeometryResolver, GeometryValidationError, ResolvedRocketGeometry,
)
from roketsim_native.math.frames import GEOMETRY_AXIAL_CONVENTION


RESOLVER = GeometryResolver()


@pytest.fixture
def geometry():
    return SingleStageRocketGeometry(
        airframe_diameter_m=0.100,
        nose=ConicalNoseGeometry(length_m=0.300),
        body=CylindricalBodyGeometry(length_m=0.700),
        fins=TrapezoidalFinSetGeometry(fin_count=4, root_chord_m=0.180,
            tip_chord_m=0.080, semi_span_m=0.120, tip_leading_edge_offset_x_m=0.050,
            root_leading_edge_x_geo_m=0.720, thickness_m=0.003),
    )


@pytest.mark.parametrize('name,expected', [
    pytest.param('nose_start_x_geo_m', 0., id='GEO-T01-start'),
    pytest.param('nose_end_x_geo_m', .3, id='GEO-T01-end'),
    pytest.param('body_start_x_geo_m', .3, id='GEO-T02-start'),
    pytest.param('body_end_x_geo_m', 1., id='GEO-T02-end'),
    pytest.param('fin_root_trailing_edge_x_geo_m', .9, id='GEO-T03'),
    pytest.param('fin_tip_leading_edge_x_geo_m', .77, id='GEO-T04'),
    pytest.param('fin_tip_trailing_edge_x_geo_m', .85, id='GEO-T05'),
    pytest.param('reference_diameter_m', .1, id='GEO-T06'),
    pytest.param('reference_length_m', .1, id='GEO-T07'),
    pytest.param('reference_area_m2', .007853981633974483, id='GEO-T08'),
    pytest.param('overall_length_m', 1., id='GEO-T09'),
])
def test_reference_fixture(geometry, name, expected):
    """GEO-T01..09: Verilen fixture'ın bağımsız sayısal beklentileri."""
    result = RESOLVER.resolve(rocket_geometry=geometry)
    assert getattr(result, name) == pytest.approx(expected, rel=2e-15, abs=0)


def test_aft_overhang(geometry):
    """GEO-T10: Root tam bağlıyken tip tail'i geçebilir; overall extent uzar."""
    geometry = replace(geometry, fins=replace(geometry.fins,
        root_leading_edge_x_geo_m=.82, tip_leading_edge_offset_x_m=.1, tip_chord_m=.12))
    result = RESOLVER.resolve(rocket_geometry=geometry)
    assert result.fin_root_trailing_edge_x_geo_m == 1.
    assert result.fin_tip_leading_edge_x_geo_m == pytest.approx(.92, rel=2e-15)
    assert result.fin_tip_trailing_edge_x_geo_m == pytest.approx(1.04, rel=2e-15)
    assert result.overall_length_m == result.fin_tip_trailing_edge_x_geo_m


def test_triangular_limit(geometry):
    """GEO-T11/17: Sıfır tip chord geçerli triangular limiting case'dir."""
    geometry = replace(geometry, fins=replace(geometry.fins, tip_chord_m=0.))
    result = RESOLVER.resolve(rocket_geometry=geometry)
    assert result.fin_tip_leading_edge_x_geo_m == result.fin_tip_trailing_edge_x_geo_m


@pytest.mark.parametrize('offset', [.05, 0., -.05])
def test_signed_offset(geometry, offset):
    """GEO-T12: Signed offset mutlak değere veya unsigned sweep'e dönüştürülmez."""
    geometry = replace(geometry, fins=replace(geometry.fins, tip_leading_edge_offset_x_m=offset))
    result = RESOLVER.resolve(rocket_geometry=geometry)
    assert result.fin_tip_leading_edge_x_geo_m == .72 + offset


@pytest.mark.parametrize('root_le', [
    pytest.param(.29, id='GEO-T13'), pytest.param(.83, id='GEO-T14'),
])
def test_root_outside_body(geometry, root_le):
    """GEO-T13/14: Root'un her iki sınırı gövde üzerinde kalmalı; clamp yok."""
    geometry = replace(geometry, fins=replace(geometry.fins, root_leading_edge_x_geo_m=root_le))
    with pytest.raises(GeometryValidationError) as caught:
        RESOLVER.resolve(rocket_geometry=geometry)
    assert caught.value.error_code == 'FIN_ROOT_OUTSIDE_BODY'


@pytest.mark.parametrize('offset', [-.73, -1.])
def test_tip_before_nose(geometry, offset):
    """GEO-T15: Tip LE veya her iki tip ucu origin önündeyse geometry reddedilir."""
    geometry = replace(geometry, fins=replace(geometry.fins, tip_leading_edge_offset_x_m=offset))
    with pytest.raises(GeometryValidationError) as caught:
        RESOLVER.resolve(rocket_geometry=geometry)
    assert caught.value.error_code == 'FIN_EXTENDS_BEFORE_NOSE'


def change_dimension(geometry, name, value):
    """Test-side immutable tasarım varyantı üretir; production helper değildir."""
    if '.' not in name:
        return replace(geometry, **{name: value})
    component, field = name.split('.')
    return replace(geometry, **{component: replace(getattr(geometry, component), **{field: value})})


POSITIVE_FIELDS = ['airframe_diameter_m', 'nose.length_m', 'body.length_m',
                   'fins.root_chord_m', 'fins.semi_span_m', 'fins.thickness_m']


@pytest.mark.parametrize('name', POSITIVE_FIELDS)
@pytest.mark.parametrize('value', [0., -1.])
def test_positive_dimensions(geometry, name, value):
    """GEO-T16/20: Finite invalid dimension structured hata üretir."""
    with pytest.raises(GeometryValidationError) as caught:
        RESOLVER.resolve(rocket_geometry=change_dimension(geometry, name, value))
    assert caught.value.error_code == 'NON_POSITIVE_DIMENSION'
    assert caught.value.field_name == name
    assert caught.value.value == value


def test_negative_tip_chord(geometry):
    """GEO-T17: Negatif tip chord reddedilir."""
    with pytest.raises(GeometryValidationError) as caught:
        RESOLVER.resolve(rocket_geometry=change_dimension(geometry, 'fins.tip_chord_m', -.1))
    assert caught.value.error_code == 'NEGATIVE_TIP_CHORD'
    assert caught.value.field_name == 'fins.tip_chord_m'
    assert caught.value.value == -.1


@pytest.mark.parametrize('count', [0, 1, 2, -1, 2.5, 3.5, True, 4.0])
def test_invalid_fin_count(geometry, count):
    """GEO-T18: Adet int>=3 olmalı; fractional/bool/coercion yok."""
    with pytest.raises(GeometryValidationError) as caught:
        RESOLVER.resolve(rocket_geometry=change_dimension(geometry, 'fins.fin_count', count))
    assert caught.value.error_code == 'INVALID_FIN_COUNT'
    assert caught.value.field_name == 'fins.fin_count'
    assert caught.value.value == count


@pytest.mark.parametrize('count', [3, 4, 8])
def test_valid_fin_count(geometry, count):
    """GEO-T18: Üç ve daha çok fin desteklenir."""
    result = RESOLVER.resolve(rocket_geometry=change_dimension(geometry, 'fins.fin_count', count))
    assert result.source.fins.fin_count == count


@pytest.mark.parametrize('name', POSITIVE_FIELDS + [
    'fins.tip_chord_m', 'fins.tip_leading_edge_offset_x_m',
    'fins.root_leading_edge_x_geo_m', 'fins.fin_count',
])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite(geometry, name, value):
    """GEO-T19: Non-finite input GeometryValidationError değil generic ValueError."""
    with pytest.raises(ValueError, match=name) as caught:
        RESOLVER.resolve(rocket_geometry=change_dimension(geometry, name, value))
    assert type(caught.value) is ValueError


@pytest.mark.parametrize('component', ['nose', 'body', 'fins', None])
def test_raw_immutable(geometry, component):
    """GEO-T21: Tüm raw tasarım nesneleri frozen ve slotted'dır."""
    design = getattr(geometry, component) if component else geometry
    assert not hasattr(design, '__dict__')
    with pytest.raises(FrozenInstanceError):
        setattr(design, fields(design)[0].name, 999)


def test_resolved_immutable(geometry):
    """GEO-T22: Derived resolved snapshot değiştirilemez."""
    result = RESOLVER.resolve(rocket_geometry=geometry)
    assert not hasattr(result, '__dict__')
    with pytest.raises(FrozenInstanceError):
        result.overall_length_m = 999.


def test_source_retained(geometry):
    """GEO-T23: Original immutable source primitive boyutlarıyla erişilebilir."""
    assert RESOLVER.resolve(rocket_geometry=geometry).source is geometry


def test_determinism(geometry):
    """GEO-T24: Aynı source deterministik çözülür; source değiştirilmez."""
    expected = RESOLVER.resolve(rocket_geometry=geometry)
    for _ in range(5):
        assert RESOLVER.resolve(rocket_geometry=geometry) == expected
        assert GeometryResolver().resolve(rocket_geometry=geometry) == expected
    assert geometry.fins.root_leading_edge_x_geo_m == .72


def test_public_contract():
    """GEO-T25: Minimal geometry yüzeyi; mass/aero/full component sistemi yok."""
    assert not inspect.signature(GeometryResolver).parameters
    parameters = inspect.signature(RESOLVER.resolve).parameters
    assert tuple(parameters) == ('rocket_geometry',)
    assert parameters['rocket_geometry'].kind is inspect.Parameter.KEYWORD_ONLY
    assert parameters['rocket_geometry'].default is inspect.Parameter.empty
    assert set(models.__all__) == {'ConicalNoseGeometry', 'CylindricalBodyGeometry',
                                  'TrapezoidalFinSetGeometry', 'SingleStageRocketGeometry'}
    assert set(resolver.__all__) == {'GeometryResolver', 'ResolvedRocketGeometry', 'GeometryValidationError'}
    assert [f.name for f in fields(ResolvedRocketGeometry)] == [
        'source', 'nose_start_x_geo_m', 'nose_end_x_geo_m',
        'body_start_x_geo_m', 'body_end_x_geo_m',
        'fin_root_leading_edge_x_geo_m', 'fin_root_trailing_edge_x_geo_m',
        'fin_tip_leading_edge_x_geo_m', 'fin_tip_trailing_edge_x_geo_m',
        'overall_length_m', 'reference_diameter_m', 'reference_length_m', 'reference_area_m2',
    ]
    assert [f.name for f in fields(SingleStageRocketGeometry)] == ['airframe_diameter_m', 'nose', 'body', 'fins']
    assert [f.name for f in fields(ConicalNoseGeometry)] == ['length_m']
    assert [f.name for f in fields(CylindricalBodyGeometry)] == ['length_m']


def test_frozen_geometry_convention():
    """NAT-006 ile x_geo nose-tip origin ve nose→tail convention parity."""
    assert dict(GEOMETRY_AXIAL_CONVENTION) == {
        'origin': 'nose tip', 'coordinate': 'x_geo', 'positive_direction': 'nose -> tail'
    }


def test_inclusive_root_and_tip_boundaries(geometry):
    """Root her iki body sınırında ve tip LE origin'de eşitlik geçerlidir."""
    geometry = replace(geometry, fins=replace(geometry.fins,
        root_leading_edge_x_geo_m=.3, root_chord_m=.7, tip_leading_edge_offset_x_m=-.3))
    result = RESOLVER.resolve(rocket_geometry=geometry)
    assert result.fin_root_leading_edge_x_geo_m == result.body_start_x_geo_m
    assert result.fin_root_trailing_edge_x_geo_m == result.body_end_x_geo_m
    assert result.fin_tip_leading_edge_x_geo_m == 0.


def test_single_diameter_authority(geometry):
    """Çap değişimi derived D/L/A'yı günceller; komponent çap kopyası yok."""
    result = RESOLVER.resolve(rocket_geometry=replace(geometry, airframe_diameter_m=.2))
    assert result.reference_diameter_m == result.reference_length_m == .2
    assert result.reference_area_m2 == pytest.approx(pi * .2**2 / 4., rel=2e-15)


@pytest.mark.parametrize('diameter', [1e308, 1e-300])
def test_reference_area_numerical_failure(geometry, diameter):
    """Reference area overflow/underflow fiziksel fallback ile düzeltilmez."""
    with pytest.raises(ValueError) as caught:
        RESOLVER.resolve(rocket_geometry=replace(geometry, airframe_diameter_m=diameter))
    assert type(caught.value) is ValueError
