package org.roketsim.core;

import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;

/** Yalnız doğrulama aracı; production JAR'a girmez. */
public final class ParityProbe {
    public static void main(String[] args) {
        double[] a = parse(args[0]), b = parse(args[1]);
        Vector left = Vector.of(a), right = Vector.of(b);
        System.out.println(Vectors.magnitude(left));
        System.out.println(Vectors.dot(left,right));
        for (double value : Vectors.normalize(left,0).toArray()) { System.out.println(value); }
        if (a.length==3) { for (double value : Vectors.cross3(left,right).toArray()) { System.out.println(value); } }
    }
    private static double[] parse(String input) {
        String[] tokens=input.split(","); double[] values=new double[tokens.length];
        for(int i=0;i<tokens.length;i++) { values[i]=Double.parseDouble(tokens[i]); }
        return values;
    }
}
