package org.roketsim.core.simulation;

import org.roketsim.core.propulsion.MotorCurveStatistics;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless NAT-025 clock classification; no force evaluation, events or transitions. */
public final class FlightPhaseClockEvaluator3DOF {
    /** Creates a stateless evaluator. */
    public FlightPhaseClockEvaluator3DOF() {}

    /** Classifies absolute time using the accepted ignition and curve-end authorities.
     * @param absoluteTimeS finite absolute simulation time (s)
     * @param flightPhase explicit phase, preserved without transition
     * @param propulsionTimeline sole ignition-time authority
     * @param motorStatistics source-curve endpoint authority
     * @return instantaneous phase and clock snapshot
     * @throws IllegalArgumentException for null inputs, invalid curve end or invalid derived times
     */
    public FlightPhaseClockState3DOF evaluate(double absoluteTimeS,FlightPhase3DOF flightPhase,
            PropulsionTimeline propulsionTimeline,MotorCurveStatistics motorStatistics) {
        requireFinite(absoluteTimeS,"absolute_time_s");
        if(flightPhase==null)throw new IllegalArgumentException("flight_phase must not be null");
        if(propulsionTimeline==null)throw new IllegalArgumentException("propulsion_timeline must not be null");
        if(motorStatistics==null)throw new IllegalArgumentException("motor_statistics must not be null");
        double ignition=propulsionTimeline.ignitionTimeS();
        double curveEnd=motorStatistics.curveEndTimeS();
        requireFinite(curveEnd,"curve_end_time_s");
        if(curveEnd<=0)throw new IllegalArgumentException("curve_end_time_s must be strictly positive; got "+curveEnd);
        double motorTime=absoluteTimeS-ignition;
        requireFinite(motorTime,"motor_time_s");
        double burnout=ignition+curveEnd;
        requireFinite(burnout,"burnout_time_s");
        if(burnout<=ignition)throw new IllegalArgumentException("burnout_time_s must be strictly greater than ignition_time_s");
        var status=absoluteTimeS<ignition?PropulsionStatus3DOF.PRE_IGNITION:
            absoluteTimeS<burnout?PropulsionStatus3DOF.CURVE_ACTIVE:PropulsionStatus3DOF.POST_BURNOUT;
        return new FlightPhaseClockState3DOF(flightPhase,status,motorTime,burnout);
    }
}
