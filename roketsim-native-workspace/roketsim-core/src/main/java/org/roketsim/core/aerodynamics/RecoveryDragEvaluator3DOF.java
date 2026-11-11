package org.roketsim.core.aerodynamics;

import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;

/** Instantaneous steady drag of one fully deployed parachute with explicitly supplied constant CdA.
 * No deployment, force composition, atmosphere evaluation or simulation execution is performed.
 * The caller retains CdA source, reference-area convention, measured/estimated/assumed basis,
 * parachute-only versus assembly-effective scope and known applicability limitations.
 * Neither this evaluator nor a finite result certifies the supplied CdA or universal applicability.
 * Existing vector norm and double arithmetic retain their finite-range/underflow limitations.
 */
public final class RecoveryDragEvaluator3DOF {
    /** Evaluates {@code -0.5 * rho * CdA * |v_relative| * v_relative} in WORLD ENU.
     * @param airDensityKgM3 finite, nonnegative supplied density (kg/m³)
     * @param relativeVelocityWorldMS immutable finite three-component WORLD relative velocity (m/s)
     * @param effectiveDragAreaM2 finite, strictly positive supplied constant CdA (m²)
     * @return independent immutable WORLD force vector (N); zero for exact zero density or flow
     * @throws AerodynamicEvaluationError for invalid input or nonfinite derived force arithmetic
     */
    public Vector evaluate(double airDensityKgM3,Vector relativeVelocityWorldMS,double effectiveDragAreaM2) {
        if(!Double.isFinite(airDensityKgM3) || airDensityKgM3<0)
            throw error("INVALID_RECOVERY_DENSITY","air_density_kg_m3",airDensityKgM3);
        Vector relative;
        try {
            relative=Vector.of(relativeVelocityWorldMS,3,"relative_velocity_world_m_s");
        } catch(IllegalArgumentException invalid) {
            var failure=error("INVALID_RECOVERY_RELATIVE_FLOW","relative_velocity_world_m_s",relativeVelocityWorldMS);
            failure.initCause(invalid);throw failure;
        }
        if(!Double.isFinite(effectiveDragAreaM2) || effectiveDragAreaM2<=0)
            throw error("INVALID_RECOVERY_DRAG_AREA","effective_drag_area_m2",effectiveDragAreaM2);
        if(airDensityKgM3==0 || (relative.get(0)==0 && relative.get(1)==0 && relative.get(2)==0))
            return Vector.of(new double[3]);
        double speed;
        try {
            speed=Vectors.magnitude(relative);
        } catch(IllegalArgumentException nonfiniteSpeed) {
            // The existing norm rejects its nonfinite result before returning it. Retain the
            // offending input vector and original cause rather than inventing a numeric result.
            var failure=error("NONFINITE_RECOVERY_FORCE","relative_velocity_world_m_s",relative);
            failure.initCause(nonfiniteSpeed);throw failure;
        }
        double scale=((-0.5*airDensityKgM3)*effectiveDragAreaM2)*speed;
        if(!Double.isFinite(scale)) throw error("NONFINITE_RECOVERY_FORCE","recovery_force_scale",scale);
        double[] force=new double[3];
        for(int i=0;i<3;i++) {
            force[i]=scale*relative.get(i);
            if(!Double.isFinite(force[i]))
                throw error("NONFINITE_RECOVERY_FORCE","recovery_force_world_N["+i+"]",force[i]);
        }
        return Vector.of(force);
    }
    private static AerodynamicEvaluationError error(String code,String field,Object value) {
        return new AerodynamicEvaluationError(code,field,value);
    }
}
