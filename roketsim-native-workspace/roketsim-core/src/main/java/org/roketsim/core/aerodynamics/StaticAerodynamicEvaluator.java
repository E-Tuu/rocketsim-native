package org.roketsim.core.aerodynamics;

import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.geometry.FinAngularArrangement;
import org.roketsim.core.geometry.ResolvedRocketGeometry;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Accepted alpha-to-zero Extended-Barrowman evaluator; {@code 0 <= Mach < 0.8}. */
public final class StaticAerodynamicEvaluator {
    public StaticAerodynamicProperties evaluate(ResolvedRocketGeometry geometry,
            BasicFlightConditions flightConditions,Object modelProfile) {
        // Same bounded str-enum equivalence as Python; return the original profile metadata.
        if(!(modelProfile instanceof StaticStabilityModelProfile profile)
                || !(profile.normalForceModel()==StaticNormalForceModel.EXTENDED_BARROWMAN_SUBSONIC
                    || StaticNormalForceModel.EXTENDED_BARROWMAN_SUBSONIC.value().equals(profile.normalForceModel()))
                || !(profile.finCenterOfPressureModel()==FinCenterOfPressureModel.EXTENDED_BARROWMAN_MACH_DEPENDENT
                    || FinCenterOfPressureModel.EXTENDED_BARROWMAN_MACH_DEPENDENT.value().equals(profile.finCenterOfPressureModel()))
                || !(profile.bodyLiftTreatment()==BodyLiftTreatment.LINEARIZED_ZERO_AOA
                    || BodyLiftTreatment.LINEARIZED_ZERO_AOA.value().equals(profile.bodyLiftTreatment()))
                || profile.maximumSupportedMachExclusive()!=StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE.maximumSupportedMachExclusive()) {
            fail("UNSUPPORTED_STATIC_STABILITY_PROFILE","model_profile",modelProfile);
        }
        var profile=(StaticStabilityModelProfile)modelProfile;
        double mach=requireFinite(flightConditions.mach(),"mach");
        if(mach<0) fail("NEGATIVE_STATIC_STABILITY_MACH","mach",mach);
        if(mach>=profile.maximumSupportedMachExclusive()) fail("UNSUPPORTED_STATIC_STABILITY_MACH_REGIME","mach",mach);
        double referenceArea=positive(geometry.referenceAreaM2(),"reference_area_m2");
        double noseArea=positive(geometry.noseFrontalAreaM2(),"nose_frontal_area_m2");
        double noseLength=positive(geometry.noseAxialLengthM(),"nose_axial_length_m");
        double span=positive(geometry.source().fins().semiSpanM(),"fins.semi_span_m");
        double finArea=positive(geometry.finPlanformAreaPerFinM2(),"fin_planform_area_per_fin_m2");
        double sweep=requireFinite(geometry.finMidchordSweepAngleRad(),"fin_midchord_sweep_angle_rad");
        double mac=positive(geometry.finMeanAerodynamicChordM(),"fin_mean_aerodynamic_chord_m");
        double macLe=requireFinite(geometry.finMeanAerodynamicChordLeadingEdgeXGeoM(),"fin_mean_aerodynamic_chord_leading_edge_x_geo_m");
        double aspect=positive(geometry.finAspectRatio(),"fin_aspect_ratio");
        double radius=positive(geometry.finBodyRadiusAtRootM(),"fin_body_radius_at_root_m");
        if(geometry.finAngularArrangement()!=FinAngularArrangement.EQUALLY_SPACED) fail("UNSUPPORTED_FIN_ANGULAR_ARRANGEMENT","fin_angular_arrangement",geometry.finAngularArrangement());
        Object rawCount=geometry.source().fins().finCount();
        boolean integral=rawCount instanceof Byte || rawCount instanceof Short || rawCount instanceof Integer
            || rawCount instanceof Long || rawCount instanceof java.math.BigInteger;
        if(!integral || ((Number)rawCount).doubleValue()<3) fail("UNSUPPORTED_FIN_COUNT","fins.fin_count",rawCount);
        double count=((Number)rawCount).doubleValue();
        double noseCna=2.0*noseArea/referenceArea;
        double noseCp=(2.0/3.0)*noseLength;
        double sweepCosine=Math.cos(sweep);
        double denominator=finArea*sweepCosine;
        if(!Double.isFinite(denominator)||denominator<=0) fail("INVALID_FIN_MIDCHORD_SWEEP","fin_midchord_sweep_angle_rad",sweep);
        double spanSquared=span*span;
        double single=2.0*Math.PI*spanSquared/referenceArea
            /(1.0+Math.sqrt(1.0+(1.0-mach*mach)*Math.pow(spanSquared/denominator,2)));
        double bare=(count/2.0)*single;
        // One current set: N and total interfering parallel Ntot coincide only in V1.
        double totalInterferingParallelFins=count;
        double interference=finFinInterferenceFactor(totalInterferingParallelFins);
        double bodyToFin=1.0+radius/(span+radius);
        double finCna=bare*interference*bodyToFin;
        double fraction=finCpFraction(mach,aspect);
        double finCp=macLe+fraction*mac;
        String[] names={"nose_cna_per_rad","fin_single_cna_per_rad","fin_set_cna_per_rad"};
        double[] values={noseCna,single,finCna};
        for(int i=0;i<values.length;i++) if(!Double.isFinite(values[i])||values[i]<=0) fail("INVALID_STATIC_CNA",names[i],values[i]);
        if(!Double.isFinite(noseCp)) fail("INVALID_STATIC_CP","nose_cp_x_geo_m",noseCp);
        if(!Double.isFinite(finCp)) fail("INVALID_STATIC_CP","fin_cp_x_geo_m",finCp);
        double total=noseCna+finCna;
        double cp=(noseCna*noseCp+finCna*finCp)/total;
        if(!Double.isFinite(total)||total<=0) fail("INVALID_TOTAL_STATIC_CNA","total_cna_per_rad",total);
        if(!Double.isFinite(cp)) fail("INVALID_TOTAL_STATIC_CP","cp_x_geo_m",cp);
        return new StaticAerodynamicProperties(new StaticAeroContribution(noseCna,noseCp),new StaticAeroContribution(finCna,finCp),total,cp,profile);
    }
    private static double finFinInterferenceFactor(double total) {
        if(total<=4) return 1.000;
        if(total==5) return .948;
        if(total==6) return .913;
        if(total==7) return .854;
        if(total==8) return .810;
        return .750;
    }
    private static double finCpFraction(double mach,double aspect) {
        if(mach<=.5) return .25;
        double rootThree=Math.sqrt(3.0);
        double denominator=2.0*aspect*rootThree-1.0;
        double endpoint=(aspect*rootThree-.67)/denominator;
        double endpointDerivative=.34*aspect*2.0/(rootThree*denominator*denominator);
        double delta=endpoint-.25;
        double scaled=1.5*endpointDerivative;
        double u=(mach-.5)/1.5;
        return .25+(10.0*delta-6.0*scaled)*Math.pow(u,2)
            +(-20.0*delta+14.0*scaled)*Math.pow(u,3)
            +(15.0*delta-11.0*scaled)*Math.pow(u,4)
            +(-4.0*delta+3.0*scaled)*Math.pow(u,5);
    }
    private static double positive(double value,String field) {
        double result=requireFinite(value,field);
        if(result<=0) fail("INVALID_STATIC_GEOMETRY_PREREQUISITE",field,result);
        return result;
    }
    private static void fail(String code,String field,Object value) { throw new AerodynamicEvaluationError(code,field,value); }
}
