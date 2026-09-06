"""Immutable SI tasarım verileri; domain/yerleşim doğrulaması resolver sınırındadır.

Demo profilinde tek çap otoritesi airframe_diameter_m'dir; nose base ve body
outer diameter bağımsız saklanmaz. Full geometry/component tree ertelenmiştir.
"""

from dataclasses import dataclass

__all__ = ("ConicalNoseGeometry", "CylindricalBodyGeometry",
           "TrapezoidalFinSetGeometry", "SingleStageRocketGeometry")


@dataclass(frozen=True, slots=True)
class ConicalNoseGeometry:
    """Konik nose'un eksenel uzunluğu, m."""

    length_m: float


@dataclass(frozen=True, slots=True)
class CylindricalBodyGeometry:
    """Sabit çaplı silindirik body'nin eksenel uzunluğu, m."""

    length_m: float


@dataclass(frozen=True, slots=True)
class TrapezoidalFinSetGeometry:
    """Root/tip chord ekseneldir; semi-span body yüzeyinden tip'e radyal mesafedir.

    Tip LE offset signed'dır: pozitif tailward, negatif forward-swept.
    Root LE nose-tip origin'inden absolute x_geo metre olarak verilir.
    """

    fin_count: int
    root_chord_m: float
    tip_chord_m: float
    semi_span_m: float
    tip_leading_edge_offset_x_m: float
    root_leading_edge_x_geo_m: float
    thickness_m: float


@dataclass(frozen=True, slots=True)
class SingleStageRocketGeometry:
    """Bir conical nose, bir cylindrical body ve bir trapezoidal fin set."""

    airframe_diameter_m: float
    nose: ConicalNoseGeometry
    body: CylindricalBodyGeometry
    fins: TrapezoidalFinSetGeometry
