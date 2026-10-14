package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.environment.Altitude;
import static org.junit.jupiter.api.Assertions.*;

class AltitudeTest {
    @Test void datumDistinctions() {
        assertEquals(970, Altitude.orthometricFromEllipsoidal(1000, 30));
        assertEquals(1030, Altitude.ellipsoidalFromOrthometric(1000, 30));
        assertEquals(350, Altitude.aglFromOrthometric(1200, 850));
        assertEquals(-50, Altitude.aglFromOrthometric(800, 850));
        assertEquals(1200, Altitude.orthometricFromAgl(350, 850));
        assertEquals(1234.5, Altitude.atmosphericGeometricFromOrthometric(1234.5));
    }
    @ParameterizedTest @ValueSource(doubles={-1000,0,1000,11000,32000,86000,90000})
    void roundTrip(double z) {
        // test_altitude.py: rel=1e-14, abs=1e-12.
        assertEquals(z, Altitude.geometricFromGeopotential(Altitude.geopotentialFromGeometric(z)),
                Math.max(1e-12, Math.abs(z)*1e-14));
    }
    @ParameterizedTest @ValueSource(doubles={-30,0,30})
    void datumRoundTrip(double n) {
        assertEquals(1234.5, Altitude.ellipsoidalFromOrthometric(
                Altitude.orthometricFromEllipsoidal(1234.5,n),n),1234.5e-14);
    }
    @Test void roundedAtmosphereLimitIsNotIdentity() {
        assertEquals(84852.04584490575, Altitude.geopotentialFromGeometric(86000),3e-11);
        assertEquals(85999.95290624202, Altitude.geometricFromGeopotential(84852),3e-11);
    }
    @Test void singularitiesAndOverflow() {
        assertThrows(IllegalArgumentException.class,()->Altitude.geopotentialFromGeometric(-6356766));
        assertThrows(IllegalArgumentException.class,()->Altitude.geometricFromGeopotential(6356766));
        assertThrows(IllegalArgumentException.class,()->Altitude.orthometricFromEllipsoidal(Double.MAX_VALUE,-Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->Altitude.ellipsoidalFromOrthometric(Double.MAX_VALUE,Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->Altitude.aglFromOrthometric(Double.MAX_VALUE,-Double.MAX_VALUE));
        assertThrows(IllegalArgumentException.class,()->Altitude.orthometricFromAgl(Double.MAX_VALUE,Double.MAX_VALUE));
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void nonfinite(double v) {
        assertThrows(IllegalArgumentException.class,()->Altitude.orthometricFromEllipsoidal(v,0));
        assertThrows(IllegalArgumentException.class,()->Altitude.orthometricFromEllipsoidal(0,v));
        assertThrows(IllegalArgumentException.class,()->Altitude.ellipsoidalFromOrthometric(v,0));
        assertThrows(IllegalArgumentException.class,()->Altitude.ellipsoidalFromOrthometric(0,v));
        assertThrows(IllegalArgumentException.class,()->Altitude.aglFromOrthometric(v,0));
        assertThrows(IllegalArgumentException.class,()->Altitude.aglFromOrthometric(0,v));
        assertThrows(IllegalArgumentException.class,()->Altitude.orthometricFromAgl(v,0));
        assertThrows(IllegalArgumentException.class,()->Altitude.orthometricFromAgl(0,v));
        assertThrows(IllegalArgumentException.class,()->Altitude.atmosphericGeometricFromOrthometric(v));
        assertThrows(IllegalArgumentException.class,()->Altitude.geopotentialFromGeometric(v));
        assertThrows(IllegalArgumentException.class,()->Altitude.geometricFromGeopotential(v));
    }
    @ParameterizedTest @ValueSource(doubles={Double.MAX_VALUE,-Double.MAX_VALUE})
    void ratioFirstAvoidsIntermediateOverflow(double value) {
        assertTrue(Double.isFinite(Altitude.geopotentialFromGeometric(value)));
        assertTrue(Double.isFinite(Altitude.geometricFromGeopotential(value)));
    }
}
