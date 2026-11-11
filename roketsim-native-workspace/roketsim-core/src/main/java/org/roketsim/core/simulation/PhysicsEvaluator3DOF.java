package org.roketsim.core.simulation;

import org.roketsim.core.environment.NoWindModel;
import org.roketsim.core.environment.ConstantWindModel;
import org.roketsim.core.flightconditions.RelativeFlowCalculator;
import org.roketsim.core.flightconditions.BasicFlightConditionsCalculator;
import org.roketsim.core.propulsion.MotorThrustCurveEvaluator;
import org.roketsim.core.propulsion.MotorPropertyEvaluator;
import org.roketsim.core.mass.RocketMassPropertiesCalculator;
import org.roketsim.core.aerodynamics.BasicDragEvaluator;
import org.roketsim.core.aerodynamics.RecoveryDragEvaluator3DOF;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.mass.RocketMassProperties;
import org.roketsim.core.propulsion.MotorThrustState;
import org.roketsim.core.propulsion.MotorMassProperties;
import java.util.Optional;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless NAT-015 instantaneous orchestration only; no integration, static aero or history. */
public final class PhysicsEvaluator3DOF {
    public PhysicsEvaluationResult3DOF evaluate(double timeS,TranslationalState3DOF state,
            LaunchConditions3DOF launchConditions,PhysicsEvaluationContext3DOF context) {
        return evaluatePoint(timeS,state,launchConditions,PhysicsContextInputs3DOF.legacy(context),false,null,null);
    }

    /** NAT-024 explicit attached inputs; validation and mass arithmetic stay in NAT-023.
     * @param timeS accepted simulation time (s)
     * @param state current WORLD translational state
     * @param launchConditions accepted launch authority
     * @param context accepted upstream physics authorities
     * @param auxiliaryMassesKg transient explicit masses (kg), never retained
     * @param auxiliaryCgXGeoM aligned transient axial CG coordinates (m)
     * @return existing nested physics snapshot
     */
    public PhysicsEvaluationResult3DOF evaluate(double timeS,TranslationalState3DOF state,
            LaunchConditions3DOF launchConditions,PhysicsEvaluationContext3DOF context,
            double[] auxiliaryMassesKg,double[] auxiliaryCgXGeoM) {
        if(auxiliaryMassesKg!=null && auxiliaryCgXGeoM!=null
                && auxiliaryMassesKg.length==0 && auxiliaryCgXGeoM.length==0) {
            return evaluate(timeS,state,launchConditions,context);
        }
        return evaluatePoint(timeS,state,launchConditions,PhysicsContextInputs3DOF.legacy(context),true,auxiliaryMassesKg,auxiliaryCgXGeoM);
    }

    public PhysicsEvaluationResult3DOF evaluateRuntime(double timeS,TranslationalState3DOF state,LaunchConditions3DOF launch,
            RuntimePhysicsEvaluationContext3DOF context,double[] masses,double[] cgs) {
        return evaluateInputs(timeS,state,launch,context,masses,cgs);
    }
    PhysicsEvaluationResult3DOF evaluateInputs(double timeS,TranslationalState3DOF state,LaunchConditions3DOF launch,
            PhysicsContextInputs3DOF context,double[] masses,double[] cgs) {
        boolean withAuxiliary=!(masses!=null && cgs!=null && masses.length==0 && cgs.length==0);
        return evaluatePoint(timeS,state,launch,context,withAuxiliary,masses,cgs);
    }
    private PhysicsEvaluationResult3DOF evaluatePoint(double timeS,TranslationalState3DOF state,
            LaunchConditions3DOF launchConditions,PhysicsContextInputs3DOF context,boolean withAuxiliary,
            double[] auxiliaryMassesKg,double[] auxiliaryCgXGeoM) {
        var u=evaluateUpstream(timeS,state,launchConditions,context,withAuxiliary,auxiliaryMassesKg,auxiliaryCgXGeoM);
        var drag=new BasicDragEvaluator().evaluate(context.resolvedGeometry(),context.aerodynamicSurfaces(),
            u.flightConditions(),u.airProperties(),context.basicDragModelProfile());
        var inputs=new TranslationalDynamicsInputs(u.rocketMass().totalMassKg(),u.motorThrust().thrustN(),u.relativeFlow(),
            u.flightConditions().dynamicPressurePa(),context.resolvedGeometry().referenceAreaM2(),drag.totalCd0(),u.gravity());
        var dynamics=new TranslationalDynamicsEvaluator().evaluate(state,launchConditions,inputs,context.translationalDynamicsModelProfile());
        return new PhysicsEvaluationResult3DOF(u.altitude(),u.motorTime(),u.atmosphere(),u.airProperties(),u.airMassVelocity(),
            u.relativeFlow(),u.flightConditions(),u.motorThrust(),u.motorMass(),u.rocketMass(),drag,u.gravity(),dynamics);
    }

    // Only upstream values, evaluated once in the original order. No new public context/result schema.
    private record Upstream(double altitude,double motorTime,DryAirAtmosphereState atmosphere,DryAirProperties airProperties,
            Vector airMassVelocity,Vector relativeFlow,BasicFlightConditions flightConditions,MotorThrustState motorThrust,
            MotorMassProperties motorMass,RocketMassProperties rocketMass,Vector gravity) { }

    RecoveryFlightResult3DOF.Physics recoveryAt(double timeS,TranslationalState3DOF state,LaunchConditions3DOF launch,
            PhysicsContextInputs3DOF context,double[] masses,double[] cgs,RecoveryConfiguration3DOF recovery,boolean active) {
        if(!active)return RecoveryFlightResult3DOF.Physics.baseline(evaluateInputs(timeS,state,launch,context,masses,cgs));
        var u=evaluateUpstream(timeS,state,launch,context,true,masses,cgs);
        var aerodynamic=new RecoveryDragEvaluator3DOF().evaluate(u.atmosphere().densityKgM3(),u.relativeFlow(),recovery.effectiveDragAreaM2());
        var dynamics=new TranslationalDynamicsEvaluator().evaluateWithAerodynamicForce(state,launch,u.rocketMass().totalMassKg(),
            u.motorThrust().thrustN(),aerodynamic,u.gravity(),context.translationalDynamicsModelProfile().thrustDirectionModel());
        return new RecoveryFlightResult3DOF.Physics(u.altitude(),u.motorTime(),u.atmosphere(),u.airProperties(),u.airMassVelocity(),
            u.relativeFlow(),u.flightConditions(),u.motorThrust(),u.motorMass(),u.rocketMass(),Optional.empty(),u.gravity(),
            dynamics,RecoveryFlightResult3DOF.AerodynamicModel.CONSTANT_PARACHUTE_DRAG);
    }

    private Upstream evaluateUpstream(double timeS,TranslationalState3DOF state,
            LaunchConditions3DOF launchConditions,PhysicsContextInputs3DOF context,boolean withAuxiliary,
            double[] auxiliaryMassesKg,double[] auxiliaryCgXGeoM) {
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
        var motorThrust=new MotorThrustCurveEvaluator().evaluateRuntime(context.motorInstallation().motor(),motorTime);
        var motorMass=new MotorPropertyEvaluator().evaluateRuntime(context.motorInstallation(),context.motorPropertyModelProfile(),motorTime);
        var massCalculator=new RocketMassPropertiesCalculator();
        var rocketMass=withAuxiliary
            ?massCalculator.evaluate(context.structuralMassProperties(),motorMass,auxiliaryMassesKg,auxiliaryCgXGeoM)
            :massCalculator.evaluate(context.structuralMassProperties(),motorMass);
        return new Upstream(environmentAltitude,motorTime,atmosphere,airProperties,airMassVelocity,relativeFlow,
            flightConditions,motorThrust,motorMass,rocketMass,gravity);
    }
}
