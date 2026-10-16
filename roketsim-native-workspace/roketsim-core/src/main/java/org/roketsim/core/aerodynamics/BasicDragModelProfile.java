package org.roketsim.core.aerodynamics;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Accepted explicit Python drag policy; only the source minimum is constructor-validated. */
public record BasicDragModelProfile(Object boundaryLayerModel,Object reynoldsLengthPolicy,
        Object lowReynoldsContinuation,double minimumFrictionEvaluationReynolds,
        Object nosePressureModel,Object finEdgeModel,Object baseDragModel,Object plumeTreatment) {
    public BasicDragModelProfile {
        double minimum=requireFinite(minimumFrictionEvaluationReynolds,"minimum_friction_evaluation_reynolds");
        if(minimum<=0) throw new AerodynamicEvaluationError("NON_POSITIVE_MINIMUM_FRICTION_REYNOLDS","minimum_friction_evaluation_reynolds",minimum);
    }
    public static final BasicDragModelProfile NATIVE_BASIC_DRAG_V1_PROFILE=new BasicDragModelProfile(
        BoundaryLayerModel.FULLY_TURBULENT,ReynoldsLengthPolicy.AERODYNAMIC_LENGTH,
        LowReynoldsContinuation.MINIMUM_EVALUATION_REYNOLDS,1e4,
        NosePressureModel.TD13_CONICAL_SUBSONIC_TO_SONIC,FinEdgeModel.TD13_SQUARE,
        BaseDragModel.TD13_SUBSONIC_TO_SONIC,PlumeTreatment.IGNORED);
}
