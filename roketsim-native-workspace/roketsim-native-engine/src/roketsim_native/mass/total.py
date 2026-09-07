"""C.3C: bir statik yapı ve bir hazır runtime motor katkısının toplamı.

StructuralMassProperties ve MotorMassProperties kendi katkılarının tek
otoritesidir; bileşen/model/geometri yeniden hesaplanmaz. Zaman girdisi yoktur.
x_geo burun ucunda sıfır, arkaya pozitiftir. Katkı CG aralığı kontrolü yalnız
ağırlıklı ortalama invariant'ıdır; Geometry extent kontrolü değildir.
Çoklu motor/staging geldiğinde tasarım yeniden ele alınacaktır; inertia/PAT yoktur.
"""

from dataclasses import dataclass
from math import isfinite

from roketsim_native.mass.models import StructuralMassProperties, MassValidationError
from roketsim_native.propulsion.properties import MotorMassProperties

__all__ = ("RocketMassProperties", "RocketMassPropertiesCalculator")


@dataclass(frozen=True, slots=True)
class RocketMassProperties:
    """Yalnız türetilmiş toplam kütle (kg) ve eksenel CG (m)."""

    total_mass_kg: float
    total_cg_x_geo_m: float


class RocketMassPropertiesCalculator:
    """Parametresiz/geçmişsiz iki katkı toplamı; motor fizik profilinden bağımsızdır."""

    __slots__ = ()

    def evaluate(self, *, structural_properties: StructuralMassProperties,
                 motor_properties: MotorMassProperties) -> RocketMassProperties:
        """Hazır kütle ve x_geo CG'leri tüket; ek kütle veya geometri politikası uygulama."""
        structure_mass = structural_properties.structure_mass_kg
        motor_mass = motor_properties.mass_kg
        structure_cg = structural_properties.structure_cg_x_geo_m
        motor_cg = motor_properties.cg_x_geo_m
        total_mass = structure_mass + motor_mass
        if not isfinite(total_mass):
            raise MassValidationError(error_code="INVALID_TOTAL_MASS",
                field_name="total_mass_kg", value=total_mass)
        if total_mass <= 0.0:
            raise MassValidationError(error_code="NON_POSITIVE_TOTAL_MASS",
                field_name="total_mass_kg", value=total_mass)
        # Eşit koordinat özdeşliği tam korunur; bu yön seçimi veya clamp değildir.
        total_cg = structure_cg if structure_cg == motor_cg else (
            structure_mass * structure_cg + motor_mass * motor_cg) / total_mass
        if not isfinite(total_cg):
            raise MassValidationError(error_code="INVALID_TOTAL_CG",
                field_name="total_cg_x_geo_m", value=total_cg)
        if not min(structure_cg, motor_cg) <= total_cg <= max(structure_cg, motor_cg):
            raise MassValidationError(error_code="TOTAL_CG_OUTSIDE_CONTRIBUTOR_SPAN",
                field_name="total_cg_x_geo_m", value=total_cg)
        return RocketMassProperties(total_mass, total_cg)
