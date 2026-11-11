package org.roketsim.core.propulsion;
/** Explicit motor inertia policy, separate from accepted mass/CG profiles. */
public sealed interface MotorInertiaModel permits MotorInertiaCurveModel,
        RadialGrainMotorInertiaModel,UniformCylinderMotorInertiaModel {}
