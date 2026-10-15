package org.roketsim.core.geometry;

/** Immutable raw tasarım; domain doğrulaması GeometryResolver sınırındadır.
 * @param mountTube rocket-side mount tube geometrisi
 * @param centeringRings body/mount radial ölçülerini tüketen iki ring
 * @param motorOverhangM signed aft reference minus mount end (m); positive aft
 */
public record MotorAttachmentGeometry(MotorMountTubeGeometry mountTube, CenteringRingPairGeometry centeringRings, double motorOverhangM) { }
