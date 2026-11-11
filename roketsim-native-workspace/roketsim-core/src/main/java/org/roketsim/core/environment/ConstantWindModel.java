package org.roketsim.core.environment;

import org.roketsim.core.math.Vector;

/** WORLD ENU TOWARD air-mass hızı; normalization ve hız sınırı yok. */
public final class ConstantWindModel {
    private final Vector airmassVelocityWorldMS;
    /** @param airmassVelocityWorldMS mandatory finite 3D hız; J-001 vector giriş temsilleri */
    public ConstantWindModel(Object airmassVelocityWorldMS) {
        this.airmassVelocityWorldMS = Vector.of(airmassVelocityWorldMS,3,"airmass_velocity_world_m_s");
    }
    /** @return model verisini paylaşmayan immutable hız (m/s) */
    public Vector evaluate() { return Vector.of(airmassVelocityWorldMS,3,"airmass_velocity_world_m_s"); }
}
