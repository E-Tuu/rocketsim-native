package org.roketsim.core;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;

/** NAT-011 test fixture'ı ve test-only immutable record varyantları; production default değil. */
final class VehicleTestData {
    private VehicleTestData() { }
    static SingleStageRocketMaterials materials() {
        return new SingleStageRocketMaterials(MaterialCatalog.POLYSTYRENE,MaterialCatalog.CARDBOARD,
            MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD,MaterialCatalog.CARDBOARD);
    }
    static SingleStageRocketGeometry geometry() {
        return new SingleStageRocketGeometry(.1,new ConicalNoseGeometry(.3,NoseConstructionMode.HOLLOW_SHELL,.002),
            new CylindricalBodyGeometry(.7,.002),new TrapezoidalFinSetGeometry(4,.18,.08,.12,.05,.72,.003,
                FinCrossSection.SQUARE,FinAngularArrangement.EQUALLY_SPACED),
            new MotorAttachmentGeometry(new MotorMountTubeGeometry(.12,.029,.001,0),new CenteringRingPairGeometry(.003),.005),
            ReferenceGeometryPolicy.MAXIMUM_DIAMETER);
    }
    static String camel(String value) { return Arrays.stream(value.split("_")).reduce("",(a,b)->a.isEmpty()?b:a+Character.toUpperCase(b.charAt(0))+b.substring(1)); }
    static Object replace(Object record,String path,Object value) {
        String[] split=path.split("\\.",2);
        RecordComponent[] fields=record.getClass().getRecordComponents();
        Object[] values=new Object[fields.length];
        Class<?>[] types=new Class<?>[fields.length];
        boolean found=false;
        try {
            for (int i=0;i<fields.length;i++) {
                types[i]=fields[i].getType(); values[i]=fields[i].getAccessor().invoke(record);
                if (fields[i].getName().equals(camel(split[0]))) {
                    found=true;
                    values[i]=split.length==1?value:replace(values[i],split[1],value);
                }
            }
            if (!found) { throw new AssertionError("Unknown test field "+path); }
            return record.getClass().getDeclaredConstructor(types).newInstance(values);
        } catch (java.lang.reflect.InvocationTargetException error) {
            if(error.getCause() instanceof RuntimeException cause) { throw cause; }
            throw new AssertionError(error);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    static SingleStageRocketGeometry change(String path,Object value) {
        return (SingleStageRocketGeometry)replace(geometry(),path,value);
    }
}
