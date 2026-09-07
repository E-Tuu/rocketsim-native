"""C.3B: kaynak veri, fizik politikası ve runtime motor sonucu ayrı otoritelerdir.

None doğrulanmış kaynak eğrisinin yokluğudur; otomatik model seçmez. Kullanıcı
profili her zaman açıktır. F50 demo profili impuls-orantılı mass ve sabit orta
nokta CG seçer; üretilen sonuçlar katalog eğrilerine yazılmaz. Kaynak eğrileri
kendi bağımsız grid'lerinde doğrusal değerlendirilir ve kendi son değerinde
tutulur. Toplam roket mass/CG, inertia ve event fiziği bu modüle ait değildir.
"""

from dataclasses import dataclass
from enum import Enum
from math import isfinite
from typing import Final

from roketsim_native.math.numerical import require_finite
from roketsim_native.propulsion.installation import MotorInstallation
from roketsim_native.propulsion.models import MotorMassSample, MotorCgSample
from roketsim_native.propulsion.thrust import (
    MotorThrustCurveEvaluator, MotorCurveAnalyzer, PropulsionEvaluationError,
)

__all__ = ("MotorMassEvolutionModel", "MotorCgEvolutionModel", "MotorPropertyModelProfile",
           "DEMO_MOTOR_PROPERTY_MODEL_PROFILE", "MotorMassProperties", "MotorPropertyEvaluator")


class MotorMassEvolutionModel(Enum):
    """Kütle yöntemi kaynak bulunabilirliğinden bağımsız açık seçimdir."""

    EXPLICIT_CURVE = "explicit_curve"
    IMPULSE_PROPORTIONAL = "impulse_proportional"


class MotorCgEvolutionModel(Enum):
    """CG yöntemi; orta nokta kaynak verisi değil model kararıdır."""

    EXPLICIT_CURVE = "explicit_curve"
    FIXED_MIDPOINT = "fixed_midpoint"


@dataclass(frozen=True, slots=True)
class MotorPropertyModelProfile:
    """Kaynak katalogdan ayrı ve varsayılanı olmayan fizik politikası."""

    mass_model: MotorMassEvolutionModel
    cg_model: MotorCgEvolutionModel

    def __post_init__(self) -> None:
        for name, value, kind in (("mass_model", self.mass_model, MotorMassEvolutionModel),
                                   ("cg_model", self.cg_model, MotorCgEvolutionModel)):
            if not isinstance(value, kind):
                raise PropulsionEvaluationError(error_code="INVALID_MOTOR_PROPERTY_MODEL",
                    field_name=name, value=value)


DEMO_MOTOR_PROPERTY_MODEL_PROFILE: Final[MotorPropertyModelProfile] = MotorPropertyModelProfile(
    mass_model=MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,
    cg_model=MotorCgEvolutionModel.FIXED_MIDPOINT,
)


@dataclass(frozen=True, slots=True)
class MotorMassProperties:
    """Yalnız motor kütlesi/CG ve exact seçilmiş profil; structural toplam değildir."""

    mass_kg: float
    cg_local_from_front_m: float
    cg_x_geo_m: float
    model_profile: MotorPropertyModelProfile


def _source_value(curve: tuple[MotorMassSample, ...] | tuple[MotorCgSample, ...],
                  time: float, field_name: str) -> float:
    """Kaynağın kendi grid'inde exact örnek/doğrusal ara değer/terminal hold."""
    for index, sample in enumerate(curve):
        value = getattr(sample, field_name)
        if time == sample.time_s:
            return value
        if time < sample.time_s:
            before = curve[index - 1]
            previous = getattr(before, field_name)
            return previous + (value - previous) * (
                (time - before.time_s) / (sample.time_s - before.time_s))
    return getattr(curve[-1], field_name)


class MotorPropertyEvaluator:
    """Geçmişsiz değerlendirici; motor yalnız kurulum nesnesinden alınır."""

    __slots__ = ()

    def evaluate(self, *, installation: MotorInstallation,
                 model_profile: MotorPropertyModelProfile,
                 motor_time_s: float) -> MotorMassProperties:
        """Ateşleme süresinde seçilmiş yöntemleri uygula; kaynak yoksa fallback yapma."""
        time = require_finite(motor_time_s, name="motor_time_s")
        if time < 0.0:
            raise PropulsionEvaluationError(error_code="NEGATIVE_MOTOR_TIME",
                field_name="motor_time_s", value=time)
        if not isinstance(model_profile, MotorPropertyModelProfile):
            raise PropulsionEvaluationError(error_code="INVALID_MOTOR_PROPERTY_MODEL",
                field_name="model_profile", value=model_profile)
        motor = installation.motor
        if model_profile.mass_model is MotorMassEvolutionModel.EXPLICIT_CURVE:
            if motor.mass_curve is None:
                raise PropulsionEvaluationError(error_code="EXPLICIT_MASS_CURVE_REQUIRED",
                    field_name="mass_curve", value=None)
            mass = _source_value(motor.mass_curve, time, "mass_kg")
        else:
            # C.3A tam eğri impulsu kullanılır; %5 etkin süre depletion sınırı değildir.
            impulse = MotorThrustCurveEvaluator().evaluate(motor=motor, motor_time_s=time).cumulative_impulse_N_s
            total = MotorCurveAnalyzer().analyze(motor=motor).total_impulse_N_s
            if not isfinite(total) or total <= 0.0:
                raise PropulsionEvaluationError(error_code="INVALID_IMPULSE_FRACTION",
                    field_name="total_impulse_N_s", value=total)
            fraction = impulse / total
            if not isfinite(fraction) or not 0.0 <= fraction <= 1.0:
                raise PropulsionEvaluationError(error_code="INVALID_IMPULSE_FRACTION",
                    field_name="impulse_fraction", value=fraction)
            mass = motor.initial_mass_kg - motor.propellant_mass_kg * fraction
        if not isfinite(mass) or mass <= 0.0:
            raise PropulsionEvaluationError(error_code="INVALID_MOTOR_MASS_RESULT",
                field_name="mass_kg", value=mass)
        if model_profile.cg_model is MotorCgEvolutionModel.EXPLICIT_CURVE:
            if motor.cg_curve is None:
                raise PropulsionEvaluationError(error_code="EXPLICIT_CG_CURVE_REQUIRED",
                    field_name="cg_curve", value=None)
            local_cg = _source_value(motor.cg_curve, time, "cg_from_front_m")
        else:
            local_cg = motor.length_m / 2.0
        if not isfinite(local_cg) or not 0.0 <= local_cg <= motor.length_m:
            raise PropulsionEvaluationError(error_code="INVALID_MOTOR_CG_LOCAL_RESULT",
                field_name="cg_local_from_front_m", value=local_cg)
        cg = installation.motor_front_x_geo_m + local_cg
        if not isfinite(cg):
            raise PropulsionEvaluationError(error_code="INVALID_MOTOR_CG_X_GEO_RESULT",
                field_name="cg_x_geo_m", value=cg)
        return MotorMassProperties(mass, local_cg, cg, model_profile)
