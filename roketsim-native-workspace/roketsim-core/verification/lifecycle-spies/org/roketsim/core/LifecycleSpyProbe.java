package org.roketsim.core;
import java.util.LinkedHashMap;
import org.roketsim.core.simulation.*;

/** Runs the UNCHANGED production engine bytecode with explicitly isolated source-equivalent spies. */
public final class LifecycleSpyProbe {
    public static void main(String[] args) throws Exception {
        String scenario=args[0]; int steps=Integer.parseInt(args[1]); int fail=Integer.parseInt(args[2]);
        FlightEventDetector3DOF.scenario=scenario; PhysicsEvaluator3DOF.failCall=fail;
        var output=new LinkedHashMap<String,Object>();
        var config=SimulationEngineTest.config(steps,2.5,.1,0);
        try {
            var execution=new SimulationEngine3DOF().run(config);
            int count=execution.recordedData().samples().size();
            int expected=execution.terminationReason()==SimulationTerminationReason.TERMINAL_EVENT && !scenario.equals("endpoint1")?5*execution.stepsPerformed():1+5*execution.stepsPerformed();
            if(PhysicsEvaluator3DOF.calls!=expected)throw new AssertionError("Wrong physics call count");
            output.put("reason",execution.terminationReason()); output.put("steps",execution.stepsPerformed());
            output.put("samples.size",count);
            for(int i=0;i<count;i++)DynamicsParityProbe.flatten(output,"samples."+i,execution.recordedData().samples().get(i).point());
            DynamicsParityProbe.flatten(output,"events",execution.recordedData().events());
            output.put("k4NotEndpoint",count>1 && PhysicsEvaluator3DOF.states.get(4)!=PhysicsEvaluator3DOF.states.get(5)
                && !PhysicsEvaluator3DOF.states.get(4).equals(PhysicsEvaluator3DOF.states.get(5)));
        } catch(RuntimeException error) {
            if(error!=PhysicsEvaluator3DOF.SENTINEL)throw error;
            output.put("exceptionIdentity",true);
        }
        output.put("physicsCalls",PhysicsEvaluator3DOF.calls); output.put("detectorCalls",FlightEventDetector3DOF.calls);
        var seen=org.roketsim.core.numerics.ClassicalRK4Integrator3DOF.configurations;
        output.put("integratorCalls",seen.size());
        output.put("fixedConfigIdentity",seen.stream().allMatch(value->value==config.fixedStepConfig()));
        PropulsionParityProbe.output("OK",output);
    }
}
