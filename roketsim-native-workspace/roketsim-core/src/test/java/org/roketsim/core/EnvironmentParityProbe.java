package org.roketsim.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.roketsim.core.environment.*;
import org.roketsim.core.flightconditions.*;

/** Test-only actual Python karşılaştırması; production JAR'da yer almaz. */
public final class EnvironmentParityProbe {
    private EnvironmentParityProbe() { }
    private static double number(String value) { return Double.parseDouble(value); }
    private static double[] vector(String value) {
        if (value.isEmpty()) { return new double[0]; }
        return Arrays.stream(value.split(",")).mapToDouble(Double::parseDouble).toArray();
    }
    private static double[] atmosphere(double height) {
        var state=new USStandardAtmosphere1976Lower().evaluate(height);
        return new double[]{state.temperatureK(),state.pressurePa(),state.densityKgM3()};
    }
    private static double[] air(double temperature,double pressure,double density) {
        var properties=new DryAirPropertiesCalculator().evaluate(new DryAirAtmosphereState(temperature,pressure,density));
        return new double[]{properties.speedOfSoundMS(),properties.dynamicViscosityPaS(),properties.kinematicViscosityM2S(),properties.specificHeatRatio()};
    }
    private static double[] conditions(double[] relative,double density,double sound,double kinematic,double length) {
        var result=new BasicFlightConditionsCalculator().evaluate(relative,new DryAirAtmosphereState(Double.NaN,Double.NaN,density),
                new DryAirProperties(sound,Double.NaN,kinematic,Double.NaN),length);
        return new double[]{result.airspeedMS(),result.mach(),result.reynolds(),result.dynamicPressurePa()};
    }
    private static double[] evaluate(String[] a) {
        return switch (a[0]) {
            case "orthometric" -> new double[]{Altitude.orthometricFromEllipsoidal(number(a[1]),number(a[2]))};
            case "ellipsoidal" -> new double[]{Altitude.ellipsoidalFromOrthometric(number(a[1]),number(a[2]))};
            case "agl" -> new double[]{Altitude.aglFromOrthometric(number(a[1]),number(a[2]))};
            case "from_agl" -> new double[]{Altitude.orthometricFromAgl(number(a[1]),number(a[2]))};
            case "bridge" -> new double[]{Altitude.atmosphericGeometricFromOrthometric(number(a[1]))};
            case "geopotential" -> new double[]{Altitude.geopotentialFromGeometric(number(a[1]))};
            case "geometric" -> new double[]{Altitude.geometricFromGeopotential(number(a[1]))};
            case "atmosphere" -> atmosphere(number(a[1]));
            case "air" -> air(number(a[1]),number(a[2]),number(a[3]));
            case "gravity" -> new ConstantGravityModel().evaluate().toArray();
            case "no_wind" -> new NoWindModel().evaluate().toArray();
            case "wind" -> new ConstantWindModel(vector(a[1])).evaluate().toArray();
            case "flow" -> new RelativeFlowCalculator().evaluate(vector(a[1]),vector(a[2])).toArray();
            case "conditions" -> conditions(vector(a[1]),number(a[2]),number(a[3]),number(a[4]),number(a[5]));
            case "chain" -> {
                double height=number(a[1]);
                double[] state=atmosphere(height);
                double[] properties=air(state[0],state[1],state[2]);
                double[] result=conditions(vector(a[2]),state[2],properties[0],properties[2],number(a[3]));
                double[] all=new double[11];
                System.arraycopy(state,0,all,0,3);
                System.arraycopy(properties,0,all,3,4);
                System.arraycopy(result,0,all,7,4);
                yield all;
            }
            case "constants" -> new double[]{Altitude.USSA76_GEOPOTENTIAL_EARTH_RADIUS_M,
                USStandardAtmosphere1976Lower.STANDARD_SEA_LEVEL_TEMPERATURE_K,
                USStandardAtmosphere1976Lower.STANDARD_SEA_LEVEL_PRESSURE_PA,
                USStandardAtmosphere1976Lower.STANDARD_GRAVITY_M_S2,
                USStandardAtmosphere1976Lower.DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K,
                USStandardAtmosphere1976Lower.USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M,
                USStandardAtmosphere1976Lower.USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M,
                DryAirPropertiesCalculator.DRY_AIR_SPECIFIC_HEAT_RATIO,
                DryAirPropertiesCalculator.SUTHERLAND_BETA,DryAirPropertiesCalculator.SUTHERLAND_CONSTANT_K};
            case "math" -> new double[]{Math.exp(number(a[1])),StrictMath.exp(number(a[1])),
                Math.pow(number(a[2]),number(a[3])),StrictMath.pow(number(a[2]),number(a[3])),
                Math.sqrt(number(a[2])),StrictMath.sqrt(number(a[2]))};
            case "hypot" -> {
                double[] v=vector(a[1]);
                yield new double[]{Math.hypot(Math.hypot(v[0],v[1]),v[2]),
                    StrictMath.hypot(StrictMath.hypot(v[0],v[1]),v[2])};
            }
            default -> throw new AssertionError(a[0]);
        };
    }
    public static void main(String[] args) throws Exception {
        try (var reader=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))) {
            String line;
            while ((line=reader.readLine())!=null) {
                try {
                    double[] output=evaluate(line.split("\t",-1));
                    StringBuilder text=new StringBuilder("OK");
                    for (double value:output) { text.append('\t').append(Double.toString(value)); }
                    System.out.println(text);
                } catch (AtmosphereDomainError error) {
                    System.out.println("ERROR\tAtmosphereDomainError\tgeopotential_height_m\t"
                        +error.requestedGeopotentialHeightM()+"\t"+error.minimumGeopotentialHeightM()+"\t"+error.maximumGeopotentialHeightM());
                } catch (AirPropertiesDomainError error) {
                    System.out.println("ERROR\tAirPropertiesDomainError\t"+error.fieldName()+"\t"+error.value());
                } catch (FlightConditionsDomainError error) {
                    System.out.println("ERROR\tFlightConditionsDomainError\t"+error.fieldName()+"\t"+error.value());
                } catch (IllegalArgumentException error) {
                    System.out.println("ERROR\tIllegalArgumentException\t"+error.getMessage());
                }
            }
        }
    }
}
