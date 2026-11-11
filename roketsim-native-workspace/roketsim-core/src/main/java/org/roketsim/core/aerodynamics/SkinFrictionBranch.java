package org.roketsim.core.aerodynamics;

/** Accepted Python SkinFrictionBranch selector; no additional model choices. */
public enum SkinFrictionBranch {
    SMOOTH("smooth"), ROUGHNESS_LIMITED("roughness_limited");
    private final String value;
    SkinFrictionBranch(String value) { this.value=value; }
    public String value() { return value; }
}
