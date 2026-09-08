"""NAT-012B Part 2: CP/CG/Dmax static-margin sahiplik ve V&V testleri."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature

import pytest

from roketsim_native.aerodynamics.drag import AerodynamicEvaluationError
from roketsim_native.aerodynamics.static_margin import (
    StaticMarginCalculator,
    StaticMarginResult,
)
from roketsim_native.aerodynamics.static_stability import (
    NATIVE_STATIC_STABILITY_V1_PROFILE,
    StaticAeroContribution,
    StaticAerodynamicEvaluator,
    StaticAerodynamicProperties,
)
from roketsim_native.flight_conditions.basic import BasicFlightConditions
from roketsim_native.geometry.models import (
    CenteringRingPairGeometry,
    ConicalNoseGeometry,
    CylindricalBodyGeometry,
    FinAngularArrangement,
    FinCrossSection,
    MotorAttachmentGeometry,
    MotorMountTubeGeometry,
    NoseConstructionMode,
    ReferenceGeometryPolicy,
    SingleStageRocketGeometry,
    TrapezoidalFinSetGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.mass.total import RocketMassProperties


CALCULATOR = StaticMarginCalculator()


@pytest.fixture(scope="module")
def geometry():
    source = SingleStageRocketGeometry(
        .1,
        ConicalNoseGeometry(.3, NoseConstructionMode.HOLLOW_SHELL, .002),
        CylindricalBodyGeometry(.7, .002),
        TrapezoidalFinSetGeometry(
            4, .18, .08, .12, .05, .72, .003, FinCrossSection.SQUARE,
            FinAngularArrangement.EQUALLY_SPACED,
        ),
        MotorAttachmentGeometry(
            MotorMountTubeGeometry(.12, .029, .001, 0.),
            CenteringRingPairGeometry(.003),
            .005,
        ),
        ReferenceGeometryPolicy.MAXIMUM_DIAMETER,
    )
    return GeometryResolver().resolve(rocket_geometry=source)


def static_properties(cp):
    contribution = StaticAeroContribution(1., cp)
    return StaticAerodynamicProperties(
        contribution, contribution, 2., cp, NATIVE_STATIC_STABILITY_V1_PROFILE
    )


def calculate(cp, cg, geometry):
    return CALCULATOR.evaluate(
        static_aerodynamics=static_properties(cp),
        rocket_mass_properties=RocketMassProperties(1., cg),
        geometry=geometry,
    )


def evaluated_static(mach, geometry):
    return StaticAerodynamicEvaluator().evaluate(
        geometry=geometry,
        flight_conditions=BasicFlightConditions(100., mach, 0., 0.),
        model_profile=NATIVE_STATIC_STABILITY_V1_PROFILE,
    )


def test_result_and_calculator_contract():
    """Result tek alanlı frozen/slotted; calculator parametresiz ve keyword-only."""
    assert [field.name for field in fields(StaticMarginResult)] == [
        "static_margin_calibers"
    ]
    result = StaticMarginResult(1.)
    assert not hasattr(result, "__dict__")
    with pytest.raises(FrozenInstanceError):
        result.static_margin_calibers = 2.
    assert not signature(StaticMarginCalculator).parameters
    parameters = signature(CALCULATOR.evaluate).parameters
    assert tuple(parameters) == (
        "static_aerodynamics", "rocket_mass_properties", "geometry"
    )
    assert all(
        parameter.kind is Parameter.KEYWORD_ONLY
        and parameter.default is Parameter.empty
        for parameter in parameters.values()
    )
    assert not hasattr(CALCULATOR, "__dict__")


@pytest.mark.parametrize(
    "cp,cg,expected",
    [(0.7, 0.6, 1.), (0.65, 0.65, 0.), (0.6, 0.7, -1.)],
    ids=("positive-cp-aft", "neutral", "negative-cp-forward"),
)
def test_signed_static_margin_semantics(cp, cg, expected, geometry):
    """Signed sonuç tasarım güvenliği sınıfına çevrilmez."""
    assert calculate(cp, cg, geometry).static_margin_calibers == pytest.approx(
        expected, abs=2e-15
    )


def test_uses_cp_cg_and_dmax_authorities_not_reference_length(geometry):
    """Dmax denominator authority'dir; numerik eşit reference_length tüketilmez."""
    static = static_properties(.7)
    mass = RocketMassProperties(2., .65)
    baseline = CALCULATOR.evaluate(
        static_aerodynamics=static, rocket_mass_properties=mass, geometry=geometry
    )
    changed_reference = CALCULATOR.evaluate(
        static_aerodynamics=static,
        rocket_mass_properties=mass,
        geometry=replace(geometry, reference_length_m=float("nan")),
    )
    changed_dmax = CALCULATOR.evaluate(
        static_aerodynamics=static,
        rocket_mass_properties=mass,
        geometry=replace(geometry, max_external_airframe_diameter_m=.2),
    )
    assert baseline == changed_reference
    assert changed_dmax.static_margin_calibers == baseline.static_margin_calibers / 2


@pytest.mark.parametrize(
    "mach,expected_cp,expected_margin",
    [
        (0., .6971650449840304, .471650449840304),
        (.75, .707290109576936, .5729010957693603),
    ],
)
def test_frozen_static_aero_and_margin_vv(mach, expected_cp, expected_margin, geometry):
    """Gerçek Part-1 CP sonucu, bağımsız Mass CG ve Geometry Dmax ile birleşir."""
    static = evaluated_static(mach, geometry)
    mass = RocketMassProperties(1., .65)
    result = CALCULATOR.evaluate(
        static_aerodynamics=static,
        rocket_mass_properties=mass,
        geometry=geometry,
    )
    assert static.cp_x_geo_m == pytest.approx(expected_cp, rel=4e-15)
    assert result.static_margin_calibers == pytest.approx(expected_margin, rel=5e-15)


def test_multiple_mass_snapshots_change_margin_without_changing_static_cp(geometry):
    static = evaluated_static(.75, geometry)
    before = repr(static)
    masses = [RocketMassProperties(1., cg) for cg in (.60, .65, .70)]
    margins = [
        CALCULATOR.evaluate(
            static_aerodynamics=static, rocket_mass_properties=mass, geometry=geometry
        ).static_margin_calibers
        for mass in masses
    ]
    assert margins[0] > margins[1] > margins[2]
    assert static.cp_x_geo_m == pytest.approx(.707290109576936, rel=4e-15)
    assert repr(static) == before


@pytest.mark.parametrize(
    "target,value",
    [
        ("cp", float("nan")), ("cp", float("inf")),
        ("cg", float("nan")), ("cg", -float("inf")),
        ("dmax", float("nan")), ("dmax", float("inf")),
    ],
)
def test_nonfinite_raw_values_use_generic_value_error(target, value, geometry):
    static = static_properties(value if target == "cp" else .7)
    mass = RocketMassProperties(1., value if target == "cg" else .65)
    selected_geometry = replace(
        geometry,
        max_external_airframe_diameter_m=value if target == "dmax" else .1,
    )
    with pytest.raises(ValueError) as caught:
        CALCULATOR.evaluate(
            static_aerodynamics=static,
            rocket_mass_properties=mass,
            geometry=selected_geometry,
        )
    assert type(caught.value) is ValueError


@pytest.mark.parametrize("diameter", [0., -1.])
def test_finite_invalid_dmax_uses_structured_aero_error(diameter, geometry):
    with pytest.raises(AerodynamicEvaluationError) as caught:
        CALCULATOR.evaluate(
            static_aerodynamics=static_properties(.7),
            rocket_mass_properties=RocketMassProperties(1., .65),
            geometry=replace(geometry, max_external_airframe_diameter_m=diameter),
        )
    assert (
        caught.value.error_code, caught.value.field_name, caught.value.value
    ) == (
        "NON_POSITIVE_STATIC_MARGIN_DIAMETER",
        "max_external_airframe_diameter_m",
        diameter,
    )


def test_nonfinite_derived_margin_fails_without_repair(geometry):
    with pytest.raises(AerodynamicEvaluationError) as caught:
        calculate(1e308, -1e308, geometry)
    assert caught.value.error_code == "INVALID_STATIC_MARGIN"
    assert caught.value.field_name == "static_margin_calibers"


def test_determinism_no_mutation_and_no_scope_leakage(geometry):
    static = evaluated_static(.75, geometry)
    mass = RocketMassProperties(1., .65)
    before = (repr(static), repr(mass), repr(geometry))
    expected = CALCULATOR.evaluate(
        static_aerodynamics=static, rocket_mass_properties=mass, geometry=geometry
    )
    for _ in range(5):
        assert CALCULATOR.evaluate(
            static_aerodynamics=static,
            rocket_mass_properties=mass,
            geometry=geometry,
        ) == expected
    assert (repr(static), repr(mass), repr(geometry)) == before
    forbidden = {
        "cp_x_geo_m", "total_cg_x_geo_m", "safe", "unsafe", "force", "moment",
        "motor_time_s", "angle_of_attack",
    }
    assert not {field.name for field in fields(StaticMarginResult)}.intersection(forbidden)
