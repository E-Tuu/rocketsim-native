package org.roketsim.core.mass;

import org.roketsim.core.materials.BulkMaterial;

/** Uniform structural component sonucu; constructor'a yeni validation eklenmez.
 * @param material seçilen immutable material provenance
 * @param massKg component kütlesi (kg)
 * @param cgXGeoM nose-tip kaynaklı axial CG (m) */
public record ComponentMassProperties(BulkMaterial material,double massKg,double cgXGeoM) { }
