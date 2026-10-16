package org.roketsim.core.propulsion;
/** Offline metadata; optional source references represent explicit absence. */
public record MotorDataProvenance(String manufacturerSource, String certificationSource, String thrustCurveSource,
        String normalizationNote, String massCurveSource, String cgCurveSource) {
    public MotorDataProvenance {
        String[] values={manufacturerSource,certificationSource,thrustCurveSource,normalizationNote,massCurveSource,cgCurveSource};
        String[] names={"manufacturer_source","certification_source","thrust_curve_source","normalization_note","mass_curve_source","cg_curve_source"};
        for(int i=0;i<6;i++) {
            if(i>=4 && values[i]==null) { continue; }
            if(blank(values[i])) { throw new MotorValidationError("INVALID_PROVENANCE",names[i],values[i]); }
        }
    }
    static boolean blank(String text) {
        return text==null || text.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0x85);
    }
}
