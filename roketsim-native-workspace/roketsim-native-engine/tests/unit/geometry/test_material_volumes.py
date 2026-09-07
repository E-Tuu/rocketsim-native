"""NAT-011A.1 CGEO-T01..T28: tek geometry source ve material-volume kanıtları."""

from dataclasses import FrozenInstanceError, fields, replace
from math import hypot, isfinite, nextafter, pi

import pytest

from roketsim_native.geometry.models import (
    ReferenceGeometryPolicy, FinCrossSection,
    ConicalNoseGeometry, CylindricalBodyGeometry, NoseConstructionMode,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver, GeometryValidationError


@pytest.fixture
def geometry():
    return SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002),
        TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003, FinCrossSection.SQUARE),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.120, .029, .001, 0.),
                                CenteringRingPairGeometry(.003), .005), ReferenceGeometryPolicy.MAXIMUM_DIAMETER)


def resolve(geometry):
    """Test-side kısa çağrı; production resolver tek kalır."""
    return GeometryResolver().resolve(rocket_geometry=geometry)


def test_solid(geometry):
    """CGEO-T01/T02: Solid cone V ve nose-tip kaynaklı volume centroid."""
    result = resolve(replace(geometry, nose=ConicalNoseGeometry(.3, NoseConstructionMode.SOLID, None)))
    assert result.nose_material_volume_m3 == pytest.approx(7.853981633974483e-4, rel=3e-15)
    assert result.nose_volume_centroid_x_geo_m == pytest.approx(.225, rel=3e-15)


@pytest.mark.parametrize('mode,thickness,code', [
    pytest.param(NoseConstructionMode.SOLID, .002, 'UNEXPECTED_WALL_THICKNESS', id='CGEO-T03'),
    pytest.param(NoseConstructionMode.HOLLOW_SHELL, None, 'MISSING_WALL_THICKNESS', id='CGEO-T04'),
    pytest.param(NoseConstructionMode.HOLLOW_SHELL, 0., 'NON_POSITIVE_WALL_THICKNESS', id='CGEO-T08-zero'),
    pytest.param(NoseConstructionMode.HOLLOW_SHELL, -.002, 'NON_POSITIVE_WALL_THICKNESS', id='CGEO-T08-negative'),
])
def test_nose_errors(geometry, mode, thickness, code):
    """Construction hataları structured; thickness yok sayılmaz."""
    with pytest.raises(GeometryValidationError) as caught:
        resolve(replace(geometry, nose=ConicalNoseGeometry(.3, mode, thickness)))
    assert caught.value.error_code == code
    assert caught.value.field_name == 'nose.wall_thickness_m'
    assert caught.value.value == thickness


def test_inner_geometry_and_shell(geometry):
    """CGEO-T05/T06: Normal-offset iç koni oracle'ı public shell hacmini belirler."""
    slant = hypot(.3, .05)
    delta_x, delta_r = .002 * slant / .05, .002 * slant / .3
    inner_length, inner_radius = .3 - delta_x, .05 - delta_r
    assert (slant, delta_x, delta_r, inner_length, inner_radius) == pytest.approx(
        (.30413812651491096, .01216552506059644, .0020275875100994067,
         .28783447493940356, .04797241248990060), rel=3e-15)
    expected = pi * .05**2 * .3 / 3 - pi * inner_radius**2 * inner_length / 3
    result = resolve(geometry)
    assert result.nose_material_volume_m3 == pytest.approx(expected, rel=2e-14)
    assert result.nose_material_volume_m3 == pytest.approx(9.172555380948023e-5, rel=2e-14)


def test_shell_centroid(geometry):
    """CGEO-T07: İç void offset'i centroid hesabına katılır."""
    assert resolve(geometry).nose_volume_centroid_x_geo_m == pytest.approx(.2019996168861517, rel=2e-14)


@pytest.mark.parametrize('multiplier', [1., 1.01])
def test_shell_limit(geometry, multiplier):
    """CGEO-T09: Geometric thickness limit eşitliği de geçersizdir."""
    thickness = .3 * .05 / hypot(.3, .05) * multiplier
    with pytest.raises(GeometryValidationError) as caught:
        resolve(replace(geometry, nose=replace(geometry.nose, wall_thickness_m=thickness)))
    assert caught.value.error_code == 'NOSE_SHELL_TOO_THICK'


def test_just_below_shell_limit(geometry):
    """CGEO-T10: Bir representable adım aşağıda arbitrary margin uygulanmaz."""
    thickness = nextafter(.3 * .05 / hypot(.3, .05), 0.)
    result = resolve(replace(geometry, nose=replace(geometry.nose, wall_thickness_m=thickness)))
    assert result.nose_material_volume_m3 > 0


def test_body(geometry):
    """CGEO-T11/T12: Body envelope değil annulus; centroid absolute x_geo."""
    result = resolve(geometry)
    assert result.body_material_volume_m3 == pytest.approx(4.310265120725205e-4, rel=2e-14)
    assert result.body_volume_centroid_x_geo_m == pytest.approx(.65, rel=3e-15)
    assert result.body_material_volume_m3 < pi * .05**2 * .7


@pytest.mark.parametrize('thickness,code', [
    pytest.param(0., 'NON_POSITIVE_WALL_THICKNESS', id='CGEO-T13-zero'),
    pytest.param(-.002, 'NON_POSITIVE_WALL_THICKNESS', id='CGEO-T13-negative'),
    pytest.param(.05, 'BODY_WALL_TOO_THICK', id='CGEO-T14-equal'),
    pytest.param(.06, 'BODY_WALL_TOO_THICK', id='CGEO-T14-above'),
])
def test_body_errors(geometry, thickness, code):
    """Body thickness strict domain; solid conversion/clamp yok."""
    with pytest.raises(GeometryValidationError) as caught:
        resolve(replace(geometry, body=replace(geometry.body, wall_thickness_m=thickness)))
    assert caught.value.error_code == code
    assert caught.value.field_name == 'body.wall_thickness_m'
    assert caught.value.value == thickness


def test_fins(geometry):
    """CGEO-T15/T16/T17: Solid plate planform area, fin-set volume ve centroid."""
    result = resolve(geometry)
    assert result.fin_set_material_volume_m3 / (4 * .003) == pytest.approx(.0156, rel=3e-15)
    assert result.fin_set_material_volume_m3 == pytest.approx(.0001872, rel=3e-15)
    assert result.fin_set_volume_centroid_x_geo_m - .72 == pytest.approx(.09, rel=3e-15)
    assert result.fin_set_volume_centroid_x_geo_m == pytest.approx(.81, rel=3e-15)


def test_triangular(geometry):
    """CGEO-T18: Triangular fin hacmi ve centroid'i sıfır tip chord ile tanımlı."""
    result = resolve(replace(geometry, fins=replace(geometry.fins, tip_chord_m=0.)))
    assert result.fin_set_material_volume_m3 == pytest.approx(4 * .18 * .12 / 2 * .003, rel=3e-15)
    assert result.fin_set_volume_centroid_x_geo_m == pytest.approx(.72 + (.18 + .05) / 3, rel=3e-15)


@pytest.mark.parametrize('offset', [.05, 0., -.05])
def test_signed_sweep(geometry, offset):
    """CGEO-T19: Signed d centroid'i değiştirir; hacim değişmez."""
    result = resolve(replace(geometry, fins=replace(geometry.fins, tip_leading_edge_offset_x_m=offset)))
    expected = .72 + (.18**2 + .18*.08 + .08**2 + offset*(.18+2*.08))/(3*(.18+.08))
    assert result.fin_set_volume_centroid_x_geo_m == pytest.approx(expected, rel=3e-15)
    assert result.fin_set_material_volume_m3 == pytest.approx(.0001872, rel=3e-15)


def test_external_geometry(geometry):
    """CGEO-T20/T21: Construction değişikliği reference/placement politikasını değiştirmez."""
    result = resolve(geometry)
    actual = [result.nose_start_x_geo_m, result.nose_end_x_geo_m,
        result.body_start_x_geo_m, result.body_end_x_geo_m,
        result.fin_root_leading_edge_x_geo_m, result.fin_root_trailing_edge_x_geo_m,
        result.fin_tip_leading_edge_x_geo_m, result.fin_tip_trailing_edge_x_geo_m,
        result.overall_length_m, result.reference_diameter_m, result.reference_length_m,
        result.reference_area_m2]
    assert actual == pytest.approx([0., .3, .3, 1., .72, .9, .77, .85, 1., .1, .1, pi*.1**2/4], rel=3e-15)


def test_immutable_contracts(geometry):
    """CGEO-T22/T23: Genişletilen source ve resolved contract frozen/slotted kalır."""
    for obj in (geometry, geometry.nose, geometry.body, geometry.fins, resolve(geometry)):
        assert not hasattr(obj, '__dict__')
        with pytest.raises(FrozenInstanceError):
            setattr(obj, fields(obj)[0].name, None)


@pytest.mark.parametrize('component', ['nose', 'body'])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite_thickness(geometry, component, value):
    """CGEO-T24: Non-finite construction input generic finite-validation hatasıdır."""
    changed = replace(geometry, **{component: replace(getattr(geometry, component), wall_thickness_m=value)})
    with pytest.raises(ValueError) as caught:
        resolve(changed)
    assert type(caught.value) is ValueError


def test_success_invariants(geometry):
    """CGEO-T25: Başarılı volume pozitif finite, centroid finite'dır."""
    result = resolve(geometry)
    for field in fields(result):
        if 'material_volume' in field.name:
            assert isfinite(getattr(result, field.name)) and getattr(result, field.name) > 0
        if 'volume_centroid' in field.name:
            assert isfinite(getattr(result, field.name))


def test_repeatability(geometry):
    """CGEO-T26: Tek immutable source korunur; çözüm deterministiktir."""
    expected = resolve(geometry)
    for _ in range(5):
        assert resolve(geometry) == expected
        assert resolve(geometry).source is geometry
    assert geometry.nose.wall_thickness_m == .002


def test_full_rocket(geometry):
    """CGEO-T27: Tek resolve, placement/reference ve üç volume/centroid sağlar."""
    result = resolve(geometry)
    assert (result.nose_material_volume_m3, result.nose_volume_centroid_x_geo_m,
        result.body_material_volume_m3, result.body_volume_centroid_x_geo_m,
        result.fin_set_material_volume_m3, result.fin_set_volume_centroid_x_geo_m) == pytest.approx(
        (9.172555380948023e-5, .2019996168861517, 4.310265120725205e-4, .65, .0001872, .81), rel=2e-14)
    assert result.overall_length_m == 1.
    assert result.reference_diameter_m == .1


def test_no_defaults_or_mass_scope(geometry):
    """CGEO-T28: Schema explicit; material catalog/mass veya ikinci source yok."""
    with pytest.raises(TypeError):
        ConicalNoseGeometry(.3)
    with pytest.raises(TypeError):
        CylindricalBodyGeometry(.7)
    assert set(NoseConstructionMode) == {NoseConstructionMode.SOLID, NoseConstructionMode.HOLLOW_SHELL}
    assert [f.name for f in fields(geometry)] == ['airframe_diameter_m', 'nose', 'body', 'fins', 'motor_attachment', 'reference_geometry_policy']
    forbidden = {'mass', 'cg', 'inertia', 'density_kg_m3', 'cp', 'cna', 'cd', 'drag'}
    assert not forbidden.intersection(f.name.lower() for f in fields(resolve(geometry)))


@pytest.mark.parametrize('mode', ['other', 'SOLID', None, 1])
def test_invalid_mode(geometry, mode):
    """Enum dışı modlar tahmin edilmez veya string'den sessizce çevrilmez."""
    with pytest.raises(GeometryValidationError) as caught:
        resolve(replace(geometry, nose=replace(geometry.nose, construction_mode=mode)))
    assert caught.value.error_code == 'INVALID_NOSE_CONSTRUCTION'


@pytest.mark.parametrize('component', ['nose', 'body'])
def test_unrepresentably_thin_volume(geometry, component):
    """Çıkarma cancellation sıfır hacim verirse epsilon repair yerine hata."""
    changed = replace(geometry, **{component: replace(getattr(geometry, component), wall_thickness_m=1e-300)})
    with pytest.raises(GeometryValidationError) as caught:
        resolve(changed)
    assert caught.value.error_code == 'INVALID_DERIVED_VOLUME'
