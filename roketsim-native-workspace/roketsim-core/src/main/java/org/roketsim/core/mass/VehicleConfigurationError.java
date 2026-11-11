package org.roketsim.core.mass;

/** A failure of the opt-in vehicle preparation or measured-property contract. */
public final class VehicleConfigurationError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final String fieldName;
    private final String detail;

    public VehicleConfigurationError(String errorCode, String fieldName, String detail) {
        super(errorCode + ": " + fieldName + " (" + detail + ")");
        this.errorCode = errorCode;
        this.fieldName = fieldName;
        this.detail = detail;
    }

    public String errorCode() { return errorCode; }
    public String fieldName() { return fieldName; }
    public String detail() { return detail; }
}
