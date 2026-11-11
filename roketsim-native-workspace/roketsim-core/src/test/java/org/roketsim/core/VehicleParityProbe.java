package org.roketsim.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.roketsim.core.geometry.*;
import org.roketsim.core.materials.*;
import org.roketsim.core.mass.*;

/** Test-only tab protocol; Native geometry production modelini değiştirmez. */
public final class VehicleParityProbe {
    private VehicleParityProbe() { }
    static Object decoded(String value) {
        if(value.equals("null")) { return null; }
        if(value.startsWith("i:")) { return new java.math.BigInteger(value.substring(2)); }
        if(value.startsWith("d:")) { return Double.valueOf(value.substring(2)); }
        if(value.startsWith("b:")) { return Boolean.valueOf(value.substring(2)); }
        if(value.startsWith("s:")) { return new String(java.util.Base64.getDecoder().decode(value.substring(2)),StandardCharsets.UTF_8); }
        if(value.startsWith("e:")) {
            String[] parts=value.substring(2).split("\\.");
            return switch(parts[0]) {
                case "NoseConstructionMode" -> NoseConstructionMode.valueOf(parts[1]);
                case "ReferenceGeometryPolicy" -> ReferenceGeometryPolicy.valueOf(parts[1]);
                case "FinCrossSection" -> FinCrossSection.valueOf(parts[1]);
                case "FinAngularArrangement" -> FinAngularArrangement.valueOf(parts[1]);
                default -> throw new AssertionError(value);
            };
        }
        throw new AssertionError(value);
    }
    static String encoded(Object value) {
        if(value==null) { return "null"; }
        if(value instanceof Enum<?> choice) { return choice.name(); }
        if(value instanceof String text) { return "s:"+java.util.Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
        return value.toString();
    }
    public static void main(String[] args) throws Exception {
        try(var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while((line=reader.readLine())!=null) {
                try {
                    String[] tokens=line.split("\t");
                    if(tokens[0].equals("catalog")) {
                        System.out.println("OK\tcardboardName="+encoded(MaterialCatalog.CARDBOARD.name())+"\tcardboardDensityKgM3="+MaterialCatalog.CARDBOARD.densityKgM3()
                            +"\tpolystyreneName="+encoded(MaterialCatalog.POLYSTYRENE.name())+"\tpolystyreneDensityKgM3="+MaterialCatalog.POLYSTYRENE.densityKgM3());
                        continue;
                    }
                    if(tokens[0].equals("material")) {
                        Object name=null,density=null;
                        for(int i=1;i<tokens.length;i++) {
                            String[] change=tokens[i].split("=",2);
                            if(change[0].equals("name")) { name=decoded(change[1]); }
                            else if(change[0].equals("density_kg_m3")) { density=decoded(change[1]); }
                            else { throw new AssertionError(change[0]); }
                        }
                        var material=new BulkMaterial((String)name,((Number)density).doubleValue());
                        System.out.println("OK\tname="+encoded(material.name())+"\tdensityKgM3="+material.densityKgM3());
                        continue;
                    }
                    Object source=VehicleTestData.geometry();
                    Object materials=VehicleTestData.materials();
                    for(int i=1;i<tokens.length;i++) {
                        String[] change=tokens[i].split("=",2);
                        if(change[0].startsWith("resolved.")) { continue; }
                        if(change[0].startsWith("materials.")) {
                            materials=VehicleTestData.replace(materials,change[0].substring(10),decoded(change[1]));
                        } else {
                            String path=change[0].startsWith("geometry.")?change[0].substring(9):change[0];
                            source=VehicleTestData.replace(source,path,decoded(change[1]));
                        }
                    }
                    var resolved=new GeometryResolver().resolve((SingleStageRocketGeometry)source);
                    for(int i=1;i<tokens.length;i++) {
                        String[] change=tokens[i].split("=",2);
                        if(change[0].startsWith("resolved.")) {
                            resolved=(ResolvedRocketGeometry)VehicleTestData.replace(resolved,change[0].substring(9),decoded(change[1]));
                        }
                    }
                    StringBuilder output=new StringBuilder("OK");
                    for(var field:resolved.getClass().getRecordComponents()) {
                        if(!field.getName().equals("source")) {
                            output.append('\t').append(field.getName()).append('=').append(encoded(field.getAccessor().invoke(resolved)));
                        }
                    }
                    if(tokens[0].equals("mass")) {
                        var result=new StructuralMassPropertiesCalculator().evaluate(resolved,(SingleStageRocketMaterials)materials);
                        ComponentMassProperties[] components={result.nose(),result.body(),result.fins(),result.motorMount(),result.centeringRings()};
                        String[] names={"nose","body","fins","motorMount","centeringRings"};
                        for(int i=0;i<5;i++) {
                            var c=components[i];
                            output.append('\t').append(names[i]).append("MaterialName=").append(encoded(c.material().name()));
                            output.append('\t').append(names[i]).append("DensityKgM3=").append(c.material().densityKgM3());
                            output.append('\t').append(names[i]).append("MassKg=").append(c.massKg());
                            output.append('\t').append(names[i]).append("CgXGeoM=").append(c.cgXGeoM());
                        }
                        double moment=result.nose().massKg()*result.nose().cgXGeoM()+result.body().massKg()*result.body().cgXGeoM()
                            +result.fins().massKg()*result.fins().cgXGeoM()+result.motorMount().massKg()*result.motorMount().cgXGeoM()
                            +result.centeringRings().massKg()*result.centeringRings().cgXGeoM();
                        output.append("\tstructureMassKg=").append(result.structureMassKg());
                        output.append("\tstructureCgXGeoM=").append(result.structureCgXGeoM());
                        output.append("\tstructureMomentKgM=").append(moment);
                    }
                    System.out.println(output);
                } catch(MassValidationError error) {
                    System.out.println("ERROR\tMassValidationError\t"+error.errorCode()+"\t"+error.fieldName()+"\t"+encoded(error.value()));
                } catch(MaterialValidationError error) {
                    System.out.println("ERROR\tMaterialValidationError\t"+error.errorCode()+"\t"+error.fieldName()+"\t"+encoded(error.value()));
                } catch(GeometryValidationError error) {
                    System.out.println("ERROR\tGeometryValidationError\t"+error.errorCode()+"\t"+error.fieldName()+"\t"+encoded(error.value()));
                } catch(IllegalArgumentException error) {
                    System.out.println("ERROR\tValueError\t"+encoded(error.getMessage()));
                } catch(ArithmeticException error) {
                    System.out.println("ERROR\tOverflowError\t"+encoded(error.getMessage()));
                }
            }
        }
    }
}
