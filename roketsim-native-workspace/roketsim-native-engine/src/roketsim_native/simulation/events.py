"""NAT-018: accepted 3DOF integration-point bracket'larında event raporlama.

Bu katman hareketi yeniden integre etmez veya physics'i değiştirmez. Burnout,
apogee ve ground crossing'lerini açık authority/policy'lerle bulur; event state'i
yalnız doğrusal bracket tahminidir.
"""

from dataclasses import dataclass
from enum import Enum
from math import isfinite

import numpy as np

from roketsim_native.dynamics.initial_state import (
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.numerics.fixed_step import IntegrationPoint3DOF
from roketsim_native.propulsion.thrust import MotorCurveStatistics
from roketsim_native.simulation.physics import PropulsionTimeline

__all__ = (
    "FlightEventType",
    "BurnoutDetectionModel",
    "ApogeeDetectionModel",
    "GroundReferenceModel",
    "EventLocalizationModel",
    "FlightEventDetectionProfile3DOF",
    "FlightEventDetectionContext3DOF",
    "FlightEventOccurrence3DOF",
    "EventDetectionError",
    "FlightEventDetector3DOF",
)


class FlightEventType(Enum):
    """V1'in raporladığı exact flight-event kümesi."""

    BURNOUT = "burnout"
    APOGEE = "apogee"
    GROUND = "ground"


class BurnoutDetectionModel(Enum):
    """Burnout boundary'sini accepted propulsion eğri sonundan alır."""

    PROPULSION_CURVE_END_TIME = "propulsion_curve_end_time"


class ApogeeDetectionModel(Enum):
    """Apogee'yi WORLD vertical velocity aşağı yönlü geçişiyle bulur."""

    WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING = (
        "world_vertical_velocity_downward_crossing"
    )


class GroundReferenceModel(Enum):
    """Ground V1, launch WORLD-z koordinatından geçen yatay düzlemdir."""

    LAUNCH_WORLD_Z_PLANE = "launch_world_z_plane"


class EventLocalizationModel(Enum):
    """Event state'i için yalnız explicit doğrusal bracket tahmini."""

    LINEAR_BRACKET_INTERPOLATION = "linear_bracket_interpolation"


class EventDetectionError(ValueError):
    """Finite event-domain ve beklenmeyen derived invariant hatası."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


def _validate_model(value: object, *, expected: type[Enum], field_name: str,
                    error_code: str) -> None:
    """Bilinmeyen modeli fallback uygulamadan structured olarak reddet."""

    if not isinstance(value, expected):
        raise EventDetectionError(
            error_code=error_code,
            field_name=field_name,
            value=value,
        )


@dataclass(frozen=True, slots=True)
class FlightEventDetectionProfile3DOF:
    """Burnout/apogee/ground/localization politikalarının defaultsuz seçimi."""

    burnout_detection_model: BurnoutDetectionModel
    apogee_detection_model: ApogeeDetectionModel
    ground_reference_model: GroundReferenceModel
    localization_model: EventLocalizationModel

    def __post_init__(self) -> None:
        _validate_model(
            self.burnout_detection_model,
            expected=BurnoutDetectionModel,
            field_name="burnout_detection_model",
            error_code="UNSUPPORTED_BURNOUT_DETECTION_MODEL",
        )
        _validate_model(
            self.apogee_detection_model,
            expected=ApogeeDetectionModel,
            field_name="apogee_detection_model",
            error_code="UNSUPPORTED_APOGEE_DETECTION_MODEL",
        )
        _validate_model(
            self.ground_reference_model,
            expected=GroundReferenceModel,
            field_name="ground_reference_model",
            error_code="UNSUPPORTED_GROUND_REFERENCE_MODEL",
        )
        _validate_model(
            self.localization_model,
            expected=EventLocalizationModel,
            field_name="localization_model",
            error_code="UNSUPPORTED_EVENT_LOCALIZATION_MODEL",
        )


@dataclass(frozen=True, slots=True)
class FlightEventDetectionContext3DOF:
    """Accepted authority'leri sahiplenmeden bir araya getiren dependency bundle."""

    launch_conditions: LaunchConditions3DOF
    propulsion_timeline: PropulsionTimeline
    motor_curve_statistics: MotorCurveStatistics
    model_profile: FlightEventDetectionProfile3DOF


@dataclass(frozen=True, slots=True)
class FlightEventOccurrence3DOF:
    """Doğrusal localized estimated state ile immutable event occurrence."""

    event_type: FlightEventType
    time_s: float
    interpolation_fraction: float
    estimated_state: TranslationalState3DOF

    @property
    def is_terminal(self) -> bool:
        """Yalnız ground V1 terminaldir; detector simulation'ı durdurmaz."""

        return self.event_type is FlightEventType.GROUND


def _localized_state(
    *,
    start_point: IntegrationPoint3DOF,
    end_point: IntegrationPoint3DOF,
    interpolation_fraction: float,
) -> TranslationalState3DOF:
    """İki accepted state arasında repairsiz doğrusal event-state tahmini."""

    alpha = interpolation_fraction
    if not isfinite(alpha) or not 0.0 <= alpha <= 1.0:
        raise EventDetectionError(
            error_code="INVALID_EVENT_LOCALIZATION",
            field_name="interpolation_fraction",
            value=alpha,
        )
    position = start_point.state.position_world_m + alpha * (
        end_point.state.position_world_m - start_point.state.position_world_m
    )
    velocity = start_point.state.velocity_world_m_s + alpha * (
        end_point.state.velocity_world_m_s - start_point.state.velocity_world_m_s
    )
    if not np.all(np.isfinite(position)) or not np.all(np.isfinite(velocity)):
        raise EventDetectionError(
            error_code="INVALID_EVENT_LOCALIZATION",
            field_name="estimated_state",
            value=(tuple(position), tuple(velocity)),
        )
    return TranslationalState3DOF(
        position_world_m=position,
        velocity_world_m_s=velocity,
    )


def _occurrence(
    *,
    event_type: FlightEventType,
    event_time_s: float,
    alpha: float,
    start_point: IntegrationPoint3DOF,
    end_point: IntegrationPoint3DOF,
) -> FlightEventOccurrence3DOF:
    """Ortak alpha/time invariant'leriyle yeni occurrence kur."""

    if not isfinite(event_time_s):
        raise EventDetectionError(
            error_code="INVALID_EVENT_LOCALIZATION",
            field_name="event_time_s",
            value=event_time_s,
        )
    return FlightEventOccurrence3DOF(
        event_type=event_type,
        time_s=event_time_s,
        interpolation_fraction=alpha,
        estimated_state=_localized_state(
            start_point=start_point,
            end_point=end_point,
            interpolation_fraction=alpha,
        ),
    )


_EVENT_TIE_ORDER = {
    FlightEventType.BURNOUT: 0,
    FlightEventType.APOGEE: 1,
    FlightEventType.GROUND: 2,
}


class FlightEventDetector3DOF:
    """Geçmişsiz ve physics response üretmeyen 3DOF bracket-event detectorü."""

    __slots__ = ()

    def detect(
        self,
        *,
        start_point: IntegrationPoint3DOF,
        end_point: IntegrationPoint3DOF,
        context: FlightEventDetectionContext3DOF,
    ) -> tuple[FlightEventOccurrence3DOF, ...]:
        """Tüm V1 crossing'lerini localized ve deterministic sırada döndür."""

        start_time = start_point.time_s
        end_time = end_point.time_s
        if end_time <= start_time:
            raise EventDetectionError(
                error_code="NON_INCREASING_EVENT_INTERVAL",
                field_name="end_point.time_s",
                value=end_time,
            )
        interval_duration = end_time - start_time
        if not isfinite(interval_duration):
            raise EventDetectionError(
                error_code="INVALID_EVENT_LOCALIZATION",
                field_name="event_interval_duration_s",
                value=interval_duration,
            )

        # Profile constructor zaten doğrular; bu audit corrupted/bypassed nesneye
        # de silent fallback uygulanmamasını garanti eder.
        profile = context.model_profile
        _validate_model(
            profile.burnout_detection_model,
            expected=BurnoutDetectionModel,
            field_name="burnout_detection_model",
            error_code="UNSUPPORTED_BURNOUT_DETECTION_MODEL",
        )
        _validate_model(
            profile.apogee_detection_model,
            expected=ApogeeDetectionModel,
            field_name="apogee_detection_model",
            error_code="UNSUPPORTED_APOGEE_DETECTION_MODEL",
        )
        _validate_model(
            profile.ground_reference_model,
            expected=GroundReferenceModel,
            field_name="ground_reference_model",
            error_code="UNSUPPORTED_GROUND_REFERENCE_MODEL",
        )
        _validate_model(
            profile.localization_model,
            expected=EventLocalizationModel,
            field_name="localization_model",
            error_code="UNSUPPORTED_EVENT_LOCALIZATION_MODEL",
        )

        events: list[FlightEventOccurrence3DOF] = []

        # Burnout authority yalnız accepted ignition + full canonical curve end.
        burnout_time = (
            context.propulsion_timeline.ignition_time_s
            + context.motor_curve_statistics.curve_end_time_s
        )
        if not isfinite(burnout_time):
            raise EventDetectionError(
                error_code="NONFINITE_EVENT_BOUNDARY_TIME",
                field_name="burnout_time_s",
                value=burnout_time,
            )
        if start_time < burnout_time <= end_time:
            burnout_alpha = (burnout_time - start_time) / interval_duration
            events.append(
                _occurrence(
                    event_type=FlightEventType.BURNOUT,
                    event_time_s=burnout_time,
                    alpha=burnout_alpha,
                    start_point=start_point,
                    end_point=end_point,
                )
            )

        start_vz = float(start_point.state.velocity_world_m_s[2])
        end_vz = float(end_point.state.velocity_world_m_s[2])
        if start_vz > 0.0 and end_vz <= 0.0:
            apogee_alpha = start_vz / (start_vz - end_vz)
            apogee_time = start_time + apogee_alpha * interval_duration
            events.append(
                _occurrence(
                    event_type=FlightEventType.APOGEE,
                    event_time_s=apogee_time,
                    alpha=apogee_alpha,
                    start_point=start_point,
                    end_point=end_point,
                )
            )

        ground_z = float(context.launch_conditions.initial_position_world_m[2])
        start_height = float(start_point.state.position_world_m[2]) - ground_z
        end_height = float(end_point.state.position_world_m[2]) - ground_z
        if start_height > 0.0 and end_height <= 0.0:
            ground_alpha = start_height / (start_height - end_height)
            ground_time = start_time + ground_alpha * interval_duration
            events.append(
                _occurrence(
                    event_type=FlightEventType.GROUND,
                    event_time_s=ground_time,
                    alpha=ground_alpha,
                    start_point=start_point,
                    end_point=end_point,
                )
            )

        events.sort(key=lambda event: (event.time_s, _EVENT_TIE_ORDER[event.event_type]))
        return tuple(events)
