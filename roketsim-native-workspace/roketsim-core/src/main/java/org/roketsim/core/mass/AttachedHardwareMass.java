package org.roketsim.core.mass;

/** Explicit attached hardware input; numeric validation belongs to NAT-023, not this record.
 * @param componentId exact, nonblank identity within one hardware inventory
 * @param massKg explicitly supplied mass (kg)
 * @param cgXGeoM nose-tip-origin, aft-positive axial coordinate (m)
 */
public record AttachedHardwareMass(String componentId,double massKg,double cgXGeoM) {
    public AttachedHardwareMass {
        if(componentId==null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId must not be null or blank");
        }
    }
}
