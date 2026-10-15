package org.roketsim.core.geometry;

/** Python ReferenceGeometryPolicy seçenekleri; implicit string dönüşümü yoktur. */
public enum ReferenceGeometryPolicy {
    /** Kabul edilmiş maximum_diameter seçimi. */
    MAXIMUM_DIAMETER("maximum_diameter");
    private final String value;
    ReferenceGeometryPolicy(String value) { this.value=value; }
    /** @return kabul edilmiş Python enum değeri */
    public String value() { return value; }
}
