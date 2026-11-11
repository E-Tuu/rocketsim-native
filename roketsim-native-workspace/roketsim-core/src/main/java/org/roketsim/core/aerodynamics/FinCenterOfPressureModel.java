package org.roketsim.core.aerodynamics;

/** Accepted Python FinCenterOfPressureModel selector; no additional model choices. */
public enum FinCenterOfPressureModel {
    EXTENDED_BARROWMAN_MACH_DEPENDENT("extended_barrowman_mach_dependent");
    private final String value;
    FinCenterOfPressureModel(String value) { this.value=value; }
    public String value() { return value; }
}
