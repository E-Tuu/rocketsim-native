package org.roketsim.core.geometry;

/** Python FinAngularArrangement seçenekleri; implicit string dönüşümü yoktur. */
public enum FinAngularArrangement {
    /** Kabul edilmiş equally_spaced seçimi. */
    EQUALLY_SPACED("equally_spaced");
    private final String value;
    FinAngularArrangement(String value) { this.value=value; }
    /** @return kabul edilmiş Python enum değeri */
    public String value() { return value; }
}
