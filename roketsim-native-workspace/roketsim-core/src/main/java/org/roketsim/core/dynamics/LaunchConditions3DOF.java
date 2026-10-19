package org.roketsim.core.dynamics;
import java.util.List;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;
import org.roketsim.core.math.NumericalValidation;

/** Explicit initial WORLD values and fixed launch direction; never normalizes supplied input. */
public record LaunchConditions3DOF(Vector initialPositionWorldM,Vector initialVelocityWorldMS,Vector launchDirectionWorldUnit) {
    /** Accepted NAT-013 local validation policy, not a global numerical default. */
    public static final double LAUNCH_DIRECTION_UNIT_NORM_ATOL=1e-8;
    public LaunchConditions3DOF {
        initialPositionWorldM=Vector.of(initialPositionWorldM,3,"initial_position_world_m");
        initialVelocityWorldMS=Vector.of(initialVelocityWorldMS,3,"initial_velocity_world_m_s");
        launchDirectionWorldUnit=Vector.of(launchDirectionWorldUnit,3,"launch_direction_world_unit");
        double norm=Vectors.magnitude(launchDirectionWorldUnit);
        var value=List.of(launchDirectionWorldUnit.get(0),launchDirectionWorldUnit.get(1),launchDirectionWorldUnit.get(2));
        if(NumericalValidation.isNearZero(norm,0)) throw new InitialStateValidationError("INVALID_LAUNCH_DIRECTION","launch_direction_world_unit",value);
        if(Math.abs(norm-1.0)>LAUNCH_DIRECTION_UNIT_NORM_ATOL) throw new InitialStateValidationError("NON_UNIT_LAUNCH_DIRECTION","launch_direction_world_unit",value);
    }
    public LaunchConditions3DOF(Object position,Object velocity,Object direction) {
        this(Vector.of(position,3,"initial_position_world_m"),Vector.of(velocity,3,"initial_velocity_world_m_s"),Vector.of(direction,3,"launch_direction_world_unit"));
    }
}
