package org.roketsim.core.propulsion;
/** Installation coordinates used by the existing axial CG evaluator. */
public sealed interface InstalledMotorPhysicsInput permits MotorPhysicsInputs.LegacyInstallationView,RuntimeMotorInstallation {
    MotorPhysicsInput motor();
    double motorFrontXGeoM();
}
