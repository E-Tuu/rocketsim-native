package org.roketsim.core.simulation;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/** Configuration checks only, never force/transition decisions or physical launch certification. */
public final class Teknofest2026LaunchDiagnostics {
    public enum Category { A1, A2, A3, A4, A5, A6, A7 }
    public enum Metric { TWO_POINT_GUIDANCE_END, OPENROCKET_NOMINAL_ROD_EXIT }
    public enum Status { PASS, FAIL, NOT_EVALUABLE }
    public record Check(String rule,Status status,String metric,OptionalDouble actual,OptionalDouble requirement,String detail) { }
    /** Explicit transformation x_geo = noseCoordinate0M - buttonGuideCoordinate0M. */
    public record PlacementEvidence(double noseCoordinate0M,String sourceReference) {
        public PlacementEvidence {
            LaunchGuideError.finite(noseCoordinate0M,"noseCoordinate0M");LaunchGuideError.text(sourceReference,"placement.sourceReference");
        }
    }
    /** Evidence must match the actual authoritative launch vector, not replace it. */
    public record OrientationEvidence(double elevationDeg,double azimuthDeg,String sourceReference) {
        public OrientationEvidence {
            LaunchGuideGeometry.directionFromAngles(elevationDeg,azimuthDeg);
            LaunchGuideError.text(sourceReference,"orientation.sourceReference");
        }
    }
    public static double minimumSpeedMS(Category category) {
        LaunchGuideError.required(category,"category");
        return switch(category) {case A1->15;case A2,A4->25;case A3->30;case A5,A6,A7->20;};
    }
    public Check minimumExitSpeed(Category category,Metric metric,OptionalDouble measured) {
        LaunchGuideError.required(metric,"metric");LaunchGuideError.required(measured,"measured");
        double minimum=minimumSpeedMS(category);
        if(measured.isPresent() && (!Double.isFinite(measured.getAsDouble()) || measured.getAsDouble()<0))
            throw new LaunchGuideError("INVALID_EXIT_SPEED_METRIC","measured",measured.getAsDouble());
        return new Check("4.4.8",measured.isEmpty()?Status.NOT_EVALUABLE:measured.getAsDouble()>=minimum?Status.PASS:Status.FAIL,
            metric.name(),measured,OptionalDouble.of(minimum),"Configured metric only; not certified physical rail clearance or launch safety");
    }
    public List<Check> evaluate(Category category,Metric metric,LaunchGuideRunConfiguration3DOF configuration,
            LaunchGuideRunResult3DOF result,Optional<PlacementEvidence> placement,Optional<OrientationEvidence> orientation) {
        LaunchGuideError.required(metric,"metric");LaunchGuideError.required(configuration,"configuration");
        LaunchGuideError.required(result,"result");LaunchGuideError.required(placement,"placement");LaunchGuideError.required(orientation,"orientation");
        if(result.policy()!=configuration.policy())
            throw new LaunchGuideError("GUIDE_RESULT_POLICY_MISMATCH","result.policy",result.policy());
        var measured=metric==Metric.TWO_POINT_GUIDANCE_END?result.twoPointGuidanceEndSpeedMS():result.openRocketNominalRodExitSpeedMS();
        var speed=minimumExitSpeed(category,metric,measured);
        boolean buttons=configuration.attachments()!=null && configuration.policy()==LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE;
        var count=new Check("4.5.15",buttons?Status.PASS:Status.NOT_EVALUABLE,"CONFIGURED_BUTTON_COUNT",
            buttons?OptionalDouble.of(2):OptionalDouble.empty(),OptionalDouble.of(2),"Only the explicitly configured two-button arrangement is supported");
        var order=new Check("BUTTON_ORDER",buttons?Status.PASS:Status.NOT_EVALUABLE,"INITIAL_GUIDE_COORDINATES",
            OptionalDouble.empty(),OptionalDouble.empty(),"Execution validates 0 <= aft < forward < usable top in one datum");
        Check cg=unknown("4.5.16","LOADED_CG_BETWEEN_BUTTONS","Explicit nose-to-guide mapping or initial mass snapshot unavailable");
        Check aft=unknown("4.5.19","AFT_BUTTON_MOTOR_REGION","Explicit placement/motor/geometry evidence unavailable");
        if(buttons && placement.isPresent() && !result.recordedData().samples().isEmpty()) {
            var evidence=placement.orElseThrow();var first=result.recordedData().samples().get(0).physics();
            double forward=LaunchGuideError.finite(evidence.noseCoordinate0M()-configuration.attachments().forwardContact0M(),"forwardButtonXGeoM");
            double rear=LaunchGuideError.finite(evidence.noseCoordinate0M()-configuration.attachments().aftContact0M(),"aftButtonXGeoM");
            double loadedCg=first.rocketMassProperties().totalCgXGeoM();
            cg=between("4.5.16","LOADED_CG_BETWEEN_BUTTONS",loadedCg,forward,rear);
            double motorCg=first.motorMassProperties().cgXGeoM();
            double bodyEnd=configuration.vehicle().baseRun().physicsContext().resolvedGeometry().bodyEndXGeoM();
            aft=between("4.5.19","AFT_BUTTON_MOTOR_REGION",rear,motorCg,bodyEnd);
        }
        Check elevation=unknown("4.1.35","DECLARED_LAUNCH_ELEVATION","Orientation evidence absent or not exactly associated with authoritative launch vector");
        if(orientation.isPresent()) {
            var o=orientation.orElseThrow();
            if(LaunchGuideGeometry.directionFromAngles(o.elevationDeg(),o.azimuthDeg()).equals(
                    configuration.vehicle().baseRun().launchConditions().launchDirectionWorldUnit()))
                elevation=new Check("4.1.35",o.elevationDeg()==85?Status.PASS:Status.FAIL,"DECLARED_LAUNCH_ELEVATION",
                    OptionalDouble.of(o.elevationDeg()),OptionalDouble.of(85),"85 degrees above horizontal; no angle tolerance or direction repair invented");
        }
        return List.of(count,order,cg,aft,elevation,speed);
    }
    private static Check between(String rule,String metric,double value,double lower,double upper) {
        LaunchGuideError.finite(value,metric);LaunchGuideError.finite(lower,"lower");LaunchGuideError.finite(upper,"upper");
        var status=value==lower || value==upper?Status.NOT_EVALUABLE:value>lower && value<upper?Status.PASS:Status.FAIL;
        return new Check(rule,status,metric,OptionalDouble.of(value),OptionalDouble.empty(),
            "Declared coordinate evidence, strict interior check; source boundary inclusivity is unspecified and not silently resolved");
    }
    private static Check unknown(String rule,String metric,String detail) {
        return new Check(rule,Status.NOT_EVALUABLE,metric,OptionalDouble.empty(),OptionalDouble.empty(),detail);
    }
}
