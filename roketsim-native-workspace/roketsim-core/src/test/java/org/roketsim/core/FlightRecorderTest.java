package org.roketsim.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.simulation.*;

class FlightRecorderTest {
    static PhysicsEvaluationResult3DOF physics() { return PhysicsEvaluatorTest.evaluate(PhysicsEvaluatorTest.context(1200,0,new org.roketsim.core.environment.NoWindModel()),0,50,0,0,0); }
    static FlightEventOccurrence3DOF event(FlightEventType type,double time) { return new FlightEventOccurrence3DOF(type,time,.5,FlightEventsTest.point(time,0,0).state()); }
    @Test void schemasAndIdentity() {
        var p=FlightEventsTest.point(0,0,0); var physics=physics(); var s=new RecordedFlightSample3DOF(p,physics);
        assertSame(p,s.point()); assertSame(physics,s.physics()); assertEquals(2,s.getClass().getRecordComponents().length);
        assertEquals(2,RecordedFlightData3DOF.class.getRecordComponents().length); assertEquals(2,FlightRecorder3DOF.class.getDeclaredFields().length);
    }
    @Test void sampleChronologyAtomicFailure() {
        var r=new FlightRecorder3DOF(); var p=physics();
        for(double t:new double[]{-2,0,.01,.02})r.recordSample(FlightEventsTest.point(t,0,0),p);
        var before=r.snapshot();
        for(double t:new double[]{.02,.005}) { var e=assertThrows(FlightRecordingError.class,()->r.recordSample(FlightEventsTest.point(t,0,0),p)); assertEquals("NON_INCREASING_SAMPLE_TIME",e.errorCode()); assertEquals("point.time_s",e.fieldName()); assertEquals(t,e.value()); assertEquals(before,r.snapshot()); }
    }
    @Test void suppliedEqualTimeAndDuplicateEventsAreNotSorted() {
        var r=new FlightRecorder3DOF(); r.recordEvents(List.of());
        var supplied=List.of(event(FlightEventType.GROUND,4),event(FlightEventType.BURNOUT,4),event(FlightEventType.BURNOUT,4),event(FlightEventType.APOGEE,4));
        r.recordEvents(supplied); assertEquals(supplied,r.snapshot().events()); assertSame(supplied.get(0),r.snapshot().events().get(0));
    }
    @Test void batchAtomicAgainstExistingAndInternalTimes() {
        var r=new FlightRecorder3DOF(); var first=event(FlightEventType.BURNOUT,1.4); r.recordEvents(List.of(first));
        for(var batch:List.of(List.of(event(FlightEventType.APOGEE,1.3)),List.of(event(FlightEventType.APOGEE,4),event(FlightEventType.GROUND,3.5)))) {
            var e=assertThrows(FlightRecordingError.class,()->r.recordEvents(batch)); assertEquals("NON_MONOTONIC_EVENT_TIME",e.errorCode()); assertEquals("event.time_s",e.fieldName()); assertEquals(List.of(first),r.snapshot().events());
        }
    }
    @Test void snapshotIsolationAndNoSeal() {
        var r=new FlightRecorder3DOF(); var empty=r.snapshot(); var physics=physics();
        r.recordSample(FlightEventsTest.point(1,0,0),physics); var first=r.snapshot(); r.recordSample(FlightEventsTest.point(1.1,0,0),physics);
        r.recordEvents(List.of(event(FlightEventType.APOGEE,1.05))); var last=r.snapshot();
        assertTrue(empty.samples().isEmpty()); assertEquals(1,first.samples().size()); assertEquals(2,last.samples().size()); assertNotSame(first,last);
        assertThrows(UnsupportedOperationException.class,()->last.samples().clear()); assertThrows(UnsupportedOperationException.class,()->last.events().clear());
        var caller=new ArrayList<>(last.samples()); var data=new RecordedFlightData3DOF(caller,List.of()); caller.clear(); assertEquals(2,data.samples().size());
        assertSame(physics,last.samples().get(0).physics()); assertNotSame(last.samples().get(0).point().state(),last.events().get(0).estimatedState());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    @Test void nullAndRawElementTypesAreRejectedBeforeMutation() {
        var r=new FlightRecorder3DOF(); assertThrows(NullPointerException.class,()->r.recordSample(FlightEventsTest.point(0,0,0),null)); assertTrue(r.snapshot().samples().isEmpty());
        assertThrows(NullPointerException.class,()->new RecordedFlightData3DOF(null,List.of()));
        assertThrows(ClassCastException.class,()->new RecordedFlightData3DOF((List)List.of("not a sample"),List.of()));
        assertThrows(ClassCastException.class,()->new RecordedFlightData3DOF(List.of(),(List)List.of("not an event")));
        var before=r.snapshot();
        assertThrows(ClassCastException.class,()->r.recordEvents((List)List.of(event(FlightEventType.BURNOUT,1),"not an event")));
        assertEquals(before,r.snapshot());
    }
}
