package org.roketsim.core.simulation;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.roketsim.core.SourceMotorRuntimeTest;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.numerics.FixedStepConfig;
import org.roketsim.core.numerics.NumericalIntegrationError;
import org.roketsim.core.propulsion.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.simulation.IntegrationStepAlignmentMode.*;
import static org.roketsim.core.simulation.PreparedSourceVehicle3DOF.*;
import static org.roketsim.core.simulation.RunControl3DOF.Status.*;

/** N37-03C Rev1. TEST_ONLY vehicles and observations, with independent decimal oracles. */
class ParametricSourceVehicleIntegrationTest {
    private static final String BODY = "structure:body";
    private static final String NOSE = "structure:nose";
    private static final double[] NONE = {};

    private record Component(double volume, double mass, double cg, double moment) {}
    private record Structure(double mass, double moment, double cg) {}
    private record Fit(double length, double area, double finArea, double mountStart, double mountEnd,
            double frontRingCenter, double rearRingCenter, double motorFront, double motorAft,
            double clearance, double engagement, double motorCg) {}
    private record Snapshot(double time, double thrust, boolean sourceKnot, double impulse, double motorMass,
            double totalMass, double moment, double cg) {}

    // These constants come from independent 60-digit decimal geometry and ENG integration.
    private enum Family {
        L("L1256", "TFL1256WS", "756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd",
            "-", MotorEjectionMetadata.Kind.UNKNOWN, .075, .5306, 3.905, 1.905,
            new Fit(1.92, .0201061929829747, .0198, 1.210, 1.910, 1.212, 1.908, 1.3844, 1.915, .0025, .5256, 1.6497),
            List.of(new Component(.00214466058485063, 3.86038905273114, .24, .926493372655473),
                new Component(.00236750422374527, 9.47001689498107, 1.12, 10.6064189223788),
                new Component(.0001782, .3564, 1.67444444444444, .596772),
                new Component(.000360654836632108, .721309673264217, 1.56, 1.12524309029218),
                new Component(.000104677867217612, .188420160991701, 1.56, .293935451147054)),
            new Structure(14.5965357819681, 13.5488628364735, .928224548540561),
            new Structure(14.5965357819681, 13.5488628364735, .928224548540561),
            List.of(new Snapshot(0, 0, true, 0, 3.905, 18.5015357819681, 19.9909413364735, 1.08050172548146),
                new Snapshot(.317, 1419.58659351685, false, 388.320563382211, 3.70233106636175, 18.2988668483299, 19.6565983966505, 1.07419757515994),
                new Snapshot(.451, 1439.078899, true, 579.851151380840, 3.60236906669143, 18.1989048486596, 19.4916910857944, 1.07103648532071),
                new Snapshot(2.967, 0, true, 3650.044730405055, 2, 16.5965357819681, 16.8482628364735, 1.01516744565326))),
        M("M1850", "TFM1850W", "111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc",
            "1000", MotorEjectionMetadata.Kind.UNKNOWN, .075, .9354, 7.010, 4.103,
            new Fit(2.40, .0314159265358979, .03375, 1.335, 2.385, 1.3375, 2.3825, 1.4546, 2.390, .0025, .9304, 1.9223),
            List.of(new Component(.000376700822596634, .244855534687812, .268706637533891, .0657943074075249),
                new Component(.00371336251654314, 3.34202626488882, 1.40, 4.67883677084435),
                new Component(.00054000, .594, 1.96111111111111, 1.1649),
                new Component(.000540982254948162, .649178705937795, 1.86, 1.20747239304430),
                new Component(.000240174758366940, .168122330856858, 1.86, .312707535393755)),
            new Structure(4.99818283637129, 7.42971100668993, 1.48648243770209),
            new Structure(6.65615657148246, 9.76098514419128, 1.46645966623007),
            List.of(new Snapshot(0, 0, true, 0, 7.010, 45.6661565714825, 64.8363081441913, 1.41978903003806),
                new Snapshot(1.298, 1979, false, 2602.910, 5.61383312967713, 44.2699897011596, 62.1524565693696, 1.40394106682482),
                new Snapshot(1.558, 1860, true, 3101.980, 5.34613851097266, 44.0022950824551, 61.6378672038340, 1.40078755183865),
                new Snapshot(6.750, 0, true, 7649.329, 2.907, 41.5631565714825, 56.9491112441913, 1.37018253525204))),
        N("N3300", "TFN3300R-P", "10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8",
            "P", MotorEjectionMetadata.Kind.PLUGGED, .098, 1.0721, 12.645, 7.668,
            new Fit(2.90, .0452389342116930, .0513, 1.630, 2.880, 1.633, 2.877, 1.8159, 2.888, .003, 1.0641, 2.35195),
            List.of(new Component(.000749116264776399, .374558132388199, .336156783293055, .125910256939871),
                new Component(.00711759231597304, 8.18523116336899, 1.70, 13.9148929777273),
                new Component(.0010260, 1.4877, 2.54777777777778, 3.790329),
                new Component(.00126056405225290, 1.26056405225290, 2.255, 2.84257193783030),
                new Component(.000393239435635142, .491549294543927, 2.255, 1.10844365919656)),
            new Structure(11.7996026425540, 21.7821478316940, 1.84600689459991),
            new Structure(12.6143714791850, 23.6172548539667, 1.87224982972299),
            List.of(new Snapshot(0, 0, true, 0, 12.645, 90.2593714791850, 150.857662603967, 1.67137949369342),
                new Snapshot(2.0955, 3595.025, false, 7370.44168425, 8.69969428691510, 86.3140657661001, 141.578500832077, 1.64027148501770),
                new Snapshot(2.252, 3570.478, true, 7931.142294, 8.39955822808107, 86.0139297072661, 140.872595828502, 1.63778816184702),
                new Snapshot(4.613, 0, true, 14325.010771, 4.977, 82.5913714791850, 132.822910003967, 1.60819353916942)));

        final String shortName, designation, sha256, token;
        final MotorEjectionMetadata.Kind ejection;
        final double diameter, length, initialMass, propellantMass;
        final Fit fit;
        final List<Component> components;
        final Structure baseline, effective;
        final List<Snapshot> snapshots;

        Family(String shortName, String designation, String sha256, String token, MotorEjectionMetadata.Kind ejection,
                double diameter, double length, double initialMass, double propellantMass, Fit fit,
                List<Component> components, Structure baseline, Structure effective, List<Snapshot> snapshots) {
            this.shortName = shortName;
            this.designation = designation;
            this.sha256 = sha256;
            this.token = token;
            this.ejection = ejection;
            this.diameter = diameter;
            this.length = length;
            this.initialMass = initialMass;
            this.propellantMass = propellantMass;
            this.fit = fit;
            this.components = components;
            this.baseline = baseline;
            this.effective = effective;
            this.snapshots = snapshots;
        }
    }

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
            hardware.stream().map(ParametricSourceVehicleIntegrationTest::evidence).toList());
    }
    private static void scalar(String name, double expected, double actual, int ulps) {
        double error = Math.abs(actual - expected);
        double tolerance = ulps == 0 || expected == 0 ? 0 : ulps * Math.ulp(expected);
        boolean equal = tolerance == 0 ? expected == actual : error <= tolerance;
        assertTrue(equal, () -> name + ": expected=" + expected + ", actual=" + actual
            + ", absolute error=" + error + ", relative error=" + (expected == 0 ? "n/a" : error / Math.abs(expected))
            + ", difference in expected ULPs=" + error / Math.ulp(expected) + ", absolute tolerance=" + tolerance
            + " (" + ulps + " ULPs; zero is exact)");
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void geometryInstallationAndStructuralOracles(Family family) {
        var fixture = fixture(family, Variant.DIRECT);
        var context = fixture.run().physicsContext();
        var g = context.resolvedGeometry();
        var f = family.fit;
        scalar("overall length", f.length(), g.overallLengthM(), 128);
        scalar("reference area", f.area(), g.referenceAreaM2(), 128);
        scalar("fin planform per fin", f.finArea(), g.finPlanformAreaPerFinM2(), 128);
        scalar("mount start", f.mountStart(), g.motorMountStartXGeoM(), 128);
        scalar("mount end", f.mountEnd(), g.motorMountEndXGeoM(), 128);
        scalar("front ring center", f.frontRingCenter(), (g.frontCenteringRingStartXGeoM() + g.frontCenteringRingEndXGeoM()) / 2, 128);
        scalar("rear ring center", f.rearRingCenter(), (g.rearCenteringRingStartXGeoM() + g.rearCenteringRingEndXGeoM()) / 2, 128);
        var install = context.motorInstallation();
        scalar("motor front", f.motorFront(), install.motorFrontXGeoM(), 128);
        scalar("motor aft", f.motorAft(), install.motorAftXGeoM(), 128);
        scalar("radial clearance", f.clearance(), install.nominalRadialClearanceM(), 128);
        scalar("engagement", f.engagement(), install.axialEngagementLengthM(), 128);

        var s = context.structuralMassProperties();
        var components = List.of(s.nose(), s.body(), s.fins(), s.motorMount(), s.centeringRings());
        double[] volumes = {g.noseMaterialVolumeM3(), g.bodyMaterialVolumeM3(), g.finSetMaterialVolumeM3(),
            g.motorMountMaterialVolumeM3(), g.centeringRingPairMaterialVolumeM3()};
        double[] centroids = {g.noseVolumeCentroidXGeoM(), g.bodyVolumeCentroidXGeoM(), g.finSetVolumeCentroidXGeoM(),
            g.motorMountVolumeCentroidXGeoM(), g.centeringRingPairVolumeCentroidXGeoM()};
        for (int i = 0; i < components.size(); i++) {
            var expected = family.components.get(i);
            var actual = components.get(i);
            int volumeBudget = i == 2 || (i == 0 && family == Family.L) ? 128 : 512;
            scalar("component " + i + " geometry volume", expected.volume(), volumes[i], volumeBudget);
            scalar("component " + i + " geometry centroid", expected.cg(), centroids[i], 512);
            scalar("component " + i + " mass", expected.mass(), actual.massKg(), 512);
            scalar("component " + i + " CG", expected.cg(), actual.cgXGeoM(), 512);
            scalar("component " + i + " first moment", expected.moment(), actual.massKg() * actual.cgXGeoM(), 512);
        }
        checkStructure("calculated", family.baseline, s);
        var prepared = fixture.prepare("direct");
        assertSame(fixture.run(), prepared.calculatedRun());
        assertSame(g, prepared.runConfiguration().physicsContext().resolvedGeometry());
        var effective = prepared.runConfiguration().physicsContext().structuralMassProperties();
        checkStructure("effective", family.effective, effective);
        if (family == Family.L) assertSame(fixture.run(), prepared.runConfiguration());
        if (family == Family.M) {
            assertEquals(s.nose().massKg(), effective.nose().massKg());
            scalar("measured nose CG", .310, effective.nose().cgXGeoM(), 0);
            scalar("measured body mass", 5.0, effective.body().massKg(), 0);
            assertEquals(s.body().cgXGeoM(), effective.body().cgXGeoM());
            scalar("effective body moment", 7, effective.body().massKg() * effective.body().cgXGeoM(), 512);
        }
        if (family == Family.N) {
            scalar("measured body mass", 9, effective.body().massKg(), 0);
            scalar("measured body CG", 1.750, effective.body().cgXGeoM(), 0);
            scalar("effective body moment", 15.750, effective.body().massKg() * effective.body().cgXGeoM(), 512);
        }
        assertEquals(s.fins(), effective.fins());
        assertEquals(s.motorMount(), effective.motorMount());
        assertEquals(s.centeringRings(), effective.centeringRings());
    }

    private static void checkStructure(String label, Structure expected, StructuralMassProperties actual) {
        scalar(label + " structure mass", expected.mass(), actual.structureMassKg(), 512);
        scalar(label + " structure moment", expected.moment(), actual.structureMassKg() * actual.structureCgXGeoM(), 512);
        scalar(label + " structure CG", expected.cg(), actual.structureCgXGeoM(), 512);
    }

    @ParameterizedTest
    @EnumSource(Family.class)
    void sourceIdentityAndIndependentSnapshots(Family family) {
        var fixture = fixture(family, Variant.DIRECT);
        var prepared = fixture.prepare("direct");
        var effective = prepared.runConfiguration();
        var context = effective.physicsContext();
        var motor = (SourceBackedMotorProfile) context.motorInstallation().motor();
        var source = motor.selectedThrustSource();
        assertAll(
            () -> assertEquals("n37-03c:" + family + ":r1", prepared.configurationId()),
            () -> assertEquals("TEST_ONLY N37-03C geometry " + family, prepared.designSourceReference()),
            () -> assertEquals(fixture.materials(), prepared.materials()),
            () -> assertEquals(fixture.overrides(), prepared.overrides()),
            () -> assertEquals(fixture.included(), prepared.includedItems()),
            () -> assertEquals(fixture.evidence(), prepared.hardwareEvidence()),
            () -> assertSame(fixture.run().physicsContext().motorInstallation(), context.motorInstallation()),
            () -> assertEquals(family.shortName, motor.catalogProfile().identity().competitionDesignation()),
            () -> assertEquals("teknofest26:" + family.designation + ":Rev1", source.curveId()),
            () -> assertEquals("Rev1", source.source().revision()),
            () -> assertEquals("Teknofest26_" + family.shortName + "_Rev1.eng", source.source().fileName()),
            () -> assertEquals(family.sha256, source.source().sha256()),
            () -> assertEquals(family.token, motor.ejection().rawSourceToken().orElseThrow()),
            () -> assertEquals(family.ejection, motor.ejection().kind()),
            () -> assertTrue(motor.ejection().delayS().isEmpty()),
            () -> assertEquals(MotorType.RELOADABLE, motor.motorType()),
            () -> assertEquals("ENG_INITIAL", motor.initialMassObservation().quantity()),
            () -> assertEquals("ENG_PROPELLANT", motor.propellantMassObservation().quantity()),
            () -> scalar("source initial mass", family.initialMass, motor.initialMassKg(), 0),
            () -> scalar("source propellant mass", family.propellantMass, motor.propellantMassKg(), 0),
            () -> scalar("source diameter after mm-to-m conversion", family.diameter, motor.diameterM(), 128),
            () -> scalar("source length after mm-to-m conversion", family.length, motor.lengthM(), 128),
            () -> assertEquals("TEST_ONLY: nominal-source geometry fixture", motor.geometryInterpretation().sourceReference()),
            () -> assertEquals("TEST_ONLY, unresolved physical installation datum; not flight-ready", motor.geometryInterpretation().qualification()),
            () -> assertEquals(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL, motor.propertyModelProfile().massModel()),
            () -> assertEquals(MotorCgEvolutionModel.FIXED_MIDPOINT, motor.propertyModelProfile().cgModel()),
            () -> assertNull(motor.massCurve()), () -> assertNull(motor.cgCurve()),
            () -> assertTrue(motor.certificationEvidence().isEmpty()));

        var end = family.snapshots.get(family.snapshots.size() - 1);
        var stats = new MotorCurveAnalyzer().analyzeRuntime(motor);
        scalar("source curve end", end.time(), stats.curveEndTimeS(), 0);
        scalar("source total impulse", end.impulse(), stats.totalImpulseNS(), 128);
        for (var expected : family.snapshots) {
            if (expected.sourceKnot()) {
                assertTrue(motor.thrustCurve().stream().anyMatch(s -> s.timeS() == expected.time()
                    && s.thrustN() == expected.thrust()), () -> "missing exact ENG node at " + expected.time());
            }
            checkSnapshot(effective, family, expected.time(), expected);
        }
        checkSnapshot(effective, family, end.time() + .25, end);
    }

    private static void checkSnapshot(SourceMotorGroundRunConfiguration3DOF run, Family family,
            double time, Snapshot expected) {
        var p = evaluate(run, time);
        String label = family + " motor time " + time + " ";
        scalar(label + "thrust", expected.thrust(), p.motorThrustState().thrustN(), expected.sourceKnot() ? 0 : 128);
        scalar(label + "impulse", expected.impulse(), p.motorThrustState().cumulativeImpulseNS(), 128);
        scalar(label + "motor mass", expected.motorMass(), p.motorMassProperties().massKg(), 128);
        scalar(label + "motor CG", family.fit.motorCg(), p.motorMassProperties().cgXGeoM(), 128);
        var total = p.rocketMassProperties();
        scalar(label + "total mass", expected.totalMass(), total.totalMassKg(), 512);
        scalar(label + "total moment", expected.moment(), total.totalMassKg() * total.totalCgXGeoM(), 512);
        scalar(label + "total CG", expected.cg(), total.totalCgXGeoM(), 512);
    }

    private static PhysicsEvaluationResult3DOF evaluate(SourceMotorGroundRunConfiguration3DOF run, double time) {
        var launch = run.launchConditions();
        var p = launch.initialPositionWorldM();
        var state = new TranslationalState3DOF(new double[]{p.get(0), p.get(1), p.get(2) + 25}, new double[]{10, 6, 20});
        var hardware = run.attachedHardware();
        return new PhysicsEvaluator3DOF().evaluateRuntime(time, state, launch, run.physicsContext(),
            hardware.isEmpty() ? NONE : hardware.stream().mapToDouble(AttachedHardwareMass::massKg).toArray(),
            hardware.isEmpty() ? NONE : hardware.stream().mapToDouble(AttachedHardwareMass::cgXGeoM).toArray());
    }

    @Test
    void lDensityOnlyTwinChangesBodyMomentAndAcceleration() {
        var base = fixture(Family.L, Variant.DIRECT).prepare("direct").runConfiguration();
        var twin = fixture(Family.L, Variant.DIRECT, true, 0, 20).prepare("density").runConfiguration();
        var a = base.physicsContext();
        var b = twin.physicsContext();
        assertEquals(a.resolvedGeometry(), b.resolvedGeometry());
        var old = a.structuralMassProperties();
        var changed = b.structuralMassProperties();
        assertEquals(old.body().cgXGeoM(), changed.body().cgXGeoM());
        assertEquals(old.nose(), changed.nose());
        assertEquals(old.fins(), changed.fins());
        assertEquals(old.motorMount(), changed.motorMount());
        assertEquals(old.centeringRings(), changed.centeringRings());
        scalar("density twin body mass", 10.6537690068537, changed.body().massKg(), 512);
        scalar("density twin body moment", 11.9322212876762, changed.body().massKg() * changed.body().cgXGeoM(), 512);
        checkStructure("density twin", new Structure(15.7802878938408, 14.8746652017709, .942610508872694), changed);
        var before = evaluate(base, .317);
        var after = evaluate(twin, .317);
        unchangedForces(before, after);
        scalar("density twin matched-state total mass", 19.4826189602026, after.rocketMassProperties().totalMassKg(), 512);
        var forces = before.dynamics().forces();
        for (int axis = 0; axis < 3; axis++) {
            double nonGravity = forces.thrustForceWorldN().get(axis) + forces.dragForceWorldN().get(axis);
            // The mass twin is heavier and gravity acceleration is unchanged.
            // Check the exact sign of F_non-gravity * (1/m_twin - 1/m_base).
            double expectedSign = Math.signum(nonGravity * (1 / 19.4826189602026 - 1 / 18.2988668483299));
            double actual = after.dynamics().accelerationWorldMS2().get(axis) - before.dynamics().accelerationWorldMS2().get(axis);
            assertTrue(expectedSign == Math.signum(actual), "density twin delta acceleration axis " + axis + ": " + actual);
        }
    }

    @Test
    void mCgOnlyAndReorderedOverridesPreserveMatchedStateAuthorities() {
        var f = fixture(Family.M, Variant.DIRECT);
        var nominal = f.prepare("direct");
        var bodyOnly = f.prepare("body-only", List.of(f.overrides().get(0)), f.included(), f.evidence());
        var reversed = f.prepare("reversed", List.of(f.overrides().get(1), f.overrides().get(0)), f.included(), f.evidence());
        var a = nominal.runConfiguration().physicsContext().structuralMassProperties();
        var b = bodyOnly.runConfiguration().physicsContext().structuralMassProperties();
        scalar("CG-only structural mass unchanged", b.structureMassKg(), a.structureMassKg(), 0);
        scalar("body-only structure first moment", 9.75087423584558, b.structureMassKg() * b.structureCgXGeoM(), 512);
        scalar("CG-only structure first moment", Family.M.effective.moment(), a.structureMassKg() * a.structureCgXGeoM(), 512);
        assertEquals(a, reversed.runConfiguration().physicsContext().structuralMassProperties());
        assertEquals(List.of(f.overrides().get(1), f.overrides().get(0)), reversed.overrides());
        var p = evaluate(nominal.runConfiguration(), 1.298);
        assertEquals(p, evaluate(reversed.runConfiguration(), 1.298));
        var q = evaluate(bodyOnly.runConfiguration(), 1.298);
        unchangedForces(q, p);
        scalar("CG-only vehicle mass unchanged", q.rocketMassProperties().totalMassKg(), p.rocketMassProperties().totalMassKg(), 0);
        assertNotEquals(q.rocketMassProperties().totalCgXGeoM(), p.rocketMassProperties().totalCgXGeoM());
        scalar("M atmosphere mapping", 475, p.environmentAltitudeM(), 0);
        scalar("M relative EAST speed", 6, p.relativeFlow().get(0), 0);
        scalar("M relative NORTH speed", 8, p.relativeFlow().get(1), 0);
        scalar("M relative UP speed", 20, p.relativeFlow().get(2), 0);
    }

    @Test
    void nAbsorbedItemIsAnOwnershipClaimWithNoAdditionalMass() {
        var f = fixture(Family.N, Variant.DIRECT);
        var nominal = f.prepare("direct");
        var without = f.prepare("without-absorbed-claim", f.overrides(), List.of(), f.evidence());
        assertEquals(nominal.runConfiguration(), without.runConfiguration());
        assertEquals(evaluate(nominal.runConfiguration(), 2.0955), evaluate(without.runConfiguration(), 2.0955));
        assertEquals(List.of("n-ballast"), nominal.runConfiguration().attachedHardware().stream()
            .map(AttachedHardwareMass::componentId).toList());
    }

    private static void unchangedForces(PhysicsEvaluationResult3DOF first, PhysicsEvaluationResult3DOF second) {
        assertEquals(first.atmosphere(), second.atmosphere());
        assertEquals(first.airProperties(), second.airProperties());
        assertEquals(first.relativeFlow(), second.relativeFlow());
        assertEquals(first.motorThrustState(), second.motorThrustState());
        assertEquals(first.motorMassProperties(), second.motorMassProperties());
        assertEquals(first.basicDrag(), second.basicDrag());
        assertEquals(first.dynamics().forces().dragForceWorldN(), second.dynamics().forces().dragForceWorldN());
    }

    private enum PreparationFailure {
        ZERO_MASS, NAN_MASS, CG_ABOVE_EXTENT, BLANK_MEASUREMENT_REFERENCE, DUPLICATE_OVERRIDE,
        ABSORBED_AND_SEPARATE, INCOMPLETE_OWNER_MEASUREMENT, MISSING_HARDWARE_EVIDENCE
    }

    @ParameterizedTest
    @EnumSource(PreparationFailure.class)
    void rejectedPreparationPreservesErrorCodes(PreparationFailure test) {
        var n = fixture(Family.N, Variant.DIRECT);
        var rows = n.overrides();
        var included = n.included();
        var evidence = n.evidence();
        String expectedCode;
        switch (test) {
            case ZERO_MASS, NAN_MASS -> {
                rows = List.of(override(Family.N, BODY, test == PreparationFailure.ZERO_MASS ? 0 : Double.NaN, null));
                expectedCode = "INVALID_MEASURED_MASS";
            }
            case CG_ABOVE_EXTENT -> {
                rows = List.of(override(Family.N, NOSE, null, Math.nextUp(2.90)));
                expectedCode = "INVALID_MEASURED_CG";
            }
            case BLANK_MEASUREMENT_REFERENCE -> {
                rows = List.of(new StructuralMassOverride(BODY,
                    Optional.of(new StructuralMassOverride.Measurement(9, " ")), Optional.empty()));
                expectedCode = "MISSING_PROPERTY_PROVENANCE";
            }
            case DUPLICATE_OVERRIDE -> {
                rows = List.of(rows.get(0), rows.get(0));
                expectedCode = "DUPLICATE_OVERRIDE_TARGET";
            }
            case ABSORBED_AND_SEPARATE -> {
                var electronics = new AttachedHardwareMass("n-electronics", .125, 1.750);
                var original = n.run();
                var both = new SourceMotorGroundRunConfiguration3DOF(original.launchConditions(), original.physicsContext(),
                    original.eventDetectionProfile(), original.fixedStepConfig(), original.terminalEventHandlingModel(),
                    List.of(original.attachedHardware().get(0), electronics), original.launchGuide(), original.recovery(),
                    original.alignmentMode());
                n = new Fixture(n.family(), n.materials(), both, rows, included, evidence);
                expectedCode = "CONFLICTING_MASS_OWNERSHIP";
            }
            case INCOMPLETE_OWNER_MEASUREMENT -> {
                rows = List.of(override(Family.N, BODY, 9.0, null));
                expectedCode = "INVALID_INCLUDED_ITEM_OWNER";
            }
            case MISSING_HARDWARE_EVIDENCE -> {
                evidence = List.of();
                expectedCode = "INVALID_HARDWARE_EVIDENCE";
            }
            default -> throw new AssertionError(test);
        }
        var selected = n;
        var selectedRows = rows;
        var selectedIncluded = included;
        var selectedEvidence = evidence;
        var failure = assertThrowsExactly(VehicleConfigurationError.class,
            () -> selected.prepare(test.name(), selectedRows, selectedIncluded, selectedEvidence));
        assertEquals(expectedCode, failure.errorCode());
    }

    @Test
    void mInvalidNominalMountsFailAtInstallation() {
        var raw = geometry(Family.M);
        var motor = SourceMotorRuntimeTest.teknofest(Family.M.shortName);
        var narrow = mountVariant(raw, new MotorMountTubeGeometry(1.050, .074, .002, .015));
        var shortMount = mountVariant(raw, new MotorMountTubeGeometry(.900, .080, .002, .015));
        var resolver = new MotorInstallationResolver();
        assertEquals("MOTOR_DIAMETER_EXCEEDS_MOUNT",
            assertThrowsExactly(MotorInstallationError.class, () -> resolver.resolveRuntime(narrow, motor)).errorCode());
        assertEquals("MOTOR_FRONT_BEFORE_MOUNT",
            assertThrowsExactly(MotorInstallationError.class, () -> resolver.resolveRuntime(shortMount, motor)).errorCode());
    }

    private static ResolvedRocketGeometry mountVariant(SingleStageRocketGeometry raw, MotorMountTubeGeometry mount) {
        var a = raw.motorAttachment();
        return new GeometryResolver().resolve(new SingleStageRocketGeometry(raw.airframeDiameterM(), raw.nose(), raw.body(),
            raw.fins(), new MotorAttachmentGeometry(mount, a.centeringRings(), a.motorOverhangM()), raw.referenceGeometryPolicy()));
    }

    @Test
    void machAndAtmosphereBoundariesRemainSeparate() {
        var c = fixture(Family.L, Variant.DIRECT).run().physicsContext();
        var atmosphere = c.atmosphereModel();
        var air = c.airPropertiesCalculator().evaluate(atmosphere.evaluate(0));
        var drag = new BasicDragEvaluator();
        var g = c.resolvedGeometry();
        var finishes = c.aerodynamicSurfaces();
        assertDoesNotThrow(() -> drag.evaluate(g, finishes, conditions(0, air), air, c.basicDragModelProfile()));
        assertDoesNotThrow(() -> drag.evaluate(g, finishes, conditions(1, air), air, c.basicDragModelProfile()));
        var beyondSonic = assertThrowsExactly(AerodynamicEvaluationError.class,
            () -> drag.evaluate(g, finishes, conditions(Math.nextUp(1.0), air), air, c.basicDragModelProfile()));
        assertEquals("UNSUPPORTED_MACH_REGIME", beyondSonic.errorCode());

        var statics = new StaticAerodynamicEvaluator();
        var profile = StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE;
        assertDoesNotThrow(() -> statics.evaluate(g, conditions(Math.nextDown(.8), air), profile));
        var beyondStatic = assertThrowsExactly(AerodynamicEvaluationError.class,
            () -> statics.evaluate(g, conditions(.8, air), profile));
        assertEquals("UNSUPPORTED_STATIC_STABILITY_MACH_REGIME", beyondStatic.errorCode());

        assertDoesNotThrow(() -> atmosphere.evaluate(-5000));
        assertDoesNotThrow(() -> atmosphere.evaluate(84852));
        assertThrowsExactly(AtmosphereDomainError.class, () -> atmosphere.evaluate(Math.nextDown(-5000.0)));
        assertThrowsExactly(AtmosphereDomainError.class, () -> atmosphere.evaluate(Math.nextUp(84852.0)));
    }

    private static BasicFlightConditions conditions(double mach, DryAirProperties air) {
        return new BasicFlightConditions(mach * air.speedOfSoundMS(), mach, 0, 0);
    }

    @Test
    void sourceKnotRepresentabilityIsPreserved() {
        var m = fixture(Family.M, Variant.DIRECT).run().physicsContext();
        var representable = MotorTimeBoundarySchedule3DOF.fromRuntimeContext(m);
        scalar("M first source knot endpoint", .104, .100 + representable.effectiveStepSizeS(.100, .020), 0);
        var l = fixture(Family.L, Variant.DIRECT, false, .37, 20).run().physicsContext();
        var unrepresentable = MotorTimeBoundarySchedule3DOF.fromRuntimeContext(l);
        var failure = assertThrowsExactly(NumericalIntegrationError.class,
            () -> unrepresentable.effectiveStepSizeForExecutionS(-1, 2));
        assertEquals("MOTOR_BOUNDARY_UNREPRESENTABLE_ENDPOINT", failure.errorCode());
        assertEquals("endpoint_time_s", failure.fieldName());
    }

    private record Smoke(Family family, Variant variant) {
        @Override public String toString() { return family + "/" + variant + "/r1"; }
    }

    private static Stream<Smoke> smokeCases() {
        return Stream.of(new Smoke(Family.L, Variant.DIRECT), new Smoke(Family.M, Variant.DIRECT),
            new Smoke(Family.N, Variant.DIRECT), new Smoke(Family.M, Variant.GUIDE),
            new Smoke(Family.N, Variant.RECOVERY), new Smoke(Family.N, Variant.CANCEL));
    }

    private static RunControl3DOF control(long cancelAt) {
        var clock = new AtomicLong();
        var reference = new AtomicReference<RunControl3DOF>();
        var control = new RunControl3DOF(new RunControl3DOF.MonitoringPolicy(1, 1_000_000_000_000L, 1000, 100000),
            p -> { if (p.acceptedSegments() >= cancelAt) reference.get().requestCancellation(); },
            w -> {}, () -> clock.addAndGet(1_000_000));
        reference.set(control);
        return control;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("smokeCases")
    void preparedSmokeHasExpectedOutcomeAndAcceptedHistory(Smoke smoke) {
        var f = fixture(smoke.family(), smoke.variant());
        String variant = switch (smoke.variant()) {
            case DIRECT -> "direct";
            case GUIDE -> "guide";
            case RECOVERY -> "recovery";
            case CANCEL -> "cancel";
        };
        var prepared = f.prepare(variant);
        var completed = prepared.runUntilGround(control(smoke.variant() == Variant.CANCEL ? 3 : 20_000));
        var result = completed.result();
        try {
            assertSame(prepared, completed.configuration());
            ordered(result);
            if (smoke.variant() == Variant.CANCEL) {
                assertEquals(CANCELLED, result.terminationReason());
                assertEquals("USER_CANCELLED", result.failure().orElseThrow().code());
                assertEquals(3, result.acceptedSegments());
                assertTrue(result.groundEvent().isEmpty());
            } else {
                assertEquals(GROUND, result.terminationReason());
                assertTrue(result.failure().isEmpty());
                var ground = result.groundEvent().orElseThrow();
                assertTrue(result.lastAcceptedSample().orElseThrow().point().timeS() <= ground.timeS());
                if (smoke.variant() == Variant.GUIDE) {
                    assertTrue(result.guideTransitions().stream()
                        .anyMatch(t -> t.kind() == LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END));
                }
                if (smoke.variant() == Variant.RECOVERY) {
                    var activation = result.recoveryEvents().stream()
                        .filter(e -> e.type() == RecoveryFlightResult3DOF.EventType.RECOVERY_ACTIVATION)
                        .findFirst().orElseThrow();
                    assertTrue(activation.point().timeS() > 4.613);
                    assertTrue(activation.point().timeS() < ground.timeS());
                    assertEquals(RecoveryFlightResult3DOF.State.ACTIVE, result.recoveryState());
                }
                if (smoke.family() == Family.L) {
                    assertSame(f.run(), prepared.runConfiguration());
                    var baseline = new SimulationEngine3DOF().runSourceUntilGround(f.run(), control(20_000));
                    assertEquals(baseline, result);
                }
            }
        } catch (AssertionError | RuntimeException firstFailure) {
            System.out.println("N37-03C first assertion for " + prepared.configurationId() + ": " + firstFailure);
            System.out.println("N37-03C accepted history and terminal result: " + result);
            throw firstFailure;
        }
        report(prepared, result);
    }

    @Test
    void lBeyondMachOneHasFrozenGroundRunDomainOutcome() {
        var prepared = fixture(Family.L, Variant.DIRECT, false, 0, 400).prepare("supersonic");
        var completed = prepared.runUntilGround(control(20_000));
        var result = completed.result();
        try {
            assertSame(prepared, completed.configuration());
            assertEquals(UNSUPPORTED, result.terminationReason());
            assertEquals("UNSUPPORTED_MACH_REGIME", result.failure().orElseThrow().code());
            assertEquals(0, result.acceptedSegments());
            assertTrue(result.samples().isEmpty());
            assertTrue(result.events().isEmpty());
            assertTrue(result.guideTransitions().isEmpty());
            assertTrue(result.recoveryEvents().isEmpty());
            assertTrue(result.groundEvent().isEmpty());
        } catch (AssertionError firstFailure) {
            System.out.println("N37-03C first assertion for " + prepared.configurationId() + ": " + firstFailure);
            System.out.println("N37-03C accepted history and terminal result: " + result);
            throw firstFailure;
        }
        report(prepared, result);
    }

    @Test
    void lNonadvancingStepRetainsInitialHistoryAndNumericalCode() {
        var prepared = fixture(Family.L, Variant.DIRECT, false, 1e16, 20).prepare("nonadvancing");
        var completed = prepared.runUntilGround(control(20_000));
        var result = completed.result();
        try {
            assertSame(prepared, completed.configuration());
            assertEquals(NUMERICAL_FAILURE, result.terminationReason());
            assertEquals("MOTOR_BOUNDARY_NONADVANCING_TIME", result.failure().orElseThrow().code());
            assertEquals(0, result.acceptedSegments());
            assertEquals(1, result.samples().size());
            scalar("initial retained time", 1e16, result.samples().get(0).point().timeS(), 0);
            assertEquals(new InitialStateBuilder().build(prepared.runConfiguration().launchConditions()),
                result.samples().get(0).point().state());
            assertTrue(result.events().isEmpty());
        } catch (AssertionError firstFailure) {
            System.out.println("N37-03C first assertion for " + prepared.configurationId() + ": " + firstFailure);
            System.out.println("N37-03C accepted history and terminal result: " + result);
            throw firstFailure;
        }
        report(prepared, result);
    }

    private static void ordered(GroundRunResult3DOF result) {
        assertFalse(result.samples().isEmpty());
        double previous = Double.NEGATIVE_INFINITY;
        for (var sample : result.samples()) {
            double time = sample.point().timeS();
            assertTrue(time > previous, "accepted sample timestamps must strictly increase");
            previous = time;
        }
    }

    private static void report(PreparedSourceVehicle3DOF prepared, GroundRunResult3DOF result) {
        System.out.printf("N37-03C %s status=%s samples=%d acceptedSegments=%d events=%d guide=%s recovery=%s failure=%s%n",
            prepared.configurationId(), result.terminationReason(), result.samples().size(), result.acceptedSegments(),
            result.events().size(), result.guideTransitions().stream().map(LaunchGuideTransitionResult::kind).toList(),
            result.recoveryEvents().stream().map(RecoveryFlightResult3DOF.Event::type).toList(), result.failure());
    }
}
