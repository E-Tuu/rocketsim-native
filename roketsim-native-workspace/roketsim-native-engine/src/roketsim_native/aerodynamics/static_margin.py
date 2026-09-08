"""NAT-012B Part 2: kabul edilmiş CP, CG ve Dmax'tan static margin.

CP Static Aerodynamics, CG Mass, maximum external airframe diameter Geometry
otoritesidir. Bu modül yalnız x_geo ayrımını caliber'a dönüştürür; CG, zaman,
kuvvet, moment veya tasarım güvenliği sınıflaması hesaplamaz.
"""

from dataclasses import dataclass
from math import isfinite

from roketsim_native.aerodynamics.drag import AerodynamicEvaluationError
from roketsim_native.aerodynamics.static_stability import StaticAerodynamicProperties
from roketsim_native.geometry.resolver import ResolvedRocketGeometry
from roketsim_native.mass.total import RocketMassProperties
from roketsim_native.math.numerical import require_finite

__all__ = ("StaticMarginResult", "StaticMarginCalculator")


@dataclass(frozen=True, slots=True)
class StaticMarginResult:
    """Yalnız signed CP-CG ayrımının Dmax ile normalize edilmiş sonucu."""

    static_margin_calibers: float


class StaticMarginCalculator:
    """Parametresiz/stateless CP-CG/Dmax aggregator; time veya model seçmez."""

    __slots__ = ()

    def evaluate(
        self,
        *,
        static_aerodynamics: StaticAerodynamicProperties,
        rocket_mass_properties: RocketMassProperties,
        geometry: ResolvedRocketGeometry,
    ) -> StaticMarginResult:
        """x_geo aft-positive işaretiyle (CP-CG)/Dmax hesapla; clamp uygulama."""
        cp = float(require_finite(static_aerodynamics.cp_x_geo_m, name="cp_x_geo_m"))
        cg = float(require_finite(
            rocket_mass_properties.total_cg_x_geo_m,
            name="total_cg_x_geo_m",
        ))
        maximum_diameter = float(require_finite(
            geometry.max_external_airframe_diameter_m,
            name="max_external_airframe_diameter_m",
        ))
        if maximum_diameter <= 0.0:
            raise AerodynamicEvaluationError(
                error_code="NON_POSITIVE_STATIC_MARGIN_DIAMETER",
                field_name="max_external_airframe_diameter_m",
                value=maximum_diameter,
            )
        margin = (cp - cg) / maximum_diameter
        if not isfinite(margin):
            raise AerodynamicEvaluationError(
                error_code="INVALID_STATIC_MARGIN",
                field_name="static_margin_calibers",
                value=margin,
            )
        return StaticMarginResult(margin)
