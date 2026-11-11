package org.roketsim.core.simulation;

/** Instantaneous immutable snapshot derived and validated by the clock evaluator.
 * @param flightPhase explicitly supplied physical phase
 * @param propulsionStatus source-curve time classification
 * @param motorTimeS elapsed seconds since ignition, possibly negative
 * @param burnoutTimeS absolute simulation time of the source-curve endpoint (s)
 */
public record FlightPhaseClockState3DOF(
    FlightPhase3DOF flightPhase,
    PropulsionStatus3DOF propulsionStatus,
    double motorTimeS,
    double burnoutTimeS
) {}
