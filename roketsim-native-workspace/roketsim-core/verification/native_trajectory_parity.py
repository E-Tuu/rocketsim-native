"""J-011: real NAT-022 engines, every sample/field, strict framing and first-difference evidence."""
import argparse
from dataclasses import asdict
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import sys
from parity_contract import nonempty,keyed_output,count_cases
from dynamics_parity import flatten
from physics_evaluator_parity import tolerances as physics_tolerances
from propulsion_parity import decoded
import numpy as np

HERE=Path(__file__).resolve().parent
sys.path.insert(0,str(HERE.parent.parent/'roketsim-native-engine/tests/integration/simulation'))
import test_native_demo_trajectory as source

# Source-approved NAT-022 verification configurations, NOT runtime defaults.
CASES=[('primary',source.PRIMARY_STEP_SIZE_S,source.PRIMARY_MAXIMUM_STEPS),
       ('repeat',source.PRIMARY_STEP_SIZE_S,source.PRIMARY_MAXIMUM_STEPS),
       ('fine',source.FINE_STEP_SIZE_S,source.FINE_MAXIMUM_STEPS)]


def sample_fields(sample,index):
    out={'index':index,**flatten(sample,'sample')};p=sample.physics
    out.update(flatten(p.dynamics.forces.net_force_world_N,'sample.physics.dynamics.forces.netForceWorldN'))
    out.update(flatten(p.dynamics.acceleration_world_m_s2,'sample.physics.dynamics.accelerationWorldMS2'))
    out.update({'sample.physics.basicDrag.frictionCd':p.basic_drag.friction_cd,
        'sample.physics.basicDrag.pressureCd':p.basic_drag.pressure_cd,'sample.physics.basicDrag.baseCd':p.basic_drag.base_cd})
    return out


def summary(r):
    out=dict(terminationReason=r.termination_reason.name,stepsPerformed=r.steps_performed,
        sampleCount=len(r.samples),eventCount=len(r.events),terminationTimeS=r.termination_time_s,
        simulationDurationS=r.simulation_duration_s,
        initialTotalMassKg=r.initial_sample.physics.rocket_mass_properties.total_mass_kg,
        initialMotorMassKg=r.initial_sample.physics.motor_mass_properties.mass_kg,
        finalMotorMassKg=r.last_accepted_sample.physics.motor_mass_properties.mass_kg,
        maximumMach=max(s.physics.flight_conditions.mach for s in r.samples),
        maximumQ=max(s.physics.flight_conditions.dynamic_pressure_Pa for s in r.samples),
        maximumAirspeed=max(s.physics.flight_conditions.airspeed_m_s for s in r.samples))
    out.update(flatten(r.samples[1].point,'firstPropagated'));out.update(flatten(r.last_accepted_sample.point,'lastAccepted'))
    for kind in source.FlightEventType:
        matches=r.events_of_type(event_type=kind);out['query.'+kind.name+'.size']=len(matches)
        if len(matches)!=1:raise AssertionError('NAT-022 event cardinality')
        out.update(flatten(matches[0],kind.name))
    out['forwardIdentity']=r.recorded_data is r.execution.recorded_data and r.samples is r.execution.recorded_data.samples
    return out


def header(configuration,r):
    c=configuration.physics_context
    out=dict(stepSizeS=configuration.fixed_step_config.step_size_s,maximumSteps=configuration.run_limits.maximum_steps,
        sampleCount=len(r.samples),eventCount=len(r.events),launchEnvironmentAltitudeM=c.launch_environment_altitude_m,
        ignitionTimeS=c.propulsion_timeline.ignition_time_s)
    for key,value in [('geometry',c.resolved_geometry),('structure',c.structural_mass_properties),
        ('surfaces',c.aerodynamic_surfaces),('launch',configuration.launch_conditions),('statistics',configuration.motor_curve_statistics)]:
        out.update(flatten(value,key))
    for key,value in [('eventProfile',configuration.event_detection_profile),('motorPropertyProfile',c.motor_property_model_profile),
        ('dragProfile',c.basic_drag_model_profile),('dynamicsProfile',c.translational_dynamics_model_profile)]:out.update(flatten(value,key))
    out.update(terminalPolicy=configuration.terminal_event_handling_model.name,
        environmentMapping=c.environment_position_mapping_model.name,motorId=c.motor_installation.motor.motor_id)
    return out


def records(configuration,r):
    yield 'HEADER',header(configuration,r)
    for index,s in enumerate(r.samples):yield 'SAMPLE',sample_fields(s,index)
    for index,e in enumerate(r.events):yield 'EVENT',{'index':index,**flatten(e,'event'),'event.isTerminal':e.is_terminal}
    yield 'RESULT',summary(r)
    yield 'END',{}


def quantity_tolerance(kind,key,time):
    # Reuse J-009's established upstream quantity contracts only. No trajectory
    # position/velocity/time/event tolerance is invented: unapproved differences STOP.
    if kind=='SAMPLE' and key.startswith('sample.physics.'):
        return physics_tolerances('evaluate','result.'+key[len('sample.physics.'):],{'time':time,'ignition':0.})
    return 0.,0.


def compare_record(kind,expected,line,location,stats):
    tokens=line.rstrip('\r\n').split('\t')
    if tokens[0]!=kind:raise AssertionError(('Record type/order',location,kind,tokens[0]))
    actual={k:decoded(v) for k,v in keyed_output(tokens[1:],expected).items()}
    for key,want in expected.items():
        got=actual[key]
        if isinstance(want,bool):
            if type(got) is not bool or got is not want:raise AssertionError(('Metadata mismatch',location,key,want,got))
        elif isinstance(want,(int,float)):
            rtol,atol=quantity_tolerance(kind,key,expected.get('sample.point.timeS',0.))
            if not isinstance(got,(int,float)) or isinstance(got,bool) or not math.isfinite(want) or not math.isfinite(got):
                raise AssertionError(('Nonfinite/type mismatch',location,key,want,got))
            delta=abs(want-got);relative=delta/abs(want) if want else None
            stats['numeric']+=1;stats['exact']+=int(want==got);stats['nonexact']+=int(want!=got)
            stats['max_absolute']=max(stats['max_absolute'],delta)
            if relative is not None:stats['max_relative']=max(stats['max_relative'],relative)
            stats['zero_reference_nonexact']+=int(want==0 and delta!=0)
            stats['shape']+=key.endswith('.size') or key=='index'
            stats['nested_physics']+=key.startswith('sample.physics.')
            if delta>max(atol,rtol*abs(want)):
                raise AssertionError(('Numeric mismatch',location,key,want,got,delta,relative,rtol,atol))
            if want!=got:
                stats.setdefault('differences',[]).append(dict(location=location,field=key,python=want,java=got,
                    absolute_difference=delta,relative_difference=relative,rtol=rtol,atol=atol))
        elif type(got) is not type(want) or got!=want:
            raise AssertionError(('Metadata mismatch',location,key,want,got))
    stats['records']+=1
    return actual


def empty_stats():
    return dict(records=0,numeric=0,exact=0,nonexact=0,shape=0,nested_physics=0,max_absolute=0.,max_relative=0.,zero_reference_nonexact=0)


def compare_records(expected_records,lines,label,artifact_dir,input_evidence):
    stats=empty_stats();stats['by_kind']={};stats['differences']=[]
    anchors=[];iterator=iter(lines);sample_index=-1
    for record_index,(kind,expected) in enumerate(expected_records):
        sample_index=expected.get('index',sample_index) if kind=='SAMPLE' else sample_index
        location=dict(run=label,record=record_index,kind=kind,sample_index=sample_index)
        line=next(iterator,None)
        try:
            if line is None:raise AssertionError(('Truncated output',location))
            row=empty_stats();actual=compare_record(kind,expected,line,location,row)
            stats['differences'].extend(row.get('differences',[]))
            group=stats['by_kind'].setdefault(kind,empty_stats())
            for target in (stats,group):
                for key in empty_stats():
                    target[key]=max(target[key],row[key]) if key in ('max_absolute','max_relative') else target[key]+row[key]
        except (AssertionError,ValueError,TypeError) as error:
            evidence=dict(input=input_evidence,
                location=location,python=expected,java_raw=line,discrepancy=repr(error),compared_so_far=stats)
            artifact_dir.mkdir(parents=True,exist_ok=True)
            with (artifact_dir/(label+'-first-difference.json')).open('w',encoding='utf-8') as f:json.dump(evidence,f,allow_nan=False,indent=2)
            print('TRAJECTORY_STOP '+json.dumps({'location':location,'discrepancy':repr(error),'evidence':str(artifact_dir/(label+'-first-difference.json'))}))
            raise
        if kind=='HEADER':sample_count=expected['sampleCount']
        if kind in ('HEADER','EVENT','RESULT') or kind=='SAMPLE' and expected['index'] in (0,1,sample_count//2,sample_count-1):
            anchors.append(dict(location=location,python=expected,java=actual))
    nonempty(stats['records'])
    if next(iterator,None) is not None:raise AssertionError('Extra output after END')
    stats.update(samples=sample_count,events=stats['by_kind'].get('EVENT',{}).get('records',0))
    return stats,anchors


def compare_run(configuration,r,lines,label,artifact_dir):
    return compare_records(records(configuration,r),lines,label,artifact_dir,
        dict(configuration=header(configuration,r),source='test_native_demo_trajectory.demo_configuration'))


def main():
    p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--build-dir',type=Path,required=True)
    p.add_argument('--artifacts-dir',type=Path,required=True);args=p.parse_args()
    nonempty(CASES);count_cases('NATIVE_TRAJECTORY',[(h,limit) for _,h,limit in CASES])
    args.artifacts_dir.mkdir(parents=True,exist_ok=True)
    cp=os.pathsep.join(str(args.build_dir/path) for path in ('classes/java/main','classes/java/test'))
    all_stats={};all_anchors=[];previous=None
    for label,h,limit in CASES:
        c=source.demo_configuration(step_size_s=h,maximum_steps=limit)
        r=source.SimulationResult3DOF(source.SimulationEngine3DOF().run(configuration=c))
        trace=args.artifacts_dir/(label+'-java.tsv');stderr=args.artifacts_dir/(label+'-java.stderr')
        with trace.open('w',encoding='utf-8',newline='\n') as out,stderr.open('w',encoding='utf-8') as err:
            subprocess.run([args.java,'-cp',cp,'org.roketsim.core.NativeTrajectoryParityProbe',repr(h),str(limit)],stdout=out,stderr=err,text=True,check=True)
        with (args.artifacts_dir/(label+'-python.jsonl')).open('w',encoding='utf-8') as out:
            for kind,values in records(c,r):out.write(json.dumps(dict(kind=kind,values=values),allow_nan=False)+'\n')
        with trace.open(encoding='utf-8') as lines:stats,anchors=compare_run(c,r,lines,label,args.artifacts_dir)
        all_stats[label]=stats;all_anchors.extend(anchors)
        if label=='primary':previous=r
        if label=='repeat':
            if summary(r)!=summary(previous):raise AssertionError('Python repeat summary differs')
            for a,b in zip(previous.samples,r.samples,strict=True):
                if sample_fields(a,0)!=sample_fields(b,0):raise AssertionError('Python repeat sample differs')
        print('TRAJECTORY_RUN '+json.dumps(dict(run=label,statistics=stats,summary=summary(r)),allow_nan=False))
    with (args.artifacts_dir/'comparison-summary.json').open('w',encoding='utf-8') as f:json.dump(all_stats,f,indent=2,allow_nan=False)
    with (args.artifacts_dir/'anchors.jsonl').open('w',encoding='utf-8') as f:
        for record in all_anchors:f.write(json.dumps(record,allow_nan=False)+'\n')
    print('NATIVE_TRAJECTORY_PARITY: PASS');print(sys.version);print('NumPy',np.__version__,platform.platform())


if __name__=='__main__':main()
