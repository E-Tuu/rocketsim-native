package org.roketsim.core.aerodynamics;

/** Accepted Python BoundaryLayerModel selector; no additional model choices. */
public enum BoundaryLayerModel {
    FULLY_TURBULENT("fully_turbulent");
    private final String value;
    BoundaryLayerModel(String value) { this.value=value; }
    public String value() { return value; }
}
