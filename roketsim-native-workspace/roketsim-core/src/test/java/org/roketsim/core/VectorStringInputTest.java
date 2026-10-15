package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.math.Vector;
import org.roketsim.core.math.Quaternion;
import org.roketsim.core.math.Quaternions;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.*;
import static org.junit.jupiter.api.Assertions.*;

class VectorStringInputTest {
    @Test void pythonDigitSeparator() {
        assertEquals(10.0, Vector.of(new String[]{"1_0"}).get(0));
    }

    @Test void longDigitSeparators() {
        // Direct Python as_vector reference: ['0_' * 2000 + '1'] -> [1.0].
        assertEquals(1.0, Vector.of(new String[]{"0_".repeat(2000) + "1"}).get(0));
    }

    @ParameterizedTest @ValueSource(strings={"1f", "0x1.0p0"})
    void rejectJavaOnlyNumericText(String text) {
        assertThrows(IllegalArgumentException.class, () -> Vector.of(new String[]{text}));
    }

    @ParameterizedTest @ValueSource(strings={"1d", "1D", "1F", "\u00001.5\u0000", "\u001c1.5\u001c",
        "_1", "1_", "1__0", "1e_2", "1\u00a02", "", " ", "nan(1)"})
    void pythonConversionErrors(String text) {
        assertEquals("vector must contain numerical values",
            assertThrows(IllegalArgumentException.class, () -> Vector.of(new String[]{text})).getMessage());
    }

    @ParameterizedTest @ValueSource(strings={"inf", "+INF", "nan", "-NaN", "Infinity", "1e309"})
    void specialNumbersReachFiniteValidation(String text) {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Vector.of(new String[]{text}))
            .getMessage().startsWith("vector[0] must be finite"));
    }

    @Test void pythonUnicodeAndNumericForms() {
        assertEquals(1234, Vector.of(new String[]{"1_2.3_4e+0_2"}).get(0));
        assertEquals(12.3, Vector.of(new String[]{"\u0661_\u0662.\u0663"}).get(0));
        assertEquals(12.5, Vector.of(new String[]{"\uff11\uff12.\uff15"}).get(0));
        for (String text : new String[]{"\u00a01.5\u00a0", "\u00851.5\u0085", "\u20031.5\u2003", " 1.5 \t\n"}) {
            assertEquals(1.5, Vector.of(new String[]{text}).get(0));
        }
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(Vector.of(new String[]{"-0.0"}).get(0)));
        assertEquals(0, Vector.of(new String[]{"1e-400"}).get(0));
        assertEquals(Double.MIN_VALUE, Vector.of(new String[]{"4.9406564584124654e-324"}).get(0));
        assertEquals(Double.MAX_VALUE, Vector.of(new String[]{"1.7976931348623157e308"}).get(0));
    }

    @Test void sharedConsumers() {
        assertEquals(new Quaternion(10, 0, 0, 0), Quaternion.of(new String[]{"1_0", "0", "0", "0"}));
        assertEquals(10, Quaternions.norm(Quaternion.of(new String[]{"1_0", "0", "0", "0"})));
        String[] strings={"1_0", "0", "0"};
        assertEquals(Vector.of(new double[]{10,0,0}), new ConstantWindModel(strings).evaluate());
        assertEquals(Vector.of(new double[]{10,0,0}), new RelativeFlowCalculator().evaluate(strings, new double[3]));
        assertEquals(new BasicFlightConditionsCalculator().evaluate(new double[]{10,0,0},
            new DryAirAtmosphereState(288,100000,2),new DryAirProperties(260,1,.00002,1.4),.2),
            new BasicFlightConditionsCalculator().evaluate(strings,new DryAirAtmosphereState(288,100000,2),
                new DryAirProperties(260,1,.00002,1.4),.2));
        for (String invalid : new String[]{"1f", "inf"}) {
            assertThrows(IllegalArgumentException.class, () -> Quaternion.of(new String[]{invalid,"0","0","0"}));
            assertThrows(IllegalArgumentException.class, () -> new ConstantWindModel(new String[]{invalid,"0","0"}));
            assertThrows(IllegalArgumentException.class, () -> new RelativeFlowCalculator().evaluate(new String[]{invalid,"0","0"},new double[3]));
            assertThrows(IllegalArgumentException.class, () -> new BasicFlightConditionsCalculator().evaluate(new String[]{invalid,"0","0"},
                new DryAirAtmosphereState(288,100000,2),new DryAirProperties(260,1,.00002,1.4),.2));
        }
    }
}
