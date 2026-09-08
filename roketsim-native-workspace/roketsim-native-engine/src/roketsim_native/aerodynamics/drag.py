"""NAT-012A.1: sıfır-AoA Native Basic Drag V1 katsayı değerlendirmesi.

Geometry şekil/alan/uzunlukları, AerodynamicSurfaces pürüzlülüğü,
FlightConditions V ve Mach'ı, AirProperties ise nu ve gamma'yı sahiplenir.
Bu modül yalnız açık model profiline göre yedi C_D0 katkısını türetir; kuvvet,
AoA, CP/CNa veya dinamik üretmez. Mach 1'e kadar formül değerlendirmek,
sonik sınıra yakın transonik doğruluğu bağımsız olarak doğrulamış sayılmaz.
"""

from dataclasses import dataclass
from enum import Enum
from math import cos, isfinite, log, sin

from roketsim_native.aerodynamics.surfaces import SingleStageRocketAerodynamicSurfaces
from roketsim_native.environment.air_properties import DryAirProperties
from roketsim_native.flight_conditions.basic import BasicFlightConditions
from roketsim_native.geometry.models import FinCrossSection
from roketsim_native.geometry.resolver import ResolvedRocketGeometry
from roketsim_native.math.numerical import require_finite

__all__ = (
    "AerodynamicEvaluationError",
    "BoundaryLayerModel",
    "ReynoldsLengthPolicy",
    "LowReynoldsContinuation",
    "NosePressureModel",
    "FinEdgeModel",
    "BaseDragModel",
    "PlumeTreatment",
    "SkinFrictionBranch",
    "SkinFrictionEvaluation",
    "BasicDragModelProfile",
    "NATIVE_BASIC_DRAG_V1_PROFILE",
    "BasicDragResult",
    "BasicDragEvaluator",
)


class AerodynamicEvaluationError(ValueError):
    """Sonlu fakat desteklenmeyen model/domain veya türetilmiş sonuç hatası."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


class BoundaryLayerModel(str, Enum):
    FULLY_TURBULENT = "fully_turbulent"


class ReynoldsLengthPolicy(str, Enum):
    AERODYNAMIC_LENGTH = "aerodynamic_length"


class LowReynoldsContinuation(str, Enum):
    MINIMUM_EVALUATION_REYNOLDS = "minimum_evaluation_reynolds"


class NosePressureModel(str, Enum):
    TD13_CONICAL_SUBSONIC_TO_SONIC = "td13_conical_subsonic_to_sonic"


class FinEdgeModel(str, Enum):
    TD13_SQUARE = "td13_square"


class BaseDragModel(str, Enum):
    TD13_SUBSONIC_TO_SONIC = "td13_subsonic_to_sonic"


class PlumeTreatment(str, Enum):
    IGNORED = "ignored"


class SkinFrictionBranch(str, Enum):
    SMOOTH = "smooth"
    ROUGHNESS_LIMITED = "roughness_limited"


@dataclass(frozen=True, slots=True)
class BasicDragModelProfile:
    """Basic Drag V1'in açık ve zorunlu fizik-politikası; fixture değildir."""

    boundary_layer_model: BoundaryLayerModel
    reynolds_length_policy: ReynoldsLengthPolicy
    low_reynolds_continuation: LowReynoldsContinuation
    minimum_friction_evaluation_reynolds: float
    nose_pressure_model: NosePressureModel
    fin_edge_model: FinEdgeModel
    base_drag_model: BaseDragModel
    plume_treatment: PlumeTreatment

    def __post_init__(self) -> None:
        minimum = require_finite(
            self.minimum_friction_evaluation_reynolds,
            name="minimum_friction_evaluation_reynolds",
        )
        if minimum <= 0.0:
            raise AerodynamicEvaluationError(
                error_code="NON_POSITIVE_MINIMUM_FRICTION_REYNOLDS",
                field_name="minimum_friction_evaluation_reynolds",
                value=minimum,
            )


NATIVE_BASIC_DRAG_V1_PROFILE = BasicDragModelProfile(
    boundary_layer_model=BoundaryLayerModel.FULLY_TURBULENT,
    reynolds_length_policy=ReynoldsLengthPolicy.AERODYNAMIC_LENGTH,
    low_reynolds_continuation=LowReynoldsContinuation.MINIMUM_EVALUATION_REYNOLDS,
    minimum_friction_evaluation_reynolds=1.0e4,
    nose_pressure_model=NosePressureModel.TD13_CONICAL_SUBSONIC_TO_SONIC,
    fin_edge_model=FinEdgeModel.TD13_SQUARE,
    base_drag_model=BaseDragModel.TD13_SUBSONIC_TO_SONIC,
    plume_treatment=PlumeTreatment.IGNORED,
)


@dataclass(frozen=True, slots=True)
class SkinFrictionEvaluation:
    """Düzeltilmiş smooth/roughness adayları ve seçilen fizik dalı."""

    smooth_corrected_cf: float
    roughness_corrected_cf: float
    selected_cf: float
    selected_branch: SkinFrictionBranch


@dataclass(frozen=True, slots=True)
class BasicDragResult:
    """Yedi fiziksel C_D0 katkısı ile Reynolds ve friction tanıları."""

    reynolds_number: float
    friction_evaluation_reynolds_number: float
    nose_skin_friction: SkinFrictionEvaluation
    body_skin_friction: SkinFrictionEvaluation
    fin_skin_friction: SkinFrictionEvaluation
    nose_friction_cd: float
    body_friction_cd: float
    fin_friction_cd: float
    nose_pressure_cd: float
    fin_leading_edge_pressure_cd: float
    fin_trailing_edge_base_cd: float
    airframe_base_cd: float
    total_cd0: float
    model_profile: BasicDragModelProfile

    @property
    def friction_cd(self) -> float:
        return self.nose_friction_cd + self.body_friction_cd + self.fin_friction_cd

    @property
    def pressure_cd(self) -> float:
        return self.nose_pressure_cd + self.fin_leading_edge_pressure_cd

    @property
    def base_cd(self) -> float:
        return self.fin_trailing_edge_base_cd + self.airframe_base_cd


class BasicDragEvaluator:
    """Parametresiz/stateless sıfır-AoA C_D0 değerlendirmesi."""

    __slots__ = ()

    def evaluate(
        self,
        *,
        resolved_geometry: ResolvedRocketGeometry,
        aerodynamic_surfaces: SingleStageRocketAerodynamicSurfaces,
        flight_conditions: BasicFlightConditions,
        air_properties: DryAirProperties,
        model_profile: BasicDragModelProfile,
    ) -> BasicDragResult:
        """Kabul edilmiş authority'leri tüket; gizli clamp/fallback uygulama."""
        self._require_supported_profile(model_profile)
        geometry = resolved_geometry
        fins = geometry.source.fins
        airspeed = float(require_finite(flight_conditions.airspeed_m_s, name="airspeed_m_s"))
        mach = float(require_finite(flight_conditions.mach, name="mach"))
        nu = float(require_finite(
            air_properties.kinematic_viscosity_m2_s,
            name="kinematic_viscosity_m2_s",
        ))
        gamma = float(require_finite(air_properties.specific_heat_ratio, name="specific_heat_ratio"))
        aerodynamic_length = float(require_finite(
            geometry.aerodynamic_length_m, name="aerodynamic_length_m"
        ))
        if airspeed < 0.0:
            self._fail("NEGATIVE_AIRSPEED", "airspeed_m_s", airspeed)
        if mach < 0.0:
            self._fail("NEGATIVE_MACH", "mach", mach)
        if mach > 1.0:
            self._fail("UNSUPPORTED_MACH_REGIME", "mach", mach)
        if nu <= 0.0:
            self._fail("NON_POSITIVE_KINEMATIC_VISCOSITY", "kinematic_viscosity_m2_s", nu)
        if gamma <= 1.0:
            self._fail("INVALID_SPECIFIC_HEAT_RATIO", "specific_heat_ratio", gamma)
        if aerodynamic_length <= 0.0:
            self._fail("INVALID_AERODYNAMIC_LENGTH", "aerodynamic_length_m", aerodynamic_length)
        if geometry.fin_cross_section is not FinCrossSection.SQUARE:
            self._fail("UNSUPPORTED_FIN_CROSS_SECTION", "fin_cross_section", geometry.fin_cross_section)

        reynolds = airspeed * aerodynamic_length / nu
        if not isfinite(reynolds) or reynolds < 0.0:
            self._fail("INVALID_REYNOLDS_NUMBER", "reynolds_number", reynolds)
        reynolds_eval = max(
            reynolds, float(model_profile.minimum_friction_evaluation_reynolds)
        )
        smooth_cf = 1.0 / (1.50 * log(reynolds_eval) - 5.6) ** 2
        compressibility = 1.0 - 0.10 * mach * mach
        smooth_corrected = smooth_cf * compressibility
        nose_skin = self._skin_friction(
            smooth_corrected, aerodynamic_surfaces.nose.equivalent_roughness_m,
            aerodynamic_length, compressibility,
        )
        body_skin = self._skin_friction(
            smooth_corrected, aerodynamic_surfaces.body.equivalent_roughness_m,
            aerodynamic_length, compressibility,
        )
        fin_skin = self._skin_friction(
            smooth_corrected, aerodynamic_surfaces.fins.equivalent_roughness_m,
            aerodynamic_length, compressibility,
        )

        reference_area = float(require_finite(geometry.reference_area_m2, name="reference_area_m2"))
        max_diameter = float(require_finite(
            geometry.max_external_airframe_diameter_m,
            name="max_external_airframe_diameter_m",
        ))
        body_length = float(require_finite(
            geometry.axisymmetric_body_length_m, name="axisymmetric_body_length_m"
        ))
        fin_mac = float(require_finite(
            geometry.fin_mean_aerodynamic_chord_m, name="fin_mean_aerodynamic_chord_m"
        ))
        nose_wetted_area = float(require_finite(
            geometry.nose_wetted_area_m2, name="nose_wetted_area_m2"
        ))
        body_wetted_area = float(require_finite(
            geometry.body_wetted_area_m2, name="body_wetted_area_m2"
        ))
        fin_planform_area = float(require_finite(
            geometry.fin_planform_area_per_fin_m2,
            name="fin_planform_area_per_fin_m2",
        ))
        nose_frontal_area = float(require_finite(
            geometry.nose_frontal_area_m2, name="nose_frontal_area_m2"
        ))
        airframe_base_area = float(require_finite(
            geometry.airframe_aft_base_area_m2, name="airframe_aft_base_area_m2"
        ))
        nose_half_angle = float(require_finite(
            geometry.nose_half_angle_rad, name="nose_half_angle_rad"
        ))
        fin_sweep_angle = float(require_finite(
            geometry.fin_leading_edge_sweep_angle_rad,
            name="fin_leading_edge_sweep_angle_rad",
        ))
        fin_thickness = float(require_finite(fins.thickness_m, name="fins.thickness_m"))
        fin_span = float(require_finite(fins.semi_span_m, name="fins.semi_span_m"))
        for name, value in (
            ("reference_area_m2", reference_area),
            ("max_external_airframe_diameter_m", max_diameter),
            ("axisymmetric_body_length_m", body_length),
            ("fin_mean_aerodynamic_chord_m", fin_mac),
            ("nose_wetted_area_m2", nose_wetted_area),
            ("body_wetted_area_m2", body_wetted_area),
            ("fin_planform_area_per_fin_m2", fin_planform_area),
            ("nose_frontal_area_m2", nose_frontal_area),
            ("airframe_aft_base_area_m2", airframe_base_area),
            ("fins.thickness_m", fin_thickness),
            ("fins.semi_span_m", fin_span),
        ):
            if value <= 0.0:
                self._fail("INVALID_GEOMETRY_PREREQUISITE", name, value)

        body_fineness = body_length / max_diameter
        body_correction = 1.0 + 1.0 / (2.0 * body_fineness)
        fin_correction = 1.0 + 2.0 * fin_thickness / fin_mac
        nose_friction = (
            body_correction * nose_skin.selected_cf * nose_wetted_area
            / reference_area
        )
        body_friction = (
            body_correction * body_skin.selected_cf * body_wetted_area
            / reference_area
        )
        fin_friction = (
            fin_skin.selected_cf * fin_correction
            * (2.0 * fins.fin_count * fin_planform_area)
            / reference_area
        )

        phi = nose_half_angle
        sin_phi = sin(phi)
        nose_c0 = 0.8 * sin_phi * sin_phi
        nose_c1 = sin_phi
        nose_d1 = 4.0 / (gamma + 1.0) * (1.0 - nose_c1 / 2.0)
        nose_amplitude = nose_c1 - nose_c0
        if nose_amplitude <= 0.0:
            self._fail("INVALID_NOSE_PRESSURE_GEOMETRY", "nose_half_angle_rad", phi)
        nose_exponent = nose_d1 / nose_amplitude
        nose_pressure_star = nose_c0 + nose_amplitude * mach ** nose_exponent
        nose_pressure = nose_pressure_star * nose_frontal_area / reference_area

        fin_edge_factor = (
            fins.fin_count * fin_thickness * fin_span / reference_area
        )
        stagnation = 0.85 * (1.0 + mach * mach / 4.0 + mach ** 4 / 40.0)
        fin_le_pressure = (
            fin_edge_factor * stagnation
            * cos(fin_sweep_angle) ** 2
        )
        base_star = 0.12 + 0.13 * mach * mach
        fin_te_base = fin_edge_factor * base_star
        airframe_base = base_star * airframe_base_area / reference_area
        contributions = (
            nose_friction,
            body_friction,
            fin_friction,
            nose_pressure,
            fin_le_pressure,
            fin_te_base,
            airframe_base,
        )
        for name, value in zip(
            (
                "nose_friction_cd", "body_friction_cd", "fin_friction_cd",
                "nose_pressure_cd", "fin_leading_edge_pressure_cd",
                "fin_trailing_edge_base_cd", "airframe_base_cd",
            ),
            contributions,
            strict=True,
        ):
            if not isfinite(value) or value < 0.0:
                self._fail("INVALID_DRAG_CONTRIBUTION", name, value)
        total = sum(contributions)
        if not isfinite(total) or total < 0.0:
            self._fail("INVALID_TOTAL_CD0", "total_cd0", total)
        return BasicDragResult(
            reynolds_number=reynolds,
            friction_evaluation_reynolds_number=reynolds_eval,
            nose_skin_friction=nose_skin,
            body_skin_friction=body_skin,
            fin_skin_friction=fin_skin,
            nose_friction_cd=nose_friction,
            body_friction_cd=body_friction,
            fin_friction_cd=fin_friction,
            nose_pressure_cd=nose_pressure,
            fin_leading_edge_pressure_cd=fin_le_pressure,
            fin_trailing_edge_base_cd=fin_te_base,
            airframe_base_cd=airframe_base,
            total_cd0=total,
            model_profile=model_profile,
        )

    @staticmethod
    def _skin_friction(
        smooth_corrected: float,
        equivalent_roughness_m: float,
        aerodynamic_length_m: float,
        compressibility_factor: float,
    ) -> SkinFrictionEvaluation:
        roughness = float(require_finite(
            equivalent_roughness_m, name="equivalent_roughness_m"
        ))
        if roughness < 0.0:
            BasicDragEvaluator._fail(
                "NEGATIVE_EQUIVALENT_ROUGHNESS", "equivalent_roughness_m", roughness
            )
        rough_corrected = 0.0 if roughness == 0.0 else (
            0.032 * (roughness / aerodynamic_length_m) ** 0.2
            * compressibility_factor
        )
        for name, value in (
            ("smooth_corrected_cf", smooth_corrected),
            ("roughness_corrected_cf", rough_corrected),
        ):
            if not isfinite(value) or value < 0.0:
                BasicDragEvaluator._fail("INVALID_SKIN_FRICTION", name, value)
        if rough_corrected > smooth_corrected:
            return SkinFrictionEvaluation(
                smooth_corrected, rough_corrected, rough_corrected,
                SkinFrictionBranch.ROUGHNESS_LIMITED,
            )
        return SkinFrictionEvaluation(
            smooth_corrected, rough_corrected, smooth_corrected,
            SkinFrictionBranch.SMOOTH,
        )

    @staticmethod
    def _require_supported_profile(profile: BasicDragModelProfile) -> None:
        expected = NATIVE_BASIC_DRAG_V1_PROFILE
        if not isinstance(profile, BasicDragModelProfile) or profile != expected:
            BasicDragEvaluator._fail(
                "UNSUPPORTED_BASIC_DRAG_PROFILE", "model_profile", profile
            )

    @staticmethod
    def _fail(error_code: str, field_name: str, value: object) -> None:
        raise AerodynamicEvaluationError(
            error_code=error_code, field_name=field_name, value=value
        )
