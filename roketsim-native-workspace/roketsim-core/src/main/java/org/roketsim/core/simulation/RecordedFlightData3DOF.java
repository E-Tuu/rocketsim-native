package org.roketsim.core.simulation;
import java.util.List;

/** Immutable isolated list adaptation of the accepted Python tuple streams. */
public record RecordedFlightData3DOF(List<RecordedFlightSample3DOF> samples,List<FlightEventOccurrence3DOF> events) {
    public RecordedFlightData3DOF {
        samples=List.copyOf(samples); events=List.copyOf(events);
        for(var sample:samples)java.util.Objects.requireNonNull(sample);
        for(var event:events)java.util.Objects.requireNonNull(event);
    }
}
