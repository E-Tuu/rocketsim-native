"""NAT-016 fixed-step configuration, point ve state-algebra focused testleri."""

from dataclasses import FrozenInstanceError, fields
from inspect import Parameter, signature

import numpy as np
import pytest

from roketsim_native.dynamics.initial_state import TranslationalState3DOF
from roketsim_native.dynamics.translational import TranslationalStateDerivative3DOF
from roketsim_native.numerics import fixed_step
from roketsim_native.numerics.fixed_step import (
    FixedStepConfig,
    IntegrationPoint3DOF,
    NumericalIntegrationError,
    TranslationalStateAlgebra3DOF,
)


def state():
    return TranslationalState3DOF((1.0, 2.0, 3.0), (4.0, 5.0, 6.0))


def derivative():
    return TranslationalStateDerivative3DOF(
        (4.0, 5.0, 6.0),
        (1.0, 2.0, 3.0),
    )


def test_fixed_step_contract_is_frozen_slotted_mandatory_and_structured():
    """NUM-T01/T02/T04/T19: defaultsuz value object ve structured hata."""
    parameter = signature(FixedStepConfig).parameters["step_size_s"]
    assert parameter.default is Parameter.empty
    config = FixedStepConfig(0.5)
    assert not hasattr(config, "__dict__")
    with pytest.raises(FrozenInstanceError):
        config.step_size_s = 1.0
    with pytest.raises(NumericalIntegrationError) as captured:
        FixedStepConfig(0.0)
    assert captured.value.error_code == "INVALID_STEP_SIZE"
    assert captured.value.field_name == "step_size_s"
    assert captured.value.value == 0.0


@pytest.mark.parametrize(
    "step_size",
    [np.nextafter(0.0, 1.0), 0.001, 0.01, 1.0, 1.0e308],
    ids=("smallest-positive", "fixture-001", "fixture-01", "one", "very-large"),
)
def test_any_positive_finite_step_is_preserved_exactly(step_size):
    """NUM-T03/T07: min/max/default/clamp/final-time adjustment yoktur."""
    assert FixedStepConfig(step_size).step_size_s == step_size


@pytest.mark.parametrize("step_size", [-1.0, -1.0e-300])
def test_negative_finite_step_has_invalid_step_error(step_size):
    """NUM-T05: finite negative step structured invalid policy'dir."""
    with pytest.raises(NumericalIntegrationError) as captured:
        FixedStepConfig(step_size)
    assert captured.value.error_code == "INVALID_STEP_SIZE"
    assert captured.value.field_name == "step_size_s"
    assert captured.value.value == step_size


@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf")])
def test_nonfinite_step_uses_generic_value_error(value):
    """NUM-T06: raw non-finite step NumericalIntegrationError değildir."""
    with pytest.raises(ValueError) as captured:
        FixedStepConfig(value)
    assert type(captured.value) is ValueError


def test_integration_point_contract_and_negative_time_vv():
    """NUM-T08/T09/T10/T12: yalnız finite numerical (t,y), time state dışında."""
    accepted_state = state()
    point = IntegrationPoint3DOF(-3.25, accepted_state)
    assert [field.name for field in fields(IntegrationPoint3DOF)] == ["time_s", "state"]
    assert point.time_s == -3.25
    assert point.state is accepted_state
    assert not hasattr(point, "__dict__")
    assert not hasattr(point.state, "time_s")
    with pytest.raises(FrozenInstanceError):
        point.time_s = 0.0


@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf")])
def test_nonfinite_integration_point_time_uses_generic_value_error(value):
    """NUM-T11: numerical time finite olmalı; sign restriction yoktur."""
    with pytest.raises(ValueError) as captured:
        IntegrationPoint3DOF(value, state())
    assert type(captured.value) is ValueError


@pytest.mark.parametrize(
    "scale,expected_position,expected_velocity",
    [
        (0.5, (3.0, 4.5, 6.0), (4.5, 6.0, 7.5)),
        (0.0, (1.0, 2.0, 3.0), (4.0, 5.0, 6.0)),
        (-0.5, (-1.0, -0.5, 0.0), (3.5, 4.0, 4.5)),
    ],
    ids=("positive-scale", "zero-scale", "negative-scale"),
)
def test_state_algebra_frozen_vv(scale, expected_position, expected_velocity):
    """NUM-T13/T14/T15: exact y+scale*k; negative ve zero matematiksel geçerlidir."""
    original_state = state()
    original_derivative = derivative()
    result = TranslationalStateAlgebra3DOF().add_scaled_derivative(
        state=original_state,
        derivative=original_derivative,
        scale_s=scale,
    )
    np.testing.assert_array_equal(result.position_world_m, expected_position)
    np.testing.assert_array_equal(result.velocity_world_m_s, expected_velocity)
    assert result is not original_state
    assert result.position_world_m is not original_state.position_world_m
    assert result.velocity_world_m_s is not original_state.velocity_world_m_s
    assert not result.position_world_m.flags.writeable
    assert not result.velocity_world_m_s.flags.writeable


@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf")])
def test_nonfinite_scale_uses_generic_value_error(value):
    """NUM-T16: scale raw non-finite ise generic finite-validation yolu kullanılır."""
    with pytest.raises(ValueError) as captured:
        TranslationalStateAlgebra3DOF().add_scaled_derivative(
            state=state(),
            derivative=derivative(),
            scale_s=value,
        )
    assert type(captured.value) is ValueError


def test_algebra_contract_inputs_immutable_and_deterministic():
    """NUM-T17: parameterless/stateless keyword-only algebra inputs'a dokunmaz."""
    algebra = TranslationalStateAlgebra3DOF()
    assert not signature(TranslationalStateAlgebra3DOF).parameters
    assert not hasattr(algebra, "__dict__")
    parameters = signature(algebra.add_scaled_derivative).parameters
    assert tuple(parameters) == ("state", "derivative", "scale_s")
    assert all(
        parameter.kind is Parameter.KEYWORD_ONLY
        and parameter.default is Parameter.empty
        for parameter in parameters.values()
    )
    original_state = state()
    original_derivative = derivative()
    snapshots = [
        original_state.position_world_m.copy(),
        original_state.velocity_world_m_s.copy(),
        original_derivative.position_derivative_world_m_s.copy(),
        original_derivative.velocity_derivative_world_m_s2.copy(),
    ]
    first = algebra.add_scaled_derivative(
        state=original_state, derivative=original_derivative, scale_s=0.5
    )
    second = algebra.add_scaled_derivative(
        state=original_state, derivative=original_derivative, scale_s=0.5
    )
    np.testing.assert_array_equal(first.position_world_m, second.position_world_m)
    np.testing.assert_array_equal(first.velocity_world_m_s, second.velocity_world_m_s)
    for vector, snapshot in zip(
        (
            original_state.position_world_m,
            original_state.velocity_world_m_s,
            original_derivative.position_derivative_world_m_s,
            original_derivative.velocity_derivative_world_m_s2,
        ),
        snapshots,
        strict=True,
    ):
        np.testing.assert_array_equal(vector, snapshot)


def test_public_scope_is_state_algebra_not_integrator():
    """NUM-T07/T18: PhysicsEvaluator/Euler/RK/adaptive/event API veya policy yoktur."""
    assert fixed_step.__all__ == (
        "NumericalIntegrationError",
        "FixedStepConfig",
        "IntegrationPoint3DOF",
        "TranslationalStateAlgebra3DOF",
    )
    config_fields = {field.name for field in fields(FixedStepConfig)}
    point_fields = {field.name for field in fields(IntegrationPoint3DOF)}
    assert config_fields == {"step_size_s"}
    assert point_fields == {"time_s", "state"}
    forbidden = {
        "minimum_step_size_s",
        "maximum_step_size_s",
        "default_step_size_s",
        "end_time_s",
        "final_step_size_s",
        "step_index",
        "derivative",
        "physics_result",
        "event",
    }
    assert forbidden.isdisjoint(config_fields | point_fields)
    assert not hasattr(fixed_step, "PhysicsEvaluator3DOF")
    assert not hasattr(fixed_step, "EulerIntegrator")
    assert not hasattr(fixed_step, "RK4Integrator")
    assert not hasattr(fixed_step, "AdaptiveIntegrator")
