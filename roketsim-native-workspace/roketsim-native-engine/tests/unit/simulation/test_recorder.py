"""NAT-019 passive append-only 3DOF recorder contract ve V&V testleri."""

from dataclasses import FrozenInstanceError, fields
from inspect import Parameter, signature
from types import SimpleNamespace

import numpy as np
import pytest

from roketsim_native.dynamics.initial_state import TranslationalState3DOF
from roketsim_native.numerics.fixed_step import IntegrationPoint3DOF
from roketsim_native.simulation import recorder
from roketsim_native.simulation.events import (
    FlightEventOccurrence3DOF,
    FlightEventType,
)
from roketsim_native.simulation.physics import PhysicsEvaluationResult3DOF
from roketsim_native.simulation.recorder import (
    FlightRecorder3DOF,
    FlightRecordingError,
    RecordedFlightData3DOF,
    RecordedFlightSample3DOF,
)


def point(time, *, z=0.0):
    return IntegrationPoint3DOF(
        time,
        TranslationalState3DOF((time, 0.0, z), (1.0, 0.0, 0.0)),
    )


def physics(marker=0.0):
    """Recorder'ın içeriğini hesaplamadığı accepted result fixture'ı."""
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


def event(event_type, time, *, z=0.0):
    return FlightEventOccurrence3DOF(
        event_type=event_type,
        time_s=time,
        interpolation_fraction=0.5,
        estimated_state=TranslationalState3DOF((time, 0.0, z), (0.0, 0.0, 0.0)),
    )


def record_sample(target, time):
    accepted_point = point(time)
    accepted_physics = physics(time)
    target.record_sample(point=accepted_point, physics=accepted_physics)
    return accepted_point, accepted_physics


def test_recorded_models_are_frozen_slotted_exact_authority_shapes():
    """REC-T01..T05/T10: sample point+physics only; snapshot exact tuple fields."""
    accepted_point = point(0.0)
    accepted_physics = physics()
    sample = RecordedFlightSample3DOF(accepted_point, accepted_physics)
    data = RecordedFlightData3DOF((sample,), ())
    assert [field.name for field in fields(RecordedFlightSample3DOF)] == [
        "point", "physics"
    ]
    assert [field.name for field in fields(RecordedFlightData3DOF)] == [
        "samples", "events"
    ]
    assert sample.point is accepted_point and sample.physics is accepted_physics
    assert type(data.samples) is tuple and type(data.events) is tuple
    for value in (sample, data):
        assert not hasattr(value, "__dict__")
        with pytest.raises(FrozenInstanceError):
            setattr(value, fields(value)[0].name, None)
    forbidden = {
        "time_s", "position", "velocity", "mass_kg", "thrust_N", "mach",
        "reynolds_number", "dynamic_pressure_Pa", "cd0", "acceleration",
    }
    assert forbidden.isdisjoint(field.name for field in fields(sample))


def test_recorder_api_is_parameterless_stateful_append_only_and_keyword_only():
    """REC-T06/T07/T12: yalnız iki history collection'lı explicit stateful API."""
    target = FlightRecorder3DOF()
    assert not signature(FlightRecorder3DOF).parameters
    assert not hasattr(target, "__dict__")
    assert set(FlightRecorder3DOF.__slots__) == {"_samples", "_events"}
    for method_name, expected in (
        ("record_sample", ("point", "physics")),
        ("record_events", ("events",)),
    ):
        parameters = signature(getattr(target, method_name)).parameters
        assert tuple(parameters) == expected
        assert all(p.kind is Parameter.KEYWORD_ONLY for p in parameters.values())


def test_sample_recording_vv_and_first_arbitrary_finite_time():
    """REC-T08/T09: ilk finite zaman serbest, sonra strict artan order aynen korunur."""
    negative_time_recorder = FlightRecorder3DOF()
    first_point, first_physics = record_sample(negative_time_recorder, -2.0)
    negative_sample = negative_time_recorder.snapshot().samples[0]
    assert negative_sample.point is first_point
    assert negative_sample.physics is first_physics

    target = FlightRecorder3DOF()
    for time in (0.00, 0.01, 0.02):
        record_sample(target, time)
    snapshot = target.snapshot()
    assert [sample.point.time_s for sample in snapshot.samples] == [0.0, 0.01, 0.02]


@pytest.mark.parametrize("invalid_time", [0.01, 0.005])
def test_equal_or_reversed_sample_time_fails_without_mutation(invalid_time):
    """REC-T10/T11: overwrite/sort yok; failed insertion history'yi değiştirmez."""
    target = FlightRecorder3DOF()
    record_sample(target, 0.0)
    record_sample(target, 0.01)
    before = target.snapshot()
    with pytest.raises(FlightRecordingError) as captured:
        target.record_sample(point=point(invalid_time), physics=physics(invalid_time))
    assert captured.value.error_code == "NON_INCREASING_SAMPLE_TIME"
    assert captured.value.field_name == "point.time_s"
    assert captured.value.value == invalid_time
    assert target.snapshot() == before


def test_empty_equal_time_and_supplied_event_order_are_preserved():
    """REC-T13/T14/T17: empty no-op; simultaneous events accepted, not re-sorted."""
    target = FlightRecorder3DOF()
    target.record_events(events=())
    assert target.snapshot().events == ()
    supplied = (
        event(FlightEventType.GROUND, 4.0),
        event(FlightEventType.BURNOUT, 4.0),
        event(FlightEventType.APOGEE, 4.0),
    )
    target.record_events(events=supplied)
    assert target.snapshot().events == supplied


def test_event_chronology_vv_and_duplicate_types_are_not_deduplicated():
    """REC-T14/T17/T18: non-decreasing exact order ve type-based dedupe yok."""
    target = FlightRecorder3DOF()
    supplied = (
        event(FlightEventType.BURNOUT, 1.43),
        event(FlightEventType.BURNOUT, 1.43),
        event(FlightEventType.APOGEE, 4.12),
        event(FlightEventType.GROUND, 8.91),
    )
    target.record_events(events=supplied)
    assert target.snapshot().events == supplied


def test_invalid_event_batch_is_atomic_against_existing_and_internal_order():
    """REC-T15/T16: batch başı ve iç adjacency tam doğrulanmadan append edilmez."""
    target = FlightRecorder3DOF()
    existing = event(FlightEventType.BURNOUT, 1.4)
    target.record_events(events=(existing,))
    for batch, invalid_time in (
        ((event(FlightEventType.APOGEE, 1.3),), 1.3),
        ((event(FlightEventType.APOGEE, 4.0), event(FlightEventType.GROUND, 3.5)), 3.5),
    ):
        with pytest.raises(FlightRecordingError) as captured:
            target.record_events(events=batch)
        assert captured.value.error_code == "NON_MONOTONIC_EVENT_TIME"
        assert captured.value.field_name == "event.time_s"
        assert captured.value.value == invalid_time
        assert target.snapshot().events == (existing,)


def test_samples_and_estimated_events_remain_separate_streams():
    """REC-T19/T20: localized event state accepted trajectory sample'a yükseltilmez."""
    target = FlightRecorder3DOF()
    record_sample(target, 1.0)
    record_sample(target, 1.1)
    occurrence = event(FlightEventType.APOGEE, 1.05, z=999.0)
    target.record_events(events=(occurrence,))
    snapshot = target.snapshot()
    assert [sample.point.time_s for sample in snapshot.samples] == [1.0, 1.1]
    assert [item.time_s for item in snapshot.events] == [1.05]
    assert snapshot.events[0] is occurrence
    assert len(snapshot.samples) == 2
    assert all(sample.point.state is not occurrence.estimated_state for sample in snapshot.samples)


def test_empty_snapshot_independence_and_snapshot_does_not_seal():
    """REC-T21..T23: immutable historical tuple copy; snapshot sonrası append geçerli."""
    target = FlightRecorder3DOF()
    empty = target.snapshot()
    assert empty == RecordedFlightData3DOF((), ())
    record_sample(target, 0.0)
    record_sample(target, 0.01)
    first_snapshot = target.snapshot()
    record_sample(target, 0.02)
    second_snapshot = target.snapshot()
    assert len(first_snapshot.samples) == 2
    assert len(second_snapshot.samples) == 3
    assert first_snapshot.samples == second_snapshot.samples[:2]
    with pytest.raises(TypeError):
        first_snapshot.samples[0] = second_snapshot.samples[0]


def test_point_physics_correspondence_is_caller_contract_not_recomputed():
    """REC-T02/T24: recorder provenance eklemez; supplied accepted pair'i aynen saklar."""
    target = FlightRecorder3DOF()
    accepted_point = point(2.0)
    deliberately_distinct_marker = physics(99.0)
    target.record_sample(point=accepted_point, physics=deliberately_distinct_marker)
    sample = target.snapshot().samples[0]
    assert sample.point is accepted_point
    assert sample.physics is deliberately_distinct_marker


def test_public_scope_contains_no_operations_or_export_policy():
    """REC-T24: physics/RK4/detection/export/resample/lifecycle API leakage yok."""
    assert recorder.__all__ == (
        "RecordedFlightSample3DOF", "RecordedFlightData3DOF",
        "FlightRecordingError", "FlightRecorder3DOF",
    )
    for forbidden in (
        "PhysicsEvaluator3DOF", "ClassicalRK4Integrator3DOF",
        "FlightEventDetector3DOF", "SimulationEngine", "SimulationResult",
        "export_csv", "export_json", "resample", "downsample", "finalize", "seal",
    ):
        assert not hasattr(recorder, forbidden)
