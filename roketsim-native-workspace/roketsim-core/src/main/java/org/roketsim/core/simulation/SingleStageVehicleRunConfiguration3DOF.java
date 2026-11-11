package org.roketsim.core.simulation;

import java.util.HashSet;
import java.util.List;
import org.roketsim.core.mass.AttachedHardwareMass;

/** Attached hardware stays aboard throughout the run; no deployment/separation physics.
 * Different IDs do not prove different physical ownership: callers must avoid structural/motor/hardware double counting.
 * @param baseRun accepted run configuration, retained without a new deep-copy policy
 * @param attachedHardware ordered immutable snapshot, with unique exact IDs
 */
public record SingleStageVehicleRunConfiguration3DOF(
        SimulationRunConfiguration3DOF baseRun,List<AttachedHardwareMass> attachedHardware) {
    public SingleStageVehicleRunConfiguration3DOF {
        if(baseRun==null) throw new IllegalArgumentException("baseRun must not be null");
        if(attachedHardware==null) throw new IllegalArgumentException("attachedHardware must not be null");
        var ids=new HashSet<String>();
        for(int i=0;i<attachedHardware.size();i++) {
            var hardware=attachedHardware.get(i);
            if(hardware==null) throw new IllegalArgumentException("attachedHardware["+i+"] must not be null");
            if(!ids.add(hardware.componentId())) {
                throw new IllegalArgumentException("attachedHardware["+i+"].componentId is duplicated: "+hardware.componentId());
            }
        }
        attachedHardware=List.copyOf(attachedHardware);
    }
}
