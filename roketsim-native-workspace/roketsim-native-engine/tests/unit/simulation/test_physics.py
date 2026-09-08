"""NAT-015 3DOF PhysicsEvaluator orchestration ve integration V&V testleri."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature
from types import SimpleNamespace

import numpy as np
import pytest

from roketsim_native.aerodynamics.drag import (
    NATIVE_BASIC_DRAG_V1_PROFILE,
    BasicDragResult,
)
from roketsim_native.aerodynamics.surfaces import (
    SingleStageRocketAerodynamicSurfaces,
    SurfaceFinish,
)
from roketsim_native.dynamics.initial_state import (
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.dynamics.translational import (
    NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
    TranslationalDynamicsResult,
)
from roketsim_native.environment.air_properties import (
    DryAirProperties,
    DryAirPropertiesCalculator,
)
from roketsim_native.environment.atmosphere import (
    AtmosphereDomainError,
    DryAirAtmosphereState,
    USStandardAtmosphere1976Lower,
)
from roketsim_native.environment.gravity import ConstantGravityModel
from roketsim_native.environment.wind import ConstantWindModel, NoWindModel
from roketsim_native.flight_conditions.basic import BasicFlightConditions
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
from roketsim_native.mass.total import RocketMassProperties
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T
from roketsim_native.propulsion.installation import MotorInstallationResolver
from roketsim_native.propulsion.properties import (
    DEMO_MOTOR_PROPERTY_MODEL_PROFILE,
    MotorMassProperties,
)
from roketsim_native.propulsion.thrust import MotorThrustState
from roketsim_native.simulation import physics
from roketsim_native.simulation.physics import (
    EnvironmentPositionMappingModel,
    PhysicsEvaluationContext3DOF,
    PhysicsEvaluationError,
    PhysicsEvaluationResult3DOF,
    PhysicsEvaluator3DOF,
    PropulsionTimeline,
)


def resolved_geometry():
    source = SingleStageRocketGeometry(
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
    return GeometryResolver().resolve(rocket_geometry=source)


@pytest.fixture(scope="module")
def accepted_dependencies():
    geometry = resolved_geometry()
    structure = StructuralMassPropertiesCalculator().evaluate(
        resolved_geometry=geometry,
        materials=SingleStageRocketMaterials(
            POLYSTYRENE,
            CARDBOARD,
            CARDBOARD,
            CARDBOARD,
            CARDBOARD,
        ),
    )
    installation = MotorInstallationResolver().resolve(
        resolved_geometry=geometry,
        motor=AEROTECH_F50_4T,
    )
    smooth = SurfaceFinish("Synthetic smooth", 0.0)
    surfaces = SingleStageRocketAerodynamicSurfaces(smooth, smooth, smooth)
    return geometry, structure, installation, surfaces


def make_context(
    accepted_dependencies,
    *,
    altitude=1200.0,
    ignition=5.0,
    wind_model=None,
):
    geometry, structure, installation, surfaces = accepted_dependencies
    return PhysicsEvaluationContext3DOF(
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
        launch_environment_altitude_m=altitude,
        propulsion_timeline=PropulsionTimeline(ignition),
        atmosphere_model=USStandardAtmosphere1976Lower(),
        air_properties_calculator=DryAirPropertiesCalculator(),
        gravity_model=ConstantGravityModel(),
        wind_model=NoWindModel() if wind_model is None else wind_model,
    )


def launch(*, z=50.0, direction=(0.0, 0.0, 1.0)):
    return LaunchConditions3DOF((10.0, 20.0, z), (0.0, 0.0, 0.0), direction)


def state(*, z=50.0, velocity=(0.0, 0.0, 0.0)):
    return TranslationalState3DOF((10.0, 20.0, z), velocity)


def evaluate(context, *, time=5.0, current_state=None, conditions=None):
    return PhysicsEvaluator3DOF().evaluate(
        time_s=time,
        state=state() if current_state is None else current_state,
        launch_conditions=launch() if conditions is None else conditions,
        context=context,
    )


def test_context_contract_is_frozen_slotted_mandatory_and_bundles_exact_authorities(
    accepted_dependencies,
):
    """PHYS-T01/T08: context dependency bundle'dır; field default'u yoktur."""
    context = make_context(accepted_dependencies)
    parameters = signature(PhysicsEvaluationContext3DOF).parameters
    assert parameters
    assert all(parameter.default is Parameter.empty for parameter in parameters.values())
    assert not hasattr(context, "__dict__")
    with pytest.raises(FrozenInstanceError):
        context.launch_environment_altitude_m = 0.0
    assert isinstance(context.atmosphere_model, USStandardAtmosphere1976Lower)
    assert isinstance(context.air_properties_calculator, DryAirPropertiesCalculator)
    assert isinstance(context.gravity_model, ConstantGravityModel)
    assert isinstance(context.wind_model, NoWindModel)


def test_mapping_and_timeline_contracts_are_explicit_frozen_and_finite():
    """PHYS-T03/T09: tek mapping enum'u ve defaultsuz finite ignition zamanı."""
    assert list(EnvironmentPositionMappingModel) == [
        EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET
    ]
    assert (
        EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET.value
        == "local_enu_vertical_offset"
    )
    assert signature(PropulsionTimeline).parameters["ignition_time_s"].default is Parameter.empty
    timeline = PropulsionTimeline(5.0)
    assert not hasattr(timeline, "__dict__")
    with pytest.raises(FrozenInstanceError):
        timeline.ignition_time_s = 0.0
    for nonfinite in (float("nan"), float("inf"), float("-inf")):
        with pytest.raises(ValueError) as captured:
            PropulsionTimeline(nonfinite)
        assert type(captured.value) is ValueError


def test_evaluator_contract_is_parameterless_stateless_keyword_only_and_result_scoped(
    accepted_dependencies,
):
    """PHYS-T02/T28: no cache/history/timestep; result duplicate aliases taşımaz."""
    evaluator = PhysicsEvaluator3DOF()
    assert not signature(PhysicsEvaluator3DOF).parameters
    assert not hasattr(evaluator, "__dict__")
    parameters = signature(evaluator.evaluate).parameters
    assert tuple(parameters) == ("time_s", "state", "launch_conditions", "context")
    assert all(
        parameter.kind is Parameter.KEYWORD_ONLY
        and parameter.default is Parameter.empty
        for parameter in parameters.values()
    )
    with pytest.raises(TypeError):
        evaluator.evaluate(5.0, state(), launch(), make_context(accepted_dependencies))
    assert [field.name for field in fields(PhysicsEvaluationResult3DOF)] == [
        "environment_altitude_m",
        "motor_time_s",
        "atmosphere",
        "air_properties",
        "air_mass_velocity_world_m_s",
        "relative_flow",
        "flight_conditions",
        "motor_thrust_state",
        "motor_mass_properties",
        "rocket_mass_properties",
        "basic_drag",
        "gravity_acceleration_world_m_s2",
        "dynamics",
    ]
    forbidden = {
        "dynamics_inputs",
        "thrust_N",
        "mass_kg",
        "mach",
        "reynolds",
        "dynamic_pressure_Pa",
        "total_cd0",
        "acceleration_world_m_s2",
        "timestep_s",
    }
    assert forbidden.isdisjoint(field.name for field in fields(PhysicsEvaluationResult3DOF))


@pytest.mark.parametrize(
    "current_z,expected",
    [(173.25, 1323.25), (20.0, 1170.0)],
    ids=("positive-displacement", "negative-displacement"),
)
def test_local_enu_altitude_mapping_and_natzero_origin_independence(
    accepted_dependencies, current_z, expected
):
    """PHYS-T04..07/T13: accepted geopotential-height input receives signed delta-z."""
    context = make_context(accepted_dependencies)
    result = evaluate(context, current_state=state(z=current_z))
    assert result.environment_altitude_m == expected
    accepted = context.atmosphere_model.evaluate(geopotential_height_m=expected)
    assert result.atmosphere == accepted
    assert result.environment_altitude_m != context.launch_environment_altitude_m + current_z


@pytest.mark.parametrize(
    "time,ignition,expected",
    [(6.43, 5.0, 1.43), (5.0, 5.0, 0.0), (-1.0, -2.0, 1.0)],
    ids=("elapsed", "exact-ignition", "negative-simulation-time-valid"),
)
def test_motor_timeline_mapping(accepted_dependencies, time, ignition, expected):
    """PHYS-T09/T10: motor_time=time-ignition; exact ignition geçerlidir."""
    result = evaluate(
        make_context(accepted_dependencies, ignition=ignition),
        time=time,
    )
    assert result.motor_time_s == pytest.approx(expected, abs=1.0e-15)


def test_preignition_rejected_without_propulsion_fallback(accepted_dependencies, monkeypatch):
    """PHYS-T11/T12: pre-ignition thrust/mass evaluator'ına ulaşmadan reddedilir."""
    called = False

    class ForbiddenThrustEvaluator:
        def evaluate(self, **kwargs):
            nonlocal called
            called = True
            raise AssertionError("pre-ignition propulsion fallback")

    monkeypatch.setattr(physics, "MotorThrustCurveEvaluator", ForbiddenThrustEvaluator)
    with pytest.raises(PhysicsEvaluationError) as captured:
        evaluate(make_context(accepted_dependencies), time=4.999)
    assert captured.value.error_code == "BEFORE_IGNITION_UNSUPPORTED"
    assert captured.value.field_name == "motor_time_s"
    assert captured.value.value == pytest.approx(-0.001)
    assert not called


def test_unsupported_mapping_and_invalid_derived_mapping_are_structured(
    accepted_dependencies,
):
    """NAT-015-owned finite model/mapping failures retain structured evidence."""
    context = make_context(accepted_dependencies)
    with pytest.raises(PhysicsEvaluationError) as unsupported:
        evaluate(replace(context, environment_position_mapping_model="other"))
    assert unsupported.value.error_code == "UNSUPPORTED_ENVIRONMENT_POSITION_MAPPING_MODEL"
    assert unsupported.value.field_name == "environment_position_mapping_model"

    huge_launch = launch(z=-1.0e308)
    huge_state = state(z=1.0e308)
    with pytest.raises(PhysicsEvaluationError) as invalid:
        evaluate(context, current_state=huge_state, conditions=huge_launch)
    assert invalid.value.error_code == "INVALID_ENVIRONMENT_ALTITUDE_MAPPING"
    assert invalid.value.field_name == "environment_altitude_m"


@pytest.mark.parametrize("nonfinite", [float("nan"), float("inf"), float("-inf")])
def test_raw_nonfinite_nat015_inputs_use_generic_value_error(
    accepted_dependencies, nonfinite
):
    """Raw time/launch-altitude non-finite değerleri PhysicsEvaluationError değildir."""
    context = make_context(accepted_dependencies)
    with pytest.raises(ValueError) as time_error:
        evaluate(context, time=nonfinite)
    assert type(time_error.value) is ValueError
    with pytest.raises(ValueError) as altitude_error:
        replace(context, launch_environment_altitude_m=nonfinite)
    assert type(altitude_error.value) is ValueError


def test_upstream_atmosphere_error_propagates_unchanged(accepted_dependencies):
    """PHYS-T27: upstream error source identity körlemesine wrap edilmez."""
    context = make_context(accepted_dependencies, altitude=90_000.0)
    with pytest.raises(AtmosphereDomainError) as captured:
        evaluate(context)
    assert type(captured.value) is AtmosphereDomainError


def test_f50_timeline_mass_and_total_mass_integration(accepted_dependencies, monkeypatch):
    """PHYS-T20/T22: aynı .012 s thrust/property chain ve frozen mass anchor'ları."""
    seen_times = []
    thrust_type = physics.MotorThrustCurveEvaluator
    property_type = physics.MotorPropertyEvaluator

    class ThrustSpy:
        def evaluate(self, **kwargs):
            seen_times.append(("thrust", kwargs["motor_time_s"]))
            return thrust_type().evaluate(**kwargs)

    class PropertySpy:
        def evaluate(self, **kwargs):
            seen_times.append(("properties", kwargs["motor_time_s"]))
            return property_type().evaluate(**kwargs)

    monkeypatch.setattr(physics, "MotorThrustCurveEvaluator", ThrustSpy)
    monkeypatch.setattr(physics, "MotorPropertyEvaluator", PropertySpy)
    result = evaluate(make_context(accepted_dependencies, ignition=0.0), time=0.012)
    assert seen_times == [("thrust", pytest.approx(0.012)), ("properties", pytest.approx(0.012))]
    assert result.motor_thrust_state.thrust_N == 51.377
    assert result.motor_mass_properties.mass_kg == pytest.approx(0.0847479321, abs=5.0e-11)
    assert result.rocket_mass_properties.total_mass_kg == pytest.approx(
        0.6355969349, abs=5.0e-11
    )


@pytest.mark.parametrize(
    "wind,expected",
    [
        (NoWindModel(), (3.0, 4.0, 5.0)),
        (
            ConstantWindModel(airmass_velocity_world_m_s=(1.0, -2.0, 0.5)),
            (2.0, 6.0, 4.5),
        ),
    ],
    ids=("explicit-zero-wind", "accepted-constant-wind"),
)
def test_wind_and_relative_flow_plumbing(accepted_dependencies, wind, expected):
    """PHYS-T16/T17: wind model ve NAT-010A çağrılır; zero wind hard-code edilmez."""
    result = evaluate(
        make_context(accepted_dependencies, wind_model=wind),
        current_state=state(velocity=(3.0, 4.0, 5.0)),
    )
    np.testing.assert_allclose(result.relative_flow, expected, rtol=0.0, atol=0.0)

def test_actual_chain_returns_exact_accepted_result_types_and_read_only_vectors(
    accepted_dependencies,
):
    """PHYS-T08/T14/T18/T21/T23/T26: accepted result objects doğrudan korunur."""
    result = evaluate(make_context(accepted_dependencies))
    assert isinstance(result.atmosphere, DryAirAtmosphereState)
    assert isinstance(result.air_properties, DryAirProperties)
    assert isinstance(result.flight_conditions, BasicFlightConditions)
    assert isinstance(result.motor_thrust_state, MotorThrustState)
    assert isinstance(result.motor_mass_properties, MotorMassProperties)
    assert isinstance(result.rocket_mass_properties, RocketMassProperties)
    assert isinstance(result.basic_drag, BasicDragResult)
    assert isinstance(result.dynamics, TranslationalDynamicsResult)
    assert not hasattr(result, "__dict__")
    with pytest.raises(FrozenInstanceError):
        result.motor_time_s = 2.0
    for vector in (
        result.air_mass_velocity_world_m_s,
        result.relative_flow,
        result.gravity_acceleration_world_m_s2,
    ):
        assert not vector.flags.writeable
        with pytest.raises(ValueError):
            vector[0] = 0.0


def test_frozen_upstream_evaluation_order(accepted_dependencies, monkeypatch):
    """PHYS-T13..26: derivative-critical accepted evaluators frozen sırada çağrılır."""
    order = []

    class InstanceSpy:
        def __init__(self, label, delegate):
            self.label = label
            self.delegate = delegate

        def evaluate(self, *args, **kwargs):
            order.append(self.label)
            return self.delegate.evaluate(*args, **kwargs)

    context = make_context(accepted_dependencies, ignition=0.0)
    context = replace(
        context,
        atmosphere_model=InstanceSpy("atmosphere", context.atmosphere_model),
        air_properties_calculator=InstanceSpy(
            "air_properties", context.air_properties_calculator
        ),
        gravity_model=InstanceSpy("gravity", context.gravity_model),
        wind_model=InstanceSpy("wind", context.wind_model),
    )

    for attribute, label in (
        ("RelativeFlowCalculator", "relative_flow"),
        ("BasicFlightConditionsCalculator", "flight_conditions"),
        ("MotorThrustCurveEvaluator", "thrust"),
        ("MotorPropertyEvaluator", "motor_properties"),
        ("RocketMassPropertiesCalculator", "total_mass"),
        ("BasicDragEvaluator", "basic_drag"),
        ("TranslationalDynamicsEvaluator", "dynamics"),
    ):
        original_type = getattr(physics, attribute)

        def factory(original_type=original_type, label=label):
            return InstanceSpy(label, original_type())

        monkeypatch.setattr(physics, attribute, factory)

    evaluate(context, time=0.0)
    assert order == [
        "atmosphere",
        "air_properties",
        "gravity",
        "wind",
        "relative_flow",
        "flight_conditions",
        "thrust",
        "motor_properties",
        "total_mass",
        "basic_drag",
        "dynamics",
    ]


def test_controlled_nat014_derivative_plumbing(accepted_dependencies, monkeypatch):
    """PHYS-T25/T26: temporary snapshot hazır outputs'tan kurulur; NAT-014 dv/dt=5."""
    context = make_context(accepted_dependencies, altitude=0.0, ignition=0.0)
    controlled_geometry = replace(context.resolved_geometry, reference_area_m2=0.01)
    controlled_structure = replace(
        context.structural_mass_properties,
        structure_mass_kg=1.0,
        structure_cg_x_geo_m=0.5,
    )
    context = replace(
        context,
        resolved_geometry=controlled_geometry,
        structural_mass_properties=controlled_structure,
        gravity_model=SimpleNamespace(
            evaluate=lambda: np.array([0.0, 0.0, -10.0])
        ),
    )

    class ControlledThrust:
        def evaluate(self, **kwargs):
            return MotorThrustState(30.0, 0.0)

    class ControlledMotorProperties:
        def evaluate(self, **kwargs):
            return MotorMassProperties(
                1.0,
                0.05,
                0.5,
                context.motor_property_model_profile,
            )

    class ControlledDrag:
        def evaluate(self, **kwargs):
            return SimpleNamespace(total_cd0=0.5)

    captured_dynamics_inputs = {}
    dynamics_type = physics.TranslationalDynamicsEvaluator

    class CapturingDynamics:
        def evaluate(self, **kwargs):
            captured_dynamics_inputs["value"] = kwargs["inputs"]
            return dynamics_type().evaluate(**kwargs)

    monkeypatch.setattr(physics, "MotorThrustCurveEvaluator", ControlledThrust)
    monkeypatch.setattr(physics, "MotorPropertyEvaluator", ControlledMotorProperties)
    monkeypatch.setattr(physics, "BasicDragEvaluator", ControlledDrag)
    monkeypatch.setattr(physics, "TranslationalDynamicsEvaluator", CapturingDynamics)
    result = evaluate(context, time=0.0, current_state=state(z=50.0))
    np.testing.assert_allclose(
        result.dynamics.derivative.velocity_derivative_world_m_s2,
        (0.0, 0.0, 5.0),
        rtol=0.0,
        atol=0.0,
    )
    assert result.rocket_mass_properties.total_mass_kg == 2.0
    assert result.motor_thrust_state.thrust_N == 30.0
    assert result.flight_conditions.dynamic_pressure_Pa == 0.0
    assert result.basic_drag.total_cd0 == 0.5
    snapshot = captured_dynamics_inputs["value"]
    assert snapshot.mass_kg == 2.0
    assert snapshot.thrust_N == 30.0
    np.testing.assert_array_equal(snapshot.relative_velocity_world_m_s, (0.0, 0.0, 0.0))
    assert snapshot.dynamic_pressure_Pa == 0.0
    assert snapshot.reference_area_m2 == 0.01
    assert snapshot.drag_coefficient_cd0 == 0.5
    np.testing.assert_array_equal(
        snapshot.gravity_acceleration_world_m_s2,
        (0.0, 0.0, -10.0),
    )


def test_determinism_no_input_mutation_and_no_static_stability_critical_path(
    accepted_dependencies,
):
    """PHYS-T24/T28: current state yeniden değerlendirilir; static aero dependency yoktur."""
    context = make_context(accepted_dependencies)
    conditions = launch()
    current_state = state(velocity=(1.0, 2.0, 3.0))
    snapshots = [
        conditions.initial_position_world_m.copy(),
        conditions.launch_direction_world_unit.copy(),
        current_state.position_world_m.copy(),
        current_state.velocity_world_m_s.copy(),
    ]
    first = evaluate(context, current_state=current_state, conditions=conditions)
    second = evaluate(context, current_state=current_state, conditions=conditions)
    assert first.environment_altitude_m == second.environment_altitude_m
    assert first.motor_time_s == second.motor_time_s
    np.testing.assert_array_equal(first.relative_flow, second.relative_flow)
    np.testing.assert_array_equal(
        first.dynamics.acceleration_world_m_s2,
        second.dynamics.acceleration_world_m_s2,
    )
    for vector, snapshot in zip(
        (
            conditions.initial_position_world_m,
            conditions.launch_direction_world_unit,
            current_state.position_world_m,
            current_state.velocity_world_m_s,
        ),
        snapshots,
        strict=True,
    ):
        np.testing.assert_array_equal(vector, snapshot)
    assert not hasattr(physics, "StaticAerodynamicEvaluator")
    assert not hasattr(physics, "StaticMarginCalculator")
    result_fields = {field.name for field in fields(PhysicsEvaluationResult3DOF)}
    assert {"static_aerodynamics", "static_margin"}.isdisjoint(result_fields)
