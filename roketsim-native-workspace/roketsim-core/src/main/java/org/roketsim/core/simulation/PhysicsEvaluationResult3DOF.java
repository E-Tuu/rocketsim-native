package org.roketsim.core.simulation;

import org.roketsim.core.environment.DryAirAtmosphereState;
import org.roketsim.core.environment.DryAirProperties;
import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.propulsion.MotorThrustState;
import org.roketsim.core.propulsion.MotorMassProperties;
import org.roketsim.core.mass.RocketMassProperties;
import org.roketsim.core.aerodynamics.BasicDragResult;
import org.roketsim.core.dynamics.TranslationalDynamicsResult;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.NumericalValidation;

/** Mapping outputs, accepted upstream snapshots and dynamics; no duplicate scalar authorities. */
public record PhysicsEvaluationResult3DOF(
        double environmentAltitudeM,
        double motorTimeS,
        DryAirAtmosphereState atmosphere,
        DryAirProperties airProperties,
        Vector airMassVelocityWorldMS,
        Vector relativeFlow,
        BasicFlightConditions flightConditions,
        MotorThrustState motorThrustState,
        MotorMassProperties motorMassProperties,
        RocketMassProperties rocketMassProperties,
        BasicDragResult basicDrag,
        Vector gravityAccelerationWorldMS2,
        TranslationalDynamicsResult dynamics) {
    public PhysicsEvaluationResult3DOF {
        NumericalValidation.requireFinite(environmentAltitudeM,"environment_altitude_m");
        NumericalValidation.requireFinite(motorTimeS,"motor_time_s");
        airMassVelocityWorldMS=Vector.of(airMassVelocityWorldMS,3,"air_mass_velocity_world_m_s");
        relativeFlow=Vector.of(relativeFlow,3,"relative_flow");
        gravityAccelerationWorldMS2=Vector.of(gravityAccelerationWorldMS2,3,"gravity_acceleration_world_m_s2");
    }
}
