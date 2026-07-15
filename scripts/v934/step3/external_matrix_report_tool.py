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
    "external_redis_signal_probe",
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


def validate_sensitive_negative_evidence(path: Path) -> None:
    expected = {
        "redis-env", "json-password", "api-key", "bearer",
        "redis-uri", "cli-password",
    }
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


def validate_bytecode_seal(path: Path) -> None:
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
    if main_modules != set(REDIS_CLEAN_MODULES):
        reject("E_CANDIDATE", "bytecode seal main module set differs")
    if not any(
        row.get("module") == "addons/foggy-dataset-model-cache"
        and row.get("tree") == "test-classes"
        for row in rows
    ):
        reject("E_CANDIDATE", "cache test bytecode seal is missing")
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


def candidate_required_paths() -> set[str]:
    return {
        "run-context.json", "run-status.env", "summary.env", "preclean.env",
        "source-before.tsv", "source-after.tsv", "run.log",
        "cells/redis7/resource.env", "cells/redis7/fixture.env",
        "cells/redis7/cleanup.env", "negative/probes.tsv",
        "negative/sensitive-probes.tsv", "sensitive-scan.env",
        "variants/redis7/bytecode.tsv", "variants/redis7-sqlite/bytecode.tsv",
        "final/report-manifest.json",
    }


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
    if (
        final_manifest["kind"] != "v934-step3-external-matrix-subset"
        or final_manifest["lane"] != "external-redis"
        or final_manifest["complete"] is not False
        or final_manifest["totals"]
        != {"variants": 2, "reports": 2, "testcase_nodes": 3,
            "failures": 0, "errors": 0, "skipped": 0}
    ):
        reject("E_CANDIDATE", "Redis final subset identity differs")
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
    missing = candidate_required_paths() - relative_files
    if missing:
        reject("E_CANDIDATE", f"candidate required files are missing: {sorted(missing)}")
    records = [
        artifact(path, path.relative_to(run_root).as_posix()) for path in files
    ]
    candidate = {
        "schema_version": 1,
        "kind": "v934-step3-external-redis-candidate",
        "run_id": outer["run_id"],
        "runner": "failsafe",
        "lane": "external-redis",
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


def verify_candidate(contract: dict[str, Any], candidate_path: Path) -> dict[str, Any]:
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
    missing = candidate_required_paths() - set(paths)
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
    validate_sensitive_negative_evidence(artifact_by_path["negative/sensitive-probes.tsv"])
    sensitive = parse_env(
        artifact_by_path["sensitive-scan.env"], {"patterns", "status"}, "E_CANDIDATE"
    )
    if sensitive != {"patterns": "5", "status": "passed"}:
        reject("E_CANDIDATE", "candidate sensitive scan evidence differs")
    validate_bytecode_seal(artifact_by_path["variants/redis7/bytecode.tsv"])
    validate_bytecode_seal(artifact_by_path["variants/redis7-sqlite/bytecode.tsv"])
    if (
        artifact_by_path["variants/redis7/bytecode.tsv"].read_bytes()
        != artifact_by_path["variants/redis7-sqlite/bytecode.tsv"].read_bytes()
    ):
        reject("E_CANDIDATE", "candidate bytecode changed between Redis variants")
    return candidate


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

    candidate = commands.add_parser("create-candidate")
    candidate.add_argument("--outer-marker", required=True)
    candidate.add_argument("--run-root", required=True)
    candidate.add_argument("--output", required=True)

    candidate_verify = commands.add_parser("verify-candidate")
    candidate_verify.add_argument("--candidate", required=True)
    return root


def main() -> int:
    try:
        args = parser().parse_args()
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
