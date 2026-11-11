package org.roketsim.core.mass;

import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless 3D parallel-axis translation; no tensor rotation, geometry or aggregation. */
public final class ParallelAxisInertiaCalculator {
    /** Creates a stateless calculator. */
    public ParallelAxisInertiaCalculator() {}

    /** Applies I_ref = I_centroid + m*((d dot d)*Identity - d*d^T).
     * @param centroidalInertia supplied tensor about the centroid in BODY-aligned axes
     * @param massKg explicit finite strictly positive mass (kg)
     * @param displacementBodyM centroid/reference displacement in the same BODY axes (m), not raw x_geo
     * @return new tensor about the parallel reference axes (kg*m^2)
     * @throws IllegalArgumentException for null, invalid mass/dimension or nonfinite derived entries
     */
    public InertiaTensor3D shiftFromCentroid(InertiaTensor3D centroidalInertia,double massKg,
            Vector displacementBodyM) {
        if(centroidalInertia==null)throw new IllegalArgumentException("centroidalInertia must not be null");
        requireFinite(massKg,"massKg");
        if(massKg<=0)throw new IllegalArgumentException("massKg must be strictly positive");
        if(displacementBodyM==null)throw new IllegalArgumentException("displacementBodyM must not be null");
        if(displacementBodyM.size()!=3)throw new IllegalArgumentException("displacementBodyM must have shape (3,)");
        double dx=displacementBodyM.get(0),dy=displacementBodyM.get(1),dz=displacementBodyM.get(2);
        double ixx=requireFinite(centroidalInertia.ixxKgM2()+massKg*(dy*dy+dz*dz),"ixxKgM2");
        double iyy=requireFinite(centroidalInertia.iyyKgM2()+massKg*(dx*dx+dz*dz),"iyyKgM2");
        double izz=requireFinite(centroidalInertia.izzKgM2()+massKg*(dx*dx+dy*dy),"izzKgM2");
        double ixy=requireFinite(centroidalInertia.ixyKgM2()-massKg*dx*dy,"ixyKgM2");
        double ixz=requireFinite(centroidalInertia.ixzKgM2()-massKg*dx*dz,"ixzKgM2");
        double iyz=requireFinite(centroidalInertia.iyzKgM2()-massKg*dy*dz,"iyzKgM2");
        return new InertiaTensor3D(ixx,iyy,izz,ixy,ixz,iyz);
    }
}
