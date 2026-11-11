package org.roketsim.core;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.propulsion.catalog.*;
import static org.roketsim.core.propulsion.catalog.CatalogMotorProfile.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.numerics.*;

/** All vehicle/geometry choices here are TEST-ONLY model fixtures, not TEKNOFEST flight approval. */
public class SourceMotorRuntimeTest {
    static SourceMotorValidationError error(String code,Runnable action) {
        var e=assertThrows(SourceMotorValidationError.class,action::run);assertEquals(code,e.errorCode());assertFalse(e.fieldName().isBlank());return e;
    }
    static SourceBackedMotorProfile bind(CatalogMotorProfile catalog,String curveId,MotorType type,double diameter,double length,
            MotorPropertyModelProfile model,List<MotorMassSample> masses,List<MotorCgSample> cgs) {
        var source=catalog.thrustSources().stream().filter(c->c.curveId().equals(curveId)).findFirst().orElseThrow();
        var md=source.ejectionMetadata();
        var kind=switch(md.interpretation()) {
            case NO_EJECTION_PLUGGED -> MotorEjectionMetadata.Kind.PLUGGED;
            case UNRESOLVED_PROVIDER_TOKEN -> MotorEjectionMetadata.Kind.UNKNOWN;
            default -> MotorEjectionMetadata.Kind.TIMED;
        };
        var delay=kind==MotorEjectionMetadata.Kind.TIMED?OptionalDouble.of(md.numericSourceOptionsS().get(0)):OptionalDouble.empty();
        return new SourceBackedMotorProfile(catalog,curveId,type,
            catalog.massObservations().stream().filter(m->m.quantity().equals("ENG_INITIAL")).findFirst().orElseThrow(),
            catalog.massObservations().stream().filter(m->m.quantity().equals("ENG_PROPELLANT")).findFirst().orElseThrow(),
            new SourceBackedMotorProfile.GeometryInterpretation(diameter,length,"TEST_ONLY: nominal-source geometry fixture","TEST_ONLY, unresolved physical installation datum; not flight-ready"),
            new MotorEjectionMetadata(kind,delay,Optional.of(md.rawToken()),md.interpretationReference()),Optional.empty(),masses,
            masses==null?Optional.empty():Optional.of("TEST_ONLY explicit mass source"),cgs,cgs==null?Optional.empty():Optional.of("TEST_ONLY explicit CG source"),model);
    }
    public static SourceBackedMotorProfile teknofest(String name) {
        var c=Teknofest2026MotorCatalog.load().stream().filter(p->p.identity().competitionDesignation().equals(name)).findFirst().orElseThrow();
        var curve=c.thrustSources().get(0);var h=curve.record().header();
        return bind(c,curve.curveId(),MotorType.RELOADABLE,h.nominalDiameterM(),h.nominalLengthM(),MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,null,null);
    }
    static SourceCurve curve(String id,MotorDefinition m,List<ThrustSample> samples) {
        var text=new StringBuilder("; TEST_ONLY numeric-equivalence fixture, not new measured data\n");
        text.append("Fixture ").append(Double.toString(m.diameterM()*1000)).append(' ').append(Double.toString(m.lengthM()*1000))
            .append(' ').append(Double.toString(m.ejectionDelayS())).append(' ').append(Double.toString(m.propellantMassKg())).append(' ')
            .append(Double.toString(m.initialMassKg())).append(" TestProvider\n");
        for(var s:samples)text.append(Double.toString(s.timeS())).append(' ').append(Double.toString(s.thrustN())).append('\n');
        var source=new RaspEngReader().read(id+".eng","TEST_ONLY","TEST_ONLY in-memory numeric fixture",text.toString().getBytes(StandardCharsets.UTF_8));
        return new SourceCurve(id,source,0,source.records().get(0).header().ejectionMetadata(),AcceptanceStatus.SOURCE_CURVE_VALIDATED);
    }
    public static SourceBackedMotorProfile equivalent(MotorDefinition m) {
        var curve=curve("test:curve:1",m,m.thrustCurve());
        var identity=new Identity(m.manufacturer(),m.designation(),m.propellantName(),List.of(new IdentityReference("TEST_ONLY",m.designation(),"TEST_ONLY numeric-equivalence",Confidence.VERIFIED_SOURCE_TEXT)));
        var catalog=new CatalogMotorProfile("test:assembly",identity,new HardwareConfiguration("test:hardware",HardwareKind.SINGLE_USE,"TEST_ONLY","synthetic binding"),List.of(curve),
            List.of(new MassObservation("ENG_INITIAL",m.initialMassKg(),"TEST_ONLY","same numeric scope","not measured"),
                new MassObservation("ENG_PROPELLANT",m.propellantMassKg(),"TEST_ONLY","same numeric scope","not measured")),List.of(),List.of());
        var profile=m.massCurve()!=null && m.cgCurve()!=null?new MotorPropertyModelProfile(MotorMassEvolutionModel.EXPLICIT_CURVE,MotorCgEvolutionModel.EXPLICIT_CURVE):MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE;
        return bind(catalog,curve.curveId(),MotorType.SINGLE_USE,m.diameterM(),m.lengthM(),profile,m.massCurve(),m.cgCurve());
    }
    public static RuntimePhysicsEvaluationContext3DOF context(SimulationRunConfiguration3DOF base,SourceBackedMotorProfile motor) {
        var c=base.physicsContext();var installation=new MotorInstallationResolver().resolveRuntime(c.resolvedGeometry(),motor);
        return new RuntimePhysicsEvaluationContext3DOF(c.resolvedGeometry(),c.aerodynamicSurfaces(),c.structuralMassProperties(),installation,motor.propertyModelProfile(),
            c.basicDragModelProfile(),c.translationalDynamicsModelProfile(),c.environmentPositionMappingModel(),c.launchEnvironmentAltitudeM(),c.propulsionTimeline(),
            c.atmosphereModel(),c.airPropertiesCalculator(),c.gravityModel(),c.windModel());
    }
    public static SourceMotorGroundRunConfiguration3DOF sourceRun(SimulationRunConfiguration3DOF base,SourceBackedMotorProfile motor,
            Optional<SourceMotorGroundRunConfiguration3DOF.GuideConfiguration> guide,Optional<RecoveryConfiguration3DOF> recovery,
            IntegrationStepAlignmentMode alignment,List<AttachedHardwareMass> hardware) {
        return new SourceMotorGroundRunConfiguration3DOF(base.launchConditions(),context(base,motor),base.eventDetectionProfile(),base.fixedStepConfig(),base.terminalEventHandlingModel(),hardware,guide,recovery,alignment);
    }
    static GroundRunResult3DOF run(SourceMotorGroundRunConfiguration3DOF c) {return new SimulationEngine3DOF().runSourceUntilGround(c,GroundTerminationTest.control(p->{}));}
    @Test void exactLegacySchemaAndF50IdentityAreUntouched() {
        assertEquals(List.of("motorId","manufacturer","designation","family","motorType","propellantName","diameterM","lengthM","initialMassKg","propellantMassKg","ejectionDelayS","thrustCurve","certification","provenance","massCurve","cgCurve"),
            Arrays.stream(MotorDefinition.class.getRecordComponents()).map(c->c.getName()).toList());
        assertEquals(5,MotorInstallation.class.getRecordComponents().length);assertEquals(MotorDefinition.class,MotorInstallation.class.getRecordComponents()[0].getType());
        assertEquals(MotorType.SINGLE_USE,MotorCatalogs.AEROTECH_F50_4T.motorType());assertEquals(4,MotorCatalogs.AEROTECH_F50_4T.ejectionDelayS());
        assertEquals(0,MotorType.SINGLE_USE.ordinal());assertEquals("single_use",MotorType.SINGLE_USE.value());assertEquals("reloadable",MotorType.RELOADABLE.value());
    }
    @Test void originalConstructorPrecedenceAndCertificationRequirementRemain() {
        assertThrows(MotorValidationError.class,()->MotorCatalogTest.change("certification",null));
        var m=MotorCatalogs.AEROTECH_F50_4T;
        var e=assertThrows(IllegalArgumentException.class,()->new MotorDefinition("",m.manufacturer(),m.designation(),m.family(),"bad",m.propellantName(),Double.NaN,m.lengthM(),m.initialMassKg(),m.propellantMassKg(),-1,m.thrustCurve(),null,null,null,null));
        assertEquals(IllegalArgumentException.class,e.getClass());assertTrue(e.getMessage().contains("diameter_m"));
        assertEquals("NEGATIVE_EJECTION_DELAY",assertThrows(MotorValidationError.class,()->new MotorDefinition(m.motorId(),m.manufacturer(),m.designation(),m.family(),m.motorType(),m.propellantName(),m.diameterM(),m.lengthM(),m.initialMassKg(),m.propellantMassKg(),-1,m.thrustCurve(),null,m.provenance(),null,null)).errorCode());
    }
    @Test void newNamesPreserveSourceCompilationAndErrorOrderingOfLegacyNullCalls() {
        var thrust=new MotorThrustCurveEvaluator();assertThrows(IllegalArgumentException.class,()->thrust.evaluate(null,Double.NaN));assertThrows(NullPointerException.class,()->thrust.evaluate(null,0));
        assertThrows(NullPointerException.class,()->new MotorCurveAnalyzer().analyze(null));
        var property=new MotorPropertyEvaluator();assertTrue(assertThrows(IllegalArgumentException.class,()->property.evaluate(null,"bad",Double.NaN)).getMessage().contains("motor_time_s"));
        assertEquals("INVALID_MOTOR_PROPERTY_MODEL",assertThrows(PropulsionEvaluationError.class,()->property.evaluate(null,"bad",0)).errorCode());
        assertThrows(NullPointerException.class,()->property.evaluate(null,MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,0));
    }
    @Test void ejectionMetadataValidatesKindsWithoutInventingRawLegacyTokens() {
        var old=MotorEjectionMetadata.fromLegacy(MotorCatalogs.AEROTECH_F50_4T);assertEquals(MotorEjectionMetadata.Kind.TIMED,old.kind());assertEquals(4,old.delayS().orElseThrow());assertTrue(old.rawSourceToken().isEmpty());
        for(var kind:List.of(MotorEjectionMetadata.Kind.PLUGGED,MotorEjectionMetadata.Kind.UNKNOWN))assertThrows(IllegalArgumentException.class,()->new MotorEjectionMetadata(kind,OptionalDouble.of(0),Optional.of("P"),"test"));
        assertThrows(IllegalArgumentException.class,()->new MotorEjectionMetadata(MotorEjectionMetadata.Kind.TIMED,OptionalDouble.empty(),Optional.empty(),"test"));
        for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new MotorEjectionMetadata(MotorEjectionMetadata.Kind.TIMED,OptionalDouble.of(value),Optional.empty(),"test"));
    }
    @Test void allTeknofestProfilesRetainExactTokensHashesIdentityAndResearchQualifications() {
        String[] names={"L1256","M1850","N3300"};String[] tokens={"-","1000","P"};
        for(int i=0;i<names.length;i++) {
            var p=teknofest(names[i]);var original=MotorEngImportTest.profile(names[i]);
            assertEquals(original.identity(),p.catalogProfile().identity());assertEquals(original,p.catalogProfile());assertEquals(MotorType.RELOADABLE,p.motorType());
            assertEquals(tokens[i],p.ejection().rawSourceToken().orElseThrow());assertTrue(p.ejection().delayS().isEmpty());
            assertEquals(i==2?MotorEjectionMetadata.Kind.PLUGGED:MotorEjectionMetadata.Kind.UNKNOWN,p.ejection().kind());
            assertEquals(original.thrustSources().get(0),p.selectedThrustSource());assertArrayEquals(original.thrustSources().get(0).source().sourceBytes(),p.selectedThrustSource().source().sourceBytes());
            assertEquals("AeroTech/RCS",p.catalogProfile().identity().physicalManufacturer());assertEquals("Teknofest26",p.selectedThrustSource().record().header().sourceProvider());assertEquals("Rev1",p.selectedThrustSource().source().revision());
            assertTrue(p.certificationEvidence().isEmpty());assertTrue(p.qualifications().contains("NO_VERIFIED_CERTIFICATION_EVIDENCE_PROVIDED"));assertTrue(p.qualifications().contains("UNVERIFIED_ASSEMBLY_EQUIVALENCE"));
            assertTrue(p.qualifications().contains("MODEL_ASSUMPTION: FIXED_MIDPOINT_CG"));
        }
    }
    @Test void sourceBindingRejectsUnresolvedNumericDelayWrongTokensTypesAndCurveIds() {
        var p=teknofest("M1850");
        error("UNRESOLVED_EJECTION_SEMANTICS",()->VehicleTestData.replace(p,"ejection",new MotorEjectionMetadata(MotorEjectionMetadata.Kind.TIMED,OptionalDouble.of(1000),Optional.of("1000"),"generic parse is not evidence")));
        error("SOURCE_TOKEN_MISMATCH",()->VehicleTestData.replace(p,"ejection",new MotorEjectionMetadata(MotorEjectionMetadata.Kind.UNKNOWN,OptionalDouble.empty(),Optional.of("-"),"test")));
        error("INVALID_CURVE_SELECTION",()->VehicleTestData.replace(p,"selected_curve_id","missing"));
        error("UNSUPPORTED_MOTOR_CONFIGURATION",()->VehicleTestData.replace(p,"motor_type",MotorType.SINGLE_USE));
        error("MISSING_MODEL_SELECTION",()->VehicleTestData.replace(p,"property_model_profile",null));
    }
    @Test void selectedMassMustActuallyBelongToSourceAndGeometryMustBeExplicit() {
        var p=teknofest("L1256");
        error("INVALID_MASS_SOURCE_SELECTION",()->VehicleTestData.replace(p,"initial_mass_observation",new MassObservation("invention",3.905,"not in source","none","not accepted")));
        error("MISSING_RUNTIME_GEOMETRY",()->VehicleTestData.replace(p,"geometry_interpretation",null));
        for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})error("INVALID_RUNTIME_GEOMETRY",()->new SourceBackedMotorProfile.GeometryInterpretation(value,1,"test","test"));
        error("MISSING_SOURCE_DATA",()->new SourceBackedMotorProfile.GeometryInterpretation(1,1," ","test"));
        assertEquals(3.905,p.initialMassKg());assertTrue(p.catalogProfile().massObservations().stream().anyMatch(m->m.massKg()==3.902));
    }
    @Test void optionalCertificationIsPairedEvidenceNotARequirementOrAnUncertifiedClaim() {
        var p=equivalent(MotorCatalogs.AEROTECH_F50_4T);assertTrue(p.certificationEvidence().isEmpty());
        var evidence=new SourceBackedMotorProfile.CertificationEvidence(MotorCatalogs.AEROTECH_F50_4T.certification(),"TEST_ONLY preserved F50 evidence reference");
        var supplied=(SourceBackedMotorProfile)VehicleTestData.replace(p,"certification_evidence",Optional.of(evidence));
        assertEquals(evidence,supplied.certificationEvidence().orElseThrow());assertFalse(supplied.qualifications().contains("NO_VERIFIED_CERTIFICATION_EVIDENCE_PROVIDED"));
        error("MISSING_SOURCE_DATA",()->new SourceBackedMotorProfile.CertificationEvidence(evidence.measuredReference(),""));
    }
    @Test void equivalentViewsHaveExactThrustImpulseAndStatistics() {
        var old=MotorCatalogs.AEROTECH_F50_4T;var source=equivalent(old);var adapter=MotorPhysicsInputs.fromLegacyMotor(old);
        var evaluator=new MotorThrustCurveEvaluator();
        for(double t:new double[]{0,.012,.142,.354,.901,1.43,100}) {
            assertEquals(evaluator.evaluate(old,t),evaluator.evaluateRuntime(source,t));assertEquals(evaluator.evaluate(old,t),evaluator.evaluateRuntime(adapter,t));
        }
        assertEquals(new MotorCurveAnalyzer().analyze(old),new MotorCurveAnalyzer().analyzeRuntime(source));
    }
    @Test void nominalInstallationAndModeledMassCgReuseExactLegacyArithmetic() {
        var geometry=MotorInstallationTest.geometry();var old=MotorCatalogs.AEROTECH_F50_4T;var source=equivalent(old);
        var legacy=new MotorInstallationResolver().resolve(geometry,old);var current=new MotorInstallationResolver().resolveRuntime(geometry,source);
        assertEquals(legacy.motorFrontXGeoM(),current.motorFrontXGeoM());assertEquals(legacy.motorAftXGeoM(),current.motorAftXGeoM());assertEquals(legacy.axialEngagementLengthM(),current.axialEngagementLengthM());
        for(double t:new double[]{0,.354,1.4,1.43,100})assertEquals(new MotorPropertyEvaluator().evaluate(legacy,source.propertyModelProfile(),t),new MotorPropertyEvaluator().evaluateRuntime(current,source.propertyModelProfile(),t));
        assertEquals("INVALID_MOTOR_CG_X_GEO_RESULT",assertThrows(PropulsionEvaluationError.class,()->new MotorPropertyEvaluator().evaluate(new MotorInstallation(old,Double.POSITIVE_INFINITY,1,0,.1),source.propertyModelProfile(),0)).errorCode());
    }
    @Test void explicitMassCgSourceValuesAndProvenanceAreRequiredWithoutFallback() {
        var old=MotorPropertiesTest.sourceMotor();var p=equivalent(old);var legacy=new MotorInstallation(old,.9,1,0,.1);var current=new RuntimeMotorInstallation(p,.9,1,0,.1);
        for(double t:new double[]{0,.1,.5,.8,1,100})assertEquals(new MotorPropertyEvaluator().evaluate(legacy,p.propertyModelProfile(),t),new MotorPropertyEvaluator().evaluateRuntime(current,p.propertyModelProfile(),t));
        error("CURVE_PROVENANCE_MISMATCH",()->VehicleTestData.replace(p,"mass_curve_source",Optional.empty()));
        var modeled=teknofest("L1256");error("EXPLICIT_MASS_CURVE_REQUIRED",()->VehicleTestData.replace(modeled,"property_model_profile",new MotorPropertyModelProfile(MotorMassEvolutionModel.EXPLICIT_CURVE,MotorCgEvolutionModel.FIXED_MIDPOINT)));
        error("EXPLICIT_CG_CURVE_REQUIRED",()->VehicleTestData.replace(modeled,"property_model_profile",new MotorPropertyModelProfile(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,MotorCgEvolutionModel.EXPLICIT_CURVE)));
    }
    @Test void selectedCurveUpdatesThrustStatisticsDepletionAndBoundaryAuthorityTogether() {
        var old=MotorCatalogs.AEROTECH_F50_4T;var original=equivalent(old);var catalog=original.catalogProfile();
        var second=curve("test:curve:2",old,List.of(new ThrustSample(0,0),new ThrustSample(.104,200),new ThrustSample(2,0)));
        var extended=new CatalogMotorProfile(catalog.profileId(),catalog.identity(),catalog.hardware(),List.of(catalog.thrustSources().get(0),second),catalog.massObservations(),catalog.geometryObservations(),catalog.compatibilityIssues());
        var a=(SourceBackedMotorProfile)VehicleTestData.replace(original,"catalog_profile",extended);var b=(SourceBackedMotorProfile)VehicleTestData.replace(a,"selected_curve_id",second.curveId());
        assertSame(a.catalogProfile().identity(),b.catalogProfile().identity());assertNotEquals(a.selectedThrustSource().source().sha256(),b.selectedThrustSource().source().sha256());
        assertNotEquals(new MotorThrustCurveEvaluator().evaluateRuntime(a,.2),new MotorThrustCurveEvaluator().evaluateRuntime(b,.2));
        assertEquals(2,new MotorCurveAnalyzer().analyzeRuntime(b).curveEndTimeS());
        var base=NativeDemoTrajectoryTest.configuration(.01,1);var ca=context(base,a);var cb=context(base,b);
        assertNotEquals(new MotorPropertyEvaluator().evaluateRuntime(ca.motorInstallation(),a.propertyModelProfile(),.2),new MotorPropertyEvaluator().evaluateRuntime(cb.motorInstallation(),b.propertyModelProfile(),.2));
        var grid=MotorTimeBoundarySchedule3DOF.fromRuntimeContext(cb).boundaryTimesS();assertEquals(List.of(0.,.104,2.),grid);
    }
    @Test void runtimeContextRejectsASecondConflictingModelSelection() {
        var c=context(NativeDemoTrajectoryTest.configuration(.02,1),equivalent(MotorCatalogs.AEROTECH_F50_4T));
        error("MOTOR_MODEL_SELECTION_MISMATCH",()->VehicleTestData.replace(c,"motor_property_model_profile",new MotorPropertyModelProfile(MotorMassEvolutionModel.EXPLICIT_CURVE,MotorCgEvolutionModel.FIXED_MIDPOINT)));
    }
    @Test void singlePointPhysicsAndOriginalGroundFlightAreExactlyEquivalent() {
        var base=NativeDemoTrajectoryTest.configuration(.02,5000);var p=equivalent(MotorCatalogs.AEROTECH_F50_4T);var c=context(base,p);var state=new InitialStateBuilder().build(base.launchConditions());
        assertEquals(new PhysicsEvaluator3DOF().evaluate(0,state,base.launchConditions(),base.physicsContext()),new PhysicsEvaluator3DOF().evaluateRuntime(0,state,base.launchConditions(),c,new double[0],new double[0]));
        var original=new SimulationEngine3DOF().runUntilGround(new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),Optional.empty(),Optional.empty(),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP),GroundTerminationTest.control(v->{}));
        var result=run(sourceRun(base,p,Optional.empty(),Optional.empty(),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP,List.of()));
        assertEquals(RunControl3DOF.Status.GROUND,result.terminationReason());assertEquals(original.samples(),result.samples());assertEquals(original.events(),result.events());assertEquals(original.acceptedSegments(),result.acceptedSegments());
    }
    @Test void recoveryAndGuideUseTheSameAcceptedLifecycleAndForceRegimes() {
        var base=NativeDemoTrajectoryTest.configuration(.02,1);var p=equivalent(MotorCatalogs.AEROTECH_F50_4T);var guide=LaunchGuideTest.primary(base,1.5,.5,.02);var recovery=Optional.of(new RecoveryConfiguration3DOF(.05,.253));
        var sourceGuide=new SourceMotorGroundRunConfiguration3DOF.GuideConfiguration(guide.guide(),guide.attachments(),guide.policy());
        var result=run(sourceRun(base,p,Optional.of(sourceGuide),recovery,guide.alignmentMode(),List.of()));
        var old=new SimulationEngine3DOF().runUntilGround(new UntilGroundRunConfiguration3DOF(guide.vehicle(),Optional.of(guide),recovery,guide.alignmentMode()),GroundTerminationTest.control(v->{}));
        assertEquals(RunControl3DOF.Status.GROUND,result.terminationReason(),result.failure().toString());
        assertEquals(old.samples(),result.samples());assertEquals(old.events(),result.events());assertEquals(old.recoveryEvents(),result.recoveryEvents());assertEquals(old.guideTransitions(),result.guideTransitions());
    }
    @Test void sourceCancellationAndPositiveThrustRecoveryRejectionPreserveHistory() {
        var base=RecoveryIntegrationTest.coast(.01,1,1,true);var p=equivalent(base.physicsContext().motorInstallation().motor());var c=sourceRun(base,p,Optional.empty(),Optional.of(new RecoveryConfiguration3DOF(.05,0)),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP,List.of());
        var unsupported=run(c);assertEquals(RunControl3DOF.Status.UNSUPPORTED,unsupported.terminationReason());assertEquals("RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST",unsupported.failure().orElseThrow().code());assertEquals(1,unsupported.recoveryEvents().size());
        var ctl=GroundTerminationTest.control(v->{});ctl.requestCancellation();var cancelled=new SimulationEngine3DOF().runSourceUntilGround(c,ctl);
        assertEquals(RunControl3DOF.Status.CANCELLED,cancelled.terminationReason());assertEquals(0,cancelled.acceptedSegments());assertEquals(1,cancelled.samples().size());assertTrue(cancelled.events().isEmpty());
    }
    public static SourceMotorGroundRunConfiguration3DOF testOnlyTeknofestRun(String name,boolean guided,Optional<RecoveryConfiguration3DOF> recovery) {
        var motor=teknofest(name);var template=NativeDemoTrajectoryTest.configuration(.02,1);var old=template.physicsContext();
        var raw=new SingleStageRocketGeometry(.2,new ConicalNoseGeometry(.4,NoseConstructionMode.HOLLOW_SHELL,.003),new CylindricalBodyGeometry(2,.003),
            new TrapezoidalFinSetGeometry(4,.3,.15,.15,.1,1.8,.004,FinCrossSection.SQUARE,FinAngularArrangement.EQUALLY_SPACED),
            new MotorAttachmentGeometry(new MotorMountTubeGeometry(1.2,motor.diameterM()+.005,.002,0),new CenteringRingPairGeometry(.004),.005),ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
        var geometry=new GeometryResolver().resolve(raw);var structure=new StructuralMassPropertiesCalculator().evaluate(geometry,VehicleTestData.materials());
        var install=new MotorInstallationResolver().resolveRuntime(geometry,motor);
        var context=new RuntimePhysicsEvaluationContext3DOF(geometry,old.aerodynamicSurfaces(),structure,install,motor.propertyModelProfile(),old.basicDragModelProfile(),old.translationalDynamicsModelProfile(),
            old.environmentPositionMappingModel(),old.launchEnvironmentAltitudeM(),old.propulsionTimeline(),old.atmosphereModel(),old.airPropertiesCalculator(),old.gravityModel(),old.windModel());
        var launch=new LaunchConditions3DOF(new double[]{100,-200,50},new double[]{0,0,guided?0:20},new double[]{0,0,1});
        var guide=guided?Optional.of(new SourceMotorGroundRunConfiguration3DOF.GuideConfiguration(
            new LaunchGuideGeometry("TEST_ONLY",LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL,1.5,"TEST_ONLY, not competition usable length"),
            new GuideAttachmentGeometry("TEST_ONLY",.5,.02,"TEST_ONLY contact coordinates"),LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE)):Optional.<SourceMotorGroundRunConfiguration3DOF.GuideConfiguration>empty();
        return new SourceMotorGroundRunConfiguration3DOF(launch,context,template.eventDetectionProfile(),template.fixedStepConfig(),template.terminalEventHandlingModel(),
            List.of(new AttachedHardwareMass("TEST_ONLY 100 kg ballast, not a real vehicle",100,1.2)),guide,recovery,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
    }
    @Test void allThreeTeknofestSourcesExecuteOnlyAsDeclaredSyntheticSubsonicFixtures() {
        for(String name:List.of("L1256","M1850","N3300")) {
            var c=testOnlyTeknofestRun(name,false,Optional.empty());var result=run(c);
            assertEquals(RunControl3DOF.Status.GROUND,result.terminationReason(),name+": "+result.failure());
            assertTrue(result.failure().isEmpty());assertTrue(result.samples().stream().allMatch(s->s.physics().flightConditions().mach()<=1));
            var profile=(SourceBackedMotorProfile)c.physicsContext().motorInstallation().motor();
            var evaluator=new MotorThrustCurveEvaluator();
            for(var sample:result.samples()) {
                assertEquals(evaluator.evaluateRuntime(profile,sample.physics().motorTimeS()),sample.physics().motorThrustState());
                assertEquals(sample.physics().motorMassProperties().massKg()+c.physicsContext().structuralMassProperties().structureMassKg()+100,sample.physics().rocketMassProperties().totalMassKg());
            }
            var burnout=result.events().stream().filter(e->e.eventType()==FlightEventType.BURNOUT).toList();assertEquals(1,burnout.size());
            assertEquals(profile.selectedThrustSource().record().curveEndTimeS(),burnout.get(0).timeS());
            System.out.println("TEST_ONLY_TF_RUNTIME "+name+" ground="+result.estimatedGroundContactTimeS().orElseThrow()+" accepted="+result.acceptedSegments()+" source="+profile.selectedThrustSource().source().sha256());
        }
    }
    @Test void declaredTeknofestGuideFixtureSharesRecoveryAndGroundHandling() {
        var c=testOnlyTeknofestRun("L1256",true,Optional.of(new RecoveryConfiguration3DOF(.5,.25)));var result=run(c);
        assertEquals(RunControl3DOF.Status.GROUND,result.terminationReason(),result.failure().toString());
        assertTrue(result.guideTransitions().stream().anyMatch(e->e.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END));
        assertEquals(2,result.recoveryEvents().size());assertEquals(RecoveryFlightResult3DOF.State.ACTIVE,result.recoveryState());
    }
    @Test void sourceMidRunCancellationAndPendingGroundPrecedenceRemainIntact() {
        var c=sourceRun(NativeDemoTrajectoryTest.configuration(.02,1),equivalent(MotorCatalogs.AEROTECH_F50_4T),Optional.empty(),Optional.of(new RecoveryConfiguration3DOF(.05,100)),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP,List.of());
        var ground=run(c);assertEquals(RunControl3DOF.Status.GROUND,ground.terminationReason());assertEquals(RecoveryFlightResult3DOF.State.PENDING,ground.recoveryState());assertEquals(1,ground.recoveryEvents().size());
        var ref=new AtomicReference<RunControl3DOF>();var ctl=GroundTerminationTest.control(p->{if(p.acceptedSegments()==10)ref.get().requestCancellation();});ref.set(ctl);
        var cancelled=new SimulationEngine3DOF().runSourceUntilGround(c,ctl);assertEquals(RunControl3DOF.Status.CANCELLED,cancelled.terminationReason());assertEquals(10,cancelled.acceptedSegments());assertEquals(11,cancelled.samples().size());
    }
    @Test void sourceZeroDelayDoesNotCoupleEjectionAndCannotDuplicateActivationSamples() {
        var c=sourceRun(NativeDemoTrajectoryTest.configuration(.02,1),equivalent(MotorCatalogs.AEROTECH_F50_4T),Optional.empty(),Optional.of(new RecoveryConfiguration3DOF(.05,0)),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP,List.of());
        var result=run(c);assertEquals(RunControl3DOF.Status.GROUND,result.terminationReason());assertEquals(2,result.recoveryEvents().size());
        var activation=result.recoveryEvents().get(1).point();assertEquals(result.recoveryEvents().get(0).point(),activation);
        assertEquals(1,result.samples().stream().filter(s->s.point().timeS()==activation.timeS()).count());
        assertNotEquals(c.physicsContext().motorInstallation().motor() instanceof SourceBackedMotorProfile p?p.ejection().delayS().orElseThrow():0,activation.timeS());
    }
    @Test void sourceInvalidInstallationAndUnacceptedCurvesFailBeforeFlight() {
        var profile=teknofest("N3300");assertEquals("MOTOR_DIAMETER_EXCEEDS_MOUNT",assertThrows(MotorInstallationError.class,()->new MotorInstallationResolver().resolveRuntime(MotorInstallationTest.geometry(),profile)).errorCode());
        var catalog=profile.catalogProfile();var old=profile.selectedThrustSource();var unaccepted=new SourceCurve(old.curveId(),old.source(),0,old.ejectionMetadata(),AcceptanceStatus.IMPORTED_NOT_ACCEPTED);
        var changed=new CatalogMotorProfile(catalog.profileId(),catalog.identity(),catalog.hardware(),List.of(unaccepted),catalog.massObservations(),catalog.geometryObservations(),catalog.compatibilityIssues());
        error("UNACCEPTED_THRUST_SOURCE",()->VehicleTestData.replace(profile,"catalog_profile",changed));
        var dry=catalog.massObservations().stream().filter(m->m.quantity().equals("ENG_DERIVED_DRY")).findFirst().orElseThrow();
        error("INVALID_MASS_OBSERVATION_ROLE",()->VehicleTestData.replace(profile,"initial_mass_observation",dry));
    }
    @Test void runtimeBoundaryFactoryUsesSelectedCurveAndExplicitSourcePoliciesAtShiftedIgnition() {
        var original=MotorPropertiesTest.sourceMotor();var profile=equivalent(original);
        var base=NativeDemoTrajectoryTest.configuration(.01,1);
        // A source profile's grids are independent; here mass/CG nodes extend beyond the thrust endpoint.
        var installed=new MotorInstallationResolver().resolveRuntime(base.physicsContext().resolvedGeometry(),profile);
        var c=context(base,profile);
        for(double ignition:new double[]{-2,5}) {
            var shifted=(RuntimePhysicsEvaluationContext3DOF)VehicleTestData.replace(c,"propulsion_timeline",new PropulsionTimeline(ignition));
            var times=MotorTimeBoundarySchedule3DOF.fromRuntimeContext(shifted).boundaryTimesS();
            assertTrue(times.contains(ignition));assertTrue(times.contains(ignition+.5));assertTrue(times.contains(ignition+1));assertTrue(times.contains(ignition+.8));
            assertEquals(times.stream().distinct().count(),times.size());
        }
    }
    @Test void insufficientExplicitCurveCoverageIsRejectedAtSourceBinding() {
        var p=equivalent(MotorCatalogs.AEROTECH_F50_4T);
        error("INSUFFICIENT_CURVE_COVERAGE",()->new SourceBackedMotorProfile(p.catalogProfile(),p.selectedCurveId(),p.motorType(),p.initialMassObservation(),p.propellantMassObservation(),p.geometryInterpretation(),p.ejection(),p.certificationEvidence(),
            List.of(new MotorMassSample(0,p.initialMassKg()),new MotorMassSample(.1,p.initialMassKg())),Optional.of("TEST_ONLY incomplete curve"),null,Optional.empty(),p.propertyModelProfile()));
    }
    @Test void sourceFlightCannotUseALegacyMotorPlaceholder() {
        var base=NativeDemoTrajectoryTest.configuration(.02,1);var profile=equivalent(MotorCatalogs.AEROTECH_F50_4T);
        var c=sourceRun(base,profile,Optional.empty(),Optional.empty(),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP,List.of());
        var legacyView=new MotorInstallationResolver().resolveRuntime(base.physicsContext().resolvedGeometry(),MotorPhysicsInputs.fromLegacyMotor(MotorCatalogs.AEROTECH_F50_4T));
        var context=(RuntimePhysicsEvaluationContext3DOF)VehicleTestData.replace(c.physicsContext(),"motor_installation",legacyView);
        error("SOURCE_PROFILE_REQUIRED",()->VehicleTestData.replace(c,"physics_context",context));
    }
    @Test void sourceImmutabilityAndRepeatedRunsHaveNoCrossRunState() {
        var p=equivalent(MotorPropertiesTest.sourceMotor());assertThrows(UnsupportedOperationException.class,()->p.massCurve().clear());assertThrows(UnsupportedOperationException.class,()->p.qualifications().clear());
        var c=sourceRun(NativeDemoTrajectoryTest.configuration(.02,1),equivalent(MotorCatalogs.AEROTECH_F50_4T),Optional.empty(),Optional.of(new RecoveryConfiguration3DOF(.05,0)),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP,List.of());
        var a=run(c);var b=run(c);assertEquals(a.samples(),b.samples());assertEquals(a.events(),b.events());assertEquals(a.recoveryEvents(),b.recoveryEvents());
        assertThrows(UnsupportedOperationException.class,()->a.samples().clear());
    }
}
