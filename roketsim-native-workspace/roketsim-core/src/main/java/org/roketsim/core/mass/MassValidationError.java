package org.roketsim.core.mass;

/** Derived mass invariant hatası; source'taki nonfinite derived ayrımını korur. */
public final class MassValidationError extends IllegalArgumentException {
    private static final long serialVersionUID=1L;
    /** Accepted code. */
    private final String errorCode;
    /** Python alan adı. */
    private final String fieldName;
    /** Derived mass/CG/extent; hata metadata'sında nonfinite olabilir. */
    private final double value;
    /** @param errorCode source code
     * @param fieldName source alan
     * @param value reddedilen derived değer */
    public MassValidationError(String errorCode,String fieldName,double value) {
        super(errorCode+": "+fieldName+"="+value);
        this.errorCode=errorCode; this.fieldName=fieldName; this.value=value;
    }
    /** @return source code */
    public String errorCode() { return errorCode; }
    /** @return source alan */
    public String fieldName() { return fieldName; }
    /** @return reddedilen derived değer */
    public double value() { return value; }
}
