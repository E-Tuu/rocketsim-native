package org.roketsim.core.simulation;

import java.util.Locale;

/** Exports only the physics fields absent from the saved B1 traces. */
final class Nat039SnapshotExport {
    public static void main(String[] args) throws ReflectiveOperationException {
        var owner = IntegratedFlightVerificationTest.class;
        var sweeps = owner.getDeclaredMethod("grids");
        sweeps.setAccessible(true);
        var control = owner.getDeclaredMethod("control", long.class, int.class);
        control.setAccessible(true);
        try (var cases = (java.util.stream.Stream<?>) sweeps.invoke(null)) {
            for (Object sweep : cases.toList()) {
                var label = sweep.toString();
                if (!label.equals("L/DIRECT/h=0.02") && !label.equals("L/DIRECT/h=0.01")) continue;
                var factory = owner.getDeclaredMethod("prepared", sweep.getClass());
                factory.setAccessible(true);
                var fixture = (PreparedSourceVehicle3DOF) factory.invoke(null, sweep);
                if (!fixture.configurationId().equals("n37-03c:L:r1"))
                    throw new IllegalStateException("Unexpected fixture " + fixture.configurationId());
                var result = fixture.runUntilGround((RunControl3DOF) control.invoke(null, 20000L, -1)).result();
                if (result.terminationReason() != RunControl3DOF.Status.GROUND || result.failure().isPresent())
                    throw new IllegalStateException("Replay failed " + result.terminationReason() + " " + result.failure());
                String step = label.substring(label.lastIndexOf('=') + 1);
                for (double t : new double[]{1.340, 2.967}) {
                    var sample = result.samples().stream().filter(s -> s.point().timeS() == t).findFirst().orElseThrow();
                    var p = sample.physics();
                    System.out.printf(Locale.ROOT, "NAT039_SNAPSHOT\t%s\t%s\t%s\t%s\t%s\t%s\t%s%n", step, t,
                        sample.point().state().positionWorldM().get(2), sample.point().state().velocityWorldMS().get(2),
                        p.motorThrustState().thrustN(), p.motorThrustState().cumulativeImpulseNS(),
                        p.rocketMassProperties().totalMassKg());
                }
            }
        }
    }
}
