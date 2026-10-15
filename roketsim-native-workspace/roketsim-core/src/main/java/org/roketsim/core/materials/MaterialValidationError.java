package org.roketsim.core.materials;

/** Python material construction semantic error; raw nonfinite generic kalır. */
public final class MaterialValidationError extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    /** Structured code. */
    private final String errorCode;
    /** Python alan adı. */
    private final String fieldName;
    /** Reddedilen name/density değeri. */
    private final Object value;
    /** @param errorCode source code
     * @param fieldName source alan
     * @param value reddedilen değer */
    public MaterialValidationError(String errorCode,String fieldName,Object value) {
        super(errorCode+": "+fieldName+"="+value);
        this.errorCode=errorCode; this.fieldName=fieldName; this.value=value;
    }
    /** @return source code */
    public String errorCode() { return errorCode; }
    /** @return source alan */
    public String fieldName() { return fieldName; }
    /** @return reddedilen değer */
    public Object value() { return value; }
}
