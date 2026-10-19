"""J-010 separately counted A/B/C/D production parity and isolated source-equivalent C spies."""
import argparse
from dataclasses import fields,replace
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import numpy as np
from parity_contract import nonempty,keyed_output,count_cases
from dynamics_parity import encode,flatten as flat
from propulsion_parity import decoded
from physics_evaluator_parity import context as physics_context,DEFAULTS as PHYSICS_DEFAULTS,tolerances as physics_tolerances
from roketsim_native.dynamics.initial_state import LaunchConditions3DOF,TranslationalState3DOF
from roketsim_native.numerics.fixed_step import FixedStepConfig,IntegrationPoint3DOF
from roketsim_native.propulsion.thrust import MotorCurveStatistics,MotorCurveAnalyzer
from roketsim_native.propulsion.catalog import AEROTECH_F50_4T
from roketsim_native.simulation import events as ev,recorder as rec,engine as eng,result as res
from roketsim_native.simulation.physics import PhysicsEvaluator3DOF,PropulsionTimeline

HERE=Path(__file__).resolve().parent
DEFAULTS=dict(t0=0.,t1=4.,p0=(0.,0.,10.),p1=(4.,8.,14.),v0=(2.,4.,6.),v1=(6.,8.,10.),ignition=0.,end=1.,ground=0.,
    burnout=ev.BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,apogee=ev.ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
    ground_model=ev.GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,localization=ev.EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION,
    times=(2.5,10.),batch1='',batch2='',reason='MAXIMUM_STEPS_REACHED',query='all',steps=3.,h=.01,vz=0.,direction=(0.,0.,1.),policy=eng.TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT)
USED={'A':('t0','t1','p0','p1','v0','v1','ignition','end','ground','burnout','apogee','ground_model','localization'),
    'B':('times','batch1','batch2'),'C':('steps','ignition','h','vz','direction','policy'),
    'D':('times','batch1','reason','query')}


def profile(v=DEFAULTS): return ev.FlightEventDetectionProfile3DOF(v['burnout'],v['apogee'],v['ground_model'],v['localization'])
def point(t,z=0.,vz=0.): return IntegrationPoint3DOF(t,TranslationalState3DOF((0.,0.,z),(0.,0.,vz)))
def event(kind,t): return ev.FlightEventOccurrence3DOF(ev.FlightEventType[kind],t,.5,point(t).state)
def event_list(text): return tuple(event(kind,float(t)) for kind,t in (x.split(':') for x in text.split(','))) if text else ()
def flatten(value,prefix):
    output=flat(value,prefix)
    if isinstance(value,(tuple,list)):
        for i,item in enumerate(value):
            if isinstance(item,ev.FlightEventOccurrence3DOF): output[f'{prefix}.{i}.isTerminal']=item.is_terminal
    return output
def real_physics():
    c=physics_context({**PHYSICS_DEFAULTS,'ignition':0.})
    l=LaunchConditions3DOF((10.,20.,50.),(0.,0.,0.),(0.,0.,1.))
    return PhysicsEvaluator3DOF().evaluate(time_s=0.,state=TranslationalState3DOF((10.,20.,50.),(0.,0.,0.)),launch_conditions=l,context=c)


def python_call(group,v,partial):
    if group=='A':
        p=profile(v); a=IntegrationPoint3DOF(v['t0'],TranslationalState3DOF(v['p0'],v['v0']));b=IntegrationPoint3DOF(v['t1'],TranslationalState3DOF(v['p1'],v['v1']))
        c=ev.FlightEventDetectionContext3DOF(LaunchConditions3DOF((10.,20.,v['ground']),(0.,0.,0.),(0.,0.,1.)),PropulsionTimeline(v['ignition']),MotorCurveStatistics(1.,1.,.5,v['end'],.1,.9,.8),p)
        return flatten(ev.FlightEventDetector3DOF().detect(start_point=a,end_point=b,context=c),'events')
    if group=='B':
        r=rec.FlightRecorder3DOF(); physics=real_physics(); before=r.snapshot()
        try:
            for t in v['times']: r.record_sample(point=point(t),physics=physics)
            r.record_events(events=event_list(v['batch1'])); before=r.snapshot(); r.record_events(events=event_list(v['batch2']))
            output=flatten(r.snapshot(),'result'); output.update(oldSampleCount=len(before.samples),oldEventCount=len(before.events))
        finally: partial.update(flatten(r.snapshot(),'after'))
        output.update(partial); return output
    if group=='C':
        c=physics_context({**PHYSICS_DEFAULTS,'ignition':v['ignition']})
        launch=LaunchConditions3DOF((1.,0.,50.),(0.,0.,v['vz']),v['direction'])
        config=eng.SimulationRunConfiguration3DOF(launch,c,MotorCurveAnalyzer().analyze(motor=AEROTECH_F50_4T),profile(),FixedStepConfig(v['h']),eng.SimulationRunLimits3DOF(int(v['steps'])),v['policy'])
        x=eng.SimulationEngine3DOF().run(configuration=config); output=flatten(x,'execution'); r=res.SimulationResult3DOF(x)
        output.update(terminationTimeS=r.termination_time_s,simulationDurationS=r.simulation_duration_s); return output
    if group!='D': raise AssertionError(group)
    physics=real_physics(); samples=tuple(rec.RecordedFlightSample3DOF(point(t),physics) for t in v['times'])
    x=eng.SimulationExecution3DOF(rec.RecordedFlightData3DOF(samples,event_list(v['batch1'])),eng.SimulationTerminationReason[v['reason']],2)
    r=res.SimulationResult3DOF(x)
    output=dict(terminationTimeS=r.termination_time_s,simulationDurationS=r.simulation_duration_s,initialTimeS=r.initial_sample.point.time_s,lastTimeS=r.last_accepted_sample.point.time_s,forwardIdentity=r.execution is x and r.samples is x.recorded_data.samples and r.events is x.recorded_data.events)
    output.update(flatten(r.events,'events'))
    if v['query']=='all':
        for kind in ev.FlightEventType: output.update(flatten(r.events_of_type(event_type=kind),'query.'+kind.name))
    else: output.update(flatten(getattr(r,v['query']+'_event'),'query'))
    return output


CASES=[]
def add(group,**values): CASES.append((group,values))
add('A')
add('A',t0=1.6,t1=1.7,ignition=.25,end=1.43,p0=(0.,0.,10.),p1=(1.,2.,20.),v0=(0.,0.,2.),v1=(1.,2.,4.))
for t0,t1 in ((1.6,1.68),(1.68,1.8)): add('A',t0=t0,t1=t1,ignition=.25,end=1.43,v0=(0.,0.,1.),v1=(0.,0.,1.))
for a,b in ((3.,-1.),(3.,0.),(0.,2.),(-1.,-2.),(0.,-1.)):
    add('A',t0=4.,t1=4.2,end=100.,p0=(0.,0.,100.),p1=(0.,0.,101.),v0=(0.,0.,a),v1=(0.,0.,b))
for a,b in ((50.3,49.9),(50.3,50.),(50.,49.9),(50.,50.1),(50.3,50.1)):
    add('A',t0=9.,t1=9.2,end=100.,ground=50.,p0=(1.,2.,a),p1=(3.,4.,b),v0=(0.,0.,-2.),v1=(0.,0.,-4.))
add('A',t0=5.,t1=5.2,ignition=5.,end=.1,ground=50.,p0=(0.,0.,51.),p1=(0.,0.,49.),v0=(0.,0.,1.),v1=(0.,0.,-1.))
for end in (0.,-1.):add('A',t0=0.,t1=end)
add('A',t0=-1e308,t1=1e308);add('A',ignition=1e308,end=1e308)
add('A',t0=0.,t1=1.,end=.5,p0=(0.,0.,-1e308),p1=(0.,0.,1e308))
for field in ('burnout','apogee','ground_model','localization'):
    for value in ('bad',None,DEFAULTS[field].value):add('A',**{field:value})
for times in ((),(-2.,0.,.01,.02),(0.,.01,.01),(0.,.01,.005)):
    add('B',times=times)
for batch1,batch2 in (('',''),('BURNOUT:1.43,APOGEE:4.12,GROUND:8.91',''),('GROUND:4,BURNOUT:4,APOGEE:4',''),('BURNOUT:1.43,BURNOUT:1.43',''),('BURNOUT:1.4','APOGEE:4,GROUND:3.5'),('BURNOUT:1.4','APOGEE:1.3')):
    add('B',times=(0.,.01,.02),batch1=batch1,batch2=batch2)
for n in (1.,2.,3.):add('C',steps=n,ignition=2.5)
for vz in (-1.,0.,2.):
    for h in (.001,.01,.1):add('C',steps=3.,vz=vz,h=h)
add('C',steps=20.,vz=2.,direction=(0.,0.,-1.))
for n in (0.,-1.):add('C',steps=n)
add('C',policy='reject_interior_candidate_accept_endpoint')
for times,batch,reason in (((2.5,2.6,2.7),'BURNOUT:2.55','MAXIMUM_STEPS_REACHED'),((),'','MAXIMUM_STEPS_REACHED'),((9.,9.1),'GROUND:9.163','TERMINAL_EVENT'),((9.,9.2),'GROUND:9.2','TERMINAL_EVENT'),((2.5,12.75),'','MAXIMUM_STEPS_REACHED'),((2.5,10.),'GROUND:10.08','TERMINAL_EVENT'),((1.,),'','TERMINAL_EVENT'),((1.,),'GROUND:2,GROUND:2.1','TERMINAL_EVENT'),((1.,),'GROUND:2','MAXIMUM_STEPS_REACHED'),((2.5,),'GROUND:2.4','TERMINAL_EVENT')):
    add('D',times=times,batch1=batch,reason=reason)
for query in ('all','burnout','apogee','ground'):
    for batch in ('','BURNOUT:1.4','APOGEE:4.1,APOGEE:4.2'):add('D',times=(5.,),batch1=batch,query=query)
add('D',times=(2.5,6.),batch1='',reason='MAXIMUM_STEPS_REACHED')
SPY_CASES=[('none',1,0),('none',2,0),('none',3,0),('interior1',1,0),('endpoint1',1,0),('interior3',3,0),('filter',1,0),('tie',1,0),('none',1,1),('none',1,2),('none',1,6),('detector-error',1,0),('recorder-error',1,0)]


def spy_python(case):
    from contextlib import ExitStack
    from unittest.mock import patch
    sys.path.insert(0,str(HERE.parent.parent/'roketsim-native-engine/tests/unit/simulation'))
    import test_engine as source
    scenario,steps,fail=case; sentinel=source.SentinelUpstreamError('sentinel')
    physics=source.PhysicsSpy(fail_call=fail or None,sentinel=sentinel)
    def e(kind,t,a):return source.occurrence(ev.FlightEventType[kind],t,a)
    batches={'interior1':((e('GROUND',2.56,.6),),),'endpoint1':((e('GROUND',2.6,1.),),),'interior3':((),(),(e('GROUND',2.76,.6),)),
        'filter':((e('APOGEE',8.1,.2),e('GROUND',8.15,.6),e('BURNOUT',8.18,.9)),), 'tie':((e('BURNOUT',5.,.6),e('APOGEE',5.,.6),e('GROUND',5.,.6)),)}
    detector=source.DetectorSpy(batches.get(scenario,()),sentinel=sentinel if scenario=='detector-error' else None)
    output={};seen_configs=[];configuration=source.configuration(maximum_steps=steps)
    accepted_integrator=source.ClassicalRK4Integrator3DOF
    class IntegratorSpy:
        def step(self,*,point,config,derivative_function):
            seen_configs.append(config)
            return accepted_integrator().step(point=point,config=config,derivative_function=derivative_function)
    with ExitStack() as stack:
        stack.enter_context(patch.object(eng,'PhysicsEvaluator3DOF',lambda:physics))
        stack.enter_context(patch.object(eng,'FlightEventDetector3DOF',lambda:detector))
        stack.enter_context(patch.object(eng,'ClassicalRK4Integrator3DOF',IntegratorSpy))
        if scenario=='recorder-error':
            class FailingRecorder:
                def record_sample(self,*,point,physics):raise sentinel
            stack.enter_context(patch.object(eng,'FlightRecorder3DOF',FailingRecorder))
        try:
            x=eng.SimulationEngine3DOF().run(configuration=configuration)
            output.update(reason=x.termination_reason.name,steps=x.steps_performed)
            output['samples.size']=len(x.recorded_data.samples)
            for i,s in enumerate(x.recorded_data.samples): output.update(flat(s.point,'samples.'+str(i)))
            output.update(flat(x.recorded_data.events,'events'))
            output['k4NotEndpoint']=len(x.recorded_data.samples)>1 and physics.calls[4][1] is not physics.calls[5][1] and not np.array_equal(physics.calls[4][1].position_world_m,physics.calls[5][1].position_world_m)
        except source.SentinelUpstreamError as error: output['exceptionIdentity']=error is sentinel
    output.update(physicsCalls=len(physics.calls),detectorCalls=len(detector.calls),integratorCalls=len(seen_configs),fixedConfigIdentity=all(c is configuration.fixed_step_config for c in seen_configs));return output


def compare(case,line,status,expected):
    parts=line.split('\t'); actual=None
    try:
        if parts[0]!=status:raise AssertionError(('Wrong category',status,parts[0]))
        actual={k:decoded(v) for k,v in keyed_output(parts[1:],expected).items()}
        exact=numeric=shape=0; maxabs=maxrel=0.
        for key,want in expected.items():
            got=actual[key]; rtol=atol=0.
            if isinstance(want,bool):match=type(got) is bool and got is want
            elif isinstance(want,(int,float)):
                if status=='OK':
                    if 'interpolationFraction' in key or (key.startswith('events.') and key.endswith('timeS')) or key=='simulationDurationS':rtol,atol=1e-6,1e-12
                    elif '.estimatedState.' in key or ('.state.' in key and '.physics.' not in key):rtol=1e-7
                    if '.physics.' in key:
                        field='result.'+key.split('.physics.',1)[1]
                        rtol,atol=physics_tolerances('evaluate',field,{'time':0.,'ignition':0.})
                        if field=='result.motorThrustState.thrustN':rtol=4e-15
                    if key.endswith('.size') or key in ('physicsCalls','detectorCalls','steps','oldSampleCount','oldEventCount'):rtol=atol=0.
                    numeric+=1;exact+=want==got;shape+=key.endswith('.size')
                    delta=abs(want-got);maxabs=max(maxabs,delta)
                    if want:maxrel=max(maxrel,delta/abs(want))
                match=want==got or math.isnan(want) and math.isnan(got) or math.isfinite(want) and math.isfinite(got) and abs(want-got)<=max(atol,rtol*abs(want))
            else:match=want==got
            if not match:raise AssertionError((key,want,got,rtol,atol))
        return numeric,exact,shape,maxabs,maxrel,actual
    except (AssertionError,TypeError,ValueError) as error:
        print('PARITY_FINDING '+json.dumps(dict(input=repr(case),python=repr(expected),raw_java=line,decoded_java=repr(actual),difference=repr(error))));raise


def main():
    p=argparse.ArgumentParser();p.add_argument('--java',required=True);p.add_argument('--build-dir',type=Path,required=True);args=p.parse_args()
    nonempty(CASES);nonempty(SPY_CASES)
    for group in USED:count_cases('LIFECYCLE_'+group,[(g,{k:v for k,v in {**DEFAULTS,**c}.items() if k in USED[g]}) for g,c in CASES if g==group])
    cp=os.pathsep.join(str(args.build_dir/path) for path in ('classes/java/main','classes/java/test'))
    payload='\n'.join('\t'.join([g,*(k+'='+encode(v) for k,v in changes.items())]) for g,changes in CASES)+'\n'
    process=subprocess.run([args.java,'-cp',cp,'org.roketsim.core.LifecycleParityProbe'],input=payload,text=True,capture_output=True,check=True)
    lines=process.stdout.splitlines()
    if len(lines)!=len(CASES):raise AssertionError(('Truncated/extra result set',len(lines),len(CASES)))
    stats={g:[0,0,0,0,0,0.,0.] for g in USED}
    for case,line in zip(CASES,lines):
        group,changes=case;partial={}
        try:expected=python_call(group,{**DEFAULTS,**changes},partial);status='OK'
        except ValueError as error:
            status='ERROR';expected={**partial,'error.category':type(error).__name__,'error.code':getattr(error,'error_code',None),'error.field':getattr(error,'field_name',None)}
            expected.update(flatten(getattr(error,'value',None),'error.value'));expected['error.context']=str(error).split()[0] if type(error) is ValueError else None
        numeric,exact,shape,ma,mr,actual=compare(case,line,status,expected)
        anchor=(group=='A' and changes.get('t0')==1.6) or (group=='B' and status=='ERROR' and bool(changes.get('batch2'))) or (group=='C' and changes.get('steps')==1 and changes.get('ignition')==2.5) or (group=='D' and changes.get('batch1') in ('GROUND:9.163','GROUND:10.08'))
        if anchor:
            print('LIFECYCLE_ANCHOR_EVIDENCE '+json.dumps(dict(group=group,input=repr({**DEFAULTS,**changes}),status=status,python=expected,java=actual,raw_java=line),allow_nan=False))
        row=stats[group];row[0]+=status=='OK';row[1]+=status=='ERROR';row[2]+=numeric;row[3]+=exact;row[4]+=shape;row[5]=max(row[5],ma);row[6]=max(row[6],mr)
    for group,row in stats.items(): print(f'LIFECYCLE_{group}_PARITY: success={row[0]} errors={row[1]} numeric={row[2]} exact={row[3]} nonexact={row[2]-row[3]} shape={row[4]} maxabs={row[5]!r} maxrel={row[6]!r}')
    sources=list((HERE/'lifecycle-spies').rglob('*.java'));nonempty(sources)
    spies=args.build_dir/'lifecycle-spies'
    subprocess.run([str(Path(args.java).with_name('javac.exe')),'--release','17','-cp',cp,'-d',str(spies),*map(str,sources)],capture_output=True,text=True,check=True)
    recorder_sources=list((HERE/'lifecycle-recorder-failure').rglob('*.java'));nonempty(recorder_sources)
    recorder_failure=args.build_dir/'lifecycle-recorder-failure'
    subprocess.run([str(Path(args.java).with_name('javac.exe')),'--release','17','-cp',str(spies)+os.pathsep+cp,'-d',str(recorder_failure),*map(str,recorder_sources)],capture_output=True,text=True,check=True)
    count_cases('LIFECYCLE_C_SPY',SPY_CASES);numeric=exact=0
    for case in SPY_CASES:
        spy_cp=(str(recorder_failure)+os.pathsep if case[0]=='recorder-error' else '')+str(spies)+os.pathsep+cp
        result=subprocess.run([args.java,'-Droketsim.verification.realClasses='+str(args.build_dir/'classes/java/main'),'-cp',spy_cp,'org.roketsim.core.LifecycleSpyProbe',*map(str,case)],capture_output=True,text=True,check=True)
        rows=result.stdout.splitlines()
        if len(rows)!=1:raise AssertionError('Truncated/extra spy output')
        expected=spy_python(case);n,e,_,_,_,actual=compare(case,rows[0],'OK',expected);numeric+=n;exact+=e
        print('LIFECYCLE_SPY_EVIDENCE '+json.dumps(dict(input=case,python=expected,java=actual),allow_nan=False))
    print(f'LIFECYCLE_C_SPY_PARITY: {len(SPY_CASES)} passed numeric={numeric} exact={exact}')
    print(sys.version);print('NumPy',np.__version__)


if __name__=='__main__':main()
