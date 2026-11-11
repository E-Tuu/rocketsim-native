package org.roketsim.core.simulation;

import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Explicit single-parachute inputs; activation is not represented by a zero drag area. */
public record RecoveryConfiguration3DOF(double effectiveDragAreaM2,double deploymentDelayS) {
    public RecoveryConfiguration3DOF {
        requireFinite(effectiveDragAreaM2,"effective_drag_area_m2");
        if(effectiveDragAreaM2<=0)throw new IllegalArgumentException("effective_drag_area_m2 must be positive");
        requireFinite(deploymentDelayS,"deployment_delay_s");
        if(deploymentDelayS<0)throw new IllegalArgumentException("deployment_delay_s must be nonnegative");
    }
}
