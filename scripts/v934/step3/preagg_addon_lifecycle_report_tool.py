#!/usr/bin/env python3
"""Fail-closed evidence collector for the Step 3 PreAgg Addon lifecycle gate."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


PREFIX = "[v934-step3-preagg-report]"
SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_ROOT = SCRIPT_DIR.parents[2]
DEFAULT_CONTRACT = SCRIPT_DIR / "preagg-addon-lifecycle-contract.json"
REPORT_FQCN = "com.foggyframework.dataset.db.model.preagg.lifecycle.PreAggAddonLifecycleIT"
REPORT_NAME = f"TEST-{REPORT_FQCN}.xml"
VARIANTS = ("sqlite", "mysql57")
TEST_NAMES = {
    "realLifecycleHasNativeQueryParity",
    "missingExplicitMappingFailsClosed",
    "numericWatermarkFailsClosedWithoutMutation",
}
STATUS_KEYS = [
    "run_id", "runner", "lane", "git_head", "contract_sha256",
    "source_sha256", "started_at", "finished_at", "last_phase",
    "exit_code", "resource_residue", "status",
]
SUMMARY_KEYS = [
    "run_id", "runner", "lane", "git_head", "contract_sha256",
    "parent_authority_kind", "parent_run_id", "parent_git_head",
    "parent_contract_sha256", "parent_source_sha256",
    "parent_outer_marker_sha256", "variants", "reports", "testcase_nodes",
    "failures", "errors", "skipped", "sqlite_reports",
    "sqlite_testcase_nodes", "mysql57_reports", "mysql57_testcase_nodes",
    "resource_residue", "status",
]


class EvidenceError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def fail(code: str, message: str) -> None:
    raise EvidenceError(code, message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=path.parent,
                                     prefix=f".{path.name}.", delete=False) as stream:
        json.dump(payload, stream, ensure_ascii=False, indent=2, sort_keys=True)
        stream.write("\n")
        temporary = Path(stream.name)
    os.replace(temporary, path)


def read_json(path: Path, code: str) -> Any:
    if path.is_symlink() or not path.is_file():
        fail(code, f"not a regular file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(code, f"cannot read {path}: {error}")


def inside_root(root: Path, relative: str, code: str) -> Path:
    candidate = root / relative
    try:
        resolved = candidate.resolve(strict=True)
    except OSError as error:
        fail(code, f"bound path is missing: {relative}: {error}")
    try:
        resolved.relative_to(root.resolve(strict=True))
    except ValueError:
        fail(code, f"bound path escapes repository: {relative}")
    if candidate.is_symlink() or not candidate.is_file():
        fail(code, f"bound path is not a regular non-symlink file: {relative}")
    return candidate


def validate_contract(root: Path, contract_path: Path) -> dict[str, Any]:
    contract = read_json(contract_path, "E_CONTRACT")
    if not isinstance(contract, dict):
        fail("E_CONTRACT", "contract root must be an object")
    expected = {
        "kind": "v934-step3-preagg-addon-lifecycle-contract",
        "schema_version": 1,
        "lane": "preagg-addon-lifecycle",
        "runner": "failsafe",
        "required": True,
        "module": "addons/foggy-dataset-model-preagg",
        "version": "9.3.4",
    }
    for key, value in expected.items():
        if contract.get(key) != value:
            fail("E_CONTRACT", f"contract {key} differs: {contract.get(key)!r}")
    variants = contract.get("variants")
    if not isinstance(variants, list) or [entry.get("variant_key") for entry in variants] != list(VARIANTS):
        fail("E_CONTRACT", "variant order must be exactly sqlite,mysql57")
    for entry in variants:
        if entry != {
            "variant_key": entry["variant_key"],
            "db_kind": entry["variant_key"],
            "expected_reports": 1,
            "expected_testcase_nodes": 3,
            "report_fqcn": REPORT_FQCN,
        }:
            fail("E_CONTRACT", f"variant contract differs: {entry!r}")
    if contract.get("totals") != {
        "variants": 2, "reports": 2, "testcase_nodes": 6,
        "failures": 0, "errors": 0, "skipped": 0,
    }:
        fail("E_CONTRACT", "frozen totals differ")
    if set(contract.get("testcase_names", [])) != TEST_NAMES or len(contract.get("testcase_names", [])) != 3:
        fail("E_CONTRACT", "testcase names differ")
    if contract.get("negative_probes") != [
        {"probe": "missing-report", "expected_error": "E_MISSING_REPORT"},
        {"probe": "failure-outcome", "expected_error": "E_REPORT_OUTCOME"},
        {"probe": "stale-report", "expected_error": "E_STALE_REPORT"},
        {"probe": "wrong-variant-report", "expected_error": "E_REPORT_IDENTITY"},
    ]:
        fail("E_CONTRACT", "negative probe contract differs")
    if contract.get("external_database") != {
        "db_kind": "mysql57",
        "expected_version": "5.7.44",
        "image_id": "sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb",
        "image_ref": "mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb",
        "resource_label": "com.foggy.v934.preagg-run",
        "data_mount_destination": "/var/lib/mysql",
        "container_port": "3306/tcp",
    }:
        fail("E_CONTRACT", "external MySQL 5.7 contract differs")
    bindings = contract.get("bindings")
    if not isinstance(bindings, dict) or not bindings:
        fail("E_BINDING", "contract bindings are empty")
    for name, binding in sorted(bindings.items()):
        if not isinstance(binding, dict) or set(binding) != {"path", "sha256"}:
            fail("E_BINDING", f"binding schema differs: {name}")
        path = inside_root(root, binding["path"], "E_BINDING")
        actual = sha256(path)
        if binding["sha256"] != actual:
            fail("E_BINDING", f"binding digest differs for {binding['path']}: {actual}")
    return contract


def binding_source_sha(contract: dict[str, Any]) -> str:
    digest = hashlib.sha256()
    for binding in sorted(contract["bindings"].values(), key=lambda item: item["path"]):
        digest.update(binding["path"].encode("utf-8"))
        digest.update(b"\0")
        digest.update(binding["sha256"].encode("ascii"))
        digest.update(b"\n")
    return digest.hexdigest()


def validate_context(context_path: Path, contract_path: Path,
                     source_sha: str) -> dict[str, Any]:
    context = read_json(context_path, "E_RUN_CONTEXT")
    required = {
        "schema_version", "kind", "run_id", "runner", "lane", "git_head",
        "contract_sha256", "source_sha256", "started_at", "status",
        "authority_mode", "parent_authority_kind", "parent_run_id",
        "parent_git_head", "parent_contract_sha256", "parent_source_sha256",
        "parent_outer_marker_sha256",
    }
    if not isinstance(context, dict) or set(context) != required:
        fail("E_RUN_CONTEXT", "run context fields differ")
    if context["kind"] != "v934-step3-preagg-addon-lifecycle-run" \
            or context["runner"] != "failsafe" \
            or context["lane"] != "preagg-addon-lifecycle" \
            or context["status"] != "started":
        fail("E_RUN_CONTEXT", "run context identity differs")
    if context["contract_sha256"] != sha256(contract_path):
        fail("E_RUN_CONTEXT", "contract digest differs")
    if context["source_sha256"] != source_sha:
        fail("E_RUN_CONTEXT", "source digest differs")
    if context["authority_mode"] not in {"standalone", "inherited"}:
        fail("E_RUN_CONTEXT", "authority mode differs")
    parent_fields = [
        "parent_authority_kind", "parent_run_id", "parent_git_head",
        "parent_contract_sha256", "parent_source_sha256",
        "parent_outer_marker_sha256",
    ]
    if context["authority_mode"] == "standalone":
        if any(context[field] != "none" for field in parent_fields):
            fail("E_RUN_CONTEXT", "standalone context contains parent provenance")
    else:
        if context["parent_authority_kind"] != "step3-required-matrix" \
                or context["parent_run_id"] != context["run_id"] \
                or context["parent_git_head"] != context["git_head"]:
            fail("E_RUN_CONTEXT", "inherited parent identity differs")
        if any(len(context[field]) != 64 for field in parent_fields[3:]):
            fail("E_RUN_CONTEXT", "inherited parent digest differs")
    return context


def collect(root: Path, contract_path: Path, variant: str, reports_dir: Path,
            marker_path: Path, context_path: Path, output_dir: Path) -> None:
    contract = validate_contract(root, contract_path)
    source_sha = binding_source_sha(contract)
    context = validate_context(context_path, contract_path, source_sha)
    validate_parent_context(root, context_path, context)
    if variant not in VARIANTS:
        fail("E_VARIANT", f"unsupported variant: {variant}")
    marker = read_json(marker_path, "E_MARKER")
    expected_marker = {
        "schema_version": 1,
        "kind": "v934-step3-preagg-addon-lifecycle-variant-run",
        "run_id": context["run_id"],
        "variant_key": variant,
        "git_head": context["git_head"],
        "contract_sha256": context["contract_sha256"],
        "source_sha256": context["source_sha256"],
        "run_context_sha256": sha256(context_path),
        "status": "started",
    }
    if marker != expected_marker:
        fail("E_MARKER", "variant marker differs")
    if reports_dir.is_symlink() or not reports_dir.is_dir():
        fail("E_MISSING_REPORT", f"report directory is missing: {reports_dir}")
    reports = sorted(reports_dir.glob("TEST-*.xml"))
    if not reports:
        fail("E_MISSING_REPORT", "no JUnit XML report found")
    if [path.name for path in reports] != [REPORT_NAME]:
        fail("E_EXTRA_REPORT", f"report set differs: {[path.name for path in reports]}")
    report = reports[0]
    if report.is_symlink() or not report.is_file():
        fail("E_REPORT", "JUnit report is not a regular file")
    if report.stat().st_mtime_ns <= marker_path.stat().st_mtime_ns:
        fail("E_STALE_REPORT", "JUnit report is not strictly newer than its marker")
    try:
        suite = ET.parse(report).getroot()
    except (ET.ParseError, OSError) as error:
        fail("E_REPORT", f"cannot parse JUnit XML: {error}")
    if suite.tag != "testsuite" or suite.get("name") != REPORT_FQCN:
        fail("E_REPORT_IDENTITY", "JUnit suite identity differs")
    report_properties: dict[str, str] = {}
    for prop in suite.findall("./properties/property"):
        name = prop.get("name")
        value = prop.get("value")
        if not name or value is None or name in report_properties:
            fail("E_REPORT_IDENTITY", "JUnit report properties are malformed")
        report_properties[name] = value
    expected_identity = {
        "v934.preagg.runId": context["run_id"],
        "v934.preagg.variant": variant,
        "v934.preagg.variantMarkerSha256": sha256(marker_path),
    }
    if any(report_properties.get(name) != value
           for name, value in expected_identity.items()):
        fail("E_REPORT_IDENTITY", "JUnit run/variant identity differs")
    testcases = suite.findall(".//testcase")
    names = {case.get("name") for case in testcases}
    metrics = {
        "tests": int(suite.get("tests", "-1")),
        "failures": int(suite.get("failures", "-1")),
        "errors": int(suite.get("errors", "-1")),
        "skipped": int(suite.get("skipped", "-1")),
    }
    if metrics["tests"] != 3 or len(testcases) != 3 or names != TEST_NAMES:
        fail("E_REPORT_COUNT", f"expected exact 3-node report, got {metrics}, names={sorted(names)}")
    if any(metrics[key] != 0 for key in ("failures", "errors", "skipped")) \
            or suite.findall(".//failure") or suite.findall(".//error") \
            or suite.findall(".//skipped"):
        fail("E_REPORT_OUTCOME", f"non-green JUnit outcome: {metrics}")
    if output_dir.exists() or output_dir.is_symlink():
        fail("E_OUTPUT", f"variant output already exists: {output_dir}")
    raw_dir = output_dir / "raw-reports"
    raw_dir.mkdir(parents=True)
    copied = raw_dir / REPORT_NAME
    shutil.copy2(report, copied)
    copied_marker = output_dir / "variant-marker.json"
    shutil.copy2(marker_path, copied_marker)
    manifest = {
        "schema_version": 1,
        "kind": "v934-step3-preagg-addon-lifecycle-variant",
        "run_id": context["run_id"],
        "variant_key": variant,
        "git_head": context["git_head"],
        "contract_sha256": context["contract_sha256"],
        "source_sha256": context["source_sha256"],
        "run_context_sha256": sha256(context_path),
        "marker_sha256": sha256(copied_marker),
        "totals": {"reports": 1, "testcase_nodes": 3,
                   "failures": 0, "errors": 0, "skipped": 0},
        "reports": [{"fqcn": REPORT_FQCN, "path": f"raw-reports/{REPORT_NAME}",
                     "sha256": sha256(copied), "size": copied.stat().st_size,
                     "testcase_nodes": 3}],
    }
    write_json(output_dir / "report-manifest.json", manifest)


def parse_env(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        fail("E_STATUS", f"env evidence is not regular: {path}")
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or "=" not in line:
            fail("E_STATUS", f"invalid env evidence line: {line!r}")
        key, value = line.split("=", 1)
        if key in result:
            fail("E_STATUS", f"duplicate env evidence key: {key}")
        result[key] = value
    return result


def validate_parent_context(root: Path, context_path: Path,
                            context: dict[str, Any]) -> None:
    path = context_path.parent / "parent-context.env"
    if context["authority_mode"] == "standalone":
        if path.exists() or path.is_symlink():
            fail("E_PARENT_CONTEXT",
                 "standalone evidence must not contain parent-context.env")
        return
    if path.is_symlink() or not path.is_file():
        fail("E_PARENT_CONTEXT", "inherited parent-context.env is missing")

    lines = path.read_text(encoding="utf-8").splitlines()
    keys = [
        "authority_kind", "run_id", "git_head", "contract_sha256",
        "source_sha256", "outer_marker_sha256", "outer_marker_path",
    ]
    pairs = []
    for line in lines:
        if "=" not in line:
            fail("E_PARENT_CONTEXT", f"invalid parent context line: {line!r}")
        pairs.append(line.split("=", 1))
    if [pair[0] for pair in pairs] != keys:
        fail("E_PARENT_CONTEXT", "parent context fields/order differ")
    values = dict(pairs)

    raw_marker = Path(values["outer_marker_path"])
    expected_marker = (root / "target" / "v934-step3-required-matrix" /
                       "runs" / context["run_id"] / "run-context.json")
    try:
        canonical_marker = raw_marker.resolve(strict=True)
        canonical_expected = expected_marker.resolve(strict=True)
    except OSError as error:
        fail("E_PARENT_CONTEXT", f"parent marker is unavailable: {error}")
    if not raw_marker.is_absolute() or raw_marker != canonical_marker \
            or canonical_marker != canonical_expected \
            or raw_marker.is_symlink() or not raw_marker.is_file():
        fail("E_PARENT_CONTEXT", "parent marker path is not canonical")

    expected_values = {
        "authority_kind": context["parent_authority_kind"],
        "run_id": context["parent_run_id"],
        "git_head": context["parent_git_head"],
        "contract_sha256": context["parent_contract_sha256"],
        "source_sha256": context["parent_source_sha256"],
        "outer_marker_sha256": context["parent_outer_marker_sha256"],
        "outer_marker_path": canonical_marker.as_posix(),
    }
    if values != expected_values:
        fail("E_PARENT_CONTEXT", "parent context values differ")
    if sha256(canonical_marker) != values["outer_marker_sha256"]:
        fail("E_PARENT_CONTEXT", "parent marker digest differs")


def validate_passed_status(run_root: Path,
                           context: dict[str, Any]) -> dict[str, str]:
    status = parse_env(run_root / "run-status.env")
    expected = {
        "run_id": context["run_id"],
        "runner": "verify-v934-preagg-addon-lifecycle.sh",
        "lane": "preagg-addon-lifecycle",
        "git_head": context["git_head"],
        "contract_sha256": context["contract_sha256"],
        "source_sha256": context["source_sha256"],
        "started_at": context["started_at"],
        "last_phase": "completed",
        "exit_code": "0",
        "resource_residue": "0/0/0",
        "status": "passed",
    }
    if list(status) != STATUS_KEYS or any(status.get(key) != value
                                          for key, value in expected.items()) \
            or not status.get("finished_at"):
        fail("E_STATUS", "passed run status fields/provenance differ")
    return status


def expected_summary(context: dict[str, Any]) -> dict[str, str]:
    return {
        "run_id": context["run_id"],
        "runner": "failsafe",
        "lane": "preagg-addon-lifecycle",
        "git_head": context["git_head"],
        "contract_sha256": context["contract_sha256"],
        "parent_authority_kind": context["parent_authority_kind"],
        "parent_run_id": context["parent_run_id"],
        "parent_git_head": context["parent_git_head"],
        "parent_contract_sha256": context["parent_contract_sha256"],
        "parent_source_sha256": context["parent_source_sha256"],
        "parent_outer_marker_sha256": context["parent_outer_marker_sha256"],
        "variants": "2", "reports": "2", "testcase_nodes": "6",
        "failures": "0", "errors": "0", "skipped": "0",
        "sqlite_reports": "1", "sqlite_testcase_nodes": "3",
        "mysql57_reports": "1", "mysql57_testcase_nodes": "3",
        "resource_residue": "0/0/0", "status": "passed",
    }


def verify_variant(run_root: Path, variant: str, context: dict[str, Any]) -> None:
    root = run_root / "variants" / variant
    manifest_path = root / "report-manifest.json"
    manifest = read_json(manifest_path, "E_VARIANT_MANIFEST")
    if manifest.get("kind") != "v934-step3-preagg-addon-lifecycle-variant" \
            or manifest.get("run_id") != context["run_id"] \
            or manifest.get("variant_key") != variant \
            or manifest.get("git_head") != context["git_head"] \
            or manifest.get("contract_sha256") != context["contract_sha256"] \
            or manifest.get("source_sha256") != context["source_sha256"]:
        fail("E_VARIANT_MANIFEST", f"variant provenance differs: {variant}")
    if manifest.get("totals") != {"reports": 1, "testcase_nodes": 3,
                                  "failures": 0, "errors": 0, "skipped": 0}:
        fail("E_VARIANT_MANIFEST", f"variant totals differ: {variant}")
    reports = manifest.get("reports")
    if not isinstance(reports, list) or len(reports) != 1:
        fail("E_VARIANT_MANIFEST", f"variant report list differs: {variant}")
    report = root / reports[0].get("path", "")
    if report.is_symlink() or not report.is_file() \
            or reports[0].get("sha256") != sha256(report) \
            or reports[0].get("size") != report.stat().st_size:
        fail("E_VARIANT_MANIFEST", f"variant report evidence differs: {variant}")


def finalize(root: Path, contract_path: Path, run_root: Path) -> None:
    contract = validate_contract(root, contract_path)
    context_path = run_root / "run-context.json"
    context = validate_context(context_path, contract_path, binding_source_sha(contract))
    validate_parent_context(root, context_path, context)
    for variant in VARIANTS:
        verify_variant(run_root, variant, context)
    validate_passed_status(run_root, context)
    resource = read_json(run_root / "resource-evidence.json", "E_RESOURCE")
    if resource != {"containers": 0, "networks": 0, "volumes": 0,
                    "derived_name_residue": 0,
                    "label": f"com.foggy.v934.preagg-run={context['run_id']}"}:
        fail("E_RESOURCE", f"resource cleanup evidence differs: {resource!r}")
    mysql = read_json(run_root / "mysql57-runtime-evidence.json", "E_EXTERNAL_DATABASE")
    if not isinstance(mysql, dict) or set(mysql) != {
        "catalog", "container_image_id", "container_image_ref", "container_name",
        "image_id", "image_ref", "mount", "network", "published_port",
        "resource_label", "version",
    }:
        fail("E_EXTERNAL_DATABASE", "MySQL runtime evidence fields differ")
    if mysql.get("image_ref") != contract["external_database"]["image_ref"] \
            or mysql.get("image_id") != contract["external_database"]["image_id"] \
            or mysql.get("container_image_ref") != contract["external_database"]["image_ref"] \
            or mysql.get("container_image_id") != contract["external_database"]["image_id"] \
            or mysql.get("version") != contract["external_database"]["expected_version"] \
            or mysql.get("catalog") != "foggy_preagg" \
            or mysql.get("container_name") != f"v934preagg-{hashlib.sha256(context['run_id'].encode()).hexdigest()[:12]}-mysql57" \
            or mysql.get("resource_label") != {
                "key": contract["external_database"]["resource_label"],
                "value": context["run_id"],
            } \
            or mysql.get("mount") != {
                "count": 1,
                "destination": contract["external_database"]["data_mount_destination"],
                "name": f"v934preagg-{hashlib.sha256(context['run_id'].encode()).hexdigest()[:12]}-mysql57-data",
                "type": "volume",
            } \
            or mysql.get("network") != {
                "count": 1,
                "name": f"v934preagg-{hashlib.sha256(context['run_id'].encode()).hexdigest()[:12]}-network",
            }:
        fail("E_EXTERNAL_DATABASE", f"MySQL 5.7 runtime evidence differs: {mysql!r}")
    published_port = mysql.get("published_port")
    if not isinstance(published_port, dict) \
            or set(published_port) != {"container_port", "dynamic_port", "host"} \
            or published_port.get("container_port") != contract["external_database"]["container_port"] \
            or published_port.get("host") != "127.0.0.1" \
            or not isinstance(published_port.get("dynamic_port"), int) \
            or not 1 <= published_port["dynamic_port"] <= 65535:
        fail("E_EXTERNAL_DATABASE", f"MySQL published-port evidence differs: {mysql!r}")
    probes = (run_root / "negative-probes.tsv").read_text(encoding="utf-8").splitlines()
    if probes != [
        "probe\texpected_error\tresult",
        "missing-report\tE_MISSING_REPORT\tpassed",
        "failure-outcome\tE_REPORT_OUTCOME\tpassed",
        "stale-report\tE_STALE_REPORT\tpassed",
        "wrong-variant-report\tE_REPORT_IDENTITY\tpassed",
    ]:
        fail("E_NEGATIVE_PROBE", "collector negative-probe evidence differs")
    summary_values = expected_summary(context)
    summary_path = run_root / "summary.env"
    summary_path.write_text("".join(
        f"{key}={summary_values[key]}\n" for key in SUMMARY_KEYS),
                            encoding="utf-8")
    artifacts = []
    for path in sorted(run_root.rglob("*")):
        if path.is_symlink():
            fail("E_CANDIDATE", f"symlink is forbidden in evidence: {path}")
        if path.is_file() and path.name != "candidate-manifest.json":
            relative = path.relative_to(run_root).as_posix()
            artifacts.append({"path": relative, "sha256": sha256(path),
                              "size": path.stat().st_size})
    candidate = {
        "schema_version": 1,
        "kind": "v934-step3-preagg-addon-candidate",
        "run_id": context["run_id"], "runner": "failsafe",
        "lane": "preagg-addon-lifecycle", "git_head": context["git_head"],
        "contract_sha256": context["contract_sha256"],
        "source_sha256": context["source_sha256"],
        "run_context_sha256": sha256(context_path),
        "summary_sha256": sha256(summary_path),
        "run_status_sha256": sha256(run_root / "run-status.env"),
        "parent": {field.removeprefix("parent_"): context[field] for field in (
            "parent_authority_kind", "parent_run_id", "parent_git_head",
            "parent_contract_sha256", "parent_source_sha256",
            "parent_outer_marker_sha256")},
        "totals": contract["totals"],
        "resource_residue": "0/0/0",
        "artifacts": artifacts,
    }
    write_json(run_root / "candidate-manifest.json", candidate)


def verify_candidate(root: Path, contract_path: Path, run_root: Path) -> None:
    contract = validate_contract(root, contract_path)
    context = validate_context(run_root / "run-context.json", contract_path,
                               binding_source_sha(contract))
    validate_parent_context(root, run_root / "run-context.json", context)
    candidate_path = run_root / "candidate-manifest.json"
    candidate = read_json(candidate_path, "E_CANDIDATE")
    if not isinstance(candidate, dict) or set(candidate) != {
        "schema_version", "kind", "run_id", "runner", "lane", "git_head",
        "contract_sha256", "source_sha256", "run_context_sha256",
        "summary_sha256", "run_status_sha256", "parent", "totals",
        "resource_residue", "artifacts",
    }:
        fail("E_CANDIDATE", "candidate fields differ")
    expected_parent = {field.removeprefix("parent_"): context[field] for field in (
        "parent_authority_kind", "parent_run_id", "parent_git_head",
        "parent_contract_sha256", "parent_source_sha256",
        "parent_outer_marker_sha256")}
    if candidate.get("schema_version") != 1 \
            or candidate.get("kind") != "v934-step3-preagg-addon-candidate" \
            or candidate.get("runner") != "failsafe" \
            or candidate.get("lane") != "preagg-addon-lifecycle" \
            or candidate.get("run_id") != context["run_id"] \
            or candidate.get("git_head") != context["git_head"] \
            or candidate.get("contract_sha256") != context["contract_sha256"] \
            or candidate.get("source_sha256") != context["source_sha256"] \
            or candidate.get("parent") != expected_parent \
            or candidate.get("totals") != contract["totals"] \
            or candidate.get("resource_residue") != "0/0/0":
        fail("E_CANDIDATE", "candidate identity or totals differ")
    expected = []
    for path in sorted(run_root.rglob("*")):
        if path.is_symlink():
            fail("E_CANDIDATE", f"symlink is forbidden in evidence: {path}")
        if path.is_file() and path != candidate_path:
            expected.append({"path": path.relative_to(run_root).as_posix(),
                             "sha256": sha256(path), "size": path.stat().st_size})
    if candidate.get("artifacts") != expected:
        fail("E_CANDIDATE", "candidate artifact closure differs")
    for variant in VARIANTS:
        verify_variant(run_root, variant, context)
    validate_passed_status(run_root, context)
    summary = parse_env(run_root / "summary.env")
    if list(summary) != SUMMARY_KEYS or summary != expected_summary(context):
        fail("E_CANDIDATE", "green summary proof differs")
    if candidate.get("run_context_sha256") != sha256(run_root / "run-context.json") \
            or candidate.get("summary_sha256") != sha256(run_root / "summary.env") \
            or candidate.get("run_status_sha256") != sha256(run_root / "run-status.env"):
        fail("E_CANDIDATE", "candidate top-level evidence digest differs")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser()
    result.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    result.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    sub = result.add_subparsers(dest="command", required=True)
    sub.add_parser("validate-contract")
    sub.add_parser("source-sha")
    collect_parser = sub.add_parser("collect")
    collect_parser.add_argument("--variant", required=True)
    collect_parser.add_argument("--reports-dir", type=Path, required=True)
    collect_parser.add_argument("--marker", type=Path, required=True)
    collect_parser.add_argument("--run-context", type=Path, required=True)
    collect_parser.add_argument("--output-dir", type=Path, required=True)
    for command in ("finalize", "verify-candidate"):
        command_parser = sub.add_parser(command)
        command_parser.add_argument("--run-root", type=Path, required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    root = args.root.resolve()
    contract_path = args.contract.resolve()
    if args.command == "validate-contract":
        validate_contract(root, contract_path)
    elif args.command == "source-sha":
        print(binding_source_sha(validate_contract(root, contract_path)))
    elif args.command == "collect":
        collect(root, contract_path, args.variant, args.reports_dir, args.marker,
                args.run_context, args.output_dir)
    elif args.command == "finalize":
        finalize(root, contract_path, args.run_root)
    elif args.command == "verify-candidate":
        verify_candidate(root, contract_path, args.run_root)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except EvidenceError as error:
        print(f"{PREFIX} ERROR {error.code}: {error}", file=sys.stderr)
        raise SystemExit(1)
