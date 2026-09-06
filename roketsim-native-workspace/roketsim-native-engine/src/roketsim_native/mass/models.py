"""Geometry hacim otoritesini kopyalamayan immutable structural mass sonuçları."""

from dataclasses import dataclass

from roketsim_native.materials.models import BulkMaterial

__all__ = ("ComponentMassProperties", "StructuralMassProperties", "MassValidationError")


class MassValidationError(ValueError):
    """Derived mass invariant hatası; clamp veya fallback kararı içermez."""

    def __init__(self, *, error_code: str, field_name: str, value: float) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class ComponentMassProperties:
    """Uniform component CG'si x_geo'dur; seçilen material provenance korunur."""

    material: BulkMaterial
    mass_kg: float
    cg_x_geo_m: float


@dataclass(frozen=True, slots=True)
class StructuralMassProperties:
    """Nose/body/fins/mount/ring yapısı; gerçek motor hariçtir.

    Centering rings iki ring'in birleşik katkısıdır; total rocket mass değildir.
    """

    nose: ComponentMassProperties
    body: ComponentMassProperties
    fins: ComponentMassProperties
    motor_mount: ComponentMassProperties
    centering_rings: ComponentMassProperties
    structure_mass_kg: float
    structure_cg_x_geo_m: float
