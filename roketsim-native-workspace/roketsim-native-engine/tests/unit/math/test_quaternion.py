"""NAT-007 scalar-first quaternion ve BODY/WORLD rotation testleri."""

import inspect
import math

import numpy as np
import pytest

from roketsim_native.math.quaternion import (
    apply_increment_left,
    as_quaternion,
    conjugate,
    from_axis_angle,
    inverse,
    multiply,
    normalize_quaternion,
    quaternion_norm,
    rotate_body_to_world,
    rotate_world_to_body,
    to_rotation_matrix,
)
from roketsim_native.math.vectors import magnitude


TEST_ATOL = 1.0e-12


@pytest.mark.parametrize("value", [[1, 2, 3, 4], (1, 2, 3, 4)])
def test_as_quaternion_accepts_list_and_tuple(value: object) -> None:
    result = as_quaternion(value)

    assert result.dtype == np.dtype(np.float64)
    np.testing.assert_array_equal(result, [1.0, 2.0, 3.0, 4.0])


def test_as_quaternion_accepts_ndarray_as_independent_float64_copy() -> None:
    source = np.array([1, 2, 3, 4], dtype=np.int32)

    result = as_quaternion(source)

    assert result.dtype == np.dtype(np.float64)
    assert not np.shares_memory(source, result)
    result[0] = 99.0
    np.testing.assert_array_equal(source, [1, 2, 3, 4])


@pytest.mark.parametrize("value", [[1, 2, 3], [1, 2, 3, 4, 5]])
def test_as_quaternion_rejects_wrong_shape(value: list[int]) -> None:
    with pytest.raises(ValueError, match="shape"):
        as_quaternion(value)


def test_as_quaternion_rejects_two_dimensional_input() -> None:
    with pytest.raises(ValueError, match="1-D"):
        as_quaternion([[1.0, 0.0, 0.0, 0.0]])


@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf])
def test_as_quaternion_rejects_non_finite_component(value: float) -> None:
    with pytest.raises(ValueError, match=r"attitude\[2\].*finite"):
        as_quaternion([1.0, 0.0, value, 0.0], name="attitude")


def test_as_quaternion_allows_zero_as_representation() -> None:
    np.testing.assert_array_equal(as_quaternion([0.0, 0.0, 0.0, 0.0]), 0.0)


def test_identity_quaternion_has_unit_norm() -> None:
    assert quaternion_norm([1.0, 0.0, 0.0, 0.0]) == 1.0


def test_identity_quaternion_inverse_is_identity() -> None:
    result = inverse([1.0, 0.0, 0.0, 0.0], atol=0.0)

    np.testing.assert_array_equal(result, [1.0, 0.0, 0.0, 0.0])


def test_identity_body_to_world_rotation_preserves_vector() -> None:
    vector = np.array([1.5, -2.0, 3.25])

    result = rotate_body_to_world(
        [1.0, 0.0, 0.0, 0.0],
        vector,
        atol=0.0,
    )

    np.testing.assert_array_equal(result, vector)


def test_identity_quaternion_rotation_matrix_is_identity() -> None:
    matrix = to_rotation_matrix([1.0, 0.0, 0.0, 0.0], atol=0.0)

    assert matrix.dtype == np.dtype(np.float64)
    np.testing.assert_array_equal(matrix, np.eye(3))


def test_normalize_quaternion_known_scale() -> None:
    result = normalize_quaternion([2.0, 0.0, 0.0, 0.0], atol=0.0)

    np.testing.assert_array_equal(result, [1.0, 0.0, 0.0, 0.0])


def test_normalized_quaternion_has_unit_norm() -> None:
    result = normalize_quaternion([1.0, 2.0, 3.0, 4.0], atol=0.0)

    assert quaternion_norm(result) == pytest.approx(1.0)


def test_normalize_quaternion_rejects_zero() -> None:
    with pytest.raises(ValueError, match="quaternion norm"):
        normalize_quaternion([0.0, 0.0, 0.0, 0.0], atol=0.0)


def test_normalize_quaternion_rejects_near_zero_for_supplied_atol() -> None:
    with pytest.raises(ValueError, match="quaternion norm"):
        normalize_quaternion([1.0e-9, 0.0, 0.0, 0.0], atol=1.0e-8)


@pytest.mark.parametrize("atol", [-1.0, math.nan, math.inf, -math.inf])
def test_normalize_quaternion_rejects_invalid_atol(atol: float) -> None:
    with pytest.raises(ValueError, match="atol"):
        normalize_quaternion([1.0, 0.0, 0.0, 0.0], atol=atol)


def test_normalize_quaternion_does_not_modify_input() -> None:
    source = np.array([1.0, 2.0, 3.0, 4.0])
    original = source.copy()

    result = normalize_quaternion(source, atol=0.0)

    np.testing.assert_array_equal(source, original)
    assert not np.shares_memory(source, result)


def test_conjugate_known_quaternion() -> None:
    result = conjugate([1.0, 2.0, -3.0, 4.0])

    np.testing.assert_array_equal(result, [1.0, -2.0, 3.0, -4.0])


def test_general_non_unit_inverse_is_two_sided() -> None:
    quaternion = np.array([2.0, -1.0, 0.5, 3.0])
    quaternion_inverse = inverse(quaternion, atol=0.0)
    identity = np.array([1.0, 0.0, 0.0, 0.0])

    np.testing.assert_allclose(
        multiply(quaternion, quaternion_inverse),
        identity,
        atol=TEST_ATOL,
    )
    np.testing.assert_allclose(
        multiply(quaternion_inverse, quaternion),
        identity,
        atol=TEST_ATOL,
    )


def test_inverse_rejects_zero_quaternion() -> None:
    with pytest.raises(ValueError, match="quaternion norm"):
        inverse([0.0, 0.0, 0.0, 0.0], atol=0.0)


def test_hamilton_product_known_hand_calculated_example() -> None:
    result = multiply([1.0, 2.0, 3.0, 4.0], [5.0, 6.0, 7.0, 8.0])

    np.testing.assert_array_equal(result, [-60.0, 12.0, 30.0, 24.0])


def test_hamilton_product_is_not_commutative() -> None:
    q1 = [1.0, 2.0, 3.0, 4.0]
    q2 = [5.0, 6.0, 7.0, 8.0]

    assert not np.allclose(multiply(q1, q2), multiply(q2, q1))


def test_axis_angle_zero_angle_is_identity() -> None:
    result = from_axis_angle([1.0, 0.0, 0.0], 0.0, atol=0.0)

    np.testing.assert_array_equal(result, [1.0, 0.0, 0.0, 0.0])


def test_axis_angle_normalizes_axis() -> None:
    result = from_axis_angle([2.0, 0.0, 0.0], math.pi / 2.0, atol=0.0)
    expected = math.sqrt(0.5)

    np.testing.assert_allclose(
        result,
        [expected, expected, 0.0, 0.0],
        atol=TEST_ATOL,
    )


def test_axis_angle_rejects_zero_axis() -> None:
    with pytest.raises(ValueError, match="magnitude"):
        from_axis_angle([0.0, 0.0, 0.0], 0.0, atol=0.0)


def test_axis_angle_rejects_near_zero_axis() -> None:
    with pytest.raises(ValueError, match="magnitude"):
        from_axis_angle([1.0e-9, 0.0, 0.0], 0.0, atol=1.0e-8)


@pytest.mark.parametrize("angle", [math.nan, math.inf, -math.inf])
def test_axis_angle_rejects_non_finite_angle(angle: float) -> None:
    with pytest.raises(ValueError, match="angle_rad"):
        from_axis_angle([1.0, 0.0, 0.0], angle, atol=0.0)


@pytest.mark.parametrize(
    ("axis", "source", "expected"),
    [
        ([1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]),
        ([1.0, 0.0, 0.0], [0.0, 0.0, 1.0], [0.0, -1.0, 0.0]),
        ([0.0, 1.0, 0.0], [0.0, 0.0, 1.0], [1.0, 0.0, 0.0]),
        ([0.0, 1.0, 0.0], [1.0, 0.0, 0.0], [0.0, 0.0, -1.0]),
        ([0.0, 0.0, 1.0], [1.0, 0.0, 0.0], [0.0, 1.0, 0.0]),
        ([0.0, 0.0, 1.0], [0.0, 1.0, 0.0], [-1.0, 0.0, 0.0]),
    ],
)
def test_positive_quarter_turn_right_hand_rotations(
    axis: list[float],
    source: list[float],
    expected: list[float],
) -> None:
    quaternion = from_axis_angle(axis, math.pi / 2.0, atol=0.0)

    result = rotate_body_to_world(quaternion, source, atol=0.0)

    np.testing.assert_allclose(result, expected, atol=TEST_ATOL)


def test_body_world_body_round_trip() -> None:
    q_bw = from_axis_angle([1.0, -2.0, 0.5], 1.234, atol=0.0)
    vector_b = np.array([3.5, -1.25, 8.0])

    vector_w = rotate_body_to_world(q_bw, vector_b, atol=0.0)
    recovered_b = rotate_world_to_body(q_bw, vector_w, atol=0.0)

    np.testing.assert_allclose(recovered_b, vector_b, atol=TEST_ATOL)


@pytest.mark.parametrize(
    "vector_b",
    [
        [1.0, 2.0, 3.0],
        [-4.5, 0.25, 7.75],
        [0.0, -3.0, 0.5],
    ],
)
def test_rotation_preserves_vector_magnitude(vector_b: list[float]) -> None:
    q_bw = from_axis_angle([2.0, 1.0, -3.0], 0.731, atol=0.0)

    vector_w = rotate_body_to_world(q_bw, vector_b, atol=0.0)

    assert magnitude(vector_w) == pytest.approx(magnitude(vector_b))


def test_q_and_negative_q_produce_same_rotation() -> None:
    q_bw = from_axis_angle([1.0, 2.0, 3.0], 0.845, atol=0.0)
    vector_b = [4.0, -2.0, 1.5]

    positive = rotate_body_to_world(q_bw, vector_b, atol=0.0)
    negative = rotate_body_to_world(-q_bw, vector_b, atol=0.0)

    np.testing.assert_allclose(positive, negative, atol=TEST_ATOL)


def test_non_unit_orientation_is_explicitly_normalized() -> None:
    q_bw = from_axis_angle([1.0, -1.0, 2.0], 0.63, atol=0.0)
    vector_b = [2.0, 3.0, -4.0]

    unit_result = rotate_body_to_world(q_bw, vector_b, atol=0.0)
    scaled_result = rotate_body_to_world(7.5 * q_bw, vector_b, atol=0.0)

    np.testing.assert_allclose(unit_result, scaled_result, atol=TEST_ATOL)


def test_rotation_matrix_matches_quaternion_rotation() -> None:
    q_bw = from_axis_angle([1.0, 4.0, -2.0], 1.1, atol=0.0)
    vector_b = np.array([2.5, -3.0, 0.75])

    quaternion_result = rotate_body_to_world(q_bw, vector_b, atol=0.0)
    matrix_result = to_rotation_matrix(q_bw, atol=0.0) @ vector_b

    np.testing.assert_allclose(matrix_result, quaternion_result, atol=TEST_ATOL)


def test_rotation_matrix_is_orthogonal_with_positive_determinant() -> None:
    q_bw = from_axis_angle([-2.0, 5.0, 1.0], 0.93, atol=0.0)
    matrix = to_rotation_matrix(q_bw, atol=0.0)

    np.testing.assert_allclose(matrix.T @ matrix, np.eye(3), atol=TEST_ATOL)
    assert np.linalg.det(matrix) == pytest.approx(1.0, abs=TEST_ATOL)


def test_apply_increment_uses_left_multiplication_order() -> None:
    q_old = from_axis_angle([1.0, 0.0, 0.0], math.pi / 2.0, atol=0.0)
    delta_q = from_axis_angle([0.0, 0.0, 1.0], math.pi / 2.0, atol=0.0)
    probe = [0.0, 1.0, 0.0]

    actual = apply_increment_left(q_old, delta_q, atol=0.0)
    expected = normalize_quaternion(multiply(delta_q, q_old), atol=0.0)
    wrong_order = normalize_quaternion(multiply(q_old, delta_q), atol=0.0)

    np.testing.assert_allclose(
        rotate_body_to_world(actual, probe, atol=0.0),
        rotate_body_to_world(expected, probe, atol=0.0),
        atol=TEST_ATOL,
    )
    assert not np.allclose(
        rotate_body_to_world(actual, probe, atol=0.0),
        rotate_body_to_world(wrong_order, probe, atol=0.0),
        atol=TEST_ATOL,
    )


@pytest.mark.parametrize(
    "function",
    [
        normalize_quaternion,
        inverse,
        rotate_body_to_world,
        rotate_world_to_body,
        to_rotation_matrix,
        apply_increment_left,
    ],
)
def test_atol_is_required_keyword_only(function: object) -> None:
    parameter = inspect.signature(function).parameters["atol"]

    assert parameter.kind is inspect.Parameter.KEYWORD_ONLY
    assert parameter.default is inspect.Parameter.empty


def test_public_helpers_do_not_modify_inputs() -> None:
    q = np.array([2.0, 0.5, -0.25, 0.75])
    other = np.array([1.0, -0.5, 0.25, 0.125])
    axis = np.array([1.0, 2.0, -1.0])
    vector = np.array([3.0, -4.0, 2.0])
    q_original = q.copy()
    other_original = other.copy()
    axis_original = axis.copy()
    vector_original = vector.copy()

    normalize_quaternion(q, atol=0.0)
    conjugate(q)
    multiply(q, other)
    inverse(q, atol=0.0)
    from_axis_angle(axis, 0.7, atol=0.0)
    rotate_body_to_world(q, vector, atol=0.0)
    rotate_world_to_body(q, vector, atol=0.0)
    to_rotation_matrix(q, atol=0.0)
    apply_increment_left(q, other, atol=0.0)

    np.testing.assert_array_equal(q, q_original)
    np.testing.assert_array_equal(other, other_original)
    np.testing.assert_array_equal(axis, axis_original)
    np.testing.assert_array_equal(vector, vector_original)
