"""NAT-015: accepted 3DOF physics authority'lerini sırayla bağlayan orchestrator.

Bu modül upstream denklemleri kopyalamaz. Current (time, state) için environment,
flow, flight conditions, propulsion, mass, drag ve NAT-014 dynamics sonuçlarını
yeniden değerlendirir. Timestep, geçmiş, integrasyon ve static-stability kritik
yolu yoktur.
"""

from dataclasses import dataclass
from enum import Enum
from math import isfinite

import numpy as np
from numpy.typing import NDArray

from roketsim_native.aerodynamics.drag import (
    BasicDragEvaluator,
    BasicDragModelProfile,
    BasicDragResult,
)
from roketsim_native.aerodynamics.surfaces import (
    SingleStageRocketAerodynamicSurfaces,
)
from roketsim_native.dynamics.initial_state import (
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.dynamics.translational import (
    TranslationalDynamicsEvaluator,
    TranslationalDynamicsInputs,
    TranslationalDynamicsModelProfile,
    TranslationalDynamicsResult,
)
from roketsim_native.environment.air_properties import (
    DryAirProperties,
    DryAirPropertiesCalculator,
)
from roketsim_native.environment.atmosphere import (
    DryAirAtmosphereState,
    USStandardAtmosphere1976Lower,
)
from roketsim_native.environment.gravity import ConstantGravityModel
from roketsim_native.environment.wind import ConstantWindModel, NoWindModel
from roketsim_native.flight_conditions.basic import (
    BasicFlightConditions,
    BasicFlightConditionsCalculator,
)
from roketsim_native.flight_conditions.relative_flow import RelativeFlowCalculator
from roketsim_native.geometry.resolver import ResolvedRocketGeometry
from roketsim_native.mass.models import StructuralMassProperties
from roketsim_native.mass.total import (
    RocketMassProperties,
    RocketMassPropertiesCalculator,
)
from roketsim_native.math.numerical import require_finite
from roketsim_native.math.vectors import as_vector
from roketsim_native.propulsion.installation import MotorInstallation
from roketsim_native.propulsion.properties import (
    MotorMassProperties,
    MotorPropertyEvaluator,
    MotorPropertyModelProfile,
)
from roketsim_native.propulsion.thrust import (
    MotorThrustCurveEvaluator,
    MotorThrustState,
)

__all__ = (
    "EnvironmentPositionMappingModel",
    "PropulsionTimeline",
    "PhysicsEvaluationError",
    "PhysicsEvaluationContext3DOF",
    "PhysicsEvaluationResult3DOF",
    "PhysicsEvaluator3DOF",
)

_Vector3 = NDArray[np.float64]
_WindModel = NoWindModel | ConstantWindModel


class EnvironmentPositionMappingModel(str, Enum):
    """Local WORLD ENU düşey farkını accepted environment altitude'a ekler."""

    LOCAL_ENU_VERTICAL_OFFSET = "local_enu_vertical_offset"


class PhysicsEvaluationError(ValueError):
    """Yalnız NAT-015-owned finite mapping/timeline semantic hatası."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class PropulsionTimeline:
    """Simulation time'dan ignition-elapsed motor time'a mandatory mapping."""

    ignition_time_s: float

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "ignition_time_s",
            float(require_finite(self.ignition_time_s, name="ignition_time_s")),
        )


@dataclass(frozen=True, slots=True)
class PhysicsEvaluationContext3DOF:
    """Accepted authority/configuration nesnelerini kopyalamadan bağlayan bundle."""

    resolved_geometry: ResolvedRocketGeometry
    aerodynamic_surfaces: SingleStageRocketAerodynamicSurfaces
    structural_mass_properties: StructuralMassProperties
    motor_installation: MotorInstallation
    motor_property_model_profile: MotorPropertyModelProfile
    basic_drag_model_profile: BasicDragModelProfile
    translational_dynamics_model_profile: TranslationalDynamicsModelProfile
    environment_position_mapping_model: EnvironmentPositionMappingModel
    launch_environment_altitude_m: float
    propulsion_timeline: PropulsionTimeline
    atmosphere_model: USStandardAtmosphere1976Lower
    air_properties_calculator: DryAirPropertiesCalculator
    gravity_model: ConstantGravityModel
    wind_model: _WindModel

    def __post_init__(self) -> None:
        # NAT-009C API'sinin beklediği exact semantic: geopotential height (m).
        object.__setattr__(
            self,
            "launch_environment_altitude_m",
            float(
                require_finite(
                    self.launch_environment_altitude_m,
                    name="launch_environment_altitude_m",
                )
            ),
        )


def _read_only_vector3(value: object, *, field_name: str) -> _Vector3:
    """Accepted vector validation ile independent read-only 3-vector üret."""

    vector = as_vector(value, size=3, name=field_name)
    vector.flags.writeable = False
    return vector


@dataclass(frozen=True, slots=True)
class PhysicsEvaluationResult3DOF:
    """NAT-015 mappings, upstream snapshots ve final NAT-014 dynamics sonucu."""

    environment_altitude_m: float
    motor_time_s: float
    atmosphere: DryAirAtmosphereState
    air_properties: DryAirProperties
    air_mass_velocity_world_m_s: _Vector3
    relative_flow: _Vector3
    flight_conditions: BasicFlightConditions
    motor_thrust_state: MotorThrustState
    motor_mass_properties: MotorMassProperties
    rocket_mass_properties: RocketMassProperties
    basic_drag: BasicDragResult
    gravity_acceleration_world_m_s2: _Vector3
    dynamics: TranslationalDynamicsResult

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "environment_altitude_m",
            float(
                require_finite(
                    self.environment_altitude_m,
                    name="environment_altitude_m",
                )
            ),
        )
        object.__setattr__(
            self,
            "motor_time_s",
            float(require_finite(self.motor_time_s, name="motor_time_s")),
        )
        object.__setattr__(
            self,
            "air_mass_velocity_world_m_s",
            _read_only_vector3(
                self.air_mass_velocity_world_m_s,
                field_name="air_mass_velocity_world_m_s",
            ),
        )
        object.__setattr__(
            self,
            "relative_flow",
            _read_only_vector3(self.relative_flow, field_name="relative_flow"),
        )
        object.__setattr__(
            self,
            "gravity_acceleration_world_m_s2",
            _read_only_vector3(
                self.gravity_acceleration_world_m_s2,
                field_name="gravity_acceleration_world_m_s2",
            ),
        )


class PhysicsEvaluator3DOF:
    """Her çağrıda current state'ten frozen physics sırasını çalıştırır."""

    __slots__ = ()

    def evaluate(
        self,
        *,
        time_s: float,
        state: TranslationalState3DOF,
        launch_conditions: LaunchConditions3DOF,
        context: PhysicsEvaluationContext3DOF,
    ) -> PhysicsEvaluationResult3DOF:
        """Accepted subsystems'i bağla; upstream error family'lerini aynen geçir."""

        time = float(require_finite(time_s, name="time_s"))

        # 1. Local ENU düşey displacement -> NAT-009C geopotential-height girdisi.
        if (
            context.environment_position_mapping_model
            is not EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET
        ):
            raise PhysicsEvaluationError(
                error_code="UNSUPPORTED_ENVIRONMENT_POSITION_MAPPING_MODEL",
                field_name="environment_position_mapping_model",
                value=context.environment_position_mapping_model,
            )
        launch_z = float(launch_conditions.initial_position_world_m[2])
        current_z = float(state.position_world_m[2])
        delta_z = current_z - launch_z
        environment_altitude = context.launch_environment_altitude_m + delta_z
        if not isfinite(environment_altitude):
            raise PhysicsEvaluationError(
                error_code="INVALID_ENVIRONMENT_ALTITUDE_MAPPING",
                field_name="environment_altitude_m",
                value=environment_altitude,
            )

        # 2. Accepted atmosphere ve dry-air property authority'leri.
        atmosphere = context.atmosphere_model.evaluate(
            geopotential_height_m=environment_altitude
        )
        air_properties = context.air_properties_calculator.evaluate(
            atmosphere_state=atmosphere
        )

        # 3-4. Accepted WORLD gravity ve steady airmass velocity authority'leri.
        gravity = context.gravity_model.evaluate()
        air_mass_velocity = context.wind_model.evaluate()

        # 5. NAT-010A signed V_rocket - V_airmass authority'si.
        relative_flow = RelativeFlowCalculator().evaluate(
            rocket_velocity_world_m_s=state.velocity_world_m_s,
            airmass_velocity_world_m_s=air_mass_velocity,
        )

        # 6. NAT-010B V/M/Re/q authority'si; accepted reference length kullanılır.
        flight_conditions = BasicFlightConditionsCalculator().evaluate(
            relative_velocity_world_m_s=relative_flow,
            atmosphere_state=atmosphere,
            air_properties=air_properties,
            reference_length_m=context.resolved_geometry.reference_length_m,
        )

        # 7. Tek simulation-time -> motor-time mapping authority'si NAT-015'tir.
        motor_time = time - context.propulsion_timeline.ignition_time_s
        require_finite(motor_time, name="motor_time_s")
        if motor_time < 0.0:
            raise PhysicsEvaluationError(
                error_code="BEFORE_IGNITION_UNSUPPORTED",
                field_name="motor_time_s",
                value=motor_time,
            )

        # 8-9. Aynı exact motor_time thrust ve motor property evaluator'larına gider.
        motor_thrust_state = MotorThrustCurveEvaluator().evaluate(
            motor=context.motor_installation.motor,
            motor_time_s=motor_time,
        )
        motor_mass_properties = MotorPropertyEvaluator().evaluate(
            installation=context.motor_installation,
            model_profile=context.motor_property_model_profile,
            motor_time_s=motor_time,
        )

        # 10. Accepted C.3C, structure ile hazır runtime motor katkısını toplar.
        rocket_mass_properties = RocketMassPropertiesCalculator().evaluate(
            structural_properties=context.structural_mass_properties,
            motor_properties=motor_mass_properties,
        )

        # 11. A.1 tek Cd0 authority'sidir; NAT-012B kritik yola dahil değildir.
        basic_drag = BasicDragEvaluator().evaluate(
            resolved_geometry=context.resolved_geometry,
            aerodynamic_surfaces=context.aerodynamic_surfaces,
            flight_conditions=flight_conditions,
            air_properties=air_properties,
            model_profile=context.basic_drag_model_profile,
        )

        # 12. NAT-014 için yalnız bu çağrı ömürlü ephemeral transport snapshot'ı.
        dynamics_inputs = TranslationalDynamicsInputs(
            mass_kg=rocket_mass_properties.total_mass_kg,
            thrust_N=motor_thrust_state.thrust_N,
            relative_velocity_world_m_s=relative_flow,
            dynamic_pressure_Pa=flight_conditions.dynamic_pressure_Pa,
            reference_area_m2=context.resolved_geometry.reference_area_m2,
            drag_coefficient_cd0=basic_drag.total_cd0,
            gravity_acceleration_world_m_s2=gravity,
        )

        # 13. Force ve derivative denklemlerinin tek authority'si NAT-014'tür.
        dynamics = TranslationalDynamicsEvaluator().evaluate(
            state=state,
            launch_conditions=launch_conditions,
            inputs=dynamics_inputs,
            model_profile=context.translational_dynamics_model_profile,
        )

        return PhysicsEvaluationResult3DOF(
            environment_altitude_m=environment_altitude,
            motor_time_s=motor_time,
            atmosphere=atmosphere,
            air_properties=air_properties,
            air_mass_velocity_world_m_s=air_mass_velocity,
            relative_flow=relative_flow,
            flight_conditions=flight_conditions,
            motor_thrust_state=motor_thrust_state,
            motor_mass_properties=motor_mass_properties,
            rocket_mass_properties=rocket_mass_properties,
            basic_drag=basic_drag,
            gravity_acceleration_world_m_s2=gravity,
            dynamics=dynamics,
        )
