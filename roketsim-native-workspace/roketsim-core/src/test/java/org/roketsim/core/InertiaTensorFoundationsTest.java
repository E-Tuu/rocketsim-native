package org.roketsim.core;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.mass.InertiaTensor3D;
import org.roketsim.core.mass.ParallelAxisInertiaCalculator;
import org.roketsim.core.math.Vector;

class InertiaTensorFoundationsTest {
    private final ParallelAxisInertiaCalculator calculator=new ParallelAxisInertiaCalculator();
    private static final InertiaTensor3D ZERO=new InertiaTensor3D(0,0,0,0,0,0);
    private static Vector vector(double... values) { return Vector.of(values); }
    private static void invalid(String field,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action);
        assertEquals(IllegalArgumentException.class,error.getClass());
        assertTrue(error.getMessage().contains(field),error.getMessage());
    }
    private static InertiaTensor3D tensor(double[] a) { return new InertiaTensor3D(a[0],a[1],a[2],a[3],a[4],a[5]); }
    @Test void exactSchemaSymmetricEntriesAndApi() throws Exception {
        var fields=InertiaTensor3D.class.getRecordComponents();
        assertArrayEquals(new String[]{"ixxKgM2","iyyKgM2","izzKgM2","ixyKgM2","ixzKgM2","iyzKgM2"},
            Arrays.stream(fields).map(x->x.getName()).toArray(String[]::new));
        assertTrue(Arrays.stream(fields).allMatch(x->x.getType()==double.class));
        assertEquals(6,InertiaTensor3D.class.getDeclaredFields().length);
        assertTrue(Modifier.isFinal(ParallelAxisInertiaCalculator.class.getModifiers()));
        assertEquals(0,ParallelAxisInertiaCalculator.class.getDeclaredFields().length);
        assertEquals(1,ParallelAxisInertiaCalculator.class.getDeclaredMethods().length);
        assertEquals(InertiaTensor3D.class,ParallelAxisInertiaCalculator.class.getMethod("shiftFromCentroid",
            InertiaTensor3D.class,double.class,Vector.class).getReturnType());
        var t=new InertiaTensor3D(1,2,3,-4,5,-6);
        assertEquals(-4,t.ixyKgM2()); assertEquals(5,t.ixzKgM2()); assertEquals(-6,t.iyzKgM2());
    }
    @Test void generalThreeDimensionalAnalyticFixture() {
        assertEquals(new InertiaTensor3D(27,22,13,-4,-6,-12),
            calculator.shiftFromCentroid(new InertiaTensor3D(1,2,3,0,0,0),2,vector(1,2,3)));
    }
    @Test void axialPositiveAndNegativeShift() {
        var centroid=new InertiaTensor3D(1,2,3,-.25,.5,-.75);
        for(double z:new double[]{.3,-.3}) {
            var result=calculator.shiftFromCentroid(centroid,2,vector(0,0,z));
            // Decimal 0.3 and rounded analytic sums: one ULP of the stated expected value.
            assertEquals(1.18,result.ixxKgM2(),Math.ulp(1.18));
            assertEquals(2.18,result.iyyKgM2(),Math.ulp(2.18));
            assertEquals(centroid.izzKgM2(),result.izzKgM2());
            assertEquals(centroid.ixyKgM2(),result.ixyKgM2());
            assertEquals(centroid.ixzKgM2(),result.ixzKgM2()); assertEquals(centroid.iyzKgM2(),result.iyzKgM2());
        }
        var increment=calculator.shiftFromCentroid(ZERO,2,vector(0,0,.3));
        assertEquals(.18,increment.ixxKgM2(),Math.ulp(.18)); assertEquals(.18,increment.iyyKgM2(),Math.ulp(.18));
        assertEquals(0,increment.izzKgM2());
    }
    @Test void zeroShiftPreservesComponentsAndReturnsNewValue() {
        var centroid=new InertiaTensor3D(1,2,3,-4,5,-6);
        var result=calculator.shiftFromCentroid(centroid,2,vector(0,0,0));
        assertEquals(centroid,result); assertNotSame(centroid,result);
    }
    @Test void fullVectorSignInvarianceButNotSingleAxisSignInvariance() {
        var centroid=new InertiaTensor3D(1,2,3,.25,-.5,.75);
        assertEquals(calculator.shiftFromCentroid(centroid,2,vector(.4,-.6,.8)),
            calculator.shiftFromCentroid(centroid,2,vector(-.4,.6,-.8)));
        assertEquals(new InertiaTensor3D(26,20,10,4,-6,12),calculator.shiftFromCentroid(ZERO,2,vector(1,-2,3)));
    }
    @Test void pointMassLimit() {
        assertEquals(new InertiaTensor3D(26,20,10,-4,-6,-12),calculator.shiftFromCentroid(ZERO,2,vector(1,2,3)));
    }
    @Test void thinRodReferenceAtCentroidAndEnd() {
        var rod=new InertiaTensor3D(1.5,1.5,0,0,0,0);
        assertEquals(rod,calculator.shiftFromCentroid(rod,2,vector(0,0,0)));
        // m=2, L=3: centroid m*L^2/12=1.5; end m*L^2/3=6.
        assertEquals(new InertiaTensor3D(6,6,0,0,0,0),calculator.shiftFromCentroid(rod,2,vector(0,0,1.5)));
    }
    @Test void hollowCylinderAnalyticReference() {
        var cylinder=new InertiaTensor3D(.17486666666666667,.17486666666666667,.0164,0,0,0);
        assertEquals(cylinder,calculator.shiftFromCentroid(cylinder,2,vector(0,0,0)));
        var end=calculator.shiftFromCentroid(cylinder,2,vector(0,0,.5));
        // Rounded analytic centroid plus exact m*(L/2)^2=0.5; one ULP rounding allowance.
        assertEquals(.6748666666666667,end.ixxKgM2(),Math.ulp(.6748666666666667));
        assertEquals(.6748666666666667,end.iyyKgM2(),Math.ulp(.6748666666666667));
        assertEquals(.0164,end.izzKgM2()); assertEquals(0,end.ixyKgM2()); assertEquals(0,end.ixzKgM2()); assertEquals(0,end.iyzKgM2());
    }
    @Test void translatingCoordinateOriginDoesNotChangeDisplacementResult() {
        double[] centroid={1,2,3},reference={5,-4,11},originShift={16,-8,32};
        double[] before=new double[3],after=new double[3];
        for(int i=0;i<3;i++) {
            before[i]=reference[i]-centroid[i];
            after[i]=(reference[i]+originShift[i])-(centroid[i]+originShift[i]);
        }
        assertArrayEquals(before,after);
        assertEquals(calculator.shiftFromCentroid(ZERO,2,vector(before)),calculator.shiftFromCentroid(ZERO,2,vector(after)));
    }
    @Test void everyNonfiniteTensorComponentRejectedInOrder() {
        String[] names={"ixxKgM2","iyyKgM2","izzKgM2","ixyKgM2","ixzKgM2","iyzKgM2"};
        for(int i=0;i<6;i++)for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            double[] values={1,2,3,-4,5,-6}; values[i]=bad;
            invalid(names[i],()->tensor(values));
        }
        invalid("ixxKgM2",()->new InertiaTensor3D(-1,Double.NaN,3,0,0,0));
        invalid("iyyKgM2",()->new InertiaTensor3D(0,-1,Double.NaN,0,0,0));
        invalid("izzKgM2",()->new InertiaTensor3D(0,0,-1,Double.NaN,0,0));
        invalid("ixyKgM2",()->new InertiaTensor3D(0,0,0,Double.NaN,Double.NaN,0));
        invalid("ixzKgM2",()->new InertiaTensor3D(0,0,0,0,Double.NaN,Double.NaN));
    }
    @Test void negativeDiagonalsRejectedZeroAndSignedOffDiagonalsAcceptedWithoutPsdClaim() {
        for(int i=0;i<3;i++) {
            double[] values={0,0,0,0,0,0}; values[i]=-Double.MIN_VALUE;
            invalid(new String[]{"ixxKgM2","iyyKgM2","izzKgM2"}[i],()->tensor(values));
        }
        assertEquals(0,ZERO.ixxKgM2());
        var notPsd=new InertiaTensor3D(0,0,0,-1,2,-3);
        assertEquals(-1,notPsd.ixyKgM2()); assertEquals(2,notPsd.ixzKgM2());
        assertEquals(Double.doubleToRawLongBits(-0.0),Double.doubleToRawLongBits(new InertiaTensor3D(-0.0,0,0,0,0,0).ixxKgM2()));
    }
    @Test void nullDimensionAndMassValidationOrder() {
        invalid("centroidalInertia",()->calculator.shiftFromCentroid(null,Double.NaN,null));
        for(double bad:new double[]{0,-0.0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            invalid("massKg",()->calculator.shiftFromCentroid(ZERO,bad,null));
        invalid("displacementBodyM",()->calculator.shiftFromCentroid(ZERO,1,null));
        for(var wrong:new Vector[]{vector(),vector(1,2),vector(1,2,3,4)})
            invalid("displacementBodyM",()->calculator.shiftFromCentroid(ZERO,1,wrong));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            invalid("vector[1]",()->vector(0,bad,0));
        assertEquals(ZERO,calculator.shiftFromCentroid(ZERO,Double.MIN_VALUE,vector(0,0,0)));
    }
    @Test void derivedOverflowAndOverflowingIntermediateRejected() {
        invalid("ixxKgM2",()->calculator.shiftFromCentroid(ZERO,1,vector(0,Double.MAX_VALUE,0)));
        invalid("iyyKgM2",()->calculator.shiftFromCentroid(ZERO,1,vector(Double.MAX_VALUE,0,0)));
        invalid("ixxKgM2",()->calculator.shiftFromCentroid(new InertiaTensor3D(Double.MAX_VALUE,0,0,0,0,0),Double.MAX_VALUE,vector(0,1,0)));
        // Squaring overflows even though reordering the tiny mass could make the mathematical term finite.
        invalid("ixxKgM2",()->calculator.shiftFromCentroid(ZERO,Double.MIN_VALUE,vector(0,Double.MAX_VALUE,0)));
        invalid("ixyKgM2",()->calculator.shiftFromCentroid(new InertiaTensor3D(0,0,0,-Double.MAX_VALUE,0,0),Double.MAX_VALUE/4,vector(1,1,0)));
    }
    @Test void immutableInputsIndependentOutputAndRepeatability() {
        double[] caller={1,2,3}; var displacement=vector(caller); var centroid=new InertiaTensor3D(1,2,3,0,0,0);
        var expected=new InertiaTensor3D(27,22,13,-4,-6,-12);
        var result=calculator.shiftFromCentroid(centroid,2,displacement);
        caller[0]=99; var exported=displacement.toArray(); exported[1]=99;
        assertArrayEquals(new double[]{1,2,3},displacement.toArray()); assertEquals(expected,result);
        for(int i=0;i<3;i++) {
            calculator.shiftFromCentroid(ZERO,1,vector(-4,1,0));
            assertEquals(expected,calculator.shiftFromCentroid(centroid,2,displacement));
            assertEquals(expected,new ParallelAxisInertiaCalculator().shiftFromCentroid(centroid,2,displacement));
        }
        assertEquals(new InertiaTensor3D(1,2,3,0,0,0),centroid);
    }
}
