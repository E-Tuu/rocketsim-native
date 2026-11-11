package org.roketsim.core.simulation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.roketsim.core.GroundTerminationTest;
import org.roketsim.core.SourceMotorRuntimeTest;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.math.Vector;
import org.roketsim.core.numerics.*;
import org.roketsim.core.propulsion.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.simulation.IntegrationStepAlignmentMode.*;
import static org.roketsim.core.simulation.PreparedSourceVehicle3DOF.*;
import static org.roketsim.core.simulation.RunControl3DOF.Status.*;

/** N37-04B1 verifies event contracts and analytic seams. Native sweeps are observations. */
class IntegratedFlightVerificationTest {
    private static final String BODY = "structure:body";
    private static final String NOSE = "structure:nose";

    private enum Family {
        L("L1256", "TFL1256WS", "756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd",
            2.967, "3650.044730405055", 3.905, 1.905, 1.6497),
        M("M1850", "TFM1850W", "111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc",
            6.750, "7649.329", 7.010, 4.103, 1.9223),
        N("N3300", "TFN3300R-P", "10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8",
            4.613, "14325.010771", 12.645, 7.668, 2.35195);

        final String shortName, designation, sha256, impulse;
        final double end, loaded, propellant, cg;

        Family(String shortName, String designation, String sha256, double end, String impulse,
                double loaded, double propellant, double cg) {
            this.shortName = shortName;
            this.designation = designation;
            this.sha256 = sha256;
            this.end = end;
            this.impulse = impulse;
            this.loaded = loaded;
            this.propellant = propellant;
            this.cg = cg;
        }
    }

    // Test-only copy of the N37-03C fixture definitions. The sweep replaces only FixedStepConfig.
    private enum Variant { DIRECT, GUIDE, RECOVERY, CANCEL }

    private record Fixture(Family family, SingleStageRocketMaterials materials, SourceMotorGroundRunConfiguration3DOF run,
            List<StructuralMassOverride> overrides, List<IncludedItem> included, List<HardwareEvidence> evidence) {
        PreparedSourceVehicle3DOF prepare(String variant) {
            return prepare(variant, overrides, included, evidence);
        }

        PreparedSourceVehicle3DOF prepare(String variant, List<StructuralMassOverride> selected,
                List<IncludedItem> absorbed, List<HardwareEvidence> references) {
            String suffix = variant.equals("direct") ? "" : ":" + variant;
            return PreparedSourceVehicle3DOF.prepare("n37-03c:" + family + suffix + ":r1",
                "TEST_ONLY N37-03C geometry " + family, materials, run, selected, absorbed, references);
        }
    }

    private static SingleStageRocketGeometry geometry(Family family) {
        return switch (family) {
            case L -> geometry(.160, .320, null, 1.600, .003, fins(3, .240, .120, .110, .070, 1.550, .003),
                new MotorMountTubeGeometry(.700, .080, .002, .010), .004, .005);
            case M -> geometry(.200, .400, .003, 2.000, .003, fins(4, .300, .150, .150, .100, 1.800, .004),
                new MotorMountTubeGeometry(1.050, .080, .002, .015), .005, .005);
            case N -> geometry(.240, .500, .004, 2.400, .004, fins(4, .360, .180, .190, .130, 2.350, .005),
                new MotorMountTubeGeometry(1.250, .104, .003, .020), .006, .008);
        };
    }

    private static SingleStageRocketGeometry geometry(double diameter, double nose, Double noseWall,
            double body, double bodyWall, TrapezoidalFinSetGeometry fins, MotorMountTubeGeometry mount,
            double ring, double overhang) {
        var construction = noseWall == null ? NoseConstructionMode.SOLID : NoseConstructionMode.HOLLOW_SHELL;
        return new SingleStageRocketGeometry(diameter, new ConicalNoseGeometry(nose, construction, noseWall),
            new CylindricalBodyGeometry(body, bodyWall), fins,
            new MotorAttachmentGeometry(mount, new CenteringRingPairGeometry(ring), overhang),
            ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
    }

    private static TrapezoidalFinSetGeometry fins(int count, double root, double tip, double span,
            double offset, double leadingEdge, double thickness) {
        return new TrapezoidalFinSetGeometry(count, root, tip, span, offset, leadingEdge, thickness,
            FinCrossSection.SQUARE, FinAngularArrangement.EQUALLY_SPACED);
    }

    private static SingleStageRocketMaterials materials(Family family, boolean densityTwin) {
        double[] densities = switch (family) {
            case L -> new double[]{1800, densityTwin ? 4500 : 4000, 2000, 2000, 1800};
            case M -> new double[]{650, 900, 1100, 1200, 700};
            case N -> new double[]{500, 1150, 1450, 1000, 1250};
        };
        return new SingleStageRocketMaterials(material(family, "nose", densities[0]),
            material(family, "body", densities[1]), material(family, "fins", densities[2]),
            material(family, "mount", densities[3]), material(family, "rings", densities[4]));
    }

    private static BulkMaterial material(Family family, String component, double density) {
        return new BulkMaterial("TEST_ONLY " + family + "/" + component, density);
    }

    private static StructuralMassOverride override(Family family, String id, Double mass, Double cg) {
        String reference = "TEST_ONLY " + family + "/" + id.substring("structure:".length());
        return new StructuralMassOverride(id, Optional.ofNullable(mass).map(v ->
            new StructuralMassOverride.Measurement(v, reference + "/mass-anchor")),
            Optional.ofNullable(cg).map(v -> new StructuralMassOverride.Measurement(v, reference + "/cg-anchor")));
    }

    private static HardwareEvidence evidence(AttachedHardwareMass item) {
        String reference = "TEST_ONLY " + item.componentId();
        return new HardwareEvidence(item.componentId(), Origin.MODEL_BASED, reference + "/mass",
            Origin.MODEL_BASED, reference + "/cg");
    }

    private static Fixture fixture(Family family, Variant variant) {
        return fixture(family, variant, false, 0, 20);
    }

    private static Fixture fixture(Family family, Variant variant, boolean densityTwin, double ignition, double speed) {
        var motor = SourceMotorRuntimeTest.teknofest(family.shortName);
        var geometry = new GeometryResolver().resolve(geometry(family));
        var material = materials(family, densityTwin);
        var structure = new StructuralMassPropertiesCalculator().evaluate(geometry, material);
        var installation = new MotorInstallationResolver().resolveRuntime(geometry, motor);
        var smooth = new SurfaceFinish("TEST_ONLY " + family + " smooth", 0);
        var context = new RuntimePhysicsEvaluationContext3DOF(geometry,
            new SingleStageRocketAerodynamicSurfaces(smooth, smooth, smooth), structure, installation,
            MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE, BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,
            TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,
            switch (family) { case L -> 1200; case M -> 450; case N -> 800; },
            new PropulsionTimeline(ignition), new USStandardAtmosphere1976Lower(), new DryAirPropertiesCalculator(),
            new ConstantGravityModel(), family == Family.M ? new ConstantWindModel(new double[]{4, -2, 0}) : new NoWindModel());
        var launch = switch (family) {
            case L -> new LaunchConditions3DOF(new double[]{0, 0, 0}, new double[]{0, 0, speed}, new double[]{0, 0, 1});
            case M -> new LaunchConditions3DOF(new double[]{100, -200, 50},
                variant == Variant.GUIDE ? new double[]{0, 0, 0} : new double[]{0, 12, 16}, new double[]{0, .6, .8});
            case N -> new LaunchConditions3DOF(new double[]{-40, 25, 20}, new double[]{0, 0, speed}, new double[]{0, 0, 1});
        };
        Optional<SourceMotorGroundRunConfiguration3DOF.GuideConfiguration> guide = variant == Variant.GUIDE
            ? Optional.of(new SourceMotorGroundRunConfiguration3DOF.GuideConfiguration(
                new LaunchGuideGeometry("TEST_ONLY:M-guide", LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL,
                    1.50, "TEST_ONLY M ideal guide top"),
                new GuideAttachmentGeometry("TEST_ONLY:M-guide", .50, .02, "TEST_ONLY M guide contacts"),
                LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE))
            : Optional.empty();
        var hardware = switch (family) {
            case L -> List.<AttachedHardwareMass>of();
            case M -> List.of(new AttachedHardwareMass("m-ballast", 32, 1.30));
            case N -> List.of(new AttachedHardwareMass("n-ballast", 65, 1.50));
        };
        var run = new SourceMotorGroundRunConfiguration3DOF(launch, context,
            new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
                ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
                GroundReferenceModel.LAUNCH_WORLD_Z_PLANE, EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION),
            new FixedStepConfig(variant == Variant.RECOVERY ? .015625 : .020),
            TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT, hardware, guide,
            variant == Variant.RECOVERY ? Optional.of(new RecoveryConfiguration3DOF(.80, 6.0)) : Optional.empty(),
            variant == Variant.RECOVERY ? LEGACY_FIXED_STEP : ALIGN_MOTOR_SOURCE_BOUNDARIES);
        var overrides = switch (family) {
            case L -> List.<StructuralMassOverride>of();
            case M -> List.of(override(family, BODY, 5.0, null), override(family, NOSE, null, .310));
            case N -> List.of(override(family, BODY, 9.0, 1.750));
        };
        return new Fixture(family, material, run, overrides,
            family == Family.N ? List.of(new IncludedItem("n-electronics", BODY)) : List.of(),
            hardware.stream().map(IntegratedFlightVerificationTest::evidence).toList());
    }

    private record Sweep(Family family, Variant variant, double step) {
        @Override public String toString() { return family + "/" + variant + "/h=" + step; }
    }

    private static Stream<Sweep> grids() {
        return Stream.concat(Stream.of(Family.L, Family.M).flatMap(f -> Stream.of(.020, .010, .005, .0025)
            .map(h -> new Sweep(f, f == Family.M ? Variant.GUIDE : Variant.DIRECT, h))),
            Stream.of(.03125, .015625, .0078125, .00390625).map(h -> new Sweep(Family.N, Variant.RECOVERY, h)));
    }

    private static PreparedSourceVehicle3DOF prepared(Sweep test) {
        var fixture = fixture(test.family(), test.variant());
        var old = fixture.run();
        var run = new SourceMotorGroundRunConfiguration3DOF(old.launchConditions(), old.physicsContext(),
            old.eventDetectionProfile(), new FixedStepConfig(test.step()), old.terminalEventHandlingModel(),
            old.attachedHardware(), old.launchGuide(), old.recovery(), old.alignmentMode());
        return new Fixture(fixture.family(), fixture.materials(), run, fixture.overrides(), fixture.included(), fixture.evidence())
            .prepare(switch (test.variant()) { case GUIDE -> "guide"; case RECOVERY -> "recovery"; default -> "direct"; });
    }

    private record Node(BigDecimal time, BigDecimal thrust) {}
    private static final MathContext DECIMAL = MathContext.DECIMAL128;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private static List<Node> readNodes(SourceBackedMotorProfile motor) {
        var raw = new String(motor.selectedThrustSource().source().sourceBytes(), StandardCharsets.UTF_8);
        var rows = raw.lines().map(String::trim).filter(s -> !s.isEmpty() && !s.startsWith(";")).toList();
        var points = new ArrayList<Node>();
        points.add(new Node(BigDecimal.ZERO, BigDecimal.ZERO));
        for (String row : rows.subList(1, rows.size())) {
            String[] columns = row.split("\\s+");
            points.add(new Node(new BigDecimal(columns[0]), new BigDecimal(columns[1])));
        }
        return points;
    }

    private static BigDecimal impulse(List<Node> nodes, BigDecimal at) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 1; i < nodes.size(); i++) {
            var a = nodes.get(i - 1);
            var b = nodes.get(i);
            if (at.compareTo(a.time()) <= 0) break;
            BigDecimal elapsed = at.min(b.time()).subtract(a.time());
            BigDecimal endThrust = at.compareTo(b.time()) >= 0 ? b.thrust()
                : a.thrust().add(b.thrust().subtract(a.thrust())
                    .multiply(elapsed).divide(b.time().subtract(a.time()), DECIMAL));
            sum = sum.add(a.thrust().add(endThrust).multiply(elapsed).divide(TWO));
        }
        return sum;
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void sourceNodesImpulseDepletionAndInstalledCg(Family family) {
        var context = fixture(family, Variant.DIRECT).run().physicsContext();
        var installation = context.motorInstallation();
        var motor = (SourceBackedMotorProfile) installation.motor();
        var source = motor.selectedThrustSource();
        assertEquals("teknofest26:" + family.designation + ":Rev1", source.curveId());
        assertEquals("Rev1", source.source().revision());
        assertEquals(family.sha256, source.source().sha256());
        assertEquals(family.loaded, motor.initialMassKg());
        assertEquals(family.propellant, motor.propellantMassKg());
        assertEquals(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL, motor.propertyModelProfile().massModel());
        assertEquals(MotorCgEvolutionModel.FIXED_MIDPOINT, motor.propertyModelProfile().cgModel());
        var nodes = readNodes(motor);
        var end = nodes.get(nodes.size() - 1).time();
        assertEquals(family.end, end.doubleValue());
        var total = impulse(nodes, end);
        assertEquals(0, new BigDecimal(family.impulse).compareTo(total));
        assertEquals(nodes.size(), motor.thrustCurve().size());
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            double t = node.time().doubleValue();
            assertEquals(t, motor.thrustCurve().get(i).timeS());
            assertEquals(node.thrust().doubleValue(), motor.thrustCurve().get(i).thrustN());
            var thrust = new MotorThrustCurveEvaluator().evaluateRuntime(motor, t);
            assertEquals(node.thrust().doubleValue(), thrust.thrustN());
            checkMotorSnapshot(family, installation, node.time(), nodes, total, thrust);
        }
        // Mid-segment and post-curve probes are derived from the preserved source grid.
        var middle = nodes.get(nodes.size() / 2);
        var prior = nodes.get(nodes.size() / 2 - 1);
        for (BigDecimal t : List.of(middle.time().add(prior.time()).divide(TWO), end.add(new BigDecimal(".25")))) {
            var thrust = new MotorThrustCurveEvaluator().evaluateRuntime(motor, t.doubleValue());
            checkMotorSnapshot(family, installation, t, nodes, total, thrust);
            if (t.compareTo(end) > 0) assertEquals(0, thrust.thrustN());
        }
    }

    private static void checkMotorSnapshot(Family f, RuntimeMotorInstallation installation,
            BigDecimal time, List<Node> nodes, BigDecimal total, MotorThrustState thrust) {
        BigDecimal expectedImpulse = impulse(nodes, time);
        double expectedMass = BigDecimal.valueOf(f.loaded).subtract(BigDecimal.valueOf(f.propellant)
            .multiply(expectedImpulse.divide(total, DECIMAL))).doubleValue();
        var actual = new MotorPropertyEvaluator().evaluateRuntime(installation,
            MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE, time.doubleValue());
        near("impulse " + f + " at " + time, expectedImpulse.doubleValue(), thrust.cumulativeImpulseNS(), 128, false);
        near("mass " + f + " at " + time, expectedMass, actual.massKg(), 128, false);
        near("installed CG " + f, f.cg, actual.cgXGeoM(), 128, false);
    }

    private static void near(String quantity, double expected, double actual, int ulps, boolean unitScale) {
        double scale = unitScale ? Math.max(1, Math.abs(expected)) : Math.abs(expected);
        double tolerance = expected == 0 && !unitScale ? 0 : ulps * Math.ulp(scale);
        double error = Math.abs(actual - expected);
        assertTrue(error <= tolerance, () -> quantity + " expected=" + expected + " actual=" + actual
            + " abs=" + error + " relative=" + (expected == 0 ? "n/a" : error / Math.abs(expected))
            + " expected-ULPs=" + error / Math.ulp(expected) + " tolerance=" + tolerance);
    }

    private static IntegrationPoint3DOF point(double t, double z, double vz) {
        return new IntegrationPoint3DOF(t, new TranslationalState3DOF(new double[]{0, 0, z}, new double[]{0, 0, vz}));
    }

    private static IntegrationPoint3DOF ballistic(double t) {
        return point(t, 5.1 * t - 4 * t * t, 5.1 - 8 * t);
    }

    @Test
    void ballisticContinuousAndChordOraclesAreSeparate() {
        double apex = 5.1 / 8;
        double continuousGround = 2 * 5.1 / 8;
        assertEquals(.6375, apex);
        near("continuous apex height", 1.625625, ballistic(apex).state().positionWorldM().get(2), 8, true);
        assertEquals(1.275, continuousGround);
        var p = ballistic(0);
        var rk = new ClassicalRK4Integrator3DOF();
        for (int i = 1; i <= 11; i++) {
            p = rk.step(p, new FixedStepConfig(.125),
                (t, s) -> new TranslationalStateDerivative3DOF(s.velocityWorldMS(), Vector.of(new double[]{0, 0, -8})));
            var expected = ballistic(i / 8.0);
            assertEquals(expected.timeS(), p.timeS());
            near("ballistic height", expected.state().positionWorldM().get(2), p.state().positionWorldM().get(2), 32, true);
            near("ballistic vertical speed", expected.state().velocityWorldMS().get(2), p.state().velocityWorldMS().get(2), 32, true);
        }
        var config = GroundTerminationTest.direct(.125, 1, Optional.empty());
        var apogee = event(GroundExecutionTest.detect(config, ballistic(.625), ballistic(.75)), FlightEventType.APOGEE);
        near("linear apogee time", .6375, apogee.timeS(), 8, true);
        near("chord apogee altitude", 1.62, apogee.estimatedState().positionWorldM().get(2), 8, true);
        near("apex chord deficit", .005625, 1.625625 - apogee.estimatedState().positionWorldM().get(2), 8, true);
        var start = ballistic(1.25);
        var end = ballistic(1.375);
        var reports = GroundExecutionTest.detect(config, start, end);
        var ground = event(reports, FlightEventType.GROUND);
        // Analytic endpoint heights are 0.125 and -0.55, so the chord fraction is 5/27.
        double chordTime = 1.25 + .125 * (5.0 / 27);
        near("ground chord fraction", 5.0 / 27, ground.interpolationFraction(), 8, true);
        near("ground chord time", chordTime, ground.timeS(), 8, true);
        near("ground chord velocity", 5.1 - 8 * chordTime, ground.estimatedState().velocityWorldMS().get(2), 8, true);
        assertTrue(ground.timeS() < continuousGround);
        var interior = GroundExecutionTest.execution(config, start);
        assertTrue(interior.runtime.terminal(reports, end));
        interior.captureRuntimeOutcome();
        assertEquals(start, interior.finish().lastAcceptedSample().orElseThrow().point());
        var root = point(continuousGround, 0, -5.1);
        var endpoint = GroundExecutionTest.execution(config, start);
        assertTrue(endpoint.runtime.terminal(GroundExecutionTest.detect(config, start, root), root));
        endpoint.captureRuntimeOutcome();
        var accepted = endpoint.finish();
        assertEquals(GROUND, accepted.terminationReason());
        assertEquals(root, accepted.lastAcceptedSample().orElseThrow().point());
        assertEquals(1, accepted.groundEvent().orElseThrow().interpolationFraction());
    }

    @Test
    void ballisticRecoverySeamTriggersAtFirstStrictDropSample() {
        var runtime = RecoveryTimingTest.runtime(5.1);
        // Start at the analytic launch state at the seam, then feed only analytic accepted points.
        runtime.maximumSampledAltitudeM = 0;
        runtime.maximumSampledAltitudeTimeS = 0;
        for (int i = 1; i <= 12; i++) runtime.observeAccepted(ballistic(i / 16.0));
        near("sampled apex height", 1.625, runtime.maximumSampledAltitudeM, 8, true);
        assertEquals(.625, runtime.maximumSampledAltitudeTimeS);
        // At 0.6875 the drop is 0.009375 m. The next sample at 0.75 first exceeds 0.01 m.
        assertEquals(.75, runtime.triggerTimeS);
        assertEquals(1.75, runtime.activationTimeS);
        assertEquals(ballistic(.75), runtime.events.get(0).point());
        assertEquals(1, runtime.events.size());
    }

    private static FlightEventOccurrence3DOF event(List<FlightEventOccurrence3DOF> events, FlightEventType type) {
        var selected = events.stream().filter(e -> e.eventType() == type).toList();
        assertEquals(1, selected.size(), () -> "expected one " + type + " in " + events);
        return selected.get(0);
    }

    private static RunControl3DOF control(long cancelAt, int beforeCommit) {
        var clock = new AtomicLong();
        var commits = new AtomicLong();
        var reference = new AtomicReference<RunControl3DOF>();
        var control = new RunControl3DOF(new RunControl3DOF.MonitoringPolicy(1, 1_000_000_000_000L, 1000, 100000),
            p -> { if (p.acceptedSegments() >= cancelAt) reference.get().requestCancellation(); },
            w -> {}, () -> clock.addAndGet(1_000_000),
            checkpoint -> {
                if (checkpoint.equals("before_commit") && commits.incrementAndGet() == beforeCommit)
                    reference.get().requestCancellation();
            });
        reference.set(control);
        return control;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grids")
    void nativeCandidateGridPreservesEventContracts(Sweep grid) {
        var prepared = prepared(grid);
        var result = prepared.runUntilGround(control(20_000, -1));
        var flight = result.result();
        var run = prepared.runConfiguration();
        var motor = (SourceBackedMotorProfile) run.physicsContext().motorInstallation().motor();
        var knots = readNodes(motor).stream().map(n -> n.time().doubleValue()).toList();
        // Emit before asserting so the initial outcome and entire accepted prefix survive any failure.
        report(grid.toString(), prepared.configurationId(), flight, knots, run.alignmentMode());
        assertSame(prepared, result.configuration());
        checkHistory(flight);
        if (flight.terminationReason() != GROUND) {
            // Preserve the candidate result as a finding, never as a completed flight.
            String code = flight.failure().orElseThrow().code();
            boolean baseline = grid.step() == (grid.family() == Family.N ? .015625 : .020);
            assertFalse(baseline, () -> "frozen baseline did not complete: " + code);
            assertEquals(NUMERICAL_FAILURE, flight.terminationReason());
            assertTrue(List.of("MOTOR_BOUNDARY_UNREPRESENTABLE_STEP", "MOTOR_BOUNDARY_UNREPRESENTABLE_ENDPOINT",
                "GUIDE_RESTART_TIME_UNREPRESENTABLE", "GUIDE_EVENT_BRACKET_UNRESOLVED", "GUIDE_NO_REPRESENTABLE_PROGRESS",
                "LIFTOFF_TIME_UNREPRESENTABLE", "RECOVERY_BOUNDARY_UNREPRESENTABLE").contains(code), code);
            assertTrue(flight.groundEvent().isEmpty());
            double last = flight.lastAcceptedSample().orElseThrow().point().timeS();
            assertTrue(flight.events().stream().allMatch(e -> e.timeS() <= last));
            assertTrue(flight.guideTransitions().stream().allMatch(e -> e.acceptedPoint().timeS() <= last));
            assertTrue(flight.recoveryEvents().stream().allMatch(e -> e.point().timeS() <= last));
            return;
        }
        assertTrue(flight.failure().isEmpty());
        var ground = flight.groundEvent().orElseThrow();
        var last = flight.lastAcceptedSample().orElseThrow().point();
        assertTrue(flight.events().stream().allMatch(e -> e.timeS() <= ground.timeS()));
        if (ground.interpolationFraction() < 1) {
            assertTrue(last.timeS() < ground.timeS());
            assertTrue(last.state().positionWorldM().get(2) > run.launchConditions().initialPositionWorldM().get(2));
        } else {
            assertEquals(ground.timeS(), last.timeS());
            assertEquals(ground.estimatedState(), last.state());
        }
        near("estimated contact plane", run.launchConditions().initialPositionWorldM().get(2),
            ground.estimatedState().positionWorldM().get(2), 8, true);

        var burnout = event(flight.events(), FlightEventType.BURNOUT);
        assertEquals(grid.family().end, burnout.timeS());
        if (run.alignmentMode() == ALIGN_MOTOR_SOURCE_BOUNDARIES) {
            for (double knot : knots) {
                if (knot <= last.timeS()) assertTrue(flight.samples().stream().anyMatch(s -> s.point().timeS() == knot),
                    () -> "missing eligible source endpoint " + knot);
            }
        }
        assertTrue(flight.samples().stream().anyMatch(s -> s.point().timeS() > burnout.timeS()));
        for (var s : flight.samples()) {
            if (s.point().timeS() >= burnout.timeS()) {
                assertEquals(0, s.physics().motorThrustState().thrustN());
                near("depleted mass", grid.family().loaded - grid.family().propellant,
                    s.physics().motorMassProperties().massKg(), 128, false);
                near("fixed installed CG", grid.family().cg, s.physics().motorMassProperties().cgXGeoM(), 128, false);
            }
        }
        if (grid.variant() == Variant.GUIDE) checkGuide(grid, run, flight, knots);
        if (grid.variant() == Variant.RECOVERY) checkRecovery(run, flight);
    }

    private static void checkHistory(GroundRunResult3DOF flight) {
        assertFalse(flight.samples().isEmpty());
        assertEquals(flight.samples().size() - 1L, flight.acceptedSegments());
        double previous = Double.NEGATIVE_INFINITY;
        for (var s : flight.samples()) {
            assertTrue(s.point().timeS() > previous);
            assertTrue(Double.isFinite(s.physics().flightConditions().mach()));
            if (s.physics().aerodynamicModel() == RecoveryFlightResult3DOF.AerodynamicModel.BASELINE_BODY_DRAG)
                assertTrue(s.physics().flightConditions().mach() <= 1);
            previous = s.point().timeS();
        }
        for (int i = 1; i < flight.events().size(); i++) {
            var a = flight.events().get(i - 1);
            var b = flight.events().get(i);
            assertTrue(a.timeS() < b.timeS() || (a.timeS() == b.timeS() && a.eventType().ordinal() <= b.eventType().ordinal()));
        }
    }

    private static void checkGuide(Sweep grid, SourceMotorGroundRunConfiguration3DOF run,
            GroundRunResult3DOF flight, List<Double> knots) {
        var transitions = flight.guideTransitions();
        assertEquals(List.of(LaunchGuideTransitionResult.Kind.LIFTOFF, LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END),
            transitions.stream().map(LaunchGuideTransitionResult::kind).toList());
        var exit = transitions.get(1);
        assertEquals(1.00, exit.targetTravelM());
        assertEquals(LaunchGuideTransitionResult.AcceptedSide.FIRST_POST_BOUNDARY_RK4, exit.acceptedSide());
        assertEquals(exit.upperTimeS(), exit.acceptedPoint().timeS());
        assertTrue(exit.lowerTimeS() <= exit.upperTimeS());
        var position = exit.acceptedPoint().state().positionWorldM();
        var origin = run.launchConditions().initialPositionWorldM();
        double travel = ((position.get(1) - origin.get(1)) * .6 + (position.get(2) - origin.get(2)) * .8)
            / (.6 * .6 + .8 * .8);
        assertTrue(travel >= 1.00, () -> "accepted exit travel " + travel);
        var free = flight.samples().stream().filter(s -> s.phase() == FlightPhase3DOF.FREE_FLIGHT).findFirst().orElseThrow();
        assertEquals(exit.acceptedPoint(), free.point());
        // Reconstruct parent endpoint from the accepted prefix and source grid, without calling the scheduler.
        double parent = flight.samples().get(0).point().timeS();
        for (int i = 1; i < flight.samples().size(); i++) {
            double previous = flight.samples().get(i - 1).point().timeS();
            double next = flight.samples().get(i).point().timeS();
            if (previous == parent) {
                double nextKnot = knots.stream().filter(k -> k > previous).findFirst().orElse(Double.POSITIVE_INFINITY);
                parent = Math.min(previous + grid.step(), nextKnot);
            }
            assertTrue(next <= parent, "guide crosses its current parent endpoint");
            if (next == exit.acceptedPoint().timeS()) {
                if (next < parent) {
                    assertEquals(parent, flight.samples().get(i + 1).point().timeS(), "first free segment consumes guide remainder");
                }
                break;
            }
        }
    }

    private static void checkRecovery(SourceMotorGroundRunConfiguration3DOF run, GroundRunResult3DOF flight) {
        assertEquals(List.of(RecoveryFlightResult3DOF.EventType.PHYSICAL_APOGEE,
            RecoveryFlightResult3DOF.EventType.RECOVERY_ACTIVATION), flight.recoveryEvents().stream()
                .map(RecoveryFlightResult3DOF.Event::type).toList());
        var detect = flight.recoveryEvents().get(0);
        var activate = flight.recoveryEvents().get(1);
        boolean eligible = false;
        double peak = Double.NEGATIVE_INFINITY;
        double peakTime = Double.NaN;
        RecoveryFlightResult3DOF.Sample first = null;
        for (var s : flight.samples()) {
            if (s.phase() != FlightPhase3DOF.FREE_FLIGHT) continue;
            var state = s.point().state();
            if (!eligible && state.velocityWorldMS().get(2) <= 0) continue;
            eligible = true;
            double altitude = state.positionWorldM().get(2);
            if (altitude > peak) { peak = altitude; peakTime = s.point().timeS(); }
            if (altitude < peak - .01) { first = s; break; }
        }
        assertNotNull(first);
        assertEquals(first.point(), detect.point());
        assertEquals(first.point().timeS() + run.recovery().orElseThrow().deploymentDelayS(), activate.point().timeS());
        assertEquals(activate.point().timeS(), detect.scheduledActivationTimeS().orElseThrow());
        var activated = flight.samples().stream().filter(s -> s.recoveryState() == RecoveryFlightResult3DOF.State.ACTIVE)
            .findFirst().orElseThrow();
        assertEquals(activate.point(), activated.point());
        assertEquals(0, activated.physics().motorThrustState().thrustN());
        assertEquals(RecoveryFlightResult3DOF.State.ACTIVE, flight.recoveryState());
        var reported = event(flight.events(), FlightEventType.APOGEE);
        System.out.println("B1_RECOVERY sampledPeakTime=" + peakTime + " sampledPeakWorldZ=" + peak
            + " reportedApogee=" + reported.timeS() + " detection=" + detect.point().timeS()
            + " activation=" + activate.point().timeS());
    }

    @Test
    void simultaneousBurnoutApogeeGroundAndActivationKeepTerminalPrecedence() {
        var run = fixture(Family.L, Variant.DIRECT).run();
        var inputs = SimulationRunInputs3DOF.source(run);
        var start = point(2.5, 1, 1);
        var endpoint = point(Family.L.end, 0, 0);
        var runtime = new RecoveryRuntime3DOF(inputs, new RecoveryConfiguration3DOF(.80, 6), new double[0],
            new double[0], start, null, List.of(), List.of(), List.of(), null);
        runtime.state = RecoveryFlightResult3DOF.State.PENDING;
        runtime.activationTimeS = endpoint.timeS();
        var context = new FlightEventDetectionContext3DOF(run.launchConditions(), run.physicsContext().propulsionTimeline(),
            inputs.motorCurveStatistics(), run.eventDetectionProfile());
        var reports = new FlightEventDetector3DOF().detect(start, endpoint, context);
        assertEquals(List.of(FlightEventType.BURNOUT, FlightEventType.APOGEE, FlightEventType.GROUND),
            reports.stream().map(FlightEventOccurrence3DOF::eventType).toList());
        assertTrue(reports.stream().allMatch(e -> e.timeS() == endpoint.timeS() && e.interpolationFraction() == 1));
        assertTrue(runtime.terminal(reports, endpoint));
        assertEquals(RecoveryFlightResult3DOF.Outcome.GROUND, runtime.outcome);
        assertEquals(RecoveryFlightResult3DOF.State.PENDING, runtime.state);
        assertTrue(runtime.events.isEmpty());
        assertEquals(endpoint, runtime.samples.get(runtime.samples.size() - 1).point());
    }

    @Test
    void isolatedPositiveThrustActivationIsRejected() {
        var r = RecoveryTimingTest.runtime(1);
        r.state = RecoveryFlightResult3DOF.State.PENDING;
        r.triggerTimeS = 0.;
        r.activationTimeS = 1.;
        assertFalse(r.activate(point(1, 1, 0)));
        assertEquals(RecoveryFlightResult3DOF.Outcome.UNSUPPORTED, r.outcome);
        assertEquals("RECOVERY_ACTIVATION_UNSUPPORTED_UNDER_THRUST", r.failure.code());
        assertTrue(r.events.isEmpty());
    }

    @Test
    void cancellationBeforeFourthCommitKeepsThreeSegmentPrefix() {
        var p = fixture(Family.N, Variant.CANCEL).prepare("cancel");
        var expected = p.runUntilGround(control(3, -1)).result();
        var stopped = p.runUntilGround(control(20_000, 4));
        report("N cancellation before fourth commit", p.configurationId(), stopped.result(), List.of(), ALIGN_MOTOR_SOURCE_BOUNDARIES);
        assertSame(p, stopped.configuration());
        assertEquals(CANCELLED, stopped.result().terminationReason());
        assertEquals("USER_CANCELLED", stopped.result().failure().orElseThrow().code());
        assertEquals(3, stopped.result().acceptedSegments());
        assertEquals(expected.samples(), stopped.result().samples());
        assertEquals(expected.events(), stopped.result().events());
        assertTrue(stopped.result().groundEvent().isEmpty());
    }

    @Test
    void unsupportedMachAndNonadvancingTimeKeepExactClassifications() {
        var mach = fixture(Family.L, Variant.DIRECT, false, 0, 400).prepare("supersonic");
        var time = fixture(Family.L, Variant.DIRECT, false, 1e16, 20).prepare("nonadvancing");
        var a = mach.runUntilGround(control(20_000, -1)).result();
        var b = time.runUntilGround(control(20_000, -1)).result();
        report("Mach negative control", mach.configurationId(), a, List.of(), ALIGN_MOTOR_SOURCE_BOUNDARIES);
        report("time negative control", time.configurationId(), b, List.of(), ALIGN_MOTOR_SOURCE_BOUNDARIES);
        assertEquals(UNSUPPORTED, a.terminationReason());
        assertEquals("UNSUPPORTED_MACH_REGIME", a.failure().orElseThrow().code());
        assertEquals(0, a.acceptedSegments());
        assertTrue(a.samples().isEmpty());
        assertTrue(a.events().isEmpty());
        assertEquals(NUMERICAL_FAILURE, b.terminationReason());
        assertEquals("MOTOR_BOUNDARY_NONADVANCING_TIME", b.failure().orElseThrow().code());
        assertEquals(0, b.acceptedSegments());
        assertEquals(1, b.samples().size());
        assertEquals(1e16, b.samples().get(0).point().timeS());
        assertEquals(new InitialStateBuilder().build(time.runConfiguration().launchConditions()), b.samples().get(0).point().state());
        assertTrue(b.events().isEmpty());
    }

    private static void report(String label, String id, GroundRunResult3DOF flight, List<Double> knots,
            IntegrationStepAlignmentMode alignment) {
        var max = flight.samples().stream().mapToDouble(s -> s.physics().flightConditions().mach()).max();
        var summary = new StringBuilder("B1_RUN ").append(label).append(" id=").append(id)
            .append(" outcome=").append(flight.terminationReason()).append(" samples=").append(flight.samples().size())
            .append(" acceptedSegments=").append(flight.acceptedSegments()).append(" maxAcceptedMach=").append(max)
            .append(" alignment=").append(alignment).append(" failure=").append(flight.failure()).append('\n');
        summary.append("B1_EVENTS ").append(flight.events()).append('\n')
            .append("B1_GUIDE ").append(flight.guideTransitions()).append('\n')
            .append("B1_DEPLOYMENT ").append(flight.recoveryEvents()).append('\n');
        double previous = Double.NaN;
        for (var s : flight.samples()) {
            var p = s.point();
            var guide = flight.guideTransitions().stream().filter(e -> e.acceptedPoint().equals(p))
                .map(LaunchGuideTransitionResult::kind).toList();
            var recovery = flight.recoveryEvents().stream().filter(e -> e.point().equals(p))
                .map(RecoveryFlightResult3DOF.Event::type).toList();
            summary.append("B1_SAMPLE t=").append(p.timeS()).append(" dt=").append(p.timeS() - previous)
                .append(" knot=").append(knots.contains(p.timeS())).append(" guide=").append(guide)
                .append(" recovery=").append(recovery).append(" phase=").append(s.phase())
                .append(" z=").append(p.state().positionWorldM()).append(" v=").append(p.state().velocityWorldMS())
                .append(" mach=").append(s.physics().flightConditions().mach()).append('\n');
            previous = p.timeS();
        }
        System.out.print(summary);
    }
}
