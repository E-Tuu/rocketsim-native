package org.roketsim.core;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.simulation.*;
import static org.junit.jupiter.api.Assertions.*;

/** NAT-026 scheduling only: source fixtures remain outside production catalogs. */
class MotorStepBoundariesTest {
    static SimulationRunConfiguration3DOF configuration(MotorDefinition motor,double ignition,double h,int steps) {
        var c=SimulationEngineTest.config(steps,ignition,h,20);
        var installed=c.physicsContext().motorInstallation();
        c=(SimulationRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.motor_installation",
            new MotorInstallation(motor,installed.motorFrontXGeoM(),installed.motorAftXGeoM(),installed.nominalRadialClearanceM(),installed.axialEngagementLengthM()));
        return (SimulationRunConfiguration3DOF)VehicleTestData.replace(c,"motor_curve_statistics",new MotorCurveAnalyzer().analyze(motor));
    }
    static MotorDefinition triangle(double knot,double end,double peak) {
        return (MotorDefinition)VehicleTestData.replace(MotorCatalogs.AEROTECH_F50_4T,"thrust_curve",
            List.of(new ThrustSample(0,0),new ThrustSample(knot,peak),new ThrustSample(end,0)));
    }
    static MotorTimeBoundarySchedule3DOF schedule(SimulationRunConfiguration3DOF c) {
        return MotorTimeBoundarySchedule3DOF.fromContext(c.physicsContext(),c.motorCurveStatistics());
    }
    @Test void legacyEntrypointsAreExactlyIdenticalAndNullModeRejected() {
        var c=NativeDemoTrajectoryTest.configuration(.01,5000); var engine=new SimulationEngine3DOF();
        assertEquals(engine.run(c),engine.run(c,IntegrationStepAlignmentMode.LEGACY_FIXED_STEP));
        assertThrowsExactly(IllegalArgumentException.class,()->engine.run(c,null));
        assertArrayEquals(new IntegrationStepAlignmentMode[]{IntegrationStepAlignmentMode.LEGACY_FIXED_STEP,
            IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES},IntegrationStepAlignmentMode.values());
    }
    @Test void syntheticInteriorBoundaryUnchangedStepAndReachedBoundary() {
        var s=schedule(configuration(triangle(.37,1,20),0,.1,1));
        assertEquals(.37-.30,s.effectiveStepSizeS(.30,.1));
        assertEquals(.1,s.effectiveStepSizeS(.1,.1));
        assertEquals(.1,s.effectiveStepSizeS(.37,.1));
        assertEquals(1-.95,s.effectiveStepSizeS(.95,.1));
        assertEquals(.1,s.effectiveStepSizeS(1,.1));
        assertThrows(UnsupportedOperationException.class,()->s.boundaryTimesS().add(5.));
    }
    @Test void independentSourceGridsOnlyParticipateForActivePolicies() {
        var c=configuration(MotorPropertiesTest.sourceMotor(),0,.1,1);
        assertEquals(List.of(0.,.2,.8),schedule(c).boundaryTimesS());
        var explicit=(SimulationRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.motor_property_model_profile",MotorPropertiesTest.explicit());
        assertEquals(List.of(0.,.2,.5,.8,1.),schedule(explicit).boundaryTimesS());
        var different=(MotorDefinition)VehicleTestData.replace(MotorPropertiesTest.sourceMotor(),"cg_curve",
            List.of(new MotorCgSample(0,.04),new MotorCgSample(.625,.05),new MotorCgSample(1.25,.06)));
        var mixed=configuration(different,0,.1,1);
        mixed=(SimulationRunConfiguration3DOF)VehicleTestData.replace(mixed,"physics_context.motor_property_model_profile",
            new MotorPropertyModelProfile(MotorMassEvolutionModel.EXPLICIT_CURVE,MotorCgEvolutionModel.FIXED_MIDPOINT));
        assertEquals(List.of(0.,.2,.5,.8,1.),schedule(mixed).boundaryTimesS());
        mixed=(SimulationRunConfiguration3DOF)VehicleTestData.replace(mixed,"physics_context.motor_property_model_profile",
            new MotorPropertyModelProfile(MotorMassEvolutionModel.IMPULSE_PROPORTIONAL,MotorCgEvolutionModel.EXPLICIT_CURVE));
        assertEquals(List.of(0.,.2,.625,.8,1.25),schedule(mixed).boundaryTimesS());
    }
    @Test void differentSourceGridsAndNegativeIgnitionUseSameScheduler() {
        for(double knot:new double[]{.25,.375,.5}) {
            var s=schedule(configuration(triangle(knot,.75,20),-2,.125,1));
            assertEquals(List.of(-2.,-2+knot,-1.25),s.boundaryTimesS());
            assertEquals(knot,s.effectiveStepSizeS(-2,.5));
        }
    }
    @Test void rejectsInvalidInputsOverflowAndUnrepresentableProgressWithoutRepair() {
        var c=configuration(triangle(.375,.75,20),0,.1,1); var s=schedule(c);
        assertThrowsExactly(IllegalArgumentException.class,()->MotorTimeBoundarySchedule3DOF.fromContext(null,c.motorCurveStatistics()));
        assertThrowsExactly(IllegalArgumentException.class,()->MotorTimeBoundarySchedule3DOF.fromContext(c.physicsContext(),null));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(bad,.1));
            assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(0,bad));
        }
        for(double bad:new double[]{0,-.1}) assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(0,bad));
        assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(Double.MAX_VALUE,Double.MAX_VALUE));
        assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(1e20,1));
        var overflowing=configuration(triangle(.375,.75,20),Double.MAX_VALUE,.1,1);
        assertThrowsExactly(IllegalArgumentException.class,()->MotorTimeBoundarySchedule3DOF.fromContext(overflowing.physicsContext(),FlightEventsTest.stats(Double.MAX_VALUE)));
    }
    @Test void coincidentNominalEndpointKeepsNominalEvenWhenDifferenceRoundsUp() {
        // Real F50 source nodes: rounded proposed endpoint is reached, but b-t > h.
        var s=schedule(SimulationEngineTest.config(1,0,.01,0));
        assertEquals(.152,.142+.01);
        assertTrue(.152-.142>.01);
        assertEquals(.01,s.effectiveStepSizeS(.142,.01));
        assertEquals(.152,.142+s.effectiveStepSizeS(.142,.01));
    }
    @Test void coincidentRoundedEndpointsKeepNominalWithoutRecomputingTheDifference() {
        var s=schedule(configuration(triangle(.375,.75,20),0,.125,1));
        for(double h:new double[]{Math.nextDown(.125),.125,Math.nextUp(.125)}) {
            assertEquals(.375,.25+h);
            assertEquals(h,s.effectiveStepSizeS(.25,h));
        }
    }
    @Test void strictlyInteriorBoundariesAreReachedWithoutCrossing() {
        var s=schedule(configuration(triangle(.375,.75,20),0,.25,1));
        for(double t:new double[]{0,.25,Math.nextDown(.375)}) {
            double h=.5,step=s.effectiveStepSizeS(t,h);
            assertTrue(t<.375 && .375<t+h);
            assertTrue(step>0 && step<=h);
            assertEquals(.375,t+step);
        }
        assertEquals(.125,s.effectiveStepSizeS(.375,.125)); // Already reached, next source knot is beyond endpoint.
    }
    @Test void coincidentExplicitCurveKnotsProduceOnlyOneEndpoint() {
        var c=configuration(MotorPropertiesTest.sourceMotor(),0,.25,1);
        c=(SimulationRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.motor_property_model_profile",MotorPropertiesTest.explicit());
        var s=schedule(c);
        assertEquals(1,s.boundaryTimesS().stream().filter(t->t==.5).count());
        assertEquals(.25,s.effectiveStepSizeS(.25,.25));
        assertEquals(.25,s.effectiveStepSizeS(.5,.25));
    }
    @Test void unrepresentableStrictInteriorEndpointStillFailsRatherThanCrossing() {
        // Standalone arithmetic probe, not pre-ignition flight mechanics.
        var s=schedule(configuration(triangle(.375,.75,20),.37,.1,1));
        double t=-1,h=2,boundary=.37;
        assertTrue(t<boundary && boundary<t+h);
        assertTrue(t+(boundary-t)>boundary);
        assertTrue(assertThrowsExactly(IllegalArgumentException.class,()->s.effectiveStepSizeS(t,h))
            .getMessage().contains("endpoint_time_s"));
    }
    @Test void realF50AlignedHundredthStepPassesTheFormerCoincidenceFailure() {
        var c=SimulationEngineTest.config(220,0,.01,20);
        var engine=new SimulationEngine3DOF();
        var result=engine.run(c,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        assertEquals(result,engine.run(c,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES));
        var times=result.recordedData().samples().stream().map(s->s.point().timeS()).toList();
        assertTrue(times.contains(.142)); assertTrue(times.contains(.152));
        for(var node:MotorCatalogs.AEROTECH_F50_4T.thrustCurve()) assertTrue(times.contains(node.timeS()));
        for(int i=1;i<times.size();i++) assertTrue(times.get(i)>times.get(i-1));
        assertEquals(220,result.stepsPerformed()); assertEquals(221,times.size());
        assertEquals(SimulationTerminationReason.MAXIMUM_STEPS_REACHED,result.terminationReason());
        assertEquals(1,result.recordedData().events().stream().filter(e->e.eventType()==FlightEventType.BURNOUT).count());
    }
    @Test void selectedExplicitGridRequiresRealSourceAndValidStatistics() {
        var c=SimulationEngineTest.config(1,0,.1,0);
        var explicit=(SimulationRunConfiguration3DOF)VehicleTestData.replace(c,"physics_context.motor_property_model_profile",MotorPropertiesTest.explicit());
        assertThrowsExactly(IllegalArgumentException.class,()->schedule(explicit));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1}) {
            assertThrowsExactly(IllegalArgumentException.class,()->MotorTimeBoundarySchedule3DOF.fromContext(c.physicsContext(),FlightEventsTest.stats(bad)));
        }
    }
    @Test void alignedRunReachesKnotsAndBurnoutOnceWithoutKnotEvents() {
        var c=configuration(triangle(.375,.75,20),0,.25,8); var e=new SimulationEngine3DOF();
        var a=e.run(c,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        assertEquals(a,e.run(c,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES));
        var times=a.recordedData().samples().stream().map(s->s.point().timeS()).toList();
        assertTrue(times.contains(.375)); assertTrue(times.contains(.75));
        assertEquals(8,a.stepsPerformed()); assertEquals(9,times.size());
        assertEquals(SimulationTerminationReason.MAXIMUM_STEPS_REACHED,a.terminationReason());
        assertEquals(1,a.recordedData().events().stream().filter(x->x.eventType()==FlightEventType.BURNOUT).count());
        assertFalse(a.recordedData().events().stream().anyMatch(x->x.timeS()==.375));
        for(var sample:a.recordedData().samples())
            assertEquals(new PhysicsEvaluator3DOF().evaluate(sample.point().timeS(),sample.point().state(),c.launchConditions(),c.physicsContext()),sample.physics());
    }
    @Test void shiftedRunStartsAtAcceptedIgnitionAndNoNegativeMotorTime() {
        var c=configuration(triangle(.375,.75,20),-2,.25,5);
        var run=new SimulationEngine3DOF().run(c,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        assertEquals(-2,run.recordedData().samples().get(0).point().timeS());
        assertTrue(run.recordedData().samples().stream().allMatch(s->s.point().timeS()>=-2));
        assertEquals(1,run.recordedData().events().stream().filter(e->e.eventType()==FlightEventType.BURNOUT && e.timeS()==-1.25).count());
    }
    @Test void terminalCandidateHandlingRemainsSharedWithLegacy() {
        // Before the first knot both grids coincide; test real interior ground crossing.
        var base=configuration(triangle(.5,1,1),0,.125,100);
        var launch=base.launchConditions();
        var c=(SimulationRunConfiguration3DOF)VehicleTestData.replace(base,"launch_conditions",
            new LaunchConditions3DOF(launch.initialPositionWorldM(),new double[]{0,0,1},launch.launchDirectionWorldUnit()));
        var engine=new SimulationEngine3DOF(); var legacy=engine.run(c);
        var aligned=engine.run(c,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        assertEquals(SimulationTerminationReason.TERMINAL_EVENT,aligned.terminationReason());
        assertEquals(legacy,aligned);
        var ground=aligned.recordedData().events().stream().filter(e->e.eventType()==FlightEventType.GROUND).findFirst().orElseThrow();
        assertTrue(ground.interpolationFraction()<1);
        assertTrue(aligned.recordedData().samples().get(aligned.recordedData().samples().size()-1).point().timeS()<ground.timeS());
    }
    @Test void realF50SourceBoundariesAndStepLimitRemainExplicit() {
        var c=SimulationEngineTest.config(40,0,.1,20);
        var run=new SimulationEngine3DOF().run(c,IntegrationStepAlignmentMode.ALIGN_MOTOR_SOURCE_BOUNDARIES);
        var times=run.recordedData().samples().stream().map(s->s.point().timeS()).toList();
        for(var node:MotorCatalogs.AEROTECH_F50_4T.thrustCurve()) assertTrue(times.contains(node.timeS()));
        assertEquals(40,run.stepsPerformed()); assertEquals(41,times.size());
        assertEquals(1,run.recordedData().events().stream().filter(e->e.eventType()==FlightEventType.BURNOUT).count());
    }
    static double integrateSource(double h,boolean align,boolean exponential) {
        var motor=triangle(.375,.75,exponential?2:24);
        var schedule=schedule(configuration(motor,0,h,1));
        var evaluator=new MotorThrustCurveEvaluator();
        var point=new IntegrationPoint3DOF(0,ClassicalRK4Test.state(exponential?1:0,0));
        var rk4=new ClassicalRK4Integrator3DOF();
        while(point.timeS()<1) {
            double step=Math.min(h,1-point.timeS());
            if(align) step=schedule.effectiveStepSizeS(point.timeS(),step);
            point=rk4.step(point,new FixedStepConfig(step),(t,state)-> {
                double thrust=evaluator.evaluate(motor,t).thrustN();
                return exponential?ClassicalRK4Test.rates(thrust*state.positionWorldM().get(0),0):
                    ClassicalRK4Test.rates(state.velocityWorldMS().get(0),thrust/2-8);
            });
        }
        return point.state().positionWorldM().get(0);
    }
    @Test void alignedPiecewisePolynomialMatchesIndependentIntegral() {
        // Triangle impulse=9, first moment=9*.375. Constant mass=2, g=-8.
        // x(1)=(9-9*.375)/2 - 4 = -1.1875. Test-only ODE, no new flight model.
        double aligned=integrateSource(.25,true,false),legacy=integrateSource(.25,false,false);
        System.out.println("N26 polynomial reference=-1.1875 aligned="+aligned+" legacy="+legacy);
        assertEquals(-1.1875,aligned,1.1875e-7);
        assertTrue(Math.abs(legacy+1.1875)>1.1875e-7);
    }
    @Test void nonlinearRk4ConvergesAgainstIndependentAnalyticReference() {
        // y'=T(t)*y, y(0)=1: y(1)=exp(integral T)=exp(.75).
        double reference=Math.exp(.75),previous=Double.POSITIVE_INFINITY;
        for(double h:new double[]{.25,.125,.0625,.03125}) {
            double error=Math.abs(integrateSource(h,true,true)-reference);
            System.out.println("N26 exponential h="+h+" reference="+reference+" absolute_error="+error);
            assertTrue(error<previous/8,"Expected fourth-order refinement: "+error+" vs "+previous);
            previous=error;
        }
        assertTrue(previous<reference*1e-7); // Existing RK4 np.assert_allclose convention.
    }
}
