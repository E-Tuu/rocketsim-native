"""NAT-009F / GRAV-001: ilk 3DOF demo için sabit standard-gravity baseline.

WORLD = ENU; +z_W Up olduğundan g_W=[0,0,-g0] m/s²'dir. g0 için tek
production otoritesi atmosphere.STANDARD_GRAVITY_M_S2 kullanılır.
Model bilerek konumdan, zamandan ve atmosfer state'inden bağımsızdır.
WGS84 gravity ve Coriolis sonraki gate'lere aittir; burada dynamics yoktur.
"""

import numpy as np
from numpy.typing import NDArray as _NDArray

from roketsim_native.environment import atmosphere as _atmosphere
from roketsim_native.math.vectors import as_vector


__all__ = ("ConstantGravityModel",)


class ConstantGravityModel:
    """Parametresiz, konum bağımsız standard-g modeli; custom gravity seçeneği yok."""

    __slots__ = ()

    def evaluate(self) -> _NDArray[np.float64]:
        """Bağımsız bir WORLD/ENU gravity_world_m_s2 array'i döndür."""
        return as_vector(
            (0.0, 0.0, -_atmosphere.STANDARD_GRAVITY_M_S2),
            size=3,
            name="gravity_world_m_s2",
        )
