"""NAT-019: accepted 3DOF sample ve event'ler için pasif in-memory recorder.

Recorder physics değerlendirmez, integrasyon/event detection yapmaz ve simulation
loop'unu kontrol etmez. Sample point ile endpoint physics eşleşmesinin authority'si
caller orchestration'dır; bu katman accepted nesneleri yalnız kaydeder.
"""

from dataclasses import dataclass

from roketsim_native.numerics.fixed_step import IntegrationPoint3DOF
from roketsim_native.simulation.events import FlightEventOccurrence3DOF
from roketsim_native.simulation.physics import PhysicsEvaluationResult3DOF

__all__ = (
    "RecordedFlightSample3DOF",
    "RecordedFlightData3DOF",
    "FlightRecordingError",
    "FlightRecorder3DOF",
)


class FlightRecordingError(ValueError):
    """Finite recorder-owned chronology ihlalini structured olarak korur."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class RecordedFlightSample3DOF:
    """Accepted integration point ile o exact endpointte hesaplanmış physics çifti."""

    point: IntegrationPoint3DOF
    physics: PhysicsEvaluationResult3DOF

    def __post_init__(self) -> None:
        if not isinstance(self.point, IntegrationPoint3DOF):
            raise TypeError("point must be IntegrationPoint3DOF")
        if not isinstance(self.physics, PhysicsEvaluationResult3DOF):
            raise TypeError("physics must be PhysicsEvaluationResult3DOF")


@dataclass(frozen=True, slots=True)
class RecordedFlightData3DOF:
    """Recorder mutable listelerini açığa çıkarmayan immutable tuple snapshot."""

    samples: tuple[RecordedFlightSample3DOF, ...]
    events: tuple[FlightEventOccurrence3DOF, ...]

    def __post_init__(self) -> None:
        if type(self.samples) is not tuple or not all(
            isinstance(sample, RecordedFlightSample3DOF) for sample in self.samples
        ):
            raise TypeError("samples must be a tuple of RecordedFlightSample3DOF")
        if type(self.events) is not tuple or not all(
            isinstance(event, FlightEventOccurrence3DOF) for event in self.events
        ):
            raise TypeError("events must be a tuple of FlightEventOccurrence3DOF")


class FlightRecorder3DOF:
    """Yalnız iki append-only history listesi taşıyan intentional stateful recorder."""

    __slots__ = ("_samples", "_events")

    def __init__(self) -> None:
        self._samples: list[RecordedFlightSample3DOF] = []
        self._events: list[FlightEventOccurrence3DOF] = []

    def record_sample(
        self,
        *,
        point: IntegrationPoint3DOF,
        physics: PhysicsEvaluationResult3DOF,
    ) -> None:
        """Strict artan point time ile accepted endpoint sample'ı append et."""

        sample = RecordedFlightSample3DOF(point=point, physics=physics)
        if self._samples and point.time_s <= self._samples[-1].point.time_s:
            raise FlightRecordingError(
                error_code="NON_INCREASING_SAMPLE_TIME",
                field_name="point.time_s",
                value=point.time_s,
            )
        self._samples.append(sample)

    def record_events(
        self,
        *,
        events: tuple[FlightEventOccurrence3DOF, ...],
    ) -> None:
        """Non-decreasing event batch'i supplied order korunarak atomik append et."""

        if type(events) is not tuple or not all(
            isinstance(event, FlightEventOccurrence3DOF) for event in events
        ):
            raise TypeError("events must be a tuple of FlightEventOccurrence3DOF")

        previous_time = self._events[-1].time_s if self._events else None
        for event in events:
            if previous_time is not None and event.time_s < previous_time:
                raise FlightRecordingError(
                    error_code="NON_MONOTONIC_EVENT_TIME",
                    field_name="event.time_s",
                    value=event.time_s,
                )
            previous_time = event.time_s

        # Tüm batch önce doğrulandı; bu noktadan önce recorder state'i değişmedi.
        self._events.extend(events)

    def snapshot(self) -> RecordedFlightData3DOF:
        """Mevcut listelerden yeni, bağımsız ve recorder'ı seal etmeyen snapshot al."""

        return RecordedFlightData3DOF(
            samples=tuple(self._samples),
            events=tuple(self._events),
        )
