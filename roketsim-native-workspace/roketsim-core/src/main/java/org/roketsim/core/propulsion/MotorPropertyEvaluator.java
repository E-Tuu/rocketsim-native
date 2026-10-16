package org.roketsim.core.propulsion;
import java.util.List;
import static org.roketsim.core.math.NumericalValidation.requireFinite;
import static org.roketsim.core.propulsion.MotorThrustCurveEvaluator.fail;
/** Stateless policy evaluator; impulse calculation is delegated, not duplicated. */
public final class MotorPropertyEvaluator {
    private static double sourceValue(List<?> curve,double time,boolean mass) {
        for(int i=0;i<curve.size();i++) {
            Object sample=curve.get(i); double t=sampleTime(sample),value=sampleValue(sample,mass);
            if(time==t) { return value; }
            if(time<t) {
                Object before=curve.get(i-1); double previous=sampleValue(before,mass);
                return previous+(value-previous)*((time-sampleTime(before))/(t-sampleTime(before)));
            }
        }
        return sampleValue(curve.get(curve.size()-1),mass);
    }
    private static double sampleTime(Object s) { return s instanceof MotorMassSample a?a.timeS():((MotorCgSample)s).timeS(); }
    private static double sampleValue(Object s,boolean mass) { return mass?((MotorMassSample)s).massKg():((MotorCgSample)s).cgFromFrontM(); }
    public MotorMassProperties evaluate(MotorInstallation installation,Object modelProfile,double motorTimeS) {
        double time=requireFinite(motorTimeS,"motor_time_s");
        if(time<0) { fail("NEGATIVE_MOTOR_TIME","motor_time_s",time); }
        if(!(modelProfile instanceof MotorPropertyModelProfile)) { fail("INVALID_MOTOR_PROPERTY_MODEL","model_profile",modelProfile); }
        var profile=(MotorPropertyModelProfile)modelProfile; var motor=installation.motor();
        double mass;
        if(profile.massModel()==MotorMassEvolutionModel.EXPLICIT_CURVE) {
            if(motor.massCurve()==null) { fail("EXPLICIT_MASS_CURVE_REQUIRED","mass_curve",null); }
            mass=sourceValue(motor.massCurve(),time,true);
        } else {
            double impulse=new MotorThrustCurveEvaluator().evaluate(motor,time).cumulativeImpulseNS();
            double total=new MotorCurveAnalyzer().analyze(motor).totalImpulseNS();
            if(!Double.isFinite(total)||total<=0) { fail("INVALID_IMPULSE_FRACTION","total_impulse_N_s",total); }
            double fraction=impulse/total;
            if(!Double.isFinite(fraction)||!(0<=fraction && fraction<=1)) { fail("INVALID_IMPULSE_FRACTION","impulse_fraction",fraction); }
            mass=motor.initialMassKg()-motor.propellantMassKg()*fraction;
        }
        if(!Double.isFinite(mass)||mass<=0) { fail("INVALID_MOTOR_MASS_RESULT","mass_kg",mass); }
        double local;
        if(profile.cgModel()==MotorCgEvolutionModel.EXPLICIT_CURVE) {
            if(motor.cgCurve()==null) { fail("EXPLICIT_CG_CURVE_REQUIRED","cg_curve",null); }
            local=sourceValue(motor.cgCurve(),time,false);
        } else { local=motor.lengthM()/2.0; }
        if(!Double.isFinite(local)||!(0<=local && local<=motor.lengthM())) { fail("INVALID_MOTOR_CG_LOCAL_RESULT","cg_local_from_front_m",local); }
        double cg=installation.motorFrontXGeoM()+local;
        if(!Double.isFinite(cg)) { fail("INVALID_MOTOR_CG_X_GEO_RESULT","cg_x_geo_m",cg); }
        return new MotorMassProperties(mass,local,cg,profile);
    }
}
