"""Intentional faults are confined to child-process probe output, never production."""

import os
from pathlib import Path
import subprocess
import sys

import pytest

HERE = Path(__file__).resolve().parent
JAVA = os.environ.get("AUD002_JAVA", "java")
BUILD = os.environ["AUD002_BUILD_DIR"]
RUNNERS = ("python_parity.py", "quaternion_parity.py", "environment_parity.py", "vehicle_parity.py")

CHILD = r'''
import runpy,subprocess,sys
from pathlib import Path
runner,fault,java,build=sys.argv[1:]
sys.path.insert(0,str(Path(runner).parent))
original_run=subprocess.run
calls=0
def corrupt(output):
    global calls
    calls+=1
    lines=output.splitlines()
    if fault=='numeric':
        lines[0]='999'
    elif fault=='vector-extra':
        if calls!=3: return output
        lines.append('999')
    elif fault=='truncated':
        lines=lines[:-1]
    elif fault=='empty-output':
        lines=[]
    elif fault=='quaternion-extra':
        if calls!=2: return output
        lines[0]+='\t999'
    elif fault=='quaternion-status':
        lines[0]='WRONG\t1'
    elif fault=='quaternion-error':
        if not lines[0].startswith('ERROR'): return output
        lines[0]+='\textra'
    elif fault=='category':
        for index,line in enumerate(lines):
            fields=line.split('\t')
            if fields[0]=='ERROR':
                fields[1]='WrongError'
                lines[index]='\t'.join(fields)
                break
    elif fault in {'missing','duplicate','unexpected','environment-missing'}:
        fields=lines[0].split('\t')
        if fault in {'missing','environment-missing'}: fields=fields[:-1]
        elif fault=='duplicate': fields.append(fields[1])
        else: fields.append('unexpected=123')
        lines[0]='\t'.join(fields)
    return '\n'.join(lines)+'\n'
def fake_run(*a,**kw):
    result=original_run(*a,**kw)
    if any(str(arg).startswith('org.roketsim.core.') for arg in a[0]):
        result.stdout=corrupt(result.stdout)
    return result
subprocess.run=fake_run
sys.argv=[runner,'--java',java,'--build-dir',build]
if runner.endswith('vehicle_parity.py'): sys.argv+=['--group','all']
runpy.run_path(runner,run_name='__main__')
'''
# The raw child code must contain normal Python escape sequences, not literal tabs.


@pytest.mark.parametrize("runner,fault,diagnostic", [
    ("python_parity.py","numeric","AssertionError"),
    ("python_parity.py","vector-extra","AssertionError"),
    ("python_parity.py","truncated","AssertionError"),
    ("quaternion_parity.py","quaternion-extra","AssertionError"),
    ("quaternion_parity.py","quaternion-status","AssertionError"),
    ("quaternion_parity.py","quaternion-error","AssertionError"),
    ("environment_parity.py","category","WrongError"),
    ("environment_parity.py","environment-missing","AssertionError"),
    ("vehicle_parity.py","category","WrongError"),
    ("vehicle_parity.py","missing","Missing/unexpected"),
    ("vehicle_parity.py","unexpected","Missing/unexpected"),
    ("vehicle_parity.py","duplicate","Duplicate result field"),
    ("vehicle_parity.py","truncated","AssertionError"),
    ("vehicle_parity.py","empty-output","AssertionError"),
])
def test_corrupt_probe_is_rejected(runner, fault, diagnostic):
    process=subprocess.run([sys.executable,"-B","-c",CHILD,str(HERE/runner),fault,JAVA,BUILD],
                           capture_output=True,text=True)
    print(f"FAULT {runner} {fault}: exit={process.returncode}")
    assert process.returncode != 0, process.stdout
    assert diagnostic in process.stderr, process.stderr


@pytest.mark.parametrize("runner",RUNNERS)
def test_optimized_execution_is_rejected(runner):
    process=subprocess.run([sys.executable,"-B","-O",str(HERE/runner),"--java",JAVA,"--build-dir",BUILD],
                           capture_output=True,text=True)
    print(f"OPTIMIZED {runner}: exit={process.returncode}")
    assert process.returncode != 0
    assert "optimized Python is forbidden" in process.stderr


def test_empty_cases_are_rejected():
    process=subprocess.run([sys.executable,"-B","-c","from parity_contract import count_cases; count_cases('EMPTY',[])"] ,
                           cwd=HERE,capture_output=True,text=True)
    print(f"EMPTY_CASES: exit={process.returncode}")
    assert process.returncode != 0
    assert "Empty/unexecuted" in process.stderr
