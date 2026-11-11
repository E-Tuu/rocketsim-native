"""J-010 isolated protocol corruption checks; production calculations stay unchanged."""
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
    if 'org.roketsim.core.LifecycleParityProbe' not in args[0]:return original(*args,**kwargs)
    if fault=='process':return original([java,'--intentionally-invalid-option'],**kwargs)
    result=original(*args,**kwargs);lines=result.stdout.splitlines()
    targets={'event-time':'events.0.timeS','event-alpha':'events.0.interpolationFraction',
        'event-state':'events.0.estimatedState.positionWorldM.0','event-count':'events.size',
        'sample-count':'execution.recordedData.samples.size','sample-time':'execution.recordedData.samples.1.point.timeS',
        'steps':'execution.stepsPerformed','duration':'simulationDurationS',
        'endpoint-physics':'execution.recordedData.samples.1.physics.atmosphere.pressurePa',
        'event-type':'events.0.eventType','reason':'execution.terminationReason',
        'category':'error.category','error-code':'error.code','error-field':'error.field'}
    if fault in targets:
        target=targets[fault]
        wrong='s:'+base64.b64encode(b'INTENTIONALLY_WRONG').decode() if fault in {'event-type','reason','category','error-code','error-field'} else '999.0'
        for i,line in enumerate(lines):
            fields=line.split('\t')
            if any(x.startswith(target+'=') for x in fields):
                lines[i]='\t'.join(target+'='+wrong if x.startswith(target+'=') else x for x in fields);break
        else:raise AssertionError('Injection target missing: '+target)
    elif fault=='event-order':
        for i,line in enumerate(lines):
            if 'events.2.eventType=' in line:
                fields=line.split('\t');changed=[]
                for x in fields:
                    if x.startswith('events.0.'):x='events.1.'+x[len('events.0.'):]
                    elif x.startswith('events.1.'):x='events.0.'+x[len('events.1.'):]
                    changed.append(x)
                lines[i]='\t'.join(changed);break
        else:raise AssertionError('Injection order target missing')
    elif fault in {'missing','duplicate','unexpected'}:
        fields=lines[0].split('\t')
        if fault=='missing':fields=fields[:-1]
        elif fault=='duplicate':fields.append(fields[1])
        else:fields.append('unexpected=1')
        lines[0]='\t'.join(fields)
    elif fault=='truncated':lines=lines[:-1]
    elif fault=='empty-output':lines=[]
    result.stdout='\n'.join(lines)+'\n';return result
subprocess.run=inject
sys.argv=[runner,'--java',java,'--build-dir',build]
if fault=='empty-cases':
    module=runpy.run_path(runner,run_name='integrity_probe')
    module['main'].__globals__['CASES']=[];module['main']()
else:runpy.run_path(runner,run_name='__main__')
'''


@pytest.mark.parametrize('fault,diagnostic',[
    ('event-time','events.0.timeS'),('event-alpha','events.0.interpolationFraction'),
    ('event-state','events.0.estimatedState.positionWorldM.0'),('event-count','events.size'),
    ('sample-count','execution.recordedData.samples.size'),('sample-time','execution.recordedData.samples.1.point.timeS'),
    ('steps','execution.stepsPerformed'),('duration','simulationDurationS'),
    ('endpoint-physics','execution.recordedData.samples.1.physics.atmosphere.pressurePa'),
    ('event-type','INTENTIONALLY_WRONG'),('reason','INTENTIONALLY_WRONG'),
    ('category','INTENTIONALLY_WRONG'),('error-code','INTENTIONALLY_WRONG'),('error-field','INTENTIONALLY_WRONG'),
    ('event-order','eventType'),('missing','Missing/unexpected'),('duplicate','Duplicate result field'),
    ('unexpected','Missing/unexpected'),('truncated','Truncated/extra'),('empty-output','Truncated/extra'),
    ('empty-cases','Empty/unexecuted'),('process','CalledProcessError')])
def test_fault_is_rejected(fault,diagnostic):
    p=subprocess.run([sys.executable,'-B','-c',CHILD,str(HERE/'lifecycle_parity.py'),fault,JAVA,BUILD],text=True,capture_output=True)
    print(f'LIFECYCLE_FAULT {fault}: exit={p.returncode}')
    assert p.returncode!=0,p.stdout
    assert diagnostic in p.stdout+p.stderr,p.stderr
    assert 'Injection target missing' not in p.stderr


def test_optimization_is_rejected():
    p=subprocess.run([sys.executable,'-B','-O',str(HERE/'lifecycle_parity.py'),'--java',JAVA,'--build-dir',BUILD],text=True,capture_output=True)
    assert p.returncode!=0
    assert 'optimized Python is forbidden' in p.stderr
