package org.roketsim.core.mass;
import org.roketsim.core.propulsion.MotorMassProperties;
/** Supplied structure, completed motor result and optional explicit auxiliary inputs; no time/model selection. */
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

    /** NAT-023 axial accounting; component ownership is the caller's responsibility.
     * @param structure accepted structural result
     * @param motor accepted runtime motor result
     * @param auxiliaryMassesKg explicit finite, strictly positive hardware masses (kg)
     * @param auxiliaryCgXGeoM matching finite nose-tip-origin, aft-positive coordinates (m)
     * @return existing total mass and axial CG result; no arrays are retained
     * @throws IllegalArgumentException if an auxiliary array is null or lengths differ
     * @throws MassValidationError if an auxiliary entry or derived total violates its contract
     */
    public RocketMassProperties evaluate(StructuralMassProperties structure,MotorMassProperties motor,
            double[] auxiliaryMassesKg,double[] auxiliaryCgXGeoM) {
        if(auxiliaryMassesKg==null || auxiliaryCgXGeoM==null) {
            throw new IllegalArgumentException("auxiliary mass and CG arrays must not be null");
        }
        if(auxiliaryMassesKg.length!=auxiliaryCgXGeoM.length) {
            throw new IllegalArgumentException("auxiliary mass and CG array lengths must agree");
        }
        if(auxiliaryMassesKg.length==0) { return evaluate(structure,motor); }
        for(int i=0;i<auxiliaryMassesKg.length;i++) {
            double mass=auxiliaryMassesKg[i];
            if(!Double.isFinite(mass)||mass<=0) {
                throw new MassValidationError("INVALID_AUXILIARY_MASS","auxiliary_masses_kg["+i+"]",mass);
            }
            double cg=auxiliaryCgXGeoM[i];
            if(!Double.isFinite(cg)) {
                throw new MassValidationError("INVALID_AUXILIARY_CG","auxiliary_cg_x_geo_m["+i+"]",cg);
            }
        }
        double structureMass=structure.structureMassKg(),motorMass=motor.massKg();
        double structureCg=structure.structureCgXGeoM(),motorCg=motor.cgXGeoM();
        double total=structureMass+motorMass;
        double moment=structureMass*structureCg+motorMass*motorCg;
        double minimum=Math.min(structureCg,motorCg),maximum=Math.max(structureCg,motorCg);
        boolean equalCg=structureCg==motorCg;
        for(int i=0;i<auxiliaryMassesKg.length;i++) {
            double mass=auxiliaryMassesKg[i],cg=auxiliaryCgXGeoM[i];
            total+=mass;
            moment+=mass*cg;
            minimum=Math.min(minimum,cg); maximum=Math.max(maximum,cg);
            equalCg=equalCg && cg==structureCg;
        }
        double cg=equalCg?structureCg:moment/total;
        if(!Double.isFinite(total)) { throw new MassValidationError("INVALID_TOTAL_MASS","total_mass_kg",total); }
        if(total<=0) { throw new MassValidationError("NON_POSITIVE_TOTAL_MASS","total_mass_kg",total); }
        if(!Double.isFinite(cg)) { throw new MassValidationError("INVALID_TOTAL_CG","total_cg_x_geo_m",cg); }
        if(!(minimum<=cg && cg<=maximum)) {
            throw new MassValidationError("TOTAL_CG_OUTSIDE_CONTRIBUTOR_SPAN","total_cg_x_geo_m",cg);
        }
        return new RocketMassProperties(total,cg);
    }
}
