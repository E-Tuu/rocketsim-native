"""INT-001/002: sürümlü JSON isteklerini accepted Native zincirine bağlar.

Bu modül yalnız dış sözleşmeyi doğrular, accepted domain/configuration
nesnelerini kurar ve accepted sonuçları JSON değerlerine dönüştürür.
"""

from dataclasses import dataclass
import json
from math import isfinite
from typing import Any

from roketsim_native.aerodynamics.drag import NATIVE_BASIC_DRAG_V1_PROFILE
from roketsim_native.aerodynamics.surfaces import (
    SingleStageRocketAerodynamicSurfaces,
    SurfaceFinish,
)
from roketsim_native.dynamics.initial_state import LaunchConditions3DOF
from roketsim_native.dynamics.translational import (
    NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
)
from roketsim_native.environment.air_properties import DryAirPropertiesCalculator
from roketsim_native.environment.atmosphere import USStandardAtmosphere1976Lower
from roketsim_native.environment.gravity import ConstantGravityModel
from roketsim_native.environment.wind import ConstantWindModel
from roketsim_native.geometry.models import (
    CenteringRingPairGeometry,
    ConicalNoseGeometry,
    CylindricalBodyGeometry,
    FinAngularArrangement,
    FinCrossSection,
    MotorAttachmentGeometry,
    MotorMountTubeGeometry,
    NoseConstructionMode,
    ReferenceGeometryPolicy,
    SingleStageRocketGeometry,
    TrapezoidalFinSetGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.materials.models import SingleStageRocketMaterials
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator
from roketsim_native.numerics.fixed_step import FixedStepConfig
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T
from roketsim_native.propulsion.installation import MotorInstallationResolver
from roketsim_native.propulsion.properties import DEMO_MOTOR_PROPERTY_MODEL_PROFILE
from roketsim_native.propulsion.thrust import MotorCurveAnalyzer
from roketsim_native.simulation.engine import (
    SimulationEngine3DOF,
    SimulationRunConfiguration3DOF,
    SimulationRunLimits3DOF,
    TerminalEventHandlingModel,
)
from roketsim_native.simulation.events import (
    ApogeeDetectionModel,
    BurnoutDetectionModel,
    EventLocalizationModel,
    FlightEventDetectionProfile3DOF,
    GroundReferenceModel,
)
from roketsim_native.simulation.physics import (
    EnvironmentPositionMappingModel,
    PhysicsEvaluationContext3DOF,
    PropulsionTimeline,
)
from roketsim_native.simulation.result import SimulationResult3DOF

SCHEMA_VERSION = "1.0"
MODEL_ID = "roketsim_native_3dof_v1"
VEHICLE_PRESET_ID = "verified_demo_vehicle_v1"
MOTOR_ID = "AEROTECH_F50_4T"

REQUEST_EXIT_CODE = 2
SIMULATION_EXIT_CODE = 3
INTERNAL_EXIT_CODE = 70

__all__ = (
    "SCHEMA_VERSION",
    "MODEL_ID",
    "VEHICLE_PRESET_ID",
    "MOTOR_ID",
    "BridgeRequestV1",
    "IntegrationRequestError",
    "parse_request_json",
    "build_simulation_configuration",
    "run_request",
    "serialize_result",
    "process_request_json",
)


class IntegrationRequestError(ValueError):
    """Dış JSON sözleşmesine ait kararlı ve yapılandırılmış istek hatası."""

    def __init__(
        self,
        *,
        error_code: str,
        field_name: str | None,
        value: object,
        message: str,
    ) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        self.message = message
        super().__init__(message)


@dataclass(frozen=True, slots=True)
class BridgeRequestV1:
    """Doğrulanmış V1 dış isteğinin physics-authority olmayan taşıma modeli."""

    schema_version: str
    model: str
    vehicle_preset: str
    motor_id: str
    ignition_time_s: float
    position_world_m: tuple[float, float, float]
    velocity_world_m_s: tuple[float, float, float]
    direction_world_unit: tuple[float, float, float]
    geopotential_altitude_m: float
    air_mass_velocity_world_m_s: tuple[float, float, float]
    fixed_step_s: float
    maximum_steps: int


def _request_error(
    *,
    code: str,
    field: str | None,
    value: object,
    message: str,
) -> IntegrationRequestError:
    return IntegrationRequestError(
        error_code=code,
        field_name=field,
        value=value,
        message=message,
    )


def _reject_nonstandard_number(token: str) -> None:
    """Python JSON genişletmesi olan NaN/Infinity token'larını reddet."""

    raise _request_error(
        code="INVALID_REQUEST",
        field=None,
        value=token,
        message="JSON sayıları sonlu olmalıdır",
    )


def _object(value: object, *, field: str) -> dict[str, Any]:
    if type(value) is not dict:
        raise _request_error(
            code="INVALID_REQUEST",
            field=field,
            value=value,
            message=f"{field} bir JSON nesnesi olmalıdır",
        )
    return value


def _exact_fields(value: dict[str, Any], *, field: str, required: set[str]) -> None:
    missing = sorted(required - value.keys())
    unknown = sorted(value.keys() - required)
    if missing or unknown:
        details = []
        if missing:
            details.append(f"eksik={missing}")
        if unknown:
            details.append(f"bilinmeyen={unknown}")
        raise _request_error(
            code="INVALID_REQUEST",
            field=field,
            value={"missing": missing, "unknown": unknown},
            message=f"{field} alanları geçersiz: {', '.join(details)}",
        )


def _string(value: object, *, field: str) -> str:
    if type(value) is not str:
        raise _request_error(
            code="INVALID_REQUEST",
            field=field,
            value=value,
            message=f"{field} bir string olmalıdır",
        )
    return value


def _number(value: object, *, field: str) -> float:
    if type(value) not in (int, float):
        raise _request_error(
            code="INVALID_REQUEST",
            field=field,
            value=value,
            message=f"{field} bir sayı olmalıdır",
        )
    converted = float(value)
    if not isfinite(converted):
        raise _request_error(
            code="INVALID_REQUEST",
            field=field,
            value=value,
            message=f"{field} sonlu olmalıdır",
        )
    return converted


def _integer(value: object, *, field: str) -> int:
    if type(value) is not int:
        raise _request_error(
            code="INVALID_REQUEST",
            field=field,
            value=value,
            message=f"{field} bir integer olmalıdır",
        )
    return value


def _vector3(value: object, *, field: str) -> tuple[float, float, float]:
    if type(value) is not list or len(value) != 3:
        raise _request_error(
            code="INVALID_REQUEST",
            field=field,
            value=value,
            message=f"{field} tam üç elemanlı bir JSON dizisi olmalıdır",
        )
    return (
        _number(value[0], field=f"{field}[0]"),
        _number(value[1], field=f"{field}[1]"),
        _number(value[2], field=f"{field}[2]"),
    )


def _require_identifier(
    value: object,
    *,
    field: str,
    expected: str,
    error_code: str,
) -> str:
    identifier = _string(value, field=field)
    if identifier != expected:
        raise _request_error(
            code=error_code,
            field=field,
            value=identifier,
            message=f"{field} desteklenmiyor: {identifier!r}",
        )
    return identifier


def _decode_request_json(source: str) -> object:
    """Standart dışı sayıları da reddederek tek JSON değerini çöz."""
    try:
        return json.loads(source, parse_constant=_reject_nonstandard_number)
    except IntegrationRequestError:
        raise
    except (json.JSONDecodeError, TypeError) as exc:
        raise _request_error(
            code="INVALID_JSON",
            field=None,
            value=None,
            message=f"Geçersiz JSON: {exc.msg if isinstance(exc, json.JSONDecodeError) else exc}",
        ) from None


def parse_request_json(source: str) -> BridgeRequestV1:
    """Tek V1.0 JSON nesnesini eksik ve bilinmeyen alanlara kapalı doğrula."""

    return _parse_v10_request(_decode_request_json(source))


def _parse_v10_request(decoded: object) -> BridgeRequestV1:
    """Çözümlenmiş JSON değerini geriye uyumlu V1.0 isteği olarak doğrula."""

    root = _object(decoded, field="request")
    _exact_fields(
        root,
        field="request",
        required={
            "schema_version",
            "model",
            "vehicle",
            "motor",
            "launch",
            "environment",
            "numerics",
        },
    )
    vehicle = _object(root["vehicle"], field="vehicle")
    motor = _object(root["motor"], field="motor")
    launch = _object(root["launch"], field="launch")
    environment = _object(root["environment"], field="environment")
    numerics = _object(root["numerics"], field="numerics")
    _exact_fields(vehicle, field="vehicle", required={"preset"})
    _exact_fields(motor, field="motor", required={"id", "ignition_time_s"})
    _exact_fields(
        launch,
        field="launch",
        required={
            "position_world_m",
            "velocity_world_m_s",
            "direction_world_unit",
        },
    )
    _exact_fields(
        environment,
        field="environment",
        required={
            "geopotential_altitude_m",
            "air_mass_velocity_world_m_s",
        },
    )
    _exact_fields(
        numerics,
        field="numerics",
        required={"fixed_step_s", "maximum_steps"},
    )

    schema_version = _require_identifier(
        root["schema_version"],
        field="schema_version",
        expected=SCHEMA_VERSION,
        error_code="UNSUPPORTED_SCHEMA_VERSION",
    )
    model = _require_identifier(
        root["model"],
        field="model",
        expected=MODEL_ID,
        error_code="UNSUPPORTED_MODEL",
    )
    vehicle_preset = _require_identifier(
        vehicle["preset"],
        field="vehicle.preset",
        expected=VEHICLE_PRESET_ID,
        error_code="UNSUPPORTED_VEHICLE_PRESET",
    )
    motor_id = _require_identifier(
        motor["id"],
        field="motor.id",
        expected=MOTOR_ID,
        error_code="UNSUPPORTED_MOTOR",
    )

    return BridgeRequestV1(
        schema_version=schema_version,
        model=model,
        vehicle_preset=vehicle_preset,
        motor_id=motor_id,
        ignition_time_s=_number(
            motor["ignition_time_s"], field="motor.ignition_time_s"
        ),
        position_world_m=_vector3(
            launch["position_world_m"], field="launch.position_world_m"
        ),
        velocity_world_m_s=_vector3(
            launch["velocity_world_m_s"], field="launch.velocity_world_m_s"
        ),
        direction_world_unit=_vector3(
            launch["direction_world_unit"], field="launch.direction_world_unit"
        ),
        geopotential_altitude_m=_number(
            environment["geopotential_altitude_m"],
            field="environment.geopotential_altitude_m",
        ),
        air_mass_velocity_world_m_s=_vector3(
            environment["air_mass_velocity_world_m_s"],
            field="environment.air_mass_velocity_world_m_s",
        ),
        fixed_step_s=_number(
            numerics["fixed_step_s"], field="numerics.fixed_step_s"
        ),
        maximum_steps=_integer(
            numerics["maximum_steps"], field="numerics.maximum_steps"
        ),
    )


def build_simulation_configuration(
    request: BridgeRequestV1,
) -> SimulationRunConfiguration3DOF:
    """Sürümlü demo preset'ini accepted production domain nesneleriyle kur.

    ``verified_demo_vehicle_v1`` yalnız entegrasyon/demo preset'idir; genel bir
    araç varsayılanı değildir. İstek denetimindeki değerler burada sabitlenmez.
    """

    source_geometry = SingleStageRocketGeometry(
        0.1,
        ConicalNoseGeometry(0.3, NoseConstructionMode.HOLLOW_SHELL, 0.002),
        CylindricalBodyGeometry(0.7, 0.002),
        TrapezoidalFinSetGeometry(
            4,
            0.18,
            0.08,
            0.12,
            0.05,
            0.72,
            0.003,
            FinCrossSection.SQUARE,
            FinAngularArrangement.EQUALLY_SPACED,
        ),
        MotorAttachmentGeometry(
            MotorMountTubeGeometry(0.12, 0.029, 0.001, 0.0),
            CenteringRingPairGeometry(0.003),
            0.005,
        ),
        ReferenceGeometryPolicy.MAXIMUM_DIAMETER,
    )
    geometry = GeometryResolver().resolve(rocket_geometry=source_geometry)
    materials = SingleStageRocketMaterials(
        POLYSTYRENE,
        CARDBOARD,
        CARDBOARD,
        CARDBOARD,
        CARDBOARD,
    )
    structure = StructuralMassPropertiesCalculator().evaluate(
        resolved_geometry=geometry,
        materials=materials,
    )
    installation = MotorInstallationResolver().resolve(
        resolved_geometry=geometry,
        motor=AEROTECH_F50_4T,
    )
    smooth = SurfaceFinish("INT-001 verified demo smooth surface", 0.0)
    surfaces = SingleStageRocketAerodynamicSurfaces(smooth, smooth, smooth)
    statistics = MotorCurveAnalyzer().analyze(motor=AEROTECH_F50_4T)
    launch_conditions = LaunchConditions3DOF(
        initial_position_world_m=request.position_world_m,
        initial_velocity_world_m_s=request.velocity_world_m_s,
        launch_direction_world_unit=request.direction_world_unit,
    )
    context = PhysicsEvaluationContext3DOF(
        resolved_geometry=geometry,
        aerodynamic_surfaces=surfaces,
        structural_mass_properties=structure,
        motor_installation=installation,
        motor_property_model_profile=DEMO_MOTOR_PROPERTY_MODEL_PROFILE,
        basic_drag_model_profile=NATIVE_BASIC_DRAG_V1_PROFILE,
        translational_dynamics_model_profile=(
            NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE
        ),
        environment_position_mapping_model=(
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET
        ),
        launch_environment_altitude_m=request.geopotential_altitude_m,
        propulsion_timeline=PropulsionTimeline(request.ignition_time_s),
        atmosphere_model=USStandardAtmosphere1976Lower(),
        air_properties_calculator=DryAirPropertiesCalculator(),
        gravity_model=ConstantGravityModel(),
        wind_model=ConstantWindModel(
            airmass_velocity_world_m_s=request.air_mass_velocity_world_m_s
        ),
    )
    event_profile = FlightEventDetectionProfile3DOF(
        BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
        ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
        GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,
        EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION,
    )
    return SimulationRunConfiguration3DOF(
        launch_conditions=launch_conditions,
        physics_context=context,
        motor_curve_statistics=statistics,
        event_detection_profile=event_profile,
        fixed_step_config=FixedStepConfig(request.fixed_step_s),
        run_limits=SimulationRunLimits3DOF(request.maximum_steps),
        terminal_event_handling_model=(
            TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT
        ),
    )


def run_request(request: BridgeRequestV1) -> SimulationResult3DOF:
    """Accepted engine'i çalıştır ve accepted immutable result facade'ını kur."""

    configuration = build_simulation_configuration(request)
    execution = SimulationEngine3DOF().run(configuration=configuration)
    return SimulationResult3DOF(execution=execution)


def _vector_json(value: Any) -> list[float]:
    """Accepted üçlü vector değerini hesap yapmadan portable JSON listesine çevir."""

    return [float(component) for component in value]


def serialize_result(
    result: SimulationResult3DOF,
    *,
    schema_version: str = SCHEMA_VERSION,
) -> dict[str, Any]:
    """Accepted history ve endpoint physics alanlarını mevcut sırada dışa aç."""

    events = [
        {
            "type": event.event_type.value,
            "time_s": event.time_s,
            "interpolation_fraction": event.interpolation_fraction,
            "is_terminal": event.is_terminal,
            "estimated_state": {
                "position_world_m": _vector_json(
                    event.estimated_state.position_world_m
                ),
                "velocity_world_m_s": _vector_json(
                    event.estimated_state.velocity_world_m_s
                ),
            },
        }
        for event in result.events
    ]
    samples = []
    for sample in result.samples:
        point = sample.point
        physics = sample.physics
        samples.append(
            {
                "time_s": point.time_s,
                "state": {
                    "position_world_m": _vector_json(point.state.position_world_m),
                    "velocity_world_m_s": _vector_json(
                        point.state.velocity_world_m_s
                    ),
                },
                "physics": {
                    "total_mass_kg": physics.rocket_mass_properties.total_mass_kg,
                    "motor_mass_kg": physics.motor_mass_properties.mass_kg,
                    "thrust_n": physics.motor_thrust_state.thrust_N,
                    "mach": physics.flight_conditions.mach,
                    "reynolds_number": physics.flight_conditions.reynolds,
                    "dynamic_pressure_pa": (
                        physics.flight_conditions.dynamic_pressure_Pa
                    ),
                    "drag_coefficient_cd0": physics.basic_drag.total_cd0,
                    "acceleration_world_m_s2": _vector_json(
                        physics.dynamics.acceleration_world_m_s2
                    ),
                    "air_mass_velocity_world_m_s": _vector_json(
                        physics.air_mass_velocity_world_m_s
                    ),
                    "relative_air_velocity_world_m_s": _vector_json(
                        physics.relative_flow
                    ),
                },
            }
        )
    return {
        "schema_version": schema_version,
        "ok": True,
        "model": MODEL_ID,
        "termination": {
            "reason": result.termination_reason.value,
            "time_s": result.termination_time_s,
            "simulation_duration_s": result.simulation_duration_s,
        },
        "steps_performed": result.steps_performed,
        "events": events,
        "samples": samples,
    }


def _error_response(
    *,
    category: str,
    code: str,
    field: str | None,
    message: str,
    schema_version: str = SCHEMA_VERSION,
) -> dict[str, Any]:
    return {
        "schema_version": schema_version,
        "ok": False,
        "error": {
            "category": category,
            "code": code,
            "field": field,
            "message": message,
        },
    }


def _request_failure(
    exc: IntegrationRequestError,
    *,
    schema_version: str,
) -> tuple[dict[str, Any], int]:
    """Sürüme uygun structured request response'u üret."""

    return (
        _error_response(
            category="request",
            code=exc.error_code,
            field=exc.field_name,
            message=exc.message,
            schema_version=schema_version,
        ),
        REQUEST_EXIT_CODE,
    )


def _run_with_response(
    request: object,
    *,
    schema_version: str,
    runner: Any,
) -> tuple[dict[str, Any], int]:
    """Accepted simülasyon hatalarını kimliklerini koruyarak protokole çevir."""

    try:
        result = runner(request)
        return serialize_result(result, schema_version=schema_version), 0
    except IntegrationRequestError as exc:
        return _request_failure(exc, schema_version=schema_version)
    except ValueError as exc:
        code = getattr(exc, "error_code", type(exc).__name__)
        field = getattr(exc, "field_name", None)
        return (
            _error_response(
                category="simulation",
                code=str(code),
                field=str(field) if field is not None else None,
                message=str(exc),
                schema_version=schema_version,
            ),
            SIMULATION_EXIT_CODE,
        )
    except Exception:
        return (
            _error_response(
                category="internal",
                code="INTERNAL_ERROR",
                field=None,
                message="Beklenmeyen iç köprü hatası",
                schema_version=schema_version,
            ),
            INTERNAL_EXIT_CODE,
        )


def process_request_json(source: str) -> tuple[dict[str, Any], int]:
    """V1.0/V1.1 tek isteğini dispatch et ve CLI exit politikasını döndür."""

    try:
        decoded = _decode_request_json(source)
    except IntegrationRequestError as exc:
        return _request_failure(exc, schema_version=SCHEMA_VERSION)

    if type(decoded) is dict and "schema_version" in decoded:
        raw_version = decoded["schema_version"]
        if type(raw_version) is str and raw_version not in {SCHEMA_VERSION, "1.1"}:
            exc = _request_error(
                code="UNSUPPORTED_SCHEMA_VERSION",
                field="schema_version",
                value=raw_version,
                message=f"schema_version desteklenmiyor: {raw_version!r}",
            )
            return _request_failure(exc, schema_version=SCHEMA_VERSION)

    if type(decoded) is dict and decoded.get("schema_version") == "1.1":
        from roketsim_native.integration.v11 import (
            CapabilitiesRequestV11,
            SCHEMA_VERSION_V11,
            capabilities_response,
            parse_v11_request,
            run_explicit_request,
        )

        try:
            request_v11 = parse_v11_request(decoded)
        except IntegrationRequestError as exc:
            return _request_failure(exc, schema_version=SCHEMA_VERSION_V11)
        if isinstance(request_v11, CapabilitiesRequestV11):
            return capabilities_response(), 0
        return _run_with_response(
            request_v11,
            schema_version=SCHEMA_VERSION_V11,
            runner=run_explicit_request,
        )

    try:
        request_v10 = _parse_v10_request(decoded)
    except IntegrationRequestError as exc:
        return _request_failure(exc, schema_version=SCHEMA_VERSION)
    return _run_with_response(
        request_v10,
        schema_version=SCHEMA_VERSION,
        runner=run_request,
    )
