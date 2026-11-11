package org.roketsim.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.mass.InertiaTensor3D;
import org.roketsim.core.propulsion.*;

class MotorInertiaTest {
    private static final InertiaTensor3D A=new InertiaTensor3D(1,2,3,-.25,.5,-.75),B=new InertiaTensor3D(3,6,9,.75,-.5,.25);
    private final MotorInertiaEvaluator evaluator=new MotorInertiaEvaluator();
    private static MotorDefinition f50() { return MotorCatalogs.AEROTECH_F50_4T; }
    private static MotorCurveStatistics stats(MotorDefinition m) { return new MotorCurveAnalyzer().analyze(m); }
    private static MotorMassProperties mass(double value,double cg) { return new MotorMassProperties(value,cg,.9+cg,MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE); }
    private static MotorMassProperties f50Mass() { return new MotorPropertyEvaluator().evaluate(MotorPropertiesTest.installed(),MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,0); }
    private MotorInertiaResult evaluate(MotorInertiaModel model,double time) { return evaluator.evaluate(f50(),f50Mass(),stats(f50()),time,model); }
    private static void close(double expected,double actual) { GeometryResolverTest.close(expected,actual,3e-14); }
    private static MotorDefinition synthetic() {
        var m=f50();double prop=2*1000*Math.PI*(.02*.02-.01*.01)*.04;
        return new MotorDefinition("synthetic_two_grain","TEST","TEST","TEST",m.motorType(),"TEST",.05,.3,.2+prop,prop,0,
            m.thrustCurve(),m.certification(),m.provenance(),null,null);
    }
    private static RadialGrainMotorInertiaModel grain() {
        return new RadialGrainMotorInertiaModel(.15,.001,.00002,2,.04,.08,.05,1000,.02,.01,
            synthetic().propellantMassKg()*3e-14,.3*3e-14,"SYNTHETIC two-grain analytical fixture; not measured F50");
    }
    private MotorInertiaResult radial(double fraction) {
        var m=synthetic();double dry=m.initialMassKg()-m.propellantMassKg(),remaining=m.propellantMassKg()*fraction;
        double cg=(dry*.15+remaining*.13)/(dry+remaining);
        return evaluator.evaluate(m,mass(dry+remaining,cg),stats(m),.5,grain());
    }
    @Test void exactSchemasAndStatelessSelection() throws Exception {
        assertEquals(3,MotorInertiaModelKind.values().length);assertTrue(MotorInertiaModel.class.isSealed());
        assertEquals(3,MotorInertiaModel.class.getPermittedSubclasses().length);
        assertEquals(2,MotorInertiaSample.class.getRecordComponents().length);assertEquals(2,MotorInertiaCurveModel.class.getRecordComponents().length);
        assertEquals(13,RadialGrainMotorInertiaModel.class.getRecordComponents().length);
        assertEquals(1,UniformCylinderMotorInertiaModel.class.getRecordComponents().length);assertEquals(3,MotorInertiaResult.class.getRecordComponents().length);
        assertEquals(0,MotorInertiaEvaluator.class.getDeclaredFields().length);
        assertEquals(MotorInertiaResult.class,MotorInertiaEvaluator.class.getMethod("evaluate",MotorDefinition.class,
            MotorMassProperties.class,MotorCurveStatistics.class,double.class,MotorInertiaModel.class).getReturnType());
    }
    @Test void sourceNodesSixTermsInterpolationAndTerminalHold() {
        String source="  independent claimed source ID  ";
        var curve=new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,A),new MotorInertiaSample(2,B)),source);
        assertSame(A,evaluate(curve,0).inertiaAtMotorCg());assertSame(B,evaluate(curve,2).inertiaAtMotorCg());assertSame(B,evaluate(curve,100).inertiaAtMotorCg());
        assertEquals(new InertiaTensor3D(2,4,6,.25,0,-.25),evaluate(curve,1).inertiaAtMotorCg());
        assertEquals(source,evaluate(curve,1).provenanceReference());assertEquals(MotorInertiaModelKind.EXPLICIT_CURVE,evaluate(curve,1).modelKind());
    }
    @Test void immutableSourceSnapshotAndRepeatability() {
        var caller=new ArrayList<>(List.of(new MotorInertiaSample(0,A),new MotorInertiaSample(2,B)));
        var curve=new MotorInertiaCurveModel(caller,"SYNTHETIC");var expected=evaluate(curve,1);caller.clear();
        assertEquals(2,curve.samples().size());assertThrows(UnsupportedOperationException.class,()->curve.samples().clear());
        evaluate(new UniformCylinderMotorInertiaModel(0),0);
        assertEquals(expected,evaluate(curve,1));assertEquals(expected,new MotorInertiaEvaluator().evaluate(f50(),f50Mass(),stats(f50()),1,curve));
    }
    @Test void invalidSourceGridsCoverageAndReferences() {
        for(var list:List.of(List.<MotorInertiaSample>of(),List.of(new MotorInertiaSample(0,A)),
            List.of(new MotorInertiaSample(1,A),new MotorInertiaSample(2,B)),
            List.of(new MotorInertiaSample(0,A),new MotorInertiaSample(0,B)),
            List.of(new MotorInertiaSample(0,A),new MotorInertiaSample(2,B),new MotorInertiaSample(1,A))))
            assertThrows(IllegalArgumentException.class,()->new MotorInertiaCurveModel(list,"source"));
        assertThrows(IllegalArgumentException.class,()->new MotorInertiaCurveModel(null,"source"));
        assertThrows(IllegalArgumentException.class,()->new MotorInertiaCurveModel(java.util.Arrays.asList(new MotorInertiaSample(0,A),null),"source"));
        for(String text:new String[]{null,""," \t"})assertThrows(IllegalArgumentException.class,
            ()->new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,A),new MotorInertiaSample(2,B)),text));
        var shortCurve=new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,A),new MotorInertiaSample(1,B)),"source");
        assertThrows(IllegalArgumentException.class,()->evaluate(shortCurve,0));
        assertThrows(IllegalArgumentException.class,()->evaluate(null,0));
    }
    @Test void uniformCylinderUsesSuppliedCurrentMassAndVisibleApproximation() {
        var model=new UniformCylinderMotorInertiaModel(0);var mass=f50Mass();var result=evaluate(model,0);
        double r=.029/2,l=.098;
        close(mass.massKg()*r*r/2,result.inertiaAtMotorCg().izzKgM2());
        close(mass.massKg()*(3*r*r+l*l)/12,result.inertiaAtMotorCg().ixxKgM2());
        assertEquals(MotorInertiaModelKind.UNIFORM_CYLINDER,result.modelKind());assertTrue(result.provenanceReference().contains("APPROXIMATION"));
        var half=evaluator.evaluate(f50(),mass(mass.massKg()/2,l/2),stats(f50()),1,model);
        close(result.inertiaAtMotorCg().ixxKgM2()/2,half.inertiaAtMotorCg().ixxKgM2());
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),mass(.08,.04),stats(f50()),0,model));
    }
    @Test void twoGrainInitialHalfMassAndDryLimitAnalytical() {
        var motor=synthetic();double dry=motor.initialMassKg()-motor.propellantMassKg();
        for(double fraction:new double[]{1,.5,0}) {
            double remaining=motor.propellantMassKg()*fraction,each=remaining/2;
            double cg=(dry*.15+remaining*.13)/(dry+remaining);
            // Independent physical sum: dry at .15, grain centers .07/.19, half-burn squared radius fixed analytically.
            double inner2=fraction==1?.0001:fraction==.5?.00025:.0004;
            double expectedZ=.00002+remaining*(.0004+inner2)/2;
            double expectedX=.001+dry*(.15-cg)*(.15-cg)+remaining*(3*(.0004+inner2)+.0016)/12
                +each*((.07-cg)*(.07-cg)+(.19-cg)*(.19-cg));
            var result=radial(fraction);
            close(expectedZ,result.inertiaAtMotorCg().izzKgM2());close(expectedX,result.inertiaAtMotorCg().ixxKgM2());
            assertEquals(result.inertiaAtMotorCg().ixxKgM2(),result.inertiaAtMotorCg().iyyKgM2());assertEquals(0,result.inertiaAtMotorCg().ixzKgM2());
            assertEquals(MotorInertiaModelKind.RADIAL_GRAIN,result.modelKind());assertEquals(grain().sourceReference(),result.provenanceReference());
        }
        assertEquals(new InertiaTensor3D(.001,.001,.00002,0,0,0),radial(0).inertiaAtMotorCg());
        assertEquals(.015811388300841896,Math.sqrt(.00025));
    }
    @Test void radialMassCgAndPlacementInconsistencyRejectedNoFallback() {
        var m=synthetic();var g=grain();var properties=mass(m.initialMassKg(),.15);
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(m,properties,stats(m),0,g));
        var inconsistent=(RadialGrainMotorInertiaModel)VehicleTestData.replace(g,"grain_density_kg_m3",900.0);
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(m,properties,stats(m),0,inconsistent));
        for(String field:new String[]{"first_grain_front_from_motor_front_m","dry_cg_from_front_m","grain_outer_radius_m"}) {
            double invalid=field.equals("grain_outer_radius_m")?.03:1;
            var model=(RadialGrainMotorInertiaModel)VehicleTestData.replace(g,field,invalid);
            assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(m,properties,stats(m),0,model));
        }
        double dry=m.initialMassKg()-m.propellantMassKg();
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(m,mass(dry-.01,.15),stats(m),0,g));
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(m,mass(m.initialMassKg()+.01,.15),stats(m),0,g));
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),f50Mass(),stats(f50()),0,g));
    }
    @Test void radialModelConstructorDomainAndDryAdmissibility() {
        var g=grain();
        for(String field:new String[]{"grain_length_m","grain_density_kg_m3","grain_outer_radius_m"})
            for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
                assertThrows(IllegalArgumentException.class,()->VehicleTestData.replace(g,field,bad));
        for(String field:new String[]{"dry_cg_from_front_m","dry_transverse_inertia_kg_m2","dry_axial_inertia_kg_m2","grain_separation_m",
            "first_grain_front_from_motor_front_m","grain_initial_inner_radius_m","initial_mass_tolerance_kg","motor_cg_tolerance_m"})
            for(double bad:new double[]{-1,Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY})
                assertThrows(IllegalArgumentException.class,()->VehicleTestData.replace(g,field,bad));
        assertThrows(IllegalArgumentException.class,()->VehicleTestData.replace(g,"grain_count",0));
        assertThrows(IllegalArgumentException.class,()->VehicleTestData.replace(g,"grain_initial_inner_radius_m",.02));
        assertThrows(IllegalArgumentException.class,()->VehicleTestData.replace(g,"dry_axial_inertia_kg_m2",.003));
        assertThrows(IllegalArgumentException.class,()->VehicleTestData.replace(g,"source_reference"," "));
    }
    @Test void nonfiniteTimeMassCgAndNulls() {
        var model=new UniformCylinderMotorInertiaModel(0);
        for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,()->evaluate(model,value));
            assertThrows(IllegalArgumentException.class,()->new MotorInertiaSample(value,A));
            assertThrows(IllegalArgumentException.class,()->new UniformCylinderMotorInertiaModel(value));
        }
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),mass(value,.049),stats(f50()),0,model));
        for(double value:new double[]{-1,.1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),mass(.08,value),stats(f50()),0,model));
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(null,f50Mass(),stats(f50()),0,model));
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),null,stats(f50()),0,model));
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),f50Mass(),null,0,model));
        assertThrows(IllegalArgumentException.class,()->new MotorInertiaSample(0,null));
        assertThrows(IllegalArgumentException.class,()->new MotorInertiaResult(null,MotorInertiaModelKind.EXPLICIT_CURVE,"source"));
    }
    @Test void derivedOverflowRejectedAndAuthoritiesUnchanged() {
        var curve=new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,new InertiaTensor3D(1,1,1,-Double.MAX_VALUE,0,0)),
            new MotorInertiaSample(2,new InertiaTensor3D(1,1,1,Double.MAX_VALUE,0,0))),"synthetic overflow");
        assertThrows(IllegalArgumentException.class,()->evaluate(curve,1));
        var huge=(MotorDefinition)VehicleTestData.replace(f50(),"diameter_m",Double.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(huge,f50Mass(),stats(huge),0,new UniformCylinderMotorInertiaModel(0)));
        var g=(RadialGrainMotorInertiaModel)VehicleTestData.replace(grain(),"grain_density_kg_m3",Double.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(synthetic(),mass(.25,.14),stats(synthetic()),0,g));
        var before=f50Mass();evaluate(new UniformCylinderMotorInertiaModel(0),0);assertEquals(before,f50Mass());
        assertNull(f50().massCurve());assertNull(f50().cgCurve());
    }
    @Test void explicitlySuppliedConsistencyTolerancesHaveNoHiddenFallbackOrClamping() {
        var m=synthetic();var g=grain();double dry=m.initialMassKg()-m.propellantMassKg();
        var near=(RadialGrainMotorInertiaModel)VehicleTestData.replace(g,"grain_density_kg_m3",1000*(1+1e-8));
        near=(RadialGrainMotorInertiaModel)VehicleTestData.replace(near,"initial_mass_tolerance_kg",m.propellantMassKg()*2e-8);
        var cg=mass(dry,.15);
        assertEquals(new InertiaTensor3D(.001,.001,.00002,0,0,0),evaluator.evaluate(m,cg,stats(m),2,near).inertiaAtMotorCg());
        assertEquals(dry,cg.massKg());
        var uniform=new UniformCylinderMotorInertiaModel(.002);
        var midpoint=evaluator.evaluate(f50(),mass(.08,.049),stats(f50()),0,uniform);
        var offset=evaluator.evaluate(f50(),mass(.08,.050),stats(f50()),0,uniform).inertiaAtMotorCg();
        close(midpoint.inertiaAtMotorCg().ixxKgM2()+.08*.001*.001,offset.ixxKgM2());
        assertEquals(midpoint.inertiaAtMotorCg().izzKgM2(),offset.izzKgM2());
        assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),mass(.08,.052),stats(f50()),0,uniform));
    }
    @Test void oneGrainAndSourceCoverageBoundary() {
        var m=synthetic();var g=grain();double dry=m.initialMassKg()-m.propellantMassKg();
        var one=new RadialGrainMotorInertiaModel(.15,.001,.00002,1,.08,0,.11,1000,.02,.01,
            g.initialMassToleranceKg(),g.motorCgToleranceM(),"SYNTHETIC single centered grain");
        var result=evaluator.evaluate(m,mass(m.initialMassKg(),.15),stats(m),0,one).inertiaAtMotorCg();
        close(.001+m.propellantMassKg()*(3*.0005+.08*.08)/12,result.ixxKgM2());
        assertTrue(dry>0);
        var exactEnd=new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,A),new MotorInertiaSample(stats(f50()).curveEndTimeS(),B)),"endpoint source");
        assertSame(B,evaluate(exactEnd,stats(f50()).curveEndTimeS()).inertiaAtMotorCg());
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(f50(),f50Mass(),FlightEventsTest.stats(invalid),0,exactEnd));
        assertThrows(IllegalArgumentException.class,()->new MotorInertiaResult(A,null,"source"));
        assertThrows(IllegalArgumentException.class,()->new MotorInertiaResult(A,MotorInertiaModelKind.EXPLICIT_CURVE,""));
    }
}
