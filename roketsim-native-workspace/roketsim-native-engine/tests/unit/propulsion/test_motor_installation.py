"""MINST-T01..40: nominal kurulum ilişkisi; fixture ölçüleri üretim varsayılanı değildir."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature
from math import isfinite

import pytest

from roketsim_native.geometry.models import (
    ReferenceGeometryPolicy, FinCrossSection, FinAngularArrangement,
    ConicalNoseGeometry, CylindricalBodyGeometry, NoseConstructionMode,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.propulsion import installation
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T as MOTOR
from roketsim_native.propulsion.installation import (
    MotorInstallation, MotorInstallationResolver, MotorInstallationError,
)


@pytest.fixture
def raw_geometry():
    return SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002),
        TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003, FinCrossSection.SQUARE,
                                 FinAngularArrangement.EQUALLY_SPACED),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.120, .029, .001, 0.),
                                CenteringRingPairGeometry(.003), .005), ReferenceGeometryPolicy.MAXIMUM_DIAMETER)


@pytest.fixture
def geometry(raw_geometry):
    return GeometryResolver().resolve(rocket_geometry=raw_geometry)


def resolve(geometry, motor=MOTOR):
    """Yalnız test fixture'ı için motor seçimi; production default yoktur."""
    return MotorInstallationResolver().resolve(resolved_geometry=geometry, motor=motor)


def test_result_frozen(geometry):
    """MINST-T01: Kurulum sonucu frozen/slotted'dır."""
    result = resolve(geometry)
    assert not hasattr(result, '__dict__')
    with pytest.raises(FrozenInstanceError):
        result.motor_front_x_geo_m = 0.


def test_resolver_signature(geometry):
    """MINST-T02/T03: Parametresiz constructor, iki zorunlu keyword-only girdi."""
    assert not signature(MotorInstallationResolver).parameters
    resolver = MotorInstallationResolver()
    parameters = signature(resolver.resolve).parameters
    assert list(parameters) == ['resolved_geometry', 'motor']
    assert all(p.kind is Parameter.KEYWORD_ONLY and p.default is Parameter.empty
               for p in parameters.values())
    with pytest.raises(TypeError):
        resolver.resolve(geometry, MOTOR)


def test_axial_equations(geometry):
    """MINST-T04/T05/T13: Hazır aft referansı ve motor uzunluğu kullanılır."""
    result = resolve(geometry)
    assert result.motor_aft_x_geo_m == geometry.motor_aft_reference_x_geo_m
    assert result.motor_front_x_geo_m == result.motor_aft_x_geo_m - MOTOR.length_m
    assert result.motor_front_x_geo_m > geometry.motor_mount_start_x_geo_m


@pytest.mark.parametrize('diameter', [.025, .029])
def test_radial_fit(geometry, diameter):
    """MINST-T06/T07/T09/T10/T11: Radial açıklık yarı çap farkıdır; eşitlik geçerli."""
    result = resolve(geometry, replace(MOTOR, diameter_m=diameter))
    assert result.nominal_radial_clearance_m == (.029 - diameter) / 2
    assert result.nominal_radial_clearance_m == pytest.approx(
        .002 if diameter == .025 else 0., rel=2e-15, abs=0)


def test_oversize_motor(geometry):
    """MINST-T08/T32: Geçerli motor tanımı, geçerli montajla uyumsuz olabilir."""
    motor = replace(MOTOR, diameter_m=.04)
    with pytest.raises(MotorInstallationError) as caught:
        resolve(geometry, motor)
    assert caught.value.error_code == 'MOTOR_DIAMETER_EXCEEDS_MOUNT'
    assert caught.value.field_name == 'diameter_m'
    assert caught.value.value == .04


def test_exact_front_boundary(geometry):
    """MINST-T12: Tam temsil edilebilir sınırlarda ön yüz eşitliği geçerli."""
    geometry = replace(geometry, motor_mount_start_x_geo_m=.75,
        motor_mount_end_x_geo_m=1., motor_aft_reference_x_geo_m=1.)
    result = resolve(geometry, replace(MOTOR, length_m=.25))
    assert result.motor_front_x_geo_m == geometry.motor_mount_start_x_geo_m
    assert result.axial_engagement_length_m == .25


def test_front_before_mount(geometry):
    """MINST-T14: Uzun motor otomatik taşınmaz veya kısaltılmaz."""
    with pytest.raises(MotorInstallationError) as caught:
        resolve(geometry, replace(MOTOR, length_m=.2))
    assert caught.value.error_code == 'MOTOR_FRONT_BEFORE_MOUNT'


@pytest.mark.parametrize('overhang', [.005, 0., -.01])
def test_signed_overhang_and_intersection(raw_geometry, overhang):
    """MINST-T15..21: Gerçek Geometry üzerinden üç işaret ve interval kesişimi."""
    raw_geometry = replace(raw_geometry, motor_attachment=replace(
        raw_geometry.motor_attachment, motor_overhang_m=overhang))
    geometry = GeometryResolver().resolve(rocket_geometry=raw_geometry)
    result = resolve(geometry)
    expected_start = max(result.motor_front_x_geo_m, geometry.motor_mount_start_x_geo_m)
    expected_end = min(result.motor_aft_x_geo_m, geometry.motor_mount_end_x_geo_m)
    assert result.axial_engagement_length_m == expected_end - expected_start
    assert result.axial_engagement_length_m > 0
    assert result.axial_engagement_length_m == pytest.approx(
        .093 if overhang > 0 else .098, rel=3e-15, abs=0)


@pytest.mark.parametrize('aft,expected', [(1.125, 0.), (1.25, -.125)])
def test_no_engagement(geometry, aft, expected):
    """MINST-T22/T23/T24: Ön containment tek başına yeterli değildir; clamp yok."""
    geometry = replace(geometry, motor_aft_reference_x_geo_m=aft)
    motor = replace(MOTOR, length_m=.125)
    assert aft - motor.length_m >= geometry.motor_mount_start_x_geo_m
    with pytest.raises(MotorInstallationError) as caught:
        resolve(geometry, motor)
    assert caught.value.error_code == 'NO_POSITIVE_MOTOR_ENGAGEMENT'
    assert caught.value.field_name == 'axial_engagement_length_m'
    assert caught.value.value == expected


@pytest.mark.parametrize('name,expected', [
    pytest.param('motor_front_x_geo_m', .907, id='MINST-T25'),
    pytest.param('motor_aft_x_geo_m', 1.005, id='MINST-T26'),
    pytest.param('nominal_radial_clearance_m', 0., id='MINST-T27'),
    pytest.param('axial_engagement_length_m', .093, id='MINST-T28'),
])
def test_f50_integration(geometry, name, expected):
    assert getattr(resolve(geometry), name) == pytest.approx(expected, rel=3e-15, abs=0)


def test_provenance_and_determinism(geometry):
    """MINST-T29/T30/T31: Exact seçim korunur; girdiler ve fiziksel state değişmez."""
    before = (repr(geometry), repr(MOTOR))
    resolver = MotorInstallationResolver()
    first = resolver.resolve(resolved_geometry=geometry, motor=MOTOR)
    for _ in range(5):
        result = resolver.resolve(resolved_geometry=geometry, motor=MOTOR)
        assert result == first
        assert result.motor is MOTOR
    assert (repr(geometry), repr(MOTOR)) == before
    assert not hasattr(resolver, '__dict__')


@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite_aft(geometry, value):
    """MINST-T33: Bozuk upstream aft sonucu installation hatasıdır."""
    with pytest.raises(MotorInstallationError) as caught:
        resolve(replace(geometry, motor_aft_reference_x_geo_m=value))
    assert caught.value.error_code == 'INVALID_MOTOR_AFT_POSITION'


def test_front_overflow(geometry):
    """MINST-T33: Sonlu operandlardan taşan ön yüz hesabı açıkça reddedilir."""
    with pytest.raises(MotorInstallationError) as caught:
        resolve(replace(geometry, motor_aft_reference_x_geo_m=-1e308),
                replace(MOTOR, length_m=1e308))
    assert caught.value.error_code == 'INVALID_MOTOR_FRONT_POSITION'
    assert not isfinite(caught.value.value)


@pytest.mark.parametrize('name,code', [
    ('motor_mount_inner_diameter_m', 'INVALID_RADIAL_CLEARANCE'),
    ('motor_mount_start_x_geo_m', 'INVALID_AXIAL_ENGAGEMENT'),
    ('motor_mount_end_x_geo_m', 'INVALID_AXIAL_ENGAGEMENT'),
])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite_relationship(geometry, name, code, value):
    """MINST-T34: min/max bozuk upstream sınırları gizleyemez."""
    with pytest.raises(MotorInstallationError) as caught:
        resolve(replace(geometry, **{name: value}))
    assert caught.value.error_code == code


def test_engagement_overflow(geometry):
    """MINST-T34: Sonlu fakat tutarsız interval farkındaki taşma onarılmaz."""
    geometry = replace(geometry, motor_mount_start_x_geo_m=-1e308,
        motor_mount_end_x_geo_m=-1e308, motor_aft_reference_x_geo_m=1e308)
    with pytest.raises(MotorInstallationError) as caught:
        resolve(geometry)
    assert caught.value.error_code == 'INVALID_AXIAL_ENGAGEMENT'


def test_authority_boundaries(geometry):
    """MINST-T35/T36: Raw geometry/katalog yerine verilen resolved değerleri tüket."""
    geometry = replace(geometry, motor_mount_inner_diameter_m=.05,
        motor_mount_start_x_geo_m=2., motor_mount_end_x_geo_m=3.,
        motor_aft_reference_x_geo_m=3.125)
    motor = replace(MOTOR, motor_id='test_selection', diameter_m=.04, length_m=.5)
    result = resolve(geometry, motor)
    assert result.motor is motor
    assert result.motor_front_x_geo_m == 2.625
    assert result.motor_aft_x_geo_m == 3.125
    assert result.nominal_radial_clearance_m == (.05 - .04) / 2
    assert result.axial_engagement_length_m == .375


def test_scope_contract():
    """MINST-T37..40: Yalnız nominal tek/eş eksenli ilişki; motor fiziği yok."""
    assert set(installation.__all__) == {
        'MotorInstallation', 'MotorInstallationResolver', 'MotorInstallationError'}
    assert [f.name for f in fields(MotorInstallation)] == [
        'motor', 'motor_front_x_geo_m', 'motor_aft_x_geo_m',
        'nominal_radial_clearance_m', 'axial_engagement_length_m']
    assert [name for name in dir(MotorInstallationResolver) if not name.startswith('_')] == ['resolve']
