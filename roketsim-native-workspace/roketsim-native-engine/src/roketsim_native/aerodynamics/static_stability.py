"""NAT-012B Part 1: alpha -> 0 Extended-Barrowman static CNa ve CP.

Geometry tüm şekil/yerleşim tanımlarını, FlightConditions Mach'ı sahiplenir.
Bu modül gerçek AoA, kuvvet, moment, CG veya static margin tüketmez. V1 global
domain'i transonik başlangıçtan önce 0 <= M < 0.8 ile sınırlıdır.
"""

from dataclasses import dataclass
from enum import Enum
from math import cos, isfinite, pi, sqrt

from roketsim_native.aerodynamics.drag import AerodynamicEvaluationError
from roketsim_native.flight_conditions.basic import BasicFlightConditions
from roketsim_native.geometry.models import FinAngularArrangement
from roketsim_native.geometry.resolver import ResolvedRocketGeometry
from roketsim_native.math.numerical import require_finite

__all__ = (
    "StaticNormalForceModel",
    "FinCenterOfPressureModel",
    "BodyLiftTreatment",
    "StaticStabilityModelProfile",
    "NATIVE_STATIC_STABILITY_V1_PROFILE",
    "StaticAeroContribution",
    "StaticAerodynamicProperties",
    "StaticAerodynamicEvaluator",
)


class StaticNormalForceModel(str, Enum):
    EXTENDED_BARROWMAN_SUBSONIC = "extended_barrowman_subsonic"


class FinCenterOfPressureModel(str, Enum):
    EXTENDED_BARROWMAN_MACH_DEPENDENT = "extended_barrowman_mach_dependent"


class BodyLiftTreatment(str, Enum):
    LINEARIZED_ZERO_AOA = "linearized_zero_aoa"


@dataclass(frozen=True, slots=True)
class StaticStabilityModelProfile:
    """V1 correlation seçimi ve exclusive global Mach sınırı; gamma içermez."""

    normal_force_model: StaticNormalForceModel
    fin_center_of_pressure_model: FinCenterOfPressureModel
    body_lift_treatment: BodyLiftTreatment
    maximum_supported_mach_exclusive: float

    def __post_init__(self) -> None:
        maximum = require_finite(
            self.maximum_supported_mach_exclusive,
            name="maximum_supported_mach_exclusive",
        )
        if maximum <= 0.0:
            raise AerodynamicEvaluationError(
                error_code="NON_POSITIVE_STATIC_STABILITY_MACH_LIMIT",
                field_name="maximum_supported_mach_exclusive",
                value=maximum,
            )


NATIVE_STATIC_STABILITY_V1_PROFILE = StaticStabilityModelProfile(
    normal_force_model=StaticNormalForceModel.EXTENDED_BARROWMAN_SUBSONIC,
    fin_center_of_pressure_model=(
        FinCenterOfPressureModel.EXTENDED_BARROWMAN_MACH_DEPENDENT
    ),
    body_lift_treatment=BodyLiftTreatment.LINEARIZED_ZERO_AOA,
    maximum_supported_mach_exclusive=0.8,
)


@dataclass(frozen=True, slots=True)
class StaticAeroContribution:
    """Tek nonzero linear static katkının CNa/rad ve x_geo CP sonucu."""

    cna_per_rad: float
    cp_x_geo_m: float


@dataclass(frozen=True, slots=True)
class StaticAerodynamicProperties:
    """Nose ve tek fin-set katkısı ile CNa-ağırlıklı toplam CP."""

    nose: StaticAeroContribution
    fin_set: StaticAeroContribution
    total_cna_per_rad: float
    cp_x_geo_m: float
    model_profile: StaticStabilityModelProfile


class StaticAerodynamicEvaluator:
    """Parametresiz/stateless linearized static-stability evaluator."""

    __slots__ = ()

    def evaluate(
        self,
        *,
        geometry: ResolvedRocketGeometry,
        flight_conditions: BasicFlightConditions,
        model_profile: StaticStabilityModelProfile,
    ) -> StaticAerodynamicProperties:
        """Accepted Geometry ve Mach authority'sini tüket; AoA/CG üretme."""
        self._require_supported_profile(model_profile)
        mach = float(require_finite(flight_conditions.mach, name="mach"))
        if mach < 0.0:
            self._fail("NEGATIVE_STATIC_STABILITY_MACH", "mach", mach)
        if mach >= model_profile.maximum_supported_mach_exclusive:
            self._fail(
                "UNSUPPORTED_STATIC_STABILITY_MACH_REGIME", "mach", mach
            )

        reference_area = self._positive_raw(
            geometry.reference_area_m2, "reference_area_m2"
        )
        nose_frontal_area = self._positive_raw(
            geometry.nose_frontal_area_m2, "nose_frontal_area_m2"
        )
        nose_length = self._positive_raw(
            geometry.nose_axial_length_m, "nose_axial_length_m"
        )
        span = self._positive_raw(geometry.source.fins.semi_span_m, "fins.semi_span_m")
        fin_area = self._positive_raw(
            geometry.fin_planform_area_per_fin_m2,
            "fin_planform_area_per_fin_m2",
        )
        midchord_sweep = float(require_finite(
            geometry.fin_midchord_sweep_angle_rad,
            name="fin_midchord_sweep_angle_rad",
        ))
        mac = self._positive_raw(
            geometry.fin_mean_aerodynamic_chord_m,
            "fin_mean_aerodynamic_chord_m",
        )
        mac_le = float(require_finite(
            geometry.fin_mean_aerodynamic_chord_leading_edge_x_geo_m,
            name="fin_mean_aerodynamic_chord_leading_edge_x_geo_m",
        ))
        aspect_ratio = self._positive_raw(
            geometry.fin_aspect_ratio, "fin_aspect_ratio"
        )
        body_radius = self._positive_raw(
            geometry.fin_body_radius_at_root_m, "fin_body_radius_at_root_m"
        )
        if geometry.fin_angular_arrangement is not FinAngularArrangement.EQUALLY_SPACED:
            self._fail(
                "UNSUPPORTED_FIN_ANGULAR_ARRANGEMENT",
                "fin_angular_arrangement",
                geometry.fin_angular_arrangement,
            )
        fin_count = geometry.source.fins.fin_count
        if isinstance(fin_count, bool) or not isinstance(fin_count, int) or fin_count < 3:
            self._fail("UNSUPPORTED_FIN_COUNT", "fins.fin_count", fin_count)

        nose_cna = 2.0 * nose_frontal_area / reference_area
        nose_cp = (2.0 / 3.0) * nose_length

        sweep_cosine = cos(midchord_sweep)
        sweep_denominator = fin_area * sweep_cosine
        if not isfinite(sweep_denominator) or sweep_denominator <= 0.0:
            self._fail(
                "INVALID_FIN_MIDCHORD_SWEEP",
                "fin_midchord_sweep_angle_rad",
                midchord_sweep,
            )
        span_squared = span * span
        single_fin_cna = (
            2.0 * pi * span_squared / reference_area
            / (
                1.0
                + sqrt(
                    1.0
                    + (1.0 - mach * mach)
                    * (span_squared / sweep_denominator) ** 2
                )
            )
        )

        fins_in_current_set = fin_count
        bare_fin_set_cna = (fins_in_current_set / 2.0) * single_fin_cna
        # V1'de tek fin seti olduğu için sayılar eşittir; kavramlar birleşmez.
        total_interfering_parallel_fins = fins_in_current_set
        fin_interference = self._fin_fin_interference_factor(
            total_interfering_parallel_fins
        )
        body_to_fin = 1.0 + body_radius / (span + body_radius)
        fin_set_cna = bare_fin_set_cna * fin_interference * body_to_fin

        cp_fraction = self._fin_cp_fraction(mach=mach, aspect_ratio=aspect_ratio)
        fin_cp = mac_le + cp_fraction * mac

        for name, value in (
            ("nose_cna_per_rad", nose_cna),
            ("fin_single_cna_per_rad", single_fin_cna),
            ("fin_set_cna_per_rad", fin_set_cna),
        ):
            if not isfinite(value) or value <= 0.0:
                self._fail("INVALID_STATIC_CNA", name, value)
        for name, value in (("nose_cp_x_geo_m", nose_cp), ("fin_cp_x_geo_m", fin_cp)):
            if not isfinite(value):
                self._fail("INVALID_STATIC_CP", name, value)

        total_cna = nose_cna + fin_set_cna
        total_cp = (nose_cna * nose_cp + fin_set_cna * fin_cp) / total_cna
        if not isfinite(total_cna) or total_cna <= 0.0:
            self._fail("INVALID_TOTAL_STATIC_CNA", "total_cna_per_rad", total_cna)
        if not isfinite(total_cp):
            self._fail("INVALID_TOTAL_STATIC_CP", "cp_x_geo_m", total_cp)
        return StaticAerodynamicProperties(
            nose=StaticAeroContribution(nose_cna, nose_cp),
            fin_set=StaticAeroContribution(fin_set_cna, fin_cp),
            total_cna_per_rad=total_cna,
            cp_x_geo_m=total_cp,
            model_profile=model_profile,
        )

    @staticmethod
    def _fin_fin_interference_factor(total_interfering_parallel_fins: int) -> float:
        if total_interfering_parallel_fins <= 4:
            return 1.000
        if total_interfering_parallel_fins == 5:
            return 0.948
        if total_interfering_parallel_fins == 6:
            return 0.913
        if total_interfering_parallel_fins == 7:
            return 0.854
        if total_interfering_parallel_fins == 8:
            return 0.810
        return 0.750

    @staticmethod
    def _fin_cp_fraction(*, mach: float, aspect_ratio: float) -> float:
        if mach <= 0.5:
            return 0.25
        root_three = sqrt(3.0)
        endpoint_denominator = 2.0 * aspect_ratio * root_three - 1.0
        endpoint = (
            aspect_ratio * root_three - 0.67
        ) / endpoint_denominator
        endpoint_derivative = (
            0.34 * aspect_ratio * 2.0
            / (root_three * endpoint_denominator * endpoint_denominator)
        )
        delta = endpoint - 0.25
        scaled_derivative = 1.5 * endpoint_derivative
        u = (mach - 0.5) / 1.5
        return (
            0.25
            + (10.0 * delta - 6.0 * scaled_derivative) * u**2
            + (-20.0 * delta + 14.0 * scaled_derivative) * u**3
            + (15.0 * delta - 11.0 * scaled_derivative) * u**4
            + (-4.0 * delta + 3.0 * scaled_derivative) * u**5
        )

    @staticmethod
    def _require_supported_profile(profile: StaticStabilityModelProfile) -> None:
        if not isinstance(profile, StaticStabilityModelProfile) or (
            profile != NATIVE_STATIC_STABILITY_V1_PROFILE
        ):
            StaticAerodynamicEvaluator._fail(
                "UNSUPPORTED_STATIC_STABILITY_PROFILE", "model_profile", profile
            )

    @staticmethod
    def _positive_raw(value: float, field_name: str) -> float:
        result = float(require_finite(value, name=field_name))
        if result <= 0.0:
            StaticAerodynamicEvaluator._fail(
                "INVALID_STATIC_GEOMETRY_PREREQUISITE", field_name, result
            )
        return result

    @staticmethod
    def _fail(error_code: str, field_name: str, value: object) -> None:
        raise AerodynamicEvaluationError(
            error_code=error_code, field_name=field_name, value=value
        )
