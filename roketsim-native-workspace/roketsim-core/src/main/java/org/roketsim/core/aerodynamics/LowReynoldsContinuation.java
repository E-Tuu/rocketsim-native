package org.roketsim.core.aerodynamics;

/** Accepted Python LowReynoldsContinuation selector; no additional model choices. */
public enum LowReynoldsContinuation {
    MINIMUM_EVALUATION_REYNOLDS("minimum_evaluation_reynolds");
    private final String value;
    LowReynoldsContinuation(String value) { this.value=value; }
    public String value() { return value; }
}
