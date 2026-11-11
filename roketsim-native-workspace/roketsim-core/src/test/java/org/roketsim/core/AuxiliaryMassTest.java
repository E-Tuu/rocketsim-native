package org.roketsim.core;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.propulsion.MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE;

/** NAT-023: explicit auxiliary inputs; accepted structural fixtures stay in RocketMassTest. */
class AuxiliaryMassTest {
    private static MotorMassProperties motor(double mass,double cg) {
        return new MotorMassProperties(mass,123,cg,null);
    }
    private static void analytic(double expected,double actual) {
        // Existing RocketMassTest source-fixture contract; not a production tolerance.
        assertEquals(expected,actual,Math.abs(expected)*3e-15);
    }
    private static void error(String code,String field,double value,Runnable action) {
        var e=assertThrowsExactly(MassValidationError.class,action::run);
        assertEquals(code,e.errorCode()); assertEquals(field,e.fieldName());
        assertEquals(Double.doubleToLongBits(value),Double.doubleToLongBits(e.value()));
    }
    @Test void exactPublicApiAndStoredSchema() throws Exception {
        // MASS23-T01..04/T21: exactly two instance overloads and the original result.
        var type=RocketMassPropertiesCalculator.class;
        var legacy=type.getDeclaredMethod("evaluate",StructuralMassProperties.class,MotorMassProperties.class);
        var extended=type.getDeclaredMethod("evaluate",StructuralMassProperties.class,MotorMassProperties.class,double[].class,double[].class);
        for(var method:List.of(legacy,extended)) {
            assertTrue(Modifier.isPublic(method.getModifiers()));
            assertFalse(Modifier.isStatic(method.getModifiers()));
            assertEquals(RocketMassProperties.class,method.getReturnType());
        }
        assertEquals(2,Arrays.stream(type.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).count());
        assertEquals(0,type.getDeclaredFields().length);
        assertEquals(List.of("totalMassKg","totalCgXGeoM"),Arrays.stream(RocketMassProperties.class.getRecordComponents()).map(c->c.getName()).toList());
        assertTrue(Arrays.stream(RocketMassProperties.class.getRecordComponents()).allMatch(c->c.getType()==double.class));
        for(String absent:List.of("AuxiliaryMassCalculator","AuxiliaryMassProperties","AuxiliaryMassContribution",
                "AuxiliaryRocketMassResult","TotalRocketMassCalculatorV2","RocketMassPropertiesV2")) {
            assertThrows(ClassNotFoundException.class,()->Class.forName("org.roketsim.core.mass."+absent));
        }
    }
    @Test void singleAndEveryOrderedEntryContributesOnce() {
        // MASS23-T06/T09: exactly representable analytic fixtures.
        var c=new RocketMassPropertiesCalculator();
        assertEquals(new RocketMassProperties(4,3.25),c.evaluate(RocketMassTest.structure(2,1),motor(1,4),new double[]{1},new double[]{7}));
        var s=RocketMassTest.structure(1,0); var m=motor(1,0);
        assertEquals(new RocketMassProperties(16,1.125),c.evaluate(s,m,new double[]{2,4,8},new double[]{3,-1,2}));
        assertEquals(new RocketMassProperties(4,.5),c.evaluate(s,m,new double[]{1,1},new double[]{1,1}));
    }
    @Test void frozenMultipleAuxiliaryAndSuppliedMotorSnapshots() {
        // MASS23-T07..09 / V&V-01/02: motor state is supplied, not recomputed.
        var c=new RocketMassPropertiesCalculator(); var s=RocketMassTest.structure(.50,.60);
        double[] masses={.08,.12},cgs={.40,1.00};
        var a=c.evaluate(s,motor(.10,.90),masses,cgs);
        var b=c.evaluate(s,motor(.05,.80),masses,cgs);
        analytic(.80,a.totalMassKg()); analytic(.6775,a.totalCgXGeoM());
        analytic(.75,b.totalMassKg()); analytic(.6560,b.totalCgXGeoM());
        analytic(.542,a.totalMassKg()*a.totalCgXGeoM());
        analytic(.492,b.totalMassKg()*b.totalCgXGeoM());
        assertArrayEquals(new double[]{.08,.12},masses);
        assertArrayEquals(new double[]{.40,1.00},cgs);
    }
    @Test void emptyArraysPreserveHistoricalMotorAnchorsExactly() {
        // MASS23-T05/T22: existing J-005/J-011 authority, same objects and exact result equality.
        var c=new RocketMassPropertiesCalculator(); var s=RocketMassTest.structure();
        var installation=MotorPropertiesTest.installed(); var evaluator=new MotorPropertyEvaluator();
        double[] times={0,.354,1.4,1.43};
        double[] masses={.6357490028277474,.6232504730167545,.5978651442997246,.5978490028277474};
        double[] cgs={.6706064563777736,.6648832361521045,.6525224306832178,.6525142370178312};
        for(int i=0;i<times.length;i++) {
            var m=evaluator.evaluate(installation,DEMO_MOTOR_PROPERTY_MODEL_PROFILE,times[i]);
            var old=c.evaluate(s,m); var extended=c.evaluate(s,m,new double[0],new double[0]);
            assertEquals(old,extended);
            analytic(masses[i],extended.totalMassKg()); analytic(cgs[i],extended.totalCgXGeoM());
        }
    }
    @Test void emptyArraysPreserveLegacyExtremeAndInvalidOutcomes() {
        var c=new RocketMassPropertiesCalculator();
        for(double[] row:new double[][]{{0,0,0,1},{-2,0,1,1},{1e308,0,1e308,1},
                {Double.NaN,0,1,1},{1,0,Double.POSITIVE_INFINITY,1},{2,Double.NaN,1,1},
                {2,1e308,1,0},{2,1,-1,2},{1e-100,1e100,1e100,1e100}}) {
            var s=RocketMassTest.structure(row[0],row[1]); var m=motor(row[2],row[3]);
            RocketMassProperties expected;
            try { expected=c.evaluate(s,m); }
            catch(MassValidationError e) {
                error(e.errorCode(),e.fieldName(),e.value(),()->c.evaluate(s,m,new double[0],new double[0]));
                continue;
            }
            assertEquals(expected,c.evaluate(s,m,new double[0],new double[0]));
        }
    }
    @ParameterizedTest @ValueSource(doubles={-10,0,.626619970554718,1e100,Double.MAX_VALUE})
    void allEqualContributorCoordinatesAreExact(double x) {
        // MASS23-T10: equal-CG shortcut also avoids a meaningless overflowed moment.
        var r=new RocketMassPropertiesCalculator().evaluate(RocketMassTest.structure(2,x),motor(3,x),new double[]{4,5},new double[]{x,x});
        assertEquals(14,r.totalMassKg()); assertEquals(x,r.totalCgXGeoM());
    }
    @Test void noNearEqualOrSmallMassCutoff() {
        var c=new RocketMassPropertiesCalculator();
        var r=c.evaluate(RocketMassTest.structure(1,1),motor(1,1),new double[]{2},new double[]{Math.nextUp(Math.nextUp(1.0))});
        assertEquals(Math.nextUp(1.0),r.totalCgXGeoM());
        var tiny=c.evaluate(RocketMassTest.structure(Double.MIN_VALUE,0),motor(Double.MIN_VALUE,0),new double[]{Double.MIN_VALUE},new double[]{0});
        assertEquals(3*Double.MIN_VALUE,tiny.totalMassKg()); assertEquals(0,tiny.totalCgXGeoM());
    }
    @ParameterizedTest @ValueSource(doubles={-1,4})
    void spanIncludesAuxiliaryCoordinatesBeyondBothOriginalContributors(double x) {
        // MASS23-T11/T18: no rocket-length/old-two-contributor span restriction.
        var r=new RocketMassPropertiesCalculator().evaluate(RocketMassTest.structure(1,1),motor(1,2),new double[]{8},new double[]{x});
        assertEquals(10,r.totalMassKg()); assertEquals(x<0?-.5:3.5,r.totalCgXGeoM());
        assertTrue(Math.min(1,x)<=r.totalCgXGeoM() && r.totalCgXGeoM()<=Math.max(2,x));
        assertTrue(r.totalCgXGeoM()<1 || r.totalCgXGeoM()>2);
    }
    @Test void shapeErrorsPrecedeEntriesAndUpstreamAccess() {
        // MASS23-T12/T13: ordinary IllegalArgumentException, never a numeric error value.
        var c=new RocketMassPropertiesCalculator();
        assertThrowsExactly(IllegalArgumentException.class,()->c.evaluate(null,null,null,null));
        assertThrowsExactly(IllegalArgumentException.class,()->c.evaluate(null,null,null,new double[]{Double.NaN}));
        assertThrowsExactly(IllegalArgumentException.class,()->c.evaluate(null,null,new double[]{Double.NaN},null));
        assertThrowsExactly(IllegalArgumentException.class,()->c.evaluate(null,null,new double[]{Double.NaN},new double[0]));
        assertThrowsExactly(IllegalArgumentException.class,()->c.evaluate(null,null,new double[0],new double[]{Double.NaN}));
    }
    @ParameterizedTest @ValueSource(doubles={0,-0.0,-.08,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void structuredAuxiliaryMassErrors(double mass) {
        // MASS23-T14/T19: entry validation occurs before reading upstream records.
        error("INVALID_AUXILIARY_MASS","auxiliary_masses_kg[0]",mass,
            ()->new RocketMassPropertiesCalculator().evaluate(null,null,new double[]{mass},new double[]{Double.NaN}));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void structuredAuxiliaryCgErrors(double cg) {
        // MASS23-T15/T19.
        error("INVALID_AUXILIARY_CG","auxiliary_cg_x_geo_m[1]",cg,
            ()->new RocketMassPropertiesCalculator().evaluate(null,null,new double[]{1,2},new double[]{0,cg}));
    }
    @Test void firstInvalidIndexAndMassBeforeCg() {
        var c=new RocketMassPropertiesCalculator();
        error("INVALID_AUXILIARY_CG","auxiliary_cg_x_geo_m[0]",Double.NaN,
            ()->c.evaluate(null,null,new double[]{1,-1},new double[]{Double.NaN,0}));
        error("INVALID_AUXILIARY_MASS","auxiliary_masses_kg[1]",-1,
            ()->c.evaluate(null,null,new double[]{1,-1,0},new double[]{0,Double.POSITIVE_INFINITY,Double.NaN}));
        error("INVALID_AUXILIARY_CG","auxiliary_cg_x_geo_m[1]",Double.NEGATIVE_INFINITY,
            ()->c.evaluate(null,null,new double[]{1,2,-1},new double[]{0,Double.NEGATIVE_INFINITY,0}));
    }
    @Test void derivedErrorOrderAndFullSpanFailureRemainStructured() {
        // MASS23-T16..18; no additional policy is imposed on supplied upstream records.
        var c=new RocketMassPropertiesCalculator();
        error("INVALID_TOTAL_MASS","total_mass_kg",Double.POSITIVE_INFINITY,
            ()->c.evaluate(RocketMassTest.structure(Double.MAX_VALUE,0),motor(Double.MAX_VALUE,0),new double[]{1},new double[]{0}));
        error("INVALID_TOTAL_MASS","total_mass_kg",Double.POSITIVE_INFINITY,
            ()->c.evaluate(RocketMassTest.structure(Double.MAX_VALUE,0),motor(1,0),new double[]{Double.MAX_VALUE},new double[]{0}));
        error("INVALID_TOTAL_MASS","total_mass_kg",Double.NaN,
            ()->c.evaluate(RocketMassTest.structure(Double.NaN,0),motor(1,0),new double[]{1},new double[]{0}));
        error("NON_POSITIVE_TOTAL_MASS","total_mass_kg",-1,
            ()->c.evaluate(RocketMassTest.structure(-2,Double.MAX_VALUE),motor(.5,0),new double[]{.5},new double[]{0}));
        error("NON_POSITIVE_TOTAL_MASS","total_mass_kg",0,
            ()->c.evaluate(RocketMassTest.structure(-2,0),motor(1,0),new double[]{1},new double[]{0}));
        error("INVALID_TOTAL_CG","total_cg_x_geo_m",Double.POSITIVE_INFINITY,
            ()->c.evaluate(RocketMassTest.structure(2,Double.MAX_VALUE),motor(1,0),new double[]{.125},new double[]{.5}));
        for(double x:new double[]{Double.MAX_VALUE,-Double.MAX_VALUE}) {
            error("INVALID_TOTAL_CG","total_cg_x_geo_m",Math.copySign(Double.POSITIVE_INFINITY,x),
                ()->c.evaluate(RocketMassTest.structure(1,0),motor(1,0),new double[]{2},new double[]{x}));
        }
        error("INVALID_TOTAL_CG","total_cg_x_geo_m",Double.NaN,
            ()->c.evaluate(RocketMassTest.structure(1,Double.NaN),motor(1,0),new double[]{1},new double[]{0}));
        error("TOTAL_CG_OUTSIDE_CONTRIBUTOR_SPAN","total_cg_x_geo_m",.125/1.125,
            ()->c.evaluate(RocketMassTest.structure(2,1),motor(-1,2),new double[]{.125},new double[]{1}));
        assertEquals(new RocketMassProperties(1,-3),c.evaluate(RocketMassTest.structure(0,.6),motor(0,.9),new double[]{1},new double[]{-3}));
    }
    @Test void arraysAreUnchangedNotRetainedAndRepeatedEvaluationIsStateless() {
        // MASS23-T20/T21 / V&V-06: duplicate/unsorted inputs are not rewritten.
        var c=new RocketMassPropertiesCalculator(); var s=RocketMassTest.structure(.5,.6); var m=motor(.1,.9);
        double[] masses={.12,.08},cgs={1,.4}; double[] savedMasses=masses.clone(),savedCgs=cgs.clone();
        var r=c.evaluate(s,m,masses,cgs);
        assertEquals(r,c.evaluate(s,m,masses,cgs));
        assertEquals(r,new RocketMassPropertiesCalculator().evaluate(s,m,masses,cgs));
        assertArrayEquals(savedMasses,masses); assertArrayEquals(savedCgs,cgs);
        masses[0]=100; cgs[0]=-100;
        analytic(.8,r.totalMassKg()); analytic(.6775,r.totalCgXGeoM());
        assertEquals(r,c.evaluate(s,m,savedMasses,savedCgs));
    }
}
