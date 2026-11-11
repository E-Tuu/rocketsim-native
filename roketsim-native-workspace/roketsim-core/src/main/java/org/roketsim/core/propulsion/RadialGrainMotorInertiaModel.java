package org.roketsim.core.propulsion;
/** Fixed coaxial annular grains: internal radial burning only; outer/end faces inhibited. */
public record RadialGrainMotorInertiaModel(double dryCgFromFrontM,double dryTransverseInertiaKgM2,
        double dryAxialInertiaKgM2,int grainCount,double grainLengthM,double grainSeparationM,
        double firstGrainFrontFromMotorFrontM,double grainDensityKgM3,double grainOuterRadiusM,
        double grainInitialInnerRadiusM,double initialMassToleranceKg,double motorCgToleranceM,
        String sourceReference) implements MotorInertiaModel {
    public RadialGrainMotorInertiaModel {
        MotorInertiaEvaluator.nonnegative(dryCgFromFrontM,"dryCgFromFrontM");
        MotorInertiaEvaluator.nonnegative(dryTransverseInertiaKgM2,"dryTransverseInertiaKgM2");
        MotorInertiaEvaluator.nonnegative(dryAxialInertiaKgM2,"dryAxialInertiaKgM2");
        if(dryTransverseInertiaKgM2<dryAxialInertiaKgM2/2)
            throw new IllegalArgumentException("dry axisymmetric inertia requires 2*transverse >= axial");
        if(grainCount<1)throw new IllegalArgumentException("grainCount must be at least one");
        MotorInertiaEvaluator.positive(grainLengthM,"grainLengthM");
        MotorInertiaEvaluator.nonnegative(grainSeparationM,"grainSeparationM");
        MotorInertiaEvaluator.nonnegative(firstGrainFrontFromMotorFrontM,"firstGrainFrontFromMotorFrontM");
        MotorInertiaEvaluator.positive(grainDensityKgM3,"grainDensityKgM3");
        MotorInertiaEvaluator.positive(grainOuterRadiusM,"grainOuterRadiusM");
        MotorInertiaEvaluator.nonnegative(grainInitialInnerRadiusM,"grainInitialInnerRadiusM");
        if(grainInitialInnerRadiusM>=grainOuterRadiusM)throw new IllegalArgumentException("grain inner radius must be less than outer radius");
        MotorInertiaEvaluator.nonnegative(initialMassToleranceKg,"initialMassToleranceKg");
        MotorInertiaEvaluator.nonnegative(motorCgToleranceM,"motorCgToleranceM");
        MotorInertiaEvaluator.source(sourceReference);
    }
}
