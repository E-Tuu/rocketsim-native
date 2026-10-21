"""J-011 corrupts actual test transport only; expected JSONL is produced by the real Python engine."""
import os
from pathlib import Path
import subprocess
import sys
import pytest

HERE=Path(__file__).resolve().parent
JAVA=os.environ.get('AUD002_JAVA','java')
BUILD=os.environ['AUD002_BUILD_DIR']


@pytest.fixture(scope='module')
def verified_artifacts(tmp_path_factory):
    output=tmp_path_factory.mktemp('j011-native-integrity')
    p=subprocess.run([sys.executable,'-B',str(HERE/'native_trajectory_parity.py'),'--java',JAVA,
        '--build-dir',BUILD,'--artifacts-dir',str(output)],capture_output=True,text=True)
    assert p.returncode==0,p.stdout+p.stderr
    assert 'NATIVE_TRAJECTORY_PARITY: PASS' in p.stdout
    return output


CHILD=r'''
import base64,json,runpy,subprocess,sys
from pathlib import Path
runner,root,fault,java,build=sys.argv[1:];sys.path.insert(0,str(Path(runner).parent))
module=runpy.run_path(runner,run_name='integrity_probe');root=Path(root)
if fault in {'process','empty-cases'}:
    if fault=='empty-cases':module['main'].__globals__['CASES']=[]
    else:
        original=subprocess.run
        def fail(*args,**kwargs):
            if 'org.roketsim.core.NativeTrajectoryParityProbe' in args[0]:
                return original([java,'--intentionally-invalid-option'],**kwargs)
            return original(*args,**kwargs)
        subprocess.run=fail
    sys.argv=[runner,'--java',java,'--build-dir',build,'--artifacts-dir',str(root/'fault')]
    module['main']()
    raise AssertionError('INJECTION_SETUP_FAILURE: failing invocation unexpectedly returned')
lines=(root/'primary-java.tsv').read_text(encoding='utf-8').splitlines()
samples=[i for i,line in enumerate(lines) if line.startswith('SAMPLE\t')]
events=[i for i,line in enumerate(lines) if line.startswith('EVENT\t')]
if not samples or len(events)!=3:raise AssertionError('INJECTION_SETUP_FAILURE: unverified fixture')
middle=samples[len(samples)//2]
def replace(index,key,value):
    fields=lines[index].split('\t')
    if sum(x.startswith(key+'=') for x in fields)!=1:raise AssertionError('INJECTION_SETUP_FAILURE: '+key)
    lines[index]='\t'.join(key+'='+value if x.startswith(key+'=') else x for x in fields)
if fault=='missing-sample':del lines[middle]
elif fault=='duplicate-sample':lines.insert(middle,lines[middle])
elif fault=='reordered-sample':lines[middle],lines[middle+1]=lines[middle+1],lines[middle]
elif fault in {'first-sample','middle-sample','last-sample'}:
    index={'first-sample':samples[0],'middle-sample':middle,'last-sample':samples[-1]}[fault]
    replace(index,'sample.point.state.positionWorldM.2','9999999.0')
elif fault in {'time','world-z','velocity','motor-mass','thrust','cd0','acceleration'}:
    key={'time':'sample.point.timeS','world-z':'sample.point.state.positionWorldM.2',
        'velocity':'sample.point.state.velocityWorldMS.2','motor-mass':'sample.physics.motorMassProperties.massKg',
        'thrust':'sample.physics.motorThrustState.thrustN','cd0':'sample.physics.basicDrag.totalCd0',
        'acceleration':'sample.physics.dynamics.accelerationWorldMS2.2'}[fault]
    replace(middle,key,'9999999.0')
elif fault in {'burnout','apogee','ground'}:replace(events[{'burnout':0,'apogee':1,'ground':2}[fault]],'event.timeS','9999999.0')
elif fault=='event-type':replace(events[0],'event.eventType','s:'+base64.b64encode(b'INTENTIONALLY_WRONG').decode())
elif fault=='event-order':lines[events[0]],lines[events[1]]=lines[events[1]],lines[events[0]]
elif fault=='terminal-alpha':replace(events[2],'event.interpolationFraction','9999999.0')
elif fault=='sample-count':replace(0,'sampleCount','9999999.0')
elif fault=='step-count':replace(len(lines)-2,'stepsPerformed','9999999.0')
elif fault=='reason':replace(len(lines)-2,'terminationReason','s:'+base64.b64encode(b'INTENTIONALLY_WRONG').decode())
elif fault in {'missing-field','duplicate-field','extra-field'}:
    index=samples[0];fields=lines[index].split('\t')
    if fault=='missing-field':fields=fields[:-1]
    elif fault=='duplicate-field':fields.append(fields[1])
    else:fields.append('unexpected=1')
    lines[index]='\t'.join(fields)
elif fault=='truncated':lines=lines[:-1]
elif fault=='extra-output':lines.append('END')
elif fault=='empty-output':lines=[]
elif fault!='empty-collection':raise AssertionError('INJECTION_SETUP_FAILURE: unknown fault')
def expected():
    if fault=='empty-collection':return
    with (root/'primary-python.jsonl').open(encoding='utf-8') as f:
        for line in f:
            record=json.loads(line);yield record['kind'],record['values']
module['compare_records'](expected(),lines,'fault-'+fault,root/'fault',{'fixture':'fresh real Python NAT-022 primary'})
raise AssertionError('INJECTION_SETUP_FAILURE: corruption was not rejected')
'''


@pytest.mark.parametrize('fault,diagnostic',[
    ('missing-sample','index'),('duplicate-sample','index'),('reordered-sample','index'),
    ('first-sample','positionWorldM.2'),('middle-sample','positionWorldM.2'),('last-sample','positionWorldM.2'),
    ('time','timeS'),('world-z','positionWorldM.2'),('velocity','velocityWorldMS.2'),
    ('motor-mass','motorMassProperties.massKg'),('thrust','motorThrustState.thrustN'),('cd0','basicDrag.totalCd0'),
    ('acceleration','accelerationWorldMS2.2'),('burnout','event.timeS'),('apogee','event.timeS'),('ground','event.timeS'),
    ('event-type','INTENTIONALLY_WRONG'),('event-order','index'),('terminal-alpha','interpolationFraction'),
    ('sample-count','sampleCount'),('step-count','stepsPerformed'),('reason','INTENTIONALLY_WRONG'),
    ('missing-field','Missing/unexpected'),('duplicate-field','Duplicate result field'),('extra-field','Missing/unexpected'),
    ('truncated','Truncated output'),('extra-output','Extra output'),('empty-output','Truncated output'),
    ('empty-collection','Empty/unexecuted'),('empty-cases','Empty/unexecuted'),('process','CalledProcessError')])
def test_actual_transport_corruption_is_rejected(verified_artifacts,fault,diagnostic):
    p=subprocess.run([sys.executable,'-B','-c',CHILD,str(HERE/'native_trajectory_parity.py'),str(verified_artifacts),fault,JAVA,BUILD],capture_output=True,text=True)
    print(f'TRAJECTORY_FAULT {fault}: exit={p.returncode}')
    assert p.returncode!=0,p.stdout
    assert diagnostic in p.stdout+p.stderr,p.stderr
    assert 'INJECTION_SETUP_FAILURE' not in p.stderr+p.stdout


def test_optimized_python_is_rejected(tmp_path):
    p=subprocess.run([sys.executable,'-B','-O',str(HERE/'native_trajectory_parity.py'),'--java',JAVA,
        '--build-dir',BUILD,'--artifacts-dir',str(tmp_path)],capture_output=True,text=True)
    assert p.returncode!=0
    assert 'optimized Python is forbidden' in p.stderr
