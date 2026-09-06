"""NAT-009H: WORLD/ENU içinde sabit TOWARD air-mass velocity, SI m/s.

Bileşen sırası East/North/Up'tır; sonuç havanın fiziksel hareket yönüdür,
rocket-relative velocity değildir. Dikey bileşen korunur. Meteorolojik FROM
dönüşümü core dışındaki adapter'a; multi-level ve turbulence sonraki gate'lere
aittir. Modeller konumdan ve zamandan bağımsızdır.
"""

import numpy as np
from numpy.typing import ArrayLike as _ArrayLike
from numpy.typing import NDArray as _NDArray

from roketsim_native.math.vectors import as_vector

__all__ = ("NoWindModel", "ConstantWindModel")


class NoWindModel:
    """İlk trajectory-demo seçimi: havanın WORLD hızını sıfır kabul eder."""

    __slots__ = ()

    def evaluate(self) -> _NDArray[np.float64]:
        """Bağımsız bir sıfır airmass_velocity_world_m_s array'i döndür."""
        return as_vector((0.0, 0.0, 0.0), size=3, name="airmass_velocity_world_m_s")


class ConstantWindModel:
    """Finite 3D TOWARD hızını değiştirmeden korur; hız limiti veya fallback yok."""

    __slots__ = ("_airmass_velocity_world_m_s",)

    def __init__(self, *, airmass_velocity_world_m_s: _ArrayLike) -> None:
        """SI WORLD hızını doğrula ve caller mutation'dan bağımsız kopyala."""
        self._airmass_velocity_world_m_s = as_vector(
            airmass_velocity_world_m_s, size=3, name="airmass_velocity_world_m_s"
        )

    def evaluate(self) -> _NDArray[np.float64]:
        """İç state'i paylaşmayan East/North/Up hız kopyasını döndür."""
        return self._airmass_velocity_world_m_s.copy()
