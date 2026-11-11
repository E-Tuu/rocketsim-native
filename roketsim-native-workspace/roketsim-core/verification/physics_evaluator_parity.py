"""J-009 real NAT-015 orchestration, complete nested results and strict protocol."""
import argparse
from dataclasses import fields
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
import numpy as np
from parity_contract import nonempty, keyed_output, count_cases
from dynamics_parity import encode, flatten, derived
from propulsion_parity import decoded, geometry, changed
from roketsim_native.simulation.physics import (EnvironmentPositionMappingModel as Mapping,
    PropulsionTimeline, PhysicsEvaluationContext3DOF, PhysicsEvaluator3DOF)
from roketsim_native.environment.atmosphere import USStandardAtmosphere1976Lower, AtmosphereDomainError
from roketsim_native.environment.air_properties import DryAirPropertiesCalculator
from roketsim_native.environment.gravity import ConstantGravityModel
from roketsim_native.environment.wind import NoWindModel, ConstantWindModel
from roketsim_native.aerodynamics.drag import NATIVE_BASIC_DRAG_V1_PROFILE as DP
from roketsim_native.aerodynamics.surfaces import SurfaceFinish, SingleStageRocketAerodynamicSurfaces
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T
from roketsim_native.propulsion.installation import MotorInstallationResolver
from roketsim_native.propulsion.properties import DEMO_MOTOR_PROPERTY_MODEL_PROFILE as MP, MotorMassEvolutionModel
from roketsim_native.materials.models import SingleStageRocketMaterials
from roketsim_native.materials.catalog import POLYSTYRENE, CARDBOARD
from roketsim_native.mass.structural import StructuralMassPropertiesCalculator
from roketsim_native.dynamics.initial_state import TranslationalState3DOF, LaunchConditions3DOF
from roketsim_native.dynamics.translational import NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE as TP

DEFAULTS=dict(time=5.,ignition=5.,altitude=1200.,position=(10.,20.,50.),velocity=(0.,0.,0.),
    launch_position=(10.,20.,50.),launch_velocity=(0.,0.,0.),direction=(0.,0.,1.),
    wind_enabled=False,wind=(0.,0.,0.),mapping=Mapping.LOCAL_ENU_VERTICAL_OFFSET)
GEO=geometry()
STRUCTURE=StructuralMassPropertiesCalculator().evaluate(resolved_geometry=GEO,
    materials=SingleStageRocketMaterials(POLYSTYRENE,CARDBOARD,CARDBOARD,CARDBOARD,CARDBOARD))
INSTALLATION=MotorInstallationResolver().resolve(resolved_geometry=GEO,motor=AEROTECH_F50_4T)


def context(v):
    smooth=SurfaceFinish('Synthetic smooth',0.)
    c=PhysicsEvaluationContext3DOF(GEO,SingleStageRocketAerodynamicSurfaces(smooth,smooth,smooth),
        STRUCTURE,INSTALLATION,MP,DP,TP,v['mapping'],v['altitude'],PropulsionTimeline(v['ignition']),
        USStandardAtmosphere1976Lower(),DryAirPropertiesCalculator(),ConstantGravityModel(),
        ConstantWindModel(airmass_velocity_world_m_s=v['wind']) if v['wind_enabled'] else NoWindModel())
    for path,value in v.items():
        if path.startswith('context.'): c=changed(c,path[8:],value)
    return c


def effective(case):
    op,changes=case
    v={**DEFAULTS,**changes}
    if op=='timeline': return op,{'ignition':v['ignition']}
    if op=='context':
        v={k:value for k,value in v.items() if k not in ('time','position','velocity','launch_position','launch_velocity','direction')}
    if not v['wind_enabled']: v.pop('wind')
    return op,v


def python_call(op,v):
    if op=='timeline': return flatten(PropulsionTimeline(v['ignition']))
    c=context(v)
    if op=='context':
        output={'result.launchEnvironmentAltitudeM':c.launch_environment_altitude_m,
            'result.environmentPositionMappingModel':c.environment_position_mapping_model.name if isinstance(c.environment_position_mapping_model,Mapping) else c.environment_position_mapping_model,
            'contract.contextFieldCount':len(fields(c)),'contract.windType':type(c.wind_model).__name__}
        output.update(flatten(c.propulsion_timeline,'result.propulsionTimeline'))
        output.update(flatten(c.motor_property_model_profile,'result.motorPropertyModelProfile'))
        output.update(flatten(c.basic_drag_model_profile,'result.basicDragModelProfile'))
        output.update(flatten(c.translational_dynamics_model_profile,'result.translationalDynamicsModelProfile'))
        return output
    if op!='evaluate': raise AssertionError(op)
    state=TranslationalState3DOF(v['position'],v['velocity'])
    launch=LaunchConditions3DOF(v['launch_position'],v['launch_velocity'],v['direction'])
    result=PhysicsEvaluator3DOF().evaluate(time_s=v['time'],state=state,launch_conditions=launch,context=c)
    output=flatten(result); output.update(derived(result.dynamics,'result.dynamics'))
    output['contract.motorProfileIdentity']=result.motor_mass_properties.model_profile is c.motor_property_model_profile
    output['contract.dragProfileIdentity']=result.basic_drag.model_profile is c.basic_drag_model_profile
    output['contract.dynamicsProfileIdentity']=result.dynamics.model_profile is c.translational_dynamics_model_profile
    for name in ('friction_cd','pressure_cd','base_cd'):
        from propulsion_parity import camel
        output['result.basicDrag.'+camel(name)]=getattr(result.basic_drag,name)
    return output


CASES=[]
def add(op='evaluate',**changes): CASES.append((op,changes))
for op in ('timeline','context','evaluate'): add(op)
# Source NAT-015 A-G anchors; H remains a controlled J-007 snapshot test, not fake full-chain physics.
add(position=(10.,20.,173.25)); add(position=(10.,20.,20.)); add(time=6.43)
add(time=5.); add(time=4.999); add(time=.012,ignition=0.)
add(velocity=(3.,4.,5.)); add(velocity=(3.,4.,5.),wind_enabled=True,wind=(1.,-2.,.5))
ANCHORS=CASES[3:]
for op in ('timeline','context','evaluate'):
    for ignition in (-5.,0.,5.,math.nan,math.inf,-math.inf): add(op,ignition=ignition)
for op in ('context','evaluate'):
    for altitude in (-5000.,-100.,0.,1200.,11000.,20000.,32000.,47000.,51000.,71000.,84852.,90000.,-5000.01,math.nan,math.inf,-math.inf): add(op,altitude=altitude)
for time,ignition in ((-1.,-2.),(-5.,-5.),(5.,5.),(math.nextafter(5.,0.),5.),(math.nextafter(5.,math.inf),5.),(1e308,-1e308)):
    add(time=time,ignition=ignition)
for time in (math.nan,math.inf,-math.inf): add(time=time)
for mapping in (Mapping.LOCAL_ENU_VERTICAL_OFFSET,'local_enu_vertical_offset','LOCAL_ENU_VERTICAL_OFFSET',' local_enu_vertical_offset','other',None,False,1.):
    add(mapping=mapping); add('context',mapping=mapping)
for speed in ((0.,0.,0.),(3.,4.,5.),(0.,0.,100.),(0.,0.,-100.),(30.,-40.,100.),(.1,.2,.3),(3.1,4.2,5.3),(0.,0.,330.),(0.,0.,1000.)):
    for wind in ((0.,0.,0.),(1.,-2.,.5),speed): add(velocity=speed,wind_enabled=True,wind=wind,time=5.012)
for time in (0.,.012,.354,1.4,1.43,2.,10.):
    for velocity in ((0.,0.,0.),(3.,4.,5.),(0.,0.,100.)): add(time=time,ignition=0.,velocity=velocity)
for path,value in (
    ('resolved_geometry.reference_length_m',.2),('resolved_geometry.reference_length_m',0.),
    ('resolved_geometry.reference_length_m',math.nan),('resolved_geometry.aerodynamic_length_m',.5),
    ('resolved_geometry.reference_area_m2',0.),('basic_drag_model_profile',None),
    ('basic_drag_model_profile.boundary_layer_model','fully_turbulent'),
    ('basic_drag_model_profile.boundary_layer_model','other'),
    ('basic_drag_model_profile.minimum_friction_evaluation_reynolds',20000.),
    ('motor_property_model_profile.mass_model',MotorMassEvolutionModel.EXPLICIT_CURVE),
    ('translational_dynamics_model_profile.thrust_direction_model','fixed_launch_direction'),
    ('translational_dynamics_model_profile.aerodynamic_force_model','other'),
    ('structural_mass_properties.structure_mass_kg',math.inf)):
    add(**{'context.'+path:value})
for launchz,currentz in ((-1e308,1e308),(1e308,-1e308),(1e308,1e308),(-100.,23.25)):
    add(launch_position=(10.,20.,launchz),position=(10.,20.,currentz))
for field in ('position','velocity','launch_position','launch_velocity','direction','wind'):
    for invalid in ((1.,2.),(math.nan,0.,0.),(math.inf,0.,0.),(-math.inf,0.,0.)):
        add(**{field:invalid},wind_enabled=True)
for direction in ((0.,0.,0.),(.6,0.,.8),(0.,0.,1+.5e-8),(0.,0.,2.)): add(direction=direction,time=5.012)
# Observable source failure priority: time -> selector -> atmosphere -> flow -> motor time -> propulsion -> drag -> dynamics.
add(time=math.nan,mapping='other')
add(time=4.999,altitude=90000.)
add(time=4.999,**{'context.resolved_geometry.reference_length_m':0.})
add(time=4.999,**{'context.basic_drag_model_profile':None})
add(time=4.999,**{'context.motor_property_model_profile.mass_model':MotorMassEvolutionModel.EXPLICIT_CURVE})
add(**{'context.basic_drag_model_profile':None,'context.translational_dynamics_model_profile.thrust_direction_model':'other'})


def tolerances(op,key,v):
    # Reuse the already verified quantity-specific contracts, never a global tolerance.
    if op!='evaluate' or key.endswith('.size') or '.modelProfile.' in key or key.startswith('contract.'): return 0.,0.
    if key.startswith('result.atmosphere.'): return 2e-14,0.  # J-003 source atmosphere assertions
    if key.startswith('result.airProperties.'): return 3e-15,0.
    if key.startswith('result.flightConditions.'): return 1e-14,0.
    if key.startswith('result.motorThrustState.'):
        t=v['time']-v['ignition']
        if key.endswith('.thrustN'):
            return (0. if t in {s.time_s for s in AEROTECH_F50_4T.thrust_curve} else 4e-15),0.
        return 3e-15,0.
    if key.startswith(('result.motorMassProperties.','result.rocketMassProperties.')): return 3e-15,0.
    if key.startswith('result.basicDrag.'): return 3e-15,0.
    if key.startswith('result.dynamics.') and not '.positionDerivativeWorldMS.' in key: return 0.,2e-15
    return 0.,0.


def main():
    p=argparse.ArgumentParser(); p.add_argument('--java',required=True); p.add_argument('--build-dir',type=Path,required=True)
    args=p.parse_args(); nonempty(CASES); count_cases('PHYSICS',[effective(case) for case in CASES])
    payload='\n'.join('\t'.join([op,*(key+'='+encode(value) for key,value in changes.items())]) for op,changes in CASES)+'\n'
    cp=os.pathsep.join(str(args.build_dir/path) for path in ('classes/java/main','classes/java/test'))
    process=subprocess.run([args.java,'-cp',cp,'org.roketsim.core.PhysicsEvaluatorParityProbe'],input=payload,text=True,capture_output=True,check=True)
    lines=process.stdout.splitlines()
    if len(lines)!=len(CASES): raise AssertionError(('Truncated/extra result set',len(lines),len(CASES),process.stderr))
    successes=errors=quantities=exact=shape=0; max_absolute=max_relative=0.; evidence=[]; nonexact=[]
    for case,line in zip(CASES,lines):
        op,v=effective(case)
        try: expected=python_call(op,v); status='OK'
        except ValueError as error:
            status='ERROR'; expected={'error.category':type(error).__name__,'error.code':getattr(error,'error_code',None),'error.field':getattr(error,'field_name',None)}
            expected.update(flatten(getattr(error,'value',None),'error.value'))
            expected['error.context']=str(error).split()[0] if type(error) is ValueError else None
            if isinstance(error,AtmosphereDomainError):
                expected.update({'error.requestedGeopotentialHeightM':error.requested_geopotential_height_m,
                    'error.minimumGeopotentialHeightM':error.minimum_geopotential_height_m,'error.maximumGeopotentialHeightM':error.maximum_geopotential_height_m})
        actual=None; tokens=line.split('\t')
        try:
            if tokens[0]!=status: raise AssertionError(('Wrong result/error category',status,tokens[0]))
            actual={key:decoded(value) for key,value in keyed_output(tokens[1:],expected).items()}
            for key,want in expected.items():
                got=actual[key]
                if isinstance(want,bool): match=type(got) is bool and want is got
                elif isinstance(want,(int,float)):
                    rtol,atol=tolerances(op,key,v) if status=='OK' else (0.,0.)
                    match=want==got or math.isnan(want) and math.isnan(got) or math.isfinite(want) and math.isfinite(got) and abs(got-want)<=atol+rtol*abs(want)
                    if status=='OK':
                        quantities+=1; exact+=int(want==got); shape+=int(key.endswith('.size') or key=='contract.contextFieldCount')
                        delta=abs(want-got); max_absolute=max(max_absolute,delta)
                        if want: max_relative=max(max_relative,delta/abs(want))
                        if want!=got: nonexact.append(dict(input=repr((op,v)),field=key,python=want,java=got,absolute=delta,rtol=rtol,atol=atol))
                else: match=want==got
                if not match: raise AssertionError((key,want,got))
        except (AssertionError,TypeError,ValueError) as discrepancy:
            print('PARITY_FINDING '+json.dumps(dict(input=repr((op,v)),python=repr(expected),raw_java=line,decoded_java=repr(actual),difference=repr(discrepancy),source_contract='NAT-015 ordering; quantity-specific J-003/005/006/007 assertion contracts'))); raise
        successes+=status=='OK'; errors+=status=='ERROR'
        if case in ANCHORS: evidence.append(dict(input=repr((op,v)),status=status,python=expected,java=actual))
    if successes+errors!=len(CASES): raise AssertionError('Unexecuted cases')
    print(f'PHYSICS_PARITY: {successes} success, {errors} expected errors; {quantities} numeric, {exact} exact; nonexact={quantities-exact}; max_absolute={max_absolute!r}; max_relative={max_relative!r}')
    print(f'PHYSICS_QUANTITIES: {quantities-shape} scalar/components; {shape} shape metadata')
    for row in evidence: print('PHYSICS_EVIDENCE '+json.dumps(row,allow_nan=False))
    for row in nonexact: print('PHYSICS_NONEXACT '+json.dumps(row,allow_nan=False))
    print(sys.version); print('NumPy',np.__version__,platform.platform())


if __name__=='__main__': main()
