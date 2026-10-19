package org.roketsim.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.simulation.*;

class SimulationResultTest {
    static SimulationExecution3DOF execution(double[] times,List<FlightEventOccurrence3DOF> events,SimulationTerminationReason reason) {
        var p=FlightRecorderTest.physics(); var samples=new ArrayList<RecordedFlightSample3DOF>();
        for(double t:times)samples.add(new RecordedFlightSample3DOF(FlightEventsTest.point(t,0,0),p));
        return new SimulationExecution3DOF(new RecordedFlightData3DOF(samples,events),reason,2);
    }
    @Test void soleAuthorityAndExactForwarding() {
        var x=execution(new double[]{2.5,2.6,2.7},List.of(),SimulationTerminationReason.MAXIMUM_STEPS_REACHED); var r=new SimulationResult3DOF(x);
        assertEquals(1,r.getClass().getRecordComponents().length); assertSame(x,r.execution()); assertSame(x.recordedData(),r.recordedData()); assertSame(x.recordedData().samples(),r.samples()); assertSame(x.recordedData().events(),r.events());
        assertSame(r.samples().get(0),r.initialSample()); assertSame(r.samples().get(2),r.lastAcceptedSample()); assertEquals(2,r.stepsPerformed()); assertEquals(2.7,r.terminationTimeS());
        var stepLimit=new SimulationResult3DOF(execution(new double[]{2.5,6},List.of(),SimulationTerminationReason.MAXIMUM_STEPS_REACHED));
        assertNull(stepLimit.terminalEvent());assertEquals(6,stepLimit.terminationTimeS());
    }
    @Test void emptyMissingMultipleUnexpectedAndNegativeIntegrity() {
        var ground=FlightRecorderTest.event(FlightEventType.GROUND,2);
        var cases=List.of(execution(new double[]{},List.of(),SimulationTerminationReason.MAXIMUM_STEPS_REACHED),execution(new double[]{1},List.of(),SimulationTerminationReason.TERMINAL_EVENT),execution(new double[]{1},List.of(ground,ground),SimulationTerminationReason.TERMINAL_EVENT),execution(new double[]{1},List.of(ground),SimulationTerminationReason.MAXIMUM_STEPS_REACHED),execution(new double[]{2.5},List.of(ground),SimulationTerminationReason.TERMINAL_EVENT));
        String[] codes={"EMPTY_RECORDED_SAMPLES","TERMINATION_EVENT_MISSING","MULTIPLE_TERMINAL_EVENTS","UNEXPECTED_TERMINAL_EVENT","INVALID_TERMINATION_TIME"};
        for(int i=0;i<cases.size();i++) { var x=cases.get(i); var e=assertThrows(SimulationResultError.class,()->new SimulationResult3DOF(x)); assertEquals(codes[i],e.errorCode()); assertFalse(e.fieldName().isEmpty()); }
    }
    @Test void genericAndSingularQueriesPreserveAllOrRejectAmbiguity() {
        var b=FlightRecorderTest.event(FlightEventType.BURNOUT,1.4); var a=FlightRecorderTest.event(FlightEventType.APOGEE,4.1); var a2=FlightRecorderTest.event(FlightEventType.APOGEE,4.2);
        var r=new SimulationResult3DOF(execution(new double[]{5},List.of(b,a,a2),SimulationTerminationReason.MAXIMUM_STEPS_REACHED));
        assertSame(b,r.burnoutEvent()); assertNull(r.groundEvent()); assertNull(r.terminalEvent()); assertEquals(List.of(a,a2),r.eventsOfType(FlightEventType.APOGEE));
        assertEquals("AMBIGUOUS_EVENT_HISTORY",assertThrows(SimulationResultError.class,r::apogeeEvent).errorCode()); assertThrows(NullPointerException.class,()->r.eventsOfType(null));
        var ground=FlightRecorderTest.event(FlightEventType.GROUND,8);
        var unique=new SimulationResult3DOF(execution(new double[]{5},List.of(a,ground),SimulationTerminationReason.TERMINAL_EVENT));
        assertSame(a,unique.apogeeEvent());assertSame(ground,unique.groundEvent());assertSame(ground,unique.terminalEvent());
    }
    @Test void interiorAndExactEndpointDistinction() {
        for(double[] fixture:new double[][]{{9.1,9.163},{9.2,9.2}}) {
            var event=FlightRecorderTest.event(FlightEventType.GROUND,fixture[1]); var r=new SimulationResult3DOF(execution(new double[]{9,fixture[0]},List.of(event),SimulationTerminationReason.TERMINAL_EVENT));
            assertEquals(fixture[0],r.lastAcceptedSample().point().timeS()); assertEquals(fixture[1],r.terminationTimeS()); assertSame(event,r.terminalEvent()); assertEquals(2,r.samples().size());
        }
    }
    @Test void nonzeroStartDurations() {
        var a=new SimulationResult3DOF(execution(new double[]{2.5,12.75},List.of(),SimulationTerminationReason.MAXIMUM_STEPS_REACHED)); assertEquals(10.25,a.simulationDurationS());
        var b=new SimulationResult3DOF(execution(new double[]{2.5,10},List.of(FlightRecorderTest.event(FlightEventType.GROUND,10.08)),SimulationTerminationReason.TERMINAL_EVENT)); assertEquals(7.58,b.simulationDurationS(),7.58e-6);
    }
    @Test void prohibitedAliasesAndOperationsAbsent() {
        var methods=Arrays.stream(SimulationResult3DOF.class.getMethods()).map(java.lang.reflect.Method::getName).toList();
        for(String name:List.of("finalState","finalSample","flightTimeS","apogeeAltitudeM","maximumAltitudeM","impactSpeed","maximumMach","maximumQ","run","evaluate","step","plot","exportCsv"))assertFalse(methods.contains(name));
    }
}
