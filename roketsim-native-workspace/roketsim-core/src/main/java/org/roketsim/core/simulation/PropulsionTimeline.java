package org.roketsim.core.simulation;

import org.roketsim.core.math.NumericalValidation;

/** Explicit ignition-time authority in seconds; finite negative ignition time is valid. */
public record PropulsionTimeline(double ignitionTimeS) {
    public PropulsionTimeline {
        NumericalValidation.requireFinite(ignitionTimeS,"ignition_time_s");
    }
}
