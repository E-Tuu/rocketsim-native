"""J-008: real Python/Java NAT-016/017 calls and complete callback-stage traces."""
import argparse
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
import numpy as np
from parity_contract import nonempty, keyed_output, count_cases
from dynamics_parity import encode, flatten
from propulsion_parity import decoded
from roketsim_native.dynamics.initial_state import TranslationalState3DOF
from roketsim_native.dynamics.translational import TranslationalStateDerivative3DOF
from roketsim_native.numerics.fixed_step import FixedStepConfig, IntegrationPoint3DOF, TranslationalStateAlgebra3DOF
from roketsim_native.numerics.rk4 import ClassicalRK4Integrator3DOF

DEFAULTS=dict(time=2.,h=.2,scale=.5,position=(1.,2.,3.),velocity=(4.,5.,6.),
    position_rate=(4.,5.,6.),velocity_rate=(1.,2.,3.),callback='constant',fail_stage=0.)
USED={'config':('h',),'point':('time','position','velocity'),
    'algebra':('position','velocity','position_rate','velocity_rate','scale'),
    'step':('time','position','velocity','h','callback','position_rate','velocity_rate','fail_stage')}


def effective(case):
    op,changes=case
    v={**DEFAULTS,**changes}
    return op,{key:v[key] for key in USED[op]}


class SentinelDerivativeError(RuntimeError):
    pass


def python_call(op,v,trace):
    def state(): return TranslationalState3DOF(v['position'],v['velocity'])
    if op=='config': return FixedStepConfig(v['h'])
    if op=='point':
        original=state(); result=IntegrationPoint3DOF(v['time'],original)
        if result.state is not original: raise AssertionError('State identity changed')
        return result
    if op=='algebra':
        return TranslationalStateAlgebra3DOF().add_scaled_derivative(state=state(),
            derivative=TranslationalStateDerivative3DOF(v['position_rate'],v['velocity_rate']),scale_s=v['scale'])
    if op!='step': raise AssertionError(op)
    point=IntegrationPoint3DOF(v['time'],state()); config=FixedStepConfig(v['h'])
    def callback(*,time_s,state):
        trace.append(IntegrationPoint3DOF(time_s,state))
        if len(trace)==v['fail_stage']:
            if v['callback']=='null': return None
            raise SentinelDerivativeError()
        if v['callback']=='oscillator': return TranslationalStateDerivative3DOF(state.velocity_world_m_s,-state.position_world_m)
        if v['callback']=='acceleration': return TranslationalStateDerivative3DOF(state.velocity_world_m_s,v['velocity_rate'])
        if v['callback']=='time':
            return TranslationalStateDerivative3DOF(tuple(time_s*(i+1) for i in range(3)),tuple(-time_s*(i+1) for i in range(3)))
        if v['callback']=='sequence': return TranslationalStateDerivative3DOF((6e307,)*3 if len(trace)==4 else (0.,)*3,(0.,)*3)
        if v['callback'] not in ('constant','sentinel','null'): raise AssertionError(v['callback'])
        return TranslationalStateDerivative3DOF(v['position_rate'],v['velocity_rate'])
    return ClassicalRK4Integrator3DOF().step(point=point,config=config,derivative_function=callback)


CASES=[]
def add(op,**changes): CASES.append((op,changes))
for op in USED: add(op)
for h in (np.nextafter(0.,1.),.001,.01,1.,1e308,sys.float_info.max,0.,-0.,-1.,-1e-300,math.nan,math.inf,-math.inf):
    add('config',h=h)
for time in (-3.25,0.,-0.,1e308,-1e308,math.nan,math.inf,-math.inf): add('point',time=time)
for scale in (.5,0.,-.5,1e-300,1e308,math.nan,math.inf,-math.inf): add('algebra',scale=scale)
# Frozen accepted fixtures; expectations always come from production Python calls.
add('step',time=2.,h=.2,position=(1.,0.,0.),velocity=(5.,0.,0.),position_rate=(2.,0.,0.),velocity_rate=(3.,0.,0.))
add('step',time=0.,h=.5,position=(0.,0.,100.),velocity=(0.,0.,20.),callback='acceleration',velocity_rate=(0.,0.,-10.))
add('step',time=0.,h=.1,position=(1.,0.,0.),velocity=(0.,0.,0.),callback='oscillator')
add('step',time=2.,h=.4,position=(1.,0.,0.),velocity=(.5,0.,0.),callback='oscillator')
FROZEN_CASES=CASES[-4:]
for callback in ('constant','acceleration','oscillator','time'):
    for time in (-3.25,0.,2.,.95):
        for h in (np.nextafter(0.,1.),.01,.1,.4): add('step',callback=callback,time=time,h=h)
for kind in ('sentinel','null'):
    for stage in range(1,5): add('step',callback=kind,fail_stage=float(stage))
for time,h in ((1.5e308,1e308),(1e308,1e308),(-1e308,1e308),(0.,sys.float_info.max)):
    add('step',time=time,h=h,position_rate=(0.,0.,0.),velocity_rate=(0.,0.,0.))
for h in (0.,-1.,math.nan,math.inf,-math.inf): add('step',h=h)
for time in (math.nan,math.inf,-math.inf): add('step',time=time)
for op in ('point','algebra','step'):
    for field in ('position','velocity'):
        for bad in ((),(1.,2.),(1.,2.,3.,4.),((1.,2.,3.),),(math.nan,0.,0.),(math.inf,0.,0.),(-math.inf,0.,0.)):
            add(op,**{field:bad})
for op in ('algebra','step'):
    for field in ('position_rate','velocity_rate'):
        for bad in ((1.,2.),(math.nan,0.,0.),(math.inf,0.,0.),(-math.inf,0.,0.)): add(op,**{field:bad})
add('step',position=(1e308,0.,0.),h=2.,position_rate=(1e308,0.,0.))
add('step',h=np.nextafter(0.,1.),position_rate=(1e308,0.,0.))
add('step',position=(1.7e308,0.,0.),h=10.,callback='sequence')
add('algebra',position=(1e308,0.,0.),position_rate=(1e308,0.,0.),scale=1.)


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--java',required=True); parser.add_argument('--build-dir',type=Path,required=True)
    args=parser.parse_args(); nonempty(CASES); count_cases('NUMERICS',[effective(case) for case in CASES])
    payload='\n'.join('\t'.join([op,*(key+'='+encode(value) for key,value in changes.items())]) for op,changes in CASES)+'\n'
    cp=os.pathsep.join(str(args.build_dir/path) for path in ('classes/java/main','classes/java/test'))
    process=subprocess.run([args.java,'-cp',cp,'org.roketsim.core.NumericsParityProbe'],input=payload,text=True,capture_output=True,check=True)
    lines=process.stdout.splitlines()
    if len(lines)!=len(CASES): raise AssertionError(('Truncated/extra result set',len(lines),len(CASES),process.stderr))
    successes=errors=quantities=exact=shape=0; max_absolute=max_relative=0.; examples=[]
    for case,line in zip(CASES,lines):
        op,v=effective(case); trace=[]
        try:
            result=python_call(op,v,trace); expected=flatten(result); status='OK'
            if op=='point': expected['contract.stateIdentity']=True
        except (ValueError,TypeError,SentinelDerivativeError) as error:
            status='ERROR'; expected={'error.category':type(error).__name__,'error.code':getattr(error,'error_code',None),'error.field':getattr(error,'field_name',None)}
            expected.update(flatten(getattr(error,'value',None),'error.value'))
            expected['error.context']=str(error).split()[0] if type(error) is ValueError else None
        expected['callbackCount']=len(trace); expected.update(flatten(trace,'trace'))
        tokens=line.split('\t'); actual=None
        try:
            if tokens[0]!=status: raise AssertionError(('Wrong result/error category',status,tokens[0]))
            actual={key:decoded(value) for key,value in keyed_output(tokens[1:],expected).items()}
            for key,want in expected.items():
                got=actual[key]
                if isinstance(want,bool): match=type(got) is bool and got is want
                elif isinstance(want,(int,float)):
                    # NAT-016 algebra is exact. NAT-017 stage/vector assertions use
                    # np.testing.assert_allclose defaults (rtol=1e-7, atol=0).
                    # Its frozen oscillator scalar assertions use pytest.approx defaults.
                    calculated=op=='step' and '.state.' in key and not key.endswith('.size')
                    rtol,atol=(1e-7,0.) if calculated else (0.,0.)
                    if case==FROZEN_CASES[2] and key in ('result.state.positionWorldM.0','result.state.velocityWorldMS.0'):
                        rtol,atol=1e-6,1e-12
                    if status=='ERROR' and not key.startswith('trace.'): rtol=atol=0.
                    match=(want==got or math.isnan(want) and math.isnan(got)
                        or math.isfinite(want) and math.isfinite(got) and abs(want-got)<=max(atol,rtol*abs(want)))
                    if status=='OK' or key.startswith('trace.') or key=='callbackCount':
                        quantities+=1; exact+=int(want==got); shape+=int(key.endswith('.size') or key=='callbackCount')
                        difference=abs(want-got); max_absolute=max(max_absolute,difference)
                        if want: max_relative=max(max_relative,difference/abs(want))
                else: match=want==got
                if not match: raise AssertionError((key,want,got))
        except (AssertionError,TypeError,ValueError) as discrepancy:
            finding=dict(case=repr(case),effective_input=repr((op,v)),python=repr(expected),raw_java=line,decoded_java=repr(actual),discrepancy=repr(discrepancy),policy='NAT-016 exact; NAT-017 state/stage allclose rtol=1e-7 atol=0; frozen oscillator scalars pytest.approx rel=1e-6 abs=1e-12; metadata/errors exact')
            print('PARITY_FINDING '+json.dumps(finding)); raise
        successes+=status=='OK'; errors+=status=='ERROR'
        if case in FROZEN_CASES: examples.append(dict(input=repr((op,v)),python=expected,java=actual))
    if successes+errors!=len(CASES): raise AssertionError('Unexecuted cases')
    print(f'NUMERICS_PARITY: {successes} success, {errors} expected errors; {quantities} numeric, {exact} exact; nonexact={quantities-exact}; max_absolute={max_absolute!r}; max_relative={max_relative!r}')
    print(f'NUMERICS_QUANTITIES: {quantities-shape} scalar/components; {shape} exact shape/callback-count metadata')
    for example in examples: print('NUMERICS_EVIDENCE '+json.dumps(example,allow_nan=False))
    print(sys.version); print('NumPy',np.__version__,platform.platform())


if __name__=='__main__': main()
