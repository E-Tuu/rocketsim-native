"""MASS-T01..T28: accepted geometry otoritesinden structural rho*V ve CG."""

from dataclasses import FrozenInstanceError, fields, replace
import inspect
from math import isfinite

import pytest

from roketsim_native.geometry.models import (
    ReferenceGeometryPolicy, FinCrossSection,
    ConicalNoseGeometry, CylindricalBodyGeometry, NoseConstructionMode,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.materials.models import BulkMaterial, SingleStageRocketMaterials
from roketsim_native.mass import models, structural
from roketsim_native.mass.models import ComponentMassProperties, StructuralMassProperties, MassValidationError
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator


CALCULATOR = StructuralMassPropertiesCalculator()


@pytest.fixture
def geometry():
    return GeometryResolver().resolve(rocket_geometry=SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002),
        TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003, FinCrossSection.SQUARE),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.120, .029, .001, 0.),
                                CenteringRingPairGeometry(.003), .005), ReferenceGeometryPolicy.MAXIMUM_DIAMETER))


@pytest.fixture
def materials():
    """Yalnız fixture seçimi; production default değildir."""
    return SingleStageRocketMaterials(POLYSTYRENE, CARDBOARD, CARDBOARD, CARDBOARD, CARDBOARD)


def evaluate(geometry, materials):
    return CALCULATOR.evaluate(resolved_geometry=geometry, materials=materials)


@pytest.mark.parametrize('component,volume_field,centroid_field', [
    pytest.param('nose', 'nose_material_volume_m3', 'nose_volume_centroid_x_geo_m', id='MASS-T01-T04'),
    pytest.param('body', 'body_material_volume_m3', 'body_volume_centroid_x_geo_m', id='MASS-T02-T05'),
    pytest.param('fins', 'fin_set_material_volume_m3', 'fin_set_volume_centroid_x_geo_m', id='MASS-T03-T06'),
])
def test_component_equations(geometry, materials, component, volume_field, centroid_field):
    """MASS-T01..06/13: Geometry hacim/centroid ve seçilen material provenance."""
    result = getattr(evaluate(geometry, materials), component)
    material = getattr(materials, component)
    assert result.mass_kg == material.density_kg_m3 * getattr(geometry, volume_field)
    assert result.cg_x_geo_m == getattr(geometry, centroid_field)
    assert result.material is material


def test_aggregate_equations(geometry, materials):
    """MASS-T07/T08: Direct mass toplamı ve weighted CG; arithmetic ortalama değil."""
    result = evaluate(geometry, materials)
    components = (result.nose, result.body, result.fins, result.motor_mount, result.centering_rings)
    total = sum(c.mass_kg for c in components)
    assert result.structure_mass_kg == total
    assert result.structure_cg_x_geo_m == sum(c.mass_kg * c.cg_x_geo_m for c in components) / total
    assert result.structure_cg_x_geo_m != sum(c.cg_x_geo_m for c in components) / 5


@pytest.mark.parametrize('component', ['nose', 'body', 'fins'])
def test_density_scaling(geometry, materials, component):
    """MASS-T09/T10: Tek material değişimi yalnız ilgili component mass'i değiştirir."""
    original = evaluate(geometry, materials)
    selected = getattr(materials, component)
    changed = replace(materials, **{component: BulkMaterial('Double', 2 * selected.density_kg_m3)})
    result = evaluate(geometry, changed)
    for name in ('nose', 'body', 'fins'):
        assert getattr(result, name).mass_kg == getattr(original, name).mass_kg * (2 if name == component else 1)


def test_material_redistribution(geometry, materials):
    """MASS-T11: Material dağılımı geometriyi değil weighted CG'yi değiştirir."""
    changed = SingleStageRocketMaterials(CARDBOARD, CARDBOARD, POLYSTYRENE, CARDBOARD, CARDBOARD)
    result = evaluate(geometry, changed)
    masses = (680 * geometry.nose_material_volume_m3, 680 * geometry.body_material_volume_m3,
              1050 * geometry.fin_set_material_volume_m3,
              680 * geometry.motor_mount_material_volume_m3, 680 * geometry.centering_ring_pair_material_volume_m3)
    expected = sum(m*x for m,x in zip(masses, (geometry.nose_volume_centroid_x_geo_m,
        geometry.body_volume_centroid_x_geo_m, geometry.fin_set_volume_centroid_x_geo_m,
        geometry.motor_mount_volume_centroid_x_geo_m, geometry.centering_ring_pair_volume_centroid_x_geo_m))) / sum(masses)
    assert result.structure_cg_x_geo_m == pytest.approx(expected, rel=3e-15)
    assert result.structure_cg_x_geo_m > evaluate(geometry, materials).structure_cg_x_geo_m


def test_uniform_density_scale(geometry):
    """MASS-T12: Uniform density ölçeği CG'yi değiştirmez."""
    low, high = BulkMaterial('Low', 100.), BulkMaterial('High', 200.)
    a = evaluate(geometry, SingleStageRocketMaterials(low, low, low, low, low))
    b = evaluate(geometry, SingleStageRocketMaterials(high, high, high, high, high))
    assert a.structure_cg_x_geo_m == b.structure_cg_x_geo_m
    assert b.structure_mass_kg == 2 * a.structure_mass_kg


def test_result_contracts(geometry, materials):
    """MASS-T14/T15/T16: Frozen/slotted sonuçlar volume otoritesini çoğaltmaz."""
    assert [f.name for f in fields(ComponentMassProperties)] == ['material', 'mass_kg', 'cg_x_geo_m']
    assert [f.name for f in fields(StructuralMassProperties)] == [
        'nose', 'body', 'fins', 'motor_mount', 'centering_rings', 'structure_mass_kg', 'structure_cg_x_geo_m']
    result = evaluate(geometry, materials)
    for obj in (result, result.nose, result.body, result.fins, result.motor_mount, result.centering_rings):
        assert not hasattr(obj, '__dict__')
        with pytest.raises(FrozenInstanceError):
            setattr(obj, fields(obj)[0].name, None)


def test_api_scope():
    """MASS-T17/T27/T28: Parametresiz calculator, mandatory keyword girdiler; motor yok."""
    assert not inspect.signature(StructuralMassPropertiesCalculator).parameters
    parameters = inspect.signature(CALCULATOR.evaluate).parameters
    assert tuple(parameters) == ('resolved_geometry', 'materials')
    assert all(p.kind is inspect.Parameter.KEYWORD_ONLY and p.default is inspect.Parameter.empty
               for p in parameters.values())
    assert structural.__all__ == ('StructuralMassPropertiesCalculator',)
    assert set(models.__all__) == {'ComponentMassProperties', 'StructuralMassProperties', 'MassValidationError'}
    with pytest.raises(TypeError):
        CALCULATOR.evaluate()


def test_determinism_and_inputs(geometry, materials):
    """MASS-T18/T19: Immutable upstream input'lar ve repeated result korunur."""
    source_before = repr(geometry), repr(materials)
    expected = evaluate(geometry, materials)
    for _ in range(5):
        assert evaluate(geometry, materials) == expected
    assert (repr(geometry), repr(materials)) == source_before
    assert {name for name in dir(CALCULATOR) if not name.startswith('_')} == {'evaluate'}


def test_success_invariants(geometry, materials):
    """MASS-T20/T21: Pozitif finite mass'ler ve rocket extent içinde CG."""
    result = evaluate(geometry, materials)
    for mass in (result.nose.mass_kg, result.body.mass_kg, result.fins.mass_kg,
                 result.motor_mount.mass_kg, result.centering_rings.mass_kg, result.structure_mass_kg):
        assert isfinite(mass) and mass > 0
    assert isfinite(result.structure_cg_x_geo_m)
    assert 0 <= result.structure_cg_x_geo_m <= geometry.overall_length_m


@pytest.mark.parametrize('volume', [0., -1., float('nan'), float('inf'), 1e308, 5e-324])
def test_invalid_component_mass(geometry, materials, volume):
    """MASS-T22/T24: Inconsistent upstream veya numerical overflow/underflow explicit hatadır."""
    if volume == 5e-324:
        materials = replace(materials, nose=BulkMaterial('Tiny', 5e-324))
    with pytest.raises(MassValidationError) as caught:
        evaluate(replace(geometry, nose_material_volume_m3=volume), materials)
    assert caught.value.error_code == 'INVALID_COMPONENT_MASS'
    assert caught.value.field_name == 'nose.mass_kg'
    expected = materials.nose.density_kg_m3 * volume
    assert caught.value.value == expected or (not isfinite(expected) and not isfinite(caught.value.value))


def test_aggregate_overflow(geometry):
    """MASS-T22: Finite component mass toplamı taşarsa onarılmaz."""
    material = BulkMaterial('Unit', 1.)
    geometry = replace(geometry, nose_material_volume_m3=1e308,
        body_material_volume_m3=1e308, fin_set_material_volume_m3=1e308)
    with pytest.raises(MassValidationError) as caught:
        evaluate(geometry, SingleStageRocketMaterials(material, material, material, material, material))
    assert caught.value.error_code == 'INVALID_STRUCTURAL_MASS'


@pytest.mark.parametrize('centroid', [-10., 10., float('nan'), float('inf')])
def test_bad_centroids(geometry, materials, centroid):
    """MASS-T21/T22/T24: Non-finite CG ve extent dışı weighted CG clamp edilmez."""
    geometry = replace(geometry, nose_volume_centroid_x_geo_m=centroid,
        body_volume_centroid_x_geo_m=centroid, fin_set_volume_centroid_x_geo_m=centroid)
    with pytest.raises(MassValidationError) as caught:
        evaluate(geometry, materials)
    assert caught.value.error_code in ('INVALID_COMPONENT_CG', 'STRUCTURAL_CG_OUTSIDE_EXTENT')


def test_full_fixture(geometry, materials):
    """MASS-T25: Gerçek A.1 fixture ve yalnız bu testin material seçimi."""
    result = evaluate(geometry, materials)
    # Eski üç-component fixture değişmez; toplam artık iki ek katkı içerir.
    old_components = (result.nose, result.body, result.fins)
    old_mass = sum(c.mass_kg for c in old_components)
    old_cg = sum(c.mass_kg * c.cg_x_geo_m for c in old_components) / old_mass
    assert (result.nose.mass_kg, result.body.mass_kg, result.fins.mass_kg,
        old_mass, old_cg) == pytest.approx(
        (.09631183149995425, .29309802820931397, .127296, .5167058597092682, .6059122913310996), rel=3e-14)
    assert result.structure_mass_kg == old_mass + result.motor_mount.mass_kg + result.centering_rings.mass_kg


def test_geometry_authority(geometry, materials):
    """MASS-T26: Test-only resolved perturbation source dimensions'ın yeniden hesaplanmadığını gösterir."""
    changed = replace(geometry, nose_material_volume_m3=.0002, nose_volume_centroid_x_geo_m=.25)
    result = evaluate(changed, materials)
    assert changed.source is geometry.source
    assert result.nose.mass_kg == 1050 * .0002
    assert result.nose.cg_x_geo_m == .25


def test_weighted_moment_overflow(geometry):
    """MASS-T22/T24: Finite total mass'e rağmen taşan weighted moment mass hatasıdır."""
    material = BulkMaterial('Unit', 1.)
    geometry = replace(geometry, nose_material_volume_m3=1e307,
        nose_volume_centroid_x_geo_m=100., overall_length_m=200.)
    with pytest.raises(MassValidationError) as caught:
        evaluate(geometry, SingleStageRocketMaterials(material, material, material, material, material))
    assert caught.value.error_code == 'INVALID_STRUCTURAL_CG'
    assert caught.value.field_name == 'structure_cg_x_geo_m'
    assert not isfinite(caught.value.value)


@pytest.mark.parametrize('extent', [0., -1., float('nan'), float('inf')])
def test_invalid_extent(geometry, materials, extent):
    """Tutarsız resolved extent, başarılı CG invariant'ı gibi kabul edilmez."""
    with pytest.raises(MassValidationError) as caught:
        evaluate(replace(geometry, overall_length_m=extent), materials)
    assert caught.value.error_code == 'INVALID_GEOMETRY_EXTENT'
