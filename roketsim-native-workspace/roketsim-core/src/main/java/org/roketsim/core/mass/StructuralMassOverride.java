package org.roketsim.core.mass;

import java.util.Optional;

/** Measurements for one existing structural contribution. CG uses nose-tip x_geo in metres. */
public record StructuralMassOverride(
        String componentId, Optional<Measurement> massKg, Optional<Measurement> cgXGeoM) {
    /** Value in the field's units and the caller's measurement evidence reference. */
    public record Measurement(double value, String sourceReference) {}
}
