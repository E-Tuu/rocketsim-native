package org.roketsim.core.propulsion.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.roketsim.core.propulsion.ThrustSample;

/** Source units are mm, kg, seconds and newtons. No runtime motor is inferred. */
public record EngMotorRecord(Header header,List<ThrustSample> listedSamples,int headerLine) {
    private static final Pattern NUMBER=Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    public EngMotorRecord {
        if(header==null || listedSamples==null || headerLine<1)
            throw new IllegalArgumentException("header, listedSamples and headerLine are required");
        listedSamples=List.copyOf(listedSamples);
        if(listedSamples.size()<2) throw new IllegalArgumentException("curve requires at least two listed samples");
        double previous=-1;
        boolean positive=false;
        for(int i=0;i<listedSamples.size();i++) {
            var sample=listedSamples.get(i);
            if(sample.timeS()<=previous) throw new IllegalArgumentException("nonincreasing sample time at index "+i);
            if(sample.timeS()==0 && sample.thrustN()!=0)
                throw new IllegalArgumentException("explicit origin must be (0,0)");
            if(sample.thrustN()==0 && i<listedSamples.size()-1 && sample.timeS()!=0)
                throw new IllegalArgumentException("zero thrust before final curve termination at index "+i);
            positive|=sample.thrustN()>0;
            previous=sample.timeS();
        }
        var last=listedSamples.get(listedSamples.size()-1);
        if(!positive || last.timeS()<=0 || last.thrustN()!=0)
            throw new IllegalArgumentException("curve termination requires positive impulse and final zero thrust");
        integrate(listedSamples); // Reject overflow at import, not during flight.
    }

    /** Normalized transport data only; the original source samples/bytes remain separate. */
    public List<ThrustSample> normalizedSamples() {
        if(listedSamples.get(0).timeS()==0) return listedSamples;
        var result=new ArrayList<ThrustSample>(listedSamples.size()+1);
        result.add(new ThrustSample(0,0)); result.addAll(listedSamples);
        return List.copyOf(result);
    }
    /** Independent trapezoidal source-data check, including the implicit origin. */
    public double totalImpulseNS() { return integrate(listedSamples); }
    public double curveEndTimeS() { return listedSamples.get(listedSamples.size()-1).timeS(); }

    private static double integrate(List<ThrustSample> samples) {
        double time=0,thrust=0,total=0;
        for(var sample:samples) {
            double area=.5*(thrust+sample.thrustN())*(sample.timeS()-time);
            total+=area;
            if(!Double.isFinite(area) || !Double.isFinite(total))
                throw new IllegalArgumentException("total_impulse_N_s overflow");
            time=sample.timeS(); thrust=sample.thrustN();
        }
        if(total<=0) throw new IllegalArgumentException("total_impulse_N_s must be positive");
        return total;
    }

    static double number(String token,String field) {
        if(!NUMBER.matcher(token).matches()) throw new IllegalArgumentException(field+" invalid decimal token: "+token);
        double value=Double.parseDouble(token);
        if(!Double.isFinite(value)) throw new IllegalArgumentException(field+" must be finite: "+token);
        return value;
    }
    static String text(String value,String field) {
        if(value==null || value.isBlank()) throw new IllegalArgumentException(field+" must not be blank");
        return value;
    }

    /** Exactly seven unaltered header tokens; parsed values retain source units. */
    public record Header(List<String> tokens) {
        public Header {
            if(tokens==null || tokens.size()!=7) throw new IllegalArgumentException("ENG header requires seven fields");
            tokens=List.copyOf(tokens);
            for(var token:tokens) text(token,"header token");
            double diameter=number(tokens.get(1),"diameter_mm"),length=number(tokens.get(2),"length_mm");
            double propellant=number(tokens.get(4),"propellant_mass_kg"),loaded=number(tokens.get(5),"initial_mass_kg");
            if(diameter<=0 || length<=0) throw new IllegalArgumentException("nominal dimensions must be positive");
            if(propellant<0 || loaded<=0 || propellant>loaded)
                throw new IllegalArgumentException("invalid propellant/initial mass relationship");
        }
        public String designation() { return tokens.get(0); }
        public double nominalDiameterMm() { return number(tokens.get(1),"diameter_mm"); }
        public double nominalLengthMm() { return number(tokens.get(2),"length_mm"); }
        public double nominalDiameterM() { return nominalDiameterMm()/1000; }
        public double nominalLengthM() { return nominalLengthMm()/1000; }
        public String rawEjectionToken() { return tokens.get(3); }
        public double propellantMassKg() { return number(tokens.get(4),"propellant_mass_kg"); }
        public double initialMassKg() { return number(tokens.get(5),"initial_mass_kg"); }
        /** The header field is source-supplied; it is not verified physical manufacturer identity. */
        public String sourceProvider() { return tokens.get(6); }
        public EjectionMetadata ejectionMetadata() { return EjectionMetadata.fromSourceToken(rawEjectionToken()); }
    }

    public enum EjectionInterpretation {
        NUMERIC_SOURCE_TOKEN, NO_EJECTION_PLUGGED, MIXED_SOURCE_OPTIONS, UNRESOLVED_PROVIDER_TOKEN
    }
    /** Numeric options are source tokens, never a silently selected physical delay. */
    public record EjectionMetadata(String rawToken,EjectionInterpretation interpretation,
            List<Double> numericSourceOptionsS,boolean pluggedOption,String interpretationReference) {
        public EjectionMetadata {
            text(rawToken,"rawToken"); text(interpretationReference,"interpretationReference");
            if(interpretation==null || numericSourceOptionsS==null) throw new IllegalArgumentException("ejection metadata required");
            numericSourceOptionsS=List.copyOf(numericSourceOptionsS);
            for(double value:numericSourceOptionsS)
                if(!Double.isFinite(value) || value<0) throw new IllegalArgumentException("numericSourceOptionsS invalid");
        }
        static EjectionMetadata fromSourceToken(String raw) {
            var options=new ArrayList<Double>(); boolean plugged=false,unknown=false;
            for(String part:raw.split("-",-1)) {
                if(part.equals("P")) plugged=true;
                else if(part.matches("[0-9]+(?:\\.[0-9]+)?")) options.add(number(part,"delay token"));
                else unknown=true;
            }
            var kind=unknown?EjectionInterpretation.UNRESOLVED_PROVIDER_TOKEN:
                plugged?(options.isEmpty()?EjectionInterpretation.NO_EJECTION_PLUGGED:EjectionInterpretation.MIXED_SOURCE_OPTIONS):
                EjectionInterpretation.NUMERIC_SOURCE_TOKEN;
            return new EjectionMetadata(raw,kind,options,plugged,"https://www.thrustcurve.org/info/raspformat.html");
        }
    }
}
