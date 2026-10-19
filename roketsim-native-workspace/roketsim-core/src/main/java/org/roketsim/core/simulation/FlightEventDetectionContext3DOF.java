package org.roketsim.core.simulation;
import org.roketsim.core.dynamics.LaunchConditions3DOF;
import org.roketsim.core.propulsion.MotorCurveStatistics;

/** Reference-preserving event authorities; no duplicate ignition or ground datum. */
public record FlightEventDetectionContext3DOF(LaunchConditions3DOF launchConditions,
        PropulsionTimeline propulsionTimeline,MotorCurveStatistics motorCurveStatistics,
        FlightEventDetectionProfile3DOF modelProfile) { }
