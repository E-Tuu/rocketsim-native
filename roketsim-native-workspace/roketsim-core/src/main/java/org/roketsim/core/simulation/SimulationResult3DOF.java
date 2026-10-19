package org.roketsim.core.simulation;
import java.util.List;
import java.util.Objects;

/** Immutable query facade; execution is its sole stored authority. Ignition is not liftoff. */
public record SimulationResult3DOF(SimulationExecution3DOF execution) {
    public SimulationResult3DOF {
        Objects.requireNonNull(execution,"execution must be SimulationExecution3DOF");
        var samples=execution.recordedData().samples(); var events=execution.recordedData().events();
        if(samples.isEmpty())throw new SimulationResultError("EMPTY_RECORDED_SAMPLES","execution.recorded_data.samples",samples);
        var terminal=events.stream().filter(FlightEventOccurrence3DOF::isTerminal).toList();
        if(execution.terminationReason()==SimulationTerminationReason.TERMINAL_EVENT) {
            if(terminal.isEmpty())throw new SimulationResultError("TERMINATION_EVENT_MISSING","execution.recorded_data.events",events);
            if(terminal.size()>1)throw new SimulationResultError("MULTIPLE_TERMINAL_EVENTS","execution.recorded_data.events",terminal);
        } else if(execution.terminationReason()==SimulationTerminationReason.MAXIMUM_STEPS_REACHED && !terminal.isEmpty())
            throw new SimulationResultError("UNEXPECTED_TERMINAL_EVENT","execution.recorded_data.events",terminal);
        double termination=execution.terminationReason()==SimulationTerminationReason.TERMINAL_EVENT?terminal.get(0).timeS():samples.get(samples.size()-1).point().timeS();
        if(termination<samples.get(0).point().timeS())throw new SimulationResultError("INVALID_TERMINATION_TIME","termination_time_s",termination);
    }
    public RecordedFlightData3DOF recordedData() { return execution.recordedData(); }
    public List<RecordedFlightSample3DOF> samples() { return recordedData().samples(); }
    public List<FlightEventOccurrence3DOF> events() { return recordedData().events(); }
    public SimulationTerminationReason terminationReason() { return execution.terminationReason(); }
    public int stepsPerformed() { return execution.stepsPerformed(); }
    public RecordedFlightSample3DOF initialSample() { return samples().get(0); }
    public RecordedFlightSample3DOF lastAcceptedSample() { return samples().get(samples().size()-1); }
    public List<FlightEventOccurrence3DOF> eventsOfType(FlightEventType eventType) {
        Objects.requireNonNull(eventType,"event_type must be FlightEventType");
        return events().stream().filter(e->e.eventType()==eventType).toList();
    }
    private FlightEventOccurrence3DOF singular(FlightEventType type) {
        var matches=eventsOfType(type);
        if(matches.size()>1)throw new SimulationResultError("AMBIGUOUS_EVENT_HISTORY","event_type",type);
        return matches.isEmpty()?null:matches.get(0);
    }
    public FlightEventOccurrence3DOF burnoutEvent() { return singular(FlightEventType.BURNOUT); }
    public FlightEventOccurrence3DOF apogeeEvent() { return singular(FlightEventType.APOGEE); }
    public FlightEventOccurrence3DOF groundEvent() { return singular(FlightEventType.GROUND); }
    public FlightEventOccurrence3DOF terminalEvent() { return events().stream().filter(FlightEventOccurrence3DOF::isTerminal).findFirst().orElse(null); }
    public double terminationTimeS() { return terminationReason()==SimulationTerminationReason.TERMINAL_EVENT?terminalEvent().timeS():lastAcceptedSample().point().timeS(); }
    public double simulationDurationS() { return terminationTimeS()-initialSample().point().timeS(); }
}
