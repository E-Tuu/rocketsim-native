package org.roketsim.core.numerics;

import org.roketsim.core.math.NumericalValidation;

/** Explicit finite positive step in seconds; no defaults, bounds or clipping policy. */
public record FixedStepConfig(double stepSizeS) {
    public FixedStepConfig {
        NumericalValidation.requireFinite(stepSizeS, "step_size_s");
        if (stepSizeS <= 0.0) {
            throw new NumericalIntegrationError("INVALID_STEP_SIZE", "step_size_s", stepSizeS);
        }
    }
}
