package org.roketsim.core.dynamics;
import org.roketsim.core.math.Vector;

/** Only thrust, drag and gravity are stored authorities; net is derived in source order. */
public record TranslationalForces3DOF(Vector thrustForceWorldN,Vector dragForceWorldN,Vector gravityForceWorldN) {
    public TranslationalForces3DOF {
        thrustForceWorldN=Vector.of(thrustForceWorldN,3,"thrust_force_world_N");
        dragForceWorldN=Vector.of(dragForceWorldN,3,"drag_force_world_N");
        gravityForceWorldN=Vector.of(gravityForceWorldN,3,"gravity_force_world_N");
    }
    public TranslationalForces3DOF(Object thrust,Object drag,Object gravity) {
        this(Vector.of(thrust,3,"thrust_force_world_N"),Vector.of(drag,3,"drag_force_world_N"),Vector.of(gravity,3,"gravity_force_world_N"));
    }
    public Vector netForceWorldN() {
        double[] result=new double[3];
        for(int i=0;i<3;i++) result[i]=(thrustForceWorldN.get(i)+dragForceWorldN.get(i))+gravityForceWorldN.get(i);
        return Vector.of(result,3,"net_force_world_N");
    }
}
