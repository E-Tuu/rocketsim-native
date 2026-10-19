package org.roketsim.core.simulation;
import org.roketsim.core.dynamics.LaunchConditions3DOF;
import org.roketsim.core.propulsion.MotorCurveStatistics;
import org.roketsim.core.numerics.FixedStepConfig;

/** Mandatory accepted run inputs; the context timeline is the sole ignition authority. */
public record SimulationRunConfiguration3DOF(LaunchConditions3DOF launchConditions,PhysicsEvaluationContext3DOF physicsContext,
        MotorCurveStatistics motorCurveStatistics,FlightEventDetectionProfile3DOF eventDetectionProfile,
        FixedStepConfig fixedStepConfig,SimulationRunLimits3DOF runLimits,Object terminalEventHandlingModel) {
    public SimulationRunConfiguration3DOF { validateTerminal(terminalEventHandlingModel); }
    static void validateTerminal(Object model) {
        if(model!=TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT)
            throw new SimulationEngineError("UNSUPPORTED_TERMINAL_EVENT_HANDLING_MODEL","terminal_event_handling_model",model);
    }
}
