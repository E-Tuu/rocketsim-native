package org.roketsim.core.simulation;
/** Completed history, lifecycle reason and successfully computed candidate count only. */
public record SimulationExecution3DOF(RecordedFlightData3DOF recordedData,SimulationTerminationReason terminationReason,int stepsPerformed) { }
