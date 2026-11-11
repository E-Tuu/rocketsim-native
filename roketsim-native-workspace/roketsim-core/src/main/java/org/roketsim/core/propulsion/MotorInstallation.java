package org.roketsim.core.propulsion;
/** Derived single-coaxial installation, x_geo positive nose to tail (metres). */
public record MotorInstallation(MotorDefinition motor, double motorFrontXGeoM, double motorAftXGeoM,
        double nominalRadialClearanceM, double axialEngagementLengthM) { }
