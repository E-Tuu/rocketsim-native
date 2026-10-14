package org.roketsim.core;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.roketsim.core.math.Quaternion;
import org.roketsim.core.math.Quaternions;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;
import static org.junit.jupiter.api.Assertions.*;

/** Python test_quaternion.py ve math-foundation V&V fixture'larının Java karşılığı. */
class QuaternionTest {
    // Python TEST_ATOL/VV_ATOL; production toleransı değildir.
    private static final double TEST_ATOL=1e-12;
    private static final Quaternion IDENTITY=new Quaternion(1,0,0,0);
    private static Vector v(double... values) { return Vector.of(values); }
    private static void close(double[] expected,double[] actual) { assertArrayEquals(expected,actual,TEST_ATOL); }

    @Test void inputAndDefensiveCopies() {
        double[] source={1,2,3,4};
        Quaternion q=Quaternion.of(source);
        assertEquals(q,Quaternion.of(new int[]{1,2,3,4}));
        assertEquals(q,Quaternion.of(List.of(1,2,3,4)));
        assertEquals(q,Quaternion.of(q));
        assertNotSame(q,Quaternion.of(q));
        source[0]=99; q.toArray()[1]=88;
        assertArrayEquals(new double[]{1,2,3,4},q.toArray());
        assertEquals(1,q.w()); assertEquals(2,q.x()); assertEquals(3,q.y()); assertEquals(4,q.z());
        assertTrue(Quaternion.class.isRecord());
        assertTrue(Arrays.stream(Quaternion.class.getDeclaredFields())
            .filter(f -> !Modifier.isStatic(f.getModifiers())).allMatch(f -> Modifier.isFinal(f.getModifiers())));
        assertArrayEquals(new double[]{0,0,0,0},Quaternion.of(new double[4]).toArray());
    }
    static Stream<Arguments> wrongShapes() {
        return Stream.of(Arguments.of((Object)new double[3]),Arguments.of((Object)new double[5]),
            Arguments.of((Object)new double[0]));
    }
    @ParameterizedTest @MethodSource("wrongShapes") void rejectWrongShape(Object input) {
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternion.of(input)).getMessage().contains("shape"));
    }
    @Test void rejectNestedAndNonnumerical() {
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternion.of(new double[][]{{1,0,0,0}}))
            .getMessage().contains("1-D"));
        assertThrows(IllegalArgumentException.class,()->Quaternion.of(new double[0][0]));
        assertThrows(IllegalArgumentException.class,()->Quaternion.of(List.of("bad",0,0,0)));
        assertThrows(IllegalArgumentException.class,()->Quaternion.of(1));
    }
    static Stream<Double> nonfinite() { return Stream.of(Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY); }
    @ParameterizedTest @MethodSource("nonfinite") void nonfiniteValues(double value) {
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternion.of(new double[]{1,0,value,0},"attitude"))
            .getMessage().contains("attitude[2]"));
        assertThrows(IllegalArgumentException.class,()->new Quaternion(value,0,0,0));
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternions.fromAxisAngle(v(1,0,0),value,0))
            .getMessage().contains("angle_rad"));
    }
    @Test void identityChain() {
        assertEquals(1,Quaternions.norm(IDENTITY));
        // NumPy assert_array_equal gibi signed zero eşittir; nonzero delta yoktur.
        assertArrayEquals(IDENTITY.toArray(),Quaternions.inverse(IDENTITY,0).toArray(),0.0);
        assertArrayEquals(IDENTITY.toArray(),Quaternions.fromAxisAngle(v(1,0,0),0,0).toArray());
        assertArrayEquals(IDENTITY.toArray(),Quaternions.normalize(new Quaternion(2,0,0,0),0).toArray());
        Vector vector=v(1.5,-2,3.25);
        assertArrayEquals(vector.toArray(),Quaternions.rotateBodyToWorld(IDENTITY,vector,0).toArray());
        assertArrayEquals(vector.toArray(),Quaternions.rotateWorldToBody(IDENTITY,vector,0).toArray());
        double[][] matrix=Quaternions.toRotationMatrix(IDENTITY,0);
        for(int i=0;i<3;i++) { double[] row=new double[3]; row[i]=1; assertArrayEquals(row,matrix[i]); }
    }
    @Test void normNormalizeConjugateAndProduct() {
        Quaternion q=new Quaternion(1,2,3,4), other=new Quaternion(5,6,7,8);
        assertEquals(Math.sqrt(30),Quaternions.norm(q));
        assertEquals(1,Quaternions.norm(Quaternions.normalize(q,0)),TEST_ATOL);
        assertArrayEquals(new double[]{1,-2,-3,-4},Quaternions.conjugate(q).toArray());
        assertArrayEquals(new double[]{1,-2,3,-4},Quaternions.conjugate(new Quaternion(1,2,-3,4)).toArray());
        assertArrayEquals(new double[]{-60,12,30,24},Quaternions.multiply(q,other).toArray());
        assertFalse(Arrays.equals(Quaternions.multiply(q,other).toArray(),Quaternions.multiply(other,q).toArray()));
        assertArrayEquals(q.toArray(),Quaternions.conjugate(Quaternions.conjugate(q)).toArray());
        assertArrayEquals(q.toArray(),Quaternions.multiply(q,IDENTITY).toArray());
        assertArrayEquals(q.toArray(),Quaternions.multiply(IDENTITY,q).toArray());
    }
    @Test void generalInverseAndNoImplicitNormalization() {
        Quaternion q=new Quaternion(2,-1,.5,3), inverse=Quaternions.inverse(q,0);
        close(IDENTITY.toArray(),Quaternions.multiply(q,inverse).toArray());
        close(IDENTITY.toArray(),Quaternions.multiply(inverse,q).toArray());
        assertEquals(4,Quaternions.multiply(new Quaternion(2,0,0,0),new Quaternion(2,0,0,0)).w());
        assertEquals(.5,Quaternions.inverse(new Quaternion(2,0,0,0),0).w());
    }
    static Stream<Arguments> invalidToleranceCases() {
        return Stream.of("normalize","inverse","axis","body","world","matrix","increment")
            .flatMap(op -> Stream.of(-1.,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY)
                .map(atol -> Arguments.of(op,atol)));
    }
    @ParameterizedTest @MethodSource("invalidToleranceCases") void invalidTolerance(String op,double atol) {
        assertTrue(assertThrows(IllegalArgumentException.class,()->invoke(op,IDENTITY,atol)).getMessage().contains("atol"));
    }
    static Stream<String> zeroOperations() { return Stream.of("normalize","inverse","body","world","matrix","increment"); }
    @ParameterizedTest @MethodSource("zeroOperations") void zeroAndNearZero(String op) {
        assertTrue(assertThrows(IllegalArgumentException.class,()->invoke(op,new Quaternion(0,0,0,0),0))
            .getMessage().contains("quaternion norm"));
        assertThrows(IllegalArgumentException.class,()->invoke(op,new Quaternion(1e-9,0,0,0),1e-8));
    }
    private static Object invoke(String op,Quaternion q,double atol) {
        return switch(op) {
            case "normalize" -> Quaternions.normalize(q,atol);
            case "inverse" -> Quaternions.inverse(q,atol);
            case "axis" -> Quaternions.fromAxisAngle(v(1,0,0),0,atol);
            case "body" -> Quaternions.rotateBodyToWorld(q,v(1,2,3),atol);
            case "world" -> Quaternions.rotateWorldToBody(q,v(1,2,3),atol);
            case "matrix" -> Quaternions.toRotationMatrix(q,atol);
            case "increment" -> Quaternions.applyIncrementLeft(q,IDENTITY,atol);
            default -> throw new AssertionError(op);
        };
    }
    @Test void numericalBoundariesAndValidationOrder() {
        assertThrows(IllegalArgumentException.class,()->Quaternions.normalize(new Quaternion(1e-8,0,0,0),1e-8));
        assertEquals(1,Quaternions.normalize(new Quaternion(2e-8,0,0,0),1e-8).w());
        assertThrows(IllegalArgumentException.class,()->Quaternions.norm(new Quaternion(Double.MAX_VALUE,0,0,0)));
        assertEquals(0,Quaternions.norm(new Quaternion(Double.MIN_VALUE,0,0,0)));
        assertThrows(IllegalArgumentException.class,()->Quaternions.inverse(new Quaternion(Double.MIN_VALUE,0,0,0),0));
        assertThrows(IllegalArgumentException.class,()->Quaternions.multiply(new Quaternion(Double.MAX_VALUE,0,0,0),new Quaternion(2,0,0,0)));
        assertThrows(IllegalArgumentException.class,()->Quaternions.fromAxisAngle(v(0,0,0),0,0));
        assertThrows(IllegalArgumentException.class,()->Quaternions.fromAxisAngle(v(1e-9,0,0),0,1e-8));
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternions.fromAxisAngle(v(1,0),0,0)).getMessage().contains("shape"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternions.fromAxisAngle(v(0,0),0,0)).getMessage().contains("magnitude"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternions.fromAxisAngle(v(0,0),Double.NaN,-1)).getMessage().contains("angle_rad"));
        assertTrue(assertThrows(IllegalArgumentException.class,()->Quaternions.rotateBodyToWorld(new Quaternion(0,0,0,0),v(1,2),0))
            .getMessage().contains("quaternion norm"));
        assertThrows(IllegalArgumentException.class,()->Quaternions.rotateBodyToWorld(IDENTITY,v(1,2),0));
        assertThrows(IllegalArgumentException.class,()->Quaternions.rotateWorldToBody(IDENTITY,v(1,2,3,4),0));
    }
    static Stream<Arguments> quarterTurns() {
        return Stream.of(Arguments.of(v(1,0,0),v(0,1,0),v(0,0,1)),Arguments.of(v(1,0,0),v(0,0,1),v(0,-1,0)),
            Arguments.of(v(0,1,0),v(0,0,1),v(1,0,0)),Arguments.of(v(0,1,0),v(1,0,0),v(0,0,-1)),
            Arguments.of(v(0,0,1),v(1,0,0),v(0,1,0)),Arguments.of(v(0,0,1),v(0,1,0),v(-1,0,0)));
    }
    @ParameterizedTest @MethodSource("quarterTurns") void rightHandQuarterTurns(Vector axis,Vector source,Vector expected) {
        Quaternion q=Quaternions.fromAxisAngle(axis,Math.PI/2,0);
        close(expected.toArray(),Quaternions.rotateBodyToWorld(q,source,0).toArray());
    }
    @Test void axisScaleAndRadianFixtures() {
        double expected=Math.sqrt(.5);
        close(new double[]{expected,expected,0,0},Quaternions.fromAxisAngle(v(2,0,0),Math.PI/2,0).toArray());
        close(new double[]{0,-1,0},Quaternions.rotateBodyToWorld(Quaternions.fromAxisAngle(v(0,0,1),-Math.PI/2,TEST_ATOL),v(1,0,0),TEST_ATOL).toArray());
        close(new double[]{-1,0,0},Quaternions.rotateBodyToWorld(Quaternions.fromAxisAngle(v(0,0,1),Math.PI,TEST_ATOL),v(1,0,0),TEST_ATOL).toArray());
    }
    static Stream<Arguments> roundTrips() {
        return Stream.of(Arguments.of(v(1,-2,.5),1.234,v(3.5,-1.25,8)),
            Arguments.of(v(1,-2,.5),.73,v(2.5,-1.25,4)),
            Arguments.of(v(1,2,3),.25,v(4,-1,2)),
            Arguments.of(v(-2,.5,1),-Math.PI/2,v(.25,3.5,-2)),
            Arguments.of(v(0,0,5),Math.PI,v(1,2,3)));
    }
    @ParameterizedTest @MethodSource("roundTrips") void nontrivialRoundTripAndNorm(Vector axis,double angle,Vector source) {
        Quaternion q=Quaternions.fromAxisAngle(axis,angle,TEST_ATOL);
        Vector world=Quaternions.rotateBodyToWorld(q,source,TEST_ATOL);
        close(source.toArray(),Quaternions.rotateWorldToBody(q,world,TEST_ATOL).toArray());
        assertEquals(Vectors.magnitude(source),Vectors.magnitude(world),TEST_ATOL);
    }
    @Test void signAndNonunitOrientationEquivalence() {
        Quaternion q=Quaternions.fromAxisAngle(v(1,-1,2),.63,0);
        Quaternion negative=new Quaternion(-q.w(),-q.x(),-q.y(),-q.z());
        Quaternion scaled=new Quaternion(7.5*q.w(),7.5*q.x(),7.5*q.y(),7.5*q.z());
        Vector probe=v(2,3,-4);
        close(Quaternions.rotateBodyToWorld(q,probe,0).toArray(),Quaternions.rotateBodyToWorld(negative,probe,0).toArray());
        close(Quaternions.rotateBodyToWorld(q,probe,0).toArray(),Quaternions.rotateBodyToWorld(scaled,probe,0).toArray());
    }
    @Test void matrixParityAndProperRotation() {
        Quaternion q=Quaternions.fromAxisAngle(v(1,4,-2),1.1,0);
        Vector probe=v(2.5,-3,.75); double[][] matrix=Quaternions.toRotationMatrix(q,0);
        double[] product=new double[3];
        for(int i=0;i<3;i++) { for(int j=0;j<3;j++) { product[i]+=matrix[i][j]*probe.get(j); } }
        close(Quaternions.rotateBodyToWorld(q,probe,0).toArray(),product);
        for(int i=0;i<3;i++) { for(int j=0;j<3;j++) {
            double value=0; for(int k=0;k<3;k++) { value+=matrix[k][i]*matrix[k][j]; }
            assertEquals(i==j?1.:0.,value,TEST_ATOL);
        } }
        double determinant=matrix[0][0]*(matrix[1][1]*matrix[2][2]-matrix[1][2]*matrix[2][1])
            -matrix[0][1]*(matrix[1][0]*matrix[2][2]-matrix[1][2]*matrix[2][0])
            +matrix[0][2]*(matrix[1][0]*matrix[2][1]-matrix[1][1]*matrix[2][0]);
        assertEquals(1,determinant,TEST_ATOL);
        matrix[0][0]=99;
        assertNotEquals(99,Quaternions.toRotationMatrix(q,0)[0][0]);
        assertNotSame(matrix[0],matrix[1]);
    }
    @Test void leftIncrementOrderAndIndependentResults() {
        Quaternion old=Quaternions.fromAxisAngle(v(1,0,0),Math.PI/2,0);
        Quaternion delta=Quaternions.fromAxisAngle(v(0,0,1),Math.PI/2,0);
        Quaternion actual=Quaternions.applyIncrementLeft(old,delta,0);
        Quaternion expected=Quaternions.normalize(Quaternions.multiply(delta,old),0);
        Quaternion wrong=Quaternions.normalize(Quaternions.multiply(old,delta),0);
        close(expected.toArray(),actual.toArray());
        Vector probe=v(0,1,0);
        assertFalse(Arrays.equals(Quaternions.rotateBodyToWorld(actual,probe,0).toArray(),
            Quaternions.rotateBodyToWorld(wrong,probe,0).toArray()));
        double[] before=old.toArray();
        Quaternions.conjugate(old); Quaternions.inverse(old,0); Quaternions.multiply(old,delta);
        Quaternions.normalize(old,0); Quaternions.rotateBodyToWorld(old,probe,0);
        Quaternions.rotateWorldToBody(old,probe,0); Quaternions.toRotationMatrix(old,0);
        assertArrayEquals(before,old.toArray());
        assertArrayEquals(new double[]{0,1,0},probe.toArray());
        assertNotSame(old,actual);
    }
    @Test void toleranceIsMandatoryInPublicSignatures() {
        for(String name:List.of("normalize","inverse","fromAxisAngle","rotateBodyToWorld","rotateWorldToBody","toRotationMatrix","applyIncrementLeft")) {
            var methods=Arrays.stream(Quaternions.class.getDeclaredMethods()).filter(m->m.getName().equals(name)).toList();
            assertEquals(1,methods.size());
            Class<?>[] parameters=methods.get(0).getParameterTypes();
            assertEquals(double.class,parameters[parameters.length-1]);
        }
    }
}
