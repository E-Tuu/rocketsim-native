package org.roketsim.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.roketsim.core.dynamics.InitialStateBuilder;
import org.roketsim.core.geometry.GeometryResolver;
import org.roketsim.core.geometry.ResolvedRocketGeometry;
import org.roketsim.core.mass.*;
import org.roketsim.core.mass.StructuralMassOverride.Measurement;
import org.roketsim.core.materials.BulkMaterial;
import org.roketsim.core.materials.SingleStageRocketMaterials;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.simulation.PreparedSourceVehicle3DOF.*;
import static org.junit.jupiter.api.Assertions.*;

class PreparedSourceVehicleTest {
    private static final String BODY = "structure:body";
    private static final String NOSE = "structure:nose";
    private static final StructuralMassPropertiesCalculator CALCULATOR = new StructuralMassPropertiesCalculator();

    private static StructuralMassOverride measured(String id, Double mass, Double cg) {
        return new StructuralMassOverride(id, Optional.ofNullable(mass).map(v -> new Measurement(v, id + " scale")),
            Optional.ofNullable(cg).map(v -> new Measurement(v, id + " balance")));
    }

    private static SourceMotorGroundRunConfiguration3DOF f50(List<AttachedHardwareMass> hardware) {
        return SourceMotorRuntimeTest.sourceRun(NativeDemoTrajectoryTest.configuration(.02, 1),
            SourceMotorRuntimeTest.equivalent(MotorCatalogs.AEROTECH_F50_4T), Optional.empty(), Optional.empty(),
            IntegrationStepAlignmentMode.LEGACY_FIXED_STEP, hardware);
    }

    private static HardwareEvidence evidence(AttachedHardwareMass item) {
        return new HardwareEvidence(item.componentId(), Origin.MEASURED, "test scale", Origin.MODEL_BASED, "test placement");
    }

    private static PreparedSourceVehicle3DOF prepare(SourceMotorGroundRunConfiguration3DOF run,
            List<StructuralMassOverride> overrides, List<IncludedItem> included, List<HardwareEvidence> evidence) {
        return PreparedSourceVehicle3DOF.prepare("fixture:r1", "TEST_ONLY design", VehicleTestData.materials(),
            run, overrides, included, evidence);
    }

    private static VehicleConfigurationError error(String code, String field, Runnable action) {
        var failure = assertThrows(VehicleConfigurationError.class, action::run);
        assertEquals(code, failure.errorCode());
        assertEquals(field, failure.fieldName());
        return failure;
    }

    private static StructuralMassProperties analytical(List<StructuralMassOverride> overrides) {
        // Test-only resolved inputs isolate selection and aggregation from volume geometry.
        Object g = new GeometryResolver().resolve(VehicleTestData.geometry());
        String[] parts = {"nose", "body", "finSet", "motorMount", "centeringRingPair"};
        double[] volumes = {1, 2, 1, 1, 1};
        double[] centroids = {.25, .75, 1, 1.25, 1.5};
        for (int i = 0; i < parts.length; i++) {
            g = VehicleTestData.replace(g, parts[i] + "MaterialVolumeM3", volumes[i]);
            g = VehicleTestData.replace(g, parts[i] + "VolumeCentroidXGeoM", centroids[i]);
        }
        g = VehicleTestData.replace(g, "overallLengthM", 2.0);
        var unit = new BulkMaterial("TEST_ONLY unit density", 1);
        return CALCULATOR.evaluateWithMeasurements((ResolvedRocketGeometry) g,
            new SingleStageRocketMaterials(unit, unit, unit, unit, unit), overrides);
    }

    private static void totals(StructuralMassProperties result, double mass, double moment) {
        assertEquals(mass, result.structureMassKg());
        assertEquals(moment / mass, result.structureCgXGeoM());
    }

    @Test void analyticalFieldReplacementAndTotals() {
        var baseline = analytical(List.of());
        totals(baseline, 6, 5.5);
        var mass = analytical(List.of(measured(BODY, 3.0, null)));
        assertEquals(3, mass.body().massKg());
        assertEquals(.75, mass.body().cgXGeoM());
        totals(mass, 7, 6.25);
        var cg = analytical(List.of(measured(BODY, null, 1.0)));
        assertEquals(2, cg.body().massKg());
        assertEquals(1, cg.body().cgXGeoM());
        totals(cg, 6, 6);
        var both = analytical(List.of(measured(BODY, 3.0, 1.0)));
        totals(both, 7, 7);
        assertEquals(baseline.nose(), both.nose());
        assertEquals(baseline.fins(), both.fins());
        assertEquals(baseline.motorMount(), both.motorMount());
        assertEquals(baseline.centeringRings(), both.centeringRings());
        assertEquals(baseline.body().material(), both.body().material());
    }

    @Test void multipleTargetsAggregateInStructuralOrder() {
        var body = measured(BODY, 3.0, null);
        var nose = measured(NOSE, null, .375);
        var forward = analytical(List.of(body, nose));
        totals(forward, 7, 6.375);
        assertEquals(forward, analytical(List.of(nose, body)));
        var prepared = prepare(f50(List.of()), List.of(body, nose), List.of(), List.of());
        assertEquals(List.of(body, nose), prepared.overrides());
        assertEquals(NOSE + " balance", prepared.overrides().get(1).cgXGeoM().orElseThrow().sourceReference());
    }

    @Test void independentTotalMassAndCgAtTwoMotorSnapshots() {
        var structure = analytical(List.of(measured(BODY, 3.0, 1.0)));
        var total = new RocketMassPropertiesCalculator();
        var first = total.evaluate(structure, new MotorMassProperties(.5, .25, 1.25, null), new double[]{.25}, new double[]{.5});
        assertEquals(new RocketMassProperties(7.75, 1), first);
        var later = total.evaluate(structure, new MotorMassProperties(.25, 0, 1, null), new double[]{.25}, new double[]{.5});
        assertEquals(new RocketMassProperties(7.5, 7.375 / 7.5), later);
    }

    @Test void validationUsesRowOrderAndMassBeforeCg() {
        for (double mass : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            var invalid = new StructuralMassOverride(BODY, Optional.of(new Measurement(mass, "")),
                Optional.of(new Measurement(Double.NaN, "")));
            error("INVALID_MEASURED_MASS", "overrides[0].massKg", () -> analytical(List.of(invalid)));
        }
        for (double cg : new double[]{-.01, 2.01, Double.NaN, Double.NEGATIVE_INFINITY})
            error("INVALID_MEASURED_CG", "overrides[0].cgXGeoM", () -> analytical(List.of(measured(BODY, null, cg))));
        assertEquals(0, analytical(List.of(measured(BODY, null, 0.0))).body().cgXGeoM());
        assertEquals(2, analytical(List.of(measured(BODY, null, 2.0))).body().cgXGeoM());
        var missing = new StructuralMassOverride(BODY, Optional.of(new Measurement(3, " ")), Optional.empty());
        error("MISSING_PROPERTY_PROVENANCE", "overrides[0].massKg.sourceReference",
            () -> analytical(List.of(missing, measured("motor:installed", 1.0, null))));
        error("UNSUPPORTED_OVERRIDE_TARGET", "overrides[0].componentId",
            () -> analytical(List.of(measured("motor:installed", 1.0, null), missing)));
        error("EMPTY_STRUCTURAL_OVERRIDE", "overrides[0]", () -> analytical(List.of(measured(BODY, null, null))));
        error("DUPLICATE_OVERRIDE_TARGET", "overrides[1].componentId",
            () -> analytical(List.of(measured(BODY, 3.0, null), measured(BODY, null, 1.0))));
    }

    @Test void preparationRejectsExactBaselineMismatchesBeforeOverrideValues() {
        var base = f50(List.of());
        var changedGeometry = (SourceMotorGroundRunConfiguration3DOF) VehicleTestData.replace(base,
            "physicsContext.resolvedGeometry.referenceAreaM2", Math.nextUp(base.physicsContext().resolvedGeometry().referenceAreaM2()));
        var changedMass = (SourceMotorGroundRunConfiguration3DOF) VehicleTestData.replace(base,
            "physicsContext.structuralMassProperties.structureMassKg", Math.nextUp(base.physicsContext().structuralMassProperties().structureMassKg()));
        var invalidOverride = List.of(measured(BODY, -1.0, null));
        error("BASELINE_GEOMETRY_MISMATCH", "calculatedRun.physicsContext.resolvedGeometry",
            () -> prepare(changedGeometry, invalidOverride, List.of(), List.of()));
        error("BASELINE_STRUCTURAL_MASS_MISMATCH", "calculatedRun.physicsContext.structuralMassProperties",
            () -> prepare(changedMass, invalidOverride, List.of(), List.of()));
    }

    @Test void ownershipRejectsReservedAndDuplicateClaims() {
        var body = measured(BODY, .3, .7);
        var nose = measured(NOSE, .1, .2);
        var battery = new IncludedItem("battery", BODY);
        error("DUPLICATE_COMPONENT_ID", "includedItems[1].itemId",
            () -> prepare(f50(List.of()), List.of(body), List.of(battery, battery), List.of()));
        error("CONFLICTING_MASS_OWNERSHIP", "includedItems[1].itemId",
            () -> prepare(f50(List.of()), List.of(body, nose), List.of(battery, new IncludedItem("battery", NOSE)), List.of()));
        error("CONFLICTING_MASS_OWNERSHIP", "attachedHardware[0].componentId",
            () -> prepare(f50(List.of(new AttachedHardwareMass("motor:installed", .1, .8))), List.of(), List.of(), List.of()));
        error("CONFLICTING_MASS_OWNERSHIP", "includedItems[0].itemId",
            () -> prepare(f50(List.of()), List.of(body), List.of(new IncludedItem(NOSE, BODY)), List.of()));
        error("INVALID_INCLUDED_ITEM_OWNER", "includedItems[0].structuralOwnerId",
            () -> prepare(f50(List.of()), List.of(measured(BODY, .3, null)), List.of(battery), List.of()));
    }

    @Test void includedHardwareCannotAlsoContributeSeparately() {
        var battery = new AttachedHardwareMass("battery", .1, .6);
        var body = measured(BODY, .3, .7);
        var included = List.of(new IncludedItem("battery", BODY));
        error("CONFLICTING_MASS_OWNERSHIP", "includedItems[0].itemId",
            () -> prepare(f50(List.of(battery)), List.of(body), included, List.of(evidence(battery))));
        var valid = prepare(f50(List.of()), List.of(body), included, List.of());
        assertTrue(valid.runConfiguration().attachedHardware().isEmpty());
        assertEquals(.3, valid.runConfiguration().physicsContext().structuralMassProperties().body().massKg());
        var withoutClaim = prepare(valid.calculatedRun(), List.of(body), List.of(), List.of());
        assertEquals(withoutClaim.runConfiguration(), valid.runConfiguration());
    }

    @Test void hardwareEvidenceCoverageAndAuxiliaryNumbersAreCheckedDuringPreparation() {
        var battery = new AttachedHardwareMass("battery", .1, .6);
        var run = f50(List.of(battery));
        error("INVALID_HARDWARE_EVIDENCE", "hardwareEvidence", () -> prepare(run, List.of(), List.of(), List.of()));
        error("INVALID_HARDWARE_EVIDENCE", "hardwareEvidence[1].componentId",
            () -> prepare(run, List.of(), List.of(), List.of(evidence(battery), evidence(battery))));
        var invalid = new AttachedHardwareMass("battery", Double.NaN, Double.NaN);
        assertEquals("INVALID_AUXILIARY_MASS", assertThrows(MassValidationError.class,
            () -> prepare(f50(List.of(invalid)), List.of(), List.of(), List.of(evidence(invalid)))).errorCode());
        assertSame(run, prepare(run, List.of(), List.of(), List.of(evidence(battery))).runConfiguration());
    }

    @Test void noOverridesPreserveExactF50SourceExecution() {
        var run = f50(List.of());
        var prepared = prepare(run, List.of(), List.of(), List.of());
        assertSame(run, prepared.calculatedRun());
        assertSame(run, prepared.runConfiguration());
        var context = run.physicsContext();
        assertEquals(CALCULATOR.evaluate(context.resolvedGeometry(), prepared.materials()),
            CALCULATOR.evaluateWithMeasurements(context.resolvedGeometry(), prepared.materials(), List.of()));
        var direct = new SimulationEngine3DOF().runSourceUntilGround(run, GroundTerminationTest.control(p -> {}));
        var complete = prepared.runUntilGround(GroundTerminationTest.control(p -> {}));
        assertSame(prepared, complete.configuration());
        assertEquals(RunControl3DOF.Status.GROUND, complete.result().terminationReason());
        assertEquals(direct.samples(), complete.result().samples());
        assertEquals(direct.events(), complete.result().events());
        assertEquals(direct.acceptedSegments(), complete.result().acceptedSegments());
    }

    @Test void geometryDragAndMotorDepletionMatchAtTheSameStateAndTime() {
        var run = f50(List.of());
        var effective = prepare(run, List.of(measured(BODY, .3, .7)), List.of(), List.of()).runConfiguration();
        var original = run.physicsContext();
        var selected = effective.physicsContext();
        assertSame(original.resolvedGeometry(), selected.resolvedGeometry());
        assertSame(original.aerodynamicSurfaces(), selected.aerodynamicSurfaces());
        assertSame(original.motorInstallation(), selected.motorInstallation());
        var state = new InitialStateBuilder().build(run.launchConditions());
        var evaluator = new PhysicsEvaluator3DOF();
        double[] empty = {};
        for (double time : new double[]{0, .25, .75, 2}) {
            var before = evaluator.evaluateRuntime(time, state, run.launchConditions(), original, empty, empty);
            var after = evaluator.evaluateRuntime(time, state, run.launchConditions(), selected, empty, empty);
            assertEquals(before.motorMassProperties(), after.motorMassProperties());
            assertEquals(before.motorThrustState(), after.motorThrustState());
            assertEquals(before.basicDrag(), after.basicDrag());
        }
        var motor = new MotorPropertyEvaluator();
        assertTrue(motor.evaluateRuntime(selected.motorInstallation(), selected.motorPropertyModelProfile(), 0).massKg()
            > motor.evaluateRuntime(selected.motorInstallation(), selected.motorPropertyModelProfile(), 2).massKg());
    }

    @Test void threeSyntheticSourceVehiclesRetainSourceAndGroundOutcome() {
        for (String name : List.of("L1256", "M1850", "N3300")) {
            var run = SourceMotorRuntimeTest.testOnlyTeknofestRun(name, false, Optional.empty());
            var prepared = prepare(run, List.of(measured(BODY, null, 1.2)), List.of(),
                run.attachedHardware().stream().map(PreparedSourceVehicleTest::evidence).toList());
            assertSame(run.physicsContext().motorInstallation(), prepared.runConfiguration().physicsContext().motorInstallation());
            var result = prepared.runUntilGround(GroundTerminationTest.control(p -> {})).result();
            assertEquals(RunControl3DOF.Status.GROUND, result.terminationReason(), name + ": " + result.failure());
            assertTrue(result.groundEvent().isPresent());
        }
    }

    @Test void guidedMeasuredRunRetainsRecoveryAndGroundSequence() {
        var run = SourceMotorRuntimeTest.testOnlyTeknofestRun("L1256", true, Optional.of(new RecoveryConfiguration3DOF(.5, .25)));
        var prepared = prepare(run, List.of(measured(BODY, null, 1.2)), List.of(),
            run.attachedHardware().stream().map(PreparedSourceVehicleTest::evidence).toList());
        var result = prepared.runUntilGround(GroundTerminationTest.control(p -> {})).result();
        assertEquals(RunControl3DOF.Status.GROUND, result.terminationReason(), result.failure().toString());
        assertTrue(result.guideTransitions().stream().anyMatch(e -> e.kind() == LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END));
        assertEquals(2, result.recoveryEvents().size());
        assertTrue(result.recoveryEvents().get(0).point().timeS() < result.recoveryEvents().get(1).point().timeS());
        assertTrue(result.recoveryEvents().get(1).point().timeS() < result.groundEvent().orElseThrow().timeS());
        assertEquals(RecoveryFlightResult3DOF.State.ACTIVE, result.recoveryState());
        GroundTerminationTest.ordered(result);
    }

    @Test void immutablePreparedAssociationSurvivesCancellationWithPartialHistory() {
        var run = f50(List.of(new AttachedHardwareMass("payload", .01, .6)));
        var overrides = new ArrayList<>(List.of(measured(BODY, .3, .7)));
        var included = new ArrayList<>(List.of(new IncludedItem("battery", BODY)));
        var evidence = new ArrayList<>(List.of(evidence(run.attachedHardware().get(0))));
        var prepared = prepare(run, overrides, included, evidence);
        overrides.clear();
        included.clear();
        evidence.clear();
        assertEquals(1, prepared.overrides().size());
        assertEquals(1, prepared.includedItems().size());
        assertEquals(1, prepared.hardwareEvidence().size());
        assertThrows(UnsupportedOperationException.class, () -> prepared.overrides().clear());
        assertThrows(UnsupportedOperationException.class, () -> prepared.includedItems().clear());
        assertThrows(UnsupportedOperationException.class, () -> prepared.hardwareEvidence().clear());
        var ref = new AtomicReference<RunControl3DOF>();
        var control = GroundTerminationTest.control(p -> {
            if (p.acceptedSegments() == 3) ref.get().requestCancellation();
        });
        ref.set(control);
        var complete = prepared.runUntilGround(control);
        assertSame(prepared, complete.configuration());
        assertEquals("fixture:r1", complete.configuration().configurationId());
        assertEquals(RunControl3DOF.Status.CANCELLED, complete.result().terminationReason());
        assertEquals(3, complete.result().acceptedSegments());
        assertTrue(complete.result().groundEvent().isEmpty());
        GroundTerminationTest.ordered(complete.result());
    }
}
