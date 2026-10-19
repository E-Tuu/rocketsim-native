package org.roketsim.core.simulation;

/** Only NAT-015 mapping/timeline semantic errors; upstream failures are not wrapped. */
public final class PhysicsEvaluationError extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    private final String errorCode;
    private final String fieldName;
    private final Object value;
    public PhysicsEvaluationError(String errorCode,String fieldName,Object value) {
        super(errorCode+": "+fieldName+"="+value);
        this.errorCode=errorCode; this.fieldName=fieldName; this.value=value;
    }
    public String errorCode() { return errorCode; }
    public String fieldName() { return fieldName; }
    public Object value() { return value; }
}
