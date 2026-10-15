package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.geometry.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryResolverTest {
    private final GeometryResolver resolver=new GeometryResolver();
    @Test void fullHollowFixture() {
        var source=VehicleTestData.geometry(); var g=resolver.resolve(source);
        assertSame(source,g.source());
        assertEquals(54,g.getClass().getRecordComponents().length);
        assertEquals(1,g.overallLengthM());
        assertEquals(.3,g.bodyStartXGeoM());
        assertEquals(.1,g.referenceLengthM());
        assertEquals(.007853981633974483,g.referenceAreaM2(),.007853981633974483*2e-15);
        close(9.172555380948023e-5,g.noseMaterialVolumeM3(),2e-14);
        close(.2019996168861517,g.noseVolumeCentroidXGeoM(),2e-14);
        close(4.310265120725205e-4,g.bodyMaterialVolumeM3(),2e-14);
        close(.65,g.bodyVolumeCentroidXGeoM(),3e-15);
        close(.0001872,g.finSetMaterialVolumeM3(),3e-15);
        close(.81,g.finSetVolumeCentroidXGeoM(),3e-15);
        close(1.1309733552923245e-5,g.motorMountMaterialVolumeM3(),4e-15);
        close(3.890077103307561e-5,g.centeringRingPairMaterialVolumeM3(),4e-15);
        close(.94,g.motorMountVolumeCentroidXGeoM(),4e-15);
        close(.94,g.centeringRingPairVolumeCentroidXGeoM(),4e-15);
        assertEquals(g,resolver.resolve(source));
    }
    static void close(double expected,double actual,double rel) { assertEquals(expected,actual,Math.abs(expected)*rel); }
    @Test void aeroGeometryOnly() {
        var g=resolver.resolve(VehicleTestData.geometry());
        close(.04777390519679037,g.noseWettedAreaM2(),3e-15);
        close(.2199114857512855,g.bodyWettedAreaM2(),3e-15);
        close(.13641025641025642,g.finMeanAerodynamicChordM(),3e-15);
        close(.39479111969976155,g.finLeadingEdgeSweepAngleRad(),3e-15);
        assertEquals(0,g.finMidchordSweepAngleRad(),1e-15);
        close(.0523076923076923,g.finMeanAerodynamicChordSpanwiseLocationM(),3e-15);
        close(.7417948717948718,g.finMeanAerodynamicChordLeadingEdgeXGeoM(),3e-15);
        close(1.8461538461538463,g.finAspectRatio(),3e-15);
        assertEquals(.05,g.finBodyRadiusAtRootM());
        assertEquals(1,g.aerodynamicLengthM());
        assertEquals(1,g.axisymmetricBodyLengthM());
    }
    @Test void solidAndShellBoundary() {
        var source=(SingleStageRocketGeometry)VehicleTestData.replace(VehicleTestData.geometry(),"nose",
            new ConicalNoseGeometry(.3,NoseConstructionMode.SOLID,null));
        var solid=resolver.resolve(source);
        close(7.853981633974483e-4,solid.noseMaterialVolumeM3(),3e-15);
        close(.225,solid.noseVolumeCentroidXGeoM(),3e-15);
        double limit=.3*.05/Math.hypot(.3,.05);
        assertEquals("NOSE_SHELL_TOO_THICK",assertThrows(GeometryValidationError.class,
            ()->resolver.resolve(VehicleTestData.change("nose.wall_thickness_m",limit))).errorCode());
        assertTrue(resolver.resolve(VehicleTestData.change("nose.wall_thickness_m",Math.nextDown(limit))).noseMaterialVolumeM3()>0);
    }
    @ParameterizedTest @ValueSource(doubles={-.05,0,.05})
    void signedSweepAndTriangle(double offset) {
        var source=VehicleTestData.change("fins.tip_leading_edge_offset_x_m",offset);
        source=(SingleStageRocketGeometry)VehicleTestData.replace(source,"fins.tip_chord_m",0.);
        var g=resolver.resolve(source);
        assertEquals(g.finTipLeadingEdgeXGeoM(),g.finTipTrailingEdgeXGeoM());
        close(.72+(.18+offset)/3,g.finSetVolumeCentroidXGeoM(),3e-15);
        assertEquals(Math.atan2(offset,.12),g.finLeadingEdgeSweepAngleRad());
    }
    @Test void inclusivePlacementAndDistinctMotorExtent() {
        var source=VehicleTestData.change("fins.root_leading_edge_x_geo_m",.3);
        source=(SingleStageRocketGeometry)VehicleTestData.replace(source,"fins.root_chord_m",.7);
        source=(SingleStageRocketGeometry)VehicleTestData.replace(source,"fins.tip_leading_edge_offset_x_m",-.3);
        var g=resolver.resolve(source);
        assertEquals(0,g.finTipLeadingEdgeXGeoM()); assertEquals(1,g.finRootTrailingEdgeXGeoM());
        assertEquals(resolver.resolve(VehicleTestData.change("motor_attachment.motor_overhang_m",100.)).overallLengthM(),1.);
        var touch=resolver.resolve(VehicleTestData.change("motor_attachment.centering_rings.axial_thickness_m",.06));
        assertEquals(touch.frontCenteringRingEndXGeoM(),touch.rearCenteringRingStartXGeoM());
        assertEquals(.88,resolver.resolve(VehicleTestData.change("motor_attachment.motor_overhang_m",-.12)).motorAftReferenceXGeoM());
    }
    @ParameterizedTest @CsvSource({"nose.wall_thickness_m,0,NON_POSITIVE_WALL_THICKNESS",
        "body.wall_thickness_m,.05,BODY_WALL_TOO_THICK","nose.wall_thickness_m,1e-300,INVALID_DERIVED_VOLUME",
        "body.wall_thickness_m,1e-300,INVALID_DERIVED_VOLUME","fins.root_leading_edge_x_geo_m,.29,FIN_ROOT_OUTSIDE_BODY",
        "fins.root_leading_edge_x_geo_m,.83,FIN_ROOT_OUTSIDE_BODY","fins.tip_leading_edge_offset_x_m,-.73,FIN_EXTENDS_BEFORE_NOSE",
        "fins.tip_chord_m,-.1,NEGATIVE_TIP_CHORD","motor_attachment.mount_tube.length_m,.71,MOTOR_MOUNT_OUTSIDE_BODY",
        "motor_attachment.mount_tube.inner_diameter_m,.094,MOTOR_MOUNT_TOO_LARGE_FOR_BODY",
        "motor_attachment.mount_tube.aft_recess_m,-.001,NEGATIVE_MOUNT_AFT_RECESS",
        "motor_attachment.motor_overhang_m,-.121,MOTOR_AFT_REFERENCE_BEFORE_MOUNT",
        "motor_attachment.centering_rings.axial_thickness_m,.061,CENTERING_RINGS_OVERLAP"})
    void relationships(String path,double value,String code) {
        assertEquals(code,assertThrows(GeometryValidationError.class,()->resolver.resolve(VehicleTestData.change(path,value))).errorCode());
    }
    @ParameterizedTest @ValueSource(strings={"airframe_diameter_m","nose.length_m","body.length_m","fins.root_chord_m","fins.semi_span_m","fins.thickness_m"})
    void dimensions(String path) {
        for(double value:new double[]{0,-1}) {
            var error=assertThrows(GeometryValidationError.class,()->resolver.resolve(VehicleTestData.change(path,value)));
            assertEquals("NON_POSITIVE_DIMENSION",error.errorCode()); assertEquals(path,error.fieldName()); assertEquals(value,error.value());
        }
    }
    @Test void selectorsAndIntegerCountAreNotCoerced() {
        for(Object count:new Object[]{0,1,2,-1,2.5,3.5,true,4.0}) {
            assertEquals("INVALID_FIN_COUNT",assertThrows(GeometryValidationError.class,()->resolver.resolve(VehicleTestData.change("fins.fin_count",count))).errorCode());
        }
        for(int count:new int[]{3,4,8}) { assertEquals(count,resolver.resolve(VehicleTestData.change("fins.fin_count",count)).source().fins().finCount()); }
        String[] paths={"reference_geometry_policy","fins.cross_section","fins.angular_arrangement","nose.construction_mode"};
        String[] codes={"INVALID_REFERENCE_GEOMETRY_POLICY","INVALID_FIN_CROSS_SECTION","INVALID_FIN_ANGULAR_ARRANGEMENT","INVALID_NOSE_CONSTRUCTION"};
        for(int i=0;i<paths.length;i++) {
            final int index=i;
            for(Object invalid:new Object[]{null,"other",1}) {
                assertEquals(codes[i],assertThrows(GeometryValidationError.class,()->resolver.resolve(VehicleTestData.change(paths[index],invalid))).errorCode());
            }
        }
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void nonfinite(double value) {
        for(String path:new String[]{"airframe_diameter_m","nose.length_m","body.length_m","fins.root_chord_m",
            "fins.tip_chord_m","fins.semi_span_m","fins.thickness_m","fins.root_leading_edge_x_geo_m","fins.tip_leading_edge_offset_x_m",
            "fins.fin_count","nose.wall_thickness_m","body.wall_thickness_m","motor_attachment.mount_tube.length_m",
            "motor_attachment.mount_tube.inner_diameter_m","motor_attachment.mount_tube.wall_thickness_m",
            "motor_attachment.mount_tube.aft_recess_m","motor_attachment.motor_overhang_m","motor_attachment.centering_rings.axial_thickness_m"}) {
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->resolver.resolve(VehicleTestData.change(path,value))).getClass());
        }
    }
    @Test void numericalDomainAndOrder() {
        for(double diameter:new double[]{1e308,1e-300}) {
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->resolver.resolve(VehicleTestData.change("airframe_diameter_m",diameter))).getClass());
        }
        var invalid=VehicleTestData.change("airframe_diameter_m",-1.);
        invalid=(SingleStageRocketGeometry)VehicleTestData.replace(invalid,"fins.tip_chord_m",Double.NaN);
        final var source=invalid;
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->resolver.resolve(source)).getClass());
    }
}
