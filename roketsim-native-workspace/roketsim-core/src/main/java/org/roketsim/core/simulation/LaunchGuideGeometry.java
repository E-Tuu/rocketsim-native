package org.roketsim.core.simulation;

import org.roketsim.core.math.Vector;

/** Straight guide coordinates: zero is the lower usable guide end; top is caller supplied.
 * A nominal comparison input is deliberately not physical usable-length evidence.
 */
public record LaunchGuideGeometry(String datumId,Kind kind,double topCoordinateM,String sourceReference) {
    public enum Kind { IDEAL_TWO_BUTTON_RAIL, NOMINAL_ROD_INPUT, LAUNCH_ROD_RESERVED, LAUNCH_TOWER_RESERVED }
    public LaunchGuideGeometry {
        LaunchGuideError.text(datumId,"guide.datumId"); LaunchGuideError.required(kind,"guide.kind");
        LaunchGuideError.text(sourceReference,"guide.sourceReference");
        LaunchGuideError.finite(topCoordinateM,"guide.topCoordinateM");
        if(topCoordinateM<=0) throw new LaunchGuideError("INVALID_GUIDE_GEOMETRY","guide.topCoordinateM",topCoordinateM);
    }
    /** Explicit ENU adapter only. The returned vector must be supplied to LaunchConditions3DOF.
     * Angles are elevation above horizontal and clockwise azimuth from North, in degrees.
     */
    public static Vector directionFromAngles(double elevationDeg,double azimuthDeg) {
        LaunchGuideError.finite(elevationDeg,"elevationDeg"); LaunchGuideError.finite(azimuthDeg,"azimuthDeg");
        if(elevationDeg<=0 || elevationDeg>90) throw new LaunchGuideError("UNSUPPORTED_LAUNCH_ELEVATION","elevationDeg",elevationDeg);
        double e=Math.toRadians(elevationDeg),az=Math.toRadians(azimuthDeg);
        return Vector.of(new double[]{Math.cos(e)*Math.sin(az),Math.cos(e)*Math.cos(az),Math.sin(e)});
    }
}
