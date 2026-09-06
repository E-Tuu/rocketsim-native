"""Small floating-point guards shared by the native physics core.

Numerical guards are not a physics validity model. Tolerances are specific to
their physics domain, so this module defines no global or default tolerance.
Invalid numerical data is rejected rather than silently corrected.

The internal missing/non-applicable NaN policy is separate from these helpers.
In particular, NaN is never treated as or converted to physical zero.
"""

import math


def is_finite(value: float) -> bool:
    """Return whether a scalar value is neither NaN nor infinity."""

    return math.isfinite(value)


def require_finite(value: float, *, name: str = "value") -> float:
    """Return an unchanged finite value or raise ``ValueError``."""

    if not is_finite(value):
        raise ValueError(f"{name} must be finite; got {value!r}")
    return value


def is_near_zero(value: float, *, atol: float) -> bool:
    """Return ``abs(value) <= atol`` after validating both scalar inputs."""

    require_finite(atol, name="atol")
    if atol < 0:
        raise ValueError(f"atol must be non-negative; got {atol!r}")

    require_finite(value)
    return abs(value) <= atol
