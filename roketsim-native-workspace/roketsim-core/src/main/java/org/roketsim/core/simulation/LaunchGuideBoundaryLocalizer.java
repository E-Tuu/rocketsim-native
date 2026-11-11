package org.roketsim.core.simulation;

import java.util.function.DoublePredicate;
import org.roketsim.core.dynamics.LaunchConditions3DOF;
import org.roketsim.core.numerics.*;

/** Only guide boundary reintegration. No causal queue, dispatch framework or historical event changes. */
public final class LaunchGuideBoundaryLocalizer {
    record DurationBracket(double low,double high) { }
    static DurationBracket firstTrue(double high,DoublePredicate predicate) {
        LaunchGuideError.finite(high,"eventStepSizeS");
        if(high<=0 || !predicate.test(high)) throw new LaunchGuideError("GUIDE_EVENT_NOT_BRACKETED","eventStepSizeS",high);
        double low=0;
        if(predicate.test(0)) return new DurationBracket(0,0);
        while(true) {
            double mid=low+(high-low)/2;
            if(mid<=low || mid>=high) return new DurationBracket(low,high);
            if(predicate.test(mid)) high=mid; else low=mid;
        }
    }
    static IntegrationPoint3DOF step(IntegrationPoint3DOF start,double h,DerivativeFunction3DOF derivative) {
        if(h==0 || start.timeS()+h==start.timeS()) return start;
        return new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(h),derivative);
    }
    /** Localizes the rising guide-travel crossing; reintegrates from the same accepted start.
     * Bisection ends at adjacent representable durations, choosing the first post-boundary RK4 point.
     */
    public LaunchGuideTransitionResult guidanceEnd(IntegrationPoint3DOF start,double maxStepSizeS,
            LaunchConditions3DOF launch,double targetTravelM,DerivativeFunction3DOF derivative,
            LaunchGuideTransitionResult.Kind kind) {
        if(kind!=LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END && kind!=LaunchGuideTransitionResult.Kind.NOMINAL_ROD_EXIT)
            throw new LaunchGuideError("UNSUPPORTED_GUIDE_BOUNDARY_KIND","kind",kind);
        LaunchGuideError.finite(targetTravelM,"targetTravelM");
        if(metric(launch,start,kind)>=targetTravelM)
            throw new LaunchGuideError("GUIDE_BOUNDARY_ALREADY_REACHED","targetTravelM",targetTravelM);
        var bracket=firstTrue(maxStepSizeS,h->metric(launch,step(start,h,derivative),kind)>=targetTravelM);
        var low=step(start,bracket.low(),derivative); var high=step(start,bracket.high(),derivative);
        if(high.timeS()<=start.timeS()) throw new LaunchGuideError("GUIDE_EVENT_NO_TIME_PROGRESS","timeS",high.timeS());
        return new LaunchGuideTransitionResult(kind,high,low.timeS(),high.timeS(),targetTravelM,
            LaunchGuideTransitionResult.AcceptedSide.FIRST_POST_BOUNDARY_RK4);
    }
    private static double metric(LaunchConditions3DOF launch,IntegrationPoint3DOF point,LaunchGuideTransitionResult.Kind kind) {
        return kind==LaunchGuideTransitionResult.Kind.NOMINAL_ROD_EXIT?
            LaunchGuideEvaluation.displacementMagnitude(launch,point.state()):LaunchGuideEvaluation.travel(launch,point.state());
    }
}
