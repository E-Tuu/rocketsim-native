package org.roketsim.core.propulsion;
import org.roketsim.core.mass.InertiaTensor3D;
/** Centroidal motor tensor and visible model/provenance identity; no mass/CG authority. */
public record MotorInertiaResult(InertiaTensor3D inertiaAtMotorCg,MotorInertiaModelKind modelKind,String provenanceReference) {
    public MotorInertiaResult {
        MotorInertiaEvaluator.required(inertiaAtMotorCg,"inertiaAtMotorCg");
        MotorInertiaEvaluator.required(modelKind,"modelKind");
        MotorInertiaEvaluator.source(provenanceReference);
    }
}
