package org.roketsim.core;

import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.*;

class StructuralInertiaTest {
    private static ResolvedRocketGeometry geometry(SingleStageRocketGeometry source) { return new GeometryResolver().resolve(source); }
    private static StructuralMassProperties masses(ResolvedRocketGeometry g) { return new StructuralMassPropertiesCalculator().evaluate(g,VehicleTestData.materials()); }
    private static StructuralComponentInertias evaluate(ResolvedRocketGeometry g) { return new StructuralInertiaCalculator().evaluate(g,masses(g)); }
    private static void close(double expected,double actual) { GeometryResolverTest.close(expected,actual,3e-14); }
    // Independent 3-node Gauss integration: exact through degree five, covering these polynomial volume integrands.
    private static double integrate(double a,double b,DoubleUnaryOperator function) {
        double mid=(a+b)/2,half=(b-a)/2,node=Math.sqrt(3.0/5);
        return half*(5.0/9*function.applyAsDouble(mid-half*node)+8.0/9*function.applyAsDouble(mid)+5.0/9*function.applyAsDouble(mid+half*node));
    }
    @Test void exactResultSchemaNullsAndStatelessness() {
        assertEquals(5,StructuralComponentInertias.class.getRecordComponents().length);
        assertEquals(0,StructuralInertiaCalculator.class.getDeclaredFields().length);
        assertThrows(IllegalArgumentException.class,()->new StructuralInertiaCalculator().evaluate(null,null));
        var z=new InertiaTensor3D(0,0,0,0,0,0);
        for(int i=0;i<5;i++) {
            InertiaTensor3D[] a={z,z,z,z,z};a[i]=null;
            assertThrows(IllegalArgumentException.class,()->new StructuralComponentInertias(a[0],a[1],a[2],a[3],a[4]));
        }
    }
    @Test void solidConeAnalytic() {
        var source=VehicleTestData.change("nose",new ConicalNoseGeometry(.3,NoseConstructionMode.SOLID,null));
        var g=geometry(source);var m=masses(g);var i=evaluate(g).nose();double mass=m.nose().massKg();
        close(mass*.00075,i.izzKgM2()); close(mass*.00375,i.ixxKgM2());
        assertEquals(i.ixxKgM2(),i.iyyKgM2());assertEquals(0,i.ixyKgM2());
    }
    @Test void normalShellIndependentCrossSectionIntegration() {
        for(double thickness:new double[]{.002,.01}) {
            var g=geometry(VehicleTestData.change("nose.wall_thickness_m",thickness));var m=masses(g);var result=evaluate(g).nose();
            double length=.3,r=.05,cg=m.nose().cgXGeoM(),density=m.nose().massKg()/g.noseMaterialVolumeM3();
            // Inner surface is r_inner(z)=R*z/L - t*sqrt(1+(R/L)^2), not a common-apex cone.
            double deltaR=thickness*Math.sqrt(1+(r/length)*(r/length)),offset=deltaR*length/r;
            DoubleUnaryOperator axial=z->{double outer=r*z/length,inner=z<offset?0:r*z/length-deltaR;
                return density*Math.PI*(Math.pow(outer,4)-Math.pow(inner,4))/2;};
            DoubleUnaryOperator transverse=z->{double outer=r*z/length,inner=z<offset?0:r*z/length-deltaR;
                return density*Math.PI*((Math.pow(outer,4)-Math.pow(inner,4))/4+(z-cg)*(z-cg)*(outer*outer-inner*inner));};
            close(integrate(0,offset,axial)+integrate(offset,length,axial),result.izzKgM2());
            close(integrate(0,offset,transverse)+integrate(offset,length,transverse),result.ixxKgM2());
            assertEquals(result.ixxKgM2(),result.iyyKgM2());
        }
    }
    @Test void bodyMountAndSeparatedRings() {
        var g=geometry(VehicleTestData.geometry());var m=masses(g);var i=evaluate(g);
        double bodyR2=.05*.05+.048*.048;
        close(m.body().massKg()*bodyR2/2,i.body().izzKgM2());close(m.body().massKg()*(3*bodyR2+.7*.7)/12,i.body().ixxKgM2());
        double mountR2=.0155*.0155+.0145*.0145;
        close(m.motorMount().massKg()*mountR2/2,i.motorMount().izzKgM2());
        close(m.motorMount().massKg()*(3*mountR2+.12*.12)/12,i.motorMount().ixxKgM2());
        double ringR2=.048*.048+.0155*.0155;
        double self=m.centeringRings().massKg()*(3*ringR2+.003*.003)/12;
        double distance=(.12-.003)/2;
        close(self+m.centeringRings().massKg()*distance*distance,i.centeringRings().ixxKgM2());
        close(m.centeringRings().massKg()*ringR2/2,i.centeringRings().izzKgM2());
        assertTrue(i.centeringRings().ixxKgM2()>self);
    }
    @Test void finsIndependentVolumeIntegrationWithSweepTriangleThicknessAndCount() {
        for(int count:new int[]{3,4,5,8})for(double sweep:new double[]{-.04,.05})for(double tip:new double[]{0,.08}) {
            var source=VehicleTestData.change("fins.fin_count",count);
            source=(SingleStageRocketGeometry)VehicleTestData.replace(source,"fins.tip_leading_edge_offset_x_m",sweep);
            source=(SingleStageRocketGeometry)VehicleTestData.replace(source,"fins.tip_chord_m",tip);
            var g=geometry(source);var m=masses(g);var result=evaluate(g).fins();
            double mass=m.fins().massKg(),density=mass/g.finSetMaterialVolumeM3(),cg=m.fins().cgXGeoM();
            double[] expected=new double[6];
            for(int fin=0;fin<count;fin++) {
                double angle=2*Math.PI*fin/count,cos=Math.cos(angle),sin=Math.sin(angle);
                for(int component=0;component<6;component++) {
                    final int axis=component;
                    expected[component]+=integrate(0,.12,u->{
                        double chord=.18+(tip-.18)*u/.12,leading=.72+sweep*u/.12;
                        return density*integrate(leading,leading+chord,q->integrate(-.0015,.0015,t->{
                            double x=(.05+u)*cos-t*sin,y=(.05+u)*sin+t*cos,z=-(q-cg);
                            return switch(axis) { case 0->y*y+z*z;case 1->x*x+z*z;case 2->x*x+y*y;
                                case 3->-x*y;case 4->-x*z;default->-y*z;};
                        }));
                    });
                }
            }
            close(expected[0],result.ixxKgM2());close(expected[1],result.iyyKgM2());close(expected[2],result.izzKgM2());
            // Angular cancellation is exact in production; quadrature/trig reference has last-bit residual products.
            for(int k=3;k<6;k++)assertEquals(0,expected[k],expected[0]*3e-14);
            assertEquals(0,result.ixyKgM2());assertEquals(0,result.ixzKgM2());assertEquals(0,result.iyzKgM2());
        }
    }
    @Test void acceptedMassScalingAndRepeatabilityWithoutRecalculation() {
        var g=geometry(VehicleTestData.geometry());var m=masses(g);var original=evaluate(g);
        var doubled=(StructuralMassProperties)VehicleTestData.replace(m,"fins.mass_kg",2*m.fins().massKg());
        var scaled=new StructuralInertiaCalculator().evaluate(g,doubled);
        assertEquals(original.nose(),scaled.nose());close(2*original.fins().ixxKgM2(),scaled.fins().ixxKgM2());
        assertEquals(original,evaluate(g));assertEquals(m,masses(g));
    }
    @Test void finiteThicknessAndRadialPlacementContribute() {
        var g=geometry(VehicleTestData.geometry());var m=masses(g);var original=evaluate(g).fins();
        var thickerSource=VehicleTestData.change("fins.thickness_m",.006);
        var thicker=geometry(thickerSource);
        // Keep supplied mass fixed to isolate thickness rather than the structural mass change.
        var changed=new StructuralInertiaCalculator().evaluate(thicker,m).fins();
        double increment=m.fins().massKg()*(.006*.006-.003*.003)/12;
        close(original.izzKgM2()+increment,changed.izzKgM2());
        close(original.ixxKgM2()+increment/2,changed.ixxKgM2());
        var farther=(ResolvedRocketGeometry)VehicleTestData.replace(g,"fin_body_radius_at_root_m",.1);
        assertTrue(new StructuralInertiaCalculator().evaluate(farther,m).fins().izzKgM2()>original.izzKgM2());
    }
    @Test void finitePositiveGeometryAndMassErrors() {
        var g=geometry(VehicleTestData.geometry());var m=masses(g);var e=new StructuralInertiaCalculator();
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            var invalid=(StructuralMassProperties)VehicleTestData.replace(m,"nose.mass_kg",bad);
            assertThrows(IllegalArgumentException.class,()->e.evaluate(g,invalid));
        }
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            var invalid=(ResolvedRocketGeometry)VehicleTestData.replace(g,"motor_mount_inner_diameter_m",bad);
            assertThrows(IllegalArgumentException.class,()->e.evaluate(invalid,m));
        }
        var overflow=(StructuralMassProperties)VehicleTestData.replace(m,"nose.mass_kg",Double.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,()->e.evaluate(g,overflow));
        var impossible=(ResolvedRocketGeometry)VehicleTestData.replace(g,"body_inner_diameter_m",.2);
        assertThrows(IllegalArgumentException.class,()->e.evaluate(impossible,m));
    }
}
