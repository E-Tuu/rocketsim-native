package org.roketsim.core.simulation;

/** NAT-015 selector: accepted by enum identity, never by canonical-string equality. */
public enum EnvironmentPositionMappingModel {
    LOCAL_ENU_VERTICAL_OFFSET("local_enu_vertical_offset");
    private final String value;
    EnvironmentPositionMappingModel(String value) { this.value=value; }
    public String value() { return value; }
}
