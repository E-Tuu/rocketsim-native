package org.roketsim.core.mass;

import org.roketsim.core.geometry.*;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Existing structural distributions only; consumes geometry and mass/CG authorities. */
public final class StructuralInertiaCalculator {
    private static double positive(double v,String name) {
        requireFinite(v,name);
        if(v<=0)throw new IllegalArgumentException(name+" must be positive");
        return v;
    }
    private static double mass(ComponentMassProperties c,String name) {
        if(c==null)throw new IllegalArgumentException(name+" must not be null");
        requireFinite(c.cgXGeoM(),name+".cgXGeoM");
        return positive(c.massKg(),name+".massKg");
    }
    private static InertiaTensor3D cone(double m,double length,double radius) {
        double transverse=3.0/20*m*radius*radius+3.0/80*m*length*length;
        return new InertiaTensor3D(transverse,transverse,3.0/10*m*radius*radius,0,0,0);
    }
    private static InertiaTensor3D annulus(double m,double length,double outer,double inner) {
        positive(length,"annulus.lengthM"); positive(outer,"annulus.outerRadiusM"); requireFinite(inner,"annulus.innerRadiusM");
        if(inner<=0 || inner>=outer)throw new IllegalArgumentException("annulus inner/outer radius ordering");
        double r2=requireFinite(outer*outer+inner*inner,"annulus.radiusSquared");
        double transverse=m*(3*r2+length*length)/12;
        return new InertiaTensor3D(transverse,transverse,m*r2/2,0,0,0);
    }
    private static InertiaTensor3D axialShift(InertiaTensor3D tensor,double m,double offset) {
        return new ParallelAxisInertiaCalculator().shiftFromCentroid(tensor,m,Vector.of(new double[]{0,0,offset}));
    }
    private static InertiaTensor3D nose(ResolvedRocketGeometry g,ComponentMassProperties c,double m,double radius) {
        var source=g.source().nose(); double length=positive(source.lengthM(),"nose.lengthM");
        if(source.constructionMode()==NoseConstructionMode.SOLID)return cone(m,length,radius);
        if(source.constructionMode()!=NoseConstructionMode.HOLLOW_SHELL || source.wallThicknessM()==null)
            throw new IllegalArgumentException("unsupported nose constructionMode");
        double thickness=positive(source.wallThicknessM(),"nose.wallThicknessM");
        double slant=requireFinite(Math.hypot(length,radius),"nose.slantM");
        if(thickness>=length*radius/slant)throw new IllegalArgumentException("nose shell too thick");
        double tipOffset=thickness*slant/radius;
        double innerLength=positive(length-tipOffset,"nose.innerLengthM");
        double innerRadius=positive(radius-thickness*slant/length,"nose.innerRadiusM");
        double density=positive(m/positive(g.noseMaterialVolumeM3(),"nose.materialVolumeM3"),"nose.inferredDensityKgM3");
        double outerMass=positive(density*Math.PI*radius*radius*length/3,"nose.outerMassKg");
        double innerMass=positive(density*Math.PI*innerRadius*innerRadius*innerLength/3,"nose.innerMassKg");
        double outerCg=g.noseStartXGeoM()+3*(length/4);
        double innerCg=g.noseStartXGeoM()+tipOffset+3*(innerLength/4);
        var outer=axialShift(cone(outerMass,length,radius),outerMass,outerCg-c.cgXGeoM());
        var inner=axialShift(cone(innerMass,innerLength,innerRadius),innerMass,innerCg-c.cgXGeoM());
        return new InertiaTensor3D(outer.ixxKgM2()-inner.ixxKgM2(),outer.iyyKgM2()-inner.iyyKgM2(),
            outer.izzKgM2()-inner.izzKgM2(),0,0,0);
    }
    private static InertiaTensor3D fins(ResolvedRocketGeometry g,ComponentMassProperties c,double m) {
        var f=g.source().fins();
        if(!(f.finCount() instanceof Number n) || !(f.finCount() instanceof Byte || f.finCount() instanceof Short
            || f.finCount() instanceof Integer || f.finCount() instanceof Long || f.finCount() instanceof java.math.BigInteger)
            || !Double.isFinite(n.doubleValue()) || n.doubleValue()<3 || g.finAngularArrangement()!=FinAngularArrangement.EQUALLY_SPACED)
            throw new IllegalArgumentException("fins require at least three equally spaced fins");
        double root=positive(f.rootChordM(),"fins.rootChordM"),tip=requireFinite(f.tipChordM(),"fins.tipChordM");
        if(tip<0)throw new IllegalArgumentException("fins.tipChordM must be nonnegative");
        double span=positive(f.semiSpanM(),"fins.semiSpanM"),thickness=positive(f.thicknessM(),"fins.thicknessM");
        double offset=requireFinite(f.tipLeadingEdgeOffsetXM(),"fins.tipLeadingEdgeOffsetXM");
        double radius=positive(g.finBodyRadiusAtRootM(),"fins.bodyRadiusM");
        double cg=requireFinite(c.cgXGeoM()-g.finRootLeadingEdgeXGeoM(),"fins.localCgM");
        // Polygon in (aft-positive chord coordinate, radial span), centered on supplied axial CG.
        double[] x={-cg,root-cg,offset+tip-cg,offset-cg},y={0,0,span,span};
        double twiceArea=0,xSecond=0,ySecond=0,yFirst=0;
        for(int i=0;i<4;i++) {
            int j=(i+1)%4; double cross=x[i]*y[j]-x[j]*y[i];
            twiceArea+=cross;
            xSecond+=cross*(x[i]*x[i]+x[i]*x[j]+x[j]*x[j]);
            ySecond+=cross*(y[i]*y[i]+y[i]*y[j]+y[j]*y[j]);
            yFirst+=cross*(y[i]+y[j]);
        }
        double area=positive(twiceArea/2,"fins.integratedAreaM2");
        double axialSecond=positive(xSecond/(12*area),"fins.axialSecondM2");
        double radialSecond=positive(ySecond/(12*area)+2*radius*yFirst/(6*area)+radius*radius,"fins.radialSecondM2");
        double lateralSecond=requireFinite(radialSecond+thickness*thickness/12,"fins.lateralSecondM2");
        // BODY z = -(x_geo-CG). Equal angular spacing N>=3 cancels all products and splits lateral moments equally.
        double transverse=m*(axialSecond+lateralSecond/2);
        return new InertiaTensor3D(transverse,transverse,m*lateralSecond,0,0,0);
    }
    /** @param geometry accepted resolved geometry and its immutable source
     * @param masses accepted five component mass/CG results
     * @return five centroidal tensors; no total vehicle inertia */
    public StructuralComponentInertias evaluate(ResolvedRocketGeometry geometry,StructuralMassProperties masses) {
        if(geometry==null || geometry.source()==null || masses==null)throw new IllegalArgumentException("geometry/source and masses must not be null");
        if(geometry.source().nose()==null || geometry.source().body()==null || geometry.source().fins()==null)
            throw new IllegalArgumentException("source nose/body/fins must not be null");
        var g=geometry;
        double mn=mass(masses.nose(),"nose"),mb=mass(masses.body(),"body"),mf=mass(masses.fins(),"fins"),
            mm=mass(masses.motorMount(),"motorMount"),mr=mass(masses.centeringRings(),"centeringRings");
        double radius=positive(g.source().airframeDiameterM()/2,"airframeRadiusM");
        var nose=nose(g,masses.nose(),mn,radius);
        var body=annulus(mb,g.source().body().lengthM(),radius,g.bodyInnerDiameterM()/2);
        var mount=annulus(mm,g.motorMountEndXGeoM()-g.motorMountStartXGeoM(),g.motorMountOuterDiameterM()/2,g.motorMountInnerDiameterM()/2);
        double frontLength=positive(g.frontCenteringRingEndXGeoM()-g.frontCenteringRingStartXGeoM(),"frontRing.lengthM");
        double rearLength=positive(g.rearCenteringRingEndXGeoM()-g.rearCenteringRingStartXGeoM(),"rearRing.lengthM");
        var front=axialShift(annulus(mr/2,frontLength,g.bodyInnerDiameterM()/2,g.motorMountOuterDiameterM()/2),mr/2,
            (g.frontCenteringRingStartXGeoM()+g.frontCenteringRingEndXGeoM())/2-masses.centeringRings().cgXGeoM());
        var rear=axialShift(annulus(mr/2,rearLength,g.bodyInnerDiameterM()/2,g.motorMountOuterDiameterM()/2),mr/2,
            (g.rearCenteringRingStartXGeoM()+g.rearCenteringRingEndXGeoM())/2-masses.centeringRings().cgXGeoM());
        var rings=new InertiaTensor3D(front.ixxKgM2()+rear.ixxKgM2(),front.iyyKgM2()+rear.iyyKgM2(),front.izzKgM2()+rear.izzKgM2(),0,0,0);
        return new StructuralComponentInertias(nose,body,fins(g,masses.fins(),mf),mount,rings);
    }
}
