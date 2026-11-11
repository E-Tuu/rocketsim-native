package org.roketsim.core;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.propulsion.*;

class FlightEventsTest {
    static FlightEventDetectionProfile3DOF profile() { return new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION); }
    static MotorCurveStatistics stats(double end) { return new MotorCurveStatistics(1,1,.5,end,.1,.9,.8); }
    static FlightEventDetectionContext3DOF context(double ignition,double end,double ground) { return new FlightEventDetectionContext3DOF(PhysicsEvaluatorTest.launch(ground),new PropulsionTimeline(ignition),stats(end),profile()); }
    static IntegrationPoint3DOF point(double t,double z,double vz) { return new IntegrationPoint3DOF(t,new TranslationalState3DOF(new double[]{0,0,z},new double[]{0,0,vz})); }
    static List<FlightEventOccurrence3DOF> detect(IntegrationPoint3DOF a,IntegrationPoint3DOF b,FlightEventDetectionContext3DOF c) { return new FlightEventDetector3DOF().detect(a,b,c); }
    @Test void schemaImmutableTerminalAndStateless() {
        assertEquals(3,FlightEventType.values().length); assertEquals("burnout",FlightEventType.BURNOUT.value());
        assertEquals(4,FlightEventDetectionProfile3DOF.class.getRecordComponents().length);
        assertEquals(4,FlightEventOccurrence3DOF.class.getRecordComponents().length); assertEquals(4,FlightEventDetectionContext3DOF.class.getRecordComponents().length);
        assertEquals(0,FlightEventDetector3DOF.class.getDeclaredFields().length);
        for(var type:FlightEventType.values())assertEquals(type==FlightEventType.GROUND,new FlightEventOccurrence3DOF(type,0,.5,point(0,0,0).state()).isTerminal());
    }
    @Test void burnoutAuthorityAndOpenClosedBoundary() {
        var stats=new MotorCurveAnalyzer().analyze(MotorCatalogs.AEROTECH_F50_4T);
        var c=new FlightEventDetectionContext3DOF(PhysicsEvaluatorTest.launch(0),new PropulsionTimeline(.25),stats,profile());
        var event=detect(point(1.6,10,2),point(1.7,20,4),c).get(0);
        assertEquals(1.68,event.timeS()); assertEquals(.8,event.interpolationFraction(),.8e-6);
        assertEquals(18,event.estimatedState().positionWorldM().get(2),18e-7);
        assertEquals(1,detect(point(1.6,10,1),point(1.68,11,1),c).get(0).interpolationFraction());
        assertTrue(detect(point(1.68,11,1),point(1.8,12,1),c).isEmpty());
    }
    @Test void directionalApogeeAndGroundFixtures() {
        var a=detect(point(4,100,3),point(4.2,101,-1),context(0,100,0)).get(0);
        assertEquals(.75,a.interpolationFraction()); assertEquals(4.15,a.timeS(),4.15e-6);
        assertEquals(100.75,a.estimatedState().positionWorldM().get(2)); assertEquals(0,a.estimatedState().velocityWorldMS().get(2));
        var g=detect(point(9,50.3,-2),point(9.2,49.9,-4),context(0,100,50)).get(0);
        assertTrue(g.isTerminal()); assertEquals(.75,g.interpolationFraction(),.75e-6); assertEquals(50,g.estimatedState().positionWorldM().get(2),50e-6);
        for(double[] vz:new double[][]{{0,2},{-1,-2},{0,-1}})assertTrue(detect(point(0,10,vz[0]),point(1,11,vz[1]),context(0,100,0)).isEmpty());
        assertTrue(detect(point(0,50,-1),point(1,49,-1),context(0,100,50)).isEmpty());
        assertEquals(1,detect(point(0,51,-1),point(1,50,-1),context(0,100,50)).get(0).interpolationFraction());
    }
    @Test void genericInterpolationAndTieOrder() {
        var a=new IntegrationPoint3DOF(0,new TranslationalState3DOF(new double[]{0,0,10},new double[]{2,4,6}));
        var b=new IntegrationPoint3DOF(4,new TranslationalState3DOF(new double[]{4,8,14},new double[]{6,8,10}));
        var e=detect(a,b,context(0,1,0)).get(0);
        assertArrayEquals(new double[]{1,2,11},e.estimatedState().positionWorldM().toArray()); assertArrayEquals(new double[]{3,5,7},e.estimatedState().velocityWorldMS().toArray());
        var tied=detect(point(5,51,1),point(5.2,49,-1),context(5,.1,50));
        assertEquals(List.of(FlightEventType.BURNOUT,FlightEventType.APOGEE,FlightEventType.GROUND),tied.stream().map(FlightEventOccurrence3DOF::eventType).toList());
        assertTrue(tied.stream().allMatch(x->x.timeS()==5.1)); assertThrows(UnsupportedOperationException.class,()->tied.clear());
        assertEquals(tied,detect(point(5,51,1),point(5.2,49,-1),context(5,.1,50)));
    }
    @Test void unsupportedPoliciesAndErrorMetadata() {
        String[] names={"burnout_detection_model","apogee_detection_model","ground_reference_model","localization_model"};
        String[] codes={"UNSUPPORTED_BURNOUT_DETECTION_MODEL","UNSUPPORTED_APOGEE_DETECTION_MODEL","UNSUPPORTED_GROUND_REFERENCE_MODEL","UNSUPPORTED_EVENT_LOCALIZATION_MODEL"};
        for(int i=0;i<4;i++) { String field=names[i]; var e=assertThrows(EventDetectionError.class,()->VehicleTestData.replace(profile(),field,"bad")); assertEquals(codes[i],e.errorCode()); assertEquals(field,e.fieldName()); assertEquals("bad",e.value()); }
    }
    @Test void invalidIntervalsBoundariesAndEstimatedOverflow() {
        for(double end:new double[]{0,-1})assertEquals("NON_INCREASING_EVENT_INTERVAL",assertThrows(EventDetectionError.class,()->detect(point(0,1,1),point(end,1,1),context(0,100,0))).errorCode());
        assertEquals("event_interval_duration_s",assertThrows(EventDetectionError.class,()->detect(point(-1e308,1,1),point(1e308,1,1),context(0,100,0))).fieldName());
        assertEquals("NONFINITE_EVENT_BOUNDARY_TIME",assertThrows(EventDetectionError.class,()->detect(point(0,1,1),point(1,1,1),context(1e308,1e308,0))).errorCode());
        var e=assertThrows(EventDetectionError.class,()->detect(point(0,-1e308,1),point(1,1e308,1),context(0,.5,0)));
        assertEquals("INVALID_EVENT_LOCALIZATION",e.errorCode()); assertEquals("estimated_state",e.fieldName());
    }
}
