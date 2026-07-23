#!/usr/bin/env python3
"""Fail-closed JaCoCo XML observation and negative probes for 9.3.4 Step 4."""

from __future__ import annotations

import argparse
import ctypes
import copy
import csv
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
import errno
from fractions import Fraction
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import stat
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from typing import Any, Callable, Iterable, Sequence

import frozen_diagnostic_capsule_tool as diagnostic_capsule


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
EXPECTED_CLASS_ID_CONSISTENCY_SCOPE = "frozen-24-module-production-class-universe"
EXPECTED_AGGREGATE_MERGE_SEMANTICS = (
    "exact-session-and-jacoco-class-id-probe-bitmap-union"
)
GIT_SAFE_DIAGNOSTIC_PROFILE = "git-safe-sanitized-attested-v1"
GIT_SAFE_DIAGNOSTIC_REPLAY_SCOPE = "sanitized-attested-semantic-replay"
GIT_SAFE_DIAGNOSTIC_CAPSULE_POLICY = {
    "schema_version": 2,
    "profile": GIT_SAFE_DIAGNOSTIC_PROFILE,
    "archive_members": [
        "evidence/diagnostic-attestation.json",
        "evidence/jacoco.xml",
    ],
    "retention": {
        "runtime_closure": "forbidden",
        "execution_bytes": "forbidden",
        "unstructured_output": "forbidden",
    },
    "replay_scope": GIT_SAFE_DIAGNOSTIC_REPLAY_SCOPE,
}
GIT_SAFE_SEMANTIC_OBSERVATION_KEYS = (
    "report_inventory",
    "aggregate_observed",
    "group_counters",
    "critical_candidate_floor",
    "critical_classes",
)
EXPECTED_DIAGNOSTIC_THRESHOLD_SHA256 = (
    "0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96"
)
RELEASE_SUCCESSOR_MARKER = "confirmed-threshold-post-step4-replay"
RUN_MODES = ("diagnostic", "formal", "release")
ARTIFACT_MODES = ("formal", "release")
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
GIT_HEAD_PATTERN = re.compile(r"[0-9a-f]{40}")
ENV_KEY_PATTERN = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
SOURCE_INVENTORY_HEADER = b"git_mode\tpath\tsha256\tsize\n"
MAX_SOURCE_INVENTORY_BYTES = 64 * 1024 * 1024
MAX_RAW_EXEC_BYTES = 512 * 1024 * 1024
UTC_TIMESTAMP_PATTERN = re.compile(
    r"(?:19|20)[0-9]{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])"
    r"T(?:[01][0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]Z"
)
BOOT_ID_PATTERN = re.compile(
    r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}"
)
CHILD_NAMES = ("unit", "integration", "step3-required")
CHILD_READY_FIELDS = (
    "schema_version",
    "kind",
    "run_id",
    "child",
    "pid",
    "pgid",
    "sid",
    "starttime_ticks",
    "boot_id",
    "status",
)
CHILD_COMPLETION_FIELDS = (
    "run_id",
    "child",
    "leader_pid",
    "leader_sid",
    "leader_starttime_ticks",
    "boot_id",
    "leader_exit_code",
    "leader_reaped",
    "ready_receipt_sha256",
    "process_group_residue",
    "status",
)
FORMALIZATION_EXACT_PATHS = (
    "scripts/v934/step4/coverage-thresholds.json",
    "scripts/v934/step4/coverage-contract.json",
    "scripts/v934/step4/SHA256SUMS",
    "scripts/v934/step6/ci-contract.json",
    "scripts/v934/step6/ci_contract_tool.py",
    "scripts/v934/step6/SHA256SUMS",
)
FORMALIZATION_ALLOWED_PREFIXES = ("docs/9.3.4/",)
CDIAG_ONLY_STEP5_TOOLING_PATHS = (
    "scripts/v934/step5/SHA256SUMS",
    "scripts/v934/step5/release_package_tool.py",
)
HEAD_INDEX_WORKTREE_IDENTITY_POLICY = (
    "head-index-exact-path-gitmode-blob+worktree-canonical-euid-egid-private-"
    "primary-group-single-link-group-write-exec-unbound-other-write-special-"
    "bits-forbidden-stable-raw-sha256-sealed-external-filter-forbidden-ambient-"
    "clean-config-denied-git-clean-raw-or-crlf-input-equivalent"
)
INDEX_FLAGS_IDENTITY_POLICY = "ordinary-H-only-no-fsmonitor-valid"
FORMAL_REPOSITORY_IDENTITY_POLICY = {
    "object_format": "sha1",
    "commit_relation": "direct-single-parent",
    "shallow_repository": "forbidden",
    "replace_refs": "forbidden",
    "nonempty_grafts": "forbidden",
    "nonempty_info_attributes": "forbidden",
    "index_flags": INDEX_FLAGS_IDENTITY_POLICY,
    "head_index_worktree": HEAD_INDEX_WORKTREE_IDENTITY_POLICY,
}
FORMAL_REPOSITORY_IDENTITY_FIELDS = (
    "object_format",
    "shallow_repository",
    "replace_ref_count",
    "nonempty_grafts",
    "nonempty_info_attributes",
    "index_flags",
    "head_index_worktree",
    "head_tree_sha256",
    "index_stage_sha256",
    "index_flags_sha256",
    "filter_attributes_sha256",
    "worktree_git_clean_blob_sha256",
    "commit_relation",
    "parent_count",
    "source_file_count",
    "source_sha256",
)

THRESHOLD_ROOT_KEYS = (
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
)
THRESHOLD_EVIDENCE_KEYS = (
    "run_id",
    "git_head",
    "source_sha256",
    "run_status_sha256",
    "summary_sha256",
    "observation_sha256",
    "coverage_contract_sha256",
    "threshold_predecessor_sha256",
    "exec_manifest_sha256",
    "aggregate_exec_sha256",
    "aggregate_xml_sha256",
    "workspace_class_tree_sha256",
)
# Structural N/A is an explicit policy exception, never inferred from a zero
# counter. NamespaceScope is a one-method delegating scope and its bytecode has
# no branch instructions; every other critical line/branch metric must retain a
# positive denominator.
CRITICAL_NOT_APPLICABLE_METRICS = frozenset(
    {
        (
            "com.foggyframework.dataset.db.model.spi.NamespaceScope",
            "foggy-dataset-model",
            "branch",
        )
    }
)
DIAGNOSTIC_SUMMARY_FIELDS = (
    "run_id",
    "mode",
    "git_head",
    "threshold_status",
    "source_before_sha256",
    "source_after_sha256",
    "coverage_contract_sha256",
    "outer_marker_sha256",
    "class_universe_sha256",
    "child_lifecycle_sha256",
    "toolchain_receipt_sha256",
    "toolchain_pre_compile_seal_replay_sha256",
    "toolchain_post_children_replay_sha256",
    "toolchain_reporter_pre_replay_sha256",
    "toolchain_reporter_post_replay_sha256",
    "toolchain_post_reporter_replay_sha256",
    "toolchain_post_model_replay_sha256",
    "report_inventory_sha256",
    "exec_manifest_sha256",
    "aggregate_exec_sha256",
    "aggregate_provenance_sha256",
    "report_provenance_sha256",
    "coverage_observation_sha256",
    "successor_overlay_negative_sha256",
    "coverage_contract_negative_sha256",
    "toolchain_receipt_negative_sha256",
    "effective_reporter_pom_negative_sha256",
    "report_inventory_negative_sha256",
    "coverage_exec_negative_sha256",
    "coverage_xml_negative_sha256",
    "coverage_xml_generic_negative_sha256",
    "run_log_lifecycle_negative_sha256",
    "model_gate_sha256",
    "cleanup_sha256",
    "sensitive_scan_sha256",
    "exec_files",
    "sessions",
    "required_reports",
    "required_structural_reports",
    "required_testcase_nodes",
    "addon_reports",
    "addon_testcase_nodes",
    "model_external_gate",
    "acceptance_candidate",
    "status",
)
_FORMAL_SUMMARY_INSERT = DIAGNOSTIC_SUMMARY_FIELDS.index("toolchain_receipt_sha256")
FORMAL_SUMMARY_FIELDS = (
    *DIAGNOSTIC_SUMMARY_FIELDS[:_FORMAL_SUMMARY_INSERT],
    "formalization_delta_sha256",
    *DIAGNOSTIC_SUMMARY_FIELDS[_FORMAL_SUMMARY_INSERT:],
)
RELEASE_SUMMARY_FIELDS = (
    *DIAGNOSTIC_SUMMARY_FIELDS[:_FORMAL_SUMMARY_INSERT],
    "release_successor",
    *DIAGNOSTIC_SUMMARY_FIELDS[_FORMAL_SUMMARY_INSERT:],
)
DIAGNOSTIC_RUN_STATUS_FIELDS = (
    "run_id",
    "mode",
    "git_head",
    "started_at",
    "finished_at",
    "last_phase",
    "exit_code",
    "source_before_sha256",
    "source_after_sha256",
    "outer_marker_sha256",
    "toolchain_receipt_sha256",
    "summary_sha256",
    "status",
)
FORMAL_RUN_STATUS_FIELDS = DIAGNOSTIC_RUN_STATUS_FIELDS[:-1] + (
    "coverage_gate_sha256",
    "candidate_manifest_sha256",
    "final_manifest_sha256",
    "status",
)
RELEASE_RUN_STATUS_FIELDS = FORMAL_RUN_STATUS_FIELDS


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


def summary_fields(mode: str) -> tuple[str, ...]:
    require(mode in RUN_MODES, "E_RUN_MODE", "unsupported coverage run mode")
    if mode == "diagnostic":
        return DIAGNOSTIC_SUMMARY_FIELDS
    if mode == "formal":
        return FORMAL_SUMMARY_FIELDS
    return RELEASE_SUMMARY_FIELDS


def run_status_fields(mode: str) -> tuple[str, ...]:
    require(mode in RUN_MODES, "E_RUN_MODE", "unsupported coverage run mode")
    return DIAGNOSTIC_RUN_STATUS_FIELDS if mode == "diagnostic" else FORMAL_RUN_STATUS_FIELDS


def successful_run_status(mode: str) -> str:
    require(mode in RUN_MODES, "E_RUN_MODE", "unsupported coverage run mode")
    return {
        "diagnostic": "diagnostic-observed",
        "formal": "formal-passed",
        "release": "release-passed",
    }[mode]


def candidate_summary_status(mode: str) -> str:
    require(mode in RUN_MODES, "E_RUN_MODE", "unsupported coverage run mode")
    return {
        "diagnostic": "diagnostic-observed",
        "formal": "formal-candidate-ready",
        "release": "release-candidate-ready",
    }[mode]


def reject_json_constant(value: str) -> None:
    reject("E_JSON", f"non-finite JSON number is forbidden: {value}")


def unique_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            reject("E_JSON", f"duplicate JSON key: {key!r}")
        result[key] = value
    return result


def regular_file(
    path: Path,
    missing_code: str,
    *,
    nonempty: bool = True,
    expected_mode: int | None = None,
) -> os.stat_result:
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
    if expected_mode is not None and stat.S_IMODE(file_stat.st_mode) != expected_mode:
        reject(
            missing_code,
            f"evidence mode must be {expected_mode:04o}: {path}",
        )
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


def exact_json_identity(actual: Any, expected: Any) -> bool:
    """Compare JSON values without Python's bool/int or int/float aliases."""

    if type(actual) is not type(expected):
        return False
    if type(actual) is dict:
        return set(actual) == set(expected) and all(
            exact_json_identity(actual[key], expected[key]) for key in actual
        )
    if type(actual) is list:
        return len(actual) == len(expected) and all(
            exact_json_identity(actual_item, expected_item)
            for actual_item, expected_item in zip(actual, expected)
        )
    return actual == expected


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


def exact_file_size(value: Any, expected: Any, code: str, label: str) -> int:
    actual_size = json_integer(value, code, label, positive=True)
    expected_size = json_integer(expected, code, f"{label} expected", positive=True)
    require(actual_size == expected_size, code, f"{label} differs")
    return actual_size


def json_sha256(value: Any, code: str, label: str) -> str:
    require(
        isinstance(value, str) and SHA256_PATTERN.fullmatch(value) is not None,
        code,
        f"{label} must be a lowercase SHA-256",
    )
    return value


def json_git_head(value: Any, code: str, label: str) -> str:
    require(
        isinstance(value, str) and GIT_HEAD_PATTERN.fullmatch(value) is not None,
        code,
        f"{label} must be a lowercase SHA-1 Git commit identity",
    )
    return value


def decimal_ratio(value: Any, code: str, label: str) -> Fraction:
    require(type(value) in (int, float), code, f"{label} must be a JSON number")
    if type(value) is float:
        require(math.isfinite(value), code, f"{label} must be finite")
    try:
        decimal = Decimal(str(value))
    except InvalidOperation:
        reject(code, f"{label} is not a decimal ratio")
    require(Decimal("0") <= decimal <= Decimal("1"), code, f"{label} must be in [0, 1]")
    return Fraction(decimal)


def exact_counter(covered: Any, total: Any, code: str, label: str) -> dict[str, Any]:
    """Return the canonical, lossless representation of a coverage counter."""

    covered_number = json_integer(covered, code, f"{label}.covered")
    total_number = json_integer(total, code, f"{label}.total", positive=True)
    require(
        covered_number <= total_number,
        code,
        f"{label}.covered exceeds total",
    )
    return {
        "covered": covered_number,
        "total": total_number,
        "fraction": f"{covered_number}/{total_number}",
    }


def validate_exact_counter(value: Any, code: str, label: str) -> dict[str, Any]:
    counter = exact_keys(value, ("covered", "total", "fraction"), code, label)
    expected = exact_counter(counter["covered"], counter["total"], code, label)
    require(counter == expected, code, f"{label}.fraction is not canonical")
    return expected


def counter_fraction(value: Any, code: str, label: str) -> Fraction:
    counter = validate_exact_counter(value, code, label)
    return Fraction(counter["covered"], counter["total"])


def counter_at_least(
    actual: Any,
    minimum: Any,
    code: str,
    label: str,
) -> bool:
    actual_counter = validate_exact_counter(actual, code, f"{label}.actual")
    minimum_counter = validate_exact_counter(minimum, code, f"{label}.minimum")
    return (
        actual_counter["covered"] * minimum_counter["total"]
        >= minimum_counter["covered"] * actual_counter["total"]
    )


def counter_at_least_ratio(
    actual: Any,
    numerator: int,
    denominator: int,
    code: str,
    label: str,
) -> bool:
    counter = validate_exact_counter(actual, code, label)
    require(
        numerator >= 0 and denominator > 0 and numerator <= denominator,
        code,
        f"{label} comparison ratio is invalid",
    )
    return counter["covered"] * denominator >= numerator * counter["total"]


def require_sha256_fields(value: dict[str, Any], fields: Iterable[str], code: str, label: str) -> None:
    for field in fields:
        json_sha256(value[field], code, f"{label}.{field}")


def validate_utc_timestamp(value: Any, code: str, label: str) -> str:
    require(
        isinstance(value, str) and UTC_TIMESTAMP_PATTERN.fullmatch(value) is not None,
        code,
        f"{label} must be a canonical UTC second timestamp",
    )
    return value


def load_env(
    path: Path,
    expected_fields: Sequence[str],
    code: str,
    label: str,
) -> dict[str, str]:
    regular_file(path, code)
    try:
        payload = path.read_bytes()
    except OSError as exc:
        reject(code, f"cannot read {label}: {exc.__class__.__name__}")
    return parse_env_bytes(payload, expected_fields, code, label)


def parse_env_bytes(
    payload: bytes,
    expected_fields: Sequence[str],
    code: str,
    label: str,
) -> dict[str, str]:
    require(b"\x00" not in payload, code, f"{label} contains NUL")
    try:
        text = payload.decode("utf-8")
    except UnicodeError:
        reject(code, f"{label} is not UTF-8")
    require(text.endswith("\n"), code, f"{label} must end with one newline")
    require("\r" not in text, code, f"{label} contains a carriage return")
    rows = text[:-1].split("\n")
    values: dict[str, str] = {}
    order: list[str] = []
    for number, row in enumerate(rows, 1):
        require(row and "=" in row, code, f"malformed {label} row {number}")
        key, value = row.split("=", 1)
        require(
            ENV_KEY_PATTERN.fullmatch(key) is not None,
            code,
            f"unsafe {label} key at row {number}",
        )
        require(key not in values, code, f"duplicate {label} key: {key}")
        require("\n" not in value and "\r" not in value, code, f"unsafe {label} value: {key}")
        order.append(key)
        values[key] = value
    require(
        tuple(order) == tuple(expected_fields),
        code,
        f"{label} fields/order differ: expected={list(expected_fields)} actual={order}",
    )
    return values


def encode_env(values: dict[str, str], fields: Sequence[str], code: str) -> bytes:
    require(tuple(values) == tuple(fields), code, "environment fields/order differ")
    rows: list[str] = []
    for key in fields:
        value = values[key]
        require(
            ENV_KEY_PATTERN.fullmatch(key) is not None
            and isinstance(value, str)
            and "\n" not in value
            and "\r" not in value
            and "\x00" not in value,
            code,
            f"unsafe environment field: {key}",
        )
        rows.append(f"{key}={value}")
    return ("\n".join(rows) + "\n").encode("utf-8")


def display_path(repo_root: Path, path: Path) -> str:
    resolved = path.resolve()
    try:
        return resolved.relative_to(repo_root).as_posix()
    except ValueError:
        return str(resolved)


def atomic_bytes(path: Path, payload: bytes, *, mode: int = 0o600) -> None:
    require(path.is_absolute(), "E_OUTPUT_PATH", "atomic output path must be absolute")
    require(mode in (0o600, 0o644), "E_OUTPUT_PATH", "atomic output mode is unsupported")
    require(path.name not in ("", ".", ".."), "E_OUTPUT_PATH", "atomic output name is unsafe")
    parent = path.parent
    try:
        parent_before = parent.lstat()
        parent_resolved = parent.resolve(strict=True)
    except OSError as exc:
        reject("E_OUTPUT_DIR", f"cannot inspect output parent: {exc.__class__.__name__}")
    require(
        stat.S_ISDIR(parent_before.st_mode)
        and not stat.S_ISLNK(parent_before.st_mode)
        and parent_resolved == parent,
        "E_OUTPUT_DIR",
        "output parent must be an existing canonical directory without symlinked ancestors",
    )
    directory_fd = -1
    descriptor = -1
    temporary_name = f".{path.name}.{os.getpid()}.{secrets.token_hex(16)}.tmp"
    temporary_identity: tuple[int, int] | None = None
    published_identity: tuple[int, int] | None = None
    published = False
    completed = False
    try:
        directory_fd = os.open(
            parent,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        bound_parent = os.fstat(directory_fd)
        require(
            stat.S_ISDIR(bound_parent.st_mode)
            and (bound_parent.st_dev, bound_parent.st_ino)
            == (parent_before.st_dev, parent_before.st_ino),
            "E_OUTPUT_DIR",
            "output parent changed while opening",
        )
        try:
            os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        except FileNotFoundError:
            pass
        else:
            reject("E_OUTPUT_EXISTS", f"refusing to overwrite output: {path}")
        descriptor = os.open(
            temporary_name,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            mode,
            dir_fd=directory_fd,
        )
        opened = os.fstat(descriptor)
        require(
            stat.S_ISREG(opened.st_mode)
            and opened.st_uid == os.getuid()
            and opened.st_nlink == 1,
            "E_OUTPUT",
            "new staged output identity/link-count differs",
        )
        temporary_identity = (opened.st_dev, opened.st_ino)
        os.fchmod(descriptor, mode)
        view = memoryview(payload)
        while view:
            written = os.write(descriptor, view)
            require(written > 0, "E_OUTPUT", "short write while staging output")
            view = view[written:]
        os.fsync(descriptor)
        staged = os.fstat(descriptor)
        require(
            stat.S_ISREG(staged.st_mode)
            and staged.st_uid == os.getuid()
            and stat.S_IMODE(staged.st_mode) == mode
            and staged.st_nlink == 1
            and staged.st_size == len(payload),
            "E_OUTPUT",
            "staged output identity/mode/link-count/size differs",
        )
        require(
            (staged.st_dev, staged.st_ino) == temporary_identity,
            "E_OUTPUT",
            "staged output inode changed while writing",
        )
        published_identity = temporary_identity
        os.close(descriptor)
        descriptor = -1
        try:
            os.link(
                temporary_name,
                path.name,
                src_dir_fd=directory_fd,
                dst_dir_fd=directory_fd,
                follow_symlinks=False,
            )
        except FileExistsError:
            reject("E_OUTPUT_EXISTS", f"refusing to overwrite output: {path}")
        published = True
        current = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        require(
            stat.S_ISREG(current.st_mode)
            and (current.st_dev, current.st_ino) == published_identity,
            "E_OUTPUT",
            "published output identity differs from staged inode",
        )
        os.fsync(directory_fd)
        temporary_current = os.stat(
            temporary_name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        require(
            (temporary_current.st_dev, temporary_current.st_ino) == temporary_identity,
            "E_OUTPUT",
            "staged temporary identity changed before cleanup",
        )
        os.unlink(temporary_name, dir_fd=directory_fd)
        temporary_identity = None
        os.fsync(directory_fd)
        parent_after = parent.lstat()
        output_after = path.lstat()
        require(
            not stat.S_ISLNK(parent_after.st_mode)
            and (parent_after.st_dev, parent_after.st_ino)
            == (bound_parent.st_dev, bound_parent.st_ino)
            and parent.resolve(strict=True) == parent
            and stat.S_ISREG(output_after.st_mode)
            and (output_after.st_dev, output_after.st_ino) == published_identity,
            "E_OUTPUT",
            "output or parent identity changed during publication",
        )
        require(
            output_after.st_uid == os.getuid()
            and stat.S_IMODE(output_after.st_mode) == mode
            and output_after.st_nlink == 1
            and output_after.st_size == len(payload),
            "E_OUTPUT",
            "published output identity/mode/link-count/size differs",
        )
        completed = True
    except CoverageXmlError:
        raise
    except OSError as exc:
        reject("E_OUTPUT", f"cannot publish output {path}: {exc.__class__.__name__}")
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if directory_fd >= 0:
            if temporary_identity is not None:
                try:
                    current = os.stat(
                        temporary_name,
                        dir_fd=directory_fd,
                        follow_symlinks=False,
                    )
                    if (current.st_dev, current.st_ino) == temporary_identity:
                        os.unlink(temporary_name, dir_fd=directory_fd)
                except FileNotFoundError:
                    pass
            if published and not completed and published_identity is not None:
                try:
                    current = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
                    if (current.st_dev, current.st_ino) == published_identity:
                        os.unlink(path.name, dir_fd=directory_fd)
                        os.fsync(directory_fd)
                except FileNotFoundError:
                    pass
            os.close(directory_fd)


def atomic_json(path: Path, value: dict[str, Any]) -> None:
    payload = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")
    atomic_bytes(path, payload)


def final_publish_bytes(
    path: Path,
    payload: bytes,
    *,
    mode: int = 0o644,
    exit_on_commit: bool = False,
) -> None:
    """Publish the canonical success marker with one final no-clobber syscall.

    Unlike ordinary evidence publication, a success marker may not become
    visible before later cleanup, durability, identity, or validation work.
    Everything is therefore staged and verified first.  A successful
    renameat2(RENAME_NOREPLACE) is the commit point and this function performs
    no filesystem operation after it returns success.
    """

    require(path.is_absolute(), "E_FINAL_OUTPUT_PATH", "final output path must be absolute")
    require(mode == 0o644, "E_FINAL_OUTPUT_PATH", "final output mode must be 0644")
    require(path.name not in ("", ".", ".."), "E_FINAL_OUTPUT_PATH", "final output name is unsafe")
    parent = path.parent
    try:
        parent_before = parent.lstat()
        parent_resolved = parent.resolve(strict=True)
    except OSError as exc:
        reject("E_FINAL_OUTPUT_DIR", f"cannot inspect final output parent: {exc.__class__.__name__}")
    require(
        stat.S_ISDIR(parent_before.st_mode)
        and not stat.S_ISLNK(parent_before.st_mode)
        and parent_resolved == parent,
        "E_FINAL_OUTPUT_DIR",
        "final output parent must be an existing canonical directory",
    )

    try:
        libc = ctypes.CDLL(None, use_errno=True)
        renameat2 = libc.renameat2
        renameat2.argtypes = (
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_uint,
        )
        renameat2.restype = ctypes.c_int
    except (AttributeError, OSError) as exc:
        reject("E_FINAL_OUTPUT_UNSUPPORTED", f"renameat2 is unavailable: {exc.__class__.__name__}")

    rename_noreplace = 1
    directory_fd = -1
    descriptor = -1
    temporary_name = f".{path.name}.{os.getpid()}.{secrets.token_hex(16)}.commit"
    committed = False
    try:
        directory_fd = os.open(
            parent,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        bound_parent = os.fstat(directory_fd)
        require(
            stat.S_ISDIR(bound_parent.st_mode)
            and (bound_parent.st_dev, bound_parent.st_ino)
            == (parent_before.st_dev, parent_before.st_ino),
            "E_FINAL_OUTPUT_DIR",
            "final output parent changed while opening",
        )
        try:
            os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        except FileNotFoundError:
            pass
        else:
            reject("E_FINAL_OUTPUT_EXISTS", f"refusing to overwrite final output: {path}")

        descriptor = os.open(
            temporary_name,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            mode,
            dir_fd=directory_fd,
        )
        os.fchmod(descriptor, mode)
        view = memoryview(payload)
        while view:
            written = os.write(descriptor, view)
            require(written > 0, "E_FINAL_OUTPUT", "short write while staging final output")
            view = view[written:]
        os.fsync(descriptor)
        staged = os.fstat(descriptor)
        require(
            stat.S_ISREG(staged.st_mode)
            and staged.st_uid == os.getuid()
            and stat.S_IMODE(staged.st_mode) == mode
            and staged.st_nlink == 1
            and staged.st_size == len(payload),
            "E_FINAL_OUTPUT",
            "staged final output identity/mode/link-count/size differs",
        )
        os.close(descriptor)
        descriptor = -1

        staged_after_close = os.stat(
            temporary_name,
            dir_fd=directory_fd,
            follow_symlinks=False,
        )
        parent_after_stage = parent.lstat()
        require(
            stat.S_ISREG(staged_after_close.st_mode)
            and (staged_after_close.st_dev, staged_after_close.st_ino)
            == (staged.st_dev, staged.st_ino)
            and staged_after_close.st_uid == os.getuid()
            and stat.S_IMODE(staged_after_close.st_mode) == mode
            and staged_after_close.st_nlink == 1
            and staged_after_close.st_size == len(payload)
            and stat.S_ISDIR(parent_after_stage.st_mode)
            and not stat.S_ISLNK(parent_after_stage.st_mode)
            and (parent_after_stage.st_dev, parent_after_stage.st_ino)
            == (bound_parent.st_dev, bound_parent.st_ino)
            and parent.resolve(strict=True) == parent,
            "E_FINAL_OUTPUT",
            "staged final output or parent changed before commit",
        )
        os.fsync(directory_fd)

        # COMMIT POINT: do not add filesystem I/O, validation, printing, or
        # cleanup after a successful call.  RENAME_NOREPLACE makes the staged
        # inode canonical without a visible pre-commit green marker.
        result = renameat2(
            directory_fd,
            os.fsencode(temporary_name),
            directory_fd,
            os.fsencode(path.name),
            rename_noreplace,
        )
        if result != 0:
            error_number = ctypes.get_errno()
            if error_number == errno.EEXIST:
                reject("E_FINAL_OUTPUT_EXISTS", f"refusing to overwrite final output: {path}")
            raise OSError(error_number, os.strerror(error_number))
        committed = True
        if exit_on_commit:
            # seal-run inherits ignored INT/TERM/HUP from the outer finalizer.
            # Define the marker as the commit authority and terminate without
            # interpreter cleanup immediately after the rename commit point.
            os._exit(0)
        return
    except CoverageXmlError:
        raise
    except OSError as exc:
        reject("E_FINAL_OUTPUT", f"cannot publish final output {path}: {exc.__class__.__name__}")
    finally:
        if not committed:
            if descriptor >= 0:
                try:
                    os.close(descriptor)
                except OSError:
                    pass
            if directory_fd >= 0:
                try:
                    os.unlink(temporary_name, dir_fd=directory_fd)
                    os.fsync(directory_fd)
                except FileNotFoundError:
                    pass
                except OSError:
                    pass
                try:
                    os.close(directory_fd)
                except OSError:
                    pass


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


def validate_threshold_evidence(value: Any, code: str = "E_THRESHOLD") -> dict[str, Any]:
    evidence = exact_keys(value, THRESHOLD_EVIDENCE_KEYS, code, "threshold evidence")
    require(
        isinstance(evidence["run_id"], str)
        and SESSION_PREFIX_PATTERN.fullmatch(evidence["run_id"]) is not None
        and len(evidence["run_id"]) <= 128,
        code,
        "threshold evidence run_id is unsafe",
    )
    require(
        isinstance(evidence["git_head"], str)
        and GIT_HEAD_PATTERN.fullmatch(evidence["git_head"]) is not None,
        code,
        "threshold evidence git_head differs",
    )
    require_sha256_fields(
        evidence,
        (field for field in THRESHOLD_EVIDENCE_KEYS if field.endswith("_sha256")),
        code,
        "threshold evidence",
    )
    require(
        evidence["threshold_predecessor_sha256"]
        == EXPECTED_DIAGNOSTIC_THRESHOLD_SHA256,
        code,
        "threshold evidence predecessor is not the frozen diagnostic successor",
    )
    return evidence


def validate_confirmed_thresholds(
    repo_root: Path,
    step1: dict[str, Any],
    step4: dict[str, Any],
) -> None:
    aggregate_observed = exact_keys(
        step4["aggregate_observed"],
        ("evidence", "line", "branch"),
        "E_THRESHOLD",
        "confirmed aggregate observation",
    )
    evidence = validate_threshold_evidence(aggregate_observed["evidence"])
    observed_line = validate_exact_counter(
        aggregate_observed["line"], "E_THRESHOLD", "confirmed aggregate line"
    )
    observed_branch = validate_exact_counter(
        aggregate_observed["branch"], "E_THRESHOLD", "confirmed aggregate branch"
    )
    reviewed = exact_keys(
        step4["aggregate_reviewed_thresholds"],
        ("line", "branch"),
        "E_THRESHOLD",
        "confirmed aggregate reviewed thresholds",
    )
    require(
        validate_exact_counter(reviewed["line"], "E_THRESHOLD", "reviewed aggregate line")
        == observed_line
        and validate_exact_counter(
            reviewed["branch"], "E_THRESHOLD", "reviewed aggregate branch"
        )
        == observed_branch,
        "E_THRESHOLD_LOWERED",
        "aggregate reviewed thresholds must exactly freeze observed counters",
    )

    critical = step4["critical_reviewed_thresholds"]
    expected_rows = step1["critical_classes"]
    require(
        type(critical) is list and len(critical) == len(expected_rows),
        "E_THRESHOLD",
        "confirmed critical threshold count differs",
    )
    for number, (actual, expected) in enumerate(zip(critical, expected_rows), 1):
        row = exact_keys(
            actual,
            ("fqcn", "module", "line", "branch"),
            "E_THRESHOLD",
            f"confirmed critical row {number}",
        )
        require(
            row["fqcn"] == expected["fqcn"] and row["module"] == expected["module"],
            "E_THRESHOLD",
            f"confirmed critical identity/order differs at row {number}",
        )
        for metric, numerator, denominator in (
            ("line", 4, 5),
            ("branch", 7, 10),
        ):
            validate_reviewed_critical_metric(
                row[metric],
                expected["fqcn"],
                expected["module"],
                metric,
                numerator,
                denominator,
                "E_THRESHOLD",
                f"confirmed critical row {number} {metric}",
            )

    review = exact_keys(
        step4["review"],
        (
            "reviewer",
            "reviewed_at",
            "diagnostic_run_id",
            "evidence_path",
            "evidence_sha256",
            "decision",
        ),
        "E_THRESHOLD",
        "confirmed threshold review",
    )
    require(
        isinstance(review["reviewer"], str)
        and review["reviewer"]
        and review["reviewer"].strip() == review["reviewer"],
        "E_THRESHOLD",
        "confirmed threshold reviewer is missing or padded",
    )
    validate_utc_timestamp(review["reviewed_at"], "E_THRESHOLD", "confirmed review time")
    require(
        review["diagnostic_run_id"] == evidence["run_id"],
        "E_THRESHOLD",
        "confirmed review diagnostic run differs from aggregate evidence",
    )
    require(
        review["decision"] == "confirm-observed-thresholds",
        "E_THRESHOLD",
        "confirmed threshold review decision differs",
    )
    evidence_path_text = review["evidence_path"]
    require(
        isinstance(evidence_path_text, str)
        and evidence_path_text
        and evidence_path_text.startswith("docs/9.3.4/")
        and "\\" not in evidence_path_text,
        "E_THRESHOLD",
        "confirmed review evidence path must be under docs/9.3.4",
    )
    evidence_pure = PurePosixPath(evidence_path_text)
    require(
        not evidence_pure.is_absolute()
        and all(part not in ("", ".", "..") for part in evidence_pure.parts),
        "E_THRESHOLD",
        "confirmed review evidence path must be repository-relative",
    )
    evidence_path = repo_root.joinpath(*evidence_pure.parts)
    require(
        sha256_file(evidence_path, "E_THRESHOLD")
        == json_sha256(
            review["evidence_sha256"], "E_THRESHOLD", "confirmed review evidence SHA"
        ),
        "E_THRESHOLD",
        "confirmed review evidence hash differs",
    )


def load_thresholds(
    repo_root: Path,
    *,
    _step4_path_override: Path | None = None,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, str]]:
    step1_path = repo_root / "scripts/v934/coverage-thresholds.json"
    step4_path = (
        _step4_path_override
        if _step4_path_override is not None
        else repo_root / "scripts/v934/step4/coverage-thresholds.json"
    )
    step1_sha = sha256_file(step1_path, "E_THRESHOLD")
    require(step1_sha == EXPECTED_STEP1_POLICY_SHA256, "E_THRESHOLD_SHA", "immutable Step 1 coverage policy differs")
    step1 = load_json(step1_path, "E_THRESHOLD")
    step4 = load_json(step4_path, "E_THRESHOLD")
    exact_keys(step4, THRESHOLD_ROOT_KEYS, "E_THRESHOLD", "Step 4 thresholds")
    require(
        type(step1.get("schema_version")) is int
        and step1.get("schema_version") == 1,
        "E_THRESHOLD",
        "Step 1 coverage schema differs",
    )
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
    require(
        type(step4.get("schema_version")) is int
        and step4.get("schema_version") == 1,
        "E_THRESHOLD",
        "Step 4 coverage schema differs",
    )
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
    if step4["status"] == "diagnostic-pending":
        require(
            step4["aggregate_observed"] is None
            and step4["aggregate_reviewed_thresholds"] is None
            and step4["critical_reviewed_thresholds"] is None,
            "E_THRESHOLD",
            "pending thresholds must not contain reviewed observations",
        )
        require(
            step4["review"]
            == {
                "reviewer": None,
                "reviewed_at": None,
                "diagnostic_run_id": None,
                "decision": "pending-all-lane-diagnostic",
            },
            "E_THRESHOLD",
            "pending threshold review differs",
        )
    else:
        validate_confirmed_thresholds(repo_root, step1, step4)
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


def validate_jacoco_execution_identity_contract(
    manifest: dict[str, Any],
    provenance: dict[str, Any] | None = None,
) -> int:
    """Validate the class-ID scope shared by exec and aggregate evidence readers."""
    require(
        manifest.get("class_id_consistency_scope")
        == EXPECTED_CLASS_ID_CONSISTENCY_SCOPE,
        "E_MANIFEST_CLASS_ID_SCOPE",
        "exec manifest class-ID consistency scope differs",
    )
    unique_execution_classes = json_integer(
        manifest.get("unique_execution_classes"),
        "E_MANIFEST",
        "unique execution classes",
        positive=True,
    )
    if provenance is None:
        return unique_execution_classes

    aggregate = provenance.get("aggregate_exec")
    require(
        isinstance(aggregate, dict),
        "E_AGGREGATE_CLASS_ID_COUNT",
        "aggregate exec class-ID count is missing",
    )
    aggregate_class_count = json_integer(
        aggregate.get("execution_class_count"),
        "E_AGGREGATE_CLASS_ID_COUNT",
        "aggregate class-ID count",
        positive=True,
    )
    require(
        aggregate_class_count == unique_execution_classes,
        "E_AGGREGATE_CLASS_ID_COUNT",
        "aggregate class-ID count differs from the exec manifest",
    )
    require(
        provenance.get("class_id_consistency_scope")
        == manifest["class_id_consistency_scope"]
        == EXPECTED_CLASS_ID_CONSISTENCY_SCOPE,
        "E_AGGREGATE_CLASS_ID_SCOPE",
        "aggregate class-ID consistency scope differs",
    )
    require(
        provenance.get("merge_semantics") == EXPECTED_AGGREGATE_MERGE_SEMANTICS,
        "E_AGGREGATE_MERGE_SEMANTICS",
        "aggregate merge semantics are not exact JaCoCo class-ID probe union",
    )
    return unique_execution_classes


def validate_manifest(
    repo_root: Path,
    manifest_path: Path,
    ledger: list[dict[str, str]],
    frozen_modules: list[str],
    *,
    _contract_path_override: Path | None = None,
    _expected_git_head: str | None = None,
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
            "class_id_consistency_scope",
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
    run_root = canonical_run_root(repo_root, run_id)
    expected_manifest_path = run_root / "exec-manifest.json"
    require(
        manifest_path.is_absolute()
        and manifest_path == expected_manifest_path
        and manifest_path.resolve(strict=True) == expected_manifest_path,
        "E_MANIFEST_PROVENANCE",
        "exec manifest is not the canonical run-owned artifact",
    )
    json_integer(manifest["not_before_ns"], "E_MANIFEST", "not_before_ns", positive=True)
    json_sha256(manifest["run_context_sha256"], "E_MANIFEST", "run context SHA")
    require(
        isinstance(manifest["git_head"], str)
        and re.fullmatch(r"[0-9a-f]{40}", manifest["git_head"]) is not None,
        "E_MANIFEST",
        "invalid manifest Git HEAD",
    )
    json_sha256(manifest["source_sha256"], "E_MANIFEST", "source SHA")

    contract_path = (
        _contract_path_override
        if _contract_path_override is not None
        else repo_root / "scripts/v934/step4/coverage-contract.json"
    )
    contract_sha = sha256_file(contract_path, "E_CONTRACT")
    context, context_sha = validate_run_context(
        repo_root,
        run_root / "run-context.json",
        run_id,
        contract_sha,
    )
    expected_git_head = (
        git_current_head(repo_root)
        if _expected_git_head is None
        else json_git_head(
            _expected_git_head,
            "E_MANIFEST_PROVENANCE",
            "expected manifest Git HEAD",
        )
    )
    require_expected_run_git_head(
        context,
        expected_git_head,
        "E_MANIFEST_PROVENANCE",
        "exec manifest",
    )
    manifest_context = validate_manifest_context_binding(
        manifest,
        context,
        context_sha,
    )
    source_context = validate_run_source_seals(
        run_root,
        context,
        context_sha,
        require_after=False,
    )

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
    validate_jacoco_execution_identity_contract(manifest)
    workspace_class_count = json_integer(manifest["workspace_class_count"], "E_MANIFEST", "workspace class count", positive=True)
    json_sha256(manifest["workspace_class_tree_sha256"], "E_MANIFEST", "workspace class tree SHA")

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
    raw_exec_validation = validate_raw_exec_replay(
        run_root,
        exec_files,
        manifest["not_before_ns"],
    )
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
        "manifest_context": manifest_context,
        "source_context": source_context,
        "raw_exec_validation": raw_exec_validation,
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
            "class_id_consistency_scope",
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
        exact_file_size(
            actual["size"],
            expected["size"],
            "E_AGGREGATE_PROVENANCE",
            f"aggregate input row {number} size",
        )
        require(
            actual["exec_file"] == expected["exec_file"]
            and actual["sha256"] == expected["sha256"],
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
    aggregate_stat = regular_file(aggregate_exec_path, "E_AGGREGATE_EXEC")
    exact_file_size(
        aggregate["size"],
        aggregate_stat.st_size,
        "E_AGGREGATE_PROVENANCE",
        "aggregate exec size",
    )
    require(
        aggregate["path"] == expected_relative
        and json_sha256(aggregate["sha256"], "E_AGGREGATE_PROVENANCE", "aggregate exec SHA")
        == sha256_file(aggregate_exec_path, "E_AGGREGATE_EXEC"),
        "E_AGGREGATE_PROVENANCE",
        "aggregate exec path/hash/size differs from verified provenance",
    )
    require(
        aggregate["session_count"] == 48
        and type(aggregate["session_count"]) is int
        and json_integer(aggregate["covered_probe_count"], "E_AGGREGATE_PROVENANCE", "aggregate covered probes", positive=True) > 0,
        "E_AGGREGATE_PROVENANCE",
        "aggregate exec verified totals differ",
    )
    validate_jacoco_execution_identity_contract(manifest=exec_manifest, provenance=provenance)
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

    def require_identity(
        label: str,
        path: Path,
        *,
        expected_mode: int | None = None,
    ) -> None:
        expected_keys = ("sha256", "size")
        if expected_mode is not None:
            expected_keys = (*expected_keys, "mode")
        identity = exact_keys(
            report[label],
            expected_keys,
            "E_REPORT_PROVENANCE",
            f"report provenance {label}",
        )
        file_stat = regular_file(
            path,
            "E_REPORT_PROVENANCE",
            expected_mode=expected_mode,
        )
        require(
            json_sha256(identity["sha256"], "E_REPORT_PROVENANCE", f"{label} SHA")
            == sha256_file(path, "E_REPORT_PROVENANCE")
            and identity["size"] == file_stat.st_size
            and type(identity["size"]) is int
            and identity["size"] > 0,
            "E_REPORT_PROVENANCE",
            f"report provenance {label} identity differs",
        )
        if expected_mode is not None:
            require(
                identity["mode"] == f"{expected_mode:04o}",
                "E_REPORT_PROVENANCE",
                f"report provenance {label} mode differs",
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
    require_identity(
        "effective_reporter_pom_receipt",
        effective_receipt_path,
        expected_mode=0o644,
    )
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
    effective_pom_stat = regular_file(effective_pom_path, "E_REPORT_PROVENANCE")
    exact_file_size(
        effective_receipt["raw_effective_pom_size"],
        effective_pom_stat.st_size,
        "E_REPORT_PROVENANCE",
        "raw effective POM size",
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
    *,
    require_compiled_class_files: bool = True,
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

    if require_compiled_class_files:
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
    *,
    require_compiled_class_files: bool = True,
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

    if require_compiled_class_files:
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
                    require_compiled_class_files=require_compiled_class_files,
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
    line_floor: Fraction,
    branch_floor: Fraction,
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
                actual = Fraction(covered, total)
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
    *,
    _threshold_path_override: Path | None = None,
    _contract_path_override: Path | None = None,
    _expected_git_head: str | None = None,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    real_directory(repo_root, "E_REPO_ROOT")
    ledger = load_ledger(repo_root)
    modules, artifact_to_module, freeze_sha = load_frozen_modules(repo_root)
    step1, step4, threshold_hashes = load_thresholds(
        repo_root,
        _step4_path_override=_threshold_path_override,
    )
    manifest, expected_sessions, input_provenance = validate_manifest(
        repo_root,
        manifest_path,
        ledger,
        modules,
        _contract_path_override=_contract_path_override,
        _expected_git_head=_expected_git_head,
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


def canonical_run_root(repo_root: Path, run_id: str) -> Path:
    require(
        isinstance(run_id, str)
        and SESSION_PREFIX_PATTERN.fullmatch(run_id) is not None
        and run_id not in (".", "..")
        and len(run_id) <= 128,
        "E_RUN_ID",
        "unsafe Step 4 run id",
    )
    root = repo_root / "target/v934-step4-coverage/runs" / run_id
    real_directory(root, "E_RUN_ROOT")
    require(
        root.resolve() == root,
        "E_RUN_ROOT",
        "Step 4 run root contains a symlinked component",
    )
    return root


def require_canonical_run_artifact_path(
    repo_root: Path,
    run_id: str,
    path: Path,
    filename: str,
    code: str,
) -> Path:
    require(
        filename in {"coverage-gate.json", "candidate-manifest.json", "final-manifest.json"},
        code,
        "unsupported formal artifact identity",
    )
    expected = canonical_run_root(repo_root.resolve(), run_id) / filename
    require(
        path.is_absolute() and path == expected and path.resolve(strict=False) == expected,
        code,
        f"formal artifact must use canonical run-owned path: {expected}",
    )
    return expected


def artifact_record(repo_root: Path, path: Path, code: str) -> dict[str, Any]:
    resolved = path.resolve()
    try:
        resolved.relative_to(repo_root.resolve())
    except (OSError, ValueError):
        reject(code, f"artifact is outside the repository: {path}")
    require(path.absolute() == resolved, code, f"artifact path is not canonical: {path}")
    path = resolved
    file_stat = regular_file(path, code)
    return {
        "path": display_path(repo_root, path),
        "sha256": sha256_file(path, code),
        "size": file_stat.st_size,
    }


def resolve_record_path(repo_root: Path, value: Any, code: str, label: str) -> Path:
    require(isinstance(value, str) and value and "\\" not in value, code, f"{label} path is unsafe")
    pure = PurePosixPath(value)
    require(
        not pure.is_absolute()
        and all(part not in ("", ".", "..") for part in pure.parts),
        code,
        f"{label} path must be repository-relative",
    )
    path = repo_root.joinpath(*pure.parts)
    try:
        resolved = path.resolve(strict=False)
        resolved.relative_to(repo_root)
    except (OSError, ValueError):
        reject(code, f"{label} path escapes the repository")
    require(path.absolute() == resolved, code, f"{label} path is not canonical")
    return resolved


def validate_artifact_record(
    repo_root: Path,
    value: Any,
    code: str,
    label: str,
    *,
    expected_path: Path | None = None,
) -> Path:
    record = exact_keys(value, ("path", "sha256", "size"), code, label)
    path = resolve_record_path(repo_root, record["path"], code, label)
    if expected_path is not None:
        require(
            path == expected_path,
            code,
            f"{label} does not reference its canonical run-owned path",
        )
    file_stat = regular_file(path, code)
    require(
        json_integer(record["size"], code, f"{label}.size", positive=True) == file_stat.st_size,
        code,
        f"{label} size differs",
    )
    require(
        json_sha256(record["sha256"], code, f"{label}.sha256") == sha256_file(path, code),
        code,
        f"{label} hash differs",
    )
    return path


def parse_source_inventory(payload: bytes, code: str) -> dict[str, Any]:
    require(
        payload.startswith(SOURCE_INVENTORY_HEADER)
        and payload.endswith(b"\n")
        and b"\r" not in payload
        and b"\0" not in payload,
        code,
        "source inventory framing/header differs",
    )
    records = payload[len(SOURCE_INVENTORY_HEADER) :].splitlines()
    require(records, code, "source inventory contains no tracked rows")
    paths: list[bytes] = []
    total_size = 0
    for number, record in enumerate(records, 1):
        fields = record.split(b"\t")
        require(
            len(fields) == 4,
            code,
            f"source inventory row {number} column count differs",
        )
        mode, relative, digest, size_text = fields
        parts = relative.split(b"/")
        require(
            mode in (b"100644", b"100755")
            and relative
            and not relative.startswith(b"/")
            and all(part not in (b"", b".", b"..") for part in parts)
            and b"\\" not in relative
            and re.fullmatch(rb"[0-9a-f]{64}", digest) is not None
            and re.fullmatch(rb"0|[1-9][0-9]*", size_text) is not None,
            code,
            f"source inventory row {number} is not canonical",
        )
        paths.append(relative)
        total_size += int(size_text, 10)
    require(
        len(paths) == len(set(paths)) and paths == sorted(paths),
        code,
        "source inventory paths are not sorted unique",
    )
    return {
        "file_count": len(records),
        "tracked_byte_count": total_size,
        "inventory_sha256": hashlib.sha256(payload).hexdigest(),
    }


def validate_run_source_seals(
    run_root: Path,
    context: dict[str, Any],
    context_sha256: str,
    *,
    require_after: bool,
) -> dict[str, Any]:
    code = "E_RUN_SOURCE"
    expected_context_sha = json_sha256(
        context_sha256,
        code,
        "canonical run-context SHA",
    )
    expected_source_sha = json_sha256(
        context.get("source_sha256"),
        code,
        "run-context source SHA",
    )
    context_payload, actual_context_sha = strict_bounded_file(
        run_root / "run-context.json",
        run_root,
        0o644,
        code,
        "canonical run context",
        maximum_size=64 * 1024,
    )
    require(
        actual_context_sha == expected_context_sha
        and parse_json_object_bytes(
            context_payload,
            code,
            "canonical run context",
        )
        == context,
        code,
        "source seal context object/hash differs from canonical run-context bytes",
    )
    before_payload, before_sha = strict_bounded_file(
        run_root / "source-before.tsv",
        run_root,
        0o644,
        code,
        "source-before inventory",
        maximum_size=MAX_SOURCE_INVENTORY_BYTES,
    )
    before_semantics = parse_source_inventory(before_payload, code)
    require(
        before_sha == expected_source_sha
        and before_semantics["inventory_sha256"] == expected_source_sha,
        code,
        "source-before inventory is not the exact run-context source seal",
    )
    result: dict[str, Any] = {
        "run_context_sha256": expected_context_sha,
        "git_head": json_git_head(
            context.get("git_head"), code, "run-context Git HEAD"
        ),
        "source_sha256": expected_source_sha,
        "not_before_ns": json_integer(
            context.get("not_before_ns"),
            code,
            "run-context not-before",
            positive=True,
        ),
        "file_count": before_semantics["file_count"],
        "tracked_byte_count": before_semantics["tracked_byte_count"],
        "source_before_sha256": before_sha,
        "source_after_sha256": None,
        "status": "source-before-exact",
    }
    if require_after:
        after_payload, after_sha = strict_bounded_file(
            run_root / "source-after.tsv",
            run_root,
            0o644,
            code,
            "source-after inventory",
            maximum_size=MAX_SOURCE_INVENTORY_BYTES,
        )
        after_semantics = parse_source_inventory(after_payload, code)
        require(
            after_payload == before_payload
            and after_sha == before_sha == expected_source_sha
            and after_semantics == before_semantics,
            code,
            "source-before/source-after are not the same exact run-context source seal",
        )
        result["source_after_sha256"] = after_sha
        result["status"] = "exact-before-after-context-bound"
    return result


def validate_manifest_context_binding(
    manifest: dict[str, Any],
    context: dict[str, Any],
    context_sha256: str,
) -> dict[str, Any]:
    code = "E_MANIFEST_PROVENANCE"
    expected = {
        "run_context_sha256": json_sha256(
            context_sha256,
            code,
            "canonical run-context SHA",
        ),
        "git_head": json_git_head(context.get("git_head"), code, "run-context Git HEAD"),
        "source_sha256": json_sha256(
            context.get("source_sha256"),
            code,
            "run-context source SHA",
        ),
        "not_before_ns": json_integer(
            context.get("not_before_ns"),
            code,
            "run-context not-before",
            positive=True,
        ),
    }
    actual = {key: manifest.get(key) for key in expected}
    require(
        actual == expected,
        code,
        "exec manifest run-context/source/Git/not-before binding differs from the canonical run context",
    )
    return {**expected, "status": "exact-canonical-context"}


def require_expected_run_git_head(
    context: dict[str, Any],
    expected_git_head: Any,
    code: str,
    label: str,
) -> str:
    expected = json_git_head(expected_git_head, code, f"{label} expected Git HEAD")
    actual = json_git_head(context.get("git_head"), code, f"{label} context Git HEAD")
    require(
        actual == expected,
        code,
        f"{label} context Git HEAD differs from the expected validation commit",
    )
    return actual


def validate_raw_exec_replay(
    run_root: Path,
    exec_rows: list[dict[str, Any]],
    not_before_ns: int,
) -> dict[str, Any]:
    code = "E_RAW_EXEC_REPLAY"
    boundary = json_integer(
        not_before_ns,
        code,
        "raw exec not-before",
        positive=True,
    )
    require(
        isinstance(exec_rows, list) and len(exec_rows) == 23,
        code,
        "raw exec replay requires exact 23 manifest rows",
    )
    names = [row.get("exec_file") if isinstance(row, dict) else None for row in exec_rows]
    require(
        all(
            isinstance(name, str)
            and re.fullmatch(r"jacoco-(?:ut|it)-?[a-z0-9-]*\.exec", name)
            is not None
            for name in names
        )
        and len(set(names)) == 23,
        code,
        "raw exec replay manifest names are not exact safe unique identities",
    )
    exec_root = run_root / "exec"
    real_directory(exec_root, code)
    require(
        exec_root.is_absolute()
        and exec_root.parent == run_root
        and exec_root.resolve(strict=True) == exec_root,
        code,
        "raw exec directory is not the canonical run-owned directory",
    )

    directory_fd = -1
    verified_rows: list[dict[str, Any]] = []
    try:
        directory_before = exec_root.lstat()
        directory_fd = os.open(
            exec_root,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        bound_directory = os.fstat(directory_fd)
        require(
            stat.S_ISDIR(bound_directory.st_mode)
            and (bound_directory.st_dev, bound_directory.st_ino)
            == (directory_before.st_dev, directory_before.st_ino)
            and sorted(os.listdir(directory_fd)) == sorted(names),
            code,
            "raw exec directory identity or exact file set differs",
        )
        for number, (name, row) in enumerate(zip(names, exec_rows), 1):
            expected_sha = json_sha256(
                row.get("sha256"), code, f"raw exec row {number} SHA"
            )
            expected_size = json_integer(
                row.get("size"),
                code,
                f"raw exec row {number} size",
                positive=True,
            )
            expected_mtime = json_integer(
                row.get("mtime_ns"),
                code,
                f"raw exec row {number} mtime",
                positive=True,
            )
            require(
                expected_size <= MAX_RAW_EXEC_BYTES and expected_mtime >= boundary,
                code,
                f"raw exec row {number} size/freshness boundary differs",
            )
            descriptor = -1
            try:
                descriptor = os.open(
                    name,
                    os.O_RDONLY
                    | getattr(os, "O_CLOEXEC", 0)
                    | getattr(os, "O_NOFOLLOW", 0)
                    | getattr(os, "O_NONBLOCK", 0),
                    dir_fd=directory_fd,
                )
                file_before = os.fstat(descriptor)
                require(
                    stat.S_ISREG(file_before.st_mode)
                    and file_before.st_uid == os.getuid()
                    and file_before.st_nlink == 1
                    and not file_before.st_mode & 0o111
                    and not file_before.st_mode & 0o002
                    and file_before.st_size == expected_size
                    and file_before.st_mtime_ns == expected_mtime,
                    code,
                    f"raw exec row {number} file identity/size/mtime is unsafe or differs",
                )
                digest = hashlib.sha256()
                byte_count = 0
                while True:
                    chunk = os.read(descriptor, 1024 * 1024)
                    if not chunk:
                        break
                    byte_count += len(chunk)
                    require(
                        byte_count <= expected_size,
                        code,
                        f"raw exec row {number} grew while hashing",
                    )
                    digest.update(chunk)
                file_after = os.fstat(descriptor)
                path_after = os.stat(name, dir_fd=directory_fd, follow_symlinks=False)
                stable_fields = (
                    "st_dev",
                    "st_ino",
                    "st_mode",
                    "st_uid",
                    "st_nlink",
                    "st_size",
                    "st_mtime_ns",
                    "st_ctime_ns",
                )
                actual_sha = digest.hexdigest()
                require(
                    byte_count == expected_size
                    and actual_sha == expected_sha
                    and all(
                        getattr(file_before, field) == getattr(file_after, field)
                        == getattr(path_after, field)
                        for field in stable_fields
                    ),
                    code,
                    f"raw exec row {number} bytes or stable inode identity differs",
                )
                verified_rows.append(
                    {
                        "exec_file": name,
                        "sha256": actual_sha,
                        "size": expected_size,
                        "mtime_ns": expected_mtime,
                    }
                )
            except CoverageXmlError:
                raise
            except OSError as exc:
                reject(
                    code,
                    f"cannot verify raw exec row {number}: {exc.__class__.__name__}",
                )
            finally:
                if descriptor >= 0:
                    os.close(descriptor)
        directory_after = exec_root.lstat()
        require(
            (directory_after.st_dev, directory_after.st_ino)
            == (bound_directory.st_dev, bound_directory.st_ino)
            and sorted(os.listdir(directory_fd)) == sorted(names),
            code,
            "raw exec directory changed during exact byte replay",
        )
    except CoverageXmlError:
        raise
    except OSError as exc:
        reject(code, f"cannot verify raw exec directory: {exc.__class__.__name__}")
    finally:
        if directory_fd >= 0:
            os.close(directory_fd)

    tree_payload = "".join(
        f"{row['exec_file']}\t{row['sha256']}\t{row['size']}\t{row['mtime_ns']}\n"
        for row in verified_rows
    ).encode("utf-8")
    return {
        "mode": "exact-retained-raw-exec-byte-replay",
        "identity_policy": "canonical-dirfd-nofollow-stable-inode",
        "freshness_policy": "exact-manifest-mtime-at-or-after-not-before",
        "exec_count": len(verified_rows),
        "byte_tree_sha256": hashlib.sha256(tree_payload).hexdigest(),
        "status": "verified",
    }


def validate_run_context(
    repo_root: Path,
    path: Path,
    run_id: str,
    contract_sha256: str,
) -> tuple[dict[str, Any], str]:
    run_root = canonical_run_root(repo_root.resolve(), run_id)
    expected_path = run_root / "run-context.json"
    require(
        path.is_absolute()
        and path == expected_path
        and path.resolve(strict=True) == expected_path,
        "E_RUN_CONTEXT",
        "Step 4 run context is not the canonical run-owned artifact",
    )
    payload, context_sha = strict_bounded_file(
        path,
        run_root,
        0o644,
        "E_RUN_CONTEXT",
        "Step 4 run context",
        maximum_size=64 * 1024,
    )
    context = parse_json_object_bytes(payload, "E_RUN_CONTEXT", "Step 4 run context")
    exact_keys(
        context,
        (
            "schema_version",
            "kind",
            "authority_kind",
            "run_id",
            "git_head",
            "contract_sha256",
            "source_sha256",
            "not_before_ns",
            "started_at",
        ),
        "E_RUN_CONTEXT",
        "Step 4 run context",
    )
    require(
        context["schema_version"] == 1
        and type(context["schema_version"]) is int
        and context["kind"] == "v934-step4-run-context"
        and context["authority_kind"] == "step4-coverage"
        and context["run_id"] == run_id,
        "E_RUN_CONTEXT",
        "Step 4 run context identity differs",
    )
    require(
        isinstance(context["git_head"], str)
        and GIT_HEAD_PATTERN.fullmatch(context["git_head"]) is not None,
        "E_RUN_CONTEXT",
        "Step 4 run context Git HEAD differs",
    )
    require(
        json_sha256(context["contract_sha256"], "E_RUN_CONTEXT", "run context contract SHA")
        == contract_sha256,
        "E_RUN_CONTEXT",
        "Step 4 run context contract hash differs",
    )
    json_sha256(context["source_sha256"], "E_RUN_CONTEXT", "run context source SHA")
    not_before_ns = json_integer(
        context["not_before_ns"],
        "E_RUN_CONTEXT",
        "run context not-before",
        positive=True,
    )
    require(
        path.lstat().st_mtime_ns >= not_before_ns,
        "E_RUN_CONTEXT",
        "Step 4 run context predates its not-before boundary",
    )
    validate_utc_timestamp(context["started_at"], "E_RUN_CONTEXT", "run context started_at")
    return context, context_sha


def validate_workflow_contract(
    repo_root: Path,
    mode: str,
    *,
    _contract_path_override: Path | None = None,
) -> tuple[Path, str]:
    contract_path = (
        _contract_path_override
        if _contract_path_override is not None
        else repo_root / "scripts/v934/step4/coverage-contract.json"
    )
    contract = load_json(contract_path, "E_CONTRACT")
    expected_status = "diagnostic-ready" if mode == "diagnostic" else "formal-ready"
    tooling = contract.get("tooling_manifest")
    successor = contract.get("threshold_successor")
    require(type(tooling) is dict and type(successor) is dict, "E_CONTRACT", "coverage workflow contract is incomplete")
    require(
        diagnostic_capsule.PROFILE == GIT_SAFE_DIAGNOSTIC_PROFILE
        and exact_json_identity(
            successor.get("frozen_diagnostic_capsule"),
            GIT_SAFE_DIAGNOSTIC_CAPSULE_POLICY,
        ),
        "E_CONTRACT_WORKFLOW",
        "coverage contract Git-safe frozen diagnostic capsule policy differs",
    )
    require(
        contract.get("schema_version") == 1
        and type(contract.get("schema_version")) is int
        and contract.get("kind") == "v934-step4-coverage-contract"
        and contract.get("status") == expected_status
        and tooling.get("publication_status") == expected_status
        and successor.get("required_status_for_diagnostic") == "diagnostic-pending"
        and successor.get("required_status_for_exit") == "confirmed",
        "E_CONTRACT_WORKFLOW",
        f"coverage contract is not in exact {mode} workflow state",
    )
    return contract_path, sha256_file(contract_path, "E_CONTRACT")


def strict_bounded_file(
    path: Path,
    expected_parent: Path,
    expected_mode: int,
    code: str,
    label: str,
    *,
    maximum_size: int = 4096,
) -> tuple[bytes, str]:
    require(
        path.is_absolute()
        and expected_parent.is_absolute()
        and path.parent == expected_parent
        and path.name not in ("", ".", ".."),
        code,
        f"{label} path is not canonical",
    )
    directory_fd = -1
    descriptor = -1
    try:
        parent_before = expected_parent.lstat()
        require(
            stat.S_ISDIR(parent_before.st_mode)
            and not stat.S_ISLNK(parent_before.st_mode)
            and expected_parent.resolve(strict=True) == expected_parent,
            code,
            f"{label} parent is unsafe",
        )
        directory_fd = os.open(
            expected_parent,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        bound_parent = os.fstat(directory_fd)
        require(
            (bound_parent.st_dev, bound_parent.st_ino)
            == (parent_before.st_dev, parent_before.st_ino),
            code,
            f"{label} parent changed while opening",
        )
        descriptor = os.open(
            path.name,
            os.O_RDONLY
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0)
            | getattr(os, "O_NONBLOCK", 0),
            dir_fd=directory_fd,
        )
        file_stat = os.fstat(descriptor)
        require(
            stat.S_ISREG(file_stat.st_mode)
            and file_stat.st_uid == os.getuid()
            and stat.S_IMODE(file_stat.st_mode) == expected_mode
            and file_stat.st_nlink == 1
            and 0 < file_stat.st_size <= maximum_size,
            code,
            f"{label} file identity/mode/link-count/size is unsafe",
        )
        chunks: list[bytes] = []
        size = 0
        while True:
            chunk = os.read(descriptor, maximum_size + 1 - size)
            if not chunk:
                break
            chunks.append(chunk)
            size += len(chunk)
            require(size <= maximum_size, code, f"{label} exceeds its size limit")
        data = b"".join(chunks)
        file_after = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        parent_after = expected_parent.lstat()
        require(
            (file_after.st_dev, file_after.st_ino)
            == (file_stat.st_dev, file_stat.st_ino)
            and file_after.st_size == file_stat.st_size == len(data)
            and file_after.st_uid == os.getuid()
            and stat.S_IMODE(file_after.st_mode) == expected_mode
            and file_after.st_nlink == 1
            and (parent_after.st_dev, parent_after.st_ino)
            == (bound_parent.st_dev, bound_parent.st_ino)
            and expected_parent.resolve(strict=True) == expected_parent,
            code,
            f"{label} changed while reading",
        )
        return data, hashlib.sha256(data).hexdigest()
    except CoverageXmlError:
        raise
    except OSError as exc:
        reject(code, f"cannot strictly read {label}: {exc.__class__.__name__}")
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if directory_fd >= 0:
            os.close(directory_fd)


def strict_directory_names(path: Path, expected: Sequence[str], code: str, label: str) -> None:
    real_directory(path, code)
    require(path.resolve(strict=True) == path, code, f"{label} contains a symlinked ancestor")
    directory_fd = -1
    try:
        before = path.lstat()
        directory_fd = os.open(
            path,
            os.O_RDONLY
            | getattr(os, "O_DIRECTORY", 0)
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
        )
        bound = os.fstat(directory_fd)
        require(
            (before.st_dev, before.st_ino) == (bound.st_dev, bound.st_ino),
            code,
            f"{label} changed while opening",
        )
        actual = sorted(os.listdir(directory_fd))
        require(
            actual == sorted(expected),
            code,
            f"{label} exact file set differs: expected={sorted(expected)} actual={actual}",
        )
        after = path.lstat()
        require(
            (after.st_dev, after.st_ino) == (bound.st_dev, bound.st_ino),
            code,
            f"{label} changed while listing",
        )
    except CoverageXmlError:
        raise
    except OSError as exc:
        reject(code, f"cannot list {label}: {exc.__class__.__name__}")
    finally:
        if directory_fd >= 0:
            os.close(directory_fd)


def parse_json_object_bytes(payload: bytes, code: str, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            payload.decode("utf-8", errors="strict"),
            object_pairs_hook=unique_json_object,
            parse_constant=reject_json_constant,
        )
    except CoverageXmlError:
        raise
    except (UnicodeError, json.JSONDecodeError, ValueError) as exc:
        reject(code, f"invalid {label} JSON: {exc.__class__.__name__}")
    require(type(value) is dict, code, f"{label} root must be an object")
    return value


def validate_child_lifecycle(run_root: Path, run_id: str) -> dict[str, Any]:
    code = "E_CHILD_LIFECYCLE"
    ready_root = run_root / "child-ready"
    completion_root = run_root / "child-lifecycle"
    strict_directory_names(
        ready_root,
        tuple(f"{child}.json" for child in CHILD_NAMES),
        code,
        "child-ready directory",
    )
    strict_directory_names(
        completion_root,
        tuple(f"{child}-complete.env" for child in CHILD_NAMES),
        code,
        "child-lifecycle directory",
    )

    children: list[dict[str, Any]] = []
    for child in CHILD_NAMES:
        ready_data, ready_sha = strict_bounded_file(
            ready_root / f"{child}.json",
            ready_root,
            0o600,
            code,
            f"child-ready {child}",
        )
        ready = parse_json_object_bytes(ready_data, code, f"child-ready {child}")
        exact_keys(ready, CHILD_READY_FIELDS, code, f"child-ready {child}")
        require(
            type(ready["schema_version"]) is int
            and ready["schema_version"] == 1
            and ready["kind"] == "v934-step4-child-ready"
            and ready["run_id"] == run_id
            and ready["child"] == child
            and type(ready["pid"]) is int
            and ready["pid"] > 1
            and type(ready["pgid"]) is int
            and ready["pgid"] == ready["pid"]
            and type(ready["sid"]) is int
            and ready["sid"] == ready["pid"]
            and type(ready["starttime_ticks"]) is int
            and ready["starttime_ticks"] > 0
            and isinstance(ready["boot_id"], str)
            and BOOT_ID_PATTERN.fullmatch(ready["boot_id"]) is not None
            and ready["status"] == "ready",
            code,
            f"child-ready typed identity differs: {child}",
        )

        completion_data, completion_sha = strict_bounded_file(
            completion_root / f"{child}-complete.env",
            completion_root,
            0o644,
            code,
            f"child completion {child}",
        )
        completion = parse_env_bytes(
            completion_data,
            CHILD_COMPLETION_FIELDS,
            code,
            f"child completion {child}",
        )
        require(
            completion
            == {
                "run_id": run_id,
                "child": child,
                "leader_pid": str(ready["pid"]),
                "leader_sid": str(ready["sid"]),
                "leader_starttime_ticks": str(ready["starttime_ticks"]),
                "boot_id": ready["boot_id"],
                "leader_exit_code": "0",
                "leader_reaped": "1",
                "ready_receipt_sha256": ready_sha,
                "process_group_residue": "0",
                "status": "passed",
            },
            code,
            f"child completion identity/residue differs: {child}",
        )
        children.append(
            {
                "child": child,
                "complete_sha256": completion_sha,
                "leader_pid": ready["pid"],
                "leader_sid": ready["sid"],
                "leader_starttime_ticks": ready["starttime_ticks"],
                "boot_id": ready["boot_id"],
                "leader_reaped": 1,
                "process_group_residue": 0,
                "ready_receipt_sha256": ready_sha,
                "status": "passed",
            }
        )

    expected_manifest = {
        "schema_version": 1,
        "kind": "v934-step4-child-lifecycle",
        "run_id": run_id,
        "child_count": len(CHILD_NAMES),
        "children": children,
        "status": "passed",
    }
    manifest = load_json(run_root / "child-lifecycle.json", code)
    require(
        exact_json_identity(manifest, expected_manifest),
        code,
        "child lifecycle manifest differs from typed ready/completion recomputation",
    )
    return manifest


def validate_git_relative_path(value: Any, code: str, label: str) -> str:
    require(
        isinstance(value, str)
        and value
        and "\\" not in value
        and "\n" not in value
        and "\r" not in value
        and "\t" not in value,
        code,
        f"{label} is unsafe",
    )
    pure = PurePosixPath(value)
    require(
        not pure.is_absolute()
        and all(part not in ("", ".", "..") for part in pure.parts),
        code,
        f"{label} is not a canonical repository-relative path",
    )
    return value


def git_changed_paths(repo_root: Path, parent: str, current: str) -> list[str]:
    require_git_ancestor(repo_root, parent, current)
    process = frozen_git_process(
        repo_root,
        [
            "--no-pager",
            "diff",
            "--name-only",
            "--no-renames",
            "--no-ext-diff",
            "-z",
            parent,
            current,
            "--",
        ],
    )
    require(process.returncode == 0, "E_FORMAL_DELTA_GIT", "formal Git diff failed")
    changed: list[str] = []
    for raw_path in process.stdout.split(b"\0"):
        if not raw_path:
            continue
        try:
            relative = raw_path.decode("utf-8", errors="strict")
        except UnicodeError:
            reject("E_FORMAL_DELTA_PATH", "formal changed path is not UTF-8")
        validate_git_relative_path(relative, "E_FORMAL_DELTA_PATH", "formal changed path")
        require(relative not in changed, "E_FORMAL_DELTA_PATH", "duplicate formal changed path")
        changed.append(relative)
    require(changed == sorted(changed), "E_FORMAL_DELTA_PATH", "formal changed paths are not sorted")
    return changed


def require_direct_single_parent(repo_root: Path, parent: str, current: str) -> None:
    process = frozen_git_process(
        repo_root,
        ["rev-list", "--parents", "-n", "1", current],
    )
    expected = f"{current} {parent}\n".encode("ascii")
    require(
        process.returncode == 0 and process.stdout == expected,
        "E_FORMAL_DELTA_PARENT",
        "formal commit must be the diagnostic commit's direct single-parent child",
    )


def validate_formal_delta_policy(policy: Any, changed_paths: list[str]) -> dict[str, Any]:
    code = "E_FORMAL_DELTA_POLICY"
    policy = exact_keys(
        policy,
        (
            "parent_git_head_source",
            "diagnostic_threshold_sha256",
            "repository_identity",
            "required_exact_paths",
            "allowed_exact_paths",
            "allowed_path_prefixes",
            "other_changes",
        ),
        code,
        "formalization delta policy",
    )
    json_sha256(
        policy["diagnostic_threshold_sha256"],
        code,
        "formalization diagnostic threshold SHA",
    )
    repository_identity = exact_keys(
        policy["repository_identity"],
        FORMAL_REPOSITORY_IDENTITY_POLICY,
        code,
        "formalization repository identity policy",
    )
    require(
        repository_identity == FORMAL_REPOSITORY_IDENTITY_POLICY,
        code,
        "formalization repository identity policy differs",
    )
    for field in ("required_exact_paths", "allowed_exact_paths", "allowed_path_prefixes"):
        values = policy[field]
        require(
            type(values) is list
            and values
            and all(isinstance(value, str) for value in values),
            code,
            f"{field} must be a non-empty string list",
        )
        require(len(values) == len(set(values)), code, f"{field} contains duplicates")
        for number, value in enumerate(values, 1):
            validate_git_relative_path(value, code, f"{field}[{number}]")
    required = policy["required_exact_paths"]
    allowed_exact = policy["allowed_exact_paths"]
    require(
        not any(
            relative in CDIAG_ONLY_STEP5_TOOLING_PATHS
            for relative in (*required, *allowed_exact)
        ),
        code,
        "Cdiag-only Step 5 tooling bindings are forbidden during formalization",
    )
    require(
        policy["parent_git_head_source"] == "aggregate_observed.evidence.git_head"
        and policy["diagnostic_threshold_sha256"]
        == EXPECTED_DIAGNOSTIC_THRESHOLD_SHA256
        and policy["repository_identity"] == FORMAL_REPOSITORY_IDENTITY_POLICY
        and tuple(policy["required_exact_paths"]) == FORMALIZATION_EXACT_PATHS
        and tuple(policy["allowed_exact_paths"]) == FORMALIZATION_EXACT_PATHS
        and tuple(policy["allowed_path_prefixes"])
        == FORMALIZATION_ALLOWED_PREFIXES
        and policy["other_changes"] == "forbidden-requires-new-diagnostic",
        code,
        "formalization delta policy frozen values differ",
    )
    allowed_prefixes = policy["allowed_path_prefixes"]
    require(
        all(relative in allowed_exact for relative in required),
        code,
        "required formalization paths are not exact-allowed",
    )
    forbidden = [
        relative
        for relative in changed_paths
        if relative not in allowed_exact
        and not any(
            relative.startswith(prefix) and len(relative) > len(prefix)
            for prefix in allowed_prefixes
        )
    ]
    require(
        not forbidden,
        "E_FORMAL_DELTA_FORBIDDEN",
        f"formal changes outside the allowlist require a new diagnostic: {forbidden}",
    )
    missing = [relative for relative in required if relative not in changed_paths]
    require(
        not missing,
        "E_FORMAL_DELTA_MISSING",
        f"required formalization changes are missing: {missing}",
    )
    return policy


def validate_formalization_delta(
    repo_root: Path,
    run_root: Path,
    context: dict[str, Any],
    step4: dict[str, Any],
    contract_path: Path,
) -> dict[str, Any]:
    code = "E_FORMAL_DELTA"
    receipt = load_json(run_root / "formalization-delta.json", code)
    exact_keys(
        receipt,
        (
            "schema_version",
            "kind",
            "parent_git_head",
            "current_git_head",
            "repository_identity_policy",
            "repository_identity",
            "changed_paths",
            "required_exact_paths",
            "allowed_exact_paths",
            "allowed_path_prefixes",
            "workflow_state",
            "status",
        ),
        code,
        "formalization delta receipt",
    )
    require(
        type(receipt["schema_version"]) is int
        and receipt["schema_version"] == 1
        and receipt["kind"] == "v934-step4-formal-delta"
        and receipt["workflow_state"] == "formal"
        and receipt["status"] == "passed",
        code,
        "formalization delta receipt identity/status differs",
    )
    current_head = json_git_head(
        receipt["current_git_head"], code, "formalization current Git HEAD"
    )
    parent_head = json_git_head(
        receipt["parent_git_head"], code, "formalization parent Git HEAD"
    )
    require(current_head == context["git_head"], code, "formal run Git identity differs")
    evidence = validate_threshold_evidence(
        step4["aggregate_observed"]["evidence"],
        code,
    )
    require(
        parent_head == evidence["git_head"] and parent_head != current_head,
        code,
        "formal receipt is not bound to the diagnostic parent",
    )
    require(
        git_current_head(repo_root) == current_head,
        code,
        "formal receipt is not bound to current committed HEAD",
    )
    require_direct_single_parent(repo_root, parent_head, current_head)
    contract = load_json(contract_path, "E_CONTRACT")
    successor = contract.get("threshold_successor")
    require(type(successor) is dict, "E_CONTRACT", "threshold successor is missing")
    changed_paths = receipt["changed_paths"]
    require(
        type(changed_paths) is list
        and all(isinstance(relative, str) for relative in changed_paths)
        and changed_paths == sorted(changed_paths)
        and len(changed_paths) == len(set(changed_paths)),
        code,
        "formal changed paths must be a sorted unique list",
    )
    for number, relative in enumerate(changed_paths, 1):
        validate_git_relative_path(relative, code, f"formal changed path {number}")
    require(
        git_changed_paths(repo_root, parent_head, current_head) == changed_paths,
        code,
        "formal changed paths differ from exact Git recomputation",
    )
    policy = validate_formal_delta_policy(
        successor.get("formalization_delta"), changed_paths
    )
    require(
        policy["diagnostic_threshold_sha256"] == evidence["threshold_predecessor_sha256"],
        "E_FORMAL_DELTA_POLICY",
        "formalization policy is not bound to the diagnostic predecessor threshold",
    )
    repository_policy = exact_keys(
        receipt["repository_identity_policy"],
        FORMAL_REPOSITORY_IDENTITY_POLICY,
        code,
        "formal receipt repository identity policy",
    )
    require(
        repository_policy == policy["repository_identity"],
        code,
        "formal receipt repository identity policy differs from contract",
    )
    repository_identity = exact_keys(
        receipt["repository_identity"],
        FORMAL_REPOSITORY_IDENTITY_FIELDS,
        code,
        "formal receipt repository identity",
    )
    require(
        repository_identity["object_format"] == "sha1"
        and repository_identity["shallow_repository"] is False
        and type(repository_identity["replace_ref_count"]) is int
        and repository_identity["replace_ref_count"] == 0
        and repository_identity["nonempty_grafts"] is False
        and repository_identity["nonempty_info_attributes"] is False
        and repository_identity["index_flags"] == INDEX_FLAGS_IDENTITY_POLICY
        and repository_identity["head_index_worktree"]
        == HEAD_INDEX_WORKTREE_IDENTITY_POLICY
        and repository_identity["commit_relation"] == "direct-single-parent"
        and type(repository_identity["parent_count"]) is int
        and repository_identity["parent_count"] == 1
        and type(repository_identity["source_file_count"]) is int
        and repository_identity["source_file_count"] > 0,
        code,
        "formal receipt repository identity typed values differ",
    )
    require_sha256_fields(
        repository_identity,
        (
            "head_tree_sha256",
            "index_stage_sha256",
            "index_flags_sha256",
            "filter_attributes_sha256",
            "worktree_git_clean_blob_sha256",
            "source_sha256",
        ),
        code,
        "formal receipt repository identity",
    )

    source_tool_path = repo_root / "scripts/v934/step4/coverage_tool.py"
    regular_file(source_tool_path, "E_FORMAL_DELTA_HOOK")
    require(
        source_tool_path.resolve(strict=True) == source_tool_path,
        "E_FORMAL_DELTA_HOOK",
        "formal delta source validator path is not canonical",
    )
    try:
        specification = importlib.util.spec_from_file_location(
            "_v934_step4_coverage_tool_hook",
            source_tool_path,
        )
        require(
            specification is not None and specification.loader is not None,
            "E_FORMAL_DELTA_HOOK",
            "formal delta source validator cannot be loaded",
        )
        source_tool = importlib.util.module_from_spec(specification)
        specification.loader.exec_module(source_tool)
        source_validator = getattr(
            source_tool,
            "validate_formalization_delta_receipt",
            None,
        )
        require(
            callable(source_validator),
            "E_FORMAL_DELTA_HOOK",
            "formal delta source validator hook is unavailable",
        )
        expected = source_validator(repo_root, copy.deepcopy(receipt))
    except CoverageXmlError:
        raise
    except Exception as exc:
        reject(
            "E_FORMAL_DELTA_RECOMPUTE",
            f"source formal delta recomputation failed: {exc.__class__.__name__}",
        )
    require(
        type(expected) is dict and receipt == expected,
        code,
        "formalization delta receipt differs from exact source recomputation",
    )
    return receipt


def validate_supporting_env(run_root: Path) -> None:
    model = load_env(
        run_root / "model-gate.env",
        (
            "profile",
            "aggregate_exec_sha256",
            "bundle_line_minimum",
            "bundle_branch_minimum",
            "semantic_scale_line_minimum",
            "semantic_scale_branch_minimum",
            "status",
        ),
        "E_RUN_EVIDENCE",
        "model gate",
    )
    require(
        model
        == {
            "profile": "v934-coverage-model-check",
            "aggregate_exec_sha256": sha256_file(
                run_root / "report/jacoco-aggregate.exec", "E_RUN_EVIDENCE"
            ),
            "bundle_line_minimum": "0.77",
            "bundle_branch_minimum": "0.62",
            "semantic_scale_line_minimum": "1.00",
            "semantic_scale_branch_minimum": "1.00",
            "status": "passed",
        },
        "E_RUN_EVIDENCE",
        "model gate values differ",
    )
    cleanup = load_env(
        run_root / "cleanup.env",
        ("container_residue", "volume_residue", "network_residue", "status"),
        "E_RUN_EVIDENCE",
        "cleanup evidence",
    )
    require(
        cleanup
        == {
            "container_residue": "0",
            "volume_residue": "0",
            "network_residue": "0",
            "status": "passed",
        },
        "E_RUN_EVIDENCE",
        "run-owned resource cleanup is incomplete",
    )
    sensitive = load_env(
        run_root / "sensitive-scan.env",
        ("patterns", "text_extensions", "status"),
        "E_RUN_EVIDENCE",
        "sensitive scan",
    )
    require(
        sensitive
        == {
            "patterns": "5",
            "text_extensions": "log,env,json,tsv,xml",
            "status": "passed",
        },
        "E_RUN_EVIDENCE",
        "sensitive scan evidence differs",
    )


def validate_summary_artifact_hashes(
    run_root: Path,
    summary: dict[str, str],
    mode: str,
    *,
    _contract_path_override: Path | None = None,
) -> None:
    bindings = {
        "coverage_contract_sha256": (
            _contract_path_override
            if _contract_path_override is not None
            else run_root.parents[3] / "scripts/v934/step4/coverage-contract.json"
        ),
        "outer_marker_sha256": run_root / "run-context.json",
        "class_universe_sha256": run_root / "class-universe.json",
        "child_lifecycle_sha256": run_root / "child-lifecycle.json",
        "toolchain_receipt_sha256": run_root / "toolchain-receipt.json",
        "toolchain_pre_compile_seal_replay_sha256": run_root / "toolchain-replay/pre-compile-seal.env",
        "toolchain_post_children_replay_sha256": run_root / "toolchain-replay/post-children.env",
        "toolchain_reporter_pre_replay_sha256": run_root / "report/toolchain-replay-pre.json",
        "toolchain_reporter_post_replay_sha256": run_root / "report/toolchain-replay-post.json",
        "toolchain_post_reporter_replay_sha256": run_root / "toolchain-replay/post-reporter.env",
        "toolchain_post_model_replay_sha256": run_root / "toolchain-replay/post-model.env",
        "report_inventory_sha256": run_root / "report-inventory.json",
        "exec_manifest_sha256": run_root / "exec-manifest.json",
        "aggregate_exec_sha256": run_root / "report/jacoco-aggregate.exec",
        "aggregate_provenance_sha256": run_root / "report/aggregate-provenance.json",
        "report_provenance_sha256": run_root / "report/report-provenance.json",
        "coverage_observation_sha256": run_root / "coverage-observation.json",
        "successor_overlay_negative_sha256": run_root / "negative/successor-overlay-probes.tsv",
        "coverage_contract_negative_sha256": run_root / "negative/coverage-contract.json",
        "toolchain_receipt_negative_sha256": run_root / "negative/toolchain-receipt.json",
        "effective_reporter_pom_negative_sha256": run_root / "negative/effective-reporter-pom.json",
        "report_inventory_negative_sha256": run_root / "negative/report-inventory-probes.tsv",
        "coverage_exec_negative_sha256": run_root / "negative/coverage-exec/negative-result.json",
        "coverage_xml_negative_sha256": run_root / "negative/coverage-xml/negative-result.json",
        "coverage_xml_generic_negative_sha256": run_root / "negative/coverage-xml-generic/negative-result.json",
        "run_log_lifecycle_negative_sha256": run_root / "negative/run-log-lifecycle.txt",
        "model_gate_sha256": run_root / "model-gate.env",
        "cleanup_sha256": run_root / "cleanup.env",
        "sensitive_scan_sha256": run_root / "sensitive-scan.env",
    }
    formalization_path = run_root / "formalization-delta.json"
    if mode == "formal":
        bindings["formalization_delta_sha256"] = formalization_path
    else:
        require(
            not formalization_path.exists() and not formalization_path.is_symlink(),
            "E_RUN_SUMMARY",
            f"{mode} run must not contain formalization-delta.json",
        )
    for field, path in bindings.items():
        require(
            json_sha256(summary[field], "E_RUN_SUMMARY", field)
            == sha256_file(path, "E_RUN_SUMMARY"),
            "E_RUN_SUMMARY",
            f"summary binding differs: {field}",
        )


def run_binding_records(
    repo_root: Path,
    run_root: Path,
    mode: str,
) -> dict[str, dict[str, Any]]:
    paths = {
        "run_context": run_root / "run-context.json",
        "source_before": run_root / "source-before.tsv",
        "source_after": run_root / "source-after.tsv",
        "summary": run_root / "summary.env",
        "class_universe": run_root / "class-universe.json",
        "child_lifecycle": run_root / "child-lifecycle.json",
        "toolchain_receipt": run_root / "toolchain-receipt.json",
        "report_inventory": run_root / "report-inventory.json",
        "exec_manifest": run_root / "exec-manifest.json",
        "aggregate_exec": run_root / "report/jacoco-aggregate.exec",
        "aggregate_provenance": run_root / "report/aggregate-provenance.json",
        "report_provenance": run_root / "report/report-provenance.json",
        "aggregate_xml": run_root / "report/jacoco-aggregate/jacoco.xml",
        "coverage_observation": run_root / "coverage-observation.json",
        "model_gate": run_root / "model-gate.env",
        "cleanup": run_root / "cleanup.env",
        "sensitive_scan": run_root / "sensitive-scan.env",
        "coverage_exec_negative": run_root / "negative/coverage-exec/negative-result.json",
        "coverage_xml_negative": run_root / "negative/coverage-xml/negative-result.json",
    }
    if mode == "formal":
        paths["formalization_delta"] = run_root / "formalization-delta.json"
    return {name: artifact_record(repo_root, path, "E_RUN_EVIDENCE") for name, path in paths.items()}


def validate_run_status(
    run_root: Path,
    run_id: str,
    mode: str,
    context: dict[str, Any],
    summary: dict[str, str],
    expected_artifact_hashes: dict[str, str] | None = None,
) -> tuple[dict[str, str], str]:
    status_path = run_root / "run-status.env"
    fields = run_status_fields(mode)
    status = load_env(status_path, fields, "E_RUN_STATUS", "run status")
    expected_status = successful_run_status(mode)
    require(
        status["run_id"] == run_id
        and status["mode"] == mode
        and status["git_head"] == context["git_head"]
        and status["started_at"] == context["started_at"]
        and status["last_phase"] == "completed"
        and status["exit_code"] == "0"
        and status["source_before_sha256"] == context["source_sha256"]
        and status["source_after_sha256"] == context["source_sha256"]
        and status["outer_marker_sha256"] == sha256_file(run_root / "run-context.json", "E_RUN_STATUS")
        and status["toolchain_receipt_sha256"]
        == sha256_file(run_root / "toolchain-receipt.json", "E_RUN_STATUS")
        and status["summary_sha256"] == sha256_file(run_root / "summary.env", "E_RUN_STATUS")
        and status["status"] == expected_status,
        "E_RUN_STATUS",
        "run status identity or success state differs",
    )
    finished_at = validate_utc_timestamp(status["finished_at"], "E_RUN_STATUS", "finished_at")
    require(finished_at >= context["started_at"], "E_RUN_STATUS", "run finished before it started")
    if mode in ARTIFACT_MODES:
        require_sha256_fields(
            status,
            ("coverage_gate_sha256", "candidate_manifest_sha256", "final_manifest_sha256"),
            "E_RUN_STATUS",
            "formal run status",
        )
        canonical_hashes = {
            "coverage_gate_sha256": sha256_file(
                run_root / "coverage-gate.json", "E_RUN_STATUS"
            ),
            "candidate_manifest_sha256": sha256_file(
                run_root / "candidate-manifest.json", "E_RUN_STATUS"
            ),
            "final_manifest_sha256": sha256_file(
                run_root / "final-manifest.json", "E_RUN_STATUS"
            ),
        }
        require(
            all(status[key] == value for key, value in canonical_hashes.items()),
            "E_RUN_STATUS",
            "formal run status does not bind canonical gate/candidate/final",
        )
        if expected_artifact_hashes is not None:
            require(
                set(expected_artifact_hashes) == set(canonical_hashes),
                "E_RUN_STATUS",
                "expected formal artifact hash set differs",
            )
            require(
                all(status[key] == value for key, value in expected_artifact_hashes.items()),
                "E_RUN_STATUS",
                "formal run status artifact hashes differ",
            )
    return status, sha256_file(status_path, "E_RUN_STATUS")


def validate_run_data(
    repo_root: Path,
    run_id: str,
    *,
    mode: str,
    require_run_status: bool,
    expected_artifact_hashes: dict[str, str] | None = None,
    _threshold_path_override: Path | None = None,
    _contract_path_override: Path | None = None,
    _expected_git_head: str | None = None,
    _preseal: bool = False,
) -> dict[str, Any]:
    require(mode in RUN_MODES, "E_RUN_MODE", "unsupported coverage run mode")
    require(
        not _preseal or not require_run_status,
        "E_RUN_STATUS",
        "preseal validation cannot require an already-published status",
    )
    require(
        mode in ARTIFACT_MODES or expected_artifact_hashes is None,
        "E_RUN_STATUS",
        "artifact hashes are formal/release-only",
    )
    repo_root = repo_root.resolve()
    real_directory(repo_root, "E_REPO_ROOT")
    run_root = canonical_run_root(repo_root, run_id)
    threshold_path = (
        _threshold_path_override
        if _threshold_path_override is not None
        else repo_root / "scripts/v934/step4/coverage-thresholds.json"
    )
    _, step4, threshold_hashes = load_thresholds(
        repo_root,
        _step4_path_override=_threshold_path_override,
    )
    expected_threshold_status = "diagnostic-pending" if mode == "diagnostic" else "confirmed"
    if step4["status"] != expected_threshold_status:
        code = {
            "diagnostic": "E_DIAGNOSTIC_THRESHOLD_STATUS",
            "formal": "E_FORMAL_THRESHOLD_STATUS",
            "release": "E_RELEASE_THRESHOLD_STATUS",
        }[mode]
        reject(code, f"{mode} run requires threshold status {expected_threshold_status}")

    contract_path, contract_sha = validate_workflow_contract(
        repo_root,
        mode,
        _contract_path_override=_contract_path_override,
    )
    context, context_sha = validate_run_context(
        repo_root,
        run_root / "run-context.json",
        run_id,
        contract_sha,
    )
    expected_git_head = (
        git_current_head(repo_root)
        if _expected_git_head is None
        else json_git_head(
            _expected_git_head,
            "E_RUN_CONTEXT",
            "expected run Git HEAD",
        )
    )
    require_expected_run_git_head(
        context,
        expected_git_head,
        "E_RUN_CONTEXT",
        "Step 4 run",
    )
    source_context = validate_run_source_seals(
        run_root,
        context,
        context_sha,
        require_after=True,
    )
    summary = load_env(
        run_root / "summary.env",
        summary_fields(mode),
        "E_RUN_SUMMARY",
        "run summary",
    )
    expected_summary_status = candidate_summary_status(mode)
    expected_acceptance = "not-generated" if mode == "diagnostic" else "required"
    require(
        summary["run_id"] == run_id
        and summary["mode"] == mode
        and summary["git_head"] == context["git_head"]
        and summary["threshold_status"] == expected_threshold_status
        and summary["source_before_sha256"] == context["source_sha256"]
        and summary["source_after_sha256"] == context["source_sha256"]
        and summary["coverage_contract_sha256"] == contract_sha
        and summary["acceptance_candidate"] == expected_acceptance
        and summary["status"] == expected_summary_status,
        "E_RUN_SUMMARY",
        "run summary identity or state differs",
    )
    if mode == "release":
        require(
            summary["release_successor"] == RELEASE_SUCCESSOR_MARKER,
            "E_RUN_SUMMARY",
            "release successor marker differs",
        )
    require(
        {
            "exec_files": summary["exec_files"],
            "sessions": summary["sessions"],
            "required_reports": summary["required_reports"],
            "required_structural_reports": summary["required_structural_reports"],
            "required_testcase_nodes": summary["required_testcase_nodes"],
            "addon_reports": summary["addon_reports"],
            "addon_testcase_nodes": summary["addon_testcase_nodes"],
            "model_external_gate": summary["model_external_gate"],
        }
        == {
            "exec_files": "23",
            "sessions": "48",
            "required_reports": "774",
            "required_structural_reports": "59",
            "required_testcase_nodes": "5709",
            "addon_reports": "2",
            "addon_testcase_nodes": "6",
            "model_external_gate": "passed",
        },
        "E_RUN_SUMMARY",
        "run summary exact counts differ",
    )
    child_lifecycle = validate_child_lifecycle(run_root, run_id)
    formalization_delta: dict[str, Any] | None = None
    if mode == "formal":
        formalization_delta = validate_formalization_delta(
            repo_root,
            run_root,
            context,
            step4,
            contract_path,
        )
    validate_summary_artifact_hashes(
        run_root,
        summary,
        mode,
        _contract_path_override=_contract_path_override,
    )
    validate_supporting_env(run_root)
    manifest_path = run_root / "exec-manifest.json"
    aggregate_exec_path = run_root / "report/jacoco-aggregate.exec"
    aggregate_provenance_path = run_root / "report/aggregate-provenance.json"
    report_provenance_path = run_root / "report/report-provenance.json"
    xml_path = run_root / "report/jacoco-aggregate/jacoco.xml"
    stored_observation = load_json(run_root / "coverage-observation.json", "E_OBSERVATION")
    recomputed_observation = observe_data(
        repo_root,
        xml_path,
        manifest_path,
        aggregate_exec_path,
        aggregate_provenance_path,
        report_provenance_path,
        _threshold_path_override=_threshold_path_override,
        _contract_path_override=_contract_path_override,
        _expected_git_head=expected_git_head,
    )
    require(
        exact_json_identity(stored_observation, recomputed_observation),
        "E_OBSERVATION_DRIFT",
        "stored coverage observation differs from exact recomputation",
    )
    require(
        stored_observation["run_id"] == run_id
        and stored_observation["provenance"]["report_provenance"]["git_head"]
        == context["git_head"],
        "E_OBSERVATION_DRIFT",
        "coverage observation run/Git identity differs",
    )

    status: dict[str, str] | None = None
    status_sha: str | None = None
    status_path = run_root / "run-status.env"
    if _preseal:
        require(
            not status_path.exists() and not status_path.is_symlink(),
            "E_RUN_STATUS_EXISTS",
            "preseal validation requires an unpublished run status",
        )
    elif require_run_status or status_path.exists() or status_path.is_symlink():
        status, status_sha = validate_run_status(
            run_root,
            run_id,
            mode,
            context,
            summary,
            expected_artifact_hashes,
        )
    evidence = {
        "run_id": run_id,
        "git_head": context["git_head"],
        "source_sha256": context["source_sha256"],
        "summary_sha256": sha256_file(run_root / "summary.env", "E_RUN_SUMMARY"),
        "observation_sha256": sha256_file(
            run_root / "coverage-observation.json", "E_OBSERVATION"
        ),
        "coverage_contract_sha256": contract_sha,
        "threshold_sha256": threshold_hashes["step4_successor_sha256"],
        "exec_manifest_sha256": sha256_file(manifest_path, "E_MANIFEST"),
        "aggregate_exec_sha256": sha256_file(aggregate_exec_path, "E_AGGREGATE_EXEC"),
        "aggregate_xml_sha256": sha256_file(xml_path, "E_XML_INVALID"),
        "workspace_class_tree_sha256": load_json(manifest_path, "E_MANIFEST")[
            "workspace_class_tree_sha256"
        ],
    }
    # Diagnostic threshold evidence must bind its seal.  Formal gate evidence
    # is deliberately preseal-stable because formal run-status binds the gate,
    # candidate, and final in the opposite direction (avoiding a hash cycle).
    if status_sha is not None and mode == "diagnostic":
        evidence["run_status_sha256"] = status_sha
    return {
        "mode": mode,
        "run_root": run_root,
        "context": context,
        "source_context": source_context,
        "summary": summary,
        "run_status": status,
        "child_lifecycle": child_lifecycle,
        "formalization_delta": formalization_delta,
        "observation": stored_observation,
        "raw_exec_validation": stored_observation["provenance"][
            "raw_exec_validation"
        ],
        "evidence": evidence,
        "bindings": run_binding_records(repo_root, run_root, mode),
        "threshold": step4,
        "threshold_path": threshold_path,
    }


def canonical_git_safe_diagnostic_attestation_path(
    repo_root: Path,
    run_id: str,
) -> Path:
    return canonical_run_root(repo_root.resolve(), run_id) / "git-safe-diagnostic-attestation.json"


def git_safe_semantic_observation(
    observation: dict[str, Any],
    *,
    code: str,
) -> dict[str, Any]:
    require(
        all(key in observation for key in GIT_SAFE_SEMANTIC_OBSERVATION_KEYS),
        code,
        "coverage observation lacks a required Git-safe semantic field",
    )
    value = {
        key: observation.get(key)
        for key in GIT_SAFE_SEMANTIC_OBSERVATION_KEYS
    }
    exact_keys(value, GIT_SAFE_SEMANTIC_OBSERVATION_KEYS, code, "Git-safe semantic observation")
    try:
        diagnostic_capsule.validate_no_runtime_metadata(value)
    except diagnostic_capsule.CapsuleError as exc:
        reject(code, f"Git-safe semantic observation is unsafe ({exc.code})")
    return value


def build_git_safe_diagnostic_attestation_data(
    repo_root: Path,
    run_id: str,
) -> dict[str, Any]:
    """Project a fully validated source run into retention-safe capsule data.

    This function intentionally invokes the normal source-side diagnostic
    validator first.  The resulting attestation contains only hash bindings
    for raw execution evidence; it never serializes execution bytes, runtime
    closure, unstructured output, or process/host metadata.
    """

    validation = validate_run_data(
        repo_root,
        run_id,
        mode="diagnostic",
        require_run_status=True,
    )
    evidence = validation["evidence"]
    source_context = validation["source_context"]
    observation = validation["observation"]
    provenance = observation.get("provenance")
    require(type(provenance) is dict, "E_GIT_SAFE_ATTESTATION", "coverage observation provenance is missing")
    raw_execution = validation["raw_exec_validation"]
    exact_keys(
        raw_execution,
        (
            "mode",
            "identity_policy",
            "freshness_policy",
            "exec_count",
            "byte_tree_sha256",
            "status",
        ),
        "E_GIT_SAFE_ATTESTATION",
        "source raw execution validation",
    )
    require(
        raw_execution["mode"] == "exact-retained-raw-exec-byte-replay"
        and raw_execution["exec_count"] == 23
        and type(raw_execution["exec_count"]) is int
        and raw_execution["status"] == "verified",
        "E_GIT_SAFE_ATTESTATION",
        "source raw execution validation is not verified",
    )
    aggregate_provenance = provenance.get("aggregate_provenance")
    aggregate_xml = provenance.get("aggregate_xml")
    report_inventory = observation.get("report_inventory")
    require(
        type(aggregate_provenance) is dict
        and type(aggregate_xml) is dict
        and type(report_inventory) is dict,
        "E_GIT_SAFE_ATTESTATION",
        "coverage observation lacks capsule-safe provenance",
    )
    require(
        aggregate_provenance.get("merge_semantics")
        == EXPECTED_AGGREGATE_MERGE_SEMANTICS,
        "E_GIT_SAFE_ATTESTATION",
        "aggregate merge semantics differs",
    )
    workspace_class_count = json_integer(
        report_inventory.get("workspace_bytecode_class_count"),
        "E_GIT_SAFE_ATTESTATION",
        "workspace bytecode class count",
        positive=True,
    )
    xml_size = json_integer(
        aggregate_xml.get("size"),
        "E_GIT_SAFE_ATTESTATION",
        "aggregate XML size",
        positive=True,
    )
    identity = {
        "run_id": evidence["run_id"],
        "git_head": evidence["git_head"],
        "source_sha256": evidence["source_sha256"],
        "run_context_sha256": source_context["run_context_sha256"],
        "run_status_sha256": evidence.get("run_status_sha256"),
        "summary_sha256": evidence["summary_sha256"],
        "coverage_contract_sha256": evidence["coverage_contract_sha256"],
        "threshold_predecessor_sha256": evidence["threshold_sha256"],
        "observation_sha256": evidence["observation_sha256"],
    }
    require(
        identity["run_id"] == run_id
        and identity["threshold_predecessor_sha256"]
        == EXPECTED_DIAGNOSTIC_THRESHOLD_SHA256,
        "E_GIT_SAFE_ATTESTATION",
        "diagnostic attestation identity differs",
    )
    json_git_head(identity["git_head"], "E_GIT_SAFE_ATTESTATION", "diagnostic Git head")
    for field, value in identity.items():
        if field not in ("run_id", "git_head"):
            json_sha256(value, "E_GIT_SAFE_ATTESTATION", f"diagnostic identity {field}")
    execution_attestation = {
        "mode": "source-validated-hash-only",
        "retention": "no-execution-bytes",
        "exec_count": 23,
        "session_count": 48,
        "byte_tree_sha256": raw_execution["byte_tree_sha256"],
        "aggregate_exec_sha256": evidence["aggregate_exec_sha256"],
        "merge_semantics": EXPECTED_AGGREGATE_MERGE_SEMANTICS,
        "status": "verified",
    }
    json_sha256(
        execution_attestation["byte_tree_sha256"],
        "E_GIT_SAFE_ATTESTATION",
        "source execution byte tree SHA",
    )
    source_attestation = {
        "class_universe_sha256": provenance.get("fresh_class_universe_sha256"),
        "workspace_class_tree_sha256": evidence["workspace_class_tree_sha256"],
        "workspace_bytecode_class_count": workspace_class_count,
        "toolchain_receipt_sha256": provenance.get("toolchain_receipt_sha256"),
        "coverage_ledger_sha256": provenance.get("coverage_ledger_sha256"),
    }
    for field, value in source_attestation.items():
        if field != "workspace_bytecode_class_count":
            json_sha256(value, "E_GIT_SAFE_ATTESTATION", f"source attestation {field}")
    require(
        source_attestation["coverage_ledger_sha256"] == EXPECTED_LEDGER_SHA256,
        "E_GIT_SAFE_ATTESTATION",
        "source attestation ledger differs",
    )
    result = {
        "schema_version": 1,
        "kind": "v934-step4-git-safe-diagnostic-attestation",
        "profile": GIT_SAFE_DIAGNOSTIC_PROFILE,
        "status": "verified",
        "identity": identity,
        "execution_attestation": execution_attestation,
        "xml": {
            "sha256": evidence["aggregate_xml_sha256"],
            "size": xml_size,
            "deterministic_report_replay_count": 2,
        },
        "source_attestation": source_attestation,
        "semantic_observation": git_safe_semantic_observation(
            observation,
            code="E_GIT_SAFE_ATTESTATION",
        ),
    }
    return result


def load_git_safe_diagnostic_attestation(
    path: Path,
    *,
    code: str,
) -> tuple[dict[str, Any], bytes]:
    try:
        attestation, payload = diagnostic_capsule.load_attestation(path)
    except diagnostic_capsule.CapsuleError as exc:
        reject(code, f"Git-safe diagnostic attestation rejected ({exc.code})")
    semantic = attestation.get("semantic_observation")
    require(type(semantic) is dict, code, "Git-safe semantic observation is missing")
    exact_keys(
        semantic,
        GIT_SAFE_SEMANTIC_OBSERVATION_KEYS,
        code,
        "Git-safe semantic observation",
    )
    return attestation, payload


def ensure_git_safe_diagnostic_attestation(
    repo_root: Path,
    run_id: str,
) -> tuple[Path, dict[str, Any], str]:
    repo_root = repo_root.resolve()
    expected = build_git_safe_diagnostic_attestation_data(repo_root, run_id)
    path = canonical_git_safe_diagnostic_attestation_path(repo_root, run_id)
    expected_payload = diagnostic_capsule.canonical_json(expected)
    if path.exists() or path.is_symlink():
        actual, payload = load_git_safe_diagnostic_attestation(
            path,
            code="E_GIT_SAFE_ATTESTATION",
        )
        require(
            payload == expected_payload and exact_json_identity(actual, expected),
            "E_GIT_SAFE_ATTESTATION",
            "existing Git-safe diagnostic attestation differs from source recomputation",
        )
    else:
        atomic_bytes(path, expected_payload, mode=0o644)
        actual, payload = load_git_safe_diagnostic_attestation(
            path,
            code="E_GIT_SAFE_ATTESTATION",
        )
        require(
            payload == expected_payload and exact_json_identity(actual, expected),
            "E_GIT_SAFE_ATTESTATION",
            "published Git-safe diagnostic attestation differs",
        )
    return path, actual, hashlib.sha256(payload).hexdigest()


def attest_git_safe_diagnostic_command(args: argparse.Namespace) -> None:
    _path, attestation, digest = ensure_git_safe_diagnostic_attestation(
        args.repo_root,
        args.run_id,
    )
    print(
        json.dumps(
            {
                "kind": "v934-step4-git-safe-diagnostic-attestation-result",
                "profile": attestation["profile"],
                "run_id": attestation["identity"]["run_id"],
                "attestation_sha256": digest,
                "status": "passed",
            },
            sort_keys=True,
            separators=(",", ":"),
        )
    )


def build_git_safe_diagnostic_capsule_data(
    repo_root: Path,
    run_id: str,
    archive: Path,
    manifest: Path,
) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    attestation_path, attestation, attestation_sha = ensure_git_safe_diagnostic_attestation(
        repo_root,
        run_id,
    )
    xml_path = canonical_run_root(repo_root, run_id) / "report/jacoco-aggregate/jacoco.xml"
    xml_stat = regular_file(xml_path, "E_GIT_SAFE_CAPSULE")
    xml_sha = sha256_file(xml_path, "E_GIT_SAFE_CAPSULE")
    require(
        attestation["xml"]
        == {
            "sha256": xml_sha,
            "size": xml_stat.st_size,
            "deterministic_report_replay_count": 2,
        },
        "E_GIT_SAFE_CAPSULE",
        "Git-safe attestation/XML binding differs before capsule build",
    )
    try:
        capsule = diagnostic_capsule.build_capsule(
            attestation_path,
            xml_path,
            archive,
            manifest,
        )
    except diagnostic_capsule.CapsuleError as exc:
        reject("E_GIT_SAFE_CAPSULE", f"Git-safe diagnostic capsule rejected ({exc.code})")
    return {
        "schema_version": 2,
        "kind": "v934-step4-git-safe-diagnostic-capsule-build",
        "profile": GIT_SAFE_DIAGNOSTIC_PROFILE,
        "run_id": attestation["identity"]["run_id"],
        "attestation_sha256": attestation_sha,
        "archive_sha256": capsule["archive_sha256"],
        "status": "passed",
    }


def build_git_safe_diagnostic_capsule_command(args: argparse.Namespace) -> None:
    result = build_git_safe_diagnostic_capsule_data(
        args.repo_root,
        args.run_id,
        args.archive,
        args.manifest,
    )
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))


def validate_diagnostic_command(args: argparse.Namespace) -> None:
    validation = validate_run_data(
        args.repo_root,
        args.run_id,
        mode="diagnostic",
        require_run_status=True,
    )
    print(
        "[v934-coverage-xml] DIAGNOSTIC VALID "
        f"run={args.run_id} observation={validation['evidence']['observation_sha256']}"
    )


def seal_run_data(
    repo_root: Path,
    run_id: str,
    mode: str,
    *,
    _exit_on_commit: bool = False,
) -> dict[str, Any]:
    require(mode in RUN_MODES, "E_RUN_MODE", "unsupported seal mode")
    repo_root = repo_root.resolve()
    validation = validate_run_data(
        repo_root,
        run_id,
        mode=mode,
        require_run_status=False,
        _preseal=True,
    )
    run_root = validation["run_root"]
    artifact_hashes: dict[str, str] = {}
    if mode in ARTIFACT_MODES:
        gate_path = run_root / "coverage-gate.json"
        candidate_path = run_root / "candidate-manifest.json"
        final_path = run_root / "final-manifest.json"
        gate = validate_coverage_gate(repo_root, gate_path, mode)
        candidate = validate_acceptance_candidate(repo_root, candidate_path, mode)
        final = validate_acceptance_final(repo_root, final_path, mode)
        require(
            gate["run_id"] == candidate["run_id"] == final["run_id"] == run_id,
            "E_ACCEPTANCE_ARTIFACT",
            f"{mode} gate/candidate/final run identities differ",
        )
        artifact_hashes = {
            "coverage_gate_sha256": sha256_file(gate_path, "E_COVERAGE_GATE"),
            "candidate_manifest_sha256": sha256_file(
                candidate_path, "E_ACCEPTANCE_CANDIDATE"
            ),
            "final_manifest_sha256": sha256_file(final_path, "E_ACCEPTANCE_FINAL"),
        }

    context = validation["context"]
    finished_at = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    validate_utc_timestamp(finished_at, "E_RUN_STATUS", "seal finished_at")
    require(
        finished_at >= context["started_at"],
        "E_RUN_STATUS",
        "run cannot be sealed before its start time",
    )
    status_value = successful_run_status(mode)
    status_values = {
        "run_id": run_id,
        "mode": mode,
        "git_head": context["git_head"],
        "started_at": context["started_at"],
        "finished_at": finished_at,
        "last_phase": "completed",
        "exit_code": "0",
        "source_before_sha256": context["source_sha256"],
        "source_after_sha256": context["source_sha256"],
        "outer_marker_sha256": sha256_file(
            run_root / "run-context.json", "E_RUN_STATUS"
        ),
        "toolchain_receipt_sha256": sha256_file(
            run_root / "toolchain-receipt.json", "E_RUN_STATUS"
        ),
        "summary_sha256": sha256_file(run_root / "summary.env", "E_RUN_STATUS"),
        **artifact_hashes,
        "status": status_value,
    }
    fields = run_status_fields(mode)
    payload = encode_env(status_values, fields, "E_RUN_STATUS")
    status_path = run_root / "run-status.env"
    result = {
        "schema_version": 1,
        "kind": "v934-step4-run-seal",
        "mode": mode,
        "run_id": run_id,
        "git_head": context["git_head"],
        "run_status": {
            "path": display_path(repo_root, status_path),
            "sha256": hashlib.sha256(payload).hexdigest(),
            "size": len(payload),
            "status": status_value,
        },
        "status": "sealed",
    }
    # The success marker is the final irreversible evidence action.  Do not
    # read or revalidate run evidence after this no-clobber publication.
    final_publish_bytes(
        status_path,
        payload,
        mode=0o644,
        exit_on_commit=_exit_on_commit,
    )
    return result


def seal_run_command(args: argparse.Namespace) -> None:
    # Intentionally quiet: run-status publication inside seal_run_data is the
    # final potentially failing I/O/validation action of the success path.
    seal_run_data(
        args.repo_root,
        args.run_id,
        args.mode,
        _exit_on_commit=True,
    )


def observation_exact_counter(value: Any, code: str, label: str) -> dict[str, Any]:
    counter = exact_keys(
        value,
        ("missed", "covered", "total", "ratio", "fraction"),
        code,
        label,
    )
    missed = json_integer(counter["missed"], code, f"{label}.missed")
    covered = json_integer(counter["covered"], code, f"{label}.covered")
    total = json_integer(counter["total"], code, f"{label}.total", positive=True)
    require(missed + covered == total, code, f"{label} total arithmetic differs")
    exact = exact_counter(covered, total, code, label)
    require(counter["fraction"] == exact["fraction"], code, f"{label} fraction differs")
    require(
        type(counter["ratio"]) is float
        and math.isfinite(counter["ratio"])
        and counter["ratio"] == round(covered / total, 12),
        code,
        f"{label} display ratio differs",
    )
    return exact


def exact_not_applicable_counter(value: Any, code: str, label: str) -> dict[str, Any]:
    counter = exact_keys(value, ("covered", "total", "fraction"), code, label)
    covered = json_integer(counter["covered"], code, f"{label}.covered")
    total = json_integer(counter["total"], code, f"{label}.total")
    require(
        covered == 0 and total == 0 and counter["fraction"] is None,
        code,
        f"{label} must be the canonical not-applicable counter",
    )
    return {"covered": 0, "total": 0, "fraction": None}


def validate_critical_observation_row(
    value: Any,
    expected: dict[str, Any],
    code: str,
    label: str,
) -> dict[str, Any]:
    row = exact_keys(
        value,
        (
            "fqcn",
            "module",
            "artifact_id",
            "line",
            "branch",
            "candidate_floor_outcome",
        ),
        code,
        label,
    )
    expected_module = expected["module"]
    expected_artifact_id = PurePosixPath(expected_module).name
    require(
        row["fqcn"] == expected["fqcn"]
        and row["module"] == expected_module
        and row["artifact_id"] == expected_artifact_id,
        code,
        f"{label} identity differs",
    )
    require(
        row["candidate_floor_outcome"]
        in ("at-or-above-floor", "below-floor"),
        code,
        f"{label} candidate floor outcome differs",
    )
    return row


def require_critical_candidate_floor_outcome(
    row: dict[str, Any],
    metric_outcomes: Sequence[str],
    code: str,
    label: str,
) -> None:
    expected = (
        "below-floor"
        if "below-floor" in metric_outcomes
        else "at-or-above-floor"
    )
    require(
        row["candidate_floor_outcome"] == expected,
        code,
        f"{label} candidate floor outcome does not match its metrics",
    )


def observation_critical_counter(
    value: Any,
    fqcn: str,
    module: str,
    metric: str,
    floor: Fraction,
    code: str,
    zero_code: str,
    label: str,
) -> tuple[dict[str, Any], str]:
    counter = exact_keys(
        value,
        (
            "missed",
            "covered",
            "total",
            "ratio",
            "fraction",
            "floor",
            "outcome",
            "gap",
        ),
        code,
        label,
    )
    missed = json_integer(counter["missed"], code, f"{label}.missed")
    covered = json_integer(counter["covered"], code, f"{label}.covered")
    total = json_integer(counter["total"], code, f"{label}.total")
    require(missed + covered == total, code, f"{label} total arithmetic differs")
    require(
        type(counter["floor"]) is float
        and math.isfinite(counter["floor"])
        and decimal_ratio(counter["floor"], code, f"{label}.floor") == floor,
        code,
        f"{label} candidate floor differs",
    )
    if total == 0:
        require(
            (fqcn, module, metric) in CRITICAL_NOT_APPLICABLE_METRICS,
            zero_code,
            f"{label} zero counter is not an approved structural metric",
        )
        require(
            missed == 0
            and covered == 0
            and counter["ratio"] is None
            and counter["fraction"] is None
            and counter["outcome"] == "not-applicable"
            and counter["gap"] is None,
            code,
            f"{label} not-applicable observation differs",
        )
        return (
            {"covered": 0, "total": 0, "fraction": None},
            "not-applicable-zero-total-only",
        )

    exact = exact_counter(covered, total, code, label)
    require(counter["fraction"] == exact["fraction"], code, f"{label} fraction differs")
    require(
        type(counter["ratio"]) is float
        and math.isfinite(counter["ratio"])
        and counter["ratio"] == round(covered / total, 12),
        code,
        f"{label} display ratio differs",
    )
    actual = Fraction(covered, total)
    expected_outcome = "at-or-above-floor" if actual >= floor else "below-floor"
    expected_gap = 0.0 if actual >= floor else round(float(floor - actual), 12)
    require(
        type(counter["gap"]) is float and math.isfinite(counter["gap"]),
        code,
        f"{label}.gap must be a finite JSON float",
    )
    gap = counter["gap"]
    require(
        counter["outcome"] == expected_outcome and gap == expected_gap,
        code,
        f"{label} floor outcome differs",
    )
    return exact, "required-positive-total"


def validate_reviewed_critical_metric(
    value: Any,
    fqcn: str,
    module: str,
    metric: str,
    numerator: int,
    denominator: int,
    code: str,
    label: str,
) -> tuple[dict[str, Any], str]:
    metric_value = exact_keys(
        value,
        ("applicability", "observed", "minimum"),
        code,
        label,
    )
    applicability = metric_value["applicability"]
    require(
        applicability
        in ("required-positive-total", "not-applicable-zero-total-only"),
        code,
        f"{label} applicability differs",
    )
    if applicability == "not-applicable-zero-total-only":
        require(
            (fqcn, module, metric) in CRITICAL_NOT_APPLICABLE_METRICS,
            code,
            f"{label} is not an approved structural metric",
        )
        observed = exact_not_applicable_counter(
            metric_value["observed"], code, f"{label} observed"
        )
        require(
            metric_value["minimum"] is None,
            code,
            f"{label} not-applicable minimum must be null",
        )
        return observed, applicability

    observed = validate_exact_counter(
        metric_value["observed"], code, f"{label} observed"
    )
    minimum = validate_exact_counter(
        metric_value["minimum"], code, f"{label} minimum"
    )
    require(
        observed == minimum,
        "E_THRESHOLD_LOWERED" if code == "E_THRESHOLD" else code,
        f"{label} minimum differs",
    )
    require(
        counter_at_least_ratio(observed, numerator, denominator, code, label),
        "E_THRESHOLD_FLOOR" if code == "E_THRESHOLD" else code,
        f"{label} is below its immutable candidate floor",
    )
    return observed, applicability


def threshold_candidate_data(
    validation: dict[str, Any],
    step1: dict[str, Any],
) -> dict[str, Any]:
    observation = validation["observation"]
    aggregate = observation["aggregate_observed"]
    aggregate_line = observation_exact_counter(
        aggregate["line"], "E_FREEZE_COUNTER", "aggregate line"
    )
    aggregate_branch = observation_exact_counter(
        aggregate["branch"], "E_FREEZE_COUNTER", "aggregate branch"
    )
    evidence_source = validation["evidence"]
    require(
        "run_status_sha256" in evidence_source,
        "E_FREEZE_RUN_STATUS",
        "threshold freeze requires a sealed diagnostic run status",
    )
    evidence = {
        "run_id": evidence_source["run_id"],
        "git_head": evidence_source["git_head"],
        "source_sha256": evidence_source["source_sha256"],
        "run_status_sha256": evidence_source["run_status_sha256"],
        "summary_sha256": evidence_source["summary_sha256"],
        "observation_sha256": evidence_source["observation_sha256"],
        "coverage_contract_sha256": evidence_source["coverage_contract_sha256"],
        "threshold_predecessor_sha256": evidence_source["threshold_sha256"],
        "exec_manifest_sha256": evidence_source["exec_manifest_sha256"],
        "aggregate_exec_sha256": evidence_source["aggregate_exec_sha256"],
        "aggregate_xml_sha256": evidence_source["aggregate_xml_sha256"],
        "workspace_class_tree_sha256": evidence_source["workspace_class_tree_sha256"],
    }
    validate_threshold_evidence(evidence, "E_FREEZE_EVIDENCE")

    observed_rows = observation["critical_classes"]
    require(
        type(observed_rows) is list and len(observed_rows) == len(step1["critical_classes"]),
        "E_FREEZE_CRITICAL",
        "diagnostic critical observation set differs",
    )
    critical_thresholds: list[dict[str, Any]] = []
    for number, (observed_row, expected_row) in enumerate(
        zip(observed_rows, step1["critical_classes"]), 1
    ):
        observed_row = validate_critical_observation_row(
            observed_row,
            expected_row,
            "E_FREEZE_CRITICAL",
            f"diagnostic critical row {number}",
        )
        metrics: dict[str, Any] = {}
        metric_outcomes: list[str] = []
        below_floor_metrics: list[str] = []
        for metric, numerator, denominator, floor in (
            ("line", 4, 5, Fraction(4, 5)),
            ("branch", 7, 10, Fraction(7, 10)),
        ):
            metric_value = observed_row[metric]
            exact, applicability = observation_critical_counter(
                metric_value,
                expected_row["fqcn"],
                expected_row["module"],
                metric,
                floor,
                "E_FREEZE_COUNTER",
                "E_FREEZE_ZERO_COUNTER",
                f"critical row {number} {metric}",
            )
            if applicability == "required-positive-total":
                if not counter_at_least_ratio(
                    exact,
                    numerator,
                    denominator,
                    "E_FREEZE_COUNTER",
                    f"critical row {number} {metric}",
                ):
                    below_floor_metrics.append(metric)
            metric_outcomes.append(metric_value["outcome"])
            metrics[metric] = {
                "applicability": applicability,
                "observed": exact,
                "minimum": (
                    exact if applicability == "required-positive-total" else None
                ),
            }
        require_critical_candidate_floor_outcome(
            observed_row,
            metric_outcomes,
            "E_FREEZE_CRITICAL",
            f"diagnostic critical row {number}",
        )
        if below_floor_metrics:
            reject(
                "E_FREEZE_FLOOR",
                f"critical {below_floor_metrics[0]} is below candidate floor at row {number}",
            )
        critical_thresholds.append(
            {
                "fqcn": expected_row["fqcn"],
                "module": expected_row["module"],
                "line": metrics["line"],
                "branch": metrics["branch"],
            }
        )
    return {
        "schema_version": 1,
        "kind": "v934-step4-coverage-threshold-freeze-candidate",
        "status": "review-required",
        "predecessor": {
            "path": "scripts/v934/step4/coverage-thresholds.json",
            "sha256": evidence["threshold_predecessor_sha256"],
            "status": "diagnostic-pending",
        },
        "aggregate_observed": {
            "evidence": evidence,
            "line": aggregate_line,
            "branch": aggregate_branch,
        },
        "aggregate_reviewed_thresholds": {
            "line": aggregate_line,
            "branch": aggregate_branch,
        },
        "critical_reviewed_thresholds": critical_thresholds,
        "review_requirements": {
            "diagnostic_run_id": evidence["run_id"],
            "decision": "confirm-observed-thresholds",
            "required_fields": [
                "reviewer",
                "reviewed_at",
                "evidence_path",
                "evidence_sha256",
            ],
        },
    }


def freeze_thresholds_data(repo_root: Path, run_id: str) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    step1, step4, _ = load_thresholds(repo_root)
    require(
        step4["status"] == "diagnostic-pending",
        "E_FREEZE_THRESHOLD_STATUS",
        "threshold freeze requires the diagnostic-pending predecessor",
    )
    validation = validate_run_data(
        repo_root,
        run_id,
        mode="diagnostic",
        require_run_status=True,
    )
    return threshold_candidate_data(validation, step1)


def freeze_thresholds_command(args: argparse.Namespace) -> None:
    candidate = freeze_thresholds_data(args.repo_root, args.run_id)
    atomic_json(args.output, candidate)
    print(
        "[v934-coverage-xml] THRESHOLD CANDIDATE "
        f"run={args.run_id} output={args.output}"
    )


def verify_threshold_candidate_data(repo_root: Path, candidate_path: Path) -> dict[str, Any]:
    candidate = load_json(candidate_path, "E_THRESHOLD_CANDIDATE")
    require(
        candidate.get("kind") == "v934-step4-coverage-threshold-freeze-candidate",
        "E_THRESHOLD_CANDIDATE",
        "threshold candidate kind differs",
    )
    aggregate = candidate.get("aggregate_observed")
    require(type(aggregate) is dict, "E_THRESHOLD_CANDIDATE", "threshold candidate aggregate is missing")
    evidence = aggregate.get("evidence")
    validated_evidence = validate_threshold_evidence(evidence, "E_THRESHOLD_CANDIDATE")
    _step1, step4, _hashes = load_thresholds(repo_root)
    if step4["status"] == "diagnostic-pending":
        expected = freeze_thresholds_data(repo_root, validated_evidence["run_id"])
    else:
        frozen = validate_frozen_diagnostic_data(repo_root)
        expected = threshold_candidate_from_frozen_result(frozen)
    require(
        exact_json_identity(candidate, expected),
        "E_THRESHOLD_CANDIDATE",
        "threshold candidate differs from recomputation",
    )
    return candidate


def verify_threshold_candidate_command(args: argparse.Namespace) -> None:
    candidate = verify_threshold_candidate_data(args.repo_root.resolve(), args.candidate)
    print(
        "[v934-coverage-xml] THRESHOLD CANDIDATE VALID "
        f"run={candidate['aggregate_observed']['evidence']['run_id']}"
    )


def git_environment() -> dict[str, str]:
    # Frozen replay must not inherit repository/object/ref/index/worktree,
    # shallow/graft, or config redirection.  A non-Git allowlist also fails
    # closed for future GIT_* variables that are unknown today.
    environment = {
        name: os.environ[name]
        for name in ("PATH", "SYSTEMROOT", "TMPDIR", "TMP", "TEMP")
        if name in os.environ
    }
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "LC_ALL": "C",
            "LANG": "C",
        }
    )
    return environment


def frozen_git_process(
    repo_root: Path,
    arguments: list[str],
) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        [
            "git",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-c",
            "core.hooksPath=/dev/null",
            "-C",
            str(repo_root),
            *arguments,
        ],
        env=git_environment(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )


def require_safe_frozen_git_repository(repo_root: Path) -> None:
    """Reject repository topologies that can make frozen replay ambiguous."""
    try:
        canonical_root = repo_root.resolve(strict=True)
    except OSError as exc:
        raise CoverageXmlError(
            "E_FROZEN_GIT", "cannot resolve frozen Git repository root"
        ) from exc
    require(
        canonical_root == repo_root.absolute()
        and repo_root.is_dir()
        and not repo_root.is_symlink(),
        "E_FROZEN_GIT",
        "frozen Git repository root is not canonical",
    )

    top_level = frozen_git_process(repo_root, ["rev-parse", "--show-toplevel"])
    object_format = frozen_git_process(
        repo_root, ["rev-parse", "--show-object-format"]
    )
    shallow = frozen_git_process(
        repo_root, ["rev-parse", "--is-shallow-repository"]
    )
    common_dir = frozen_git_process(
        repo_root,
        ["rev-parse", "--path-format=absolute", "--git-common-dir"],
    )
    replace_refs = frozen_git_process(
        repo_root,
        ["for-each-ref", "--format=%(refname)", "refs/replace"],
    )
    require(
        top_level.returncode == 0
        and top_level.stdout == os.fsencode(str(canonical_root)) + b"\n"
        and object_format.returncode == 0
        and object_format.stdout == b"sha1\n"
        and shallow.returncode == 0
        and shallow.stdout == b"false\n"
        and common_dir.returncode == 0
        and common_dir.stdout.endswith(b"\n")
        and common_dir.stdout.count(b"\n") == 1
        and replace_refs.returncode == 0
        and replace_refs.stdout == b"",
        "E_FROZEN_GIT",
        "frozen Git repository topology is unsafe or ambiguous",
    )
    common_path = Path(os.fsdecode(common_dir.stdout[:-1])).absolute()
    require(
        common_path.is_dir()
        and not common_path.is_symlink()
        and common_path.resolve(strict=True) == common_path,
        "E_FROZEN_GIT",
        "frozen Git common directory is not canonical",
    )
    info_dir = common_path / "info"
    try:
        info_stat = info_dir.lstat()
    except FileNotFoundError:
        return
    except OSError as exc:
        raise CoverageXmlError(
            "E_FROZEN_GIT", "cannot inspect frozen Git graft metadata"
        ) from exc
    require(
        stat.S_ISDIR(info_stat.st_mode)
        and not stat.S_ISLNK(info_stat.st_mode)
        and info_dir.resolve(strict=True) == info_dir.absolute(),
        "E_FROZEN_GIT",
        "frozen Git info directory is not canonical",
    )
    grafts = info_dir / "grafts"
    try:
        grafts_stat = grafts.lstat()
    except FileNotFoundError:
        return
    except OSError as exc:
        raise CoverageXmlError(
            "E_FROZEN_GIT", "cannot inspect frozen Git graft metadata"
        ) from exc
    require(
        stat.S_ISREG(grafts_stat.st_mode)
        and not stat.S_ISLNK(grafts_stat.st_mode)
        and grafts.resolve(strict=True) == grafts.absolute()
        and grafts_stat.st_size == 0,
        "E_FROZEN_GIT",
        "frozen Git graft metadata is unsafe or non-empty",
    )


def git_current_head(repo_root: Path) -> str:
    require_safe_frozen_git_repository(repo_root)
    process = frozen_git_process(
        repo_root, ["rev-parse", "--verify", "HEAD^{commit}"]
    )
    try:
        value = process.stdout.decode("ascii", errors="strict").strip()
    except UnicodeError:
        value = ""
    require(
        process.returncode == 0 and GIT_HEAD_PATTERN.fullmatch(value) is not None,
        "E_FROZEN_GIT",
        "current committed Git HEAD is unavailable",
    )
    return value


def require_git_ancestor(repo_root: Path, ancestor: str, current_head: str) -> None:
    require(
        GIT_HEAD_PATTERN.fullmatch(ancestor) is not None
        and GIT_HEAD_PATTERN.fullmatch(current_head) is not None,
        "E_FROZEN_GIT",
        "frozen diagnostic Git identity is invalid",
    )
    require_safe_frozen_git_repository(repo_root)
    process = frozen_git_process(
        repo_root,
        ["merge-base", "--is-ancestor", ancestor, current_head],
    )
    if process.returncode == 1:
        reject(
            "E_FROZEN_ANCESTOR",
            "confirmed diagnostic commit is not an ancestor of current HEAD",
        )
    require(
        process.returncode == 0,
        "E_FROZEN_GIT",
        "cannot verify the confirmed diagnostic commit ancestry",
    )


def git_show_blob(repo_root: Path, commit: str, relative_path: str) -> bytes:
    require(
        GIT_HEAD_PATTERN.fullmatch(commit) is not None,
        "E_FROZEN_GIT",
        "Git blob commit identity is invalid",
    )
    require(
        relative_path
        in {
            "scripts/v934/step4/coverage-thresholds.json",
            "scripts/v934/step4/coverage-contract.json",
        },
        "E_FROZEN_GIT",
        "Git blob path is not an internal frozen-diagnostic input",
    )
    require_safe_frozen_git_repository(repo_root)
    process = frozen_git_process(
        repo_root,
        [
            "--no-pager",
            "show",
            "--no-ext-diff",
            "--no-textconv",
            f"{commit}:{relative_path}",
        ],
    )
    require(
        process.returncode == 0 and 0 < len(process.stdout) <= 4 * 1024 * 1024,
        "E_FROZEN_BLOB",
        f"cannot read bounded frozen Git blob: {relative_path}",
    )
    return process.stdout


def require_frozen_blob_hash(
    payload: bytes,
    expected_sha256: Any,
    label: str,
) -> str:
    expected = json_sha256(expected_sha256, "E_FROZEN_BLOB", f"{label} expected SHA")
    actual = hashlib.sha256(payload).hexdigest()
    require(actual == expected, "E_FROZEN_BLOB", f"{label} Git blob hash differs")
    return actual


def write_private_blob(directory: Path, name: str, payload: bytes) -> Path:
    real_directory(directory, "E_FROZEN_TEMP")
    directory_stat = directory.lstat()
    require(
        stat.S_IMODE(directory_stat.st_mode) & 0o077 == 0,
        "E_FROZEN_TEMP",
        "frozen diagnostic temporary directory is not private",
    )
    require(name in {"coverage-thresholds.json", "coverage-contract.json"}, "E_FROZEN_TEMP", "unsafe frozen blob name")
    path = directory / name
    descriptor = -1
    try:
        descriptor = os.open(
            path,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        view = memoryview(payload)
        while view:
            written = os.write(descriptor, view)
            require(written > 0, "E_FROZEN_TEMP", "short write for frozen Git blob")
            view = view[written:]
        os.fsync(descriptor)
    except OSError as exc:
        path.unlink(missing_ok=True)
        reject("E_FROZEN_TEMP", f"cannot stage frozen Git blob: {exc.__class__.__name__}")
    finally:
        if descriptor >= 0:
            os.close(descriptor)
    regular_file(path, "E_FROZEN_TEMP")
    return path


def validate_frozen_candidate_equivalence(
    confirmed: dict[str, Any],
    candidate: dict[str, Any],
) -> None:
    confirmed_aggregate = confirmed["aggregate_observed"]
    candidate_aggregate = candidate["aggregate_observed"]
    require(
        exact_json_identity(
            confirmed_aggregate["evidence"], candidate_aggregate["evidence"]
        ),
        "E_FROZEN_EVIDENCE",
        "confirmed diagnostic evidence differs from full run recomputation",
    )
    require(
        exact_json_identity(confirmed_aggregate, candidate_aggregate),
        "E_FROZEN_RECOMPUTE",
        "confirmed aggregate observation differs from full run recomputation",
    )
    require(
        exact_json_identity(
            confirmed["aggregate_reviewed_thresholds"],
            candidate["aggregate_reviewed_thresholds"],
        ),
        "E_FROZEN_RECOMPUTE",
        "confirmed aggregate reviewed thresholds differ from recomputation",
    )
    require(
        exact_json_identity(
            confirmed["critical_reviewed_thresholds"],
            candidate["critical_reviewed_thresholds"],
        ),
        "E_FROZEN_RECOMPUTE",
        "confirmed critical reviewed thresholds differ from recomputation",
    )


def threshold_candidate_from_frozen_result(result: dict[str, Any]) -> dict[str, Any]:
    evidence = result["evidence"]
    return {
        "schema_version": 1,
        "kind": "v934-step4-coverage-threshold-freeze-candidate",
        "status": "review-required",
        "predecessor": {
            "path": "scripts/v934/step4/coverage-thresholds.json",
            "sha256": result["frozen_blobs"]["threshold"]["sha256"],
            "status": "diagnostic-pending",
        },
        "aggregate_observed": result["aggregate_observed"],
        "aggregate_reviewed_thresholds": result["aggregate_reviewed_thresholds"],
        "critical_reviewed_thresholds": result["critical_reviewed_thresholds"],
        "review_requirements": {
            "diagnostic_run_id": evidence["run_id"],
            "decision": "confirm-observed-thresholds",
            "required_fields": [
                "reviewer",
                "reviewed_at",
                "evidence_path",
                "evidence_sha256",
            ],
        },
    }


def expected_sessions_from_ledger(
    run_id: str,
    ledger: list[dict[str, str]],
) -> list[str]:
    require(
        isinstance(run_id, str)
        and SESSION_PREFIX_PATTERN.fullmatch(run_id) is not None
        and len(run_id) <= 128,
        "E_FROZEN_SESSIONS",
        "frozen diagnostic run id is unsafe",
    )
    sessions = sorted(
        f"{run_id}-{row['variant_key']}-{owner}"
        for row in ledger
        for owner in row["expected_session_owners"].split(",")
    )
    require(
        len(sessions) == 48 and len(set(sessions)) == 48,
        "E_FROZEN_SESSIONS",
        "frozen diagnostic session derivation differs",
    )
    return sessions


def recompute_sanitized_attested_observation(
    repo_root: Path,
    xml_path: Path,
    run_id: str,
    step1: dict[str, Any],
    diagnostic_threshold: dict[str, Any],
    attestation: dict[str, Any],
) -> dict[str, Any]:
    """Recompute only the retained XML semantics of a Git-safe capsule."""

    modules, artifact_to_module, freeze_sha = load_frozen_modules(repo_root)
    ledger = load_ledger(repo_root)
    expected_sessions = expected_sessions_from_ledger(run_id, ledger)
    root, xml_stat, xml_sha = read_xml(xml_path)
    require(
        attestation["xml"]
        == {
            "sha256": xml_sha,
            "size": xml_stat.st_size,
            "deterministic_report_replay_count": 2,
        },
        "E_FROZEN_XML",
        "frozen capsule XML binding differs",
    )
    root_counters, group_counters, class_index, xml_class_names = validate_xml_structure(
        root,
        expected_sessions,
        modules,
        artifact_to_module,
        step1["critical_classes"],
        repo_root,
        require_compiled_class_files=False,
    )
    line_floor = decimal_ratio(
        diagnostic_threshold["critical_candidate_floor"]["line"],
        "E_FROZEN_THRESHOLD",
        "critical line floor",
    )
    branch_floor = decimal_ratio(
        diagnostic_threshold["critical_candidate_floor"]["branch"],
        "E_FROZEN_THRESHOLD",
        "critical branch floor",
    )
    critical_results, below_floor_count, not_applicable_count = critical_observations(
        step1["critical_classes"],
        class_index,
        artifact_to_module,
        line_floor,
        branch_floor,
    )
    source_attestation = attestation["source_attestation"]
    require(
        source_attestation["coverage_ledger_sha256"] == EXPECTED_LEDGER_SHA256
        and source_attestation["workspace_bytecode_class_count"] > 0,
        "E_FROZEN_ATTESTATION",
        "frozen source attestation ledger/class count differs",
    )
    value = {
        "report_inventory": {
            "group_count": len(group_counters),
            "session_count": 48,
            "critical_class_count": len(critical_results),
            "reportable_class_count": len(xml_class_names),
            "workspace_bytecode_class_count": source_attestation[
                "workspace_bytecode_class_count"
            ],
            "class_universe_binding": "exact-reporter-config-and-deterministic-replay",
            "frozen_modules": modules,
        },
        "aggregate_observed": {
            "line": counter_json(root_counters["LINE"]),
            "branch": counter_json(root_counters["BRANCH"]),
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
            "outcome": "below-floor-gaps-recorded"
            if below_floor_count
            else "at-or-above-floor",
            "below_floor_class_count": below_floor_count,
            "not_applicable_metric_count": not_applicable_count,
            "thresholds_frozen_by_observe": False,
        },
        "critical_classes": critical_results,
    }
    require(
        freeze_sha == EXPECTED_STEP1_FREEZE_SHA256,
        "E_FROZEN_RECOMPUTE",
        "frozen module mapping freeze differs",
    )
    return value


def validate_frozen_git_safe_attestation(
    attestation: dict[str, Any],
    confirmed_evidence: dict[str, Any],
    diagnostic_head: str,
) -> dict[str, Any]:
    identity = attestation["identity"]
    expected_identity = {
        "run_id": confirmed_evidence["run_id"],
        "git_head": diagnostic_head,
        "source_sha256": confirmed_evidence["source_sha256"],
        "run_status_sha256": confirmed_evidence["run_status_sha256"],
        "summary_sha256": confirmed_evidence["summary_sha256"],
        "coverage_contract_sha256": confirmed_evidence["coverage_contract_sha256"],
        "threshold_predecessor_sha256": confirmed_evidence[
            "threshold_predecessor_sha256"
        ],
        "observation_sha256": confirmed_evidence["observation_sha256"],
    }
    require(
        all(identity[field] == expected for field, expected in expected_identity.items()),
        "E_FROZEN_ATTESTATION",
        "frozen attestation identity differs from confirmed threshold evidence",
    )
    execution = attestation["execution_attestation"]
    require(
        execution["aggregate_exec_sha256"]
        == confirmed_evidence["aggregate_exec_sha256"]
        and execution["merge_semantics"] == EXPECTED_AGGREGATE_MERGE_SEMANTICS,
        "E_FROZEN_ATTESTATION",
        "frozen execution attestation differs from confirmed evidence",
    )
    require(
        attestation["xml"]["sha256"] == confirmed_evidence["aggregate_xml_sha256"],
        "E_FROZEN_ATTESTATION",
        "frozen XML attestation differs from confirmed evidence",
    )
    source = attestation["source_attestation"]
    require(
        source["workspace_class_tree_sha256"]
        == confirmed_evidence["workspace_class_tree_sha256"]
        and source["coverage_ledger_sha256"] == EXPECTED_LEDGER_SHA256,
        "E_FROZEN_ATTESTATION",
        "frozen source attestation differs from confirmed evidence",
    )
    return execution


def validate_frozen_diagnostic_data(repo_root: Path) -> dict[str, Any]:
    repo_root = repo_root.resolve()
    real_directory(repo_root, "E_REPO_ROOT")
    step1, confirmed, threshold_hashes = load_thresholds(repo_root)
    require(
        confirmed["status"] == "confirmed",
        "E_FROZEN_THRESHOLD_STATUS",
        "frozen diagnostic validation requires canonical confirmed thresholds",
    )
    confirmed_evidence = validate_threshold_evidence(
        confirmed["aggregate_observed"]["evidence"],
        "E_FROZEN_EVIDENCE",
    )
    current_head = git_current_head(repo_root)
    diagnostic_head = confirmed_evidence["git_head"]
    require_git_ancestor(repo_root, diagnostic_head, current_head)

    threshold_relative = "scripts/v934/step4/coverage-thresholds.json"
    contract_relative = "scripts/v934/step4/coverage-contract.json"
    threshold_blob = git_show_blob(repo_root, diagnostic_head, threshold_relative)
    contract_blob = git_show_blob(repo_root, diagnostic_head, contract_relative)
    threshold_blob_sha = require_frozen_blob_hash(
        threshold_blob,
        confirmed_evidence["threshold_predecessor_sha256"],
        "diagnostic threshold",
    )
    contract_blob_sha = require_frozen_blob_hash(
        contract_blob,
        confirmed_evidence["coverage_contract_sha256"],
        "diagnostic coverage contract",
    )
    capsule_stem = (
        repo_root
        / "docs/9.3.4/evidence/step-4"
        / f"{confirmed_evidence['run_id']}-portable-capsule"
    )
    capsule_archive = Path(f"{capsule_stem}.tar.gz")
    capsule_manifest = Path(f"{capsule_stem}.manifest.json")

    with tempfile.TemporaryDirectory(prefix="v934-frozen-diagnostic-") as temporary_name:
        temporary_root = Path(temporary_name)
        override_root = temporary_root / "overrides"
        override_root.mkdir(mode=0o700)
        evidence_root = temporary_root / "evidence"
        threshold_path = write_private_blob(
            override_root,
            "coverage-thresholds.json",
            threshold_blob,
        )
        contract_path = write_private_blob(
            override_root,
            "coverage-contract.json",
            contract_blob,
        )
        old_step1, old_threshold, old_hashes = load_thresholds(
            repo_root,
            _step4_path_override=threshold_path,
        )
        require(
            old_step1 == step1,
            "E_FROZEN_RECOMPUTE",
            "Step 1 policy changed during semantic replay",
        )
        require(
            old_threshold["status"] == "diagnostic-pending"
            and old_hashes["step4_successor_sha256"] == threshold_blob_sha,
            "E_FROZEN_BLOB",
            "frozen threshold blob is not the exact diagnostic-pending predecessor",
        )
        _contract_path, validated_contract_sha = validate_workflow_contract(
            repo_root,
            "diagnostic",
            _contract_path_override=contract_path,
        )
        require(
            validated_contract_sha == contract_blob_sha,
            "E_FROZEN_BLOB",
            "frozen contract blob is not the exact diagnostic-ready contract",
        )
        try:
            diagnostic_capsule.materialize_capsule(
                capsule_archive,
                capsule_manifest,
                evidence_root,
                expected_run_id=confirmed_evidence["run_id"],
                expected_git_head=diagnostic_head,
                expected_source_sha256=confirmed_evidence["source_sha256"],
            )
        except diagnostic_capsule.CapsuleError as exc:
            reject(
                "E_FROZEN_CAPSULE",
                f"Git-safe diagnostic capsule rejected ({exc.code})",
            )
        attestation_path = evidence_root / "evidence/diagnostic-attestation.json"
        xml_path = evidence_root / "evidence/jacoco.xml"
        attestation, attestation_payload = load_git_safe_diagnostic_attestation(
            attestation_path,
            code="E_FROZEN_ATTESTATION",
        )
        execution_attestation = validate_frozen_git_safe_attestation(
            attestation,
            confirmed_evidence,
            diagnostic_head,
        )
        recomputed_semantic = recompute_sanitized_attested_observation(
            repo_root,
            xml_path,
            confirmed_evidence["run_id"],
            old_step1,
            old_threshold,
            attestation,
        )
        require(
            exact_json_identity(
                attestation["semantic_observation"], recomputed_semantic
            ),
            "E_FROZEN_RECOMPUTE",
            "retained semantic observation differs from XML recomputation",
        )
        frozen_evidence = {
            "run_id": attestation["identity"]["run_id"],
            "git_head": attestation["identity"]["git_head"],
            "source_sha256": attestation["identity"]["source_sha256"],
            "run_status_sha256": attestation["identity"]["run_status_sha256"],
            "summary_sha256": attestation["identity"]["summary_sha256"],
            "observation_sha256": attestation["identity"]["observation_sha256"],
            "coverage_contract_sha256": attestation["identity"][
                "coverage_contract_sha256"
            ],
            "threshold_sha256": attestation["identity"][
                "threshold_predecessor_sha256"
            ],
            "exec_manifest_sha256": confirmed_evidence["exec_manifest_sha256"],
            "aggregate_exec_sha256": execution_attestation["aggregate_exec_sha256"],
            "aggregate_xml_sha256": attestation["xml"]["sha256"],
            "workspace_class_tree_sha256": attestation["source_attestation"][
                "workspace_class_tree_sha256"
            ],
        }
        candidate = threshold_candidate_data(
            {
                "observation": {
                    "aggregate_observed": recomputed_semantic["aggregate_observed"],
                    "critical_classes": recomputed_semantic["critical_classes"],
                },
                "evidence": frozen_evidence,
            },
            old_step1,
        )

    validate_frozen_candidate_equivalence(confirmed, candidate)
    return {
        "schema_version": 1,
        "kind": "v934-step4-frozen-diagnostic-validation",
        "status": "passed",
        "run_id": confirmed_evidence["run_id"],
        "diagnostic_git_head": diagnostic_head,
        "current_git_head": current_head,
        "ancestor_verified": True,
        "confirmed_threshold_sha256": threshold_hashes["step4_successor_sha256"],
        "frozen_blobs": {
            "threshold": {
                "git_path": threshold_relative,
                "sha256": threshold_blob_sha,
                "status": "diagnostic-pending",
            },
            "contract": {
                "git_path": contract_relative,
                "sha256": contract_blob_sha,
                "status": "diagnostic-ready",
            },
        },
        "replay_receipt": {
            "profile": GIT_SAFE_DIAGNOSTIC_PROFILE,
            "capsule_manifest_sha256": sha256_file(
                capsule_manifest,
                "E_FROZEN_CAPSULE",
            ),
            "attestation_sha256": hashlib.sha256(attestation_payload).hexdigest(),
            "aggregate_xml_sha256": attestation["xml"]["sha256"],
            "execution_attestation": execution_attestation,
            "scope": GIT_SAFE_DIAGNOSTIC_REPLAY_SCOPE,
            "status": "verified",
        },
        "evidence": confirmed_evidence,
        "aggregate_observed": candidate["aggregate_observed"],
        "aggregate_reviewed_thresholds": candidate["aggregate_reviewed_thresholds"],
        "critical_reviewed_thresholds": candidate["critical_reviewed_thresholds"],
    }


def validate_frozen_diagnostic_command(args: argparse.Namespace) -> None:
    result = validate_frozen_diagnostic_data(args.repo_root)
    print(json.dumps(result, sort_keys=True, separators=(",", ":")))


def require_formal_class_tree(formal_sha256: Any, diagnostic_sha256: Any) -> None:
    formal = json_sha256(formal_sha256, "E_FORMAL_CLASS_TREE", "formal class-tree SHA")
    diagnostic = json_sha256(
        diagnostic_sha256,
        "E_FORMAL_CLASS_TREE",
        "diagnostic class-tree SHA",
    )
    require(
        formal == diagnostic,
        "E_FORMAL_CLASS_TREE",
        "formal workspace class tree differs from the reviewed diagnostic baseline",
    )


def require_formal_applicability(
    actual: str,
    reviewed: str,
    label: str,
) -> None:
    require(
        actual == reviewed,
        "E_FORMAL_APPLICABILITY_DRIFT",
        f"{label} applicability differs",
    )


def formal_metric_result(
    actual_value: Any,
    diagnostic_value: Any,
    minimum_value: Any,
    label: str,
) -> dict[str, Any]:
    actual = validate_exact_counter(actual_value, "E_FORMAL_COUNTER", f"{label} observed")
    diagnostic = validate_exact_counter(
        diagnostic_value,
        "E_FORMAL_THRESHOLD",
        f"{label} diagnostic observed",
    )
    minimum = validate_exact_counter(
        minimum_value,
        "E_FORMAL_THRESHOLD",
        f"{label} minimum",
    )
    require(
        actual["total"] == diagnostic["total"] == minimum["total"],
        "E_FORMAL_DENOMINATOR",
        f"{label} denominator differs from the reviewed diagnostic baseline",
    )
    require(
        counter_at_least(actual, minimum, "E_FORMAL_COUNTER", label),
        "E_FORMAL_LOW",
        f"{label} is below the reviewed threshold",
    )
    return {"observed": actual, "minimum": minimum, "outcome": "passed"}


def formal_critical_metric_result(
    actual_value: Any,
    reviewed_value: Any,
    fqcn: str,
    module: str,
    metric: str,
    numerator: int,
    denominator: int,
    floor: Fraction,
    label: str,
) -> dict[str, Any]:
    observed, actual_applicability = observation_critical_counter(
        actual_value,
        fqcn,
        module,
        metric,
        floor,
        "E_FORMAL_COUNTER",
        "E_FORMAL_COUNTER",
        label,
    )
    reviewed_counter, reviewed_applicability = validate_reviewed_critical_metric(
        reviewed_value,
        fqcn,
        module,
        metric,
        numerator,
        denominator,
        "E_FORMAL_THRESHOLD",
        f"{label} reviewed",
    )
    require_formal_applicability(
        actual_applicability,
        reviewed_applicability,
        label,
    )
    if actual_applicability == "not-applicable-zero-total-only":
        require(
            observed == reviewed_counter,
            "E_FORMAL_DENOMINATOR",
            f"{label} structural counter differs",
        )
        return {
            "applicability": "not-applicable-zero-total-only",
            "observed": observed,
            "minimum": None,
            "outcome": "passed",
        }
    return {
        "applicability": "required-positive-total",
        **formal_metric_result(
            observed,
            reviewed_counter,
            reviewed_counter,
            label,
        ),
    }


def formal_check_data(
    repo_root: Path,
    run_id: str,
    mode: str = "formal",
) -> dict[str, Any]:
    require(mode in ARTIFACT_MODES, "E_RUN_MODE", "coverage gate mode must be formal or release")
    repo_root = repo_root.resolve()
    step1, step4, threshold_hashes = load_thresholds(repo_root)
    if step4["status"] != "confirmed":
        reject(
            "E_FORMAL_THRESHOLD_STATUS",
            "formal coverage requires a confirmed threshold successor",
        )
    # Standalone gate recomputation must mechanically prove that the confirmed
    # successor still derives from its exact commit-frozen diagnostic inputs.
    # Do not rely on an earlier runner/contract preflight for this provenance.
    frozen_diagnostic = validate_frozen_diagnostic_data(repo_root)
    validation = validate_run_data(
        repo_root,
        run_id,
        mode=mode,
        require_run_status=False,
    )
    require_formal_class_tree(
        validation["evidence"]["workspace_class_tree_sha256"],
        step4["aggregate_observed"]["evidence"]["workspace_class_tree_sha256"],
    )
    observation = validation["observation"]
    reviewed_aggregate = step4["aggregate_reviewed_thresholds"]
    aggregate_results: dict[str, Any] = {}
    for metric in ("line", "branch"):
        observed = observation_exact_counter(
            observation["aggregate_observed"][metric],
            "E_FORMAL_COUNTER",
            f"formal aggregate {metric}",
        )
        aggregate_results[metric] = formal_metric_result(
            observed,
            step4["aggregate_observed"][metric],
            reviewed_aggregate[metric],
            f"formal aggregate {metric}",
        )

    observed_rows = observation["critical_classes"]
    reviewed_rows = step4["critical_reviewed_thresholds"]
    require(
        type(observed_rows) is list
        and len(observed_rows) == len(step1["critical_classes"])
        and type(reviewed_rows) is list
        and len(reviewed_rows) == len(step1["critical_classes"]),
        "E_FORMAL_CRITICAL",
        "formal critical set cardinality differs",
    )
    critical_results: list[dict[str, Any]] = []
    for number, (expected, observed_row, reviewed_row) in enumerate(
        zip(step1["critical_classes"], observed_rows, reviewed_rows), 1
    ):
        observed_row = validate_critical_observation_row(
            observed_row,
            expected,
            "E_FORMAL_CRITICAL",
            f"formal critical row {number}",
        )
        reviewed_row = exact_keys(
            reviewed_row,
            ("fqcn", "module", "line", "branch"),
            "E_FORMAL_CRITICAL",
            f"reviewed critical row {number}",
        )
        require(
            reviewed_row["fqcn"] == expected["fqcn"]
            and reviewed_row["module"] == expected["module"],
            "E_FORMAL_CRITICAL",
            f"reviewed critical row {number} identity differs",
        )
        metrics: dict[str, Any] = {}
        metric_outcomes: list[str] = []
        for metric, numerator, denominator, floor in (
            ("line", 4, 5, Fraction(4, 5)),
            ("branch", 7, 10, Fraction(7, 10)),
        ):
            metrics[metric] = formal_critical_metric_result(
                observed_row[metric],
                reviewed_row[metric],
                expected["fqcn"],
                expected["module"],
                metric,
                numerator,
                denominator,
                floor,
                f"formal critical row {number} {metric}",
            )
            metric_outcomes.append(observed_row[metric]["outcome"])
        require_critical_candidate_floor_outcome(
            observed_row,
            metric_outcomes,
            "E_FORMAL_CRITICAL",
            f"formal critical row {number}",
        )
        critical_results.append(
            {
                "fqcn": expected["fqcn"],
                "module": expected["module"],
                "line": metrics["line"],
                "branch": metrics["branch"],
            }
        )
    result = {
        "schema_version": 1,
        "kind": "v934-step4-coverage-gate",
        "status": "passed",
        "run_id": run_id,
        "git_head": validation["context"]["git_head"],
        "threshold": artifact_record(
            repo_root,
            repo_root / "scripts/v934/step4/coverage-thresholds.json",
            "E_FORMAL_THRESHOLD",
        ),
        "threshold_sha256": threshold_hashes["step4_successor_sha256"],
        "frozen_diagnostic": frozen_diagnostic,
        "diagnostic_baseline": step4["aggregate_observed"]["evidence"],
        "formal_evidence": validation["evidence"],
        "bindings": validation["bindings"],
        "aggregate": aggregate_results,
        "critical_classes": critical_results,
    }
    if mode == "release":
        result["release_successor"] = RELEASE_SUCCESSOR_MARKER
    return result


def formal_check_command(args: argparse.Namespace) -> None:
    _step1, step4, _hashes = load_thresholds(args.repo_root.resolve())
    if step4["status"] != "confirmed":
        reject(
            "E_FORMAL_THRESHOLD_STATUS",
            "formal coverage requires a confirmed threshold successor",
        )
    output = require_canonical_run_artifact_path(
        args.repo_root.resolve(),
        args.run_id,
        args.output,
        "coverage-gate.json",
        "E_COVERAGE_GATE_PATH",
    )
    result = formal_check_data(args.repo_root, args.run_id, args.mode)
    atomic_json(output, result)
    print(f"[v934-coverage-xml] {args.mode.upper()} PASS run={args.run_id} output={output}")


def validate_coverage_gate(
    repo_root: Path,
    gate_path: Path,
    expected_mode: str | None = None,
) -> dict[str, Any]:
    gate = load_json(gate_path, "E_COVERAGE_GATE")
    mode = "release" if "release_successor" in gate else "formal"
    require(
        expected_mode is None or expected_mode == mode,
        "E_COVERAGE_GATE_MODE",
        "coverage gate workflow mode differs",
    )
    if mode == "release":
        require(
            gate.get("release_successor") == RELEASE_SUCCESSOR_MARKER,
            "E_COVERAGE_GATE_MODE",
            "release coverage gate successor marker differs",
        )
    require(
        gate.get("kind") == "v934-step4-coverage-gate"
        and gate.get("status") == "passed"
        and isinstance(gate.get("run_id"), str),
        "E_COVERAGE_GATE",
        "coverage gate identity/status differs",
    )
    canonical_gate = require_canonical_run_artifact_path(
        repo_root,
        gate["run_id"],
        gate_path,
        "coverage-gate.json",
        "E_COVERAGE_GATE_PATH",
    )
    require(gate_path.absolute() == canonical_gate, "E_COVERAGE_GATE_PATH", "coverage gate path differs")
    expected = formal_check_data(repo_root, gate["run_id"], mode)
    require(
        exact_json_identity(gate, expected),
        "E_COVERAGE_GATE",
        "coverage gate differs from exact recomputation",
    )
    return gate


def artifact_workflow_mode(
    value: dict[str, Any],
    stage: str,
    code: str,
) -> str:
    status = value.get("status")
    if status == f"formal-{stage}":
        require(
            "release_successor" not in value,
            code,
            "formal artifact contains a release successor marker",
        )
        return "formal"
    if status == f"release-{stage}":
        require(
            value.get("release_successor") == RELEASE_SUCCESSOR_MARKER,
            code,
            "release artifact successor marker differs",
        )
        return "release"
    reject(code, f"{stage} artifact workflow status differs")


def acceptance_candidate_data(
    repo_root: Path,
    run_id: str,
    gate_path: Path,
    mode: str = "formal",
) -> dict[str, Any]:
    require(mode in ARTIFACT_MODES, "E_RUN_MODE", "candidate mode must be formal or release")
    gate_path = require_canonical_run_artifact_path(
        repo_root,
        run_id,
        gate_path,
        "coverage-gate.json",
        "E_COVERAGE_GATE_PATH",
    )
    gate = validate_coverage_gate(repo_root, gate_path, mode)
    require(gate["run_id"] == run_id, "E_ACCEPTANCE_CANDIDATE", "gate/run identity differs")
    result = {
        "schema_version": 1,
        "kind": "v934-step4-coverage-acceptance-artifact",
        "stage": "candidate",
        "status": f"{mode}-candidate",
        "run_id": run_id,
        "git_head": gate["git_head"],
        "threshold": gate["threshold"],
        "coverage_gate": artifact_record(repo_root, gate_path, "E_COVERAGE_GATE"),
        "evidence": gate["formal_evidence"],
        "bindings": gate["bindings"],
    }
    if mode == "release":
        result["release_successor"] = RELEASE_SUCCESSOR_MARKER
    return result


def validate_acceptance_candidate(
    repo_root: Path,
    candidate_path: Path,
    expected_mode: str | None = None,
) -> dict[str, Any]:
    candidate = load_json(candidate_path, "E_ACCEPTANCE_CANDIDATE")
    mode = artifact_workflow_mode(
        candidate, "candidate", "E_ACCEPTANCE_CANDIDATE"
    )
    require(
        expected_mode is None or expected_mode == mode,
        "E_ACCEPTANCE_CANDIDATE_MODE",
        "acceptance candidate workflow mode differs",
    )
    expected_keys = (
        "schema_version",
        "kind",
        "stage",
        "status",
        "run_id",
        "git_head",
        "threshold",
        "coverage_gate",
        "evidence",
        "bindings",
    )
    if mode == "release":
        expected_keys = (*expected_keys, "release_successor")
    exact_keys(
        candidate,
        expected_keys,
        "E_ACCEPTANCE_CANDIDATE",
        "formal acceptance candidate",
    )
    require(
        candidate["schema_version"] == 1
        and type(candidate["schema_version"]) is int
        and candidate["kind"] == "v934-step4-coverage-acceptance-artifact"
        and candidate["stage"] == "candidate"
        and candidate["status"] == f"{mode}-candidate",
        "E_ACCEPTANCE_CANDIDATE",
        "formal acceptance candidate identity/status differs",
    )
    candidate_path = require_canonical_run_artifact_path(
        repo_root,
        candidate["run_id"],
        candidate_path,
        "candidate-manifest.json",
        "E_ACCEPTANCE_CANDIDATE_PATH",
    )
    expected_gate_path = canonical_run_root(repo_root, candidate["run_id"]) / "coverage-gate.json"
    gate_path = validate_artifact_record(
        repo_root,
        candidate["coverage_gate"],
        "E_ACCEPTANCE_CANDIDATE",
        "candidate coverage gate",
        expected_path=expected_gate_path,
    )
    expected = acceptance_candidate_data(
        repo_root, candidate["run_id"], gate_path, mode
    )
    require(
        exact_json_identity(candidate, expected),
        "E_ACCEPTANCE_CANDIDATE",
        "formal acceptance candidate differs from recomputation",
    )
    return candidate


def acceptance_final_data(
    repo_root: Path,
    candidate_path: Path,
    mode: str | None = None,
) -> dict[str, Any]:
    candidate = validate_acceptance_candidate(repo_root, candidate_path, mode)
    workflow_mode = artifact_workflow_mode(
        candidate, "candidate", "E_ACCEPTANCE_FINAL"
    )
    result = {
        "schema_version": 1,
        "kind": "v934-step4-coverage-acceptance-artifact",
        "stage": "final",
        "status": f"{workflow_mode}-final",
        "run_id": candidate["run_id"],
        "git_head": candidate["git_head"],
        "threshold": candidate["threshold"],
        "coverage_gate": candidate["coverage_gate"],
        "candidate_manifest": artifact_record(
            repo_root, candidate_path, "E_ACCEPTANCE_CANDIDATE"
        ),
        "evidence": candidate["evidence"],
        "bindings": candidate["bindings"],
    }
    if workflow_mode == "release":
        result["release_successor"] = RELEASE_SUCCESSOR_MARKER
    return result


def validate_acceptance_final(
    repo_root: Path,
    final_path: Path,
    expected_mode: str | None = None,
) -> dict[str, Any]:
    final = load_json(final_path, "E_ACCEPTANCE_FINAL")
    mode = artifact_workflow_mode(final, "final", "E_ACCEPTANCE_FINAL")
    require(
        expected_mode is None or expected_mode == mode,
        "E_ACCEPTANCE_FINAL_MODE",
        "acceptance final workflow mode differs",
    )
    expected_keys = (
        "schema_version",
        "kind",
        "stage",
        "status",
        "run_id",
        "git_head",
        "threshold",
        "coverage_gate",
        "candidate_manifest",
        "evidence",
        "bindings",
    )
    if mode == "release":
        expected_keys = (*expected_keys, "release_successor")
    exact_keys(
        final,
        expected_keys,
        "E_ACCEPTANCE_FINAL",
        "formal acceptance final",
    )
    require(
        final["schema_version"] == 1
        and type(final["schema_version"]) is int
        and final["kind"] == "v934-step4-coverage-acceptance-artifact"
        and final["stage"] == "final"
        and final["status"] == f"{mode}-final",
        "E_ACCEPTANCE_FINAL",
        "formal acceptance final identity/status differs",
    )
    final_path = require_canonical_run_artifact_path(
        repo_root,
        final["run_id"],
        final_path,
        "final-manifest.json",
        "E_ACCEPTANCE_FINAL_PATH",
    )
    expected_candidate_path = canonical_run_root(repo_root, final["run_id"]) / "candidate-manifest.json"
    candidate_path = validate_artifact_record(
        repo_root,
        final["candidate_manifest"],
        "E_ACCEPTANCE_FINAL",
        "final candidate manifest",
        expected_path=expected_candidate_path,
    )
    expected = acceptance_final_data(repo_root, candidate_path, mode)
    require(
        exact_json_identity(final, expected),
        "E_ACCEPTANCE_FINAL",
        "formal final differs from recomputation",
    )
    return final


def build_artifact_command(args: argparse.Namespace) -> None:
    repo_root = args.repo_root.resolve()
    mode = getattr(args, "mode", "formal")
    # Both acceptance stages are confirmed-threshold-only. Check this before
    # looking at an attacker-controlled candidate/gate path.
    _, step4, _ = load_thresholds(repo_root)
    if step4["status"] != "confirmed":
        reject(
            "E_FORMAL_THRESHOLD_STATUS",
            "formal/release artifacts require a confirmed threshold successor",
        )
    if args.stage == "candidate":
        require(args.run_id is not None, "E_ARGUMENT", "candidate stage requires --run-id")
        require(args.coverage_gate is not None, "E_ARGUMENT", "candidate stage requires --coverage-gate")
        require(args.candidate is None, "E_ARGUMENT", "candidate stage forbids --candidate")
        value = acceptance_candidate_data(
            repo_root, args.run_id, args.coverage_gate, mode
        )
        output = require_canonical_run_artifact_path(
            repo_root,
            value["run_id"],
            args.output,
            "candidate-manifest.json",
            "E_ACCEPTANCE_CANDIDATE_PATH",
        )
    else:
        require(args.candidate is not None, "E_ARGUMENT", "final stage requires --candidate")
        require(args.run_id is None, "E_ARGUMENT", "final stage forbids --run-id")
        require(args.coverage_gate is None, "E_ARGUMENT", "final stage forbids --coverage-gate")
        value = acceptance_final_data(repo_root, args.candidate, mode)
        output = require_canonical_run_artifact_path(
            repo_root,
            value["run_id"],
            args.output,
            "final-manifest.json",
            "E_ACCEPTANCE_FINAL_PATH",
        )
    atomic_json(output, value)
    print(
        f"[v934-coverage-xml] ARTIFACT {args.stage.upper()} "
        f"run={value['run_id']} output={output}"
    )


def verify_artifact_command(args: argparse.Namespace) -> None:
    repo_root = args.repo_root.resolve()
    expected_mode = getattr(args, "mode", None)
    _, step4, _ = load_thresholds(repo_root)
    if step4["status"] != "confirmed":
        reject(
            "E_FORMAL_THRESHOLD_STATUS",
            "formal/release artifact verification requires confirmed thresholds",
        )
    payload = load_json(args.artifact, "E_ACCEPTANCE_ARTIFACT")
    stage = payload.get("stage")
    if stage == "candidate":
        require(args.run_status is None, "E_ARGUMENT", "--run-status is final-only")
        value = validate_acceptance_candidate(repo_root, args.artifact, expected_mode)
    elif stage == "final":
        require(
            args.run_status is not None,
            "E_RUN_STATUS",
            "public final verification requires the canonical run status",
        )
        value = validate_acceptance_final(repo_root, args.artifact, expected_mode)
        mode = artifact_workflow_mode(value, "final", "E_ACCEPTANCE_ARTIFACT")
        run_root = canonical_run_root(repo_root, value["run_id"])
        canonical_status = run_root / "run-status.env"
        require(
            args.run_status.is_absolute()
            and args.run_status == canonical_status
            and args.run_status.resolve(strict=False) == canonical_status,
            "E_RUN_STATUS",
            "--run-status is not the canonical run status",
        )
        expected_hashes = {
            "coverage_gate_sha256": value["coverage_gate"]["sha256"],
            "candidate_manifest_sha256": value["candidate_manifest"]["sha256"],
            "final_manifest_sha256": sha256_file(args.artifact, "E_ACCEPTANCE_FINAL"),
        }
        validate_run_data(
            repo_root,
            value["run_id"],
            mode=mode,
            require_run_status=True,
            expected_artifact_hashes=expected_hashes,
        )
    else:
        reject("E_ACCEPTANCE_ARTIFACT", "acceptance artifact stage differs")
    print(f"[v934-coverage-xml] ARTIFACT VALID stage={stage} run={value['run_id']}")


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

    def cdiag_only_formal_policy(path: str, field: str) -> dict[str, Any]:
        policy: dict[str, Any] = {
            "parent_git_head_source": "aggregate_observed.evidence.git_head",
            "diagnostic_threshold_sha256": EXPECTED_DIAGNOSTIC_THRESHOLD_SHA256,
            "repository_identity": copy.deepcopy(FORMAL_REPOSITORY_IDENTITY_POLICY),
            "required_exact_paths": list(FORMALIZATION_EXACT_PATHS),
            "allowed_exact_paths": list(FORMALIZATION_EXACT_PATHS),
            "allowed_path_prefixes": list(FORMALIZATION_ALLOWED_PREFIXES),
            "other_changes": "forbidden-requires-new-diagnostic",
        }
        policy[field].append(path)
        return policy

    for cdiag_only_path in CDIAG_ONLY_STEP5_TOOLING_PATHS:
        for policy_field in ("required_exact_paths", "allowed_exact_paths"):
            case_name = (
                f"formal-cdiag-only-{policy_field.removesuffix('_exact_paths')}-"
                f"{Path(cdiag_only_path).name}"
            )
            cases[case_name] = expect_failure(
                "E_FORMAL_DELTA_POLICY",
                lambda cdiag_only_path=cdiag_only_path, policy_field=policy_field: validate_formal_delta_policy(
                    cdiag_only_formal_policy(cdiag_only_path, policy_field),
                    list(FORMALIZATION_EXACT_PATHS),
                ),
            )
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
    symlink_path.unlink()
    require(
        not symlink_path.exists() and not symlink_path.is_symlink(),
        "E_NEGATIVE_CLEANUP",
        "symlink XML negative fixture survived",
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

    validate_diagnostic = commands.add_parser(
        "validate-diagnostic-run",
        help="recompute and validate a sealed all-lane diagnostic run",
    )
    validate_diagnostic.add_argument("--repo-root", type=Path, required=True)
    validate_diagnostic.add_argument("--run-id", required=True)
    validate_diagnostic.set_defaults(function=validate_diagnostic_command)

    attest_diagnostic = commands.add_parser(
        "attest-git-safe-diagnostic",
        help="validate a sealed diagnostic source run and publish its safe hash-only attestation",
    )
    attest_diagnostic.add_argument("--repo-root", type=Path, required=True)
    attest_diagnostic.add_argument("--run-id", required=True)
    attest_diagnostic.set_defaults(function=attest_git_safe_diagnostic_command)

    build_capsule = commands.add_parser(
        "build-git-safe-diagnostic-capsule",
        help="build the two-member Git-safe capsule from a sealed diagnostic source run",
    )
    build_capsule.add_argument("--repo-root", type=Path, required=True)
    build_capsule.add_argument("--run-id", required=True)
    build_capsule.add_argument("--archive", type=Path, required=True)
    build_capsule.add_argument("--manifest", type=Path, required=True)
    build_capsule.set_defaults(function=build_git_safe_diagnostic_capsule_command)

    freeze = commands.add_parser(
        "freeze-thresholds",
        help="build an immutable reviewed-threshold candidate from a diagnostic run",
    )
    freeze.add_argument("--repo-root", type=Path, required=True)
    freeze.add_argument("--run-id", required=True)
    freeze.add_argument("--output", type=Path, required=True)
    freeze.set_defaults(function=freeze_thresholds_command)

    verify_threshold = commands.add_parser(
        "verify-threshold-candidate",
        help="recompute and verify a threshold freeze candidate",
    )
    verify_threshold.add_argument("--repo-root", type=Path, required=True)
    verify_threshold.add_argument("--candidate", type=Path, required=True)
    verify_threshold.set_defaults(function=verify_threshold_candidate_command)

    frozen_diagnostic = commands.add_parser(
        "validate-frozen-diagnostic",
        help="replay the confirmed diagnostic run against its commit-frozen inputs",
    )
    frozen_diagnostic.add_argument("--repo-root", type=Path, required=True)
    frozen_diagnostic.set_defaults(function=validate_frozen_diagnostic_command)

    seal_run = commands.add_parser(
        "seal-run",
        help="fully prevalidate and atomically publish the canonical success status",
    )
    seal_run.add_argument("--mode", choices=RUN_MODES, required=True)
    seal_run.add_argument("--repo-root", type=Path, required=True)
    seal_run.add_argument("--run-id", required=True)
    seal_run.set_defaults(function=seal_run_command)

    formal = commands.add_parser(
        "formal-check",
        help="apply confirmed exact coverage gates to a fresh formal or release run",
    )
    formal.add_argument("--mode", choices=ARTIFACT_MODES, default="formal")
    formal.add_argument("--repo-root", type=Path, required=True)
    formal.add_argument("--run-id", required=True)
    formal.add_argument("--output", type=Path, required=True)
    formal.set_defaults(function=formal_check_command)

    build_artifact = commands.add_parser(
        "build-artifact",
        help="build a formal or release acceptance candidate/final manifest",
    )
    build_artifact.add_argument("--mode", choices=ARTIFACT_MODES, default="formal")
    build_artifact.add_argument("--repo-root", type=Path, required=True)
    build_artifact.add_argument("--stage", choices=("candidate", "final"), required=True)
    build_artifact.add_argument("--run-id")
    build_artifact.add_argument("--coverage-gate", type=Path)
    build_artifact.add_argument("--candidate", type=Path)
    build_artifact.add_argument("--output", type=Path, required=True)
    build_artifact.set_defaults(function=build_artifact_command)

    verify_artifact = commands.add_parser(
        "verify-artifact",
        help="recompute a formal/release candidate or verify a sealed final",
    )
    verify_artifact.add_argument("--mode", choices=ARTIFACT_MODES)
    verify_artifact.add_argument("--repo-root", type=Path, required=True)
    verify_artifact.add_argument("--artifact", type=Path, required=True)
    verify_artifact.add_argument("--run-status", type=Path)
    verify_artifact.set_defaults(function=verify_artifact_command)

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
