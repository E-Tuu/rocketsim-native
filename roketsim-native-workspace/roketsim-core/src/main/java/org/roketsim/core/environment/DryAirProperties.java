package org.roketsim.core.environment;

/** Yalnız a/mu/nu/gamma snapshot; constructor domain doğrulaması yapmaz.
 * @param speedOfSoundMS ses hızı (m/s)
 * @param dynamicViscosityPaS dynamic viskozite (Pa s)
 * @param kinematicViscosityM2S kinematic viskozite (m²/s)
 * @param specificHeatRatio gamma (boyutsuz) */
public record DryAirProperties(double speedOfSoundMS, double dynamicViscosityPaS,
        double kinematicViscosityM2S, double specificHeatRatio) { }
