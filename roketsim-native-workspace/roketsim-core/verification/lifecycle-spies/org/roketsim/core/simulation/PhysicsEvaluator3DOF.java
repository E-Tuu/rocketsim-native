package org.roketsim.core.simulation;
import java.util.ArrayList;
import java.util.List;
import org.roketsim.core.dynamics.*;

/** ISOLATED TEST CLASSPATH ONLY: NAT-020 PhysicsSpy, never a production JAR source. */
public final class PhysicsEvaluator3DOF {
    public static int calls,failCall;
    public static final RuntimeException SENTINEL=new RuntimeException("test sentinel");
    public static final List<TranslationalState3DOF> states=new ArrayList<>();
    public PhysicsEvaluator3DOF() { calls=0; states.clear(); }
    public PhysicsEvaluationResult3DOF evaluate(double t,TranslationalState3DOF s,LaunchConditions3DOF l,PhysicsEvaluationContext3DOF c) {
        calls++; states.add(s); if(calls==failCall)throw SENTINEL;
        var derivative=new TranslationalStateDerivative3DOF(new double[]{s.positionWorldM().get(0),0,0},new double[]{0,0,0});
        var zero=org.roketsim.core.math.Vector.of(new double[]{0,0,0});
        return new PhysicsEvaluationResult3DOF(0,0,null,null,zero,zero,null,null,null,null,null,zero,new TranslationalDynamicsResult(null,derivative,null));
    }
}
