package org.roketsim.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.propulsion.*;

class SimulationEngineTest {
    static SimulationRunConfiguration3DOF config(int steps,double ignition,double h,double vz) {
        return new SimulationRunConfiguration3DOF(new LaunchConditions3DOF(new double[]{1,0,50},new double[]{0,0,vz},new double[]{0,0,1}),PhysicsEvaluatorTest.context(1200,ignition,new NoWindModel()),new MotorCurveAnalyzer().analyze(MotorCatalogs.AEROTECH_F50_4T),FlightEventsTest.profile(),new FixedStepConfig(h),new SimulationRunLimits3DOF(steps),TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT);
    }
    @Test void explicitSchemasAndLimits() {
        assertEquals(7,SimulationRunConfiguration3DOF.class.getRecordComponents().length); assertEquals(3,SimulationExecution3DOF.class.getRecordComponents().length); assertEquals(0,SimulationEngine3DOF.class.getDeclaredFields().length);
        for(int n:new int[]{0,-1}) { var e=assertThrows(SimulationEngineError.class,()->new SimulationRunLimits3DOF(n)); assertEquals("INVALID_MAXIMUM_STEPS",e.errorCode()); assertEquals("maximum_steps",e.fieldName()); assertEquals(n,e.value()); }
        assertEquals("UNSUPPORTED_TERMINAL_EVENT_HANDLING_MODEL",assertThrows(SimulationEngineError.class,()->VehicleTestData.replace(config(1,2.5,.01,0),"terminal_event_handling_model","reject_interior_candidate_accept_endpoint")).errorCode());
    }
    @Test void realInitialAndMaximumStepsAndFreshRuns() {
        var engine=new SimulationEngine3DOF();
        for(int n=1;n<=3;n++) {
            var c=config(n,2.5,.01,0); var a=engine.run(c); var b=engine.run(c);
            assertEquals(n,a.stepsPerformed()); assertEquals(n+1,a.recordedData().samples().size()); assertEquals(SimulationTerminationReason.MAXIMUM_STEPS_REACHED,a.terminationReason()); assertEquals(2.5,a.recordedData().samples().get(0).point().timeS());
            assertEquals(a,b); assertNotSame(a.recordedData(),b.recordedData());
            for(var sample:a.recordedData().samples()) assertEquals(new PhysicsEvaluator3DOF().evaluate(sample.point().timeS(),sample.point().state(),c.launchConditions(),c.physicsContext()),sample.physics());
        }
    }
    @Test void endpointIsNotK4AndFixedStepIsUnclipped() {
        var c=config(1,2.5,.1,0); var x=new SimulationEngine3DOF().run(c); var first=x.recordedData().samples().get(0); var last=x.recordedData().samples().get(1);
        var stages=new java.util.ArrayList<TranslationalState3DOF>();
        var endpoint=new ClassicalRK4Integrator3DOF().step(first.point(),c.fixedStepConfig(),(t,s)->{ stages.add(s); return new PhysicsEvaluator3DOF().evaluate(t,s,c.launchConditions(),c.physicsContext()).dynamics().derivative(); });
        assertEquals(endpoint,last.point()); assertNotEquals(stages.get(3),last.point().state()); assertEquals(2.5+.1,last.point().timeS());
    }
    @Test void launchPlaneZeroToNegativeIsNotRepaired() {
        var x=new SimulationEngine3DOF().run(config(1,0,.001,-1));
        assertEquals(SimulationTerminationReason.MAXIMUM_STEPS_REACHED,x.terminationReason()); assertTrue(x.recordedData().events().isEmpty()); assertTrue(x.recordedData().samples().get(1).point().state().positionWorldM().get(2)<50);
    }
    @Test void upstreamAtmosphereErrorNotWrapped() {
        var c=config(1,0,.01,0); var invalid=(SimulationRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.launch_environment_altitude_m",90000.);
        assertThrows(AtmosphereDomainError.class,()->new SimulationEngine3DOF().run(invalid));
    }
}
