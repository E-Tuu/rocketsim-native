"""NAT-020: accepted 3DOF bileşenlerini bağlayan simulation lifecycle katmanı.

Engine yeni fizik veya numerical denklem sahiplenmez. Initial state, endpoint
physics, fixed-step RK4 candidate, event screening ve passive recording sırasını
kurar; run history engine instance'ında tutulmaz.
"""

from dataclasses import dataclass
from enum import Enum

from roketsim_native.dynamics.initial_state import (
    InitialStateBuilder,
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.dynamics.translational import TranslationalStateDerivative3DOF
from roketsim_native.numerics.fixed_step import FixedStepConfig, IntegrationPoint3DOF
from roketsim_native.numerics.rk4 import ClassicalRK4Integrator3DOF
from roketsim_native.propulsion.thrust import MotorCurveStatistics
from roketsim_native.simulation.events import (
    FlightEventDetectionContext3DOF,
    FlightEventDetectionProfile3DOF,
    FlightEventDetector3DOF,
)
from roketsim_native.simulation.physics import (
    PhysicsEvaluationContext3DOF,
    PhysicsEvaluator3DOF,
)
from roketsim_native.simulation.recorder import (
    FlightRecorder3DOF,
    RecordedFlightData3DOF,
)

__all__ = (
    "SimulationRunLimits3DOF",
    "SimulationTerminationReason",
    "TerminalEventHandlingModel",
    "SimulationRunConfiguration3DOF",
    "SimulationExecution3DOF",
    "SimulationEngineError",
    "SimulationEngine3DOF",
)


class SimulationEngineError(ValueError):
    """Yalnız NAT-020-owned finite lifecycle/configuration semantic hatası."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class SimulationRunLimits3DOF:
    """Defaultsuz positive integer RK4 candidate-step execution guard'ı."""

    maximum_steps: int

    def __post_init__(self) -> None:
        if type(self.maximum_steps) is not int:
            raise TypeError("maximum_steps must be an int and not bool")
        if self.maximum_steps <= 0:
            raise SimulationEngineError(
                error_code="INVALID_MAXIMUM_STEPS",
                field_name="maximum_steps",
                value=self.maximum_steps,
            )


class SimulationTerminationReason(Enum):
    """V1 run'ın normal lifecycle bitiş nedenleri."""

    TERMINAL_EVENT = "terminal_event"
    MAXIMUM_STEPS_REACHED = "maximum_steps_reached"


class TerminalEventHandlingModel(Enum):
    """Interior terminal candidate'i reddedip exact endpoint'i kabul eder."""

    REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT = (
        "reject_interior_candidate_accept_endpoint"
    )


@dataclass(frozen=True, slots=True)
class SimulationRunConfiguration3DOF:
    """Tek ignition timeline authority'siyle tüm accepted run girdileri."""

    launch_conditions: LaunchConditions3DOF
    physics_context: PhysicsEvaluationContext3DOF
    motor_curve_statistics: MotorCurveStatistics
    event_detection_profile: FlightEventDetectionProfile3DOF
    fixed_step_config: FixedStepConfig
    run_limits: SimulationRunLimits3DOF
    terminal_event_handling_model: TerminalEventHandlingModel

    def __post_init__(self) -> None:
        if self.terminal_event_handling_model is not (
            TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT
        ):
            raise SimulationEngineError(
                error_code="UNSUPPORTED_TERMINAL_EVENT_HANDLING_MODEL",
                field_name="terminal_event_handling_model",
                value=self.terminal_event_handling_model,
            )


@dataclass(frozen=True, slots=True)
class SimulationExecution3DOF:
    """Recorded history, lifecycle nedeni ve executed candidate-step sayısı."""

    recorded_data: RecordedFlightData3DOF
    termination_reason: SimulationTerminationReason
    steps_performed: int


class SimulationEngine3DOF:
    """Her run için fresh local orchestration state kuran reusable engine."""

    __slots__ = ()

    def run(
        self,
        *,
        configuration: SimulationRunConfiguration3DOF,
    ) -> SimulationExecution3DOF:
        """Ignition başlangıcından terminal event veya step guard'a kadar çalıştır."""

        if configuration.terminal_event_handling_model is not (
            TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT
        ):
            raise SimulationEngineError(
                error_code="UNSUPPORTED_TERMINAL_EVENT_HANDLING_MODEL",
                field_name="terminal_event_handling_model",
                value=configuration.terminal_event_handling_model,
            )

        recorder = FlightRecorder3DOF()
        physics_evaluator = PhysicsEvaluator3DOF()
        integrator = ClassicalRK4Integrator3DOF()
        event_detector = FlightEventDetector3DOF()

        initial_state = InitialStateBuilder().build(
            launch_conditions=configuration.launch_conditions,
        )
        current_point = IntegrationPoint3DOF(
            time_s=configuration.physics_context.propulsion_timeline.ignition_time_s,
            state=initial_state,
        )
        initial_physics = physics_evaluator.evaluate(
            time_s=current_point.time_s,
            state=current_point.state,
            launch_conditions=configuration.launch_conditions,
            context=configuration.physics_context,
        )
        recorder.record_sample(point=current_point, physics=initial_physics)

        event_context = FlightEventDetectionContext3DOF(
            launch_conditions=configuration.launch_conditions,
            propulsion_timeline=configuration.physics_context.propulsion_timeline,
            motor_curve_statistics=configuration.motor_curve_statistics,
            model_profile=configuration.event_detection_profile,
        )

        def derivative_function(
            *,
            time_s: float,
            state: TranslationalState3DOF,
        ) -> TranslationalStateDerivative3DOF:
            """NAT-015 result'tan yalnız accepted NAT-014 derivative'i geçir."""

            physics = physics_evaluator.evaluate(
                time_s=time_s,
                state=state,
                launch_conditions=configuration.launch_conditions,
                context=configuration.physics_context,
            )
            return physics.dynamics.derivative

        steps_performed = 0
        while steps_performed < configuration.run_limits.maximum_steps:
            candidate_point = integrator.step(
                point=current_point,
                config=configuration.fixed_step_config,
                derivative_function=derivative_function,
            )
            steps_performed += 1

            detected_events = event_detector.detect(
                start_point=current_point,
                end_point=candidate_point,
                context=event_context,
            )
            terminal_event = next(
                (event for event in detected_events if event.is_terminal),
                None,
            )

            if terminal_event is not None:
                retained_events = tuple(
                    event
                    for event in detected_events
                    if event.time_s <= terminal_event.time_s
                )
                if terminal_event.interpolation_fraction < 1.0:
                    recorder.record_events(events=retained_events)
                    return SimulationExecution3DOF(
                        recorded_data=recorder.snapshot(),
                        termination_reason=SimulationTerminationReason.TERMINAL_EVENT,
                        steps_performed=steps_performed,
                    )

                # Accepted NAT-018 occurrence exact endpointteyse alpha tam 1'dir.
                # Yakın-bir tolerance veya candidate clipping uygulanmaz.
                endpoint_physics = physics_evaluator.evaluate(
                    time_s=candidate_point.time_s,
                    state=candidate_point.state,
                    launch_conditions=configuration.launch_conditions,
                    context=configuration.physics_context,
                )
                recorder.record_events(events=retained_events)
                recorder.record_sample(
                    point=candidate_point,
                    physics=endpoint_physics,
                )
                return SimulationExecution3DOF(
                    recorded_data=recorder.snapshot(),
                    termination_reason=SimulationTerminationReason.TERMINAL_EVENT,
                    steps_performed=steps_performed,
                )

            # K4 physics y4 trial state'indedir; accepted endpoint burada ayrıca
            # değerlendirilir ve ancak sonra trajectory sample olarak kaydedilir.
            endpoint_physics = physics_evaluator.evaluate(
                time_s=candidate_point.time_s,
                state=candidate_point.state,
                launch_conditions=configuration.launch_conditions,
                context=configuration.physics_context,
            )
            recorder.record_events(events=detected_events)
            recorder.record_sample(
                point=candidate_point,
                physics=endpoint_physics,
            )
            current_point = candidate_point

        return SimulationExecution3DOF(
            recorded_data=recorder.snapshot(),
            termination_reason=SimulationTerminationReason.MAXIMUM_STEPS_REACHED,
            steps_performed=steps_performed,
        )
