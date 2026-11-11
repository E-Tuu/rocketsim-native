package org.roketsim.core.mass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import org.roketsim.core.math.Vector;
import org.roketsim.core.propulsion.MotorCurveAnalyzer;
import org.roketsim.core.propulsion.MotorPropertyEvaluator;
import org.roketsim.core.propulsion.MotorInertiaEvaluator;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Stateless coaxial aggregation only; existing mass/CG totals remain authoritative. */
public final class VehicleInertiaEvaluator {
    private static final List<String> RESERVED=List.of("structure:nose","structure:body","structure:fins",
        "structure:motor-mount","structure:centering-rings","motor:installed");
    static void required(Object value,String name) {
        if(value==null)throw new IllegalArgumentException(name+" must not be null");
    }
    static void text(String value,String name) {
        if(value==null || value.isBlank())throw new IllegalArgumentException(name+" must not be blank");
    }
    static void nonnegative(double value,String name) {
        requireFinite(value,name);if(value<0)throw new IllegalArgumentException(name+" must be nonnegative");
    }
    static void positive(double value,String name) {
        requireFinite(value,name);if(value<=0)throw new IllegalArgumentException(name+" must be positive");
    }
    private static void consistent(double actual,double authority,double tolerance,String field) {
        double difference=requireFinite(actual-authority,field+" difference");
        if(Math.abs(difference)>tolerance)throw new IllegalArgumentException(field+" inconsistent: contributors="+actual+", authority="+authority);
    }
    private static void hardwareId(String id,String field) {
        text(id,field);
        if(RESERVED.contains(id))throw new IllegalArgumentException(field+": reserved componentId "+id);
    }
    private static InertiaTensor3D shift(InertiaTensor3D inertia,double mass,double cg,double reference,String id) {
        required(inertia,id+".inertia");positive(mass,id+".massKg");requireFinite(cg,id+".cgXGeoM");
        double dz=requireFinite(reference-cg,id+".displacementBodyZM");
        return new ParallelAxisInertiaCalculator().shiftFromCentroid(inertia,mass,Vector.of(new double[]{0,0,dz}));
    }
    private static InertiaTensor3D add(InertiaTensor3D a,InertiaTensor3D b,String id) {
        return new InertiaTensor3D(requireFinite(a.ixxKgM2()+b.ixxKgM2(),id+".ixxKgM2 sum"),
            requireFinite(a.iyyKgM2()+b.iyyKgM2(),id+".iyyKgM2 sum"),requireFinite(a.izzKgM2()+b.izzKgM2(),id+".izzKgM2 sum"),
            requireFinite(a.ixyKgM2()+b.ixyKgM2(),id+".ixyKgM2 sum"),requireFinite(a.ixzKgM2()+b.ixzKgM2(),id+".ixzKgM2 sum"),
            requireFinite(a.iyzKgM2()+b.iyzKgM2(),id+".iyzKgM2 sum"));
    }
    /** @param request same-state authoritative totals, explicit models and exact hardware inventory
     * @return BODY tensor and unchanged authoritative mass/CG, with exact included IDs
     * @throws IllegalArgumentException for invalid inputs, incomplete coverage or inconsistent snapshots */
    public VehicleInertiaResult evaluate(VehicleInertiaEvaluationRequest request) {
        required(request,"request");var r=request;
        var hardware=new LinkedHashMap<String,AttachedHardwareInertia>();
        for(var inertia:r.attachedHardwareInertias()) {
            String id=inertia.componentId();hardwareId(id,"attachedHardwareInertias");
            if(hardware.putIfAbsent(id,inertia)!=null)throw new IllegalArgumentException("duplicate attachedHardwareInertias componentId: "+id);
        }
        var massIds=new HashSet<String>();
        for(var mass:r.attachedHardwareMasses()) {
            String id=mass.componentId();hardwareId(id,"attachedHardwareMasses");
            if(!massIds.add(id))throw new IllegalArgumentException("duplicate attachedHardwareMasses componentId: "+id);
            positive(mass.massKg(),id+".massKg");requireFinite(mass.cgXGeoM(),id+".cgXGeoM");
            if(!hardware.containsKey(id))throw new IllegalArgumentException("missing inertia for included componentId: "+id);
        }
        for(String id:hardware.keySet())if(!massIds.contains(id))throw new IllegalArgumentException("unmatched inertia componentId: "+id);
        var structure=r.structuralMassProperties();var tensors=r.structuralComponentInertias();
        ComponentMassProperties[] components={structure.nose(),structure.body(),structure.fins(),structure.motorMount(),structure.centeringRings()};
        InertiaTensor3D[] inertias={tensors.nose(),tensors.body(),tensors.fins(),tensors.motorMount(),tensors.centeringRings()};
        double structureSum=0,moment=0;
        for(int i=0;i<5;i++) {
            String id=RESERVED.get(i);required(components[i],id);required(inertias[i],id+".inertia");
            positive(components[i].massKg(),id+".massKg");requireFinite(components[i].cgXGeoM(),id+".cgXGeoM");
            structureSum=requireFinite(structureSum+components[i].massKg(),id+" mass sum");
            double contribution=requireFinite(components[i].massKg()*components[i].cgXGeoM(),id+" first moment");
            moment=requireFinite(moment+contribution,id+" first moment sum");
        }
        positive(structure.structureMassKg(),"structuralMassProperties.structureMassKg");
        requireFinite(structure.structureCgXGeoM(),"structuralMassProperties.structureCgXGeoM");
        consistent(structureSum,structure.structureMassKg(),r.massConsistencyToleranceKg(),"structural mass");
        // One motor time is passed unchanged to both existing authorities; no ignition or mass formula here.
        var motor=new MotorPropertyEvaluator().evaluate(r.motorInstallation(),r.motorPropertyModelProfile(),r.motorTimeS());
        var statistics=new MotorCurveAnalyzer().analyze(r.motorInstallation().motor());
        var motorInertia=new MotorInertiaEvaluator().evaluate(r.motorInstallation().motor(),motor,statistics,r.motorTimeS(),r.motorInertiaModel());
        positive(motor.massKg(),"motor:installed.massKg");requireFinite(motor.cgXGeoM(),"motor:installed.cgXGeoM");
        double total=requireFinite(structureSum+motor.massKg(),"contributorMassSum");
        moment=requireFinite(moment+requireFinite(motor.massKg()*motor.cgXGeoM(),"motor:installed first moment"),"contributor first moment sum");
        for(var mass:r.attachedHardwareMasses()) {
            total=requireFinite(total+mass.massKg(),mass.componentId()+" mass sum");
            moment=requireFinite(moment+requireFinite(mass.massKg()*mass.cgXGeoM(),mass.componentId()+" first moment"),"contributor first moment sum");
        }
        consistent(total,r.authoritativeTotalMassKg(),r.massConsistencyToleranceKg(),"authoritativeTotalMassKg");
        double checkCg=requireFinite(moment/total,"contributorCgXGeoM");
        consistent(checkCg,r.authoritativeCgXGeoM(),r.cgConsistencyToleranceM(),"authoritativeCgXGeoM");
        double reference=r.authoritativeCgXGeoM();var sum=new InertiaTensor3D(0,0,0,0,0,0);
        for(int i=0;i<5;i++)sum=add(sum,shift(inertias[i],components[i].massKg(),components[i].cgXGeoM(),reference,RESERVED.get(i)),RESERVED.get(i));
        sum=add(sum,shift(motorInertia.inertiaAtMotorCg(),motor.massKg(),motor.cgXGeoM(),reference,"motor:installed"),"motor:installed");
        var included=new ArrayList<>(RESERVED);
        for(var mass:r.attachedHardwareMasses()) {
            String id=mass.componentId();
            sum=add(sum,shift(hardware.get(id).inertiaAtComponentCgBody(),mass.massKg(),mass.cgXGeoM(),reference,id),id);
            included.add(id);
        }
        return new VehicleInertiaResult(r.motorTimeS(),r.authoritativeTotalMassKg(),reference,sum,
            motorInertia.modelKind(),motorInertia.provenanceReference(),included);
    }
}
