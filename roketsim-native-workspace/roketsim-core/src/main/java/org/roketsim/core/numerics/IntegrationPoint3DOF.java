package org.roketsim.core.numerics;

import org.roketsim.core.dynamics.TranslationalState3DOF;
import org.roketsim.core.math.NumericalValidation;

/** Numerical (time, state) only; finite negative time is valid and state ownership is retained. */
public record IntegrationPoint3DOF(double timeS, TranslationalState3DOF state) {
    public IntegrationPoint3DOF {
        NumericalValidation.requireFinite(timeS, "time_s");
    }
}
