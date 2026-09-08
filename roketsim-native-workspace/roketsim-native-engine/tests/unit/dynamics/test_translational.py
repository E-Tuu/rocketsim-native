"""NAT-014 instantaneous 3DOF translational dynamics focused testleri."""

from dataclasses import FrozenInstanceError, fields
from inspect import Parameter, signature

import numpy as np
import pytest

from roketsim_native.dynamics.initial_state import (
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.dynamics.translational import (
    AerodynamicForceModel,
    DynamicsEvaluationError,
    NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
    ThrustDirectionModel,
    TranslationalDynamicsEvaluator,
    TranslationalDynamicsInputs,
    TranslationalDynamicsModelProfile,
    TranslationalDynamicsResult,
    TranslationalForces3DOF,
    TranslationalStateDerivative3DOF,
)


EVALUATOR = TranslationalDynamicsEvaluator()
PROFILE = NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE


def state(velocity=(0.0, 0.0, 0.0)):
    return TranslationalState3DOF((1.0, 2.0, 3.0), velocity)


def launch(direction=(0.0, 0.0, 1.0)):
    return LaunchConditions3DOF((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), direction)


def inputs(
    *,
    mass=2.0,
    thrust=30.0,
    relative_velocity=(0.0, 0.0, 0.0),
    q=0.0,
    area=0.01,
    cd0=0.5,
    gravity=(0.0, 0.0, -10.0),
):
    return TranslationalDynamicsInputs(
        mass,
        thrust,
        relative_velocity,
        q,
        area,
        cd0,
        gravity,
    )


def evaluate(*, velocity=(0.0, 0.0, 0.0), direction=(0.0, 0.0, 1.0), physics=None):
    return EVALUATOR.evaluate(
        state=state(velocity),
        launch_conditions=launch(direction),
        inputs=inputs() if physics is None else physics,
        model_profile=PROFILE,
    )


def assert_vector(actual, expected):
    np.testing.assert_allclose(actual, expected, rtol=0.0, atol=2.0e-15)


def test_model_profile_is_explicit_frozen_slotted_and_has_stable_enum_values():
    """DYN-T01/T02/T03: mandatory explicit V1 policies and exact enum values."""
    assert ThrustDirectionModel.FIXED_LAUNCH_DIRECTION.value == "fixed_launch_direction"
    assert AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY.value == "baseline_cd0_drag_only"
    parameters = signature(TranslationalDynamicsModelProfile).parameters
    assert all(parameter.default is Parameter.empty for parameter in parameters.values())
    assert not hasattr(PROFILE, "__dict__")
    with pytest.raises(FrozenInstanceError):
        PROFILE.thrust_direction_model = ThrustDirectionModel.FIXED_LAUNCH_DIRECTION


def test_evaluator_contract_is_parameterless_stateless_and_keyword_only():
    """DYN-T22: evaluator cache/history taşımaz ve tüm inputs keyword-only'dir."""
    assert not signature(TranslationalDynamicsEvaluator).parameters
    assert not hasattr(EVALUATOR, "__dict__")
    parameters = signature(EVALUATOR.evaluate).parameters
    assert tuple(parameters) == ("state", "launch_conditions", "inputs", "model_profile")
    assert all(
        parameter.kind is Parameter.KEYWORD_ONLY
        and parameter.default is Parameter.empty
        for parameter in parameters.values()
    )
    with pytest.raises(TypeError):
        EVALUATOR.evaluate(state(), launch(), inputs(), PROFILE)


def test_input_snapshot_contract_and_defensive_read_only_vectors():
    """DYN-T04: ephemeral snapshot frozen/slotted; caller arrays alias edilmez."""
    relative = np.array([3.0, 0.0, 4.0])
    gravity = np.array([0.0, 0.0, -9.8])
    snapshot = inputs(relative_velocity=relative, gravity=gravity)
    assert not hasattr(snapshot, "__dict__")
    with pytest.raises(FrozenInstanceError):
        snapshot.mass_kg = 1.0
    relative[:] = 99.0
    gravity[:] = 99.0
    assert_vector(snapshot.relative_velocity_world_m_s, (3.0, 0.0, 4.0))
    assert_vector(snapshot.gravity_acceleration_world_m_s2, (0.0, 0.0, -9.8))
    for vector in (
        snapshot.relative_velocity_world_m_s,
        snapshot.gravity_acceleration_world_m_s2,
    ):
        assert not vector.flags.writeable
        with pytest.raises(ValueError):
            vector[0] = 0.0


@pytest.mark.parametrize(
    "model,field,code",
    [
        (TranslationalDynamicsModelProfile("other", AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY), "thrust_direction_model", "UNSUPPORTED_THRUST_DIRECTION_MODEL"),
        (TranslationalDynamicsModelProfile(ThrustDirectionModel.FIXED_LAUNCH_DIRECTION, "other"), "aerodynamic_force_model", "UNSUPPORTED_AERODYNAMIC_FORCE_MODEL"),
    ],
)
def test_unsupported_model_selection_fails_explicitly(model, field, code):
    with pytest.raises(DynamicsEvaluationError) as captured:
        EVALUATOR.evaluate(
            state=state(), launch_conditions=launch(), inputs=inputs(), model_profile=model
        )
    assert captured.value.error_code == code
    assert captured.value.field_name == field


@pytest.mark.parametrize(
    "overrides,field,code",
    [
        ({"mass": 0.0}, "mass_kg", "INVALID_MASS"),
        ({"mass": -1.0}, "mass_kg", "INVALID_MASS"),
        ({"thrust": -1.0}, "thrust_N", "INVALID_THRUST"),
        ({"q": -1.0}, "dynamic_pressure_Pa", "INVALID_DYNAMIC_PRESSURE"),
        ({"area": 0.0}, "reference_area_m2", "INVALID_REFERENCE_AREA"),
        ({"area": -1.0}, "reference_area_m2", "INVALID_REFERENCE_AREA"),
        ({"cd0": -1.0}, "drag_coefficient_cd0", "INVALID_DRAG_COEFFICIENT"),
    ],
)
def test_finite_scalar_input_domain_validation(overrides, field, code):
    """DYN-T20: finite physical-domain invalidity structured hata üretir."""
    with pytest.raises(DynamicsEvaluationError) as captured:
        inputs(**overrides)
    assert captured.value.error_code == code
    assert captured.value.field_name == field
    assert captured.value.value == next(iter(overrides.values()))


@pytest.mark.parametrize("field", ["mass", "thrust", "q", "area", "cd0"])
@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf")])
def test_nonfinite_raw_scalars_use_generic_value_error(field, value):
    """DYN-T21: non-finite raw scalar structured dynamics hatasına çevrilmez."""
    with pytest.raises(ValueError) as captured:
        inputs(**{field: value})
    assert type(captured.value) is ValueError


@pytest.mark.parametrize("field", ["relative_velocity", "gravity"])
@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf")])
def test_nonfinite_raw_vector_components_use_generic_value_error(field, value):
    vector = np.array([value, 0.0, 0.0])
    with pytest.raises(ValueError) as captured:
        inputs(**{field: vector})
    assert type(captured.value) is ValueError


def test_thrust_gravity_drag_net_and_derivative_equations():
    """DYN-T06/T08/T09/T10/T14/T15/T16: true-3D equations exact authorities kullanır."""
    result = evaluate(
        velocity=(3.0, 0.0, 4.0),
        direction=(0.6, 0.0, 0.8),
        physics=inputs(
            mass=1.5,
            thrust=10.0,
            relative_velocity=(3.0, 0.0, 4.0),
            q=50.0,
            area=0.2,
            cd0=0.4,
            gravity=(0.0, 0.0, -9.8),
        ),
    )
    assert_vector(result.forces.thrust_force_world_N, (6.0, 0.0, 8.0))
    assert_vector(result.forces.drag_force_world_N, (-2.4, 0.0, -3.2))
    assert_vector(result.forces.gravity_force_world_N, (0.0, 0.0, -14.7))
    assert_vector(result.forces.net_force_world_N, (3.6, 0.0, -9.9))
    assert_vector(result.derivative.position_derivative_world_m_s, (3.0, 0.0, 4.0))
    assert_vector(result.derivative.velocity_derivative_world_m_s2, (2.4, 0.0, -6.6))
    assert result.acceleration_world_m_s2 is result.derivative.velocity_derivative_world_m_s2


@pytest.mark.parametrize(
    "relative,q,expected",
    [
        ((0.0, 0.0, 0.0), 0.0, (0.0, 0.0, 0.0)),
        ((1.0, 0.0, 0.0), 0.0, (0.0, 0.0, 0.0)),
    ],
    ids=("zero-flow-zero-q", "nonzero-flow-zero-q"),
)
def test_valid_zero_drag_branches(relative, q, expected):
    """DYN-T11/T13: exact zero-flow branch ve independently supplied q korunur."""
    result = evaluate(physics=inputs(relative_velocity=relative, q=q))
    assert_vector(result.forces.drag_force_world_N, expected)


def test_zero_relative_flow_with_positive_q_is_inconsistent():
    """DYN-T12: q yeniden hesaplanmaz; inconsistent authority snapshot reddedilir."""
    with pytest.raises(DynamicsEvaluationError) as captured:
        evaluate(physics=inputs(relative_velocity=(0.0, 0.0, 0.0), q=1.0))
    assert captured.value.error_code == "INCONSISTENT_DRAG_INPUTS"
    assert captured.value.field_name == "dynamic_pressure_Pa"
    assert captured.value.value == 1.0


@pytest.mark.parametrize(
    "velocity,physics,expected_drag,expected_net,expected_acceleration",
    [
        (
            (0.0, 0.0, 0.0),
            inputs(),
            (0.0, 0.0, 0.0),
            (0.0, 0.0, 10.0),
            (0.0, 0.0, 5.0),
        ),
        (
            (0.0, 0.0, 20.0),
            inputs(relative_velocity=(0.0, 0.0, 20.0), q=100.0),
            (0.0, 0.0, -0.5),
            (0.0, 0.0, 9.5),
            (0.0, 0.0, 4.75),
        ),
        (
            (0.0, 0.0, -20.0),
            inputs(thrust=0.0, relative_velocity=(0.0, 0.0, -20.0), q=100.0),
            (0.0, 0.0, 0.5),
            (0.0, 0.0, -19.5),
            (0.0, 0.0, -9.75),
        ),
    ],
    ids=("ignition", "powered-ascent", "descent"),
)
def test_frozen_vertical_vv(velocity, physics, expected_drag, expected_net, expected_acceleration):
    """DYN-T07/T18/T19/T23: ignition/ascent/descent frozen anchors."""
    result = evaluate(velocity=velocity, physics=physics)
    assert_vector(result.forces.drag_force_world_N, expected_drag)
    assert_vector(result.forces.net_force_world_N, expected_net)
    assert_vector(result.acceleration_world_m_s2, expected_acceleration)
    assert_vector(result.derivative.position_derivative_world_m_s, velocity)


def test_outputs_are_frozen_slotted_defensive_read_only_and_net_is_derived():
    """DYN-T05/T14: outputs alias içermez; net stored dataclass field değildir."""
    thrust = np.array([1.0, 2.0, 3.0])
    drag = np.array([4.0, 5.0, 6.0])
    gravity = np.array([7.0, 8.0, 9.0])
    forces = TranslationalForces3DOF(thrust, drag, gravity)
    derivative = TranslationalStateDerivative3DOF(thrust, drag)
    result = TranslationalDynamicsResult(forces, derivative, PROFILE)
    for model in (forces, derivative, result):
        assert not hasattr(model, "__dict__")
        with pytest.raises(FrozenInstanceError):
            setattr(model, fields(model)[0].name, None)
    thrust[:] = drag[:] = gravity[:] = 99.0
    assert_vector(forces.net_force_world_N, (12.0, 15.0, 18.0))
    assert [field.name for field in fields(TranslationalForces3DOF)] == [
        "thrust_force_world_N", "drag_force_world_N", "gravity_force_world_N"
    ]
    vectors = (
        forces.thrust_force_world_N,
        forces.drag_force_world_N,
        forces.gravity_force_world_N,
        forces.net_force_world_N,
        derivative.position_derivative_world_m_s,
        derivative.velocity_derivative_world_m_s2,
    )
    for vector in vectors:
        assert not vector.flags.writeable
        with pytest.raises(ValueError):
            vector[0] = 0.0


def test_evaluation_is_deterministic_and_does_not_mutate_inputs():
    """DYN-T22: repeated evaluation history-independent ve inputs immutable kalır."""
    current_state = state((3.0, 0.0, 4.0))
    conditions = launch((0.6, 0.0, 0.8))
    physics = inputs(
        mass=1.5, thrust=10.0, relative_velocity=(3.0, 0.0, 4.0),
        q=50.0, area=0.2, cd0=0.4, gravity=(0.0, 0.0, -9.8)
    )
    snapshots = [vector.copy() for vector in (
        current_state.position_world_m,
        current_state.velocity_world_m_s,
        conditions.launch_direction_world_unit,
        physics.relative_velocity_world_m_s,
        physics.gravity_acceleration_world_m_s2,
    )]
    first = EVALUATOR.evaluate(
        state=current_state, launch_conditions=conditions, inputs=physics, model_profile=PROFILE
    )
    second = EVALUATOR.evaluate(
        state=current_state, launch_conditions=conditions, inputs=physics, model_profile=PROFILE
    )
    assert_vector(first.forces.net_force_world_N, second.forces.net_force_world_N)
    assert_vector(first.acceleration_world_m_s2, second.acceleration_world_m_s2)
    for vector, snapshot in zip((
        current_state.position_world_m,
        current_state.velocity_world_m_s,
        conditions.launch_direction_world_unit,
        physics.relative_velocity_world_m_s,
        physics.gravity_acceleration_world_m_s2,
    ), snapshots, strict=True):
        np.testing.assert_array_equal(vector, snapshot)


def test_public_schemas_exclude_mdot_static_aero_and_future_dynamics_scope():
    """DYN-T17/T25: CNa/CP/AoA/rail/time/integrator/6DOF API'ye sızmaz."""
    names = {
        field.name
        for model in (
            TranslationalDynamicsInputs,
            TranslationalForces3DOF,
            TranslationalStateDerivative3DOF,
            TranslationalDynamicsResult,
        )
        for field in fields(model)
    }
    forbidden = {
        "mass_flow_rate_kg_s", "mdot", "cna_per_rad", "cp_x_geo_m",
        "angle_of_attack_rad", "static_margin_calibers", "rail_constraint",
        "time_s", "timestep_s", "quaternion", "angular_velocity", "inertia",
        "moment_world_N_m", "normal_force_world_N", "lift_force_world_N",
    }
    assert forbidden.isdisjoint(names)
