package org.roketsim.core.propulsion;
import org.roketsim.core.geometry.ResolvedRocketGeometry;
/** Stateless nominal-fit resolver; no repositioning or manufacturing tolerance. */
public final class MotorInstallationResolver {
    public MotorInstallation resolve(ResolvedRocketGeometry geometry,MotorDefinition motor) {
        double aft=geometry.motorAftReferenceXGeoM();
        finite(aft,"INVALID_MOTOR_AFT_POSITION","motor_aft_x_geo_m");
        double front=aft-motor.lengthM();
        finite(front,"INVALID_MOTOR_FRONT_POSITION","motor_front_x_geo_m");
        double mountDiameter=geometry.motorMountInnerDiameterM();
        double motorDiameter=motor.diameterM();
        double clearance=(mountDiameter-motorDiameter)/2.0;
        finite(clearance,"INVALID_RADIAL_CLEARANCE","nominal_radial_clearance_m");
        if(motorDiameter>mountDiameter) { fail("MOTOR_DIAMETER_EXCEEDS_MOUNT","diameter_m",motorDiameter); }
        double start=geometry.motorMountStartXGeoM(),end=geometry.motorMountEndXGeoM();
        finite(start,"INVALID_AXIAL_ENGAGEMENT","motor_mount_start_x_geo_m");
        finite(end,"INVALID_AXIAL_ENGAGEMENT","motor_mount_end_x_geo_m");
        if(front<start) { fail("MOTOR_FRONT_BEFORE_MOUNT","motor_front_x_geo_m",front); }
        double engagement=Math.min(aft,end)-Math.max(front,start);
        finite(engagement,"INVALID_AXIAL_ENGAGEMENT","axial_engagement_length_m");
        if(engagement<=0) { fail("NO_POSITIVE_MOTOR_ENGAGEMENT","axial_engagement_length_m",engagement); }
        return new MotorInstallation(motor,front,aft,clearance,engagement);
    }
    private static void finite(double value,String code,String field) { if(!Double.isFinite(value)) { fail(code,field,value); } }
    private static void fail(String code,String field,double value) { throw new MotorInstallationError(code,field,value); }
}
