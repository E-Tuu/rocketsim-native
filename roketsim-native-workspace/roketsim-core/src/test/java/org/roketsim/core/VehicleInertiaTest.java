package org.roketsim.core;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.roketsim.core.mass.*;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.simulation.*;

class VehicleInertiaTest {
    private static final InertiaTensor3D ZERO=new InertiaTensor3D(0,0,0,0,0,0);
    private static final List<String> BASE_IDS=List.of("structure:nose","structure:body","structure:fins",
        "structure:motor-mount","structure:centering-rings","motor:installed");
    private final VehicleInertiaEvaluator evaluator=new VehicleInertiaEvaluator();
    private static void close(double expected,double actual) { GeometryResolverTest.close(expected,actual,3e-14); }
    private static void invalid(String context,org.junit.jupiter.api.function.Executable action) {
        var e=assertThrows(IllegalArgumentException.class,action);
        assertTrue(e.getMessage().contains(context),e.getMessage());
    }
    private static VehicleInertiaEvaluationRequest change(VehicleInertiaEvaluationRequest r,String path,Object value) {
        return (VehicleInertiaEvaluationRequest)VehicleTestData.replace(r,path,value);
    }
    private static AttachedHardwareInertia hardware(String id,InertiaTensor3D tensor) {
        return new AttachedHardwareInertia(id,tensor,0,0,"SYNTHETIC test evidence for "+id);
    }
    private static PhysicsEvaluationContext3DOF context() { return NativeDemoTrajectoryTest.configuration(.01,3).physicsContext(); }
    private static VehicleInertiaEvaluationRequest request(PhysicsEvaluationContext3DOF c,double time,
            List<AttachedHardwareMass> masses,List<AttachedHardwareInertia> inertias,MotorInertiaModel model) {
        double[] ms=masses.stream().mapToDouble(AttachedHardwareMass::massKg).toArray();
        double[] xs=masses.stream().mapToDouble(AttachedHardwareMass::cgXGeoM).toArray();
        // The authoritative values really come from the accepted NAT-024 physics overload at this time/state.
        var snapshot=new PhysicsEvaluator3DOF().evaluate(time,PhysicsEvaluatorTest.state(50,0,0,0),PhysicsEvaluatorTest.launch(50),c,ms,xs);
        var total=snapshot.rocketMassProperties();
        return new VehicleInertiaEvaluationRequest(c.structuralMassProperties(),
            new StructuralInertiaCalculator().evaluate(c.resolvedGeometry(),c.structuralMassProperties()),
            c.motorInstallation(),c.motorPropertyModelProfile(),model,snapshot.motorTimeS(),masses,inertias,
            total.totalMassKg(),total.totalCgXGeoM(),total.totalMassKg()*3e-14,Math.abs(total.totalCgXGeoM())*3e-14);
    }
    private static VehicleInertiaEvaluationRequest request() {
        return request(context(),0,List.of(),List.of(),new UniformCylinderMotorInertiaModel(0));
    }
    private static VehicleInertiaEvaluationRequest points(List<AttachedHardwareMass> masses,List<AttachedHardwareInertia> tensors) {
        // Mathematical aggregation fixture only: six mandatory one-kg contributions at x_geo=0.
        // Zero source tensors do not claim real F50 geometry or independently verified source data.
        var component=new ComponentMassProperties(null,1,0);
        var structure=new StructuralMassProperties(component,component,component,component,component,5,0);
        var m=MotorCatalogs.AEROTECH_F50_4T;
        var synthetic=new MotorDefinition("NAT025C mathematical point fixture","TEST","TEST","TEST",m.motorType(),"TEST",
            .125,.25,1,.5,0,m.thrustCurve(),m.certification(),m.provenance(),null,null);
        var installed=new MotorInstallation(synthetic,-.125,.125,0,.25);
        var profile=MotorPropertyModelProfile.DEMO_MOTOR_PROPERTY_MODEL_PROFILE;
        var motor=new MotorPropertyEvaluator().evaluate(installed,profile,0);
        var authority=new RocketMassPropertiesCalculator().evaluate(structure,motor,
            masses.stream().mapToDouble(AttachedHardwareMass::massKg).toArray(),masses.stream().mapToDouble(AttachedHardwareMass::cgXGeoM).toArray());
        var curve=new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,ZERO),new MotorInertiaSample(2,ZERO)),"SYNTHETIC mathematical tensor fixture");
        return new VehicleInertiaEvaluationRequest(structure,new StructuralComponentInertias(ZERO,ZERO,ZERO,ZERO,ZERO),
            installed,profile,curve,0,masses,tensors,authority.totalMassKg(),authority.totalCgXGeoM(),0,0);
    }
    @Test void exactSchemasAndSingleStatelessApi() throws Exception {
        assertArrayEquals(new String[]{"componentId","inertiaAtComponentCgBody","cgBodyXM","cgBodyYM","provenanceReference"},
            Arrays.stream(AttachedHardwareInertia.class.getRecordComponents()).map(x->x.getName()).toArray(String[]::new));
        assertArrayEquals(new String[]{"structuralMassProperties","structuralComponentInertias","motorInstallation","motorPropertyModelProfile",
            "motorInertiaModel","motorTimeS","attachedHardwareMasses","attachedHardwareInertias","authoritativeTotalMassKg",
            "authoritativeCgXGeoM","massConsistencyToleranceKg","cgConsistencyToleranceM"},
            Arrays.stream(VehicleInertiaEvaluationRequest.class.getRecordComponents()).map(x->x.getName()).toArray(String[]::new));
        assertArrayEquals(new String[]{"motorTimeS","totalMassKg","totalCgXGeoM","inertiaAtVehicleCgBody","motorInertiaModelKind",
            "motorInertiaProvenanceReference","includedComponentIds"},Arrays.stream(VehicleInertiaResult.class.getRecordComponents()).map(x->x.getName()).toArray(String[]::new));
        assertEquals(VehicleInertiaResult.class,VehicleInertiaEvaluator.class.getMethod("evaluate",VehicleInertiaEvaluationRequest.class).getReturnType());
        assertTrue(Modifier.isFinal(VehicleInertiaEvaluator.class.getModifiers()));
        assertTrue(Arrays.stream(VehicleInertiaEvaluator.class.getDeclaredFields()).allMatch(x->Modifier.isStatic(x.getModifiers())&&Modifier.isFinal(x.getModifiers())));
        assertEquals(14,PhysicsEvaluationContext3DOF.class.getRecordComponents().length);
        assertEquals(7,SimulationRunConfiguration3DOF.class.getRecordComponents().length);
        assertEquals(13,PhysicsEvaluationResult3DOF.class.getRecordComponents().length);
        assertEquals(2,RocketMassProperties.class.getRecordComponents().length);
    }
    @Test void twoOppositeOneKgPointMassesYieldTwoTransverseMoments() {
        var empty=evaluator.evaluate(points(List.of(),List.of()));assertEquals(ZERO,empty.inertiaAtVehicleCgBody());
        var r=points(List.of(new AttachedHardwareMass("left",1,-1),new AttachedHardwareMass("right",1,1)),
            List.of(hardware("right",ZERO),hardware("left",ZERO)));
        var result=evaluator.evaluate(r);
        assertEquals(new InertiaTensor3D(2,2,0,0,0,0),result.inertiaAtVehicleCgBody());
        assertEquals(8,result.totalMassKg());assertEquals(0,result.totalCgXGeoM());
    }
    @Test void fiveStructuralGroupsAndMotorAreEachCountedOnceWithAllSixTerms() {
        var r=points(List.of(),List.of());
        InertiaTensor3D[] tensors=new InertiaTensor3D[6];
        for(int i=0;i<6;i++) { int n=i+1;tensors[i]=new InertiaTensor3D(n,n+1,n+2,n*.0625,n*.125,n*.25); }
        r=change(r,"structural_component_inertias",new StructuralComponentInertias(tensors[0],tensors[1],tensors[2],tensors[3],tensors[4]));
        r=change(r,"motor_inertia_model",new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,tensors[5]),new MotorInertiaSample(2,tensors[5])),"SYNTHETIC source six"));
        var result=evaluator.evaluate(r);
        assertEquals(new InertiaTensor3D(21,27,33,1.3125,2.625,5.25),result.inertiaAtVehicleCgBody());
        assertEquals(BASE_IDS,result.includedComponentIds());
        assertEquals(MotorInertiaModelKind.EXPLICIT_CURVE,result.motorInertiaModelKind());
        assertEquals("SYNTHETIC source six",result.motorInertiaProvenanceReference());
    }
    @Test void realNoHardwareUsesAcceptedSnapshotAndIndependentScalarAggregation() {
        var r=request();var result=evaluator.evaluate(r);
        assertEquals(BASE_IDS,result.includedComponentIds());
        assertEquals(r.authoritativeTotalMassKg(),result.totalMassKg());assertEquals(r.authoritativeCgXGeoM(),result.totalCgXGeoM());
        assertEquals(MotorInertiaModelKind.UNIFORM_CYLINDER,result.motorInertiaModelKind());
        assertTrue(result.motorInertiaProvenanceReference().contains("APPROXIMATION"));
        checkIndependentAggregation(r,result);
    }
    private static void checkIndependentAggregation(VehicleInertiaEvaluationRequest r,VehicleInertiaResult result) {
        var s=r.structuralMassProperties();var t=r.structuralComponentInertias();
        ComponentMassProperties[] ms={s.nose(),s.body(),s.fins(),s.motorMount(),s.centeringRings()};
        InertiaTensor3D[] ts={t.nose(),t.body(),t.fins(),t.motorMount(),t.centeringRings()};
        double xx=0,yy=0,zz=0;
        for(int i=0;i<5;i++) {
            double distance=r.authoritativeCgXGeoM()-ms[i].cgXGeoM();
            xx+=ts[i].ixxKgM2()+ms[i].massKg()*distance*distance;
            yy+=ts[i].iyyKgM2()+ms[i].massKg()*distance*distance;zz+=ts[i].izzKgM2();
        }
        var motor=new MotorPropertyEvaluator().evaluate(r.motorInstallation(),r.motorPropertyModelProfile(),r.motorTimeS());
        var tensor=new MotorInertiaEvaluator().evaluate(r.motorInstallation().motor(),motor,
            new MotorCurveAnalyzer().analyze(r.motorInstallation().motor()),r.motorTimeS(),r.motorInertiaModel()).inertiaAtMotorCg();
        double distance=r.authoritativeCgXGeoM()-motor.cgXGeoM();
        xx+=tensor.ixxKgM2()+motor.massKg()*distance*distance;yy+=tensor.iyyKgM2()+motor.massKg()*distance*distance;zz+=tensor.izzKgM2();
        for(var mass:r.attachedHardwareMasses()) {
            var h=r.attachedHardwareInertias().stream().filter(x->x.componentId().equals(mass.componentId())).findFirst().orElseThrow();
            double d=r.authoritativeCgXGeoM()-mass.cgXGeoM();
            xx+=h.inertiaAtComponentCgBody().ixxKgM2()+mass.massKg()*d*d;yy+=h.inertiaAtComponentCgBody().iyyKgM2()+mass.massKg()*d*d;zz+=h.inertiaAtComponentCgBody().izzKgM2();
        }
        close(xx,result.inertiaAtVehicleCgBody().ixxKgM2());close(yy,result.inertiaAtVehicleCgBody().iyyKgM2());close(zz,result.inertiaAtVehicleCgBody().izzKgM2());
    }
    @Test void dynamicMotorMassCgAndCurveUseTheSameSuppliedTime() {
        var c=context();var installed=new MotorInstallationResolver().resolve(c.resolvedGeometry(),MotorPropertiesTest.sourceMotor());
        c=PhysicsEvaluatorTest.change(c,"motor_installation",installed);
        c=PhysicsEvaluatorTest.change(c,"motor_property_model_profile",MotorPropertiesTest.explicit());
        var curve=new MotorInertiaCurveModel(List.of(new MotorInertiaSample(0,new InertiaTensor3D(.001,.001,.0001,0,0,0)),
            new MotorInertiaSample(2,new InertiaTensor3D(.003,.003,.0003,0,0,0))),"SYNTHETIC time-dependent inertia curve");
        var a=request(c,.25,List.of(),List.of(),curve);var b=request(c,.75,List.of(),List.of(),curve);
        var ra=evaluator.evaluate(a);var rb=evaluator.evaluate(b);
        assertEquals(.25,ra.motorTimeS());assertEquals(.75,rb.motorTimeS());
        assertNotEquals(ra.totalMassKg(),rb.totalMassKg());assertNotEquals(ra.totalCgXGeoM(),rb.totalCgXGeoM());
        assertNotEquals(ra.inertiaAtVehicleCgBody(),rb.inertiaAtVehicleCgBody());
        checkIndependentAggregation(a,ra);checkIndependentAggregation(b,rb);
        var ma=new MotorPropertyEvaluator().evaluate(installed,MotorPropertiesTest.explicit(),.25);
        var mb=new MotorPropertyEvaluator().evaluate(installed,MotorPropertiesTest.explicit(),.75);
        close(.09,ma.massKg());close(.07,mb.massKg());close(.045,ma.cgLocalFromFrontM());close(.055,mb.cgLocalFromFrontM());
        double structuralZ=a.structuralComponentInertias().nose().izzKgM2()+a.structuralComponentInertias().body().izzKgM2()
            +a.structuralComponentInertias().fins().izzKgM2()+a.structuralComponentInertias().motorMount().izzKgM2()+a.structuralComponentInertias().centeringRings().izzKgM2();
        close(structuralZ+.000125,ra.inertiaAtVehicleCgBody().izzKgM2());close(structuralZ+.000175,rb.inertiaAtVehicleCgBody().izzKgM2());
        double noseA=a.structuralMassProperties().nose().massKg()*Math.pow(a.authoritativeCgXGeoM()-a.structuralMassProperties().nose().cgXGeoM(),2);
        double noseB=b.structuralMassProperties().nose().massKg()*Math.pow(b.authoritativeCgXGeoM()-b.structuralMassProperties().nose().cgXGeoM(),2);
        assertNotEquals(noseA,noseB);
        var stale=change(a,"motor_time_s",.75);
        invalid("authoritativeTotalMassKg",()->evaluator.evaluate(stale));
    }
    @Test void twoIncludedHardwareEntriesMatchByExactIdNotPositionAndPreserveCrossTerms() {
        var masses=List.of(new AttachedHardwareMass("avionics",.08,.4),new AttachedHardwareMass("recovery-hardware",.12,1));
        var first=new InertiaTensor3D(.001,.002,.003,.000125,-.00025,.000375);
        var second=new InertiaTensor3D(.002,.003,.004,.00025,-.0005,.00075);
        var inertias=List.of(hardware("recovery-hardware",second),hardware("avionics",first));
        var r=request(context(),.5,masses,inertias,new UniformCylinderMotorInertiaModel(0));var result=evaluator.evaluate(r);
        assertEquals(List.of("structure:nose","structure:body","structure:fins","structure:motor-mount","structure:centering-rings",
            "motor:installed","avionics","recovery-hardware"),result.includedComponentIds());
        // Coaxial translation leaves each product unchanged: exact sum of supplied doubles, not a separately rounded decimal literal.
        assertEquals(first.ixyKgM2()+second.ixyKgM2(),result.inertiaAtVehicleCgBody().ixyKgM2());
        assertEquals(first.ixzKgM2()+second.ixzKgM2(),result.inertiaAtVehicleCgBody().ixzKgM2());
        assertEquals(first.iyzKgM2()+second.iyzKgM2(),result.inertiaAtVehicleCgBody().iyzKgM2());
        checkIndependentAggregation(r,result);
        assertEquals(result,evaluator.evaluate(change(r,"attached_hardware_inertias",List.of(inertias.get(1),inertias.get(0)))));
    }
    @Test void missingIncludedInertiaNamesComponentButAbsentHardwareNeedsNothing() {
        var r=request();assertEquals(6,evaluator.evaluate(r).includedComponentIds().size());
        var missing=change(r,"attached_hardware_masses",List.of(new AttachedHardwareMass("payload-actually-included",.1,.5)));
        invalid("payload-actually-included",()->evaluator.evaluate(missing));
        // No payload/avionics/recovery entries are requested when absent; no default tensors are inferred.
        assertEquals(evaluator.evaluate(r),evaluator.evaluate(request()));
    }
    @Test void unmatchedDuplicateAndReservedIdsRejected() {
        var r=request();
        invalid("orphan",()->evaluator.evaluate(change(r,"attached_hardware_inertias",List.of(hardware("orphan",ZERO)))));
        invalid("dup",()->evaluator.evaluate(change(r,"attached_hardware_inertias",List.of(hardware("dup",ZERO),hardware("dup",ZERO)))));
        var duplicateMass=change(r,"attached_hardware_masses",List.of(new AttachedHardwareMass("dup",1,0),new AttachedHardwareMass("dup",2,1)));
        duplicateMass=change(duplicateMass,"attached_hardware_inertias",List.of(hardware("dup",ZERO)));
        var duplicateRequest=duplicateMass;
        invalid("duplicate attachedHardwareMasses",()->evaluator.evaluate(duplicateRequest));
        for(String reserved:BASE_IDS) {
            invalid(reserved,()->evaluator.evaluate(change(r,"attached_hardware_masses",List.of(new AttachedHardwareMass(reserved,1,0)))));
            invalid(reserved,()->evaluator.evaluate(change(r,"attached_hardware_inertias",List.of(hardware(reserved,ZERO)))));
        }
    }
    @Test void explicitHardwareIdentityProvenanceAndCenterlineBoundary() {
        for(String bad:new String[]{null,""," \t"}) {
            invalid("componentId",()->new AttachedHardwareInertia(bad,ZERO,0,0,"source"));
            invalid("provenanceReference",()->new AttachedHardwareInertia("id",ZERO,0,0,bad));
        }
        invalid("id",()->new AttachedHardwareInertia("id",null,0,0,"source"));
        for(double bad:new double[]{1,-1,Double.MIN_VALUE,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            invalid("cgBody",()->new AttachedHardwareInertia("id",ZERO,bad,0,"source"));
            invalid("cgBody",()->new AttachedHardwareInertia("id",ZERO,0,bad,"source"));
        }
        var exact=new AttachedHardwareInertia(" Mixed CASE ",ZERO,-0.0,0," claimed source ");
        assertEquals(" Mixed CASE ",exact.componentId());assertEquals(" claimed source ",exact.provenanceReference());
    }
    @Test void massAndCgAuthoritiesAreCheckedNotReplaced() {
        var r=request();
        invalid("authoritativeTotalMassKg",()->evaluator.evaluate(change(r,"authoritative_total_mass_kg",r.authoritativeTotalMassKg()+.1)));
        invalid("authoritativeCgXGeoM",()->evaluator.evaluate(change(r,"authoritative_cg_x_geo_m",r.authoritativeCgXGeoM()+.1)));
        var bad=change(r,"structural_mass_properties.nose.mass_kg",r.structuralMassProperties().nose().massKg()+.01);
        invalid("structural mass",()->evaluator.evaluate(bad));
        var withTolerance=change(r,"mass_consistency_tolerance_kg",.001);
        withTolerance=change(withTolerance,"authoritative_total_mass_kg",r.authoritativeTotalMassKg()+.0001);
        assertEquals(withTolerance.authoritativeTotalMassKg(),evaluator.evaluate(withTolerance).totalMassKg());
        var shifted=change(r,"cg_consistency_tolerance_m",.001);
        shifted=change(shifted,"authoritative_cg_x_geo_m",r.authoritativeCgXGeoM()+.0001);
        var result=evaluator.evaluate(shifted);assertEquals(shifted.authoritativeCgXGeoM(),result.totalCgXGeoM());
        checkIndependentAggregation(shifted,result);
    }
    @Test void commonAxialOriginTranslationLeavesTensorUnchanged() {
        var r=points(List.of(new AttachedHardwareMass("front",1,-1),new AttachedHardwareMass("aft",1,1)),
            List.of(hardware("front",ZERO),hardware("aft",ZERO)));
        var c=new ComponentMassProperties(null,1,8);
        var shifted=change(r,"structural_mass_properties",new StructuralMassProperties(c,c,c,c,c,5,8));
        shifted=change(shifted,"motor_installation",new MotorInstallation(r.motorInstallation().motor(),7.875,8.125,0,.25));
        shifted=change(shifted,"attached_hardware_masses",List.of(new AttachedHardwareMass("front",1,7),new AttachedHardwareMass("aft",1,9)));
        shifted=change(shifted,"authoritative_cg_x_geo_m",8.0);
        assertEquals(evaluator.evaluate(r).inertiaAtVehicleCgBody(),evaluator.evaluate(shifted).inertiaAtVehicleCgBody());
    }
    @Test void requestReferencesListsAndNumbersAreValidated() {
        var r=request();invalid("request",()->evaluator.evaluate(null));
        for(String field:new String[]{"structural_mass_properties","structural_component_inertias","motor_installation",
            "motor_property_model_profile","motor_inertia_model","attached_hardware_masses","attached_hardware_inertias"})
            assertThrows(IllegalArgumentException.class,()->change(r,field,null));
        invalid("attachedHardwareMasses[0]",()->change(r,"attached_hardware_masses",Arrays.asList((AttachedHardwareMass)null)));
        invalid("attachedHardwareInertias[0]",()->change(r,"attached_hardware_inertias",Arrays.asList((AttachedHardwareInertia)null)));
        invalid("motorInstallation.motor",()->change(r,"motor_installation",new MotorInstallation(null,0,1,0,1)));
        for(String field:new String[]{"motor_time_s","mass_consistency_tolerance_kg","cg_consistency_tolerance_m"})
            for(double bad:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
                assertThrows(IllegalArgumentException.class,()->change(r,field,bad));
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            invalid("authoritativeTotalMassKg",()->change(r,"authoritative_total_mass_kg",bad));
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            invalid("authoritativeCgXGeoM",()->change(r,"authoritative_cg_x_geo_m",bad));
    }
    @Test void invalidComponentMassCgAndDerivedOverflowRejected() {
        var r=points(List.of(),List.of());
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            invalid("structure:nose",()->evaluator.evaluate(change(r,"structural_mass_properties.nose.mass_kg",bad)));
            var invalidAux=change(r,"attached_hardware_masses",List.of(new AttachedHardwareMass("bad-mass",bad,0)));
            invalidAux=change(invalidAux,"attached_hardware_inertias",List.of(hardware("bad-mass",ZERO)));
            var input=invalidAux;invalid("bad-mass",()->evaluator.evaluate(input));
        }
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            invalid("structure:nose",()->evaluator.evaluate(change(r,"structural_mass_properties.nose.cg_x_geo_m",bad)));
        invalid("structure:nose",()->evaluator.evaluate(change(r,"structural_mass_properties.nose",null)));
        invalid("first moment",()->evaluator.evaluate(change(change(r,"structural_mass_properties.nose.mass_kg",2.0),"structural_mass_properties.nose.cg_x_geo_m",Double.MAX_VALUE)));
        invalid("mass sum",()->evaluator.evaluate(change(change(r,"structural_mass_properties.nose.mass_kg",Double.MAX_VALUE),"structural_mass_properties.body.mass_kg",Double.MAX_VALUE)));
        var huge=new InertiaTensor3D(Double.MAX_VALUE,0,0,0,0,0);
        var hugeRequest=change(r,"structural_component_inertias",new StructuralComponentInertias(huge,huge,ZERO,ZERO,ZERO));
        invalid("sum",()->evaluator.evaluate(hugeRequest));
        var distant=change(change(r,"structural_mass_properties.nose.cg_x_geo_m",1e155),"structural_mass_properties.body.cg_x_geo_m",-1e155);
        invalid("ixxKgM2",()->evaluator.evaluate(distant));
    }
    @Test void actualNat024RecordedMassSnapshotsAreConsumedWithoutFlightIntegrationChanges() {
        var base=NativeDemoTrajectoryTest.configuration(.01,3);var c=base.physicsContext();
        var masses=List.of(new AttachedHardwareMass("avionics",.08,.4),new AttachedHardwareMass("recovery",.12,1));
        var inertias=List.of(hardware("avionics",new InertiaTensor3D(.001,.002,.003,0,0,0)),hardware("recovery",ZERO));
        var engine=new SimulationEngine3DOF();var configured=new SingleStageVehicleRunConfiguration3DOF(base,masses);
        var execution=engine.run(configured);var legacy=engine.run(base);
        for(var sample:execution.recordedData().samples()) {
            var snapshot=sample.physics();var total=snapshot.rocketMassProperties();
            var input=new VehicleInertiaEvaluationRequest(c.structuralMassProperties(),
                new StructuralInertiaCalculator().evaluate(c.resolvedGeometry(),c.structuralMassProperties()),
                c.motorInstallation(),c.motorPropertyModelProfile(),new UniformCylinderMotorInertiaModel(0),
                snapshot.motorTimeS(),masses,inertias,total.totalMassKg(),total.totalCgXGeoM(),total.totalMassKg()*3e-14,total.totalCgXGeoM()*3e-14);
            var result=evaluator.evaluate(input);
            assertEquals(total.totalMassKg(),result.totalMassKg());assertEquals(total.totalCgXGeoM(),result.totalCgXGeoM());
            assertEquals(snapshot.motorTimeS(),result.motorTimeS());checkIndependentAggregation(input,result);
            invalid("avionics",()->evaluator.evaluate(change(input,"attached_hardware_inertias",List.of(inertias.get(1)))));
        }
        assertEquals(execution,engine.run(configured));assertEquals(legacy,engine.run(base));
    }
    @Test void immutableListSnapshotsIndependentResultsAndRepeatability() {
        var massList=new ArrayList<>(List.of(new AttachedHardwareMass("first",1,-1),new AttachedHardwareMass("second",1,1)));
        var inertiaList=new ArrayList<>(List.of(hardware("first",ZERO),hardware("second",ZERO)));
        var r=points(massList,inertiaList);var expected=evaluator.evaluate(r);
        massList.clear();inertiaList.clear();assertEquals(2,r.attachedHardwareMasses().size());assertEquals(2,r.attachedHardwareInertias().size());
        assertThrows(UnsupportedOperationException.class,()->r.attachedHardwareMasses().clear());
        assertThrows(UnsupportedOperationException.class,()->r.attachedHardwareInertias().clear());
        assertThrows(UnsupportedOperationException.class,()->expected.includedComponentIds().clear());
        for(int i=0;i<3;i++) { evaluator.evaluate(request());assertEquals(expected,evaluator.evaluate(r));assertEquals(expected,new VehicleInertiaEvaluator().evaluate(r)); }
        var callerIds=new ArrayList<>(expected.includedComponentIds());
        var copy=new VehicleInertiaResult(expected.motorTimeS(),expected.totalMassKg(),expected.totalCgXGeoM(),expected.inertiaAtVehicleCgBody(),
            expected.motorInertiaModelKind(),expected.motorInertiaProvenanceReference(),callerIds);
        callerIds.clear();assertEquals(expected,copy);
    }
}
