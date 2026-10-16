package org.roketsim.core;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.propulsion.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.propulsion.MotorCatalogs.*;
import static org.roketsim.core.propulsion.MotorPropertyModelProfile.*;

class MotorPropertiesTest {
    static MotorInstallation installed() { return new MotorInstallationResolver().resolve(MotorInstallationTest.geometry(),AEROTECH_F50_4T); }
    static MotorPropertyModelProfile explicit() { return new MotorPropertyModelProfile(MotorMassEvolutionModel.EXPLICIT_CURVE,MotorCgEvolutionModel.EXPLICIT_CURVE); }
    static MotorDefinition sourceMotor() {
        var m=(MotorDefinition)VehicleTestData.replace(AEROTECH_F50_4T,"initial_mass_kg",.1);
        m=(MotorDefinition)VehicleTestData.replace(m,"length_m",.1);
        m=(MotorDefinition)VehicleTestData.replace(m,"thrust_curve",List.of(new ThrustSample(0,0),new ThrustSample(.2,20),new ThrustSample(.8,0)));
        // All three fields must change atomically to preserve source/provenance consistency.
        var p=m.provenance();
        return new MotorDefinition(m.motorId(),m.manufacturer(),m.designation(),m.family(),m.motorType(),m.propellantName(),m.diameterM(),m.lengthM(),m.initialMassKg(),m.propellantMassKg(),m.ejectionDelayS(),m.thrustCurve(),m.certification(),
            new MotorDataProvenance(p.manufacturerSource(),p.certificationSource(),p.thrustCurveSource(),p.normalizationNote(),"Synthetic test mass source","Synthetic test CG source"),
            List.of(new MotorMassSample(0,.1),new MotorMassSample(.5,.08),new MotorMassSample(1,.06)),
            List.of(new MotorCgSample(0,.04),new MotorCgSample(.5,.05),new MotorCgSample(1,.06)));
    }
    @Test void f50AnchorsAndTail() {
        var e=new MotorPropertyEvaluator(); var i=installed(); double[] times={0,.354,1.4,1.43,100};
        double[] expected={.0849,.0724014701890071,.047016141471977205,.047,.047};
        for(int n=0;n<times.length;n++) { var result=e.evaluate(i,DEMO_MOTOR_PROPERTY_MODEL_PROFILE,times[n]);
            assertEquals(expected[n],result.massKg(),expected[n]*3e-15);
            assertEquals(.049,result.cgLocalFromFrontM()); assertEquals(.956,result.cgXGeoM(),.956*3e-15);
            assertSame(DEMO_MOTOR_PROPERTY_MODEL_PROFILE,result.modelProfile());
        }
        var stats=new MotorCurveAnalyzer().analyze(i.motor());
        assertTrue(e.evaluate(i,DEMO_MOTOR_PROPERTY_MODEL_PROFILE,stats.effectiveBurnEnd5pctS()).massKg()>e.evaluate(i,DEMO_MOTOR_PROPERTY_MODEL_PROFILE,1.4).massKg());
        assertTrue(e.evaluate(i,DEMO_MOTOR_PROPERTY_MODEL_PROFILE,1.4).massKg()>e.evaluate(i,DEMO_MOTOR_PROPERTY_MODEL_PROFILE,1.43).massKg());
    }
    @Test void explicitIndependentGridsAndProfiles() {
        var m=sourceMotor(); var i=new MotorInstallation(m,.9,1,0,.1); var e=new MotorPropertyEvaluator();
        var a=e.evaluate(i,explicit(),.25); assertEquals(.09,a.massKg(),.09*3e-15); assertEquals(.045,a.cgLocalFromFrontM(),.045*3e-15); assertEquals(.945,a.cgXGeoM(),.945*3e-15);
        var b=e.evaluate(i,explicit(),.75); assertEquals(.07,b.massKg(),.07*3e-15); assertEquals(.055,b.cgLocalFromFrontM(),.055*3e-15);
        assertEquals(.06,e.evaluate(i,explicit(),100).massKg()); assertEquals(.06,e.evaluate(i,explicit(),100).cgLocalFromFrontM());
        for(var sample:m.massCurve()) { assertEquals(sample.massKg(),e.evaluate(i,explicit(),sample.timeS()).massKg()); }
        for(var sample:m.cgCurve()) { assertEquals(sample.cgFromFrontM(),e.evaluate(i,explicit(),sample.timeS()).cgLocalFromFrontM()); }
        for(var mass:MotorMassEvolutionModel.values()) { for(var cg:MotorCgEvolutionModel.values()) {
            var profile=new MotorPropertyModelProfile(mass,cg); var result=e.evaluate(i,profile,.25);
            assertSame(profile,result.modelProfile());
            if(cg==MotorCgEvolutionModel.FIXED_MIDPOINT) { assertEquals(.05,result.cgLocalFromFrontM()); }
        } }
        m=(MotorDefinition)VehicleTestData.replace(m,"mass_curve",List.of(new MotorMassSample(0,.1),new MotorMassSample(.4,.08),new MotorMassSample(2,.04)));
        m=(MotorDefinition)VehicleTestData.replace(m,"cg_curve",List.of(new MotorCgSample(0,.02),new MotorCgSample(.6,.08),new MotorCgSample(3,.02)));
        var independent=new MotorInstallation(m,.9,1,0,.1);
        assertEquals(.065,e.evaluate(independent,explicit(),1).massKg(),.065*3e-15);
        assertEquals(.07,e.evaluate(independent,explicit(),1).cgLocalFromFrontM(),.07*3e-15);
        assertEquals(.04,e.evaluate(independent,explicit(),2.5).massKg());
    }
    @Test void modelErrorsAndNoFallback() {
        var e=new MotorPropertyEvaluator(); var i=installed();
        assertThrows(PropulsionEvaluationError.class,()->new MotorPropertyModelProfile("impulse_proportional",MotorCgEvolutionModel.FIXED_MIDPOINT));
        assertEquals("INVALID_MOTOR_PROPERTY_MODEL",assertThrows(PropulsionEvaluationError.class,()->e.evaluate(i,null,0)).errorCode());
        assertEquals("EXPLICIT_MASS_CURVE_REQUIRED",assertThrows(PropulsionEvaluationError.class,()->e.evaluate(i,explicit(),0)).errorCode());
        assertEquals("EXPLICIT_CG_CURVE_REQUIRED",assertThrows(PropulsionEvaluationError.class,()->e.evaluate(i,new MotorPropertyModelProfile(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,MotorCgEvolutionModel.EXPLICIT_CURVE),0)).errorCode());
        assertEquals("NEGATIVE_MOTOR_TIME",assertThrows(PropulsionEvaluationError.class,()->e.evaluate(i,null,-1)).errorCode());
        assertEquals("INVALID_MOTOR_CG_X_GEO_RESULT",assertThrows(PropulsionEvaluationError.class,()->e.evaluate(new MotorInstallation(i.motor(),Double.POSITIVE_INFINITY,1,0,.1),DEMO_MOTOR_PROPERTY_MODEL_PROFILE,0)).errorCode());
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void nonfiniteTime(double value) { assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,
        ()->new MotorPropertyEvaluator().evaluate(installed(),DEMO_MOTOR_PROPERTY_MODEL_PROFILE,value)).getClass()); }
    @Test void sourceValidation() {
        var m=sourceMotor();
        assertEquals("MASS_CURVE_INCREASES",assertThrows(MotorValidationError.class,()->VehicleTestData.replace(m,"mass_curve",List.of(new MotorMassSample(0,.1),new MotorMassSample(1,.11)))).errorCode());
        assertEquals("CG_OUTSIDE_MOTOR",assertThrows(MotorValidationError.class,()->VehicleTestData.replace(m,"cg_curve",List.of(new MotorCgSample(0,.04),new MotorCgSample(1,.101)))).errorCode());
        assertEquals("MASS_CURVE_PROVENANCE_MISMATCH",assertThrows(MotorValidationError.class,()->VehicleTestData.replace(m,"mass_curve",null)).errorCode());
    }
}
