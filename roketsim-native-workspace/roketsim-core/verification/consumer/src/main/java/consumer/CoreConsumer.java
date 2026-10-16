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
    }
}
