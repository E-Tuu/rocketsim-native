package org.roketsim.core.simulation;

/** Accepted NAT-018 ordinary-enum selector; no text conversion. */
public enum GroundReferenceModel {
    LAUNCH_WORLD_Z_PLANE("launch_world_z_plane");
    private final String value;
    GroundReferenceModel(String value) { this.value=value; }
    public String value() { return value; }
}
