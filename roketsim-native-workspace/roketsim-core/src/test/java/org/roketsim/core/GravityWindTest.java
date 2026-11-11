package org.roketsim.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.math.Vector;
import org.roketsim.core.environment.*;
import static org.junit.jupiter.api.Assertions.*;

class GravityWindTest {
    @Test void constantGravity() {
        ConstantGravityModel model=new ConstantGravityModel();
        assertArrayEquals(new double[]{0,0,-9.80665},model.evaluate().toArray());
        assertEquals(-USStandardAtmosphere1976Lower.STANDARD_GRAVITY_M_S2,model.evaluate().get(2));
        assertNotSame(model.evaluate(),model.evaluate());
        double[] output=model.evaluate().toArray(); output[2]=99;
        assertEquals(-9.80665,model.evaluate().get(2));
        assertEquals(0,ConstantGravityModel.class.getDeclaredFields().length);
    }
    @Test void noWind() {
        NoWindModel model=new NoWindModel();
        assertArrayEquals(new double[]{0,0,0},model.evaluate().toArray());
        assertNotSame(model.evaluate(),model.evaluate());
        assertEquals(0,NoWindModel.class.getDeclaredFields().length);
    }
    @Test void representationsAndCopies() {
        double[] source={5,-2,3};
        ConstantWindModel model=new ConstantWindModel(source);
        source[0]=Double.NaN;
        assertArrayEquals(new double[]{5,-2,3},model.evaluate().toArray());
        double[] output=model.evaluate().toArray(); output[1]=99;
        assertArrayEquals(new double[]{5,-2,3},model.evaluate().toArray());
        assertEquals(model.evaluate(),new ConstantWindModel(List.of(5,-2,3)).evaluate());
        assertEquals(model.evaluate(),new ConstantWindModel(Vector.of(new double[]{5,-2,3})).evaluate());
        assertNotSame(model.evaluate(),model.evaluate());
    }
    @Test void towardDirectionVerticalAndNoSpeedLimit() {
        for (double[] vector:new double[][]{{0,-10,0},{0,0,3},{4,-2,-3},{500,0,0},{Double.MAX_VALUE,0,0}}) {
            assertArrayEquals(vector,new ConstantWindModel(vector).evaluate().toArray());
        }
    }
    @Test void dimensions() {
        for (Object invalid:new Object[]{new double[]{1,2},new double[]{1,2,3,4},new double[][]{{1,2,3}},1.}) {
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,
                ()->new ConstantWindModel(invalid)).getClass());
        }
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void finiteValidation(double value) {
        for (int i=0;i<3;i++) {
            double[] vector={0,0,0}; vector[i]=value;
            assertTrue(assertThrows(IllegalArgumentException.class,()->new ConstantWindModel(vector))
                .getMessage().contains("airmass_velocity_world_m_s"));
        }
    }
}
