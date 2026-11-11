package org.roketsim.core.simulation;

/** Local guide/configuration/numerical error; no change to existing upstream errors. */
public final class LaunchGuideError extends IllegalArgumentException {
    private final String errorCode,fieldName;
    private final Object value;
    public LaunchGuideError(String code,String field,Object value) {
        super(code+": "+field+"="+value); errorCode=code;fieldName=field;this.value=value;
    }
    public String errorCode() { return errorCode; }
    public String fieldName() { return fieldName; }
    public Object value() { return value; }
    static void required(Object value,String field) {
        if(value==null) throw new LaunchGuideError("MISSING_GUIDE_INPUT",field,null);
    }
    static void text(String value,String field) {
        if(value==null || value.isBlank()) throw new LaunchGuideError("MISSING_GUIDE_PROVENANCE",field,value);
    }
    static double finite(double value,String field) {
        if(!Double.isFinite(value)) throw new LaunchGuideError("NONFINITE_GUIDE_VALUE",field,value);
        return value;
    }
}
