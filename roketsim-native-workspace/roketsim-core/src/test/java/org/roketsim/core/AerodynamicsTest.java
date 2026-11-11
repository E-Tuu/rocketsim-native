package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.environment.DryAirProperties;
import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import static org.junit.jupiter.api.Assertions.*;

/** Accepted NAT-012 fixtures and validation/authority tests, not production defaults. */
class AerodynamicsTest {
    static ResolvedRocketGeometry geometry() { return new GeometryResolver().resolve(VehicleTestData.geometry()); }
    static BasicFlightConditions flight(double mach) { return new BasicFlightConditions(100,mach,123,456); }
    static DryAirProperties air() { return new DryAirProperties(340,1.8e-5,1.5e-5,1.4); }
    static SingleStageRocketAerodynamicSurfaces surfaces(double n,double b,double f) {
        return new SingleStageRocketAerodynamicSurfaces(new SurfaceFinish("Nose",n),new SurfaceFinish("Body",b),new SurfaceFinish("Fins",f));
    }
    static BasicDragResult drag(double mach) {
        return new BasicDragEvaluator().evaluate(geometry(),surfaces(0,0,0),flight(mach),air(),BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE);
    }
    static StaticAerodynamicProperties stat(double mach) {
        return new StaticAerodynamicEvaluator().evaluate(geometry(),flight(mach),StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE);
    }
    static void relative(double expected,double actual,double tolerance) { assertEquals(expected,actual,Math.abs(expected)*tolerance); }
    @Test void frozenSevenDragTerms() {
        var r=drag(.3);
        double[] expected={.019602822950748604,.09023515876002,.05091519406760164,.021621978661408206,.13580527568987105,.024146733317993406,.1317,.4740271634476429};
        double[] actual={r.noseFrictionCd(),r.bodyFrictionCd(),r.finFrictionCd(),r.nosePressureCd(),r.finLeadingEdgePressureCd(),r.finTrailingEdgeBaseCd(),r.airframeBaseCd(),r.totalCd0()};
        for(int i=0;i<expected.length;i++) relative(expected[i],actual[i],5e-15);
        relative(r.totalCd0(),r.frictionCd()+r.pressureCd()+r.baseCd(),3e-15);
        assertEquals(PlumeTreatment.IGNORED,r.modelProfile().plumeTreatment());
    }
    @ParameterizedTest @ValueSource(doubles={0,.5,.75,.8,1}) void dragClosedMachDomain(double mach) { assertTrue(drag(mach).totalCd0()>0); }
    @ParameterizedTest @ValueSource(doubles={-1,1.0001}) void dragUnsupportedMach(double mach) { assertThrows(AerodynamicEvaluationError.class,()->drag(mach)); }
    @ParameterizedTest @ValueSource(doubles={0,.5,.500001,.75,.799999999}) void staticDomain(double mach) { assertTrue(stat(mach).totalCnaPerRad()>0); }
    @ParameterizedTest @ValueSource(doubles={.8,.81,1,2,-.1}) void staticUnsupportedMach(double mach) { assertThrows(AerodynamicEvaluationError.class,()->stat(mach)); }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) void rawNonfinite(double value) {
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->drag(value)).getClass());
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->stat(value)).getClass());
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new SurfaceFinish("N",value)).getClass());
    }
    @Test void surfacesValidationAndIdentity() {
        for(String name:new String[]{"","  ",null}) assertEquals("EMPTY_SURFACE_FINISH_NAME",assertThrows(SurfaceValidationError.class,()->new SurfaceFinish(name,0)).errorCode());
        var error=assertThrows(SurfaceValidationError.class,()->new SurfaceFinish("N",-1));
        assertEquals("NEGATIVE_EQUIVALENT_ROUGHNESS",error.errorCode()); assertEquals("equivalent_roughness_m",error.fieldName()); assertEquals(-1.,error.value());
        var shared=new SurfaceFinish("N",100); var s=new SingleStageRocketAerodynamicSurfaces(shared,shared,shared);
        assertSame(shared,s.nose()); assertSame(shared,s.body()); assertSame(shared,s.fins());
        assertTrue(SurfaceFinish.class.isRecord()); assertTrue(SingleStageRocketAerodynamicSurfaces.class.isRecord());
    }
    @Test void lowReAndIndependentRoughness() {
        var evaluator=new BasicDragEvaluator();
        var stopped=evaluator.evaluate(geometry(),surfaces(0,0,0),new BasicFlightConditions(0,0,999,999),air(),BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE);
        assertEquals(0,stopped.reynoldsNumber()); assertEquals(1e4,stopped.frictionEvaluationReynoldsNumber());
        var rough=evaluator.evaluate(geometry(),surfaces(0,1e-3,1e-10),flight(.3),air(),BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE);
        assertEquals(SkinFrictionBranch.SMOOTH,rough.noseSkinFriction().selectedBranch());
        assertEquals(SkinFrictionBranch.ROUGHNESS_LIMITED,rough.bodySkinFriction().selectedBranch());
        assertEquals(SkinFrictionBranch.SMOOTH,rough.finSkinFriction().selectedBranch());
        relative(.032*Math.pow(1e-3,.2)*(1-.10*.3*.3),rough.bodySkinFriction().selectedCf(),3e-15);
    }
    @Test void frozenStaticFixtures() {
        var zero=stat(0); relative(12.629237832516534,zero.finSet().cnaPerRad(),4e-15); relative(.6971650449840304,zero.cpXGeoM(),4e-15);
        var r=stat(.75); relative(13.729822473770069,r.finSet().cnaPerRad(),4e-15); relative(15.729822473770069,r.totalCnaPerRad(),4e-15); relative(.78118620117551,r.finSet().cpXGeoM(),4e-15); relative(.707290109576936,r.cpXGeoM(),4e-15);
        assertEquals(r.nose().cnaPerRad()+r.finSet().cnaPerRad(),r.totalCnaPerRad()); relative(.2,r.nose().cpXGeoM(),3e-15);
    }
    @ParameterizedTest @ValueSource(ints={3,4,5,6,7,8,9,12}) void finInterference(int count) {
        var g=new GeometryResolver().resolve(VehicleTestData.change("fins.fin_count",count));
        var r=new StaticAerodynamicEvaluator().evaluate(g,flight(0),StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE);
        double factor=switch(count){case 5->.948;case 6->.913;case 7->.854;case 8->.810;default->count<=4?1:.750;};
        relative(4.879478253472297*(count/2.)*factor*(1+.05/(.12+.05)),r.finSet().cnaPerRad(),4e-15);
    }
    @Test void profileIsMandatoryAndExact() {
        assertThrows(AerodynamicEvaluationError.class,()->new BasicDragEvaluator().evaluate(geometry(),surfaces(0,0,0),flight(0),air(),null));
        assertThrows(AerodynamicEvaluationError.class,()->new StaticAerodynamicEvaluator().evaluate(geometry(),flight(0),null));
        assertThrows(AerodynamicEvaluationError.class,()->new BasicDragModelProfile(null,null,null,0,null,null,null,null));
        assertThrows(AerodynamicEvaluationError.class,()->new StaticStabilityModelProfile(null,null,null,0));
    }
    @Test void marginSignedAndAuthority() {
        for(double[] row:new double[][]{{.7,.6,1},{.65,.65,0},{.6,.7,-1}}) {
            var c=new StaticAeroContribution(1,row[0]); var a=new StaticAerodynamicProperties(c,c,2,row[0],null);
            var m=new RocketMassProperties(1,row[1]); var calc=new StaticMarginCalculator();
            assertEquals(row[2],calc.evaluate(a,m,geometry()).staticMarginCalibers(),2e-15);
            var reference=(ResolvedRocketGeometry)VehicleTestData.replace(geometry(),"reference_length_m",Double.NaN);
            assertEquals(calc.evaluate(a,m,geometry()),calc.evaluate(a,m,reference));
        }
        relative(.5729010957693603,new StaticMarginCalculator().evaluate(stat(.75),new RocketMassProperties(1,.65),geometry()).staticMarginCalibers(),5e-15);
    }
    @Test void actualJ005MassAndDeterminism() {
        var motor=new MotorInstallationResolver().resolve(geometry(),MotorCatalogs.AEROTECH_F50_4T);
        var mp=new MotorPropertyEvaluator().evaluate(motor,MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,1.4);
        var structure=new StructuralMassPropertiesCalculator().evaluate(geometry(),VehicleTestData.materials());
        var mass=new RocketMassPropertiesCalculator().evaluate(structure,mp);
        var result=new StaticMarginCalculator().evaluate(stat(.75),mass,geometry());
        relative((stat(.75).cpXGeoM()-mass.totalCgXGeoM())/.1,result.staticMarginCalibers(),5e-15);
        assertEquals(drag(.3),drag(.3)); assertEquals(stat(.75),stat(.75));
    }
}
