"""US Standard Atmosphere 1976 lower dry-air baseline (NAT-009C).

Girdi geopotential height (m); domain [-5000, 84852] uçları dahildir.
İlk layer base'i 0 m kalır; negatif yükseklik o base'e göre hesaplanır.
[LITERATURE / USSA76], local ATM-001..005 ve frozen NAT-009C kararları:
sonraki base T/p değerleri önceki layer endpoint'inden recursive türetilir.
Bu immutable initialization verisi runtime ayarı veya fiziksel cache değildir.
Clamp/extrapolation yoktur. Sonuç derived snapshot'tır; authoritative state
değildir. Ses hızı ve viskozite NAT-009D kapsamındadır, burada hesaplanmaz.
"""

from bisect import bisect_right
from dataclasses import dataclass
from math import exp
from typing import Final

from roketsim_native.math.numerical import require_finite


__all__ = (
    "USStandardAtmosphere1976Lower",
    "DryAirAtmosphereState",
    "AtmosphereDomainError",
    "STANDARD_SEA_LEVEL_TEMPERATURE_K",
    "STANDARD_SEA_LEVEL_PRESSURE_PA",
    "STANDARD_GRAVITY_M_S2",
    "DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K",
    "USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M",
    "USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M",
)

STANDARD_SEA_LEVEL_TEMPERATURE_K: Final[float] = 288.15
STANDARD_SEA_LEVEL_PRESSURE_PA: Final[float] = 101_325.0
STANDARD_GRAVITY_M_S2: Final[float] = 9.80665
DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K: Final[float] = 287.053
USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M: Final[float] = -5_000.0
USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M: Final[float] = 84_852.0


@dataclass(frozen=True, slots=True)
class DryAirAtmosphereState:
    """SI birimli, immutable ve yalnız T/p/rho içeren derived snapshot."""

    temperature_K: float
    pressure_Pa: float
    density_kg_m3: float


class AtmosphereDomainError(ValueError):
    """Model domain hatası; warning, abort veya fallback kararı vermez."""

    def __init__(self, *, requested_geopotential_height_m: float) -> None:
        self.requested_geopotential_height_m = requested_geopotential_height_m
        self.minimum_geopotential_height_m = USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M
        self.maximum_geopotential_height_m = USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M
        super().__init__(
            "U.S. Standard Atmosphere 1976 lower atmosphere: "
            f"requested geopotential_height_m={requested_geopotential_height_m!r}; "
            f"supported domain [{self.minimum_geopotential_height_m}, "
            f"{self.maximum_geopotential_height_m}] m"
        )


@dataclass(frozen=True, slots=True)
class _AtmosphereLayer:
    """Private layer base'i; sonraki base sıcaklık/basıncı derived veridir."""

    base_geopotential_height_m: float
    lapse_rate_K_per_m: float
    base_temperature_K: float
    base_pressure_Pa: float


_REFERENCE_HEIGHTS_M: Final = (
    0.0, 11000.0, 20000.0, 32000.0, 47000.0, 51000.0, 71000.0, 84852.0,
)
_LAPSE_RATES_K_PER_M: Final = (-0.0065, 0.0, 0.0010, 0.0028, 0.0, -0.0028, -0.0020)


def _temperature_pressure(
    layer: _AtmosphereLayer, geopotential_height_m: float
) -> tuple[float, float]:
    """Layer bağıntılarını uygula; exact sıfır lapse için exponential branch kullan."""
    delta_height_m = geopotential_height_m - layer.base_geopotential_height_m
    temperature_K = layer.base_temperature_K + layer.lapse_rate_K_per_m * delta_height_m
    if layer.lapse_rate_K_per_m == 0.0:
        pressure_Pa = layer.base_pressure_Pa * exp(
            -STANDARD_GRAVITY_M_S2 * delta_height_m
            / (DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K * layer.base_temperature_K)
        )
    else:
        pressure_Pa = layer.base_pressure_Pa * (
            temperature_K / layer.base_temperature_K
        ) ** (
            -STANDARD_GRAVITY_M_S2
            / (DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K * layer.lapse_rate_K_per_m)
        )
    return temperature_K, pressure_Pa


def _build_layers() -> tuple[_AtmosphereLayer, ...]:
    """Yalnız sea-level T/p'den yedi computational layer base'ini türet."""
    layers = []
    temperature_K = STANDARD_SEA_LEVEL_TEMPERATURE_K
    pressure_Pa = STANDARD_SEA_LEVEL_PRESSURE_PA
    for index, lapse_rate in enumerate(_LAPSE_RATES_K_PER_M):
        if layers:
            temperature_K, pressure_Pa = _temperature_pressure(
                layers[-1], _REFERENCE_HEIGHTS_M[index]
            )
        layers.append(_AtmosphereLayer(
            _REFERENCE_HEIGHTS_M[index], lapse_rate, temperature_K, pressure_Pa
        ))
    return tuple(layers)


_LAYERS: Final = _build_layers()


class USStandardAtmosphere1976Lower:
    """Parametresiz, deterministik dry-air provider; override veya runtime state yok."""

    __slots__ = ()

    def evaluate(self, *, geopotential_height_m: float) -> DryAirAtmosphereState:
        """Geopotential height'tan T/p/rho üret; domain dışında açık hata ver."""
        geopotential_height_m = float(require_finite(
            geopotential_height_m, name="geopotential_height_m"
        ))
        if not (
            USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M <= geopotential_height_m
            <= USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M
        ):
            raise AtmosphereDomainError(
                requested_geopotential_height_m=geopotential_height_m
            )
        # İç sınır üst layer'a aittir; terminal 84852 yeni computational layer değildir.
        layer_index = bisect_right(_REFERENCE_HEIGHTS_M[1:-1], geopotential_height_m)
        temperature_K, pressure_Pa = _temperature_pressure(
            _LAYERS[layer_index], geopotential_height_m
        )
        # Defect durumunda sıfır sıcaklıkla bölmeden önce T/p invariant'larını denetle.
        for name, value in (
            ("temperature_K", temperature_K),
            ("pressure_Pa", pressure_Pa),
        ):
            require_finite(value, name=name)
            if value <= 0.0:
                raise ValueError(f"{name} must be positive; got {value!r}")
        density_kg_m3 = require_finite(
            pressure_Pa / (DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K * temperature_K),
            name="density_kg_m3",
        )
        if density_kg_m3 <= 0.0:
            raise ValueError(f"density_kg_m3 must be positive; got {density_kg_m3!r}")
        return DryAirAtmosphereState(temperature_K, pressure_Pa, density_kg_m3)
