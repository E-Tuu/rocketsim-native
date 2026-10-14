package org.roketsim.core.environment;

import java.util.ArrayList;
import java.util.List;
import static org.roketsim.core.math.NumericalValidation.requireFinite;

/** Kabul edilen USSA76 lower dry-air modeli; yedi layer recursive sea-level base'ten türer. */
public final class USStandardAtmosphere1976Lower {
    /** USSA76 sea-level sıcaklığı (K). */
    public static final double STANDARD_SEA_LEVEL_TEMPERATURE_K = 288.15;
    /** USSA76 sea-level basıncı (Pa). */
    public static final double STANDARD_SEA_LEVEL_PRESSURE_PA = 101325.0;
    /** Atmosfer ve constant gravity için tek standard g otoritesi (m/s²). */
    public static final double STANDARD_GRAVITY_M_S2 = 9.80665;
    /** Kabul edilen kuru hava gaz sabiti (J/(kg K)). */
    public static final double DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K = 287.053;
    /** Dahil lower domain sınırı (geopotential m). */
    public static final double USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M = -5000.0;
    /** Dahil upper domain sınırı (geopotential m). */
    public static final double USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M = 84852.0;

    private static final List<Double> HEIGHTS = List.of(0.,11000.,20000.,32000.,47000.,51000.,71000.,84852.);
    private static final List<Double> LAPSES = List.of(-.0065,0.,.001,.0028,0.,-.0028,-.002);
    private record Layer(double baseHeightM, double lapseKPerM, double baseTemperatureK, double basePressurePa) { }
    private record TemperaturePressure(double temperatureK, double pressurePa) { }
    private static final List<Layer> LAYERS = buildLayers();

    /** Parametresiz ve state tutmayan provider. */
    public USStandardAtmosphere1976Lower() { }

    private static TemperaturePressure temperaturePressure(Layer layer, double height) {
        double delta = height - layer.baseHeightM();
        double temperature = layer.baseTemperatureK() + layer.lapseKPerM() * delta;
        double pressure;
        if (layer.lapseKPerM() == 0.0) {
            pressure = layer.basePressurePa() * Math.exp(-STANDARD_GRAVITY_M_S2 * delta
                    / (DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K * layer.baseTemperatureK()));
        } else {
            pressure = layer.basePressurePa() * Math.pow(temperature / layer.baseTemperatureK(),
                    -STANDARD_GRAVITY_M_S2 / (DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K * layer.lapseKPerM()));
        }
        return new TemperaturePressure(temperature, pressure);
    }
    private static List<Layer> buildLayers() {
        List<Layer> layers = new ArrayList<>();
        double temperature = STANDARD_SEA_LEVEL_TEMPERATURE_K;
        double pressure = STANDARD_SEA_LEVEL_PRESSURE_PA;
        for (int i=0; i<LAPSES.size(); i++) {
            if (!layers.isEmpty()) {
                TemperaturePressure previous = temperaturePressure(layers.get(i-1), HEIGHTS.get(i));
                temperature = previous.temperatureK();
                pressure = previous.pressurePa();
            }
            layers.add(new Layer(HEIGHTS.get(i), LAPSES.get(i), temperature, pressure));
        }
        return List.copyOf(layers);
    }
    /** @param geopotentialHeightM geopotential yükseklik (m)
     * @return T/p/rho; iç sınır üst layer'a, terminal sınır son layer'a aittir */
    public DryAirAtmosphereState evaluate(double geopotentialHeightM) {
        requireFinite(geopotentialHeightM, "geopotential_height_m");
        if (!(USSA76_LOWER_MIN_GEOPOTENTIAL_HEIGHT_M <= geopotentialHeightM
                && geopotentialHeightM <= USSA76_LOWER_MAX_GEOPOTENTIAL_HEIGHT_M)) {
            throw new AtmosphereDomainError(geopotentialHeightM);
        }
        int index = 0;
        while (index < LAYERS.size()-1 && geopotentialHeightM >= HEIGHTS.get(index+1)) { index++; }
        TemperaturePressure values = temperaturePressure(LAYERS.get(index), geopotentialHeightM);
        double temperature = positive(values.temperatureK(), "temperature_K");
        double pressure = positive(values.pressurePa(), "pressure_Pa");
        double density = positive(pressure / (DRY_AIR_SPECIFIC_GAS_CONSTANT_J_KG_K * temperature), "density_kg_m3");
        return new DryAirAtmosphereState(temperature, pressure, density);
    }
    private static double positive(double value, String name) {
        requireFinite(value,name);
        if (value <= 0.0) { throw new IllegalArgumentException(name + " must be positive; got " + value); }
        return value;
    }
}
