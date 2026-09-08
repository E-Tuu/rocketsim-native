"""NAT-013 Initial / Launch State V1 focused sözleşme testleri."""

from dataclasses import FrozenInstanceError, fields
from inspect import Parameter, signature

import numpy as np
import pytest

from roketsim_native.dynamics.initial_state import (
    LAUNCH_DIRECTION_UNIT_NORM_ATOL,
    InitialStateBuilder,
    InitialStateValidationError,
    LaunchConditions3DOF,
    TranslationalState3DOF,
)
from roketsim_native.math.vectors import as_vector, magnitude


def launch_conditions(position=(0.0, 0.0, 0.0), velocity=(0.0, 0.0, 0.0), direction=(0.0, 0.0, 1.0)):
    return LaunchConditions3DOF(
        initial_position_world_m=position,
        initial_velocity_world_m_s=velocity,
        launch_direction_world_unit=direction,
    )


def test_accepted_numpy_vector_api_is_reused_without_parallel_vector_type():
    """INIT-T01: domain alanları accepted ndarray/float64 temsilini kullanır."""
    conditions = launch_conditions()
    for vector in (
        conditions.initial_position_world_m,
        conditions.initial_velocity_world_m_s,
        conditions.launch_direction_world_unit,
    ):
        assert type(vector) is np.ndarray
        assert vector.dtype == np.float64
        assert vector.shape == (3,)
    assert not hasattr(__import__("roketsim_native.dynamics.initial_state", fromlist=["Vector3"]), "Vector3")


@pytest.mark.parametrize("model", [TranslationalState3DOF, LaunchConditions3DOF])
def test_domain_models_are_frozen_and_slotted(model):
    """INIT-T02/T03: frozen dataclass ve slot davranışı korunur."""
    instance = (
        TranslationalState3DOF((1, 2, 3), (4, 5, 6))
        if model is TranslationalState3DOF
        else launch_conditions()
    )
    assert not hasattr(instance, "__dict__")
    with pytest.raises(FrozenInstanceError):
        setattr(instance, fields(model)[0].name, np.zeros(3))


@pytest.mark.parametrize(
    "factory,names",
    [
        (
            lambda values: TranslationalState3DOF(values[0], values[1]),
            ("position_world_m", "velocity_world_m_s"),
        ),
        (
            lambda values: LaunchConditions3DOF(values[0], values[1], values[2]),
            (
                "initial_position_world_m",
                "initial_velocity_world_m_s",
                "launch_direction_world_unit",
            ),
        ),
    ],
)
def test_all_stored_vectors_are_defensive_read_only_copies(factory, names):
    """INIT-T04/T05/T06: caller mutation sızmaz, stored array yazılamaz."""
    source = [
        np.array([1.0, 2.0, 3.0]),
        np.array([4.0, 5.0, 6.0]),
        np.array([0.0, 0.0, 1.0]),
    ]
    instance = factory(source)
    snapshots = [getattr(instance, name).copy() for name in names]
    for original in source:
        original[:] = 99.0
    for name, snapshot in zip(names, snapshots, strict=True):
        stored = getattr(instance, name)
        np.testing.assert_array_equal(stored, snapshot)
        assert not stored.flags.writeable
        with pytest.raises(ValueError):
            stored[0] = 0.0


@pytest.mark.parametrize(
    "position,velocity",
    [
        ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0)),
        ((125.0, -40.0, 12.5), (3.0, -2.0, 7.0)),
    ],
    ids=("explicit-demo-zero", "nonzero-world-position-and-velocity"),
)
def test_explicit_initial_position_and_velocity_are_preserved(position, velocity):
    """INIT-T04/T05/T06/T07/T08: origin veya zero-velocity default'u yoktur."""
    conditions = launch_conditions(position, velocity)
    np.testing.assert_array_equal(conditions.initial_position_world_m, position)
    np.testing.assert_array_equal(conditions.initial_velocity_world_m_s, velocity)


@pytest.mark.parametrize(
    "direction",
    [(0.0, 0.0, 1.0), (0.6, 0.8, 0.0)],
    ids=("vertical", "arbitrary-unit"),
)
def test_finite_unit_launch_directions_are_valid(direction):
    """INIT-T09/T10: local WORLD ENU içinde vertical ve arbitrary unit geçerlidir."""
    conditions = launch_conditions(direction=direction)
    np.testing.assert_array_equal(conditions.launch_direction_world_unit, direction)
    assert magnitude(conditions.launch_direction_world_unit) == pytest.approx(1.0)


@pytest.mark.parametrize("norm_offset", [0.5e-8, 1.0e-8], ids=("inside", "boundary"))
def test_within_tolerance_direction_is_accepted_and_not_normalized(norm_offset):
    """INIT-T12/T20: approved tolerans yalnız validation'dır, repair değildir."""
    supplied = np.array([0.0, 0.0, 1.0 + norm_offset])
    assert abs(magnitude(supplied) - 1.0) <= LAUNCH_DIRECTION_UNIT_NORM_ATOL
    conditions = launch_conditions(direction=supplied)
    np.testing.assert_array_equal(conditions.launch_direction_world_unit, supplied)
    assert magnitude(conditions.launch_direction_world_unit) != 1.0


@pytest.mark.parametrize("scale", [1.0 + 2.0e-8, 5.0])
def test_nonunit_direction_fails_without_normalization(scale):
    """INIT-T13: finite nonzero out-of-tolerance yön structured hata üretir."""
    supplied = np.array([0.0, 0.0, scale])
    with pytest.raises(InitialStateValidationError) as captured:
        launch_conditions(direction=supplied)
    assert captured.value.error_code == "NON_UNIT_LAUNCH_DIRECTION"
    assert captured.value.field_name == "launch_direction_world_unit"
    assert captured.value.value == tuple(supplied)
    np.testing.assert_array_equal(supplied, [0.0, 0.0, scale])


def test_zero_direction_has_distinct_structured_error():
    """INIT-T11: zero finite vector direction temsil etmez."""
    with pytest.raises(InitialStateValidationError) as captured:
        launch_conditions(direction=(0.0, 0.0, 0.0))
    assert captured.value.error_code == "INVALID_LAUNCH_DIRECTION"
    assert captured.value.field_name == "launch_direction_world_unit"
    assert captured.value.value == (0.0, 0.0, 0.0)


@pytest.mark.parametrize("field_index", [0, 1, 2])
@pytest.mark.parametrize("nonfinite", [float("nan"), float("inf"), float("-inf")])
def test_nonfinite_raw_components_use_generic_value_error(field_index, nonfinite):
    """INIT-T15: non-finite raw numerics structured domain hatasına çevrilmez."""
    values = [np.zeros(3), np.zeros(3), np.array([0.0, 0.0, 1.0])]
    values[field_index][0] = nonfinite
    with pytest.raises(ValueError) as captured:
        LaunchConditions3DOF(*values)
    assert type(captured.value) is ValueError


def test_initial_state_builder_contract_mapping_and_determinism():
    """INIT-T14/T15/T16: keyword-only stateless builder yalnız position/velocity map eder."""
    builder = InitialStateBuilder()
    assert not signature(InitialStateBuilder).parameters
    assert not hasattr(builder, "__dict__")
    parameter = signature(builder.build).parameters["launch_conditions"]
    assert parameter.kind is Parameter.KEYWORD_ONLY
    assert parameter.default is Parameter.empty
    with pytest.raises(TypeError):
        builder.build(launch_conditions())

    conditions = launch_conditions((10.0, 20.0, 30.0), (1.0, 2.0, 3.0), (0.6, 0.8, 0.0))
    first = builder.build(launch_conditions=conditions)
    second = builder.build(launch_conditions=conditions)
    np.testing.assert_array_equal(first.position_world_m, conditions.initial_position_world_m)
    np.testing.assert_array_equal(first.velocity_world_m_s, conditions.initial_velocity_world_m_s)
    np.testing.assert_array_equal(first.position_world_m, second.position_world_m)
    np.testing.assert_array_equal(first.velocity_world_m_s, second.velocity_world_m_s)
    assert first.position_world_m is not conditions.initial_position_world_m
    assert first.velocity_world_m_s is not conditions.initial_velocity_world_m_s


def test_state_and_launch_condition_schemas_keep_strict_scope():
    """INIT-T16/T17/T18: time/mass/aero/environment/attitude alanı veya yeni altitude yoktur."""
    assert [field.name for field in fields(TranslationalState3DOF)] == [
        "position_world_m",
        "velocity_world_m_s",
    ]
    assert [field.name for field in fields(LaunchConditions3DOF)] == [
        "initial_position_world_m",
        "initial_velocity_world_m_s",
        "launch_direction_world_unit",
    ]
    forbidden = {
        "time_s", "motor_time_s", "mass_kg", "cg_x_geo_m", "thrust_N",
        "acceleration_world_m_s2", "mach_number", "reynolds_number",
        "dynamic_pressure_Pa", "drag_coefficient", "cna_per_rad", "cp_x_geo_m",
        "static_margin_calibers", "atmosphere", "wind", "quaternion",
        "angular_velocity", "altitude_m",
    }
    state_names = {field.name for field in fields(TranslationalState3DOF)}
    condition_names = {field.name for field in fields(LaunchConditions3DOF)}
    assert forbidden.isdisjoint(state_names | condition_names)
    assert "launch_direction_world_unit" not in state_names


def test_accepted_vector_constructor_semantics_remain_independent_and_finite():
    """INIT-T01/T18: existing math semantics reused; global helper değiştirilmez."""
    source = np.array([1.0, 2.0, 3.0])
    accepted = as_vector(source, size=3)
    assert accepted.dtype == np.float64
    assert accepted is not source
    assert accepted.flags.writeable
