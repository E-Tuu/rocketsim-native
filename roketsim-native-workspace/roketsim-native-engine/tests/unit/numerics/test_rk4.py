"""NAT-017 klasik fixed-step RK4 focused contract ve V&V testleri."""

from inspect import Parameter, signature

import numpy as np
import pytest

from roketsim_native.dynamics.initial_state import TranslationalState3DOF
from roketsim_native.dynamics.translational import TranslationalStateDerivative3DOF
from roketsim_native.numerics import rk4
from roketsim_native.numerics.fixed_step import (
    FixedStepConfig,
    IntegrationPoint3DOF,
    NumericalIntegrationError,
    TranslationalStateAlgebra3DOF,
)
from roketsim_native.numerics.rk4 import (
    ClassicalRK4Integrator3DOF,
    DerivativeFunction3DOF,
)


def make_state(position=(0.0, 0.0, 0.0), velocity=(0.0, 0.0, 0.0)):
    return TranslationalState3DOF(position, velocity)


def make_derivative(position_rate, velocity_rate):
    return TranslationalStateDerivative3DOF(position_rate, velocity_rate)


def test_public_contract_reuses_accepted_types_and_is_keyword_only_stateless():
    """RK4-T01..T06: minimal callable ve accepted numerical/state API reuse."""
    call_parameters = signature(DerivativeFunction3DOF.__call__).parameters
    assert tuple(call_parameters) == ("self", "time_s", "state")
    assert all(
        parameter.kind is Parameter.KEYWORD_ONLY
        for name, parameter in call_parameters.items()
        if name != "self"
    )
    integrator = ClassicalRK4Integrator3DOF()
    assert not signature(ClassicalRK4Integrator3DOF).parameters
    assert not hasattr(integrator, "__dict__")
    step_parameters = signature(integrator.step).parameters
    assert tuple(step_parameters) == ("point", "config", "derivative_function")
    assert all(p.kind is Parameter.KEYWORD_ONLY for p in step_parameters.values())
    assert rk4.IntegrationPoint3DOF is IntegrationPoint3DOF
    assert rk4.FixedStepConfig is FixedStepConfig
    assert rk4.TranslationalStateAlgebra3DOF is TranslationalStateAlgebra3DOF


def test_constant_derivative_vv_exact_four_calls_and_fixed_endpoint_time():
    """RK4-T14/T17/T18: constant derivative ve dört çağrı frozen V&V."""
    calls = []

    def derivative_function(*, time_s, state):
        calls.append((time_s, state))
        return make_derivative((2.0, 0.0, 0.0), (3.0, 0.0, 0.0))

    point = IntegrationPoint3DOF(2.0, make_state((1, 0, 0), (5, 0, 0)))
    result = ClassicalRK4Integrator3DOF().step(
        point=point,
        config=FixedStepConfig(0.2),
        derivative_function=derivative_function,
    )
    assert len(calls) == 4
    assert [call[0] for call in calls] == [2.0, 2.1, 2.1, 2.2]
    assert result.time_s == 2.2
    np.testing.assert_allclose(result.state.position_world_m, (1.4, 0, 0))
    np.testing.assert_allclose(result.state.velocity_world_m_s, (5.6, 0, 0))


def test_constant_acceleration_distinguishes_rk4_from_euler():
    """RK4-T19: constant acceleration analytical trajectory V&V."""
    def derivative_function(*, time_s, state):
        del time_s
        return make_derivative(state.velocity_world_m_s, (0.0, 0.0, -10.0))

    result = ClassicalRK4Integrator3DOF().step(
        point=IntegrationPoint3DOF(0.0, make_state((0, 0, 100), (0, 0, 20))),
        config=FixedStepConfig(0.5),
        derivative_function=derivative_function,
    )
    np.testing.assert_allclose(result.state.position_world_m, (0, 0, 108.75))
    np.testing.assert_allclose(result.state.velocity_world_m_s, (0, 0, 15))


def test_harmonic_oscillator_classical_rk4_vv():
    """RK4-T15/T20: exact 1:2:2:1/6 weighting harmonic oscillator V&V."""
    def derivative_function(*, time_s, state):
        del time_s
        return make_derivative(
            state.velocity_world_m_s,
            (-state.position_world_m[0], 0.0, 0.0),
        )

    result = ClassicalRK4Integrator3DOF().step(
        point=IntegrationPoint3DOF(0.0, make_state((1, 0, 0), (0, 0, 0))),
        config=FixedStepConfig(0.1),
        derivative_function=derivative_function,
    )
    assert result.state.position_world_m[0] == pytest.approx(0.9950041666666667)
    assert result.state.velocity_world_m_s[0] == pytest.approx(-0.0998333333333333)


def test_stage_times_states_original_base_and_k4_not_endpoint():
    """RK4-T07..T13/T21/T22: stage spy original-y ve k4 trial ayrımını kanıtlar."""
    calls = []

    def derivative_function(*, time_s, state):
        calls.append(
            (time_s, state.position_world_m.copy(), state.velocity_world_m_s.copy())
        )
        return make_derivative(
            state.velocity_world_m_s,
            (-state.position_world_m[0], 0.0, 0.0),
        )

    original = make_state((1, 0, 0), (0.5, 0, 0))
    result = ClassicalRK4Integrator3DOF().step(
        point=IntegrationPoint3DOF(2.0, original),
        config=FixedStepConfig(0.4),
        derivative_function=derivative_function,
    )
    assert len(calls) == 4
    assert [call[0] for call in calls] == [2.0, 2.2, 2.2, 2.4]
    np.testing.assert_allclose(calls[0][1], (1.0, 0, 0))
    np.testing.assert_allclose(calls[0][2], (0.5, 0, 0))
    np.testing.assert_allclose(calls[1][1], (1.1, 0, 0))
    np.testing.assert_allclose(calls[1][2], (0.3, 0, 0))
    np.testing.assert_allclose(calls[2][1], (1.06, 0, 0))
    np.testing.assert_allclose(calls[2][2], (0.28, 0, 0))
    np.testing.assert_allclose(calls[3][1], (1.112, 0, 0))
    np.testing.assert_allclose(calls[3][2], (0.076, 0, 0))
    assert calls[3][1][0] != pytest.approx(result.state.position_world_m[0])
    assert calls[3][2][0] != pytest.approx(result.state.velocity_world_m_s[0])


def test_every_trial_and_final_state_use_nat016_algebra(monkeypatch):
    """RK4-T06/T09/T11/T13/T16: üç trial ve final yalnız StateAlgebra yolundadır."""
    accepted_algebra = TranslationalStateAlgebra3DOF
    records = []

    class SpyAlgebra:
        def add_scaled_derivative(self, *, state, derivative, scale_s):
            records.append((state, derivative, scale_s))
            return accepted_algebra().add_scaled_derivative(
                state=state,
                derivative=derivative,
                scale_s=scale_s,
            )

    monkeypatch.setattr(rk4, "TranslationalStateAlgebra3DOF", SpyAlgebra)
    original = make_state((1, 0, 0), (0, 0, 0))

    def derivative_function(*, time_s, state):
        del time_s
        return make_derivative(state.velocity_world_m_s, (-state.position_world_m[0], 0, 0))

    ClassicalRK4Integrator3DOF().step(
        point=IntegrationPoint3DOF(0.0, original),
        config=FixedStepConfig(0.2),
        derivative_function=derivative_function,
    )
    assert len(records) == 4
    assert all(record[0] is original for record in records)
    assert [record[2] for record in records] == [0.1, 0.1, 0.2, 0.2]


class SentinelDerivativeError(RuntimeError):
    pass


@pytest.mark.parametrize("failure_call", [1, 2, 3, 4])
def test_upstream_stage_error_propagates_unchanged(failure_call):
    """RK4-T23: k1..k4 upstream error identity/family wrapping olmadan korunur."""
    sentinel = SentinelDerivativeError(f"stage {failure_call}")
    calls = 0

    def derivative_function(*, time_s, state):
        nonlocal calls
        del time_s, state
        calls += 1
        if calls == failure_call:
            raise sentinel
        return make_derivative((0, 0, 0), (0, 0, 0))

    with pytest.raises(SentinelDerivativeError) as captured:
        ClassicalRK4Integrator3DOF().step(
            point=IntegrationPoint3DOF(0.0, make_state()),
            config=FixedStepConfig(0.1),
            derivative_function=derivative_function,
        )
    assert captured.value is sentinel
    assert calls == failure_call


@pytest.mark.parametrize("bad_result", [None, (1, 2), np.zeros(6)])
def test_wrong_derivative_return_is_type_error_without_coercion(bad_result):
    """RK4-T01/T23: callable yanlış return type'ı normal API TypeError'ıdır."""
    def derivative_function(*, time_s, state):
        del time_s, state
        return bad_result

    with pytest.raises(TypeError):
        ClassicalRK4Integrator3DOF().step(
            point=IntegrationPoint3DOF(0.0, make_state()),
            config=FixedStepConfig(0.1),
            derivative_function=derivative_function,
        )


@pytest.mark.parametrize(
    "time_s,step_size_s,expected_field",
    [
        (1.5e308, 1.0e308, "half_time_s"),
        (1.0e308, 1.0e308, "full_time_s"),
    ],
)
def test_nonfinite_stage_time_is_structured_and_precedes_derivative_calls(
    time_s, step_size_s, expected_field
):
    """RK4-T24: finite t/h overflow'u structured, repairsiz reddedilir."""
    calls = 0

    def derivative_function(*, time_s, state):
        nonlocal calls
        del time_s, state
        calls += 1
        return make_derivative((0, 0, 0), (0, 0, 0))

    with pytest.raises(NumericalIntegrationError) as captured:
        ClassicalRK4Integrator3DOF().step(
            point=IntegrationPoint3DOF(time_s, make_state()),
            config=FixedStepConfig(step_size_s),
            derivative_function=derivative_function,
        )
    assert captured.value.error_code == "NONFINITE_RK4_STAGE_TIME"
    assert captured.value.field_name == expected_field
    assert not np.isfinite(captured.value.value)
    assert calls == 0


def test_inputs_unmodified_output_independent_read_only_and_deterministic():
    """RK4-T02/T25: input immutable, output independent/read-only/deterministic."""
    state = make_state((1, 2, 3), (4, 5, 6))
    point = IntegrationPoint3DOF(-1.0, state)
    config = FixedStepConfig(0.1)
    snapshots = (state.position_world_m.copy(), state.velocity_world_m_s.copy())

    def derivative_function(*, time_s, state):
        del time_s
        return make_derivative(state.velocity_world_m_s, (1, 2, 3))

    first = ClassicalRK4Integrator3DOF().step(
        point=point, config=config, derivative_function=derivative_function
    )
    second = ClassicalRK4Integrator3DOF().step(
        point=point, config=config, derivative_function=derivative_function
    )
    np.testing.assert_array_equal(state.position_world_m, snapshots[0])
    np.testing.assert_array_equal(state.velocity_world_m_s, snapshots[1])
    assert point.time_s == -1.0
    assert config.step_size_s == 0.1
    assert first is not point and first.state is not state
    assert not first.state.position_world_m.flags.writeable
    assert not first.state.velocity_world_m_s.flags.writeable
    np.testing.assert_array_equal(first.state.position_world_m, second.state.position_world_m)
    np.testing.assert_array_equal(first.state.velocity_world_m_s, second.state.velocity_world_m_s)


def test_no_final_time_clipping_or_physics_event_scope():
    """RK4-T17/T26: fixed h aynen kullanılır; physics/adaptive/event API yoktur."""
    def derivative_function(*, time_s, state):
        del time_s, state
        return make_derivative((0, 0, 0), (0, 0, 0))

    result = ClassicalRK4Integrator3DOF().step(
        point=IntegrationPoint3DOF(0.95, make_state()),
        config=FixedStepConfig(0.1),
        derivative_function=derivative_function,
    )
    assert result.time_s == 0.95 + 0.1
    assert rk4.__all__ == ("DerivativeFunction3DOF", "ClassicalRK4Integrator3DOF")
    for forbidden in (
        "PhysicsEvaluator3DOF",
        "SimulationEngine",
        "AdaptiveIntegrator",
        "Event",
        "RK4StepResult",
        "RK4StageResult",
    ):
        assert not hasattr(rk4, forbidden)
