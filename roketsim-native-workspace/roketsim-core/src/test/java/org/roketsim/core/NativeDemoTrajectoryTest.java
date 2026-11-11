package org.roketsim.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.math.Vector;
import java.util.List;

/** NAT-022's nine source tests. All numbers here are verification fixtures, not defaults. */
class NativeDemoTrajectoryTest {
    static SimulationRunConfiguration3DOF configuration(double step,int maximumSteps) {
        var source=new SingleStageRocketGeometry(.1,
            new ConicalNoseGeometry(.3,NoseConstructionMode.HOLLOW_SHELL,.002),
            new CylindricalBodyGeometry(.7,.002),
            new TrapezoidalFinSetGeometry(4,.18,.08,.12,.05,.72,.003,
                FinCrossSection.SQUARE,FinAngularArrangement.EQUALLY_SPACED),
            new MotorAttachmentGeometry(new MotorMountTubeGeometry(.12,.029,.001,0),
                new CenteringRingPairGeometry(.003),.005),ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
        var geometry=new GeometryResolver().resolve(source);
        var materials=new SingleStageRocketMaterials(MaterialCatalog.POLYSTYRENE,MaterialCatalog.CARDBOARD,
            MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD);
        var structure=new StructuralMassPropertiesCalculator().evaluate(geometry,materials);
        var installation=new MotorInstallationResolver().resolve(geometry,MotorCatalogs.AEROTECH_F50_4T);
        var smooth=new SurfaceFinish("NAT-022 synthetic smooth V&V surface",0);
        var context=new PhysicsEvaluationContext3DOF(geometry,
            new SingleStageRocketAerodynamicSurfaces(smooth,smooth,smooth),structure,installation,
            MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,
            BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,
            TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,1200,new PropulsionTimeline(0),
            new USStandardAtmosphere1976Lower(),new DryAirPropertiesCalculator(),
            new ConstantGravityModel(),new NoWindModel());
        return new SimulationRunConfiguration3DOF(
            new LaunchConditions3DOF(new double[]{0,0,0},new double[]{0,0,0},new double[]{0,0,1}),
            context,new MotorCurveAnalyzer().analyze(MotorCatalogs.AEROTECH_F50_4T),
            new FlightEventDetectionProfile3DOF(BurnoutDetectionModel.PROPULSION_CURVE_END_TIME,
                ApogeeDetectionModel.WORLD_VERTICAL_VELOCITY_DOWNWARD_CROSSING,
                GroundReferenceModel.LAUNCH_WORLD_Z_PLANE,EventLocalizationModel.LINEAR_BRACKET_INTERPOLATION),
            new FixedStepConfig(step),new SimulationRunLimits3DOF(maximumSteps),
            TerminalEventHandlingModel.REJECT_INTERIOR_CANDIDATE_ACCEPT_ENDPOINT);
    }
    static SimulationResult3DOF execute(double step,int limit) {
        return new SimulationResult3DOF(new SimulationEngine3DOF().run(configuration(step,limit)));
    }
    static SimulationResult3DOF primary,repeated,fine;
    @BeforeAll static void runs() { primary=execute(.01,5000);repeated=execute(.01,5000);fine=execute(.005,10000); }
    static List<SimulationResult3DOF> distinctRuns() { return List.of(primary,fine); }
    static void finite(Vector vector) { for(double x:vector.toArray())assertTrue(Double.isFinite(x)); }
    @Test void realChainCompletesAndResultIntegrityHolds() {
        for(var r:distinctRuns()) {
            assertEquals(SimulationTerminationReason.TERMINAL_EVENT,r.terminationReason());
            assertTrue(r.stepsPerformed()>0);assertTrue(r.stepsPerformed()<(r==primary?5000:10000));
            assertTrue(r.samples().size()>=2);assertSame(r.samples().get(0),r.initialSample());
            assertSame(r.samples().get(r.samples().size()-1),r.lastAcceptedSample());assertSame(r.groundEvent(),r.terminalEvent());
            assertEquals(r==primary?1584:3167,r.stepsPerformed());assertEquals(r.stepsPerformed(),r.samples().size());
        }
    }
    @Test void chronologyFiniteDomainsAndVerticalSymmetry() {
        for(var r:distinctRuns()) { double last=Double.NEGATIVE_INFINITY;
            for(var s:r.samples()) {
                assertTrue(s.point().timeS()>last);last=s.point().timeS();var p=s.physics();
                finite(s.point().state().positionWorldM());finite(s.point().state().velocityWorldMS());
                finite(p.gravityAccelerationWorldMS2());finite(p.dynamics().accelerationWorldMS2());
                for(int i=0;i<2;i++) { // NAT-022's rtol=0 / atol=1e-12 symmetry assertion.
                    assertEquals(0,s.point().state().positionWorldM().get(i),1e-12);
                    assertEquals(0,s.point().state().velocityWorldMS().get(i),1e-12);
                }
                assertTrue(p.rocketMassProperties().totalMassKg()>0);assertTrue(p.flightConditions().dynamicPressurePa()>=0);
                assertTrue(p.basicDrag().totalCd0()>=0);assertTrue(p.flightConditions().mach()>=0 && p.flightConditions().mach()<=1);
                assertTrue(p.flightConditions().reynolds()>=0);assertTrue(Double.isFinite(p.motorThrustState().thrustN()));
            }
        }
    }
    @Test void eventCardinalityOrderAndExactBurnoutAuthority() {
        for(var r:distinctRuns()) {
            assertEquals(3,r.events().size());
            for(var type:FlightEventType.values())assertEquals(1,r.eventsOfType(type).size());
            assertEquals(List.of(FlightEventType.BURNOUT,FlightEventType.APOGEE,FlightEventType.GROUND),r.events().stream().map(FlightEventOccurrence3DOF::eventType).toList());
            assertTrue(r.burnoutEvent().timeS()<r.apogeeEvent().timeS() && r.apogeeEvent().timeS()<r.groundEvent().timeS());
            assertEquals(1.43,r.burnoutEvent().timeS(),2e-15);assertSame(r.groundEvent(),r.events().get(2));
            assertFalse(r.burnoutEvent().isTerminal());assertFalse(r.apogeeEvent().isTerminal());
        }
        assertEquals(primary.burnoutEvent().timeS(),fine.burnoutEvent().timeS());
    }
    @Test void earlyPoweredFlightAndNoBelowGroundSamples() {
        for(var r:distinctRuns()) {
            var first=r.samples().get(1);assertTrue(first.point().state().positionWorldM().get(2)>0);
            assertTrue(first.point().state().velocityWorldMS().get(2)>0);
            assertTrue(r.samples().stream().allMatch(s->s.point().state().positionWorldM().get(2)>=0));
            assertTrue(r.samples().stream().anyMatch(s->s.point().timeS()>0 && s.point().timeS()<r.burnoutEvent().timeS()
                && s.physics().motorThrustState().thrustN()>0 && s.point().state().velocityWorldMS().get(2)>0));
        }
    }
    @Test void massEvolutionPositiveAndNonincreasing() {
        for(var r:distinctRuns()) { double previous=Double.POSITIVE_INFINITY;
            for(var s:r.samples())if(s.point().timeS()<=r.burnoutEvent().timeS()) {
                double mass=s.physics().rocketMassProperties().totalMassKg();assertTrue(mass>0 && mass<=previous);previous=mass;
            }
            assertTrue(r.lastAcceptedSample().physics().motorMassProperties().massKg()<r.initialSample().physics().motorMassProperties().massKg());
        }
    }
    @Test void coastApogeeDescentAndGroundSemantics() {
        for(var r:distinctRuns()) {
            assertTrue(r.samples().stream().anyMatch(s->s.point().timeS()>r.burnoutEvent().timeS() && s.point().timeS()<r.apogeeEvent().timeS() && s.point().state().velocityWorldMS().get(2)>0));
            assertTrue(r.samples().stream().anyMatch(s->s.point().timeS()>r.apogeeEvent().timeS() && s.point().timeS()<r.groundEvent().timeS() && s.point().state().velocityWorldMS().get(2)<0));
            assertEquals(0,r.apogeeEvent().estimatedState().velocityWorldMS().get(2),2e-15);
            assertTrue(r.apogeeEvent().estimatedState().positionWorldM().get(2)>r.burnoutEvent().estimatedState().positionWorldM().get(2));
            assertEquals(0,r.groundEvent().estimatedState().positionWorldM().get(2),2e-15);
            assertTrue(r.groundEvent().estimatedState().velocityWorldMS().get(2)<0);assertTrue(r.groundEvent().isTerminal());
            assertEquals(r.groundEvent().timeS(),r.terminationTimeS());assertTrue(r.groundEvent().interpolationFraction()<1);
            assertTrue(r.lastAcceptedSample().point().timeS()<r.terminationTimeS());
            assertTrue(r.samples().stream().noneMatch(s->s.point().timeS()==r.terminationTimeS()));
        }
    }
    @Test void identicalPrimaryRunsAreDeterministic() {
        assertEquals(primary.execution(),repeated.execution());
        assertNotSame(primary.recordedData(),repeated.recordedData());
        assertThrows(UnsupportedOperationException.class,()->primary.samples().clear());
        double[] copy=primary.lastAcceptedSample().point().state().positionWorldM().toArray();copy[2]=-999;
        assertTrue(primary.lastAcceptedSample().point().state().positionWorldM().get(2)>0);
    }
    @Test void fixedStepSensitivitySourcePolicy() {
        // NAT-022 V&V-only sensitivity thresholds, NEVER cross-language tolerances.
        assertTrue(Math.abs(primary.apogeeEvent().timeS()-fine.apogeeEvent().timeS())<=.020);
        assertTrue(Math.abs(primary.groundEvent().timeS()-fine.groundEvent().timeS())<=.020);
        double height=fine.apogeeEvent().estimatedState().positionWorldM().get(2);assertTrue(height>0);
        assertTrue(Math.abs(primary.apogeeEvent().estimatedState().positionWorldM().get(2)-height)/height<=.01);
    }
    @Test void diagnosticSummary() throws Exception {
        for(var r:distinctRuns())System.out.println("JAVA_NAT022_DIAGNOSTICS "+NativeTrajectoryParityProbe.summary(r));
    }
}
