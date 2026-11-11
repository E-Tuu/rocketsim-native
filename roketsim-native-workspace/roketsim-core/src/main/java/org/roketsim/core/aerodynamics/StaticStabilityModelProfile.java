package org.roketsim.core.aerodynamics;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Explicit alpha-to-zero policy, with exclusive Mach ceiling; not an AoA model. */
public record StaticStabilityModelProfile(Object normalForceModel,Object finCenterOfPressureModel,
        Object bodyLiftTreatment,double maximumSupportedMachExclusive) {
    public StaticStabilityModelProfile {
        double maximum=requireFinite(maximumSupportedMachExclusive,"maximum_supported_mach_exclusive");
        if(maximum<=0) throw new AerodynamicEvaluationError("NON_POSITIVE_STATIC_STABILITY_MACH_LIMIT","maximum_supported_mach_exclusive",maximum);
    }
    public static final StaticStabilityModelProfile NATIVE_STATIC_STABILITY_V1_PROFILE=new StaticStabilityModelProfile(
        StaticNormalForceModel.EXTENDED_BARROWMAN_SUBSONIC,FinCenterOfPressureModel.EXTENDED_BARROWMAN_MACH_DEPENDENT,
        BodyLiftTreatment.LINEARIZED_ZERO_AOA,.8);
}
