package org.roketsim.core.propulsion;

import java.util.List;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Immutable SI definition; curve grids and policy selection remain separate authorities. */
public record MotorDefinition(String motorId, String manufacturer, String designation, String family,
        Object motorType, String propellantName, double diameterM, double lengthM,
        double initialMassKg, double propellantMassKg, double ejectionDelayS,
        List<ThrustSample> thrustCurve, MotorCertificationReference certification,
        MotorDataProvenance provenance, List<MotorMassSample> massCurve, List<MotorCgSample> cgCurve) {
    public MotorDefinition {
        String[] names={"diameter_m","length_m","initial_mass_kg","propellant_mass_kg"};
        String[] codes={"NON_POSITIVE_MOTOR_DIAMETER","NON_POSITIVE_MOTOR_LENGTH","NON_POSITIVE_INITIAL_MASS","NON_POSITIVE_PROPELLANT_MASS"};
        double[] values={diameterM,lengthM,initialMassKg,propellantMassKg};
        for(int i=0;i<4;i++) { requireFinite(values[i],names[i]); }
        requireFinite(ejectionDelayS,"ejection_delay_s");
        String[] texts={motorId,manufacturer,designation,family,propellantName};
        String[] fields={"motor_id","manufacturer","designation","family","propellant_name"};
        String[] textCodes={"EMPTY_MOTOR_ID","EMPTY_MANUFACTURER","EMPTY_DESIGNATION","EMPTY_MOTOR_FAMILY","EMPTY_PROPELLANT_NAME"};
        for(int i=0;i<5;i++) { if(MotorDataProvenance.blank(texts[i])) { fail(textCodes[i],fields[i],texts[i]); } }
        if(!(motorType instanceof MotorType)) { fail("INVALID_MOTOR_TYPE","motor_type",motorType); }
        for(int i=0;i<4;i++) { if(values[i]<=0) { fail(codes[i],names[i],values[i]); } }
        if(propellantMassKg>=initialMassKg) { fail("PROPELLANT_MASS_NOT_LESS_THAN_INITIAL_MASS","propellant_mass_kg",propellantMassKg); }
        if(ejectionDelayS<0) { fail("NEGATIVE_EJECTION_DELAY","ejection_delay_s",ejectionDelayS); }
        if(certification==null) { fail("INVALID_CERTIFICATION_REFERENCE","certification",null); }
        if(provenance==null) { fail("INVALID_PROVENANCE","provenance",null); }
        if(thrustCurve==null || ((List<?>)thrustCurve).stream().anyMatch(s->!(s instanceof ThrustSample))) {
            fail("INVALID_THRUST_CURVE_TYPE","thrust_curve",thrustCurve);
        }
        if(thrustCurve.size()<3) { fail("TOO_FEW_THRUST_SAMPLES","thrust_curve",thrustCurve.size()); }
        ThrustSample first=thrustCurve.get(0),last=thrustCurve.get(thrustCurve.size()-1);
        if(first.timeS()!=0 || first.thrustN()!=0) { fail("INVALID_THRUST_CURVE_START","thrust_curve",first); }
        for(int i=1;i<thrustCurve.size();i++) {
            if(thrustCurve.get(i).timeS()<=thrustCurve.get(i-1).timeS()) { fail("NON_INCREASING_THRUST_TIME","time_s",thrustCurve.get(i).timeS()); }
        }
        if(thrustCurve.stream().noneMatch(s->s.thrustN()>0)) { fail("NO_POSITIVE_THRUST","thrust_curve",thrustCurve); }
        if(last.timeS()<=0 || last.thrustN()!=0) { fail("INVALID_THRUST_CURVE_END","thrust_curve",last); }
        validateSource("MASS",massCurve,MotorMassSample.class,provenance.massCurveSource(),last.timeS());
        validateSource("CG",cgCurve,MotorCgSample.class,provenance.cgCurveSource(),last.timeS());
        if(massCurve!=null) {
            if(massCurve.get(0).massKg()!=initialMassKg) { fail("MASS_CURVE_INITIAL_MISMATCH","mass_curve",massCurve.get(0).massKg()); }
            for(int i=1;i<massCurve.size();i++) {
                if(massCurve.get(i).massKg()>massCurve.get(i-1).massKg()) { fail("MASS_CURVE_INCREASES","mass_curve",massCurve); }
            }
        }
        if(cgCurve!=null) {
            for(MotorCgSample sample:cgCurve) {
                if(!(0<=sample.cgFromFrontM() && sample.cgFromFrontM()<=lengthM)) { fail("CG_OUTSIDE_MOTOR","cg_from_front_m",sample.cgFromFrontM()); }
            }
        }
        thrustCurve=List.copyOf(thrustCurve);
        massCurve=massCurve==null?null:List.copyOf(massCurve);
        cgCurve=cgCurve==null?null:List.copyOf(cgCurve);
    }
    private static void validateSource(String code,List<?> samples,Class<?> type,String source,double end) {
        String field=code.toLowerCase(java.util.Locale.ROOT)+"_curve";
        if((samples==null)!=(source==null)) { fail(code+"_CURVE_PROVENANCE_MISMATCH",field,samples); }
        if(samples==null) { return; }
        if(samples.stream().anyMatch(s->!type.isInstance(s))) { fail("INVALID_"+code+"_CURVE_TYPE",field,samples); }
        if(samples.size()<2) { fail("TOO_FEW_"+code+"_SAMPLES",field,samples.size()); }
        if(time(samples.get(0))!=0) { fail("INVALID_"+code+"_CURVE_START",field,time(samples.get(0))); }
        for(int i=1;i<samples.size();i++) {
            if(time(samples.get(i))<=time(samples.get(i-1))) { fail("NON_INCREASING_"+code+"_CURVE_TIME",field,samples); }
        }
        if(time(samples.get(samples.size()-1))<end) { fail(code+"_CURVE_INSUFFICIENT_COVERAGE",field,time(samples.get(samples.size()-1))); }
    }
    private static double time(Object sample) {
        return sample instanceof MotorMassSample s?s.timeS():((MotorCgSample)sample).timeS();
    }
    private static void fail(String code,String field,Object value) { throw new MotorValidationError(code,field,value); }
}
