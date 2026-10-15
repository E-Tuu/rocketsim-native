package org.roketsim.core.materials;

import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Uniform bulk density authority; name whitespace korunur, surface finish türetilmez.
 * @param name malzeme kimliği, boş/yalnız whitespace olamaz
 * @param densityKgM3 finite positive yoğunluk (kg/m³) */
public record BulkMaterial(String name,double densityKgM3) {
    /** @param name source material name
     * @param densityKgM3 source density */
    public BulkMaterial {
        requireFinite(densityKgM3,"density_kg_m3");
        if(densityKgM3<=0) { throw new MaterialValidationError("NON_POSITIVE_DENSITY","density_kg_m3",densityKgM3); }
        // Python str.strip: Java isWhitespace tek başına NBSP/NEL'i kapsamaz.
        if(name.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0x85)) {
            throw new MaterialValidationError("EMPTY_MATERIAL_NAME","name",name);
        }
    }
}
