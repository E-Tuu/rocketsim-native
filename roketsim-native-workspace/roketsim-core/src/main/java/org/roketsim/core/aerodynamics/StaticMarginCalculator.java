package org.roketsim.core.aerodynamics;
import org.roketsim.core.geometry.ResolvedRocketGeometry;
import org.roketsim.core.mass.RocketMassProperties;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
/** Stateless consumer of CP, CG and geometry Dmax authorities. */
public final class StaticMarginCalculator {
    public StaticMarginResult evaluate(StaticAerodynamicProperties staticAerodynamics,
            RocketMassProperties rocketMassProperties,ResolvedRocketGeometry geometry) {
        double cp=requireFinite(staticAerodynamics.cpXGeoM(),"cp_x_geo_m");
        double cg=requireFinite(rocketMassProperties.totalCgXGeoM(),"total_cg_x_geo_m");
        double diameter=requireFinite(geometry.maxExternalAirframeDiameterM(),"max_external_airframe_diameter_m");
        if(diameter<=0) throw new AerodynamicEvaluationError("NON_POSITIVE_STATIC_MARGIN_DIAMETER","max_external_airframe_diameter_m",diameter);
        double margin=(cp-cg)/diameter;
        if(!Double.isFinite(margin)) throw new AerodynamicEvaluationError("INVALID_STATIC_MARGIN","static_margin_calibers",margin);
        return new StaticMarginResult(margin);
    }
}
