package org.roketsim.core.simulation;
/** Explicit typed integer candidate-step guard; no default or time guard. */
public record SimulationRunLimits3DOF(int maximumSteps) {
    public SimulationRunLimits3DOF { if(maximumSteps<=0)throw new SimulationEngineError("INVALID_MAXIMUM_STEPS","maximum_steps",maximumSteps); }
}
