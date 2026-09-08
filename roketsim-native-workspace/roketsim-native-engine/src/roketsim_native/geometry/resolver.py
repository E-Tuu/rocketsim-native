"""NAT-011A: nose tip x_geo=0; +x_geo nose→tail, BODY z_B değildir.

Root chord tamamen body'ye bağlı olmalıdır; tip aft-overhang serbesttir,
ancak tip nose origin'inin önüne geçemez. Yerleşim clamp edilmez.
DEMO MODEL DECISION: D_ref=L_ref=airframe diameter, A_ref=pi*D_ref²/4.
Mass/aero, frame transform, component tree ve staging hesaplanmaz.
"""

from dataclasses import dataclass
from math import atan2, hypot, isfinite, pi

from roketsim_native.geometry.models import (
    SingleStageRocketGeometry, NoseConstructionMode, ReferenceGeometryPolicy, FinCrossSection,
)
from roketsim_native.math.numerical import require_finite

__all__ = ("GeometryResolver", "ResolvedRocketGeometry", "GeometryValidationError")


class GeometryValidationError(ValueError):
    """Finite invalid geometry için deterministik structured hata."""

    def __init__(self, *, error_code: str, field_name: str | None = None,
                 value: float | int | str | None = None) -> None:
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
    nose_material_volume_m3: float
    nose_volume_centroid_x_geo_m: float
    body_material_volume_m3: float
    body_volume_centroid_x_geo_m: float
    fin_set_material_volume_m3: float
    fin_set_volume_centroid_x_geo_m: float
    body_inner_diameter_m: float
    motor_mount_inner_diameter_m: float
    motor_mount_outer_diameter_m: float
    motor_mount_start_x_geo_m: float
    motor_mount_end_x_geo_m: float
    front_centering_ring_start_x_geo_m: float
    front_centering_ring_end_x_geo_m: float
    rear_centering_ring_start_x_geo_m: float
    rear_centering_ring_end_x_geo_m: float
    motor_aft_reference_x_geo_m: float
    motor_mount_material_volume_m3: float
    motor_mount_volume_centroid_x_geo_m: float
    centering_ring_pair_material_volume_m3: float
    centering_ring_pair_volume_centroid_x_geo_m: float
    reference_geometry_policy: ReferenceGeometryPolicy
    aerodynamic_length_m: float
    max_external_airframe_diameter_m: float
    axisymmetric_body_length_m: float
    nose_wetted_area_m2: float
    body_wetted_area_m2: float
    nose_frontal_area_m2: float
    airframe_aft_base_area_m2: float
    nose_fineness_ratio: float
    nose_half_angle_rad: float
    fin_planform_area_per_fin_m2: float
    fin_mean_aerodynamic_chord_m: float
    fin_leading_edge_sweep_angle_rad: float
    fin_cross_section: FinCrossSection


class GeometryResolver:
    """Parametresiz çözümleyici; source değiştirmez ve fiziksel state tutmaz."""

    __slots__ = ()

    def resolve(self, *, rocket_geometry: SingleStageRocketGeometry) -> ResolvedRocketGeometry:
        """Tüm scalar/domain koşullarını ve root attachment ilişkisini doğrula."""
        geometry = rocket_geometry
        fins = geometry.fins
        if not isinstance(geometry.reference_geometry_policy, ReferenceGeometryPolicy):
            raise GeometryValidationError(error_code="INVALID_REFERENCE_GEOMETRY_POLICY",
                field_name="reference_geometry_policy", value=geometry.reference_geometry_policy)
        if not isinstance(fins.cross_section, FinCrossSection):
            raise GeometryValidationError(error_code="INVALID_FIN_CROSS_SECTION",
                field_name="fins.cross_section", value=fins.cross_section)
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

        # Construction aynı source'tan çözülür; hacimler geometry'ye aittir, mass değildir.
        nose = geometry.nose
        nose_thickness = nose.wall_thickness_m
        body_thickness = geometry.body.wall_thickness_m
        if nose_thickness is not None:
            require_finite(nose_thickness, name="nose.wall_thickness_m")
        require_finite(body_thickness, name="body.wall_thickness_m")
        if not isinstance(nose.construction_mode, NoseConstructionMode):
            raise GeometryValidationError(error_code="INVALID_NOSE_CONSTRUCTION",
                field_name="nose.construction_mode", value=nose.construction_mode)
        radius = diameter / 2.0
        if body_thickness <= 0.0:
            raise GeometryValidationError(error_code="NON_POSITIVE_WALL_THICKNESS",
                field_name="body.wall_thickness_m", value=body_thickness)
        if body_thickness >= radius:
            raise GeometryValidationError(error_code="BODY_WALL_TOO_THICK",
                field_name="body.wall_thickness_m", value=body_thickness)
        outer_volume = require_finite(pi * radius * radius * nose_end / 3.0,
                                      name="nose_outer_volume_m3")
        outer_centroid = 3.0 * (nose_end / 4.0)
        if nose.construction_mode is NoseConstructionMode.SOLID:
            if nose_thickness is not None:
                raise GeometryValidationError(error_code="UNEXPECTED_WALL_THICKNESS",
                    field_name="nose.wall_thickness_m", value=nose_thickness)
            nose_volume, nose_centroid = outer_volume, outer_centroid
        else:
            if nose_thickness is None:
                raise GeometryValidationError(error_code="MISSING_WALL_THICKNESS",
                    field_name="nose.wall_thickness_m")
            if nose_thickness <= 0.0:
                raise GeometryValidationError(error_code="NON_POSITIVE_WALL_THICKNESS",
                    field_name="nose.wall_thickness_m", value=nose_thickness)
            slant = require_finite(hypot(nose_end, radius), name="nose_slant_length_m")
            limit = nose_end * radius / slant
            if nose_thickness >= limit:
                raise GeometryValidationError(error_code="NOSE_SHELL_TOO_THICK",
                    field_name="nose.wall_thickness_m", value=nose_thickness)
            delta_x = require_finite(nose_thickness * slant / radius, name="nose_inner_tip_offset_m")
            delta_r = require_finite(nose_thickness * slant / nose_end, name="nose_inner_radius_reduction_m")
            inner_length, inner_radius = nose_end - delta_x, radius - delta_r
            if inner_length <= 0.0 or inner_radius <= 0.0:
                raise GeometryValidationError(error_code="NOSE_SHELL_TOO_THICK",
                    field_name="nose.wall_thickness_m", value=nose_thickness)
            inner_volume = require_finite(pi * inner_radius * inner_radius * inner_length / 3.0,
                                          name="nose_inner_volume_m3")
            nose_volume = require_finite(outer_volume - inner_volume, name="nose_material_volume_m3")
            if nose_volume <= 0.0:
                raise GeometryValidationError(error_code="INVALID_DERIVED_VOLUME",
                    field_name="nose_material_volume_m3", value=nose_volume)
            inner_centroid = delta_x + 3.0 * (inner_length / 4.0)
            nose_centroid = (outer_volume * outer_centroid - inner_volume * inner_centroid) / nose_volume

        body_inner_radius = radius - body_thickness
        body_volume = pi * (radius * radius - body_inner_radius * body_inner_radius) * geometry.body.length_m
        body_centroid = nose_end + geometry.body.length_m / 2.0
        root_chord, tip_chord = fins.root_chord_m, fins.tip_chord_m
        fin_area = (root_chord + tip_chord) * fins.semi_span_m / 2.0
        fin_volume = fins.fin_count * fin_area * fins.thickness_m
        fin_centroid = root_le + (
            root_chord * root_chord + root_chord * tip_chord + tip_chord * tip_chord
            + fins.tip_leading_edge_offset_x_m * (root_chord + 2.0 * tip_chord)
        ) / (3.0 * (root_chord + tip_chord))
        for name, value in (
            ("nose_material_volume_m3", nose_volume), ("body_material_volume_m3", body_volume),
            ("fin_set_material_volume_m3", fin_volume),
        ):
            require_finite(value, name=name)
            if value <= 0.0:
                raise GeometryValidationError(error_code="INVALID_DERIVED_VOLUME", field_name=name, value=value)
        for name, value, lower, upper in (
            ("nose_volume_centroid_x_geo_m", nose_centroid, 0.0, nose_end),
            ("body_volume_centroid_x_geo_m", body_centroid, nose_end, body_end),
            ("fin_set_volume_centroid_x_geo_m", fin_centroid, min(root_le, tip_le), max(root_te, tip_te)),
        ):
            require_finite(value, name=name)
            if not lower <= value <= upper:
                raise GeometryValidationError(error_code="INVALID_DERIVED_CENTROID", field_name=name, value=value)
        # NAT-011A.2: aynı geometry source/resolver; motor fit veya mass yorumu yok.
        attachment = geometry.motor_attachment
        mount = attachment.mount_tube
        ring_thickness = attachment.centering_rings.axial_thickness_m
        mount_dimensions = (
            ("motor_attachment.mount_tube.length_m", mount.length_m, "NON_POSITIVE_MOUNT_LENGTH"),
            ("motor_attachment.mount_tube.inner_diameter_m", mount.inner_diameter_m, "NON_POSITIVE_MOUNT_INNER_DIAMETER"),
            ("motor_attachment.mount_tube.wall_thickness_m", mount.wall_thickness_m, "NON_POSITIVE_MOUNT_WALL_THICKNESS"),
            ("motor_attachment.centering_rings.axial_thickness_m", ring_thickness, "NON_POSITIVE_CENTERING_RING_THICKNESS"),
        )
        for name, value, _ in mount_dimensions:
            require_finite(value, name=name)
        require_finite(mount.aft_recess_m, name="motor_attachment.mount_tube.aft_recess_m")
        require_finite(attachment.motor_overhang_m, name="motor_attachment.motor_overhang_m")
        for name, value, code in mount_dimensions:
            if value <= 0.0:
                raise GeometryValidationError(error_code=code, field_name=name, value=value)
        if mount.aft_recess_m < 0.0:
            raise GeometryValidationError(error_code="NEGATIVE_MOUNT_AFT_RECESS",
                field_name="motor_attachment.mount_tube.aft_recess_m", value=mount.aft_recess_m)
        body_inner_diameter = require_finite(diameter - 2.0 * body_thickness, name="body_inner_diameter_m")
        if body_inner_diameter <= 0.0:
            raise GeometryValidationError(error_code="INVALID_BODY_INNER_DIAMETER",
                field_name="body_inner_diameter_m", value=body_inner_diameter)
        mount_outer = require_finite(mount.inner_diameter_m + 2.0 * mount.wall_thickness_m,
                                     name="motor_mount_outer_diameter_m")
        if mount_outer >= body_inner_diameter:
            raise GeometryValidationError(error_code="MOTOR_MOUNT_TOO_LARGE_FOR_BODY",
                field_name="motor_mount_outer_diameter_m", value=mount_outer)
        mount_end = require_finite(body_end - mount.aft_recess_m, name="motor_mount_end_x_geo_m")
        mount_start = require_finite(mount_end - mount.length_m, name="motor_mount_start_x_geo_m")
        if mount_start < nose_end or mount_end > body_end:
            raise GeometryValidationError(error_code="MOTOR_MOUNT_OUTSIDE_BODY",
                field_name="motor_mount_start_x_geo_m", value=mount_start)
        if 2.0 * ring_thickness > mount.length_m:
            raise GeometryValidationError(error_code="CENTERING_RINGS_OVERLAP",
                field_name="motor_attachment.centering_rings.axial_thickness_m", value=ring_thickness)
        if attachment.motor_overhang_m < -mount.length_m:
            raise GeometryValidationError(error_code="MOTOR_AFT_REFERENCE_BEFORE_MOUNT",
                field_name="motor_attachment.motor_overhang_m", value=attachment.motor_overhang_m)
        aft_reference = require_finite(mount_end + attachment.motor_overhang_m,
                                       name="motor_aft_reference_x_geo_m")
        front_end = mount_start + ring_thickness
        rear_start = mount_end - ring_thickness
        mount_outer_radius, mount_inner_radius = mount_outer / 2.0, mount.inner_diameter_m / 2.0
        mount_volume = pi * (mount_outer_radius * mount_outer_radius - mount_inner_radius * mount_inner_radius) * mount.length_m
        ring_outer_radius = body_inner_diameter / 2.0
        one_ring_volume = pi * (ring_outer_radius * ring_outer_radius - mount_outer_radius * mount_outer_radius) * ring_thickness
        pair_volume = 2.0 * one_ring_volume
        mount_centroid = (mount_start + mount_end) / 2.0
        front_centroid = mount_start + ring_thickness / 2.0
        rear_centroid = mount_end - ring_thickness / 2.0
        pair_centroid = (front_centroid + rear_centroid) / 2.0
        for name, value, code in (
            ("motor_mount_material_volume_m3", mount_volume, "INVALID_MOUNT_VOLUME"),
            ("one_centering_ring_material_volume_m3", one_ring_volume, "INVALID_RING_VOLUME"),
            ("centering_ring_pair_material_volume_m3", pair_volume, "INVALID_RING_VOLUME"),
        ):
            require_finite(value, name=name)
            if value <= 0.0:
                raise GeometryValidationError(error_code=code, field_name=name, value=value)
        for name, value, code in (
            ("motor_mount_volume_centroid_x_geo_m", mount_centroid, "INVALID_MOUNT_CENTROID"),
            ("centering_ring_pair_volume_centroid_x_geo_m", pair_centroid, "INVALID_RING_CENTROID"),
        ):
            require_finite(value, name=name)
            if not mount_start <= value <= mount_end:
                raise GeometryValidationError(error_code=code, field_name=name, value=value)
        # A.0: yalnız geometri; tasarım ölçüleri sabit motor/demo sayıları değildir.
        # MAXIMUM_DIAMETER tek çaplı dış airframe'dir; fins/internal hardware hariç.
        overall_length = max(body_end, root_te, tip_te)
        max_external_airframe_diameter = diameter
        axisymmetric_body_length = body_end
        nose_wetted = pi * radius * hypot(nose_end, radius)
        body_wetted = 2.0 * pi * radius * geometry.body.length_m
        frontal = pi * radius * radius
        fineness = nose_end / diameter
        half_angle = atan2(radius, nose_end)
        mac = (2.0 / 3.0) * (root_chord + tip_chord
                            - root_chord * tip_chord / (root_chord + tip_chord))
        sweep = atan2(fins.tip_leading_edge_offset_x_m, fins.semi_span_m)
        for name, value, code in (
            ("aerodynamic_length_m", overall_length, "INVALID_AERODYNAMIC_LENGTH"),
            ("max_external_airframe_diameter_m", max_external_airframe_diameter,
             "INVALID_MAX_EXTERNAL_AIRFRAME_DIAMETER"),
            ("axisymmetric_body_length_m", axisymmetric_body_length,
             "INVALID_AXISYMMETRIC_BODY_LENGTH"),
            ("nose_wetted_area_m2", nose_wetted, "INVALID_NOSE_WETTED_AREA"),
            ("body_wetted_area_m2", body_wetted, "INVALID_BODY_WETTED_AREA"),
            ("nose_frontal_area_m2", frontal, "INVALID_NOSE_FRONTAL_AREA"),
            ("airframe_aft_base_area_m2", frontal, "INVALID_AIRFRAME_AFT_BASE_AREA"),
            ("nose_fineness_ratio", fineness, "INVALID_NOSE_FINENESS_RATIO"),
            ("fin_planform_area_per_fin_m2", fin_area, "INVALID_FIN_PLANFORM_AREA"),
            ("fin_mean_aerodynamic_chord_m", mac, "INVALID_FIN_MEAN_AERODYNAMIC_CHORD"),
        ):
            if not isfinite(value) or value <= 0.0:
                raise GeometryValidationError(error_code=code, field_name=name, value=value)
        if not isfinite(half_angle) or not 0.0 < half_angle < pi / 2.0:
            raise GeometryValidationError(error_code="INVALID_NOSE_HALF_ANGLE",
                field_name="nose_half_angle_rad", value=half_angle)
        if not isfinite(sweep):
            raise GeometryValidationError(error_code="INVALID_FIN_SWEEP_ANGLE",
                field_name="fin_leading_edge_sweep_angle_rad", value=sweep)
        return ResolvedRocketGeometry(
            source=geometry,
            nose_start_x_geo_m=0.0, nose_end_x_geo_m=nose_end,
            body_start_x_geo_m=nose_end, body_end_x_geo_m=body_end,
            fin_root_leading_edge_x_geo_m=root_le, fin_root_trailing_edge_x_geo_m=root_te,
            fin_tip_leading_edge_x_geo_m=tip_le, fin_tip_trailing_edge_x_geo_m=tip_te,
            overall_length_m=overall_length,
            reference_diameter_m=diameter, reference_length_m=diameter,
            reference_area_m2=area,
            nose_material_volume_m3=nose_volume, nose_volume_centroid_x_geo_m=nose_centroid,
            body_material_volume_m3=body_volume, body_volume_centroid_x_geo_m=body_centroid,
            fin_set_material_volume_m3=fin_volume, fin_set_volume_centroid_x_geo_m=fin_centroid,
            body_inner_diameter_m=body_inner_diameter,
            motor_mount_inner_diameter_m=mount.inner_diameter_m,
            motor_mount_outer_diameter_m=mount_outer,
            motor_mount_start_x_geo_m=mount_start, motor_mount_end_x_geo_m=mount_end,
            front_centering_ring_start_x_geo_m=mount_start, front_centering_ring_end_x_geo_m=front_end,
            rear_centering_ring_start_x_geo_m=rear_start, rear_centering_ring_end_x_geo_m=mount_end,
            motor_aft_reference_x_geo_m=aft_reference,
            motor_mount_material_volume_m3=mount_volume, motor_mount_volume_centroid_x_geo_m=mount_centroid,
            centering_ring_pair_material_volume_m3=pair_volume, centering_ring_pair_volume_centroid_x_geo_m=pair_centroid,
            reference_geometry_policy=geometry.reference_geometry_policy,
            aerodynamic_length_m=overall_length,
            max_external_airframe_diameter_m=max_external_airframe_diameter,
            axisymmetric_body_length_m=axisymmetric_body_length,
            nose_wetted_area_m2=nose_wetted, body_wetted_area_m2=body_wetted,
            nose_frontal_area_m2=frontal, airframe_aft_base_area_m2=frontal,
            nose_fineness_ratio=fineness, nose_half_angle_rad=half_angle,
            fin_planform_area_per_fin_m2=fin_area, fin_mean_aerodynamic_chord_m=mac,
            fin_leading_edge_sweep_angle_rad=sweep, fin_cross_section=fins.cross_section,
        )
