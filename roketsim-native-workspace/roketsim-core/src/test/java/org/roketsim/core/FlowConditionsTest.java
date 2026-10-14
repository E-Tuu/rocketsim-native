package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.*;
import static org.junit.jupiter.api.Assertions.*;

class FlowConditionsTest {
    private final RelativeFlowCalculator flow=new RelativeFlowCalculator();
    private final BasicFlightConditionsCalculator calculator=new BasicFlightConditionsCalculator();
    private final DryAirAtmosphereState state=new DryAirAtmosphereState(288,100000,2);
    private final DryAirProperties air=new DryAirProperties(260,.123,.00002,1.4);
    @Test void analyticFlowSigns() {
        double[][] rockets={{10,20,30},{0,100,0},{0,100,0},{0,100,0},{10,-20,30},{10,-20,30},{0,0,0}};
        double[][] winds={{0,0,0},{0,20,0},{0,-20,0},{10,0,0},{-5,4,3},{10,-20,30},{0,-10,0}};
        double[][] expected={{10,20,30},{0,80,0},{0,120,0},{-10,100,0},{15,-24,27},{0,0,0},{0,10,0}};
        for (int i=0;i<rockets.length;i++) { assertArrayEquals(expected[i],flow.evaluate(rockets[i],winds[i]).toArray()); }
    }
    @Test void flowCopiesAndOverflow() {
        double[] rocket={10,20,30}, wind={1,-2,3};
        var output=flow.evaluate(rocket,wind);
        output.toArray()[0]=99;
        assertArrayEquals(new double[]{10,20,30},rocket);
        assertArrayEquals(new double[]{1,-2,3},wind);
        rocket[0]=99;
        assertEquals(9,output.get(0));
        assertThrows(IllegalArgumentException.class,()->flow.evaluate(new double[]{Double.MAX_VALUE,0,0},new double[]{-Double.MAX_VALUE,0,0}));
    }
    @Test void analyticConditionsAndZero() {
        BasicFlightConditions result=calculator.evaluate(new double[]{3,4,12},state,air,.2);
        assertEquals(13,result.airspeedMS());
        assertEquals(13./260,result.mach(),(13./260)*1e-14);
        assertEquals(13*.2/.00002,result.reynolds(),(13*.2/.00002)*1e-14);
        assertEquals(169,result.dynamicPressurePa());
        assertEquals(new BasicFlightConditions(0,0,0,0),calculator.evaluate(new double[]{0,0,0},state,air,.2));
        assertEquals(result,calculator.evaluate(new double[]{-12,3,-4},state,air,.2));
        BasicFlightConditions doubled=calculator.evaluate(new double[]{6,8,24},state,air,.2);
        assertEquals(2*result.mach(),doubled.mach());
        assertEquals(2*result.reynolds(),doubled.reynolds());
        assertEquals(4*result.dynamicPressurePa(),doubled.dynamicPressurePa());
        assertEquals(2*result.reynolds(),calculator.evaluate(new double[]{3,4,12},state,air,.4).reynolds());
    }
    @Test void realUpstreamChain() {
        var atmosphere=new USStandardAtmosphere1976Lower().evaluate(0);
        var properties=new DryAirPropertiesCalculator().evaluate(atmosphere);
        var relative=flow.evaluate(new double[]{0,120,0},new double[]{0,20,0});
        var result=calculator.evaluate(relative,atmosphere,properties,.1);
        assertEquals(100,result.airspeedMS());
        assertEquals(.2938634853,result.mach(),5e-11);
        assertEquals(684594.26,result.reynolds(),.005);
        assertEquals(6124.9973,result.dynamicPressurePa(),5e-5);
    }
    @ParameterizedTest @ValueSource(doubles={0,-1})
    void positivePrerequisites(double value) {
        for (int field=0;field<4;field++) {
            final int index=field;
            FlightConditionsDomainError error=assertThrows(FlightConditionsDomainError.class,()->evaluatePrerequisite(index,value));
            assertEquals(new String[]{"density_kg_m3","speed_of_sound_m_s","kinematic_viscosity_m2_s","reference_length_m"}[field],error.fieldName());
            assertEquals(value,error.value());
        }
        assertThrows(FlightConditionsDomainError.class,()->calculator.evaluate(new double[]{0,0,0},state,air,0));
    }
    private BasicFlightConditions evaluatePrerequisite(int field,double value) {
        return calculator.evaluate(new double[]{3,4,12},new DryAirAtmosphereState(288,0,field==0?value:2),
            new DryAirProperties(field==1?value:260,Double.NaN,field==2?value:.00002,Double.NaN),field==3?value:.2);
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void nonfinite(double value) {
        for (int field=0;field<4;field++) {
            final int index=field;
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->evaluatePrerequisite(index,value)).getClass());
        }
        for (int i=0;i<3;i++) {
            double[] vector={0,0,0}; vector[i]=value;
            assertThrows(IllegalArgumentException.class,()->flow.evaluate(vector,new double[3]));
            assertThrows(IllegalArgumentException.class,()->flow.evaluate(new double[3],vector));
            assertThrows(IllegalArgumentException.class,()->calculator.evaluate(vector,state,air,.2));
        }
    }
    @Test void shapesAndUnconsumedAuthorities() {
        for (Object invalid:new Object[]{new double[]{1,2},new double[]{1,2,3,4},new double[][]{{1,2,3}},1.}) {
            assertThrows(IllegalArgumentException.class,()->flow.evaluate(invalid,new double[3]));
            assertThrows(IllegalArgumentException.class,()->flow.evaluate(new double[3],invalid));
            assertThrows(IllegalArgumentException.class,()->calculator.evaluate(invalid,state,air,.2));
        }
        assertEquals(calculator.evaluate(new double[]{3,4,12},state,air,.2),calculator.evaluate(new double[]{3,4,12},
            new DryAirAtmosphereState(Double.NaN,Double.NaN,2),new DryAirProperties(260,Double.NaN,.00002,Double.NaN),.2));
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,
            ()->calculator.evaluate(new double[3],new DryAirAtmosphereState(0,0,-1),new DryAirProperties(Double.NaN,0,1,0),1)).getClass());
    }
    @Test void extremeValuesNoSilentRepair() {
        for (int field=0;field<4;field++) {
            final int index=field;
            double value=(field==1 || field==2)?Double.MIN_VALUE:1e308;
            assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->evaluatePrerequisite(index,value)).getClass());
        }
        assertThrows(IllegalArgumentException.class,()->calculator.evaluate(new double[]{1e-200,0,0},state,air,.2));
        assertEquals(1e6,calculator.evaluate(new double[]{0,0,1e6},state,air,1e6).airspeedMS());
        // Hypot ara kare taşmasına/underflow'a dayanır; J-001 magnitude kullanılmaz.
        assertEquals(1e155,calculator.evaluate(new double[]{1e155,0,0},new DryAirAtmosphereState(0,0,1e-100),
            new DryAirProperties(1e155,0,1,0),1e-155).airspeedMS());
        assertThrows(IllegalArgumentException.class,()->calculator.evaluate(new double[]{Double.MAX_VALUE,Double.MAX_VALUE,0},state,air,.2));
    }
    @Test void snapshotsAndStatelessness() {
        assertTrue(BasicFlightConditions.class.isRecord());
        assertEquals(4,BasicFlightConditions.class.getRecordComponents().length);
        assertEquals(0,RelativeFlowCalculator.class.getDeclaredFields().length);
        assertEquals(0,BasicFlightConditionsCalculator.class.getDeclaredFields().length);
        double[] vector={3,4,12};
        assertEquals(calculator.evaluate(vector,state,air,.2),new BasicFlightConditionsCalculator().evaluate(vector,state,air,.2));
        assertArrayEquals(new double[]{3,4,12},vector);
    }
}
