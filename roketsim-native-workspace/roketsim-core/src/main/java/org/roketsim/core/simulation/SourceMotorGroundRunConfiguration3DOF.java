package org.roketsim.core.simulation;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.roketsim.core.dynamics.LaunchConditions3DOF;
import org.roketsim.core.mass.AttachedHardwareMass;
import org.roketsim.core.numerics.FixedStepConfig;
import org.roketsim.core.propulsion.SourceMotorValidationError;

/** Explicit source-capable ground policy. No duration/step cap, placeholder motor or selected-source override. */
public record SourceMotorGroundRunConfiguration3DOF(LaunchConditions3DOF launchConditions,
        RuntimePhysicsEvaluationContext3DOF physicsContext,FlightEventDetectionProfile3DOF eventDetectionProfile,
        FixedStepConfig fixedStepConfig,Object terminalEventHandlingModel,List<AttachedHardwareMass> attachedHardware,
        Optional<GuideConfiguration> launchGuide,Optional<RecoveryConfiguration3DOF> recovery,IntegrationStepAlignmentMode alignmentMode) {
    public record GuideConfiguration(LaunchGuideGeometry guide,GuideAttachmentGeometry attachments,LaunchGuideConstraintPolicy policy) {
        public GuideConfiguration {
            if(guide==null || policy==null || (policy==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE && attachments==null))
                throw new SourceMotorValidationError("MISSING_GUIDE_INPUT","launchGuide");
        }
    }
    public SourceMotorGroundRunConfiguration3DOF {
        if(launchConditions==null || physicsContext==null || eventDetectionProfile==null || fixedStepConfig==null || attachedHardware==null
                || launchGuide==null || recovery==null || alignmentMode==null)
            throw new SourceMotorValidationError("MISSING_RUNTIME_RUN_INPUT","sourceRun");
        if(!(physicsContext.motorInstallation().motor() instanceof org.roketsim.core.propulsion.SourceBackedMotorProfile))
            throw new SourceMotorValidationError("SOURCE_PROFILE_REQUIRED","physicsContext.motorInstallation.motor");
        SimulationRunConfiguration3DOF.validateTerminal(terminalEventHandlingModel);
        var ids=new HashSet<String>();
        for(int i=0;i<attachedHardware.size();i++) {
            var h=attachedHardware.get(i);
            if(h==null || !ids.add(h.componentId()))throw new SourceMotorValidationError("INVALID_HARDWARE_IDENTITY","attachedHardware["+i+"]");
        }
        attachedHardware=List.copyOf(attachedHardware);
    }
}
