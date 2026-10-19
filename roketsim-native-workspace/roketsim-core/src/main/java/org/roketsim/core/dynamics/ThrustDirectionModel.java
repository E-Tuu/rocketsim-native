package org.roketsim.core.dynamics;

/** Ordinary Python Enum counterpart; evaluator selection uses identity, not text. */
public enum ThrustDirectionModel {
    FIXED_LAUNCH_DIRECTION("fixed_launch_direction");
    private final String value;
    ThrustDirectionModel(String value) { this.value=value; }
    public String value() { return value; }
}
