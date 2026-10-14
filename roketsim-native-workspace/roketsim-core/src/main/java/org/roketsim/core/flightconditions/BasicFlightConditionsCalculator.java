package org.roketsim.core.flightconditions;

import org.roketsim.core.environment.DryAirAtmosphereState;
import org.roketsim.core.environment.DryAirProperties;
import org.roketsim.core.math.Vector;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Yalnız supplied rho/a/nu/L kullanır; drag Reynolds otoritesi değildir. */
public final class BasicFlightConditionsCalculator {
    /** Parametresiz ve stateless calculator. */
    public BasicFlightConditionsCalculator() { }
    /** @param relativeVelocityWorldMS finite 3D WORLD bağıl hız (m/s)
     * @param atmosphereState supplied yoğunluk
     * @param airProperties supplied a ve nu
     * @param referenceLengthM açık karakteristik uzunluk (m), zorunlu çap değildir
     * @return V/M/Re/q; upstream fizik yeniden hesaplanmaz */
    public BasicFlightConditions evaluate(Object relativeVelocityWorldMS, DryAirAtmosphereState atmosphereState,
            DryAirProperties airProperties, double referenceLengthM) {
        Vector relative = Vector.of(relativeVelocityWorldMS,3,"relative_velocity_world_m_s");
        double density = requireFinite(atmosphereState.densityKgM3(),"density_kg_m3");
        double sound = requireFinite(airProperties.speedOfSoundMS(),"speed_of_sound_m_s");
        double kinematic = requireFinite(airProperties.kinematicViscosityM2S(),"kinematic_viscosity_m2_s");
        double length = requireFinite(referenceLengthM,"reference_length_m");
        positive(density,"density_kg_m3");
        positive(sound,"speed_of_sound_m_s");
        positive(kinematic,"kinematic_viscosity_m2_s");
        positive(length,"reference_length_m");
        double speed = requireFinite(hypot3(relative),"airspeed_m_s");
        double mach = speed/sound;
        double reynolds = speed*length/kinematic;
        double pressure = .5*density*speed*speed;
        result(speed,speed,"airspeed_m_s");
        result(mach,speed,"mach");
        result(reynolds,speed,"reynolds");
        result(pressure,speed,"dynamic_pressure_Pa");
        return new BasicFlightConditions(speed,mach,reynolds,pressure);
    }
    // Python'ın tam 3D hypot contract'ı: binary scaling ve compensated sum/sqrt.
    // Nested Math.hypot, (-12,3,-4) için exact 13'ü korumaz. Tolerans/epsilon yoktur.
    private static double hypot3(Vector vector) {
        double maximum = Math.max(Math.abs(vector.get(0)),Math.max(Math.abs(vector.get(1)),Math.abs(vector.get(2))));
        if (maximum==0.0) { return 0.0; }
        int exponent = Math.getExponent(maximum);
        double sum=0.0, correction=0.0;
        for (int i=0;i<3;i++) {
            double component=Math.scalb(vector.get(i),-exponent);
            double square=component*component;
            double next=sum+square;
            double reconstructed=next-sum;
            correction += (sum-(next-reconstructed))+(square-reconstructed)
                    + Math.fma(component,component,-square);
            sum=next;
        }
        double root=Math.sqrt(sum);
        double corrected=root+(Math.fma(-root,root,sum)+correction)/(2*root);
        return Math.scalb(corrected,exponent);
    }
    private static void positive(double value, String name) {
        if (value<=0) { throw new FlightConditionsDomainError(name,value); }
    }
    private static void result(double value, double speed, String name) {
        requireFinite(value,name);
        if (value<0 || (speed>0 && value==0)) {
            throw new IllegalArgumentException(name+" violates non-negative/positive-speed invariant; got "+value);
        }
    }
}
