"""NAT-009D: NAT-009C thermodynamic state'inden kuru hava özellikleri.

Frozen Native baseline: a=sqrt(gamma*R_air*T), gamma=1.40;
Sutherland mu=beta*T^(3/2)/(T+S); nu=mu/rho. R_air tek otorite olan
atmosphere modülünden, rho doğrudan supplied NAT-009C state'inden alınır.
Pressure kullanılmaz; altitude domain yoktur. T ve rho finite ve pozitif olmalı.
OpenRocket exact viscosity parity sonraki V&V'ye, Mach/Re downstream'e aittir.
Clamp, fallback, humidity veya temperature-dependent gamma uygulanmaz.
"""

from dataclasses import dataclass
from math import sqrt
from typing import Final

from roketsim_native.environment import atmosphere as _atmosphere
from roketsim_native.math.numerical import require_finite


__all__ = (
    "DryAirPropertiesCalculator",
    "DryAirProperties",
    "AirPropertiesDomainError",
    "DRY_AIR_SPECIFIC_HEAT_RATIO",
    "SUTHERLAND_BETA",
    "SUTHERLAND_CONSTANT_K",
)

DRY_AIR_SPECIFIC_HEAT_RATIO: Final[float] = 1.40
# [LITERATURE / Sutherland] beta birimi kg/(m s sqrt(K)); ikinci mu0/T0 kaynağı yok.
SUTHERLAND_BETA: Final[float] = 1.458e-6
SUTHERLAND_CONSTANT_K: Final[float] = 110.4


@dataclass(frozen=True, slots=True)
class DryAirProperties:
    """Yalnız a/mu/nu içeren SI birimli immutable derived snapshot."""

    speed_of_sound_m_s: float
    dynamic_viscosity_Pa_s: float
    kinematic_viscosity_m2_s: float


class AirPropertiesDomainError(ValueError):
    """Finite fakat pozitif olmayan prerequisite için structured domain hatası."""

    def __init__(self, *, field_name: str, value: float) -> None:
        self.field_name = field_name
        self.value = value
        super().__init__(f"{field_name} must be strictly positive; got {value!r}")


class DryAirPropertiesCalculator:
    """Parametresiz kuru hava hesaplayıcısı; pressure/density yeniden hesaplanmaz."""

    __slots__ = ()

    def evaluate(
        self, *, atmosphere_state: _atmosphere.DryAirAtmosphereState
    ) -> DryAirProperties:
        """Finite T/rho'yu doğrula, pozitif domain'i denetle ve a/mu/nu üret."""
        temperature_K = float(require_finite(
            atmosphere_state.temperature_K, name="temperature_K"
        ))
        density_kg_m3 = float(require_finite(
            atmosphere_state.density_kg_m3, name="density_kg_m3"
        ))
        for name, value in (
            ("temperature_K", temperature_K), ("density_kg_m3", density_kg_m3)
        ):
            if value <= 0.0:
                raise AirPropertiesDomainError(field_name=name, value=value)

        # Cebirsel eşdeğer çarpanlama, büyük finite T için gereksiz ara taşmayı önler.
        root_temperature = sqrt(temperature_K)
        speed_of_sound_m_s = sqrt(
            DRY_AIR_SPECIFIC_HEAT_RATIO
            * _atmosphere.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K
        ) * root_temperature
        dynamic_viscosity_Pa_s = (
            SUTHERLAND_BETA * root_temperature
            * (temperature_K / (temperature_K + SUTHERLAND_CONSTANT_K))
        )
        kinematic_viscosity_m2_s = dynamic_viscosity_Pa_s / density_kg_m3
        for name, value in (
            ("speed_of_sound_m_s", speed_of_sound_m_s),
            ("dynamic_viscosity_Pa_s", dynamic_viscosity_Pa_s),
            ("kinematic_viscosity_m2_s", kinematic_viscosity_m2_s),
        ):
            require_finite(value, name=name)
            if value <= 0.0:
                raise ValueError(f"{name} must be positive; got {value!r}")
        return DryAirProperties(
            speed_of_sound_m_s, dynamic_viscosity_Pa_s, kinematic_viscosity_m2_s
        )
