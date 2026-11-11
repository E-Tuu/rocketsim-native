package org.roketsim.core.simulation;
/** NAT-021 semantic execution/result-integrity failure only. */
public final class SimulationResultError extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    private final String errorCode,fieldName; private final Object value;
    public SimulationResultError(String code,String field,Object value) { super(code+": "+field+"="+value); this.errorCode=code; this.fieldName=field; this.value=value; }
    public String errorCode() { return errorCode; }
    public String fieldName() { return fieldName; }
    public Object value() { return value; }
}
