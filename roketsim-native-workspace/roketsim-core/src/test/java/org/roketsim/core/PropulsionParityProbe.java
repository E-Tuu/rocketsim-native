package org.roketsim.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.RecordComponent;
import java.util.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.geometry.*;
import static org.roketsim.core.propulsion.MotorCatalogs.*;
import static org.roketsim.core.propulsion.MotorPropertyModelProfile.*;

/** Test adapter only: numeric wire parsing never enters production calculations. */
public final class PropulsionParityProbe {
    private PropulsionParityProbe() { }
    static Object decode(String text) {
        if(text.startsWith("e:MotorType.")) { return MotorType.valueOf(text.substring(12)); }
        if(text.startsWith("e:MotorMassEvolutionModel.")) { return MotorMassEvolutionModel.valueOf(text.substring(26)); }
        if(text.startsWith("e:MotorCgEvolutionModel.")) { return MotorCgEvolutionModel.valueOf(text.substring(24)); }
        return VehicleParityProbe.decoded(text);
    }
    static MotorDefinition fixture(String name) {
        var m=AEROTECH_F50_4T;
        return switch(name) {
            case "f50" -> m;
            case "source" -> MotorPropertiesTest.sourceMotor();
            case "independent" -> {
                var source=MotorPropertiesTest.sourceMotor();
                source=(MotorDefinition)VehicleTestData.replace(source,"mass_curve",List.of(new MotorMassSample(0,.1),new MotorMassSample(.4,.08),new MotorMassSample(2,.04)));
                yield (MotorDefinition)VehicleTestData.replace(source,"cg_curve",List.of(new MotorCgSample(0,.02),new MotorCgSample(.6,.08),new MotorCgSample(3,.02)));
            }
            case "triangle" -> MotorThrustTest.curve(new double[][]{{0,0},{1,20},{3,0}});
            case "plateau" -> MotorThrustTest.curve(new double[][]{{0,0},{1,1},{2,1},{3,20},{4,0},{5,1},{6,1},{7,0}});
            case "multi" -> MotorThrustTest.curve(new double[][]{{0,0},{1,20},{2,0},{3,20},{4,0}});
            case "overflow" -> MotorThrustTest.curve(new double[][]{{0,0},{1e308,20},{1.1e308,0}});
            case "underflow" -> MotorThrustTest.curve(new double[][]{{0,0},{1e-200,1e-200},{2e-200,0}});
            default -> throw new AssertionError("Unknown fixture "+name);
        };
    }
    static List<?> curve(String encoded,String kind) {
        if(encoded.isEmpty()) { return List.of(); }
        return Arrays.stream(encoded.split(";")).map(pair->{
            String[] values=pair.split(","); double time=Double.parseDouble(values[0]),value=Double.parseDouble(values[1]);
            return switch(kind) { case "thrust" -> new ThrustSample(time,value); case "mass" -> new MotorMassSample(time,value); case "cg" -> new MotorCgSample(time,value); default -> throw new AssertionError(kind); };
        }).toList();
    }
    static Object evaluate(String[] tokens) {
        String operation=tokens[0],name=tokens[1];
        MotorDefinition motor=fixture(name);
        var geometry=MotorInstallationTest.geometry();
        Object massModel=MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,cgModel=MotorCgEvolutionModel.FIXED_MIDPOINT;
        double time=0; Object lookup="aerotech_f50_4t"; String kind="thrust";
        double sampleValue=0;
        Map<String,Object> deferred=new LinkedHashMap<>();
        for(int i=2;i<tokens.length;i++) {
            String[] entry=tokens[i].split("=",2); String path=entry[0]; Object value=decode(entry[1]);
            if(path.startsWith("motor.")) { motor=(MotorDefinition)VehicleTestData.replace(motor,path.substring(6),value); }
            else if(path.startsWith("geometry.")) { geometry=(ResolvedRocketGeometry)VehicleTestData.replace(geometry,path.substring(9),value); }
            else if(path.startsWith("curve.")) { String type=path.substring(6); motor=(MotorDefinition)VehicleTestData.replace(motor,type+"_curve",curve((String)value,type)); }
            else if(path.equals("time")) { time=((Number)value).doubleValue(); }
            else if(path.equals("mass_model")) { massModel=value; }
            else if(path.equals("cg_model")) { cgModel=value; }
            else if(path.equals("lookup")) { lookup=value; }
            else if(path.equals("kind")) { kind=(String)value; }
            else if(path.equals("value")) { sampleValue=((Number)value).doubleValue(); }
            else { deferred.put(path,value); }
        }
        if(operation.equals("catalog")) { return motor; }
        if(operation.equals("lookup")) { return DEMO_MOTOR_CATALOG.get((String)lookup); }
        if(operation.equals("duplicate")) { return new MotorCatalog(List.of(motor,motor)); }
        if(operation.equals("certification")) {
            Object cert=motor.certification(); for(var change:deferred.entrySet()) { cert=VehicleTestData.replace(cert,change.getKey(),change.getValue()); } return cert;
        }
        if(operation.equals("provenance")) {
            Object p=motor.provenance(); for(var change:deferred.entrySet()) { p=VehicleTestData.replace(p,change.getKey(),change.getValue()); } return p;
        }
        if(operation.equals("sample")) {
            return switch(kind) { case "thrust" -> new ThrustSample(time,sampleValue); case "mass" -> new MotorMassSample(time,sampleValue); case "cg" -> new MotorCgSample(time,sampleValue); default -> throw new AssertionError(kind); };
        }
        if(operation.equals("analyze")) { return new MotorCurveAnalyzer().analyze(motor); }
        if(operation.equals("thrust")) { return new MotorThrustCurveEvaluator().evaluate(motor,time); }
        if(operation.equals("profile")) { return new MotorPropertyModelProfile(massModel,cgModel); }
        var installation=new MotorInstallationResolver().resolve(geometry,motor);
        for(var change:deferred.entrySet()) {
            if(change.getKey().startsWith("installation.")) { installation=(MotorInstallation)VehicleTestData.replace(installation,change.getKey().substring(13),change.getValue()); }
        }
        if(operation.equals("install")) { return installation; }
        var profile=new MotorPropertyModelProfile(massModel,cgModel);
        var properties=new MotorPropertyEvaluator().evaluate(installation,profile,time);
        if(operation.equals("properties")) { return properties; }
        if(!operation.equals("total")) { throw new AssertionError(operation); }
        var structure=RocketMassTest.structure();
        for(var change:deferred.entrySet()) {
            if(change.getKey().startsWith("structure.")) { structure=(StructuralMassProperties)VehicleTestData.replace(structure,change.getKey().substring(10),change.getValue()); }
            else if(change.getKey().startsWith("properties.")) { properties=(MotorMassProperties)VehicleTestData.replace(properties,change.getKey().substring(11),change.getValue()); }
        }
        return new RocketMassPropertiesCalculator().evaluate(structure,properties);
    }
    static void flatten(Map<String,Object> output,String prefix,Object value) throws ReflectiveOperationException {
        if(value instanceof List<?> list) {
            output.put(prefix+".size",list.size());
            for(int i=0;i<list.size();i++) { flatten(output,prefix+"."+i,list.get(i)); }
        } else if(value!=null && value.getClass().isRecord()) {
            for(RecordComponent field:value.getClass().getRecordComponents()) { flatten(output,prefix+"."+field.getName(),field.getAccessor().invoke(value)); }
        } else { output.put(prefix,value); }
    }
    static void output(String status,Map<String,Object> fields) {
        StringBuilder line=new StringBuilder(status);
        for(var field:fields.entrySet()) { line.append('\t').append(field.getKey()).append('=').append(VehicleParityProbe.encoded(field.getValue())); }
        System.out.println(line);
    }
    public static void main(String[] args) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                Map<String,Object> fields=new LinkedHashMap<>();
                try { flatten(fields,"result",evaluate(line.split("\t"))); output("OK",fields); }
                catch(IllegalArgumentException|NoSuchElementException error) {
                    String category="ValueError",code=null,field=null; Object value=null;
                    if(error instanceof MotorValidationError e) { category="MotorValidationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof MotorInstallationError e) { category="MotorInstallationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof PropulsionEvaluationError e) { category="PropulsionEvaluationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof MassValidationError e) { category="MassValidationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof NoSuchElementException) { category="KeyError"; value=error.getMessage(); }
                    fields.put("error.category",category); fields.put("error.code",code); fields.put("error.field",field);
                    flatten(fields,"error.value",value);
                    fields.put("error.context",category.equals("ValueError")?error.getMessage().split(" ")[0]:null);
                    output("ERROR",fields);
                }
            }
        }
    }
}
