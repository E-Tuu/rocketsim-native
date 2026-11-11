package org.roketsim.core;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;
import org.roketsim.core.numerics.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.simulation.*;
import static org.junit.jupiter.api.Assertions.*;

class LaunchGuideTest {
    static final String DATUM="synthetic-lower-usable-guide-end";
    static LaunchGuideRunConfiguration3DOF primary(SimulationRunConfiguration3DOF base,double top,double forward,double aft) {
        return new LaunchGuideRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),
            new LaunchGuideGeometry(DATUM,LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL,top,"explicit synthetic usable geometry"),
            new GuideAttachmentGeometry(DATUM,forward,aft,"explicit synthetic contact coordinates"),
            LaunchGuideConstraintPolicy.GEOMETRIC_TWO_POINT_GUIDANCE,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
    }
    static LaunchGuideRunConfiguration3DOF nominal(SimulationRunConfiguration3DOF base,double length) {
        return new LaunchGuideRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(base,List.of()),
            new LaunchGuideGeometry(DATUM,LaunchGuideGeometry.Kind.NOMINAL_ROD_INPUT,length,"explicit comparison input, not physical rail length"),null,
            LaunchGuideConstraintPolicy.OPENROCKET_NOMINAL_ROD_COMPAT,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
    }
    static SimulationRunConfiguration3DOF pulse(double peak,double h,int steps) {
        return MotorStepBoundariesTest.configuration(MotorStepBoundariesTest.triangle(.1,.2,peak),0,h,steps);
    }
    static SimulationRunConfiguration3DOF stationary(SimulationRunConfiguration3DOF c) {
        var l=c.launchConditions();
        return (SimulationRunConfiguration3DOF)VehicleTestData.replace(c,"launch_conditions",
            new LaunchConditions3DOF(l.initialPositionWorldM(),new double[]{0,0,0},l.launchDirectionWorldUnit()));
    }
    static LaunchGuideRunResult3DOF run(LaunchGuideRunConfiguration3DOF c) { return new LaunchGuideSimulator3DOF().run(c); }
    static void close(double expected,double actual) {
        assertEquals(expected,actual,3e-14*Math.max(1,Math.abs(expected))); // Existing GeometryResolverTest convention.
    }
    static LaunchGuideTransitionResult event(LaunchGuideRunResult3DOF r,LaunchGuideTransitionResult.Kind kind) {
        return r.transitions().stream().filter(e->e.kind()==kind).findFirst().orElseThrow();
    }

    @Test void insufficientThrustHoldsPadWithoutNegativeTravelOrInfiniteRun() {
        var c=primary(stationary(pulse(3,.05,60)),2,.5,.02); var r=run(c);
        assertEquals(LaunchGuideRunResult3DOF.Outcome.NO_LIFTOFF_STEP_LIMIT,r.outcome());
        assertEquals(60,r.stepsPerformed()); assertEquals(61,r.recordedData().samples().size());
        assertTrue(r.transitions().isEmpty()); assertTrue(r.twoPointGuidanceEndSpeedMS().isEmpty());
        for(var e:r.guideSamples()) {
            assertEquals(c.vehicle().baseRun().launchConditions().initialPositionWorldM(),e.point().state().positionWorldM());
            assertEquals(0,Vectors.magnitude(e.point().state().velocityWorldMS())); assertEquals(0,e.guideTravelM());
            assertEquals(FlightPhase3DOF.PAD_CONTACT,e.clock().flightPhase());
            assertEquals(0,Vectors.magnitude(e.appliedDerivative().velocityDerivativeWorldMS2()));
        }
        assertTrue(r.guideSamples().stream().anyMatch(e->e.clock().propulsionStatus()==PropulsionStatus3DOF.POST_BURNOUT));
        assertEquals(1,r.recordedData().events().stream().filter(e->e.eventType()==FlightEventType.BURNOUT).count());
    }
    @Test void forceCrossingInsideStepLocalizesLiftoffAtUnchangedPadState() {
        var c=primary(stationary(pulse(20,.2,250)),10,.5,.02); var r=run(c);
        var lift=event(r,LaunchGuideTransitionResult.Kind.LIFTOFF); var base=c.vehicle().baseRun();
        assertTrue(lift.acceptedPoint().timeS()>0 && lift.acceptedPoint().timeS()<.1);
        assertEquals(base.launchConditions().initialPositionWorldM(),lift.acceptedPoint().state().positionWorldM());
        assertEquals(0,Vectors.magnitude(lift.acceptedPoint().state().velocityWorldMS()));
        var evaluator=new PhysicsEvaluator3DOF(); var initial=new InitialStateBuilder().build(base.launchConditions());
        assertTrue(Vectors.dot(evaluator.evaluate(lift.acceptedPoint().timeS(),initial,base.launchConditions(),base.physicsContext()).dynamics().forces().netForceWorldN(),
            base.launchConditions().launchDirectionWorldUnit())>0);
        assertTrue(Vectors.dot(evaluator.evaluate(lift.lowerTimeS(),initial,base.launchConditions(),base.physicsContext()).dynamics().forces().netForceWorldN(),
            base.launchConditions().launchDirectionWorldUnit())<=0);
        assertTrue(lift.upperTimeS()-lift.lowerTimeS()<=2*Math.ulp(lift.upperTimeS()));
    }
    @Test void recontactTerminatesAtPreimpactStateWithoutPenetrationOrLanding() {
        var c=primary(stationary(pulse(20,.02,1000)),10,.5,.02); var r=run(c);
        assertEquals(LaunchGuideRunResult3DOF.Outcome.IMPACT_DYNAMICS_UNSUPPORTED,r.outcome());
        var impact=event(r,LaunchGuideTransitionResult.Kind.PAD_RECONTACT);
        var lift=event(r,LaunchGuideTransitionResult.Kind.LIFTOFF);
        assertTrue(impact.acceptedPoint().timeS()>lift.acceptedPoint().timeS());
        assertTrue(LaunchGuideEvaluation.axialVelocity(c.vehicle().baseRun().launchConditions(),impact.acceptedPoint().state())<0);
        assertEquals(r.recordedData().samples().get(r.recordedData().samples().size()-1).point(),impact.acceptedPoint());
        assertEquals(LaunchGuideTransitionResult.AcceptedSide.LAST_NONPENETRATING_RK4,impact.acceptedSide());
        for(var e:r.guideSamples()) {assertTrue(e.guideTravelM()>=0); assertTrue(e.point().state().positionWorldM().get(2)>=50);}
        close(0,LaunchGuideEvaluation.travel(c.vehicle().baseRun().launchConditions(),impact.acceptedPoint().state()));
        assertTrue(impact.lowerTimeS()<=impact.acceptedPoint().timeS() && impact.acceptedPoint().timeS()<=impact.upperTimeS());
        assertTrue(r.freeTerminationReason().isEmpty()); assertTrue(r.guidanceEnd().isEmpty());
        assertFalse(r.recordedData().events().stream().anyMatch(e->e.eventType()==FlightEventType.GROUND));
        assertEquals(FlightPhase3DOF.GUIDED_FLIGHT,r.guideSamples().get(r.guideSamples().size()-1).clock().flightPhase());
    }
    @Test void inclinedArbitraryEnuGuideCancelsLateralWindMotionAndUsesCurrentMass() {
        var b=NativeDemoTrajectoryTest.configuration(.02,1500);
        var launch=new LaunchConditions3DOF(new double[]{100,-200,50},new double[]{0,0,0},new double[]{.36,.48,.8});
        b=(SimulationRunConfiguration3DOF)VehicleTestData.replace(b,"launch_conditions",launch);
        b=(SimulationRunConfiguration3DOF)VehicleTestData.replace(b,"physics_context.wind_model",new ConstantWindModel(new double[]{8,-4,0}));
        var c=primary(b,1.5,.5,.02); var r=run(c); assertTrue(r.guidanceEnd().isPresent());
        for(var e:r.guideSamples()) {
            assertEquals(new PhysicsEvaluator3DOF().evaluate(e.point().timeS(),e.point().state(),launch,b.physicsContext()),e.unconstrainedPhysics());
            if(e.clock().flightPhase()==FlightPhase3DOF.GUIDED_FLIGHT) {
                var u=launch.launchDirectionWorldUnit(); var s=e.point().state();
                assertTrue(Vectors.magnitude(Vectors.cross3(s.velocityWorldMS(),u))<=3e-14*Math.max(1,Vectors.magnitude(s.velocityWorldMS())));
                for(int i=0;i<3;i++) close(launch.initialPositionWorldM().get(i)+e.guideTravelM()*u.get(i),s.positionWorldM().get(i));
                var force=e.unconstrainedPhysics().dynamics().forces().netForceWorldN();double mass=e.unconstrainedPhysics().rocketMassProperties().totalMassKg();
                for(int i=0;i<3;i++) close(mass*e.appliedDerivative().velocityDerivativeWorldMS2().get(i),force.get(i)+e.netConstraintReactionWorldN().get(i));
            }
        }
        var first=r.guideSamples().get(0).unconstrainedPhysics().motorMassProperties();
        var end=r.guidanceEnd().orElseThrow();
        var endPhysics=r.recordedData().samples().stream().filter(s->s.point().timeS()==end.acceptedPoint().timeS()).findFirst().orElseThrow().physics();
        assertTrue(endPhysics.motorMassProperties().massKg()<first.massKg());
    }
    @Test void primaryThresholdsAndFreeRestartAreDistinctContinuousAndDeterministic() {
        var b=NativeDemoTrajectoryTest.configuration(.02,2000);var c=primary(b,1.5,.5,.02);
        var r=run(c); assertEquals(r,run(c));
        var exit=event(r,LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END);
        close(1,LaunchGuideEvaluation.travel(b.launchConditions(),exit.acceptedPoint().state()));
        assertEquals(1.48,r.lastContactGeometricThresholdM().orElseThrow());
        assertEquals(1,r.transitions().stream().filter(t->t.kind()==LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END).count());
        assertEquals("APPROXIMATE_AFTER_TWO_POINT_EXIT",r.continuationClassification().orElseThrow());
        assertTrue(r.openRocketNominalRodExitSpeedMS().isEmpty());
        var samples=r.recordedData().samples();
        var boundary=samples.stream().filter(s->s.point().timeS()==exit.acceptedPoint().timeS()).findFirst().orElseThrow();
        assertSame(exit.acceptedPoint(),boundary.point());
        int index=samples.indexOf(boundary); assertTrue(index>0 && index<samples.size()-1);
        assertTrue(samples.get(index-1).point().timeS()<=exit.lowerTimeS());
        assertTrue(exit.acceptedPoint().timeS()<=exit.upperTimeS());
        var start=boundary.point(); var next=samples.get(index+1).point();
        double nominal=b.fixedStepConfig().stepSizeS();
        double h=start.timeS()+nominal==next.timeS()?nominal:next.timeS()-start.timeS();
        var direct=new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(h),
            (t,s)->new PhysicsEvaluator3DOF().evaluate(t,s,b.launchConditions(),b.physicsContext()).dynamics().derivative());
        assertEquals(next,direct); // Accepted state restart, no impulse, no estimated event-state substitution.
        assertEquals(LaunchGuideRunResult3DOF.Outcome.FREE_FLIGHT_TERMINATED,r.outcome());
        assertEquals(SimulationTerminationReason.TERMINAL_EVENT,r.freeTerminationReason().orElseThrow());
        assertEquals(1,r.recordedData().events().stream().filter(e->e.eventType()==FlightEventType.GROUND).count());
    }
    @Test void nominalSixMetreRunIsIndependentAndNotPhysicalTwoButtonClearance() {
        var b=NativeDemoTrajectoryTest.configuration(.02,2000);
        var a=run(primary(b,1.5,.5,.02));var config=nominal(b,6);var n=run(config);
        assertEquals(6,config.guidanceEndTravelM());
        var exit=event(n,LaunchGuideTransitionResult.Kind.NOMINAL_ROD_EXIT);
        close(6,LaunchGuideEvaluation.travel(b.launchConditions(),exit.acceptedPoint().state()));
        assertTrue(n.openRocketNominalRodExitSpeedMS().isPresent());assertTrue(n.twoPointGuidanceEndSpeedMS().isEmpty());
        assertTrue(n.lastContactGeometricThresholdM().isEmpty());
        assertNotEquals(a.guidanceEnd().orElseThrow().acceptedPoint().timeS(),exit.acceptedPoint().timeS());
        assertNotSame(a.recordedData(),n.recordedData());assertEquals(a,run(primary(b,1.5,.5,.02)));
        assertEquals(LaunchGuideGeometry.Kind.NOMINAL_ROD_INPUT,config.guide().kind());
    }
    @Test void missingGeometryIsNotEvaluableAndUnsupportedCombinationsAreExplicit() {
        var b=NativeDemoTrajectoryTest.configuration(.02,20);var c=primary(b,1.5,.5,.02);
        for(var unknown:List.of(new LaunchGuideRunConfiguration3DOF(c.vehicle(),null,c.attachments(),c.policy(),c.alignmentMode()),
                new LaunchGuideRunConfiguration3DOF(c.vehicle(),c.guide(),null,c.policy(),c.alignmentMode()))) {
            var r=run(unknown);assertEquals(LaunchGuideRunResult3DOF.Outcome.NOT_EVALUABLE,r.outcome());assertTrue(r.recordedData().samples().isEmpty());
        }
        assertEquals("UNSUPPORTED_GUIDE_ALIGNMENT_COMBINATION",assertThrows(LaunchGuideError.class,()->run(
            new LaunchGuideRunConfiguration3DOF(c.vehicle(),c.guide(),c.attachments(),c.policy(),IntegrationStepAlignmentMode.LEGACY_FIXED_STEP))).errorCode());
        for(var kind:List.of(LaunchGuideGeometry.Kind.LAUNCH_ROD_RESERVED,LaunchGuideGeometry.Kind.LAUNCH_TOWER_RESERVED,LaunchGuideGeometry.Kind.NOMINAL_ROD_INPUT))
            assertEquals("UNSUPPORTED_GUIDE_KIND",assertThrows(LaunchGuideError.class,()->run(new LaunchGuideRunConfiguration3DOF(c.vehicle(),
                new LaunchGuideGeometry(DATUM,kind,6,"reserved or nominal fixture"),c.attachments(),c.policy(),c.alignmentMode()))).errorCode());
        assertEquals("GUIDE_DATUM_MISMATCH",assertThrows(LaunchGuideError.class,()->run(new LaunchGuideRunConfiguration3DOF(c.vehicle(),c.guide(),
            new GuideAttachmentGeometry("other datum",.5,.02,"source"),c.policy(),c.alignmentMode()))).errorCode());
    }
    @Test void invalidGeometryNullsAndNonfiniteInputsRejectedWithoutDefaults() {
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(LaunchGuideError.class,()->new LaunchGuideGeometry(DATUM,LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL,invalid,"source"));
        assertThrows(LaunchGuideError.class,()->new GuideAttachmentGeometry(DATUM,.5,.5,"source"));
        assertThrows(LaunchGuideError.class,()->new GuideAttachmentGeometry(DATUM,.5,-.1,"source"));
        assertThrows(LaunchGuideError.class,()->new GuideAttachmentGeometry(DATUM,.5,Double.NaN,"source"));
        assertThrows(LaunchGuideError.class,()->new LaunchGuideGeometry("",LaunchGuideGeometry.Kind.IDEAL_TWO_BUTTON_RAIL,1,"source"));
        var b=NativeDemoTrajectoryTest.configuration(.02,20);assertThrows(LaunchGuideError.class,()->run(primary(b,.5,.5,.02)));
        assertEquals("UNSUPPORTED_PAD_INITIAL_VELOCITY",assertThrows(LaunchGuideError.class,()->run(primary(SimulationEngineTest.config(1,0,.1,1),1.5,.5,.02))).errorCode());
        assertThrows(LaunchGuideError.class,()->new LaunchGuideSimulator3DOF().run(null));
    }
    @Test void explicitHardwareMassAndCurrentMotorSnapshotAreUsedAtEveryAcceptedPoint() {
        var b=NativeDemoTrajectoryTest.configuration(.02,1500);var empty=primary(b,1.5,.5,.02);
        var hardware=List.of(new AttachedHardwareMass("analytic battery",.08,.4));
        var configured=new LaunchGuideRunConfiguration3DOF(new SingleStageVehicleRunConfiguration3DOF(b,hardware),empty.guide(),empty.attachments(),empty.policy(),empty.alignmentMode());
        var r=run(configured);
        for(var e:r.guideSamples()) {
            var direct=new PhysicsEvaluator3DOF().evaluate(e.point().timeS(),e.point().state(),b.launchConditions(),b.physicsContext(),new double[]{.08},new double[]{.4});
            assertEquals(direct,e.unconstrainedPhysics());
        }
        assertTrue(r.twoPointGuidanceEndSpeedMS().isPresent());
    }

    static final PhysicsEvaluationResult3DOF SEED=PhysicsEvaluatorTest.evaluate(
        PhysicsEvaluatorTest.context(1200,0,new NoWindModel()),.5,50,0,0,0);
    static PhysicsEvaluationResult3DOF synthetic(LaunchConditions3DOF launch,TranslationalState3DOF state,double mass,double thrust,Vector gravity) {
        var inputs=new TranslationalDynamicsInputs(mass,thrust,new double[]{0,0,0},0,1,0,gravity);
        var dynamics=new TranslationalDynamicsEvaluator().evaluate(state,launch,inputs,TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE);
        return new PhysicsEvaluationResult3DOF(SEED.environmentAltitudeM(),SEED.motorTimeS(),SEED.atmosphere(),SEED.airProperties(),
            SEED.airMassVelocityWorldMS(),SEED.relativeFlow(),SEED.flightConditions(),new MotorThrustState(thrust,0),SEED.motorMassProperties(),
            new RocketMassProperties(mass,.5),SEED.basicDrag(),gravity,dynamics);
    }
    static LaunchGuideEvaluation syntheticEvaluation(double t,TranslationalState3DOF state,LaunchConditions3DOF launch,double thrust,FlightPhase3DOF phase) {
        return LaunchGuideEvaluation.evaluate(new IntegrationPoint3DOF(t,state),launch,synthetic(launch,state,2,thrust,Vector.of(new double[]{0,0,-10})),
            new FlightPhaseClockEvaluator3DOF().evaluate(t,phase,new PropulsionTimeline(0),FlightEventsTest.stats(100)));
    }
    @Test void instantaneousProjectionReactionAndInclinedGravityAreIndependentAnalyticChecks() {
        var launch=new LaunchConditions3DOF(new double[]{10,20,50},new double[]{0,0,0},new double[]{.6,0,.8});
        var state=new InitialStateBuilder().build(launch);
        var guide=syntheticEvaluation(0,state,launch,20,FlightPhase3DOF.GUIDED_FLIGHT);
        close(1.2,guide.appliedDerivative().velocityDerivativeWorldMS2().get(0));close(1.6,guide.appliedDerivative().velocityDerivativeWorldMS2().get(2));
        close(-9.6,guide.netConstraintReactionWorldN().get(0));close(7.2,guide.netConstraintReactionWorldN().get(2));
        var pad=syntheticEvaluation(0,state,launch,0,FlightPhase3DOF.PAD_CONTACT);
        close(-16,pad.axialAppliedForceN());assertEquals(0,Vectors.magnitude(pad.appliedDerivative().velocityDerivativeWorldMS2()));
        close(20,pad.netConstraintReactionWorldN().get(2));
    }
    static LaunchGuideEvaluation projection(LaunchConditions3DOF launch,Vector acceleration) {
        var state=new InitialStateBuilder().build(launch);
        return LaunchGuideEvaluation.evaluate(new IntegrationPoint3DOF(0,state),launch,
            synthetic(launch,state,2,0,acceleration),new FlightPhaseClockEvaluator3DOF().evaluate(
                0,FlightPhase3DOF.GUIDED_FLIGHT,new PropulsionTimeline(0),FlightEventsTest.stats(100)));
    }
    static void checkNearUnitProjection(double scale,boolean oblique) {
        var direction=oblique?Vector.of(new double[]{.6*scale,0,.8*scale}):Vector.of(new double[]{0,0,scale});
        var launch=new LaunchConditions3DOF(new double[]{10,20,50},new double[]{0,0,0},direction);
        var first=projection(launch,Vector.of(new double[]{0,0,5}));
        var projected=first.appliedDerivative().velocityDerivativeWorldMS2();
        // Independent geometric result: scaling a direction does not change its projection axis.
        double[] expected=oblique?new double[]{2.4,0,3.2}:new double[]{0,0,5};
        var twice=projection(launch,projected).appliedDerivative().velocityDerivativeWorldMS2();
        for(int i=0;i<3;i++) { close(expected[i],projected.get(i));close(projected.get(i),twice.get(i)); }
        close(0,Vectors.dot(first.netConstraintReactionWorldN(),direction));
        assertEquals(direction,launch.launchDirectionWorldUnit()); // No normalization or replacement.
    }
    @Test void aboveUnitDirectionHasIdempotentProjectionAndTransverseReaction() {
        checkNearUnitProjection(1+5e-9,false);
    }
    @Test void belowUnitDirectionHasIdempotentProjectionAndTransverseReaction() {
        checkNearUnitProjection(1-5e-9,false);
    }
    @Test void obliqueNearUnitDirectionsHaveIdempotentProjectionAndTransverseReaction() {
        checkNearUnitProjection(1+5e-9,true);checkNearUnitProjection(1-5e-9,true);
    }
    @Test void exactUnitProjectionRetainsExactAxialAccelerationAndZeroReaction() {
        var launch=new LaunchConditions3DOF(new double[]{10,20,50},new double[]{0,0,0},new double[]{0,0,1});
        var result=projection(launch,Vector.of(new double[]{0,0,5}));
        assertEquals(Vector.of(new double[]{0,0,5}),result.appliedDerivative().velocityDerivativeWorldMS2());
        assertEquals(Vector.of(new double[]{0,0,0}),result.netConstraintReactionWorldN());
    }
    @Test void constantAccelerationEventMatchesFrozenAnalyticReferenceAtSeveralSteps() {
        var launch=PhysicsEvaluatorTest.launch(50);
        for(double h:new double[]{.5,.25,.125,.0625}) {
            var start=new IntegrationPoint3DOF(0,new InitialStateBuilder().build(launch));
            DerivativeFunction3DOF derivative=(t,s)->syntheticEvaluation(t,s,launch,30,FlightPhase3DOF.GUIDED_FLIGHT).appliedDerivative();
            while(LaunchGuideEvaluation.travel(launch,new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(h),derivative).state())<1)
                start=new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(h),derivative);
            var event=new LaunchGuideBoundaryLocalizer().guidanceEnd(start,h,launch,1,derivative,LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END);
            double time=event.acceptedPoint().timeS(),speed=Vectors.magnitude(event.acceptedPoint().state().velocityWorldMS());
            close(Math.sqrt(2./5),time);close(Math.sqrt(10),speed);close(1,LaunchGuideEvaluation.travel(launch,event.acceptedPoint().state()));
            assertTrue(event.timeQuantizationBoundS()<=2*Math.ulp(time));
            System.out.println("constant a=5 h="+h+" time="+time+" speed="+speed+" bound="+event.timeQuantizationBoundS());
        }
    }
    @Test void nonlinearAnalyticReferenceDemonstratesRefinementConvergence() {
        var launch=PhysicsEvaluatorTest.launch(0);double previous=Double.POSITIVE_INFINITY;
        // a=5+t^4: s=2.5*t^2+t^6/30, v=5*t+t^5/5. Target s(1)=76/30.
        for(double h:new double[]{.4,.2,.1,.05}) {
            DerivativeFunction3DOF derivative=(t,s)->syntheticEvaluation(t,s,launch,30+2*Math.pow(t,4),FlightPhase3DOF.GUIDED_FLIGHT).appliedDerivative();
            var start=new IntegrationPoint3DOF(0,new InitialStateBuilder().build(launch));
            while(LaunchGuideEvaluation.travel(launch,new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(h),derivative).state())<76./30)
                start=new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(h),derivative);
            var e=new LaunchGuideBoundaryLocalizer().guidanceEnd(start,h,launch,76./30,derivative,LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END);
            double error=Math.abs(e.acceptedPoint().timeS()-1);
            assertTrue(error<previous/4); previous=error;
            System.out.println("a=5+t^4 h="+h+" time="+e.acceptedPoint().timeS()+" timeError="+error+" speed="+Vectors.magnitude(e.acceptedPoint().state().velocityWorldMS()));
        }
        assertTrue(previous<1e-7); // Existing RK4 allclose convention, local V&V only.
    }
    @Test void angleAdapterAndCompetitionDiagnosticsUseExplicitEvidenceAndNamedMetrics() {
        var u=LaunchGuideGeometry.directionFromAngles(85,90);
        close(Math.cos(Math.toRadians(85)),u.get(0));close(0,u.get(1));close(Math.sin(Math.toRadians(85)),u.get(2));
        var base=NativeDemoTrajectoryTest.configuration(.02,1500);
        base=(SimulationRunConfiguration3DOF)VehicleTestData.replace(base,"launch_conditions",new LaunchConditions3DOF(new double[]{0,0,0},new double[]{0,0,0},u));
        var c=primary(base,1.5,.5,.02);var result=run(c);var checker=new Teknofest2026LaunchDiagnostics();
        var checks=checker.evaluate(Teknofest2026LaunchDiagnostics.Category.A1,Teknofest2026LaunchDiagnostics.Metric.TWO_POINT_GUIDANCE_END,c,result,
            Optional.of(new Teknofest2026LaunchDiagnostics.PlacementEvidence(1,"explicit synthetic nose-to-guide transformation")),
            Optional.of(new Teknofest2026LaunchDiagnostics.OrientationEvidence(85,90,"declared orientation fixture")));
        for(String rule:List.of("4.5.15","BUTTON_ORDER","4.5.16","4.5.19","4.1.35"))
            assertEquals(Teknofest2026LaunchDiagnostics.Status.PASS,checks.stream().filter(ch->ch.rule().equals(rule)).findFirst().orElseThrow().status());
        var missing=checker.evaluate(Teknofest2026LaunchDiagnostics.Category.A1,Teknofest2026LaunchDiagnostics.Metric.OPENROCKET_NOMINAL_ROD_EXIT,c,result,Optional.empty(),Optional.empty());
        for(String rule:List.of("4.5.16","4.5.19","4.1.35","4.4.8"))
            assertEquals(Teknofest2026LaunchDiagnostics.Status.NOT_EVALUABLE,missing.stream().filter(ch->ch.rule().equals(rule)).findFirst().orElseThrow().status());
        double[] limits={15,25,30,25,20,20,20};int index=0;
        for(var category:Teknofest2026LaunchDiagnostics.Category.values()) assertEquals(limits[index++],Teknofest2026LaunchDiagnostics.minimumSpeedMS(category));
        assertThrows(UnsupportedOperationException.class,()->checks.clear());
    }
    @Test void legacyAndAlignedPathsRemainExactlyTheExistingEntrypoints() {
        var c=NativeDemoTrajectoryTest.configuration(.01,2000); var engine=new SimulationEngine3DOF();
        var before=engine.run(c);run(primary(c,1.5,.5,.02));
        assertEquals(before,engine.run(c));assertEquals(before,engine.run(c,IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
        assertEquals(3,FlightEventType.values().length);
        assertEquals(14,PhysicsEvaluationContext3DOF.class.getRecordComponents().length);
        assertEquals(7,SimulationRunConfiguration3DOF.class.getRecordComponents().length);
        assertEquals(0,LaunchGuideSimulator3DOF.class.getDeclaredFields().length);
    }
    @Test void padForceMaximumBetweenNegativeEndpointAndMidpointSamplesIsNotMissed() {
        var base=stationary(pulse(20,1,300));
        double loaded=base.physicsContext().structuralMassProperties().structureMassKg()+1;
        double g=-base.physicsContext().gravityModel().evaluate().get(2);
        double peak=g*loaded-.001;
        var motor=MotorCatalogs.AEROTECH_F50_4T;
        motor=(MotorDefinition)VehicleTestData.replace(motor,"initial_mass_kg",1.);
        motor=(MotorDefinition)VehicleTestData.replace(motor,"propellant_mass_kg",.8);
        motor=(MotorDefinition)VehicleTestData.replace(motor,"thrust_curve",List.of(
            new ThrustSample(0,0),new ThrustSample(.000001,peak),new ThrustSample(1,0)));
        var b=stationary(MotorStepBoundariesTest.configuration(motor,0,1,300));
        var physics=new PhysicsEvaluator3DOF();var state=new InitialStateBuilder().build(b.launchConditions());
        for(double t:new double[]{.000001,.5000005,1})
            assertTrue(physics.evaluate(t,state,b.launchConditions(),b.physicsContext()).dynamics().forces().netForceWorldN().get(2)<0);
        assertTrue(physics.evaluate(.03,state,b.launchConditions(),b.physicsContext()).dynamics().forces().netForceWorldN().get(2)>0);
        var r=run(primary(b,100,.5,.02));var lift=event(r,LaunchGuideTransitionResult.Kind.LIFTOFF);
        assertTrue(lift.acceptedPoint().timeS()>.000001 && lift.acceptedPoint().timeS()<.03);
    }
    @Test void shiftedIgnitionAndAcceptedNearUnitVectorArePreservedWithoutRepair() {
        var motor=MotorStepBoundariesTest.triangle(.125,.5,20);
        var base=stationary(MotorStepBoundariesTest.configuration(motor,-2,.0625,40));
        var r=run(primary(base,100,.5,.02));
        assertEquals(-2,r.recordedData().samples().get(0).point().timeS());
        for(var e:r.guideSamples()) {
            assertEquals(e.point().timeS()+2,e.clock().motorTimeS());assertTrue(e.clock().motorTimeS()>=0);
        }
        double near=1+5e-9; var nativeBase=NativeDemoTrajectoryTest.configuration(.01,50);
        var l=new LaunchConditions3DOF(new double[]{10,20,50},new double[]{0,0,0},new double[]{0,0,near});
        nativeBase=(SimulationRunConfiguration3DOF)VehicleTestData.replace(nativeBase,"launch_conditions",l);
        run(primary(nativeBase,100,.5,.02));assertEquals(near,l.launchDirectionWorldUnit().get(2));
        var comparison=run(nominal(nativeBase,1));
        close(1,LaunchGuideEvaluation.displacementMagnitude(l,comparison.guidanceEnd().orElseThrow().acceptedPoint().state()));
    }
    @Test void stepLimitBeforeExitAndResultImmutabilityNeverFabricateAMetric() {
        var r=run(primary(NativeDemoTrajectoryTest.configuration(.01,10),1000,.5,.02));
        assertEquals(10,r.stepsPerformed());assertEquals(LaunchGuideRunResult3DOF.Outcome.GUIDED_STEP_LIMIT,r.outcome());
        assertTrue(r.guidanceEnd().isEmpty());assertTrue(r.twoPointGuidanceEndSpeedMS().isEmpty());
        assertTrue(r.openRocketNominalRodExitSpeedMS().isEmpty());assertTrue(r.continuationClassification().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->r.guideSamples().clear());
        assertThrows(UnsupportedOperationException.class,()->r.transitions().clear());
        assertThrows(UnsupportedOperationException.class,()->r.recordedData().samples().clear());
        var input=new ArrayList<>(r.guideSamples());
        var snapshot=new LaunchGuideRunResult3DOF(r.policy(),r.outcome(),r.recordedData(),input,r.transitions(),r.stepsPerformed(),r.lastContactGeometricThresholdM(),r.freeTerminationReason());
        input.clear();assertEquals(r,snapshot);
    }
    @Test void guidedRk4QueriesCurrentMotorMassAtFourStagesAndAcceptedEndpointSeparately() {
        var base=NativeDemoTrajectoryTest.configuration(.05,10);var l=base.launchConditions();
        var start=new IntegrationPoint3DOF(.1,new TranslationalState3DOF(new double[]{0,0,.1},new double[]{0,0,2}));
        var times=new ArrayList<Double>();var masses=new ArrayList<Double>();var states=new ArrayList<TranslationalState3DOF>();
        var result=new ClassicalRK4Integrator3DOF().step(start,new FixedStepConfig(.05),(t,s)-> {
            times.add(t);states.add(s);var p=new PhysicsEvaluator3DOF().evaluate(t,s,l,base.physicsContext());masses.add(p.motorMassProperties().massKg());
            return LaunchGuideEvaluation.evaluate(new IntegrationPoint3DOF(t,s),l,p,
                new FlightPhaseClockEvaluator3DOF().evaluate(t,FlightPhase3DOF.GUIDED_FLIGHT,base.physicsContext().propulsionTimeline(),base.motorCurveStatistics())).appliedDerivative();
        });
        assertEquals(List.of(.1,.1+.025,.1+.025,.1+.05),times);
        for(int i=0;i<times.size();i++) assertEquals(new MotorPropertyEvaluator().evaluate(base.physicsContext().motorInstallation(),base.physicsContext().motorPropertyModelProfile(),times.get(i)).massKg(),masses.get(i));
        assertNotEquals(masses.get(0),masses.get(3));assertNotEquals(states.get(3),result.state());
        var endpoint=new PhysicsEvaluator3DOF().evaluate(result.timeS(),result.state(),l,base.physicsContext());
        assertEquals(result.timeS(),endpoint.motorTimeS());
    }
    @Test void competitionSpeedChecksHaveExplicitMetricAndExactSourceThresholds() {
        var checks=new Teknofest2026LaunchDiagnostics();var metric=Teknofest2026LaunchDiagnostics.Metric.OPENROCKET_NOMINAL_ROD_EXIT;
        for(var category:Teknofest2026LaunchDiagnostics.Category.values()) {
            double threshold=Teknofest2026LaunchDiagnostics.minimumSpeedMS(category);
            assertEquals(Teknofest2026LaunchDiagnostics.Status.PASS,checks.minimumExitSpeed(category,metric,OptionalDouble.of(threshold)).status());
            assertEquals(Teknofest2026LaunchDiagnostics.Status.FAIL,checks.minimumExitSpeed(category,metric,OptionalDouble.of(Math.nextDown(threshold))).status());
            assertEquals(Teknofest2026LaunchDiagnostics.Status.NOT_EVALUABLE,checks.minimumExitSpeed(category,metric,OptionalDouble.empty()).status());
            assertEquals(metric.name(),checks.minimumExitSpeed(category,metric,OptionalDouble.of(threshold)).metric());
        }
        assertThrows(LaunchGuideError.class,()->checks.minimumExitSpeed(Teknofest2026LaunchDiagnostics.Category.A1,metric,OptionalDouble.of(Double.NaN)));
        var c=primary(NativeDemoTrajectoryTest.configuration(.02,20),100,.5,.02);var r=run(c);
        var declared=new Teknofest2026LaunchDiagnostics.OrientationEvidence(90,0,"explicit alternative orientation");
        var reported=checks.evaluate(Teknofest2026LaunchDiagnostics.Category.A1,metric,c,r,Optional.empty(),Optional.of(declared));
        // A declaration that does not exactly match the authoritative direction is not repaired.
        assertEquals(Teknofest2026LaunchDiagnostics.Status.NOT_EVALUABLE,reported.stream().filter(ch->ch.rule().equals("4.1.35")).findFirst().orElseThrow().status());
    }
    @Test void invalidBracketsUnsupportedDirectionsAndUpstreamErrorsAreNotHidden() {
        var l=PhysicsEvaluatorTest.launch(50);var start=new IntegrationPoint3DOF(0,new InitialStateBuilder().build(l));
        DerivativeFunction3DOF rhs=(t,s)->syntheticEvaluation(t,s,l,30,FlightPhase3DOF.GUIDED_FLIGHT).appliedDerivative();
        assertEquals("GUIDE_EVENT_NOT_BRACKETED",assertThrows(LaunchGuideError.class,()->new LaunchGuideBoundaryLocalizer().guidanceEnd(start,.1,l,100,rhs,LaunchGuideTransitionResult.Kind.TWO_POINT_GUIDANCE_END)).errorCode());
        assertThrows(LaunchGuideError.class,()->new LaunchGuideBoundaryLocalizer().guidanceEnd(start,.1,l,1,rhs,LaunchGuideTransitionResult.Kind.LIFTOFF));
        assertThrows(LaunchGuideError.class,()->LaunchGuideGeometry.directionFromAngles(0,0));
        assertThrows(LaunchGuideError.class,()->LaunchGuideGeometry.directionFromAngles(85,Double.POSITIVE_INFINITY));
        var b=NativeDemoTrajectoryTest.configuration(.02,20);
        b=(SimulationRunConfiguration3DOF)VehicleTestData.replace(b,"physics_context.launch_environment_altitude_m",90000.);
        var invalid=primary(b,1.5,.5,.02);assertThrows(AtmosphereDomainError.class,()->run(invalid));
    }
}
