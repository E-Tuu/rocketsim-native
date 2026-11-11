package org.roketsim.core.flightconditions;

/** Python ile aynı dört türetilmiş scalar; constructor domain doğrulaması yoktur.
 * @param airspeedMS tam 3D bağıl hava hızı (m/s)
 * @param mach Mach (boyutsuz)
 * @param reynolds Reynolds (boyutsuz)
 * @param dynamicPressurePa dynamic basınç (Pa) */
public record BasicFlightConditions(double airspeedMS, double mach, double reynolds, double dynamicPressurePa) { }
