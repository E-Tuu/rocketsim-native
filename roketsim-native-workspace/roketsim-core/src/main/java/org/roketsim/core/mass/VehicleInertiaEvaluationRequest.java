package org.roketsim.core.mass;

import java.util.List;
import org.roketsim.core.propulsion.MotorInstallation;
import org.roketsim.core.propulsion.MotorPropertyModelProfile;
import org.roketsim.core.propulsion.MotorInertiaModel;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Explicit instantaneous request; totals must come from the same accepted vehicle mass snapshot.
 * Lists are immutable snapshots; existing immutable upstream references are retained.
 */
public record VehicleInertiaEvaluationRequest(StructuralMassProperties structuralMassProperties,
        StructuralComponentInertias structuralComponentInertias,MotorInstallation motorInstallation,
        MotorPropertyModelProfile motorPropertyModelProfile,MotorInertiaModel motorInertiaModel,
        double motorTimeS,List<AttachedHardwareMass> attachedHardwareMasses,
        List<AttachedHardwareInertia> attachedHardwareInertias,double authoritativeTotalMassKg,
        double authoritativeCgXGeoM,double massConsistencyToleranceKg,double cgConsistencyToleranceM) {
    public VehicleInertiaEvaluationRequest {
        VehicleInertiaEvaluator.required(structuralMassProperties,"structuralMassProperties");
        VehicleInertiaEvaluator.required(structuralComponentInertias,"structuralComponentInertias");
        VehicleInertiaEvaluator.required(motorInstallation,"motorInstallation");
        VehicleInertiaEvaluator.required(motorInstallation.motor(),"motorInstallation.motor");
        VehicleInertiaEvaluator.required(motorPropertyModelProfile,"motorPropertyModelProfile");
        VehicleInertiaEvaluator.required(motorInertiaModel,"motorInertiaModel");
        VehicleInertiaEvaluator.nonnegative(motorTimeS,"motorTimeS");
        VehicleInertiaEvaluator.required(attachedHardwareMasses,"attachedHardwareMasses");
        VehicleInertiaEvaluator.required(attachedHardwareInertias,"attachedHardwareInertias");
        for(int i=0;i<attachedHardwareMasses.size();i++)
            VehicleInertiaEvaluator.required(attachedHardwareMasses.get(i),"attachedHardwareMasses["+i+"]");
        for(int i=0;i<attachedHardwareInertias.size();i++)
            VehicleInertiaEvaluator.required(attachedHardwareInertias.get(i),"attachedHardwareInertias["+i+"]");
        VehicleInertiaEvaluator.positive(authoritativeTotalMassKg,"authoritativeTotalMassKg");
        requireFinite(authoritativeCgXGeoM,"authoritativeCgXGeoM");
        VehicleInertiaEvaluator.nonnegative(massConsistencyToleranceKg,"massConsistencyToleranceKg");
        VehicleInertiaEvaluator.nonnegative(cgConsistencyToleranceM,"cgConsistencyToleranceM");
        attachedHardwareMasses=List.copyOf(attachedHardwareMasses);
        attachedHardwareInertias=List.copyOf(attachedHardwareInertias);
    }
}
