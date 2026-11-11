package org.roketsim.core.environment;

/** Finite fakat pozitif olmayan prerequisite; Python field_name/value ayrımını korur. */
public final class AirPropertiesDomainError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** Python prerequisite alan adı; serialization verisi. */
    private final String fieldName;
    /** Reddedilen prerequisite; serialization verisi. */
    private final double value;
    /** @param fieldName Python alan adı
     * @param value reddedilen finite değer */
    public AirPropertiesDomainError(String fieldName, double value) {
        super(fieldName + " must be strictly positive; got " + value);
        this.fieldName = fieldName;
        this.value = value;
    }
    /** @return Python alan adı */
    public String fieldName() { return fieldName; }
    /** @return reddedilen değer */
    public double value() { return value; }
}
