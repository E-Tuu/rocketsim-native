package org.roketsim.core.mass;

import java.util.HashSet;
import java.util.List;
import org.roketsim.core.propulsion.MotorInertiaModelKind;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Vehicle tensor about authoritative axial CG in BODY axes; no general off-axis CG claim. */
public record VehicleInertiaResult(double motorTimeS,double totalMassKg,double totalCgXGeoM,
        InertiaTensor3D inertiaAtVehicleCgBody,MotorInertiaModelKind motorInertiaModelKind,
        String motorInertiaProvenanceReference,List<String> includedComponentIds) {
    public VehicleInertiaResult {
        VehicleInertiaEvaluator.nonnegative(motorTimeS,"motorTimeS");
        VehicleInertiaEvaluator.positive(totalMassKg,"totalMassKg");
        requireFinite(totalCgXGeoM,"totalCgXGeoM");
        VehicleInertiaEvaluator.required(inertiaAtVehicleCgBody,"inertiaAtVehicleCgBody");
        VehicleInertiaEvaluator.required(motorInertiaModelKind,"motorInertiaModelKind");
        VehicleInertiaEvaluator.text(motorInertiaProvenanceReference,"motorInertiaProvenanceReference");
        VehicleInertiaEvaluator.required(includedComponentIds,"includedComponentIds");
        var ids=new HashSet<String>();
        for(String id:includedComponentIds) {
            VehicleInertiaEvaluator.text(id,"includedComponentIds entry");
            if(!ids.add(id))throw new IllegalArgumentException("duplicate includedComponentIds: "+id);
        }
        includedComponentIds=List.copyOf(includedComponentIds);
    }
}
