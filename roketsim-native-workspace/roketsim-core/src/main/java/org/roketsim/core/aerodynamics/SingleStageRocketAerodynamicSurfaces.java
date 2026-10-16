package org.roketsim.core.aerodynamics;
/** Three mandatory independent selections; immutable finish instances may be shared. */
public record SingleStageRocketAerodynamicSurfaces(SurfaceFinish nose,SurfaceFinish body,SurfaceFinish fins) { }
