package org.roketsim.core.math;

/** Python numerical.py doğrulamasını açık toleranslarla korur. */
public final class NumericalValidation {
    private NumericalValidation() { }

    public static boolean isFinite(double value) { return Double.isFinite(value); }

    public static double requireFinite(double value) { return requireFinite(value, "value"); }

    public static double requireFinite(double value, String name) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite; got " + value);
        }
        return value;
    }

    public static boolean isNearZero(double value, double atol) {
        requireFinite(atol, "atol");
        if (atol < 0.0) { throw new IllegalArgumentException("atol must be non-negative"); }
        requireFinite(value, "value");
        return Math.abs(value) <= atol;
    }
}
