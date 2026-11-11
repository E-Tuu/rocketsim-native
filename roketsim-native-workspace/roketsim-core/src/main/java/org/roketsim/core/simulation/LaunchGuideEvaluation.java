package org.roketsim.core.simulation;

import org.roketsim.core.dynamics.*;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Vectors;
import org.roketsim.core.numerics.IntegrationPoint3DOF;

/** Explicit constrained RHS and net ideal reaction, alongside the unchanged free-physics snapshot.
 * The free snapshot's dynamics is not relabelled as the guided acceleration.
 */
public record LaunchGuideEvaluation(IntegrationPoint3DOF point,PhysicsEvaluationResult3DOF unconstrainedPhysics,
        FlightPhaseClockState3DOF clock,double guideTravelM,double axialVelocityMS,double axialAppliedForceN,
        TranslationalStateDerivative3DOF appliedDerivative,Vector netConstraintReactionWorldN) {
    public LaunchGuideEvaluation {
        LaunchGuideError.required(point,"point"); LaunchGuideError.required(unconstrainedPhysics,"unconstrainedPhysics");
        LaunchGuideError.required(clock,"clock"); LaunchGuideError.required(appliedDerivative,"appliedDerivative");
        LaunchGuideError.required(netConstraintReactionWorldN,"netConstraintReactionWorldN");
        netConstraintReactionWorldN=Vector.of(netConstraintReactionWorldN,3,"netConstraintReactionWorldN");
        LaunchGuideError.finite(guideTravelM,"guideTravelM"); LaunchGuideError.finite(axialVelocityMS,"axialVelocityMS");
        LaunchGuideError.finite(axialAppliedForceN,"axialAppliedForceN");
    }
    public static double travel(LaunchConditions3DOF launch,TranslationalState3DOF state) {
        var u=launch.launchDirectionWorldUnit();
        return LaunchGuideError.finite(Vectors.dot(offset(launch,state),u)/Vectors.dot(u,u),"guideTravelM");
    }
    public static double displacementMagnitude(LaunchConditions3DOF launch,TranslationalState3DOF state) {
        return Vectors.magnitude(offset(launch,state));
    }
    private static Vector offset(LaunchConditions3DOF launch,TranslationalState3DOF state) {
        double[] delta=new double[3];
        for(int i=0;i<3;i++) delta[i]=state.positionWorldM().get(i)-launch.initialPositionWorldM().get(i);
        return Vector.of(delta);
    }
    public static double axialVelocity(LaunchConditions3DOF launch,TranslationalState3DOF state) {
        var u=launch.launchDirectionWorldUnit();
        return LaunchGuideError.finite(Vectors.dot(state.velocityWorldMS(),u)/Vectors.dot(u,u),"axialVelocityMS");
    }
    public static LaunchGuideEvaluation evaluate(IntegrationPoint3DOF point,LaunchConditions3DOF launch,
            PhysicsEvaluationResult3DOF physics,FlightPhaseClockState3DOF clock) {
        var u=launch.launchDirectionWorldUnit(); var forces=physics.dynamics().forces().netForceWorldN();
        double force=Vectors.dot(forces,u),parallelAcceleration=Vectors.dot(physics.dynamics().accelerationWorldMS2(),u)/Vectors.dot(u,u);
        double s=travel(launch,point.state()),speed=axialVelocity(launch,point.state());
        if(clock.flightPhase()==FlightPhase3DOF.FREE_FLIGHT)
            return new LaunchGuideEvaluation(point,physics,clock,s,speed,force,physics.dynamics().derivative(),zero());
        if(clock.flightPhase()!=FlightPhase3DOF.PAD_CONTACT && clock.flightPhase()!=FlightPhase3DOF.GUIDED_FLIGHT)
            throw new LaunchGuideError("UNSUPPORTED_GUIDE_PHASE","flightPhase",clock.flightPhase());
        boolean held=clock.flightPhase()==FlightPhase3DOF.PAD_CONTACT && force<=0;
        if(held && (!point.state().positionWorldM().equals(launch.initialPositionWorldM())
                || Vectors.magnitude(point.state().velocityWorldMS())!=0))
            throw new LaunchGuideError("INVALID_PAD_STATE","guideTravel/velocity",java.util.List.of(s,speed));
        if(clock.flightPhase()==FlightPhase3DOF.PAD_CONTACT && force>0 && parallelAcceleration<=0)
            throw new LaunchGuideError("INCONSISTENT_PROJECTED_FORCE_SIGN","parallelAcceleration",parallelAcceleration);
        double[] a=new double[3],reaction=new double[3];
        for(int i=0;i<3;i++) {
            a[i]=held?0:parallelAcceleration*u.get(i);
            reaction[i]=physics.rocketMassProperties().totalMassKg()*a[i]-forces.get(i);
        }
        var derivative=new TranslationalStateDerivative3DOF(held?zero():point.state().velocityWorldMS(),Vector.of(a));
        return new LaunchGuideEvaluation(point,physics,clock,s,speed,force,derivative,Vector.of(reaction));
    }
    static Vector zero() { return Vector.of(new double[]{0,0,0}); }
}
