"""MOTOR-T01..T40: immutable veri contract ve offline F50 kaynak V&V."""

from dataclasses import FrozenInstanceError, fields, replace

import pytest

from roketsim_native.propulsion import models, catalog
from roketsim_native.propulsion.models import (
    MotorType, MotorValidationError, ThrustSample, MotorCertificationReference,
    MotorDataProvenance, MotorDefinition,
)
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T as MOTOR, DEMO_MOTOR_CATALOG, MotorCatalog


# NAR F50.pdf sayfa 2 / frozen user fixture; production'dan beklenen veri üretilmez.
SOURCE_POINTS = (
    (.012, 51.377), (.023, 61.197), (.026, 66.117), (.044, 66.564),
    (.082, 69.685), (.152, 73.264), (.208, 75.053), (.237, 77.279),
    (.254, 76.832), (.272, 77.726), (.307, 77.726), (.330, 76.832),
    (.336, 78.621), (.342, 76.832), (.354, 79.590), (.363, 76.385),
    (.371, 77.756), (.395, 76.385), (.447, 75.937), (.523, 73.711),
    (.652, 68.344), (.810, 60.302), (.828, 62.539), (.836, 58.076),
    (.901, 53.603), (1.079, 37.074), (1.158, 29.480), (1.196, 25.464),
    (1.246, 16.976), (1.301, 9.380), (1.430, 0.),
)


def test_motor_type():
    """MOTOR-T01: Enum yalnız fiziksel single-use mimarisini tanımlar."""
    assert list(MotorType) == [MotorType.SINGLE_USE]
    assert MotorType.SINGLE_USE.value == 'single_use'


@pytest.mark.parametrize('obj', [
    pytest.param(ThrustSample(0, 0), id='MOTOR-T02'),
    pytest.param(MOTOR, id='MOTOR-T06'),
    pytest.param(MOTOR.certification, id='MOTOR-T18'),
    pytest.param(MOTOR.provenance, id='MOTOR-T19'),
    pytest.param(DEMO_MOTOR_CATALOG, id='MOTOR-T36'),
])
def test_immutable_slotted(obj):
    """Frozen/slotted nesneler ve nested tuple verileri public mutation'a kapalıdır."""
    assert not hasattr(obj, '__dict__')
    with pytest.raises(FrozenInstanceError):
        setattr(obj, fields(obj)[0].name, None)


@pytest.mark.parametrize('name,code', [('time_s', 'NEGATIVE_THRUST_TIME'), ('thrust_N', 'NEGATIVE_THRUST')])
def test_negative_sample(name, code):
    """MOTOR-T03/T04/T38: Finite negatif sample structured error üretir."""
    with pytest.raises(MotorValidationError) as caught:
        ThrustSample(**{'time_s': 0., 'thrust_N': 0., name: -1.})
    assert (caught.value.error_code, caught.value.field_name, caught.value.value) == (code, name, -1.)


@pytest.mark.parametrize('name', ['time_s', 'thrust_N'])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite_sample(name, value):
    """MOTOR-T05: Non-finite sample generic ValueError'dır."""
    with pytest.raises(ValueError) as caught:
        ThrustSample(**{'time_s': 0., 'thrust_N': 0., name: value})
    assert type(caught.value) is ValueError


@pytest.mark.parametrize('name', ['motor_id', 'manufacturer', 'designation', 'family', 'propellant_name'])
@pytest.mark.parametrize('value', ['', ' \t'])
def test_identity_text(name, value):
    """MOTOR-T07: Required identity/text boş olamaz; string düzeltmesi yapılmaz."""
    with pytest.raises(MotorValidationError) as caught:
        replace(MOTOR, **{name: value})
    assert caught.value.field_name == name


@pytest.mark.parametrize('name', ['diameter_m', 'length_m', 'initial_mass_kg', 'propellant_mass_kg'])
@pytest.mark.parametrize('value', [0., -1.])
def test_positive_domains(name, value):
    """MOTOR-T08/T09/T10: Boyut ve mass pozitif olmalı."""
    with pytest.raises(MotorValidationError) as caught:
        replace(MOTOR, **{name: value})
    assert caught.value.field_name == name


@pytest.mark.parametrize('propellant', [.0849, .1])
def test_mass_relation(propellant):
    """MOTOR-T11: Propellant initial mass'ten strictly küçük olmalı."""
    with pytest.raises(MotorValidationError, match='PROPELLANT_MASS_NOT_LESS'):
        replace(MOTOR, propellant_mass_kg=propellant)


def test_delay():
    """MOTOR-T12: Zero delay verisi geçerli; negatif delay reddedilir."""
    assert replace(MOTOR, ejection_delay_s=0.).ejection_delay_s == 0.
    with pytest.raises(MotorValidationError, match='NEGATIVE_EJECTION_DELAY'):
        replace(MOTOR, ejection_delay_s=-1.)


@pytest.mark.parametrize('points,code', [
    pytest.param([], 'TOO_FEW_THRUST_SAMPLES', id='MOTOR-T13-empty'),
    pytest.param([(0,0), (1,0)], 'TOO_FEW_THRUST_SAMPLES', id='MOTOR-T13-short'),
    pytest.param([(0,1), (1,2), (2,0)], 'INVALID_THRUST_CURVE_START', id='MOTOR-T14-thrust'),
    pytest.param([(.1,0), (1,2), (2,0)], 'INVALID_THRUST_CURVE_START', id='MOTOR-T14-time'),
    pytest.param([(0,0), (1,2), (1,0)], 'NON_INCREASING_THRUST_TIME', id='MOTOR-T15-duplicate'),
    pytest.param([(0,0), (2,2), (1,0)], 'NON_INCREASING_THRUST_TIME', id='MOTOR-T15-order'),
    pytest.param([(0,0), (1,0), (2,0)], 'NO_POSITIVE_THRUST', id='MOTOR-T16'),
    pytest.param([(0,0), (1,2), (2,1)], 'INVALID_THRUST_CURVE_END', id='MOTOR-T17'),
])
def test_curve_validation(points, code):
    """Canonical olmayan curve düzeltilmez; exact hata yolu korunur."""
    curve = tuple(ThrustSample(t, f) for t, f in points)
    with pytest.raises(MotorValidationError) as caught:
        replace(MOTOR, thrust_curve=curve)
    assert caught.value.error_code == code


def test_tuple_requirement():
    """MOTOR-T13: Mutable list reddedilir; canonical minimum üç nokta kabul edilir."""
    with pytest.raises(MotorValidationError, match='INVALID_THRUST_CURVE_TYPE'):
        replace(MOTOR, thrust_curve=list(MOTOR.thrust_curve))
    curve = (ThrustSample(0,0), ThrustSample(1,1), ThrustSample(2,0))
    assert replace(MOTOR, thrust_curve=curve).thrust_curve is curve


@pytest.mark.parametrize('name', [f.name for f in fields(MotorCertificationReference)])
@pytest.mark.parametrize('value', [0., -1.])
def test_certification_positive(name, value):
    """MOTOR-T18: Certification ölçümleri pozitif olmalıdır."""
    with pytest.raises(MotorValidationError, match='INVALID_CERTIFICATION_REFERENCE'):
        replace(MOTOR.certification, **{name: value})


@pytest.mark.parametrize('name', [f.name for f in fields(MotorDataProvenance)])
@pytest.mark.parametrize('value', ['', ' \n'])
def test_provenance_required(name, value):
    """MOTOR-T19: Tüm provenance string'leri açıklayıcı ve non-blank olmalıdır."""
    with pytest.raises(MotorValidationError, match='INVALID_PROVENANCE'):
        replace(MOTOR.provenance, **{name: value})


def test_frozen_catalog_identity():
    """MOTOR-T20..23/T39: Exact Native verileri manufacturer rounding ile değiştirilmez."""
    assert (MOTOR.motor_id, MOTOR.manufacturer, MOTOR.designation, MOTOR.family,
            MOTOR.motor_type, MOTOR.propellant_name) == (
        'aerotech_f50_4t', 'AeroTech', 'F50-4T', 'F50T', MotorType.SINGLE_USE, 'Blue Thunder')
    assert (MOTOR.diameter_m, MOTOR.length_m) == (.029, .098)
    assert (MOTOR.initial_mass_kg, MOTOR.propellant_mass_kg) == (.0849, .0379)
    assert MOTOR.initial_mass_kg != .085 and MOTOR.propellant_mass_kg != .038
    assert MOTOR.ejection_delay_s == 4.


def test_source_points():
    """MOTOR-T24..28: Tek Native boundary ve değişmemiş 31 published sample."""
    assert type(MOTOR.thrust_curve) is tuple
    assert len(MOTOR.thrust_curve) == 32
    assert MOTOR.thrust_curve[0] == ThrustSample(0., 0.)
    assert tuple((s.time_s, s.thrust_N) for s in MOTOR.thrust_curve[1:]) == SOURCE_POINTS
    assert max(MOTOR.thrust_curve, key=lambda s: s.thrust_N) == ThrustSample(.354, 79.590)
    assert MOTOR.thrust_curve[-1] == ThrustSample(1.430, 0.)
    assert 'prepends (0.0 s, 0.0 N)' in MOTOR.provenance.normalization_note


def test_impulse_reference():
    """MOTOR-T29..31: Trapezoidal integration yalnız test-side V&V'dir."""
    impulse = sum((b.time_s-a.time_s)*(a.thrust_N+b.thrust_N)/2
                  for a,b in zip(MOTOR.thrust_curve, MOTOR.thrust_curve[1:]))
    assert impulse == pytest.approx(76.828387, rel=0, abs=1e-12)
    assert impulse == pytest.approx(MOTOR.certification.measured_total_impulse_N_s, rel=0, abs=.005)
    assert MOTOR.certification == MotorCertificationReference(76.83, 53.73, 79.59, 1.43)


def test_catalog_lookup():
    """MOTOR-T32/T33/T34/T36: Tek verified motor, deterministic ID lookup, fallback yok."""
    assert DEMO_MOTOR_CATALOG.motors == (MOTOR,)
    for _ in range(5):
        assert DEMO_MOTOR_CATALOG.get('aerotech_f50_4t') is MOTOR
    for unknown in ('unknown', 'F50-4T', ''):
        with pytest.raises(KeyError) as caught:
            DEMO_MOTOR_CATALOG.get(unknown)
        assert caught.value.args == (unknown,)


def test_catalog_duplicates_and_tuple():
    """MOTOR-T35/T36: Duplicate stable ID ve mutable catalog reddedilir."""
    with pytest.raises(ValueError, match='duplicate'):
        MotorCatalog((MOTOR, replace(MOTOR, designation='Other display')))
    with pytest.raises(ValueError):
        MotorCatalog([MOTOR])
    second = replace(MOTOR, motor_id='test_verified_id')
    assert MotorCatalog((MOTOR, second)).get('test_verified_id') is second


@pytest.mark.parametrize('name', ['diameter_m', 'length_m', 'initial_mass_kg', 'propellant_mass_kg', 'ejection_delay_s'])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_motor_nonfinite(name, value):
    """MOTOR-T37: Non-finite MotorDefinition input generic ValueError."""
    with pytest.raises(ValueError) as caught:
        replace(MOTOR, **{name: value})
    assert type(caught.value) is ValueError


@pytest.mark.parametrize('name', [f.name for f in fields(MotorCertificationReference)])
@pytest.mark.parametrize('value', [float('nan'), float('inf'), -float('inf')])
def test_certification_nonfinite(name, value):
    """MOTOR-T37: Non-finite certification input generic ValueError."""
    with pytest.raises(ValueError) as caught:
        replace(MOTOR.certification, **{name: value})
    assert type(caught.value) is ValueError


@pytest.mark.parametrize('name,value', [('motor_type', 'single_use'), ('certification', None),
                                      ('provenance', None), ('thrust_curve', ((0,0), (1,1), (2,0)))])
def test_nested_contract_types(name, value):
    """Nested immutable veri sözleşmeleri yerine mutable/ham değerler kabul edilmez."""
    with pytest.raises(MotorValidationError):
        replace(MOTOR, **{name: value})


def test_scope_contract():
    """MOTOR-T40: C.1 public yüzeyi yalnız data definitions/catalog; physics evaluator yok."""
    assert set(models.__all__) == {'MotorType', 'MotorValidationError', 'ThrustSample',
        'MotorCertificationReference', 'MotorDataProvenance', 'MotorDefinition'}
    assert set(catalog.__all__) == {'MotorCatalog', 'AEROTECH_F50_4T', 'DEMO_MOTOR_CATALOG'}
    assert [f.name for f in fields(MotorDefinition)] == ['motor_id', 'manufacturer', 'designation',
        'family', 'motor_type', 'propellant_name', 'diameter_m', 'length_m', 'initial_mass_kg',
        'propellant_mass_kg', 'ejection_delay_s', 'thrust_curve', 'certification', 'provenance']
    assert not [n for n in dir(MOTOR) if not n.startswith('_') and callable(getattr(MOTOR, n))]
