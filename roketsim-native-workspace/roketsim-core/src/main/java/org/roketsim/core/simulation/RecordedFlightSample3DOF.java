package org.roketsim.core.simulation;
import java.util.Objects;
import org.roketsim.core.numerics.IntegrationPoint3DOF;

/** Supplied accepted point and endpoint physics only; caller owns their correspondence. */
public record RecordedFlightSample3DOF(IntegrationPoint3DOF point,PhysicsEvaluationResult3DOF physics) {
    public RecordedFlightSample3DOF { Objects.requireNonNull(point,"point must be IntegrationPoint3DOF"); Objects.requireNonNull(physics,"physics must be PhysicsEvaluationResult3DOF"); }
}
