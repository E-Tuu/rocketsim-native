"""NAT-014: evaluated upstream snapshot'tan instantaneous 3DOF translation.

Bu modül atmosphere, relative flow, q, Cd0, thrust veya mass modeli çalıştırmaz.
Yalnız hazır physical inputs ile m*dv/dt = thrust + drag + gravity ve
dr/dt = velocity denklemlerini uygular. Integrasyon, rail ve attitude yoktur.
"""

from dataclasses import dataclass
from enum import Enum
from math import isfinite
from typing import Final

import numpy as np
from numpy.typing import NDArray

from roketsim_native.dynamics.initial_state import (
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.math.numerical import require_finite
from roketsim_native.math.vectors import as_vector, magnitude

__all__ = (
    "ThrustDirectionModel",
    "AerodynamicForceModel",
    "TranslationalDynamicsModelProfile",
    "NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE",
    "DynamicsEvaluationError",
    "TranslationalDynamicsInputs",
    "TranslationalForces3DOF",
    "TranslationalStateDerivative3DOF",
    "TranslationalDynamicsResult",
    "TranslationalDynamicsEvaluator",
)

_Vector3 = NDArray[np.float64]


class ThrustDirectionModel(Enum):
    """V1'de thrust yönünü sabit WORLD launch direction belirler."""

    FIXED_LAUNCH_DIRECTION = "fixed_launch_direction"


class AerodynamicForceModel(Enum):
    """V1 yalnız zero-AoA baseline Cd0 drag kuvvetini içerir."""

    BASELINE_CD0_DRAG_ONLY = "baseline_cd0_drag_only"


@dataclass(frozen=True, slots=True)
class TranslationalDynamicsModelProfile:
    """Thrust-direction ve aerodynamic-force politikalarını açıkça seçer."""

    thrust_direction_model: ThrustDirectionModel
    aerodynamic_force_model: AerodynamicForceModel


NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE: Final[
    TranslationalDynamicsModelProfile
] = TranslationalDynamicsModelProfile(
    ThrustDirectionModel.FIXED_LAUNCH_DIRECTION,
    AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY,
)


class DynamicsEvaluationError(ValueError):
    """Finite dynamics semantic/model-domain hatasını structured olarak korur."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


def _read_only_vector3(value: object, *, field_name: str) -> _Vector3:
    """Accepted vector semantics ile bağımsız, finite, read-only 3-vector üret."""

    vector = as_vector(value, size=3, name=field_name)
    vector.flags.writeable = False
    return vector


def _validated_scalar(
    value: float,
    *,
    field_name: str,
    error_code: str,
    strictly_positive: bool,
) -> float:
    """Non-finite raw değeri generic, finite domain ihlalini structured reddet."""

    canonical = float(require_finite(value, name=field_name))
    invalid = canonical <= 0.0 if strictly_positive else canonical < 0.0
    if invalid:
        raise DynamicsEvaluationError(
            error_code=error_code,
            field_name=field_name,
            value=canonical,
        )
    return canonical


@dataclass(frozen=True, slots=True)
class TranslationalDynamicsInputs:
    """NAT-015'in ileride kuracağı ephemeral evaluated-physics taşıyıcısı."""

    mass_kg: float
    thrust_N: float
    relative_velocity_world_m_s: _Vector3
    dynamic_pressure_Pa: float
    reference_area_m2: float
    drag_coefficient_cd0: float
    gravity_acceleration_world_m_s2: _Vector3

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "mass_kg",
            _validated_scalar(
                self.mass_kg,
                field_name="mass_kg",
                error_code="INVALID_MASS",
                strictly_positive=True,
            ),
        )
        object.__setattr__(
            self,
            "thrust_N",
            _validated_scalar(
                self.thrust_N,
                field_name="thrust_N",
                error_code="INVALID_THRUST",
                strictly_positive=False,
            ),
        )
        object.__setattr__(
            self,
            "relative_velocity_world_m_s",
            _read_only_vector3(
                self.relative_velocity_world_m_s,
                field_name="relative_velocity_world_m_s",
            ),
        )
        object.__setattr__(
            self,
            "dynamic_pressure_Pa",
            _validated_scalar(
                self.dynamic_pressure_Pa,
                field_name="dynamic_pressure_Pa",
                error_code="INVALID_DYNAMIC_PRESSURE",
                strictly_positive=False,
            ),
        )
        object.__setattr__(
            self,
            "reference_area_m2",
            _validated_scalar(
                self.reference_area_m2,
                field_name="reference_area_m2",
                error_code="INVALID_REFERENCE_AREA",
                strictly_positive=True,
            ),
        )
        object.__setattr__(
            self,
            "drag_coefficient_cd0",
            _validated_scalar(
                self.drag_coefficient_cd0,
                field_name="drag_coefficient_cd0",
                error_code="INVALID_DRAG_COEFFICIENT",
                strictly_positive=False,
            ),
        )
        object.__setattr__(
            self,
            "gravity_acceleration_world_m_s2",
            _read_only_vector3(
                self.gravity_acceleration_world_m_s2,
                field_name="gravity_acceleration_world_m_s2",
            ),
        )


@dataclass(frozen=True, slots=True)
class TranslationalForces3DOF:
    """Üç physical WORLD force contribution; net force derived property'dir."""

    thrust_force_world_N: _Vector3
    drag_force_world_N: _Vector3
    gravity_force_world_N: _Vector3

    def __post_init__(self) -> None:
        for field_name in (
            "thrust_force_world_N",
            "drag_force_world_N",
            "gravity_force_world_N",
        ):
            object.__setattr__(
                self,
                field_name,
                _read_only_vector3(getattr(self, field_name), field_name=field_name),
            )

    @property
    def net_force_world_N(self) -> _Vector3:
        """Stored authority yaratmadan üç contribution'ın exact toplamını döndür."""

        return _read_only_vector3(
            self.thrust_force_world_N
            + self.drag_force_world_N
            + self.gravity_force_world_N,
            field_name="net_force_world_N",
        )


@dataclass(frozen=True, slots=True)
class TranslationalStateDerivative3DOF:
    """WORLD position rate ve velocity rate; time state içinde değildir."""

    position_derivative_world_m_s: _Vector3
    velocity_derivative_world_m_s2: _Vector3

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "position_derivative_world_m_s",
            _read_only_vector3(
                self.position_derivative_world_m_s,
                field_name="position_derivative_world_m_s",
            ),
        )
        object.__setattr__(
            self,
            "velocity_derivative_world_m_s2",
            _read_only_vector3(
                self.velocity_derivative_world_m_s2,
                field_name="velocity_derivative_world_m_s2",
            ),
        )


@dataclass(frozen=True, slots=True)
class TranslationalDynamicsResult:
    """Instantaneous forces, state derivative ve kullanılan explicit profile."""

    forces: TranslationalForces3DOF
    derivative: TranslationalStateDerivative3DOF
    model_profile: TranslationalDynamicsModelProfile

    @property
    def acceleration_world_m_s2(self) -> _Vector3:
        """Ayrı authority olmadan derivative velocity-rate görünümünü döndür."""

        return self.derivative.velocity_derivative_world_m_s2


class TranslationalDynamicsEvaluator:
    """History/cache tutmayan instantaneous point-mass evaluator."""

    __slots__ = ()

    def evaluate(
        self,
        *,
        state: TranslationalState3DOF,
        launch_conditions: LaunchConditions3DOF,
        inputs: TranslationalDynamicsInputs,
        model_profile: TranslationalDynamicsModelProfile,
    ) -> TranslationalDynamicsResult:
        """Hazır girdilerden force breakdown ve dr/dt,dv/dt hesapla."""

        if (
            model_profile.thrust_direction_model
            is not ThrustDirectionModel.FIXED_LAUNCH_DIRECTION
        ):
            raise DynamicsEvaluationError(
                error_code="UNSUPPORTED_THRUST_DIRECTION_MODEL",
                field_name="thrust_direction_model",
                value=model_profile.thrust_direction_model,
            )
        if (
            model_profile.aerodynamic_force_model
            is not AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY
        ):
            raise DynamicsEvaluationError(
                error_code="UNSUPPORTED_AERODYNAMIC_FORCE_MODEL",
                field_name="aerodynamic_force_model",
                value=model_profile.aerodynamic_force_model,
            )

        thrust_force = inputs.thrust_N * launch_conditions.launch_direction_world_unit
        gravity_force = inputs.mass_kg * inputs.gravity_acceleration_world_m_s2

        relative_speed = magnitude(inputs.relative_velocity_world_m_s)
        if relative_speed == 0.0:
            if inputs.dynamic_pressure_Pa > 0.0:
                raise DynamicsEvaluationError(
                    error_code="INCONSISTENT_DRAG_INPUTS",
                    field_name="dynamic_pressure_Pa",
                    value=inputs.dynamic_pressure_Pa,
                )
            drag_force = np.zeros(3, dtype=np.float64)
        else:
            drag_magnitude = (
                inputs.dynamic_pressure_Pa
                * inputs.reference_area_m2
                * inputs.drag_coefficient_cd0
            )
            drag_force = (
                -drag_magnitude
                * inputs.relative_velocity_world_m_s
                / relative_speed
            )

        forces = TranslationalForces3DOF(
            thrust_force_world_N=thrust_force,
            drag_force_world_N=drag_force,
            gravity_force_world_N=gravity_force,
        )
        acceleration = forces.net_force_world_N / inputs.mass_kg
        if not all(isfinite(float(component)) for component in acceleration):
            raise DynamicsEvaluationError(
                error_code="INVALID_ACCELERATION",
                field_name="velocity_derivative_world_m_s2",
                value=tuple(float(component) for component in acceleration),
            )
        derivative = TranslationalStateDerivative3DOF(
            position_derivative_world_m_s=state.velocity_world_m_s,
            velocity_derivative_world_m_s2=acceleration,
        )
        return TranslationalDynamicsResult(forces, derivative, model_profile)
