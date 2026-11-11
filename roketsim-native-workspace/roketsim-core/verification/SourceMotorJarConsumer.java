import java.util.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.propulsion.catalog.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.simulation.*;

/** Independent MAIN-JAR consumer. All vehicle dimensions/ballast below are TEST_ONLY assumptions. */
public class SourceMotorJarConsumer {
    public static void main(String[] args) {
        for(var catalog:Teknofest2026MotorCatalog.load()) {
            var curve=catalog.thrustSources().get(0);var header=curve.record().header();
            var ejectionKind=header.rawEjectionToken().equals("P")?MotorEjectionMetadata.Kind.PLUGGED:MotorEjectionMetadata.Kind.UNKNOWN;
            var model=new MotorPropertyModelProfile(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,MotorCgEvolutionModel.FIXED_MIDPOINT);
            var motor=new SourceBackedMotorProfile(catalog,curve.curveId(),MotorType.RELOADABLE,
                catalog.massObservations().stream().filter(m->m.quantity().equals("ENG_INITIAL")).findFirst().orElseThrow(),
                catalog.massObservations().stream().filter(m->m.quantity().equals("ENG_PROPELLANT")).findFirst().orElseThrow(),
                new SourceBackedMotorProfile.GeometryInterpretation(header.nominalDiameterM(),header.nominalLengthM(),"TEST_ONLY: declared nominal geometry","TEST_ONLY, physical datum/envelope unresolved"),
                new MotorEjectionMetadata(ejectionKind,OptionalDouble.empty(),Optional.of(header.rawEjectionToken()),"TEKNOFEST source token, no asserted physical delay"),
                Optional.empty(),null,Optional.empty(),null,Optional.empty(),model);
            var raw=new SingleStageRocketGeometry(.2,new ConicalNoseGeometry(.4,NoseConstructionMode.HOLLOW_SHELL,.003),new CylindricalBodyGeometry(2,.003),
                new TrapezoidalFinSetGeometry(4,.3,.15,.15,.1,1.8,.004,FinCrossSection.SQUARE,FinAngularArrangement.EQUALLY_SPACED),
                new MotorAttachmentGeometry(new MotorMountTubeGeometry(1.2,motor.diameterM()+.005,.002,0),new CenteringRingPairGeometry(.004),.005),ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
            var geometry=new GeometryResolver().resolve(raw);
            var structure=new StructuralMassPropertiesCalculator().evaluate(geometry,new SingleStageRocketMaterials(MaterialCatalog.POLYSTYRENE,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD));
            var installed=new MotorInstallationResolver().resolveRuntime(geometry,motor);var surface=new SurfaceFinish("TEST_ONLY",0);
            var context=new RuntimePhysicsEvaluationContext3DOF(geometry,new SingleStageRocketAerodynamicSurfaces(surface,surface,surface),structure,installed,model,
                BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,
                1200,new PropulsionTimeline(0),new USStandardAtmosphere1976Lower(),new DryAirPropertiesCalculator(),new ConstantGravityModel(),new NoWindModel());
            var config=new SourceMotorGroundRunConfiguration3DOF(new LaunchConditions3DOF(new double[]{100,-200,50},new double[]{0,0,20},new double[]{0,0,1}),context,
                new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION),
                new FixedStepConfig(.02),TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT,
                List.of(new AttachedHardwareMass("TEST_ONLY 100 kg ballast, not actual vehicle",100,1.2)),Optional.empty(),Optional.empty(),IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
            var policy=new RunControl3DOF.MonitoringPolicy(1_000_000,10_000_000_000L,60,10000);
            var result=new SimulationEngine3DOF().runSourceUntilGround(config,new RunControl3DOF(policy,p->{},w->{}));
            if(result.terminationReason()!=RunControl3DOF.Status.GROUND || result.groundEvent().isEmpty() || motor.certificationEvidence().isPresent())throw new AssertionError("source runtime failed "+result.failure());
            if(!motor.qualifications().contains("NO_VERIFIED_CERTIFICATION_EVIDENCE_PROVIDED"))throw new AssertionError("missing research qualification");
            if(!motor.selectedThrustSource().source().sha256().equals(curve.source().sha256()))throw new AssertionError("lost source provenance");
            System.out.println("TEST_ONLY "+header.designation()+" sha256="+curve.source().sha256()+" ground="+result.estimatedGroundContactTimeS().orElseThrow()+" accepted="+result.acceptedSegments());
        }
        System.out.println("SOURCE_MOTOR_JAR_CONSUMER: PASS (TEST_ONLY vehicle fixtures, not physical flight readiness)");
    }
}
