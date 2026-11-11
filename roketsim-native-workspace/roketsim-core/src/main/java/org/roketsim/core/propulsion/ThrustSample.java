package org.roketsim.core.propulsion;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Immutable source sample; seconds and SI scalar value. */
public record ThrustSample(double timeS, double thrustN) {
    public ThrustSample {
        requireFinite(timeS,"time_s"); requireFinite(thrustN,"thrust_N");
        if(timeS<0) { throw new MotorValidationError("NEGATIVE_THRUST_TIME","time_s",timeS); }
        if(thrustN<0) { throw new MotorValidationError("NEGATIVE_THRUST","thrust_N",thrustN); }
    }
}
