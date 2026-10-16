package org.roketsim.core.mass;
import org.roketsim.core.propulsion.MotorMassProperties;
/** One supplied structure plus one completed motor result; no time/model selection. */
public final class RocketMassPropertiesCalculator {
    public RocketMassProperties evaluate(StructuralMassProperties structure,MotorMassProperties motor) {
        double structureMass=structure.structureMassKg(),motorMass=motor.massKg();
        double structureCg=structure.structureCgXGeoM(),motorCg=motor.cgXGeoM();
        double total=structureMass+motorMass;
        if(!Double.isFinite(total)) { throw new MassValidationError("INVALID_TOTAL_MASS","total_mass_kg",total); }
        if(total<=0) { throw new MassValidationError("NON_POSITIVE_TOTAL_MASS","total_mass_kg",total); }
        double cg=structureCg==motorCg?structureCg:(structureMass*structureCg+motorMass*motorCg)/total;
        if(!Double.isFinite(cg)) { throw new MassValidationError("INVALID_TOTAL_CG","total_cg_x_geo_m",cg); }
        if(!(Math.min(structureCg,motorCg)<=cg && cg<=Math.max(structureCg,motorCg))) {
            throw new MassValidationError("TOTAL_CG_OUTSIDE_CONTRIBUTOR_SPAN","total_cg_x_geo_m",cg);
        }
        return new RocketMassProperties(total,cg);
    }
}
