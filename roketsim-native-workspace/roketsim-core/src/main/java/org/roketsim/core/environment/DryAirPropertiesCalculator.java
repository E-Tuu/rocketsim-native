package org.roketsim.core.environment;

import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Python NAT-009D kuru hava denklemleri; pressure okunmaz, rho yeniden hesaplanmaz. */
public final class DryAirPropertiesCalculator {
    /** Kabul edilmiş fixed dry-air gamma. */
    public static final double DRY_AIR_SPECIFIC_HEAT_RATIO = 1.40;
    /** Sutherland beta, kg/(m s sqrt(K)). */
    public static final double SUTHERLAND_BETA = 1.458e-6;
    /** Sutherland sıcaklık sabiti (K). */
    public static final double SUTHERLAND_CONSTANT_K = 110.4;
    /** Parametresiz ve stateless hesaplayıcı. */
    public DryAirPropertiesCalculator() { }
    /** @param atmosphereState supplied T/rho snapshot
     * @return a/mu/nu/gamma; altitude domain veya humidity modeli uygulanmaz */
    public DryAirProperties evaluate(DryAirAtmosphereState atmosphereState) {
        double temperature = requireFinite(atmosphereState.temperatureK(), "temperature_K");
        double density = requireFinite(atmosphereState.densityKgM3(), "density_kg_m3");
        if (temperature <= 0.0) { throw new AirPropertiesDomainError("temperature_K",temperature); }
        if (density <= 0.0) { throw new AirPropertiesDomainError("density_kg_m3",density); }
        double rootTemperature = Math.sqrt(temperature);
        double sound = Math.sqrt(DRY_AIR_SPECIFIC_HEAT_RATIO
                * USStandardAtmosphere1976Lower.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K) * rootTemperature;
        double viscosity = SUTHERLAND_BETA * rootTemperature * (temperature / (temperature + SUTHERLAND_CONSTANT_K));
        double kinematic = viscosity / density;
        positive(sound, "speed_of_sound_m_s");
        positive(viscosity, "dynamic_viscosity_Pa_s");
        positive(kinematic, "kinematic_viscosity_m2_s");
        return new DryAirProperties(sound, viscosity, kinematic, DRY_AIR_SPECIFIC_HEAT_RATIO);
    }
    private static void positive(double value, String name) {
        requireFinite(value,name);
        if (value <= 0.0) { throw new IllegalArgumentException(name + " must be positive; got " + value); }
    }
}
