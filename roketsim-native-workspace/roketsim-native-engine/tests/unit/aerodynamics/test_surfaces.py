"""SURFACE-T01..14: bağımsız, açık yüzey tasarım verisi; preset veya drag yok."""

from dataclasses import FrozenInstanceError, fields
from inspect import Parameter, signature

import pytest

from roketsim_native.aerodynamics import surfaces
from roketsim_native.aerodynamics.surfaces import (
    SurfaceFinish, SingleStageRocketAerodynamicSurfaces, SurfaceValidationError,
)
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.materials.models import SingleStageRocketMaterials


def test_immutable_contracts():
    """SURFACE-T01/T08: İki değer nesnesi frozen/slotted; field sahiplikleri açık."""
    finish = SurfaceFinish('Synthetic finish',1e-5)
    assignment = SingleStageRocketAerodynamicSurfaces(finish,finish,finish)
    assert [f.name for f in fields(finish)] == ['name','equivalent_roughness_m']
    assert [f.name for f in fields(assignment)] == ['nose','body','fins']
    for obj in (finish,assignment):
        assert not hasattr(obj,'__dict__')
        with pytest.raises(FrozenInstanceError):
            setattr(obj,fields(obj)[0].name,None)


@pytest.mark.parametrize('name',['','   ',None])
def test_empty_name(name):
    """SURFACE-T02: Boş finish adı structured hata."""
    with pytest.raises(SurfaceValidationError) as caught:
        SurfaceFinish(name,0.)
    assert (caught.value.error_code,caught.value.field_name,caught.value.value) == ('EMPTY_SURFACE_FINISH_NAME','name',name)


@pytest.mark.parametrize('roughness',[float('nan'),float('inf'),-float('inf')])
def test_nonfinite(roughness):
    """SURFACE-T03/T07: Ham non-finite pürüzlülük generic ValueError."""
    with pytest.raises(ValueError) as caught:
        SurfaceFinish('Synthetic',roughness)
    assert type(caught.value) is ValueError


@pytest.mark.parametrize('roughness',[0.,1e-6,100.])
def test_valid_roughness(roughness):
    """SURFACE-T04/T05: Sıfır geçerli; keyfi üst sınır uygulanmaz."""
    assert SurfaceFinish('Synthetic',roughness).equivalent_roughness_m == roughness


def test_negative_roughness():
    """SURFACE-T06: Negatif değer abs/clamp ile düzeltilmez."""
    with pytest.raises(SurfaceValidationError) as caught:
        SurfaceFinish('Synthetic',-1e-5)
    assert caught.value.error_code == 'NEGATIVE_EQUIVALENT_ROUGHNESS'
    assert caught.value.field_name == 'equivalent_roughness_m'
    assert caught.value.value == -1e-5


def test_mandatory_assignment():
    """SURFACE-T09: Üç seçim zorunlu; model veya malzeme default'u yok."""
    params = signature(SingleStageRocketAerodynamicSurfaces).parameters
    assert list(params) == ['nose','body','fins']
    assert all(p.default is Parameter.empty for p in params.values())
    with pytest.raises(TypeError):
        SingleStageRocketAerodynamicSurfaces()


def test_independent_and_shared_assignment():
    """SURFACE-T10/T11: Ayrı finish veya aynı immutable nesne paylaşımı desteklenir."""
    nose,body,fins = [SurfaceFinish(name,k) for name,k in [('A',0.),('B',1e-5),('C',2e-5)]]
    result = SingleStageRocketAerodynamicSurfaces(nose,body,fins)
    assert result.nose is nose and result.body is body and result.fins is fins
    shared = SingleStageRocketAerodynamicSurfaces(nose,nose,nose)
    assert shared.nose is shared.body is shared.fins


def test_material_surface_independence():
    """SURFACE-T12/T13: Material seçimi/yoğunluğu ile surface ataması ilişkilendirilmez."""
    material = SingleStageRocketMaterials(POLYSTYRENE,CARDBOARD,CARDBOARD,CARDBOARD,CARDBOARD)
    before = repr(material)
    finishes = [SurfaceFinish('A',0.),SurfaceFinish('B',1e-5)]
    for finish in finishes:
        assignment = SingleStageRocketAerodynamicSurfaces(finish,finish,finish)
        assert assignment.nose is finish
        assert repr(material) == before
    assert not hasattr(material,'surface_finish')
    assert CARDBOARD.density_kg_m3 == 680.
    assert POLYSTYRENE.density_kg_m3 == 1050.


def test_no_presets_or_physics():
    """SURFACE-T14/SCOPE-T01..06: Yalnız veri sınıfları; preset/katsayı/fizik hesabı yok."""
    assert set(surfaces.__all__) == {'SurfaceFinish','SurfaceValidationError','SingleStageRocketAerodynamicSurfaces'}
    assert not any(isinstance(value,SurfaceFinish) for value in vars(surfaces).values())
    assert not [name for name in dir(SurfaceFinish) if not name.startswith('_') and callable(getattr(SurfaceFinish,name))]
