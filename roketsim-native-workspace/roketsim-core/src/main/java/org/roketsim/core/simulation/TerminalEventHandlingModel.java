package org.roketsim.core.simulation;
/** Accepted NAT-020 explicit lifecycle selector. */
public enum TerminalEventHandlingModel {
    REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT("reject_interior_candidate_accept_endpoint");
    private final String value;
    TerminalEventHandlingModel(String value) { this.value=value; }
    public String value() { return value; }
}
