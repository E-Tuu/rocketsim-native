package org.roketsim.core.propulsion;
/** Instantaneous scalar thrust and impulse since ignition, N and N s. */
public record MotorThrustState(double thrustN,double cumulativeImpulseNS) { }
