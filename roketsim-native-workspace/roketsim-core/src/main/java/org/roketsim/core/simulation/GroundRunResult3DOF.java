package org.roketsim.core.simulation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.simulation.RecoveryFlightResult3DOF.*;

/** Immutable accepted history and authoritative NAT-018 contact estimate, not an impact solution. */
public record GroundRunResult3DOF(RunControl3DOF.Status terminationReason,List<Sample> samples,
        List<FlightEventOccurrence3DOF> events,List<Event> recoveryEvents,List<LaunchGuideTransitionResult> guideTransitions,
        long acceptedSegments,long elapsedComputationNanos,Optional<FailureDetail> failure,
        Optional<RunControl3DOF.Progress> finalProgress) {
    public record FailureDetail(String code,String message,Map<String,String> details) {
        public FailureDetail {details=Map.copyOf(details);}
    }
    public GroundRunResult3DOF {
        samples=List.copyOf(samples);events=List.copyOf(events);recoveryEvents=List.copyOf(recoveryEvents);
        guideTransitions=List.copyOf(guideTransitions);
        if(terminationReason==null || terminationReason==RunControl3DOF.Status.RUNNING || failure==null || finalProgress==null
                || acceptedSegments<0 || elapsedComputationNanos<0)throw new IllegalArgumentException("invalid ground-run result");
        long grounds=events.stream().filter(e->e.eventType()==FlightEventType.GROUND).count();
        if((terminationReason==RunControl3DOF.Status.GROUND)!=(grounds==1) || grounds>1)
            throw new IllegalArgumentException("GROUND termination requires exactly one authoritative contact event");
        for(int i=1;i<samples.size();i++)if(samples.get(i).point().timeS()<=samples.get(i-1).point().timeS())
            throw new IllegalArgumentException("accepted sample timestamps must strictly increase");
    }
    public Optional<Sample> lastAcceptedSample() {return samples.isEmpty()?Optional.empty():Optional.of(samples.get(samples.size()-1));}
    public Optional<FlightEventOccurrence3DOF> groundEvent() {return events.stream().filter(e->e.eventType()==FlightEventType.GROUND).findFirst();}
    public OptionalDouble estimatedGroundContactTimeS() {return groundEvent().isEmpty()?OptionalDouble.empty():OptionalDouble.of(groundEvent().orElseThrow().timeS());}
    public Optional<Vector> estimatedGroundContactPositionWorldM() {return groundEvent().map(e->e.estimatedState().positionWorldM());}
    public Optional<Vector> estimatedGroundContactVelocityWorldMS() {return groundEvent().map(e->e.estimatedState().velocityWorldMS());}
    public State recoveryState() {return lastAcceptedSample().map(Sample::recoveryState).orElse(State.NOT_TRIGGERED);}
}
