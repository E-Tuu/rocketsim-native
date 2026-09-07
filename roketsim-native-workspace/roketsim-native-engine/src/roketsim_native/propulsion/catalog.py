"""İlk demo kataloğu: kullanıcı verified motor seçer, desteklenen veriyi elle girmez.

NAR static-test exact değerleri üretici yuvarlamasından üstündür. Curve dynamics
verisi, certification özeti V&V metadata'dır; burada runtime değerlendirme yok.
"""

from dataclasses import dataclass
from typing import Final

from roketsim_native.propulsion.models import (
    MotorDefinition, MotorType, ThrustSample, MotorCertificationReference, MotorDataProvenance,
)

__all__ = ("MotorCatalog", "AEROTECH_F50_4T", "DEMO_MOTOR_CATALOG")


@dataclass(frozen=True, slots=True)
class MotorCatalog:
    """Immutable verified tanımlar; bilinmeyen ID için fallback motor yok."""

    motors: tuple[MotorDefinition, ...]

    def __post_init__(self) -> None:
        if not isinstance(self.motors, tuple) or not all(isinstance(m, MotorDefinition) for m in self.motors):
            raise ValueError("motors must be a tuple of MotorDefinition")
        identifiers = [m.motor_id for m in self.motors]
        if len(set(identifiers)) != len(identifiers):
            raise ValueError("duplicate motor_id in MotorCatalog")

    def get(self, motor_id: str) -> MotorDefinition:
        """Stable ID ile exact definition döndür; bulunamazsa KeyError üret."""
        for motor in self.motors:
            if motor.motor_id == motor_id:
                return motor
        raise KeyError(motor_id)


AEROTECH_F50_4T: Final[MotorDefinition] = MotorDefinition(
    motor_id="aerotech_f50_4t", manufacturer="AeroTech", designation="F50-4T", family="F50T",
    motor_type=MotorType.SINGLE_USE, propellant_name="Blue Thunder",
    diameter_m=0.029, length_m=0.098, initial_mass_kg=0.0849, propellant_mass_kg=0.0379,
    ejection_delay_s=4.0,
    # Doğrulanmış mass/CG zaman serisi yoktur; model politikası katalogdan ayrıdır.
    mass_curve=None, cg_curve=None,
    thrust_curve=(
        # Yalnız bu katalog kaydı için explicit Native ignition boundary.
        ThrustSample(0.000, 0.000),
        ThrustSample(0.012, 51.377), ThrustSample(0.023, 61.197),
        ThrustSample(0.026, 66.117), ThrustSample(0.044, 66.564),
        ThrustSample(0.082, 69.685), ThrustSample(0.152, 73.264),
        ThrustSample(0.208, 75.053), ThrustSample(0.237, 77.279),
        ThrustSample(0.254, 76.832), ThrustSample(0.272, 77.726),
        ThrustSample(0.307, 77.726), ThrustSample(0.330, 76.832),
        ThrustSample(0.336, 78.621), ThrustSample(0.342, 76.832),
        ThrustSample(0.354, 79.590), ThrustSample(0.363, 76.385),
        ThrustSample(0.371, 77.756), ThrustSample(0.395, 76.385),
        ThrustSample(0.447, 75.937), ThrustSample(0.523, 73.711),
        ThrustSample(0.652, 68.344), ThrustSample(0.810, 60.302),
        ThrustSample(0.828, 62.539), ThrustSample(0.836, 58.076),
        ThrustSample(0.901, 53.603), ThrustSample(1.079, 37.074),
        ThrustSample(1.158, 29.480), ThrustSample(1.196, 25.464),
        ThrustSample(1.246, 16.976), ThrustSample(1.301, 9.380),
        ThrustSample(1.430, 0.000),
    ),
    certification=MotorCertificationReference(76.83, 53.73, 79.59, 1.43),
    provenance=MotorDataProvenance(
        manufacturer_source="AeroTech F50-4T product 65004: https://aerotech-rocketry.com/products/product_24cdff8c-59d9-6dcb-a66e-8bc9babb0c2c",
        certification_source="NAR S&T AEROTECH F50, tested 1995-09-03, updated 1/98, page 1 (4 s variant): https://www.thrustcurve.org/motors/cert/60c63bfcb5bc370004713e82/F50.pdf",
        thrust_curve_source="NAR-published data RASP transcription, 2000-07-04, page 2: https://www.thrustcurve.org/motors/cert/60c63bfcb5bc370004713e82/F50.pdf",
        normalization_note="Native canonical curve prepends (0.0 s, 0.0 N); all published thrust samples are otherwise preserved.",
        mass_curve_source=None, cg_curve_source=None,
    ),
)

DEMO_MOTOR_CATALOG: Final[MotorCatalog] = MotorCatalog((AEROTECH_F50_4T,))
