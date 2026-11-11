package org.roketsim.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.environment.DryAirProperties;
import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.RocketMassProperties;

/** Test-only wire adapter; no parsing or reflection in production aero calculations. */
public final class AerodynamicsParityProbe {
    private AerodynamicsParityProbe() { }
    @SuppressWarnings({"unchecked","rawtypes"})
    static Object decode(String wire) throws ReflectiveOperationException {
        if(wire.equals("profile:static")) return StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE;
        if(wire.equals("profile:drag")) return BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE;
        if(wire.startsWith("e:")) {
            String[] pair=wire.substring(2).split("\\.",2);
            try { return Enum.valueOf((Class)Class.forName("org.roketsim.core.aerodynamics."+pair[0]),pair[1]); }
            catch(ClassNotFoundException absent) { return VehicleParityProbe.decoded(wire); }
        }
        return VehicleParityProbe.decoded(wire);
    }
    static Object evaluate(String[] tokens) throws ReflectiveOperationException {
        String op=tokens[0];
        var geometry=AerodynamicsTest.geometry();
        var flight=AerodynamicsTest.flight(.3);
        var air=AerodynamicsTest.air();
        var surfaces=AerodynamicsTest.surfaces(0,0,0);
        Object dp=BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE;
        Object sp=StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE;
        double cp=.7,cg=.65,mach=.75,aspect=geometry.finAspectRatio(); int count=4;
        String finishName="Synthetic"; double rough=0;
        for(int i=1;i<tokens.length;i++) {
            String[] entry=tokens[i].split("=",2); String path=entry[0]; Object value=decode(entry[1]);
            if(path.startsWith("geometry.")) geometry=(ResolvedRocketGeometry)VehicleTestData.replace(geometry,path.substring(9),value);
            else if(path.startsWith("source.")) geometry=new GeometryResolver().resolve((SingleStageRocketGeometry)VehicleTestData.replace(geometry.source(),path.substring(7),value));
            else if(path.startsWith("flight.")) flight=(BasicFlightConditions)VehicleTestData.replace(flight,path.substring(7),value);
            else if(path.startsWith("air.")) air=(DryAirProperties)VehicleTestData.replace(air,path.substring(4),value);
            else if(path.startsWith("surfaces.")) surfaces=(SingleStageRocketAerodynamicSurfaces)VehicleTestData.replace(surfaces,path.substring(9),value);
            else if(path.startsWith("dp.")) dp=VehicleTestData.replace(dp,path.substring(3),value);
            else if(path.startsWith("sp.")) sp=VehicleTestData.replace(sp,path.substring(3),value);
            else switch(path) {
                case "dp" -> dp=value;
                case "sp" -> sp=value;
                case "cp" -> cp=((Number)value).doubleValue();
                case "cg" -> cg=((Number)value).doubleValue();
                case "name" -> finishName=(String)value;
                case "rough" -> rough=((Number)value).doubleValue();
                case "mach" -> mach=((Number)value).doubleValue();
                case "aspect" -> aspect=((Number)value).doubleValue();
                case "count" -> count=((Number)value).intValue();
                default -> throw new AssertionError(path);
            }
        }
        return switch(op) {
            case "surface" -> new SurfaceFinish(finishName,rough);
            case "dp" -> dp;
            case "sp" -> sp;
            case "geometry" -> geometry;
            case "drag" -> new BasicDragEvaluator().evaluate(geometry,surfaces,flight,air,dp);
            case "static" -> new StaticAerodynamicEvaluator().evaluate(geometry,flight,sp);
            case "margin","chain" -> {
                var contribution=new StaticAeroContribution(1,cp);
                var stat=new StaticAerodynamicProperties(contribution,contribution,2,cp,null);
                var mass=new RocketMassProperties(1,cg);
                if(op.equals("chain")) {
                    stat=new StaticAerodynamicEvaluator().evaluate(geometry,flight,sp);
                    var installation=new org.roketsim.core.propulsion.MotorInstallationResolver().resolve(geometry,org.roketsim.core.propulsion.MotorCatalogs.AEROTECH_F50_4T);
                    var motor=new org.roketsim.core.propulsion.MotorPropertyEvaluator().evaluate(installation,org.roketsim.core.propulsion.MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,1.4);
                    var structure=new org.roketsim.core.mass.StructuralMassPropertiesCalculator().evaluate(geometry,VehicleTestData.materials());
                    mass=new org.roketsim.core.mass.RocketMassPropertiesCalculator().evaluate(structure,motor);
                }
                yield new StaticMarginCalculator().evaluate(stat,mass,geometry);
            }
            case "diagnostics" -> {
                var factor=StaticAerodynamicEvaluator.class.getDeclaredMethod("finFinInterferenceFactor",double.class);
                var fraction=StaticAerodynamicEvaluator.class.getDeclaredMethod("finCpFraction",double.class,double.class);
                factor.setAccessible(true); fraction.setAccessible(true);
                yield List.of(factor.invoke(null,(double)count),fraction.invoke(null,mach,aspect));
            }
            default -> throw new AssertionError(op);
        };
    }
    public static void main(String[] args) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                Map<String,Object> fields=new LinkedHashMap<>();
                try {
                    Object result=evaluate(line.split("\t"));
                    PropulsionParityProbe.flatten(fields,"result",result);
                    if(result instanceof BasicDragResult drag) {
                        fields.put("result.frictionCd",drag.frictionCd()); fields.put("result.pressureCd",drag.pressureCd()); fields.put("result.baseCd",drag.baseCd());
                    }
                    PropulsionParityProbe.output("OK",fields);
                } catch(IllegalArgumentException error) {
                    String category="ValueError",code=null,field=null; Object value=null;
                    if(error instanceof SurfaceValidationError e) { category="SurfaceValidationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof AerodynamicEvaluationError e) { category="AerodynamicEvaluationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof GeometryValidationError e) { category="GeometryValidationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    fields.put("error.category",category); fields.put("error.code",code); fields.put("error.field",field);
                    PropulsionParityProbe.flatten(fields,"error.value",value);
                    fields.put("error.context",category.equals("ValueError")?error.getMessage().split(" ")[0]:null);
                    PropulsionParityProbe.output("ERROR",fields);
                }
            }
        }
    }
}
