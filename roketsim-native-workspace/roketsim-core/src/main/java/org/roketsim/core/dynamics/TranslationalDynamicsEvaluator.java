package org.roketsim.core.dynamics;
import java.util.List;
import org.roketsim.core.math.Vectors;

/** Stateless instantaneous point-mass equations; no upstream evaluation or integration. */
public final class TranslationalDynamicsEvaluator {
    public TranslationalDynamicsResult evaluate(TranslationalState3DOF state,LaunchConditions3DOF launchConditions,
            TranslationalDynamicsInputs inputs,TranslationalDynamicsModelProfile modelProfile) {
        if(modelProfile.thrustDirectionModel()!=ThrustDirectionModel.FIXED_LAUNCH_DIRECTION)
            throw new DynamicsEvaluationError("UNSUPPORTED_THRUST_DIRECTION_MODEL","thrust_direction_model",modelProfile.thrustDirectionModel());
        if(modelProfile.aerodynamicForceModel()!=AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY)
            throw new DynamicsEvaluationError("UNSUPPORTED_AERODYNAMIC_FORCE_MODEL","aerodynamic_force_model",modelProfile.aerodynamicForceModel());
        double[] thrust=new double[3],gravity=new double[3],drag=new double[3];
        for(int i=0;i<3;i++) {
            thrust[i]=inputs.thrustN()*launchConditions.launchDirectionWorldUnit().get(i);
            gravity[i]=inputs.massKg()*inputs.gravityAccelerationWorldMS2().get(i);
        }
        double relativeSpeed=Vectors.magnitude(inputs.relativeVelocityWorldMS());
        if(relativeSpeed==0) {
            if(inputs.dynamicPressurePa()>0) throw new DynamicsEvaluationError("INCONSISTENT_DRAG_INPUTS","dynamic_pressure_Pa",inputs.dynamicPressurePa());
        } else {
            double dragMagnitude=(inputs.dynamicPressurePa()*inputs.referenceAreaM2())*inputs.dragCoefficientCd0();
            for(int i=0;i<3;i++) drag[i]=(-dragMagnitude*inputs.relativeVelocityWorldMS().get(i))/relativeSpeed;
        }
        var forces=new TranslationalForces3DOF(thrust,drag,gravity);
        var net=forces.netForceWorldN();
        double[] acceleration=new double[3]; boolean finite=true;
        for(int i=0;i<3;i++) { acceleration[i]=net.get(i)/inputs.massKg(); finite&=Double.isFinite(acceleration[i]); }
        if(!finite) throw new DynamicsEvaluationError("INVALID_ACCELERATION","velocity_derivative_world_m_s2",List.of(acceleration[0],acceleration[1],acceleration[2]));
        var derivative=new TranslationalStateDerivative3DOF(state.velocityWorldMS(),org.roketsim.core.math.Vector.of(acceleration,3,"velocity_derivative_world_m_s2"));
        return new TranslationalDynamicsResult(forces,derivative,modelProfile);
    }
}
