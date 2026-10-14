package org.roketsim.core.flightconditions;

/** Finite fakat pozitif olmayan prerequisite; raw nonfinite generic hatadan ayrıdır. */
public final class FlightConditionsDomainError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** Python prerequisite alan adı; serialization verisi. */
    private final String fieldName;
    /** Reddedilen prerequisite; serialization verisi. */
    private final double value;
    /** @param fieldName Python alan adı
     * @param value reddedilen finite değer */
    public FlightConditionsDomainError(String fieldName, double value) {
        super(fieldName + " must be strictly positive; got " + value);
        this.fieldName=fieldName; this.value=value;
    }
    /** @return Python alan adı */
    public String fieldName() { return fieldName; }
    /** @return reddedilen değer */
    public double value() { return value; }
}
