package org.roketsim.core.geometry;

/** Python NoseConstructionMode seçenekleri; implicit string dönüşümü yoktur. */
public enum NoseConstructionMode {
    /** Kabul edilmiş SOLID seçimi. */
    SOLID("SOLID"),
    /** Kabul edilmiş HOLLOW_SHELL seçimi. */
    HOLLOW_SHELL("HOLLOW_SHELL");
    private final String value;
    NoseConstructionMode(String value) { this.value=value; }
    /** @return kabul edilmiş Python enum değeri */
    public String value() { return value; }
}
