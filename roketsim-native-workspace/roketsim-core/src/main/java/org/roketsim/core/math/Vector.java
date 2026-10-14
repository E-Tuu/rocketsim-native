package org.roketsim.core.math;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

/** Boyutu sabitlenmemiş immutable değer; array giriş ve çıkışları kopyalanır. */
public final class Vector {
    private final double[] values;

    private Vector(double[] values) { this.values = values.clone(); }

    public static Vector of(Object input) { return of(input, null, "vector"); }

    public static Vector of(Object input, Integer size) { return of(input, size, "vector"); }

    public static Vector of(Object input, Integer size, String name) {
        Object[] source;
        if (input instanceof Vector vector) {
            return of(vector.toArray(), size, name);
        } else if (input instanceof List<?> list) {
            source = list.toArray();
        } else if (input != null && input.getClass().isArray()) {
            if (input.getClass().getComponentType().isArray()) {
                throw new IllegalArgumentException(name + " must be 1-D");
            }
            source = new Object[Array.getLength(input)];
            for (int i = 0; i < source.length; i++) { source[i] = Array.get(input, i); }
        } else {
            throw new IllegalArgumentException(name + " must be 1-D");
        }
        double[] result = new double[source.length];
        for (int i = 0; i < source.length; i++) {
            Object component = source[i];
            if (component instanceof List<?> || (component != null && component.getClass().isArray())) {
                throw new IllegalArgumentException(name + " must be 1-D");
            }
            try {
                if (component instanceof Number number) { result[i] = number.doubleValue(); }
                else if (component instanceof Boolean flag) { result[i] = flag ? 1.0 : 0.0; }
                else if (component instanceof String text) { result[i] = Double.parseDouble(text); }
                else { throw new IllegalArgumentException(name + " must contain numerical values"); }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(name + " must contain numerical values", exception);
            }
        }
        if (size != null && source.length != size) {
            throw new IllegalArgumentException(name + " must have shape (" + size + ",)");
        }
        for (int i = 0; i < result.length; i++) {
            NumericalValidation.requireFinite(result[i], name + "[" + i + "]");
        }
        return new Vector(result);
    }

    public int size() { return values.length; }
    public double get(int index) { return values[index]; }
    public double[] toArray() { return values.clone(); }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Vector vector) || size() != vector.size()) { return false; }
        for (int i = 0; i < size(); i++) { if (get(i) != vector.get(i)) { return false; } }
        return true;
    }
    @Override public int hashCode() {
        int hash = 1;
        for (double value : values) { hash = 31 * hash + Double.hashCode(value == 0.0 ? 0.0 : value); }
        return hash;
    }
    @Override public String toString() { return Arrays.toString(values); }
}
