"""NAT-012A.1: Native Basic Drag V1 seçici, denklem ve kapsam V&V."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature
from math import cos, log, pi, sin

import pytest

from roketsim_native.aerodynamics import drag
from roketsim_native.aerodynamics.drag import (
    AerodynamicEvaluationError,
    BaseDragModel,
    BasicDragEvaluator,
    BasicDragModelProfile,
    BasicDragResult,
    BoundaryLayerModel,
    FinEdgeModel,
    LowReynoldsContinuation,
    NATIVE_BASIC_DRAG_V1_PROFILE,
    NosePressureModel,
    PlumeTreatment,
    ReynoldsLengthPolicy,
    SkinFrictionBranch,
    SkinFrictionEvaluation,
)
from roketsim_native.aerodynamics.surfaces import (
    SingleStageRocketAerodynamicSurfaces,
    SurfaceFinish,
)
from roketsim_native.environment.air_properties import (
    DRY_AIR_SPECIFIC_HEAT_RATIO,
    DryAirProperties,
    DryAirPropertiesCalculator,
)
from roketsim_native.environment.atmosphere import DryAirAtmosphereState
from roketsim_native.flight_conditions.basic import BasicFlightConditions
from roketsim_native.geometry.models import (
    CenteringRingPairGeometry,
    ConicalNoseGeometry,
    CylindricalBodyGeometry,
    FinCrossSection,
    MotorAttachmentGeometry,
    MotorMountTubeGeometry,
    NoseConstructionMode,
    ReferenceGeometryPolicy,
    SingleStageRocketGeometry,
    TrapezoidalFinSetGeometry,
)
from roketsim_native.geometry.resolver import GeometryResolver


EVALUATOR = BasicDragEvaluator()


def make_geometry(*, fin_offset_m: float = 0.05, fin_tip_chord_m: float = 0.08):
    source = SingleStageRocketGeometry(
        0.1,
        ConicalNoseGeometry(0.3, NoseConstructionMode.HOLLOW_SHELL, 0.002),
        CylindricalBodyGeometry(0.7, 0.002),
        TrapezoidalFinSetGeometry(
            4, 0.18, fin_tip_chord_m, 0.12, fin_offset_m, 0.72, 0.003,
            FinCrossSection.SQUARE,
        ),
        MotorAttachmentGeometry(
            MotorMountTubeGeometry(0.12, 0.029, 0.001, 0.0),
            CenteringRingPairGeometry(0.003),
            0.005,
        ),
        ReferenceGeometryPolicy.MAXIMUM_DIAMETER,
    )
    return GeometryResolver().resolve(rocket_geometry=source)


def make_surfaces(nose=0.0, body=0.0, fins=0.0):
    return SingleStageRocketAerodynamicSurfaces(
        SurfaceFinish("Synthetic nose", nose),
        SurfaceFinish("Synthetic body", body),
        SurfaceFinish("Synthetic fins", fins),
    )


def make_inputs(*, airspeed=100.0, mach=0.3, nu=1.5e-5, gamma=1.4, surfaces=None):
    return {
        "resolved_geometry": make_geometry(),
        "aerodynamic_surfaces": surfaces or make_surfaces(),
        "flight_conditions": BasicFlightConditions(airspeed, mach, 123.0, 456.0),
        "air_properties": DryAirProperties(340.0, 1.8e-5, nu, gamma),
        "model_profile": NATIVE_BASIC_DRAG_V1_PROFILE,
    }


def evaluate(**overrides):
    inputs = make_inputs()
    inputs.update(overrides)
    return EVALUATOR.evaluate(**inputs)


def test_aero_geometry_length_and_diameter_semantics():
    """Dmax/body/aero/reference uzunlukları ayrı authority anlamlarını korur."""
    normal = make_geometry()
    overhang_source = replace(
        normal.source,
        fins=replace(
            normal.source.fins,
            root_leading_edge_x_geo_m=0.82,
            tip_leading_edge_offset_x_m=0.10,
            tip_chord_m=0.12,
        ),
    )
    overhang = GeometryResolver().resolve(rocket_geometry=overhang_source)
    assert normal.reference_length_m == 0.1
    assert normal.max_external_airframe_diameter_m == 0.1
    assert normal.axisymmetric_body_length_m == 1.0
    assert normal.aerodynamic_length_m == 1.0
    assert overhang.reference_length_m == 0.1
    assert overhang.max_external_airframe_diameter_m == 0.1
    assert overhang.axisymmetric_body_length_m == 1.0
    assert overhang.aerodynamic_length_m == pytest.approx(1.04, rel=3e-15)


def test_gamma_is_air_properties_authority():
    """Gamma aerodynamics profile'ında değil NAT-009D snapshot'ındadır."""
    properties = DryAirPropertiesCalculator().evaluate(
        atmosphere_state=DryAirAtmosphereState(288.15, 101325.0, 1.225)
    )
    assert properties.specific_heat_ratio == DRY_AIR_SPECIFIC_HEAT_RATIO == 1.4
    assert "specific_heat_ratio" not in {field.name for field in fields(BasicDragModelProfile)}
    low_gamma = evaluate(air_properties=replace(make_inputs()["air_properties"], specific_heat_ratio=1.2))
    high_gamma = evaluate(air_properties=replace(make_inputs()["air_properties"], specific_heat_ratio=1.67))
    assert low_gamma.nose_pressure_cd != high_gamma.nose_pressure_cd


def test_profile_is_explicit_frozen_policy():
    """Profil evaluator'a mandatory gelir; 1e4 görünür model politikasıdır."""
    profile = NATIVE_BASIC_DRAG_V1_PROFILE
    assert profile == BasicDragModelProfile(
        BoundaryLayerModel.FULLY_TURBULENT,
        ReynoldsLengthPolicy.AERODYNAMIC_LENGTH,
        LowReynoldsContinuation.MINIMUM_EVALUATION_REYNOLDS,
        1.0e4,
        NosePressureModel.TD13_CONICAL_SUBSONIC_TO_SONIC,
        FinEdgeModel.TD13_SQUARE,
        BaseDragModel.TD13_SUBSONIC_TO_SONIC,
        PlumeTreatment.IGNORED,
    )
    assert not hasattr(profile, "__dict__")
    with pytest.raises(FrozenInstanceError):
        profile.minimum_friction_evaluation_reynolds = 1.0
    parameters = signature(EVALUATOR.evaluate).parameters
    assert tuple(parameters) == (
        "resolved_geometry", "aerodynamic_surfaces", "flight_conditions",
        "air_properties", "model_profile",
    )
    assert all(
        p.kind is Parameter.KEYWORD_ONLY and p.default is Parameter.empty
        for p in parameters.values()
    )
    with pytest.raises(TypeError):
        EVALUATOR.evaluate(**{k: v for k, v in make_inputs().items() if k != "model_profile"})


def test_result_contract_and_grouped_properties():
    """Yalnız yedi physical term saklanır; grouped değerler derived property'dir."""
    result = evaluate()
    assert [f.name for f in fields(BasicDragResult)] == [
        "reynolds_number", "friction_evaluation_reynolds_number",
        "nose_skin_friction", "body_skin_friction", "fin_skin_friction",
        "nose_friction_cd", "body_friction_cd", "fin_friction_cd",
        "nose_pressure_cd", "fin_leading_edge_pressure_cd",
        "fin_trailing_edge_base_cd", "airframe_base_cd", "total_cd0",
        "model_profile",
    ]
    assert not hasattr(result, "__dict__")
    with pytest.raises(FrozenInstanceError):
        result.total_cd0 = 0.0
    assert result.total_cd0 == pytest.approx(
        result.friction_cd + result.pressure_cd + result.base_cd, rel=3e-15
    )


def test_reynolds_and_explicit_low_re_policy():
    """Actual Re korunur; yalnız Cf evaluation Re'si açık minimuma devam eder."""
    nominal = evaluate()
    assert nominal.reynolds_number == pytest.approx(100.0 / 1.5e-5, rel=2e-15)
    assert nominal.friction_evaluation_reynolds_number == nominal.reynolds_number
    stopped = evaluate(flight_conditions=BasicFlightConditions(0.0, 0.0, 0.0, 0.0))
    assert stopped.reynolds_number == 0.0
    assert stopped.friction_evaluation_reynolds_number == 1.0e4


def test_smooth_turbulent_skin_friction_equation():
    """Historical 0.0148 shortcut yerine log correlation ve Mach correction."""
    result = evaluate()
    expected_smooth = 1.0 / (1.50 * log(100.0 / 1.5e-5) - 5.6) ** 2
    corrected = expected_smooth * (1.0 - 0.10 * 0.3**2)
    for skin in (result.nose_skin_friction, result.body_skin_friction, result.fin_skin_friction):
        assert skin.smooth_corrected_cf == pytest.approx(corrected, rel=3e-15)
        assert skin.roughness_corrected_cf == 0.0
        assert skin.selected_cf == skin.smooth_corrected_cf
        assert skin.selected_branch is SkinFrictionBranch.SMOOTH


def test_independent_roughness_branch_selection():
    """Nose/body/fins aynı material'dan tahmin edilmeden bağımsız seçilir."""
    surfaces = make_surfaces(nose=0.0, body=1e-3, fins=1e-10)
    result = evaluate(aerodynamic_surfaces=surfaces)
    assert result.nose_skin_friction.selected_branch is SkinFrictionBranch.SMOOTH
    assert result.body_skin_friction.selected_branch is SkinFrictionBranch.ROUGHNESS_LIMITED
    assert result.fin_skin_friction.selected_branch is SkinFrictionBranch.SMOOTH
    expected = 0.032 * (1e-3 / 1.0) ** 0.2 * (1.0 - 0.10 * 0.3**2)
    assert result.body_skin_friction.roughness_corrected_cf == pytest.approx(expected, rel=3e-15)
    assert result.body_skin_friction.selected_cf == result.body_skin_friction.roughness_corrected_cf


@pytest.mark.parametrize(
    "field, expected",
    [
        ("nose_friction_cd", 0.019602822950748604),
        ("body_friction_cd", 0.09023515876002),
        ("fin_friction_cd", 0.05091519406760164),
        ("nose_pressure_cd", 0.021621978661408206),
        ("fin_leading_edge_pressure_cd", 0.13580527568987105),
        ("fin_trailing_edge_base_cd", 0.024146733317993406),
        ("airframe_base_cd", 0.1317),
        ("total_cd0", 0.4740271634476429),
    ],
)
def test_frozen_smooth_vv(field, expected):
    """Yedi katkı ve exact toplam sentetik equation-derived ankrajlarla eşleşir."""
    assert getattr(evaluate(), field) == pytest.approx(expected, rel=5e-15, abs=0.0)


def test_body_and_fin_form_corrections_are_diameter_and_mac_based():
    """K_B çapı (radius bug değil), K_F fin MAC'ı kullanır."""
    result = evaluate()
    geometry = make_geometry()
    cf = result.nose_skin_friction.selected_cf
    k_body = result.nose_friction_cd * geometry.reference_area_m2 / (
        cf * geometry.nose_wetted_area_m2
    )
    k_fin = result.fin_friction_cd * geometry.reference_area_m2 / (
        result.fin_skin_friction.selected_cf
        * 2.0 * geometry.source.fins.fin_count * geometry.fin_planform_area_per_fin_m2
    )
    assert k_body == pytest.approx(1.05, rel=3e-15)
    assert k_fin == pytest.approx(1.0439849624060151, rel=3e-15)


def test_pressure_and_base_equations_are_distinct():
    """Nose/LE pressure ile square TE/airframe base ayrı taxonomidir."""
    result = evaluate()
    geometry = make_geometry()
    mach = 0.3
    phi = geometry.nose_half_angle_rad
    c0, c1 = 0.8 * sin(phi) ** 2, sin(phi)
    d1 = 4.0 / 2.4 * (1.0 - c1 / 2.0)
    expected_nose = (c0 + (c1 - c0) * mach ** (d1 / (c1 - c0)))
    expected_nose *= geometry.nose_frontal_area_m2 / geometry.reference_area_m2
    c_stag = 0.85 * (1 + mach**2 / 4 + mach**4 / 40)
    edge = 4 * 0.003 * 0.12 / geometry.reference_area_m2
    assert result.nose_pressure_cd == pytest.approx(expected_nose, rel=3e-15)
    assert result.fin_leading_edge_pressure_cd == pytest.approx(
        edge * c_stag * cos(geometry.fin_leading_edge_sweep_angle_rad) ** 2,
        rel=3e-15,
    )
    base_star = 0.12 + 0.13 * mach**2
    assert result.fin_trailing_edge_base_cd == pytest.approx(edge * base_star, rel=3e-15)
    assert result.airframe_base_cd == pytest.approx(base_star, rel=3e-15)


@pytest.mark.parametrize("mach", [0.0, 1.0])
def test_supported_mach_boundaries(mach):
    """Subsonic-to-sonic frozen domain iki kapalı ucu kabul eder."""
    result = evaluate(flight_conditions=BasicFlightConditions(100.0, mach, 0.0, 0.0))
    assert result.total_cd0 >= 0.0


def test_unsupported_mach_is_structured_without_clamp():
    with pytest.raises(AerodynamicEvaluationError) as caught:
        evaluate(flight_conditions=BasicFlightConditions(100.0, 1.0001, 0.0, 0.0))
    assert (
        caught.value.error_code, caught.value.field_name, caught.value.value
    ) == ("UNSUPPORTED_MACH_REGIME", "mach", 1.0001)


@pytest.mark.parametrize("value", [float("nan"), float("inf"), -float("inf")])
def test_nonfinite_raw_numerics_use_generic_value_error(value):
    with pytest.raises(ValueError) as caught:
        evaluate(flight_conditions=BasicFlightConditions(100.0, value, 0.0, 0.0))
    assert type(caught.value) is ValueError


def test_exact_seven_term_aggregation_and_plume_ignored():
    """Base alanı motor/mount/nozzle ile azaltılmaz; yalnız yedi terim toplanır."""
    result = evaluate()
    terms = [
        result.nose_friction_cd,
        result.body_friction_cd,
        result.fin_friction_cd,
        result.nose_pressure_cd,
        result.fin_leading_edge_pressure_cd,
        result.fin_trailing_edge_base_cd,
        result.airframe_base_cd,
    ]
    assert result.total_cd0 == sum(terms)
    assert result.model_profile.plume_treatment is PlumeTreatment.IGNORED
    assert result.airframe_base_cd == pytest.approx(0.12 + 0.13 * 0.3**2, rel=3e-15)


def test_determinism_and_no_mutation():
    inputs = make_inputs(surfaces=make_surfaces(0.0, 1e-3, 1e-10))
    before = tuple(repr(inputs[name]) for name in inputs)
    expected = EVALUATOR.evaluate(**inputs)
    for _ in range(5):
        assert EVALUATOR.evaluate(**inputs) == expected
        assert BasicDragEvaluator().evaluate(**inputs) == expected
    assert tuple(repr(inputs[name]) for name in inputs) == before


def test_public_scope_has_no_later_aerodynamics_or_force():
    names = {field.name for field in fields(BasicDragResult)}
    forbidden = {
        "drag_force", "drag_force_N", "angle_of_attack", "aoa", "cp", "cna",
        "static_margin", "lift", "thrust", "dynamic_pressure_Pa",
    }
    assert not names.intersection(forbidden)
    assert {name for name in dir(BasicDragEvaluator) if not name.startswith("_")} == {"evaluate"}
    assert "ROUNDED" not in FinCrossSection.__members__
    assert "AIRFOIL" not in FinCrossSection.__members__
    assert set(drag.__all__) >= {
        "BasicDragEvaluator", "BasicDragResult", "BasicDragModelProfile",
        "SkinFrictionEvaluation", "AerodynamicEvaluationError",
    }
