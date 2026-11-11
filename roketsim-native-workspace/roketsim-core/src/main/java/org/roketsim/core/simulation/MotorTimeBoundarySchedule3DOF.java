package org.roketsim.core.simulation;

import java.util.List;
import java.util.TreeSet;
import org.roketsim.core.propulsion.MotorCgEvolutionModel;
import org.roketsim.core.propulsion.MotorCurveStatistics;
import org.roketsim.core.propulsion.MotorMassEvolutionModel;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Immutable absolute-time source grid. No thrust, impulse or mass is evaluated here. */
public final class MotorTimeBoundarySchedule3DOF {
    private final List<Double> boundaryTimesS;

    private MotorTimeBoundarySchedule3DOF(List<Double> times) {
        boundaryTimesS=List.copyOf(times);
    }

    /** Build from the installed motor and explicitly selected source policies.
     * @param context accepted physics context, including the sole ignition authority
     * @param motorStatistics accepted curve-end authority
     * @return immutable sorted, deduplicated absolute boundaries
     */
    public static MotorTimeBoundarySchedule3DOF fromContext(
            PhysicsEvaluationContext3DOF context,MotorCurveStatistics motorStatistics) {
        if(context==null)throw new IllegalArgumentException("context must not be null");
        return fromInputs(PhysicsContextInputs3DOF.legacy(context),motorStatistics);
    }
    public static MotorTimeBoundarySchedule3DOF fromRuntimeContext(RuntimePhysicsEvaluationContext3DOF context) {
        if(context==null)throw new IllegalArgumentException("context must not be null");
        return fromInputs(context,new org.roketsim.core.propulsion.MotorCurveAnalyzer().analyzeRuntime(context.motorInstallation().motor()));
    }
    static MotorTimeBoundarySchedule3DOF fromInputs(PhysicsContextInputs3DOF context,MotorCurveStatistics motorStatistics) {
        if(context==null) throw new IllegalArgumentException("context must not be null");
        if(motorStatistics==null) throw new IllegalArgumentException("motor_statistics must not be null");
        if(context.propulsionTimeline()==null) throw new IllegalArgumentException("propulsion_timeline must not be null");
        if(context.motorInstallation()==null || context.motorInstallation().motor()==null)
            throw new IllegalArgumentException("motor_installation.motor must not be null");
        if(context.motorPropertyModelProfile()==null)
            throw new IllegalArgumentException("motor_property_model_profile must not be null");
        double ignition=context.propulsionTimeline().ignitionTimeS();
        double end=requireFinite(motorStatistics.curveEndTimeS(),"curve_end_time_s");
        if(end<=0) throw new IllegalArgumentException("curve_end_time_s must be positive");
        var motor=context.motorInstallation().motor();
        // Numeric comparison also coalesces signed zero; no near-equality tolerance.
        var times=new TreeSet<Double>((a,b)->a.doubleValue()==b.doubleValue()?0:Double.compare(a,b));
        for(var sample:motor.thrustCurve()) addBoundary(times,ignition,sample.timeS());
        addBoundary(times,ignition,end);
        if(context.motorPropertyModelProfile().massModel()==MotorMassEvolutionModel.EXPLICIT_CURVE) {
            if(motor.massCurve()==null) throw new IllegalArgumentException("explicit mass_curve is required");
            for(var sample:motor.massCurve()) addBoundary(times,ignition,sample.timeS());
        }
        if(context.motorPropertyModelProfile().cgModel()==MotorCgEvolutionModel.EXPLICIT_CURVE) {
            if(motor.cgCurve()==null) throw new IllegalArgumentException("explicit cg_curve is required");
            for(var sample:motor.cgCurve()) addBoundary(times,ignition,sample.timeS());
        }
        return new MotorTimeBoundarySchedule3DOF(List.copyOf(times));
    }

    private static void addBoundary(TreeSet<Double> times,double ignition,double motorTime) {
        times.add(requireFinite(ignition+motorTime,"absolute_boundary_time_s"));
    }

    /** Retain coincident nominal endpoints; otherwise shorten to an interior source boundary.
     * @param currentTimeS current accepted absolute time
     * @param nominalStepSizeS positive finite nominal step
     * @return positive finite step no greater than nominal
     */
    public double effectiveStepSizeS(double currentTimeS,double nominalStepSizeS) {
        return effectiveStepSize(currentTimeS,nominalStepSizeS,false);
    }

    // Ground-enabled execution needs typed numerical outcomes; legacy callers retain exact errors.
    double effectiveStepSizeForExecutionS(double currentTimeS,double nominalStepSizeS) {
        return effectiveStepSize(currentTimeS,nominalStepSizeS,true);
    }

    private double effectiveStepSize(double currentTimeS,double nominalStepSizeS,boolean execution) {
        requireFinite(currentTimeS,"current_time_s");
        requireFinite(nominalStepSizeS,"nominal_step_size_s");
        if(nominalStepSizeS<=0) throw new IllegalArgumentException("nominal_step_size_s must be positive");
        double proposed=finiteStep(currentTimeS+nominalStepSizeS,"proposed_endpoint_time_s",execution);
        if(proposed<=currentTimeS) throw stepFailure(execution,"MOTOR_BOUNDARY_NONADVANCING_TIME","proposed_endpoint_time_s",proposed,
            "proposed_endpoint_time_s does not advance current_time_s");
        int low=0,high=boundaryTimesS.size();
        while(low<high) {
            int mid=low+(high-low)/2;
            if(boundaryTimesS.get(mid)<=currentTimeS) low=mid+1; else high=mid;
        }
        if(low==boundaryTimesS.size() || boundaryTimesS.get(low)>proposed) return nominalStepSizeS;
        double boundary=boundaryTimesS.get(low);
        // Subtracting a boundary already reached by t+h can round to a step larger than h.
        if(boundary==proposed) return nominalStepSizeS;
        double effective=finiteStep(boundary-currentTimeS,"effective_step_size_s",execution);
        if(effective<=0 || effective>nominalStepSizeS)
            throw stepFailure(execution,"MOTOR_BOUNDARY_UNREPRESENTABLE_STEP","effective_step_size_s",effective,"effective_step_size_s cannot safely represent boundary_time_s="+boundary
                +" from current_time_s="+currentTimeS+" within nominal_step_size_s="+nominalStepSizeS);
        double endpoint=finiteStep(currentTimeS+effective,"endpoint_time_s",execution);
        if(endpoint<=currentTimeS || endpoint!=boundary)
            throw stepFailure(execution,"MOTOR_BOUNDARY_UNREPRESENTABLE_ENDPOINT","endpoint_time_s",endpoint,
                "endpoint_time_s cannot reach boundary_time_s="+boundary+" without crossing or snapping");
        return effective;
    }

    private static double finiteStep(double value,String field,boolean execution) {
        if(!Double.isFinite(value))throw stepFailure(execution,"MOTOR_BOUNDARY_NONFINITE_TIME",field,value,field+" must be finite; got "+value);
        return value;
    }
    private static IllegalArgumentException stepFailure(boolean execution,String code,String field,double value,String message) {
        return execution?new org.roketsim.core.numerics.NumericalIntegrationError(code,field,value):new IllegalArgumentException(message);
    }

    /** @return immutable increasing distinct absolute-time boundaries */
    public List<Double> boundaryTimesS() { return boundaryTimesS; }
}
