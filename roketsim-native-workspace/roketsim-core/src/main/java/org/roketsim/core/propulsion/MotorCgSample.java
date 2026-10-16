package org.roketsim.core.propulsion;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Immutable source sample; seconds and SI scalar value. */
public record MotorCgSample(double timeS, double cgFromFrontM) {
    public MotorCgSample {
        requireFinite(timeS,"time_s"); requireFinite(cgFromFrontM,"cg_from_front_m");
        if(timeS<0) { throw new MotorValidationError("NEGATIVE_CG_SAMPLE_TIME","time_s",timeS); }
        if(cgFromFrontM<0) { throw new MotorValidationError("NEGATIVE_CG_POSITION","cg_from_front_m",cgFromFrontM); }
    }
}
