package org.roketsim.core.propulsion;
import static org.roketsim.core.propulsion.MotorThrustCurveEvaluator.fail;
/** First peak, first threshold entry and last exit; full-tail impulse is retained. */
public final class MotorCurveAnalyzer {
    public MotorCurveStatistics analyze(MotorDefinition motor) {
        var curve=motor.thrustCurve(); var prefixes=MotorThrustCurveEvaluator.impulsePrefixes(curve);
        double total=prefixes[prefixes.length-1];
        ThrustSample peak=curve.get(0);
        for(var sample:curve) { if(sample.thrustN()>peak.thrustN()) { peak=sample; } }
        double endTime=curve.get(curve.size()-1).timeS();
        if(!Double.isFinite(peak.thrustN())||peak.thrustN()<=0) { fail("INVALID_PEAK_THRUST","peak_thrust_N",peak.thrustN()); }
        if(!Double.isFinite(endTime)||endTime<=0) { fail("INVALID_CURVE_END_TIME","curve_end_time_s",endTime); }
        if(!Double.isFinite(peak.timeS())||!(0<=peak.timeS() && peak.timeS()<=endTime)) { fail("INVALID_PEAK_TIME","peak_thrust_time_s",peak.timeS()); }
        double threshold=0.05*peak.thrustN();
        if(threshold<=0) { fail("INVALID_EFFECTIVE_BURN_START","threshold_thrust_N",threshold); }
        int first=-1,last=-1;
        for(int i=0;i<curve.size();i++) { if(curve.get(i).thrustN()>=threshold) { if(first==-1) { first=i; } last=i; } }
        var entry=curve.get(first); var exit=curve.get(last);
        double start=entry.timeS(),end=exit.timeS();
        if(entry.thrustN()!=threshold) {
            var before=curve.get(first-1);
            start=before.timeS()+(threshold-before.thrustN())/(entry.thrustN()-before.thrustN())*(entry.timeS()-before.timeS());
        }
        if(exit.thrustN()!=threshold) {
            var after=curve.get(last+1);
            end=exit.timeS()+(threshold-exit.thrustN())/(after.thrustN()-exit.thrustN())*(after.timeS()-exit.timeS());
        }
        if(!Double.isFinite(start)||!(0<=start && start<endTime)) { fail("INVALID_EFFECTIVE_BURN_START","effective_burn_start_5pct_s",start); }
        if(!Double.isFinite(end)||!(start<end && end<=endTime)) { fail("INVALID_EFFECTIVE_BURN_END","effective_burn_end_5pct_s",end); }
        double duration=end-start;
        if(!Double.isFinite(duration)||duration<=0) { fail("INVALID_EFFECTIVE_BURN_TIME","effective_burn_time_5pct_s",duration); }
        return new MotorCurveStatistics(total,peak.thrustN(),peak.timeS(),endTime,start,end,duration);
    }
}
