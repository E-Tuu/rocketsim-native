"""NAT-011B: MASS-001 rho*V ve MASS-002 mass-weighted CG.

GEO16 material volume ve volume centroid Geometry otoritesidir; yeniden
türetilmez. Her component uniform-density olduğu için CG=volume centroid.
x_geo nose tip'ten tail'e artar. Materials seçimi kullanıcıya aittir; katalog
varsayımı yoktur. Motor, inertia, measured/override fizik bu gate'te yoktur.
NAT-011B.1 mount ve birleşik ring-pair katkılarını aynı MASS-001/002 ile ekler.
Geometry tek volume/volume-centroid, Materials tek density otoritesidir.
NAT-011C motor/propulsion ve zamana bağlı toplam roket kütlesini ekleyecektir.
"""

from math import isfinite

from roketsim_native.geometry.resolver import ResolvedRocketGeometry
from roketsim_native.materials.models import SingleStageRocketMaterials
from roketsim_native.mass.models import (
    ComponentMassProperties, StructuralMassProperties, MassValidationError,
)
from roketsim_native.math.numerical import require_finite

__all__ = ("StructuralMassPropertiesCalculator",)


class StructuralMassPropertiesCalculator:
    """Parametresiz, mutable fiziksel state tutmayan structural hesaplayıcı."""

    __slots__ = ()

    def evaluate(self, *, resolved_geometry: ResolvedRocketGeometry,
                 materials: SingleStageRocketMaterials) -> StructuralMassProperties:
        """Geometry hacimlerini tüket; derived non-finite sonuçları mass hatası yap."""
        geometry = resolved_geometry
        components = []
        for name, material, volume, centroid in (
            ("nose", materials.nose, geometry.nose_material_volume_m3, geometry.nose_volume_centroid_x_geo_m),
            ("body", materials.body, geometry.body_material_volume_m3, geometry.body_volume_centroid_x_geo_m),
            ("fins", materials.fins, geometry.fin_set_material_volume_m3, geometry.fin_set_volume_centroid_x_geo_m),
            ("motor_mount", materials.motor_mount, geometry.motor_mount_material_volume_m3, geometry.motor_mount_volume_centroid_x_geo_m),
            ("centering_rings", materials.centering_rings, geometry.centering_ring_pair_material_volume_m3, geometry.centering_ring_pair_volume_centroid_x_geo_m),
        ):
            density = float(require_finite(material.density_kg_m3, name=f"{name}.density_kg_m3"))
            mass = density * float(volume)
            if not isfinite(mass) or mass <= 0.0:
                raise MassValidationError(error_code="INVALID_COMPONENT_MASS",
                    field_name=f"{name}.mass_kg", value=mass)
            cg = float(centroid)
            if not isfinite(cg):
                raise MassValidationError(error_code="INVALID_COMPONENT_CG",
                    field_name=f"{name}.cg_x_geo_m", value=cg)
            components.append(ComponentMassProperties(material, mass, cg))
        nose, body, fins, motor_mount, centering_rings = components
        structure_mass = (nose.mass_kg + body.mass_kg + fins.mass_kg
                          + motor_mount.mass_kg + centering_rings.mass_kg)
        if not isfinite(structure_mass) or structure_mass <= 0.0:
            raise MassValidationError(error_code="INVALID_STRUCTURAL_MASS",
                field_name="structure_mass_kg", value=structure_mass)
        structure_cg = (nose.mass_kg * nose.cg_x_geo_m + body.mass_kg * body.cg_x_geo_m
                        + fins.mass_kg * fins.cg_x_geo_m
                        + motor_mount.mass_kg * motor_mount.cg_x_geo_m
                        + centering_rings.mass_kg * centering_rings.cg_x_geo_m) / structure_mass
        if not isfinite(structure_cg):
            raise MassValidationError(error_code="INVALID_STRUCTURAL_CG",
                field_name="structure_cg_x_geo_m", value=structure_cg)
        extent = float(geometry.overall_length_m)
        if not isfinite(extent) or extent <= 0.0:
            raise MassValidationError(error_code="INVALID_GEOMETRY_EXTENT",
                field_name="overall_length_m", value=extent)
        if not 0.0 <= structure_cg <= extent:
            raise MassValidationError(error_code="STRUCTURAL_CG_OUTSIDE_EXTENT",
                field_name="structure_cg_x_geo_m", value=structure_cg)
        return StructuralMassProperties(nose, body, fins, motor_mount, centering_rings,
                                        structure_mass, structure_cg)
