import java.util.List;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.simulation.*;

/** Independent packaged-module consumer. All dimensions below are synthetic V&V inputs, not defaults. */
public class RecoveryJarConsumer {
    public static void main(String[] args) {
        var raw=new SingleStageRocketGeometry(.1,new ConicalNoseGeometry(.3,NoseConstructionMode.HOLLOW_SHELL,.002),
            new CylindricalBodyGeometry(.7,.002),new TrapezoidalFinSetGeometry(4,.18,.08,.12,.05,.72,.003,
                FinCrossSection.SQUARE,FinAngularArrangement.EQUALLY_SPACED),
            new MotorAttachmentGeometry(new MotorMountTubeGeometry(.12,.029,.001,0),new CenteringRingPairGeometry(.003),.005),
            ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
        var geometry=new GeometryResolver().resolve(raw);
        var materials=new SingleStageRocketMaterials(MaterialCatalog.POLYSTYRENE,MaterialCatalog.CARDBOARD,
            MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD);
        var structure=new StructuralMassPropertiesCalculator().evaluate(geometry,materials);
        var motor=new MotorInstallationResolver().resolve(geometry,MotorCatalogs.AEROTECH_F50_4T);
        var finish=new SurfaceFinish("synthetic consumer surface",0);
        var context=new PhysicsEvaluationContext3DOF(geometry,new SingleStageRocketAerodynamicSurfaces(finish,finish,finish),
            structure,motor,MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,
            TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,
            1200,new PropulsionTimeline(0),new USStandardAtmosphere1976Lower(),new DryAirPropertiesCalculator(),new ConstantGravityModel(),new NoWindModel());
        var launch=new LaunchConditions3DOF(new double[]{10,20,50},new double[]{0,0,0},LaunchGuideGeometry.directionFromAngles(85,90));
        var run=new SimulationRunConfiguration3DOF(launch,context,new MotorCurveAnalyzer().analyze(motor.motor()),
            new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
                GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION),new FixedStepConfig(.02),
            new SimulationRunLimits3DOF(2000),TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT);
        var vehicle=new SingleStageVehicleRunConfiguration3DOF(run,List.of());
        var primary=new LaunchGuideRunConfiguration3DOF(vehicle,
            new LaunchGuideGeometry("consumer datum",LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL,1.5,"synthetic usable geometry"),
            new GuideAttachmentGeometry("consumer datum",.5,.02,"synthetic contact coordinates"),
            LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        var recovery=new RecoveryConfiguration3DOF(.05,.25);
        var engine=new SimulationEngine3DOF();var original=engine.run(run);
        var direct=engine.run(new RecoveryRunConfiguration3DOF(vehicle,recovery,IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
        var guided=new LaunchGuideSimulator3DOF().run(primary,recovery);
        for(var result:List.of(direct,guided)) {
            if(result.outcome()!=RecoveryFlightResult3DOF.Outcome.GROUND || result.activation().isEmpty())
                throw new AssertionError("recovery failed: "+result.outcome()+" "+result.failure());
            if(result.recoveryEvents().size()!=2)throw new AssertionError("repeated deployment");
            for(var sample:result.samples()) if(sample.recoveryState()==RecoveryFlightResult3DOF.State.ACTIVE) {
                if(sample.physics().bodyDrag().isPresent() || sample.phase()!=FlightPhase3DOF.RECOVERY_DESCENT)
                    throw new AssertionError("incorrect active model metadata");
                var force=new RecoveryDragEvaluator3DOF().evaluate(sample.physics().atmosphere().densityKgM3(),sample.physics().relativeFlow(),.05);
                if(!force.equals(sample.physics().dynamics().forces().dragForceWorldN()))throw new AssertionError("incorrect applied drag");
            }
        }
        if(!original.equals(engine.run(run)))throw new AssertionError("legacy mutated");
        System.out.println("direct detection="+direct.physicalApogee().orElseThrow().point().timeS()+" activation="+direct.activation().orElseThrow().point().timeS()+" termination="+direct.terminationTimeS());
        System.out.println("guided detection="+guided.physicalApogee().orElseThrow().point().timeS()+" activation="+guided.activation().orElseThrow().point().timeS()+" termination="+guided.terminationTimeS());
        System.out.println("RECOVERY_JAR_CONSUMER: PASS");
    }
}