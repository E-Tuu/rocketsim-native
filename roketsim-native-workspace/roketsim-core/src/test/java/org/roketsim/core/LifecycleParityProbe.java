package org.roketsim.core;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.dynamics.*;

/** Real production A/B/C/D adapter. Shadow-spy JVMs are separate and explicitly labelled. */
public final class LifecycleParityProbe {
    private LifecycleParityProbe() { }
    static Object decode(String wire) {
        if(wire.startsWith("e:")) {
            String[] parts=wire.substring(2).split("\\.",2);
            return switch(parts[0]) {
                case "BurnoutDetectionModel" -> BurnoutDetectionModel.valueOf(parts[1]);
                case "ApogeeDetectionModel" -> ApogeeDetectionModel.valueOf(parts[1]);
                case "GroundReferenceModel" -> GroundReferenceModel.valueOf(parts[1]);
                case "EventLocalizationModel" -> EventLocalizationModel.valueOf(parts[1]);
                case "TerminalEventHandlingModel" -> TerminalEventHandlingModel.valueOf(parts[1]);
                default -> throw new AssertionError(wire);
            };
        }
        return DynamicsParityProbe.decode(wire);
    }
    static double n(Map<String,Object> v,String key) { return DynamicsParityProbe.number(v,key); }
    static Map<String,Object> defaults() {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("t0",0.);v.put("t1",4.);v.put("p0",new double[]{0,0,10});v.put("p1",new double[]{4,8,14});v.put("v0",new double[]{2,4,6});v.put("v1",new double[]{6,8,10});v.put("ignition",0.);v.put("end",1.);v.put("ground",0.);
        v.put("burnout",BurnoutDetectionModel.PROPULSION_CURVE_END_TIME);v.put("apogee",ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING);v.put("ground_model",GroundReferenceModel.LAUNCH_WORLD_Z_PLANE);v.put("localization",EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION);
        v.put("times",new double[]{2.5,10});v.put("batch1","");v.put("batch2","");v.put("reason","MAXIMUM_STEPS_REACHED");v.put("query","all");v.put("steps",3.);v.put("h",.01);v.put("vz",0.);v.put("direction",new double[]{0,0,1});v.put("policy",TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT);return v;
    }
    static List<FlightEventOccurrence3DOF> events(Object encoded) {
        var result=new ArrayList<FlightEventOccurrence3DOF>();
        if(!encoded.equals(""))for(String entry:((String)encoded).split(",")) { String[] pair=entry.split(":"); result.add(FlightRecorderTest.event(FlightEventType.valueOf(pair[0]),Double.parseDouble(pair[1]))); }
        return result;
    }
    static void flatten(Map<String,Object> out,String prefix,Object value) throws ReflectiveOperationException {
        DynamicsParityProbe.flatten(out,prefix,value);
        if(value instanceof List<?> list)for(int i=0;i<list.size();i++)if(list.get(i) instanceof FlightEventOccurrence3DOF e)out.put(prefix+"."+i+".isTerminal",e.isTerminal());
    }
    static void execute(String[] tokens,Map<String,Object> out) throws ReflectiveOperationException {
        var v=defaults(); for(int i=1;i<tokens.length;i++){String[] p=tokens[i].split("=",2);if(!v.containsKey(p[0]))throw new AssertionError(p[0]);v.put(p[0],decode(p[1]));}
        switch(tokens[0]) {
            case "A" -> {
                var profile=new FlightEventDetectionProfile3DOF(v.get("burnout"),v.get("apogee"),v.get("ground_model"),v.get("localization"));
                var a=new IntegrationPoint3DOF(n(v,"t0"),new TranslationalState3DOF(v.get("p0"),v.get("v0")));
                var b=new IntegrationPoint3DOF(n(v,"t1"),new TranslationalState3DOF(v.get("p1"),v.get("v1")));
                var context=new FlightEventDetectionContext3DOF(PhysicsEvaluatorTest.launch(n(v,"ground")),new PropulsionTimeline(n(v,"ignition")),FlightEventsTest.stats(n(v,"end")),profile);
                flatten(out,"events",new FlightEventDetector3DOF().detect(a,b,context));
            }
            case "B" -> {
                var recorder=new FlightRecorder3DOF(); var physics=FlightRecorderTest.physics(); var before=recorder.snapshot();
                try {
                    for(double time:(double[])v.get("times"))recorder.recordSample(FlightEventsTest.point(time,0,0),physics);
                    recorder.recordEvents(events(v.get("batch1"))); before=recorder.snapshot(); recorder.recordEvents(events(v.get("batch2")));
                    flatten(out,"result",recorder.snapshot());out.put("oldSampleCount",before.samples().size());out.put("oldEventCount",before.events().size());
                } finally { flatten(out,"after",recorder.snapshot()); }
            }
            case "C" -> {
                var config=SimulationEngineTest.config((int)n(v,"steps"),n(v,"ignition"),n(v,"h"),n(v,"vz"));
                config=(SimulationRunConfiguration3DOF)VehicleTestData.replace(config,"launch_conditions",new LaunchConditions3DOF(new double[]{1,0,50},new double[]{0,0,n(v,"vz")},v.get("direction")));
                config=(SimulationRunConfiguration3DOF)VehicleTestData.replace(config,"terminal_event_handling_model",v.get("policy"));
                var x=new SimulationEngine3DOF().run(config);flatten(out,"execution",x);
                var r=new SimulationResult3DOF(x); out.put("terminationTimeS",r.terminationTimeS());out.put("simulationDurationS",r.simulationDurationS());
            }
            case "D" -> {
                var x=SimulationResultTest.execution((double[])v.get("times"),events(v.get("batch1")),SimulationTerminationReason.valueOf((String)v.get("reason")));
                var r=new SimulationResult3DOF(x);out.put("terminationTimeS",r.terminationTimeS());out.put("simulationDurationS",r.simulationDurationS());out.put("initialTimeS",r.initialSample().point().timeS());out.put("lastTimeS",r.lastAcceptedSample().point().timeS());
                flatten(out,"events",r.events());out.put("forwardIdentity",r.execution()==x && r.samples()==x.recordedData().samples() && r.events()==x.recordedData().events());
                String query=(String)v.get("query");
                if(query.equals("all"))for(var type:FlightEventType.values())flatten(out,"query."+type.name(),r.eventsOfType(type));
                else flatten(out,"query",switch(query){case "burnout"->r.burnoutEvent();case "apogee"->r.apogeeEvent();case "ground"->r.groundEvent();default->throw new AssertionError(query);});
            }
            default -> throw new AssertionError(tokens[0]);
        }
    }
    public static void main(String[] args) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) { String line;
            while((line=reader.readLine())!=null) { var out=new LinkedHashMap<String,Object>();
                try {execute(line.split("\t"),out);PropulsionParityProbe.output("OK",out);}
                catch(IllegalArgumentException error) {
                    out.keySet().removeIf(k->!k.startsWith("after."));String category="ValueError",code=null,field=null;Object value=null;
                    if(error instanceof EventDetectionError e){category="EventDetectionError";code=e.errorCode();field=e.fieldName();value=e.value();}
                    else if(error instanceof FlightRecordingError e){category="FlightRecordingError";code=e.errorCode();field=e.fieldName();value=e.value();}
                    else if(error instanceof SimulationEngineError e){category="SimulationEngineError";code=e.errorCode();field=e.fieldName();value=e.value();}
                    else if(error instanceof SimulationResultError e){category="SimulationResultError";code=e.errorCode();field=e.fieldName();value=e.value();}
                    out.put("error.category",category);out.put("error.code",code);out.put("error.field",field);flatten(out,"error.value",value);out.put("error.context",category.equals("ValueError")?error.getMessage().split(" ")[0]:null);PropulsionParityProbe.output("ERROR",out);
                }
            }
        }
    }
}
