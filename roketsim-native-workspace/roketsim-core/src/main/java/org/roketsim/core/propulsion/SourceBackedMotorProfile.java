package org.roketsim.core.propulsion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.roketsim.core.propulsion.catalog.CatalogMotorProfile;
import static org.roketsim.core.propulsion.catalog.CatalogMotorProfile.*;
import static org.roketsim.core.propulsion.MotorEjectionMetadata.Kind.*;

/** Explicit research binding. Catalog identity/aliases remain evidence, not verified assembly equivalence.
 * A valid binding is not physical installation, certification or flight-readiness approval. */
public record SourceBackedMotorProfile(CatalogMotorProfile catalogProfile,String selectedCurveId,MotorType motorType,
        MassObservation initialMassObservation,MassObservation propellantMassObservation,GeometryInterpretation geometryInterpretation,
        MotorEjectionMetadata ejection,Optional<CertificationEvidence> certificationEvidence,
        List<MotorMassSample> massCurve,Optional<String> massCurveSource,List<MotorCgSample> cgCurve,Optional<String> cgCurveSource,
        MotorPropertyModelProfile propertyModelProfile) implements MotorPhysicsInput {
    public record GeometryInterpretation(double diameterM,double lengthM,String sourceReference,String qualification) {
        public GeometryInterpretation {
            if(!Double.isFinite(diameterM)||!Double.isFinite(lengthM)||diameterM<=0||lengthM<=0)fail("INVALID_RUNTIME_GEOMETRY","diameterM/lengthM");
            text(sourceReference,"geometry.sourceReference");text(qualification,"geometry.qualification");
        }
    }
    public record CertificationEvidence(MotorCertificationReference measuredReference,String sourceReference) {
        public CertificationEvidence {if(measuredReference==null)fail("MISSING_CERTIFICATION_REFERENCE","measuredReference");text(sourceReference,"certification.sourceReference");}
    }
    public SourceBackedMotorProfile {
        if(catalogProfile==null)fail("MISSING_SOURCE_DATA","catalogProfile");
        text(selectedCurveId,"selectedCurveId");
        var selected=catalogProfile.thrustSources().stream().filter(s->s.curveId().equals(selectedCurveId)).findFirst()
            .orElseThrow(()->new SourceMotorValidationError("INVALID_CURVE_SELECTION","selectedCurveId"));
        if(selected.acceptanceStatus()!=AcceptanceStatus.SOURCE_CURVE_VALIDATED)fail("UNACCEPTED_THRUST_SOURCE","selectedCurveId");
        if(motorType==null)fail("MISSING_MOTOR_TYPE","motorType");
        if(catalogProfile.hardware().kind()==HardwareKind.UNKNOWN
                || (catalogProfile.hardware().kind()==HardwareKind.RELOADABLE)!=(motorType==MotorType.RELOADABLE))
            fail("UNSUPPORTED_MOTOR_CONFIGURATION","motorType/hardware.kind");
        if(initialMassObservation==null || propellantMassObservation==null)fail("MISSING_SOURCE_DATA","massObservations");
        if(!catalogProfile.massObservations().contains(initialMassObservation) || !catalogProfile.massObservations().contains(propellantMassObservation))
            fail("INVALID_MASS_SOURCE_SELECTION","massObservations");
        if(initialMassObservation.quantity().contains("PROPELLANT") || initialMassObservation.quantity().contains("DRY")
                || !propellantMassObservation.quantity().contains("PROPELLANT"))fail("INVALID_MASS_OBSERVATION_ROLE","massObservations");
        if(initialMassObservation.massKg()<=0 || propellantMassObservation.massKg()<=0 || propellantMassObservation.massKg()>=initialMassObservation.massKg())
            fail("INVALID_MOTOR_MASSES","massObservations");
        if(geometryInterpretation==null)fail("MISSING_RUNTIME_GEOMETRY","geometryInterpretation");
        if(ejection==null)fail("MISSING_EJECTION_METADATA","ejection");
        if(ejection.rawSourceToken().isEmpty() || !ejection.rawSourceToken().orElseThrow().equals(selected.record().header().rawEjectionToken()))
            fail("SOURCE_TOKEN_MISMATCH","ejection.rawSourceToken");
        var metadata=selected.ejectionMetadata();
        switch(metadata.interpretation()) {
            case UNRESOLVED_PROVIDER_TOKEN -> {if(ejection.kind()!=UNKNOWN)fail("UNRESOLVED_EJECTION_SEMANTICS","ejection.kind");}
            case NO_EJECTION_PLUGGED -> {if(ejection.kind()!=PLUGGED)fail("INCOMPATIBLE_EJECTION_SEMANTICS","ejection.kind");}
            case NUMERIC_SOURCE_TOKEN,MIXED_SOURCE_OPTIONS -> {
                if(ejection.kind()==TIMED && !metadata.numericSourceOptionsS().contains(ejection.delayS().orElseThrow()))fail("INVALID_DELAY_SELECTION","ejection.delayS");
                if(ejection.kind()==PLUGGED && !metadata.pluggedOption())fail("INVALID_DELAY_SELECTION","ejection.kind");
            }
        }
        if(certificationEvidence==null || massCurveSource==null || cgCurveSource==null)fail("MISSING_OPTIONAL_CONTAINER","sourceReferences");
        if(propertyModelProfile==null)fail("MISSING_MODEL_SELECTION","propertyModelProfile");
        double end=selected.record().curveEndTimeS();
        validateCurve(massCurve,massCurveSource,end,true,initialMassObservation.massKg(),geometryInterpretation.lengthM());
        validateCurve(cgCurve,cgCurveSource,end,false,initialMassObservation.massKg(),geometryInterpretation.lengthM());
        if(propertyModelProfile.massModel()==MotorMassEvolutionModel.EXPLICIT_CURVE && massCurve==null)fail("EXPLICIT_MASS_CURVE_REQUIRED","massCurve");
        if(propertyModelProfile.cgModel()==MotorCgEvolutionModel.EXPLICIT_CURVE && cgCurve==null)fail("EXPLICIT_CG_CURVE_REQUIRED","cgCurve");
        massCurve=massCurve==null?null:List.copyOf(massCurve);cgCurve=cgCurve==null?null:List.copyOf(cgCurve);
    }
    private static void validateCurve(List<?> curve,Optional<String> source,double end,boolean mass,double initial,double length) {
        if((curve==null)!=source.isEmpty())fail("CURVE_PROVENANCE_MISMATCH",mass?"massCurve":"cgCurve");
        if(curve==null)return;
        text(source.orElseThrow(),"curveSource");
        if(curve.size()<2)fail("INSUFFICIENT_SOURCE_CURVE","curve");
        double previous=-1,lastMass=initial;
        for(int i=0;i<curve.size();i++) {
            Object entry=curve.get(i);
            if(mass?!(entry instanceof MotorMassSample):!(entry instanceof MotorCgSample))fail("INVALID_SOURCE_CURVE_ENTRY","curve["+i+"]");
            double t=mass?((MotorMassSample)entry).timeS():((MotorCgSample)entry).timeS();
            double value=mass?((MotorMassSample)entry).massKg():((MotorCgSample)entry).cgFromFrontM();
            if((i==0 && t!=0)||t<=previous)fail("INVALID_SOURCE_CURVE_GRID","curve["+i+"]");
            if(mass && ((i==0 && value!=initial)||value>lastMass))fail("INVALID_MASS_SOURCE_CURVE","massCurve["+i+"]");
            if(!mass && value>length)fail("CG_OUTSIDE_MOTOR","cgCurve["+i+"]");
            previous=t;lastMass=value;
        }
        if(previous<end)fail("INSUFFICIENT_CURVE_COVERAGE","curve");
    }
    private static void fail(String code,String field) {throw new SourceMotorValidationError(code,field);}
    private static void text(String value,String field) {if(value==null||value.isBlank())fail("MISSING_SOURCE_DATA",field);}
    public SourceCurve selectedThrustSource() {return catalogProfile.thrustSources().stream().filter(s->s.curveId().equals(selectedCurveId)).findFirst().orElseThrow();}
    public List<ThrustSample> thrustCurve() {return selectedThrustSource().record().normalizedSamples();}
    public double diameterM() {return geometryInterpretation.diameterM();}
    public double lengthM() {return geometryInterpretation.lengthM();}
    public double initialMassKg() {return initialMassObservation.massKg();}
    public double propellantMassKg() {return propellantMassObservation.massKg();}
    /** Observations/assumptions, not automatic physical-readiness flags or termination policies. */
    public List<String> qualifications() {
        var result=new ArrayList<String>();
        if(!catalogProfile.compatibilityIssues().isEmpty())result.add("LEGACY_CATALOG_COMPATIBILITY_REPORT_RETAINED: not the research runtime gate");
        if(catalogProfile.identity().sourceNames().stream().anyMatch(s->s.relationshipConfidence()==Confidence.UNRESOLVED_MAPPING))result.add("UNVERIFIED_ASSEMBLY_EQUIVALENCE");
        if(certificationEvidence.isEmpty())result.add("NO_VERIFIED_CERTIFICATION_EVIDENCE_PROVIDED");
        if(propertyModelProfile.massModel()==MotorMassEvolutionModel.IMPULSE_PROPORTIONAL)result.add("MODEL_ASSUMPTION: IMPULSE_PROPORTIONAL_MASS");
        if(propertyModelProfile.cgModel()==MotorCgEvolutionModel.FIXED_MIDPOINT)result.add("MODEL_ASSUMPTION: FIXED_MIDPOINT_CG");
        result.add("GEOMETRY_INTERPRETATION: "+geometryInterpretation.qualification());
        return List.copyOf(result);
    }
}
