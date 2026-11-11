package org.roketsim.core.aerodynamics;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Explicit finish design data; independent of bulk material density. */
public record SurfaceFinish(String name,double equivalentRoughnessM) {
    public SurfaceFinish {
        requireFinite(equivalentRoughnessM,"equivalent_roughness_m");
        if(name==null || name.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0x85)) {
            throw new SurfaceValidationError("EMPTY_SURFACE_FINISH_NAME","name",name);
        }
        if(equivalentRoughnessM<0) throw new SurfaceValidationError("NEGATIVE_EQUIVALENT_ROUGHNESS","equivalent_roughness_m",equivalentRoughnessM);
    }
}
