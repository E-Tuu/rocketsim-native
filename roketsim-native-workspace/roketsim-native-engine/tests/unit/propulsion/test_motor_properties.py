"""MPROP-T01..68: kaynak, açık politika ve motor runtime sonucu ayrı doğrulanır."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature

import pytest

from roketsim_native.geometry.models import (
    ReferenceGeometryPolicy, FinCrossSection, FinAngularArrangement,
    ConicalNoseGeometry, CylindricalBodyGeometry, NoseConstructionMode,
    SingleStageRocketGeometry, TrapezoidalFinSetGeometry,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.propulsion import properties
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T as F50
from roketsim_native.propulsion.models import (
    MotorMassSample, MotorCgSample, MotorDefinition, MotorDataProvenance,
    MotorValidationError, ThrustSample,
)
from roketsim_native.propulsion.installation import MotorInstallation, MotorInstallationResolver
from roketsim_native.propulsion.thrust import (
    MotorThrustCurveEvaluator, MotorCurveAnalyzer, MotorThrustState, PropulsionEvaluationError,
)
from roketsim_native.propulsion.properties import (
    MotorMassEvolutionModel as MassModel, MotorCgEvolutionModel as CgModel,
    MotorPropertyModelProfile as Profile, MotorMassProperties, MotorPropertyEvaluator,
    DEMO_MOTOR_PROPERTY_MODEL_PROFILE as DEMO,
)

EXPLICIT = Profile(MassModel.EXPLICIT_CURVE, CgModel.EXPLICIT_CURVE)


@pytest.fixture
def installation():
    """Katalog F50 ve gerçek A.2/C.2 zinciri; ölçüler test seçimi."""
    raw = SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002),
        TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003, FinCrossSection.SQUARE,
                                 FinAngularArrangement.EQUALLY_SPACED),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.12, .029, .001, 0.),
                                CenteringRingPairGeometry(.003), .005), ReferenceGeometryPolicy.MAXIMUM_DIAMETER)
    geometry = GeometryResolver().resolve(rocket_geometry=raw)
    return MotorInstallationResolver().resolve(resolved_geometry=geometry, motor=F50)


@pytest.fixture
def source_motor():
    return replace(F50, motor_id='synthetic_source', initial_mass_kg=.1, length_m=.1,
        thrust_curve=(ThrustSample(0.,0.), ThrustSample(.2,20.), ThrustSample(.8,0.)),
        mass_curve=(MotorMassSample(0.,.1), MotorMassSample(.5,.08), MotorMassSample(1.,.06)),
        cg_curve=(MotorCgSample(0.,.04), MotorCgSample(.5,.05), MotorCgSample(1.,.06)),
        provenance=replace(F50.provenance, mass_curve_source='Synthetic test mass source',
                           cg_curve_source='Synthetic test CG source'))


@pytest.fixture
def source_installation(installation, source_motor):
    return replace(installation, motor=source_motor, motor_front_x_geo_m=.9, motor_aft_x_geo_m=1.)


def evaluate(installation, profile, time):
    return MotorPropertyEvaluator().evaluate(installation=installation,
        model_profile=profile, motor_time_s=time)


@pytest.mark.parametrize('sample', [MotorMassSample(0.,.1), MotorCgSample(0.,0.)])
def test_sample_immutable(sample):
    """MPROP-T01/T04: Kaynak örnekleri frozen/slotted."""
    assert not hasattr(sample, '__dict__')
    with pytest.raises(FrozenInstanceError):
        sample.time_s = 1.


@pytest.mark.parametrize('cls,name,value,code', [
    (MotorMassSample, 'time_s', -1., 'NEGATIVE_MASS_SAMPLE_TIME'),
    (MotorMassSample, 'mass_kg', 0., 'NON_POSITIVE_MASS_SAMPLE'),
    (MotorMassSample, 'mass_kg', -1., 'NON_POSITIVE_MASS_SAMPLE'),
    (MotorCgSample, 'time_s', -1., 'NEGATIVE_CG_SAMPLE_TIME'),
    (MotorCgSample, 'cg_from_front_m', -1., 'NEGATIVE_CG_POSITION'),
])
def test_sample_domains(cls, name, value, code):
    """MPROP-T02/T05: Sonlu fiziksel geçersizlik structured MotorValidationError."""
    values = {'time_s': 0., 'mass_kg' if cls is MotorMassSample else 'cg_from_front_m': .1}
    values[name] = value
    with pytest.raises(MotorValidationError) as caught:
        cls(**values)
    assert (caught.value.error_code, caught.value.field_name, caught.value.value) == (code, name, value)


@pytest.mark.parametrize('cls,name', [(MotorMassSample,'time_s'), (MotorMassSample,'mass_kg'),
    (MotorCgSample,'time_s'), (MotorCgSample,'cg_from_front_m')])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_sample_nonfinite(cls, name, value):
    """MPROP-T03/T06: Ham non-finite kaynak sayısı generic ValueError."""
    values = {'time_s': 0., 'mass_kg' if cls is MotorMassSample else 'cg_from_front_m': .1}
    values[name] = value
    with pytest.raises(ValueError) as caught:
        cls(**values)
    assert type(caught.value) is ValueError


def test_explicit_absence_and_schema():
    """MPROP-T07/T08/T22/T23: None açık yokluk; varsayılan kaynak veya profil yok."""
    assert F50.mass_curve is F50.cg_curve is None
    assert F50.provenance.mass_curve_source is F50.provenance.cg_curve_source is None
    assert (F50.initial_mass_kg, F50.propellant_mass_kg, F50.length_m) == (.0849,.0379,.098)
    for cls, names in [(MotorDefinition, ['mass_curve','cg_curve']),
                       (MotorDataProvenance, ['mass_curve_source','cg_curve_source'])]:
        for name in names:
            assert signature(cls).parameters[name].default is Parameter.empty


@pytest.mark.parametrize('name,points,code', [
    ('mass_curve', [], 'TOO_FEW_MASS_SAMPLES'),
    ('mass_curve', [(0.,.1)], 'TOO_FEW_MASS_SAMPLES'),
    ('mass_curve', [(.1,.1),(1.,.06)], 'INVALID_MASS_CURVE_START'),
    ('mass_curve', [(0.,.1),(0.,.08),(1.,.06)], 'NON_INCREASING_MASS_CURVE_TIME'),
    ('mass_curve', [(0.,.1),(.7,.08),(.5,.07),(1.,.06)], 'NON_INCREASING_MASS_CURVE_TIME'),
    ('mass_curve', [(0.,.1),(1.,.11)], 'MASS_CURVE_INCREASES'),
    ('mass_curve', [(0.,.099),(1.,.06)], 'MASS_CURVE_INITIAL_MISMATCH'),
    ('mass_curve', [(0.,.1),(.79,.06)], 'MASS_CURVE_INSUFFICIENT_COVERAGE'),
    ('cg_curve', [], 'TOO_FEW_CG_SAMPLES'),
    ('cg_curve', [(0.,.04)], 'TOO_FEW_CG_SAMPLES'),
    ('cg_curve', [(.1,.04),(1.,.06)], 'INVALID_CG_CURVE_START'),
    ('cg_curve', [(0.,.04),(0.,.05),(1.,.06)], 'NON_INCREASING_CG_CURVE_TIME'),
    ('cg_curve', [(0.,.04),(.7,.05),(.5,.06),(1.,.06)], 'NON_INCREASING_CG_CURVE_TIME'),
    ('cg_curve', [(0.,.04),(1.,.101)], 'CG_OUTSIDE_MOTOR'),
    ('cg_curve', [(0.,.04),(.79,.06)], 'CG_CURVE_INSUFFICIENT_COVERAGE'),
])
def test_source_curve_validation(source_motor, name, points, code):
    """MPROP-T09..19: Kaynak grid/değer/coverage koşulları onarılmadan doğrulanır."""
    cls = MotorMassSample if name == 'mass_curve' else MotorCgSample
    with pytest.raises(MotorValidationError) as caught:
        replace(source_motor, **{name: tuple(cls(*p) for p in points)})
    assert caught.value.error_code == code


@pytest.mark.parametrize('name', ['mass_curve','cg_curve'])
def test_tuple_required(source_motor, name):
    """MPROP-T09/T15: Liste veya yanlış örnek tipi sessizce dönüştürülmez."""
    for value in [list(getattr(source_motor,name)), (ThrustSample(0.,0.), ThrustSample(1.,0.))]:
        with pytest.raises(MotorValidationError):
            replace(source_motor, **{name:value})


def test_source_boundaries(source_motor):
    """MPROP-T12/T14/T18/T19: Sabit mass, tam coverage ve iki yüz CG sınırı geçerli."""
    motor = replace(source_motor,
        mass_curve=(MotorMassSample(0.,.1),MotorMassSample(.8,.1)),
        cg_curve=(MotorCgSample(0.,0.),MotorCgSample(.3,.1),MotorCgSample(.8,0.)))
    assert motor.cg_curve[1].cg_from_front_m == motor.length_m
    assert motor.mass_curve[-1].time_s == motor.thrust_curve[-1].time_s


@pytest.mark.parametrize('name', ['mass','cg'])
def test_source_provenance_consistency(source_motor, name):
    """MPROP-T20/T21: Kaynak eğrisi varsa referansı var; yoksa referansı da yok."""
    with pytest.raises(MotorValidationError) as caught:
        replace(source_motor, **{f'{name}_curve':None})
    assert caught.value.error_code == f'{name.upper()}_CURVE_PROVENANCE_MISMATCH'
    with pytest.raises(MotorValidationError) as caught:
        replace(source_motor, provenance=replace(source_motor.provenance, **{f'{name}_curve_source':None}))
    assert caught.value.error_code == f'{name.upper()}_CURVE_PROVENANCE_MISMATCH'
    for text in ['', '   ']:
        with pytest.raises(MotorValidationError):
            replace(source_motor.provenance, **{f'{name}_curve_source':text})


def test_profiles_and_api():
    """MPROP-T24..29: İki bağımsız açık enum, mandatory profile, ikinci motor girdisi yok."""
    assert {m.name:m.value for m in MassModel} == {'EXPLICIT_CURVE':'explicit_curve','IMPULSE_PROPORTIONAL':'impulse_proportional'}
    assert {m.name:m.value for m in CgModel} == {'EXPLICIT_CURVE':'explicit_curve','FIXED_MIDPOINT':'fixed_midpoint'}
    assert DEMO == Profile(MassModel.IMPULSE_PROPORTIONAL,CgModel.FIXED_MIDPOINT)
    assert not signature(MotorPropertyEvaluator).parameters
    params = signature(MotorPropertyEvaluator().evaluate).parameters
    assert list(params) == ['installation','model_profile','motor_time_s']
    assert all(p.kind is Parameter.KEYWORD_ONLY and p.default is Parameter.empty for p in params.values())
    assert not hasattr(DEMO,'__dict__')
    with pytest.raises(FrozenInstanceError):
        DEMO.mass_model = MassModel.EXPLICIT_CURVE
    with pytest.raises(TypeError):
        Profile()


@pytest.mark.parametrize('kwargs', [{'mass_model':'impulse_proportional','cg_model':CgModel.FIXED_MIDPOINT},
                                  {'mass_model':MassModel.EXPLICIT_CURVE,'cg_model':None}])
def test_invalid_profile(kwargs):
    """Bilinmeyen yöntem otomatik fallback'e düşmez."""
    with pytest.raises(PropulsionEvaluationError):
        Profile(**kwargs)


def test_negative_time(installation):
    """MPROP-T30: Sonlu negatif zaman C.3A hata semantiğini korur."""
    with pytest.raises(PropulsionEvaluationError) as caught:
        evaluate(installation,DEMO,-1.)
    assert (caught.value.error_code,caught.value.field_name,caught.value.value) == ('NEGATIVE_MOTOR_TIME','motor_time_s',-1.)


@pytest.mark.parametrize('time',[float('nan'),float('inf'),-float('inf')])
def test_nonfinite_time(installation,time):
    """MPROP-T31: Ham non-finite zaman generic ValueError."""
    with pytest.raises(ValueError) as caught:
        evaluate(installation,DEMO,time)
    assert type(caught.value) is ValueError


@pytest.mark.parametrize('profile,code',[
    (Profile(MassModel.EXPLICIT_CURVE,CgModel.FIXED_MIDPOINT),'EXPLICIT_MASS_CURVE_REQUIRED'),
    (Profile(MassModel.IMPULSE_PROPORTIONAL,CgModel.EXPLICIT_CURVE),'EXPLICIT_CG_CURVE_REQUIRED')])
def test_missing_source(installation,profile,code):
    """MPROP-T32/T33: Eksik veride explicit model seçimi hata; fallback yok."""
    with pytest.raises(PropulsionEvaluationError) as caught:
        evaluate(installation,profile,0.)
    assert caught.value.error_code == code


@pytest.mark.parametrize('time,mass,cg,absolute',[(.25,.09,.045,.945),(.75,.07,.055,.955)])
def test_explicit_fixture(source_installation,time,mass,cg,absolute):
    """MPROP-T34/T37/T54..56: Bağımsız source doğrusal ara değerleri ve x_geo."""
    result = evaluate(source_installation,EXPLICIT,time)
    assert (result.mass_kg,result.cg_local_from_front_m,result.cg_x_geo_m) == pytest.approx((mass,cg,absolute),rel=3e-15,abs=0)


def test_exact_source_and_terminal(source_installation):
    """MPROP-T35/T36/T38/T39: Exact düğümler ve eğrinin kendi sonundan sonra hold."""
    motor = source_installation.motor
    for sample in motor.mass_curve:
        assert evaluate(source_installation,EXPLICIT,sample.time_s).mass_kg == sample.mass_kg
    for sample in motor.cg_curve:
        assert evaluate(source_installation,EXPLICIT,sample.time_s).cg_local_from_front_m == sample.cg_from_front_m
    result = evaluate(source_installation,EXPLICIT,100.)
    assert result.mass_kg == motor.mass_curve[-1].mass_kg
    assert result.cg_local_from_front_m == motor.cg_curve[-1].cg_from_front_m


def test_independent_grids_postburn(source_installation):
    """MPROP-T40/T41: Üç ayrı grid; mass/CG thrust sonundan sonra kendi verisini izler."""
    motor = replace(source_installation.motor,
        mass_curve=(MotorMassSample(0.,.1),MotorMassSample(.4,.08),MotorMassSample(2.,.04)),
        cg_curve=(MotorCgSample(0.,.02),MotorCgSample(.6,.08),MotorCgSample(3.,.02)))
    installation = replace(source_installation,motor=motor)
    at_one = evaluate(installation,EXPLICIT,1.)
    assert at_one.mass_kg == pytest.approx(.065,rel=3e-15)
    assert at_one.cg_local_from_front_m == pytest.approx(.07,rel=3e-15)
    assert at_one.mass_kg < evaluate(installation,EXPLICIT,.8).mass_kg
    assert evaluate(installation,EXPLICIT,2.5).mass_kg == .04
    assert evaluate(installation,EXPLICIT,2.5).cg_local_from_front_m > .02


@pytest.mark.parametrize('time,expected',[(0.,.0849),(.354,.0724014701890071),
    (1.4,.047016141471977205),(1.43,.047),(100.,.047)])
def test_f50_mass_and_cg(installation,time,expected):
    """MPROP-T43..48/T50..53: Gerçek F50/C.2/C.3A zincirinde mass ve sabit midpoint."""
    result = evaluate(installation,DEMO,time)
    assert result.mass_kg == pytest.approx(expected,rel=3e-15,abs=0)
    assert result.cg_local_from_front_m == .049 == F50.length_m/2
    assert result.cg_x_geo_m == pytest.approx(.956,rel=3e-15)
    if time == 0.:
        assert result.mass_kg == F50.initial_mass_kg
    if time >= 1.43:
        assert result.mass_kg == F50.initial_mass_kg-F50.propellant_mass_kg


def test_full_tail_depletion(installation):
    """MPROP-T42/T49: %5'ten sonra impuls ve kütle değişimi curve_end'e dek sürer."""
    stats = MotorCurveAnalyzer().analyze(motor=F50)
    times = [stats.effective_burn_end_5pct_s,1.4,stats.curve_end_time_s]
    masses = [evaluate(installation,DEMO,t).mass_kg for t in times]
    assert masses[0] > masses[1] > masses[2]
    for time,mass in zip(times,masses):
        impulse = MotorThrustCurveEvaluator().evaluate(motor=F50,motor_time_s=time).cumulative_impulse_N_s
        assert mass == F50.initial_mass_kg-F50.propellant_mass_kg*(impulse/stats.total_impulse_N_s)


@pytest.mark.parametrize('profile',[
    Profile(MassModel.EXPLICIT_CURVE,CgModel.FIXED_MIDPOINT),
    Profile(MassModel.IMPULSE_PROPORTIONAL,CgModel.EXPLICIT_CURVE),DEMO])
def test_independent_model_selection(source_installation,profile):
    """MPROP-T57..59/T66: Kaynak varken de politika üstün; auto-promotion yok."""
    result = evaluate(source_installation,profile,.25)
    motor = source_installation.motor
    if profile.mass_model is MassModel.EXPLICIT_CURVE:
        assert result.mass_kg == pytest.approx(.09,rel=3e-15)
    else:
        total = MotorCurveAnalyzer().analyze(motor=motor).total_impulse_N_s
        impulse = MotorThrustCurveEvaluator().evaluate(motor=motor,motor_time_s=.25).cumulative_impulse_N_s
        assert result.mass_kg == motor.initial_mass_kg-motor.propellant_mass_kg*impulse/total
        assert result.mass_kg != .09
    assert result.cg_local_from_front_m == pytest.approx(
        .045 if profile.cg_model is CgModel.EXPLICIT_CURVE else .05,rel=3e-15)


def test_immutability_determinism(installation,source_installation):
    """MPROP-T60..62/T65: Kaynak/profil değişmez; fallback noktaları yazılmaz."""
    for installed,profile in [(installation,DEMO),(source_installation,EXPLICIT)]:
        before = (repr(installed),repr(profile))
        evaluator = MotorPropertyEvaluator()
        first = evaluator.evaluate(installation=installed,model_profile=profile,motor_time_s=.25)
        for time in [10.,0.,.75,.25]:
            evaluator.evaluate(installation=installed,model_profile=profile,motor_time_s=time)
        assert evaluator.evaluate(installation=installed,model_profile=profile,motor_time_s=.25) == first
        assert first.model_profile is profile
        assert not hasattr(first,'__dict__')
        with pytest.raises(FrozenInstanceError):
            first.mass_kg = 0.
        assert (repr(installed),repr(profile)) == before
    assert F50.mass_curve is F50.cg_curve is None


def test_c3a_delegation(installation,monkeypatch):
    """MPROP-T42: Kabul edilmiş public C.3A sonuçları tüketilir; ikinci integral yok."""
    stats = replace(MotorCurveAnalyzer().analyze(motor=F50),total_impulse_N_s=8.)
    calls = []
    def runtime(self,*,motor,motor_time_s):
        calls.append((motor,motor_time_s))
        return MotorThrustState(1.,2.)
    monkeypatch.setattr(MotorThrustCurveEvaluator,'evaluate',runtime)
    monkeypatch.setattr(MotorCurveAnalyzer,'analyze',lambda self,*,motor:stats)
    assert evaluate(installation,DEMO,.5).mass_kg == .0849-.0379*.25
    assert calls == [(F50,.5)]


@pytest.mark.parametrize('impulse,total', [(-1.,1.),(2.,1.),(float('nan'),1.),
    (float('inf'),1.),(0.,0.),(0.,-1.),(0.,float('inf'))])
def test_bad_impulse_fraction(installation,monkeypatch,impulse,total):
    """MPROP-T63: Bozuk upstream impuls oranı clamp edilmez."""
    stats = replace(MotorCurveAnalyzer().analyze(motor=F50),total_impulse_N_s=total)
    monkeypatch.setattr(MotorThrustCurveEvaluator,'evaluate',lambda self,**kwargs:MotorThrustState(0.,impulse))
    monkeypatch.setattr(MotorCurveAnalyzer,'analyze',lambda self,**kwargs:stats)
    with pytest.raises(PropulsionEvaluationError) as caught:
        evaluate(installation,DEMO,0.)
    assert caught.value.error_code == 'INVALID_IMPULSE_FRACTION'


@pytest.mark.parametrize('field,value,code',[
    ('mass_kg',0.,'INVALID_MOTOR_MASS_RESULT'),('mass_kg',-1.,'INVALID_MOTOR_MASS_RESULT'),
    ('mass_kg',float('inf'),'INVALID_MOTOR_MASS_RESULT'),
    ('cg_from_front_m',-.01,'INVALID_MOTOR_CG_LOCAL_RESULT'),
    ('cg_from_front_m',.101,'INVALID_MOTOR_CG_LOCAL_RESULT'),
    ('cg_from_front_m',float('nan'),'INVALID_MOTOR_CG_LOCAL_RESULT')])
def test_bad_derived_source_result(source_installation,monkeypatch,field,value,code):
    """MPROP-T64: Beklenmeyen türetim arızası çıktıda onarılmaz."""
    original = properties._source_value
    monkeypatch.setattr(properties,'_source_value',
        lambda curve,time,field_name:value if field_name==field else original(curve,time,field_name))
    with pytest.raises(PropulsionEvaluationError) as caught:
        evaluate(source_installation,EXPLICIT,.25)
    assert caught.value.error_code == code


def test_bad_installed_cg(installation):
    """MPROP-T64: Bozuk installation x_geo sonucu açık hata."""
    with pytest.raises(PropulsionEvaluationError) as caught:
        evaluate(replace(installation,motor_front_x_geo_m=float('inf')),DEMO,0.)
    assert caught.value.error_code == 'INVALID_MOTOR_CG_X_GEO_RESULT'


def test_scope():
    """MPROP-T67/T68: Yalnız motor sonucu; toplam roket/olay/inertia/vector yok."""
    assert [f.name for f in fields(MotorMassProperties)] == [
        'mass_kg','cg_local_from_front_m','cg_x_geo_m','model_profile']
    assert [f.name for f in fields(Profile)] == ['mass_model','cg_model']
    assert set(properties.__all__) == {'MotorMassEvolutionModel','MotorCgEvolutionModel',
        'MotorPropertyModelProfile','DEMO_MOTOR_PROPERTY_MODEL_PROFILE',
        'MotorMassProperties','MotorPropertyEvaluator'}
