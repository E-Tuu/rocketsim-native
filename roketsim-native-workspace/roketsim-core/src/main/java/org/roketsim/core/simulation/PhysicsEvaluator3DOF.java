package org.roketsim.core.simulation;

import org.roketsim.core.environment.NoWindModel;
import org.roketsim.core.environment.ConstantWindModel;
import org.roketsim.core.flightconditions.RelativeFlowCalculator;
import org.roketsim.core.flightconditions.BasicFlightConditionsCalculator;
import org.roketsim.core.propulsion.MotorThrustCurveEvaluator;
import org.roketsim.core.propulsion.MotorPropertyEvaluator;
import org.roketsim.core.mass.RocketMassPropertiesCalculator;
import org.roketsim.core.aerodynamics.BasicDragEvaluator;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless NAT-015 instantaneous orchestration only; no integration, static aero or history. */
public final class PhysicsEvaluator3DOF {
    public PhysicsEvaluationResult3DOF evaluate(double timeS,TranslationalState3DOF state,
            LaunchConditions3DOF launchConditions,PhysicsEvaluationContext3DOF context) {
        double time=requireFinite(timeS,"time_s");
        // 1: Local WORLD ENU signed displacement, not a new altitude conversion.
        if(context.environmentPositionMappingModel()!=EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET) {
            throw new PhysicsEvaluationError("UNSUPPORTED_ENVIRONMENT_POSITION_MAPPING_MODEL",
                "environment_position_mapping_model",context.environmentPositionMappingModel());
        }
        double launchZ=launchConditions.initialPositionWorldM().get(2);
        double currentZ=state.positionWorldM().get(2);
        double deltaZ=currentZ-launchZ;
        double environmentAltitude=context.launchEnvironmentAltitudeM()+deltaZ;
        if(!Double.isFinite(environmentAltitude)) {
            throw new PhysicsEvaluationError("INVALID_ENVIRONMENT_ALTITUDE_MAPPING","environment_altitude_m",environmentAltitude);
        }
        // 2-6: Each result remains owned by its accepted upstream calculator.
        var atmosphere=context.atmosphereModel().evaluate(environmentAltitude);
        var airProperties=context.airPropertiesCalculator().evaluate(atmosphere);
        var gravity=context.gravityModel().evaluate();
        Vector airMassVelocity;
        if(context.windModel() instanceof NoWindModel wind) airMassVelocity=wind.evaluate();
        else if(context.windModel() instanceof ConstantWindModel wind) airMassVelocity=wind.evaluate();
        else throw new IllegalArgumentException("wind_model must be NoWindModel or ConstantWindModel");
        var relativeFlow=new RelativeFlowCalculator().evaluate(state.velocityWorldMS(),airMassVelocity);
        var flightConditions=new BasicFlightConditionsCalculator().evaluate(relativeFlow,atmosphere,airProperties,
            context.resolvedGeometry().referenceLengthM());
        // 7: Single motor-time mapping, deliberately after environment and flow.
        double motorTime=time-context.propulsionTimeline().ignitionTimeS();
        requireFinite(motorTime,"motor_time_s");
        if(motorTime<0.0) throw new PhysicsEvaluationError("BEFORE_IGNITION_UNSUPPORTED","motor_time_s",motorTime);
        // 8-11: No propulsion, mass or drag equations are duplicated here.
        var motorThrust=new MotorThrustCurveEvaluator().evaluate(context.motorInstallation().motor(),motorTime);
        var motorMass=new MotorPropertyEvaluator().evaluate(context.motorInstallation(),context.motorPropertyModelProfile(),motorTime);
        var rocketMass=new RocketMassPropertiesCalculator().evaluate(context.structuralMassProperties(),motorMass);
        var drag=new BasicDragEvaluator().evaluate(context.resolvedGeometry(),context.aerodynamicSurfaces(),
            flightConditions,airProperties,context.basicDragModelProfile());
        // 12-13: Ephemeral snapshot; it is not stored in the public result.
        var inputs=new TranslationalDynamicsInputs(rocketMass.totalMassKg(),motorThrust.thrustN(),relativeFlow,
            flightConditions.dynamicPressurePa(),context.resolvedGeometry().referenceAreaM2(),drag.totalCd0(),gravity);
        var dynamics=new TranslationalDynamicsEvaluator().evaluate(state,launchConditions,inputs,
            context.translationalDynamicsModelProfile());
        return new PhysicsEvaluationResult3DOF(environmentAltitude,motorTime,atmosphere,airProperties,airMassVelocity,
            relativeFlow,flightConditions,motorThrust,motorMass,rocketMass,drag,gravity,dynamics);
    }
}
