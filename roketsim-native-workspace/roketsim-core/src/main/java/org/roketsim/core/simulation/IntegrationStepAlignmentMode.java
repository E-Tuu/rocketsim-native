package org.roketsim.core.simulation;

/** Explicit numerical scheduling policy; source knots are not physical events. */
public enum IntegrationStepAlignmentMode {
    LEGACY_FIXED_STEP,
    ALIGN_MOTOR_SOURCE_BOUNDARIES
}
