"""MAT-T01..T08: katalog, explicit atama ve material validation."""

from dataclasses import FrozenInstanceError
import inspect

import pytest

from roketsim_native.materials import catalog
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.materials.models import BulkMaterial, SingleStageRocketMaterials, MaterialValidationError


@pytest.mark.parametrize('material,name,density', [
    pytest.param(CARDBOARD, 'Cardboard', 680., id='MAT-T01'),
    pytest.param(POLYSTYRENE, 'Polystyrene', 1050., id='MAT-T02'),
])
def test_catalog(material, name, density):
    """İlk demo kataloğunda yalnız iki frozen seçenek vardır."""
    assert material == BulkMaterial(name, density)
    assert catalog.__all__ == ('CARDBOARD', 'POLYSTYRENE')


def test_immutable():
    """MAT-T03: BulkMaterial frozen/slotted'dır."""
    assert not hasattr(CARDBOARD, '__dict__')
    with pytest.raises(FrozenInstanceError):
        CARDBOARD.density_kg_m3 = 1.


@pytest.mark.parametrize('density', [0., -1.])
def test_density_domain(density):
    """MAT-T04 / MASS-T23: Finite invalid density structured hata verir."""
    with pytest.raises(MaterialValidationError) as caught:
        BulkMaterial('Test', density)
    assert caught.value.error_code == 'NON_POSITIVE_DENSITY'
    assert caught.value.field_name == 'density_kg_m3'
    assert caught.value.value == density


@pytest.mark.parametrize('density', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite_density(density):
    """MAT-T05: Non-finite density generic ValueError yolunu kullanır."""
    with pytest.raises(ValueError) as caught:
        BulkMaterial('Test', density)
    assert type(caught.value) is ValueError


@pytest.mark.parametrize('name', ['', ' ', '\t\n'])
def test_empty_name(name):
    """MAT-T06 / MASS-T23: Empty/whitespace material adı reddedilir."""
    with pytest.raises(MaterialValidationError) as caught:
        BulkMaterial(name, 1.)
    assert caught.value.error_code == 'EMPTY_MATERIAL_NAME'
    assert caught.value.field_name == 'name'
    assert caught.value.value == name


def test_explicit_assignment():
    """MAT-T07 / MASS-T27: Her atama mandatory; fiziksel default yoktur."""
    parameters = inspect.signature(SingleStageRocketMaterials).parameters
    assert tuple(parameters) == ('nose', 'body', 'fins', 'motor_mount', 'centering_rings')
    assert all(p.default is inspect.Parameter.empty for p in parameters.values())
    with pytest.raises(TypeError):
        SingleStageRocketMaterials()
    with pytest.raises(TypeError):
        SingleStageRocketMaterials(nose=CARDBOARD, body=POLYSTYRENE)


def test_independent_assignments():
    """MAT-T08: Genel BulkMaterial kullanılabilir; component seçimi hard-coded değil."""
    third = BulkMaterial('Test material', 900.)
    assignment = SingleStageRocketMaterials(CARDBOARD, third, POLYSTYRENE, CARDBOARD, third)
    assert assignment.nose is CARDBOARD
    assert assignment.body is third
    assert assignment.fins is POLYSTYRENE
    assert not hasattr(assignment, '__dict__')
    with pytest.raises(FrozenInstanceError):
        assignment.nose = third
