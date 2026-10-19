package org.roketsim.core;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;

class ClassicalRK4Test {
    static TranslationalState3DOF state(double x,double v) {
        return new TranslationalState3DOF(new double[]{x,0,0},new double[]{v,0,0});
    }
    static TranslationalStateDerivative3DOF rates(double x,double v) {
        return new TranslationalStateDerivative3DOF(new double[]{x,0,0},new double[]{v,0,0});
    }
    static DerivativeFunction3DOF oscillator() { return (t,s)->rates(s.velocityWorldMS().get(0),-s.positionWorldM().get(0)); }
    @Test void constantDerivativeAndFourCalls() {
        var times=new ArrayList<Double>(); var initial=state(1,5);
        var result=new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(2,initial),new FixedStepConfig(.2),(t,s)->{times.add(t);return rates(2,3);});
        assertEquals(java.util.List.of(2.,2.1,2.1,2.2),times);
        assertEquals(2.2,result.timeS()); assertEquals(1.4,result.state().positionWorldM().get(0),1.4e-7);
        assertEquals(5.6,result.state().velocityWorldMS().get(0),5.6e-7);
    }
    @Test void constantAccelerationIsNotEuler() {
        var initial=new TranslationalState3DOF(new double[]{0,0,100},new double[]{0,0,20});
        var result=new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,initial),new FixedStepConfig(.5),
            (t,s)->new TranslationalStateDerivative3DOF(s.velocityWorldMS(),new double[]{0,0,-10}));
        for(int i=0;i<2;i++) {
            assertEquals(0,result.state().positionWorldM().get(i));
            assertEquals(0,result.state().velocityWorldMS().get(i));
        }
        assertEquals(108.75,result.state().positionWorldM().get(2),108.75e-7);
        assertEquals(15,result.state().velocityWorldMS().get(2),15e-7);
    }
    @Test void oscillatorSourceApproxContract() {
        var result=new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,state(1,0)),new FixedStepConfig(.1),oscillator());
        // Python pytest.approx default: max(1e-12, 1e-6*abs(expected)).
        assertEquals(.9950041666666667,result.state().positionWorldM().get(0),.9950041666666667*1e-6);
        assertEquals(-.0998333333333333,result.state().velocityWorldMS().get(0),.0998333333333333*1e-6);
    }
    @Test void stageSpyOriginalBaseAndK4NotEndpoint() {
        var times=new ArrayList<Double>(); var states=new ArrayList<TranslationalState3DOF>(); var original=state(1,.5);
        var result=new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(2,original),new FixedStepConfig(.4),(t,s)->{times.add(t);states.add(s);return oscillator().evaluate(t,s);});
        assertEquals(java.util.List.of(2.,2.2,2.2,2.4),times); assertSame(original,states.get(0));
        double[] xs={1,1.1,1.06,1.112},vs={.5,.3,.28,.076};
        for(int i=0;i<4;i++) {
            // Source np.testing.assert_allclose defaults: rtol=1e-7, atol=0.
            assertEquals(xs[i],states.get(i).positionWorldM().get(0),Math.abs(xs[i])*1e-7);
            assertEquals(vs[i],states.get(i).velocityWorldMS().get(0),Math.abs(vs[i])*1e-7);
        }
        assertNotEquals(states.get(3),result.state()); assertNotSame(original,result.state());
    }
    @Test void exceptionIdentityAtEveryStageAndNoRetry() {
        for(int stage=1;stage<=4;stage++) {
            int failure=stage; int[] calls={0}; var sentinel=new IllegalStateException("stage "+stage);
            var got=assertThrows(IllegalStateException.class,()->new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,state(0,0)),new FixedStepConfig(.1),(t,s)->{if(++calls[0]==failure)throw sentinel;return rates(0,0);}));
            assertSame(sentinel,got); assertEquals(stage,calls[0]);
        }
    }
    @Test void nullDerivativeAtEveryStageWithoutCoercion() {
        for(int stage=1;stage<=4;stage++) {
            int failure=stage; int[] calls={0};
            var e=assertThrows(NullPointerException.class,()->new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,state(0,0)),new FixedStepConfig(.1),(t,s)->++calls[0]==failure?null:rates(0,0)));
            assertTrue(e.getMessage().startsWith("k"+stage+" derivative_function return")); assertEquals(stage,calls[0]);
        }
    }
    @Test void timeOverflowPrecedesAllCallbacksAndNullCallbackUse() {
        double[] ts={1.5e308,1e308}; String[] fields={"half_time_s","full_time_s"};
        for(int i=0;i<2;i++) {
            int[] calls={0}; final double time=ts[i];
            var e=assertThrows(NumericalIntegrationError.class,()->new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(time,state(0,0)),new FixedStepConfig(1e308),(t,s)->{calls[0]++;return rates(0,0);}));
            assertEquals("NONFINITE_RK4_STAGE_TIME",e.errorCode()); assertEquals(fields[i],e.fieldName());
            assertEquals(Double.POSITIVE_INFINITY,e.value()); assertEquals(0,calls[0]);
        }
        assertThrows(NullPointerException.class,()->new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,state(0,0)),new FixedStepConfig(.1),null));
    }
    @Test void trialOverflowDoesNotCallNextStage() {
        int[] calls={0};
        var e=assertThrows(IllegalArgumentException.class,()->new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,state(1e308,0)),new FixedStepConfig(2),(t,s)->{calls[0]++;return rates(1e308,0);}));
        assertEquals(1,calls[0]); assertTrue(e.getMessage().startsWith("position_world_m[0]"));
    }
    @Test void weightedDerivativeOverflowAfterExactlyFourCalls() {
        int[] calls={0};
        var e=assertThrows(IllegalArgumentException.class,()->new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,state(0,0)),new FixedStepConfig(Double.MIN_VALUE),(t,s)->{calls[0]++;return rates(1e308,0);}));
        assertEquals(4,calls[0]); assertTrue(e.getMessage().startsWith("position_derivative_world_m_s[0]"));
    }
    @Test void finalStateOverflowAfterExactlyFourCalls() {
        int[] calls={0};
        var e=assertThrows(IllegalArgumentException.class,()->new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,state(1.7e308,0)),new FixedStepConfig(10),(t,s)->{int n=++calls[0];return rates(n==4?6e307:0,0);}));
        assertEquals(4,calls[0]); assertTrue(e.getMessage().startsWith("position_world_m[0]"));
    }
    @Test void noClippingSmallestStepAndRepeatedImmutableOutputs() {
        var integrator=new ClassicalRK4Integrator3DOF(); var original=state(1,2);
        for(double h:new double[]{Double.MIN_VALUE,.1}) {
            var point=new IntegrationPoint3DOF(.95,original); var config=new FixedStepConfig(h);
            var a=integrator.step(point,config,(t,s)->rates(0,0)); var b=integrator.step(point,config,(t,s)->rates(0,0));
            assertEquals(.95+h,a.timeS()); assertEquals(a,b); assertNotSame(a.state(),original);
            assertNotSame(a.state().positionWorldM(),original.positionWorldM());
            a.state().positionWorldM().toArray()[0]=100; assertEquals(1,original.positionWorldM().get(0));
            assertEquals(1,a.state().positionWorldM().get(0));
        }
    }
}
