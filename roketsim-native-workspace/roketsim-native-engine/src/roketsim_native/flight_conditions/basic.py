"""NAT-010B: WORLD/ENU bağıl hızından yalnız V/M/Re/q scalar koşulları.

Airspeed tam 3D normdur. Mach NAT-009D ses hızını, Re NAT-009D kinematic
viscosity'yi, q NAT-009C yoğunluğunu tüketir; upstream fizik yeniden hesaplanmaz.
Reference length açık karakteristik uzunluktur (SI m), zorunlu bir çap değildir.
Sıfır bağıl hız geçerlidir. AoA, BODY flow ve aerodynamics sonraki kapsamlardır.
"""

from dataclasses import dataclass
from math import hypot

from numpy.typing import ArrayLike as _ArrayLike

from roketsim_native.environment.atmosphere import DryAirAtmosphereState
from roketsim_native.environment.air_properties import DryAirProperties
from roketsim_native.math.numerical import require_finite
from roketsim_native.math.vectors import as_vector

__all__ = (
    "BasicFlightConditions", "BasicFlightConditionsCalculator", "FlightConditionsDomainError"
)


@dataclass(frozen=True, slots=True)
class BasicFlightConditions:
    """Yalnız dört türetilmiş scalar; upstream state'in kopyası değildir."""

    airspeed_m_s: float
    mach: float
    reynolds: float
    dynamic_pressure_Pa: float


class FlightConditionsDomainError(ValueError):
    """Finite fakat pozitif olmayan prerequisite için structured domain hatası."""

    def __init__(self, *, field_name: str, value: float) -> None:
        self.field_name = field_name
        self.value = value
        super().__init__(f"{field_name} must be strictly positive; got {value!r}")


class BasicFlightConditionsCalculator:
    """Parametresiz ve fiziksel state tutmayan temel koşul hesaplayıcısı."""

    __slots__ = ()

    def evaluate(
        self,
        *,
        relative_velocity_world_m_s: _ArrayLike,
        atmosphere_state: DryAirAtmosphereState,
        air_properties: DryAirProperties,
        reference_length_m: float,
    ) -> BasicFlightConditions:
        """Finite girdileri ve pozitif prerequisite'leri doğrula; clamp uygulama."""
        relative_velocity = as_vector(
            relative_velocity_world_m_s, size=3, name="relative_velocity_world_m_s"
        )
        density = float(require_finite(atmosphere_state.density_kg_m3, name="density_kg_m3"))
        sound_speed = float(require_finite(air_properties.speed_of_sound_m_s, name="speed_of_sound_m_s"))
        kinematic_viscosity = float(require_finite(
            air_properties.kinematic_viscosity_m2_s, name="kinematic_viscosity_m2_s"
        ))
        reference_length = float(require_finite(reference_length_m, name="reference_length_m"))
        for name, value in (
            ("density_kg_m3", density), ("speed_of_sound_m_s", sound_speed),
            ("kinematic_viscosity_m2_s", kinematic_viscosity),
            ("reference_length_m", reference_length),
        ):
            if value <= 0.0:
                raise FlightConditionsDomainError(field_name=name, value=value)

        # hypot, tam 3D Öklid normunda gereksiz ara kare taşmasını önler.
        airspeed_m_s = require_finite(hypot(*relative_velocity), name="airspeed_m_s")
        mach = airspeed_m_s / sound_speed
        reynolds = airspeed_m_s * reference_length / kinematic_viscosity
        dynamic_pressure_Pa = 0.5 * density * airspeed_m_s * airspeed_m_s
        for name, value in (
            ("airspeed_m_s", airspeed_m_s), ("mach", mach),
            ("reynolds", reynolds), ("dynamic_pressure_Pa", dynamic_pressure_Pa),
        ):
            require_finite(value, name=name)
            if value < 0.0 or (airspeed_m_s > 0.0 and value == 0.0):
                raise ValueError(f"{name} violates non-negative/positive-speed invariant; got {value!r}")
        return BasicFlightConditions(airspeed_m_s, mach, reynolds, dynamic_pressure_Pa)
