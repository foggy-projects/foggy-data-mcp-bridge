#!/usr/bin/env python3
"""Materialize a run-owned Step 4 view of the immutable Step 2 report contract.

The generated directory implements the ``--successor-dir`` interface consumed
by ``scripts/v934/step2_report_tool.py``.  It copies the confirmed Step 2
contract, applies only the reviewed Step 4 report amendment, and keeps
the parent structural inventory byte-for-byte unchanged.
"""

from __future__ import annotations

import argparse
import copy
import csv
import ctypes
import hashlib
import importlib.util
import io
import json
import os
import random
import re
import shutil
import sys
import tempfile
from pathlib import Path
from typing import Any, Iterable, Sequence


PREFIX = "[v934-step4-step2-report-view]"
CONTRACT_RELATIVE = Path("scripts/v934/step4/step2-report-view-contract.json")
HASH_FILE = "SHA256SUMS"
AMENDMENT_FILE = "coverage-report-amendment.tsv"
EXECUTION_FILE = "step2-required-execution.tsv"
STRUCTURAL_FILE = "structural-report-inventory.tsv"
DISCOVERY_FILE = "discovery-inventory.tsv"
SOURCE_FILE = "source-inventory.tsv"
RUNNER_FILE = "runner-contract.json"
FREEZE_FILE = "contract-freeze.json"
PARENT_LINK_FILE = "parent-link.json"
EXPECTED_PARENT_MANIFEST_SHA256 = "4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919"
EXPECTED_REPORT_TOOL_SHA256 = "ec478fac5eab355e1aed99c2b7a8934f0d5103425abfdd3f15f6f0733977c1ea"

EXECUTION_HEADER = [
    "execution_key", "source_id", "report_fqcn", "runner", "lane",
    "variant_key", "db_kind", "infra_kind", "execution_step", "required",
    "owner", "optional_reason", "review_at",
]
STRUCTURAL_HEADER = [
    "module", "source_id", "source_fqcn", "report_fqcn", "runner", "lane",
    "variant_key", "owner", "discovered_test_nodes",
    "runtime_deferred_containers", "positive_sibling_execution_keys",
    "disposition", "rationale",
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
AMENDMENT_HEADER = [
    "source_path", "module", "report_fqcn", "runner", "variant_key",
    "step2_source_sha256", "step4_source_sha256", "step2_testcase_nodes",
    "step4_expected_testcase_nodes", "change_kind", "disposition", "workitem",
]

EXPECTED_COUNTS = {
    "parent": {
        "positive_reports": 724,
        "structural_reports": 59,
        "testcases": 5205,
        "surefire_positive_reports": 677,
        "surefire_structural_reports": 55,
        "surefire_testcases": 4890,
        "failsafe_positive_reports": 47,
        "failsafe_structural_reports": 4,
        "failsafe_testcases": 315,
    },
    "derived": {
        "positive_reports": 729,
        "structural_reports": 59,
        "testcases": 5263,
        "surefire_positive_reports": 682,
        "surefire_structural_reports": 55,
        "surefire_testcases": 4943,
        "failsafe_positive_reports": 47,
        "failsafe_structural_reports": 4,
        "failsafe_testcases": 320,
    },
}


class ViewError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def fail(code: str, message: str) -> None:
    raise ViewError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        fail(code, message)


def exact_keys(value: Any, keys: Sequence[str], label: str) -> dict[str, Any]:
    require(isinstance(value, dict), "E_CONTRACT", f"{label} must be an object")
    expected = set(keys)
    actual = set(value)
    require(
        actual == expected,
        "E_CONTRACT",
        f"{label} keys differ missing={sorted(expected - actual)} extra={sorted(actual - expected)}",
    )
    return value


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    try:
        digest = hashlib.sha256()
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()
    except OSError as exc:
        fail("E_IO", f"cannot hash {path}: {exc}")


def read_json(path: Path, code: str = "E_JSON") -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(code, f"cannot read JSON {path}: {exc}")
    require(isinstance(value, dict), code, f"JSON root must be an object: {path}")
    return value


def json_bytes(value: dict[str, Any]) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def read_tsv(path: Path, header: Sequence[str], code: str) -> list[dict[str, str]]:
    try:
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            require(reader.fieldnames == list(header), code, f"unexpected TSV header: {path}")
            rows = [dict(row) for row in reader]
    except OSError as exc:
        fail(code, f"cannot read TSV {path}: {exc}")
    require(
        all(all(value is not None for value in row.values()) for row in rows),
        code,
        f"malformed TSV row: {path}",
    )
    return rows


def tsv_bytes(header: Sequence[str], rows: Iterable[dict[str, Any]]) -> bytes:
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(
        stream, fieldnames=list(header), delimiter="\t", lineterminator="\n"
    )
    writer.writeheader()
    for row in rows:
        writer.writerow({name: row[name] for name in header})
    return stream.getvalue().encode("utf-8")


def safe_run_id(value: str) -> str:
    require(
        bool(re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", value)),
        "E_PATH",
        f"unsafe run id: {value!r}",
    )
    return value


def safe_relative(value: str, label: str) -> Path:
    path = Path(value)
    require(
        bool(value) and not path.is_absolute() and ".." not in path.parts,
        "E_PATH",
        f"unsafe {label}: {value!r}",
    )
    return path


def framed(values: Sequence[str], prefix: str) -> str:
    return prefix + "|" + "|".join(
        f"{len(value.encode('utf-8'))}:{value}" for value in values
    )


def source_id(path: str) -> str:
    return framed([path], "v934-src")


def execution_key(runner: str, lane: str, variant: str, fqcn: str) -> str:
    return framed([runner, lane, variant, fqcn], "v934")


def load_contract(root: Path) -> tuple[dict[str, Any], Path]:
    path = root / CONTRACT_RELATIVE
    require(path.is_file() and not path.is_symlink(), "E_CONTRACT", f"missing contract: {path}")
    contract = exact_keys(
        read_json(path, "E_CONTRACT"),
        (
            "schema_version", "kind", "status", "parent", "report_tool",
            "amendment", "counts", "output",
        ),
        "contract",
    )
    require(contract["schema_version"] == 1, "E_CONTRACT", "schema_version must be 1")
    require(
        contract["kind"] == "v934-step4-step2-report-view-contract",
        "E_CONTRACT",
        "unexpected contract kind",
    )
    require(
        contract["status"] == "bootstrap-reviewed-amendment",
        "E_CONTRACT",
        "unexpected contract status",
    )
    require(contract["counts"] == EXPECTED_COUNTS, "E_CONTRACT", "frozen counts differ")
    parent = exact_keys(
        contract["parent"],
        ("directory", "hash_manifest_sha256", "artifacts"),
        "contract.parent",
    )
    require(
        parent["directory"] == "scripts/v934/successor/step2",
        "E_CONTRACT",
        "unexpected Step 2 parent directory",
    )
    require(
        parent["hash_manifest_sha256"] == EXPECTED_PARENT_MANIFEST_SHA256,
        "E_CONTRACT",
        "immutable Step 2 parent manifest identity differs",
    )
    require(
        isinstance(parent["artifacts"], dict) and len(parent["artifacts"]) == 13,
        "E_CONTRACT",
        "parent artifact set must contain exactly 13 files",
    )
    require(
        all(
            re.fullmatch(r"[0-9a-f]{64}", digest or "")
            for digest in parent["artifacts"].values()
        ),
        "E_CONTRACT",
        "invalid parent artifact hash",
    )
    tool = exact_keys(
        contract["report_tool"],
        ("path", "sha256", "successor_argument", "compatible_commands"),
        "contract.report_tool",
    )
    require(
        tool["path"] == "scripts/v934/step2_report_tool.py"
        and tool["sha256"] == EXPECTED_REPORT_TOOL_SHA256
        and tool["successor_argument"] == "--successor-dir"
        and tool["compatible_commands"] == ["verify", "collect", "finalize", "merge", "negative"],
        "E_CONTRACT",
        "Step 2 report tool interface differs",
    )
    amendment = exact_keys(
        contract["amendment"],
        (
            "path", "sha256", "rows", "new_positive_reports",
            "changed_positive_reports", "testcase_delta",
        ),
        "contract.amendment",
    )
    require(
        amendment == {
            "path": "scripts/v934/step4/coverage-report-amendment.tsv",
            "sha256": "c1aa8b86e7280a4014fb1d6131e3f6ca0d42f7b024648b4cec22242357f52cd6",
            "rows": 13,
            "new_positive_reports": 5,
            "changed_positive_reports": 8,
            "testcase_delta": 58,
        },
        "E_CONTRACT",
        "reviewed amendment identity/counts differ",
    )
    output = exact_keys(
        contract["output"],
        ("root_pattern", "directory", "manifest_entries"),
        "contract.output",
    )
    expected_entries = sorted(
        [
            FREEZE_FILE, AMENDMENT_FILE, DISCOVERY_FILE, PARENT_LINK_FILE,
            RUNNER_FILE, SOURCE_FILE, EXECUTION_FILE, STRUCTURAL_FILE,
        ]
    )
    require(
        output["root_pattern"] == "target/v934-step4-coverage/runs/<run-id>/step2-report-view"
        and output["directory"] == "step2-report-view"
        and output["manifest_entries"] == expected_entries,
        "E_CONTRACT",
        "output contract differs",
    )
    return contract, path


def parse_hash_manifest(path: Path, code: str) -> dict[str, str]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        fail(code, f"cannot read hash manifest {path}: {exc}")
    result: dict[str, str] = {}
    for line in lines:
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+)", line)
        require(match is not None, code, f"invalid hash-manifest line: {line!r}")
        digest, name = match.groups()
        require(name not in result, code, f"duplicate hash-manifest entry: {name}")
        result[name] = digest
    return result


def import_report_tool(root: Path, contract: dict[str, Any]):
    tool_path = root / contract["report_tool"]["path"]
    require(
        tool_path.is_file() and not tool_path.is_symlink(),
        "E_REPORT_TOOL",
        f"missing real Step 2 report tool: {tool_path}",
    )
    require(
        sha256_file(tool_path) == contract["report_tool"]["sha256"],
        "E_REPORT_TOOL",
        "Step 2 report tool hash differs",
    )
    spec = importlib.util.spec_from_file_location("v934_step2_report_view_parent_tool", tool_path)
    require(spec is not None and spec.loader is not None, "E_REPORT_TOOL", "cannot load report tool")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
    except Exception as exc:
        fail("E_REPORT_TOOL", f"cannot import Step 2 report tool: {exc}")
    return module


def validate_parent_directory(
    root: Path,
    directory: Path,
    contract: dict[str, Any],
    *,
    enforce_location: bool,
):
    expected_parent = (root / contract["parent"]["directory"]).absolute()
    if enforce_location:
        require(directory.absolute() == expected_parent, "E_PARENT_PATH", "parent path differs")
    require(directory.is_dir() and not directory.is_symlink(), "E_PARENT_PATH", "parent is not a real directory")
    if enforce_location:
        require(directory.resolve() == expected_parent, "E_PARENT_PATH", "parent path contains a symlink")
    expected_names = set(contract["parent"]["artifacts"]) | {HASH_FILE}
    actual_names = {entry.name for entry in directory.iterdir()}
    require(actual_names == expected_names, "E_PARENT_SET", "immutable parent file set differs")
    for entry in directory.iterdir():
        require(entry.is_file() and not entry.is_symlink(), "E_PARENT_PATH", f"non-file parent entry: {entry}")
    manifest = directory / HASH_FILE
    require(
        sha256_file(manifest) == contract["parent"]["hash_manifest_sha256"],
        "E_PARENT_MANIFEST",
        "immutable Step 2 hash-manifest hash differs",
    )
    entries = parse_hash_manifest(manifest, "E_PARENT_MANIFEST")
    require(entries == contract["parent"]["artifacts"], "E_PARENT_MANIFEST", "parent manifest entries differ")
    for name, digest in entries.items():
        require(sha256_file(directory / name) == digest, "E_PARENT_HASH", f"parent artifact differs: {name}")
    if not enforce_location:
        return None
    report_tool = import_report_tool(root, contract)
    try:
        parent = report_tool.load_successor(directory)
    except Exception as exc:
        fail("E_PARENT_SEMANTICS", f"Step 2 report tool rejected immutable parent: {exc}")
    require(len(parent.rows) == 724, "E_PARENT_COUNTS", "parent positive report count differs")
    require(len(parent.structural_rows) == 59, "E_PARENT_COUNTS", "parent structural count differs")
    return parent, report_tool


def fqcn_from_source(path: str) -> str:
    marker = "/src/test/java/"
    require(marker in path and path.endswith(".java"), "E_AMENDMENT", f"not a Java test source: {path}")
    return path.split(marker, 1)[1][:-5].replace("/", ".")


def validate_workspace_sources(root: Path, rows: Sequence[dict[str, str]]) -> None:
    for row in rows:
        relative = safe_relative(row["source_path"], "amended source path")
        path = root / relative
        require(path.is_file() and not path.is_symlink(), "E_SOURCE_HASH", f"missing real source: {relative}")
        require(path.resolve().is_relative_to(root.resolve()), "E_SOURCE_HASH", f"source escapes workspace: {relative}")
        require(
            sha256_file(path) == row["step4_source_sha256"],
            "E_SOURCE_HASH",
            f"current source hash differs: {relative}",
        )


def load_amendment(
    root: Path,
    path: Path,
    contract: dict[str, Any],
    *,
    validate_sources: bool = True,
) -> list[dict[str, str]]:
    require(path.is_file() and not path.is_symlink(), "E_AMENDMENT_PATH", f"missing amendment: {path}")
    require(
        sha256_file(path) == contract["amendment"]["sha256"],
        "E_AMENDMENT_HASH",
        "reviewed amendment hash differs",
    )
    rows = read_tsv(path, AMENDMENT_HEADER, "E_AMENDMENT")
    require(len(rows) == 13, "E_AMENDMENT", "amendment must contain exactly thirteen rows")
    require(
        len({row["source_path"] for row in rows}) == 13
        and len({row["report_fqcn"] for row in rows}) == 13,
        "E_AMENDMENT",
        "amendment paths/report identities must be unique",
    )
    kinds = {kind: sum(row["change_kind"] == kind for row in rows) for kind in {
        "new-positive-report", "changed-positive-report"
    }}
    require(
        kinds == {"new-positive-report": 5, "changed-positive-report": 8}
        and all(row["change_kind"] in kinds for row in rows),
        "E_AMENDMENT",
        "amendment change kinds differ",
    )
    delta = 0
    for row in rows:
        require(all(row.values()), "E_AMENDMENT", "amendment contains an empty field")
        require(row["report_fqcn"] == fqcn_from_source(row["source_path"]), "E_AMENDMENT", "source/FQCN differs")
        require(
            row["source_path"].startswith(row["module"] + "/src/test/java/"),
            "E_AMENDMENT",
            f"source/module differs: {row['source_path']}",
        )
        require(row["runner"] in {"surefire", "failsafe"}, "E_AMENDMENT", "unknown runner")
        require(re.fullmatch(r"[A-Za-z0-9._-]+", row["variant_key"]) is not None, "E_AMENDMENT", "unsafe variant")
        require(re.fullmatch(r"[0-9a-f]{64}", row["step4_source_sha256"]) is not None, "E_AMENDMENT", "invalid current hash")
        old = int(row["step2_testcase_nodes"])
        new = int(row["step4_expected_testcase_nodes"])
        require(new > 0, "E_AMENDMENT", "amended testcase count must be positive")
        if row["change_kind"] == "new-positive-report":
            require(
                row["step2_source_sha256"] == "absent"
                and old == 0
                and row["disposition"] == "step4-required-unit-amendment"
                and row["runner"] == "surefire"
                and row["variant_key"] == "unit",
                "E_AMENDMENT",
                "new-report amendment semantics differ",
            )
        else:
            require(
                re.fullmatch(r"[0-9a-f]{64}", row["step2_source_sha256"]) is not None
                and row["step2_source_sha256"] != row["step4_source_sha256"],
                "E_AMENDMENT",
                "changed-report amendment semantics differ",
            )
            if row["disposition"] == "step4-cardinality-amendment":
                require(new > old, "E_AMENDMENT", "cardinality amendment must increase testcase count")
            elif row["disposition"] == "step4-source-amendment":
                require(new == old, "E_AMENDMENT", "source amendment must preserve testcase count")
            else:
                fail("E_AMENDMENT", "changed-report disposition differs")
        workitem = root / safe_relative(row["workitem"], "amendment workitem")
        require(workitem.is_file() and not workitem.is_symlink(), "E_AMENDMENT", f"missing workitem: {workitem}")
        delta += new - old
    require(delta == 58, "E_AMENDMENT", f"amendment testcase delta is {delta}, expected 58")
    if validate_sources:
        validate_workspace_sources(root, rows)
    return rows


def module_class_hashes(discovery: Sequence[dict[str, str]], module: str) -> tuple[str, str]:
    pairs = {
        (row["test_classes_sha256"], row["main_classes_sha256"])
        for row in discovery
        if row["module"] == module
    }
    require(len(pairs) == 1, "E_PARENT_SEMANTICS", f"module class hashes are not unique: {module}")
    return next(iter(pairs))


def directory_tree_hash(directory: Path, label: str) -> str:
    require(directory.is_dir() and not directory.is_symlink(), "E_CLASS_HASH", f"missing real class tree: {label}")
    files: list[Path] = []
    try:
        for path in directory.rglob("*"):
            if path.is_symlink():
                fail("E_CLASS_HASH", f"class tree contains a symlink: {path}")
            if path.is_file():
                files.append(path)
    except OSError as exc:
        fail("E_CLASS_HASH", f"cannot walk class tree {label}: {exc}")
    require(files, "E_CLASS_HASH", f"class tree is empty: {label}")
    digest = hashlib.sha256()
    for path in sorted(set(files), key=lambda item: item.relative_to(directory).as_posix()):
        relative = path.relative_to(directory).as_posix()
        content_hash = sha256_file(path)
        for value in (relative, content_hash):
            encoded = value.encode("utf-8")
            digest.update(str(len(encoded)).encode("ascii"))
            digest.update(b":")
            digest.update(encoded)
            digest.update(b"\0")
    return digest.hexdigest()


def bind_current_workspace(
    root: Path,
    discoveries: Sequence[dict[str, str]],
    sources: Sequence[dict[str, str]],
) -> None:
    source_by_id = {row["source_id"]: row for row in sources}
    require(len(source_by_id) == len(sources), "E_SOURCE_HASH", "derived source identities are not unique")
    module_hashes: dict[str, tuple[str, str]] = {}
    for row in discoveries:
        source = source_by_id.get(row["source_id"])
        require(source is not None, "E_SOURCE_HASH", f"discovery source is absent: {row['source_id']}")
        require(
            source["module"] == row["module"],
            "E_SOURCE_HASH",
            f"discovery/source module differs: {row['source_id']}",
        )
        relative = safe_relative(source["source_path"], "derived source path")
        source_path = root / relative
        require(
            source_path.is_file()
            and not source_path.is_symlink()
            and source_path.resolve().is_relative_to(root.resolve()),
            "E_SOURCE_HASH",
            f"missing or unsafe current source: {relative}",
        )
        if row["module"] not in module_hashes:
            module_root = root / safe_relative(row["module"], "derived module")
            module_hashes[row["module"]] = (
                directory_tree_hash(module_root / "target/test-classes", f"{row['module']}/test-classes"),
                directory_tree_hash(module_root / "target/classes", f"{row['module']}/classes"),
            )
        row["source_sha256"] = sha256_file(source_path)
        row["test_classes_sha256"], row["main_classes_sha256"] = module_hashes[row["module"]]


def validate_amendment_against_parent(
    amendment: Sequence[dict[str, str]],
    executions: Sequence[dict[str, str]],
    discoveries: Sequence[dict[str, str]],
    sources: Sequence[dict[str, str]],
) -> None:
    execution_by_fqcn = {row["report_fqcn"]: row for row in executions}
    source_by_path = {row["source_path"]: row for row in sources}
    discovery_by_identity = {
        (row["source_id"], row["report_fqcn"]): row for row in discoveries
    }
    all_discovery_fqcns = {row["report_fqcn"] for row in discoveries}
    for row in amendment:
        existing = execution_by_fqcn.get(row["report_fqcn"])
        if row["change_kind"] == "new-positive-report":
            require(existing is None, "E_UNDECLARED_DELTA", f"new report already exists: {row['report_fqcn']}")
            require(row["source_path"] not in source_by_path, "E_UNDECLARED_DELTA", "new source already exists")
            require(row["report_fqcn"] not in all_discovery_fqcns, "E_UNDECLARED_DELTA", "new discovery already exists")
            continue
        require(existing is not None, "E_UNDECLARED_DELTA", f"changed report is absent: {row['report_fqcn']}")
        require(
            existing["runner"] == row["runner"]
            and existing["variant_key"] == row["variant_key"]
            and existing["owner"] == row["module"],
            "E_UNDECLARED_DELTA",
            f"changed report ownership differs: {row['report_fqcn']}",
        )
        parent_source = source_by_path.get(row["source_path"])
        require(
            parent_source is not None and parent_source["source_id"] == existing["source_id"],
            "E_UNDECLARED_DELTA",
            f"changed source binding differs: {row['report_fqcn']}",
        )
        discovery = discovery_by_identity.get((existing["source_id"], existing["report_fqcn"]))
        require(
            discovery is not None
            and discovery["module"] == row["module"]
            and discovery["discovered_test_nodes"] == row["step2_testcase_nodes"]
            and discovery["runtime_deferred_containers"] == "0",
            "E_UNDECLARED_DELTA",
            f"changed parent cardinality differs: {row['report_fqcn']}",
        )


def apply_amendment(
    root: Path,
    parent_dir: Path,
    amendment: Sequence[dict[str, str]],
) -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    executions = read_tsv(parent_dir / EXECUTION_FILE, EXECUTION_HEADER, "E_PARENT_SEMANTICS")
    structural = read_tsv(parent_dir / STRUCTURAL_FILE, STRUCTURAL_HEADER, "E_PARENT_SEMANTICS")
    discoveries = read_tsv(parent_dir / DISCOVERY_FILE, DISCOVERY_HEADER, "E_PARENT_SEMANTICS")
    sources = read_tsv(parent_dir / SOURCE_FILE, SOURCE_HEADER, "E_PARENT_SEMANTICS")
    validate_amendment_against_parent(amendment, executions, discoveries, sources)
    discovery_by_identity = {
        (row["source_id"], row["report_fqcn"]): row for row in discoveries
    }
    execution_by_fqcn = {row["report_fqcn"]: row for row in executions}
    source_ids = {row["source_id"] for row in sources}
    execution_keys = {row["execution_key"] for row in executions}

    for change in amendment:
        if change["change_kind"] == "changed-positive-report":
            execution = execution_by_fqcn[change["report_fqcn"]]
            discovery = discovery_by_identity[(execution["source_id"], execution["report_fqcn"])]
            discovery["discovered_test_nodes"] = change["step4_expected_testcase_nodes"]
            discovery["source_sha256"] = change["step4_source_sha256"]
            continue

        sid = source_id(change["source_path"])
        key = execution_key("surefire", "unit", "unit", change["report_fqcn"])
        require(sid not in source_ids and key not in execution_keys, "E_UNDECLARED_DELTA", "new identity collides")
        source_root = change["module"] + "/src/test/java"
        executions.append({
            "execution_key": key,
            "source_id": sid,
            "report_fqcn": change["report_fqcn"],
            "runner": "surefire",
            "lane": "unit",
            "variant_key": "unit",
            "db_kind": "none",
            "infra_kind": "hermetic",
            "execution_step": "2",
            "required": "true",
            "owner": change["module"],
            "optional_reason": "none",
            "review_at": "none",
        })
        discoveries.append({
            "module": change["module"],
            "source_id": sid,
            "source_fqcn": change["report_fqcn"],
            "report_fqcn": change["report_fqcn"],
            "discovered_test_nodes": change["step4_expected_testcase_nodes"],
            "runtime_deferred_containers": "0",
            "engine_ids": "junit-jupiter",
            "source_sha256": change["step4_source_sha256"],
            # Replaced for every derived discovery row by bind_current_workspace
            # after all reviewed amendments have been materialized.
            "test_classes_sha256": "0" * 64,
            "main_classes_sha256": "0" * 64,
        })
        sources.append({
            "source_id": sid,
            "module": change["module"],
            "reactor_member": "true",
            "source_root": source_root,
            "source_path": change["source_path"],
            "top_level_fqcn": change["report_fqcn"],
            "kind": "executable",
            "discovery_patterns": "*Test",
            "disposition": "reactor-owned-executable",
            "owner": change["module"],
            "reason": "Step 4 reviewed report amendment",
        })
        source_ids.add(sid)
        execution_keys.add(key)

    bind_current_workspace(root, discoveries, sources)
    executions.sort(key=lambda row: row["execution_key"])
    discoveries.sort(key=lambda row: (row["module"], row["source_fqcn"], row["report_fqcn"]))
    sources.sort(key=lambda row: row["source_path"])
    require(len(executions) == 729, "E_DERIVED_COUNTS", "derived positive report count differs")
    require(len(structural) == 59, "E_DERIVED_COUNTS", "derived structural report count differs")
    require(len(discoveries) == 825, "E_DERIVED_COUNTS", "derived discovery row count differs")
    require(len(sources) == 537, "E_DERIVED_COUNTS", "derived source row count differs")
    require(
        sum(row["runner"] == "surefire" for row in executions) == 682
        and sum(row["runner"] == "failsafe" for row in executions) == 47,
        "E_DERIVED_COUNTS",
        "derived runner report counts differ",
    )
    return executions, structural, discoveries, sources


def provenance_payload(
    root: Path,
    run_id: str,
    contract: dict[str, Any],
    contract_path: Path,
    amendment: Sequence[dict[str, str]],
    parent_dir: Path,
    discoveries: Sequence[dict[str, str]],
    sources: Sequence[dict[str, str]],
) -> dict[str, Any]:
    parent_hashes = contract["parent"]["artifacts"]
    source_bindings = []
    parent_discovery = {
        row["report_fqcn"]: row
        for row in read_tsv(parent_dir / DISCOVERY_FILE, DISCOVERY_HEADER, "E_PARENT_SEMANTICS")
    }
    for row in sorted(amendment, key=lambda item: item["report_fqcn"]):
        source_bindings.append({
            "change_kind": row["change_kind"],
            "current_source_sha256": row["step4_source_sha256"],
            "parent_discovery_source_sha256": (
                parent_discovery[row["report_fqcn"]]["source_sha256"]
                if row["report_fqcn"] in parent_discovery else "absent"
            ),
            "report_fqcn": row["report_fqcn"],
            "reviewed_prior_source_sha256": row["step2_source_sha256"],
            "source_path": row["source_path"],
        })
    return {
        "schema_version": 1,
        "kind": "v934-step4-step2-report-view-parent-link",
        "run_id": run_id,
        "status": "materialized-not-step4-acceptance",
        "contract": {
            "path": CONTRACT_RELATIVE.as_posix(),
            "sha256": sha256_file(contract_path),
        },
        "parent": {
            "directory": contract["parent"]["directory"],
            "hash_manifest_sha256": contract["parent"]["hash_manifest_sha256"],
            "contract_freeze_sha256": parent_hashes[FREEZE_FILE],
            "required_execution_sha256": parent_hashes[EXECUTION_FILE],
            "structural_inventory_sha256": parent_hashes[STRUCTURAL_FILE],
            "discovery_inventory_sha256": parent_hashes[DISCOVERY_FILE],
            "source_inventory_sha256": parent_hashes[SOURCE_FILE],
            "runner_contract_sha256": parent_hashes[RUNNER_FILE],
        },
        "amendment": {
            **contract["amendment"],
            "copied_as": AMENDMENT_FILE,
        },
        "counts": copy.deepcopy(contract["counts"]),
        "report_tool": copy.deepcopy(contract["report_tool"]),
        "current_workspace_binding": {
            "discovery_inventory_sha256": sha256_bytes(tsv_bytes(DISCOVERY_HEADER, discoveries)),
            "discovery_rows": len(discoveries),
            "source_inventory_sha256": sha256_bytes(tsv_bytes(SOURCE_HEADER, sources)),
            "source_rows": len(sources),
            "semantics": "fresh-test-compile-current-source-and-module-class-trees",
        },
        "source_bindings": source_bindings,
    }


def derive_runner_contract(
    parent_dir: Path,
    provenance: dict[str, Any],
) -> dict[str, Any]:
    runner = copy.deepcopy(read_json(parent_dir / RUNNER_FILE, "E_PARENT_SEMANTICS"))
    evidence = runner.get("report_evidence_contract")
    require(isinstance(evidence, dict), "E_PARENT_SEMANTICS", "missing report evidence contract")
    variant_counts = evidence.get("variant_report_cardinality")
    runner_counts = evidence.get("runner_report_cardinality")
    require(isinstance(variant_counts, dict) and isinstance(runner_counts, dict), "E_PARENT_SEMANTICS", "missing report counts")
    variant_counts["surefire/unit"] = {"positive": 682, "structural": 55, "raw": 737}
    runner_counts["surefire"] = {"positive": 682, "structural": 55, "raw": 737}
    runner_counts["failsafe"] = {"positive": 47, "structural": 4, "raw": 51}
    runner_counts["step2"] = {"positive": 729, "structural": 59, "raw": 788}
    ownership = runner.get("ownership")
    require(isinstance(ownership, dict), "E_PARENT_SEMANTICS", "missing runner ownership")
    ownership["discovery_report_containers"]["surefire"] = 737
    ownership["executable_reactor_sources"] = 519
    ownership["execution_owners"]["surefire"] = 682
    ownership["positive_report_owners"]["surefire"] = 682
    ownership["source_owners"]["surefire"] = 476
    ownership["step2_owners"]["surefire"] = 682
    runner["step4_report_view"] = {
        "kind": provenance["kind"],
        "run_id": provenance["run_id"],
        "status": provenance["status"],
        "parent_hash_manifest_sha256": provenance["parent"]["hash_manifest_sha256"],
        "amendment_sha256": provenance["amendment"]["sha256"],
        "positive_reports": 729,
        "structural_reports": 59,
        "testcases": 5263,
    }
    return runner


def derive_freeze(parent_dir: Path, provenance: dict[str, Any]) -> dict[str, Any]:
    freeze = copy.deepcopy(read_json(parent_dir / FREEZE_FILE, "E_PARENT_SEMANTICS"))
    counts = freeze.get("counts")
    require(isinstance(counts, dict), "E_PARENT_SEMANTICS", "missing parent freeze counts")
    counts.update({
        "required_step2": 729,
        "structural_reports": 59,
        "execution_keys": 775,
        "discovery_reports": 809,
        "discovery_rows": 825,
        "reactor_sources": 535,
        "workspace_sources": 537,
    })
    zero = freeze.get("zero_container_amendment")
    if isinstance(zero, dict):
        zero["positive_execution_keys"] = 775
        zero["required_step2"] = 729
        zero["structural_reports"] = 59
    freeze["step4_report_view"] = {
        "schema_version": 1,
        "kind": provenance["kind"],
        "run_id": provenance["run_id"],
        "status": provenance["status"],
        "parent": provenance["parent"],
        "amendment": provenance["amendment"],
        "counts": provenance["counts"],
        "compatibility": {
            "report_tool_path": provenance["report_tool"]["path"],
            "report_tool_sha256": provenance["report_tool"]["sha256"],
            "successor_argument": "--successor-dir",
        },
    }
    return freeze


def build_view_files(
    root: Path,
    run_id: str,
    contract: dict[str, Any],
    contract_path: Path,
    parent_dir: Path,
    amendment_path: Path,
) -> dict[str, bytes]:
    amendment = load_amendment(root, amendment_path, contract)
    executions, _structural, discoveries, sources = apply_amendment(root, parent_dir, amendment)
    provenance = provenance_payload(
        root,
        run_id,
        contract,
        contract_path,
        amendment,
        parent_dir,
        discoveries,
        sources,
    )
    files = {
        EXECUTION_FILE: tsv_bytes(EXECUTION_HEADER, executions),
        STRUCTURAL_FILE: (parent_dir / STRUCTURAL_FILE).read_bytes(),
        DISCOVERY_FILE: tsv_bytes(DISCOVERY_HEADER, discoveries),
        SOURCE_FILE: tsv_bytes(SOURCE_HEADER, sources),
        RUNNER_FILE: json_bytes(derive_runner_contract(parent_dir, provenance)),
        FREEZE_FILE: json_bytes(derive_freeze(parent_dir, provenance)),
        PARENT_LINK_FILE: json_bytes(provenance),
        AMENDMENT_FILE: amendment_path.read_bytes(),
    }
    expected_entries = contract["output"]["manifest_entries"]
    require(sorted(files) == expected_entries, "E_CONTRACT", "generated file set differs")
    files[HASH_FILE] = "".join(
        f"{sha256_bytes(files[name])}  {name}\n" for name in sorted(files)
    ).encode("utf-8")
    return files


def canonical_paths(root: Path, run_id: str) -> tuple[Path, Path, Path]:
    run_id = safe_run_id(run_id)
    runs_root = root / "target/v934-step4-coverage/runs"
    run_root = runs_root / run_id
    view = run_root / "step2-report-view"
    return runs_root, run_root, view


def validate_run_root(root: Path, run_id: str) -> tuple[Path, Path]:
    runs_root, run_root, view = canonical_paths(root, run_id)
    require(runs_root.is_dir() and not runs_root.is_symlink(), "E_RUN_ROOT", f"missing real runs root: {runs_root}")
    require(runs_root.resolve() == runs_root.absolute(), "E_RUN_ROOT", "runs root contains a symlink")
    require(run_root.is_dir() and not run_root.is_symlink(), "E_RUN_ROOT", f"missing real run root: {run_root}")
    require(run_root.resolve() == run_root.absolute(), "E_RUN_ROOT", "run root contains a symlink")
    return run_root, view


def materialize(root: Path, run_id: str) -> Path:
    contract, contract_path = load_contract(root)
    parent_dir = root / contract["parent"]["directory"]
    validate_parent_directory(root, parent_dir, contract, enforce_location=True)
    amendment_path = root / contract["amendment"]["path"]
    _run_root, view = validate_run_root(root, run_id)
    if view.is_symlink():
        fail("E_OUTPUT_PATH", f"refusing symlink output: {view}")
    require(not view.exists(), "E_OUTPUT", f"view already exists: {view}")
    files = build_view_files(root, run_id, contract, contract_path, parent_dir, amendment_path)
    temporary = Path(tempfile.mkdtemp(prefix=".step2-report-view.", dir=view.parent))
    try:
        require(not temporary.is_symlink(), "E_OUTPUT_PATH", "temporary view is a symlink")
        for name, data in files.items():
            path = temporary / name
            with path.open("xb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            path.chmod(0o644)
        temporary_fd = os.open(
            temporary,
            os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
        )
        try:
            os.fsync(temporary_fd)
        finally:
            os.close(temporary_fd)
        libc = ctypes.CDLL(None, use_errno=True)
        renameat2 = getattr(libc, "renameat2", None)
        require(renameat2 is not None, "E_OUTPUT", "renameat2 no-replace is unavailable")
        renameat2.argtypes = (
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_uint,
        )
        renameat2.restype = ctypes.c_int
        result = renameat2(
            -100,
            os.fsencode(temporary),
            -100,
            os.fsencode(view),
            1,
        )
        if result != 0:
            error_number = ctypes.get_errno()
            fail(
                "E_OUTPUT",
                f"no-clobber directory publication failed: errno={error_number}",
            )
        parent_fd = os.open(
            view.parent,
            os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
        )
        try:
            os.fsync(parent_fd)
        finally:
            os.close(parent_fd)
    except Exception:
        shutil.rmtree(temporary, ignore_errors=True)
        raise
    validate_view(root, run_id)
    return view


def content_error(name: str) -> str:
    if name == STRUCTURAL_FILE:
        return "E_STRUCTURAL"
    if name == PARENT_LINK_FILE:
        return "E_PROVENANCE"
    return "E_DERIVED_DELTA"


def validate_view(root: Path, run_id: str) -> Path:
    contract, contract_path = load_contract(root)
    parent_dir = root / contract["parent"]["directory"]
    parent, report_tool = validate_parent_directory(root, parent_dir, contract, enforce_location=True)
    amendment_path = root / contract["amendment"]["path"]
    _run_root, view = validate_run_root(root, run_id)
    require(view.is_dir() and not view.is_symlink(), "E_VIEW_PATH", f"missing real view: {view}")
    require(view.resolve() == view.absolute(), "E_VIEW_PATH", "view path contains a symlink")
    expected_names = set(contract["output"]["manifest_entries"]) | {HASH_FILE}
    actual_names = {entry.name for entry in view.iterdir()}
    require(actual_names == expected_names, "E_VIEW_SET", "view contains missing/undeclared entries")
    for entry in view.iterdir():
        require(entry.is_file() and not entry.is_symlink(), "E_VIEW_PATH", f"view entry is not a real file: {entry}")
    provenance = read_json(view / PARENT_LINK_FILE, "E_PROVENANCE")
    require(
        provenance.get("kind") == "v934-step4-step2-report-view-parent-link"
        and provenance.get("run_id") == run_id
        and provenance.get("status") == "materialized-not-step4-acceptance",
        "E_PROVENANCE",
        "derived view is not owned by this exact run",
    )
    expected = build_view_files(root, run_id, contract, contract_path, parent_dir, amendment_path)
    for name in contract["output"]["manifest_entries"]:
        require(
            (view / name).read_bytes() == expected[name],
            content_error(name),
            f"derived view content differs: {name}",
        )
    require(
        (view / HASH_FILE).read_bytes() == expected[HASH_FILE],
        "E_VIEW_MANIFEST",
        "derived view hash manifest differs",
    )
    manifest = parse_hash_manifest(view / HASH_FILE, "E_VIEW_MANIFEST")
    require(set(manifest) == set(contract["output"]["manifest_entries"]), "E_VIEW_MANIFEST", "view manifest set differs")
    for name, digest in manifest.items():
        require(sha256_file(view / name) == digest, "E_VIEW_MANIFEST", f"view artifact hash differs: {name}")
    try:
        derived = report_tool.load_successor(view)
    except Exception as exc:
        fail("E_REPORT_INTERFACE", f"Step 2 report tool rejected derived view: {exc}")
    require(len(derived.rows) == 729 and len(derived.structural_rows) == 59, "E_REPORT_INTERFACE", "derived interface counts differ")
    parent_by_key = {row["execution_key"]: row for row in parent.rows}
    derived_by_key = {row["execution_key"]: row for row in derived.rows}
    require(
        all(derived_by_key.get(key) == row for key, row in parent_by_key.items()),
        "E_DERIVED_DELTA",
        "a parent execution identity/lane mapping changed",
    )
    require(
        sha256_file(view / STRUCTURAL_FILE) == contract["parent"]["artifacts"][STRUCTURAL_FILE],
        "E_STRUCTURAL",
        "structural inventory is not byte-identical to Step 2",
    )
    return view


def validate_parent_command(args: argparse.Namespace) -> None:
    root = args.repo_root.resolve()
    contract, _ = load_contract(root)
    parent_dir = root / contract["parent"]["directory"]
    parent, _tool = validate_parent_directory(root, parent_dir, contract, enforce_location=True)
    load_amendment(root, root / contract["amendment"]["path"], contract)
    print(f"{PREFIX} parent PASS positive={len(parent.rows)} structural={len(parent.structural_rows)} amendment=13")


def generate_command(args: argparse.Namespace) -> None:
    root = args.repo_root.resolve()
    view = materialize(root, args.run_id)
    print(f"{PREFIX} generated PASS run_id={args.run_id} positive=729 structural=59 testcases=5263 view={view}")


def validate_command(args: argparse.Namespace) -> None:
    root = args.repo_root.resolve()
    view = validate_view(root, args.run_id)
    print(f"{PREFIX} validate PASS run_id={args.run_id} positive=729 structural=59 testcases=5263 view={view}")


def expect_error(name: str, expected: str, action, results: list[tuple[str, str, str]]) -> None:
    try:
        action()
    except ViewError as exc:
        actual = exc.code
    else:
        actual = "none"
    require(actual == expected, "E_NEGATIVE", f"probe {name} actual={actual}, expected={expected}")
    results.append((name, expected, actual))


def negative_command(args: argparse.Namespace) -> None:
    root = args.repo_root.resolve()
    contract, _contract_path = load_contract(root)
    parent_dir = root / contract["parent"]["directory"]
    validate_parent_directory(root, parent_dir, contract, enforce_location=True)
    amendment_path = root / contract["amendment"]["path"]
    amendment = load_amendment(root, amendment_path, contract)
    runs_root = root / "target/v934-step4-coverage/runs"
    runs_root.mkdir(parents=True, exist_ok=True)
    require(runs_root.is_dir() and not runs_root.is_symlink(), "E_RUN_ROOT", "negative runs root is unsafe")
    nonce = f"{os.getpid()}-{random.SystemRandom().randrange(1, 10**12)}"
    created: list[Path] = []
    results: list[tuple[str, str, str]] = []

    def new_run(label: str, *, generate: bool = True) -> tuple[str, Path, Path]:
        run_id = f"negative-step2-view-{label}-{nonce}"
        _runs, run_root, view = canonical_paths(root, run_id)
        run_root.mkdir(mode=0o755)
        created.append(run_root)
        if generate:
            materialize(root, run_id)
        return run_id, run_root, view

    try:
        with tempfile.TemporaryDirectory(prefix="v934-step2-parent-probe-") as temporary_name:
            copied = Path(temporary_name) / "step2"
            shutil.copytree(parent_dir, copied)
            with (copied / EXECUTION_FILE).open("ab") as stream:
                stream.write(b"\n")
            expect_error(
                "parent-artifact-drift", "E_PARENT_HASH",
                lambda: validate_parent_directory(root, copied, contract, enforce_location=False),
                results,
            )

        with tempfile.TemporaryDirectory(prefix="v934-step2-amendment-probe-") as temporary_name:
            copied = Path(temporary_name) / AMENDMENT_FILE
            shutil.copy2(amendment_path, copied)
            with copied.open("ab") as stream:
                stream.write(b"\n")
            expect_error(
                "amendment-hash-drift", "E_AMENDMENT_HASH",
                lambda: load_amendment(root, copied, contract, validate_sources=False),
                results,
            )

        with tempfile.TemporaryDirectory(prefix="v934-step2-source-probe-") as temporary_name:
            fixture_root = Path(temporary_name)
            for row in amendment:
                target = fixture_root / row["source_path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(root / row["source_path"], target)
            with (fixture_root / amendment[0]["source_path"]).open("ab") as stream:
                stream.write(b"\n")
            expect_error(
                "workspace-source-drift", "E_SOURCE_HASH",
                lambda: validate_workspace_sources(fixture_root, amendment),
                results,
            )

        run_id, _run_root, _view = new_run("overwrite")
        expect_error("overwrite", "E_OUTPUT", lambda: materialize(root, run_id), results)

        run_id, _run_root, view = new_run("output-symlink", generate=False)
        with tempfile.TemporaryDirectory(prefix="v934-step2-view-link-") as link_target:
            os.symlink(link_target, view)
            expect_error("output-symlink", "E_OUTPUT_PATH", lambda: materialize(root, run_id), results)
            view.unlink()

        run_id, _run_root, view = new_run("undeclared-extra")
        (view / "undeclared.txt").write_text("undeclared\n", encoding="utf-8")
        expect_error("undeclared-extra", "E_VIEW_SET", lambda: validate_view(root, run_id), results)

        run_id, _run_root, view = new_run("execution-drift")
        with (view / EXECUTION_FILE).open("ab") as stream:
            stream.write(b"\n")
        expect_error("execution-drift", "E_DERIVED_DELTA", lambda: validate_view(root, run_id), results)

        run_id, _run_root, view = new_run("structural-drift")
        with (view / STRUCTURAL_FILE).open("ab") as stream:
            stream.write(b"\n")
        expect_error("structural-drift", "E_STRUCTURAL", lambda: validate_view(root, run_id), results)

        run_id, _run_root, view = new_run("provenance-drift")
        with (view / PARENT_LINK_FILE).open("ab") as stream:
            stream.write(b"\n")
        expect_error("provenance-drift", "E_PROVENANCE", lambda: validate_view(root, run_id), results)

        _source_run_id, _source_run_root, source_view = new_run("splice-source")
        target_run_id, _target_run_root, target_view = new_run("splice-target", generate=False)
        shutil.copytree(source_view, target_view)
        expect_error(
            "cross-run-view-splice",
            "E_PROVENANCE",
            lambda: validate_view(root, target_run_id),
            results,
        )

        run_id, _run_root, view = new_run("entry-symlink")
        (view / SOURCE_FILE).unlink()
        os.symlink(parent_dir / SOURCE_FILE, view / SOURCE_FILE)
        expect_error("entry-symlink", "E_VIEW_PATH", lambda: validate_view(root, run_id), results)

        run_id, _run_root, view = new_run("manifest-drift")
        with (view / HASH_FILE).open("ab") as stream:
            stream.write(b"\n")
        expect_error("manifest-drift", "E_VIEW_MANIFEST", lambda: validate_view(root, run_id), results)
    finally:
        for run_root in reversed(created):
            if run_root.exists() and run_root.parent == runs_root:
                shutil.rmtree(run_root, ignore_errors=True)

    for name, expected, actual in results:
        print(f"{name}\t{expected}\t{actual}\tpassed")
    print(f"{PREFIX} negative PASS probes={len(results)}")


def build_parser() -> argparse.ArgumentParser:
    root_default = Path(__file__).resolve().parents[3]
    parser = argparse.ArgumentParser(
        description="Generate and validate the Step 4 derived Step 2 report view."
    )
    subparsers = parser.add_subparsers(required=True)

    parent = subparsers.add_parser("validate-parent", help="validate immutable parent and amendment")
    parent.add_argument("--repo-root", "--root", dest="repo_root", type=Path, default=root_default)
    parent.set_defaults(handler=validate_parent_command)

    for name, handler, help_text in (
        ("generate", generate_command, "materialize a new canonical run-owned view"),
        ("validate", validate_command, "validate an existing canonical run-owned view"),
    ):
        command = subparsers.add_parser(name, help=help_text)
        command.add_argument("--repo-root", "--root", dest="repo_root", type=Path, default=root_default)
        command.add_argument("--run-id", required=True)
        command.set_defaults(handler=handler)

    negative = subparsers.add_parser("negative", help="run fail-closed view probes")
    negative.add_argument("--repo-root", "--root", dest="repo_root", type=Path, default=root_default)
    negative.set_defaults(handler=negative_command)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        args.handler(args)
        return 0
    except ViewError as exc:
        print(f"{PREFIX} ERROR {exc}", file=sys.stderr)
        return 1
    except (OSError, ValueError) as exc:
        print(f"{PREFIX} ERROR E_IO: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
