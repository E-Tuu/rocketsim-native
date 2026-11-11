package org.roketsim.core.simulation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.roketsim.core.geometry.GeometryResolver;
import org.roketsim.core.mass.AttachedHardwareMass;
import org.roketsim.core.mass.MassValidationError;
import org.roketsim.core.mass.StructuralMassOverride;
import org.roketsim.core.mass.StructuralMassProperties;
import org.roketsim.core.mass.StructuralMassPropertiesCalculator;
import org.roketsim.core.mass.VehicleConfigurationError;
import org.roketsim.core.materials.SingleStageRocketMaterials;

/** Validated axial mass selection and evidence for one source-backed 3DOF configuration. */
public final class PreparedSourceVehicle3DOF {
    private static final Set<String> RESERVED = Set.of(
        "structure:nose", "structure:body", "structure:fins", "structure:motor-mount",
        "structure:centering-rings", "motor:installed");

    public enum Origin { CALCULATED, MEASURED, MODEL_BASED }

    /** Declares an item already covered by both measurements of its structural owner. */
    public record IncludedItem(String itemId, String structuralOwnerId) {}

    /** Evidence for the two fields of an existing separately accounted hardware entry. */
    public record HardwareEvidence(String componentId, Origin massOrigin, String massReference,
            Origin cgOrigin, String cgReference) {}

    private final String configurationId;
    private final String designSourceReference;
    private final SingleStageRocketMaterials materials;
    private final SourceMotorGroundRunConfiguration3DOF calculatedRun;
    private final SourceMotorGroundRunConfiguration3DOF runConfiguration;
    private final List<StructuralMassOverride> overrides;
    private final List<IncludedItem> includedItems;
    private final List<HardwareEvidence> hardwareEvidence;

    private PreparedSourceVehicle3DOF(String configurationId, String designSourceReference,
            SingleStageRocketMaterials materials, SourceMotorGroundRunConfiguration3DOF calculatedRun,
            SourceMotorGroundRunConfiguration3DOF runConfiguration, List<StructuralMassOverride> overrides,
            List<IncludedItem> includedItems, List<HardwareEvidence> hardwareEvidence) {
        this.configurationId = configurationId;
        this.designSourceReference = designSourceReference;
        this.materials = materials;
        this.calculatedRun = calculatedRun;
        this.runConfiguration = runConfiguration;
        this.overrides = overrides;
        this.includedItems = includedItems;
        this.hardwareEvidence = hardwareEvidence;
    }

    /** Validates the calculated baseline, selects measured fields, and checks the declared inventory. */
    public static PreparedSourceVehicle3DOF prepare(String configurationId, String designSourceReference,
            SingleStageRocketMaterials materials, SourceMotorGroundRunConfiguration3DOF calculatedRun,
            List<StructuralMassOverride> overrides, List<IncludedItem> includedItems,
            List<HardwareEvidence> hardwareEvidence) {
        if (blank(configurationId))
            throw new VehicleConfigurationError("INVALID_CONFIGURATION_ID", "configurationId", "revision identifier required");
        if (blank(designSourceReference))
            throw new VehicleConfigurationError("MISSING_PROPERTY_PROVENANCE", "designSourceReference", "design reference required");
        required(materials, "materials");
        required(calculatedRun, "calculatedRun");
        required(overrides, "overrides");
        required(includedItems, "includedItems");
        required(hardwareEvidence, "hardwareEvidence");
        for (int i = 0; i < overrides.size(); i++) {
            String field = "overrides[" + i + "]";
            var override = overrides.get(i);
            required(override, field);
            required(override.massKg(), field + ".massKg");
            required(override.cgXGeoM(), field + ".cgXGeoM");
        }
        overrides = List.copyOf(overrides);
        includedItems = snapshot(includedItems, "includedItems");
        hardwareEvidence = snapshot(hardwareEvidence, "hardwareEvidence");

        var context = calculatedRun.physicsContext();
        var geometry = context.resolvedGeometry();
        var raw = geometry.source();
        required(raw, "geometry.source");
        required(raw.nose(), "geometry.nose");
        required(raw.body(), "geometry.body");
        required(raw.fins(), "geometry.fins");
        required(raw.motorAttachment(), "geometry.motorAttachment");
        required(raw.motorAttachment().mountTube(), "geometry.motorAttachment.mountTube");
        required(raw.motorAttachment().centeringRings(), "geometry.motorAttachment.centeringRings");
        required(materials.nose(), "materials.nose");
        required(materials.body(), "materials.body");
        required(materials.fins(), "materials.fins");
        required(materials.motorMount(), "materials.motorMount");
        required(materials.centeringRings(), "materials.centeringRings");

        var resolved = new GeometryResolver().resolve(raw);
        if (!resolved.equals(geometry))
            throw new VehicleConfigurationError("BASELINE_GEOMETRY_MISMATCH", "calculatedRun.physicsContext.resolvedGeometry",
                "must match the geometry resolver output");
        var calculator = new StructuralMassPropertiesCalculator();
        var baseline = calculator.evaluate(resolved, materials);
        if (!baseline.equals(context.structuralMassProperties()))
            throw new VehicleConfigurationError("BASELINE_STRUCTURAL_MASS_MISMATCH", "calculatedRun.physicsContext.structuralMassProperties",
                "must match the calculated structure for the selected geometry and materials");
        var selected = overrides.isEmpty() ? baseline : calculator.evaluateWithMeasurements(resolved, materials, overrides);

        validateInventory(calculatedRun.attachedHardware(), overrides, includedItems, hardwareEvidence);
        var effective = overrides.isEmpty() ? calculatedRun : withStructure(calculatedRun, selected);
        return new PreparedSourceVehicle3DOF(configurationId, designSourceReference, materials, calculatedRun,
            effective, overrides, includedItems, hardwareEvidence);
    }

    private static void validateInventory(List<AttachedHardwareMass> hardware, List<StructuralMassOverride> overrides,
            List<IncludedItem> includedItems, List<HardwareEvidence> evidence) {
        Map<String, String> owners = new HashMap<>();
        for (String id : RESERVED) owners.put(id, id);
        Set<String> activeHardware = new HashSet<>();
        for (int i = 0; i < hardware.size(); i++) {
            var item = hardware.get(i);
            String id = item.componentId();
            claim(owners, id, id, "attachedHardware[" + i + "].componentId");
            activeHardware.add(id);
            // The accepted auxiliary predicates are checked here before any run is started.
            if (!Double.isFinite(item.massKg()) || item.massKg() <= 0)
                throw new MassValidationError("INVALID_AUXILIARY_MASS", "auxiliary_masses_kg[" + i + "]", item.massKg());
            if (!Double.isFinite(item.cgXGeoM()))
                throw new MassValidationError("INVALID_AUXILIARY_CG", "auxiliary_cg_x_geo_m[" + i + "]", item.cgXGeoM());
        }

        Set<String> measuredOwners = new HashSet<>();
        for (var override : overrides) {
            if (override.massKg().isPresent() && override.cgXGeoM().isPresent())
                measuredOwners.add(override.componentId());
        }
        for (int i = 0; i < includedItems.size(); i++) {
            var item = includedItems.get(i);
            String field = "includedItems[" + i + "]";
            if (blank(item.itemId()))
                throw new VehicleConfigurationError("MISSING_VEHICLE_INPUT", field + ".itemId", "nonblank item ID required");
            if (!measuredOwners.contains(item.structuralOwnerId()))
                throw new VehicleConfigurationError("INVALID_INCLUDED_ITEM_OWNER", field + ".structuralOwnerId",
                    "owner must supply both measured mass and CG");
            claim(owners, item.itemId(), item.structuralOwnerId(), field + ".itemId");
        }

        Set<String> evidenced = new HashSet<>();
        for (int i = 0; i < evidence.size(); i++) {
            var row = evidence.get(i);
            String field = "hardwareEvidence[" + i + "]";
            if (!activeHardware.contains(row.componentId()) || !evidenced.add(row.componentId()))
                throw new VehicleConfigurationError("INVALID_HARDWARE_EVIDENCE", field + ".componentId",
                    "unmatched or duplicate evidence ID");
            if (row.massOrigin() == null || blank(row.massReference()))
                throw new VehicleConfigurationError("INVALID_HARDWARE_EVIDENCE", field + ".mass", "origin and reference required");
            if (row.cgOrigin() == null || blank(row.cgReference()))
                throw new VehicleConfigurationError("INVALID_HARDWARE_EVIDENCE", field + ".cg", "origin and reference required");
        }
        for (var item : hardware) {
            if (!evidenced.contains(item.componentId()))
                throw new VehicleConfigurationError("INVALID_HARDWARE_EVIDENCE", "hardwareEvidence",
                    "missing evidence for " + item.componentId());
        }
    }

    private static void claim(Map<String, String> owners, String id, String owner, String field) {
        if (RESERVED.contains(id))
            throw new VehicleConfigurationError("CONFLICTING_MASS_OWNERSHIP", field, "reserved identity " + id);
        String previous = owners.putIfAbsent(id, owner);
        if (previous != null)
            throw new VehicleConfigurationError(previous.equals(owner) ? "DUPLICATE_COMPONENT_ID" : "CONFLICTING_MASS_OWNERSHIP",
                field, id + " already accounted for by " + previous);
    }

    private static SourceMotorGroundRunConfiguration3DOF withStructure(
            SourceMotorGroundRunConfiguration3DOF run, StructuralMassProperties structure) {
        var c = run.physicsContext();
        var effective = new RuntimePhysicsEvaluationContext3DOF(c.resolvedGeometry(), c.aerodynamicSurfaces(),
            structure, c.motorInstallation(), c.motorPropertyModelProfile(), c.basicDragModelProfile(),
            c.translationalDynamicsModelProfile(), c.environmentPositionMappingModel(), c.launchEnvironmentAltitudeM(),
            c.propulsionTimeline(), c.atmosphereModel(), c.airPropertiesCalculator(), c.gravityModel(), c.windModel());
        return new SourceMotorGroundRunConfiguration3DOF(run.launchConditions(), effective, run.eventDetectionProfile(),
            run.fixedStepConfig(), run.terminalEventHandlingModel(), run.attachedHardware(), run.launchGuide(),
            run.recovery(), run.alignmentMode());
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static void required(Object value, String field) {
        if (value == null) throw new VehicleConfigurationError("MISSING_VEHICLE_INPUT", field, "required");
    }

    private static <T> List<T> snapshot(List<T> values, String field) {
        for (int i = 0; i < values.size(); i++) required(values.get(i), field + "[" + i + "]");
        return List.copyOf(values);
    }

    public String configurationId() { return configurationId; }
    public String designSourceReference() { return designSourceReference; }
    public SingleStageRocketMaterials materials() { return materials; }
    public SourceMotorGroundRunConfiguration3DOF calculatedRun() { return calculatedRun; }
    public SourceMotorGroundRunConfiguration3DOF runConfiguration() { return runConfiguration; }
    public List<StructuralMassOverride> overrides() { return overrides; }
    public List<IncludedItem> includedItems() { return includedItems; }
    public List<HardwareEvidence> hardwareEvidence() { return hardwareEvidence; }

    public CompletedRun runUntilGround(RunControl3DOF control) {
        return new CompletedRun(this, new SimulationEngine3DOF().runSourceUntilGround(runConfiguration, control));
    }

    /** Associates the exact preparation with the engine result, including partial histories. */
    public static final class CompletedRun {
        private final PreparedSourceVehicle3DOF configuration;
        private final GroundRunResult3DOF result;

        private CompletedRun(PreparedSourceVehicle3DOF configuration, GroundRunResult3DOF result) {
            this.configuration = configuration;
            this.result = result;
        }

        public PreparedSourceVehicle3DOF configuration() { return configuration; }
        public GroundRunResult3DOF result() { return result; }
    }
}
