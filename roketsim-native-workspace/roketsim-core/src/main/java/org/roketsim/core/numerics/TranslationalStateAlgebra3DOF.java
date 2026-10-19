package org.roketsim.core.numerics;

import org.roketsim.core.dynamics.TranslationalState3DOF;
import org.roketsim.core.dynamics.TranslationalStateDerivative3DOF;
import org.roketsim.core.math.NumericalValidation;
import org.roketsim.core.math.Vector;

/** Stateless state algebra, not a time-advancing or physics-evaluating integrator. */
public final class TranslationalStateAlgebra3DOF {
    public TranslationalState3DOF addScaledDerivative(TranslationalState3DOF state,
            TranslationalStateDerivative3DOF derivative, double scaleS) {
        NumericalValidation.requireFinite(scaleS, "scale_s");
        double[] position = add(state.positionWorldM(), derivative.positionDerivativeWorldMS(), scaleS);
        double[] velocity = add(state.velocityWorldMS(), derivative.velocityDerivativeWorldMS2(), scaleS);
        return new TranslationalState3DOF(position, velocity);
    }

    private static double[] add(Vector value, Vector rate, double scale) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = value.get(i) + scale * rate.get(i);
        }
        return result;
    }
}
