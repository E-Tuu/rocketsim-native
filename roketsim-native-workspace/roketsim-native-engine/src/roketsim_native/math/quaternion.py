"""Scalar-first quaternion cebiri ve BODY/WORLD rotation yardımcıları.

Quaternion temsili her zaman ``[w, x, y, z]`` sıralı, shape ``(4,)`` ve
``numpy.float64`` dtype'lı bağımsız bir array'dir. Orientation quaternion'u
``q_BW``, BODY frame'den WORLD frame'e yönelimi temsil eder:

``v_W = q_BW * v_B * inverse(q_BW)``

Ters yön ``inverse(q_BW) * v_W * q_BW`` ile hesaplanır. Hamilton product
commutative olmadığı için sıra convention'ın bir parçasıdır. Increment update
``delta_q * q_old`` biçiminde soldan uygulanır ve sonuç normalize edilir.

Orientation kullanan rotation ve matrix helper'ları arbitrary non-unit
quaternion'u açıkça normalize eder; zero/near-zero quaternion'u reddeder. Bu
normalization quaternion ölçeğinin orientation taşımadığı gerçeğini uygular,
silent identity fallback üretmez. Global/default tolerance yoktur; ``atol``
çağıran tarafından açıkça verilir.

Bu modül Euler-angle state, angular integration veya dynamics modeli içermez.
"""

import math

import numpy as np
from numpy.typing import ArrayLike as _ArrayLike
from numpy.typing import NDArray as _NDArray

from roketsim_native.math.numerical import is_near_zero, require_finite
from roketsim_native.math.vectors import (
    as_vector,
    magnitude,
    normalize as normalize_vector,
)


__all__ = (
    "as_quaternion",
    "quaternion_norm",
    "normalize_quaternion",
    "conjugate",
    "multiply",
    "inverse",
    "from_axis_angle",
    "rotate_body_to_world",
    "rotate_world_to_body",
    "to_rotation_matrix",
    "apply_increment_left",
)


_QuaternionArray = _NDArray[np.float64]
_VectorArray = _NDArray[np.float64]
_RotationMatrix = _NDArray[np.float64]


def as_quaternion(
    value: _ArrayLike,
    *,
    name: str = "quaternion",
) -> _QuaternionArray:
    """Girdiyi finite, bağımsız ve scalar-first float64 quaternion'a çevir."""

    return as_vector(value, size=4, name=name)


def quaternion_norm(q: _ArrayLike) -> float:
    """Scalar-first quaternion'un Öklid normunu döndür."""

    return magnitude(as_quaternion(q))


def normalize_quaternion(q: _ArrayLike, *, atol: float) -> _QuaternionArray:
    """Quaternion'u normalize et; zero/near-zero norm için hata üret."""

    canonical = as_quaternion(q)
    norm = quaternion_norm(canonical)
    if is_near_zero(norm, atol=atol):
        raise ValueError(
            f"quaternion norm must be greater than atol; got {norm!r}"
        )
    return as_quaternion(canonical / norm, name="normalized quaternion")


def conjugate(q: _ArrayLike) -> _QuaternionArray:
    """``[w, x, y, z]`` için ``[w, -x, -y, -z]`` döndür."""

    result = as_quaternion(q)
    result[1:] *= -1.0
    return result


def multiply(q1: _ArrayLike, q2: _ArrayLike) -> _QuaternionArray:
    """İki scalar-first quaternion'un sıralı Hamilton product'ını döndür."""

    left = as_quaternion(q1, name="q1")
    right = as_quaternion(q2, name="q2")
    w1, x1, y1, z1 = left
    w2, x2, y2, z2 = right

    return as_quaternion(
        [
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
            w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
        ],
        name="Hamilton product",
    )


def inverse(q: _ArrayLike, *, atol: float) -> _QuaternionArray:
    """Genel quaternion inverse'ünü ``conjugate(q) / ||q||²`` ile döndür."""

    canonical = as_quaternion(q)
    norm = quaternion_norm(canonical)
    if is_near_zero(norm, atol=atol):
        raise ValueError(
            f"quaternion norm must be greater than atol; got {norm!r}"
        )

    norm_squared = require_finite(norm * norm, name="quaternion norm squared")
    if norm_squared == 0.0:
        raise ValueError("quaternion norm squared must be greater than zero")
    return as_quaternion(
        conjugate(canonical) / norm_squared,
        name="quaternion inverse",
    )


def from_axis_angle(
    axis: _ArrayLike,
    angle_rad: float,
    *,
    atol: float,
) -> _QuaternionArray:
    """Radian angle ve 3-D axis'ten right-hand unit quaternion üret."""

    require_finite(angle_rad, name="angle_rad")
    unit_axis = normalize_vector(axis, atol=atol)
    if unit_axis.shape != (3,):
        raise ValueError(f"axis must have shape (3,); got shape {unit_axis.shape}")

    half_angle = angle_rad / 2.0
    sin_half = math.sin(half_angle)
    return as_quaternion(
        [
            math.cos(half_angle),
            unit_axis[0] * sin_half,
            unit_axis[1] * sin_half,
            unit_axis[2] * sin_half,
        ]
    )


def rotate_body_to_world(
    q_bw: _ArrayLike,
    vector_b: _ArrayLike,
    *,
    atol: float,
) -> _VectorArray:
    """``q_BW * v_B * inverse(q_BW)`` ile BODY vektörünü WORLD'e döndür."""

    orientation = normalize_quaternion(q_bw, atol=atol)
    vector = as_vector(vector_b, size=3, name="vector_b")
    pure_vector = as_quaternion([0.0, *vector], name="vector_b quaternion")
    rotated = multiply(
        multiply(orientation, pure_vector),
        inverse(orientation, atol=atol),
    )
    return as_vector(rotated[1:], size=3, name="vector_w")


def rotate_world_to_body(
    q_bw: _ArrayLike,
    vector_w: _ArrayLike,
    *,
    atol: float,
) -> _VectorArray:
    """``inverse(q_BW) * v_W * q_BW`` ile WORLD vektörünü BODY'ye döndür."""

    orientation = normalize_quaternion(q_bw, atol=atol)
    vector = as_vector(vector_w, size=3, name="vector_w")
    pure_vector = as_quaternion([0.0, *vector], name="vector_w quaternion")
    rotated = multiply(
        multiply(inverse(orientation, atol=atol), pure_vector),
        orientation,
    )
    return as_vector(rotated[1:], size=3, name="vector_b")


def to_rotation_matrix(q: _ArrayLike, *, atol: float) -> _RotationMatrix:
    """Quaternion rotation ile aynı BODY -> WORLD derived 3x3 matrix'i üret."""

    w, x, y, z = normalize_quaternion(q, atol=atol)
    return np.array(
        [
            [1.0 - 2.0 * (y * y + z * z), 2.0 * (x * y - w * z), 2.0 * (x * z + w * y)],
            [2.0 * (x * y + w * z), 1.0 - 2.0 * (x * x + z * z), 2.0 * (y * z - w * x)],
            [2.0 * (x * z - w * y), 2.0 * (y * z + w * x), 1.0 - 2.0 * (x * x + y * y)],
        ],
        dtype=np.float64,
    )


def apply_increment_left(
    q_old: _ArrayLike,
    delta_q: _ArrayLike,
    *,
    atol: float,
) -> _QuaternionArray:
    """``normalize(delta_q * q_old)`` soldan increment update'ını uygula."""

    old_orientation = normalize_quaternion(q_old, atol=atol)
    delta_orientation = normalize_quaternion(delta_q, atol=atol)
    return normalize_quaternion(
        multiply(delta_orientation, old_orientation),
        atol=atol,
    )
