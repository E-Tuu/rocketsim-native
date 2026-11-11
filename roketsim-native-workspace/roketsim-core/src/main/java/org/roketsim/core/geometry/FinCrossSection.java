package org.roketsim.core.geometry;

/** Python FinCrossSection seçenekleri; implicit string dönüşümü yoktur. */
public enum FinCrossSection {
    /** Kabul edilmiş square seçimi. */
    SQUARE("square");
    private final String value;
    FinCrossSection(String value) { this.value=value; }
    /** @return kabul edilmiş Python enum değeri */
    public String value() { return value; }
}
