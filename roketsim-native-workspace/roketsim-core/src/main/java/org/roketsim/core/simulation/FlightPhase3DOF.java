package org.roketsim.core.simulation;

/** Explicit physical phase classification; no transition or propulsion policy. */
public enum FlightPhase3DOF {
    /** Contact with the launch support. */
    PAD_CONTACT,
    /** Constrained by a launch guide. */
    GUIDED_FLIGHT,
    /** Flight without a launch-guide constraint. */
    FREE_FLIGHT,
    /** Descent with an active recovery system. */
    RECOVERY_DESCENT,
    /** Accepted landed phase. */
    LANDED
}
