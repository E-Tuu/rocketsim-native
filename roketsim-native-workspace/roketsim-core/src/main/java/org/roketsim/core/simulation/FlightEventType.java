package org.roketsim.core.simulation;

/** Accepted NAT-018 ordinary-enum selector; no text conversion. */
public enum FlightEventType {
    BURNOUT("burnout"),
    APOGEE("apogee"),
    GROUND("ground");
    private final String value;
    FlightEventType(String value) { this.value=value; }
    public String value() { return value; }
}
