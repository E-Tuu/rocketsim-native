package org.roketsim.core;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.simulation.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.math.Vector;

class PhysicsEvaluatorTest {
    static PhysicsEvaluationContext3DOF context(double altitude,double ignition,Object wind) {
        var smooth=new SurfaceFinish("Synthetic smooth",0);
        return new PhysicsEvaluationContext3DOF(MotorInstallationTest.geometry(),
            new SingleStageRocketAerodynamicSurfaces(smooth,smooth,smooth),RocketMassTest.structure(),
            new MotorInstallationResolver().resolve(MotorInstallationTest.geometry(),MotorCatalogs.AEROTECH_F50_4T),
            MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,
            TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,altitude,new PropulsionTimeline(ignition),
            new USStandardAtmosphere1976Lower(),new DryAirPropertiesCalculator(),new ConstantGravityModel(),wind);
    }
    static PhysicsEvaluationContext3DOF change(PhysicsEvaluationContext3DOF c,String path,Object value) {
        return (PhysicsEvaluationContext3DOF)VehicleTestData.replace(c,path,value);
    }
    static LaunchConditions3DOF launch(double z) { return new LaunchConditions3DOF(new double[]{10,20,z},new double[]{0,0,0},new double[]{0,0,1}); }
    static TranslationalState3DOF state(double z,double... velocity) { return new TranslationalState3DOF(new double[]{10,20,z},velocity); }
    static PhysicsEvaluationResult3DOF evaluate(PhysicsEvaluationContext3DOF c,double time,double z,double... velocity) {
        return new PhysicsEvaluator3DOF().evaluate(time,state(z,velocity),launch(50),c);
    }
    @Test void mandatorySchemasAndStatelessScope() {
        assertEquals(14,PhysicsEvaluationContext3DOF.class.getRecordComponents().length);
        assertEquals(13,PhysicsEvaluationResult3DOF.class.getRecordComponents().length);
        assertEquals(1,PropulsionTimeline.class.getRecordComponents().length);
        assertEquals(0,PhysicsEvaluator3DOF.class.getDeclaredFields().length);
        var fields=Arrays.stream(PhysicsEvaluationResult3DOF.class.getRecordComponents()).map(x->x.getName()).collect(Collectors.toSet());
        for(String forbidden:Set.of("dynamicsInputs","thrustN","massKg","mach","reynolds","dynamicPressurePa","totalCd0","accelerationWorldMS2","timestepS")) assertFalse(fields.contains(forbidden));
        assertEquals(1,EnvironmentPositionMappingModel.values().length);
        assertEquals("local_enu_vertical_offset",EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET.value());
    }
    @Test void contextRetainsAllUpstreamReferences() {
        var c=context(-100,-2,new NoWindModel()); var copy=change(c,"launch_environment_altitude_m",1200.);
        assertEquals(-100,c.launchEnvironmentAltitudeM()); assertEquals(-2,c.propulsionTimeline().ignitionTimeS());
        assertSame(c.resolvedGeometry(),copy.resolvedGeometry()); assertSame(c.structuralMassProperties(),copy.structuralMassProperties());
        assertSame(c.aerodynamicSurfaces(),copy.aerodynamicSurfaces()); assertSame(c.motorInstallation(),copy.motorInstallation());
        assertSame(c.motorPropertyModelProfile(),copy.motorPropertyModelProfile()); assertSame(c.basicDragModelProfile(),copy.basicDragModelProfile());
        assertSame(c.translationalDynamicsModelProfile(),copy.translationalDynamicsModelProfile()); assertSame(c.propulsionTimeline(),copy.propulsionTimeline());
        assertSame(c.atmosphereModel(),copy.atmosphereModel()); assertSame(c.airPropertiesCalculator(),copy.airPropertiesCalculator());
        assertSame(c.gravityModel(),copy.gravityModel()); assertSame(c.windModel(),copy.windModel());
    }
    @Test void rawNonfiniteValidationAndTimePriority() {
        var c=context(1200,5,new NoWindModel());
        for(double x:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new PropulsionTimeline(x)).getClass());
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->change(c,"launch_environment_altitude_m",x)).getClass());
            var e=assertThrows(IllegalArgumentException.class,()->evaluate(change(c,"environment_position_mapping_model","other"),x,50,0,0,0));
            assertEquals(IllegalArgumentException.class,e.getClass()); assertTrue(e.getMessage().startsWith("time_s"));
        }
    }
    @Test void signedEnvironmentMappingFixtures() {
        var c=context(1200,5,new NoWindModel());
        for(double[] fixture:new double[][]{{173.25,1323.25},{20,1170}}) {
            var r=evaluate(c,5,fixture[0],0,0,0);
            assertEquals(fixture[1],r.environmentAltitudeM()); assertEquals(c.atmosphereModel().evaluate(fixture[1]),r.atmosphere());
        }
    }
    @Test void mappingRequiresEnumIdentityNotCanonicalString() {
        var c=context(1200,5,new NoWindModel());
        for(Object invalid:new Object[]{"local_enu_vertical_offset","LOCAL_ENU_VERTICAL_OFFSET","other",null,1.}) {
            var e=assertThrows(PhysicsEvaluationError.class,()->evaluate(change(c,"environment_position_mapping_model",invalid),5,50,0,0,0));
            assertEquals("UNSUPPORTED_ENVIRONMENT_POSITION_MAPPING_MODEL",e.errorCode());
            assertEquals("environment_position_mapping_model",e.fieldName()); assertSame(invalid,e.value());
        }
    }
    @Test void derivedAltitudeOverflowIsStructured() {
        var e=assertThrows(PhysicsEvaluationError.class,()->new PhysicsEvaluator3DOF().evaluate(5,state(1e308,0,0,0),launch(-1e308),context(1200,5,new NoWindModel())));
        assertEquals("INVALID_ENVIRONMENT_ALTITUDE_MAPPING",e.errorCode()); assertEquals("environment_altitude_m",e.fieldName()); assertEquals(Double.POSITIVE_INFINITY,e.value());
    }
    @Test void timelineAnchorsAndSameExactPropulsionTime() {
        for(double[] fixture:new double[][]{{6.43,5},{5,5},{-1,-2}}) {
            var c=context(1200,fixture[1],new NoWindModel()); var r=evaluate(c,fixture[0],50,0,0,0);
            double elapsed=fixture[0]-fixture[1]; assertEquals(elapsed,r.motorTimeS());
            assertEquals(new MotorThrustCurveEvaluator().evaluate(c.motorInstallation().motor(),elapsed),r.motorThrustState());
            assertEquals(new MotorPropertyEvaluator().evaluate(c.motorInstallation(),c.motorPropertyModelProfile(),elapsed),r.motorMassProperties());
        }
    }
    @Test void preignitionAndFailureOrderingWithoutFallback() {
        var c=context(1200,5,new NoWindModel());
        var e=assertThrows(PhysicsEvaluationError.class,()->evaluate(c,4.999,50,0,0,0));
        assertEquals("BEFORE_IGNITION_UNSUPPORTED",e.errorCode()); assertEquals("motor_time_s",e.fieldName()); assertEquals(4.999-5,e.value());
        // Atmosphere precedes motor time, motor time precedes motor access.
        assertThrows(AtmosphereDomainError.class,()->evaluate(change(c,"launch_environment_altitude_m",90000.),4.999,50,0,0,0));
        assertThrows(PhysicsEvaluationError.class,()->evaluate(change(c,"motor_installation",null),4.999,50,0,0,0));
        assertThrows(PhysicsEvaluationError.class,()->evaluate(change(c,"basic_drag_model_profile",null),4.999,50,0,0,0));
        var badDynamics=change(c,"translational_dynamics_model_profile",new TranslationalDynamicsModelProfile("other","other"));
        var badBoth=change(badDynamics,"basic_drag_model_profile",null);
        assertThrows(AerodynamicEvaluationError.class,()->evaluate(badBoth,5,50,0,0,0));
    }
    @Test void realF50Anchor() {
        var r=evaluate(context(1200,0,new NoWindModel()),.012,50,0,0,0);
        assertEquals(51.377,r.motorThrustState().thrustN());
        assertEquals(.0847479321,r.motorMassProperties().massKg(),5e-11);
        assertEquals(.6355969349,r.rocketMassProperties().totalMassKg(),5e-11);
    }
    @Test void bothWindModelsAndUpstreamPlumbing() {
        for(Object wind:new Object[]{new NoWindModel(),new ConstantWindModel(new double[]{1,-2,.5})}) {
            var c=context(1200,5,wind); var r=evaluate(c,5,50,3,4,5);
            var expected=wind instanceof NoWindModel?new double[]{3,4,5}:new double[]{2,6,4.5};
            assertArrayEquals(expected,r.relativeFlow().toArray());
            assertEquals(new BasicFlightConditionsCalculator().evaluate(r.relativeFlow(),r.atmosphere(),r.airProperties(),c.resolvedGeometry().referenceLengthM()),r.flightConditions());
            assertEquals(new BasicDragEvaluator().evaluate(c.resolvedGeometry(),c.aerodynamicSurfaces(),r.flightConditions(),r.airProperties(),c.basicDragModelProfile()),r.basicDrag());
            var snapshot=new TranslationalDynamicsInputs(r.rocketMassProperties().totalMassKg(),r.motorThrustState().thrustN(),r.relativeFlow(),r.flightConditions().dynamicPressurePa(),c.resolvedGeometry().referenceAreaM2(),r.basicDrag().totalCd0(),r.gravityAccelerationWorldMS2());
            assertEquals(new TranslationalDynamicsEvaluator().evaluate(state(50,3,4,5),launch(50),snapshot,c.translationalDynamicsModelProfile()),r.dynamics());
        }
    }
    @Test void resultCopiesVectorsAndRetainsNestedAuthorities() {
        var r=evaluate(context(1200,5,new NoWindModel()),5,50,1,2,3);
        var copy=new PhysicsEvaluationResult3DOF(r.environmentAltitudeM(),r.motorTimeS(),r.atmosphere(),r.airProperties(),r.airMassVelocityWorldMS(),r.relativeFlow(),r.flightConditions(),r.motorThrustState(),r.motorMassProperties(),r.rocketMassProperties(),r.basicDrag(),r.gravityAccelerationWorldMS2(),r.dynamics());
        assertSame(r.atmosphere(),copy.atmosphere()); assertSame(r.dynamics(),copy.dynamics()); assertSame(r.basicDrag(),copy.basicDrag());
        assertNotSame(r.airMassVelocityWorldMS(),copy.airMassVelocityWorldMS()); assertNotSame(r.relativeFlow(),copy.relativeFlow()); assertNotSame(r.gravityAccelerationWorldMS2(),copy.gravityAccelerationWorldMS2());
        copy.relativeFlow().toArray()[0]=999; assertEquals(1,copy.relativeFlow().get(0));
        assertThrows(IllegalArgumentException.class,()->new PhysicsEvaluationResult3DOF(0,0,r.atmosphere(),r.airProperties(),Vector.of(new double[]{1,2}),r.relativeFlow(),r.flightConditions(),r.motorThrustState(),r.motorMassProperties(),r.rocketMassProperties(),r.basicDrag(),r.gravityAccelerationWorldMS2(),r.dynamics()));
    }
    @Test void deterministicNoMutationNoWindFallback() {
        var c=context(1200,5,new NoWindModel()); var s=state(50,1,2,3); var l=launch(50); var evaluator=new PhysicsEvaluator3DOF();
        assertEquals(evaluator.evaluate(5,s,l,c),evaluator.evaluate(5,s,l,c));
        assertArrayEquals(new double[]{10,20,50},s.positionWorldM().toArray()); assertArrayEquals(new double[]{1,2,3},s.velocityWorldMS().toArray());
        assertThrows(IllegalArgumentException.class,()->evaluate(change(c,"wind_model",null),5,50,0,0,0));
        assertThrows(IllegalArgumentException.class,()->evaluate(change(c,"wind_model","no_wind"),5,50,0,0,0));
    }
    @Test void controlledNat014AssemblyAnchorIsNotProductionDefault() {
        var snapshot=new TranslationalDynamicsInputs(2,30,new double[]{0,0,0},0,.01,.5,new double[]{0,0,-10});
        var r=new TranslationalDynamicsEvaluator().evaluate(state(50,0,0,0),launch(50),snapshot,TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE);
        assertArrayEquals(new double[]{0,0,5},r.derivative().velocityDerivativeWorldMS2().toArray());
    }
}
