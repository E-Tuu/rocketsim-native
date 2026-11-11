package org.roketsim.core.simulation;

/** Opt-in composition; no accepted positional record changes. Null geometry means unknown,
 * and is reported NOT_EVALUABLE by the executor. No usable length or hardware defaults.
 */
public record LaunchGuideRunConfiguration3DOF(SingleStageVehicleRunConfiguration3DOF vehicle,
        LaunchGuideGeometry guide,GuideAttachmentGeometry attachments,LaunchGuideConstraintPolicy policy,
        IntegrationStepAlignmentMode alignmentMode) {
    public LaunchGuideRunConfiguration3DOF {
        LaunchGuideError.required(vehicle,"vehicle"); LaunchGuideError.required(policy,"policy");
        LaunchGuideError.required(alignmentMode,"alignmentMode");
    }
    public double guidanceEndTravelM() {
        LaunchGuideError.required(guide,"guide");
        if(policy==LaunchGuideConstraintPolicy.OPENROCKET_NOMINAL_ROD_COMPAT) return guide.topCoordinateM();
        LaunchGuideError.required(attachments,"attachments");
        return LaunchGuideError.finite(guide.topCoordinateM()-attachments.forwardContact0M(),"guidanceEndTravelM");
    }
}
