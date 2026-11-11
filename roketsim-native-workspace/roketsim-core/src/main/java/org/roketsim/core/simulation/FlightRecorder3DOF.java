package org.roketsim.core.simulation;
import java.util.ArrayList;
import java.util.List;

/** Intentionally stateful passive recorder; owns only two append-only histories. */
public final class FlightRecorder3DOF {
    private final List<RecordedFlightSample3DOF> samples=new ArrayList<>();
    private final List<FlightEventOccurrence3DOF> events=new ArrayList<>();
    public void recordSample(org.roketsim.core.numerics.IntegrationPoint3DOF point,PhysicsEvaluationResult3DOF physics) {
        var sample=new RecordedFlightSample3DOF(point,physics);
        if(!samples.isEmpty() && point.timeS()<=samples.get(samples.size()-1).point().timeS())
            throw new FlightRecordingError("NON_INCREASING_SAMPLE_TIME","point.time_s",point.timeS());
        samples.add(sample);
    }
    public void recordEvents(List<FlightEventOccurrence3DOF> incoming) {
        var batch=List.copyOf(incoming);
        Double previous=events.isEmpty()?null:events.get(events.size()-1).timeS();
        for(var event:batch) {
            if(previous!=null && event.timeS()<previous)throw new FlightRecordingError("NON_MONOTONIC_EVENT_TIME","event.time_s",event.timeS());
            previous=event.timeS();
        }
        events.addAll(batch);
    }
    public RecordedFlightData3DOF snapshot() { return new RecordedFlightData3DOF(samples,events); }
}
