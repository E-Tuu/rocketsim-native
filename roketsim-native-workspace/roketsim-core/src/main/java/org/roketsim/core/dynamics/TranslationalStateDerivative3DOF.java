package org.roketsim.core.dynamics;
import org.roketsim.core.math.Vector;

/** WORLD position and velocity rates; independent immutable finite 3-vectors. */
public record TranslationalStateDerivative3DOF(Vector positionDerivativeWorldMS,Vector velocityDerivativeWorldMS2) {
    public TranslationalStateDerivative3DOF {
        positionDerivativeWorldMS=Vector.of(positionDerivativeWorldMS,3,"position_derivative_world_m_s");
        velocityDerivativeWorldMS2=Vector.of(velocityDerivativeWorldMS2,3,"velocity_derivative_world_m_s2");
    }
    public TranslationalStateDerivative3DOF(Object positionRate,Object velocityRate) {
        this(Vector.of(positionRate,3,"position_derivative_world_m_s"),Vector.of(velocityRate,3,"velocity_derivative_world_m_s2"));
    }
}
