package org.roketsim.core.simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.roketsim.core.numerics.IntegrationPoint3DOF;
import org.roketsim.core.dynamics.TranslationalState3DOF;

/** Stateless NAT-018 bracket crossings and linear estimates; no physics or integration. */
public final class FlightEventDetector3DOF {
    public List<FlightEventOccurrence3DOF> detect(IntegrationPoint3DOF startPoint,
            IntegrationPoint3DOF endPoint,FlightEventDetectionContext3DOF context) {
        double start=startPoint.timeS(),end=endPoint.timeS();
        if(end<=start)throw new EventDetectionError("NON_INCREASING_EVENT_INTERVAL","end_point.time_s",end);
        double duration=end-start;
        if(!Double.isFinite(duration))throw new EventDetectionError("INVALID_EVENT_LOCALIZATION","event_interval_duration_s",duration);
        var p=context.modelProfile();
        FlightEventDetectionProfile3DOF.validate(p.burnoutDetectionModel(),p.apogeeDetectionModel(),p.groundReferenceModel(),p.localizationModel());
        var events=new ArrayList<FlightEventOccurrence3DOF>();
        double burnout=context.propulsionTimeline().ignitionTimeS()+context.motorCurveStatistics().curveEndTimeS();
        if(!Double.isFinite(burnout))throw new EventDetectionError("NONFINITE_EVENT_BOUNDARY_TIME","burnout_time_s",burnout);
        if(start<burnout && burnout<=end)events.add(occurrence(FlightEventType.BURNOUT,burnout,(burnout-start)/duration,startPoint,endPoint));
        double vz0=startPoint.state().velocityWorldMS().get(2),vz1=endPoint.state().velocityWorldMS().get(2);
        if(vz0>0 && vz1<=0) {
            double alpha=vz0/(vz0-vz1);
            events.add(occurrence(FlightEventType.APOGEE,start+alpha*duration,alpha,startPoint,endPoint));
        }
        double ground=context.launchConditions().initialPositionWorldM().get(2);
        double h0=startPoint.state().positionWorldM().get(2)-ground,h1=endPoint.state().positionWorldM().get(2)-ground;
        if(h0>0 && h1<=0) {
            double alpha=h0/(h0-h1);
            events.add(occurrence(FlightEventType.GROUND,start+alpha*duration,alpha,startPoint,endPoint));
        }
        events.sort(Comparator.comparingDouble(FlightEventOccurrence3DOF::timeS).thenComparingInt(e->e.eventType().ordinal()));
        return List.copyOf(events);
    }
    private static FlightEventOccurrence3DOF occurrence(FlightEventType type,double time,double alpha,
            IntegrationPoint3DOF start,IntegrationPoint3DOF end) {
        if(!Double.isFinite(time))throw new EventDetectionError("INVALID_EVENT_LOCALIZATION","event_time_s",time);
        if(!Double.isFinite(alpha)||!(0<=alpha && alpha<=1))throw new EventDetectionError("INVALID_EVENT_LOCALIZATION","interpolation_fraction",alpha);
        double[] position=new double[3],velocity=new double[3]; boolean finite=true;
        for(int i=0;i<3;i++) {
            position[i]=start.state().positionWorldM().get(i)+alpha*(end.state().positionWorldM().get(i)-start.state().positionWorldM().get(i));
            velocity[i]=start.state().velocityWorldMS().get(i)+alpha*(end.state().velocityWorldMS().get(i)-start.state().velocityWorldMS().get(i));
            finite &= Double.isFinite(position[i]) && Double.isFinite(velocity[i]);
        }
        if(!finite)throw new EventDetectionError("INVALID_EVENT_LOCALIZATION","estimated_state",
            List.of(List.of(position[0],position[1],position[2]),List.of(velocity[0],velocity[1],velocity[2])));
        return new FlightEventOccurrence3DOF(type,time,alpha,new TranslationalState3DOF(position,velocity));
    }
}
