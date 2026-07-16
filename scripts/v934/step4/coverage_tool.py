#!/usr/bin/env python3
"""Fail-closed validation for the 9.3.4 Step 4 coverage bootstrap.

The validator intentionally uses only the Python standard library.  Its default
inputs are the versioned Step 4 contract files; individual mutable inputs may be
overridden on the command line so negative probes can operate on copies without
touching the canonical files.
"""

from __future__ import annotations

import argparse
import csv
import fcntl
import hashlib
import io
import json
import os
import re
import signal
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path, PurePosixPath
from typing import Any, Iterable


MAVEN_NS = "http://maven.apache.org/POM/4.0.0"
NS = {"m": MAVEN_NS}
REPORTER_MODULE = "build-support/foggy-coverage-report"
REPORTER_ARTIFACT = "foggy-coverage-report"

EXPECTED_PARENT_LINKS: dict[str, dict[str, Any]] = {
    "step1_coverage_policy": {
        "path": "scripts/v934/coverage-thresholds.json",
        "sha256": "45058f63b71558e4660f60e0cfda9a8a490fa8f96b532c6656c3d726eaad44fb",
    },
    "step1_hash_manifest": {
        "path": "scripts/v934/SHA256SUMS",
        "sha256": "e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f",
    },
    "step2_successor_hash_manifest": {
        "path": "scripts/v934/successor/step2/SHA256SUMS",
        "sha256": "4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919",
    },
    "step2_successor_freeze": {
        "path": "scripts/v934/successor/step2/contract-freeze.json",
        "sha256": "44b11ed756bf41e3b271ac57b59c2c882a0b31a56963f42ae154fdb5d37b2fb6",
    },
    "step3_required_contract": {
        "path": "scripts/v934/step3/step3-required-contract.json",
        "sha256": "f2bd52df7ed2829051ad263f97d560d3f8babe048d25864edb725eb671ba4d1b",
    },
    "step3_required_final_manifest": {
        "run_id": "step3-required-20260716-final-r4",
        "tested_commit": "ce3d70c391c7b8bd8046fe66dde0ad568d66601e",
        "sha256": "9040bff263101ed7ad33dbc4681bbf37f48e39b00957d2bf8224fb506afa5282",
    },
    "step3_required_candidate_manifest": {
        "run_id": "step3-required-20260716-final-r4",
        "tested_commit": "ce3d70c391c7b8bd8046fe66dde0ad568d66601e",
        "sha256": "6b1e5f3502dd2e666e5546ecf3c6469e9b5060bdafa8e7d4e1068fd41688cfa4",
    },
}

EXPECTED_LEDGER_SHA256 = (
    "10ddf85daa0426d530bec3ccd9bb1a10446aa426d920c6c5c433163455552711"
)
EXPECTED_STEP4_MANIFEST_PATHS = (
    "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/dataset/db/model/cache/provider/RedisCrossJvmCacheIT.java",
    "build-support/foggy-coverage-report/pom.xml",
    "foggy-dataset-model/pom.xml",
    "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationDataValidationTest.java",
    "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java",
    "pom.xml",
    "scripts/v934/authority_runner_lib.sh",
    "scripts/v934/step4/JaCoCoExecInspector.java",
    "scripts/v934/step4/authority_parent_lib.sh",
    "scripts/v934/step4/authority_parent_negative_test.sh",
    "scripts/v934/step4/coverage-contract.json",
    "scripts/v934/step4/coverage-exec-ledger.tsv",
    "scripts/v934/step4/coverage-report-amendment.tsv",
    "scripts/v934/step4/coverage-thresholds.json",
    "scripts/v934/step4/coverage_contract_negative_tool.py",
    "scripts/v934/step4/coverage_exec_tool.py",
    "scripts/v934/step4/coverage_report_runner.sh",
    "scripts/v934/step4/coverage_runner_lib.sh",
    "scripts/v934/step4/coverage_tool.py",
    "scripts/v934/step4/coverage_xml_tool.py",
    "scripts/v934/step4/report_inventory_tool.py",
    "scripts/v934/step4/reporter_effective_pom_tool.py",
    "scripts/v934/step4/step2-report-view-contract.json",
    "scripts/v934/step4/step2_report_view_tool.py",
    "scripts/v934/step4/successor/SHA256SUMS",
    "scripts/v934/step4/successor/database-authority-SHA256SUMS",
    "scripts/v934/step4/successor/database-matrix-contract.json",
    "scripts/v934/step4/successor/database-matrix-protected-trees.tsv",
    "scripts/v934/step4/successor/database-matrix-source-amendment.tsv",
    "scripts/v934/step4/successor/database_matrix_report_tool.py",
    "scripts/v934/step4/successor/declared-amendments.tsv",
    "scripts/v934/step4/successor/external-matrix-contract.json",
    "scripts/v934/step4/successor/external_matrix_report_tool.py",
    "scripts/v934/step4/successor/overlay-contract.json",
    "scripts/v934/step4/successor/overlay_tool.py",
    "scripts/v934/step4/successor/preagg-addon-lifecycle-contract.json",
    "scripts/v934/step4/successor/step3-required-contract.json",
    "scripts/v934/step4/toolchain_receipt_tool.py",
    "scripts/verify-v934-database-matrix.sh",
    "scripts/verify-v934-external-matrix.sh",
    "scripts/verify-v934-external-mongo.sh",
    "scripts/verify-v934-external-mysql.sh",
    "scripts/verify-v934-external-redis.sh",
    "scripts/verify-v934-external-vector.sh",
    "scripts/verify-v934-integration.sh",
    "scripts/verify-v934-preagg-addon-lifecycle.sh",
    "scripts/verify-v934-step3-required-matrix.sh",
    "scripts/verify-v934-step4-coverage.sh",
    "scripts/verify-v934-unit.sh",
)
LEDGER_HEADER = (
    "exec_file",
    "runner",
    "lane",
    "variant_key",
    "expected_session_count",
    "expected_session_owners",
    "required",
    "disposition",
)

# The checksum above freezes the bytes; these signatures independently freeze
# the semantic identities and make corruption failures useful to operators.
EXPECTED_LEDGER_ROWS: dict[str, tuple[str, str, str, int, str, str]] = {
    "jacoco-ut.exec": (
        "surefire",
        "unit",
        "unit",
        21,
        "foggy-chart-storage-cloud,foggy-data-viewer,foggy-dataset-client,foggy-dataset-graphql,foggy-dataset-model-cache,foggy-dataset-model-mongo,foggy-dataset-model-preagg,foggy-dataset-model-vector,foggy-dataset-mongo,foggy-dataset-vector,foggy-fsscript-client,foggy-bean-copy,foggy-core,foggy-dataset,foggy-dataset-mcp,foggy-dataset-memory-grid-bridge,foggy-dataset-memory-grid-duckdb,foggy-dataset-model,foggy-fsscript,foggy-mcp-launcher,foggy-runtime-api",
        "all-reactor-unit",
    ),
    "jacoco-it-caffeine-sqlite.exec": ("failsafe", "integration", "caffeine-sqlite", 1, "foggy-dataset-model-cache", "step2-required"),
    "jacoco-it-hermetic.exec": ("failsafe", "integration", "hermetic", 1, "foggy-dataset-model", "step2-required"),
    "jacoco-it-sqlite-broad.exec": ("failsafe", "integration", "sqlite-broad", 2, "foggy-dataset-model-cache,foggy-dataset-model", "step2-required"),
    "jacoco-it-sqlite-harness.exec": ("failsafe", "integration", "sqlite-harness", 1, "foggy-dataset-model", "step2-required"),
    "jacoco-it-sqlite-lifecycle.exec": ("failsafe", "integration", "sqlite-lifecycle", 1, "foggy-dataset-model", "step2-required"),
    "jacoco-it-sqlite-refresh.exec": ("failsafe", "integration", "sqlite-refresh", 1, "foggy-dataset-model", "step2-required"),
    "jacoco-it-db-sqlite.exec": ("failsafe", "database", "db-sqlite", 1, "foggy-dataset-model", "step3-required"),
    "jacoco-it-db-mysql57.exec": ("failsafe", "database", "db-mysql57", 1, "foggy-dataset-model", "step3-required"),
    "jacoco-it-db-mysql8.exec": ("failsafe", "database", "db-mysql8", 1, "foggy-dataset-model", "step3-required"),
    "jacoco-it-mysql8-targeted.exec": ("failsafe", "database", "mysql8-targeted", 1, "foggy-dataset-model", "step3-required"),
    "jacoco-it-db-postgres15.exec": ("failsafe", "database", "db-postgres15", 1, "foggy-dataset-model", "step3-required"),
    "jacoco-it-postgres15-targeted.exec": ("failsafe", "database", "postgres15-targeted", 1, "foggy-dataset-model", "step3-required"),
    "jacoco-it-db-sqlserver2022.exec": ("failsafe", "database", "db-sqlserver2022", 1, "foggy-dataset-model", "step3-required"),
    "jacoco-it-redis7.exec": ("failsafe", "external", "redis7", 3, "foggy-dataset-model-cache,child-write,child-restart", "step3-required-cross-jvm"),
    "jacoco-it-redis7-sqlite.exec": ("failsafe", "external", "redis7-sqlite", 1, "foggy-dataset-model-cache", "step3-required"),
    "jacoco-it-mongo6.exec": ("failsafe", "external", "mongo6", 2, "foggy-data-viewer,foggy-dataset-model-mongo", "step3-required"),
    "jacoco-it-mysql57-mcp.exec": ("failsafe", "external", "mysql57-mcp", 1, "foggy-dataset-mcp", "step3-required"),
    "jacoco-it-mysql57-direct.exec": ("failsafe", "external", "mysql57-direct", 1, "foggy-dataset-mcp", "step3-required"),
    "jacoco-it-mysql57-compose.exec": ("failsafe", "external", "mysql57-compose", 1, "foggy-dataset-mcp", "step3-required"),
    "jacoco-it-milvus24-embedding.exec": ("failsafe", "external", "milvus24-embedding", 2, "foggy-dataset-model-vector,foggy-dataset-vector", "step3-required"),
    "jacoco-it-preagg-addon-sqlite.exec": ("failsafe", "addon", "preagg-addon-sqlite", 1, "foggy-dataset-model-preagg", "step3-required-companion"),
    "jacoco-it-preagg-addon-mysql57.exec": ("failsafe", "addon", "preagg-addon-mysql57", 1, "foggy-dataset-model-preagg", "step3-required-companion"),
}


class ContractError(RuntimeError):
    """A deterministic contract validation failure."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def require_exact_keys(value: Any, expected: Iterable[str], label: str) -> dict[str, Any]:
    require(type(value) is dict, f"{label}: expected object")
    expected_set = set(expected)
    actual_set = set(value)
    require(actual_set == expected_set, f"{label}: keys must be {sorted(expected_set)}")
    return value


def reject_json_constant(value: str) -> None:
    raise ContractError(f"JSON: non-finite number {value!r} is forbidden")


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ContractError(f"JSON: duplicate key {key!r}")
        result[key] = value
    return result


def load_json(path: Path, label: str) -> dict[str, Any]:
    require(path.is_file(), f"{label}: missing regular file")
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ContractError(f"{label}: cannot read UTF-8 JSON ({exc.__class__.__name__})") from exc
    try:
        value = json.loads(
            text,
            object_pairs_hook=unique_object,
            parse_constant=reject_json_constant,
        )
    except ContractError:
        raise
    except (json.JSONDecodeError, ValueError) as exc:
        raise ContractError(f"{label}: invalid JSON") from exc
    require(type(value) is dict, f"{label}: top level must be an object")
    return value


def sha256_file(path: Path, label: str) -> str:
    require(path.is_file(), f"{label}: missing regular file")
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise ContractError(f"{label}: cannot hash file ({exc.__class__.__name__})") from exc
    return digest.hexdigest()


def safe_repo_path(repo_root: Path, relative: str, label: str) -> Path:
    require(type(relative) is str and relative != "", f"{label}: path must be non-empty string")
    pure = PurePosixPath(relative)
    require(not pure.is_absolute() and ".." not in pure.parts, f"{label}: unsafe repository path")
    require("\\" not in relative, f"{label}: path must use POSIX separators")
    path = repo_root.joinpath(*pure.parts)
    try:
        path.resolve(strict=False).relative_to(repo_root)
    except (OSError, ValueError) as exc:
        raise ContractError(f"{label}: path escapes repository") from exc
    require(not path.is_symlink(), f"{label}: symlink is forbidden")
    return path


def validate_sha_manifest(path: Path, label: str) -> int:
    require(path.is_file() and not path.is_symlink(), f"{label}: missing regular non-symlink file")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as exc:
        raise ContractError(f"{label}: cannot read manifest ({exc.__class__.__name__})") from exc
    require(lines, f"{label}: empty manifest")
    seen: set[str] = set()
    for number, line in enumerate(lines, 1):
        match = re.fullmatch(r"([0-9a-f]{64})  ([^\r\n]+)", line)
        require(match is not None, f"{label}: malformed row {number}")
        expected_hash, relative = match.groups()
        require(relative not in seen, f"{label}: duplicate path {relative!r}")
        seen.add(relative)
        pure = PurePosixPath(relative)
        require(not pure.is_absolute() and ".." not in pure.parts and "\\" not in relative, f"{label}: unsafe path at row {number}")
        target = path.parent.joinpath(*pure.parts)
        try:
            target.resolve(strict=False).relative_to(path.parent.resolve())
        except (OSError, ValueError) as exc:
            raise ContractError(f"{label}: path escapes manifest directory at row {number}") from exc
        require(not target.is_symlink(), f"{label}: symlink target at row {number}")
        actual_hash = sha256_file(target, f"{label} row {number}")
        require(actual_hash == expected_hash, f"{label}: hash mismatch for {relative!r}")
    return len(lines)


def validate_step4_manifest(repo_root: Path, path: Path) -> int:
    label = "Step 4 diagnostic tooling manifest"
    require(path.is_file() and not path.is_symlink(), f"{label}: missing regular non-symlink file")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as exc:
        raise ContractError(f"{label}: cannot read manifest ({exc.__class__.__name__})") from exc
    require(len(lines) == len(EXPECTED_STEP4_MANIFEST_PATHS), f"{label}: expected exact {len(EXPECTED_STEP4_MANIFEST_PATHS)} rows")
    seen: set[str] = set()
    actual_paths: list[str] = []
    for number, line in enumerate(lines, 1):
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._/-]+)", line)
        require(match is not None, f"{label}: malformed row {number}")
        expected_hash, relative = match.groups()
        require(relative not in seen, f"{label}: duplicate path {relative!r}")
        seen.add(relative)
        actual_paths.append(relative)
        target = safe_repo_path(repo_root, relative, f"{label} row {number}")
        require(target.is_file(), f"{label}: non-file target at row {number}")
        require(
            sha256_file(target, f"{label} row {number}") == expected_hash,
            f"{label}: hash mismatch for {relative!r}",
        )
    require(
        actual_paths == list(EXPECTED_STEP4_MANIFEST_PATHS),
        f"{label}: path order/set differs",
    )
    return len(lines)


def parse_xml(path: Path, label: str) -> ET.Element:
    require(path.is_file(), f"{label}: missing regular file")
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        raise ContractError(f"{label}: invalid XML") from exc
    require(root.tag == f"{{{MAVEN_NS}}}project", f"{label}: unexpected root element")
    return root


def text_of(element: ET.Element | None, label: str) -> str:
    require(element is not None, f"{label}: missing element")
    value = (element.text or "").strip()
    require(value != "", f"{label}: empty element")
    return value


def only(elements: list[ET.Element], label: str) -> ET.Element:
    require(len(elements) == 1, f"{label}: expected exactly one element, found {len(elements)}")
    return elements[0]


def child_text(parent: ET.Element, name: str, label: str) -> str:
    return text_of(only(parent.findall(f"m:{name}", NS), f"{label}.{name}"), f"{label}.{name}")


def require_child_names(parent: ET.Element, expected: tuple[str, ...], label: str) -> None:
    actual: list[str] = []
    prefix = f"{{{MAVEN_NS}}}"
    for child in parent:
        require(child.tag.startswith(prefix), f"{label}: unexpected non-Maven child {child.tag!r}")
        actual.append(child.tag[len(prefix):])
    require(actual == list(expected), f"{label}: child sequence must be {list(expected)}, found {actual}")


def find_plugin(container: ET.Element, artifact_id: str, label: str) -> ET.Element:
    matches = []
    for plugin in container.findall("m:plugin", NS):
        artifact = plugin.find("m:artifactId", NS)
        if artifact is not None and (artifact.text or "").strip() == artifact_id:
            matches.append(plugin)
    return only(matches, f"{label} plugin {artifact_id}")


def validate_contract_json(contract: dict[str, Any]) -> None:
    require_exact_keys(
        contract,
        (
            "schema_version",
            "kind",
            "status",
            "tooling_manifest",
            "parent_links",
            "jacoco",
            "toolchain_receipt",
            "execution_ledger",
            "report_inventory",
            "run_layout",
            "reporter",
            "threshold_successor",
        ),
        "coverage contract",
    )
    require(type(contract["schema_version"]) is int and contract["schema_version"] == 1, "coverage contract.schema_version: expected integer 1")
    require(contract["kind"] == "v934-step4-coverage-contract", "coverage contract.kind: unexpected value")
    require(contract["status"] == "diagnostic-ready", "coverage contract.status: expected diagnostic-ready")

    tooling_manifest = require_exact_keys(
        contract["tooling_manifest"],
        ("path", "path_semantics", "required_entries", "publication_status"),
        "coverage contract.tooling_manifest",
    )
    require(
        tooling_manifest
        == {
            "path": "scripts/v934/step4/SHA256SUMS",
            "path_semantics": "repository-relative-posix",
            "required_entries": len(EXPECTED_STEP4_MANIFEST_PATHS),
            "publication_status": "diagnostic-ready",
        },
        "coverage contract.tooling_manifest: frozen values changed",
    )

    parent_links = require_exact_keys(contract["parent_links"], EXPECTED_PARENT_LINKS, "coverage contract.parent_links")
    require(parent_links == EXPECTED_PARENT_LINKS, "coverage contract.parent_links: frozen values changed")

    jacoco = require_exact_keys(
        contract["jacoco"],
        ("version", "unit_property", "integration_property", "append", "reactor_parallelism", "shared_exec_fork_count"),
        "coverage contract.jacoco",
    )
    require(jacoco["version"] == "0.8.12", "coverage contract.jacoco.version: expected 0.8.12")
    require(jacoco["unit_property"] == "jacoco.ut.argLine", "coverage contract.jacoco.unit_property: unexpected value")
    require(jacoco["integration_property"] == "jacoco.it.argLine", "coverage contract.jacoco.integration_property: unexpected value")
    require(jacoco["unit_property"] != jacoco["integration_property"], "coverage contract.jacoco: unit and integration properties must differ")
    require(type(jacoco["append"]) is bool and jacoco["append"] is True, "coverage contract.jacoco.append: expected boolean true")
    require(jacoco["reactor_parallelism"] == "forbidden", "coverage contract.jacoco.reactor_parallelism: expected forbidden")
    require(type(jacoco["shared_exec_fork_count"]) is int and jacoco["shared_exec_fork_count"] == 1, "coverage contract.jacoco.shared_exec_fork_count: expected integer 1")

    toolchain = require_exact_keys(
        contract["toolchain_receipt"],
        (
            "validator",
            "run_path",
            "replay_stages",
            "compiler_asm",
            "jacoco_reporter_asm",
            "test_classpath_asm_guard",
        ),
        "coverage contract.toolchain_receipt",
    )
    require(
        toolchain
        == {
            "validator": "scripts/v934/step4/toolchain_receipt_tool.py",
            "run_path": "toolchain-receipt.json",
            "replay_stages": [
                "pre-compile-seal",
                "post-children",
                "post-reporter",
                "post-model",
            ],
            "compiler_asm": "9.6",
            "jacoco_reporter_asm": "9.7",
            "test_classpath_asm_guard": "9.7.1",
        },
        "coverage contract.toolchain_receipt: frozen values changed",
    )

    ledger = require_exact_keys(
        contract["execution_ledger"],
        ("path", "sha256", "exec_files", "expected_sessions", "optional_llm"),
        "coverage contract.execution_ledger",
    )
    require(ledger == {
        "path": "scripts/v934/step4/coverage-exec-ledger.tsv",
        "sha256": EXPECTED_LEDGER_SHA256,
        "exec_files": 23,
        "expected_sessions": 48,
        "optional_llm": "reviewed-optional-excluded",
    }, "coverage contract.execution_ledger: frozen values changed")
    require(type(ledger["exec_files"]) is int, "coverage contract.execution_ledger.exec_files: expected integer")
    require(type(ledger["expected_sessions"]) is int, "coverage contract.execution_ledger.expected_sessions: expected integer")

    report_inventory = require_exact_keys(
        contract["report_inventory"],
        (
            "amendment_path",
            "amendment_sha256",
            "step2_parent_positive_reports",
            "step2_parent_structural_reports",
            "step2_parent_testcases",
            "step4_new_positive_reports",
            "step4_changed_positive_reports",
            "step4_step2_testcase_delta",
            "step3_required_positive_reports",
            "step3_required_testcases",
            "required_positive_reports",
            "required_structural_reports",
            "required_testcases",
            "addon_companion_reports",
            "addon_companion_testcases",
        ),
        "coverage contract.report_inventory",
    )
    require(report_inventory == {
        "amendment_path": "scripts/v934/step4/coverage-report-amendment.tsv",
        "amendment_sha256": "5a1a07e2c47835fa244b90a06334341e13660a305d9eb7c74c64ee2f36a06504",
        "step2_parent_positive_reports": 724,
        "step2_parent_structural_reports": 59,
        "step2_parent_testcases": 5205,
        "step4_new_positive_reports": 4,
        "step4_changed_positive_reports": 6,
        "step4_step2_testcase_delta": 56,
        "step3_required_positive_reports": 45,
        "step3_required_testcases": 446,
        "required_positive_reports": 773,
        "required_structural_reports": 59,
        "required_testcases": 5707,
        "addon_companion_reports": 2,
        "addon_companion_testcases": 6,
    }, "coverage contract.report_inventory: frozen values changed")

    run_layout = require_exact_keys(
        contract["run_layout"],
        (
            "root_pattern",
            "toolchain_receipt",
            "class_universe",
            "exec_directory",
            "exec_manifest",
            "aggregate_exec",
            "aggregate_xml",
            "aggregate_html",
            "aggregate_provenance",
            "report_provenance",
            "coverage_observation",
        ),
        "coverage contract.run_layout",
    )
    require(run_layout == {
        "root_pattern": "target/v934-step4-coverage/runs/<run-id>",
        "toolchain_receipt": "toolchain-receipt.json",
        "class_universe": "class-universe.json",
        "exec_directory": "exec",
        "exec_manifest": "exec-manifest.json",
        "aggregate_exec": "report/jacoco-aggregate.exec",
        "aggregate_xml": "report/jacoco-aggregate/jacoco.xml",
        "aggregate_html": "report/jacoco-aggregate/index.html",
        "aggregate_provenance": "report/aggregate-provenance.json",
        "report_provenance": "report/report-provenance.json",
        "coverage_observation": "coverage-observation.json",
    }, "coverage contract.run_layout: frozen values changed")

    reporter = require_exact_keys(
        contract["reporter"],
        (
            "module",
            "packaging",
            "production_classes",
            "production_dependencies_on_reporter",
            "standard_jacoco_check_allowed",
            "versioned_xml_verifier_required",
            "effective_model_receipt_required",
        ),
        "coverage contract.reporter",
    )
    require(reporter["module"] == REPORTER_MODULE, "coverage contract.reporter.module: unexpected value")
    require(reporter["packaging"] == "pom", "coverage contract.reporter.packaging: expected pom")
    require(type(reporter["production_classes"]) is int and reporter["production_classes"] == 0, "coverage contract.reporter.production_classes: expected integer 0")
    require(type(reporter["production_dependencies_on_reporter"]) is int and reporter["production_dependencies_on_reporter"] == 0, "coverage contract.reporter.production_dependencies_on_reporter: expected integer 0")
    require(type(reporter["standard_jacoco_check_allowed"]) is bool and reporter["standard_jacoco_check_allowed"] is False, "coverage contract.reporter.standard_jacoco_check_allowed: expected boolean false")
    require(type(reporter["versioned_xml_verifier_required"]) is bool and reporter["versioned_xml_verifier_required"] is True, "coverage contract.reporter.versioned_xml_verifier_required: expected boolean true")
    require(type(reporter["effective_model_receipt_required"]) is bool and reporter["effective_model_receipt_required"] is True, "coverage contract.reporter.effective_model_receipt_required: expected boolean true")

    successor = require_exact_keys(
        contract["threshold_successor"],
        ("path", "required_status_for_diagnostic", "required_status_for_exit"),
        "coverage contract.threshold_successor",
    )
    require(successor == {
        "path": "scripts/v934/step4/coverage-thresholds.json",
        "required_status_for_diagnostic": "diagnostic-pending",
        "required_status_for_exit": "confirmed",
    }, "coverage contract.threshold_successor: frozen values changed")


def validate_thresholds(thresholds: dict[str, Any]) -> None:
    require_exact_keys(
        thresholds,
        (
            "schema_version",
            "kind",
            "status",
            "parent_policy",
            "jacoco",
            "model_inherited_gate",
            "critical_candidate_floor",
            "aggregate_observed",
            "aggregate_reviewed_thresholds",
            "critical_reviewed_thresholds",
            "review",
        ),
        "coverage thresholds",
    )
    require(type(thresholds["schema_version"]) is int and thresholds["schema_version"] == 1, "coverage thresholds.schema_version: expected integer 1")
    require(thresholds["kind"] == "v934-step4-coverage-threshold-successor", "coverage thresholds.kind: unexpected value")
    require(thresholds["status"] == "diagnostic-pending", "coverage thresholds.status: expected diagnostic-pending")

    parent = require_exact_keys(thresholds["parent_policy"], ("path", "sha256", "immutable"), "coverage thresholds.parent_policy")
    require(parent["path"] == EXPECTED_PARENT_LINKS["step1_coverage_policy"]["path"], "coverage thresholds.parent_policy.path: unexpected value")
    require(parent["sha256"] == EXPECTED_PARENT_LINKS["step1_coverage_policy"]["sha256"], "coverage thresholds.parent_policy.sha256: frozen value changed")
    require(type(parent["immutable"]) is bool and parent["immutable"] is True, "coverage thresholds.parent_policy.immutable: expected boolean true")

    jacoco = require_exact_keys(thresholds["jacoco"], ("version", "aggregate_reporter", "aggregate_check"), "coverage thresholds.jacoco")
    require(jacoco == {"version": "0.8.12", "aggregate_reporter": "build-only", "aggregate_check": "versioned-xml-verifier"}, "coverage thresholds.jacoco: unexpected values")

    gate = require_exact_keys(thresholds["model_inherited_gate"], ("module", "exec_policy", "bundle", "classes"), "coverage thresholds.model_inherited_gate")
    require(gate["module"] == "foggy-dataset-model", "coverage thresholds.model_inherited_gate.module: unexpected value")
    require(gate["exec_policy"] == "merged-unit-and-all-required-it", "coverage thresholds.model_inherited_gate.exec_policy: unexpected value")
    bundle = require_exact_keys(gate["bundle"], ("line", "branch"), "coverage thresholds.model_inherited_gate.bundle")
    require(type(bundle["line"]) is float and bundle["line"] == 0.77, "coverage thresholds model bundle line: expected 0.77")
    require(type(bundle["branch"]) is float and bundle["branch"] == 0.62, "coverage thresholds model bundle branch: expected 0.62")
    require(type(gate["classes"]) is list and len(gate["classes"]) == 1, "coverage thresholds.model_inherited_gate.classes: expected one class")
    class_gate = require_exact_keys(gate["classes"][0], ("fqcn", "line", "branch"), "coverage thresholds.model_inherited_gate.classes[0]")
    require(class_gate["fqcn"] == "com.foggyframework.dataset.db.model.impl.SemanticScaleSqlSupport", "coverage thresholds inherited class: unexpected FQCN")
    require(type(class_gate["line"]) is float and class_gate["line"] == 1.0, "coverage thresholds inherited class line: expected 1.0")
    require(type(class_gate["branch"]) is float and class_gate["branch"] == 1.0, "coverage thresholds inherited class branch: expected 1.0")

    floor = require_exact_keys(thresholds["critical_candidate_floor"], ("line", "branch"), "coverage thresholds.critical_candidate_floor")
    require(type(floor["line"]) is float and floor["line"] == 0.8, "coverage thresholds critical line floor: expected 0.8")
    require(type(floor["branch"]) is float and floor["branch"] == 0.7, "coverage thresholds critical branch floor: expected 0.7")
    require(thresholds["aggregate_observed"] is None, "coverage thresholds.aggregate_observed: expected null before diagnostic")
    require(thresholds["aggregate_reviewed_thresholds"] is None, "coverage thresholds.aggregate_reviewed_thresholds: expected null before review")
    require(thresholds["critical_reviewed_thresholds"] is None, "coverage thresholds.critical_reviewed_thresholds: expected null before review")
    review = require_exact_keys(thresholds["review"], ("reviewer", "reviewed_at", "diagnostic_run_id", "decision"), "coverage thresholds.review")
    require(review == {"reviewer": None, "reviewed_at": None, "diagnostic_run_id": None, "decision": "pending-all-lane-diagnostic"}, "coverage thresholds.review: unexpected pre-diagnostic values")


def validate_parent_lineage(repo_root: Path, contract: dict[str, Any], thresholds: dict[str, Any]) -> int:
    parent_links = contract["parent_links"]
    for name in (
        "step1_coverage_policy",
        "step1_hash_manifest",
        "step2_successor_hash_manifest",
        "step2_successor_freeze",
        "step3_required_contract",
    ):
        link = parent_links[name]
        path = safe_repo_path(repo_root, link["path"], f"parent {name}")
        require(sha256_file(path, f"parent {name}") == link["sha256"], f"parent {name}: hash mismatch")

    manifest_file_count = 0
    step1_manifest = safe_repo_path(repo_root, parent_links["step1_hash_manifest"]["path"], "Step 1 hash manifest")
    step2_manifest = safe_repo_path(repo_root, parent_links["step2_successor_hash_manifest"]["path"], "Step 2 hash manifest")
    manifest_file_count += validate_sha_manifest(step1_manifest, "Step 1 hash manifest")
    manifest_file_count += validate_sha_manifest(step2_manifest, "Step 2 hash manifest")

    step1_policy = load_json(safe_repo_path(repo_root, thresholds["parent_policy"]["path"], "Step 1 coverage policy"), "Step 1 coverage policy")
    require(step1_policy.get("schema_version") == 1, "Step 1 coverage policy: schema_version must be 1")
    require(step1_policy.get("status") == "step1-policy-frozen-observed-baseline-deferred-to-step4", "Step 1 coverage policy: frozen status changed")
    require(step1_policy.get("jacoco") == {"aggregate_check": "versioned-xml-verifier", "aggregate_reporter": "build-only", "version": "0.8.12"}, "Step 1 coverage policy: JaCoCo policy changed")
    require(step1_policy.get("candidate_floor") == {"branch": 0.7, "line": 0.8}, "Step 1 coverage policy: candidate floor changed")
    require(step1_policy.get("model_existing_gate") == {"branch": 0.62, "exec_policy": "merged-unit-and-it", "line": 0.77, "module": "foggy-dataset-model"}, "Step 1 coverage policy: model gate changed")

    run_id = parent_links["step3_required_final_manifest"]["run_id"]
    final_manifest = safe_repo_path(repo_root, f"target/v934-step3-required-matrix/runs/{run_id}/final/report-manifest.json", "Step 3 final manifest")
    candidate_manifest = safe_repo_path(repo_root, f"target/v934-step3-required-matrix/runs/{run_id}/candidate-manifest.json", "Step 3 candidate manifest")
    require(sha256_file(final_manifest, "Step 3 final manifest") == parent_links["step3_required_final_manifest"]["sha256"], "Step 3 final manifest: hash mismatch")
    require(sha256_file(candidate_manifest, "Step 3 candidate manifest") == parent_links["step3_required_candidate_manifest"]["sha256"], "Step 3 candidate manifest: hash mismatch")
    return manifest_file_count


def validate_ledger(path: Path, contract: dict[str, Any]) -> dict[str, int]:
    require(not path.is_symlink(), "coverage ledger: symlink is forbidden")
    actual_hash = sha256_file(path, "coverage ledger")
    require(actual_hash == contract["execution_ledger"]["sha256"], "coverage ledger: SHA-256 mismatch")
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ContractError(f"coverage ledger: cannot read UTF-8 TSV ({exc.__class__.__name__})") from exc
    require("\r" not in text, "coverage ledger: CR line endings are forbidden")
    reader = csv.DictReader(io.StringIO(text), delimiter="\t")
    require(tuple(reader.fieldnames or ()) == LEDGER_HEADER, "coverage ledger: header mismatch")
    rows = list(reader)
    require(len(rows) == 23, f"coverage ledger: expected 23 rows, found {len(rows)}")
    require(None not in (key for row in rows for key in row), "coverage ledger: extra TSV columns")

    exec_files = [row["exec_file"] for row in rows]
    variants = [row["variant_key"] for row in rows]
    require(len(set(exec_files)) == 23, "coverage ledger: exec_file values must be unique")
    require(len(set(variants)) == 23, "coverage ledger: variant_key values must be unique")
    require(set(exec_files) == set(EXPECTED_LEDGER_ROWS), "coverage ledger: exact exec-file identity set changed")

    counts: Counter[str] = Counter()
    sessions = 0
    for number, row in enumerate(rows, 2):
        exec_file = row["exec_file"]
        require(re.fullmatch(r"jacoco-(?:ut|it)-?[a-z0-9-]*\.exec", exec_file) is not None, f"coverage ledger row {number}: unsafe exec_file")
        require(re.fullmatch(r"[a-z0-9][a-z0-9-]*", row["variant_key"]) is not None, f"coverage ledger row {number}: unsafe variant_key")
        require(row["required"] == "true", f"coverage ledger row {number}: required must be literal true")
        try:
            session_count = int(row["expected_session_count"], 10)
        except ValueError as exc:
            raise ContractError(f"coverage ledger row {number}: invalid session count") from exc
        require(str(session_count) == row["expected_session_count"] and session_count > 0, f"coverage ledger row {number}: session count must be canonical positive integer")
        owners = row["expected_session_owners"].split(",")
        require(len(owners) == session_count and len(set(owners)) == session_count, f"coverage ledger row {number}: owner cardinality must equal session count")
        require(all(re.fullmatch(r"[a-z0-9][a-z0-9-]*", owner) for owner in owners), f"coverage ledger row {number}: unsafe session owner")
        require(row["disposition"] != "" and re.fullmatch(r"[a-z0-9][a-z0-9-]*", row["disposition"]) is not None, f"coverage ledger row {number}: unsafe disposition")
        expected = EXPECTED_LEDGER_ROWS[exec_file]
        actual = (row["runner"], row["lane"], row["variant_key"], session_count, row["expected_session_owners"], row["disposition"])
        require(actual == expected, f"coverage ledger row {number}: frozen semantic signature changed for {exec_file}")
        counts[row["lane"]] += 1
        sessions += session_count

    expected_counts = {"unit": 1, "integration": 6, "database": 7, "external": 7, "addon": 2}
    require(dict(counts) == expected_counts, f"coverage ledger: lane counts must be {expected_counts}")
    require(sessions == 48, f"coverage ledger: expected 48 sessions, found {sessions}")
    require(len(rows) == contract["execution_ledger"]["exec_files"], "coverage ledger: row count disagrees with contract")
    require(sessions == contract["execution_ledger"]["expected_sessions"], "coverage ledger: session count disagrees with contract")
    return expected_counts


def validate_report_amendment(repo_root: Path, contract: dict[str, Any]) -> dict[str, int]:
    inventory = contract["report_inventory"]
    path = safe_repo_path(repo_root, inventory["amendment_path"], "coverage report amendment")
    require(
        sha256_file(path, "coverage report amendment") == inventory["amendment_sha256"],
        "coverage report amendment: hash differs",
    )
    header = (
        "source_path",
        "module",
        "report_fqcn",
        "runner",
        "variant_key",
        "step2_source_sha256",
        "step4_source_sha256",
        "step2_testcase_nodes",
        "step4_expected_testcase_nodes",
        "change_kind",
        "disposition",
        "workitem",
    )
    try:
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            require(tuple(reader.fieldnames or ()) == header, "coverage report amendment: header changed")
            rows = list(reader)
    except (OSError, UnicodeError) as exc:
        raise ContractError("coverage report amendment: cannot read UTF-8 TSV") from exc
    require(len(rows) == 10, "coverage report amendment: expected exact 10 rows")
    require(len({row["source_path"] for row in rows}) == 10, "coverage report amendment: duplicate source")
    require(len({row["report_fqcn"] for row in rows}) == 10, "coverage report amendment: duplicate report FQCN")
    counts: Counter[str] = Counter()
    testcase_delta = 0
    for number, row in enumerate(rows, 1):
        source = safe_repo_path(repo_root, row["source_path"], f"coverage report amendment row {number} source")
        require(source.is_file() and not source.is_symlink(), f"coverage report amendment row {number}: source missing")
        module = safe_repo_path(repo_root, row["module"], f"coverage report amendment row {number} module")
        require(module.is_dir(), f"coverage report amendment row {number}: module missing")
        require(source.is_relative_to(module), f"coverage report amendment row {number}: source is outside module")
        require(re.fullmatch(r"[a-zA-Z_$][a-zA-Z0-9_$.]*", row["report_fqcn"]) is not None, f"coverage report amendment row {number}: invalid FQCN")
        require(row["runner"] in {"surefire", "failsafe"}, f"coverage report amendment row {number}: invalid runner")
        require(row["variant_key"] == ("unit" if row["runner"] == "surefire" else "sqlite-broad"), f"coverage report amendment row {number}: runner/variant mismatch")
        require(re.fullmatch(r"[0-9a-f]{64}", row["step4_source_sha256"]) is not None, f"coverage report amendment row {number}: invalid Step4 source hash")
        require(sha256_file(source, f"coverage report amendment row {number} source") == row["step4_source_sha256"], f"coverage report amendment row {number}: current source hash differs")
        try:
            before_nodes = int(row["step2_testcase_nodes"])
            after_nodes = int(row["step4_expected_testcase_nodes"])
        except ValueError as exc:
            raise ContractError(f"coverage report amendment row {number}: invalid testcase count") from exc
        require(before_nodes >= 0 and after_nodes > 0, f"coverage report amendment row {number}: invalid testcase cardinality")
        change_kind = row["change_kind"]
        require(change_kind in {"new-positive-report", "changed-positive-report"}, f"coverage report amendment row {number}: invalid change kind")
        if change_kind == "new-positive-report":
            require(row["step2_source_sha256"] == "absent" and before_nodes == 0, f"coverage report amendment row {number}: new report must be absent from Step2")
            require(row["disposition"] == "step4-required-unit-amendment" and row["runner"] == "surefire", f"coverage report amendment row {number}: invalid new-report disposition")
        else:
            require(re.fullmatch(r"[0-9a-f]{64}", row["step2_source_sha256"]) is not None, f"coverage report amendment row {number}: invalid Step2 source hash")
            require(row["step2_source_sha256"] != row["step4_source_sha256"], f"coverage report amendment row {number}: unchanged source")
            if row["disposition"] == "step4-cardinality-amendment":
                require(after_nodes > before_nodes, f"coverage report amendment row {number}: cardinality amendment must increase testcase count")
            elif row["disposition"] == "step4-source-amendment":
                require(after_nodes == before_nodes, f"coverage report amendment row {number}: source amendment must preserve testcase count")
            else:
                require(False, f"coverage report amendment row {number}: invalid changed-report disposition")
        workitem = safe_repo_path(repo_root, row["workitem"], f"coverage report amendment row {number} workitem")
        require(workitem.is_file() and not workitem.is_symlink(), f"coverage report amendment row {number}: workitem missing")
        counts[change_kind] += 1
        testcase_delta += after_nodes - before_nodes
    require(counts == Counter({"new-positive-report": 4, "changed-positive-report": 6}), "coverage report amendment: expected 4 new and 6 changed reports")
    require(testcase_delta == inventory["step4_step2_testcase_delta"] == 56, "coverage report amendment: testcase delta must be 56")
    require(inventory["required_positive_reports"] == inventory["step2_parent_positive_reports"] + inventory["step4_new_positive_reports"] + inventory["step3_required_positive_reports"], "coverage report inventory: required positive arithmetic differs")
    require(inventory["required_testcases"] == inventory["step2_parent_testcases"] + testcase_delta + inventory["step3_required_testcases"], "coverage report inventory: required testcase arithmetic differs")
    return dict(counts)


def validate_step2_module_lineage(repo_root: Path) -> list[str]:
    freeze_path = safe_repo_path(repo_root, "scripts/v934/contract-freeze.json", "Step 1 frozen reactor contract")
    freeze = load_json(freeze_path, "Step 1 frozen reactor contract")
    require(freeze.get("schema_version") == 1 and freeze.get("step") == 1, "Step 1 frozen reactor contract: invalid identity")
    require(freeze.get("status") == "confirmed" and freeze.get("decision") == "passed", "Step 1 frozen reactor contract: not confirmed")
    reactor = require_exact_keys(freeze.get("reactor"), ("module_count", "modules", "path_policy", "profile_policy"), "Step 1 frozen reactor contract.reactor")
    require(type(reactor["module_count"]) is int and reactor["module_count"] == 24, "Step 1 frozen reactor contract: expected 24 modules")
    require(type(reactor["modules"]) is list and all(type(item) is str for item in reactor["modules"]), "Step 1 frozen reactor contract: invalid module list")
    modules = list(reactor["modules"])
    require(len(modules) == 24 and len(set(modules)) == 24, "Step 1 frozen reactor contract: module list must contain 24 unique paths")
    require(modules == sorted(modules), "Step 1 frozen reactor contract: module list must be sorted")

    successor_path = safe_repo_path(repo_root, "scripts/v934/successor/step2/contract-freeze.json", "Step 2 successor freeze")
    successor = load_json(successor_path, "Step 2 successor freeze")
    require(successor.get("schema_version") == 6 and successor.get("step") == 2, "Step 2 successor freeze: invalid identity")
    require(successor.get("status") == "confirmed" and successor.get("decision") == "passed", "Step 2 successor freeze: not confirmed")
    parent = successor.get("parent")
    require(type(parent) is dict, "Step 2 successor freeze.parent: expected object")
    require(parent.get("step1_contract_manifest_sha256") == EXPECTED_PARENT_LINKS["step1_hash_manifest"]["sha256"], "Step 2 successor freeze: Step 1 lineage hash changed")
    require(parent.get("step1_contract_freeze_sha256") == sha256_file(freeze_path, "Step 1 frozen reactor contract"), "Step 2 successor freeze: Step 1 freeze lineage mismatch")

    inventory_path = safe_repo_path(repo_root, "scripts/v934/package-successor-inventory.tsv", "package successor inventory")
    try:
        inventory_text = inventory_path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ContractError(f"package successor inventory: cannot read ({exc.__class__.__name__})") from exc
    inventory_rows = list(csv.DictReader(io.StringIO(inventory_text), delimiter="\t"))
    matches = [row for row in inventory_rows if row.get("surface") == "active-reactor-projects"]
    require(len(matches) == 1, "package successor inventory: expected one active-reactor-projects row")
    row = matches[0]
    require(row.get("predecessor_expectation") == "25 including root aggregator", "package successor inventory: predecessor reactor expectation changed")
    require(row.get("v934_expected_delta") == "+1 build-only reporter at Step4", "package successor inventory: Step 4 reporter delta changed")
    require(row.get("v934_successor_rule") == "26 projects after reporter; no production dependency on reporter", "package successor inventory: successor reactor rule changed")
    require(row.get("observed_step") == "4", "package successor inventory: reporter must be observed at Step 4")
    return modules


def validate_root_pom(path: Path, frozen_modules: list[str]) -> tuple[ET.Element, list[str]]:
    root = parse_xml(path, "root POM")
    modules_node = only(root.findall("m:modules", NS), "root POM.modules")
    modules = [text_of(item, "root POM.module") for item in modules_node.findall("m:module", NS)]
    require(len(modules) == 25 and len(set(modules)) == 25, "root POM: expected 25 unique module declarations")
    require(set(modules) == set(frozen_modules) | {REPORTER_MODULE}, "root POM: modules must be exact frozen 24 plus coverage reporter")
    require(modules.count(REPORTER_MODULE) == 1, "root POM: coverage reporter must appear exactly once")

    properties = only(root.findall("m:properties", NS), "root POM.properties")
    prop_values: dict[str, str] = {}
    for item in list(properties):
        name = item.tag.rsplit("}", 1)[-1]
        require(name not in prop_values, f"root POM.properties: duplicate {name}")
        prop_values[name] = (item.text or "").strip()
    require(prop_values.get("java.version") == "17", "root POM: java.version must be exact 17")
    require(prop_values.get("jacoco-maven-plugin.version") == "0.8.12", "root POM: JaCoCo version must be 0.8.12")
    require("jacoco.skip" not in prop_values, "root POM: jacoco.skip is forbidden")
    require("jacoco.ut.argLine" in prop_values and prop_values["jacoco.ut.argLine"] == "", "root POM: jacoco.ut.argLine must exist and default empty")
    require("jacoco.it.argLine" in prop_values and prop_values["jacoco.it.argLine"] == "", "root POM: jacoco.it.argLine must exist and default empty")
    require(prop_values.get("argLine") == "-Dfile.encoding=UTF-8", "root POM: repository UTF-8 argLine changed")
    require(prop_values.get("jacoco.ut.destFile") == "${project.build.directory}/jacoco-ut.exec", "root POM: unit destination default changed")
    require(prop_values.get("jacoco.it.destFile") == "${project.build.directory}/jacoco-it.exec", "root POM: integration destination default changed")
    require(prop_values.get("v934.coverage.sessionId") == "${project.artifactId}", "root POM: coverage session ID default changed")

    build = only(root.findall("m:build", NS), "root POM.build")
    require_child_names(build, ("plugins",), "root POM.build")
    plugins = only(root.findall("m:build/m:plugins", NS), "root POM.build.plugins")
    base_plugins = plugins.findall("m:plugin", NS)
    require_child_names(
        plugins,
        ("plugin", "plugin", "plugin", "plugin"),
        "root POM.build.plugins",
    )
    base_artifacts = [
        child_text(plugin, "artifactId", "root POM base plugin")
        for plugin in base_plugins
    ]
    require(
        base_artifacts
        == [
            "maven-compiler-plugin",
            "maven-surefire-plugin",
            "maven-failsafe-plugin",
            "maven-source-plugin",
        ],
        "root POM: base build plugin order/set changed",
    )
    for container_label, inherited_plugins in (
        ("root POM.build.plugins", root.findall("m:build/m:plugins/m:plugin", NS)),
        (
            "root POM.build.pluginManagement",
            root.findall("m:build/m:pluginManagement/m:plugins/m:plugin", NS),
        ),
    ):
        for plugin in inherited_plugins:
            artifact = child_text(plugin, "artifactId", container_label)
            require(
                artifact != "jacoco-maven-plugin",
                f"{container_label}: base JaCoCo plugin/configuration is forbidden",
            )
    compiler = find_plugin(plugins, "maven-compiler-plugin", "root POM")
    require_child_names(
        compiler,
        ("groupId", "artifactId", "configuration"),
        "root POM maven-compiler-plugin",
    )
    require(
        child_text(compiler, "groupId", "root POM maven-compiler-plugin")
        == "org.apache.maven.plugins",
        "root POM maven-compiler-plugin: groupId changed",
    )
    compiler_config = only(
        compiler.findall("m:configuration", NS),
        "root POM maven-compiler-plugin.configuration",
    )
    require_child_names(
        compiler_config,
        ("source", "target", "encoding"),
        "root POM maven-compiler-plugin.configuration",
    )
    require(
        child_text(compiler_config, "source", "root POM maven-compiler-plugin")
        == "${java.version}"
        and child_text(compiler_config, "target", "root POM maven-compiler-plugin")
        == "${java.version}"
        and child_text(compiler_config, "encoding", "root POM maven-compiler-plugin")
        == "UTF-8",
        "root POM maven-compiler-plugin: compiler configuration changed",
    )

    expected_patterns = {
        "maven-surefire-plugin": {
            "property": "jacoco.ut.argLine",
            "config_children": (
                "skipTests", "argLine", "forkCount", "reuseForks", "includes",
                "excludes", "failIfNoTests", "failIfNoSpecifiedTests",
            ),
            "skip": ("skipTests", "${skipUnitTests}"),
            "includes": ("**/*Test.java", "**/*Tests.java", "**/*TestCase.java"),
            "excludes": (
                "**/IT*.java", "**/*IT.java", "**/*IT$*.java", "**/*ITCase.java",
                "**/*ITCase$*.java", "**/*E2E.java", "**/*E2E$*.java",
                "**/*E2ETest.java", "**/*E2ETest$*.java",
                "**/MultiDatabaseQueryTest.java", "**/MultiDatabaseQueryTest$*.java",
            ),
            "fail_if_none": "${surefire.failIfNoTests}",
            "fail_if_selected": "true",
        },
        "maven-failsafe-plugin": {
            "property": "jacoco.it.argLine",
            "config_children": (
                "skipITs", "argLine", "forkCount", "reuseForks", "includes",
                "failIfNoTests", "failIfNoSpecifiedTests",
            ),
            "skip": ("skipITs", "${skipITs}"),
            "includes": (
                "**/IT*.java", "**/*IT.java", "**/*ITCase.java", "**/*E2E.java",
                "**/*E2ETest.java", "**/MultiDatabaseQueryTest.java",
            ),
            "excludes": (),
            "fail_if_none": "${failsafe.failIfNoTests}",
            "fail_if_selected": "${failsafe.failIfNoSpecifiedTests}",
        },
    }
    for artifact, expected in expected_patterns.items():
        property_name = expected["property"]
        plugin = find_plugin(plugins, artifact, "root POM")
        require_child_names(
            plugin,
            ("groupId", "artifactId", "version", "configuration"),
            f"root POM {artifact}",
        )
        require(
            child_text(plugin, "groupId", f"root POM {artifact}")
            == "org.apache.maven.plugins",
            f"root POM {artifact}: groupId changed",
        )
        config = only(plugin.findall("m:configuration", NS), f"root POM {artifact}.configuration")
        require_child_names(config, expected["config_children"], f"root POM {artifact}.configuration")
        skip_name, skip_value = expected["skip"]
        require(
            child_text(config, skip_name, f"root POM {artifact}") == skip_value,
            f"root POM {artifact}: skip binding changed",
        )
        require(child_text(config, "argLine", f"root POM {artifact}") == f"@{{argLine}} @{{{property_name}}}", f"root POM {artifact}: distinct late JaCoCo argLine missing")
        require(child_text(config, "forkCount", f"root POM {artifact}") == "1", f"root POM {artifact}: forkCount must be 1")
        require(child_text(config, "reuseForks", f"root POM {artifact}") == "true", f"root POM {artifact}: reuseForks must be true")
        require(config.find("m:parallel", NS) is None and config.find("m:threadCount", NS) is None, f"root POM {artifact}: parallel execution is forbidden for shared exec files")
        includes = tuple(
            text_of(item, f"root POM {artifact}.include")
            for item in config.findall("m:includes/m:include", NS)
        )
        excludes = tuple(
            text_of(item, f"root POM {artifact}.exclude")
            for item in config.findall("m:excludes/m:exclude", NS)
        )
        require(includes == expected["includes"], f"root POM {artifact}: includes changed")
        require(excludes == expected["excludes"], f"root POM {artifact}: excludes changed")
        require(
            child_text(config, "failIfNoTests", f"root POM {artifact}")
            == expected["fail_if_none"]
            and child_text(config, "failIfNoSpecifiedTests", f"root POM {artifact}")
            == expected["fail_if_selected"],
            f"root POM {artifact}: zero-test policy changed",
        )

    source = find_plugin(plugins, "maven-source-plugin", "root POM")
    require_child_names(
        source,
        ("groupId", "artifactId", "executions"),
        "root POM maven-source-plugin",
    )
    require(
        child_text(source, "groupId", "root POM maven-source-plugin")
        == "org.apache.maven.plugins",
        "root POM maven-source-plugin: groupId changed",
    )
    source_execution = only(
        source.findall("m:executions/m:execution", NS),
        "root POM maven-source-plugin execution",
    )
    require_child_names(
        source_execution,
        ("id", "goals"),
        "root POM maven-source-plugin execution",
    )
    require(
        child_text(source_execution, "id", "root POM maven-source-plugin execution")
        == "attach-sources"
        and [
            text_of(goal, "root POM maven-source-plugin goal")
            for goal in source_execution.findall("m:goals/m:goal", NS)
        ]
        == ["jar-no-fork"],
        "root POM maven-source-plugin: execution changed",
    )

    profiles_node = only(root.findall("m:profiles", NS), "root POM.profiles")
    require_child_names(profiles_node, ("profile", "profile"), "root POM.profiles")
    profiles = profiles_node.findall("m:profile", NS)
    profile_ids = [child_text(item, "id", "root POM profile") for item in profiles]
    require(
        profile_ids == ["v934-coverage", "release"],
        "root POM: profile order/set changed",
    )
    release_profile = profiles[1]
    require_child_names(
        release_profile,
        ("id", "build"),
        "root POM release profile",
    )
    release_plugins = only(
        release_profile.findall("m:build/m:plugins", NS),
        "root POM release profile plugins",
    )
    central = find_plugin(
        release_plugins,
        "central-publishing-maven-plugin",
        "root POM release profile",
    )
    require_child_names(
        central,
        ("groupId", "artifactId", "version", "extensions", "configuration"),
        "root POM central-publishing-maven-plugin",
    )
    require(
        child_text(central, "groupId", "root POM central-publishing-maven-plugin")
        == "org.sonatype.central"
        and child_text(central, "version", "root POM central-publishing-maven-plugin")
        == "0.6.0"
        and child_text(central, "extensions", "root POM central-publishing-maven-plugin")
        == "true",
        "root POM central-publishing-maven-plugin identity changed",
    )
    central_config = only(
        central.findall("m:configuration", NS),
        "root POM central-publishing-maven-plugin configuration",
    )
    require_child_names(
        central_config,
        ("publishingServerId", "autoPublish", "excludeArtifacts"),
        "root POM central-publishing-maven-plugin configuration",
    )
    require(
        child_text(
            central_config,
            "publishingServerId",
            "root POM central-publishing-maven-plugin configuration",
        )
        == "central"
        and child_text(
            central_config,
            "autoPublish",
            "root POM central-publishing-maven-plugin configuration",
        )
        == "true"
        and child_text(
            central_config,
            "excludeArtifacts",
            "root POM central-publishing-maven-plugin configuration",
        )
        == REPORTER_ARTIFACT,
        "root POM coverage reporter Central exclusion changed",
    )
    coverage_profiles = [profile for profile in profiles if child_text(profile, "id", "root POM profile") == "v934-coverage"]
    profile = only(coverage_profiles, "root POM v934-coverage profile")
    require_child_names(profile, ("id", "build"), "root POM v934-coverage profile")
    profile_plugins = only(profile.findall("m:build/m:plugins", NS), "root POM v934-coverage plugins")
    require(len(profile_plugins.findall("m:plugin", NS)) == 1, "root POM v934-coverage: only the JaCoCo agent plugin is allowed")
    jacoco_plugin = find_plugin(profile_plugins, "jacoco-maven-plugin", "root POM v934-coverage")
    require_child_names(
        jacoco_plugin,
        ("groupId", "artifactId", "version", "executions"),
        "root POM v934-coverage JaCoCo",
    )
    require(child_text(jacoco_plugin, "groupId", "root POM v934-coverage JaCoCo") == "org.jacoco", "root POM v934-coverage: wrong JaCoCo groupId")
    require(child_text(jacoco_plugin, "version", "root POM v934-coverage JaCoCo") == "${jacoco-maven-plugin.version}", "root POM v934-coverage: version must use frozen property")
    all_jacoco_plugins = [
        plugin
        for plugin in root.findall(".//m:plugin", NS)
        if child_text(plugin, "artifactId", "root POM plugin") == "jacoco-maven-plugin"
    ]
    require(
        len(all_jacoco_plugins) == 1 and all_jacoco_plugins[0] is jacoco_plugin,
        "root POM: JaCoCo plugin is allowed only in the exact v934-coverage profile",
    )
    executions = jacoco_plugin.findall("m:executions/m:execution", NS)
    require(len(executions) == 2, "root POM v934-coverage: expected exactly two agent executions")
    by_id = {child_text(execution, "id", "root POM v934-coverage execution"): execution for execution in executions}
    require(len(by_id) == 2 and set(by_id) == {"v934-prepare-unit-agent", "v934-prepare-integration-agent"}, "root POM v934-coverage: execution IDs changed")
    expected_agents = {
        "v934-prepare-unit-agent": ("initialize", "prepare-agent", "jacoco.ut.argLine", "${jacoco.ut.destFile}"),
        "v934-prepare-integration-agent": ("pre-integration-test", "prepare-agent-integration", "jacoco.it.argLine", "${jacoco.it.destFile}"),
    }
    for execution_id, (phase, goal, property_name, destination) in expected_agents.items():
        execution = by_id[execution_id]
        require_child_names(
            execution,
            ("id", "phase", "goals", "configuration"),
            f"root POM {execution_id}",
        )
        require(child_text(execution, "phase", f"root POM {execution_id}") == phase, f"root POM {execution_id}: phase changed")
        goals = [text_of(item, f"root POM {execution_id}.goal") for item in execution.findall("m:goals/m:goal", NS)]
        require(goals == [goal], f"root POM {execution_id}: goal changed")
        config = only(execution.findall("m:configuration", NS), f"root POM {execution_id}.configuration")
        require_child_names(
            config,
            ("propertyName", "destFile", "append", "sessionId"),
            f"root POM {execution_id}.configuration",
        )
        require(child_text(config, "propertyName", f"root POM {execution_id}") == property_name, f"root POM {execution_id}: propertyName changed")
        require(child_text(config, "destFile", f"root POM {execution_id}") == destination, f"root POM {execution_id}: destFile changed")
        require(child_text(config, "append", f"root POM {execution_id}") == "true", f"root POM {execution_id}: append must be true")
        require(child_text(config, "sessionId", f"root POM {execution_id}") == "${v934.coverage.sessionId}-${project.artifactId}", f"root POM {execution_id}: sessionId changed")

    try:
        pom_text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ContractError(f"root POM: cannot read text ({exc.__class__.__name__})") from exc
    require(re.search(r"(?<![A-Za-z0-9])-T(?:\s|[0-9]|C|$)", pom_text) is None, "root POM: Maven -T reactor parallelism is forbidden")
    return root, modules


def module_artifact_ids(repo_root: Path, modules: list[str]) -> dict[str, str]:
    artifacts: dict[str, str] = {}
    for module in modules:
        module_path = safe_repo_path(repo_root, module, f"production module {module}")
        pom_path = module_path / "pom.xml"
        require(pom_path.is_file() and not pom_path.is_symlink(), f"production module {module}: pom.xml missing or symlinked")
        pom = parse_xml(pom_path, f"production module {module} POM")
        artifact = text_of(only(pom.findall("m:artifactId", NS), f"production module {module}.artifactId"), f"production module {module}.artifactId")
        require(artifact != REPORTER_ARTIFACT, f"production module {module}: reporter artifact collision")
        require(artifact not in artifacts, f"production modules: duplicate artifactId {artifact}")
        artifacts[artifact] = module
    require(len(artifacts) == 24, "production modules: expected 24 unique artifactIds")
    return artifacts


def jacoco_rule_signature(
    execution: ET.Element,
    label: str,
    config_children: tuple[str, ...] = ("rules",),
) -> tuple[Any, ...]:
    config = only(execution.findall("m:configuration", NS), f"{label}.configuration")
    require_child_names(config, config_children, f"{label}.configuration")
    rules = only(config.findall("m:rules", NS), f"{label}.rules")
    require_child_names(rules, ("rule", "rule"), f"{label}.rules")
    result = []
    for rule in rules.findall("m:rule", NS):
        element = child_text(rule, "element", f"{label}.rule")
        includes_node = rule.find("m:includes", NS)
        require_child_names(
            rule,
            ("element", "limits") if includes_node is None else ("element", "includes", "limits"),
            f"{label}.rule[{element}]",
        )
        includes = () if includes_node is None else tuple(
            text_of(item, f"{label}.include") for item in includes_node.findall("m:include", NS)
        )
        if includes_node is not None:
            require_child_names(
                includes_node,
                tuple("include" for _ in includes),
                f"{label}.rule[{element}].includes",
            )
            require(includes, f"{label}.rule[{element}]: includes must not be empty")
        limits_node = only(rule.findall("m:limits", NS), f"{label}.limits")
        limits_elements = limits_node.findall("m:limit", NS)
        require_child_names(
            limits_node,
            tuple("limit" for _ in limits_elements),
            f"{label}.rule[{element}].limits",
        )
        require(limits_elements, f"{label}.rule[{element}]: limits must not be empty")
        limits = []
        for limit in limits_elements:
            require_child_names(
                limit,
                ("counter", "value", "minimum"),
                f"{label}.rule[{element}].limit",
            )
            limits.append((
                child_text(limit, "counter", f"{label}.limit"),
                child_text(limit, "value", f"{label}.limit"),
                child_text(limit, "minimum", f"{label}.limit"),
            ))
        result.append((element, includes, tuple(limits)))
    return tuple(result)


def validate_model_gate_profile(path: Path) -> None:
    require(not path.is_symlink(), "model POM: symlink is forbidden")
    model = parse_xml(path, "model POM")
    profiles = model.findall("m:profiles/m:profile", NS)
    by_id = {child_text(profile, "id", "model POM profile"): profile for profile in profiles}
    require(len(by_id) == len(profiles), "model POM: duplicate profile ID")
    require("coverage" in by_id and "v934-coverage-model-check" in by_id, "model POM: legacy and Step4 coverage profiles are required")
    expected_rules = (
        (
            "BUNDLE",
            (),
            (("LINE", "COVEREDRATIO", "0.77"), ("BRANCH", "COVEREDRATIO", "0.62")),
        ),
        (
            "CLASS",
            ("com.foggyframework.dataset.db.model.impl.SemanticScaleSqlSupport",),
            (("LINE", "COVEREDRATIO", "1.00"), ("BRANCH", "COVEREDRATIO", "1.00")),
        ),
    )

    legacy_plugins = only(by_id["coverage"].findall("m:build/m:plugins", NS), "model legacy coverage plugins")
    require_child_names(by_id["coverage"], ("id", "build"), "model legacy coverage profile")
    require_child_names(legacy_plugins, ("plugin",), "model legacy coverage plugins")
    legacy_jacoco = find_plugin(legacy_plugins, "jacoco-maven-plugin", "model legacy coverage")
    require_child_names(
        legacy_jacoco,
        ("groupId", "artifactId", "version", "executions"),
        "model legacy coverage JaCoCo",
    )
    require(child_text(legacy_jacoco, "groupId", "model legacy coverage JaCoCo") == "org.jacoco", "model legacy coverage: wrong JaCoCo groupId")
    require(child_text(legacy_jacoco, "version", "model legacy coverage JaCoCo") == "0.8.12", "model legacy coverage: JaCoCo version changed")
    legacy_executions = legacy_jacoco.findall("m:executions/m:execution", NS)
    legacy_executions_node = only(legacy_jacoco.findall("m:executions", NS), "model legacy coverage executions")
    require_child_names(
        legacy_executions_node,
        ("execution", "execution", "execution"),
        "model legacy coverage executions",
    )
    legacy_by_id = {child_text(execution, "id", "model legacy coverage execution"): execution for execution in legacy_executions}
    require(set(legacy_by_id) == {"jacoco-prepare-agent", "jacoco-report", "jacoco-check"}, "model legacy coverage: execution set changed")
    require_child_names(legacy_by_id["jacoco-prepare-agent"], ("id", "goals"), "model legacy prepare-agent")
    require_child_names(legacy_by_id["jacoco-report"], ("id", "phase", "goals"), "model legacy report")
    require_child_names(legacy_by_id["jacoco-check"], ("id", "phase", "goals", "configuration"), "model legacy check")
    require(
        [text_of(goal, "model legacy prepare-agent goal") for goal in legacy_by_id["jacoco-prepare-agent"].findall("m:goals/m:goal", NS)] == ["prepare-agent"]
        and child_text(legacy_by_id["jacoco-report"], "phase", "model legacy report") == "verify"
        and [text_of(goal, "model legacy report goal") for goal in legacy_by_id["jacoco-report"].findall("m:goals/m:goal", NS)] == ["report"]
        and child_text(legacy_by_id["jacoco-check"], "phase", "model legacy check") == "verify"
        and [text_of(goal, "model legacy check goal") for goal in legacy_by_id["jacoco-check"].findall("m:goals/m:goal", NS)] == ["check"],
        "model legacy coverage: execution phases/goals changed",
    )
    require(jacoco_rule_signature(legacy_by_id["jacoco-check"], "model legacy jacoco-check") == expected_rules, "model legacy coverage: inherited gates changed")

    step4 = by_id["v934-coverage-model-check"]
    require_child_names(
        step4,
        ("id", "properties", "build"),
        "model Step4 coverage profile",
    )
    properties = only(step4.findall("m:properties", NS), "model Step4 coverage properties")
    require_child_names(
        properties,
        ("v934.coverage.model.dataFile",),
        "model Step4 coverage properties",
    )
    sentinel = child_text(properties, "v934.coverage.model.dataFile", "model Step4 coverage properties")
    require(sentinel == "${project.build.directory}/v934-step4-coverage/__MISSING_EXTERNAL_MERGED_UNIT_ALL_REQUIRED_IT__.exec", "model Step4 coverage: fail-closed sentinel changed")
    plugins = only(step4.findall("m:build/m:plugins", NS), "model Step4 coverage plugins")
    require(len(plugins.findall("m:plugin", NS)) == 2, "model Step4 coverage: expected only enforcer and JaCoCo")
    require(
        [child_text(plugin, "artifactId", "model Step4 coverage plugin") for plugin in plugins.findall("m:plugin", NS)]
        == ["maven-enforcer-plugin", "jacoco-maven-plugin"],
        "model Step4 coverage: plugin order/set changed",
    )
    enforcer = find_plugin(plugins, "maven-enforcer-plugin", "model Step4 coverage")
    require_child_names(
        enforcer,
        ("groupId", "artifactId", "version", "executions"),
        "model Step4 coverage enforcer",
    )
    require(child_text(enforcer, "groupId", "model Step4 coverage enforcer") == "org.apache.maven.plugins", "model Step4 coverage: wrong enforcer groupId")
    require(child_text(enforcer, "version", "model Step4 coverage enforcer") == "3.5.0", "model Step4 coverage: enforcer version changed")
    enforcer_executions = enforcer.findall("m:executions/m:execution", NS)
    enforcer_execution = only(enforcer_executions, "model Step4 coverage enforcer execution")
    require_child_names(
        enforcer_execution,
        ("id", "phase", "goals", "configuration"),
        "model Step4 coverage enforcer execution",
    )
    require(child_text(enforcer_execution, "id", "model Step4 coverage enforcer") == "v934-coverage-model-require-external-data", "model Step4 coverage: enforcer execution ID changed")
    require(child_text(enforcer_execution, "phase", "model Step4 coverage enforcer") == "validate", "model Step4 coverage: enforcer must run in validate")
    require(
        [text_of(goal, "model Step4 coverage enforcer goal") for goal in enforcer_execution.findall("m:goals/m:goal", NS)] == ["enforce"],
        "model Step4 coverage: enforcer goal changed",
    )
    enforcer_config = only(enforcer_execution.findall("m:configuration", NS), "model Step4 coverage enforcer configuration")
    require_child_names(enforcer_config, ("rules",), "model Step4 coverage enforcer configuration")
    enforcer_rules = only(enforcer_config.findall("m:rules", NS), "model Step4 coverage enforcer rules")
    require_child_names(enforcer_rules, ("requireProperty", "requireFilesExist"), "model Step4 coverage enforcer rules")
    require_property = only(enforcer_rules.findall("m:requireProperty", NS), "model Step4 coverage requireProperty")
    require_child_names(require_property, ("property", "regex", "regexMessage"), "model Step4 coverage requireProperty")
    require(child_text(require_property, "property", "model Step4 coverage requireProperty") == "v934.coverage.model.dataFile", "model Step4 coverage: required property changed")
    regex = child_text(require_property, "regex", "model Step4 coverage requireProperty")
    require(regex == "^/(?!.*__MISSING_EXTERNAL_MERGED_UNIT_ALL_REQUIRED_IT__).+$", "model Step4 coverage: data file must be absolute and reject sentinel")
    require(
        child_text(require_property, "regexMessage", "model Step4 coverage requireProperty")
        == "Supply -Dv934.coverage.model.dataFile=<absolute merged Unit+all-required-IT exec file>.",
        "model Step4 coverage: required-property message changed",
    )
    require_files = only(enforcer_rules.findall("m:requireFilesExist", NS), "model Step4 coverage requireFilesExist")
    require_child_names(require_files, ("files", "message"), "model Step4 coverage requireFilesExist")
    files = only(require_files.findall("m:files", NS), "model Step4 coverage requireFilesExist.files")
    require_child_names(files, ("file",), "model Step4 coverage requireFilesExist.files")
    require(child_text(files, "file", "model Step4 coverage requireFilesExist.files") == "${v934.coverage.model.dataFile}", "model Step4 coverage: required external file changed")
    require(child_text(require_files, "message", "model Step4 coverage requireFilesExist") == "The merged Unit+all-required-IT JaCoCo exec file is required.", "model Step4 coverage: required-file message changed")

    jacoco = find_plugin(plugins, "jacoco-maven-plugin", "model Step4 coverage")
    require_child_names(
        jacoco,
        ("groupId", "artifactId", "version", "executions"),
        "model Step4 coverage JaCoCo",
    )
    require(child_text(jacoco, "groupId", "model Step4 coverage JaCoCo") == "org.jacoco", "model Step4 coverage: wrong JaCoCo groupId")
    require(child_text(jacoco, "version", "model Step4 coverage JaCoCo") == "0.8.12", "model Step4 coverage: JaCoCo version changed")
    executions = jacoco.findall("m:executions/m:execution", NS)
    execution = only(executions, "model Step4 coverage JaCoCo execution")
    require_child_names(
        execution,
        ("id", "phase", "goals", "configuration"),
        "model Step4 coverage JaCoCo execution",
    )
    require(child_text(execution, "id", "model Step4 coverage JaCoCo") == "v934-coverage-model-check", "model Step4 coverage: execution ID changed")
    require(child_text(execution, "phase", "model Step4 coverage JaCoCo") == "verify", "model Step4 coverage: check must run in verify")
    goals = [text_of(goal, "model Step4 coverage goal") for goal in execution.findall("m:goals/m:goal", NS)]
    require(goals == ["check"], "model Step4 coverage: only jacoco:check is allowed")
    config = only(execution.findall("m:configuration", NS), "model Step4 coverage check configuration")
    require_child_names(
        config,
        ("dataFile", "haltOnFailure", "rules"),
        "model Step4 coverage check configuration",
    )
    require(child_text(config, "dataFile", "model Step4 coverage check") == "${v934.coverage.model.dataFile}", "model Step4 coverage: external dataFile binding changed")
    require(child_text(config, "haltOnFailure", "model Step4 coverage check") == "true", "model Step4 coverage: haltOnFailure must be true")
    require(
        jacoco_rule_signature(
            execution,
            "model Step4 coverage check",
            ("dataFile", "haltOnFailure", "rules"),
        ) == expected_rules,
        "model Step4 coverage: inherited gates changed",
    )


def validate_reporter(repo_root: Path, reporter_pom_path: Path, frozen_modules: list[str]) -> None:
    reporter_dir = reporter_pom_path.parent
    require(not reporter_pom_path.is_symlink(), "coverage reporter POM: symlink is forbidden")
    require(not (reporter_dir / "src").exists(), "coverage reporter: src tree is forbidden")
    reporter = parse_xml(reporter_pom_path, "coverage reporter POM")
    require_child_names(
        reporter,
        (
            "modelVersion",
            "parent",
            "artifactId",
            "packaging",
            "name",
            "description",
            "properties",
            "dependencies",
            "profiles",
        ),
        "coverage reporter POM",
    )
    require(child_text(reporter, "modelVersion", "coverage reporter POM") == "4.0.0", "coverage reporter POM: modelVersion changed")
    parent = only(reporter.findall("m:parent", NS), "coverage reporter POM.parent")
    require_child_names(
        parent,
        ("groupId", "artifactId", "version", "relativePath"),
        "coverage reporter POM.parent",
    )
    require(
        child_text(parent, "groupId", "coverage reporter POM.parent") == "com.foggysource"
        and child_text(parent, "artifactId", "coverage reporter POM.parent") == "foggy-data-mcp-bridge"
        and child_text(parent, "version", "coverage reporter POM.parent") == "9.1.0.beta"
        and child_text(parent, "relativePath", "coverage reporter POM.parent") == "../../pom.xml",
        "coverage reporter POM: parent coordinates changed",
    )
    require(child_text(reporter, "artifactId", "coverage reporter POM") == REPORTER_ARTIFACT, "coverage reporter POM: artifactId changed")
    require(child_text(reporter, "packaging", "coverage reporter POM") == "pom", "coverage reporter POM: packaging must be pom")

    production_artifacts = module_artifact_ids(repo_root, frozen_modules)
    dependencies_node = only(reporter.findall("m:dependencies", NS), "coverage reporter POM.dependencies")
    dependencies = dependencies_node.findall("m:dependency", NS)
    require(len(dependencies) == 24, "coverage reporter POM: expected exactly 24 direct production dependencies")
    require_child_names(
        dependencies_node,
        tuple("dependency" for _ in range(24)),
        "coverage reporter POM.dependencies",
    )
    seen: set[str] = set()
    for number, dependency in enumerate(dependencies, 1):
        allowed_children = {f"{{{MAVEN_NS}}}groupId", f"{{{MAVEN_NS}}}artifactId", f"{{{MAVEN_NS}}}version"}
        require(set(child.tag for child in dependency) == allowed_children and len(list(dependency)) == 3, f"coverage reporter dependency {number}: only groupId/artifactId/version are allowed")
        require(child_text(dependency, "groupId", f"coverage reporter dependency {number}") == "com.foggysource", f"coverage reporter dependency {number}: groupId changed")
        artifact = child_text(dependency, "artifactId", f"coverage reporter dependency {number}")
        require(artifact not in seen, f"coverage reporter POM: duplicate dependency {artifact}")
        seen.add(artifact)
        require(child_text(dependency, "version", f"coverage reporter dependency {number}") == "${project.version}", f"coverage reporter dependency {artifact}: version must be project.version")
    require(seen == set(production_artifacts), "coverage reporter POM: dependencies must be exact frozen production module artifacts")

    properties = only(reporter.findall("m:properties", NS), "coverage reporter POM.properties")
    require_child_names(
        properties,
        ("v934.coverage.dataFileInclude", "v934.coverage.reportDirectory"),
        "coverage reporter POM.properties",
    )
    require(child_text(properties, "v934.coverage.dataFileInclude", "coverage reporter POM.properties") == "target/jacoco-aggregate.exec", "coverage reporter POM: aggregate data-file include changed")
    require(child_text(properties, "v934.coverage.reportDirectory", "coverage reporter POM.properties") == "${project.build.directory}/site/jacoco-aggregate", "coverage reporter POM: report directory changed")

    profiles_node = only(reporter.findall("m:profiles", NS), "coverage reporter POM.profiles")
    require_child_names(profiles_node, ("profile",), "coverage reporter POM.profiles")
    profiles = profiles_node.findall("m:profile", NS)
    matches = [profile for profile in profiles if child_text(profile, "id", "coverage reporter profile") == "v934-coverage-report"]
    require(len(profiles) == 1, "coverage reporter POM: only v934-coverage-report profile is allowed")
    profile = only(matches, "coverage reporter v934-coverage-report profile")
    require_child_names(profile, ("id", "build"), "coverage reporter profile")
    profile_build = only(profile.findall("m:build", NS), "coverage reporter profile.build")
    require_child_names(profile_build, ("plugins",), "coverage reporter profile.build")
    plugins = only(profile.findall("m:build/m:plugins", NS), "coverage reporter profile plugins")
    require(len(plugins.findall("m:plugin", NS)) == 1, "coverage reporter POM: only the JaCoCo reporter plugin is allowed")
    require_child_names(plugins, ("plugin",), "coverage reporter profile plugins")
    jacoco = find_plugin(plugins, "jacoco-maven-plugin", "coverage reporter profile")
    require_child_names(
        jacoco,
        ("groupId", "artifactId", "version", "executions"),
        "coverage reporter JaCoCo",
    )
    require(child_text(jacoco, "groupId", "coverage reporter JaCoCo") == "org.jacoco", "coverage reporter POM: wrong JaCoCo groupId")
    require(child_text(jacoco, "version", "coverage reporter JaCoCo") == "${jacoco-maven-plugin.version}", "coverage reporter POM: JaCoCo version must use root property")
    executions = jacoco.findall("m:executions/m:execution", NS)
    require(len(executions) == 2, "coverage reporter POM: expected exactly merge and report-aggregate executions")
    executions_node = only(jacoco.findall("m:executions", NS), "coverage reporter JaCoCo.executions")
    require_child_names(executions_node, ("execution", "execution"), "coverage reporter JaCoCo.executions")
    ids = [child_text(execution, "id", "coverage reporter execution") for execution in executions]
    require(ids == ["v934-merge-exec", "v934-report-aggregate"], "coverage reporter POM: merge must precede report-aggregate")

    merge, report = executions
    require_child_names(
        merge,
        ("id", "phase", "goals", "configuration"),
        "coverage reporter merge",
    )
    require_child_names(
        report,
        ("id", "phase", "goals", "configuration"),
        "coverage reporter report",
    )
    require(child_text(merge, "phase", "coverage reporter merge") == "generate-resources", "coverage reporter merge: phase changed")
    merge_goals = only(merge.findall("m:goals", NS), "coverage reporter merge.goals")
    require_child_names(merge_goals, ("goal",), "coverage reporter merge.goals")
    require([text_of(goal, "coverage reporter merge goal") for goal in merge_goals.findall("m:goal", NS)] == ["merge"], "coverage reporter merge: exact goal missing")
    merge_config = only(merge.findall("m:configuration", NS), "coverage reporter merge.configuration")
    require_child_names(
        merge_config,
        ("fileSets", "destFile"),
        "coverage reporter merge.configuration",
    )
    file_sets = only(merge_config.findall("m:fileSets", NS), "coverage reporter merge.fileSets")
    require_child_names(file_sets, ("fileSet",), "coverage reporter merge.fileSets")
    file_set = only(file_sets.findall("m:fileSet", NS), "coverage reporter merge.fileSet")
    require_child_names(
        file_set,
        ("directory", "includes"),
        "coverage reporter merge.fileSet",
    )
    require(child_text(file_set, "directory", "coverage reporter merge.fileSet") == "${project.build.directory}/coverage-input", "coverage reporter merge: input directory changed")
    includes = only(file_set.findall("m:includes", NS), "coverage reporter merge.includes")
    require_child_names(includes, ("include",), "coverage reporter merge.includes")
    require([text_of(item, "coverage reporter merge.include") for item in includes.findall("m:include", NS)] == ["*.exec"], "coverage reporter merge: exact exec include changed")
    require(child_text(merge_config, "destFile", "coverage reporter merge") == "${project.build.directory}/jacoco-aggregate.exec", "coverage reporter merge: destination changed")

    require(child_text(report, "phase", "coverage reporter report") == "verify", "coverage reporter report: phase changed")
    report_goals = only(report.findall("m:goals", NS), "coverage reporter report.goals")
    require_child_names(report_goals, ("goal",), "coverage reporter report.goals")
    require([text_of(goal, "coverage reporter report goal") for goal in report_goals.findall("m:goal", NS)] == ["report-aggregate"], "coverage reporter report: exact goal missing")
    report_config = only(report.findall("m:configuration", NS), "coverage reporter report.configuration")
    require_child_names(
        report_config,
        ("includeCurrentProject", "dataFileIncludes", "outputDirectory", "title"),
        "coverage reporter report.configuration",
    )
    require(child_text(report_config, "includeCurrentProject", "coverage reporter report") == "false", "coverage reporter report: current POM project must be excluded")
    data_includes = only(report_config.findall("m:dataFileIncludes", NS), "coverage reporter report.dataFileIncludes")
    require_child_names(data_includes, ("dataFileInclude",), "coverage reporter report.dataFileIncludes")
    require([text_of(item, "coverage reporter dataFileInclude") for item in data_includes.findall("m:dataFileInclude", NS)] == ["${v934.coverage.dataFileInclude}"], "coverage reporter report: aggregate exec include changed")
    require(child_text(report_config, "outputDirectory", "coverage reporter report") == "${v934.coverage.reportDirectory}", "coverage reporter report: output directory changed")
    require(child_text(report_config, "title", "coverage reporter report") == "Foggy 9.3.4 Aggregate Coverage", "coverage reporter report: title changed")

    goals = [(goal.text or "").strip() for goal in reporter.findall(".//m:goal", NS)]
    require(
        goals == ["merge", "report-aggregate"],
        "coverage reporter POM: only exact merge/report-aggregate goals are allowed",
    )

    for module in frozen_modules:
        module_dir = safe_repo_path(repo_root, module, f"production module {module}")
        for pom_path in sorted(module_dir.rglob("pom.xml")):
            relative_parts = pom_path.relative_to(module_dir).parts
            if "target" in relative_parts:
                continue
            production_pom = parse_xml(pom_path, f"production POM {pom_path.relative_to(repo_root).as_posix()}")
            dependency_artifacts = [
                (artifact.text or "").strip()
                for artifact in production_pom.findall(".//m:dependency/m:artifactId", NS)
            ]
            require(REPORTER_ARTIFACT not in dependency_artifacts, f"production POM {pom_path.relative_to(repo_root).as_posix()}: dependency on coverage reporter is forbidden")
            jacoco_plugins = [
                plugin
                for plugin in production_pom.findall(".//m:plugin", NS)
                if child_text(plugin, "artifactId", f"production POM {pom_path.relative_to(repo_root).as_posix()}")
                == "jacoco-maven-plugin"
            ]
            if pom_path.resolve() == (repo_root / "foggy-dataset-model/pom.xml").resolve():
                require(
                    len(jacoco_plugins) == 2,
                    "model production POM: expected only the validated legacy and Step4 JaCoCo plugins",
                )
            else:
                require(
                    not jacoco_plugins,
                    f"production POM {pom_path.relative_to(repo_root).as_posix()}: module-local JaCoCo plugin is forbidden",
                )


def resolve_override(repo_root: Path, value: str | None, default: str) -> Path:
    if value is None:
        return safe_repo_path(repo_root, default, default)
    candidate = Path(value).expanduser()
    if not candidate.is_absolute():
        candidate = repo_root / candidate
    return candidate.resolve(strict=False)


def tracked_source_inventory(repo_root: Path) -> tuple[bytes, int]:
    process = subprocess.run(
        ["git", "-C", str(repo_root), "ls-files", "--stage", "-z"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(process.returncode == 0, "source inventory: git ls-files failed")
    records = [record for record in process.stdout.split(b"\0") if record]
    require(records, "source inventory: tracked file set is empty")
    rows = [b"mode\tpath\tsha256\tsize\n"]
    seen: set[bytes] = set()
    for number, record in enumerate(records, 1):
        try:
            metadata, relative_bytes = record.split(b"\t", 1)
            mode_bytes, object_id, stage = metadata.split(b" ", 2)
        except ValueError as exc:
            raise ContractError(f"source inventory: malformed git row {number}") from exc
        require(stage == b"0", f"source inventory: unmerged index entry at row {number}")
        require(mode_bytes in (b"100644", b"100755"), f"source inventory: unsupported tracked mode at row {number}")
        require(re.fullmatch(b"[0-9a-f]{40,64}", object_id) is not None, f"source inventory: invalid object ID at row {number}")
        require(relative_bytes not in seen, f"source inventory: duplicate path at row {number}")
        require(
            relative_bytes
            and b"\t" not in relative_bytes
            and b"\n" not in relative_bytes
            and b"\r" not in relative_bytes
            and b"\\" not in relative_bytes,
            f"source inventory: unsafe tracked path at row {number}",
        )
        seen.add(relative_bytes)
        relative = os.fsdecode(relative_bytes)
        pure = PurePosixPath(relative)
        require(not pure.is_absolute() and ".." not in pure.parts, f"source inventory: path escapes repository at row {number}")
        path = repo_root.joinpath(*pure.parts)
        try:
            file_stat = path.lstat()
        except OSError as exc:
            raise ContractError(f"source inventory: cannot inspect {relative!r}") from exc
        require(stat.S_ISREG(file_stat.st_mode), f"source inventory: tracked path is not a regular file: {relative!r}")
        digest = hashlib.sha256()
        try:
            with path.open("rb") as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
        except OSError as exc:
            raise ContractError(f"source inventory: cannot hash {relative!r}") from exc
        rows.append(
            mode_bytes
            + b"\t"
            + relative_bytes
            + b"\t"
            + digest.hexdigest().encode("ascii")
            + b"\t"
            + str(file_stat.st_size).encode("ascii")
            + b"\n"
        )
    return b"".join(rows), len(records)


def source_hash_command(args: argparse.Namespace) -> dict[str, Any]:
    repo_root = Path(args.repo_root).expanduser().resolve(strict=True)
    require(repo_root.is_dir(), "repo root: expected directory")
    status = subprocess.run(
        ["git", "-C", str(repo_root), "status", "--porcelain=v1", "--untracked-files=all"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    require(status.returncode == 0, "source inventory: git status failed")
    require(status.stdout == b"", "source inventory: exact clean committed worktree is required")
    head_process = subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "--verify", "HEAD^{commit}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    git_head = head_process.stdout.strip().decode("ascii", errors="strict")
    require(
        head_process.returncode == 0 and re.fullmatch(r"[0-9a-f]{40}", git_head) is not None,
        "source inventory: committed HEAD identity is unavailable",
    )
    payload, count = tracked_source_inventory(repo_root)
    digest = hashlib.sha256(payload).hexdigest()
    if args.output:
        output = Path(args.output).expanduser()
        if not output.is_absolute():
            output = repo_root / output
        require(not output.exists() and not output.is_symlink(), "source inventory output: refusing overwrite")
        output.parent.mkdir(parents=True, exist_ok=True)
        require(
            output.parent.is_dir()
            and not output.parent.is_symlink()
            and output.parent.resolve(strict=True) == output.parent,
            "source inventory output: unsafe parent",
        )
        temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
        published = False
        try:
            descriptor = os.open(
                temporary,
                os.O_WRONLY
                | os.O_CREAT
                | os.O_EXCL
                | getattr(os, "O_NOFOLLOW", 0),
                0o644,
            )
            with os.fdopen(descriptor, "wb") as stream:
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            os.link(temporary, output, follow_symlinks=False)
            published = True
            temporary.unlink()
            directory_fd = os.open(
                output.parent,
                os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
            )
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        except (OSError, RuntimeError) as exc:
            temporary.unlink(missing_ok=True)
            if published:
                output.unlink(missing_ok=True)
            raise ContractError("source inventory output: atomic publish failed") from exc
    return {
        "command": "source-hash",
        "file_count": count,
        "git_head": git_head,
        "sha256": digest,
        "status": "passed",
    }


def launch_child_command(args: argparse.Namespace) -> None:
    repo_root = Path(args.repo_root).expanduser().resolve(strict=True)
    require(repo_root.is_dir(), "child launcher: repository root is not a directory")
    require(
        re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", args.run_id) is not None
        and args.run_id not in {".", ".."}
        and len(args.run_id) <= 128,
        "child launcher: unsafe run id",
    )
    child_paths = {
        "unit": "scripts/verify-v934-unit.sh",
        "integration": "scripts/verify-v934-integration.sh",
        "step3-required": "scripts/verify-v934-step3-required-matrix.sh",
    }
    relative = child_paths[args.child]
    runner = repo_root / relative
    require(
        runner.is_file() and not runner.is_symlink() and os.access(runner, os.X_OK),
        f"child launcher: runner is missing, symlinked, or not executable: {relative}",
    )
    require(args.lock_fd > 2, "child launcher: lock descriptor must exceed stderr")
    require(
        os.environ.get("V934_AUTHORITY_LOCK_FD") == str(args.lock_fd)
        and os.environ.get("V934_AUTHORITY_LOCK_MODE") == "inherited"
        and os.environ.get("V934_PARENT_AUTHORITY_KIND") == "step4-coverage"
        and os.environ.get("V934_PARENT_RUN_ID") == args.run_id,
        "child launcher: inherited authority environment differs",
    )
    git_dir_process = subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "--absolute-git-dir"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    require(git_dir_process.returncode == 0, "child launcher: cannot resolve Git directory")
    expected_lock = Path(git_dir_process.stdout.strip()) / "v934-step2-authority.lock"
    require(expected_lock.is_file() and not expected_lock.is_symlink(), "child launcher: canonical lock is missing")
    try:
        descriptor_stat = os.fstat(args.lock_fd)
        lock_stat = expected_lock.stat()
        require(
            stat.S_ISREG(descriptor_stat.st_mode)
            and stat.S_ISREG(lock_stat.st_mode)
            and (descriptor_stat.st_dev, descriptor_stat.st_ino)
            == (lock_stat.st_dev, lock_stat.st_ino),
            "child launcher: descriptor does not reference the canonical lock",
        )
        os.set_inheritable(args.lock_fd, True)
        fcntl.flock(args.lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except (BlockingIOError, OSError) as exc:
        raise ContractError("child launcher: inherited lock descriptor is unusable") from exc
    for name in ("SIGINT", "SIGTERM", "SIGHUP", "SIGPIPE", "SIGXFZ", "SIGXFSZ"):
        value = getattr(signal, name, None)
        if value is not None:
            signal.signal(value, signal.SIG_DFL)
    os.setsid()
    require(
        os.getsid(0) == os.getpid() and os.getpgrp() == os.getpid(),
        "child launcher: failed to establish a process group",
    )
    os.chdir(repo_root)
    environment = os.environ.copy()
    os.execve(str(runner), [str(runner), args.run_id], environment)


def validate_all(args: argparse.Namespace) -> dict[str, Any]:
    repo_root = Path(args.repo_root).expanduser().resolve(strict=True)
    require(repo_root.is_dir(), "repo root: expected directory")

    contract_path = resolve_override(repo_root, args.contract, "scripts/v934/step4/coverage-contract.json")
    thresholds_path = resolve_override(repo_root, args.thresholds, "scripts/v934/step4/coverage-thresholds.json")
    ledger_path = resolve_override(repo_root, args.ledger, "scripts/v934/step4/coverage-exec-ledger.tsv")
    root_pom_path = resolve_override(repo_root, args.root_pom, "pom.xml")
    model_pom_path = resolve_override(repo_root, args.model_pom, "foggy-dataset-model/pom.xml")
    reporter_pom_path = resolve_override(repo_root, args.reporter_pom, f"{REPORTER_MODULE}/pom.xml")

    contract = load_json(contract_path, "coverage contract")
    thresholds = load_json(thresholds_path, "coverage thresholds")
    validate_contract_json(contract)
    step4_manifest_path = safe_repo_path(
        repo_root,
        contract["tooling_manifest"]["path"],
        "Step 4 diagnostic tooling manifest",
    )
    step4_manifest_files = validate_step4_manifest(repo_root, step4_manifest_path)
    validate_thresholds(thresholds)
    require(contract["threshold_successor"]["required_status_for_diagnostic"] == thresholds["status"], "coverage thresholds: status disagrees with contract diagnostic status")
    parent_files = validate_parent_lineage(repo_root, contract, thresholds)
    lane_counts = validate_ledger(ledger_path, contract)
    report_amendments = validate_report_amendment(repo_root, contract)
    frozen_modules = validate_step2_module_lineage(repo_root)
    _, current_modules = validate_root_pom(root_pom_path, frozen_modules)
    validate_model_gate_profile(model_pom_path)
    validate_reporter(repo_root, reporter_pom_path, frozen_modules)

    return {
        "command": "validate-contract",
        "exec_files": contract["execution_ledger"]["exec_files"],
        "expected_sessions": contract["execution_ledger"]["expected_sessions"],
        "lane_counts": lane_counts,
        "report_amendments": report_amendments,
        "required_positive_reports": contract["report_inventory"]["required_positive_reports"],
        "required_structural_reports": contract["report_inventory"]["required_structural_reports"],
        "required_testcases": contract["report_inventory"]["required_testcases"],
        "parent_manifest_files": parent_files,
        "step4_manifest_files": step4_manifest_files,
        "production_modules": len(frozen_modules),
        "reactor_modules": len(current_modules),
        "model_gate_profiles": 2,
        "status": "passed",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Validate Foggy 9.3.4 Step 4 coverage evidence")
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate = subparsers.add_parser("validate-contract", help="validate the Step 4 bootstrap contract")
    validate.add_argument("--repo-root", required=True, help="repository root")
    validate.add_argument("--contract", help="coverage contract override (negative probes)")
    validate.add_argument("--thresholds", help="coverage threshold successor override (negative probes)")
    validate.add_argument("--ledger", help="coverage exec ledger override (negative probes)")
    validate.add_argument("--root-pom", help="root POM override (negative probes)")
    validate.add_argument("--model-pom", help="model POM override (negative probes)")
    validate.add_argument("--reporter-pom", help="reporter POM override (negative probes)")
    source_hash = subparsers.add_parser("source-hash", help="seal the exact tracked worktree bytes")
    source_hash.add_argument("--repo-root", required=True, help="repository root")
    source_hash.add_argument("--output", help="optional non-existing TSV inventory output")
    launch = subparsers.add_parser("launch-child", help="exec one canonical Step 4 authority child")
    launch.add_argument("--repo-root", required=True, help="repository root")
    launch.add_argument("--child", choices=("unit", "integration", "step3-required"), required=True)
    launch.add_argument("--run-id", required=True)
    launch.add_argument("--lock-fd", type=int, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "validate-contract":
            result = validate_all(args)
        elif args.command == "source-hash":
            result = source_hash_command(args)
        elif args.command == "launch-child":
            launch_child_command(args)
            raise ContractError("child launcher returned without exec")
        else:  # argparse makes this unreachable; keep dispatch fail-closed.
            raise ContractError(f"unsupported command {args.command!r}")
    except (ContractError, FileNotFoundError, OSError) as exc:
        output = {
            "command": getattr(args, "command", None),
            "error": str(exc) or exc.__class__.__name__,
            "status": "failed",
        }
        print(json.dumps(output, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
        return 2
    print(json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
