package org.roketsim.core.numerics;

import java.util.Objects;
import org.roketsim.core.dynamics.TranslationalStateDerivative3DOF;
import org.roketsim.core.math.Vector;

/** One classical explicit fixed step; four callbacks, no endpoint physics evaluation. */
public final class ClassicalRK4Integrator3DOF {
    public IntegrationPoint3DOF step(IntegrationPoint3DOF point, FixedStepConfig config,
            DerivativeFunction3DOF derivativeFunction) {
        double time = point.timeS();
        double h = config.stepSizeS();
        double halfH = h / 2.0;
        double halfTime = time + halfH;
        double fullTime = time + h;
        requireStageTime(halfTime, "half_time_s");
        requireStageTime(fullTime, "full_time_s");

        var algebra = new TranslationalStateAlgebra3DOF();
        var original = point.state();
        var k1 = requireDerivative(derivativeFunction.evaluate(time, original), "k1");
        var state2 = algebra.addScaledDerivative(original, k1, halfH);
        var k2 = requireDerivative(derivativeFunction.evaluate(halfTime, state2), "k2");
        var state3 = algebra.addScaledDerivative(original, k2, halfH);
        var k3 = requireDerivative(derivativeFunction.evaluate(halfTime, state3), "k3");
        var state4 = algebra.addScaledDerivative(original, k3, h);
        var k4 = requireDerivative(derivativeFunction.evaluate(fullTime, state4), "k4");
        var weighted = new TranslationalStateDerivative3DOF(
            weighted(k1.positionDerivativeWorldMS(), k2.positionDerivativeWorldMS(),
                k3.positionDerivativeWorldMS(), k4.positionDerivativeWorldMS()),
            weighted(k1.velocityDerivativeWorldMS2(), k2.velocityDerivativeWorldMS2(),
                k3.velocityDerivativeWorldMS2(), k4.velocityDerivativeWorldMS2()));
        var nextState = algebra.addScaledDerivative(original, weighted, h);
        return new IntegrationPoint3DOF(fullTime, nextState);
    }

    private static void requireStageTime(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new NumericalIntegrationError("NONFINITE_RK4_STAGE_TIME", field, value);
        }
    }

    private static TranslationalStateDerivative3DOF requireDerivative(
            TranslationalStateDerivative3DOF value, String stage) {
        return Objects.requireNonNull(value,
            stage + " derivative_function return must be TranslationalStateDerivative3DOF");
    }

    private static double[] weighted(Vector k1, Vector k2, Vector k3, Vector k4) {
        double[] result = new double[3];
        for (int i = 0; i < 3; i++) {
            result[i] = (((k1.get(i) + 2.0 * k2.get(i)) + 2.0 * k3.get(i)) + k4.get(i)) / 6.0;
        }
        return result;
    }
}
