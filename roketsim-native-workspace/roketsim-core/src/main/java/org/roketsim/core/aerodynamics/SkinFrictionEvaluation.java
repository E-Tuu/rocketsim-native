package org.roketsim.core.aerodynamics;
/** Smooth/rough candidates and selected branch, as in Python drag.py. */
public record SkinFrictionEvaluation(double smoothCorrectedCf,double roughnessCorrectedCf,
        double selectedCf,SkinFrictionBranch selectedBranch) { }
