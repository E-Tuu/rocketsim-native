"""INT-002: açık araç girdili JSON sözleşmesi 1.1.

Bu katman yalnız doğrulanmış dış alanları accepted Native kurucularına eşler.
Geometri, kütle, propulsion, aerodinamik ve simülasyon denklemleri mevcut
üretim katmanlarında kalır.
"""

from dataclasses import dataclass
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
from roketsim_native.materials.models import BulkMaterial, SingleStageRocketMaterials
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

from roketsim_native.integration.json_bridge import (
    MODEL_ID,
    MOTOR_ID,
    IntegrationRequestError,
    _exact_fields,
    _integer,
    _number,
    _object,
    _request_error,
    _string,
    _vector3,
)

SCHEMA_VERSION_V11 = "1.1"
CAPABILITIES_OPERATION = "capabilities"
SIMULATE_OPERATION = "simulate"

MATERIALS_BY_ID: dict[str, BulkMaterial] = {
    "POLYSTYRENE": POLYSTYRENE,
    "CARDBOARD": CARDBOARD,
}
MOTORS_BY_ID = {MOTOR_ID: AEROTECH_F50_4T}
SUPPORTED_COMPONENT_TYPES = ("nose_cone", "body_tube", "fin_set")

__all__ = (
    "SCHEMA_VERSION_V11",
    "CapabilitiesRequestV11",
    "ExplicitVehicleRequestV11",
    "parse_v11_request",
    "capabilities_response",
    "build_explicit_simulation_configuration",
    "run_explicit_request",
)


@dataclass(frozen=True, slots=True)
class NoseComponentV11:
    component_id: str
    axial_position_m: float
    length_m: float
    base_diameter_m: float
    wall_thickness_m: float
    material_id: str


@dataclass(frozen=True, slots=True)
class BodyTubeComponentV11:
    component_id: str
    axial_position_m: float
    length_m: float
    outer_diameter_m: float
    wall_thickness_m: float
    material_id: str


@dataclass(frozen=True, slots=True)
class FinSetComponentV11:
    component_id: str
    axial_position_m: float
    count: int
    root_chord_m: float
    tip_chord_m: float
    semi_span_m: float
    sweep_length_m: float
    thickness_m: float
    material_id: str


@dataclass(frozen=True, slots=True)
class MotorInstallationInputV11:
    axial_position_m: float
    mount_tube_length_m: float
    mount_tube_inner_diameter_m: float
    mount_tube_wall_thickness_m: float
    mount_tube_aft_recess_m: float
    mount_tube_material_id: str
    centering_ring_axial_thickness_m: float
    centering_ring_material_id: str
    motor_overhang_m: float


@dataclass(frozen=True, slots=True)
class CapabilitiesRequestV11:
    schema_version: str
    operation: str


@dataclass(frozen=True, slots=True)
class ExplicitVehicleRequestV11:
    schema_version: str
    operation: str
    model: str
    nose: NoseComponentV11
    body: BodyTubeComponentV11
    fins: FinSetComponentV11
    motor_installation: MotorInstallationInputV11
    equivalent_roughness_m: float
    motor_id: str
    ignition_time_s: float
    position_world_m: tuple[float, float, float]
    velocity_world_m_s: tuple[float, float, float]
    direction_world_unit: tuple[float, float, float]
    geopotential_altitude_m: float
    air_mass_velocity_world_m_s: tuple[float, float, float]
    fixed_step_s: float
    maximum_steps: int


def _supported(
    value: object,
    *,
    field: str,
    accepted: str,
    error_code: str,
) -> str:
    identifier = _string(value, field=field)
    if identifier != accepted:
        raise _request_error(
            code=error_code,
            field=field,
            value=identifier,
            message=f"{field} desteklenmiyor: {identifier!r}",
        )
    return identifier


def _component_id(value: object, *, field: str) -> str:
    identifier = _string(value, field=field)
    if not identifier.strip():
        raise _request_error(
            code="INVALID_COMPONENT",
            field=field,
            value=identifier,
            message=f"{field} boş olamaz",
        )
    return identifier


def _material_id(value: object, *, field: str) -> str:
    identifier = _string(value, field=field)
    if identifier not in MATERIALS_BY_ID:
        raise _request_error(
            code="UNSUPPORTED_MATERIAL",
            field=field,
            value=identifier,
            message=f"{field} desteklenmiyor: {identifier!r}",
        )
    return identifier


def _parse_nose(component: dict[str, Any], *, index: int) -> NoseComponentV11:
    path = f"vehicle.components[{index}]"
    _exact_fields(
        component,
        field=path,
        required={
            "id", "type", "shape", "construction_mode", "axial_position_m",
            "length_m", "base_diameter_m", "wall_thickness_m", "material_id",
        },
    )
    _supported(
        component["shape"], field=f"{path}.shape", accepted="conical",
        error_code="UNSUPPORTED_NOSE_SHAPE",
    )
    _supported(
        component["construction_mode"],
        field=f"{path}.construction_mode",
        accepted=NoseConstructionMode.HOLLOW_SHELL.value,
        error_code="INVALID_COMPONENT",
    )
    return NoseComponentV11(
        component_id=_component_id(component["id"], field=f"{path}.id"),
        axial_position_m=_number(
            component["axial_position_m"], field=f"{path}.axial_position_m"
        ),
        length_m=_number(component["length_m"], field=f"{path}.length_m"),
        base_diameter_m=_number(
            component["base_diameter_m"], field=f"{path}.base_diameter_m"
        ),
        wall_thickness_m=_number(
            component["wall_thickness_m"], field=f"{path}.wall_thickness_m"
        ),
        material_id=_material_id(
            component["material_id"], field=f"{path}.material_id"
        ),
    )


def _parse_body(component: dict[str, Any], *, index: int) -> BodyTubeComponentV11:
    path = f"vehicle.components[{index}]"
    _exact_fields(
        component,
        field=path,
        required={
            "id", "type", "axial_position_m", "length_m", "outer_diameter_m",
            "wall_thickness_m", "material_id",
        },
    )
    return BodyTubeComponentV11(
        component_id=_component_id(component["id"], field=f"{path}.id"),
        axial_position_m=_number(
            component["axial_position_m"], field=f"{path}.axial_position_m"
        ),
        length_m=_number(component["length_m"], field=f"{path}.length_m"),
        outer_diameter_m=_number(
            component["outer_diameter_m"], field=f"{path}.outer_diameter_m"
        ),
        wall_thickness_m=_number(
            component["wall_thickness_m"], field=f"{path}.wall_thickness_m"
        ),
        material_id=_material_id(
            component["material_id"], field=f"{path}.material_id"
        ),
    )


def _parse_fins(component: dict[str, Any], *, index: int) -> FinSetComponentV11:
    path = f"vehicle.components[{index}]"
    _exact_fields(
        component,
        field=path,
        required={
            "id", "type", "axial_position_m", "count", "root_chord_m",
            "tip_chord_m", "semi_span_m", "sweep_length_m", "thickness_m",
            "cross_section", "angular_arrangement", "material_id",
        },
    )
    _supported(
        component["cross_section"], field=f"{path}.cross_section",
        accepted=FinCrossSection.SQUARE.value,
        error_code="UNSUPPORTED_FIN_CROSS_SECTION",
    )
    _supported(
        component["angular_arrangement"],
        field=f"{path}.angular_arrangement",
        accepted=FinAngularArrangement.EQUALLY_SPACED.value,
        error_code="INVALID_COMPONENT",
    )
    return FinSetComponentV11(
        component_id=_component_id(component["id"], field=f"{path}.id"),
        axial_position_m=_number(
            component["axial_position_m"], field=f"{path}.axial_position_m"
        ),
        count=_integer(component["count"], field=f"{path}.count"),
        root_chord_m=_number(
            component["root_chord_m"], field=f"{path}.root_chord_m"
        ),
        tip_chord_m=_number(
            component["tip_chord_m"], field=f"{path}.tip_chord_m"
        ),
        semi_span_m=_number(
            component["semi_span_m"], field=f"{path}.semi_span_m"
        ),
        sweep_length_m=_number(
            component["sweep_length_m"], field=f"{path}.sweep_length_m"
        ),
        thickness_m=_number(
            component["thickness_m"], field=f"{path}.thickness_m"
        ),
        material_id=_material_id(
            component["material_id"], field=f"{path}.material_id"
        ),
    )


def _parse_components(
    value: object,
) -> tuple[NoseComponentV11, BodyTubeComponentV11, FinSetComponentV11]:
    if type(value) is not list:
        raise _request_error(
            code="INVALID_COMPONENT", field="vehicle.components", value=value,
            message="vehicle.components bir JSON dizisi olmalıdır",
        )
    parsed: list[object] = []
    identifiers: set[str] = set()
    for index, raw_component in enumerate(value):
        path = f"vehicle.components[{index}]"
        component = _object(raw_component, field=path)
        if "id" not in component or "type" not in component:
            raise _request_error(
                code="INVALID_COMPONENT", field=path, value=component,
                message=f"{path} id ve type alanlarını içermelidir",
            )
        component_id = _component_id(component["id"], field=f"{path}.id")
        if component_id in identifiers:
            raise _request_error(
                code="DUPLICATE_COMPONENT_ID", field=f"{path}.id",
                value=component_id, message=f"Tekrarlanan component id: {component_id!r}",
            )
        identifiers.add(component_id)
        component_type = _string(component["type"], field=f"{path}.type")
        parser = {
            "nose_cone": _parse_nose,
            "body_tube": _parse_body,
            "fin_set": _parse_fins,
        }.get(component_type)
        if parser is None:
            raise _request_error(
                code="UNSUPPORTED_COMPONENT_TYPE", field=f"{path}.type",
                value=component_type,
                message=f"Desteklenmeyen component type: {component_type!r}",
            )
        parsed.append(parser(component, index=index))

    noses = [item for item in parsed if isinstance(item, NoseComponentV11)]
    bodies = [item for item in parsed if isinstance(item, BodyTubeComponentV11)]
    fin_sets = [item for item in parsed if isinstance(item, FinSetComponentV11)]
    if len(noses) != 1 or len(bodies) != 1 or len(fin_sets) != 1:
        raise _request_error(
            code="INVALID_COMPONENT", field="vehicle.components",
            value={"nose_cone": len(noses), "body_tube": len(bodies), "fin_set": len(fin_sets)},
            message="V1.1 tam bir nose_cone, body_tube ve fin_set gerektirir",
        )
    return noses[0], bodies[0], fin_sets[0]


def _parse_motor_installation(value: object) -> MotorInstallationInputV11:
    path = "vehicle.motor_installation"
    installation = _object(value, field=path)
    _exact_fields(
        installation,
        field=path,
        required={"axial_position_m", "motor_overhang_m", "mount_tube", "centering_rings"},
    )
    mount = _object(installation["mount_tube"], field=f"{path}.mount_tube")
    rings = _object(
        installation["centering_rings"], field=f"{path}.centering_rings"
    )
    _exact_fields(
        mount,
        field=f"{path}.mount_tube",
        required={"length_m", "inner_diameter_m", "wall_thickness_m", "aft_recess_m", "material_id"},
    )
    _exact_fields(
        rings,
        field=f"{path}.centering_rings",
        required={"axial_thickness_m", "material_id"},
    )
    return MotorInstallationInputV11(
        axial_position_m=_number(
            installation["axial_position_m"], field=f"{path}.axial_position_m"
        ),
        motor_overhang_m=_number(
            installation["motor_overhang_m"], field=f"{path}.motor_overhang_m"
        ),
        mount_tube_length_m=_number(
            mount["length_m"], field=f"{path}.mount_tube.length_m"
        ),
        mount_tube_inner_diameter_m=_number(
            mount["inner_diameter_m"], field=f"{path}.mount_tube.inner_diameter_m"
        ),
        mount_tube_wall_thickness_m=_number(
            mount["wall_thickness_m"], field=f"{path}.mount_tube.wall_thickness_m"
        ),
        mount_tube_aft_recess_m=_number(
            mount["aft_recess_m"], field=f"{path}.mount_tube.aft_recess_m"
        ),
        mount_tube_material_id=_material_id(
            mount["material_id"], field=f"{path}.mount_tube.material_id"
        ),
        centering_ring_axial_thickness_m=_number(
            rings["axial_thickness_m"],
            field=f"{path}.centering_rings.axial_thickness_m",
        ),
        centering_ring_material_id=_material_id(
            rings["material_id"], field=f"{path}.centering_rings.material_id"
        ),
    )


def parse_v11_request(
    decoded: dict[str, Any],
) -> CapabilitiesRequestV11 | ExplicitVehicleRequestV11:
    """Önceden JSON olarak çözümlenmiş V1.1 operation nesnesini doğrula."""

    operation = _string(decoded.get("operation"), field="operation")
    _supported(
        decoded.get("schema_version"), field="schema_version",
        accepted=SCHEMA_VERSION_V11, error_code="UNSUPPORTED_SCHEMA_VERSION",
    )
    if operation == CAPABILITIES_OPERATION:
        _exact_fields(
            decoded, field="request", required={"schema_version", "operation"}
        )
        return CapabilitiesRequestV11(SCHEMA_VERSION_V11, operation)
    if operation != SIMULATE_OPERATION:
        raise _request_error(
            code="INVALID_REQUEST", field="operation", value=operation,
            message=f"Desteklenmeyen operation: {operation!r}",
        )

    _exact_fields(
        decoded,
        field="request",
        required={"schema_version", "operation", "model", "vehicle", "motor", "launch", "environment", "numerics"},
    )
    _supported(
        decoded["model"], field="model", accepted=MODEL_ID,
        error_code="UNSUPPORTED_MODEL",
    )
    vehicle = _object(decoded["vehicle"], field="vehicle")
    motor = _object(decoded["motor"], field="motor")
    launch = _object(decoded["launch"], field="launch")
    environment = _object(decoded["environment"], field="environment")
    numerics = _object(decoded["numerics"], field="numerics")
    _exact_fields(
        vehicle,
        field="vehicle",
        required={"reference_geometry_policy", "components", "motor_installation", "surface_finish"},
    )
    _supported(
        vehicle["reference_geometry_policy"],
        field="vehicle.reference_geometry_policy",
        accepted=ReferenceGeometryPolicy.MAXIMUM_DIAMETER.value,
        error_code="INVALID_REQUEST",
    )
    nose, body, fins = _parse_components(vehicle["components"])
    installation = _parse_motor_installation(vehicle["motor_installation"])
    surface = _object(vehicle["surface_finish"], field="vehicle.surface_finish")
    _exact_fields(
        surface,
        field="vehicle.surface_finish",
        required={"equivalent_roughness_m"},
    )
    _exact_fields(motor, field="motor", required={"id", "ignition_time_s"})
    _supported(
        motor["id"], field="motor.id", accepted=MOTOR_ID,
        error_code="UNSUPPORTED_MOTOR",
    )
    _exact_fields(
        launch,
        field="launch",
        required={"position_world_m", "velocity_world_m_s", "direction_world_unit"},
    )
    _exact_fields(
        environment,
        field="environment",
        required={"geopotential_altitude_m", "air_mass_velocity_world_m_s"},
    )
    _exact_fields(
        numerics, field="numerics", required={"fixed_step_s", "maximum_steps"}
    )
    return ExplicitVehicleRequestV11(
        schema_version=SCHEMA_VERSION_V11,
        operation=operation,
        model=MODEL_ID,
        nose=nose,
        body=body,
        fins=fins,
        motor_installation=installation,
        equivalent_roughness_m=_number(
            surface["equivalent_roughness_m"],
            field="vehicle.surface_finish.equivalent_roughness_m",
        ),
        motor_id=MOTOR_ID,
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


def capabilities_response() -> dict[str, Any]:
    """Yalnız gerçekten V1.1 tarafından accepted edilen seçenekleri yayımla."""

    return {
        "schema_version": SCHEMA_VERSION_V11,
        "ok": True,
        "operation": CAPABILITIES_OPERATION,
        "models": [MODEL_ID],
        "vehicle": {
            "component_types": list(SUPPORTED_COMPONENT_TYPES),
            "nose_shapes": ["conical"],
            "nose_construction_modes": [NoseConstructionMode.HOLLOW_SHELL.value],
            "fin_cross_sections": [FinCrossSection.SQUARE.value],
            "fin_angular_arrangements": [FinAngularArrangement.EQUALLY_SPACED.value],
            "reference_geometry_policies": [ReferenceGeometryPolicy.MAXIMUM_DIAMETER.value],
            "materials": [
                {"id": material_id, "display_name": material.name}
                for material_id, material in MATERIALS_BY_ID.items()
            ],
        },
        "motors": [
            {
                "id": motor_id,
                "display_name": f"{motor.manufacturer} {motor.designation}",
            }
            for motor_id, motor in MOTORS_BY_ID.items()
        ],
    }


def _require_exact_mapping(
    *, actual: float, expected: float, field: str, semantic: str
) -> None:
    """Dış yerleşim alanının accepted resolved primitive ile exact eşleşmesini iste."""

    if actual != expected:
        raise IntegrationRequestError(
            error_code="INVALID_COMPONENT",
            field_name=field,
            value=expected,
            message=f"{field}, accepted {semantic} değeri {actual!r} ile eşleşmelidir",
        )


def build_explicit_simulation_configuration(
    request: ExplicitVehicleRequestV11,
) -> SimulationRunConfiguration3DOF:
    """Açık V1.1 alanlarını accepted üretim constructor ve resolver'larına aktar."""

    installation_input = request.motor_installation
    source_geometry = SingleStageRocketGeometry(
        request.nose.base_diameter_m,
        ConicalNoseGeometry(
            request.nose.length_m,
            NoseConstructionMode.HOLLOW_SHELL,
            request.nose.wall_thickness_m,
        ),
        CylindricalBodyGeometry(
            request.body.length_m,
            request.body.wall_thickness_m,
        ),
        TrapezoidalFinSetGeometry(
            request.fins.count,
            request.fins.root_chord_m,
            request.fins.tip_chord_m,
            request.fins.semi_span_m,
            request.fins.sweep_length_m,
            request.fins.axial_position_m,
            request.fins.thickness_m,
            FinCrossSection.SQUARE,
            FinAngularArrangement.EQUALLY_SPACED,
        ),
        MotorAttachmentGeometry(
            MotorMountTubeGeometry(
                installation_input.mount_tube_length_m,
                installation_input.mount_tube_inner_diameter_m,
                installation_input.mount_tube_wall_thickness_m,
                installation_input.mount_tube_aft_recess_m,
            ),
            CenteringRingPairGeometry(
                installation_input.centering_ring_axial_thickness_m
            ),
            installation_input.motor_overhang_m,
        ),
        ReferenceGeometryPolicy.MAXIMUM_DIAMETER,
    )
    _require_exact_mapping(
        actual=request.nose.base_diameter_m,
        expected=request.body.outer_diameter_m,
        field="vehicle.components.body_tube.outer_diameter_m",
        semantic="airframe_diameter_m",
    )
    geometry = GeometryResolver().resolve(rocket_geometry=source_geometry)
    _require_exact_mapping(
        actual=geometry.nose_start_x_geo_m,
        expected=request.nose.axial_position_m,
        field="vehicle.components.nose_cone.axial_position_m",
        semantic="nose_start_x_geo_m",
    )
    _require_exact_mapping(
        actual=geometry.body_start_x_geo_m,
        expected=request.body.axial_position_m,
        field="vehicle.components.body_tube.axial_position_m",
        semantic="body_start_x_geo_m",
    )
    _require_exact_mapping(
        actual=geometry.motor_aft_reference_x_geo_m,
        expected=installation_input.axial_position_m,
        field="vehicle.motor_installation.axial_position_m",
        semantic="motor_aft_reference_x_geo_m",
    )

    materials = SingleStageRocketMaterials(
        MATERIALS_BY_ID[request.nose.material_id],
        MATERIALS_BY_ID[request.body.material_id],
        MATERIALS_BY_ID[request.fins.material_id],
        MATERIALS_BY_ID[installation_input.mount_tube_material_id],
        MATERIALS_BY_ID[installation_input.centering_ring_material_id],
    )
    structure = StructuralMassPropertiesCalculator().evaluate(
        resolved_geometry=geometry,
        materials=materials,
    )
    motor = MOTORS_BY_ID[request.motor_id]
    installation = MotorInstallationResolver().resolve(
        resolved_geometry=geometry,
        motor=motor,
    )
    surface = SurfaceFinish(
        "INT-002 explicit vehicle surface",
        request.equivalent_roughness_m,
    )
    surfaces = SingleStageRocketAerodynamicSurfaces(surface, surface, surface)
    statistics = MotorCurveAnalyzer().analyze(motor=motor)
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
        translational_dynamics_model_profile=NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
        environment_position_mapping_model=EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,
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
        terminal_event_handling_model=TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT,
    )


def run_explicit_request(request: ExplicitVehicleRequestV11) -> SimulationResult3DOF:
    """Accepted SimulationEngine3DOF zincirini açık V1.1 config ile çalıştır."""

    configuration = build_explicit_simulation_configuration(request)
    execution = SimulationEngine3DOF().run(configuration=configuration)
    return SimulationResult3DOF(execution=execution)
