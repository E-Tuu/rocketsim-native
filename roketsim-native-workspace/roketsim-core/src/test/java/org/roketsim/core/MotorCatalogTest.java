package org.roketsim.core;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.propulsion.*;
import static org.roketsim.core.propulsion.MotorCatalogs.*;
import static org.junit.jupiter.api.Assertions.*;

class MotorCatalogTest {
    static MotorDefinition change(String field,Object value) {
        return (MotorDefinition)VehicleTestData.replace(AEROTECH_F50_4T,field,value);
    }
    @Test void exactCatalogAndMetadata() {
        var m=AEROTECH_F50_4T;
        assertEquals("aerotech_f50_4t",m.motorId()); assertEquals("AeroTech",m.manufacturer());
        assertEquals("F50-4T",m.designation()); assertEquals("F50T",m.family());
        assertEquals(MotorType.SINGLE_USE,m.motorType()); assertEquals("single_use",MotorType.SINGLE_USE.value());
        assertEquals("Blue Thunder",m.propellantName());
        assertEquals(.029,m.diameterM()); assertEquals(.098,m.lengthM());
        assertEquals(.0849,m.initialMassKg()); assertEquals(.0379,m.propellantMassKg()); assertEquals(4,m.ejectionDelayS());
        assertEquals(32,m.thrustCurve().size()); assertEquals(new ThrustSample(0,0),m.thrustCurve().get(0));
        assertEquals(new ThrustSample(.354,79.590),m.thrustCurve().get(15));
        assertEquals(new ThrustSample(1.430,0),m.thrustCurve().get(31));
        assertEquals(new MotorCertificationReference(76.83,53.73,79.59,1.43),m.certification());
        assertTrue(m.provenance().normalizationNote().contains("prepends (0.0 s, 0.0 N)"));
        assertNull(m.massCurve()); assertNull(m.cgCurve()); assertNull(m.provenance().massCurveSource()); assertNull(m.provenance().cgCurveSource());
        assertSame(m,DEMO_MOTOR_CATALOG.get(m.motorId()));
        assertThrows(NoSuchElementException.class,()->DEMO_MOTOR_CATALOG.get("F50-4T"));
        assertThrows(IllegalArgumentException.class,()->new MotorCatalog(List.of(m,m)));
        assertThrows(UnsupportedOperationException.class,()->m.thrustCurve().clear());
        assertThrows(UnsupportedOperationException.class,()->DEMO_MOTOR_CATALOG.motors().clear());
        var list=new ArrayList<>(m.thrustCurve()); var copied=change("thrust_curve",list); list.clear(); assertEquals(32,copied.thrustCurve().size());
    }
    @ParameterizedTest @ValueSource(strings={"motor_id","manufacturer","designation","family","propellant_name"})
    void emptyMetadata(String field) {
        for(String text:new String[]{""," \t","\u00a0","\u0085"}) { assertEquals(field,assertThrows(MotorValidationError.class,()->change(field,text)).fieldName()); }
    }
    @ParameterizedTest @ValueSource(strings={"diameter_m","length_m","initial_mass_kg","propellant_mass_kg"})
    void domains(String field) {
        for(double x:new double[]{0,-1}) { assertEquals(field,assertThrows(MotorValidationError.class,()->change(field,x)).fieldName()); }
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->change(field,x)).getClass());
        }
    }
    @Test void sampleAndReferenceValidation() {
        assertEquals("NEGATIVE_THRUST_TIME",assertThrows(MotorValidationError.class,()->new ThrustSample(-1,-1)).errorCode());
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new ThrustSample(-1,Double.NaN)).getClass());
        assertEquals("NEGATIVE_THRUST",assertThrows(MotorValidationError.class,()->new ThrustSample(0,-1)).errorCode());
        assertEquals("NON_POSITIVE_MASS_SAMPLE",assertThrows(MotorValidationError.class,()->new MotorMassSample(0,0)).errorCode());
        assertEquals("NEGATIVE_CG_POSITION",assertThrows(MotorValidationError.class,()->new MotorCgSample(0,-1)).errorCode());
        assertThrows(MotorValidationError.class,()->new MotorMassSample(-1,1));
        assertThrows(MotorValidationError.class,()->new MotorCgSample(-1,0));
        assertThrows(MotorValidationError.class,()->new MotorCertificationReference(0,1,1,1));
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new MotorCertificationReference(0,Double.NaN,1,1)).getClass());
        assertThrows(MotorValidationError.class,()->new MotorDataProvenance(" ","x","x","x",null,null));
        assertThrows(MotorValidationError.class,()->change("motor_type","single_use"));
        assertThrows(MotorValidationError.class,()->change("certification",null));
        assertThrows(MotorValidationError.class,()->change("provenance",null));
        assertThrows(MotorValidationError.class,()->change("propellant_mass_kg",.0849));
        assertEquals(0,change("ejection_delay_s",0.).ejectionDelayS());
        assertThrows(MotorValidationError.class,()->change("ejection_delay_s",-1.));
    }
    @Test void curveValidationAndOrder() {
        List<List<ThrustSample>> curves=List.of(List.of(),List.of(new ThrustSample(0,0),new ThrustSample(1,0)),
            List.of(new ThrustSample(0,1),new ThrustSample(1,1),new ThrustSample(2,0)),
            List.of(new ThrustSample(0,0),new ThrustSample(1,1),new ThrustSample(1,0)),
            List.of(new ThrustSample(0,0),new ThrustSample(1,0),new ThrustSample(2,0)),
            List.of(new ThrustSample(0,0),new ThrustSample(1,1),new ThrustSample(2,1)));
        String[] codes={"TOO_FEW_THRUST_SAMPLES","TOO_FEW_THRUST_SAMPLES","INVALID_THRUST_CURVE_START","NON_INCREASING_THRUST_TIME","NO_POSITIVE_THRUST","INVALID_THRUST_CURVE_END"};
        for(int i=0;i<curves.size();i++) { final int n=i; assertEquals(codes[i],assertThrows(MotorValidationError.class,()->change("thrust_curve",curves.get(n))).errorCode()); }
        assertEquals("INVALID_THRUST_CURVE_TYPE",assertThrows(MotorValidationError.class,()->change("thrust_curve",List.of("bad"))).errorCode());
        assertEquals("MASS_CURVE_PROVENANCE_MISMATCH",assertThrows(MotorValidationError.class,()->change("mass_curve",List.of())).errorCode());
    }
}
