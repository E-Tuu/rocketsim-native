package org.roketsim.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.simulation.*;
import static org.roketsim.core.simulation.RunControl3DOF.*;

/** NAT-036 functional execution, independent of legacy duration/step-budget policy. */
public class GroundTerminationTest {
    public static UntilGroundRunConfiguration3DOF direct(double h,int legacyLimit,Optional<RecoveryConfiguration3DOF> recovery) {
        return new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(NativeDemoTrajectoryTest.configuration(h,legacyLimit),List.of()),
            Optional.empty(),recovery,IntegrationStepAlignmentMode.LEGACY_FIXED_STEP);
    }
    public static RunControl3DOF control(java.util.function.Consumer<Progress> progress) {
        return new RunControl3DOF(new MonitoringPolicy(1,1_000_000_000_000L,1000,100_000),progress,w->{});
    }
    static GroundRunResult3DOF run(UntilGroundRunConfiguration3DOF c) {return new SimulationEngine3DOF().runUntilGround(c,control(p->{}));}
    static void ordered(GroundRunResult3DOF r) {
        for(int i=1;i<r.samples().size();i++)assertTrue(r.samples().get(i).point().timeS()>r.samples().get(i-1).point().timeS());
        assertEquals(r.samples().size()-1,r.acceptedSegments());
    }
    @Test void normalFlightIgnoresLegacyStepCapAndRetainsAuthoritativeGroundData() {
        var c=direct(.01,1,Optional.empty());var r=run(c);
        assertEquals(Status.GROUND,r.terminationReason(),r.failure().toString());
        assertTrue(r.acceptedSegments()>1);assertEquals(1,c.vehicle().baseRun().runLimits().maximumSteps());
        assertTrue(r.failure().isEmpty());assertEquals(RecoveryFlightResult3DOF.State.NOT_TRIGGERED,r.recoveryState());ordered(r);
        var e=r.groundEvent().orElseThrow();assertEquals(e.timeS(),r.estimatedGroundContactTimeS().orElseThrow());
        assertSame(e.estimatedState().positionWorldM(),r.estimatedGroundContactPositionWorldM().orElseThrow());
        assertSame(e.estimatedState().velocityWorldMS(),r.estimatedGroundContactVelocityWorldMS().orElseThrow());
        assertTrue(r.lastAcceptedSample().orElseThrow().point().timeS()<e.timeS());
        for(var s:r.samples())assertTrue(s.point().state().positionWorldM().get(2)>=0);
        assertTrue(e.estimatedState().velocityWorldMS().get(2)<0);
        var legacy=new SimulationEngine3DOF().run(NativeDemoTrajectoryTest.configuration(.01,5000));
        assertEquals(legacy.recordedData().events(),r.events());
        assertEquals(legacy.recordedData().samples().stream().map(RecordedFlightSample3DOF::point).toList(),r.samples().stream().map(RecoveryFlightResult3DOF.Sample::point).toList());
    }
    @Test void recoveryFlightCanExceedSixtySimulatedSecondsWithoutAnyDurationLimit() {
        var r=run(direct(.05,1,Optional.of(new RecoveryConfiguration3DOF(5,.25))));
        assertEquals(Status.GROUND,r.terminationReason(),r.failure().toString());
        assertTrue(r.estimatedGroundContactTimeS().orElseThrow()>60);assertTrue(r.acceptedSegments()>1);
        assertEquals(RecoveryFlightResult3DOF.State.ACTIVE,r.recoveryState());assertEquals(2,r.recoveryEvents().size());ordered(r);
        System.out.println("UNTIL_GROUND_LONG_FLIGHT seconds="+r.estimatedGroundContactTimeS().orElseThrow()+" accepted="+r.acceptedSegments());
    }
    @Test void groundModeRecoveryIsExactlyTheAcceptedNat035PathWhenBudgetDoesNotBind() {
        var c=direct(.02,5000,Optional.of(new RecoveryConfiguration3DOF(.05,.253)));
        var old=new SimulationEngine3DOF().run(new RecoveryRunConfiguration3DOF(c.vehicle(),c.recovery().orElseThrow(),c.alignmentMode()));
        var r=run(c);assertEquals(Status.GROUND,r.terminationReason());
        assertEquals(old.samples(),r.samples());assertEquals(old.legacyEvents(),r.events());assertEquals(old.recoveryEvents(),r.recoveryEvents());
    }
    @Test void groundDuringPendingDelayNeverActivates() {
        var r=run(direct(.02,1,Optional.of(new RecoveryConfiguration3DOF(.05,100))));
        assertEquals(Status.GROUND,r.terminationReason());assertEquals(RecoveryFlightResult3DOF.State.PENDING,r.recoveryState());
        assertEquals(1,r.recoveryEvents().size());
    }
    @Test void zeroDelayAndUnderThrustRejectionRemainDistinct() {
        var r=run(direct(.02,1,Optional.of(new RecoveryConfiguration3DOF(.05,0))));
        assertEquals(Status.GROUND,r.terminationReason());assertEquals(r.recoveryEvents().get(0).point(),r.recoveryEvents().get(1).point());
        var powered=RecoveryIntegrationTest.coast(.01,1,1,true);
        var unsupported=run(new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(powered,List.of()),Optional.empty(),
            Optional.of(new RecoveryConfiguration3DOF(.05,0)),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
        assertEquals(Status.UNSUPPORTED,unsupported.terminationReason());
        assertEquals("RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST",unsupported.failure().orElseThrow().code());
        assertTrue(Double.parseDouble(unsupported.failure().orElseThrow().details().get("motor_thrust_N"))>0);
        assertEquals(1,unsupported.recoveryEvents().size());ordered(unsupported);
    }
    @Test void cancellationBeforeFirstSegmentPreservesInitialSampleWithoutFakeGround() {
        var control=control(p->{});control.requestCancellation();
        var r=new SimulationEngine3DOF().runUntilGround(direct(.01,1,Optional.empty()),control);
        assertEquals(Status.CANCELLED,r.terminationReason());assertEquals(0,r.acceptedSegments());assertEquals(1,r.samples().size());
        assertTrue(r.events().isEmpty());assertTrue(r.groundEvent().isEmpty());ordered(r);
    }
    @Test void cancellationOnAcceptedProgressLeavesOrderedCompleteHistory() {
        var ref=new AtomicReference<RunControl3DOF>();var progress=new ArrayList<Progress>();
        var control=control(p->{progress.add(p);if(p.acceptedSegments()==10)ref.get().requestCancellation();});ref.set(control);
        var r=new SimulationEngine3DOF().runUntilGround(direct(.01,1,Optional.empty()),control);
        assertEquals(Status.CANCELLED,r.terminationReason());assertEquals(10,r.acceptedSegments());ordered(r);
        assertEquals(Status.CANCELLED,progress.get(progress.size()-1).executionStatus());
        for(var p:progress)assertTrue(r.samples().stream().anyMatch(s->s.point().timeS()==p.simulatedFlightTimeS()));
    }
    @Test void cancellationIsSafeFromAnotherThreadWithoutTimingOnlyRace() throws Exception {
        var reached=new CountDownLatch(1);var release=new CountDownLatch(1);
        var c=control(p->{if(p.executionStatus()==Status.RUNNING && p.acceptedSegments()==3) {
            reached.countDown();try {if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("test handshake timed out");}
            catch(InterruptedException e) {Thread.currentThread().interrupt();throw new AssertionError(e);}
        }});
        var executor=Executors.newSingleThreadExecutor();
        try {
            var future=executor.submit(()->new SimulationEngine3DOF().runUntilGround(direct(.01,1,Optional.empty()),c));
            assertTrue(reached.await(5,TimeUnit.SECONDS));c.requestCancellation();release.countDown();
            var r=future.get(5,TimeUnit.SECONDS);assertEquals(Status.CANCELLED,r.terminationReason());assertEquals(3,r.acceptedSegments());ordered(r);
        } finally {release.countDown();executor.shutdownNow();}
    }
    @Test void cancellationAfterCommittedGroundCannotReplaceLanding() {
        var ref=new AtomicReference<RunControl3DOF>();var c=control(p->{if(p.executionStatus()==Status.GROUND)ref.get().requestCancellation();});ref.set(c);
        var r=new SimulationEngine3DOF().runUntilGround(direct(.02,1,Optional.empty()),c);
        assertTrue(c.isCancellationRequested());assertEquals(Status.GROUND,r.terminationReason());assertTrue(r.groundEvent().isPresent());
    }
    @Test void guideHandoffSupportsOptionalRecoveryAndCountsAcceptedSegmentsWithoutLegacyCap() {
        for(boolean recovery:new boolean[]{false,true}) {
            var guide=LaunchGuideTest.primary(NativeDemoTrajectoryTest.configuration(.02,1),1.5,.5,.02);
            var c=new UntilGroundRunConfiguration3DOF(guide.vehicle(),Optional.of(guide),
                recovery?Optional.of(new RecoveryConfiguration3DOF(.05,.25)):Optional.empty(),guide.alignmentMode());
            var r=run(c);assertEquals(Status.GROUND,r.terminationReason(),r.failure().toString());ordered(r);
            assertEquals(1,r.guideTransitions().stream().filter(e->e.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END).count());
            assertEquals(FlightPhase3DOF.PAD_CONTACT,r.samples().get(0).phase());
            assertEquals(recovery?2:0,r.recoveryEvents().size());
        }
    }
    @Test void heldPadContinuesUntilCancelledInsteadOfPretendingToLandOrApplyingStepCap() {
        var guide=LaunchGuideTest.primary(LaunchGuideTest.stationary(LaunchGuideTest.pulse(3,.05,1)),2,.5,.02);
        var c=new UntilGroundRunConfiguration3DOF(guide.vehicle(),Optional.of(guide),Optional.empty(),guide.alignmentMode());
        var ref=new AtomicReference<RunControl3DOF>();var ctl=control(p->{if(p.acceptedSegments()>=8)ref.get().requestCancellation();});ref.set(ctl);
        var r=new SimulationEngine3DOF().runUntilGround(c,ctl);
        assertEquals(Status.CANCELLED,r.terminationReason());assertEquals(8,r.acceptedSegments());ordered(r);
        assertTrue(r.groundEvent().isEmpty());assertTrue(r.samples().stream().allMatch(s->s.phase()==FlightPhase3DOF.PAD_CONTACT));
    }
    @Test void unsupportedDownwardPlaneStartAndFirstBelowGroundTrialDoNotFabricateLanding() {
        for(double vz:new double[]{-1,0}) {
            var base=RecoveryIntegrationTest.coast(.02,1,vz,false);
            var r=run(new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),Optional.empty(),Optional.empty(),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
            assertEquals(Status.UNSUPPORTED,r.terminationReason());assertTrue(r.groundEvent().isEmpty());assertEquals(0,r.acceptedSegments());
        }
    }
    @Test void modelDomainFailureIsNotCancellationOrGround() {
        var c=direct(.01,1,Optional.empty());
        var base=(SimulationRunConfiguration3DOF)VehicleTestData.replace(c.vehicle().baseRun(),"physics_context.launch_environment_altitude_m",100000.);
        var r=run(new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),Optional.empty(),Optional.empty(),c.alignmentMode()));
        assertEquals(Status.MODEL_DOMAIN_FAILURE,r.terminationReason());assertTrue(r.groundEvent().isEmpty());assertTrue(r.failure().isPresent());
    }
    @Test void nonzeroWorldLaunchPositionKeepsGroundReferenceAndProgressRelative() {
        var base=NativeDemoTrajectoryTest.configuration(.02,1);
        base=(SimulationRunConfiguration3DOF)VehicleTestData.replace(base,"launch_conditions",
            new LaunchConditions3DOF(new double[]{100,-200,50},new double[]{0,0,0},new double[]{0,0,1}));
        var progress=new ArrayList<Progress>();
        var r=new SimulationEngine3DOF().runUntilGround(new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),
            Optional.empty(),Optional.empty(),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP),control(progress::add));
        assertEquals(Status.GROUND,r.terminationReason());
        assertEquals(100,r.estimatedGroundContactPositionWorldM().orElseThrow().get(0));assertEquals(-200,r.estimatedGroundContactPositionWorldM().orElseThrow().get(1));
        assertEquals(50,r.estimatedGroundContactPositionWorldM().orElseThrow().get(2));
        for(var p:progress)assertTrue(r.samples().stream().anyMatch(sample->sample.point().timeS()==p.simulatedFlightTimeS()
            && sample.point().state().positionWorldM().get(2)-50==p.groundRelativeAltitudeM()));
    }
    @Test void unusedParentGroundEstimateCannotOverrideAuthorizedActivationSegment() {
        var base=RecoveryIntegrationTest.coast(.02,2000,20,false);
        var pending=RecoveryIntegrationTest.run(base,100);
        double delay=pending.terminationTimeS()-pending.physicalApogee().orElseThrow().point().timeS();
        var accepted=RecoveryIntegrationTest.run(base,delay);
        var r=run(new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),Optional.empty(),
            Optional.of(new RecoveryConfiguration3DOF(.05,delay)),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
        assertEquals(Status.GROUND,r.terminationReason());assertTrue(r.failure().isEmpty());
        assertEquals(accepted.samples(),r.samples());assertEquals(accepted.recoveryEvents(),r.recoveryEvents());
    }
    @Test void explicitMonitoringPolicyAndConfigurationNeverIntroduceDurationEtaOrPercentage() {
        assertEquals(List.of("vehicle","launchGuide","recovery","alignmentMode"),Arrays.stream(UntilGroundRunConfiguration3DOF.class.getRecordComponents()).map(c->c.getName()).toList());
        assertEquals(List.of("simulatedFlightTimeS","elapsedComputationNanos","acceptedSegments","groundRelativeAltitudeM","verticalVelocityWorldMS","flightPhase","recoveryState","executionStatus"),
            Arrays.stream(Progress.class.getRecordComponents()).map(c->c.getName()).toList());
        assertThrows(IllegalArgumentException.class,()->new MonitoringPolicy(0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new MonitoringPolicy(1,0,1,1));
        assertThrows(IllegalArgumentException.class,()->new MonitoringPolicy(1,1,Double.NaN,1));
        assertThrows(IllegalArgumentException.class,()->new MonitoringPolicy(1,1,1,0));
        var c=direct(.02,1,Optional.empty());var ctl=control(p->{});ctl.requestCancellation();
        new SimulationEngine3DOF().runUntilGround(c,ctl);
        assertThrows(IllegalArgumentException.class,()->new SimulationEngine3DOF().runUntilGround(c,ctl));
    }
    @Test void legacyRunStillUsesItsOwnLimitAfterGroundModeRuns() {
        var c=direct(.02,1,Optional.empty());var engine=new SimulationEngine3DOF();var before=engine.run(c.vehicle().baseRun());
        run(c);assertEquals(before,engine.run(c.vehicle().baseRun()));assertEquals(1,before.stepsPerformed());
        assertEquals(SimulationTerminationReason.MAXIMUM_STEPS_REACHED,before.terminationReason());
    }
}
