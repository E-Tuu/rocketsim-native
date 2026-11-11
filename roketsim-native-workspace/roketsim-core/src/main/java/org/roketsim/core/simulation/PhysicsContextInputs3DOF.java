package org.roketsim.core.simulation;
import org.roketsim.core.geometry.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
/** Internal dependency view; legacy access remains lazy and unvalidated. */
interface PhysicsContextInputs3DOF {
    ResolvedRocketGeometry resolvedGeometry();
    SingleStageRocketAerodynamicSurfaces aerodynamicSurfaces();
    StructuralMassProperties structuralMassProperties();
    InstalledMotorPhysicsInput motorInstallation();
    MotorPropertyModelProfile motorPropertyModelProfile();
    BasicDragModelProfile basicDragModelProfile();
    TranslationalDynamicsModelProfile translationalDynamicsModelProfile();
    Object environmentPositionMappingModel();
    double launchEnvironmentAltitudeM();
    PropulsionTimeline propulsionTimeline();
    USStandardAtmosphere1976Lower atmosphereModel();
    DryAirPropertiesCalculator airPropertiesCalculator();
    ConstantGravityModel gravityModel();
    Object windModel();
    static PhysicsContextInputs3DOF legacy(PhysicsEvaluationContext3DOF source) {return new Legacy(source);}
    record Legacy(PhysicsEvaluationContext3DOF source) implements PhysicsContextInputs3DOF {
        public ResolvedRocketGeometry resolvedGeometry() {return source.resolvedGeometry();}
        public SingleStageRocketAerodynamicSurfaces aerodynamicSurfaces() {return source.aerodynamicSurfaces();}
        public StructuralMassProperties structuralMassProperties() {return source.structuralMassProperties();}
        public InstalledMotorPhysicsInput motorInstallation() {var i=source.motorInstallation();return i==null?null:MotorPhysicsInputs.fromLegacyInstallation(i);}
        public MotorPropertyModelProfile motorPropertyModelProfile() {return source.motorPropertyModelProfile();}
        public BasicDragModelProfile basicDragModelProfile() {return source.basicDragModelProfile();}
        public TranslationalDynamicsModelProfile translationalDynamicsModelProfile() {return source.translationalDynamicsModelProfile();}
        public Object environmentPositionMappingModel() {return source.environmentPositionMappingModel();}
        public double launchEnvironmentAltitudeM() {return source.launchEnvironmentAltitudeM();}
        public PropulsionTimeline propulsionTimeline() {return source.propulsionTimeline();}
        public USStandardAtmosphere1976Lower atmosphereModel() {return source.atmosphereModel();}
        public DryAirPropertiesCalculator airPropertiesCalculator() {return source.airPropertiesCalculator();}
        public ConstantGravityModel gravityModel() {return source.gravityModel();}
        public Object windModel() {return source.windModel();}
    }
}
