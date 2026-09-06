"""NAT-010A / FLOW-001: roketin hava kütlesine göre WORLD hız vektörü.

WORLD = ENU (East/North/Up), birimler m/s. Hava girdisi fiziksel TOWARD
air-mass velocity'dir: relative_velocity = rocket_velocity - airmass_velocity.
FROM yönü gibi ters çevrilmez. Sıfır bağıl hız geçerlidir; norm/airspeed ve
scalar flight conditions NAT-010B'ye aittir. Environment lookup ve BODY
dönüşümü burada yapılmaz.
"""

import numpy as np
from numpy.typing import ArrayLike as _ArrayLike
from numpy.typing import NDArray as _NDArray

from roketsim_native.math.vectors import as_vector

__all__ = ("RelativeFlowCalculator",)


class RelativeFlowCalculator:
    """Fiziksel state taşımayan, parametresiz WORLD relative-flow hesaplayıcısı."""

    __slots__ = ()

    def evaluate(
        self,
        *,
        rocket_velocity_world_m_s: _ArrayLike,
        airmass_velocity_world_m_s: _ArrayLike,
    ) -> _NDArray[np.float64]:
        """İki finite 3-vektörü doğrula; bağımsız relative_velocity_world_m_s döndür.

        Girdiler değişmez; temsil edilemeyen non-finite çıkarım sonucu da
        mevcut vector validation üzerinden ValueError üretir, düzeltilmez.
        """
        rocket = as_vector(
            rocket_velocity_world_m_s, size=3, name="rocket_velocity_world_m_s"
        )
        airmass = as_vector(
            airmass_velocity_world_m_s, size=3, name="airmass_velocity_world_m_s"
        )
        with np.errstate(over="ignore"):
            relative_velocity_world_m_s = rocket - airmass
        return as_vector(
            relative_velocity_world_m_s, size=3, name="relative_velocity_world_m_s"
        )
