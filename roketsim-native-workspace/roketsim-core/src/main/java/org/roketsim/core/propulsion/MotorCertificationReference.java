package org.roketsim.core.propulsion;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Measured certification metadata, never runtime thrust authority. */
public record MotorCertificationReference(double measuredTotalImpulseNS, double measuredAverageThrustN,
        double measuredPeakThrustN, double measuredBurnTimeS) {
    public MotorCertificationReference {
        double[] values={measuredTotalImpulseNS,measuredAverageThrustN,measuredPeakThrustN,measuredBurnTimeS};
        String[] names={"measured_total_impulse_N_s","measured_average_thrust_N","measured_peak_thrust_N","measured_burn_time_s"};
        for(int i=0;i<4;i++) { requireFinite(values[i],names[i]); }
        for(int i=0;i<4;i++) { if(values[i]<=0) { throw new MotorValidationError("INVALID_CERTIFICATION_REFERENCE",names[i],values[i]); } }
    }
}
