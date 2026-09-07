"""THRUST-T01..45: tam eğri fiziği ile %5 karakterizasyonunun ayrı kanıtları."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature
from math import nextafter

import pytest

from roketsim_native.propulsion import thrust
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T as F50
from roketsim_native.propulsion.models import ThrustSample, MotorCertificationReference
from roketsim_native.propulsion.thrust import (
    MotorThrustState, MotorCurveStatistics, PropulsionEvaluationError,
    MotorThrustCurveEvaluator, MotorCurveAnalyzer,
)


def motor_with_curve(points):
    """Yalnız test tarafında geçerli sentetik eğri; katalog değişmez."""
    return replace(F50, thrust_curve=tuple(ThrustSample(t, force) for t, force in points))


@pytest.fixture
def triangle():
    return motor_with_curve([(0., 0.), (1., 20.), (3., 0.)])


def evaluate(motor, time):
    return MotorThrustCurveEvaluator().evaluate(motor=motor, motor_time_s=time)


def analyze(motor):
    return MotorCurveAnalyzer().analyze(motor=motor)


@pytest.mark.parametrize('result', [evaluate(F50, 0.), analyze(F50)])
def test_immutable_results(result):
    """THRUST-T01/T02: Her iki sonuç frozen/slotted."""
    assert not hasattr(result, '__dict__')
    with pytest.raises(FrozenInstanceError):
        setattr(result, fields(result)[0].name, -1.)


def test_signatures():
    """THRUST-T03..05: Parametresiz ve keyword-only public API."""
    for cls, method, names in [(MotorThrustCurveEvaluator, 'evaluate', ['motor', 'motor_time_s']),
                               (MotorCurveAnalyzer, 'analyze', ['motor'])]:
        assert not signature(cls).parameters
        instance = cls()
        params = signature(getattr(instance, method)).parameters
        assert list(params) == names
        assert all(p.kind is Parameter.KEYWORD_ONLY and p.default is Parameter.empty
                   for p in params.values())
        assert not hasattr(instance, '__dict__')


@pytest.mark.parametrize('time', [-1., -1e-100])
def test_negative_time(time):
    """THRUST-T06/T44: Sonlu negatif ateşleme zamanı structured hata."""
    with pytest.raises(PropulsionEvaluationError) as caught:
        evaluate(F50, time)
    assert caught.value.error_code == 'NEGATIVE_MOTOR_TIME'
    assert caught.value.field_name == 'motor_time_s'
    assert caught.value.value == time


@pytest.mark.parametrize('time', [float('nan'), float('inf'), -float('inf')])
def test_nonfinite_time(time):
    """THRUST-T07: Ham non-finite zaman generic ValueError yoludur."""
    with pytest.raises(ValueError) as caught:
        evaluate(F50, time)
    assert type(caught.value) is ValueError


def test_origin(triangle):
    """THRUST-T08/T09: İtki ve impuls başlangıçta tam sıfır."""
    assert evaluate(triangle, 0.) == MotorThrustState(0., 0.)


@pytest.mark.parametrize('sample', F50.thrust_curve)
def test_exact_samples(sample):
    """THRUST-T10/T43: Her kaynak örnek değeri birebir korunur."""
    assert evaluate(F50, sample.time_s).thrust_N == sample.thrust_N


@pytest.mark.parametrize('time,force,impulse', [(.5, 10., 2.5), (1., 20., 10.), (2., 10., 25.)])
def test_analytic_intervals(triangle, time, force, impulse):
    """THRUST-T11/T14/T15: Yükselen/düşen parçaların analitik trapezleri."""
    assert evaluate(triangle, time) == MotorThrustState(force, impulse)


def test_no_time_snap(triangle):
    """THRUST-T10/T11: Yakın zamanlar örneğe toleransla yapıştırılmaz."""
    time = nextafter(1., 0.)
    assert evaluate(triangle, time).thrust_N == 20. * time
    assert evaluate(triangle, time).thrust_N < 20.


@pytest.mark.parametrize('time', [3., 4., 1e100])
def test_end_and_post_curve(triangle, time):
    """THRUST-T12/T13/T17/T18: Tam eğri sonunda ve sonrasında sabit total impuls."""
    assert evaluate(triangle, time) == MotorThrustState(0., 30.)
    assert evaluate(triangle, time).cumulative_impulse_N_s == analyze(triangle).total_impulse_N_s


def test_monotonic_impulse():
    """THRUST-T16: Kanonik düğümler ve sık zaman örneklerinde J azalmıyor."""
    times = sorted({i / 1000 for i in range(1601)} | {s.time_s for s in F50.thrust_curve})
    impulses = [evaluate(F50, t).cumulative_impulse_N_s for t in times]
    assert all(a <= b for a, b in zip(impulses, impulses[1:]))


def test_history_and_determinism():
    """THRUST-T19/T20/T43: Çağrı sırası/geçmiş yok; motor girdisi değişmez."""
    evaluator = MotorThrustCurveEvaluator()
    before = repr(F50)
    expected = evaluate(F50, 1.4)
    for time in [2., .1, 0., 1.4, .354, 1.4]:
        assert evaluator.evaluate(motor=F50, motor_time_s=time) == evaluate(F50, time)
    assert evaluator.evaluate(motor=F50, motor_time_s=1.4) == expected
    assert analyze(F50) == analyze(F50)
    assert repr(F50) == before


def test_analytic_statistics(triangle):
    """THRUST-T21..28: J=30, peak=20, eşik=1; giriş=.05, çıkış=2.9."""
    result = analyze(triangle)
    assert result.total_impulse_N_s == 30.
    assert result.peak_thrust_N == 20.
    assert result.peak_thrust_time_s == 1.
    assert result.curve_end_time_s == 3.
    assert result.effective_burn_start_5pct_s == .05
    assert result.effective_burn_end_5pct_s == 2.9
    assert result.effective_burn_time_5pct_s == 2.9 - .05


def test_multiple_crossings_and_earliest_peak():
    """THRUST-T23/T29: Ayrık bölgeler toplanmaz; ilk giriş ve son çıkış seçilir."""
    motor = motor_with_curve([(0.,0.), (1.,20.), (2.,0.), (3.,20.), (4.,0.)])
    result = analyze(motor)
    assert result.peak_thrust_time_s == 1.
    assert result.effective_burn_start_5pct_s == .05
    assert result.effective_burn_end_5pct_s == 3.95
    assert result.effective_burn_time_5pct_s == 3.95 - .05


def test_threshold_samples_and_plateaus():
    """THRUST-T30: Eşik örneği/plateau ve ayrı eşik teması tam zamanda korunur."""
    motor = motor_with_curve([(0.,0.), (1.,1.), (2.,1.), (3.,20.),
                             (4.,0.), (5.,1.), (6.,1.), (7.,0.)])
    result = analyze(motor)
    assert result.effective_burn_start_5pct_s == 1.
    assert result.effective_burn_end_5pct_s == 6.
    assert result.effective_burn_time_5pct_s == 5.


@pytest.mark.parametrize('name,expected', [
    pytest.param('total_impulse_N_s', 76.828387, id='THRUST-T33'),
    pytest.param('effective_burn_start_5pct_s', .0009294820639585808, id='THRUST-T37'),
    pytest.param('effective_burn_end_5pct_s', 1.3752712686567163, id='THRUST-T38'),
    pytest.param('effective_burn_time_5pct_s', 1.3743417865927579, id='THRUST-T39'),
])
def test_f50_numeric_statistics(name, expected):
    assert getattr(analyze(F50), name) == pytest.approx(expected, rel=3e-15, abs=0)


def test_f50_exact_statistics():
    """THRUST-T34..36: Kaynak peak/time/end birebir korunur."""
    result = analyze(F50)
    assert result.peak_thrust_N == 79.590
    assert result.peak_thrust_time_s == .354
    assert result.curve_end_time_s == 1.430


def test_f50_tail_is_not_cutoff():
    """THRUST-T31/T32/T40/T41: %5 sonrası kuyruk hem T hem J için aktiftir."""
    stats = analyze(F50)
    state = evaluate(F50, 1.4)
    assert stats.effective_burn_end_5pct_s < 1.4 < stats.curve_end_time_s
    assert 0 < state.thrust_N < .05 * stats.peak_thrust_N
    assert state.thrust_N == pytest.approx(2.181395348837211, rel=4e-15, abs=0)
    assert state.cumulative_impulse_N_s == pytest.approx(76.79566606976745, rel=3e-15, abs=0)
    assert evaluate(F50, stats.effective_burn_end_5pct_s).cumulative_impulse_N_s < state.cumulative_impulse_N_s
    assert state.cumulative_impulse_N_s < evaluate(F50, stats.curve_end_time_s).cumulative_impulse_N_s


def test_certification_is_not_authority():
    """THRUST-T42: Dış özet kasıtlı farklıyken fizik yalnız eğriyi izler."""
    motor = replace(F50, certification=MotorCertificationReference(1000., 100., 200., 10.))
    assert analyze(motor) == analyze(F50)
    for time in [0., .2, 1.4, 1.43, 2.]:
        assert evaluate(motor, time) == evaluate(F50, time)
    assert analyze(F50).total_impulse_N_s != F50.certification.measured_total_impulse_N_s
    assert analyze(F50).total_impulse_N_s == pytest.approx(76.83, rel=0, abs=.005)


@pytest.mark.parametrize('points', [
    [(0.,0.), (1e308,20.), (1.1e308,0.)],
    [(0.,0.), (1e-200,1e-200), (2e-200,0.)],
])
def test_invalid_derived_impulse(points):
    """THRUST-T44: Geçerli sonlu eğride overflow/underflow açık türetim hatası."""
    motor = motor_with_curve(points)
    for operation in [lambda: analyze(motor), lambda: evaluate(motor, 0.)]:
        with pytest.raises(PropulsionEvaluationError) as caught:
            operation()
        assert caught.value.error_code == 'INVALID_TOTAL_IMPULSE'
        assert caught.value.field_name == 'total_impulse_N_s'


def test_scope():
    """THRUST-T45: Public sonuç/API yalnız skaler eğri fiziği ve karakterizasyon."""
    assert [f.name for f in fields(MotorThrustState)] == ['thrust_N', 'cumulative_impulse_N_s']
    assert [f.name for f in fields(MotorCurveStatistics)] == [
        'total_impulse_N_s', 'peak_thrust_N', 'peak_thrust_time_s', 'curve_end_time_s',
        'effective_burn_start_5pct_s', 'effective_burn_end_5pct_s', 'effective_burn_time_5pct_s']
    assert set(thrust.__all__) == {'PropulsionEvaluationError', 'MotorThrustState',
        'MotorCurveStatistics', 'MotorThrustCurveEvaluator', 'MotorCurveAnalyzer'}
