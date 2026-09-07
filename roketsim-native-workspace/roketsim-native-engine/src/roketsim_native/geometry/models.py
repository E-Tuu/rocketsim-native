"""Immutable SI tasarım verileri; domain/yerleşim doğrulaması resolver sınırındadır.

Demo profilinde tek çap otoritesi airframe_diameter_m'dir; nose base ve body
outer diameter bağımsız saklanmaz. Full geometry/component tree ertelenmiştir.
NAT-011A.1 onaylı schema genişlemesidir: construction aynı component geometry'nin
parçasıdır; ikinci source yoktur. Volume centroid henüz CG değildir; density ve
mass yorumu NAT-011B'ye aittir.
"""

from dataclasses import dataclass
from enum import Enum

__all__ = ("ConicalNoseGeometry", "CylindricalBodyGeometry",
           "TrapezoidalFinSetGeometry", "SingleStageRocketGeometry", "NoseConstructionMode",
           "MotorMountTubeGeometry", "CenteringRingPairGeometry", "MotorAttachmentGeometry",
           "ReferenceGeometryPolicy", "FinCrossSection")


class ReferenceGeometryPolicy(str, Enum):
    """Yalnız dış eksenel simetrik airframe çapı; fin ve iç donanım hariçtir."""

    MAXIMUM_DIAMETER = "maximum_diameter"


class FinCrossSection(str, Enum):
    """Kalınlık yönündeki kenar kesiti; planformun kare olduğu anlamına gelmez."""

    SQUARE = "square"


class NoseConstructionMode(str, Enum):
    """Demo nose construction seçimi; varsayılan fiziksel mod yoktur."""

    SOLID = "SOLID"
    HOLLOW_SHELL = "HOLLOW_SHELL"


@dataclass(frozen=True, slots=True)
class ConicalNoseGeometry:
    """Konik nose; shell kalınlığı lateral yüzeye NORMAL ölçülür, m."""

    length_m: float
    construction_mode: NoseConstructionMode
    wall_thickness_m: float | None


@dataclass(frozen=True, slots=True)
class CylindricalBodyGeometry:
    """Zorunlu duvar kalınlıklı hollow cylindrical tube; solid seçeneği yoktur."""

    length_m: float
    wall_thickness_m: float


@dataclass(frozen=True, slots=True)
class TrapezoidalFinSetGeometry:
    """Root/tip chord ekseneldir; semi-span body yüzeyinden tip'e radyal mesafedir.

    Tip LE offset signed'dır: pozitif tailward, negatif forward-swept.
    Root LE nose-tip origin'inden absolute x_geo metre olarak verilir.
    Fin uniform solid plate'tir; thickness_m tek kalınlık otoritesidir.
    """

    fin_count: int
    root_chord_m: float
    tip_chord_m: float
    semi_span_m: float
    tip_leading_edge_offset_x_m: float
    root_leading_edge_x_geo_m: float
    thickness_m: float
    cross_section: FinCrossSection


@dataclass(frozen=True, slots=True)
class MotorMountTubeGeometry:
    """Coaxial iç mount tube; seçilmiş motorun kendisi veya ölçüleri değildir."""

    length_m: float
    inner_diameter_m: float
    wall_thickness_m: float
    aft_recess_m: float


@dataclass(frozen=True, slots=True)
class CenteringRingPairGeometry:
    """Tam iki aynı ring; radyal ölçüler body/mount'tan türetilir."""

    axial_thickness_m: float


@dataclass(frozen=True, slots=True)
class MotorAttachmentGeometry:
    """Rocket-side referans: signed overhang = aft reference - mount end.

    Pozitif aft, sıfır flush, negatif recessed. Fit/katalog NAT-011C'ye,
    mount/ring mass NAT-011B.1'e aittir. Manufacturing tolerances ve retention
    hardware ertelidir; fiziksel default ölçü yoktur.
    """

    mount_tube: MotorMountTubeGeometry
    centering_rings: CenteringRingPairGeometry
    motor_overhang_m: float


@dataclass(frozen=True, slots=True)
class SingleStageRocketGeometry:
    """Bir conical nose, bir cylindrical body ve bir trapezoidal fin set."""

    airframe_diameter_m: float
    nose: ConicalNoseGeometry
    body: CylindricalBodyGeometry
    fins: TrapezoidalFinSetGeometry
    motor_attachment: MotorAttachmentGeometry
    reference_geometry_policy: ReferenceGeometryPolicy
