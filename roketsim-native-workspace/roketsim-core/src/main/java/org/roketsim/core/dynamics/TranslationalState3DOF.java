package org.roketsim.core.dynamics;
import org.roketsim.core.math.Vector;

/** Timeless local WORLD ENU position (m) and velocity (m/s); WORLD z is not altitude. */
public record TranslationalState3DOF(Vector positionWorldM,Vector velocityWorldMS) {
    public TranslationalState3DOF {
        positionWorldM=Vector.of(positionWorldM,3,"position_world_m");
        velocityWorldMS=Vector.of(velocityWorldMS,3,"velocity_world_m_s");
    }
    public TranslationalState3DOF(Object positionWorldM,Object velocityWorldMS) {
        this(Vector.of(positionWorldM,3,"position_world_m"),Vector.of(velocityWorldMS,3,"velocity_world_m_s"));
    }
}
