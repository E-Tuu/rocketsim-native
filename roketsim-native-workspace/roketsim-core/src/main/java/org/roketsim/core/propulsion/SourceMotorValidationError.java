package org.roketsim.core.propulsion;
/** Local source-binding validation; no alteration of legacy errors or global error architecture. */
public final class SourceMotorValidationError extends IllegalArgumentException {
    private final String errorCode,fieldName;
    public SourceMotorValidationError(String code,String field) {super(code+": "+field);errorCode=code;fieldName=field;}
    public String errorCode() {return errorCode;}
    public String fieldName() {return fieldName;}
}
