package org.roketsim.core;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import static org.junit.jupiter.api.Assertions.*;

/** NAT-014 accepted atol=2e-15, rtol=0 fixtures. Numbers here are not engine defaults. */
class TranslationalDynamicsTest {
    static final double[] ZERO={0,0,0};
    static TranslationalDynamicsInputs inputs() { return new TranslationalDynamicsInputs(2,30,ZERO,0,.01,.5,new double[]{0,0,-10}); }
    static TranslationalDynamicsResult evaluate(TranslationalDynamicsInputs in,Object direction,Object velocity) {
        return new TranslationalDynamicsEvaluator().evaluate(new TranslationalState3DOF(new double[]{1,2,3},velocity),
            new LaunchConditions3DOF(ZERO,ZERO,direction),in,TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE);
    }
    static void vector(Vector actual,double... expected) { assertArrayEquals(expected,actual.toArray(),2e-15); }
    @Test void frozenVerticalFixtures() {
        var ignition=evaluate(inputs(),new double[]{0,0,1},ZERO);
        vector(ignition.forces().thrustForceWorldN(),0,0,30); vector(ignition.forces().gravityForceWorldN(),0,0,-20);
        vector(ignition.forces().dragForceWorldN(),0,0,0); vector(ignition.forces().netForceWorldN(),0,0,10); vector(ignition.accelerationWorldMS2(),0,0,5);
        var ascent=evaluate(new TranslationalDynamicsInputs(2,30,new double[]{0,0,20},100,.01,.5,new double[]{0,0,-10}),new double[]{0,0,1},new double[]{0,0,20});
        vector(ascent.forces().dragForceWorldN(),0,0,-.5); vector(ascent.forces().netForceWorldN(),0,0,9.5); vector(ascent.accelerationWorldMS2(),0,0,4.75);
        var descent=evaluate(new TranslationalDynamicsInputs(2,0,new double[]{0,0,-20},100,.01,.5,new double[]{0,0,-10}),new double[]{0,0,1},new double[]{0,0,-20});
        vector(descent.forces().dragForceWorldN(),0,0,.5); vector(descent.forces().netForceWorldN(),0,0,-19.5); vector(descent.accelerationWorldMS2(),0,0,-9.75);
    }
    @Test void trueThreeDimensionalFixtureAndAuthorities() {
        var in=new TranslationalDynamicsInputs(1.5,10,new double[]{3,0,4},50,.2,.4,new double[]{0,0,-9.8});
        var result=evaluate(in,new double[]{.6,0,.8},new double[]{3,0,4});
        vector(result.forces().thrustForceWorldN(),6,0,8); vector(result.forces().dragForceWorldN(),-2.4,0,-3.2);
        vector(result.forces().gravityForceWorldN(),0,0,-14.7); vector(result.forces().netForceWorldN(),3.6,0,-9.9);
        vector(result.derivative().positionDerivativeWorldMS(),3,0,4); vector(result.accelerationWorldMS2(),2.4,0,-6.6);
        assertSame(result.derivative().velocityDerivativeWorldMS2(),result.accelerationWorldMS2());
        var independent=evaluate(in,new double[]{.6,0,.8},new double[]{-12,4,19});
        assertEquals(result.forces(),independent.forces()); vector(independent.derivative().positionDerivativeWorldMS(),-12,4,19);
        assertEquals(result,evaluate(in,new double[]{.6,0,.8},new double[]{3,0,4}));
        assertEquals(0,TranslationalDynamicsEvaluator.class.getDeclaredFields().length);
    }
    @Test void exactZeroFlowBranches() {
        vector(evaluate(inputs(),new double[]{0,0,1},ZERO).forces().dragForceWorldN(),0,0,0);
        var nonzero=new TranslationalDynamicsInputs(2,30,new double[]{1,0,0},0,.01,.5,new double[]{0,0,-10});
        vector(evaluate(nonzero,new double[]{0,0,1},ZERO).forces().dragForceWorldN(),0,0,0);
        var inconsistent=new TranslationalDynamicsInputs(2,30,ZERO,1,.01,.5,new double[]{0,0,-10});
        var e=assertThrows(DynamicsEvaluationError.class,()->evaluate(inconsistent,new double[]{0,0,1},ZERO));
        assertEquals("INCONSISTENT_DRAG_INPUTS",e.errorCode()); assertEquals("dynamic_pressure_Pa",e.fieldName()); assertEquals(1.,e.value());
    }
    static TranslationalDynamicsInputs scalars(double[] values) { return new TranslationalDynamicsInputs(values[0],values[1],ZERO,values[2],values[3],values[4],ZERO); }
    @Test void finiteScalarDomain() {
        String[] fields={"mass_kg","thrust_N","dynamic_pressure_Pa","reference_area_m2","drag_coefficient_cd0"};
        String[] codes={"INVALID_MASS","INVALID_THRUST","INVALID_DYNAMIC_PRESSURE","INVALID_REFERENCE_AREA","INVALID_DRAG_COEFFICIENT"};
        for(int i=0;i<5;i++) {
            double[] values={2,30,0,.01,.5}; values[i]=-1;
            var e=assertThrows(DynamicsEvaluationError.class,()->scalars(values));
            assertEquals(codes[i],e.errorCode()); assertEquals(fields[i],e.fieldName()); assertEquals(-1.,e.value());
        }
        assertThrows(DynamicsEvaluationError.class,()->scalars(new double[]{0,0,0,.01,0}));
        assertThrows(DynamicsEvaluationError.class,()->scalars(new double[]{1,0,0,0,0}));
        scalars(new double[]{1,0,0,.01,0});
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) void rawNonfinite(double value) {
        for(int i=0;i<5;i++) { double[] v={2,30,0,.01,.5}; v[i]=value;
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->scalars(v)).getClass()); }
        assertThrows(IllegalArgumentException.class,()->new TranslationalDynamicsInputs(1,0,new double[]{value,0,0},0,1,0,ZERO));
        assertThrows(IllegalArgumentException.class,()->new TranslationalDynamicsInputs(1,0,ZERO,0,1,0,new double[]{value,0,0}));
    }
    @Test void selectorIdentityNotTextEquivalence() {
        for(Object[] pair:new Object[][]{{"fixed_launch_direction",AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY},{ThrustDirectionModel.FIXED_LAUNCH_DIRECTION,"baseline_cd0_drag_only"},{null,null}}) {
            var profile=new TranslationalDynamicsModelProfile(pair[0],pair[1]);
            var e=assertThrows(DynamicsEvaluationError.class,()->new TranslationalDynamicsEvaluator().evaluate(new TranslationalState3DOF(ZERO,ZERO),new LaunchConditions3DOF(ZERO,ZERO,new double[]{0,0,1}),inputs(),profile));
            boolean thrust=pair[0]!=ThrustDirectionModel.FIXED_LAUNCH_DIRECTION;
            assertEquals(thrust?"UNSUPPORTED_THRUST_DIRECTION_MODEL":"UNSUPPORTED_AERODYNAMIC_FORCE_MODEL",e.errorCode());
            assertEquals(thrust?"thrust_direction_model":"aerodynamic_force_model",e.fieldName());
        }
    }
    @Test void accelerationOverflowAndNetValidation() {
        var in=new TranslationalDynamicsInputs(Double.MIN_VALUE,1,ZERO,0,1,0,ZERO);
        var e=assertThrows(DynamicsEvaluationError.class,()->evaluate(in,new double[]{0,0,1},ZERO));
        assertEquals("INVALID_ACCELERATION",e.errorCode()); assertEquals("velocity_derivative_world_m_s2",e.fieldName());
        var forces=new TranslationalForces3DOF(new double[]{Double.MAX_VALUE,0,0},new double[]{Double.MAX_VALUE,0,0},ZERO);
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,forces::netForceWorldN).getClass());
    }
    @Test void immutableIndependentVectorsAndStoredSchemas() {
        double[] a={1,2,3},b={4,5,6},c={7,8,9};
        var forces=new TranslationalForces3DOF(a,b,c); var derivative=new TranslationalStateDerivative3DOF(a,b);
        Arrays.fill(a,99); Arrays.fill(b,99); Arrays.fill(c,99);
        vector(forces.netForceWorldN(),12,15,18); vector(derivative.positionDerivativeWorldMS(),1,2,3);
        double[] leaked=forces.netForceWorldN().toArray(); leaked[0]=99; vector(forces.netForceWorldN(),12,15,18);
        assertEquals(3,TranslationalForces3DOF.class.getRecordComponents().length);
        assertEquals(2,TranslationalStateDerivative3DOF.class.getRecordComponents().length);
        assertEquals(3,TranslationalDynamicsResult.class.getRecordComponents().length);
        assertEquals(7,TranslationalDynamicsInputs.class.getRecordComponents().length);
        var in=new TranslationalDynamicsInputs(1,0,a,0,1,0,b); a[0]=1; b[0]=1;
        assertEquals(99,in.relativeVelocityWorldMS().get(0)); assertEquals(99,in.gravityAccelerationWorldMS2().get(0));
    }
}
