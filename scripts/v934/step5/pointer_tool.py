#!/usr/bin/env python3
"""Publish, verify, and finally promote transactional v9.3.4 pointers."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import tempfile
from typing import Any, Callable, Sequence
import zipfile


HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")
RUN_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
ENV_KEY = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
MAX_ENV_BYTES = 2 * 1024 * 1024
CONTRACT_PATH = Path(__file__).with_name("final-promotion-contract.json")
VERSION = "9.3.4"
FINAL_POINTER = "final-authority-run.env"
AUTHORITY_CANDIDATE = "authority-candidate-run.env"
STEP7_DIRECTORY = "step7/runs/{authority_run_id}"
STEP7_FILES = (
    "branch-protection-api.json",
    "branch-protection-receipt.json",
    "main-aggregate-receipt.json",
    "main-artifacts-api.json",
    "main-ci-receipt.json",
    "main-jobs-api.json",
    "main-required-artifact-transport.json",
    "main-required-artifact.zip",
    "main-run-api.json",
    "portable-replay.json",
    "pr-aggregate-receipt.json",
    "pr-api.json",
    "pr-artifacts-api.json",
    "pr-ci-receipt.json",
    "pr-jobs-api.json",
    "pr-required-artifact-transport.json",
    "pr-required-artifact.zip",
    "pr-run-api.json",
    "release-artifacts-api.json",
    "release-dry-run-artifact-transport.json",
    "release-dry-run-artifact.zip",
    "release-dry-run-receipt.json",
    "release-jobs-api.json",
    "release-platform-receipt.json",
    "release-run-api.json",
    "version-coverage-audit.md",
    "version-implementation-quality.md",
    "version-signoff.md",
)
AGGREGATOR_ID = "required-aggregator"
AGGREGATOR_NAME = "9.3.4 Test & Release Authority"
CI_WORKFLOW = ".github/workflows/test-ci-evidence-chain.yml"
DOCKER_IMAGE_ID = re.compile(r"sha256:[0-9a-f]{64}")
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
RUNTIME_BASE_FIELDS = (
    "tag_reference",
    "pinned_reference",
    "index_digest",
    "manifest_digest",
    "config_digest",
    "platform",
)
RUNTIME_BASE_PLATFORM_FIELDS = ("os", "architecture")
REPOSITORY = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
FINAL_FIELDS = (
    "schema_version",
    "kind",
    "status",
    "version",
    "authority_run_id",
    "git_head",
    "source_sha256",
    "repository_origin",
    "tooling_manifest_sha256",
    "promotion_tool_sha256",
    "promotion_contract_sha256",
    "authority_candidate_sha256",
    "summary_sha256",
    "archive_sha256",
    "archive_manifest_sha256",
    "archive_digest_sha256",
    "launcher_jar_sha256",
    "package_manifest_sha256",
    "image_manifest_sha256",
    "runtime_base_tag_reference",
    "runtime_base_pinned_reference",
    "runtime_base_index_digest",
    "runtime_base_manifest_digest",
    "runtime_base_config_digest",
    "runtime_base_os",
    "runtime_base_architecture",
    "portable_replay_sha256",
    "pr_ci_receipt_sha256",
    "pr_ci_commit_sha",
    "pr_ci_platform_head_sha",
    "pr_number",
    "pr_ci_workflow_run_id",
    "main_ci_receipt_sha256",
    "main_ci_workflow_run_id",
    "branch_protection_receipt_sha256",
    "release_dry_run_receipt_sha256",
    "release_platform_receipt_sha256",
    "release_workflow_run_id",
    "quality_review_sha256",
    "coverage_audit_sha256",
    "version_signoff_sha256",
)
REPOSITORY_FULL_NAME = "foggy-projects/foggy-data-mcp-bridge"
CANONICAL_ORIGINS = (
    "git@github.com:foggy-projects/foggy-data-mcp-bridge.git",
    "https://github.com/foggy-projects/foggy-data-mcp-bridge",
    "https://github.com/foggy-projects/foggy-data-mcp-bridge.git",
)
TOOLING_MANIFEST = "scripts/v934/step5/SHA256SUMS"
FROZEN_TOOLING_PATHS = (
    "foggy-mcp-launcher/Dockerfile.release",
    "scripts/v934/step5/final-promotion-contract.json",
    "scripts/v934/step5/pointer_tool.py",
    "scripts/v934/step5/portable_replay_tool.py",
    "scripts/v934/step5/release-artifact-contract.json",
    "scripts/v934/step5/release_artifact_tool.py",
    "scripts/v934/step5/release_package_tool.py",
    "scripts/verify-v934-release-gate.sh",
)
AUTHORITY_ARTIFACTS = {
    "summary": "summary.env",
    "archive": "bundle/v934-release-evidence.tar.gz",
    "archive_manifest": "bundle/v934-release-evidence.archive.json",
    "archive_digest": "bundle/v934-release-evidence.tar.gz.sha256",
    "jar": "package/app.jar",
    "package_manifest": "package/package-manifest.json",
    "image_manifest": "package/image-manifest.json",
}
PORTABLE_TOP_FIELDS = (
    "schema_version",
    "kind",
    "status",
    "run_id",
    "release_mode",
    "git_head",
    "source_sha256",
    "fixture_run_id",
    "input",
    "contract_freeze",
    "fixture_manifest",
    "package",
    "materialized",
    "subprocesses",
    "step4",
)
CI_RECEIPT_FIELDS = (
    "schema_version",
    "kind",
    "status",
    "version",
    "repository",
    "event_name",
    "workflow_file",
    "workflow_run_id",
    "attempt",
    "commit_sha",
    "platform_head_sha",
    "authority_run_id",
    "gate_mode",
    "pull_request_number",
    "aggregator",
    "aggregate_receipt_sha256",
    "required_receipt_artifact",
    "required_artifact_transport_sha256",
    "run_api_sha256",
    "jobs_api_sha256",
    "artifacts_api_sha256",
    "pull_request_api_sha256",
)
BRANCH_RECEIPT_FIELDS = (
    "schema_version",
    "kind",
    "status",
    "version",
    "repository",
    "branch",
    "commit_sha",
    "strict",
    "enforce_admins",
    "required_contexts",
    "github_api_response_sha256",
)
RELEASE_RECEIPT_FIELDS = (
    "schema_version",
    "kind",
    "status",
    "version",
    "commit_sha",
    "gate_mode",
    "release_workflow",
    "consumed_main_authority",
    "tested_assets",
    "dry_run_image",
    "publish_performed",
)
RELEASE_WORKFLOW_FIELDS = ("run_id", "attempt", "event_name")
CONSUMED_MAIN_FIELDS = (
    "workflow_run_id",
    "attempt",
    "authority_run_id",
    "artifact_id",
    "artifact_name",
    "artifact_digest",
    "run_api_sha256",
    "jobs_api_sha256",
    "artifacts_api_sha256",
)
RELEASE_ASSET_FIELDS = (
    "jar_sha256",
    "archive_sha256",
    "archive_manifest_sha256",
    "archive_digest_sha256",
    "package_manifest_sha256",
    "image_manifest_sha256",
    "release_assets_sha256",
    "gate_image_id",
    "gate_base_image",
)
RELEASE_IMAGE_FIELDS = (
    "image_id",
    "embedded_jar_sha256",
    "context_files",
    "base_image",
    "status",
)
PLATFORM_ARTIFACT_FIELDS = ("id", "name", "digest", "expired")
ARTIFACT_TRANSPORT_FIELDS = (
    "schema_version",
    "kind",
    "status",
    "repository",
    "role",
    "workflow_run_id",
    "attempt",
    "artifact",
    "archive",
    "member",
    "entries",
)
ARTIFACT_TRANSPORT_ARCHIVE_FIELDS = ("file", "sha256", "size_bytes")
ARTIFACT_TRANSPORT_MEMBER_FIELDS = ("path", "sha256", "size_bytes")
ARTIFACT_TRANSPORT_ENTRIES_FIELDS = (
    "file_count",
    "directory_count",
    "total_uncompressed_bytes",
    "manifest_sha256",
)
MAX_ARTIFACT_TRANSPORT_ARCHIVE_BYTES = 64 * 1024 * 1024
MAX_ARTIFACT_TRANSPORT_ENTRIES = 256
MAX_ARTIFACT_TRANSPORT_EXTRACTED_BYTES = 64 * 1024 * 1024
MAX_ARTIFACT_TRANSPORT_MEMBER_BYTES = 32 * 1024 * 1024
ARTIFACT_TRANSPORT_SPECS: dict[str, dict[str, Any]] = {
    "pr": {
        "role": "pr-required",
        "archive_file": "pr-required-artifact.zip",
        "receipt_file": "pr-required-artifact-transport.json",
        "member_path": "aggregator/required-aggregate-receipt.json",
        "local_file": "pr-aggregate-receipt.json",
        "artifact_prefix": "v934-required-receipt",
        "exact_files": None,
    },
    "main": {
        "role": "main-required",
        "archive_file": "main-required-artifact.zip",
        "receipt_file": "main-required-artifact-transport.json",
        "member_path": "aggregator/required-aggregate-receipt.json",
        "local_file": "main-aggregate-receipt.json",
        "artifact_prefix": "v934-required-receipt",
        "exact_files": None,
    },
    "release": {
        "role": "release-dry-run",
        "archive_file": "release-dry-run-artifact.zip",
        "receipt_file": "release-dry-run-artifact-transport.json",
        "member_path": "receipt.json",
        "local_file": "release-dry-run-receipt.json",
        "artifact_prefix": "v934-release-dry-run",
        "exact_files": (
            "main-artifacts.json",
            "main-jobs.json",
            "main-run.json",
            "receipt.json",
        ),
    },
}
RELEASE_PLATFORM_FIELDS = (
    "schema_version",
    "kind",
    "status",
    "version",
    "repository",
    "workflow_file",
    "event_name",
    "workflow_run_id",
    "attempt",
    "commit_sha",
    "dry_run_job",
    "dry_run_artifact",
    "dry_run_receipt_sha256",
    "dry_run_artifact_transport_sha256",
    "run_api_sha256",
    "jobs_api_sha256",
    "artifacts_api_sha256",
)
REQUIRED_JOB_NAMES = (
    "Inventory + Unit — single gate producer",
    "SQLite broad integration evidence",
    "Exact five-database artifact collector",
    "External integration evidence",
    "Immutable coverage collector/check",
    "Same-tested JAR and package evidence",
    AGGREGATOR_NAME,
)
REQUIRED_ARTIFACT_PREFIXES = (
    "v934-gate-run",
    "v934-db-sqlite",
    "v934-db-mysql57",
    "v934-db-mysql8",
    "v934-db-postgres15",
    "v934-db-sqlserver2022",
    "v934-db-collector",
    "v934-portable-replay",
    "v934-tested-release-assets",
    "v934-required-receipt",
)
RELEASE_JOB_RESULTS = {
    "Same-tested JAR/archive/image dry run": "success",
    "Publish verified runtime-only image": "skipped",
    "Publish same-tested GitHub release assets": "skipped",
}
REVIEW_COMMON_FIELDS = (
    "schema_version",
    "kind",
    "version",
    "status",
    "decision",
    "authority_run_id",
    "git_head",
    "authority_candidate_sha256",
    "summary_sha256",
)


class PointerError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def reject(code: str, message: str) -> None:
    raise PointerError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def lexists(path: Path) -> bool:
    return os.path.lexists(os.fspath(path))


def absolute(path: Path) -> Path:
    return Path(os.path.abspath(path))


def real_directory(path: Path, label: str) -> Path:
    candidate = absolute(path)
    try:
        observed = os.lstat(candidate)
    except FileNotFoundError:
        reject("E_DIRECTORY", f"missing {label}: {candidate}")
    require(
        stat.S_ISDIR(observed.st_mode) and not stat.S_ISLNK(observed.st_mode),
        "E_DIRECTORY",
        f"{label} is not a real directory: {candidate}",
    )
    try:
        resolved = candidate.resolve(strict=True)
    except OSError as exc:
        reject("E_DIRECTORY", f"cannot resolve {label}: {exc}")
    require(resolved == candidate, "E_SYMLINK", f"{label} has symlinked components")
    return candidate


def regular_bytes(path: Path, label: str, maximum: int = MAX_ENV_BYTES) -> bytes:
    candidate = absolute(path)
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(candidate, flags)
    except FileNotFoundError:
        reject("E_FILE_MISSING", f"missing {label}: {candidate}")
    except OSError as exc:
        reject("E_FILE", f"cannot open {label}: {exc}")
    try:
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not regular")
        require(before.st_size <= maximum, "E_FILE_SIZE", f"{label} exceeds size limit")
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_RACE", f"short read from {label}")
            chunks.append(chunk)
            remaining -= len(chunk)
        require(os.read(descriptor, 1) == b"", "E_FILE_RACE", f"{label} grew")
        after = os.fstat(descriptor)
        current = os.lstat(candidate)
        identity = lambda row: (
            row.st_dev,
            row.st_ino,
            row.st_size,
            row.st_mtime_ns,
            row.st_ctime_ns,
        )
        require(
            identity(before) == identity(after) == identity(current),
            "E_FILE_RACE",
            f"{label} changed while read",
        )
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def sha256_file(path: Path, label: str, maximum: int = 1024 * 1024 * 1024) -> str:
    candidate = absolute(path)
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(candidate, flags)
    except FileNotFoundError:
        reject("E_FILE_MISSING", f"missing {label}: {candidate}")
    except OSError as exc:
        reject("E_FILE", f"cannot open {label}: {exc}")
    try:
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not regular")
        require(before.st_size <= maximum, "E_FILE_SIZE", f"{label} exceeds size limit")
        digest = hashlib.sha256()
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_RACE", f"short read from {label}")
            digest.update(chunk)
            remaining -= len(chunk)
        require(os.read(descriptor, 1) == b"", "E_FILE_RACE", f"{label} grew")
        after = os.fstat(descriptor)
        current = os.lstat(candidate)
        identity = lambda row: (
            row.st_dev,
            row.st_ino,
            row.st_size,
            row.st_mtime_ns,
            row.st_ctime_ns,
        )
        require(
            identity(before) == identity(after) == identity(current),
            "E_FILE_RACE",
            f"{label} changed while hashed",
        )
        return digest.hexdigest()
    finally:
        os.close(descriptor)


def parse_env(path: Path, label: str) -> dict[str, str]:
    raw = regular_bytes(path, label)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        reject("E_ENV", f"{label} is not UTF-8: {exc}")
    require(text.endswith("\n"), "E_ENV", f"{label} is not newline terminated")
    values: dict[str, str] = {}
    for number, line in enumerate(text.splitlines(), 1):
        require(bool(line) and "=" in line, "E_ENV", f"malformed {label} row {number}")
        key, value = line.split("=", 1)
        require(ENV_KEY.fullmatch(key) is not None, "E_ENV", f"invalid {label} key")
        require(key not in values, "E_ENV_DUPLICATE", f"duplicate {label} key: {key}")
        require(not any(ord(character) < 32 or ord(character) == 127 for character in value), "E_ENV", f"control character in {label}")
        values[key] = value
    return values


def duplicate_rejecting_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        require(type(key) is str, "E_JSON_TYPE", "JSON object key is not a string")
        require(key not in result, "E_JSON_DUPLICATE", f"duplicate JSON key: {key}")
        result[key] = value
    return result


def parse_json_bytes(raw: bytes, label: str) -> dict[str, Any]:
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        reject("E_JSON", f"{label} is not UTF-8: {exc}")
    try:
        value = json.loads(
            text,
            object_pairs_hook=duplicate_rejecting_object,
            parse_constant=lambda token: reject(
                "E_JSON_TYPE", f"non-finite JSON number in {label}: {token}"
            ),
        )
    except PointerError:
        raise
    except (json.JSONDecodeError, ValueError) as exc:
        reject("E_JSON", f"cannot parse {label}: {exc}")
    require(type(value) is dict, "E_JSON_TYPE", f"{label} root must be an object")
    return value


def parse_json(path: Path, label: str, maximum: int = 64 * 1024 * 1024) -> tuple[dict[str, Any], bytes]:
    raw = regular_bytes(path, label, maximum)
    return parse_json_bytes(raw, label), raw


def exact_keys(value: Any, expected: set[str] | tuple[str, ...], code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict, "E_JSON_TYPE", f"{label} must be an object")
    expected_set = set(expected)
    require(set(value) == expected_set, code, f"{label} keys differ")
    return value


def runtime_base_identity() -> dict[str, Any]:
    return {
        "tag_reference": RUNTIME_BASE_TAG_REFERENCE,
        "pinned_reference": RUNTIME_BASE_PINNED_REFERENCE,
        "index_digest": RUNTIME_BASE_INDEX_DIGEST,
        "manifest_digest": RUNTIME_BASE_MANIFEST_DIGEST,
        "config_digest": RUNTIME_BASE_CONFIG_DIGEST,
        "platform": dict(RUNTIME_BASE_PLATFORM),
    }


def artifact_transport_contract() -> dict[str, Any]:
    roles: dict[str, dict[str, Any]] = {}
    for prefix, spec in ARTIFACT_TRANSPORT_SPECS.items():
        exact_files = spec["exact_files"]
        roles[prefix] = {
            "role": spec["role"],
            "archive_file": spec["archive_file"],
            "receipt_file": spec["receipt_file"],
            "member_path": spec["member_path"],
            "local_file": spec["local_file"],
            "artifact_prefix": spec["artifact_prefix"],
            "exact_files": None if exact_files is None else list(exact_files),
        }
    return {
        "schema_version": 1,
        "kind": "v934-rest-artifact-transport",
        "receipt_fields": list(ARTIFACT_TRANSPORT_FIELDS),
        "artifact_fields": list(PLATFORM_ARTIFACT_FIELDS),
        "archive_fields": list(ARTIFACT_TRANSPORT_ARCHIVE_FIELDS),
        "member_fields": list(ARTIFACT_TRANSPORT_MEMBER_FIELDS),
        "entries_fields": list(ARTIFACT_TRANSPORT_ENTRIES_FIELDS),
        "framing": "unique-eocd-at-eof-no-comment-no-zip64-exact-central-and-contiguous-local-records-no-prefix-trailing-concatenation",
        "roles": roles,
        "limits": {
            "archive_bytes": MAX_ARTIFACT_TRANSPORT_ARCHIVE_BYTES,
            "entries": MAX_ARTIFACT_TRANSPORT_ENTRIES,
            "extracted_bytes": MAX_ARTIFACT_TRANSPORT_EXTRACTED_BYTES,
            "member_bytes": MAX_ARTIFACT_TRANSPORT_MEMBER_BYTES,
        },
    }


def validate_runtime_base_identity(value: Any, label: str) -> dict[str, Any]:
    row = exact_keys(value, RUNTIME_BASE_FIELDS, "E_BASE_IMAGE", label)
    exact_keys(
        row["platform"],
        RUNTIME_BASE_PLATFORM_FIELDS,
        "E_BASE_IMAGE",
        f"{label} platform",
    )
    require(
        row == runtime_base_identity(),
        "E_BASE_IMAGE",
        f"{label} differs from the frozen linux/amd64 runtime base",
    )
    return row


def exact_string(value: Any, expected: str, code: str, label: str) -> str:
    require(type(value) is str and value == expected, code, f"{label} differs")
    return value


def hex40(value: Any, code: str, label: str) -> str:
    require(type(value) is str and HEX40.fullmatch(value) is not None, code, f"{label} differs")
    return value


def hex64(value: Any, code: str, label: str) -> str:
    require(type(value) is str and HEX64.fullmatch(value) is not None, code, f"{label} differs")
    return value


def positive_integer(value: Any, code: str, label: str) -> int:
    require(type(value) is int and 0 < value <= 2**63 - 1, code, f"{label} is not a positive integer")
    return value


def strict_bool(value: Any, expected: bool, code: str, label: str) -> bool:
    require(type(value) is bool and value is expected, code, f"{label} differs")
    return value


def fsync_directory(path: Path) -> None:
    directory = real_directory(path, "pointer parent")
    flags = (
        os.O_RDONLY
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_DIRECTORY", 0)
        | getattr(os, "O_NOFOLLOW", 0)
    )
    descriptor = os.open(directory, flags)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def safe_run_id(value: str) -> str:
    require(RUN_ID.fullmatch(value or "") is not None, "E_RUN_ID", "unsafe run id")
    return value


def pointer_name(mode: str) -> str:
    require(mode in {"rehearsal", "authority"}, "E_MODE", "candidate mode differs")
    return "candidate-run.env" if mode == "rehearsal" else "authority-candidate-run.env"


def validate_candidate_output_name(name: str, mode: str) -> None:
    expected = pointer_name(mode)
    require(name == expected, "E_POINTER_SCOPE", f"candidate pointer must equal {expected}")
    require(name != "final-authority-run.env", "E_POINTER_SCOPE", "candidate cannot target final authority")


def pointer_context(
    target_root: Path,
    run_root: Path,
    run_id: str,
    git_head: str,
    mode: str,
) -> tuple[Path, bytes, dict[str, str]]:
    target = real_directory(target_root, "release target root")
    runs = real_directory(target / "runs", "release runs root")
    run = real_directory(run_root, "release run root")
    safe_run_id(run_id)
    require(run == runs / run_id, "E_RUN_ROOT", "release run root identity differs")
    require(HEX40.fullmatch(git_head or "") is not None, "E_GIT", "Git HEAD differs")
    name = pointer_name(mode)
    validate_candidate_output_name(name, mode)
    output = target / name

    summary_path = run / "summary.env"
    summary = parse_env(summary_path, "release summary")
    expected_summary = {
        "run_id": run_id,
        "mode": mode,
        "git_head": git_head,
        "source_before_sha256": summary.get("source_before_sha256", ""),
        "source_after_sha256": summary.get("source_after_sha256", ""),
        "step4_run_id": run_id,
        "launcher_jar_sha256": summary.get("launcher_jar_sha256", ""),
        "package_manifest_sha256": summary.get("package_manifest_sha256", ""),
        "image_manifest_sha256": summary.get("image_manifest_sha256", ""),
        "archive_sha256": summary.get("archive_sha256", ""),
        "archive_manifest_sha256": summary.get("archive_manifest_sha256", ""),
        "archive_digest_sha256": summary.get("archive_digest_sha256", ""),
        "portable_byte_verify": "passed",
        "portable_semantic_replay": "required-downstream",
        "final_authority_pointer_updated": "false",
        "status": "candidate-passed",
    }
    require(summary == expected_summary, "E_SUMMARY", "release summary identity/state differs")
    for key in (
        "source_before_sha256",
        "source_after_sha256",
        "launcher_jar_sha256",
        "package_manifest_sha256",
        "image_manifest_sha256",
        "archive_sha256",
        "archive_manifest_sha256",
        "archive_digest_sha256",
    ):
        require(HEX64.fullmatch(summary[key]) is not None, "E_SUMMARY", f"summary {key} differs")
    require(
        summary["source_before_sha256"] == summary["source_after_sha256"],
        "E_SUMMARY",
        "release source drift is recorded",
    )

    archive = run / "bundle/v934-release-evidence.tar.gz"
    jar = run / "package/app.jar"
    require(sha256_file(archive, "release archive") == summary["archive_sha256"], "E_ARCHIVE", "archive digest differs from summary")
    require(sha256_file(jar, "tested Launcher JAR") == summary["launcher_jar_sha256"], "E_JAR", "JAR digest differs from summary")
    summary_sha256 = sha256_file(summary_path, "release summary")
    payload_values = {
        "run_id": run_id,
        "git_head": git_head,
        "mode": mode,
        "summary_sha256": summary_sha256,
        "archive_sha256": summary["archive_sha256"],
        "launcher_jar_sha256": summary["launcher_jar_sha256"],
        "status": "passed",
    }
    payload = "".join(f"{key}={value}\n" for key, value in payload_values.items()).encode("ascii")
    return output, payload, payload_values


def same_regular_bytes(path: Path, payload: bytes) -> bool:
    try:
        return regular_bytes(path, "candidate pointer") == payload
    except PointerError:
        return False


def atomic_replace_bytes(
    output: Path,
    payload: bytes,
    *,
    failpoint: str | None = None,
) -> None:
    parent = real_directory(output.parent, "pointer parent")
    require(output.parent == parent, "E_POINTER_PATH", "pointer parent is non-canonical")
    require(output.name not in {"", ".", ".."}, "E_POINTER_PATH", "pointer name differs")
    if lexists(output):
        observed = os.lstat(output)
        require(
            stat.S_ISREG(observed.st_mode) and not stat.S_ISLNK(observed.st_mode),
            "E_POINTER_TYPE",
            "existing pointer is not a regular file",
        )

    descriptor = -1
    temporary: Path | None = None
    backup: Path | None = None
    previous_existed = lexists(output)
    committed = False
    try:
        if previous_existed:
            backup_fd, backup_name = tempfile.mkstemp(
                prefix=f".{output.name}.previous.", suffix=".tmp", dir=parent
            )
            os.close(backup_fd)
            backup = Path(backup_name)
            backup.unlink()
            try:
                os.link(output, backup, follow_symlinks=False)
            except OSError as exc:
                reject("E_POINTER_BACKUP", f"cannot preserve previous pointer: {exc}")
            require(
                os.lstat(output).st_ino == os.lstat(backup).st_ino,
                "E_POINTER_BACKUP",
                "previous pointer backup identity differs",
            )
            fsync_directory(parent)

        descriptor, temporary_name = tempfile.mkstemp(
            prefix=f".{output.name}.new.", suffix=".tmp", dir=parent
        )
        temporary = Path(temporary_name)
        os.fchmod(descriptor, 0o644)
        view = memoryview(payload)
        while view:
            written = os.write(descriptor, view)
            require(written > 0, "E_POINTER_WRITE", "short pointer write")
            view = view[written:]
        os.fsync(descriptor)
        os.close(descriptor)
        descriptor = -1
        if failpoint == "before-replace":
            reject("E_POINTER_INJECTED", "injected pre-commit publication failure")
        os.replace(temporary, output)
        temporary = None
        try:
            if failpoint == "after-replace-before-fsync":
                raise OSError("injected post-replace directory fsync failure")
            fsync_directory(parent)
        except OSError as commit_error:
            try:
                if previous_existed:
                    assert backup is not None
                    os.replace(backup, output)
                    backup = None
                else:
                    os.unlink(output)
                fsync_directory(parent)
            except OSError as rollback_error:
                # If rollback cannot complete but the exact new pointer can be
                # durably committed on retry, publication is successful and
                # must never be mislabeled as a failed run.
                if same_regular_bytes(output, payload):
                    try:
                        fsync_directory(parent)
                    except OSError:
                        reject(
                            "E_POINTER_CATASTROPHIC",
                            f"pointer commit and rollback both failed: {commit_error}; {rollback_error}",
                        )
                    committed = True
                    return
                reject(
                    "E_POINTER_CATASTROPHIC",
                    f"pointer commit and rollback both failed: {commit_error}; {rollback_error}",
                )
            reject("E_POINTER_ROLLED_BACK", f"pointer publication rolled back: {commit_error}")
        committed = True
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if temporary is not None and lexists(temporary):
            try:
                temporary.unlink()
            except OSError:
                pass
        if backup is not None and lexists(backup):
            try:
                backup.unlink()
                if committed:
                    try:
                        fsync_directory(parent)
                    except OSError:
                        # The new pointer was already durably committed; a
                        # stale private backup is cleanup residue, not a false
                        # publication failure.
                        pass
            except OSError:
                pass


def load_promotion_contract() -> tuple[dict[str, Any], str]:
    contract, raw = parse_json(CONTRACT_PATH, "final promotion contract")
    exact_keys(
        contract,
        {
            "schema_version",
            "kind",
            "version",
            "authority_candidate_pointer",
            "final_pointer",
            "evidence_directory",
            "evidence_files",
            "authority_artifacts",
            "runtime_base_image",
            "repository",
            "portable_replay",
            "artifact_transport",
            "ci",
            "branch_protection",
            "release_dry_run",
            "release_platform",
            "review_chain",
            "final_pointer_fields",
        },
        "E_CONTRACT",
        "final promotion contract",
    )
    require(
        type(contract["schema_version"]) is int
        and contract["schema_version"] == 1
        and contract["kind"] == "v934-final-promotion-contract"
        and contract["version"] == VERSION
        and contract["authority_candidate_pointer"] == AUTHORITY_CANDIDATE
        and contract["final_pointer"] == FINAL_POINTER
        and contract["evidence_directory"] == STEP7_DIRECTORY
        and contract["evidence_files"] == list(STEP7_FILES)
        and contract["final_pointer_fields"] == list(FINAL_FIELDS),
        "E_CONTRACT",
        "final promotion contract identity/path schema differs",
    )
    require(contract["authority_artifacts"] == AUTHORITY_ARTIFACTS, "E_CONTRACT", "authority artifact paths differ")
    require(
        contract["runtime_base_image"] == runtime_base_identity(),
        "E_CONTRACT",
        "final promotion runtime base image differs",
    )
    require(
        contract["repository"]
        == {
            "full_name": REPOSITORY_FULL_NAME,
            "branch": "main",
            "origin_urls": list(CANONICAL_ORIGINS),
            "tooling_manifest": TOOLING_MANIFEST,
            "frozen_tooling_paths": list(FROZEN_TOOLING_PATHS),
        },
        "E_CONTRACT",
        "final promotion repository contract differs",
    )
    require(
        contract["artifact_transport"] == artifact_transport_contract(),
        "E_CONTRACT",
        "final promotion REST artifact transport contract differs",
    )
    portable = exact_keys(
        contract["portable_replay"],
        {"schema_version", "kind", "top_level_fields", "package_fields", "package_file_fields"},
        "E_CONTRACT",
        "portable replay contract",
    )
    require(
        portable
        == {
            "schema_version": 1,
            "kind": "v934-portable-release-replay",
            "top_level_fields": list(PORTABLE_TOP_FIELDS),
            "package_fields": ["files", "manifest_kind", "tested_classes"],
            "package_file_fields": ["sha256", "size"],
        },
        "E_CONTRACT",
        "portable replay contract differs",
    )
    ci = exact_keys(
        contract["ci"],
        {
            "workflow_file",
            "aggregator_id",
            "aggregator_name",
            "pr_event",
            "pr_gate_mode",
            "main_event",
            "main_gate_mode",
            "receipt_fields",
            "aggregator_fields",
            "artifact_fields",
            "required_job_names",
            "required_artifact_prefixes",
        },
        "E_CONTRACT",
        "final promotion CI contract",
    )
    require(
        ci
        == {
            "workflow_file": CI_WORKFLOW,
            "aggregator_id": AGGREGATOR_ID,
            "aggregator_name": AGGREGATOR_NAME,
            "pr_event": "pull_request",
            "pr_gate_mode": "rehearsal",
            "main_event": "push",
            "main_gate_mode": "authority",
            "receipt_fields": list(CI_RECEIPT_FIELDS),
            "aggregator_fields": ["id", "name", "job_id", "conclusion"],
            "artifact_fields": list(PLATFORM_ARTIFACT_FIELDS),
            "required_job_names": list(REQUIRED_JOB_NAMES),
            "required_artifact_prefixes": list(REQUIRED_ARTIFACT_PREFIXES),
        },
        "E_CONTRACT",
        "final promotion CI authority differs",
    )
    branch = exact_keys(
        contract["branch_protection"],
        {"branch", "strict", "receipt_fields", "required_contexts"},
        "E_CONTRACT",
        "branch protection contract",
    )
    require(
        branch
        == {
            "branch": "main",
            "strict": True,
            "receipt_fields": list(BRANCH_RECEIPT_FIELDS),
            "required_contexts": [AGGREGATOR_NAME],
        },
        "E_CONTRACT",
        "branch protection contract differs",
    )
    release = exact_keys(
        contract["release_dry_run"],
        {
            "schema_version",
            "kind",
            "gate_mode",
            "event_name",
            "publish_performed",
            "receipt_fields",
            "release_workflow_fields",
            "consumed_main_authority_fields",
            "tested_asset_fields",
            "dry_run_image_fields",
            "base_image_fields",
            "base_image_platform_fields",
            "context_files",
        },
        "E_CONTRACT",
        "release dry-run contract",
    )
    require(
        release
        == {
            "schema_version": 3,
            "kind": "v934-release-dry-run",
            "gate_mode": "authority",
            "event_name": "workflow_dispatch",
            "publish_performed": False,
            "receipt_fields": list(RELEASE_RECEIPT_FIELDS),
            "release_workflow_fields": list(RELEASE_WORKFLOW_FIELDS),
            "consumed_main_authority_fields": list(CONSUMED_MAIN_FIELDS),
            "tested_asset_fields": list(RELEASE_ASSET_FIELDS),
            "dry_run_image_fields": list(RELEASE_IMAGE_FIELDS),
            "base_image_fields": list(RUNTIME_BASE_FIELDS),
            "base_image_platform_fields": list(RUNTIME_BASE_PLATFORM_FIELDS),
            "context_files": ["Dockerfile", "app.jar"],
        },
        "E_CONTRACT",
        "release dry-run contract differs",
    )
    require(
        contract["release_platform"]
        == {
            "workflow_file": ".github/workflows/release.yml",
            "job_name": "Same-tested JAR/archive/image dry run",
            "job_results": RELEASE_JOB_RESULTS,
            "receipt_fields": list(RELEASE_PLATFORM_FIELDS),
            "job_fields": ["id", "name", "conclusion"],
            "artifact_fields": list(PLATFORM_ARTIFACT_FIELDS),
        },
        "E_CONTRACT",
        "release platform contract differs",
    )
    require(
        contract["review_chain"]
        == [
            {
                "file": "version-implementation-quality.md",
                "kind": "v934-version-implementation-quality",
                "status": "reviewed",
                "decision": "ready-for-coverage-audit",
                "frontmatter_fields": list(REVIEW_COMMON_FIELDS),
            },
            {
                "file": "version-coverage-audit.md",
                "kind": "v934-version-coverage-audit",
                "status": "reviewed",
                "decision": "ready-for-signoff",
                "frontmatter_fields": [*REVIEW_COMMON_FIELDS, "quality_sha256"],
            },
            {
                "file": "version-signoff.md",
                "kind": "v934-version-signoff",
                "status": "signed-off",
                "decision": "accepted",
                "frontmatter_fields": [*REVIEW_COMMON_FIELDS, "quality_sha256", "coverage_sha256"],
            },
        ],
        "E_CONTRACT",
        "review chain contract differs",
    )
    return contract, hashlib.sha256(raw).hexdigest()


def reject_ambient_git_overrides() -> None:
    ambient = {name for name in os.environ if name.startswith("GIT_")}
    require(ambient <= {"GIT_PAGER"}, "E_GIT_ENV", f"ambient Git override differs: {sorted(ambient)}")


def process_environment() -> dict[str, str]:
    return {
        name: os.environ[name]
        for name in ("PATH", "SYSTEMROOT", "TMPDIR", "TMP", "TEMP")
        if name in os.environ
    }


def git_environment() -> dict[str, str]:
    reject_ambient_git_overrides()
    environment = process_environment()
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_ATTR_NOSYSTEM": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "GIT_SSH_COMMAND": "ssh -F /dev/null -oBatchMode=yes -oProxyCommand=none -oProxyJump=none",
            "LC_ALL": "C",
            "LANG": "C",
        }
    )
    return environment


def git_audit_environment(home_override: Path | None = None, *, disable_system: bool = False) -> dict[str, str]:
    reject_ambient_git_overrides()
    environment = process_environment()
    home = absolute(home_override) if home_override is not None else Path(os.environ.get("HOME", str(Path.home())))
    environment.update(
        {
            "HOME": str(home),
            "GIT_ATTR_NOSYSTEM": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "LC_ALL": "C",
            "LANG": "C",
        }
    )
    if home_override is None and "XDG_CONFIG_HOME" in os.environ:
        environment["XDG_CONFIG_HOME"] = os.environ["XDG_CONFIG_HOME"]
    if disable_system:
        environment["GIT_CONFIG_NOSYSTEM"] = "1"
    return environment


def run_process(
    command: Sequence[str],
    cwd: Path,
    code: str,
    label: str,
    *,
    allow_one: bool = False,
    environment: dict[str, str] | None = None,
) -> subprocess.CompletedProcess[bytes]:
    try:
        result = subprocess.run(
            list(command),
            cwd=cwd,
            env=git_environment() if environment is None else environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=120,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        reject(code, f"cannot run {label}: {exc}")
    allowed = {0, 1} if allow_one else {0}
    require(result.returncode in allowed, code, f"{label} failed with exit {result.returncode}")
    return result


def git_output(repo: Path, arguments: Sequence[str], code: str, label: str, *, allow_one: bool = False) -> bytes:
    return run_process(
        [
            "git",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-c",
            "core.hooksPath=/dev/null",
            "-c",
            "core.attributesFile=/dev/null",
            "-C",
            str(repo),
            *arguments,
        ],
        repo,
        code,
        label,
        allow_one=allow_one,
    ).stdout


def validate_git_config_names(raw: bytes) -> dict[str, Any]:
    fields = raw.split(b"\0")
    require(fields and fields[-1] == b"", "E_GIT_CONFIG", "Git configuration audit is not NUL terminated")
    fields.pop()
    require(len(fields) % 3 == 0, "E_GIT_CONFIG", "Git configuration audit record shape differs")
    forbidden: list[str] = []
    for index in range(0, len(fields), 3):
        scope_raw, origin_raw, key_raw = fields[index : index + 3]
        try:
            scope = scope_raw.decode("utf-8", errors="strict")
            origin = origin_raw.decode("utf-8", errors="strict")
            key = key_raw.decode("utf-8", errors="strict").lower()
        except UnicodeDecodeError as exc:
            reject("E_GIT_CONFIG", f"Git configuration audit is not UTF-8: {exc}")
        require(scope in {"system", "global", "local", "worktree", "command"} and bool(origin) and bool(key), "E_GIT_CONFIG", "Git configuration audit identity differs")
        url_rewrite = key.startswith("url.") and key.endswith((".insteadof", ".pushinsteadof"))
        origin_transport = key in {
            "remote.origin.uploadpack",
            "remote.origin.receivepack",
            "remote.origin.proxy",
            "remote.origin.pushurl",
            "remote.origin.vcs",
        }
        core_transport = key in {"core.sshcommand", "core.gitproxy"}
        http_proxy = key == "http.proxy" or (key.startswith("http.") and key.endswith(".proxy"))
        if url_rewrite or origin_transport or core_transport or http_proxy:
            forbidden.append(f"{scope}:{origin}:{key}")
    require(not forbidden, "E_GIT_CONFIG", f"effective Git remote transport override differs: {forbidden}")
    return {"entry_count": len(fields) // 3, "sha256": hashlib.sha256(raw).hexdigest()}


def audit_git_configuration(
    repo: Path,
    *,
    home_override: Path | None = None,
    disable_system: bool = False,
) -> dict[str, Any]:
    result = run_process(
        [
            "git",
            "-C",
            str(repo),
            "config",
            "--includes",
            "--show-origin",
            "--show-scope",
            "--null",
            "--name-only",
            "--list",
        ],
        repo,
        "E_GIT_CONFIG",
        "effective Git configuration audit",
        environment=git_audit_environment(home_override, disable_system=disable_system),
    )
    return validate_git_config_names(result.stdout)


def parse_tooling_manifest(repo: Path) -> dict[str, Any]:
    path = repo / TOOLING_MANIFEST
    raw = regular_bytes(path, "Step 5 tooling manifest", 1024 * 1024)
    try:
        text = raw.decode("ascii")
    except UnicodeDecodeError as exc:
        reject("E_TOOLING_MANIFEST", f"Step 5 tooling manifest is not ASCII: {exc}")
    require(text.endswith("\n"), "E_TOOLING_MANIFEST", "Step 5 tooling manifest is not newline terminated")
    rows: dict[str, str] = {}
    order: list[str] = []
    for number, line in enumerate(text.splitlines(), 1):
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9][A-Za-z0-9._/-]*)", line)
        require(match is not None, "E_TOOLING_MANIFEST", f"malformed Step 5 tooling row {number}")
        digest, relative = match.groups()
        require("//" not in relative and "/./" not in relative and ".." not in relative.split("/"), "E_TOOLING_MANIFEST", f"unsafe Step 5 tooling path at row {number}")
        require(relative not in rows, "E_TOOLING_MANIFEST", f"duplicate Step 5 tooling path: {relative}")
        rows[relative] = digest
        order.append(relative)
    require(tuple(order) == FROZEN_TOOLING_PATHS, "E_TOOLING_MANIFEST", "Step 5 tooling manifest exact path/order differs")
    for relative, expected in rows.items():
        observed = sha256_file(repo / relative, f"frozen Step 5 tooling {relative}")
        require(observed == expected, "E_TOOLING_DRIFT", f"frozen Step 5 tooling digest differs: {relative}")
    return {
        "sha256": hashlib.sha256(raw).hexdigest(),
        "promotion_tool_sha256": rows["scripts/v934/step5/pointer_tool.py"],
        "promotion_contract_sha256": rows["scripts/v934/step5/final-promotion-contract.json"],
    }


def validate_repository_binding(
    value: dict[str, Any], git_head: str, source_sha256: str, contract_sha256: str
) -> dict[str, Any]:
    exact_keys(
        value,
        {
            "repository",
            "origin",
            "git_head",
            "local_main",
            "live_main",
            "source_sha256",
            "clean",
            "tooling_manifest_sha256",
            "promotion_tool_sha256",
            "promotion_contract_sha256",
        },
        "E_REPOSITORY",
        "promotion repository binding",
    )
    actual_tool_sha = sha256_file(Path(__file__), "current final promotion tool", 16 * 1024 * 1024)
    actual_contract_sha = sha256_file(CONTRACT_PATH, "current final promotion contract", 4 * 1024 * 1024)
    require(value["repository"] == REPOSITORY_FULL_NAME, "E_ORIGIN", "promotion repository identity differs")
    require(value["origin"] in CANONICAL_ORIGINS, "E_ORIGIN", "promotion canonical origin differs")
    require(type(value["clean"]) is bool and value["clean"] is True, "E_REPO_CLEAN", "promotion repository is dirty")
    require(
        value["git_head"] == git_head
        and value["local_main"] == git_head
        and value["live_main"] == git_head,
        "E_LIVE_MAIN",
        "promotion HEAD/local origin/main/live main identity differs",
    )
    require(value["source_sha256"] == source_sha256, "E_SOURCE_SEAL", "promotion source seal differs from authority")
    hex64(value["tooling_manifest_sha256"], "E_TOOLING_MANIFEST", "Step 5 tooling manifest digest")
    require(
        value["promotion_tool_sha256"] == actual_tool_sha
        and value["promotion_contract_sha256"] == actual_contract_sha
        and value["promotion_contract_sha256"] == contract_sha256,
        "E_TOOLING_DRIFT",
        "promotion tool/contract differs from frozen tooling authority",
    )
    return value


def observe_repository(
    repo_root: Path, git_head: str, source_sha256: str, contract_sha256: str
) -> dict[str, Any]:
    repo = real_directory(repo_root, "promotion repository root")
    audit_git_configuration(repo)
    top = git_output(repo, ["rev-parse", "--show-toplevel"], "E_REPOSITORY", "repository top-level")
    require(top == os.fsencode(str(repo)) + b"\n", "E_REPOSITORY", "promotion repo-root is not the Git top-level")
    remotes = git_output(repo, ["remote"], "E_ORIGIN", "repository remotes")
    require(remotes == b"origin\n", "E_ORIGIN", "promotion repository must have exact origin remote")
    origins = git_output(repo, ["config", "--get-all", "remote.origin.url"], "E_ORIGIN", "origin URL")
    origin_rows = origins.decode("utf-8", errors="strict").splitlines()
    require(len(origin_rows) == 1 and origin_rows[0] in CANONICAL_ORIGINS, "E_ORIGIN", "promotion origin URL is not canonical")
    effective_origins = git_output(repo, ["remote", "get-url", "--all", "origin"], "E_ORIGIN", "effective origin URL")
    require(effective_origins == origins, "E_ORIGIN", "effective origin URL differs from exact configured origin")
    head = git_output(repo, ["rev-parse", "--verify", "HEAD^{commit}"], "E_REPOSITORY", "repository HEAD").decode("ascii").strip()
    local_main = git_output(repo, ["rev-parse", "--verify", "origin/main^{commit}"], "E_LIVE_MAIN", "local origin/main").decode("ascii").strip()
    status = git_output(repo, ["status", "--porcelain=v1", "--untracked-files=normal"], "E_REPO_CLEAN", "repository clean status")
    live = git_output(repo, ["ls-remote", "--exit-code", "origin", "refs/heads/main"], "E_LIVE_MAIN", "live origin/main")
    expected_live = f"{git_head}\trefs/heads/main\n".encode("ascii")
    require(live == expected_live, "E_LIVE_MAIN", "live origin/main differs from final HEAD")
    tooling = parse_tooling_manifest(repo)
    expected_tool = repo / "scripts/v934/step5/pointer_tool.py"
    expected_contract = repo / "scripts/v934/step5/final-promotion-contract.json"
    require(Path(__file__).resolve(strict=True) == expected_tool and CONTRACT_PATH.resolve(strict=True) == expected_contract, "E_TOOLING_DRIFT", "executed promotion tool/contract path is outside repo-root")
    source_tool = repo / "scripts/v934/step4/coverage_tool.py"
    source_result = run_process(
        [sys.executable, str(source_tool), "source-hash", "--repo-root", str(repo)],
        repo,
        "E_SOURCE_SEAL",
        "tracked source seal",
    )
    lines = [line for line in source_result.stdout.splitlines() if line]
    require(len(lines) == 1, "E_SOURCE_SEAL", "tracked source seal output differs")
    source_value = parse_json_bytes(lines[0], "tracked source seal output")
    exact_keys(source_value, {"command", "file_count", "git_head", "sha256", "status"}, "E_SOURCE_SEAL", "tracked source seal output")
    require(
        source_value["command"] == "source-hash"
        and source_value["status"] == "passed"
        and type(source_value["file_count"]) is int
        and source_value["file_count"] > 0,
        "E_SOURCE_SEAL",
        "tracked source seal state differs",
    )
    binding = {
        "repository": REPOSITORY_FULL_NAME,
        "origin": origin_rows[0],
        "git_head": head,
        "local_main": local_main,
        "live_main": git_head if live == expected_live else "",
        "source_sha256": source_value.get("sha256"),
        "clean": status == b"",
        **tooling,
    }
    return validate_repository_binding(binding, git_head, source_sha256, contract_sha256)


def exact_evidence_directory(target: Path, run_id: str) -> Path:
    step7 = real_directory(target / "step7", "Step 7 root")
    runs = real_directory(step7 / "runs", "Step 7 runs root")
    expected = runs / run_id
    evidence = real_directory(expected, "Step 7 evidence directory")
    require(evidence == expected, "E_EVIDENCE_PATH", "Step 7 evidence directory path differs")
    observed: set[str] = set()
    for entry in os.scandir(evidence):
        metadata = os.lstat(entry.path)
        require(not stat.S_ISLNK(metadata.st_mode), "E_SYMLINK", f"symlinked Step 7 evidence: {entry.name}")
        require(stat.S_ISREG(metadata.st_mode), "E_SPECIAL", f"non-regular Step 7 evidence: {entry.name}")
        observed.add(entry.name)
    require(observed == set(STEP7_FILES), "E_EVIDENCE_SET", "Step 7 evidence file set differs")
    return evidence


def validate_release_artifacts(run: Path, summary: dict[str, str]) -> dict[str, Any]:
    bundle = real_directory(run / "bundle", "release bundle directory")
    package = real_directory(run / "package", "release package directory")
    require(bundle == run / "bundle" and package == run / "package", "E_ARTIFACT_PATH", "release artifact directory path differs")
    summary_paths = {
        "archive_sha256": bundle / "v934-release-evidence.tar.gz",
        "archive_manifest_sha256": bundle / "v934-release-evidence.archive.json",
        "archive_digest_sha256": bundle / "v934-release-evidence.tar.gz.sha256",
        "launcher_jar_sha256": package / "app.jar",
        "package_manifest_sha256": package / "package-manifest.json",
        "image_manifest_sha256": package / "image-manifest.json",
    }
    release_asset_paths = {
        "maven_log_sha256": package / "maven-invocations.log",
        "docker_log_sha256": package / "docker-build.log",
        "tested_tree_log_sha256": package / "tested-tree-validation.log",
    }
    paths = {**summary_paths, **release_asset_paths}
    values = {
        name: sha256_file(path, name)
        for name, path in paths.items()
    }
    for name in summary_paths:
        observed = values[name]
        require(summary.get(name) == observed, "E_ARTIFACT_DIGEST", f"release {name} differs from summary")
    digest = regular_bytes(paths["archive_digest_sha256"], "release archive digest", 1024)
    require(
        digest == f"{values['archive_sha256']}  v934-release-evidence.tar.gz\n".encode("ascii"),
        "E_ARCHIVE_DIGEST",
        "release archive digest file differs",
    )

    package_manifest, _ = parse_json(paths["package_manifest_sha256"], "package manifest")
    require(
        package_manifest.get("schema_version") == 4
        and type(package_manifest.get("schema_version")) is int
        and package_manifest.get("kind") == "v934-tested-output-tree-package"
        and package_manifest.get("status") == "passed"
        and package_manifest.get("run_id") == summary["run_id"]
        and package_manifest.get("git_head") == summary["git_head"],
        "E_PACKAGE",
        "package manifest authority identity differs",
    )
    package_jar = package_manifest.get("jar")
    require(
        type(package_jar) is dict
        and package_jar.get("sha256") == values["launcher_jar_sha256"]
        and type(package_jar.get("size")) is int
        and package_jar["size"] > 0,
        "E_PACKAGE_JAR",
        "package manifest JAR binding differs",
    )
    package_image = package_manifest.get("image")
    require(type(package_image) is dict, "E_BASE_IMAGE", "package image binding is absent")
    package_base = validate_runtime_base_identity(
        package_image.get("base_image"), "package runtime base identity"
    )

    image_manifest, _ = parse_json(paths["image_manifest_sha256"], "runtime image manifest")
    require(
        image_manifest.get("schema_version") == 2
        and type(image_manifest.get("schema_version")) is int
        and image_manifest.get("kind") == "v934-runtime-image-receipt"
        and image_manifest.get("status") == "passed"
        and image_manifest.get("run_id") == summary["run_id"]
        and type(image_manifest.get("image_id")) is str
        and DOCKER_IMAGE_ID.fullmatch(image_manifest["image_id"]) is not None,
        "E_IMAGE",
        "runtime image manifest authority identity differs",
    )
    require(
        image_manifest.get("tested_jar")
        == {
            "path": "app.jar",
            "sha256": values["launcher_jar_sha256"],
            "size": package_jar["size"],
        }
        and image_manifest.get("embedded_jar")
        == {
            "path": "/app/app.jar",
            "sha256": values["launcher_jar_sha256"],
            "size": package_jar["size"],
        },
        "E_IMAGE_JAR",
        "runtime image/JAR binding differs",
    )
    image_base = validate_runtime_base_identity(
        image_manifest.get("base_image"), "runtime image manifest base identity"
    )
    require(
        package_base == image_base,
        "E_BASE_IMAGE",
        "package/runtime image base identity splice detected",
    )
    return {
        **values,
        "jar_size": package_jar["size"],
        "image_id": image_manifest["image_id"],
        "base_image": image_base,
    }


def release_assets_preimage(
    authority_run_id: str,
    git_head: str,
    workflow_run_id: int,
    attempt: int,
    artifacts: dict[str, Any],
) -> bytes:
    """Rebuild the exact jq-produced main authority release-assets.json bytes."""

    value = {
        "schema_version": 1,
        "kind": "v934-tested-release-assets",
        "status": "passed",
        "identity": {
            "authority_run_id": authority_run_id,
            "commit_sha": git_head,
            "workflow_run_id": workflow_run_id,
            "attempt": attempt,
            "gate_mode": "authority",
        },
        "files": {
            "package/app.jar": artifacts["launcher_jar_sha256"],
            "v934-release-evidence.tar.gz": artifacts["archive_sha256"],
            "v934-release-evidence.archive.json": artifacts[
                "archive_manifest_sha256"
            ],
            "v934-release-evidence.tar.gz.sha256": artifacts[
                "archive_digest_sha256"
            ],
            "package/package-manifest.json": artifacts[
                "package_manifest_sha256"
            ],
            "package/image-manifest.json": artifacts["image_manifest_sha256"],
            "package/maven-invocations.log": artifacts["maven_log_sha256"],
            "package/docker-build.log": artifacts["docker_log_sha256"],
            "package/tested-tree-validation.log": artifacts[
                "tested_tree_log_sha256"
            ],
        },
    }
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def validate_portable_replay(
    path: Path,
    run_id: str,
    git_head: str,
    source_sha256: str,
    artifacts: dict[str, Any],
) -> dict[str, Any]:
    value, raw = parse_json(path, "portable replay receipt")
    exact_keys(
        value,
        PORTABLE_TOP_FIELDS,
        "E_PORTABLE",
        "portable replay receipt",
    )
    require(
        type(value["schema_version"]) is int
        and value["schema_version"] == 1
        and value["kind"] == "v934-portable-release-replay"
        and value["status"] == "passed"
        and value["release_mode"] == "authority"
        and value["run_id"] == run_id
        and value["git_head"] == git_head
        and value["source_sha256"] == source_sha256,
        "E_PORTABLE_IDENTITY",
        "portable replay authority identity differs",
    )
    package = exact_keys(
        value["package"],
        {"files", "manifest_kind", "tested_classes"},
        "E_PORTABLE",
        "portable replay package",
    )
    require(package["manifest_kind"] == "v934-tested-output-tree-package", "E_PORTABLE", "portable package kind differs")
    files = package["files"]
    require(
        type(files) is dict
        and set(files)
        == {
            "app.jar",
            "docker-build.log",
            "image-manifest.json",
            "maven-invocations.log",
            "package-manifest.json",
            "tested-tree-validation.log",
        },
        "E_PORTABLE",
        "portable package file set differs",
    )
    for name, row in files.items():
        exact_keys(row, {"sha256", "size"}, "E_PORTABLE", f"portable package file {name}")
        hex64(row["sha256"], "E_PORTABLE", f"portable package file {name} digest")
        positive_integer(row["size"], "E_PORTABLE", f"portable package file {name} size")
    require(
        files["app.jar"]
        == {"sha256": artifacts["launcher_jar_sha256"], "size": artifacts["jar_size"]},
        "E_PORTABLE_JAR",
        "portable replay JAR differs from authority JAR",
    )
    require(
        files["package-manifest.json"]["sha256"] == artifacts["package_manifest_sha256"]
        and files["image-manifest.json"]["sha256"] == artifacts["image_manifest_sha256"],
        "E_PORTABLE_PACKAGE",
        "portable replay package/image manifest binding differs",
    )
    for key in ("input", "contract_freeze", "fixture_manifest", "materialized", "subprocesses", "step4"):
        require(type(value[key]) is dict, "E_JSON_TYPE", f"portable replay {key} must be an object")
    return {"sha256": hashlib.sha256(raw).hexdigest()}


def require_fields(value: Any, fields: set[str] | tuple[str, ...], code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict and set(fields) <= set(value), code, f"{label} required fields differ")
    return value


def raw_api(path: Path, label: str) -> tuple[dict[str, Any], str]:
    value, raw = parse_json(path, label, 64 * 1024 * 1024)
    return value, hashlib.sha256(raw).hexdigest()


def validate_run_api(
    path: Path,
    *,
    workflow_run_id: int,
    attempt: int,
    event_name: str,
    platform_head_sha: str,
    workflow_file: str,
    pull_request_number: int | None,
) -> dict[str, Any]:
    value, digest = raw_api(path, f"{event_name} workflow run API response")
    if event_name == "pull_request":
        require(type(pull_request_number) is int and pull_request_number > 0, "E_PR_LINEAGE", "pull request number differs")
        allowed_workflow_paths = {
            workflow_file,
            f"{workflow_file}@refs/pull/{pull_request_number}/merge",
        }
    else:
        allowed_workflow_paths = {
            workflow_file,
            f"{workflow_file}@main",
            f"{workflow_file}@refs/heads/main",
        }
    require_fields(
        value,
        {"id", "run_attempt", "event", "status", "conclusion", "head_sha", "head_branch", "path", "repository", "head_repository", "pull_requests"},
        "E_API_RUN",
        f"{event_name} workflow run API response",
    )
    repository = require_fields(value["repository"], {"full_name"}, "E_API_REPO", "workflow run repository")
    head_repository = require_fields(value["head_repository"], {"full_name"}, "E_API_REPO", "workflow run head repository")
    require(
        repository["full_name"] == REPOSITORY_FULL_NAME
        and head_repository["full_name"] == REPOSITORY_FULL_NAME,
        "E_API_REPO",
        "workflow run repository/head repository differs",
    )
    require(
        value["id"] == workflow_run_id
        and type(value["id"]) is int
        and value["run_attempt"] == attempt
        and type(value["run_attempt"]) is int
        and value["event"] == event_name
        and value["status"] == "completed"
        and value["conclusion"] == "success"
        and value["head_sha"] == platform_head_sha
        and value["path"] in allowed_workflow_paths,
        "E_API_RUN",
        f"{event_name} workflow run API identity/state differs",
    )
    pulls = value["pull_requests"]
    require(type(pulls) is list, "E_API_RUN", "workflow run pull_requests must be an array")
    pull_head_sha: str | None = None
    if event_name == "pull_request":
        require(type(value["head_branch"]) is str and bool(value["head_branch"]), "E_PR_LINEAGE", "PR workflow head branch differs")
        require(len(pulls) <= 1, "E_PR_LINEAGE", "PR workflow run linkage cardinality differs")
        if pulls:
            pull = require_fields(pulls[0], {"number", "head", "base"}, "E_PR_LINEAGE", "workflow run PR linkage")
            head = require_fields(pull["head"], {"sha", "repo"}, "E_PR_LINEAGE", "workflow run PR head")
            base = require_fields(pull["base"], {"ref", "repo"}, "E_PR_LINEAGE", "workflow run PR base")
            head_repo = require_fields(head["repo"], {"url"}, "E_PR_LINEAGE", "workflow run PR head repo")
            base_repo = require_fields(base["repo"], {"url"}, "E_PR_LINEAGE", "workflow run PR base repo")
            canonical_repo_api = f"https://api.github.com/repos/{REPOSITORY_FULL_NAME}"
            require(
                pull["number"] == pull_request_number
                and type(pull["number"]) is int
                and head["sha"] == platform_head_sha
                and head_repo["url"] == canonical_repo_api
                and base["ref"] == "main"
                and base_repo["url"] == canonical_repo_api,
                "E_PR_LINEAGE",
                "workflow run PR head/base linkage differs",
            )
        pull_head_sha = platform_head_sha
    else:
        require(value["head_branch"] == "main", "E_API_RUN", "main workflow run branch linkage differs")
    return {"sha256": digest, "pull_head_sha": pull_head_sha, "head_branch": value["head_branch"]}


def validate_jobs_api(
    path: Path,
    *,
    workflow_run_id: int,
    attempt: int,
    platform_head_sha: str,
    required_names: tuple[str, ...] | None,
    required_success_name: str | None = None,
) -> dict[str, Any]:
    value, digest = raw_api(path, "workflow jobs API response")
    require_fields(value, {"total_count", "jobs"}, "E_API_JOBS", "workflow jobs API response")
    jobs = value["jobs"]
    require(type(jobs) is list and type(value["total_count"]) is int and value["total_count"] == len(jobs), "E_API_JOBS", "workflow jobs cardinality differs")
    ids: set[int] = set()
    names: list[str] = []
    by_name: dict[str, dict[str, Any]] = {}
    for number, row in enumerate(jobs, 1):
        job = require_fields(row, {"id", "run_id", "run_attempt", "name", "status", "conclusion", "head_sha"}, "E_API_JOBS", f"workflow job {number}")
        job_id = positive_integer(job["id"], "E_API_JOBS", f"workflow job {number} id")
        require(job_id not in ids, "E_API_JOBS", "duplicate workflow job id")
        ids.add(job_id)
        require(
            job["run_id"] == workflow_run_id
            and type(job["run_id"]) is int
            and job["run_attempt"] == attempt
            and type(job["run_attempt"]) is int
            and type(job["name"]) is str
            and job["status"] == "completed"
            and job["head_sha"] == platform_head_sha,
            "E_API_JOBS",
            f"workflow job {number} identity/state differs",
        )
        names.append(job["name"])
        require(job["name"] not in by_name, "E_API_JOBS", "duplicate workflow job name")
        by_name[job["name"]] = job
    if required_names is not None:
        require(set(names) == set(required_names) and len(names) == len(required_names) and all(row["conclusion"] == "success" for row in jobs), "E_API_JOBS", "required CI job exact set/result differs")
    if required_success_name is not None:
        require(required_success_name in by_name and by_name[required_success_name]["conclusion"] == "success", "E_RELEASE_JOB", "release dry-run job is not successful")
        require(all(row["conclusion"] in {"success", "skipped"} for row in jobs), "E_RELEASE_JOB", "release workflow contains failed/cancelled job")
    return {"sha256": digest, "jobs": by_name}


def validate_release_job_set(jobs_result: dict[str, Any]) -> None:
    jobs = jobs_result["jobs"]
    require(
        set(jobs) == set(RELEASE_JOB_RESULTS)
        and all(jobs[name]["conclusion"] == conclusion for name, conclusion in RELEASE_JOB_RESULTS.items()),
        "E_RELEASE_JOB",
        "release workflow exact job set/result differs",
    )


def expected_ci_artifact_names(commit_sha: str, workflow_run_id: int, attempt: int) -> tuple[str, ...]:
    return tuple(f"{prefix}-{commit_sha}-{workflow_run_id}-{attempt}" for prefix in REQUIRED_ARTIFACT_PREFIXES)


def infer_ci_tested_commit(path: Path, workflow_run_id: int, attempt: int) -> str:
    value, _ = raw_api(path, "workflow artifacts API response")
    require_fields(value, {"artifacts"}, "E_API_ARTIFACTS", "workflow artifacts API response")
    artifacts = value["artifacts"]
    require(type(artifacts) is list, "E_API_ARTIFACTS", "workflow artifacts must be an array")
    names: list[str] = []
    for number, row in enumerate(artifacts, 1):
        artifact = require_fields(row, {"name"}, "E_API_ARTIFACTS", f"workflow artifact {number}")
        require(type(artifact["name"]) is str, "E_API_ARTIFACTS", f"workflow artifact {number} name differs")
        names.append(artifact["name"])
    commits: set[str] = set()
    for prefix in REQUIRED_ARTIFACT_PREFIXES:
        pattern = re.compile(
            rf"{re.escape(prefix)}-([0-9a-f]{{40}})-{workflow_run_id}-{attempt}"
        )
        matches = [pattern.fullmatch(name) for name in names]
        current = [match.group(1) for match in matches if match is not None]
        require(len(current) == 1, "E_API_ARTIFACTS", f"current artifact identity differs for {prefix}")
        commits.add(current[0])
    require(len(commits) == 1, "E_CI_CROSS_BINDING", "current CI artifacts do not share one tested commit")
    return next(iter(commits))


def validate_artifacts_api(
    path: Path,
    *,
    workflow_run_id: int,
    platform_head_sha: str,
    head_branch: str,
    attempt: int,
    expected_names: tuple[str, ...],
) -> dict[str, Any]:
    value, digest = raw_api(path, "workflow artifacts API response")
    require_fields(value, {"total_count", "artifacts"}, "E_API_ARTIFACTS", "workflow artifacts API response")
    artifacts = value["artifacts"]
    require(type(artifacts) is list and type(value["total_count"]) is int and value["total_count"] == len(artifacts), "E_API_ARTIFACTS", "workflow artifact cardinality differs")
    ids: set[int] = set()
    rows: dict[str, dict[str, Any]] = {}
    for number, row in enumerate(artifacts, 1):
        artifact = require_fields(row, {"id", "name", "size_in_bytes", "expired", "digest", "workflow_run"}, "E_API_ARTIFACTS", f"workflow artifact {number}")
        artifact_id = positive_integer(artifact["id"], "E_API_ARTIFACTS", f"workflow artifact {number} id")
        positive_integer(artifact["size_in_bytes"], "E_API_ARTIFACTS", f"workflow artifact {number} size")
        require(artifact_id not in ids and type(artifact["name"]) is str and artifact["name"] not in rows, "E_API_ARTIFACTS", "duplicate workflow artifact identity/name")
        ids.add(artifact_id)
        workflow = require_fields(artifact["workflow_run"], {"id", "head_sha", "head_branch"}, "E_API_ARTIFACTS", f"workflow artifact {number} run")
        require(
            type(artifact["expired"]) is bool
            and (artifact["expired"] is False or artifact["name"] not in expected_names)
            and type(artifact["digest"]) is str
            and DOCKER_IMAGE_ID.fullmatch(artifact["digest"]) is not None
            and workflow["id"] == workflow_run_id
            and type(workflow["id"]) is int
            and workflow["head_sha"] == platform_head_sha
            and workflow["head_branch"] == head_branch,
            "E_API_ARTIFACTS",
            f"workflow artifact {number} expiry/digest/run binding differs",
        )
        rows[artifact["name"]] = artifact
    expected = set(expected_names)
    require(expected <= set(rows), "E_API_ARTIFACTS", "current workflow attempt artifact set is incomplete")
    roots: list[str] = []
    for name in expected_names:
        root, separator, attempt_text = name.rpartition("-")
        require(separator == "-" and attempt_text == str(attempt), "E_API_ARTIFACTS", "expected artifact attempt naming differs")
        roots.append(root)
    for name in set(rows) - expected:
        historical_attempts: list[int] = []
        for root in roots:
            prefix = root + "-"
            if name.startswith(prefix) and name[len(prefix) :].isdigit():
                historical_attempts.append(int(name[len(prefix) :]))
        require(
            len(historical_attempts) == 1 and 0 < historical_attempts[0] < attempt,
            "E_API_ARTIFACTS",
            "workflow artifact is not an exact current/prior-attempt artifact",
        )
    return {"sha256": digest, "artifacts": rows}


def validate_aggregate_receipt(
    path: Path,
    *,
    workflow_run_id: int,
    attempt: int,
    commit_sha: str,
    authority_run_id: str,
) -> dict[str, Any]:
    value, raw = parse_json(path, "required aggregate receipt", 32 * 1024 * 1024)
    exact_keys(
        value,
        {"schema_version", "kind", "status", "contract_sha256", "tool_sha256", "aggregator", "identity", "required_jobs", "job_states_sha256", "database_artifacts", "database_artifact_set_sha256"},
        "E_AGGREGATE_RECEIPT",
        "required aggregate receipt",
    )
    require(
        type(value["schema_version"]) is int
        and value["schema_version"] == 1
        and value["kind"] == "v934-ci-aggregate-receipt"
        and value["status"] == "passed"
        and value["aggregator"] == {"id": AGGREGATOR_ID, "name": AGGREGATOR_NAME},
        "E_AGGREGATE_RECEIPT",
        "required aggregate receipt identity/state differs",
    )
    hex64(value["contract_sha256"], "E_AGGREGATE_RECEIPT", "aggregate contract digest")
    hex64(value["tool_sha256"], "E_AGGREGATE_RECEIPT", "aggregate tool digest")
    identity = exact_keys(value["identity"], {"attempt", "authority_run_id", "commit_sha", "workflow_run_id"}, "E_AGGREGATE_RECEIPT", "aggregate identity")
    require(
        identity
        == {"attempt": attempt, "authority_run_id": authority_run_id, "commit_sha": commit_sha, "workflow_run_id": workflow_run_id},
        "E_AGGREGATE_RECEIPT",
        "aggregate workflow/run/commit identity differs",
    )
    required_job_ids = ("inventory-unit", "sqlite-integration", "database-matrix", "external-integration", "coverage", "package-evidence")
    jobs = value["required_jobs"]
    require(type(jobs) is list and len(jobs) == len(required_job_ids), "E_AGGREGATE_RECEIPT", "aggregate required job set differs")
    for index, expected in enumerate(required_job_ids):
        require(jobs[index] == {"job_id": expected, "result": "success"}, "E_AGGREGATE_RECEIPT", f"aggregate required job differs at {index}")
    states = {row["job_id"]: row["result"] for row in jobs}
    require(value["job_states_sha256"] == hashlib.sha256(canonical_json(states)).hexdigest(), "E_AGGREGATE_RECEIPT", "aggregate job states digest differs")
    databases = value["database_artifacts"]
    kinds = ("sqlite", "mysql57", "mysql8", "postgres15", "sqlserver2022")
    require(type(databases) is list and len(databases) == len(kinds), "E_AGGREGATE_RECEIPT", "aggregate database artifact set differs")
    for index, kind in enumerate(kinds):
        row = require_fields(databases[index], {"artifact_tree_sha256", "authority_sha256", "cell_manifest_sha256", "db_kind", "evidence_file_count", "evidence_set_sha256", "logical_name"}, "E_AGGREGATE_RECEIPT", f"aggregate database row {index}")
        require(
            row["db_kind"] == kind
            and row["logical_name"] == f"v934-db-{kind}-{commit_sha}-{workflow_run_id}-{attempt}"
            and type(row["evidence_file_count"]) is int
            and row["evidence_file_count"] > 0,
            "E_AGGREGATE_RECEIPT",
            f"aggregate database row {index} identity differs",
        )
        for key in ("artifact_tree_sha256", "authority_sha256", "cell_manifest_sha256", "evidence_set_sha256"):
            hex64(row[key], "E_AGGREGATE_RECEIPT", f"aggregate database {kind} {key}")
    require(value["database_artifact_set_sha256"] == hashlib.sha256(canonical_json(databases)).hexdigest(), "E_AGGREGATE_RECEIPT", "aggregate database set digest differs")
    return {"sha256": hashlib.sha256(raw).hexdigest()}


def validate_pull_request_api(
    path: Path,
    number: int,
    platform_head_sha: str,
    run_head_branch: str,
    final_head: str,
) -> dict[str, Any]:
    value, digest = raw_api(path, "merged pull request API response")
    require_fields(value, {"number", "state", "merged", "merge_commit_sha", "head", "base"}, "E_PR_LINEAGE", "merged pull request API response")
    head = require_fields(value["head"], {"sha", "ref", "repo"}, "E_PR_LINEAGE", "merged PR head")
    base = require_fields(value["base"], {"ref", "repo"}, "E_PR_LINEAGE", "merged PR base")
    head_repo = require_fields(head["repo"], {"full_name"}, "E_PR_LINEAGE", "merged PR head repo")
    base_repo = require_fields(base["repo"], {"full_name"}, "E_PR_LINEAGE", "merged PR base repo")
    require(
        value["number"] == number
        and type(value["number"]) is int
        and value["state"] == "closed"
        and type(value["merged"]) is bool
        and value["merged"] is True
        and value["merge_commit_sha"] == final_head
        and head["sha"] == platform_head_sha
        and head["ref"] == run_head_branch
        and head_repo["full_name"] == REPOSITORY_FULL_NAME
        and base["ref"] == "main"
        and base_repo["full_name"] == REPOSITORY_FULL_NAME,
        "E_PR_LINEAGE",
        "PR is unmerged or does not produce exact final main HEAD",
    )
    return {"sha256": digest}


def platform_artifact_projection(row: dict[str, Any]) -> dict[str, Any]:
    return {"id": row["id"], "name": row["name"], "digest": row["digest"], "expired": row["expired"]}


def artifact_transport_spec(prefix: str) -> dict[str, Any]:
    require(prefix in ARTIFACT_TRANSPORT_SPECS, "E_ARTIFACT_TRANSPORT_ROLE", "artifact transport role differs")
    return ARTIFACT_TRANSPORT_SPECS[prefix]


def canonical_artifact_zip_path(raw_name: str, is_directory: bool) -> str:
    require(type(raw_name) is str and bool(raw_name), "E_ARTIFACT_TRANSPORT_PATH", "empty artifact ZIP entry")
    require(
        "\\" not in raw_name
        and "\x00" not in raw_name
        and not any(ord(character) < 32 or ord(character) == 127 for character in raw_name),
        "E_ARTIFACT_TRANSPORT_PATH",
        f"unsafe artifact ZIP entry path: {raw_name!r}",
    )
    if is_directory:
        require(
            raw_name.endswith("/") and not raw_name.endswith("//"),
            "E_ARTIFACT_TRANSPORT_PATH",
            f"non-canonical artifact ZIP directory: {raw_name!r}",
        )
        candidate = raw_name[:-1]
    else:
        require(
            not raw_name.endswith("/"),
            "E_ARTIFACT_TRANSPORT_PATH",
            f"non-canonical artifact ZIP file: {raw_name!r}",
        )
        candidate = raw_name
    require(
        bool(candidate)
        and len(candidate.encode("utf-8")) <= 512
        and not candidate.startswith("/"),
        "E_ARTIFACT_TRANSPORT_PATH",
        f"artifact ZIP entry path differs: {raw_name!r}",
    )
    parts = candidate.split("/")
    require(
        all(part not in {"", ".", ".."} for part in parts)
        and ":" not in parts[0],
        "E_ARTIFACT_TRANSPORT_PATH",
        f"artifact ZIP entry escapes its root: {raw_name!r}",
    )
    normalized = PurePosixPath(*parts).as_posix()
    require(
        normalized == candidate,
        "E_ARTIFACT_TRANSPORT_PATH",
        f"non-canonical artifact ZIP entry: {raw_name!r}",
    )
    return normalized


def validate_artifact_zip_extra(raw: bytes, label: str) -> None:
    cursor = 0
    while cursor < len(raw):
        require(
            cursor + 4 <= len(raw),
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"truncated ZIP extra field in {label}",
        )
        field_id = int.from_bytes(raw[cursor : cursor + 2], "little")
        field_size = int.from_bytes(raw[cursor + 2 : cursor + 4], "little")
        cursor += 4
        require(
            cursor + field_size <= len(raw),
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"ZIP extra field exceeds its record in {label}",
        )
        require(
            field_id != 0x0001,
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"ZIP64 extra field is forbidden in {label}",
        )
        cursor += field_size
    require(
        cursor == len(raw),
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"ZIP extra field framing differs in {label}",
    )


def validate_artifact_zip_framing(data: bytes, label: str) -> int:
    require(
        len(data) >= 22,
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"artifact ZIP is truncated: {label}",
    )
    eocd_signature = b"PK\x05\x06"
    candidates: list[int] = []
    cursor = max(0, len(data) - (65_535 + 22))
    while True:
        offset = data.find(eocd_signature, cursor)
        if offset < 0:
            break
        if offset + 22 <= len(data):
            comment_size = int.from_bytes(data[offset + 20 : offset + 22], "little")
            if offset + 22 + comment_size == len(data):
                candidates.append(offset)
        cursor = offset + 1
    require(
        len(candidates) == 1,
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"artifact ZIP EOCD is absent/ambiguous or does not end at EOF: {label}",
    )
    eocd = candidates[0]
    disk = int.from_bytes(data[eocd + 4 : eocd + 6], "little")
    directory_disk = int.from_bytes(data[eocd + 6 : eocd + 8], "little")
    disk_entries = int.from_bytes(data[eocd + 8 : eocd + 10], "little")
    total_entries = int.from_bytes(data[eocd + 10 : eocd + 12], "little")
    directory_size = int.from_bytes(data[eocd + 12 : eocd + 16], "little")
    directory_offset = int.from_bytes(data[eocd + 16 : eocd + 20], "little")
    comment_size = int.from_bytes(data[eocd + 20 : eocd + 22], "little")
    require(
        disk == directory_disk == 0 and disk_entries == total_entries,
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"multi-disk artifact ZIP is forbidden: {label}",
    )
    require(
        0 < total_entries < 0xFFFF
        and directory_size != 0xFFFFFFFF
        and directory_offset != 0xFFFFFFFF,
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"empty/ZIP64 artifact ZIP is forbidden: {label}",
    )
    require(
        comment_size == 0,
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"artifact ZIP comment is forbidden: {label}",
    )
    require(
        directory_offset > 0
        and directory_size > 0
        and directory_offset + directory_size == eocd
        and data.startswith(b"PK\x03\x04")
        and data[directory_offset : directory_offset + 4] == b"PK\x01\x02",
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"artifact ZIP has a prefix or non-exact central directory: {label}",
    )

    records: list[dict[str, Any]] = []
    cursor = directory_offset
    for index in range(total_entries):
        require(
            cursor + 46 <= eocd and data[cursor : cursor + 4] == b"PK\x01\x02",
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"artifact ZIP central record differs at entry {index + 1}: {label}",
        )
        flags = int.from_bytes(data[cursor + 8 : cursor + 10], "little")
        method = int.from_bytes(data[cursor + 10 : cursor + 12], "little")
        crc32 = int.from_bytes(data[cursor + 16 : cursor + 20], "little")
        compressed_size = int.from_bytes(data[cursor + 20 : cursor + 24], "little")
        uncompressed_size = int.from_bytes(data[cursor + 24 : cursor + 28], "little")
        name_size = int.from_bytes(data[cursor + 28 : cursor + 30], "little")
        extra_size = int.from_bytes(data[cursor + 30 : cursor + 32], "little")
        member_comment_size = int.from_bytes(data[cursor + 32 : cursor + 34], "little")
        start_disk = int.from_bytes(data[cursor + 34 : cursor + 36], "little")
        local_offset = int.from_bytes(data[cursor + 42 : cursor + 46], "little")
        record_end = cursor + 46 + name_size + extra_size + member_comment_size
        require(
            name_size > 0 and record_end <= eocd,
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"artifact ZIP central record exceeds its directory: {label}",
        )
        require(
            member_comment_size == 0,
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"artifact ZIP member comment is forbidden: {label}",
        )
        require(
            start_disk == 0
            and compressed_size != 0xFFFFFFFF
            and uncompressed_size != 0xFFFFFFFF
            and local_offset != 0xFFFFFFFF,
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"artifact ZIP64/multi-disk member is forbidden: {label}",
        )
        require(
            flags & ~0x080E == 0,
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"unsupported artifact ZIP flags are present: {label}",
        )
        name_start = cursor + 46
        name_end = name_start + name_size
        extra_end = name_end + extra_size
        member_name = data[name_start:name_end]
        validate_artifact_zip_extra(
            data[name_end:extra_end], f"{label} central entry {index + 1}"
        )
        records.append(
            {
                "compressed_size": compressed_size,
                "crc32": crc32,
                "flags": flags,
                "local_offset": local_offset,
                "method": method,
                "name": member_name,
                "uncompressed_size": uncompressed_size,
            }
        )
        cursor = record_end
    require(
        cursor == eocd,
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"artifact ZIP central directory contains trailing/non-file records: {label}",
    )

    local_offsets = [int(record["local_offset"]) for record in records]
    require(
        len(set(local_offsets)) == total_entries and min(local_offsets) == 0,
        "E_ARTIFACT_TRANSPORT_FRAMING",
        f"artifact ZIP local records have a prefix/duplicate offset: {label}",
    )
    ordered = sorted(records, key=lambda record: int(record["local_offset"]))
    for index, record in enumerate(ordered):
        local_offset = int(record["local_offset"])
        require(
            local_offset + 30 <= directory_offset
            and data[local_offset : local_offset + 4] == b"PK\x03\x04",
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"artifact ZIP local header differs at entry {index + 1}: {label}",
        )
        flags = int.from_bytes(data[local_offset + 6 : local_offset + 8], "little")
        method = int.from_bytes(data[local_offset + 8 : local_offset + 10], "little")
        crc32 = int.from_bytes(data[local_offset + 14 : local_offset + 18], "little")
        compressed_size = int.from_bytes(data[local_offset + 18 : local_offset + 22], "little")
        uncompressed_size = int.from_bytes(data[local_offset + 22 : local_offset + 26], "little")
        name_size = int.from_bytes(data[local_offset + 26 : local_offset + 28], "little")
        extra_size = int.from_bytes(data[local_offset + 28 : local_offset + 30], "little")
        name_start = local_offset + 30
        name_end = name_start + name_size
        extra_end = name_end + extra_size
        payload_end = extra_end + int(record["compressed_size"])
        require(
            extra_end <= directory_offset
            and payload_end <= directory_offset
            and flags == record["flags"]
            and method == record["method"]
            and data[name_start:name_end] == record["name"],
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"artifact ZIP local/central header binding differs: {label}",
        )
        validate_artifact_zip_extra(
            data[name_end:extra_end], f"{label} local entry {index + 1}"
        )
        if flags & 0x0008:
            require(
                crc32 in {0, record["crc32"]}
                and compressed_size in {0, record["compressed_size"]}
                and uncompressed_size in {0, record["uncompressed_size"]},
                "E_ARTIFACT_TRANSPORT_FRAMING",
                f"artifact ZIP deferred local sizes differ: {label}",
            )
            descriptor = payload_end
            if data[descriptor : descriptor + 4] == b"PK\x07\x08":
                descriptor += 4
            require(
                descriptor + 12 <= directory_offset
                and int.from_bytes(data[descriptor : descriptor + 4], "little")
                == record["crc32"]
                and int.from_bytes(data[descriptor + 4 : descriptor + 8], "little")
                == record["compressed_size"]
                and int.from_bytes(data[descriptor + 8 : descriptor + 12], "little")
                == record["uncompressed_size"],
                "E_ARTIFACT_TRANSPORT_FRAMING",
                f"artifact ZIP data descriptor differs: {label}",
            )
            payload_end = descriptor + 12
        else:
            require(
                crc32 == record["crc32"]
                and compressed_size == record["compressed_size"]
                and uncompressed_size == record["uncompressed_size"],
                "E_ARTIFACT_TRANSPORT_FRAMING",
                f"artifact ZIP local sizes differ: {label}",
            )
        next_offset = (
            int(ordered[index + 1]["local_offset"])
            if index + 1 < len(ordered)
            else directory_offset
        )
        require(
            payload_end == next_offset,
            "E_ARTIFACT_TRANSPORT_FRAMING",
            f"artifact ZIP local records contain a gap/trailing data: {label}",
        )
    return total_entries


def inspect_artifact_transport_archive(
    archive_path: Path,
    spec: dict[str, Any],
    artifact: dict[str, Any],
) -> dict[str, Any]:
    archive_raw = regular_bytes(
        archive_path,
        f"{spec['role']} REST artifact ZIP",
        MAX_ARTIFACT_TRANSPORT_ARCHIVE_BYTES,
    )
    require(bool(archive_raw), "E_ARTIFACT_TRANSPORT_SIZE", "REST artifact ZIP is empty")
    archive_sha256 = hashlib.sha256(archive_raw).hexdigest()
    require(
        artifact.get("digest") == f"sha256:{archive_sha256}",
        "E_ARTIFACT_TRANSPORT_DIGEST",
        f"{spec['role']} REST artifact ZIP differs from the API digest",
    )
    framed_entries = validate_artifact_zip_framing(
        archive_raw, f"{spec['role']} REST artifact ZIP"
    )
    try:
        with zipfile.ZipFile(io.BytesIO(archive_raw), "r") as archive_zip:
            infos = archive_zip.infolist()
            require(
                0 < len(infos) <= MAX_ARTIFACT_TRANSPORT_ENTRIES
                and len(infos) == framed_entries
                and archive_zip.comment == b""
                and min(info.header_offset for info in infos) == 0,
                "E_ARTIFACT_TRANSPORT_SET",
                f"{spec['role']} artifact ZIP entry cardinality differs",
            )
            entries: dict[str, tuple[zipfile.ZipInfo, bool]] = {}
            casefolded: dict[str, str] = {}
            total_uncompressed = 0
            for info in infos:
                require(
                    info.orig_filename == info.filename
                    and not info.comment
                    and info.flag_bits & ~0x080E == 0,
                    "E_ARTIFACT_TRANSPORT_FRAMING",
                    f"artifact ZIP reader metadata differs: {info.filename!r}",
                )
                validate_artifact_zip_extra(
                    info.extra, f"{spec['role']} ZIP member {info.filename!r}"
                )
                is_directory = info.is_dir()
                name = canonical_artifact_zip_path(info.filename, is_directory)
                folded = name.casefold()
                require(
                    name not in entries and folded not in casefolded,
                    "E_ARTIFACT_TRANSPORT_DUPLICATE",
                    f"duplicate/case-colliding artifact ZIP entry: {name}",
                )
                casefolded[folded] = name
                require(
                    info.flag_bits & 0x1 == 0,
                    "E_ARTIFACT_TRANSPORT_ARCHIVE",
                    f"encrypted artifact ZIP entry: {name}",
                )
                require(
                    info.compress_type in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED},
                    "E_ARTIFACT_TRANSPORT_ARCHIVE",
                    f"unsupported artifact ZIP compression: {name}",
                )
                unix_mode = (info.external_attr >> 16) & 0xFFFF
                file_type = stat.S_IFMT(unix_mode)
                allowed_types = {0, stat.S_IFDIR} if is_directory else {0, stat.S_IFREG}
                require(
                    file_type in allowed_types,
                    "E_ARTIFACT_TRANSPORT_ARCHIVE",
                    f"symlink/special artifact ZIP entry: {name}",
                )
                require(
                    type(info.file_size) is int
                    and info.file_size >= 0
                    and type(info.compress_size) is int
                    and info.compress_size >= 0,
                    "E_ARTIFACT_TRANSPORT_SIZE",
                    f"invalid artifact ZIP entry size: {name}",
                )
                if is_directory:
                    require(
                        info.file_size == 0,
                        "E_ARTIFACT_TRANSPORT_SIZE",
                        f"non-empty artifact ZIP directory: {name}",
                    )
                else:
                    total_uncompressed += info.file_size
                    require(
                        total_uncompressed <= MAX_ARTIFACT_TRANSPORT_EXTRACTED_BYTES,
                        "E_ARTIFACT_TRANSPORT_SIZE",
                        f"{spec['role']} artifact ZIP expands beyond its budget",
                    )
                entries[name] = (info, is_directory)

            for name, (_, is_directory) in entries.items():
                for ancestor in PurePosixPath(name).parents:
                    ancestor_name = ancestor.as_posix()
                    if ancestor_name == ".":
                        continue
                    if ancestor_name in entries:
                        require(
                            entries[ancestor_name][1],
                            "E_ARTIFACT_TRANSPORT_PATH",
                            f"artifact ZIP file is an entry ancestor: {ancestor_name}",
                        )
                require(
                    is_directory or not name.endswith("/"),
                    "E_ARTIFACT_TRANSPORT_PATH",
                    f"artifact ZIP file path differs: {name}",
                )

            observed_files = {
                name for name, (_, is_directory) in entries.items() if not is_directory
            }
            observed_directories = {
                name for name, (_, is_directory) in entries.items() if is_directory
            }
            exact_files = spec["exact_files"]
            if exact_files is not None:
                require(
                    observed_files == set(exact_files) and not observed_directories,
                    "E_ARTIFACT_TRANSPORT_SET",
                    f"{spec['role']} artifact ZIP exact file set differs",
                )
            target = spec["member_path"]
            require(
                target in entries and not entries[target][1],
                "E_ARTIFACT_TRANSPORT_MEMBER",
                f"{spec['role']} target member is absent",
            )
            require(
                entries[target][0].file_size <= MAX_ARTIFACT_TRANSPORT_MEMBER_BYTES,
                "E_ARTIFACT_TRANSPORT_SIZE",
                f"{spec['role']} target member exceeds its budget",
            )

            rows: list[dict[str, Any]] = []
            target_raw: bytes | None = None
            for name in sorted(entries, key=lambda value: value.encode("utf-8")):
                info, is_directory = entries[name]
                if is_directory:
                    rows.append({"kind": "directory", "path": name, "size_bytes": 0})
                    continue
                digest = hashlib.sha256()
                size = 0
                chunks: list[bytes] | None = [] if name == target else None
                with archive_zip.open(info, "r") as member:
                    while True:
                        chunk = member.read(1024 * 1024)
                        if not chunk:
                            break
                        size += len(chunk)
                        require(
                            size <= info.file_size,
                            "E_ARTIFACT_TRANSPORT_SIZE",
                            f"artifact ZIP member exceeds its declared size: {name}",
                        )
                        digest.update(chunk)
                        if chunks is not None:
                            chunks.append(chunk)
                require(
                    size == info.file_size,
                    "E_ARTIFACT_TRANSPORT_SIZE",
                    f"artifact ZIP member size differs: {name}",
                )
                member_sha256 = digest.hexdigest()
                rows.append(
                    {
                        "compressed_size_bytes": info.compress_size,
                        "compression_method": info.compress_type,
                        "kind": "file",
                        "path": name,
                        "sha256": member_sha256,
                        "size_bytes": size,
                    }
                )
                if chunks is not None:
                    target_raw = b"".join(chunks)
            require(
                target_raw is not None,
                "E_ARTIFACT_TRANSPORT_MEMBER",
                f"{spec['role']} target member could not be read",
            )
    except PointerError:
        raise
    except (zipfile.BadZipFile, zipfile.LargeZipFile, EOFError, RuntimeError, OSError) as exc:
        reject(
            "E_ARTIFACT_TRANSPORT_ARCHIVE",
            f"cannot safely read {spec['role']} REST artifact ZIP: {exc}",
        )

    return {
        "archive_sha256": archive_sha256,
        "archive_size_bytes": len(archive_raw),
        "member_raw": target_raw,
        "member_sha256": hashlib.sha256(target_raw).hexdigest(),
        "member_size_bytes": len(target_raw),
        "file_count": len(observed_files),
        "directory_count": len(observed_directories),
        "total_uncompressed_bytes": total_uncompressed,
        "manifest_sha256": hashlib.sha256(canonical_json(rows)).hexdigest(),
    }


def artifact_transport_receipt_value(
    prefix: str,
    workflow_run_id: int,
    attempt: int,
    artifact: dict[str, Any],
    inspection: dict[str, Any],
) -> dict[str, Any]:
    spec = artifact_transport_spec(prefix)
    return {
        "schema_version": 1,
        "kind": "v934-rest-artifact-transport-receipt",
        "status": "passed",
        "repository": REPOSITORY_FULL_NAME,
        "role": spec["role"],
        "workflow_run_id": workflow_run_id,
        "attempt": attempt,
        "artifact": platform_artifact_projection(artifact),
        "archive": {
            "file": spec["archive_file"],
            "sha256": inspection["archive_sha256"],
            "size_bytes": inspection["archive_size_bytes"],
        },
        "member": {
            "path": spec["member_path"],
            "sha256": inspection["member_sha256"],
            "size_bytes": inspection["member_size_bytes"],
        },
        "entries": {
            "file_count": inspection["file_count"],
            "directory_count": inspection["directory_count"],
            "total_uncompressed_bytes": inspection["total_uncompressed_bytes"],
            "manifest_sha256": inspection["manifest_sha256"],
        },
    }


def validate_artifact_transport(
    evidence: Path,
    prefix: str,
    workflow_run_id: int,
    attempt: int,
    artifact: dict[str, Any],
) -> dict[str, Any]:
    spec = artifact_transport_spec(prefix)
    inspection = inspect_artifact_transport_archive(
        evidence / spec["archive_file"], spec, artifact
    )
    local_raw = regular_bytes(
        evidence / spec["local_file"],
        f"{spec['role']} local target receipt",
        MAX_ARTIFACT_TRANSPORT_MEMBER_BYTES,
    )
    require(
        local_raw == inspection["member_raw"],
        "E_ARTIFACT_TRANSPORT_MEMBER",
        f"{spec['role']} local receipt is not the downloaded artifact member",
    )
    value, raw = parse_json(
        evidence / spec["receipt_file"],
        f"{spec['role']} artifact transport receipt",
        2 * 1024 * 1024,
    )
    exact_keys(
        value,
        ARTIFACT_TRANSPORT_FIELDS,
        "E_ARTIFACT_TRANSPORT_SCHEMA",
        f"{spec['role']} artifact transport receipt",
    )
    exact_keys(
        value["artifact"],
        PLATFORM_ARTIFACT_FIELDS,
        "E_ARTIFACT_TRANSPORT_SCHEMA",
        f"{spec['role']} artifact projection",
    )
    exact_keys(
        value["archive"],
        ARTIFACT_TRANSPORT_ARCHIVE_FIELDS,
        "E_ARTIFACT_TRANSPORT_SCHEMA",
        f"{spec['role']} archive binding",
    )
    exact_keys(
        value["member"],
        ARTIFACT_TRANSPORT_MEMBER_FIELDS,
        "E_ARTIFACT_TRANSPORT_SCHEMA",
        f"{spec['role']} member binding",
    )
    exact_keys(
        value["entries"],
        ARTIFACT_TRANSPORT_ENTRIES_FIELDS,
        "E_ARTIFACT_TRANSPORT_SCHEMA",
        f"{spec['role']} entry framing",
    )
    expected = artifact_transport_receipt_value(
        prefix, workflow_run_id, attempt, artifact, inspection
    )
    require(
        value == expected,
        "E_ARTIFACT_TRANSPORT_IDENTITY",
        f"{spec['role']} artifact transport receipt differs from API/ZIP authority",
    )
    return {
        "sha256": hashlib.sha256(raw).hexdigest(),
        "member_sha256": inspection["member_sha256"],
        "archive_sha256": inspection["archive_sha256"],
    }


def resolve_artifact_transport_authority(
    evidence: Path, prefix: str
) -> tuple[int, int, dict[str, Any]]:
    spec = artifact_transport_spec(prefix)
    if prefix in {"pr", "main"}:
        run_value, _ = raw_api(
            evidence / f"{prefix}-run-api.json", f"{prefix} transport run API"
        )
        workflow_run_id = positive_integer(
            run_value.get("id"), "E_ARTIFACT_TRANSPORT_IDENTITY", "transport workflow run id"
        )
        attempt = positive_integer(
            run_value.get("run_attempt"), "E_ARTIFACT_TRANSPORT_IDENTITY", "transport workflow attempt"
        )
        platform_head = hex40(
            run_value.get("head_sha"), "E_ARTIFACT_TRANSPORT_IDENTITY", "transport platform head"
        )
        if prefix == "pr":
            pr_value, _ = raw_api(evidence / "pr-api.json", "transport PR API")
            pull_number = positive_integer(
                pr_value.get("number"), "E_ARTIFACT_TRANSPORT_IDENTITY", "transport PR number"
            )
            event_name = "pull_request"
        else:
            pull_number = None
            event_name = "push"
        run_result = validate_run_api(
            evidence / f"{prefix}-run-api.json",
            workflow_run_id=workflow_run_id,
            attempt=attempt,
            event_name=event_name,
            platform_head_sha=platform_head,
            workflow_file=CI_WORKFLOW,
            pull_request_number=pull_number,
        )
        commit = infer_ci_tested_commit(
            evidence / f"{prefix}-artifacts-api.json", workflow_run_id, attempt
        )
        artifacts_result = validate_artifacts_api(
            evidence / f"{prefix}-artifacts-api.json",
            workflow_run_id=workflow_run_id,
            platform_head_sha=platform_head,
            head_branch=run_result["head_branch"],
            attempt=attempt,
            expected_names=expected_ci_artifact_names(commit, workflow_run_id, attempt),
        )
    else:
        run_value, _ = raw_api(evidence / "release-run-api.json", "release transport run API")
        workflow_run_id = positive_integer(
            run_value.get("id"), "E_ARTIFACT_TRANSPORT_IDENTITY", "release transport workflow run id"
        )
        attempt = positive_integer(
            run_value.get("run_attempt"), "E_ARTIFACT_TRANSPORT_IDENTITY", "release transport workflow attempt"
        )
        commit = hex40(
            run_value.get("head_sha"), "E_ARTIFACT_TRANSPORT_IDENTITY", "release transport commit"
        )
        run_result = validate_run_api(
            evidence / "release-run-api.json",
            workflow_run_id=workflow_run_id,
            attempt=attempt,
            event_name="workflow_dispatch",
            platform_head_sha=commit,
            workflow_file=".github/workflows/release.yml",
            pull_request_number=None,
        )
        expected_name = f"{spec['artifact_prefix']}-{commit}-{workflow_run_id}-{attempt}"
        artifacts_result = validate_artifacts_api(
            evidence / "release-artifacts-api.json",
            workflow_run_id=workflow_run_id,
            platform_head_sha=commit,
            head_branch=run_result["head_branch"],
            attempt=attempt,
            expected_names=(expected_name,),
        )
    artifact_name = f"{spec['artifact_prefix']}-{commit}-{workflow_run_id}-{attempt}"
    return workflow_run_id, attempt, artifacts_result["artifacts"][artifact_name]


def build_artifact_transport_receipt(args: argparse.Namespace) -> dict[str, Any]:
    load_promotion_contract()
    evidence = real_directory(args.evidence_dir, "Step 7 receipt evidence directory")
    prefix = args.role
    spec = artifact_transport_spec(prefix)
    workflow_run_id, attempt, artifact = resolve_artifact_transport_authority(
        evidence, prefix
    )
    inspection = inspect_artifact_transport_archive(
        evidence / spec["archive_file"], spec, artifact
    )
    local_raw = regular_bytes(
        evidence / spec["local_file"],
        f"{spec['role']} local target receipt",
        MAX_ARTIFACT_TRANSPORT_MEMBER_BYTES,
    )
    require(
        local_raw == inspection["member_raw"],
        "E_ARTIFACT_TRANSPORT_MEMBER",
        f"{spec['role']} local receipt is not the downloaded artifact member",
    )
    receipt = artifact_transport_receipt_value(
        prefix, workflow_run_id, attempt, artifact, inspection
    )
    require(
        tuple(receipt) == ARTIFACT_TRANSPORT_FIELDS,
        "E_RECEIPT_BUILD",
        "artifact transport receipt field order differs",
    )
    payload = canonical_json(receipt)
    output = evidence / spec["receipt_file"]
    atomic_replace_bytes(output, payload)
    return {
        "command": "build-artifact-transport-receipt",
        "path": str(output),
        "role": spec["role"],
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "passed",
        "workflow_run_id": workflow_run_id,
    }


def build_ci_receipt(
    args: argparse.Namespace, failpoint: str | None = None
) -> dict[str, Any]:
    load_promotion_contract()
    evidence = real_directory(args.evidence_dir, "Step 7 receipt evidence directory")
    require(args.prefix in {"pr", "main"}, "E_RECEIPT_BUILD", "CI receipt prefix differs")
    prefix = args.prefix
    authority_run_id = safe_run_id(args.authority_run_id)
    final_head = hex40(args.final_head, "E_GIT", "final Git HEAD")
    event_name = "pull_request" if prefix == "pr" else "push"
    gate_mode = "rehearsal" if prefix == "pr" else "authority"

    pull_request_number: int | None = None
    if prefix == "pr":
        pr_value, _ = raw_api(evidence / "pr-api.json", "merged pull request API response")
        require_fields(pr_value, {"number"}, "E_PR_LINEAGE", "merged pull request API response")
        pull_request_number = positive_integer(pr_value["number"], "E_PR_LINEAGE", "pull request number")

    run_value, _ = raw_api(evidence / f"{prefix}-run-api.json", f"{event_name} workflow run API response")
    require_fields(
        run_value,
        {"id", "run_attempt", "event", "head_sha", "pull_requests"},
        "E_API_RUN",
        f"{event_name} workflow run API response",
    )
    workflow_run_id = positive_integer(run_value["id"], "E_API_RUN", "workflow run id")
    attempt = positive_integer(run_value["run_attempt"], "E_API_RUN", "workflow run attempt")
    platform_head_sha = hex40(run_value["head_sha"], "E_API_RUN", "workflow run platform head")
    require(run_value["event"] == event_name, "E_API_RUN", "workflow run event differs from receipt prefix")
    commit_sha = infer_ci_tested_commit(
        evidence / f"{prefix}-artifacts-api.json", workflow_run_id, attempt
    )
    if prefix == "main":
        require(
            commit_sha == final_head and platform_head_sha == final_head,
            "E_CI_IDENTITY",
            "main CI tested/platform commit differs from final HEAD",
        )

    run_result = validate_run_api(
        evidence / f"{prefix}-run-api.json",
        workflow_run_id=workflow_run_id,
        attempt=attempt,
        event_name=event_name,
        platform_head_sha=platform_head_sha,
        workflow_file=CI_WORKFLOW,
        pull_request_number=pull_request_number,
    )
    jobs_result = validate_jobs_api(
        evidence / f"{prefix}-jobs-api.json",
        workflow_run_id=workflow_run_id,
        attempt=attempt,
        platform_head_sha=platform_head_sha,
        required_names=REQUIRED_JOB_NAMES,
    )
    artifacts_result = validate_artifacts_api(
        evidence / f"{prefix}-artifacts-api.json",
        workflow_run_id=workflow_run_id,
        platform_head_sha=platform_head_sha,
        head_branch=run_result["head_branch"],
        attempt=attempt,
        expected_names=expected_ci_artifact_names(commit_sha, workflow_run_id, attempt),
    )
    aggregate = validate_aggregate_receipt(
        evidence / f"{prefix}-aggregate-receipt.json",
        workflow_run_id=workflow_run_id,
        attempt=attempt,
        commit_sha=commit_sha,
        authority_run_id=authority_run_id,
    )
    pull_request_api_sha256: str | None = None
    if prefix == "pr":
        assert pull_request_number is not None
        pull_request_api_sha256 = validate_pull_request_api(
            evidence / "pr-api.json",
            pull_request_number,
            platform_head_sha,
            run_result["head_branch"],
            final_head,
        )["sha256"]

    aggregator = jobs_result["jobs"][AGGREGATOR_NAME]
    required_name = f"v934-required-receipt-{commit_sha}-{workflow_run_id}-{attempt}"
    required_artifact = artifacts_result["artifacts"][required_name]
    transport = validate_artifact_transport(
        evidence, prefix, workflow_run_id, attempt, required_artifact
    )
    require(
        transport["member_sha256"] == aggregate["sha256"],
        "E_ARTIFACT_TRANSPORT_MEMBER",
        "required artifact member digest differs from the aggregate receipt",
    )
    receipt = {
        "schema_version": 1,
        "kind": "v934-platform-ci-receipt",
        "status": "passed",
        "version": VERSION,
        "repository": REPOSITORY_FULL_NAME,
        "event_name": event_name,
        "workflow_file": CI_WORKFLOW,
        "workflow_run_id": workflow_run_id,
        "attempt": attempt,
        "commit_sha": commit_sha,
        "platform_head_sha": platform_head_sha,
        "authority_run_id": authority_run_id,
        "gate_mode": gate_mode,
        "pull_request_number": pull_request_number,
        "aggregator": {
            "id": AGGREGATOR_ID,
            "name": AGGREGATOR_NAME,
            "job_id": aggregator["id"],
            "conclusion": "success",
        },
        "aggregate_receipt_sha256": aggregate["sha256"],
        "required_receipt_artifact": platform_artifact_projection(
            required_artifact
        ),
        "required_artifact_transport_sha256": transport["sha256"],
        "run_api_sha256": run_result["sha256"],
        "jobs_api_sha256": jobs_result["sha256"],
        "artifacts_api_sha256": artifacts_result["sha256"],
        "pull_request_api_sha256": pull_request_api_sha256,
    }
    require(tuple(receipt) == CI_RECEIPT_FIELDS, "E_RECEIPT_BUILD", "CI receipt field order differs")
    output = evidence / f"{prefix}-ci-receipt.json"
    payload = canonical_json(receipt)
    atomic_replace_bytes(output, payload, failpoint=failpoint)
    return {
        "command": "build-ci-receipt",
        "path": str(output),
        "prefix": prefix,
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "passed",
        "workflow_run_id": workflow_run_id,
    }


def validate_ci_receipt(
    evidence: Path,
    prefix: str,
    *,
    event_name: str,
    gate_mode: str,
    authority_run_id: str | None,
    git_head: str | None,
    final_head: str,
) -> dict[str, Any]:
    label = f"{event_name} CI receipt"
    path = evidence / f"{prefix}-ci-receipt.json"
    value, raw = parse_json(path, label)
    exact_keys(value, CI_RECEIPT_FIELDS, "E_CI_SCHEMA", label)
    require(
        type(value["schema_version"]) is int
        and value["schema_version"] == 1
        and value["kind"] == "v934-platform-ci-receipt"
        and value["status"] == "passed"
        and value["version"] == VERSION
        and value["repository"] == REPOSITORY_FULL_NAME
        and value["event_name"] == event_name
        and value["workflow_file"] == CI_WORKFLOW
        and value["gate_mode"] == gate_mode,
        "E_CI_IDENTITY",
        f"{label} identity/state differs",
    )
    workflow_run_id = positive_integer(value["workflow_run_id"], "E_CI_IDENTITY", f"{label} workflow run id")
    attempt = positive_integer(value["attempt"], "E_CI_IDENTITY", f"{label} attempt")
    commit = hex40(value["commit_sha"], "E_CI_IDENTITY", f"{label} commit")
    platform_head = hex40(value["platform_head_sha"], "E_CI_IDENTITY", f"{label} platform head")
    require(type(value["authority_run_id"]) is str, "E_CI_IDENTITY", f"{label} authority run id differs")
    safe_run_id(value["authority_run_id"])
    if authority_run_id is not None:
        require(value["authority_run_id"] == authority_run_id, "E_CI_IDENTITY", f"{label} authority run differs")
    if git_head is not None:
        require(
            commit == git_head and platform_head == git_head,
            "E_CI_IDENTITY",
            f"{label} tested/platform commit differs from final HEAD",
        )
    pull_number = value["pull_request_number"]
    if event_name == "pull_request":
        positive_integer(pull_number, "E_PR_LINEAGE", "pull request number")
        hex64(value["pull_request_api_sha256"], "E_CI_RECEIPT", "pull request API digest")
    else:
        require(pull_number is None and value["pull_request_api_sha256"] is None, "E_CI_SCHEMA", "main CI must not claim PR linkage")
    run_result = validate_run_api(
        evidence / f"{prefix}-run-api.json",
        workflow_run_id=workflow_run_id,
        attempt=attempt,
        event_name=event_name,
        platform_head_sha=platform_head,
        workflow_file=CI_WORKFLOW,
        pull_request_number=pull_number,
    )
    jobs_result = validate_jobs_api(
        evidence / f"{prefix}-jobs-api.json",
        workflow_run_id=workflow_run_id,
        attempt=attempt,
        platform_head_sha=platform_head,
        required_names=REQUIRED_JOB_NAMES,
    )
    inferred_commit = infer_ci_tested_commit(
        evidence / f"{prefix}-artifacts-api.json", workflow_run_id, attempt
    )
    require(inferred_commit == commit, "E_CI_CROSS_BINDING", f"{label} tested commit differs from artifact identities")
    artifact_names = expected_ci_artifact_names(commit, workflow_run_id, attempt)
    artifacts_result = validate_artifacts_api(
        evidence / f"{prefix}-artifacts-api.json",
        workflow_run_id=workflow_run_id,
        platform_head_sha=platform_head,
        head_branch=run_result["head_branch"],
        attempt=attempt,
        expected_names=artifact_names,
    )
    require(
        value["run_api_sha256"] == run_result["sha256"]
        and value["jobs_api_sha256"] == jobs_result["sha256"]
        and value["artifacts_api_sha256"] == artifacts_result["sha256"],
        "E_API_DIGEST",
        f"{label} raw API preimage digest differs",
    )
    aggregator = exact_keys(value["aggregator"], {"id", "name", "job_id", "conclusion"}, "E_CI_AGGREGATOR", f"{label} aggregator")
    api_aggregator = jobs_result["jobs"][AGGREGATOR_NAME]
    require(
        aggregator
        == {"id": AGGREGATOR_ID, "name": AGGREGATOR_NAME, "job_id": api_aggregator["id"], "conclusion": "success"},
        "E_CI_AGGREGATOR",
        f"{label} stable aggregator is not successful",
    )
    aggregate = validate_aggregate_receipt(
        evidence / f"{prefix}-aggregate-receipt.json",
        workflow_run_id=workflow_run_id,
        attempt=attempt,
        commit_sha=commit,
        authority_run_id=value["authority_run_id"],
    )
    require(value["aggregate_receipt_sha256"] == aggregate["sha256"], "E_CI_RECEIPT", f"{label} aggregate receipt preimage differs")
    required_name = f"v934-required-receipt-{commit}-{workflow_run_id}-{attempt}"
    artifact = artifacts_result["artifacts"][required_name]
    projection = exact_keys(value["required_receipt_artifact"], PLATFORM_ARTIFACT_FIELDS, "E_CI_ARTIFACT", f"{label} required receipt artifact")
    require(projection == platform_artifact_projection(artifact), "E_CI_ARTIFACT", f"{label} required receipt artifact ID/name/digest differs")
    transport = validate_artifact_transport(
        evidence, prefix, workflow_run_id, attempt, artifact
    )
    require(
        value["required_artifact_transport_sha256"] == transport["sha256"]
        and transport["member_sha256"] == aggregate["sha256"],
        "E_ARTIFACT_TRANSPORT_MEMBER",
        f"{label} is not bound to its downloaded required receipt member",
    )
    if event_name == "pull_request":
        pr_result = validate_pull_request_api(
            evidence / "pr-api.json",
            pull_number,
            platform_head,
            run_result["head_branch"],
            final_head,
        )
        require(value["pull_request_api_sha256"] == pr_result["sha256"], "E_API_DIGEST", "PR API preimage digest differs")
    return {
        "sha256": hashlib.sha256(raw).hexdigest(),
        "commit_sha": commit,
        "platform_head_sha": platform_head,
        "workflow_run_id": workflow_run_id,
        "attempt": attempt,
        "authority_run_id": value["authority_run_id"],
        "pull_request_number": pull_number,
        "artifacts": artifacts_result["artifacts"],
        "raw_hashes": {"run": run_result["sha256"], "jobs": jobs_result["sha256"], "artifacts": artifacts_result["sha256"]},
    }


def validate_branch_api(path: Path) -> dict[str, Any]:
    api, api_sha = raw_api(path, "branch protection API response")
    require_fields(api, {"url", "required_status_checks", "enforce_admins"}, "E_BRANCH_API", "branch protection API response")
    required = require_fields(api["required_status_checks"], {"url", "strict", "contexts", "checks"}, "E_BRANCH_API", "required status checks")
    admins = require_fields(api["enforce_admins"], {"enabled"}, "E_BRANCH_API", "branch protection enforce_admins")
    require(type(required["contexts"]) is list and type(required["checks"]) is list, "E_BRANCH_API", "branch required context/check arrays differ")
    require(all(type(context) is str for context in required["contexts"]), "E_BRANCH_API", "branch required contexts differ")
    contexts = set(required["contexts"])
    checks: set[str] = set()
    for row in required["checks"]:
        check = require_fields(row, {"context"}, "E_BRANCH_API", "branch required check")
        require(type(check["context"]) is str, "E_BRANCH_API", "branch required check context differs")
        checks.add(check["context"])
    api_base = f"https://api.github.com/repos/{REPOSITORY_FULL_NAME}/branches/main/protection"
    require(
        api["url"] == api_base
        and required["url"] == f"{api_base}/required_status_checks"
        and type(required["strict"]) is bool
        and required["strict"] is True
        and contexts | checks == {AGGREGATOR_NAME}
        and not ((contexts | checks) - {AGGREGATOR_NAME})
        and type(admins["enabled"]) is bool
        and admins["enabled"] is True,
        "E_BRANCH_API",
        "branch API does not enforce exact stable aggregator context",
    )
    return {"sha256": api_sha}


def build_branch_receipt(
    args: argparse.Namespace, failpoint: str | None = None
) -> dict[str, Any]:
    load_promotion_contract()
    evidence = real_directory(args.evidence_dir, "Step 7 receipt evidence directory")
    git_head = hex40(args.git_head, "E_GIT", "final Git HEAD")
    api = validate_branch_api(evidence / "branch-protection-api.json")
    receipt = {
        "schema_version": 1,
        "kind": "v934-branch-protection-receipt",
        "status": "passed",
        "version": VERSION,
        "repository": REPOSITORY_FULL_NAME,
        "branch": "main",
        "commit_sha": git_head,
        "strict": True,
        "enforce_admins": True,
        "required_contexts": [AGGREGATOR_NAME],
        "github_api_response_sha256": api["sha256"],
    }
    require(tuple(receipt) == BRANCH_RECEIPT_FIELDS, "E_RECEIPT_BUILD", "branch receipt field order differs")
    output = evidence / "branch-protection-receipt.json"
    payload = canonical_json(receipt)
    atomic_replace_bytes(output, payload, failpoint=failpoint)
    return {
        "command": "build-branch-receipt",
        "path": str(output),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "passed",
    }


def validate_branch_protection(evidence: Path, git_head: str) -> dict[str, Any]:
    path = evidence / "branch-protection-receipt.json"
    value, raw = parse_json(path, "branch protection receipt")
    exact_keys(value, BRANCH_RECEIPT_FIELDS, "E_BRANCH_SCHEMA", "branch protection receipt")
    api = validate_branch_api(evidence / "branch-protection-api.json")
    require(
        type(value["schema_version"]) is int
        and value["schema_version"] == 1
        and value["kind"] == "v934-branch-protection-receipt"
        and value["status"] == "passed"
        and value["version"] == VERSION
        and value["repository"] == REPOSITORY_FULL_NAME
        and value["branch"] == "main"
        and value["commit_sha"] == git_head
        and type(value["strict"]) is bool
        and value["strict"] is True
        and type(value["enforce_admins"]) is bool
        and value["enforce_admins"] is True
        and value["required_contexts"] == [AGGREGATOR_NAME]
        and value["github_api_response_sha256"] == api["sha256"],
        "E_BRANCH_PROTECTION",
        "exact main branch protection authority/digest differs",
    )
    return {"sha256": hashlib.sha256(raw).hexdigest()}


def validate_release_receipt_shape(path: Path, git_head: str) -> dict[str, Any]:
    value, raw = parse_json(path, "release dry-run receipt")
    exact_keys(value, RELEASE_RECEIPT_FIELDS, "E_RELEASE_SCHEMA", "release dry-run receipt")
    require(
        type(value["schema_version"]) is int
        and value["schema_version"] == 3
        and value["kind"] == "v934-release-dry-run"
        and value["status"] == "passed"
        and value["commit_sha"] == git_head
        and value["version"] == VERSION
        and value["gate_mode"] == "authority"
        and type(value["publish_performed"]) is bool
        and value["publish_performed"] is False,
        "E_RELEASE_IDENTITY",
        "release dry-run authority identity/publication state differs",
    )
    workflow = exact_keys(value["release_workflow"], RELEASE_WORKFLOW_FIELDS, "E_RELEASE_IDENTITY", "release workflow identity")
    release_run_id = positive_integer(workflow["run_id"], "E_RELEASE_IDENTITY", "release workflow run id")
    release_attempt = positive_integer(workflow["attempt"], "E_RELEASE_IDENTITY", "release workflow attempt")
    require(workflow["event_name"] == "workflow_dispatch", "E_RELEASE_IDENTITY", "release workflow event differs")
    consumed = exact_keys(value["consumed_main_authority"], CONSUMED_MAIN_FIELDS, "E_RELEASE_IDENTITY", "consumed main authority")
    positive_integer(consumed["workflow_run_id"], "E_RELEASE_IDENTITY", "consumed main workflow run id")
    positive_integer(consumed["attempt"], "E_RELEASE_IDENTITY", "consumed main workflow attempt")
    safe_run_id(consumed["authority_run_id"])
    positive_integer(consumed["artifact_id"], "E_RELEASE_IDENTITY", "consumed main artifact id")
    require(type(consumed["artifact_name"]) is str and bool(consumed["artifact_name"]), "E_RELEASE_IDENTITY", "consumed main artifact name differs")
    require(type(consumed["artifact_digest"]) is str and DOCKER_IMAGE_ID.fullmatch(consumed["artifact_digest"]) is not None, "E_RELEASE_IDENTITY", "consumed main artifact digest differs")
    for key in ("run_api_sha256", "jobs_api_sha256", "artifacts_api_sha256"):
        hex64(consumed[key], "E_RELEASE_IDENTITY", f"consumed main {key}")
    tested = exact_keys(value["tested_assets"], RELEASE_ASSET_FIELDS, "E_RELEASE_ASSETS", "release dry-run tested assets")
    for key in ("jar_sha256", "archive_sha256", "archive_manifest_sha256", "archive_digest_sha256", "package_manifest_sha256", "image_manifest_sha256", "release_assets_sha256"):
        hex64(tested[key], "E_RELEASE_ASSETS", f"release tested asset {key}")
    require(type(tested["gate_image_id"]) is str and DOCKER_IMAGE_ID.fullmatch(tested["gate_image_id"]) is not None, "E_RELEASE_ASSETS", "release gate image id differs")
    gate_base = validate_runtime_base_identity(
        tested["gate_base_image"], "release gate runtime base identity"
    )
    dry_image = exact_keys(value["dry_run_image"], RELEASE_IMAGE_FIELDS, "E_RELEASE_IMAGE", "release dry-run image")
    dry_base = validate_runtime_base_identity(
        dry_image["base_image"], "release dry-run runtime base identity"
    )
    require(
        type(dry_image["image_id"]) is str
        and DOCKER_IMAGE_ID.fullmatch(dry_image["image_id"]) is not None
        and type(dry_image["embedded_jar_sha256"]) is str
        and HEX64.fullmatch(dry_image["embedded_jar_sha256"]) is not None
        and dry_image["context_files"] == ["Dockerfile", "app.jar"]
        and dry_image["status"] == "passed",
        "E_RELEASE_JAR",
        "release dry-run image/JAR shape differs",
    )
    require(
        gate_base == dry_base,
        "E_RELEASE_BASE_IMAGE",
        "release gate/dry-run runtime base identity splice detected",
    )
    return {
        "value": value,
        "sha256": hashlib.sha256(raw).hexdigest(),
        "workflow_run_id": release_run_id,
        "attempt": release_attempt,
    }


def validate_release_dry_run(
    path: Path,
    run_id: str,
    git_head: str,
    artifacts: dict[str, Any],
    main_ci: dict[str, Any],
) -> dict[str, Any]:
    shaped = validate_release_receipt_shape(path, git_head)
    value = shaped["value"]
    consumed = value["consumed_main_authority"]
    expected_main_artifact_name = f"v934-tested-release-assets-{git_head}-{main_ci['workflow_run_id']}-{main_ci['attempt']}"
    main_artifact = main_ci["artifacts"][expected_main_artifact_name]
    require(
        consumed
        == {
            "workflow_run_id": main_ci["workflow_run_id"],
            "attempt": main_ci["attempt"],
            "authority_run_id": run_id,
            "artifact_id": main_artifact["id"],
            "artifact_name": expected_main_artifact_name,
            "artifact_digest": main_artifact["digest"],
            "run_api_sha256": main_ci["raw_hashes"]["run"],
            "jobs_api_sha256": main_ci["raw_hashes"]["jobs"],
            "artifacts_api_sha256": main_ci["raw_hashes"]["artifacts"],
        },
        "E_RELEASE_CONSUMED_MAIN",
        "release did not consume exact main authority artifact/API identity",
    )
    tested = value["tested_assets"]
    reconstructed_release_assets_sha256 = hashlib.sha256(
        release_assets_preimage(
            run_id,
            git_head,
            main_ci["workflow_run_id"],
            main_ci["attempt"],
            artifacts,
        )
    ).hexdigest()
    expected = {
        "jar_sha256": artifacts["launcher_jar_sha256"],
        "archive_sha256": artifacts["archive_sha256"],
        "archive_manifest_sha256": artifacts["archive_manifest_sha256"],
        "archive_digest_sha256": artifacts["archive_digest_sha256"],
        "package_manifest_sha256": artifacts["package_manifest_sha256"],
        "image_manifest_sha256": artifacts["image_manifest_sha256"],
        "release_assets_sha256": reconstructed_release_assets_sha256,
        "gate_image_id": artifacts["image_id"],
        "gate_base_image": artifacts["base_image"],
    }
    require(all(tested[key] == expected[key] for key in expected), "E_RELEASE_ASSETS", "release dry-run tested asset binding differs")
    dry_image = value["dry_run_image"]
    require(
        type(dry_image["image_id"]) is str
        and DOCKER_IMAGE_ID.fullmatch(dry_image["image_id"]) is not None
        and dry_image["embedded_jar_sha256"] == artifacts["launcher_jar_sha256"]
        and dry_image["context_files"] == ["Dockerfile", "app.jar"]
        and dry_image["base_image"] == artifacts["base_image"]
        and dry_image["status"] == "passed",
        "E_RELEASE_JAR",
        "release dry-run image/JAR binding differs",
    )
    return {
        "sha256": shaped["sha256"],
        "workflow_run_id": shaped["workflow_run_id"],
        "attempt": shaped["attempt"],
    }


def build_release_platform_receipt(
    args: argparse.Namespace, failpoint: str | None = None
) -> dict[str, Any]:
    load_promotion_contract()
    evidence = real_directory(args.evidence_dir, "Step 7 receipt evidence directory")
    git_head = hex40(args.git_head, "E_GIT", "final Git HEAD")
    release = validate_release_receipt_shape(evidence / "release-dry-run-receipt.json", git_head)
    run_id = release["workflow_run_id"]
    attempt = release["attempt"]
    run_result = validate_run_api(
        evidence / "release-run-api.json",
        workflow_run_id=run_id,
        attempt=attempt,
        event_name="workflow_dispatch",
        platform_head_sha=git_head,
        workflow_file=".github/workflows/release.yml",
        pull_request_number=None,
    )
    jobs_result = validate_jobs_api(
        evidence / "release-jobs-api.json",
        workflow_run_id=run_id,
        attempt=attempt,
        platform_head_sha=git_head,
        required_names=None,
        required_success_name="Same-tested JAR/archive/image dry run",
    )
    validate_release_job_set(jobs_result)
    artifact_name = f"v934-release-dry-run-{git_head}-{run_id}-{attempt}"
    artifacts_result = validate_artifacts_api(
        evidence / "release-artifacts-api.json",
        workflow_run_id=run_id,
        platform_head_sha=git_head,
        head_branch=run_result["head_branch"],
        attempt=attempt,
        expected_names=(artifact_name,),
    )
    dry_artifact = artifacts_result["artifacts"][artifact_name]
    transport = validate_artifact_transport(
        evidence, "release", run_id, attempt, dry_artifact
    )
    require(
        transport["member_sha256"] == release["sha256"],
        "E_ARTIFACT_TRANSPORT_MEMBER",
        "release artifact member digest differs from the dry-run receipt",
    )
    dry_job = jobs_result["jobs"]["Same-tested JAR/archive/image dry run"]
    receipt = {
        "schema_version": 1,
        "kind": "v934-release-platform-receipt",
        "status": "passed",
        "version": VERSION,
        "repository": REPOSITORY_FULL_NAME,
        "workflow_file": ".github/workflows/release.yml",
        "event_name": "workflow_dispatch",
        "workflow_run_id": run_id,
        "attempt": attempt,
        "commit_sha": git_head,
        "dry_run_job": {"id": dry_job["id"], "name": dry_job["name"], "conclusion": "success"},
        "dry_run_artifact": platform_artifact_projection(dry_artifact),
        "dry_run_receipt_sha256": release["sha256"],
        "dry_run_artifact_transport_sha256": transport["sha256"],
        "run_api_sha256": run_result["sha256"],
        "jobs_api_sha256": jobs_result["sha256"],
        "artifacts_api_sha256": artifacts_result["sha256"],
    }
    require(tuple(receipt) == RELEASE_PLATFORM_FIELDS, "E_RECEIPT_BUILD", "release platform receipt field order differs")
    output = evidence / "release-platform-receipt.json"
    payload = canonical_json(receipt)
    atomic_replace_bytes(output, payload, failpoint=failpoint)
    return {
        "command": "build-release-platform-receipt",
        "path": str(output),
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "passed",
        "workflow_run_id": run_id,
    }


def validate_release_platform(evidence: Path, git_head: str, release: dict[str, Any]) -> dict[str, Any]:
    value, raw = parse_json(evidence / "release-platform-receipt.json", "release platform receipt")
    exact_keys(value, RELEASE_PLATFORM_FIELDS, "E_RELEASE_PLATFORM", "release platform receipt")
    run_id = positive_integer(value["workflow_run_id"], "E_RELEASE_PLATFORM", "release platform workflow run id")
    attempt = positive_integer(value["attempt"], "E_RELEASE_PLATFORM", "release platform attempt")
    require(
        type(value["schema_version"]) is int
        and value["schema_version"] == 1
        and value["kind"] == "v934-release-platform-receipt"
        and value["status"] == "passed"
        and value["version"] == VERSION
        and value["repository"] == REPOSITORY_FULL_NAME
        and value["workflow_file"] == ".github/workflows/release.yml"
        and value["event_name"] == "workflow_dispatch"
        and value["commit_sha"] == git_head
        and run_id == release["workflow_run_id"]
        and attempt == release["attempt"],
        "E_RELEASE_PLATFORM",
        "release platform workflow identity differs",
    )
    run_result = validate_run_api(
        evidence / "release-run-api.json",
        workflow_run_id=run_id,
        attempt=attempt,
        event_name="workflow_dispatch",
        platform_head_sha=git_head,
        workflow_file=".github/workflows/release.yml",
        pull_request_number=None,
    )
    jobs_result = validate_jobs_api(
        evidence / "release-jobs-api.json",
        workflow_run_id=run_id,
        attempt=attempt,
        platform_head_sha=git_head,
        required_names=None,
        required_success_name="Same-tested JAR/archive/image dry run",
    )
    validate_release_job_set(jobs_result)
    artifact_name = f"v934-release-dry-run-{git_head}-{run_id}-{attempt}"
    artifacts_result = validate_artifacts_api(
        evidence / "release-artifacts-api.json",
        workflow_run_id=run_id,
        platform_head_sha=git_head,
        head_branch=run_result["head_branch"],
        attempt=attempt,
        expected_names=(artifact_name,),
    )
    require(
        value["run_api_sha256"] == run_result["sha256"]
        and value["jobs_api_sha256"] == jobs_result["sha256"]
        and value["artifacts_api_sha256"] == artifacts_result["sha256"],
        "E_API_DIGEST",
        "release raw API preimage digest differs",
    )
    dry_job = jobs_result["jobs"]["Same-tested JAR/archive/image dry run"]
    require(
        value["dry_run_job"]
        == {"id": dry_job["id"], "name": dry_job["name"], "conclusion": "success"},
        "E_RELEASE_JOB",
        "release dry-run job ID/name/result differs",
    )
    dry_artifact = artifacts_result["artifacts"][artifact_name]
    require(value["dry_run_artifact"] == platform_artifact_projection(dry_artifact), "E_RELEASE_ARTIFACT", "release dry-run artifact ID/name/digest differs")
    require(value["dry_run_receipt_sha256"] == release["sha256"], "E_RELEASE_ARTIFACT", "release platform receipt does not bind dry-run receipt bytes")
    transport = validate_artifact_transport(
        evidence, "release", run_id, attempt, dry_artifact
    )
    require(
        value["dry_run_artifact_transport_sha256"] == transport["sha256"]
        and transport["member_sha256"] == release["sha256"],
        "E_ARTIFACT_TRANSPORT_MEMBER",
        "release platform receipt is not bound to its downloaded dry-run member",
    )
    return {"sha256": hashlib.sha256(raw).hexdigest(), "workflow_run_id": run_id}


def parse_frontmatter(path: Path, label: str) -> tuple[dict[str, str], bytes]:
    raw = regular_bytes(path, label, 8 * 1024 * 1024)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        reject("E_REVIEW_FRONTMATTER", f"{label} is not UTF-8: {exc}")
    lines = text.splitlines()
    require(lines and lines[0] == "---", "E_REVIEW_FRONTMATTER", f"{label} frontmatter start differs")
    try:
        end = lines.index("---", 1)
    except ValueError:
        reject("E_REVIEW_FRONTMATTER", f"{label} frontmatter end is missing")
    require(end > 1 and any(line.strip() for line in lines[end + 1 :]), "E_REVIEW_FRONTMATTER", f"{label} body is empty")
    values: dict[str, str] = {}
    for number, line in enumerate(lines[1:end], 2):
        require(bool(line) and ":" in line, "E_REVIEW_FRONTMATTER", f"malformed {label} frontmatter row {number}")
        key, value = line.split(":", 1)
        require(ENV_KEY.fullmatch(key) is not None and key not in values, "E_REVIEW_FRONTMATTER", f"invalid/duplicate {label} frontmatter key")
        value = value.strip()
        require(bool(value) and not any(ord(character) < 32 or ord(character) == 127 for character in value), "E_REVIEW_FRONTMATTER", f"invalid {label} frontmatter value")
        if len(value) >= 2 and value[0] == value[-1] == '"':
            try:
                decoded = json.loads(value)
            except json.JSONDecodeError as exc:
                reject("E_REVIEW_FRONTMATTER", f"invalid quoted {label} value: {exc}")
            require(type(decoded) is str, "E_REVIEW_FRONTMATTER", f"non-string {label} frontmatter value")
            value = decoded
        elif len(value) >= 2 and value[0] == value[-1] == "'":
            value = value[1:-1].replace("''", "'")
        values[key] = value
    return values, raw


def validate_review_chain(
    evidence: Path,
    run_id: str,
    git_head: str,
    candidate_sha256: str,
    summary_sha256: str,
) -> dict[str, str]:
    common = {
        "schema_version": "1",
        "version": VERSION,
        "authority_run_id": run_id,
        "git_head": git_head,
        "authority_candidate_sha256": candidate_sha256,
        "summary_sha256": summary_sha256,
    }
    quality, quality_raw = parse_frontmatter(evidence / "version-implementation-quality.md", "version implementation quality")
    expected_quality = {
        **common,
        "kind": "v934-version-implementation-quality",
        "status": "reviewed",
        "decision": "ready-for-coverage-audit",
    }
    require(quality == expected_quality, "E_REVIEW_ORDER", "version implementation quality frontmatter differs")
    quality_sha = hashlib.sha256(quality_raw).hexdigest()

    coverage, coverage_raw = parse_frontmatter(evidence / "version-coverage-audit.md", "version coverage audit")
    expected_coverage = {
        **common,
        "kind": "v934-version-coverage-audit",
        "status": "reviewed",
        "decision": "ready-for-signoff",
        "quality_sha256": quality_sha,
    }
    require(coverage == expected_coverage, "E_REVIEW_ORDER", "version coverage audit does not follow quality review")
    coverage_sha = hashlib.sha256(coverage_raw).hexdigest()

    signoff, signoff_raw = parse_frontmatter(evidence / "version-signoff.md", "version signoff")
    expected_signoff = {
        **common,
        "kind": "v934-version-signoff",
        "status": "signed-off",
        "decision": "accepted",
        "quality_sha256": quality_sha,
        "coverage_sha256": coverage_sha,
    }
    require(signoff == expected_signoff, "E_SIGNOFF", "version signoff is not accepted after quality/coverage")
    return {
        "quality_review_sha256": quality_sha,
        "coverage_audit_sha256": coverage_sha,
        "version_signoff_sha256": hashlib.sha256(signoff_raw).hexdigest(),
    }


def final_context(
    repo_root: Path,
    target_root: Path,
    run_root: Path,
    run_id: str,
    git_head: str,
    repository_binding: dict[str, Any] | None = None,
) -> tuple[Path, bytes, dict[str, str]]:
    contract, contract_sha = load_promotion_contract()
    target = real_directory(target_root, "release target root")
    runs = real_directory(target / "runs", "release runs root")
    run = real_directory(run_root, "release run root")
    safe_run_id(run_id)
    require(run == runs / run_id, "E_RUN_ROOT", "release run root identity differs")
    hex40(git_head, "E_GIT", "final Git HEAD")

    summary_path = run / "summary.env"
    summary = parse_env(summary_path, "release summary")
    require(summary.get("mode") == "authority", "E_FINAL_AUTHORITY", "rehearsal candidate cannot be promoted")
    repository = (
        observe_repository(repo_root, git_head, summary["source_before_sha256"], contract_sha)
        if repository_binding is None
        else validate_repository_binding(
            repository_binding, git_head, summary["source_before_sha256"], contract_sha
        )
    )

    candidate, candidate_payload, candidate_values = pointer_context(
        target, run, run_id, git_head, "authority"
    )
    require(candidate.name == AUTHORITY_CANDIDATE, "E_FINAL_AUTHORITY", "final promotion candidate scope differs")
    require(same_regular_bytes(candidate, candidate_payload), "E_FINAL_AUTHORITY", "authority candidate pointer differs")
    require(
        same_regular_bytes(run / "published-candidate-pointer.env", candidate_payload),
        "E_FINAL_AUTHORITY",
        "uploaded main authority candidate pointer differs",
    )
    candidate_sha = hashlib.sha256(candidate_payload).hexdigest()
    summary_sha = sha256_file(summary_path, "release summary")
    artifacts = validate_release_artifacts(run, summary)
    require(
        candidate_values["archive_sha256"] == artifacts["archive_sha256"]
        and candidate_values["launcher_jar_sha256"] == artifacts["launcher_jar_sha256"],
        "E_FINAL_AUTHORITY",
        "authority candidate artifact binding differs",
    )

    evidence = exact_evidence_directory(target, run_id)
    portable = validate_portable_replay(
        evidence / "portable-replay.json",
        run_id,
        git_head,
        summary["source_before_sha256"],
        artifacts,
    )
    pr_ci = validate_ci_receipt(
        evidence,
        "pr",
        event_name="pull_request",
        gate_mode="rehearsal",
        authority_run_id=None,
        git_head=None,
        final_head=git_head,
    )
    main_ci = validate_ci_receipt(
        evidence,
        "main",
        event_name="push",
        gate_mode="authority",
        authority_run_id=run_id,
        git_head=git_head,
        final_head=git_head,
    )
    branch = validate_branch_protection(evidence, git_head)
    release = validate_release_dry_run(
        evidence / "release-dry-run-receipt.json", run_id, git_head, artifacts, main_ci
    )
    release_platform = validate_release_platform(evidence, git_head, release)
    reviews = validate_review_chain(evidence, run_id, git_head, candidate_sha, summary_sha)

    values = {
        "schema_version": "1",
        "kind": "v934-final-authority",
        "status": "accepted",
        "version": VERSION,
        "authority_run_id": run_id,
        "git_head": git_head,
        "source_sha256": summary["source_before_sha256"],
        "repository_origin": repository["origin"],
        "tooling_manifest_sha256": repository["tooling_manifest_sha256"],
        "promotion_tool_sha256": repository["promotion_tool_sha256"],
        "promotion_contract_sha256": contract_sha,
        "authority_candidate_sha256": candidate_sha,
        "summary_sha256": summary_sha,
        "archive_sha256": artifacts["archive_sha256"],
        "archive_manifest_sha256": artifacts["archive_manifest_sha256"],
        "archive_digest_sha256": artifacts["archive_digest_sha256"],
        "launcher_jar_sha256": artifacts["launcher_jar_sha256"],
        "package_manifest_sha256": artifacts["package_manifest_sha256"],
        "image_manifest_sha256": artifacts["image_manifest_sha256"],
        "runtime_base_tag_reference": artifacts["base_image"]["tag_reference"],
        "runtime_base_pinned_reference": artifacts["base_image"]["pinned_reference"],
        "runtime_base_index_digest": artifacts["base_image"]["index_digest"],
        "runtime_base_manifest_digest": artifacts["base_image"]["manifest_digest"],
        "runtime_base_config_digest": artifacts["base_image"]["config_digest"],
        "runtime_base_os": artifacts["base_image"]["platform"]["os"],
        "runtime_base_architecture": artifacts["base_image"]["platform"]["architecture"],
        "portable_replay_sha256": portable["sha256"],
        "pr_ci_receipt_sha256": pr_ci["sha256"],
        "pr_ci_commit_sha": pr_ci["commit_sha"],
        "pr_ci_platform_head_sha": pr_ci["platform_head_sha"],
        "pr_number": str(pr_ci["pull_request_number"]),
        "pr_ci_workflow_run_id": str(pr_ci["workflow_run_id"]),
        "main_ci_receipt_sha256": main_ci["sha256"],
        "main_ci_workflow_run_id": str(main_ci["workflow_run_id"]),
        "branch_protection_receipt_sha256": branch["sha256"],
        "release_dry_run_receipt_sha256": release["sha256"],
        "release_platform_receipt_sha256": release_platform["sha256"],
        "release_workflow_run_id": str(release["workflow_run_id"]),
        **reviews,
    }
    require(tuple(values) == FINAL_FIELDS and contract["final_pointer_fields"] == list(values), "E_FINAL_SCHEMA", "final pointer field order differs")
    payload = "".join(f"{key}={values[key]}\n" for key in FINAL_FIELDS).encode("ascii")
    return target / FINAL_POINTER, payload, values


def promote_final(
    args: argparse.Namespace,
    failpoint: str | None = None,
    repository_binding: dict[str, Any] | None = None,
) -> dict[str, Any]:
    output, payload, values = final_context(
        args.repo_root,
        args.target_root,
        args.run_root,
        args.run_id,
        args.git_head,
        repository_binding,
    )
    require(output.name == FINAL_POINTER, "E_POINTER_SCOPE", "final promotion output path differs")
    atomic_replace_bytes(output, payload, failpoint=failpoint)
    return {
        "command": "promote-final",
        "git_head": values["git_head"],
        "path": str(output),
        "run_id": values["authority_run_id"],
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "accepted",
    }


def verify_final(
    args: argparse.Namespace, repository_binding: dict[str, Any] | None = None
) -> dict[str, Any]:
    output, payload, values = final_context(
        args.repo_root,
        args.target_root,
        args.run_root,
        args.run_id,
        args.git_head,
        repository_binding,
    )
    require(same_regular_bytes(output, payload), "E_FINAL_VERIFY", "final authority pointer differs")
    return {
        "command": "verify-final",
        "git_head": values["git_head"],
        "path": str(output),
        "run_id": values["authority_run_id"],
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "accepted",
    }


def publish(args: argparse.Namespace) -> dict[str, Any]:
    output, payload, values = pointer_context(
        args.target_root, args.run_root, args.run_id, args.git_head, args.mode
    )
    atomic_replace_bytes(output, payload)
    return {
        "command": "publish-candidate",
        "git_head": values["git_head"],
        "mode": values["mode"],
        "path": str(output),
        "run_id": values["run_id"],
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "passed",
    }


def verify(args: argparse.Namespace) -> dict[str, Any]:
    output, payload, values = pointer_context(
        args.target_root, args.run_root, args.run_id, args.git_head, args.mode
    )
    require(same_regular_bytes(output, payload), "E_POINTER_VERIFY", "candidate pointer differs")
    return {
        "command": "verify-candidate",
        "git_head": values["git_head"],
        "mode": values["mode"],
        "path": str(output),
        "run_id": values["run_id"],
        "sha256": hashlib.sha256(payload).hexdigest(),
        "status": "passed",
    }


def expect_failure(name: str, code: str, operation: Callable[[], None]) -> dict[str, str]:
    try:
        operation()
    except PointerError as exc:
        require(exc.code == code, "E_NEGATIVE", f"{name}: expected {code}, observed {exc.code}")
        return {"case": name, "expected": code, "observed": exc.code, "status": "passed"}
    reject("E_NEGATIVE", f"{name}: unexpectedly passed")


def negative(args: argparse.Namespace) -> dict[str, Any]:
    output_dir = absolute(args.output_dir)
    parent = real_directory(output_dir.parent, "negative output parent")
    require(not lexists(output_dir), "E_OUTPUT_EXISTS", "negative output exists")
    output_dir.mkdir(mode=0o755)
    fsync_directory(parent)
    target = output_dir / "target"
    run_id = "pointer-negative"
    git_head = "a" * 40
    run = target / "runs" / run_id
    (run / "bundle").mkdir(parents=True)
    (run / "package").mkdir()
    archive = run / "bundle/v934-release-evidence.tar.gz"
    jar = run / "package/app.jar"
    archive.write_bytes(b"synthetic-archive\n")
    jar.write_bytes(b"synthetic-jar\n")
    archive_sha = hashlib.sha256(archive.read_bytes()).hexdigest()
    jar_sha = hashlib.sha256(jar.read_bytes()).hexdigest()
    summary = (
        f"run_id={run_id}\nmode=rehearsal\ngit_head={git_head}\n"
        f"source_before_sha256={'b' * 64}\nsource_after_sha256={'b' * 64}\n"
        f"step4_run_id={run_id}\nlauncher_jar_sha256={jar_sha}\n"
        f"package_manifest_sha256={'c' * 64}\nimage_manifest_sha256={'d' * 64}\n"
        f"archive_sha256={archive_sha}\narchive_manifest_sha256={'e' * 64}\n"
        f"archive_digest_sha256={'f' * 64}\nportable_byte_verify=passed\n"
        "portable_semantic_replay=required-downstream\n"
        "final_authority_pointer_updated=false\nstatus=candidate-passed\n"
    ).encode("ascii")
    (run / "summary.env").write_bytes(summary)
    output, payload, _ = pointer_context(target, run, run_id, git_head, "rehearsal")
    final_pointer = target / "final-authority-run.env"
    cases: list[dict[str, str]] = []
    cases.append(
        expect_failure(
            "candidate-final-pointer-refusal",
            "E_POINTER_SCOPE",
            lambda: validate_candidate_output_name("final-authority-run.env", "rehearsal"),
        )
    )
    output.write_bytes(b"previous-pointer\n")
    previous = output.read_bytes()

    def precommit() -> None:
        try:
            atomic_replace_bytes(output, payload, failpoint="before-replace")
        except PointerError:
            require(output.read_bytes() == previous, "E_NEGATIVE", "pre-commit failure changed previous pointer")
            require(not lexists(final_pointer), "E_NEGATIVE", "candidate failure created final pointer")
            raise

    cases.append(expect_failure("failed-precommit-preserves-pointer", "E_POINTER_INJECTED", precommit))

    def postreplace() -> None:
        try:
            atomic_replace_bytes(output, payload, failpoint="after-replace-before-fsync")
        except PointerError:
            require(output.read_bytes() == previous, "E_NEGATIVE", "post-replace rollback changed previous pointer")
            require(not lexists(final_pointer), "E_NEGATIVE", "candidate rollback created final pointer")
            raise

    cases.append(expect_failure("failed-postreplace-rolls-back-pointer", "E_POINTER_ROLLED_BACK", postreplace))
    output.unlink()

    def absent_postreplace() -> None:
        try:
            atomic_replace_bytes(output, payload, failpoint="after-replace-before-fsync")
        except PointerError:
            require(not lexists(output), "E_NEGATIVE", "failed first publication left a success pointer")
            raise

    cases.append(expect_failure("failed-first-publication-remains-absent", "E_POINTER_ROLLED_BACK", absent_postreplace))
    output.symlink_to("missing")
    cases.append(
        expect_failure(
            "symlink-pointer-refusal",
            "E_POINTER_TYPE",
            lambda: atomic_replace_bytes(output, payload),
        )
    )
    output.unlink()
    atomic_replace_bytes(output, payload)
    require(output.read_bytes() == payload, "E_NEGATIVE", "positive pointer publication differs")
    require(not lexists(final_pointer), "E_NEGATIVE", "candidate publication changed final pointer")
    result = {
        "schema_version": 1,
        "kind": "v934-candidate-pointer-negative-result",
        "status": "passed",
        "cases": cases,
        "case_count": len(cases),
        "positive_sha256": hashlib.sha256(payload).hexdigest(),
        "final_pointer": "absent",
    }
    result_path = output_dir / "negative-result.json"
    result_path.write_bytes(
        (json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n").encode("utf-8")
    )
    return {
        "command": "negative",
        "cases": len(cases),
        "result": str(result_path),
        "result_sha256": hashlib.sha256(result_path.read_bytes()).hexdigest(),
        "status": "passed",
    }


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True) + "\n").encode("utf-8")


def synthetic_zip_bytes(files: dict[str, bytes]) -> bytes:
    output = io.BytesIO()
    with zipfile.ZipFile(
        output, "w", compression=zipfile.ZIP_DEFLATED, allowZip64=True
    ) as archive:
        for name in sorted(files, key=lambda value: value.encode("utf-8")):
            info = zipfile.ZipInfo(name, date_time=(2020, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            archive.writestr(info, files[name])
    return output.getvalue()


def review_document(values: dict[str, str], title: str) -> bytes:
    rows = ["---", *(f"{key}: {value}" for key, value in values.items()), "---", "", f"# {title}", "", "Accepted Step 7 governance evidence.", ""]
    return "\n".join(rows).encode("utf-8")


def synthetic_aggregate_receipt(
    workflow_run_id: int, attempt: int, commit_sha: str, authority_run_id: str
) -> dict[str, Any]:
    required_ids = ("inventory-unit", "sqlite-integration", "database-matrix", "external-integration", "coverage", "package-evidence")
    jobs = [{"job_id": job, "result": "success"} for job in required_ids]
    states = {row["job_id"]: row["result"] for row in jobs}
    databases = [
        {
            "artifact_tree_sha256": format(index + 1, "064x"),
            "authority_sha256": format(index + 11, "064x"),
            "cell_manifest_sha256": format(index + 21, "064x"),
            "db_kind": kind,
            "evidence_file_count": index + 1,
            "evidence_set_sha256": format(index + 31, "064x"),
            "logical_name": f"v934-db-{kind}-{commit_sha}-{workflow_run_id}-{attempt}",
        }
        for index, kind in enumerate(("sqlite", "mysql57", "mysql8", "postgres15", "sqlserver2022"))
    ]
    return {
        "schema_version": 1,
        "kind": "v934-ci-aggregate-receipt",
        "status": "passed",
        "contract_sha256": "8" * 64,
        "tool_sha256": "9" * 64,
        "aggregator": {"id": AGGREGATOR_ID, "name": AGGREGATOR_NAME},
        "identity": {"attempt": attempt, "authority_run_id": authority_run_id, "commit_sha": commit_sha, "workflow_run_id": workflow_run_id},
        "required_jobs": jobs,
        "job_states_sha256": hashlib.sha256(canonical_json(states)).hexdigest(),
        "database_artifacts": databases,
        "database_artifact_set_sha256": hashlib.sha256(canonical_json(databases)).hexdigest(),
    }


def synthetic_run_api(
    workflow_run_id: int,
    attempt: int,
    event: str,
    commit_sha: str,
    workflow_file: str,
    pull_number: int | None = None,
    pull_head_sha: str | None = None,
) -> dict[str, Any]:
    return {
        "id": workflow_run_id,
        "run_attempt": attempt,
        "event": event,
        "status": "completed",
        "conclusion": "success",
        "head_sha": commit_sha,
        "head_branch": "feature/final" if event == "pull_request" else "main",
        "path": workflow_file,
        "repository": {"full_name": REPOSITORY_FULL_NAME},
        "head_repository": {"full_name": REPOSITORY_FULL_NAME},
        "pull_requests": [],
    }


def synthetic_jobs_api(
    workflow_run_id: int,
    attempt: int,
    platform_head_sha: str,
    names_and_results: Sequence[tuple[str, str]],
    base_id: int,
) -> dict[str, Any]:
    jobs = [
        {
            "id": base_id + index,
            "run_id": workflow_run_id,
            "run_attempt": attempt,
            "name": name,
            "status": "completed",
            "conclusion": conclusion,
            "head_sha": platform_head_sha,
        }
        for index, (name, conclusion) in enumerate(names_and_results, 1)
    ]
    return {"total_count": len(jobs), "jobs": jobs}


def synthetic_artifacts_api(
    workflow_run_id: int,
    platform_head_sha: str,
    head_branch: str,
    names: Sequence[str],
    base_id: int,
) -> dict[str, Any]:
    rows = [
        {
            "id": base_id + index,
            "name": name,
            "size_in_bytes": 1000 + index,
            "expired": False,
            "digest": "sha256:" + format(base_id + index, "064x"),
            "workflow_run": {
                "id": workflow_run_id,
                "head_sha": platform_head_sha,
                "head_branch": head_branch,
            },
        }
        for index, name in enumerate(names, 1)
    ]
    return {"total_count": len(rows), "artifacts": rows}


def build_final_negative_fixture(output_dir: Path) -> tuple[argparse.Namespace, dict[str, Any]]:
    target = output_dir / "target"
    run_id = "final-authority-negative"
    git_head = "a" * 40
    source_sha = "b" * 64
    run = target / "runs" / run_id
    bundle = run / "bundle"
    package = run / "package"
    evidence = target / "step7" / "runs" / run_id
    bundle.mkdir(parents=True)
    package.mkdir()
    evidence.mkdir(parents=True)

    archive = bundle / "v934-release-evidence.tar.gz"
    archive.write_bytes(b"synthetic-authority-archive\n")
    archive_sha = sha256_file(archive, "negative archive")
    archive_manifest = bundle / "v934-release-evidence.archive.json"
    archive_manifest.write_bytes(canonical_json({"kind": "synthetic-archive-manifest", "status": "passed"}))
    archive_manifest_sha = sha256_file(archive_manifest, "negative archive manifest")
    archive_digest = bundle / "v934-release-evidence.tar.gz.sha256"
    archive_digest.write_bytes(f"{archive_sha}  v934-release-evidence.tar.gz\n".encode("ascii"))
    archive_digest_sha = sha256_file(archive_digest, "negative archive digest")

    jar = package / "app.jar"
    jar.write_bytes(b"synthetic-authority-jar\n")
    jar_sha = sha256_file(jar, "negative JAR")
    jar_size = len(jar.read_bytes())
    image_id = "sha256:" + "1" * 64
    base_image = runtime_base_identity()
    image_manifest = {
        "schema_version": 2,
        "kind": "v934-runtime-image-receipt",
        "status": "passed",
        "run_id": run_id,
        "dockerfile": {},
        "context": {},
        "base_image": base_image,
        "image_id": image_id,
        "tested_jar": {"path": "app.jar", "sha256": jar_sha, "size": jar_size},
        "embedded_jar": {"path": "/app/app.jar", "sha256": jar_sha, "size": jar_size},
        "cleanup": {},
        "log": {},
    }
    image_path = package / "image-manifest.json"
    image_path.write_bytes(canonical_json(image_manifest))
    image_sha = sha256_file(image_path, "negative image manifest")
    package_manifest = {
        "schema_version": 4,
        "kind": "v934-tested-output-tree-package",
        "status": "passed",
        "run_id": run_id,
        "git_head": git_head,
        "source": {},
        "step4": {},
        "reactor": {},
        "seals": {},
        "maven": {},
        "jar": {"sha256": jar_sha, "size": jar_size},
        "validation_log": {},
        "image": {"base_image": base_image},
        "cleanup": {},
    }
    package_manifest_path = package / "package-manifest.json"
    package_manifest_path.write_bytes(canonical_json(package_manifest))
    package_manifest_sha = sha256_file(package_manifest_path, "negative package manifest")
    for name in ("docker-build.log", "maven-invocations.log", "tested-tree-validation.log"):
        (package / name).write_bytes(f"synthetic {name}\n".encode("ascii"))

    summary = (
        f"run_id={run_id}\nmode=authority\ngit_head={git_head}\n"
        f"source_before_sha256={source_sha}\nsource_after_sha256={source_sha}\n"
        f"step4_run_id={run_id}\nlauncher_jar_sha256={jar_sha}\n"
        f"package_manifest_sha256={package_manifest_sha}\nimage_manifest_sha256={image_sha}\n"
        f"archive_sha256={archive_sha}\narchive_manifest_sha256={archive_manifest_sha}\n"
        f"archive_digest_sha256={archive_digest_sha}\nportable_byte_verify=passed\n"
        "portable_semantic_replay=required-downstream\n"
        "final_authority_pointer_updated=false\nstatus=candidate-passed\n"
    ).encode("ascii")
    summary_path = run / "summary.env"
    summary_path.write_bytes(summary)
    candidate, candidate_payload, _ = pointer_context(target, run, run_id, git_head, "authority")
    atomic_replace_bytes(candidate, candidate_payload)
    (run / "published-candidate-pointer.env").write_bytes(candidate_payload)
    candidate_sha = hashlib.sha256(candidate_payload).hexdigest()
    summary_sha = hashlib.sha256(summary).hexdigest()

    portable_files: dict[str, dict[str, Any]] = {}
    for name in (
        "app.jar",
        "docker-build.log",
        "image-manifest.json",
        "maven-invocations.log",
        "package-manifest.json",
        "tested-tree-validation.log",
    ):
        path = package / name
        portable_files[name] = {"sha256": sha256_file(path, f"negative portable {name}"), "size": len(path.read_bytes())}
    portable = {
        "schema_version": 1,
        "kind": "v934-portable-release-replay",
        "status": "passed",
        "run_id": run_id,
        "release_mode": "authority",
        "git_head": git_head,
        "source_sha256": source_sha,
        "fixture_run_id": "fixture-negative",
        "input": {},
        "contract_freeze": {},
        "fixture_manifest": {},
        "package": {"files": portable_files, "manifest_kind": "v934-tested-output-tree-package", "tested_classes": {}},
        "materialized": {},
        "subprocesses": {},
        "step4": {},
    }
    (evidence / "portable-replay.json").write_bytes(canonical_json(portable))

    pr_run_id, main_run_id, release_run_id = 101, 102, 103
    attempt = 2
    pr_commit = "c" * 40
    pr_head = "d" * 40
    pr_number = 77

    def write_json(name: str, value: dict[str, Any]) -> str:
        path = evidence / name
        path.write_bytes(canonical_json(value))
        return sha256_file(path, f"synthetic {name}")

    ci_records: dict[str, dict[str, Any]] = {}
    for prefix, event, mode, commit, authority, workflow_run, base_id, pull in (
        ("pr", "pull_request", "rehearsal", pr_commit, "pr-rehearsal-negative", pr_run_id, 1000, pr_number),
        ("main", "push", "authority", git_head, run_id, main_run_id, 2000, None),
    ):
        platform_commit = pr_head if prefix == "pr" else commit
        head_branch = "feature/final" if prefix == "pr" else "main"
        run_value = synthetic_run_api(workflow_run, attempt, event, platform_commit, CI_WORKFLOW, pull, pr_head if pull else None)
        run_sha = write_json(f"{prefix}-run-api.json", run_value)
        jobs_value = synthetic_jobs_api(workflow_run, attempt, platform_commit, [(name, "success") for name in REQUIRED_JOB_NAMES], base_id)
        jobs_sha = write_json(f"{prefix}-jobs-api.json", jobs_value)
        aggregate_value = synthetic_aggregate_receipt(workflow_run, attempt, commit, authority)
        aggregate_raw = canonical_json(aggregate_value)
        aggregate_sha = write_json(f"{prefix}-aggregate-receipt.json", aggregate_value)
        required_zip = evidence / ARTIFACT_TRANSPORT_SPECS[prefix]["archive_file"]
        required_zip.write_bytes(
            synthetic_zip_bytes(
                {
                    "aggregator/required-aggregate-receipt.json": aggregate_raw,
                    "aggregator/required-job-states.json": canonical_json(
                        {"synthetic": "required-job-states"}
                    ),
                    "negative-contract/negative-result.json": canonical_json(
                        {"status": "passed", "synthetic": "negative-contract"}
                    ),
                }
            )
        )
        artifact_names = expected_ci_artifact_names(commit, workflow_run, attempt)
        artifacts_value = synthetic_artifacts_api(workflow_run, platform_commit, head_branch, artifact_names, base_id + 100)
        required_name = f"v934-required-receipt-{commit}-{workflow_run}-{attempt}"
        artifact_row = next(row for row in artifacts_value["artifacts"] if row["name"] == required_name)
        artifact_row["digest"] = "sha256:" + sha256_file(
            required_zip, f"synthetic {prefix} required artifact ZIP"
        )
        artifacts_sha = write_json(f"{prefix}-artifacts-api.json", artifacts_value)
        inspection = inspect_artifact_transport_archive(
            required_zip, ARTIFACT_TRANSPORT_SPECS[prefix], artifact_row
        )
        transport_value = artifact_transport_receipt_value(
            prefix, workflow_run, attempt, artifact_row, inspection
        )
        transport_sha = write_json(
            ARTIFACT_TRANSPORT_SPECS[prefix]["receipt_file"], transport_value
        )
        aggregator_job = next(row for row in jobs_value["jobs"] if row["name"] == AGGREGATOR_NAME)
        ci_records[prefix] = {
            "run_sha": run_sha,
            "jobs_sha": jobs_sha,
            "artifacts_sha": artifacts_sha,
            "artifacts": {row["name"]: row for row in artifacts_value["artifacts"]},
            "workflow_run_id": workflow_run,
            "attempt": attempt,
        }
        if prefix == "pr":
            pr_api = {
                "number": pr_number,
                "state": "closed",
                "merged": True,
                "merge_commit_sha": git_head,
                "head": {"sha": pr_head, "ref": "feature/final", "repo": {"full_name": REPOSITORY_FULL_NAME}},
                "base": {"ref": "main", "repo": {"full_name": REPOSITORY_FULL_NAME}},
            }
            pr_api_sha: str | None = write_json("pr-api.json", pr_api)
        else:
            pr_api_sha = None
        ci_receipt = {
            "schema_version": 1,
            "kind": "v934-platform-ci-receipt",
            "status": "passed",
            "version": VERSION,
            "repository": REPOSITORY_FULL_NAME,
            "event_name": event,
            "workflow_file": CI_WORKFLOW,
            "workflow_run_id": workflow_run,
            "attempt": attempt,
            "commit_sha": commit,
            "platform_head_sha": platform_commit,
            "authority_run_id": authority,
            "gate_mode": mode,
            "pull_request_number": pull,
            "aggregator": {"id": AGGREGATOR_ID, "name": AGGREGATOR_NAME, "job_id": aggregator_job["id"], "conclusion": "success"},
            "aggregate_receipt_sha256": aggregate_sha,
            "required_receipt_artifact": platform_artifact_projection(artifact_row),
            "required_artifact_transport_sha256": transport_sha,
            "run_api_sha256": run_sha,
            "jobs_api_sha256": jobs_sha,
            "artifacts_api_sha256": artifacts_sha,
            "pull_request_api_sha256": pr_api_sha,
        }
        write_json(f"{prefix}-ci-receipt.json", ci_receipt)

    branch_api = {
        "url": f"https://api.github.com/repos/{REPOSITORY_FULL_NAME}/branches/main/protection",
        "required_status_checks": {
            "url": f"https://api.github.com/repos/{REPOSITORY_FULL_NAME}/branches/main/protection/required_status_checks",
            "strict": True,
            "contexts": [AGGREGATOR_NAME],
            "checks": [{"context": AGGREGATOR_NAME, "app_id": 1}],
        },
        "enforce_admins": {"enabled": True},
    }
    branch_api_sha = write_json("branch-protection-api.json", branch_api)
    branch = {
        "schema_version": 1,
        "kind": "v934-branch-protection-receipt",
        "status": "passed",
        "version": VERSION,
        "repository": REPOSITORY_FULL_NAME,
        "branch": "main",
        "commit_sha": git_head,
        "strict": True,
        "enforce_admins": True,
        "required_contexts": [AGGREGATOR_NAME],
        "github_api_response_sha256": branch_api_sha,
    }
    write_json("branch-protection-receipt.json", branch)

    main_release_name = f"v934-tested-release-assets-{git_head}-{main_run_id}-{attempt}"
    main_release_artifact = ci_records["main"]["artifacts"][main_release_name]
    fixture_release_artifacts = {
        "launcher_jar_sha256": jar_sha,
        "archive_sha256": archive_sha,
        "archive_manifest_sha256": archive_manifest_sha,
        "archive_digest_sha256": archive_digest_sha,
        "package_manifest_sha256": package_manifest_sha,
        "image_manifest_sha256": image_sha,
        "maven_log_sha256": sha256_file(
            package / "maven-invocations.log", "negative Maven log"
        ),
        "docker_log_sha256": sha256_file(
            package / "docker-build.log", "negative Docker log"
        ),
        "tested_tree_log_sha256": sha256_file(
            package / "tested-tree-validation.log", "negative tested-tree log"
        ),
    }
    release_assets_sha = hashlib.sha256(
        release_assets_preimage(
            run_id,
            git_head,
            main_run_id,
            attempt,
            fixture_release_artifacts,
        )
    ).hexdigest()
    release = {
        "schema_version": 3,
        "kind": "v934-release-dry-run",
        "status": "passed",
        "version": VERSION,
        "commit_sha": git_head,
        "gate_mode": "authority",
        "release_workflow": {"run_id": release_run_id, "attempt": attempt, "event_name": "workflow_dispatch"},
        "consumed_main_authority": {
            "workflow_run_id": main_run_id,
            "attempt": attempt,
            "authority_run_id": run_id,
            "artifact_id": main_release_artifact["id"],
            "artifact_name": main_release_name,
            "artifact_digest": main_release_artifact["digest"],
            "run_api_sha256": ci_records["main"]["run_sha"],
            "jobs_api_sha256": ci_records["main"]["jobs_sha"],
            "artifacts_api_sha256": ci_records["main"]["artifacts_sha"],
        },
        "tested_assets": {
            "jar_sha256": jar_sha,
            "archive_sha256": archive_sha,
            "archive_manifest_sha256": archive_manifest_sha,
            "archive_digest_sha256": archive_digest_sha,
            "package_manifest_sha256": package_manifest_sha,
            "image_manifest_sha256": image_sha,
            "release_assets_sha256": release_assets_sha,
            "gate_image_id": image_id,
            "gate_base_image": base_image,
        },
        "dry_run_image": {
            "image_id": "sha256:" + "6" * 64,
            "embedded_jar_sha256": jar_sha,
            "context_files": ["Dockerfile", "app.jar"],
            "base_image": base_image,
            "status": "passed",
        },
        "publish_performed": False,
    }
    release_raw = canonical_json(release)
    release_receipt_sha = write_json("release-dry-run-receipt.json", release)
    release_run_sha = write_json("release-run-api.json", synthetic_run_api(release_run_id, attempt, "workflow_dispatch", git_head, ".github/workflows/release.yml"))
    release_jobs_value = synthetic_jobs_api(
        release_run_id,
        attempt,
        git_head,
        [
            ("Same-tested JAR/archive/image dry run", "success"),
            ("Publish verified runtime-only image", "skipped"),
            ("Publish same-tested GitHub release assets", "skipped"),
        ],
        3000,
    )
    release_jobs_sha = write_json("release-jobs-api.json", release_jobs_value)
    release_artifact_name = f"v934-release-dry-run-{git_head}-{release_run_id}-{attempt}"
    release_zip = evidence / ARTIFACT_TRANSPORT_SPECS["release"]["archive_file"]
    release_zip.write_bytes(
        synthetic_zip_bytes(
            {
                "main-artifacts.json": (
                    evidence / "main-artifacts-api.json"
                ).read_bytes(),
                "main-jobs.json": (evidence / "main-jobs-api.json").read_bytes(),
                "main-run.json": (evidence / "main-run-api.json").read_bytes(),
                "receipt.json": release_raw,
            }
        )
    )
    release_artifacts_value = synthetic_artifacts_api(release_run_id, git_head, "main", (release_artifact_name,), 3100)
    release_artifact_row = release_artifacts_value["artifacts"][0]
    release_artifact_row["digest"] = "sha256:" + sha256_file(
        release_zip, "synthetic release dry-run artifact ZIP"
    )
    release_artifacts_sha = write_json("release-artifacts-api.json", release_artifacts_value)
    release_transport_inspection = inspect_artifact_transport_archive(
        release_zip, ARTIFACT_TRANSPORT_SPECS["release"], release_artifact_row
    )
    release_transport_value = artifact_transport_receipt_value(
        "release",
        release_run_id,
        attempt,
        release_artifact_row,
        release_transport_inspection,
    )
    release_transport_sha = write_json(
        ARTIFACT_TRANSPORT_SPECS["release"]["receipt_file"],
        release_transport_value,
    )
    dry_job = release_jobs_value["jobs"][0]
    dry_artifact = release_artifact_row
    release_platform = {
        "schema_version": 1,
        "kind": "v934-release-platform-receipt",
        "status": "passed",
        "version": VERSION,
        "repository": REPOSITORY_FULL_NAME,
        "workflow_file": ".github/workflows/release.yml",
        "event_name": "workflow_dispatch",
        "workflow_run_id": release_run_id,
        "attempt": attempt,
        "commit_sha": git_head,
        "dry_run_job": {"id": dry_job["id"], "name": dry_job["name"], "conclusion": "success"},
        "dry_run_artifact": platform_artifact_projection(dry_artifact),
        "dry_run_receipt_sha256": release_receipt_sha,
        "dry_run_artifact_transport_sha256": release_transport_sha,
        "run_api_sha256": release_run_sha,
        "jobs_api_sha256": release_jobs_sha,
        "artifacts_api_sha256": release_artifacts_sha,
    }
    write_json("release-platform-receipt.json", release_platform)

    common = {
        "schema_version": "1",
        "kind": "v934-version-implementation-quality",
        "version": VERSION,
        "status": "reviewed",
        "decision": "ready-for-coverage-audit",
        "authority_run_id": run_id,
        "git_head": git_head,
        "authority_candidate_sha256": candidate_sha,
        "summary_sha256": summary_sha,
    }
    quality_path = evidence / "version-implementation-quality.md"
    quality_path.write_bytes(review_document(common, "Version Implementation Quality"))
    quality_sha = sha256_file(quality_path, "negative quality review")
    coverage_fields = {
        **common,
        "kind": "v934-version-coverage-audit",
        "decision": "ready-for-signoff",
        "quality_sha256": quality_sha,
    }
    coverage_path = evidence / "version-coverage-audit.md"
    coverage_path.write_bytes(review_document(coverage_fields, "Version Coverage Audit"))
    coverage_sha = sha256_file(coverage_path, "negative coverage audit")
    signoff_fields = {
        **common,
        "kind": "v934-version-signoff",
        "status": "signed-off",
        "decision": "accepted",
        "quality_sha256": quality_sha,
        "coverage_sha256": coverage_sha,
    }
    (evidence / "version-signoff.md").write_bytes(review_document(signoff_fields, "Version Signoff"))
    namespace = argparse.Namespace(repo_root=Path.cwd(), target_root=target, run_root=run, run_id=run_id, git_head=git_head)
    repository_binding = {
        "repository": REPOSITORY_FULL_NAME,
        "origin": CANONICAL_ORIGINS[0],
        "git_head": git_head,
        "local_main": git_head,
        "live_main": git_head,
        "source_sha256": source_sha,
        "clean": True,
        "tooling_manifest_sha256": "7" * 64,
        "promotion_tool_sha256": sha256_file(Path(__file__), "synthetic current promotion tool", 16 * 1024 * 1024),
        "promotion_contract_sha256": sha256_file(CONTRACT_PATH, "synthetic current promotion contract", 4 * 1024 * 1024),
    }
    return namespace, {
        "archive": archive,
        "summary": summary_path,
        "portable": evidence / "portable-replay.json",
        "pr": evidence / "pr-ci-receipt.json",
        "pr_api": evidence / "pr-api.json",
        "pr_run_api": evidence / "pr-run-api.json",
        "pr_jobs_api": evidence / "pr-jobs-api.json",
        "pr_artifacts_api": evidence / "pr-artifacts-api.json",
        "pr_aggregate": evidence / "pr-aggregate-receipt.json",
        "main": evidence / "main-ci-receipt.json",
        "main_run_api": evidence / "main-run-api.json",
        "main_jobs_api": evidence / "main-jobs-api.json",
        "main_artifacts_api": evidence / "main-artifacts-api.json",
        "main_aggregate": evidence / "main-aggregate-receipt.json",
        "main_transport": evidence / "main-required-artifact-transport.json",
        "main_zip": evidence / "main-required-artifact.zip",
        "pr_transport": evidence / "pr-required-artifact-transport.json",
        "pr_zip": evidence / "pr-required-artifact.zip",
        "release": evidence / "release-dry-run-receipt.json",
        "release_platform": evidence / "release-platform-receipt.json",
        "release_transport": evidence / "release-dry-run-artifact-transport.json",
        "release_zip": evidence / "release-dry-run-artifact.zip",
        "branch_api": evidence / "branch-protection-api.json",
        "coverage": coverage_path,
        "evidence": evidence,
        "final": target / FINAL_POINTER,
        "repository_binding": repository_binding,
    }


def final_negative(args: argparse.Namespace) -> dict[str, Any]:
    output_dir = absolute(args.output_dir)
    parent = real_directory(output_dir.parent, "final negative output parent")
    require(not lexists(output_dir), "E_OUTPUT_EXISTS", "final negative output exists")
    output_dir.mkdir(mode=0o755)
    fsync_directory(parent)
    fixture, paths = build_final_negative_fixture(output_dir)
    cases: list[dict[str, str]] = []
    repository_binding = paths["repository_binding"]

    def mutate_bytes(path: Path, replacement: bytes, operation: Callable[[], None]) -> None:
        previous = path.read_bytes()
        path.write_bytes(replacement)
        try:
            operation()
        finally:
            if lexists(path) and not path.is_symlink():
                path.write_bytes(previous)

    def mutate_many(replacements: Sequence[tuple[Path, bytes]], operation: Callable[[], None]) -> None:
        previous = [(path, path.read_bytes()) for path, _ in replacements]
        for path, replacement in replacements:
            path.write_bytes(replacement)
        try:
            operation()
        finally:
            for path, payload in previous:
                if lexists(path) and not path.is_symlink():
                    path.write_bytes(payload)

    def context(binding: dict[str, Any] | None = None) -> None:
        final_context(
            fixture.repo_root,
            fixture.target_root,
            fixture.run_root,
            fixture.run_id,
            fixture.git_head,
            repository_binding if binding is None else binding,
        )

    main_builder_args = argparse.Namespace(
        evidence_dir=paths["evidence"],
        prefix="main",
        authority_run_id=fixture.run_id,
        final_head=fixture.git_head,
    )
    pr_builder_args = argparse.Namespace(
        evidence_dir=paths["evidence"],
        prefix="pr",
        authority_run_id="pr-rehearsal-negative",
        final_head=fixture.git_head,
    )
    branch_builder_args = argparse.Namespace(evidence_dir=paths["evidence"], git_head=fixture.git_head)
    release_builder_args = argparse.Namespace(evidence_dir=paths["evidence"], git_head=fixture.git_head)
    built_receipts = [
        build_artifact_transport_receipt(
            argparse.Namespace(evidence_dir=paths["evidence"], role="pr")
        ),
        build_artifact_transport_receipt(
            argparse.Namespace(evidence_dir=paths["evidence"], role="main")
        ),
        build_artifact_transport_receipt(
            argparse.Namespace(evidence_dir=paths["evidence"], role="release")
        ),
        build_ci_receipt(pr_builder_args),
        build_ci_receipt(main_builder_args),
        build_branch_receipt(branch_builder_args),
        build_release_platform_receipt(release_builder_args),
    ]
    workflow_path_positive_variants = ["pr:bare", "main:bare", "release:bare"]

    def validate_path_variant(
        path: Path,
        workflow_path: str,
        *,
        event_name: str,
        workflow_file: str,
        pull_request_number: int | None,
    ) -> None:
        value = parse_json(path, "workflow path positive fixture")[0]
        changed = json.loads(json.dumps(value))
        changed["path"] = workflow_path
        mutate_bytes(
            path,
            canonical_json(changed),
            lambda: validate_run_api(
                path,
                workflow_run_id=value["id"],
                attempt=value["run_attempt"],
                event_name=event_name,
                platform_head_sha=value["head_sha"],
                workflow_file=workflow_file,
                pull_request_number=pull_request_number,
            ),
        )
        workflow_path_positive_variants.append(f"{event_name}:{workflow_path}")

    validate_path_variant(
        paths["main_run_api"],
        f"{CI_WORKFLOW}@main",
        event_name="push",
        workflow_file=CI_WORKFLOW,
        pull_request_number=None,
    )
    validate_path_variant(
        paths["main_run_api"],
        f"{CI_WORKFLOW}@refs/heads/main",
        event_name="push",
        workflow_file=CI_WORKFLOW,
        pull_request_number=None,
    )
    validate_path_variant(
        paths["pr_run_api"],
        f"{CI_WORKFLOW}@refs/pull/77/merge",
        event_name="pull_request",
        workflow_file=CI_WORKFLOW,
        pull_request_number=77,
    )
    validate_path_variant(
        paths["evidence"] / "release-run-api.json",
        ".github/workflows/release.yml@main",
        event_name="workflow_dispatch",
        workflow_file=".github/workflows/release.yml",
        pull_request_number=None,
    )

    cases.append(expect_failure("candidate-command-cannot-write-final", "E_POINTER_SCOPE", lambda: validate_candidate_output_name(FINAL_POINTER, "authority")))

    summary_original = paths["summary"].read_bytes()
    rehearsal_summary = summary_original.replace(b"mode=authority\n", b"mode=rehearsal\n", 1)
    cases.append(expect_failure("rehearsal-cannot-promote-final", "E_FINAL_AUTHORITY", lambda: mutate_bytes(paths["summary"], rehearsal_summary, context)))

    main_value = parse_json(paths["main"], "negative main receipt")[0]
    crossed = dict(main_value)
    crossed["authority_run_id"] = "different-authority-run"
    cases.append(expect_failure("cross-run-main-ci-refusal", "E_CI_IDENTITY", lambda: mutate_bytes(paths["main"], canonical_json(crossed), context)))

    missing_path = paths["evidence"] / "branch-protection-receipt.json"
    missing_raw = missing_path.read_bytes()
    missing_path.unlink()
    try:
        cases.append(expect_failure("missing-step7-evidence-refusal", "E_EVIDENCE_SET", context))
    finally:
        missing_path.write_bytes(missing_raw)

    archive_raw = paths["archive"].read_bytes()
    cases.append(expect_failure("tampered-archive-refusal", "E_ARCHIVE", lambda: mutate_bytes(paths["archive"], archive_raw + b"tamper\n", context)))

    wrong_head = dict(main_value)
    wrong_head["commit_sha"] = "d" * 40
    cases.append(expect_failure("wrong-main-head-refusal", "E_CI_IDENTITY", lambda: mutate_bytes(paths["main"], canonical_json(wrong_head), context)))

    portable_value = parse_json(paths["portable"], "negative portable receipt")[0]
    wrong_portable = json.loads(json.dumps(portable_value))
    wrong_portable["package"]["files"]["app.jar"]["sha256"] = "e" * 64
    cases.append(expect_failure("wrong-portable-jar-refusal", "E_PORTABLE_JAR", lambda: mutate_bytes(paths["portable"], canonical_json(wrong_portable), context)))

    release_value = parse_json(paths["release"], "negative release receipt")[0]
    wrong_release = json.loads(json.dumps(release_value))
    wrong_release["tested_assets"]["jar_sha256"] = "f" * 64
    cases.append(expect_failure("wrong-release-jar-refusal", "E_RELEASE_ASSETS", lambda: mutate_bytes(paths["release"], canonical_json(wrong_release), context)))

    wrong_release_assets = json.loads(json.dumps(release_value))
    wrong_release_assets["tested_assets"]["release_assets_sha256"] = "0" * 64

    def spliced_release_assets_with_rebuilt_transport() -> None:
        changed_paths = (
            paths["release"],
            paths["release_zip"],
            paths["evidence"] / "release-artifacts-api.json",
            paths["release_transport"],
            paths["release_platform"],
        )
        previous = [(path, path.read_bytes()) for path in changed_paths]
        try:
            changed_release_raw = canonical_json(wrong_release_assets)
            paths["release"].write_bytes(changed_release_raw)
            paths["release_zip"].write_bytes(
                synthetic_zip_bytes(
                    {
                        "main-artifacts.json": paths["main_artifacts_api"].read_bytes(),
                        "main-jobs.json": paths["main_jobs_api"].read_bytes(),
                        "main-run.json": paths["main_run_api"].read_bytes(),
                        "receipt.json": changed_release_raw,
                    }
                )
            )
            changed_api = parse_json(
                paths["evidence"] / "release-artifacts-api.json",
                "spliced release artifacts API",
            )[0]
            current_artifact = next(
                row
                for row in changed_api["artifacts"]
                if row["name"].endswith(
                    f"-{fixture.git_head}-{release_value['release_workflow']['run_id']}-{release_value['release_workflow']['attempt']}"
                )
            )
            current_artifact["digest"] = "sha256:" + sha256_file(
                paths["release_zip"], "spliced release dry-run artifact ZIP"
            )
            (paths["evidence"] / "release-artifacts-api.json").write_bytes(
                canonical_json(changed_api)
            )
            build_artifact_transport_receipt(
                argparse.Namespace(evidence_dir=paths["evidence"], role="release")
            )
            build_release_platform_receipt(release_builder_args)
            context()
        finally:
            for path, payload in previous:
                path.write_bytes(payload)

    cases.append(
        expect_failure(
            "wrong-release-assets-preimage-refusal",
            "E_RELEASE_ASSETS",
            spliced_release_assets_with_rebuilt_transport,
        )
    )

    wrong_main_aggregate = parse_json(
        paths["main_aggregate"], "negative main aggregate receipt"
    )[0]
    wrong_main_aggregate["contract_sha256"] = "0" * 64
    cases.append(
        expect_failure(
            "spliced-main-local-artifact-member-refusal",
            "E_ARTIFACT_TRANSPORT_MEMBER",
            lambda: mutate_bytes(
                paths["main_aggregate"],
                canonical_json(wrong_main_aggregate),
                lambda: build_ci_receipt(main_builder_args),
            ),
        )
    )

    wrong_pr_aggregate = parse_json(
        paths["pr_aggregate"], "negative PR aggregate receipt"
    )[0]
    wrong_pr_aggregate["contract_sha256"] = "1" * 64
    cases.append(
        expect_failure(
            "spliced-pr-local-artifact-member-refusal",
            "E_ARTIFACT_TRANSPORT_MEMBER",
            lambda: mutate_bytes(
                paths["pr_aggregate"],
                canonical_json(wrong_pr_aggregate),
                lambda: build_ci_receipt(pr_builder_args),
            ),
        )
    )

    spliced_dry_release = json.loads(json.dumps(release_value))
    spliced_dry_release["dry_run_image"]["image_id"] = "sha256:" + "7" * 64
    cases.append(
        expect_failure(
            "spliced-release-local-artifact-member-refusal",
            "E_ARTIFACT_TRANSPORT_MEMBER",
            lambda: mutate_bytes(
                paths["release"],
                canonical_json(spliced_dry_release),
                lambda: build_release_platform_receipt(release_builder_args),
            ),
        )
    )

    main_zip_raw = paths["main_zip"].read_bytes()
    cases.append(
        expect_failure(
            "wrong-main-artifact-zip-digest-refusal",
            "E_ARTIFACT_TRANSPORT_DIGEST",
            lambda: mutate_bytes(paths["main_zip"], main_zip_raw + b"tamper\n", context),
        )
    )

    main_transport_value = parse_json(
        paths["main_transport"], "negative main artifact transport receipt"
    )[0]

    def inspect_framing_mutation(mutated_zip: bytes) -> None:
        matching_artifact = json.loads(json.dumps(main_transport_value["artifact"]))
        matching_artifact["digest"] = "sha256:" + hashlib.sha256(
            mutated_zip
        ).hexdigest()
        mutate_bytes(
            paths["main_zip"],
            mutated_zip,
            lambda: inspect_artifact_transport_archive(
                paths["main_zip"],
                ARTIFACT_TRANSPORT_SPECS["main"],
                matching_artifact,
            ),
        )

    for framing_name, mutated_zip in (
        ("prefix", b"opaque-prefix\n" + main_zip_raw),
        ("trailing", main_zip_raw + b"opaque-trailing\n"),
        ("concatenated", main_zip_raw + main_zip_raw),
    ):
        cases.append(
            expect_failure(
                f"artifact-zip-{framing_name}-framing-refusal",
                "E_ARTIFACT_TRANSPORT_FRAMING",
                lambda payload=mutated_zip: inspect_framing_mutation(payload),
            )
        )

    wrong_transport_id = json.loads(json.dumps(main_transport_value))
    wrong_transport_id["artifact"]["id"] += 1
    cases.append(
        expect_failure(
            "wrong-main-artifact-transport-id-refusal",
            "E_ARTIFACT_TRANSPORT_IDENTITY",
            lambda: mutate_bytes(
                paths["main_transport"], canonical_json(wrong_transport_id), context
            ),
        )
    )

    prior_attempt_transport = json.loads(json.dumps(main_transport_value))
    prior_attempt_transport["attempt"] -= 1
    prior_attempt_transport["artifact"]["name"] = re.sub(
        r"-[0-9]+$", f"-{prior_attempt_transport['attempt']}",
        prior_attempt_transport["artifact"]["name"],
    )
    cases.append(
        expect_failure(
            "prior-attempt-artifact-transport-refusal",
            "E_ARTIFACT_TRANSPORT_IDENTITY",
            lambda: mutate_bytes(
                paths["main_transport"],
                canonical_json(prior_attempt_transport),
                context,
            ),
        )
    )

    missing_transport_raw = paths["main_transport"].read_bytes()
    paths["main_transport"].unlink()
    try:
        cases.append(
            expect_failure(
                "missing-artifact-transport-refusal", "E_EVIDENCE_SET", context
            )
        )
    finally:
        paths["main_transport"].write_bytes(missing_transport_raw)

    wrong_gate_base_manifest = json.loads(json.dumps(release_value))
    wrong_gate_base_manifest["tested_assets"]["gate_base_image"][
        "manifest_digest"
    ] = "sha256:" + "0" * 64
    cases.append(
        expect_failure(
            "wrong-gate-base-manifest-refusal",
            "E_BASE_IMAGE",
            lambda: mutate_bytes(
                paths["release"], canonical_json(wrong_gate_base_manifest), context
            ),
        )
    )
    wrong_dry_base_config = json.loads(json.dumps(release_value))
    wrong_dry_base_config["dry_run_image"]["base_image"][
        "config_digest"
    ] = "sha256:" + "1" * 64
    cases.append(
        expect_failure(
            "spliced-release-base-config-refusal",
            "E_BASE_IMAGE",
            lambda: mutate_bytes(
                paths["release"], canonical_json(wrong_dry_base_config), context
            ),
        )
    )
    missing_dry_base = json.loads(json.dumps(release_value))
    del missing_dry_base["dry_run_image"]["base_image"]
    cases.append(
        expect_failure(
            "missing-release-base-identity-refusal",
            "E_RELEASE_IMAGE",
            lambda: mutate_bytes(
                paths["release"], canonical_json(missing_dry_base), context
            ),
        )
    )

    coverage_raw = paths["coverage"].read_bytes()
    wrong_order = re.sub(rb"quality_sha256: [0-9a-f]{64}", b"quality_sha256: " + b"0" * 64, coverage_raw, count=1)
    require(wrong_order != coverage_raw, "E_NEGATIVE", "cannot construct review order negative")
    cases.append(expect_failure("quality-coverage-signoff-order-refusal", "E_REVIEW_ORDER", lambda: mutate_bytes(paths["coverage"], wrong_order, context)))

    portable_raw = paths["portable"].read_bytes()
    duplicate = portable_raw.replace(b'"schema_version":1', b'"schema_version":1,"schema_version":1', 1)
    cases.append(expect_failure("duplicate-json-key-refusal", "E_JSON_DUPLICATE", lambda: mutate_bytes(paths["portable"], duplicate, context)))

    symlink_path = paths["evidence"] / "branch-protection-receipt.json"
    symlink_raw = symlink_path.read_bytes()
    symlink_path.unlink()
    symlink_path.symlink_to("missing.json")
    try:
        cases.append(expect_failure("symlink-evidence-refusal", "E_SYMLINK", context))
    finally:
        symlink_path.unlink()
        symlink_path.write_bytes(symlink_raw)

    fake_digest = json.loads(json.dumps(main_value))
    fake_digest["run_api_sha256"] = "0" * 64
    cases.append(expect_failure("fake-api-digest-refusal", "E_API_DIGEST", lambda: mutate_bytes(paths["main"], canonical_json(fake_digest), context)))

    main_run_value = parse_json(paths["main_run_api"], "negative main run API")[0]
    harmless_raw_tamper = {**main_run_value, "tampered": True}
    cases.append(expect_failure("raw-api-tamper-refusal", "E_API_DIGEST", lambda: mutate_bytes(paths["main_run_api"], canonical_json(harmless_raw_tamper), context)))

    wrong_repo = json.loads(json.dumps(main_run_value))
    wrong_repo["repository"]["full_name"] = "attacker/example"
    cases.append(expect_failure("wrong-api-repository-refusal", "E_API_REPO", lambda: mutate_bytes(paths["main_run_api"], canonical_json(wrong_repo), context)))

    wrong_run = json.loads(json.dumps(main_run_value))
    wrong_run["id"] += 1000
    cases.append(expect_failure("wrong-api-run-refusal", "E_API_RUN", lambda: mutate_bytes(paths["main_run_api"], canonical_json(wrong_run), context)))

    wrong_main_workflow_ref = json.loads(json.dumps(main_run_value))
    wrong_main_workflow_ref["path"] = f"{CI_WORKFLOW}@feature/untrusted"
    cases.append(expect_failure("wrong-main-workflow-ref-refusal", "E_API_RUN", lambda: mutate_bytes(paths["main_run_api"], canonical_json(wrong_main_workflow_ref), context)))

    pr_run_value = parse_json(paths["pr_run_api"], "negative PR run API")[0]
    wrong_pr_workflow_ref = json.loads(json.dumps(pr_run_value))
    wrong_pr_workflow_ref["path"] = f"{CI_WORKFLOW}@refs/pull/999/merge"
    cases.append(expect_failure("wrong-pr-workflow-ref-refusal", "E_API_RUN", lambda: mutate_bytes(paths["pr_run_api"], canonical_json(wrong_pr_workflow_ref), context)))

    main_jobs = parse_json(paths["main_jobs_api"], "negative main jobs API")[0]
    failed_jobs = json.loads(json.dumps(main_jobs))
    next(row for row in failed_jobs["jobs"] if row["name"] == AGGREGATOR_NAME)["conclusion"] = "failure"
    cases.append(expect_failure("failed-aggregator-job-refusal", "E_API_JOBS", lambda: mutate_bytes(paths["main_jobs_api"], canonical_json(failed_jobs), context)))

    missing_job_attempt = json.loads(json.dumps(main_jobs))
    del missing_job_attempt["jobs"][0]["run_attempt"]
    cases.append(expect_failure("missing-job-attempt-refusal", "E_API_JOBS", lambda: mutate_bytes(paths["main_jobs_api"], canonical_json(missing_job_attempt), context)))

    wrong_job_attempt = json.loads(json.dumps(main_jobs))
    wrong_job_attempt["jobs"][0]["run_attempt"] += 1
    cases.append(expect_failure("wrong-job-attempt-refusal", "E_API_JOBS", lambda: mutate_bytes(paths["main_jobs_api"], canonical_json(wrong_job_attempt), context)))

    main_artifacts = parse_json(paths["main_artifacts_api"], "negative main artifacts API")[0]
    changed_artifact_id = json.loads(json.dumps(main_artifacts))
    required_row = next(row for row in changed_artifact_id["artifacts"] if row["name"].startswith("v934-required-receipt-"))
    required_row["id"] += 10000
    artifact_id_receipt = json.loads(json.dumps(main_value))
    changed_artifacts_raw = canonical_json(changed_artifact_id)
    artifact_id_receipt["artifacts_api_sha256"] = hashlib.sha256(changed_artifacts_raw).hexdigest()
    cases.append(
        expect_failure(
            "wrong-required-artifact-id-refusal",
            "E_CI_ARTIFACT",
            lambda: mutate_many(
                [(paths["main_artifacts_api"], changed_artifacts_raw), (paths["main"], canonical_json(artifact_id_receipt))],
                context,
            ),
        )
    )

    wrong_artifact_name = json.loads(json.dumps(main_artifacts))
    wrong_artifact_name["artifacts"][0]["name"] = "unexpected-artifact"
    cases.append(expect_failure("wrong-artifact-name-refusal", "E_API_ARTIFACTS", lambda: mutate_bytes(paths["main_artifacts_api"], canonical_json(wrong_artifact_name), context)))

    wrong_artifact_digest = json.loads(json.dumps(main_artifacts))
    wrong_artifact_digest["artifacts"][0]["digest"] = "sha256:not-a-digest"
    cases.append(expect_failure("wrong-artifact-digest-refusal", "E_API_ARTIFACTS", lambda: mutate_bytes(paths["main_artifacts_api"], canonical_json(wrong_artifact_digest), context)))

    expired_artifact = json.loads(json.dumps(main_artifacts))
    expired_artifact["artifacts"][0]["expired"] = True
    cases.append(expect_failure("expired-current-artifact-refusal", "E_API_ARTIFACTS", lambda: mutate_bytes(paths["main_artifacts_api"], canonical_json(expired_artifact), context)))

    branch_api = parse_json(paths["branch_api"], "negative branch API")[0]
    wrong_branch = json.loads(json.dumps(branch_api))
    wrong_branch["required_status_checks"]["contexts"] = ["unstable-context"]
    cases.append(expect_failure("wrong-branch-context-refusal", "E_BRANCH_API", lambda: mutate_bytes(paths["branch_api"], canonical_json(wrong_branch), context)))

    pr_api = parse_json(paths["pr_api"], "negative PR API")[0]
    unmerged_pr = json.loads(json.dumps(pr_api))
    unmerged_pr["merged"] = False
    cases.append(expect_failure("unmerged-pr-refusal", "E_PR_LINEAGE", lambda: mutate_bytes(paths["pr_api"], canonical_json(unmerged_pr), context)))

    wrong_lineage = json.loads(json.dumps(pr_api))
    wrong_lineage["merge_commit_sha"] = "e" * 40
    cases.append(expect_failure("wrong-pr-main-lineage-refusal", "E_PR_LINEAGE", lambda: mutate_bytes(paths["pr_api"], canonical_json(wrong_lineage), context)))

    wrong_pr_head = json.loads(json.dumps(pr_api))
    wrong_pr_head["head"]["sha"] = "e" * 40
    cases.append(expect_failure("wrong-pr-platform-head-refusal", "E_PR_LINEAGE", lambda: mutate_bytes(paths["pr_api"], canonical_json(wrong_pr_head), context)))

    wrong_pr_ref = json.loads(json.dumps(pr_api))
    wrong_pr_ref["head"]["ref"] = "feature/other"
    cases.append(expect_failure("wrong-pr-head-branch-refusal", "E_PR_LINEAGE", lambda: mutate_bytes(paths["pr_api"], canonical_json(wrong_pr_ref), context)))

    pr_receipt = parse_json(paths["pr"], "negative PR receipt")[0]
    wrong_platform_receipt = json.loads(json.dumps(pr_receipt))
    wrong_platform_receipt["platform_head_sha"] = "e" * 40
    cases.append(expect_failure("wrong-pr-receipt-platform-head-refusal", "E_API_RUN", lambda: mutate_bytes(paths["pr"], canonical_json(wrong_platform_receipt), context)))

    pr_artifacts = parse_json(paths["pr_artifacts_api"], "negative PR artifacts API")[0]
    wrong_tested_commit_artifacts = json.loads(json.dumps(pr_artifacts))
    for row in wrong_tested_commit_artifacts["artifacts"]:
        row["name"] = row["name"].replace("c" * 40, "e" * 40, 1)
    cases.append(expect_failure("wrong-pr-tested-commit-cross-binding-refusal", "E_CI_CROSS_BINDING", lambda: mutate_bytes(paths["pr_artifacts_api"], canonical_json(wrong_tested_commit_artifacts), context)))

    wrong_pr_artifact_platform = json.loads(json.dumps(pr_artifacts))
    wrong_pr_artifact_platform["artifacts"][0]["workflow_run"]["head_sha"] = "e" * 40
    cases.append(expect_failure("wrong-pr-artifact-platform-head-refusal", "E_API_ARTIFACTS", lambda: mutate_bytes(paths["pr_artifacts_api"], canonical_json(wrong_pr_artifact_platform), context)))

    pr_aggregate_path = paths["evidence"] / "pr-aggregate-receipt.json"
    pr_aggregate = parse_json(pr_aggregate_path, "negative PR aggregate receipt")[0]
    wrong_aggregate_logical = json.loads(json.dumps(pr_aggregate))
    wrong_aggregate_logical["database_artifacts"][0]["logical_name"] = "v934-db-sqlite-wrong-lineage"
    wrong_aggregate_logical["database_artifact_set_sha256"] = hashlib.sha256(
        canonical_json(wrong_aggregate_logical["database_artifacts"])
    ).hexdigest()
    cases.append(expect_failure("wrong-pr-aggregate-logical-lineage-refusal", "E_AGGREGATE_RECEIPT", lambda: mutate_bytes(pr_aggregate_path, canonical_json(wrong_aggregate_logical), context)))

    pr_run_binding = parse_json(paths["pr_run_api"], "negative PR run binding")[0]
    wrong_pr_head_repo = json.loads(json.dumps(pr_run_binding))
    wrong_pr_head_repo["head_repository"]["full_name"] = "attacker/fork"
    cases.append(expect_failure("wrong-pr-head-repository-refusal", "E_API_REPO", lambda: mutate_bytes(paths["pr_run_api"], canonical_json(wrong_pr_head_repo), context)))

    dirty_binding = {**repository_binding, "clean": False}
    cases.append(expect_failure("dirty-repository-refusal", "E_REPO_CLEAN", lambda: context(dirty_binding)))
    origin_binding = {**repository_binding, "origin": "https://example.invalid/repository.git"}
    cases.append(expect_failure("noncanonical-origin-refusal", "E_ORIGIN", lambda: context(origin_binding)))
    live_binding = {**repository_binding, "live_main": "e" * 40}
    cases.append(expect_failure("stale-live-main-refusal", "E_LIVE_MAIN", lambda: context(live_binding)))
    source_binding = {**repository_binding, "source_sha256": "e" * 64}
    cases.append(expect_failure("repository-source-seal-refusal", "E_SOURCE_SEAL", lambda: context(source_binding)))
    tool_binding = {**repository_binding, "promotion_tool_sha256": "e" * 64}
    cases.append(expect_failure("promotion-tool-drift-refusal", "E_TOOLING_DRIFT", lambda: context(tool_binding)))

    git_audit_home = output_dir / "git-audit-home"
    git_audit_home.mkdir()
    included_config = git_audit_home / "included-transport.gitconfig"
    included_config.write_bytes(b'[url "ssh://attacker.invalid/"]\n\tinsteadOf = https://github.com/\n')
    (git_audit_home / ".gitconfig").write_bytes(
        f'[includeIf "gitdir:/**"]\n\tpath = {included_config}\n'.encode("utf-8")
    )
    cases.append(
        expect_failure(
            "global-includeif-url-rewrite-refusal",
            "E_GIT_CONFIG",
            lambda: audit_git_configuration(
                fixture.repo_root,
                home_override=git_audit_home,
                disable_system=True,
            ),
        )
    )

    def ambient_git_override() -> None:
        previous = os.environ.get("GIT_DIR")
        os.environ["GIT_DIR"] = str(output_dir / "attacker-git-dir")
        try:
            git_environment()
        finally:
            if previous is None:
                del os.environ["GIT_DIR"]
            else:
                os.environ["GIT_DIR"] = previous

    cases.append(expect_failure("ambient-git-override-refusal", "E_GIT_ENV", ambient_git_override))

    tooling_root = output_dir / "synthetic-tooling"
    tooling_root.mkdir()
    tooling_rows: list[str] = []
    for index, relative in enumerate(FROZEN_TOOLING_PATHS, 1):
        tooling_path = tooling_root / relative
        tooling_path.parent.mkdir(parents=True, exist_ok=True)
        tooling_path.write_bytes(f"synthetic frozen tooling {index}\n".encode("ascii"))
        tooling_rows.append(f"{sha256_file(tooling_path, 'synthetic frozen tooling')}  {relative}")
    tooling_manifest = tooling_root / TOOLING_MANIFEST
    tooling_manifest.parent.mkdir(parents=True, exist_ok=True)
    tooling_manifest.write_text("\n".join(tooling_rows) + "\n", encoding="ascii")
    parse_tooling_manifest(tooling_root)
    frozen_tool = tooling_root / FROZEN_TOOLING_PATHS[0]
    frozen_raw = frozen_tool.read_bytes()
    cases.append(expect_failure("frozen-tooling-manifest-drift-refusal", "E_TOOLING_DRIFT", lambda: mutate_bytes(frozen_tool, frozen_raw + b"drift\n", lambda: parse_tooling_manifest(tooling_root))))

    wrong_consumed = json.loads(json.dumps(release_value))
    wrong_consumed["consumed_main_authority"]["artifact_id"] += 1
    cases.append(expect_failure("wrong-release-main-artifact-refusal", "E_RELEASE_CONSUMED_MAIN", lambda: mutate_bytes(paths["release"], canonical_json(wrong_consumed), context)))

    release_run_path = paths["evidence"] / "release-run-api.json"
    release_run = parse_json(release_run_path, "negative release run API")[0]
    release_run_tamper = {**release_run, "tampered": True}
    cases.append(expect_failure("release-raw-api-tamper-refusal", "E_API_DIGEST", lambda: mutate_bytes(release_run_path, canonical_json(release_run_tamper), context)))
    wrong_release_workflow_ref = json.loads(json.dumps(release_run))
    wrong_release_workflow_ref["path"] = ".github/workflows/release.yml@refs/tags/untrusted"
    cases.append(expect_failure("wrong-release-workflow-ref-refusal", "E_API_RUN", lambda: mutate_bytes(release_run_path, canonical_json(wrong_release_workflow_ref), context)))

    release_jobs_path = paths["evidence"] / "release-jobs-api.json"
    release_jobs = parse_json(release_jobs_path, "negative release jobs API")[0]
    missing_dry_job = json.loads(json.dumps(release_jobs))
    missing_dry_job["jobs"][0]["name"] = "Renamed unstable release job"
    cases.append(expect_failure("wrong-release-job-refusal", "E_RELEASE_JOB", lambda: mutate_bytes(release_jobs_path, canonical_json(missing_dry_job), context)))

    release_artifacts_path = paths["evidence"] / "release-artifacts-api.json"
    release_artifacts = parse_json(release_artifacts_path, "negative release artifacts API")[0]
    expired_release_artifact = json.loads(json.dumps(release_artifacts))
    expired_release_artifact["artifacts"][0]["expired"] = True
    cases.append(expect_failure("expired-release-artifact-refusal", "E_API_ARTIFACTS", lambda: mutate_bytes(release_artifacts_path, canonical_json(expired_release_artifact), context)))

    def builder_preserves(path: Path, builder: Callable[[], dict[str, Any]]) -> None:
        previous = path.read_bytes()
        try:
            builder()
        except PointerError:
            require(path.read_bytes() == previous, "E_NEGATIVE", "failed receipt build changed previous receipt")
            raise

    cases.append(
        expect_failure(
            "ci-receipt-builder-rejects-failed-job",
            "E_API_JOBS",
            lambda: mutate_bytes(
                paths["main_jobs_api"],
                canonical_json(failed_jobs),
                lambda: builder_preserves(paths["main"], lambda: build_ci_receipt(main_builder_args)),
            ),
        )
    )
    cases.append(
        expect_failure(
            "branch-receipt-builder-rejects-wrong-context",
            "E_BRANCH_API",
            lambda: mutate_bytes(
                paths["branch_api"],
                canonical_json(wrong_branch),
                lambda: builder_preserves(missing_path, lambda: build_branch_receipt(branch_builder_args)),
            ),
        )
    )
    failed_release_jobs = json.loads(json.dumps(release_jobs))
    failed_release_jobs["jobs"][0]["conclusion"] = "failure"
    cases.append(
        expect_failure(
            "release-receipt-builder-rejects-failed-job",
            "E_RELEASE_JOB",
            lambda: mutate_bytes(
                release_jobs_path,
                canonical_json(failed_release_jobs),
                lambda: builder_preserves(paths["release_platform"], lambda: build_release_platform_receipt(release_builder_args)),
            ),
        )
    )

    previous_branch_receipt = missing_path.read_bytes()

    def failed_receipt_publication() -> None:
        try:
            build_branch_receipt(branch_builder_args, failpoint="after-replace-before-fsync")
        except PointerError:
            require(missing_path.read_bytes() == previous_branch_receipt, "E_NEGATIVE", "failed receipt publication changed previous receipt")
            raise

    cases.append(expect_failure("failed-receipt-publication-rolls-back", "E_POINTER_ROLLED_BACK", failed_receipt_publication))

    promoted = promote_final(fixture, repository_binding=repository_binding)
    previous_final = paths["final"].read_bytes()

    def failed_publication() -> None:
        try:
            promote_final(fixture, failpoint="after-replace-before-fsync", repository_binding=repository_binding)
        except PointerError:
            require(paths["final"].read_bytes() == previous_final, "E_NEGATIVE", "failed final publication changed previous final pointer")
            raise

    cases.append(expect_failure("failed-final-publication-rolls-back", "E_POINTER_ROLLED_BACK", failed_publication))
    require(paths["final"].read_bytes() == previous_final, "E_NEGATIVE", "final pointer changed after focused negatives")
    verified = verify_final(fixture, repository_binding=repository_binding)
    result = {
        "schema_version": 1,
        "kind": "v934-final-promotion-negative-result",
        "status": "passed",
        "case_count": len(cases),
        "cases": cases,
        "built_receipts": built_receipts,
        "workflow_path_positive_variants": workflow_path_positive_variants,
        "positive_final_sha256": promoted["sha256"],
        "verified_final_sha256": verified["sha256"],
    }
    result_path = output_dir / "final-negative-result.json"
    result_path.write_bytes(canonical_json(result))
    return {
        "command": "final-negative",
        "cases": len(cases),
        "result": str(result_path),
        "result_sha256": sha256_file(result_path, "final negative result"),
        "status": "passed",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("publish-candidate", "verify-candidate"):
        command = commands.add_parser(name)
        command.add_argument("--target-root", type=Path, required=True)
        command.add_argument("--run-root", type=Path, required=True)
        command.add_argument("--run-id", required=True)
        command.add_argument("--git-head", required=True)
        command.add_argument("--mode", choices=("rehearsal", "authority"), required=True)
    for name in ("promote-final", "verify-final"):
        command = commands.add_parser(name)
        command.add_argument("--repo-root", type=Path, required=True)
        command.add_argument("--target-root", type=Path, required=True)
        command.add_argument("--run-root", type=Path, required=True)
        command.add_argument("--run-id", required=True)
        command.add_argument("--git-head", required=True)
    ci_receipt = commands.add_parser("build-ci-receipt")
    ci_receipt.add_argument("--evidence-dir", type=Path, required=True)
    ci_receipt.add_argument("--prefix", choices=("pr", "main"), required=True)
    ci_receipt.add_argument("--authority-run-id", required=True)
    ci_receipt.add_argument("--final-head", required=True)
    transport_receipt = commands.add_parser("build-artifact-transport-receipt")
    transport_receipt.add_argument("--evidence-dir", type=Path, required=True)
    transport_receipt.add_argument(
        "--role", choices=("pr", "main", "release"), required=True
    )
    branch_receipt = commands.add_parser("build-branch-receipt")
    branch_receipt.add_argument("--evidence-dir", type=Path, required=True)
    branch_receipt.add_argument("--git-head", required=True)
    release_receipt = commands.add_parser("build-release-platform-receipt")
    release_receipt.add_argument("--evidence-dir", type=Path, required=True)
    release_receipt.add_argument("--git-head", required=True)
    negative_command = commands.add_parser("negative")
    negative_command.add_argument("--output-dir", type=Path, required=True)
    final_negative_command = commands.add_parser("final-negative")
    final_negative_command.add_argument("--output-dir", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "publish-candidate":
            result = publish(args)
        elif args.command == "verify-candidate":
            result = verify(args)
        elif args.command == "promote-final":
            result = promote_final(args)
        elif args.command == "verify-final":
            result = verify_final(args)
        elif args.command == "build-ci-receipt":
            result = build_ci_receipt(args)
        elif args.command == "build-artifact-transport-receipt":
            result = build_artifact_transport_receipt(args)
        elif args.command == "build-branch-receipt":
            result = build_branch_receipt(args)
        elif args.command == "build-release-platform-receipt":
            result = build_release_platform_receipt(args)
        elif args.command == "final-negative":
            result = final_negative(args)
        else:
            result = negative(args)
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
        return 0
    except PointerError as exc:
        print(
            json.dumps(
                {"command": getattr(args, "command", "unknown"), "error": exc.code, "message": exc.message, "status": "failed"},
                ensure_ascii=False,
                separators=(",", ":"),
                sort_keys=True,
            ),
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
