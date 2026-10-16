package org.roketsim.core.aerodynamics;

/** Accepted Python StaticNormalForceModel selector; no additional model choices. */
public enum StaticNormalForceModel {
    EXTENDED_BARROWMAN_SUBSONIC("extended_barrowman_subsonic");
    private final String value;
    StaticNormalForceModel(String value) { this.value=value; }
    public String value() { return value; }
}
