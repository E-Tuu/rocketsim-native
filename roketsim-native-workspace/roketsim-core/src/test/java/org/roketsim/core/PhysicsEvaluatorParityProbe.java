package org.roketsim.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.FlightConditionsDomainError;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.mass.MassValidationError;
import org.roketsim.core.aerodynamics.AerodynamicEvaluationError;
import org.roketsim.core.dynamics.*;

/** Test-only full-chain adapter. Production evaluator, upstream calculators and raw nested outputs. */
public final class PhysicsEvaluatorParityProbe {
    private PhysicsEvaluatorParityProbe() { }
    static Object decode(String wire) throws ReflectiveOperationException {
        if(wire.equals("e:EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET")) return EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET;
        if(wire.startsWith("e:ThrustDirectionModel.")||wire.startsWith("e:AerodynamicForceModel.")) return DynamicsParityProbe.decode(wire);
        if(wire.startsWith("e:MotorMassEvolutionModel.")||wire.startsWith("e:MotorCgEvolutionModel.")) return PropulsionParityProbe.decode(wire);
        return wire.startsWith("e:")?AerodynamicsParityProbe.decode(wire):DynamicsParityProbe.decode(wire);
    }
    static Map<String,Object> defaults() {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("time",5.); v.put("ignition",5.); v.put("altitude",1200.);
        v.put("position",new double[]{10,20,50}); v.put("velocity",new double[]{0,0,0});
        v.put("launch_position",new double[]{10,20,50}); v.put("launch_velocity",new double[]{0,0,0}); v.put("direction",new double[]{0,0,1});
        v.put("wind_enabled",false); v.put("wind",new double[]{0,0,0});
        v.put("mapping",EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET); return v;
    }
    static void evaluate(String[] tokens,Map<String,Object> output) throws ReflectiveOperationException {
        Map<String,Object> v=defaults(),overrides=new LinkedHashMap<>();
        for(int i=1;i<tokens.length;i++) {
            String[] pair=tokens[i].split("=",2); Object value=decode(pair[1]);
            if(pair[0].startsWith("context."))overrides.put(pair[0].substring(8),value);
            else { if(!v.containsKey(pair[0]))throw new AssertionError(pair[0]); v.put(pair[0],value); }
        }
        if(tokens[0].equals("timeline")) { DynamicsParityProbe.flatten(output,"result",new PropulsionTimeline(DynamicsParityProbe.number(v,"ignition"))); return; }
        Object wind=(Boolean)v.get("wind_enabled")?new ConstantWindModel(v.get("wind")):new NoWindModel();
        var context=PhysicsEvaluatorTest.context(DynamicsParityProbe.number(v,"altitude"),DynamicsParityProbe.number(v,"ignition"),wind);
        context=PhysicsEvaluatorTest.change(context,"environment_position_mapping_model",v.get("mapping"));
        for(var override:overrides.entrySet())context=PhysicsEvaluatorTest.change(context,override.getKey(),override.getValue());
        if(tokens[0].equals("context")) {
            output.put("result.launchEnvironmentAltitudeM",context.launchEnvironmentAltitudeM());
            DynamicsParityProbe.flatten(output,"result.propulsionTimeline",context.propulsionTimeline());
            output.put("result.environmentPositionMappingModel",context.environmentPositionMappingModel());
            output.put("contract.contextFieldCount",context.getClass().getRecordComponents().length);
            output.put("contract.windType",wind.getClass().getSimpleName());
            DynamicsParityProbe.flatten(output,"result.motorPropertyModelProfile",context.motorPropertyModelProfile());
            DynamicsParityProbe.flatten(output,"result.basicDragModelProfile",context.basicDragModelProfile());
            DynamicsParityProbe.flatten(output,"result.translationalDynamicsModelProfile",context.translationalDynamicsModelProfile()); return;
        }
        if(!tokens[0].equals("evaluate"))throw new AssertionError(tokens[0]);
        var state=new TranslationalState3DOF(v.get("position"),v.get("velocity"));
        var launch=new LaunchConditions3DOF(v.get("launch_position"),v.get("launch_velocity"),v.get("direction"));
        var result=new PhysicsEvaluator3DOF().evaluate(DynamicsParityProbe.number(v,"time"),state,launch,context);
        DynamicsParityProbe.flatten(output,"result",result);
        DynamicsParityProbe.derived(output,"result.dynamics",result.dynamics());
        output.put("contract.motorProfileIdentity",result.motorMassProperties().modelProfile()==context.motorPropertyModelProfile());
        output.put("contract.dragProfileIdentity",result.basicDrag().modelProfile()==context.basicDragModelProfile());
        output.put("contract.dynamicsProfileIdentity",result.dynamics().modelProfile()==context.translationalDynamicsModelProfile());
        output.put("result.basicDrag.frictionCd",result.basicDrag().frictionCd());
        output.put("result.basicDrag.pressureCd",result.basicDrag().pressureCd());
        output.put("result.basicDrag.baseCd",result.basicDrag().baseCd());
    }
    public static void main(String[] args) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                Map<String,Object> output=new LinkedHashMap<>();
                try { evaluate(line.split("\t"),output); PropulsionParityProbe.output("OK",output); }
                catch(IllegalArgumentException error) {
                    output.clear(); String category="ValueError",code=null,field=null; Object value=null;
                    if(error instanceof PhysicsEvaluationError e) { category="PhysicsEvaluationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof PropulsionEvaluationError e) { category="PropulsionEvaluationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof MassValidationError e) { category="MassValidationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof AerodynamicEvaluationError e) { category="AerodynamicEvaluationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof DynamicsEvaluationError e) { category="DynamicsEvaluationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof InitialStateValidationError e) { category="InitialStateValidationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof FlightConditionsDomainError e) { category="FlightConditionsDomainError"; field=e.fieldName(); value=e.value(); }
                    else if(error instanceof AtmosphereDomainError e) {
                        category="AtmosphereDomainError";
                        output.put("error.requestedGeopotentialHeightM",e.requestedGeopotentialHeightM());
                        output.put("error.minimumGeopotentialHeightM",e.minimumGeopotentialHeightM());
                        output.put("error.maximumGeopotentialHeightM",e.maximumGeopotentialHeightM());
                    }
                    output.put("error.category",category); output.put("error.code",code); output.put("error.field",field);
                    DynamicsParityProbe.flatten(output,"error.value",value);
                    output.put("error.context",category.equals("ValueError")?error.getMessage().split(" ")[0]:null);
                    PropulsionParityProbe.output("ERROR",output);
                }
            }
        }
    }
}
