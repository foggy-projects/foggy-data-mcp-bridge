#!/usr/bin/env python3
"""Export, synchronize, and validate the Analytics question FAP delivery.

The Java Function catalog is the publication source of truth. This tool has no
FAP credential, management endpoint, retry, repair, or remote apply operation.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

from fap_question_publication_gate import (
    PublicationValidationError,
    validate_catalog,
)


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
QUESTION_ROOT = (
    REPOSITORY_ROOT
    / "addons"
    / "foggy-analytics-console"
    / "src"
    / "main"
    / "resources"
    / "fap"
    / "analytics-question-answering"
)
SKILL_METADATA = QUESTION_ROOT / "skill-metadata.json"
SCHEMA_DELIVERY = QUESTION_ROOT / "function-schema-delivery.json"
HOST_MANIFEST = QUESTION_ROOT / "host-publication-manifest.json"
SKILL_DOCUMENT = QUESTION_ROOT / "SKILL.md"
EXPORTER = (
    "com.foggyframework.analytics.function.fap."
    "FapAnalyticsQuestionPublicationExporter"
)
EXPORT_CONTRACT = "foggy.analytics.question-function-publication.v1"


class DeliveryError(RuntimeError):
    """Raised when source delivery files drift from the Java catalog."""


def _read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise DeliveryError(f"cannot read JSON object {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise DeliveryError(f"expected a JSON object: {path}")
    return value


def _write_json(path: Path, value: dict[str, Any]) -> bool:
    content = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if path.exists() and path.read_text(encoding="utf-8") == content:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        newline="\n",
        dir=path.parent,
        prefix=f".{path.name}.",
        delete=False,
    ) as stream:
        temporary = Path(stream.name)
        stream.write(content)
    temporary.replace(path)
    return True


def _compile_catalog(maven_executable: str) -> None:
    subprocess.run(
        [
            maven_executable,
            "-q",
            "-pl",
            "foggy-analytics-function-fap-adapter",
            "-am",
            "compile",
            "-DskipTests=true",
        ],
        cwd=REPOSITORY_ROOT,
        check=True,
    )


def _export_from_java(maven_executable: str, skip_compile: bool) -> dict[str, Any]:
    if not skip_compile:
        _compile_catalog(maven_executable)
    result = subprocess.run(
        [
            maven_executable,
            "-q",
            "-pl",
            "foggy-analytics-function-fap-adapter",
            "org.codehaus.mojo:exec-maven-plugin:3.5.0:java",
            f"-Dexec.mainClass={EXPORTER}",
        ],
        cwd=REPOSITORY_ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    try:
        envelope = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise DeliveryError("Java publication exporter returned invalid JSON") from exc
    if not isinstance(envelope, dict) or envelope.get("contractVersion") != EXPORT_CONTRACT:
        raise DeliveryError("Java publication exporter returned an unsupported contract")
    functions = envelope.get("functions")
    if not isinstance(functions, list):
        raise DeliveryError("Java publication exporter returned no functions array")
    return envelope


def _catalog(args: argparse.Namespace) -> dict[str, Any]:
    if args.catalog_json:
        value = json.load(sys.stdin) if args.catalog_json == "-" else json.loads(
            Path(args.catalog_json).read_text(encoding="utf-8")
        )
        if isinstance(value, list):
            return {"contractVersion": EXPORT_CONTRACT, "functions": value}
        if not isinstance(value, dict):
            raise DeliveryError("catalog JSON must be an object or array")
        return value
    return _export_from_java(args.maven_executable, args.skip_compile)


def _derived_delivery(
    catalog: dict[str, Any], metadata: dict[str, Any]
) -> tuple[dict[str, Any], dict[str, Any]]:
    functions = catalog.get("functions")
    gate = validate_catalog(functions)
    refs = gate["functionRefs"]
    schema_delivery = {
        "contractVersion": "fap.langbiz.function-schema-delivery.v1",
        "functions": [
            {"functionRef": function_ref, "mode": "INLINE"}
            for function_ref in refs
        ],
    }
    host_manifest = {
        "contractVersion": "foggy.analytics.question-host-publication.v1",
        "publicationMode": "HOST_MANAGED_EXPLICIT",
        "launcherStartupMutationAllowed": False,
        "skill": {
            "name": metadata.get("name"),
            "revision": metadata.get("revision"),
        },
        "functions": [
            {
                "functionRef": function_ref,
                "schemaDigest": gate["digests"][function_ref]["schemaDigest"],
                "projectionDigest": gate["digests"][function_ref]["projectionDigest"],
            }
            for function_ref in refs
        ],
    }
    return schema_delivery, host_manifest


def _validate_metadata(metadata: dict[str, Any]) -> None:
    if metadata.get("contractVersion") != "fap.skill.metadata.v1":
        raise DeliveryError("skill metadata contractVersion mismatch")
    if metadata.get("name") != "analytics-question-answering":
        raise DeliveryError("skill metadata name mismatch")
    revision = metadata.get("revision")
    if not isinstance(revision, int) or isinstance(revision, bool) or revision < 1:
        raise DeliveryError("skill metadata revision must be a positive integer")


def _check(catalog: dict[str, Any]) -> dict[str, Any]:
    metadata = _read_json(SKILL_METADATA)
    _validate_metadata(metadata)
    expected_delivery, expected_manifest = _derived_delivery(catalog, metadata)
    actual_delivery = _read_json(SCHEMA_DELIVERY)
    actual_manifest = _read_json(HOST_MANIFEST)
    if actual_delivery != expected_delivery:
        raise DeliveryError(
            "function-schema-delivery.json is stale; run fap_question_delivery.py sync"
        )
    if actual_manifest != expected_manifest:
        raise DeliveryError(
            "host-publication-manifest.json is stale; run fap_question_delivery.py sync"
        )
    skill = SKILL_DOCUMENT.read_text(encoding="utf-8")
    missing = [
        item["functionRef"]
        for item in expected_delivery["functions"]
        if item["functionRef"] not in skill
    ]
    if missing:
        raise DeliveryError(f"question Skill is missing FunctionRefs: {missing}")
    return {
        "contractVersion": "foggy.analytics.question-delivery-check.v1",
        "status": "CURRENT",
        "mutationPerformed": False,
        "skill": expected_manifest["skill"],
        "functionRefs": [
            item["functionRef"] for item in expected_manifest["functions"]
        ],
        "digests": {
            item["functionRef"]: {
                "schemaDigest": item["schemaDigest"],
                "projectionDigest": item["projectionDigest"],
            }
            for item in expected_manifest["functions"]
        },
    }


def _sync(catalog: dict[str, Any], skill_revision: int | None) -> dict[str, Any]:
    metadata = _read_json(SKILL_METADATA)
    if skill_revision is not None:
        if skill_revision < 1:
            raise DeliveryError("--skill-revision must be a positive integer")
        metadata["revision"] = skill_revision
    _validate_metadata(metadata)
    delivery, manifest = _derived_delivery(catalog, metadata)
    changed = any((
        _write_json(SKILL_METADATA, metadata),
        _write_json(SCHEMA_DELIVERY, delivery),
        _write_json(HOST_MANIFEST, manifest),
    ))
    checked = _check(catalog)
    checked["status"] = "SYNCHRONIZED" if changed else "CURRENT"
    checked["mutationPerformed"] = changed
    return checked


def _bundle(catalog: dict[str, Any]) -> dict[str, Any]:
    _check(catalog)
    documents = [
        {
            "path": path.relative_to(QUESTION_ROOT).as_posix(),
            "content": path.read_text(encoding="utf-8"),
        }
        for path in sorted(
            [SKILL_DOCUMENT, *QUESTION_ROOT.joinpath("references").glob("*.md")]
        )
    ]
    return {
        "contractVersion": "foggy.analytics.question-host-sync-bundle.v1",
        "publicationMode": "HOST_MANAGED_EXPLICIT",
        "mutationPerformed": False,
        "providerCallback": {
            "contractVersion": "fap.service-provider.v1alpha1",
            "method": "POST",
            "path": "/analytics-console/internal/fap/functions:invoke",
        },
        "skillMetadata": _read_json(SKILL_METADATA),
        "skillDocuments": documents,
        "functionSchemaDelivery": _read_json(SCHEMA_DELIVERY),
        "hostPublicationManifest": _read_json(HOST_MANIFEST),
        "functions": catalog["functions"],
    }


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    for name in ("export", "check", "sync", "bundle"):
        command = subcommands.add_parser(name)
        command.add_argument(
            "--catalog-json",
            help="use an existing Java catalog export, or - for stdin",
        )
        command.add_argument("--maven-executable", default="mvn")
        command.add_argument(
            "--skip-compile",
            action="store_true",
            help="reuse already compiled adapter and contract classes",
        )
        if name in {"export", "bundle"}:
            command.add_argument("--output", default="-")
        if name == "sync":
            command.add_argument("--skill-revision", type=int)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        catalog = _catalog(args)
        if args.command in {"export", "bundle"}:
            value = catalog if args.command == "export" else _bundle(catalog)
            content = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
            if args.output == "-":
                sys.stdout.write(content)
            else:
                Path(args.output).write_text(content, encoding="utf-8", newline="\n")
        elif args.command == "check":
            print(json.dumps(_check(catalog), ensure_ascii=False, indent=2))
        elif args.command == "sync":
            print(
                json.dumps(
                    _sync(catalog, args.skill_revision),
                    ensure_ascii=False,
                    indent=2,
                )
            )
        return 0
    except (
        DeliveryError,
        PublicationValidationError,
        OSError,
        json.JSONDecodeError,
        subprocess.CalledProcessError,
    ) as exc:
        print(f"FAP question delivery failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
