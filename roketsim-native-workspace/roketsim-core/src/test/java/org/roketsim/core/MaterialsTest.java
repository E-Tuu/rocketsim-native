package org.roketsim.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.roketsim.core.materials.*;
import static org.junit.jupiter.api.Assertions.*;

class MaterialsTest {
    @Test void exactCatalogAndExplicitAssignments() {
        assertEquals(new BulkMaterial("Cardboard",680),MaterialCatalog.CARDBOARD);
        assertEquals(new BulkMaterial("Polystyrene",1050),MaterialCatalog.POLYSTYRENE);
        assertEquals(2,MaterialCatalog.class.getFields().length);
        var third=new BulkMaterial("Test material",900);
        var assignment=new SingleStageRocketMaterials(MaterialCatalog.CARDBOARD,third,MaterialCatalog.POLYSTYRENE,third,MaterialCatalog.CARDBOARD);
        assertSame(third,assignment.body()); assertSame(third,assignment.motorMount());
        assertTrue(BulkMaterial.class.isRecord()); assertTrue(SingleStageRocketMaterials.class.isRecord());
        assertEquals(5,SingleStageRocketMaterials.class.getRecordComponents().length);
        assertEquals(5,SingleStageRocketMaterials.class.getDeclaredConstructors()[0].getParameterCount());
        assertEquals("  name  ",new BulkMaterial("  name  ",1).name());
    }
    @ParameterizedTest @ValueSource(doubles={0,-1})
    void densityDomain(double value) {
        var error=assertThrows(MaterialValidationError.class,()->new BulkMaterial("Test",value));
        assertEquals("NON_POSITIVE_DENSITY",error.errorCode()); assertEquals("density_kg_m3",error.fieldName()); assertEquals(value,error.value());
    }
    @ParameterizedTest @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void nonfinite(double value) {
        assertEquals(IllegalArgumentException.class,assertThrows(IllegalArgumentException.class,()->new BulkMaterial("",value)).getClass());
    }
    @ParameterizedTest @ValueSource(strings={""," ","\t\n","\u00a0","\u0085","\u2007","\u202f","\u001c"})
    void pythonWhitespace(String name) {
        var error=assertThrows(MaterialValidationError.class,()->new BulkMaterial(name,1));
        assertEquals("EMPTY_MATERIAL_NAME",error.errorCode()); assertEquals("name",error.fieldName()); assertEquals(name,error.value());
    }
    @Test void validationOrderAndSmallPositiveDomain() {
        assertEquals("NON_POSITIVE_DENSITY",assertThrows(MaterialValidationError.class,()->new BulkMaterial("",-1)).errorCode());
        assertEquals(Double.MIN_VALUE,new BulkMaterial("Tiny",Double.MIN_VALUE).densityKgM3());
        assertEquals(Double.MAX_VALUE,new BulkMaterial("Large",Double.MAX_VALUE).densityKgM3());
        assertThrows(NullPointerException.class,()->new BulkMaterial(null,1));
    }
}
