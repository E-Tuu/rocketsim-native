package org.roketsim.core.geometry;

/** Immutable raw tasarım; domain doğrulaması GeometryResolver sınırındadır.
 * @param lengthM nose tip'ten base'e axial uzunluk (m)
 * @param constructionMode açık NoseConstructionMode; raw invalid selector resolver'da reddedilir
 * @param wallThicknessM lateral yüzeye NORMAL shell kalınlığı (m); solid için null
 */
public record ConicalNoseGeometry(double lengthM, Object constructionMode, Double wallThicknessM) { }
