package org.roketsim.core.simulation;

import org.roketsim.core.numerics.IntegrationPoint3DOF;

/** Accepted RK4 state and its disclosed representable event bracket; never NAT-018's estimated state. */
public record LaunchGuideTransitionResult(Kind kind,IntegrationPoint3DOF acceptedPoint,
        double lowerTimeS,double upperTimeS,double targetTravelM,AcceptedSide acceptedSide) {
    public enum Kind { LIFTOFF, TWO_POINT_GUIDANCE_END, NOMINAL_ROD_EXIT, PAD_RECONTACT }
    public enum AcceptedSide { FIRST_POST_BOUNDARY_RK4, LAST_NONPENETRATING_RK4 }
    public LaunchGuideTransitionResult {
        LaunchGuideError.required(kind,"kind"); LaunchGuideError.required(acceptedPoint,"acceptedPoint");
        LaunchGuideError.required(acceptedSide,"acceptedSide");
        LaunchGuideError.finite(lowerTimeS,"lowerTimeS"); LaunchGuideError.finite(upperTimeS,"upperTimeS");
        LaunchGuideError.finite(targetTravelM,"targetTravelM");
        if(lowerTimeS>acceptedPoint.timeS() || acceptedPoint.timeS()>upperTimeS)
            throw new LaunchGuideError("INVALID_EVENT_BRACKET","acceptedPoint.timeS",acceptedPoint.timeS());
        LaunchGuideError.finite(upperTimeS-lowerTimeS,"bracketWidthS");
    }
    /** Timestamp quantization bound only; does not bound RK4 truncation or physical-model error. */
    public double timeQuantizationBoundS() {
        return Math.max(upperTimeS-lowerTimeS,Math.ulp(acceptedPoint.timeS()));
    }
}
