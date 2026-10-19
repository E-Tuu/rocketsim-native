"""J-007: actual accepted NAT-013/014 calls, complete results, strict output protocol."""
import argparse
from dataclasses import fields,is_dataclass
from enum import Enum
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
from parity_contract import nonempty,keyed_output,count_cases
from propulsion_parity import camel,encode as scalar_encode,decoded
import numpy as np
from roketsim_native.dynamics.initial_state import TranslationalState3DOF,LaunchConditions3DOF,InitialStateBuilder
from roketsim_native.dynamics.translational import (
    ThrustDirectionModel as TM,AerodynamicForceModel as AM,TranslationalDynamicsModelProfile,
    TranslationalDynamicsInputs,TranslationalForces3DOF,TranslationalStateDerivative3DOF,
    TranslationalDynamicsResult,TranslationalDynamicsEvaluator)

DEFAULTS=dict(position=(1.,2.,3.),velocity=(0.,0.,0.),launch_position=(0.,0.,0.),
    launch_velocity=(0.,0.,0.),direction=(0.,0.,1.),mass=2.,thrust=30.,relative=(0.,0.,0.),
    q=0.,area=.01,cd0=.5,gravity=(0.,0.,-10.),thrust_model=TM.FIXED_LAUNCH_DIRECTION,
    aero_model=AM.BASELINE_CD0_DRAG_ONLY,thrust_force=(1.,2.,3.),drag_force=(4.,5.,6.),
    gravity_force=(7.,8.,9.),position_rate=(1.,2.,3.),velocity_rate=(4.,5.,6.))
USED={
    'state':('position','velocity'), 'launch':('launch_position','launch_velocity','direction'),
    'build':('launch_position','launch_velocity','direction'),
    'inputs':('mass','thrust','relative','q','area','cd0','gravity'),
    'forces':('thrust_force','drag_force','gravity_force'),
    'derivative':('position_rate','velocity_rate'), 'profile':('thrust_model','aero_model')}
USED['evaluate']=USED['state']+USED['launch']+USED['inputs']+USED['profile']


def effective(case):
    op,changes=case
    values={**DEFAULTS,**changes}
    return op,{key:values[key] for key in USED[op]}


def encode(value):
    if isinstance(value,(tuple,list)):
        if value and isinstance(value[0],(tuple,list)):
            if len(value)!=1: raise AssertionError('Only the deliberate one-row shape fixture is encoded')
            return 'matrix:'+','.join(str(float(x)) for x in value[0])
        return 'v:'+','.join('NaN' if math.isnan(x) else 'Infinity' if x==math.inf else '-Infinity' if x==-math.inf else repr(float(x)) for x in value)
    return scalar_encode(value)


def flatten(value,prefix='result'):
    result={}
    if is_dataclass(value):
        for field in fields(value): result.update(flatten(getattr(value,field.name),prefix+'.'+camel(field.name)))
    elif isinstance(value,(np.ndarray,tuple,list)):
        result[prefix+'.size']=len(value)
        for i,item in enumerate(value): result.update(flatten(item,prefix+'.'+str(i)))
    else: result[prefix]=value.name if isinstance(value,Enum) else value
    return result


def python_call(op,v):
    def state(): return TranslationalState3DOF(v['position'],v['velocity'])
    def launch(): return LaunchConditions3DOF(v['launch_position'],v['launch_velocity'],v['direction'])
    def inputs(): return TranslationalDynamicsInputs(v['mass'],v['thrust'],v['relative'],v['q'],v['area'],v['cd0'],v['gravity'])
    if op=='state': return state()
    if op=='launch': return launch()
    if op=='build': return InitialStateBuilder().build(launch_conditions=launch())
    if op=='inputs': return inputs()
    if op=='forces': return TranslationalForces3DOF(v['thrust_force'],v['drag_force'],v['gravity_force'])
    if op=='derivative': return TranslationalStateDerivative3DOF(v['position_rate'],v['velocity_rate'])
    profile=TranslationalDynamicsModelProfile(v['thrust_model'],v['aero_model'])
    if op=='profile': return profile
    if op!='evaluate': raise AssertionError(op)
    result=TranslationalDynamicsEvaluator().evaluate(state=state(),launch_conditions=launch(),inputs=inputs(),model_profile=profile)
    if result.model_profile is not profile: raise AssertionError('Python profile identity changed')
    return result


def derived(result,prefix='result'):
    output={}
    if isinstance(result,TranslationalForces3DOF): output.update(flatten(result.net_force_world_N,prefix+'.netForceWorldN'))
    if isinstance(result,TranslationalDynamicsResult):
        output.update(derived(result.forces,prefix+'.forces'))
        output.update(flatten(result.acceleration_world_m_s2,prefix+'.accelerationWorldMS2'))
        output['contract.accelerationViewIdentity']=result.acceleration_world_m_s2 is result.derivative.velocity_derivative_world_m_s2
        output['contract.profileIdentity']=True
    return output


CASES=[]
def add(op,**changes): CASES.append((op,changes))
for op in USED: add(op)
for op in ('state','launch','build'):
    if op=='state': names=('position','velocity')
    else: names=('launch_position','launch_velocity')
    add(op,**{names[0]:(125.,-40.,12.5),names[1]:(3.,-2.,7.)})
directions=[(0.,0.,1.),(.6,.8,0.),(.6,0.,.8),(1/3,2/3,2/3),tuple(1/math.sqrt(3) for _ in range(3)),
    (0.,0.,0.),(0.,0.,1+.5e-8),(0.,0.,1+1e-8),(0.,0.,math.nextafter(1+1e-8,math.inf)),
    (0.,0.,1-1e-8),(0.,0.,math.nextafter(1-1e-8,math.inf)),(0.,0.,5.),(1e-200,0.,0.),(1e308,0.,0.)]
for op in ('launch','build','evaluate'):
    for direction in directions: add(op,direction=direction)
# Four frozen NAT-014 fixtures: actual production functions produce the expectations.
add('evaluate',velocity=(0.,0.,20.),relative=(0.,0.,20.),q=100.)
add('evaluate',velocity=(0.,0.,-20.),relative=(0.,0.,-20.),q=100.,thrust=0.)
add('evaluate',velocity=(3.,0.,4.),direction=(.6,0.,.8),mass=1.5,thrust=10.,relative=(3.,0.,4.),q=50.,area=.2,cd0=.4,gravity=(0.,0.,-9.8))
for flow in ((0.,0.,0.),(1.,0.,0.),(0.,0.,20.),(0.,0.,-20.),(3.,0.,4.),(1.,2.,3.),(-12.,3.,-4.),(.1,.2,.3),(3.1,4.2,5.3),(1e-160,0.,0.),(1e-200,0.,0.),(1e308,0.,0.)):
    for q in (0.,50.,100.,10000.):
        add('evaluate',relative=flow,q=q,velocity=(-7.,11.,2.))
for field in ('mass','thrust','q','area','cd0'):
    for value in (0.,-0.,-1.,math.nan,math.inf,-math.inf,np.nextafter(0.,1.)):
        add('inputs',**{field:value})
        add('evaluate',**{field:value})
for field in ('thrust_model','aero_model'):
    choice=DEFAULTS[field]
    for value in (choice,choice.value,choice.value.upper(),' '+choice.value,'other',None,False,1.,TM.FIXED_LAUNCH_DIRECTION if field=='aero_model' else AM.BASELINE_CD0_DRAG_ONLY):
        add('evaluate',**{field:value})
        add('profile',**{field:value})
for op,names in USED.items():
    vector_fields=[x for x in names if isinstance(DEFAULTS[x],tuple)]
    for field in vector_fields:
        for invalid in ((),(1.,2.),(1.,2.,3.,4.),((1.,2.,3.),),None,1.,(math.nan,0.,0.),(math.inf,0.,0.),(-math.inf,0.,0.)):
            add(op,**{field:invalid})
add('evaluate',mass=np.nextafter(0.,1.),thrust=1.,gravity=(0.,0.,0.))
add('evaluate',thrust=1e308,direction=(1+1e-8,0.,0.))
add('evaluate',mass=1e308,gravity=(0.,0.,-10.))
add('evaluate',relative=(1.,0.,0.),q=1e308,area=1e308)
add('forces',thrust_force=(1e308,0.,0.),drag_force=(1e308,0.,0.))
add('inputs',mass=-1.,relative=(math.nan,0.,0.))
add('inputs',thrust=-1.,relative=(math.nan,0.,0.))
add('evaluate',thrust_model='fixed_launch_direction',aero_model='baseline_cd0_drag_only')
add('evaluate',gravity=(2.,-3.,4.),relative=(3.,4.,12.),q=100.)


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--java',required=True); parser.add_argument('--build-dir',type=Path,required=True)
    args=parser.parse_args(); nonempty(CASES); count_cases('DYNAMICS',[effective(case) for case in CASES])
    payload='\n'.join('\t'.join([op,*(key+'='+encode(value) for key,value in changes.items())]) for op,changes in CASES)+'\n'
    cp=os.pathsep.join(str(args.build_dir/path) for path in ('classes/java/main','classes/java/test'))
    process=subprocess.run([args.java,'-cp',cp,'org.roketsim.core.DynamicsParityProbe'],input=payload,text=True,capture_output=True,check=True)
    lines=process.stdout.splitlines()
    if len(lines)!=len(CASES): raise AssertionError(('Truncated/extra result set',len(lines),len(CASES),process.stderr))
    successes=errors=quantities=exact=shape_quantities=0; max_absolute=0.; examples=[]
    for case,line in zip(CASES,lines):
        op,values=effective(case)
        try:
            result=python_call(op,values); expected=flatten(result); expected.update(derived(result)); status='OK'
        except ValueError as error:
            status='ERROR'; expected={'error.category':type(error).__name__,'error.code':getattr(error,'error_code',None),'error.field':getattr(error,'field_name',None)}
            expected.update(flatten(getattr(error,'value',None),'error.value'))
            expected['error.context']=str(error).split()[0] if type(error) is ValueError else None
        tokens=line.split('\t'); actual=None
        try:
            if tokens[0]!=status: raise AssertionError(('Wrong result/error category',status,tokens[0]))
            actual={key:decoded(value) for key,value in keyed_output(tokens[1:],expected).items()}
            for key,want in expected.items():
                got=actual[key]
                if isinstance(want,bool):
                    if type(got) is not bool or got is not want: raise AssertionError((key,want,got))
                elif isinstance(want,(int,float)):
                    if status=='ERROR': match=want==got or math.isnan(want) and math.isnan(got)
                    else:
                        # NAT-014 assert_vector: rtol=0, atol=2e-15. Non-calculated fields exact.
                        computed=op=='evaluate' and (key.startswith('result.forces.') or key.startswith('result.derivative.velocityDerivativeWorldMS2.') or key.startswith('result.accelerationWorldMS2.'))
                        tolerance=2e-15 if computed and not key.endswith('.size') else 0.
                        match=math.isfinite(got) and abs(got-want)<=tolerance
                        quantities+=1; exact+=int(want==got); max_absolute=max(max_absolute,abs(got-want))
                        shape_quantities+=int(key.endswith('.size'))
                    if not match: raise AssertionError((key,want,got))
                elif want!=got: raise AssertionError((key,want,got))
        except (AssertionError,TypeError,ValueError) as discrepancy:
            finding=dict(case=repr(case),effective_input=repr((op,values)),python=repr(expected),raw_java=line,decoded_java=repr(actual),tolerance='NAT-014 rtol=0 atol=2e-15 for evaluated results; other outputs/errors exact',discrepancy=repr(discrepancy))
            print('PARITY_FINDING '+json.dumps(finding)); raise
        if status=='OK' and op=='evaluate' and len(examples)<5: examples.append(dict(input=repr((op,values)),python=expected,java=actual))
        successes+=status=='OK'; errors+=status=='ERROR'
    if successes+errors!=len(CASES): raise AssertionError('Unexecuted cases')
    print(f'DYNAMICS_PARITY: {successes} success, {errors} expected errors; {quantities} numeric, {exact} exact; nonexact={quantities-exact}; max_absolute={max_absolute!r}')
    print(f'DYNAMICS_QUANTITIES: {quantities-shape_quantities} physical scalar/components; {shape_quantities} exact shape metadata')
    for example in examples: print('DYNAMICS_EVIDENCE '+json.dumps(example,allow_nan=False))
    print(sys.version); print('NumPy',np.__version__,platform.platform())


if __name__=='__main__': main()
