package org.roketsim.core.simulation;
import org.roketsim.core.geometry.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
/** Additive source-capable context; legacy positional context is unchanged. */
public record RuntimePhysicsEvaluationContext3DOF(
        ResolvedRocketGeometry resolvedGeometry,
        SingleStageRocketAerodynamicSurfaces aerodynamicSurfaces,
        StructuralMassProperties structuralMassProperties,
        RuntimeMotorInstallation motorInstallation,
        MotorPropertyModelProfile motorPropertyModelProfile,
        BasicDragModelProfile basicDragModelProfile,
        TranslationalDynamicsModelProfile translationalDynamicsModelProfile,
        Object environmentPositionMappingModel,
        double launchEnvironmentAltitudeM,
        PropulsionTimeline propulsionTimeline,
        USStandardAtmosphere1976Lower atmosphereModel,
        DryAirPropertiesCalculator airPropertiesCalculator,
        ConstantGravityModel gravityModel,
        Object windModel) implements PhysicsContextInputs3DOF {
    public RuntimePhysicsEvaluationContext3DOF {
        if(resolvedGeometry==null || aerodynamicSurfaces==null || structuralMassProperties==null || motorInstallation==null
                || motorPropertyModelProfile==null || basicDragModelProfile==null || translationalDynamicsModelProfile==null
                || environmentPositionMappingModel==null || propulsionTimeline==null || atmosphereModel==null
                || airPropertiesCalculator==null || gravityModel==null || windModel==null)
            throw new SourceMotorValidationError("MISSING_RUNTIME_CONTEXT_INPUT","physicsContext");
        org.roketsim.core.math.NumericalValidation.requireFinite(launchEnvironmentAltitudeM,"launch_environment_altitude_m");
        if(motorInstallation.motor() instanceof SourceBackedMotorProfile profile && !profile.propertyModelProfile().equals(motorPropertyModelProfile))
            throw new SourceMotorValidationError("MOTOR_MODEL_SELECTION_MISMATCH","motorPropertyModelProfile");
        var expected=new MotorInstallationResolver().resolveRuntime(resolvedGeometry,motorInstallation.motor());
        requireInstallationValue(motorInstallation.motorFrontXGeoM(),expected.motorFrontXGeoM(),"motorFrontXGeoM");
        requireInstallationValue(motorInstallation.motorAftXGeoM(),expected.motorAftXGeoM(),"motorAftXGeoM");
        requireInstallationValue(motorInstallation.nominalRadialClearanceM(),expected.nominalRadialClearanceM(),"nominalRadialClearanceM");
        requireInstallationValue(motorInstallation.axialEngagementLengthM(),expected.axialEngagementLengthM(),"axialEngagementLengthM");
    }
    private static void requireInstallationValue(double supplied,double expected,String field) {
        if(supplied!=expected)throw new SourceMotorValidationError("RUNTIME_INSTALLATION_GEOMETRY_MISMATCH",field);
    }
}
