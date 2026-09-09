"""Java 17 ve diğer yerel istemciler için sürümlü JSON/CLI sınırı."""

from roketsim_native.integration.json_bridge import (
    BridgeRequestV1,
    IntegrationRequestError,
    build_simulation_configuration,
    parse_request_json,
    process_request_json,
    run_request,
    serialize_result,
)
from roketsim_native.integration.v11 import (
    CapabilitiesRequestV11,
    ExplicitVehicleRequestV11,
    build_explicit_simulation_configuration,
    capabilities_response,
    parse_v11_request,
    run_explicit_request,
)

__all__ = (
    "BridgeRequestV1",
    "IntegrationRequestError",
    "build_simulation_configuration",
    "parse_request_json",
    "process_request_json",
    "run_request",
    "serialize_result",
    "CapabilitiesRequestV11",
    "ExplicitVehicleRequestV11",
    "build_explicit_simulation_configuration",
    "capabilities_response",
    "parse_v11_request",
    "run_explicit_request",
)
