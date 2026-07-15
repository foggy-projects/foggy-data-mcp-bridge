#!/usr/bin/env python3
"""Validate and collect the V934 required external integration matrix."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Any, Iterable
import uuid
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[3]
CONTRACT_PATH = Path(__file__).with_name("external-matrix-contract.json")
DEFERRED_HEADER = [
    "execution_key", "source_id", "report_fqcn", "runner", "lane",
    "variant_key", "db_kind", "infra_kind", "execution_step", "required",
    "owner", "optional_reason", "review_at", "disposition",
]
DISCOVERY_HEADER = [
    "module", "source_id", "source_fqcn", "report_fqcn",
    "discovered_test_nodes", "runtime_deferred_containers", "engine_ids",
    "source_sha256", "test_classes_sha256", "main_classes_sha256",
]
SOURCE_HEADER = [
    "source_id", "module", "reactor_member", "source_root", "source_path",
    "top_level_fqcn", "kind", "discovery_patterns", "disposition", "owner",
    "reason",
]
INVENTORY_BINDINGS = {"deferred_inventory", "discovery_inventory", "source_inventory"}
FRAMEWORK_BINDINGS = {
    "authority_runner_lib", "external_report_tool", "external_redis_runner",
    "external_redis_signal_probe", "external_mongo_runner",
    "external_mysql_runner",
}
EXPECTED_BINDINGS = INVENTORY_BINDINGS | FRAMEWORK_BINDINGS
OUTER_FIELDS = {
    "schema_version", "kind", "run_id", "lane", "runner", "git_head",
    "contract_sha256", "started_at", "status",
}
VARIANT_MARKER_FIELDS = OUTER_FIELDS | {
    "variant_key", "infra_kind", "outer_marker_sha256", "selector",
}
GIT_COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")
REPORT_MANIFEST_FIELDS = {
    "schema_version", "kind", "run_id", "runner", "lane", "variant_key",
    "infra_kind", "git_head", "contract_sha256", "outer_marker_sha256",
    "variant_marker_sha256", "markers", "reports", "totals",
}
REPORT_RECORD_FIELDS = {
    "execution_key", "source_id", "source_path", "source_sha256", "module",
    "report_fqcn", "expected_testcase_nodes", "origin", "evidence",
}
MERGED_MANIFEST_FIELDS = {
    "schema_version", "kind", "run_id", "runner", "lane", "git_head",
    "contract_sha256", "outer_marker_sha256", "complete", "variants", "totals",
}
CANDIDATE_MANIFEST_FIELDS = {
    "schema_version", "kind", "run_id", "runner", "lane", "git_head",
    "contract_sha256", "outer_marker_sha256", "report_manifest_sha256",
    "artifacts", "totals",
}
CONTRACT_FIELDS = {
    "bindings", "execution_order", "kind", "negative_probes", "optional_execution",
    "required_totals", "required", "runner", "schema_version", "source_amendments",
    "variants",
}
CANDIDATE_SUMMARY_FIELDS = {
    "run_id", "runner", "lane", "git_head", "variants", "reports",
    "testcase_nodes", "failures", "errors", "skipped", "source_before",
    "source_after", "outer_marker_sha256", "contract_sha256",
    "final_report_manifest_sha256", "run_status_sha256", "resource_sha256",
    "fixture_sha256", "cleanup_sha256", "negative_probes", "negative_sha256",
    "sensitive_negative_probes", "sensitive_negative_sha256",
    "sensitive_scan_sha256", "resource_residue", "status",
}
RUN_STATUS_FIELDS = {
    "run_id", "runner", "git_head", "started_at", "finished_at", "last_phase",
    "exit_code", "source_before_sha256", "source_after_sha256",
    "outer_marker_sha256", "successor_manifest_sha256",
    "final_report_manifest_sha256", "status",
}
REDIS_IMAGE_REF = "redis@sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98"
REDIS_IMAGE_ID = "sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98"
REDIS_CLEAN_MODULES = (
    "foggy-bean-copy", "foggy-core", "foggy-fsscript", "foggy-dataset",
    "foggy-dataset-demo", "foggy-dataset-model", "addons/foggy-dataset-model-cache",
)
MONGO_IMAGE_REF = "mongo@sha256:03cda579c8caad6573cb98c2b3d5ff5ead452a6450561129b89595b4b9c18de2"
MONGO_IMAGE_ID = "sha256:03cda579c8caad6573cb98c2b3d5ff5ead452a6450561129b89595b4b9c18de2"
MONGO_GIT_VERSION = "fc88ca137231d7457aed6265d4f32a361ae71716"
MONGO_CLEAN_MODULES = (
    "foggy-core", "foggy-bean-copy", "foggy-mcp-spi", "foggy-fsscript",
    "foggy-dataset", "foggy-dataset-demo", "foggy-dataset-model",
    "addons/foggy-data-viewer", "addons/foggy-dataset-model-mongo",
)
MYSQL57_IMAGE_REF = "mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
MYSQL57_IMAGE_ID = "sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
MYSQL_CLEAN_MODULES = (
    "foggy-core", "foggy-bean-copy", "foggy-mcp-spi", "foggy-fsscript",
    "foggy-dataset", "foggy-dataset-demo", "foggy-dataset-model",
    "foggy-dataset-mcp",
)
MYSQL57_TABLE_COUNT = 69
MYSQL57_TABLE_SET_SHA256 = (
    "6c3356917e89c46c5e37851226a40b3d28e07f1db02bb2fe5fbecec8183591b7"
)
MYSQL57_CONTENT_SHA256 = (
    "c8edcd273ed2b0f9383330c7546521515ce729b078d5614995cd05752123ec8f"
)
MYSQL57_REQUIRED_TABLES = frozenset({
    "dim_channel", "dim_customer", "dim_date", "dim_product",
    "dim_promotion", "dim_sales_team", "dim_store", "fact_order",
    "fact_return", "fact_sales",
})
MYSQL57_FIXTURE_METRICS = {
    "table_count": 69,
    "primary_key_table_count": 69,
    "dim_date_count": 1461,
    "dim_product_count": 500,
    "dim_customer_count": 1000,
    "dim_store_count": 50,
    "fact_sales_count": 3088,
    "fact_order_count": 20005,
    "fact_return_count": 316,
    "compose_join_count": 10,
    "foreign_database_count": 0,
}
MYSQL57_DIRECT_CASE_COUNT = 23
MYSQL57_ECOMMERCE_SOURCE = (
    ROOT / "foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce"
)
MYSQL57_CURATED_BUNDLE_COUNTS = {
    "files": 59,
    "qm_files": 32,
    "tm_files": 25,
    "fsscript_files": 2,
}


class ContractError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message


def reject(code: str, message: str) -> None:
    raise ContractError(code, message)


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def atomic_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, path)


def atomic_json(path: Path, payload: dict[str, Any]) -> None:
    atomic_text(path, json.dumps(payload, indent=2, sort_keys=True) + "\n")


def ensure_regular(path: Path, code: str, description: str) -> Path:
    reject_symlink_components(path, code)
    if not path.is_file():
        reject(code, f"{description} is missing or not a regular file: {path}")
    return path


def ensure_directory(path: Path, code: str, description: str) -> Path:
    reject_symlink_components(path, code)
    if not path.is_dir():
        reject(code, f"{description} is missing or not a regular directory: {path}")
    return path


def reject_symlink_components(path: Path, code: str) -> None:
    absolute = path.absolute()
    current = Path(absolute.anchor)
    for part in absolute.parts[1:]:
        current /= part
        if current.is_symlink():
            reject(code, f"path contains a symlink component: {path}")


def repo_path(value: str, code: str = "E_CONTRACT") -> Path:
    lexical = ROOT / value
    if lexical.is_symlink():
        reject(code, f"bound path must not be a symlink: {value}")
    try:
        resolved = lexical.resolve(strict=True)
        resolved.relative_to(ROOT)
    except (OSError, ValueError) as error:
        raise ContractError(code, f"bound path escapes or is missing: {value}") from error
    return resolved


def read_tsv(path: Path, header: list[str], code: str) -> list[dict[str, str]]:
    ensure_regular(path, code, "TSV input")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != header:
            reject(code, f"TSV header differs for {path}: {reader.fieldnames}")
        rows = list(reader)
    if any(None in row or any(value is None for value in row.values()) for row in rows):
        reject(code, f"malformed TSV row in {path}")
    return rows


def parse_positive_int(value: str, code: str, description: str) -> int:
    try:
        parsed = int(value)
    except ValueError as error:
        raise ContractError(code, f"{description} is not an integer: {value!r}") from error
    if parsed <= 0:
        reject(code, f"{description} must be positive: {parsed}")
    return parsed


def current_git_head() -> str:
    result = subprocess.run(
        ["git", "-C", str(ROOT), "rev-parse", "HEAD"],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout.strip()


def load_contract() -> dict[str, Any]:
    ensure_regular(CONTRACT_PATH, "E_CONTRACT", "external matrix contract")
    try:
        contract = json.loads(CONTRACT_PATH.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as error:
        raise ContractError("E_CONTRACT", "external matrix contract is not valid JSON") from error
    if not isinstance(contract, dict):
        reject("E_CONTRACT", "contract root must be an object")
    if (
        set(contract) != CONTRACT_FIELDS
        or
        contract.get("schema_version") != 1
        or contract.get("kind") != "v934-step3-external-matrix-contract"
        or contract.get("runner") != "failsafe"
        or contract.get("required") is not True
    ):
        reject("E_CONTRACT", "contract identity differs")
    bindings = contract.get("bindings")
    if not isinstance(bindings, dict) or set(bindings) != EXPECTED_BINDINGS:
        reject("E_CONTRACT", "contract bindings differ")
    resolved: dict[str, Path] = {}
    for key, binding in bindings.items():
        if (
            not isinstance(binding, dict)
            or set(binding) != {"path", "sha256"}
            or not isinstance(binding["path"], str)
            or not binding["path"]
            or not isinstance(binding["sha256"], str)
            or re.fullmatch(r"[0-9a-f]{64}", binding["sha256"]) is None
        ):
            reject("E_CONTRACT", f"malformed binding: {key}")
        path = repo_path(binding["path"])
        if sha256_file(path) != binding["sha256"]:
            reject("E_BINDING", f"bound input SHA-256 differs: {binding['path']}")
        resolved[key] = path

    deferred = read_tsv(resolved["deferred_inventory"], DEFERRED_HEADER, "E_INVENTORY")
    discovery = read_tsv(resolved["discovery_inventory"], DISCOVERY_HEADER, "E_INVENTORY")
    sources = read_tsv(resolved["source_inventory"], SOURCE_HEADER, "E_INVENTORY")
    required = [
        row for row in deferred
        if row["execution_step"] == "3"
        and row["required"] == "true"
        and row["lane"] != "database-contract-matrix"
    ]
    optional = [
        row for row in deferred
        if row["execution_step"] == "3" and row["required"] == "false"
    ]
    if len(required) != 16 or len(optional) != 1:
        reject("E_INVENTORY", "external deferred inventory must be exact 16 required + 1 optional")
    if len({row["execution_key"] for row in required + optional}) != 17:
        reject("E_INVENTORY", "external execution keys are not unique")

    discovery_by_key: dict[tuple[str, str], dict[str, str]] = {}
    for row in discovery:
        key = (row["source_id"], row["report_fqcn"])
        if key in discovery_by_key:
            reject("E_INVENTORY", f"duplicate discovery key: {key}")
        discovery_by_key[key] = row
    source_by_id: dict[str, dict[str, str]] = {}
    for row in sources:
        if row["source_id"] in source_by_id:
            reject("E_INVENTORY", f"duplicate source id: {row['source_id']}")
        source_by_id[row["source_id"]] = row

    amendments = contract.get("source_amendments")
    if not isinstance(amendments, list):
        reject("E_CONTRACT", "source amendments must be a list")
    amendment_by_source: dict[str, dict[str, Any]] = {}
    amendment_fields = {
        "source_id", "source_path", "original_sha256", "amended_sha256",
        "report_fqcns", "reason",
    }
    all_step3 = [row for row in deferred if row["execution_step"] == "3"]
    discovery_by_source: dict[str, list[dict[str, str]]] = {}
    for row in discovery:
        discovery_by_source.setdefault(row["source_id"], []).append(row)
    for amendment in amendments:
        if not isinstance(amendment, dict) or set(amendment) != amendment_fields:
            reject("E_CONTRACT", "malformed source amendment")
        source_id = amendment["source_id"]
        source_row = source_by_id.get(source_id)
        discovery_rows = discovery_by_source.get(source_id, [])
        expected_reports = sorted(
            row["report_fqcn"] for row in all_step3 if row["source_id"] == source_id
        )
        report_fqcns = amendment.get("report_fqcns")
        if (
            not isinstance(source_id, str)
            or not source_id
            or source_id in amendment_by_source
            or source_row is None
            or not discovery_rows
            or amendment["source_path"] != source_row["source_path"]
            or not isinstance(report_fqcns, list)
            or not all(isinstance(value, str) and value for value in report_fqcns)
            or sorted(report_fqcns) != expected_reports
            or not expected_reports
            or not isinstance(amendment["reason"], str)
            or not amendment["reason"].strip()
            or not isinstance(amendment["original_sha256"], str)
            or not isinstance(amendment["amended_sha256"], str)
            or re.fullmatch(r"[0-9a-f]{64}", amendment["original_sha256"]) is None
            or re.fullmatch(r"[0-9a-f]{64}", amendment["amended_sha256"]) is None
            or amendment["original_sha256"] == amendment["amended_sha256"]
            or {row["source_sha256"] for row in discovery_rows}
            != {amendment["original_sha256"]}
        ):
            reject("E_CONTRACT", f"source amendment differs: {source_id!r}")
        amended_path = repo_path(amendment["source_path"], "E_SOURCE")
        if sha256_file(amended_path) != amendment["amended_sha256"]:
            reject("E_SOURCE", f"amended source SHA-256 differs: {amendment['source_path']}")
        amendment_by_source[source_id] = amendment

    variants = contract.get("variants")
    order = contract.get("execution_order")
    if not isinstance(variants, list) or len(variants) != 7:
        reject("E_CONTRACT", "contract variants must contain exactly seven rows")
    if not isinstance(order, list) or len(order) != 7 or len(set(order)) != 7:
        reject("E_CONTRACT", "execution order must contain seven unique variants")
    variant_by_key: dict[str, dict[str, Any]] = {}
    required_nodes = 0
    for row in required:
        discovery_row = discovery_by_key.get((row["source_id"], row["report_fqcn"]))
        source_row = source_by_id.get(row["source_id"])
        if discovery_row is None or source_row is None:
            reject("E_INVENTORY", f"external execution has no discovery/source row: {row['execution_key']}")
        if discovery_row["module"] != row["owner"] or source_row["module"] != row["owner"]:
            reject("E_INVENTORY", f"external owner differs: {row['execution_key']}")
        required_nodes += parse_positive_int(
            discovery_row["discovered_test_nodes"], "E_INVENTORY", "discovered test nodes"
        )
        row["expected_testcase_nodes"] = discovery_row["discovered_test_nodes"]
        row["source_path"] = source_row["source_path"]
        amendment = amendment_by_source.get(row["source_id"])
        row["source_sha256"] = (
            amendment["amended_sha256"] if amendment else discovery_row["source_sha256"]
        )
        source_path = repo_path(row["source_path"], "E_SOURCE")
        if sha256_file(source_path) != row["source_sha256"]:
            reject("E_SOURCE", f"current test source differs: {row['source_path']}")
    if required_nodes != 76:
        reject("E_INVENTORY", f"external required testcase total is {required_nodes}, expected 76")

    allowed_variant_fields = {
        "variant_key", "lane", "infra_kind", "modules",
        "expected_reports", "expected_testcase_nodes", "selector",
    }
    for variant in variants:
        if not isinstance(variant, dict) or set(variant) != allowed_variant_fields:
            reject("E_CONTRACT", "malformed external variant row")
        key = variant["variant_key"]
        if not isinstance(key, str) or not key or key in variant_by_key:
            reject("E_CONTRACT", f"duplicate/blank variant key: {key!r}")
        rows = [row for row in required if row["variant_key"] == key]
        modules = sorted({row["owner"] for row in rows})
        nodes = sum(int(row["expected_testcase_nodes"]) for row in rows)
        if (
            len(rows) != variant["expected_reports"]
            or nodes != variant["expected_testcase_nodes"]
            or modules != sorted(variant["modules"])
            or len({row["lane"] for row in rows}) != 1
            or rows[0]["lane"] != variant["lane"]
            or len({row["infra_kind"] for row in rows}) != 1
            or rows[0]["infra_kind"] != variant["infra_kind"]
            or not isinstance(variant["selector"], str)
            or not variant["selector"].strip()
        ):
            reject("E_CONTRACT", f"variant aggregate differs: {key}")
        variant_by_key[key] = variant
    if order != [variant["variant_key"] for variant in variants]:
        reject("E_CONTRACT", "execution order must match the ordered variants")
    if {row["variant_key"] for row in required} != set(variant_by_key):
        reject("E_CONTRACT", "required inventory and contract variant sets differ")

    totals = contract.get("required_totals")
    expected_totals = {
        "variants": 7, "reports": 16, "testcase_nodes": 76,
        "failures": 0, "errors": 0, "skipped": 0,
    }
    if totals != expected_totals:
        reject("E_CONTRACT", f"required totals differ: {totals}")
    optional_contract = contract.get("optional_execution")
    optional_row = optional[0]
    if not isinstance(optional_contract, dict) or any(
        optional_contract.get(key) != optional_row[key]
        for key in (
            "execution_key", "lane", "variant_key", "owner",
            "optional_reason", "review_at",
        )
    ):
        reject("E_CONTRACT", "optional LLM disposition binding differs")
    probes = contract.get("negative_probes")
    if (
        not isinstance(probes, list)
        or len(probes) != 12
        or len({probe.get("probe") for probe in probes if isinstance(probe, dict)}) != 12
        or any(
            not isinstance(probe, dict)
            or set(probe) != {"probe", "expected_error"}
            or not probe["probe"]
            or not probe["expected_error"]
            for probe in probes
        )
    ):
        reject("E_CONTRACT", "negative probe contract must be exact twelve unique rows")

    contract["_required"] = required
    contract["_variant_by_key"] = variant_by_key
    contract["_contract_sha256"] = sha256_file(CONTRACT_PATH)
    return contract


def expected_rows(contract: dict[str, Any], variant: str) -> list[dict[str, str]]:
    if variant not in contract["_variant_by_key"]:
        reject("E_VARIANT", f"unknown external variant: {variant}")
    return [row for row in contract["_required"] if row["variant_key"] == variant]


def parse_timestamp(value: Any, code: str) -> dt.datetime:
    if not isinstance(value, str) or not value:
        reject(code, "marker timestamp is blank")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise ContractError(code, f"marker timestamp is invalid: {value}") from error
    if parsed.tzinfo is None:
        reject(code, "marker timestamp must include a timezone")
    return parsed


def load_outer_marker(
    path: Path,
    contract: dict[str, Any],
    require_current_head: bool = False,
) -> dict[str, Any]:
    ensure_regular(path, "E_MARKER", "outer marker")
    try:
        marker = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError("E_MARKER", "outer marker is not valid JSON") from error
    if not isinstance(marker, dict) or set(marker) != OUTER_FIELDS:
        reject("E_MARKER", "outer marker fields differ")
    if (
        marker["schema_version"] != 1
        or marker["kind"] != "v934-step3-external-matrix-outer-run"
        or marker["lane"] != "external-matrix"
        or marker["runner"] != "failsafe"
        or marker["status"] != "started"
        or marker["contract_sha256"] != contract["_contract_sha256"]
        or not isinstance(marker["git_head"], str)
        or GIT_COMMIT_PATTERN.fullmatch(marker["git_head"]) is None
        or not isinstance(marker["run_id"], str)
        or not marker["run_id"]
    ):
        reject("E_MARKER", "outer marker identity differs")
    if require_current_head and marker["git_head"] != current_git_head():
        reject("E_MARKER", "outer marker does not match the current Git HEAD")
    parse_timestamp(marker["started_at"], "E_MARKER")
    marker["_path"] = path
    marker["_sha256"] = sha256_file(path)
    marker["_mtime_ns"] = path.stat().st_mtime_ns
    return marker


def load_variant_marker(
    path: Path,
    contract: dict[str, Any],
    outer: dict[str, Any],
    variant: str,
) -> dict[str, Any]:
    ensure_regular(path, "E_MARKER", "variant marker")
    try:
        marker = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError("E_MARKER", "variant marker is not valid JSON") from error
    definition = contract["_variant_by_key"].get(variant)
    if not isinstance(marker, dict) or set(marker) != VARIANT_MARKER_FIELDS:
        reject("E_MARKER", "variant marker fields differ")
    if (
        definition is None
        or marker["schema_version"] != 1
        or marker["kind"] != "v934-step3-external-matrix-variant-run"
        or marker["run_id"] != outer["run_id"]
        or marker["lane"] != "external-matrix"
        or marker["runner"] != "failsafe"
        or marker["git_head"] != outer["git_head"]
        or marker["contract_sha256"] != outer["contract_sha256"]
        or marker["status"] != "started"
        or marker["variant_key"] != variant
        or marker["infra_kind"] != definition["infra_kind"]
        or marker["outer_marker_sha256"] != outer["_sha256"]
        or marker["selector"] != definition["selector"]
    ):
        reject("E_MARKER", "variant marker identity differs")
    parse_timestamp(marker["started_at"], "E_MARKER")
    if path.stat().st_mtime_ns < outer["_mtime_ns"]:
        reject("E_MARKER", "variant marker predates outer marker")
    marker["_path"] = path
    marker["_sha256"] = sha256_file(path)
    marker["_mtime_ns"] = path.stat().st_mtime_ns
    return marker


def testcase_elements(root: ET.Element) -> list[ET.Element]:
    return [element for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "testcase"]


def report_counts(root: ET.Element) -> dict[str, int]:
    values: dict[str, int] = {}
    for key in ("tests", "failures", "errors", "skipped"):
        if key not in root.attrib:
            reject("E_REPORT_COUNT", f"report {key} attribute is missing")
        try:
            values[key] = int(root.attrib.get(key, "0"))
        except ValueError as error:
            raise ContractError("E_REPORT_COUNT", f"report {key} is not an integer") from error
        if values[key] < 0:
            reject("E_REPORT_COUNT", f"report {key} is negative")
    return values


def report_properties(root: ET.Element) -> dict[str, str]:
    containers = [
        child for child in root
        if child.tag.rsplit("}", 1)[-1] == "properties"
    ]
    if len(containers) != 1:
        reject("E_REPORT_CONTEXT", "report must contain exactly one properties element")
    values: dict[str, str] = {}
    for element in containers[0]:
        if element.tag.rsplit("}", 1)[-1] != "property":
            continue
        name = element.attrib.get("name")
        value = element.attrib.get("value")
        if not name or value is None or name in values:
            reject("E_REPORT_CONTEXT", "report contains malformed/duplicate properties")
        values[name] = value
    return values


def parse_report(
    path: Path,
    marker_mtime_ns: int,
    expected_properties: dict[str, str],
) -> dict[str, Any]:
    ensure_regular(path, "E_REPORT", "Failsafe report")
    content = path.read_bytes()
    try:
        decoded = content.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ContractError("E_REPORT", f"report XML is not UTF-8: {path}") from error
    if not decoded.startswith('<?xml version="1.0" encoding="UTF-8"?>\n'):
        reject("E_REPORT", f"report XML declaration differs: {path}")
    upper_content = decoded.upper()
    if "<!DOCTYPE" in upper_content or "<!ENTITY" in upper_content:
        reject("E_REPORT", f"report XML contains a prohibited declaration: {path}")
    try:
        root = ET.fromstring(content)
    except ET.ParseError as error:
        raise ContractError("E_REPORT", f"report XML is malformed: {path}") from error
    if root.tag.rsplit("}", 1)[-1] != "testsuite":
        reject("E_REPORT", f"report root is not testsuite: {path}")
    name = root.attrib.get("name", "")
    if not name:
        reject("E_REPORT", f"report suite name is blank: {path}")
    if path.name != f"TEST-{name}.xml":
        reject("E_REPORT", f"report filename and suite name differ: {path}")
    properties = report_properties(root)
    for key, value in expected_properties.items():
        if properties.get(key) != value:
            reject("E_REPORT_CONTEXT", f"report property differs: {key}")
    counts = report_counts(root)
    nodes = testcase_elements(root)
    if counts["tests"] != len(nodes):
        reject("E_REPORT_COUNT", f"tests attribute and testcase nodes differ: {path}")
    child_outcomes = {
        element.tag.rsplit("}", 1)[-1]
        for node in nodes
        for element in node
        if element.tag.rsplit("}", 1)[-1] in {
            "failure", "error", "skipped", "flakyFailure", "flakyError",
            "rerunFailure", "rerunError",
        }
    }
    if counts["failures"] or counts["errors"] or counts["skipped"] or child_outcomes:
        reject("E_REPORT_OUTCOME", f"report is not F0/E0/S0: {path}")
    if path.stat().st_mtime_ns <= marker_mtime_ns:
        reject("E_STALE_REPORT", f"report is not newer than its variant marker: {path}")
    return {"name": name, "counts": counts, "testcase_nodes": len(nodes)}


def artifact(path: Path, relative: str) -> dict[str, Any]:
    ensure_regular(path, "E_EVIDENCE", "evidence artifact")
    return {
        "path": relative,
        "sha256": sha256_file(path),
        "size_bytes": path.stat().st_size,
        "mtime_ns": path.stat().st_mtime_ns,
    }


def copy_regular(source: Path, target: Path) -> None:
    ensure_regular(source, "E_EVIDENCE", "copy source")
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() or target.is_symlink():
        reject("E_EVIDENCE", f"copy target already exists: {target}")
    shutil.copy2(source, target)
    if source.read_bytes() != target.read_bytes():
        reject("E_EVIDENCE", f"copied evidence differs: {source}")


def parse_report_roots(values: list[str], expected_modules: list[str]) -> dict[str, Path]:
    roots: dict[str, Path] = {}
    for value in values:
        if "=" not in value:
            reject("E_REPORT_ROOT", f"report root must be module=path: {value}")
        module, raw_path = value.split("=", 1)
        if module in roots or module not in expected_modules or not raw_path:
            reject("E_REPORT_ROOT", f"unexpected/duplicate report root: {module}")
        path = Path(raw_path)
        if not path.is_absolute():
            path = ROOT / path
        ensure_directory(path, "E_REPORT_ROOT", "report root")
        try:
            path.resolve(strict=True).relative_to(ROOT)
        except ValueError as error:
            raise ContractError("E_REPORT_ROOT", f"report root escapes repository: {path}") from error
        roots[module] = path
    if set(roots) != set(expected_modules):
        reject("E_REPORT_ROOT", f"report root module set differs: {sorted(roots)}")
    return roots


def collect_variant(
    contract: dict[str, Any],
    variant: str,
    outer_path: Path,
    marker_path: Path,
    report_root_values: list[str],
    output: Path,
) -> Path:
    definition = contract["_variant_by_key"].get(variant)
    if definition is None:
        reject("E_VARIANT", f"unknown variant: {variant}")
    if output.exists() or output.is_symlink():
        reject("E_OUTPUT", f"output already exists: {output}")
    outer = load_outer_marker(outer_path, contract, require_current_head=True)
    marker = load_variant_marker(marker_path, contract, outer, variant)
    expected_properties = {
        "v934.external.run-id": outer["run_id"],
        "v934.external.variant": variant,
        "it.test": marker["selector"],
        "skipITs": "false",
        "failsafe.rerunFailingTestsCount": "0",
    }
    roots = parse_report_roots(report_root_values, definition["modules"])
    expected = expected_rows(contract, variant)
    expected_by_name = {row["report_fqcn"]: row for row in expected}
    discovered: list[tuple[str, Path, dict[str, Any]]] = []
    for module, root in roots.items():
        for path in sorted(root.rglob("TEST-*.xml")):
            if path.is_symlink():
                reject("E_REPORT", f"report is a symlink: {path}")
            parsed = parse_report(path, marker["_mtime_ns"], expected_properties)
            discovered.append((module, path, parsed))
    names = [parsed["name"] for _, _, parsed in discovered]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        reject("E_DUPLICATE_REPORT", f"duplicate report identities: {duplicates}")
    missing = sorted(set(expected_by_name) - set(names))
    extra = sorted(set(names) - set(expected_by_name))
    if missing:
        reject("E_MISSING_REPORT", f"missing expected reports: {missing}")
    if extra:
        reject("E_EXTRA_REPORT", f"unexpected reports: {extra}")
    if len(discovered) != len(expected):
        reject("E_REPORT_COUNT", "report count differs")

    output.mkdir(parents=True)
    marker_output = output / "markers/variant-run-marker.json"
    outer_output = output / "markers/outer-run-marker.json"
    copy_regular(marker_path, marker_output)
    copy_regular(outer_path, outer_output)
    report_records: list[dict[str, Any]] = []
    total_nodes = 0
    for module, source_report, parsed in sorted(discovered, key=lambda item: item[2]["name"]):
        row = expected_by_name[parsed["name"]]
        if module != row["owner"]:
            reject("E_REPORT_OWNER", f"report owner differs for {parsed['name']}: {module}")
        expected_nodes = int(row["expected_testcase_nodes"])
        if parsed["testcase_nodes"] != expected_nodes:
            reject(
                "E_REPORT_COUNT",
                f"testcase count differs for {parsed['name']}: "
                f"{parsed['testcase_nodes']} != {expected_nodes}",
            )
        source_path = repo_path(row["source_path"], "E_SOURCE")
        if sha256_file(source_path) != row["source_sha256"]:
            reject("E_SOURCE", f"current test source differs from frozen discovery: {row['source_path']}")
        target = output / "raw" / module / source_report.name
        copy_regular(source_report, target)
        relative = target.relative_to(output).as_posix()
        report_records.append({
            "execution_key": row["execution_key"],
            "source_id": row["source_id"],
            "source_path": row["source_path"],
            "source_sha256": row["source_sha256"],
            "module": module,
            "report_fqcn": parsed["name"],
            "expected_testcase_nodes": expected_nodes,
            "origin": artifact(source_report, source_report.relative_to(ROOT).as_posix()),
            "evidence": artifact(target, relative),
        })
        total_nodes += expected_nodes
    manifest = {
        "schema_version": 1,
        "kind": "v934-step3-external-matrix-variant-evidence",
        "run_id": outer["run_id"],
        "runner": "failsafe",
        "lane": definition["lane"],
        "variant_key": variant,
        "infra_kind": definition["infra_kind"],
        "git_head": outer["git_head"],
        "contract_sha256": contract["_contract_sha256"],
        "outer_marker_sha256": outer["_sha256"],
        "variant_marker_sha256": marker["_sha256"],
        "markers": {
            "outer": artifact(outer_output, "markers/outer-run-marker.json"),
            "variant": artifact(marker_output, "markers/variant-run-marker.json"),
        },
        "reports": report_records,
        "totals": {
            "variants": 1,
            "reports": len(report_records),
            "testcase_nodes": total_nodes,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        },
    }
    manifest_path = output / "report-manifest.json"
    atomic_json(manifest_path, manifest)
    verify_variant_manifest(contract, outer, manifest_path)
    return manifest_path


def validate_artifact(root: Path, record: Any, code: str = "E_EVIDENCE") -> Path:
    if not isinstance(record, dict) or set(record) != {"path", "sha256", "size_bytes", "mtime_ns"}:
        reject(code, "artifact record fields differ")
    raw_path = record["path"]
    if not isinstance(raw_path, str) or not raw_path or raw_path.startswith("/"):
        reject(code, f"artifact path is unsafe: {raw_path!r}")
    path = root / raw_path
    try:
        resolved = path.resolve(strict=True)
        resolved.relative_to(root.resolve(strict=True))
    except (OSError, ValueError) as error:
        raise ContractError(code, f"artifact escapes or is missing: {raw_path}") from error
    ensure_regular(path, code, "artifact")
    if (
        sha256_file(path) != record["sha256"]
        or path.stat().st_size != record["size_bytes"]
        or path.stat().st_mtime_ns != record["mtime_ns"]
    ):
        reject(code, f"artifact identity differs: {raw_path}")
    return path


def load_json_manifest(path: Path) -> dict[str, Any]:
    ensure_regular(path, "E_MANIFEST", "report manifest")
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError("E_MANIFEST", "report manifest is not valid JSON") from error
    if not isinstance(manifest, dict):
        reject("E_MANIFEST", "report manifest root must be an object")
    return manifest


def verify_variant_manifest(
    contract: dict[str, Any], outer: dict[str, Any], manifest_path: Path
) -> dict[str, Any]:
    manifest = load_json_manifest(manifest_path)
    if set(manifest) != REPORT_MANIFEST_FIELDS:
        reject("E_MANIFEST", "variant manifest fields differ")
    if manifest.get("kind") != "v934-step3-external-matrix-variant-evidence":
        reject("E_MANIFEST", "manifest is not variant evidence")
    variant = manifest.get("variant_key")
    definition = contract["_variant_by_key"].get(variant)
    if definition is None:
        reject("E_VARIANT", f"manifest variant is unknown: {variant}")
    if (
        manifest.get("schema_version") != 1
        or manifest.get("run_id") != outer["run_id"]
        or manifest.get("runner") != "failsafe"
        or manifest.get("lane") != definition["lane"]
        or manifest.get("infra_kind") != definition["infra_kind"]
        or manifest.get("git_head") != outer["git_head"]
        or manifest.get("contract_sha256") != contract["_contract_sha256"]
        or manifest.get("outer_marker_sha256") != outer["_sha256"]
    ):
        reject("E_CROSS_RUN_SPLICE", "variant manifest context differs")
    manifest_root = manifest_path.parent
    markers = manifest.get("markers")
    if not isinstance(markers, dict) or set(markers) != {"outer", "variant"}:
        reject("E_MANIFEST", "variant marker records differ")
    bundled_outer = validate_artifact(manifest_root, markers["outer"])
    bundled_variant = validate_artifact(manifest_root, markers["variant"])
    if bundled_outer.read_bytes() != outer["_path"].read_bytes():
        reject("E_CROSS_RUN_SPLICE", "bundled outer marker differs")
    variant_marker = load_variant_marker(bundled_variant, contract, outer, variant)
    if manifest.get("variant_marker_sha256") != variant_marker["_sha256"]:
        reject("E_CROSS_RUN_SPLICE", "variant marker SHA differs")

    expected = expected_rows(contract, variant)
    expected_properties = {
        "v934.external.run-id": outer["run_id"],
        "v934.external.variant": variant,
        "it.test": variant_marker["selector"],
        "skipITs": "false",
        "failsafe.rerunFailingTestsCount": "0",
    }
    expected_by_name = {row["report_fqcn"]: row for row in expected}
    reports = manifest.get("reports")
    if not isinstance(reports, list):
        reject("E_MANIFEST", "variant reports must be a list")
    names = [report.get("report_fqcn") for report in reports if isinstance(report, dict)]
    if len(names) != len(set(names)):
        reject("E_DUPLICATE_REPORT", "variant manifest has duplicate reports")
    if set(names) != set(expected_by_name):
        reject("E_MISSING_REPORT" if set(expected_by_name) - set(names) else "E_EXTRA_REPORT",
               "variant manifest report set differs")
    nodes = 0
    for report in reports:
        if not isinstance(report, dict) or set(report) != REPORT_RECORD_FIELDS:
            reject("E_MANIFEST", "malformed report record")
        row = expected_by_name[report["report_fqcn"]]
        for key in ("execution_key", "source_id", "source_path", "source_sha256"):
            if report.get(key) != row[key]:
                reject("E_MANIFEST", f"report {key} differs: {report['report_fqcn']}")
        expected_nodes = int(row["expected_testcase_nodes"])
        if (
            report.get("module") != row["owner"]
            or report.get("expected_testcase_nodes") != expected_nodes
        ):
            reject("E_REPORT_COUNT", f"report owner/count differs: {report['report_fqcn']}")
        evidence_path = validate_artifact(manifest_root, report.get("evidence"))
        parsed = parse_report(
            evidence_path,
            variant_marker["_mtime_ns"],
            expected_properties,
        )
        if parsed["name"] != report["report_fqcn"] or parsed["testcase_nodes"] != expected_nodes:
            reject("E_REPORT_COUNT", f"evidence report differs: {report['report_fqcn']}")
        origin = report.get("origin")
        if not isinstance(origin, dict) or set(origin) != {"path", "sha256", "size_bytes", "mtime_ns"}:
            reject("E_MANIFEST", "origin report record fields differ")
        if origin["sha256"] != report["evidence"]["sha256"] or origin["size_bytes"] != report["evidence"]["size_bytes"]:
            reject("E_EVIDENCE", f"origin/evidence content differs: {report['report_fqcn']}")
        nodes += expected_nodes
    expected_totals = {
        "variants": 1,
        "reports": definition["expected_reports"],
        "testcase_nodes": definition["expected_testcase_nodes"],
        "failures": 0, "errors": 0, "skipped": 0,
    }
    if manifest.get("totals") != expected_totals or nodes != expected_totals["testcase_nodes"]:
        reject("E_REPORT_COUNT", "variant manifest totals differ")
    expected_files = {
        "report-manifest.json",
        "markers/outer-run-marker.json",
        "markers/variant-run-marker.json",
        *[report["evidence"]["path"] for report in reports],
    }
    actual_files = {
        path.relative_to(manifest_root).as_posix()
        for path in manifest_root.rglob("*")
        if path.is_file() or path.is_symlink()
    }
    if actual_files != expected_files:
        reject("E_EVIDENCE", "variant evidence file set differs")
    return manifest


def tree_hash(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            reject("E_EVIDENCE", f"evidence tree contains a symlink: {path}")
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        digest.update(relative.encode())
        digest.update(b"\0")
        digest.update(sha256_file(path).encode())
        digest.update(b"\0")
        digest.update(str(path.stat().st_size).encode())
        digest.update(b"\n")
    return digest.hexdigest()


def copy_tree(source: Path, target: Path) -> None:
    ensure_directory(source, "E_EVIDENCE", "variant evidence tree")
    if target.exists() or target.is_symlink():
        reject("E_OUTPUT", f"target tree exists: {target}")
    for path in source.rglob("*"):
        if path.is_symlink():
            reject("E_EVIDENCE", f"source tree contains a symlink: {path}")
    shutil.copytree(source, target, copy_function=shutil.copy2)
    if tree_hash(source) != tree_hash(target):
        reject("E_EVIDENCE", "copied variant tree differs")


def merge_manifests(
    contract: dict[str, Any],
    outer_path: Path,
    manifest_paths: list[Path],
    output: Path,
    lane: str | None,
) -> Path:
    if output.exists() or output.is_symlink():
        reject("E_OUTPUT", f"merge output already exists: {output}")
    outer = load_outer_marker(outer_path, contract, require_current_head=True)
    manifests: dict[str, tuple[Path, dict[str, Any]]] = {}
    for path in manifest_paths:
        manifest = verify_variant_manifest(contract, outer, path)
        variant = manifest["variant_key"]
        if variant in manifests:
            reject("E_DUPLICATE_REPORT", f"duplicate variant manifest: {variant}")
        manifests[variant] = (path, manifest)
    expected_variants = [
        variant["variant_key"] for variant in contract["variants"]
        if lane is None or variant["lane"] == lane
    ]
    if not expected_variants:
        reject("E_VARIANT", f"lane has no contract variants: {lane}")
    missing = sorted(set(expected_variants) - set(manifests))
    extra = sorted(set(manifests) - set(expected_variants))
    if missing:
        reject("E_MISSING_REPORT", f"missing variant manifests: {missing}")
    if extra:
        reject("E_EXTRA_REPORT", f"unexpected variant manifests: {extra}")
    output.mkdir(parents=True)
    copy_regular(outer_path, output / "outer-run-marker.json")
    variants_root = output / "variants"
    variants_root.mkdir()
    records: list[dict[str, Any]] = []
    totals = {"variants": 0, "reports": 0, "testcase_nodes": 0, "failures": 0, "errors": 0, "skipped": 0}
    for variant in expected_variants:
        source_manifest, manifest = manifests[variant]
        target_root = variants_root / variant
        copy_tree(source_manifest.parent, target_root)
        target_manifest = target_root / "report-manifest.json"
        verify_variant_manifest(contract, outer, target_manifest)
        records.append({
            "variant_key": variant,
            "manifest_path": target_manifest.relative_to(output).as_posix(),
            "manifest_sha256": sha256_file(target_manifest),
            "tree_sha256": tree_hash(target_root),
        })
        for key in totals:
            totals[key] += manifest["totals"][key]
    merged = {
        "schema_version": 1,
        "kind": (
            "v934-step3-external-matrix-final"
            if lane is None else "v934-step3-external-matrix-subset"
        ),
        "run_id": outer["run_id"],
        "runner": "failsafe",
        "lane": "external-matrix" if lane is None else lane,
        "git_head": outer["git_head"],
        "contract_sha256": contract["_contract_sha256"],
        "outer_marker_sha256": outer["_sha256"],
        "complete": lane is None,
        "variants": records,
        "totals": totals,
    }
    path = output / "report-manifest.json"
    atomic_json(path, merged)
    verify_merged_manifest(contract, outer, path)
    return path


def verify_merged_manifest(
    contract: dict[str, Any], outer: dict[str, Any], manifest_path: Path
) -> dict[str, Any]:
    manifest = load_json_manifest(manifest_path)
    if set(manifest) != MERGED_MANIFEST_FIELDS:
        reject("E_MANIFEST", "merged manifest fields differ")
    kind = manifest.get("kind")
    if kind not in {
        "v934-step3-external-matrix-subset",
        "v934-step3-external-matrix-final",
    }:
        reject("E_MANIFEST", "manifest is not a merged external manifest")
    if (
        manifest.get("schema_version") != 1
        or manifest.get("run_id") != outer["run_id"]
        or manifest.get("runner") != "failsafe"
        or manifest.get("git_head") != outer["git_head"]
        or manifest.get("contract_sha256") != contract["_contract_sha256"]
        or manifest.get("outer_marker_sha256") != outer["_sha256"]
    ):
        reject("E_CROSS_RUN_SPLICE", "merged manifest context differs")
    lane = manifest.get("lane")
    if kind.endswith("-final"):
        if lane != "external-matrix" or manifest.get("complete") is not True:
            reject("E_MANIFEST", "final manifest identity differs")
        expected_variants = [variant["variant_key"] for variant in contract["variants"]]
    else:
        if not isinstance(lane, str) or lane == "external-matrix" or manifest.get("complete") is not False:
            reject("E_MANIFEST", "subset manifest identity differs")
        expected_variants = [
            variant["variant_key"] for variant in contract["variants"] if variant["lane"] == lane
        ]
    root = manifest_path.parent
    bundled_outer = ensure_regular(root / "outer-run-marker.json", "E_EVIDENCE", "bundled outer marker")
    if bundled_outer.read_bytes() != outer["_path"].read_bytes():
        reject("E_CROSS_RUN_SPLICE", "merged bundled outer marker differs")
    records = manifest.get("variants")
    if not isinstance(records, list) or [record.get("variant_key") for record in records] != expected_variants:
        reject("E_MISSING_REPORT", "merged variant set/order differs")
    totals = {"variants": 0, "reports": 0, "testcase_nodes": 0, "failures": 0, "errors": 0, "skipped": 0}
    for record in records:
        if not isinstance(record, dict) or set(record) != {
            "variant_key", "manifest_path", "manifest_sha256", "tree_sha256"
        }:
            reject("E_MANIFEST", "merged variant record fields differ")
        expected_path = f"variants/{record['variant_key']}/report-manifest.json"
        if record["manifest_path"] != expected_path:
            reject("E_EVIDENCE", "merged variant manifest path differs")
        path = ensure_regular(root / expected_path, "E_EVIDENCE", "merged variant manifest")
        if sha256_file(path) != record["manifest_sha256"]:
            reject("E_EVIDENCE", "merged variant manifest SHA differs")
        if tree_hash(path.parent) != record["tree_sha256"]:
            reject("E_EVIDENCE", "merged variant tree SHA differs")
        variant_manifest = verify_variant_manifest(contract, outer, path)
        for key in totals:
            totals[key] += variant_manifest["totals"][key]
    if manifest.get("totals") != totals:
        reject("E_REPORT_COUNT", "merged totals differ")
    expected_files = {"report-manifest.json", "outer-run-marker.json"}
    for record in records:
        variant_root = root / "variants" / record["variant_key"]
        expected_files.update(
            f"variants/{record['variant_key']}/{path.relative_to(variant_root).as_posix()}"
            for path in variant_root.rglob("*") if path.is_file() or path.is_symlink()
        )
    actual_files = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*") if path.is_file() or path.is_symlink()
    }
    if actual_files != expected_files:
        reject("E_EVIDENCE", "merged evidence file set differs")
    return manifest


def verify_any(contract: dict[str, Any], outer_path: Path, manifest_path: Path) -> dict[str, Any]:
    outer = load_outer_marker(outer_path, contract)
    manifest = load_json_manifest(manifest_path)
    if manifest.get("kind") == "v934-step3-external-matrix-variant-evidence":
        return verify_variant_manifest(contract, outer, manifest_path)
    return verify_merged_manifest(contract, outer, manifest_path)


def write_marker(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    atomic_json(path, payload)


def synthetic_xml(
    name: str,
    tests: int,
    run_id: str,
    variant: str,
    selector: str,
    outcome: str | None = None,
) -> str:
    failures = 1 if outcome == "failure" else 0
    errors = 1 if outcome == "error" else 0
    skipped = 1 if outcome == "skipped" else 0
    cases = []
    for index in range(tests):
        child = f"<{outcome}/>" if outcome and index == 0 else ""
        cases.append(f'<testcase classname="{name}" name="case-{index}">{child}</testcase>')
    properties = (
        "<properties>"
        f'<property name="v934.external.run-id" value="{run_id}"/>'
        f'<property name="v934.external.variant" value="{variant}"/>'
        f'<property name="it.test" value="{selector}"/>'
        '<property name="skipITs" value="false"/>'
        '<property name="failsafe.rerunFailingTestsCount" value="0"/>'
        "</properties>"
    )
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        f'<testsuite name="{name}" tests="{tests}" failures="{failures}" '
        f'errors="{errors}" skipped="{skipped}">' + properties
        + "".join(cases) + "</testsuite>\n"
    )


def run_negative_probes(contract: dict[str, Any], output: Path) -> None:
    rows: list[dict[str, str]] = []
    base = ROOT / "target/v934-step3-external-matrix/negative" / f"probe-{uuid.uuid4().hex}"
    base.mkdir(parents=True)
    try:
        for index, probe in enumerate(contract["negative_probes"]):
            probe_name = probe["probe"]
            expected_error = probe["expected_error"]
            root = base / f"{index:02d}-{probe_name}"
            run_id = f"negative-{index}-{uuid.uuid4().hex[:8]}"
            run_root = ROOT / "target/v934-step3-external-matrix/runs" / run_id
            outer_path = run_root / "run-context.json"
            marker_path = run_root / "variants/redis7/run-marker.json"
            report_root = root / "reports"
            evidence_root = root / "evidence"
            started = dt.datetime.now(dt.timezone.utc).isoformat()
            outer_payload = {
                "schema_version": 1,
                "kind": "v934-step3-external-matrix-outer-run",
                "run_id": run_id,
                "lane": "external-matrix",
                "runner": "failsafe",
                "git_head": current_git_head(),
                "contract_sha256": contract["_contract_sha256"],
                "started_at": started,
                "status": "started",
            }
            write_marker(outer_path, outer_payload)
            marker_payload = {
                **outer_payload,
                "kind": "v934-step3-external-matrix-variant-run",
                "variant_key": "redis7",
                "infra_kind": "redis",
                "outer_marker_sha256": sha256_file(outer_path),
                "selector": expected_rows(contract, "redis7")[0]["report_fqcn"],
            }
            if probe_name == "wrong-variant-marker":
                marker_payload["variant_key"] = "redis7-sqlite"
            write_marker(marker_path, marker_payload)
            report_root.mkdir(parents=True)
            expected = expected_rows(contract, "redis7")[0]
            report_name = expected["report_fqcn"]
            report_path = report_root / f"TEST-{report_name}.xml"
            outcome = None
            tests = int(expected["expected_testcase_nodes"])
            if probe_name == "wrong-test-count":
                tests += 1
            elif probe_name == "failure-outcome":
                outcome = "failure"
            elif probe_name == "error-outcome":
                outcome = "error"
            elif probe_name == "skipped-outcome":
                outcome = "skipped"
            elif probe_name == "flaky-outcome":
                outcome = "flakyFailure"
            report_run_id = "another-run" if probe_name == "raw-report-splice" else run_id
            if probe_name != "missing-report":
                report_path.write_text(
                    synthetic_xml(
                        report_name,
                        tests,
                        report_run_id,
                        "redis7",
                        marker_payload["selector"],
                        outcome,
                    ),
                    encoding="utf-8",
                )
                fresh_ns = marker_path.stat().st_mtime_ns + 1_000_000
                if probe_name == "stale-report":
                    fresh_ns = marker_path.stat().st_mtime_ns
                os.utime(report_path, ns=(fresh_ns, fresh_ns))
            if probe_name == "extra-report":
                extra = report_root / "TEST-extra.ExternalIT.xml"
                extra.write_text(
                    synthetic_xml(
                        "extra.ExternalIT", 1, run_id, "redis7",
                        marker_payload["selector"],
                    ),
                    encoding="utf-8",
                )
                fresh_ns = marker_path.stat().st_mtime_ns + 1_000_000
                os.utime(extra, ns=(fresh_ns, fresh_ns))
            if probe_name == "duplicate-report-identity":
                duplicate = report_root / "duplicate" / f"TEST-{report_name}.xml"
                duplicate.parent.mkdir()
                duplicate.write_text(
                    synthetic_xml(
                        report_name, tests, run_id, "redis7",
                        marker_payload["selector"],
                    ),
                    encoding="utf-8",
                )
                fresh_ns = marker_path.stat().st_mtime_ns + 1_000_000
                os.utime(duplicate, ns=(fresh_ns, fresh_ns))
            actual_error = "NO_ERROR"
            try:
                manifest_path = collect_variant(
                    contract,
                    "redis7",
                    outer_path,
                    marker_path,
                    [f"addons/foggy-dataset-model-cache={report_root}"],
                    evidence_root,
                )
                if probe_name == "cross-run-manifest-splice":
                    manifest = load_json_manifest(manifest_path)
                    manifest["run_id"] = "another-run"
                    atomic_json(manifest_path, manifest)
                    verify_any(contract, outer_path, manifest_path)
            except ContractError as error:
                actual_error = error.code
            status = "passed" if actual_error == expected_error else "failed"
            rows.append({
                "probe": probe_name,
                "expected_error": expected_error,
                "actual_error": actual_error,
                "status": status,
            })
            shutil.rmtree(run_root, ignore_errors=True)
        if any(row["status"] != "passed" for row in rows):
            details = ", ".join(
                f"{row['probe']}={row['actual_error']}/{row['expected_error']}"
                for row in rows if row["status"] != "passed"
            )
            reject("E_NEGATIVE", f"one or more negative probes failed: {details}")
        content = "probe\texpected_error\tactual_error\tstatus\n" + "".join(
            "\t".join(row[key] for key in ("probe", "expected_error", "actual_error", "status")) + "\n"
            for row in rows
        )
        atomic_text(output, content)
    finally:
        shutil.rmtree(base, ignore_errors=True)


def parse_env(path: Path, expected_fields: set[str], code: str) -> dict[str, str]:
    ensure_regular(path, code, "environment evidence")
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or "=" not in line:
            reject(code, f"malformed environment evidence: {path}")
        key, value = line.split("=", 1)
        if not key or key in values:
            reject(code, f"duplicate/blank environment key: {path}")
        values[key] = value
    if set(values) != expected_fields:
        reject(code, f"environment evidence fields differ: {path}")
    return values


def source_manifest_digest(path: Path) -> str:
    ensure_regular(path, "E_CANDIDATE", "source seal")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["path", "sha256", "size_bytes"]:
            reject("E_CANDIDATE", "source seal header differs")
        rows = list(reader)
    if not rows or any(None in row or None in row.values() for row in rows):
        reject("E_CANDIDATE", "source seal rows are malformed")
    if [row["path"] for row in rows] != sorted({row["path"] for row in rows}):
        reject("E_CANDIDATE", "source seal paths are not exact sorted unique")
    digest = hashlib.sha256()
    for row in rows:
        if re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is None:
            reject("E_CANDIDATE", "source seal contains an invalid SHA-256")
        try:
            size = int(row["size_bytes"])
        except ValueError as error:
            raise ContractError("E_CANDIDATE", "source seal size is not an integer") from error
        if size < 0:
            reject("E_CANDIDATE", "source seal contains a negative size")
        digest.update(row["path"].encode())
        digest.update(b"\0")
        digest.update(row["sha256"].encode())
        digest.update(b"\0")
        digest.update(str(size).encode())
        digest.update(b"\n")
    return digest.hexdigest()


def source_seal_inputs(lane: str) -> tuple[str, ...]:
    if lane == "external-mongo":
        return (
            "pom.xml",
            "foggy-core/pom.xml", "foggy-core/src/main",
            "foggy-bean-copy/pom.xml", "foggy-bean-copy/src/main",
            "foggy-mcp-spi/pom.xml", "foggy-mcp-spi/src/main",
            "foggy-fsscript/pom.xml", "foggy-fsscript/src/main",
            "foggy-dataset/pom.xml", "foggy-dataset/src/main",
            "foggy-dataset-demo/pom.xml", "foggy-dataset-demo/src/main",
            "foggy-dataset-model/pom.xml", "foggy-dataset-model/src/main",
            "addons/foggy-data-viewer/pom.xml", "addons/foggy-data-viewer/src",
            "addons/foggy-dataset-model-mongo/pom.xml",
            "addons/foggy-dataset-model-mongo/src",
        )
    if lane == "external-mysql":
        return (
            "pom.xml",
            "foggy-core/pom.xml", "foggy-core/src/main",
            "foggy-bean-copy/pom.xml", "foggy-bean-copy/src/main",
            "foggy-mcp-spi/pom.xml", "foggy-mcp-spi/src/main",
            "foggy-fsscript/pom.xml", "foggy-fsscript/src/main",
            "foggy-dataset/pom.xml", "foggy-dataset/src/main",
            "foggy-dataset-demo/pom.xml", "foggy-dataset-demo/src/main",
            "foggy-dataset-demo/docker/mysql/init",
            "foggy-dataset-model/pom.xml", "foggy-dataset-model/src/main",
            "foggy-dataset-mcp/pom.xml", "foggy-dataset-mcp/src",
        )
    reject("E_SEAL", f"unsupported source seal lane: {lane}")


def create_source_seal(lane: str, output: Path) -> str:
    if output.exists() or output.is_symlink():
        reject("E_OUTPUT", f"source seal output exists: {output}")
    paths: list[Path] = []
    for value in source_seal_inputs(lane):
        candidate = ROOT / value
        reject_symlink_components(candidate, "E_SEAL")
        if candidate.is_file():
            paths.append(candidate)
        elif candidate.is_dir():
            for path in candidate.rglob("*"):
                if path.is_symlink():
                    reject("E_SEAL", f"protected source tree contains a symlink: {path}")
                if path.is_file():
                    paths.append(path)
        else:
            reject("E_SEAL", f"protected source path is missing: {value}")
    rows = []
    for path in sorted(set(paths), key=lambda item: item.relative_to(ROOT).as_posix()):
        rows.append((
            path.relative_to(ROOT).as_posix(), sha256_file(path), path.stat().st_size,
        ))
    content = "path\tsha256\tsize_bytes\n" + "".join(
        f"{relative}\t{digest}\t{size}\n" for relative, digest, size in rows
    )
    atomic_text(output, content)
    return source_manifest_digest(output)


def create_bytecode_seal(lane: str, output: Path) -> None:
    if output.exists() or output.is_symlink():
        reject("E_OUTPUT", f"bytecode seal output exists: {output}")
    modules_by_lane = {
        "external-mongo": MONGO_CLEAN_MODULES,
        "external-mysql": MYSQL_CLEAN_MODULES,
    }
    modules = modules_by_lane.get(lane)
    if modules is None:
        reject("E_SEAL", f"unsupported bytecode seal lane: {lane}")
    rows: list[tuple[str, str, int, str]] = []
    for module in modules:
        main_root = ROOT / module / "target/classes"
        reject_symlink_components(main_root, "E_SEAL")
        if not main_root.is_dir():
            reject("E_SEAL", f"fresh main bytecode tree is missing: {module}")
        for tree_name in ("classes", "test-classes"):
            tree = ROOT / module / "target" / tree_name
            if not tree.exists():
                continue
            reject_symlink_components(tree, "E_SEAL")
            if not tree.is_dir():
                reject("E_SEAL", f"bytecode tree is not a directory: {tree}")
            files = []
            for path in tree.rglob("*"):
                if path.is_symlink():
                    reject("E_SEAL", f"bytecode tree contains a symlink: {path}")
                if path.is_file():
                    files.append(path)
            files.sort(key=lambda item: item.relative_to(tree).as_posix())
            if tree_name == "classes" and not files:
                reject("E_SEAL", f"main bytecode tree is empty: {module}")
            digest = hashlib.sha256()
            for path in files:
                for value in (path.relative_to(tree).as_posix(), sha256_file(path)):
                    encoded = value.encode("utf-8")
                    digest.update(str(len(encoded)).encode("ascii"))
                    digest.update(b":")
                    digest.update(encoded)
                    digest.update(b"\0")
            rows.append((module, tree_name, len(files), digest.hexdigest()))
    content = "module\ttree\tfiles\tsha256\n" + "".join(
        f"{module}\t{tree}\t{count}\t{digest}\n"
        for module, tree, count, digest in rows
    )
    atomic_text(output, content)
    validate_bytecode_seal(output, lane)


def validate_negative_evidence(path: Path, contract: dict[str, Any]) -> None:
    with ensure_regular(path, "E_CANDIDATE", "negative evidence").open(
        encoding="utf-8", newline=""
    ) as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["probe", "expected_error", "actual_error", "status"]:
            reject("E_CANDIDATE", "negative evidence header differs")
        rows = list(reader)
    expected = {
        probe["probe"]: probe["expected_error"] for probe in contract["negative_probes"]
    }
    if (
        len(rows) != len(expected)
        or {row.get("probe") for row in rows} != set(expected)
        or any(
            row.get("expected_error") != expected.get(row.get("probe"))
            or row.get("actual_error") != expected.get(row.get("probe"))
            or row.get("status") != "passed"
            for row in rows
        )
    ):
        reject("E_CANDIDATE", "negative evidence does not prove the exact contract")


def validate_sensitive_negative_evidence(path: Path, lane: str) -> None:
    expected_by_lane = {
        "external-redis": {
            "redis-env", "json-password", "api-key", "bearer",
            "redis-uri", "cli-password",
        },
        "external-mongo": {
            "mongo-env", "json-password", "api-key", "auth-header",
            "mongo-uri", "cli-password",
        },
        "external-mysql": {
            "mysql-env", "json-password", "api-key", "auth-header",
            "mysql-uri", "cli-password",
        },
    }
    expected = expected_by_lane.get(lane)
    if expected is None:
        reject("E_CANDIDATE", f"unsupported candidate lane: {lane}")
    with ensure_regular(path, "E_CANDIDATE", "sensitive negative evidence").open(
        encoding="utf-8", newline=""
    ) as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["probe", "status"]:
            reject("E_CANDIDATE", "sensitive negative evidence header differs")
        rows = list(reader)
    if (
        len(rows) != len(expected)
        or {row.get("probe") for row in rows} != expected
        or any(row.get("status") != "passed" for row in rows)
    ):
        reject("E_CANDIDATE", "sensitive negative evidence differs")


def validate_bytecode_seal(path: Path, lane: str) -> None:
    with ensure_regular(path, "E_CANDIDATE", "bytecode seal").open(
        encoding="utf-8", newline=""
    ) as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["module", "tree", "files", "sha256"]:
            reject("E_CANDIDATE", "bytecode seal header differs")
        rows = list(reader)
    keys = [(row.get("module"), row.get("tree")) for row in rows]
    if len(keys) != len(set(keys)):
        reject("E_CANDIDATE", "bytecode seal contains duplicate trees")
    main_modules = {
        row.get("module") for row in rows if row.get("tree") == "classes"
    }
    clean_modules_by_lane = {
        "external-redis": REDIS_CLEAN_MODULES,
        "external-mongo": MONGO_CLEAN_MODULES,
        "external-mysql": MYSQL_CLEAN_MODULES,
    }
    clean_modules = clean_modules_by_lane.get(lane)
    if clean_modules is None:
        reject("E_CANDIDATE", f"unsupported candidate lane: {lane}")
    if main_modules != set(clean_modules):
        reject("E_CANDIDATE", "bytecode seal main module set differs")
    required_test_modules = {
        "external-redis": {"addons/foggy-dataset-model-cache"},
        "external-mongo": {
            "addons/foggy-data-viewer", "addons/foggy-dataset-model-mongo",
        },
        "external-mysql": {"foggy-dataset-mcp"},
    }[lane]
    sealed_test_modules = {
        row.get("module") for row in rows
        if row.get("tree") == "test-classes"
        and re.fullmatch(r"[0-9]+", row.get("files", "")) is not None
        and int(row["files"]) > 0
    }
    if not required_test_modules.issubset(sealed_test_modules):
        reject("E_CANDIDATE", "required test bytecode seal is missing")
    for row in rows:
        if row.get("tree") not in {"classes", "test-classes"}:
            reject("E_CANDIDATE", "bytecode seal tree kind differs")
        try:
            files = int(row.get("files", ""))
        except ValueError as error:
            raise ContractError("E_CANDIDATE", "bytecode file count is invalid") from error
        if files < 0 or (row.get("tree") == "classes" and files == 0):
            reject("E_CANDIDATE", "bytecode seal file count differs")
        if re.fullmatch(r"[0-9a-f]{64}", row.get("sha256", "")) is None:
            reject("E_CANDIDATE", "bytecode seal SHA-256 is invalid")


def candidate_required_paths(lane: str) -> set[str]:
    common = {
        "run-context.json", "run-status.env", "summary.env", "preclean.env",
        "source-before.tsv", "source-after.tsv", "run.log",
        "negative/probes.tsv",
        "negative/sensitive-probes.tsv", "sensitive-scan.env",
        "final/report-manifest.json",
    }
    if lane == "external-redis":
        return common | {
            "cells/redis7/resource.env", "cells/redis7/fixture.env",
            "cells/redis7/cleanup.env", "variants/redis7/bytecode.tsv",
            "variants/redis7-sqlite/bytecode.tsv",
        }
    if lane == "external-mongo":
        return common | {
            "cells/mongo6/resource.env", "cells/mongo6/fixture.env",
            "cells/mongo6/cleanup.env", "variants/mongo6/bytecode.tsv",
        }
    if lane == "external-mysql":
        return common | {
            "cells/mysql57/resource.env", "cells/mysql57/fixture.env",
            "cells/mysql57/cleanup.env",
            "cells/mysql57/bundle.env",
            "cells/mysql57/bundle-manifest.tsv",
            "cells/mysql57/init-manifest.tsv",
            "cells/mysql57/fixture-before.tsv",
            "cells/mysql57/fixture-after.tsv",
            "cells/mysql57/grants-before.env",
            "cells/mysql57/grants-after.env",
            "variants/mysql57-mcp/bytecode.tsv",
            "variants/mysql57-direct/bytecode.tsv",
            "variants/mysql57-direct/direct-report.json",
            "variants/mysql57-compose/bytecode.tsv",
        }
    reject("E_CANDIDATE", f"unsupported candidate lane: {lane}")


def candidate_definition(lane: str) -> dict[str, Any]:
    definitions = {
        "external-redis": {
            "kind": "v934-step3-external-redis-candidate",
            "cell": "redis7",
            "totals": {
                "variants": 2, "reports": 2, "testcase_nodes": 3,
                "failures": 0, "errors": 0, "skipped": 0,
            },
        },
        "external-mongo": {
            "kind": "v934-step3-external-mongo-candidate",
            "cell": "mongo6",
            "totals": {
                "variants": 1, "reports": 4, "testcase_nodes": 30,
                "failures": 0, "errors": 0, "skipped": 0,
            },
        },
        "external-mysql": {
            "kind": "v934-step3-external-mysql-candidate",
            "cell": "mysql57",
            "totals": {
                "variants": 3, "reports": 8, "testcase_nodes": 23,
                "failures": 0, "errors": 0, "skipped": 0,
            },
        },
    }
    definition = definitions.get(lane)
    if definition is None:
        reject("E_CANDIDATE", f"unsupported candidate lane: {lane}")
    return definition


def create_candidate(
    contract: dict[str, Any], outer_path: Path, run_root: Path, output: Path
) -> Path:
    ensure_directory(run_root, "E_CANDIDATE", "candidate run root")
    if output != run_root / "candidate-manifest.json":
        reject("E_OUTPUT", "candidate manifest must be at the run-root canonical path")
    if output.exists() or output.is_symlink():
        reject("E_OUTPUT", f"candidate manifest already exists: {output}")
    outer = load_outer_marker(outer_path, contract, require_current_head=True)
    if outer_path != run_root / "run-context.json":
        reject("E_CANDIDATE", "outer marker is not at the candidate canonical path")
    final_path = run_root / "final/report-manifest.json"
    final_manifest = verify_merged_manifest(contract, outer, final_path)
    lane = final_manifest.get("lane")
    definition = candidate_definition(lane)
    if (
        final_manifest["kind"] != "v934-step3-external-matrix-subset"
        or final_manifest["complete"] is not False
        or final_manifest["totals"] != definition["totals"]
    ):
        reject("E_CANDIDATE", f"{lane} final subset identity differs")
    files: list[Path] = []
    for path in run_root.rglob("*"):
        if path.is_symlink():
            reject("E_CANDIDATE", f"candidate tree contains a symlink: {path}")
        if path.is_file():
            files.append(path)
        elif not path.is_dir():
            reject("E_CANDIDATE", f"candidate tree contains a special file: {path}")
    files.sort(key=lambda path: path.relative_to(run_root).as_posix())
    relative_files = {path.relative_to(run_root).as_posix() for path in files}
    missing = candidate_required_paths(lane) - relative_files
    if missing:
        reject("E_CANDIDATE", f"candidate required files are missing: {sorted(missing)}")
    records = [
        artifact(path, path.relative_to(run_root).as_posix()) for path in files
    ]
    candidate = {
        "schema_version": 1,
        "kind": definition["kind"],
        "run_id": outer["run_id"],
        "runner": "failsafe",
        "lane": lane,
        "git_head": outer["git_head"],
        "contract_sha256": contract["_contract_sha256"],
        "outer_marker_sha256": outer["_sha256"],
        "report_manifest_sha256": sha256_file(final_path),
        "artifacts": records,
        "totals": final_manifest["totals"],
    }
    atomic_json(output, candidate)
    verify_candidate(contract, output)
    return output


def verify_redis_candidate(contract: dict[str, Any], candidate_path: Path) -> dict[str, Any]:
    ensure_regular(candidate_path, "E_CANDIDATE", "candidate manifest")
    if candidate_path.name != "candidate-manifest.json":
        reject("E_CANDIDATE", "candidate manifest name differs")
    root = candidate_path.parent
    candidate = load_json_manifest(candidate_path)
    if set(candidate) != CANDIDATE_MANIFEST_FIELDS:
        reject("E_CANDIDATE", "candidate manifest fields differ")
    outer = load_outer_marker(root / "run-context.json", contract)
    if (
        candidate.get("schema_version") != 1
        or candidate.get("kind") != "v934-step3-external-redis-candidate"
        or candidate.get("run_id") != outer["run_id"]
        or candidate.get("runner") != "failsafe"
        or candidate.get("lane") != "external-redis"
        or candidate.get("git_head") != outer["git_head"]
        or candidate.get("contract_sha256") != contract["_contract_sha256"]
        or candidate.get("outer_marker_sha256") != outer["_sha256"]
    ):
        reject("E_CANDIDATE", "candidate context differs")
    records = candidate.get("artifacts")
    if not isinstance(records, list) or not records:
        reject("E_CANDIDATE", "candidate artifacts are empty")
    paths = [record.get("path") for record in records if isinstance(record, dict)]
    if len(paths) != len(records) or paths != sorted(set(paths)):
        reject("E_CANDIDATE", "candidate artifact paths are not exact sorted unique")
    artifact_by_path: dict[str, Path] = {}
    for record in records:
        path = validate_artifact(root, record, "E_CANDIDATE")
        artifact_by_path[record["path"]] = path
    actual_files = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*") if path.is_file() or path.is_symlink()
    }
    if actual_files != set(paths) | {"candidate-manifest.json"}:
        reject("E_CANDIDATE", "candidate run-root file set differs")
    missing = candidate_required_paths("external-redis") - set(paths)
    if missing:
        reject("E_CANDIDATE", f"candidate required artifacts are missing: {sorted(missing)}")

    final_path = artifact_by_path["final/report-manifest.json"]
    final_manifest = verify_merged_manifest(contract, outer, final_path)
    expected_totals = {
        "variants": 2, "reports": 2, "testcase_nodes": 3,
        "failures": 0, "errors": 0, "skipped": 0,
    }
    if (
        final_manifest.get("lane") != "external-redis"
        or final_manifest.get("complete") is not False
        or final_manifest.get("totals") != expected_totals
        or candidate.get("totals") != expected_totals
        or candidate.get("report_manifest_sha256") != sha256_file(final_path)
    ):
        reject("E_CANDIDATE", "candidate report subset differs")

    summary = parse_env(artifact_by_path["summary.env"], CANDIDATE_SUMMARY_FIELDS, "E_CANDIDATE")
    status = parse_env(artifact_by_path["run-status.env"], RUN_STATUS_FIELDS, "E_CANDIDATE")
    expected_scalar = {
        "run_id": outer["run_id"], "runner": "failsafe", "lane": "external-redis",
        "git_head": outer["git_head"], "variants": "2", "reports": "2",
        "testcase_nodes": "3", "failures": "0", "errors": "0", "skipped": "0",
        "outer_marker_sha256": outer["_sha256"],
        "contract_sha256": contract["_contract_sha256"],
        "negative_probes": "12/12", "sensitive_negative_probes": "6/6",
        "resource_residue": "0/0", "status": "passed",
    }
    if any(summary.get(key) != value for key, value in expected_scalar.items()):
        reject("E_CANDIDATE", "candidate summary identity differs")
    source_before = artifact_by_path["source-before.tsv"]
    source_after = artifact_by_path["source-after.tsv"]
    if source_before.read_bytes() != source_after.read_bytes():
        reject("E_CANDIDATE", "candidate source seal changed during execution")
    source_digest = source_manifest_digest(source_before)
    if summary["source_before"] != source_digest or summary["source_after"] != source_digest:
        reject("E_CANDIDATE", "candidate source summary differs")
    hash_bindings = {
        "final_report_manifest_sha256": "final/report-manifest.json",
        "run_status_sha256": "run-status.env",
        "resource_sha256": "cells/redis7/resource.env",
        "fixture_sha256": "cells/redis7/fixture.env",
        "cleanup_sha256": "cells/redis7/cleanup.env",
        "negative_sha256": "negative/probes.tsv",
        "sensitive_negative_sha256": "negative/sensitive-probes.tsv",
        "sensitive_scan_sha256": "sensitive-scan.env",
    }
    if any(
        summary[key] != sha256_file(artifact_by_path[path])
        for key, path in hash_bindings.items()
    ):
        reject("E_CANDIDATE", "candidate summary artifact hash differs")
    deferred_sha = contract["bindings"]["deferred_inventory"]["sha256"]
    expected_status = {
        "run_id": outer["run_id"], "runner": "failsafe", "git_head": outer["git_head"],
        "last_phase": "completed", "exit_code": "0", "source_before_sha256": source_digest,
        "source_after_sha256": source_digest, "outer_marker_sha256": outer["_sha256"],
        "successor_manifest_sha256": deferred_sha,
        "final_report_manifest_sha256": sha256_file(final_path), "status": "passed",
    }
    if any(status.get(key) != value for key, value in expected_status.items()):
        reject("E_CANDIDATE", "candidate durable status differs")
    if parse_timestamp(status["finished_at"], "E_CANDIDATE") < parse_timestamp(
        status["started_at"], "E_CANDIDATE"
    ):
        reject("E_CANDIDATE", "candidate finish time predates start time")

    resource_fields = {
        "run_id", "cell", "container", "image_ref", "image_id", "mapped_port",
        "mount_count", "mount_identity", "volume", "volume_created", "redis_version",
        "redis_mode", "initial_dbsize", "status",
    }
    resource = parse_env(artifact_by_path["cells/redis7/resource.env"], resource_fields, "E_CANDIDATE")
    scope = hashlib.sha256(f"{outer['run_id']}|redis7\n".encode()).hexdigest()[:12]
    container = f"v934ext-redis7-{scope}"
    volume = f"{container}-data"
    if (
        resource != {
            "run_id": outer["run_id"], "cell": "redis7", "container": container,
            "image_ref": REDIS_IMAGE_REF, "image_id": REDIS_IMAGE_ID,
            "mapped_port": resource["mapped_port"], "mount_count": "1",
            "mount_identity": f"{volume}|/data|volume", "volume": volume,
            "volume_created": resource["volume_created"], "redis_version": "7.4.6",
            "redis_mode": "standalone", "initial_dbsize": "0", "status": "verified",
        }
        or re.fullmatch(r"127\.0\.0\.1:[0-9]+", resource["mapped_port"]) is None
    ):
        reject("E_CANDIDATE", "candidate Redis resource identity differs")
    parse_timestamp(resource["volume_created"], "E_CANDIDATE")
    fixture = parse_env(
        artifact_by_path["cells/redis7/fixture.env"],
        {"cell", "redis7_key_count", "redis7_sqlite_key_count", "total_key_count",
         "foreign_key_count", "status"},
        "E_CANDIDATE",
    )
    if fixture != {
        "cell": "redis7", "redis7_key_count": "4", "redis7_sqlite_key_count": "0",
        "total_key_count": "4", "foreign_key_count": "0", "status": "verified",
    }:
        reject("E_CANDIDATE", "candidate Redis fixture differs")
    cleanup = parse_env(
        artifact_by_path["cells/redis7/cleanup.env"],
        {"cell", "container", "volume", "container_residue", "volume_residue", "status"},
        "E_CANDIDATE",
    )
    if cleanup != {
        "cell": "redis7", "container": container, "volume": volume,
        "container_residue": "0", "volume_residue": "0", "status": "passed",
    }:
        reject("E_CANDIDATE", "candidate Redis cleanup differs")
    preclean = parse_env(
        artifact_by_path["preclean.env"],
        {"modules", "root_target_preserved", "status"},
        "E_CANDIDATE",
    )
    if preclean != {
        "modules": ",".join(REDIS_CLEAN_MODULES),
        "root_target_preserved": "true", "status": "passed",
    }:
        reject("E_CANDIDATE", "candidate preclean evidence differs")
    validate_negative_evidence(artifact_by_path["negative/probes.tsv"], contract)
    validate_sensitive_negative_evidence(
        artifact_by_path["negative/sensitive-probes.tsv"], "external-redis"
    )
    sensitive = parse_env(
        artifact_by_path["sensitive-scan.env"], {"patterns", "status"}, "E_CANDIDATE"
    )
    if sensitive != {"patterns": "5", "status": "passed"}:
        reject("E_CANDIDATE", "candidate sensitive scan evidence differs")
    validate_bytecode_seal(
        artifact_by_path["variants/redis7/bytecode.tsv"], "external-redis"
    )
    validate_bytecode_seal(
        artifact_by_path["variants/redis7-sqlite/bytecode.tsv"], "external-redis"
    )
    if (
        artifact_by_path["variants/redis7/bytecode.tsv"].read_bytes()
        != artifact_by_path["variants/redis7-sqlite/bytecode.tsv"].read_bytes()
    ):
        reject("E_CANDIDATE", "candidate bytecode changed between Redis variants")
    return candidate


def verify_mongo_candidate(contract: dict[str, Any], candidate_path: Path) -> dict[str, Any]:
    ensure_regular(candidate_path, "E_CANDIDATE", "candidate manifest")
    if candidate_path.name != "candidate-manifest.json":
        reject("E_CANDIDATE", "candidate manifest name differs")
    root = candidate_path.parent
    candidate = load_json_manifest(candidate_path)
    if set(candidate) != CANDIDATE_MANIFEST_FIELDS:
        reject("E_CANDIDATE", "candidate manifest fields differ")
    outer = load_outer_marker(root / "run-context.json", contract)
    definition = candidate_definition("external-mongo")
    if (
        candidate.get("schema_version") != 1
        or candidate.get("kind") != definition["kind"]
        or candidate.get("run_id") != outer["run_id"]
        or candidate.get("runner") != "failsafe"
        or candidate.get("lane") != "external-mongo"
        or candidate.get("git_head") != outer["git_head"]
        or candidate.get("contract_sha256") != contract["_contract_sha256"]
        or candidate.get("outer_marker_sha256") != outer["_sha256"]
    ):
        reject("E_CANDIDATE", "candidate context differs")
    records = candidate.get("artifacts")
    if not isinstance(records, list) or not records:
        reject("E_CANDIDATE", "candidate artifacts are empty")
    paths = [record.get("path") for record in records if isinstance(record, dict)]
    if len(paths) != len(records) or paths != sorted(set(paths)):
        reject("E_CANDIDATE", "candidate artifact paths are not exact sorted unique")
    artifact_by_path: dict[str, Path] = {}
    for record in records:
        path = validate_artifact(root, record, "E_CANDIDATE")
        artifact_by_path[record["path"]] = path
    actual_files = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*") if path.is_file() or path.is_symlink()
    }
    if actual_files != set(paths) | {"candidate-manifest.json"}:
        reject("E_CANDIDATE", "candidate run-root file set differs")
    missing = candidate_required_paths("external-mongo") - set(paths)
    if missing:
        reject("E_CANDIDATE", f"candidate required artifacts are missing: {sorted(missing)}")

    final_path = artifact_by_path["final/report-manifest.json"]
    final_manifest = verify_merged_manifest(contract, outer, final_path)
    expected_totals = definition["totals"]
    if (
        final_manifest.get("lane") != "external-mongo"
        or final_manifest.get("complete") is not False
        or final_manifest.get("totals") != expected_totals
        or candidate.get("totals") != expected_totals
        or candidate.get("report_manifest_sha256") != sha256_file(final_path)
    ):
        reject("E_CANDIDATE", "candidate report subset differs")

    summary = parse_env(
        artifact_by_path["summary.env"], CANDIDATE_SUMMARY_FIELDS, "E_CANDIDATE"
    )
    status = parse_env(
        artifact_by_path["run-status.env"], RUN_STATUS_FIELDS, "E_CANDIDATE"
    )
    expected_scalar = {
        "run_id": outer["run_id"], "runner": "failsafe", "lane": "external-mongo",
        "git_head": outer["git_head"], "variants": "1", "reports": "4",
        "testcase_nodes": "30", "failures": "0", "errors": "0", "skipped": "0",
        "outer_marker_sha256": outer["_sha256"],
        "contract_sha256": contract["_contract_sha256"],
        "negative_probes": "12/12", "sensitive_negative_probes": "6/6",
        "resource_residue": "0/0", "status": "passed",
    }
    if any(summary.get(key) != value for key, value in expected_scalar.items()):
        reject("E_CANDIDATE", "candidate summary identity differs")
    source_before = artifact_by_path["source-before.tsv"]
    source_after = artifact_by_path["source-after.tsv"]
    if source_before.read_bytes() != source_after.read_bytes():
        reject("E_CANDIDATE", "candidate source seal changed during execution")
    source_digest = source_manifest_digest(source_before)
    if summary["source_before"] != source_digest or summary["source_after"] != source_digest:
        reject("E_CANDIDATE", "candidate source summary differs")
    hash_bindings = {
        "final_report_manifest_sha256": "final/report-manifest.json",
        "run_status_sha256": "run-status.env",
        "resource_sha256": "cells/mongo6/resource.env",
        "fixture_sha256": "cells/mongo6/fixture.env",
        "cleanup_sha256": "cells/mongo6/cleanup.env",
        "negative_sha256": "negative/probes.tsv",
        "sensitive_negative_sha256": "negative/sensitive-probes.tsv",
        "sensitive_scan_sha256": "sensitive-scan.env",
    }
    if any(
        summary[key] != sha256_file(artifact_by_path[path])
        for key, path in hash_bindings.items()
    ):
        reject("E_CANDIDATE", "candidate summary artifact hash differs")
    deferred_sha = contract["bindings"]["deferred_inventory"]["sha256"]
    expected_status = {
        "run_id": outer["run_id"], "runner": "failsafe", "git_head": outer["git_head"],
        "last_phase": "completed", "exit_code": "0", "source_before_sha256": source_digest,
        "source_after_sha256": source_digest, "outer_marker_sha256": outer["_sha256"],
        "successor_manifest_sha256": deferred_sha,
        "final_report_manifest_sha256": sha256_file(final_path), "status": "passed",
    }
    if any(status.get(key) != value for key, value in expected_status.items()):
        reject("E_CANDIDATE", "candidate durable status differs")
    if parse_timestamp(status["finished_at"], "E_CANDIDATE") < parse_timestamp(
        status["started_at"], "E_CANDIDATE"
    ):
        reject("E_CANDIDATE", "candidate finish time predates start time")

    scope = hashlib.sha256(f"{outer['run_id']}|mongo6\n".encode()).hexdigest()[:12]
    container = f"v934ext-mongo6-{scope}"
    data_volume = f"{container}-data"
    config_volume = f"{container}-config"
    model_database = f"v934_{scope}_model"
    viewer_database = f"v934_{scope}_viewer"
    resource_fields = {
        "run_id", "cell", "container", "image_ref", "image_id", "mapped_port",
        "mount_count", "data_mount_identity", "config_mount_identity", "data_volume",
        "config_volume", "data_volume_created", "config_volume_created", "mongo_version",
        "mongo_git_version", "topology", "auth_mode", "server_process",
        "storage_engine", "model_database", "viewer_database",
        "initial_model_collections", "initial_viewer_collections",
        "initial_foreign_databases", "status",
    }
    resource = parse_env(
        artifact_by_path["cells/mongo6/resource.env"], resource_fields, "E_CANDIDATE"
    )
    expected_resource = {
        "run_id": outer["run_id"], "cell": "mongo6", "container": container,
        "image_ref": MONGO_IMAGE_REF, "image_id": MONGO_IMAGE_ID,
        "mapped_port": resource["mapped_port"], "mount_count": "2",
        "data_mount_identity": f"{data_volume}|/data/db|volume",
        "config_mount_identity": f"{config_volume}|/data/configdb|volume",
        "data_volume": data_volume, "config_volume": config_volume,
        "data_volume_created": resource["data_volume_created"],
        "config_volume_created": resource["config_volume_created"],
        "mongo_version": "6.0.27", "mongo_git_version": MONGO_GIT_VERSION,
        "topology": "standalone", "auth_mode": "disabled",
        "server_process": "mongod", "storage_engine": "wiredTiger",
        "model_database": model_database, "viewer_database": viewer_database,
        "initial_model_collections": "0", "initial_viewer_collections": "0",
        "initial_foreign_databases": "0",
        "status": "verified",
    }
    if (
        resource != expected_resource
        or re.fullmatch(r"127\.0\.0\.1:[0-9]+", resource["mapped_port"]) is None
    ):
        reject("E_CANDIDATE", "candidate Mongo resource identity differs")
    parse_timestamp(resource["data_volume_created"], "E_CANDIDATE")
    parse_timestamp(resource["config_volume_created"], "E_CANDIDATE")

    fixture = parse_env(
        artifact_by_path["cells/mongo6/fixture.env"],
        {
            "cell", "model_database", "viewer_database", "model_collections",
            "mcp_audit_count", "sales_order_count", "geo_station_count",
            "viewer_collections", "list_presets_count", "foreign_database_count", "status",
        },
        "E_CANDIDATE",
    )
    if fixture != {
        "cell": "mongo6", "model_database": model_database,
        "viewer_database": viewer_database,
        "model_collections": "mcp_tool_audit_log,sales_order_test",
        "mcp_audit_count": "25", "sales_order_count": "20", "geo_station_count": "0",
        "viewer_collections": "list_presets", "list_presets_count": "0",
        "foreign_database_count": "0", "status": "verified",
    }:
        reject("E_CANDIDATE", "candidate Mongo fixture differs")
    cleanup = parse_env(
        artifact_by_path["cells/mongo6/cleanup.env"],
        {
            "cell", "container", "data_volume", "config_volume",
            "container_residue", "volume_residue", "status",
        },
        "E_CANDIDATE",
    )
    if cleanup != {
        "cell": "mongo6", "container": container, "data_volume": data_volume,
        "config_volume": config_volume, "container_residue": "0", "volume_residue": "0",
        "status": "passed",
    }:
        reject("E_CANDIDATE", "candidate Mongo cleanup differs")
    preclean = parse_env(
        artifact_by_path["preclean.env"],
        {"modules", "root_target_preserved", "status"}, "E_CANDIDATE",
    )
    if preclean != {
        "modules": ",".join(MONGO_CLEAN_MODULES),
        "root_target_preserved": "true", "status": "passed",
    }:
        reject("E_CANDIDATE", "candidate preclean evidence differs")
    validate_negative_evidence(artifact_by_path["negative/probes.tsv"], contract)
    validate_sensitive_negative_evidence(
        artifact_by_path["negative/sensitive-probes.tsv"], "external-mongo"
    )
    sensitive = parse_env(
        artifact_by_path["sensitive-scan.env"], {"patterns", "status"}, "E_CANDIDATE"
    )
    if sensitive != {"patterns": "5", "status": "passed"}:
        reject("E_CANDIDATE", "candidate sensitive scan evidence differs")
    validate_bytecode_seal(
        artifact_by_path["variants/mongo6/bytecode.tsv"], "external-mongo"
    )
    return candidate


def validate_mysql_init_manifest(path: Path) -> None:
    with ensure_regular(path, "E_CANDIDATE", "MySQL init manifest").open(
        encoding="utf-8", newline=""
    ) as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["path", "sha256", "size_bytes"]:
            reject("E_CANDIDATE", "MySQL init manifest header differs")
        rows = list(reader)
    if any(None in row or any(value is None for value in row.values()) for row in rows):
        reject("E_CANDIDATE", "MySQL init manifest rows are malformed")
    names = [row.get("path") for row in rows]
    if len(rows) != 10 or names != sorted(set(names)):
        reject("E_CANDIDATE", "MySQL init manifest paths are not exact sorted unique")
    init_root = ROOT / "foggy-dataset-demo/docker/mysql/init"
    expected_files = sorted(init_root.glob("*.sql"), key=lambda item: item.name)
    if len(expected_files) != 10 or names != [item.name for item in expected_files]:
        reject("E_CANDIDATE", "MySQL init manifest file set differs")
    for row, source in zip(rows, expected_files, strict=True):
        ensure_regular(source, "E_CANDIDATE", "MySQL init script")
        if (
            row.get("sha256") != sha256_file(source)
            or row.get("size_bytes") != str(source.stat().st_size)
        ):
            reject("E_CANDIDATE", f"MySQL init script identity differs: {source.name}")


def validate_mysql_curated_bundle(
    bundle_root: Path,
    manifest_path: Path,
    evidence_path: Path,
    code: str = "E_CANDIDATE",
) -> None:
    reject_symlink_components(MYSQL57_ECOMMERCE_SOURCE, code)
    if not MYSQL57_ECOMMERCE_SOURCE.is_dir():
        reject(code, "MySQL ecommerce source bundle is missing")
    source_files: list[Path] = []
    for path in MYSQL57_ECOMMERCE_SOURCE.rglob("*"):
        if path.is_symlink():
            reject(code, f"MySQL ecommerce source contains a symlink: {path}")
        if path.is_file():
            relative = path.relative_to(MYSQL57_ECOMMERCE_SOURCE).as_posix()
            if not relative.startswith("demo/"):
                source_files.append(path)
    source_files.sort(key=lambda item: item.relative_to(MYSQL57_ECOMMERCE_SOURCE).as_posix())
    expected_paths = [
        path.relative_to(MYSQL57_ECOMMERCE_SOURCE).as_posix()
        for path in source_files
    ]

    reject_symlink_components(bundle_root, code)
    if not bundle_root.is_dir():
        reject(code, "MySQL curated ecommerce bundle is missing")
    bundle_files: list[Path] = []
    for path in bundle_root.rglob("*"):
        if path.is_symlink():
            reject(code, f"MySQL curated ecommerce bundle contains a symlink: {path}")
        if path.is_file():
            bundle_files.append(path)
    bundle_files.sort(key=lambda item: item.relative_to(bundle_root).as_posix())
    actual_paths = [path.relative_to(bundle_root).as_posix() for path in bundle_files]
    if (
        len(expected_paths) != MYSQL57_CURATED_BUNDLE_COUNTS["files"]
        or expected_paths != actual_paths
        or any(path.startswith("demo/") for path in actual_paths)
    ):
        reject(code, "MySQL curated ecommerce bundle file set differs")

    extension_counts = {
        "files": len(actual_paths),
        "qm_files": sum(path.endswith(".qm") for path in actual_paths),
        "tm_files": sum(path.endswith(".tm") for path in actual_paths),
        "fsscript_files": sum(path.endswith(".fsscript") for path in actual_paths),
    }
    if extension_counts != MYSQL57_CURATED_BUNDLE_COUNTS:
        reject(code, "MySQL curated ecommerce bundle cardinality differs")

    with ensure_regular(
        manifest_path, code, "MySQL curated ecommerce bundle manifest"
    ).open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != ["path", "sha256", "size_bytes"]:
            reject(code, "MySQL curated ecommerce bundle manifest header differs")
        rows = list(reader)
    if (
        any(None in row or any(value is None for value in row.values()) for row in rows)
        or [row["path"] for row in rows] != expected_paths
    ):
        reject(code, "MySQL curated ecommerce bundle manifest rows differ")
    for row, source, destination in zip(rows, source_files, bundle_files, strict=True):
        digest = sha256_file(source)
        size = str(source.stat().st_size)
        if (
            row["sha256"] != digest
            or row["size_bytes"] != size
            or sha256_file(destination) != digest
            or str(destination.stat().st_size) != size
        ):
            reject(code, f"MySQL curated ecommerce resource differs: {row['path']}")

    evidence = parse_env(
        evidence_path,
        {
            "cell", "source", "excluded_prefix", "files", "qm_files",
            "tm_files", "fsscript_files", "manifest_sha256", "status",
        },
        code,
    )
    expected_evidence = {
        "cell": "mysql57",
        "source": "foggy-dataset-demo/src/main/resources/foggy/templates/ecommerce",
        "excluded_prefix": "demo/",
        **{key: str(value) for key, value in MYSQL57_CURATED_BUNDLE_COUNTS.items()},
        "manifest_sha256": sha256_file(manifest_path),
        "status": "verified",
    }
    if evidence != expected_evidence:
        reject(code, "MySQL curated ecommerce bundle evidence differs")


def validate_mysql_snapshot(
    path: Path,
    database: str,
    code: str = "E_MYSQL_SNAPSHOT",
) -> dict[str, Any]:
    ensure_regular(path, code, "MySQL fixture snapshot")
    if re.fullmatch(r"v934_[0-9a-f]{12}_mcp", database) is None:
        reject(code, f"MySQL snapshot database identity differs: {database!r}")
    content = path.read_bytes()
    if not content or b"\r" in content or not content.endswith(b"\n"):
        reject(code, "MySQL snapshot must use non-empty canonical LF records")
    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ContractError(code, "MySQL snapshot is not valid UTF-8") from error
    lines = text[:-1].split("\n")
    expected_rows = 2 + MYSQL57_TABLE_COUNT + len(MYSQL57_FIXTURE_METRICS)
    if len(lines) != expected_rows or any(not line for line in lines):
        reject(code, f"MySQL snapshot row count differs: {len(lines)}")
    rows = [line.split("\t") for line in lines]
    if any(len(row) != 3 for row in rows):
        reject(code, "MySQL snapshot rows must contain exactly three TSV fields")
    if rows[0] != ["identity", "database", database]:
        reject(code, "MySQL snapshot database row differs")

    table_rows = rows[1:1 + MYSQL57_TABLE_COUNT]
    if any(row[0] != "table" or row[2] != "present" for row in table_rows):
        reject(code, "MySQL snapshot table rows differ")
    table_names = [row[1] for row in table_rows]
    if (
        table_names != sorted(set(table_names))
        or len(table_names) != MYSQL57_TABLE_COUNT
        or not MYSQL57_REQUIRED_TABLES.issubset(table_names)
    ):
        reject(code, "MySQL snapshot table set is not exact sorted unique")
    table_set_sha256 = sha256_bytes(
        "".join(f"{name}\n" for name in table_names).encode("utf-8")
    )
    if table_set_sha256 != MYSQL57_TABLE_SET_SHA256:
        reject(code, "MySQL snapshot table-set SHA-256 differs")

    metric_rows = rows[1 + MYSQL57_TABLE_COUNT:-1]
    metric_names = [row[1] for row in metric_rows]
    if (
        any(row[0] != "metric" for row in metric_rows)
        or metric_names != list(MYSQL57_FIXTURE_METRICS)
    ):
        reject(code, "MySQL snapshot metric rows or order differ")
    metrics: dict[str, int] = {}
    for row in metric_rows:
        name, value = row[1], row[2]
        if re.fullmatch(r"0|[1-9][0-9]*", value) is None:
            reject(code, f"MySQL snapshot metric is not a canonical integer: {name}")
        metrics[name] = int(value)
    if metrics != MYSQL57_FIXTURE_METRICS:
        reject(code, f"MySQL snapshot exact metrics differ: {metrics}")
    if metrics["table_count"] != len(table_names):
        reject(code, "MySQL snapshot table metric and table rows differ")
    digest_row = rows[-1]
    if digest_row != ["digest", "content_sha256", MYSQL57_CONTENT_SHA256]:
        reject(code, "MySQL snapshot frozen content SHA-256 differs")
    return {
        "database": database,
        "table_names": table_names,
        "table_set_sha256": table_set_sha256,
        "metrics": metrics,
        "content_sha256": digest_row[2],
    }


def validate_mysql_grants(
    path: Path,
    database: str,
    code: str = "E_MYSQL_GRANTS",
) -> dict[str, str]:
    if re.fullmatch(r"v934_[0-9a-f]{12}_mcp", database) is None:
        reject(code, f"MySQL grants database identity differs: {database!r}")
    evidence = parse_env(
        path,
        {
            "cell", "principal", "database", "global_privileges",
            "schema_database", "schema_pattern", "schema_privileges",
            "schema_privilege_rows",
            "table_privilege_rows", "column_privilege_rows",
            "routine_privilege_rows", "proxy_privilege_rows", "status",
        },
        code,
    )
    expected = {
        "cell": "mysql57",
        "principal": "v934_runner@%",
        "database": database,
        "global_privileges": "USAGE:NO",
        "schema_database": database,
        "schema_pattern": database.replace("_", r"\_"),
        "schema_privileges": "SELECT:NO",
        "schema_privilege_rows": "1",
        "table_privilege_rows": "0",
        "column_privilege_rows": "0",
        "routine_privilege_rows": "0",
        "proxy_privilege_rows": "0",
        "status": "verified",
    }
    if evidence != expected:
        reject(code, "MySQL app grants are not exact SELECT-only")
    return evidence


def validate_mysql_direct_report(
    path: Path,
    code: str = "E_MYSQL_DIRECT_REPORT",
) -> dict[str, Any]:
    ensure_regular(path, code, "MySQL direct report")
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError(code, "MySQL direct report is not valid JSON") from error
    expected_path = ROOT / (
        "foggy-dataset-mcp/src/test/resources/ai-test-cases/ecommerce-tests.json"
    )
    ensure_regular(expected_path, code, "MySQL direct test-case source")
    try:
        expected_source = json.loads(expected_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ContractError(code, "MySQL direct test-case source is invalid") from error
    expected_cases = expected_source.get("testCases") if isinstance(expected_source, dict) else None
    if (
        not isinstance(expected_cases, list)
        or len(expected_cases) != MYSQL57_DIRECT_CASE_COUNT
        or any(not isinstance(case, dict) for case in expected_cases)
    ):
        reject(code, "MySQL direct test-case source differs")
    expected_ids = [case.get("id") for case in expected_cases]
    expected_tools = [case.get("expected_tool") for case in expected_cases]
    if (
        any(not isinstance(value, str) or not value for value in expected_ids)
        or len(set(expected_ids)) != MYSQL57_DIRECT_CASE_COUNT
        or any(not isinstance(value, str) or not value for value in expected_tools)
    ):
        reject(code, "MySQL direct test-case source IDs/tools differ")
    if not isinstance(report, dict):
        reject(code, "MySQL direct report root differs")
    exact_counts = {
        "resultCount": MYSQL57_DIRECT_CASE_COUNT,
        "passedCount": MYSQL57_DIRECT_CASE_COUNT,
        "failedCount": 0,
        "toolBusinessErrorCount": 0,
        "toolBusinessErrorCaseCount": 0,
        "warningCount": 0,
        "warningCaseCount": 0,
        "toolBusinessErrorWarningCount": 0,
    }
    if any(
        type(report.get(key)) is not int or report.get(key) != value
        for key, value in exact_counts.items()
    ):
        reject(code, "MySQL direct report totals differ")
    if (
        report.get("failureCategories") != {"success": MYSQL57_DIRECT_CASE_COUNT}
        or report.get("toolBusinessErrors") != []
        or report.get("warningCategories") != {}
        or report.get("warnings") != []
    ):
        reject(code, "MySQL direct report aggregate outcomes differ")
    parse_timestamp(report.get("generatedAt"), code)

    models = report.get("models")
    if not isinstance(models, list) or len(models) != 1 or not isinstance(models[0], dict):
        reject(code, "MySQL direct report model summary differs")
    model = models[0]
    model_counts = {
        "resultCount": MYSQL57_DIRECT_CASE_COUNT,
        "passedCount": MYSQL57_DIRECT_CASE_COUNT,
        "failedCount": 0,
        "clarifyCaseCount": 0,
        "toolBusinessErrorCaseCount": 0,
        "toolBusinessErrorCount": 0,
        "warningCaseCount": 0,
        "warningCount": 0,
        "toolBusinessErrorWarningCount": 0,
    }
    if (
        model.get("model") != "direct/tool-execution"
        or model.get("failureCategories") != {"success": MYSQL57_DIRECT_CASE_COUNT}
        or any(
            type(model.get(key)) is not int or model.get(key) != value
            for key, value in model_counts.items()
        )
        or isinstance(model.get("successRate"), bool)
        or not isinstance(model.get("successRate"), (int, float))
        or model.get("successRate") != 100.0
    ):
        reject(code, "MySQL direct report model aggregate differs")

    clarify = report.get("clarify")
    clarify_counts = {
        "caseCount", "observationCount", "domainCount", "riskTypeCount",
        "ownerRuleCount", "missingSlotCount",
    }
    clarify_lists = {"domains", "riskTypes", "ownerRules", "missingSlots", "observations"}
    if (
        not isinstance(clarify, dict)
        or any(type(clarify.get(key)) is not int or clarify.get(key) != 0 for key in clarify_counts)
        or any(clarify.get(key) != [] for key in clarify_lists)
    ):
        reject(code, "MySQL direct report clarify aggregate differs")

    cases = report.get("cases")
    if not isinstance(cases, list) or len(cases) != MYSQL57_DIRECT_CASE_COUNT:
        reject(code, "MySQL direct report case count differs")
    actual_ids = [case.get("testCaseId") for case in cases if isinstance(case, dict)]
    if (
        len(actual_ids) != MYSQL57_DIRECT_CASE_COUNT
        or actual_ids != expected_ids
    ):
        reject(code, "MySQL direct report case IDs/order differ")
    for case, expected_id, expected_tool in zip(
        cases, expected_ids, expected_tools, strict=True
    ):
        if not isinstance(case, dict):
            reject(code, f"MySQL direct report case is not an object: {expected_id}")
        query_payloads = case.get("queryPayloads")
        if (
            case.get("provider") != "direct"
            or case.get("modelName") != "tool-execution"
            or case.get("success") is not True
            or case.get("errorCategory") != "success"
            or case.get("errorMessage") is not None
            or case.get("calledTools") != [expected_tool]
            or type(case.get("durationMs")) is not int
            or case.get("durationMs") < 0
            or type(case.get("toolBusinessErrorCount")) is not int
            or case.get("toolBusinessErrorCount") != 0
            or case.get("toolBusinessErrors") != []
            or type(case.get("warningCount")) is not int
            or case.get("warningCount") != 0
            or case.get("warnings") != []
            or type(case.get("queryPayloadCount")) is not int
            or case.get("queryPayloadCount") < 0
            or not isinstance(query_payloads, list)
            or case.get("queryPayloadCount") != len(query_payloads)
        ):
            reject(code, f"MySQL direct report case result differs: {expected_id}")

    comparisons = report.get("caseComparison")
    comparison_ids = [
        row.get("testCaseId") for row in comparisons if isinstance(row, dict)
    ] if isinstance(comparisons, list) else []
    if comparison_ids != expected_ids:
        reject(code, "MySQL direct report comparison IDs/order differ")
    for comparison, expected_id, expected_tool in zip(
        comparisons, expected_ids, expected_tools, strict=True
    ):
        comparison_models = comparison.get("models")
        comparison_model = (
            comparison_models[0]
            if isinstance(comparison_models, list)
            and len(comparison_models) == 1
            and isinstance(comparison_models[0], dict)
            else None
        )
        if (
            type(comparison.get("resultCount")) is not int
            or comparison.get("resultCount") != 1
            or type(comparison.get("passedCount")) is not int
            or comparison.get("passedCount") != 1
            or type(comparison.get("failedCount")) is not int
            or comparison.get("failedCount") != 0
            or comparison.get("consensus") != "all_passed"
            or comparison_model is None
            or comparison_model.get("model") != "direct/tool-execution"
            or comparison_model.get("provider") != "direct"
            or comparison_model.get("modelName") != "tool-execution"
            or comparison_model.get("success") is not True
            or comparison_model.get("errorCategory") != "success"
            or comparison_model.get("errorMessage") is not None
            or comparison_model.get("calledTools") != [expected_tool]
            or type(comparison_model.get("toolBusinessErrorCount")) is not int
            or comparison_model.get("toolBusinessErrorCount") != 0
            or type(comparison_model.get("warningCount")) is not int
            or comparison_model.get("warningCount") != 0
        ):
            reject(code, f"MySQL direct report comparison differs: {expected_id}")
    return report


def verify_mysql_candidate(contract: dict[str, Any], candidate_path: Path) -> dict[str, Any]:
    ensure_regular(candidate_path, "E_CANDIDATE", "candidate manifest")
    if candidate_path.name != "candidate-manifest.json":
        reject("E_CANDIDATE", "candidate manifest name differs")
    root = candidate_path.parent
    candidate = load_json_manifest(candidate_path)
    if set(candidate) != CANDIDATE_MANIFEST_FIELDS:
        reject("E_CANDIDATE", "candidate manifest fields differ")
    outer = load_outer_marker(root / "run-context.json", contract)
    definition = candidate_definition("external-mysql")
    if (
        candidate.get("schema_version") != 1
        or candidate.get("kind") != definition["kind"]
        or candidate.get("run_id") != outer["run_id"]
        or candidate.get("runner") != "failsafe"
        or candidate.get("lane") != "external-mysql"
        or candidate.get("git_head") != outer["git_head"]
        or candidate.get("contract_sha256") != contract["_contract_sha256"]
        or candidate.get("outer_marker_sha256") != outer["_sha256"]
    ):
        reject("E_CANDIDATE", "candidate context differs")
    records = candidate.get("artifacts")
    if not isinstance(records, list) or not records:
        reject("E_CANDIDATE", "candidate artifacts are empty")
    paths = [record.get("path") for record in records if isinstance(record, dict)]
    if len(paths) != len(records) or paths != sorted(set(paths)):
        reject("E_CANDIDATE", "candidate artifact paths are not exact sorted unique")
    artifact_by_path: dict[str, Path] = {}
    for record in records:
        path = validate_artifact(root, record, "E_CANDIDATE")
        artifact_by_path[record["path"]] = path
    actual_files = {
        path.relative_to(root).as_posix()
        for path in root.rglob("*") if path.is_file() or path.is_symlink()
    }
    if actual_files != set(paths) | {"candidate-manifest.json"}:
        reject("E_CANDIDATE", "candidate run-root file set differs")
    missing = candidate_required_paths("external-mysql") - set(paths)
    if missing:
        reject("E_CANDIDATE", f"candidate required artifacts are missing: {sorted(missing)}")

    final_path = artifact_by_path["final/report-manifest.json"]
    final_manifest = verify_merged_manifest(contract, outer, final_path)
    expected_totals = definition["totals"]
    if (
        final_manifest.get("lane") != "external-mysql"
        or final_manifest.get("complete") is not False
        or final_manifest.get("totals") != expected_totals
        or candidate.get("totals") != expected_totals
        or candidate.get("report_manifest_sha256") != sha256_file(final_path)
    ):
        reject("E_CANDIDATE", "candidate report subset differs")

    summary = parse_env(
        artifact_by_path["summary.env"], CANDIDATE_SUMMARY_FIELDS, "E_CANDIDATE"
    )
    status = parse_env(
        artifact_by_path["run-status.env"], RUN_STATUS_FIELDS, "E_CANDIDATE"
    )
    expected_scalar = {
        "run_id": outer["run_id"], "runner": "failsafe", "lane": "external-mysql",
        "git_head": outer["git_head"], "variants": "3", "reports": "8",
        "testcase_nodes": "23", "failures": "0", "errors": "0", "skipped": "0",
        "outer_marker_sha256": outer["_sha256"],
        "contract_sha256": contract["_contract_sha256"],
        "negative_probes": "12/12", "sensitive_negative_probes": "6/6",
        "resource_residue": "0/0", "status": "passed",
    }
    if any(summary.get(key) != value for key, value in expected_scalar.items()):
        reject("E_CANDIDATE", "candidate summary identity differs")
    source_before = artifact_by_path["source-before.tsv"]
    source_after = artifact_by_path["source-after.tsv"]
    if source_before.read_bytes() != source_after.read_bytes():
        reject("E_CANDIDATE", "candidate source seal changed during execution")
    source_digest = source_manifest_digest(source_before)
    if summary["source_before"] != source_digest or summary["source_after"] != source_digest:
        reject("E_CANDIDATE", "candidate source summary differs")
    hash_bindings = {
        "final_report_manifest_sha256": "final/report-manifest.json",
        "run_status_sha256": "run-status.env",
        "resource_sha256": "cells/mysql57/resource.env",
        "fixture_sha256": "cells/mysql57/fixture.env",
        "cleanup_sha256": "cells/mysql57/cleanup.env",
        "negative_sha256": "negative/probes.tsv",
        "sensitive_negative_sha256": "negative/sensitive-probes.tsv",
        "sensitive_scan_sha256": "sensitive-scan.env",
    }
    if any(
        summary[key] != sha256_file(artifact_by_path[path])
        for key, path in hash_bindings.items()
    ):
        reject("E_CANDIDATE", "candidate summary artifact hash differs")
    deferred_sha = contract["bindings"]["deferred_inventory"]["sha256"]
    expected_status = {
        "run_id": outer["run_id"], "runner": "failsafe", "git_head": outer["git_head"],
        "last_phase": "completed", "exit_code": "0", "source_before_sha256": source_digest,
        "source_after_sha256": source_digest, "outer_marker_sha256": outer["_sha256"],
        "successor_manifest_sha256": deferred_sha,
        "final_report_manifest_sha256": sha256_file(final_path), "status": "passed",
    }
    if any(status.get(key) != value for key, value in expected_status.items()):
        reject("E_CANDIDATE", "candidate durable status differs")
    if parse_timestamp(status["finished_at"], "E_CANDIDATE") < parse_timestamp(
        status["started_at"], "E_CANDIDATE"
    ):
        reject("E_CANDIDATE", "candidate finish time predates start time")

    scope = hashlib.sha256(f"{outer['run_id']}|mysql57\n".encode()).hexdigest()[:12]
    container = f"v934ext-mysql57-{scope}"
    volume = f"{container}-data"
    database = f"v934_{scope}_mcp"
    resource_fields = {
        "run_id", "cell", "container", "image_ref", "image_id", "mapped_port",
        "mount_count", "mount_identity", "volume", "volume_created", "mysql_version",
        "server_process", "storage_engine", "character_set_server", "collation_server",
        "time_zone", "lower_case_table_names", "auth_mode", "app_user", "app_host",
        "app_schema_privilege", "credentials_distinct", "database",
        "initial_table_count", "initial_foreign_database_count", "status",
    }
    resource = parse_env(
        artifact_by_path["cells/mysql57/resource.env"], resource_fields, "E_CANDIDATE"
    )
    expected_resource = {
        "run_id": outer["run_id"], "cell": "mysql57", "container": container,
        "image_ref": MYSQL57_IMAGE_REF, "image_id": MYSQL57_IMAGE_ID,
        "mapped_port": resource["mapped_port"], "mount_count": "1",
        "mount_identity": f"{volume}|/var/lib/mysql|volume", "volume": volume,
        "volume_created": resource["volume_created"], "mysql_version": "5.7.44-log",
        "server_process": "mysqld", "storage_engine": "InnoDB",
        "character_set_server": "utf8mb4", "collation_server": "utf8mb4_unicode_ci",
        "time_zone": "+08:00", "lower_case_table_names": "1",
        "auth_mode": "distinct-ephemeral-root-app-passwords",
        "app_user": "v934_runner", "app_host": "%",
        "app_schema_privilege": "SELECT", "credentials_distinct": "true",
        "database": database,
        "initial_table_count": "0", "initial_foreign_database_count": "0",
        "status": "verified",
    }
    if (
        resource != expected_resource
        or re.fullmatch(r"127\.0\.0\.1:[0-9]+", resource["mapped_port"]) is None
    ):
        reject("E_CANDIDATE", "candidate MySQL resource identity differs")
    parse_timestamp(resource["volume_created"], "E_CANDIDATE")

    init_manifest_path = artifact_by_path["cells/mysql57/init-manifest.tsv"]
    validate_mysql_init_manifest(init_manifest_path)
    validate_mysql_curated_bundle(
        root / "cells/mysql57/ecommerce-bundle",
        artifact_by_path["cells/mysql57/bundle-manifest.tsv"],
        artifact_by_path["cells/mysql57/bundle.env"],
    )
    fixture_before = artifact_by_path["cells/mysql57/fixture-before.tsv"]
    fixture_after = artifact_by_path["cells/mysql57/fixture-after.tsv"]
    if fixture_before.read_bytes() != fixture_after.read_bytes():
        reject("E_CANDIDATE", "candidate MySQL fixture changed during execution")
    snapshot = validate_mysql_snapshot(fixture_before, database, "E_CANDIDATE")
    validate_mysql_snapshot(fixture_after, database, "E_CANDIDATE")
    grants_before = artifact_by_path["cells/mysql57/grants-before.env"]
    grants_after = artifact_by_path["cells/mysql57/grants-after.env"]
    if grants_before.read_bytes() != grants_after.read_bytes():
        reject("E_CANDIDATE", "candidate MySQL app grants changed during execution")
    validate_mysql_grants(grants_before, database, "E_CANDIDATE")
    validate_mysql_grants(grants_after, database, "E_CANDIDATE")
    fixture = parse_env(
        artifact_by_path["cells/mysql57/fixture.env"],
        {
            "cell", "database", "fixture_timestamp_epoch", "fixture_time_zone",
            "fixture_transaction_mode",
            "rand_seed1", "rand_seed2", "content_hash_format",
            "content_before_sha256", "content_after_sha256",
            "grants_before_sha256", "grants_after_sha256",
            "init_script_count", "init_manifest_sha256",
            "table_count", "primary_key_table_count", "table_set_sha256",
            "dim_date_count", "dim_product_count",
            "dim_customer_count", "dim_store_count", "fact_sales_count",
            "fact_order_count", "fact_return_count", "compose_join_count",
            "before_snapshot_sha256", "after_snapshot_sha256",
            "foreign_database_count", "status",
        },
        "E_CANDIDATE",
    )
    metrics = snapshot["metrics"]
    exact_fixture = {
        "cell": "mysql57", "database": database,
        "fixture_timestamp_epoch": "1710864000", "fixture_time_zone": "+08:00",
        "fixture_transaction_mode": "single-session-commit",
        "rand_seed1": "934", "rand_seed2": "934", "init_script_count": "10",
        "content_hash_format": "mysqldump-data-v1",
        "content_before_sha256": snapshot["content_sha256"],
        "content_after_sha256": snapshot["content_sha256"],
        "grants_before_sha256": sha256_file(grants_before),
        "grants_after_sha256": sha256_file(grants_after),
        "init_manifest_sha256": sha256_file(init_manifest_path),
        "table_count": str(metrics["table_count"]),
        "primary_key_table_count": str(metrics["primary_key_table_count"]),
        "table_set_sha256": snapshot["table_set_sha256"],
        "dim_date_count": str(metrics["dim_date_count"]),
        "dim_product_count": str(metrics["dim_product_count"]),
        "dim_customer_count": str(metrics["dim_customer_count"]),
        "dim_store_count": str(metrics["dim_store_count"]),
        "fact_sales_count": str(metrics["fact_sales_count"]),
        "fact_order_count": str(metrics["fact_order_count"]),
        "fact_return_count": str(metrics["fact_return_count"]),
        "compose_join_count": str(metrics["compose_join_count"]),
        "before_snapshot_sha256": sha256_file(fixture_before),
        "after_snapshot_sha256": sha256_file(fixture_after),
        "foreign_database_count": str(metrics["foreign_database_count"]),
        "status": "verified",
    }
    if fixture != exact_fixture:
        reject("E_CANDIDATE", "candidate MySQL fixture identity differs")
    if (
        fixture["before_snapshot_sha256"] != fixture["after_snapshot_sha256"]
        or fixture["content_before_sha256"] != fixture["content_after_sha256"]
        or fixture["grants_before_sha256"] != fixture["grants_after_sha256"]
    ):
        reject("E_CANDIDATE", "candidate MySQL fixture or grants hashes differ")

    cleanup = parse_env(
        artifact_by_path["cells/mysql57/cleanup.env"],
        {"cell", "container", "volume", "container_residue", "volume_residue", "status"},
        "E_CANDIDATE",
    )
    if cleanup != {
        "cell": "mysql57", "container": container, "volume": volume,
        "container_residue": "0", "volume_residue": "0", "status": "passed",
    }:
        reject("E_CANDIDATE", "candidate MySQL cleanup differs")
    preclean = parse_env(
        artifact_by_path["preclean.env"],
        {"modules", "root_target_preserved", "status"}, "E_CANDIDATE",
    )
    if preclean != {
        "modules": ",".join(MYSQL_CLEAN_MODULES),
        "root_target_preserved": "true", "status": "passed",
    }:
        reject("E_CANDIDATE", "candidate preclean evidence differs")
    validate_negative_evidence(artifact_by_path["negative/probes.tsv"], contract)
    validate_sensitive_negative_evidence(
        artifact_by_path["negative/sensitive-probes.tsv"], "external-mysql"
    )
    sensitive = parse_env(
        artifact_by_path["sensitive-scan.env"],
        {"patterns", "ephemeral_secrets", "status"},
        "E_CANDIDATE",
    )
    if sensitive != {"patterns": "5", "ephemeral_secrets": "2", "status": "passed"}:
        reject("E_CANDIDATE", "candidate sensitive scan evidence differs")
    validate_mysql_direct_report(
        artifact_by_path["variants/mysql57-direct/direct-report.json"], "E_CANDIDATE"
    )
    bytecode_paths = [
        "variants/mysql57-mcp/bytecode.tsv",
        "variants/mysql57-direct/bytecode.tsv",
        "variants/mysql57-compose/bytecode.tsv",
    ]
    for path in bytecode_paths:
        validate_bytecode_seal(artifact_by_path[path], "external-mysql")
    if len({artifact_by_path[path].read_bytes() for path in bytecode_paths}) != 1:
        reject("E_CANDIDATE", "candidate bytecode changed between MySQL variants")
    return candidate


def verify_candidate(contract: dict[str, Any], candidate_path: Path) -> dict[str, Any]:
    candidate = load_json_manifest(candidate_path)
    lane = candidate.get("lane")
    if lane == "external-redis":
        return verify_redis_candidate(contract, candidate_path)
    if lane == "external-mongo":
        return verify_mongo_candidate(contract, candidate_path)
    if lane == "external-mysql":
        return verify_mysql_candidate(contract, candidate_path)
    reject("E_CANDIDATE", f"unsupported candidate lane: {lane}")


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser()
    commands = root.add_subparsers(dest="command", required=True)
    commands.add_parser("validate")

    collect = commands.add_parser("collect")
    collect.add_argument("--variant", required=True)
    collect.add_argument("--outer-marker", required=True)
    collect.add_argument("--run-marker", required=True)
    collect.add_argument("--report-root", action="append", required=True)
    collect.add_argument("--output", required=True)

    verify = commands.add_parser("verify-manifest")
    verify.add_argument("--outer-marker", required=True)
    verify.add_argument("--manifest", required=True)

    subset = commands.add_parser("merge-subset")
    subset.add_argument("--lane", required=True)
    subset.add_argument("--outer-marker", required=True)
    subset.add_argument("--manifest", action="append", required=True)
    subset.add_argument("--output", required=True)

    finalize = commands.add_parser("finalize")
    finalize.add_argument("--outer-marker", required=True)
    finalize.add_argument("--manifest", action="append", required=True)
    finalize.add_argument("--output", required=True)

    negative = commands.add_parser("negative")
    negative.add_argument("--output", required=True)

    source_seal = commands.add_parser("seal-source")
    source_seal.add_argument("--lane", required=True)
    source_seal.add_argument("--output", required=True)

    bytecode_seal = commands.add_parser("seal-bytecode")
    bytecode_seal.add_argument("--lane", required=True)
    bytecode_seal.add_argument("--output", required=True)

    candidate = commands.add_parser("create-candidate")
    candidate.add_argument("--outer-marker", required=True)
    candidate.add_argument("--run-root", required=True)
    candidate.add_argument("--output", required=True)

    candidate_verify = commands.add_parser("verify-candidate")
    candidate_verify.add_argument("--candidate", required=True)

    mysql_snapshot = commands.add_parser("verify-mysql-snapshot")
    mysql_snapshot.add_argument("--snapshot", required=True)
    mysql_snapshot.add_argument("--database", required=True)

    mysql_direct = commands.add_parser("verify-mysql-direct-report")
    mysql_direct.add_argument("--report", required=True)
    return root


def main() -> int:
    try:
        args = parser().parse_args()
        if args.command == "verify-mysql-snapshot":
            snapshot = validate_mysql_snapshot(Path(args.snapshot), args.database)
            metrics = snapshot["metrics"]
            print(
                "V934_EXTERNAL_MYSQL_SNAPSHOT "
                f"database={snapshot['database']} tables={metrics['table_count']} "
                f"table_set_sha256={snapshot['table_set_sha256']} "
                f"fact_sales={metrics['fact_sales_count']} "
                f"fact_return={metrics['fact_return_count']} status=verified"
            )
            return 0
        if args.command == "verify-mysql-direct-report":
            report = validate_mysql_direct_report(Path(args.report))
            print(
                "V934_EXTERNAL_MYSQL_DIRECT_REPORT "
                f"results={report['resultCount']} passed={report['passedCount']} "
                "failed=0 tool_business_errors=0 status=verified"
            )
            return 0
        contract = load_contract()
        if args.command == "validate":
            print("V934_EXTERNAL_CONTRACT variants=7 reports=16 testcase_nodes=76 optional=1")
        elif args.command == "collect":
            path = collect_variant(
                contract,
                args.variant,
                Path(args.outer_marker),
                Path(args.run_marker),
                args.report_root,
                Path(args.output),
            )
            print(f"V934_EXTERNAL_COLLECT variant={args.variant} manifest={path}")
        elif args.command == "verify-manifest":
            manifest = verify_any(contract, Path(args.outer_marker), Path(args.manifest))
            print(
                "V934_EXTERNAL_VERIFY "
                f"kind={manifest['kind']} reports={manifest['totals']['reports']} "
                f"testcase_nodes={manifest['totals']['testcase_nodes']}"
            )
        elif args.command == "merge-subset":
            path = merge_manifests(
                contract,
                Path(args.outer_marker),
                [Path(value) for value in args.manifest],
                Path(args.output),
                args.lane,
            )
            print(f"V934_EXTERNAL_SUBSET lane={args.lane} manifest={path}")
        elif args.command == "finalize":
            path = merge_manifests(
                contract,
                Path(args.outer_marker),
                [Path(value) for value in args.manifest],
                Path(args.output),
                None,
            )
            print(f"V934_EXTERNAL_FINAL manifest={path}")
        elif args.command == "negative":
            run_negative_probes(contract, Path(args.output))
            print("V934_EXTERNAL_NEGATIVE passed=12 total=12")
        elif args.command == "seal-source":
            digest = create_source_seal(args.lane, Path(args.output))
            print(f"V934_EXTERNAL_SOURCE_SEAL lane={args.lane} sha256={digest}")
        elif args.command == "seal-bytecode":
            create_bytecode_seal(args.lane, Path(args.output))
            print(f"V934_EXTERNAL_BYTECODE_SEAL lane={args.lane} output={args.output}")
        elif args.command == "create-candidate":
            path = create_candidate(
                contract,
                Path(args.outer_marker),
                Path(args.run_root),
                Path(args.output),
            )
            print(f"V934_EXTERNAL_CANDIDATE created={path}")
        elif args.command == "verify-candidate":
            candidate = verify_candidate(contract, Path(args.candidate))
            print(
                "V934_EXTERNAL_CANDIDATE_VERIFY "
                f"run={candidate['run_id']} reports={candidate['totals']['reports']} "
                f"testcase_nodes={candidate['totals']['testcase_nodes']}"
            )
    except (ContractError, OSError, subprocess.SubprocessError) as error:
        if isinstance(error, ContractError):
            print(f"[{error.code}] {error.message}", file=sys.stderr)
        else:
            print(f"[E_RUNTIME] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
