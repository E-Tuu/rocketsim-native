package org.roketsim.core.propulsion;

import org.roketsim.core.mass.InertiaTensor3D;
import org.roketsim.core.mass.ParallelAxisInertiaCalculator;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless inertia evaluation from supplied instantaneous mass/CG; no mass or thrust evaluation. */
public final class MotorInertiaEvaluator {
    static void required(Object value,String name) {
        if(value==null)throw new IllegalArgumentException(name+" must not be null");
    }
    static void source(String value) {
        if(value==null || value.isBlank())throw new IllegalArgumentException("sourceReference/provenanceReference must not be blank");
    }
    static double nonnegative(double value,String name) {
        requireFinite(value,name); if(value<0)throw new IllegalArgumentException(name+" must be nonnegative"); return value;
    }
    static double positive(double value,String name) {
        requireFinite(value,name); if(value<=0)throw new IllegalArgumentException(name+" must be positive"); return value;
    }
    private static InertiaTensor3D annulus(double mass,double length,double outer2,double inner2) {
        double axial=mass*(outer2+inner2)/2;
        double transverse=mass*(3*(outer2+inner2)+length*length)/12;
        return new InertiaTensor3D(transverse,transverse,axial,0,0,0);
    }
    private static InertiaTensor3D shift(InertiaTensor3D tensor,double mass,double from,double target) {
        // Motor-local aft-positive coordinate differences have opposite BODY Z sign; axial quadratic shift is unchanged.
        return new ParallelAxisInertiaCalculator().shiftFromCentroid(tensor,mass,Vector.of(new double[]{0,0,from-target}));
    }
    private static double lerp(double a,double b,double fraction,String field) {
        double difference=requireFinite(b-a,field+".difference");
        return requireFinite(a+difference*fraction,field);
    }
    private static InertiaTensor3D curve(MotorInertiaCurveModel model,double time,double curveEnd) {
        var samples=model.samples(); var last=samples.get(samples.size()-1);
        if(last.motorTimeS()<curveEnd)throw new IllegalArgumentException("samples insufficient source curveEndTimeS coverage");
        for(int i=0;i<samples.size();i++) {
            var next=samples.get(i);
            if(time==next.motorTimeS())return next.inertiaAtMotorCg();
            if(time<next.motorTimeS()) {
                var previous=samples.get(i-1); var a=previous.inertiaAtMotorCg(); var b=next.inertiaAtMotorCg();
                double fraction=(time-previous.motorTimeS())/positive(next.motorTimeS()-previous.motorTimeS(),"sample interval");
                return new InertiaTensor3D(lerp(a.ixxKgM2(),b.ixxKgM2(),fraction,"ixxKgM2"),
                    lerp(a.iyyKgM2(),b.iyyKgM2(),fraction,"iyyKgM2"),lerp(a.izzKgM2(),b.izzKgM2(),fraction,"izzKgM2"),
                    lerp(a.ixyKgM2(),b.ixyKgM2(),fraction,"ixyKgM2"),lerp(a.ixzKgM2(),b.ixzKgM2(),fraction,"ixzKgM2"),
                    lerp(a.iyzKgM2(),b.iyzKgM2(),fraction,"iyzKgM2"));
            }
        }
        return last.inertiaAtMotorCg();
    }
    private static InertiaTensor3D radial(MotorDefinition motor,MotorMassProperties mass,RadialGrainMotorInertiaModel model) {
        if(model.dryCgFromFrontM()>motor.lengthM())throw new IllegalArgumentException("dryCgFromFrontM outside motor length");
        if(2*model.grainOuterRadiusM()>motor.diameterM())throw new IllegalArgumentException("grain outer diameter exceeds motor diameter");
        double spacing=positive(model.grainLengthM()+model.grainSeparationM(),"grain spacing");
        double lastEnd=requireFinite(model.firstGrainFrontFromMotorFrontM()+(model.grainCount()-1.0)*spacing+model.grainLengthM(),"lastGrainEndM");
        if(lastEnd>motor.lengthM())throw new IllegalArgumentException("grain positions outside motor length");
        double outer2=positive(model.grainOuterRadiusM()*model.grainOuterRadiusM(),"grainOuterRadiusSquared");
        double initialInner2=nonnegative(model.grainInitialInnerRadiusM()*model.grainInitialInnerRadiusM(),"initialInnerRadiusSquared");
        double capacity=positive(model.grainCount()*model.grainDensityKgM3()*Math.PI*model.grainLengthM(),"grain mass per radius squared");
        double geometryMass=positive(capacity*(outer2-initialInner2),"initialGrainMassKg");
        if(Math.abs(geometryMass-motor.propellantMassKg())>model.initialMassToleranceKg())
            throw new IllegalArgumentException("initialGrainMassKg inconsistent with motor.propellantMassKg");
        double dry=positive(motor.initialMassKg()-motor.propellantMassKg(),"dryMassKg");
        double remaining=nonnegative(mass.massKg()-dry,"remainingPropellantMassKg");
        if(remaining>motor.propellantMassKg() && remaining-motor.propellantMassKg()>model.initialMassToleranceKg())
            throw new IllegalArgumentException("remainingPropellantMassKg exceeds initial propellant mass");
        double inner2=nonnegative(outer2-remaining/capacity,"innerRadiusSquared");
        if(inner2>outer2 || remaining>geometryMass+model.initialMassToleranceKg())
            throw new IllegalArgumentException("innerRadiusSquared inconsistent with initial/burnout grain geometry");
        double target=mass.cgLocalFromFrontM();
        double moment=requireFinite(dry*model.dryCgFromFrontM(),"dry moment");
        double grainMass=remaining/model.grainCount();
        for(int i=0;i<model.grainCount();i++) {
            double cg=model.firstGrainFrontFromMotorFrontM()+i*spacing+model.grainLengthM()/2;
            moment=requireFinite(moment+grainMass*cg,"motor CG moment");
        }
        double modeledCg=requireFinite(moment/mass.massKg(),"modeledMotorCgM");
        if(Math.abs(modeledCg-target)>model.motorCgToleranceM())throw new IllegalArgumentException("motor local CG inconsistent with radial grain model");
        var dryTensor=new InertiaTensor3D(model.dryTransverseInertiaKgM2(),model.dryTransverseInertiaKgM2(),model.dryAxialInertiaKgM2(),0,0,0);
        var dryAtTarget=shift(dryTensor,dry,model.dryCgFromFrontM(),target);
        double transverse=dryAtTarget.ixxKgM2(),axial=dryAtTarget.izzKgM2();
        if(remaining>0) {
            var grain=annulus(positive(grainMass,"grainMassKg"),model.grainLengthM(),outer2,inner2);
            for(int i=0;i<model.grainCount();i++) {
                double cg=model.firstGrainFrontFromMotorFrontM()+i*spacing+model.grainLengthM()/2;
                var contribution=shift(grain,grainMass,cg,target);
                transverse=requireFinite(transverse+contribution.ixxKgM2(),"radial transverse inertia");
                axial=requireFinite(axial+contribution.izzKgM2(),"radial axial inertia");
            }
        }
        return new InertiaTensor3D(transverse,transverse,axial,0,0,0);
    }
    /** @param motor accepted immutable motor definition
     * @param motorMassProperties supplied instantaneous mass and local CG authority
     * @param motorStatistics accepted source-curve endpoint authority
     * @param motorTimeS finite nonnegative elapsed motor time (s)
     * @param model explicitly selected inertia model, never inferred
     * @return centroidal tensor and visible provenance */
    public MotorInertiaResult evaluate(MotorDefinition motor,MotorMassProperties motorMassProperties,
            MotorCurveStatistics motorStatistics,double motorTimeS,MotorInertiaModel model) {
        required(motor,"motor"); required(motorMassProperties,"motorMassProperties"); required(motorStatistics,"motorStatistics");
        nonnegative(motorTimeS,"motorTimeS"); required(model,"model");
        positive(motor.diameterM(),"motor.diameterM"); positive(motor.lengthM(),"motor.lengthM");
        positive(motorMassProperties.massKg(),"motorMassProperties.massKg");
        double cg=nonnegative(motorMassProperties.cgLocalFromFrontM(),"motorMassProperties.cgLocalFromFrontM");
        requireFinite(motorMassProperties.cgXGeoM(),"motorMassProperties.cgXGeoM");
        if(cg>motor.lengthM())throw new IllegalArgumentException("motor CG outside motor length");
        double end=positive(motorStatistics.curveEndTimeS(),"curveEndTimeS");
        if(model instanceof MotorInertiaCurveModel curve)
            return new MotorInertiaResult(curve(curve,motorTimeS,end),MotorInertiaModelKind.EXPLICIT_CURVE,curve.sourceReference());
        if(model instanceof RadialGrainMotorInertiaModel grain)
            return new MotorInertiaResult(radial(motor,motorMassProperties,grain),MotorInertiaModelKind.RADIAL_GRAIN,grain.sourceReference());
        var uniform=(UniformCylinderMotorInertiaModel)model;
        if(Math.abs(cg-motor.lengthM()/2)>uniform.motorCgToleranceM())throw new IllegalArgumentException("motor local CG inconsistent with uniform cylinder midpoint");
        double radius=motor.diameterM()/2;
        var centroidal=annulus(motorMassProperties.massKg(),motor.lengthM(),radius*radius,0);
        // Keep the modeled cylinder at L/2; express its tensor about the accepted CG, even for an allowed small mismatch.
        return new MotorInertiaResult(shift(centroidal,motorMassProperties.massKg(),motor.lengthM()/2,cg),
            MotorInertiaModelKind.UNIFORM_CYLINDER,"MODEL-APPROXIMATION: uniform homogeneous motor cylinder");
    }
}
