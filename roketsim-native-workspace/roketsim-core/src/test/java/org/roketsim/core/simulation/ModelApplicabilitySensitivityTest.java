package org.roketsim.core.simulation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.roketsim.core.SourceMotorRuntimeTest;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.math.Vector;
import org.roketsim.core.numerics.FixedStepConfig;
import org.roketsim.core.propulsion.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.roketsim.core.simulation.IntegrationStepAlignmentMode.*;
import static org.roketsim.core.simulation.PreparedSourceVehicle3DOF.*;

/** NAT-038 uses synthetic inputs and the existing execution API. Trajectory deltas are observations. */
class ModelApplicabilitySensitivityTest {
    private static final String NOSE = "structure:nose";
    private static final String BODY = "structure:body";
    private static final double L_END = 2.967;
    private static final double[] MOTOR_PROBE_TIMES = {0, 1.4835, L_END, 3.967};
    private static final long WATCHDOG_SEGMENTS = 20_000;

    private enum Family {
        L("L1256", "TFL1256WS", "756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd", 2.967),
        M("M1850", "TFM1850W", "111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc", 6.750),
        N("N3300", "TFN3300R-P", "10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8", 4.613);

        final String shortName, designation, sha256;
        final double burnoutS;

        Family(String shortName, String designation, String sha256, double burnoutS) {
            this.shortName = shortName;
            this.designation = designation;
            this.sha256 = sha256;
            this.burnoutS = burnoutS;
        }
    }

    private enum FlightCase {
        L_BASE(Family.L, .020, "baseline body density 4000 kg/m3"),
        M_BASE(Family.M, .020, "baseline m-ballast 32 kg; WORLD ENU wind [4,-2,0] m/s"),
        N_BASE(Family.N, .015625, "baseline environment altitude 800 m geopotential"),
        L_DENSITY(Family.L, .020, "body density 4000 -> 4500 kg/m3"),
        M_BALLAST(Family.M, .020, "m-ballast 32 -> 36 kg at unchanged x_geo 1.300 m"),
        M_WIND(Family.M, .020, "east air-mass velocity 4 -> 8 m/s; north -2 and up 0 unchanged"),
        N_ALTITUDE(Family.N, .015625, "environment altitude 800 -> 1300 m geopotential"),
        L_DENSITY_FINE(Family.L, .010, "body density 4500 kg/m3; step 0.020 -> 0.010 s");

        final Family family;
        final double step;
        final String changedInput;

        FlightCase(Family family, double step, String changedInput) {
            this.family = family;
            this.step = step;
            this.changedInput = changedInput;
        }
    }

    private record ProbeOptions(double mNoseCgM, double lBodyRoughnessM, MotorPropertyModelProfile motorModel) {
        static final ProbeOptions BASE = new ProbeOptions(.310, 0, MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE);
    }

    // Frozen N37-03C geometry and materials. Changes in the flight table are applied only to the named field.
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

    private static SingleStageRocketMaterials materials(FlightCase test) {
        double[] d = switch (test.family) {
            case L -> new double[]{1800, test == FlightCase.L_BASE ? 4000 : 4500, 2000, 2000, 1800};
            case M -> new double[]{650, 900, 1100, 1200, 700};
            case N -> new double[]{500, 1150, 1450, 1000, 1250};
        };
        String label = "TEST_ONLY " + test.family + "/";
        return new SingleStageRocketMaterials(new BulkMaterial(label + "nose", d[0]), new BulkMaterial(label + "body", d[1]),
            new BulkMaterial(label + "fins", d[2]), new BulkMaterial(label + "mount", d[3]), new BulkMaterial(label + "rings", d[4]));
    }

    private static StructuralMassOverride override(Family family, String id, Double mass, Double cg) {
        String ref = "TEST_ONLY " + family + "/" + id.substring("structure:".length());
        return new StructuralMassOverride(id, Optional.ofNullable(mass).map(v -> new StructuralMassOverride.Measurement(v, ref + "/mass-anchor")),
            Optional.ofNullable(cg).map(v -> new StructuralMassOverride.Measurement(v, ref + (v == .330 ? "/nat-038-cg-variant" : "/cg-anchor"))));
    }

    private static SourceBackedMotorProfile motor(Family family, MotorPropertyModelProfile model) {
        var base = SourceMotorRuntimeTest.teknofest(family.shortName);
        if (model.equals(base.propertyModelProfile())) return base;
        boolean mass = model.massModel() == MotorMassEvolutionModel.EXPLICIT_CURVE;
        boolean cg = model.cgModel() == MotorCgEvolutionModel.EXPLICIT_CURVE;
        return new SourceBackedMotorProfile(base.catalogProfile(), base.selectedCurveId(), base.motorType(),
            base.initialMassObservation(), base.propellantMassObservation(), base.geometryInterpretation(),
            base.ejection(), base.certificationEvidence(),
            mass ? List.of(new MotorMassSample(0, 3.905), new MotorMassSample(L_END, 2.000)) : null,
            mass ? Optional.of("TEST_ONLY NAT-038 linear-in-time L mass assumption") : Optional.empty(),
            cg ? List.of(new MotorCgSample(0, .2653), new MotorCgSample(L_END, .2853)) : null,
            cg ? Optional.of("TEST_ONLY NAT-038 linear L axial-CG assumption") : Optional.empty(), model);
    }

    private static PreparedSourceVehicle3DOF prepare(FlightCase test, ProbeOptions options, String suffix) {
        var family = test.family;
        var sourceMotor = motor(family, options.motorModel());
        var geometry = new GeometryResolver().resolve(geometry(family));
        var materials = materials(test);
        var structure = new StructuralMassPropertiesCalculator().evaluate(geometry, materials);
        var installation = new MotorInstallationResolver().resolveRuntime(geometry, sourceMotor);
        var smooth = new SurfaceFinish("TEST_ONLY " + family + " smooth", 0);
        var bodyFinish = options.lBodyRoughnessM() == 0 ? smooth
            : new SurfaceFinish("TEST_ONLY NAT-038 L roughness variant", options.lBodyRoughnessM());
        var context = new RuntimePhysicsEvaluationContext3DOF(geometry,
            new SingleStageRocketAerodynamicSurfaces(smooth, bodyFinish, smooth), structure, installation, options.motorModel(),
            BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE, TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,
            switch (family) { case L -> 1200; case M -> 450; case N -> test == FlightCase.N_ALTITUDE ? 1300 : 800; },
            new PropulsionTimeline(0), new USStandardAtmosphere1976Lower(), new DryAirPropertiesCalculator(), new ConstantGravityModel(),
            family == Family.M ? new ConstantWindModel(new double[]{test == FlightCase.M_WIND ? 8 : 4, -2, 0}) : new NoWindModel());
        var launch = switch (family) {
            case L -> new LaunchConditions3DOF(new double[]{0, 0, 0}, new double[]{0, 0, 20}, new double[]{0, 0, 1});
            case M -> new LaunchConditions3DOF(new double[]{100, -200, 50}, new double[]{0, 12, 16}, new double[]{0, .6, .8});
            case N -> new LaunchConditions3DOF(new double[]{-40, 25, 20}, new double[]{0, 0, 20}, new double[]{0, 0, 1});
        };
        var hardware = switch (family) {
            case L -> List.<AttachedHardwareMass>of();
            case M -> List.of(new AttachedHardwareMass("m-ballast", test == FlightCase.M_BALLAST ? 36 : 32, 1.30));
            case N -> List.of(new AttachedHardwareMass("n-ballast", 65, 1.50));
        };
        var evidence = hardware.stream().map(item -> {
            String ref = "TEST_ONLY " + item.componentId();
            return new HardwareEvidence(item.componentId(), Origin.MODEL_BASED,
                ref + (test == FlightCase.M_BALLAST ? "/nat-038-mass-variant" : "/mass"), Origin.MODEL_BASED, ref + "/cg");
        }).toList();
        var run = new SourceMotorGroundRunConfiguration3DOF(launch, context,
            new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
                ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
                GroundReferenceModel.LAUNCH_WORLD_Z_PLANE, EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION),
            new FixedStepConfig(test.step), TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT, hardware,
            Optional.empty(), family == Family.N ? Optional.of(new RecoveryConfiguration3DOF(.80, 6.0)) : Optional.empty(),
            family == Family.N ? LEGACY_FIXED_STEP : ALIGN_MOTOR_SOURCE_BOUNDARIES);
        var overrides = switch (family) {
            case L -> List.<StructuralMassOverride>of();
            case M -> List.of(override(family, BODY, 5.0, null), override(family, NOSE, null, options.mNoseCgM()));
            case N -> List.of(override(family, BODY, 9.0, 1.750));
        };
        return PreparedSourceVehicle3DOF.prepare("nat-038:" + test.name() + suffix + ":r1",
            "TEST_ONLY N37-03C geometry " + family, materials, run, overrides,
            family == Family.N ? List.of(new IncludedItem("n-electronics", BODY)) : List.of(), evidence);
    }

    private static PreparedSourceVehicle3DOF prepare(FlightCase test) {
        return prepare(test, ProbeOptions.BASE, "");
    }

    private static PhysicsEvaluationResult3DOF at(PreparedSourceVehicle3DOF prepared, double time, TranslationalState3DOF state) {
        var run = prepared.runConfiguration();
        return new PhysicsEvaluator3DOF().evaluateRuntime(time, state, run.launchConditions(), run.physicsContext(),
            run.attachedHardware().stream().mapToDouble(AttachedHardwareMass::massKg).toArray(),
            run.attachedHardware().stream().mapToDouble(AttachedHardwareMass::cgXGeoM).toArray());
    }

    private static PhysicsEvaluationResult3DOF at(PreparedSourceVehicle3DOF prepared, double time) {
        return at(prepared, time, new InitialStateBuilder().build(prepared.runConfiguration().launchConditions()));
    }

    private static StructuralMassProperties structure(PreparedSourceVehicle3DOF vehicle) {
        return vehicle.runConfiguration().physicsContext().structuralMassProperties();
    }

    private static double moment(PhysicsEvaluationResult3DOF result) {
        var mass = result.rocketMassProperties();
        return mass.totalMassKg() * mass.totalCgXGeoM();
    }

    private static double moment(StructuralMassProperties structure) {
        return structure.structureMassKg() * structure.structureCgXGeoM();
    }

    private static void residual(String quantity, double expected, double actual, double bound) {
        double error = actual - expected;
        double relative = expected == 0 ? Double.NaN : error / expected;
        double expectedUlps = error / Math.ulp(expected);
        System.out.printf(java.util.Locale.ROOT, "N38_RESIDUAL %s expected=%.17g actual=%.17g signed=%.17g relative=%.17g expectedUlps=%.17g bound=%.17g%n",
            quantity, expected, actual, error, relative, expectedUlps, bound);
        assertEquals(expected, actual, bound, quantity);
    }

    // Difference of two rounded aggregates. Scale to the operands, not the cancelled result.
    private static void difference(String quantity, double expected, double a, double b) {
        residual(quantity, expected, b - a, 16 * (Math.ulp(a) + Math.ulp(b)));
    }

    private static void preserveNonGravity(PhysicsEvaluationResult3DOF a, PhysicsEvaluationResult3DOF b) {
        assertEquals(a.motorThrustState(), b.motorThrustState());
        assertEquals(a.basicDrag(), b.basicDrag());
        assertEquals(a.gravityAccelerationWorldMS2(), b.gravityAccelerationWorldMS2());
        assertEquals(a.dynamics().forces().thrustForceWorldN(), b.dynamics().forces().thrustForceWorldN());
        assertEquals(a.dynamics().forces().dragForceWorldN(), b.dynamics().forces().dragForceWorldN());
    }

    private static void checkMassResponse(String label, PhysicsEvaluationResult3DOF a, PhysicsEvaluationResult3DOF b) {
        preserveNonGravity(a, b);
        double ma = a.rocketMassProperties().totalMassKg();
        double mb = b.rocketMassProperties().totalMassKg();
        var force = a.dynamics().forces();
        for (int i = 0; i < 3; i++) {
            double nonGravity = force.thrustForceWorldN().get(i) + force.dragForceWorldN().get(i);
            double expected = nonGravity * (1 / mb - 1 / ma);
            difference(label + " delta acceleration axis " + i + " [m/s2]", expected,
                a.dynamics().accelerationWorldMS2().get(i), b.dynamics().accelerationWorldMS2().get(i));
        }
    }

    @Test
    void bodyDensityChangesOnlyTheSelectedStructuralContributionAndMassResponse() {
        var base = prepare(FlightCase.L_BASE);
        var changed = prepare(FlightCase.L_DENSITY);
        assertEquals(base.runConfiguration().physicsContext().resolvedGeometry(), changed.runConfiguration().physicsContext().resolvedGeometry());
        var a = structure(base);
        var b = structure(changed);
        assertEquals(4500, changed.materials().body().densityKgM3());
        assertEquals(a.nose(), b.nose());
        assertEquals(a.fins(), b.fins());
        assertEquals(a.motorMount(), b.motorMount());
        assertEquals(a.centeringRings(), b.centeringRings());
        assertEquals(a.body().cgXGeoM(), b.body().cgXGeoM());
        // (R - r)(R + r) from the frozen wall thickness and diameters.
        double expectedMass = 500 * Math.PI * .003 * .157 * 1.600;
        difference("L body mass [kg]", expectedMass, a.body().massKg(), b.body().massKg());
        difference("L structural mass [kg]", expectedMass, a.structureMassKg(), b.structureMassKg());
        difference("L structural moment [kg m]", expectedMass * 1.120, moment(a), moment(b));
        var pa = at(base, .317);
        var pb = at(changed, .317);
        assertEquals(pa.motorMassProperties(), pb.motorMassProperties());
        checkMassResponse("L density", pa, pb);
    }

    @Test
    void ballastChangesTotalMassAndMomentAtFixedStation() {
        var base = prepare(FlightCase.M_BASE);
        var changed = prepare(FlightCase.M_BALLAST);
        var a = at(base, 1.298);
        var b = at(changed, 1.298);
        assertEquals(structure(base), structure(changed));
        assertEquals(List.of(new AttachedHardwareMass("m-ballast", 36, 1.30)), changed.runConfiguration().attachedHardware());
        assertEquals(base.includedItems(), changed.includedItems());
        assertEquals(base.hardwareEvidence().get(0).cgReference(), changed.hardwareEvidence().get(0).cgReference());
        assertEquals(Origin.MODEL_BASED, changed.hardwareEvidence().get(0).massOrigin());
        assertEquals(a.motorMassProperties(), b.motorMassProperties());
        residual("M ballast total mass [kg]", 4, b.rocketMassProperties().totalMassKg() - a.rocketMassProperties().totalMassKg(), 0);
        difference("M ballast total moment [kg m]", 5.2, moment(a), moment(b));
        checkMassResponse("M ballast", a, b);
    }

    @Test
    void eastWindChangesRelativeFlowAndDynamicPressure() {
        var a = at(prepare(FlightCase.M_BASE), 1.298);
        var b = at(prepare(FlightCase.M_WIND), 1.298);
        assertEquals(Vector.of(new double[]{-4, 14, 16}, 3, "reference flow"), a.relativeFlow());
        assertEquals(Vector.of(new double[]{-8, 14, 16}, 3, "variant flow"), b.relativeFlow());
        assertEquals(a.atmosphere(), b.atmosphere());
        for (int i = 0; i < 3; i++) assertEquals(i == 0 ? -4 : 0, b.relativeFlow().get(i) - a.relativeFlow().get(i));
        double rho = a.atmosphere().densityKgM3();
        // Squared component sums for WORLD velocity [0,12,16] under the two frozen winds.
        double qa = .5 * rho * 468;
        double qb = .5 * rho * 516;
        residual("M base dynamic pressure [Pa]", qa, a.flightConditions().dynamicPressurePa(), 4 * Math.ulp(qa));
        residual("M wind dynamic pressure [Pa]", qb, b.flightConditions().dynamicPressurePa(), 4 * Math.ulp(qb));
        assertEquals(a.rocketMassProperties(), b.rocketMassProperties());
        assertEquals(a.motorThrustState(), b.motorThrustState());
    }

    @Test
    void launchEnvironmentOffsetDoesNotMoveTheWorldStateOrGroundPlane() {
        var base = prepare(FlightCase.N_BASE);
        var changed = prepare(FlightCase.N_ALTITUDE);
        assertEquals(base.runConfiguration().launchConditions(), changed.runConfiguration().launchConditions());
        assertEquals(base.runConfiguration().eventDetectionProfile(), changed.runConfiguration().eventDetectionProfile());
        assertEquals(base.runConfiguration().recovery(), changed.runConfiguration().recovery());
        var state = new TranslationalState3DOF(Vector.of(new double[]{-40, 25, 45}, 3, "probe position"),
            base.runConfiguration().launchConditions().initialVelocityWorldMS());
        var a = at(base, 2.0955, state);
        var b = at(changed, 2.0955, state);
        residual("N environment shift [geopotential m]", 500, b.environmentAltitudeM() - a.environmentAltitudeM(), 0);
        assertEquals(825, a.environmentAltitudeM());
        assertEquals(1325, b.environmentAltitudeM());
        assertEquals(a.relativeFlow(), b.relativeFlow());
        assertEquals(a.gravityAccelerationWorldMS2(), b.gravityAccelerationWorldMS2());
        assertEquals(a.motorThrustState(), b.motorThrustState());
        assertEquals(a.motorMassProperties(), b.motorMassProperties());
        assertEquals(a.rocketMassProperties(), b.rocketMassProperties());
        System.out.println("N38_ATMOSPHERE matched WORLD z=45 m t=2.0955 s base=" + a.atmosphere() + " variant=" + b.atmosphere()
            + " baseAir=" + a.airProperties() + " variantAir=" + b.airProperties());
    }

    @Test
    void structuralCgAltersMomentWithoutChangingTheFixedDirectionDynamics() {
        var base = prepare(FlightCase.M_BASE);
        var changed = prepare(FlightCase.M_BASE, new ProbeOptions(.330, 0, ProbeOptions.BASE.motorModel()), ":nose-cg");
        var a = at(base, 1.298);
        var b = at(changed, 1.298);
        assertEquals(.330, structure(changed).nose().cgXGeoM());
        assertEquals(structure(base).nose().massKg(), structure(changed).nose().massKg());
        assertEquals(structure(base).structureMassKg(), structure(changed).structureMassKg());
        assertEquals(a.rocketMassProperties().totalMassKg(), b.rocketMassProperties().totalMassKg());
        assertEquals(a.motorMassProperties(), b.motorMassProperties());
        preserveNonGravity(a, b);
        assertEquals(a.dynamics(), b.dynamics());
        double expected = structure(base).nose().massKg() * .020;
        difference("M structural CG shift moment [kg m]", expected, moment(structure(base)), moment(structure(changed)));
        difference("M vehicle CG shift moment [kg m]", expected, moment(a), moment(b));
    }

    @Test
    void isolatedBodyRoughnessChangesTheExpectedFrictionBranchAndForce() {
        var base = prepare(FlightCase.L_BASE);
        var changed = prepare(FlightCase.L_BASE, new ProbeOptions(.310, .000100, ProbeOptions.BASE.motorModel()), ":body-finish");
        var state = new TranslationalState3DOF(base.runConfiguration().launchConditions().initialPositionWorldM(),
            Vector.of(new double[]{0, 0, 150}, 3, "roughness probe velocity"));
        var a = at(base, 0, state);
        var b = at(changed, 0, state);
        var da = a.basicDrag();
        var db = b.basicDrag();
        assertEquals(a.flightConditions(), b.flightConditions());
        assertEquals(a.rocketMassProperties(), b.rocketMassProperties());
        assertEquals(a.motorThrustState(), b.motorThrustState());
        assertEquals(da.noseSkinFriction(), db.noseSkinFriction());
        assertEquals(da.finSkinFriction(), db.finSkinFriction());
        assertEquals(da.pressureCd(), db.pressureCd());
        assertEquals(da.baseCd(), db.baseCd());
        System.out.println("N38_ROUGHNESS base=" + da.bodySkinFriction() + " variant=" + db.bodySkinFriction()
            + " Cd0=" + da.totalCd0() + "/" + db.totalCd0() + " dragN="
            + a.dynamics().forces().dragForceWorldN() + "/" + b.dynamics().forces().dragForceWorldN());
        assertEquals(SkinFrictionBranch.SMOOTH, da.bodySkinFriction().selectedBranch());
        assertEquals(SkinFrictionBranch.ROUGHNESS_LIMITED, db.bodySkinFriction().selectedBranch());
        double compressibility = 1 - .10 * Math.pow(a.flightConditions().mach(), 2);
        double expectedCf = .032 * Math.pow(.000100 / 1.920, .2) * compressibility;
        residual("L rough body Cf [1]", expectedCf, db.bodySkinFriction().selectedCf(), 8 * Math.ulp(expectedCf));
        assertTrue(db.totalCd0() > da.totalCd0());
        double area = Math.PI * .080 * .080;
        double q = a.flightConditions().dynamicPressurePa();
        double expectedForceDifference = -q * area * (db.bodyFrictionCd() - da.bodyFrictionCd());
        var dragA = a.dynamics().forces().dragForceWorldN();
        var dragB = b.dynamics().forces().dragForceWorldN();
        assertEquals(0.0, dragB.get(0) - dragA.get(0));
        assertEquals(0.0, dragB.get(1) - dragA.get(1));
        difference("L roughness axial drag [N]", expectedForceDifference, dragA.get(2), dragB.get(2));
    }

    @Test
    void motorMassLawChangesOnlyDepletionAtMatchingMotorTimes() {
        var base = prepare(FlightCase.L_BASE);
        var model = new MotorPropertyModelProfile(MotorMassEvolutionModel.EXPLICIT_CURVE, MotorCgEvolutionModel.FIXED_MIDPOINT);
        var changed = prepare(FlightCase.L_BASE, new ProbeOptions(.310, 0, model), ":explicit-mass");
        var source = (SourceBackedMotorProfile) base.runConfiguration().physicsContext().motorInstallation().motor();
        var totalImpulse = impulse(source, L_END);
        var explicitExpected = new double[]{3.905, 2.9525, 2.000, 2.000};
        for (int i = 0; i < MOTOR_PROBE_TIMES.length; i++) {
            double time = MOTOR_PROBE_TIMES[i];
            var a = at(base, time);
            var b = at(changed, time);
            double expected = BigDecimal.valueOf(3.905).subtract(BigDecimal.valueOf(1.905)
                .multiply(impulse(source, time).divide(totalImpulse, MathContext.DECIMAL128))).doubleValue();
            String label = "L mass law t=" + time;
            residual(label + " impulse proportional [kg]", expected, a.motorMassProperties().massKg(), 128 * Math.ulp(expected));
            residual(label + " explicit linear [kg]", explicitExpected[i], b.motorMassProperties().massKg(),
                128 * Math.ulp(explicitExpected[i]));
            assertEquals(a.motorMassProperties().cgXGeoM(), b.motorMassProperties().cgXGeoM());
            difference(label + " total mass [kg]", explicitExpected[i] - expected,
                a.rocketMassProperties().totalMassKg(), b.rocketMassProperties().totalMassKg());
            difference(label + " total moment [kg m]", (explicitExpected[i] - expected) * 1.6497, moment(a), moment(b));
            checkMassResponse(label, a, b);
        }
    }

    // Independent decimal integral of the original ENG, including the implicit zero sample.
    private static BigDecimal impulse(SourceBackedMotorProfile motor, double time) {
        var rows = new String(motor.selectedThrustSource().source().sourceBytes(), StandardCharsets.UTF_8).lines()
            .map(String::trim).filter(row -> !row.isEmpty() && !row.startsWith(";")).skip(1).toList();
        var at = BigDecimal.valueOf(time);
        var previousTime = BigDecimal.ZERO;
        var previousForce = BigDecimal.ZERO;
        var sum = BigDecimal.ZERO;
        for (String row : rows) {
            if (at.compareTo(previousTime) <= 0) break;
            var columns = row.split("\\s+");
            var end = new BigDecimal(columns[0]);
            var force = new BigDecimal(columns[1]);
            var duration = at.min(end).subtract(previousTime);
            var fraction = duration.divide(end.subtract(previousTime), MathContext.DECIMAL128);
            var change = force.subtract(previousForce).multiply(fraction);
            sum = sum.add(previousForce.add(change.divide(BigDecimal.valueOf(2))).multiply(duration));
            previousTime = end;
            previousForce = force;
        }
        return sum;
    }

    @Test
    void motorCgLawChangesOnlyInstalledMomentAtMatchingMotorTimes() {
        var base = prepare(FlightCase.L_BASE);
        var model = new MotorPropertyModelProfile(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL, MotorCgEvolutionModel.EXPLICIT_CURVE);
        var changed = prepare(FlightCase.L_BASE, new ProbeOptions(.310, 0, model), ":explicit-cg");
        // The ENG header specifies 530.6 mm. Apply the fixture's SI conversion before halving.
        double sourceMidpointM = (530.6 / 1000.0) / 2.0;
        var shifts = new double[]{0, .010, .020, .020};
        for (int i = 0; i < MOTOR_PROBE_TIMES.length; i++) {
            double time = MOTOR_PROBE_TIMES[i];
            var a = at(base, time);
            var b = at(changed, time);
            String label = "L CG law t=" + time;
            residual(label + " midpoint [m]", sourceMidpointM, a.motorMassProperties().cgLocalFromFrontM(), 0);
            difference(label + " local shift [m]", shifts[i],
                a.motorMassProperties().cgLocalFromFrontM(), b.motorMassProperties().cgLocalFromFrontM());
            difference(label + " installed shift [m]", shifts[i],
                a.motorMassProperties().cgXGeoM(), b.motorMassProperties().cgXGeoM());
            assertEquals(a.motorMassProperties().massKg(), b.motorMassProperties().massKg());
            assertEquals(a.rocketMassProperties().totalMassKg(), b.rocketMassProperties().totalMassKg());
            difference(label + " vehicle moment [kg m]", shifts[i] * a.motorMassProperties().massKg(), moment(a), moment(b));
            preserveNonGravity(a, b);
            assertEquals(a.dynamics(), b.dynamics());
        }
    }

    private static RunControl3DOF control() {
        var clock = new AtomicLong();
        var reference = new AtomicReference<RunControl3DOF>();
        var control = new RunControl3DOF(new RunControl3DOF.MonitoringPolicy(1, 1_000_000_000_000L, 1000, 100000),
            progress -> {
                if (progress.acceptedSegments() >= WATCHDOG_SEGMENTS) reference.get().requestCancellation();
            }, warning -> {}, () -> clock.addAndGet(1_000_000L));
        reference.set(control);
        return control;
    }

    @ParameterizedTest
    @EnumSource(FlightCase.class)
    void frozenGroundAttempt(FlightCase test) {
        System.out.println("N38_ATTEMPT " + test + " TEST_ONLY N37-03C/r1; " + test.changedInput + "; nominalStepS=" + test.step);
        var prepared = prepare(test);
        var run = prepared.runConfiguration();
        var source = (SourceBackedMotorProfile) run.physicsContext().motorInstallation().motor();
        assertEquals(test.family.sha256, source.selectedThrustSource().source().sha256());
        assertEquals("teknofest26:" + test.family.designation + ":Rev1", source.selectedCurveId());
        assertEquals(ProbeOptions.BASE.motorModel(), source.propertyModelProfile());
        assertTrue(run.launchGuide().isEmpty());
        var completed = prepared.runUntilGround(control());
        var result = completed.result();
        report(test, prepared, result);
        try {
            assertSame(prepared, completed.configuration());
            assertEquals(RunControl3DOF.Status.GROUND, result.terminationReason(), () -> result.failure().toString());
            assertTrue(result.failure().isEmpty());
            assertEquals(result.acceptedSegments() + 1, result.samples().size());
            var burnout = result.events().stream().filter(e -> e.eventType() == FlightEventType.BURNOUT).toList();
            assertEquals(1, burnout.size());
            assertEquals(test.family.burnoutS, burnout.get(0).timeS());
            assertEquals(run.launchConditions().initialPositionWorldM().get(2),
                result.estimatedGroundContactPositionWorldM().orElseThrow().get(2));
            if (run.recovery().isPresent()) checkRecovery(run, result);
            else assertTrue(result.recoveryEvents().isEmpty());
        } catch (AssertionError | RuntimeException failure) {
            System.out.println("N38_FIRST_MISMATCH " + test + " " + failure);
            result.samples().forEach(sample -> System.out.println("N38_HISTORY " + sample));
            throw failure;
        }
    }

    private static void checkRecovery(SourceMotorGroundRunConfiguration3DOF run, GroundRunResult3DOF result) {
        double maximum = Double.NEGATIVE_INFINITY;
        boolean ascent = false;
        RecoveryFlightResult3DOF.Sample firstDrop = null;
        for (var sample : result.samples()) {
            if (sample.phase() != FlightPhase3DOF.FREE_FLIGHT) continue;
            var state = sample.point().state();
            double z = state.positionWorldM().get(2);
            maximum = Math.max(maximum, z);
            ascent |= state.velocityWorldMS().get(2) > 0;
            if (ascent && z < maximum - .01) {
                firstDrop = sample;
                break;
            }
        }
        assertNotNull(firstDrop);
        assertEquals(List.of(RecoveryFlightResult3DOF.EventType.PHYSICAL_APOGEE, RecoveryFlightResult3DOF.EventType.RECOVERY_ACTIVATION),
            result.recoveryEvents().stream().map(RecoveryFlightResult3DOF.Event::type).toList());
        var detection = result.recoveryEvents().get(0);
        var activation = result.recoveryEvents().get(1);
        assertEquals(firstDrop.point(), detection.point());
        double scheduled = firstDrop.point().timeS() + run.recovery().orElseThrow().deploymentDelayS();
        assertEquals(scheduled, detection.scheduledActivationTimeS().orElseThrow());
        assertEquals(scheduled, activation.point().timeS());
        assertEquals(RecoveryFlightResult3DOF.State.ACTIVE, result.recoveryState());
    }

    private static double altitude(RecoveryFlightResult3DOF.Sample sample, double ground) {
        return sample.point().state().positionWorldM().get(2) - ground;
    }

    private static void report(FlightCase test, PreparedSourceVehicle3DOF prepared, GroundRunResult3DOF result) {
        var run = prepared.runConfiguration();
        var context = run.physicsContext();
        double ground = run.launchConditions().initialPositionWorldM().get(2);
        var samples = result.samples();
        System.out.println("N38_RUN " + test + " id=" + prepared.configurationId() + " outcome=" + result.terminationReason()
            + " failure=" + result.failure() + " accepted=" + result.acceptedSegments() + " samples=" + samples.size()
            + " nominalStep=" + run.fixedStepConfig().stepSizeS() + " alignment=" + run.alignmentMode());
        System.out.println("N38_CONFIG " + test + " " + prepared.designSourceReference() + " materials=" + prepared.materials()
            + " geometry=" + context.resolvedGeometry().source() + " launch=" + run.launchConditions() + " hardware=" + run.attachedHardware()
            + " overrides=" + prepared.overrides() + " evidence=" + prepared.hardwareEvidence() + " included=" + prepared.includedItems()
            + " recovery=" + run.recovery() + " groundZ=" + ground + " environmentAltitude=" + context.launchEnvironmentAltitudeM());
        System.out.println("N38_EVENTS " + test + " " + result.events());
        System.out.println("N38_RECOVERY " + test + " state=" + result.recoveryState() + " " + result.recoveryEvents());
        if (samples.isEmpty()) return;
        var peak = samples.stream().max(Comparator.comparingDouble(s -> altitude(s, ground))).orElseThrow();
        double maxMach = samples.stream().mapToDouble(s -> s.physics().flightConditions().mach()).max().orElseThrow();
        double maxSpeed = samples.stream().mapToDouble(s -> {
            var v = s.point().state().velocityWorldMS();
            return Math.hypot(Math.hypot(v.get(0), v.get(1)), v.get(2));
        }).max().orElseThrow();
        double minVz = samples.stream().mapToDouble(s -> s.point().state().velocityWorldMS().get(2)).min().orElseThrow();
        double maxVz = samples.stream().mapToDouble(s -> s.point().state().velocityWorldMS().get(2)).max().orElseThrow();
        var motor = (SourceBackedMotorProfile) context.motorInstallation().motor();
        var knotTimes = motor.thrustCurve().stream().map(ThrustSample::timeS).toList();
        double minDt = Double.POSITIVE_INFINITY;
        double maxDt = 0;
        int sourceEndpoints = 0;
        int sourceSplits = 0;
        for (int i = 1; i < samples.size(); i++) {
            double start = samples.get(i - 1).point().timeS();
            double end = samples.get(i).point().timeS();
            minDt = Math.min(minDt, end - start);
            maxDt = Math.max(maxDt, end - start);
            if (run.alignmentMode() == ALIGN_MOTOR_SOURCE_BOUNDARIES && knotTimes.contains(end)) {
                sourceEndpoints++;
                if (end < start + test.step) sourceSplits++;
            }
        }
        System.out.println("N38_METRICS " + test + " peakM=" + altitude(peak, ground) + " peakS=" + peak.point().timeS()
            + " maxSpeedMS=" + maxSpeed + " maxMach=" + maxMach + " minVzMS=" + minVz + " maxVzMS=" + maxVz
            + " minDtS=" + (samples.size() > 1 ? minDt : "NA") + " maxDtS=" + (samples.size() > 1 ? maxDt : "NA")
            + " sourceEndpoints=" + sourceEndpoints + " sourceSplits=" + sourceSplits);
        System.out.println("N38_STRUCTURE " + test + " " + structure(prepared));
        reportSnapshot(test, "initial", samples.get(0));
        samples.stream().filter(s -> s.point().timeS() >= test.family.burnoutS).findFirst()
            .ifPresent(s -> reportSnapshot(test, "first-at-or-after-burnout", s));
        result.lastAcceptedSample().ifPresent(s -> reportSnapshot(test, "last", s));
    }

    private static void reportSnapshot(FlightCase test, String label, RecoveryFlightResult3DOF.Sample sample) {
        var p = sample.physics();
        System.out.println("N38_SNAPSHOT " + test + " " + label + " point=" + sample.point() + " motorTime=" + p.motorTimeS()
            + " motor=" + p.motorMassProperties() + " vehicle=" + p.rocketMassProperties() + " thrust=" + p.motorThrustState()
            + " atmosphere=" + p.atmosphere() + " geopotentialM=" + p.environmentAltitudeM() + " wind=" + p.airMassVelocityWorldMS());
    }
}
