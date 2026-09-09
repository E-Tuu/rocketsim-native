"""INT-001 sıkı dış sözleşme, accepted mapping ve gerçek demo köprüsü testleri."""

from copy import deepcopy
import json
import os
from pathlib import Path
import subprocess
import sys

import numpy as np
import pytest

import roketsim_native.integration.json_bridge as bridge


WORKSPACE_ROOT = Path(__file__).parents[4]
EXAMPLE_REQUEST = WORKSPACE_ROOT / "examples" / "integration" / "request-v1.json"
REQUEST_SCHEMA = (
    WORKSPACE_ROOT / "schemas" / "integration" / "v1" / "request.schema.json"
)
RESPONSE_SCHEMA = (
    WORKSPACE_ROOT / "schemas" / "integration" / "v1" / "response.schema.json"
)


@pytest.fixture()
def valid_payload() -> dict[str, object]:
    return json.loads(EXAMPLE_REQUEST.read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def real_runs():
    source = EXAMPLE_REQUEST.read_text(encoding="utf-8")
    request = bridge.parse_request_json(source)
    first = bridge.run_request(request)
    second = bridge.run_request(request)
    return first, second


def test_valid_request_parsing_and_machine_readable_contract(valid_payload):
    request = bridge.parse_request_json(json.dumps(valid_payload))
    assert request.schema_version == "1.0"
    assert request.model == "roketsim_native_3dof_v1"
    assert request.vehicle_preset == "verified_demo_vehicle_v1"
    assert request.motor_id == "AEROTECH_F50_4T"
    assert request.position_world_m == (0.0, 0.0, 0.0)

    request_schema = json.loads(REQUEST_SCHEMA.read_text(encoding="utf-8"))
    response_schema = json.loads(RESPONSE_SCHEMA.read_text(encoding="utf-8"))
    assert request_schema["$schema"].endswith("draft/2020-12/schema")
    assert request_schema["additionalProperties"] is False
    assert response_schema["$schema"].endswith("draft/2020-12/schema")
    assert set(response_schema["$defs"]) >= {"success", "failure", "sample", "event"}


@pytest.mark.parametrize(
    ("mutation", "field"),
    [
        (lambda value: value.pop("model"), "request"),
        (lambda value: value.__setitem__("unexpected", 1), "request"),
        (lambda value: value["motor"].__setitem__("extra", 1), "motor"),
        (lambda value: value["launch"].pop("velocity_world_m_s"), "launch"),
    ],
)
def test_missing_and_unknown_fields_are_rejected(valid_payload, mutation, field):
    mutation(valid_payload)
    with pytest.raises(bridge.IntegrationRequestError) as caught:
        bridge.parse_request_json(json.dumps(valid_payload))
    assert caught.value.error_code == "INVALID_REQUEST"
    assert caught.value.field_name == field


@pytest.mark.parametrize(
    ("source", "expected_code"),
    [
        ("{not-json", "INVALID_JSON"),
        ("[]", "INVALID_REQUEST"),
        ("", "INVALID_JSON"),
    ],
)
def test_malformed_or_non_object_json_is_rejected(source, expected_code):
    response, exit_code = bridge.process_request_json(source)
    assert exit_code == 2
    assert response["ok"] is False
    assert response["error"]["category"] == "request"
    assert response["error"]["code"] == expected_code


@pytest.mark.parametrize(
    ("path", "value", "expected_code"),
    [
        (("schema_version",), "2.0", "UNSUPPORTED_SCHEMA_VERSION"),
        (("model",), "other", "UNSUPPORTED_MODEL"),
        (("vehicle", "preset"), "other", "UNSUPPORTED_VEHICLE_PRESET"),
        (("motor", "id"), "other", "UNSUPPORTED_MOTOR"),
    ],
)
def test_unsupported_identifiers_have_stable_codes(
    valid_payload, path, value, expected_code
):
    target = valid_payload
    for key in path[:-1]:
        target = target[key]
    target[path[-1]] = value
    response, exit_code = bridge.process_request_json(json.dumps(valid_payload))
    assert exit_code == 2
    assert response["error"]["code"] == expected_code


@pytest.mark.parametrize(
    ("path", "value"),
    [
        (("launch", "position_world_m"), [0.0, 0.0]),
        (("launch", "velocity_world_m_s"), [0.0, 0.0, 0.0, 0.0]),
        (("numerics", "maximum_steps"), 5.0),
        (("motor", "ignition_time_s"), True),
    ],
)
def test_wrong_types_and_vector_shapes_are_rejected(valid_payload, path, value):
    target = valid_payload
    for key in path[:-1]:
        target = target[key]
    target[path[-1]] = value
    response, exit_code = bridge.process_request_json(json.dumps(valid_payload))
    assert exit_code == 2
    assert response["error"]["code"] == "INVALID_REQUEST"


@pytest.mark.parametrize("token", ["NaN", "Infinity", "-Infinity"])
def test_nonfinite_json_numbers_are_rejected(token):
    source = EXAMPLE_REQUEST.read_text(encoding="utf-8").replace("1200.0", token)
    response, exit_code = bridge.process_request_json(source)
    assert exit_code == 2
    assert response["error"]["code"] == "INVALID_REQUEST"


def test_request_values_map_exactly_to_accepted_configuration(valid_payload):
    valid_payload["motor"]["ignition_time_s"] = 2.25
    valid_payload["launch"]["position_world_m"] = [1.0, 2.0, 3.0]
    valid_payload["launch"]["velocity_world_m_s"] = [4.0, 5.0, 6.0]
    valid_payload["environment"]["geopotential_altitude_m"] = 950.0
    valid_payload["environment"]["air_mass_velocity_world_m_s"] = [7.0, 8.0, 9.0]
    valid_payload["numerics"]["fixed_step_s"] = 0.025
    valid_payload["numerics"]["maximum_steps"] = 321
    request = bridge.parse_request_json(json.dumps(valid_payload))
    configuration = bridge.build_simulation_configuration(request)

    assert configuration.physics_context.propulsion_timeline.ignition_time_s == 2.25
    assert configuration.physics_context.launch_environment_altitude_m == 950.0
    assert configuration.fixed_step_config.step_size_s == 0.025
    assert configuration.run_limits.maximum_steps == 321
    np.testing.assert_array_equal(
        configuration.launch_conditions.initial_position_world_m, [1.0, 2.0, 3.0]
    )
    np.testing.assert_array_equal(
        configuration.launch_conditions.initial_velocity_world_m_s, [4.0, 5.0, 6.0]
    )
    np.testing.assert_array_equal(
        configuration.physics_context.wind_model.evaluate(), [7.0, 8.0, 9.0]
    )


def test_upstream_domain_error_identity_is_preserved(valid_payload, monkeypatch):
    class SentinelDomainError(ValueError):
        error_code = "SENTINEL_DOMAIN_ERROR"
        field_name = "sentinel_field"

    def fail(_request):
        raise SentinelDomainError("accepted upstream failure")

    monkeypatch.setattr(bridge, "run_request", fail)
    response, exit_code = bridge.process_request_json(json.dumps(valid_payload))
    assert exit_code == 3
    assert response["error"] == {
        "category": "simulation",
        "code": "SENTINEL_DOMAIN_ERROR",
        "field": "sentinel_field",
        "message": "accepted upstream failure",
    }


def test_unexpected_bridge_error_is_hidden_and_uses_exit_70(valid_payload, monkeypatch):
    def fail(_request):
        raise RuntimeError("private traceback detail")

    monkeypatch.setattr(bridge, "run_request", fail)
    response, exit_code = bridge.process_request_json(json.dumps(valid_payload))
    assert exit_code == 70
    assert response["error"] == {
        "category": "internal",
        "code": "INTERNAL_ERROR",
        "field": None,
        "message": "Beklenmeyen iç köprü hatası",
    }
    assert "private traceback detail" not in json.dumps(response)


def test_accepted_launch_direction_validation_remains_authoritative(valid_payload):
    valid_payload["launch"]["direction_world_unit"] = [0.0, 0.0, 5.0]
    response, exit_code = bridge.process_request_json(json.dumps(valid_payload))
    assert exit_code == 3
    assert response["error"]["category"] == "simulation"
    assert response["error"]["code"] == "NON_UNIT_LAUNCH_DIRECTION"


def test_real_demo_success_response_preserves_history_and_direct_physics(real_runs):
    result, _ = real_runs
    response = bridge.serialize_result(result)
    assert response["schema_version"] == "1.0"
    assert response["ok"] is True
    assert response["model"] == "roketsim_native_3dof_v1"
    assert response["termination"] == {
        "reason": result.termination_reason.value,
        "time_s": result.termination_time_s,
        "simulation_duration_s": result.simulation_duration_s,
    }
    assert [event["type"] for event in response["events"]] == [
        event.event_type.value for event in result.events
    ]
    assert [sample["time_s"] for sample in response["samples"]] == [
        sample.point.time_s for sample in result.samples
    ]

    recorded = result.samples[37]
    serialized = response["samples"][37]
    assert serialized["physics"] == {
        "total_mass_kg": recorded.physics.rocket_mass_properties.total_mass_kg,
        "motor_mass_kg": recorded.physics.motor_mass_properties.mass_kg,
        "thrust_n": recorded.physics.motor_thrust_state.thrust_N,
        "mach": recorded.physics.flight_conditions.mach,
        "reynolds_number": recorded.physics.flight_conditions.reynolds,
        "dynamic_pressure_pa": recorded.physics.flight_conditions.dynamic_pressure_Pa,
        "drag_coefficient_cd0": recorded.physics.basic_drag.total_cd0,
        "acceleration_world_m_s2": list(
            recorded.physics.dynamics.acceleration_world_m_s2
        ),
        "air_mass_velocity_world_m_s": list(
            recorded.physics.air_mass_velocity_world_m_s
        ),
        "relative_air_velocity_world_m_s": list(recorded.physics.relative_flow),
    }


def test_repeated_real_request_serialization_is_deterministic_and_portable(real_runs):
    first, second = real_runs
    left = json.dumps(bridge.serialize_result(first), allow_nan=False, separators=(",", ":"))
    right = json.dumps(bridge.serialize_result(second), allow_nan=False, separators=(",", ":"))
    assert left == right
    assert "NaN" not in left
    assert "Infinity" not in left


def test_cli_writes_one_json_object_and_no_progress_to_stdout():
    source = EXAMPLE_REQUEST.read_text(encoding="utf-8")
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(Path.cwd() / "src")
    completed = subprocess.run(
        [sys.executable, "-m", "roketsim_native.integration.cli"],
        input=source,
        text=True,
        capture_output=True,
        check=False,
        env=environment,
    )
    assert completed.returncode == 0
    response = json.loads(completed.stdout)
    assert response["ok"] is True
    assert completed.stderr == ""
    assert completed.stdout.count("\n") == 1


@pytest.mark.parametrize(
    ("source", "expected_exit", "expected_category"),
    [
        ("{bad", 2, "request"),
        (
            EXAMPLE_REQUEST.read_text(encoding="utf-8").replace(
                "[0.0, 0.0, 1.0]", "[0.0, 0.0, 5.0]"
            ),
            3,
            "simulation",
        ),
    ],
)
def test_cli_exit_codes_and_error_body(source, expected_exit, expected_category):
    environment = os.environ.copy()
    environment["PYTHONPATH"] = str(Path.cwd() / "src")
    completed = subprocess.run(
        [sys.executable, "-m", "roketsim_native.integration.cli"],
        input=source,
        text=True,
        capture_output=True,
        check=False,
        env=environment,
    )
    response = json.loads(completed.stdout)
    assert completed.returncode == expected_exit
    assert response["ok"] is False
    assert response["error"]["category"] == expected_category
    assert completed.stderr.startswith(expected_category + ":")


def test_external_payload_is_not_mutated(valid_payload):
    original = deepcopy(valid_payload)
    bridge.parse_request_json(json.dumps(valid_payload))
    assert valid_payload == original
