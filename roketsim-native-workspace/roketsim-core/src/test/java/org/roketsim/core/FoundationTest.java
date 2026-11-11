package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.roketsim.core.math.*;
import org.roketsim.core.units.UnitPolicy;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class FoundationTest {
    @ParameterizedTest @ValueSource(doubles = {0, 12.5, -12.5, Double.MIN_NORMAL, Double.MAX_VALUE})
    void finiteValues(double value) {
        assertTrue(NumericalValidation.isFinite(value));
        assertEquals(value, NumericalValidation.requireFinite(value, "value"));
        assertEquals(value, NumericalValidation.requireFinite(value));
    }
    static Stream<Double> nonFinite() { return Stream.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY); }
    @ParameterizedTest @MethodSource("nonFinite")
    void nonFiniteValues(double value) {
        assertFalse(NumericalValidation.isFinite(value));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> NumericalValidation.requireFinite(value, "dynamic_pressure")).getMessage().contains("dynamic_pressure"));
        assertThrows(IllegalArgumentException.class, () -> NumericalValidation.isNearZero(value, 1));
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> Vector.of(new double[]{1, value, 3}, null, "sample")).getMessage().contains("sample[1]"));
    }
    static Stream<Double> invalidTolerance() { return Stream.of(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY); }
    @ParameterizedTest @MethodSource("invalidTolerance")
    void invalidTolerance(double atol) {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> NumericalValidation.isNearZero(0, atol)).getMessage().contains("atol"));
        assertThrows(IllegalArgumentException.class, () -> Vectors.normalize(Vector.of(new double[]{1,0,0}), atol));
    }
    static Stream<Arguments> nearZeroCases() {
        return Stream.of(Arguments.of(0.,1.,true), Arguments.of(.5,1.,true), Arguments.of(1.,1.,true),
            Arguments.of(1.01,1.,false), Arguments.of(-.5,1.,true), Arguments.of(0.,0.,true),
            Arguments.of(Double.MIN_NORMAL,0.,false));
    }
    @ParameterizedTest @MethodSource("nearZeroCases")
    void inclusiveTolerance(double value, double atol, boolean expected) {
        assertEquals(expected, NumericalValidation.isNearZero(value, atol));
    }
    @Test void creationAndShape() {
        assertEquals(Vector.of(new double[]{1,2,3}), Vector.of(List.of(1,2,3)));
        assertEquals(Vector.of(new double[]{1,2,3}), Vector.of(new int[]{1,2,3}));
        assertEquals(2, Vector.of(new double[]{1,2}, 2, "vector").size());
        assertEquals(2, Vector.of(new double[]{1,2}, 2).size());
        assertThrows(IllegalArgumentException.class, () -> Vector.of(new double[]{1,2}, 3, "vector"));
        assertThrows(IllegalArgumentException.class, () -> Vector.of(new double[][]{{1,2,3}}));
        assertThrows(IllegalArgumentException.class, () -> Vector.of(new double[0][0]));
        assertThrows(IllegalArgumentException.class, () -> Vector.of(3));
        assertThrows(IllegalArgumentException.class, () -> Vector.of(List.of("bad")));
        assertEquals(Vector.of(new double[]{1,0}), Vector.of(List.of(true,false)));
    }
    @Test void immutableValue() {
        double[] input = {3,4,0};
        Vector vector = Vector.of(input);
        input[0] = 99;
        double[] output = vector.toArray(); output[0] = 88;
        assertArrayEquals(new double[]{3,4,0}, vector.toArray());
        assertEquals(Vector.of(new double[]{3,4,0}), vector);
        assertEquals(Vector.of(new double[]{0}), Vector.of(new double[]{-0.0}));
        assertEquals(Vector.of(new double[]{0}).hashCode(), Vector.of(new double[]{-0.0}).hashCode());
    }
    @Test void normsAndNormalization() {
        Vector input = Vector.of(new double[]{3,4,0});
        assertEquals(5., Vectors.magnitude(input));
        assertArrayEquals(new double[]{.6,.8,0}, Vectors.normalize(input,0).toArray());
        assertArrayEquals(new double[]{3,4,0}, input.toArray());
        assertEquals(0., Vectors.magnitude(Vector.of(new double[]{0,0,0})));
        assertThrows(IllegalArgumentException.class, () -> Vectors.normalize(Vector.of(new double[]{0,0,0}),0));
        assertThrows(IllegalArgumentException.class, () -> Vectors.normalize(Vector.of(new double[]{1e-9,0,0}),1e-8));
        assertThrows(IllegalArgumentException.class, () -> Vectors.normalize(Vector.of(new double[]{1e-8,0,0}),1e-8));
        assertEquals(1., Vectors.normalize(Vector.of(new double[]{2e-8,0,0}),1e-8).get(0));
        assertEquals(0., Vectors.magnitude(Vector.of(new double[]{})));
        assertEquals(0., Vectors.dot(Vector.of(new double[]{}),Vector.of(new double[]{})));
        assertThrows(IllegalArgumentException.class, () -> Vectors.normalize(Vector.of(new double[]{}),0));
        assertThrows(IllegalArgumentException.class, () -> Vectors.magnitude(Vector.of(new double[]{Double.MAX_VALUE})));
        assertEquals(0., Vectors.magnitude(Vector.of(new double[]{Double.MIN_VALUE})));
    }
    @Test void dotAndCross() {
        Vector a=Vector.of(new double[]{1,2,3}), b=Vector.of(new double[]{4,-5,6});
        assertEquals(12., Vectors.dot(a,b));
        assertArrayEquals(new double[]{27,6,-13}, Vectors.cross3(a,b).toArray());
        Vector x=Vector.of(new double[]{1,0,0}), y=Vector.of(new double[]{0,1,0});
        assertEquals(0., Vectors.dot(x,y));
        assertArrayEquals(new double[]{0,0,1},Vectors.cross3(x,y).toArray());
        assertArrayEquals(new double[]{0,0,-1},Vectors.cross3(y,x).toArray());
        assertArrayEquals(new double[]{0,0,0},Vectors.cross3(a,Vector.of(new double[]{2,4,6})).toArray());
        assertThrows(IllegalArgumentException.class, () -> Vectors.dot(a,Vector.of(new double[]{1,2})));
        assertThrows(IllegalArgumentException.class, () -> Vectors.cross3(a,Vector.of(new double[]{1,2})));
        assertThrows(IllegalArgumentException.class, () -> Vectors.cross3(a,Vector.of(new double[]{1,2,3,4})));
        assertThrows(IllegalArgumentException.class, () -> Vectors.dot(Vector.of(new double[]{Double.MAX_VALUE}),Vector.of(new double[]{2})));
        assertArrayEquals(new double[]{1,2,3}, a.toArray());
        assertArrayEquals(new double[]{4,-5,6}, b.toArray());
    }
    @Test void frameMetadata() {
        assertArrayEquals(new ReferenceFrame[]{ReferenceFrame.WORLD_ENU,ReferenceFrame.BODY},ReferenceFrame.values());
        assertEquals(List.of(new AxisConvention("x_W","East"),new AxisConvention("y_W","North"),
            new AxisConvention("z_W","Up")),FrameConventions.FRAME_AXES.get(ReferenceFrame.WORLD_ENU));
        assertEquals(List.of(new AxisConvention("x_B","lateral"),new AxisConvention("y_B","lateral"),
            new AxisConvention("z_B","longitudinal / thrust / roll axis")),FrameConventions.FRAME_AXES.get(ReferenceFrame.BODY));
        assertEquals(Map.of("origin","nose tip","coordinate","x_geo","positive_direction","nose -> tail"),
            FrameConventions.GEOMETRY_AXIAL_CONVENTION);
        assertNotEquals("z_B",FrameConventions.GEOMETRY_AXIAL_CONVENTION.get("coordinate"));
        assertThrows(UnsupportedOperationException.class, () -> FrameConventions.FRAME_AXES.clear());
        assertThrows(UnsupportedOperationException.class, () -> FrameConventions.FRAME_AXES.get(ReferenceFrame.BODY).clear());
        assertThrows(UnsupportedOperationException.class, () -> FrameConventions.GEOMETRY_AXIAL_CONVENTION.clear());
    }
    @Test void allCanonicalUnits() {
        assertEquals(Map.ofEntries(Map.entry("length","m"),Map.entry("mass","kg"),Map.entry("time","s"),
            Map.entry("velocity","m/s"),Map.entry("acceleration","m/s^2"),Map.entry("force","N"),
            Map.entry("moment","N*m"),Map.entry("pressure","Pa"),Map.entry("density","kg/m^3"),
            Map.entry("temperature","K"),Map.entry("angle","rad"),Map.entry("angular_rate","rad/s"),
            Map.entry("angular_accel","rad/s^2"),Map.entry("area","m^2"),Map.entry("volume","m^3"),
            Map.entry("inertia","kg*m^2"),Map.entry("dynamic_viscosity","Pa*s"),Map.entry("kinematic_viscosity","m^2/s")),
            UnitPolicy.CANONICAL_UNITS);
        assertThrows(UnsupportedOperationException.class, () -> UnitPolicy.CANONICAL_UNITS.clear());
    }
}
