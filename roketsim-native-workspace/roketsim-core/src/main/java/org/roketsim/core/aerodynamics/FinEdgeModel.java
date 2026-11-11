package org.roketsim.core.aerodynamics;

/** Accepted Python FinEdgeModel selector; no additional model choices. */
public enum FinEdgeModel {
    TD13_SQUARE("td13_square");
    private final String value;
    FinEdgeModel(String value) { this.value=value; }
    public String value() { return value; }
}
