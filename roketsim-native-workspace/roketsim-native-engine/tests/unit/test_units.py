"""Unit tests for the NAT-003 canonical SI policy."""

from roketsim_native.units.policy import CANONICAL_UNITS


def test_canonical_units_match_nat_003_policy() -> None:
    assert dict(CANONICAL_UNITS) == {
        "length": "m",
        "mass": "kg",
        "time": "s",
        "velocity": "m/s",
        "acceleration": "m/s^2",
        "force": "N",
        "moment": "N*m",
        "pressure": "Pa",
        "density": "kg/m^3",
        "temperature": "K",
        "angle": "rad",
        "angular_rate": "rad/s",
        "angular_accel": "rad/s^2",
        "area": "m^2",
        "volume": "m^3",
        "inertia": "kg*m^2",
        "dynamic_viscosity": "Pa*s",
        "kinematic_viscosity": "m^2/s",
    }


def test_canonical_angle_unit_is_radian() -> None:
    assert CANONICAL_UNITS["angle"] == "rad"
