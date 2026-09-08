"""NAT-016: fixed-step policy, (t,y) point ve 3DOF state algebra temeli.

Bu modül integrator değildir: derivative çağırmaz, time ilerletmez ve kabul
edilmiş bir Euler/RK adımı tanımlamaz. Yalnız ileride RK4 ara state'lerinin
kurulması için y + scale*k cebirini sağlar.
"""

from dataclasses import dataclass

from roketsim_native.dynamics.initial_state import TranslationalState3DOF
from roketsim_native.dynamics.translational import TranslationalStateDerivative3DOF
from roketsim_native.math.numerical import require_finite

__all__ = (
    "NumericalIntegrationError",
    "FixedStepConfig",
    "IntegrationPoint3DOF",
    "TranslationalStateAlgebra3DOF",
)


class NumericalIntegrationError(ValueError):
    """Finite numerical-policy semantic hatasını structured olarak korur."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class FixedStepConfig:
    """Defaultsuz, min/max/clipping içermeyen pozitif fixed-step politikası."""

    step_size_s: float

    def __post_init__(self) -> None:
        step_size = float(require_finite(self.step_size_s, name="step_size_s"))
        if step_size <= 0.0:
            raise NumericalIntegrationError(
                error_code="INVALID_STEP_SIZE",
                field_name="step_size_s",
                value=step_size,
            )
        object.__setattr__(self, "step_size_s", step_size)


@dataclass(frozen=True, slots=True)
class IntegrationPoint3DOF:
    """Yalnız finite numerical time ve accepted translational state: (t,y)."""

    time_s: float
    state: TranslationalState3DOF

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "time_s",
            float(require_finite(self.time_s, name="time_s")),
        )


class TranslationalStateAlgebra3DOF:
    """PhysicsEvaluator çağırmayan parameterless/stateless 3DOF state cebiri."""

    __slots__ = ()

    def add_scaled_derivative(
        self,
        *,
        state: TranslationalState3DOF,
        derivative: TranslationalStateDerivative3DOF,
        scale_s: float,
    ) -> TranslationalState3DOF:
        """Yeni y + scale*k state'i üret; input veya time değiştirme."""

        scale = float(require_finite(scale_s, name="scale_s"))
        new_position = (
            state.position_world_m
            + scale * derivative.position_derivative_world_m_s
        )
        new_velocity = (
            state.velocity_world_m_s
            + scale * derivative.velocity_derivative_world_m_s2
        )
        return TranslationalState3DOF(
            position_world_m=new_position,
            velocity_world_m_s=new_velocity,
        )
