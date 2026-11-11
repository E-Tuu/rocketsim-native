package org.roketsim.core;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.propulsion.*;
import org.roketsim.core.propulsion.catalog.*;
import static org.roketsim.core.propulsion.catalog.CatalogMotorProfile.*;
import static org.roketsim.core.propulsion.catalog.EngMotorRecord.EjectionInterpretation.*;
import static org.junit.jupiter.api.Assertions.*;

class MotorEngImportTest {
    static final String SYNTHETIC="; independent synthetic source\nX9 24 70 3-5-P .01 .03 OtherMaker\n .25 8\n .5 0\n;";
    static EngSourceFile read(String text) {
        return new RaspEngReader().read("synthetic.eng","test-rev","synthetic fixture",text.getBytes(StandardCharsets.UTF_8));
    }
    static CatalogMotorProfile profile(String name) {
        return Teknofest2026MotorCatalog.load().stream().filter(p->p.identity().competitionDesignation().equals(name)).findFirst().orElseThrow();
    }
    @Test void importsAllSourceIdentitiesMassesDimensionsAndExactBytes() throws Exception {
        String[] names={"TFL1256WS","TFM1850W","TFN3300R-P"};
        String[] hashes={"756cde5e3654bdb3e484a6a5c61826967f3e89834d81453655691a2017612cdd",
            "111f905fc096e8669e5ac530ec422a91dbbb177e40993a1f31134eab2e32c8fc",
            "10db8293b858988cde352676d7e15bda01d46d9c5d18af944c0991be773c51f8"};
        double[] total={3.905,7.010,12.645},prop={1.905,4.103,7.668},diameter={75,75,98},length={530.6,935.4,1072.1};
        int[] rawCount={16,23,26},byteCount={333,287,424};
        var profiles=Teknofest2026MotorCatalog.load(); assertEquals(3,profiles.size());
        for(int i=0;i<profiles.size();i++) {
            var p=profiles.get(i); var curve=p.thrustSources().get(0); var source=curve.source(); var motor=curve.record(); var h=motor.header();
            assertEquals(names[i],h.designation()); assertEquals("AeroTech/RCS",p.identity().physicalManufacturer());
            assertEquals("Teknofest26",h.sourceProvider()); assertNotEquals(h.sourceProvider(),p.identity().physicalManufacturer());
            assertEquals(total[i],h.initialMassKg()); assertEquals(prop[i],h.propellantMassKg());
            assertEquals(diameter[i],h.nominalDiameterMm()); assertEquals(length[i],h.nominalLengthMm());
            assertEquals(diameter[i]/1000,h.nominalDiameterM()); assertEquals(length[i]/1000,h.nominalLengthM());
            assertEquals(rawCount[i],motor.listedSamples().size()); assertEquals(rawCount[i]+1,motor.normalizedSamples().size());
            assertEquals(byteCount[i],source.sourceBytes().length); assertEquals(hashes[i],source.sha256());
            assertEquals(hashes[i],HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.sourceBytes())));
            try(var input=Teknofest2026MotorCatalog.class.getResourceAsStream("teknofest2026/"+source.fileName())) {
                assertNotNull(input); assertArrayEquals(input.readAllBytes(),source.sourceBytes());
            }
            assertEquals("Rev1",source.revision()); assertTrue(source.sourceReference().contains(Teknofest2026MotorCatalog.SOURCE_ZIP_SHA256));
            String raw=new String(source.sourceBytes(),StandardCharsets.UTF_8);
            assertEquals(List.of(raw.lines().findFirst().orElseThrow().strip().split("[ \\t]+")),h.tokens());
            var rawPoints=raw.lines().skip(1).filter(line->!line.isBlank() && !line.strip().startsWith(";")).toList();
            for(int point=0;point<rawPoints.size();point++) {
                var tokens=rawPoints.get(point).strip().split("\\s+");
                assertEquals(Double.parseDouble(tokens[0]),motor.listedSamples().get(point).timeS());
                assertEquals(Double.parseDouble(tokens[1]),motor.listedSamples().get(point).thrustN());
            }
            assertEquals(raw.lines().filter(line->line.strip().startsWith(";")).toList(),source.commentLines());
            assertEquals(1,motor.headerLine()); assertEquals(1,source.records().size());
        }
    }
    @Test void independentDecimalIntegrationMatchesFrozenSourceImpulses() {
        String[] exact={"3650.044730405055","7649.329000","14325.010771"};
        var profiles=Teknofest2026MotorCatalog.load();
        for(int i=0;i<3;i++) {
            var curve=profiles.get(i).thrustSources().get(0); var expected=new BigDecimal(exact[i]);
            BigDecimal time=BigDecimal.ZERO,thrust=BigDecimal.ZERO,impulse=BigDecimal.ZERO;
            for(String line:new String(curve.source().sourceBytes(),StandardCharsets.UTF_8).lines().skip(1).toList()) {
                if(line.isBlank() || line.strip().startsWith(";")) continue;
                var tokens=line.strip().split("\\s+");
                var nextTime=new BigDecimal(tokens[0]); var nextThrust=new BigDecimal(tokens[1]);
                impulse=impulse.add(thrust.add(nextThrust).multiply(nextTime.subtract(time)).divide(BigDecimal.valueOf(2)));
                time=nextTime; thrust=nextThrust;
            }
            assertEquals(0,expected.compareTo(impulse)); // Decimal source oracle independent of production doubles.
            // Reuse MotorThrustTest/J-005's accepted impulse rtol; do not invent a new policy.
            double tolerance=expected.doubleValue()*3e-15;
            assertEquals(expected.doubleValue(),curve.record().totalImpulseNS(),tolerance);
            System.out.println(curve.record().header().designation()+" impulse="+curve.record().totalImpulseNS()+" tolerance="+tolerance);
        }
    }
    @Test void genericSyntheticUnitsOriginCurveAndImpulse() {
        var file=read(SYNTHETIC); var m=file.records().get(0);
        assertEquals("X9",m.header().designation()); assertEquals("OtherMaker",m.header().sourceProvider());
        assertEquals(.024,m.header().nominalDiameterM()); assertEquals(.07,m.header().nominalLengthM());
        assertEquals(.01,m.header().propellantMassKg()); assertEquals(.03,m.header().initialMassKg());
        assertEquals(List.of(new ThrustSample(0,0),new ThrustSample(.25,8),new ThrustSample(.5,0)),m.normalizedSamples());
        assertEquals(2,m.totalImpulseNS()); assertEquals(.5,m.curveEndTimeS()); assertEquals(2,m.listedSamples().size());
    }
    @Test void whitespaceNewlinesCommentsBomAndMultipleMotorRecords() {
        for(String newline:List.of("\n","\r\n","\r")) {
            var file=read("\ufeff  \t\n".replace("\n",newline)+SYNTHETIC.replace("\n",newline)+newline+
                SYNTHETIC.replace("X9","Y10").replace("\n",newline));
            assertEquals(2,file.records().size()); assertEquals("Y10",file.records().get(1).header().designation());
            assertEquals(4,file.commentLines().size());
            assertTrue(new String(file.sourceBytes(),StandardCharsets.UTF_8).startsWith("\ufeff"));
        }
        assertThrows(IllegalArgumentException.class,()->read(SYNTHETIC.replace("\n;","")+"\n"+SYNTHETIC.substring(SYNTHETIC.indexOf("X9"))));
    }
    @Test void explicitOriginIsToleratedButNotDuplicatedOrRepaired() {
        var file=read(SYNTHETIC.replace(" .25 8"," 0 0\n .25 8"));
        assertEquals(3,file.records().get(0).normalizedSamples().size());
        assertEquals(file.records().get(0).listedSamples(),file.records().get(0).normalizedSamples());
        assertThrows(IllegalArgumentException.class,()->read(SYNTHETIC.replace(" .25 8"," 0 8\n .25 8")));
    }
    @ParameterizedTest @ValueSource(strings={"NaN","Infinity","-Infinity","1e400","0x1.0p0","2f","1_0"})
    void invalidNumericalTokensRejected(String token) {
        assertThrows(IllegalArgumentException.class,()->read(SYNTHETIC.replace("24 70",token+" 70")));
        assertThrows(IllegalArgumentException.class,()->read(SYNTHETIC.replace(".25 8",token+" 8")));
        assertThrows(IllegalArgumentException.class,()->read(SYNTHETIC.replace(".25 8",".25 "+token)));
    }
    @Test void malformedHeadersEmptyInvalidUtf8AndInvalidDomainsRejected() {
        for(String text:List.of("","; only comments",SYNTHETIC.replace(" OtherMaker",""),SYNTHETIC.replace("OtherMaker","Other Maker"),
                SYNTHETIC.replace("24 70","0 70"),SYNTHETIC.replace(".01 .03",".04 .03"),
                SYNTHETIC.replace(".01 .03","-.01 .03"),SYNTHETIC.replace(".01 .03",".01 0")))
            assertThrows(IllegalArgumentException.class,()->read(text));
        assertThrows(IllegalArgumentException.class,()->new RaspEngReader().read("x","r","s",null));
        assertThrows(IllegalArgumentException.class,()->new RaspEngReader().read("x","r","s",new byte[]{(byte)255}));
        assertThrows(IllegalArgumentException.class,()->new RaspEngReader().read("x"," ","s",SYNTHETIC.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void duplicateNonIncreasingNegativeAndInvalidTerminationRejected() {
        for(String sampleText:List.of(".25 8\n.25 0",".25 8\n.2 0","-.1 8\n.5 0",".25 -8\n.5 0",
                ".25 8\n.5 1",".1 8\n.2 0\n.3 4\n.5 0",".5 0",".25 0\n.5 0")) {
            var e=assertThrows(IllegalArgumentException.class,()->read("X 24 70 P .01 .03 M\n"+sampleText));
            assertTrue(e.getMessage().contains("synthetic.eng"));
        }
        assertThrows(IllegalArgumentException.class,()->read("X 24 70 P .01 .03 M\n1 1e308\n10 0"));
    }
    @Test void rawDelayTokensAndContextualSentinelInterpretationAreSeparate() {
        var profiles=Teknofest2026MotorCatalog.load(); String[] tokens={"-","1000","P"};
        for(int i=0;i<3;i++) {
            var source=profiles.get(i).thrustSources().get(0);
            assertEquals(tokens[i],source.record().header().rawEjectionToken()); assertEquals(tokens[i],source.ejectionMetadata().rawToken());
        }
        assertEquals(NUMERIC_SOURCE_TOKEN,profiles.get(1).thrustSources().get(0).record().header().ejectionMetadata().interpretation());
        assertEquals(List.of(1000.),profiles.get(1).thrustSources().get(0).ejectionMetadata().numericSourceOptionsS());
        assertEquals(UNRESOLVED_PROVIDER_TOKEN,profiles.get(1).thrustSources().get(0).ejectionMetadata().interpretation());
        assertEquals(UNRESOLVED_PROVIDER_TOKEN,profiles.get(0).thrustSources().get(0).ejectionMetadata().interpretation());
        assertEquals(NO_EJECTION_PLUGGED,profiles.get(2).thrustSources().get(0).ejectionMetadata().interpretation());
        var generic=read(SYNTHETIC).records().get(0).header().ejectionMetadata();
        assertEquals(MIXED_SOURCE_OPTIONS,generic.interpretation()); assertEquals(List.of(3.,5.),generic.numericSourceOptionsS()); assertTrue(generic.pluggedOption());
    }
    @Test void sourceNamesHardwareAndAlternativeMassEvidenceRemainDistinct() {
        var l=profile("L1256"); var names=l.identity().sourceNames().stream().map(IdentityReference::designation).toList();
        assertTrue(names.containsAll(List.of("TFL1256WS","L1256WS-P","L1256WS-PS","L1256")));
        assertEquals(HardwareKind.RELOADABLE,l.hardware().kind()); assertEquals("RMS-75/3840",l.hardware().hardwareId());
        assertTrue(l.identity().sourceNames().stream().filter(n->!n.role().equals("COMPETITION")).allMatch(n->n.relationshipConfidence()==Confidence.UNRESOLVED_MAPPING));
        var eng=l.massObservations().stream().filter(m->m.quantity().equals("ENG_INITIAL")).findFirst().orElseThrow();
        var nozzle=l.massObservations().stream().filter(m->m.quantity().equals("NOZZLE_SHEET_INITIAL")).findFirst().orElseThrow();
        assertEquals(3.905,eng.massKg()); assertEquals(3.902,nozzle.massKg());
        assertEquals(3.905,l.thrustSources().get(0).record().header().initialMassKg());
        assertEquals(12,l.massObservations().size()); assertTrue(l.massObservations().stream().allMatch(m->!m.sourceReference().isBlank()));
    }
    @Test void readinessIsNotRuntimeInstallationOrInertiaApproval() {
        for(var p:Teknofest2026MotorCatalog.load()) {
            var result=p.currentCoreCompatibility();
            assertEquals(Set.of(Readiness.CATALOG_AVAILABLE,Readiness.THRUST_SOURCE_VALIDATED),result.availableCapabilities());
            assertFalse(result.availableCapabilities().contains(Readiness.MOTOR_PHYSICS_READY));
            assertFalse(result.availableCapabilities().contains(Readiness.INSTALLATION_READY));
            assertFalse(result.availableCapabilities().contains(Readiness.INERTIA_READY));
            for(String code:List.of("CERTIFICATION_REFERENCE_MISSING","PHYSICAL_EJECTION_DELAY_UNREPRESENTABLE","INSTALLATION_DATUM_UNRESOLVED","INERTIA_INPUTS_UNAVAILABLE"))
                assertTrue(result.issues().stream().anyMatch(i->i.code().equals(code) && !i.fieldName().isBlank() && !i.detail().isBlank()));
            assertTrue(p.geometryObservations().stream().anyMatch(o->o.axialDatum().equals("UNRESOLVED")));
            assertTrue(p.geometryObservations().stream().anyMatch(o->o.quantity().equals("LABELED_AXIAL_PROJECTION") && o.value()==.5 && o.sourceUnit().equals("in")));
            assertTrue(p.geometryObservations().stream().anyMatch(o->o.confidence()==Confidence.SUPPORTED_INFERENCE && o.axialDatum().equals("UNRESOLVED")));
        }
        assertTrue(profile("M1850").compatibilityIssues().stream().anyMatch(i->i.detail().contains("0.875-in") && i.detail().contains("1.000-in")));
    }
    @Test void inputAndOutputSnapshotsAreImmutableAndRepeatable() {
        byte[] bytes=SYNTHETIC.getBytes(StandardCharsets.UTF_8); var original=bytes.clone();
        var reader=new RaspEngReader(); var file=reader.read("a.eng","R1","test",bytes);
        Arrays.fill(bytes,(byte)0); assertArrayEquals(original,file.sourceBytes());
        byte[] returned=file.sourceBytes(); returned[0]=0; assertArrayEquals(original,file.sourceBytes());
        assertEquals(file,reader.read("a.eng","R1","test",original));
        assertThrows(UnsupportedOperationException.class,()->file.records().clear());
        assertThrows(UnsupportedOperationException.class,()->file.commentLines().clear());
        assertThrows(UnsupportedOperationException.class,()->file.records().get(0).header().tokens().clear());
        assertThrows(UnsupportedOperationException.class,()->file.records().get(0).listedSamples().clear());
        assertThrows(UnsupportedOperationException.class,()->file.records().get(0).normalizedSamples().clear());
        var profiles=Teknofest2026MotorCatalog.load(); assertEquals(profiles,Teknofest2026MotorCatalog.load());
        assertThrows(UnsupportedOperationException.class,()->profiles.clear());
        assertThrows(UnsupportedOperationException.class,()->profiles.get(0).massObservations().clear());
        assertThrows(UnsupportedOperationException.class,()->profiles.get(0).currentCoreCompatibility().availableCapabilities().clear());
        var supplied=new ArrayList<>(profiles.get(0).massObservations()); var p=profiles.get(0);
        var copy=new CatalogMotorProfile(p.profileId(),p.identity(),p.hardware(),p.thrustSources(),supplied,p.geometryObservations(),p.compatibilityIssues());
        supplied.clear(); assertEquals(p,copy);
    }
    @Test void multipleSourceRevisionsCoexistWithoutChoosingOneOrAddingFlightModels() {
        var p=profile("L1256"); var c=p.thrustSources().get(0);
        var synthetic=read(SYNTHETIC); var second=new SourceCurve("independent-fixture",synthetic,0,
            synthetic.records().get(0).header().ejectionMetadata(),AcceptanceStatus.IMPORTED_NOT_ACCEPTED);
        var extended=new CatalogMotorProfile(p.profileId(),p.identity(),p.hardware(),List.of(c,second),p.massObservations(),p.geometryObservations(),p.compatibilityIssues());
        assertEquals(2,extended.thrustSources().size()); assertEquals(c.source().sha256(),extended.thrustSources().get(0).source().sha256());
        assertEquals(Set.of(Readiness.CATALOG_AVAILABLE),extended.currentCoreCompatibility().availableCapabilities());
        assertEquals(MotorType.SINGLE_USE,MotorCatalogs.AEROTECH_F50_4T.motorType());
        assertEquals(76.828387,new MotorCurveAnalyzer().analyze(MotorCatalogs.AEROTECH_F50_4T).totalImpulseNS(),76.828387*3e-15);
        assertEquals(0,MotorCatalogs.AEROTECH_F50_4T.thrustCurve().get(0).timeS());
    }
}
