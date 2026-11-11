package org.roketsim.core.propulsion;
/** Explicit policy, never selected from source availability. */
public enum MotorCgEvolutionModel {
    EXPLICIT_CURVE, FIXED_MIDPOINT;
    public String value() { return name().toLowerCase(java.util.Locale.ROOT); }
}
