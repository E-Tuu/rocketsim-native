package org.roketsim.core.aerodynamics;

/** Accepted Python ReynoldsLengthPolicy selector; no additional model choices. */
public enum ReynoldsLengthPolicy {
    AERODYNAMIC_LENGTH("aerodynamic_length");
    private final String value;
    ReynoldsLengthPolicy(String value) { this.value=value; }
    public String value() { return value; }
}
