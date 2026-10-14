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
    }
}
