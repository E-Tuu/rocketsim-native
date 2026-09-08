"""NAT-012B checkpoint 1: linearized Extended-Barrowman CNa/CP V&V."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature
from math import cos, pi, sqrt

import pytest

from roketsim_native.aerodynamics.drag import AerodynamicEvaluationError
from roketsim_native.aerodynamics import static_stability
from roketsim_native.aerodynamics.static_stability import (
    BodyLiftTreatment,
    FinCenterOfPressureModel,
    NATIVE_STATIC_STABILITY_V1_PROFILE,
    StaticAeroContribution,
    StaticAerodynamicEvaluator,
    StaticAerodynamicProperties,
    StaticNormalForceModel,
    StaticStabilityModelProfile,
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


EVALUATOR = StaticAerodynamicEvaluator()


def make_source(*, fin_count=4, arrangement=FinAngularArrangement.EQUALLY_SPACED):
    return SingleStageRocketGeometry(
        0.1,
        ConicalNoseGeometry(0.3, NoseConstructionMode.HOLLOW_SHELL, 0.002),
        CylindricalBodyGeometry(0.7, 0.002),
        TrapezoidalFinSetGeometry(
            fin_count, 0.18, 0.08, 0.12, 0.05, 0.72, 0.003,
            FinCrossSection.SQUARE, arrangement,
        ),
        MotorAttachmentGeometry(
            MotorMountTubeGeometry(0.12, 0.029, 0.001, 0.0),
            CenteringRingPairGeometry(0.003),
            0.005,
        ),
        ReferenceGeometryPolicy.MAXIMUM_DIAMETER,
    )


def resolve_source(**kwargs):
    return GeometryResolver().resolve(rocket_geometry=make_source(**kwargs))


def conditions(mach):
    return BasicFlightConditions(100.0, mach, 0.0, 0.0)


def evaluate(mach, *, geometry=None, profile=NATIVE_STATIC_STABILITY_V1_PROFILE):
    return EVALUATOR.evaluate(
        geometry=geometry or resolve_source(),
        flight_conditions=conditions(mach),
        model_profile=profile,
    )


def single_fin_cna(geometry, mach):
    span = geometry.source.fins.semi_span_m
    area = geometry.fin_planform_area_per_fin_m2
    return (
        2 * pi * span**2 / geometry.reference_area_m2
        / (1 + sqrt(1 + (1-mach**2) * (span**2/(area*cos(geometry.fin_midchord_sweep_angle_rad)))**2))
    )


def interference_factor(n):
    return {5: .948, 6: .913, 7: .854, 8: .810}.get(n, 1.0 if n <= 4 else .750)


def test_fin_arrangement_is_mandatory_and_geometry_owned():
    """EQUALLY_SPACED tek enum değeridir; raw fin field mandatory'dir."""
    assert {x.name: x.value for x in FinAngularArrangement} == {
        "EQUALLY_SPACED": "equally_spaced"
    }
    parameter = signature(TrapezoidalFinSetGeometry).parameters["angular_arrangement"]
    assert parameter.default is Parameter.empty
    geometry = resolve_source()
    assert geometry.fin_angular_arrangement is FinAngularArrangement.EQUALLY_SPACED
    assert not hasattr(geometry.source.fins, "fin_azimuths")


def test_geometry_descriptor_vv_and_a0_a1_preservation():
    """Mid-chord sweep LE sweep değildir; accepted alan/uzunluklar değişmez."""
    geometry = resolve_source()
    assert geometry.nose_axial_length_m == .3
    assert geometry.fin_midchord_sweep_angle_rad == pytest.approx(0., abs=1e-15)
    assert geometry.fin_leading_edge_sweep_angle_rad == pytest.approx(.39479111969976155)
    assert geometry.fin_mean_aerodynamic_chord_spanwise_location_m == pytest.approx(.0523076923076923)
    assert geometry.fin_mean_aerodynamic_chord_leading_edge_x_geo_m == pytest.approx(.7417948717948718)
    assert geometry.fin_aspect_ratio == pytest.approx(1.8461538461538463)
    assert geometry.fin_body_radius_at_root_m == .05
    assert geometry.reference_area_m2 == pytest.approx(pi*.1**2/4)
    assert geometry.aerodynamic_length_m == geometry.axisymmetric_body_length_m == 1.
    assert geometry.nose_material_volume_m3 == pytest.approx(9.172555380948023e-5)


def test_profile_and_result_contracts_are_explicit_immutable():
    """Profil default değildir; result yalnız nose/fin/total CNa-CP taşır."""
    profile = NATIVE_STATIC_STABILITY_V1_PROFILE
    assert profile == StaticStabilityModelProfile(
        StaticNormalForceModel.EXTENDED_BARROWMAN_SUBSONIC,
        FinCenterOfPressureModel.EXTENDED_BARROWMAN_MACH_DEPENDENT,
        BodyLiftTreatment.LINEARIZED_ZERO_AOA,
        .8,
    )
    assert "gamma" not in {f.name for f in fields(profile)}
    result = evaluate(0.)
    assert [f.name for f in fields(StaticAeroContribution)] == ["cna_per_rad", "cp_x_geo_m"]
    assert [f.name for f in fields(StaticAerodynamicProperties)] == [
        "nose", "fin_set", "total_cna_per_rad", "cp_x_geo_m", "model_profile"
    ]
    for item in (profile, result.nose, result):
        assert not hasattr(item, "__dict__")
        with pytest.raises(FrozenInstanceError):
            setattr(item, fields(item)[0].name, None)
    params = signature(EVALUATOR.evaluate).parameters
    assert tuple(params) == ("geometry", "flight_conditions", "model_profile")
    assert all(p.kind is Parameter.KEYWORD_ONLY and p.default is Parameter.empty for p in params.values())
    with pytest.raises(TypeError):
        EVALUATOR.evaluate(geometry=resolve_source(), flight_conditions=conditions(0.))


@pytest.mark.parametrize("mach", [0., .25, .5, .500001, .75, .799999999])
def test_runtime_mach_domain_is_strictly_below_point_eight(mach):
    assert evaluate(mach).total_cna_per_rad > 0.


@pytest.mark.parametrize("mach", [.8, .81, 1., 2.])
def test_transonic_and_supersonic_mach_are_explicitly_unsupported(mach):
    with pytest.raises(AerodynamicEvaluationError) as caught:
        evaluate(mach)
    assert caught.value.error_code == "UNSUPPORTED_STATIC_STABILITY_MACH_REGIME"
    assert caught.value.field_name == "mach"
    assert caught.value.value == mach


@pytest.mark.parametrize("mach", [float("nan"), float("inf"), -float("inf")])
def test_nonfinite_raw_mach_uses_generic_value_error(mach):
    with pytest.raises(ValueError) as caught:
        evaluate(mach)
    assert type(caught.value) is ValueError


def test_nose_area_cna_and_conical_cp_ignore_construction_volume():
    """Nose CNa area ratio, CP 2L/3; structural shell volume/centroid tüketilmez."""
    geometry = resolve_source()
    baseline = evaluate(0., geometry=geometry)
    half_frontal = evaluate(0., geometry=replace(
        geometry, nose_frontal_area_m2=geometry.nose_frontal_area_m2/2
    ))
    altered_construction = evaluate(0., geometry=replace(
        geometry, nose_material_volume_m3=float("nan"),
        nose_volume_centroid_x_geo_m=float("nan"),
    ))
    assert baseline.nose.cna_per_rad == 2.
    assert half_frontal.nose.cna_per_rad == 1.
    assert baseline.nose.cp_x_geo_m == pytest.approx(.2, rel=3e-15)
    assert altered_construction.nose == baseline.nose


@pytest.mark.parametrize(
    "mach,single_expected,fin_expected,total_expected,fin_cp_expected,total_cp_expected",
    [
        (0., 4.879478253472297, 12.629237832516534, 14.629237832516534,
         .7758974358974359, .6971650449840304),
        (.75, 5.304704137592981, 13.729822473770069, 15.729822473770069,
         .78118620117551, .707290109576936),
    ],
)
def test_frozen_static_aero_vv(
    mach, single_expected, fin_expected, total_expected, fin_cp_expected, total_cp_expected
):
    geometry = resolve_source()
    result = evaluate(mach, geometry=geometry)
    assert single_fin_cna(geometry, mach) == pytest.approx(single_expected, rel=4e-15)
    assert result.fin_set.cna_per_rad == pytest.approx(fin_expected, rel=4e-15)
    assert result.total_cna_per_rad == pytest.approx(total_expected, rel=4e-15)
    assert result.fin_set.cp_x_geo_m == pytest.approx(fin_cp_expected, rel=4e-15)
    assert result.cp_x_geo_m == pytest.approx(total_cp_expected, rel=4e-15)


@pytest.mark.parametrize("fin_count", [3, 4, 5, 6, 7, 8, 9, 12])
def test_equal_fin_aggregation_full_eq_354_table(fin_count):
    """N/2 Eq.3.53 ve Ntot-indexed tam Eq.3.54 tablosu davranışsal doğrulanır."""
    geometry = resolve_source(fin_count=fin_count)
    result = evaluate(0., geometry=geometry)
    one = single_fin_cna(geometry, 0.)
    body_to_fin = 1 + .05/(.12+.05)
    expected = one * (fin_count/2) * interference_factor(fin_count) * body_to_fin
    assert result.fin_set.cna_per_rad == pytest.approx(expected, rel=4e-15)


@pytest.mark.parametrize(
    "ntot, expected",
    [(1, 1.), (2, 1.), (3, 1.), (4, 1.), (5, .948), (6, .913),
     (7, .854), (8, .810), (9, .750), (20, .750)],
)
def test_complete_eq_354_ntot_source_factor_table(ntot, expected):
    """N=1/2 V1 layout değildir; yine de Eq.3.54 Ntot tablosu tam doğrulanır."""
    assert EVALUATOR._fin_fin_interference_factor(ntot) == expected


@pytest.mark.parametrize("fin_count", [1, 2])
def test_one_or_two_fin_directional_case_is_unsupported(fin_count):
    """Resolver domain bypass edilse bile evaluator N/2'yi N=1/2'ye uygulamaz."""
    geometry = resolve_source()
    bad_source = replace(
        geometry.source, fins=replace(geometry.source.fins, fin_count=fin_count)
    )
    with pytest.raises(AerodynamicEvaluationError) as caught:
        evaluate(0., geometry=replace(geometry, source=bad_source))
    assert caught.value.error_code == "UNSUPPORTED_FIN_COUNT"


def test_n_and_ntot_remain_distinct_concepts_in_single_set_v1():
    """Raw shape yalnız current-set N taşır; Ntot ileride configuration çözümüdür."""
    names = {f.name for f in fields(TrapezoidalFinSetGeometry)}
    assert "fin_count" in names
    assert not names.intersection({"ntot", "total_interfering_parallel_fins"})
    assert evaluate(0.).fin_set.cna_per_rad > 0.


@pytest.mark.parametrize("mach", [0., .25, .5])
def test_fin_cp_quarter_chord_through_half_mach(mach):
    geometry = resolve_source()
    fraction = (
        evaluate(mach, geometry=geometry).fin_set.cp_x_geo_m
        - geometry.fin_mean_aerodynamic_chord_leading_edge_x_geo_m
    ) / geometry.fin_mean_aerodynamic_chord_m
    assert fraction == pytest.approx(.25, rel=0., abs=2e-15)


def test_quintic_boundary_conditions_and_point_75_fraction():
    """Altı OR13 koşulundan exact algebraic quintic; M=2 yalnız construction endpoint."""
    ar = resolve_source().fin_aspect_ratio
    root_three = sqrt(3.)
    denominator = 2*ar*root_three-1
    endpoint = (ar*root_three-.67)/denominator
    endpoint_derivative = .34*ar*2/(root_three*denominator**2)
    delta = endpoint-.25
    scaled = 1.5*endpoint_derivative
    coefficients = (
        10*delta-6*scaled,
        -20*delta+14*scaled,
        15*delta-11*scaled,
        -4*delta+3*scaled,
    )
    a, b, c, d = coefficients
    assert .25 + a+b+c+d == pytest.approx(endpoint, rel=3e-15)
    assert (2*a+3*b+4*c+5*d)/1.5 == pytest.approx(endpoint_derivative, rel=2e-14)
    assert 2*a+6*b+12*c+20*d == pytest.approx(0., abs=2e-14)
    assert 6*b+24*c+60*d == pytest.approx(0., abs=3e-14)
    geometry = resolve_source()
    fraction = (
        evaluate(.75).fin_set.cp_x_geo_m
        - geometry.fin_mean_aerodynamic_chord_leading_edge_x_geo_m
    ) / geometry.fin_mean_aerodynamic_chord_m
    assert fraction == pytest.approx(.2887710236550541, rel=4e-15)


def test_total_is_exact_cna_sum_and_weighted_cp():
    result = evaluate(.75)
    assert result.total_cna_per_rad == result.nose.cna_per_rad + result.fin_set.cna_per_rad
    assert result.cp_x_geo_m == pytest.approx(
        (result.nose.cna_per_rad*result.nose.cp_x_geo_m
         + result.fin_set.cna_per_rad*result.fin_set.cp_x_geo_m)
        / result.total_cna_per_rad,
        rel=4e-15,
    )


def test_body_linear_term_is_zero_without_fake_output_or_galejs():
    """Continuous cylinder alpha->0 contribution is zero; finite-AoA Galejs yoktur."""
    result_names = {f.name for f in fields(StaticAerodynamicProperties)}
    assert "body" not in result_names
    assert "body_cna_per_rad" not in result_names
    assert "angle_of_attack" not in signature(EVALUATOR.evaluate).parameters


def test_determinism_no_mutation_and_public_scope():
    geometry = resolve_source()
    flight = conditions(.75)
    before = (repr(geometry), repr(flight))
    expected = EVALUATOR.evaluate(
        geometry=geometry, flight_conditions=flight,
        model_profile=NATIVE_STATIC_STABILITY_V1_PROFILE,
    )
    for _ in range(5):
        assert evaluate(.75, geometry=geometry) == expected
    assert (repr(geometry), repr(flight)) == before
    names = set(dir(StaticAerodynamicProperties)) | set(signature(EVALUATOR.evaluate).parameters)
    assert not names.intersection({
        "static_margin", "cg_x_geo_m", "normal_force", "aerodynamic_moment",
        "angle_of_attack", "alpha", "stall",
    })
    assert set(static_stability.__all__) == {
        "StaticNormalForceModel", "FinCenterOfPressureModel", "BodyLiftTreatment",
        "StaticStabilityModelProfile", "NATIVE_STATIC_STABILITY_V1_PROFILE",
        "StaticAeroContribution", "StaticAerodynamicProperties", "StaticAerodynamicEvaluator",
    }
