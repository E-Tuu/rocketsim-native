package org.roketsim.core.propulsion;
import java.util.List;

/** Frozen accepted NAT-011C.1 catalog. Source strings are offline provenance metadata.
 * The explicit zero sample is this catalog entry's decision, not an insertion rule. */
public final class MotorCatalogs {
    private MotorCatalogs() { }
    public static final MotorDefinition AEROTECH_F50_4T = new MotorDefinition(
        "aerotech_f50_4t", "AeroTech", "F50-4T", "F50T",
        MotorType.SINGLE_USE, "Blue Thunder",
        0.029, 0.098, 0.0849, 0.0379, 4,
        List.of(new ThrustSample(0, 0),
                new ThrustSample(0.012, 51.377),
                new ThrustSample(0.023, 61.197),
                new ThrustSample(0.026, 66.117),
                new ThrustSample(0.044, 66.564),
                new ThrustSample(0.082, 69.685),
                new ThrustSample(0.152, 73.264),
                new ThrustSample(0.208, 75.053),
                new ThrustSample(0.237, 77.279),
                new ThrustSample(0.254, 76.832),
                new ThrustSample(0.272, 77.726),
                new ThrustSample(0.307, 77.726),
                new ThrustSample(0.33, 76.832),
                new ThrustSample(0.336, 78.621),
                new ThrustSample(0.342, 76.832),
                new ThrustSample(0.354, 79.59),
                new ThrustSample(0.363, 76.385),
                new ThrustSample(0.371, 77.756),
                new ThrustSample(0.395, 76.385),
                new ThrustSample(0.447, 75.937),
                new ThrustSample(0.523, 73.711),
                new ThrustSample(0.652, 68.344),
                new ThrustSample(0.81, 60.302),
                new ThrustSample(0.828, 62.539),
                new ThrustSample(0.836, 58.076),
                new ThrustSample(0.901, 53.603),
                new ThrustSample(1.079, 37.074),
                new ThrustSample(1.158, 29.48),
                new ThrustSample(1.196, 25.464),
                new ThrustSample(1.246, 16.976),
                new ThrustSample(1.301, 9.38),
                new ThrustSample(1.43, 0)),
        new MotorCertificationReference(76.83,53.73,79.59,1.43),
        new MotorDataProvenance("AeroTech F50-4T product 65004: https://aerotech-rocketry.com/products/product_24cdff8c-59d9-6dcb-a66e-8bc9babb0c2c","NAR S&T AEROTECH F50, tested 1995-09-03, updated 1/98, page 1 (4 s variant): https://www.thrustcurve.org/motors/cert/60c63bfcb5bc370004713e82/F50.pdf","NAR-published data RASP transcription, 2000-07-04, page 2: https://www.thrustcurve.org/motors/cert/60c63bfcb5bc370004713e82/F50.pdf",
            "Native canonical curve prepends (0.0 s, 0.0 N); all published thrust samples are otherwise preserved.",null,null),null,null);
    public static final MotorCatalog DEMO_MOTOR_CATALOG = new MotorCatalog(List.of(AEROTECH_F50_4T));
}
