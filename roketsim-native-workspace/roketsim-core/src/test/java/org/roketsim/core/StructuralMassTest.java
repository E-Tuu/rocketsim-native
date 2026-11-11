package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.mass.*;
import static org.junit.jupiter.api.Assertions.*;

class StructuralMassTest {
    private final ResolvedRocketGeometry geometry=new GeometryResolver().resolve(VehicleTestData.geometry());
    private final SingleStageRocketMaterials materials=VehicleTestData.materials();
    private final StructuralMassPropertiesCalculator calculator=new StructuralMassPropertiesCalculator();
    private ResolvedRocketGeometry change(ResolvedRocketGeometry g,String name,double value) {
        return (ResolvedRocketGeometry)VehicleTestData.replace(g,name,value);
    }
    @Test void componentAndAggregateFixture() {
        var result=calculator.evaluate(geometry,materials);
        GeometryResolverTest.close(.09631183149995425,result.nose().massKg(),3e-14);
        GeometryResolverTest.close(.29309802820931397,result.body().massKg(),3e-14);
        GeometryResolverTest.close(.127296,result.fins().massKg(),3e-14);
        GeometryResolverTest.close(.007690618815987807,result.motorMount().massKg(),3e-14);
        GeometryResolverTest.close(.026452524302491415,result.centeringRings().massKg(),3e-14);
        GeometryResolverTest.close(.5508490028277474,result.structureMassKg(),3e-14);
        GeometryResolverTest.close(.6266199705547180,result.structureCgXGeoM(),3e-14);
        ComponentMassProperties[] components={result.nose(),result.body(),result.fins(),result.motorMount(),result.centeringRings()};
        BulkMaterial[] selected={materials.nose(),materials.body(),materials.fins(),materials.motorMount(),materials.centeringRings()};
        double[] volumes={geometry.noseMaterialVolumeM3(),geometry.bodyMaterialVolumeM3(),geometry.finSetMaterialVolumeM3(),geometry.motorMountMaterialVolumeM3(),geometry.centeringRingPairMaterialVolumeM3()};
        double[] centers={geometry.noseVolumeCentroidXGeoM(),geometry.bodyVolumeCentroidXGeoM(),geometry.finSetVolumeCentroidXGeoM(),geometry.motorMountVolumeCentroidXGeoM(),geometry.centeringRingPairVolumeCentroidXGeoM()};
        double total=0,moment=0;
        for(int i=0;i<5;i++) {
            assertSame(selected[i],components[i].material());
            assertEquals(selected[i].densityKgM3()*volumes[i],components[i].massKg());
            assertEquals(centers[i],components[i].cgXGeoM());
            total+=components[i].massKg(); moment+=components[i].massKg()*components[i].cgXGeoM();
        }
        assertEquals(total,result.structureMassKg()); assertEquals(moment/total,result.structureCgXGeoM());
        assertEquals(result,new StructuralMassPropertiesCalculator().evaluate(geometry,materials));
    }
    @ParameterizedTest @ValueSource(strings={"nose","body","fins","motor_mount","centering_rings"})
    void independentMaterialsAndResolvedAuthority(String name) throws Exception {
        var original=calculator.evaluate(geometry,materials);
        var selected=(BulkMaterial)SingleStageRocketMaterials.class.getMethod(VehicleTestData.camel(name)).invoke(materials);
        var twice=new BulkMaterial("Double",2*selected.densityKgM3());
        var changed=(SingleStageRocketMaterials)VehicleTestData.replace(materials,name,twice);
        var result=calculator.evaluate(geometry,changed);
        for(String component:new String[]{"nose","body","fins","motor_mount","centering_rings"}) {
            var before=(ComponentMassProperties)StructuralMassProperties.class.getMethod(VehicleTestData.camel(component)).invoke(original);
            var after=(ComponentMassProperties)StructuralMassProperties.class.getMethod(VehicleTestData.camel(component)).invoke(result);
            assertEquals(before.massKg()*(component.equals(name)?2:1),after.massKg());
            assertEquals(before.cgXGeoM(),after.cgXGeoM());
        }
    }
    @Test void resolvedVolumesAreNotReconstructedAndNoDefaultMaterial() {
        var changed=change(change(geometry,"nose_material_volume_m3",.0002),"nose_volume_centroid_x_geo_m",.25);
        var result=calculator.evaluate(changed,materials);
        assertSame(geometry.source(),changed.source()); assertEquals(1050*.0002,result.nose().massKg()); assertEquals(.25,result.nose().cgXGeoM());
        var unit=new BulkMaterial("Unit",100); var twice=new BulkMaterial("Double",200);
        var first=calculator.evaluate(geometry,new SingleStageRocketMaterials(unit,unit,unit,unit,unit));
        var second=calculator.evaluate(geometry,new SingleStageRocketMaterials(twice,twice,twice,twice,twice));
        assertEquals(first.structureCgXGeoM(),second.structureCgXGeoM()); assertEquals(2*first.structureMassKg(),second.structureMassKg());
        assertThrows(NullPointerException.class,()->calculator.evaluate(geometry,new SingleStageRocketMaterials(null,unit,unit,unit,unit)));
    }
    @ParameterizedTest @ValueSource(strings={"nose_material_volume_m3","body_material_volume_m3","fin_set_material_volume_m3","motor_mount_material_volume_m3","centering_ring_pair_material_volume_m3"})
    void invalidVolumes(String field) {
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,1e308}) {
            assertEquals("INVALID_COMPONENT_MASS",assertThrows(MassValidationError.class,()->calculator.evaluate(change(geometry,field,value),materials)).errorCode());
        }
    }
    @ParameterizedTest @ValueSource(strings={"nose_volume_centroid_x_geo_m","body_volume_centroid_x_geo_m","fin_set_volume_centroid_x_geo_m","motor_mount_volume_centroid_x_geo_m","centering_ring_pair_volume_centroid_x_geo_m"})
    void invalidCentroids(String field) {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertEquals("INVALID_COMPONENT_CG",assertThrows(MassValidationError.class,()->calculator.evaluate(change(geometry,field,value),materials)).errorCode());
        }
    }
    @ParameterizedTest @ValueSource(doubles={0,-1,Double.NaN,Double.POSITIVE_INFINITY})
    void extent(double value) {
        var error=assertThrows(MassValidationError.class,()->calculator.evaluate(change(geometry,"overall_length_m",value),materials));
        assertEquals("INVALID_GEOMETRY_EXTENT",error.errorCode()); assertEquals("overall_length_m",error.fieldName());
    }
    @Test void aggregateOverflowUnderflowAndExtent() {
        var unit=new BulkMaterial("Unit",1); var all=new SingleStageRocketMaterials(unit,unit,unit,unit,unit);
        var big=change(change(change(geometry,"nose_material_volume_m3",1e308),"body_material_volume_m3",1e308),"fin_set_material_volume_m3",1e308);
        assertEquals("INVALID_STRUCTURAL_MASS",assertThrows(MassValidationError.class,()->calculator.evaluate(big,all)).errorCode());
        var moment=change(change(change(geometry,"nose_material_volume_m3",1e307),"nose_volume_centroid_x_geo_m",100),"overall_length_m",200);
        assertEquals("INVALID_STRUCTURAL_CG",assertThrows(MassValidationError.class,()->calculator.evaluate(moment,all)).errorCode());
        var tiny=new BulkMaterial("Tiny",Double.MIN_VALUE);
        assertEquals("INVALID_COMPONENT_MASS",assertThrows(MassValidationError.class,()->calculator.evaluate(change(geometry,"nose_material_volume_m3",Double.MIN_VALUE),new SingleStageRocketMaterials(tiny,unit,unit,unit,unit))).errorCode());
        var outside=change(change(change(geometry,"nose_volume_centroid_x_geo_m",10),"body_volume_centroid_x_geo_m",10),"fin_set_volume_centroid_x_geo_m",10);
        assertEquals("STRUCTURAL_CG_OUTSIDE_EXTENT",assertThrows(MassValidationError.class,()->calculator.evaluate(outside,materials)).errorCode());
        assertTrue(Double.isFinite(calculator.evaluate(change(geometry,"nose_volume_centroid_x_geo_m",-.01),materials).structureCgXGeoM()));
    }
    @Test void immutableSchemaAndNoRuntimeMotor() {
        assertTrue(ComponentMassProperties.class.isRecord()); assertTrue(StructuralMassProperties.class.isRecord());
        assertEquals(3,ComponentMassProperties.class.getRecordComponents().length);
        assertEquals(7,StructuralMassProperties.class.getRecordComponents().length);
        assertEquals(0,StructuralMassPropertiesCalculator.class.getDeclaredFields().length);
        assertEquals(-1,new ComponentMassProperties(materials.nose(),-1,Double.NaN).massKg());
    }
}
