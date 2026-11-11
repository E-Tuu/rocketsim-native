package org.roketsim.core.math;

/** Python vectors.py matematiği; üç boyut zorunluluğu yalnız cross3 içindir. */
public final class Vectors {
    private Vectors() { }

    public static double magnitude(Vector vector) {
        double sum = 0.0;
        for (int i = 0; i < vector.size(); i++) { sum += vector.get(i) * vector.get(i); }
        return NumericalValidation.requireFinite(Math.sqrt(sum), "magnitude");
    }

    public static double dot(Vector left, Vector right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("vectors must have the same shape");
        }
        double sum = 0.0;
        for (int i = 0; i < left.size(); i++) { sum += left.get(i) * right.get(i); }
        return NumericalValidation.requireFinite(sum, "dot product");
    }

    public static Vector normalize(Vector vector, double atol) {
        double norm = magnitude(vector);
        if (NumericalValidation.isNearZero(norm, atol)) {
            throw new IllegalArgumentException("vector magnitude must be greater than atol");
        }
        double[] result = vector.toArray();
        for (int i = 0; i < result.length; i++) { result[i] /= norm; }
        return Vector.of(result);
    }

    public static Vector cross3(Vector left, Vector right) {
        if (left.size() != 3 || right.size() != 3) {
            throw new IllegalArgumentException("cross3 vectors must have shape (3,)");
        }
        return Vector.of(new double[] {
            left.get(1) * right.get(2) - left.get(2) * right.get(1),
            left.get(2) * right.get(0) - left.get(0) * right.get(2),
            left.get(0) * right.get(1) - left.get(1) * right.get(0)
        }, 3, "cross product");
    }
}
