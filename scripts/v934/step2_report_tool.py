#!/usr/bin/env python3
"""Collect and merge fail-closed 9.3.4 Step 2 Maven report evidence.

``verify`` must run immediately after one Maven variant.  It labels that run
with the exact ``variant_key`` from the confirmed successor inventory, rejects
every stale or unexpected report in the Step 2 owning modules, and copies the
fresh raw XML into an immutable-by-convention evidence directory.

``finalize`` consumes those per-run manifests and proves that their manifest-owned
execution keys and reviewed structural containers exactly cover one runner.
Report FQCN is validated as a report identity, but is never promoted to an
execution key.  In particular, Failsafe variants must be collected separately
before later Maven runs can overwrite their owning-module
``target/failsafe-reports`` directories.

The tool intentionally does not run Maven, wait for reports, infer among
multiple variants, or accept skipped/zero-test positive suites.  A zero-test
suite is accepted only when the confirmed successor names it as a reviewed
structural container.  Its inputs are local trusted build and successor
artifacts; it does not parse untrusted XML with external entities.
"""

from __future__ import annotations

import argparse
import contextlib
import csv
import datetime as dt
import hashlib
import io
import json
import os
import re
import shutil
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence


PREFIX = "[v934-step2-report]"
STEP2_FILE = "step2-required-execution.tsv"
STRUCTURAL_FILE = "structural-report-inventory.tsv"
DISCOVERY_FILE = "discovery-inventory.tsv"
FREEZE_FILE = "contract-freeze.json"
RUNNER_FILE = "runner-contract.json"
HASH_FILE = "SHA256SUMS"
RUN_MANIFEST = "report-manifest.json"
METRICS_FILE = "report-metrics.tsv"
STRUCTURAL_METRICS_FILE = "structural-report-metrics.tsv"
TESTCASES_FILE = "testcases.tsv"
RUNNERS = ("surefire", "failsafe")
REPORT_DIRECTORIES = {
    "surefire": "surefire-reports",
    "failsafe": "failsafe-reports",
}
EXECUTION_HEADER = [
    "execution_key",
    "source_id",
    "report_fqcn",
    "runner",
    "lane",
    "variant_key",
    "db_kind",
    "infra_kind",
    "execution_step",
    "required",
    "owner",
    "optional_reason",
    "review_at",
]
STRUCTURAL_HEADER = [
    "module",
    "source_id",
    "source_fqcn",
    "report_fqcn",
    "runner",
    "lane",
    "variant_key",
    "owner",
    "discovered_test_nodes",
    "runtime_deferred_containers",
    "positive_sibling_execution_keys",
    "disposition",
    "rationale",
]
DISCOVERY_HEADER = [
    "module",
    "source_id",
    "source_fqcn",
    "report_fqcn",
    "discovered_test_nodes",
    "runtime_deferred_containers",
    "engine_ids",
    "source_sha256",
    "test_classes_sha256",
    "main_classes_sha256",
]
METRICS_HEADER = [
    "execution_key",
    "source_id",
    "report_fqcn",
    "runner",
    "variant_key",
    "module",
    "discovered_test_nodes",
    "runtime_deferred_containers",
    "cardinality_policy",
    "minimum_testcase_nodes",
    "evidence_report",
    "tests",
    "failures",
    "errors",
    "skipped",
    "testcase_nodes",
    "sha256",
]
STRUCTURAL_METRICS_HEADER = STRUCTURAL_HEADER + [
    "cardinality_policy",
    "minimum_testcase_nodes",
    "evidence_report",
    "tests",
    "failures",
    "errors",
    "skipped",
    "testcase_nodes",
    "sha256",
]
TESTCASE_HEADER = [
    "execution_key",
    "report_fqcn",
    "classname",
    "name",
]
NEGATIVE_HEADER = ["probe", "expected_error", "actual_error", "status"]
NEGATIVE_PROBES = [
    ("missing-xml", "E_MISSING_REPORT"),
    ("stale-marker", "E_STALE_REPORT"),
    ("zero-testcase", "E_ZERO_REPORT"),
    ("duplicate-fqcn", "E_DUPLICATE_FQCN"),
    ("unexpected-extra", "E_EXTRA_REPORT"),
    ("skipped", "E_REPORT_OUTCOME"),
    ("runner-overlap", "E_RUNNER_OVERLAP"),
    ("missing-structural", "E_MISSING_REPORT"),
    ("structural-nonzero", "E_STRUCTURAL_REPORT"),
    ("unexpected-structural-zero", "E_EXTRA_REPORT"),
    ("unowned-reactor-extra", "E_EXTRA_REPORT"),
    ("structural-stale", "E_STALE_REPORT"),
    ("forged-positive-mtime", "E_EVIDENCE_MTIME"),
    ("forged-structural-mtime", "E_EVIDENCE_MTIME"),
    ("cross-run-manifest-splice", "E_CROSS_RUN_SPLICE"),
    ("static-discovery-underflow", "E_DISCOVERY_CARDINALITY"),
    ("static-discovery-overflow", "E_DISCOVERY_CARDINALITY"),
    ("deferred-discovery-underflow", "E_DISCOVERY_CARDINALITY"),
    ("manifest-discovery-drift", "E_EVIDENCE_DISCOVERY"),
    ("discovery-binding-drift", "E_DISCOVERY_BINDING"),
]


class ReportError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def fail(code: str, message: str) -> None:
    raise ReportError(code, message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        fail("E_IO", f"cannot hash {path}: {exc}")
    return digest.hexdigest()


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail("E_JSON", f"cannot read {path}: {exc}")
    if not isinstance(value, dict):
        fail("E_JSON", f"JSON root is not an object: {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def read_tsv(path: Path, header: Sequence[str]) -> list[dict[str, str]]:
    try:
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            if reader.fieldnames != list(header):
                fail("E_TSV_SCHEMA", f"unexpected header in {path}: {reader.fieldnames}")
            rows = [dict(row) for row in reader]
    except OSError as exc:
        fail("E_TSV", f"cannot read {path}: {exc}")
    if any(any(value is None for value in row.values()) for row in rows):
        fail("E_TSV_SCHEMA", f"malformed row in {path}")
    return rows


def write_tsv(path: Path, header: Sequence[str], rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=list(header),
            delimiter="\t",
            lineterminator="\n",
        )
        writer.writeheader()
        for row in rows:
            writer.writerow({name: row[name] for name in header})


def safe_relative_path(value: str, label: str) -> Path:
    path = Path(value)
    if not value or path.is_absolute() or ".." in path.parts:
        fail("E_PATH", f"unsafe {label}: {value!r}")
    return path


def safe_filename_token(value: str, label: str) -> str:
    if value in {"", ".", ".."} or not re.fullmatch(r"[A-Za-z0-9._-]+", value):
        fail("E_PATH", f"unsafe {label}: {value!r}")
    return value


def ensure_within(path: Path, root: Path, label: str) -> Path:
    resolved = path.resolve()
    base = root.resolve()
    if not resolved.is_relative_to(base):
        fail("E_PATH", f"{label} escapes {base}: {path}")
    return resolved


def validate_hash_manifest(directory: Path) -> dict[str, str]:
    manifest = directory / HASH_FILE
    try:
        lines = manifest.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        fail("E_SUCCESSOR", f"cannot read successor hash manifest: {exc}")
    entries: dict[str, str] = {}
    for line in lines:
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+)", line)
        if not match:
            fail("E_SUCCESSOR_HASH", f"invalid successor hash line: {line!r}")
        digest, name = match.groups()
        if name in entries:
            fail("E_SUCCESSOR_HASH", f"duplicate successor hash entry: {name}")
        path = directory / name
        if not path.is_file() or sha256_file(path) != digest:
            fail("E_SUCCESSOR_HASH", f"missing or stale successor artifact: {name}")
        entries[name] = digest
    for required in (
        STEP2_FILE,
        STRUCTURAL_FILE,
        DISCOVERY_FILE,
        RUNNER_FILE,
        FREEZE_FILE,
    ):
        if required not in entries:
            fail("E_SUCCESSOR_HASH", f"successor hash entry is missing: {required}")
    return entries


@dataclass(frozen=True)
class SuccessorContract:
    directory: Path
    rows: tuple[dict[str, str], ...]
    structural_rows: tuple[dict[str, str], ...]
    discovery_rows: tuple[dict[str, str], ...]
    required_execution_sha256: str
    structural_report_inventory_sha256: str
    discovery_inventory_sha256: str
    runner_contract_sha256: str
    freeze_sha256: str
    hash_manifest_sha256: str
    reactor_modules: tuple[str, ...]


def load_successor(directory: Path) -> SuccessorContract:
    directory = directory.resolve()
    if not directory.is_dir():
        fail("E_SUCCESSOR", f"confirmed successor directory does not exist: {directory}")
    entries = validate_hash_manifest(directory)
    freeze = read_json(directory / FREEZE_FILE)
    if (
        freeze.get("generation") != "step2-post-rename"
        or freeze.get("status") != "confirmed"
        or freeze.get("decision") != "passed"
        or not freeze.get("reviewer")
        or not freeze.get("reviewed_at")
        or not freeze.get("independent_review_evidence")
    ):
        fail("E_SUCCESSOR_STATUS", "Step 2 successor is not confirmed/passed")
    rows = read_tsv(directory / STEP2_FILE, EXECUTION_HEADER)
    if not rows:
        fail("E_ZERO_EXPECTED", "Step 2 required execution set is empty")
    structural_rows = read_tsv(directory / STRUCTURAL_FILE, STRUCTURAL_HEADER)
    if not structural_rows:
        fail("E_ZERO_EXPECTED", "Step 2 structural report set is empty")
    discovery_rows = read_tsv(directory / DISCOVERY_FILE, DISCOVERY_HEADER)
    if not discovery_rows:
        fail("E_ZERO_EXPECTED", "discovery inventory is empty")
    runner_contract = read_json(directory / RUNNER_FILE)
    reactor_modules = runner_contract.get("reactor_modules")
    if (
        not isinstance(reactor_modules, list)
        or len(reactor_modules) != 24
        or any(not isinstance(module, str) for module in reactor_modules)
        or len(set(reactor_modules)) != 24
        or reactor_modules != sorted(reactor_modules)
    ):
        fail("E_SUCCESSOR", "runner contract must contain 24 sorted unique reactor modules")
    for module in reactor_modules:
        safe_relative_path(module, "reactor module")

    discovery_by_identity: dict[tuple[str, str], dict[str, str]] = {}
    for discovery in discovery_rows:
        identity = (discovery["source_id"], discovery["report_fqcn"])
        if not all(discovery[name] for name in DISCOVERY_HEADER):
            fail("E_DISCOVERY_BINDING", f"incomplete discovery row: {identity}")
        safe_relative_path(discovery["module"], "discovery module")
        if identity in discovery_by_identity:
            fail("E_DISCOVERY_BINDING", f"duplicate discovery identity: {identity}")
        for name in ("discovered_test_nodes", "runtime_deferred_containers"):
            if not re.fullmatch(r"[0-9]+", discovery[name]):
                fail("E_DISCOVERY_BINDING", f"invalid discovery {name}: {identity}")
        for name in ("source_sha256", "test_classes_sha256", "main_classes_sha256"):
            if not re.fullmatch(r"[0-9a-f]{64}", discovery[name]):
                fail("E_DISCOVERY_BINDING", f"invalid discovery {name}: {identity}")
        discovery_by_identity[identity] = discovery

    execution_keys: set[str] = set()
    report_owners: dict[str, str] = {}
    for row in rows:
        if row["execution_step"] != "2" or row["required"] != "true":
            fail("E_EXECUTION_SET", f"non-required Step 2 row: {row['execution_key']}")
        if row["runner"] not in RUNNERS:
            fail("E_EXECUTION_SET", f"unknown runner: {row['runner']}")
        if not all(row[name] for name in ("execution_key", "report_fqcn", "variant_key", "owner")):
            fail("E_EXECUTION_SET", f"incomplete execution row: {row}")
        safe_relative_path(row["owner"], "owning module")
        safe_filename_token(row["variant_key"], "variant key")
        if row["execution_key"] in execution_keys:
            fail("E_DUPLICATE_EXECUTION", f"duplicate execution key: {row['execution_key']}")
        execution_keys.add(row["execution_key"])
        previous = report_owners.get(row["report_fqcn"])
        if previous is not None and previous != row["runner"]:
            fail("E_RUNNER_OVERLAP", f"report belongs to both runners: {row['report_fqcn']}")
        if previous == row["runner"]:
            fail("E_DUPLICATE_FQCN", f"duplicate required report FQCN: {row['report_fqcn']}")
        report_owners[row["report_fqcn"]] = row["runner"]
        discovery = discovery_by_identity.get((row["source_id"], row["report_fqcn"]))
        if discovery is None or discovery["module"] != row["owner"]:
            fail(
                "E_DISCOVERY_BINDING",
                f"positive execution has no exact discovery binding: {row['execution_key']}",
            )
        discovered = int(discovery["discovered_test_nodes"])
        deferred = int(discovery["runtime_deferred_containers"])
        if discovered == 0 and deferred == 0:
            fail(
                "E_DISCOVERY_BINDING",
                f"positive execution binds to a structural discovery row: {row['execution_key']}",
            )

    structural_identities: set[tuple[str, str]] = set()
    structural_fqcns: set[str] = set()
    execution_by_key = {row["execution_key"]: row for row in rows}
    for row in structural_rows:
        if row["runner"] not in RUNNERS:
            fail("E_EXECUTION_SET", f"unknown structural runner: {row['runner']}")
        if not all(
            row[name]
            for name in (
                "module",
                "source_id",
                "source_fqcn",
                "report_fqcn",
                "lane",
                "variant_key",
                "owner",
                "positive_sibling_execution_keys",
                "rationale",
            )
        ):
            fail("E_EXECUTION_SET", f"incomplete structural report row: {row}")
        safe_relative_path(row["module"], "structural module")
        safe_relative_path(row["owner"], "structural owning module")
        safe_filename_token(row["variant_key"], "structural variant key")
        if row["module"] != row["owner"]:
            fail("E_EXECUTION_SET", f"structural module/owner differs: {row['report_fqcn']}")
        if row["source_fqcn"] != row["report_fqcn"]:
            fail("E_EXECUTION_SET", f"structural source/report FQCN differs: {row['report_fqcn']}")
        if (
            row["discovered_test_nodes"] != "0"
            or row["runtime_deferred_containers"] != "0"
            or row["disposition"] != "reviewed-structural-container"
        ):
            fail("E_EXECUTION_SET", f"invalid structural disposition: {row['report_fqcn']}")
        identity = (row["source_id"], row["report_fqcn"])
        if identity in structural_identities or row["report_fqcn"] in structural_fqcns:
            fail("E_DUPLICATE_FQCN", f"duplicate structural report FQCN: {row['report_fqcn']}")
        if row["report_fqcn"] in report_owners:
            fail("E_DUPLICATE_FQCN", f"positive/structural report overlap: {row['report_fqcn']}")
        structural_identities.add(identity)
        structural_fqcns.add(row["report_fqcn"])
        discovery = discovery_by_identity.get(identity)
        if (
            discovery is None
            or discovery["module"] != row["owner"]
            or discovery["source_fqcn"] != row["source_fqcn"]
            or discovery["discovered_test_nodes"] != "0"
            or discovery["runtime_deferred_containers"] != "0"
        ):
            fail(
                "E_DISCOVERY_BINDING",
                f"structural report has no exact zero discovery binding: {row['report_fqcn']}",
            )
        siblings = row["positive_sibling_execution_keys"].split(",")
        if (
            any(not key for key in siblings)
            or len(siblings) != len(set(siblings))
            or siblings != sorted(siblings)
        ):
            fail("E_EXECUTION_SET", f"invalid structural sibling keys: {row['report_fqcn']}")
        for key in siblings:
            sibling = execution_by_key.get(key)
            if sibling is None:
                fail("E_EXECUTION_SET", f"unknown structural sibling execution key: {key}")
            if any(
                sibling[name] != row[name]
                for name in ("source_id", "runner", "lane", "variant_key", "owner")
            ) or not sibling["report_fqcn"].startswith(row["report_fqcn"] + "$"):
                fail("E_EXECUTION_SET", f"structural sibling identity differs: {key}")

    return SuccessorContract(
        directory=directory,
        rows=tuple(rows),
        structural_rows=tuple(structural_rows),
        discovery_rows=tuple(discovery_rows),
        required_execution_sha256=entries[STEP2_FILE],
        structural_report_inventory_sha256=entries[STRUCTURAL_FILE],
        discovery_inventory_sha256=entries[DISCOVERY_FILE],
        runner_contract_sha256=entries[RUNNER_FILE],
        freeze_sha256=entries[FREEZE_FILE],
        hash_manifest_sha256=sha256_file(directory / HASH_FILE),
        reactor_modules=tuple(reactor_modules),
    )


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def integer_attribute(element: ET.Element, name: str, path: Path) -> int:
    value = element.attrib.get(name)
    if value is None or not re.fullmatch(r"[0-9]+", value):
        fail("E_REPORT_METRICS", f"invalid {name} attribute in {path}: {value!r}")
    return int(value)


@dataclass(frozen=True)
class ReportMetrics:
    fqcn: str
    tests: int
    failures: int
    errors: int
    skipped: int
    testcase_nodes: int
    sha256: str
    testcases: tuple[tuple[str, str], ...]


def report_fqcn(path: Path) -> str:
    name = path.name
    if not name.startswith("TEST-") or not name.endswith(".xml"):
        fail("E_REPORT_NAME", f"non-canonical Maven report name: {name}")
    fqcn = name[len("TEST-") : -len(".xml")]
    if not fqcn:
        fail("E_REPORT_NAME", f"empty report FQCN: {path}")
    return fqcn


def parse_report(path: Path, *, structural: bool = False) -> ReportMetrics:
    fqcn = report_fqcn(path)
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as exc:
        fail("E_XML", f"cannot parse {path}: {exc}")
    root_name = local_name(root.tag)
    if root_name not in {"testsuite", "testsuites"}:
        fail("E_XML", f"unexpected XML root {root_name!r}: {path}")
    suites = [element for element in root.iter() if local_name(element.tag) == "testsuite"]
    if not suites:
        fail("E_XML", f"testsuite element is missing: {path}")
    suite_names = {suite.attrib.get("name", "") for suite in suites}
    if suite_names != {fqcn}:
        fail("E_REPORT_FQCN", f"suite names {sorted(suite_names)} do not own {fqcn}: {path}")

    totals = {
        name: sum(integer_attribute(suite, name, path) for suite in suites)
        for name in ("tests", "failures", "errors", "skipped")
    }
    testcase_elements = [
        element for element in root.iter() if local_name(element.tag) == "testcase"
    ]
    outcome_nodes = {
        name: sum(1 for element in root.iter() if local_name(element.tag) == name)
        for name in ("failure", "error", "skipped")
    }
    if totals["tests"] != len(testcase_elements):
        fail(
            "E_REPORT_COUNT",
            f"suite tests={totals['tests']} testcase nodes={len(testcase_elements)}: {path}",
        )
    if (
        totals["failures"] != outcome_nodes["failure"]
        or totals["errors"] != outcome_nodes["error"]
        or totals["skipped"] != outcome_nodes["skipped"]
    ):
        fail("E_REPORT_COUNT", f"suite outcome metrics differ from outcome nodes: {path}")
    if any(totals[name] != 0 for name in ("failures", "errors", "skipped")):
        fail(
            "E_REPORT_OUTCOME",
            "report is not green "
            f"failures={totals['failures']} errors={totals['errors']} "
            f"skipped={totals['skipped']}: {path}",
        )
    if root_name == "testsuites":
        for name in ("tests", "failures", "errors", "skipped"):
            value = root.attrib.get(name)
            if value is not None:
                if not re.fullmatch(r"[0-9]+", value) or int(value) != totals[name]:
                    fail("E_REPORT_COUNT", f"testsuites {name} aggregate differs: {path}")

    if structural:
        if totals["tests"] != 0 or testcase_elements:
            fail(
                "E_STRUCTURAL_REPORT",
                f"reviewed structural report must contain exactly zero tests: {path}",
            )
    elif totals["tests"] == 0:
        fail("E_ZERO_REPORT", f"zero-test report cannot satisfy positive Step 2 execution: {path}")

    testcases = tuple(
        (
            element.attrib.get("classname", ""),
            element.attrib.get("name", ""),
        )
        for element in testcase_elements
    )
    if any(not classname or not test_name for classname, test_name in testcases):
        fail("E_TESTCASE_IDENTITY", f"testcase classname/name is missing: {path}")
    return ReportMetrics(
        fqcn=fqcn,
        tests=totals["tests"],
        failures=totals["failures"],
        errors=totals["errors"],
        skipped=totals["skipped"],
        testcase_nodes=len(testcase_elements),
        sha256=sha256_file(path),
        testcases=testcases,
    )


def report_row(
    contract: SuccessorContract,
    expected: dict[str, str],
    metrics: ReportMetrics,
    source_report: str,
    evidence_report: str,
    source_mtime_ns: int,
) -> dict[str, Any]:
    cardinality = positive_cardinality_evidence(
        contract,
        expected,
        metrics.testcase_nodes,
    )
    return {
        "execution_key": expected["execution_key"],
        **cardinality,
        "report_fqcn": expected["report_fqcn"],
        "runner": expected["runner"],
        "execution_lane": expected["lane"],
        "variant_key": expected["variant_key"],
        "module": expected["owner"],
        "db_kind": expected["db_kind"],
        "infra_kind": expected["infra_kind"],
        "source_report": source_report,
        "source_mtime_ns": source_mtime_ns,
        "evidence_report": evidence_report,
        "tests": metrics.tests,
        "failures": metrics.failures,
        "errors": metrics.errors,
        "skipped": metrics.skipped,
        "testcase_nodes": metrics.testcase_nodes,
        "sha256": metrics.sha256,
    }


def structural_report_row(
    expected: dict[str, str],
    metrics: ReportMetrics,
    source_report: str,
    evidence_report: str,
    source_mtime_ns: int,
) -> dict[str, Any]:
    return {
        **{name: expected[name] for name in STRUCTURAL_HEADER},
        "cardinality_policy": "structural-zero-v1",
        "minimum_testcase_nodes": 0,
        "source_report": source_report,
        "source_mtime_ns": source_mtime_ns,
        "evidence_report": evidence_report,
        "tests": metrics.tests,
        "failures": metrics.failures,
        "errors": metrics.errors,
        "skipped": metrics.skipped,
        "testcase_nodes": metrics.testcase_nodes,
        "sha256": metrics.sha256,
    }


def sanitize_tsv(value: str) -> str:
    return value.replace("\t", " ").replace("\r", " ").replace("\n", " ")


def write_evidence_tables(
    output: Path,
    reports: Sequence[dict[str, Any]],
    structural_reports: Sequence[dict[str, Any]],
    testcase_rows: Sequence[dict[str, str]],
) -> None:
    write_tsv(output / METRICS_FILE, METRICS_HEADER, reports)
    write_tsv(
        output / STRUCTURAL_METRICS_FILE,
        STRUCTURAL_METRICS_HEADER,
        structural_reports,
    )
    write_tsv(output / TESTCASES_FILE, TESTCASE_HEADER, testcase_rows)


def prepare_output(output: Path) -> Path:
    output = output.resolve()
    if output.exists():
        fail("E_OUTPUT", f"evidence output already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    return Path(tempfile.mkdtemp(prefix=f".{output.name}.", dir=output.parent))


def publish_output(temporary: Path, output: Path) -> None:
    try:
        os.replace(temporary, output.resolve())
    except OSError as exc:
        fail("E_OUTPUT", f"cannot publish evidence output {output}: {exc}")


def remove_temporary(temporary: Path) -> None:
    if temporary.exists():
        shutil.rmtree(temporary, ignore_errors=True)


def source_label(path: Path, root: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(root.resolve()).as_posix()
    except ValueError:
        return str(resolved)


def contract_identity(contract: SuccessorContract) -> dict[str, str]:
    return {
        "required_execution_sha256": contract.required_execution_sha256,
        "structural_report_inventory_sha256": contract.structural_report_inventory_sha256,
        "discovery_inventory_sha256": contract.discovery_inventory_sha256,
        "runner_contract_sha256": contract.runner_contract_sha256,
        "contract_freeze_sha256": contract.freeze_sha256,
        "hash_manifest_sha256": contract.hash_manifest_sha256,
    }


def discovery_binding(
    contract: SuccessorContract,
    expected: dict[str, str],
) -> dict[str, str]:
    identity = (expected["source_id"], expected["report_fqcn"])
    matches = [
        row
        for row in contract.discovery_rows
        if (row["source_id"], row["report_fqcn"]) == identity
    ]
    if len(matches) != 1 or matches[0]["module"] != expected["owner"]:
        fail("E_DISCOVERY_BINDING", f"no exact frozen discovery binding: {identity}")
    return matches[0]


def positive_cardinality_evidence(
    contract: SuccessorContract,
    expected: dict[str, str],
    actual_testcase_nodes: int,
) -> dict[str, Any]:
    discovery = discovery_binding(contract, expected)
    discovered = int(discovery["discovered_test_nodes"])
    deferred = int(discovery["runtime_deferred_containers"])
    if discovered == 0 and deferred == 0:
        fail(
            "E_DISCOVERY_BINDING",
            f"positive execution has a zero/zero discovery binding: {expected['execution_key']}",
        )
    if deferred == 0:
        policy = "static-exact-v1"
        minimum = discovered
        valid = actual_testcase_nodes == discovered
        relation = "=="
    else:
        policy = "runtime-deferred-min-v1"
        minimum = discovered + deferred
        valid = actual_testcase_nodes >= minimum
        relation = ">="
    if not valid:
        fail(
            "E_DISCOVERY_CARDINALITY",
            f"{expected['execution_key']} testcase_nodes={actual_testcase_nodes} must be "
            f"{relation}{minimum} (discovered={discovered}, deferred={deferred})",
        )
    return {
        "source_id": expected["source_id"],
        "discovered_test_nodes": discovered,
        "runtime_deferred_containers": deferred,
        "cardinality_policy": policy,
        "minimum_testcase_nodes": minimum,
    }


@dataclass(frozen=True)
class OuterRun:
    context: dict[str, Any]
    marker: Path
    run_root: Path
    sha256: str
    mtime_ns: int


@dataclass(frozen=True)
class VariantRun:
    context: dict[str, Any]
    marker: Path
    sha256: str
    mtime_ns: int


def require_exact_json_fields(
    value: dict[str, Any],
    expected: set[str],
    code: str,
    label: str,
) -> None:
    if set(value) != expected:
        fail(
            code,
            f"{label} fields differ missing={sorted(expected - set(value))} "
            f"extra={sorted(set(value) - expected)}",
        )


def validate_outer_marker(
    marker_path: Path,
    contract: SuccessorContract,
    runner: str,
) -> OuterRun:
    marker_path = marker_path.resolve()
    if marker_path.is_symlink() or not marker_path.is_file():
        fail("E_OUTER_RUN", f"outer marker is not a regular file: {marker_path}")
    context = read_json(marker_path)
    require_exact_json_fields(
        context,
        {
            "schema_version",
            "kind",
            "run_id",
            "runner",
            "git_head",
            "source_before_sha256",
            "started_at",
            "status",
            "successor",
        },
        "E_OUTER_RUN",
        "outer marker",
    )
    run_id = context.get("run_id")
    git_head = context.get("git_head")
    source_before = context.get("source_before_sha256")
    if (
        context.get("schema_version") != 1
        or context.get("kind") != "v934-step2-outer-run"
        or context.get("runner") != runner
        or context.get("status") != "started"
        or not isinstance(run_id, str)
        or not re.fullmatch(r"[A-Za-z0-9._-]+", run_id)
        or run_id in {".", ".."}
        or not isinstance(git_head, str)
        or not re.fullmatch(r"(?:[0-9a-f]{40}|[0-9a-f]{64})", git_head)
        or not isinstance(source_before, str)
        or not re.fullmatch(r"[0-9a-f]{64}", source_before)
        or not isinstance(context.get("started_at"), str)
        or not context["started_at"]
        or context.get("successor") != contract_identity(contract)
    ):
        fail("E_OUTER_RUN", f"invalid or mismatched outer marker: {marker_path}")
    return OuterRun(
        context=context,
        marker=marker_path,
        run_root=marker_path.parent.resolve(),
        sha256=sha256_file(marker_path),
        mtime_ns=marker_path.stat().st_mtime_ns,
    )


def require_in_run_root(path: Path, outer: OuterRun, label: str) -> Path:
    resolved = path.resolve()
    if resolved == outer.run_root or not resolved.is_relative_to(outer.run_root):
        fail("E_RUN_ROOT", f"{label} is outside outer parent run root: {resolved}")
    return resolved


def validate_variant_marker(
    marker_path: Path,
    outer: OuterRun,
    variant_key: str,
    *,
    evidence: bool = False,
) -> VariantRun:
    marker_path = require_in_run_root(marker_path, outer, "variant marker")
    code = "E_CROSS_RUN_SPLICE" if evidence else "E_VARIANT_MARKER"
    if marker_path.is_symlink() or not marker_path.is_file():
        fail(code, f"variant marker is not a regular file: {marker_path}")
    context = read_json(marker_path)
    require_exact_json_fields(
        context,
        {
            "schema_version",
            "kind",
            "run_id",
            "runner",
            "variant_key",
            "outer_marker_sha256",
            "started_at",
            "status",
        },
        code,
        "variant marker",
    )
    if (
        context.get("schema_version") != 1
        or context.get("kind") != "v934-step2-variant-run"
        or context.get("run_id") != outer.context["run_id"]
        or context.get("runner") != outer.context["runner"]
        or context.get("variant_key") != variant_key
        or context.get("outer_marker_sha256") != outer.sha256
        or context.get("status") != "started"
        or not isinstance(context.get("started_at"), str)
        or not context["started_at"]
    ):
        fail(code, f"variant marker tuple differs from outer run: {marker_path}")
    mtime_ns = marker_path.stat().st_mtime_ns
    if mtime_ns < outer.mtime_ns:
        fail(code, f"variant marker predates outer marker: {marker_path}")
    return VariantRun(
        context=context,
        marker=marker_path,
        sha256=sha256_file(marker_path),
        mtime_ns=mtime_ns,
    )


def marker_record(
    marker: OuterRun | VariantRun,
    evidence_path: Path,
) -> dict[str, Any]:
    return {
        "context": marker.context,
        "evidence_path": evidence_path.as_posix(),
        "mtime_ns": marker.mtime_ns,
        "sha256": marker.sha256,
    }


def collect(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    raw_root = (args.raw_root or root).resolve()
    output = args.evidence_output.resolve()
    contract = load_successor(args.successor_dir)
    outer = validate_outer_marker(args.outer_marker, contract, args.lane)
    require_in_run_root(output, outer, "evidence output")
    if not root.is_dir():
        fail("E_ROOT", f"workspace root does not exist: {root}")
    if not raw_root.is_dir():
        fail("E_ROOT", f"raw report root does not exist: {raw_root}")
    runner_rows = [row for row in contract.rows if row["runner"] == args.lane]
    runner_structural_rows = [
        row for row in contract.structural_rows if row["runner"] == args.lane
    ]
    runner_variants = sorted(
        {row["variant_key"] for row in runner_rows + runner_structural_rows}
    )
    if not runner_variants:
        fail("E_ZERO_EXPECTED", f"runner has no Step 2 reports: {args.lane}")
    variant_key = args.variant_key
    if variant_key is None:
        if len(runner_variants) != 1:
            fail(
                "E_VARIANT",
                f"--variant-key is required for {args.lane}; expected one of {runner_variants}",
            )
        variant_key = runner_variants[0]
    expected_rows = [row for row in runner_rows if row["variant_key"] == variant_key]
    expected_structural_rows = [
        row for row in runner_structural_rows if row["variant_key"] == variant_key
    ]
    if not expected_rows:
        fail(
            "E_VARIANT",
            f"variant {variant_key!r} has no positive {args.lane} executions; "
            f"expected one of {runner_variants}",
        )
    variant_marker = validate_variant_marker(
        args.run_marker,
        outer,
        variant_key,
    )
    marker = variant_marker.marker
    marker_mtime_ns = variant_marker.mtime_ns
    expected_by_fqcn = {row["report_fqcn"]: row for row in expected_rows}
    if len(expected_by_fqcn) != len(expected_rows):
        fail("E_DUPLICATE_FQCN", f"variant has duplicate report FQCN: {variant_key}")
    structural_by_fqcn = {
        row["report_fqcn"]: row for row in expected_structural_rows
    }
    if len(structural_by_fqcn) != len(expected_structural_rows):
        fail("E_DUPLICATE_FQCN", f"variant has duplicate structural FQCN: {variant_key}")
    expected_fqcns = set(expected_by_fqcn) | set(structural_by_fqcn)

    all_contract_rows = list(contract.rows) + list(contract.structural_rows)
    contract_owners = {row["owner"] for row in all_contract_rows}
    all_modules = list(contract.reactor_modules)
    if not contract_owners.issubset(all_modules):
        fail("E_EXECUTION_SET", "report owner is outside the active reactor module contract")
    observed: dict[str, tuple[Path, str]] = {}
    other_runner = {
        row["report_fqcn"]: row["runner"]
        for row in all_contract_rows
        if row["runner"] != args.lane
    }
    for module in all_modules:
        module_path = ensure_within(
            raw_root / safe_relative_path(module, "owning module"),
            raw_root,
            "raw owning module",
        )
        report_dir = module_path / "target" / REPORT_DIRECTORIES[args.lane]
        if not report_dir.is_dir():
            continue
        for path in sorted(report_dir.glob("TEST-*.xml")):
            if path.is_symlink() or not path.is_file():
                fail("E_REPORT_PATH", f"report is not a regular file: {path}")
            if path.stat().st_mtime_ns <= marker_mtime_ns:
                fail("E_STALE_REPORT", f"report is not newer than run marker: {path}")
            fqcn = report_fqcn(path)
            if fqcn in observed:
                fail("E_DUPLICATE_FQCN", f"duplicate fresh report FQCN: {fqcn}")
            if fqcn in other_runner:
                fail(
                    "E_RUNNER_OVERLAP",
                    f"{fqcn} appeared in {args.lane} but belongs to {other_runner[fqcn]}",
                )
            observed[fqcn] = (path, module)

    if not observed:
        fail("E_MISSING_REPORT", f"no fresh {args.lane} TEST-*.xml reports were found")
    missing = sorted(expected_fqcns - set(observed))
    extra = sorted(set(observed) - expected_fqcns)
    if missing:
        fail("E_MISSING_REPORT", f"missing expected reports: {missing[:10]}")
    if extra:
        fail("E_EXTRA_REPORT", f"unexpected fresh reports: {extra[:10]}")

    temporary = prepare_output(output)
    try:
        outer_marker_relative = Path("outer-run-marker.json")
        variant_marker_relative = Path("variant-run-marker.json")
        shutil.copy2(outer.marker, temporary / outer_marker_relative)
        shutil.copy2(marker, temporary / variant_marker_relative)
        report_rows: list[dict[str, Any]] = []
        structural_report_rows: list[dict[str, Any]] = []
        testcase_rows: list[dict[str, str]] = []
        for fqcn, expected in sorted(expected_by_fqcn.items(), key=lambda item: item[1]["execution_key"]):
            source, module = observed[fqcn]
            if module != expected["owner"]:
                fail(
                    "E_REPORT_OWNER",
                    f"{fqcn} came from module {module}, expected {expected['owner']}",
                )
            metrics = parse_report(source)
            relative = Path("raw-reports") / safe_relative_path(module, "owning module") / source.name
            target = temporary / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            row = report_row(
                contract,
                expected,
                metrics,
                source_label(source, raw_root),
                relative.as_posix(),
                source.stat().st_mtime_ns,
            )
            report_rows.append(row)
            for classname, test_name in metrics.testcases:
                testcase_rows.append({
                    "execution_key": expected["execution_key"],
                    "report_fqcn": fqcn,
                    "classname": sanitize_tsv(classname),
                    "name": sanitize_tsv(test_name),
                })
        for fqcn, expected in sorted(structural_by_fqcn.items()):
            source, module = observed[fqcn]
            if module != expected["owner"]:
                fail(
                    "E_REPORT_OWNER",
                    f"structural {fqcn} came from module {module}, expected {expected['owner']}",
                )
            metrics = parse_report(source, structural=True)
            relative = (
                Path("structural-reports")
                / safe_relative_path(module, "structural owning module")
                / source.name
            )
            target = temporary / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            structural_report_rows.append(
                structural_report_row(
                    expected,
                    metrics,
                    source_label(source, raw_root),
                    relative.as_posix(),
                    source.stat().st_mtime_ns,
                )
            )
        totals = {
            name: sum(int(row[name]) for row in report_rows)
            for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
        }
        structural_totals = {
            name: sum(int(row[name]) for row in structural_report_rows)
            for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
        }
        manifest = {
            "schema_version": 3,
            "kind": "v934-step2-report-run",
            "status": "passed",
            "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "successor": contract_identity(contract),
            "runner": args.lane,
            "variant_key": variant_key,
            "raw_root": str(raw_root),
            "outer_run": marker_record(outer, outer_marker_relative),
            "variant_marker": marker_record(
                variant_marker,
                variant_marker_relative,
            ),
            "expected_execution_count": len(expected_rows),
            "expected_structural_report_count": len(expected_structural_rows),
            "report_count": len(report_rows),
            "structural_report_count": len(structural_report_rows),
            "raw_report_count": len(report_rows) + len(structural_report_rows),
            "totals": totals,
            "structural_totals": structural_totals,
            "reports": report_rows,
            "structural_reports": structural_report_rows,
        }
        write_evidence_tables(
            temporary,
            report_rows,
            structural_report_rows,
            testcase_rows,
        )
        write_json(temporary / RUN_MANIFEST, manifest)
        publish_output(temporary, output)
    except Exception:
        remove_temporary(temporary)
        raise
    print(
        f"{PREFIX} PASS collect runner={args.lane} variant={variant_key} "
        f"executions={len(expected_rows)} structural={len(expected_structural_rows)} "
        f"tests={totals['tests']} output={output}"
    )


def manifest_report_rows(manifest: dict[str, Any], path: Path) -> list[dict[str, Any]]:
    reports = manifest.get("reports")
    if not isinstance(reports, list) or any(not isinstance(row, dict) for row in reports):
        fail("E_EVIDENCE_SCHEMA", f"reports is not an object list: {path}")
    return reports


def manifest_structural_report_rows(
    manifest: dict[str, Any],
    path: Path,
) -> list[dict[str, Any]]:
    reports = manifest.get("structural_reports")
    if not isinstance(reports, list) or any(not isinstance(row, dict) for row in reports):
        fail("E_EVIDENCE_SCHEMA", f"structural_reports is not an object list: {path}")
    return reports


@dataclass(frozen=True)
class ValidatedRun:
    variant: str
    reports: tuple[tuple[dict[str, str], dict[str, Any], ReportMetrics, Path], ...]
    structural_reports: tuple[
        tuple[dict[str, str], dict[str, Any], ReportMetrics, Path], ...
    ]
    variant_marker: Path


def evidence_marker_path(
    record: Any,
    evidence_root: Path,
    label: str,
) -> Path:
    if not isinstance(record, dict):
        fail("E_EVIDENCE_SCHEMA", f"{label} evidence is not an object")
    require_exact_json_fields(
        record,
        {"context", "evidence_path", "mtime_ns", "sha256"},
        "E_EVIDENCE_SCHEMA",
        f"{label} evidence",
    )
    if (
        not isinstance(record.get("context"), dict)
        or not isinstance(record.get("mtime_ns"), int)
        or not isinstance(record.get("sha256"), str)
        or not re.fullmatch(r"[0-9a-f]{64}", record["sha256"])
    ):
        fail("E_EVIDENCE_SCHEMA", f"{label} evidence metadata is incomplete")
    relative = safe_relative_path(str(record.get("evidence_path", "")), label)
    raw = ensure_within(evidence_root / relative, evidence_root, label)
    if raw.is_symlink() or not raw.is_file():
        fail("E_EVIDENCE_MARKER", f"{label} evidence is missing: {raw}")
    if (
        raw.stat().st_mtime_ns != record["mtime_ns"]
        or sha256_file(raw) != record["sha256"]
        or read_json(raw) != record["context"]
    ):
        fail("E_EVIDENCE_MARKER", f"{label} evidence differs from manifest: {raw}")
    return raw


def validate_run_manifest(
    manifest_path: Path,
    contract: SuccessorContract,
    runner: str,
    outer: OuterRun,
) -> ValidatedRun:
    manifest_path = require_in_run_root(manifest_path, outer, "run manifest")
    manifest = read_json(manifest_path)
    if (
        manifest.get("schema_version") != 3
        or manifest.get("kind") != "v934-step2-report-run"
        or manifest.get("status") != "passed"
    ):
        fail("E_EVIDENCE_SCHEMA", f"not a passed Step 2 run manifest: {manifest_path}")
    if manifest.get("successor") != contract_identity(contract):
        fail("E_EVIDENCE_SUCCESSOR", f"manifest belongs to a different successor: {manifest_path}")
    evidence_root = manifest_path.parent.resolve()
    outer_record = manifest.get("outer_run")
    outer_raw = evidence_marker_path(outer_record, evidence_root, "outer run marker")
    if (
        outer_record["context"] != outer.context
        or outer_record["sha256"] != outer.sha256
        or outer_record["mtime_ns"] != outer.mtime_ns
    ):
        fail("E_CROSS_RUN_SPLICE", f"manifest outer tuple differs: {manifest_path}")
    if outer_raw.read_bytes() != outer.marker.read_bytes():
        fail("E_CROSS_RUN_SPLICE", f"manifest outer marker differs: {manifest_path}")
    manifest_runner = manifest.get("runner")
    if manifest_runner != runner or manifest_runner != outer.context["runner"]:
        fail("E_CROSS_RUN_SPLICE", f"manifest runner differs from outer run: {manifest_path}")
    variant = manifest.get("variant_key")
    if not isinstance(variant, str) or not variant:
        fail("E_EVIDENCE_SCHEMA", f"manifest variant_key is missing: {manifest_path}")
    expected_rows = [
        row for row in contract.rows
        if row["runner"] == runner and row["variant_key"] == variant
    ]
    expected_structural_rows = [
        row for row in contract.structural_rows
        if row["runner"] == runner and row["variant_key"] == variant
    ]
    if not expected_rows:
        fail("E_VARIANT", f"manifest variant is not expected: {variant}")
    variant_record = manifest.get("variant_marker")
    variant_raw = evidence_marker_path(
        variant_record,
        evidence_root,
        "variant run marker",
    )
    validated_variant = validate_variant_marker(
        variant_raw,
        outer,
        variant,
        evidence=True,
    )
    if (
        variant_record["context"] != validated_variant.context
        or variant_record["sha256"] != validated_variant.sha256
        or variant_record["mtime_ns"] != validated_variant.mtime_ns
    ):
        fail("E_CROSS_RUN_SPLICE", f"manifest variant tuple differs: {manifest_path}")
    expected_by_key = {row["execution_key"]: row for row in expected_rows}
    expected_structural_by_fqcn = {
        row["report_fqcn"]: row for row in expected_structural_rows
    }
    reports = manifest_report_rows(manifest, manifest_path)
    structural_reports = manifest_structural_report_rows(manifest, manifest_path)
    if manifest.get("expected_execution_count") != len(expected_rows):
        fail("E_EVIDENCE_SCHEMA", f"manifest expected count differs: {manifest_path}")
    if manifest.get("report_count") != len(reports):
        fail("E_EVIDENCE_SCHEMA", f"manifest report count differs: {manifest_path}")
    if manifest.get("expected_structural_report_count") != len(expected_structural_rows):
        fail("E_EVIDENCE_SCHEMA", f"manifest structural expected count differs: {manifest_path}")
    if manifest.get("structural_report_count") != len(structural_reports):
        fail("E_EVIDENCE_SCHEMA", f"manifest structural report count differs: {manifest_path}")
    if manifest.get("raw_report_count") != len(reports) + len(structural_reports):
        fail("E_EVIDENCE_SCHEMA", f"manifest raw report count differs: {manifest_path}")

    observed_keys: set[str] = set()
    observed_fqcns: set[str] = set()
    referenced_raw_reports: set[Path] = set()
    result: list[tuple[dict[str, str], dict[str, Any], ReportMetrics, Path]] = []
    structural_result: list[
        tuple[dict[str, str], dict[str, Any], ReportMetrics, Path]
    ] = []
    for observed in reports:
        key = observed.get("execution_key")
        if not isinstance(key, str) or key in observed_keys:
            fail("E_DUPLICATE_EXECUTION", f"duplicate/invalid evidence execution key: {key!r}")
        observed_keys.add(key)
        expected = expected_by_key.get(key)
        if expected is None:
            fail("E_EXTRA_REPORT", f"unexpected evidence execution key: {key}")
        if observed.get("source_id") != expected["source_id"]:
            fail("E_DISCOVERY_BINDING", f"manifest source_id differs for execution key: {key}")
        exact = {
            "execution_key": expected["execution_key"],
            "report_fqcn": expected["report_fqcn"],
            "runner": expected["runner"],
            "execution_lane": expected["lane"],
            "variant_key": expected["variant_key"],
            "module": expected["owner"],
            "db_kind": expected["db_kind"],
            "infra_kind": expected["infra_kind"],
        }
        if any(observed.get(name) != value for name, value in exact.items()):
            fail("E_EVIDENCE_IDENTITY", f"manifest identity differs for execution key: {key}")
        fqcn = expected["report_fqcn"]
        if fqcn in observed_fqcns:
            fail("E_DUPLICATE_FQCN", f"duplicate FQCN in run evidence: {fqcn}")
        observed_fqcns.add(fqcn)
        relative = safe_relative_path(str(observed.get("evidence_report", "")), "evidence report")
        raw = ensure_within(evidence_root / relative, evidence_root, "evidence report")
        if raw.is_symlink() or not raw.is_file():
            fail("E_EVIDENCE_REPORT", f"raw evidence report is missing: {raw}")
        if raw in referenced_raw_reports:
            fail("E_DUPLICATE_FQCN", f"raw evidence report is referenced twice: {raw}")
        referenced_raw_reports.add(raw)
        metrics = parse_report(raw)
        cardinality = positive_cardinality_evidence(
            contract,
            expected,
            metrics.testcase_nodes,
        )
        if any(observed.get(name) != value for name, value in cardinality.items()):
            fail("E_EVIDENCE_DISCOVERY", f"manifest discovery evidence differs: {key}")
        metric_values = {
            "report_fqcn": metrics.fqcn,
            "tests": metrics.tests,
            "failures": metrics.failures,
            "errors": metrics.errors,
            "skipped": metrics.skipped,
            "testcase_nodes": metrics.testcase_nodes,
            "sha256": metrics.sha256,
        }
        if any(observed.get(name) != value for name, value in metric_values.items()):
            fail("E_EVIDENCE_REPORT", f"raw report differs from manifest: {raw}")
        source_mtime = observed.get("source_mtime_ns")
        if not isinstance(source_mtime, int):
            fail("E_EVIDENCE_SCHEMA", f"manifest report mtime is invalid: {raw}")
        if source_mtime != raw.stat().st_mtime_ns:
            fail("E_EVIDENCE_MTIME", f"manifest report mtime differs from raw XML: {raw}")
        if source_mtime <= validated_variant.mtime_ns:
            fail("E_STALE_REPORT", f"manifest carries a stale report: {raw}")
        result.append((expected, observed, metrics, raw))
    missing = sorted(set(expected_by_key) - observed_keys)
    if missing:
        fail("E_MISSING_REPORT", f"run evidence is missing execution keys: {missing[:10]}")
    if not result:
        fail("E_ZERO_REPORT", f"run evidence contains no reports: {manifest_path}")
    observed_structural_fqcns: set[str] = set()
    for observed in structural_reports:
        fqcn = observed.get("report_fqcn")
        if not isinstance(fqcn, str) or fqcn in observed_structural_fqcns:
            fail("E_DUPLICATE_FQCN", f"duplicate/invalid structural evidence FQCN: {fqcn!r}")
        if fqcn in observed_fqcns:
            fail("E_DUPLICATE_FQCN", f"positive/structural evidence overlap: {fqcn}")
        observed_structural_fqcns.add(fqcn)
        expected = expected_structural_by_fqcn.get(fqcn)
        if expected is None:
            fail("E_EXTRA_REPORT", f"unexpected structural evidence report: {fqcn}")
        if observed.get("source_id") != expected["source_id"]:
            fail("E_DISCOVERY_BINDING", f"structural source_id differs: {fqcn}")
        if any(observed.get(name) != expected[name] for name in STRUCTURAL_HEADER):
            fail("E_EVIDENCE_IDENTITY", f"structural manifest identity differs: {fqcn}")
        discovery = discovery_binding(contract, expected)
        if (
            discovery["discovered_test_nodes"] != "0"
            or discovery["runtime_deferred_containers"] != "0"
        ):
            fail("E_DISCOVERY_BINDING", f"structural discovery binding is nonzero: {fqcn}")
        if (
            observed.get("cardinality_policy") != "structural-zero-v1"
            or observed.get("minimum_testcase_nodes") != 0
        ):
            fail("E_EVIDENCE_DISCOVERY", f"structural discovery evidence differs: {fqcn}")
        relative = safe_relative_path(
            str(observed.get("evidence_report", "")),
            "structural evidence report",
        )
        raw = ensure_within(
            evidence_root / relative,
            evidence_root,
            "structural evidence report",
        )
        if raw.is_symlink() or not raw.is_file():
            fail("E_EVIDENCE_REPORT", f"raw structural evidence report is missing: {raw}")
        if raw in referenced_raw_reports:
            fail("E_DUPLICATE_FQCN", f"raw evidence report is referenced twice: {raw}")
        referenced_raw_reports.add(raw)
        metrics = parse_report(raw, structural=True)
        metric_values = {
            "report_fqcn": metrics.fqcn,
            "tests": metrics.tests,
            "failures": metrics.failures,
            "errors": metrics.errors,
            "skipped": metrics.skipped,
            "testcase_nodes": metrics.testcase_nodes,
            "sha256": metrics.sha256,
        }
        if any(observed.get(name) != value for name, value in metric_values.items()):
            fail("E_EVIDENCE_REPORT", f"raw structural report differs from manifest: {raw}")
        source_mtime = observed.get("source_mtime_ns")
        if not isinstance(source_mtime, int):
            fail("E_EVIDENCE_SCHEMA", f"manifest structural report mtime is invalid: {raw}")
        if source_mtime != raw.stat().st_mtime_ns:
            fail(
                "E_EVIDENCE_MTIME",
                f"manifest structural report mtime differs from raw XML: {raw}",
            )
        if source_mtime <= validated_variant.mtime_ns:
            fail("E_STALE_REPORT", f"manifest carries a stale structural report: {raw}")
        structural_result.append((expected, observed, metrics, raw))
    missing_structural = sorted(
        set(expected_structural_by_fqcn) - observed_structural_fqcns
    )
    if missing_structural:
        fail(
            "E_MISSING_REPORT",
            f"run evidence is missing structural reports: {missing_structural[:10]}",
        )
    actual_raw_reports: set[Path] = set()
    for path in evidence_root.rglob("TEST-*.xml"):
        if path.is_symlink() or not path.is_file():
            fail("E_REPORT_PATH", f"evidence report is not a regular file: {path}")
        actual_raw_reports.add(ensure_within(path, evidence_root, "raw evidence report"))
    missing_raw = sorted(str(path) for path in referenced_raw_reports - actual_raw_reports)
    extra_raw = sorted(str(path) for path in actual_raw_reports - referenced_raw_reports)
    if missing_raw:
        fail("E_MISSING_REPORT", f"manifest raw report files are missing: {missing_raw[:10]}")
    if extra_raw:
        fail("E_EXTRA_REPORT", f"manifest has unowned raw report files: {extra_raw[:10]}")
    totals = {
        name: sum(getattr(metrics, name) for _, _, metrics, _ in result)
        for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
    }
    if manifest.get("totals") != totals:
        fail("E_EVIDENCE_SCHEMA", f"manifest totals differ from raw reports: {manifest_path}")
    structural_totals = {
        name: sum(getattr(metrics, name) for _, _, metrics, _ in structural_result)
        for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
    }
    if manifest.get("structural_totals") != structural_totals:
        fail(
            "E_EVIDENCE_SCHEMA",
            f"manifest structural totals differ from raw reports: {manifest_path}",
        )
    return ValidatedRun(
        variant=variant,
        reports=tuple(result),
        structural_reports=tuple(structural_result),
        variant_marker=variant_raw,
    )


def merge(args: argparse.Namespace) -> None:
    output = args.evidence_output.resolve()
    contract = load_successor(args.successor_dir)
    outer = validate_outer_marker(args.outer_marker, contract, args.lane)
    require_in_run_root(output, outer, "merged evidence output")
    expected_rows = [row for row in contract.rows if row["runner"] == args.lane]
    expected_structural_rows = [
        row for row in contract.structural_rows if row["runner"] == args.lane
    ]
    if not expected_rows:
        fail("E_ZERO_EXPECTED", f"runner has no Step 2 execution keys: {args.lane}")
    expected_by_key = {row["execution_key"]: row for row in expected_rows}
    expected_structural_by_fqcn = {
        row["report_fqcn"]: row for row in expected_structural_rows
    }
    expected_variants = {
        row["variant_key"] for row in expected_rows + expected_structural_rows
    }
    if not args.manifest:
        fail("E_MISSING_EVIDENCE", "at least one --manifest is required")

    variants: set[str] = set()
    execution_keys: set[str] = set()
    report_fqcns: set[str] = set()
    structural_fqcns: set[str] = set()
    validated: list[tuple[dict[str, str], dict[str, Any], ReportMetrics, Path]] = []
    validated_structural: list[
        tuple[dict[str, str], dict[str, Any], ReportMetrics, Path]
    ] = []
    manifest_sources: list[tuple[str, Path, Path]] = []
    for manifest_path in args.manifest:
        resolved_manifest = manifest_path.resolve()
        run = validate_run_manifest(resolved_manifest, contract, args.lane, outer)
        if run.variant in variants:
            fail("E_DUPLICATE_VARIANT", f"duplicate run manifest for variant: {run.variant}")
        variants.add(run.variant)
        manifest_sources.append((run.variant, resolved_manifest, run.variant_marker))
        for expected, observed, metrics, raw in run.reports:
            key = expected["execution_key"]
            fqcn = expected["report_fqcn"]
            if key in execution_keys:
                fail("E_DUPLICATE_EXECUTION", f"duplicate merged execution key: {key}")
            if fqcn in report_fqcns:
                fail("E_DUPLICATE_FQCN", f"duplicate merged report FQCN: {fqcn}")
            execution_keys.add(key)
            report_fqcns.add(fqcn)
            validated.append((expected, observed, metrics, raw))
        for expected, observed, metrics, raw in run.structural_reports:
            fqcn = expected["report_fqcn"]
            if fqcn in structural_fqcns or fqcn in report_fqcns:
                fail("E_DUPLICATE_FQCN", f"duplicate merged structural report FQCN: {fqcn}")
            structural_fqcns.add(fqcn)
            validated_structural.append((expected, observed, metrics, raw))
    if variants != expected_variants:
        missing_variants = sorted(expected_variants - variants)
        extra_variants = sorted(variants - expected_variants)
        fail(
            "E_MISSING_EVIDENCE",
            f"variant evidence differs missing={missing_variants} extra={extra_variants}",
        )
    missing = sorted(set(expected_by_key) - execution_keys)
    extra = sorted(execution_keys - set(expected_by_key))
    if missing:
        fail("E_MISSING_REPORT", f"merged evidence is missing execution keys: {missing[:10]}")
    if extra:
        fail("E_EXTRA_REPORT", f"merged evidence has extra execution keys: {extra[:10]}")
    missing_structural = sorted(set(expected_structural_by_fqcn) - structural_fqcns)
    extra_structural = sorted(structural_fqcns - set(expected_structural_by_fqcn))
    if missing_structural:
        fail(
            "E_MISSING_REPORT",
            f"merged evidence is missing structural reports: {missing_structural[:10]}",
        )
    if extra_structural:
        fail(
            "E_EXTRA_REPORT",
            f"merged evidence has extra structural reports: {extra_structural[:10]}",
        )
    if not validated:
        fail("E_ZERO_REPORT", "merged evidence contains no reports")

    temporary = prepare_output(output)
    try:
        outer_marker_relative = Path("outer-run-marker.json")
        shutil.copy2(outer.marker, temporary / outer_marker_relative)
        report_rows: list[dict[str, Any]] = []
        structural_report_rows: list[dict[str, Any]] = []
        testcase_rows: list[dict[str, str]] = []
        for expected, observed, metrics, raw in sorted(
            validated,
            key=lambda item: item[0]["execution_key"],
        ):
            relative = (
                Path("raw-reports")
                / expected["variant_key"]
                / safe_relative_path(expected["owner"], "owning module")
                / raw.name
            )
            target = temporary / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(raw, target)
            merged_row = dict(observed)
            merged_row["evidence_report"] = relative.as_posix()
            report_rows.append(merged_row)
            for classname, test_name in metrics.testcases:
                testcase_rows.append({
                    "execution_key": expected["execution_key"],
                    "report_fqcn": expected["report_fqcn"],
                    "classname": sanitize_tsv(classname),
                    "name": sanitize_tsv(test_name),
                })
        for expected, observed, metrics, raw in sorted(
            validated_structural,
            key=lambda item: item[0]["report_fqcn"],
        ):
            relative = (
                Path("structural-reports")
                / expected["variant_key"]
                / safe_relative_path(expected["owner"], "structural owning module")
                / raw.name
            )
            target = temporary / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(raw, target)
            merged_row = dict(observed)
            merged_row["evidence_report"] = relative.as_posix()
            structural_report_rows.append(merged_row)
        totals = {
            name: sum(int(row[name]) for row in report_rows)
            for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
        }
        structural_totals = {
            name: sum(int(row[name]) for row in structural_report_rows)
            for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
        }
        source_manifest_rows: list[dict[str, Any]] = []
        variant_marker_rows: list[dict[str, Any]] = []
        for variant, source_manifest, marker_raw in sorted(manifest_sources):
            variant_token = safe_filename_token(variant, "variant key")
            manifest_relative = Path("source-manifests") / f"{variant_token}.json"
            marker_relative = Path("run-markers") / f"{variant_token}.marker"
            (temporary / manifest_relative).parent.mkdir(parents=True, exist_ok=True)
            (temporary / marker_relative).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source_manifest, temporary / manifest_relative)
            shutil.copy2(marker_raw, temporary / marker_relative)
            source_manifest_rows.append({
                "variant_key": variant,
                "evidence_manifest": manifest_relative.as_posix(),
                "sha256": sha256_file(source_manifest),
            })
            variant_marker_rows.append({
                "variant_key": variant,
                "context": read_json(marker_raw),
                "evidence_path": marker_relative.as_posix(),
                "mtime_ns": marker_raw.stat().st_mtime_ns,
                "sha256": sha256_file(marker_raw),
            })
        manifest = {
            "schema_version": 3,
            "kind": "v934-step2-report-merged",
            "status": "passed",
            "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "successor": contract_identity(contract),
            "runner": args.lane,
            "outer_run": marker_record(outer, outer_marker_relative),
            "variant_keys": sorted(variants),
            "expected_execution_count": len(expected_rows),
            "expected_structural_report_count": len(expected_structural_rows),
            "report_count": len(report_rows),
            "structural_report_count": len(structural_report_rows),
            "raw_report_count": len(report_rows) + len(structural_report_rows),
            "totals": totals,
            "structural_totals": structural_totals,
            "source_manifests": source_manifest_rows,
            "variant_markers": variant_marker_rows,
            "reports": report_rows,
            "structural_reports": structural_report_rows,
        }
        write_evidence_tables(
            temporary,
            report_rows,
            structural_report_rows,
            testcase_rows,
        )
        write_json(temporary / RUN_MANIFEST, manifest)
        publish_output(temporary, output)
    except Exception:
        remove_temporary(temporary)
        raise
    print(
        f"{PREFIX} PASS merge runner={args.lane} variants={len(variants)} "
        f"executions={len(report_rows)} structural={len(structural_report_rows)} "
        f"tests={totals['tests']} output={output}"
    )


def write_probe_report(
    raw_root: Path,
    expected: dict[str, str],
    runner: str,
    *,
    fqcn: str | None = None,
    module: str | None = None,
    tests: int = 1,
    skipped: int = 0,
) -> Path:
    report_fqcn = fqcn or expected["report_fqcn"]
    owning_module = module or expected["owner"]
    directory = (
        raw_root
        / safe_relative_path(owning_module, "probe owning module")
        / "target"
        / REPORT_DIRECTORIES[runner]
    )
    directory.mkdir(parents=True, exist_ok=True)
    cases = []
    for index in range(tests):
        outcome = "<skipped/>" if index < skipped else ""
        cases.append(
            f'<testcase classname="{report_fqcn}" name="probe-{index}">{outcome}</testcase>'
        )
    body = (
        f'<testsuite name="{report_fqcn}" tests="{tests}" failures="0" '
        f'errors="0" skipped="{skipped}">{"".join(cases)}</testsuite>'
    )
    path = directory / f"TEST-{report_fqcn}.xml"
    path.write_text(body, encoding="utf-8", newline="\n")
    return path


def probe_test_count(contract: SuccessorContract, expected: dict[str, str]) -> int:
    discovery = discovery_binding(contract, expected)
    discovered = int(discovery["discovered_test_nodes"])
    deferred = int(discovery["runtime_deferred_containers"])
    return discovered if deferred == 0 else discovered + deferred


def write_probe_markers(
    run_root: Path,
    contract: SuccessorContract,
    runner: str,
    variant: str,
    run_id: str,
    outer_mtime_ns: int,
) -> tuple[Path, Path, int]:
    run_root.mkdir(parents=True, exist_ok=True)
    outer_marker = run_root / "outer-run.json"
    write_json(outer_marker, {
        "schema_version": 1,
        "kind": "v934-step2-outer-run",
        "run_id": run_id,
        "runner": runner,
        "git_head": "0" * 40,
        "source_before_sha256": hashlib.sha256(run_id.encode()).hexdigest(),
        "started_at": "2026-07-15T00:00:00+00:00",
        "status": "started",
        "successor": contract_identity(contract),
    })
    os.utime(outer_marker, ns=(outer_mtime_ns, outer_mtime_ns))
    variant_mtime_ns = outer_mtime_ns + 100_000_000
    variant_marker = run_root / f"variant-{safe_filename_token(variant, 'variant key')}.json"
    write_json(variant_marker, {
        "schema_version": 1,
        "kind": "v934-step2-variant-run",
        "run_id": run_id,
        "runner": runner,
        "variant_key": variant,
        "outer_marker_sha256": sha256_file(outer_marker),
        "started_at": "2026-07-15T00:00:01+00:00",
        "status": "started",
    })
    os.utime(variant_marker, ns=(variant_mtime_ns, variant_mtime_ns))
    return outer_marker, variant_marker, variant_mtime_ns


def negative(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    output = args.evidence_output.resolve()
    contract = load_successor(args.successor_dir)
    if not root.is_dir():
        fail("E_ROOT", f"workspace root does not exist: {root}")

    groups: dict[tuple[str, str], list[dict[str, str]]] = {}
    for row in contract.rows:
        groups.setdefault((row["runner"], row["variant_key"]), []).append(row)
    structural_groups = {
        (row["runner"], row["variant_key"])
        for row in contract.structural_rows
    }
    groups_with_structural = [
        item for item in groups.items() if item[0] in structural_groups
    ]
    if not groups_with_structural:
        fail("E_NEGATIVE_SETUP", "negative probes require a variant with structural reports")
    (runner, variant), expected_rows = min(
        groups_with_structural,
        key=lambda item: (len(item[1]), item[0][0], item[0][1]),
    )
    expected_structural_rows = [
        row for row in contract.structural_rows
        if row["runner"] == runner and row["variant_key"] == variant
    ]
    all_contract_rows = list(contract.rows) + list(contract.structural_rows)
    other_runner_rows = [row for row in all_contract_rows if row["runner"] != runner]
    contract_owners = {row["owner"] for row in all_contract_rows}
    modules = list(contract.reactor_modules)
    unowned_modules = sorted(set(modules) - contract_owners)
    if not other_runner_rows or len(modules) < 2:
        fail("E_NEGATIVE_SETUP", "negative probes require both runners and at least two modules")
    if not unowned_modules:
        fail("E_NEGATIVE_SETUP", "negative probes require a source-less reactor module")

    deferred_row = next(
        (
            row
            for row in contract.rows
            if int(discovery_binding(contract, row)["runtime_deferred_containers"]) > 0
        ),
        None,
    )
    if deferred_row is None:
        fail("E_NEGATIVE_SETUP", "negative probes require a deferred discovery binding")

    results: list[dict[str, str]] = []
    for probe, expected_error in NEGATIVE_PROBES:
        with tempfile.TemporaryDirectory(prefix=f"v934-step2-report-{probe}-") as temporary_name:
            temporary = Path(temporary_name)
            probe_runner = runner
            probe_variant = variant
            probe_rows = expected_rows
            probe_structural_rows = expected_structural_rows
            if probe == "deferred-discovery-underflow":
                probe_runner = deferred_row["runner"]
                probe_variant = deferred_row["variant_key"]
                probe_rows = [
                    row for row in contract.rows
                    if row["runner"] == probe_runner and row["variant_key"] == probe_variant
                ]
                probe_structural_rows = [
                    row for row in contract.structural_rows
                    if row["runner"] == probe_runner and row["variant_key"] == probe_variant
                ]

            marker_base_ns = time.time_ns() - 3_000_000_000
            outer_marker, marker, marker_ns = write_probe_markers(
                temporary / "run-a",
                contract,
                probe_runner,
                probe_variant,
                f"negative-a-{probe}",
                marker_base_ns,
            )
            raw_root = temporary / "raw"
            raw_root.mkdir()
            fresh_ns = marker_ns + 1_000_000_000
            report_paths: dict[str, Path] = {}
            structural_paths: dict[str, Path] = {}
            for row in probe_rows:
                path = write_probe_report(
                    raw_root,
                    row,
                    probe_runner,
                    tests=probe_test_count(contract, row),
                )
                os.utime(path, ns=(fresh_ns, fresh_ns))
                report_paths[row["execution_key"]] = path
            for row in probe_structural_rows:
                path = write_probe_report(raw_root, row, probe_runner, tests=0)
                os.utime(path, ns=(fresh_ns, fresh_ns))
                structural_paths[row["report_fqcn"]] = path

            victim = probe_rows[0]
            static_victim = next(
                row for row in probe_rows
                if int(discovery_binding(contract, row)["runtime_deferred_containers"]) == 0
                and probe_test_count(contract, row) >= 2
            )
            victim_path = report_paths[victim["execution_key"]]
            static_victim_path = report_paths[static_victim["execution_key"]]
            structural_victim = probe_structural_rows[0]
            structural_victim_path = structural_paths[structural_victim["report_fqcn"]]
            if probe == "missing-xml":
                victim_path.unlink()
            elif probe == "stale-marker":
                os.utime(victim_path, ns=(marker_ns, marker_ns))
            elif probe == "zero-testcase":
                victim_path = write_probe_report(raw_root, victim, probe_runner, tests=0)
                os.utime(victim_path, ns=(fresh_ns, fresh_ns))
            elif probe == "duplicate-fqcn":
                duplicate_module = next(module for module in modules if module != victim["owner"])
                duplicate = write_probe_report(
                    raw_root,
                    victim,
                    probe_runner,
                    module=duplicate_module,
                )
                os.utime(duplicate, ns=(fresh_ns, fresh_ns))
            elif probe == "unexpected-extra":
                extra = write_probe_report(
                    raw_root,
                    victim,
                    probe_runner,
                    fqcn="v934.probe.UnexpectedExtraIT",
                )
                os.utime(extra, ns=(fresh_ns, fresh_ns))
            elif probe == "skipped":
                victim_path = write_probe_report(
                    raw_root,
                    victim,
                    probe_runner,
                    tests=probe_test_count(contract, victim),
                    skipped=1,
                )
                os.utime(victim_path, ns=(fresh_ns, fresh_ns))
            elif probe == "runner-overlap":
                overlap_row = other_runner_rows[0]
                overlap = write_probe_report(
                    raw_root,
                    overlap_row,
                    probe_runner,
                    fqcn=overlap_row["report_fqcn"],
                )
                os.utime(overlap, ns=(fresh_ns, fresh_ns))
            elif probe == "missing-structural":
                structural_victim_path.unlink()
            elif probe == "structural-nonzero":
                structural_victim_path = write_probe_report(
                    raw_root,
                    structural_victim,
                    probe_runner,
                    tests=1,
                )
                os.utime(structural_victim_path, ns=(fresh_ns, fresh_ns))
            elif probe == "unexpected-structural-zero":
                extra = write_probe_report(
                    raw_root,
                    structural_victim,
                    probe_runner,
                    fqcn="v934.probe.UnexpectedStructuralContainer",
                    tests=0,
                )
                os.utime(extra, ns=(fresh_ns, fresh_ns))
            elif probe == "unowned-reactor-extra":
                extra = write_probe_report(
                    raw_root,
                    victim,
                    probe_runner,
                    module=unowned_modules[0],
                    fqcn="v934.probe.UnownedReactorExtraTest",
                )
                os.utime(extra, ns=(fresh_ns, fresh_ns))
            elif probe == "structural-stale":
                os.utime(structural_victim_path, ns=(marker_ns, marker_ns))
            elif probe in {"static-discovery-underflow", "static-discovery-overflow"}:
                delta = -1 if probe.endswith("underflow") else 1
                static_victim_path = write_probe_report(
                    raw_root,
                    static_victim,
                    probe_runner,
                    tests=probe_test_count(contract, static_victim) + delta,
                )
                os.utime(static_victim_path, ns=(fresh_ns, fresh_ns))
            elif probe == "deferred-discovery-underflow":
                deferred_victim = next(
                    row for row in probe_rows
                    if int(discovery_binding(contract, row)["runtime_deferred_containers"]) > 0
                )
                deferred_path = write_probe_report(
                    raw_root,
                    deferred_victim,
                    probe_runner,
                    tests=probe_test_count(contract, deferred_victim) - 1,
                )
                os.utime(deferred_path, ns=(fresh_ns, fresh_ns))
            elif probe in {
                "forged-positive-mtime",
                "forged-structural-mtime",
                "cross-run-manifest-splice",
                "manifest-discovery-drift",
                "discovery-binding-drift",
            }:
                pass
            else:
                fail("E_NEGATIVE_SETUP", f"unknown negative probe: {probe}")

            probe_args = argparse.Namespace(
                root=root,
                raw_root=raw_root,
                outer_marker=outer_marker,
                run_marker=marker,
                evidence_output=temporary / "run-a" / "unexpected-pass",
                successor_dir=contract.directory,
                lane=probe_runner,
                variant_key=probe_variant,
            )
            try:
                post_collect_probes = {
                    "forged-positive-mtime",
                    "forged-structural-mtime",
                    "cross-run-manifest-splice",
                    "manifest-discovery-drift",
                    "discovery-binding-drift",
                }
                if probe in post_collect_probes:
                    validation_outer_marker = outer_marker
                    if probe == "cross-run-manifest-splice":
                        outer_b, marker_b, _ = write_probe_markers(
                            temporary / "run-b",
                            contract,
                            probe_runner,
                            probe_variant,
                            f"negative-b-{probe}",
                            marker_base_ns,
                        )
                        probe_args.outer_marker = outer_b
                        probe_args.run_marker = marker_b
                        probe_args.evidence_output = temporary / "run-b" / "evidence"
                    with contextlib.redirect_stdout(io.StringIO()):
                        collect(probe_args)
                    manifest_path = probe_args.evidence_output / RUN_MANIFEST
                    if probe == "cross-run-manifest-splice":
                        spliced = temporary / "run-a" / "spliced-evidence"
                        shutil.copytree(probe_args.evidence_output, spliced)
                        manifest_path = spliced / RUN_MANIFEST
                    else:
                        manifest = read_json(manifest_path)
                        if probe == "forged-positive-mtime":
                            manifest["reports"][0]["source_mtime_ns"] += 10**18
                        elif probe == "forged-structural-mtime":
                            manifest["structural_reports"][0]["source_mtime_ns"] += 10**18
                        elif probe == "manifest-discovery-drift":
                            manifest["reports"][0]["discovered_test_nodes"] += 1
                        elif probe == "discovery-binding-drift":
                            manifest["reports"][0]["source_id"] += "-forged"
                        write_json(manifest_path, manifest)
                    outer = validate_outer_marker(
                        validation_outer_marker,
                        contract,
                        probe_runner,
                    )
                    validate_run_manifest(manifest_path, contract, probe_runner, outer)
                else:
                    collect(probe_args)
            except ReportError as exc:
                actual_error = exc.code
            else:
                actual_error = "none"
            if actual_error != expected_error:
                fail(
                    "E_NEGATIVE",
                    f"probe {probe} actual={actual_error}, expected={expected_error}",
                )
            results.append({
                "probe": probe,
                "expected_error": expected_error,
                "actual_error": actual_error,
                "status": "passed",
            })

    temporary_output = prepare_output(output)
    try:
        write_tsv(temporary_output / "negative-probes.tsv", NEGATIVE_HEADER, results)
        publish_output(temporary_output, output)
    except Exception:
        remove_temporary(temporary_output)
        raise
    print(
        f"{PREFIX} negative PASS probes={len(results)} runner={runner} "
        f"variant={variant} output={output}"
    )


def build_parser() -> argparse.ArgumentParser:
    root_default = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(
        description="Collect and merge strict Maven XML evidence for 9.3.4 Step 2.",
        epilog=(
            "Run collect immediately after each labeled Maven variant; then merge all "
            "run manifests for exactly one runner. Existing output directories are refused."
        ),
    )
    subparsers = parser.add_subparsers(required=True)

    def add_collect_parser(name: str, help_text: str) -> None:
        command = subparsers.add_parser(name, help=help_text)
        command.add_argument("--root", type=Path, default=root_default)
        command.add_argument("--successor-dir", type=Path, required=True)
        command.add_argument("--lane", "--runner", dest="lane", choices=RUNNERS, required=True)
        command.add_argument("--outer-marker", type=Path, required=True)
        command.add_argument(
            "--variant-key",
            help=(
                "exact variant_key from step2-required-execution.tsv; may be omitted "
                "only when the runner owns exactly one variant"
            ),
        )
        command.add_argument("--run-marker", "--marker", dest="run_marker", type=Path, required=True)
        command.add_argument(
            "--raw-root",
            type=Path,
            help=(
                "root of a workspace-shaped raw tree containing "
                "<module>/target/<runner>-reports; defaults to --root"
            ),
        )
        command.add_argument(
            "--evidence-output",
            "--output-dir",
            dest="evidence_output",
            type=Path,
            required=True,
        )
        command.set_defaults(handler=collect)

    add_collect_parser("verify", "validate and copy one fresh runner/variant report set")
    add_collect_parser("collect", "alias of verify")

    def add_merge_parser(name: str, help_text: str) -> None:
        command = subparsers.add_parser(name, help=help_text)
        command.add_argument("--successor-dir", type=Path, required=True)
        command.add_argument("--lane", "--runner", dest="lane", choices=RUNNERS, required=True)
        command.add_argument("--outer-marker", type=Path, required=True)
        command.add_argument("--manifest", type=Path, action="append", required=True)
        command.add_argument(
            "--evidence-output",
            "--output-dir",
            dest="evidence_output",
            type=Path,
            required=True,
        )
        command.set_defaults(handler=merge)

    add_merge_parser("finalize", "merge run manifests and prove complete runner coverage")
    add_merge_parser("merge", "alias of finalize")

    negative_parser = subparsers.add_parser(
        "negative",
        help="run isolated expected-negative report probes",
    )
    negative_parser.add_argument("--root", type=Path, default=root_default)
    negative_parser.add_argument("--successor-dir", type=Path, required=True)
    negative_parser.add_argument(
        "--evidence-output",
        "--output-dir",
        dest="evidence_output",
        type=Path,
        required=True,
    )
    negative_parser.set_defaults(handler=negative)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        args.handler(args)
        return 0
    except ReportError as exc:
        print(f"{PREFIX} ERROR {exc}", file=sys.stderr)
        return 1
    except OSError as exc:
        print(f"{PREFIX} ERROR E_IO: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
