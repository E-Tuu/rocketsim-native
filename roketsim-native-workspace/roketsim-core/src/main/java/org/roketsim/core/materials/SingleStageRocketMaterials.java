package org.roketsim.core.materials;

/** Beş explicit, bağımsız material seçimi; default atama yoktur.
 * @param nose nose malzemesi
 * @param body body malzemesi
 * @param fins uniform fin-set malzemesi
 * @param motorMount mount tube malzemesi, motorun kendisi değil
 * @param centeringRings iki identical ring'in ortak malzemesi */
public record SingleStageRocketMaterials(BulkMaterial nose,BulkMaterial body,BulkMaterial fins,
        BulkMaterial motorMount,BulkMaterial centeringRings) { }
