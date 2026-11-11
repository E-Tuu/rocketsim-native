package org.roketsim.core.propulsion;
/** Curve-derived characterization; 5-percent times do not terminate runtime thrust. */
public record MotorCurveStatistics(double totalImpulseNS,double peakThrustN,double peakThrustTimeS,
        double curveEndTimeS,double effectiveBurnStart5pctS,double effectiveBurnEnd5pctS,
        double effectiveBurnTime5pctS) { }
