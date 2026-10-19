package org.roketsim.core.dynamics;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Ephemeral already-evaluated physics snapshot; does not calculate upstream quantities. */
public record TranslationalDynamicsInputs(double massKg,double thrustN,Vector relativeVelocityWorldMS,
        double dynamicPressurePa,double referenceAreaM2,double dragCoefficientCd0,Vector gravityAccelerationWorldMS2) {
    public TranslationalDynamicsInputs {
        massKg=scalar(massKg,"mass_kg","INVALID_MASS",true);
        thrustN=scalar(thrustN,"thrust_N","INVALID_THRUST",false);
        relativeVelocityWorldMS=Vector.of(relativeVelocityWorldMS,3,"relative_velocity_world_m_s");
        dynamicPressurePa=scalar(dynamicPressurePa,"dynamic_pressure_Pa","INVALID_DYNAMIC_PRESSURE",false);
        referenceAreaM2=scalar(referenceAreaM2,"reference_area_m2","INVALID_REFERENCE_AREA",true);
        dragCoefficientCd0=scalar(dragCoefficientCd0,"drag_coefficient_cd0","INVALID_DRAG_COEFFICIENT",false);
        gravityAccelerationWorldMS2=Vector.of(gravityAccelerationWorldMS2,3,"gravity_acceleration_world_m_s2");
    }
    public TranslationalDynamicsInputs(double mass,double thrust,Object relative,double pressure,double area,double cd0,Object gravity) {
        this(scalar(mass,"mass_kg","INVALID_MASS",true),scalar(thrust,"thrust_N","INVALID_THRUST",false),
            Vector.of(relative,3,"relative_velocity_world_m_s"),scalar(pressure,"dynamic_pressure_Pa","INVALID_DYNAMIC_PRESSURE",false),
            scalar(area,"reference_area_m2","INVALID_REFERENCE_AREA",true),scalar(cd0,"drag_coefficient_cd0","INVALID_DRAG_COEFFICIENT",false),
            Vector.of(gravity,3,"gravity_acceleration_world_m_s2"));
    }
    private static double scalar(double value,String field,String code,boolean positive) {
        double canonical=requireFinite(value,field);
        if(positive?canonical<=0:canonical<0) throw new DynamicsEvaluationError(code,field,canonical);
        return canonical;
    }
}
