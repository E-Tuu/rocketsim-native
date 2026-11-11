package org.roketsim.core.dynamics;
import java.util.List;
import org.roketsim.core.math.Vectors;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless instantaneous point-mass equations; no upstream evaluation or integration. */
public final class TranslationalDynamicsEvaluator {
    public TranslationalDynamicsResult evaluate(TranslationalState3DOF state,LaunchConditions3DOF launchConditions,
            TranslationalDynamicsInputs inputs,TranslationalDynamicsModelProfile modelProfile) {
        if(modelProfile.thrustDirectionModel()!=ThrustDirectionModel.FIXED_LAUNCH_DIRECTION)
            throw new DynamicsEvaluationError("UNSUPPORTED_THRUST_DIRECTION_MODEL","thrust_direction_model",modelProfile.thrustDirectionModel());
        if(modelProfile.aerodynamicForceModel()!=AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY)
            throw new DynamicsEvaluationError("UNSUPPORTED_AERODYNAMIC_FORCE_MODEL","aerodynamic_force_model",modelProfile.aerodynamicForceModel());
        var directed=directedForces(launchConditions,inputs.massKg(),inputs.thrustN(),inputs.gravityAccelerationWorldMS2());
        double[] thrust=directed[0],gravity=directed[1],drag=new double[3];
        double relativeSpeed=Vectors.magnitude(inputs.relativeVelocityWorldMS());
        if(relativeSpeed==0) {
            if(inputs.dynamicPressurePa()>0) throw new DynamicsEvaluationError("INCONSISTENT_DRAG_INPUTS","dynamic_pressure_Pa",inputs.dynamicPressurePa());
        } else {
            double dragMagnitude=(inputs.dynamicPressurePa()*inputs.referenceAreaM2())*inputs.dragCoefficientCd0();
            for(int i=0;i<3;i++) drag[i]=(-dragMagnitude*inputs.relativeVelocityWorldMS().get(i))/relativeSpeed;
        }
        var forces=new TranslationalForces3DOF(thrust,drag,gravity);
        return new TranslationalDynamicsResult(forces,derivative(state,inputs.massKg(),forces),modelProfile);
    }

    /** Evaluated forces/derivative without falsely attaching a baseline CD0 model profile. */
    public record ForceEvaluation(TranslationalForces3DOF forces,TranslationalStateDerivative3DOF derivative) { }

    /** Explicit supplied aerodynamic force; the same class still owns thrust, gravity and F/m.
     * No aerodynamic law is selected or recomputed here.
     */
    public ForceEvaluation evaluateWithAerodynamicForce(TranslationalState3DOF state,LaunchConditions3DOF launch,
            double massKg,double thrustN,Vector aerodynamicForceWorldN,Vector gravityAccelerationWorldMS2,
            Object thrustDirectionModel) {
        if(thrustDirectionModel!=ThrustDirectionModel.FIXED_LAUNCH_DIRECTION)
            throw new DynamicsEvaluationError("UNSUPPORTED_THRUST_DIRECTION_MODEL","thrust_direction_model",thrustDirectionModel);
        requireFinite(massKg,"mass_kg");requireFinite(thrustN,"thrust_N");
        if(massKg<=0)throw new DynamicsEvaluationError("INVALID_MASS","mass_kg",massKg);
        if(thrustN<0)throw new DynamicsEvaluationError("INVALID_THRUST","thrust_N",thrustN);
        var directed=directedForces(launch,massKg,thrustN,Vector.of(gravityAccelerationWorldMS2,3,"gravity_acceleration_world_m_s2"));
        var forces=new TranslationalForces3DOF(directed[0],Vector.of(aerodynamicForceWorldN,3,"aerodynamic_force_world_N"),directed[1]);
        return new ForceEvaluation(forces,derivative(state,massKg,forces));
    }
    private static double[][] directedForces(LaunchConditions3DOF launch,double mass,double thrustN,Vector gravityAcceleration) {
        double[] thrust=new double[3],gravity=new double[3];
        for(int i=0;i<3;i++) {
            thrust[i]=thrustN*launch.launchDirectionWorldUnit().get(i);
            gravity[i]=mass*gravityAcceleration.get(i);
        }
        return new double[][]{thrust,gravity};
    }
    private static TranslationalStateDerivative3DOF derivative(TranslationalState3DOF state,double mass,TranslationalForces3DOF forces) {
        var net=forces.netForceWorldN();
        double[] acceleration=new double[3]; boolean finite=true;
        for(int i=0;i<3;i++) { acceleration[i]=net.get(i)/mass; finite&=Double.isFinite(acceleration[i]); }
        if(!finite) throw new DynamicsEvaluationError("INVALID_ACCELERATION","velocity_derivative_world_m_s2",List.of(acceleration[0],acceleration[1],acceleration[2]));
        return new TranslationalStateDerivative3DOF(state.velocityWorldMS(),org.roketsim.core.math.Vector.of(acceleration,3,"velocity_derivative_world_m_s2"));
    }
}
