package org.roketsim.core.numerics;

import org.roketsim.core.dynamics.TranslationalState3DOF;
import org.roketsim.core.dynamics.TranslationalStateDerivative3DOF;

/** Caller-supplied instantaneous derivative; time in seconds, immutable WORLD SI state. */
@FunctionalInterface
public interface DerivativeFunction3DOF {
    TranslationalStateDerivative3DOF evaluate(double timeS, TranslationalState3DOF state);
}
