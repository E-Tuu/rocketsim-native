package org.roketsim.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.propulsion.MotorPropertyModelProfile.*;

class RocketMassTest {
    static StructuralMassProperties structure() {
        return new StructuralMassPropertiesCalculator().evaluate(MotorInstallationTest.geometry(),VehicleTestData.materials());
    }
    static StructuralMassProperties structure(double mass,double cg) {
        var s=structure(); return new StructuralMassProperties(s.nose(),s.body(),s.fins(),s.motorMount(),s.centeringRings(),mass,cg);
    }
    @Test void integratedAnchors() {
        double[] times={0,.354,1.4,1.43}; double[] masses={.6357490028277474,.6232504730167545,.5978651442997246,.5978490028277474};
        double[] cgs={.6706064563777736,.6648832361521045,.6525224306832178,.6525142370178312};
        var s=structure(); var i=MotorPropertiesTest.installed(); var e=new MotorPropertyEvaluator(); var c=new RocketMassPropertiesCalculator();
        for(int n=0;n<4;n++) {
            var m=e.evaluate(i,DEMO_MOTOR_PROPERTY_MODEL_PROFILE,times[n]); var result=c.evaluate(s,m);
            assertEquals(s.structureMassKg()+m.massKg(),result.totalMassKg());
            assertEquals(masses[n],result.totalMassKg(),masses[n]*3e-15); assertEquals(cgs[n],result.totalCgXGeoM(),cgs[n]*3e-15);
            if(n==0||n==3) { assertEquals(masses[n],result.totalMassKg()); }
        }
        assertEquals(2,RocketMassProperties.class.getRecordComponents().length);
        assertEquals(0,RocketMassPropertiesCalculator.class.getDeclaredFields().length);
    }
    @ParameterizedTest @ValueSource(doubles={-10,0,.626619970554718,1e100})
    void exactEqualCoordinates(double cg) {
        var c=new RocketMassPropertiesCalculator();
        for(double[] mass:new double[][]{{.1,.3},{1e-100,1e100}}) {
            assertEquals(cg,c.evaluate(structure(mass[0],cg),new MotorMassProperties(mass[1],0,cg,DEMO_MOTOR_PROPERTY_MODEL_PROFILE)).totalCgXGeoM());
        }
    }
    @Test void suppliedAuthorityAndBothOrderings() {
        var c=new RocketMassPropertiesCalculator();
        assertEquals(new RocketMassProperties(3,4),c.evaluate(structure(2,3),new MotorMassProperties(1,.001,6,DEMO_MOTOR_PROPERTY_MODEL_PROFILE)));
        for(double[] cg:new double[][]{{1,4},{4,1},{-4,-1},{-1,-4}}) {
            assertEquals((2*cg[0]+cg[1])/3,c.evaluate(structure(2,cg[0]),new MotorMassProperties(1,0,cg[1],null)).totalCgXGeoM());
        }
    }
    @Test void invalidUpstreamTotals() {
        var c=new RocketMassPropertiesCalculator();
        for(double[] mass:new double[][]{{0,0},{-2,1},{1e308,1e308},{Double.NaN,1},{1,Double.POSITIVE_INFINITY}}) {
            String code=Double.isFinite(mass[0]+mass[1])?"NON_POSITIVE_TOTAL_MASS":"INVALID_TOTAL_MASS";
            assertEquals(code,assertThrows(MassValidationError.class,()->c.evaluate(structure(mass[0],0),new MotorMassProperties(mass[1],0,1,null))).errorCode());
        }
        for(double[] cg:new double[][]{{Double.NaN,1},{1,Double.POSITIVE_INFINITY},{1e308,0}}) {
            assertEquals("INVALID_TOTAL_CG",assertThrows(MassValidationError.class,()->c.evaluate(structure(2,cg[0]),new MotorMassProperties(1,0,cg[1],null))).errorCode());
        }
        assertEquals("TOTAL_CG_OUTSIDE_CONTRIBUTOR_SPAN",assertThrows(MassValidationError.class,()->c.evaluate(structure(2,1),new MotorMassProperties(-1,0,2,null))).errorCode());
    }
}
