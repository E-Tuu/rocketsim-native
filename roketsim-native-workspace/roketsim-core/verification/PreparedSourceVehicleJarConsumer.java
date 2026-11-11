import java.util.List;
import java.util.Optional;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.numerics.FixedStepConfig;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.propulsion.catalog.Teknofest2026MotorCatalog;
import org.roketsim.core.simulation.*;

/** External main-JAR API check with a synthetic vehicle and synthetic measurements. */
public class PreparedSourceVehicleJarConsumer {
    public static void main(String[] args) {
        var catalog = Teknofest2026MotorCatalog.load().get(0);
        var curve = catalog.thrustSources().get(0);
        var header = curve.record().header();
        var model = new MotorPropertyModelProfile(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,
            MotorCgEvolutionModel.FIXED_MIDPOINT);
        var motor = new SourceBackedMotorProfile(catalog, curve.curveId(), MotorType.RELOADABLE,
            catalog.massObservations().stream().filter(m -> m.quantity().equals("ENG_INITIAL")).findFirst().orElseThrow(),
            catalog.massObservations().stream().filter(m -> m.quantity().equals("ENG_PROPELLANT")).findFirst().orElseThrow(),
            new SourceBackedMotorProfile.GeometryInterpretation(header.nominalDiameterM(), header.nominalLengthM(),
                "TEST_ONLY nominal source geometry", "physical datum and envelope unresolved"),
            new MotorEjectionMetadata(MotorEjectionMetadata.Kind.UNKNOWN, java.util.OptionalDouble.empty(),
                Optional.of(header.rawEjectionToken()), "source token only"),
            Optional.empty(), null, Optional.empty(), null, Optional.empty(), model);
        var raw = new SingleStageRocketGeometry(.2,
            new ConicalNoseGeometry(.4, NoseConstructionMode.HOLLOW_SHELL, .003),
            new CylindricalBodyGeometry(2, .003),
            new TrapezoidalFinSetGeometry(4, .3, .15, .15, .1, 1.8, .004,
                FinCrossSection.SQUARE, FinAngularArrangement.EQUALLY_SPACED),
            new MotorAttachmentGeometry(new MotorMountTubeGeometry(1.2, motor.diameterM() + .005, .002, 0),
                new CenteringRingPairGeometry(.004), .005), ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
        var geometry = new GeometryResolver().resolve(raw);
        var cardboard = MaterialCatalog.CARDBOARD;
        var materials = new SingleStageRocketMaterials(MaterialCatalog.POLYSTYRENE, cardboard, cardboard, cardboard, cardboard);
        var structure = new StructuralMassPropertiesCalculator().evaluate(geometry, materials);
        var installed = new MotorInstallationResolver().resolveRuntime(geometry, motor);
        var finish = new SurfaceFinish("TEST_ONLY", 0);
        var context = new RuntimePhysicsEvaluationContext3DOF(geometry,
            new SingleStageRocketAerodynamicSurfaces(finish, finish, finish), structure, installed, model,
            BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,
            TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET, 1200, new PropulsionTimeline(0),
            new USStandardAtmosphere1976Lower(), new DryAirPropertiesCalculator(), new ConstantGravityModel(), new NoWindModel());
        var ballast = new AttachedHardwareMass("TEST_ONLY ballast", 100, 1.2);
        var run = new SourceMotorGroundRunConfiguration3DOF(
            new LaunchConditions3DOF(new double[]{100, -200, 50}, new double[]{0, 0, 20}, new double[]{0, 0, 1}),
            context, new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
                ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING, GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,
                EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION),
            new FixedStepConfig(.02), TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT,
            List.of(ballast), Optional.empty(), Optional.empty(), IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        var override = new StructuralMassOverride("structure:body",
            Optional.of(new StructuralMassOverride.Measurement(1, "TEST_ONLY synthetic scale")),
            Optional.of(new StructuralMassOverride.Measurement(1.2, "TEST_ONLY synthetic balance")));
        var evidence = new PreparedSourceVehicle3DOF.HardwareEvidence(ballast.componentId(),
            PreparedSourceVehicle3DOF.Origin.MODEL_BASED, "synthetic ballast mass",
            PreparedSourceVehicle3DOF.Origin.MODEL_BASED, "synthetic ballast position");
        var prepared = PreparedSourceVehicle3DOF.prepare("test-vehicle:r1", "TEST_ONLY design", materials, run,
            List.of(override), List.of(new PreparedSourceVehicle3DOF.IncludedItem("TEST_ONLY included device", "structure:body")),
            List.of(evidence));
        var policy = new RunControl3DOF.MonitoringPolicy(1_000_000, 10_000_000_000L, 60, 10000);
        var complete = prepared.runUntilGround(new RunControl3DOF(policy, p -> {}, w -> {}));
        var selected = prepared.runConfiguration().physicsContext().structuralMassProperties().body();
        if (complete.configuration() != prepared || !complete.configuration().configurationId().equals("test-vehicle:r1")
                || selected.massKg() != 1 || selected.cgXGeoM() != 1.2
                || complete.result().terminationReason() != RunControl3DOF.Status.GROUND
                || complete.result().groundEvent().isEmpty())
            throw new AssertionError("Prepared source run failed: " + complete.result().failure());
        System.out.println("PREPARED_SOURCE_VEHICLE_JAR_CONSUMER: PASS (synthetic fixture)");
    }
}
