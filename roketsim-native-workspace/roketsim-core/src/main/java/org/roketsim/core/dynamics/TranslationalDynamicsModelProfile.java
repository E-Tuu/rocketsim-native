package org.roketsim.core.dynamics;

/** Two mandatory source policies; invalid selections are rejected by the evaluator. */
public record TranslationalDynamicsModelProfile(Object thrustDirectionModel,Object aerodynamicForceModel) {
    public static final TranslationalDynamicsModelProfile NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE=
        new TranslationalDynamicsModelProfile(ThrustDirectionModel.FIXED_LAUNCH_DIRECTION,AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY);
}
