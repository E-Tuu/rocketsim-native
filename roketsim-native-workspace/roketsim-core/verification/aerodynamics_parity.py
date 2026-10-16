"""J-006 real accepted Python authority and strict named-field parity protocol."""
import argparse
from dataclasses import replace
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
from parity_contract import nonempty, keyed_output, count_cases
from propulsion_parity import geometry, changed, flatten, encode as scalar_encode, decoded
import numpy as np
from roketsim_native.aerodynamics.surfaces import SurfaceFinish, SingleStageRocketAerodynamicSurfaces
from roketsim_native.aerodynamics.drag import BasicDragEvaluator, NATIVE_BASIC_DRAG_V1_PROFILE as DP
from roketsim_native.aerodynamics.static_stability import (StaticAerodynamicEvaluator,
    StaticAeroContribution, StaticAerodynamicProperties, NATIVE_STATIC_STABILITY_V1_PROFILE as SP)
from roketsim_native.aerodynamics.static_margin import StaticMarginCalculator
from roketsim_native.environment.air_properties import DryAirProperties
from roketsim_native.flight_conditions.basic import BasicFlightConditions
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.mass.total import RocketMassProperties


def encode(value):
    # Integer counts stay integral at the test adapter boundary, not floating-point 4.0.
    return 'i:'+str(value) if type(value) is int else scalar_encode(value)


def python_call(op,changes):
    geo=geometry(); flight=BasicFlightConditions(100.,.3,123.,456.)
    air=DryAirProperties(340.,1.8e-5,1.5e-5,1.4)
    surfaces=SingleStageRocketAerodynamicSurfaces(*(SurfaceFinish(n,0.) for n in ('Nose','Body','Fins')))
    dp=DP; sp=SP
    for path,value in changes.items():
        if path.startswith('geometry.'): geo=changed(geo,path[9:],value)
        elif path.startswith('source.'): geo=GeometryResolver().resolve(rocket_geometry=changed(geo.source,path[7:],value))
        elif path.startswith('flight.'): flight=changed(flight,path[7:],value)
        elif path.startswith('air.'): air=changed(air,path[4:],value)
        elif path.startswith('surfaces.'): surfaces=changed(surfaces,path[9:],value)
        elif path.startswith('dp.'): dp=changed(dp,path[3:],value)
        elif path.startswith('sp.'): sp=changed(sp,path[3:],value)
        elif path=='dp': dp=value
        elif path=='sp': sp=value
    if op=='surface': return SurfaceFinish(changes.get('name','Synthetic'),changes.get('rough',0.))
    if op=='dp': return dp
    if op=='sp': return sp
    if op=='geometry': return geo
    if op=='drag': return BasicDragEvaluator().evaluate(resolved_geometry=geo,aerodynamic_surfaces=surfaces,flight_conditions=flight,air_properties=air,model_profile=dp)
    if op=='static': return StaticAerodynamicEvaluator().evaluate(geometry=geo,flight_conditions=flight,model_profile=sp)
    if op=='diagnostics': return (
        StaticAerodynamicEvaluator._fin_fin_interference_factor(changes.get('count',4)),
        StaticAerodynamicEvaluator._fin_cp_fraction(mach=changes.get('mach',.75),aspect_ratio=changes.get('aspect',geo.fin_aspect_ratio)))
    contribution=StaticAeroContribution(1.,changes.get('cp',.7))
    static=StaticAerodynamicProperties(contribution,contribution,2.,changes.get('cp',.7),None)
    mass=RocketMassProperties(1.,changes.get('cg',.65))
    if op=='chain':
        from propulsion_parity import python_call as propulsion_call
        mass=propulsion_call('total','f50',{'time':1.4})
        static=StaticAerodynamicEvaluator().evaluate(geometry=geo,flight_conditions=flight,model_profile=sp)
    elif op!='margin': raise AssertionError(op)
    return StaticMarginCalculator().evaluate(static_aerodynamics=static,rocket_mass_properties=mass,geometry=geo)


CASES=[]
def add(op,**changes): CASES.append((op,changes))
for name in ('Synthetic','', ' ', '\u0085','\u00a0',None):
    for rough in (0.,1e-6,100.,-1.,math.nan,math.inf,-math.inf): add('surface',name=name,rough=rough)
for op,limit in (('drag',1.),('static',.8)):
    for mach in (0.,.3,.5,math.nextafter(.5,0.),math.nextafter(.5,1.),.75,.8,1.,math.nextafter(limit,0.),math.nextafter(limit,math.inf),-.1,math.nan,math.inf,-math.inf):
        for rough in (0.,1e-10,1e-5,1e-3): add(op,**{'flight.mach':mach,'surfaces.body.equivalent_roughness_m':rough})
for speed in (0.,.001,.149999999999,.15,.150000000001,100.,1000.,-1.,math.nan,math.inf,1e308):
    for nu in (1.5e-5,0.,-1.,math.nan,math.inf): add('drag',**{'flight.airspeed_m_s':speed,'air.kinematic_viscosity_m2_s':nu})
for gamma in (1.,1.2,1.4,1.67,0.,math.nan,math.inf): add('drag',**{'air.specific_heat_ratio':gamma})
for part in ('nose','body','fins'):
    for rough in (0.,1e-10,1e-5,1e-3): add('drag',**{'surfaces.'+part+'.equivalent_roughness_m':rough})
for op in ('drag','static','geometry'):
    for offset in (-.05,0.,.05,.1):
        for tip in (0.,.08,.12): add(op,**{'source.fins.tip_leading_edge_offset_x_m':offset,'source.fins.tip_chord_m':tip})
for count in (3,4,5,6,7,8,9,12):
    for mach in (0.,.5,.75): add('static',**{'source.fins.fin_count':count,'flight.mach':mach})
for n in range(1,21): add('diagnostics',count=n)
for mach in (0.,.25,.5,.500001,.75,math.nextafter(.8,0.)):
    for aspect in (.5,1.,1.8461538461538463,4.): add('diagnostics',mach=mach,aspect=aspect)
drag_fields=('reference_area_m2','max_external_airframe_diameter_m','axisymmetric_body_length_m','fin_mean_aerodynamic_chord_m','nose_wetted_area_m2','body_wetted_area_m2','fin_planform_area_per_fin_m2','nose_frontal_area_m2','airframe_aft_base_area_m2','aerodynamic_length_m')
static_fields=('reference_area_m2','nose_frontal_area_m2','nose_axial_length_m','fin_planform_area_per_fin_m2','fin_mean_aerodynamic_chord_m','fin_aspect_ratio','fin_body_radius_at_root_m')
for op,names in (('drag',drag_fields),('static',static_fields)):
    for field in names:
        for value in (0.,-1.,math.nan,math.inf): add(op,**{'geometry.'+field:value})
for op,path in (('drag','geometry.nose_half_angle_rad'),('drag','geometry.fin_leading_edge_sweep_angle_rad'),('static','geometry.fin_midchord_sweep_angle_rad'),('static','geometry.fin_mean_aerodynamic_chord_leading_edge_x_geo_m')):
    for value in (0.,-1.,math.pi,math.nan,math.inf): add(op,**{path:value})
for count in (1,2,True,4.,None): add('static',**{'geometry.source.fins.fin_count':count})
add('drag',**{'geometry.fin_cross_section':None})
add('static',**{'geometry.fin_angular_arrangement':None})
for op,profile in (('drag','dp'),('static','sp')):
    for value in (None,'unsupported'): add(op,**{profile:value})
    fields=DP.__dataclass_fields__ if profile=='dp' else SP.__dataclass_fields__
    for field in fields:
        value=2e4 if field=='minimum_friction_evaluation_reynolds' else .9 if field=='maximum_supported_mach_exclusive' else None
        add(op,**{profile+'.'+field:value})
for op,field in (('dp','minimum_friction_evaluation_reynolds'),('sp','maximum_supported_mach_exclusive')):
    for value in (0.,-1.,math.nan,math.inf): add(op,**{op+'.'+field:value})
for cp,cg in ((.7,.6),(.65,.65),(.6,.7),(1e308,-1e308)):
    for diameter in (.1,.2,0.,-1.,math.nan,math.inf): add('margin',cp=cp,cg=cg,**{'geometry.max_external_airframe_diameter_m':diameter})
for field in ('cp','cg'):
    for value in (math.nan,math.inf,-math.inf): add('margin',**{field:value})
add('margin',**{'geometry.reference_length_m':math.nan})
add('drag',**{'flight.reynolds':math.nan,'flight.dynamic_pressure_Pa':math.nan})
add('static',**{'geometry.nose_material_volume_m3':math.nan,'geometry.nose_volume_centroid_x_geo_m':math.nan})
for mach in (0.,.5,.75): add('chain',**{'flight.mach':mach})

# J006-P01: exact selectors are metadata, not numeric input. Keep original 487 cases.
for op,prefix,profile in (('drag','dp',DP),('static','sp',SP)):
    canonical={}
    for field in profile.__dataclass_fields__:
        choice=getattr(profile,field)
        if not hasattr(choice,'value'): continue
        canonical[prefix+'.'+field]=choice.value
        for value in (choice,choice.value,choice.value.upper(),' '+choice.value,
                      choice.value+' ','unknown','',None,1,True):
            add(op,**{prefix+'.'+field:value})
    add(op,**canonical)
    # Reject changed numerical policies even when every selector uses accepted strings.
    numeric='minimum_friction_evaluation_reynolds' if prefix=='dp' else 'maximum_supported_mach_exclusive'
    limit=getattr(profile,numeric)
    for value in (math.nextafter(limit,0.),math.nextafter(limit,math.inf)):
        add(op,**{**canonical,prefix+'.'+numeric:value})
add('drag',dp=SP)
add('static',sp=DP)


def encode_profile(value):
    # Test protocol marker for invalid *profile class* cases; no production parsing.
    if value is SP: return 'profile:static'
    if value is DP: return 'profile:drag'
    return encode(value)


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--java',required=True); parser.add_argument('--build-dir',type=Path,required=True)
    args=parser.parse_args(); nonempty(CASES)
    # Include the effective defaults in the count, not only syntactically different overrides.
    effective=[]
    for op,changes in CASES:
        geo=geometry()
        roots={'geometry':geo,'source':geo.source,'flight':BasicFlightConditions(100.,.3,123.,456.),
            'air':DryAirProperties(340.,1.8e-5,1.5e-5,1.4),'dp':DP,'sp':SP,
            'surfaces':SingleStageRocketAerodynamicSurfaces(*(SurfaceFinish(n,0.) for n in ('Nose','Body','Fins')))}
        defaults={'mach':.75,'count':4,'aspect':geo.fin_aspect_ratio,'name':'Synthetic','rough':0.,'cp':.7,'cg':.65}
        for path in changes:
            if '.' in path:
                root,*tail=path.split('.'); default=roots[root]
                for part in tail: default=getattr(default,part)
                defaults[path]=default
        effective.append((op,{k:v for k,v in changes.items() if k not in defaults or defaults[k]!=v}))
    count_cases('AERODYNAMICS',effective)
    payload='\n'.join('\t'.join([op,*(key+'='+encode_profile(value) for key,value in changes.items())]) for op,changes in CASES)+'\n'
    cp=os.pathsep.join(str(args.build_dir/path) for path in ('classes/java/main','classes/java/test'))
    result=subprocess.run([args.java,'-cp',cp,'org.roketsim.core.AerodynamicsParityProbe'],input=payload,text=True,capture_output=True,check=True)
    lines=result.stdout.splitlines()
    if len(lines)!=len(CASES): raise AssertionError(('Truncated/extra result set',len(lines),len(CASES),result.stderr))
    successes=errors=quantities=exact=0; max_relative=0.; evidence=[]
    for case,line in zip(CASES,lines):
        op,changes=case
        try:
            value=python_call(op,changes); expected=flatten(value); status='OK'
            if op=='drag': expected.update({'result.frictionCd':value.friction_cd,'result.pressureCd':value.pressure_cd,'result.baseCd':value.base_cd})
        except ValueError as error:
            status='ERROR'; expected={'error.category':type(error).__name__,'error.code':getattr(error,'error_code',None),'error.field':getattr(error,'field_name',None)}
            expected.update(flatten(getattr(error,'value',None),'error.value'))
            expected['error.context']=str(error).split()[0] if type(error) is ValueError else None
        tokens=line.split('\t')
        try:
            if tokens[0]!=status: raise AssertionError(('Wrong result/error category',status,tokens[0]))
            actual={k:decoded(v) for k,v in keyed_output(tokens[1:],expected).items()}
            for key,want in expected.items():
                got=actual[key]
                if isinstance(want,(int,float)):
                    if status=='ERROR': match=want==got or math.isnan(want) and math.isnan(got)
                    else:
                        # Accepted NAT-012 equation assertions: drag 3e-15 (frozen terms 5e-15), static 4e-15, margin 5e-15.
                        tolerance=3e-15 if op=='drag' else 4e-15 if op in {'static','diagnostics'} else 5e-15 if op in {'margin','chain'} else 3e-15 if op=='geometry' else 0.
                        match=math.isfinite(got) and abs(got-want)<=abs(want)*tolerance
                        quantities+=1; exact+=int(want==got)
                        if want: max_relative=max(max_relative,abs(got-want)/abs(want))
                    if not match: raise AssertionError((key,want,got))
                elif want!=got: raise AssertionError((key,want,got))
        except (AssertionError,TypeError,ValueError) as discrepancy:
            finding=dict(input=repr(case),python=repr(expected),java=line,discrepancy=repr(discrepancy))
            print('PARITY_FINDING '+json.dumps(finding)); raise
        if status=='OK' and (not changes or changes=={'flight.mach':.75}): evidence.append(dict(input=repr(case),python=expected,java=actual))
        successes+=status=='OK'; errors+=status=='ERROR'
    if successes+errors!=len(CASES): raise AssertionError('Unexecuted cases')
    print(f'AERODYNAMICS_PARITY: {successes} success, {errors} expected errors; {quantities} numeric, {exact} exact; max_relative={max_relative!r}')
    for row in evidence: print('AERO_EVIDENCE '+json.dumps(row,allow_nan=False))
    print(sys.version); print('NumPy',np.__version__,platform.platform())


if __name__=='__main__': main()
