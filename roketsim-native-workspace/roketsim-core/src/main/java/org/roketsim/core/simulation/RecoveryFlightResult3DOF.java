package org.roketsim.core.simulation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.roketsim.core.aerodynamics.BasicDragResult;
import org.roketsim.core.dynamics.TranslationalDynamicsEvaluator.ForceEvaluation;
import org.roketsim.core.environment.DryAirAtmosphereState;
import org.roketsim.core.environment.DryAirProperties;
import org.roketsim.core.flightconditions.BasicFlightConditions;
import org.roketsim.core.mass.RocketMassProperties;
import org.roketsim.core.math.Vector;
import org.roketsim.core.numerics.IntegrationPoint3DOF;
import org.roketsim.core.propulsion.MotorMassProperties;
import org.roketsim.core.propulsion.MotorThrustState;

/** New opt-in history. No recovery force is stored in a legacy body-only result schema. */
public record RecoveryFlightResult3DOF(List<Sample> samples,List<FlightEventOccurrence3DOF> legacyEvents,
        List<Event> recoveryEvents,Outcome outcome,double terminationTimeS,int stepsPerformed,
        Optional<Failure> failure,List<LaunchGuideTransitionResult> guideTransitions) {
    public enum State { NOT_TRIGGERED, PENDING, ACTIVE }
    public enum AerodynamicModel { BASELINE_BODY_DRAG, CONSTANT_PARACHUTE_DRAG }
    public enum Outcome { GROUND, MAXIMUM_STEPS, GUIDE_NOT_RELEASED, UNSUPPORTED, NUMERICAL_FAILURE }
    /** PHYSICAL_APOGEE is the retained development API name for v1.1 sampled-altitude
     * detection. Its point is the detection sample, never a localized physical maximum. */
    public enum EventType { PHYSICAL_APOGEE, RECOVERY_ACTIVATION }
    public record Physics(double environmentAltitudeM,double motorTimeS,DryAirAtmosphereState atmosphere,
            DryAirProperties airProperties,Vector airMassVelocityWorldMS,Vector relativeFlow,
            BasicFlightConditions flightConditions,MotorThrustState motorThrustState,
            MotorMassProperties motorMassProperties,RocketMassProperties rocketMassProperties,
            Optional<BasicDragResult> bodyDrag,Vector gravityAccelerationWorldMS2,
            ForceEvaluation dynamics,AerodynamicModel aerodynamicModel) {
        public Physics {
            Objects.requireNonNull(bodyDrag);Objects.requireNonNull(dynamics);Objects.requireNonNull(aerodynamicModel);
            if(bodyDrag.isPresent()!=(aerodynamicModel==AerodynamicModel.BASELINE_BODY_DRAG))
                throw new IllegalArgumentException("bodyDrag must describe only the selected baseline model");
        }
        static Physics baseline(PhysicsEvaluationResult3DOF p) {
            return new Physics(p.environmentAltitudeM(),p.motorTimeS(),p.atmosphere(),p.airProperties(),
                p.airMassVelocityWorldMS(),p.relativeFlow(),p.flightConditions(),p.motorThrustState(),
                p.motorMassProperties(),p.rocketMassProperties(),Optional.of(p.basicDrag()),p.gravityAccelerationWorldMS2(),
                new ForceEvaluation(p.dynamics().forces(),p.dynamics().derivative()),AerodynamicModel.BASELINE_BODY_DRAG);
        }
    }
    public record Sample(IntegrationPoint3DOF point,Physics physics,FlightPhase3DOF phase,State recoveryState) {
        public Sample {
            Objects.requireNonNull(point);Objects.requireNonNull(physics);Objects.requireNonNull(phase);Objects.requireNonNull(recoveryState);
            if((recoveryState==State.ACTIVE)!=(phase==FlightPhase3DOF.RECOVERY_DESCENT)
                    || (recoveryState==State.ACTIVE)!=(physics.aerodynamicModel()==AerodynamicModel.CONSTANT_PARACHUTE_DRAG))
                throw new IllegalArgumentException("recorded phase, recovery state and applied aerodynamic model disagree");
        }
    }
    public record Event(EventType type,IntegrationPoint3DOF point,OptionalDouble scheduledActivationTimeS,
            double lowerTimeS,double upperTimeS) {
        public Event {
            Objects.requireNonNull(type);Objects.requireNonNull(point);Objects.requireNonNull(scheduledActivationTimeS);
            if(!Double.isFinite(lowerTimeS)||!Double.isFinite(upperTimeS)||lowerTimeS>point.timeS()||point.timeS()>upperTimeS)
                throw new IllegalArgumentException("invalid recovery event bracket");
            if((type==EventType.PHYSICAL_APOGEE)!=scheduledActivationTimeS.isPresent())
                throw new IllegalArgumentException("only physical apogee owns the scheduled activation time");
            if(scheduledActivationTimeS.isPresent() && (!Double.isFinite(scheduledActivationTimeS.getAsDouble())||scheduledActivationTimeS.getAsDouble()<point.timeS()))
                throw new IllegalArgumentException("invalid scheduled activation time");
        }
    }
    public record Failure(String code,String message,Map<String,Double> details) {
        public Failure {Objects.requireNonNull(code);Objects.requireNonNull(message);details=Map.copyOf(details);}
    }
    public RecoveryFlightResult3DOF {
        samples=List.copyOf(samples);legacyEvents=List.copyOf(legacyEvents);recoveryEvents=List.copyOf(recoveryEvents);
        guideTransitions=List.copyOf(guideTransitions);Objects.requireNonNull(outcome);Objects.requireNonNull(failure);
        if(!Double.isFinite(terminationTimeS)||stepsPerformed<0)throw new IllegalArgumentException("invalid termination time/step count");
        if(!samples.isEmpty() && terminationTimeS<samples.get(samples.size()-1).point().timeS())
            throw new IllegalArgumentException("termination precedes last accepted sample");
        if((outcome==Outcome.UNSUPPORTED || outcome==Outcome.NUMERICAL_FAILURE) && failure.isEmpty())
            throw new IllegalArgumentException("failure outcome requires detailed failure evidence");
        for(int i=1;i<samples.size();i++) if(samples.get(i).point().timeS()<=samples.get(i-1).point().timeS())
            throw new IllegalArgumentException("recovery sample times must strictly increase");
        if(recoveryEvents.stream().filter(e->e.type()==EventType.PHYSICAL_APOGEE).count()>1
                || recoveryEvents.stream().filter(e->e.type()==EventType.RECOVERY_ACTIVATION).count()>1)
            throw new IllegalArgumentException("single parachute may trigger/activate only once");
    }
    public State recoveryState() {return samples.isEmpty()?State.NOT_TRIGGERED:samples.get(samples.size()-1).recoveryState();}
    /** Returns the v1.1 altitude-drop detection sample; does not return a physical root. */
    public Optional<Event> physicalApogee() {return recoveryEvents.stream().filter(e->e.type()==EventType.PHYSICAL_APOGEE).findFirst();}
    public Optional<Event> activation() {return recoveryEvents.stream().filter(e->e.type()==EventType.RECOVERY_ACTIVATION).findFirst();}
}
