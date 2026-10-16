package org.roketsim.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.geometry.*;
import org.roketsim.core.propulsion.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.propulsion.MotorCatalogs.*;

class MotorInstallationTest {
    static ResolvedRocketGeometry geometry() { return new GeometryResolver().resolve(VehicleTestData.geometry()); }
    static ResolvedRocketGeometry change(ResolvedRocketGeometry g,String field,double value) {
        return (ResolvedRocketGeometry)VehicleTestData.replace(g,field,value);
    }
    @Test void nominalFixtureAndAuthority() {
        var g=geometry(); var result=new MotorInstallationResolver().resolve(g,AEROTECH_F50_4T);
        assertSame(AEROTECH_F50_4T,result.motor());
        double[] expected={.907,1.005,0,.093};
        double[] actual={result.motorFrontXGeoM(),result.motorAftXGeoM(),result.nominalRadialClearanceM(),result.axialEngagementLengthM()};
        for(int i=0;i<4;i++) { assertEquals(expected[i],actual[i],Math.abs(expected[i])*3e-15); }
        assertEquals(g.motorAftReferenceXGeoM()-AEROTECH_F50_4T.lengthM(),result.motorFrontXGeoM());
        assertEquals(result,new MotorInstallationResolver().resolve(g,AEROTECH_F50_4T));
        assertEquals(0,MotorInstallationResolver.class.getDeclaredFields().length);
    }
    @ParameterizedTest @ValueSource(doubles={.005,0,-.01})
    void signedOverhang(double overhang) {
        var raw=VehicleTestData.change("motor_attachment.motor_overhang_m",overhang);
        var g=new GeometryResolver().resolve(raw); var m=new MotorInstallationResolver().resolve(g,AEROTECH_F50_4T);
        assertEquals(Math.min(m.motorAftXGeoM(),g.motorMountEndXGeoM())-Math.max(m.motorFrontXGeoM(),g.motorMountStartXGeoM()),m.axialEngagementLengthM());
        assertEquals(overhang>0?.093:.098,m.axialEngagementLengthM(),.098*3e-15);
    }
    @Test void fitAndExactContainment() {
        var g=geometry(); var resolver=new MotorInstallationResolver();
        assertEquals("MOTOR_DIAMETER_EXCEEDS_MOUNT",assertThrows(MotorInstallationError.class,()->resolver.resolve(g,MotorCatalogTest.change("diameter_m",.04))).errorCode());
        assertEquals("MOTOR_FRONT_BEFORE_MOUNT",assertThrows(MotorInstallationError.class,()->resolver.resolve(g,MotorCatalogTest.change("length_m",.2))).errorCode());
        var boundary=change(change(change(g,"motor_mount_start_x_geo_m",.75),"motor_mount_end_x_geo_m",1),"motor_aft_reference_x_geo_m",1);
        assertEquals(.75,resolver.resolve(boundary,MotorCatalogTest.change("length_m",.25)).motorFrontXGeoM());
        assertEquals(.25,resolver.resolve(boundary,MotorCatalogTest.change("length_m",.25)).axialEngagementLengthM());
        for(double aft:new double[]{1.125,1.25}) {
            assertEquals("NO_POSITIVE_MOTOR_ENGAGEMENT",assertThrows(MotorInstallationError.class,
                ()->resolver.resolve(change(g,"motor_aft_reference_x_geo_m",aft),MotorCatalogTest.change("length_m",.125))).errorCode());
        }
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void invalidDerived(double value) {
        String[] fields={"motor_aft_reference_x_geo_m","motor_mount_inner_diameter_m","motor_mount_start_x_geo_m","motor_mount_end_x_geo_m"};
        String[] codes={"INVALID_MOTOR_AFT_POSITION","INVALID_RADIAL_CLEARANCE","INVALID_AXIAL_ENGAGEMENT","INVALID_AXIAL_ENGAGEMENT"};
        for(int i=0;i<4;i++) { final int n=i; assertEquals(codes[i],assertThrows(MotorInstallationError.class,
            ()->new MotorInstallationResolver().resolve(change(geometry(),fields[n],value),AEROTECH_F50_4T)).errorCode()); }
    }
    @Test void finiteOverflow() {
        assertEquals("INVALID_MOTOR_FRONT_POSITION",assertThrows(MotorInstallationError.class,
            ()->new MotorInstallationResolver().resolve(change(geometry(),"motor_aft_reference_x_geo_m",-1e308),MotorCatalogTest.change("length_m",1e308))).errorCode());
        var g=change(change(change(geometry(),"motor_mount_start_x_geo_m",-1e308),"motor_mount_end_x_geo_m",-1e308),"motor_aft_reference_x_geo_m",1e308);
        assertEquals("INVALID_AXIAL_ENGAGEMENT",assertThrows(MotorInstallationError.class,()->new MotorInstallationResolver().resolve(g,AEROTECH_F50_4T)).errorCode());
    }
}
