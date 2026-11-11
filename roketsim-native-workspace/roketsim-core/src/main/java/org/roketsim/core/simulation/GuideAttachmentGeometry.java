package org.roketsim.core.simulation;

/** Explicit initial contact coordinates in the guide datum, not inferred from rocket length/CG. */
public record GuideAttachmentGeometry(String datumId,double forwardContact0M,double aftContact0M,String sourceReference) {
    public GuideAttachmentGeometry {
        LaunchGuideError.text(datumId,"attachments.datumId"); LaunchGuideError.text(sourceReference,"attachments.sourceReference");
        LaunchGuideError.finite(forwardContact0M,"forwardContact0M"); LaunchGuideError.finite(aftContact0M,"aftContact0M");
        if(aftContact0M<0 || forwardContact0M<=aftContact0M)
            throw new LaunchGuideError("INVALID_BUTTON_ORDER","forward/aftContact0M",java.util.List.of(forwardContact0M,aftContact0M));
    }
}
