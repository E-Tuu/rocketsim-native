package org.roketsim.core.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vectors;
import org.roketsim.core.numerics.*;

/** Opt-in pad/guide execution, handing accepted release state to the one existing free-flight loop. */
public final class LaunchGuideSimulator3DOF {
    /** Same pad/guide path, then the engine's shared opt-in recovery continuation. */
    public RecoveryFlightResult3DOF run(LaunchGuideRunConfiguration3DOF configuration,RecoveryConfiguration3DOF recovery) {
        if(recovery==null)throw new IllegalArgumentException("recovery must not be null");
        LaunchGuideError.required(configuration,"configuration");
        if(configuration.guide()==null || (configuration.policy()==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE
                && configuration.attachments()==null))return RecoveryRuntime3DOF.unreleased(run(configuration),configuration.vehicle().baseRun().physicsContext().propulsionTimeline().ignitionTimeS());
        validate(GuideRunInputs3DOF.legacy(configuration));
        var execution=new Execution(GuideRunInputs3DOF.legacy(configuration));execution.recovery=recovery;
        var guide=execution.run();
        return execution.recoveryResult!=null?execution.recoveryResult:RecoveryRuntime3DOF.unreleased(guide,configuration.vehicle().baseRun().physicsContext().propulsionTimeline().ignitionTimeS());
    }
    public LaunchGuideRunResult3DOF run(LaunchGuideRunConfiguration3DOF configuration) {
        LaunchGuideError.required(configuration,"configuration");
        if(configuration.guide()==null || (configuration.policy()==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE
                && configuration.attachments()==null))
            return new LaunchGuideRunResult3DOF(configuration.policy(),LaunchGuideRunResult3DOF.Outcome.NOT_EVALUABLE,
                new RecordedFlightData3DOF(List.of(),List.of()),List.of(),List.of(),0,OptionalDouble.empty(),Optional.empty());
        validate(GuideRunInputs3DOF.legacy(configuration));
        return new Execution(GuideRunInputs3DOF.legacy(configuration)).run();
    }
    void runUntilGround(LaunchGuideRunConfiguration3DOF configuration,GroundRunExecution3DOF ground) {
        runRuntimeUntilGround(GuideRunInputs3DOF.legacy(configuration),ground);
    }
    void runRuntimeUntilGround(GuideRunInputs3DOF configuration,GroundRunExecution3DOF ground) {
        if(configuration.guide()==null || (configuration.policy()==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE && configuration.attachments()==null))
            throw new GroundRunExecution3DOF.ExecutionFailure(RunControl3DOF.Status.UNSUPPORTED,"GUIDE_NOT_EVALUABLE");
        validate(configuration);
        var execution=new Execution(configuration);execution.ground=ground;
        ground.guideSamples=execution.evaluations;ground.transitions=execution.transitions;ground.guideRecorder=execution.recorder;
        var result=execution.run();
        if(ground.runtime==null)throw new GroundRunExecution3DOF.ExecutionFailure(RunControl3DOF.Status.UNSUPPORTED,"GUIDE_"+result.outcome().name());
    }
    private static void validate(GuideRunInputs3DOF c) {
        if(c.alignmentMode()!=IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES)
            throw new LaunchGuideError("UNSUPPORTED_GUIDE_ALIGNMENT_COMBINATION","alignmentMode",c.alignmentMode());
        var guide=c.guide();
        if(c.policy()==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE) {
            if(guide.kind()!=LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL)
                throw new LaunchGuideError("UNSUPPORTED_GUIDE_KIND","guide.kind",guide.kind());
            if(!guide.datumId().equals(c.attachments().datumId()))
                throw new LaunchGuideError("GUIDE_DATUM_MISMATCH","attachments.datumId",c.attachments().datumId());
            if(c.attachments().forwardContact0M()>=guide.topCoordinateM())
                throw new LaunchGuideError("INVALID_INITIAL_BUTTON_ENGAGEMENT","forwardContact0M",c.attachments().forwardContact0M());
        } else {
            if(guide.kind()!=LaunchGuideGeometry.Kind.NOMINAL_ROD_INPUT || c.attachments()!=null)
                throw new LaunchGuideError("INVALID_NOMINAL_COMPARISON_INPUT","guide.kind/attachments",guide.kind());
        }
        var launch=c.base().launchConditions();
        if(Vectors.magnitude(launch.initialVelocityWorldMS())!=0)
            throw new LaunchGuideError("UNSUPPORTED_PAD_INITIAL_VELOCITY","initialVelocityWorldMS",launch.initialVelocityWorldMS());
        if(launch.launchDirectionWorldUnit().get(2)<=0)
            throw new LaunchGuideError("UNSUPPORTED_NONASCENDING_GUIDE","launchDirectionWorldUnit",launch.launchDirectionWorldUnit());
        if(c.guidanceEndTravelM()<=0)
            throw new LaunchGuideError("INVALID_GUIDANCE_END_TRAVEL","guidanceEndTravelM",c.guidanceEndTravelM());
    }

    // Every mutable object below belongs to one run. The public executor has no fields/history.
    private static final class Execution {
        final GuideRunInputs3DOF c;
        final SimulationRunInputs3DOF base;
        final LaunchConditions3DOF launch;
        final SimulationEngine3DOF engine=new SimulationEngine3DOF();
        final PhysicsEvaluator3DOF physics=new PhysicsEvaluator3DOF();
        final FlightRecorder3DOF recorder=new FlightRecorder3DOF();
        final FlightPhaseClockEvaluator3DOF clock=new FlightPhaseClockEvaluator3DOF();
        final FlightEventDetector3DOF detector=new FlightEventDetector3DOF();
        final MotorTimeBoundarySchedule3DOF schedule;
        final FlightEventDetectionContext3DOF eventContext;
        final double[] masses,cgs;
        final List<LaunchGuideEvaluation> evaluations=new ArrayList<>();
        final List<LaunchGuideTransitionResult> transitions=new ArrayList<>();
        final double threshold;
        IntegrationPoint3DOF current;
        FlightPhase3DOF phase=FlightPhase3DOF.PAD_CONTACT;
        int steps;
        GroundRunExecution3DOF ground;
        RecoveryConfiguration3DOF recovery;
        RecoveryFlightResult3DOF recoveryResult;
        Execution(GuideRunInputs3DOF c) {
            this.c=c; base=c.base(); launch=base.launchConditions(); threshold=c.guidanceEndTravelM();
            var hardware=c.hardware();
            masses=new double[hardware.size()];cgs=new double[hardware.size()];
            for(int i=0;i<hardware.size();i++) {masses[i]=hardware.get(i).massKg();cgs[i]=hardware.get(i).cgXGeoM();}
            schedule=MotorTimeBoundarySchedule3DOF.fromInputs(base.physicsContext(),base.motorCurveStatistics());
            eventContext=new FlightEventDetectionContext3DOF(launch,base.physicsContext().propulsionTimeline(),
                base.motorCurveStatistics(),base.eventDetectionProfile());
            current=new IntegrationPoint3DOF(base.physicsContext().propulsionTimeline().ignitionTimeS(),new InitialStateBuilder().build(launch));
        }
        PhysicsEvaluationResult3DOF at(double time,TranslationalState3DOF state) {
            if(ground!=null && ground.last!=null)ground.checkpoint("guide_evaluation");
            return engine.physicsAtInputs(physics,base,time,state,masses,cgs);
        }
        LaunchGuideEvaluation evaluation(IntegrationPoint3DOF point,FlightPhase3DOF selected) {
            return LaunchGuideEvaluation.evaluate(point,launch,at(point.timeS(),point.state()),
                clock.evaluate(point.timeS(),selected,base.physicsContext().propulsionTimeline(),base.motorCurveStatistics()));
        }
        double travel(IntegrationPoint3DOF point) { return LaunchGuideEvaluation.travel(launch,point.state()); }
        double exitMetric(IntegrationPoint3DOF point) {
            return c.policy()==LaunchGuideConstraintPolicy.OPENROCKET_NOMINAL_ROD_COMPAT?
                LaunchGuideEvaluation.displacementMagnitude(launch,point.state()):travel(point);
        }
        double speed(IntegrationPoint3DOF point) { return LaunchGuideEvaluation.axialVelocity(launch,point.state()); }
        double force(double duration) {
            return Vectors.dot(at(current.timeS()+duration,current.state()).dynamics().forces().netForceWorldN(),launch.launchDirectionWorldUnit());
        }
        boolean canDepart(double duration) {
            var p=at(current.timeS()+duration,current.state());
            return Vectors.dot(p.dynamics().forces().netForceWorldN(),launch.launchDirectionWorldUnit())>0
                && Vectors.dot(p.dynamics().accelerationWorldMS2(),launch.launchDirectionWorldUnit())>0;
        }
        // With the accepted stationary environment/wind and source-aligned intervals, pad net
        // force is quadratic (impulse mass) or linear (explicit mass). Fit only to FIND a bracket;
        // all sign decisions and accepted endpoints use actual PhysicsEvaluator values.
        Double departureBracketHigh(double h) {
            double f0=force(0),fm=force(h/2),f1=force(h);
            double a=LaunchGuideError.finite(2*(f1+f0-2*fm),"padForceQuadraticA");
            double b=LaunchGuideError.finite(f1-f0-a,"padForceQuadraticB");
            var probes=new ArrayList<Double>(); probes.add(h/2);probes.add(h);
            if(a<0) {
                double vertex=-b/LaunchGuideError.finite(2*a,"padForceQuadraticDenominator");
                if(vertex>0 && vertex<1) probes.add(vertex*h);
            }
            probes.sort(Double::compare);
            for(double probe:probes) if(canDepart(probe)) return probe;
            return null;
        }
        static TranslationalStateDerivative3DOF held() {
            return new TranslationalStateDerivative3DOF(LaunchGuideEvaluation.zero(),LaunchGuideEvaluation.zero());
        }
        void record(IntegrationPoint3DOF next,FlightPhase3DOF selected) {
            if(ground!=null) {
                var e=evaluation(next,selected);
                if(selected!=FlightPhase3DOF.FREE_FLIGHT && e.guideTravelM()<0)
                    throw new LaunchGuideError("GUIDE_PENETRATION_REJECTED","guideTravelM",e.guideTravelM());
                var reports=next.timeS()>current.timeS()?detector.detect(current,next,eventContext).stream().filter(t->!t.isTerminal()).toList():List.<FlightEventOccurrence3DOF>of();
                ground.beforeCommit(next);
                recorder.recordEvents(reports);recorder.recordSample(next,e.unconstrainedPhysics());evaluations.add(e);current=next;
                ground.committed(new RecoveryFlightResult3DOF.Sample(next,RecoveryFlightResult3DOF.Physics.baseline(e.unconstrainedPhysics()),selected,RecoveryFlightResult3DOF.State.NOT_TRIGGERED));
                ground.publish(false);return;
            }
            if(next.timeS()>current.timeS()) {
                // The constrained support boundary owns recontact. Do not relabel it as NAT-018 GROUND.
                recorder.recordEvents(detector.detect(current,next,eventContext).stream().filter(e->!e.isTerminal()).toList());
            }
            var e=evaluation(next,selected);
            if(selected!=FlightPhase3DOF.FREE_FLIGHT && e.guideTravelM()<0)
                throw new LaunchGuideError("GUIDE_PENETRATION_REJECTED","guideTravelM",e.guideTravelM());
            recorder.recordSample(next,e.unconstrainedPhysics());evaluations.add(e);current=next;
        }
        LaunchGuideRunResult3DOF result(LaunchGuideRunResult3DOF.Outcome outcome,RecordedFlightData3DOF data,
                Optional<SimulationTerminationReason> termination) {
            var last=c.policy()==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE?OptionalDouble.of(
                LaunchGuideError.finite(c.guide().topCoordinateM()-c.attachments().aftContact0M(),"lastContactGeometricThresholdM")):
                OptionalDouble.empty();
            return new LaunchGuideRunResult3DOF(c.policy(),outcome,data,evaluations,transitions,steps,last,termination);
        }
        LaunchGuideRunResult3DOF impact(double lower,double upper) {
            if(speed(current)>=0 || travel(current)<0)
                throw new LaunchGuideError("INVALID_PREIMPACT_STATE","axialVelocityMS",speed(current));
            transitions.add(new LaunchGuideTransitionResult(LaunchGuideTransitionResult.Kind.PAD_RECONTACT,current,
                lower,upper,0,LaunchGuideTransitionResult.AcceptedSide.LAST_NONPENETRATING_RK4));
            return result(LaunchGuideRunResult3DOF.Outcome.IMPACT_DYNAMICS_UNSUPPORTED,recorder.snapshot(),Optional.empty());
        }
        LaunchGuideRunResult3DOF run() {
            record(current,phase);
            Double parentEnd=null;
            while(ground!=null || steps<base.maximumSteps()) {
                if(ground!=null)ground.checkpoint();
                if(parentEnd==null) parentEnd=LaunchGuideError.finite(current.timeS()+
                    (ground==null?schedule.effectiveStepSizeS(current.timeS(),base.fixedStepConfig().stepSizeS()):
                        schedule.effectiveStepSizeForExecutionS(current.timeS(),base.fixedStepConfig().stepSizeS())),"guideStepEndS");
                double h=SimulationEngine3DOF.remainderStep(current.timeS(),parentEnd,base.fixedStepConfig().stepSizeS());
                if(phase==FlightPhase3DOF.PAD_CONTACT) {
                    if(canDepart(0)) {
                        transitions.add(new LaunchGuideTransitionResult(LaunchGuideTransitionResult.Kind.LIFTOFF,current,
                            current.timeS(),current.timeS(),0,LaunchGuideTransitionResult.AcceptedSide.FIRST_POST_BOUNDARY_RK4));
                        phase=FlightPhase3DOF.GUIDED_FLIGHT;continue;
                    }
                    Double positive=departureBracketHigh(h);
                    if(positive==null) {
                        if(ground==null)steps++; var next=LaunchGuideBoundaryLocalizer.step(current,h,(t,s)->held());
                        if(next.timeS()<=current.timeS()) throw new LaunchGuideError("GUIDE_NO_TIME_PROGRESS","timeS",next.timeS());
                        record(next,phase);
                    } else {
                        var bracket=LaunchGuideBoundaryLocalizer.firstTrue(positive,this::canDepart);
                        var low=LaunchGuideBoundaryLocalizer.step(current,bracket.low(),(t,s)->held());
                        var next=LaunchGuideBoundaryLocalizer.step(current,bracket.high(),(t,s)->held());
                        if(next.timeS()<=current.timeS()) throw new LaunchGuideError("LIFTOFF_TIME_UNREPRESENTABLE","timeS",next.timeS());
                        transitions.add(new LaunchGuideTransitionResult(LaunchGuideTransitionResult.Kind.LIFTOFF,next,
                            low.timeS(),next.timeS(),0,LaunchGuideTransitionResult.AcceptedSide.FIRST_POST_BOUNDARY_RK4));
                        phase=FlightPhase3DOF.GUIDED_FLIGHT;if(ground==null)steps++;record(next,phase);
                    }
                } else {
                    if(travel(current)==0 && speed(current)<0) return impact(current.timeS(),current.timeS());
                    if(travel(current)==0 && speed(current)==0 && !canDepart(0)) {
                        phase=FlightPhase3DOF.PAD_CONTACT;continue;
                    }
                    if(ground==null)steps++;
                    SafeStep candidate=safeCandidate(h);
                    if(candidate.point().timeS()<=current.timeS()) {
                        if(speed(current)<0 && candidate.unsafeDuration()!=null)
                            return impact(current.timeS(),current.timeS()+candidate.unsafeDuration());
                        throw new LaunchGuideError("GUIDE_NO_REPRESENTABLE_PROGRESS","timeS",current.timeS());
                    }
                    double candidateH=candidate.duration();
                    // A stage can reveal an excursion hidden by a returning accepted endpoint.
                    while(candidate.maxTravel()>=threshold && exitMetric(candidate.point())<threshold) {
                        candidateH/=2;
                        candidate=safeCandidate(candidateH);
                        if(candidate.point().timeS()<=current.timeS())
                            throw new LaunchGuideError("GUIDE_EVENT_BRACKET_UNRESOLVED","timeS",current.timeS());
                        candidateH=candidate.duration();
                    }
                    if(exitMetric(candidate.point())>=threshold) {
                        var kind=c.policy()==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE?
                            LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END:LaunchGuideTransitionResult.Kind.NOMINAL_ROD_EXIT;
                        var event=new LaunchGuideBoundaryLocalizer().guidanceEnd(current,candidateH,launch,threshold,this::guidedDerivative,kind);
                        if(ground==null)transitions.add(event);phase=FlightPhase3DOF.FREE_FLIGHT;
                        record(event.acceptedPoint(),phase);
                        if(ground!=null)transitions.add(event);
                        Double remainder=current.timeS()<parentEnd?parentEnd:null;
                        if(ground!=null) {
                            engine.continueUntilGroundInputs(base,masses,cgs,schedule,current,remainder,evaluations,recorder.snapshot().events(),transitions,ground);
                            return null;
                        }
                        if(recovery!=null) {
                            recoveryResult=engine.continueRecoveryFrom(((SimulationRunInputs3DOF.Legacy)base).source(),masses,cgs,schedule,current,evaluations.size()-1,remainder,recovery,
                                evaluations,recorder.snapshot().events(),transitions);
                            return null; // Internal alternate typed result; legacy calls never take this branch.
                        }
                        var free=engine.continueFrom(((SimulationRunInputs3DOF.Legacy)base).source(),masses,cgs,schedule,recorder,current,steps,remainder);
                        steps=free.stepsPerformed();
                        for(var sample:free.recordedData().samples()) if(sample.point().timeS()>current.timeS())
                            evaluations.add(LaunchGuideEvaluation.evaluate(sample.point(),launch,sample.physics(),
                                clock.evaluate(sample.point().timeS(),FlightPhase3DOF.FREE_FLIGHT,base.physicsContext().propulsionTimeline(),base.motorCurveStatistics())));
                        return result(free.terminationReason()==SimulationTerminationReason.TERMINAL_EVENT?
                            LaunchGuideRunResult3DOF.Outcome.FREE_FLIGHT_TERMINATED:LaunchGuideRunResult3DOF.Outcome.FREE_FLIGHT_STEP_LIMIT,
                            free.recordedData(),Optional.of(free.terminationReason()));
                    }
                    record(candidate.point(),phase);
                    if(travel(current)==0 && speed(current)<0) return impact(current.timeS(),current.timeS());
                }
                if(current.timeS()==parentEnd) parentEnd=null;
                else if(current.timeS()>parentEnd) throw new LaunchGuideError("GUIDE_STEP_BOUNDARY_CROSSED","parentEndS",parentEnd);
            }
            return result(transitions.stream().noneMatch(t->t.kind()==LaunchGuideTransitionResult.Kind.LIFTOFF)?
                LaunchGuideRunResult3DOF.Outcome.NO_LIFTOFF_STEP_LIMIT:LaunchGuideRunResult3DOF.Outcome.GUIDED_STEP_LIMIT,
                recorder.snapshot(),Optional.empty());
        }

        TranslationalStateDerivative3DOF guidedDerivative(double time,TranslationalState3DOF state) {
            if(LaunchGuideEvaluation.travel(launch,state)<0) throw new PadTrialCrossing();
            return evaluation(new IntegrationPoint3DOF(time,state),FlightPhase3DOF.GUIDED_FLIGHT).appliedDerivative();
        }
        record SafeStep(IntegrationPoint3DOF point,double duration,double maxTravel,Double unsafeDuration) { }
        SafeStep trial(double h) {
            if(ground!=null)ground.checkpoint("guide_trial");
            double[] max={exitMetric(current)};
            var next=LaunchGuideBoundaryLocalizer.step(current,h,(t,s)-> {
                max[0]=Math.max(max[0],exitMetric(new IntegrationPoint3DOF(t,s)));
                return guidedDerivative(t,s);
            });
            if(travel(next)<0) throw new PadTrialCrossing();
            return new SafeStep(next,h,Math.max(max[0],exitMetric(next)),null);
        }
        SafeStep safeCandidate(double h) {
            try {return trial(h);} catch(PadTrialCrossing crossing) {
                // Only a known lower-support domain crossing is intercepted; upstream physics errors propagate.
                double low=0,high=h; SafeStep accepted=new SafeStep(current,0,exitMetric(current),h);
                while(true) {
                    double mid=low+(high-low)/2;
                    if(mid<=low || mid>=high) return new SafeStep(accepted.point(),low,accepted.maxTravel(),high);
                    try {var p=trial(mid);low=mid;accepted=p;} catch(PadTrialCrossing contact) {high=mid;}
                }
            }
        }
    }
    private static final class PadTrialCrossing extends RuntimeException { }
}
