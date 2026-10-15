package org.roketsim.core.geometry;

/** Immutable raw tasarım; domain doğrulaması GeometryResolver sınırındadır.
 * @param axialThicknessM tam iki identical ring'in her birinin axial kalınlığı (m)
 */
public record CenteringRingPairGeometry(double axialThicknessM) { }
