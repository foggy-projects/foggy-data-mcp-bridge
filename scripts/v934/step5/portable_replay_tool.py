#!/usr/bin/env python3
"""Materialize and semantically replay one extracted v9.3.4 release candidate.

The input is the root produced by ``release_artifact_tool.py extract-verify``.
This verifier deliberately does not remove materialized evidence: callers are
expected to use an ephemeral checkout and may inspect every canonical target
after the replay.
"""

from __future__ import annotations

import argparse
import errno
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import tempfile
import unicodedata
from dataclasses import dataclass
from typing import Any, Iterable, Sequence


HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")
ENV_KEY = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
RUN_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
FIXTURE_RUN_ID = re.compile(r"unit-mysql57-[0-9a-f]{16}")
CONTROL = re.compile(r"[\x00-\x1f\x7f]")

FREEZE_RELATIVE = Path("scripts/v934/contract-freeze.json")
SOURCE_TOOL_RELATIVE = Path("scripts/v934/step4/coverage_tool.py")
COVERAGE_TOOL_RELATIVE = Path("scripts/v934/step4/coverage_xml_tool.py")
DATABASE_TOOL_RELATIVE = Path(
    "scripts/v934/step4/successor/database_matrix_report_tool.py"
)
DATABASE_CONTRACT_RELATIVE = Path(
    "scripts/v934/step4/successor/database-matrix-contract.json"
)
UNIT_FIXTURE_TOOL_RELATIVE = Path(
    "scripts/v934/step4/unit_mysql_fixture_tool.py"
)
PACKAGE_TOOL_RELATIVE = Path("scripts/v934/step5/release_package_tool.py")
ARTIFACT_TOOL_RELATIVE = Path("scripts/v934/step5/release_artifact_tool.py")
ARTIFACT_CONTRACT_RELATIVE = Path(
    "scripts/v934/step5/release-artifact-contract.json"
)

EVIDENCE_TARGETS = {
    "step4": Path("target/v934-step4-coverage/runs"),
    "unit": Path("target/v934-step2-unit/runs"),
    "integration": Path("target/v934-step2-integration/runs"),
    "step3-required": Path("target/v934-step3-required-matrix/runs"),
    "database": Path("target/v934-step3-database-matrix/runs"),
    "external": Path("target/v934-step3-external-matrix/runs"),
    "addon": Path("target/v934-step3-preagg-addon/runs"),
}
EVIDENCE_LABELS = tuple(EVIDENCE_TARGETS) + ("unit-database",)
TOP_LEVEL = {"META-INF", "release", "evidence", "package", "tested-classes"}
PACKAGE_FILES = {
    "app.jar",
    "package-manifest.json",
    "image-manifest.json",
    "maven-invocations.log",
    "docker-build.log",
    "tested-tree-validation.log",
}
RUNTIME_SOURCE_MODULES = (
    "addons/foggy-chart-storage-cloud",
    "addons/foggy-data-viewer",
    "addons/foggy-odoo-bridge-java",
    "foggy-bean-copy",
    "foggy-core",
    "foggy-dataset",
    "foggy-dataset-demo",
    "foggy-dataset-mcp",
    "foggy-dataset-memory-grid-bridge",
    "foggy-dataset-model",
    "foggy-fsscript",
    "foggy-mcp-launcher",
    "foggy-mcp-spi",
)

EXPECTED_STEP4_SUMMARY = {
    "mode": "release",
    "threshold_status": "confirmed",
    "release_successor": "confirmed-threshold-post-step4-replay",
    "exec_files": "23",
    "sessions": "48",
    "required_reports": "773",
    "required_structural_reports": "59",
    "required_testcase_nodes": "5707",
    "addon_reports": "2",
    "addon_testcase_nodes": "6",
    "model_external_gate": "passed",
    "acceptance_candidate": "required",
    "status": "release-candidate-ready",
}


class ReplayError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def reject(code: str, message: str) -> None:
    raise ReplayError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def canonical_json(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        + "\n"
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def unique_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            reject("E_JSON_DUPLICATE", f"duplicate JSON key: {key}")
        result[key] = value
    return result


def parse_json_bytes(value: bytes, label: str) -> dict[str, Any]:
    try:
        parsed = json.loads(
            value.decode("utf-8"),
            object_pairs_hook=unique_json_object,
            parse_constant=lambda token: reject(
                "E_JSON", f"{label} contains a non-finite number: {token}"
            ),
        )
    except ReplayError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        reject("E_JSON", f"cannot parse {label}: {exc}")
    require(type(parsed) is dict, "E_JSON", f"{label} must be a JSON object")
    return parsed


def exact_keys(
    value: dict[str, Any], expected: Iterable[str], code: str, label: str
) -> None:
    wanted = set(expected)
    actual = set(value)
    require(
        actual == wanted,
        code,
        f"{label} keys differ: missing={sorted(wanted - actual)} "
        f"extra={sorted(actual - wanted)}",
    )


def absolute_path(path: Path) -> Path:
    return Path(os.path.abspath(path))


def lexists(path: Path) -> bool:
    return os.path.lexists(os.fspath(path))


def real_directory(path: Path, label: str) -> Path:
    absolute = absolute_path(path)
    try:
        observed = os.lstat(absolute)
    except FileNotFoundError:
        reject("E_DIRECTORY", f"missing {label}: {absolute}")
    except OSError as exc:
        reject("E_DIRECTORY", f"cannot inspect {label}: {absolute}: {exc}")
    require(
        stat.S_ISDIR(observed.st_mode) and not stat.S_ISLNK(observed.st_mode),
        "E_DIRECTORY",
        f"{label} is not a real directory: {absolute}",
    )
    try:
        resolved = absolute.resolve(strict=True)
    except OSError as exc:
        reject("E_DIRECTORY", f"cannot resolve {label}: {absolute}: {exc}")
    require(resolved == absolute, "E_SYMLINK", f"{label} has symlinked components")
    return absolute


def regular_file(path: Path, label: str) -> Path:
    absolute = absolute_path(path)
    try:
        observed = os.lstat(absolute)
    except FileNotFoundError:
        reject("E_FILE_MISSING", f"missing {label}: {absolute}")
    except OSError as exc:
        reject("E_FILE", f"cannot inspect {label}: {absolute}: {exc}")
    require(
        stat.S_ISREG(observed.st_mode) and not stat.S_ISLNK(observed.st_mode),
        "E_SPECIAL",
        f"{label} is not a regular file: {absolute}",
    )
    require(
        absolute.resolve(strict=True) == absolute,
        "E_SYMLINK",
        f"{label} has symlinked components",
    )
    return absolute


def secure_bytes(path: Path, label: str, maximum: int | None = None) -> bytes:
    absolute = regular_file(path, label)
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except OSError as exc:
        reject("E_FILE", f"cannot open {label}: {absolute}: {exc}")
    try:
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not regular")
        if maximum is not None:
            require(before.st_size <= maximum, "E_FILE_SIZE", f"{label} is too large")
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_RACE", f"short read from {label}")
            chunks.append(chunk)
            remaining -= len(chunk)
        require(os.read(descriptor, 1) == b"", "E_FILE_RACE", f"{label} grew")
        after = os.fstat(descriptor)
        require(
            (
                before.st_dev,
                before.st_ino,
                before.st_size,
                before.st_mtime_ns,
                before.st_ctime_ns,
            )
            == (
                after.st_dev,
                after.st_ino,
                after.st_size,
                after.st_mtime_ns,
                after.st_ctime_ns,
            ),
            "E_FILE_RACE",
            f"{label} changed while read",
        )
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def parse_env(path: Path, label: str) -> tuple[dict[str, str], dict[str, Any]]:
    raw = secure_bytes(path, label, 2 * 1024 * 1024)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        reject("E_ENV", f"{label} is not UTF-8: {exc}")
    require(text.endswith("\n"), "E_ENV", f"{label} is not newline terminated")
    result: dict[str, str] = {}
    for number, line in enumerate(text.splitlines(), 1):
        require(bool(line) and "=" in line, "E_ENV", f"malformed {label} line {number}")
        key, value = line.split("=", 1)
        require(ENV_KEY.fullmatch(key) is not None, "E_ENV", f"invalid {label} key")
        require(key not in result, "E_ENV_DUPLICATE", f"duplicate {label} key: {key}")
        require(CONTROL.search(value) is None, "E_ENV", f"control byte in {label}")
        result[key] = value
    return result, {"sha256": sha256_bytes(raw), "size": len(raw)}


def safe_relative(value: str, label: str) -> str:
    require(type(value) is str and bool(value), "E_PATH", f"empty {label}")
    require(value == unicodedata.normalize("NFC", value), "E_PATH", f"{label} is not NFC")
    require("\\" not in value and CONTROL.search(value) is None, "E_PATH", f"unsafe {label}")
    pure = PurePosixPath(value)
    require(not pure.is_absolute(), "E_PATH", f"absolute {label}")
    require(
        all(component not in ("", ".", "..") for component in value.split("/")),
        "E_PATH",
        f"non-canonical {label}",
    )
    require(pure.as_posix() == value, "E_PATH", f"non-canonical {label}")
    return value


@dataclass(frozen=True)
class Entry:
    path: str
    kind: str
    mode: int
    size: int
    mtime_ns: int
    sha256: str | None
    device: int
    inode: int

    def digest_row(self) -> dict[str, Any]:
        row: dict[str, Any] = {
            "kind": self.kind,
            "mode": f"{self.mode & 0o7777:04o}",
            "mtime_ns": self.mtime_ns,
            "path": self.path,
        }
        if self.kind == "file":
            row.update({"sha256": self.sha256, "size": self.size})
        return row


@dataclass(frozen=True)
class TreeSnapshot:
    entries: dict[str, Entry]
    sha256: str
    files: int
    directories: int
    bytes: int


def snapshot_from_entries(entries: dict[str, Entry]) -> TreeSnapshot:
    ordered = [entries[path].digest_row() for path in sorted(entries, key=lambda p: p.encode("utf-8"))]
    return TreeSnapshot(
        entries=entries,
        sha256=sha256_bytes(canonical_json(ordered)),
        files=sum(entry.kind == "file" for entry in entries.values()),
        directories=sum(entry.kind == "directory" for entry in entries.values()),
        bytes=sum(entry.size for entry in entries.values() if entry.kind == "file"),
    )


def scan_tree(root: Path, label: str) -> TreeSnapshot:
    base = real_directory(root, label)
    entries: dict[str, Entry] = {}
    folded: dict[str, str] = {}
    inodes: dict[tuple[int, int], str] = {}

    def visit(directory: Path, prefix: str) -> None:
        try:
            children = sorted(os.scandir(directory), key=lambda row: row.name.encode("utf-8"))
        except OSError as exc:
            reject("E_DIRECTORY", f"cannot scan {label}: {directory}: {exc}")
        for child in children:
            relative = f"{prefix}/{child.name}" if prefix else child.name
            safe_relative(relative, f"{label} entry")
            alias = relative.casefold()
            require(
                alias not in folded,
                "E_PATH_COLLISION",
                f"case-fold collision in {label}: {folded.get(alias)}, {relative}",
            )
            folded[alias] = relative
            path = Path(child.path)
            try:
                observed = os.lstat(path)
            except OSError as exc:
                reject("E_FILE_RACE", f"cannot inspect {label} entry {relative}: {exc}")
            require(not stat.S_ISLNK(observed.st_mode), "E_SYMLINK", f"symlink in {label}: {relative}")
            if stat.S_ISDIR(observed.st_mode):
                entries[relative] = Entry(
                    relative,
                    "directory",
                    observed.st_mode,
                    0,
                    observed.st_mtime_ns,
                    None,
                    observed.st_dev,
                    observed.st_ino,
                )
                visit(path, relative)
            elif stat.S_ISREG(observed.st_mode):
                identity = (observed.st_dev, observed.st_ino)
                require(
                    identity not in inodes,
                    "E_HARDLINK",
                    f"hard-linked input files are forbidden in {label}: "
                    f"{inodes.get(identity)}, {relative}",
                )
                inodes[identity] = relative
                raw = secure_bytes(path, f"{label} entry {relative}")
                after = os.lstat(path)
                require(
                    (observed.st_dev, observed.st_ino, observed.st_size, observed.st_mtime_ns)
                    == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns),
                    "E_FILE_RACE",
                    f"{label} entry changed: {relative}",
                )
                entries[relative] = Entry(
                    relative,
                    "file",
                    observed.st_mode,
                    observed.st_size,
                    observed.st_mtime_ns,
                    sha256_bytes(raw),
                    observed.st_dev,
                    observed.st_ino,
                )
            else:
                reject("E_SPECIAL", f"special entry in {label}: {relative}")

    visit(base, "")
    return snapshot_from_entries(entries)


def direct_children(snapshot: TreeSnapshot, prefix: str) -> dict[str, Entry]:
    prefix_with_slash = prefix + "/"
    result: dict[str, Entry] = {}
    for path, entry in snapshot.entries.items():
        if path.startswith(prefix_with_slash):
            remainder = path[len(prefix_with_slash) :]
            if "/" not in remainder:
                result[remainder] = entry
    return result


def subtree_snapshot(snapshot: TreeSnapshot, prefix: str) -> TreeSnapshot:
    marker = prefix + "/"
    values: dict[str, Entry] = {}
    for path, entry in snapshot.entries.items():
        if path.startswith(marker):
            relative = path[len(marker) :]
            values[relative] = Entry(
                relative,
                entry.kind,
                entry.mode,
                entry.size,
                entry.mtime_ns,
                entry.sha256,
                entry.device,
                entry.inode,
            )
    return snapshot_from_entries(values)


def load_frozen_modules(repo: Path) -> tuple[list[str], dict[str, Any]]:
    freeze = regular_file(repo / FREEZE_RELATIVE, "Step 1 contract freeze")
    raw = secure_bytes(freeze, "Step 1 contract freeze", 8 * 1024 * 1024)
    value = parse_json_bytes(raw, "Step 1 contract freeze")
    reactor = value.get("reactor")
    require(type(reactor) is dict, "E_FREEZE", "contract freeze reactor is missing")
    modules = reactor.get("modules")
    require(
        type(modules) is list
        and reactor.get("module_count") == 24
        and type(reactor.get("module_count")) is int
        and len(modules) == 24
        and len(set(modules)) == 24
        and modules == sorted(modules),
        "E_FREEZE",
        "contract freeze does not contain exact sorted 24 modules",
    )
    parsed: list[str] = []
    for number, module in enumerate(modules, 1):
        require(type(module) is str, "E_FREEZE", f"module {number} is not a string")
        parsed_module = safe_relative(module, f"frozen module {number}")
        real_directory(repo / PurePosixPath(parsed_module), f"frozen module {parsed_module}")
        parsed.append(parsed_module)
    return parsed, {
        "module_count": 24,
        "modules_sha256": sha256_bytes(canonical_json(parsed)),
        "path": FREEZE_RELATIVE.as_posix(),
        "sha256": sha256_bytes(raw),
        "size": len(raw),
    }


def validate_context(root: Path) -> tuple[dict[str, str], dict[str, Any]]:
    context, binding = parse_env(root / "release/context.env", "release context")
    exact_keys(
        context,
        (
            "schema_version",
            "kind",
            "run_id",
            "mode",
            "git_head",
            "source_sha256",
            "step4_run_id",
            "status",
        ),
        "E_CONTEXT",
        "release context",
    )
    require(
        context["schema_version"] == "1"
        and context["kind"] == "v934-release-candidate"
        and RUN_ID.fullmatch(context["run_id"]) is not None
        and context["mode"] in {"rehearsal", "authority"}
        and context["mode"] != "formal"
        and HEX40.fullmatch(context["git_head"]) is not None
        and HEX64.fullmatch(context["source_sha256"]) is not None
        and context["step4_run_id"] == context["run_id"]
        and context["status"] == "candidate",
        "E_CONTEXT",
        "release context identity/state differs",
    )
    return context, binding


def validate_sensitive(root: Path) -> tuple[dict[str, str], dict[str, Any]]:
    value, binding = parse_env(
        root / "release/sensitive-scan.env", "release sensitive scan"
    )
    exact_keys(
        value,
        (
            "schema_version",
            "kind",
            "contract_sha256",
            "policy_sha256",
            "scope",
            "pattern_count",
            "text_extension_count",
            "archive_extension_count",
            "files",
            "bytes",
            "status",
        ),
        "E_SENSITIVE",
        "release sensitive scan",
    )
    require(
        value["schema_version"] == "2"
        and value["kind"] == "v934-release-sensitive-scan"
        and HEX64.fullmatch(value["contract_sha256"]) is not None
        and HEX64.fullmatch(value["policy_sha256"]) is not None
        and value["scope"] == "payload-text-and-recursive-zip-archives"
        and value["pattern_count"] == "8"
        and value["text_extension_count"] == "46"
        and value["archive_extension_count"] == "2"
        and value["files"].isdigit()
        and value["bytes"].isdigit()
        and value["status"] == "passed",
        "E_SENSITIVE",
        "release sensitive scan is not the exact passed policy",
    )
    return value, binding


def validate_runtime_source(
    root: Path, context: dict[str, str], sensitive: dict[str, str]
) -> tuple[dict[str, Any], dict[str, Any]]:
    path = root / "release/runtime-source-scan.json"
    raw = secure_bytes(path, "runtime source scan receipt", 16 * 1024 * 1024)
    value = parse_json_bytes(raw, "runtime source scan receipt")
    exact_keys(
        value,
        (
            "bytes",
            "command",
            "contract_sha256",
            "files",
            "git_head",
            "kind",
            "module_count",
            "modules",
            "schema_version",
            "set_sha256",
            "status",
            "strict_policy_sha256",
        ),
        "E_RUNTIME_SOURCE",
        "runtime source scan receipt",
    )
    require(
        value["schema_version"] == 1
        and type(value["schema_version"]) is int
        and value["kind"] == "v934-runtime-source-sensitive-scan"
        and value["command"] == "scan-runtime-source"
        and value["status"] == "passed"
        and value["git_head"] == context["git_head"]
        and value["contract_sha256"] == sensitive["contract_sha256"]
        and value["module_count"] == len(RUNTIME_SOURCE_MODULES)
        and value["modules"] == list(RUNTIME_SOURCE_MODULES)
        and type(value["files"]) is int
        and value["files"] > 0
        and type(value["bytes"]) is int
        and value["bytes"] > 0
        and type(value["set_sha256"]) is str
        and HEX64.fullmatch(value["set_sha256"]) is not None
        and type(value["strict_policy_sha256"]) is str
        and HEX64.fullmatch(value["strict_policy_sha256"]) is not None,
        "E_RUNTIME_SOURCE",
        "runtime source scan receipt identity/policy differs",
    )
    return value, {"sha256": sha256_bytes(raw), "size": len(raw)}


def validate_step4_preflight(
    root: Path, context: dict[str, str]
) -> tuple[dict[str, str], dict[str, Any]]:
    summary, summary_binding = parse_env(
        root / "evidence/step4/summary.env", "Step 4 release summary"
    )
    for key, expected in EXPECTED_STEP4_SUMMARY.items():
        require(summary.get(key) == expected, "E_STEP4", f"Step 4 summary {key} differs")
    require(
        summary.get("run_id") == context["run_id"]
        and summary.get("git_head") == context["git_head"]
        and summary.get("source_before_sha256") == context["source_sha256"]
        and summary.get("source_after_sha256") == context["source_sha256"],
        "E_STEP4",
        "Step 4 summary context/source binding differs",
    )
    status, _ = parse_env(
        root / "evidence/step4/run-status.env", "Step 4 release run status"
    )
    require(
        status.get("run_id") == context["run_id"]
        and status.get("mode") == "release"
        and status.get("git_head") == context["git_head"]
        and status.get("exit_code") == "0"
        and status.get("status") == "release-passed",
        "E_STEP4",
        "Step 4 run status is not release-passed",
    )
    final_raw = secure_bytes(
        root / "evidence/step4/final-manifest.json",
        "Step 4 final manifest",
        64 * 1024 * 1024,
    )
    final = parse_json_bytes(final_raw, "Step 4 final manifest")
    require(
        final.get("stage") == "final"
        and final.get("status") == "release-final"
        and final.get("run_id") == context["run_id"]
        and final.get("git_head") == context["git_head"]
        and final.get("release_successor")
        == "confirmed-threshold-post-step4-replay",
        "E_STEP4",
        "Step 4 final manifest is not the release final",
    )
    return summary, summary_binding


def validate_fixture(root: Path, context: dict[str, str]) -> tuple[str, dict[str, Any]]:
    path = root / "evidence/unit/mysql57-fixture-manifest.json"
    raw = secure_bytes(path, "Unit MySQL 5.7 fixture manifest", 32 * 1024 * 1024)
    value = parse_json_bytes(raw, "Unit MySQL 5.7 fixture manifest")
    fixture_run_id = value.get("fixture_run_id")
    require(
        value.get("schema_version") == 1
        and type(value.get("schema_version")) is int
        and value.get("kind") == "v934-step4-unit-mysql57-fixture"
        and value.get("run_id") == context["run_id"]
        and value.get("database") == "mysql57"
        and value.get("status") == "passed"
        and type(fixture_run_id) is str
        and FIXTURE_RUN_ID.fullmatch(fixture_run_id) is not None
        and fixture_run_id != context["run_id"],
        "E_FIXTURE",
        "Unit MySQL 5.7 fixture manifest identity/state differs",
    )
    return fixture_run_id, {"sha256": sha256_bytes(raw), "size": len(raw)}


def module_for_class_path(path: str, modules: Sequence[str]) -> str | None:
    matches = [
        module
        for module in modules
        if path.startswith(f"{module}/target/classes/")
    ]
    return matches[0] if len(matches) == 1 else None


def validate_package_and_classes(
    root: Path,
    snapshot: TreeSnapshot,
    context: dict[str, str],
    modules: Sequence[str],
) -> tuple[dict[str, Any], dict[str, Any]]:
    manifest_path = root / "package/package-manifest.json"
    raw = secure_bytes(manifest_path, "package manifest", 64 * 1024 * 1024)
    manifest = parse_json_bytes(raw, "package manifest")
    require(
        manifest.get("schema_version") == 4
        and type(manifest.get("schema_version")) is int
        and manifest.get("kind") == "v934-tested-output-tree-package"
        and manifest.get("status") == "passed"
        and manifest.get("run_id") == context["run_id"]
        and manifest.get("git_head") == context["git_head"],
        "E_PACKAGE",
        "package manifest identity/state differs",
    )
    source = manifest.get("source")
    reactor = manifest.get("reactor")
    seals = manifest.get("seals")
    validation_log = manifest.get("validation_log")
    require(type(source) is dict, "E_PACKAGE", "package source seal is missing")
    require(
        source.get("sha256") == context["source_sha256"],
        "E_PACKAGE",
        "package source seal differs from release context",
    )
    require(
        type(reactor) is dict
        and reactor.get("module_count") == 24
        and reactor.get("modules") == list(modules),
        "E_PACKAGE",
        "package reactor differs from the frozen 24 modules",
    )
    require(
        type(validation_log) is dict,
        "E_PACKAGE",
        "package tested-tree validation log binding is missing",
    )
    exact_keys(
        validation_log,
        ("path", "sha256", "size"),
        "E_PACKAGE",
        "package tested-tree validation log binding",
    )
    validation_entry = snapshot.entries.get("package/tested-tree-validation.log")
    require(
        validation_log.get("path") == "tested-tree-validation.log"
        and type(validation_log.get("sha256")) is str
        and HEX64.fullmatch(validation_log["sha256"]) is not None
        and type(validation_log.get("size")) is int
        and validation_log["size"] > 0
        and validation_entry is not None
        and validation_entry.kind == "file"
        and validation_entry.sha256 == validation_log["sha256"]
        and validation_entry.size == validation_log["size"],
        "E_PACKAGE",
        "package tested-tree validation log differs from its durable binding",
    )
    require(type(seals) is dict, "E_PACKAGE", "package tested-tree seals are missing")
    before = seals.get("tested_tree_before")
    after = seals.get("tested_tree_after")
    require(
        type(before) is dict and before == after,
        "E_PACKAGE",
        "package tested-tree before/after seals differ",
    )
    rows = before.get("entries")
    require(type(rows) is list and bool(rows), "E_PACKAGE", "package tested entries are missing")
    expected: dict[str, dict[str, Any]] = {}
    module_counts = {module: 0 for module in modules}
    previous: bytes | None = None
    for number, row in enumerate(rows, 1):
        require(type(row) is dict, "E_PACKAGE", f"tested entry {number} is not an object")
        exact_keys(row, ("path", "sha256", "size"), "E_PACKAGE", f"tested entry {number}")
        path = safe_relative(row["path"], f"tested entry {number}")
        encoded = path.encode("utf-8")
        require(previous is None or previous < encoded, "E_PACKAGE", "tested entries are not sorted unique")
        previous = encoded
        module = module_for_class_path(path, modules)
        require(module is not None, "E_PACKAGE", f"tested entry is outside frozen modules: {path}")
        require(
            type(row["sha256"]) is str
            and HEX64.fullmatch(row["sha256"]) is not None
            and type(row["size"]) is int
            and row["size"] >= 0,
            "E_PACKAGE",
            f"tested entry identity differs: {path}",
        )
        if path.endswith(".class"):
            expected[path] = row
            module_counts[module] += 1
    require(
        expected and all(value > 0 for value in module_counts.values()),
        "E_CLASS_MISSING",
        "each frozen module must have at least one package-bound class file",
    )

    observed: dict[str, Entry] = {}
    class_prefix = "tested-classes/"
    for path, entry in snapshot.entries.items():
        if not path.startswith(class_prefix) or entry.kind != "file":
            continue
        relative = path[len(class_prefix) :]
        require(relative.endswith(".class"), "E_CLASS_EXTRA", f"non-class tested output: {relative}")
        observed[relative] = entry
    require(
        set(observed) == set(expected),
        "E_CLASS_MISSING",
        f"portable class set differs: missing={sorted(set(expected) - set(observed))[:8]} "
        f"extra={sorted(set(observed) - set(expected))[:8]}",
    )
    for path, entry in observed.items():
        wanted = expected[path]
        require(
            entry.sha256 == wanted["sha256"] and entry.size == wanted["size"],
            "E_CLASS_HASH",
            f"portable class differs from package seal: {path}",
        )

    allowed_directories = {"tested-classes"}
    for path in expected:
        current = PurePosixPath("tested-classes") / PurePosixPath(path).parent
        while current.as_posix() != ".":
            allowed_directories.add(current.as_posix())
            if current.as_posix() == "tested-classes":
                break
            current = current.parent
    unexpected_directories = sorted(
        path
        for path, entry in snapshot.entries.items()
        if path.startswith("tested-classes/")
        and entry.kind == "directory"
        and path not in allowed_directories
    )
    require(
        not unexpected_directories,
        "E_CLASS_EXTRA",
        f"unexpected tested-class directories: {unexpected_directories[:8]}",
    )
    class_rows = [
        {"path": path, "sha256": observed[path].sha256, "size": observed[path].size}
        for path in sorted(observed, key=lambda value: value.encode("utf-8"))
    ]
    return manifest, {
        "class_files": len(observed),
        "module_counts": module_counts,
        "sha256": sha256_bytes(canonical_json(class_rows)),
        "package_manifest_sha256": sha256_bytes(raw),
    }


def validate_layout(snapshot: TreeSnapshot) -> None:
    # Use explicit top-level extraction; evidence subtrees use the prefix
    # helper below.
    observed_top = {
        path: entry
        for path, entry in snapshot.entries.items()
        if "/" not in path
    }
    require(set(observed_top) == TOP_LEVEL, "E_LAYOUT", "extracted top-level layout differs")
    require(
        all(observed_top[name].kind == "directory" for name in TOP_LEVEL),
        "E_LAYOUT",
        "extracted top-level entries must be directories",
    )
    evidence = direct_children(snapshot, "evidence")
    require(
        set(evidence) == set(EVIDENCE_LABELS)
        and all(entry.kind == "directory" for entry in evidence.values()),
        "E_LAYOUT",
        "portable evidence tree set differs",
    )
    package = direct_children(snapshot, "package")
    require(
        set(package) == PACKAGE_FILES
        and all(entry.kind == "file" for entry in package.values()),
        "E_LAYOUT",
        "portable package file set differs",
    )
    for relative in (
        "release/context.env",
        "release/sensitive-scan.env",
        "release/runtime-source-scan.json",
        "evidence/step4/final-manifest.json",
        "evidence/step4/run-status.env",
        "evidence/step4/summary.env",
        "evidence/unit/mysql57-fixture-manifest.json",
    ):
        entry = snapshot.entries.get(relative)
        require(entry is not None and entry.kind == "file", "E_LAYOUT", f"missing {relative}")
    for label in EVIDENCE_LABELS:
        subtree = subtree_snapshot(snapshot, f"evidence/{label}")
        require(subtree.files > 0, "E_LAYOUT", f"empty evidence tree: {label}")
    metadata = direct_children(snapshot, "META-INF").get("v934")
    require(
        metadata is not None and metadata.kind == "directory",
        "E_LAYOUT",
        "release artifact metadata is missing",
    )


def output_path(path: Path, root: Path) -> Path:
    output = absolute_path(path)
    require(output.name not in ("", ".", ".."), "E_OUTPUT", "invalid output name")
    parent = real_directory(output.parent, "output parent")
    require(parent == output.parent, "E_OUTPUT", "output parent is non-canonical")
    require(not lexists(output), "E_OUTPUT_EXISTS", f"output already exists: {output}")
    require(
        output != root and root not in output.parents,
        "E_OUTPUT",
        "output must be outside the extracted root",
    )
    return output


def git_head(repo: Path) -> str:
    try:
        process = subprocess.run(
            ["git", "-C", os.fspath(repo), "rev-parse", "--verify", "HEAD^{commit}"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=30,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        reject("E_GIT", f"cannot resolve checkout HEAD: {exc}")
    require(process.returncode == 0, "E_GIT", "cannot resolve checkout HEAD")
    try:
        value = process.stdout.decode("ascii").strip()
    except UnicodeDecodeError:
        reject("E_GIT", "checkout HEAD is not ASCII")
    require(HEX40.fullmatch(value) is not None, "E_GIT", "checkout HEAD is invalid")
    return value


def validate_tool(repo: Path, relative: Path, label: str) -> tuple[Path, dict[str, Any]]:
    path = regular_file(repo / relative, label)
    raw = secure_bytes(path, label, 16 * 1024 * 1024)
    return path, {
        "path": relative.as_posix(),
        "sha256": sha256_bytes(raw),
        "size": len(raw),
    }


def ensure_directory_chain(repo: Path, relative: Path) -> Path:
    current = repo
    for component in relative.parts:
        current = current / component
        if lexists(current):
            real_directory(current, f"canonical target parent {relative}")
        else:
            try:
                current.mkdir(mode=0o755)
            except FileExistsError:
                pass
            real_directory(current, f"canonical target parent {relative}")
    return current


def validate_existing_ancestor_chain(repo: Path, destination: Path) -> None:
    """Reject a symlink/special existing component without creating anything."""
    require(destination.is_absolute(), "E_TARGET", "target path is not absolute")
    try:
        relative = destination.relative_to(repo)
    except ValueError:
        reject("E_TARGET", f"target is outside the repository: {destination}")
    current = repo
    missing_seen = False
    for component in relative.parts:
        current = current / component
        if missing_seen:
            require(
                not lexists(current),
                "E_TARGET",
                f"target descendant exists below a missing parent: {current}",
            )
        elif lexists(current):
            real_directory(current, f"existing target ancestor {current}")
        else:
            missing_seen = True


def verify_entry_unchanged(source: Path, entry: Entry, label: str) -> None:
    observed = os.lstat(source)
    require(
        stat.S_ISREG(observed.st_mode)
        and not stat.S_ISLNK(observed.st_mode)
        and (
            observed.st_dev,
            observed.st_ino,
            observed.st_size,
            observed.st_mtime_ns,
        )
        == (entry.device, entry.inode, entry.size, entry.mtime_ns),
        "E_SOURCE_RACE",
        f"source entry changed before materialization: {label}",
    )


def copy_new_file(source: Path, destination: Path, entry: Entry, label: str) -> None:
    source_flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
    target_flags = (
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0)
        | getattr(os, "O_CLOEXEC", 0)
    )
    source_fd = -1
    target_fd = -1
    try:
        source_fd = os.open(source, source_flags)
        before = os.fstat(source_fd)
        require(
            stat.S_ISREG(before.st_mode)
            and (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
            == (entry.device, entry.inode, entry.size, entry.mtime_ns),
            "E_SOURCE_RACE",
            f"source entry changed: {label}",
        )
        target_fd = os.open(destination, target_flags, 0o600)
        remaining = before.st_size
        digest = hashlib.sha256()
        while remaining:
            chunk = os.read(source_fd, min(1024 * 1024, remaining))
            require(bool(chunk), "E_SOURCE_RACE", f"short source read: {label}")
            digest.update(chunk)
            view = memoryview(chunk)
            while view:
                written = os.write(target_fd, view)
                require(written > 0, "E_COPY", f"short target write: {label}")
                view = view[written:]
            remaining -= len(chunk)
        require(os.read(source_fd, 1) == b"", "E_SOURCE_RACE", f"source grew: {label}")
        after = os.fstat(source_fd)
        require(
            (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
            == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
            and digest.hexdigest() == entry.sha256,
            "E_SOURCE_RACE",
            f"source changed while copied: {label}",
        )
        os.fsync(target_fd)
    except FileExistsError:
        reject("E_TARGET_EXISTS", f"materialization target appeared: {destination}")
    except OSError as exc:
        reject("E_COPY", f"cannot copy {label}: {exc}")
    finally:
        if target_fd >= 0:
            os.close(target_fd)
        if source_fd >= 0:
            os.close(source_fd)
    os.chmod(destination, entry.mode & 0o7777, follow_symlinks=False)
    os.utime(destination, ns=(entry.mtime_ns, entry.mtime_ns), follow_symlinks=False)


def link_or_copy(source: Path, destination: Path, entry: Entry, label: str) -> str:
    verify_entry_unchanged(source, entry, label)
    fallback_errors = {
        errno.EXDEV,
        errno.EPERM,
        errno.EACCES,
        errno.EMLINK,
        getattr(errno, "EOPNOTSUPP", errno.EPERM),
        getattr(errno, "ENOTSUP", errno.EPERM),
    }
    try:
        os.link(source, destination, follow_symlinks=False)
        method = "hardlink"
    except OSError as exc:
        if exc.errno not in fallback_errors:
            if exc.errno == errno.EEXIST:
                reject("E_TARGET_EXISTS", f"materialization target appeared: {destination}")
            reject("E_LINK", f"cannot hard-link {label}: {exc}")
        copy_new_file(source, destination, entry, label)
        method = "copy"
    raw = secure_bytes(destination, f"materialized {label}")
    observed = os.lstat(destination)
    require(
        observed.st_size == entry.size
        and observed.st_mtime_ns == entry.mtime_ns
        and sha256_bytes(raw) == entry.sha256,
        "E_COPY",
        f"materialized entry differs: {label}",
    )
    return method


def materialize_tree(
    source: Path,
    destination: Path,
    tree: TreeSnapshot,
    label: str,
) -> dict[str, Any]:
    require(not lexists(destination), "E_TARGET_EXISTS", f"target exists: {destination}")
    source_root = real_directory(source, f"source {label}")
    source_root_stat = os.lstat(source_root)
    try:
        destination.mkdir(mode=source_root_stat.st_mode & 0o7777)
    except FileExistsError:
        reject("E_TARGET_EXISTS", f"target appeared: {destination}")
    directories = sorted(
        (entry for entry in tree.entries.values() if entry.kind == "directory"),
        key=lambda entry: (entry.path.count("/"), entry.path.encode("utf-8")),
    )
    for entry in directories:
        target = destination / PurePosixPath(entry.path)
        try:
            target.mkdir(mode=entry.mode & 0o7777)
        except FileExistsError:
            reject("E_TARGET_EXISTS", f"directory target appeared: {target}")
    hardlinks = 0
    copies = 0
    files = sorted(
        (entry for entry in tree.entries.values() if entry.kind == "file"),
        key=lambda entry: entry.path.encode("utf-8"),
    )
    for entry in files:
        source_file = source_root / PurePosixPath(entry.path)
        target_file = destination / PurePosixPath(entry.path)
        method = link_or_copy(source_file, target_file, entry, f"{label}/{entry.path}")
        hardlinks += method == "hardlink"
        copies += method == "copy"
    for entry in sorted(directories, key=lambda item: item.path.count("/"), reverse=True):
        target = destination / PurePosixPath(entry.path)
        os.chmod(target, entry.mode & 0o7777, follow_symlinks=False)
        os.utime(target, ns=(entry.mtime_ns, entry.mtime_ns), follow_symlinks=False)
    os.chmod(destination, source_root_stat.st_mode & 0o7777, follow_symlinks=False)
    os.utime(
        destination,
        ns=(source_root_stat.st_mtime_ns, source_root_stat.st_mtime_ns),
        follow_symlinks=False,
    )
    observed = scan_tree(destination, f"materialized {label}")
    require(
        observed.sha256 == tree.sha256
        and observed.files == tree.files
        and observed.directories == tree.directories
        and observed.bytes == tree.bytes,
        "E_MATERIALIZED_TREE",
        f"materialized tree differs: {label}",
    )
    return {
        "bytes": tree.bytes,
        "copies": copies,
        "directories": tree.directories + 1,
        "files": tree.files,
        "hardlinks": hardlinks,
        "sha256": tree.sha256,
        "status": "passed",
    }


def subprocess_environment() -> dict[str, str]:
    environment = dict(os.environ)
    environment.update(
        {
            "LANG": "C.UTF-8",
            "LC_ALL": "C.UTF-8",
            "PYTHONDONTWRITEBYTECODE": "1",
            "TZ": "UTC",
        }
    )
    return environment


def run_verifier(
    label: str,
    command: Sequence[str],
    repo: Path,
    tool_binding: dict[str, Any],
    *,
    timeout: int = 900,
) -> tuple[dict[str, Any], bytes]:
    require(command and absolute_path(Path(command[0])) == Path(command[0]), "E_SUBPROCESS", f"{label} interpreter is not absolute")
    require(len(command) > 1 and Path(command[1]).is_absolute(), "E_SUBPROCESS", f"{label} tool is not absolute")
    try:
        process = subprocess.run(
            list(command),
            cwd=repo,
            env=subprocess_environment(),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as exc:
        reject("E_SUBPROCESS_TIMEOUT", f"{label} timed out after {timeout}s: {exc}")
    except OSError as exc:
        reject("E_SUBPROCESS", f"cannot run {label}: {exc}")
    receipt = {
        "exit_code": process.returncode,
        "stderr_sha256": sha256_bytes(process.stderr),
        "stderr_size": len(process.stderr),
        "stdout_sha256": sha256_bytes(process.stdout),
        "stdout_size": len(process.stdout),
        "status": "passed" if process.returncode == 0 else "failed",
        "tool": tool_binding,
    }
    require(
        process.returncode == 0,
        "E_SUBPROCESS",
        f"{label} exited {process.returncode}; stderr_sha256={receipt['stderr_sha256']}",
    )
    return receipt, process.stdout


def atomic_create_json(path: Path, value: dict[str, Any]) -> None:
    payload = canonical_json(value)
    require(not lexists(path), "E_OUTPUT_EXISTS", f"output already exists: {path}")
    descriptor = -1
    temporary: Path | None = None
    try:
        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{path.name}.", suffix=".tmp", dir=path.parent
        )
        temporary = Path(temporary_name)
        os.fchmod(descriptor, 0o644)
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            descriptor = -1
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        try:
            os.link(temporary, path, follow_symlinks=False)
        except FileExistsError:
            reject("E_OUTPUT_EXISTS", f"output appeared before publication: {path}")
        temporary.unlink()
        temporary = None
        directory_fd = os.open(
            path.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
        )
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if temporary is not None:
            try:
                temporary.unlink()
            except FileNotFoundError:
                pass


def verify(args: argparse.Namespace) -> dict[str, Any]:
    repo = real_directory(args.repo_root, "repository root")
    require(real_directory(repo / ".git", "Git metadata") == repo / ".git", "E_GIT", "repository metadata differs")
    extracted = real_directory(args.root, "extracted release root")
    output = output_path(args.output, extracted)
    require(
        extracted != repo and extracted not in repo.parents,
        "E_PATH",
        "extracted root must not contain or equal the repository checkout",
    )

    full_snapshot = scan_tree(extracted, "extracted release root")
    validate_layout(full_snapshot)
    context, context_binding = validate_context(extracted)
    sensitive, sensitive_binding = validate_sensitive(extracted)
    runtime_source, runtime_source_binding = validate_runtime_source(
        extracted, context, sensitive
    )
    modules, freeze_binding = load_frozen_modules(repo)
    step4_summary, step4_summary_binding = validate_step4_preflight(extracted, context)
    fixture_run_id, fixture_binding = validate_fixture(extracted, context)
    package_manifest, class_binding = validate_package_and_classes(
        extracted, full_snapshot, context, modules
    )

    current_head = git_head(repo)
    require(current_head == context["git_head"], "E_GIT", "checkout HEAD differs from release context")

    source_tool, source_tool_binding = validate_tool(
        repo, SOURCE_TOOL_RELATIVE, "official source-hash tool"
    )
    coverage_tool, coverage_tool_binding = validate_tool(
        repo, COVERAGE_TOOL_RELATIVE, "official Step 4 artifact verifier"
    )
    database_tool, database_tool_binding = validate_tool(
        repo, DATABASE_TOOL_RELATIVE, "official database matrix verifier"
    )
    database_contract, database_contract_binding = validate_tool(
        repo, DATABASE_CONTRACT_RELATIVE, "official database matrix contract"
    )
    unit_fixture_tool, unit_fixture_tool_binding = validate_tool(
        repo, UNIT_FIXTURE_TOOL_RELATIVE, "official Unit MySQL fixture verifier"
    )
    package_tool, package_tool_binding = validate_tool(
        repo, PACKAGE_TOOL_RELATIVE, "official release package verifier"
    )
    artifact_tool, artifact_tool_binding = validate_tool(
        repo, ARTIFACT_TOOL_RELATIVE, "official release artifact verifier"
    )
    artifact_contract, artifact_contract_binding = validate_tool(
        repo, ARTIFACT_CONTRACT_RELATIVE, "official release artifact contract"
    )
    require(
        artifact_contract_binding["sha256"] == sensitive["contract_sha256"],
        "E_SENSITIVE",
        "recorded sensitive scan contract differs from checkout",
    )
    artifact_contract_value = parse_json_bytes(
        secure_bytes(
            artifact_contract,
            "official release artifact contract",
            1024 * 1024,
        ),
        "official release artifact contract",
    )
    policy = artifact_contract_value.get("sensitive_text_policy")
    require(type(policy) is dict, "E_SENSITIVE", "artifact sensitive policy is absent")
    require(
        sha256_bytes(canonical_json(policy)) == sensitive["policy_sha256"],
        "E_SENSITIVE",
        "recorded sensitive scan policy differs from checkout",
    )
    try:
        python = Path(sys.executable).resolve(strict=True)
    except OSError as exc:
        reject("E_SUBPROCESS", f"cannot resolve Python interpreter: {exc}")
    regular_file(python, "Python interpreter")

    artifact_manifests = artifact_contract_value.get("manifests")
    require(
        type(artifact_manifests) is dict,
        "E_ARTIFACT_ROOT",
        "artifact manifest contract is absent",
    )
    root_manifest_relative = safe_relative(
        artifact_manifests.get("root_manifest"),
        "artifact root manifest path",
    )
    root_manifest_entry = full_snapshot.entries.get(root_manifest_relative)
    require(
        root_manifest_entry is not None and root_manifest_entry.kind == "file",
        "E_ARTIFACT_ROOT",
        "artifact root manifest is absent from the extracted release",
    )
    root_manifest_raw = secure_bytes(
        extracted / PurePosixPath(root_manifest_relative),
        "artifact root manifest",
        32 * 1024 * 1024,
    )
    root_manifest_sha256 = sha256_bytes(root_manifest_raw)
    require(
        root_manifest_entry.sha256 == root_manifest_sha256
        and root_manifest_entry.size == len(root_manifest_raw),
        "E_ARTIFACT_ROOT",
        "artifact root manifest changed after the extracted-tree snapshot",
    )
    package_jar_entry = full_snapshot.entries.get("package/app.jar")
    require(
        package_jar_entry is not None and package_jar_entry.kind == "file",
        "E_ARTIFACT_ROOT",
        "release package JAR is absent",
    )
    artifact_root_receipt, artifact_root_stdout = run_verifier(
        "release artifact root manifest",
        [
            os.fspath(python),
            os.fspath(artifact_tool),
            "verify-root",
            "--root",
            os.fspath(extracted),
            "--root-manifest-sha256",
            root_manifest_sha256,
        ],
        repo,
        artifact_tool_binding,
    )
    artifact_root_result = parse_json_bytes(
        artifact_root_stdout, "release artifact root verify result"
    )
    require(
        artifact_root_result.get("command") == "verify-root"
        and artifact_root_result.get("status") == "passed"
        and artifact_root_result.get("root_manifest_sha256")
        == root_manifest_sha256
        and artifact_root_result.get("jar_sha256") == package_jar_entry.sha256,
        "E_ARTIFACT_ROOT",
        "release artifact root verifier JSON differs from the extracted package",
    )
    artifact_root_receipt["contract"] = artifact_contract_binding
    artifact_root_receipt["root_manifest"] = {
        "path": root_manifest_relative,
        "sha256": root_manifest_sha256,
        "size": len(root_manifest_raw),
    }
    artifact_root_receipt["result"] = artifact_root_result

    runtime_source_receipt, runtime_source_stdout = run_verifier(
        "runtime source closure",
        [
            os.fspath(python),
            os.fspath(artifact_tool),
            "scan-runtime-source",
            "--repo-root",
            os.fspath(repo),
        ],
        repo,
        artifact_tool_binding,
    )
    runtime_source_result = parse_json_bytes(
        runtime_source_stdout, "runtime source closure replay result"
    )
    require(
        runtime_source_result == runtime_source,
        "E_RUNTIME_SOURCE",
        "runtime source closure differs from the recorded release receipt",
    )
    runtime_source_receipt["contract"] = artifact_contract_binding
    runtime_source_receipt["result"] = runtime_source_result

    source_receipt, source_stdout = run_verifier(
        "checkout source seal",
        [os.fspath(python), os.fspath(source_tool), "source-hash", "--repo-root", os.fspath(repo)],
        repo,
        source_tool_binding,
    )
    source_result = parse_json_bytes(source_stdout, "checkout source-hash result")
    require(
        source_result.get("command") == "source-hash"
        and source_result.get("status") == "passed"
        and source_result.get("git_head") == context["git_head"]
        and source_result.get("sha256") == context["source_sha256"],
        "E_SOURCE",
        "checkout source seal differs from release context",
    )
    source_receipt["result"] = source_result

    sensitive_replay_receipt, sensitive_replay_stdout = run_verifier(
        "release sensitive material replay",
        [
            os.fspath(python),
            os.fspath(artifact_tool),
            "scan-root",
            "--root",
            os.fspath(extracted),
            "--allow-metadata",
        ],
        repo,
        artifact_tool_binding,
    )
    sensitive_replay_result = parse_json_bytes(
        sensitive_replay_stdout, "release sensitive material replay result"
    )
    require(
        sensitive_replay_result.get("command") == "scan-root"
        and sensitive_replay_result.get("kind") == "v934-release-sensitive-scan"
        and sensitive_replay_result.get("schema_version") == 2
        and sensitive_replay_result.get("metadata_allowed") is True
        and sensitive_replay_result.get("contract_sha256")
        == sensitive["contract_sha256"]
        and sensitive_replay_result.get("policy_sha256")
        == sensitive["policy_sha256"]
        and sensitive_replay_result.get("pattern_count") == 8
        and sensitive_replay_result.get("text_extension_count") == 46
        and sensitive_replay_result.get("archive_extension_count") == 2
        and sensitive_replay_result.get("status") == "passed",
        "E_SENSITIVE",
        "release sensitive material replay differs from recorded policy",
    )
    sensitive_replay_receipt["contract"] = artifact_contract_binding
    sensitive_replay_receipt["result"] = sensitive_replay_result

    run_id = context["run_id"]
    destinations = {
        label: repo / parent / run_id for label, parent in EVIDENCE_TARGETS.items()
    }
    destinations["unit-database"] = (
        repo / Path("target/v934-step3-database-matrix/runs") / fixture_run_id
    )
    class_destinations = {
        module: repo / PurePosixPath(module) / "target/classes" for module in modules
    }
    all_destinations = list(destinations.values()) + list(class_destinations.values())
    require(len(set(all_destinations)) == len(all_destinations), "E_TARGET", "target paths collide")
    for destination in all_destinations:
        require(not lexists(destination), "E_TARGET_EXISTS", f"canonical target exists: {destination}")
        validate_existing_ancestor_chain(repo, destination.parent)
        require(
            destination != extracted and extracted not in destination.parents,
            "E_TARGET",
            f"canonical target overlaps extracted root: {destination}",
        )
        require(
            output != destination and destination not in output.parents,
            "E_OUTPUT",
            f"output is inside materialization target: {destination}",
        )

    evidence_receipts: dict[str, Any] = {}
    for label in EVIDENCE_LABELS:
        parent = destinations[label].parent
        relative_parent = parent.relative_to(repo)
        ensure_directory_chain(repo, relative_parent)
        source = extracted / "evidence" / label
        tree = subtree_snapshot(full_snapshot, f"evidence/{label}")
        evidence_receipts[label] = {
            "destination": destinations[label].relative_to(repo).as_posix(),
            **materialize_tree(source, destinations[label], tree, f"evidence/{label}"),
        }

    class_totals = {
        "bytes": 0,
        "copies": 0,
        "directories": 0,
        "files": 0,
        "hardlinks": 0,
    }
    class_module_receipts: dict[str, Any] = {}
    for module in modules:
        destination = class_destinations[module]
        target_parent = destination.parent
        if lexists(target_parent):
            real_directory(target_parent, f"module target parent {module}")
        else:
            target_parent.mkdir(mode=0o755)
            real_directory(target_parent, f"module target parent {module}")
        source = extracted / "tested-classes" / PurePosixPath(module) / "target/classes"
        tree = subtree_snapshot(
            full_snapshot, f"tested-classes/{module}/target/classes"
        )
        require(tree.files > 0, "E_CLASS_MISSING", f"empty tested class tree: {module}")
        row = materialize_tree(source, destination, tree, f"tested-classes/{module}")
        class_module_receipts[module] = row
        for key in class_totals:
            class_totals[key] += int(row[key])

    canonical_step4 = destinations["step4"]
    coverage_receipt, _ = run_verifier(
        "Step 4 release final",
        [
            os.fspath(python),
            os.fspath(coverage_tool),
            "verify-artifact",
            "--mode",
            "release",
            "--repo-root",
            os.fspath(repo),
            "--artifact",
            os.fspath(canonical_step4 / "final-manifest.json"),
            "--run-status",
            os.fspath(canonical_step4 / "run-status.env"),
        ],
        repo,
        coverage_tool_binding,
    )

    canonical_database = destinations["database"]
    database_receipt, _ = run_verifier(
        "database matrix final",
        [
            os.fspath(python),
            os.fspath(database_tool),
            "verify-final",
            "--outer-marker",
            os.fspath(canonical_database / "run-context.json"),
            "--manifest",
            os.fspath(canonical_database / "final/report-manifest.json"),
        ],
        repo,
        database_tool_binding,
    )
    database_receipt["contract"] = database_contract_binding

    fixture_manifest = destinations["unit"] / "mysql57-fixture-manifest.json"
    fixture_receipt, _ = run_verifier(
        "Unit MySQL 5.7 recorded fixture",
        [
            os.fspath(python),
            os.fspath(unit_fixture_tool),
            "verify-recorded",
            "--repo-root",
            os.fspath(repo),
            "--run-id",
            run_id,
            "--manifest",
            os.fspath(fixture_manifest),
        ],
        repo,
        unit_fixture_tool_binding,
    )

    package_root = extracted / "package"
    package_receipt, package_stdout = run_verifier(
        "release package",
        [
            os.fspath(python),
            os.fspath(package_tool),
            "verify",
            "--repo-root",
            os.fspath(repo),
            "--manifest",
            os.fspath(package_root / "package-manifest.json"),
            "--jar",
            os.fspath(package_root / "app.jar"),
        ],
        repo,
        package_tool_binding,
    )
    package_result = parse_json_bytes(package_stdout, "release package verify result")
    require(
        package_result.get("command") == "verify"
        and package_result.get("status") == "passed"
        and package_result.get("run_id") == run_id
        and package_result.get("git_head") == context["git_head"],
        "E_PACKAGE_VERIFY",
        "release package verifier JSON differs",
    )
    package_receipt["result"] = package_result

    final_snapshot = scan_tree(extracted, "extracted release root after replay")
    require(
        final_snapshot.sha256 == full_snapshot.sha256
        and final_snapshot.files == full_snapshot.files
        and final_snapshot.directories == full_snapshot.directories
        and final_snapshot.bytes == full_snapshot.bytes,
        "E_SOURCE_DRIFT",
        "extracted release root changed during replay",
    )

    package_hashes = {
        name: {
            "sha256": full_snapshot.entries[f"package/{name}"].sha256,
            "size": full_snapshot.entries[f"package/{name}"].size,
        }
        for name in sorted(PACKAGE_FILES)
    }
    receipt: dict[str, Any] = {
        "schema_version": 1,
        "kind": "v934-portable-release-replay",
        "status": "passed",
        "run_id": run_id,
        "release_mode": context["mode"],
        "git_head": context["git_head"],
        "source_sha256": context["source_sha256"],
        "fixture_run_id": fixture_run_id,
        "input": {
            "bytes": full_snapshot.bytes,
            "context": context_binding,
            "directories": full_snapshot.directories + 1,
            "files": full_snapshot.files,
            "root": os.fspath(extracted),
            "runtime_source": {
                **runtime_source_binding,
                "files": runtime_source["files"],
                "module_count": runtime_source["module_count"],
                "set_sha256": runtime_source["set_sha256"],
                "status": runtime_source["status"],
            },
            "sensitive_scan": {**sensitive_binding, "status": sensitive["status"]},
            "step4_summary": step4_summary_binding,
            "tree_sha256": full_snapshot.sha256,
            "status": "unchanged",
        },
        "contract_freeze": freeze_binding,
        "fixture_manifest": fixture_binding,
        "package": {
            "files": package_hashes,
            "manifest_kind": package_manifest["kind"],
            "tested_classes": class_binding,
        },
        "materialized": {
            "evidence": evidence_receipts,
            "tested_classes": {
                **class_totals,
                "module_count": len(modules),
                "modules": class_module_receipts,
                "sha256": class_binding["sha256"],
                "status": "passed",
            },
        },
        "subprocesses": {
            "artifact_root": artifact_root_receipt,
            "checkout_source": source_receipt,
            "runtime_source": runtime_source_receipt,
            "sensitive_material": sensitive_replay_receipt,
            "step4_release": coverage_receipt,
            "database_final": database_receipt,
            "unit_mysql57_fixture": fixture_receipt,
            "package_verify": package_receipt,
        },
        "step4": {
            "mode": step4_summary["mode"],
            "reports": int(step4_summary["required_reports"]),
            "structural_reports": int(step4_summary["required_structural_reports"]),
            "testcase_nodes": int(step4_summary["required_testcase_nodes"]),
            "status": step4_summary["status"],
        },
    }
    atomic_create_json(output, receipt)
    return {
        "command": "verify",
        "output": os.fspath(output),
        "output_sha256": sha256_bytes(canonical_json(receipt)),
        "run_id": run_id,
        "status": "passed",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    verify_parser = commands.add_parser(
        "verify", help="materialize and replay one extracted release candidate"
    )
    verify_parser.add_argument("--repo-root", type=Path, required=True)
    verify_parser.add_argument("--root", type=Path, required=True)
    verify_parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = verify(args)
    except ReplayError as exc:
        print(
            json.dumps(
                {
                    "command": getattr(args, "command", "unknown"),
                    "error": exc.code,
                    "message": exc.message,
                    "status": "failed",
                },
                ensure_ascii=False,
                separators=(",", ":"),
                sort_keys=True,
            ),
            file=sys.stderr,
        )
        return 1
    except (OSError, ValueError) as exc:
        print(
            json.dumps(
                {
                    "command": getattr(args, "command", "unknown"),
                    "error": "E_RUNTIME",
                    "message": str(exc) or exc.__class__.__name__,
                    "status": "failed",
                },
                ensure_ascii=False,
                separators=(",", ":"),
                sort_keys=True,
            ),
            file=sys.stderr,
        )
        return 1
    except KeyboardInterrupt:
        print(
            json.dumps(
                {
                    "command": getattr(args, "command", "unknown"),
                    "error": "E_SIGNAL",
                    "message": "interrupted",
                    "status": "failed",
                },
                separators=(",", ":"),
                sort_keys=True,
            ),
            file=sys.stderr,
        )
        return 130
    print(json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
