package org.roketsim.core.mass;

/** Beş structural katkı; gerçek motor, inertia veya total rocket mass değildir.
 * @param nose nose katkısı
 * @param body body katkısı
 * @param fins bütün fin seti
 * @param motorMount mount tube katkısı
 * @param centeringRings iki ring'in birleşik katkısı
 * @param structureMassKg structural toplam (kg)
 * @param structureCgXGeoM structural axial CG (m) */
public record StructuralMassProperties(ComponentMassProperties nose,ComponentMassProperties body,
        ComponentMassProperties fins,ComponentMassProperties motorMount,ComponentMassProperties centeringRings,
        double structureMassKg,double structureCgXGeoM) { }
