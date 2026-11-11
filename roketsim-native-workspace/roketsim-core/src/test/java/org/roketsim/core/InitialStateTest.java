package org.roketsim.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import static org.junit.jupiter.api.Assertions.*;

/** NAT-013 fixtures and local validation policy; no production launch defaults. */
class InitialStateTest {
    static final double[] ZERO={0,0,0};
    static LaunchConditions3DOF launch(Object direction) { return new LaunchConditions3DOF(ZERO,ZERO,direction); }
    @Test void schemasAndStatelessBuilder() {
        assertArrayEquals(new String[]{"positionWorldM","velocityWorldMS"},Arrays.stream(TranslationalState3DOF.class.getRecordComponents()).map(x->x.getName()).toArray(String[]::new));
        assertArrayEquals(new String[]{"initialPositionWorldM","initialVelocityWorldMS","launchDirectionWorldUnit"},Arrays.stream(LaunchConditions3DOF.class.getRecordComponents()).map(x->x.getName()).toArray(String[]::new));
        assertEquals(0,InitialStateBuilder.class.getDeclaredFields().length);
        var launch=new LaunchConditions3DOF(new double[]{125,-40,12.5},new double[]{3,-2,7},new double[]{.6,.8,0});
        var first=new InitialStateBuilder().build(launch); var second=new InitialStateBuilder().build(launch);
        assertEquals(first,second); assertEquals(launch.initialPositionWorldM(),first.positionWorldM());
        assertEquals(launch.initialVelocityWorldMS(),first.velocityWorldMS());
        assertNotSame(launch.initialPositionWorldM(),first.positionWorldM()); assertNotSame(launch.initialVelocityWorldMS(),first.velocityWorldMS());
    }
    @Test void copiesAndExplicitZero() {
        double[] p={1,2,3},v={4,5,6},d={0,0,1};
        var launch=new LaunchConditions3DOF(p,v,d); var state=new TranslationalState3DOF(p,v);
        Arrays.fill(p,99); Arrays.fill(v,99); Arrays.fill(d,99);
        assertArrayEquals(new double[]{1,2,3},launch.initialPositionWorldM().toArray());
        assertArrayEquals(new double[]{4,5,6},state.velocityWorldMS().toArray());
        double[] leaked=launch.launchDirectionWorldUnit().toArray(); leaked[2]=99;
        assertArrayEquals(new double[]{0,0,1},launch.launchDirectionWorldUnit().toArray());
        assertEquals(Vector.of(ZERO),new InitialStateBuilder().build(launch(new double[]{0,0,1})).positionWorldM());
        var input=Vector.of(new double[]{1,2,3}); var copied=new TranslationalState3DOF(input,input);
        assertNotSame(input,copied.positionWorldM()); assertNotSame(copied.positionWorldM(),copied.velocityWorldMS());
    }
    @ParameterizedTest @ValueSource(doubles={0,0.5e-8,1e-8}) void nearUnitPreserved(double offset) {
        double z=1+offset;
        assertEquals(z,launch(new double[]{0,0,z}).launchDirectionWorldUnit().get(2));
        assertEquals(1e-8,LaunchConditions3DOF.LAUNCH_DIRECTION_UNIT_NORM_ATOL);
    }
    @ParameterizedTest @ValueSource(doubles={1.00000002,5}) void nonunit(double z) {
        var error=assertThrows(InitialStateValidationError.class,()->launch(new double[]{0,0,z}));
        assertEquals("NON_UNIT_LAUNCH_DIRECTION",error.errorCode()); assertEquals("launch_direction_world_unit",error.fieldName());
    }
    @Test void zeroAndImmediatelyOutsideBoundary() {
        assertEquals("INVALID_LAUNCH_DIRECTION",assertThrows(InitialStateValidationError.class,()->launch(ZERO)).errorCode());
        assertEquals("NON_UNIT_LAUNCH_DIRECTION",assertThrows(InitialStateValidationError.class,()->launch(new double[]{0,0,Math.nextUp(1+1e-8)})).errorCode());
        assertArrayEquals(new double[]{.6,0,.8},launch(new double[]{.6,0,.8}).launchDirectionWorldUnit().toArray());
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) void nonfinite(double value) {
        for(int field=0;field<3;field++) {
            Object[] vectors={ZERO,ZERO,new double[]{0,0,1}}; vectors[field]=new double[]{value,0,0};
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new LaunchConditions3DOF(vectors[0],vectors[1],vectors[2])).getClass());
        }
    }
    @Test void invalidShapesAndTypedBoundary() {
        for(Object invalid:new Object[]{null,1,new double[0],new double[2],new double[4],new double[][]{{1,2,3}},new String[]{"0","0","1"}}) {
            assertThrows(IllegalArgumentException.class,()->new TranslationalState3DOF(invalid,ZERO));
            assertThrows(IllegalArgumentException.class,()->launch(invalid));
        }
    }
}
