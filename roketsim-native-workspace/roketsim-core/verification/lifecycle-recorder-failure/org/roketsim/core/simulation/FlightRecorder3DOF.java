package org.roketsim.core.simulation;
import java.util.List;
import org.roketsim.core.numerics.IntegrationPoint3DOF;
/** Isolated equivalent of NAT-020's FailingRecorder; never included in the Core JAR. */
public final class FlightRecorder3DOF {
    public void recordSample(IntegrationPoint3DOF point,PhysicsEvaluationResult3DOF physics) {
        throw PhysicsEvaluator3DOF.SENTINEL;
    }
    public void recordEvents(List<FlightEventOccurrence3DOF> events) { throw new AssertionError("Unreachable"); }
    public RecordedFlightData3DOF snapshot() { throw new AssertionError("Unreachable"); }
}
