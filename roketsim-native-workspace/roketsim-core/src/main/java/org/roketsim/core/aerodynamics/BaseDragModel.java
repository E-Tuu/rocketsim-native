package org.roketsim.core.aerodynamics;

/** Accepted Python BaseDragModel selector; no additional model choices. */
public enum BaseDragModel {
    TD13_SUBSONIC_TO_SONIC("td13_subsonic_to_sonic");
    private final String value;
    BaseDragModel(String value) { this.value=value; }
    public String value() { return value; }
}
