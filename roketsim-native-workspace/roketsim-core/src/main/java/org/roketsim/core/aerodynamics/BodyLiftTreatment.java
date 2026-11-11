package org.roketsim.core.aerodynamics;

/** Accepted Python BodyLiftTreatment selector; no additional model choices. */
public enum BodyLiftTreatment {
    LINEARIZED_ZERO_AOA("linearized_zero_aoa");
    private final String value;
    BodyLiftTreatment(String value) { this.value=value; }
    public String value() { return value; }
}
