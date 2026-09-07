"""RMASS-T01..38: bir yapı + bir runtime motor katkısı; yeni motor fiziği yok."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature

import pytest

from roketsim_native.geometry.models import (
    ReferenceGeometryPolicy, FinCrossSection,
    ConicalNoseGeometry, CylindricalBodyGeometry, NoseConstructionMode,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.materials.models import SingleStageRocketMaterials
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.mass.models import MassValidationError
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator
from roketsim_native.mass import total
from roketsim_native.mass.total import RocketMassProperties, RocketMassPropertiesCalculator
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T
from roketsim_native.propulsion.installation import MotorInstallationResolver
from roketsim_native.propulsion.properties import (
    MotorPropertyEvaluator, MotorMassProperties, MotorPropertyModelProfile,
    MotorMassEvolutionModel, MotorCgEvolutionModel, DEMO_MOTOR_PROPERTY_MODEL_PROFILE,
)


@pytest.fixture
def upstream():
    """Kabul edilmiş gerçek Geometry→Structure ve Installation→Motor zinciri."""
    geometry = GeometryResolver().resolve(rocket_geometry=SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002),
        TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003, FinCrossSection.SQUARE),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.12, .029, .001, 0.),
                                CenteringRingPairGeometry(.003), .005), ReferenceGeometryPolicy.MAXIMUM_DIAMETER))
    structure = StructuralMassPropertiesCalculator().evaluate(resolved_geometry=geometry,
        materials=SingleStageRocketMaterials(POLYSTYRENE,CARDBOARD,CARDBOARD,CARDBOARD,CARDBOARD))
    installed = MotorInstallationResolver().resolve(resolved_geometry=geometry,motor=AEROTECH_F50_4T)
    return structure, installed


def runtime(installed,time):
    return MotorPropertyEvaluator().evaluate(installation=installed,
        model_profile=DEMO_MOTOR_PROPERTY_MODEL_PROFILE,motor_time_s=time)


def aggregate(structure,motor):
    return RocketMassPropertiesCalculator().evaluate(structural_properties=structure,motor_properties=motor)


def test_result_contract(upstream):
    """RMASS-T01/T04: İki alanlı frozen/slotted sonuç."""
    structure,installed = upstream
    result = aggregate(structure,runtime(installed,0.))
    assert [f.name for f in fields(result)] == ['total_mass_kg','total_cg_x_geo_m']
    assert not hasattr(result,'__dict__')
    with pytest.raises(FrozenInstanceError):
        result.total_mass_kg = 0.


def test_api():
    """RMASS-T02/T03/T33: Parametresiz; iki zorunlu keyword-only katkı, zaman yok."""
    assert not signature(RocketMassPropertiesCalculator).parameters
    calculator = RocketMassPropertiesCalculator()
    params = signature(calculator.evaluate).parameters
    assert list(params) == ['structural_properties','motor_properties']
    assert all(p.kind is Parameter.KEYWORD_ONLY and p.default is Parameter.empty for p in params.values())
    assert not hasattr(calculator,'__dict__')


def test_equations_and_authority(upstream):
    """RMASS-T05..08: Hazır aggregate değiştirilince alt bileşen/model yeniden çözülmez."""
    structure,installed = upstream
    structure = replace(structure,structure_mass_kg=2.,structure_cg_x_geo_m=3.)
    motor = replace(runtime(installed,0.),mass_kg=1.,cg_x_geo_m=6.,cg_local_from_front_m=.001)
    result = aggregate(structure,motor)
    assert result.total_mass_kg == 3.
    assert result.total_cg_x_geo_m == (2.*3.+1.*6.)/3. == 4.


@pytest.mark.parametrize('mass_model',list(MotorMassEvolutionModel))
@pytest.mark.parametrize('cg_model',list(MotorCgEvolutionModel))
def test_model_independence(upstream,mass_model,cg_model):
    """RMASS-T09/T10/T35: Aynı runtime değerleri hangi profilden gelirse gelsin aynı."""
    structure,installed = upstream
    motor = runtime(installed,.354)
    changed = replace(motor,model_profile=MotorPropertyModelProfile(mass_model,cg_model))
    assert aggregate(structure,changed) == aggregate(structure,motor)


@pytest.mark.parametrize('coordinate',[-10.,0.,.626619970554718,1e100])
@pytest.mark.parametrize('masses',[(.1,.3),(1e-100,1e100)])
def test_equal_cgs(upstream,coordinate,masses):
    """RMASS-T11: Kütle oranından bağımsız exact ortak CG; geometri aralığı uygulanmaz."""
    structure,installed = upstream
    structure = replace(structure,structure_mass_kg=masses[0],structure_cg_x_geo_m=coordinate)
    motor = replace(runtime(installed,0.),mass_kg=masses[1],cg_x_geo_m=coordinate)
    assert aggregate(structure,motor).total_cg_x_geo_m == coordinate


@pytest.mark.parametrize('structure_cg,motor_cg',[(1.,4.),(4.,1.),(-4.,-1.),(-1.,-4.)])
def test_both_orderings_and_span(upstream,structure_cg,motor_cg):
    """RMASS-T12..14/T34: İki yön ve negatif x_geo; contributor span Geometry değildir."""
    structure,installed = upstream
    structure = replace(structure,structure_mass_kg=2.,structure_cg_x_geo_m=structure_cg)
    motor = replace(runtime(installed,0.),mass_kg=1.,cg_x_geo_m=motor_cg)
    result = aggregate(structure,motor)
    assert result.total_cg_x_geo_m == (2*structure_cg+motor_cg)/3
    assert min(structure_cg,motor_cg) <= result.total_cg_x_geo_m <= max(structure_cg,motor_cg)


@pytest.mark.parametrize('structure_mass,motor_mass,code',[
    (0.,0.,'NON_POSITIVE_TOTAL_MASS'),(-2.,1.,'NON_POSITIVE_TOTAL_MASS'),
    (1e308,1e308,'INVALID_TOTAL_MASS'),(float('nan'),1.,'INVALID_TOTAL_MASS'),
    (1.,float('inf'),'INVALID_TOTAL_MASS')])
def test_invalid_total_mass(upstream,structure_mass,motor_mass,code):
    """RMASS-T15/T16/T19/T20: Bozuk upstream toplamı structured mass hatası; repair yok."""
    structure,installed = upstream
    structure = replace(structure,structure_mass_kg=structure_mass)
    motor = replace(runtime(installed,0.),mass_kg=motor_mass)
    with pytest.raises(MassValidationError) as caught:
        aggregate(structure,motor)
    assert caught.value.error_code == code
    assert caught.value.field_name == 'total_mass_kg'
    if code == 'NON_POSITIVE_TOTAL_MASS':
        assert caught.value.value == structure_mass+motor_mass


@pytest.mark.parametrize('structure_cg,motor_cg',[(float('nan'),1.),(1.,float('inf')),(1e308,0.)])
def test_invalid_total_cg(upstream,structure_cg,motor_cg):
    """RMASS-T17: Moment taşması veya non-finite katkı CG'si sessizce onarılmaz."""
    structure,installed = upstream
    structure = replace(structure,structure_mass_kg=2.,structure_cg_x_geo_m=structure_cg)
    motor = replace(runtime(installed,0.),mass_kg=1.,cg_x_geo_m=motor_cg)
    with pytest.raises(MassValidationError) as caught:
        aggregate(structure,motor)
    assert caught.value.error_code == 'INVALID_TOTAL_CG'


def test_span_failure(upstream):
    """RMASS-T18/T19/T20: Tutarsız upstream katkıları span ihlaliyle reddedilir."""
    structure,installed = upstream
    structure = replace(structure,structure_mass_kg=2.,structure_cg_x_geo_m=1.)
    motor = replace(runtime(installed,0.),mass_kg=-1.,cg_x_geo_m=2.)
    with pytest.raises(MassValidationError) as caught:
        aggregate(structure,motor)
    assert caught.value.error_code == 'TOTAL_CG_OUTSIDE_CONTRIBUTOR_SPAN'
    assert caught.value.field_name == 'total_cg_x_geo_m'
    assert caught.value.value == 0.


@pytest.mark.parametrize('time,mass,cg',[
    pytest.param(0.,.6357490028277474,.6706064563777736,id='RMASS-T21-T22'),
    pytest.param(.354,.6232504730167545,.6648832361521045,id='RMASS-T23-T24'),
    pytest.param(1.4,.5978651442997246,.6525224306832178,id='RMASS-T25-T26'),
    pytest.param(1.43,.5978490028277474,.6525142370178312,id='RMASS-T27-T28')])
def test_f50_integration(upstream,time,mass,cg):
    structure,installed = upstream
    motor = runtime(installed,time)
    result = aggregate(structure,motor)
    assert result.total_mass_kg == structure.structure_mass_kg+motor.mass_kg
    assert result.total_mass_kg == pytest.approx(mass,rel=3e-15,abs=0)
    if time in (0.,1.43):
        assert result.total_mass_kg == mass
    assert result.total_cg_x_geo_m == pytest.approx(cg,rel=3e-15,abs=0)


def test_tail_and_forward_shift(upstream):
    """RMASS-T29/T30: Kuyruk final state'e çevrilmez; aft kütle azalınca CG öne gider."""
    structure,installed = upstream
    initial,tail,end = [aggregate(structure,runtime(installed,t)) for t in [0.,1.4,1.43]]
    assert tail != end
    assert initial.total_mass_kg > tail.total_mass_kg > end.total_mass_kg
    assert initial.total_cg_x_geo_m > tail.total_cg_x_geo_m > end.total_cg_x_geo_m


def test_input_immutability_and_determinism(upstream):
    """RMASS-T31/T32: Hazır sonuçlar değişmez; önceki çağrı etkisi yok."""
    structure,installed = upstream
    motor = runtime(installed,.354)
    before = (repr(structure),repr(motor))
    calculator = RocketMassPropertiesCalculator()
    expected = aggregate(structure,motor)
    for _ in range(5):
        assert calculator.evaluate(structural_properties=structure,motor_properties=motor) == expected
    assert (repr(structure),repr(motor)) == before


def test_scope():
    """RMASS-T33..38: Zaman/geometri/physic-model/arbitrary contributor API yok."""
    assert set(total.__all__) == {'RocketMassProperties','RocketMassPropertiesCalculator'}
    assert [name for name in dir(RocketMassPropertiesCalculator) if not name.startswith('_')] == ['evaluate']
    assert list(signature(RocketMassPropertiesCalculator().evaluate).parameters) == ['structural_properties','motor_properties']
