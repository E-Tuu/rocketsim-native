package consumer;

import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;
import org.roketsim.core.math.FrameConventions;
import org.roketsim.core.units.UnitPolicy;
import org.roketsim.core.math.Quaternion;
import org.roketsim.core.math.Quaternions;
import org.roketsim.core.environment.Altitude;
import org.roketsim.core.environment.USStandardAtmosphere1976Lower;
import org.roketsim.core.environment.DryAirPropertiesCalculator;
import org.roketsim.core.environment.ConstantGravityModel;
import org.roketsim.core.environment.ConstantWindModel;
import org.roketsim.core.flightconditions.RelativeFlowCalculator;
import org.roketsim.core.flightconditions.BasicFlightConditionsCalculator;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.mass.StructuralMassPropertiesCalculator;
import org.roketsim.core.mass.RocketMassPropertiesCalculator;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.aerodynamics.*;
import org.roketsim.core.dynamics.*;
import org.roketsim.core.numerics.*;
import org.roketsim.core.simulation.*;

public final class CoreConsumer {
    public static void main(String[] args) {
        if (Vectors.magnitude(Vector.of(new double[]{3,4,0})) != 5.0
            || !UnitPolicy.CANONICAL_UNITS.get("angle").equals("rad")
            || !FrameConventions.GEOMETRY_AXIAL_CONVENTION.get("coordinate").equals("x_geo")) {
            throw new AssertionError("Independent consumer failed");
        }
        System.out.println("INDEPENDENT_CONSUMER: PASS");
        Quaternion q = Quaternions.fromAxisAngle(Vector.of(new double[]{1,0,0}), Math.PI/2, 0);
        Vector world = Quaternions.rotateBodyToWorld(q, Vector.of(new double[]{0,0,1}), 0);
        // Kabul edilmiş QUAT-T02 fixture toleransı; production default değildir.
        if (Math.abs(world.get(1)+1)>1e-12 || Math.abs(world.get(2))>1e-12) {
            throw new AssertionError("Quaternion consumer failed");
        }
        System.out.println("QUATERNION_CONSUMER: PASS");
        var atmosphere = new USStandardAtmosphere1976Lower().evaluate(1200);
        var air = new DryAirPropertiesCalculator().evaluate(atmosphere);
        var wind = new ConstantWindModel(new double[]{0,20,0}).evaluate();
        var relative = new RelativeFlowCalculator().evaluate(new double[]{0,120,0},wind);
        var conditions = new BasicFlightConditionsCalculator().evaluate(relative,atmosphere,air,.1);
        if (conditions.airspeedMS()!=100 || conditions.mach()!=100/air.speedOfSoundMS()
            || new ConstantGravityModel().evaluate().get(2)!=-9.80665
            || Altitude.aglFromOrthometric(1200,850)!=350) {
            throw new AssertionError("Environment consumer failed");
        }
        System.out.println("ENVIRONMENT_CONSUMER: PASS");
        // Açık NAT-011 consumer fixture'ı; generic production default değildir.
        var vehicle=new SingleStageRocketGeometry(.1,new ConicalNoseGeometry(.3,NoseConstructionMode.HOLLOW_SHELL,.002),
            new CylindricalBodyGeometry(.7,.002),new TrapezoidalFinSetGeometry(4,.18,.08,.12,.05,.72,.003,
                FinCrossSection.SQUARE,FinAngularArrangement.EQUALLY_SPACED),
            new MotorAttachmentGeometry(new MotorMountTubeGeometry(.12,.029,.001,0),new CenteringRingPairGeometry(.003),.005),
            ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
        var resolved=new GeometryResolver().resolve(vehicle);
        var materials=new SingleStageRocketMaterials(MaterialCatalog.POLYSTYRENE,MaterialCatalog.CARDBOARD,
            MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD);
        var structure=new StructuralMassPropertiesCalculator().evaluate(resolved,materials);
        // Existing SMEXT-T16 relative tolerance; production numerical policy değildir.
        if(resolved.source()!=vehicle || Math.abs(structure.structureMassKg()-.5508490028277474)>.5508490028277474*3e-14
            || Math.abs(structure.structureCgXGeoM()-.6266199705547180)>.6266199705547180*3e-14
            || structure.nose().material()!=MaterialCatalog.POLYSTYRENE) {
            throw new AssertionError("Vehicle structural consumer failed");
        }
        System.out.println("VEHICLE_STRUCTURAL_CONSUMER: PASS");
        var motor=MotorCatalogs.DEMO_MOTOR_CATALOG.get("aerotech_f50_4t");
        var installation=new MotorInstallationResolver().resolve(resolved,motor);
        var runtime=new MotorPropertyEvaluator().evaluate(installation,
            MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,1.4);
        var total=new RocketMassPropertiesCalculator().evaluate(structure,runtime);
        var thrust=new MotorThrustCurveEvaluator().evaluate(motor,1.4);
        // Accepted THRUST/MPROP/RMASS fixture tolerances, not production defaults.
        if(Math.abs(runtime.massKg()-.047016141471977205)>.047016141471977205*3e-15
            ||Math.abs(total.totalMassKg()-.5978651442997246)>.5978651442997246*3e-15
            ||Math.abs(total.totalCgXGeoM()-.6525224306832178)>.6525224306832178*3e-15
            ||Math.abs(thrust.thrustN()-2.181395348837211)>2.181395348837211*4e-15) {
            throw new AssertionError("Propulsion/runtime mass consumer failed");
        }
        System.out.println("PROPULSION_RUNTIME_MASS_CONSUMER: PASS");
        // Accepted NAT-012 synthetic fixtures, not generic engine defaults.
        var smooth=new SurfaceFinish("Consumer synthetic",0);
        var surfaces=new SingleStageRocketAerodynamicSurfaces(smooth,smooth,smooth);
        var aeroFlight=new org.roketsim.core.flightconditions.BasicFlightConditions(100,.3,123,456);
        var aeroAir=new org.roketsim.core.environment.DryAirProperties(340,1.8e-5,1.5e-5,1.4);
        var explicitProfile=new BasicDragModelProfile("fully_turbulent","aerodynamic_length",
            "minimum_evaluation_reynolds",1e4,"td13_conical_subsonic_to_sonic",
            "td13_square","td13_subsonic_to_sonic","ignored");
        var drag=new BasicDragEvaluator().evaluate(resolved,surfaces,aeroFlight,aeroAir,explicitProfile);
        var staticAero=new StaticAerodynamicEvaluator().evaluate(resolved,
            new org.roketsim.core.flightconditions.BasicFlightConditions(100,.75,0,0),
            StaticStabilityModelProfile.NATIVE_STATIC_STABILITY_V1_PROFILE);
        var margin=new StaticMarginCalculator().evaluate(staticAero,total,resolved);
        if(Math.abs(drag.totalCd0()-.4740271634476429)>.4740271634476429*5e-15
            || drag.modelProfile()!=explicitProfile
            || Math.abs(staticAero.cpXGeoM()-.707290109576936)>.707290109576936*4e-15
            || Math.abs(margin.staticMarginCalibers()-.5476767889371825)>.5476767889371825*5e-15) {
            throw new AssertionError("Aerodynamics consumer failed");
        }
        System.out.println("AERODYNAMICS_CONSUMER: PASS");
        // Accepted NAT-013/014 explicit 3D fixture; no upstream physics orchestration.
        var launch=new LaunchConditions3DOF(new double[]{10,20,30},new double[]{3,0,4},new double[]{.6,0,.8});
        var state=new InitialStateBuilder().build(launch);
        var snapshot=new TranslationalDynamicsInputs(1.5,10,new double[]{3,0,4},50,.2,.4,new double[]{0,0,-9.8});
        var dynamics=new TranslationalDynamicsEvaluator().evaluate(state,launch,snapshot,
            TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE);
        double[] expectedAcceleration={2.4,0,-6.6};
        for(int i=0;i<3;i++) {
            if(Math.abs(dynamics.accelerationWorldMS2().get(i)-expectedAcceleration[i])>2e-15)
                throw new AssertionError("Dynamics acceleration fixture failed");
        }
        if(state.positionWorldM().get(0)!=10 || state.velocityWorldMS().get(2)!=4
            || state.positionWorldM()==launch.initialPositionWorldM()
            || dynamics.accelerationWorldMS2()!=dynamics.derivative().velocityDerivativeWorldMS2())
            throw new AssertionError("Dynamics state/ownership consumer failed");
        System.out.println("DYNAMICS_CONSUMER: PASS");
        // Accepted NAT-017 constant-acceleration fixture; packaged JAR API only.
        var numericalState=new TranslationalState3DOF(new double[]{0,0,100},new double[]{0,0,20});
        int[] calls={0};
        var endpoint=new ClassicalRK4Integrator3DOF().step(new IntegrationPoint3DOF(0,numericalState),
            new FixedStepConfig(.5),(t,s)->{
                calls[0]++;
                return new TranslationalStateDerivative3DOF(s.velocityWorldMS(),new double[]{0,0,-10});
            });
        if(calls[0]!=4 || endpoint.timeS()!=.5 || Math.abs(endpoint.state().positionWorldM().get(2)-108.75)>108.75e-7
            || Math.abs(endpoint.state().velocityWorldMS().get(2)-15)>15e-7 || endpoint.state()==numericalState
            || numericalState.positionWorldM().get(2)!=100)
            throw new AssertionError("RK4 independent consumer failed");
        System.out.println("NUMERICS_RK4_CONSUMER: PASS");
        // NAT-015 real F50 fixture: no numerical integration or duplicate physics.
        var physicsContext=new PhysicsEvaluationContext3DOF(resolved,surfaces,structure,installation,
            MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE,
            BasicDragModelProfile.NATIVE_BASIC_DRAG_V1_PROFILE,
            TranslationalDynamicsModelProfile.NATIVE_TRANSLATIONAL_DYNAMICS_V1_PROFILE,
            EnvironmentPositionMappingModel.LOCAL_ENU_VERTICAL_OFFSET,1200,new PropulsionTimeline(0),
            new USStandardAtmosphere1976Lower(),new DryAirPropertiesCalculator(),
            new ConstantGravityModel(),new org.roketsim.core.environment.NoWindModel());
        var physicsLaunch=new LaunchConditions3DOF(new double[]{10,20,50},new double[]{0,0,0},new double[]{0,0,1});
        var physicsState=new InitialStateBuilder().build(physicsLaunch);
        var physics=new PhysicsEvaluator3DOF().evaluate(.012,physicsState,physicsLaunch,physicsContext);
        if(physics.environmentAltitudeM()!=1200 || physics.motorTimeS()!=.012
            || physics.motorThrustState().thrustN()!=51.377
            || Math.abs(physics.motorMassProperties().massKg()-.0847479321)>5e-11
            || Math.abs(physics.rocketMassProperties().totalMassKg()-.6355969349)>5e-11
            || physics.dynamics().modelProfile()!=physicsContext.translationalDynamicsModelProfile()
            || physics.flightConditions().airspeedMS()!=0)
            throw new AssertionError("Physics evaluator consumer snapshot failed");
        var supplied=new TranslationalDynamicsInputs(physics.rocketMassProperties().totalMassKg(),
            physics.motorThrustState().thrustN(),physics.relativeFlow(),physics.flightConditions().dynamicPressurePa(),
            resolved.referenceAreaM2(),physics.basicDrag().totalCd0(),physics.gravityAccelerationWorldMS2());
        if(!physics.dynamics().derivative().equals(new TranslationalDynamicsEvaluator().evaluate(physicsState,
                physicsLaunch,supplied,physicsContext.translationalDynamicsModelProfile()).derivative()))
            throw new AssertionError("Physics evaluator consumer derivative failed");
        System.out.println("PHYSICS_EVALUATOR_CONSUMER: PASS");
    }
}
