package org.roketsim.core.propulsion;
import java.util.List;
/** Lazy reference adapters: no eager reads, upstream revalidation, numerical conversion or fallback. */
public final class MotorPhysicsInputs {
    private MotorPhysicsInputs() { }
    public static MotorPhysicsInput fromLegacyMotor(MotorDefinition motor) {return motor==null?null:new LegacyMotorView(motor);}
    public static InstalledMotorPhysicsInput fromLegacyInstallation(MotorInstallation installation) {return new LegacyInstallationView(installation);}
    static final class LegacyMotorView implements MotorPhysicsInput {
        final MotorDefinition source;
        LegacyMotorView(MotorDefinition source) {this.source=source;}
        public double diameterM() {return source.diameterM();}
        public double lengthM() {return source.lengthM();}
        public double initialMassKg() {return source.initialMassKg();}
        public double propellantMassKg() {return source.propellantMassKg();}
        public List<ThrustSample> thrustCurve() {return source.thrustCurve();}
        public List<MotorMassSample> massCurve() {return source.massCurve();}
        public List<MotorCgSample> cgCurve() {return source.cgCurve();}
    }
    static final class LegacyInstallationView implements InstalledMotorPhysicsInput {
        final MotorInstallation source;
        LegacyInstallationView(MotorInstallation source) {this.source=source;}
        public MotorPhysicsInput motor() {return fromLegacyMotor(source.motor());}
        public double motorFrontXGeoM() {return source.motorFrontXGeoM();}
    }
}
