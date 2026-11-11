package org.roketsim.core;

import java.util.LinkedHashMap;
import java.util.Map;
import org.roketsim.core.simulation.*;

/** Test-only complete-history transport. No physics, rounding, downsampling or runtime export API. */
public final class NativeTrajectoryParityProbe {
    private NativeTrajectoryParityProbe() { }
    static Map<String,Object> sample(RecordedFlightSample3DOF sample,int index) throws ReflectiveOperationException {
        var out=new LinkedHashMap<String,Object>();out.put("index",index);
        DynamicsParityProbe.flatten(out,"sample",sample);
        var p=sample.physics();
        DynamicsParityProbe.flatten(out,"sample.physics.dynamics.forces.netForceWorldN",p.dynamics().forces().netForceWorldN());
        DynamicsParityProbe.flatten(out,"sample.physics.dynamics.accelerationWorldMS2",p.dynamics().accelerationWorldMS2());
        out.put("sample.physics.basicDrag.frictionCd",p.basicDrag().frictionCd());
        out.put("sample.physics.basicDrag.pressureCd",p.basicDrag().pressureCd());
        out.put("sample.physics.basicDrag.baseCd",p.basicDrag().baseCd());return out;
    }
    static Map<String,Object> summary(SimulationResult3DOF r) throws ReflectiveOperationException {
        var out=new LinkedHashMap<String,Object>();out.put("terminationReason",r.terminationReason());
        out.put("stepsPerformed",r.stepsPerformed());out.put("sampleCount",r.samples().size());out.put("eventCount",r.events().size());
        out.put("terminationTimeS",r.terminationTimeS());out.put("simulationDurationS",r.simulationDurationS());
        out.put("initialTotalMassKg",r.initialSample().physics().rocketMassProperties().totalMassKg());
        out.put("initialMotorMassKg",r.initialSample().physics().motorMassProperties().massKg());
        out.put("finalMotorMassKg",r.lastAcceptedSample().physics().motorMassProperties().massKg());
        DynamicsParityProbe.flatten(out,"firstPropagated",r.samples().get(1).point());
        DynamicsParityProbe.flatten(out,"lastAccepted",r.lastAcceptedSample().point());
        out.put("maximumMach",r.samples().stream().mapToDouble(s->s.physics().flightConditions().mach()).max().orElseThrow());
        out.put("maximumQ",r.samples().stream().mapToDouble(s->s.physics().flightConditions().dynamicPressurePa()).max().orElseThrow());
        out.put("maximumAirspeed",r.samples().stream().mapToDouble(s->s.physics().flightConditions().airspeedMS()).max().orElseThrow());
        for(var type:FlightEventType.values()) {
            var list=r.eventsOfType(type);out.put("query."+type.name()+".size",list.size());
            if(list.size()!=1)throw new AssertionError("NAT-022 event cardinality");
            DynamicsParityProbe.flatten(out,type.name(),list.get(0));
        }
        out.put("forwardIdentity",r.recordedData()==r.execution().recordedData() && r.samples()==r.execution().recordedData().samples());
        return out;
    }
    public static void main(String[] args) throws Exception {
        if(args.length>1 && args[0].equals("--math-diagnostic")) {
            // Scalar library-call investigation only, NOT a second trajectory/physics oracle.
            for(int i=1;i<args.length;i++) {
                double re=Double.parseDouble(args[i]),d=1.50*Math.log(re)-5.6;
                var out=new LinkedHashMap<String,Object>();out.put("reynolds",re);
                out.put("log",Math.log(re));out.put("strictLog",StrictMath.log(re));out.put("denominator",d);
                out.put("pow",Math.pow(d,2));out.put("strictPow",StrictMath.pow(d,2));out.put("multiplied",d*d);
                PropulsionParityProbe.output("MATH",out);
            }
            return;
        }
        if(args.length!=2)throw new AssertionError("Explicit step and maximumSteps required");
        double h=Double.parseDouble(args[0]);int limit=Integer.parseInt(args[1]);
        var configuration=NativeDemoTrajectoryTest.configuration(h,limit);
        var r=new SimulationResult3DOF(new SimulationEngine3DOF().run(configuration));
        var header=new LinkedHashMap<String,Object>();header.put("stepSizeS",h);header.put("maximumSteps",limit);
        header.put("sampleCount",r.samples().size());header.put("eventCount",r.events().size());
        DynamicsParityProbe.flatten(header,"geometry",configuration.physicsContext().resolvedGeometry());
        DynamicsParityProbe.flatten(header,"structure",configuration.physicsContext().structuralMassProperties());
        DynamicsParityProbe.flatten(header,"surfaces",configuration.physicsContext().aerodynamicSurfaces());
        DynamicsParityProbe.flatten(header,"launch",configuration.launchConditions());
        DynamicsParityProbe.flatten(header,"statistics",configuration.motorCurveStatistics());
        DynamicsParityProbe.flatten(header,"eventProfile",configuration.eventDetectionProfile());
        DynamicsParityProbe.flatten(header,"motorPropertyProfile",configuration.physicsContext().motorPropertyModelProfile());
        DynamicsParityProbe.flatten(header,"dragProfile",configuration.physicsContext().basicDragModelProfile());
        DynamicsParityProbe.flatten(header,"dynamicsProfile",configuration.physicsContext().translationalDynamicsModelProfile());
        header.put("terminalPolicy",configuration.terminalEventHandlingModel());
        header.put("environmentMapping",configuration.physicsContext().environmentPositionMappingModel());
        header.put("motorId",configuration.physicsContext().motorInstallation().motor().motorId());
        header.put("launchEnvironmentAltitudeM",configuration.physicsContext().launchEnvironmentAltitudeM());
        header.put("ignitionTimeS",configuration.physicsContext().propulsionTimeline().ignitionTimeS());
        PropulsionParityProbe.output("HEADER",header);
        for(int i=0;i<r.samples().size();i++)PropulsionParityProbe.output("SAMPLE",sample(r.samples().get(i),i));
        for(int i=0;i<r.events().size();i++) {
            var fields=new LinkedHashMap<String,Object>();fields.put("index",i);
            DynamicsParityProbe.flatten(fields,"event",r.events().get(i));fields.put("event.isTerminal",r.events().get(i).isTerminal());
            PropulsionParityProbe.output("EVENT",fields);
        }
        PropulsionParityProbe.output("RESULT",summary(r));
        System.out.println("END");
    }
}
