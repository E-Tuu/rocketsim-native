package org.roketsim.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;

/** Test transport only: synthetic derivatives, raw stage traces, real production integrator. */
public final class NumericsParityProbe {
    private NumericsParityProbe() { }
    private static final class Sentinel extends RuntimeException {
        private static final long serialVersionUID=1L;
    }
    static Map<String,Object> defaults() {
        Map<String,Object> v=new LinkedHashMap<>();
        v.put("time",2.); v.put("h",.2); v.put("scale",.5);
        v.put("position",new double[]{1,2,3}); v.put("velocity",new double[]{4,5,6});
        v.put("position_rate",new double[]{4,5,6}); v.put("velocity_rate",new double[]{1,2,3});
        v.put("callback","constant"); v.put("fail_stage",0.);
        return v;
    }
    static TranslationalStateDerivative3DOF callback(Map<String,Object> v,double time,TranslationalState3DOF state,int count) {
        int fail=(int)DynamicsParityProbe.number(v,"fail_stage");
        if(count==fail) {
            if(v.get("callback").equals("null"))return null;
            throw new Sentinel();
        }
        double[] p=state.positionWorldM().toArray(), velocity=state.velocityWorldMS().toArray();
        double[] acceleration=new double[3];
        switch((String)v.get("callback")) {
            case "oscillator":
                for(int i=0;i<3;i++)acceleration[i]=-p[i];
                return new TranslationalStateDerivative3DOF(velocity,acceleration);
            case "acceleration": return new TranslationalStateDerivative3DOF(velocity,v.get("velocity_rate"));
            case "time":
                for(int i=0;i<3;i++) { velocity[i]=time*(i+1); acceleration[i]=-time*(i+1); }
                return new TranslationalStateDerivative3DOF(velocity,acceleration);
            case "sequence":
                for(int i=0;i<3;i++) { velocity[i]=(count==4?6e307:0); }
                return new TranslationalStateDerivative3DOF(velocity,acceleration);
            case "constant", "sentinel", "null":
                return new TranslationalStateDerivative3DOF(v.get("position_rate"),v.get("velocity_rate"));
            default: throw new AssertionError("Unknown callback");
        }
    }
    public static void main(String[] args) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                String[] tokens=line.split("\t"); Map<String,Object> v=defaults();
                for(int i=1;i<tokens.length;i++) {
                    String[] pair=tokens[i].split("=",2);
                    if(!v.containsKey(pair[0])) throw new AssertionError("Unknown input "+pair[0]);
                    v.put(pair[0],DynamicsParityProbe.decode(pair[1]));
                }
                var trace=new ArrayList<IntegrationPoint3DOF>();
                Map<String,Object> output=new LinkedHashMap<>(); String status="OK";
                try {
                    Object result;
                    switch(tokens[0]) {
                        case "config": result=new FixedStepConfig(DynamicsParityProbe.number(v,"h")); break;
                        case "point": {
                            var state=DynamicsParityProbe.state(v);
                            var point=new IntegrationPoint3DOF(DynamicsParityProbe.number(v,"time"),state);
                            output.put("contract.stateIdentity",point.state()==state); result=point; break;
                        }
                        case "algebra": result=new TranslationalStateAlgebra3DOF().addScaledDerivative(DynamicsParityProbe.state(v),
                            new TranslationalStateDerivative3DOF(v.get("position_rate"),v.get("velocity_rate")),DynamicsParityProbe.number(v,"scale")); break;
                        case "step": {
                            var original=DynamicsParityProbe.state(v);
                            var point=new IntegrationPoint3DOF(DynamicsParityProbe.number(v,"time"),original);
                            var config=new FixedStepConfig(DynamicsParityProbe.number(v,"h"));
                            result=new ClassicalRK4Integrator3DOF().step(point,config,(t,s)->{
                                trace.add(new IntegrationPoint3DOF(t,s)); return callback(v,t,s,trace.size());
                            }); break;
                        }
                        default: throw new AssertionError(tokens[0]);
                    }
                    DynamicsParityProbe.flatten(output,"result",result);
                } catch(IllegalArgumentException|NullPointerException|Sentinel error) {
                    output.clear(); status="ERROR";
                    String category="ValueError",code=null,field=null; Object value=null;
                    if(error instanceof NumericalIntegrationError e) { category="NumericalIntegrationError"; code=e.errorCode(); field=e.fieldName(); value=e.value(); }
                    else if(error instanceof NullPointerException) category="TypeError";
                    else if(error instanceof Sentinel) category="SentinelDerivativeError";
                    output.put("error.category",category); output.put("error.code",code); output.put("error.field",field);
                    DynamicsParityProbe.flatten(output,"error.value",value);
                    output.put("error.context",category.equals("ValueError")?error.getMessage().split(" ")[0]:null);
                }
                output.put("callbackCount",trace.size()); DynamicsParityProbe.flatten(output,"trace",trace);
                PropulsionParityProbe.output(status,output);
            }
        }
    }
}
