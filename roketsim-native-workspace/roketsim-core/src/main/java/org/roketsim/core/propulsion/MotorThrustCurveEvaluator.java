package org.roketsim.core.propulsion;
import java.util.List;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Stateless piecewise-linear evaluator and analytic trapezoidal integration. */
public final class MotorThrustCurveEvaluator {
    static double[] impulsePrefixes(List<ThrustSample> curve) {
        double[] prefixes=new double[curve.size()];
        for(int i=1;i<curve.size();i++) {
            ThrustSample left=curve.get(i-1),right=curve.get(i);
            double area=0.5*(left.thrustN()+right.thrustN())*(right.timeS()-left.timeS());
            double total=prefixes[i-1]+area;
            if(!Double.isFinite(total)||area<0) { fail("INVALID_TOTAL_IMPULSE","total_impulse_N_s",total); }
            prefixes[i]=total;
        }
        if(prefixes[prefixes.length-1]<=0) { fail("INVALID_TOTAL_IMPULSE","total_impulse_N_s",prefixes[prefixes.length-1]); }
        return prefixes;
    }
    public MotorThrustState evaluate(MotorDefinition motor,double motorTimeS) {
        double time=requireFinite(motorTimeS,"motor_time_s");
        if(time<0) { fail("NEGATIVE_MOTOR_TIME","motor_time_s",time); }
        var curve=motor.thrustCurve(); var prefixes=impulsePrefixes(curve);
        double total=prefixes[prefixes.length-1],thrust=0,impulse=total;
        if(time<=curve.get(curve.size()-1).timeS()) {
            for(int i=0;i<curve.size();i++) {
                var sample=curve.get(i);
                if(time==sample.timeS()) { thrust=sample.thrustN(); impulse=prefixes[i]; break; }
                if(time<sample.timeS()) {
                    var left=curve.get(i-1); double elapsed=time-left.timeS();
                    thrust=left.thrustN()+(sample.thrustN()-left.thrustN())*(elapsed/(sample.timeS()-left.timeS()));
                    impulse=prefixes[i-1]+0.5*(left.thrustN()+thrust)*elapsed;
                    break;
                }
            }
        }
        if(!Double.isFinite(thrust)||thrust<0) { fail("INVALID_THRUST_RESULT","thrust_N",thrust); }
        if(!Double.isFinite(impulse)||!(0<=impulse && impulse<=total)) { fail("INVALID_CUMULATIVE_IMPULSE","cumulative_impulse_N_s",impulse); }
        return new MotorThrustState(thrust,impulse);
    }
    static void fail(String code,String field,Object value) { throw new PropulsionEvaluationError(code,field,value); }
}
