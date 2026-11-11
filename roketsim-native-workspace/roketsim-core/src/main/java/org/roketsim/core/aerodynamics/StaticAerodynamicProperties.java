package org.roketsim.core.aerodynamics;
/** Accepted nose/fin-set and weighted total static aerodynamic properties. */
public record StaticAerodynamicProperties(StaticAeroContribution nose,StaticAeroContribution finSet,
        double totalCnaPerRad,double cpXGeoM,StaticStabilityModelProfile modelProfile) { }
