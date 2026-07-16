#!/usr/bin/env python3
"""Fail-closed JaCoCo XML observation and negative probes for 9.3.4 Step 4."""

from __future__ import annotations

import argparse
import copy
import csv
from decimal import Decimal, InvalidOperation
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
import xml.etree.ElementTree as ET
from typing import Any, Callable, Iterable, Sequence


JACOCO_DOCTYPE = b'<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">'
MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"
MAVEN_NS = {"m": MAVEN_NAMESPACE}
COUNTER_TYPES = (
    "INSTRUCTION",
    "BRANCH",
    "LINE",
    "COMPLEXITY",
    "METHOD",
    "CLASS",
)
SUMMABLE_METHOD_COUNTERS = ("INSTRUCTION", "BRANCH", "COMPLEXITY", "METHOD")
EXPECTED_STEP1_POLICY_SHA256 = (
    "45058f63b71558e4660f60e0cfda9a8a490fa8f96b532c6656c3d726eaad44fb"
)
EXPECTED_STEP1_FREEZE_SHA256 = (
    "ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876"
)
EXPECTED_LEDGER_SHA256 = (
    "10ddf85daa0426d530bec3ccd9bb1a10446aa426d920c6c5c433163455552711"
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
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
UNSIGNED_PATTERN = re.compile(r"0|[1-9][0-9]*")
SESSION_PREFIX_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*")


class CoverageXmlError(RuntimeError):
    """A deterministic validation failure carrying a stable evidence code."""

    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message


def reject(code: str, message: str) -> None:
    raise CoverageXmlError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def reject_json_constant(value: str) -> None:
    reject("E_JSON", f"non-finite JSON number is forbidden: {value}")


def unique_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            reject("E_JSON", f"duplicate JSON key: {key!r}")
        result[key] = value
    return result


def regular_file(path: Path, missing_code: str, *, nonempty: bool = True) -> os.stat_result:
    try:
        file_stat = path.lstat()
    except FileNotFoundError:
        reject(missing_code, f"missing file: {path}")
    except OSError as exc:
        reject(missing_code, f"cannot inspect file {path}: {exc.__class__.__name__}")
    if stat.S_ISLNK(file_stat.st_mode):
        reject("E_FILE_SYMLINK", f"symlink evidence is forbidden: {path}")
    if not stat.S_ISREG(file_stat.st_mode):
        reject("E_FILE_TYPE", f"evidence is not a regular file: {path}")
    if nonempty and file_stat.st_size <= 0:
        reject("E_FILE_EMPTY", f"evidence is empty: {path}")
    return file_stat


def real_directory(path: Path, code: str) -> None:
    try:
        file_stat = path.lstat()
    except FileNotFoundError:
        reject(code, f"missing directory: {path}")
    except OSError as exc:
        reject(code, f"cannot inspect directory {path}: {exc.__class__.__name__}")
    if stat.S_ISLNK(file_stat.st_mode) or not stat.S_ISDIR(file_stat.st_mode):
        reject(code, f"expected a real non-symlink directory: {path}")


def sha256_file(path: Path, code: str = "E_FILE") -> str:
    regular_file(path, code)
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        reject(code, f"cannot hash {path}: {exc.__class__.__name__}")
    return digest.hexdigest()


def load_json(path: Path, code: str) -> dict[str, Any]:
    regular_file(path, code)
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=unique_json_object,
            parse_constant=reject_json_constant,
        )
    except CoverageXmlError:
        raise
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as exc:
        reject(code, f"invalid UTF-8 JSON {path}: {exc.__class__.__name__}")
    require(type(value) is dict, code, f"JSON root must be an object: {path}")
    return value


def exact_keys(value: Any, expected: Iterable[str], code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict, code, f"{label} must be an object")
    expected_set = set(expected)
    require(
        set(value) == expected_set,
        code,
        f"{label} keys differ: expected={sorted(expected_set)} actual={sorted(value)}",
    )
    return value


def unsigned(value: Any, code: str, label: str, *, positive: bool = False) -> int:
    require(
        isinstance(value, str) and UNSIGNED_PATTERN.fullmatch(value) is not None,
        code,
        f"{label} must be a canonical unsigned integer",
    )
    number = int(value)
    require(not positive or number > 0, code, f"{label} must be positive")
    return number


def json_integer(value: Any, code: str, label: str, *, positive: bool = False) -> int:
    require(type(value) is int and value >= 0, code, f"{label} must be a non-negative integer")
    require(not positive or value > 0, code, f"{label} must be positive")
    return value


def json_sha256(value: Any, code: str, label: str) -> str:
    require(
        isinstance(value, str) and SHA256_PATTERN.fullmatch(value) is not None,
        code,
        f"{label} must be a lowercase SHA-256",
    )
    return value


def decimal_ratio(value: Any, code: str, label: str) -> Decimal:
    require(type(value) in (int, float), code, f"{label} must be a JSON number")
    if type(value) is float:
        require(math.isfinite(value), code, f"{label} must be finite")
    try:
        result = Decimal(str(value))
    except InvalidOperation:
        reject(code, f"{label} is not a decimal ratio")
    require(Decimal("0") <= result <= Decimal("1"), code, f"{label} must be in [0, 1]")
    return result


def display_path(repo_root: Path, path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(repo_root).as_posix()
    except ValueError:
        return str(resolved)


def atomic_json(path: Path, value: dict[str, Any]) -> None:
    if path.exists() or path.is_symlink():
        reject("E_OUTPUT_EXISTS", f"refusing to overwrite output: {path}")
    parent = path.parent
    parent.mkdir(parents=True, exist_ok=True)
    real_directory(parent, "E_OUTPUT_DIR")
    temporary = parent / f".{path.name}.{os.getpid()}.tmp"
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor: int | None = None
    published = False
    try:
        descriptor = os.open(temporary, flags, 0o600)
        payload = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            descriptor = None
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, path, follow_symlinks=False)
        published = True
        temporary.unlink()
        directory_flags = os.O_RDONLY
        if hasattr(os, "O_DIRECTORY"):
            directory_flags |= os.O_DIRECTORY
        if hasattr(os, "O_NOFOLLOW"):
            directory_flags |= os.O_NOFOLLOW
        directory_descriptor = os.open(parent, directory_flags)
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
    except OSError as exc:
        if descriptor is not None:
            os.close(descriptor)
        temporary.unlink(missing_ok=True)
        if published:
            path.unlink(missing_ok=True)
        reject("E_OUTPUT", f"cannot publish output {path}: {exc.__class__.__name__}")


def load_ledger(repo_root: Path) -> list[dict[str, str]]:
    ledger_path = repo_root / "scripts/v934/step4/coverage-exec-ledger.tsv"
    require(
        sha256_file(ledger_path, "E_LEDGER") == EXPECTED_LEDGER_SHA256,
        "E_LEDGER_SHA",
        "coverage exec ledger differs from its frozen Step 4 hash",
    )
    try:
        with ledger_path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            require(tuple(reader.fieldnames or ()) == LEDGER_HEADER, "E_LEDGER", "ledger header differs")
            rows = list(reader)
    except (OSError, UnicodeError, csv.Error) as exc:
        reject("E_LEDGER", f"cannot parse coverage ledger: {exc.__class__.__name__}")
    require(len(rows) == 23, "E_LEDGER", f"expected exact 23 ledger rows, found {len(rows)}")
    names: set[str] = set()
    total_sessions = 0
    for number, row in enumerate(rows, 2):
        name = row["exec_file"]
        require(
            name and name == Path(name).name and name.endswith(".exec"),
            "E_LEDGER",
            f"unsafe exec identity at row {number}",
        )
        require(name not in names, "E_LEDGER", f"duplicate exec identity: {name}")
        names.add(name)
        require(row["required"] == "true", "E_LEDGER", f"non-required exec row: {name}")
        require(
            SESSION_PREFIX_PATTERN.fullmatch(row["variant_key"]) is not None,
            "E_LEDGER",
            f"unsafe variant key: {row['variant_key']!r}",
        )
        owners = row["expected_session_owners"].split(",")
        require(
            owners and len(owners) == len(set(owners)) and all(SESSION_PREFIX_PATTERN.fullmatch(owner) for owner in owners),
            "E_LEDGER",
            f"invalid session owners for {name}",
        )
        expected_count = unsigned(row["expected_session_count"], "E_LEDGER", f"{name} session count", positive=True)
        require(expected_count == len(owners), "E_LEDGER", f"session owner count differs for {name}")
        total_sessions += expected_count
    require(total_sessions == 48, "E_LEDGER", f"expected 48 ledger sessions, found {total_sessions}")
    return rows


def load_frozen_modules(repo_root: Path) -> tuple[list[str], dict[str, str], str]:
    freeze_path = repo_root / "scripts/v934/contract-freeze.json"
    freeze_sha = sha256_file(freeze_path, "E_FREEZE")
    require(
        freeze_sha == EXPECTED_STEP1_FREEZE_SHA256,
        "E_FREEZE_SHA",
        "Step 1 contract freeze hash differs",
    )
    freeze = load_json(freeze_path, "E_FREEZE")
    reactor = freeze.get("reactor")
    require(type(reactor) is dict, "E_FREEZE", "Step 1 reactor freeze is missing")
    modules = reactor.get("modules")
    require(
        reactor.get("module_count") == 24
        and isinstance(modules, list)
        and len(modules) == 24
        and len(set(modules)) == 24
        and modules == sorted(modules),
        "E_FREEZE",
        "Step 1 reactor must contain exact 24 sorted unique modules",
    )
    artifact_to_module: dict[str, str] = {}
    for module in modules:
        require(isinstance(module, str) and module, "E_FREEZE", "invalid frozen module path")
        pure = PurePosixPath(module)
        require(
            not pure.is_absolute() and ".." not in pure.parts and "\\" not in module,
            "E_FREEZE",
            f"unsafe frozen module path: {module!r}",
        )
        pom_path = repo_root.joinpath(*pure.parts) / "pom.xml"
        regular_file(pom_path, "E_MODULE_POM")
        try:
            pom = ET.parse(pom_path).getroot()
        except (ET.ParseError, OSError) as exc:
            reject("E_MODULE_POM", f"invalid module POM {pom_path}: {exc.__class__.__name__}")
        require(pom.tag == f"{{{MAVEN_NAMESPACE}}}project", "E_MODULE_POM", f"invalid POM root: {module}")
        artifacts = pom.findall("m:artifactId", MAVEN_NS)
        require(len(artifacts) == 1, "E_MODULE_POM", f"module must have one direct artifactId: {module}")
        artifact = (artifacts[0].text or "").strip()
        require(artifact and artifact == PurePosixPath(module).name, "E_MODULE_POM", f"unexpected artifactId for {module}")
        require(artifact not in artifact_to_module, "E_MODULE_POM", f"duplicate artifactId: {artifact}")
        artifact_to_module[artifact] = module
    return modules, artifact_to_module, freeze_sha


def load_thresholds(repo_root: Path) -> tuple[dict[str, Any], dict[str, Any], dict[str, str]]:
    step1_path = repo_root / "scripts/v934/coverage-thresholds.json"
    step4_path = repo_root / "scripts/v934/step4/coverage-thresholds.json"
    step1_sha = sha256_file(step1_path, "E_THRESHOLD")
    require(step1_sha == EXPECTED_STEP1_POLICY_SHA256, "E_THRESHOLD_SHA", "immutable Step 1 coverage policy differs")
    step1 = load_json(step1_path, "E_THRESHOLD")
    step4 = load_json(step4_path, "E_THRESHOLD")
    require(step1.get("schema_version") == 1, "E_THRESHOLD", "Step 1 coverage schema differs")
    require(
        step1.get("status") == "step1-policy-frozen-observed-baseline-deferred-to-step4",
        "E_THRESHOLD",
        "Step 1 coverage status differs",
    )
    parent = step4.get("parent_policy")
    require(type(parent) is dict, "E_THRESHOLD", "Step 4 parent policy is missing")
    require(
        parent.get("path") == "scripts/v934/coverage-thresholds.json"
        and parent.get("sha256") == EXPECTED_STEP1_POLICY_SHA256
        and parent.get("immutable") is True,
        "E_THRESHOLD",
        "Step 4 threshold successor does not preserve the immutable Step 1 parent",
    )
    require(step4.get("schema_version") == 1, "E_THRESHOLD", "Step 4 coverage schema differs")
    require(step4.get("kind") == "v934-step4-coverage-threshold-successor", "E_THRESHOLD", "Step 4 threshold kind differs")
    require(step4.get("status") in ("diagnostic-pending", "confirmed"), "E_THRESHOLD", "unsupported Step 4 threshold status")
    step1_floor = step1.get("candidate_floor")
    step4_floor = step4.get("critical_candidate_floor")
    require(type(step1_floor) is dict and type(step4_floor) is dict, "E_THRESHOLD", "candidate floor is missing")
    require(set(step1_floor) == {"line", "branch"} and set(step4_floor) == {"line", "branch"}, "E_THRESHOLD", "candidate floor keys differ")
    for counter_name in ("line", "branch"):
        require(
            decimal_ratio(step1_floor[counter_name], "E_THRESHOLD", f"Step 1 {counter_name} floor")
            == decimal_ratio(step4_floor[counter_name], "E_THRESHOLD", f"Step 4 {counter_name} floor"),
            "E_THRESHOLD",
            f"Step 4 {counter_name} floor lowers or changes its Step 1 parent",
        )
    critical = step1.get("critical_classes")
    require(
        isinstance(critical, list) and len(critical) == 12,
        "E_THRESHOLD",
        "Step 1 policy must contain exact 12 critical classes",
    )
    identities: set[str] = set()
    for number, row in enumerate(critical, 1):
        require(type(row) is dict and set(row) == {"fqcn", "module"}, "E_THRESHOLD", f"critical row {number} differs")
        fqcn = row.get("fqcn")
        module = row.get("module")
        require(isinstance(fqcn, str) and fqcn and "/" not in fqcn, "E_THRESHOLD", f"invalid critical fqcn at row {number}")
        require(isinstance(module, str) and module, "E_THRESHOLD", f"invalid critical module at row {number}")
        require(fqcn not in identities, "E_THRESHOLD", f"duplicate critical class: {fqcn}")
        identities.add(fqcn)
    return step1, step4, {
        "step1_policy_sha256": step1_sha,
        "step4_successor_sha256": sha256_file(step4_path, "E_THRESHOLD"),
    }


def validate_toolchain_receipt(
    repo_root: Path,
    run_id: str,
    git_head: str,
) -> tuple[Path, str]:
    path = (
        repo_root
        / "target/v934-step4-coverage/runs"
        / run_id
        / "toolchain-receipt.json"
    )
    receipt = load_json(path, "E_TOOLCHAIN_RECEIPT")
    exact_keys(
        receipt,
        (
            "schema_version",
            "kind",
            "status",
            "run_id",
            "git_head",
            "tool_sha256",
            "step1_contract_freeze_sha256",
            "platform",
            "commands",
            "jdk",
            "maven",
            "plugin_realms",
            "test_classpath_asm_guard",
            "compiler_effective_contract",
        ),
        "E_TOOLCHAIN_RECEIPT",
        "toolchain receipt",
    )
    require(
        receipt["schema_version"] == 1
        and type(receipt["schema_version"]) is int
        and receipt["kind"] == "v934-step4-toolchain-receipt"
        and receipt["status"] == "verified"
        and receipt["run_id"] == run_id
        and receipt["git_head"] == git_head
        and receipt["tool_sha256"]
        == sha256_file(
            repo_root / "scripts/v934/step4/toolchain_receipt_tool.py",
            "E_TOOLCHAIN_RECEIPT",
        )
        and receipt["step1_contract_freeze_sha256"]
        == sha256_file(
            repo_root / "scripts/v934/contract-freeze.json",
            "E_TOOLCHAIN_RECEIPT",
        ),
        "E_TOOLCHAIN_RECEIPT",
        "toolchain receipt identity/tool binding differs",
    )
    return path, sha256_file(path, "E_TOOLCHAIN_RECEIPT")


def validate_manifest(
    repo_root: Path,
    manifest_path: Path,
    ledger: list[dict[str, str]],
    frozen_modules: list[str],
) -> tuple[dict[str, Any], list[str], dict[str, Any]]:
    manifest = load_json(manifest_path, "E_MANIFEST")
    exact_keys(
        manifest,
        (
            "schema_version",
            "kind",
            "run_id",
            "session_prefix",
            "not_before_ns",
            "run_context_sha256",
            "git_head",
            "source_sha256",
            "fresh_class_universe_sha256",
            "toolchain_receipt_sha256",
            "coverage_contract_sha256",
            "coverage_ledger_sha256",
            "jacoco",
            "exec_count",
            "session_count",
            "unique_execution_classes",
            "workspace_class_count",
            "module_class_counts",
            "workspace_class_tree_sha256",
            "exec_files",
            "status",
        ),
        "E_MANIFEST",
        "exec manifest",
    )
    require(manifest["schema_version"] == 1 and type(manifest["schema_version"]) is int, "E_MANIFEST", "exec manifest schema differs")
    require(manifest["kind"] == "v934-step4-exec-manifest", "E_MANIFEST", "exec manifest kind differs")
    require(manifest["status"] == "verified", "E_MANIFEST", "exec manifest is not verified")
    run_id = manifest["run_id"]
    session_prefix = manifest["session_prefix"]
    require(isinstance(run_id, str) and SESSION_PREFIX_PATTERN.fullmatch(run_id), "E_MANIFEST", "unsafe run id")
    require(isinstance(session_prefix, str) and SESSION_PREFIX_PATTERN.fullmatch(session_prefix), "E_MANIFEST", "unsafe session prefix")
    require(run_id == session_prefix, "E_MANIFEST", "run id and session prefix must be identical")
    json_integer(manifest["not_before_ns"], "E_MANIFEST", "not_before_ns", positive=True)
    json_sha256(manifest["run_context_sha256"], "E_MANIFEST", "run context SHA")
    require(
        isinstance(manifest["git_head"], str)
        and re.fullmatch(r"[0-9a-f]{40}", manifest["git_head"]) is not None,
        "E_MANIFEST",
        "invalid manifest Git HEAD",
    )
    json_sha256(manifest["source_sha256"], "E_MANIFEST", "source SHA")
    class_universe_sha = json_sha256(
        manifest["fresh_class_universe_sha256"],
        "E_MANIFEST",
        "fresh class-universe SHA",
    )
    class_universe_path = (
        repo_root
        / "target/v934-step4-coverage/runs"
        / run_id
        / "class-universe.json"
    )
    require(
        class_universe_sha == sha256_file(class_universe_path, "E_CLASS_UNIVERSE"),
        "E_MANIFEST_PROVENANCE",
        "exec manifest fresh class-universe hash differs",
    )
    _toolchain_path, toolchain_sha = validate_toolchain_receipt(
        repo_root,
        run_id,
        manifest["git_head"],
    )
    require(
        json_sha256(
            manifest["toolchain_receipt_sha256"],
            "E_MANIFEST",
            "toolchain receipt SHA",
        )
        == toolchain_sha,
        "E_MANIFEST_PROVENANCE",
        "exec manifest toolchain receipt hash differs",
    )
    require(manifest["exec_count"] == 23 and type(manifest["exec_count"]) is int, "E_MANIFEST", "exec count must be 23")
    require(manifest["session_count"] == 48 and type(manifest["session_count"]) is int, "E_MANIFEST", "session count must be 48")
    json_integer(manifest["unique_execution_classes"], "E_MANIFEST", "unique execution classes", positive=True)
    workspace_class_count = json_integer(manifest["workspace_class_count"], "E_MANIFEST", "workspace class count", positive=True)
    json_sha256(manifest["workspace_class_tree_sha256"], "E_MANIFEST", "workspace class tree SHA")

    contract_path = repo_root / "scripts/v934/step4/coverage-contract.json"
    contract_sha = sha256_file(contract_path, "E_CONTRACT")
    require(
        json_sha256(manifest["coverage_contract_sha256"], "E_MANIFEST", "coverage contract SHA") == contract_sha,
        "E_MANIFEST_PROVENANCE",
        "exec manifest coverage contract hash differs from the current contract",
    )
    require(
        json_sha256(manifest["coverage_ledger_sha256"], "E_MANIFEST", "coverage ledger SHA") == EXPECTED_LEDGER_SHA256,
        "E_MANIFEST_PROVENANCE",
        "exec manifest coverage ledger hash differs",
    )
    module_counts = manifest["module_class_counts"]
    require(type(module_counts) is dict and list(module_counts) == frozen_modules, "E_MANIFEST", "manifest module class tree differs from frozen reactor")
    normalized_module_counts: dict[str, int] = {}
    for module, count in module_counts.items():
        normalized_module_counts[module] = json_integer(count, "E_MANIFEST", f"class count for {module}", positive=True)
    require(sum(normalized_module_counts.values()) == workspace_class_count, "E_MANIFEST", "workspace class count does not equal module class counts")

    jacoco = exact_keys(
        manifest["jacoco"],
        (
            "version",
            "agent_jar_sha256",
            "core_jar_sha256",
            "inspector_source_sha256",
            "inspector_class_sha256",
        ),
        "E_MANIFEST",
        "exec manifest jacoco",
    )
    require(jacoco["version"] == "0.8.12", "E_MANIFEST", "unexpected JaCoCo version")
    for key in (
        "agent_jar_sha256",
        "core_jar_sha256",
        "inspector_source_sha256",
        "inspector_class_sha256",
    ):
        json_sha256(jacoco[key], "E_MANIFEST", f"jacoco {key}")

    exec_files = manifest["exec_files"]
    require(isinstance(exec_files, list) and len(exec_files) == 23, "E_MANIFEST", "manifest must contain exact 23 exec rows")
    expected_names = [row["exec_file"] for row in ledger]
    require([row.get("exec_file") if isinstance(row, dict) else None for row in exec_files] == expected_names, "E_MANIFEST", "manifest exec order/set differs from ledger")
    flattened_sessions: list[str] = []
    input_provenance_rows: list[dict[str, Any]] = []
    for ledger_row, exec_row in zip(ledger, exec_files):
        label = ledger_row["exec_file"]
        exact_keys(
            exec_row,
            (
                "exec_file",
                "runner",
                "lane",
                "variant_key",
                "sha256",
                "size",
                "mtime_ns",
                "sessions",
                "session_count",
                "execution_class_count",
                "covered_probe_count",
                "class_shape_sha256",
                "workspace_execution_class_count",
            ),
            "E_MANIFEST",
            f"exec manifest row {label}",
        )
        for key in ("runner", "lane", "variant_key"):
            require(exec_row[key] == ledger_row[key], "E_MANIFEST", f"{key} differs for {label}")
        exec_sha = json_sha256(exec_row["sha256"], "E_MANIFEST", f"{label} SHA")
        size = json_integer(exec_row["size"], "E_MANIFEST", f"{label} size", positive=True)
        json_integer(exec_row["mtime_ns"], "E_MANIFEST", f"{label} mtime", positive=True)
        execution_class_count = json_integer(exec_row["execution_class_count"], "E_MANIFEST", f"{label} class count", positive=True)
        json_integer(exec_row["covered_probe_count"], "E_MANIFEST", f"{label} covered probes", positive=True)
        workspace_execution_class_count = json_integer(exec_row["workspace_execution_class_count"], "E_MANIFEST", f"{label} workspace class count", positive=True)
        require(workspace_execution_class_count <= execution_class_count, "E_MANIFEST", f"workspace class count exceeds total for {label}")
        class_shape_sha = json_sha256(exec_row["class_shape_sha256"], "E_MANIFEST", f"{label} class shape SHA")
        sessions = exec_row["sessions"]
        require(isinstance(sessions, list) and all(isinstance(item, str) and item for item in sessions), "E_MANIFEST", f"invalid sessions for {label}")
        require(sessions == sorted(sessions) and len(sessions) == len(set(sessions)), "E_MANIFEST", f"sessions are not sorted unique for {label}")
        expected_sessions = sorted(
            f"{session_prefix}-{ledger_row['variant_key']}-{owner}"
            for owner in ledger_row["expected_session_owners"].split(",")
        )
        require(sessions == expected_sessions, "E_MANIFEST", f"session set differs for {label}")
        require(exec_row["session_count"] == len(sessions) and type(exec_row["session_count"]) is int, "E_MANIFEST", f"session count differs for {label}")
        flattened_sessions.extend(sessions)
        input_provenance_rows.append(
            {
                "exec_file": label,
                "sha256": exec_sha,
                "size": size,
                "class_shape_sha256": class_shape_sha,
            }
        )
    require(len(flattened_sessions) == 48 and len(set(flattened_sessions)) == 48, "E_MANIFEST", "manifest sessions are not exact 48 unique identities")
    provenance_sha = hashlib.sha256(
        "".join(
            f"{row['exec_file']}\t{row['sha256']}\t{row['size']}\t{row['class_shape_sha256']}\n"
            for row in input_provenance_rows
        ).encode("utf-8")
    ).hexdigest()
    return manifest, flattened_sessions, {
        "coverage_contract_sha256": contract_sha,
        "coverage_ledger_sha256": EXPECTED_LEDGER_SHA256,
        "fresh_class_universe_sha256": class_universe_sha,
        "toolchain_receipt_sha256": toolchain_sha,
        "workspace_class_tree_sha256": manifest["workspace_class_tree_sha256"],
        "input_exec_tree_sha256": provenance_sha,
        "input_exec_files": input_provenance_rows,
    }


def validate_aggregate_provenance(
    repo_root: Path,
    provenance_path: Path,
    aggregate_exec_path: Path,
    exec_manifest_path: Path,
    exec_manifest: dict[str, Any],
) -> dict[str, Any]:
    provenance = load_json(provenance_path, "E_AGGREGATE_PROVENANCE")
    exact_keys(
        provenance,
        (
            "schema_version",
            "kind",
            "run_id",
            "run_context_sha256",
            "git_head",
            "source_sha256",
            "toolchain_receipt_sha256",
            "exec_manifest_sha256",
            "coverage_contract_sha256",
            "coverage_ledger_sha256",
            "inspector_source_sha256",
            "inspector_class_sha256",
            "input_exec_count",
            "input_exec_files",
            "aggregate_exec",
            "merge_semantics",
            "status",
        ),
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance",
    )
    require(
        provenance["schema_version"] == 1
        and type(provenance["schema_version"]) is int
        and provenance["kind"] == "v934-step4-aggregate-exec-provenance"
        and provenance["status"] == "verified",
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance identity/status differs",
    )
    require(
        provenance["run_id"] == exec_manifest["run_id"],
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance run identity differs",
    )
    require(
        provenance["run_context_sha256"] == exec_manifest["run_context_sha256"]
        and provenance["git_head"] == exec_manifest["git_head"]
        and provenance["source_sha256"] == exec_manifest["source_sha256"],
        "E_AGGREGATE_PROVENANCE",
        "aggregate run/source provenance differs",
    )
    json_sha256(provenance["run_context_sha256"], "E_AGGREGATE_PROVENANCE", "run context SHA")
    require(
        isinstance(provenance["git_head"], str)
        and re.fullmatch(r"[0-9a-f]{40}", provenance["git_head"]) is not None,
        "E_AGGREGATE_PROVENANCE",
        "invalid aggregate Git HEAD",
    )
    json_sha256(provenance["source_sha256"], "E_AGGREGATE_PROVENANCE", "source SHA")
    require(
        json_sha256(
            provenance["toolchain_receipt_sha256"],
            "E_AGGREGATE_PROVENANCE",
            "toolchain receipt SHA",
        )
        == exec_manifest["toolchain_receipt_sha256"],
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance toolchain receipt binding differs",
    )
    require(
        json_sha256(provenance["exec_manifest_sha256"], "E_AGGREGATE_PROVENANCE", "exec manifest SHA")
        == sha256_file(exec_manifest_path, "E_MANIFEST"),
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance exec-manifest hash differs",
    )
    for key in (
        "coverage_contract_sha256",
        "coverage_ledger_sha256",
        "inspector_source_sha256",
        "inspector_class_sha256",
    ):
        json_sha256(provenance[key], "E_AGGREGATE_PROVENANCE", key)
    require(
        provenance["coverage_contract_sha256"] == exec_manifest["coverage_contract_sha256"]
        and provenance["coverage_ledger_sha256"] == exec_manifest["coverage_ledger_sha256"]
        and provenance["inspector_source_sha256"] == exec_manifest["jacoco"]["inspector_source_sha256"]
        and provenance["inspector_class_sha256"] == exec_manifest["jacoco"]["inspector_class_sha256"],
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance tool/contract binding differs",
    )
    require(
        provenance["input_exec_count"] == 23
        and type(provenance["input_exec_count"]) is int,
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance input count must be 23",
    )
    input_rows = provenance["input_exec_files"]
    require(
        isinstance(input_rows, list) and len(input_rows) == 23,
        "E_AGGREGATE_PROVENANCE",
        "aggregate provenance must contain exact 23 input rows",
    )
    expected_rows = exec_manifest["exec_files"]
    for number, (actual, expected) in enumerate(zip(input_rows, expected_rows), 1):
        exact_keys(actual, ("exec_file", "sha256", "size"), "E_AGGREGATE_PROVENANCE", f"aggregate input row {number}")
        require(
            actual["exec_file"] == expected["exec_file"]
            and actual["sha256"] == expected["sha256"]
            and actual["size"] == expected["size"],
            "E_AGGREGATE_PROVENANCE",
            f"aggregate input row differs from exec manifest: {expected['exec_file']}",
        )
    aggregate = exact_keys(
        provenance["aggregate_exec"],
        (
            "path",
            "sha256",
            "size",
            "session_count",
            "execution_class_count",
            "covered_probe_count",
        ),
        "E_AGGREGATE_PROVENANCE",
        "aggregate exec provenance",
    )
    expected_relative = display_path(repo_root, aggregate_exec_path)
    require(
        aggregate["path"] == expected_relative
        and json_sha256(aggregate["sha256"], "E_AGGREGATE_PROVENANCE", "aggregate exec SHA")
        == sha256_file(aggregate_exec_path, "E_AGGREGATE_EXEC")
        and aggregate["size"] == regular_file(aggregate_exec_path, "E_AGGREGATE_EXEC").st_size,
        "E_AGGREGATE_PROVENANCE",
        "aggregate exec path/hash/size differs from verified provenance",
    )
    require(
        aggregate["session_count"] == 48
        and type(aggregate["session_count"]) is int
        and json_integer(aggregate["execution_class_count"], "E_AGGREGATE_PROVENANCE", "aggregate class count", positive=True) > 0
        and json_integer(aggregate["covered_probe_count"], "E_AGGREGATE_PROVENANCE", "aggregate covered probes", positive=True) > 0,
        "E_AGGREGATE_PROVENANCE",
        "aggregate exec verified totals differ",
    )
    require(
        provenance["merge_semantics"] == "exact-session-and-probe-bitmap-union",
        "E_AGGREGATE_PROVENANCE",
        "aggregate merge semantics are not exact probe union",
    )
    return provenance


def validate_toolchain_replay_payload(
    replay: dict[str, Any],
    *,
    label: str,
    expected_stage: str,
    run_id: str,
    receipt_sha256: str,
    tool_sha256: str,
) -> None:
    exact_keys(
        replay,
        (
            "schema_version",
            "kind",
            "stage",
            "command",
            "run_id",
            "receipt_sha256",
            "tool_sha256",
            "compiler_realm",
            "jacoco_realm",
            "result",
        ),
        "E_REPORT_PROVENANCE",
        label,
    )
    require(
        replay["schema_version"] == 1
        and type(replay["schema_version"]) is int
        and replay["kind"] == "v934-step4-toolchain-replay-stage"
        and replay["stage"] == expected_stage
        and replay["command"] == "verify"
        and replay["run_id"] == run_id
        and json_sha256(
            replay["receipt_sha256"],
            "E_REPORT_PROVENANCE",
            f"{label} receipt SHA",
        )
        == receipt_sha256
        and json_sha256(
            replay["tool_sha256"],
            "E_REPORT_PROVENANCE",
            f"{label} tool SHA",
        )
        == tool_sha256
        and replay["compiler_realm"] == 12
        and type(replay["compiler_realm"]) is int
        and replay["jacoco_realm"] == 12
        and type(replay["jacoco_realm"]) is int
        and replay["result"] == "passed",
        "E_REPORT_PROVENANCE",
        f"{label} replay binding differs",
    )


def validate_report_provenance(
    repo_root: Path,
    report_provenance_path: Path,
    aggregate_provenance_path: Path,
    aggregate_exec_path: Path,
    xml_path: Path,
    exec_manifest_path: Path,
    run_id: str,
) -> dict[str, Any]:
    report = load_json(report_provenance_path, "E_REPORT_PROVENANCE")
    exact_keys(
        report,
        (
            "schema_version",
            "kind",
            "run_id",
            "git_head",
            "run_context_sha256",
            "source_sha256",
            "toolchain_receipt",
            "toolchain_replay_pre",
            "toolchain_replay_post",
            "exec_manifest",
            "aggregate_provenance",
            "aggregate_exec",
            "aggregate_xml",
            "aggregate_html_entry",
            "effective_reporter_pom",
            "effective_reporter_pom_receipt",
            "normalized_effective_pom_sha256",
            "jacoco",
            "deterministic_replay_count",
            "status",
        ),
        "E_REPORT_PROVENANCE",
        "report provenance",
    )
    require(
        report["schema_version"] == 1
        and type(report["schema_version"]) is int
        and report["kind"] == "v934-step4-deterministic-report-provenance"
        and report["run_id"] == run_id
        and isinstance(report["git_head"], str)
        and re.fullmatch(r"[0-9a-f]{40}", report["git_head"]) is not None
        and report["deterministic_replay_count"] == 2
        and type(report["deterministic_replay_count"]) is int
        and report["status"] == "verified",
        "E_REPORT_PROVENANCE",
        "report provenance identity/status differs",
    )
    exec_manifest = load_json(exec_manifest_path, "E_REPORT_PROVENANCE")
    require(
        report["run_context_sha256"] == exec_manifest.get("run_context_sha256")
        and report["source_sha256"] == exec_manifest.get("source_sha256")
        and report["git_head"] == exec_manifest.get("git_head"),
        "E_REPORT_PROVENANCE",
        "report run/source provenance differs from exec manifest",
    )
    json_sha256(report["run_context_sha256"], "E_REPORT_PROVENANCE", "run context SHA")
    json_sha256(report["source_sha256"], "E_REPORT_PROVENANCE", "source SHA")

    def require_identity(label: str, path: Path) -> None:
        identity = exact_keys(
            report[label],
            ("sha256", "size"),
            "E_REPORT_PROVENANCE",
            f"report provenance {label}",
        )
        file_stat = regular_file(path, "E_REPORT_PROVENANCE")
        require(
            json_sha256(identity["sha256"], "E_REPORT_PROVENANCE", f"{label} SHA")
            == sha256_file(path, "E_REPORT_PROVENANCE")
            and identity["size"] == file_stat.st_size
            and type(identity["size"]) is int
            and identity["size"] > 0,
            "E_REPORT_PROVENANCE",
            f"report provenance {label} identity differs",
        )

    require_identity("exec_manifest", exec_manifest_path)
    toolchain_receipt_path = (
        repo_root
        / "target/v934-step4-coverage/runs"
        / run_id
        / "toolchain-receipt.json"
    )
    require_identity("toolchain_receipt", toolchain_receipt_path)
    require(
        report["toolchain_receipt"]["sha256"]
        == exec_manifest.get("toolchain_receipt_sha256"),
        "E_REPORT_PROVENANCE",
        "report toolchain receipt binding differs from exec manifest",
    )

    def require_toolchain_replay(
        label: str,
        path: Path,
        expected_stage: str,
    ) -> None:
        require_identity(label, path)
        replay = load_json(path, "E_REPORT_PROVENANCE")
        validate_toolchain_replay_payload(
            replay,
            label=f"report provenance {label}",
            expected_stage=expected_stage,
            run_id=run_id,
            receipt_sha256=report["toolchain_receipt"]["sha256"],
            tool_sha256=sha256_file(
                repo_root / "scripts/v934/step4/toolchain_receipt_tool.py",
                "E_REPORT_PROVENANCE",
            ),
        )

    require_toolchain_replay(
        "toolchain_replay_pre",
        aggregate_exec_path.parent / "toolchain-replay-pre.json",
        "reporter-pre",
    )
    require_toolchain_replay(
        "toolchain_replay_post",
        aggregate_exec_path.parent / "toolchain-replay-post.json",
        "reporter-post",
    )
    require_identity("aggregate_provenance", aggregate_provenance_path)
    require_identity("aggregate_exec", aggregate_exec_path)
    require_identity("aggregate_xml", xml_path)
    html_path = aggregate_exec_path.parent / "jacoco-aggregate/index.html"
    require_identity("aggregate_html_entry", html_path)
    effective_pom_path = aggregate_exec_path.parent / "effective-reporter-pom.xml"
    effective_receipt_path = (
        aggregate_exec_path.parent / "effective-reporter-pom-receipt.json"
    )
    require_identity("effective_reporter_pom", effective_pom_path)
    require_identity("effective_reporter_pom_receipt", effective_receipt_path)
    effective_receipt = load_json(
        effective_receipt_path,
        "E_REPORT_PROVENANCE",
    )
    exact_keys(
        effective_receipt,
        (
            "schema_version",
            "kind",
            "validator_sha256",
            "raw_effective_pom_sha256",
            "raw_effective_pom_size",
            "normalized_effective_pom_sha256",
            "active_project_profiles",
            "build_plugins",
            "status",
        ),
        "E_REPORT_PROVENANCE",
        "effective reporter POM receipt",
    )
    require(
        effective_receipt["schema_version"] == 1
        and type(effective_receipt["schema_version"]) is int
        and effective_receipt["kind"]
        == "v934-step4-effective-reporter-pom-receipt"
        and effective_receipt["status"] == "verified"
        and effective_receipt["active_project_profiles"]
        == ["v934-coverage-report"]
        and effective_receipt["validator_sha256"]
        == sha256_file(
            repo_root / "scripts/v934/step4/reporter_effective_pom_tool.py",
            "E_REPORT_PROVENANCE",
        )
        and effective_receipt["raw_effective_pom_sha256"]
        == sha256_file(effective_pom_path, "E_REPORT_PROVENANCE")
        and effective_receipt["raw_effective_pom_size"]
        == regular_file(effective_pom_path, "E_REPORT_PROVENANCE").st_size
        and report["normalized_effective_pom_sha256"]
        == effective_receipt["normalized_effective_pom_sha256"],
        "E_REPORT_PROVENANCE",
        "effective reporter POM receipt binding differs",
    )
    json_sha256(
        effective_receipt["validator_sha256"],
        "E_REPORT_PROVENANCE",
        "effective POM validator SHA",
    )
    json_sha256(
        effective_receipt["raw_effective_pom_sha256"],
        "E_REPORT_PROVENANCE",
        "raw effective POM SHA",
    )
    json_sha256(
        report["normalized_effective_pom_sha256"],
        "E_REPORT_PROVENANCE",
        "normalized effective POM SHA",
    )
    expected_effective_plugins = [
        ("org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0", []),
        ("org.apache.maven.plugins", "maven-surefire-plugin", "3.5.3", []),
        (
            "org.apache.maven.plugins",
            "maven-failsafe-plugin",
            "3.5.3",
            [("", "", ["integration-test", "verify"])],
        ),
        (
            "org.apache.maven.plugins",
            "maven-source-plugin",
            "3.3.1",
            [("attach-sources", "", ["jar-no-fork"])],
        ),
        (
            "org.jacoco",
            "jacoco-maven-plugin",
            "0.8.12",
            [
                ("v934-merge-exec", "generate-resources", ["merge"]),
                ("v934-report-aggregate", "verify", ["report-aggregate"]),
            ],
        ),
        (
            "org.apache.maven.plugins",
            "maven-clean-plugin",
            "3.4.1",
            [("default-clean", "clean", ["clean"])],
        ),
        (
            "org.apache.maven.plugins",
            "maven-install-plugin",
            "3.1.4",
            [("default-install", "install", ["install"])],
        ),
        (
            "org.apache.maven.plugins",
            "maven-deploy-plugin",
            "3.1.4",
            [("default-deploy", "deploy", ["deploy"])],
        ),
        (
            "org.apache.maven.plugins",
            "maven-site-plugin",
            "3.3",
            [
                ("default-site", "site", ["site"]),
                ("default-deploy", "site-deploy", ["deploy"]),
            ],
        ),
    ]
    actual_effective_plugins = []
    require(
        type(effective_receipt["build_plugins"]) is list,
        "E_REPORT_PROVENANCE",
        "effective reporter plugin receipt must be an array",
    )
    for number, plugin in enumerate(effective_receipt["build_plugins"], 1):
        exact_keys(
            plugin,
            ("group_id", "artifact_id", "version", "executions"),
            "E_REPORT_PROVENANCE",
            f"effective plugin {number}",
        )
        require(
            type(plugin["executions"]) is list,
            "E_REPORT_PROVENANCE",
            f"effective plugin {number} executions must be an array",
        )
        executions = []
        for execution_number, execution in enumerate(plugin["executions"], 1):
            exact_keys(
                execution,
                ("id", "phase", "goals"),
                "E_REPORT_PROVENANCE",
                f"effective plugin {number} execution {execution_number}",
            )
            require(
                type(execution["goals"]) is list
                and all(type(goal) is str for goal in execution["goals"]),
                "E_REPORT_PROVENANCE",
                "effective execution goals must be strings",
            )
            executions.append(
                (execution["id"], execution["phase"], execution["goals"])
            )
        actual_effective_plugins.append(
            (
                plugin["group_id"],
                plugin["artifact_id"],
                plugin["version"],
                executions,
            )
        )
    require(
        actual_effective_plugins == expected_effective_plugins,
        "E_REPORT_PROVENANCE",
        "effective reporter plugin execution surface differs",
    )
    jacoco = exact_keys(
        report["jacoco"],
        ("version", "maven_plugin_sha256", "report_jar_sha256", "core_jar_sha256"),
        "E_REPORT_PROVENANCE",
        "report provenance jacoco",
    )
    require(
        jacoco
        == {
            "version": "0.8.12",
            "maven_plugin_sha256": "b305a57535247cff2b7450c4dc1db505c7c246c838cec48c10e52fa71aa423bd",
            "report_jar_sha256": "f9c79ad66a66a0337c57849ad1287a2ab23b9b232d35314443e5ec49e6e3d20f",
            "core_jar_sha256": "fca26db37c0c5fbd5dc4985237eb82866df9799d5082af899475a73f91f5b035",
        },
        "E_REPORT_PROVENANCE",
        "trusted JaCoCo report tool hashes differ",
    )
    expected_path = (
        repo_root
        / "target/v934-step4-coverage/runs"
        / run_id
        / "report/report-provenance.json"
    )
    require(
        report_provenance_path.resolve() == expected_path.resolve(),
        "E_REPORT_PROVENANCE",
        "report provenance is not at its canonical run-owned path",
    )
    return report


def validate_text_nodes(root: ET.Element) -> None:
    for element in root.iter():
        require(not element.text or not element.text.strip(), "E_XML_SCHEMA", f"unexpected text in <{element.tag}>")
        require(not element.tail or not element.tail.strip(), "E_XML_SCHEMA", f"unexpected tail after <{element.tag}>")


def read_xml(path: Path) -> tuple[ET.Element, os.stat_result, str]:
    file_stat = regular_file(path, "E_XML_MISSING")
    require(file_stat.st_size <= 256 * 1024 * 1024, "E_XML_SIZE", "JaCoCo XML exceeds 256 MiB")
    try:
        payload = path.read_bytes()
    except OSError as exc:
        reject("E_XML_INVALID", f"cannot read JaCoCo XML: {exc.__class__.__name__}")
    require(payload.count(JACOCO_DOCTYPE) == 1, "E_XML_DOCTYPE", "exact JaCoCo Report 1.1 doctype is required")
    require(b"<!ENTITY" not in payload.upper(), "E_XML_DOCTYPE", "XML entity declarations are forbidden")
    try:
        root = ET.fromstring(payload)
    except ET.ParseError as exc:
        reject("E_XML_INVALID", f"invalid JaCoCo XML: {exc}")
    require(root.tag == "report", "E_XML_ROOT", "JaCoCo XML root must be <report>")
    validate_text_nodes(root)
    return root, file_stat, hashlib.sha256(payload).hexdigest()


def require_attributes(element: ET.Element, keys: Sequence[str], code: str, label: str) -> None:
    require(set(element.attrib) == set(keys), code, f"{label} attributes differ: {sorted(element.attrib)}")
    for key in keys:
        require(element.attrib[key] != "" and element.attrib[key].strip() == element.attrib[key], code, f"{label}.{key} is empty or padded")


def require_child_order(element: ET.Element, phases: Sequence[str], code: str, label: str) -> None:
    phase_index = {name: index for index, name in enumerate(phases)}
    last = -1
    for child in element:
        require(child.tag in phase_index, code, f"unexpected <{child.tag}> in {label}")
        current = phase_index[child.tag]
        require(current >= last, code, f"out-of-order <{child.tag}> in {label}")
        last = current


CounterVector = dict[str, tuple[int, int]]


def counters(element: ET.Element, label: str) -> CounterVector:
    result: CounterVector = {}
    for counter in element.findall("counter"):
        require_attributes(counter, ("type", "missed", "covered"), "E_COUNTER", f"{label} counter")
        counter_type = counter.attrib["type"]
        require(counter_type in COUNTER_TYPES, "E_COUNTER", f"unknown counter type in {label}: {counter_type}")
        require(counter_type not in result, "E_COUNTER", f"duplicate {counter_type} counter in {label}")
        missed = unsigned(counter.attrib["missed"], "E_COUNTER", f"{label} {counter_type} missed")
        covered = unsigned(counter.attrib["covered"], "E_COUNTER", f"{label} {counter_type} covered")
        require(missed + covered > 0, "E_COUNTER", f"zero-total {counter_type} counter in {label}")
        result[counter_type] = (missed, covered)
    return result


def sum_counters(elements: Sequence[ET.Element]) -> CounterVector:
    totals: dict[str, list[int]] = {counter_type: [0, 0] for counter_type in COUNTER_TYPES}
    for element in elements:
        for counter_type, (missed, covered) in counters(element, f"<{element.tag}>").items():
            totals[counter_type][0] += missed
            totals[counter_type][1] += covered
    return {
        counter_type: (values[0], values[1])
        for counter_type, values in totals.items()
        if values[0] + values[1] > 0
    }


def require_parent_sum(parent: ET.Element, children: Sequence[ET.Element], label: str) -> None:
    actual = counters(parent, label)
    expected = sum_counters(children)
    require(actual == expected, "E_COUNTER_SUM", f"{label} counters do not equal child sums: actual={actual} expected={expected}")


def require_selected_parent_sum(
    parent: ET.Element,
    children: Sequence[ET.Element],
    counter_types: Sequence[str],
    label: str,
) -> None:
    actual = counters(parent, label)
    expected = sum_counters(children)
    for counter_type in counter_types:
        require(
            actual.get(counter_type, (0, 0)) == expected.get(counter_type, (0, 0)),
            "E_COUNTER_SUM",
            f"{label} {counter_type} does not equal child sums: "
            f"actual={actual.get(counter_type, (0, 0))} "
            f"expected={expected.get(counter_type, (0, 0))}",
        )


def validate_method(method: ET.Element, class_name: str) -> tuple[str, str, str]:
    require_attributes(method, ("name", "desc", "line"), "E_XML_SCHEMA", f"method in {class_name}")
    unsigned(method.attrib["line"], "E_XML_SCHEMA", f"method line in {class_name}", positive=True)
    require_child_order(method, ("counter",), "E_XML_SCHEMA", f"method {class_name}#{method.attrib['name']}")
    method_counters = counters(method, f"method {class_name}#{method.attrib['name']}{method.attrib['desc']}")
    require(set(method_counters).issubset(set(SUMMABLE_METHOD_COUNTERS) | {"LINE"}), "E_COUNTER", f"invalid method counter types in {class_name}")
    require("INSTRUCTION" in method_counters and "LINE" in method_counters and "COMPLEXITY" in method_counters and "METHOD" in method_counters, "E_COUNTER", f"incomplete method counters in {class_name}")
    require(sum(method_counters["METHOD"]) == 1, "E_COUNTER", f"method cardinality counter differs in {class_name}")
    return class_name, method.attrib["name"], method.attrib["desc"]


def validate_class(
    class_element: ET.Element,
    package_name: str,
    artifact: str,
    module: str,
    repo_root: Path,
    source_names: set[str],
) -> tuple[str, CounterVector]:
    require_attributes(class_element, ("name", "sourcefilename"), "E_XML_SCHEMA", f"class in {artifact}")
    class_name = class_element.attrib["name"]
    source_name = class_element.attrib["sourcefilename"]
    require("\\" not in class_name and class_name == class_name.strip("/"), "E_XML_SCHEMA", f"unsafe XML class name: {class_name!r}")
    parts = class_name.split("/")
    require(parts and all(part not in ("", ".", "..") for part in parts), "E_XML_SCHEMA", f"unsafe XML class name: {class_name!r}")
    require("/".join(parts[:-1]) == package_name, "E_XML_SCHEMA", f"class/package mismatch: {class_name}")
    require("/" not in source_name and "\\" not in source_name and source_name in source_names, "E_XML_SCHEMA", f"class source file is absent in package: {class_name}")
    require_child_order(class_element, ("method", "counter"), "E_XML_SCHEMA", f"class {class_name}")
    methods = class_element.findall("method")
    method_identities = [validate_method(method, class_name) for method in methods]
    require(len(method_identities) == len(set(method_identities)), "E_METHOD_IDENTITY", f"duplicate method identity in {class_name}")
    class_counters = counters(class_element, f"class {class_name}")
    if class_counters:
        require("CLASS" in class_counters and sum(class_counters["CLASS"]) == 1, "E_COUNTER", f"class cardinality counter differs: {class_name}")
        method_sum = sum_counters(methods)
        for counter_type in SUMMABLE_METHOD_COUNTERS:
            actual = class_counters.get(counter_type, (0, 0))
            expected = method_sum.get(counter_type, (0, 0))
            require(actual == expected, "E_COUNTER_SUM", f"class {class_name} {counter_type} does not equal method sum")

    classes_root = repo_root / module / "target/classes"
    class_file = classes_root.joinpath(*parts[:-1], f"{parts[-1]}.class")
    try:
        class_file.resolve(strict=False).relative_to(classes_root.resolve())
    except (OSError, ValueError) as exc:
        reject("E_XML_CLASS_EXTRA", f"class path escapes module {module}: {class_name} ({exc.__class__.__name__})")
    try:
        class_stat = class_file.lstat()
    except FileNotFoundError:
        reject("E_XML_CLASS_EXTRA", f"XML class is absent from {module}/target/classes: {class_name}")
    except OSError as exc:
        reject("E_XML_CLASS_EXTRA", f"cannot inspect compiled class {class_name}: {exc.__class__.__name__}")
    require(stat.S_ISREG(class_stat.st_mode) and not stat.S_ISLNK(class_stat.st_mode) and class_stat.st_size > 0, "E_XML_CLASS_EXTRA", f"compiled class is not a real nonempty file: {class_name}")
    return class_name, class_counters


def validate_sourcefile(sourcefile: ET.Element, package_label: str) -> tuple[str, CounterVector]:
    require_attributes(sourcefile, ("name",), "E_XML_SCHEMA", f"sourcefile in {package_label}")
    name = sourcefile.attrib["name"]
    require("/" not in name and "\\" not in name and name not in (".", ".."), "E_XML_SCHEMA", f"unsafe source file name: {name!r}")
    require_child_order(sourcefile, ("line", "counter"), "E_XML_SCHEMA", f"sourcefile {package_label}/{name}")
    lines = sourcefile.findall("line")
    line_numbers: list[int] = []
    instruction = [0, 0]
    branch = [0, 0]
    line_counter = [0, 0]
    for line in lines:
        require_attributes(line, ("nr", "mi", "ci", "mb", "cb"), "E_XML_SCHEMA", f"line in {package_label}/{name}")
        require(len(line) == 0, "E_XML_SCHEMA", f"line element has children in {package_label}/{name}")
        number = unsigned(line.attrib["nr"], "E_XML_SCHEMA", f"line number in {name}", positive=True)
        missed_instruction = unsigned(line.attrib["mi"], "E_XML_SCHEMA", f"line {number} missed instructions")
        covered_instruction = unsigned(line.attrib["ci"], "E_XML_SCHEMA", f"line {number} covered instructions")
        missed_branch = unsigned(line.attrib["mb"], "E_XML_SCHEMA", f"line {number} missed branches")
        covered_branch = unsigned(line.attrib["cb"], "E_XML_SCHEMA", f"line {number} covered branches")
        require(missed_instruction + covered_instruction > 0, "E_XML_SCHEMA", f"line {number} has no instructions")
        line_numbers.append(number)
        instruction[0] += missed_instruction
        instruction[1] += covered_instruction
        branch[0] += missed_branch
        branch[1] += covered_branch
        line_counter[covered_instruction > 0] += 1
    require(line_numbers == sorted(set(line_numbers)), "E_LINE_IDENTITY", f"source lines are not strictly increasing unique in {package_label}/{name}")
    source_counters = counters(sourcefile, f"sourcefile {package_label}/{name}")
    derived = {
        "INSTRUCTION": tuple(instruction),
        "BRANCH": tuple(branch),
        "LINE": tuple(line_counter),
    }
    for counter_type, value in derived.items():
        expected = value if sum(value) else (0, 0)
        require(source_counters.get(counter_type, (0, 0)) == expected, "E_COUNTER_SUM", f"sourcefile {package_label}/{name} {counter_type} does not equal line sums")
    return name, source_counters


def validate_xml_structure(
    root: ET.Element,
    expected_sessions: Sequence[str],
    module_order: list[str],
    artifact_to_module: dict[str, str],
    critical_rows: list[dict[str, str]],
    repo_root: Path,
) -> tuple[
    CounterVector,
    dict[str, CounterVector],
    dict[str, tuple[str, CounterVector]],
    set[str],
]:
    require_attributes(root, ("name",), "E_XML_SCHEMA", "report")
    require_child_order(root, ("sessioninfo", "group", "counter"), "E_XML_SCHEMA", "report")

    xml_sessions: list[str] = []
    for session in root.findall("sessioninfo"):
        require_attributes(session, ("id", "start", "dump"), "E_XML_SCHEMA", "sessioninfo")
        require(len(session) == 0, "E_XML_SCHEMA", "sessioninfo must not have children")
        start = unsigned(session.attrib["start"], "E_XML_SCHEMA", f"session {session.attrib['id']} start", positive=True)
        dump = unsigned(session.attrib["dump"], "E_XML_SCHEMA", f"session {session.attrib['id']} dump", positive=True)
        require(dump >= start, "E_XML_SCHEMA", f"session dump predates start: {session.attrib['id']}")
        xml_sessions.append(session.attrib["id"])
    require(len(xml_sessions) == len(set(xml_sessions)), "E_SESSION_DUPLICATE", "duplicate XML session identity")
    require(
        len(xml_sessions) == 48 and set(xml_sessions) == set(expected_sessions),
        "E_SESSION_SET",
        f"XML session set differs from exact manifest sessions: missing={sorted(set(expected_sessions) - set(xml_sessions))} unexpected={sorted(set(xml_sessions) - set(expected_sessions))}",
    )

    groups = root.findall("group")
    expected_artifacts = [next(artifact for artifact, module in artifact_to_module.items() if module == module_path) for module_path in module_order]
    group_names: list[str] = []
    for group in groups:
        require_attributes(group, ("name",), "E_XML_SCHEMA", "group")
        group_names.append(group.attrib["name"])
    require(len(group_names) == len(set(group_names)), "E_GROUP_IDENTITY", "duplicate JaCoCo group identity")
    require(group_names == expected_artifacts, "E_GROUP_SET", f"JaCoCo groups differ from exact frozen production artifacts: expected={expected_artifacts} actual={group_names}")

    for module in module_order:
        real_directory(repo_root / module / "target/classes", "E_CLASS_TREE")

    # Critical presence is an independent fail-closed identity contract.  Check
    # it before hierarchy arithmetic so deleting a critical node reports the
    # semantic omission, not merely the resulting package-counter drift.
    for row in critical_rows:
        xml_name = row["fqcn"].replace(".", "/")
        matches = [
            group.attrib["name"]
            for group in groups
            for package in group.findall("package")
            for class_element in package.findall("class")
            if class_element.attrib.get("name") == xml_name
        ]
        require(len(matches) == 1, "E_CRITICAL_MISSING", f"critical class must occur exactly once in JaCoCo XML: {row['fqcn']}")
        expected_artifact = next(artifact for artifact, module in artifact_to_module.items() if module == row["module"])
        require(matches[0] == expected_artifact, "E_CRITICAL_MODULE", f"critical class {row['fqcn']} belongs to {matches[0]}, expected {expected_artifact}")

    all_package_ids: set[tuple[str, str]] = set()
    all_class_names: set[str] = set()
    all_method_ids: set[tuple[str, str, str]] = set()
    group_counters: dict[str, CounterVector] = {}
    class_index: dict[str, tuple[str, CounterVector]] = {}
    for group in groups:
        artifact = group.attrib["name"]
        module = artifact_to_module[artifact]
        require_child_order(group, ("package", "counter"), "E_XML_SCHEMA", f"group {artifact}")
        packages = group.findall("package")
        for package in packages:
            require_attributes(package, ("name",), "E_XML_SCHEMA", f"package in {artifact}")
            package_name = package.attrib["name"]
            package_parts = package_name.split("/")
            require(package_name and "\\" not in package_name and all(part not in ("", ".", "..") for part in package_parts), "E_XML_SCHEMA", f"unsafe package name: {package_name!r}")
            package_id = (artifact, package_name)
            require(package_id not in all_package_ids, "E_PACKAGE_IDENTITY", f"duplicate package identity: {artifact}:{package_name}")
            all_package_ids.add(package_id)
            require_child_order(package, ("class", "sourcefile", "counter"), "E_XML_SCHEMA", f"package {artifact}:{package_name}")
            sourcefiles = package.findall("sourcefile")
            source_names = [sourcefile.attrib.get("name", "") for sourcefile in sourcefiles]
            require(len(source_names) == len(set(source_names)), "E_SOURCE_IDENTITY", f"duplicate sourcefile identity in {artifact}:{package_name}")
            source_counter_rows: list[CounterVector] = []
            for sourcefile in sourcefiles:
                _, source_counter = validate_sourcefile(sourcefile, f"{artifact}:{package_name}")
                source_counter_rows.append(source_counter)

            classes = package.findall("class")
            for class_element in classes:
                class_name, class_counter = validate_class(
                    class_element,
                    package_name,
                    artifact,
                    module,
                    repo_root,
                    set(source_names),
                )
                require(class_name not in all_class_names, "E_CLASS_IDENTITY", f"duplicate XML class identity: {class_name}")
                all_class_names.add(class_name)
                class_index[class_name] = (artifact, class_counter)
                for method in class_element.findall("method"):
                    identity = (class_name, method.attrib["name"], method.attrib["desc"])
                    require(identity not in all_method_ids, "E_METHOD_IDENTITY", f"duplicate method identity: {identity}")
                    all_method_ids.add(identity)

            # A source file can contain multiple classes on the same source line,
            # so LINE is intentionally not additive across class nodes.  The
            # bytecode-oriented counters remain additive across classes, while
            # the sourcefile view below proves all six package counters.
            require_selected_parent_sum(
                package,
                classes,
                ("INSTRUCTION", "BRANCH", "COMPLEXITY", "METHOD", "CLASS"),
                f"package {artifact}:{package_name} from classes",
            )
            require_parent_sum(package, sourcefiles, f"package {artifact}:{package_name} from sourcefiles")
        require_parent_sum(group, packages, f"group {artifact}")
        group_counters[artifact] = counters(group, f"group {artifact}")

    # Locate critical identities before aggregate arithmetic so a critical-class
    # deletion cannot hide behind the consequent parent-counter drift.
    for row in critical_rows:
        xml_name = row["fqcn"].replace(".", "/")
        require(xml_name in class_index, "E_CRITICAL_MISSING", f"critical class is absent from JaCoCo XML: {row['fqcn']}")
        expected_artifact = next(artifact for artifact, module in artifact_to_module.items() if module == row["module"])
        actual_artifact = class_index[xml_name][0]
        require(actual_artifact == expected_artifact, "E_CRITICAL_MODULE", f"critical class {row['fqcn']} belongs to {actual_artifact}, expected {expected_artifact}")

    require_parent_sum(root, groups, "report")
    root_counters = counters(root, "report")
    require("LINE" in root_counters and "BRANCH" in root_counters, "E_COUNTER", "aggregate report must contain LINE and BRANCH counters")
    return root_counters, group_counters, class_index, all_class_names


def counter_json(counter: tuple[int, int]) -> dict[str, Any]:
    missed, covered = counter
    total = missed + covered
    return {
        "missed": missed,
        "covered": covered,
        "total": total,
        "ratio": None if total == 0 else round(covered / total, 12),
        "fraction": None if total == 0 else f"{covered}/{total}",
    }


def all_counters_json(values: CounterVector) -> dict[str, dict[str, Any]]:
    return {counter_type.lower(): counter_json(values.get(counter_type, (0, 0))) for counter_type in COUNTER_TYPES}


def critical_observations(
    critical_rows: list[dict[str, str]],
    class_index: dict[str, tuple[str, CounterVector]],
    artifact_to_module: dict[str, str],
    line_floor: Decimal,
    branch_floor: Decimal,
) -> tuple[list[dict[str, Any]], int, int]:
    results: list[dict[str, Any]] = []
    below_floor_count = 0
    not_applicable_count = 0
    for row in critical_rows:
        xml_name = row["fqcn"].replace(".", "/")
        artifact, class_counters = class_index[xml_name]
        require(artifact_to_module[artifact] == row["module"], "E_CRITICAL_MODULE", f"critical module mismatch: {row['fqcn']}")
        metrics: dict[str, Any] = {}
        class_below = False
        for name, counter_type, floor in (
            ("line", "LINE", line_floor),
            ("branch", "BRANCH", branch_floor),
        ):
            missed, covered = class_counters.get(counter_type, (0, 0))
            total = missed + covered
            if total == 0:
                outcome = "not-applicable"
                gap: float | None = None
                not_applicable_count += 1
            else:
                actual = Decimal(covered) / Decimal(total)
                if actual >= floor:
                    outcome = "at-or-above-floor"
                    gap = 0.0
                else:
                    outcome = "below-floor"
                    gap = round(float(floor - actual), 12)
                    class_below = True
            metrics[name] = {
                **counter_json((missed, covered)),
                "floor": float(floor),
                "outcome": outcome,
                "gap": gap,
            }
        if class_below:
            below_floor_count += 1
        results.append(
            {
                "fqcn": row["fqcn"],
                "module": row["module"],
                "artifact_id": artifact,
                "line": metrics["line"],
                "branch": metrics["branch"],
                "candidate_floor_outcome": "below-floor" if class_below else "at-or-above-floor",
            }
        )
    return results, below_floor_count, not_applicable_count


def observe_data(
    repo_root: Path,
    xml_path: Path,
    manifest_path: Path,
    aggregate_exec_path: Path,
    aggregate_provenance_path: Path,
    report_provenance_path: Path,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    real_directory(repo_root, "E_REPO_ROOT")
    ledger = load_ledger(repo_root)
    modules, artifact_to_module, freeze_sha = load_frozen_modules(repo_root)
    step1, step4, threshold_hashes = load_thresholds(repo_root)
    manifest, expected_sessions, input_provenance = validate_manifest(
        repo_root, manifest_path, ledger, modules
    )
    aggregate_provenance = validate_aggregate_provenance(
        repo_root,
        aggregate_provenance_path,
        aggregate_exec_path,
        manifest_path,
        manifest,
    )
    report_provenance = validate_report_provenance(
        repo_root,
        report_provenance_path,
        aggregate_provenance_path,
        aggregate_exec_path,
        xml_path,
        manifest_path,
        manifest["run_id"],
    )
    aggregate_stat = regular_file(aggregate_exec_path, "E_AGGREGATE_EXEC_MISSING")
    aggregate_sha = sha256_file(aggregate_exec_path, "E_AGGREGATE_EXEC")
    root, xml_stat, xml_sha = read_xml(xml_path)
    critical_rows = step1["critical_classes"]
    root_counters, group_counters, class_index, xml_class_names = validate_xml_structure(
        root,
        expected_sessions,
        modules,
        artifact_to_module,
        critical_rows,
        repo_root,
    )
    # JaCoCo intentionally filters compiler-generated classes (for example
    # switch-map helpers) and package-info bytecode from report XML.  Raw
    # target/classes equality would therefore reject every valid report.  The
    # class universe is instead sealed by the exact 24-dependency reporter POM,
    # trusted JaCoCo JAR hashes, exact exec probe union, and two byte-identical
    # reporter replays recorded in report provenance.
    line_floor = decimal_ratio(step4["critical_candidate_floor"]["line"], "E_THRESHOLD", "critical line floor")
    branch_floor = decimal_ratio(step4["critical_candidate_floor"]["branch"], "E_THRESHOLD", "critical branch floor")
    critical_results, below_floor_count, not_applicable_count = critical_observations(
        critical_rows,
        class_index,
        artifact_to_module,
        line_floor,
        branch_floor,
    )
    aggregate_line = root_counters["LINE"]
    aggregate_branch = root_counters["BRANCH"]
    return {
        "schema_version": 1,
        "kind": "v934-step4-coverage-observation",
        "run_id": manifest["run_id"],
        "status": "observed",
        "provenance": {
            "exec_manifest": {
                "path": display_path(repo_root, manifest_path),
                "sha256": sha256_file(manifest_path, "E_MANIFEST"),
                "session_count": 48,
                "exec_count": 23,
            },
            "aggregate_exec": {
                "path": display_path(repo_root, aggregate_exec_path),
                "sha256": aggregate_sha,
                "size": aggregate_stat.st_size,
            },
            "aggregate_provenance": {
                "path": display_path(repo_root, aggregate_provenance_path),
                "sha256": sha256_file(aggregate_provenance_path, "E_AGGREGATE_PROVENANCE"),
                "merge_semantics": aggregate_provenance["merge_semantics"],
            },
            "report_provenance": {
                "path": display_path(repo_root, report_provenance_path),
                "sha256": sha256_file(report_provenance_path, "E_REPORT_PROVENANCE"),
                "git_head": report_provenance["git_head"],
                "deterministic_replay_count": 2,
            },
            "aggregate_xml": {
                "path": display_path(repo_root, xml_path),
                "sha256": xml_sha,
                "size": xml_stat.st_size,
            },
            "step1_contract_freeze_sha256": freeze_sha,
            **threshold_hashes,
            **input_provenance,
        },
        "report_inventory": {
            "group_count": len(group_counters),
            "session_count": 48,
            "critical_class_count": len(critical_results),
            "reportable_class_count": len(xml_class_names),
            "workspace_bytecode_class_count": manifest["workspace_class_count"],
            "class_universe_binding": "exact-reporter-config-and-deterministic-replay",
            "frozen_modules": modules,
        },
        "aggregate_observed": {
            "line": counter_json(aggregate_line),
            "branch": counter_json(aggregate_branch),
            "counters": all_counters_json(root_counters),
        },
        "group_counters": {
            artifact: {
                "module": artifact_to_module[artifact],
                "counters": all_counters_json(group_counters[artifact]),
            }
            for artifact in artifact_to_module
        },
        "critical_candidate_floor": {
            "line": float(line_floor),
            "branch": float(branch_floor),
            "outcome": "below-floor-gaps-recorded" if below_floor_count else "at-or-above-floor",
            "below_floor_class_count": below_floor_count,
            "not_applicable_metric_count": not_applicable_count,
            "thresholds_frozen_by_observe": False,
        },
        "critical_classes": critical_results,
    }


def observe_command(args: argparse.Namespace) -> None:
    result = observe_data(
        args.repo_root,
        args.xml,
        args.exec_manifest,
        args.aggregate_exec,
        args.aggregate_provenance,
        args.report_provenance,
    )
    atomic_json(args.output, result)
    print(
        "[v934-coverage-xml] PASS "
        f"groups={result['report_inventory']['group_count']} "
        f"sessions={result['report_inventory']['session_count']} "
        f"critical={result['report_inventory']['critical_class_count']} "
        f"below_floor={result['critical_candidate_floor']['below_floor_class_count']} "
        f"output={args.output}"
    )


def write_xml_fixture(path: Path, root: ET.Element) -> None:
    body = ET.tostring(root, encoding="utf-8", short_empty_elements=True)
    payload = b'<?xml version="1.0" encoding="UTF-8" standalone="yes"?>\n' + JACOCO_DOCTYPE + b"\n" + body + b"\n"
    path.write_bytes(payload)


def expect_failure(
    expected_code: str,
    action: Callable[[], Any],
) -> dict[str, str]:
    try:
        action()
    except CoverageXmlError as exc:
        require(exc.code == expected_code, "E_NEGATIVE_CODE", f"expected {expected_code}, got {exc.code}: {exc.message}")
        return {"expected_code": expected_code, "observed_code": exc.code, "status": "passed"}
    reject("E_NEGATIVE_FALSE_GREEN", f"mutation was accepted: expected {expected_code}")


def insert_group_before_counters(root: ET.Element, group: ET.Element) -> None:
    children = list(root)
    position = next((index for index, child in enumerate(children) if child.tag == "counter"), len(children))
    root.insert(position, group)


def negative_command(args: argparse.Namespace) -> None:
    output_dir = args.output_dir
    if output_dir.exists() or output_dir.is_symlink():
        reject("E_OUTPUT_EXISTS", f"refusing to overwrite negative output directory: {output_dir}")
    output_dir.mkdir(parents=True)
    real_directory(output_dir, "E_OUTPUT_DIR")

    # Prove the supplied fixture itself satisfies every observation contract.
    baseline = observe_data(
        args.repo_root,
        args.xml,
        args.exec_manifest,
        args.aggregate_exec,
        args.aggregate_provenance,
        args.report_provenance,
    )
    root, _, xml_sha = read_xml(args.xml)
    cases: dict[str, dict[str, str]] = {}
    repo_root = args.repo_root.resolve()
    ledger = load_ledger(repo_root)
    modules, artifact_to_module, _ = load_frozen_modules(repo_root)
    step1, _, _ = load_thresholds(repo_root)
    manifest, expected_sessions, _ = validate_manifest(
        repo_root,
        args.exec_manifest,
        ledger,
        modules,
    )
    replay_pre = load_json(
        args.aggregate_exec.parent / "toolchain-replay-pre.json",
        "E_REPORT_PROVENANCE",
    )
    replay_tamper = copy.deepcopy(replay_pre)
    replay_tamper["receipt_sha256"] = "0" * 64
    cases["toolchain-replay-receipt-tamper"] = expect_failure(
        "E_REPORT_PROVENANCE",
        lambda: validate_toolchain_replay_payload(
            replay_tamper,
            label="negative reporter-pre replay",
            expected_stage="reporter-pre",
            run_id=manifest["run_id"],
            receipt_sha256=manifest["toolchain_receipt_sha256"],
            tool_sha256=sha256_file(
                repo_root / "scripts/v934/step4/toolchain_receipt_tool.py",
                "E_REPORT_PROVENANCE",
            ),
        ),
    )
    def validate_mutated_xml(path: Path) -> None:
        mutated_root, _, _ = read_xml(path)
        validate_xml_structure(
            mutated_root,
            expected_sessions,
            modules,
            artifact_to_module,
            step1["critical_classes"],
            repo_root,
        )

    missing_path = output_dir / "missing.xml"
    cases["missing"] = expect_failure(
        "E_XML_MISSING",
        lambda: validate_mutated_xml(missing_path),
    )

    empty_path = output_dir / "empty.xml"
    empty_path.touch()
    cases["empty"] = expect_failure(
        "E_FILE_EMPTY",
        lambda: validate_mutated_xml(empty_path),
    )

    symlink_path = output_dir / "symlink.xml"
    symlink_path.symlink_to(args.xml.resolve())
    cases["symlink"] = expect_failure(
        "E_FILE_SYMLINK",
        lambda: validate_mutated_xml(symlink_path),
    )

    malformed_path = output_dir / "malformed.xml"
    malformed_path.write_bytes(JACOCO_DOCTYPE + b"\n<report>")
    cases["malformed"] = expect_failure(
        "E_XML_INVALID",
        lambda: validate_mutated_xml(malformed_path),
    )

    missing_session_root = copy.deepcopy(root)
    first_session = missing_session_root.find("sessioninfo")
    require(first_session is not None, "E_NEGATIVE_FIXTURE", "baseline XML contains no session")
    missing_session_root.remove(first_session)
    missing_session_path = output_dir / "missing-session.xml"
    write_xml_fixture(missing_session_path, missing_session_root)
    cases["missing-session"] = expect_failure(
        "E_SESSION_SET",
        lambda: validate_mutated_xml(missing_session_path),
    )

    duplicate_group_root = copy.deepcopy(root)
    first_group = duplicate_group_root.find("group")
    require(first_group is not None, "E_NEGATIVE_FIXTURE", "baseline XML contains no group")
    insert_group_before_counters(duplicate_group_root, copy.deepcopy(first_group))
    duplicate_group_path = output_dir / "duplicate-group.xml"
    write_xml_fixture(duplicate_group_path, duplicate_group_root)
    cases["duplicate-group"] = expect_failure(
        "E_GROUP_IDENTITY",
        lambda: validate_mutated_xml(duplicate_group_path),
    )

    counter_drift_root = copy.deepcopy(root)
    line_counter = next(
        (counter for counter in counter_drift_root.findall("counter") if counter.attrib.get("type") == "LINE"),
        None,
    )
    require(line_counter is not None, "E_NEGATIVE_FIXTURE", "baseline XML contains no root LINE counter")
    line_counter.set("covered", str(int(line_counter.attrib["covered"]) + 1))
    counter_drift_path = output_dir / "counter-drift.xml"
    write_xml_fixture(counter_drift_path, counter_drift_root)
    cases["counter-drift"] = expect_failure(
        "E_COUNTER_SUM",
        lambda: validate_mutated_xml(counter_drift_path),
    )

    step1, _, _ = load_thresholds(args.repo_root.resolve())
    critical_name = step1["critical_classes"][0]["fqcn"].replace(".", "/")
    critical_missing_root = copy.deepcopy(root)
    removed = False
    for package in critical_missing_root.findall("./group/package"):
        for class_element in package.findall("class"):
            if class_element.attrib.get("name") == critical_name:
                package.remove(class_element)
                removed = True
                break
        if removed:
            break
    require(removed, "E_NEGATIVE_FIXTURE", "baseline XML is missing the first critical class")
    critical_missing_path = output_dir / "critical-missing.xml"
    write_xml_fixture(critical_missing_path, critical_missing_root)
    cases["critical-missing"] = expect_failure(
        "E_CRITICAL_MISSING",
        lambda: validate_mutated_xml(critical_missing_path),
    )

    result = {
        "schema_version": 1,
        "kind": "v934-step4-coverage-xml-negative-result",
        "baseline_xml_sha256": xml_sha,
        "baseline_exec_manifest_sha256": sha256_file(args.exec_manifest, "E_MANIFEST"),
        "baseline_aggregate_exec_sha256": sha256_file(args.aggregate_exec, "E_AGGREGATE_EXEC"),
        "baseline_aggregate_provenance_sha256": sha256_file(args.aggregate_provenance, "E_AGGREGATE_PROVENANCE"),
        "baseline_report_provenance_sha256": sha256_file(args.report_provenance, "E_REPORT_PROVENANCE"),
        "baseline_status": baseline["status"],
        "case_count": len(cases),
        "cases": cases,
        "status": "passed",
    }
    atomic_json(output_dir / "negative-result.json", result)
    print(f"[v934-coverage-xml] NEGATIVE PASS cases={len(cases)} output={output_dir / 'negative-result.json'}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    observe = commands.add_parser("observe", help="validate and record an all-lane aggregate observation")
    observe.add_argument("--repo-root", type=Path, required=True)
    observe.add_argument("--xml", type=Path, required=True)
    observe.add_argument("--exec-manifest", type=Path, required=True)
    observe.add_argument("--aggregate-exec", type=Path, required=True)
    observe.add_argument("--aggregate-provenance", type=Path, required=True)
    observe.add_argument("--report-provenance", type=Path, required=True)
    observe.add_argument("--output", type=Path, required=True)
    observe.set_defaults(function=observe_command)

    negative = commands.add_parser("negative", help="prove malformed and incomplete XML evidence fails closed")
    negative.add_argument("--repo-root", type=Path, required=True)
    negative.add_argument("--xml", type=Path, required=True)
    negative.add_argument("--exec-manifest", type=Path, required=True)
    negative.add_argument("--aggregate-exec", type=Path, required=True)
    negative.add_argument("--aggregate-provenance", type=Path, required=True)
    negative.add_argument("--report-provenance", type=Path, required=True)
    negative.add_argument("--output-dir", type=Path, required=True)
    negative.set_defaults(function=negative_command)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        args.function(args)
    except CoverageXmlError as exc:
        print(f"[v934-coverage-xml] ERROR {exc.code}: {exc.message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
