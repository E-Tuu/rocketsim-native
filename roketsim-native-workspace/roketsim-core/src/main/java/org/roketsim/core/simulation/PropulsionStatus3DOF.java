package org.roketsim.core.simulation;

/** Source-curve clock classification independent of physical phase and thrust magnitude. */
public enum PropulsionStatus3DOF {
    /** Absolute time is before ignition. */
    PRE_IGNITION,
    /** Within the source-curve interval; instantaneous thrust may be zero. */
    CURVE_ACTIVE,
    /** At or after the source-curve endpoint. */
    POST_BURNOUT
}
