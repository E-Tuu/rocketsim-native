"""RoketSim Native için dondurulmuş coordinate-frame convention'ları.

``WORLD_ENU`` eksen sırası East, North, Up; ``BODY`` eksen sırası ise x_B,
y_B, z_B'dir. BODY z_B longitudinal/thrust/roll eksenidir. Rapor tarafından
tanımlanmayan x_B ve y_B yönelim ayrıntıları bu modülde varsayılmaz.

Geometry ``x_geo`` bir 3-D dynamics frame değildir: nose tip origin'inden
nose -> tail yönünde artan ayrı bir axial coordinate'tir. Bu nedenle x_geo
ile BODY z_B aynı identifier veya değişken değildir.

Sonraki attitude katmanı için dondurulmuş quaternion yönü q_BW: BODY -> WORLD
olarak belgelenir. Bu modül quaternion temsili, rotation matrix veya vector
transform davranışı sağlamaz.

İleride bilimsel değişkenlerde ``position_W``, ``velocity_W``, ``wind_W``,
``force_B``, ``moment_B`` ve ``omega_W`` gibi frame suffix'leri tercih edilir.
Farklı frame'lerdeki vektörler sessizce karıştırılmamalıdır; gerçek enforcement
domain ve transform katmanlarının sorumluluğundadır.
"""

from enum import Enum
from types import MappingProxyType
from typing import Final, Mapping, NamedTuple


__all__ = (
    "ReferenceFrame",
    "AxisConvention",
    "FRAME_AXES",
    "GEOMETRY_AXIAL_CONVENTION",
)


class ReferenceFrame(str, Enum):
    """Physics core tarafından tanınan 3-D dynamics frame identifier'ları."""

    WORLD_ENU = "WORLD_ENU"
    BODY = "BODY"


class AxisConvention(NamedTuple):
    """Bir array index'ine karşılık gelen immutable axis metadata kaydı."""

    identifier: str
    meaning: str


_FrameAxes = tuple[AxisConvention, AxisConvention, AxisConvention]


FRAME_AXES: Final[Mapping[ReferenceFrame, _FrameAxes]] = MappingProxyType(
    {
        ReferenceFrame.WORLD_ENU: (
            AxisConvention("x_W", "East"),
            AxisConvention("y_W", "North"),
            AxisConvention("z_W", "Up"),
        ),
        ReferenceFrame.BODY: (
            AxisConvention("x_B", "lateral"),
            AxisConvention("y_B", "lateral"),
            AxisConvention("z_B", "longitudinal / thrust / roll axis"),
        ),
    }
)


GEOMETRY_AXIAL_CONVENTION: Final[Mapping[str, str]] = MappingProxyType(
    {
        "origin": "nose tip",
        "coordinate": "x_geo",
        "positive_direction": "nose -> tail",
    }
)
