package org.roketsim.core.propulsion;
import java.util.Optional;
import java.util.OptionalDouble;
/** Source semantics only. Does not schedule recovery or manufacture a legacy raw token. */
public record MotorEjectionMetadata(Kind kind,OptionalDouble delayS,Optional<String> rawSourceToken,String sourceReference) {
    public enum Kind { TIMED,PLUGGED,UNKNOWN }
    public MotorEjectionMetadata {
        if(kind==null || delayS==null || rawSourceToken==null || sourceReference==null || sourceReference.isBlank())
            throw new IllegalArgumentException("ejection kind, optional values and sourceReference are required");
        if((kind==Kind.TIMED)!=delayS.isPresent())throw new IllegalArgumentException("only TIMED must have a physical delay");
        if(delayS.isPresent() && (!Double.isFinite(delayS.getAsDouble()) || delayS.getAsDouble()<0))
            throw new IllegalArgumentException("ejection delayS must be finite and nonnegative");
        if(rawSourceToken.isPresent() && rawSourceToken.orElseThrow().isBlank())throw new IllegalArgumentException("rawSourceToken must not be blank");
    }
    public static MotorEjectionMetadata fromLegacy(MotorDefinition motor) {
        return new MotorEjectionMetadata(Kind.TIMED,OptionalDouble.of(motor.ejectionDelayS()),Optional.empty(),"legacy:"+motor.motorId()+".ejectionDelayS");
    }
}
