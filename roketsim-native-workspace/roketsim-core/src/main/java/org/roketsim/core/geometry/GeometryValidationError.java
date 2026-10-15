package org.roketsim.core.geometry;

/** Python finite/derived geometry invariant hatası; raw nonfinite'dan ayrıdır. */
public final class GeometryValidationError extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    /** Python structured code. */
    private final String errorCode;
    /** Python alan adı veya null. */
    private final String fieldName;
    /** Reddedilen scalar/selector veya null. */
    private final Object value;
    /** @param errorCode accepted code
     * @param fieldName alan veya null
     * @param value raw veya derived değer */
    public GeometryValidationError(String errorCode,String fieldName,Object value) {
        super(errorCode+": "+fieldName+"="+value);
        this.errorCode=errorCode; this.fieldName=fieldName; this.value=value;
    }
    /** @return code */
    public String errorCode() { return errorCode; }
    /** @return alan veya null */
    public String fieldName() { return fieldName; }
    /** @return reddedilen değer */
    public Object value() { return value; }
}
