package org.roketsim.core.propulsion;
/** Upstream motor result, not structural/rocket mass authority. */
public record MotorMassProperties(double massKg,double cgLocalFromFrontM,double cgXGeoM,
        MotorPropertyModelProfile modelProfile) { }
