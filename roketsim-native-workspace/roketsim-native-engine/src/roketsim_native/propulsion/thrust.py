"""C.3A: kanonik eğri tek çalışma zamanı itki/impuls otoritesidir.

motor_time_s ateşlemeden beri geçen süredir; global zaman değildir. Parçalı
doğrusal eğri analitik trapezlerle integre edilir; timestep geçmişi tutulmaz.
Certification yalnız dış V&V referansıdır. %5 eşiği karakterizasyon içindir,
itki kesmesi değildir: eşik altındaki kuyruk tam curve_end'e kadar korunur.
İleride demo burnout otoritesi curve_end_time_s olacaktır; burada event yoktur.
Motor kütlesi/CG ve kaynak eğrileri C.3B, roket toplamları C.3C'ye ertelidir.
"""

from dataclasses import dataclass
from math import isfinite

from roketsim_native.math.numerical import require_finite
from roketsim_native.propulsion.models import MotorDefinition, ThrustSample

__all__ = ("PropulsionEvaluationError", "MotorThrustState", "MotorCurveStatistics",
           "MotorThrustCurveEvaluator", "MotorCurveAnalyzer")


class PropulsionEvaluationError(ValueError):
    """Sonlu semantik girdi veya beklenmeyen türetim hatasının yapılandırılmış bilgisi."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class MotorThrustState:
    """Yalnız anlık skaler itki ve ateşlemeden beri impuls; SI birimleri."""

    thrust_N: float
    cumulative_impulse_N_s: float


@dataclass(frozen=True, slots=True)
class MotorCurveStatistics:
    """Tam eğri istatistikleri; %5 alanları çalışma zamanı sınırı değildir."""

    total_impulse_N_s: float
    peak_thrust_N: float
    peak_thrust_time_s: float
    curve_end_time_s: float
    effective_burn_start_5pct_s: float
    effective_burn_end_5pct_s: float
    effective_burn_time_5pct_s: float


def _impulse_prefixes(curve: tuple[ThrustSample, ...]) -> tuple[float, ...]:
    """Tam aralık alanlarını tek yerden türet; yalnız çağrıya ait geçici veridir."""
    prefixes = [0.0]
    for left, right in zip(curve, curve[1:]):
        area = 0.5 * (left.thrust_N + right.thrust_N) * (right.time_s - left.time_s)
        total = prefixes[-1] + area
        if not isfinite(total) or area < 0.0:
            raise PropulsionEvaluationError(error_code="INVALID_TOTAL_IMPULSE",
                field_name="total_impulse_N_s", value=total)
        prefixes.append(total)
    if prefixes[-1] <= 0.0:
        raise PropulsionEvaluationError(error_code="INVALID_TOTAL_IMPULSE",
            field_name="total_impulse_N_s", value=prefixes[-1])
    return tuple(prefixes)


class MotorThrustCurveEvaluator:
    """Parametresiz, geçmişsiz eğri değerlendirici; örnek zamanları toleransla snap edilmez."""

    __slots__ = ()

    def evaluate(self, *, motor: MotorDefinition, motor_time_s: float) -> MotorThrustState:
        """Tam kanonik eğriden T(t), J(t) hesapla; eğri sonrasında T=0 ve J=J_total."""
        time = require_finite(motor_time_s, name="motor_time_s")
        if time < 0.0:
            raise PropulsionEvaluationError(error_code="NEGATIVE_MOTOR_TIME",
                field_name="motor_time_s", value=time)
        curve = motor.thrust_curve
        prefixes = _impulse_prefixes(curve)
        total = prefixes[-1]
        thrust, impulse = 0.0, total
        if time <= curve[-1].time_s:
            for index, sample in enumerate(curve):
                if time == sample.time_s:
                    thrust, impulse = sample.thrust_N, prefixes[index]
                    break
                if time < sample.time_s:
                    left = curve[index - 1]
                    elapsed = time - left.time_s
                    thrust = left.thrust_N + (sample.thrust_N - left.thrust_N) * (
                        elapsed / (sample.time_s - left.time_s))
                    impulse = prefixes[index - 1] + 0.5 * (left.thrust_N + thrust) * elapsed
                    break
        if not isfinite(thrust) or thrust < 0.0:
            raise PropulsionEvaluationError(error_code="INVALID_THRUST_RESULT",
                field_name="thrust_N", value=thrust)
        if not isfinite(impulse) or not 0.0 <= impulse <= total:
            raise PropulsionEvaluationError(error_code="INVALID_CUMULATIVE_IMPULSE",
                field_name="cumulative_impulse_N_s", value=impulse)
        return MotorThrustState(thrust, impulse)


class MotorCurveAnalyzer:
    """Kanonik eğriden tam impuls ve ilk giriş/son çıkış %5 karakterizasyonu."""

    __slots__ = ()

    def analyze(self, *, motor: MotorDefinition) -> MotorCurveStatistics:
        """Certification değerlerini kullanmadan eğri istatistiklerini türet."""
        curve = motor.thrust_curve
        total = _impulse_prefixes(curve)[-1]
        peak = max(curve, key=lambda sample: sample.thrust_N)
        end_time = curve[-1].time_s
        if not isfinite(peak.thrust_N) or peak.thrust_N <= 0.0:
            raise PropulsionEvaluationError(error_code="INVALID_PEAK_THRUST",
                field_name="peak_thrust_N", value=peak.thrust_N)
        if not isfinite(end_time) or end_time <= 0.0:
            raise PropulsionEvaluationError(error_code="INVALID_CURVE_END_TIME",
                field_name="curve_end_time_s", value=end_time)
        if not isfinite(peak.time_s) or not 0.0 <= peak.time_s <= end_time:
            raise PropulsionEvaluationError(error_code="INVALID_PEAK_TIME",
                field_name="peak_thrust_time_s", value=peak.time_s)
        threshold = 0.05 * peak.thrust_N
        if threshold <= 0.0:
            raise PropulsionEvaluationError(error_code="INVALID_EFFECTIVE_BURN_START",
                field_name="threshold_thrust_N", value=threshold)
        first = next(i for i, sample in enumerate(curve) if sample.thrust_N >= threshold)
        last = max(i for i, sample in enumerate(curve) if sample.thrust_N >= threshold)
        # Eşikle eşit örnekler doğrudan kullanılır; plateau/multiple crossing korunur.
        entry, exit = curve[first], curve[last]
        start = entry.time_s
        if entry.thrust_N != threshold:
            before = curve[first - 1]
            start = before.time_s + (threshold - before.thrust_N) / (
                entry.thrust_N - before.thrust_N) * (entry.time_s - before.time_s)
        end = exit.time_s
        if exit.thrust_N != threshold:
            after = curve[last + 1]
            end = exit.time_s + (threshold - exit.thrust_N) / (
                after.thrust_N - exit.thrust_N) * (after.time_s - exit.time_s)
        if not isfinite(start) or not 0.0 <= start < end_time:
            raise PropulsionEvaluationError(error_code="INVALID_EFFECTIVE_BURN_START",
                field_name="effective_burn_start_5pct_s", value=start)
        if not isfinite(end) or not start < end <= end_time:
            raise PropulsionEvaluationError(error_code="INVALID_EFFECTIVE_BURN_END",
                field_name="effective_burn_end_5pct_s", value=end)
        duration = end - start
        if not isfinite(duration) or duration <= 0.0:
            raise PropulsionEvaluationError(error_code="INVALID_EFFECTIVE_BURN_TIME",
                field_name="effective_burn_time_5pct_s", value=duration)
        return MotorCurveStatistics(total, peak.thrust_N, peak.time_s, end_time,
                                    start, end, duration)
