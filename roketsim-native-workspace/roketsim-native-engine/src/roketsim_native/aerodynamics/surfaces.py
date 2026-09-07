"""Yüzey bitişi/pürüzlülüğü tasarım veya kaynak verisidir; bulk material değildir.

Malzeme yoğunluğundan finish seçilmez. Preset katalog ve drag hesabı yoktur.
Her üretim sayısı standart, tasarım girdisi, doğrulanmış kaynak veya açık model
parametresi olmalıdır; test ölçüleri üretim varsayılanına dönüştürülmez.
"""

from dataclasses import dataclass

from roketsim_native.math.numerical import require_finite

__all__ = ("SurfaceValidationError", "SurfaceFinish", "SingleStageRocketAerodynamicSurfaces")


class SurfaceValidationError(ValueError):
    """Sonlu fakat geçersiz yüzey verisi için yapılandırılmış hata."""

    def __init__(self, *, error_code: str, field_name: str, value: object) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class SurfaceFinish:
    """Dış bitiş adı ve eşdeğer aerodinamik pürüzlülük (m); sıfır geçerlidir."""

    name: str
    equivalent_roughness_m: float

    def __post_init__(self) -> None:
        require_finite(self.equivalent_roughness_m, name="equivalent_roughness_m")
        if not isinstance(self.name, str) or not self.name.strip():
            raise SurfaceValidationError(error_code="EMPTY_SURFACE_FINISH_NAME",
                field_name="name", value=self.name)
        if self.equivalent_roughness_m < 0.0:
            raise SurfaceValidationError(error_code="NEGATIVE_EQUIVALENT_ROUGHNESS",
                field_name="equivalent_roughness_m", value=self.equivalent_roughness_m)


@dataclass(frozen=True, slots=True)
class SingleStageRocketAerodynamicSurfaces:
    """Üç bağımsız zorunlu yüzey seçimi; aynı immutable finish paylaşılabilir."""

    nose: SurfaceFinish
    body: SurfaceFinish
    fins: SurfaceFinish
