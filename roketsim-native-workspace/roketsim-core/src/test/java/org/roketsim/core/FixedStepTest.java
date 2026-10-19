package org.roketsim.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;

class FixedStepTest {
    static TranslationalState3DOF state() {
        return new TranslationalState3DOF(new double[]{1,2,3},new double[]{4,5,6});
    }
    static TranslationalStateDerivative3DOF derivative() {
        return new TranslationalStateDerivative3DOF(new double[]{4,5,6},new double[]{1,2,3});
    }
    @Test void anyPositiveFiniteStepIsUnchanged() {
        for(double h:new double[]{Double.MIN_VALUE,.001,.01,1,1e308,Double.MAX_VALUE})
            assertEquals(h,new FixedStepConfig(h).stepSizeS());
    }
    @Test void finiteNonpositiveStepIsStructured() {
        for(double h:new double[]{0,-0.,-1,-1e-300}) {
            var e=assertThrows(NumericalIntegrationError.class,()->new FixedStepConfig(h));
            assertEquals("INVALID_STEP_SIZE",e.errorCode());
            assertEquals("step_size_s",e.fieldName()); assertEquals(h,e.value());
        }
    }
    @Test void rawNonfiniteValuesRemainGeneric() {
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new FixedStepConfig(x)).getClass());
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new IntegrationPoint3DOF(x,state())).getClass());
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new TranslationalStateAlgebra3DOF().addScaledDerivative(state(),derivative(),x)).getClass());
        }
    }
    @Test void pointPreservesStateIdentityAndNegativeTime() {
        var s=state(); var p=new IntegrationPoint3DOF(-3.25,s);
        assertSame(s,p.state()); assertEquals(-3.25,p.timeS());
    }
    @Test void exactFrozenAlgebra() {
        var algebra=new TranslationalStateAlgebra3DOF();
        double[][] ps={{3,4.5,6},{1,2,3},{-1,-.5,0}};
        double[][] vs={{4.5,6,7.5},{4,5,6},{3.5,4,4.5}};
        double[] scales={.5,0,-.5};
        for(int i=0;i<3;i++) {
            var s=state(); var d=derivative(); var result=algebra.addScaledDerivative(s,d,scales[i]);
            assertArrayEquals(ps[i],result.positionWorldM().toArray());
            assertArrayEquals(vs[i],result.velocityWorldMS().toArray());
            assertNotSame(s,result); assertNotSame(s.positionWorldM(),result.positionWorldM());
            assertNotSame(s.velocityWorldMS(),result.velocityWorldMS());
            result.positionWorldM().toArray()[0]=999;
            assertEquals(ps[i][0],result.positionWorldM().get(0));
            assertEquals(1,s.positionWorldM().get(0)); assertEquals(4,d.positionDerivativeWorldMS().get(0));
            assertEquals(result,algebra.addScaledDerivative(s,d,scales[i]));
        }
    }
    @Test void algebraOverflowUsesAcceptedStateValidation() {
        var e=assertThrows(IllegalArgumentException.class,()->new TranslationalStateAlgebra3DOF().addScaledDerivative(state(),derivative(),Double.MAX_VALUE));
        assertEquals(IllegalArgumentException.class,e.getClass()); assertTrue(e.getMessage().startsWith("position_world_m[0]"));
    }
    @Test void storedSchemaAndStatelessScope() {
        assertEquals(1,FixedStepConfig.class.getRecordComponents().length);
        assertEquals(2,IntegrationPoint3DOF.class.getRecordComponents().length);
        assertEquals(0,TranslationalStateAlgebra3DOF.class.getDeclaredFields().length);
        assertEquals(0,ClassicalRK4Integrator3DOF.class.getDeclaredFields().length);
    }
}
