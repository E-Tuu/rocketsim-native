package org.roketsim.core.aerodynamics;

/** Accepted Python PlumeTreatment selector; no additional model choices. */
public enum PlumeTreatment {
    IGNORED("ignored");
    private final String value;
    PlumeTreatment(String value) { this.value=value; }
    public String value() { return value; }
}
