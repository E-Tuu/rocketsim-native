package org.roketsim.core.propulsion;
import org.roketsim.core.mass.InertiaTensor3D;
/** Source tensor about instantaneous motor CG, with axes parallel to BODY. */
public record MotorInertiaSample(double motorTimeS,InertiaTensor3D inertiaAtMotorCg) {
    public MotorInertiaSample {
        MotorInertiaEvaluator.nonnegative(motorTimeS,"motorTimeS");
        MotorInertiaEvaluator.required(inertiaAtMotorCg,"inertiaAtMotorCg");
    }
}
