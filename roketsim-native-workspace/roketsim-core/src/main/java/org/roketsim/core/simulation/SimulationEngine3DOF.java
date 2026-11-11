package org.roketsim.core.simulation;
import org.roketsim.core.dynamics.InitialStateBuilder;
import org.roketsim.core.dynamics.TranslationalState3DOF;
import org.roketsim.core.numerics.*;
import java.util.List;

/** Reusable stateless lifecycle. Every run owns fresh local history and evaluators. */
public final class SimulationEngine3DOF {
    /** Explicit source-backed policy; initialization only, followed by the same ground/flight lifecycle. */
    public GroundRunResult3DOF runSourceUntilGround(SourceMotorGroundRunConfiguration3DOF configuration,RunControl3DOF control) {
        if(configuration==null || control==null)throw new IllegalArgumentException("configuration and control must not be null");
        var base=SimulationRunInputs3DOF.source(configuration);
        var execution=new GroundRunExecution3DOF(GroundConfigurationInputs3DOF.source(configuration,base),control);
        try {
            var initial=new IntegrationPoint3DOF(base.physicsContext().propulsionTimeline().ignitionTimeS(),new InitialStateBuilder().build(base.launchConditions()));
            execution.validateInitial(initial);
            if(configuration.launchGuide().isPresent())new LaunchGuideSimulator3DOF().runRuntimeUntilGround(GuideRunInputs3DOF.source(configuration,base),execution);
            else {
                var hardware=configuration.attachedHardware();
                double[] masses=new double[hardware.size()],cgs=new double[hardware.size()];
                for(int i=0;i<hardware.size();i++) {masses[i]=hardware.get(i).massKg();cgs[i]=hardware.get(i).cgXGeoM();}
                var schedule=configuration.alignmentMode()==IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES?
                    MotorTimeBoundarySchedule3DOF.fromInputs(base.physicsContext(),base.motorCurveStatistics()):null;
                continueUntilGroundInputs(base,masses,cgs,schedule,initial,null,List.of(),List.of(),List.of(),execution);
            }
        } catch(RuntimeException failure) {execution.failed(failure);}
        return execution.finish();
    }

    /** Explicit uncapped execution; composed legacy limits remain authoritative only for legacy run methods. */
    public GroundRunResult3DOF runUntilGround(UntilGroundRunConfiguration3DOF configuration,RunControl3DOF control) {
        if(configuration==null || control==null)throw new IllegalArgumentException("configuration and control must not be null");
        var execution=new GroundRunExecution3DOF(configuration,control);
        try {
            var base=configuration.vehicle().baseRun();
            SimulationRunConfiguration3DOF.validateTerminal(base.terminalEventHandlingModel());
            var initial=new IntegrationPoint3DOF(base.physicsContext().propulsionTimeline().ignitionTimeS(),new InitialStateBuilder().build(base.launchConditions()));
            execution.validateInitial(initial);
            if(configuration.launchGuide().isPresent())new LaunchGuideSimulator3DOF().runUntilGround(configuration.launchGuide().orElseThrow(),execution);
            else {
                var hardware=configuration.vehicle().attachedHardware();
                double[] masses=new double[hardware.size()],cgs=new double[hardware.size()];
                for(int i=0;i<hardware.size();i++) {masses[i]=hardware.get(i).massKg();cgs[i]=hardware.get(i).cgXGeoM();}
                var schedule=configuration.alignmentMode()==IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES?
                    MotorTimeBoundarySchedule3DOF.fromContext(base.physicsContext(),base.motorCurveStatistics()):null;
                continueUntilGround(base,masses,cgs,schedule,initial,null,List.of(),List.of(),List.of(),execution);
            }
        } catch(RuntimeException failure) {execution.failed(failure);}
        return execution.finish();
    }

    /** Explicit opt-in recovery; no existing run overload activates a parachute. */
    public RecoveryFlightResult3DOF run(RecoveryRunConfiguration3DOF configuration) {
        if(configuration==null)throw new IllegalArgumentException("recovery run configuration must not be null");
        var vehicle=configuration.vehicle();var base=vehicle.baseRun();
        SimulationRunConfiguration3DOF.validateTerminal(base.terminalEventHandlingModel());
        double[] masses=new double[vehicle.attachedHardware().size()],cgs=new double[masses.length];
        for(int i=0;i<masses.length;i++) {masses[i]=vehicle.attachedHardware().get(i).massKg();cgs[i]=vehicle.attachedHardware().get(i).cgXGeoM();}
        var current=new IntegrationPoint3DOF(base.physicsContext().propulsionTimeline().ignitionTimeS(),new InitialStateBuilder().build(base.launchConditions()));
        var schedule=configuration.alignmentMode()==IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES?
            MotorTimeBoundarySchedule3DOF.fromContext(base.physicsContext(),base.motorCurveStatistics()):null;
        return continueRecoveryFrom(base,masses,cgs,schedule,current,0,null,configuration.recovery(),List.of(),List.of(),List.of());
    }
    public SimulationExecution3DOF run(SimulationRunConfiguration3DOF configuration) {
        return runInternal(configuration,null,null,null);
    }

    /** Explicit opt-in source alignment; the original entrypoint remains fixed-step.
     * @param configuration existing run configuration
     * @param alignmentMode numerical boundary policy
     * @return existing execution/history result
     */
    public SimulationExecution3DOF run(SimulationRunConfiguration3DOF configuration,
            IntegrationStepAlignmentMode alignmentMode) {
        if(alignmentMode==null) throw new IllegalArgumentException("alignment_mode must not be null");
        if(alignmentMode==IntegrationStepAlignmentMode.LEGACY_FIXED_STEP) return run(configuration);
        if(configuration==null) throw new IllegalArgumentException("configuration must not be null");
        var schedule=MotorTimeBoundarySchedule3DOF.fromContext(configuration.physicsContext(),configuration.motorCurveStatistics());
        return runInternal(configuration,null,null,schedule);
    }

    /** NAT-024 single-stage run; hardware arrays are private locals, not additional authorities.
     * @param configuration accepted base run and immutable attached inventory
     * @return existing execution/history result
     */
    public SimulationExecution3DOF run(SingleStageVehicleRunConfiguration3DOF configuration) {
        var hardware=configuration.attachedHardware();
        if(hardware.isEmpty()) return run(configuration.baseRun());
        double[] masses=new double[hardware.size()],cgs=new double[hardware.size()];
        for(int i=0;i<hardware.size();i++) {
            masses[i]=hardware.get(i).massKg(); cgs[i]=hardware.get(i).cgXGeoM();
        }
        return runInternal(configuration.baseRun(),masses,cgs,null);
    }

    PhysicsEvaluationResult3DOF physicsAt(PhysicsEvaluator3DOF evaluator,
            SimulationRunConfiguration3DOF configuration,double timeS,TranslationalState3DOF state,
            double[] masses,double[] cgs) {
        return physicsAtInputs(evaluator,SimulationRunInputs3DOF.legacy(configuration),timeS,state,masses,cgs);
    }
    PhysicsEvaluationResult3DOF physicsAtInputs(PhysicsEvaluator3DOF evaluator,
            SimulationRunInputs3DOF configuration,double timeS,TranslationalState3DOF state,double[] masses,double[] cgs) {
        if(masses==null)return evaluator.evaluateInputs(timeS,state,configuration.launchConditions(),configuration.physicsContext(),new double[0],new double[0]);
        return evaluator.evaluateInputs(timeS,state,configuration.launchConditions(),configuration.physicsContext(),masses,cgs);
    }

    private SimulationExecution3DOF runInternal(SimulationRunConfiguration3DOF configuration,
            double[] masses,double[] cgs,MotorTimeBoundarySchedule3DOF schedule) {
        SimulationRunConfiguration3DOF.validateTerminal(configuration.terminalEventHandlingModel());
        var recorder=new FlightRecorder3DOF(); var physicsEvaluator=new PhysicsEvaluator3DOF();
        var initialState=new InitialStateBuilder().build(configuration.launchConditions());
        var current=new IntegrationPoint3DOF(configuration.physicsContext().propulsionTimeline().ignitionTimeS(),initialState);
        var initialPhysics=physicsAt(physicsEvaluator,configuration,current.timeS(),current.state(),masses,cgs);
        recorder.recordSample(current,initialPhysics);
        return continueFrom(configuration,masses,cgs,schedule,recorder,current,0,null);
    }

    // Shared accepted free-flight lifecycle. New guide execution transfers an accepted RK4
    // point/history here; no public restart API, duplicated event loop or force authority.
    SimulationExecution3DOF continueFrom(SimulationRunConfiguration3DOF configuration,
            double[] masses,double[] cgs,MotorTimeBoundarySchedule3DOF schedule,FlightRecorder3DOF recorder,
            IntegrationPoint3DOF current,int steps,Double firstStepEndS) {
        return continueShared(SimulationRunInputs3DOF.legacy(configuration),masses,cgs,schedule,recorder,current,steps,firstStepEndS,null);
    }

    RecoveryFlightResult3DOF continueRecoveryFrom(SimulationRunConfiguration3DOF configuration,double[] masses,double[] cgs,
            MotorTimeBoundarySchedule3DOF schedule,IntegrationPoint3DOF current,int steps,Double firstStepEndS,
            RecoveryConfiguration3DOF recovery,List<LaunchGuideEvaluation> prefix,List<FlightEventOccurrence3DOF> previousEvents,
            List<LaunchGuideTransitionResult> transitions) {
        var runtime=new RecoveryRuntime3DOF(configuration,recovery,masses,cgs,current,firstStepEndS,prefix,previousEvents,transitions);
        runtime.completedSteps=steps;
        continueShared(SimulationRunInputs3DOF.legacy(configuration),masses,cgs,schedule,null,current,steps,firstStepEndS,runtime);
        return runtime.finish(runtime.samples.get(runtime.samples.size()-1).point(),runtime.completedSteps);
    }

    void continueUntilGround(SimulationRunConfiguration3DOF configuration,double[] masses,double[] cgs,
            MotorTimeBoundarySchedule3DOF schedule,IntegrationPoint3DOF current,Double firstStepEndS,
            List<LaunchGuideEvaluation> prefix,List<FlightEventOccurrence3DOF> events,List<LaunchGuideTransitionResult> transitions,
            GroundRunExecution3DOF execution) {
        continueUntilGroundInputs(SimulationRunInputs3DOF.legacy(configuration),masses,cgs,schedule,current,firstStepEndS,prefix,events,transitions,execution);
    }
    void continueUntilGroundInputs(SimulationRunInputs3DOF configuration,double[] masses,double[] cgs,
            MotorTimeBoundarySchedule3DOF schedule,IntegrationPoint3DOF current,Double firstStepEndS,
            List<LaunchGuideEvaluation> prefix,List<FlightEventOccurrence3DOF> events,List<LaunchGuideTransitionResult> transitions,
            GroundRunExecution3DOF execution) {
        execution.runtime=new RecoveryRuntime3DOF(configuration,execution.configuration.recovery().orElse(null),masses,cgs,current,
            firstStepEndS,prefix,events,transitions,execution);
        execution.publish(true);execution.checkpoint();
        continueShared(configuration,masses,cgs,schedule,null,current,0,firstStepEndS,execution.runtime,execution);
    }

    // One authoritative flight loop for legacy and explicit recovery execution.
    private SimulationExecution3DOF continueShared(SimulationRunInputs3DOF configuration,
            double[] masses,double[] cgs,MotorTimeBoundarySchedule3DOF schedule,FlightRecorder3DOF recorder,
            IntegrationPoint3DOF current,int steps,Double firstStepEndS,RecoveryRuntime3DOF recovery) {
        return continueShared(configuration,masses,cgs,schedule,recorder,current,steps,firstStepEndS,recovery,null);
    }
    private SimulationExecution3DOF continueShared(SimulationRunInputs3DOF configuration,
            double[] masses,double[] cgs,MotorTimeBoundarySchedule3DOF schedule,FlightRecorder3DOF recorder,
            IntegrationPoint3DOF current,int steps,Double firstStepEndS,RecoveryRuntime3DOF recovery,GroundRunExecution3DOF ground) {
        var physicsEvaluator=new PhysicsEvaluator3DOF();
        var integrator=new ClassicalRK4Integrator3DOF(); var detector=new FlightEventDetector3DOF();
        var eventContext=new FlightEventDetectionContext3DOF(configuration.launchConditions(),configuration.physicsContext().propulsionTimeline(),configuration.motorCurveStatistics(),configuration.eventDetectionProfile());
        DerivativeFunction3DOF derivative=(time,state)->physicsAtInputs(physicsEvaluator,configuration,time,state,masses,cgs).dynamics().derivative();
        while(ground!=null || steps<configuration.maximumSteps()) {
            if(recovery!=null) {
                var next=recovery.advance(current,configuration.fixedStepConfig().stepSizeS(),schedule,detector,eventContext);
                if(next!=null) {
                    if(next.timeS()<=current.timeS())throw new IllegalArgumentException("recovery segment does not advance time");
                    current=next;
                    if(ground==null) {steps++;recovery.completedSteps=steps;}
                }
                if(ground!=null) {ground.captureRuntimeOutcome();ground.publish(false);}
                if(recovery.outcome!=null)return null;
                continue;
            }
            var stepConfig=configuration.fixedStepConfig();
            if(firstStepEndS!=null) {
                stepConfig=new FixedStepConfig(remainderStep(current.timeS(),firstStepEndS,stepConfig.stepSizeS()));
                firstStepEndS=null;
            }
            if(schedule!=null) stepConfig=new FixedStepConfig(schedule.effectiveStepSizeS(current.timeS(),stepConfig.stepSizeS()));
            var candidate=integrator.step(current,stepConfig,derivative); steps++;
            var events=detector.detect(current,candidate,eventContext);
            var terminal=events.stream().filter(FlightEventOccurrence3DOF::isTerminal).findFirst().orElse(null);
            if(terminal!=null) {
                var retained=events.stream().filter(e->e.timeS()<=terminal.timeS()).toList();
                if(terminal.interpolationFraction()<1.0) {
                    recorder.recordEvents(retained);
                    return new SimulationExecution3DOF(recorder.snapshot(),SimulationTerminationReason.TERMINAL_EVENT,steps);
                }
                var endpoint=physicsAtInputs(physicsEvaluator,configuration,candidate.timeS(),candidate.state(),masses,cgs);
                recorder.recordEvents(retained); recorder.recordSample(candidate,endpoint);
                return new SimulationExecution3DOF(recorder.snapshot(),SimulationTerminationReason.TERMINAL_EVENT,steps);
            }
            var endpoint=physicsAtInputs(physicsEvaluator,configuration,candidate.timeS(),candidate.state(),masses,cgs);
            recorder.recordEvents(events); recorder.recordSample(candidate,endpoint); current=candidate;
        }
        return recovery==null?new SimulationExecution3DOF(recorder.snapshot(),SimulationTerminationReason.MAXIMUM_STEPS_REACHED,steps):null;
    }

    static double remainderStep(double time,double end,double nominal) {
        // Same endpoint-coincidence rule as NAT-026.1; no source-time snapping.
        if(time+nominal==end) return nominal;
        double h=LaunchGuideError.finite(end-time,"remainderStepS");
        if(h<=0 || h>nominal || time+h!=end)
            throw new LaunchGuideError("GUIDE_RESTART_TIME_UNREPRESENTABLE","remainderEndS",end);
        return h;
    }
}
