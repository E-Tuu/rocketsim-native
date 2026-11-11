package org.roketsim.core.simulation;
import java.util.List;
import java.util.Optional;
import org.roketsim.core.dynamics.LaunchConditions3DOF;
import org.roketsim.core.mass.AttachedHardwareMass;
import org.roketsim.core.numerics.FixedStepConfig;
import org.roketsim.core.propulsion.*;

/** Internal run views. No simulation loop, physical defaults or fake unlimited limit. */
interface SimulationRunInputs3DOF {
    LaunchConditions3DOF launchConditions();
    PhysicsContextInputs3DOF physicsContext();
    MotorCurveStatistics motorCurveStatistics();
    FlightEventDetectionProfile3DOF eventDetectionProfile();
    FixedStepConfig fixedStepConfig();
    Object terminalEventHandlingModel();
    int maximumSteps();
    static SimulationRunInputs3DOF legacy(SimulationRunConfiguration3DOF source) {return new Legacy(source);}
    static SimulationRunInputs3DOF source(SourceMotorGroundRunConfiguration3DOF source) {
        return new Source(source,new MotorCurveAnalyzer().analyzeRuntime(source.physicsContext().motorInstallation().motor()));
    }
    record Legacy(SimulationRunConfiguration3DOF source) implements SimulationRunInputs3DOF {
        public LaunchConditions3DOF launchConditions() {return source.launchConditions();}
        public PhysicsContextInputs3DOF physicsContext() {return PhysicsContextInputs3DOF.legacy(source.physicsContext());}
        public MotorCurveStatistics motorCurveStatistics() {return source.motorCurveStatistics();}
        public FlightEventDetectionProfile3DOF eventDetectionProfile() {return source.eventDetectionProfile();}
        public FixedStepConfig fixedStepConfig() {return source.fixedStepConfig();}
        public Object terminalEventHandlingModel() {return source.terminalEventHandlingModel();}
        public int maximumSteps() {return source.runLimits().maximumSteps();}
    }
    record Source(SourceMotorGroundRunConfiguration3DOF source,MotorCurveStatistics motorCurveStatistics) implements SimulationRunInputs3DOF {
        public LaunchConditions3DOF launchConditions() {return source.launchConditions();}
        public PhysicsContextInputs3DOF physicsContext() {return source.physicsContext();}
        public FlightEventDetectionProfile3DOF eventDetectionProfile() {return source.eventDetectionProfile();}
        public FixedStepConfig fixedStepConfig() {return source.fixedStepConfig();}
        public Object terminalEventHandlingModel() {return source.terminalEventHandlingModel();}
        public int maximumSteps() {throw new IllegalStateException("Source until-ground policy has no step cap");}
    }
}
interface GroundConfigurationInputs3DOF {
    SimulationRunInputs3DOF base();
    boolean hasGuide();
    Optional<RecoveryConfiguration3DOF> recovery();
    static GroundConfigurationInputs3DOF legacy(UntilGroundRunConfiguration3DOF c) {
        return new GroundConfigurationInputs3DOF() {
            public SimulationRunInputs3DOF base() {return SimulationRunInputs3DOF.legacy(c.vehicle().baseRun());}
            public boolean hasGuide() {return c.launchGuide().isPresent();}
            public Optional<RecoveryConfiguration3DOF> recovery() {return c.recovery();}
        };
    }
    static GroundConfigurationInputs3DOF source(SourceMotorGroundRunConfiguration3DOF c,SimulationRunInputs3DOF base) {
        return new GroundConfigurationInputs3DOF() {
            public SimulationRunInputs3DOF base() {return base;}
            public boolean hasGuide() {return c.launchGuide().isPresent();}
            public Optional<RecoveryConfiguration3DOF> recovery() {return c.recovery();}
        };
    }
}
interface GuideRunInputs3DOF {
    SimulationRunInputs3DOF base();
    List<AttachedHardwareMass> hardware();
    LaunchGuideGeometry guide();
    GuideAttachmentGeometry attachments();
    LaunchGuideConstraintPolicy policy();
    IntegrationStepAlignmentMode alignmentMode();
    default double guidanceEndTravelM() {
        LaunchGuideError.required(guide(),"guide");
        if(policy()==LaunchGuideConstraintPolicy.OPENROCKET_NOMINAL_ROD_COMPAT)return guide().topCoordinateM();
        LaunchGuideError.required(attachments(),"attachments");
        return LaunchGuideError.finite(guide().topCoordinateM()-attachments().forwardContact0M(),"guidanceEndTravelM");
    }
    static GuideRunInputs3DOF legacy(LaunchGuideRunConfiguration3DOF c) {
        return new GuideRunInputs3DOF() {
            public SimulationRunInputs3DOF base() {return SimulationRunInputs3DOF.legacy(c.vehicle().baseRun());}
            public List<AttachedHardwareMass> hardware() {return c.vehicle().attachedHardware();}
            public LaunchGuideGeometry guide() {return c.guide();}
            public GuideAttachmentGeometry attachments() {return c.attachments();}
            public LaunchGuideConstraintPolicy policy() {return c.policy();}
            public IntegrationStepAlignmentMode alignmentMode() {return c.alignmentMode();}
        };
    }
    static GuideRunInputs3DOF source(SourceMotorGroundRunConfiguration3DOF c,SimulationRunInputs3DOF base) {
        var g=c.launchGuide().orElseThrow();
        return new GuideRunInputs3DOF() {
            public SimulationRunInputs3DOF base() {return base;}
            public List<AttachedHardwareMass> hardware() {return c.attachedHardware();}
            public LaunchGuideGeometry guide() {return g.guide();}
            public GuideAttachmentGeometry attachments() {return g.attachments();}
            public LaunchGuideConstraintPolicy policy() {return g.policy();}
            public IntegrationStepAlignmentMode alignmentMode() {return c.alignmentMode();}
        };
    }
}
