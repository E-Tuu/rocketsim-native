package org.roketsim.core.propulsion;
import java.util.List;
import java.util.HashSet;
import java.util.NoSuchElementException;
/** Explicit immutable selection, no fallback motor. */
public record MotorCatalog(List<MotorDefinition> motors) {
    public MotorCatalog {
        if(motors==null || ((List<?>)motors).stream().anyMatch(m->!(m instanceof MotorDefinition))) { throw new IllegalArgumentException("motors must be a List of MotorDefinition"); }
        var ids=new HashSet<String>();
        for(MotorDefinition motor:motors) { if(!ids.add(motor.motorId())) { throw new IllegalArgumentException("duplicate motor_id in MotorCatalog"); } }
        motors=List.copyOf(motors);
    }
    public MotorDefinition get(String motorId) {
        for(MotorDefinition motor:motors) { if(motor.motorId().equals(motorId)) { return motor; } }
        throw new NoSuchElementException(motorId);
    }
}
