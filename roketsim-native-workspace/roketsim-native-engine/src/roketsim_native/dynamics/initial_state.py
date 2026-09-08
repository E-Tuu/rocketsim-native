"""NAT-013: WORLD ENU başlangıç konumu, hızı ve launch-direction girdisi.

Kabul edilmiş vektör temsili ``numpy.ndarray[numpy.float64]`` olarak yeniden
kullanılır. Domain nesneleri caller array'lerini kopyalar ve saklanan kopyaları
read-only yapar. Launch direction için bu modüle özgü unit-norm toleransı
uygulanır; girdi hiçbir zaman normalize edilmez veya başka biçimde onarılmaz.

WORLD z koordinatı atmosfer yüksekliği değildir. Altitude/environment dönüşümü
NAT-009 otoritesinde kalır ve WORLD-position bağlantısı NAT-015'e ertelenmiştir.
"""

from dataclasses import dataclass
from typing import Final

import numpy as np
from numpy.typing import NDArray

from roketsim_native.math.numerical import is_near_zero
from roketsim_native.math.vectors import as_vector, magnitude

__all__ = (
    "LAUNCH_DIRECTION_UNIT_NORM_ATOL",
    "InitialStateValidationError",
    "TranslationalState3DOF",
    "LaunchConditions3DOF",
    "InitialStateBuilder",
)


# Native NAT-013 validation policy; global math toleransı veya fizik sabiti değildir.
LAUNCH_DIRECTION_UNIT_NORM_ATOL: Final[float] = 1.0e-8

_Vector3 = NDArray[np.float64]


class InitialStateValidationError(ValueError):
    """Finite launch-state semantic hatasını structured olarak korur."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


def _read_only_vector3(value: object, *, field_name: str) -> _Vector3:
    """Accepted vector doğrulamasını uygula; bağımsız read-only kopya sakla."""

    vector = as_vector(value, size=3, name=field_name)
    vector.flags.writeable = False
    return vector


@dataclass(frozen=True, slots=True)
class TranslationalState3DOF:
    """Local WORLD ENU konum ve hızdan oluşan zamansız translational state."""

    position_world_m: _Vector3
    velocity_world_m_s: _Vector3

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "position_world_m",
            _read_only_vector3(self.position_world_m, field_name="position_world_m"),
        )
        object.__setattr__(
            self,
            "velocity_world_m_s",
            _read_only_vector3(
                self.velocity_world_m_s,
                field_name="velocity_world_m_s",
            ),
        )


@dataclass(frozen=True, slots=True)
class LaunchConditions3DOF:
    """Explicit WORLD initial değerleri ve launcher longitudinal yönü."""

    initial_position_world_m: _Vector3
    initial_velocity_world_m_s: _Vector3
    launch_direction_world_unit: _Vector3

    def __post_init__(self) -> None:
        position = _read_only_vector3(
            self.initial_position_world_m,
            field_name="initial_position_world_m",
        )
        velocity = _read_only_vector3(
            self.initial_velocity_world_m_s,
            field_name="initial_velocity_world_m_s",
        )
        direction = _read_only_vector3(
            self.launch_direction_world_unit,
            field_name="launch_direction_world_unit",
        )
        direction_norm = magnitude(direction)
        direction_value = tuple(float(component) for component in direction)
        if is_near_zero(direction_norm, atol=0.0):
            raise InitialStateValidationError(
                error_code="INVALID_LAUNCH_DIRECTION",
                field_name="launch_direction_world_unit",
                value=direction_value,
            )
        if abs(direction_norm - 1.0) > LAUNCH_DIRECTION_UNIT_NORM_ATOL:
            raise InitialStateValidationError(
                error_code="NON_UNIT_LAUNCH_DIRECTION",
                field_name="launch_direction_world_unit",
                value=direction_value,
            )

        object.__setattr__(self, "initial_position_world_m", position)
        object.__setattr__(self, "initial_velocity_world_m_s", velocity)
        object.__setattr__(self, "launch_direction_world_unit", direction)


class InitialStateBuilder:
    """Launch koşullarından yalnız position/velocity state'i üreten stateless builder."""

    __slots__ = ()

    def build(
        self,
        *,
        launch_conditions: LaunchConditions3DOF,
    ) -> TranslationalState3DOF:
        """Girdileri değiştirmeden exact position ve velocity değerlerini eşle."""

        return TranslationalState3DOF(
            position_world_m=launch_conditions.initial_position_world_m,
            velocity_world_m_s=launch_conditions.initial_velocity_world_m_s,
        )
