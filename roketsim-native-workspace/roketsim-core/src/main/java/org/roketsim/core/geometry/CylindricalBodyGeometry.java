package org.roketsim.core.geometry;

/** Immutable raw tasarım; domain doğrulaması GeometryResolver sınırındadır.
 * @param lengthM hollow cylinder axial uzunluğu (m)
 * @param wallThicknessM radial duvar kalınlığı (m); solid body seçeneği yok
 */
public record CylindricalBodyGeometry(double lengthM, double wallThicknessM) { }
