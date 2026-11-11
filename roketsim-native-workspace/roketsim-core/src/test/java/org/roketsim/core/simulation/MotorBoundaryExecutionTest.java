package org.roketsim.core.simulation;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.SourceMotorRuntimeTest;
import org.roketsim.core.numerics.NumericalIntegrationError;

class MotorBoundaryExecutionTest {
    static MotorTimeBoundarySchedule3DOF schedule() {
        var c=SourceMotorRuntimeTest.testOnlyTeknofestRun("L1256",false,Optional.empty()).physicsContext();
        return MotorTimeBoundarySchedule3DOF.fromRuntimeContext(new RuntimePhysicsEvaluationContext3DOF(
            c.resolvedGeometry(),c.aerodynamicSurfaces(),c.structuralMassProperties(),c.motorInstallation(),
            c.motorPropertyModelProfile(),c.basicDragModelProfile(),c.translationalDynamicsModelProfile(),
            c.environmentPositionMappingModel(),c.launchEnvironmentAltitudeM(),new PropulsionTimeline(.37),
            c.atmosphereModel(),c.airPropertiesCalculator(),c.gravityModel(),c.windModel()));
    }
    @Test void unrepresentableInteriorBoundaryStillRejectsWithoutRoundingRepair() {
        var s=schedule();
        // Existing NAT-026.1 standalone arithmetic limitation, not a pre-ignition flight.
        assertTrue(-1+(.37-(-1))>.37);
        var legacy=assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(-1,2));
        assertTrue(legacy.getMessage().contains("endpoint_time_s"));
        var execution=assertThrowsExactly(NumericalIntegrationError.class,()->s.effectiveStepSizeForExecutionS(-1,2));
        assertEquals("MOTOR_BOUNDARY_UNREPRESENTABLE_ENDPOINT",execution.errorCode());assertEquals("endpoint_time_s",execution.fieldName());
    }
    @Test void derivedOverflowIsNumericalButRawInputValidationRemainsUnchanged() {
        var s=schedule();
        assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(Double.MAX_VALUE,Double.MAX_VALUE));
        assertEquals("MOTOR_BOUNDARY_NONFINITE_TIME",assertThrowsExactly(NumericalIntegrationError.class,
            ()->s.effectiveStepSizeForExecutionS(Double.MAX_VALUE,Double.MAX_VALUE)).errorCode());
        assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeForExecutionS(Double.NaN,.02));
        assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeForExecutionS(0,0));
    }
}
