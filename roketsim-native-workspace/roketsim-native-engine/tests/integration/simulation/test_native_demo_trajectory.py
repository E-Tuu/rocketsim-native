"""NAT-022 gerçek accepted Native 3DOF zinciri end-to-end V&V gate testleri."""

from dataclasses import dataclass

import numpy as np
import pytest

from roketsim_native.aerodynamics.drag import NATIVE_BASIC_DRAG_V1_PROFILE
from roketsim_native.aerodynamics.surfaces import (
    SingleStageRocketAerodynamicSurfaces,
    SurfaceFinish,
)
from roketsim_native.dynamics.initial_state import LaunchConditions3DOF
from roketsim_native.dynamics.translational import (
    NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
)
from roketsim_native.environment.air_properties import DryAirPropertiesCalculator
from roketsim_native.environment.atmosphere import USStandardAtmosphere1976Lower
from roketsim_native.environment.gravity import ConstantGravityModel
from roketsim_native.environment.wind import NoWindModel
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
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.materials.models import SingleStageRocketMaterials
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator
from roketsim_native.numerics.fixed_step import FixedStepConfig
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T
from roketsim_native.propulsion.installation import MotorInstallationResolver
from roketsim_native.propulsion.properties import DEMO_MOTOR_PROPERTY_MODEL_PROFILE
from roketsim_native.propulsion.thrust import MotorCurveAnalyzer
from roketsim_native.simulation.engine import (
    SimulationEngine3DOF,
    SimulationRunConfiguration3DOF,
    SimulationRunLimits3DOF,
    SimulationTerminationReason,
    TerminalEventHandlingModel,
)
from roketsim_native.simulation.events import (
    ApogeeDetectionModel,
    BurnoutDetectionModel,
    EventLocalizationModel,
    FlightEventDetectionProfile3DOF,
    FlightEventType,
    GroundReferenceModel,
)
from roketsim_native.simulation.physics import (
    EnvironmentPositionMappingModel,
    PhysicsEvaluationContext3DOF,
    PropulsionTimeline,
)
from roketsim_native.simulation.result import SimulationResult3DOF


# [V&V FIXTURE — NOT A PRODUCTION DEFAULT]
PRIMARY_STEP_SIZE_S = 0.010
PRIMARY_MAXIMUM_STEPS = 5000
FINE_STEP_SIZE_S = 0.005
FINE_MAXIMUM_STEPS = 10000
LAUNCH_ENVIRONMENT_ALTITUDE_M = 1200.0
IGNITION_TIME_S = 0.0


@dataclass(frozen=True, slots=True)
class DemoRun:
    """Test-only configuration/result çifti; production API değildir."""

    configuration: SimulationRunConfiguration3DOF
    result: SimulationResult3DOF


def accepted_demo_dependencies():
    """NAT-011/012/015 accepted fixture girdilerini ikinci authority kurmadan bağla."""

    source_geometry = SingleStageRocketGeometry(
        0.1,
        ConicalNoseGeometry(0.3, NoseConstructionMode.HOLLOW_SHELL, 0.002),
        CylindricalBodyGeometry(0.7, 0.002),
        TrapezoidalFinSetGeometry(
            4,
            0.18,
            0.08,
            0.12,
            0.05,
            0.72,
            0.003,
            FinCrossSection.SQUARE,
            FinAngularArrangement.EQUALLY_SPACED,
        ),
        MotorAttachmentGeometry(
            MotorMountTubeGeometry(0.12, 0.029, 0.001, 0.0),
            CenteringRingPairGeometry(0.003),
            0.005,
        ),
        ReferenceGeometryPolicy.MAXIMUM_DIAMETER,
    )
    geometry = GeometryResolver().resolve(rocket_geometry=source_geometry)
    materials = SingleStageRocketMaterials(
        POLYSTYRENE,
        CARDBOARD,
        CARDBOARD,
        CARDBOARD,
        CARDBOARD,
    )
    structure = StructuralMassPropertiesCalculator().evaluate(
        resolved_geometry=geometry,
        materials=materials,
    )
    installation = MotorInstallationResolver().resolve(
        resolved_geometry=geometry,
        motor=AEROTECH_F50_4T,
    )
    smooth = SurfaceFinish("NAT-022 synthetic smooth V&V surface", 0.0)
    surfaces = SingleStageRocketAerodynamicSurfaces(smooth, smooth, smooth)
    statistics = MotorCurveAnalyzer().analyze(motor=AEROTECH_F50_4T)
    return geometry, structure, installation, surfaces, statistics


def demo_configuration(*, step_size_s, maximum_steps):
    """Bütün policy/fixture değerleri explicit olan real production-chain config."""

    geometry, structure, installation, surfaces, statistics = (
        accepted_demo_dependencies()
    )
    launch_conditions = LaunchConditions3DOF(
        initial_position_world_m=(0.0, 0.0, 0.0),
        initial_velocity_world_m_s=(0.0, 0.0, 0.0),
        launch_direction_world_unit=(0.0, 0.0, 1.0),
    )
    context = PhysicsEvaluationContext3DOF(
        resolved_geometry=geometry,
        aerodynamic_surfaces=surfaces,
        structural_mass_properties=structure,
        motor_installation=installation,
        motor_property_model_profile=DEMO_MOTOR_PROPERTY_MODEL_PROFILE,
        basic_drag_model_profile=NATIVE_BASIC_DRAG_V1_PROFILE,
        translational_dynamics_model_profile=(
            NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE
        ),
        environment_position_mapping_model=(
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET
        ),
        launch_environment_altitude_m=LAUNCH_ENVIRONMENT_ALTITUDE_M,
        propulsion_timeline=PropulsionTimeline(IGNITION_TIME_S),
        atmosphere_model=USStandardAtmosphere1976Lower(),
        air_properties_calculator=DryAirPropertiesCalculator(),
        gravity_model=ConstantGravityModel(),
        wind_model=NoWindModel(),
    )
    event_profile = FlightEventDetectionProfile3DOF(
        BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
        ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
        GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,
        EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION,
    )
    return SimulationRunConfiguration3DOF(
        launch_conditions=launch_conditions,
        physics_context=context,
        motor_curve_statistics=statistics,
        event_detection_profile=event_profile,
        fixed_step_config=FixedStepConfig(step_size_s),
        run_limits=SimulationRunLimits3DOF(maximum_steps),
        terminal_event_handling_model=(
            TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT
        ),
    )


def execute_demo(*, step_size_s, maximum_steps):
    configuration = demo_configuration(
        step_size_s=step_size_s,
        maximum_steps=maximum_steps,
    )
    execution = SimulationEngine3DOF().run(configuration=configuration)
    return DemoRun(configuration, SimulationResult3DOF(execution=execution))


@pytest.fixture(scope="module")
def demo_runs():
    """Coarse determinism repeat ve independent fine-step execution'ları."""

    coarse = execute_demo(
        step_size_s=PRIMARY_STEP_SIZE_S,
        maximum_steps=PRIMARY_MAXIMUM_STEPS,
    )
    repeated = execute_demo(
        step_size_s=PRIMARY_STEP_SIZE_S,
        maximum_steps=PRIMARY_MAXIMUM_STEPS,
    )
    fine = execute_demo(
        step_size_s=FINE_STEP_SIZE_S,
        maximum_steps=FINE_MAXIMUM_STEPS,
    )
    return coarse, repeated, fine


def single_event(result, event_type):
    matches = result.events_of_type(event_type=event_type)
    assert len(matches) == 1
    return matches[0]


def test_real_chain_completes_and_result_integrity_holds(demo_runs):
    """E2E-T01..T03: mocksuz real engine terminale gider ve result integrity sağlar."""

    for run in (demo_runs[0], demo_runs[2]):
        result = run.result
        assert result.termination_reason is SimulationTerminationReason.TERMINAL_EVENT
        assert result.steps_performed < run.configuration.run_limits.maximum_steps
        assert result.steps_performed >= 1
        assert len(result.samples) >= 2
        assert result.initial_sample is result.samples[0]
        assert result.last_accepted_sample is result.samples[-1]
        assert result.terminal_event is result.ground_event


def test_sample_chronology_finite_domains_and_vertical_symmetry(demo_runs):
    """E2E-T04/T05/T23/T24: accepted histories finite, valid ve vertical symmetric."""

    for run in (demo_runs[0], demo_runs[2]):
        samples = run.result.samples
        times = np.array([sample.point.time_s for sample in samples])
        assert np.all(np.diff(times) > 0.0)
        for sample in samples:
            state = sample.point.state
            physics = sample.physics
            assert np.all(np.isfinite(state.position_world_m))
            assert np.all(np.isfinite(state.velocity_world_m_s))
            assert np.allclose(state.position_world_m[:2], 0.0, rtol=0.0, atol=1e-12)
            assert np.allclose(state.velocity_world_m_s[:2], 0.0, rtol=0.0, atol=1e-12)
            assert physics.rocket_mass_properties.total_mass_kg > 0.0
            assert physics.flight_conditions.dynamic_pressure_Pa >= 0.0
            assert physics.basic_drag.total_cd0 >= 0.0
            assert physics.flight_conditions.mach >= 0.0
            assert physics.flight_conditions.reynolds >= 0.0
            assert np.all(np.isfinite(physics.gravity_acceleration_world_m_s2))
            assert np.isfinite(physics.motor_thrust_state.thrust_N)
            assert np.all(np.isfinite(physics.dynamics.acceleration_world_m_s2))


def test_event_cardinality_order_and_exact_burnout_authority(demo_runs):
    """E2E-T06..T10/T27: exact curve-end burnout ve strict event chronology."""

    burnout_times = []
    for run in (demo_runs[0], demo_runs[2]):
        result = run.result
        burnout = single_event(result, FlightEventType.BURNOUT)
        apogee = single_event(result, FlightEventType.APOGEE)
        ground = single_event(result, FlightEventType.GROUND)
        assert burnout.time_s < apogee.time_s < ground.time_s
        assert result.events[-1] is ground
        expected_burnout = (
            run.configuration.physics_context.propulsion_timeline.ignition_time_s
            + run.configuration.motor_curve_statistics.curve_end_time_s
        )
        assert run.configuration.motor_curve_statistics.curve_end_time_s == 1.430
        assert burnout.time_s == pytest.approx(expected_burnout, rel=0.0, abs=2e-15)
        burnout_times.append(burnout.time_s)
    assert burnout_times[0] == burnout_times[1]


def test_early_powered_flight_and_no_below_ground_samples(demo_runs):
    """E2E-T11..T13: missing pad modeline rağmen F50 ilk accepted stepte yükselir."""

    for run in (demo_runs[0], demo_runs[2]):
        result = run.result
        launch = run.configuration.launch_conditions
        first = result.samples[1]
        displacement = first.point.state.position_world_m - launch.initial_position_world_m
        assert float(displacement @ launch.launch_direction_world_unit) > 0.0
        assert first.point.state.velocity_world_m_s[2] > 0.0
        assert first.point.state.position_world_m[2] > launch.initial_position_world_m[2]
        ground_z = launch.initial_position_world_m[2]
        assert all(sample.point.state.position_world_m[2] >= ground_z for sample in result.samples)
        burnout_time = result.burnout_event.time_s
        powered = [
            sample
            for sample in result.samples
            if IGNITION_TIME_S < sample.point.time_s < burnout_time
            and sample.physics.motor_thrust_state.thrust_N > 0.0
        ]
        assert powered
        assert any(sample.point.state.velocity_world_m_s[2] > 0.0 for sample in powered)


def test_mass_evolution_is_accepted_positive_and_nonincreasing(demo_runs):
    """E2E-T14: NAT-015-owned rocket mass kullanılır; bağımsız depletion hesabı yok."""

    for run in (demo_runs[0], demo_runs[2]):
        result = run.result
        burnout_time = result.burnout_event.time_s
        powered_masses = np.array(
            [
                sample.physics.rocket_mass_properties.total_mass_kg
                for sample in result.samples
                if sample.point.time_s <= burnout_time
            ]
        )
        assert np.all(powered_masses > 0.0)
        assert np.all(np.diff(powered_masses) <= 0.0)
        assert result.last_accepted_sample.physics.motor_mass_properties.mass_kg < (
            result.initial_sample.physics.motor_mass_properties.mass_kg
        )


def test_coast_apogee_descent_and_ground_semantics(demo_runs):
    """E2E-T15..T22: coast, localized apogee, ballistic descent ve terminal ground."""

    for run in (demo_runs[0], demo_runs[2]):
        result = run.result
        burnout = result.burnout_event
        apogee = result.apogee_event
        ground = result.ground_event
        launch_z = run.configuration.launch_conditions.initial_position_world_m[2]
        coast = [
            sample
            for sample in result.samples
            if burnout.time_s < sample.point.time_s < apogee.time_s
            and sample.point.state.velocity_world_m_s[2] > 0.0
        ]
        descent = [
            sample
            for sample in result.samples
            if apogee.time_s < sample.point.time_s < ground.time_s
            and sample.point.state.velocity_world_m_s[2] < 0.0
        ]
        assert coast and descent
        assert apogee.estimated_state.velocity_world_m_s[2] == pytest.approx(
            0.0, abs=2e-15
        )
        assert apogee.estimated_state.position_world_m[2] > launch_z
        assert apogee.estimated_state.position_world_m[2] > (
            burnout.estimated_state.position_world_m[2]
        )
        assert ground.is_terminal
        assert ground.estimated_state.position_world_m[2] == pytest.approx(
            launch_z, abs=2e-15
        )
        assert ground.estimated_state.velocity_world_m_s[2] < 0.0
        assert result.termination_time_s == pytest.approx(ground.time_s, abs=2e-15)
        if ground.interpolation_fraction < 1.0:
            assert result.last_accepted_sample.point.time_s < ground.time_s
            assert all(sample.point.time_s != ground.time_s for sample in result.samples)
        else:
            assert result.last_accepted_sample.point.time_s == ground.time_s


def test_identical_primary_runs_are_deterministic(demo_runs):
    """E2E-T25: random/turbulence olmayan exact config aynı history'yi üretir."""

    first, repeated, _ = demo_runs
    left, right = first.result, repeated.result
    assert left.termination_reason is right.termination_reason
    assert left.steps_performed == right.steps_performed
    assert len(left.samples) == len(right.samples)
    assert [event.event_type for event in left.events] == [
        event.event_type for event in right.events
    ]
    assert [event.time_s for event in left.events] == [
        event.time_s for event in right.events
    ]
    for left_sample, right_sample in zip(left.samples, right.samples):
        assert left_sample.point.time_s == right_sample.point.time_s
        np.testing.assert_array_equal(
            left_sample.point.state.position_world_m,
            right_sample.point.state.position_world_m,
        )
        np.testing.assert_array_equal(
            left_sample.point.state.velocity_world_m_s,
            right_sample.point.state.velocity_world_m_s,
        )
    for left_event, right_event in zip(left.events, right.events):
        np.testing.assert_array_equal(
            left_event.estimated_state.position_world_m,
            right_event.estimated_state.position_world_m,
        )
        np.testing.assert_array_equal(
            left_event.estimated_state.velocity_world_m_s,
            right_event.estimated_state.velocity_world_m_s,
        )


def test_fixed_step_sensitivity_policy(demo_runs):
    """E2E-T26/T28..T30: explicit V&V-only coarse/fine acceptance policy."""

    coarse, _, fine = demo_runs
    coarse_apogee = coarse.result.apogee_event
    fine_apogee = fine.result.apogee_event
    coarse_ground = coarse.result.ground_event
    fine_ground = fine.result.ground_event
    assert abs(coarse_apogee.time_s - fine_apogee.time_s) <= 2 * PRIMARY_STEP_SIZE_S
    assert abs(coarse_ground.time_s - fine_ground.time_s) <= 2 * PRIMARY_STEP_SIZE_S
    launch_z = coarse.configuration.launch_conditions.initial_position_world_m[2]
    coarse_height = coarse_apogee.estimated_state.position_world_m[2] - launch_z
    fine_height = fine_apogee.estimated_state.position_world_m[2] - launch_z
    assert fine_height > 0.0
    assert abs(coarse_height - fine_height) / fine_height <= 0.01


def test_diagnostic_summary(demo_runs):
    """V&V evidence için production API'ye taşınmayan actual primary diagnostics."""

    run, _, fine_run = demo_runs
    result = run.result
    burnout, apogee, ground = (
        result.burnout_event,
        result.apogee_event,
        result.ground_event,
    )
    max_mach = max(sample.physics.flight_conditions.mach for sample in result.samples)
    max_q = max(
        sample.physics.flight_conditions.dynamic_pressure_Pa
        for sample in result.samples
    )
    max_speed = max(
        sample.physics.flight_conditions.airspeed_m_s for sample in result.samples
    )
    fine_result = fine_run.result
    fine_apogee = fine_result.apogee_event
    fine_ground = fine_result.ground_event
    first_propagated = result.samples[1]
    post_burn = next(
        sample for sample in result.samples if sample.point.time_s > burnout.time_s
    )
    print(
        "NAT022_DIAGNOSTICS",
        {
            "initial_total_mass_kg": result.initial_sample.physics.rocket_mass_properties.total_mass_kg,
            "final_motor_mass_kg": result.last_accepted_sample.physics.motor_mass_properties.mass_kg,
            "post_burn_motor_mass_kg": post_burn.physics.motor_mass_properties.mass_kg,
            "steps": result.steps_performed,
            "samples": len(result.samples),
            "termination": result.termination_reason.value,
            "burnout": (
                burnout.time_s,
                float(burnout.estimated_state.position_world_m[2]),
                float(burnout.estimated_state.velocity_world_m_s[2]),
            ),
            "apogee": (
                apogee.time_s,
                float(apogee.estimated_state.position_world_m[2]),
                float(apogee.estimated_state.velocity_world_m_s[2]),
            ),
            "ground": (
                ground.time_s,
                float(ground.estimated_state.velocity_world_m_s[2]),
                ground.interpolation_fraction,
            ),
            "last": (
                result.last_accepted_sample.point.time_s,
                float(result.last_accepted_sample.point.state.position_world_m[2]),
            ),
            "maximum_mach": max_mach,
            "maximum_dynamic_pressure_Pa": max_q,
            "maximum_speed_m_s": max_speed,
            "first_propagated": (
                first_propagated.point.time_s,
                float(first_propagated.point.state.position_world_m[2]),
                float(first_propagated.point.state.velocity_world_m_s[2]),
            ),
            "fine": {
                "steps": fine_result.steps_performed,
                "samples": len(fine_result.samples),
                "burnout_time_s": fine_result.burnout_event.time_s,
                "apogee_time_s": fine_apogee.time_s,
                "apogee_world_z_m": float(fine_apogee.estimated_state.position_world_m[2]),
                "ground_time_s": fine_ground.time_s,
            },
            "sensitivity": {
                "apogee_time_difference_s": abs(apogee.time_s - fine_apogee.time_s),
                "ground_time_difference_s": abs(ground.time_s - fine_ground.time_s),
                "apogee_height_relative_difference": abs(
                    apogee.estimated_state.position_world_m[2]
                    - fine_apogee.estimated_state.position_world_m[2]
                )
                / fine_apogee.estimated_state.position_world_m[2],
            },
        },
    )
