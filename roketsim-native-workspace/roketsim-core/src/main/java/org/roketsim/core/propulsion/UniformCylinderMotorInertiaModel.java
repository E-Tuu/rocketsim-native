package org.roketsim.core.propulsion;
/** Explicit homogeneous-cylinder approximation, not a grain regression model. */
public record UniformCylinderMotorInertiaModel(double motorCgToleranceM) implements MotorInertiaModel {
    public UniformCylinderMotorInertiaModel { MotorInertiaEvaluator.nonnegative(motorCgToleranceM,"motorCgToleranceM"); }
}
