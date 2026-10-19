package org.roketsim.core.dynamics;
import org.roketsim.core.math.Vector;

/** Instantaneous force breakdown, derivative and explicit selected profile; no history. */
public record TranslationalDynamicsResult(TranslationalForces3DOF forces,TranslationalStateDerivative3DOF derivative,
        TranslationalDynamicsModelProfile modelProfile) {
    public Vector accelerationWorldMS2() { return derivative.velocityDerivativeWorldMS2(); }
}
