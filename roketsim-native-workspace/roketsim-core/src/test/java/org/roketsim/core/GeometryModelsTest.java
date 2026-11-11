package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.roketsim.core.geometry.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryModelsTest {
    @Test void rawRecordsDoNotMoveResolverValidation() {
        var nose=new ConicalNoseGeometry(Double.NaN,"SOLID",null);
        assertTrue(Double.isNaN(nose.lengthM()));
        assertEquals("SOLID",nose.constructionMode());
        assertNull(nose.wallThicknessM());
        var fins=new TrapezoidalFinSetGeometry(true,0,0,0,0,0,0,null,null);
        assertEquals(true,fins.finCount());
    }
    @Test void immutableMandatorySchema() {
        for (Class<?> type:new Class<?>[]{ConicalNoseGeometry.class,CylindricalBodyGeometry.class,
            TrapezoidalFinSetGeometry.class,MotorMountTubeGeometry.class,CenteringRingPairGeometry.class,
            MotorAttachmentGeometry.class,SingleStageRocketGeometry.class}) {
            assertTrue(type.isRecord());
            assertEquals(1,type.getDeclaredConstructors().length);
            assertEquals(type.getRecordComponents().length,type.getDeclaredConstructors()[0].getParameterCount());
            for (var field:type.getDeclaredFields()) { assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers())); }
        }
        assertEquals(9,TrapezoidalFinSetGeometry.class.getRecordComponents().length);
        assertEquals(6,SingleStageRocketGeometry.class.getRecordComponents().length);
    }
    @Test void exactEnumValuesAndNoNewFamily() {
        assertEquals("maximum_diameter",ReferenceGeometryPolicy.MAXIMUM_DIAMETER.value());
        assertEquals("square",FinCrossSection.SQUARE.value());
        assertEquals("equally_spaced",FinAngularArrangement.EQUALLY_SPACED.value());
        assertEquals("HOLLOW_SHELL",NoseConstructionMode.HOLLOW_SHELL.value());
        assertEquals(1,ReferenceGeometryPolicy.values().length);
        assertEquals(1,FinCrossSection.values().length);
        assertEquals(1,FinAngularArrangement.values().length);
        assertEquals(2,NoseConstructionMode.values().length);
    }
}
