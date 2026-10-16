package org.roketsim.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.propulsion.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.propulsion.MotorCatalogs.*;

class MotorThrustTest {
    static MotorDefinition curve(double[][] points) {
        return MotorCatalogTest.change("thrust_curve",Arrays.stream(points).map(p->new ThrustSample(p[0],p[1])).toList());
    }
    @Test void analyticalTriangleAndNoSnapping() {
        var motor=curve(new double[][]{{0,0},{1,20},{3,0}}); var e=new MotorThrustCurveEvaluator();
        assertEquals(new MotorThrustState(0,0),e.evaluate(motor,0));
        assertEquals(new MotorThrustState(10,2.5),e.evaluate(motor,.5));
        assertEquals(new MotorThrustState(20,10),e.evaluate(motor,1));
        assertEquals(new MotorThrustState(10,25),e.evaluate(motor,2));
        assertEquals(new MotorThrustState(0,30),e.evaluate(motor,100));
        assertEquals(20*Math.nextDown(1.),e.evaluate(motor,Math.nextDown(1.)).thrustN());
        assertTrue(e.evaluate(motor,Math.nextDown(1.)).thrustN()<20);
        var s=new MotorCurveAnalyzer().analyze(motor);
        assertEquals(new MotorCurveStatistics(30,20,1,3,.05,2.9,2.9-.05),s);
    }
    @Test void peakAndPlateau() {
        var m=curve(new double[][]{{0,0},{1,20},{2,0},{3,20},{4,0}});
        var s=new MotorCurveAnalyzer().analyze(m);
        assertEquals(1,s.peakThrustTimeS()); assertEquals(.05,s.effectiveBurnStart5pctS()); assertEquals(3.95,s.effectiveBurnEnd5pctS());
        m=curve(new double[][]{{0,0},{1,1},{2,1},{3,20},{4,0},{5,1},{6,1},{7,0}});
        s=new MotorCurveAnalyzer().analyze(m); assertEquals(1,s.effectiveBurnStart5pctS()); assertEquals(6,s.effectiveBurnEnd5pctS());
    }
    @Test void f50AnchorsAndAllNodes() {
        var e=new MotorThrustCurveEvaluator(); var m=AEROTECH_F50_4T; var s=new MotorCurveAnalyzer().analyze(m);
        for(var node:m.thrustCurve()) { assertEquals(node.thrustN(),e.evaluate(m,node.timeS()).thrustN()); }
        assertEquals(76.828387,s.totalImpulseNS(),76.828387*3e-15);
        assertEquals(79.590,s.peakThrustN()); assertEquals(.354,s.peakThrustTimeS()); assertEquals(1.430,s.curveEndTimeS());
        assertEquals(1.3752712686567163,s.effectiveBurnEnd5pctS(),1.3752712686567163*3e-15);
        assertEquals(2.181395348837211,e.evaluate(m,1.4).thrustN(),2.181395348837211*4e-15);
        assertEquals(76.79566606976745,e.evaluate(m,1.4).cumulativeImpulseNS(),76.79566606976745*3e-15);
        double previous=-1;
        for(int i=0;i<=1600;i++) { double impulse=e.evaluate(m,i/1000.).cumulativeImpulseNS(); assertTrue(impulse>=previous); previous=impulse; }
        assertEquals(new MotorThrustState(0,s.totalImpulseNS()),e.evaluate(m,1e100));
        var changed=MotorCatalogTest.change("certification",new MotorCertificationReference(1000,100,200,10));
        assertEquals(s,new MotorCurveAnalyzer().analyze(changed));
        assertEquals(e.evaluate(m,1.4),e.evaluate(changed,1.4));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void rawNonfinite(double value) {
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new MotorThrustCurveEvaluator().evaluate(AEROTECH_F50_4T,value)).getClass());
    }
    @Test void negativeAndDerivedFailure() {
        assertEquals("NEGATIVE_MOTOR_TIME",assertThrows(PropulsionEvaluationError.class,()->new MotorThrustCurveEvaluator().evaluate(AEROTECH_F50_4T,-1)).errorCode());
        for(var m:List.of(curve(new double[][]{{0,0},{1e308,20},{1.1e308,0}}),curve(new double[][]{{0,0},{1e-200,1e-200},{2e-200,0}}))) {
            assertEquals("INVALID_TOTAL_IMPULSE",assertThrows(PropulsionEvaluationError.class,()->new MotorCurveAnalyzer().analyze(m)).errorCode());
            assertEquals("INVALID_TOTAL_IMPULSE",assertThrows(PropulsionEvaluationError.class,()->new MotorThrustCurveEvaluator().evaluate(m,0)).errorCode());
        }
    }
}
