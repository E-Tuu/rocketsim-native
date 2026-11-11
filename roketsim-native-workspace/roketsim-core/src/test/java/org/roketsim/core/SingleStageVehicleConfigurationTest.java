package org.roketsim.core;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.simulation.*;
import static org.junit.jupiter.api.Assertions.*;

/** NAT-024 integration contracts; existing geometry/motor/trajectory fixtures are reused. */
class SingleStageVehicleConfigurationTest {
    static final List<AttachedHardwareMass> HARDWARE=List.of(
        new AttachedHardwareMass("avionics",.08,.4),new AttachedHardwareMass("recovery_hardware",.12,1));
    static SimulationRunConfiguration3DOF base;
    static SimulationExecution3DOF legacy,empty,attached,repeated,different;
    @BeforeAll static void runRealChains() {
        base=NativeDemoTrajectoryTest.configuration(.01,5000);
        var engine=new SimulationEngine3DOF();
        legacy=engine.run(base);
        empty=engine.run(new SingleStageVehicleRunConfiguration3DOF(base,List.of()));
        var configured=new SingleStageVehicleRunConfiguration3DOF(base,HARDWARE);
        attached=engine.run(configured); repeated=engine.run(configured);
        different=engine.run(new SingleStageVehicleRunConfiguration3DOF(base,List.of(new AttachedHardwareMass("battery",.03,.5))));
    }
    static void massError(String code,String field,double value,Runnable action) {
        var e=assertThrowsExactly(MassValidationError.class,action::run);
        assertEquals(code,e.errorCode()); assertEquals(field,e.fieldName());
        assertEquals(Double.doubleToLongBits(value),Double.doubleToLongBits(e.value()));
    }
    @Test void exactNewAndPreservedSchemasAndPublicEntrypoints() throws Exception {
        // CFG24-T01..04/T16: no accepted record fields are changed.
        assertEquals(List.of("componentId","massKg","cgXGeoM"),Arrays.stream(AttachedHardwareMass.class.getRecordComponents()).map(c->c.getName()).toList());
        assertEquals(List.of(String.class,double.class,double.class),Arrays.stream(AttachedHardwareMass.class.getRecordComponents()).map(c->c.getType()).toList());
        assertEquals(List.of("baseRun","attachedHardware"),Arrays.stream(SingleStageVehicleRunConfiguration3DOF.class.getRecordComponents()).map(c->c.getName()).toList());
        assertEquals(14,PhysicsEvaluationContext3DOF.class.getRecordComponents().length);
        assertEquals(7,SimulationRunConfiguration3DOF.class.getRecordComponents().length);
        assertEquals(13,PhysicsEvaluationResult3DOF.class.getRecordComponents().length);
        assertEquals(2,RocketMassProperties.class.getRecordComponents().length);
        assertEquals(3,SimulationExecution3DOF.class.getRecordComponents().length);
        assertEquals(1,SimulationResult3DOF.class.getRecordComponents().length);
        assertEquals(2,RecordedFlightSample3DOF.class.getRecordComponents().length);
        assertEquals(2,RecordedFlightData3DOF.class.getRecordComponents().length);
        for(var type:List.of(PhysicsEvaluator3DOF.class,SimulationEngine3DOF.class)) {
            assertEquals(0,type.getDeclaredFields().length);
            // NAT-026/035/036 and N37-02 add explicit entrypoints; original signatures remain checked below.
            assertEquals(type==SimulationEngine3DOF.class?6:3,Arrays.stream(type.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).count());
        }
        PhysicsEvaluator3DOF.class.getMethod("evaluate",double.class,TranslationalState3DOF.class,LaunchConditions3DOF.class,PhysicsEvaluationContext3DOF.class);
        PhysicsEvaluator3DOF.class.getMethod("evaluate",double.class,TranslationalState3DOF.class,LaunchConditions3DOF.class,PhysicsEvaluationContext3DOF.class,double[].class,double[].class);
        assertEquals(PhysicsEvaluationResult3DOF.class,PhysicsEvaluator3DOF.class.getMethod("evaluateRuntime",double.class,TranslationalState3DOF.class,LaunchConditions3DOF.class,RuntimePhysicsEvaluationContext3DOF.class,double[].class,double[].class).getReturnType());
        assertEquals(GroundRunResult3DOF.class,SimulationEngine3DOF.class.getMethod("runSourceUntilGround",SourceMotorGroundRunConfiguration3DOF.class,RunControl3DOF.class).getReturnType());
        assertEquals(GroundRunResult3DOF.class,SimulationEngine3DOF.class.getMethod("runUntilGround",UntilGroundRunConfiguration3DOF.class,RunControl3DOF.class).getReturnType());
        assertEquals(RecoveryFlightResult3DOF.class,SimulationEngine3DOF.class.getMethod("run",RecoveryRunConfiguration3DOF.class).getReturnType());
        assertEquals(SimulationExecution3DOF.class,SimulationEngine3DOF.class.getMethod("run",SimulationRunConfiguration3DOF.class).getReturnType());
        assertEquals(SimulationExecution3DOF.class,SimulationEngine3DOF.class.getMethod("run",SingleStageVehicleRunConfiguration3DOF.class).getReturnType());
        assertEquals(SimulationExecution3DOF.class,SimulationEngine3DOF.class.getMethod("run",SimulationRunConfiguration3DOF.class,IntegrationStepAlignmentMode.class).getReturnType());
    }
    @Test void identityErrorsAreOrdinaryAndContextual() {
        // CFG24-T05: exact identity, not numeric validation or normalization.
        for(String id:Arrays.asList(null,""," ","\t\n")) {
            var e=assertThrowsExactly(IllegalArgumentException.class,()->new AttachedHardwareMass(id,1,0));
            assertTrue(e.getMessage().contains("componentId"));
        }
        assertTrue(assertThrowsExactly(IllegalArgumentException.class,()->new SingleStageVehicleRunConfiguration3DOF(null,List.of())).getMessage().contains("baseRun"));
        assertTrue(assertThrowsExactly(IllegalArgumentException.class,()->new SingleStageVehicleRunConfiguration3DOF(base,null)).getMessage().contains("attachedHardware"));
        var withNull=new ArrayList<>(HARDWARE); withNull.add(null);
        assertTrue(assertThrowsExactly(IllegalArgumentException.class,()->new SingleStageVehicleRunConfiguration3DOF(base,withNull)).getMessage().contains("[2]"));
        for(var duplicate:List.of(HARDWARE.get(0),new AttachedHardwareMass("avionics",9,-2))) {
            var e=assertThrowsExactly(IllegalArgumentException.class,()->new SingleStageVehicleRunConfiguration3DOF(base,List.of(HARDWARE.get(0),duplicate)));
            assertTrue(e.getMessage().contains("[1]")); assertTrue(e.getMessage().contains("componentId"));
        }
        var spaced=new AttachedHardwareMass(" avionics ",1,-1);
        var c=new SingleStageVehicleRunConfiguration3DOF(base,List.of(spaced,new AttachedHardwareMass("AVIONICS",2,2),HARDWARE.get(0)));
        assertEquals(" avionics ",c.attachedHardware().get(0).componentId());
        assertEquals(3,c.attachedHardware().size()); // Different names are not a physical-ownership proof.
    }
    @Test void immutableListSnapshotAndBaseReferenceBoundary() {
        // CFG24-T04/T13: only the hardware snapshot is copied, not an invented baseRun deep copy.
        var caller=new ArrayList<>(HARDWARE); var c=new SingleStageVehicleRunConfiguration3DOF(base,caller);
        assertSame(base,c.baseRun()); assertEquals(HARDWARE,c.attachedHardware());
        caller.clear(); caller.add(new AttachedHardwareMass("other",1,-3));
        assertEquals(HARDWARE,c.attachedHardware());
        assertThrows(UnsupportedOperationException.class,()->c.attachedHardware().clear());
        assertEquals(attached,new SimulationEngine3DOF().run(c));
    }
    @ParameterizedTest @ValueSource(doubles={0,-.1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void numericMassValidationBelongsToNat023(double mass) {
        // CFG24-T06: records accept numeric values; the authoritative physics mass call rejects them.
        var hardware=new AttachedHardwareMass("invalid",mass,Double.NaN);
        var configured=new SingleStageVehicleRunConfiguration3DOF(base,List.of(HARDWARE.get(0),hardware));
        massError("INVALID_AUXILIARY_MASS","auxiliary_masses_kg[1]",mass,()->new SimulationEngine3DOF().run(configured));
        var state=new InitialStateBuilder().build(base.launchConditions());
        massError("INVALID_AUXILIARY_MASS","auxiliary_masses_kg[1]",mass,()->new PhysicsEvaluator3DOF().evaluate(0,state,base.launchConditions(),base.physicsContext(),new double[]{.08,mass},new double[]{.4,Double.NaN}));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void numericCgValidationBelongsToNat023(double cg) {
        var configured=new SingleStageVehicleRunConfiguration3DOF(base,List.of(new AttachedHardwareMass("invalid",.1,cg)));
        massError("INVALID_AUXILIARY_CG","auxiliary_cg_x_geo_m[0]",cg,()->new SimulationEngine3DOF().run(configured));
        var state=new InitialStateBuilder().build(base.launchConditions());
        massError("INVALID_AUXILIARY_CG","auxiliary_cg_x_geo_m[0]",cg,()->new PhysicsEvaluator3DOF().evaluate(0,state,base.launchConditions(),base.physicsContext(),new double[]{.1},new double[]{cg}));
    }
    @Test void emptyInputsPreserveExactLegacyPhysicsAndExecution() {
        // CFG24-T07/T15: complete real J-011 history, not approximate headlines.
        assertEquals(legacy,empty); assertNotSame(legacy.recordedData(),empty.recordedData());
        var evaluator=new PhysicsEvaluator3DOF(); var s=PhysicsEvaluatorTest.state(70,3,4,5);
        var c=PhysicsEvaluatorTest.context(1200,2.5,new NoWindModel()); var l=PhysicsEvaluatorTest.launch(50);
        for(double t:new double[]{2.5,2.854,3.9,3.93}) {
            assertEquals(evaluator.evaluate(t,s,l,c),evaluator.evaluate(t,s,l,c,new double[0],new double[0]));
        }
    }
    @Test void controlledPointMassCgAndAccelerationUseTheirAcceptedAuthorities() {
        // CFG24-T08/T09: identical point, different total mass; no complete-trajectory inference.
        var c=PhysicsEvaluatorTest.context(1200,0,new ConstantWindModel(new double[]{1,-2,.5}));
        var s=PhysicsEvaluatorTest.state(70,3,4,5); var l=PhysicsEvaluatorTest.launch(50); var evaluator=new PhysicsEvaluator3DOF();
        double[] masses={.08,.12},cgs={.4,1};
        var old=evaluator.evaluate(.354,s,l,c); var r=evaluator.evaluate(.354,s,l,c,masses,cgs);
        assertEquals(new RocketMassPropertiesCalculator().evaluate(c.structuralMassProperties(),r.motorMassProperties(),masses,cgs),r.rocketMassProperties());
        assertEquals(old.motorThrustState(),r.motorThrustState()); assertEquals(old.motorMassProperties(),r.motorMassProperties());
        assertEquals(old.atmosphere(),r.atmosphere()); assertEquals(old.airProperties(),r.airProperties());
        assertEquals(old.relativeFlow(),r.relativeFlow()); assertEquals(old.flightConditions(),r.flightConditions()); assertEquals(old.basicDrag(),r.basicDrag());
        assertEquals(old.gravityAccelerationWorldMS2(),r.gravityAccelerationWorldMS2());
        assertEquals(old.dynamics().forces().thrustForceWorldN(),r.dynamics().forces().thrustForceWorldN());
        assertEquals(old.dynamics().forces().dragForceWorldN(),r.dynamics().forces().dragForceWorldN());
        for(int axis=0;axis<3;axis++) {
            double propulsive=r.dynamics().forces().thrustForceWorldN().get(axis)+r.dynamics().forces().dragForceWorldN().get(axis);
            double expected=propulsive/r.rocketMassProperties().totalMassKg()+r.gravityAccelerationWorldMS2().get(axis);
            // NAT-014 evaluated-acceleration contract already used by J-007/J-009.
            assertEquals(expected,r.dynamics().accelerationWorldMS2().get(axis),2e-15);
        }
        assertNotEquals(old.dynamics().accelerationWorldMS2(),r.dynamics().accelerationWorldMS2());
        var shifted=evaluator.evaluate(.354,s,l,c,masses,new double[]{-2,4});
        assertNotEquals(r.rocketMassProperties().totalCgXGeoM(),shifted.rocketMassProperties().totalCgXGeoM());
        assertEquals(r.dynamics(),shifted.dynamics()); // Current 3DOF has no moment/attitude coupling.
        masses[0]=100; cgs[0]=-100;
        assertEquals(r,evaluator.evaluate(.354,s,l,c,new double[]{.08,.12},new double[]{.4,1}));
    }
    static PhysicsEvaluationContext3DOF explicitMotorContext() {
        var c=PhysicsEvaluatorTest.context(1200,2.5,new NoWindModel());
        var installation=new MotorInstallationResolver().resolve(c.resolvedGeometry(),MotorPropertiesTest.sourceMotor());
        c=PhysicsEvaluatorTest.change(c,"motor_installation",installation);
        return PhysicsEvaluatorTest.change(c,"motor_property_model_profile",MotorPropertiesTest.explicit());
    }
    @Test void currentMotorMassAndCgReevaluatedAtEveryRk4StageAndEndpoint() {
        // CFG24-T10: real source curves, real RK4 and physics, independent single-step reconstruction.
        var c=explicitMotorContext(); var source=SimulationEngineTest.config(1,2.5,.1,5);
        var run=(SimulationRunConfiguration3DOF)VehicleTestData.replace(source,"physics_context",c);
        var actual=new SimulationEngine3DOF().run(new SingleStageVehicleRunConfiguration3DOF(run,HARDWARE));
        var first=actual.recordedData().samples().get(0); var last=actual.recordedData().samples().get(1);
        var states=new ArrayList<TranslationalState3DOF>(); var snapshots=new ArrayList<PhysicsEvaluationResult3DOF>(); var times=new ArrayList<Double>();
        var expected=new ClassicalRK4Integrator3DOF().step(first.point(),run.fixedStepConfig(),(t,s)->{
            states.add(s); times.add(t);
            var p=new PhysicsEvaluator3DOF().evaluate(t,s,run.launchConditions(),c,new double[]{.08,.12},new double[]{.4,1});
            snapshots.add(p); return p.dynamics().derivative();
        });
        assertEquals(List.of(2.5,2.5+.05,2.5+.05,2.5+.1),times); assertEquals(4,snapshots.size());
        assertEquals(expected,last.point()); assertNotEquals(states.get(3),last.point().state());
        for(var p:snapshots) {
            var currentMotor=new MotorPropertyEvaluator().evaluate(c.motorInstallation(),c.motorPropertyModelProfile(),p.motorTimeS());
            assertEquals(currentMotor,p.motorMassProperties());
            assertEquals(new RocketMassPropertiesCalculator().evaluate(c.structuralMassProperties(),currentMotor,new double[]{.08,.12},new double[]{.4,1}),p.rocketMassProperties());
        }
        assertNotEquals(snapshots.get(0).motorMassProperties().massKg(),snapshots.get(3).motorMassProperties().massKg());
        assertNotEquals(snapshots.get(0).motorMassProperties().cgXGeoM(),snapshots.get(3).motorMassProperties().cgXGeoM());
        for(var sample:actual.recordedData().samples()) {
            assertEquals(new PhysicsEvaluator3DOF().evaluate(sample.point().timeS(),sample.point().state(),run.launchConditions(),c,new double[]{.08,.12},new double[]{.4,1}),sample.physics());
        }
    }
    @Test void allAcceptedRecordedSamplesHaveCurrentMassCgAndPhysics() {
        // CFG24-T11: mass authority checked for every point, not display/recorder arithmetic.
        var c=base.physicsContext(); var evaluator=new PhysicsEvaluator3DOF();
        for(var sample:attached.recordedData().samples()) {
            var p=sample.physics();
            assertEquals(new MotorPropertyEvaluator().evaluate(c.motorInstallation(),c.motorPropertyModelProfile(),p.motorTimeS()),p.motorMassProperties());
            assertEquals(new RocketMassPropertiesCalculator().evaluate(c.structuralMassProperties(),p.motorMassProperties(),new double[]{.08,.12},new double[]{.4,1}),p.rocketMassProperties());
            assertEquals(evaluator.evaluate(sample.point().timeS(),sample.point().state(),base.launchConditions(),c,new double[]{.08,.12},new double[]{.4,1}),p);
        }
    }
    @Test void eventQueriesTerminationAndDurationRetainTheirMeaning() {
        // CFG24-T12: semantic equality, not equality of times between different masses.
        var r=new SimulationResult3DOF(attached);
        assertEquals(SimulationTerminationReason.TERMINAL_EVENT,r.terminationReason());
        assertEquals(List.of(FlightEventType.BURNOUT,FlightEventType.APOGEE,FlightEventType.GROUND),r.events().stream().map(FlightEventOccurrence3DOF::eventType).toList());
        for(var type:FlightEventType.values()) assertEquals(1,r.eventsOfType(type).size());
        assertEquals(1.43,r.burnoutEvent().timeS()); assertSame(r.groundEvent(),r.terminalEvent());
        assertEquals(r.terminalEvent().timeS(),r.terminationTimeS());
        assertEquals(r.terminationTimeS()-r.initialSample().point().timeS(),r.simulationDurationS());
        assertTrue(r.lastAcceptedSample().point().timeS()<r.terminationTimeS());
        assertTrue(r.terminalEvent().interpolationFraction()<1); assertTrue(r.lastAcceptedSample().point().state().positionWorldM().get(2)>=0);
        var limited=new SimulationResult3DOF(new SimulationEngine3DOF().run(new SingleStageVehicleRunConfiguration3DOF(SimulationEngineTest.config(2,2.5,.01,5),HARDWARE)));
        assertEquals(SimulationTerminationReason.MAXIMUM_STEPS_REACHED,limited.terminationReason());
        assertEquals(limited.lastAcceptedSample().point().timeS(),limited.terminationTimeS());
        assertEquals(limited.terminationTimeS()-2.5,limited.simulationDurationS());
        assertThrows(NoSuchMethodException.class,()->SimulationResult3DOF.class.getMethod("flightTimeS"));
    }
    @Test void independentConfigurationsAndFreshRunsRemainDeterministic() {
        // CFG24-T13/T14: existing base remains unchanged after different attached runs.
        assertEquals(attached,repeated); assertNotSame(attached.recordedData(),repeated.recordedData());
        assertNotEquals(attached,different); assertNotEquals(legacy,attached);
        assertEquals(legacy,new SimulationEngine3DOF().run(base));
        assertEquals(legacy,new SimulationEngine3DOF().run(new SingleStageVehicleRunConfiguration3DOF(base,List.of())));
        assertThrows(UnsupportedOperationException.class,()->attached.recordedData().samples().clear());
    }
    @Test void upstreamAndDerivedErrorsPropagateWithoutWrappingOrChangedOrdering() {
        var evaluator=new PhysicsEvaluator3DOF(); var s=PhysicsEvaluatorTest.state(50,0,0,0); var l=PhysicsEvaluatorTest.launch(50);
        var c=PhysicsEvaluatorTest.context(90000,0,new NoWindModel());
        assertThrowsExactly(AtmosphereDomainError.class,()->evaluator.evaluate(0,s,l,c,new double[]{Double.NaN},new double[]{0}));
        assertThrowsExactly(PhysicsEvaluationError.class,()->evaluator.evaluate(-1,s,l,base.physicsContext(),new double[]{Double.NaN},new double[]{0}));
        assertThrowsExactly(IllegalArgumentException.class,()->evaluator.evaluate(0,s,l,base.physicsContext(),null,new double[0]));
        assertThrowsExactly(IllegalArgumentException.class,()->evaluator.evaluate(0,s,l,base.physicsContext(),new double[1],new double[0]));
        var valid=PhysicsEvaluatorTest.context(1200,0,new NoWindModel());
        massError("INVALID_TOTAL_MASS","total_mass_kg",Double.POSITIVE_INFINITY,()->evaluator.evaluate(0,s,l,valid,new double[]{Double.MAX_VALUE,Double.MAX_VALUE},new double[]{0,0}));
        massError("INVALID_TOTAL_CG","total_cg_x_geo_m",Double.POSITIVE_INFINITY,()->evaluator.evaluate(0,s,l,valid,new double[]{2},new double[]{Double.MAX_VALUE}));
        var badRun=(SimulationRunConfiguration3DOF)VehicleTestData.replace(base,"physics_context.launch_environment_altitude_m",90000.);
        assertThrowsExactly(AtmosphereDomainError.class,()->new SimulationEngine3DOF().run(new SingleStageVehicleRunConfiguration3DOF(badRun,HARDWARE)));
    }
}
