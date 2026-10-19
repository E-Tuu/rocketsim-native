package org.roketsim.core.simulation;

/** NAT-019 chronology failure, without mutation of the failed insertion. */
public final class FlightRecordingError extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    private final String errorCode,fieldName; private final Object value;
    public FlightRecordingError(String code,String field,Object value) { super(code+": "+field+"="+value); this.errorCode=code; this.fieldName=field; this.value=value; }
    public String errorCode() { return errorCode; }
    public String fieldName() { return fieldName; }
    public Object value() { return value; }
}
