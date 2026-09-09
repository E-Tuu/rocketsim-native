"""NAT-021 immutable SimulationResult3DOF facade contract ve V&V testleri."""

from dataclasses import FrozenInstanceError, fields
from inspect import Parameter, signature
from types import SimpleNamespace

import pytest

from roketsim_native.dynamics.initial_state import TranslationalState3DOF
from roketsim_native.numerics.fixed_step import IntegrationPoint3DOF
from roketsim_native.simulation import result as result_module
from roketsim_native.simulation.engine import (
    SimulationExecution3DOF,
    SimulationTerminationReason,
)
from roketsim_native.simulation.events import (
    FlightEventOccurrence3DOF,
    FlightEventType,
)
from roketsim_native.simulation.physics import PhysicsEvaluationResult3DOF
from roketsim_native.simulation.recorder import (
    RecordedFlightData3DOF,
    RecordedFlightSample3DOF,
)
from roketsim_native.simulation.result import (
    SimulationResult3DOF,
    SimulationResultError,
)


def point(time_s):
    return IntegrationPoint3DOF(
        time_s,
        TranslationalState3DOF((time_s, 0.0, 0.0), (1.0, 0.0, 0.0)),
    )


def physics(marker=0.0):
    placeholder = SimpleNamespace(marker=marker)
    return PhysicsEvaluationResult3DOF(
        environment_altitude_m=marker,
        motor_time_s=marker,
        atmosphere=placeholder,
        air_properties=placeholder,
        air_mass_velocity_world_m_s=(0.0, 0.0, 0.0),
        relative_flow=(0.0, 0.0, 0.0),
        flight_conditions=placeholder,
        motor_thrust_state=placeholder,
        motor_mass_properties=placeholder,
        rocket_mass_properties=placeholder,
        basic_drag=placeholder,
        gravity_acceleration_world_m_s2=(0.0, 0.0, -9.8),
        dynamics=placeholder,
    )


def sample(time_s):
    return RecordedFlightSample3DOF(point(time_s), physics(time_s))


def event(event_type, time_s):
    return FlightEventOccurrence3DOF(
        event_type,
        time_s,
        0.5,
        TranslationalState3DOF((time_s, 0.0, 0.0), (0.0, 0.0, 0.0)),
    )


def execution(
    *,
    samples,
    events=(),
    reason=SimulationTerminationReason.MAXIMUM_STEPS_REACHED,
    steps=0,
):
    return SimulationExecution3DOF(
        RecordedFlightData3DOF(tuple(samples), tuple(events)), reason, steps
    )


def assert_error(captured, *, code):
    assert captured.value.error_code == code
    assert captured.value.field_name
    assert hasattr(captured.value, "value")


def test_result_is_frozen_slotted_single_authority_and_forwards_exact_objects():
    """RES-T01..T06/T29: execution tek field; bütün accepted authority'ler exact."""
    samples = (sample(2.5), sample(2.6), sample(2.7))
    events = (event(FlightEventType.BURNOUT, 2.55),)
    accepted = execution(samples=samples, events=events, steps=2)
    result = SimulationResult3DOF(accepted)
    assert [field.name for field in fields(result)] == ["execution"]
    assert result.execution is accepted
    assert result.recorded_data is accepted.recorded_data
    assert result.samples is accepted.recorded_data.samples
    assert result.events is accepted.recorded_data.events
    assert result.termination_reason is accepted.termination_reason
    assert result.steps_performed == accepted.steps_performed
    assert result.initial_sample is samples[0]
    assert result.last_accepted_sample is samples[-1]
    assert not hasattr(result, "__dict__")
    with pytest.raises(FrozenInstanceError):
        result.execution = accepted


def test_completed_result_rejects_empty_samples_with_structured_error():
    """RES-T07: NAT-019 empty snapshot geçerli olsa da completed result değildir."""
    with pytest.raises(SimulationResultError) as captured:
        SimulationResult3DOF(execution(samples=()))
    assert_error(captured, code="EMPTY_RECORDED_SAMPLES")


def test_generic_event_query_is_keyword_only_complete_and_order_preserving():
    """RES-T11: generic query ambiguity yaratmadan tüm exact occurrence'ları döndürür."""
    apogee_1 = event(FlightEventType.APOGEE, 4.1)
    apogee_2 = event(FlightEventType.APOGEE, 4.2)
    events = (
        event(FlightEventType.BURNOUT, 1.4),
        apogee_1,
        apogee_2,
    )
    result = SimulationResult3DOF(execution(samples=(sample(5.0),), events=events))
    parameter = signature(result.events_of_type).parameters["event_type"]
    assert parameter.kind is Parameter.KEYWORD_ONLY
    assert result.events_of_type(event_type=FlightEventType.APOGEE) == (
        apogee_1,
        apogee_2,
    )
    assert result.events_of_type(event_type=FlightEventType.GROUND) == ()


def test_named_event_absent_unique_and_ambiguous_semantics():
    """RES-T12..T14: named lookup None/exact/error cardinality sözleşmesini korur."""
    burnout = event(FlightEventType.BURNOUT, 1.4)
    result = SimulationResult3DOF(
        execution(samples=(sample(5.0),), events=(burnout,))
    )
    assert result.burnout_event is burnout
    assert result.apogee_event is None
    assert result.ground_event is None

    ambiguous = SimulationResult3DOF(
        execution(
            samples=(sample(5.0),),
            events=(event(FlightEventType.APOGEE, 4.1), event(FlightEventType.APOGEE, 4.2)),
        )
    )
    with pytest.raises(SimulationResultError) as captured:
        _ = ambiguous.apogee_event
    assert_error(captured, code="AMBIGUOUS_EVENT_HISTORY")


def test_terminal_execution_uses_is_terminal_and_preserves_interior_distinction(monkeypatch):
    """RES-T15/T20/T22: classification property authority; event time != last sample."""
    monkeypatch.setattr(
        FlightEventOccurrence3DOF,
        "is_terminal",
        property(lambda self: self.event_type is FlightEventType.BURNOUT),
    )
    terminal = event(FlightEventType.BURNOUT, 9.163)
    result = SimulationResult3DOF(
        execution(
            samples=(sample(9.0), sample(9.10)),
            events=(terminal,),
            reason=SimulationTerminationReason.TERMINAL_EVENT,
            steps=1,
        )
    )
    assert result.terminal_event is terminal
    assert result.last_accepted_sample.point.time_s == 9.10
    assert result.termination_time_s == 9.163
    assert all(item.point.time_s != 9.163 for item in result.samples)


def test_terminal_exactly_at_accepted_endpoint_keeps_distinct_authorities():
    """RES-T20/T22: equal timestamps event ile accepted RK4 sample'ı birleştirmez."""
    terminal = event(FlightEventType.GROUND, 9.20)
    result = SimulationResult3DOF(
        execution(
            samples=(sample(9.0), sample(9.20)),
            events=(terminal,),
            reason=SimulationTerminationReason.TERMINAL_EVENT,
        )
    )
    assert result.last_accepted_sample.point.time_s == result.termination_time_s == 9.20
    assert result.terminal_event is terminal


@pytest.mark.parametrize(
    ("reason", "events", "expected_code"),
    [
        (
            SimulationTerminationReason.TERMINAL_EVENT,
            (),
            "TERMINATION_EVENT_MISSING",
        ),
        (
            SimulationTerminationReason.TERMINAL_EVENT,
            (
                event(FlightEventType.GROUND, 2.0),
                event(FlightEventType.GROUND, 2.1),
            ),
            "MULTIPLE_TERMINAL_EVENTS",
        ),
        (
            SimulationTerminationReason.MAXIMUM_STEPS_REACHED,
            (event(FlightEventType.GROUND, 2.0),),
            "UNEXPECTED_TERMINAL_EVENT",
        ),
    ],
)
def test_termination_integrity_failures_are_explicit(reason, events, expected_code):
    """RES-T16..T19: lifecycle reason ile terminal occurrence cardinality tutarlı."""
    with pytest.raises(SimulationResultError) as captured:
        SimulationResult3DOF(
            execution(samples=(sample(1.0),), events=events, reason=reason)
        )
    assert_error(captured, code=expected_code)


def test_maximum_step_termination_uses_last_accepted_sample():
    """RES-T19/T21: terminal yokken result zamanı last accepted point authority'sidir."""
    result = SimulationResult3DOF(
        execution(samples=(sample(2.5), sample(6.0)), steps=35)
    )
    assert result.terminal_event is None
    assert result.termination_time_s == 6.0


@pytest.mark.parametrize(
    ("initial", "last", "terminal", "expected_duration"),
    [
        (2.5, 12.75, None, 10.25),
        (2.5, 10.0, 10.08, 7.58),
    ],
)
def test_duration_is_relative_to_start_and_uses_terminal_event_when_present(
    initial, last, terminal, expected_duration
):
    """RES-T23/T24: nonzero ignition ve interior terminal exact duration authority'si."""
    if terminal is None:
        accepted = execution(samples=(sample(initial), sample(last)))
    else:
        accepted = execution(
            samples=(sample(initial), sample(last)),
            events=(event(FlightEventType.GROUND, terminal),),
            reason=SimulationTerminationReason.TERMINAL_EVENT,
        )
    result = SimulationResult3DOF(accepted)
    assert result.simulation_duration_s == pytest.approx(expected_duration)


def test_termination_before_initial_fails_without_absolute_value_or_clamp():
    """RES-T25: negative derived duration semantic error'dır, repair edilmez."""
    with pytest.raises(SimulationResultError) as captured:
        SimulationResult3DOF(
            execution(
                samples=(sample(2.5),),
                events=(event(FlightEventType.GROUND, 2.4),),
                reason=SimulationTerminationReason.TERMINAL_EVENT,
            )
        )
    assert_error(captured, code="INVALID_TERMINATION_TIME")
    assert captured.value.value == 2.4


def test_public_scope_has_no_ambiguous_aliases_analytics_or_operations():
    """RES-T10/T26..T28/T30: facade lifecycle query dışında scope genişletmez."""
    accepted = SimulationResult3DOF(execution(samples=(sample(0.0),)))
    forbidden = (
        "final_state", "final_sample", "flight_time_s", "apogee_altitude_m",
        "maximum_altitude_m", "impact_speed", "maximum_speed",
        "maximum_acceleration", "maximum_mach", "maximum_dynamic_pressure",
        "maximum_q", "run", "evaluate", "step", "detect", "record_sample",
        "export_csv", "export_json", "plot",
    )
    assert all(not hasattr(accepted, name) for name in forbidden)
    assert result_module.__all__ == ("SimulationResultError", "SimulationResult3DOF")
