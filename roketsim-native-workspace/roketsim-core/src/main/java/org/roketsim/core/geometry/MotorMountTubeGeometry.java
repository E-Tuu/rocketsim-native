package org.roketsim.core.geometry;

/** Immutable raw tasarım; domain doğrulaması GeometryResolver sınırındadır.
 * @param lengthM coaxial iç mount uzunluğu (m); motor uzunluğu değildir
 * @param innerDiameterM tube iç çapı (m)
 * @param wallThicknessM radial tube kalınlığı (m)
 * @param aftRecessM body tail'den mount end'e ileri recess (m)
 */
public record MotorMountTubeGeometry(double lengthM, double innerDiameterM, double wallThicknessM, double aftRecessM) { }
