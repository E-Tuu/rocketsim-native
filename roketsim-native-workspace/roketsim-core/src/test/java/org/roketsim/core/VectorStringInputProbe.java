package org.roketsim.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.roketsim.core.math.Vector;

/** AUD-002: text is passed unchanged to the actual public Vector factory. */
public final class VectorStringInputProbe {
    private VectorStringInputProbe() { }

    public static void main(String[] args) {
        for (String encoded : args) {
            String text = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            try {
                System.out.println("OK\t" + Double.toString(Vector.of(new String[]{text}).get(0)));
            } catch (IllegalArgumentException error) {
                System.out.println("ERROR\t" + error.getClass().getSimpleName() + "\t"
                        + Base64.getEncoder().encodeToString(error.getMessage().getBytes(StandardCharsets.UTF_8)));
            }
        }
    }
}
