"""MOUNT-T01..T34: rocket-side mount geometry; ölçüler yalnız test fixture'ıdır."""

from dataclasses import FrozenInstanceError, fields, replace
from math import pi

import pytest

from roketsim_native.geometry.models import (
    ConicalNoseGeometry, CylindricalBodyGeometry, NoseConstructionMode,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver, GeometryValidationError


@pytest.fixture
def geometry():
    return SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002),
        TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.120, .029, .001, 0.),
                                CenteringRingPairGeometry(.003), .005))


def resolve(geometry):
    return GeometryResolver().resolve(rocket_geometry=geometry)


def change(geometry, field, value):
    """Test-side immutable attachment varyantı; production default değildir."""
    attachment = geometry.motor_attachment
    if field == 'motor_overhang_m':
        attachment = replace(attachment, motor_overhang_m=value)
    elif field == 'axial_thickness_m':
        attachment = replace(attachment, centering_rings=replace(attachment.centering_rings, axial_thickness_m=value))
    else:
        attachment = replace(attachment, mount_tube=replace(attachment.mount_tube, **{field: value}))
    return replace(geometry, motor_attachment=attachment)


@pytest.mark.parametrize('field,expected', [
    pytest.param('body_inner_diameter_m', .096, id='MOUNT-T01'),
    pytest.param('motor_mount_outer_diameter_m', .031, id='MOUNT-T02'),
    pytest.param('motor_mount_end_x_geo_m', 1., id='MOUNT-T03'),
    pytest.param('motor_mount_start_x_geo_m', .88, id='MOUNT-T05'),
    pytest.param('motor_aft_reference_x_geo_m', 1.005, id='MOUNT-T12'),
    pytest.param('front_centering_ring_start_x_geo_m', .88, id='MOUNT-T16-start'),
    pytest.param('front_centering_ring_end_x_geo_m', .883, id='MOUNT-T16-end'),
    pytest.param('rear_centering_ring_start_x_geo_m', .997, id='MOUNT-T17-start'),
    pytest.param('rear_centering_ring_end_x_geo_m', 1., id='MOUNT-T17-end'),
    pytest.param('motor_mount_material_volume_m3', 1.1309733552923245e-5, id='MOUNT-T20'),
    pytest.param('motor_mount_volume_centroid_x_geo_m', .94, id='MOUNT-T21'),
    pytest.param('centering_ring_pair_material_volume_m3', 3.890077103307561e-5, id='MOUNT-T23'),
    pytest.param('centering_ring_pair_volume_centroid_x_geo_m', .94, id='MOUNT-T24'),
])
def test_reference_values(geometry, field, expected):
    """Analitik fixture ölçüleri strict floating-point tolerance ile doğrulanır."""
    assert getattr(resolve(geometry), field) == pytest.approx(expected, rel=4e-15, abs=0)


def test_recess(geometry):
    """MOUNT-T04: Pozitif recess tüpü body tail'den ileri kaydırır."""
    result = resolve(change(geometry, 'aft_recess_m', .05))
    assert result.motor_mount_end_x_geo_m == .95
    assert result.motor_mount_start_x_geo_m == .95 - .12


def test_mount_at_body_start(geometry):
    """MOUNT-T06: Tam body uzunluğu mount için sınır eşitliği geçerlidir."""
    geometry = replace(geometry, nose=replace(geometry.nose, length_m=.25),
                       body=replace(geometry.body, length_m=.75))
    result = resolve(change(geometry, 'length_m', .75))
    assert result.motor_mount_start_x_geo_m == result.body_start_x_geo_m == .25


@pytest.mark.parametrize('field,value,code', [
    pytest.param('length_m', .71, 'MOTOR_MOUNT_OUTSIDE_BODY', id='MOUNT-T07'),
    pytest.param('aft_recess_m', 1., 'MOTOR_MOUNT_OUTSIDE_BODY', id='MOUNT-T08'),
    pytest.param('inner_diameter_m', .094, 'MOTOR_MOUNT_TOO_LARGE_FOR_BODY', id='MOUNT-T10'),
    pytest.param('inner_diameter_m', .095, 'MOTOR_MOUNT_TOO_LARGE_FOR_BODY', id='MOUNT-T11'),
    pytest.param('motor_overhang_m', -.121, 'MOTOR_AFT_REFERENCE_BEFORE_MOUNT', id='MOUNT-T15'),
    pytest.param('axial_thickness_m', .061, 'CENTERING_RINGS_OVERLAP', id='MOUNT-T19'),
    pytest.param('aft_recess_m', -.001, 'NEGATIVE_MOUNT_AFT_RECESS', id='MOUNT-T26'),
])
def test_relationship_errors(geometry, field, value, code):
    """Finite invalid ilişkiler structured geometry hatasıdır; relocation yok."""
    with pytest.raises(GeometryValidationError) as caught:
        resolve(change(geometry, field, value))
    assert caught.value.error_code == code


def test_radial_clearance(geometry):
    """MOUNT-T09: Pozitif radial ring alanı için strict clearance korunur."""
    result = resolve(geometry)
    assert result.motor_mount_outer_diameter_m < result.body_inner_diameter_m


@pytest.mark.parametrize('overhang', [pytest.param(0., id='MOUNT-T13'),
    pytest.param(-.01, id='MOUNT-T14'), pytest.param(-.12, id='MOUNT-T14-boundary')])
def test_signed_overhang(geometry, overhang):
    """Flush/negative referanslar kabul edilir; installed motor envelope bilinmez."""
    result = resolve(change(geometry, 'motor_overhang_m', overhang))
    assert result.motor_aft_reference_x_geo_m == result.motor_mount_end_x_geo_m + overhang


def test_rings_touch(geometry):
    """MOUNT-T18: 2*t=L eşitliği overlap değildir."""
    result = resolve(change(geometry, 'axial_thickness_m', .06))
    assert result.front_centering_ring_end_x_geo_m == result.rear_centering_ring_start_x_geo_m


def test_ring_volume_and_centroids(geometry):
    """MOUNT-T22/T23/T24: Tek ring annulus, pair 2*V ve iki merkezin ortalaması."""
    result = resolve(geometry)
    one_ring = pi * ((.096/2)**2 - (.031/2)**2) * .003
    assert result.centering_ring_pair_material_volume_m3 == 2 * one_ring
    front = result.front_centering_ring_start_x_geo_m + .003 / 2
    rear = result.rear_centering_ring_end_x_geo_m - .003 / 2
    assert (front, rear) == pytest.approx((.8815, .9985), rel=3e-15)
    assert result.centering_ring_pair_volume_centroid_x_geo_m == (front + rear) / 2


@pytest.mark.parametrize('field,code', [
    ('length_m', 'NON_POSITIVE_MOUNT_LENGTH'),
    ('inner_diameter_m', 'NON_POSITIVE_MOUNT_INNER_DIAMETER'),
    ('wall_thickness_m', 'NON_POSITIVE_MOUNT_WALL_THICKNESS'),
    ('axial_thickness_m', 'NON_POSITIVE_CENTERING_RING_THICKNESS'),
])
@pytest.mark.parametrize('value', [0., -1.])
def test_positive_dimensions(geometry, field, code, value):
    """MOUNT-T25/T28: Finite invalid dimension alanı ve değeri hatada korunur."""
    with pytest.raises(GeometryValidationError) as caught:
        resolve(change(geometry, field, value))
    assert caught.value.error_code == code
    assert caught.value.field_name.endswith(field)
    assert caught.value.value == value


@pytest.mark.parametrize('field', ['length_m', 'inner_diameter_m', 'wall_thickness_m',
    'aft_recess_m', 'axial_thickness_m', 'motor_overhang_m'])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite(geometry, field, value):
    """MOUNT-T27: Non-finite input generic ValueError olarak reddedilir."""
    with pytest.raises(ValueError) as caught:
        resolve(change(geometry, field, value))
    assert type(caught.value) is ValueError


def test_frozen(geometry):
    """MOUNT-T29/T30: Üç yeni raw nesne ve resolved frozen/slotted kalır."""
    attachment = geometry.motor_attachment
    for obj in (attachment, attachment.mount_tube, attachment.centering_rings, resolve(geometry)):
        assert not hasattr(obj, '__dict__')
        with pytest.raises(FrozenInstanceError):
            setattr(obj, fields(obj)[0].name, None)


def test_existing_geometry_unchanged(geometry):
    """MOUNT-T31: Mount/referans değişimi nose/body/fin/reference/overall'ı etkilemez."""
    before = resolve(geometry)
    after = resolve(change(change(geometry, 'motor_overhang_m', 100.), 'aft_recess_m', .01))
    for field in fields(before):
        if field.name == 'source' or any(token in field.name for token in ('motor_', 'centering_')):
            continue
        assert getattr(after, field.name) == getattr(before, field.name)
    assert after.overall_length_m == 1.
    assert after.motor_aft_reference_x_geo_m > after.overall_length_m


def test_determinism(geometry):
    """MOUNT-T32: Immutable input korunur ve yeniden çözüm exact deterministiktir."""
    before = repr(geometry)
    expected = resolve(geometry)
    for _ in range(5):
        assert resolve(geometry) == expected
        assert resolve(geometry).source is geometry
    assert repr(geometry) == before


def test_full_fixture(geometry):
    """MOUNT-T33: Tek resolver external geometry ve mount hacimlerini birlikte verir."""
    result = resolve(geometry)
    assert (result.body_inner_diameter_m, result.motor_mount_inner_diameter_m,
        result.motor_mount_outer_diameter_m, result.motor_mount_start_x_geo_m,
        result.motor_mount_end_x_geo_m, result.motor_aft_reference_x_geo_m,
        result.motor_mount_material_volume_m3, result.motor_mount_volume_centroid_x_geo_m,
        result.centering_ring_pair_material_volume_m3, result.centering_ring_pair_volume_centroid_x_geo_m
    ) == pytest.approx((.096, .029, .031, .88, 1., 1.005, 1.1309733552923245e-5,
        .94, 3.890077103307561e-5, .94), rel=4e-15)


def test_scope(geometry):
    """MOUNT-T34: Motor seçimi/fit/material/mass API yok; raw input'lar explicit."""
    assert [f.name for f in fields(MotorMountTubeGeometry)] == [
        'length_m', 'inner_diameter_m', 'wall_thickness_m', 'aft_recess_m']
    assert [f.name for f in fields(CenteringRingPairGeometry)] == ['axial_thickness_m']
    assert [f.name for f in fields(MotorAttachmentGeometry)] == ['mount_tube', 'centering_rings', 'motor_overhang_m']
    with pytest.raises(TypeError):
        MotorAttachmentGeometry()
    assert not {'motor_mass_kg', 'mount_mass_kg', 'thrust'}.intersection(f.name for f in fields(resolve(geometry)))
