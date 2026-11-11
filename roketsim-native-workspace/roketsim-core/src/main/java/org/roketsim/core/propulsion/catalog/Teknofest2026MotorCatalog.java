package org.roketsim.core.propulsion.catalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import static org.roketsim.core.propulsion.catalog.CatalogMotorProfile.*;
import org.roketsim.core.propulsion.catalog.EngMotorRecord.EjectionMetadata;
import org.roketsim.core.propulsion.catalog.EngMotorRecord.EjectionInterpretation;

/** The three owner-supplied Rev1 source profiles. No runtime catalog entry or physical default. */
public final class Teknofest2026MotorCatalog {
    public static final String SOURCE_ZIP_SHA256="467bd83b1b72c720305cdd22ec9b2f05aa91120b455988415a202915c9beb5de";
    private Teknofest2026MotorCatalog() { }

    public static List<CatalogMotorProfile> load() {
        return List.of(profile("L1256","L1256WS","Super White Lightning","75/3840",20.390,19.622,18.601,2.965,3.125,
                1.964,3.756,1.905,3.905,3.902,1.827,3.756,1.827,3.756,
                "756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd"),
            profile("M1850","M1850W","White Lightning","75/7680",36.328,35.551,34.539,2.965,3.125,
                3.951,6.693,4.103,7.010,7.010,3.979,6.871,4.122,6.032,
                "111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc"),
            profile("N3300","N3300R","Redline","98/15360",41.711,40.573,40.063,3.870,3.970,
                7.512,12.259,7.668,12.645,12.645,7.338,12.054,7.512,12.054,
                "10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8"));
    }

    private static CatalogMotorProfile profile(String shortName,String variant,String propellant,String caseId,
            double longest,double axialB,double axialC,double caseDiameter,double flangeDiameter,
            double originalPropellant,double originalLoaded,double updatedPropellant,double updatedLoaded,double nozzleLoaded,
            double brochurePropellant,double brochureLoaded,double secondaryPropellant,double secondaryLoaded,String checksum) {
        String file="Teknofest26_"+shortName+"_Rev1.eng";
        byte[] bytes;
        try(var input=Teknofest2026MotorCatalog.class.getResourceAsStream("teknofest2026/"+file)) {
            if(input==null) throw new IllegalStateException("missing source resource "+file);
            bytes=input.readAllBytes();
        } catch(IOException e) { throw new UncheckedIOException("reading "+file,e); }
        var source=new RaspEngReader().read(file,"Rev1","zip-sha256:"+SOURCE_ZIP_SHA256+"!/"+file,bytes);
        if(!source.sha256().equals(checksum) || source.records().size()!=1)
            throw new IllegalStateException("source integrity mismatch "+file);
        var header=source.records().get(0).header();
        String expected=shortName.equals("N3300")?"TFN3300R-P":"TF"+variant;
        if(!header.designation().equals(expected) || !header.sourceProvider().equals("Teknofest26"))
            throw new IllegalStateException("source identity mismatch "+file);
        String dimension=shortName+" Motor Boyutları.pdf";
        String drawing=variant+" P DWG.pdf";
        String nozzle=shortName+" Motorunun Nozul Bölgesi Ölçüleri.pdf";
        var identity=new Identity("AeroTech/RCS",shortName,propellant,List.of(
            new IdentityReference("ENG",header.designation(),file,Confidence.UNRESOLVED_MAPPING),
            new IdentityReference("RELOAD_DRAWING",variant+"-P",drawing,Confidence.UNRESOLVED_MAPPING),
            new IdentityReference("BROCHURE",variant+"-PS",shortName+"-1.jpg",Confidence.UNRESOLVED_MAPPING),
            new IdentityReference("COMPETITION",shortName,"Guncel_motor_verileri.xlsx!"+shortName,Confidence.VERIFIED_SOURCE_TEXT)));
        var hardware=new HardwareConfiguration("RMS-"+caseId,HardwareKind.RELOADABLE,drawing,
            "Drawing family association; exact TF/P/PS assembly identity remains unverified.");
        var ejection=header.ejectionMetadata();
        if(!header.rawEjectionToken().equals("P"))
            ejection=new EjectionMetadata(header.rawEjectionToken(),EjectionInterpretation.UNRESOLVED_PROVIDER_TOKEN,
                ejection.numericSourceOptionsS(),ejection.pluggedOption(),file+": provider meaning requires clarification; not a physical delay");
        var curve=new SourceCurve("teknofest26:"+header.designation()+":Rev1",source,0,ejection,AcceptanceStatus.SOURCE_CURVE_VALIDATED);
        var masses=new ArrayList<MassObservation>();
        masses.add(mass("ENG_INITIAL",header.initialMassKg(),file+":header[5]","loaded assembly per source","Source-reported, not independent certification"));
        masses.add(mass("ENG_PROPELLANT",header.propellantMassKg(),file+":header[4]","consumables per source","Source-reported"));
        masses.add(mass("ENG_DERIVED_DRY",header.initialMassKg()-header.propellantMassKg(),file+":header[5]-header[4]","difference only","Derived arithmetic, not measured dry mass"));
        String sheet="Guncel_motor_verileri.xlsx!"+shortName;
        masses.add(mass("WORKBOOK_ORIGINAL_PROPELLANT",originalPropellant,sheet+"!G6","source label manufacturer","Workbook footnote attributes original values to ENG; exact older file absent"));
        masses.add(mass("WORKBOOK_ORIGINAL_INITIAL",originalLoaded,sheet+"!J6","source label manufacturer","Not independently verified manufacturer measurement"));
        masses.add(mass("WORKBOOK_UPDATED_PROPELLANT",updatedPropellant,sheet+"!G7","source label measured","No metrological certification supplied"));
        masses.add(mass("WORKBOOK_UPDATED_INITIAL",updatedLoaded,sheet+"!J7","source label measured","No metrological certification supplied"));
        masses.add(mass("NOZZLE_SHEET_INITIAL",nozzleLoaded,nozzle,"hardware + grain + O-ring/smoke","L1256 differs from ENG/workbook by 0.003 kg; never substituted"));
        masses.add(mass("BROCHURE_PROPELLANT",brochurePropellant,shortName+"-1.jpg","variant -PS brochure","Variant association unverified; not substituted"));
        masses.add(mass("BROCHURE_INITIAL",brochureLoaded,shortName+"-1.jpg","variant -PS brochure","Not a certification reference"));
        masses.add(mass("SECONDARY_SCREENSHOT_PROPELLANT",secondaryPropellant,shortName+"-2.jpg","unverified screenshot assembly","Source/version and assembly differences unresolved"));
        masses.add(mass("SECONDARY_SCREENSHOT_MOTOR_WEIGHT",secondaryLoaded,shortName+"-2.jpg","unverified screenshot assembly","Reported motor weight; loaded/dry scope unresolved, not substituted for ENG mass"));
        var geometry=List.of(
            geometry("LONGEST_LABELED_AXIAL_DIMENSION",longest,dimension,"UNRESOLVED","Labeled span; full installed envelope/reference planes not accepted"),
            geometry("ADDITIONAL_AXIAL_DIMENSION_B",axialB,dimension,"UNRESOLVED","Separate labeled span, not an inferred datum"),
            geometry("ADDITIONAL_AXIAL_DIMENSION_C",axialC,dimension,"UNRESOLVED","Separate labeled span, not an inferred datum"),
            geometry("CASE_DIAMETER",caseDiameter,dimension,"NOT_APPLICABLE","Nominal ENG diameter does not include flange envelope"),
            geometry("LARGEST_LABELED_FLANGE_DIAMETER",flangeDiameter,dimension,"NOT_APPLICABLE","Known labeled external diameter, not certification of all external features"),
            geometry("LABELED_AXIAL_PROJECTION",.500,dimension,"UNRESOLVED","0.500 in = 12.7 mm; ENG length relationship only supported inference"),
            new GeometryObservation("ENG_LENGTH_MINUS_LONGEST_LABELED_SPAN",header.nominalLengthMm()-longest*25.4,
                "mm",file+" + "+dimension,Confidence.SUPPORTED_INFERENCE,"UNRESOLVED",
                "Numerical proximity to 12.7 mm does not establish matching reference planes"));
        var issues=List.of(
            issue("IDENTITY_VARIANT_MAPPING_UNRESOLVED",Readiness.MOTOR_PHYSICS_READY,"identity","TF source / P drawing / PS brochure are not established equivalent variants"),
            issue("RELOADABLE_TYPE_UNREPRESENTABLE",Readiness.MOTOR_PHYSICS_READY,"motor_type","HISTORICAL MOTOR-001, before N37-02: Accepted MotorType supports SINGLE_USE only; no reclassification"),
            issue("CERTIFICATION_REFERENCE_MISSING",Readiness.MOTOR_PHYSICS_READY,"certification","No verified four-quantity MotorCertificationReference for this exact curve/assembly"),
            issue("PHYSICAL_EJECTION_DELAY_UNREPRESENTABLE",Readiness.MOTOR_PHYSICS_READY,"ejection_delay_s","Raw token "+header.rawEjectionToken()+" is not an accepted finite physical delay; no numeric substitute"),
            issue("MOTOR_PROPERTY_PROFILE_NOT_SELECTED",Readiness.MOTOR_PHYSICS_READY,"motor_property_model_profile","Source has no verified mass/CG curves; no automatic fallback profile"),
            issue("INSTALLATION_DATUM_UNRESOLVED",Readiness.INSTALLATION_READY,"mounting_geometry","Approximately 12.7 mm difference and full closure/nozzle envelope need reference-plane confirmation"),
            issue("INERTIA_INPUTS_UNAVAILABLE",Readiness.INERTIA_READY,"motor_inertia",shortName.equals("M1850")?
                "Five forward 0.875-in bores versus one aft 1.000-in bore do not fit identical-grain assumptions; dry inertia/regression also missing":
                "Validated dry CG/inertia and grain regression missing; no uniform-cylinder or radial-grain model selected"));
        return new CatalogMotorProfile("teknofest26:"+shortName+":Rev1",identity,hardware,List.of(curve),masses,geometry,issues);
    }
    private static MassObservation mass(String quantity,double value,String source,String scope,String qualification) {
        return new MassObservation(quantity,value,source,scope,qualification);
    }
    private static GeometryObservation geometry(String quantity,double value,String source,String datum,String note) {
        return new GeometryObservation(quantity,value,"in",source,Confidence.VERIFIED_SOURCE_TEXT,datum,note);
    }
    private static CompatibilityIssue issue(String code,Readiness capability,String field,String detail) {
        return new CompatibilityIssue(code,capability,field,detail);
    }
}
