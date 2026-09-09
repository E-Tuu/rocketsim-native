"""NAT-020 SimulationEngine orchestration/lifecycle focused testleri."""

from dataclasses import FrozenInstanceError, fields
from inspect import Parameter, signature
from types import SimpleNamespace

import numpy as np
import pytest

from roketsim_native.dynamics.initial_state import (
    InitialStateBuilder,
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.dynamics.translational import TranslationalStateDerivative3DOF
from roketsim_native.numerics.fixed_step import FixedStepConfig
from roketsim_native.numerics.rk4 import ClassicalRK4Integrator3DOF
from roketsim_native.propulsion.thrust import MotorCurveStatistics
from roketsim_native.simulation import engine
from roketsim_native.simulation.engine import (
    SimulationEngine3DOF,
    SimulationEngineError,
    SimulationExecution3DOF,
    SimulationRunConfiguration3DOF,
    SimulationRunLimits3DOF,
    SimulationTerminationReason,
    TerminalEventHandlingModel,
)
from roketsim_native.simulation.events import (
    ApogeeDetectionModel,
    BurnoutDetectionModel,
    EventLocalizationModel,
    FlightEventDetectionProfile3DOF,
    FlightEventOccurrence3DOF,
    FlightEventType,
    GroundReferenceModel,
)
from roketsim_native.simulation.physics import (
    PhysicsEvaluationContext3DOF,
    PhysicsEvaluationResult3DOF,
    PropulsionTimeline,
)
from roketsim_native.simulation.recorder import FlightRecorder3DOF


def event_profile():
    return FlightEventDetectionProfile3DOF(
        BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
        ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
        GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,
        EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION,
    )


def physics_context(*, ignition=2.5):
    return PhysicsEvaluationContext3DOF(
        resolved_geometry=None,
        aerodynamic_surfaces=None,
        structural_mass_properties=None,
        motor_installation=None,
        motor_property_model_profile=None,
        basic_drag_model_profile=None,
        translational_dynamics_model_profile=None,
        environment_position_mapping_model=None,
        launch_environment_altitude_m=0.0,
        propulsion_timeline=PropulsionTimeline(ignition),
        atmosphere_model=None,
        air_properties_calculator=None,
        gravity_model=None,
        wind_model=None,
    )


def configuration(*, maximum_steps=1, ignition=2.5, launch_velocity=(0, 0, 0)):
    return SimulationRunConfiguration3DOF(
        launch_conditions=LaunchConditions3DOF(
            (1.0, 0.0, 50.0), launch_velocity, (0.0, 0.0, 1.0)
        ),
        physics_context=physics_context(ignition=ignition),
        motor_curve_statistics=MotorCurveStatistics(
            1.0, 1.0, 0.5, 100.0, 0.1, 0.9, 0.8
        ),
        event_detection_profile=event_profile(),
        fixed_step_config=FixedStepConfig(0.1),
        run_limits=SimulationRunLimits3DOF(maximum_steps),
        terminal_event_handling_model=(
            TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT
        ),
    )


def physics_result(*, derivative, marker=0):
    placeholder = SimpleNamespace(marker=marker)
    return PhysicsEvaluationResult3DOF(
        environment_altitude_m=0.0,
        motor_time_s=0.0,
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
        dynamics=SimpleNamespace(derivative=derivative, marker=marker),
    )


class PhysicsSpy:
    def __init__(self, *, derivative_factory=None, fail_call=None, sentinel=None, log=None):
        self.calls = []
        self.derivative_factory = derivative_factory or self._default_derivative
        self.fail_call = fail_call
        self.sentinel = sentinel
        self.log = log

    @staticmethod
    def _default_derivative(state):
        x = float(state.position_world_m[0])
        return TranslationalStateDerivative3DOF((x, 0, 0), (0, 0, 0))

    def evaluate(self, *, time_s, state, launch_conditions, context):
        self.calls.append((time_s, state, launch_conditions, context))
        if self.log is not None:
            self.log.append(("physics", time_s, state))
        if self.fail_call == len(self.calls):
            raise self.sentinel
        return physics_result(
            derivative=self.derivative_factory(state), marker=len(self.calls)
        )


class DetectorSpy:
    def __init__(self, batches=(), *, sentinel=None, log=None):
        self.batches = tuple(batches)
        self.calls = []
        self.sentinel = sentinel
        self.log = log

    def detect(self, *, start_point, end_point, context):
        self.calls.append((start_point, end_point, context))
        if self.log is not None:
            self.log.append(("detect", end_point.time_s, end_point.state))
        if self.sentinel is not None:
            raise self.sentinel
        index = len(self.calls) - 1
        return self.batches[index] if index < len(self.batches) else ()


def occurrence(event_type, time, fraction, *, z=50.0):
    return FlightEventOccurrence3DOF(
        event_type,
        time,
        fraction,
        TranslationalState3DOF((99.0, 0.0, z), (0.0, 0.0, -1.0)),
    )


def install_spies(monkeypatch, *, physics_spy=None, detector_spy=None):
    physics_spy = physics_spy or PhysicsSpy()
    detector_spy = detector_spy or DetectorSpy()
    monkeypatch.setattr(engine, "PhysicsEvaluator3DOF", lambda: physics_spy)
    monkeypatch.setattr(engine, "FlightEventDetector3DOF", lambda: detector_spy)
    return physics_spy, detector_spy


def test_limits_enums_configuration_execution_and_engine_contracts():
    """ENG-T01..T07: frozen mandatory contracts, exact enum ve stateless API."""
    limit_parameter = signature(SimulationRunLimits3DOF).parameters["maximum_steps"]
    assert limit_parameter.default is Parameter.empty
    limits = SimulationRunLimits3DOF(1)
    assert not hasattr(limits, "__dict__")
    with pytest.raises(FrozenInstanceError):
        limits.maximum_steps = 2
    assert [(item.name, item.value) for item in SimulationTerminationReason] == [
        ("TERMINAL_EVENT", "terminal_event"),
        ("MAXIMUM_STEPS_REACHED", "maximum_steps_reached"),
    ]
    assert [item.value for item in TerminalEventHandlingModel] == [
        "reject_interior_candidate_accept_endpoint"
    ]
    config = configuration()
    assert all(p.default is Parameter.empty for p in signature(type(config)).parameters.values())
    assert "initial_time_s" not in {field.name for field in fields(config)}
    assert not hasattr(config, "__dict__")
    with pytest.raises(FrozenInstanceError):
        config.run_limits = limits
    execution_fields = [field.name for field in fields(SimulationExecution3DOF)]
    assert execution_fields == ["recorded_data", "termination_reason", "steps_performed"]
    instance = SimulationEngine3DOF()
    assert not signature(SimulationEngine3DOF).parameters
    assert not hasattr(instance, "__dict__")
    parameters = signature(instance.run).parameters
    assert tuple(parameters) == ("configuration",)
    assert parameters["configuration"].kind is Parameter.KEYWORD_ONLY


@pytest.mark.parametrize("value", [True, 10.5, "100", None])
def test_maximum_steps_rejects_non_integer_programming_inputs(value):
    """ENG-T02: bool dahil true-int olmayan count normal TypeError'dır."""
    with pytest.raises(TypeError):
        SimulationRunLimits3DOF(value)


@pytest.mark.parametrize("value", [0, -1])
def test_nonpositive_integer_maximum_steps_is_structured(value):
    """ENG-T02: finite integer domain ihlali INVALID_MAXIMUM_STEPS."""
    with pytest.raises(SimulationEngineError) as captured:
        SimulationRunLimits3DOF(value)
    assert captured.value.error_code == "INVALID_MAXIMUM_STEPS"
    assert captured.value.field_name == "maximum_steps"
    assert captured.value.value == value


def test_unsupported_terminal_model_is_structured():
    """ENG-T04/T33: terminal policy fallback olmadan structured reddedilir."""
    values = {field.name: getattr(configuration(), field.name)
              for field in fields(SimulationRunConfiguration3DOF)}
    values["terminal_event_handling_model"] = "bad"
    with pytest.raises(SimulationEngineError) as captured:
        SimulationRunConfiguration3DOF(**values)
    assert captured.value.error_code == "UNSUPPORTED_TERMINAL_EVENT_HANDLING_MODEL"
    assert captured.value.field_name == "terminal_event_handling_model"


def test_nonzero_initial_time_builder_event_context_and_one_step_wiring(monkeypatch):
    """ENG-T08..T17: ignition t0, builder, shared timeline, stage/screen/endpoint sıra."""
    log = []
    physics_spy = PhysicsSpy(log=log)
    detector_spy = DetectorSpy(log=log)
    install_spies(monkeypatch, physics_spy=physics_spy, detector_spy=detector_spy)
    accepted_builder = InitialStateBuilder
    builder_calls = []

    class BuilderSpy:
        def build(self, *, launch_conditions):
            builder_calls.append(launch_conditions)
            return accepted_builder().build(launch_conditions=launch_conditions)

    monkeypatch.setattr(engine, "InitialStateBuilder", BuilderSpy)
    config = configuration(maximum_steps=1, ignition=2.5)
    result = SimulationEngine3DOF().run(configuration=config)
    assert not hasattr(result, "__dict__")
    with pytest.raises(FrozenInstanceError):
        result.steps_performed = 2
    assert builder_calls == [config.launch_conditions]
    assert result.steps_performed == 1
    assert result.termination_reason is SimulationTerminationReason.MAXIMUM_STEPS_REACHED
    assert len(result.recorded_data.samples) == 2
    assert result.recorded_data.events == ()
    initial, endpoint = result.recorded_data.samples
    assert initial.point.time_s == 2.5
    np.testing.assert_array_equal(
        initial.point.state.position_world_m,
        config.launch_conditions.initial_position_world_m,
    )
    assert len(physics_spy.calls) == 6
    assert physics_spy.calls[0][0] == 2.5
    assert physics_spy.calls[0][1] is initial.point.state
    assert log[5][0] == "detect" and log[6][0] == "physics"
    assert physics_spy.calls[-1][0] == endpoint.point.time_s
    assert physics_spy.calls[-1][1] is endpoint.point.state
    assert detector_spy.calls[0][2].propulsion_timeline is config.physics_context.propulsion_timeline
    assert detector_spy.calls[0][2].launch_conditions is config.launch_conditions
    assert detector_spy.calls[0][2].motor_curve_statistics is config.motor_curve_statistics


def test_endpoint_physics_is_distinct_from_k4_trial_physics(monkeypatch):
    """ENG-T12/T13/T15/T16: 4 stage + separate y_next endpoint, k4 cache değildir."""
    physics_spy, _ = install_spies(monkeypatch)
    result = SimulationEngine3DOF().run(configuration=configuration(maximum_steps=1))
    assert len(physics_spy.calls) == 6
    k4_call = physics_spy.calls[4]
    endpoint_call = physics_spy.calls[5]
    assert k4_call[0] == endpoint_call[0] == result.recorded_data.samples[-1].point.time_s
    assert k4_call[1] is not endpoint_call[1]
    assert not np.array_equal(
        k4_call[1].position_world_m,
        endpoint_call[1].position_world_m,
    )
    assert endpoint_call[1] is result.recorded_data.samples[-1].point.state


@pytest.mark.parametrize(
    "maximum_steps,expected_samples,expected_calls",
    [(1, 2, 6), (2, 3, 11), (3, 4, 16)],
)
def test_maximum_step_lifecycle_counts(monkeypatch, maximum_steps, expected_samples, expected_calls):
    """ENG-T25..T28: N steps, N+1 samples ve 1+5N physics calls."""
    physics_spy, detector_spy = install_spies(monkeypatch)
    config = configuration(maximum_steps=maximum_steps)
    result = SimulationEngine3DOF().run(configuration=config)
    assert result.termination_reason is SimulationTerminationReason.MAXIMUM_STEPS_REACHED
    assert result.steps_performed == maximum_steps
    assert len(result.recorded_data.samples) == expected_samples
    assert len(physics_spy.calls) == expected_calls
    assert len(detector_spy.calls) == maximum_steps


def test_interior_terminal_rejects_candidate_and_endpoint_physics(monkeypatch):
    """ENG-T18/T19/T24/T26..T29: first interior terminal, 1 sample ve 5 calls."""
    terminal = occurrence(FlightEventType.GROUND, 2.56, 0.6, z=49.0)
    physics_spy, detector_spy = install_spies(
        monkeypatch, detector_spy=DetectorSpy(((terminal,),))
    )
    result = SimulationEngine3DOF().run(configuration=configuration(maximum_steps=1))
    assert result.termination_reason is SimulationTerminationReason.TERMINAL_EVENT
    assert result.steps_performed == 1
    assert len(result.recorded_data.samples) == 1
    assert result.recorded_data.events == (terminal,)
    assert len(physics_spy.calls) == 5
    assert len(detector_spy.calls) == 1
    assert all(sample.point.time_s != terminal.time_s for sample in result.recorded_data.samples)
    assert terminal.estimated_state is result.recorded_data.events[0].estimated_state


def test_terminal_exactly_at_endpoint_accepts_candidate(monkeypatch):
    """ENG-T20/T21/T24: alpha==1 candidate accepted, terminal wins, 6 calls."""
    terminal = occurrence(FlightEventType.GROUND, 2.6, 1.0)
    physics_spy, _ = install_spies(
        monkeypatch, detector_spy=DetectorSpy(((terminal,),))
    )
    result = SimulationEngine3DOF().run(configuration=configuration(maximum_steps=1))
    assert result.termination_reason is SimulationTerminationReason.TERMINAL_EVENT
    assert result.steps_performed == 1
    assert len(result.recorded_data.samples) == 2
    assert result.recorded_data.samples[-1].point.time_s == 2.6
    assert len(physics_spy.calls) == 6
    assert physics_spy.calls[-1][1] is result.recorded_data.samples[-1].point.state


def test_events_after_terminal_dropped_and_equal_terminal_time_retained(monkeypatch):
    """ENG-T22/T23: terminal sonrası drop; eş-zaman supplied NAT-018 order kalır."""
    before = occurrence(FlightEventType.APOGEE, 8.10, 0.2)
    terminal = occurrence(FlightEventType.GROUND, 8.15, 0.6)
    after = occurrence(FlightEventType.BURNOUT, 8.18, 0.9)
    install_spies(monkeypatch, detector_spy=DetectorSpy(((before, terminal, after),)))
    result = SimulationEngine3DOF().run(configuration=configuration())
    assert result.recorded_data.events == (before, terminal)

    tied = (
        occurrence(FlightEventType.BURNOUT, 5.0, 0.6),
        occurrence(FlightEventType.APOGEE, 5.0, 0.6),
        occurrence(FlightEventType.GROUND, 5.0, 0.6),
    )
    install_spies(monkeypatch, detector_spy=DetectorSpy((tied,)))
    tied_result = SimulationEngine3DOF().run(configuration=configuration())
    assert tied_result.recorded_data.events == tied


def test_interior_terminal_on_third_step_call_and_sample_invariants(monkeypatch):
    """ENG-T26..T28: step3 interior -> 3 executed, 3 samples, exact 15 calls."""
    terminal = occurrence(FlightEventType.GROUND, 2.76, 0.6)
    physics_spy, detector_spy = install_spies(
        monkeypatch, detector_spy=DetectorSpy(((), (), (terminal,)))
    )
    result = SimulationEngine3DOF().run(configuration=configuration(maximum_steps=3))
    assert result.termination_reason is SimulationTerminationReason.TERMINAL_EVENT
    assert result.steps_performed == 3
    assert len(result.recorded_data.samples) == 3
    assert len(physics_spy.calls) == 15
    assert len(detector_spy.calls) == 3


def test_fresh_recorder_and_fixed_config_identity_across_reused_engine(monkeypatch):
    """ENG-T30/T31/T32: two runs bağımsız; exact config her RK4 call'a gider."""
    accepted_integrator = ClassicalRK4Integrator3DOF
    seen_configs = []

    class IntegratorSpy:
        def step(self, *, point, config, derivative_function):
            seen_configs.append(config)
            return accepted_integrator().step(
                point=point, config=config, derivative_function=derivative_function
            )

    physics_spy, detector_spy = install_spies(monkeypatch)
    monkeypatch.setattr(engine, "ClassicalRK4Integrator3DOF", IntegratorSpy)
    config = configuration(maximum_steps=2)
    reusable = SimulationEngine3DOF()
    first = reusable.run(configuration=config)
    second = reusable.run(configuration=config)
    assert first is not second and first.recorded_data is not second.recorded_data
    assert len(first.recorded_data.samples) == len(second.recorded_data.samples) == 3
    assert all(seen is config.fixed_step_config for seen in seen_configs)
    assert len(seen_configs) == 4
    assert len(detector_spy.calls) == 4
    assert len(physics_spy.calls) == 22


class SentinelUpstreamError(RuntimeError):
    pass


@pytest.mark.parametrize("source", ["initial", "rk_stage", "detector", "endpoint", "recorder"])
def test_upstream_errors_propagate_same_instance(monkeypatch, source):
    """ENG-T33: beş orchestration boundary upstream exception'ı wrapping olmadan yayar."""
    sentinel = SentinelUpstreamError(source)
    fail_call = {"initial": 1, "rk_stage": 2, "endpoint": 6}.get(source)
    physics_spy = PhysicsSpy(fail_call=fail_call, sentinel=sentinel)
    detector_spy = DetectorSpy(sentinel=sentinel if source == "detector" else None)
    install_spies(monkeypatch, physics_spy=physics_spy, detector_spy=detector_spy)

    if source == "recorder":
        class FailingRecorder:
            def record_sample(self, *, point, physics):
                raise sentinel

        monkeypatch.setattr(engine, "FlightRecorder3DOF", FailingRecorder)

    with pytest.raises(SentinelUpstreamError) as captured:
        SimulationEngine3DOF().run(configuration=configuration())
    assert captured.value is sentinel


def test_no_failed_liftoff_ground_patch_bypasses_real_event_detector(monkeypatch):
    """ENG-T34: H0=0 -> H1<0 NAT-018 no-event kalır ve candidate kabul edilir."""
    downward = lambda state: TranslationalStateDerivative3DOF((0, 0, -1), (0, 0, 0))
    physics_spy = PhysicsSpy(derivative_factory=downward)
    monkeypatch.setattr(engine, "PhysicsEvaluator3DOF", lambda: physics_spy)
    result = SimulationEngine3DOF().run(configuration=configuration(maximum_steps=1))
    assert result.termination_reason is SimulationTerminationReason.MAXIMUM_STEPS_REACHED
    assert result.recorded_data.events == ()
    assert len(result.recorded_data.samples) == 2
    assert result.recorded_data.samples[-1].point.state.position_world_m[2] < 50.0


def test_public_scope_has_no_result_physics_or_post_demo_controls():
    """ENG-T06/T35: minimal execution schema; yeni physics/recovery/output API yok."""
    assert engine.__all__ == (
        "SimulationRunLimits3DOF", "SimulationTerminationReason",
        "TerminalEventHandlingModel", "SimulationRunConfiguration3DOF",
        "SimulationExecution3DOF", "SimulationEngineError", "SimulationEngine3DOF",
    )
    config_fields = {field.name for field in fields(SimulationRunConfiguration3DOF)}
    execution_fields = {field.name for field in fields(SimulationExecution3DOF)}
    assert {"initial_time_s", "maximum_time_s", "minimum_time_s"}.isdisjoint(config_fields)
    forbidden_result = {
        "final_position", "final_velocity", "final_state", "terminal_event",
        "burnout_time", "apogee_time", "ground_time", "maximum_altitude",
        "maximum_mach", "maximum_q",
    }
    assert forbidden_result.isdisjoint(execution_fields)
    for name in (
        "SimulationResult", "RecoveryDeployment", "RailEvent6DOF",
        "TrajectoryExporter", "AdaptiveStepConfig", "RailConstraint",
    ):
        assert not hasattr(engine, name)
