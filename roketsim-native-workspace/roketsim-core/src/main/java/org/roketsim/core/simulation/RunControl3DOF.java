package org.roketsim.core.simulation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One run's cooperative cancellation and synchronous lightweight observers.
 * Only requestCancellation/isCancellationRequested are intended for concurrent calls.
 * Observers run on the simulation thread: they should return promptly and may request cancellation.
 * Monitoring thresholds are explicit operational settings, never physical validity limits. */
public final class RunControl3DOF {
    public record MonitoringPolicy(long progressIntervalNanos,long wallWarningIntervalNanos,
            double airborneWarningIntervalS,long historyWarningEverySamples) {
        public MonitoringPolicy {
            if(progressIntervalNanos<=0 || wallWarningIntervalNanos<=0
                    || !Double.isFinite(airborneWarningIntervalS) || airborneWarningIntervalS<=0 || historyWarningEverySamples<=0)
                throw new IllegalArgumentException("monitoring intervals must be finite and positive");
        }
    }
    public enum Status { RUNNING, GROUND, CANCELLED, UNSUPPORTED, MODEL_DOMAIN_FAILURE,
        NUMERICAL_FAILURE, RESOURCE_FAILURE, MONITORING_FAILURE }
    public record Progress(double simulatedFlightTimeS,long elapsedComputationNanos,long acceptedSegments,
            double groundRelativeAltitudeM,double verticalVelocityWorldMS,FlightPhase3DOF flightPhase,
            RecoveryFlightResult3DOF.State recoveryState,Status executionStatus) { }
    public record Warning(String code,String message,Progress observation,long recordedSamples) { }
    final MonitoringPolicy policy;
    final Consumer<Progress> progress;
    final Consumer<Warning> warnings;
    final LongSupplier nanoClock;
    final Consumer<String> checkpointObserver; // Package-private deterministic V&V hook; no physical callback.
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicBoolean claimed=new AtomicBoolean();
    public RunControl3DOF(MonitoringPolicy policy,Consumer<Progress> progress,Consumer<Warning> warnings) {
        this(policy,progress,warnings,System::nanoTime);
    }
    RunControl3DOF(MonitoringPolicy policy,Consumer<Progress> progress,Consumer<Warning> warnings,LongSupplier nanoClock) {
        this(policy,progress,warnings,nanoClock,point->{});
    }
    RunControl3DOF(MonitoringPolicy policy,Consumer<Progress> progress,Consumer<Warning> warnings,
            LongSupplier nanoClock,Consumer<String> checkpointObserver) {
        this.checkpointObserver=Objects.requireNonNull(checkpointObserver);
        this.policy=Objects.requireNonNull(policy);this.progress=Objects.requireNonNull(progress);
        this.warnings=Objects.requireNonNull(warnings);this.nanoClock=Objects.requireNonNull(nanoClock);
    }
    public void requestCancellation() {cancelled.set(true);}
    public boolean isCancellationRequested() {return cancelled.get();}
    void claim() {
        if(!claimed.compareAndSet(false,true))throw new IllegalArgumentException("RunControl3DOF is single-run; create fresh control for another execution");
    }
}
