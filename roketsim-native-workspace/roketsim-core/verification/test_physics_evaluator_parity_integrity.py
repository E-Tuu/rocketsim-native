"""J-009 isolated full-chain protocol fault injection, never production modifications."""
import os
from pathlib import Path
import subprocess
import sys
import pytest

HERE=Path(__file__).resolve().parent
JAVA=os.environ.get('AUD002_JAVA','java')
BUILD=os.environ['AUD002_BUILD_DIR']
CHILD=r'''
import base64,runpy,subprocess,sys
from pathlib import Path
runner,fault,java,build=sys.argv[1:]
sys.path.insert(0,str(Path(runner).parent))
original=subprocess.run
def inject(*args,**kwargs):
    if 'org.roketsim.core.PhysicsEvaluatorParityProbe' not in args[0]: return original(*args,**kwargs)
    if fault=='process': return original([java,'--intentionally-invalid-option'],**kwargs)
    result=original(*args,**kwargs); lines=result.stdout.splitlines()
    targets={'atmosphere':'result.atmosphere.pressurePa','altitude':'result.environmentAltitudeM',
        'motor-time':'result.motorTimeS','thrust':'result.motorThrustState.thrustN',
        'motor-mass':'result.motorMassProperties.massKg','rocket-mass':'result.rocketMassProperties.totalMassKg',
        'q':'result.flightConditions.dynamicPressurePa','cd0':'result.basicDrag.totalCd0',
        'derivative':'result.dynamics.derivative.velocityDerivativeWorldMS2.2'}
    if fault in targets:
        target=targets[fault]
        for index,line in enumerate(lines):
            fields=line.split('\t')
            if any(item.startswith(target+'=') for item in fields):
                fields=[target+'=999.0' if item.startswith(target+'=') else item for item in fields]
                lines[index]='\t'.join(fields); break
        else: raise AssertionError('Fault injection target missing')
    elif fault in {'category','error-code','error-field'}:
        for index,line in enumerate(lines):
            if line.startswith('ERROR\t'):
                target,wrong={'category':('error.category','WrongError'),'error-code':('error.code','WrongCode'),'error-field':('error.field','WrongField')}[fault]
                fields=[target+'=s:'+base64.b64encode(wrong.encode()).decode() if item.startswith(target+'=') else item for item in line.split('\t')]
                lines[index]='\t'.join(fields); break
        else: raise AssertionError('Fault injection error target missing')
    elif fault in {'missing','duplicate','unexpected'}:
        fields=lines[0].split('\t')
        if fault=='missing': fields=fields[:-1]
        elif fault=='duplicate': fields.append(fields[1])
        else: fields.append('unexpected=1')
        lines[0]='\t'.join(fields)
    elif fault=='truncated': lines=lines[:-1]
    elif fault=='empty-output': lines=[]
    result.stdout='\n'.join(lines)+'\n'; return result
subprocess.run=inject
sys.argv=[runner,'--java',java,'--build-dir',build]
if fault=='empty-cases':
    module=runpy.run_path(runner,run_name='integrity_probe')
    module['main'].__globals__['CASES']=[]; module['main']()
else: runpy.run_path(runner,run_name='__main__')
'''


@pytest.mark.parametrize('fault,diagnostic',[
    ('atmosphere','result.atmosphere.pressurePa'),('altitude','result.environmentAltitudeM'),
    ('motor-time','result.motorTimeS'),('thrust','result.motorThrustState.thrustN'),
    ('motor-mass','result.motorMassProperties.massKg'),('rocket-mass','result.rocketMassProperties.totalMassKg'),
    ('q','result.flightConditions.dynamicPressurePa'),('cd0','result.basicDrag.totalCd0'),
    ('derivative','result.dynamics.derivative.velocityDerivativeWorldMS2.2'),
    ('category','WrongError'),('error-code','WrongCode'),('error-field','WrongField'),
    ('missing','Missing/unexpected'),('duplicate','Duplicate result field'),('unexpected','Missing/unexpected'),
    ('truncated','Truncated/extra'),('empty-output','Truncated/extra'),
    ('empty-cases','Empty/unexecuted'),('process','CalledProcessError')])
def test_injected_fault_is_rejected(fault,diagnostic):
    p=subprocess.run([sys.executable,'-B','-c',CHILD,str(HERE/'physics_evaluator_parity.py'),fault,JAVA,BUILD],text=True,capture_output=True)
    print(f'PHYSICS_FAULT {fault}: exit={p.returncode}')
    assert p.returncode!=0,p.stdout
    assert diagnostic in p.stderr+p.stdout,p.stderr


def test_optimization_is_rejected():
    p=subprocess.run([sys.executable,'-B','-O',str(HERE/'physics_evaluator_parity.py'),'--java',JAVA,'--build-dir',BUILD],text=True,capture_output=True)
    print(f'PHYSICS_OPTIMIZED: exit={p.returncode}')
    assert p.returncode!=0
    assert 'optimized Python is forbidden' in p.stderr
