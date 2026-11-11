package org.roketsim.core.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.roketsim.core.numerics.*;
import static org.roketsim.core.simulation.RecoveryFlightResult3DOF.*;
import static org.roketsim.core.simulation.RunControl3DOF.*;

/** Run-local observation/result adapter. Contains no integration loop or physical equations. */
final class GroundRunExecution3DOF {
    final GroundConfigurationInputs3DOF configuration;
    final RunControl3DOF control;
    final long started;
    final double ignition,groundZ;
    RecoveryRuntime3DOF runtime;
    List<LaunchGuideEvaluation> guideSamples=List.of();
    List<LaunchGuideTransitionResult> transitions=List.of();
    FlightRecorder3DOF guideRecorder;
    Sample last;
    long acceptedSegments,lastNotice,lastWallWarning,lastHistoryWarning,observedElapsed;
    double lastAirborneWarning;
    Status status=Status.RUNNING;
    GroundRunResult3DOF.FailureDetail failure;
    Progress finalProgress;
    static final class Cancelled extends RuntimeException { }
    static final class ExecutionFailure extends RuntimeException {
        final Status status;final String code;
        ExecutionFailure(Status status,String code) {super(code);this.status=status;this.code=code;}
    }
    GroundRunExecution3DOF(UntilGroundRunConfiguration3DOF configuration,RunControl3DOF control) {
        this(GroundConfigurationInputs3DOF.legacy(configuration),control);
    }
    GroundRunExecution3DOF(GroundConfigurationInputs3DOF configuration,RunControl3DOF control) {
        this.configuration=configuration;this.control=control;control.claim();started=control.nanoClock.getAsLong();
        var base=configuration.base();ignition=base.physicsContext().propulsionTimeline().ignitionTimeS();
        groundZ=base.launchConditions().initialPositionWorldM().get(2);
    }
    void checkpoint() {checkpoint("execution");}
    void checkpoint(String location) {
        control.checkpointObserver.accept(location);
        if(status==Status.RUNNING && control.isCancellationRequested())throw new Cancelled();
    }
    static long increment(long value) {
        try {return Math.incrementExact(value);}catch(ArithmeticException overflow) {throw new ExecutionFailure(Status.NUMERICAL_FAILURE,"ACCEPTED_SEGMENT_COUNTER_OVERFLOW");}
    }
    void beforeCommit(IntegrationPoint3DOF point) {
        if(last!=null)checkpoint("before_commit");
        long size=runtime!=null?runtime.samples.size():guideSamples.size();
        if(size==Integer.MAX_VALUE)throw new ExecutionFailure(Status.RESOURCE_FAILURE,"HISTORY_INDEX_CAPACITY_EXHAUSTED");
        if(last!=null && point.timeS()>last.point().timeS())increment(acceptedSegments);
        if(last!=null && point.timeS()<last.point().timeS())throw new ExecutionFailure(Status.NUMERICAL_FAILURE,"NON_MONOTONIC_ACCEPTED_TIME");
    }
    void committed(Sample sample) {
        if(last!=null && sample.point().timeS()>last.point().timeS())acceptedSegments=increment(acceptedSegments);
        last=sample;
    }
    void validateInitial(IntegrationPoint3DOF initial) {
        if(initial.state().positionWorldM().get(2)<groundZ || (!configuration.hasGuide()
                && initial.state().positionWorldM().get(2)==groundZ && initial.state().velocityWorldMS().get(2)<0))
            throw new ExecutionFailure(Status.UNSUPPORTED,"UNSUPPORTED_INITIAL_GROUND_CONDITION");
    }
    void validateTrial(IntegrationPoint3DOF current,IntegrationPoint3DOF candidate) {
        if(candidate.timeS()<=current.timeS())throw new ExecutionFailure(Status.NUMERICAL_FAILURE,"NONADVANCING_INTEGRATION_TIME");
        if(current.state().positionWorldM().get(2)<=groundZ && candidate.state().positionWorldM().get(2)<groundZ)
            throw new ExecutionFailure(Status.UNSUPPORTED,"NO_POSITIVE_HEIGHT_BEFORE_BELOW_GROUND_TRIAL");
    }
    long elapsed() {
        long elapsed=control.nanoClock.getAsLong()-started;
        if(elapsed<0)throw new ExecutionFailure(Status.NUMERICAL_FAILURE,"MONOTONIC_CLOCK_RANGE_EXCEEDED");
        observedElapsed=elapsed;return elapsed;
    }
    void publish(boolean force) {
        if(last==null)return;
        long elapsed=elapsed();
        if(!force && elapsed-lastNotice<control.policy.progressIntervalNanos())return;
        double time=last.point().timeS()-ignition;
        if(!Double.isFinite(time))throw new ExecutionFailure(Status.NUMERICAL_FAILURE,"NONFINITE_PROGRESS_TIME");
        double height=last.point().state().positionWorldM().get(2)-groundZ;
        if(!Double.isFinite(height))throw new ExecutionFailure(Status.NUMERICAL_FAILURE,"NONFINITE_GROUND_RELATIVE_ALTITUDE");
        var p=new Progress(time,elapsed,acceptedSegments,height,
            last.point().state().velocityWorldMS().get(2),last.phase(),last.recoveryState(),status);
        finalProgress=p;lastNotice=elapsed;
        try {
            control.progress.accept(p);
            long size=runtime!=null?runtime.samples.size():guideSamples.size();
            if(status!=Status.RUNNING)return;
            if(elapsed-lastWallWarning>=control.policy.wallWarningIntervalNanos()) {
                control.warnings.accept(new Warning("LONG_COMPUTATION","Computation is continuing; no valid ground contact has been committed.",p,size));
                lastWallWarning=elapsed;
            }
            if(p.groundRelativeAltitudeM()>0 && time-lastAirborneWarning>=control.policy.airborneWarningIntervalS()) {
                control.warnings.accept(new Warning("LONG_OBSERVED_AIRBORNE_TIME","Observed simulated interval remains airborne; no cause or ETA is inferred.",p,size));
                lastAirborneWarning=time;
            }
            if(size-lastHistoryWarning>=control.policy.historyWarningEverySamples()) {
                control.warnings.accept(new Warning("GROWING_FLIGHT_HISTORY","Recorded accepted history continues growing; memory is finite and history is not truncated.",p,size));
                lastHistoryWarning=size;
            }
        } catch(RuntimeException observer) {throw new ExecutionFailure(Status.MONITORING_FAILURE,"MONITOR_CALLBACK_FAILED: "+observer);}
    }
    void captureRuntimeOutcome() {
        if(runtime==null || runtime.outcome==null)return;
        status=switch(runtime.outcome) {
            case GROUND -> Status.GROUND;
            case UNSUPPORTED,GUIDE_NOT_RELEASED -> Status.UNSUPPORTED;
            case NUMERICAL_FAILURE -> Status.NUMERICAL_FAILURE;
            case MAXIMUM_STEPS -> throw new ExecutionFailure(Status.NUMERICAL_FAILURE,"UNEXPECTED_LEGACY_STEP_TERMINATION");
        };
        if(runtime.failure!=null) {
            var details=new java.util.LinkedHashMap<String,String>();
            runtime.failure.details().forEach((k,v)->details.put(k,Double.toString(v)));
            failure=new GroundRunResult3DOF.FailureDetail(runtime.failure.code(),runtime.failure.message(),details);
        }
    }
    private static String errorCode(RuntimeException e) {
        if(e instanceof ExecutionFailure x)return x.code;
        if(e instanceof NumericalIntegrationError x)return x.errorCode();
        if(e instanceof EventDetectionError x)return x.errorCode();
        if(e instanceof LaunchGuideError x)return x.errorCode();
        if(e instanceof RecoveryRuntime3DOF.TimingFailure x)return x.code();
        if(e instanceof PhysicsEvaluationError x)return x.errorCode();
        if(e instanceof org.roketsim.core.aerodynamics.AerodynamicEvaluationError x)return x.errorCode();
        if(e instanceof org.roketsim.core.dynamics.DynamicsEvaluationError x)return x.errorCode();
        if(e instanceof org.roketsim.core.mass.MassValidationError x)return x.errorCode();
        if(e instanceof org.roketsim.core.propulsion.PropulsionEvaluationError x)return x.errorCode();
        if(e instanceof IllegalArgumentException && String.valueOf(e.getMessage()).contains("must be finite; got"))return "NONFINITE_NUMERIC_VALUE";
        return e.getClass().getSimpleName();
    }
    void failed(RuntimeException exception) {
        if(status==Status.GROUND)return;
        if(exception instanceof Cancelled) {status=Status.CANCELLED;failure=new GroundRunResult3DOF.FailureDetail("USER_CANCELLED","Cooperative cancellation at an uncommitted-work checkpoint",Map.of());return;}
        String code=errorCode(exception);
        if(exception instanceof ExecutionFailure e)status=e.status;
        else if(exception instanceof NumericalIntegrationError || exception instanceof EventDetectionError || exception instanceof FlightRecordingError
                || exception instanceof RecoveryRuntime3DOF.TimingFailure || exception instanceof ArithmeticException || code.contains("NONFINITE")
                || code.equals("INVALID_ACCELERATION"))status=Status.NUMERICAL_FAILURE;
        else if(exception instanceof LaunchGuideError e)status=(e.errorCode().contains("TIME") || e.errorCode().contains("PROGRESS")
                || e.errorCode().contains("UNREPRESENTABLE") || e.errorCode().contains("UNRESOLVED"))?Status.NUMERICAL_FAILURE:Status.UNSUPPORTED;
        else if(code.contains("UNSUPPORTED"))status=Status.UNSUPPORTED;
        else if(exception instanceof IllegalArgumentException)status=Status.MODEL_DOMAIN_FAILURE;
        else status=Status.NUMERICAL_FAILURE;
        failure=new GroundRunResult3DOF.FailureDetail(code,String.valueOf(exception.getMessage()),Map.of("exception_type",exception.getClass().getName()));
    }
    GroundRunResult3DOF finish() {
        if(status==Status.RUNNING)throw new IllegalStateException("runUntilGround returned without a terminal outcome");
        try {publish(true);}catch(RuntimeException observer) {if(status!=Status.GROUND)failed(observer);}
        var samples=runtime!=null?runtime.samples:guideSamples.stream().map(e->new Sample(e.point(),Physics.baseline(e.unconstrainedPhysics()),e.clock().flightPhase(),State.NOT_TRIGGERED)).toList();
        var events=runtime!=null?runtime.legacy:guideRecorder==null?List.<FlightEventOccurrence3DOF>of():guideRecorder.snapshot().events();
        var recovered=runtime!=null?runtime.events:List.<Event>of();
        var guideEvents=runtime!=null?runtime.guideTransitions:transitions.stream().filter(t->last!=null && t.acceptedPoint().timeS()<=last.point().timeS()).toList();
        if(finalProgress!=null && finalProgress.executionStatus()!=status)
            finalProgress=new Progress(finalProgress.simulatedFlightTimeS(),observedElapsed,acceptedSegments,
                finalProgress.groundRelativeAltitudeM(),finalProgress.verticalVelocityWorldMS(),finalProgress.flightPhase(),finalProgress.recoveryState(),status);
        return new GroundRunResult3DOF(status,samples,events,recovered,guideEvents,acceptedSegments,observedElapsed,Optional.ofNullable(failure),Optional.ofNullable(finalProgress));
    }
}
