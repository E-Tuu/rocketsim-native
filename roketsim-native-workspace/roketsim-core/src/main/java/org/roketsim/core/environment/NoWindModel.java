package org.roketsim.core.environment;

import org.roketsim.core.math.Vector;

/** WORLD ENU hava kütlesi hızını exact sıfır kabul eden açık model. */
public final class NoWindModel {
    /** Parametresiz no-wind modeli. */
    public NoWindModel() { }
    /** @return bağımsız immutable sıfır air-mass hızı (m/s) */
    public Vector evaluate() { return Vector.of(new double[]{0,0,0},3,"airmass_velocity_world_m_s"); }
}
