package org.roketsim.core.propulsion.catalog;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.roketsim.core.propulsion.catalog.EngMotorRecord.EjectionMetadata;

/** Source evidence for a motor/hardware association, separate from the accepted runtime motor API. */
public record CatalogMotorProfile(String profileId,Identity identity,HardwareConfiguration hardware,
        List<SourceCurve> thrustSources,List<MassObservation> massObservations,
        List<GeometryObservation> geometryObservations,List<CompatibilityIssue> compatibilityIssues) {
    public CatalogMotorProfile {
        EngMotorRecord.text(profileId,"profileId");
        if(identity==null || hardware==null) throw new IllegalArgumentException("identity and hardware required");
        thrustSources=List.copyOf(thrustSources); massObservations=List.copyOf(massObservations);
        geometryObservations=List.copyOf(geometryObservations); compatibilityIssues=List.copyOf(compatibilityIssues);
        if(thrustSources.isEmpty()) throw new IllegalArgumentException("thrustSources must not be empty");
        if(thrustSources.stream().map(SourceCurve::curveId).distinct().count()!=thrustSources.size())
            throw new IllegalArgumentException("duplicate curveId");
    }

    public enum Readiness { CATALOG_AVAILABLE, THRUST_SOURCE_VALIDATED, MOTOR_PHYSICS_READY, INSTALLATION_READY, INERTIA_READY }
    public enum Confidence { VERIFIED_SOURCE_TEXT, SUPPORTED_INFERENCE, UNRESOLVED_MAPPING, CONFLICT }
    public enum HardwareKind { RELOADABLE, SINGLE_USE, UNKNOWN }
    public enum AcceptanceStatus { SOURCE_CURVE_VALIDATED, IMPORTED_NOT_ACCEPTED }

    /** An observed name is not an assertion that its variant equals another observed name. */
    public record IdentityReference(String role,String designation,String sourceReference,Confidence relationshipConfidence) {
        public IdentityReference {
            EngMotorRecord.text(role,"role"); EngMotorRecord.text(designation,"designation");
            EngMotorRecord.text(sourceReference,"sourceReference");
            if(relationshipConfidence==null) throw new IllegalArgumentException("relationshipConfidence required");
        }
    }
    public record Identity(String physicalManufacturer,String competitionDesignation,String propellantFamily,
            List<IdentityReference> sourceNames) {
        public Identity {
            EngMotorRecord.text(physicalManufacturer,"physicalManufacturer");
            EngMotorRecord.text(competitionDesignation,"competitionDesignation");
            EngMotorRecord.text(propellantFamily,"propellantFamily"); sourceNames=List.copyOf(sourceNames);
        }
    }
    public record HardwareConfiguration(String hardwareId,HardwareKind kind,String sourceReference,String qualification) {
        public HardwareConfiguration {
            EngMotorRecord.text(hardwareId,"hardwareId"); EngMotorRecord.text(sourceReference,"sourceReference");
            EngMotorRecord.text(qualification,"qualification");
            if(kind==null) throw new IllegalArgumentException("hardware kind required");
        }
    }
    /** Each curve retains its own file revision/hash and source-specific ejection interpretation. */
    public record SourceCurve(String curveId,EngSourceFile source,int entryIndex,
            EjectionMetadata ejectionMetadata,AcceptanceStatus acceptanceStatus) {
        public SourceCurve {
            EngMotorRecord.text(curveId,"curveId");
            if(source==null || entryIndex<0 || entryIndex>=source.records().size())
                throw new IllegalArgumentException("invalid source/entryIndex");
            if(ejectionMetadata==null || acceptanceStatus==null) throw new IllegalArgumentException("source metadata required");
            if(!source.records().get(entryIndex).header().rawEjectionToken().equals(ejectionMetadata.rawToken()))
                throw new IllegalArgumentException("ejection interpretation must retain raw token");
        }
        public EngMotorRecord record() { return source.records().get(entryIndex); }
    }
    /** Observations are retained together; none silently supersedes another observation. */
    public record MassObservation(String quantity,double massKg,String sourceReference,String assemblyScope,String qualification) {
        public MassObservation {
            EngMotorRecord.text(quantity,"quantity"); EngMotorRecord.text(sourceReference,"sourceReference");
            EngMotorRecord.text(assemblyScope,"assemblyScope"); EngMotorRecord.text(qualification,"qualification");
            if(!Double.isFinite(massKg) || massKg<0) throw new IllegalArgumentException("invalid observed massKg");
        }
    }
    /** Drawing units and reference-plane uncertainty are explicit, not an installation geometry. */
    public record GeometryObservation(String quantity,double value,String sourceUnit,String sourceReference,
            Confidence confidence,String axialDatum,String qualification) {
        public GeometryObservation {
            EngMotorRecord.text(quantity,"quantity"); EngMotorRecord.text(sourceUnit,"sourceUnit");
            EngMotorRecord.text(sourceReference,"sourceReference"); EngMotorRecord.text(axialDatum,"axialDatum");
            EngMotorRecord.text(qualification,"qualification");
            if(!Double.isFinite(value) || confidence==null) throw new IllegalArgumentException("invalid geometry observation");
        }
    }
    public record CompatibilityIssue(String code,Readiness blockedCapability,String fieldName,String detail) {
        public CompatibilityIssue {
            EngMotorRecord.text(code,"code"); EngMotorRecord.text(fieldName,"fieldName"); EngMotorRecord.text(detail,"detail");
            if(blockedCapability==null) throw new IllegalArgumentException("blockedCapability required");
        }
    }
    public record CompatibilityResult(Set<Readiness> availableCapabilities,List<CompatibilityIssue> issues) {
        public CompatibilityResult {
            availableCapabilities=Set.copyOf(availableCapabilities); issues=List.copyOf(issues);
        }
    }
    /** Current source-only readiness; RELOADABLE is supported by the source-backed runtime.
     * Other unresolved evidence is retained; no profile becomes flight-ready from enum support.
     * There is deliberately no MotorDefinition construction or implicit approximation adapter.
     */
    public CompatibilityResult currentCoreCompatibility() {
        var available=EnumSet.of(Readiness.CATALOG_AVAILABLE);
        if(thrustSources.stream().allMatch(s->s.acceptanceStatus()==AcceptanceStatus.SOURCE_CURVE_VALIDATED))
            available.add(Readiness.THRUST_SOURCE_VALIDATED);
        return new CompatibilityResult(available,compatibilityIssues.stream().filter(i->
            !(hardware.kind()==HardwareKind.RELOADABLE && i.code().equals("RELOADABLE_TYPE_UNREPRESENTABLE"))).toList());
    }

    /** Explicitly historical MOTOR-001/legacy-contract observations, not current runtime blockers.
     * Original issue codes/text remain available through the compatibilityIssues record component.
     */
    public CompatibilityResult historicalCoreCompatibility() {
        var original=currentCoreCompatibility();
        return new CompatibilityResult(original.availableCapabilities(),compatibilityIssues.stream().map(i->
            new CompatibilityIssue(i.code(),i.blockedCapability(),i.fieldName(),"HISTORICAL MOTOR-001 / legacy contract snapshot: "+i.detail())).toList());
    }
}
