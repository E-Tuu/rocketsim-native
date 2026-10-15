"""J-004 actual Python/Java geometry/material/structural parity; test-only araç."""

import argparse
import base64
from dataclasses import asdict, fields, replace
from enum import Enum
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
from parity_contract import count_cases, keyed_output, nonempty

import numpy as np

CORE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(CORE.parent / 'roketsim-native-engine/src'))
from roketsim_native.geometry import models as gm
from roketsim_native.geometry.resolver import GeometryResolver, GeometryValidationError
from roketsim_native.materials.models import BulkMaterial, SingleStageRocketMaterials
from roketsim_native.materials.catalog import CARDBOARD, POLYSTYRENE
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator

parser = argparse.ArgumentParser()
parser.add_argument('--java', required=True)
parser.add_argument('--build-dir', type=Path, required=True)
parser.add_argument('--group', choices=['geometry', 'materials', 'mass', 'all'], default='geometry')
args = parser.parse_args()


def geometry():
    # Accepted NAT-011 fixture; production preset/default değildir.
    return gm.SingleStageRocketGeometry(.1,
        gm.ConicalNoseGeometry(.3, gm.NoseConstructionMode.HOLLOW_SHELL, .002),
        gm.CylindricalBodyGeometry(.7, .002),
        gm.TrapezoidalFinSetGeometry(4, .18, .08, .12, .05, .72, .003,
            gm.FinCrossSection.SQUARE, gm.FinAngularArrangement.EQUALLY_SPACED),
        gm.MotorAttachmentGeometry(gm.MotorMountTubeGeometry(.12, .029, .001, 0.),
            gm.CenteringRingPairGeometry(.003), .005), gm.ReferenceGeometryPolicy.MAXIMUM_DIAMETER)


def changed(record, path, value):
    first, *tail = path.split('.', 1)
    return replace(record, **{first: changed(getattr(record, first), tail[0], value) if tail else value})


def encode(value):
    if value is None: return 'null'
    if isinstance(value, Enum): return f'e:{type(value).__name__}.{value.name}'
    if isinstance(value, str): return 's:' + base64.b64encode(value.encode()).decode()
    if isinstance(value, bool): return 'b:' + str(value).lower()
    if isinstance(value, int): return 'i:' + str(value)
    return 'd:' + ('NaN' if math.isnan(value) else 'Infinity' if value == math.inf else '-Infinity' if value == -math.inf else repr(value))


def decoded(value):
    if value == 'null': return None
    if value.startswith('s:'): return base64.b64decode(value[2:]).decode()
    if value in ('true', 'false'): return value == 'true'
    if value.lstrip('-').isdigit(): return int(value)
    try: return float(value)
    except ValueError: return value


def camel(name):
    first, *tail = name.split('_')
    return first + ''.join(x[0].upper() + x[1:] for x in tail)


cases = []
def add(**changes): cases.append(('geometry', changes))
add()
add(**{'nose.construction_mode': gm.NoseConstructionMode.SOLID, 'nose.wall_thickness_m': None})
for diameter in (.1, .15, .2): add(airframe_diameter_m=diameter)
for count in (3, 4, 8): add(**{'fins.fin_count': count})
for offset in (-.05, 0., .05):
    for tip in (0., .08): add(**{'fins.tip_leading_edge_offset_x_m': offset, 'fins.tip_chord_m': tip})
add(**{'fins.root_leading_edge_x_geo_m': .82, 'fins.tip_leading_edge_offset_x_m': .1, 'fins.tip_chord_m': .12})
add(**{'fins.root_leading_edge_x_geo_m': .3, 'fins.root_chord_m': .7, 'fins.tip_leading_edge_offset_x_m': -.3})
for overhang in (0., -.01, -.12, 100.): add(**{'motor_attachment.motor_overhang_m': overhang})
add(**{'motor_attachment.mount_tube.aft_recess_m': .05})
add(**{'motor_attachment.centering_rings.axial_thickness_m': .06})
add(**{'nose.length_m': .25, 'body.length_m': .75, 'motor_attachment.mount_tube.length_m': .75})
limit = .3*.05/math.hypot(.3, .05)
for thickness in (math.nextafter(limit, 0.), limit, limit*1.01, 0., -.002, 1e-300, None):
    add(**{'nose.wall_thickness_m': thickness})
add(**{'nose.construction_mode': gm.NoseConstructionMode.SOLID})
for path, value in (('body.wall_thickness_m', .05), ('body.wall_thickness_m', .06),
    ('body.wall_thickness_m', 1e-300), ('fins.tip_chord_m', -.1),
    ('fins.root_leading_edge_x_geo_m', .29), ('fins.root_leading_edge_x_geo_m', .83),
    ('fins.tip_leading_edge_offset_x_m', -.73), ('motor_attachment.mount_tube.length_m', .71),
    ('motor_attachment.mount_tube.aft_recess_m', 1.), ('motor_attachment.mount_tube.inner_diameter_m', .094),
    ('motor_attachment.mount_tube.inner_diameter_m', .095), ('motor_attachment.motor_overhang_m', -.121),
    ('motor_attachment.centering_rings.axial_thickness_m', .061), ('motor_attachment.mount_tube.aft_recess_m', -.001)):
    add(**{path: value})
positive = ['airframe_diameter_m', 'nose.length_m', 'body.length_m', 'fins.root_chord_m',
    'fins.semi_span_m', 'fins.thickness_m', 'motor_attachment.mount_tube.length_m',
    'motor_attachment.mount_tube.inner_diameter_m', 'motor_attachment.mount_tube.wall_thickness_m',
    'motor_attachment.centering_rings.axial_thickness_m', 'body.wall_thickness_m']
for path in positive:
    for value in (0., -1.): add(**{path: value})
for path in positive + ['fins.tip_chord_m', 'fins.tip_leading_edge_offset_x_m',
    'fins.root_leading_edge_x_geo_m', 'fins.fin_count', 'nose.wall_thickness_m',
    'motor_attachment.mount_tube.aft_recess_m', 'motor_attachment.motor_overhang_m']:
    for value in (math.nan, math.inf, -math.inf): add(**{path: value})
for count in (0, 1, 2, -1, 2.5, 3.5, True, 4.0, 10**400, -10**400): add(**{'fins.fin_count': count})
for path in ('reference_geometry_policy', 'fins.cross_section', 'fins.angular_arrangement', 'nose.construction_mode'):
    for invalid in (None, 'other', 1): add(**{path: invalid})
for diameter in (1e308, 1e-300): add(airframe_diameter_m=diameter)
add(**{'airframe_diameter_m': -1., 'fins.tip_chord_m': math.nan})
# Ölçeklenmiş fixture'lar fizik default'u değildir; birim ve dimension scaling kontrolü.
for scale in (.5, 2., 4.):
    original = geometry()
    changes = {'airframe_diameter_m': .1*scale}
    for path in ('nose.length_m', 'nose.wall_thickness_m', 'body.length_m', 'body.wall_thickness_m',
        'fins.root_chord_m', 'fins.tip_chord_m', 'fins.semi_span_m', 'fins.tip_leading_edge_offset_x_m',
        'fins.root_leading_edge_x_geo_m', 'fins.thickness_m', 'motor_attachment.mount_tube.length_m',
        'motor_attachment.mount_tube.inner_diameter_m', 'motor_attachment.mount_tube.wall_thickness_m',
        'motor_attachment.centering_rings.axial_thickness_m', 'motor_attachment.motor_overhang_m'):
        value = original
        for segment in path.split('.'): value = getattr(value, segment)
        changes[path] = value*scale
    add(**changes)


for name, density in (('Cardboard', 680.), ('Polystyrene', 1050.), ('Test material', 900.),
    ('  name  ', 1.), ('Tiny', 5e-324), ('Large', sys.float_info.max), ('', -1.), ('', math.nan)):
    cases.append(('material', dict(name=name, density_kg_m3=density)))
for density in (0., -1., math.nan, math.inf, -math.inf):
    cases.append(('material', dict(name='Test', density_kg_m3=density)))
for name in ('', ' ', '\t\n', '\u00a0', '\u0085', '\u2007', '\u202f', '\u001c'):
    cases.append(('material', dict(name=name, density_kg_m3=1.)))
cases.append(('catalog', {}))
geometry_cases = list(cases)
for op, changes in geometry_cases:
    if op == 'geometry' and not changes:
        cases.append(('mass', {}))
    elif op == 'geometry' and (not any(isinstance(value, float) and not math.isfinite(value) for value in changes.values())):
        cases.append(('mass', {'geometry.'+key:value for key,value in changes.items()}))
for component in ('nose', 'body', 'fins', 'motor_mount', 'centering_rings'):
    for density in (100., 200., 900., 1360., 2100., 0., -1., math.nan, math.inf, -math.inf):
        cases.append(('mass', {f'materials.{component}.density_kg_m3':density}))
    cases.append(('mass', {f'materials.{component}.name':''}))
    for volume in (0., -1., math.nan, math.inf, -math.inf, 1e308):
        volume_field = {'nose':'nose_material_volume_m3', 'body':'body_material_volume_m3',
            'fins':'fin_set_material_volume_m3','motor_mount':'motor_mount_material_volume_m3',
            'centering_rings':'centering_ring_pair_material_volume_m3'}[component]
        cases.append(('mass', {'resolved.'+volume_field:volume}))
    centroid_field = {'nose':'nose_volume_centroid_x_geo_m','body':'body_volume_centroid_x_geo_m',
        'fins':'fin_set_volume_centroid_x_geo_m','motor_mount':'motor_mount_volume_centroid_x_geo_m',
        'centering_rings':'centering_ring_pair_volume_centroid_x_geo_m'}[component]
    for cg in (math.nan, math.inf, -math.inf):
        cases.append(('mass', {'resolved.'+centroid_field:cg}))
for extent in (0., -1., math.nan, math.inf): cases.append(('mass', {'resolved.overall_length_m':extent}))
cases.append(('mass', {'resolved.nose_material_volume_m3':.0002, 'resolved.nose_volume_centroid_x_geo_m':.25}))
for component, stem in (('motor_mount','motor_mount'),('centering_rings','centering_ring_pair')):
    cases.append(('mass', {f'resolved.{stem}_material_volume_m3':.0001, f'resolved.{stem}_volume_centroid_x_geo_m':.4}))
for value in (-10., 10.):
    cases.append(('mass', {'resolved.nose_volume_centroid_x_geo_m':value,
        'resolved.body_volume_centroid_x_geo_m':value, 'resolved.fin_set_volume_centroid_x_geo_m':value}))
cases.append(('mass', {'resolved.nose_volume_centroid_x_geo_m':-.01}))
for density in (100.,200.):
    cases.append(('mass', {f'materials.{name}.density_kg_m3':density for name in ('nose','body','fins','motor_mount','centering_rings')}))
unit_materials = {f'materials.{name}.density_kg_m3':1. for name in ('nose','body','fins','motor_mount','centering_rings')}
cases.append(('mass', {**unit_materials,'resolved.nose_material_volume_m3':1e308,
    'resolved.body_material_volume_m3':1e308,'resolved.fin_set_material_volume_m3':1e308}))
cases.append(('mass', {**unit_materials,'resolved.nose_material_volume_m3':1e307,
    'resolved.nose_volume_centroid_x_geo_m':100.,'resolved.overall_length_m':200.}))
cases.append(('mass', {'materials.nose.density_kg_m3':5e-324,'resolved.nose_material_volume_m3':5e-324}))


def reference(operation, changes):
    if operation == 'material':
        material = BulkMaterial(**changes)
        return dict(name=material.name, density_kg_m3=material.density_kg_m3)
    if operation == 'catalog':
        return dict(cardboard_name=CARDBOARD.name, cardboard_density_kg_m3=CARDBOARD.density_kg_m3,
            polystyrene_name=POLYSTYRENE.name, polystyrene_density_kg_m3=POLYSTYRENE.density_kg_m3)
    source = geometry()
    materials = SingleStageRocketMaterials(POLYSTYRENE,CARDBOARD,CARDBOARD,CARDBOARD,CARDBOARD)
    for path, value in changes.items():
        if path.startswith('resolved.'): continue
        if path.startswith('materials.'): materials = changed(materials, path[10:], value)
        else: source = changed(source, path[9:] if path.startswith('geometry.') else path, value)
    result = GeometryResolver().resolve(rocket_geometry=source)
    for path, value in changes.items():
        if path.startswith('resolved.'): result = changed(result, path[9:], value)
    output = {f.name: getattr(result, f.name) for f in fields(result) if f.name != 'source'}
    if operation == 'mass':
        mass = StructuralMassPropertiesCalculator().evaluate(resolved_geometry=result, materials=materials)
        for name in ('nose','body','fins','motor_mount','centering_rings'):
            component = getattr(mass,name)
            output.update({name+'_material_name':component.material.name, name+'_density_kg_m3':component.material.density_kg_m3,
                name+'_mass_kg':component.mass_kg,name+'_cg_x_geo_m':component.cg_x_geo_m})
        output.update(structure_mass_kg=mass.structure_mass_kg, structure_cg_x_geo_m=mass.structure_cg_x_geo_m,
            structure_moment_kg_m=(mass.nose.mass_kg*mass.nose.cg_x_geo_m+mass.body.mass_kg*mass.body.cg_x_geo_m
                +mass.fins.mass_kg*mass.fins.cg_x_geo_m+mass.motor_mount.mass_kg*mass.motor_mount.cg_x_geo_m
                +mass.centering_rings.mass_kg*mass.centering_rings.cg_x_geo_m))
    return output


def effective_case(operation, changes):
    if operation in {'material', 'catalog'}:
        return operation, changes
    source = asdict(geometry())
    materials = asdict(SingleStageRocketMaterials(POLYSTYRENE,CARDBOARD,CARDBOARD,CARDBOARD,CARDBOARD))
    resolved_changes = {}
    for path, value in changes.items():
        if path.startswith('resolved.'):
            resolved_changes[path] = value
        else:
            target = materials if path.startswith('materials.') else source
            path = path[10:] if path.startswith('materials.') else path[9:] if path.startswith('geometry.') else path
            names = path.split('.')
            for name in names[:-1]:
                target = target[name]
            target[names[-1]] = value
    return operation, source, materials, resolved_changes


def run(selected):
    nonempty(selected)
    count_cases('VEHICLE', [effective_case(op, changes) for op, changes in selected])
    payload = '\n'.join('\t'.join([op, *(path+'='+encode(value) for path, value in changes.items())]) for op, changes in selected)+'\n'
    cp = os.pathsep.join(str(args.build_dir / name) for name in ('classes/java/main', 'classes/java/test'))
    process = subprocess.run([args.java, '-cp', cp, 'org.roketsim.core.VehicleParityProbe'],
        input=payload, capture_output=True, text=True, check=True)
    lines = process.stdout.splitlines()
    assert len(lines) == len(selected), (process.stderr, lines[-2:])
    successes = errors = quantities = exact = 0
    max_relative = 0.
    for (op, changes), line in zip(selected, lines):
        parts = line.split('\t')
        try: expected = reference(op, changes)
        except (ValueError, OverflowError) as error:
            assert parts[:2] == ['ERROR', type(error).__name__], (changes, error, parts)
            if hasattr(error, 'error_code'):
                assert len(parts) == 5, parts
                assert parts[2] == error.error_code and parts[3] == (error.field_name or 'null'), (changes, error, parts)
                want, got = error.value, decoded(parts[4])
                if isinstance(want, (float, int)) and not isinstance(want, bool):
                    assert (want == got or isinstance(want, float) and math.isnan(want) and math.isnan(got)), (changes, want, got)
                else: assert want == got, (changes, want, got)
            else:
                assert len(parts) == 3, parts
                assert str(error).split()[0] in decoded(parts[2]), (changes, error, parts)
            errors += 1
            continue
        assert parts[0] == 'OK', (changes, parts)
        actual = keyed_output(parts[1:], {camel(name) for name in expected})
        assert set(actual) == {camel(name) for name in expected}, (op, actual.keys(), expected.keys())
        for name, want in expected.items():
            got = decoded(actual[camel(name)])
            if isinstance(want, Enum):
                assert want.name == got
            elif isinstance(want, str):
                assert want == got
            else:
                # Quantity-specific accepted source test contracts (no new threshold).
                tolerance = (3e-14 if op == 'mass' and (name.endswith('_mass_kg') or name.endswith('_cg_x_geo_m') or name=='structure_moment_kg_m')
                    else 2e-14 if name in {'nose_material_volume_m3', 'nose_volume_centroid_x_geo_m', 'body_material_volume_m3'}
                    else 4e-15 if 'motor_mount' in name or 'centering_ring' in name else 3e-15)
                if name == 'fin_midchord_sweep_angle_rad' and want == 0:
                    assert abs(got-want) <= 1e-15  # existing static geometry fixture
                else: assert abs(got-want) <= abs(want)*tolerance, (changes, name, want, got)
                quantities += 1
                exact += int(got == want)
                max_relative = max(max_relative, abs(got-want)/abs(want) if want else 0.)
        successes += 1
    assert successes + errors == len(selected)
    print(f'{args.group.upper()}_PARITY: {successes} success, {errors} error cases; {quantities} numerical quantities, {exact} exact; max_relative={max_relative}')


if __name__ == '__main__':
    run([case for case in cases if args.group == 'all' or
        (case[0] == 'geometry' if args.group == 'geometry' else case[0] == 'mass' if args.group == 'mass' else case[0] in {'material', 'catalog'})])
    print(sys.version)
    print('NumPy', np.__version__, platform.platform())
