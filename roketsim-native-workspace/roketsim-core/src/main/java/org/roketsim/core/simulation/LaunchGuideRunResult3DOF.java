package org.roketsim.core.simulation;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.roketsim.core.math.Vectors;

/** New opt-in wrapper; old schemas and the meaning of their stored free-physics snapshots stay intact. */
public record LaunchGuideRunResult3DOF(LaunchGuideConstraintPolicy policy,Outcome outcome,
        RecordedFlightData3DOF recordedData,List<LaunchGuideEvaluation> guideSamples,
        List<LaunchGuideTransitionResult> transitions,int stepsPerformed,
        OptionalDouble lastContactGeometricThresholdM,Optional<SimulationTerminationReason> freeTerminationReason) {
    public enum Outcome { NOT_EVALUABLE, NO_LIFTOFF_STEP_LIMIT, GUIDED_STEP_LIMIT,
        FREE_FLIGHT_TERMINATED, FREE_FLIGHT_STEP_LIMIT, IMPACT_DYNAMICS_UNSUPPORTED }
    public LaunchGuideRunResult3DOF {
        LaunchGuideError.required(policy,"policy"); LaunchGuideError.required(outcome,"outcome");
        LaunchGuideError.required(recordedData,"recordedData");
        guideSamples=List.copyOf(guideSamples); transitions=List.copyOf(transitions);
        LaunchGuideError.required(lastContactGeometricThresholdM,"lastContactGeometricThresholdM");
        LaunchGuideError.required(freeTerminationReason,"freeTerminationReason");
        if(stepsPerformed<0) throw new LaunchGuideError("INVALID_GUIDE_STEP_COUNT","stepsPerformed",stepsPerformed);
        if(guideSamples.size()!=recordedData.samples().size())
            throw new LaunchGuideError("GUIDE_SAMPLE_SHAPE_MISMATCH","guideSamples",guideSamples.size());
        for(int i=0;i<guideSamples.size();i++) {
            var constraint=guideSamples.get(i);var recorded=recordedData.samples().get(i);
            if(!constraint.point().equals(recorded.point()) || !constraint.unconstrainedPhysics().equals(recorded.physics()))
                throw new LaunchGuideError("GUIDE_SAMPLE_SNAPSHOT_MISMATCH","sampleIndex",i);
        }
        long exits=transitions.stream().filter(t->t.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END
            || t.kind()==LaunchGuideTransitionResult.Kind.NOMINAL_ROD_EXIT).count();
        if(exits>1) throw new LaunchGuideError("MULTIPLE_GUIDANCE_ENDS","transitions",exits);
        for(var event:transitions) {
            if(event.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END && policy!=LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE
                    || event.kind()==LaunchGuideTransitionResult.Kind.NOMINAL_ROD_EXIT && policy!=LaunchGuideConstraintPolicy.OPENROCKET_NOMINAL_ROD_COMPAT)
                throw new LaunchGuideError("GUIDE_EVENT_POLICY_MISMATCH","event.kind",event.kind());
        }
    }
    public Optional<LaunchGuideTransitionResult> guidanceEnd() {
        return transitions.stream().filter(t->t.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END
            || t.kind()==LaunchGuideTransitionResult.Kind.NOMINAL_ROD_EXIT).findFirst();
    }
    public OptionalDouble twoPointGuidanceEndSpeedMS() {
        return speed(LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE);
    }
    public OptionalDouble openRocketNominalRodExitSpeedMS() {
        return speed(LaunchGuideConstraintPolicy.OPENROCKET_NOMINAL_ROD_COMPAT);
    }
    private OptionalDouble speed(LaunchGuideConstraintPolicy selected) {
        return policy==selected && guidanceEnd().isPresent()?OptionalDouble.of(
            Vectors.magnitude(guidanceEnd().orElseThrow().acceptedPoint().state().velocityWorldMS())):OptionalDouble.empty();
    }
    public Optional<String> continuationClassification() {
        return guidanceEnd().isEmpty()?Optional.empty():Optional.of(policy==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE?
            "APPROXIMATE_AFTER_TWO_POINT_EXIT":"OPENROCKET_NOMINAL_ROD_COMPARISON_CONTINUATION");
    }
}
