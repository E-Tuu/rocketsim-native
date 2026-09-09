"""NAT-021: tamamlanmış 3DOF execution için immutable semantic sonuç facade'ı.

Bu katman simulation çalıştırmaz, physics veya event zamanı hesaplamaz ve accepted
history'yi değiştirmez. Tek stored authority NAT-020 ``SimulationExecution3DOF``
nesnesidir; diğer bütün public değerler bu nesneden türetilir.
"""

from dataclasses import dataclass

from roketsim_native.simulation.engine import (
    SimulationExecution3DOF,
    SimulationTerminationReason,
)
from roketsim_native.simulation.events import (
    FlightEventOccurrence3DOF,
    FlightEventType,
)
from roketsim_native.simulation.recorder import (
    RecordedFlightData3DOF,
    RecordedFlightSample3DOF,
)

__all__ = (
    "SimulationResultError",
    "SimulationResult3DOF",
)


class SimulationResultError(ValueError):
    """NAT-021-owned finite execution/result integrity hatası."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class SimulationResult3DOF:
    """Accepted execution'ı kopyalamadan güvenli semantic query API'sine açar."""

    execution: SimulationExecution3DOF

    def __post_init__(self) -> None:
        if not isinstance(self.execution, SimulationExecution3DOF):
            raise TypeError("execution must be SimulationExecution3DOF")

        if not self.samples:
            raise SimulationResultError(
                error_code="EMPTY_RECORDED_SAMPLES",
                field_name="execution.recorded_data.samples",
                value=self.samples,
            )

        terminal_events = self._terminal_events()
        if self.termination_reason is SimulationTerminationReason.TERMINAL_EVENT:
            if not terminal_events:
                raise SimulationResultError(
                    error_code="TERMINATION_EVENT_MISSING",
                    field_name="execution.recorded_data.events",
                    value=self.events,
                )
            if len(terminal_events) > 1:
                raise SimulationResultError(
                    error_code="MULTIPLE_TERMINAL_EVENTS",
                    field_name="execution.recorded_data.events",
                    value=terminal_events,
                )
        elif (
            self.termination_reason
            is SimulationTerminationReason.MAXIMUM_STEPS_REACHED
            and terminal_events
        ):
            raise SimulationResultError(
                error_code="UNEXPECTED_TERMINAL_EVENT",
                field_name="execution.recorded_data.events",
                value=terminal_events,
            )

        termination_time = self._termination_time(terminal_events=terminal_events)
        initial_time = self.initial_sample.point.time_s
        if termination_time < initial_time:
            raise SimulationResultError(
                error_code="INVALID_TERMINATION_TIME",
                field_name="termination_time_s",
                value=termination_time,
            )

    @property
    def recorded_data(self) -> RecordedFlightData3DOF:
        """NAT-020 execution'ın exact immutable recorder snapshot'ı."""

        return self.execution.recorded_data

    @property
    def samples(self) -> tuple[RecordedFlightSample3DOF, ...]:
        """Kopyalanmayan accepted trajectory sample tuple'ı."""

        return self.recorded_data.samples

    @property
    def events(self) -> tuple[FlightEventOccurrence3DOF, ...]:
        """Kopyalanmayan accepted localized event tuple'ı."""

        return self.recorded_data.events

    @property
    def termination_reason(self) -> SimulationTerminationReason:
        """Accepted NAT-020 lifecycle termination authority'si."""

        return self.execution.termination_reason

    @property
    def steps_performed(self) -> int:
        """Accepted NAT-020 executed candidate-step sayısı."""

        return self.execution.steps_performed

    @property
    def initial_sample(self) -> RecordedFlightSample3DOF:
        """Ignition-time accepted trajectory sample'ı; her zaman samples[0]."""

        return self.samples[0]

    @property
    def last_accepted_sample(self) -> RecordedFlightSample3DOF:
        """Son accepted RK4 sample'ı; localized terminal state ile karıştırılmaz."""

        return self.samples[-1]

    def events_of_type(
        self,
        *,
        event_type: FlightEventType,
    ) -> tuple[FlightEventOccurrence3DOF, ...]:
        """İstenen accepted event type'ın tüm occurrence'larını recorded sırada ver."""

        if not isinstance(event_type, FlightEventType):
            raise TypeError("event_type must be FlightEventType")
        return tuple(event for event in self.events if event.event_type is event_type)

    def _singular_event(
        self,
        *,
        event_type: FlightEventType,
    ) -> FlightEventOccurrence3DOF | None:
        """Named convenience lookup'ta yok/tek/ambiguous cardinality'yi koru."""

        matches = self.events_of_type(event_type=event_type)
        if len(matches) > 1:
            raise SimulationResultError(
                error_code="AMBIGUOUS_EVENT_HISTORY",
                field_name="event_type",
                value=event_type,
            )
        return matches[0] if matches else None

    @property
    def burnout_event(self) -> FlightEventOccurrence3DOF | None:
        """Unique accepted BURNOUT occurrence veya yoksa None."""

        return self._singular_event(event_type=FlightEventType.BURNOUT)

    @property
    def apogee_event(self) -> FlightEventOccurrence3DOF | None:
        """Unique accepted APOGEE occurrence veya yoksa None."""

        return self._singular_event(event_type=FlightEventType.APOGEE)

    @property
    def ground_event(self) -> FlightEventOccurrence3DOF | None:
        """Unique accepted GROUND occurrence veya yoksa None."""

        return self._singular_event(event_type=FlightEventType.GROUND)

    def _terminal_events(self) -> tuple[FlightEventOccurrence3DOF, ...]:
        """Terminal classification'ı event type'tan değil accepted property'den al."""

        return tuple(event for event in self.events if event.is_terminal)

    @property
    def terminal_event(self) -> FlightEventOccurrence3DOF | None:
        """Integrity doğrulamasından geçmiş accepted terminal occurrence."""

        terminal_events = self._terminal_events()
        return terminal_events[0] if terminal_events else None

    def _termination_time(
        self,
        *,
        terminal_events: tuple[FlightEventOccurrence3DOF, ...],
    ) -> float:
        """Lifecycle reason'a göre event veya last accepted sample zamanını seç."""

        if self.termination_reason is SimulationTerminationReason.TERMINAL_EVENT:
            return terminal_events[0].time_s
        return self.last_accepted_sample.point.time_s

    @property
    def termination_time_s(self) -> float:
        """Physical lifecycle sonu; interior terminalde last sample'dan ileride olabilir."""

        return self._termination_time(terminal_events=self._terminal_events())

    @property
    def simulation_duration_s(self) -> float:
        """Simulation başlangıcından lifecycle termination'a geçen süre."""

        return self.termination_time_s - self.initial_sample.point.time_s
