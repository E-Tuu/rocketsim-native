package org.roketsim.core.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.roketsim.core.numerics.*;
import static org.roketsim.core.simulation.RecoveryFlightResult3DOF.*;

/** Per-run recovery state/one-segment handling. The existing engine owns the only flight loop. */
final class RecoveryRuntime3DOF {
    final SimulationRunInputs3DOF base;
    final RecoveryConfiguration3DOF configuration;
    final double[] masses,cgs;
    final PhysicsEvaluator3DOF physics=new PhysicsEvaluator3DOF();
    final List<Sample> samples=new ArrayList<>();
    final List<FlightEventOccurrence3DOF> legacy=new ArrayList<>();
    final List<Event> events=new ArrayList<>();
    final List<LaunchGuideTransitionResult> guideTransitions;
    State state=State.NOT_TRIGGERED;
    boolean ascentSeen;
    double maximumSampledAltitudeM,maximumSampledAltitudeTimeS;
    static final class TimingFailure extends IllegalArgumentException {
        private final String code;
        TimingFailure(String code) {super(code);this.code=code;}
        String code() {return code;}
    }
    Double parentEndS,triggerTimeS,activationTimeS;
    Outcome outcome;
    Failure failure;
    double terminationTimeS;
    int completedSteps;
    final GroundRunExecution3DOF groundExecution;

    RecoveryRuntime3DOF(SimulationRunConfiguration3DOF base,RecoveryConfiguration3DOF configuration,
            double[] masses,double[] cgs,IntegrationPoint3DOF initial,Double firstEnd,
            List<LaunchGuideEvaluation> guidePrefix,List<FlightEventOccurrence3DOF> previousEvents,
            List<LaunchGuideTransitionResult> guideTransitions) {
        this(base,configuration,masses,cgs,initial,firstEnd,guidePrefix,previousEvents,guideTransitions,null);
    }
    RecoveryRuntime3DOF(SimulationRunConfiguration3DOF base,RecoveryConfiguration3DOF configuration,
            double[] masses,double[] cgs,IntegrationPoint3DOF initial,Double firstEnd,
            List<LaunchGuideEvaluation> guidePrefix,List<FlightEventOccurrence3DOF> previousEvents,
            List<LaunchGuideTransitionResult> guideTransitions,GroundRunExecution3DOF groundExecution) {
        this(SimulationRunInputs3DOF.legacy(base),configuration,masses,cgs,initial,firstEnd,guidePrefix,previousEvents,guideTransitions,groundExecution);
    }
    RecoveryRuntime3DOF(SimulationRunInputs3DOF base,RecoveryConfiguration3DOF configuration,
            double[] masses,double[] cgs,IntegrationPoint3DOF initial,Double firstEnd,
            List<LaunchGuideEvaluation> guidePrefix,List<FlightEventOccurrence3DOF> previousEvents,
            List<LaunchGuideTransitionResult> guideTransitions,GroundRunExecution3DOF groundExecution) {
        this.groundExecution=groundExecution;
        this.base=base;this.configuration=configuration;this.masses=masses;this.cgs=cgs;
        this.parentEndS=firstEnd;this.guideTransitions=List.copyOf(guideTransitions);legacy.addAll(previousEvents);
        for(var e:guidePrefix)samples.add(new Sample(e.point(),Physics.baseline(e.unconstrainedPhysics()),e.clock().flightPhase(),State.NOT_TRIGGERED));
        if(samples.isEmpty())record(initial);
        ascentSeen=initial.state().velocityWorldMS().get(2)>0; // Eligible entry only; no guided ascent history.
        maximumSampledAltitudeM=initial.state().positionWorldM().get(2);
        maximumSampledAltitudeTimeS=initial.timeS();
        terminationTimeS=initial.timeS();
    }
    Physics evaluate(IntegrationPoint3DOF p,boolean active) {
        if(configuration==null)return Physics.baseline(physics.evaluateInputs(p.timeS(),p.state(),base.launchConditions(),base.physicsContext(),masses,cgs));
        return physics.recoveryAt(p.timeS(),p.state(),base.launchConditions(),base.physicsContext(),masses,cgs,configuration,active);
    }
    void record(IntegrationPoint3DOF p) {
        store(p,evaluate(p,state==State.ACTIVE));
    }
    private void store(IntegrationPoint3DOF p,Physics evaluated) {
        var sample=new Sample(p,evaluated,state==State.ACTIVE?FlightPhase3DOF.RECOVERY_DESCENT:FlightPhase3DOF.FREE_FLIGHT,state);
        if(groundExecution!=null)groundExecution.beforeCommit(p);
        storePrepared(sample);
    }
    private void storePrepared(Sample sample) {
        var p=sample.point();
        if(!samples.isEmpty() && samples.get(samples.size()-1).point().timeS()==p.timeS()) {
            if(!samples.get(samples.size()-1).point().equals(p))throw new IllegalArgumentException("same-time state replacement is forbidden");
            samples.set(samples.size()-1,sample); // One boundary point, post-transition physics metadata.
        } else samples.add(sample);
        terminationTimeS=p.timeS();
        if(groundExecution!=null)groundExecution.committed(sample);
    }
    void fail(String code,String message,Map<String,Double> details,Outcome kind) {
        outcome=kind;failure=new Failure(code,message,details);
    }
    boolean activate(IntegrationPoint3DOF current) {
        if(state!=State.PENDING || current.timeS()!=activationTimeS)return true;
        double thrust=evaluate(current,false).motorThrustState().thrustN();
        if(groundExecution!=null)groundExecution.checkpoint();
        if(thrust>0) {
            fail("RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST","Recovery activation rejected before applying parachute drag: authoritative motor thrust is positive",
                Map.of("activation_time_s",current.timeS(),"apogee_trigger_time_s",triggerTimeS,
                    "deployment_delay_s",configuration.deploymentDelayS(),"motor_thrust_N",thrust),Outcome.UNSUPPORTED);
            return false;
        }
        Physics activePhysics=groundExecution==null?null:evaluate(current,true);
        if(groundExecution!=null)groundExecution.beforeCommit(current);
        state=State.ACTIVE;
        events.add(new Event(EventType.RECOVERY_ACTIVATION,current,OptionalDouble.empty(),current.timeS(),current.timeS()));
        if(groundExecution==null)record(current);else storePrepared(new Sample(current,activePhysics,FlightPhase3DOF.RECOVERY_DESCENT,state));
        return true;
    }
    private static double duration(double start,double end,double nominal) {
        if(start+nominal==end)return nominal;
        double h=end-start;
        if(!Double.isFinite(h)||h<=0||h>nominal||start+h!=end)
            throw new TimingFailure("RECOVERY_BOUNDARY_UNREPRESENTABLE");
        return h;
    }
    static double activationTime(double trigger,double delay) {
        double activation=trigger+delay;
        if(!Double.isFinite(trigger)||!Double.isFinite(delay)||delay<0||!Double.isFinite(activation)
                || (delay>0 && activation<=trigger))
            throw new TimingFailure("INVALID_RECOVERY_ACTIVATION_TIME");
        return activation;
    }
    boolean terminal(List<FlightEventOccurrence3DOF> reports,IntegrationPoint3DOF candidate) {
        var ground=reports.stream().filter(FlightEventOccurrence3DOF::isTerminal).findFirst().orElse(null);
        if(ground==null)return false;
        Physics endpoint=groundExecution!=null && ground.interpolationFraction()==1?evaluate(candidate,state==State.ACTIVE):null;
        if(groundExecution!=null)groundExecution.beforeCommit(ground.interpolationFraction()==1?candidate:groundExecution.last.point());
        for(var e:reports)if(e.timeS()<=ground.timeS())legacy.add(e);
        outcome=Outcome.GROUND;
        if(ground.interpolationFraction()==1) {
            if(groundExecution==null)record(candidate);else storePrepared(new Sample(candidate,endpoint,state==State.ACTIVE?FlightPhase3DOF.RECOVERY_DESCENT:FlightPhase3DOF.FREE_FLIGHT,state));
        }
        terminationTimeS=ground.timeS();return true;
    }
    /** Returns at most one accepted positive-duration segment, or null on explicit termination. */
    IntegrationPoint3DOF advance(IntegrationPoint3DOF current,double nominal,MotorTimeBoundarySchedule3DOF schedule,
            FlightEventDetector3DOF detector,FlightEventDetectionContext3DOF eventContext) {
        try {
            if(groundExecution!=null)groundExecution.checkpoint();
            if(!activate(current))return null;
            if(parentEndS==null) {
                double h=schedule==null?nominal:alignedStep(schedule,current.timeS(),nominal);
                parentEndS=current.timeS()+h;
            }
            double end=parentEndS;
            if(state==State.PENDING && activationTimeS<end)end=activationTimeS;
            double h=duration(current.timeS(),end,nominal);
            if(schedule!=null)h=alignedStep(schedule,current.timeS(),h);
            final boolean active=state==State.ACTIVE; // Fixed for ALL stages, including endpoint k4.
            DerivativeFunction3DOF derivative=(t,s)->configuration==null?
                physics.evaluateInputs(t,s,base.launchConditions(),base.physicsContext(),masses,cgs).dynamics().derivative():
                physics.recoveryAt(t,s,base.launchConditions(),base.physicsContext(),masses,cgs,configuration,active).dynamics().derivative();
            var candidate=new ClassicalRK4Integrator3DOF().step(current,new FixedStepConfig(h),derivative);
            if(groundExecution!=null) {
                groundExecution.checkpoint();groundExecution.validateTrial(current,candidate);
            }
            // Screen on this actual segment, never order an earlier long-step ground estimate as exact.
            var reports=detector.detect(current,candidate,eventContext);
            if(terminal(reports,candidate))return reports.stream().anyMatch(e->e.isTerminal()&&e.interpolationFraction()==1)?candidate:null;
            Physics endpoint=groundExecution==null?null:evaluate(candidate,active);
            if(groundExecution!=null)groundExecution.beforeCommit(candidate);
            observeAccepted(candidate);
            legacy.addAll(reports);
            if(groundExecution==null)record(candidate);else storePrepared(new Sample(candidate,endpoint,state==State.ACTIVE?FlightPhase3DOF.RECOVERY_DESCENT:FlightPhase3DOF.FREE_FLIGHT,state));
            activate(candidate); // Zero delay uses this accepted boundary, including at the step cap.
            if(candidate.timeS()==parentEndS)parentEndS=null;
            return candidate;
        } catch(TimingFailure unresolved) {
            fail(unresolved.code(),"Recovery timing boundary unresolved; no deployment is inferred",
                Map.of("last_accepted_time_s",current.timeS()),Outcome.NUMERICAL_FAILURE);return null;
        }
    }
    private double alignedStep(MotorTimeBoundarySchedule3DOF schedule,double time,double step) {
        return groundExecution==null?schedule.effectiveStepSizeS(time,step):schedule.effectiveStepSizeForExecutionS(time,step);
    }

    /** Only accepted eligible free-flight samples enter the frozen altitude-drop monitor. */
    void observeAccepted(IntegrationPoint3DOF point) {
        if(configuration==null || state!=State.NOT_TRIGGERED)return;
        double altitude=point.state().positionWorldM().get(2);
        if(!ascentSeen) {
            if(point.state().velocityWorldMS().get(2)<=0)return;
            ascentSeen=true;
            maximumSampledAltitudeM=altitude;maximumSampledAltitudeTimeS=point.timeS();
            return;
        }
        if(altitude>maximumSampledAltitudeM) {
            maximumSampledAltitudeM=altitude;maximumSampledAltitudeTimeS=point.timeS();
        }
        if(altitude<maximumSampledAltitudeM-0.01) {
            double activation=activationTime(point.timeS(),configuration.deploymentDelayS());
            triggerTimeS=point.timeS();activationTimeS=activation;state=State.PENDING;
            // Historical development name denotes sampled detection, not a physical root.
            events.add(new Event(EventType.PHYSICAL_APOGEE,point,OptionalDouble.of(activation),point.timeS(),point.timeS()));
        }
    }
    RecoveryFlightResult3DOF finish(IntegrationPoint3DOF current,int steps) {
        if(outcome==null) {outcome=Outcome.MAXIMUM_STEPS;terminationTimeS=current.timeS();}
        return new RecoveryFlightResult3DOF(samples,legacy,events,outcome,terminationTimeS,steps,Optional.ofNullable(failure),guideTransitions);
    }
    static RecoveryFlightResult3DOF unreleased(LaunchGuideRunResult3DOF guide,double ignitionTimeS) {
        var prefix=guide.guideSamples().stream().map(e->new Sample(e.point(),Physics.baseline(e.unconstrainedPhysics()),e.clock().flightPhase(),State.NOT_TRIGGERED)).toList();
        double time=prefix.isEmpty()?ignitionTimeS:prefix.get(prefix.size()-1).point().timeS();
        return new RecoveryFlightResult3DOF(prefix,guide.recordedData().events(),List.of(),Outcome.GUIDE_NOT_RELEASED,time,
            Math.max(0,prefix.size()-1),Optional.of(new Failure("GUIDE_"+guide.outcome().name(),"Recovery free flight was never entered",Map.of())),guide.transitions());
    }
}
