package org.roketsim.core.environment;

import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Python altitude.py dönüşümleri; WORLD z mutlak yükseklik değildir. */
public final class Altitude {
    /** USSA76 etkin geopotential yarıçapı; WGS84 yarıçapı değildir (m). */
    public static final double USSA76_GEOPOTENTIAL_EARTH_RADIUS_M = 6356766.0;
    private Altitude() { }

    /** @param ellipsoidalHeightM ellipsoid yüksekliği (m)
     * @param geoidUndulationM uyumlu datum'daki işaretli N (m)
     * @return orthometric yükseklik (m) */
    public static double orthometricFromEllipsoidal(double ellipsoidalHeightM, double geoidUndulationM) {
        requireFinite(ellipsoidalHeightM, "ellipsoidal_height_m");
        requireFinite(geoidUndulationM, "geoid_undulation_m");
        return requireFinite(ellipsoidalHeightM - geoidUndulationM, "orthometric_height_m");
    }
    /** @param orthometricHeightM orthometric yükseklik (m)
     * @param geoidUndulationM uyumlu datum'daki N (m)
     * @return ellipsoid yüksekliği (m) */
    public static double ellipsoidalFromOrthometric(double orthometricHeightM, double geoidUndulationM) {
        requireFinite(orthometricHeightM, "orthometric_height_m");
        requireFinite(geoidUndulationM, "geoid_undulation_m");
        return requireFinite(orthometricHeightM + geoidUndulationM, "ellipsoidal_height_m");
    }
    /** @param orthometricHeightM orthometric yükseklik (m)
     * @param groundElevationM aynı datum'daki yer yüksekliği (m)
     * @return işaretli AGL (m) */
    public static double aglFromOrthometric(double orthometricHeightM, double groundElevationM) {
        requireFinite(orthometricHeightM, "orthometric_height_m");
        requireFinite(groundElevationM, "ground_elevation_m");
        return requireFinite(orthometricHeightM - groundElevationM, "agl_altitude_m");
    }
    /** @param aglAltitudeM işaretli AGL (m)
     * @param groundElevationM uyumlu datum'daki yer yüksekliği (m)
     * @return orthometric yükseklik (m) */
    public static double orthometricFromAgl(double aglAltitudeM, double groundElevationM) {
        requireFinite(aglAltitudeM, "agl_altitude_m");
        requireFinite(groundElevationM, "ground_elevation_m");
        return requireFinite(aglAltitudeM + groundElevationM, "orthometric_height_m");
    }
    /** Kabul edilen MODEL DECISION Z_atm := H_orth; evrensel datum eşitliği değildir.
     * @param orthometricHeightM orthometric yükseklik (m)
     * @return atmosfer geometric yüksekliği (m) */
    public static double atmosphericGeometricFromOrthometric(double orthometricHeightM) {
        return requireFinite(orthometricHeightM, "orthometric_height_m");
    }
    /** @param atmosphericGeometricAltitudeM atmosfer geometric Z (m)
     * @return geopotential H (m), atmosfer domain sınırlaması uygulanmaz */
    public static double geopotentialFromGeometric(double atmosphericGeometricAltitudeM) {
        requireFinite(atmosphericGeometricAltitudeM, "atmospheric_geometric_altitude_m");
        double denominator = USSA76_GEOPOTENTIAL_EARTH_RADIUS_M + atmosphericGeometricAltitudeM;
        if (denominator == 0.0) {
            throw new IllegalArgumentException("atmospheric_geometric_altitude_m produces a zero geopotential denominator");
        }
        return requireFinite(USSA76_GEOPOTENTIAL_EARTH_RADIUS_M
                * (atmosphericGeometricAltitudeM / denominator), "geopotential_height_m");
    }
    /** @param geopotentialHeightM geopotential H (m)
     * @return atmosfer geometric Z (m), atmosfer domain sınırlaması uygulanmaz */
    public static double geometricFromGeopotential(double geopotentialHeightM) {
        requireFinite(geopotentialHeightM, "geopotential_height_m");
        double denominator = USSA76_GEOPOTENTIAL_EARTH_RADIUS_M - geopotentialHeightM;
        if (denominator == 0.0) {
            throw new IllegalArgumentException("geopotential_height_m produces a zero geometric denominator");
        }
        return requireFinite(USSA76_GEOPOTENTIAL_EARTH_RADIUS_M
                * (geopotentialHeightM / denominator), "atmospheric_geometric_altitude_m");
    }
}
