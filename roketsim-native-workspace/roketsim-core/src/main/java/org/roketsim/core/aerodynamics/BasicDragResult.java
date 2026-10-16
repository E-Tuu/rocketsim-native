package org.roketsim.core.aerodynamics;
/** Seven zero-AoA coefficient terms and their source diagnostics; no force. */
public record BasicDragResult(double reynoldsNumber,double frictionEvaluationReynoldsNumber,
        SkinFrictionEvaluation noseSkinFriction,SkinFrictionEvaluation bodySkinFriction,
        SkinFrictionEvaluation finSkinFriction,double noseFrictionCd,double bodyFrictionCd,
        double finFrictionCd,double nosePressureCd,double finLeadingEdgePressureCd,
        double finTrailingEdgeBaseCd,double airframeBaseCd,double totalCd0,BasicDragModelProfile modelProfile) {
    public double frictionCd() { return noseFrictionCd+bodyFrictionCd+finFrictionCd; }
    public double pressureCd() { return nosePressureCd+finLeadingEdgePressureCd; }
    public double baseCd() { return finTrailingEdgeBaseCd+airframeBaseCd; }
}
