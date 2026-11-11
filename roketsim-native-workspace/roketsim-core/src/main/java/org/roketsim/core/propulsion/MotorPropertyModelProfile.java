package org.roketsim.core.propulsion;
/** Explicit independent mass and CG policies; no constructor defaults. */
public record MotorPropertyModelProfile(Object massModel,Object cgModel) {
    public static final MotorPropertyModelProfile DEMO_MOTOR_PROPERTY_MODEL_PROFILE =
        new MotorPropertyModelProfile(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,MotorCgEvolutionModel.FIXED_MIDPOINT);
    public MotorPropertyModelProfile {
        if(!(massModel instanceof MotorMassEvolutionModel)) { throw new PropulsionEvaluationError("INVALID_MOTOR_PROPERTY_MODEL","mass_model",massModel); }
        if(!(cgModel instanceof MotorCgEvolutionModel)) { throw new PropulsionEvaluationError("INVALID_MOTOR_PROPERTY_MODEL","cg_model",cgModel); }
    }
}
