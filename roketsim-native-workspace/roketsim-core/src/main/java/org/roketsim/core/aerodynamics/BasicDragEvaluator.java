package org.roketsim.core.aerodynamics;

import org.roketsim.core.environment.DryAirProperties;
import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.geometry.FinCrossSection;
import org.roketsim.core.geometry.ResolvedRocketGeometry;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless accepted zero-AoA Basic Drag V1. Runtime domain: {@code 0 <= Mach <= 1}. */
public final class BasicDragEvaluator {
    public BasicDragResult evaluate(ResolvedRocketGeometry geometry,
            SingleStageRocketAerodynamicSurfaces aerodynamicSurfaces,
            BasicFlightConditions flightConditions,DryAirProperties airProperties,Object modelProfile) {
        // Python str-enum dataclass equality accepts the exact canonical metadata string.
        // Keep the supplied profile intact; no parsing, trimming or global record equality override.
        if(!(modelProfile instanceof BasicDragModelProfile profile)
                || !(profile.boundaryLayerModel()==BoundaryLayerModel.FULLY_TURBULENT
                    || BoundaryLayerModel.FULLY_TURBULENT.value().equals(profile.boundaryLayerModel()))
                || !(profile.reynoldsLengthPolicy()==ReynoldsLengthPolicy.AERODYNAMIC_LENGTH
                    || ReynoldsLengthPolicy.AERODYNAMIC_LENGTH.value().equals(profile.reynoldsLengthPolicy()))
                || !(profile.lowReynoldsContinuation()==LowReynoldsContinuation.MINIMUM_EVALUATION_REYNOLDS
                    || LowReynoldsContinuation.MINIMUM_EVALUATION_REYNOLDS.value().equals(profile.lowReynoldsContinuation()))
                || profile.minimumFrictionEvaluationReynolds()!=BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE.minimumFrictionEvaluationReynolds()
                || !(profile.nosePressureModel()==NosePressureModel.TD13_CONICAL_SUBSONIC_TO_SONIC
                    || NosePressureModel.TD13_CONICAL_SUBSONIC_TO_SONIC.value().equals(profile.nosePressureModel()))
                || !(profile.finEdgeModel()==FinEdgeModel.TD13_SQUARE
                    || FinEdgeModel.TD13_SQUARE.value().equals(profile.finEdgeModel()))
                || !(profile.baseDragModel()==BaseDragModel.TD13_SUBSONIC_TO_SONIC
                    || BaseDragModel.TD13_SUBSONIC_TO_SONIC.value().equals(profile.baseDragModel()))
                || !(profile.plumeTreatment()==PlumeTreatment.IGNORED
                    || PlumeTreatment.IGNORED.value().equals(profile.plumeTreatment()))) {
            fail("UNSUPPORTED_BASIC_DRAG_PROFILE","model_profile",modelProfile);
        }
        var profile=(BasicDragModelProfile)modelProfile;
        var fins=geometry.source().fins();
        double airspeed=requireFinite(flightConditions.airspeedMS(),"airspeed_m_s");
        double mach=requireFinite(flightConditions.mach(),"mach");
        double nu=requireFinite(airProperties.kinematicViscosityM2S(),"kinematic_viscosity_m2_s");
        double gamma=requireFinite(airProperties.specificHeatRatio(),"specific_heat_ratio");
        double length=requireFinite(geometry.aerodynamicLengthM(),"aerodynamic_length_m");
        if(airspeed<0) fail("NEGATIVE_AIRSPEED","airspeed_m_s",airspeed);
        if(mach<0) fail("NEGATIVE_MACH","mach",mach);
        if(mach>1) fail("UNSUPPORTED_MACH_REGIME","mach",mach);
        if(nu<=0) fail("NON_POSITIVE_KINEMATIC_VISCOSITY","kinematic_viscosity_m2_s",nu);
        if(gamma<=1) fail("INVALID_SPECIFIC_HEAT_RATIO","specific_heat_ratio",gamma);
        if(length<=0) fail("INVALID_AERODYNAMIC_LENGTH","aerodynamic_length_m",length);
        if(geometry.finCrossSection()!=FinCrossSection.SQUARE) fail("UNSUPPORTED_FIN_CROSS_SECTION","fin_cross_section",geometry.finCrossSection());
        double reynolds=airspeed*length/nu;
        if(!Double.isFinite(reynolds)||reynolds<0) fail("INVALID_REYNOLDS_NUMBER","reynolds_number",reynolds);
        double evaluationReynolds=Math.max(reynolds,profile.minimumFrictionEvaluationReynolds());
        double smoothCf=1.0/Math.pow(1.50*Math.log(evaluationReynolds)-5.6,2);
        double compressibility=1.0-.10*mach*mach;
        double smoothCorrected=smoothCf*compressibility;
        var nose=skinFriction(smoothCorrected,aerodynamicSurfaces.nose().equivalentRoughnessM(),length,compressibility);
        var body=skinFriction(smoothCorrected,aerodynamicSurfaces.body().equivalentRoughnessM(),length,compressibility);
        var fin=skinFriction(smoothCorrected,aerodynamicSurfaces.fins().equivalentRoughnessM(),length,compressibility);
        double area=requireFinite(geometry.referenceAreaM2(),"reference_area_m2");
        double diameter=requireFinite(geometry.maxExternalAirframeDiameterM(),"max_external_airframe_diameter_m");
        double bodyLength=requireFinite(geometry.axisymmetricBodyLengthM(),"axisymmetric_body_length_m");
        double mac=requireFinite(geometry.finMeanAerodynamicChordM(),"fin_mean_aerodynamic_chord_m");
        double noseWetted=requireFinite(geometry.noseWettedAreaM2(),"nose_wetted_area_m2");
        double bodyWetted=requireFinite(geometry.bodyWettedAreaM2(),"body_wetted_area_m2");
        double finArea=requireFinite(geometry.finPlanformAreaPerFinM2(),"fin_planform_area_per_fin_m2");
        double noseFrontal=requireFinite(geometry.noseFrontalAreaM2(),"nose_frontal_area_m2");
        double baseArea=requireFinite(geometry.airframeAftBaseAreaM2(),"airframe_aft_base_area_m2");
        double phi=requireFinite(geometry.noseHalfAngleRad(),"nose_half_angle_rad");
        double sweep=requireFinite(geometry.finLeadingEdgeSweepAngleRad(),"fin_leading_edge_sweep_angle_rad");
        double thickness=requireFinite(fins.thicknessM(),"fins.thickness_m");
        double span=requireFinite(fins.semiSpanM(),"fins.semi_span_m");
        String[] names={"reference_area_m2","max_external_airframe_diameter_m","axisymmetric_body_length_m","fin_mean_aerodynamic_chord_m","nose_wetted_area_m2","body_wetted_area_m2","fin_planform_area_per_fin_m2","nose_frontal_area_m2","airframe_aft_base_area_m2","fins.thickness_m","fins.semi_span_m"};
        double[] values={area,diameter,bodyLength,mac,noseWetted,bodyWetted,finArea,noseFrontal,baseArea,thickness,span};
        for(int i=0;i<values.length;i++) if(values[i]<=0) fail("INVALID_GEOMETRY_PREREQUISITE",names[i],values[i]);
        double fineness=bodyLength/diameter;
        double bodyCorrection=1.0+1.0/(2.0*fineness);
        double finCorrection=1.0+2.0*thickness/mac;
        double noseFriction=bodyCorrection*nose.selectedCf()*noseWetted/area;
        double bodyFriction=bodyCorrection*body.selectedCf()*bodyWetted/area;
        double finCount=((Number)fins.finCount()).doubleValue();
        double finFriction=fin.selectedCf()*finCorrection*(2.0*finCount*finArea)/area;
        double sine=Math.sin(phi);
        double c0=.8*sine*sine;
        double c1=sine;
        double d1=4.0/(gamma+1.0)*(1.0-c1/2.0);
        double amplitude=c1-c0;
        if(amplitude<=0) fail("INVALID_NOSE_PRESSURE_GEOMETRY","nose_half_angle_rad",phi);
        double exponent=d1/amplitude;
        double nosePressureStar=c0+amplitude*Math.pow(mach,exponent);
        double nosePressure=nosePressureStar*noseFrontal/area;
        double edge=finCount*thickness*span/area;
        double stagnation=.85*(1.0+mach*mach/4.0+Math.pow(mach,4)/40.0);
        double leading=edge*stagnation*Math.pow(Math.cos(sweep),2);
        double baseStar=.12+.13*mach*mach;
        double trailing=edge*baseStar;
        double base=baseStar*baseArea/area;
        double[] contributions={noseFriction,bodyFriction,finFriction,nosePressure,leading,trailing,base};
        String[] contributionNames={"nose_friction_cd","body_friction_cd","fin_friction_cd","nose_pressure_cd","fin_leading_edge_pressure_cd","fin_trailing_edge_base_cd","airframe_base_cd"};
        for(int i=0;i<contributions.length;i++) if(!Double.isFinite(contributions[i])||contributions[i]<0) fail("INVALID_DRAG_CONTRIBUTION",contributionNames[i],contributions[i]);
        // Python 3.12 sum(float iterable) uses compensated accumulation, not a grouped formula.
        double high=0,low=0;
        for(double value:contributions) {
            double next=high+value;
            low+=Math.abs(high)>=Math.abs(value)?(high-next)+value:(value-next)+high;
            high=next;
        }
        double total=low!=0 && Double.isFinite(low)?high+low:high;
        if(!Double.isFinite(total)||total<0) fail("INVALID_TOTAL_CD0","total_cd0",total);
        return new BasicDragResult(reynolds,evaluationReynolds,nose,body,fin,noseFriction,bodyFriction,
            finFriction,nosePressure,leading,trailing,base,total,profile);
    }
    private static SkinFrictionEvaluation skinFriction(double smooth,double roughnessInput,double length,double compressibility) {
        double roughness=requireFinite(roughnessInput,"equivalent_roughness_m");
        if(roughness<0) fail("NEGATIVE_EQUIVALENT_ROUGHNESS","equivalent_roughness_m",roughness);
        double rough=roughness==0?0:.032*Math.pow(roughness/length,.2)*compressibility;
        if(!Double.isFinite(smooth)||smooth<0) fail("INVALID_SKIN_FRICTION","smooth_corrected_cf",smooth);
        if(!Double.isFinite(rough)||rough<0) fail("INVALID_SKIN_FRICTION","roughness_corrected_cf",rough);
        return rough>smooth?new SkinFrictionEvaluation(smooth,rough,rough,SkinFrictionBranch.ROUGHNESS_LIMITED)
            :new SkinFrictionEvaluation(smooth,rough,smooth,SkinFrictionBranch.SMOOTH);
    }
    private static void fail(String code,String field,Object value) { throw new AerodynamicEvaluationError(code,field,value); }
}
