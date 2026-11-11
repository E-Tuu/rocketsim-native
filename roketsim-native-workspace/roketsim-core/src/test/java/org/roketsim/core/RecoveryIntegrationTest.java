package org.roketsim.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.simulation.RecoveryFlightResult3DOF.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import org.roketsim.core.numerics.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.aerodynamics.RecoveryDragEvaluator3DOF;

class RecoveryIntegrationTest {
    static Vector v(double... values) {return Vector.of(values);}
    static SimulationRunConfiguration3DOF coast(double h,int limit,double vz,boolean powered) {
        var base=NativeDemoTrajectoryTest.configuration(h,limit);
        var motor=MotorCatalogs.AEROTECH_F50_4T;
        motor=(MotorDefinition)VehicleTestData.replace(motor,"thrust_curve",powered?
            List.of(new ThrustSample(0,0),new ThrustSample(.001,1),new ThrustSample(100,1),new ThrustSample(101,0)):
            List.of(new ThrustSample(0,0),new ThrustSample(100,0),new ThrustSample(101,1),new ThrustSample(102,0)));
        var context=(PhysicsEvaluationContext3DOF)VehicleTestData.replace(base.physicsContext(),"motor_installation.motor",motor);
        return new SimulationRunConfiguration3DOF(new LaunchConditions3DOF(v(0,0,100),v(0,0,vz),v(0,0,1)),context,
            new MotorCurveAnalyzer().analyze(motor),base.eventDetectionProfile(),base.fixedStepConfig(),base.runLimits(),base.terminalEventHandlingModel());
    }
    static RecoveryFlightResult3DOF run(SimulationRunConfiguration3DOF base,double delay) {
        return new SimulationEngine3DOF().run(new RecoveryRunConfiguration3DOF(
            new SingleStageVehicleRunConfiguration3DOF(base,List.of()),new RecoveryConfiguration3DOF(.05,delay),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
    }
    static IntegrationPoint3DOF point(double time,double height,double vz) {
        return new IntegrationPoint3DOF(time,new TranslationalState3DOF(v(0,0,height),v(0,0,vz)));
    }
    static DerivativeFunction3DOF acceleration(double a) {
        return (t,s)->new TranslationalStateDerivative3DOF(s.velocityWorldMS(),v(0,0,a));
    }
    @Test void noPriorAscentDoesNotManufactureAnApogee() {
        for(double vz:new double[]{0,-1}) {
            var r=run(coast(.02,40,vz,false),0);
            assertEquals(State.NOT_TRIGGERED,r.recoveryState());assertTrue(r.recoveryEvents().isEmpty());
            assertEquals(Outcome.MAXIMUM_STEPS,r.outcome());
        }
    }
    @Test void zeroDelayActivatesOnceAtTheSameAcceptedPoint() {
        var r=run(coast(.02,2000,20,false),0);
        assertEquals(Outcome.GROUND,r.outcome());assertEquals(2,r.recoveryEvents().size());
        var trigger=r.physicalApogee().orElseThrow();var activation=r.activation().orElseThrow();
        assertEquals(trigger.point(),activation.point());
        assertEquals(trigger.point().timeS(),trigger.scheduledActivationTimeS().orElseThrow());
        assertEquals(1,r.samples().stream().filter(s->s.point().timeS()==activation.point().timeS()).count());
    }
    @Test void delayPreservesBaselinePhysicsAndPendingPhase() {
        var base=coast(.02,2000,20,false);var r=run(base,.25);
        var trigger=r.physicalApogee().orElseThrow();var activation=r.activation().orElseThrow();
        assertEquals(trigger.point().timeS()+.25,activation.point().timeS());
        assertTrue(r.samples().stream().anyMatch(s->s.recoveryState()==State.PENDING));
        for(var sample:r.samples()) if(sample.point().timeS()<activation.point().timeS()) {
            var expected=new PhysicsEvaluator3DOF().evaluate(sample.point().timeS(),sample.point().state(),base.launchConditions(),base.physicsContext());
            assertEquals(expected.dynamics().forces(),sample.physics().dynamics().forces());
            assertEquals(FlightPhase3DOF.FREE_FLIGHT,sample.phase());
            assertEquals(AerodynamicModel.BASELINE_BODY_DRAG,sample.physics().aerodynamicModel());
        }
    }
    static TranslationalStateDerivative3DOF recoveryDerivative(SimulationRunConfiguration3DOF base,double time,TranslationalState3DOF state) {
        var p=new PhysicsEvaluator3DOF().evaluate(time,state,base.launchConditions(),base.physicsContext());
        var drag=new RecoveryDragEvaluator3DOF().evaluate(p.atmosphere().densityKgM3(),p.relativeFlow(),.05);
        return new TranslationalDynamicsEvaluator().evaluateWithAerodynamicForce(state,base.launchConditions(),p.rocketMassProperties().totalMassKg(),
            p.motorThrustState().thrustN(),drag,p.gravityAccelerationWorldMS2(),ThrustDirectionModel.FIXED_LAUNCH_DIRECTION).derivative();
    }
    @Test void activationKeepsAllFourStagesOnTheirOwnRegimeWithoutImpulse() {
        var base=coast(.02,2000,20,false);var r=run(base,.253);
        var boundary=r.activation().orElseThrow().point();var samples=r.samples();
        int i=0;while(!samples.get(i).point().equals(boundary))i++;
        var before=samples.get(i-1).point();var after=samples.get(i+1).point();
        var body=new ClassicalRK4Integrator3DOF().step(before,new FixedStepConfig(boundary.timeS()-before.timeS()),
            (t,s)->new PhysicsEvaluator3DOF().evaluate(t,s,base.launchConditions(),base.physicsContext()).dynamics().derivative());
        assertEquals(body,boundary);
        var recovery=new ClassicalRK4Integrator3DOF().step(boundary,new FixedStepConfig(after.timeS()-boundary.timeS()),
            (t,s)->recoveryDerivative(base,t,s));
        assertEquals(recovery,after);
    }
    @Test void activePhysicsIsOnlyNat034DragIncludingWindAndCurrentMass() {
        var base=coast(.02,2000,20,false);
        var context=(PhysicsEvaluationContext3DOF)VehicleTestData.replace(base.physicsContext(),"wind_model",new ConstantWindModel(v(3,-2,0)));
        base=(SimulationRunConfiguration3DOF)VehicleTestData.replace(base,"physics_context",context);
        var r=run(base,.1);assertTrue(r.activation().isPresent());
        for(var s:r.samples()) if(s.recoveryState()==State.ACTIVE) {
            var p=s.physics();assertTrue(p.bodyDrag().isEmpty());assertEquals(FlightPhase3DOF.RECOVERY_DESCENT,s.phase());
            assertEquals(AerodynamicModel.CONSTANT_PARACHUTE_DRAG,p.aerodynamicModel());
            assertEquals(new RecoveryDragEvaluator3DOF().evaluate(p.atmosphere().densityKgM3(),p.relativeFlow(),.05),p.dynamics().forces().dragForceWorldN());
            assertEquals(v(s.point().state().velocityWorldMS().get(0)-3,s.point().state().velocityWorldMS().get(1)+2,s.point().state().velocityWorldMS().get(2)),p.relativeFlow());
        }
    }
    @Test void groundDuringPendingDelayDoesNotActivate() {
        var r=run(coast(.02,2000,20,false),100);
        assertEquals(Outcome.GROUND,r.outcome());assertEquals(State.PENDING,r.recoveryState());assertTrue(r.activation().isEmpty());
        assertTrue(r.terminationTimeS()<r.physicalApogee().orElseThrow().scheduledActivationTimeS().orElseThrow());
    }
    @Test void positiveThrustRejectsActivationWithDetailedOutcome() {
        var r=run(coast(.01,500,1,true),0);
        assertEquals(Outcome.UNSUPPORTED,r.outcome());assertEquals(State.PENDING,r.recoveryState());assertTrue(r.activation().isEmpty());
        var f=r.failure().orElseThrow();assertEquals("RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST",f.code());
        assertTrue(f.details().get("motor_thrust_N")>0);assertEquals(0,f.details().get("deployment_delay_s"));
        assertEquals(r.physicalApogee().orElseThrow().point().timeS(),f.details().get("activation_time_s"));
        assertTrue(r.samples().stream().allMatch(s->s.physics().aerodynamicModel()==AerodynamicModel.BASELINE_BODY_DRAG));
    }
    @Test void collapsedActivationDelayProducesNumericalFailureWithoutDeployment() {
        var r=run(coast(.02,2000,20,false),Double.MIN_VALUE);
        assertEquals(Outcome.NUMERICAL_FAILURE,r.outcome());assertTrue(r.activation().isEmpty());
        assertEquals("INVALID_RECOVERY_ACTIVATION_TIME",r.failure().orElseThrow().code());
    }
    @Test void discardedParentGroundEstimateCannotForceNumericalFailure() {
        var base=coast(.02,2000,20,false);var pending=run(base,100);
        double trigger=pending.physicalApogee().orElseThrow().point().timeS();
        double delay=pending.terminationTimeS()-trigger;
        assertEquals(pending.terminationTimeS(),trigger+delay);
        var previous=pending.samples().get(pending.samples().size()-1).point();
        var full=new ClassicalRK4Integrator3DOF().step(previous,base.fixedStepConfig(),
            (t,s)->new PhysicsEvaluator3DOF().evaluate(t,s,base.launchConditions(),base.physicsContext()).dynamics().derivative());
        var clipped=new ClassicalRK4Integrator3DOF().step(previous,new FixedStepConfig((trigger+delay)-previous.timeS()),
            (t,s)->new PhysicsEvaluator3DOF().evaluate(t,s,base.launchConditions(),base.physicsContext()).dynamics().derivative());
        var events=new FlightEventDetector3DOF().detect(previous,full,new FlightEventDetectionContext3DOF(base.launchConditions(),
            base.physicsContext().propulsionTimeline(),base.motorCurveStatistics(),base.eventDetectionProfile()));
        assertTrue(events.stream().anyMatch(e->e.isTerminal() && e.timeS()<=trigger+delay));
        assertTrue(clipped.state().positionWorldM().get(2)>base.launchConditions().initialPositionWorldM().get(2));
        var r=run(base,delay);
        assertEquals(clipped,r.activation().orElseThrow().point());
        assertEquals(Outcome.GROUND,r.outcome());assertTrue(r.failure().isEmpty());
        // The shorter authorized segment is above ground: activation may occur there.
        assertTrue(r.activation().isPresent());
    }
    @Test void invalidConfigurationNeverIntroducesDefaults() {
        for(double x:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new RecoveryConfiguration3DOF(x,0));
        for(double x:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->new RecoveryConfiguration3DOF(1,x));
        assertThrows(IllegalArgumentException.class,()->new RecoveryRunConfiguration3DOF(null,new RecoveryConfiguration3DOF(1,0),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
    }
    @Test void normalGuideExitSharesRecoveryContinuationAndPreservesPrefix() {
        var c=LaunchGuideTest.primary(NativeDemoTrajectoryTest.configuration(.01,5000),1.5,.5,.02);
        var legacy=new LaunchGuideSimulator3DOF().run(c);
        var r=new LaunchGuideSimulator3DOF().run(c,new RecoveryConfiguration3DOF(.05,0));
        assertEquals(Outcome.GROUND,r.outcome());assertTrue(r.activation().isPresent());
        var exit=r.guideTransitions().stream().filter(t->t.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END).findFirst().orElseThrow();
        assertEquals(legacy.guidanceEnd().orElseThrow().acceptedPoint(),exit.acceptedPoint());
        assertTrue(r.physicalApogee().orElseThrow().point().timeS()>exit.acceptedPoint().timeS());
        assertEquals(FlightPhase3DOF.PAD_CONTACT,r.samples().get(0).phase());
    }
    @Test void unsuccessfulGuideLaunchNeverTriggersRecovery() {
        var c=LaunchGuideTest.primary(LaunchGuideTest.stationary(LaunchGuideTest.pulse(3,.05,40)),2,.5,.02);
        var r=new LaunchGuideSimulator3DOF().run(c,new RecoveryConfiguration3DOF(.05,0));
        assertEquals(Outcome.GUIDE_NOT_RELEASED,r.outcome());assertTrue(r.recoveryEvents().isEmpty());
    }
    @Test void stepLimitAndRepeatabilityPreserveSingleDeploymentAndImmutableResults() {
        var base=coast(.02,20,20,false);var a=run(base,0);var b=run(base,0);
        assertEquals(a,b);assertEquals(20,a.stepsPerformed());assertEquals(21,a.samples().size());
        assertEquals(Outcome.MAXIMUM_STEPS,a.outcome());assertTrue(a.activation().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->a.samples().clear());
        assertEquals(run(coast(.02,2000,20,false),0),run(coast(.02,2000,20,false),0));
    }
    @Test void sampledDetectionAndActivationTimingSensitivity() {
        for(double h:new double[]{.08,.04,.005}) {
            var r=run(coast(h,3000,20,false),.25);
            var detection=r.physicalApogee().orElseThrow();
            double max=Double.NEGATIVE_INFINITY;
            for(var sample:r.samples()) {
                if(sample.point().timeS()>detection.point().timeS())break;
                double z=sample.point().state().positionWorldM().get(2);
                max=Math.max(max,z);
                boolean qualifies=z<max-.01;
                if(sample.point().equals(detection.point()))assertTrue(qualifies);
                else assertFalse(qualifies);
            }
            assertEquals(detection.point().timeS()+.25,r.activation().orElseThrow().point().timeS());
            var legacy=new SimulationEngine3DOF().run(coast(h,3000,20,false));
            assertTrue(legacy.recordedData().samples().stream().anyMatch(s->s.point().equals(detection.point())));
            assertEquals(legacy.recordedData().events().stream().filter(e->e.eventType()==FlightEventType.APOGEE).toList(),
                r.legacyEvents().stream().filter(e->e.eventType()==FlightEventType.APOGEE).toList());
            System.out.println("RECOVERY_SAMPLED_TIMING h="+h+" detection="+detection.point().timeS()+" activation="+r.activation().orElseThrow().point().timeS());
        }
    }
    @Test void legacyRunAndSchemasRemainIndependentOfRecoveryOptIn() {
        var base=coast(.02,100,20,false);var engine=new SimulationEngine3DOF();var before=engine.run(base);
        run(base,0);assertEquals(before,engine.run(base));
        assertEquals(14,PhysicsEvaluationContext3DOF.class.getRecordComponents().length);
        assertEquals(13,PhysicsEvaluationResult3DOF.class.getRecordComponents().length);
        assertEquals(7,SimulationRunConfiguration3DOF.class.getRecordComponents().length);
    }
}
