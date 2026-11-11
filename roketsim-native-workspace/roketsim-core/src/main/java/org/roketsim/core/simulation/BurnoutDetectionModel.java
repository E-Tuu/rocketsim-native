package org.roketsim.core.simulation;

/** Accepted NAT-018 ordinary-enum selector; no text conversion. */
public enum BurnoutDetectionModel {
    PROPULSION_CURVE_END_TIME("propulsion_curve_end_time");
    private final String value;
    BurnoutDetectionModel(String value) { this.value=value; }
    public String value() { return value; }
}
