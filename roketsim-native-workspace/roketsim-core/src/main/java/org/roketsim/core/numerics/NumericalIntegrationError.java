package org.roketsim.core.numerics;

/** Structured finite numerical-policy error; raw nonfinite inputs remain generic. */
public final class NumericalIntegrationError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final String fieldName;
    private final Object value;

    public NumericalIntegrationError(String errorCode, String fieldName, Object value) {
        super(errorCode + ": " + fieldName + "=" + value);
        this.errorCode = errorCode;
        this.fieldName = fieldName;
        this.value = value;
    }
    public String errorCode() { return errorCode; }
    public String fieldName() { return fieldName; }
    public Object value() { return value; }
}
