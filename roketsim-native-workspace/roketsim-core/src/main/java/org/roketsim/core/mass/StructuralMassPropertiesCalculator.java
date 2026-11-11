package org.roketsim.core.mass;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        return aggregate(geometry,nose,body,fins,motorMount,rings);
    }

    /** Selects measured fields on a valid calculated baseline, then aggregates in structural order. */
    public StructuralMassProperties evaluateWithMeasurements(ResolvedRocketGeometry geometry,
            SingleStageRocketMaterials materials, List<StructuralMassOverride> overrides) {
        required(geometry, "geometry");
        required(materials, "materials");
        required(overrides, "overrides");
        required(materials.nose(), "materials.nose");
        required(materials.body(), "materials.body");
        required(materials.fins(), "materials.fins");
        required(materials.motorMount(), "materials.motorMount");
        required(materials.centeringRings(), "materials.centeringRings");
        for (int i = 0; i < overrides.size(); i++) {
            String field = "overrides[" + i + "]";
            var override = overrides.get(i);
            required(override, field);
            required(override.massKg(), field + ".massKg");
            required(override.cgXGeoM(), field + ".cgXGeoM");
        }
        var calculated = evaluate(geometry, materials);
        if (overrides.isEmpty()) return calculated;

        var selected = new ComponentMassProperties[]{
            calculated.nose(), calculated.body(), calculated.fins(),
            calculated.motorMount(), calculated.centeringRings()
        };
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < overrides.size(); i++) {
            var override = overrides.get(i);
            String field = "overrides[" + i + "]";
            String id = override.componentId();
            int slot = id == null ? -1 : switch (id) {
                case "structure:nose" -> 0;
                case "structure:body" -> 1;
                case "structure:fins" -> 2;
                case "structure:motor-mount" -> 3;
                case "structure:centering-rings" -> 4;
                default -> -1;
            };
            if (slot < 0) throw new VehicleConfigurationError("UNSUPPORTED_OVERRIDE_TARGET", field + ".componentId", String.valueOf(id));
            if (!seen.add(id)) throw new VehicleConfigurationError("DUPLICATE_OVERRIDE_TARGET", field + ".componentId", id);
            if (override.massKg().isEmpty() && override.cgXGeoM().isEmpty())
                throw new VehicleConfigurationError("EMPTY_STRUCTURAL_OVERRIDE", field, id);

            var baseline = selected[slot];
            double mass = baseline.massKg();
            if (override.massKg().isPresent()) {
                var measurement = override.massKg().orElseThrow();
                mass = measurement.value();
                if (!Double.isFinite(mass) || mass <= 0)
                    throw new VehicleConfigurationError("INVALID_MEASURED_MASS", field + ".massKg", Double.toString(mass));
                requireReference(measurement.sourceReference(), field + ".massKg.sourceReference");
            }
            double cg = baseline.cgXGeoM();
            if (override.cgXGeoM().isPresent()) {
                var measurement = override.cgXGeoM().orElseThrow();
                cg = measurement.value();
                if (!Double.isFinite(cg) || cg < 0 || cg > geometry.overallLengthM())
                    throw new VehicleConfigurationError("INVALID_MEASURED_CG", field + ".cgXGeoM", Double.toString(cg));
                requireReference(measurement.sourceReference(), field + ".cgXGeoM.sourceReference");
            }
            selected[slot] = new ComponentMassProperties(baseline.material(), mass, cg);
        }
        return aggregate(geometry, selected[0], selected[1], selected[2], selected[3], selected[4]);
    }

    private static void required(Object value, String field) {
        if (value == null) throw new VehicleConfigurationError("MISSING_VEHICLE_INPUT", field, "required");
    }

    private static void requireReference(String value, String field) {
        if (value == null || value.isBlank())
            throw new VehicleConfigurationError("MISSING_PROPERTY_PROVENANCE", field, "measurement reference required");
    }

    private static StructuralMassProperties aggregate(ResolvedRocketGeometry geometry,
            ComponentMassProperties nose, ComponentMassProperties body, ComponentMassProperties fins,
            ComponentMassProperties motorMount, ComponentMassProperties rings) {
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
