package org.roketsim.core.simulation;
import org.roketsim.core.dynamics.InitialStateBuilder;
import org.roketsim.core.numerics.*;

/** Reusable stateless lifecycle. Every run owns fresh local history and evaluators. */
public final class SimulationEngine3DOF {
    public SimulationExecution3DOF run(SimulationRunConfiguration3DOF configuration) {
        SimulationRunConfiguration3DOF.validateTerminal(configuration.terminalEventHandlingModel());
        var recorder=new FlightRecorder3DOF(); var physicsEvaluator=new PhysicsEvaluator3DOF();
        var integrator=new ClassicalRK4Integrator3DOF(); var detector=new FlightEventDetector3DOF();
        var initialState=new InitialStateBuilder().build(configuration.launchConditions());
        var current=new IntegrationPoint3DOF(configuration.physicsContext().propulsionTimeline().ignitionTimeS(),initialState);
        var initialPhysics=physicsEvaluator.evaluate(current.timeS(),current.state(),configuration.launchConditions(),configuration.physicsContext());
        recorder.recordSample(current,initialPhysics);
        var eventContext=new FlightEventDetectionContext3DOF(configuration.launchConditions(),configuration.physicsContext().propulsionTimeline(),configuration.motorCurveStatistics(),configuration.eventDetectionProfile());
        DerivativeFunction3DOF derivative=(time,state)->physicsEvaluator.evaluate(time,state,configuration.launchConditions(),configuration.physicsContext()).dynamics().derivative();
        int steps=0;
        while(steps<configuration.runLimits().maximumSteps()) {
            var candidate=integrator.step(current,configuration.fixedStepConfig(),derivative); steps++;
            var events=detector.detect(current,candidate,eventContext);
            var terminal=events.stream().filter(FlightEventOccurrence3DOF::isTerminal).findFirst().orElse(null);
            if(terminal!=null) {
                var retained=events.stream().filter(e->e.timeS()<=terminal.timeS()).toList();
                if(terminal.interpolationFraction()<1.0) {
                    recorder.recordEvents(retained);
                    return new SimulationExecution3DOF(recorder.snapshot(),SimulationTerminationReason.TERMINAL_EVENT,steps);
                }
                var endpoint=physicsEvaluator.evaluate(candidate.timeS(),candidate.state(),configuration.launchConditions(),configuration.physicsContext());
                recorder.recordEvents(retained); recorder.recordSample(candidate,endpoint);
                return new SimulationExecution3DOF(recorder.snapshot(),SimulationTerminationReason.TERMINAL_EVENT,steps);
            }
            var endpoint=physicsEvaluator.evaluate(candidate.timeS(),candidate.state(),configuration.launchConditions(),configuration.physicsContext());
            recorder.recordEvents(events); recorder.recordSample(candidate,endpoint); current=candidate;
        }
        return new SimulationExecution3DOF(recorder.snapshot(),SimulationTerminationReason.MAXIMUM_STEPS_REACHED,steps);
    }
}
