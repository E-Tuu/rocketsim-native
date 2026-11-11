package org.roketsim.core.simulation;

import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.GroundTerminationTest;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import org.roketsim.core.numerics.*;
import static org.roketsim.core.simulation.RunControl3DOF.*;

class GroundExecutionTest {
    static IntegrationPoint3DOF point(double t,double z,double vz) {
        return new IntegrationPoint3DOF(t,new TranslationalState3DOF(new double[]{0,0,z},new double[]{0,0,vz}));
    }
    static FlightEventDetectionContext3DOF context(UntilGroundRunConfiguration3DOF c) {
        var b=c.vehicle().baseRun();return new FlightEventDetectionContext3DOF(b.launchConditions(),b.physicsContext().propulsionTimeline(),b.motorCurveStatistics(),b.eventDetectionProfile());
    }
    static List<FlightEventOccurrence3DOF> detect(UntilGroundRunConfiguration3DOF c,IntegrationPoint3DOF start,IntegrationPoint3DOF end) {
        return new FlightEventDetector3DOF().detect(start,end,context(c));
    }
    static GroundRunExecution3DOF execution(UntilGroundRunConfiguration3DOF c,IntegrationPoint3DOF start) {
        var g=new GroundRunExecution3DOF(c,GroundTerminationTest.control(p->{}));
        var b=c.vehicle().baseRun();g.runtime=new RecoveryRuntime3DOF(b,c.recovery().orElse(null),new double[0],new double[0],start,null,List.of(),List.of(),List.of(),g);
        return g;
    }
    @Test void analyticConstantVelocityInteriorContactUsesOnlyNat018Estimate() {
        var c=GroundTerminationTest.direct(2,1,Optional.empty());var start=point(5,3,-2);
        var end=new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(2),
            (t,s)->new TranslationalStateDerivative3DOF(s.velocityWorldMS(),Vector.of(new double[]{0,0,0})));
        assertEquals(-1,end.state().positionWorldM().get(2));
        var reports=detect(c,start,end);var event=reports.stream().filter(FlightEventOccurrence3DOF::isTerminal).findFirst().orElseThrow();
        assertEquals(.75,event.interpolationFraction());assertEquals(6.5,event.timeS());assertEquals(0,event.estimatedState().positionWorldM().get(2));assertEquals(-2,event.estimatedState().velocityWorldMS().get(2));
        var g=execution(c,start);assertTrue(g.runtime.terminal(reports,end));g.captureRuntimeOutcome();
        var r=g.finish();assertEquals(Status.GROUND,r.terminationReason());assertEquals(start,r.lastAcceptedSample().orElseThrow().point());
        assertEquals(0,r.acceptedSegments());assertNotEquals(event.estimatedState(),r.lastAcceptedSample().orElseThrow().point().state());
    }
    @Test void constantAccelerationInteriorGroundIsLinearEstimateNotPhysicalImpactRoot() {
        var c=GroundTerminationTest.direct(4,1,Optional.empty());var start=point(5,10,0);
        var end=new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(4),
            (t,s)->new TranslationalStateDerivative3DOF(s.velocityWorldMS(),Vector.of(new double[]{0,0,-2})));
        assertEquals(-6,end.state().positionWorldM().get(2));assertEquals(-8,end.state().velocityWorldMS().get(2));
        var e=detect(c,start,end).stream().filter(FlightEventOccurrence3DOF::isTerminal).findFirst().orElseThrow();
        assertEquals(.625,e.interpolationFraction());assertEquals(7.5,e.timeS());assertEquals(-5,e.estimatedState().velocityWorldMS().get(2));
        assertNotEquals(5+Math.sqrt(10),e.timeS());
    }
    @Test void exactGroundEndpointIsAcceptedBeforeCoincidentRecoveryActivation() {
        var c=GroundTerminationTest.direct(2,1,Optional.of(new RecoveryConfiguration3DOF(.05,2)));var start=point(5,4,0);
        var end=new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(2),
            (t,s)->new TranslationalStateDerivative3DOF(s.velocityWorldMS(),Vector.of(new double[]{0,0,-2})));
        assertEquals(0,end.state().positionWorldM().get(2));assertEquals(-4,end.state().velocityWorldMS().get(2));
        var g=execution(c,start);g.runtime.state=RecoveryFlightResult3DOF.State.PENDING;g.runtime.triggerTimeS=5.;g.runtime.activationTimeS=7.;
        assertTrue(g.runtime.terminal(detect(c,start,end),end));g.captureRuntimeOutcome();var r=g.finish();
        assertEquals(Status.GROUND,r.terminationReason());assertEquals(end,r.lastAcceptedSample().orElseThrow().point());assertEquals(1,r.acceptedSegments());
        assertEquals(RecoveryFlightResult3DOF.State.PENDING,r.recoveryState());assertTrue(r.recoveryEvents().isEmpty());
    }
    @Test void cancellationBeforeTrialCommitDiscardsTrialAndItsReportingEvents() {
        var ref=new AtomicReference<RunControl3DOF>();var commits=new AtomicInteger();
        var ctl=new RunControl3DOF(new MonitoringPolicy(1,10000,1000,1000),p->{},w->{},System::nanoTime,
            checkpoint->{if(checkpoint.equals("before_commit") && commits.incrementAndGet()==1)ref.get().requestCancellation();});ref.set(ctl);
        var r=new SimulationEngine3DOF().runUntilGround(GroundTerminationTest.direct(.02,1,Optional.empty()),ctl);
        assertEquals(Status.CANCELLED,r.terminationReason());assertEquals(0,r.acceptedSegments());assertEquals(1,r.samples().size());assertTrue(r.events().isEmpty());
    }
    @Test void cancellationInsideGuideBoundarySearchKeepsOnlyCommittedPrefix() {
        var base=GroundTerminationTest.direct(.02,1,Optional.empty());
        var guide=new LaunchGuideRunConfiguration3DOF(base.vehicle(),
            new LaunchGuideGeometry("test",LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL,1.5,"synthetic geometry"),
            new GuideAttachmentGeometry("test",.5,.02,"synthetic contacts"),LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE,
            IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        var ref=new AtomicReference<RunControl3DOF>();var evaluations=new AtomicInteger();
        var ctl=new RunControl3DOF(new MonitoringPolicy(1,10000,1000,1000),p->{},w->{},System::nanoTime,
            checkpoint->{if(checkpoint.equals("guide_evaluation") && evaluations.incrementAndGet()==25)ref.get().requestCancellation();});ref.set(ctl);
        var r=new SimulationEngine3DOF().runUntilGround(new UntilGroundRunConfiguration3DOF(guide.vehicle(),Optional.of(guide),Optional.empty(),guide.alignmentMode()),ctl);
        assertEquals(Status.CANCELLED,r.terminationReason());assertEquals(25,evaluations.get());
        assertEquals(r.samples().size()-1,r.acceptedSegments());
        assertTrue(r.guideTransitions().stream().noneMatch(e->e.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END));
        assertTrue(r.samples().stream().allMatch(sample->sample.point().state().positionWorldM().get(2)>=0));
        assertTrue(r.events().stream().allMatch(e->e.timeS()<=r.lastAcceptedSample().orElseThrow().point().timeS()));
    }
    @Test void historyIndexCapacityIsControlledWithoutAllocationOrTruncation() {
        var g=execution(GroundTerminationTest.direct(.02,1,Optional.empty()),point(0,1,1));
        var original=g.runtime;g.runtime=null;
        g.guideSamples=new AbstractList<LaunchGuideEvaluation>() {
            public int size() {return Integer.MAX_VALUE;}
            public LaunchGuideEvaluation get(int index) {throw new AssertionError("capacity check must not read fabricated entries");}
        };
        var e=assertThrows(GroundRunExecution3DOF.ExecutionFailure.class,()->g.beforeCommit(point(1,2,1)));
        assertEquals(Status.RESOURCE_FAILURE,e.status);assertEquals(1,original.samples.size());
    }
    static UntilGroundRunConfiguration3DOF shifted(double ignition,double h) {
        var c=GroundTerminationTest.direct(h,1,Optional.empty());var b=c.vehicle().baseRun();var x=b.physicsContext();
        var shifted=new PhysicsEvaluationContext3DOF(x.resolvedGeometry(),x.aerodynamicSurfaces(),x.structuralMassProperties(),x.motorInstallation(),
            x.motorPropertyModelProfile(),x.basicDragModelProfile(),x.translationalDynamicsModelProfile(),x.environmentPositionMappingModel(),
            x.launchEnvironmentAltitudeM(),new PropulsionTimeline(ignition),x.atmosphereModel(),x.airPropertiesCalculator(),x.gravityModel(),x.windModel());
        var base=new SimulationRunConfiguration3DOF(b.launchConditions(),shifted,b.motorCurveStatistics(),b.eventDetectionProfile(),b.fixedStepConfig(),b.runLimits(),b.terminalEventHandlingModel());
        return new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),Optional.empty(),Optional.empty(),c.alignmentMode());
    }
    @Test void actualOverflowedNominalEndpointReturnsNumericalFailure() {
        var r=new SimulationEngine3DOF().runUntilGround(shifted(Double.MAX_VALUE,Double.MAX_VALUE),GroundTerminationTest.control(p->{}));
        assertEquals(Status.NUMERICAL_FAILURE,r.terminationReason());assertTrue(r.groundEvent().isEmpty());assertEquals(0,r.acceptedSegments());
        assertEquals("NONFINITE_RK4_STAGE_TIME",r.failure().orElseThrow().code());
    }
    @Test void actualCollapsedNominalTimeDoesNotAppendAnUnadvancedSample() {
        var r=new SimulationEngine3DOF().runUntilGround(shifted(1e16,.01),GroundTerminationTest.control(p->{}));
        assertEquals(Status.NUMERICAL_FAILURE,r.terminationReason());assertEquals("NONADVANCING_INTEGRATION_TIME",r.failure().orElseThrow().code());
        assertEquals(1,r.samples().size());assertEquals(0,r.acceptedSegments());assertTrue(r.groundEvent().isEmpty());
    }
    @Test void warningObservationsDoNotChangeAnyAcceptedPointOrForce() {
        var config=GroundTerminationTest.direct(.02,1,Optional.empty());var reference=new SimulationEngine3DOF().runUntilGround(config,GroundTerminationTest.control(p->{}));
        var warnings=new ArrayList<Warning>();var progress=new ArrayList<Progress>();var ticks=new AtomicLong();
        var ctl=new RunControl3DOF(new MonitoringPolicy(10,30,.02,3),progress::add,warnings::add,()->ticks.getAndAdd(10));
        var r=new SimulationEngine3DOF().runUntilGround(config,ctl);
        assertEquals(Status.GROUND,r.terminationReason());assertEquals(reference.samples(),r.samples());assertEquals(reference.events(),r.events());
        assertEquals(Set.of("LONG_COMPUTATION","LONG_OBSERVED_AIRBORNE_TIME","GROWING_FLIGHT_HISTORY"),warnings.stream().map(Warning::code).collect(java.util.stream.Collectors.toSet()));
        assertTrue(warnings.stream().allMatch(w->w.observation().executionStatus()==Status.RUNNING));
        for(var p:progress)assertTrue(r.samples().stream().anyMatch(s->s.point().timeS()==p.simulatedFlightTimeS() && s.point().state().velocityWorldMS().get(2)==p.verticalVelocityWorldMS()));
    }
    @Test void progressRateLimitAndFinalNotificationAreIndependentOfRk4Stages() {
        var progress=new ArrayList<Progress>();var ticks=new AtomicLong();
        var ctl=new RunControl3DOF(new MonitoringPolicy(100,10000,1000,10000),progress::add,w->{},()->ticks.getAndAdd(10));
        var r=new SimulationEngine3DOF().runUntilGround(GroundTerminationTest.direct(.02,1,Optional.empty()),ctl);
        assertEquals(Status.GROUND,r.terminationReason());assertTrue(progress.size()<r.acceptedSegments()/2);
        for(int i=1;i<progress.size()-1;i++)assertTrue(progress.get(i).elapsedComputationNanos()-progress.get(i-1).elapsedComputationNanos()>=100);
        assertEquals(Status.GROUND,progress.get(progress.size()-1).executionStatus());
    }
    @Test void finiteCounterOverflowIsExplicitBeforeAnAcceptedAppend() {
        var g=execution(GroundTerminationTest.direct(.02,1,Optional.empty()),point(0,1,1));g.acceptedSegments=Long.MAX_VALUE;
        var e=assertThrows(GroundRunExecution3DOF.ExecutionFailure.class,()->g.beforeCommit(point(1,2,1)));
        assertEquals(Status.NUMERICAL_FAILURE,e.status);assertEquals("ACCEPTED_SEGMENT_COUNTER_OVERFLOW",e.code);
        assertEquals(1,g.runtime.samples.size());assertEquals(Long.MAX_VALUE,g.acceptedSegments);
    }
    @Test void belowGroundInitialReferenceAndNoPositiveHeightTrialAreExplicitlyUnsupported() {
        var c=GroundTerminationTest.direct(.02,1,Optional.empty());var g=new GroundRunExecution3DOF(c,GroundTerminationTest.control(p->{}));
        assertEquals(Status.UNSUPPORTED,assertThrows(GroundRunExecution3DOF.ExecutionFailure.class,()->g.validateInitial(point(0,-1,1))).status);
        assertEquals(Status.UNSUPPORTED,assertThrows(GroundRunExecution3DOF.ExecutionFailure.class,()->g.validateTrial(point(0,0,0),point(1,-1,-1))).status);
    }
    @Test void genuineNonadvancingTimeIsNumericalFailureWithoutFakeGround() {
        var c=GroundTerminationTest.direct(.02,1,Optional.empty());var g=new GroundRunExecution3DOF(c,GroundTerminationTest.control(p->{}));
        var e=assertThrows(GroundRunExecution3DOF.ExecutionFailure.class,()->g.validateTrial(point(1e16,1,0),point(1e16+.01,1,0)));
        g.failed(e);var result=g.finish();assertEquals(Status.NUMERICAL_FAILURE,result.terminationReason());assertTrue(result.groundEvent().isEmpty());
    }
    @Test void invalidGroundInterpolationIsNumericalFailureAndNeverGround() {
        var c=GroundTerminationTest.direct(.02,1,Optional.empty());
        var failure=assertThrows(EventDetectionError.class,()->detect(c,point(0,Double.MAX_VALUE,0),point(1,-Double.MAX_VALUE,-1)));
        var g=new GroundRunExecution3DOF(c,GroundTerminationTest.control(p->{}));g.failed(failure);var r=g.finish();
        assertEquals(Status.NUMERICAL_FAILURE,r.terminationReason());assertEquals(failure.errorCode(),r.failure().orElseThrow().code());assertTrue(r.groundEvent().isEmpty());
    }
    @Test void monitoringCallbackFailurePreservesInitialHistoryAndHasSeparateOutcome() {
        var ctl=new RunControl3DOF(new MonitoringPolicy(1,1000,100,100),p->{throw new IllegalStateException("synthetic observer failure");},w->{});
        var r=new SimulationEngine3DOF().runUntilGround(GroundTerminationTest.direct(.02,1,Optional.empty()),ctl);
        assertEquals(Status.MONITORING_FAILURE,r.terminationReason());assertEquals(1,r.samples().size());assertTrue(r.groundEvent().isEmpty());
    }
}
