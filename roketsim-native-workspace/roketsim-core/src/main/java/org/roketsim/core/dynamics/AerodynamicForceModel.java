package org.roketsim.core.dynamics;

/** Ordinary Python Enum counterpart; evaluator selection uses identity, not text. */
public enum AerodynamicForceModel {
    BASELINE_CD0_DRAG_ONLY("baseline_cd0_drag_only");
    private final String value;
    AerodynamicForceModel(String value) { this.value=value; }
    public String value() { return value; }
}
