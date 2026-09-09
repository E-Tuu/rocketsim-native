"""Tek istek/tek yanıt RoketSim Native JSON komut satırı giriş noktası."""

import json
import sys

from roketsim_native.integration.json_bridge import (
    INTERNAL_EXIT_CODE,
    SCHEMA_VERSION,
    process_request_json,
)


def _internal_response(*, schema_version: str) -> dict[str, object]:
    return {
        "schema_version": schema_version,
        "ok": False,
        "error": {
            "category": "internal",
            "code": "INTERNAL_ERROR",
            "field": None,
            "message": "Yanıt portable JSON olarak serileştirilemedi",
        },
    }


def main() -> int:
    """stdin'i tüket, stdout'a yalnız bir portable JSON nesnesi yaz ve sonlan."""

    response, exit_code = process_request_json(sys.stdin.read())
    try:
        rendered = json.dumps(
            response,
            ensure_ascii=False,
            allow_nan=False,
            separators=(",", ":"),
        )
    except (TypeError, ValueError):
        response_version = response.get("schema_version", SCHEMA_VERSION)
        if not isinstance(response_version, str):
            response_version = SCHEMA_VERSION
        response = _internal_response(schema_version=response_version)
        exit_code = INTERNAL_EXIT_CODE
        rendered = json.dumps(
            response,
            ensure_ascii=False,
            allow_nan=False,
            separators=(",", ":"),
        )
    sys.stdout.write(rendered + "\n")
    if exit_code != 0:
        error = response["error"]
        if isinstance(error, dict):
            sys.stderr.write(f"{error['category']}:{error['code']}\n")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
