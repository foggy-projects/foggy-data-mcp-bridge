#!/usr/bin/env python3
"""Build and verify the fail-closed v9.3.4 required-CI evidence contract."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import unicodedata
import warnings
import zipfile
from typing import Any, Callable, Iterable


TOOL_PATH = Path(__file__).resolve()
TOOL_DIR = TOOL_PATH.parent
CONTRACT_NAME = "ci-contract.json"
CONTRACT_PATH = TOOL_DIR / CONTRACT_NAME
TOOL_RELATIVE_PATH = Path("scripts/v934/step6/ci_contract_tool.py")
CONTRACT_RELATIVE_PATH = Path("scripts/v934/step6/ci-contract.json")
TOOLING_MANIFEST_RELATIVE_PATH = Path("scripts/v934/step6/SHA256SUMS")
STEP4_TOOLING_MANIFEST = Path("scripts/v934/step4/SHA256SUMS")
STEP4_TOOLING_MANIFEST_SHA256 = (
    "77cc8269eb23ec17bcefea33a59bd068e272f7cd22efbe11e907d1e1262919a1"
)
STEP4_DATABASE_AUTHORITY_MANIFEST = Path(
    "scripts/v934/step4/successor/database-authority-SHA256SUMS"
)
STEP5_TOOLING_MANIFEST = Path("scripts/v934/step5/SHA256SUMS")
STEP5_TOOLING_MANIFEST_SHA256 = (
    "ab06ad058ddfe226ada5250e0efcd731c39be71dd5c8e08c298c6e4ac9b2bc49"
)
DATABASE_AUTHORITY_TOOL = Path(
    "scripts/v934/step4/successor/database_matrix_report_tool.py"
)
DATABASE_AUTHORITY_CONTRACT = Path(
    "scripts/v934/step4/successor/database-matrix-contract.json"
)

STEP5_FROZEN_TOOLING_PATHS = (
    "foggy-mcp-launcher/Dockerfile.release",
    "scripts/v934/step5/final-promotion-contract.json",
    "scripts/v934/step5/pointer_tool.py",
    "scripts/v934/step5/portable_replay_tool.py",
    "scripts/v934/step5/release-artifact-contract.json",
    "scripts/v934/step5/release_artifact_tool.py",
    "scripts/v934/step5/release_package_tool.py",
    "scripts/verify-v934-release-gate.sh",
)

FROZEN_TOOLING_PATHS = (
    ".github/workflows/model-lifecycle-concurrency.yml",
    ".github/workflows/pivot-release-readiness.yml",
    ".github/workflows/release.yml",
    ".github/workflows/test-ci-evidence-chain.yml",
    "foggy-mcp-launcher/Dockerfile.release",
    "scripts/v934/step4/SHA256SUMS",
    "scripts/v934/step5/SHA256SUMS",
    "scripts/v934/step5/final-promotion-contract.json",
    "scripts/v934/step5/pointer_tool.py",
    "scripts/v934/step5/portable_replay_tool.py",
    "scripts/v934/step5/release-artifact-contract.json",
    "scripts/v934/step5/release_artifact_tool.py",
    "scripts/v934/step5/release_package_tool.py",
    "scripts/v934/step6/ci-contract.json",
    "scripts/v934/step6/ci_contract_tool.py",
    "scripts/verify-v934-release-gate.sh",
)

REQUIRED_JOBS = (
    "inventory-unit",
    "sqlite-integration",
    "database-matrix",
    "external-integration",
    "coverage",
    "package-evidence",
)
DB_KINDS = ("sqlite", "mysql57", "mysql8", "postgres15", "sqlserver2022")
RELEASE_VERSION = "9.3.4"
AUTHORITY_WORKFLOW_FILE = ".github/workflows/test-ci-evidence-chain.yml"
AUTHORITY_WORKFLOW_NAME = "9.3.4 Test & Release Evidence Chain"
AUTHORITY_JOB_NAMES = (
    "Inventory + Unit — single gate producer",
    "SQLite broad integration evidence",
    "Exact five-database artifact collector",
    "External integration evidence",
    "Immutable coverage collector/check",
    "Same-tested JAR and package evidence",
    "9.3.4 Test & Release Authority",
)
AUTHORITY_EVENTS = ("pull_request", "push", "workflow_dispatch", "workflow_call")
AUTHORITY_JOB_GRAPH = (
    ("inventory-unit", ()),
    ("sqlite-integration", ("inventory-unit",)),
    (
        "database-matrix",
        (
            "inventory-unit",
            "sqlite-integration",
            "external-integration",
            "coverage",
            "package-evidence",
        ),
    ),
    ("external-integration", ("inventory-unit",)),
    ("coverage", ("inventory-unit",)),
    ("package-evidence", ("inventory-unit",)),
    ("required-aggregator", REQUIRED_JOBS),
)
RELEASE_EVENTS = ("push", "workflow_dispatch")
RELEASE_JOB_GRAPH = (
    ("release-dry-run", ()),
    ("docker-publish", ("release-dry-run",)),
    ("github-release", ("release-dry-run", "docker-publish")),
)
RELEASE_JOB_NAMES = (
    "Same-tested JAR/archive/image dry run",
    "Publish verified runtime-only image",
    "Publish same-tested GitHub release assets",
)
TOOLING_CLOSURE_MANIFESTS = (
    "scripts/v934/step6/SHA256SUMS",
    "scripts/v934/step4/SHA256SUMS",
    "scripts/v934/step5/SHA256SUMS",
)
AUTHORITY_ARTIFACT_TEMPLATES = (
    "v934-gate-run-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-db-sqlite-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-db-mysql57-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-db-mysql8-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-db-postgres15-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-db-sqlserver2022-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-db-collector-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-portable-replay-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-tested-release-assets-{commit_sha}-{workflow_run_id}-{attempt}",
    "v934-required-receipt-{commit_sha}-{workflow_run_id}-{attempt}",
)
RELEASE_ARTIFACT_TEMPLATE = (
    "v934-tested-release-assets-{commit_sha}-{workflow_run_id}-{attempt}"
)
DB_EXPECTATIONS: dict[str, dict[str, object]] = {
    "sqlite": {"reports": 5, "testcase_nodes": 50, "variant_keys": ["db-sqlite"]},
    "mysql57": {"reports": 5, "testcase_nodes": 50, "variant_keys": ["db-mysql57"]},
    "mysql8": {
        "reports": 6,
        "testcase_nodes": 105,
        "variant_keys": ["db-mysql8", "mysql8-targeted"],
    },
    "postgres15": {
        "reports": 8,
        "testcase_nodes": 115,
        "variant_keys": ["db-postgres15", "postgres15-targeted"],
    },
    "sqlserver2022": {
        "reports": 5,
        "testcase_nodes": 50,
        "variant_keys": ["db-sqlserver2022"],
    },
}
AGGREGATOR_ID = "required-aggregator"
AGGREGATOR_NAME = "9.3.4 Test & Release Authority"
CELL_MANIFEST = "cell-manifest.json"
PAYLOAD_DIRECTORY = "payload"
RECEIPT_KIND = "v934-ci-aggregate-receipt"

SHA40 = re.compile(r"[0-9a-f]{40}")
SHA256 = re.compile(r"[0-9a-f]{64}")
SAFE_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
CONTROL = re.compile(r"[\x00-\x1f\x7f]")
MAX_POSITIVE_INTEGER = 9_223_372_036_854_775_807
MAX_JSON_BYTES = 16 * 1024 * 1024
MAX_ARTIFACT_ARCHIVE_BYTES = 1024 * 1024 * 1024
MAX_ARTIFACT_EXTRACTED_BYTES = 2 * 1024 * 1024 * 1024
MAX_ARTIFACT_ARCHIVE_ENTRIES = 64
RUNTIME_BASE_TAG_REFERENCE = "eclipse-temurin:17-jre-alpine"
RUNTIME_BASE_INDEX_DIGEST = (
    "sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57"
)
RUNTIME_BASE_MANIFEST_DIGEST = (
    "sha256:b658bee7bbf0277559bd07dfb2e8473c30dc90c3da0d8cfe568e61f52792ce52"
)
RUNTIME_BASE_CONFIG_DIGEST = (
    "sha256:af15432fe4678068270da7f69356edd1e53555f15671a6373ce44d9e65c2dfcc"
)
RUNTIME_BASE_PINNED_REFERENCE = (
    f"{RUNTIME_BASE_TAG_REFERENCE}@{RUNTIME_BASE_MANIFEST_DIGEST}"
)
RUNTIME_BASE_PLATFORM = {"os": "linux", "architecture": "amd64"}
EXTERNAL_ACTION_PINS = (
    {
        "repository": "actions/checkout",
        "commit_sha": "34e114876b0b11c390a56381ad16ebd13914f8d5",
        "authority_occurrences": 7,
        "release_occurrences": 3,
    },
    {
        "repository": "actions/upload-artifact",
        "commit_sha": "ea165f8d65b6e75b540449e92b4886f43607fa02",
        "authority_occurrences": 10,
        "release_occurrences": 1,
    },
    {
        "repository": "actions/download-artifact",
        "commit_sha": "d3f86a106a0bac45b974a628896c90dbdf5c8093",
        "authority_occurrences": 14,
        "release_occurrences": 0,
    },
    {
        "repository": "docker/login-action",
        "commit_sha": "c94ce9fb468520275223c153574b00df6fe4bcc9",
        "authority_occurrences": 0,
        "release_occurrences": 1,
    },
    {
        "repository": "softprops/action-gh-release",
        "commit_sha": "3bb12739c298aeb8a4eeaf626c5b8d85266b0e65",
        "authority_occurrences": 0,
        "release_occurrences": 1,
    },
)

SECRET_PATTERNS = (
    re.compile(
        rb"(?i)(?:[\"']?)(?:password|passwd|secret|token|api[_-]?key|authorization|credential)"
        rb"(?:[\"']?)[ \t]*[:=][ \t]*(?:[\"']?)(?!redacted(?:[\"']|\s|$)|null(?:[\"']|\s|$)|\*{3,})"
        rb"[^\s\"',;<>]{4,}"
    ),
    re.compile(rb"(?i)[a-z][a-z0-9+.-]*://[^/\s:@]+:[^/\s@]+@"),
    re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    re.compile(rb"(?:github_pat_|gh[pousr]_|glpat-)[A-Za-z0-9_-]{12,}"),
    re.compile(rb"AKIA[0-9A-Z]{16}"),
)


class ContractError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def reject(code: str, message: str) -> None:
    raise ContractError(code, message)


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


def unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            reject("E_JSON_DUPLICATE", f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json_bytes(data: bytes, label: str) -> dict[str, Any]:
    try:
        decoded = data.decode("utf-8")
        value = json.loads(
            decoded,
            object_pairs_hook=unique_object,
            parse_constant=lambda token: reject(
                "E_JSON_TYPE", f"{label} contains a non-finite number: {token}"
            ),
        )
    except ContractError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        reject("E_JSON", f"cannot parse {label}: {error}")
    require(type(value) is dict, "E_JSON_TYPE", f"{label} must be a JSON object")
    return value


def exact_keys(
    value: dict[str, Any], expected: Iterable[str], code: str, label: str
) -> None:
    expected_set = set(expected)
    actual_set = set(value)
    require(
        actual_set == expected_set,
        code,
        f"{label} keys differ: missing={sorted(expected_set - actual_set)} "
        f"extra={sorted(actual_set - expected_set)}",
    )


def strict_equal(actual: object, expected: object) -> bool:
    if type(actual) is not type(expected):
        return False
    if type(expected) is dict:
        actual_dict = actual
        expected_dict = expected
        assert isinstance(actual_dict, dict) and isinstance(expected_dict, dict)
        return set(actual_dict) == set(expected_dict) and all(
            strict_equal(actual_dict[key], expected_dict[key]) for key in expected_dict
        )
    if type(expected) is list:
        actual_list = actual
        expected_list = expected
        assert isinstance(actual_list, list) and isinstance(expected_list, list)
        return len(actual_list) == len(expected_list) and all(
            strict_equal(left, right)
            for left, right in zip(actual_list, expected_list)
        )
    return actual == expected


def expected_contract() -> dict[str, object]:
    return {
        "schema_version": 1,
        "kind": "v934-ci-contract",
        "tooling_manifest": {
            "path": TOOLING_MANIFEST_RELATIVE_PATH.as_posix(),
            "exact_paths": list(FROZEN_TOOLING_PATHS),
            "cardinality": len(FROZEN_TOOLING_PATHS),
            "step4_manifest": STEP4_TOOLING_MANIFEST.as_posix(),
            "step4_manifest_sha256": STEP4_TOOLING_MANIFEST_SHA256,
            "step5_manifest": "scripts/v934/step5/SHA256SUMS",
            "step5_manifest_sha256": STEP5_TOOLING_MANIFEST_SHA256,
            "policy": "exact-path-order-and-byte-digest",
        },
        "required_upstream_job_ids": list(REQUIRED_JOBS),
        "aggregator": {
            "id": AGGREGATOR_ID,
            "name": AGGREGATOR_NAME,
            "always_run": True,
            "required_result": "success",
        },
        "workflow_structure": {
            "authority": {
                "events": list(AUTHORITY_EVENTS),
                "push_branches": ["main"],
                "jobs": [
                    {"id": job_id, "needs": list(needs)}
                    for job_id, needs in AUTHORITY_JOB_GRAPH
                ],
            },
            "release": {
                "events": list(RELEASE_EVENTS),
                "push_tags": ["v9.3.4"],
                "jobs": [
                    {"id": job_id, "needs": list(needs)}
                    for job_id, needs in RELEASE_JOB_GRAPH
                ],
            },
            "tooling_closure": {
                "authority_job_count": len(AUTHORITY_JOB_GRAPH),
                "release_job_count": len(RELEASE_JOB_GRAPH),
                "manifest_order": list(TOOLING_CLOSURE_MANIFESTS),
                "validator": "python3-$CI_TOOL-validate-workflows",
            },
        },
        "database_artifacts": {
            "kinds": list(DB_KINDS),
            "cardinality": 5,
            "logical_name_template": (
                "v934-db-{kind}-{commit_sha}-{workflow_run_id}-{attempt}"
            ),
            "manifest_file": CELL_MANIFEST,
            "payload_directory": PAYLOAD_DIRECTORY,
            "authority_input": {
                "kind": "v934-step3-database-matrix-final",
                "verifier": (
                    "scripts/v934/step4/successor/"
                    "database_matrix_report_tool.py verify-final"
                ),
                "selection": (
                    "outer-marker+merged-manifest+metrics+exact-cell+exact-variants"
                ),
            },
            "cell_expectations": [
                {"db_kind": kind, **DB_EXPECTATIONS[kind]} for kind in DB_KINDS
            ],
        },
        "identity": {
            "commit_sha": "lowercase-40-hex",
            "workflow_run_id": "positive-json-integer",
            "attempt": "positive-json-integer",
            "authority_run_id": "safe-id",
        },
        "job_states": {
            "format": "exact-job-id-to-result-object",
            "accepted_result": "success",
            "terminal_results": ["success", "failure", "skipped", "cancelled"],
        },
        "receipt": {
            "kind": RECEIPT_KIND,
            "write_policy": "atomic-create-no-replace",
        },
        "release_authority_source": {
            "version": RELEASE_VERSION,
            "workflow_file": AUTHORITY_WORKFLOW_FILE,
            "workflow_name": AUTHORITY_WORKFLOW_NAME,
            "event": "push",
            "branch": "main",
            "gate_mode": "authority",
            "required_job_names": list(AUTHORITY_JOB_NAMES),
            "artifact_name_templates": list(AUTHORITY_ARTIFACT_TEMPLATES),
            "artifact_cardinality": len(AUTHORITY_ARTIFACT_TEMPLATES),
            "release_artifact_name_template": RELEASE_ARTIFACT_TEMPLATE,
            "artifact_transport": {
                "selector": "github-rest-artifact-id",
                "archive_format": "zip",
                "digest": "sha256:lowercase-64-hex",
                "digest_mismatch": "fail",
                "extraction": "canonical-framing-exact-set-no-traversal-no-symlink-no-duplicate",
                "framing": "unique-eocd-at-eof-no-comment-no-zip64-exact-central-and-contiguous-local-records-no-prefix-trailing-concatenation",
            },
        },
        "runtime_base_image": {
            "tag_reference": RUNTIME_BASE_TAG_REFERENCE,
            "pinned_reference": RUNTIME_BASE_PINNED_REFERENCE,
            "index_digest": RUNTIME_BASE_INDEX_DIGEST,
            "manifest_digest": RUNTIME_BASE_MANIFEST_DIGEST,
            "config_digest": RUNTIME_BASE_CONFIG_DIGEST,
            "platform": dict(RUNTIME_BASE_PLATFORM),
        },
        "external_action_pins": [dict(row) for row in EXTERNAL_ACTION_PINS],
        "workflow_actions": {
            "authority_local_uses": "forbidden",
            "release_local_uses": "forbidden",
            "external_reference": "full-commit-sha",
        },
        "security": {
            "json_duplicate_keys": "reject",
            "non_integer_numeric_identity": "reject",
            "symlink_or_special": "reject",
            "path_traversal": "reject",
            "secret_material": "reject",
        },
    }


def absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path)))


def path_exists(path: Path) -> bool:
    return os.path.lexists(os.fspath(path))


def real_directory(path: Path, label: str) -> Path:
    candidate = absolute(path)
    try:
        metadata = os.lstat(candidate)
    except FileNotFoundError:
        reject("E_DIRECTORY", f"missing {label}: {candidate}")
    require(stat.S_ISDIR(metadata.st_mode), "E_DIRECTORY", f"{label} is not a real directory")
    require(not stat.S_ISLNK(metadata.st_mode), "E_SYMLINK", f"{label} is symlinked")
    try:
        resolved = candidate.resolve(strict=True)
    except OSError as error:
        reject("E_DIRECTORY", f"cannot resolve {label}: {error}")
    require(resolved == candidate, "E_SYMLINK", f"{label} has symlinked components")
    return candidate


def validate_output_target(path: Path, label: str) -> tuple[Path, Path]:
    target = absolute(path)
    parent = real_directory(target.parent, f"{label} parent")
    require(not path_exists(target), "E_OUTPUT_EXISTS", f"{label} already exists: {target}")
    require(target.name not in {"", ".", ".."}, "E_PATH", f"invalid {label} name")
    return target, parent


def normalize_relative_path(raw: str, label: str) -> str:
    require(type(raw) is str and bool(raw), "E_PATH", f"{label} must be a non-empty string")
    require(len(raw.encode("utf-8")) <= 4096, "E_PATH", f"{label} is too long")
    require(raw == unicodedata.normalize("NFC", raw), "E_PATH", f"{label} is not NFC-normalized")
    require("\\" not in raw, "E_PATH", f"{label} contains a backslash")
    require(CONTROL.search(raw) is None, "E_PATH", f"{label} contains a control character")
    pure = PurePosixPath(raw)
    require(not pure.is_absolute(), "E_PATH", f"{label} is absolute")
    parts = raw.split("/")
    require(
        all(part not in {"", ".", ".."} for part in parts),
        "E_PATH",
        f"{label} contains an empty/current/parent component",
    )
    require(pure.as_posix() == raw, "E_PATH", f"{label} is not canonical")
    return raw


def register_casefold(path: str, seen: dict[str, str]) -> None:
    folded = path.casefold()
    previous = seen.get(folded)
    require(
        previous is None or previous == path,
        "E_PATH_COLLISION",
        f"case-folded paths collide: {previous!r}, {path!r}",
    )
    seen[folded] = path


class SecretScanner:
    def __init__(self, label: str):
        self.label = label
        self.tail = b""

    def feed(self, chunk: bytes) -> None:
        window = self.tail + chunk
        for pattern in SECRET_PATTERNS:
            if pattern.search(window) is not None:
                reject("E_SECRET", f"secret-shaped material found in {self.label}")
        self.tail = window[-1024:]


def secure_file_binding(
    path: Path,
    label: str,
    *,
    maximum: int | None = None,
    scan_secrets: bool = True,
) -> tuple[str, int]:
    candidate = absolute(path)
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(candidate, flags)
    except FileNotFoundError:
        reject("E_FILE_MISSING", f"missing {label}: {candidate}")
    except OSError as error:
        reject("E_FILE_OPEN", f"cannot open {label}: {candidate}: {error}")
    try:
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not a regular file")
        if maximum is not None:
            require(before.st_size <= maximum, "E_FILE_SIZE", f"{label} exceeds size limit")
        digest = hashlib.sha256()
        scanner = SecretScanner(label) if scan_secrets else None
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_READ", f"short read from {label}")
            digest.update(chunk)
            if scanner is not None:
                scanner.feed(chunk)
            remaining -= len(chunk)
        require(os.read(descriptor, 1) == b"", "E_FILE_READ", f"{label} grew while read")
        after = os.fstat(descriptor)
        try:
            current = os.lstat(candidate)
        except FileNotFoundError:
            reject("E_FILE_RACE", f"{label} disappeared while read")
        identity_before = (
            before.st_dev,
            before.st_ino,
            before.st_size,
            before.st_mtime_ns,
            before.st_ctime_ns,
        )
        identity_after = (
            after.st_dev,
            after.st_ino,
            after.st_size,
            after.st_mtime_ns,
            after.st_ctime_ns,
        )
        require(identity_before == identity_after, "E_FILE_RACE", f"{label} changed while read")
        require(
            (current.st_dev, current.st_ino, current.st_size)
            == (after.st_dev, after.st_ino, after.st_size),
            "E_FILE_RACE",
            f"{label} path identity changed while read",
        )
        return digest.hexdigest(), before.st_size
    finally:
        os.close(descriptor)


def secure_regular_bytes(path: Path, label: str, maximum: int = MAX_JSON_BYTES) -> bytes:
    candidate = absolute(path)
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(candidate, flags)
    except FileNotFoundError:
        reject("E_FILE_MISSING", f"missing {label}: {candidate}")
    except OSError as error:
        reject("E_FILE_OPEN", f"cannot open {label}: {candidate}: {error}")
    try:
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not a regular file")
        require(before.st_size <= maximum, "E_FILE_SIZE", f"{label} exceeds size limit")
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_READ", f"short read from {label}")
            chunks.append(chunk)
            remaining -= len(chunk)
        require(os.read(descriptor, 1) == b"", "E_FILE_READ", f"{label} grew while read")
        after = os.fstat(descriptor)
        try:
            current = os.lstat(candidate)
        except FileNotFoundError:
            reject("E_FILE_RACE", f"{label} disappeared while read")
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
        require(
            (current.st_dev, current.st_ino, current.st_size)
            == (after.st_dev, after.st_ino, after.st_size),
            "E_FILE_RACE",
            f"{label} path identity changed while read",
        )
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def current_head(repo_root: Path) -> str:
    try:
        top_level = subprocess.run(
            ["git", "-c", "core.fsmonitor=false", "-C", str(repo_root), "rev-parse", "--show-toplevel"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=sanitized_subprocess_environment(),
        )
        completed = subprocess.run(
            ["git", "-c", "core.fsmonitor=false", "-C", str(repo_root), "rev-parse", "--verify", "HEAD^{commit}"],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            env=sanitized_subprocess_environment(),
        )
    except (OSError, subprocess.CalledProcessError) as error:
        reject("E_GIT", f"cannot resolve repository HEAD: {error}")
    require(
        Path(top_level.stdout.strip()).resolve() == repo_root.resolve(),
        "E_GIT",
        "Git top-level directory differs from the repository root",
    )
    head = completed.stdout.strip()
    require(SHA40.fullmatch(head) is not None, "E_GIT", f"invalid repository HEAD: {head!r}")
    return head


def sanitized_subprocess_environment() -> dict[str, str]:
    """Return an environment that cannot redirect Git or Python identity."""

    environment = {
        key: value
        for key, value in os.environ.items()
        if not key.startswith("GIT_") and key not in {"PYTHONHOME", "PYTHONPATH"}
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


def validate_bound_sha256_manifest(
    repo_root: Path,
    manifest_relative: Path,
    label: str,
    *,
    expected_sha256: str | None = None,
    expected_paths: tuple[str, ...] | None = None,
    validate_targets: bool = True,
) -> dict[str, Any]:
    manifest_path = absolute(repo_root / manifest_relative)
    raw = secure_regular_bytes(manifest_path, label, maximum=1024 * 1024)
    manifest_sha256 = sha256_bytes(raw)
    if expected_sha256 is not None:
        require(
            manifest_sha256 == expected_sha256,
            "E_TOOLING_DRIFT",
            f"{label} digest differs",
        )
    try:
        text = raw.decode("ascii")
    except UnicodeDecodeError as error:
        reject("E_TOOLING_MANIFEST", f"{label} is not ASCII: {error}")
    require(
        bool(text) and text.endswith("\n") and "\r" not in text,
        "E_TOOLING_MANIFEST",
        f"{label} must use non-empty newline-terminated LF rows",
    )
    rows: dict[str, str] = {}
    order: list[str] = []
    for number, line in enumerate(text.splitlines(), 1):
        match = re.fullmatch(
            r"([0-9a-f]{64})  ([A-Za-z0-9.][A-Za-z0-9._/-]*)", line
        )
        require(
            match is not None,
            "E_TOOLING_MANIFEST",
            f"malformed {label} row {number}",
        )
        assert match is not None
        expected, relative = match.groups()
        normalized = normalize_relative_path(relative, f"{label} row {number}")
        require(
            normalized == relative and relative not in rows,
            "E_TOOLING_MANIFEST",
            f"duplicate/non-canonical {label} path: {relative}",
        )
        rows[relative] = expected
        order.append(relative)
    require(
        order == sorted(order, key=lambda value: value.encode("utf-8")),
        "E_TOOLING_MANIFEST",
        f"{label} path order differs",
    )
    if expected_paths is not None:
        require(
            tuple(order) == expected_paths,
            "E_TOOLING_MANIFEST",
            f"{label} exact path set differs",
        )
    for relative, expected in rows.items() if validate_targets else ():
        candidate = absolute(repo_root / PurePosixPath(relative))
        try:
            resolved = candidate.resolve(strict=True)
        except OSError as error:
            reject(
                "E_TOOLING_DRIFT",
                f"cannot resolve {label} target {relative}: {error}",
            )
        require(
            resolved == candidate,
            "E_TOOLING_DRIFT",
            f"{label} target is symlinked: {relative}",
        )
        observed, _ = secure_file_binding(
            candidate,
            f"{label} target {relative}",
            maximum=8 * 1024 * 1024,
            scan_secrets=False,
        )
        require(
            observed == expected,
            "E_TOOLING_DRIFT",
            f"{label} target digest differs: {relative}",
        )
    return {
        "entries": dict(rows),
        "sha256": manifest_sha256,
        "paths": len(rows),
        "status": "passed",
    }


def validate_database_authority_tooling(
    repo_root: Path,
    *,
    expected_head: str,
    expected_tool_sha256: str,
    expected_contract_sha256: str,
    expected_manifest_sha256: str,
) -> dict[str, Any]:
    """Delegate canonical source identity to the bound successor verifier.

    The authority manifest records canonical Git blob bytes, while checkout
    policy may project those text blobs to LF, CRLF, or a mixture of both.
    Step 4's bound database adapter proves strict worktree-to-HEAD EOL
    equivalence and rejects every other content or file-set drift.  Reusing it
    here prevents Step 6 from reintroducing a raw-worktree-only identity.
    """

    tool = absolute(repo_root / DATABASE_AUTHORITY_TOOL)
    tool_sha256_before, _ = secure_file_binding(
        tool,
        "database authority verifier",
        maximum=8 * 1024 * 1024,
        scan_secrets=False,
    )
    require(
        tool_sha256_before == expected_tool_sha256,
        "E_TOOLING_DRIFT",
        "database authority verifier digest differs before execution",
    )
    try:
        completed = subprocess.run(
            [
                sys.executable,
                str(tool),
                "validate-authority",
                "--expected-git-head",
                expected_head,
                "--expected-contract-sha256",
                expected_contract_sha256,
                "--expected-manifest-sha256",
                expected_manifest_sha256,
            ],
            cwd=repo_root,
            env=sanitized_subprocess_environment(),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=120,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        reject("E_TOOLING_DRIFT", f"database authority verifier could not run: {error}")
    require(
        completed.returncode == 0,
        "E_TOOLING_DRIFT",
        "database authority verifier rejected the worktree: "
        + completed.stderr.decode("utf-8", errors="replace").strip()[:2000],
    )
    require(
        completed.stderr == b"",
        "E_TOOLING_DRIFT",
        "database authority verifier emitted unexpected stderr",
    )
    tool_sha256_after, _ = secure_file_binding(
        tool,
        "database authority verifier",
        maximum=8 * 1024 * 1024,
        scan_secrets=False,
    )
    require(
        tool_sha256_after == expected_tool_sha256,
        "E_TOOLING_DRIFT",
        "database authority verifier digest differs after execution",
    )
    require(
        current_head(repo_root) == expected_head,
        "E_TOOLING_DRIFT",
        "repository HEAD changed while validating database authority",
    )
    receipt = load_json_bytes(completed.stdout, "database authority verifier receipt")
    require(
        set(receipt)
        == {
            "authority_manifest_sha256",
            "contract_sha256",
            "git_head",
            "paths",
            "status",
        }
        and receipt.get("authority_manifest_sha256") == expected_manifest_sha256
        and receipt.get("contract_sha256") == expected_contract_sha256
        and receipt.get("git_head") == expected_head
        and type(receipt.get("paths")) is int
        and receipt["paths"] == 69
        and receipt.get("status") == "passed",
        "E_TOOLING_DRIFT",
        "database authority verifier receipt differs",
    )
    return receipt


def validate_tooling_manifest(
    repo_root: Path, manifest_bytes: bytes | None = None
) -> dict[str, Any]:
    manifest_path = repo_root / TOOLING_MANIFEST_RELATIVE_PATH
    raw = (
        secure_regular_bytes(
            manifest_path, "Step 6 tooling manifest", maximum=1024 * 1024
        )
        if manifest_bytes is None
        else manifest_bytes
    )
    require(
        len(raw) <= 1024 * 1024,
        "E_TOOLING_MANIFEST",
        "Step 6 tooling manifest exceeds its size budget",
    )
    try:
        text = raw.decode("ascii")
    except UnicodeDecodeError as error:
        reject("E_TOOLING_MANIFEST", f"Step 6 tooling manifest is not ASCII: {error}")
    require(
        text.endswith("\n") and "\r" not in text,
        "E_TOOLING_MANIFEST",
        "Step 6 tooling manifest must use newline-terminated LF rows",
    )
    rows: dict[str, str] = {}
    order: list[str] = []
    for number, line in enumerate(text.splitlines(), 1):
        match = re.fullmatch(
            r"([0-9a-f]{64})  ([A-Za-z0-9.][A-Za-z0-9._/-]*)", line
        )
        require(
            match is not None,
            "E_TOOLING_MANIFEST",
            f"malformed Step 6 tooling row {number}",
        )
        assert match is not None
        digest, relative = match.groups()
        require(
            relative not in rows
            and "//" not in relative
            and "/./" not in relative
            and ".." not in relative.split("/"),
            "E_TOOLING_MANIFEST",
            f"duplicate/unsafe Step 6 tooling path: {relative}",
        )
        rows[relative] = digest
        order.append(relative)
    require(
        tuple(order) == FROZEN_TOOLING_PATHS,
        "E_TOOLING_MANIFEST",
        "Step 6 tooling manifest exact path/order differs",
    )
    for relative, expected in rows.items():
        candidate = absolute(repo_root / PurePosixPath(relative))
        try:
            resolved = candidate.resolve(strict=True)
        except OSError as error:
            reject(
                "E_TOOLING_DRIFT",
                f"cannot resolve frozen Step 6 tooling {relative}: {error}",
            )
        require(
            resolved == candidate,
            "E_TOOLING_DRIFT",
            f"frozen Step 6 tooling path is symlinked: {relative}",
        )
        observed, _ = secure_file_binding(
            candidate,
            f"frozen Step 6 tooling {relative}",
            maximum=8 * 1024 * 1024,
            scan_secrets=False,
        )
        require(
            observed == expected,
            "E_TOOLING_DRIFT",
            f"frozen Step 6 tooling digest differs: {relative}",
        )
    step4 = validate_bound_sha256_manifest(
        repo_root,
        STEP4_TOOLING_MANIFEST,
        "Step 4 tooling manifest",
        expected_sha256=STEP4_TOOLING_MANIFEST_SHA256,
    )
    step4_entries = step4["entries"]
    require(
        isinstance(step4_entries, dict)
        and DATABASE_AUTHORITY_TOOL.as_posix() in step4_entries
        and DATABASE_AUTHORITY_CONTRACT.as_posix() in step4_entries
        and STEP4_DATABASE_AUTHORITY_MANIFEST.as_posix() in step4_entries,
        "E_TOOLING_MANIFEST",
        "Step 4 manifest lacks database authority closure",
    )
    expected_head = current_head(repo_root)
    expected_tool_sha256 = step4_entries[DATABASE_AUTHORITY_TOOL.as_posix()]
    expected_contract_sha256 = step4_entries[DATABASE_AUTHORITY_CONTRACT.as_posix()]
    expected_manifest_sha256 = step4_entries[
        STEP4_DATABASE_AUTHORITY_MANIFEST.as_posix()
    ]
    database_authority = validate_bound_sha256_manifest(
        repo_root,
        STEP4_DATABASE_AUTHORITY_MANIFEST,
        "Step 4 database-authority tooling manifest",
        expected_sha256=expected_manifest_sha256,
        validate_targets=False,
    )
    validate_database_authority_tooling(
        repo_root,
        expected_head=expected_head,
        expected_tool_sha256=expected_tool_sha256,
        expected_contract_sha256=expected_contract_sha256,
        expected_manifest_sha256=expected_manifest_sha256,
    )
    step5 = validate_bound_sha256_manifest(
        repo_root,
        STEP5_TOOLING_MANIFEST,
        "Step 5 tooling manifest",
        expected_sha256=STEP5_TOOLING_MANIFEST_SHA256,
        expected_paths=STEP5_FROZEN_TOOLING_PATHS,
    )
    return {
        "sha256": sha256_bytes(raw),
        "step4_manifest_sha256": step4["sha256"],
        "step4_database_authority_paths": database_authority["paths"],
        "step5_manifest_sha256": step5["sha256"],
        "paths": len(rows),
        "status": "passed",
    }


def load_context(repo_root_value: Path) -> dict[str, Any]:
    repo_root = real_directory(repo_root_value, "repository root")
    repository_tool = absolute(repo_root / TOOL_RELATIVE_PATH)
    repository_contract = absolute(repo_root / CONTRACT_RELATIVE_PATH)
    database_authority_tool = absolute(repo_root / DATABASE_AUTHORITY_TOOL)
    require(repository_tool == TOOL_PATH, "E_REPOSITORY", "invoked tool is not the repository tool")
    require(repository_contract == CONTRACT_PATH, "E_REPOSITORY", "contract path differs from repository contract")
    tool_sha256, _ = secure_file_binding(repository_tool, "CI contract tool", scan_secrets=False)
    database_authority_tool_sha256, _ = secure_file_binding(
        database_authority_tool,
        "database authority verifier",
        scan_secrets=False,
    )
    contract_bytes = secure_regular_bytes(repository_contract, "CI contract", maximum=1024 * 1024)
    contract = load_json_bytes(contract_bytes, "CI contract")
    require(strict_equal(contract, expected_contract()), "E_CONTRACT", "CI contract differs from the frozen contract")
    tooling = validate_tooling_manifest(repo_root)
    return {
        "repo_root": repo_root,
        "head": current_head(repo_root),
        "contract": contract,
        "contract_sha256": sha256_bytes(contract_bytes),
        "tool_sha256": tool_sha256,
        "tooling_manifest_sha256": tooling["sha256"],
        "database_authority_tool": database_authority_tool,
        "database_authority_tool_sha256": database_authority_tool_sha256,
    }


def validate_commit_sha(value: object, label: str = "commit SHA") -> str:
    require(type(value) is str and SHA40.fullmatch(value) is not None, "E_IDENTITY", f"invalid {label}")
    return value


def validate_run_id(value: object, label: str = "authority run id") -> str:
    require(
        type(value) is str
        and SAFE_ID.fullmatch(value) is not None
        and value not in {".", ".."},
        "E_IDENTITY",
        f"invalid {label}",
    )
    return value


def validate_positive_integer(value: object, label: str) -> int:
    require(
        type(value) is int and 1 <= value <= MAX_POSITIVE_INTEGER,
        "E_JSON_TYPE",
        f"{label} must be a positive JSON integer",
    )
    return value


def parse_positive_cli(value: str, label: str) -> int:
    require(
        re.fullmatch(r"[1-9][0-9]{0,18}", value) is not None,
        "E_ARGUMENT",
        f"{label} must be a positive decimal integer",
    )
    parsed = int(value)
    require(parsed <= MAX_POSITIVE_INTEGER, "E_ARGUMENT", f"{label} is too large")
    return parsed


def validate_db_kind(value: object) -> str:
    require(type(value) is str and value in DB_KINDS, "E_DB_KIND", f"invalid database kind: {value!r}")
    return value


def validate_cli_identity(
    context: dict[str, Any],
    commit_sha: str,
    workflow_run_id: str,
    attempt: str,
    authority_run_id: str,
) -> dict[str, object]:
    commit = validate_commit_sha(commit_sha)
    require(commit == context["head"], "E_COMMIT", "commit SHA does not match repository HEAD")
    return {
        "authority_run_id": validate_run_id(authority_run_id),
        "commit_sha": commit,
        "workflow_run_id": parse_positive_cli(workflow_run_id, "workflow run id"),
        "attempt": parse_positive_cli(attempt, "attempt"),
    }


def artifact_name(db_kind: str, identity: dict[str, object]) -> str:
    return (
        f"v934-db-{db_kind}-{identity['commit_sha']}-"
        f"{identity['workflow_run_id']}-{identity['attempt']}"
    )


def scan_tree(root_value: Path, label: str) -> tuple[list[str], list[str]]:
    root = real_directory(root_value, label)
    files: list[str] = []
    directories: list[str] = []
    casefolded: dict[str, str] = {}

    def visit(directory: Path, prefix: str) -> None:
        try:
            entries = sorted(os.scandir(directory), key=lambda entry: entry.name.encode("utf-8"))
        except (OSError, UnicodeEncodeError) as error:
            reject("E_DIRECTORY", f"cannot scan {label}: {error}")
        for entry in entries:
            relative = f"{prefix}/{entry.name}" if prefix else entry.name
            normalize_relative_path(relative, f"{label} path")
            register_casefold(relative, casefolded)
            try:
                metadata = entry.stat(follow_symlinks=False)
            except OSError as error:
                reject("E_FILE_RACE", f"cannot stat {label} entry {relative}: {error}")
            if entry.is_symlink():
                reject("E_SYMLINK", f"symlink is prohibited in {label}: {relative}")
            if stat.S_ISDIR(metadata.st_mode):
                directories.append(relative)
                visit(Path(entry.path), relative)
            elif stat.S_ISREG(metadata.st_mode):
                files.append(relative)
            else:
                reject("E_SPECIAL", f"special file is prohibited in {label}: {relative}")

    visit(root, "")
    return files, directories


def paths_overlap(left: Path, right: Path) -> bool:
    left_abs = absolute(left)
    right_abs = absolute(right)
    try:
        left_abs.relative_to(right_abs)
        return True
    except ValueError:
        pass
    try:
        right_abs.relative_to(left_abs)
        return True
    except ValueError:
        return False


def copy_regular_with_binding(source: Path, destination: Path, label: str) -> tuple[str, int]:
    source_path = absolute(source)
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        source_fd = os.open(source_path, flags)
    except OSError as error:
        reject("E_FILE_OPEN", f"cannot open {label}: {error}")
    destination_fd: int | None = None
    try:
        before = os.fstat(source_fd)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not a regular file")
        destination.parent.mkdir(parents=True, exist_ok=True)
        try:
            destination_fd = os.open(
                destination,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_CLOEXEC", 0),
                0o600,
            )
        except OSError as error:
            reject("E_OUTPUT", f"cannot create copied evidence file: {error}")
        digest = hashlib.sha256()
        scanner = SecretScanner(label)
        remaining = before.st_size
        while remaining:
            chunk = os.read(source_fd, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_READ", f"short read from {label}")
            digest.update(chunk)
            scanner.feed(chunk)
            view = memoryview(chunk)
            while view:
                written = os.write(destination_fd, view)
                require(written > 0, "E_OUTPUT", f"short write while copying {label}")
                view = view[written:]
            remaining -= len(chunk)
        require(os.read(source_fd, 1) == b"", "E_FILE_READ", f"{label} grew while copied")
        after = os.fstat(source_fd)
        try:
            current = os.lstat(source_path)
        except FileNotFoundError:
            reject("E_FILE_RACE", f"{label} disappeared while copied")
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
            f"{label} changed while copied",
        )
        require(
            (current.st_dev, current.st_ino, current.st_size)
            == (after.st_dev, after.st_ino, after.st_size),
            "E_FILE_RACE",
            f"{label} path identity changed while copied",
        )
        os.fsync(destination_fd)
        os.fchmod(destination_fd, 0o644)
        return digest.hexdigest(), before.st_size
    finally:
        if destination_fd is not None:
            os.close(destination_fd)
        os.close(source_fd)


def write_exclusive_file(path: Path, data: bytes, mode: int = 0o644) -> None:
    try:
        descriptor = os.open(
            path,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_CLOEXEC", 0),
            0o600,
        )
    except OSError as error:
        reject("E_OUTPUT", f"cannot create output file {path.name}: {error}")
    try:
        view = memoryview(data)
        while view:
            written = os.write(descriptor, view)
            require(written > 0, "E_OUTPUT", f"short write to {path.name}")
            view = view[written:]
        os.fsync(descriptor)
        os.fchmod(descriptor, mode)
    finally:
        os.close(descriptor)


def fsync_directory(path: Path) -> None:
    flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_CLOEXEC", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as error:
        reject("E_OUTPUT", f"cannot open output directory for fsync: {error}")
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def atomic_new_file(path: Path, data: bytes) -> None:
    target, parent = validate_output_target(path, "receipt")
    temporary_fd, temporary_name = tempfile.mkstemp(prefix=f".{target.name}.", suffix=".tmp", dir=parent)
    temporary = Path(temporary_name)
    published = False
    try:
        view = memoryview(data)
        while view:
            written = os.write(temporary_fd, view)
            require(written > 0, "E_OUTPUT", "short write to receipt temporary file")
            view = view[written:]
        os.fsync(temporary_fd)
        os.fchmod(temporary_fd, 0o644)
        os.close(temporary_fd)
        temporary_fd = -1
        try:
            os.link(temporary, target, follow_symlinks=False)
        except FileExistsError:
            reject("E_OUTPUT_EXISTS", f"receipt already exists: {target}")
        except OSError as error:
            reject("E_OUTPUT", f"cannot atomically publish receipt: {error}")
        published = True
        os.unlink(temporary)
        fsync_directory(parent)
    finally:
        if temporary_fd >= 0:
            os.close(temporary_fd)
        if path_exists(temporary):
            os.unlink(temporary)
        if not published and path_exists(target):
            # A target created by another writer is never removed here.
            pass


def publish_new_directory(temporary: Path, target: Path, parent: Path) -> None:
    require(not path_exists(target), "E_OUTPUT_EXISTS", f"output directory already exists: {target}")
    try:
        os.rename(temporary, target)
    except FileExistsError:
        reject("E_OUTPUT_EXISTS", f"output directory already exists: {target}")
    except OSError as error:
        reject("E_OUTPUT", f"cannot publish output directory: {error}")
    fsync_directory(parent)


def validate_database_authority(
    context: dict[str, Any],
    evidence_dir: Path,
    db_kind: str,
    identity: dict[str, object],
) -> tuple[list[str], dict[str, object]]:
    """Verify one live Step 3 final bundle, then select one exact DB cell.

    The Step 3 verifier is deliberately executed before any CI artifact is
    published.  It replays exact XML freshness, hashes, schemas, five-cell
    cardinality and F0/E0/S0 against the current commit.  A downloaded CI cell
    is therefore a compact attestation of a fully verified live authority
    bundle, rather than an arbitrary directory stamped green by this tool.
    """

    outer_marker = evidence_dir / "outer-run-marker.json"
    merged_manifest = evidence_dir / "report-manifest.json"
    metrics_path = evidence_dir / "report-metrics.tsv"
    for path, label in (
        (outer_marker, "database outer marker"),
        (merged_manifest, "database merged manifest"),
        (metrics_path, "database report metrics"),
    ):
        secure_file_binding(path, label, scan_secrets=True)

    try:
        completed = subprocess.run(
            [
                sys.executable,
                str(context["database_authority_tool"]),
                "verify-final",
                "--outer-marker",
                str(outer_marker),
                "--manifest",
                str(merged_manifest),
            ],
            cwd=context["repo_root"],
            env=sanitized_subprocess_environment(),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=120,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        reject("E_DB_AUTHORITY", f"database authority verifier could not run: {error}")
    require(
        completed.returncode == 0,
        "E_DB_AUTHORITY",
        "database authority verifier rejected the final bundle: "
        + completed.stderr.strip()[:2000],
    )
    require(
        "verified final reports=29 testcase_nodes=370 F0/E0/S0" in completed.stdout,
        "E_DB_AUTHORITY",
        "database authority verifier did not emit its exact success receipt",
    )

    outer_bytes = secure_regular_bytes(outer_marker, "database outer marker")
    outer = load_json_bytes(outer_bytes, "database outer marker")
    require(
        outer.get("run_id") == identity["authority_run_id"]
        and outer.get("git_head") == identity["commit_sha"],
        "E_DB_IDENTITY",
        "database outer marker differs from CI authority identity",
    )
    require(
        outer.get("kind") == "v934-step3-database-matrix-outer-run"
        and outer.get("lane") == "database-contract-matrix"
        and outer.get("runner") == "failsafe"
        and outer.get("status") == "started",
        "E_DB_AUTHORITY",
        "database outer marker contract differs",
    )

    manifest_bytes = secure_regular_bytes(
        merged_manifest, "database merged manifest", maximum=16 * 1024 * 1024
    )
    manifest = load_json_bytes(manifest_bytes, "database merged manifest")
    require(
        manifest.get("schema_version") == 1
        and type(manifest.get("schema_version")) is int
        and manifest.get("kind") == "v934-step3-database-matrix-merged"
        and manifest.get("status") == "passed"
        and manifest.get("run_id") == identity["authority_run_id"],
        "E_DB_AUTHORITY",
        "database merged manifest identity/status differs",
    )
    cells = manifest.get("database_cells")
    variants = manifest.get("variants")
    require(type(cells) is list and type(variants) is list, "E_DB_AUTHORITY", "database merged rows are absent")
    selected_cells = [row for row in cells if type(row) is dict and row.get("db_kind") == db_kind]
    require(len(selected_cells) == 1, "E_DB_KIND", f"database merged cell is not unique: {db_kind}")
    cell = selected_cells[0]
    expectation = DB_EXPECTATIONS[db_kind]
    expected_variants = expectation["variant_keys"]
    totals = cell.get("totals")
    require(type(totals) is dict, "E_DB_TOTALS", f"database cell totals are absent: {db_kind}")
    require(
        cell.get("status") == "passed"
        and cell.get("variant_keys") == expected_variants
        and type(totals.get("reports")) is int
        and totals.get("reports") == expectation["reports"]
        and type(totals.get("testcase_nodes")) is int
        and totals.get("testcase_nodes") == expectation["testcase_nodes"]
        and type(totals.get("variants")) is int
        and totals.get("variants") == len(expected_variants)
        and all(type(totals.get(name)) is int and totals.get(name) == 0 for name in ("failures", "errors", "skipped")),
        "E_DB_TOTALS",
        f"database cell is not exact F0/E0/S0 authority: {db_kind}",
    )

    selected_variants = [
        row
        for row in variants
        if type(row) is dict and row.get("variant_key") in expected_variants
    ]
    require(
        [row.get("variant_key") for row in selected_variants] == expected_variants
        and all(row.get("db_kind") == db_kind for row in selected_variants),
        "E_DB_VARIANT",
        f"database variant set differs: {db_kind}",
    )

    selected_paths = {"outer-run-marker.json", "report-manifest.json", "report-metrics.tsv"}
    for owner, label in [(cell, "cell"), *[(row, "variant") for row in selected_variants]]:
        files = owner.get("files")
        require(type(files) is list and bool(files), "E_DB_AUTHORITY", f"database {label} files are absent: {db_kind}")
        for binding in files:
            require(type(binding) is dict, "E_DB_AUTHORITY", f"database {label} file binding is invalid")
            relative = normalize_relative_path(binding.get("path"), f"database {label} evidence path")
            require(relative not in selected_paths, "E_DB_AUTHORITY", f"duplicate database evidence path: {relative}")
            selected_paths.add(relative)
    xml_count = sum(path.endswith(".xml") for path in selected_paths)
    require(
        xml_count == expectation["reports"],
        "E_DB_REPORT_SET",
        f"database raw XML cardinality differs: {db_kind}",
    )
    for relative in selected_paths:
        secure_file_binding(
            evidence_dir / relative,
            f"selected database evidence {db_kind}/{relative}",
            scan_secrets=True,
        )

    metrics_binding = manifest.get("metrics")
    require(type(metrics_binding) is dict, "E_DB_AUTHORITY", "database metrics binding is absent")
    metrics_sha256, _ = secure_file_binding(metrics_path, "database report metrics", scan_secrets=True)
    require(metrics_binding.get("sha256") == metrics_sha256, "E_DB_AUTHORITY", "database metrics hash differs")
    attestation: dict[str, object] = {
        "schema_version": 1,
        "kind": "v934-step3-database-cell-attestation",
        "status": "passed",
        "authority_run_id": identity["authority_run_id"],
        "commit_sha": identity["commit_sha"],
        "db_kind": db_kind,
        "variant_keys": expected_variants,
        "reports": expectation["reports"],
        "testcase_nodes": expectation["testcase_nodes"],
        "failures": 0,
        "errors": 0,
        "skipped": 0,
        "outer_marker_sha256": sha256_bytes(outer_bytes),
        "merged_manifest_sha256": sha256_bytes(manifest_bytes),
        "report_metrics_sha256": metrics_sha256,
        "verifier_sha256": context["database_authority_tool_sha256"],
    }
    return sorted(selected_paths, key=lambda value: value.encode("utf-8")), attestation


def validate_cell_authority(
    context: dict[str, Any], value: object, identity: dict[str, Any]
) -> dict[str, Any]:
    require(type(value) is dict, "E_DB_AUTHORITY", "cell authority attestation must be an object")
    authority = value
    exact_keys(
        authority,
        {
            "schema_version", "kind", "status", "authority_run_id", "commit_sha",
            "db_kind", "variant_keys", "reports", "testcase_nodes", "failures",
            "errors", "skipped", "outer_marker_sha256", "merged_manifest_sha256",
            "report_metrics_sha256", "verifier_sha256",
        },
        "E_DB_AUTHORITY",
        "cell authority attestation",
    )
    db_kind = validate_db_kind(authority["db_kind"])
    expectation = DB_EXPECTATIONS[db_kind]
    require(
        type(authority["schema_version"]) is int
        and authority["schema_version"] == 1
        and authority["kind"] == "v934-step3-database-cell-attestation"
        and authority["status"] == "passed"
        and authority["authority_run_id"] == identity["authority_run_id"]
        and authority["commit_sha"] == identity["commit_sha"]
        and authority["variant_keys"] == expectation["variant_keys"]
        and type(authority["reports"]) is int
        and authority["reports"] == expectation["reports"]
        and type(authority["testcase_nodes"]) is int
        and authority["testcase_nodes"] == expectation["testcase_nodes"]
        and all(type(authority[name]) is int and authority[name] == 0 for name in ("failures", "errors", "skipped")),
        "E_DB_AUTHORITY",
        f"cell authority attestation differs: {db_kind}",
    )
    for name in (
        "outer_marker_sha256", "merged_manifest_sha256", "report_metrics_sha256",
        "verifier_sha256",
    ):
        require(type(authority[name]) is str and SHA256.fullmatch(authority[name]) is not None, "E_DB_AUTHORITY", f"invalid authority digest: {name}")
    require(
        authority["verifier_sha256"] == context["database_authority_tool_sha256"],
        "E_DB_AUTHORITY",
        "database authority verifier binding differs",
    )
    return authority


def build_cell(
    repo_root: Path,
    db_kind_value: str,
    commit_sha: str,
    workflow_run_id: str,
    attempt: str,
    authority_run_id: str,
    evidence_dir_value: Path,
    output_dir_value: Path,
    *,
    authority_validator: Callable[
        [dict[str, Any], Path, str, dict[str, object]],
        tuple[list[str], dict[str, object]],
    ] = validate_database_authority,
) -> dict[str, object]:
    context = load_context(repo_root)
    identity = validate_cli_identity(
        context, commit_sha, workflow_run_id, attempt, authority_run_id
    )
    db_kind = validate_db_kind(db_kind_value)
    logical_name = artifact_name(db_kind, identity)
    output_dir, output_parent = validate_output_target(output_dir_value, "artifact output directory")
    require(output_dir.name == logical_name, "E_ARTIFACT_NAME", f"artifact output directory must be named {logical_name}")
    evidence_dir = real_directory(evidence_dir_value, "cell evidence directory")
    require(
        not paths_overlap(evidence_dir, output_dir),
        "E_PATH",
        "evidence and artifact output directories overlap",
    )
    evidence_files, authority = authority_validator(context, evidence_dir, db_kind, identity)
    require(bool(evidence_files), "E_EVIDENCE_EMPTY", "validated cell evidence set is empty")

    temporary = Path(tempfile.mkdtemp(prefix=f".{logical_name}.", suffix=".tmp", dir=output_parent))
    try:
        payload = temporary / PAYLOAD_DIRECTORY
        payload.mkdir(mode=0o755)
        bindings: list[dict[str, object]] = []
        for relative in evidence_files:
            source = evidence_dir / relative
            destination = payload / relative
            digest, size = copy_regular_with_binding(
                source, destination, f"cell evidence {relative}"
            )
            bindings.append(
                {
                    "path": f"{PAYLOAD_DIRECTORY}/{relative}",
                    "sha256": digest,
                    "size_bytes": size,
                }
            )
        bindings.sort(key=lambda row: str(row["path"]).encode("utf-8"))
        cell_identity = {
            **identity,
            "artifact_name": logical_name,
            "db_kind": db_kind,
        }
        manifest = {
            "schema_version": 1,
            "kind": "v934-db-cell-artifact",
            "status": "passed",
            "contract_sha256": context["contract_sha256"],
            "tool_sha256": context["tool_sha256"],
            "identity": cell_identity,
            "authority": authority,
            "evidence_files": bindings,
            "evidence_set_sha256": sha256_bytes(canonical_json(bindings)),
        }
        manifest_bytes = canonical_json(manifest)
        write_exclusive_file(temporary / CELL_MANIFEST, manifest_bytes)
        fsync_directory(payload)
        fsync_directory(temporary)
        publish_new_directory(temporary, output_dir, output_parent)
    except BaseException:
        if path_exists(temporary):
            shutil.rmtree(temporary)
        raise

    verified = verify_cell_directory(context, output_dir, identity, db_kind)
    return {
        "artifact_name": logical_name,
        "cell_manifest_sha256": verified["cell_manifest_sha256"],
        "command": "build-cell",
        "db_kind": db_kind,
        "authority_sha256": sha256_bytes(canonical_json(authority)),
        "evidence_files": len(bindings),
        "evidence_set_sha256": manifest["evidence_set_sha256"],
        "status": "passed",
    }


def validate_cell_manifest(
    context: dict[str, Any], value: dict[str, Any]
) -> dict[str, Any]:
    exact_keys(
        value,
        {
            "schema_version",
            "kind",
            "status",
            "contract_sha256",
            "tool_sha256",
            "identity",
            "authority",
            "evidence_files",
            "evidence_set_sha256",
        },
        "E_CELL_MANIFEST",
        "cell manifest",
    )
    require(type(value["schema_version"]) is int and value["schema_version"] == 1, "E_JSON_TYPE", "cell schema_version must be integer 1")
    require(value["kind"] == "v934-db-cell-artifact" and type(value["kind"]) is str, "E_CELL_MANIFEST", "cell manifest kind differs")
    require(value["status"] == "passed" and type(value["status"]) is str, "E_CELL_MANIFEST", "cell manifest status differs")
    require(value["contract_sha256"] == context["contract_sha256"], "E_CONTRACT", "cell contract binding differs")
    require(value["tool_sha256"] == context["tool_sha256"], "E_TOOL", "cell tool binding differs")

    identity = value["identity"]
    require(type(identity) is dict, "E_JSON_TYPE", "cell identity must be an object")
    exact_keys(
        identity,
        {"artifact_name", "attempt", "authority_run_id", "commit_sha", "db_kind", "workflow_run_id"},
        "E_IDENTITY",
        "cell identity",
    )
    validate_commit_sha(identity["commit_sha"])
    validate_run_id(identity["authority_run_id"])
    validate_positive_integer(identity["workflow_run_id"], "cell workflow_run_id")
    validate_positive_integer(identity["attempt"], "cell attempt")
    db_kind = validate_db_kind(identity["db_kind"])
    expected_name = artifact_name(db_kind, identity)
    require(type(identity["artifact_name"]) is str and identity["artifact_name"] == expected_name, "E_ARTIFACT_NAME", "cell artifact name differs")
    validate_cell_authority(context, value["authority"], identity)

    evidence = value["evidence_files"]
    require(type(evidence) is list and bool(evidence), "E_JSON_TYPE", "cell evidence_files must be a non-empty list")
    seen: dict[str, str] = {}
    previous: bytes | None = None
    for index, row in enumerate(evidence):
        require(type(row) is dict, "E_JSON_TYPE", f"cell evidence row {index} must be an object")
        exact_keys(row, {"path", "sha256", "size_bytes"}, "E_EVIDENCE", f"cell evidence row {index}")
        path = normalize_relative_path(row["path"], f"cell evidence path {index}")
        require(path.startswith(f"{PAYLOAD_DIRECTORY}/"), "E_PATH", f"cell evidence path is outside payload: {path}")
        register_casefold(path, seen)
        encoded = path.encode("utf-8")
        require(previous is None or previous < encoded, "E_EVIDENCE", "cell evidence paths are not strictly sorted")
        previous = encoded
        require(type(row["sha256"]) is str and SHA256.fullmatch(row["sha256"]) is not None, "E_EVIDENCE", f"invalid evidence SHA-256: {path}")
        require(type(row["size_bytes"]) is int and row["size_bytes"] >= 0, "E_JSON_TYPE", f"evidence size must be a non-negative integer: {path}")
    require(
        type(value["evidence_set_sha256"]) is str
        and value["evidence_set_sha256"] == sha256_bytes(canonical_json(evidence)),
        "E_EVIDENCE_SET",
        "cell evidence set digest differs",
    )
    return value


def implied_directories(evidence_paths: Iterable[str]) -> set[str]:
    result = {PAYLOAD_DIRECTORY}
    for path in evidence_paths:
        parent = PurePosixPath(path).parent
        while parent.as_posix() not in {".", ""}:
            result.add(parent.as_posix())
            parent = parent.parent
    return result


def verify_cell_directory(
    context: dict[str, Any],
    directory_value: Path,
    expected_identity: dict[str, object],
    expected_db_kind: str,
) -> dict[str, object]:
    directory = real_directory(directory_value, "database artifact directory")
    expected_name = artifact_name(expected_db_kind, expected_identity)
    require(directory.name == expected_name, "E_ARTIFACT_NAME", f"database artifact directory must be named {expected_name}")
    actual_files, actual_directories = scan_tree(directory, f"database artifact {expected_db_kind}")
    require(CELL_MANIFEST in actual_files, "E_CELL_MANIFEST", "cell manifest is missing")
    manifest_bytes = secure_regular_bytes(directory / CELL_MANIFEST, "cell manifest")
    manifest = validate_cell_manifest(context, load_json_bytes(manifest_bytes, "cell manifest"))
    identity = manifest["identity"]
    expected_full_identity = {
        **expected_identity,
        "artifact_name": expected_name,
        "db_kind": expected_db_kind,
    }
    require(strict_equal(identity, expected_full_identity), "E_IDENTITY", f"cell identity differs for {expected_db_kind}")

    evidence_rows = manifest["evidence_files"]
    evidence_paths = [row["path"] for row in evidence_rows]
    expected_files = {CELL_MANIFEST, *evidence_paths}
    require(set(actual_files) == expected_files, "E_ARTIFACT_TREE", f"artifact file set differs for {expected_db_kind}")
    require(set(actual_directories) == implied_directories(evidence_paths), "E_ARTIFACT_TREE", f"artifact directory set differs for {expected_db_kind}")

    tree_rows: list[dict[str, object]] = []
    manifest_sha = sha256_bytes(manifest_bytes)
    tree_rows.append({"path": CELL_MANIFEST, "sha256": manifest_sha, "size_bytes": len(manifest_bytes)})
    for row in evidence_rows:
        path = row["path"]
        digest, size = secure_file_binding(directory / path, f"artifact evidence {expected_db_kind}/{path}")
        require(size == row["size_bytes"], "E_EVIDENCE_SIZE", f"evidence size differs: {expected_db_kind}/{path}")
        require(digest == row["sha256"], "E_EVIDENCE_HASH", f"evidence SHA-256 differs: {expected_db_kind}/{path}")
        tree_rows.append({"path": path, "sha256": digest, "size_bytes": size})
    tree_rows.sort(key=lambda row: str(row["path"]).encode("utf-8"))
    return {
        "db_kind": expected_db_kind,
        "logical_name": expected_name,
        "cell_manifest_sha256": manifest_sha,
        "authority_sha256": sha256_bytes(canonical_json(manifest["authority"])),
        "evidence_set_sha256": manifest["evidence_set_sha256"],
        "evidence_file_count": len(evidence_rows),
        "artifact_tree_sha256": sha256_bytes(canonical_json(tree_rows)),
    }


def collect_artifacts(
    context: dict[str, Any], artifacts_dir_value: Path, identity: dict[str, object]
) -> list[dict[str, object]]:
    artifacts_dir = real_directory(artifacts_dir_value, "downloaded artifacts directory")
    expected_names = {artifact_name(kind, identity) for kind in DB_KINDS}
    actual_names: set[str] = set()
    try:
        entries = list(os.scandir(artifacts_dir))
    except OSError as error:
        reject("E_DIRECTORY", f"cannot scan downloaded artifacts directory: {error}")
    for entry in entries:
        normalize_relative_path(entry.name, "artifact directory name")
        if entry.is_symlink():
            reject("E_SYMLINK", f"artifact entry is symlinked: {entry.name}")
        try:
            metadata = entry.stat(follow_symlinks=False)
        except OSError as error:
            reject("E_FILE_RACE", f"cannot stat artifact entry {entry.name}: {error}")
        require(stat.S_ISDIR(metadata.st_mode), "E_ARTIFACT_SET", f"non-directory artifact entry: {entry.name}")
        require(entry.name not in actual_names, "E_ARTIFACT_DUPLICATE", f"duplicate artifact name: {entry.name}")
        actual_names.add(entry.name)
    require(
        actual_names == expected_names and len(actual_names) == len(DB_KINDS),
        "E_ARTIFACT_SET",
        f"database artifact set differs: missing={sorted(expected_names - actual_names)} extra={sorted(actual_names - expected_names)}",
    )
    return [
        verify_cell_directory(
            context,
            artifacts_dir / artifact_name(db_kind, identity),
            identity,
            db_kind,
        )
        for db_kind in DB_KINDS
    ]


def load_job_states(path: Path) -> tuple[list[dict[str, str]], str]:
    raw = secure_regular_bytes(path, "required job states", maximum=1024 * 1024)
    scanner = SecretScanner("required job states")
    scanner.feed(raw)
    value = load_json_bytes(raw, "required job states")
    exact_keys(value, REQUIRED_JOBS, "E_JOB_SET", "required job states")
    allowed = {"success", "failure", "skipped", "cancelled"}
    normalized: list[dict[str, str]] = []
    for job_id in REQUIRED_JOBS:
        state = value[job_id]
        require(type(state) is str and state in allowed, "E_JOB_STATE", f"invalid required job result: {job_id}={state!r}")
        require(state == "success", "E_REQUIRED_JOB", f"required job did not succeed: {job_id}={state}")
        normalized.append({"job_id": job_id, "result": state})
    canonical_states = {row["job_id"]: row["result"] for row in normalized}
    return normalized, sha256_bytes(canonical_json(canonical_states))


def receipt_artifact_rows(cells: list[dict[str, object]]) -> list[dict[str, object]]:
    return [
        {
            "artifact_tree_sha256": row["artifact_tree_sha256"],
            "authority_sha256": row["authority_sha256"],
            "cell_manifest_sha256": row["cell_manifest_sha256"],
            "db_kind": row["db_kind"],
            "evidence_file_count": row["evidence_file_count"],
            "evidence_set_sha256": row["evidence_set_sha256"],
            "logical_name": row["logical_name"],
        }
        for row in cells
    ]


def aggregate(
    repo_root: Path,
    commit_sha: str,
    workflow_run_id: str,
    attempt: str,
    authority_run_id: str,
    job_states_path: Path,
    artifacts_dir: Path,
    output_path: Path,
) -> dict[str, object]:
    context = load_context(repo_root)
    identity = validate_cli_identity(
        context, commit_sha, workflow_run_id, attempt, authority_run_id
    )
    output, _ = validate_output_target(output_path, "aggregate receipt")
    artifacts_root = real_directory(artifacts_dir, "downloaded artifacts directory")
    require(not paths_overlap(output, artifacts_root), "E_PATH", "aggregate receipt must be outside artifacts directory")
    jobs, jobs_sha256 = load_job_states(job_states_path)
    cells = collect_artifacts(context, artifacts_root, identity)
    artifact_rows = receipt_artifact_rows(cells)
    receipt = {
        "schema_version": 1,
        "kind": RECEIPT_KIND,
        "status": "passed",
        "contract_sha256": context["contract_sha256"],
        "tool_sha256": context["tool_sha256"],
        "aggregator": {"id": AGGREGATOR_ID, "name": AGGREGATOR_NAME},
        "identity": identity,
        "required_jobs": jobs,
        "job_states_sha256": jobs_sha256,
        "database_artifacts": artifact_rows,
        "database_artifact_set_sha256": sha256_bytes(canonical_json(artifact_rows)),
    }
    receipt_bytes = canonical_json(receipt)
    atomic_new_file(output, receipt_bytes)
    verified = verify_receipt(context, output, artifacts_root)
    return {
        "command": "aggregate",
        "database_artifact_set_sha256": receipt["database_artifact_set_sha256"],
        "database_artifacts": len(artifact_rows),
        "receipt_sha256": verified["receipt_sha256"],
        "required_jobs": len(jobs),
        "status": "passed",
    }


def validate_receipt_value(context: dict[str, Any], receipt: dict[str, Any]) -> dict[str, Any]:
    exact_keys(
        receipt,
        {
            "schema_version",
            "kind",
            "status",
            "contract_sha256",
            "tool_sha256",
            "aggregator",
            "identity",
            "required_jobs",
            "job_states_sha256",
            "database_artifacts",
            "database_artifact_set_sha256",
        },
        "E_RECEIPT",
        "aggregate receipt",
    )
    require(type(receipt["schema_version"]) is int and receipt["schema_version"] == 1, "E_JSON_TYPE", "receipt schema_version must be integer 1")
    require(type(receipt["kind"]) is str and receipt["kind"] == RECEIPT_KIND, "E_RECEIPT", "receipt kind differs")
    require(type(receipt["status"]) is str and receipt["status"] == "passed", "E_RECEIPT", "receipt status differs")
    require(receipt["contract_sha256"] == context["contract_sha256"], "E_CONTRACT", "receipt contract binding differs")
    require(receipt["tool_sha256"] == context["tool_sha256"], "E_TOOL", "receipt tool binding differs")
    require(
        strict_equal(receipt["aggregator"], {"id": AGGREGATOR_ID, "name": AGGREGATOR_NAME}),
        "E_AGGREGATOR",
        "receipt aggregator identity differs",
    )

    identity = receipt["identity"]
    require(type(identity) is dict, "E_JSON_TYPE", "receipt identity must be an object")
    exact_keys(identity, {"attempt", "authority_run_id", "commit_sha", "workflow_run_id"}, "E_IDENTITY", "receipt identity")
    validate_commit_sha(identity["commit_sha"])
    validate_run_id(identity["authority_run_id"])
    validate_positive_integer(identity["workflow_run_id"], "receipt workflow_run_id")
    validate_positive_integer(identity["attempt"], "receipt attempt")
    require(identity["commit_sha"] == context["head"], "E_COMMIT", "receipt commit SHA does not match repository HEAD")

    jobs = receipt["required_jobs"]
    require(type(jobs) is list and len(jobs) == len(REQUIRED_JOBS), "E_JOB_SET", "receipt required job cardinality differs")
    for index, expected_job in enumerate(REQUIRED_JOBS):
        row = jobs[index]
        require(type(row) is dict, "E_JSON_TYPE", f"receipt required job row {index} must be an object")
        exact_keys(row, {"job_id", "result"}, "E_JOB_SET", f"receipt required job row {index}")
        require(type(row["job_id"]) is str and row["job_id"] == expected_job, "E_JOB_SET", f"receipt required job id differs at index {index}")
        require(type(row["result"]) is str and row["result"] == "success", "E_REQUIRED_JOB", f"receipt required job is not successful: {expected_job}")
    canonical_job_states = {
        row["job_id"]: row["result"] for row in jobs
    }
    require(
        type(receipt["job_states_sha256"]) is str
        and receipt["job_states_sha256"]
        == sha256_bytes(canonical_json(canonical_job_states)),
        "E_RECEIPT",
        "receipt job states binding differs",
    )

    artifacts = receipt["database_artifacts"]
    require(type(artifacts) is list and len(artifacts) == len(DB_KINDS), "E_ARTIFACT_SET", "receipt database artifact cardinality differs")
    for index, db_kind in enumerate(DB_KINDS):
        row = artifacts[index]
        require(type(row) is dict, "E_JSON_TYPE", f"receipt database artifact row {index} must be an object")
        exact_keys(
            row,
            {
                "artifact_tree_sha256",
                "authority_sha256",
                "cell_manifest_sha256",
                "db_kind",
                "evidence_file_count",
                "evidence_set_sha256",
                "logical_name",
            },
            "E_ARTIFACT_SET",
            f"receipt database artifact row {index}",
        )
        require(type(row["db_kind"]) is str and row["db_kind"] == db_kind, "E_DB_KIND", f"receipt DB kind differs at index {index}")
        require(type(row["logical_name"]) is str and row["logical_name"] == artifact_name(db_kind, identity), "E_ARTIFACT_NAME", f"receipt artifact name differs: {db_kind}")
        for key in ("artifact_tree_sha256", "authority_sha256", "cell_manifest_sha256", "evidence_set_sha256"):
            require(type(row[key]) is str and SHA256.fullmatch(row[key]) is not None, "E_RECEIPT", f"invalid receipt digest: {db_kind}/{key}")
        require(type(row["evidence_file_count"]) is int and row["evidence_file_count"] > 0, "E_JSON_TYPE", f"receipt evidence file count must be a positive integer: {db_kind}")
    require(
        type(receipt["database_artifact_set_sha256"]) is str
        and receipt["database_artifact_set_sha256"] == sha256_bytes(canonical_json(artifacts)),
        "E_ARTIFACT_SET",
        "receipt database artifact set digest differs",
    )
    return receipt


def verify_receipt(
    context: dict[str, Any], receipt_path: Path, artifacts_dir: Path
) -> dict[str, object]:
    receipt_bytes = secure_regular_bytes(receipt_path, "aggregate receipt")
    receipt = validate_receipt_value(
        context, load_json_bytes(receipt_bytes, "aggregate receipt")
    )
    cells = collect_artifacts(context, artifacts_dir, receipt["identity"])
    actual_rows = receipt_artifact_rows(cells)
    require(
        strict_equal(actual_rows, receipt["database_artifacts"]),
        "E_RECEIPT_BINDING",
        "receipt database artifact bindings differ from downloaded artifacts",
    )
    return {
        "command": "verify",
        "database_artifact_set_sha256": receipt["database_artifact_set_sha256"],
        "database_artifacts": len(cells),
        "receipt_sha256": sha256_bytes(receipt_bytes),
        "required_jobs": len(receipt["required_jobs"]),
        "status": "passed",
    }


def verify_command(
    repo_root: Path, receipt_path: Path, artifacts_dir: Path
) -> dict[str, object]:
    return verify_receipt(load_context(repo_root), receipt_path, artifacts_dir)


RELEASE_ROOT_FILES = {
    "release-assets.json",
    "v934-release-evidence.archive.json",
    "v934-release-evidence.tar.gz",
    "v934-release-evidence.tar.gz.sha256",
}
RELEASE_PACKAGE_FILES = {
    "app.jar",
    "docker-build.log",
    "image-manifest.json",
    "maven-invocations.log",
    "package-manifest.json",
    "tested-tree-validation.log",
}
GITHUB_REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
GITHUB_ARTIFACT_DIGEST = re.compile(r"sha256:[0-9a-f]{64}")


def exact_release_directory(path: Path, files: set[str], directories: set[str], label: str) -> Path:
    root = real_directory(path, label)
    observed_files: set[str] = set()
    observed_directories: set[str] = set()
    for entry in os.scandir(root):
        metadata = os.lstat(entry.path)
        require(not stat.S_ISLNK(metadata.st_mode), "E_SYMLINK", f"symlink in {label}: {entry.name}")
        if stat.S_ISREG(metadata.st_mode):
            observed_files.add(entry.name)
        elif stat.S_ISDIR(metadata.st_mode):
            observed_directories.add(entry.name)
        else:
            reject("E_SPECIAL", f"special entry in {label}: {entry.name}")
    require(
        observed_files == files and observed_directories == directories,
        "E_RELEASE_ASSET_SET",
        f"{label} differs: files={sorted(observed_files)} directories={sorted(observed_directories)}",
    )
    return root


def artifact_zip_pread(
    descriptor: int, offset: int, size: int, archive_size: int, label: str
) -> bytes:
    require(
        type(offset) is int
        and type(size) is int
        and offset >= 0
        and size >= 0
        and offset + size <= archive_size,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP record exceeds archive bounds: {label}",
    )
    try:
        raw = os.pread(descriptor, size, offset)
    except OSError as error:
        reject(
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"cannot read ZIP record {label}: {error}",
        )
    require(
        len(raw) == size,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"short ZIP record read: {label}",
    )
    return raw


def validate_artifact_zip_extra(raw: bytes, label: str) -> None:
    cursor = 0
    while cursor < len(raw):
        require(
            cursor + 4 <= len(raw),
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"truncated ZIP extra field: {label}",
        )
        field_id = int.from_bytes(raw[cursor : cursor + 2], "little")
        field_size = int.from_bytes(raw[cursor + 2 : cursor + 4], "little")
        cursor += 4
        require(
            cursor + field_size <= len(raw),
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP extra field exceeds its record: {label}",
        )
        require(
            field_id != 0x0001,
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP64 extra field is forbidden: {label}",
        )
        cursor += field_size
    require(
        cursor == len(raw),
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP extra field framing differs: {label}",
    )


def validate_artifact_zip_framing(
    descriptor: int, archive_size: int, label: str
) -> int:
    """Validate one canonical non-ZIP64 archive before handing bytes to zipfile."""

    require(
        archive_size >= 22,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP is truncated: {label}",
    )
    tail_size = min(archive_size, 65_535 + 22)
    tail_offset = archive_size - tail_size
    tail = artifact_zip_pread(
        descriptor, tail_offset, tail_size, archive_size, f"{label} EOCD window"
    )
    signature = b"PK\x05\x06"
    candidates: list[int] = []
    cursor = 0
    while True:
        relative = tail.find(signature, cursor)
        if relative < 0:
            break
        absolute_offset = tail_offset + relative
        if relative + 22 <= len(tail):
            comment_size = int.from_bytes(tail[relative + 20 : relative + 22], "little")
            if absolute_offset + 22 + comment_size == archive_size:
                candidates.append(absolute_offset)
        cursor = relative + 1
    require(
        len(candidates) == 1,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP EOCD is absent/ambiguous or does not end at EOF: {label}",
    )
    eocd_offset = candidates[0]
    eocd = artifact_zip_pread(
        descriptor, eocd_offset, 22, archive_size, f"{label} EOCD"
    )
    disk = int.from_bytes(eocd[4:6], "little")
    directory_disk = int.from_bytes(eocd[6:8], "little")
    disk_entries = int.from_bytes(eocd[8:10], "little")
    total_entries = int.from_bytes(eocd[10:12], "little")
    directory_size = int.from_bytes(eocd[12:16], "little")
    directory_offset = int.from_bytes(eocd[16:20], "little")
    comment_size = int.from_bytes(eocd[20:22], "little")
    require(
        disk == directory_disk == 0 and disk_entries == total_entries,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"multi-disk ZIP is forbidden: {label}",
    )
    require(
        0 < total_entries <= MAX_ARTIFACT_ARCHIVE_ENTRIES
        and total_entries < 0xFFFF
        and directory_size not in {0, 0xFFFFFFFF}
        and directory_offset not in {0, 0xFFFFFFFF},
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"empty/oversized/ZIP64 ZIP is forbidden: {label}",
    )
    require(
        comment_size == 0,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP archive comment is forbidden: {label}",
    )
    require(
        directory_offset + directory_size == eocd_offset,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP central directory is not exact or archive has a prefix: {label}",
    )
    require(
        artifact_zip_pread(descriptor, 0, 4, archive_size, f"{label} first record")
        == b"PK\x03\x04"
        and artifact_zip_pread(
            descriptor,
            directory_offset,
            4,
            archive_size,
            f"{label} first central record",
        )
        == b"PK\x01\x02",
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP local/central record start differs: {label}",
    )

    records: list[dict[str, int | bytes]] = []
    cursor = directory_offset
    for index in range(total_entries):
        header = artifact_zip_pread(
            descriptor,
            cursor,
            46,
            archive_size,
            f"{label} central entry {index + 1}",
        )
        require(
            header[:4] == b"PK\x01\x02",
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP central record signature differs at entry {index + 1}: {label}",
        )
        flags = int.from_bytes(header[8:10], "little")
        method = int.from_bytes(header[10:12], "little")
        crc32 = int.from_bytes(header[16:20], "little")
        compressed_size = int.from_bytes(header[20:24], "little")
        uncompressed_size = int.from_bytes(header[24:28], "little")
        name_size = int.from_bytes(header[28:30], "little")
        extra_size = int.from_bytes(header[30:32], "little")
        member_comment_size = int.from_bytes(header[32:34], "little")
        start_disk = int.from_bytes(header[34:36], "little")
        local_offset = int.from_bytes(header[42:46], "little")
        record_size = 46 + name_size + extra_size + member_comment_size
        require(
            name_size > 0 and cursor + record_size <= eocd_offset,
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP central record exceeds its directory: {label}",
        )
        require(
            member_comment_size == 0,
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP member comment is forbidden: {label}",
        )
        require(
            start_disk == 0
            and compressed_size != 0xFFFFFFFF
            and uncompressed_size != 0xFFFFFFFF
            and local_offset != 0xFFFFFFFF,
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP64/multi-disk member is forbidden: {label}",
        )
        require(
            flags & ~0x080E == 0 and method in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED},
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"unsupported ZIP flags/compression method: {label}",
        )
        variable = artifact_zip_pread(
            descriptor,
            cursor + 46,
            name_size + extra_size + member_comment_size,
            archive_size,
            f"{label} central entry {index + 1} variable fields",
        )
        name = variable[:name_size]
        validate_artifact_zip_extra(
            variable[name_size : name_size + extra_size],
            f"{label} central entry {index + 1}",
        )
        records.append(
            {
                "compressed_size": compressed_size,
                "crc32": crc32,
                "flags": flags,
                "local_offset": local_offset,
                "method": method,
                "name": name,
                "uncompressed_size": uncompressed_size,
            }
        )
        cursor += record_size
    require(
        cursor == eocd_offset,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP central directory has trailing/non-file records: {label}",
    )

    local_offsets = [int(record["local_offset"]) for record in records]
    require(
        len(set(local_offsets)) == total_entries and min(local_offsets) == 0,
        "E_ARTIFACT_ARCHIVE_FRAMING",
        f"ZIP local records have a prefix/duplicate offset: {label}",
    )
    ordered = sorted(records, key=lambda record: int(record["local_offset"]))
    for index, record in enumerate(ordered):
        local_offset = int(record["local_offset"])
        header = artifact_zip_pread(
            descriptor,
            local_offset,
            30,
            archive_size,
            f"{label} local entry {index + 1}",
        )
        require(
            header[:4] == b"PK\x03\x04",
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP local record signature differs at entry {index + 1}: {label}",
        )
        flags = int.from_bytes(header[6:8], "little")
        method = int.from_bytes(header[8:10], "little")
        crc32 = int.from_bytes(header[14:18], "little")
        compressed_size = int.from_bytes(header[18:22], "little")
        uncompressed_size = int.from_bytes(header[22:26], "little")
        name_size = int.from_bytes(header[26:28], "little")
        extra_size = int.from_bytes(header[28:30], "little")
        variable = artifact_zip_pread(
            descriptor,
            local_offset + 30,
            name_size + extra_size,
            archive_size,
            f"{label} local entry {index + 1} variable fields",
        )
        name = variable[:name_size]
        validate_artifact_zip_extra(
            variable[name_size:], f"{label} local entry {index + 1}"
        )
        payload_end = (
            local_offset + 30 + name_size + extra_size + int(record["compressed_size"])
        )
        require(
            payload_end <= directory_offset
            and flags == record["flags"]
            and method == record["method"]
            and name == record["name"],
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP local/central header binding differs: {label}",
        )
        if flags & 0x0008:
            require(
                crc32 in {0, record["crc32"]}
                and compressed_size in {0, record["compressed_size"]}
                and uncompressed_size in {0, record["uncompressed_size"]},
                "E_ARTIFACT_ARCHIVE_FRAMING",
                f"ZIP deferred local sizes differ: {label}",
            )
            descriptor_offset = payload_end
            if artifact_zip_pread(
                descriptor,
                descriptor_offset,
                min(4, directory_offset - descriptor_offset),
                archive_size,
                f"{label} data descriptor prefix",
            ) == b"PK\x07\x08":
                descriptor_offset += 4
            descriptor_raw = artifact_zip_pread(
                descriptor,
                descriptor_offset,
                12,
                archive_size,
                f"{label} data descriptor",
            )
            require(
                int.from_bytes(descriptor_raw[0:4], "little") == record["crc32"]
                and int.from_bytes(descriptor_raw[4:8], "little")
                == record["compressed_size"]
                and int.from_bytes(descriptor_raw[8:12], "little")
                == record["uncompressed_size"],
                "E_ARTIFACT_ARCHIVE_FRAMING",
                f"ZIP data descriptor differs: {label}",
            )
            payload_end = descriptor_offset + 12
        else:
            require(
                crc32 == record["crc32"]
                and compressed_size == record["compressed_size"]
                and uncompressed_size == record["uncompressed_size"],
                "E_ARTIFACT_ARCHIVE_FRAMING",
                f"ZIP local sizes differ: {label}",
            )
        next_offset = (
            int(ordered[index + 1]["local_offset"])
            if index + 1 < len(ordered)
            else directory_offset
        )
        require(
            payload_end == next_offset,
            "E_ARTIFACT_ARCHIVE_FRAMING",
            f"ZIP local records contain a gap/trailing data: {label}",
        )
    return total_entries


def extract_verified_artifact(
    repo_root: Path,
    archive_value: Path,
    artifact_id: str,
    artifact_digest: str,
    destination_value: Path,
) -> dict[str, object]:
    """Verify an exact GitHub artifact ZIP by REST identity/digest and extract it safely."""

    context = load_context(repo_root)
    artifact_number = parse_positive_cli(artifact_id, "GitHub artifact id")
    require(
        type(artifact_digest) is str
        and GITHUB_ARTIFACT_DIGEST.fullmatch(artifact_digest) is not None,
        "E_ARTIFACT_ARCHIVE_DIGEST",
        "GitHub artifact digest differs",
    )
    expected_digest = artifact_digest.removeprefix("sha256:")
    archive = absolute(archive_value)
    real_directory(archive.parent, "GitHub artifact archive parent")
    destination, destination_parent = validate_output_target(
        destination_value, "GitHub artifact extraction directory"
    )
    require(
        not paths_overlap(archive, destination),
        "E_PATH",
        "GitHub artifact archive and extraction directory overlap",
    )

    descriptor = -1
    zip_descriptor = -1
    temporary: Path | None = None
    published = False
    try:
        flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
        try:
            descriptor = os.open(archive, flags)
        except FileNotFoundError:
            reject("E_FILE_MISSING", f"missing GitHub artifact archive: {archive}")
        except OSError as error:
            reject("E_FILE_OPEN", f"cannot open GitHub artifact archive: {error}")
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", "GitHub artifact archive is not regular")
        require(
            0 < before.st_size <= MAX_ARTIFACT_ARCHIVE_BYTES,
            "E_FILE_SIZE",
            "GitHub artifact archive size differs",
        )
        digest = hashlib.sha256()
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_READ", "short read from GitHub artifact archive")
            digest.update(chunk)
            remaining -= len(chunk)
        require(os.read(descriptor, 1) == b"", "E_FILE_READ", "GitHub artifact archive grew while hashed")
        observed_digest = digest.hexdigest()
        require(
            observed_digest == expected_digest,
            "E_ARTIFACT_ARCHIVE_DIGEST",
            "downloaded GitHub artifact ZIP digest differs from REST authority",
        )
        framed_entries = validate_artifact_zip_framing(
            descriptor, before.st_size, "GitHub artifact ZIP"
        )
        os.lseek(descriptor, 0, os.SEEK_SET)

        temporary = Path(
            tempfile.mkdtemp(
                prefix=f".{destination.name}.", suffix=".artifact.tmp", dir=destination_parent
            )
        )
        zip_descriptor = os.dup(descriptor)
        with os.fdopen(zip_descriptor, "rb", closefd=True) as archive_stream:
            zip_descriptor = -1
            with zipfile.ZipFile(archive_stream, "r") as archive_zip:
                infos = archive_zip.infolist()
                require(
                    0 < len(infos) <= MAX_ARTIFACT_ARCHIVE_ENTRIES,
                    "E_ARTIFACT_ARCHIVE_SET",
                    "GitHub artifact ZIP entry cardinality differs",
                )
                require(
                    len(infos) == framed_entries
                    and archive_zip.comment == b""
                    and min(info.header_offset for info in infos) == 0,
                    "E_ARTIFACT_ARCHIVE_FRAMING",
                    "ZIP reader metadata differs from canonical framing",
                )
                entries: dict[str, tuple[zipfile.ZipInfo, bool]] = {}
                casefolded: dict[str, str] = {}
                total_size = 0
                for info in infos:
                    require(
                        info.orig_filename == info.filename
                        and not info.comment
                        and info.flag_bits & ~0x080E == 0,
                        "E_ARTIFACT_ARCHIVE_FRAMING",
                        f"ZIP reader entry metadata differs: {info.filename!r}",
                    )
                    validate_artifact_zip_extra(
                        info.extra, f"GitHub artifact ZIP entry {info.filename!r}"
                    )
                    raw_name = info.filename
                    require(type(raw_name) is str and bool(raw_name), "E_ARTIFACT_ARCHIVE_PATH", "empty ZIP entry name")
                    is_directory = info.is_dir()
                    if is_directory:
                        require(
                            raw_name.endswith("/") and not raw_name.endswith("//"),
                            "E_ARTIFACT_ARCHIVE_PATH",
                            f"non-canonical ZIP directory entry: {raw_name!r}",
                        )
                        candidate_name = raw_name[:-1]
                    else:
                        require(
                            not raw_name.endswith("/"),
                            "E_ARTIFACT_ARCHIVE_PATH",
                            f"non-canonical ZIP file entry: {raw_name!r}",
                        )
                        candidate_name = raw_name
                    try:
                        normalized = normalize_relative_path(candidate_name, "GitHub artifact ZIP entry")
                    except ContractError as error:
                        reject("E_ARTIFACT_ARCHIVE_PATH", str(error))
                    require(
                        normalized not in entries,
                        "E_ARTIFACT_ARCHIVE_DUPLICATE",
                        f"duplicate ZIP entry: {normalized}",
                    )
                    try:
                        register_casefold(normalized, casefolded)
                    except ContractError as error:
                        reject("E_ARTIFACT_ARCHIVE_DUPLICATE", str(error))
                    require(
                        info.flag_bits & 0x1 == 0,
                        "E_ARTIFACT_ARCHIVE_ENCRYPTED",
                        f"encrypted ZIP entry: {normalized}",
                    )
                    require(
                        info.compress_type in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED},
                        "E_ARTIFACT_ARCHIVE_COMPRESSION",
                        f"unsupported ZIP compression for {normalized}",
                    )
                    unix_mode = (info.external_attr >> 16) & 0xFFFF
                    file_type = stat.S_IFMT(unix_mode)
                    allowed_types = {0, stat.S_IFDIR} if is_directory else {0, stat.S_IFREG}
                    require(
                        file_type in allowed_types,
                        "E_ARTIFACT_ARCHIVE_SPECIAL",
                        f"symlink or special ZIP entry: {normalized}",
                    )
                    require(
                        type(info.file_size) is int and info.file_size >= 0,
                        "E_ARTIFACT_ARCHIVE_SIZE",
                        f"invalid ZIP entry size: {normalized}",
                    )
                    if is_directory:
                        require(info.file_size == 0, "E_ARTIFACT_ARCHIVE_SIZE", f"non-empty ZIP directory: {normalized}")
                    else:
                        total_size += info.file_size
                        require(
                            total_size <= MAX_ARTIFACT_EXTRACTED_BYTES,
                            "E_ARTIFACT_ARCHIVE_SIZE",
                            "GitHub artifact ZIP expands beyond the limit",
                        )
                    entries[normalized] = (info, is_directory)

                expected_files = {
                    *RELEASE_ROOT_FILES,
                    *(f"package/{name}" for name in RELEASE_PACKAGE_FILES),
                }
                observed_files = {
                    name for name, (_, is_directory) in entries.items() if not is_directory
                }
                observed_directories = {
                    name for name, (_, is_directory) in entries.items() if is_directory
                }
                require(
                    observed_files == expected_files
                    and observed_directories <= {"package"},
                    "E_ARTIFACT_ARCHIVE_SET",
                    "GitHub artifact ZIP release file set differs",
                )

                package_directory = temporary / "package"
                package_directory.mkdir(mode=0o755)
                rows: list[dict[str, object]] = []
                for name in sorted(observed_files, key=lambda value: value.encode("utf-8")):
                    info = entries[name][0]
                    output = temporary / PurePosixPath(name)
                    output_flags = (
                        os.O_WRONLY
                        | os.O_CREAT
                        | os.O_EXCL
                        | getattr(os, "O_CLOEXEC", 0)
                        | getattr(os, "O_NOFOLLOW", 0)
                    )
                    output_descriptor = -1
                    file_digest = hashlib.sha256()
                    extracted_size = 0
                    try:
                        output_descriptor = os.open(output, output_flags, 0o600)
                        with archive_zip.open(info, "r") as source:
                            while True:
                                chunk = source.read(1024 * 1024)
                                if not chunk:
                                    break
                                extracted_size += len(chunk)
                                require(
                                    extracted_size <= info.file_size,
                                    "E_ARTIFACT_ARCHIVE_SIZE",
                                    f"ZIP entry exceeds declared size: {name}",
                                )
                                file_digest.update(chunk)
                                view = memoryview(chunk)
                                while view:
                                    written = os.write(output_descriptor, view)
                                    require(written > 0, "E_OUTPUT", f"short write extracting ZIP entry: {name}")
                                    view = view[written:]
                        require(
                            extracted_size == info.file_size,
                            "E_ARTIFACT_ARCHIVE_SIZE",
                            f"ZIP entry size differs: {name}",
                        )
                        os.fsync(output_descriptor)
                        os.fchmod(output_descriptor, 0o644)
                    finally:
                        if output_descriptor >= 0:
                            os.close(output_descriptor)
                    rows.append(
                        {"path": name, "sha256": file_digest.hexdigest(), "size": extracted_size}
                    )

        after = os.fstat(descriptor)
        try:
            current = os.lstat(archive)
        except FileNotFoundError:
            reject("E_FILE_RACE", "GitHub artifact archive disappeared while extracted")
        before_identity = (
            before.st_dev,
            before.st_ino,
            before.st_size,
            before.st_mtime_ns,
            before.st_ctime_ns,
        )
        after_identity = (
            after.st_dev,
            after.st_ino,
            after.st_size,
            after.st_mtime_ns,
            after.st_ctime_ns,
        )
        require(before_identity == after_identity, "E_FILE_RACE", "GitHub artifact archive changed while extracted")
        require(
            (current.st_dev, current.st_ino, current.st_size)
            == (after.st_dev, after.st_ino, after.st_size),
            "E_FILE_RACE",
            "GitHub artifact archive path identity changed while extracted",
        )
        fsync_directory(temporary / "package")
        fsync_directory(temporary)
        publish_new_directory(temporary, destination, destination_parent)
        published = True
        return {
            "command": "extract-verified-artifact",
            "status": "passed",
            "artifact_id": artifact_number,
            "artifact_digest": artifact_digest,
            "archive_size": before.st_size,
            "files": len(rows),
            "extracted_set_sha256": sha256_bytes(canonical_json(rows)),
            "contract_sha256": context["contract_sha256"],
            "tool_sha256": context["tool_sha256"],
        }
    except ContractError:
        raise
    except (zipfile.BadZipFile, zipfile.LargeZipFile, EOFError, RuntimeError) as error:
        reject("E_ARTIFACT_ARCHIVE", f"cannot safely extract GitHub artifact ZIP: {error}")
    finally:
        if zip_descriptor >= 0:
            os.close(zip_descriptor)
        if descriptor >= 0:
            os.close(descriptor)
        if temporary is not None and not published and path_exists(temporary):
            shutil.rmtree(temporary)


def expected_authority_artifact_names(
    commit_sha: str, workflow_run_id: int, attempt: int
) -> tuple[str, ...]:
    values = {
        "commit_sha": commit_sha,
        "workflow_run_id": workflow_run_id,
        "attempt": attempt,
    }
    return tuple(template.format(**values) for template in AUTHORITY_ARTIFACT_TEMPLATES)


def expected_release_artifact_name(
    commit_sha: str, workflow_run_id: int, attempt: int
) -> str:
    return RELEASE_ARTIFACT_TEMPLATE.format(
        commit_sha=commit_sha,
        workflow_run_id=workflow_run_id,
        attempt=attempt,
    )


def validate_github_repository(value: object) -> str:
    require(
        type(value) is str and GITHUB_REPOSITORY.fullmatch(value) is not None,
        "E_GITHUB_REPOSITORY",
        "GitHub repository identity differs",
    )
    return value


def github_api_object(value: object, code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict, code, f"{label} must be an object")
    return value


def github_api_positive_integer(value: object, code: str, label: str) -> int:
    require(
        type(value) is int and 0 < value <= MAX_POSITIVE_INTEGER,
        code,
        f"{label} must be a positive integer",
    )
    return value


def validate_workflow_api_path(value: object) -> None:
    require(
        type(value) is str
        and value
        in {
            AUTHORITY_WORKFLOW_FILE,
            f"{AUTHORITY_WORKFLOW_FILE}@main",
            f"{AUTHORITY_WORKFLOW_FILE}@refs/heads/main",
        },
        "E_MAIN_RUN",
        "main authority workflow path differs",
    )


def validate_main_run_value(
    value: dict[str, Any],
    repository: str,
    commit_sha: str,
    *,
    expected_run_id: int | None = None,
    expected_attempt: int | None = None,
) -> dict[str, int]:
    run_id = github_api_positive_integer(value.get("id"), "E_MAIN_RUN", "main workflow run id")
    attempt = github_api_positive_integer(
        value.get("run_attempt"), "E_MAIN_RUN", "main workflow run attempt"
    )
    github_api_positive_integer(
        value.get("workflow_id"), "E_MAIN_RUN", "main authority workflow id"
    )
    github_api_positive_integer(
        value.get("run_number"), "E_MAIN_RUN", "main authority workflow run number"
    )
    if expected_run_id is not None:
        require(run_id == expected_run_id, "E_MAIN_RUN", "main workflow run id differs")
    if expected_attempt is not None:
        require(attempt == expected_attempt, "E_MAIN_RUN", "main workflow run attempt differs")
    require(
        value.get("name") == AUTHORITY_WORKFLOW_NAME
        and value.get("event") == "push"
        and value.get("head_branch") == "main"
        and value.get("head_sha") == commit_sha
        and value.get("status") == "completed"
        and value.get("conclusion") == "success",
        "E_MAIN_RUN",
        "main authority workflow identity/state differs",
    )
    validate_workflow_api_path(value.get("path"))
    repository_value = github_api_object(
        value.get("repository"), "E_MAIN_RUN", "main workflow repository"
    )
    require(
        repository_value.get("full_name") == repository,
        "E_MAIN_RUN",
        "main workflow repository differs",
    )
    head_repository = github_api_object(
        value.get("head_repository"), "E_MAIN_RUN", "main workflow head repository"
    )
    require(
        head_repository.get("full_name") == repository,
        "E_MAIN_RUN",
        "main workflow head repository differs",
    )
    return {"workflow_run_id": run_id, "attempt": attempt}


def resolve_main_authority_api(
    repo_root: Path,
    response_path: Path,
    repository: str,
    commit_sha: str,
) -> dict[str, object]:
    context = load_context(repo_root)
    repository = validate_github_repository(repository)
    commit_sha = validate_commit_sha(commit_sha)
    require(commit_sha == context["head"], "E_COMMIT", "release target differs from repository HEAD")
    raw = secure_regular_bytes(response_path, "main workflow runs API response")
    response = load_json_bytes(raw, "main workflow runs API response")
    total_count = github_api_positive_integer(
        response.get("total_count"), "E_MAIN_RUN_SET", "main workflow run count"
    )
    runs = response.get("workflow_runs")
    require(type(runs) is list, "E_MAIN_RUN_SET", "main workflow runs must be an array")
    require(
        total_count == len(runs),
        "E_MAIN_RUN_SET",
        "main workflow runs response is partial or paginated",
    )
    matches: list[dict[str, int]] = []
    for row in runs:
        require(type(row) is dict, "E_MAIN_RUN_SET", "main workflow run row must be an object")
        try:
            matches.append(validate_main_run_value(row, repository, commit_sha))
        except ContractError as error:
            if error.code != "E_MAIN_RUN":
                raise
    unique = {(row["workflow_run_id"], row["attempt"]) for row in matches}
    require(
        len(matches) == 1 and len(unique) == 1,
        "E_MAIN_RUN_SET",
        f"expected one exact successful main authority run, found {len(matches)}",
    )
    selected = matches[0]
    return {
        "command": "resolve-main-authority-api",
        "status": "passed",
        "version": RELEASE_VERSION,
        "repository": repository,
        "commit_sha": commit_sha,
        "workflow_run_id": selected["workflow_run_id"],
        "attempt": selected["attempt"],
        "authority_run_id": (
            f"ci-{selected['workflow_run_id']}-{selected['attempt']}"
        ),
        "api_response_sha256": sha256_bytes(raw),
    }


def validate_main_jobs_value(
    response: dict[str, Any], workflow_run_id: int, attempt: int, commit_sha: str
) -> dict[str, int]:
    total_count = response.get("total_count")
    jobs = response.get("jobs")
    require(
        type(total_count) is int
        and total_count == len(AUTHORITY_JOB_NAMES)
        and type(jobs) is list
        and len(jobs) == len(AUTHORITY_JOB_NAMES),
        "E_MAIN_JOB_SET",
        "main authority job response is partial or has wrong cardinality",
    )
    observed: dict[str, int] = {}
    job_ids: set[int] = set()
    for row in jobs:
        require(type(row) is dict, "E_MAIN_JOB_SET", "main authority job row must be an object")
        name = row.get("name")
        require(
            type(name) is str and name in AUTHORITY_JOB_NAMES and name not in observed,
            "E_MAIN_JOB_SET",
            f"unexpected or duplicate main authority job: {name!r}",
        )
        job_id = github_api_positive_integer(row.get("id"), "E_MAIN_JOB_SET", f"job id for {name}")
        require(job_id not in job_ids, "E_MAIN_JOB_SET", "duplicate main authority job id")
        job_ids.add(job_id)
        require(
            row.get("run_id") == workflow_run_id
            and type(row.get("run_attempt")) is int
            and row.get("run_attempt") == attempt
            and row.get("head_sha") == commit_sha
            and row.get("status") == "completed"
            and row.get("conclusion") == "success",
            "E_MAIN_JOB_STATE",
            f"main authority job identity/state differs: {name}",
        )
        if "workflow_name" in row:
            require(
                row["workflow_name"] == AUTHORITY_WORKFLOW_NAME,
                "E_MAIN_JOB_STATE",
                f"main authority job workflow differs: {name}",
            )
        if "head_branch" in row:
            require(
                row["head_branch"] == "main",
                "E_MAIN_JOB_STATE",
                f"main authority job branch differs: {name}",
            )
        observed[name] = job_id
    require(
        tuple(name for name in AUTHORITY_JOB_NAMES if name in observed)
        == AUTHORITY_JOB_NAMES,
        "E_MAIN_JOB_SET",
        "main authority job set differs",
    )
    return {
        "count": len(observed),
        "aggregator_job_id": observed[AGGREGATOR_NAME],
    }


def validate_main_artifacts_value(
    response: dict[str, Any], workflow_run_id: int, attempt: int, commit_sha: str
) -> dict[str, object]:
    expected_names = expected_authority_artifact_names(
        commit_sha, workflow_run_id, attempt
    )
    expected_set = set(expected_names)
    total_count = response.get("total_count")
    artifacts = response.get("artifacts")
    require(
        type(total_count) is int
        and type(artifacts) is list
        and total_count == len(artifacts)
        and len(artifacts) >= len(expected_names),
        "E_MAIN_ARTIFACT_SET",
        "main authority artifact response is partial or lacks the current attempt",
    )
    observed: dict[str, dict[str, object]] = {}
    artifact_ids: set[int] = set()
    for row in artifacts:
        require(
            type(row) is dict,
            "E_MAIN_ARTIFACT_SET",
            "main authority artifact row must be an object",
        )
        name = row.get("name")
        require(
            type(name) is str and name not in observed,
            "E_MAIN_ARTIFACT_SET",
            f"unexpected or duplicate main authority artifact: {name!r}",
        )
        artifact_attempt: int | None = None
        for template in AUTHORITY_ARTIFACT_TEMPLATES:
            prefix = template.format(
                commit_sha=commit_sha,
                workflow_run_id=workflow_run_id,
                attempt="",
            )
            if name.startswith(prefix):
                suffix = name[len(prefix) :]
                if suffix.isdigit() and int(suffix) > 0:
                    artifact_attempt = int(suffix)
                    break
        require(
            artifact_attempt is not None,
            "E_MAIN_ARTIFACT_SET",
            f"artifact does not belong to the exact authority run: {name}",
        )
        require(
            artifact_attempt <= attempt,
            "E_MAIN_ARTIFACT_SET",
            f"artifact comes from a future authority attempt: {name}",
        )
        artifact_id = github_api_positive_integer(
            row.get("id"), "E_MAIN_ARTIFACT_SET", f"artifact id for {name}"
        )
        require(
            artifact_id not in artifact_ids,
            "E_MAIN_ARTIFACT_SET",
            "duplicate main authority artifact id",
        )
        artifact_ids.add(artifact_id)
        size = github_api_positive_integer(
            row.get("size_in_bytes"), "E_MAIN_ARTIFACT_SET", f"artifact size for {name}"
        )
        digest = row.get("digest")
        require(
            type(digest) is str
            and GITHUB_ARTIFACT_DIGEST.fullmatch(digest) is not None,
            "E_MAIN_ARTIFACT_DIGEST",
            f"artifact digest differs: {name}",
        )
        require(type(row.get("expired")) is bool, "E_MAIN_ARTIFACT_EXPIRED", f"artifact expiry state differs: {name}")
        if name in expected_set:
            require(
                row.get("expired") is False,
                "E_MAIN_ARTIFACT_EXPIRED",
                f"current main authority artifact is expired: {name}",
            )
        workflow = github_api_object(
            row.get("workflow_run"),
            "E_MAIN_ARTIFACT_IDENTITY",
            f"artifact workflow identity for {name}",
        )
        require(
            workflow.get("id") == workflow_run_id
            and workflow.get("head_branch") == "main"
            and workflow.get("head_sha") == commit_sha,
            "E_MAIN_ARTIFACT_IDENTITY",
            f"artifact workflow identity differs: {name}",
        )
        observed[name] = {
            "id": artifact_id,
            "name": name,
            "digest": digest,
            "size_in_bytes": size,
        }
    require(
        expected_set.issubset(observed),
        "E_MAIN_ARTIFACT_SET",
        "current main authority artifact set differs",
    )
    release_name = expected_release_artifact_name(commit_sha, workflow_run_id, attempt)
    release = observed[release_name]
    rows = [observed[name] for name in expected_names]
    return {
        "count": len(rows),
        "set_sha256": sha256_bytes(canonical_json(rows)),
        "release_artifact": release,
    }


def verify_main_authority_api(
    repo_root: Path,
    run_response_path: Path,
    jobs_response_path: Path,
    artifacts_response_path: Path,
    repository: str,
    commit_sha: str,
    workflow_run_id: str,
    attempt: str,
) -> dict[str, object]:
    context = load_context(repo_root)
    repository = validate_github_repository(repository)
    commit_sha = validate_commit_sha(commit_sha)
    require(commit_sha == context["head"], "E_COMMIT", "release target differs from repository HEAD")
    run_number = parse_positive_cli(workflow_run_id, "main workflow_run_id")
    attempt_number = parse_positive_cli(attempt, "main workflow attempt")

    run_raw = secure_regular_bytes(run_response_path, "main workflow run API response")
    jobs_raw = secure_regular_bytes(jobs_response_path, "main workflow jobs API response")
    artifacts_raw = secure_regular_bytes(
        artifacts_response_path, "main workflow artifacts API response"
    )
    run_value = load_json_bytes(run_raw, "main workflow run API response")
    jobs_value = load_json_bytes(jobs_raw, "main workflow jobs API response")
    artifacts_value = load_json_bytes(
        artifacts_raw, "main workflow artifacts API response"
    )
    run = validate_main_run_value(
        run_value,
        repository,
        commit_sha,
        expected_run_id=run_number,
        expected_attempt=attempt_number,
    )
    jobs = validate_main_jobs_value(
        jobs_value, run_number, attempt_number, commit_sha
    )
    artifacts = validate_main_artifacts_value(
        artifacts_value, run_number, attempt_number, commit_sha
    )
    release = artifacts["release_artifact"]
    assert isinstance(release, dict)
    return {
        "command": "verify-main-authority-api",
        "status": "passed",
        "version": RELEASE_VERSION,
        "repository": repository,
        "commit_sha": commit_sha,
        "workflow_run_id": run["workflow_run_id"],
        "attempt": run["attempt"],
        "authority_run_id": f"ci-{run_number}-{attempt_number}",
        "gate_mode": "authority",
        "aggregator": {
            "id": AGGREGATOR_ID,
            "name": AGGREGATOR_NAME,
            "job_id": jobs["aggregator_job_id"],
            "conclusion": "success",
        },
        "artifact_count": artifacts["count"],
        "artifact_set_sha256": artifacts["set_sha256"],
        "release_artifact": release,
        "api": {
            "run": {"sha256": sha256_bytes(run_raw), "size": len(run_raw)},
            "jobs": {"sha256": sha256_bytes(jobs_raw), "size": len(jobs_raw)},
            "artifacts": {
                "sha256": sha256_bytes(artifacts_raw),
                "size": len(artifacts_raw),
            },
        },
        "contract_sha256": context["contract_sha256"],
        "tool_sha256": context["tool_sha256"],
    }


def release_target(
    event_name: str,
    github_ref: str,
    ref_name: str,
    github_sha: str,
    ref_commit: str,
    requested_version: str,
) -> dict[str, str]:
    validate_commit_sha(github_sha)
    validate_commit_sha(ref_commit)
    require(ref_commit == github_sha, "E_RELEASE_TARGET", "release ref commit differs from workflow SHA")
    if event_name == "push":
        require(
            github_ref == "refs/tags/v9.3.4" and ref_name == "v9.3.4",
            "E_RELEASE_TARGET",
            "release push is not the exact v9.3.4 tag",
        )
        version = ref_name[1:]
    else:
        require(event_name == "workflow_dispatch", "E_RELEASE_TARGET", "release event differs")
        version = requested_version
    require(
        version == RELEASE_VERSION,
        "E_RELEASE_VERSION",
        "release version must equal 9.3.4",
    )
    return {
        "event_name": event_name,
        "github_sha": github_sha,
        "ref_commit": ref_commit,
        "version": version,
        "status": "passed",
    }


def verify_release_assets(
    assets_dir: Path,
    authority_run_id: str,
    commit_sha: str,
    workflow_run_id: str,
    attempt: str,
    gate_mode: str,
    expected_jar_sha256: str,
) -> dict[str, object]:
    validate_run_id(authority_run_id)
    validate_commit_sha(commit_sha)
    run_number = parse_positive_cli(workflow_run_id, "release workflow_run_id")
    attempt_number = parse_positive_cli(attempt, "release attempt")
    require(
        gate_mode in {"rehearsal", "authority"},
        "E_RELEASE_IDENTITY",
        "release gate mode differs",
    )
    require(SHA256.fullmatch(expected_jar_sha256 or "") is not None, "E_RELEASE_IDENTITY", "expected JAR SHA differs")
    root = exact_release_directory(assets_dir, RELEASE_ROOT_FILES, {"package"}, "release assets")
    package = exact_release_directory(root / "package", RELEASE_PACKAGE_FILES, set(), "release package assets")
    receipt_bytes = secure_regular_bytes(root / "release-assets.json", "release assets receipt")
    receipt = load_json_bytes(receipt_bytes, "release assets receipt")
    exact_keys(receipt, {"schema_version", "kind", "status", "identity", "files"}, "E_RELEASE_RECEIPT", "release assets receipt")
    identity = receipt["identity"]
    require(type(identity) is dict, "E_JSON_TYPE", "release identity must be an object")
    exact_keys(identity, {"authority_run_id", "commit_sha", "workflow_run_id", "attempt", "gate_mode"}, "E_RELEASE_IDENTITY", "release identity")
    require(
        receipt["schema_version"] == 1
        and type(receipt["schema_version"]) is int
        and receipt["kind"] == "v934-tested-release-assets"
        and receipt["status"] == "passed"
        and identity
        == {
            "authority_run_id": authority_run_id,
            "commit_sha": commit_sha,
            "workflow_run_id": run_number,
            "attempt": attempt_number,
            "gate_mode": gate_mode,
        },
        "E_RELEASE_IDENTITY",
        "release asset receipt identity differs",
    )
    expected_names = {
        *(f"package/{name}" for name in RELEASE_PACKAGE_FILES),
        "v934-release-evidence.archive.json",
        "v934-release-evidence.tar.gz",
        "v934-release-evidence.tar.gz.sha256",
    }
    files = receipt["files"]
    require(type(files) is dict and set(files) == expected_names, "E_RELEASE_ASSET_SET", "release receipt file set differs")
    rows: list[dict[str, object]] = []
    for name in sorted(expected_names, key=lambda value: value.encode("utf-8")):
        expected = files[name]
        require(type(expected) is str and SHA256.fullmatch(expected) is not None, "E_RELEASE_RECEIPT", f"release digest differs: {name}")
        observed, size = secure_file_binding(root / PurePosixPath(name), f"release asset {name}", maximum=1024 * 1024 * 1024, scan_secrets=False)
        require(observed == expected, "E_RELEASE_ASSET_DIGEST", f"release asset digest differs: {name}")
        rows.append({"path": name, "sha256": observed, "size": size})
    require(files["package/app.jar"] == expected_jar_sha256, "E_RELEASE_JAR", "release JAR differs from gate")
    digest_text = secure_regular_bytes(
        root / "v934-release-evidence.tar.gz.sha256",
        "release archive digest",
        maximum=1024,
    )
    expected_line = f"{files['v934-release-evidence.tar.gz']}  v934-release-evidence.tar.gz\n".encode("ascii")
    require(digest_text == expected_line, "E_RELEASE_ASSET_DIGEST", "archive digest file differs")
    return {
        "command": "verify-release-assets",
        "assets": len(rows),
        "jar_sha256": expected_jar_sha256,
        "receipt_sha256": sha256_bytes(receipt_bytes),
        "set_sha256": sha256_bytes(canonical_json(rows)),
        "status": "passed",
    }


def validate_workflow_contract(repo_root: Path) -> dict[str, object]:
    root = real_directory(repo_root, "workflow repository root")
    tooling = validate_tooling_manifest(root)
    paths = {
        "authority": root / ".github/workflows/test-ci-evidence-chain.yml",
        "release": root / ".github/workflows/release.yml",
        "legacy-model": root / ".github/workflows/model-lifecycle-concurrency.yml",
        "legacy-pivot": root / ".github/workflows/pivot-release-readiness.yml",
    }
    texts: dict[str, str] = {}
    for label, path in paths.items():
        raw = secure_regular_bytes(path, f"{label} workflow", maximum=4 * 1024 * 1024)
        try:
            texts[label] = raw.decode("utf-8")
        except UnicodeDecodeError as error:
            reject("E_WORKFLOW", f"{label} workflow is not UTF-8: {error}")
    validate_workflow_texts(texts)
    dockerfile_raw = secure_regular_bytes(
        root / "foggy-mcp-launcher/Dockerfile.release",
        "runtime release Dockerfile",
        maximum=1024 * 1024,
    )
    try:
        dockerfile = dockerfile_raw.decode("utf-8")
    except UnicodeDecodeError as error:
        reject("E_RELEASE_BASE_IMAGE", f"runtime release Dockerfile is not UTF-8: {error}")
    validate_runtime_base_dockerfile_text(dockerfile)
    return {
        "command": "validate-workflows",
        "tooling_paths": tooling["paths"],
        "workflows": len(texts),
        "status": "passed",
    }


def action_pin_counter(text: str, label: str) -> Counter[tuple[str, str]]:
    counter: Counter[tuple[str, str]] = Counter()
    uses_lines = re.findall(
        r"(?m)^\s*(?:-\s*)?uses:\s*([^\s#]+)(?:\s+#.*)?$", text
    )
    require(bool(uses_lines), "E_ACTION_PIN", f"{label} workflow has no action uses")
    for specification in uses_lines:
        require(
            not specification.startswith("./"),
            "E_LOCAL_ACTION",
            f"local action/reusable workflow is forbidden in {label}: {specification}",
        )
        require("@" in specification, "E_ACTION_PIN", f"unpinned action in {label}: {specification}")
        repository, reference = specification.rsplit("@", 1)
        require(
            re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository)
            is not None
            and SHA40.fullmatch(reference) is not None,
            "E_ACTION_PIN",
            f"external action is not full-SHA pinned in {label}: {specification}",
        )
        counter[(repository, reference)] += 1
    return counter


def validate_external_action_pins(authority: str, release: str) -> None:
    for label, text, occurrence_key in (
        ("authority", authority, "authority_occurrences"),
        ("release", release, "release_occurrences"),
    ):
        expected: Counter[tuple[str, str]] = Counter()
        for row in EXTERNAL_ACTION_PINS:
            count = row[occurrence_key]
            assert isinstance(count, int)
            if count:
                expected[(str(row["repository"]), str(row["commit_sha"]))] = count
        require(
            action_pin_counter(text, label) == expected,
            "E_ACTION_PIN",
            f"{label} external action SHA pin set/cardinality differs",
        )


def workflow_top_level_block(text: str, key: str, label: str) -> list[str]:
    lines = text.splitlines()
    starts = [index for index, line in enumerate(lines) if line == f"{key}:"]
    require(
        len(starts) == 1,
        "E_WORKFLOW_DAG" if key == "jobs" else "E_WORKFLOW_EVENT",
        f"{label} workflow must contain one top-level {key!r} mapping",
    )
    start = starts[0] + 1
    end = len(lines)
    for index in range(start, len(lines)):
        line = lines[index]
        if line and not line[0].isspace() and not line.startswith("#"):
            end = index
            break
    return lines[start:end]


def workflow_mapping_chunks(
    lines: list[str], indent: int, label: str, error_code: str
) -> list[tuple[str, list[str]]]:
    prefix = " " * indent
    pattern = re.compile(rf"^{re.escape(prefix)}([A-Za-z0-9_-]+):(?:\s*(.*))?$")
    starts: list[tuple[int, str]] = []
    for index, line in enumerate(lines):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        leading = len(line) - len(line.lstrip(" "))
        require(
            "\t" not in line[:leading] and leading >= indent,
            error_code,
            f"{label} mapping indentation differs",
        )
        if leading == indent:
            match = pattern.fullmatch(line)
            require(
                match is not None,
                error_code,
                f"{label} direct mapping row differs: {line!r}",
            )
            assert match is not None
            starts.append((index, match.group(1)))
    chunks: list[tuple[str, list[str]]] = []
    for position, (start, mapping_key) in enumerate(starts):
        end = starts[position + 1][0] if position + 1 < len(starts) else len(lines)
        chunks.append((mapping_key, lines[start:end]))
    return chunks


def workflow_scalar_list(
    chunk: list[str], key: str, indent: int, label: str, error_code: str
) -> tuple[str, ...]:
    header = " " * indent + f"{key}:"
    matches = [index for index, line in enumerate(chunk) if line == header]
    require(
        len(matches) == 1,
        error_code,
        f"{label} must contain one {key!r} sequence",
    )
    start = matches[0] + 1
    item_indent = " " * (indent + 2)
    values: list[str] = []
    for line in chunk[start:]:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        leading = len(line) - len(line.lstrip(" "))
        if leading <= indent:
            break
        require(
            line.startswith(item_indent + "- ") and leading == indent + 2,
            error_code,
            f"{label} {key!r} sequence row differs: {line!r}",
        )
        value = line[len(item_indent) + 2 :].strip()
        require(bool(value), error_code, f"{label} {key!r} has an empty item")
        if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
            value = value[1:-1]
        values.append(value)
    return tuple(values)


def workflow_event_chunks(text: str, label: str) -> list[tuple[str, list[str]]]:
    return workflow_mapping_chunks(
        workflow_top_level_block(text, "on", label),
        2,
        f"{label} events",
        "E_WORKFLOW_EVENT",
    )


def workflow_job_chunks(text: str, label: str) -> list[tuple[str, list[str]]]:
    return workflow_mapping_chunks(
        workflow_top_level_block(text, "jobs", label),
        2,
        f"{label} jobs",
        "E_WORKFLOW_DAG",
    )


def workflow_job_needs(chunk: list[str], label: str) -> tuple[str, ...]:
    scalar_pattern = re.compile(r"^    needs:\s*([A-Za-z0-9_-]+)\s*$")
    block_header = "    needs:"
    matches = [
        (index, scalar_pattern.fullmatch(line), line == block_header)
        for index, line in enumerate(chunk)
        if scalar_pattern.fullmatch(line) is not None or line == block_header
    ]
    require(
        len(matches) <= 1,
        "E_WORKFLOW_DAG",
        f"{label} has duplicate needs declarations",
    )
    if not matches:
        return ()
    index, scalar_match, is_block = matches[0]
    if not is_block:
        assert scalar_match is not None
        return (scalar_match.group(1),)
    values: list[str] = []
    for line in chunk[index + 1 :]:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        leading = len(line) - len(line.lstrip(" "))
        if leading <= 4:
            break
        match = re.fullmatch(r"      - ([A-Za-z0-9_-]+)\s*", line)
        require(
            match is not None,
            "E_WORKFLOW_DAG",
            f"{label} needs sequence row differs: {line!r}",
        )
        assert match is not None
        values.append(match.group(1))
    require(bool(values), "E_WORKFLOW_DAG", f"{label} has an empty needs sequence")
    return tuple(values)


def workflow_job_name(chunk: list[str], label: str) -> str:
    names = [
        match.group(1).strip()
        for line in chunk
        if (match := re.fullmatch(r"    name:\s*(.+?)\s*", line)) is not None
    ]
    require(
        len(names) == 1,
        "E_WORKFLOW_DAG",
        f"{label} must contain one stable name",
    )
    return names[0]


def validate_job_tooling_closure(
    chunk: list[str], label: str, checkout_name: str, full_history: bool
) -> None:
    steps_headers = [index for index, line in enumerate(chunk) if line == "    steps:"]
    require(
        len(steps_headers) == 1,
        "E_WORKFLOW_TOOLING_CLOSURE",
        f"{label} must contain one direct steps sequence",
    )
    steps_start = steps_headers[0]
    direct_steps = [
        (index, line)
        for index, line in enumerate(chunk)
        if index > steps_start and re.match(r"^      - ", line) is not None
    ]
    step_names: list[tuple[int, str]] = []
    for index, line in direct_steps:
        match = re.fullmatch(r"      - name:\s*(.+?)\s*", line)
        require(
            match is not None,
            "E_WORKFLOW_TOOLING_CLOSURE",
            f"{label} contains an unnamed/non-canonical direct step: {line!r}",
        )
        assert match is not None
        step_names.append((index, match.group(1).strip()))
    require(
        len(direct_steps) == len(step_names)
        and len(step_names) >= 2
        and step_names[0][1] == checkout_name
        and step_names[1][1] == "Verify frozen Steps 4-6 workflow/tooling bytes",
        "E_WORKFLOW_TOOLING_CLOSURE",
        f"{label} must verify frozen tooling immediately after checkout",
    )
    checkout_lines = [
        line
        for line in chunk[step_names[0][0] : step_names[1][0]]
        if line.strip()
    ]
    expected_checkout_lines = [
        f"      - name: {checkout_name}",
        "        uses: actions/checkout@"
        "34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1",
    ]
    if full_history:
        expected_checkout_lines.extend(["        with:", "          fetch-depth: 0"])
    require(
        checkout_lines == expected_checkout_lines,
        "E_WORKFLOW_CHECKOUT",
        f"{label} exact checkout action/configuration differs",
    )
    closure_start = step_names[1][0]
    closure_end = step_names[2][0] if len(step_names) > 2 else len(chunk)
    closure_lines = [line for line in chunk[closure_start:closure_end] if line.strip()]
    commands = [
        *(f"sha256sum -c {path}" for path in TOOLING_CLOSURE_MANIFESTS),
        'python3 "$CI_TOOL" validate-workflows --repo-root .',
    ]
    expected_closure_lines = [
        "      - name: Verify frozen Steps 4-6 workflow/tooling bytes",
        "        shell: bash",
        "        run: |",
        "          set -euo pipefail",
        *(f"          {command}" for command in commands),
    ]
    require(
        closure_lines == expected_closure_lines,
        "E_WORKFLOW_TOOLING_CLOSURE",
        f"{label} fail-closed tooling closure step differs",
    )
    job_text = "\n".join(chunk)
    for command in commands:
        require(
            job_text.count(command) == 1,
            "E_WORKFLOW_TOOLING_CLOSURE",
            f"{label} tooling closure command set/cardinality differs: {command}",
        )


def validate_workflow_structure(authority: str, release: str) -> None:
    authority_events = workflow_event_chunks(authority, "authority")
    release_events = workflow_event_chunks(release, "release")
    require(
        tuple(name for name, _ in authority_events) == AUTHORITY_EVENTS,
        "E_WORKFLOW_EVENT",
        "authority workflow event set/order differs",
    )
    require(
        tuple(name for name, _ in release_events) == RELEASE_EVENTS,
        "E_WORKFLOW_EVENT",
        "release workflow event set/order differs",
    )
    authority_event_map = dict(authority_events)
    release_event_map = dict(release_events)
    require(
        not any(line.strip() for line in authority_event_map["pull_request"][1:])
        and not any(line.strip() for line in authority_event_map["workflow_dispatch"][1:]),
        "E_WORKFLOW_EVENT",
        "authority pull_request/workflow_dispatch trigger configuration differs",
    )
    authority_push = workflow_mapping_chunks(
        authority_event_map["push"][1:], 4, "authority push", "E_WORKFLOW_EVENT"
    )
    authority_call = workflow_mapping_chunks(
        authority_event_map["workflow_call"][1:],
        4,
        "authority workflow_call",
        "E_WORKFLOW_EVENT",
    )
    require(
        [key for key, _ in authority_push] == ["branches"]
        and workflow_scalar_list(
            authority_event_map["push"],
            "branches",
            4,
            "authority push",
            "E_WORKFLOW_EVENT",
        )
        == ("main",)
        and [key for key, _ in authority_call] == ["outputs"],
        "E_WORKFLOW_EVENT",
        "authority push branch/workflow_call configuration differs",
    )
    release_push = workflow_mapping_chunks(
        release_event_map["push"][1:], 4, "release push", "E_WORKFLOW_EVENT"
    )
    release_dispatch = workflow_mapping_chunks(
        release_event_map["workflow_dispatch"][1:],
        4,
        "release workflow_dispatch",
        "E_WORKFLOW_EVENT",
    )
    require(
        [key for key, _ in release_push] == ["tags"]
        and workflow_scalar_list(
            release_event_map["push"],
            "tags",
            4,
            "release push",
            "E_WORKFLOW_EVENT",
        )
        == ("v9.3.4",)
        and [key for key, _ in release_dispatch] == ["inputs"],
        "E_WORKFLOW_EVENT",
        "release tag/workflow_dispatch configuration differs",
    )

    for label, text, expected_graph, expected_names in (
        ("authority", authority, AUTHORITY_JOB_GRAPH, AUTHORITY_JOB_NAMES),
        ("release", release, RELEASE_JOB_GRAPH, RELEASE_JOB_NAMES),
    ):
        chunks = workflow_job_chunks(text, label)
        require(
            tuple(job_id for job_id, _ in chunks)
            == tuple(job_id for job_id, _ in expected_graph),
            "E_WORKFLOW_DAG",
            f"{label} job set/order differs",
        )
        for index, ((job_id, chunk), (_, expected_needs)) in enumerate(
            zip(chunks, expected_graph)
        ):
            require(
                workflow_job_needs(chunk, f"{label} job {job_id}") == expected_needs
                and workflow_job_name(chunk, f"{label} job {job_id}")
                == expected_names[index],
                "E_WORKFLOW_DAG",
                f"{label} job dependency/name differs: {job_id}",
            )
            require(
                not any(
                    re.match(
                        r"^    (?:container|services|continue-on-error):", line
                    )
                    is not None
                    for line in chunk
                ),
                "E_WORKFLOW_EXECUTION_CONTEXT",
                f"{label} job {job_id} has a forbidden pre-closure/failure context",
            )
            validate_job_tooling_closure(
                chunk,
                f"{label} job {job_id}",
                (
                    "Checkout exact workflow commit"
                    if label == "authority"
                    else "Checkout exact release commit"
                ),
                (
                    job_id in {"inventory-unit", "coverage"}
                    if label == "authority"
                    else job_id == "release-dry-run"
                ),
            )

        chunk_map = {job_id: chunk for job_id, chunk in chunks}
        if label == "authority":
            for job_id in (
                "inventory-unit",
                "sqlite-integration",
                "database-matrix",
                "external-integration",
                "coverage",
                "package-evidence",
            ):
                require(
                    not any(
                        line.startswith("    if:") for line in chunk_map[job_id]
                    ),
                    "E_WORKFLOW_JOB_GUARD",
                    f"authority job {job_id} cannot be conditionally skipped",
                )
            aggregator_if_rows = [
                line
                for line in chunk_map["required-aggregator"]
                if line.startswith("    if:")
            ]
            require(
                aggregator_if_rows == ["    if: ${{ always() }}"],
                "E_WORKFLOW_AGGREGATOR_GUARD",
                "authority aggregator must have one exact always() guard",
            )
            coverage_text = "\n".join(chunk_map["coverage"])
            coverage_checkout = (
                "      - name: Checkout exact workflow commit\n"
                "        uses: actions/checkout@"
                "34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1\n"
                "        with:\n"
                "          fetch-depth: 0"
            )
            require(
                coverage_text.count(coverage_checkout) == 1
                and coverage_text.count("fetch-depth: 0") == 1,
                "E_WORKFLOW_CHECKOUT",
                "coverage job must use the exact full-history checkout",
            )
            extraction_parent = (
                "          test ! -e target/v934-ci\n"
                "          mkdir target/v934-ci\n"
                '          python3 "$RELEASE_ARTIFACT_TOOL" extract-verify \\'
            )
            for job_id in ("coverage", "package-evidence"):
                job_text = "\n".join(chunk_map[job_id])
                require(
                    job_text.count(extraction_parent) == 1
                    and job_text.count("test ! -e target/v934-ci") == 1
                    and job_text.count("mkdir target/v934-ci") == 1
                    and job_text.count("extract-verify") == 1,
                    "E_WORKFLOW_EXTRACT_PARENT",
                    f"{job_id} must create one fresh canonical extraction parent",
                )
        else:
            require(
                not any(
                    line.startswith("    if:")
                    for line in chunk_map["release-dry-run"]
                ),
                "E_WORKFLOW_JOB_GUARD",
                "release-dry-run cannot be conditionally skipped",
            )
            publish_guard = (
                "    if: github.event_name == 'push' && "
                "github.ref == 'refs/tags/v9.3.4'"
            )
            for job_id in ("docker-publish", "github-release"):
                job_text = "\n".join(chunk_map[job_id])
                direct_if_rows = [
                    line for line in chunk_map[job_id] if line.startswith("    if:")
                ]
                require(
                    direct_if_rows == [publish_guard]
                    and job_text.count(publish_guard) == 1,
                    "E_RELEASE_PUBLISH_GUARD",
                    f"{job_id} exact tag-push publication guard differs",
                )


def validate_runtime_base_dockerfile_text(text: str) -> None:
    logical = [
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    from_lines = [line for line in logical if line.upper().startswith("FROM ")]
    expected = f"FROM --platform=linux/amd64 {RUNTIME_BASE_PINNED_REFERENCE}"
    require(
        from_lines == [expected],
        "E_RELEASE_BASE_IMAGE",
        "runtime release Dockerfile must use the exact linux/amd64 platform manifest",
    )


def validate_workflow_texts(texts: dict[str, str]) -> None:
    authority = texts["authority"]
    release = texts["release"]
    validate_external_action_pins(authority, release)
    validate_workflow_structure(authority, release)
    require(f"    name: {AGGREGATOR_NAME}\n" in authority and "    if: ${{ always() }}\n" in authority, "E_WORKFLOW", "stable always-run aggregator differs")
    for label in ("legacy-model", "legacy-pivot"):
        text = texts[label]
        require(re.search(r"(?m)^  pull_request:\s*$", text) is None, "E_LEGACY_AUTHORITY", f"{label} handles pull requests")
        require(re.search(r"(?m)^  push:\s*$", text) is None, "E_LEGACY_AUTHORITY", f"{label} handles pushes")
        require(AGGREGATOR_NAME not in text, "E_LEGACY_AUTHORITY", f"{label} masquerades as required authority")
    forbidden_release = re.compile(
        r"(?i)(?:-D(?:skipTests|maven\.test\.skip|skipITs|skipUnitTests)|--skip-(?:external|tests?)|\b(?:mvnw(?:\.cmd)?|mvn(?:\.cmd)?|gradlew(?:\.bat)?|gradle)\b)"
    )
    require(forbidden_release.search(release) is None, "E_RELEASE_REBUILD", "release workflow contains skip/rebuild path")
    require(
        "actions/download-artifact" not in release,
        "E_RELEASE_ARTIFACT_TRANSPORT",
        "release main-authority consumer uses name-based artifact action download",
    )
    require(
        re.search(
            r"(?m)^\s*uses:\s*[^\n]*test-ci-evidence-chain\.yml(?:@[^\s]+)?\s*$",
            release,
        )
        is None,
        "E_RELEASE_AUTHORITY_SOURCE",
        "release workflow starts a fresh authority gate instead of consuming main authority",
    )
    for required in (
        "verify-main-authority-api",
        "resolve-main-authority-api",
        "actions: read",
        "actions/artifacts/$ARTIFACT_ID/zip",
        "extract-verified-artifact",
        '--artifact-id "$ARTIFACT_ID"',
        '--artifact-digest "$ARTIFACT_DIGEST"',
    ):
        require(
            required in release,
            "E_RELEASE_AUTHORITY_SOURCE",
            f"release main-authority consumer contract is absent: {required}",
        )
    require(
        release.count("actions/artifacts/$ARTIFACT_ID/zip") == 3
        and release.count("extract-verified-artifact") == 3
        and release.count('sha256:$(sha256sum "$artifact_zip"') == 3
        and release.count("--proto '=https' --proto-redir '=https'") == 3
        and release.count("verify-release-assets") == 3,
        "E_RELEASE_ARTIFACT_TRANSPORT",
        "release must verify exactly three HTTPS ID/digest-selected artifact transports",
    )
    require(
        release.count(
            '"v934-tested-release-assets-$GITHUB_SHA-$MAIN_WORKFLOW_RUN_ID-$MAIN_WORKFLOW_ATTEMPT"'
        )
        == 3,
        "E_RELEASE_AUTHORITY_SOURCE",
        "release artifact names are not bound to final SHA/main run/attempt in all consumers",
    )
    expected_base_env = {
        "RUNTIME_BASE_TAG_REFERENCE": RUNTIME_BASE_TAG_REFERENCE,
        "RUNTIME_BASE_PINNED_REFERENCE": RUNTIME_BASE_PINNED_REFERENCE,
        "RUNTIME_BASE_INDEX_DIGEST": RUNTIME_BASE_INDEX_DIGEST,
        "RUNTIME_BASE_INDEX_REFERENCE": f"eclipse-temurin@{RUNTIME_BASE_INDEX_DIGEST}",
        "RUNTIME_BASE_MANIFEST_DIGEST": RUNTIME_BASE_MANIFEST_DIGEST,
        "RUNTIME_BASE_CONFIG_DIGEST": RUNTIME_BASE_CONFIG_DIGEST,
        "RUNTIME_BASE_OS": RUNTIME_BASE_PLATFORM["os"],
        "RUNTIME_BASE_ARCHITECTURE": RUNTIME_BASE_PLATFORM["architecture"],
    }
    require(
        all(
            release.count(f"  {name}: {value}\n") == 1
            for name, value in expected_base_env.items()
        )
        and release.count(".image.base_image.tag_reference") == 3
        and release.count(".image.base_image.pinned_reference") == 3
        and release.count(".image.base_image.index_digest") == 3
        and release.count(".image.base_image.manifest_digest") == 3
        and release.count(".image.base_image.config_digest") == 3
        and release.count(".image.base_image.platform.os") == 3
        and release.count(".image.base_image.platform.architecture") == 3
        and release.count("docker buildx imagetools inspect --raw") == 4
        and release.count("--pull=false") == 2
        and release.count("docker image inspect --format '{{.Id}}|{{.Os}}|{{.Architecture}}'")
        == 2
        and release.count(
            'test "$GATE_BASE_MANIFEST_DIGEST" = "$DRY_BASE_MANIFEST_DIGEST"'
        )
        == 1
        and "gate_base_image:" in release
        and "base_image:" in release,
        "E_RELEASE_BASE_IMAGE",
        "release runtime base identity is not exact across gate/dry/publish consumers",
    )
    require("dist/package/app.jar" in release and "Dockerfile.release" in release, "E_WORKFLOW", "release same-tested runtime path differs")


def fixture_write_json(path: Path, value: object) -> None:
    path.write_bytes(canonical_json(value))


def fixture_load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    assert isinstance(value, dict)
    return value


def synthetic_authority_validator(
    context: dict[str, Any],
    evidence_dir: Path,
    db_kind: str,
    identity: dict[str, object],
) -> tuple[list[str], dict[str, object]]:
    """Hermetic fixture validator used only by the built-in negative matrix."""

    result_path = evidence_dir / "result.json"
    result = load_json_bytes(
        secure_regular_bytes(result_path, "synthetic database result"),
        "synthetic database result",
    )
    exact_keys(
        result,
        {
            "db_kind", "status", "variant_keys", "reports", "testcase_nodes",
            "failures", "errors", "skipped",
        },
        "E_DB_AUTHORITY",
        "synthetic database result",
    )
    expectation = DB_EXPECTATIONS[db_kind]
    require(result["db_kind"] == db_kind, "E_DB_KIND", "synthetic database kind differs")
    require(
        result["status"] == "passed"
        and result["variant_keys"] == expectation["variant_keys"]
        and type(result["reports"]) is int
        and result["reports"] == expectation["reports"]
        and type(result["testcase_nodes"]) is int
        and result["testcase_nodes"] == expectation["testcase_nodes"]
        and all(type(result[name]) is int and result[name] == 0 for name in ("failures", "errors", "skipped")),
        "E_DB_TOTALS",
        "synthetic database totals differ",
    )
    files, _ = scan_tree(evidence_dir, "synthetic database evidence")
    xml_files = [path for path in files if path.endswith(".xml")]
    require(len(xml_files) == expectation["reports"], "E_DB_REPORT_SET", "synthetic raw XML cardinality differs")
    binding = sha256_bytes(canonical_json(result))
    return files, {
        "schema_version": 1,
        "kind": "v934-step3-database-cell-attestation",
        "status": "passed",
        "authority_run_id": identity["authority_run_id"],
        "commit_sha": identity["commit_sha"],
        "db_kind": db_kind,
        "variant_keys": expectation["variant_keys"],
        "reports": expectation["reports"],
        "testcase_nodes": expectation["testcase_nodes"],
        "failures": 0,
        "errors": 0,
        "skipped": 0,
        "outer_marker_sha256": binding,
        "merged_manifest_sha256": binding,
        "report_metrics_sha256": binding,
        "verifier_sha256": context["database_authority_tool_sha256"],
    }


def expect_failure(
    name: str,
    expected_code: str,
    operation: Callable[[], object],
    output: Path | None,
    results: list[dict[str, str]],
) -> None:
    try:
        operation()
    except ContractError as error:
        require(
            error.code == expected_code,
            "E_SELF_TEST",
            f"negative {name} expected {expected_code}, got {error.code}: {error}",
        )
        if output is not None:
            require(not path_exists(output), "E_SELF_TEST", f"negative {name} published a receipt")
        results.append({"case": name, "code": error.code, "status": "passed"})
        return
    reject("E_SELF_TEST", f"negative {name} unexpectedly passed")


def negative_matrix(repo_root: Path, output_dir_value: Path) -> dict[str, object]:
    context = load_context(repo_root)
    output_dir, output_parent = validate_output_target(output_dir_value, "negative output directory")
    commit = context["head"]
    workflow_run = "934001"
    attempt = "2"
    authority_run = "v934-step6-negative"
    identity = validate_cli_identity(context, commit, workflow_run, attempt, authority_run)
    results: list[dict[str, str]] = []
    with tempfile.TemporaryDirectory(prefix="v934-step6-negative-") as temporary_name:
        root = Path(temporary_name)
        base_artifacts = root / "base-artifacts"
        base_artifacts.mkdir()
        for db_kind in DB_KINDS:
            evidence = root / f"evidence-{db_kind}"
            evidence.mkdir()
            expectation = DB_EXPECTATIONS[db_kind]
            fixture = {
                "db_kind": db_kind,
                "status": "passed",
                "variant_keys": expectation["variant_keys"],
                "reports": expectation["reports"],
                "testcase_nodes": expectation["testcase_nodes"],
                "failures": 0,
                "errors": 0,
                "skipped": 0,
            }
            fixture_write_json(evidence / "result.json", fixture)
            for index in range(int(expectation["reports"])):
                (evidence / f"TEST-contract-{index}.xml").write_text(
                    f'<testsuite name="{db_kind}-{index}" tests="1" failures="0" errors="0" skipped="0"/>\n',
                    encoding="utf-8",
                )

            if db_kind == "sqlite":
                arbitrary = root / "arbitrary-nonempty"
                arbitrary.mkdir()
                (arbitrary / "foo.txt").write_text("not database evidence\n", encoding="utf-8")
                expect_failure(
                    "arbitrary-nonempty-evidence",
                    "E_FILE_MISSING",
                    lambda: synthetic_authority_validator(context, arbitrary, db_kind, identity),
                    None,
                    results,
                )

                wrong_kind = dict(fixture)
                wrong_kind["db_kind"] = "mysql57"
                fixture_write_json(evidence / "result.json", wrong_kind)
                expect_failure(
                    "semantic-wrong-db-kind",
                    "E_DB_KIND",
                    lambda: synthetic_authority_validator(context, evidence, db_kind, identity),
                    None,
                    results,
                )
                failing = dict(fixture)
                failing["failures"] = 1
                fixture_write_json(evidence / "result.json", failing)
                expect_failure(
                    "semantic-nonzero-failure",
                    "E_DB_TOTALS",
                    lambda: synthetic_authority_validator(context, evidence, db_kind, identity),
                    None,
                    results,
                )
                fixture_write_json(evidence / "result.json", fixture)
                missing_xml = evidence / "TEST-contract-0.xml"
                missing_payload = missing_xml.read_bytes()
                missing_xml.unlink()
                expect_failure(
                    "semantic-missing-raw-xml",
                    "E_DB_REPORT_SET",
                    lambda: synthetic_authority_validator(context, evidence, db_kind, identity),
                    None,
                    results,
                )
                missing_xml.write_bytes(missing_payload)
            build_cell(
                repo_root,
                db_kind,
                commit,
                workflow_run,
                attempt,
                authority_run,
                evidence,
                base_artifacts / artifact_name(db_kind, identity),
                authority_validator=synthetic_authority_validator,
            )
        base_jobs = root / "jobs.json"
        fixture_write_json(base_jobs, {job: "success" for job in REQUIRED_JOBS})
        positive_receipt = root / "positive-receipt.json"
        aggregate(
            repo_root,
            commit,
            workflow_run,
            attempt,
            authority_run,
            base_jobs,
            base_artifacts,
            positive_receipt,
        )
        verify_command(repo_root, positive_receipt, base_artifacts)

        release_assets = root / "release-assets"
        release_package = release_assets / "package"
        release_package.mkdir(parents=True)
        for name in RELEASE_PACKAGE_FILES:
            (release_package / name).write_bytes(f"synthetic release {name}\n".encode("utf-8"))
        archive_path = release_assets / "v934-release-evidence.tar.gz"
        archive_path.write_bytes(b"synthetic release archive\n")
        (release_assets / "v934-release-evidence.archive.json").write_bytes(b'{"status":"passed"}\n')
        archive_sha = hashlib.sha256(archive_path.read_bytes()).hexdigest()
        (release_assets / "v934-release-evidence.tar.gz.sha256").write_text(
            f"{archive_sha}  v934-release-evidence.tar.gz\n", encoding="ascii"
        )
        release_names = {
            *(f"package/{name}" for name in RELEASE_PACKAGE_FILES),
            "v934-release-evidence.archive.json",
            "v934-release-evidence.tar.gz",
            "v934-release-evidence.tar.gz.sha256",
        }
        release_digests = {
            name: hashlib.sha256((release_assets / PurePosixPath(name)).read_bytes()).hexdigest()
            for name in release_names
        }
        fixture_write_json(
            release_assets / "release-assets.json",
            {
                "schema_version": 1,
                "kind": "v934-tested-release-assets",
                "status": "passed",
                "identity": {
                    "authority_run_id": authority_run,
                    "commit_sha": commit,
                    "workflow_run_id": int(workflow_run),
                    "attempt": int(attempt),
                    "gate_mode": "authority",
                },
                "files": release_digests,
            },
        )
        verify_release_assets(
            release_assets,
            authority_run,
            commit,
            workflow_run,
            attempt,
            "authority",
            release_digests["package/app.jar"],
        )
        release_zip = root / "release-assets.zip"
        with zipfile.ZipFile(
            release_zip, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True
        ) as archive_zip:
            for source in sorted(
                (path for path in release_assets.rglob("*") if path.is_file()),
                key=lambda path: path.relative_to(release_assets).as_posix().encode("utf-8"),
            ):
                archive_zip.write(
                    source, arcname=source.relative_to(release_assets).as_posix()
                )
        release_zip_digest = f"sha256:{sha256_bytes(release_zip.read_bytes())}"
        extracted_release = root / "release-assets-extracted"
        extracted_result = extract_verified_artifact(
            repo_root,
            release_zip,
            "20008",
            release_zip_digest,
            extracted_release,
        )
        require(
            extracted_result["files"] == len(RELEASE_ROOT_FILES) + len(RELEASE_PACKAGE_FILES),
            "E_SELF_TEST",
            "verified GitHub artifact extraction positive differs",
        )
        verify_release_assets(
            extracted_release,
            authority_run,
            commit,
            workflow_run,
            attempt,
            "authority",
            release_digests["package/app.jar"],
        )

        wrong_zip_digest_output = root / "release-assets-wrong-digest-output"
        expect_failure(
            "release-artifact-zip-wrong-digest",
            "E_ARTIFACT_ARCHIVE_DIGEST",
            lambda: extract_verified_artifact(
                repo_root,
                release_zip,
                "20008",
                f"sha256:{'0' * 64}",
                wrong_zip_digest_output,
            ),
            wrong_zip_digest_output,
            results,
        )

        release_zip_raw = release_zip.read_bytes()
        for framing_name, framing_payload in (
            ("prefix", b"non-zip-prefix\n" + release_zip_raw),
            ("trailing", release_zip_raw + b"\nnon-zip-trailing"),
            ("concatenated", release_zip_raw + release_zip_raw),
        ):
            framing_zip = root / f"release-assets-{framing_name}.zip"
            framing_zip.write_bytes(framing_payload)
            framing_output = root / f"release-assets-{framing_name}-output"
            expect_failure(
                f"release-artifact-zip-{framing_name}-framing",
                "E_ARTIFACT_ARCHIVE_FRAMING",
                lambda archive=framing_zip, destination=framing_output: (
                    extract_verified_artifact(
                        repo_root,
                        archive,
                        "20008",
                        f"sha256:{sha256_bytes(archive.read_bytes())}",
                        destination,
                    )
                ),
                framing_output,
                results,
            )

        traversal_zip = root / "release-assets-traversal.zip"
        with zipfile.ZipFile(traversal_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive_zip:
            archive_zip.writestr("../escape.txt", b"escape\n")
        traversal_zip_output = root / "release-assets-traversal-output"
        expect_failure(
            "release-artifact-zip-path-traversal",
            "E_ARTIFACT_ARCHIVE_PATH",
            lambda: extract_verified_artifact(
                repo_root,
                traversal_zip,
                "20008",
                f"sha256:{sha256_bytes(traversal_zip.read_bytes())}",
                traversal_zip_output,
            ),
            traversal_zip_output,
            results,
        )

        duplicate_zip = root / "release-assets-duplicate.zip"
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with zipfile.ZipFile(duplicate_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive_zip:
                archive_zip.writestr("release-assets.json", b"first\n")
                archive_zip.writestr("release-assets.json", b"second\n")
        duplicate_zip_output = root / "release-assets-duplicate-output"
        expect_failure(
            "release-artifact-zip-duplicate-entry",
            "E_ARTIFACT_ARCHIVE_DUPLICATE",
            lambda: extract_verified_artifact(
                repo_root,
                duplicate_zip,
                "20008",
                f"sha256:{sha256_bytes(duplicate_zip.read_bytes())}",
                duplicate_zip_output,
            ),
            duplicate_zip_output,
            results,
        )

        symlink_zip = root / "release-assets-symlink.zip"
        symlink_info = zipfile.ZipInfo("package/app.jar")
        symlink_info.create_system = 3
        symlink_info.external_attr = (stat.S_IFLNK | 0o777) << 16
        with zipfile.ZipFile(symlink_zip, "w") as archive_zip:
            archive_zip.writestr(symlink_info, b"../outside.jar")
        symlink_zip_output = root / "release-assets-symlink-output"
        expect_failure(
            "release-artifact-zip-symlink-entry",
            "E_ARTIFACT_ARCHIVE_SPECIAL",
            lambda: extract_verified_artifact(
                repo_root,
                symlink_zip,
                "20008",
                f"sha256:{sha256_bytes(symlink_zip.read_bytes())}",
                symlink_zip_output,
            ),
            symlink_zip_output,
            results,
        )
        release_target(
            "push",
            "refs/tags/v9.3.4",
            "v9.3.4",
            commit,
            commit,
            "",
        )

        github_repository = "example/foggy-data-mcp"
        main_run_id = int(workflow_run)
        main_attempt = int(attempt)
        main_run_response = {
            "id": main_run_id,
            "run_attempt": main_attempt,
            "workflow_id": 934,
            "run_number": 44,
            "name": AUTHORITY_WORKFLOW_NAME,
            "event": "push",
            "head_branch": "main",
            "head_sha": commit,
            "status": "completed",
            "conclusion": "success",
            "path": AUTHORITY_WORKFLOW_FILE,
            "repository": {"full_name": github_repository},
            "head_repository": {"full_name": github_repository},
        }
        main_jobs_response = {
            "total_count": len(AUTHORITY_JOB_NAMES),
            "jobs": [
                {
                    "id": 10_000 + index,
                    "name": name,
                    "run_id": main_run_id,
                    "run_attempt": main_attempt,
                    "head_sha": commit,
                    "status": "completed",
                    "conclusion": "success",
                }
                for index, name in enumerate(AUTHORITY_JOB_NAMES)
            ],
        }
        main_artifact_names = expected_authority_artifact_names(
            commit, main_run_id, main_attempt
        )
        main_artifacts_response = {
            "total_count": len(main_artifact_names),
            "artifacts": [
                {
                    "id": 20_000 + index,
                    "name": name,
                    "size_in_bytes": 1_000 + index,
                    "digest": f"sha256:{index + 1:064x}",
                    "expired": False,
                    "workflow_run": {
                        "id": main_run_id,
                        "head_branch": "main",
                        "head_sha": commit,
                    },
                }
                for index, name in enumerate(main_artifact_names)
            ],
        }
        main_run_path = root / "main-run.json"
        main_jobs_path = root / "main-jobs.json"
        main_artifacts_path = root / "main-artifacts.json"
        main_runs_path = root / "main-runs.json"
        fixture_write_json(main_run_path, main_run_response)
        fixture_write_json(main_jobs_path, main_jobs_response)
        fixture_write_json(main_artifacts_path, main_artifacts_response)
        fixture_write_json(
            main_runs_path,
            {"total_count": 1, "workflow_runs": [main_run_response]},
        )
        resolved_main = resolve_main_authority_api(
            repo_root, main_runs_path, github_repository, commit
        )
        require(
            resolved_main["workflow_run_id"] == main_run_id
            and resolved_main["attempt"] == main_attempt,
            "E_SELF_TEST",
            "main authority resolver positive differs",
        )
        verified_main = verify_main_authority_api(
            repo_root,
            main_run_path,
            main_jobs_path,
            main_artifacts_path,
            github_repository,
            commit,
            workflow_run,
            attempt,
        )
        require(
            verified_main["authority_run_id"] == f"ci-{workflow_run}-{attempt}"
            and verified_main["artifact_count"] == len(main_artifact_names),
            "E_SELF_TEST",
            "main authority API positive differs",
        )

        def api_fixture(path: Path) -> dict[str, Any]:
            return fixture_load_json(path)

        prior_attempt_artifacts = api_fixture(main_artifacts_path)
        prior_row = dict(prior_attempt_artifacts["artifacts"][0])
        prior_row["id"] = 29_999
        prior_row["name"] = expected_authority_artifact_names(
            commit, main_run_id, main_attempt - 1
        )[0]
        prior_row["expired"] = True
        prior_attempt_artifacts["artifacts"].append(prior_row)
        prior_attempt_artifacts["total_count"] += 1
        prior_attempt_artifacts_path = root / "main-artifacts-with-prior-attempt.json"
        fixture_write_json(prior_attempt_artifacts_path, prior_attempt_artifacts)
        prior_verified_main = verify_main_authority_api(
            repo_root,
            main_run_path,
            main_jobs_path,
            prior_attempt_artifacts_path,
            github_repository,
            commit,
            workflow_run,
            attempt,
        )
        require(
            prior_verified_main["artifact_count"] == len(main_artifact_names),
            "E_SELF_TEST",
            "expired prior-attempt artifacts must not alter current authority evidence",
        )

        wrong_main_event_path = root / "main-run-wrong-event.json"
        wrong_main_event = api_fixture(main_run_path)
        wrong_main_event["event"] = "workflow_dispatch"
        fixture_write_json(wrong_main_event_path, wrong_main_event)
        expect_failure(
            "main-authority-wrong-event",
            "E_MAIN_RUN",
            lambda: verify_main_authority_api(
                repo_root, wrong_main_event_path, main_jobs_path, main_artifacts_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        failed_main_job_path = root / "main-jobs-failed-aggregator.json"
        failed_main_jobs = api_fixture(main_jobs_path)
        for row in failed_main_jobs["jobs"]:
            if row["name"] == AGGREGATOR_NAME:
                row["conclusion"] = "failure"
        fixture_write_json(failed_main_job_path, failed_main_jobs)
        expect_failure(
            "main-authority-failed-aggregator",
            "E_MAIN_JOB_STATE",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, failed_main_job_path, main_artifacts_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        missing_job_attempt_path = root / "main-jobs-missing-attempt.json"
        missing_job_attempt = api_fixture(main_jobs_path)
        missing_job_attempt["jobs"][0].pop("run_attempt")
        fixture_write_json(missing_job_attempt_path, missing_job_attempt)
        expect_failure(
            "main-authority-missing-job-attempt",
            "E_MAIN_JOB_STATE",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, missing_job_attempt_path, main_artifacts_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        wrong_job_attempt_path = root / "main-jobs-wrong-attempt.json"
        wrong_job_attempt = api_fixture(main_jobs_path)
        wrong_job_attempt["jobs"][0]["run_attempt"] = main_attempt + 1
        fixture_write_json(wrong_job_attempt_path, wrong_job_attempt)
        expect_failure(
            "main-authority-wrong-job-attempt",
            "E_MAIN_JOB_STATE",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, wrong_job_attempt_path, main_artifacts_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        prior_attempt_jobs_path = root / "main-jobs-prior-attempt.json"
        prior_attempt_jobs = api_fixture(main_jobs_path)
        for row in prior_attempt_jobs["jobs"]:
            row["run_attempt"] = main_attempt - 1
        fixture_write_json(prior_attempt_jobs_path, prior_attempt_jobs)
        expect_failure(
            "main-authority-prior-attempt-job-splice",
            "E_MAIN_JOB_STATE",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, prior_attempt_jobs_path, main_artifacts_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        missing_main_artifact_path = root / "main-artifacts-missing.json"
        missing_main_artifacts = api_fixture(main_artifacts_path)
        missing_main_artifacts["artifacts"].pop()
        missing_main_artifacts["total_count"] -= 1
        fixture_write_json(missing_main_artifact_path, missing_main_artifacts)
        expect_failure(
            "main-authority-missing-artifact",
            "E_MAIN_ARTIFACT_SET",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, main_jobs_path, missing_main_artifact_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        expired_main_artifact_path = root / "main-artifacts-expired.json"
        expired_main_artifacts = api_fixture(main_artifacts_path)
        expired_main_artifacts["artifacts"][0]["expired"] = True
        fixture_write_json(expired_main_artifact_path, expired_main_artifacts)
        expect_failure(
            "main-authority-expired-artifact",
            "E_MAIN_ARTIFACT_EXPIRED",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, main_jobs_path, expired_main_artifact_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        bad_main_digest_path = root / "main-artifacts-bad-digest.json"
        bad_main_digest = api_fixture(main_artifacts_path)
        bad_main_digest["artifacts"][0]["digest"] = None
        fixture_write_json(bad_main_digest_path, bad_main_digest)
        expect_failure(
            "main-authority-missing-artifact-digest",
            "E_MAIN_ARTIFACT_DIGEST",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, main_jobs_path, bad_main_digest_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        future_main_artifact_path = root / "main-artifacts-future-attempt.json"
        future_main_artifacts = api_fixture(main_artifacts_path)
        future_row = dict(future_main_artifacts["artifacts"][0])
        future_row["id"] = 30_000
        future_row["name"] = expected_authority_artifact_names(
            commit, main_run_id, main_attempt + 1
        )[0]
        future_main_artifacts["artifacts"].append(future_row)
        future_main_artifacts["total_count"] += 1
        fixture_write_json(future_main_artifact_path, future_main_artifacts)
        expect_failure(
            "main-authority-future-attempt-artifact",
            "E_MAIN_ARTIFACT_SET",
            lambda: verify_main_authority_api(
                repo_root, main_run_path, main_jobs_path, future_main_artifact_path,
                github_repository, commit, workflow_run, attempt
            ),
            None,
            results,
        )

        ambiguous_main_runs_path = root / "main-runs-ambiguous.json"
        second_main_run = dict(main_run_response)
        second_main_run["id"] = main_run_id + 1
        fixture_write_json(
            ambiguous_main_runs_path,
            {
                "total_count": 2,
                "workflow_runs": [main_run_response, second_main_run],
            },
        )
        expect_failure(
            "main-authority-ambiguous-run",
            "E_MAIN_RUN_SET",
            lambda: resolve_main_authority_api(
                repo_root, ambiguous_main_runs_path, github_repository, commit
            ),
            None,
            results,
        )

        missing_release = root / "release-missing"
        shutil.copytree(release_assets, missing_release)
        (missing_release / "package/app.jar").unlink()
        expect_failure(
            "release-missing-asset",
            "E_RELEASE_ASSET_SET",
            lambda: verify_release_assets(
                missing_release, authority_run, commit, workflow_run, attempt,
                "authority", release_digests["package/app.jar"]
            ),
            None,
            results,
        )
        extra_release = root / "release-extra"
        shutil.copytree(release_assets, extra_release)
        (extra_release / "unexpected.bin").write_bytes(b"unexpected\n")
        expect_failure(
            "release-extra-asset",
            "E_RELEASE_ASSET_SET",
            lambda: verify_release_assets(
                extra_release, authority_run, commit, workflow_run, attempt,
                "authority", release_digests["package/app.jar"]
            ),
            None,
            results,
        )
        tampered_release = root / "release-tampered"
        shutil.copytree(release_assets, tampered_release)
        with (tampered_release / "package/app.jar").open("ab") as stream:
            stream.write(b"tampered\n")
        expect_failure(
            "release-tampered-jar",
            "E_RELEASE_ASSET_DIGEST",
            lambda: verify_release_assets(
                tampered_release, authority_run, commit, workflow_run, attempt,
                "authority", release_digests["package/app.jar"]
            ),
            None,
            results,
        )
        wrong_release_identity = root / "release-wrong-identity"
        shutil.copytree(release_assets, wrong_release_identity)
        wrong_receipt = fixture_load_json(wrong_release_identity / "release-assets.json")
        wrong_receipt["identity"]["commit_sha"] = "0" * 40
        fixture_write_json(wrong_release_identity / "release-assets.json", wrong_receipt)
        expect_failure(
            "release-wrong-identity",
            "E_RELEASE_IDENTITY",
            lambda: verify_release_assets(
                wrong_release_identity, authority_run, commit, workflow_run, attempt,
                "authority", release_digests["package/app.jar"]
            ),
            None,
            results,
        )
        expect_failure(
            "release-tag-sha-mismatch",
            "E_RELEASE_TARGET",
            lambda: release_target(
                "push", "refs/tags/v9.3.4", "v9.3.4", commit, "0" * 40, ""
            ),
            None,
            results,
        )
        expect_failure(
            "release-wrong-tag-version",
            "E_RELEASE_TARGET",
            lambda: release_target(
                "push", "refs/tags/v9.4.0", "v9.4.0", commit, commit, ""
            ),
            None,
            results,
        )
        expect_failure(
            "release-wrong-dispatch-version",
            "E_RELEASE_VERSION",
            lambda: release_target(
                "workflow_dispatch", "refs/heads/main", "main", commit, commit, "9.4.0"
            ),
            None,
            results,
        )

        workflow_paths = {
            "authority": repo_root / ".github/workflows/test-ci-evidence-chain.yml",
            "release": repo_root / ".github/workflows/release.yml",
            "legacy-model": repo_root / ".github/workflows/model-lifecycle-concurrency.yml",
            "legacy-pivot": repo_root / ".github/workflows/pivot-release-readiness.yml",
        }
        workflow_texts = {
            label: path.read_text(encoding="utf-8") for label, path in workflow_paths.items()
        }
        validate_workflow_texts(workflow_texts)

        authority_missing_event = dict(workflow_texts)
        authority_missing_event["authority"] = authority_missing_event[
            "authority"
        ].replace("  pull_request:\n", "", 1)
        expect_failure(
            "authority-missing-pull-request-event",
            "E_WORKFLOW_EVENT",
            lambda: validate_workflow_texts(authority_missing_event),
            None,
            results,
        )
        authority_extra_event = dict(workflow_texts)
        authority_extra_event["authority"] = authority_extra_event["authority"].replace(
            "  workflow_call:\n",
            "  schedule:\n"
            "    - cron: '0 0 * * *'\n"
            "  workflow_call:\n",
            1,
        )
        expect_failure(
            "authority-extra-schedule-event",
            "E_WORKFLOW_EVENT",
            lambda: validate_workflow_texts(authority_extra_event),
            None,
            results,
        )
        release_wrong_tag_event = dict(workflow_texts)
        release_wrong_tag_event["release"] = release_wrong_tag_event["release"].replace(
            "      - 'v9.3.4'\n", "      - 'v9.4.0'\n", 1
        )
        expect_failure(
            "release-wrong-trigger-tag",
            "E_WORKFLOW_EVENT",
            lambda: validate_workflow_texts(release_wrong_tag_event),
            None,
            results,
        )

        authority_extra_job = dict(workflow_texts)
        authority_extra_job["authority"] += (
            "\n  unexpected-authority-job:\n"
            "    name: Unexpected authority job\n"
            "    runs-on: ubuntu-latest\n"
        )
        expect_failure(
            "authority-extra-job",
            "E_WORKFLOW_DAG",
            lambda: validate_workflow_texts(authority_extra_job),
            None,
            results,
        )
        authority_missing_edge = dict(workflow_texts)
        authority_missing_edge["authority"] = authority_missing_edge[
            "authority"
        ].replace(
            "  coverage:\n"
            "    name: Immutable coverage collector/check\n"
            "    needs: inventory-unit\n",
            "  coverage:\n"
            "    name: Immutable coverage collector/check\n",
            1,
        )
        expect_failure(
            "authority-coverage-missing-edge",
            "E_WORKFLOW_DAG",
            lambda: validate_workflow_texts(authority_missing_edge),
            None,
            results,
        )
        authority_aggregator_edge = dict(workflow_texts)
        authority_aggregator_edge["authority"] = authority_aggregator_edge[
            "authority"
        ].replace(
            "  required-aggregator:\n"
            "    name: 9.3.4 Test & Release Authority\n"
            "    if: ${{ always() }}\n"
            "    needs:\n"
            "      - inventory-unit\n"
            "      - sqlite-integration\n"
            "      - database-matrix\n"
            "      - external-integration\n"
            "      - coverage\n"
            "      - package-evidence\n",
            "  required-aggregator:\n"
            "    name: 9.3.4 Test & Release Authority\n"
            "    if: ${{ always() }}\n"
            "    needs:\n"
            "      - inventory-unit\n"
            "      - sqlite-integration\n"
            "      - database-matrix\n"
            "      - external-integration\n"
            "      - coverage\n",
            1,
        )
        expect_failure(
            "authority-aggregator-missing-dependency",
            "E_WORKFLOW_DAG",
            lambda: validate_workflow_texts(authority_aggregator_edge),
            None,
            results,
        )
        release_extra_job = dict(workflow_texts)
        release_extra_job["release"] += (
            "\n  unexpected-release-job:\n"
            "    name: Unexpected release job\n"
            "    runs-on: ubuntu-latest\n"
        )
        expect_failure(
            "release-extra-job",
            "E_WORKFLOW_DAG",
            lambda: validate_workflow_texts(release_extra_job),
            None,
            results,
        )
        release_missing_edge = dict(workflow_texts)
        release_missing_edge["release"] = release_missing_edge["release"].replace(
            "  docker-publish:\n"
            "    name: Publish verified runtime-only image\n"
            "    if: github.event_name == 'push' && github.ref == 'refs/tags/v9.3.4'\n"
            "    needs: release-dry-run\n",
            "  docker-publish:\n"
            "    name: Publish verified runtime-only image\n"
            "    if: github.event_name == 'push' && github.ref == 'refs/tags/v9.3.4'\n",
            1,
        )
        expect_failure(
            "release-docker-publish-missing-edge",
            "E_WORKFLOW_DAG",
            lambda: validate_workflow_texts(release_missing_edge),
            None,
            results,
        )
        authority_missing_closure = dict(workflow_texts)
        authority_missing_closure["authority"] = authority_missing_closure[
            "authority"
        ].replace("          sha256sum -c scripts/v934/step5/SHA256SUMS\n", "", 1)
        expect_failure(
            "authority-job-missing-tooling-closure",
            "E_WORKFLOW_TOOLING_CLOSURE",
            lambda: validate_workflow_texts(authority_missing_closure),
            None,
            results,
        )
        authority_unnamed_preclosure = dict(workflow_texts)
        authority_unnamed_preclosure["authority"] = authority_unnamed_preclosure[
            "authority"
        ].replace(
            "          fetch-depth: 0\n\n"
            "      - name: Verify frozen Steps 4-6 workflow/tooling bytes\n",
            "          fetch-depth: 0\n\n"
            "      - run: echo pre-closure-bypass\n\n"
            "      - name: Verify frozen Steps 4-6 workflow/tooling bytes\n",
            1,
        )
        expect_failure(
            "authority-unnamed-step-before-tooling-closure",
            "E_WORKFLOW_TOOLING_CLOSURE",
            lambda: validate_workflow_texts(authority_unnamed_preclosure),
            None,
            results,
        )
        authority_disabled_closure = dict(workflow_texts)
        authority_disabled_closure["authority"] = authority_disabled_closure[
            "authority"
        ].replace(
            "      - name: Verify frozen Steps 4-6 workflow/tooling bytes\n"
            "        shell: bash\n",
            "      - name: Verify frozen Steps 4-6 workflow/tooling bytes\n"
            "        if: false\n"
            "        shell: bash\n",
            1,
        )
        expect_failure(
            "authority-disabled-tooling-closure",
            "E_WORKFLOW_TOOLING_CLOSURE",
            lambda: validate_workflow_texts(authority_disabled_closure),
            None,
            results,
        )
        authority_checkout_extra_configuration = dict(workflow_texts)
        authority_checkout_extra_configuration["authority"] = (
            authority_checkout_extra_configuration["authority"].replace(
                "  sqlite-integration:\n"
                "    name: SQLite broad integration evidence\n"
                "    needs: inventory-unit\n"
                "    runs-on: ubuntu-latest\n"
                "    timeout-minutes: 30\n"
                "    steps:\n"
                "      - name: Checkout exact workflow commit\n"
                "        uses: actions/checkout@"
                "34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1\n",
                "  sqlite-integration:\n"
                "    name: SQLite broad integration evidence\n"
                "    needs: inventory-unit\n"
                "    runs-on: ubuntu-latest\n"
                "    timeout-minutes: 30\n"
                "    steps:\n"
                "      - name: Checkout exact workflow commit\n"
                "        uses: actions/checkout@"
                "34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1\n"
                "        with:\n"
                "          repository: attacker/alternate-source\n",
                1,
            )
        )
        expect_failure(
            "authority-checkout-extra-repository",
            "E_WORKFLOW_CHECKOUT",
            lambda: validate_workflow_texts(authority_checkout_extra_configuration),
            None,
            results,
        )
        authority_preclosure_container = dict(workflow_texts)
        authority_preclosure_container["authority"] = authority_preclosure_container[
            "authority"
        ].replace(
            "  sqlite-integration:\n"
            "    name: SQLite broad integration evidence\n"
            "    needs: inventory-unit\n"
            "    runs-on: ubuntu-latest\n",
            "  sqlite-integration:\n"
            "    name: SQLite broad integration evidence\n"
            "    needs: inventory-unit\n"
            "    container: ubuntu:24.04\n"
            "    runs-on: ubuntu-latest\n",
            1,
        )
        expect_failure(
            "authority-job-preclosure-container",
            "E_WORKFLOW_EXECUTION_CONTEXT",
            lambda: validate_workflow_texts(authority_preclosure_container),
            None,
            results,
        )
        authority_job_continue_on_error = dict(workflow_texts)
        authority_job_continue_on_error["authority"] = authority_job_continue_on_error[
            "authority"
        ].replace(
            "  required-aggregator:\n"
            "    name: 9.3.4 Test & Release Authority\n"
            "    if: ${{ always() }}\n",
            "  required-aggregator:\n"
            "    name: 9.3.4 Test & Release Authority\n"
            "    if: ${{ always() }}\n"
            "    continue-on-error: true\n",
            1,
        )
        expect_failure(
            "authority-aggregator-job-continue-on-error",
            "E_WORKFLOW_EXECUTION_CONTEXT",
            lambda: validate_workflow_texts(authority_job_continue_on_error),
            None,
            results,
        )
        authority_changed_aggregator_guard = dict(workflow_texts)
        authority_changed_aggregator_guard["authority"] = (
            authority_changed_aggregator_guard["authority"].replace(
                "  required-aggregator:\n"
                "    name: 9.3.4 Test & Release Authority\n"
                "    if: ${{ always() }}\n",
                "  required-aggregator:\n"
                "    name: 9.3.4 Test & Release Authority\n"
                "    if: ${{ success() }}\n",
                1,
            )
        )
        expect_failure(
            "authority-aggregator-changed-guard",
            "E_WORKFLOW_AGGREGATOR_GUARD",
            lambda: validate_workflow_texts(authority_changed_aggregator_guard),
            None,
            results,
        )
        authority_conditional_required_job = dict(workflow_texts)
        authority_conditional_required_job["authority"] = authority_conditional_required_job[
            "authority"
        ].replace(
            "  coverage:\n"
            "    name: Immutable coverage collector/check\n"
            "    needs: inventory-unit\n",
            "  coverage:\n"
            "    name: Immutable coverage collector/check\n"
            "    if: false\n"
            "    needs: inventory-unit\n",
            1,
        )
        expect_failure(
            "authority-required-job-conditional-skip",
            "E_WORKFLOW_JOB_GUARD",
            lambda: validate_workflow_texts(authority_conditional_required_job),
            None,
            results,
        )
        authority_shallow_coverage = dict(workflow_texts)
        authority_shallow_coverage["authority"] = authority_shallow_coverage[
            "authority"
        ].replace(
            "  coverage:\n"
            "    name: Immutable coverage collector/check\n"
            "    needs: inventory-unit\n"
            "    runs-on: ubuntu-latest\n"
            "    timeout-minutes: 45\n"
            "    steps:\n"
            "      - name: Checkout exact workflow commit\n"
            "        uses: actions/checkout@"
            "34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1\n"
            "        with:\n"
            "          fetch-depth: 0\n",
            "  coverage:\n"
            "    name: Immutable coverage collector/check\n"
            "    needs: inventory-unit\n"
            "    runs-on: ubuntu-latest\n"
            "    timeout-minutes: 45\n"
            "    steps:\n"
            "      - name: Checkout exact workflow commit\n"
            "        uses: actions/checkout@"
            "34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1\n",
            1,
        )
        expect_failure(
            "authority-coverage-shallow-checkout",
            "E_WORKFLOW_CHECKOUT",
            lambda: validate_workflow_texts(authority_shallow_coverage),
            None,
            results,
        )
        authority_coverage_missing_parent_guard = dict(workflow_texts)
        authority_coverage_missing_parent_guard["authority"] = (
            authority_coverage_missing_parent_guard["authority"].replace(
                "          test ! -e target/v934-ci\n"
                "          mkdir target/v934-ci\n"
                '          python3 "$RELEASE_ARTIFACT_TOOL" extract-verify \\\n',
                "          mkdir target/v934-ci\n"
                '          python3 "$RELEASE_ARTIFACT_TOOL" extract-verify \\\n',
                1,
            )
        )
        expect_failure(
            "authority-coverage-missing-extraction-parent-guard",
            "E_WORKFLOW_EXTRACT_PARENT",
            lambda: validate_workflow_texts(authority_coverage_missing_parent_guard),
            None,
            results,
        )
        authority_package_missing_parent = dict(workflow_texts)
        package_marker = "\n  package-evidence:\n"
        aggregator_marker = "\n  required-aggregator:\n"
        package_prefix, separator, package_tail = authority_package_missing_parent[
            "authority"
        ].partition(package_marker)
        require(bool(separator), "E_SELF_TEST", "package-evidence fixture marker is absent")
        package_job, separator, package_suffix = package_tail.partition(aggregator_marker)
        require(bool(separator), "E_SELF_TEST", "required-aggregator fixture marker is absent")
        mutated_package_job = package_job.replace(
            "          test ! -e target/v934-ci\n"
            "          mkdir target/v934-ci\n"
            '          python3 "$RELEASE_ARTIFACT_TOOL" extract-verify \\\n',
            '          python3 "$RELEASE_ARTIFACT_TOOL" extract-verify \\\n',
            1,
        )
        require(
            mutated_package_job != package_job,
            "E_SELF_TEST",
            "package-evidence extraction-parent fixture mutation was not applied",
        )
        authority_package_missing_parent["authority"] = (
            package_prefix
            + package_marker
            + mutated_package_job
            + aggregator_marker
            + package_suffix
        )
        expect_failure(
            "authority-package-missing-fresh-extraction-parent",
            "E_WORKFLOW_EXTRACT_PARENT",
            lambda: validate_workflow_texts(authority_package_missing_parent),
            None,
            results,
        )
        release_missing_publish_guard = dict(workflow_texts)
        release_missing_publish_guard["release"] = release_missing_publish_guard[
            "release"
        ].replace(
            "  docker-publish:\n"
            "    name: Publish verified runtime-only image\n"
            "    if: github.event_name == 'push' && github.ref == 'refs/tags/v9.3.4'\n",
            "  docker-publish:\n"
            "    name: Publish verified runtime-only image\n",
            1,
        )
        expect_failure(
            "release-docker-publish-missing-guard",
            "E_RELEASE_PUBLISH_GUARD",
            lambda: validate_workflow_texts(release_missing_publish_guard),
            None,
            results,
        )
        release_changed_publish_guard = dict(workflow_texts)
        release_changed_publish_guard["release"] = release_changed_publish_guard[
            "release"
        ].replace(
            "  github-release:\n"
            "    name: Publish same-tested GitHub release assets\n"
            "    if: github.event_name == 'push' && github.ref == 'refs/tags/v9.3.4'\n",
            "  github-release:\n"
            "    name: Publish same-tested GitHub release assets\n"
            "    if: github.event_name == 'workflow_dispatch'\n",
            1,
        )
        expect_failure(
            "release-github-release-changed-guard",
            "E_RELEASE_PUBLISH_GUARD",
            lambda: validate_workflow_texts(release_changed_publish_guard),
            None,
            results,
        )

        tooling_raw = secure_regular_bytes(
            repo_root / TOOLING_MANIFEST_RELATIVE_PATH,
            "negative Step 6 tooling manifest",
            maximum=1024 * 1024,
        )
        tooling_lines = tooling_raw.splitlines(keepends=True)
        require(
            len(tooling_lines) == len(FROZEN_TOOLING_PATHS),
            "E_SELF_TEST",
            "positive Step 6 tooling manifest row count differs",
        )
        expect_failure(
            "step6-tooling-missing-row",
            "E_TOOLING_MANIFEST",
            lambda: validate_tooling_manifest(repo_root, b"".join(tooling_lines[:-1])),
            None,
            results,
        )
        wrong_tooling_digest = bytearray(tooling_raw)
        wrong_tooling_digest[:64] = b"0" * 64
        expect_failure(
            "step6-tooling-digest-drift",
            "E_TOOLING_DRIFT",
            lambda: validate_tooling_manifest(repo_root, bytes(wrong_tooling_digest)),
            None,
            results,
        )
        expect_failure(
            "step6-tooling-extra-row",
            "E_TOOLING_MANIFEST",
            lambda: validate_tooling_manifest(
                repo_root,
                tooling_raw
                + b"0" * 64
                + b"  scripts/v934/step6/unexpected-tool.py\n",
            ),
            None,
            results,
        )
        legacy_masquerade = dict(workflow_texts)
        legacy_masquerade["legacy-model"] += "\n  pull_request:\n"
        expect_failure(
            "legacy-partial-authority-masquerade",
            "E_LEGACY_AUTHORITY",
            lambda: validate_workflow_texts(legacy_masquerade),
            None,
            results,
        )
        release_rebuild = dict(workflow_texts)
        release_rebuild["release"] += "\n# mvn -DskipTests package\n"
        expect_failure(
            "release-source-rebuild",
            "E_RELEASE_REBUILD",
            lambda: validate_workflow_texts(release_rebuild),
            None,
            results,
        )
        release_maven_wrapper_rebuild = dict(workflow_texts)
        release_maven_wrapper_rebuild["release"] += "\n# ./mvnw package\n"
        expect_failure(
            "release-maven-wrapper-rebuild",
            "E_RELEASE_REBUILD",
            lambda: validate_workflow_texts(release_maven_wrapper_rebuild),
            None,
            results,
        )
        release_gradle_wrapper_rebuild = dict(workflow_texts)
        release_gradle_wrapper_rebuild["release"] += "\n# ./gradlew build\n"
        expect_failure(
            "release-gradle-wrapper-rebuild",
            "E_RELEASE_REBUILD",
            lambda: validate_workflow_texts(release_gradle_wrapper_rebuild),
            None,
            results,
        )
        release_fresh_gate = dict(workflow_texts)
        release_fresh_gate["release"] += (
            "\n  forbidden-fresh-gate:\n"
            "    uses: ./.github/workflows/test-ci-evidence-chain.yml\n"
        )
        expect_failure(
            "release-fresh-authority-gate",
            "E_LOCAL_ACTION",
            lambda: validate_workflow_texts(release_fresh_gate),
            None,
            results,
        )
        authority_local_action = dict(workflow_texts)
        authority_local_action["authority"] += (
            "\n  opaque-authority-step:\n"
            "    steps:\n"
            "      - uses: ./.github/actions/opaque-authority\n"
        )
        expect_failure(
            "authority-opaque-local-action",
            "E_LOCAL_ACTION",
            lambda: validate_workflow_texts(authority_local_action),
            None,
            results,
        )
        release_local_rebuild = dict(workflow_texts)
        release_local_rebuild["release"] += (
            "\n  opaque-release-step:\n"
            "    steps:\n"
            "      - uses: ./.github/actions/opaque-rebuild\n"
        )
        expect_failure(
            "release-opaque-local-rebuild-action",
            "E_LOCAL_ACTION",
            lambda: validate_workflow_texts(release_local_rebuild),
            None,
            results,
        )
        release_name_download = dict(workflow_texts)
        release_name_download["release"] += "\n# uses: actions/download-artifact@v4\n"
        expect_failure(
            "release-name-based-artifact-download",
            "E_RELEASE_ARTIFACT_TRANSPORT",
            lambda: validate_workflow_texts(release_name_download),
            None,
            results,
        )
        release_missing_digest_transport = dict(workflow_texts)
        release_missing_digest_transport["release"] = release_missing_digest_transport[
            "release"
        ].replace("extract-verified-artifact", "missing-artifact-verifier", 1)
        expect_failure(
            "release-missing-artifact-digest-verifier",
            "E_RELEASE_ARTIFACT_TRANSPORT",
            lambda: validate_workflow_texts(release_missing_digest_transport),
            None,
            results,
        )
        authority_tag_action = dict(workflow_texts)
        authority_tag_action["authority"] = authority_tag_action["authority"].replace(
            "actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5",
            "actions/checkout@v4",
            1,
        )
        expect_failure(
            "authority-movable-action-tag",
            "E_ACTION_PIN",
            lambda: validate_workflow_texts(authority_tag_action),
            None,
            results,
        )
        release_tag_action = dict(workflow_texts)
        release_tag_action["release"] = release_tag_action["release"].replace(
            "docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9",
            "docker/login-action@v3",
            1,
        )
        expect_failure(
            "release-movable-action-tag",
            "E_ACTION_PIN",
            lambda: validate_workflow_texts(release_tag_action),
            None,
            results,
        )
        release_missing_base_check = dict(workflow_texts)
        release_missing_base_check["release"] = release_missing_base_check[
            "release"
        ].replace(
            ".image.base_image.manifest_digest",
            ".image.base_image.missing_manifest_digest",
            1,
        )
        expect_failure(
            "release-missing-base-manifest-check",
            "E_RELEASE_BASE_IMAGE",
            lambda: validate_workflow_texts(release_missing_base_check),
            None,
            results,
        )
        release_spliced_base = dict(workflow_texts)
        release_spliced_base["release"] = release_spliced_base["release"].replace(
            'test "$GATE_BASE_MANIFEST_DIGEST" = "$DRY_BASE_MANIFEST_DIGEST"',
            'test "$GATE_BASE_MANIFEST_DIGEST" != "$DRY_BASE_MANIFEST_DIGEST"',
            1,
        )
        expect_failure(
            "release-spliced-gate-dry-base",
            "E_RELEASE_BASE_IMAGE",
            lambda: validate_workflow_texts(release_spliced_base),
            None,
            results,
        )

        dockerfile_text = (repo_root / "foggy-mcp-launcher/Dockerfile.release").read_text(
            encoding="utf-8"
        )
        mutable_dockerfile = dockerfile_text.replace(
            f"{RUNTIME_BASE_TAG_REFERENCE}@{RUNTIME_BASE_MANIFEST_DIGEST}",
            RUNTIME_BASE_TAG_REFERENCE,
            1,
        )
        expect_failure(
            "runtime-base-mutable-tag",
            "E_RELEASE_BASE_IMAGE",
            lambda: validate_runtime_base_dockerfile_text(mutable_dockerfile),
            None,
            results,
        )
        wrong_base_manifest = dockerfile_text.replace(
            RUNTIME_BASE_MANIFEST_DIGEST, "sha256:" + "0" * 64, 1
        )
        expect_failure(
            "runtime-base-wrong-manifest",
            "E_RELEASE_BASE_IMAGE",
            lambda: validate_runtime_base_dockerfile_text(wrong_base_manifest),
            None,
            results,
        )
        wrong_base_platform = dockerfile_text.replace(
            "--platform=linux/amd64", "--platform=linux/arm64", 1
        )
        expect_failure(
            "runtime-base-wrong-platform",
            "E_RELEASE_BASE_IMAGE",
            lambda: validate_runtime_base_dockerfile_text(wrong_base_platform),
            None,
            results,
        )

        tampered_jobs_receipt = root / "receipt-job-binding-tamper.json"
        tampered_jobs_value = fixture_load_json(positive_receipt)
        tampered_jobs_value["job_states_sha256"] = "0" * 64
        fixture_write_json(tampered_jobs_receipt, tampered_jobs_value)
        expect_failure(
            "job-states-binding-tamper",
            "E_RECEIPT",
            lambda: verify_command(repo_root, tampered_jobs_receipt, base_artifacts),
            None,
            results,
        )

        for state in ("failure", "skipped", "cancelled"):
            jobs_path = root / f"jobs-{state}.json"
            states = {job: "success" for job in REQUIRED_JOBS}
            states["coverage"] = state
            fixture_write_json(jobs_path, states)
            receipt = root / f"receipt-job-{state}.json"
            expect_failure(
                f"required-job-{state}",
                "E_REQUIRED_JOB",
                lambda p=jobs_path, o=receipt: aggregate(
                    repo_root, commit, workflow_run, attempt, authority_run, p, base_artifacts, o
                ),
                receipt,
                results,
            )

        def cloned(name: str) -> Path:
            destination = root / name
            shutil.copytree(base_artifacts, destination, symlinks=True)
            return destination

        missing = cloned("missing")
        shutil.rmtree(missing / artifact_name("mysql8", identity))
        missing_receipt = root / "receipt-missing.json"
        expect_failure(
            "missing-artifact",
            "E_ARTIFACT_SET",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, missing, missing_receipt),
            missing_receipt,
            results,
        )

        duplicate = cloned("duplicate")
        shutil.copytree(
            duplicate / artifact_name("sqlite", identity),
            duplicate / f"v934-db-duplicate-{commit}-{workflow_run}-{attempt}",
        )
        duplicate_receipt = root / "receipt-duplicate.json"
        expect_failure(
            "duplicate-artifact",
            "E_ARTIFACT_SET",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, duplicate, duplicate_receipt),
            duplicate_receipt,
            results,
        )

        wrong_kind = cloned("wrong-kind")
        (wrong_kind / artifact_name("sqlite", identity)).rename(
            wrong_kind / f"v934-db-oracle-{commit}-{workflow_run}-{attempt}"
        )
        wrong_kind_receipt = root / "receipt-wrong-kind.json"
        expect_failure(
            "wrong-kind-artifact",
            "E_ARTIFACT_SET",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, wrong_kind, wrong_kind_receipt),
            wrong_kind_receipt,
            results,
        )

        def mutate_manifest(tree: Path, db_kind: str, mutation: Callable[[dict[str, Any]], None]) -> None:
            path = tree / artifact_name(db_kind, identity) / CELL_MANIFEST
            value = fixture_load_json(path)
            mutation(value)
            fixture_write_json(path, value)

        stale = cloned("stale")
        mutate_manifest(stale, "mysql57", lambda value: value["identity"].__setitem__("attempt", 1))
        stale_receipt = root / "receipt-stale.json"
        expect_failure(
            "stale-artifact",
            "E_ARTIFACT_NAME",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, stale, stale_receipt),
            stale_receipt,
            results,
        )

        tampered = cloned("tampered")
        with (tampered / artifact_name("postgres15", identity) / PAYLOAD_DIRECTORY / "result.json").open("ab") as stream:
            stream.write(b"tampered\n")
        tampered_receipt = root / "receipt-tampered.json"
        expect_failure(
            "tampered-evidence",
            "E_EVIDENCE_SIZE",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, tampered, tampered_receipt),
            tampered_receipt,
            results,
        )

        authority_tampered = cloned("authority-tampered")
        mutate_manifest(
            authority_tampered,
            "mysql8",
            lambda value: value["authority"].__setitem__("reports", 5),
        )
        authority_tampered_receipt = root / "receipt-authority-tampered.json"
        expect_failure(
            "authority-attestation-tamper",
            "E_DB_AUTHORITY",
            lambda: aggregate(
                repo_root,
                commit,
                workflow_run,
                attempt,
                authority_run,
                base_jobs,
                authority_tampered,
                authority_tampered_receipt,
            ),
            authority_tampered_receipt,
            results,
        )

        cross_run = cloned("cross-run")
        mutate_manifest(cross_run, "sqlserver2022", lambda value: value["identity"].__setitem__("authority_run_id", "another-run"))
        cross_run_receipt = root / "receipt-cross-run.json"
        expect_failure(
            "cross-run-splice",
            "E_DB_AUTHORITY",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, cross_run, cross_run_receipt),
            cross_run_receipt,
            results,
        )

        linked = cloned("symlink")
        linked_file = linked / artifact_name("sqlite", identity) / PAYLOAD_DIRECTORY / "result.json"
        linked_file.unlink()
        os.symlink("TEST-contract.xml", linked_file)
        linked_receipt = root / "receipt-symlink.json"
        expect_failure(
            "symlink-evidence",
            "E_SYMLINK",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, linked, linked_receipt),
            linked_receipt,
            results,
        )

        traversal = cloned("traversal")
        mutate_manifest(
            traversal,
            "mysql8",
            lambda value: value["evidence_files"][0].__setitem__("path", "payload/../escape.json"),
        )
        traversal_receipt = root / "receipt-traversal.json"
        expect_failure(
            "path-traversal",
            "E_PATH",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, traversal, traversal_receipt),
            traversal_receipt,
            results,
        )

        duplicate_json = cloned("duplicate-json")
        duplicate_manifest = duplicate_json / artifact_name("postgres15", identity) / CELL_MANIFEST
        raw = duplicate_manifest.read_text(encoding="utf-8")
        raw = raw.replace(
            '"kind":"v934-db-cell-artifact"',
            '"kind":"v934-db-cell-artifact","kind":"v934-db-cell-artifact"',
            1,
        )
        duplicate_manifest.write_text(raw, encoding="utf-8")
        duplicate_json_receipt = root / "receipt-duplicate-json.json"
        expect_failure(
            "duplicate-json-key",
            "E_JSON_DUPLICATE",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, duplicate_json, duplicate_json_receipt),
            duplicate_json_receipt,
            results,
        )

        for label, bad_value in (("boolean", True), ("float", 2.0), ("string", "2")):
            bad_numeric = cloned(f"bad-numeric-{label}")
            mutate_manifest(
                bad_numeric,
                "mysql57",
                lambda value, replacement=bad_value: value["identity"].__setitem__("attempt", replacement),
            )
            bad_receipt = root / f"receipt-bad-numeric-{label}.json"
            expect_failure(
                f"bad-numeric-{label}",
                "E_JSON_TYPE",
                lambda tree=bad_numeric, receipt=bad_receipt: aggregate(
                    repo_root, commit, workflow_run, attempt, authority_run, base_jobs, tree, receipt
                ),
                bad_receipt,
                results,
            )

        secret = cloned("secret")
        with (secret / artifact_name("sqlite", identity) / PAYLOAD_DIRECTORY / "result.json").open("ab") as stream:
            stream.write(b"password=hunter2\n")
        secret_receipt = root / "receipt-secret.json"
        expect_failure(
            "secret-material",
            "E_SECRET",
            lambda: aggregate(repo_root, commit, workflow_run, attempt, authority_run, base_jobs, secret, secret_receipt),
            secret_receipt,
            results,
        )

    summary = {
        "schema_version": 1,
        "kind": "v934-ci-contract-negative-result",
        "status": "passed",
        "positive_build_cells": len(DB_KINDS),
        "positive_aggregate": "passed",
        "positive_verify": "passed",
        "passed": len(results),
        "cases": results,
    }
    temporary = Path(tempfile.mkdtemp(prefix=f".{output_dir.name}.", suffix=".tmp", dir=output_parent))
    try:
        write_exclusive_file(temporary / "negative-result.json", canonical_json(summary))
        fsync_directory(temporary)
        publish_new_directory(temporary, output_dir, output_parent)
    except BaseException:
        if path_exists(temporary):
            shutil.rmtree(temporary)
        raise
    return {
        "command": "negative",
        "passed": len(results),
        "result_sha256": sha256_bytes(canonical_json(summary)),
        "status": "passed",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    build = commands.add_parser("build-cell", help="build one bound database-cell artifact")
    build.add_argument("--repo-root", type=Path, required=True)
    build.add_argument("--db-kind", required=True)
    build.add_argument("--commit-sha", required=True)
    build.add_argument("--workflow-run-id", required=True)
    build.add_argument("--attempt", required=True)
    build.add_argument("--authority-run-id", required=True)
    build.add_argument("--evidence-dir", type=Path, required=True)
    build.add_argument("--output-dir", type=Path, required=True)

    aggregate_parser = commands.add_parser("aggregate", help="validate all required jobs and exact five-cell artifacts")
    aggregate_parser.add_argument("--repo-root", type=Path, required=True)
    aggregate_parser.add_argument("--commit-sha", required=True)
    aggregate_parser.add_argument("--workflow-run-id", required=True)
    aggregate_parser.add_argument("--attempt", required=True)
    aggregate_parser.add_argument("--authority-run-id", required=True)
    aggregate_parser.add_argument("--job-states", type=Path, required=True)
    aggregate_parser.add_argument("--artifacts-dir", type=Path, required=True)
    aggregate_parser.add_argument("--output", type=Path, required=True)

    verify_parser = commands.add_parser("verify", help="replay an aggregate receipt against downloaded artifacts")
    verify_parser.add_argument("--repo-root", type=Path, required=True)
    verify_parser.add_argument("--receipt", type=Path, required=True)
    verify_parser.add_argument("--artifacts-dir", type=Path, required=True)

    release_assets_parser = commands.add_parser(
        "verify-release-assets", help="verify the exact same-tested release asset set"
    )
    release_assets_parser.add_argument("--assets-dir", type=Path, required=True)
    release_assets_parser.add_argument("--authority-run-id", required=True)
    release_assets_parser.add_argument("--commit-sha", required=True)
    release_assets_parser.add_argument("--workflow-run-id", required=True)
    release_assets_parser.add_argument("--attempt", required=True)
    release_assets_parser.add_argument("--gate-mode", required=True)
    release_assets_parser.add_argument("--expected-jar-sha256", required=True)

    extract_artifact_parser = commands.add_parser(
        "extract-verified-artifact",
        help="verify an exact GitHub artifact ZIP digest and safely extract its release files",
    )
    extract_artifact_parser.add_argument("--repo-root", type=Path, required=True)
    extract_artifact_parser.add_argument("--archive", type=Path, required=True)
    extract_artifact_parser.add_argument("--artifact-id", required=True)
    extract_artifact_parser.add_argument("--artifact-digest", required=True)
    extract_artifact_parser.add_argument("--destination", type=Path, required=True)

    resolve_main_parser = commands.add_parser(
        "resolve-main-authority-api",
        help="resolve one exact successful main-push authority run from a GitHub API response",
    )
    resolve_main_parser.add_argument("--repo-root", type=Path, required=True)
    resolve_main_parser.add_argument("--response", type=Path, required=True)
    resolve_main_parser.add_argument("--repository", required=True)
    resolve_main_parser.add_argument("--commit-sha", required=True)

    verify_main_parser = commands.add_parser(
        "verify-main-authority-api",
        help="verify exact main authority run, job, and artifact GitHub API responses",
    )
    verify_main_parser.add_argument("--repo-root", type=Path, required=True)
    verify_main_parser.add_argument("--run-response", type=Path, required=True)
    verify_main_parser.add_argument("--jobs-response", type=Path, required=True)
    verify_main_parser.add_argument("--artifacts-response", type=Path, required=True)
    verify_main_parser.add_argument("--repository", required=True)
    verify_main_parser.add_argument("--commit-sha", required=True)
    verify_main_parser.add_argument("--workflow-run-id", required=True)
    verify_main_parser.add_argument("--attempt", required=True)

    release_target_parser = commands.add_parser(
        "validate-release-target", help="validate release event/tag/commit/version identity"
    )
    release_target_parser.add_argument("--event-name", required=True)
    release_target_parser.add_argument("--github-ref", required=True)
    release_target_parser.add_argument("--ref-name", required=True)
    release_target_parser.add_argument("--github-sha", required=True)
    release_target_parser.add_argument("--ref-commit", required=True)
    release_target_parser.add_argument("--requested-version", default="")

    workflows_parser = commands.add_parser(
        "validate-workflows", help="validate stable authority and legacy diagnostic workflow roles"
    )
    workflows_parser.add_argument("--repo-root", type=Path, required=True)

    negative_parser = commands.add_parser("negative", help="run focused fail-closed mutation checks")
    negative_parser.add_argument("--repo-root", type=Path, required=True)
    negative_parser.add_argument("--output-dir", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "build-cell":
            result = build_cell(
                args.repo_root,
                args.db_kind,
                args.commit_sha,
                args.workflow_run_id,
                args.attempt,
                args.authority_run_id,
                args.evidence_dir,
                args.output_dir,
            )
        elif args.command == "aggregate":
            result = aggregate(
                args.repo_root,
                args.commit_sha,
                args.workflow_run_id,
                args.attempt,
                args.authority_run_id,
                args.job_states,
                args.artifacts_dir,
                args.output,
            )
        elif args.command == "verify":
            result = verify_command(args.repo_root, args.receipt, args.artifacts_dir)
        elif args.command == "verify-release-assets":
            result = verify_release_assets(
                args.assets_dir,
                args.authority_run_id,
                args.commit_sha,
                args.workflow_run_id,
                args.attempt,
                args.gate_mode,
                args.expected_jar_sha256,
            )
        elif args.command == "extract-verified-artifact":
            result = extract_verified_artifact(
                args.repo_root,
                args.archive,
                args.artifact_id,
                args.artifact_digest,
                args.destination,
            )
        elif args.command == "resolve-main-authority-api":
            result = resolve_main_authority_api(
                args.repo_root,
                args.response,
                args.repository,
                args.commit_sha,
            )
        elif args.command == "verify-main-authority-api":
            result = verify_main_authority_api(
                args.repo_root,
                args.run_response,
                args.jobs_response,
                args.artifacts_response,
                args.repository,
                args.commit_sha,
                args.workflow_run_id,
                args.attempt,
            )
        elif args.command == "validate-release-target":
            result = {
                "command": "validate-release-target",
                **release_target(
                    args.event_name,
                    args.github_ref,
                    args.ref_name,
                    args.github_sha,
                    args.ref_commit,
                    args.requested_version,
                ),
            }
        elif args.command == "validate-workflows":
            result = validate_workflow_contract(args.repo_root)
        elif args.command == "negative":
            result = negative_matrix(args.repo_root, args.output_dir)
        else:
            reject("E_ARGUMENT", f"unsupported command: {args.command}")
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
        return 0
    except ContractError as error:
        print(f"[v934-ci-contract] ERROR {error.code}: {error}", file=sys.stderr)
        return 1
    except OSError as error:
        print(f"[v934-ci-contract] ERROR E_RUNTIME: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
