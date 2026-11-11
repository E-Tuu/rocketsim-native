package org.roketsim.core.simulation;
import org.roketsim.core.dynamics.TranslationalState3DOF;

/** Estimated linear event state, not an accepted integrated trajectory sample. */
public record FlightEventOccurrence3DOF(FlightEventType eventType,double timeS,
        double interpolationFraction,TranslationalState3DOF estimatedState) {
    public boolean isTerminal() { return eventType==FlightEventType.GROUND; }
}
