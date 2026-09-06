"""NAT-005 frame-bağımsız vektör yardımcılarının unit testleri."""

import math

import numpy as np
import pytest

from roketsim_native.math.vectors import (
    as_vector,
    cross3,
    dot,
    magnitude,
    normalize,
)


@pytest.mark.parametrize("value", [[1, 2, 3], (1, 2, 3)])
def test_as_vector_accepts_list_and_tuple(value: object) -> None:
    result = as_vector(value)

    np.testing.assert_array_equal(result, [1.0, 2.0, 3.0])


def test_as_vector_accepts_ndarray_and_returns_float64() -> None:
    result = as_vector(np.array([1, 2, 3], dtype=np.int32))

    assert result.dtype == np.dtype(np.float64)
    np.testing.assert_array_equal(result, [1.0, 2.0, 3.0])


def test_as_vector_accepts_requested_size() -> None:
    result = as_vector([1, 2], size=2)

    assert result.shape == (2,)


def test_as_vector_rejects_wrong_size() -> None:
    with pytest.raises(ValueError, match="shape"):
        as_vector([1, 2], size=3)


def test_as_vector_rejects_two_dimensional_input_without_flattening() -> None:
    with pytest.raises(ValueError, match="1-D"):
        as_vector([[1, 2, 3]])


@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf])
def test_as_vector_rejects_non_finite_component(value: float) -> None:
    with pytest.raises(ValueError, match=r"sample\[1\].*finite"):
        as_vector([1.0, value, 3.0], name="sample")


def test_as_vector_returns_independent_array() -> None:
    source = np.array([1.0, 2.0, 3.0], dtype=np.float64)
    result = as_vector(source)

    assert not np.shares_memory(source, result)
    result[0] = 99.0
    np.testing.assert_array_equal(source, [1.0, 2.0, 3.0])


def test_magnitude_known_vector() -> None:
    assert magnitude([3.0, 4.0, 0.0]) == pytest.approx(5.0)


def test_magnitude_zero_vector() -> None:
    assert magnitude([0.0, 0.0, 0.0]) == 0.0


def test_magnitude_does_not_modify_input() -> None:
    vector = np.array([3.0, 4.0, 0.0])
    original = vector.copy()

    magnitude(vector)

    np.testing.assert_array_equal(vector, original)


def test_normalize_known_vector() -> None:
    result = normalize([3.0, 4.0, 0.0], atol=0.0)

    np.testing.assert_allclose(result, [0.6, 0.8, 0.0])


def test_normalized_magnitude_is_one() -> None:
    result = normalize([1.0, 2.0, 3.0], atol=0.0)

    assert magnitude(result) == pytest.approx(1.0)


def test_normalize_rejects_exact_zero() -> None:
    with pytest.raises(ValueError, match="magnitude"):
        normalize([0.0, 0.0, 0.0], atol=0.0)


def test_normalize_rejects_near_zero_for_supplied_tolerance() -> None:
    with pytest.raises(ValueError, match="magnitude"):
        normalize([1.0e-9, 0.0, 0.0], atol=1.0e-8)


@pytest.mark.parametrize("atol", [-1.0, math.nan, math.inf, -math.inf])
def test_normalize_rejects_invalid_tolerance(atol: float) -> None:
    with pytest.raises(ValueError, match="atol"):
        normalize([1.0, 0.0, 0.0], atol=atol)


def test_normalize_does_not_modify_or_alias_input() -> None:
    vector = np.array([3.0, 4.0, 0.0])
    original = vector.copy()

    result = normalize(vector, atol=0.0)

    np.testing.assert_array_equal(vector, original)
    assert not np.shares_memory(vector, result)


def test_dot_of_orthogonal_basis_vectors_is_zero() -> None:
    assert dot([1.0, 0.0, 0.0], [0.0, 1.0, 0.0]) == 0.0


def test_dot_known_general_case() -> None:
    assert dot([1.0, 2.0, 3.0], [4.0, -5.0, 6.0]) == pytest.approx(12.0)


def test_dot_rejects_size_mismatch() -> None:
    with pytest.raises(ValueError, match="same shape"):
        dot([1.0, 2.0], [1.0, 2.0, 3.0])


def test_cross3_ex_cross_ey_is_ez() -> None:
    result = cross3([1.0, 0.0, 0.0], [0.0, 1.0, 0.0])

    np.testing.assert_array_equal(result, [0.0, 0.0, 1.0])


def test_cross3_ey_cross_ex_is_negative_ez() -> None:
    result = cross3([0.0, 1.0, 0.0], [1.0, 0.0, 0.0])

    np.testing.assert_array_equal(result, [0.0, 0.0, -1.0])


def test_cross3_parallel_vectors_produce_zero() -> None:
    result = cross3([1.0, 2.0, 3.0], [2.0, 4.0, 6.0])

    np.testing.assert_array_equal(result, [0.0, 0.0, 0.0])


@pytest.mark.parametrize("value", [[1.0, 2.0], [1.0, 2.0, 3.0, 4.0]])
def test_cross3_rejects_wrong_size(value: list[float]) -> None:
    with pytest.raises(ValueError, match="shape"):
        cross3(value, [1.0, 0.0, 0.0])


def test_cross3_does_not_modify_inputs() -> None:
    left = np.array([1.0, 2.0, 3.0])
    right = np.array([4.0, 5.0, 6.0])
    left_original = left.copy()
    right_original = right.copy()

    cross3(left, right)

    np.testing.assert_array_equal(left, left_original)
    np.testing.assert_array_equal(right, right_original)
