"""Physics core için küçük, güvenli ve frame-bağımsız vektör yardımcıları.

Bu modül vektörlere fiziksel birim, coordinate frame veya domain anlamı
yüklemez. Canonical temsil, tek boyutlu ve ``numpy.float64`` dtype'lı bağımsız
bir ``numpy.ndarray`` kopyasıdır. Geçersiz sayılar sessizce düzeltilmez.

Normalization toleransı domain-specific olduğu için global veya varsayılan
bir tolerance tanımlanmaz; çağıran kod ``atol`` değerini açıkça sağlamalıdır.
"""

import numpy as np
from numpy.typing import ArrayLike as _ArrayLike
from numpy.typing import NDArray as _NDArray

from roketsim_native.math.numerical import is_near_zero, require_finite


__all__ = ("as_vector", "magnitude", "normalize", "dot", "cross3")


_VectorArray = _NDArray[np.float64]


def as_vector(
    value: _ArrayLike,
    *,
    size: int | None = None,
    name: str = "vector",
) -> _VectorArray:
    """Girdiyi finite bileşenli, bağımsız bir 1-D float64 array'e çevir."""

    try:
        vector = np.array(value, dtype=np.float64, copy=True)
    except (TypeError, ValueError, OverflowError) as exc:
        raise ValueError(f"{name} must contain numerical values") from exc

    if vector.ndim != 1:
        raise ValueError(f"{name} must be 1-D; got shape {vector.shape}")
    if size is not None and vector.shape != (size,):
        raise ValueError(
            f"{name} must have shape ({size},); got shape {vector.shape}"
        )

    for index, component in enumerate(vector):
        require_finite(float(component), name=f"{name}[{index}]")

    return vector


def magnitude(vector: _ArrayLike) -> float:
    """Finite bir 1-D vektörün Öklid normunu döndür."""

    result = float(np.linalg.norm(as_vector(vector)))
    return require_finite(result, name="magnitude")


def normalize(vector: _ArrayLike, *, atol: float) -> _VectorArray:
    """Vektörü normalize et; sıfır veya near-zero norm için hata üret."""

    canonical = as_vector(vector)
    norm = magnitude(canonical)
    if is_near_zero(norm, atol=atol):
        raise ValueError(
            f"vector magnitude must be greater than atol; got {norm!r}"
        )
    return canonical / norm


def dot(a: _ArrayLike, b: _ArrayLike) -> float:
    """Aynı boyuttaki iki 1-D vektörün dot product sonucunu döndür."""

    left = as_vector(a, name="a")
    right = as_vector(b, name="b")
    if left.shape != right.shape:
        raise ValueError(
            f"a and b must have the same shape; got {left.shape} and {right.shape}"
        )

    result = float(np.dot(left, right))
    return require_finite(result, name="dot product")


def cross3(a: _ArrayLike, b: _ArrayLike) -> _VectorArray:
    """İki 3-bileşenli vektörün right-hand cross product sonucunu döndür."""

    left = as_vector(a, size=3, name="a")
    right = as_vector(b, size=3, name="b")
    return as_vector(np.cross(left, right), size=3, name="cross product")
