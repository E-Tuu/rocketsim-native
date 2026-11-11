package org.roketsim.core.propulsion;
public enum MotorType {
    SINGLE_USE,
    RELOADABLE;
    public String value() { return this==SINGLE_USE?"single_use":"reloadable"; }
}
