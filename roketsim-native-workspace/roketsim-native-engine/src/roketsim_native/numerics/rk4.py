"""NAT-017: klasik explicit fixed-step RK4 ile tek 3DOF numerical adım.

Bu modül yalnız ``(t, y) -> dy/dt`` callable sözleşmesini bilir. Domain physics
authority'si içermez; event, history ve adaptive step politikası uygulamaz.
"""

from math import isfinite
from typing import Protocol

from roketsim_native.dynamics.initial_state import TranslationalState3DOF
from roketsim_native.dynamics.translational import TranslationalStateDerivative3DOF
from roketsim_native.numerics.fixed_step import (
    FixedStepConfig,
    IntegrationPoint3DOF,
    NumericalIntegrationError,
    TranslationalStateAlgebra3DOF,
)

__all__ = (
    "DerivativeFunction3DOF",
    "ClassicalRK4Integrator3DOF",
)


class DerivativeFunction3DOF(Protocol):
    """Physics authority olmayan minimal numerical ODE callable sözleşmesi."""

    def __call__(
        self,
        *,
        time_s: float,
        state: TranslationalState3DOF,
    ) -> TranslationalStateDerivative3DOF:
        """Verilen numerical noktada kabul edilmiş 3DOF türevini üret."""

        ...


def _require_derivative(
    value: object,
    *,
    stage_name: str,
) -> TranslationalStateDerivative3DOF:
    """Callable dönüşünü coercion olmadan accepted derivative türüne sınırla."""

    if not isinstance(value, TranslationalStateDerivative3DOF):
        raise TypeError(
            f"{stage_name} derivative_function return must be "
            "TranslationalStateDerivative3DOF"
        )
    return value


class ClassicalRK4Integrator3DOF:
    """Parametresiz/stateless klasik explicit fixed-step RK4 integratorü."""

    __slots__ = ()

    def step(
        self,
        *,
        point: IntegrationPoint3DOF,
        config: FixedStepConfig,
        derivative_function: DerivativeFunction3DOF,
    ) -> IntegrationPoint3DOF:
        """Tam bir klasik RK4 adımı uygula ve yalnız kabul edilen ``(t,y)`` döndür."""

        time_s = point.time_s
        step_size_s = config.step_size_s
        half_step_s = step_size_s / 2.0
        half_time_s = time_s + half_step_s
        full_time_s = time_s + step_size_s

        for field_name, value in (
            ("half_time_s", half_time_s),
            ("full_time_s", full_time_s),
        ):
            if not isfinite(value):
                raise NumericalIntegrationError(
                    error_code="NONFINITE_RK4_STAGE_TIME",
                    field_name=field_name,
                    value=value,
                )

        algebra = TranslationalStateAlgebra3DOF()
        original_state = point.state

        k1 = _require_derivative(
            derivative_function(time_s=time_s, state=original_state),
            stage_name="k1",
        )
        state_2 = algebra.add_scaled_derivative(
            state=original_state,
            derivative=k1,
            scale_s=half_step_s,
        )
        k2 = _require_derivative(
            derivative_function(time_s=half_time_s, state=state_2),
            stage_name="k2",
        )
        state_3 = algebra.add_scaled_derivative(
            state=original_state,
            derivative=k2,
            scale_s=half_step_s,
        )
        k3 = _require_derivative(
            derivative_function(time_s=half_time_s, state=state_3),
            stage_name="k3",
        )
        state_4 = algebra.add_scaled_derivative(
            state=original_state,
            derivative=k3,
            scale_s=step_size_s,
        )
        k4 = _require_derivative(
            derivative_function(time_s=full_time_s, state=state_4),
            stage_name="k4",
        )

        weighted_derivative = TranslationalStateDerivative3DOF(
            position_derivative_world_m_s=(
                k1.position_derivative_world_m_s
                + 2.0 * k2.position_derivative_world_m_s
                + 2.0 * k3.position_derivative_world_m_s
                + k4.position_derivative_world_m_s
            )
            / 6.0,
            velocity_derivative_world_m_s2=(
                k1.velocity_derivative_world_m_s2
                + 2.0 * k2.velocity_derivative_world_m_s2
                + 2.0 * k3.velocity_derivative_world_m_s2
                + k4.velocity_derivative_world_m_s2
            )
            / 6.0,
        )
        next_state = algebra.add_scaled_derivative(
            state=original_state,
            derivative=weighted_derivative,
            scale_s=step_size_s,
        )

        # k4'ün state_4'ü yalnız trial state'tir; accepted endpoint next_state'tir.
        # Endpoint physics gerekirse üst orchestration katmanı ayrıca değerlendirir.
        return IntegrationPoint3DOF(time_s=full_time_s, state=next_state)
