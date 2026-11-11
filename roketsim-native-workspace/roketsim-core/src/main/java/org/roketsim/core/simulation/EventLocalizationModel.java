package org.roketsim.core.simulation;

/** Accepted NAT-018 ordinary-enum selector; no text conversion. */
public enum EventLocalizationModel {
    LINEAR_BRACKET_INTERPOLATION("linear_bracket_interpolation");
    private final String value;
    EventLocalizationModel(String value) { this.value=value; }
    public String value() { return value; }
}
