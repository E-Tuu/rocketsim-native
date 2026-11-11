package org.roketsim.core.dynamics;

/** Stateless position/velocity mapping only; no time, force or environment operations. */
public final class InitialStateBuilder {
    public TranslationalState3DOF build(LaunchConditions3DOF launchConditions) {
        return new TranslationalState3DOF(launchConditions.initialPositionWorldM(),launchConditions.initialVelocityWorldMS());
    }
}
