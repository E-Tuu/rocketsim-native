package org.roketsim.core.simulation;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import org.roketsim.core.numerics.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.aerodynamics.*;

class RecoveryTimingTest {
    static RecoveryRuntime3DOF runtime(double initialVz) {
        // Isolated terminal-handler fixture: accepted boundary lies on the declared ground plane.
        // It verifies precedence, not a claim that this arbitrary point was a complete native trajectory.
        var geometry=new GeometryResolver().resolve(new SingleStageRocketGeometry(.1,
            new ConicalNoseGeometry(.3,NoseConstructionMode.HOLLOW_SHELL,.002),new CylindricalBodyGeometry(.7,.002),
            new TrapezoidalFinSetGeometry(4,.18,.08,.12,.05,.72,.003,FinCrossSection.SQUARE,FinAngularArrangement.EQUALLY_SPACED),
            new MotorAttachmentGeometry(new MotorMountTubeGeometry(.12,.029,.001,0),new CenteringRingPairGeometry(.003),.005),ReferenceGeometryPolicy.MAXIMUM_DIAMETER));
        var structure=new StructuralMassPropertiesCalculator().evaluate(geometry,new SingleStageRocketMaterials(
            MaterialCatalog.POLYSTYRENE,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD));
        var finish=new SurfaceFinish("synthetic terminal precedence",0);
        var context=new PhysicsEvaluationContext3DOF(geometry,new SingleStageRocketAerodynamicSurfaces(finish,finish,finish),structure,
            new MotorInstallationResolver().resolve(geometry,MotorCatalogs.AEROTECH_F50_4T),MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,
            BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,1200,new PropulsionTimeline(0),new USStandardAtmosphere1976Lower(),
            new DryAirPropertiesCalculator(),new ConstantGravityModel(),new NoWindModel());
        var launch=new LaunchConditions3DOF(new double[]{0,0,0},new double[]{0,0,0},new double[]{0,0,1});
        var profile=new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
            ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION);
        var base=new SimulationRunConfiguration3DOF(launch,context,new MotorCurveAnalyzer().analyze(MotorCatalogs.AEROTECH_F50_4T),profile,
            new FixedStepConfig(1),new SimulationRunLimits3DOF(10),TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT);
        var start=point(0,1,initialVz);
        return new RecoveryRuntime3DOF(base,new RecoveryConfiguration3DOF(.05,1),new double[0],new double[0],start,null,List.of(),List.of(),List.of());
    }
    static IntegrationPoint3DOF point(double t,double z,double vz) {
        return new IntegrationPoint3DOF(t,new TranslationalState3DOF(Vector.of(new double[]{0,0,z}),Vector.of(new double[]{0,0,vz})));
    }
    @Test void verifiedGroundEndpointPrecedesCoincidentActivationEvenUnderThrust() {
        var runtime=runtime(-1);
        runtime.state=RecoveryFlightResult3DOF.State.PENDING;runtime.triggerTimeS=0.;runtime.activationTimeS=1.;
        var endpoint=new IntegrationPoint3DOF(1,new TranslationalState3DOF(new double[]{0,0,0},new double[]{0,0,-2}));
        var detector=new FlightEventDetector3DOF();
        var c=new FlightEventDetectionContext3DOF(runtime.base.launchConditions(),runtime.base.physicsContext().propulsionTimeline(),
            runtime.base.motorCurveStatistics(),runtime.base.eventDetectionProfile());
        var reports=detector.detect(runtime.samples.get(0).point(),endpoint,c);
        assertTrue(reports.stream().anyMatch(e->e.isTerminal() && e.interpolationFraction()==1));
        assertTrue(runtime.terminal(reports,endpoint));
        assertEquals(RecoveryFlightResult3DOF.Outcome.GROUND,runtime.outcome);
        assertEquals(RecoveryFlightResult3DOF.State.PENDING,runtime.state);assertTrue(runtime.events.isEmpty());
        assertTrue(runtime.samples.get(runtime.samples.size()-1).physics().motorThrustState().thrustN()>0);
        assertEquals(RecoveryFlightResult3DOF.AerodynamicModel.BASELINE_BODY_DRAG,runtime.samples.get(runtime.samples.size()-1).physics().aerodynamicModel());
    }
    @Test void activationArithmeticRejectsOverflowAndCollapsedPositiveDelay() {
        assertEquals(5,RecoveryRuntime3DOF.activationTime(5,0));
        assertEquals(5.5,RecoveryRuntime3DOF.activationTime(5,.5));
        assertEquals("INVALID_RECOVERY_ACTIVATION_TIME",assertThrows(RecoveryRuntime3DOF.TimingFailure.class,
            ()->RecoveryRuntime3DOF.activationTime(Double.MAX_VALUE,Double.MAX_VALUE)).code());
        assertEquals("INVALID_RECOVERY_ACTIVATION_TIME",assertThrows(RecoveryRuntime3DOF.TimingFailure.class,
            ()->RecoveryRuntime3DOF.activationTime(1,Double.MIN_VALUE)).code());
    }
    @Test void strictThresholdMaximumTimestampAndFirstDetectionSample() {
        var r=runtime(1);
        r.observeAccepted(point(1,2,1));
        r.observeAccepted(point(2,2,0));
        assertEquals(2,r.maximumSampledAltitudeM);assertEquals(1,r.maximumSampledAltitudeTimeS);
        double boundary=2-.01;
        r.observeAccepted(point(3,Math.nextUp(boundary),-1));
        r.observeAccepted(point(4,boundary,-1));
        assertEquals(RecoveryFlightResult3DOF.State.NOT_TRIGGERED,r.state);
        r.observeAccepted(point(5,Math.nextDown(boundary),1)); // No separate vz sign condition.
        assertEquals(RecoveryFlightResult3DOF.State.PENDING,r.state);
        assertEquals(5,r.triggerTimeS);assertEquals(6,r.activationTimeS);
        assertEquals(5,r.events.get(0).point().timeS());
        r.observeAccepted(point(6,0,-1));assertEquals(1,r.events.size());
        assertEquals(2,r.maximumSampledAltitudeM);assertEquals(1,r.maximumSampledAltitudeTimeS);
    }
    @Test void noAscentHistoryUntilPositiveEligibleSample() {
        for(double initial:new double[]{0,-1}) {
            var r=runtime(initial);
            r.observeAccepted(point(1,.5,-1));assertFalse(r.ascentSeen);assertTrue(r.events.isEmpty());
            r.observeAccepted(point(2,.6,1));assertTrue(r.ascentSeen);
            assertEquals(.6,r.maximumSampledAltitudeM);assertEquals(2,r.maximumSampledAltitudeTimeS);
            r.observeAccepted(point(3,.58,-1));assertEquals(3,r.triggerTimeS);
        }
    }
    @Test void noAltitudeDropMeansNoTriggerRegardlessOfZeroVelocityTouches() {
        var r=runtime(1);
        for(int i=1;i<=4;i++)r.observeAccepted(point(i,1,i%2==0?1:0));
        assertTrue(r.events.isEmpty());
    }
    @Test void actualAuthorizedSegmentGroundCrossingPreventsActivation() {
        var r=runtime(-1);r.state=RecoveryFlightResult3DOF.State.PENDING;
        r.triggerTimeS=1.;r.activationTimeS=5.1;
        var start=point(5,.01,-1);
        var c=new FlightEventDetectionContext3DOF(r.base.launchConditions(),r.base.physicsContext().propulsionTimeline(),
            r.base.motorCurveStatistics(),r.base.eventDetectionProfile());
        var accepted=r.advance(start,.1,null,new FlightEventDetector3DOF(),c);
        assertNull(accepted);assertEquals(RecoveryFlightResult3DOF.Outcome.GROUND,r.outcome);
        assertNull(r.failure);assertTrue(r.events.isEmpty());
        assertEquals(RecoveryFlightResult3DOF.State.PENDING,r.state);
        assertTrue(r.terminationTimeS<r.activationTimeS);
    }
    @Test void pointBelowGroundWithoutCrossingEvidenceDoesNotManufactureGroundFailure() {
        var r=runtime(-1);r.state=RecoveryFlightResult3DOF.State.PENDING;
        r.triggerTimeS=1.;r.activationTimeS=5.;
        assertTrue(r.activate(point(5,-1,-1)));
        assertEquals(RecoveryFlightResult3DOF.State.ACTIVE,r.state);assertNull(r.failure);
    }
}
