package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.roketsim.core.aerodynamics.AerodynamicEvaluationError;
import org.roketsim.core.aerodynamics.RecoveryDragEvaluator3DOF;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;
import static org.junit.jupiter.api.Assertions.*;

class RecoveryDragTest {
    private final RecoveryDragEvaluator3DOF evaluator=new RecoveryDragEvaluator3DOF();
    private static Vector v(double... components) { return Vector.of(components); }
    private static void error(String code,String field,Object value,Runnable action) {
        var e=assertThrows(AerodynamicEvaluationError.class,action::run);
        assertEquals(code,e.errorCode());assertEquals(field,e.fieldName());assertEquals(value,e.value());
    }
    @Test void independentThreeFourFiveFixtureHasExactComponents() {
        assertEquals(v(-45,-60,0),evaluator.evaluate(2,v(3,4,0),3));
    }
    @Test void reversedFlowReversesForce() {
        assertEquals(v(45,60,0),evaluator.evaluate(2,v(-3,-4,0),3));
    }
    @Test void doublingVelocityQuadruplesForce() {
        assertEquals(v(-180,-240,0),evaluator.evaluate(2,v(6,8,0),3));
    }
    @Test void densityAndEffectiveAreaScaleLinearly() {
        assertEquals(v(-90,-120,0),evaluator.evaluate(4,v(3,4,0),3));
        assertEquals(v(-90,-120,0),evaluator.evaluate(2,v(3,4,0),6));
    }
    @Test void exactZeroFlowReturnsExactZeroEvenWithLargeValidScalars() {
        assertArrayEquals(new double[]{0,0,0},evaluator.evaluate(Double.MAX_VALUE,v(0,0,0),Double.MAX_VALUE).toArray());
    }
    @Test void exactZeroDensityReturnsExactZeroAfterValidationWithoutNormOverflow() {
        assertArrayEquals(new double[]{0,0,0},evaluator.evaluate(0,v(Double.MAX_VALUE,0,0),1).toArray());
    }
    @Test void axialAndTrueThreeDimensionalSignedFixturesAreExact() {
        assertEquals(v(0,0,48),evaluator.evaluate(2,v(0,0,-4),3));
        // |(-2,3,-6)|=7, scale=-21: independent integer expectation, not production-derived.
        assertEquals(v(42,-63,126),evaluator.evaluate(2,v(-2,3,-6),3));
    }
    @Test void suitableNonzeroFlowAlwaysDissipatesRelativeMotion() {
        for(var flow:new Vector[]{v(3,4,0),v(-2,3,-6),v(0,0,-4),v(0,-5,0)}) {
            var force=evaluator.evaluate(2,flow,3);
            assertTrue(Vectors.dot(force,flow)<0);
        }
    }
    @Test void invalidDensityHasStructuredMetadata() {
        for(double density:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            error("INVALID_RECOVERY_DENSITY","air_density_kg_m3",density,()->evaluator.evaluate(density,v(1,0,0),1));
    }
    @Test void nullAndWrongSizeFlowHaveStructuredMetadata() {
        error("INVALID_RECOVERY_RELATIVE_FLOW","relative_velocity_world_m_s",null,()->evaluator.evaluate(1,null,1));
        for(var flow:new Vector[]{v(),v(1,2),v(1,2,3,4)})
            error("INVALID_RECOVERY_RELATIVE_FLOW","relative_velocity_world_m_s",flow,()->evaluator.evaluate(1,flow,1));
    }
    @Test void nonfiniteRawFlowIsRejectedByExistingVectorAuthority() {
        // Public Vector construction rejects these before they can reach the typed evaluator.
        for(int i=0;i<3;i++) for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            double[] raw={1,2,3};raw[i]=invalid;
            assertThrows(IllegalArgumentException.class,()->evaluator.evaluate(1,Vector.of(raw),1));
        }
    }
    @Test void invalidAreaHasStructuredMetadata() {
        for(double area:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            error("INVALID_RECOVERY_DRAG_AREA","effective_drag_area_m2",area,()->evaluator.evaluate(1,v(1,0,0),area));
    }
    @Test void validationPrecedencePrecedesZeroShortcutsAndDerivedArithmetic() {
        error("INVALID_RECOVERY_DENSITY","air_density_kg_m3",-1.,()->evaluator.evaluate(-1,null,0));
        error("INVALID_RECOVERY_RELATIVE_FLOW","relative_velocity_world_m_s",null,()->evaluator.evaluate(0,null,0));
        error("INVALID_RECOVERY_DRAG_AREA","effective_drag_area_m2",0.,()->evaluator.evaluate(0,v(0,0,0),0));
        error("INVALID_RECOVERY_DRAG_AREA","effective_drag_area_m2",0.,()->evaluator.evaluate(1,v(Double.MAX_VALUE,0,0),0));
    }
    @Test void derivedNormScaleAndComponentOverflowAreRejected() {
        var overflowingNorm=v(Double.MAX_VALUE,0,0);
        error("NONFINITE_RECOVERY_FORCE","relative_velocity_world_m_s",overflowingNorm,
            ()->evaluator.evaluate(1,overflowingNorm,1));
        error("NONFINITE_RECOVERY_FORCE","recovery_force_scale",Double.NEGATIVE_INFINITY,
            ()->evaluator.evaluate(Double.MAX_VALUE,v(1,0,0),4));
        error("NONFINITE_RECOVERY_FORCE","recovery_force_world_N[0]",Double.NEGATIVE_INFINITY,
            ()->evaluator.evaluate(Double.MAX_VALUE,v(2,0,0),1));
    }
    @Test void smallPowerOfTwoFixtureRetainsExactNonzeroForce() {
        double speed=Math.scalb(1.,-200);
        assertEquals(v(-Math.scalb(1.,-400),0,0),evaluator.evaluate(2,v(speed,0,0),1));
    }
    @Test void underflowFollowsFrozenNormAndMultiplicationOrderWithoutThreshold() {
        assertEquals(v(0,0,0),evaluator.evaluate(2,v(Double.MIN_VALUE,0,0),1));
        // (-.5*MIN_VALUE) rounds to -0 before later multiplication; no regrouping to rescue it.
        assertEquals(v(0,0,0),evaluator.evaluate(Double.MIN_VALUE,v(1,0,0),Double.MAX_VALUE));
    }
    @Test void inputStorageAndRepeatedResultsRemainIndependent() {
        double[] raw={3,4,0};var flow=Vector.of(raw);var before=flow.toArray();
        var first=evaluator.evaluate(2,flow,3);raw[0]=100;var exported=flow.toArray();exported[1]=100;
        assertArrayEquals(before,flow.toArray());assertEquals(v(-45,-60,0),first);
        assertEquals(first,evaluator.evaluate(2,flow,3));
        assertEquals(first,new RecoveryDragEvaluator3DOF().evaluate(2,flow,3));
        assertEquals(0,RecoveryDragEvaluator3DOF.class.getDeclaredFields().length);
    }
}
