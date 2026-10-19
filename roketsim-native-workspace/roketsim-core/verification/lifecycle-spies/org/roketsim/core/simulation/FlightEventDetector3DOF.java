package org.roketsim.core.simulation;
import java.util.List;
import org.roketsim.core.numerics.IntegrationPoint3DOF;
import org.roketsim.core.dynamics.TranslationalState3DOF;

/** ISOLATED TEST CLASSPATH ONLY: exact source DetectorSpy batches for lifecycle policy tests. */
public final class FlightEventDetector3DOF {
    public static int calls;
    public static String scenario="none";
    public FlightEventDetector3DOF() { calls=0; }
    private static FlightEventOccurrence3DOF event(FlightEventType type,double time,double alpha) {
        return new FlightEventOccurrence3DOF(type,time,alpha,new TranslationalState3DOF(new double[]{99,0,50},new double[]{0,0,-1}));
    }
    public List<FlightEventOccurrence3DOF> detect(IntegrationPoint3DOF a,IntegrationPoint3DOF b,FlightEventDetectionContext3DOF c) {
        calls++;
        return switch(scenario) {
            case "interior1" -> List.of(event(FlightEventType.GROUND,2.56,.6));
            case "endpoint1" -> List.of(event(FlightEventType.GROUND,2.6,1));
            case "interior3" -> calls==3?List.of(event(FlightEventType.GROUND,2.76,.6)):List.of();
            case "filter" -> List.of(event(FlightEventType.APOGEE,8.10,.2),event(FlightEventType.GROUND,8.15,.6),event(FlightEventType.BURNOUT,8.18,.9));
            case "tie" -> List.of(event(FlightEventType.BURNOUT,5,.6),event(FlightEventType.APOGEE,5,.6),event(FlightEventType.GROUND,5,.6));
            case "detector-error" -> throw PhysicsEvaluator3DOF.SENTINEL;
            default -> List.of();
        };
    }
}
