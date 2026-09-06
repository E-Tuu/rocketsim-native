"""SMEXT-T01..T18: mount/ring katkıları yalnız accepted Geometry'den tüketilir."""

from dataclasses import FrozenInstanceError, fields, replace
import inspect

import pytest

from roketsim_native.geometry.models import (
    ConicalNoseGeometry, CylindricalBodyGeometry, NoseConstructionMode,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.materials.models import BulkMaterial, SingleStageRocketMaterials
from roketsim_native.mass.models import ComponentMassProperties, StructuralMassProperties, MassValidationError
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator


@pytest.fixture
def geometry():
    return GeometryResolver().resolve(rocket_geometry=SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002), TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.12, .029, .001, 0.),
                                CenteringRingPairGeometry(.003), .005)))


@pytest.fixture
def materials():
    return SingleStageRocketMaterials(POLYSTYRENE, CARDBOARD, CARDBOARD, CARDBOARD, CARDBOARD)


def evaluate(geometry, materials):
    return StructuralMassPropertiesCalculator().evaluate(resolved_geometry=geometry, materials=materials)


@pytest.mark.parametrize('missing', [pytest.param('motor_mount', id='SMEXT-T01'),
                                     pytest.param('centering_rings', id='SMEXT-T02')])
def test_mandatory_assignment(materials, missing):
    """Yeni atamalar mandatory; eksik değer default ile doldurulmaz."""
    parameters = inspect.signature(SingleStageRocketMaterials).parameters
    assert parameters[missing].default is inspect.Parameter.empty
    values = {f.name: getattr(materials, f.name) for f in fields(materials) if f.name != missing}
    with pytest.raises(TypeError):
        SingleStageRocketMaterials(**values)


def test_assignment_independence(materials):
    """SMEXT-T03/T04: Ayrı material seçimi ve frozen/slotted assignment."""
    changed = replace(materials, centering_rings=POLYSTYRENE)
    assert changed.motor_mount is CARDBOARD
    assert changed.centering_rings is POLYSTYRENE
    assert not hasattr(changed, '__dict__')
    with pytest.raises(FrozenInstanceError):
        changed.motor_mount = POLYSTYRENE


@pytest.mark.parametrize('component,volume,centroid', [
    pytest.param('motor_mount', 'motor_mount_material_volume_m3', 'motor_mount_volume_centroid_x_geo_m', id='SMEXT-T05-T07'),
    pytest.param('centering_rings', 'centering_ring_pair_material_volume_m3', 'centering_ring_pair_volume_centroid_x_geo_m', id='SMEXT-T06-T08'),
])
def test_new_components(geometry, materials, component, volume, centroid):
    """SMEXT-T05..08/T14: Aynı generic result, rho*V, direct centroid ve provenance."""
    result = getattr(evaluate(geometry, materials), component)
    assert type(result) is ComponentMassProperties
    assert result.material is getattr(materials, component)
    assert result.mass_kg == result.material.density_kg_m3 * getattr(geometry, volume)
    assert result.cg_x_geo_m == getattr(geometry, centroid)


def test_old_components(geometry, materials):
    """SMEXT-T09: Eski component mass ve centroid'leri frozen fixture ile aynı."""
    result = evaluate(geometry, materials)
    assert (result.nose.mass_kg, result.body.mass_kg, result.fins.mass_kg) == pytest.approx(
        (.09631183149995425, .29309802820931397, .127296), rel=3e-14)
    assert (result.nose.cg_x_geo_m, result.body.cg_x_geo_m, result.fins.cg_x_geo_m) == pytest.approx(
        (.2019996168861517, .65, .81), rel=3e-14)


def test_five_component_aggregation(geometry, materials):
    """SMEXT-T10/T11: Beş direct mass toplamı ve beş weighted CG katkısı."""
    result = evaluate(geometry, materials)
    components = [getattr(result, name) for name in ('nose', 'body', 'fins', 'motor_mount', 'centering_rings')]
    total = sum(c.mass_kg for c in components)
    assert result.structure_mass_kg == total
    assert result.structure_cg_x_geo_m == sum(c.mass_kg*c.cg_x_geo_m for c in components)/total


@pytest.mark.parametrize('component', [pytest.param('motor_mount', id='SMEXT-T12'),
                                      pytest.param('centering_rings', id='SMEXT-T13')])
def test_independent_density_change(geometry, materials, component):
    """Tek density değişimi diğer component sonuçlarına dokunmaz."""
    original = evaluate(geometry, materials)
    selected = BulkMaterial('Double', 2 * getattr(materials, component).density_kg_m3)
    changed = evaluate(geometry, replace(materials, **{component: selected}))
    for name in ('nose', 'body', 'fins', 'motor_mount', 'centering_rings'):
        if name == component:
            assert getattr(changed, name).mass_kg == 2 * getattr(original, name).mass_kg
            assert getattr(changed, name).material is selected
        else:
            assert getattr(changed, name) == getattr(original, name)


@pytest.mark.parametrize('component,volume,centroid', [
    ('motor_mount', 'motor_mount_material_volume_m3', 'motor_mount_volume_centroid_x_geo_m'),
    ('centering_rings', 'centering_ring_pair_material_volume_m3', 'centering_ring_pair_volume_centroid_x_geo_m'),
])
def test_geometry_authority(geometry, materials, component, volume, centroid):
    """SMEXT-T15: Source raw geometry aynıyken resolved perturbation doğrudan tüketilir."""
    changed = replace(geometry, **{volume: .0001, centroid: .4})
    assert changed.source is geometry.source
    result = getattr(evaluate(changed, materials), component)
    assert result.mass_kg == 680 * .0001
    assert result.cg_x_geo_m == .4


def test_full_fixture(geometry, materials):
    """SMEXT-T16: Accepted A.2 fixture ve test-only Cardboard mount/ring seçimi."""
    result = evaluate(geometry, materials)
    assert (result.motor_mount.mass_kg, result.centering_rings.mass_kg,
        result.structure_mass_kg, result.structure_cg_x_geo_m) == pytest.approx(
        (.007690618815987807, .026452524302491415, .5508490028277474, .6266199705547180), rel=3e-14)


def test_immutable_determinism(geometry, materials):
    """SMEXT-T17: Extended result frozen; inputs ve repeated sonuçlar değişmez."""
    before = repr(geometry), repr(materials)
    result = evaluate(geometry, materials)
    for _ in range(5):
        assert evaluate(geometry, materials) == result
    assert (repr(geometry), repr(materials)) == before
    for obj in (result, result.motor_mount, result.centering_rings):
        assert not hasattr(obj, '__dict__')
        with pytest.raises(FrozenInstanceError):
            setattr(obj, fields(obj)[0].name, None)


def test_scope():
    """SMEXT-T18: Structural schema içinde yalnız beş yapı katkısı, gerçek motor yok."""
    assert [f.name for f in fields(StructuralMassProperties)] == [
        'nose', 'body', 'fins', 'motor_mount', 'centering_rings', 'structure_mass_kg', 'structure_cg_x_geo_m']
    assert [f.name for f in fields(ComponentMassProperties)] == ['material', 'mass_kg', 'cg_x_geo_m']
    assert tuple(inspect.signature(StructuralMassPropertiesCalculator().evaluate).parameters) == ('resolved_geometry', 'materials')


@pytest.mark.parametrize('component,volume', [('motor_mount', 'motor_mount_material_volume_m3'),
    ('centering_rings', 'centering_ring_pair_material_volume_m3')])
@pytest.mark.parametrize('value', [0., -1., float('nan'), float('inf'), 1e308])
def test_new_contribution_errors(geometry, materials, component, volume, value):
    """Yeni katkıların invalid derived mass'i mevcut structured hata yolunu kullanır."""
    with pytest.raises(MassValidationError) as caught:
        evaluate(replace(geometry, **{volume: value}), materials)
    assert caught.value.error_code == 'INVALID_COMPONENT_MASS'
    assert caught.value.field_name == component + '.mass_kg'
