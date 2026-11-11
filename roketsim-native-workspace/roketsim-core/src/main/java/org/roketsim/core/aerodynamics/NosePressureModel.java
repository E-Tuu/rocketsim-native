package org.roketsim.core.aerodynamics;

/** Accepted Python NosePressureModel selector; no additional model choices. */
public enum NosePressureModel {
    TD13_CONICAL_SUBSONIC_TO_SONIC("td13_conical_subsonic_to_sonic");
    private final String value;
    NosePressureModel(String value) { this.value=value; }
    public String value() { return value; }
}
