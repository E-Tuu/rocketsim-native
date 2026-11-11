package org.roketsim.core;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.propulsion.catalog.*;
import org.roketsim.core.simulation.*;
import static org.roketsim.core.propulsion.catalog.CatalogMotorProfile.Readiness.*;

/** N37-02.1: constructor bypass, typed numerical outcomes, and historical/current evidence. */
class SourceMotorCorrectionTest {
    static RuntimePhysicsEvaluationContext3DOF context() {
        return SourceMotorRuntimeTest.context(NativeDemoTrajectoryTest.configuration(.02,1),
            SourceMotorRuntimeTest.equivalent(MotorCatalogs.AEROTECH_F50_4T));
    }
    static void installationError(String field,RuntimePhysicsEvaluationContext3DOF c,RuntimeMotorInstallation i) {
        var error=assertThrows(SourceMotorValidationError.class,()->VehicleTestData.replace(c,"motor_installation",i));
        assertEquals("RUNTIME_INSTALLATION_GEOMETRY_MISMATCH",error.errorCode());assertEquals(field,error.fieldName());
    }
    @Test void directlyConstructedInconsistentMotorSpanIsRejected() {
        var i=context().motorInstallation();
        var error=assertThrows(SourceMotorValidationError.class,()->new RuntimeMotorInstallation(i.motor(),Math.nextUp(i.motorFrontXGeoM()),
            i.motorAftXGeoM(),i.nominalRadialClearanceM(),i.axialEngagementLengthM()));
        assertEquals("RUNTIME_MOTOR_SPAN_MISMATCH",error.errorCode());
    }
    @Test void declaredClearanceCannotOverrideActualVehicleFit() {
        var c=context();var i=c.motorInstallation();
        installationError("nominalRadialClearanceM",c,new RuntimeMotorInstallation(i.motor(),i.motorFrontXGeoM(),i.motorAftXGeoM(),
            i.nominalRadialClearanceM()+.01,i.axialEngagementLengthM()));
    }
    @Test void declaredEngagementCannotOverrideActualVehicleFit() {
        var c=context();var i=c.motorInstallation();
        installationError("axialEngagementLengthM",c,new RuntimeMotorInstallation(i.motor(),i.motorFrontXGeoM(),i.motorAftXGeoM(),
            i.nominalRadialClearanceM(),i.axialEngagementLengthM()+.01));
    }
    @Test void manuallyConstructedLargeMotorCannotBypassVehicleMountCheck() {
        var c=context();var n=SourceMotorRuntimeTest.teknofest("N3300");double aft=c.motorInstallation().motorAftXGeoM();
        var manual=new RuntimeMotorInstallation(n,aft-n.lengthM(),aft,.001,.1);
        var error=assertThrows(MotorInstallationError.class,()->VehicleTestData.replace(c,"motor_installation",manual));
        assertEquals("MOTOR_DIAMETER_EXCEEDS_MOUNT",error.errorCode());
    }
    @Test void internallyConsistentSpanStillMustUseActualVehicleDatum() {
        var c=context();var i=c.motorInstallation();double aft=i.motorAftXGeoM()+.01;
        installationError("motorFrontXGeoM",c,new RuntimeMotorInstallation(i.motor(),aft-i.motor().lengthM(),aft,
            i.nominalRadialClearanceM(),i.axialEngagementLengthM()));
    }
    @Test void resolverGeneratedInstallationsRemainAcceptedWithoutNewTolerance() {
        assertNotNull(context());
        for(String motor:List.of("L1256","M1850","N3300")) {
            // Construction only: these remain explicitly TEST_ONLY vehicle interpretations.
            var c=SourceMotorRuntimeTest.testOnlyTeknofestRun(motor,false,Optional.empty());
            assertEquals(c.physicsContext().motorInstallation(),new MotorInstallationResolver().resolveRuntime(
                c.physicsContext().resolvedGeometry(),c.physicsContext().motorInstallation().motor()));
        }
    }
    @Test void sourceAlignedNonadvancingTimeIsNumericalAndRetainsInitialHistory() {
        var base=NativeDemoTrajectoryTest.configuration(.02,1);
        var c=SourceMotorRuntimeTest.sourceRun(base,SourceMotorRuntimeTest.equivalent(MotorCatalogs.AEROTECH_F50_4T),
            Optional.empty(),Optional.empty(),IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES,List.of());
        c=(SourceMotorGroundRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.propulsion_timeline",new PropulsionTimeline(1e16));
        var result=new SimulationEngine3DOF().runSourceUntilGround(c,GroundTerminationTest.control(p->{}));
        assertEquals(RunControl3DOF.Status.NUMERICAL_FAILURE,result.terminationReason());
        assertEquals("MOTOR_BOUNDARY_NONADVANCING_TIME",result.failure().orElseThrow().code());
        assertEquals(0,result.acceptedSegments());assertEquals(1,result.samples().size());
        assertEquals(1e16,result.lastAcceptedSample().orElseThrow().point().timeS());assertTrue(result.events().isEmpty());
    }
    @Test void sharedLegacyGroundPolicyAlsoClassifiesAlignedTimeFailureNumerically() {
        var base=(SimulationRunConfiguration3DOF)VehicleTestData.replace(NativeDemoTrajectoryTest.configuration(.02,1),
            "physics_context.propulsion_timeline",new PropulsionTimeline(1e16));
        var c=new UntilGroundRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),Optional.empty(),
            Optional.empty(),IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        var result=new SimulationEngine3DOF().runUntilGround(c,GroundTerminationTest.control(p->{}));
        assertEquals(RunControl3DOF.Status.NUMERICAL_FAILURE,result.terminationReason());assertEquals(1,result.samples().size());
    }
    @Test void unrelatedModelDomainErrorsAreNotReclassifiedAsNumerical() {
        var c=SourceMotorRuntimeTest.sourceRun(NativeDemoTrajectoryTest.configuration(.02,1),
            SourceMotorRuntimeTest.equivalent(MotorCatalogs.AEROTECH_F50_4T),Optional.empty(),Optional.empty(),
            IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES,List.of());
        c=(SourceMotorGroundRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.wind_model",new Object());
        var result=new SimulationEngine3DOF().runSourceUntilGround(c,GroundTerminationTest.control(p->{}));
        assertEquals(RunControl3DOF.Status.MODEL_DOMAIN_FAILURE,result.terminationReason());
        assertTrue(result.failure().orElseThrow().message().contains("wind_model"));assertEquals(0,result.acceptedSegments());
    }
    @Test void guidedSourceBoundaryFailureAlsoPreservesSupportedPadHistory() {
        var c=SourceMotorRuntimeTest.testOnlyTeknofestRun("L1256",true,Optional.empty());
        c=(SourceMotorGroundRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.propulsion_timeline",new PropulsionTimeline(1e16));
        var result=new SimulationEngine3DOF().runSourceUntilGround(c,GroundTerminationTest.control(p->{}));
        assertEquals(RunControl3DOF.Status.NUMERICAL_FAILURE,result.terminationReason());
        assertEquals("MOTOR_BOUNDARY_NONADVANCING_TIME",result.failure().orElseThrow().code());
        assertEquals(0,result.acceptedSegments());assertEquals(1,result.samples().size());
        assertEquals(FlightPhase3DOF.PAD_CONTACT,result.lastAcceptedSample().orElseThrow().phase());
        assertTrue(result.guideTransitions().isEmpty());assertTrue(result.events().isEmpty());
    }
    @Test void currentCatalogReportsReloadableSupportWithoutPromotingPhysicalReadiness() {
        for(var p:Teknofest2026MotorCatalog.load()) {
            var current=p.currentCoreCompatibility();
            assertEquals(Set.of(CATALOG_AVAILABLE,THRUST_SOURCE_VALIDATED),current.availableCapabilities());
            assertTrue(current.issues().stream().noneMatch(i->i.code().equals("RELOADABLE_TYPE_UNREPRESENTABLE")));
            for(String unresolved:List.of("IDENTITY_VARIANT_MAPPING_UNRESOLVED","CERTIFICATION_REFERENCE_MISSING",
                    "INSTALLATION_DATUM_UNRESOLVED","MOTOR_PROPERTY_PROFILE_NOT_SELECTED","INERTIA_INPUTS_UNAVAILABLE"))
                assertTrue(current.issues().stream().anyMatch(i->i.code().equals(unresolved)));
            assertEquals("reloadable",MotorType.RELOADABLE.value());
        }
    }
    @Test void historicalCompatibilityEvidenceIsExplicitImmutableAndSourceDataIsUnchanged() {
        for(var p:Teknofest2026MotorCatalog.load()) {
            var historical=p.historicalCoreCompatibility();
            assertEquals(p.compatibilityIssues().size(),historical.issues().size());
            assertTrue(historical.issues().stream().allMatch(i->i.detail().startsWith("HISTORICAL MOTOR-001 / legacy contract snapshot:")));
            assertTrue(historical.issues().stream().anyMatch(i->i.code().equals("RELOADABLE_TYPE_UNREPRESENTABLE")));
            assertTrue(p.compatibilityIssues().stream().filter(i->i.code().equals("RELOADABLE_TYPE_UNREPRESENTABLE"))
                .allMatch(i->i.detail().startsWith("HISTORICAL MOTOR-001, before N37-02:")));
            assertThrows(UnsupportedOperationException.class,()->historical.issues().clear());
            var bound=SourceMotorRuntimeTest.teknofest(p.identity().competitionDesignation());
            assertEquals(p.identity(),bound.catalogProfile().identity());assertEquals(p.massObservations(),bound.catalogProfile().massObservations());
            assertEquals(p.thrustSources().get(0).source().sha256(),bound.selectedThrustSource().source().sha256());
            assertTrue(bound.certificationEvidence().isEmpty());
        }
    }
}
