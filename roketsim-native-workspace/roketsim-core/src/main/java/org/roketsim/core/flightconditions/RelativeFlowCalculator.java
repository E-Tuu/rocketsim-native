package org.roketsim.core.flightconditions;

import org.roketsim.core.math.Vector;

/** WORLD ENU V_relative = V_rocket - V_airmass; wind veya norm hesaplamaz. */
public final class RelativeFlowCalculator {
    /** Parametresiz ve stateless calculator. */
    public RelativeFlowCalculator() { }
    /** @param rocketVelocityWorldMS finite 3D roket hızı (m/s)
     * @param airmassVelocityWorldMS finite 3D TOWARD hava kütlesi hızı (m/s)
     * @return bağımsız immutable relative hız (m/s) */
    public Vector evaluate(Object rocketVelocityWorldMS, Object airmassVelocityWorldMS) {
        Vector rocket = Vector.of(rocketVelocityWorldMS,3,"rocket_velocity_world_m_s");
        Vector air = Vector.of(airmassVelocityWorldMS,3,"airmass_velocity_world_m_s");
        return Vector.of(new double[]{rocket.get(0)-air.get(0),rocket.get(1)-air.get(1),rocket.get(2)-air.get(2)},
                3,"relative_velocity_world_m_s");
    }
}
