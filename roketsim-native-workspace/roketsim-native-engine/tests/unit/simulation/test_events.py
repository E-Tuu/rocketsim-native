"""NAT-018 burnout/apogee/ground bracket-event contract ve V&V testleri."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature

import numpy as np
import pytest

from roketsim_native.dynamics.initial_state import (
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.numerics.fixed_step import IntegrationPoint3DOF
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T
from roketsim_native.propulsion.thrust import MotorCurveAnalyzer, MotorCurveStatistics
from roketsim_native.simulation import events
from roketsim_native.simulation.events import (
    ApogeeDetectionModel,
    BurnoutDetectionModel,
    EventDetectionError,
    EventLocalizationModel,
    FlightEventDetectionContext3DOF,
    FlightEventDetectionProfile3DOF,
    FlightEventDetector3DOF,
    FlightEventOccurrence3DOF,
    FlightEventType,
    GroundReferenceModel,
)
from roketsim_native.simulation.physics import PropulsionTimeline


def profile():
    return FlightEventDetectionProfile3DOF(
        BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
        ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
        GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,
        EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION,
    )


def statistics(curve_end=100.0):
    return MotorCurveStatistics(1.0, 1.0, 0.5, curve_end, 0.1, 0.9, 0.8)


def context(*, ignition=0.0, curve_end=100.0, launch_z=0.0):
    return FlightEventDetectionContext3DOF(
        launch_conditions=LaunchConditions3DOF(
            (10.0, 20.0, launch_z), (0.0, 0.0, 0.0), (0.0, 0.0, 1.0)
        ),
        propulsion_timeline=PropulsionTimeline(ignition),
        motor_curve_statistics=statistics(curve_end),
        model_profile=profile(),
    )


def point(time, *, position=(0.0, 0.0, 10.0), velocity=(0.0, 0.0, 1.0)):
    return IntegrationPoint3DOF(time, TranslationalState3DOF(position, velocity))


def detect(start, end, event_context):
    return FlightEventDetector3DOF().detect(
        start_point=start, end_point=end, context=event_context
    )


def test_enum_profile_context_and_occurrence_contracts():
    """EVT-T01..T06: exact enums, mandatory frozen/slotted values, terminal derived."""
    assert [(item.name, item.value) for item in FlightEventType] == [
        ("BURNOUT", "burnout"), ("APOGEE", "apogee"), ("GROUND", "ground")
    ]
    assert [item.value for item in BurnoutDetectionModel] == ["propulsion_curve_end_time"]
    assert [item.value for item in ApogeeDetectionModel] == [
        "world_vertical_velocity_downward_crossing"
    ]
    assert [item.value for item in GroundReferenceModel] == ["launch_world_z_plane"]
    assert [item.value for item in EventLocalizationModel] == [
        "linear_bracket_interpolation"
    ]
    for model in (FlightEventDetectionProfile3DOF, FlightEventDetectionContext3DOF):
        assert all(p.default is Parameter.empty for p in signature(model).parameters.values())
    event_context = context()
    occurrence = FlightEventOccurrence3DOF(
        FlightEventType.GROUND, 1.0, 0.5, TranslationalState3DOF((0, 0, 0), (0, 0, 0))
    )
    for value in (event_context.model_profile, event_context, occurrence):
        assert not hasattr(value, "__dict__")
        with pytest.raises(FrozenInstanceError):
            setattr(value, fields(value)[0].name, None)
    assert [f.name for f in fields(FlightEventOccurrence3DOF)] == [
        "event_type", "time_s", "interpolation_fraction", "estimated_state"
    ]
    assert occurrence.is_terminal
    assert not replace(occurrence, event_type=FlightEventType.BURNOUT).is_terminal
    assert not replace(occurrence, event_type=FlightEventType.APOGEE).is_terminal
    assert not occurrence.estimated_state.position_world_m.flags.writeable


def test_detector_api_is_parameterless_stateless_keyword_only_and_no_event_empty():
    """EVT-T07/T29: stateless API ve placeholder'sız empty tuple."""
    detector = FlightEventDetector3DOF()
    assert not signature(FlightEventDetector3DOF).parameters
    assert not hasattr(detector, "__dict__")
    parameters = signature(detector.detect).parameters
    assert tuple(parameters) == ("start_point", "end_point", "context")
    assert all(p.kind is Parameter.KEYWORD_ONLY for p in parameters.values())
    result = detect(point(0), point(1, position=(0, 0, 11)), context(curve_end=2))
    assert result == () and type(result) is tuple


@pytest.mark.parametrize("start_time,end_time", [(1.0, 1.0), (2.0, 1.0)])
def test_interval_must_be_strictly_increasing(start_time, end_time):
    """EVT-T08: equal/reversed interval sıralanmaz veya abs ile onarılmaz."""
    with pytest.raises(EventDetectionError) as captured:
        detect(point(start_time), point(end_time), context())
    assert captured.value.error_code == "NON_INCREASING_EVENT_INTERVAL"
    assert captured.value.field_name == "end_point.time_s"
    assert captured.value.value == end_time


def test_f50_burnout_authority_and_linear_localization():
    """EVT-T09..T11/T14: F50 curve-end + ignition authority, linear state estimate."""
    stats = MotorCurveAnalyzer().analyze(motor=AEROTECH_F50_4T)
    assert stats.curve_end_time_s == 1.430
    event_context = FlightEventDetectionContext3DOF(
        context(launch_z=0).launch_conditions,
        PropulsionTimeline(0.250),
        stats,
        profile(),
    )
    start = point(1.6, position=(0, 0, 10), velocity=(0, 0, 2))
    end = point(1.7, position=(1, 2, 20), velocity=(1, 2, 4))
    result = detect(start, end, event_context)
    assert len(result) == 1
    event = result[0]
    assert event.event_type is FlightEventType.BURNOUT
    assert event.time_s == 1.68
    assert event.interpolation_fraction == pytest.approx(0.8)
    np.testing.assert_allclose(event.estimated_state.position_world_m, (0.8, 1.6, 18))
    np.testing.assert_allclose(event.estimated_state.velocity_world_m_s, (0.8, 1.6, 3.6))
    assert not event.is_terminal


def test_burnout_interval_is_open_closed_without_duplicate():
    """EVT-T12/T13: exact (t0,t1] boundary endpointte bir kez raporlanır."""
    event_context = context(ignition=0.25, curve_end=1.43)
    first = detect(point(1.6), point(1.68), event_context)
    second = detect(point(1.68), point(1.8), event_context)
    assert len(first) == 1 and first[0].event_type is FlightEventType.BURNOUT
    assert first[0].interpolation_fraction == 1.0
    assert second == ()


@pytest.mark.parametrize(
    "vz0,vz1,detected",
    [(3.0, -1.0, True), (3.0, 0.0, True), (0.0, 2.0, False), (-1.0, -2.0, False)],
)
def test_apogee_exact_directional_crossing(vz0, vz1, detected):
    """EVT-T15..T18: yalnız positive -> zero/negative WORLD-vz crossing."""
    result = detect(
        point(4.0, velocity=(0, 0, vz0)),
        point(4.2, position=(0, 0, 11), velocity=(0, 0, vz1)),
        context(),
    )
    assert bool(result) is detected
    if detected:
        assert result[0].event_type is FlightEventType.APOGEE
        assert result[0].interpolation_fraction == (1.0 if vz1 == 0 else 0.75)


def test_apogee_vv_and_endpoint_no_duplicate():
    """EVT-T18/T19: alpha=.75, t=4.15, z=100.75, vz=0 ve duplicate yok."""
    first = detect(
        point(4.0, position=(0, 0, 100), velocity=(0, 0, 3)),
        point(4.2, position=(0, 0, 101), velocity=(0, 0, -1)),
        context(),
    )[0]
    assert first.time_s == pytest.approx(4.15)
    assert first.interpolation_fraction == 0.75
    assert first.estimated_state.position_world_m[2] == 100.75
    assert first.estimated_state.velocity_world_m_s[2] == 0.0
    assert not first.is_terminal
    assert detect(
        point(4.2, velocity=(0, 0, 0)),
        point(4.3, velocity=(0, 0, -1)),
        context(),
    ) == ()


@pytest.mark.parametrize(
    "height0,height1,detected",
    [(0.3, 0.1, False), (0.3, 0.0, True), (0.3, -0.1, True),
     (0.0, -0.1, False), (0.0, 0.1, False)],
)
def test_ground_exact_directional_crossing(height0, height1, detected):
    """EVT-T20..T24: launch-z relative yalnız positive -> zero/negative crossing."""
    result = detect(
        point(1.0, position=(0, 0, 50 + height0), velocity=(0, 0, -1)),
        point(1.2, position=(0, 0, 50 + height1), velocity=(0, 0, -1)),
        context(launch_z=50),
    )
    assert bool(result) is detected
    if detected:
        assert result[0].event_type is FlightEventType.GROUND


def test_nonzero_world_origin_ground_vv_and_endpoint_no_duplicate():
    """EVT-T22/T25: launch z=50, alpha=.75, t=9.15 ve ground terminal."""
    event_context = context(launch_z=50)
    event = detect(
        point(9.0, position=(1, 2, 50.3), velocity=(0, 0, -2)),
        point(9.2, position=(3, 4, 49.9), velocity=(0, 0, -4)),
        event_context,
    )[0]
    assert event.interpolation_fraction == pytest.approx(0.75)
    assert event.time_s == pytest.approx(9.15)
    assert event.estimated_state.position_world_m[2] == pytest.approx(50.0)
    assert event.is_terminal
    assert detect(
        point(9.2, position=(0, 0, 50), velocity=(0, 0, -1)),
        point(9.3, position=(0, 0, 49), velocity=(0, 0, -1)),
        event_context,
    ) == ()


def test_generic_linear_state_estimate_and_input_immutability():
    """EVT-T05/T26/T32: alpha=.25 exact, yeni read-only state, bracket'lar değişmez."""
    start = point(0, position=(0, 0, 10), velocity=(2, 4, 6))
    end = point(4, position=(4, 8, 14), velocity=(6, 8, 10))
    event_context = context(curve_end=1)
    snapshots = tuple(
        vector.copy() for vector in (
            start.state.position_world_m, start.state.velocity_world_m_s,
            end.state.position_world_m, end.state.velocity_world_m_s,
        )
    )
    event = detect(start, end, event_context)[0]
    repeated = detect(start, end, event_context)[0]
    assert event.event_type is FlightEventType.BURNOUT
    assert event.interpolation_fraction == 0.25
    np.testing.assert_array_equal(event.estimated_state.position_world_m, (1, 2, 11))
    np.testing.assert_array_equal(event.estimated_state.velocity_world_m_s, (3, 5, 7))
    assert not event.estimated_state.position_world_m.flags.writeable
    assert not event.estimated_state.velocity_world_m_s.flags.writeable
    assert (event.event_type, event.time_s, event.interpolation_fraction) == (
        repeated.event_type, repeated.time_s, repeated.interpolation_fraction
    )
    np.testing.assert_array_equal(
        event.estimated_state.position_world_m,
        repeated.estimated_state.position_world_m,
    )
    np.testing.assert_array_equal(
        event.estimated_state.velocity_world_m_s,
        repeated.estimated_state.velocity_world_m_s,
    )
    for current, snapshot in zip(
        (start.state.position_world_m, start.state.velocity_world_m_s,
         end.state.position_world_m, end.state.velocity_world_m_s), snapshots, strict=True
    ):
        np.testing.assert_array_equal(current, snapshot)


def test_multiple_events_chronological_and_equal_time_tie_order():
    """EVT-T27/T28: time sırası ve exact BURNOUT/APOGEE/GROUND tie policy."""
    chronological = detect(
        point(5.0, position=(0, 0, 100), velocity=(0, 0, 7)),
        point(5.2, position=(0, 0, 101), velocity=(0, 0, -3)),
        context(ignition=5.0, curve_end=0.1),
    )
    assert [(event.event_type, event.time_s) for event in chronological] == [
        (FlightEventType.BURNOUT, 5.1),
        (FlightEventType.APOGEE, pytest.approx(5.14)),
    ]
    tied = detect(
        point(5.0, position=(0, 0, 51), velocity=(0, 0, 1)),
        point(5.2, position=(0, 0, 49), velocity=(0, 0, -1)),
        context(ignition=5.0, curve_end=0.1, launch_z=50),
    )
    assert [event.event_type for event in tied] == [
        FlightEventType.BURNOUT, FlightEventType.APOGEE, FlightEventType.GROUND
    ]
    assert all(event.time_s == 5.1 for event in tied)


@pytest.mark.parametrize(
    "field,bad_value,error_code",
    [
        ("burnout_detection_model", "bad", "UNSUPPORTED_BURNOUT_DETECTION_MODEL"),
        ("apogee_detection_model", "bad", "UNSUPPORTED_APOGEE_DETECTION_MODEL"),
        ("ground_reference_model", "bad", "UNSUPPORTED_GROUND_REFERENCE_MODEL"),
        ("localization_model", "bad", "UNSUPPORTED_EVENT_LOCALIZATION_MODEL"),
    ],
)
def test_unsupported_models_are_structured_without_fallback(field, bad_value, error_code):
    """EVT-T30: dört policy alanında bilinmeyen model explicit başarısızdır."""
    values = {
        "burnout_detection_model": BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
        "apogee_detection_model": ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
        "ground_reference_model": GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,
        "localization_model": EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION,
    }
    values[field] = bad_value
    with pytest.raises(EventDetectionError) as captured:
        FlightEventDetectionProfile3DOF(**values)
    assert captured.value.error_code == error_code
    assert captured.value.field_name == field
    assert captured.value.value == bad_value


def test_nonfinite_boundary_and_invalid_localization_are_structured():
    """EVT-T31: boundary overflow ve alpha domain failure clamp edilmez."""
    with pytest.raises(EventDetectionError) as captured:
        detect(point(0), point(1), context(ignition=1e308, curve_end=1e308))
    assert captured.value.error_code == "NONFINITE_EVENT_BOUNDARY_TIME"
    assert captured.value.field_name == "burnout_time_s"
    with pytest.raises(EventDetectionError) as captured:
        events._localized_state(
            start_point=point(0), end_point=point(1), interpolation_fraction=1.01
        )
    assert captured.value.error_code == "INVALID_EVENT_LOCALIZATION"
    assert captured.value.value == 1.01


def test_runtime_authority_separation_and_scope():
    """EVT-T09/T10/T32: yalnız stats curve-end; threshold/physics/history API yok."""
    assert [f.name for f in fields(FlightEventDetectionContext3DOF)] == [
        "launch_conditions", "propulsion_timeline", "motor_curve_statistics", "model_profile"
    ]
    assert [f.name for f in fields(MotorCurveStatistics)].count("curve_end_time_s") == 1
    forbidden = {
        "thrust_N", "measured_burn_time_s", "effective_burn_end_5pct_s",
        "ejection_delay_s", "burnout_seen", "apogee_seen", "ground_seen",
        "root_solver", "dense_output", "deploy_recovery", "stop_simulation",
    }
    public_fields = {
        field.name for model in (
            FlightEventDetectionProfile3DOF,
            FlightEventDetectionContext3DOF,
            FlightEventOccurrence3DOF,
        ) for field in fields(model)
    }
    assert forbidden.isdisjoint(public_fields)
    assert events.__all__ == (
        "FlightEventType", "BurnoutDetectionModel", "ApogeeDetectionModel",
        "GroundReferenceModel", "EventLocalizationModel",
        "FlightEventDetectionProfile3DOF", "FlightEventDetectionContext3DOF",
        "FlightEventOccurrence3DOF", "EventDetectionError", "FlightEventDetector3DOF",
    )
