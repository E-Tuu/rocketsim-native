package org.roketsim.core;

import java.io.*;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;

/** Test-only numeric wire adapter; parsing and reflection never enter the Core API. */
public final class DynamicsParityProbe {
    private DynamicsParityProbe() { }
    static Object decode(String value) {
        if(value.startsWith("v:")) {
            String body=value.substring(2);
            if(body.isEmpty()) return new double[0];
            String[] parts=body.split(",",-1); double[] result=new double[parts.length];
            for(int i=0;i<parts.length;i++) result[i]=Double.parseDouble(parts[i]);
            return result;
        }
        if(value.startsWith("matrix:")) return new double[][]{(double[])decode("v:"+value.substring(7))};
        if(value.equals("e:ThrustDirectionModel.FIXED_LAUNCH_DIRECTION")) return ThrustDirectionModel.FIXED_LAUNCH_DIRECTION;
        if(value.equals("e:AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY")) return AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY;
        return VehicleParityProbe.decoded(value);
    }
    static double number(Map<String,Object> values,String name) {
        Object value=values.get(name);
        return value instanceof Boolean flag?(flag?1:0):((Number)value).doubleValue();
    }
    static TranslationalState3DOF state(Map<String,Object> v) { return new TranslationalState3DOF(v.get("position"),v.get("velocity")); }
    static LaunchConditions3DOF launch(Map<String,Object> v) { return new LaunchConditions3DOF(v.get("launch_position"),v.get("launch_velocity"),v.get("direction")); }
    static TranslationalDynamicsInputs inputs(Map<String,Object> v) {
        return new TranslationalDynamicsInputs(number(v,"mass"),number(v,"thrust"),v.get("relative"),number(v,"q"),number(v,"area"),number(v,"cd0"),v.get("gravity"));
    }
    static Object evaluate(String[] tokens) {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("position",new double[]{1,2,3}); v.put("velocity",new double[]{0,0,0});
        v.put("launch_position",new double[]{0,0,0}); v.put("launch_velocity",new double[]{0,0,0}); v.put("direction",new double[]{0,0,1});
        v.put("mass",2.); v.put("thrust",30.); v.put("relative",new double[]{0,0,0}); v.put("q",0.); v.put("area",.01); v.put("cd0",.5); v.put("gravity",new double[]{0,0,-10});
        v.put("thrust_model",ThrustDirectionModel.FIXED_LAUNCH_DIRECTION); v.put("aero_model",AerodynamicForceModel.BASELINE_CD0_DRAG_ONLY);
        v.put("thrust_force",new double[]{1,2,3}); v.put("drag_force",new double[]{4,5,6}); v.put("gravity_force",new double[]{7,8,9});
        v.put("position_rate",new double[]{1,2,3}); v.put("velocity_rate",new double[]{4,5,6});
        for(int i=1;i<tokens.length;i++) {
            String[] pair=tokens[i].split("=",2);
            if(!v.containsKey(pair[0])) throw new AssertionError("Unknown input "+pair[0]);
            v.put(pair[0],decode(pair[1]));
        }
        String op=tokens[0];
        return switch(op) {
            case "state" -> state(v);
            case "launch" -> launch(v);
            case "build" -> new InitialStateBuilder().build(launch(v));
            case "inputs" -> inputs(v);
            case "forces" -> new TranslationalForces3DOF(v.get("thrust_force"),v.get("drag_force"),v.get("gravity_force"));
            case "derivative" -> new TranslationalStateDerivative3DOF(v.get("position_rate"),v.get("velocity_rate"));
            case "profile" -> new TranslationalDynamicsModelProfile(v.get("thrust_model"),v.get("aero_model"));
            case "evaluate" -> {
                var state=state(v); var launch=launch(v); var inputs=inputs(v);
                var profile=new TranslationalDynamicsModelProfile(v.get("thrust_model"),v.get("aero_model"));
                var result=new TranslationalDynamicsEvaluator().evaluate(state,launch,inputs,profile);
                if(result.modelProfile()!=profile) throw new AssertionError("Profile identity changed");
                yield result;
            }
            default -> throw new AssertionError(op);
        };
    }
    static void flatten(Map<String,Object> output,String prefix,Object value) throws ReflectiveOperationException {
        if(value instanceof Vector vector) {
            output.put(prefix+".size",vector.size());
            for(int i=0;i<vector.size();i++) output.put(prefix+"."+i,vector.get(i));
        } else if(value instanceof List<?> list) {
            output.put(prefix+".size",list.size());
            for(int i=0;i<list.size();i++) flatten(output,prefix+"."+i,list.get(i));
        } else if(value!=null && value.getClass().isRecord()) {
            for(RecordComponent field:value.getClass().getRecordComponents()) flatten(output,prefix+"."+field.getName(),field.getAccessor().invoke(value));
        } else output.put(prefix,value);
    }
    static void derived(Map<String,Object> fields,String prefix,Object value) throws ReflectiveOperationException {
        if(value instanceof TranslationalForces3DOF forces) flatten(fields,prefix+".netForceWorldN",forces.netForceWorldN());
        if(value instanceof TranslationalDynamicsResult result) {
            derived(fields,prefix+".forces",result.forces());
            flatten(fields,prefix+".accelerationWorldMS2",result.accelerationWorldMS2());
            fields.put("contract.accelerationViewIdentity",result.accelerationWorldMS2()==result.derivative().velocityDerivativeWorldMS2());
            fields.put("contract.profileIdentity",true);
        }
    }
    public static void main(String[] args) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                Map<String,Object> fields=new LinkedHashMap<>();
                try {
                    Object result=evaluate(line.split("\t")); flatten(fields,"result",result); derived(fields,"result",result);
                    PropulsionParityProbe.output("OK",fields);
                } catch(IllegalArgumentException error) {
                    // Do not keep partial result fields when a derived property raises.
                    fields.clear(); String category="ValueError",code=null,field=null; Object value=null;
                    if(error instanceof InitialStateValidationError e) { category="InitialStateValidationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof DynamicsEvaluationError e) { category="DynamicsEvaluationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    fields.put("error.category",category); fields.put("error.code",code); fields.put("error.field",field);
                    flatten(fields,"error.value",value);
                    fields.put("error.context",category.equals("ValueError")?error.getMessage().split(" ")[0]:null);
                    PropulsionParityProbe.output("ERROR",fields);
                }
            }
        }
    }
}
