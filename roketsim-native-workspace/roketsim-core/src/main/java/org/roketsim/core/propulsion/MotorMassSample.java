package org.roketsim.core.propulsion;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Immutable source sample; seconds and SI scalar value. */
public record MotorMassSample(double timeS, double massKg) {
    public MotorMassSample {
        requireFinite(timeS,"time_s"); requireFinite(massKg,"mass_kg");
        if(timeS<0) { throw new MotorValidationError("NEGATIVE_MASS_SAMPLE_TIME","time_s",timeS); }
        if(massKg<=0) { throw new MotorValidationError("NON_POSITIVE_MASS_SAMPLE","mass_kg",massKg); }
    }
}
