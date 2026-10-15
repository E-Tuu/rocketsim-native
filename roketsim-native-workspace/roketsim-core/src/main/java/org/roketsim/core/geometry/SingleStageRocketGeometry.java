package org.roketsim.core.geometry;

/** Immutable raw tasarım; domain doğrulaması GeometryResolver sınırındadır.
 * @param airframeDiameterM nose base/body outer diameter için tek otorite (m)
 * @param nose conical nose, tip x_geo=0
 * @param body nose base'inden başlayan cylindrical body
 * @param fins tek trapezoidal fin seti
 * @param motorAttachment rocket-side attachment; seçilmiş motor değildir
 * @param referenceGeometryPolicy açık ReferenceGeometryPolicy selector
 */
public record SingleStageRocketGeometry(double airframeDiameterM, ConicalNoseGeometry nose, CylindricalBodyGeometry body, TrapezoidalFinSetGeometry fins, MotorAttachmentGeometry motorAttachment, Object referenceGeometryPolicy) { }
