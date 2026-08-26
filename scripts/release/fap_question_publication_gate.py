#!/usr/bin/env python3
"""Validate a host-exported Analytics question Function publication catalog.

This is a read-only release gate. It deliberately has no FAP credentials,
management endpoint, retry, repair, or apply path.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from runtime_launcher_package import (
    EXPECTED_FAP_FUNCTION_DIGESTS,
    EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY,
)


PUBLICATION_FIELDS = {
    "functionRef",
    "name",
    "displayName",
    "description",
    "searchText",
    "tags",
    "inputSchema",
    "outputSchema",
    "examples",
    "schemaDigest",
    "projectionDigest",
}
FORBIDDEN_MODEL_REVISION_MARKERS = (
    "expectedmodelrevision",
    "modelrevision",
    "model_revision_conflict",
)


class PublicationValidationError(RuntimeError):
    """Raised when host publication material is not the exact governed catalog."""


def canonical_digest(value: Any) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        allow_nan=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return "sha256:" + hashlib.sha256(encoded).hexdigest()


def _reject_model_revision_markers(value: Any, path: str = "$") -> None:
    if isinstance(value, dict):
        for key, nested in value.items():
            _reject_model_revision_markers(str(key), f"{path}.<key>")
            _reject_model_revision_markers(nested, f"{path}.{key}")
    elif isinstance(value, list):
        for index, nested in enumerate(value):
            _reject_model_revision_markers(nested, f"{path}[{index}]")
    elif isinstance(value, str):
        lowered = value.lower()
        marker = next(
            (candidate for candidate in FORBIDDEN_MODEL_REVISION_MARKERS
             if candidate in lowered),
            None,
        )
        if marker is not None:
            raise PublicationValidationError(
                f"caller-visible model revision marker {marker} found at {path}"
            )


def _publications(value: Any) -> list[dict[str, Any]]:
    if isinstance(value, dict):
        value = value.get("functions")
    if not isinstance(value, list):
        raise PublicationValidationError(
            "catalog must be a JSON array or an object containing a functions array"
        )
    if not all(isinstance(item, dict) for item in value):
        raise PublicationValidationError("every Function publication must be a JSON object")
    return value


def validate_catalog(value: Any) -> dict[str, Any]:
    publications = _publications(value)
    actual_refs = [item.get("functionRef") for item in publications]
    if tuple(actual_refs) != EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY:
        raise PublicationValidationError(
            "host catalog must contain the exact Analytics question FunctionRef set "
            f"in canonical order; actual={actual_refs}"
        )

    for index, publication in enumerate(publications):
        function_ref = publication["functionRef"]
        if set(publication) != PUBLICATION_FIELDS:
            raise PublicationValidationError(
                f"Function publication {function_ref} has unexpected fields: "
                f"{sorted(set(publication) ^ PUBLICATION_FIELDS)}"
            )
        _reject_model_revision_markers(publication, f"$.functions[{index}]")

        schema_value = {
            "functionRef": function_ref,
            "name": publication["name"],
            "inputSchema": publication["inputSchema"],
            "outputSchema": publication["outputSchema"],
        }
        actual_schema_digest = canonical_digest(schema_value)
        if publication["schemaDigest"] != actual_schema_digest:
            raise PublicationValidationError(
                f"Function schemaDigest is not canonical for {function_ref}"
            )

        projection_value = {
            key: publication[key]
            for key in PUBLICATION_FIELDS
            if key != "projectionDigest"
        }
        actual_projection_digest = canonical_digest(projection_value)
        if publication["projectionDigest"] != actual_projection_digest:
            raise PublicationValidationError(
                f"Function projectionDigest is not canonical for {function_ref}"
            )

        expected = EXPECTED_FAP_FUNCTION_DIGESTS[function_ref]
        if (actual_schema_digest, actual_projection_digest) != expected:
            raise PublicationValidationError(
                f"Function publication does not match the frozen release catalog: {function_ref}"
            )

    return {
        "contractVersion": "foggy.analytics.question-host-publication-gate.v1",
        "status": "READY_FOR_HOST_APPLY",
        "mutationPerformed": False,
        "publicationMode": "HOST_MANAGED_EXPLICIT",
        "functionRefs": actual_refs,
        "digests": {
            function_ref: {
                "schemaDigest": EXPECTED_FAP_FUNCTION_DIGESTS[function_ref][0],
                "projectionDigest": EXPECTED_FAP_FUNCTION_DIGESTS[function_ref][1],
            }
            for function_ref in actual_refs
        },
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Validate an exact host-exported Analytics question Function catalog; "
            "this command never calls or mutates FAP."
        )
    )
    parser.add_argument(
        "--catalog-json",
        required=True,
        help="BusinessFunctionProjection JSON array/envelope, or - for stdin",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.catalog_json == "-":
            value = json.load(sys.stdin)
        else:
            value = json.loads(Path(args.catalog_json).read_text(encoding="utf-8"))
        print(json.dumps(validate_catalog(value), ensure_ascii=False, indent=2))
        return 0
    except (OSError, json.JSONDecodeError, PublicationValidationError) as exc:
        print(f"FAP publication gate failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
