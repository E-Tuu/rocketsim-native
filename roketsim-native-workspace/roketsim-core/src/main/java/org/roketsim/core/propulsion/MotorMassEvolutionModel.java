package org.roketsim.core.propulsion;
/** Explicit policy, never selected from source availability. */
public enum MotorMassEvolutionModel {
    EXPLICIT_CURVE, IMPULSE_PROPORTIONAL;
    public String value() { return name().toLowerCase(java.util.Locale.ROOT); }
}
