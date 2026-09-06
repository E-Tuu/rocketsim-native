"""NAT-011A: nose tip x_geo=0; +x_geo nose→tail, BODY z_B değildir.

Root chord tamamen body'ye bağlı olmalıdır; tip aft-overhang serbesttir,
ancak tip nose origin'inin önüne geçemez. Yerleşim clamp edilmez.
DEMO MODEL DECISION: D_ref=L_ref=airframe diameter, A_ref=pi*D_ref²/4.
Mass/aero, frame transform, component tree ve staging hesaplanmaz.
"""

from dataclasses import dataclass
from math import pi

from roketsim_native.geometry.models import SingleStageRocketGeometry
from roketsim_native.math.numerical import require_finite

__all__ = ("GeometryResolver", "ResolvedRocketGeometry", "GeometryValidationError")


class GeometryValidationError(ValueError):
    """Finite invalid geometry için deterministik structured hata."""

    def __init__(self, *, error_code: str, field_name: str | None = None,
                 value: float | int | None = None) -> None:
        self.error_code = error_code
        self.field_name = field_name
        self.value = value
        super().__init__(f"{error_code}: {field_name}={value!r}")


@dataclass(frozen=True, slots=True)
class ResolvedRocketGeometry:
    """Immutable source ve derived eksenel/reference geometry; dynamics state değil."""

    source: SingleStageRocketGeometry
    nose_start_x_geo_m: float
    nose_end_x_geo_m: float
    body_start_x_geo_m: float
    body_end_x_geo_m: float
    fin_root_leading_edge_x_geo_m: float
    fin_root_trailing_edge_x_geo_m: float
    fin_tip_leading_edge_x_geo_m: float
    fin_tip_trailing_edge_x_geo_m: float
    overall_length_m: float
    reference_diameter_m: float
    reference_length_m: float
    reference_area_m2: float


class GeometryResolver:
    """Parametresiz çözümleyici; source değiştirmez ve fiziksel state tutmaz."""

    __slots__ = ()

    def resolve(self, *, rocket_geometry: SingleStageRocketGeometry) -> ResolvedRocketGeometry:
        """Tüm scalar/domain koşullarını ve root attachment ilişkisini doğrula."""
        geometry = rocket_geometry
        fins = geometry.fins
        positive_dimensions = (
            ("airframe_diameter_m", geometry.airframe_diameter_m),
            ("nose.length_m", geometry.nose.length_m),
            ("body.length_m", geometry.body.length_m),
            ("fins.root_chord_m", fins.root_chord_m),
            ("fins.semi_span_m", fins.semi_span_m),
            ("fins.thickness_m", fins.thickness_m),
        )
        for name, value in positive_dimensions + (
            ("fins.tip_chord_m", fins.tip_chord_m),
            ("fins.tip_leading_edge_offset_x_m", fins.tip_leading_edge_offset_x_m),
            ("fins.root_leading_edge_x_geo_m", fins.root_leading_edge_x_geo_m),
        ):
            require_finite(value, name=name)
        for name, value in positive_dimensions:
            if value <= 0.0:
                raise GeometryValidationError(
                    error_code="NON_POSITIVE_DIMENSION", field_name=name, value=value
                )
        if fins.tip_chord_m < 0.0:
            raise GeometryValidationError(error_code="NEGATIVE_TIP_CHORD",
                field_name="fins.tip_chord_m", value=fins.tip_chord_m)
        # Adet tam sayıdır; bool veya fractional adet sessizce int'e çevrilmez.
        if not isinstance(fins.fin_count, int):
            require_finite(fins.fin_count, name="fins.fin_count")
        if isinstance(fins.fin_count, bool) or not isinstance(fins.fin_count, int) or fins.fin_count < 3:
            raise GeometryValidationError(error_code="INVALID_FIN_COUNT",
                field_name="fins.fin_count", value=fins.fin_count)

        nose_end = float(geometry.nose.length_m)
        body_end = require_finite(nose_end + float(geometry.body.length_m), name="body_end_x_geo_m")
        root_le = float(fins.root_leading_edge_x_geo_m)
        root_te = require_finite(root_le + float(fins.root_chord_m), name="fin_root_trailing_edge_x_geo_m")
        tip_le = require_finite(root_le + float(fins.tip_leading_edge_offset_x_m), name="fin_tip_leading_edge_x_geo_m")
        tip_te = require_finite(tip_le + float(fins.tip_chord_m), name="fin_tip_trailing_edge_x_geo_m")
        if root_le < nose_end or root_te > body_end:
            raise GeometryValidationError(error_code="FIN_ROOT_OUTSIDE_BODY")
        if tip_le < 0.0 or tip_te < 0.0:
            raise GeometryValidationError(error_code="FIN_EXTENDS_BEFORE_NOSE")
        diameter = float(geometry.airframe_diameter_m)
        area = require_finite((pi / 4.0) * diameter * diameter, name="reference_area_m2")
        if area <= 0.0:
            raise ValueError("reference_area_m2 must be positive; numerical underflow")
        return ResolvedRocketGeometry(
            source=geometry,
            nose_start_x_geo_m=0.0, nose_end_x_geo_m=nose_end,
            body_start_x_geo_m=nose_end, body_end_x_geo_m=body_end,
            fin_root_leading_edge_x_geo_m=root_le, fin_root_trailing_edge_x_geo_m=root_te,
            fin_tip_leading_edge_x_geo_m=tip_le, fin_tip_trailing_edge_x_geo_m=tip_te,
            overall_length_m=max(body_end, root_te, tip_te),
            reference_diameter_m=diameter, reference_length_m=diameter,
            reference_area_m2=area,
        )
