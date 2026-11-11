"""J-005 actual Python production reference; no expected outputs originate from Java."""

import argparse
import base64
from dataclasses import fields, is_dataclass, replace
from enum import Enum
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys

from parity_contract import count_cases, keyed_output, nonempty
import numpy as np

CORE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(CORE.parent/'roketsim-native-engine/src'))
from roketsim_native.propulsion.models import *
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T as F50, DEMO_MOTOR_CATALOG, MotorCatalog
from roketsim_native.propulsion.installation import MotorInstallationResolver
from roketsim_native.propulsion.thrust import MotorThrustCurveEvaluator, MotorCurveAnalyzer
from roketsim_native.propulsion.properties import (MotorMassEvolutionModel as MM, MotorCgEvolutionModel as CM,
    MotorPropertyModelProfile as Profile, MotorPropertyEvaluator)
from roketsim_native.geometry import models as gm
from roketsim_native.geometry.resolver import GeometryResolver
from roketsim_native.materials.models import SingleStageRocketMaterials
from roketsim_native.materials.catalog import POLYSTYRENE, CARDBOARD
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator
from roketsim_native.mass.total import RocketMassPropertiesCalculator


def geometry():
    return GeometryResolver().resolve(rocket_geometry=gm.SingleStageRocketGeometry(.1,
        gm.ConicalNoseGeometry(.3,gm.NoseConstructionMode.HOLLOW_SHELL,.002),gm.CylindricalBodyGeometry(.7,.002),
        gm.TrapezoidalFinSetGeometry(4,.18,.08,.12,.05,.72,.003,gm.FinCrossSection.SQUARE,gm.FinAngularArrangement.EQUALLY_SPACED),
        gm.MotorAttachmentGeometry(gm.MotorMountTubeGeometry(.12,.029,.001,0.),gm.CenteringRingPairGeometry(.003),.005),
        gm.ReferenceGeometryPolicy.MAXIMUM_DIAMETER))


def fixture(name):
    points={'triangle':[(0.,0.),(1.,20.),(3.,0.)],
        'plateau':[(0.,0.),(1.,1.),(2.,1.),(3.,20.),(4.,0.),(5.,1.),(6.,1.),(7.,0.)],
        'multi':[(0.,0.),(1.,20.),(2.,0.),(3.,20.),(4.,0.)],
        'overflow':[(0.,0.),(1e308,20.),(1.1e308,0.)],
        'underflow':[(0.,0.),(1e-200,1e-200),(2e-200,0.)]}
    if name=='f50': return F50
    if name in points: return replace(F50,thrust_curve=tuple(ThrustSample(*p) for p in points[name]))
    if name not in {'source','independent'}: raise AssertionError(name)
    motor=replace(F50,initial_mass_kg=.1,length_m=.1,
        thrust_curve=tuple(ThrustSample(*p) for p in [(0.,0.),(.2,20.),(.8,0.)]),
        mass_curve=tuple(MotorMassSample(*p) for p in [(0.,.1),(.5,.08),(1.,.06)]),
        cg_curve=tuple(MotorCgSample(*p) for p in [(0.,.04),(.5,.05),(1.,.06)]),
        provenance=replace(F50.provenance,mass_curve_source='Synthetic test mass source',cg_curve_source='Synthetic test CG source'))
    if name=='independent':
        motor=replace(motor,mass_curve=tuple(MotorMassSample(*p) for p in [(0.,.1),(.4,.08),(2.,.04)]),
            cg_curve=tuple(MotorCgSample(*p) for p in [(0.,.02),(.6,.08),(3.,.02)]))
    return motor


def changed(obj,path,value):
    head,*tail=path.split('.',1)
    return replace(obj,**{head:changed(getattr(obj,head),tail[0],value) if tail else value})


def python_call(operation,name,changes):
    motor=fixture(name); geo=geometry()
    for path,value in changes.items():
        if path.startswith('motor.'): motor=changed(motor,path[6:],value)
        elif path.startswith('geometry.'): geo=changed(geo,path[9:],value)
        elif path.startswith('curve.'):
            kind=path[6:]; cls={'thrust':ThrustSample,'mass':MotorMassSample,'cg':MotorCgSample}[kind]
            points=tuple(cls(*map(float,part.split(','))) for part in value.split(';')) if value else ()
            motor=replace(motor,**{kind+'_curve':points})
    time=changes.get('time',0.)
    if operation=='catalog': return motor
    if operation=='lookup': return DEMO_MOTOR_CATALOG.get(changes.get('lookup','aerotech_f50_4t'))
    if operation=='duplicate': return MotorCatalog((motor,motor))
    if operation in {'certification','provenance'}:
        obj=getattr(motor,operation)
        for path,value in changes.items(): obj=changed(obj,path,value)
        return obj
    if operation=='sample':
        cls={'thrust':ThrustSample,'mass':MotorMassSample,'cg':MotorCgSample}[changes.get('kind','thrust')]
        return cls(time,changes.get('value',0.))
    if operation=='analyze': return MotorCurveAnalyzer().analyze(motor=motor)
    if operation=='thrust': return MotorThrustCurveEvaluator().evaluate(motor=motor,motor_time_s=time)
    profile_args=dict(mass_model=changes.get('mass_model',MM.IMPULSE_PROPORTIONAL),cg_model=changes.get('cg_model',CM.FIXED_MIDPOINT))
    if operation=='profile': return Profile(**profile_args)
    installation=MotorInstallationResolver().resolve(resolved_geometry=geo,motor=motor)
    for path,value in changes.items():
        if path.startswith('installation.'): installation=changed(installation,path[13:],value)
    if operation=='install': return installation
    properties=MotorPropertyEvaluator().evaluate(installation=installation,model_profile=Profile(**profile_args),motor_time_s=time)
    if operation=='properties': return properties
    if operation!='total': raise AssertionError(operation)
    structure=StructuralMassPropertiesCalculator().evaluate(resolved_geometry=geometry(),
        materials=SingleStageRocketMaterials(POLYSTYRENE,CARDBOARD,CARDBOARD,CARDBOARD,CARDBOARD))
    for path,value in changes.items():
        if path.startswith('structure.'): structure=changed(structure,path[10:],value)
        elif path.startswith('properties.'): properties=changed(properties,path[11:],value)
    return RocketMassPropertiesCalculator().evaluate(structural_properties=structure,motor_properties=properties)


def camel(name):
    head,*tail=name.split('_')
    return head+''.join(part[0].upper()+part[1:] for part in tail)


def flatten(value,prefix='result'):
    output={}
    if is_dataclass(value):
        for field in fields(value): output.update(flatten(getattr(value,field.name),prefix+'.'+camel(field.name)))
    elif isinstance(value,(tuple,list)):
        output[prefix+'.size']=len(value)
        for i,item in enumerate(value): output.update(flatten(item,prefix+'.'+str(i)))
    else: output[prefix]=value.name if isinstance(value,Enum) else value
    return output


def encode(value):
    if value is None: return 'null'
    if isinstance(value,Enum): return 'e:'+type(value).__name__+'.'+value.name
    if isinstance(value,str): return 's:'+base64.b64encode(value.encode()).decode()
    if isinstance(value,bool): return 'b:'+str(value).lower()
    return 'd:'+('NaN' if math.isnan(value) else 'Infinity' if value==math.inf else '-Infinity' if value==-math.inf else repr(float(value)))


def decoded(value):
    if value=='null': return None
    if value in {'true','false'}: return value=='true'
    if value.startswith('s:'): return base64.b64decode(value[2:],validate=True).decode()
    try: return float(value)
    except ValueError: return value


def effective_inputs(case):
    # Normalize explicit default overrides only for counting, never for evaluation.
    operation,name,changes=case
    motor=fixture(name); geo=geometry(); effective={}
    for path,value in changes.items():
        found=False; default=None
        if path.startswith('motor.'):
            default=motor
            for part in path[6:].split('.'): default=getattr(default,part)
            found=True
        elif path.startswith('geometry.'):
            default=getattr(geo,path[9:]); found=True
        elif path in {'time','mass_model','cg_model'}:
            default={'time':0.,'mass_model':MM.IMPULSE_PROPORTIONAL,'cg_model':CM.FIXED_MIDPOINT}[path]; found=True
        if not found or default!=value: effective[path]=value
    return operation,name,effective


CASES=[]
def add(op,name='f50',**changes): CASES.append((op,name,changes))
for name in ('f50','source','independent'): add('catalog',name)
for name in ('f50','triangle','plateau','multi','overflow','underflow'): add('analyze',name)
for name in ('f50','triangle','plateau','multi'):
    motor=fixture(name)
    times=[0.,.354,1.4,1.43,100.,-1.,math.nan,math.inf,-math.inf]
    for sample in motor.thrust_curve: times.extend((sample.time_s,math.nextafter(sample.time_s,-math.inf),math.nextafter(sample.time_s,math.inf)))
    times.extend((a.time_s+b.time_s)/2 for a,b in zip(motor.thrust_curve,motor.thrust_curve[1:]))
    for time in times: add('thrust',name,time=time)
for time in (i/1000 for i in range(0,1601,10)): add('thrust',time=time)
for name in ('f50','source','independent'):
    for mass in MM:
        for cg in CM:
            for time in (0.,.25,.354,.5,.75,.8,1.,1.4,1.43,2.5,3.,100.,-1.,math.nan):
                add('properties',name,time=time,mass_model=mass,cg_model=cg)
for time in (0.,.354,1.4,1.43,100.): add('total',time=time)
for diameter in (.025,.029,.04): add('install',**{'motor.diameter_m':diameter})
for aft in (1.005,1.,.99,1.125,1.25): add('install',**{'geometry.motor_aft_reference_x_geo_m':aft})
add('install',**{'geometry.motor_mount_start_x_geo_m':.75,'geometry.motor_mount_end_x_geo_m':1.,'geometry.motor_aft_reference_x_geo_m':1.,'motor.length_m':.25})
add('install',**{'geometry.motor_aft_reference_x_geo_m':-1e308,'motor.length_m':1e308})
add('install',**{'geometry.motor_mount_start_x_geo_m':-1e308,'geometry.motor_mount_end_x_geo_m':-1e308,'geometry.motor_aft_reference_x_geo_m':1e308})
for field in ('motor_aft_reference_x_geo_m','motor_mount_inner_diameter_m','motor_mount_start_x_geo_m','motor_mount_end_x_geo_m'):
    for value in (math.nan,math.inf,-math.inf): add('install',**{'geometry.'+field:value})
for field in ('diameter_m','length_m','initial_mass_kg','propellant_mass_kg','ejection_delay_s'):
    for value in (0.,-1.,math.nan,math.inf,-math.inf): add('catalog',**{'motor.'+field:value})
for field in ('motor_id','manufacturer','designation','family','propellant_name'):
    for value in ('',' \t','\u00a0','\u0085',None): add('catalog',**{'motor.'+field:value})
for field,value in (('motor_type','single_use'),('motor_type',None),('motor_type',False),('certification',None),('provenance',None),('propellant_mass_kg',.0849),('propellant_mass_kg',.1)):
    add('catalog',**{'motor.'+field:value})
for points in ('','0,0;1,0','0,1;1,2;2,0','.1,0;1,2;2,0','0,0;1,2;1,0','0,0;2,2;1,0','0,0;1,0;2,0','0,0;1,2;2,1'):
    add('catalog',**{'curve.thrust':points})
for kind in ('thrust','mass','cg'):
    for time,value in ((0.,0.),(-1.,1.),(0.,-1.),(math.nan,1.),(0.,math.inf),(0.,math.nan)):
        add('sample',kind=kind,time=time,value=value)
for field in ('measured_total_impulse_N_s','measured_average_thrust_N','measured_peak_thrust_N','measured_burn_time_s'):
    for value in (0.,-1.,math.nan,math.inf,-math.inf): add('certification',**{field:value})
for field in ('manufacturer_source','certification_source','thrust_curve_source','normalization_note','mass_curve_source','cg_curve_source'):
    for value in ('',' \n',None): add('provenance',**{field:value})
for field in ('mass_model','cg_model'):
    for value in ('unknown',None): add('profile',**{field:value})
for kind in ('mass','cg'):
    points=('','0,.1','.1,.1;1,.06','0,.1;0,.08;1,.06','0,.1;.7,.08;.5,.07;1,.06','0,.1;.79,.06')
    for value in points: add('catalog','source',**{'curve.'+kind:value})
add('catalog','source',**{'curve.mass':'0,.1;1,.11'})
add('catalog','source',**{'curve.mass':'0,.099;1,.06'})
add('catalog','source',**{'curve.cg':'0,.04;1,.101'})
for name in ('mass','cg'):
    add('catalog','source',**{'motor.'+name+'_curve':None})
    add('catalog','source',**{'motor.provenance.'+name+'_curve_source':None})
for value in ('aerotech_f50_4t','unknown','F50-4T',''): add('lookup',lookup=value)
add('duplicate')
for a,b in ((0.,0.),(-2.,1.),(1e308,1e308),(math.nan,1.),(1.,math.inf)):
    add('total',**{'structure.structure_mass_kg':a,'properties.mass_kg':b})
for a,b in ((math.nan,1.),(1.,math.inf),(1e308,0.)):
    add('total',**{'structure.structure_mass_kg':2.,'structure.structure_cg_x_geo_m':a,'properties.mass_kg':1.,'properties.cg_x_geo_m':b})
add('total',**{'structure.structure_mass_kg':2.,'structure.structure_cg_x_geo_m':1.,'properties.mass_kg':-1.,'properties.cg_x_geo_m':2.})
for coordinate in (-10.,0.,.626619970554718,1e100):
    for a,b in ((.1,.3),(1e-100,1e100)):
        add('total',**{'structure.structure_mass_kg':a,'structure.structure_cg_x_geo_m':coordinate,'properties.mass_kg':b,'properties.cg_x_geo_m':coordinate})
add('properties',**{'installation.motor_front_x_geo_m':math.inf})


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--java',required=True)
    parser.add_argument('--build-dir',type=Path,required=True)
    parser.add_argument('--f50-evidence',action='store_true')
    args=parser.parse_args()
    nonempty(CASES); count_cases('PROPULSION',[effective_inputs(case) for case in CASES])
    payload='\n'.join('\t'.join([op,name,*(key+'='+encode(value) for key,value in changes.items())]) for op,name,changes in CASES)+'\n'
    cp=os.pathsep.join(str(args.build_dir/path) for path in ('classes/java/main','classes/java/test'))
    result=subprocess.run([args.java,'-cp',cp,'org.roketsim.core.PropulsionParityProbe'],input=payload,text=True,capture_output=True,check=True)
    lines=result.stdout.splitlines()
    if len(lines)!=len(CASES): raise AssertionError(('Truncated/extra result set',len(lines),len(CASES),result.stderr))
    successes=errors=quantities=exact=0
    evidence_seen=set()
    for case,line in zip(CASES,lines):
        op,name,changes=case
        try:
            expected=flatten(python_call(*case)); status='OK'
        except (ValueError,KeyError) as error:
            status='ERROR'
            expected={'error.category':type(error).__name__,'error.code':getattr(error,'error_code',None),'error.field':getattr(error,'field_name',None)}
            expected.update(flatten(error.args[0] if isinstance(error,KeyError) else getattr(error,'value',None),'error.value'))
            expected['error.context']=str(error).split()[0] if type(error) is ValueError else None
        tokens=line.split('\t')
        try:
            if tokens[0]!=status: raise AssertionError(('Wrong result/error category',status,tokens[0]))
            actual={key:decoded(value) for key,value in keyed_output(tokens[1:],expected).items()}
            for key,want in expected.items():
                got=actual[key]
                if isinstance(want,(int,float)):
                    if status=='ERROR': match=want==got or math.isnan(want) and math.isnan(got)
                    else:
                        tolerance=(4e-15 if op=='thrust' and key.endswith('.thrustN') else
                            2e-15 if op=='install' and key.endswith('.nominalRadialClearanceM') else
                            0. if op in {'catalog','lookup','sample','profile','certification','provenance'} or '.motor.' in key else 3e-15)
                        if op=='thrust' and key.endswith('.thrustN') and changes.get('time',0.) in {s.time_s for s in fixture(name).thrust_curve}: tolerance=0.
                        match=math.isfinite(got) and abs(got-want)<=abs(want)*tolerance
                        quantities+=1; exact+=int(want==got)
                    if not match: raise AssertionError((key,want,got))
                elif want!=got: raise AssertionError((key,want,got))
        except (AssertionError,TypeError,ValueError) as discrepancy:
            print('PARITY_FINDING '+json.dumps(dict(input=repr(case),python=repr(expected),java=line,discrepancy=repr(discrepancy))))
            raise
        evidence_key=repr(effective_inputs(case))
        if (args.f50_evidence and status=='OK' and name=='f50'
                and op in {'analyze','install','thrust','properties','total'}
                and changes.get('time',0.) in {0.,.354,1.4,1.43}
                and changes.get('mass_model',MM.IMPULSE_PROPORTIONAL) is MM.IMPULSE_PROPORTIONAL
                and changes.get('cg_model',CM.FIXED_MIDPOINT) is CM.FIXED_MIDPOINT
                and evidence_key not in evidence_seen):
            evidence_seen.add(evidence_key)
            print('F50_EVIDENCE '+json.dumps(dict(input=repr(case),python=expected,java=actual),allow_nan=False))
        successes+=status=='OK'; errors+=status=='ERROR'
    if successes+errors!=len(CASES): raise AssertionError('Unexecuted cases')
    print(f'PROPULSION_PARITY: {successes} success, {errors} expected errors; {quantities} numeric quantities, {exact} exact')
    print(sys.version); print('NumPy',np.__version__,platform.platform())


if __name__=='__main__': main()
