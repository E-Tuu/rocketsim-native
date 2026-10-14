package org.roketsim.core;

import java.util.Arrays;
import org.roketsim.core.math.Quaternion;
import org.roketsim.core.math.Quaternions;
import org.roketsim.core.math.Vector;

/** Test-only Python/Java parity giriş noktası; production artifact'a girmez. */
public final class QuaternionParityProbe {
    public static void main(String[] args) {
        try {
            Object result=switch(args[0]) {
                case "as" -> Quaternion.of(parse(args[1]));
                case "norm" -> Quaternions.norm(Quaternion.of(parse(args[1])));
                case "normalize" -> Quaternions.normalize(Quaternion.of(parse(args[1])),number(args[2]));
                case "conjugate" -> Quaternions.conjugate(Quaternion.of(parse(args[1])));
                case "multiply" -> Quaternions.multiply(Quaternion.of(parse(args[1])),Quaternion.of(parse(args[2])));
                case "inverse" -> Quaternions.inverse(Quaternion.of(parse(args[1])),number(args[2]));
                case "axis" -> Quaternions.fromAxisAngle(Vector.of(parse(args[1])),number(args[2]),number(args[3]));
                case "body" -> Quaternions.rotateBodyToWorld(Quaternion.of(parse(args[1])),Vector.of(parse(args[2])),number(args[3]));
                case "world" -> Quaternions.rotateWorldToBody(Quaternion.of(parse(args[1])),Vector.of(parse(args[2])),number(args[3]));
                case "matrix" -> Quaternions.toRotationMatrix(Quaternion.of(parse(args[1])),number(args[2]));
                case "increment" -> Quaternions.applyIncrementLeft(Quaternion.of(parse(args[1])),Quaternion.of(parse(args[2])),number(args[3]));
                case "trig" -> new double[] {Math.sin(number(args[1])),Math.cos(number(args[1])),
                    StrictMath.sin(number(args[1])),StrictMath.cos(number(args[1]))};
                default -> throw new AssertionError(args[0]);
            };
            System.out.print("OK");
            double[] values;
            if(result instanceof Quaternion q) { values=q.toArray(); }
            else if(result instanceof Vector v) { values=v.toArray(); }
            else if(result instanceof double[][] matrix) { values=Arrays.stream(matrix).flatMapToDouble(Arrays::stream).toArray(); }
            else if(result instanceof double[] array) { values=array; }
            else { values=new double[] {(Double)result}; }
            for(double value:values) { System.out.print("\t"+value); }
            System.out.println();
        } catch(IllegalArgumentException exception) {
            System.out.println("ERROR\t"+exception.getMessage());
        }
    }
    private static double number(String value) { return Double.parseDouble(value); }
    private static double[] parse(String text) {
        if(text.isEmpty()) { return new double[0]; }
        return Arrays.stream(text.split(",")).mapToDouble(Double::parseDouble).toArray();
    }
}
