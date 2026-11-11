package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.roketsim.core.environment.*;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereAirTest {
    private final USStandardAtmosphere1976Lower atmosphere = new USStandardAtmosphere1976Lower();
    private final DryAirPropertiesCalculator calculator = new DryAirPropertiesCalculator();
    // NAT-009E/test_atmosphere.py analytic tolerance, tablo yuvarlama bütçesi değildir.
    private static void relative(double expected, double actual, double rtol) {
        assertEquals(expected,actual,Math.abs(expected)*rtol);
    }
    @Test void seaLevelAndEquations() {
        DryAirAtmosphereState state = atmosphere.evaluate(0);
        assertEquals(288.15,state.temperatureK());
        assertEquals(101325,state.pressurePa());
        relative(101325/(287.053*288.15),state.densityKgM3(),2e-15);
        DryAirProperties air = calculator.evaluate(state);
        relative(340.2940650819523,air.speedOfSoundMS(),3e-15);
        relative(1.789380278077583e-5,air.dynamicViscosityPaS(),3e-15);
        relative(air.dynamicViscosityPaS()/state.densityKgM3(),air.kinematicViscosityM2S(),3e-15);
        assertEquals(1.4,air.specificHeatRatio());
        double t5000 = 288.15-.0065*5000;
        relative(101325*Math.pow(t5000/288.15,-9.80665/(287.053*-.0065)),atmosphere.evaluate(5000).pressurePa(),2e-14);
        DryAirAtmosphereState base = atmosphere.evaluate(11000);
        relative(base.pressurePa()*Math.exp(-9.80665*4000/(287.053*base.temperatureK())),atmosphere.evaluate(15000).pressurePa(),2e-14);
    }
    @ParameterizedTest @CsvSource({"0,288.15,101325,0", "11000,216.65,22632.05546,5e-6",
        "20000,216.65,5474.884660,5e-7", "32000,228.65,868.017648,5e-7",
        "47000,270.65,110.906116,5e-7", "51000,270.65,66.9387501,5e-8",
        "71000,214.65,3.95641035,5e-9", "84852,186.946,.373382417,5e-10"})
    void frozenKnots(double height, double temperature, double pressure, double pressureAtol) {
        DryAirAtmosphereState state = atmosphere.evaluate(height);
        assertEquals(temperature,state.temperatureK(),1e-12);
        assertEquals(pressure,state.pressurePa(),pressureAtol);
    }
    @ParameterizedTest @ValueSource(doubles={11000,20000,32000,47000,51000,71000})
    void boundarySides(double height) {
        DryAirAtmosphereState center = atmosphere.evaluate(height);
        for (double side : new double[]{Math.nextDown(height),Math.nextUp(height)}) {
            DryAirAtmosphereState nearby = atmosphere.evaluate(side);
            relative(center.temperatureK(),nearby.temperatureK(),2e-14);
            relative(center.pressurePa(),nearby.pressurePa(),2e-14);
            relative(center.densityKgM3(),nearby.densityKgM3(),2e-14);
        }
    }
    @Test void denseDomainAndIdentities() {
        double previousPressure = Double.POSITIVE_INFINITY;
        double previousDensity = Double.POSITIVE_INFINITY;
        for (double height=-5000; height<=84852; height+=50) {
            DryAirAtmosphereState state = atmosphere.evaluate(height);
            DryAirProperties air = calculator.evaluate(state);
            assertTrue(state.pressurePa()>0 && state.pressurePa()<previousPressure);
            assertTrue(state.densityKgM3()>0 && state.densityKgM3()<previousDensity);
            relative(287.053,state.pressurePa()/(state.densityKgM3()*state.temperatureK()),3e-15);
            relative(1.4,air.speedOfSoundMS()*air.speedOfSoundMS()/(287.053*state.temperatureK()),3e-15);
            relative(state.densityKgM3(),air.dynamicViscosityPaS()/air.kinematicViscosityM2S(),3e-15);
            previousPressure=state.pressurePa(); previousDensity=state.densityKgM3();
        }
        assertTrue(atmosphere.evaluate(84852).densityKgM3()>0);
    }
    @ParameterizedTest @ValueSource(doubles={-5000.0001,84852.0001,90000})
    void domainError(double height) {
        AtmosphereDomainError error=assertThrows(AtmosphereDomainError.class,()->atmosphere.evaluate(height));
        assertEquals(height,error.requestedGeopotentialHeightM());
        assertEquals(-5000,error.minimumGeopotentialHeightM());
        assertEquals(84852,error.maximumGeopotentialHeightM());
    }
    @Test void exactDomainAndRoundedGeometricLimit() {
        assertTrue(atmosphere.evaluate(-5000).pressurePa()>0);
        assertThrows(AtmosphereDomainError.class,()->atmosphere.evaluate(Math.nextDown(-5000.)));
        assertThrows(AtmosphereDomainError.class,()->atmosphere.evaluate(Math.nextUp(84852.)));
        assertThrows(AtmosphereDomainError.class,()->atmosphere.evaluate(Altitude.geopotentialFromGeometric(86000)));
    }
    @ParameterizedTest @ValueSource(doubles={186.946,216.65,255.65,270.65,320.65,500})
    void airEquations(double temperature) {
        for (double density : new double[]{1e-5,.2,1,2}) {
            DryAirProperties air=calculator.evaluate(new DryAirAtmosphereState(temperature,Double.NaN,density));
            double mu=1.458e-6*Math.pow(temperature,1.5)/(temperature+110.4);
            relative(Math.sqrt(1.4*287.053*temperature),air.speedOfSoundMS(),3e-15);
            relative(mu,air.dynamicViscosityPaS(),3e-15);
            relative(mu/density,air.kinematicViscosityM2S(),3e-15);
            assertEquals(1.4,air.specificHeatRatio());
        }
    }
    @ParameterizedTest @ValueSource(doubles={0,-1})
    void airDomain(double value) {
        AirPropertiesDomainError t=assertThrows(AirPropertiesDomainError.class,
            ()->calculator.evaluate(new DryAirAtmosphereState(value,0,1)));
        assertEquals("temperature_K",t.fieldName()); assertEquals(value,t.value());
        AirPropertiesDomainError rho=assertThrows(AirPropertiesDomainError.class,
            ()->calculator.evaluate(new DryAirAtmosphereState(288,0,value)));
        assertEquals("density_kg_m3",rho.fieldName()); assertEquals(value,rho.value());
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void nonfinite(double value) {
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->atmosphere.evaluate(value)).getClass());
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,
            ()->calculator.evaluate(new DryAirAtmosphereState(value,0,1))).getClass());
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,
            ()->calculator.evaluate(new DryAirAtmosphereState(288,0,value))).getClass());
        // Basınç tüketilmez; snapshot kendiliğinden validation otoritesi değildir.
        assertEquals(calculator.evaluate(new DryAirAtmosphereState(288,0,1)),
            calculator.evaluate(new DryAirAtmosphereState(288,value,1)));
    }
    @Test void validationOrderAndExtremeValues() {
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,
            ()->calculator.evaluate(new DryAirAtmosphereState(-1,0,Double.NaN))).getClass());
        assertTrue(Double.isFinite(calculator.evaluate(new DryAirAtmosphereState(Double.MAX_VALUE,0,1)).speedOfSoundMS()));
        assertThrows(IllegalArgumentException.class,()->calculator.evaluate(new DryAirAtmosphereState(Double.MIN_VALUE,0,1)));
        assertThrows(IllegalArgumentException.class,()->calculator.evaluate(new DryAirAtmosphereState(288,0,Double.MIN_VALUE)));
        assertTrue(DryAirProperties.class.isRecord());
        assertTrue(DryAirAtmosphereState.class.isRecord());
        assertEquals(4,DryAirProperties.class.getRecordComponents().length);
        assertEquals(3,DryAirAtmosphereState.class.getRecordComponents().length);
        assertEquals(atmosphere.evaluate(1200),new USStandardAtmosphere1976Lower().evaluate(1200));
    }
}
