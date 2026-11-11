package org.roketsim.core.simulation;

/** Accepted NAT-018 ordinary-enum selector; no text conversion. */
public enum ApogeeDetectionModel {
    WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING("world_vertical_velocity_downward_crossing");
    private final String value;
    ApogeeDetectionModel(String value) { this.value=value; }
    public String value() { return value; }
}
