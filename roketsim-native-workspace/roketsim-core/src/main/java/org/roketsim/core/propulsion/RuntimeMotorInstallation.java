package org.roketsim.core.propulsion;
/** Explicit coaxial runtime installation. Nominal fit does not certify closure/flange clearance. */
public record RuntimeMotorInstallation(MotorPhysicsInput motor,double motorFrontXGeoM,double motorAftXGeoM,
        double nominalRadialClearanceM,double axialEngagementLengthM) implements InstalledMotorPhysicsInput {
    public RuntimeMotorInstallation {
        if(motor==null)throw new SourceMotorValidationError("MISSING_RUNTIME_MOTOR","motor");
        if(!Double.isFinite(motorFrontXGeoM)||!Double.isFinite(motorAftXGeoM)||motorAftXGeoM<=motorFrontXGeoM
                || !Double.isFinite(nominalRadialClearanceM)||nominalRadialClearanceM<0
                || !Double.isFinite(axialEngagementLengthM)||axialEngagementLengthM<=0)
            throw new SourceMotorValidationError("INVALID_RUNTIME_INSTALLATION","coordinates/clearance/engagement");
        // Use the resolver's aft-minus-length arithmetic, not a separately rounded span.
        if(motorFrontXGeoM!=motorAftXGeoM-motor.lengthM())
            throw new SourceMotorValidationError("RUNTIME_MOTOR_SPAN_MISMATCH","motorFrontXGeoM");
    }
}
