"""GEO14: Materials density'yi sahiplenir; geometry veya mass hesaplamaz."""

from dataclasses import dataclass

from roketsim_native.math.numerical import require_finite

__all__ = ("BulkMaterial", "SingleStageRocketMaterials", "MaterialValidationError")


class MaterialValidationError(ValueError):
    """Finite invalid malzeme tanımı için structured hata."""

    def __init__(self, *, error_code: str, field_name: str, value: str | float) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class BulkMaterial:
    """Uniform bulk density, kg/m³; doğrulama construction sınırındadır."""

    name: str
    density_kg_m3: float

    def __post_init__(self) -> None:
        require_finite(self.density_kg_m3, name="density_kg_m3")
        if self.density_kg_m3 <= 0.0:
            raise MaterialValidationError(error_code="NON_POSITIVE_DENSITY",
                field_name="density_kg_m3", value=self.density_kg_m3)
        if not self.name.strip():
            raise MaterialValidationError(error_code="EMPTY_MATERIAL_NAME",
                field_name="name", value=self.name)


@dataclass(frozen=True, slots=True)
class SingleStageRocketMaterials:
    """Üç bağımsız ve zorunlu kullanıcı seçimi; component default'u yoktur."""

    nose: BulkMaterial
    body: BulkMaterial
    fins: BulkMaterial
