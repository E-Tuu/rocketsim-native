package org.roketsim.core.simulation;
/** Accepted NAT-020 explicit lifecycle selector. */
public enum SimulationTerminationReason {
    TERMINAL_EVENT("terminal_event"), MAXIMUM_STEPS_REACHED("maximum_steps_reached");
    private final String value;
    SimulationTerminationReason(String value) { this.value=value; }
    public String value() { return value; }
}
