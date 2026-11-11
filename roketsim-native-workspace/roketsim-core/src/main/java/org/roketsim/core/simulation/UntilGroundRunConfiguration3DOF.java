package org.roketsim.core.simulation;

import java.util.Optional;

/** Explicit ground-termination policy. The composed legacy step cap is not used by this policy.
 * No duration or synthetic unlimited limit is stored. Guide and recovery are independently optional. */
public record UntilGroundRunConfiguration3DOF(SingleStageVehicleRunConfiguration3DOF vehicle,
        Optional<LaunchGuideRunConfiguration3DOF> launchGuide,Optional<RecoveryConfiguration3DOF> recovery,
        IntegrationStepAlignmentMode alignmentMode) {
    public UntilGroundRunConfiguration3DOF {
        if(vehicle==null || launchGuide==null || recovery==null || alignmentMode==null)
            throw new IllegalArgumentException("vehicle, launchGuide, recovery and alignmentMode must not be null");
        if(launchGuide.isPresent() && (!launchGuide.orElseThrow().vehicle().equals(vehicle)
                || launchGuide.orElseThrow().alignmentMode()!=alignmentMode))
            throw new IllegalArgumentException("launchGuide must use the same vehicle and alignmentMode");
    }
}
