package org.roketsim.core;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.roketsim.core.aerodynamics.*;
import static org.junit.jupiter.api.Assertions.*;

/** J006-P01 regression: exact metadata selector semantics, not numerical text parsing. */
class AerodynamicProfileTest {
    static Stream<Arguments> selectors() {
        return Stream.of(
            Arguments.of("drag","boundary_layer_model",BoundaryLayerModel.FULLY_TURBULENT,"fully_turbulent"),
            Arguments.of("drag","reynolds_length_policy",ReynoldsLengthPolicy.AERODYNAMIC_LENGTH,"aerodynamic_length"),
            Arguments.of("drag","low_reynolds_continuation",LowReynoldsContinuation.MINIMUM_EVALUATION_REYNOLDS,"minimum_evaluation_reynolds"),
            Arguments.of("drag","nose_pressure_model",NosePressureModel.TD13_CONICAL_SUBSONIC_TO_SONIC,"td13_conical_subsonic_to_sonic"),
            Arguments.of("drag","fin_edge_model",FinEdgeModel.TD13_SQUARE,"td13_square"),
            Arguments.of("drag","base_drag_model",BaseDragModel.TD13_SUBSONIC_TO_SONIC,"td13_subsonic_to_sonic"),
            Arguments.of("drag","plume_treatment",PlumeTreatment.IGNORED,"ignored"),
            Arguments.of("static","normal_force_model",StaticNormalForceModel.EXTENDED_BARROWMAN_SUBSONIC,"extended_barrowman_subsonic"),
            Arguments.of("static","fin_center_of_pressure_model",FinCenterOfPressureModel.EXTENDED_BARROWMAN_MACH_DEPENDENT,"extended_barrowman_mach_dependent"),
            Arguments.of("static","body_lift_treatment",BodyLiftTreatment.LINEARIZED_ZERO_AOA,"linearized_zero_aoa"));
    }
    static Object profile(String operation) {
        return operation.equals("drag")?BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE:StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE;
    }
    static Object evaluate(String operation,Object profile) {
        return operation.equals("drag")?new BasicDragEvaluator().evaluate(AerodynamicsTest.geometry(),AerodynamicsTest.surfaces(0,0,0),AerodynamicsTest.flight(.3),AerodynamicsTest.air(),profile)
            :new StaticAerodynamicEvaluator().evaluate(AerodynamicsTest.geometry(),AerodynamicsTest.flight(.3),profile);
    }
    @ParameterizedTest @MethodSource("selectors")
    void exactCanonicalStringPreservesCompleteResultAndMetadata(String op,String field,Object typed,String canonical) {
        Object original=profile(op);
        Object selected=VehicleTestData.replace(original,field,canonical);
        Object result=evaluate(op,selected);
        assertEquals(VehicleTestData.replace(evaluate(op,original),"model_profile",selected),result);
        assertEquals(evaluate(op,original),evaluate(op,VehicleTestData.replace(original,field,typed)));
        Object returned=op.equals("drag")?((BasicDragResult)result).modelProfile():((StaticAerodynamicProperties)result).modelProfile();
        assertSame(selected,returned);
        assertNotEquals(original,selected); // Global record equality was deliberately NOT changed.
    }
    @ParameterizedTest @MethodSource("selectors")
    void noncanonicalValuesRemainStructuredErrors(String op,String field,Object typed,String canonical) {
        for(Object value:new Object[]{canonical.toUpperCase()," "+canonical,canonical+" ","unknown","",null,1,true}) {
            Object supplied=VehicleTestData.replace(profile(op),field,value);
            var error=assertThrows(AerodynamicEvaluationError.class,()->evaluate(op,supplied));
            assertEquals(op.equals("drag")?"UNSUPPORTED_BASIC_DRAG_PROFILE":"UNSUPPORTED_STATIC_STABILITY_PROFILE",error.errorCode());
            assertEquals("model_profile",error.fieldName()); assertSame(supplied,error.value());
        }
    }
    @Test void independentProfileClassesAndExactNumericalPolicies() {
        assertThrows(AerodynamicEvaluationError.class,()->evaluate("drag",profile("static")));
        assertThrows(AerodynamicEvaluationError.class,()->evaluate("static",profile("drag")));
        for(double value:new double[]{Math.nextDown(1e4),Math.nextUp(1e4),2e4})
            assertThrows(AerodynamicEvaluationError.class,()->evaluate("drag",VehicleTestData.replace(profile("drag"),"minimum_friction_evaluation_reynolds",value)));
        for(double value:new double[]{Math.nextDown(.8),Math.nextUp(.8),.9})
            assertThrows(AerodynamicEvaluationError.class,()->evaluate("static",VehicleTestData.replace(profile("static"),"maximum_supported_mach_exclusive",value)));
    }
    @Test void allCanonicalStringsCanBeCombinedWithoutNormalization() {
        Object dp=profile("drag"),sp=profile("static");
        for(Arguments row:selectors().toList()) {
            Object[] args=row.get();
            if(args[0].equals("drag")) dp=VehicleTestData.replace(dp,(String)args[1],args[3]);
            else sp=VehicleTestData.replace(sp,(String)args[1],args[3]);
        }
        assertEquals(VehicleTestData.replace(evaluate("drag",profile("drag")),"model_profile",dp),evaluate("drag",dp));
        assertEquals(VehicleTestData.replace(evaluate("static",profile("static")),"model_profile",sp),evaluate("static",sp));
    }
}
