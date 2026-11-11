package org.roketsim.core.simulation;

import org.roketsim.core.geometry.ResolvedRocketGeometry;
import org.roketsim.core.aerodynamics.SingleStageRocketAerodynamicSurfaces;
import org.roketsim.core.aerodynamics.BasicDragModelProfile;
import org.roketsim.core.mass.StructuralMassProperties;
import org.roketsim.core.propulsion.MotorInstallation;
import org.roketsim.core.propulsion.MotorPropertyModelProfile;
import org.roketsim.core.dynamics.TranslationalDynamicsModelProfile;
import org.roketsim.core.environment.*;
import org.roketsim.core.math.NumericalValidation;

/** Mandatory reference-preserving dependency bundle; launch altitude is geopotential height (m).
 * Wind is Object only because the accepted two final wind classes have no common interface.
 * Evaluation dispatch supports exactly NoWindModel and ConstantWindModel, with no fallback.
 */
public record PhysicsEvaluationContext3DOF(
        ResolvedRocketGeometry resolvedGeometry,
        SingleStageRocketAerodynamicSurfaces aerodynamicSurfaces,
        StructuralMassProperties structuralMassProperties,
        MotorInstallation motorInstallation,
        MotorPropertyModelProfile motorPropertyModelProfile,
        BasicDragModelProfile basicDragModelProfile,
        TranslationalDynamicsModelProfile translationalDynamicsModelProfile,
        Object environmentPositionMappingModel,
        double launchEnvironmentAltitudeM,
        PropulsionTimeline propulsionTimeline,
        USStandardAtmosphere1976Lower atmosphereModel,
        DryAirPropertiesCalculator airPropertiesCalculator,
        ConstantGravityModel gravityModel,
        Object windModel) {
    public PhysicsEvaluationContext3DOF {
        NumericalValidation.requireFinite(launchEnvironmentAltitudeM,"launch_environment_altitude_m");
    }
}
