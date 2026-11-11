package org.roketsim.core.propulsion;
import java.util.List;
/** Read-only numerical projection. Neither certification nor ejection selects a force law. */
public sealed interface MotorPhysicsInput permits MotorPhysicsInputs.LegacyMotorView,SourceBackedMotorProfile {
    double diameterM();
    double lengthM();
    double initialMassKg();
    double propellantMassKg();
    List<ThrustSample> thrustCurve();
    List<MotorMassSample> massCurve();
    List<MotorCgSample> cgCurve();
}
