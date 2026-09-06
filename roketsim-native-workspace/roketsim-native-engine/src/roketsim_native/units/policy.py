"""Canonical internal unit policy for the RoketSim Native physics core.

The technical specification Chapter 5, CONV-001, requires SI internal units.
CONV-015 defines radians, radians per second, and radians per second squared
for angular quantities. ``CANONICAL_UNITS`` is declarative policy data; it
does not perform conversion, validation, or physics calculations.

Boundary policy
---------------
External user interfaces, contracts, and file formats may represent values in
presentation units such as mm, cm, inch, degree, gram, or km/h. Boundary and
adapter layers must convert those values to the canonical SI units before they
enter the physics core. Presentation units are not carried inside the core.
Conversion adapters are outside NAT-003 and are not implemented here.

Naming policy
-------------
Boundary and contract fields may make units explicit, for example ``length_m``,
``mass_kg``, ``time_s``, ``thrust_n``, and ``angle_rad``. Physics modules whose
SI invariant is already guaranteed may use readable scientific names such as
``rho``, ``mass``, ``velocity``, ``force``, and ``alpha``. A single scope must
not mix values expressed in different unit systems.

Invalid numerical values
------------------------
Per CONV-017, missing or non-applicable internal floating-point data uses NaN.
At a future JSON/API boundary, NaN will be mapped to null. Validation and
mapping behavior belong to later tasks and are not implemented here.
"""

from types import MappingProxyType
from typing import Final, Mapping


CANONICAL_UNITS: Final[Mapping[str, str]] = MappingProxyType(
    {
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
)
