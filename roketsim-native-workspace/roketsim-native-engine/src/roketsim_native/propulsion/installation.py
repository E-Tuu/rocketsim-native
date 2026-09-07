"""NAT-011C.2: geçerli montaj geometrisi ile seçilmiş motorun ilişkisi.

Geometry montaj ölçüleri/yerleşiminin, MotorDefinition motor verisinin tek
otoritesidir. x_geo burun ucundan kuyruğa artar; tek eş eksenli motor vardır.
Çap eşitliği nominal uyumdur, üretim toleransı veya sıkı geçme modeli değildir.
Pozitif/sıfır/negatif overhang ancak pozitif eksenel örtüşmeyle geçerlidir.
İtki, motor kütlesi ve CG fiziği C.3'e ertelidir.
"""

from dataclasses import dataclass
from math import isfinite

from roketsim_native.geometry.resolver import ResolvedRocketGeometry
from roketsim_native.propulsion.models import MotorDefinition

__all__ = ("MotorInstallation", "MotorInstallationResolver", "MotorInstallationError")


class MotorInstallationError(ValueError):
    """Geçerli motor/geometri birleşiminin uyumsuzluğunu veya türetim hatasını bildir."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class MotorInstallation:
    """Seçilmiş motor kimliği ve yalnız türetilmiş kurulum ilişkileri; tüm uzunluklar m."""

    motor: MotorDefinition
    motor_front_x_geo_m: float
    motor_aft_x_geo_m: float
    nominal_radial_clearance_m: float
    axial_engagement_length_m: float


class MotorInstallationResolver:
    """Fiziksel durum tutmayan nominal tek-motor kurulum çözümleyicisi."""

    __slots__ = ()

    def resolve(self, *, resolved_geometry: ResolvedRocketGeometry,
                motor: MotorDefinition) -> MotorInstallation:
        """Hazır otoriteleri tüket; ölçüleri veya overhang'i yeniden çözme/düzeltme."""
        motor_aft = float(resolved_geometry.motor_aft_reference_x_geo_m)
        if not isfinite(motor_aft):
            raise MotorInstallationError(error_code="INVALID_MOTOR_AFT_POSITION",
                field_name="motor_aft_x_geo_m", value=motor_aft)
        motor_front = motor_aft - float(motor.length_m)
        if not isfinite(motor_front):
            raise MotorInstallationError(error_code="INVALID_MOTOR_FRONT_POSITION",
                field_name="motor_front_x_geo_m", value=motor_front)

        mount_diameter = float(resolved_geometry.motor_mount_inner_diameter_m)
        motor_diameter = float(motor.diameter_m)
        clearance = (mount_diameter - motor_diameter) / 2.0
        if not isfinite(clearance):
            raise MotorInstallationError(error_code="INVALID_RADIAL_CLEARANCE",
                field_name="nominal_radial_clearance_m", value=clearance)
        if motor_diameter > mount_diameter:
            raise MotorInstallationError(error_code="MOTOR_DIAMETER_EXCEEDS_MOUNT",
                field_name="diameter_m", value=motor_diameter)

        mount_start = float(resolved_geometry.motor_mount_start_x_geo_m)
        mount_end = float(resolved_geometry.motor_mount_end_x_geo_m)
        # min/max bir NaN sınırını gizlememelidir; bozuk upstream sonuç açık hatadır.
        for name, value in (("motor_mount_start_x_geo_m", mount_start),
                            ("motor_mount_end_x_geo_m", mount_end)):
            if not isfinite(value):
                raise MotorInstallationError(error_code="INVALID_AXIAL_ENGAGEMENT",
                    field_name=name, value=value)
        if motor_front < mount_start:
            raise MotorInstallationError(error_code="MOTOR_FRONT_BEFORE_MOUNT",
                field_name="motor_front_x_geo_m", value=motor_front)
        overlap_start = max(motor_front, mount_start)
        overlap_end = min(motor_aft, mount_end)
        engagement = overlap_end - overlap_start
        if not isfinite(engagement):
            raise MotorInstallationError(error_code="INVALID_AXIAL_ENGAGEMENT",
                field_name="axial_engagement_length_m", value=engagement)
        if engagement <= 0.0:
            raise MotorInstallationError(error_code="NO_POSITIVE_MOTOR_ENGAGEMENT",
                field_name="axial_engagement_length_m", value=engagement)
        return MotorInstallation(motor, motor_front, motor_aft, clearance, engagement)
