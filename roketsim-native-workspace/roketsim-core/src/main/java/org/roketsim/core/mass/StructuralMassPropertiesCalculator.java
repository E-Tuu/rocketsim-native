package org.roketsim.core.mass;

import org.roketsim.core.geometry.ResolvedRocketGeometry;
import org.roketsim.core.materials.BulkMaterial;
import org.roketsim.core.materials.SingleStageRocketMaterials;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** MASS-001 rho*V ve MASS-002 weighted CG; geometry/density yeniden türetilmez. */
public final class StructuralMassPropertiesCalculator {
    /** Parametresiz ve stateless calculator. */
    public StructuralMassPropertiesCalculator() { }
    private static ComponentMassProperties component(String name,BulkMaterial material,double volume,double centroid) {
        double density=requireFinite(material.densityKgM3(),name+".density_kg_m3");
        double mass=density*volume;
        if(!Double.isFinite(mass)||mass<=0) { throw new MassValidationError("INVALID_COMPONENT_MASS",name+".mass_kg",mass); }
        if(!Double.isFinite(centroid)) { throw new MassValidationError("INVALID_COMPONENT_CG",name+".cg_x_geo_m",centroid); }
        return new ComponentMassProperties(material,mass,centroid);
    }
    /** @param geometry accepted resolved volume/centroid authority
     * @param materials beş explicit density/material seçimi
     * @return yalnız structural kütle ve nose-tip x_geo CG */
    public StructuralMassProperties evaluate(ResolvedRocketGeometry geometry,SingleStageRocketMaterials materials) {
        var nose=component("nose",materials.nose(),geometry.noseMaterialVolumeM3(),geometry.noseVolumeCentroidXGeoM());
        var body=component("body",materials.body(),geometry.bodyMaterialVolumeM3(),geometry.bodyVolumeCentroidXGeoM());
        var fins=component("fins",materials.fins(),geometry.finSetMaterialVolumeM3(),geometry.finSetVolumeCentroidXGeoM());
        var motorMount=component("motor_mount",materials.motorMount(),geometry.motorMountMaterialVolumeM3(),geometry.motorMountVolumeCentroidXGeoM());
        var rings=component("centering_rings",materials.centeringRings(),geometry.centeringRingPairMaterialVolumeM3(),geometry.centeringRingPairVolumeCentroidXGeoM());
        double mass=nose.massKg()+body.massKg()+fins.massKg()+motorMount.massKg()+rings.massKg();
        if(!Double.isFinite(mass)||mass<=0) { throw new MassValidationError("INVALID_STRUCTURAL_MASS","structure_mass_kg",mass); }
        double cg=(nose.massKg()*nose.cgXGeoM()+body.massKg()*body.cgXGeoM()+fins.massKg()*fins.cgXGeoM()
            +motorMount.massKg()*motorMount.cgXGeoM()+rings.massKg()*rings.cgXGeoM())/mass;
        if(!Double.isFinite(cg)) { throw new MassValidationError("INVALID_STRUCTURAL_CG","structure_cg_x_geo_m",cg); }
        double extent=geometry.overallLengthM();
        if(!Double.isFinite(extent)||extent<=0) { throw new MassValidationError("INVALID_GEOMETRY_EXTENT","overall_length_m",extent); }
        if(!(0<=cg && cg<=extent)) { throw new MassValidationError("STRUCTURAL_CG_OUTSIDE_EXTENT","structure_cg_x_geo_m",cg); }
        return new StructuralMassProperties(nose,body,fins,motorMount,rings,mass,cg);
    }
}
