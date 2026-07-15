#!/usr/bin/env python3
"""Validate and bind the complete 9.3.4 Step 3 required matrix.

The database (29/370) and external (16/76) children remain independently
verifiable authorities.  This tool proves that one parent invocation consumed
their exact, disjoint deferred execution-key subsets and records the PreAgg
Addon lifecycle as a required companion without changing the frozen 45/446
required total.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
from typing import Any, Iterable, Sequence


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_CONTRACT = ROOT / "scripts/v934/step3/step3-required-contract.json"
PREFIX = "[v934-step3-required-tool]"
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
HEAD_PATTERN = re.compile(r"[0-9a-f]{40}")
RUN_ID_PATTERN = re.compile(r"[A-Za-z0-9._-]+")
PARENT_ENV_FIELDS = (
    "authority_kind",
    "run_id",
    "git_head",
    "contract_sha256",
    "source_sha256",
    "outer_marker_sha256",
    "outer_marker_path",
)
REQUIRED_TOTALS = {
    "database_variants": 7,
    "external_variants": 7,
    "execution_keys": 45,
    "reports": 45,
    "testcase_nodes": 446,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
}
ADDON_TOTALS = {
    "variants": 2,
    "reports": 2,
    "testcase_nodes": 6,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
}
CHILD_ORDER = ("addon-companion", "database-matrix", "external-matrix")
REDIS_RESOURCE_STATE_PROBES = (
    ("wrong-container-identity", "E_RESOURCE_IDENTITY"),
    ("wrong-mount-identity", "E_RESOURCE_MOUNT"),
    ("dirty-state", "E_RESOURCE_DIRTY"),
    ("forced-cleanup-failure", "E_RESOURCE_CLEANUP"),
)


class RequiredMatrixError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def reject(code: str, message: str) -> None:
    raise RequiredMatrixError(code, message)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def ensure_regular(path: Path, code: str, label: str) -> Path:
    absolute = path.absolute()
    if absolute.is_symlink() or not absolute.is_file():
        reject(code, f"{label} is not a regular file: {absolute}")
    return absolute.resolve(strict=True)


def ensure_directory(path: Path, code: str, label: str) -> Path:
    absolute = path.absolute()
    if absolute.is_symlink() or not absolute.is_dir():
        reject(code, f"{label} is not a regular directory: {absolute}")
    return absolute.resolve(strict=True)


def sha256_file(path: Path) -> str:
    path = ensure_regular(path, "E_ARTIFACT", "SHA-256 input")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path: Path, code: str = "E_SCHEMA") -> dict[str, Any]:
    path = ensure_regular(path, code, "JSON artifact")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RequiredMatrixError(code, f"invalid JSON: {path}") from error
    if not isinstance(value, dict):
        reject(code, f"JSON root is not an object: {path}")
    return value


def write_json(path: Path, value: Any) -> None:
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, path)


def read_env(path: Path, expected: Iterable[str] | None = None) -> dict[str, str]:
    path = ensure_regular(path, "E_STATUS", "environment evidence")
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or "=" not in line:
            reject("E_STATUS", f"malformed environment line in {path}: {line!r}")
        key, value = line.split("=", 1)
        if not key or key in values:
            reject("E_STATUS", f"duplicate/blank environment key in {path}: {key!r}")
        values[key] = value
    if expected is not None and set(values) != set(expected):
        reject("E_STATUS", f"environment fields differ in {path}: {sorted(values)}")
    return values


def parse_timestamp(value: Any, code: str = "E_MARKER") -> dt.datetime:
    if not isinstance(value, str):
        reject(code, "timestamp is not a string")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as error:
        raise RequiredMatrixError(code, f"timestamp is invalid: {value}") from error
    if parsed.tzinfo is None:
        reject(code, "timestamp has no timezone")
    return parsed


def current_head(repo: Path = ROOT) -> str:
    completed = subprocess.run(
        ["git", "-C", str(repo), "rev-parse", "HEAD"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    value = completed.stdout.strip()
    if HEAD_PATTERN.fullmatch(value) is None:
        reject("E_GIT_HEAD", f"invalid Git HEAD: {value}")
    return value


def safe_repo_path(repo: Path, value: Any, label: str) -> Path:
    if not isinstance(value, str) or not value or "\\" in value:
        reject("E_CONTRACT", f"invalid {label} path")
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts or relative.as_posix() != value:
        reject("E_CONTRACT", f"unsafe {label} path: {value}")
    path = (repo / relative).absolute()
    try:
        path.resolve(strict=False).relative_to(repo.resolve())
    except ValueError as error:
        raise RequiredMatrixError("E_CONTRACT", f"{label} path escapes repository") from error
    return path


def read_tsv(path: Path) -> list[dict[str, str]]:
    path = ensure_regular(path, "E_INVENTORY", "TSV inventory")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames is None or len(reader.fieldnames) != len(set(reader.fieldnames)):
            reject("E_INVENTORY", f"TSV header is missing/duplicated: {path}")
        return list(reader)


def load_contract(
    repo: Path = ROOT,
    path: Path = DEFAULT_CONTRACT,
    *,
    require_ready: bool,
) -> dict[str, Any]:
    repo = repo.resolve()
    path = ensure_regular(path, "E_CONTRACT", "Step 3 required contract")
    contract = read_json(path, "E_CONTRACT")
    expected_fields = {
        "schema_version",
        "kind",
        "integration_status",
        "lane",
        "runner",
        "required",
        "bindings",
        "child_order",
        "children",
        "required_totals",
        "addon_companion",
        "optional_execution",
        "negative_probes",
        "source_policy",
    }
    if set(contract) != expected_fields:
        reject("E_CONTRACT", f"contract fields differ: {sorted(contract)}")
    if (
        contract["schema_version"] != 1
        or contract["kind"] != "v934-step3-required-matrix-contract"
        or contract["lane"] != "step3-required-matrix"
        or contract["runner"] != "orchestrator"
        or contract["required"] is not True
        or contract["integration_status"] not in {"pending", "ready"}
        or contract["child_order"] != list(CHILD_ORDER)
        or contract["required_totals"] != REQUIRED_TOTALS
    ):
        reject("E_CONTRACT", "contract identity or totals differ")
    if require_ready and contract["integration_status"] != "ready":
        reject("E_INTEGRATION_PENDING", "child bindings have not been frozen")

    bindings = contract["bindings"]
    if not isinstance(bindings, dict) or not bindings:
        reject("E_CONTRACT", "bindings are missing")
    resolved_bindings: dict[str, Path] = {}
    for name, row in bindings.items():
        if not isinstance(row, dict) or set(row) != {"path", "sha256"}:
            reject("E_CONTRACT", f"malformed binding: {name}")
        resolved = safe_repo_path(repo, row["path"], f"binding {name}")
        if SHA_PATTERN.fullmatch(str(row["sha256"])) is None:
            reject("E_CONTRACT", f"binding SHA-256 is invalid: {name}")
        if require_ready:
            ensure_regular(resolved, "E_BINDING", f"binding {name}")
            if sha256_file(resolved) != row["sha256"]:
                reject("E_BINDING", f"binding differs: {name}")
        resolved_bindings[name] = resolved
    required_bindings = {
        "authority_runner_lib",
        "addon_contract",
        "addon_report_tool",
        "database_contract",
        "database_report_tool",
        "database_runner",
        "deferred_inventory",
        "external_contract",
        "external_report_tool",
        "external_runner",
        "addon_runner",
        "required_report_tool",
        "required_runner",
        "step4_prereq_multithread_executor",
        "step4_prereq_multithread_executor_test",
        "successor_manifest",
    }
    if set(bindings) != required_bindings:
        reject("E_CONTRACT", f"binding set differs: {sorted(bindings)}")

    children = contract["children"]
    if not isinstance(children, dict) or set(children) != set(CHILD_ORDER):
        reject("E_CONTRACT", "child set differs")
    expected_children = {
        "addon-companion": {
            "runner_binding": "addon_runner",
            "contract_binding": "addon_contract",
            "report_tool_binding": "addon_report_tool",
            "run_root": "target/v934-step3-preagg-addon/runs/{run_id}",
            "candidate": "candidate-manifest.json",
            "included_in_required_totals": False,
            "required_subgates": [],
        },
        "database-matrix": {
            "runner_binding": "database_runner",
            "contract_binding": "database_contract",
            "report_tool_binding": "database_report_tool",
            "run_root": "target/v934-step3-database-matrix/runs/{run_id}",
            "candidate": "none",
            "included_in_required_totals": True,
            "required_subgates": ["database-resource-state-negatives"],
        },
        "external-matrix": {
            "runner_binding": "external_runner",
            "contract_binding": "external_contract",
            "report_tool_binding": "external_report_tool",
            "run_root": "target/v934-step3-external-matrix/runs/{run_id}",
            "candidate": "candidate-manifest.json",
            "included_in_required_totals": True,
            "required_subgates": ["redis-resource-state-negatives"],
        },
    }
    for name, fixed in expected_children.items():
        row = children[name]
        if row != fixed:
            reject("E_CONTRACT", f"child definition differs: {name}")

    companion = contract["addon_companion"]
    if companion != {
        "required": True,
        "included_in_required_totals": False,
        "totals": ADDON_TOTALS,
        "resource_residue": "0/0/0",
    }:
        reject("E_CONTRACT", "Addon companion contract differs")
    optional = contract["optional_execution"]
    expected_optional_fields = {
        "execution_key",
        "report_fqcn",
        "lane",
        "variant_key",
        "owner",
        "optional_reason",
        "review_at",
        "disposition",
    }
    if not isinstance(optional, dict) or set(optional) != expected_optional_fields:
        reject("E_CONTRACT", "optional execution schema differs")
    if optional["disposition"] != "reviewed-optional-excluded" or not optional["optional_reason"]:
        reject("E_CONTRACT", "optional execution disposition differs")
    try:
        dt.date.fromisoformat(optional["review_at"])
    except (TypeError, ValueError) as error:
        raise RequiredMatrixError("E_CONTRACT", "optional review date is invalid") from error

    probes = contract["negative_probes"]
    if (
        not isinstance(probes, list)
        or not probes
        or any(not isinstance(row, dict) or set(row) != {"probe", "expected_error"} for row in probes)
        or len({row["probe"] for row in probes}) != len(probes)
    ):
        reject("E_CONTRACT", "negative probes are malformed or duplicated")
    if contract["source_policy"] != {
        "inventory": "git-tracked-plus-unignored-untracked",
        "special_files": "reject",
        "before_after": "byte-identical",
    }:
        reject("E_CONTRACT", "source policy differs")

    deferred_path = resolved_bindings["deferred_inventory"]
    if deferred_path.exists():
        rows = read_tsv(deferred_path)
        required = [row for row in rows if row.get("required") == "true"]
        optional_rows = [row for row in rows if row.get("required") == "false"]
        database = [row for row in required if row.get("lane") == "database-contract-matrix"]
        external = [row for row in required if row.get("lane", "").startswith("external-")]
        if len(rows) != 46 or len(required) != 45 or len(database) != 29 or len(external) != 16:
            reject("E_INVENTORY", "deferred inventory cardinality differs")
        if len({row["execution_key"] for row in rows}) != 46:
            reject("E_INVENTORY", "deferred execution keys are duplicated")
        if len(optional_rows) != 1:
            reject("E_INVENTORY", "optional deferred execution cardinality differs")
        optional_row = optional_rows[0]
        for key in (
            "execution_key", "report_fqcn", "lane", "variant_key", "owner",
            "optional_reason", "review_at",
        ):
            if optional[key] != optional_row[key]:
                reject("E_OPTIONAL_EXECUTION", f"optional execution binding differs: {key}")
        contract["_deferred_rows"] = rows
        contract["_required_rows"] = required
    elif require_ready:
        reject("E_BINDING", "deferred inventory is missing")
    contract["_repo"] = repo
    contract["_path"] = path
    contract["_sha256"] = sha256_file(path)
    contract["_bindings"] = resolved_bindings
    return contract


def source_rows(repo: Path) -> list[dict[str, Any]]:
    completed = subprocess.run(
        ["git", "-C", str(repo), "ls-files", "-co", "--exclude-standard", "-z"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    raw_paths = completed.stdout.split(b"\0")
    paths = sorted({value.decode("utf-8") for value in raw_paths if value})
    rows: list[dict[str, Any]] = []
    for relative in paths:
        if "\t" in relative or "\n" in relative or "\r" in relative:
            reject("E_SOURCE", f"source path contains a prohibited character: {relative!r}")
        path = safe_repo_path(repo, relative, "source")
        if path.is_symlink() or not path.is_file():
            reject("E_SOURCE", f"source is not a regular file: {relative}")
        rows.append({
            "path": relative,
            "size_bytes": path.stat().st_size,
            "sha256": sha256_file(path),
        })
    if not rows:
        reject("E_SOURCE", "source inventory is empty")
    return rows


def write_source_seal(repo: Path, output: Path) -> str:
    rows = source_rows(repo)
    content = "path\tsize_bytes\tsha256\n" + "".join(
        f"{row['path']}\t{row['size_bytes']}\t{row['sha256']}\n" for row in rows
    )
    temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, output)
    return sha256_bytes(content.encode("utf-8"))


def expected_parent_context(outer: dict[str, Any], outer_path: Path) -> dict[str, str]:
    return {
        "authority_kind": "step3-required-matrix",
        "run_id": outer["run_id"],
        "git_head": outer["git_head"],
        "contract_sha256": outer["contract_sha256"],
        "source_sha256": outer["source_sha256"],
        "outer_marker_sha256": sha256_file(outer_path),
        "outer_marker_path": str(outer_path.resolve()),
    }


def load_outer(path: Path, contract: dict[str, Any], require_head: bool = True) -> dict[str, Any]:
    marker = read_json(path, "E_MARKER")
    fields = {
        "schema_version", "kind", "run_id", "lane", "runner", "git_head",
        "contract_sha256", "source_sha256", "started_at", "status",
        "child_order", "child_contracts",
    }
    if set(marker) != fields:
        reject("E_MARKER", f"outer marker fields differ: {sorted(marker)}")
    if (
        marker["schema_version"] != 1
        or marker["kind"] != "v934-step3-required-matrix-run"
        or marker["lane"] != "step3-required-matrix"
        or marker["runner"] != "orchestrator"
        or marker["contract_sha256"] != contract["_sha256"]
        or marker["status"] != "started"
        or marker["child_order"] != list(CHILD_ORDER)
        or RUN_ID_PATTERN.fullmatch(str(marker["run_id"])) is None
        or marker["run_id"] in {".", ".."}
        or HEAD_PATTERN.fullmatch(str(marker["git_head"])) is None
        or SHA_PATTERN.fullmatch(str(marker["source_sha256"])) is None
    ):
        reject("E_MARKER", "outer marker identity differs")
    expected_child_contracts = {
        "addon-companion": contract["bindings"]["addon_contract"]["sha256"],
        "database-matrix": contract["bindings"]["database_contract"]["sha256"],
        "external-matrix": contract["bindings"]["external_contract"]["sha256"],
    }
    if marker["child_contracts"] != expected_child_contracts:
        reject("E_MARKER", "child contract binding differs")
    parse_timestamp(marker["started_at"])
    if require_head and marker["git_head"] != current_head(contract["_repo"]):
        reject("E_GIT_HEAD", "outer marker does not match current Git HEAD")
    marker["_sha256"] = sha256_file(path)
    marker["_mtime_ns"] = ensure_regular(path, "E_MARKER", "outer marker").stat().st_mtime_ns
    return marker


def verify_parent_file(root: Path, outer: dict[str, Any], outer_path: Path) -> None:
    observed = read_env(root / "parent-context.env", PARENT_ENV_FIELDS)
    if observed != expected_parent_context(outer, outer_path):
        reject("E_PARENT_CONTEXT", f"child parent context differs: {root}")


def verify_status(root: Path, run_id: str, git_head: str) -> dict[str, str]:
    status = read_env(root / "run-status.env")
    if (
        status.get("run_id") != run_id
        or status.get("git_head") != git_head
        or status.get("last_phase") != "completed"
        or status.get("exit_code") != "0"
        or status.get("status") != "passed"
    ):
        reject("E_CHILD_STATUS", f"child durable status is not passed: {root}")
    return status


def verify_child_context(
    root: Path,
    expected_kind: str,
    expected_lane: str,
    expected_runner: str,
    expected_contract: str,
    outer: dict[str, Any],
) -> dict[str, Any]:
    context = read_json(root / "run-context.json", "E_CHILD_CONTEXT")
    for key, expected in {
        "schema_version": 1,
        "kind": expected_kind,
        "run_id": outer["run_id"],
        "lane": expected_lane,
        "runner": expected_runner,
        "git_head": outer["git_head"],
        "contract_sha256": expected_contract,
        "status": "started",
    }.items():
        if context.get(key) != expected:
            reject("E_CHILD_CONTEXT", f"child context differs: {expected_lane}/{key}")
    if ensure_regular(root / "run-context.json", "E_CHILD_CONTEXT", "child context").stat().st_mtime_ns < outer["_mtime_ns"]:
        reject("E_CROSS_RUN_SPLICE", f"child context predates parent marker: {expected_lane}")
    return context


def run_verifier(command: list[str], label: str) -> None:
    completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if completed.returncode != 0:
        reject("E_CHILD_EVIDENCE", f"{label} verifier failed: {completed.stderr.strip()}")


def collect_database(
    contract: dict[str, Any], root: Path, outer: dict[str, Any], outer_path: Path
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    ensure_directory(root, "E_CHILD_MISSING", "database child root")
    verify_parent_file(root, outer, outer_path)
    verify_status(root, outer["run_id"], outer["git_head"])
    database_contract_sha = contract["bindings"]["database_contract"]["sha256"]
    verify_child_context(
        root,
        "v934-step3-database-matrix-outer-run",
        "database-contract-matrix",
        "failsafe",
        database_contract_sha,
        outer,
    )
    final = root / "final/report-manifest.json"
    run_verifier(
        [
            sys.executable,
            str(contract["_bindings"]["database_report_tool"]),
            "verify-final",
            "--outer-marker", str(root / "run-context.json"),
            "--manifest", str(final),
        ],
        "database final",
    )
    manifest = read_json(final, "E_CHILD_EVIDENCE")
    if (
        manifest.get("run_id") != outer["run_id"]
        or manifest.get("contract_sha256") != database_contract_sha
        or manifest.get("status") != "passed"
        or manifest.get("totals") != {
            "database_cells": 5,
            "variants": 7,
            "reports": 29,
            "testcase_nodes": 370,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        }
    ):
        reject("E_MATRIX_TOTAL", "database final totals/identity differ")
    state_manifest_path = root / "state-negative/manifest.json"
    run_verifier(
        [
            sys.executable,
            str(contract["_repo"] / "scripts/v934/step3/database_state_negative_tool.py"),
            "verify", "--manifest", str(state_manifest_path),
        ],
        "database resource-state companion",
    )
    state_manifest = read_json(state_manifest_path, "E_CHILD_EVIDENCE")
    expected_state_totals = {
        "probes": 18,
        "evidence_tamper": 10,
        "runtime_lightweight": 2,
        "runtime_dynamic": 6,
        "signals": 3,
        "failed": 0,
    }
    if (
        state_manifest.get("kind") != "v934-step3-database-state-negative-manifest"
        or state_manifest.get("run_id") != outer["run_id"]
        or state_manifest.get("mode") != "all"
        or state_manifest.get("scope") != "database-companion"
        or state_manifest.get("git_head") != outer["git_head"]
        or state_manifest.get("database_contract_sha256") != database_contract_sha
        or state_manifest.get("database_outer_marker_sha256")
        != sha256_file(root / "run-context.json")
        or state_manifest.get("complete") is not True
        or state_manifest.get("totals") != expected_state_totals
    ):
        reject("E_CHILD_EVIDENCE", "database resource-state companion differs")
    database_summary = read_env(root / "summary.env")
    if (
        database_summary.get("outer_marker_sha256")
        != state_manifest.get("database_outer_marker_sha256")
        or database_summary.get("database_state_probes") != "18/18"
        or database_summary.get("database_state_complete") != "true"
        or database_summary.get("database_state_manifest_sha256")
        != sha256_file(state_manifest_path)
        or database_summary.get("database_state_contract_sha256")
        != state_manifest.get("state_contract_sha256")
    ):
        reject("E_CHILD_EVIDENCE", "database resource-state summary binding differs")
    records: list[dict[str, Any]] = []
    for variant in manifest.get("variants", []):
        for row in variant.get("reports", []):
            records.append({
                "execution_key": row.get("execution_key"),
                "testcase_nodes": row.get("testcase_nodes"),
                "failures": row.get("failures"),
                "errors": row.get("errors"),
                "skipped": row.get("skipped"),
                "lane": "database-contract-matrix",
            })
    return records, manifest


def collect_external(
    contract: dict[str, Any], root: Path, outer: dict[str, Any], outer_path: Path
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    ensure_directory(root, "E_CHILD_MISSING", "external child root")
    verify_parent_file(root, outer, outer_path)
    verify_status(root, outer["run_id"], outer["git_head"])
    external_contract_sha = contract["bindings"]["external_contract"]["sha256"]
    verify_child_context(
        root,
        "v934-step3-external-matrix-outer-run",
        "external-matrix",
        "failsafe",
        external_contract_sha,
        outer,
    )
    candidate = root / "candidate-manifest.json"
    run_verifier(
        [
            sys.executable,
            str(contract["_bindings"]["external_report_tool"]),
            "verify-candidate",
            "--candidate", str(candidate),
        ],
        "external candidate",
    )
    manifest = read_json(root / "final/report-manifest.json", "E_CHILD_EVIDENCE")
    if (
        manifest.get("run_id") != outer["run_id"]
        or manifest.get("git_head") != outer["git_head"]
        or manifest.get("contract_sha256") != external_contract_sha
        or manifest.get("complete") is not True
        or manifest.get("totals") != {
            "variants": 7,
            "reports": 16,
            "testcase_nodes": 76,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        }
    ):
        reject("E_MATRIX_TOTAL", "external final totals/identity differ")
    resource_state_path = root / "aggregate/redis-resource-state-negatives.tsv"
    resource_state_rows = read_tsv(resource_state_path)
    if (list(resource_state_rows[0]) if resource_state_rows else []) != [
        "probe", "expected_error", "actual_error", "status"
    ]:
        reject("E_CHILD_EVIDENCE", "Redis resource-state negative header differs")
    observed_resource_state = [
        (row.get("probe"), row.get("expected_error"), row.get("actual_error"), row.get("status"))
        for row in resource_state_rows
    ]
    expected_resource_state = [
        (probe, error, error, "passed") for probe, error in REDIS_RESOURCE_STATE_PROBES
    ]
    if observed_resource_state != expected_resource_state:
        reject("E_CHILD_EVIDENCE", "Redis resource-state negative evidence differs")
    records: list[dict[str, Any]] = []
    for variant in manifest.get("variants", []):
        path = root / "final" / variant["manifest_path"]
        child = read_json(path, "E_CHILD_EVIDENCE")
        if child.get("run_id") != outer["run_id"] or child.get("git_head") != outer["git_head"]:
            reject("E_CROSS_RUN_SPLICE", "external variant context differs")
        totals = child.get("totals", {})
        if any(totals.get(key) != 0 for key in ("failures", "errors", "skipped")):
            reject("E_REPORT_OUTCOME", "external variant is not F0/E0/S0")
        for row in child.get("reports", []):
            records.append({
                "execution_key": row.get("execution_key"),
                "testcase_nodes": row.get("expected_testcase_nodes"),
                "failures": 0,
                "errors": 0,
                "skipped": 0,
                "lane": child.get("lane"),
            })
    return records, manifest


def artifact_rows(root: Path, exclude: set[str] | None = None) -> list[dict[str, Any]]:
    root = ensure_directory(root, "E_ARTIFACT", "artifact tree")
    excluded = exclude or set()
    rows: list[dict[str, Any]] = []
    for path in sorted(root.rglob("*"), key=lambda value: value.relative_to(root).as_posix()):
        relative = path.relative_to(root).as_posix()
        if relative in excluded:
            continue
        if path.is_symlink():
            reject("E_ARTIFACT", f"artifact tree contains symlink: {path}")
        if path.is_file():
            rows.append({
                "path": relative,
                "sha256": sha256_file(path),
                "size_bytes": path.stat().st_size,
                "mtime_ns": path.stat().st_mtime_ns,
            })
        elif not path.is_dir():
            reject("E_ARTIFACT", f"artifact tree contains special file: {path}")
    return rows


def tree_digest(rows: list[dict[str, Any]]) -> str:
    payload = "".join(
        f"{row['path']}\0{row['sha256']}\0{row['size_bytes']}\0{row['mtime_ns']}\n"
        for row in rows
    )
    return sha256_bytes(payload.encode("utf-8"))


def verify_addon_candidate(
    contract: dict[str, Any], root: Path, outer: dict[str, Any], outer_path: Path
) -> dict[str, Any]:
    ensure_directory(root, "E_ADDON_COMPANION", "Addon child root")
    verify_parent_file(root, outer, outer_path)
    verify_status(root, outer["run_id"], outer["git_head"])
    addon_contract_sha = contract["bindings"]["addon_contract"]["sha256"]
    context = verify_child_context(
        root,
        "v934-step3-preagg-addon-lifecycle-run",
        "preagg-addon-lifecycle",
        "failsafe",
        addon_contract_sha,
        outer,
    )
    run_verifier(
        [
            sys.executable,
            str(contract["_bindings"]["addon_report_tool"]),
            "--root", str(contract["_repo"]),
            "--contract", str(contract["_bindings"]["addon_contract"]),
            "verify-candidate",
            "--run-root", str(root),
        ],
        "Addon candidate",
    )
    candidate_path = root / "candidate-manifest.json"
    candidate = read_json(candidate_path, "E_ADDON_COMPANION")
    fields = {
        "schema_version", "kind", "run_id", "runner", "lane", "git_head",
        "contract_sha256", "run_context_sha256", "summary_sha256",
        "run_status_sha256", "source_sha256", "parent", "totals",
        "resource_residue", "artifacts",
    }
    if set(candidate) != fields:
        reject("E_ADDON_COMPANION", "Addon candidate fields differ")
    expected_parent = expected_parent_context(outer, outer_path)
    expected_parent.pop("outer_marker_path")
    if (
        candidate["schema_version"] != 1
        or candidate["kind"] != "v934-step3-preagg-addon-candidate"
        or candidate["run_id"] != outer["run_id"]
        or candidate["runner"] != "failsafe"
        or candidate["lane"] != "preagg-addon-lifecycle"
        or candidate["git_head"] != outer["git_head"]
        or candidate["contract_sha256"] != addon_contract_sha
        or candidate["source_sha256"] != context.get("source_sha256")
        or candidate["parent"] != expected_parent
        or candidate["totals"] != ADDON_TOTALS
        or candidate["resource_residue"] != "0/0/0"
    ):
        reject("E_ADDON_COMPANION", "Addon candidate identity/totals differ")
    summary = read_env(root / "summary.env")
    expected_summary = {
        "run_id": outer["run_id"],
        "runner": "failsafe",
        "lane": "preagg-addon-lifecycle",
        "git_head": outer["git_head"],
        "contract_sha256": addon_contract_sha,
        "parent_authority_kind": "step3-required-matrix",
        "parent_run_id": outer["run_id"],
        "parent_git_head": outer["git_head"],
        "parent_contract_sha256": outer["contract_sha256"],
        "parent_source_sha256": outer["source_sha256"],
        "parent_outer_marker_sha256": outer["_sha256"],
        "variants": "2",
        "reports": "2",
        "testcase_nodes": "6",
        "failures": "0",
        "errors": "0",
        "skipped": "0",
        "sqlite_reports": "1",
        "sqlite_testcase_nodes": "3",
        "mysql57_reports": "1",
        "mysql57_testcase_nodes": "3",
        "resource_residue": "0/0/0",
        "status": "passed",
    }
    for key, value in expected_summary.items():
        if summary.get(key) != value:
            reject("E_ADDON_COMPANION", f"Addon summary differs: {key}")
    if set(summary) != set(expected_summary):
        reject("E_ADDON_COMPANION", "Addon summary fields differ")
    if (
        candidate["run_context_sha256"] != sha256_file(root / "run-context.json")
        or candidate["summary_sha256"] != sha256_file(root / "summary.env")
        or candidate["run_status_sha256"] != sha256_file(root / "run-status.env")
    ):
        reject("E_ADDON_COMPANION", "Addon candidate top-level digest differs")
    records = candidate["artifacts"]
    if not isinstance(records, list) or not records:
        reject("E_ADDON_COMPANION", "Addon candidate artifacts are empty")
    if records != sorted(records, key=lambda row: row.get("path", "")):
        reject("E_ADDON_COMPANION", "Addon candidate artifacts are not sorted")
    observed_paths: set[str] = set()
    for row in records:
        if not isinstance(row, dict) or set(row) != {"path", "sha256", "size"}:
            reject("E_ADDON_COMPANION", "Addon artifact schema differs")
        relative = row["path"]
        if relative in observed_paths:
            reject("E_ADDON_COMPANION", "Addon artifact path duplicated")
        observed_paths.add(relative)
        path = safe_child_relative(root, relative, "Addon artifact")
        if sha256_file(path) != row["sha256"] or path.stat().st_size != row["size"]:
            reject("E_ADDON_COMPANION", f"Addon artifact differs: {relative}")
    actual = {row["path"] for row in artifact_rows(root, {"candidate-manifest.json"})}
    if observed_paths != actual:
        reject("E_ADDON_COMPANION", "Addon candidate artifact set differs")
    return candidate


def safe_child_relative(root: Path, value: Any, label: str) -> Path:
    if not isinstance(value, str) or not value or "\\" in value:
        reject("E_ARTIFACT", f"invalid {label} path")
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts or relative.as_posix() != value:
        reject("E_ARTIFACT", f"unsafe {label} path: {value}")
    path = (root / relative).absolute()
    try:
        resolved = path.resolve(strict=True)
        resolved.relative_to(root.resolve())
    except (FileNotFoundError, ValueError) as error:
        raise RequiredMatrixError("E_ARTIFACT", f"{label} escapes/is missing: {value}") from error
    return ensure_regular(resolved, "E_ARTIFACT", label)


def validate_union_ledger(
    database: list[dict[str, Any]],
    external: list[dict[str, Any]],
    expected_keys: set[str],
    optional_key: str,
    *,
    addon_ok: bool = True,
    parent_ok: bool = True,
    child_status_ok: bool = True,
    source_ok: bool = True,
    evidence_ok: bool = True,
    sensitive_ok: bool = True,
    residue_ok: bool = True,
) -> dict[str, int]:
    if not parent_ok:
        reject("E_PARENT_CONTEXT", "child parent context differs")
    if not child_status_ok:
        reject("E_CHILD_STATUS", "child status is not passed")
    if not addon_ok:
        reject("E_ADDON_COMPANION", "required Addon companion differs")
    if not source_ok:
        reject("E_SOURCE_DRIFT", "source before/after differs")
    if not evidence_ok:
        reject("E_CHILD_EVIDENCE", "bound child artifact differs")
    if not sensitive_ok:
        reject("E_SENSITIVE", "sensitive scan did not pass")
    if not residue_ok:
        reject("E_RESOURCE_RESIDUE", "run-owned resource residue is non-zero")

    def keyed(rows: list[dict[str, Any]], label: str) -> dict[str, dict[str, Any]]:
        values: dict[str, dict[str, Any]] = {}
        for row in rows:
            key = row.get("execution_key")
            if not isinstance(key, str) or not key:
                reject("E_EXECUTION_KEY", f"blank execution key in {label}")
            if key in values:
                reject("E_DUPLICATE_KEY", f"duplicate execution key in {label}: {key}")
            if key == optional_key:
                reject("E_OPTIONAL_EXECUTION", "optional LLM execution leaked into required ledger")
            if any(row.get(name) != 0 for name in ("failures", "errors", "skipped")):
                reject("E_REPORT_OUTCOME", f"non-zero report outcome: {key}")
            nodes = row.get("testcase_nodes")
            if not isinstance(nodes, int) or nodes <= 0:
                reject("E_MATRIX_TOTAL", f"invalid testcase cardinality: {key}")
            values[key] = row
        return values

    database_by_key = keyed(database, "database")
    external_by_key = keyed(external, "external")
    overlap = set(database_by_key) & set(external_by_key)
    if overlap:
        reject("E_KEY_OVERLAP", f"database/external keys overlap: {sorted(overlap)}")
    actual = set(database_by_key) | set(external_by_key)
    missing = expected_keys - actual
    extra = actual - expected_keys
    if missing:
        reject("E_REQUIRED_GAP", f"required execution keys are missing: {sorted(missing)}")
    if extra:
        reject("E_EXTRA_KEY", f"unexpected required execution keys: {sorted(extra)}")
    totals = {
        "database_variants": 7,
        "external_variants": 7,
        "execution_keys": len(actual),
        "reports": len(actual),
        "testcase_nodes": sum(row["testcase_nodes"] for row in database + external),
        "failures": 0,
        "errors": 0,
        "skipped": 0,
    }
    if len(database_by_key) != 29 or len(external_by_key) != 16 or totals != REQUIRED_TOTALS:
        reject("E_MATRIX_TOTAL", f"required matrix totals differ: {totals}")
    return totals


def child_root(contract: dict[str, Any], child: str, run_id: str) -> Path:
    pattern = contract["children"][child]["run_root"]
    return safe_repo_path(contract["_repo"], pattern.format(run_id=run_id), f"{child} root")


def child_binding(root: Path, repo: Path) -> dict[str, Any]:
    rows = artifact_rows(root)
    return {
        "root": root.relative_to(repo).as_posix(),
        "artifacts": len(rows),
        "tree_sha256": tree_digest(rows),
    }


def build_final(
    contract: dict[str, Any],
    outer_path: Path,
    database_root: Path,
    external_root: Path,
    addon_root: Path,
    source_before: Path,
    source_after: Path,
) -> dict[str, Any]:
    outer = load_outer(outer_path, contract)
    canonical = {
        "database-matrix": child_root(contract, "database-matrix", outer["run_id"]),
        "external-matrix": child_root(contract, "external-matrix", outer["run_id"]),
        "addon-companion": child_root(contract, "addon-companion", outer["run_id"]),
    }
    supplied = {
        "database-matrix": ensure_directory(database_root, "E_CHILD_MISSING", "database root"),
        "external-matrix": ensure_directory(external_root, "E_CHILD_MISSING", "external root"),
        "addon-companion": ensure_directory(addon_root, "E_CHILD_MISSING", "Addon root"),
    }
    if any(supplied[name] != canonical[name].resolve() for name in supplied):
        reject("E_CHILD_CONTEXT", "child root is not at its canonical run path")
    before = ensure_regular(source_before, "E_SOURCE", "source-before seal")
    after = ensure_regular(source_after, "E_SOURCE", "source-after seal")
    if before.read_bytes() != after.read_bytes() or sha256_file(before) != outer["source_sha256"]:
        reject("E_SOURCE_DRIFT", "top-level protected source changed during execution")

    database, database_manifest = collect_database(contract, supplied["database-matrix"], outer, outer_path)
    external, external_manifest = collect_external(contract, supplied["external-matrix"], outer, outer_path)
    addon = verify_addon_candidate(contract, supplied["addon-companion"], outer, outer_path)
    required_rows = contract["_required_rows"]
    expected_keys = {row["execution_key"] for row in required_rows}
    optional_key = contract["optional_execution"]["execution_key"]
    totals = validate_union_ledger(database, external, expected_keys, optional_key)
    ordered_records = sorted(database + external, key=lambda row: row["execution_key"])
    return {
        "schema_version": 1,
        "kind": "v934-step3-required-matrix-final",
        "status": "passed",
        "complete": True,
        "run_id": outer["run_id"],
        "runner": "orchestrator",
        "lane": "step3-required-matrix",
        "git_head": outer["git_head"],
        "contract_sha256": contract["_sha256"],
        "outer_marker_sha256": outer["_sha256"],
        "source_sha256": outer["source_sha256"],
        "deferred_inventory_sha256": contract["bindings"]["deferred_inventory"]["sha256"],
        "children": {
            "addon-companion": {
                **child_binding(supplied["addon-companion"], contract["_repo"]),
                "candidate_sha256": sha256_file(supplied["addon-companion"] / "candidate-manifest.json"),
                "included_in_required_totals": False,
                "totals": addon["totals"],
            },
            "database-matrix": {
                **child_binding(supplied["database-matrix"], contract["_repo"]),
                "final_manifest_sha256": sha256_file(supplied["database-matrix"] / "final/report-manifest.json"),
                "included_in_required_totals": True,
                "totals": database_manifest["totals"],
            },
            "external-matrix": {
                **child_binding(supplied["external-matrix"], contract["_repo"]),
                "candidate_sha256": sha256_file(supplied["external-matrix"] / "candidate-manifest.json"),
                "included_in_required_totals": True,
                "totals": external_manifest["totals"],
            },
        },
        "required_execution_keys": ordered_records,
        "partition": {
            "database_keys": 29,
            "external_keys": 16,
            "overlap_keys": 0,
            "gap_keys": 0,
            "extra_keys": 0,
        },
        "optional_execution": contract["optional_execution"],
        "addon_companion": {
            "required": True,
            "included_in_required_totals": False,
            "resource_residue": "0/0/0",
            "totals": ADDON_TOTALS,
        },
        "totals": totals,
    }


def finalize(
    contract: dict[str, Any],
    outer: Path,
    database_root: Path,
    external_root: Path,
    addon_root: Path,
    source_before: Path,
    source_after: Path,
    output: Path,
) -> Path:
    parent_root = ensure_directory(outer.parent, "E_RUN_ROOT", "parent run root")
    output = output.absolute()
    if output != parent_root / "final" or output.exists() or output.is_symlink():
        reject("E_OUTPUT", "final output must be a new canonical run-root/final directory")
    value = build_final(
        contract, outer, database_root, external_root, addon_root, source_before, source_after
    )
    temporary = Path(tempfile.mkdtemp(prefix=".final.", dir=parent_root))
    try:
        shutil.copy2(outer, temporary / "outer-run-marker.json")
        value["created_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
        write_json(temporary / "report-manifest.json", value)
        os.replace(temporary, output)
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    return output / "report-manifest.json"


def verify_final(contract: dict[str, Any], outer: Path, manifest_path: Path) -> dict[str, Any]:
    observed = read_json(manifest_path, "E_FINAL")
    final_root = ensure_directory(manifest_path.parent, "E_FINAL", "final root")
    if manifest_path != final_root / "report-manifest.json":
        reject("E_FINAL", "final manifest path differs")
    created_at = observed.get("created_at")
    parse_timestamp(created_at, "E_FINAL")
    parent_root = outer.parent
    expected = build_final(
        contract,
        outer,
        child_root(contract, "database-matrix", observed.get("run_id", "")),
        child_root(contract, "external-matrix", observed.get("run_id", "")),
        child_root(contract, "addon-companion", observed.get("run_id", "")),
        parent_root / "source-before.tsv",
        parent_root / "source-after.tsv",
    )
    expected["created_at"] = created_at
    if observed != expected:
        reject("E_FINAL", "final manifest differs from live child evidence")
    marker_copy = ensure_regular(final_root / "outer-run-marker.json", "E_FINAL", "bundled outer marker")
    if marker_copy.read_bytes() != ensure_regular(outer, "E_FINAL", "outer marker").read_bytes():
        reject("E_FINAL", "bundled outer marker differs")
    actual = {
        path.relative_to(final_root).as_posix()
        for path in final_root.rglob("*")
        if path.is_file() and not path.is_symlink()
    }
    if actual != {"outer-run-marker.json", "report-manifest.json"}:
        reject("E_FINAL", f"final artifact set differs: {sorted(actual)}")
    return observed


def verify_parent_controls(
    contract: dict[str, Any], run_root: Path, final: dict[str, Any]
) -> None:
    status = read_env(run_root / "run-status.env")
    if set(status) != {
        "run_id", "runner", "git_head", "started_at", "finished_at", "last_phase",
        "exit_code", "source_before_sha256", "source_after_sha256",
        "outer_marker_sha256", "successor_manifest_sha256",
        "final_report_manifest_sha256", "status",
    }:
        reject("E_STATUS", "top-level run-status fields differ")
    parse_timestamp(status["started_at"], "E_STATUS")
    parse_timestamp(status["finished_at"], "E_STATUS")
    if status != {
        **status,
        "run_id": final["run_id"],
        "runner": "orchestrator",
        "git_head": final["git_head"],
        "last_phase": "completed",
        "exit_code": "0",
        "source_before_sha256": final["source_sha256"],
        "source_after_sha256": final["source_sha256"],
        "outer_marker_sha256": final["outer_marker_sha256"],
        "successor_manifest_sha256": contract["bindings"]["successor_manifest"]["sha256"],
        "final_report_manifest_sha256": sha256_file(run_root / "final/report-manifest.json"),
        "status": "passed",
    }:
        reject("E_STATUS", "top-level durable status differs")

    cleanup = read_env(
        run_root / "cleanup.env",
        {"container_residue", "volume_residue", "network_residue", "status"},
    )
    if cleanup != {
        "container_residue": "0", "volume_residue": "0",
        "network_residue": "0", "status": "passed",
    }:
        reject("E_RESOURCE_RESIDUE", "top-level cleanup/residue evidence differs")
    sensitive = read_env(run_root / "sensitive-scan.env", {"roots", "patterns", "status"})
    if sensitive != {"roots": "4", "patterns": "5", "status": "passed"}:
        reject("E_SENSITIVE", "top-level sensitive scan evidence differs")

    negative_rows = read_tsv(run_root / "negative/probes.tsv")
    expected_negative = [
        (row["probe"], row["expected_error"], row["expected_error"], "passed")
        for row in contract["negative_probes"]
    ]
    observed_negative = [
        (row.get("probe"), row.get("expected_error"), row.get("actual_error"), row.get("status"))
        for row in negative_rows
    ]
    if (
        (list(negative_rows[0]) if negative_rows else [])
        != ["probe", "expected_error", "actual_error", "status"]
        or observed_negative != expected_negative
    ):
        reject("E_NEGATIVE_PROBE", "top-level negative evidence differs")

    summary = read_env(run_root / "summary.env")
    expected_summary = {
        "run_id": final["run_id"], "runner": "orchestrator",
        "lane": "step3-required-matrix", "git_head": final["git_head"],
        "contract_sha256": contract["_sha256"],
        "source_before": final["source_sha256"], "source_after": final["source_sha256"],
        "outer_marker_sha256": final["outer_marker_sha256"],
        "final_report_manifest_sha256": sha256_file(run_root / "final/report-manifest.json"),
        "database_variants": "7", "external_variants": "7",
        "execution_keys": "45", "reports": "45", "testcase_nodes": "446",
        "failures": "0", "errors": "0", "skipped": "0",
        "database_keys": "29", "external_keys": "16", "overlap_keys": "0",
        "gap_keys": "0", "extra_keys": "0", "addon_variants": "2",
        "addon_reports": "2", "addon_testcase_nodes": "6",
        "optional_llm": "reviewed-optional-excluded", "negative_probes": "17/17",
        "resource_residue": "0/0/0", "status": "passed",
    }
    if summary != expected_summary:
        reject("E_STATUS", "top-level summary differs")


def create_candidate(contract: dict[str, Any], outer: Path, run_root: Path, output: Path) -> Path:
    run_root = ensure_directory(run_root, "E_CANDIDATE", "parent run root")
    if output != run_root / "candidate-manifest.json" or output.exists() or output.is_symlink():
        reject("E_OUTPUT", "candidate path is not a new canonical path")
    final = verify_final(contract, outer, run_root / "final/report-manifest.json")
    verify_parent_controls(contract, run_root, final)
    rows = artifact_rows(run_root, {"candidate-manifest.json"})
    required = {
        "run-context.json", "run-status.env", "summary.env", "source-before.tsv",
        "source-after.tsv", "negative/probes.tsv", "sensitive-scan.env",
        "final/outer-run-marker.json", "final/report-manifest.json",
    }
    if not required.issubset({row["path"] for row in rows}):
        reject("E_CANDIDATE", "candidate required artifact set is incomplete")
    candidate = {
        "schema_version": 1,
        "kind": "v934-step3-required-matrix-candidate",
        "run_id": final["run_id"],
        "runner": "orchestrator",
        "lane": "step3-required-matrix",
        "git_head": final["git_head"],
        "contract_sha256": contract["_sha256"],
        "outer_marker_sha256": final["outer_marker_sha256"],
        "report_manifest_sha256": sha256_file(run_root / "final/report-manifest.json"),
        "artifact_tree_sha256": tree_digest(rows),
        "artifacts": rows,
        "totals": final["totals"],
        "addon_companion": final["addon_companion"],
    }
    write_json(output, candidate)
    return output


def verify_candidate(contract: dict[str, Any], candidate_path: Path) -> dict[str, Any]:
    candidate = read_json(candidate_path, "E_CANDIDATE")
    run_root = candidate_path.parent.resolve()
    expected_fields = {
        "schema_version", "kind", "run_id", "runner", "lane", "git_head",
        "contract_sha256", "outer_marker_sha256", "report_manifest_sha256",
        "artifact_tree_sha256", "artifacts", "totals", "addon_companion",
    }
    if set(candidate) != expected_fields:
        reject("E_CANDIDATE", "candidate fields differ")
    final = verify_final(contract, run_root / "run-context.json", run_root / "final/report-manifest.json")
    verify_parent_controls(contract, run_root, final)
    rows = artifact_rows(run_root, {"candidate-manifest.json"})
    if (
        candidate["schema_version"] != 1
        or candidate["kind"] != "v934-step3-required-matrix-candidate"
        or candidate["run_id"] != final["run_id"]
        or candidate["git_head"] != final["git_head"]
        or candidate["contract_sha256"] != contract["_sha256"]
        or candidate["outer_marker_sha256"] != final["outer_marker_sha256"]
        or candidate["report_manifest_sha256"] != sha256_file(run_root / "final/report-manifest.json")
        or candidate["artifact_tree_sha256"] != tree_digest(rows)
        or candidate["artifacts"] != rows
        or candidate["totals"] != REQUIRED_TOTALS
        or candidate["addon_companion"] != final["addon_companion"]
    ):
        reject("E_CANDIDATE", "candidate identity/artifact binding differs")
    return candidate


def synthetic_records(contract: dict[str, Any]) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    required = contract.get("_required_rows")
    if required is None:
        deferred = read_tsv(contract["_bindings"]["deferred_inventory"])
        required = [row for row in deferred if row["required"] == "true"]
    database_rows = [row for row in required if row["lane"] == "database-contract-matrix"]
    external_rows = [row for row in required if row["lane"].startswith("external-")]
    database_nodes = [12] * 28 + [34]
    external_nodes = [4] * 15 + [16]
    database = [
        {"execution_key": row["execution_key"], "testcase_nodes": nodes,
         "failures": 0, "errors": 0, "skipped": 0, "lane": row["lane"]}
        for row, nodes in zip(database_rows, database_nodes, strict=True)
    ]
    external = [
        {"execution_key": row["execution_key"], "testcase_nodes": nodes,
         "failures": 0, "errors": 0, "skipped": 0, "lane": row["lane"]}
        for row, nodes in zip(external_rows, external_nodes, strict=True)
    ]
    if sum(row["testcase_nodes"] for row in database) != 370 or sum(row["testcase_nodes"] for row in external) != 76:
        reject("E_NEGATIVE_PROBE", "synthetic fixture cardinality differs")
    return database, external


def run_negative_probes(contract: dict[str, Any]) -> list[dict[str, str]]:
    database, external = synthetic_records(contract)
    expected = {row["execution_key"] for row in contract["_required_rows"]}
    optional = contract["optional_execution"]["execution_key"]

    def clone(rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return [dict(row) for row in rows]

    probes: list[tuple[str, str, Any]] = []
    probes.append(("missing-database-key", "E_REQUIRED_GAP", lambda: validate_union_ledger(database[:-1], external, expected, optional)))
    probes.append(("missing-external-key", "E_REQUIRED_GAP", lambda: validate_union_ledger(database, external[:-1], expected, optional)))
    probes.append(("duplicate-database-key", "E_DUPLICATE_KEY", lambda: validate_union_ledger(database + [dict(database[0])], external, expected, optional)))
    overlap = clone(external); overlap[0]["execution_key"] = database[0]["execution_key"]
    probes.append(("database-external-overlap", "E_KEY_OVERLAP", lambda: validate_union_ledger(database, overlap, expected, optional)))
    extra = clone(external); extra[0]["execution_key"] = "v934|extra"
    probes.append(("extra-required-key", "E_REQUIRED_GAP", lambda: validate_union_ledger(database, extra, expected, optional)))
    leaked = clone(external); leaked[0]["execution_key"] = optional
    probes.append(("optional-llm-leak", "E_OPTIONAL_EXECUTION", lambda: validate_union_ledger(database, leaked, expected, optional)))
    wrong_count = clone(database); wrong_count[0]["testcase_nodes"] += 1
    probes.append(("wrong-testcase-total", "E_MATRIX_TOTAL", lambda: validate_union_ledger(wrong_count, external, expected, optional)))
    for name in ("failures", "errors", "skipped"):
        outcome = clone(database); outcome[0][name] = 1
        probes.append((f"nonzero-{name}", "E_REPORT_OUTCOME", lambda outcome=outcome: validate_union_ledger(outcome, external, expected, optional)))
    probes.extend([
        ("missing-addon-companion", "E_ADDON_COMPANION", lambda: validate_union_ledger(database, external, expected, optional, addon_ok=False)),
        ("wrong-parent-context", "E_PARENT_CONTEXT", lambda: validate_union_ledger(database, external, expected, optional, parent_ok=False)),
        ("failed-child-status", "E_CHILD_STATUS", lambda: validate_union_ledger(database, external, expected, optional, child_status_ok=False)),
        ("source-drift", "E_SOURCE_DRIFT", lambda: validate_union_ledger(database, external, expected, optional, source_ok=False)),
        ("child-artifact-tamper", "E_CHILD_EVIDENCE", lambda: validate_union_ledger(database, external, expected, optional, evidence_ok=False)),
        ("sensitive-scan-hit", "E_SENSITIVE", lambda: validate_union_ledger(database, external, expected, optional, sensitive_ok=False)),
        ("resource-residue", "E_RESOURCE_RESIDUE", lambda: validate_union_ledger(database, external, expected, optional, residue_ok=False)),
    ])
    expected_probes = [(row["probe"], row["expected_error"]) for row in contract["negative_probes"]]
    if [(name, code) for name, code, _ in probes] != expected_probes:
        reject("E_NEGATIVE_PROBE", "implemented negative probe set differs from contract")
    results: list[dict[str, str]] = []
    for name, code, callback in probes:
        actual = "none"
        try:
            callback()
        except RequiredMatrixError as error:
            actual = error.code
        if actual != code:
            reject("E_NEGATIVE_PROBE", f"{name} returned {actual}, expected {code}")
        results.append({"probe": name, "expected_error": code, "actual_error": actual, "status": "passed"})
    return results


def launch_child(contract: dict[str, Any], child: str, run_id: str, lock_fd: int) -> None:
    if child not in CHILD_ORDER:
        reject("E_CHILD", f"unsupported child: {child}")
    if RUN_ID_PATTERN.fullmatch(run_id) is None or run_id in {".", ".."}:
        reject("E_CHILD", f"unsafe run id: {run_id}")
    definition = contract["children"][child]
    runner = ensure_regular(contract["_bindings"][definition["runner_binding"]], "E_CHILD", "child runner")
    if lock_fd <= 2 or os.environ.get("V934_AUTHORITY_LOCK_FD") != str(lock_fd):
        reject("E_AUTHORITY_LOCK", "child lock descriptor differs")
    expected_lock = Path(
        subprocess.run(
            ["git", "-C", str(contract["_repo"]), "rev-parse", "--absolute-git-dir"],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
        ).stdout.strip()
    ) / "v934-step2-authority.lock"
    try:
        fd_stat = os.fstat(lock_fd)
        lock_stat = expected_lock.stat()
        if (
            not stat.S_ISREG(fd_stat.st_mode)
            or not stat.S_ISREG(lock_stat.st_mode)
            or (fd_stat.st_dev, fd_stat.st_ino) != (lock_stat.st_dev, lock_stat.st_ino)
        ):
            reject("E_AUTHORITY_LOCK", "child descriptor does not reference the expected lock")
        os.set_inheritable(lock_fd, True)
        fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except (BlockingIOError, OSError) as error:
        raise RequiredMatrixError("E_AUTHORITY_LOCK", "child lock descriptor is not inherited") from error
    for name in ("SIGINT", "SIGTERM", "SIGHUP", "SIGPIPE", "SIGXFZ", "SIGXFSZ"):
        value = getattr(signal, name, None)
        if value is not None:
            signal.signal(value, signal.SIG_DFL)
    os.setsid()
    if os.getsid(0) != os.getpid() or os.getpgrp() != os.getpid():
        reject("E_CHILD", "child launcher did not establish a new session")
    environment = os.environ.copy()
    environment["V934_AUTHORITY_LOCK_MODE"] = "inherited"
    os.execve(str(runner), [str(runner), run_id], environment)


def command_validate_contract(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=False)
    print(json.dumps({
        "status": "passed",
        "integration_status": contract["integration_status"],
        "contract_sha256": contract["_sha256"],
        **REQUIRED_TOTALS,
        "addon_reports": ADDON_TOTALS["reports"],
        "addon_testcase_nodes": ADDON_TOTALS["testcase_nodes"],
    }, sort_keys=True))


def command_validate(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=True)
    print(json.dumps({"status": "passed", "contract_sha256": contract["_sha256"], **REQUIRED_TOTALS}, sort_keys=True))


def command_seal_source(args: argparse.Namespace) -> None:
    digest = write_source_seal(args.repo_root, args.output)
    print(f"{PREFIX} source files sealed sha256={digest}")


def command_create_outer(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=True)
    if RUN_ID_PATTERN.fullmatch(args.run_id) is None or args.run_id in {".", ".."}:
        reject("E_MARKER", "unsafe run id")
    if args.git_head != current_head(args.repo_root) or SHA_PATTERN.fullmatch(args.source_sha256) is None:
        reject("E_MARKER", "outer Git/source identity differs")
    output = args.output.absolute()
    if output.exists() or output.is_symlink():
        reject("E_OUTPUT", "outer marker already exists")
    output.parent.mkdir(parents=True, exist_ok=True)
    marker = {
        "schema_version": 1,
        "kind": "v934-step3-required-matrix-run",
        "run_id": args.run_id,
        "lane": "step3-required-matrix",
        "runner": "orchestrator",
        "git_head": args.git_head,
        "contract_sha256": contract["_sha256"],
        "source_sha256": args.source_sha256,
        "started_at": args.started_at,
        "status": "started",
        "child_order": list(CHILD_ORDER),
        "child_contracts": {
            "addon-companion": contract["bindings"]["addon_contract"]["sha256"],
            "database-matrix": contract["bindings"]["database_contract"]["sha256"],
            "external-matrix": contract["bindings"]["external_contract"]["sha256"],
        },
    }
    parse_timestamp(args.started_at)
    write_json(output, marker)
    load_outer(output, contract)


def command_finalize(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=True)
    manifest = finalize(
        contract, args.outer_marker, args.database_root, args.external_root,
        args.addon_root, args.source_before, args.source_after, args.output,
    )
    print(f"{PREFIX} finalized required=45/446 F0/E0/S0 addon=2/6: {manifest}")


def command_verify_final(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=True)
    verify_final(contract, args.outer_marker, args.manifest)
    print(f"{PREFIX} verified final required=45/446 F0/E0/S0: {args.manifest}")


def command_create_candidate(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=True)
    output = create_candidate(contract, args.outer_marker, args.run_root, args.output)
    print(f"{PREFIX} created candidate: {output}")


def command_verify_candidate(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=True)
    verify_candidate(contract, args.candidate)
    print(f"{PREFIX} verified candidate: {args.candidate}")


def command_negative(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=False)
    results = run_negative_probes(contract)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.output.with_name(f".{args.output.name}.{os.getpid()}.tmp")
        with temporary.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(
                stream,
                fieldnames=["probe", "expected_error", "actual_error", "status"],
                delimiter="\t",
                lineterminator="\n",
            )
            writer.writeheader()
            writer.writerows(results)
        os.replace(temporary, args.output)
    print(f"{PREFIX} synthetic negatives passed: {len(results)}/{len(results)}")


def command_launch_child(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract, require_ready=True)
    launch_child(contract, args.child, args.run_id, args.lock_fd)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=ROOT)
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("validate-contract").set_defaults(func=command_validate_contract)
    sub.add_parser("validate").set_defaults(func=command_validate)

    source = sub.add_parser("seal-source")
    source.add_argument("--output", type=Path, required=True)
    source.set_defaults(func=command_seal_source)

    marker = sub.add_parser("create-outer")
    marker.add_argument("--run-id", required=True)
    marker.add_argument("--git-head", required=True)
    marker.add_argument("--source-sha256", required=True)
    marker.add_argument("--started-at", required=True)
    marker.add_argument("--output", type=Path, required=True)
    marker.set_defaults(func=command_create_outer)

    final = sub.add_parser("finalize")
    final.add_argument("--outer-marker", type=Path, required=True)
    final.add_argument("--database-root", type=Path, required=True)
    final.add_argument("--external-root", type=Path, required=True)
    final.add_argument("--addon-root", type=Path, required=True)
    final.add_argument("--source-before", type=Path, required=True)
    final.add_argument("--source-after", type=Path, required=True)
    final.add_argument("--output", type=Path, required=True)
    final.set_defaults(func=command_finalize)

    verify_final_parser = sub.add_parser("verify-final")
    verify_final_parser.add_argument("--outer-marker", type=Path, required=True)
    verify_final_parser.add_argument("--manifest", type=Path, required=True)
    verify_final_parser.set_defaults(func=command_verify_final)

    candidate = sub.add_parser("create-candidate")
    candidate.add_argument("--outer-marker", type=Path, required=True)
    candidate.add_argument("--run-root", type=Path, required=True)
    candidate.add_argument("--output", type=Path, required=True)
    candidate.set_defaults(func=command_create_candidate)

    verify_candidate_parser = sub.add_parser("verify-candidate")
    verify_candidate_parser.add_argument("--candidate", type=Path, required=True)
    verify_candidate_parser.set_defaults(func=command_verify_candidate)

    negative = sub.add_parser("negative")
    negative.add_argument("--output", type=Path)
    negative.set_defaults(func=command_negative)

    launch = sub.add_parser("launch-child")
    launch.add_argument("--child", choices=CHILD_ORDER, required=True)
    launch.add_argument("--run-id", required=True)
    launch.add_argument("--lock-fd", type=int, required=True)
    launch.set_defaults(func=command_launch_child)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        args.repo_root = args.repo_root.resolve()
        args.contract = args.contract.absolute()
        args.func(args)
        return 0
    except RequiredMatrixError as error:
        print(f"{PREFIX} {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
