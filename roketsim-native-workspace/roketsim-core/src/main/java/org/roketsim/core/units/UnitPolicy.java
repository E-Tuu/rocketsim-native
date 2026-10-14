package org.roketsim.core.units;

import java.util.Map;

/** SI metadata; dönüşüm veya fizik denklemi uygulamaz. */
public final class UnitPolicy {
    private UnitPolicy() { }
    public static final Map<String, String> CANONICAL_UNITS = Map.ofEntries(
        Map.entry("length", "m"), Map.entry("mass", "kg"), Map.entry("time", "s"),
        Map.entry("velocity", "m/s"), Map.entry("acceleration", "m/s^2"),
        Map.entry("force", "N"), Map.entry("moment", "N*m"), Map.entry("pressure", "Pa"),
        Map.entry("density", "kg/m^3"), Map.entry("temperature", "K"),
        Map.entry("angle", "rad"), Map.entry("angular_rate", "rad/s"),
        Map.entry("angular_accel", "rad/s^2"), Map.entry("area", "m^2"),
        Map.entry("volume", "m^3"), Map.entry("inertia", "kg*m^2"),
        Map.entry("dynamic_viscosity", "Pa*s"), Map.entry("kinematic_viscosity", "m^2/s"));
}
