package org.roketsim.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Quaternion;
import org.roketsim.core.math.Quaternions;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.*;
import static org.junit.jupiter.api.Assertions.*;

/** M-001 approved numeric boundary; lexical Python compatibility is not a Core contract. */
class NumericInputContractTest {
    @Test void numericContainersAndWrappers() {
        assertArrayEquals(new double[]{1,2,3}, Vector.of(new double[]{1,2,3}).toArray());
        assertArrayEquals(new double[]{1,2,3}, Vector.of(new int[]{1,2,3}).toArray());
        assertArrayEquals(new double[]{1,2,3}, Vector.of(List.of(1,2L,3.0)).toArray());
        assertArrayEquals(new double[]{1,2,3,4,5,6,7.5,8}, Vector.of(List.of(
            (byte)1, (short)2, 3, 4L, 5f, 6d, new BigDecimal("7.5"), BigInteger.valueOf(8))).toArray());
    }

    @Test void independentArrayAndListCopies() {
        double[] source={-0.0,Double.MIN_VALUE,Double.MAX_VALUE};
        Vector stored=Vector.of(source);
        source[1]=99;
        stored.toArray()[2]=99;
        assertEquals(Double.MIN_VALUE,stored.get(1));
        assertEquals(Double.MAX_VALUE,stored.get(2));
        assertEquals(Double.doubleToRawLongBits(-0.0),Double.doubleToRawLongBits(stored.get(0)));
        ArrayList<Number> list=new ArrayList<>(List.of(1,2,3));
        Vector copy=Vector.of(list);
        list.set(0,99);
        assertEquals(1,copy.get(0));
    }

    @Test void existingVectorIsCopied() {
        Vector source=Vector.of(new double[]{1,2,3});
        Vector copy=Vector.of(source);
        assertEquals(source,copy);
        assertNotSame(source,copy);
        copy.toArray()[0]=99;
        assertEquals(1,source.get(0));
        assertEquals(1,copy.get(0));
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4})
    void dimensionalityIsNotRestrictedTo3D(int size) {
        assertEquals(size,Vector.of(new double[size],size).size());
    }

    @Test void wrongShapeIsRejected() {
        assertThrows(IllegalArgumentException.class,()->Vector.of(new double[]{1,2},3));
        assertThrows(IllegalArgumentException.class,()->Vector.of(new double[]{1,2,3,4},3));
        assertThrows(IllegalArgumentException.class,()->Vector.of(new double[][]{{1,2,3}}));
        assertThrows(IllegalArgumentException.class,()->Vector.of(1));
    }

    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void rawNonfiniteIsRejected(double value) {
        assertTrue(assertThrows(IllegalArgumentException.class,
            ()->Vector.of(new double[]{1,value,3},3,"input")).getMessage().contains("input[1] must be finite"));
        assertThrows(IllegalArgumentException.class,()->Vector.of(List.of(1,value,3)));
    }

    @ParameterizedTest @ValueSource(strings={"1","1_0","1f","0x1.0p0","\u0661","inf",""," 1 "})
    void stringsAreExplicitlyRejected(String text) {
        var error=assertThrows(IllegalArgumentException.class,()->Vector.of(new String[]{text},1,"input"));
        assertTrue(error.getMessage().contains("input"));
        assertTrue(error.getMessage().contains("String components are not supported"));
        assertThrows(IllegalArgumentException.class,()->Vector.of(List.of(text)));
    }

    @Test void mixedNumericAndStringListIsRejected() {
        assertThrows(IllegalArgumentException.class,()->Vector.of(List.of(1,"2",3)));
        assertThrows(IllegalArgumentException.class,()->Vector.of(new Object[]{1,2,"3"}));
        assertThrows(IllegalArgumentException.class,()->Vector.of(new String[]{"1","2","3"}));
    }

    @Test void booleanCompatibilityIsUnchanged() {
        assertArrayEquals(new double[]{1,0},Vector.of(new boolean[]{true,false}).toArray());
        assertArrayEquals(new double[]{1,0,2},Vector.of(List.of(true,false,2)).toArray());
    }

    @Test void downstreamNumericInputsWorkAndStringInputsAreRejected() {
        Quaternion q=Quaternion.of(new double[]{1,0,0,0});
        assertArrayEquals(new double[]{1,2,3},Quaternions.rotateBodyToWorld(q,Vector.of(new double[]{1,2,3}),0).toArray());
        assertArrayEquals(new double[]{1,2,3},new ConstantWindModel(new double[]{1,2,3}).evaluate().toArray());
        assertArrayEquals(new double[]{1,2,3},new RelativeFlowCalculator().evaluate(new double[]{2,3,4},new double[]{1,1,1}).toArray());
        var atmosphere=new DryAirAtmosphereState(288,100000,2);
        var air=new DryAirProperties(260,1,.00002,1.4);
        assertEquals(13,new BasicFlightConditionsCalculator().evaluate(new double[]{3,4,12},atmosphere,air,.2).airspeedMS());
        assertThrows(IllegalArgumentException.class,()->Quaternion.of(new String[]{"1","0","0","0"}));
        String[] text={"1","2","3"};
        assertThrows(IllegalArgumentException.class,()->new ConstantWindModel(text));
        assertThrows(IllegalArgumentException.class,()->new RelativeFlowCalculator().evaluate(text,new double[3]));
        assertThrows(IllegalArgumentException.class,()->new RelativeFlowCalculator().evaluate(new double[3],text));
        assertThrows(IllegalArgumentException.class,()->new BasicFlightConditionsCalculator().evaluate(text,atmosphere,air,.2));
    }
}
