"""AEROGEO-T01..28: yalnız fiziksel geometri; tasarım sayıları test fixture'ıdır."""

from dataclasses import FrozenInstanceError, fields, replace
from inspect import Parameter, signature
from math import atan2, isfinite, pi

import pytest

from roketsim_native.geometry.models import (
    ReferenceGeometryPolicy, FinCrossSection, ConicalNoseGeometry, CylindricalBodyGeometry,
    NoseConstructionMode, TrapezoidalFinSetGeometry, SingleStageRocketGeometry,
    MotorAttachmentGeometry, MotorMountTubeGeometry, CenteringRingPairGeometry,
)
from roketsim_native.geometry import resolver
from roketsim_native.geometry.resolver import GeometryResolver, GeometryValidationError


@pytest.fixture
def geometry():
    return SingleStageRocketGeometry(.1,
        ConicalNoseGeometry(.3,NoseConstructionMode.HOLLOW_SHELL,.002),
        CylindricalBodyGeometry(.7,.002),
        TrapezoidalFinSetGeometry(4,.18,.08,.12,.05,.72,.003,FinCrossSection.SQUARE),
        MotorAttachmentGeometry(MotorMountTubeGeometry(.12,.029,.001,0.),
                                CenteringRingPairGeometry(.003),.005),
        ReferenceGeometryPolicy.MAXIMUM_DIAMETER)


def resolve(geometry):
    return GeometryResolver().resolve(rocket_geometry=geometry)


def test_enums_and_mandatory_schema():
    """AEROGEO-T01/T02/T08/T09: Tek desteklenen enum; default politika/kesit yok."""
    assert {x.name:x.value for x in ReferenceGeometryPolicy} == {'MAXIMUM_DIAMETER':'maximum_diameter'}
    assert {x.name:x.value for x in FinCrossSection} == {'SQUARE':'square'}
    assert signature(SingleStageRocketGeometry).parameters['reference_geometry_policy'].default is Parameter.empty
    assert signature(TrapezoidalFinSetGeometry).parameters['cross_section'].default is Parameter.empty


@pytest.mark.parametrize('diameter',[.1,.15,.2])
def test_reference_design_authority(geometry,diameter):
    """AEROGEO-T03..06/T26, SCOPE-T06: D_ref tasarım girdisidir, sabit .1 değildir."""
    result = resolve(replace(geometry,airframe_diameter_m=diameter))
    assert result.reference_diameter_m == result.reference_length_m == diameter
    assert result.reference_area_m2 == pytest.approx(pi*diameter**2/4,rel=3e-15)
    assert result.reference_geometry_policy is ReferenceGeometryPolicy.MAXIMUM_DIAMETER


def test_axisymmetric_reference_only(geometry):
    """AEROGEO-T07: Fin span ve iç mount ölçüleri dış airframe referansını değiştirmez."""
    attachment = geometry.motor_attachment
    changed = replace(geometry,fins=replace(geometry.fins,semi_span_m=.5),
        motor_attachment=replace(attachment,mount_tube=replace(attachment.mount_tube,inner_diameter_m=.04)))
    before,after = resolve(geometry),resolve(changed)
    assert after.reference_length_m == before.reference_length_m
    assert after.reference_area_m2 == before.reference_area_m2


def test_distinct_lengths_and_motor_reference(geometry):
    """AEROGEO-T10..12: Aerodinamik uzunluk structural extent'tir, motor referansı değil."""
    result = resolve(geometry)
    assert result.reference_length_m == .1
    assert result.aerodynamic_length_m == result.overall_length_m == 1.
    changed = replace(geometry,motor_attachment=replace(geometry.motor_attachment,motor_overhang_m=10.))
    after = resolve(changed)
    assert after.motor_aft_reference_x_geo_m > 10.
    assert after.aerodynamic_length_m == 1.


def test_fin_aft_extent(geometry):
    """AEROGEO-T13: Kabul edilmiş aft fin uzantısı aynı overall hesabıyla korunur."""
    result = resolve(replace(geometry,fins=replace(geometry.fins,
        root_leading_edge_x_geo_m=.82,tip_leading_edge_offset_x_m=.1,tip_chord_m=.12)))
    assert result.aerodynamic_length_m == result.overall_length_m == result.fin_tip_trailing_edge_x_geo_m
    assert result.aerodynamic_length_m == pytest.approx(1.04,rel=3e-15)


@pytest.mark.parametrize('name,value',[
    pytest.param('nose_wetted_area_m2',.04777390519679037,id='AEROGEO-T14'),
    pytest.param('body_wetted_area_m2',.2199114857512855,id='AEROGEO-T15'),
    pytest.param('nose_frontal_area_m2',.007853981633974483,id='AEROGEO-T16'),
    pytest.param('airframe_aft_base_area_m2',.007853981633974483,id='AEROGEO-T17'),
    pytest.param('nose_fineness_ratio',3.,id='AEROGEO-T18'),
    pytest.param('nose_half_angle_rad',.16514867741462685,id='AEROGEO-T19'),
    pytest.param('fin_planform_area_per_fin_m2',.0156,id='AEROGEO-T20'),
    pytest.param('fin_mean_aerodynamic_chord_m',.13641025641025642,id='AEROGEO-T21'),
    pytest.param('fin_leading_edge_sweep_angle_rad',.39479111969976155,id='AEROGEO-T22'),
])
def test_analytic_fixture(geometry,name,value):
    assert getattr(resolve(geometry),name) == pytest.approx(value,rel=3e-15,abs=0)


@pytest.mark.parametrize('offset',[-.05,0.,.05])
@pytest.mark.parametrize('tip_chord',[0.,.08])
def test_triangular_and_signed_sweep(geometry,offset,tip_chord):
    """AEROGEO-T20..23: Üçgen limit, signed sweep ve ham kesit otoritesi."""
    fins = replace(geometry.fins,tip_chord_m=tip_chord,tip_leading_edge_offset_x_m=offset)
    result = resolve(replace(geometry,fins=fins))
    assert result.fin_cross_section is fins.cross_section
    assert result.fin_leading_edge_sweep_angle_rad == atan2(offset,fins.semi_span_m)
    assert result.fin_planform_area_per_fin_m2 == (fins.root_chord_m+tip_chord)*fins.semi_span_m/2
    if tip_chord == 0.:
        assert result.fin_mean_aerodynamic_chord_m == (2/3)*fins.root_chord_m


def test_previous_construction_and_mount(geometry):
    """AEROGEO-T24/T25: Kabul edilmiş hacim/centroid/mount referansları korunur."""
    r = resolve(geometry)
    assert (r.nose_material_volume_m3,r.nose_volume_centroid_x_geo_m,
        r.body_material_volume_m3,r.body_volume_centroid_x_geo_m,
        r.fin_set_material_volume_m3,r.fin_set_volume_centroid_x_geo_m,
        r.motor_mount_material_volume_m3,r.motor_mount_volume_centroid_x_geo_m,
        r.centering_ring_pair_material_volume_m3,r.centering_ring_pair_volume_centroid_x_geo_m) == pytest.approx(
        (9.172555380948023e-5,.2019996168861517,4.310265120725205e-4,.65,
         .0001872,.81,1.1309733552923245e-5,.94,3.890077103307561e-5,.94),rel=2e-14)
    assert (r.motor_mount_start_x_geo_m,r.motor_mount_end_x_geo_m,r.motor_aft_reference_x_geo_m) == pytest.approx((.88,1.,1.005),rel=3e-15)


def test_invariants_and_immutability(geometry):
    """AEROGEO-T27/T28: Başarılı sonuçlar sonlu/geçerli; raw/result değişmez."""
    before = repr(geometry)
    r = resolve(geometry)
    for name in ('aerodynamic_length_m','nose_wetted_area_m2','body_wetted_area_m2',
        'nose_frontal_area_m2','airframe_aft_base_area_m2','nose_fineness_ratio',
        'fin_planform_area_per_fin_m2','fin_mean_aerodynamic_chord_m'):
        assert isfinite(getattr(r,name)) and getattr(r,name)>0
    assert 0 < r.nose_half_angle_rad < pi/2
    for obj in (geometry,geometry.fins,r):
        assert not hasattr(obj,'__dict__')
        with pytest.raises(FrozenInstanceError):
            setattr(obj,fields(obj)[0].name,None)
    assert resolve(geometry) == r
    assert repr(geometry) == before


@pytest.mark.parametrize('value',[None,'maximum_diameter',1])
def test_invalid_policy(geometry,value):
    with pytest.raises(GeometryValidationError) as caught:
        resolve(replace(geometry,reference_geometry_policy=value))
    assert caught.value.error_code == 'INVALID_REFERENCE_GEOMETRY_POLICY'


@pytest.mark.parametrize('value',[None,'square','airfoil'])
def test_invalid_cross_section(geometry,value):
    with pytest.raises(GeometryValidationError) as caught:
        resolve(replace(geometry,fins=replace(geometry.fins,cross_section=value)))
    assert caught.value.error_code == 'INVALID_FIN_CROSS_SECTION'


@pytest.mark.parametrize('bad',[0.,float('nan'),float('inf')])
def test_derived_angle_failure(geometry,monkeypatch,bad):
    """Yeni türetilmiş geometri invariant arızası GeometryValidationError olur."""
    monkeypatch.setattr(resolver,'atan2',lambda *args:bad)
    with pytest.raises(GeometryValidationError) as caught:
        resolve(geometry)
    assert caught.value.error_code == 'INVALID_NOSE_HALF_ANGLE'


def test_scope(geometry):
    """SCOPE-T01..05: Geometri verisi dışında aero katsayısı/force/state yüzeyi yok."""
    names = {f.name for f in fields(resolve(geometry))}
    assert not names.intersection({'reynolds','cf','cd','cp','cna','static_margin','drag_force_N','mach'})
    assert 'fin_leading_edge_sweep_angle_rad' in names
    assert not names.intersection({'fin_sweep_cos','fin_sweep_cos_squared','fin_total_area_m2'})
