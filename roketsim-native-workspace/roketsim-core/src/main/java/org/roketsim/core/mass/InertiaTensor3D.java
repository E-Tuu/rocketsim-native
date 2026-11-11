package org.roketsim.core.mass;

import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Symmetric BODY-aligned inertia tensor; reference point is the caller's responsibility.
 * Off-diagonals are tensor entries, e.g. Ixy = -integral(x*y dm).
 * Finite entries and nonnegative diagonals do not certify PSD or physical realizability.
 * @param ixxKgM2 X-axis moment (kg*m^2)
 * @param iyyKgM2 Y-axis moment (kg*m^2)
 * @param izzKgM2 Z-axis moment (kg*m^2)
 * @param ixyKgM2 symmetric XY entry (kg*m^2)
 * @param ixzKgM2 symmetric XZ entry (kg*m^2)
 * @param iyzKgM2 symmetric YZ entry (kg*m^2)
 */
public record InertiaTensor3D(double ixxKgM2,double iyyKgM2,double izzKgM2,
        double ixyKgM2,double ixzKgM2,double iyzKgM2) {
    /** Validates in declaration order without projecting or normalizing entries. */
    public InertiaTensor3D {
        requireFinite(ixxKgM2,"ixxKgM2");
        if(ixxKgM2<0)throw new IllegalArgumentException("ixxKgM2 must be nonnegative");
        requireFinite(iyyKgM2,"iyyKgM2");
        if(iyyKgM2<0)throw new IllegalArgumentException("iyyKgM2 must be nonnegative");
        requireFinite(izzKgM2,"izzKgM2");
        if(izzKgM2<0)throw new IllegalArgumentException("izzKgM2 must be nonnegative");
        requireFinite(ixyKgM2,"ixyKgM2");
        requireFinite(ixzKgM2,"ixzKgM2");
        requireFinite(iyzKgM2,"iyzKgM2");
    }
}
