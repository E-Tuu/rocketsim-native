package org.roketsim.core.mass;

import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Supplied BODY-aligned centroidal tensor; only explicitly centered hardware is supported.
 * Provenance is a supplied evidence/model identifier, not certification of realizability.
 */
public record AttachedHardwareInertia(String componentId,InertiaTensor3D inertiaAtComponentCgBody,
        double cgBodyXM,double cgBodyYM,String provenanceReference) {
    public AttachedHardwareInertia {
        VehicleInertiaEvaluator.text(componentId,"componentId");
        VehicleInertiaEvaluator.required(inertiaAtComponentCgBody,componentId+".inertiaAtComponentCgBody");
        requireFinite(cgBodyXM,componentId+".cgBodyXM");
        requireFinite(cgBodyYM,componentId+".cgBodyYM");
        if(cgBodyXM!=0 || cgBodyYM!=0)throw new IllegalArgumentException(componentId+": nonzero cgBodyXM/cgBodyYM unsupported");
        VehicleInertiaEvaluator.text(provenanceReference,componentId+".provenanceReference");
    }
}
