package org.roketsim.core.environment;

import org.roketsim.core.math.Vector;

/** Konum/zaman bağımsız kabul edilmiş standard-g; WORLD ENU aşağı yön. */
public final class ConstantGravityModel {
    /** Parametresiz; custom gravity politikası yok. */
    public ConstantGravityModel() { }
    /** @return bağımsız immutable WORLD ivmesi (m/s²) */
    public Vector evaluate() {
        return Vector.of(new double[]{0,0,-USStandardAtmosphere1976Lower.STANDARD_GRAVITY_M_S2},3,"gravity_world_m_s2");
    }
}
