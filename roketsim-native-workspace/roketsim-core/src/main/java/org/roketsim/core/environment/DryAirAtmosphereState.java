package org.roketsim.core.environment;

/** Python ile aynı, constructor doğrulaması olmayan türetilmiş snapshot.
 * @param temperatureK sıcaklık (K)
 * @param pressurePa basınç (Pa)
 * @param densityKgM3 yoğunluk (kg/m³) */
public record DryAirAtmosphereState(double temperatureK, double pressurePa, double densityKgM3) { }
