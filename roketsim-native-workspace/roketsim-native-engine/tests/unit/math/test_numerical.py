"""Unit tests for the NAT-004 scalar numerical guards."""

import inspect
import math
import sys

import pytest

from roketsim_native.math.numerical import (
    is_finite,
    is_near_zero,
    require_finite,
)


@pytest.mark.parametrize(
    "value",
    [0.0, 12.5, -12.5, sys.float_info.min, sys.float_info.max],
)
def test_is_finite_accepts_finite_values(value: float) -> None:
    assert is_finite(value)


@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf])
def test_is_finite_rejects_non_finite_values(value: float) -> None:
    assert not is_finite(value)


def test_require_finite_returns_value_unchanged() -> None:
    value = 12.5

    assert require_finite(value) is value


@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf])
def test_require_finite_raises_for_non_finite_values(value: float) -> None:
    with pytest.raises(ValueError, match="must be finite"):
        require_finite(value)


def test_require_finite_includes_name_in_error() -> None:
    with pytest.raises(ValueError, match="dynamic_pressure"):
        require_finite(math.nan, name="dynamic_pressure")


@pytest.mark.parametrize(
    ("value", "atol", "expected"),
    [
        (0.0, 1.0, True),
        (0.5, 1.0, True),
        (1.0, 1.0, True),
        (1.01, 1.0, False),
        (-0.5, 1.0, True),
        (0.0, 0.0, True),
        (sys.float_info.min, 0.0, False),
    ],
)
def test_is_near_zero_uses_inclusive_absolute_tolerance(
    value: float,
    atol: float,
    expected: bool,
) -> None:
    assert is_near_zero(value, atol=atol) is expected


@pytest.mark.parametrize("atol", [-1.0, math.nan, math.inf, -math.inf])
def test_is_near_zero_rejects_invalid_tolerance(atol: float) -> None:
    with pytest.raises(ValueError, match="atol"):
        is_near_zero(0.0, atol=atol)


@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf])
def test_is_near_zero_rejects_non_finite_value(value: float) -> None:
    with pytest.raises(ValueError, match="value must be finite"):
        is_near_zero(value, atol=1.0)


def test_is_near_zero_requires_explicit_keyword_tolerance() -> None:
    parameter = inspect.signature(is_near_zero).parameters["atol"]

    assert parameter.kind is inspect.Parameter.KEYWORD_ONLY
    assert parameter.default is inspect.Parameter.empty
