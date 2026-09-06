"""NAT-003..NAT-007 matematik temelinin bütünleşik V&V kabul testleri.

Bu testler yeni bir convention tanımlamaz. SI/radian, WORLD_ENU, BODY,
geometry x_geo ve scalar-first q_BW kararlarının modüller arası aynı anlamı
koruduğunu deterministik analitik fixture'larla doğrular. ``VV_ATOL`` yalnız
test karşılaştırma toleransıdır; production physics toleransı değildir.
"""

import math

import numpy as np
import pytest

from roketsim_native.math.frames import (
    FRAME_AXES,
    GEOMETRY_AXIAL_CONVENTION,
    ReferenceFrame,
)
from roketsim_native.math.quaternion import (
    apply_increment_left,
    as_quaternion,
    from_axis_angle,
    multiply,
    normalize_quaternion,
    rotate_body_to_world,
    rotate_world_to_body,
    to_rotation_matrix,
)
from roketsim_native.math.vectors import cross3, magnitude
from roketsim_native.units.policy import CANONICAL_UNITS


VV_ATOL = 1.0e-12
E_X = np.array([1.0, 0.0, 0.0], dtype=np.float64)
E_Y = np.array([0.0, 1.0, 0.0], dtype=np.float64)
E_Z = np.array([0.0, 0.0, 1.0], dtype=np.float64)
Q_IDENTITY = np.array([1.0, 0.0, 0.0, 0.0], dtype=np.float64)


def test_frozen_convention_audit() -> None:
    """Dondurulmuş unit, frame, geometry ve quaternion kararlarını denetle."""

    assert CANONICAL_UNITS["length"] == "m"
    assert CANONICAL_UNITS["mass"] == "kg"
    assert CANONICAL_UNITS["angle"] == "rad"

    assert list(ReferenceFrame) == [ReferenceFrame.WORLD_ENU, ReferenceFrame.BODY]
    assert [axis.meaning for axis in FRAME_AXES[ReferenceFrame.WORLD_ENU]] == [
        "East",
        "North",
        "Up",
    ]
    assert [axis.identifier for axis in FRAME_AXES[ReferenceFrame.BODY]] == [
        "x_B",
        "y_B",
        "z_B",
    ]
    assert FRAME_AXES[ReferenceFrame.BODY][2].meaning == (
        "longitudinal / thrust / roll axis"
    )

    assert GEOMETRY_AXIAL_CONVENTION == {
        "origin": "nose tip",
        "coordinate": "x_geo",
        "positive_direction": "nose -> tail",
    }
    assert (
        GEOMETRY_AXIAL_CONVENTION["coordinate"]
        != FRAME_AXES[ReferenceFrame.BODY][2].identifier
    )

    scalar_first_probe = as_quaternion([1.0, 2.0, 3.0, 4.0])
    np.testing.assert_array_equal(scalar_first_probe, [1.0, 2.0, 3.0, 4.0])
    assert scalar_first_probe.dtype == np.dtype(np.float64)
    assert "derived" in (to_rotation_matrix.__doc__ or "")


def test_quat_t01_identity_chain_preserves_body_vector() -> None:
    """QUAT-T01: identity q_BW, BODY -> WORLD -> BODY zincirini değiştirmez."""

    vector_b = np.array([1.0, 2.0, 3.0], dtype=np.float64)

    vector_w = rotate_body_to_world(Q_IDENTITY, vector_b, atol=VV_ATOL)
    recovered_b = rotate_world_to_body(Q_IDENTITY, vector_w, atol=VV_ATOL)

    np.testing.assert_allclose(vector_w, vector_b, atol=VV_ATOL, rtol=0.0)
    np.testing.assert_allclose(recovered_b, vector_b, atol=VV_ATOL, rtol=0.0)


@pytest.mark.parametrize(
    ("axis", "expected_world"),
    [
        (E_X, -E_Y),
        (E_Y, E_X),
    ],
)
def test_quat_t02_known_body_longitudinal_axis_mapping(
    axis: np.ndarray,
    expected_world: np.ndarray,
) -> None:
    """QUAT-T02: +90° right-hand dönüşümünde BODY z_B eşlemesini doğrula."""

    assert FRAME_AXES[ReferenceFrame.BODY][2].identifier == "z_B"
    q_bw = from_axis_angle(axis, math.pi / 2.0, atol=VV_ATOL)

    vector_w = rotate_body_to_world(q_bw, E_Z, atol=VV_ATOL)

    np.testing.assert_allclose(vector_w, expected_world, atol=VV_ATOL, rtol=0.0)


@pytest.mark.parametrize(
    ("angle_rad", "expected_world"),
    [
        (-math.pi / 2.0, -E_Y),
        (math.pi, -E_X),
    ],
)
def test_analytic_radian_fixtures_about_z(
    angle_rad: float,
    expected_world: np.ndarray,
) -> None:
    """Negatif pi/2 ve pi fixture'larının radian/right-hand anlamını koru."""

    q_bw = from_axis_angle(E_Z, angle_rad, atol=VV_ATOL)

    vector_w = rotate_body_to_world(q_bw, E_X, atol=VV_ATOL)

    np.testing.assert_allclose(vector_w, expected_world, atol=VV_ATOL, rtol=0.0)


def test_quat_t03_body_world_body_round_trip() -> None:
    """QUAT-T03: non-trivial q_BW ile ters dönüşüm zincirini doğrula."""

    axis = np.array([1.0, -2.0, 0.5], dtype=np.float64)
    q_bw = from_axis_angle(axis, 0.73, atol=VV_ATOL)
    vector_b = np.array([2.5, -1.25, 4.0], dtype=np.float64)

    vector_w = rotate_body_to_world(q_bw, vector_b, atol=VV_ATOL)
    recovered_b = rotate_world_to_body(q_bw, vector_w, atol=VV_ATOL)

    np.testing.assert_allclose(recovered_b, vector_b, atol=VV_ATOL, rtol=0.0)


def test_quaternion_and_matrix_body_to_world_parity() -> None:
    """Derived matrix ile q_BW cebrinin aynı BODY -> WORLD yönünü kullandığını doğrula."""

    q_bw = from_axis_angle([2.0, 1.0, -3.0], 1.1, atol=VV_ATOL)
    vector_b = np.array([-0.5, 3.0, 2.25], dtype=np.float64)

    via_quaternion = rotate_body_to_world(q_bw, vector_b, atol=VV_ATOL)
    via_matrix = to_rotation_matrix(q_bw, atol=VV_ATOL) @ vector_b

    np.testing.assert_allclose(via_quaternion, via_matrix, atol=VV_ATOL, rtol=0.0)


@pytest.mark.parametrize(
    ("axis", "angle_rad", "vector_b"),
    [
        ([1.0, 2.0, 3.0], 0.25, [4.0, -1.0, 2.0]),
        ([-2.0, 0.5, 1.0], -math.pi / 2.0, [0.25, 3.5, -2.0]),
        ([0.0, 0.0, 5.0], math.pi, [1.0, 2.0, 3.0]),
    ],
)
def test_rotation_preserves_vector_norm(
    axis: list[float],
    angle_rad: float,
    vector_b: list[float],
) -> None:
    """Çeşitli non-trivial orientation'larda Öklid normunun korunduğunu doğrula."""

    q_bw = from_axis_angle(axis, angle_rad, atol=VV_ATOL)
    vector_w = rotate_body_to_world(q_bw, vector_b, atol=VV_ATOL)

    assert magnitude(vector_w) == pytest.approx(
        magnitude(vector_b), abs=VV_ATOL, rel=0.0
    )


def test_rotation_matrix_is_proper() -> None:
    """Derived rotation matrix'in orthogonal ve reflection içermeyen yapı olduğunu doğrula."""

    q_bw = from_axis_angle([1.0, 4.0, -2.0], 0.91, atol=VV_ATOL)
    matrix = to_rotation_matrix(q_bw, atol=VV_ATOL)

    np.testing.assert_allclose(
        matrix.T @ matrix,
        np.identity(3),
        atol=VV_ATOL,
        rtol=0.0,
    )
    assert np.linalg.det(matrix) == pytest.approx(1.0, abs=VV_ATOL, rel=0.0)


def test_left_increment_order_uses_delta_times_old_orientation() -> None:
    """Soldan update'ı doğrula ve non-commuting sağdan çarpımı probe ile ayır."""

    q_old = from_axis_angle(E_X, math.pi / 2.0, atol=VV_ATOL)
    delta_q = from_axis_angle(E_Z, math.pi / 2.0, atol=VV_ATOL)
    actual = apply_increment_left(q_old, delta_q, atol=VV_ATOL)
    expected = normalize_quaternion(multiply(delta_q, q_old), atol=VV_ATOL)
    wrong_order = normalize_quaternion(multiply(q_old, delta_q), atol=VV_ATOL)
    probe_b = E_Y

    actual_probe = rotate_body_to_world(actual, probe_b, atol=VV_ATOL)
    expected_probe = rotate_body_to_world(expected, probe_b, atol=VV_ATOL)
    wrong_probe = rotate_body_to_world(wrong_order, probe_b, atol=VV_ATOL)

    np.testing.assert_allclose(actual_probe, expected_probe, atol=VV_ATOL, rtol=0.0)
    assert not np.allclose(actual_probe, wrong_probe, atol=VV_ATOL, rtol=0.0)


def test_vector_and_quaternion_right_hand_conventions_are_consistent() -> None:
    """e_x x e_y = e_z ile +90° z dönüşümünün aynı handedness'i kullandığını doğrula."""

    q_bw = from_axis_angle(E_Z, math.pi / 2.0, atol=VV_ATOL)
    rotated_x = rotate_body_to_world(q_bw, E_X, atol=VV_ATOL)

    np.testing.assert_allclose(cross3(E_X, E_Y), E_Z, atol=VV_ATOL, rtol=0.0)
    np.testing.assert_allclose(rotated_x, E_Y, atol=VV_ATOL, rtol=0.0)
    np.testing.assert_allclose(cross3(E_X, rotated_x), E_Z, atol=VV_ATOL, rtol=0.0)


def test_q_and_negative_q_have_equivalent_orientation() -> None:
    """Orientation eşitliğinin raw quaternion bileşen eşitliği olmadığını doğrula."""

    q_bw = from_axis_angle([1.0, -1.0, 2.0], 0.88, atol=VV_ATOL)
    vector_b = np.array([3.0, 0.25, -4.0], dtype=np.float64)

    rotated_q = rotate_body_to_world(q_bw, vector_b, atol=VV_ATOL)
    rotated_negative_q = rotate_body_to_world(-q_bw, vector_b, atol=VV_ATOL)

    np.testing.assert_allclose(rotated_q, rotated_negative_q, atol=VV_ATOL, rtol=0.0)


def test_integrated_transformation_chain_has_no_hidden_mutation() -> None:
    """Cross-module zincirde vector, q_BW ve delta_q girdilerinin değişmediğini doğrula."""

    vector_b = np.array([1.5, -2.5, 0.75], dtype=np.float64)
    q_old = from_axis_angle([1.0, 3.0, -1.0], 0.4, atol=VV_ATOL)
    delta_q = from_axis_angle(E_Z, -0.2, atol=VV_ATOL)
    vector_before = vector_b.copy()
    q_old_before = q_old.copy()
    delta_before = delta_q.copy()

    q_new = apply_increment_left(q_old, delta_q, atol=VV_ATOL)
    vector_w = rotate_body_to_world(q_new, vector_b, atol=VV_ATOL)
    rotate_world_to_body(q_new, vector_w, atol=VV_ATOL)
    to_rotation_matrix(q_new, atol=VV_ATOL)

    np.testing.assert_array_equal(vector_b, vector_before)
    np.testing.assert_array_equal(q_old, q_old_before)
    np.testing.assert_array_equal(delta_q, delta_before)
