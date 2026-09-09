"""INT-002 açık araç V1.1 sözleşmesi, capabilities ve tam parite testleri."""

from copy import deepcopy
import json
from pathlib import Path

import numpy as np
import pytest

from roketsim_native.integration.json_bridge import process_request_json
from roketsim_native.integration.v11 import (
    CapabilitiesRequestV11,
    ExplicitVehicleRequestV11,
    build_explicit_simulation_configuration,
    parse_v11_request,
)


WORKSPACE_ROOT = Path(__file__).parents[4]
V10_REQUEST = WORKSPACE_ROOT / "examples" / "integration" / "request-v1.json"
V11_REQUEST = (
    WORKSPACE_ROOT / "examples" / "integration" / "request-v1.1-explicit-demo.json"
)
V11_SCHEMAS = WORKSPACE_ROOT / "schemas" / "integration" / "v1.1"


@pytest.fixture()
def explicit_payload() -> dict[str, object]:
    return json.loads(V11_REQUEST.read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def parity_responses():
    v10, v10_exit = process_request_json(V10_REQUEST.read_text(encoding="utf-8"))
    v11, v11_exit = process_request_json(V11_REQUEST.read_text(encoding="utf-8"))
    return v10, v10_exit, v11, v11_exit


def _component(payload, component_type):
    return next(
        component
        for component in payload["vehicle"]["components"]
        if component["type"] == component_type
    )


def test_capabilities_reports_only_accepted_v11_surface():
    response, exit_code = process_request_json(
        '{"schema_version":"1.1","operation":"capabilities"}'
    )
    assert exit_code == 0
    assert response["schema_version"] == "1.1"
    assert response["ok"] is True
    assert response["operation"] == "capabilities"
    assert response["models"] == ["roketsim_native_3dof_v1"]
    assert response["vehicle"]["component_types"] == [
        "nose_cone", "body_tube", "fin_set"
    ]
    assert response["vehicle"]["nose_shapes"] == ["conical"]
    assert response["vehicle"]["fin_cross_sections"] == ["square"]
    assert {item["id"] for item in response["vehicle"]["materials"]} == {
        "POLYSTYRENE", "CARDBOARD"
    }
    assert [item["id"] for item in response["motors"]] == ["AEROTECH_F50_4T"]
    rendered = json.dumps(response)
    for unsupported in ("transition", "parachute", "rounded", "airfoil", "6dof"):
        assert unsupported not in rendered.lower()


def test_capabilities_request_is_strict():
    response, exit_code = process_request_json(
        '{"schema_version":"1.1","operation":"capabilities","extra":true}'
    )
    assert exit_code == 2
    assert response["schema_version"] == "1.1"
    assert response["error"]["code"] == "INVALID_REQUEST"


@pytest.mark.parametrize(
    ("source", "expected_code"),
    [
        ('{"schema_version":"1.2","operation":"capabilities"}', "UNSUPPORTED_SCHEMA_VERSION"),
        ('{"schema_version":"1.1","operation":"export"}', "INVALID_REQUEST"),
    ],
)
def test_v11_rejects_unsupported_version_and_operation(source, expected_code):
    response, exit_code = process_request_json(source)
    assert exit_code == 2
    assert response["error"]["code"] == expected_code


def test_explicit_request_maps_exact_accepted_constructor_inputs(explicit_payload):
    request = parse_v11_request(explicit_payload)
    assert isinstance(request, ExplicitVehicleRequestV11)
    configuration = build_explicit_simulation_configuration(request)
    source = configuration.physics_context.resolved_geometry.source
    materials = configuration.physics_context.structural_mass_properties

    assert source.airframe_diameter_m == 0.1
    assert source.nose.length_m == 0.3
    assert source.body.length_m == 0.7
    assert source.fins.root_leading_edge_x_geo_m == 0.72
    assert source.fins.tip_leading_edge_offset_x_m == 0.05
    assert source.motor_attachment.mount_tube.length_m == 0.12
    assert source.motor_attachment.mount_tube.aft_recess_m == 0.0
    assert source.motor_attachment.motor_overhang_m == 0.005
    assert materials.nose.material.name == "Polystyrene"
    assert materials.body.material.name == "Cardboard"
    assert configuration.physics_context.motor_installation.motor.motor_id == (
        "aerotech_f50_4t"
    )


@pytest.mark.parametrize(
    "mutation",
    [
        lambda payload: _component(payload, "nose_cone").pop("length_m"),
        lambda payload: _component(payload, "body_tube").__setitem__("extra", 1),
        lambda payload: payload.__setitem__("extra", 1),
        lambda payload: payload["vehicle"]["surface_finish"].__setitem__("extra", 1),
    ],
)
def test_missing_and_unknown_fields_are_rejected(explicit_payload, mutation):
    mutation(explicit_payload)
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["code"] == "INVALID_REQUEST"


def test_duplicate_component_id_is_rejected(explicit_payload):
    explicit_payload["vehicle"]["components"][1]["id"] = "nose-1"
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["code"] == "DUPLICATE_COMPONENT_ID"
    assert response["error"]["field"] == "vehicle.components[1].id"


@pytest.mark.parametrize(
    ("component_type", "field", "value", "expected_code"),
    [
        ("nose_cone", "type", "transition", "UNSUPPORTED_COMPONENT_TYPE"),
        ("nose_cone", "shape", "ogive", "UNSUPPORTED_NOSE_SHAPE"),
        ("fin_set", "cross_section", "rounded", "UNSUPPORTED_FIN_CROSS_SECTION"),
        ("body_tube", "material_id", "ALUMINUM", "UNSUPPORTED_MATERIAL"),
    ],
)
def test_unsupported_component_choices_are_rejected(
    explicit_payload, component_type, field, value, expected_code
):
    _component(explicit_payload, component_type)[field] = value
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["code"] == expected_code


def test_unsupported_motor_is_rejected(explicit_payload):
    explicit_payload["motor"]["id"] = "UNKNOWN_MOTOR"
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["code"] == "UNSUPPORTED_MOTOR"
    assert response["error"]["field"] == "motor.id"


@pytest.mark.parametrize(
    ("component_type", "field", "value"),
    [
        ("fin_set", "count", 4.5),
        ("body_tube", "length_m", "0.7"),
    ],
)
def test_malformed_component_values_are_rejected(
    explicit_payload, component_type, field, value
):
    _component(explicit_payload, component_type)[field] = value
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["code"] == "INVALID_REQUEST"


def test_invalid_v11_vector_shape_is_rejected(explicit_payload):
    explicit_payload["launch"]["position_world_m"] = [0.0, 0.0]
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["code"] == "INVALID_REQUEST"
    assert response["error"]["field"] == "launch.position_world_m"


@pytest.mark.parametrize("token", ["NaN", "Infinity", "-Infinity"])
def test_nonfinite_v11_number_is_rejected(token):
    source = V11_REQUEST.read_text(encoding="utf-8").replace("1200.0", token)
    response, exit_code = process_request_json(source)
    assert exit_code == 2
    assert response["error"]["code"] == "INVALID_REQUEST"


def test_invalid_launch_direction_propagates_accepted_nat013_error(explicit_payload):
    explicit_payload["launch"]["direction_world_unit"] = [0.0, 0.0, 5.0]
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 3
    assert response["schema_version"] == "1.1"
    assert response["error"]["code"] == "NON_UNIT_LAUNCH_DIRECTION"


def test_invalid_geometry_propagates_accepted_resolver_error(explicit_payload):
    _component(explicit_payload, "body_tube")["wall_thickness_m"] = 0.06
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 3
    assert response["error"]["code"] == "BODY_WALL_TOO_THICK"


@pytest.mark.parametrize(
    ("component_type", "field", "value"),
    [
        ("nose_cone", "axial_position_m", 0.01),
        ("body_tube", "axial_position_m", 0.31),
        ("body_tube", "outer_diameter_m", 0.11),
    ],
)
def test_explicit_axial_and_common_diameter_contract_is_not_silently_repaired(
    explicit_payload, component_type, field, value
):
    _component(explicit_payload, component_type)[field] = value
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["category"] == "request"
    assert response["error"]["code"] == "INVALID_COMPONENT"


def test_motor_aft_reference_contract_is_exact(explicit_payload):
    explicit_payload["vehicle"]["motor_installation"]["axial_position_m"] = 1.006
    response, exit_code = process_request_json(json.dumps(explicit_payload))
    assert exit_code == 2
    assert response["error"]["category"] == "request"
    assert response["error"]["code"] == "INVALID_COMPONENT"
    assert response["error"]["field"] == "vehicle.motor_installation.axial_position_m"


def test_all_v11_schema_files_are_draft_2020_12_and_strict():
    expected = {
        "simulate-request.schema.json",
        "capabilities-request.schema.json",
        "simulate-response.schema.json",
        "capabilities-response.schema.json",
        "error-response.schema.json",
    }
    assert {path.name for path in V11_SCHEMAS.glob("*.json")} == expected
    for path in V11_SCHEMAS.glob("*.json"):
        schema = json.loads(path.read_text(encoding="utf-8"))
        assert schema["$schema"].endswith("draft/2020-12/schema")
        assert schema["additionalProperties"] is False


def test_v10_and_explicit_v11_demo_have_full_deterministic_parity(parity_responses):
    v10, v10_exit, v11, v11_exit = parity_responses
    assert v10_exit == v11_exit == 0
    assert v10["ok"] is v11["ok"] is True
    assert v10["termination"] == v11["termination"]
    assert v10["steps_performed"] == v11["steps_performed"]
    assert v10["events"] == v11["events"]
    assert v10["samples"] == v11["samples"]
    assert [event["type"] for event in v11["events"]] == [
        "burnout", "apogee", "ground"
    ]
    assert np.all(np.diff([sample["time_s"] for sample in v11["samples"]]) > 0.0)


def test_v11_response_is_complete_and_portable_json(parity_responses):
    _, _, response, _ = parity_responses
    assert set(response) == {
        "schema_version", "ok", "model", "termination", "steps_performed",
        "events", "samples",
    }
    expected_physics = {
        "total_mass_kg", "motor_mass_kg", "thrust_n", "mach",
        "reynolds_number", "dynamic_pressure_pa", "drag_coefficient_cd0",
        "acceleration_world_m_s2", "air_mass_velocity_world_m_s",
        "relative_air_velocity_world_m_s",
    }
    assert set(response["samples"][0]["physics"]) == expected_physics
    rendered = json.dumps(response, allow_nan=False)
    assert "NaN" not in rendered and "Infinity" not in rendered


def test_parse_v11_capabilities_model_is_immutable_value_object():
    request = parse_v11_request(
        {"schema_version": "1.1", "operation": "capabilities"}
    )
    assert isinstance(request, CapabilitiesRequestV11)
