package org.roketsim.core;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.simulation.*;

class FlightPhaseClockTest {
    private final FlightPhaseClockEvaluator3DOF evaluator=new FlightPhaseClockEvaluator3DOF();
    private static MotorCurveStatistics statistics() {
        return new MotorCurveAnalyzer().analyze(MotorCatalogs.AEROTECH_F50_4T);
    }
    private FlightPhaseClockState3DOF evaluate(double time,double ignition,FlightPhase3DOF phase) {
        return evaluator.evaluate(time,phase,new PropulsionTimeline(ignition),statistics());
    }
    private static void invalid(String field,org.junit.jupiter.api.function.Executable action) {
        var error=assertThrows(IllegalArgumentException.class,action);
        assertEquals(IllegalArgumentException.class,error.getClass());
        assertTrue(error.getMessage().contains(field),error.getMessage());
    }
    @Test void exactSchemasAndPublicApi() throws Exception {
        assertArrayEquals(new String[]{"PAD_CONTACT","GUIDED_FLIGHT","FREE_FLIGHT","RECOVERY_DESCENT","LANDED"},
            Arrays.stream(FlightPhase3DOF.values()).map(Enum::name).toArray(String[]::new));
        assertArrayEquals(new String[]{"PRE_IGNITION","CURVE_ACTIVE","POST_BURNOUT"},
            Arrays.stream(PropulsionStatus3DOF.values()).map(Enum::name).toArray(String[]::new));
        var fields=FlightPhaseClockState3DOF.class.getRecordComponents();
        assertArrayEquals(new String[]{"flightPhase","propulsionStatus","motorTimeS","burnoutTimeS"},
            Arrays.stream(fields).map(x->x.getName()).toArray(String[]::new));
        assertArrayEquals(new Class<?>[]{FlightPhase3DOF.class,PropulsionStatus3DOF.class,double.class,double.class},
            Arrays.stream(fields).map(x->x.getType()).toArray(Class<?>[]::new));
        assertTrue(Modifier.isFinal(FlightPhaseClockEvaluator3DOF.class.getModifiers()));
        assertEquals(0,FlightPhaseClockEvaluator3DOF.class.getDeclaredFields().length);
        assertEquals(1,FlightPhaseClockEvaluator3DOF.class.getDeclaredMethods().length);
        assertEquals(FlightPhaseClockState3DOF.class,FlightPhaseClockEvaluator3DOF.class.getMethod("evaluate",
            double.class,FlightPhase3DOF.class,PropulsionTimeline.class,MotorCurveStatistics.class).getReturnType());
        assertArrayEquals(new FlightEventType[]{FlightEventType.BURNOUT,FlightEventType.APOGEE,FlightEventType.GROUND},FlightEventType.values());
    }
    @Test void frozenZeroIgnitionFixture() {
        double end=statistics().curveEndTimeS(); assertEquals(1.43,end);
        double[] times={-.20,0,.50,end,2};
        var statuses=new PropulsionStatus3DOF[]{PropulsionStatus3DOF.PRE_IGNITION,PropulsionStatus3DOF.CURVE_ACTIVE,
            PropulsionStatus3DOF.CURVE_ACTIVE,PropulsionStatus3DOF.POST_BURNOUT,PropulsionStatus3DOF.POST_BURNOUT};
        for(int i=0;i<times.length;i++) assertEquals(new FlightPhaseClockState3DOF(FlightPhase3DOF.FREE_FLIGHT,
            statuses[i],times[i],end),evaluate(times[i],0,FlightPhase3DOF.FREE_FLIGHT));
    }
    @Test void shiftedAndNegativeIgnitionFixtures() {
        for(double ignition:new double[]{5,-2}) {
            double burnout=ignition+statistics().curveEndTimeS();
            double[] times={ignition-.5,ignition,ignition+1,burnout,burnout+1};
            var statuses=new PropulsionStatus3DOF[]{PropulsionStatus3DOF.PRE_IGNITION,PropulsionStatus3DOF.CURVE_ACTIVE,
                PropulsionStatus3DOF.CURVE_ACTIVE,PropulsionStatus3DOF.POST_BURNOUT,PropulsionStatus3DOF.POST_BURNOUT};
            for(int i=0;i<times.length;i++) {
                var result=evaluate(times[i],ignition,FlightPhase3DOF.FREE_FLIGHT);
                assertEquals(statuses[i],result.propulsionStatus());
                assertEquals(times[i]-ignition,result.motorTimeS()); assertEquals(burnout,result.burnoutTimeS());
            }
        }
    }
    @Test void immediateNeighborsHaveNoToleranceBand() {
        for(double ignition:new double[]{0,5,-2}) {
            double end=ignition+statistics().curveEndTimeS();
            assertEquals(PropulsionStatus3DOF.PRE_IGNITION,evaluate(Math.nextDown(ignition),ignition,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
            assertEquals(PropulsionStatus3DOF.CURVE_ACTIVE,evaluate(ignition,ignition,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
            assertEquals(PropulsionStatus3DOF.CURVE_ACTIVE,evaluate(Math.nextUp(ignition),ignition,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
            assertEquals(PropulsionStatus3DOF.CURVE_ACTIVE,evaluate(Math.nextDown(end),ignition,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
            assertEquals(PropulsionStatus3DOF.POST_BURNOUT,evaluate(end,ignition,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
            assertEquals(PropulsionStatus3DOF.POST_BURNOUT,evaluate(Math.nextUp(end),ignition,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
        }
    }
    @Test void allPhasesAndStatusesRemainIndependent() {
        for(double time:new double[]{-1,.5,2}) {
            var reference=evaluate(time,0,FlightPhase3DOF.FREE_FLIGHT);
            for(var phase:FlightPhase3DOF.values()) {
                var result=evaluate(time,0,phase);
                assertSame(phase,result.flightPhase()); assertSame(reference.propulsionStatus(),result.propulsionStatus());
                assertEquals(reference.motorTimeS(),result.motorTimeS()); assertEquals(reference.burnoutTimeS(),result.burnoutTimeS());
            }
        }
    }
    @Test void curveActiveDoesNotMeanPositiveThrustOrEffectiveBurn() {
        assertEquals(0,new MotorThrustCurveEvaluator().evaluate(MotorCatalogs.AEROTECH_F50_4T,0).thrustN());
        assertEquals(PropulsionStatus3DOF.CURVE_ACTIVE,evaluate(0,0,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
        assertTrue(statistics().effectiveBurnEnd5pctS()<1.4);
        assertEquals(PropulsionStatus3DOF.CURVE_ACTIVE,evaluate(1.4,0,FlightPhase3DOF.PAD_CONTACT).propulsionStatus());
        assertEquals(PropulsionStatus3DOF.POST_BURNOUT,evaluate(1.43,0,FlightPhase3DOF.GUIDED_FLIGHT).propulsionStatus());
    }
    @Test void nonfiniteAbsoluteTimeRejectedFirst() {
        for(double time:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            invalid("absolute_time_s",()->evaluator.evaluate(time,null,null,null));
    }
    @Test void nullInputsRejectedInOrder() {
        invalid("flight_phase",()->evaluator.evaluate(0,null,null,null));
        invalid("propulsion_timeline",()->evaluator.evaluate(0,FlightPhase3DOF.PAD_CONTACT,null,null));
        invalid("motor_statistics",()->evaluator.evaluate(0,FlightPhase3DOF.PAD_CONTACT,new PropulsionTimeline(0),null));
    }
    @Test void invalidCurveEndsBeforeDerivedArithmetic() {
        for(double end:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0,-0.0,-1})
            invalid("curve_end_time_s",()->evaluator.evaluate(Double.MAX_VALUE,FlightPhase3DOF.FREE_FLIGHT,
                new PropulsionTimeline(-Double.MAX_VALUE),FlightEventsTest.stats(end)));
    }
    @Test void derivedMotorTimeOverflowRejectedBeforeBurnoutOverflow() {
        for(double ignition:new double[]{-Double.MAX_VALUE,Double.MAX_VALUE})
            invalid("motor_time_s",()->evaluator.evaluate(-ignition,FlightPhase3DOF.FREE_FLIGHT,
                new PropulsionTimeline(ignition),FlightEventsTest.stats(Double.MAX_VALUE)));
    }
    @Test void derivedBurnoutOverflowRejected() {
        invalid("burnout_time_s",()->evaluator.evaluate(Double.MAX_VALUE,FlightPhase3DOF.FREE_FLIGHT,
            new PropulsionTimeline(Double.MAX_VALUE),FlightEventsTest.stats(Double.MAX_VALUE)));
    }
    @Test void collapsedBoundaryRejectedWithoutRepair() {
        for(double ignition:new double[]{Double.MAX_VALUE,-Double.MAX_VALUE,1e16}) {
            assertEquals(ignition,ignition+1);
            invalid("burnout_time_s",()->evaluator.evaluate(ignition,FlightPhase3DOF.FREE_FLIGHT,
                new PropulsionTimeline(ignition),FlightEventsTest.stats(1)));
        }
    }
    @Test void smallestPositiveDistinguishableIntervalAccepted() {
        var result=evaluator.evaluate(0,FlightPhase3DOF.LANDED,new PropulsionTimeline(0),FlightEventsTest.stats(Double.MIN_VALUE));
        assertEquals(Double.MIN_VALUE,result.burnoutTimeS()); assertEquals(PropulsionStatus3DOF.CURVE_ACTIVE,result.propulsionStatus());
        assertEquals(PropulsionStatus3DOF.POST_BURNOUT,evaluator.evaluate(Double.MIN_VALUE,FlightPhase3DOF.LANDED,
            new PropulsionTimeline(0),FlightEventsTest.stats(Double.MIN_VALUE)).propulsionStatus());
    }
    @Test void repeatedEvaluationAndUnrelatedStatisticsDoNotChangeClock() {
        var timeline=new PropulsionTimeline(5); var stats=statistics();
        var expected=evaluator.evaluate(6,FlightPhase3DOF.GUIDED_FLIGHT,timeline,stats);
        var unrelated=new MotorCurveStatistics(Double.NaN,Double.NaN,Double.NaN,stats.curveEndTimeS(),Double.NaN,Double.NaN,Double.NaN);
        for(int i=0;i<3;i++) {
            evaluate(-3,-2,FlightPhase3DOF.RECOVERY_DESCENT);
            assertEquals(expected,evaluator.evaluate(6,FlightPhase3DOF.GUIDED_FLIGHT,timeline,stats));
            assertEquals(expected,new FlightPhaseClockEvaluator3DOF().evaluate(6,FlightPhase3DOF.GUIDED_FLIGHT,timeline,unrelated));
        }
    }
}
