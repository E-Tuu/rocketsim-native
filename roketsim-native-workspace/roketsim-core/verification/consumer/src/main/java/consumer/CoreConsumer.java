package consumer;

import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;
import org.roketsim.core.math.FrameConventions;
import org.roketsim.core.units.UnitPolicy;
import org.roketsim.core.math.Quaternion;
import org.roketsim.core.math.Quaternions;

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
    }
}
