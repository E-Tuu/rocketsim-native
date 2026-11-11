package org.roketsim.core.simulation;

/** Opt-in direct flight; existing configurations, constructors and default entrypoints are unchanged. */
public record RecoveryRunConfiguration3DOF(SingleStageVehicleRunConfiguration3DOF vehicle,
        RecoveryConfiguration3DOF recovery,IntegrationStepAlignmentMode alignmentMode) {
    public RecoveryRunConfiguration3DOF {
        if(vehicle==null || recovery==null || alignmentMode==null)
            throw new IllegalArgumentException("vehicle, recovery and alignmentMode must not be null");
    }
}
