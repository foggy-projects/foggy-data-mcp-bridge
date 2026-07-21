#!/usr/bin/env python3
"""Package the exact v9.3.4 tested output tree and verify its release JAR.

The package command deliberately invokes Maven plugin goals rather than a
lifecycle.  It must not compile or execute tests: the production class trees
and resources under every ``target/classes`` tree, plus the TEST-*.xml
reports, have already been produced and sealed by the owning Step 4 release
authority run.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from typing import Any, Callable, Iterable, Mapping, Sequence
import xml.etree.ElementTree as ET


HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")
RUN_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
ENV_KEY = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
CONTROL = re.compile(r"[\x00-\x1f\x7f]")
ANSI = re.compile(r"\x1b\[[0-9;]*m")
STEP1_FREEZE = Path("scripts/v934/contract-freeze.json")
CLASS_TOOL = Path("scripts/v934/step4/coverage_exec_tool.py")
COVERAGE_XML_TOOL = Path("scripts/v934/step4/coverage_xml_tool.py")
SOURCE_TOOL = Path("scripts/v934/step4/coverage_tool.py")
REPORT_TOOL = Path("scripts/v934/step4/report_inventory_tool.py")
DOCKERFILE = Path("foggy-mcp-launcher/Dockerfile.release")
RUNTIME_BASE_TAG_REFERENCE = "eclipse-temurin:17-jre-alpine"
RUNTIME_BASE_INDEX_DIGEST = (
    "sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57"
)
RUNTIME_BASE_INDEX_REFERENCE = f"eclipse-temurin@{RUNTIME_BASE_INDEX_DIGEST}"
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
RUNTIME_BASE_FROM = (
    f"FROM --platform=linux/amd64 {RUNTIME_BASE_PINNED_REFERENCE}"
)
LAUNCHER = "foggy-mcp-launcher"
RUNTIME_REACTOR_MODULES = (
    "foggy-bean-copy",
    "addons/foggy-chart-storage-cloud",
    "foggy-core",
    "addons/foggy-data-viewer",
    "foggy-dataset",
    "foggy-dataset-demo",
    "foggy-dataset-mcp",
    "foggy-dataset-memory-grid-bridge",
    "foggy-dataset-model",
    "foggy-fsscript",
    "foggy-mcp-spi",
    "addons/foggy-odoo-bridge-java",
)
MAVEN_METADATA_GROUP = "com.foggysource"
PACKAGE_MANIFEST_NAME = "package-manifest.json"
IMAGE_MANIFEST_NAME = "image-manifest.json"
APP_JAR_NAME = "app.jar"
MAVEN_LOG_NAME = "maven-invocations.log"
VALIDATOR_LOG_NAME = "tested-tree-validation.log"
DOCKER_LOG_NAME = "docker-build.log"
RELEASE_SUCCESSOR_MARKER = "confirmed-threshold-post-step4-replay"
PACKAGE_OUTPUT_NAMES = (
    APP_JAR_NAME,
    DOCKER_LOG_NAME,
    IMAGE_MANIFEST_NAME,
    MAVEN_LOG_NAME,
    PACKAGE_MANIFEST_NAME,
    VALIDATOR_LOG_NAME,
)
FAILURE_RECEIPT_NAME = "package-tested-tree-failure.env"
FAILURE_RECEIPT_FIELDS = (
    "schema_version",
    "kind",
    "run_id",
    "gate_phase",
    "operation",
    "subphase",
    "error_code",
    "tool_exit_code",
    "status",
)
FAILURE_RECEIPT_KIND = "v934-package-subphase-failure"
FAILURE_RECEIPT_GATE_PHASE = "package-tested-tree"
FAILURE_RECEIPT_OPERATIONS = ("package", "verify")
PACKAGE_IMAGE_EIMAGE_SUBPHASES = (
    "package-image-runtime-inspect",
    "package-image-readback-precondition",
    "package-image-receipt-completeness",
)
PACKAGE_FAILURE_SUBPHASES = (
    "package-preflight",
    "package-maven-reactor",
    "package-maven-launcher-jar",
    "package-image",
    *PACKAGE_IMAGE_EIMAGE_SUBPHASES,
    "package-postconditions",
    "package-manifest",
    "package-internal-verify",
)
VERIFY_FAILURE_SUBPHASES = ("verify-package",)
FAILURE_RECEIPT_ERROR_CODES = frozenset(
    {
        "E_BASE_CONFIG_DIGEST",
        "E_BASE_FROM",
        "E_BASE_IMAGE",
        "E_BASE_INDEX_DIGEST",
        "E_BASE_INDEX_REFERENCE",
        "E_BASE_MANIFEST_DIGEST",
        "E_BASE_PINNED_REFERENCE",
        "E_BASE_PLATFORM",
        "E_BASE_TAG_REFERENCE",
        "E_CLASS_TREE",
        "E_COMMAND",
        "E_CONTEXT_CLEANUP",
        "E_CONTEXT_POLICY",
        "E_DIRECTORY",
        "E_DOCKERFILE",
        "E_FAILURE_RECEIPT",
        "E_FILE",
        "E_FILE_MISSING",
        "E_FILE_RACE",
        "E_FILE_SIZE",
        "E_FREEZE",
        "E_GIT",
        "E_IMAGE",
        "E_IMAGE_CLEANUP",
        "E_IMAGE_DRIFT",
        "E_IMAGE_MANIFEST",
        "E_INTERNAL",
        "E_JAR_CARDINALITY",
        "E_JAR_CLASS_TREE",
        "E_JAR_DRIFT",
        "E_JAR_ENTRY",
        "E_JAR_LIBRARY",
        "E_JAR_MANIFEST",
        "E_JAR_ZIP",
        "E_JSON",
        "E_JSON_DUPLICATE",
        "E_MANIFEST",
        "E_MANIFEST_NAME",
        "E_MAVEN_CONFIG",
        "E_MAVEN_ENV",
        "E_MAVEN_GOAL",
        "E_MAVEN_POLICY",
        "E_MAVEN_SELECTOR",
        "E_MAVEN_SKIP",
        "E_MAVEN_TOOLCHAIN",
        "E_NEGATIVE",
        "E_OUTPUT",
        "E_OUTPUT_CONTRACT",
        "E_OUTPUT_EXISTS",
        "E_OUTPUT_NAMES",
        "E_OUTPUT_RACE",
        "E_PATH",
        "E_POM",
        "E_QUARANTINE",
        "E_REACTOR_MODULES",
        "E_RECEIPT_DRIFT",
        "E_REPORT_DRIFT",
        "E_REPORT_INVENTORY",
        "E_REPORT_TREE",
        "E_RUN_ID",
        "E_SIGNAL",
        "E_SOURCE_DRIFT",
        "E_SOURCE_SEAL",
        "E_SPECIAL",
        "E_STEP4",
        "E_STEP4_BINDING",
        "E_STEP4_CONTEXT",
        "E_STEP4_DRIFT",
        "E_STEP4_FINAL",
        "E_STEP4_IDENTITY",
        "E_STEP4_RELEASE_VERIFY",
        "E_STEP4_ROOT",
        "E_STEP4_STATUS",
        "E_STEP4_SUMMARY",
        "E_SUCCESSOR_MARKER",
        "E_SYMLINK",
        "E_TESTED_TREE",
        "E_TESTED_TREE_DRIFT",
        "E_TOOL",
        "E_TREE",
        "E_VERIFY_PATH",
        "E_XML_TOOL",
    }
)


class PackageError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass
class FailureReceiptContext:
    path: Path
    run_id: str
    operation: str
    subphase: str
    pending_eimage_subphase: str | None = None

    def set_subphase(self, value: str) -> None:
        allowed = (
            PACKAGE_FAILURE_SUBPHASES
            if self.operation == "package"
            else VERIFY_FAILURE_SUBPHASES
        )
        require(value in allowed, "E_FAILURE_RECEIPT", "failure receipt subphase differs")
        self.subphase = value
        self.pending_eimage_subphase = None

    def defer_eimage_subphase(self, value: str) -> None:
        require(
            self.operation == "package"
            and self.subphase == "package-image"
            and value in PACKAGE_IMAGE_EIMAGE_SUBPHASES,
            "E_FAILURE_RECEIPT",
            "pending image failure subphase differs",
        )
        self.pending_eimage_subphase = value

    def commit_pending_eimage_subphase(self, error_code: str) -> None:
        pending = self.pending_eimage_subphase
        self.pending_eimage_subphase = None
        if error_code == "E_IMAGE" and pending is not None:
            self.set_subphase(pending)

    def clear_pending_eimage_subphase(self) -> None:
        self.pending_eimage_subphase = None


def reject(code: str, message: str) -> None:
    raise PackageError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def strict_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in result, "E_JSON_DUPLICATE", f"duplicate JSON key: {key}")
        result[key] = value
    return result


def canonical_json(value: Any, *, pretty: bool = True) -> bytes:
    if pretty:
        text = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True)
    else:
        text = json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    return (text + "\n").encode("utf-8")


def parse_json(data: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            data.decode("utf-8"),
            object_pairs_hook=strict_object,
            parse_constant=lambda token: reject(
                "E_JSON", f"{label} contains non-finite number: {token}"
            ),
        )
    except PackageError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        reject("E_JSON", f"cannot parse {label}: {exc}")
    require(type(value) is dict, "E_JSON", f"{label} must be an object")
    return value


def exact_keys(value: Any, expected: Iterable[str], code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict, code, f"{label} must be an object")
    expected_set = set(expected)
    actual = set(value)
    require(
        actual == expected_set,
        code,
        f"{label} keys differ: missing={sorted(expected_set - actual)} "
        f"extra={sorted(actual - expected_set)}",
    )
    return value


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def real_directory(path: Path, label: str) -> Path:
    absolute = Path(os.path.abspath(path))
    try:
        observed = os.lstat(absolute)
    except FileNotFoundError:
        reject("E_DIRECTORY", f"missing {label}: {absolute}")
    require(stat.S_ISDIR(observed.st_mode), "E_DIRECTORY", f"{label} is not a real directory")
    require(not stat.S_ISLNK(observed.st_mode), "E_SYMLINK", f"{label} is symlinked")
    try:
        resolved = absolute.resolve(strict=True)
    except OSError as exc:
        reject("E_DIRECTORY", f"cannot resolve {label}: {exc}")
    require(resolved == absolute, "E_SYMLINK", f"{label} has a symlinked path component")
    return absolute


def fsync_directory(path: Path, label: str) -> None:
    directory = real_directory(path, label)
    flags = (
        os.O_RDONLY
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_DIRECTORY", 0)
        | getattr(os, "O_NOFOLLOW", 0)
    )
    try:
        descriptor = os.open(directory, flags)
    except OSError as exc:
        reject("E_OUTPUT", f"cannot open {label} for durable sync: {exc}")
    try:
        os.fsync(descriptor)
    except OSError as exc:
        reject("E_OUTPUT", f"cannot durably sync {label}: {exc}")
    finally:
        os.close(descriptor)


def repo_root(path: Path) -> Path:
    root = real_directory(path, "repository root")
    process = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--show-toplevel"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    require(process.returncode == 0, "E_GIT", "cannot resolve Git repository root")
    require(process.stdout.strip() == str(root), "E_GIT", "supplied root differs from Git root")
    return root


def safe_run_id(value: str) -> str:
    require(RUN_ID.fullmatch(value or "") is not None, "E_RUN_ID", "unsafe run id")
    require(value not in {".", ".."}, "E_RUN_ID", "unsafe run id")
    return value


def safe_relative(value: str, label: str) -> str:
    require(type(value) is str and bool(value), "E_PATH", f"{label} is empty")
    require("\\" not in value and CONTROL.search(value) is None, "E_PATH", f"unsafe {label}")
    pure = PurePosixPath(value)
    require(not pure.is_absolute(), "E_PATH", f"{label} is absolute")
    require(
        all(part not in {"", ".", ".."} for part in value.split("/")),
        "E_PATH",
        f"{label} contains a non-canonical component",
    )
    require(pure.as_posix() == value, "E_PATH", f"{label} is not canonical")
    return value


def secure_open(path: Path, label: str) -> tuple[int, os.stat_result]:
    absolute = Path(os.path.abspath(path))
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except FileNotFoundError:
        reject("E_FILE_MISSING", f"missing {label}: {absolute}")
    except OSError as exc:
        if absolute.is_symlink():
            reject("E_SYMLINK", f"{label} is symlinked: {absolute}")
        reject("E_FILE", f"cannot open {label}: {absolute}: {exc}")
    observed = os.fstat(descriptor)
    if not stat.S_ISREG(observed.st_mode):
        os.close(descriptor)
        reject("E_SPECIAL", f"{label} is not a regular file: {absolute}")
    return descriptor, observed


def secure_bytes(path: Path, label: str, maximum: int | None = None) -> bytes:
    descriptor, before = secure_open(path, label)
    try:
        if maximum is not None:
            require(before.st_size <= maximum, "E_FILE_SIZE", f"{label} exceeds size limit")
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(descriptor, min(1024 * 1024, remaining))
            require(bool(chunk), "E_FILE_RACE", f"short read from {label}")
            chunks.append(chunk)
            remaining -= len(chunk)
        require(os.read(descriptor, 1) == b"", "E_FILE_RACE", f"{label} grew while read")
        after = os.fstat(descriptor)
        current = os.lstat(Path(os.path.abspath(path)))
        identity = lambda row: (
            row.st_dev,
            row.st_ino,
            row.st_size,
            row.st_mtime_ns,
            row.st_ctime_ns,
        )
        require(identity(before) == identity(after), "E_FILE_RACE", f"{label} changed while read")
        require(
            (current.st_dev, current.st_ino, current.st_size)
            == (after.st_dev, after.st_ino, after.st_size),
            "E_FILE_RACE",
            f"{label} path identity changed while read",
        )
        return b"".join(chunks)
    finally:
        os.close(descriptor)


def file_identity(path: Path, label: str) -> dict[str, Any]:
    descriptor, before = secure_open(path, label)
    digest = hashlib.sha256()
    try:
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
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
            f"{label} changed while hashed",
        )
        current = os.lstat(Path(os.path.abspath(path)))
        require(
            (current.st_dev, current.st_ino, current.st_size)
            == (after.st_dev, after.st_ino, after.st_size),
            "E_FILE_RACE",
            f"{label} path identity changed while hashed",
        )
        return {"sha256": digest.hexdigest(), "size": before.st_size}
    finally:
        os.close(descriptor)


def binding(path: Path, *, relative_to: Path | None = None, label: str = "file") -> dict[str, Any]:
    identity = file_identity(path, label)
    if relative_to is None:
        name = path.name
    else:
        try:
            name = path.relative_to(relative_to).as_posix()
        except ValueError:
            reject("E_PATH", f"{label} is outside its expected root")
    return {"path": name, **identity}


def write_new(path: Path, data: bytes, mode: int = 0o644) -> None:
    require(not path.exists() and not path.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {path}")
    parent = real_directory(path.parent, "output parent")
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=parent)
    temporary = Path(temporary_name)
    try:
        os.fchmod(descriptor, mode)
        view = memoryview(data)
        while view:
            count = os.write(descriptor, view)
            require(count > 0, "E_OUTPUT", f"short write: {path}")
            view = view[count:]
        os.fsync(descriptor)
        os.close(descriptor)
        descriptor = -1
        try:
            os.link(temporary, path, follow_symlinks=False)
        except FileExistsError:
            reject("E_OUTPUT_EXISTS", f"output appeared before publication: {path}")
        except OSError as exc:
            reject("E_OUTPUT", f"cannot publish {path}: {exc}")
        fsync_directory(parent, "output parent after publication")
        temporary.unlink()
        fsync_directory(parent, "output parent after temporary cleanup")
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()


def failure_receipt_target(path: Path, *, package_root: Path | None = None) -> Path:
    absolute = Path(os.path.abspath(path))
    require(
        absolute.name == FAILURE_RECEIPT_NAME,
        "E_FAILURE_RECEIPT",
        "failure receipt name differs",
    )
    if package_root is not None:
        output = Path(os.path.abspath(package_root))
        try:
            absolute.relative_to(output)
        except ValueError:
            pass
        else:
            reject(
                "E_FAILURE_RECEIPT",
                "failure receipt must be outside the durable package directory",
            )
        require(
            absolute.parent == output.parent,
            "E_FAILURE_RECEIPT",
            "failure receipt is not in the package run root",
        )
    real_directory(absolute.parent, "failure receipt parent")
    return absolute


def failure_receipt_subphases(operation: str) -> tuple[str, ...]:
    require(
        operation in FAILURE_RECEIPT_OPERATIONS,
        "E_FAILURE_RECEIPT",
        "failure receipt operation differs",
    )
    return (
        PACKAGE_FAILURE_SUBPHASES
        if operation == "package"
        else VERIFY_FAILURE_SUBPHASES
    )


def require_failure_receipt_subphase_error_code(
    operation: str,
    subphase: str,
    error_code: str,
) -> None:
    if (
        operation == "package"
        and subphase in PACKAGE_IMAGE_EIMAGE_SUBPHASES
    ):
        require(
            error_code == "E_IMAGE",
            "E_FAILURE_RECEIPT",
            "refined image failure subphase requires E_IMAGE",
        )


def failure_receipt_bytes(
    *,
    run_id: str,
    operation: str,
    subphase: str,
    error_code: str,
    tool_exit_code: int,
) -> bytes:
    safe_run_id(run_id)
    require(
        operation in FAILURE_RECEIPT_OPERATIONS,
        "E_FAILURE_RECEIPT",
        "failure receipt operation differs",
    )
    require(
        subphase in failure_receipt_subphases(operation),
        "E_FAILURE_RECEIPT",
        "failure receipt subphase differs",
    )
    require(
        error_code in FAILURE_RECEIPT_ERROR_CODES,
        "E_FAILURE_RECEIPT",
        "failure receipt error code differs",
    )
    require_failure_receipt_subphase_error_code(operation, subphase, error_code)
    require(
        type(tool_exit_code) is int and 1 <= tool_exit_code <= 255,
        "E_FAILURE_RECEIPT",
        "failure receipt exit code differs",
    )
    values = (
        ("schema_version", "1"),
        ("kind", FAILURE_RECEIPT_KIND),
        ("run_id", run_id),
        ("gate_phase", FAILURE_RECEIPT_GATE_PHASE),
        ("operation", operation),
        ("subphase", subphase),
        ("error_code", error_code),
        ("tool_exit_code", str(tool_exit_code)),
        ("status", "failed"),
    )
    require(
        tuple(key for key, _ in values) == FAILURE_RECEIPT_FIELDS,
        "E_FAILURE_RECEIPT",
        "failure receipt field order differs",
    )
    return ("".join(f"{key}={value}\n" for key, value in values)).encode("ascii")


def publish_failure_receipt(
    context: FailureReceiptContext,
    *,
    error_code: str,
    tool_exit_code: int,
) -> None:
    write_new(
        context.path,
        failure_receipt_bytes(
            run_id=context.run_id,
            operation=context.operation,
            subphase=context.subphase,
            error_code=error_code,
            tool_exit_code=tool_exit_code,
        ),
    )


def read_failure_receipt(
    path: Path,
    *,
    run_id: str,
    operation: str,
    tool_exit_code: int | None = None,
    package_root: Path | None = None,
) -> dict[str, str]:
    try:
        receipt = failure_receipt_target(path, package_root=package_root)
        raw = secure_bytes(receipt, "package failure receipt", 4096)
        require(raw.endswith(b"\n") and b"\r" not in raw, "E_FAILURE_RECEIPT", "failure receipt line ending differs")
        text = raw.decode("ascii")
        lines = text[:-1].split("\n")
        require(
            len(lines) == len(FAILURE_RECEIPT_FIELDS),
            "E_FAILURE_RECEIPT",
            "failure receipt field count differs",
        )
        values: dict[str, str] = {}
        for key, line in zip(FAILURE_RECEIPT_FIELDS, lines, strict=True):
            require(line.count("=") == 1, "E_FAILURE_RECEIPT", "failure receipt line differs")
            actual_key, value = line.split("=", 1)
            require(actual_key == key and key not in values, "E_FAILURE_RECEIPT", "failure receipt field order differs")
            values[key] = value
        require(values["schema_version"] == "1", "E_FAILURE_RECEIPT", "failure receipt schema differs")
        require(values["kind"] == FAILURE_RECEIPT_KIND, "E_FAILURE_RECEIPT", "failure receipt kind differs")
        require(values["run_id"] == safe_run_id(run_id), "E_FAILURE_RECEIPT", "failure receipt run id differs")
        require(values["gate_phase"] == FAILURE_RECEIPT_GATE_PHASE, "E_FAILURE_RECEIPT", "failure receipt gate phase differs")
        require(values["operation"] == operation, "E_FAILURE_RECEIPT", "failure receipt operation differs")
        require(
            values["subphase"] in failure_receipt_subphases(operation),
            "E_FAILURE_RECEIPT",
            "failure receipt subphase differs",
        )
        require(
            values["error_code"] in FAILURE_RECEIPT_ERROR_CODES,
            "E_FAILURE_RECEIPT",
            "failure receipt error code differs",
        )
        require_failure_receipt_subphase_error_code(
            operation,
            values["subphase"],
            values["error_code"],
        )
        exit_code = values["tool_exit_code"]
        require(
            re.fullmatch(r"[1-9][0-9]{0,2}", exit_code) is not None
            and 1 <= int(exit_code) <= 255,
            "E_FAILURE_RECEIPT",
            "failure receipt exit code differs",
        )
        if tool_exit_code is not None:
            require(
                type(tool_exit_code) is int
                and 1 <= tool_exit_code <= 255
                and int(exit_code) == tool_exit_code,
                "E_FAILURE_RECEIPT",
                "failure receipt exit code differs",
            )
        require(values["status"] == "failed", "E_FAILURE_RECEIPT", "failure receipt status differs")
        return values
    except PackageError as exc:
        if exc.code == "E_FAILURE_RECEIPT":
            raise
        reject("E_FAILURE_RECEIPT", "failure receipt is invalid")
    except (UnicodeDecodeError, ValueError):
        reject("E_FAILURE_RECEIPT", "failure receipt is invalid")


def failure_receipt_error_code(exc: PackageError) -> str:
    return (
        exc.code
        if exc.code in FAILURE_RECEIPT_ERROR_CODES and exc.code != "E_SIGNAL"
        else "E_INTERNAL"
    )


def failure_receipt_context(args: argparse.Namespace) -> FailureReceiptContext | None:
    value = getattr(args, "failure_receipt", None)
    if value is None:
        return None
    operation = getattr(args, "command", "")
    require(
        operation in FAILURE_RECEIPT_OPERATIONS,
        "E_FAILURE_RECEIPT",
        "failure receipt command differs",
    )
    run_id = getattr(args, "run_id", None)
    require(type(run_id) is str, "E_FAILURE_RECEIPT", "failure receipt run id is absent")
    safe_run_id(run_id)
    path = failure_receipt_target(
        value,
        package_root=(
            args.output_dir
            if operation == "package"
            else args.manifest.parent
        ),
    )
    return FailureReceiptContext(
        path=path,
        run_id=run_id,
        operation=operation,
        subphase=("package-preflight" if operation == "package" else "verify-package"),
    )


def guarded_eimage_boundary(
    failure_context: FailureReceiptContext | None,
    subphase: str,
    action: Callable[[], Any],
) -> Any:
    try:
        return action()
    except PackageError as exc:
        if failure_context is not None and exc.code == "E_IMAGE":
            failure_context.defer_eimage_subphase(subphase)
        raise


def execute_with_failure_receipt(
    context: FailureReceiptContext,
    action: Callable[[], dict[str, Any]],
) -> tuple[dict[str, Any] | None, int]:
    try:
        result = action()
        context.clear_pending_eimage_subphase()
        return result, 0
    except PackageError as exc:
        error_code = failure_receipt_error_code(exc)
        try:
            context.commit_pending_eimage_subphase(error_code)
        except BaseException:
            context.clear_pending_eimage_subphase()
        try:
            publish_failure_receipt(
                context,
                error_code=error_code,
                tool_exit_code=1,
            )
        except BaseException:
            pass
        return None, 1
    except KeyboardInterrupt:
        context.clear_pending_eimage_subphase()
        try:
            publish_failure_receipt(
                context,
                error_code="E_SIGNAL",
                tool_exit_code=130,
            )
        except BaseException:
            pass
        return None, 130
    except BaseException:
        context.clear_pending_eimage_subphase()
        try:
            publish_failure_receipt(
                context,
                error_code="E_INTERNAL",
                tool_exit_code=1,
            )
        except BaseException:
            pass
        return None, 1


def verify_failure_receipt_command(args: argparse.Namespace) -> dict[str, Any]:
    values = read_failure_receipt(
        args.failure_receipt,
        run_id=args.run_id,
        operation=args.operation,
        tool_exit_code=args.tool_exit_code,
        package_root=args.package_root,
    )
    return {
        "command": "verify-failure-receipt",
        "error_code": values["error_code"],
        "gate_phase": values["gate_phase"],
        "operation": values["operation"],
        "receipt": "valid",
        "run_id": values["run_id"],
        "status": values["status"],
        "subphase": values["subphase"],
        "tool_exit_code": int(values["tool_exit_code"]),
    }


def create_output(path: Path) -> Path:
    absolute = Path(os.path.abspath(path))
    require(not absolute.exists() and not absolute.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {absolute}")
    real_directory(absolute.parent, "output parent")
    try:
        absolute.mkdir(mode=0o755)
    except OSError as exc:
        reject("E_OUTPUT", f"cannot create output directory: {exc}")
    fsync_directory(absolute.parent, "output directory parent after creation")
    return real_directory(absolute, "output directory")


def cleanup_flat_directory(path: Path, label: str) -> None:
    directory = real_directory(path, label)
    parent = real_directory(directory.parent, f"{label} parent")
    try:
        children = sorted(os.scandir(directory), key=lambda child: child.name.encode("utf-8"))
    except OSError as exc:
        reject("E_OUTPUT", f"cannot scan {label}: {exc}")
    for child in children:
        try:
            observed = child.stat(follow_symlinks=False)
        except OSError as exc:
            reject("E_OUTPUT", f"cannot stat {label} entry: {exc}")
        require(
            not stat.S_ISDIR(observed.st_mode),
            "E_OUTPUT",
            f"{label} contains an unexpected directory",
        )
        try:
            os.unlink(child.path)
        except OSError as exc:
            reject("E_OUTPUT", f"cannot remove {label} entry: {exc}")
    fsync_directory(directory, f"{label} before removal")
    try:
        directory.rmdir()
    except OSError as exc:
        reject("E_OUTPUT", f"cannot remove {label}: {exc}")
    fsync_directory(parent, f"{label} parent after removal")


def link_regular_no_replace(source: Path, destination: Path, label: str) -> None:
    require(
        not destination.exists() and not destination.is_symlink(),
        "E_OUTPUT_EXISTS",
        f"output exists: {destination}",
    )
    descriptor, before = secure_open(source, label)
    try:
        try:
            os.link(source, destination, follow_symlinks=False)
        except FileExistsError:
            reject("E_OUTPUT_EXISTS", f"output appeared before publication: {destination}")
        except OSError as exc:
            reject("E_OUTPUT", f"cannot publish {label}: {exc}")
        after = os.fstat(descriptor)
        current = os.lstat(Path(os.path.abspath(source)))
        published = os.lstat(destination)
        require(
            (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
            == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
            == (current.st_dev, current.st_ino, current.st_size, current.st_mtime_ns),
            "E_FILE_RACE",
            f"{label} changed while published",
        )
        require(
            after.st_nlink == before.st_nlink + 1
            and current.st_nlink == after.st_nlink
            and stat.S_ISREG(published.st_mode)
            and (published.st_dev, published.st_ino, published.st_size, published.st_mtime_ns)
            == (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
            and published.st_nlink == after.st_nlink,
            "E_OUTPUT",
            f"published {label} identity differs",
        )
        fsync_directory(destination.parent, "durable package directory after file publication")
    finally:
        os.close(descriptor)


def create_receipt_package_staging(final_output: Path) -> Path:
    final = Path(os.path.abspath(final_output))
    require(
        not final.exists() and not final.is_symlink(),
        "E_OUTPUT_EXISTS",
        f"output exists: {final}",
    )
    run_root = real_directory(final.parent, "receipt package run root")
    staging_parent = real_directory(run_root.parent, "receipt package staging parent")
    try:
        name = tempfile.mkdtemp(prefix=f".{final.name}-receipt-staging-", dir=staging_parent)
    except OSError as exc:
        reject("E_OUTPUT", f"cannot create receipt package staging directory: {exc}")
    return real_directory(Path(name), "receipt package staging directory")


def publish_staged_package(staging: Path, final_output: Path) -> Path:
    source = real_directory(staging, "receipt package staging directory")
    validate_package_output(source)
    destination: Path | None = None
    try:
        destination = create_output(final_output)
        for name in PACKAGE_OUTPUT_NAMES:
            link_regular_no_replace(
                source / name,
                destination / name,
                f"staged durable package file {name}",
            )
        validate_package_output(destination)
        cleanup_flat_directory(source, "receipt package staging directory")
        return destination
    except BaseException as primary_error:
        cleanup_error: BaseException | None = None
        if destination is not None and (destination.exists() or destination.is_symlink()):
            try:
                cleanup_flat_directory(destination, "partial durable package directory")
            except BaseException as exc:
                cleanup_error = exc
        if cleanup_error is not None:
            if isinstance(primary_error, KeyboardInterrupt):
                raise primary_error
            raise PackageError(
                "E_OUTPUT",
                "partial durable package cleanup failed",
            ) from cleanup_error
        raise primary_error


def validate_package_output(directory: Path) -> dict[str, Any]:
    base = real_directory(directory, "durable package directory")
    entries: list[dict[str, Any]] = []
    try:
        children = sorted(os.scandir(base), key=lambda child: child.name.encode("utf-8"))
    except OSError as exc:
        reject("E_OUTPUT_CONTRACT", f"cannot scan durable package directory: {exc}")
    for child in children:
        try:
            observed = child.stat(follow_symlinks=False)
        except OSError as exc:
            reject("E_OUTPUT_CONTRACT", f"cannot stat durable package entry: {exc}")
        path = Path(child.path)
        if stat.S_ISLNK(observed.st_mode):
            reject("E_SYMLINK", f"durable package entry is symlinked: {path}")
        require(
            stat.S_ISREG(observed.st_mode),
            "E_OUTPUT_CONTRACT",
            f"durable package entry is not a regular file: {path}",
        )
        entries.append(
            {
                "path": child.name,
                **file_identity(path, f"durable package file {child.name}"),
            }
        )
    require(
        tuple(entry["path"] for entry in entries) == PACKAGE_OUTPUT_NAMES,
        "E_OUTPUT_CONTRACT",
        "durable package directory must contain exactly the six contracted files",
    )
    return {"file_count": len(entries), "files": entries}


def current_head(root: Path) -> str:
    process = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--verify", "HEAD^{commit}"],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    require(process.returncode == 0, "E_GIT", "cannot resolve HEAD")
    value = process.stdout.strip()
    require(HEX40.fullmatch(value) is not None, "E_GIT", "unexpected HEAD identity")
    return value


def frozen_modules(root: Path) -> tuple[list[str], dict[str, Any]]:
    path = root / STEP1_FREEZE
    raw = secure_bytes(path, "Step 1 contract freeze", 4 * 1024 * 1024)
    value = parse_json(raw, "Step 1 contract freeze")
    reactor = value.get("reactor")
    require(type(reactor) is dict, "E_FREEZE", "contract freeze reactor is absent")
    modules = reactor.get("modules")
    require(
        reactor.get("module_count") == 24
        and type(reactor.get("module_count")) is int
        and type(modules) is list
        and len(modules) == 24
        and len(set(modules)) == 24,
        "E_FREEZE",
        "contract freeze must contain exactly 24 unique modules",
    )
    parsed: list[str] = []
    for index, module in enumerate(modules):
        require(type(module) is str, "E_FREEZE", f"module {index} is not a string")
        safe_relative(module, f"module {index}")
        real_directory(root / module, f"module {module}")
        parsed.append(module)
    return parsed, {
        "path": STEP1_FREEZE.as_posix(),
        "sha256": sha256_bytes(raw),
        "size": len(raw),
    }


def parse_env(path: Path, label: str) -> tuple[dict[str, str], dict[str, Any]]:
    raw = secure_bytes(path, label, 2 * 1024 * 1024)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        reject("E_STEP4", f"{label} is not UTF-8: {exc}")
    require(text.endswith("\n"), "E_STEP4", f"{label} is not newline terminated")
    result: dict[str, str] = {}
    for number, line in enumerate(text.splitlines(), 1):
        require(bool(line) and "=" in line, "E_STEP4", f"malformed {label} line {number}")
        key, value = line.split("=", 1)
        require(ENV_KEY.fullmatch(key) is not None, "E_STEP4", f"invalid {label} key")
        require(key not in result, "E_STEP4", f"duplicate {label} key: {key}")
        require(CONTROL.search(value) is None, "E_STEP4", f"control character in {label}")
        result[key] = value
    return result, {"sha256": sha256_bytes(raw), "size": len(raw)}


def resolve_binding_path(root: Path, value: Any, label: str) -> Path:
    row = exact_keys(value, ("path", "sha256", "size"), "E_STEP4_BINDING", label)
    relative = safe_relative(row["path"], f"{label} path")
    require(type(row["sha256"]) is str and HEX64.fullmatch(row["sha256"]), "E_STEP4_BINDING", f"invalid {label} SHA")
    require(type(row["size"]) is int and row["size"] >= 0, "E_STEP4_BINDING", f"invalid {label} size")
    path = root / PurePosixPath(relative)
    observed = file_identity(path, label)
    require(observed == {"sha256": row["sha256"], "size": row["size"]}, "E_STEP4_BINDING", f"{label} differs")
    return path


@dataclass(frozen=True)
class Step4Authority:
    root: Path
    run_id: str
    git_head: str
    source_sha256: str
    not_before_ns: int
    context: Path
    class_universe: Path
    final_manifest: Path
    receipts: dict[str, Any]
    maven_authority: dict[str, Any]


def validate_step4_release_contract(
    status: dict[str, str],
    summary: dict[str, str],
    final: dict[str, Any],
) -> None:
    """Reject any formal artifact masquerading as the package release input."""

    require(
        status.get("mode") == "release"
        and status.get("last_phase") == "completed"
        and status.get("exit_code") == "0"
        and status.get("status") == "release-passed",
        "E_STEP4_STATUS",
        "Step 4 run status is not a completed release pass",
    )
    require(
        summary.get("mode") == "release"
        and summary.get("threshold_status") == "confirmed"
        and summary.get("release_successor") == RELEASE_SUCCESSOR_MARKER
        and "formalization_delta_sha256" not in summary
        and summary.get("status") == "release-candidate-ready",
        "E_STEP4_SUMMARY",
        "Step 4 summary is not release-ready",
    )
    exact_keys(
        final,
        (
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
            "release_successor",
        ),
        "E_STEP4_FINAL",
        "Step 4 release final manifest",
    )
    require(
        final.get("schema_version") == 1
        and type(final.get("schema_version")) is int
        and final.get("kind") == "v934-step4-coverage-acceptance-artifact"
        and final.get("stage") == "final"
        and final.get("status") == "release-final"
        and final.get("release_successor") == RELEASE_SUCCESSOR_MARKER,
        "E_STEP4_FINAL",
        "Step 4 final manifest is not the exact release successor artifact",
    )


def maven_configuration_state(
    root: Path, maven_home: Path, local_repository: Path
) -> list[dict[str, Any]]:
    candidates = (
        ("global-settings", maven_home / "conf/settings.xml"),
        ("global-toolchains", maven_home / "conf/toolchains.xml"),
        ("user-settings", local_repository.parent / "settings.xml"),
        ("user-toolchains", local_repository.parent / "toolchains.xml"),
        ("user-mavenrc", Path.home() / ".mavenrc"),
        ("system-mavenrc", Path("/etc/mavenrc")),
        ("project-maven-config", root / ".mvn/maven.config"),
        ("project-jvm-config", root / ".mvn/jvm.config"),
        ("project-extensions", root / ".mvn/extensions.xml"),
    )
    rows: list[dict[str, Any]] = []
    for label, path in candidates:
        if path.exists() or path.is_symlink():
            observed = os.lstat(path)
            require(
                stat.S_ISREG(observed.st_mode) and not stat.S_ISLNK(observed.st_mode),
                "E_MAVEN_CONFIG",
                f"unsafe Maven configuration: {label}",
            )
            rows.append({"label": label, "present": True, **file_identity(path, label)})
        else:
            rows.append({"label": label, "present": False})
    return rows


def require_maven_launcher_identity(
    recorded: Mapping[str, Any], actual: Mapping[str, Any]
) -> None:
    require(
        actual
        == {"sha256": recorded.get("sha256"), "size": recorded.get("size")},
        "E_MAVEN_TOOLCHAIN",
        "current Maven launcher differs from Step 4",
    )


def require_maven_configuration_unchanged(
    recorded: Any, current: list[dict[str, Any]]
) -> None:
    require(
        current == recorded,
        "E_MAVEN_CONFIG",
        "Maven global/user/project configuration drifted after Step 4",
    )


def validate_maven_toolchain_receipt(
    root: Path, receipt_path: Path, run_id: str, git_head: str
) -> dict[str, Any]:
    raw = secure_bytes(receipt_path, "Step 4 toolchain receipt", 32 * 1024 * 1024)
    receipt = parse_json(raw, "Step 4 toolchain receipt")
    require(
        receipt.get("schema_version") == 1
        and type(receipt.get("schema_version")) is int
        and receipt.get("kind") == "v934-step4-toolchain-receipt"
        and receipt.get("status") == "verified"
        and receipt.get("run_id") == run_id
        and receipt.get("git_head") == git_head,
        "E_MAVEN_TOOLCHAIN",
        "Step 4 Maven toolchain receipt identity differs",
    )
    commands = receipt.get("commands")
    maven = receipt.get("maven")
    require(type(commands) is dict and type(maven) is dict, "E_MAVEN_TOOLCHAIN", "Step 4 Maven toolchain sections are absent")
    recorded_launcher = commands.get("mvn")
    configuration = maven.get("configuration")
    require(type(recorded_launcher) is dict and type(configuration) is list, "E_MAVEN_TOOLCHAIN", "Step 4 Maven launcher/configuration is absent")
    require(
        recorded_launcher.get("path") == "$MAVEN_HOME/bin/mvn"
        and type(recorded_launcher.get("sha256")) is str
        and HEX64.fullmatch(recorded_launcher["sha256"]) is not None
        and type(recorded_launcher.get("size")) is int
        and recorded_launcher["size"] > 0
        and type(recorded_launcher.get("version_lines")) is list
        and bool(recorded_launcher["version_lines"]),
        "E_MAVEN_TOOLCHAIN",
        "Step 4 Maven launcher receipt shape differs",
    )
    executable_name = shutil.which("mvn")
    require(bool(executable_name), "E_MAVEN_TOOLCHAIN", "Maven executable is absent from PATH")
    try:
        executable = Path(str(executable_name)).resolve(strict=True)
    except OSError as exc:
        reject("E_MAVEN_TOOLCHAIN", f"cannot resolve Maven executable: {exc}")
    actual_launcher = file_identity(executable, "current Maven launcher")
    require_maven_launcher_identity(recorded_launcher, actual_launcher)
    output = run_capture(
        [str(executable), "-version"],
        root,
        "E_MAVEN_TOOLCHAIN",
        "current Maven version",
    )
    lines = [ANSI.sub("", line).strip() for line in output.splitlines() if line.strip()]
    require(lines and lines[0] == "Apache Maven 3.8.7", "E_MAVEN_TOOLCHAIN", "current Maven version differs")
    home_lines = [line for line in lines if line.startswith("Maven home: ")]
    require(len(home_lines) == 1, "E_MAVEN_TOOLCHAIN", "current Maven home is ambiguous")
    try:
        maven_home = Path(home_lines[0].split(": ", 1)[1]).resolve(strict=True)
    except OSError as exc:
        reject("E_MAVEN_TOOLCHAIN", f"cannot resolve current Maven home: {exc}")
    require(
        executable == (maven_home / "bin/mvn").resolve(strict=True),
        "E_MAVEN_TOOLCHAIN",
        "current Maven executable/home differs",
    )
    try:
        local_repository = (Path.home() / ".m2/repository").resolve(strict=True)
    except OSError as exc:
        reject("E_MAVEN_CONFIG", f"cannot resolve Maven local repository: {exc}")
    real_directory(local_repository, "Maven local repository")
    current_configuration = maven_configuration_state(root, maven_home, local_repository)
    require_maven_configuration_unchanged(configuration, current_configuration)
    return {
        "configuration_sha256": sha256_bytes(canonical_json(configuration, pretty=False)),
        "launcher": {"path": str(recorded_launcher["path"]), **actual_launcher},
        "receipt": {"sha256": sha256_bytes(raw), "size": len(raw)},
        "status": "exact-step4-replay",
        "version": "Apache Maven 3.8.7",
    }


def validate_step4(root: Path, run_id: str, supplied: Path) -> Step4Authority:
    expected = root / "target/v934-step4-coverage/runs" / run_id
    candidate = supplied if supplied.is_absolute() else root / supplied
    actual = real_directory(candidate, "Step 4 run root")
    require(actual == Path(os.path.abspath(expected)), "E_STEP4_ROOT", "Step 4 run root is not canonical for run id")
    context_path = actual / "run-context.json"
    context_raw = secure_bytes(context_path, "Step 4 run context", 1024 * 1024)
    context = parse_json(context_raw, "Step 4 run context")
    require(
        context.get("schema_version") == 1
        and type(context.get("schema_version")) is int
        and context.get("kind") == "v934-step4-run-context"
        and context.get("authority_kind") == "step4-coverage"
        and context.get("run_id") == run_id
        and type(context.get("git_head")) is str
        and HEX40.fullmatch(context["git_head"])
        and type(context.get("source_sha256")) is str
        and HEX64.fullmatch(context["source_sha256"])
        and type(context.get("not_before_ns")) is int
        and context["not_before_ns"] > 0,
        "E_STEP4_CONTEXT",
        "Step 4 run context identity is invalid",
    )
    head = context["git_head"]
    source = context["source_sha256"]
    status, status_id = parse_env(actual / "run-status.env", "Step 4 run status")
    summary, summary_id = parse_env(actual / "summary.env", "Step 4 summary")
    for label, payload in (("status", status), ("summary", summary)):
        require(payload.get("run_id") == run_id, "E_STEP4_IDENTITY", f"Step 4 {label} run differs")
        require(payload.get("git_head") == head, "E_STEP4_IDENTITY", f"Step 4 {label} HEAD differs")
        require(
            payload.get("source_before_sha256") == source
            and payload.get("source_after_sha256") == source,
            "E_STEP4_IDENTITY",
            f"Step 4 {label} source seal differs",
        )
    final_path = actual / "final-manifest.json"
    final_raw = secure_bytes(final_path, "Step 4 final manifest", 8 * 1024 * 1024)
    final = parse_json(final_raw, "Step 4 final manifest")
    validate_step4_release_contract(status, summary, final)
    require(
        final.get("run_id") == run_id
        and final.get("git_head") == head,
        "E_STEP4_FINAL",
        "Step 4 release final manifest identity differs",
    )
    require(status.get("summary_sha256") == summary_id["sha256"], "E_STEP4_BINDING", "Step 4 summary status binding differs")
    evidence = final.get("evidence")
    require(
        type(evidence) is dict
        and evidence.get("run_id") == run_id
        and evidence.get("git_head") == head
        and evidence.get("source_sha256") == source,
        "E_STEP4_FINAL",
        "Step 4 final evidence binding differs",
    )
    final_sha = sha256_bytes(final_raw)
    require(status.get("final_manifest_sha256") == final_sha, "E_STEP4_BINDING", "Step 4 final manifest status binding differs")
    bindings = final.get("bindings")
    require(type(bindings) is dict and bool(bindings), "E_STEP4_BINDING", "Step 4 final bindings are absent")
    for name, row in bindings.items():
        require(type(name) is str and bool(name), "E_STEP4_BINDING", "invalid Step 4 binding name")
        resolve_binding_path(root, row, f"Step 4 binding {name}")
    for name in ("candidate_manifest", "coverage_gate", "threshold"):
        resolve_binding_path(root, final.get(name), f"Step 4 {name}")
    class_universe = actual / "class-universe.json"
    toolchain_receipt = actual / "toolchain-receipt.json"
    require("class_universe" in bindings, "E_STEP4_BINDING", "Step 4 class-universe binding is absent")
    require(resolve_binding_path(root, bindings["class_universe"], "Step 4 class universe") == class_universe, "E_STEP4_BINDING", "Step 4 class-universe path differs")
    require(resolve_binding_path(root, bindings["run_context"], "Step 4 run context") == context_path, "E_STEP4_BINDING", "Step 4 run-context path differs")
    require(resolve_binding_path(root, bindings["summary"], "Step 4 summary") == actual / "summary.env", "E_STEP4_BINDING", "Step 4 summary path differs")
    require("toolchain_receipt" in bindings, "E_STEP4_BINDING", "Step 4 toolchain receipt binding is absent")
    require(
        resolve_binding_path(root, bindings["toolchain_receipt"], "Step 4 toolchain receipt")
        == toolchain_receipt,
        "E_STEP4_BINDING",
        "Step 4 toolchain receipt path differs",
    )
    run_capture(
        [
            sys.executable,
            str(root / COVERAGE_XML_TOOL),
            "verify-artifact",
            "--mode",
            "release",
            "--repo-root",
            str(root),
            "--artifact",
            str(final_path),
            "--run-status",
            str(actual / "run-status.env"),
        ],
        root,
        "E_STEP4_RELEASE_VERIFY",
        "Step 4 canonical release artifact verification",
    )
    maven_authority = validate_maven_toolchain_receipt(
        root, toolchain_receipt, run_id, head
    )
    return Step4Authority(
        actual,
        run_id,
        head,
        source,
        context["not_before_ns"],
        context_path,
        class_universe,
        final_path,
        {
            "run_context": {"sha256": sha256_bytes(context_raw), "size": len(context_raw)},
            "run_status": status_id,
            "summary": summary_id,
            "final_manifest": {"sha256": final_sha, "size": len(final_raw)},
            "toolchain_receipt": maven_authority["receipt"],
        },
        maven_authority,
    )


def run_capture(command: Sequence[str], root: Path, code: str, label: str) -> str:
    try:
        process = subprocess.run(
            list(command),
            cwd=root,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
    except OSError as exc:
        reject(code, f"cannot start {label}: {exc}")
    require(process.returncode == 0, code, f"{label} failed with exit {process.returncode}: {process.stdout[-1000:]}")
    return process.stdout


def source_seal(root: Path) -> dict[str, Any]:
    output = run_capture(
        [sys.executable, str(root / SOURCE_TOOL), "source-hash", "--repo-root", str(root)],
        root,
        "E_SOURCE_SEAL",
        "tracked source seal",
    )
    lines = [line for line in output.splitlines() if line.strip()]
    require(len(lines) == 1, "E_SOURCE_SEAL", "source seal returned non-canonical output")
    value = parse_json(lines[0].encode("utf-8"), "source seal response")
    exact_keys(value, ("command", "file_count", "git_head", "sha256", "status"), "E_SOURCE_SEAL", "source seal response")
    require(
        value["command"] == "source-hash"
        and value["status"] == "passed"
        and type(value["file_count"]) is int
        and value["file_count"] > 0
        and type(value["git_head"]) is str
        and HEX40.fullmatch(value["git_head"])
        and type(value["sha256"]) is str
        and HEX64.fullmatch(value["sha256"]),
        "E_SOURCE_SEAL",
        "source seal response identity differs",
    )
    return value


def validate_class_universe(root: Path, authority: Step4Authority) -> str:
    return run_capture(
        [
            sys.executable,
            str(root / CLASS_TOOL),
            "verify-classes",
            "--repo-root",
            str(root),
            "--run-id",
            authority.run_id,
            "--not-before-ns",
            str(authority.not_before_ns),
            "--run-context",
            str(authority.context),
            "--class-universe",
            str(authority.class_universe),
        ],
        root,
        "E_CLASS_TREE",
        "Step 4 class-universe verification",
    )


def validate_report_inventory(root: Path, authority: Step4Authority) -> str:
    return run_capture(
        [
            sys.executable,
            str(root / REPORT_TOOL),
            "validate",
            "--repo-root",
            str(root),
            "--run-id",
            authority.run_id,
        ],
        root,
        "E_REPORT_INVENTORY",
        "Step 4 live report inventory verification",
    )


def walk_selected(root: Path, predicate: Callable[[str], bool], label: str) -> list[tuple[str, dict[str, Any]]]:
    base = real_directory(root, label)
    rows: list[tuple[str, dict[str, Any]]] = []

    def visit(directory: Path) -> None:
        try:
            children = sorted(os.scandir(directory), key=lambda child: child.name.encode("utf-8"))
        except OSError as exc:
            reject("E_TREE", f"cannot scan {label}: {exc}")
        for child in children:
            try:
                observed = child.stat(follow_symlinks=False)
            except OSError as exc:
                reject("E_TREE", f"cannot stat {child.path}: {exc}")
            path = Path(child.path)
            if stat.S_ISLNK(observed.st_mode):
                reject("E_SYMLINK", f"{label} contains symlink: {path}")
            if stat.S_ISDIR(observed.st_mode):
                visit(path)
            elif stat.S_ISREG(observed.st_mode):
                if predicate(child.name):
                    relative = path.relative_to(base).as_posix()
                    rows.append((relative, file_identity(path, f"{label} file {relative}")))
            elif predicate(child.name):
                reject("E_SPECIAL", f"{label} contains special selected entry: {path}")

    visit(base)
    return rows


def tree_digest(entries: list[dict[str, Any]]) -> str:
    return sha256_bytes(canonical_json(entries, pretty=False))


def content_tree(entries: Sequence[dict[str, Any]]) -> dict[str, Any]:
    ordered = sorted(
        (dict(entry) for entry in entries),
        key=lambda entry: entry["path"].encode("utf-8"),
    )
    require(
        len({entry["path"] for entry in ordered}) == len(ordered),
        "E_JAR_ENTRY",
        "content tree contains duplicate paths",
    )
    return {
        "bytes": sum(int(entry["size"]) for entry in ordered),
        "files": len(ordered),
        "sha256": tree_digest(ordered),
        "entries": ordered,
    }


def module_output_tree(snapshot: Mapping[str, Any], module: str) -> dict[str, Any]:
    entries = snapshot.get("entries")
    require(type(entries) is list, "E_TESTED_TREE", "tested output entries are absent")
    prefix = f"{module}/target/classes/"
    rows: list[dict[str, Any]] = []
    for index, entry in enumerate(entries):
        require(type(entry) is dict, "E_TESTED_TREE", f"tested output entry {index} is invalid")
        path = entry.get("path")
        if type(path) is not str or not path.startswith(prefix):
            continue
        relative = safe_relative(path[len(prefix) :], f"tested output entry {index}")
        require(
            type(entry.get("sha256")) is str
            and HEX64.fullmatch(entry["sha256"]) is not None
            and type(entry.get("size")) is int
            and entry["size"] >= 0,
            "E_TESTED_TREE",
            f"tested output identity is invalid: {path}",
        )
        rows.append(
            {"path": relative, "sha256": entry["sha256"], "size": entry["size"]}
        )
    require(bool(rows), "E_TESTED_TREE", f"tested output tree is empty: {module}")
    return content_tree(rows)


def runtime_reactor_coordinates(
    coordinates: Mapping[str, tuple[str, str]],
) -> dict[str, dict[str, str]]:
    require(
        set(RUNTIME_REACTOR_MODULES).issubset(coordinates),
        "E_JAR_LIBRARY",
        "runtime reactor module set is outside the frozen reactor",
    )
    result: dict[str, dict[str, str]] = {}
    for module in RUNTIME_REACTOR_MODULES:
        artifact, version = coordinates[module]
        file_name = f"{artifact}-{version}.jar"
        require(file_name not in result, "E_JAR_LIBRARY", "runtime reactor JAR names collide")
        result[file_name] = {
            "artifact_id": artifact,
            "file_name": file_name,
            "module": module,
            "nested_path": f"BOOT-INF/lib/{file_name}",
            "source_path": f"{module}/target/{file_name}",
        }
    require(
        len(result) == len(RUNTIME_REACTOR_MODULES) == 12,
        "E_JAR_LIBRARY",
        "runtime reactor JAR cardinality differs",
    )
    return result


def tested_tree_snapshot_from_roots(
    module_roots: Sequence[tuple[str, Path]],
) -> dict[str, Any]:
    entries: list[dict[str, Any]] = []
    counts: dict[str, int] = {}
    for module, class_root in module_roots:
        rows = walk_selected(
            class_root,
            lambda _name: True,
            f"tested output tree {module}",
        )
        require(bool(rows), "E_CLASS_TREE", f"empty tested output tree: {module}")
        counts[module] = len(rows)
        entries.extend({"path": f"{module}/target/classes/{path}", **identity} for path, identity in rows)
    entries.sort(key=lambda row: row["path"].encode("utf-8"))
    return {
        "files": len(entries),
        "module_counts": counts,
        "sha256": tree_digest(entries),
        "entries": entries,
    }


def tested_tree_snapshot(root: Path, modules: Sequence[str]) -> dict[str, Any]:
    return tested_tree_snapshot_from_roots(
        [(module, root / module / "target/classes") for module in modules]
    )


def report_snapshot(root: Path, modules: Sequence[str]) -> dict[str, Any]:
    entries: list[dict[str, Any]] = []
    counts: dict[str, int] = {}
    for module in modules:
        count = 0
        for family in ("surefire-reports", "failsafe-reports"):
            report_root = root / module / "target" / family
            if not report_root.exists() and not report_root.is_symlink():
                continue
            rows = walk_selected(
                report_root,
                lambda name: name.startswith("TEST-") and name.endswith(".xml"),
                f"TEST XML tree {module}/{family}",
            )
            count += len(rows)
            entries.extend(
                {"path": f"{module}/target/{family}/{path}", **identity}
                for path, identity in rows
            )
        counts[module] = count
    require(bool(entries), "E_REPORT_TREE", "no TEST XML reports are present")
    entries.sort(key=lambda row: row["path"].encode("utf-8"))
    return {
        "files": len(entries),
        "module_counts": counts,
        "sha256": tree_digest(entries),
    }


def require_unchanged(label: str, before: dict[str, Any], after: dict[str, Any], code: str) -> None:
    require(before == after, code, f"{label} changed during release packaging")


def pom_coordinates(root: Path, module: str) -> tuple[str, str]:
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    try:
        module_root = ET.fromstring(secure_bytes(root / module / "pom.xml", f"{module} POM", 4 * 1024 * 1024))
        project_root = ET.fromstring(secure_bytes(root / "pom.xml", "root POM", 4 * 1024 * 1024))
    except ET.ParseError as exc:
        reject("E_POM", f"cannot parse Maven POM: {exc}")
    artifact = module_root.findtext("m:artifactId", namespaces=namespace)
    version = module_root.findtext("m:version", namespaces=namespace)
    if not version:
        version = module_root.findtext("m:parent/m:version", namespaces=namespace)
    root_version = project_root.findtext("m:version", namespaces=namespace)
    require(bool(artifact) and bool(version) and version == root_version, "E_POM", f"unexpected coordinates for {module}")
    require(re.fullmatch(r"[A-Za-z0-9_.-]+", artifact or "") is not None, "E_POM", "unsafe artifact id")
    require(re.fullmatch(r"[A-Za-z0-9_.-]+", version or "") is not None, "E_POM", "unsafe version")
    return artifact, version


ALLOWED_GOALS = {"jar:jar", "install:install", "spring-boot:repackage"}
MAVEN_CONTROL_ENV = (
    "MAVEN_ARGS",
    "MAVEN_BASEDIR",
    "MAVEN_CONFIG",
    "MAVEN_OPTS",
    "MAVEN_SKIP_RC",
    "JAVA_TOOL_OPTIONS",
    "JDK_JAVA_OPTIONS",
    "_JAVA_OPTIONS",
)
FORBIDDEN_PHASES = {
    "validate", "initialize", "generate-sources", "process-sources", "generate-resources",
    "process-resources", "compile", "process-classes", "generate-test-sources",
    "process-test-sources", "generate-test-resources", "process-test-resources",
    "test-compile", "process-test-classes", "test", "prepare-package", "package",
    "pre-integration-test", "integration-test", "post-integration-test", "verify",
    "install", "deploy", "clean", "site",
}


def validate_maven_environment(environment: Mapping[str, str]) -> None:
    populated = [name for name in MAVEN_CONTROL_ENV if environment.get(name, "") != ""]
    require(
        not populated,
        "E_MAVEN_ENV",
        f"ambient Maven/JVM control variables are forbidden: {','.join(populated)}",
    )


def validate_maven_tokens(command: Sequence[str], expected_goals: Sequence[str] | None = None) -> None:
    require(bool(command) and Path(command[0]).name in {"mvn", "mvn.cmd"}, "E_MAVEN_POLICY", "Maven executable differs")
    lower = [token.lower() for token in command]
    for token in lower:
        require("skiptests" not in token and "skipunit" not in token and "skipit" not in token, "E_MAVEN_SKIP", f"skip flag is forbidden: {token}")
        require("skip-external" not in token, "E_MAVEN_SKIP", f"external skip is forbidden: {token}")
        require(not token.startswith("-dtest") and not token.startswith("-dit.test"), "E_MAVEN_SELECTOR", f"test selector is forbidden: {token}")
        require(token not in FORBIDDEN_PHASES, "E_MAVEN_GOAL", f"Maven lifecycle phase is forbidden: {token}")
    goals = [token for token in command[1:] if not token.startswith("-") and ":" in token]
    require(all(goal in ALLOWED_GOALS for goal in goals), "E_MAVEN_GOAL", f"illegal Maven goal set: {goals}")
    if expected_goals is not None:
        require(goals == list(expected_goals), "E_MAVEN_GOAL", f"Maven goals differ: {goals}")


def maven_commands(modules: Sequence[str]) -> list[list[str]]:
    project_list = ",".join(modules)
    all_modules = ["mvn", "-B", "-ntp", "-pl", project_list, "jar:jar", "install:install"]
    launcher = ["mvn", "-B", "-ntp", "-pl", LAUNCHER, "jar:jar", "spring-boot:repackage"]
    validate_maven_tokens(all_modules, ("jar:jar", "install:install"))
    validate_maven_tokens(launcher, ("jar:jar", "spring-boot:repackage"))
    return [all_modules, launcher]


def run_logged(
    command: Sequence[str],
    root: Path,
    stream: Any,
    label: str,
    *,
    receipt_safe: bool = False,
) -> None:
    validate_maven_environment(os.environ)
    if receipt_safe:
        stream.write(
            (
                json.dumps(
                    {"label": label, "terminal_output": "suppressed"},
                    separators=(",", ":"),
                )
                + "\n"
            ).encode()
        )
    else:
        stream.write((json.dumps({"label": label, "argv": list(command)}, separators=(",", ":")) + "\n").encode())
    stream.flush()
    try:
        process = subprocess.run(
            list(command),
            cwd=root,
            stdout=subprocess.DEVNULL if receipt_safe else stream,
            stderr=subprocess.DEVNULL if receipt_safe else subprocess.STDOUT,
        )
    except OSError as exc:
        reject("E_COMMAND", f"cannot start {label}: {exc}")
    stream.write((json.dumps({"label": label, "exit_code": process.returncode}, separators=(",", ":")) + "\n").encode())
    stream.flush()
    os.fsync(stream.fileno())
    require(process.returncode == 0, "E_COMMAND", f"{label} failed with exit {process.returncode}")


def is_launcher_main_jar(name: str) -> bool:
    if not name.startswith(f"{LAUNCHER}-") or not name.endswith(".jar"):
        return False
    return not any(name.endswith(suffix) for suffix in ("-sources.jar", "-javadoc.jar", "-tests.jar"))


def launcher_candidates(target: Path) -> list[Path]:
    directory = real_directory(target, "Launcher target")
    result: list[Path] = []
    for entry in sorted(os.scandir(directory), key=lambda row: row.name.encode("utf-8")):
        if not is_launcher_main_jar(entry.name):
            continue
        observed = entry.stat(follow_symlinks=False)
        require(not stat.S_ISLNK(observed.st_mode), "E_SYMLINK", f"Launcher JAR is symlinked: {entry.path}")
        require(stat.S_ISREG(observed.st_mode), "E_SPECIAL", f"Launcher JAR is not regular: {entry.path}")
        result.append(Path(entry.path))
    return result


def launcher_originals(target: Path) -> list[Path]:
    directory = real_directory(target, "Launcher target")
    result: list[Path] = []
    for entry in sorted(os.scandir(directory), key=lambda row: row.name.encode("utf-8")):
        if not entry.name.endswith(".jar.original"):
            continue
        if not is_launcher_main_jar(entry.name[: -len(".original")]):
            continue
        observed = entry.stat(follow_symlinks=False)
        require(not stat.S_ISLNK(observed.st_mode), "E_SYMLINK", f"Launcher original JAR is symlinked: {entry.path}")
        require(stat.S_ISREG(observed.st_mode), "E_SPECIAL", f"Launcher original JAR is not regular: {entry.path}")
        result.append(Path(entry.path))
    return result


@dataclass
class LauncherQuarantine:
    target: Path
    directory: Path
    moved: list[tuple[Path, Path, dict[str, Any]]]

    @classmethod
    def create(cls, target: Path) -> "LauncherQuarantine":
        target = real_directory(target, "Launcher target")
        quarantine = target / f".v934-release-quarantine-{os.getpid()}-{secrets.token_hex(6)}"
        require(not quarantine.exists() and not quarantine.is_symlink(), "E_QUARANTINE", "quarantine path exists")
        quarantine.mkdir(mode=0o700)
        moved: list[tuple[Path, Path, dict[str, Any]]] = []
        paths: list[Path] = [*launcher_candidates(target), *launcher_originals(target)]
        try:
            for source in paths:
                identity = file_identity(source, "pre-existing Launcher artifact")
                destination = quarantine / source.name
                require(not destination.exists() and not destination.is_symlink(), "E_QUARANTINE", "duplicate quarantine artifact")
                os.replace(source, destination)
                moved.append((source, destination, identity))
        except Exception:
            for source, destination, _ in reversed(moved):
                if destination.exists() and not source.exists() and not source.is_symlink():
                    os.replace(destination, source)
            if quarantine.exists():
                quarantine.rmdir()
            raise
        return cls(target, quarantine, moved)

    def restore(self) -> dict[str, Any]:
        generated: list[Path] = launcher_candidates(self.target)
        generated.extend(launcher_originals(self.target))
        for path in generated:
            observed = os.lstat(path)
            require(stat.S_ISREG(observed.st_mode) or stat.S_ISLNK(observed.st_mode), "E_QUARANTINE", "generated Launcher artifact is not removable")
            path.unlink()
        for source, destination, identity in self.moved:
            require(not source.exists() and not source.is_symlink(), "E_QUARANTINE", "restore destination appeared")
            os.replace(destination, source)
            require(file_identity(source, "restored Launcher artifact") == identity, "E_QUARANTINE", "restored Launcher artifact differs")
        require(not any(os.scandir(self.directory)), "E_QUARANTINE", "quarantine is not empty")
        self.directory.rmdir()
        require(not self.directory.exists() and not self.directory.is_symlink(), "E_QUARANTINE", "quarantine cleanup failed")
        return {
            "preexisting_artifacts": [
                {"name": source.name, **identity} for source, _, identity in self.moved
            ],
            "restored": True,
            "quarantine_removed": True,
        }


@dataclass
class ReactorJarQuarantine:
    root: Path
    directory: Path
    descriptors: dict[str, dict[str, str]]
    moved: list[tuple[Path, Path, dict[str, Any], dict[str, str]]]

    @classmethod
    def create(
        cls, root: Path, descriptors: dict[str, dict[str, str]]
    ) -> "ReactorJarQuarantine":
        parent = real_directory(root / "target", "reactor quarantine parent")
        directory = parent / f".v934-release-reactor-quarantine-{os.getpid()}-{secrets.token_hex(6)}"
        require(
            not directory.exists() and not directory.is_symlink(),
            "E_QUARANTINE",
            "reactor quarantine path exists",
        )
        directory.mkdir(mode=0o700)
        moved: list[tuple[Path, Path, dict[str, Any], dict[str, str]]] = []
        try:
            for index, descriptor in enumerate(descriptors.values()):
                source = root / PurePosixPath(descriptor["source_path"])
                real_directory(source.parent, f"reactor target {descriptor['module']}")
                if not source.exists() and not source.is_symlink():
                    continue
                identity = file_identity(
                    source, f"pre-existing reactor JAR {descriptor['module']}"
                )
                destination = directory / f"{index:02d}-{descriptor['file_name']}"
                os.replace(source, destination)
                moved.append((source, destination, identity, descriptor))
        except Exception:
            for source, destination, _, _ in reversed(moved):
                if destination.exists() and not source.exists() and not source.is_symlink():
                    os.replace(destination, source)
            if directory.exists():
                directory.rmdir()
            raise
        return cls(root, directory, descriptors, moved)

    def restore(self) -> dict[str, Any]:
        for descriptor in self.descriptors.values():
            generated = self.root / PurePosixPath(descriptor["source_path"])
            if not generated.exists() and not generated.is_symlink():
                continue
            observed = os.lstat(generated)
            require(
                stat.S_ISREG(observed.st_mode) or stat.S_ISLNK(observed.st_mode),
                "E_QUARANTINE",
                f"generated reactor artifact is not removable: {generated}",
            )
            generated.unlink()
        for source, destination, identity, _ in self.moved:
            require(
                not source.exists() and not source.is_symlink(),
                "E_QUARANTINE",
                "reactor restore destination appeared",
            )
            os.replace(destination, source)
            require(
                file_identity(source, "restored reactor JAR") == identity,
                "E_QUARANTINE",
                "restored reactor JAR differs",
            )
        require(
            not any(os.scandir(self.directory)),
            "E_QUARANTINE",
            "reactor quarantine is not empty",
        )
        self.directory.rmdir()
        require(
            not self.directory.exists() and not self.directory.is_symlink(),
            "E_QUARANTINE",
            "reactor quarantine cleanup failed",
        )
        return {
            "preexisting_artifacts": [
                {
                    "module": descriptor["module"],
                    "path": descriptor["source_path"],
                    **identity,
                }
                for _, _, identity, descriptor in self.moved
            ],
            "restored": True,
            "quarantine_removed": True,
        }


def copy_regular(source: Path, destination: Path) -> dict[str, Any]:
    require(not destination.exists() and not destination.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {destination}")
    source_fd, before = secure_open(source, "packaged Launcher JAR")
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    destination_fd = -1
    digest = hashlib.sha256()
    total = 0
    try:
        destination_fd = os.open(destination, flags, 0o644)
        while True:
            chunk = os.read(source_fd, 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
            total += len(chunk)
            view = memoryview(chunk)
            while view:
                count = os.write(destination_fd, view)
                require(count > 0, "E_OUTPUT", "short JAR copy write")
                view = view[count:]
        os.fsync(destination_fd)
        after = os.fstat(source_fd)
        require(
            (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns)
            == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns)
            and total == before.st_size,
            "E_JAR_DRIFT",
            "Launcher JAR changed while copied",
        )
    except Exception:
        if destination_fd >= 0:
            os.close(destination_fd)
            destination_fd = -1
        if destination.exists() or destination.is_symlink():
            destination.unlink()
        raise
    finally:
        os.close(source_fd)
        if destination_fd >= 0:
            os.close(destination_fd)
    return {"sha256": digest.hexdigest(), "size": total}


def zip_safe_name(name: str) -> None:
    require(name and "\\" not in name and CONTROL.search(name) is None, "E_JAR_ENTRY", "unsafe JAR entry name")
    pure = PurePosixPath(name)
    require(not pure.is_absolute(), "E_JAR_ENTRY", "absolute JAR entry")
    parts = name.rstrip("/").split("/")
    require(all(part not in {"", ".", ".."} for part in parts), "E_JAR_ENTRY", f"unsafe JAR entry: {name}")


def parse_manifest(data: bytes) -> dict[str, str]:
    require(len(data) <= 256 * 1024, "E_JAR_MANIFEST", "JAR manifest is too large")
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        reject("E_JAR_MANIFEST", f"JAR manifest is not UTF-8: {exc}")
    text = text.replace("\r\n", "\n")
    lines = text.split("\n")
    unfolded: list[str] = []
    for line in lines:
        if line.startswith(" "):
            require(bool(unfolded), "E_JAR_MANIFEST", "orphan manifest continuation")
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    result: dict[str, str] = {}
    for line in unfolded:
        if line == "":
            break
        require(": " in line, "E_JAR_MANIFEST", f"malformed manifest line: {line!r}")
        key, value = line.split(": ", 1)
        require(key not in result and bool(key), "E_JAR_MANIFEST", f"duplicate manifest attribute: {key}")
        result[key] = value
    require(result.get("Manifest-Version") == "1.0", "E_JAR_MANIFEST", "manifest version differs")
    require(result.get("Main-Class") == "org.springframework.boot.loader.launch.JarLauncher", "E_JAR_MANIFEST", "Boot Main-Class differs")
    require(result.get("Start-Class") == "com.foggyframework.mcp.launcher.McpLauncherApplication", "E_JAR_MANIFEST", "Launcher Start-Class differs")
    require(bool(result.get("Spring-Boot-Version")), "E_JAR_MANIFEST", "Spring-Boot-Version is absent")
    return result


def audit_reactor_jar_data(
    data: bytes,
    descriptor: Mapping[str, str],
    expected_tree: Mapping[str, Any],
    label: str,
) -> dict[str, Any]:
    require(bool(data) and len(data) <= 256 * 1024 * 1024, "E_JAR_LIBRARY", f"{label} size differs")
    try:
        bundle = zipfile.ZipFile(io.BytesIO(data), "r")
    except (OSError, zipfile.BadZipFile) as exc:
        reject("E_JAR_LIBRARY", f"cannot open {label}: {exc}")
    payload_rows: list[dict[str, Any]] = []
    metadata_rows: list[dict[str, Any]] = []
    seen: dict[str, str] = {}
    artifact = descriptor["artifact_id"]
    metadata_paths = {
        "META-INF/MANIFEST.MF",
        f"META-INF/maven/{MAVEN_METADATA_GROUP}/{artifact}/pom.properties",
        f"META-INF/maven/{MAVEN_METADATA_GROUP}/{artifact}/pom.xml",
    }
    try:
        for info in bundle.infolist():
            zip_safe_name(info.filename)
            canonical = info.filename[:-1] if info.is_dir() else info.filename
            folded = canonical.casefold()
            require(
                folded not in seen,
                "E_JAR_ENTRY",
                f"duplicate/colliding {label} entry: {seen.get(folded)!r}, {canonical!r}",
            )
            seen[folded] = canonical
            require(not (info.flag_bits & 0x1), "E_JAR_ENTRY", f"encrypted {label} entry: {canonical}")
            require(
                info.compress_type in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED},
                "E_JAR_ENTRY",
                f"unsupported {label} compression: {canonical}",
            )
            unix_mode = (info.external_attr >> 16) & 0xFFFF if info.create_system == 3 else 0
            if unix_mode not in (0, 0xFFFF):
                expected_type = stat.S_IFDIR if info.is_dir() else stat.S_IFREG
                require(
                    stat.S_IFMT(unix_mode) == expected_type,
                    "E_JAR_ENTRY",
                    f"link/special {label} entry: {canonical}",
                )
            if info.is_dir():
                require(info.file_size == 0, "E_JAR_ENTRY", f"non-empty {label} directory: {canonical}")
                continue
            require(
                info.file_size <= 128 * 1024 * 1024,
                "E_JAR_LIBRARY",
                f"oversized {label} entry: {canonical}",
            )
            member = bundle.read(info)
            require(len(member) == info.file_size, "E_JAR_LIBRARY", f"short {label} entry: {canonical}")
            row = {"path": canonical, "sha256": sha256_bytes(member), "size": len(member)}
            if canonical in metadata_paths:
                metadata_rows.append(row)
            else:
                payload_rows.append(row)
        require(bundle.testzip() is None, "E_JAR_LIBRARY", f"corrupt {label} entry")
    except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
        if isinstance(exc, PackageError):
            raise
        reject("E_JAR_LIBRARY", f"cannot audit {label}: {exc}")
    finally:
        bundle.close()
    require(
        {row["path"] for row in metadata_rows} == metadata_paths,
        "E_JAR_LIBRARY",
        f"{label} wrapper metadata set differs",
    )
    payload = content_tree(payload_rows)
    require(payload == expected_tree, "E_JAR_CLASS_TREE", f"{label} payload differs from tested target/classes")
    return {
        "payload": payload,
        "wrapper_metadata": content_tree(metadata_rows),
        "wrapper_policy": "exact-maven-manifest-pom-properties-pom-xml",
    }


def capture_reactor_artifacts(
    root: Path,
    descriptors: Mapping[str, dict[str, str]],
    tested_tree: Mapping[str, Any],
) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for file_name, descriptor in descriptors.items():
        path = root / PurePosixPath(descriptor["source_path"])
        raw = secure_bytes(path, f"fresh reactor JAR {descriptor['module']}", 256 * 1024 * 1024)
        identity = {"sha256": sha256_bytes(raw), "size": len(raw)}
        audit = audit_reactor_jar_data(
            raw,
            descriptor,
            module_output_tree(tested_tree, descriptor["module"]),
            f"reactor JAR {descriptor['module']}",
        )
        results.append({**descriptor, **identity, "content": audit})
    return sorted(results, key=lambda row: row["module"].encode("utf-8"))


def audit_jar(
    path: Path,
    reactor_names: set[str],
    runtime_descriptors: Mapping[str, dict[str, str]] | None = None,
    tested_tree: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    require(
        (runtime_descriptors is None) == (tested_tree is None),
        "E_JAR_LIBRARY",
        "runtime reactor descriptors/tested tree must be supplied together",
    )
    jar_identity = file_identity(path, "tested Launcher JAR")
    try:
        bundle = zipfile.ZipFile(path, "r")
    except (OSError, zipfile.BadZipFile) as exc:
        reject("E_JAR_ZIP", f"cannot open tested Launcher JAR: {exc}")
    libraries: list[dict[str, Any]] = []
    application_rows: list[dict[str, Any]] = []
    seen: dict[str, str] = {}
    manifest_data: bytes | None = None
    entry_count = 0
    try:
        for info in bundle.infolist():
            zip_safe_name(info.filename)
            canonical = info.filename[:-1] if info.is_dir() else info.filename
            folded = canonical.casefold()
            require(
                folded not in seen,
                "E_JAR_ENTRY",
                f"duplicate/colliding JAR entry: {seen.get(folded)!r}, {canonical!r}",
            )
            seen[folded] = canonical
            entry_count += 1
            require(not (info.flag_bits & 0x1), "E_JAR_ENTRY", f"encrypted JAR entry: {info.filename}")
            require(
                info.compress_type in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED},
                "E_JAR_ENTRY",
                f"unsupported JAR compression: {info.filename}",
            )
            unix_mode = (info.external_attr >> 16) & 0xFFFF if info.create_system == 3 else 0
            if unix_mode not in (0, 0xFFFF):
                expected_type = stat.S_IFDIR if info.is_dir() else stat.S_IFREG
                require(
                    stat.S_IFMT(unix_mode) == expected_type,
                    "E_JAR_ENTRY",
                    f"link/special JAR entry: {info.filename}",
                )
            if info.is_dir():
                require(info.file_size == 0, "E_JAR_ENTRY", f"non-empty JAR directory: {info.filename}")
                continue
            if info.filename == "META-INF/MANIFEST.MF":
                manifest_data = bundle.read(info)
            if info.filename.startswith("BOOT-INF/classes/"):
                relative = safe_relative(
                    info.filename[len("BOOT-INF/classes/") :],
                    "Launcher BOOT-INF/classes entry",
                )
                member = bundle.read(info)
                application_rows.append(
                    {"path": relative, "sha256": sha256_bytes(member), "size": len(member)}
                )
            if info.filename.startswith("BOOT-INF/lib/") and info.filename.endswith(".jar"):
                require(info.file_size > 0 and "/" not in info.filename[len("BOOT-INF/lib/"):], "E_JAR_LIBRARY", f"invalid nested library: {info.filename}")
                member = bundle.read(info)
                row: dict[str, Any] = {
                    "path": info.filename,
                    "sha256": sha256_bytes(member),
                    "size": len(member),
                    "crc32": f"{info.CRC:08x}",
                    "compression": "stored" if info.compress_type == zipfile.ZIP_STORED else "deflated",
                }
                file_name = Path(info.filename).name
                if runtime_descriptors is not None and file_name in runtime_descriptors:
                    assert tested_tree is not None
                    descriptor = runtime_descriptors[file_name]
                    row["reactor_binding"] = {
                        **descriptor,
                        "content": audit_reactor_jar_data(
                            member,
                            descriptor,
                            module_output_tree(tested_tree, descriptor["module"]),
                            f"nested reactor JAR {descriptor['module']}",
                        ),
                    }
                libraries.append(row)
        require(bundle.testzip() is None, "E_JAR_ZIP", "tested Launcher JAR has a corrupt ZIP entry")
    except (OSError, RuntimeError, zipfile.BadZipFile) as exc:
        if isinstance(exc, PackageError):
            raise
        reject("E_JAR_ZIP", f"cannot audit tested Launcher JAR: {exc}")
    finally:
        bundle.close()
    require(manifest_data is not None, "E_JAR_MANIFEST", "JAR manifest is absent")
    require(bool(libraries), "E_JAR_LIBRARY", "Spring Boot nested libraries are absent")
    libraries.sort(key=lambda row: row["path"].encode("utf-8"))
    internal = [row["path"] for row in libraries if Path(row["path"]).name in reactor_names]
    if runtime_descriptors is None:
        require(len(internal) == 12 and len(set(internal)) == 12, "E_JAR_LIBRARY", f"reactor nested library set must contain exactly 12 JARs, got {len(internal)}")
    else:
        expected_internal = {
            descriptor["nested_path"] for descriptor in runtime_descriptors.values()
        }
        require(
            set(internal) == expected_internal and len(internal) == len(expected_internal) == 12,
            "E_JAR_LIBRARY",
            f"reactor nested library set differs: missing={sorted(expected_internal - set(internal))} "
            f"extra={sorted(set(internal) - expected_internal)}",
        )
    attributes = parse_manifest(manifest_data)
    application_tree = content_tree(application_rows)
    if tested_tree is not None:
        require(
            application_tree == module_output_tree(tested_tree, LAUNCHER),
            "E_JAR_CLASS_TREE",
            "Launcher BOOT-INF/classes differs from sealed tested target/classes",
        )
    return {
        "path": APP_JAR_NAME,
        **jar_identity,
        "zip_entries": entry_count,
        "manifest": {
            "path": "META-INF/MANIFEST.MF",
            "sha256": sha256_bytes(manifest_data),
            "size": len(manifest_data),
            "attributes": attributes,
        },
        "application_tree": application_tree,
        "nested_library_count": len(libraries),
        "nested_libraries": libraries,
        "reactor_nested_library_count": len(internal),
        "reactor_nested_libraries": internal,
    }


def bind_reactor_source_artifacts(
    jar_audit: dict[str, Any], source_artifacts: Any
) -> dict[str, Any]:
    require(type(source_artifacts) is list, "E_JAR_LIBRARY", "reactor source artifact receipt is not a list")
    nested = {
        row["path"]: row
        for row in jar_audit["nested_libraries"]
        if type(row) is dict and "reactor_binding" in row
    }
    expected: list[dict[str, Any]] = []
    for module in RUNTIME_REACTOR_MODULES:
        matches = [
            row
            for row in nested.values()
            if row["reactor_binding"]["module"] == module
        ]
        require(len(matches) == 1, "E_JAR_LIBRARY", f"nested reactor binding differs: {module}")
        row = matches[0]
        expected.append(
            {
                **row["reactor_binding"],
                "sha256": row["sha256"],
                "size": row["size"],
            }
        )
    expected.sort(key=lambda row: row["module"].encode("utf-8"))
    require(
        source_artifacts == expected,
        "E_JAR_LIBRARY",
        "fresh reactor source JARs differ from exact fat-JAR nested artifacts",
    )
    result = dict(jar_audit)
    result["reactor_source_artifacts"] = expected
    result["same_tested_tree"] = {
        "launcher": "exact-BOOT-INF/classes-to-foggy-mcp-launcher-target/classes",
        "reactor": "exact-12-fresh-module-jars-to-BOOT-INF/lib-with-target/classes-payload",
        "status": "exact",
    }
    return result


def runtime_base_identity() -> dict[str, Any]:
    return {
        "tag_reference": RUNTIME_BASE_TAG_REFERENCE,
        "pinned_reference": RUNTIME_BASE_PINNED_REFERENCE,
        "index_digest": RUNTIME_BASE_INDEX_DIGEST,
        "manifest_digest": RUNTIME_BASE_MANIFEST_DIGEST,
        "config_digest": RUNTIME_BASE_CONFIG_DIGEST,
        "platform": dict(RUNTIME_BASE_PLATFORM),
    }


def validate_runtime_base_identity(value: Any, label: str) -> dict[str, Any]:
    row = exact_keys(
        value,
        (
            "tag_reference",
            "pinned_reference",
            "index_digest",
            "manifest_digest",
            "config_digest",
            "platform",
        ),
        "E_BASE_IMAGE",
        label,
    )
    exact_keys(
        row["platform"],
        ("os", "architecture"),
        "E_BASE_IMAGE",
        f"{label} platform",
    )
    require(
        row == runtime_base_identity(),
        "E_BASE_IMAGE",
        f"{label} differs from the frozen linux/amd64 base image",
    )
    return row


def validate_dockerfile(root: Path) -> dict[str, Any]:
    path = root / DOCKERFILE
    raw = secure_bytes(path, "runtime-only release Dockerfile", 1024 * 1024)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        reject("E_DOCKERFILE", f"Dockerfile is not UTF-8: {exc}")
    logical = [line.strip() for line in text.splitlines() if line.strip() and not line.lstrip().startswith("#")]
    instructions = [line.split(None, 1)[0].upper() for line in logical]
    require(all(item in {"FROM", "ARG", "WORKDIR", "COPY", "USER", "ENTRYPOINT"} for item in instructions), "E_DOCKERFILE", f"runtime Dockerfile contains forbidden instruction: {instructions}")
    require(instructions.count("FROM") == 1 and instructions.count("COPY") == 1, "E_DOCKERFILE", "runtime Dockerfile FROM/COPY cardinality differs")
    source = next(line for line in logical if line.upper().startswith("FROM "))
    require(
        source == RUNTIME_BASE_FROM,
        "E_BASE_IMAGE",
        "runtime Dockerfile base image reference/platform differs",
    )
    copy = next(line for line in logical if line.upper().startswith("COPY "))
    require("${TESTED_JAR}" in copy and "/app/app.jar" in copy, "E_DOCKERFILE", "runtime Dockerfile does not copy the tested JAR")
    joined = "\n".join(logical).lower()
    require("mvn" not in joined and "gradle" not in joined and "skiptests" not in joined and "src/" not in joined, "E_DOCKERFILE", "runtime Dockerfile contains a source rebuild path")
    return {
        "path": DOCKERFILE.as_posix(),
        "sha256": sha256_bytes(raw),
        "size": len(raw),
        "base_image": runtime_base_identity(),
    }


def validate_docker_context(
    context: Path,
    jar: dict[str, Any],
    dockerfile: dict[str, Any],
) -> dict[str, Any]:
    rows = walk_selected(context, lambda _name: True, "isolated Docker build context")
    entries = [
        {"path": path, **identity}
        for path, identity in rows
    ]
    expected = [
        {
            "path": "Dockerfile",
            "sha256": dockerfile["sha256"],
            "size": dockerfile["size"],
        },
        {"path": APP_JAR_NAME, "sha256": jar["sha256"], "size": jar["size"]},
    ]
    require(
        entries == expected,
        "E_CONTEXT_POLICY",
        "Docker build context must contain exactly Dockerfile and tested app.jar",
    )
    return {
        "policy": "isolated-exact-two-file-context",
        "file_count": 2,
        "files": entries,
    }


def validate_docker_context_receipt(
    value: Any,
    jar: dict[str, Any],
    dockerfile: dict[str, Any],
    label: str,
) -> dict[str, Any]:
    expected = {
        "policy": "isolated-exact-two-file-context",
        "file_count": 2,
        "files": [
            {
                "path": "Dockerfile",
                "sha256": dockerfile["sha256"],
                "size": dockerfile["size"],
            },
            {"path": APP_JAR_NAME, "sha256": jar["sha256"], "size": jar["size"]},
        ],
    }
    require(value == expected, "E_CONTEXT_POLICY", f"{label} differs")
    return value


def require_image_cleanup(errors: Sequence[str]) -> None:
    require(
        not errors,
        "E_IMAGE_CLEANUP",
        f"Docker cleanup failed: {list(errors)}",
    )


def docker_inspect_identity(root: Path, reference: str, label: str) -> dict[str, Any]:
    observed = run_capture(
        [
            "docker",
            "image",
            "inspect",
            "--format",
            "{{.Id}}\\n{{.Os}}\\n{{.Architecture}}",
            reference,
        ],
        root,
        "E_BASE_IMAGE" if reference == RUNTIME_BASE_PINNED_REFERENCE else "E_IMAGE",
        f"{label} inspect",
    ).splitlines()
    require(
        len(observed) == 3
        and re.fullmatch(r"sha256:[0-9a-f]{64}", observed[0]) is not None
        and observed[1] in {"linux"}
        and observed[2] in {"amd64"},
        "E_BASE_IMAGE" if reference == RUNTIME_BASE_PINNED_REFERENCE else "E_IMAGE",
        f"{label} inspect identity differs",
    )
    return {
        "engine_image_id": observed[0],
        "platform": {"os": observed[1], "architecture": observed[2]},
    }


def inspect_runtime_base_image(root: Path) -> dict[str, Any]:
    raw_index_text = run_capture(
        [
            "docker",
            "buildx",
            "imagetools",
            "inspect",
            "--raw",
            RUNTIME_BASE_INDEX_REFERENCE,
        ],
        root,
        "E_BASE_IMAGE",
        "runtime base OCI index inspect",
    )
    raw_index = raw_index_text.encode("utf-8")
    require(
        sha256_bytes(raw_index) == RUNTIME_BASE_INDEX_DIGEST.removeprefix("sha256:"),
        "E_BASE_IMAGE",
        "runtime base OCI index bytes differ from the frozen digest",
    )
    index = parse_json(raw_index, "runtime base OCI index")
    manifests = index.get("manifests")
    require(
        index.get("schemaVersion") == 2 and type(manifests) is list,
        "E_BASE_IMAGE",
        "runtime base OCI index schema differs",
    )
    platform_matches = [
        row
        for row in manifests
        if type(row) is dict
        and row.get("digest") == RUNTIME_BASE_MANIFEST_DIGEST
        and row.get("platform") == RUNTIME_BASE_PLATFORM
    ]
    require(
        len(platform_matches) == 1,
        "E_BASE_IMAGE",
        "runtime base OCI index does not bind one exact linux/amd64 manifest",
    )
    raw_manifest_text = run_capture(
        [
            "docker",
            "buildx",
            "imagetools",
            "inspect",
            "--raw",
            RUNTIME_BASE_PINNED_REFERENCE,
        ],
        root,
        "E_BASE_IMAGE",
        "runtime base OCI manifest inspect",
    )
    raw_manifest = raw_manifest_text.encode("utf-8")
    require(
        sha256_bytes(raw_manifest) == RUNTIME_BASE_MANIFEST_DIGEST.removeprefix("sha256:"),
        "E_BASE_IMAGE",
        "runtime base OCI manifest bytes differ from the pinned digest",
    )
    manifest = parse_json(raw_manifest, "runtime base OCI manifest")
    config = exact_keys(
        manifest.get("config"),
        ("mediaType", "digest", "size"),
        "E_BASE_IMAGE",
        "runtime base OCI config descriptor",
    )
    require(
        manifest.get("schemaVersion") == 2
        and manifest.get("mediaType")
        in {
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.docker.distribution.manifest.v2+json",
        }
        and config["mediaType"]
        in {
            "application/vnd.oci.image.config.v1+json",
            "application/vnd.docker.container.image.v1+json",
        }
        and config["digest"] == RUNTIME_BASE_CONFIG_DIGEST
        and type(config["size"]) is int
        and config["size"] > 0,
        "E_BASE_IMAGE",
        "runtime base OCI manifest/config descriptor differs",
    )
    return runtime_base_identity()


def docker_image(
    root: Path,
    output: Path,
    run_id: str,
    jar: dict[str, Any],
    *,
    receipt_safe: bool = False,
    failure_context: FailureReceiptContext | None = None,
) -> dict[str, Any]:
    dockerfile = validate_dockerfile(root)
    slug = re.sub(r"[^a-z0-9_.-]+", "-", run_id.lower()).strip("-.")[:50] or "run"
    token = secrets.token_hex(6)
    tag = f"v934-release-package:{slug}-{os.getpid()}-{token}"
    container = f"v934-release-readback-{slug[:30]}-{os.getpid()}-{token}"
    log_path = output / DOCKER_LOG_NAME
    image_id = ""
    base_image: dict[str, Any] | None = None
    embedded: dict[str, Any] | None = None
    created = False
    build_completed = False
    cleanup_errors: list[str] = []
    readback = output / f".embedded-app-{token}.jar"
    context_receipt: dict[str, Any] | None = None
    with tempfile.TemporaryDirectory(prefix="v934-release-docker-context-") as context_name:
        context = real_directory(Path(context_name), "isolated Docker build context")
        copied_dockerfile = copy_regular(root / DOCKERFILE, context / "Dockerfile")
        copied_jar = copy_regular(output / APP_JAR_NAME, context / APP_JAR_NAME)
        require(
            copied_dockerfile
            == {"sha256": dockerfile["sha256"], "size": dockerfile["size"]},
            "E_CONTEXT_POLICY",
            "isolated Dockerfile copy differs",
        )
        require(
            copied_jar == {"sha256": jar["sha256"], "size": jar["size"]},
            "E_CONTEXT_POLICY",
            "isolated tested JAR copy differs",
        )
        context_receipt = validate_docker_context(context, jar, dockerfile)
        primary_error: BaseException | None = None
        with open(log_path, "xb") as log:
            try:
                run_logged(
                    [
                        "docker",
                        "build",
                        "--pull=false",
                        "--file",
                        str(context / "Dockerfile"),
                        "--build-arg",
                        f"TESTED_JAR={APP_JAR_NAME}",
                        "--tag",
                        tag,
                        str(context),
                    ],
                    root,
                    log,
                    "runtime-only Docker image build",
                    receipt_safe=receipt_safe,
                )
                build_completed = True
                base_image = inspect_runtime_base_image(root)

                def inspect_runtime_image() -> dict[str, Any]:
                    inspect = docker_inspect_identity(root, tag, "runtime image")
                    require(
                        inspect["platform"] == RUNTIME_BASE_PLATFORM,
                        "E_IMAGE",
                        "runtime image platform differs from the frozen base platform",
                    )
                    return inspect

                inspect = guarded_eimage_boundary(
                    failure_context,
                    "package-image-runtime-inspect",
                    inspect_runtime_image,
                )
                image_id = inspect["engine_image_id"]
                run_logged(
                    ["docker", "create", "--name", container, tag],
                    root,
                    log,
                    "Docker readback container create",
                    receipt_safe=receipt_safe,
                )
                created = True
                guarded_eimage_boundary(
                    failure_context,
                    "package-image-readback-precondition",
                    lambda: require(
                        not readback.exists() and not readback.is_symlink(),
                        "E_IMAGE",
                        "image readback path exists",
                    ),
                )
                run_logged(
                    ["docker", "cp", f"{container}:/app/app.jar", str(readback)],
                    root,
                    log,
                    "Docker embedded JAR readback",
                    receipt_safe=receipt_safe,
                )
                embedded = file_identity(readback, "image embedded /app/app.jar")
                readback.unlink()
                require(
                    embedded == {"sha256": jar["sha256"], "size": jar["size"]},
                    "E_IMAGE_DRIFT",
                    "image /app/app.jar differs from tested JAR",
                )
            except BaseException as exc:
                primary_error = exc
            finally:
                try:
                    if readback.exists() or readback.is_symlink():
                        observed = os.lstat(readback)
                        if stat.S_ISREG(observed.st_mode) or stat.S_ISLNK(observed.st_mode):
                            readback.unlink()
                        else:
                            cleanup_errors.append("readback-special")
                except OSError:
                    cleanup_errors.append("readback-remove")
                if created:
                    try:
                        process = subprocess.run(
                            ["docker", "rm", "--force", container],
                            cwd=root,
                            stdout=subprocess.DEVNULL if receipt_safe else log,
                            stderr=subprocess.DEVNULL if receipt_safe else subprocess.STDOUT,
                        )
                        if receipt_safe:
                            log.write(
                                (json.dumps({"label": "Docker readback container cleanup", "exit_code": process.returncode}, separators=(",", ":")) + "\n").encode()
                            )
                        if process.returncode != 0:
                            cleanup_errors.append("container-remove")
                    except OSError:
                        cleanup_errors.append("container-remove-start")
                try:
                    process = subprocess.run(
                        ["docker", "image", "rm", "--force", tag],
                        cwd=root,
                        stdout=subprocess.DEVNULL if receipt_safe else log,
                        stderr=subprocess.DEVNULL if receipt_safe else subprocess.STDOUT,
                    )
                    if receipt_safe:
                        log.write(
                            (json.dumps({"label": "Docker image cleanup", "exit_code": process.returncode}, separators=(",", ":")) + "\n").encode()
                        )
                    if (build_completed or image_id) and process.returncode != 0:
                        cleanup_errors.append("image-remove")
                except OSError:
                    cleanup_errors.append("image-remove-start")
                try:
                    log.flush()
                    os.fsync(log.fileno())
                except OSError:
                    cleanup_errors.append("log-fsync")
        try:
            container_check = subprocess.run(
                ["docker", "container", "inspect", container],
                cwd=root,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if container_check.returncode == 0:
                cleanup_errors.append("container-survived")
        except OSError:
            cleanup_errors.append("container-inspect")
        try:
            image_check = subprocess.run(
                ["docker", "image", "inspect", tag],
                cwd=root,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            if image_check.returncode == 0:
                cleanup_errors.append("image-survived")
        except OSError:
            cleanup_errors.append("image-inspect")
        context_after = validate_docker_context(context, jar, dockerfile)
        require(
            context_after == context_receipt,
            "E_CONTEXT_POLICY",
            "isolated Docker build context changed during image build",
        )
        require_image_cleanup(cleanup_errors)
        if primary_error is not None:
            raise primary_error
    require(
        not context.exists() and not context.is_symlink(),
        "E_CONTEXT_CLEANUP",
        "isolated Docker build context survived cleanup",
    )
    guarded_eimage_boundary(
        failure_context,
        "package-image-receipt-completeness",
        lambda: require(
            embedded is not None
            and image_id
            and context_receipt is not None
            and base_image is not None,
            "E_IMAGE",
            "Docker image receipt is incomplete",
        ),
    )
    result = {
        "schema_version": 2,
        "kind": "v934-runtime-image-receipt",
        "status": "passed",
        "run_id": run_id,
        "dockerfile": dockerfile,
        "context": context_receipt,
        "base_image": base_image,
        "image_id": image_id,
        "tested_jar": {"path": APP_JAR_NAME, "sha256": jar["sha256"], "size": jar["size"]},
        "embedded_jar": {"path": "/app/app.jar", **embedded},
        "cleanup": {
            "container_removed": True,
            "image_tag_removed": True,
            "readback_removed": True,
            "context_removed": True,
        },
        "log": binding(log_path, relative_to=output, label="Docker build log"),
    }
    write_new(output / IMAGE_MANIFEST_NAME, canonical_json(result))
    return result


def package_command_impl(
    args: argparse.Namespace,
    failure_context: FailureReceiptContext | None = None,
    *,
    output_dir: Path | None = None,
    output_is_precreated: bool = False,
) -> dict[str, Any]:
    validate_maven_environment(os.environ)
    root = repo_root(args.repo_root)
    run_id = safe_run_id(args.run_id)
    modules, freeze_binding = frozen_modules(root)
    authority = validate_step4(root, run_id, args.step4_run_root)
    require(current_head(root) == authority.git_head, "E_SOURCE_DRIFT", "current HEAD differs from Step 4 tested HEAD")
    source_before = source_seal(root)
    require(source_before["git_head"] == authority.git_head and source_before["sha256"] == authority.source_sha256, "E_SOURCE_DRIFT", "current tracked source differs from Step 4 tested source")
    class_validation_before = validate_class_universe(root, authority)
    report_validation_before = validate_report_inventory(root, authority)
    tested_tree_before = tested_tree_snapshot(root, modules)
    reports_before = report_snapshot(root, modules)
    requested_output = args.output_dir if output_dir is None else output_dir
    output = (
        real_directory(requested_output, "receipt package staging directory")
        if output_is_precreated
        else create_output(requested_output)
    )
    write_new(
        output / VALIDATOR_LOG_NAME,
        (class_validation_before + report_validation_before).encode("utf-8"),
    )
    coordinates = {module: pom_coordinates(root, module) for module in modules}
    reactor_names = {f"{artifact}-{version}.jar" for artifact, version in coordinates.values()}
    runtime_descriptors = runtime_reactor_coordinates(coordinates)
    launcher_artifact, launcher_version = coordinates[LAUNCHER]
    expected_launcher_name = f"{launcher_artifact}-{launcher_version}.jar"
    commands = maven_commands(modules)
    reactor_quarantine = ReactorJarQuarantine.create(root, runtime_descriptors)
    launcher_quarantine: LauncherQuarantine | None = None
    launcher_cleanup: dict[str, Any] | None = None
    reactor_cleanup: dict[str, Any] | None = None
    jar_audit: dict[str, Any] | None = None
    reactor_artifacts: list[dict[str, Any]] | None = None
    try:
        launcher_quarantine = LauncherQuarantine.create(root / LAUNCHER / "target")
        try:
            with open(output / MAVEN_LOG_NAME, "xb") as log:
                if failure_context is not None:
                    failure_context.set_subphase("package-maven-reactor")
                run_logged(
                    commands[0],
                    root,
                    log,
                    "all frozen modules jar/install",
                    receipt_safe=failure_context is not None,
                )
                reactor_artifacts = capture_reactor_artifacts(
                    root, runtime_descriptors, tested_tree_before
                )
                if failure_context is not None:
                    failure_context.set_subphase("package-maven-launcher-jar")
                run_logged(
                    commands[1],
                    root,
                    log,
                    "Launcher jar/repackage",
                    receipt_safe=failure_context is not None,
                )
                reactor_artifacts_after = capture_reactor_artifacts(
                    root, runtime_descriptors, tested_tree_before
                )
                require(
                    reactor_artifacts_after == reactor_artifacts,
                    "E_JAR_DRIFT",
                    "fresh reactor JAR set changed during Launcher repackaging",
                )
            candidates = launcher_candidates(root / LAUNCHER / "target")
            require(len(candidates) == 1, "E_JAR_CARDINALITY", f"expected one Launcher JAR, found {[path.name for path in candidates]}")
            require(candidates[0].name == expected_launcher_name, "E_JAR_CARDINALITY", "Launcher JAR name differs from POM coordinates")
            copy_identity = copy_regular(candidates[0], output / APP_JAR_NAME)
            audited = audit_jar(
                output / APP_JAR_NAME,
                reactor_names,
                runtime_descriptors,
                tested_tree_before,
            )
            jar_audit = bind_reactor_source_artifacts(audited, reactor_artifacts)
            require(copy_identity == {"sha256": jar_audit["sha256"], "size": jar_audit["size"]}, "E_JAR_DRIFT", "copied JAR identity differs from audit")
        finally:
            if launcher_quarantine is not None:
                launcher_cleanup = launcher_quarantine.restore()
    finally:
        reactor_cleanup = reactor_quarantine.restore()
    assert jar_audit is not None and launcher_cleanup is not None and reactor_cleanup is not None
    if failure_context is not None:
        failure_context.set_subphase("package-image")
    image = docker_image(
        root,
        output,
        run_id,
        jar_audit,
        receipt_safe=failure_context is not None,
        failure_context=failure_context,
    )
    if failure_context is not None:
        failure_context.set_subphase("package-postconditions")
    maven_log_binding = binding(
        output / MAVEN_LOG_NAME,
        relative_to=output,
        label="Maven invocation log",
    )
    docker_log_binding = binding(
        output / DOCKER_LOG_NAME,
        relative_to=output,
        label="Docker build log",
    )
    require(
        image["log"] == docker_log_binding,
        "E_RECEIPT_DRIFT",
        "Docker log changed after image receipt publication",
    )
    image_manifest_binding = binding(output / IMAGE_MANIFEST_NAME, relative_to=output, label="image manifest")
    class_validation_after = validate_class_universe(root, authority)
    report_validation_after = validate_report_inventory(root, authority)
    with open(output / VALIDATOR_LOG_NAME, "ab") as validator_log:
        validator_log.write((class_validation_after + report_validation_after).encode("utf-8"))
        validator_log.flush()
        os.fsync(validator_log.fileno())
    validator_log_binding = binding(
        output / VALIDATOR_LOG_NAME,
        relative_to=output,
        label="tested-tree validation log",
    )
    tested_tree_after = tested_tree_snapshot(root, modules)
    reports_after = report_snapshot(root, modules)
    source_after = source_seal(root)
    require_unchanged("tracked source/HEAD", source_before, source_after, "E_SOURCE_DRIFT")
    require_unchanged(
        "tested production output trees",
        tested_tree_before,
        tested_tree_after,
        "E_TESTED_TREE_DRIFT",
    )
    require_unchanged("TEST XML reports", reports_before, reports_after, "E_REPORT_DRIFT")
    authority_after = validate_step4(root, run_id, authority.root)
    require(authority.receipts == authority_after.receipts, "E_STEP4_DRIFT", "Step 4 authority files changed during packaging")
    manifest = {
        "schema_version": 4,
        "kind": "v934-tested-output-tree-package",
        "status": "passed",
        "run_id": run_id,
        "git_head": authority.git_head,
        "source": {
            "file_count": source_before["file_count"],
            "sha256": authority.source_sha256,
            "semantics": "canonical tracked worktree bytes equal Step4 before/after seal",
        },
        "step4": {
            "run_root": authority.root.relative_to(root).as_posix(),
            "not_before_ns": authority.not_before_ns,
            **authority.receipts,
        },
        "reactor": {
            "module_count": 24,
            "modules": modules,
            "contract_freeze": freeze_binding,
        },
        "seals": {
            "tested_tree_before": tested_tree_before,
            "tested_tree_after": tested_tree_after,
            "reports_before": reports_before,
            "reports_after": reports_after,
            "source_before": source_before,
            "source_after": source_after,
        },
        "maven": {
            "policy": "direct plugin goals only; no lifecycle, test/external skip, or test selectors",
            "authority": authority.maven_authority,
            "invocation_count": 2,
            "invocations": [
                {"ordinal": 1, "projects": modules, "goals": ["jar:jar", "install:install"]},
                {"ordinal": 2, "projects": [LAUNCHER], "goals": ["jar:jar", "spring-boot:repackage"]},
            ],
            "log": maven_log_binding,
        },
        "jar": jar_audit,
        "validation_log": validator_log_binding,
        "image": {
            "manifest": image_manifest_binding,
            "log": docker_log_binding,
            "context": image["context"],
            "base_image": image["base_image"],
            "image_id": image["image_id"],
            "embedded_jar": image["embedded_jar"],
        },
        "cleanup": {
            "launcher": launcher_cleanup,
            "reactor": reactor_cleanup,
            "docker": image["cleanup"],
            "status": "exact",
        },
    }
    if failure_context is not None:
        failure_context.set_subphase("package-manifest")
    write_new(output / PACKAGE_MANIFEST_NAME, canonical_json(manifest))
    if failure_context is not None:
        failure_context.set_subphase("package-internal-verify")
    verified = verify_package(
        root,
        output / PACKAGE_MANIFEST_NAME,
        output / APP_JAR_NAME,
        expected_run_id=run_id,
    )
    return {
        "command": "package",
        "status": "passed",
        "run_id": run_id,
        "git_head": authority.git_head,
        "output_dir": str(output),
        "jar_sha256": jar_audit["sha256"],
        "jar_size": jar_audit["size"],
        "nested_libraries": jar_audit["nested_library_count"],
        "package_manifest_sha256": file_identity(output / PACKAGE_MANIFEST_NAME, "package manifest")["sha256"],
        "image_manifest_sha256": image_manifest_binding["sha256"],
        "verified": verified["status"] == "passed",
    }


def package_command(
    args: argparse.Namespace,
    failure_context: FailureReceiptContext | None = None,
) -> dict[str, Any]:
    if failure_context is None:
        return package_command_impl(args)
    staging = create_receipt_package_staging(args.output_dir)
    primary_error: BaseException | None = None
    try:
        result = package_command_impl(
            args,
            failure_context,
            output_dir=staging,
            output_is_precreated=True,
        )
        # Final publication is the durable package-manifest boundary.  Keep the
        # receipt inside the approved enum even though all construction and
        # internal verification completed in the isolated staging directory.
        failure_context.set_subphase("package-manifest")
        published = publish_staged_package(staging, args.output_dir)
        result["output_dir"] = str(published)
        return result
    except BaseException as exc:
        primary_error = exc
        raise
    finally:
        if staging.exists() or staging.is_symlink():
            try:
                cleanup_flat_directory(staging, "receipt package staging directory")
            except BaseException as cleanup_error:
                if primary_error is None:
                    raise
                if isinstance(primary_error, KeyboardInterrupt):
                    raise primary_error
                raise PackageError(
                    "E_OUTPUT",
                    "receipt package staging cleanup failed",
                ) from cleanup_error


def validate_manifest_binding_shape(value: Any, path: str, label: str) -> dict[str, Any]:
    row = exact_keys(value, ("path", "sha256", "size"), "E_MANIFEST", label)
    require(row["path"] == path, "E_MANIFEST", f"{label} path differs")
    require(type(row["sha256"]) is str and HEX64.fullmatch(row["sha256"]), "E_MANIFEST", f"{label} SHA differs")
    require(type(row["size"]) is int and row["size"] >= 0, "E_MANIFEST", f"{label} size differs")
    return row


def verify_bound_sibling(
    directory: Path,
    value: Any,
    name: str,
    label: str,
) -> tuple[Path, dict[str, Any]]:
    row = validate_manifest_binding_shape(value, name, f"{label} binding")
    path = directory / name
    observed = file_identity(path, label)
    require(
        observed == {"sha256": row["sha256"], "size": row["size"]},
        "E_RECEIPT_DRIFT",
        f"{label} differs from its package binding",
    )
    require(observed["size"] > 0, "E_RECEIPT_DRIFT", f"{label} is empty")
    return path, row


def validate_identity_shape(value: Any, label: str, *, positive_size: bool = False) -> dict[str, Any]:
    row = exact_keys(value, ("sha256", "size"), "E_MANIFEST", label)
    require(
        type(row["sha256"]) is str and HEX64.fullmatch(row["sha256"]) is not None,
        "E_MANIFEST",
        f"{label} SHA differs",
    )
    require(
        type(row["size"]) is int and row["size"] >= (1 if positive_size else 0),
        "E_MANIFEST",
        f"{label} size differs",
    )
    return row


def validate_image_receipt(
    root: Path,
    path: Path,
    manifest_binding: dict[str, Any],
    docker_log_binding: dict[str, Any],
    jar: dict[str, Any],
    run_id: str,
) -> dict[str, Any]:
    raw = secure_bytes(path, "runtime image manifest", 4 * 1024 * 1024)
    require(
        {"sha256": sha256_bytes(raw), "size": len(raw)}
        == {
            "sha256": manifest_binding["sha256"],
            "size": manifest_binding["size"],
        },
        "E_RECEIPT_DRIFT",
        "runtime image manifest changed while verified",
    )
    receipt = parse_json(raw, "runtime image manifest")
    exact_keys(
        receipt,
        (
            "schema_version",
            "kind",
            "status",
            "run_id",
            "dockerfile",
            "context",
            "base_image",
            "image_id",
            "tested_jar",
            "embedded_jar",
            "cleanup",
            "log",
        ),
        "E_IMAGE_MANIFEST",
        "runtime image manifest",
    )
    require(
        receipt["schema_version"] == 2
        and type(receipt["schema_version"]) is int
        and receipt["kind"] == "v934-runtime-image-receipt"
        and receipt["status"] == "passed"
        and receipt["run_id"] == run_id,
        "E_IMAGE_MANIFEST",
        "runtime image receipt identity differs",
    )
    dockerfile = validate_dockerfile(root)
    require(
        receipt["dockerfile"] == dockerfile,
        "E_IMAGE_MANIFEST",
        "runtime image Dockerfile binding differs",
    )
    validate_docker_context_receipt(
        receipt["context"], jar, dockerfile, "runtime image context receipt"
    )
    base_image = validate_runtime_base_identity(
        receipt["base_image"], "runtime image base identity"
    )
    require(
        dockerfile["base_image"] == base_image,
        "E_BASE_IMAGE",
        "runtime image Dockerfile/base receipt binding differs",
    )
    tested_jar = exact_keys(
        receipt["tested_jar"],
        ("path", "sha256", "size"),
        "E_IMAGE_MANIFEST",
        "runtime image tested JAR",
    )
    require(
        tested_jar
        == {"path": APP_JAR_NAME, "sha256": jar["sha256"], "size": jar["size"]},
        "E_IMAGE_DRIFT",
        "runtime image tested JAR binding differs",
    )
    embedded = exact_keys(
        receipt["embedded_jar"],
        ("path", "sha256", "size"),
        "E_IMAGE_MANIFEST",
        "runtime image embedded JAR",
    )
    require(
        embedded
        == {"path": "/app/app.jar", "sha256": jar["sha256"], "size": jar["size"]},
        "E_IMAGE_DRIFT",
        "runtime image embedded JAR differs",
    )
    log = validate_manifest_binding_shape(
        receipt["log"], DOCKER_LOG_NAME, "runtime image Docker log binding"
    )
    require(
        log == docker_log_binding,
        "E_RECEIPT_DRIFT",
        "runtime image/package Docker log bindings differ",
    )
    require(
        type(receipt["image_id"]) is str
        and re.fullmatch(r"sha256:[0-9a-f]{64}", receipt["image_id"]) is not None,
        "E_IMAGE_MANIFEST",
        "runtime image ID differs",
    )
    cleanup = exact_keys(
        receipt["cleanup"],
        (
            "container_removed",
            "image_tag_removed",
            "readback_removed",
            "context_removed",
        ),
        "E_IMAGE_MANIFEST",
        "runtime image cleanup",
    )
    require(
        all(cleanup[name] is True for name in cleanup),
        "E_IMAGE_MANIFEST",
        "runtime image cleanup is incomplete",
    )
    return receipt


def validate_tree_snapshot(value: Any, modules: Sequence[str], label: str) -> dict[str, Any]:
    row = exact_keys(value, ("files", "module_counts", "sha256"), "E_MANIFEST", label)
    require(type(row["files"]) is int and row["files"] > 0, "E_MANIFEST", f"{label} file count differs")
    require(type(row["module_counts"]) is dict and set(row["module_counts"]) == set(modules), "E_MANIFEST", f"{label} module set differs")
    counts = row["module_counts"]
    require(
        all(type(counts[module]) is int and counts[module] >= 0 for module in modules)
        and sum(counts[module] for module in modules) == row["files"],
        "E_MANIFEST",
        f"{label} module counts differ",
    )
    require(type(row["sha256"]) is str and HEX64.fullmatch(row["sha256"]), "E_MANIFEST", f"{label} SHA differs")
    return row


def validate_tested_tree_snapshot(
    value: Any, modules: Sequence[str], label: str
) -> dict[str, Any]:
    row = exact_keys(
        value,
        ("files", "module_counts", "sha256", "entries"),
        "E_MANIFEST",
        label,
    )
    require(
        type(row["files"]) is int and row["files"] > 0,
        "E_MANIFEST",
        f"{label} file count differs",
    )
    require(
        type(row["module_counts"]) is dict
        and set(row["module_counts"]) == set(modules),
        "E_MANIFEST",
        f"{label} module set differs",
    )
    require(type(row["entries"]) is list, "E_MANIFEST", f"{label} entries differ")
    require(
        len(row["entries"]) == row["files"],
        "E_MANIFEST",
        f"{label} entry count differs",
    )
    prefixes = {f"{module}/target/classes/": module for module in modules}
    observed_counts = {module: 0 for module in modules}
    canonical_entries: list[dict[str, Any]] = []
    previous_path: str | None = None
    for index, entry in enumerate(row["entries"]):
        item = exact_keys(
            entry,
            ("path", "sha256", "size"),
            "E_MANIFEST",
            f"{label} entry {index}",
        )
        path = safe_relative(item["path"], f"{label} entry {index} path")
        matching = [module for prefix, module in prefixes.items() if path.startswith(prefix)]
        require(
            len(matching) == 1 and len(path) > len(f"{matching[0]}/target/classes/"),
            "E_MANIFEST",
            f"{label} entry {index} is outside the frozen tested trees",
        )
        require(
            previous_path is None or previous_path.encode("utf-8") < path.encode("utf-8"),
            "E_MANIFEST",
            f"{label} entries are not strictly sorted and unique",
        )
        require(
            type(item["sha256"]) is str and HEX64.fullmatch(item["sha256"]) is not None,
            "E_MANIFEST",
            f"{label} entry {index} SHA differs",
        )
        require(
            type(item["size"]) is int and item["size"] >= 0,
            "E_MANIFEST",
            f"{label} entry {index} size differs",
        )
        observed_counts[matching[0]] += 1
        previous_path = path
        canonical_entries.append(
            {"path": path, "sha256": item["sha256"], "size": item["size"]}
        )
    require(
        all(
            type(row["module_counts"][module]) is int
            and row["module_counts"][module] > 0
            for module in modules
        )
        and row["module_counts"] == observed_counts,
        "E_MANIFEST",
        f"{label} module counts differ",
    )
    require(
        type(row["sha256"]) is str
        and HEX64.fullmatch(row["sha256"]) is not None
        and row["sha256"] == tree_digest(canonical_entries),
        "E_MANIFEST",
        f"{label} digest differs",
    )
    return row


def validate_source_seal_shape(value: Any, label: str) -> dict[str, Any]:
    row = exact_keys(value, ("command", "file_count", "git_head", "sha256", "status"), "E_MANIFEST", label)
    require(
        row["command"] == "source-hash"
        and row["status"] == "passed"
        and type(row["file_count"]) is int
        and row["file_count"] > 0
        and type(row["git_head"]) is str
        and HEX40.fullmatch(row["git_head"])
        and type(row["sha256"]) is str
        and HEX64.fullmatch(row["sha256"]),
        "E_MANIFEST",
        f"{label} identity differs",
    )
    return row


def validate_package_run_id(
    value: Any,
    expected_run_id: str | None = None,
) -> str:
    require(type(value) is str, "E_MANIFEST", "package run id is not a string")
    observed = safe_run_id(value)
    if expected_run_id is not None:
        require(
            observed == safe_run_id(expected_run_id),
            "E_RUN_ID",
            "package run id differs from the expected gate run",
        )
    return observed


def verify_package(
    root: Path,
    manifest_path: Path,
    jar_path: Path,
    *,
    expected_run_id: str | None = None,
) -> dict[str, Any]:
    manifest_file = Path(os.path.abspath(manifest_path))
    jar_file = Path(os.path.abspath(jar_path))
    require(manifest_file.name == PACKAGE_MANIFEST_NAME and jar_file.name == APP_JAR_NAME, "E_VERIFY_PATH", "durable package file names differ")
    require(manifest_file.parent == jar_file.parent, "E_VERIFY_PATH", "manifest and JAR must share one directory")
    package_directory = real_directory(manifest_file.parent, "durable package directory")
    require(
        package_directory == manifest_file.parent,
        "E_VERIFY_PATH",
        "durable package directory is not canonical",
    )
    validate_package_output(package_directory)
    raw = secure_bytes(manifest_file, "package manifest", 32 * 1024 * 1024)
    manifest = parse_json(raw, "package manifest")
    exact_keys(
        manifest,
        ("schema_version", "kind", "status", "run_id", "git_head", "source", "step4", "reactor", "seals", "maven", "jar", "validation_log", "image", "cleanup"),
        "E_MANIFEST",
        "package manifest",
    )
    require(manifest["schema_version"] == 4 and type(manifest["schema_version"]) is int, "E_MANIFEST", "package schema differs")
    require(manifest["kind"] == "v934-tested-output-tree-package" and manifest["status"] == "passed", "E_MANIFEST", "package identity/status differs")
    validate_package_run_id(manifest["run_id"], expected_run_id)
    require(type(manifest["git_head"]) is str and HEX40.fullmatch(manifest["git_head"]), "E_MANIFEST", "package HEAD differs")
    modules, freeze = frozen_modules(root)
    reactor = exact_keys(manifest["reactor"], ("module_count", "modules", "contract_freeze"), "E_MANIFEST", "reactor")
    require(reactor["module_count"] == 24 and type(reactor["module_count"]) is int and reactor["modules"] == modules, "E_MANIFEST", "frozen reactor differs")
    require(reactor["contract_freeze"] == freeze, "E_MANIFEST", "contract-freeze binding differs")
    source = exact_keys(manifest["source"], ("file_count", "sha256", "semantics"), "E_MANIFEST", "source")
    require(
        type(source["file_count"]) is int
        and source["file_count"] > 0
        and type(source["sha256"]) is str
        and HEX64.fullmatch(source["sha256"])
        and source["semantics"] == "canonical tracked worktree bytes equal Step4 before/after seal",
        "E_MANIFEST",
        "source identity differs",
    )
    step4 = exact_keys(
        manifest["step4"],
        ("run_root", "not_before_ns", "run_context", "run_status", "summary", "final_manifest", "toolchain_receipt"),
        "E_MANIFEST",
        "Step 4 receipt",
    )
    require(
        step4["run_root"] == f"target/v934-step4-coverage/runs/{manifest['run_id']}"
        and type(step4["not_before_ns"]) is int
        and step4["not_before_ns"] > 0,
        "E_MANIFEST",
        "Step 4 run binding differs",
    )
    for name in ("run_context", "run_status", "summary", "final_manifest", "toolchain_receipt"):
        validate_identity_shape(step4[name], f"Step 4 {name}", positive_size=True)
    seals = exact_keys(manifest["seals"], ("tested_tree_before", "tested_tree_after", "reports_before", "reports_after", "source_before", "source_after"), "E_MANIFEST", "seals")
    validate_tested_tree_snapshot(seals["tested_tree_before"], modules, "tested-tree-before seal")
    validate_tested_tree_snapshot(seals["tested_tree_after"], modules, "tested-tree-after seal")
    validate_tree_snapshot(seals["reports_before"], modules, "reports-before seal")
    validate_tree_snapshot(seals["reports_after"], modules, "reports-after seal")
    validate_source_seal_shape(seals["source_before"], "source-before seal")
    validate_source_seal_shape(seals["source_after"], "source-after seal")
    require(seals["tested_tree_before"] == seals["tested_tree_after"], "E_TESTED_TREE_DRIFT", "manifest records tested output tree drift")
    require(seals["reports_before"] == seals["reports_after"], "E_REPORT_DRIFT", "manifest records report drift")
    require(seals["source_before"] == seals["source_after"], "E_SOURCE_DRIFT", "manifest records source drift")
    require(
        seals["source_before"]["git_head"] == manifest["git_head"]
        and seals["source_before"]["sha256"] == source["sha256"]
        and seals["source_before"]["file_count"] == source["file_count"],
        "E_MANIFEST",
        "source seal binding differs",
    )
    maven = exact_keys(manifest["maven"], ("policy", "authority", "invocation_count", "invocations", "log"), "E_MANIFEST", "Maven receipt")
    maven_authority = exact_keys(
        maven["authority"],
        ("configuration_sha256", "launcher", "receipt", "status", "version"),
        "E_MANIFEST",
        "Maven Step 4 authority",
    )
    require(
        maven_authority["status"] == "exact-step4-replay"
        and maven_authority["version"] == "Apache Maven 3.8.7"
        and type(maven_authority["configuration_sha256"]) is str
        and HEX64.fullmatch(maven_authority["configuration_sha256"]) is not None
        and maven_authority["receipt"] == step4["toolchain_receipt"],
        "E_MAVEN_TOOLCHAIN",
        "Maven Step 4 authority receipt differs",
    )
    launcher_authority = exact_keys(
        maven_authority["launcher"],
        ("path", "sha256", "size"),
        "E_MANIFEST",
        "Maven launcher authority",
    )
    require(
        launcher_authority["path"] == "$MAVEN_HOME/bin/mvn",
        "E_MAVEN_TOOLCHAIN",
        "Maven launcher authority path differs",
    )
    validate_identity_shape(
        {"sha256": launcher_authority["sha256"], "size": launcher_authority["size"]},
        "Maven launcher authority",
        positive_size=True,
    )
    require(maven["invocation_count"] == 2 and type(maven["invocation_count"]) is int, "E_MANIFEST", "Maven invocation count differs")
    require(
        maven["invocations"] == [
            {"ordinal": 1, "projects": modules, "goals": ["jar:jar", "install:install"]},
            {"ordinal": 2, "projects": [LAUNCHER], "goals": ["jar:jar", "spring-boot:repackage"]},
        ],
        "E_MAVEN_GOAL",
        "Maven invocation contract differs",
    )
    verify_bound_sibling(
        package_directory,
        maven["log"],
        MAVEN_LOG_NAME,
        "Maven invocation log",
    )
    verify_bound_sibling(
        package_directory,
        manifest["validation_log"],
        VALIDATOR_LOG_NAME,
        "tested-tree validation log",
    )
    coordinates = {module: pom_coordinates(root, module) for module in modules}
    reactor_names = {f"{artifact}-{version}.jar" for artifact, version in coordinates.values()}
    runtime_descriptors = runtime_reactor_coordinates(coordinates)
    recorded_jar = manifest["jar"]
    require(type(recorded_jar) is dict, "E_MANIFEST", "tested JAR receipt is not an object")
    actual_jar = bind_reactor_source_artifacts(
        audit_jar(
            jar_file,
            reactor_names,
            runtime_descriptors,
            seals["tested_tree_before"],
        ),
        recorded_jar.get("reactor_source_artifacts"),
    )
    require(manifest["jar"] == actual_jar, "E_JAR_DRIFT", "tested JAR differs from package manifest")
    image = exact_keys(
        manifest["image"],
        ("manifest", "log", "context", "base_image", "image_id", "embedded_jar"),
        "E_MANIFEST",
        "image binding",
    )
    image_manifest_path, image_manifest_binding = verify_bound_sibling(
        package_directory,
        image["manifest"],
        IMAGE_MANIFEST_NAME,
        "runtime image manifest",
    )
    _, docker_log_binding = verify_bound_sibling(
        package_directory,
        image["log"],
        DOCKER_LOG_NAME,
        "Docker build log",
    )
    image_receipt = validate_image_receipt(
        root,
        image_manifest_path,
        image_manifest_binding,
        docker_log_binding,
        actual_jar,
        manifest["run_id"],
    )
    require(
        image["context"] == image_receipt["context"],
        "E_CONTEXT_POLICY",
        "package/image Docker context receipts differ",
    )
    require(
        validate_runtime_base_identity(
            image["base_image"], "package runtime base identity"
        )
        == image_receipt["base_image"],
        "E_BASE_IMAGE",
        "package/image runtime base identity differs",
    )
    embedded = exact_keys(image["embedded_jar"], ("path", "sha256", "size"), "E_MANIFEST", "embedded JAR")
    require(embedded == {"path": "/app/app.jar", "sha256": actual_jar["sha256"], "size": actual_jar["size"]}, "E_IMAGE_DRIFT", "image/JAR binding differs")
    require(
        image["image_id"] == image_receipt["image_id"],
        "E_MANIFEST",
        "package/image receipt ID differs",
    )
    cleanup = manifest["cleanup"]
    cleanup = exact_keys(cleanup, ("launcher", "reactor", "docker", "status"), "E_MANIFEST", "cleanup")
    require(cleanup["status"] == "exact", "E_MANIFEST", "cleanup status differs")
    launcher_cleanup = exact_keys(
        cleanup["launcher"],
        ("preexisting_artifacts", "restored", "quarantine_removed"),
        "E_MANIFEST",
        "Launcher cleanup",
    )
    require(launcher_cleanup["restored"] is True and launcher_cleanup["quarantine_removed"] is True, "E_MANIFEST", "Launcher cleanup is incomplete")
    require(type(launcher_cleanup["preexisting_artifacts"]) is list, "E_MANIFEST", "Launcher cleanup artifacts differ")
    old_names: set[str] = set()
    for index, artifact in enumerate(launcher_cleanup["preexisting_artifacts"]):
        row = exact_keys(artifact, ("name", "sha256", "size"), "E_MANIFEST", f"preexisting Launcher artifact {index}")
        require(
            type(row["name"]) is str
            and Path(row["name"]).name == row["name"]
            and row["name"] not in old_names,
            "E_MANIFEST",
            "preexisting Launcher artifact name differs",
        )
        old_names.add(row["name"])
        validate_identity_shape({"sha256": row["sha256"], "size": row["size"]}, f"preexisting Launcher artifact {index}")
    reactor_cleanup = exact_keys(
        cleanup["reactor"],
        ("preexisting_artifacts", "restored", "quarantine_removed"),
        "E_MANIFEST",
        "reactor cleanup",
    )
    require(
        reactor_cleanup["restored"] is True
        and reactor_cleanup["quarantine_removed"] is True
        and type(reactor_cleanup["preexisting_artifacts"]) is list,
        "E_MANIFEST",
        "reactor cleanup is incomplete",
    )
    seen_reactor_cleanup: set[str] = set()
    for index, artifact in enumerate(reactor_cleanup["preexisting_artifacts"]):
        row = exact_keys(
            artifact,
            ("module", "path", "sha256", "size"),
            "E_MANIFEST",
            f"preexisting reactor artifact {index}",
        )
        require(
            type(row["module"]) is str
            and row["module"] in RUNTIME_REACTOR_MODULES
            and row["module"] not in seen_reactor_cleanup
            and row["path"] == runtime_descriptors[
                f"{coordinates[row['module']][0]}-{coordinates[row['module']][1]}.jar"
            ]["source_path"],
            "E_MANIFEST",
            "preexisting reactor cleanup artifact differs",
        )
        seen_reactor_cleanup.add(row["module"])
        validate_identity_shape(
            {"sha256": row["sha256"], "size": row["size"]},
            f"preexisting reactor artifact {index}",
        )
    docker_cleanup = exact_keys(
        cleanup["docker"],
        (
            "container_removed",
            "image_tag_removed",
            "readback_removed",
            "context_removed",
        ),
        "E_MANIFEST",
        "Docker cleanup",
    )
    require(all(docker_cleanup[name] is True for name in docker_cleanup), "E_MANIFEST", "Docker cleanup is incomplete")
    require(
        docker_cleanup == image_receipt["cleanup"],
        "E_IMAGE_MANIFEST",
        "package/image cleanup receipts differ",
    )
    return {
        "command": "verify",
        "status": "passed",
        "run_id": manifest["run_id"],
        "git_head": manifest["git_head"],
        "jar_sha256": actual_jar["sha256"],
        "jar_size": actual_jar["size"],
        "nested_libraries": actual_jar["nested_library_count"],
        "manifest_sha256": sha256_bytes(raw),
        "base_image": runtime_base_identity(),
    }


def expect_failure(name: str, code: str, action: Callable[[], None]) -> dict[str, str]:
    try:
        action()
    except PackageError as exc:
        require(exc.code == code, "E_NEGATIVE", f"negative {name} expected {code}, got {exc.code}")
        return {"case": name, "expected": code, "actual": exc.code, "status": "passed"}
    reject("E_NEGATIVE", f"negative {name} unexpectedly passed")


def expect_signal(name: str, action: Callable[[], None]) -> dict[str, str]:
    try:
        action()
    except KeyboardInterrupt:
        return {
            "case": name,
            "expected": "E_SIGNAL",
            "actual": "E_SIGNAL",
            "status": "passed",
        }
    reject("E_NEGATIVE", f"negative {name} unexpectedly passed")


def negative_command(args: argparse.Namespace) -> dict[str, Any]:
    root = repo_root(args.repo_root)
    frozen_modules(root)
    output = create_output(args.output_dir)
    cases: list[dict[str, str]] = []
    base = {"sha256": "a" * 64, "files": 1}
    cases.append(expect_failure("source-drift", "E_SOURCE_DRIFT", lambda: require_unchanged("source", base, {**base, "sha256": "b" * 64}, "E_SOURCE_DRIFT")))
    cases.append(expect_failure("report-drift", "E_REPORT_DRIFT", lambda: require_unchanged("reports", base, {**base, "sha256": "c" * 64}, "E_REPORT_DRIFT")))
    cases.append(expect_failure("jar-drift", "E_JAR_DRIFT", lambda: require({"sha256": "a" * 64} == {"sha256": "b" * 64}, "E_JAR_DRIFT", "JAR drift")))
    cases.append(expect_failure("image-drift", "E_IMAGE_DRIFT", lambda: require({"sha256": "a" * 64} == {"sha256": "b" * 64}, "E_IMAGE_DRIFT", "image drift")))
    cases.append(expect_failure("illegal-lifecycle-goal", "E_MAVEN_GOAL", lambda: validate_maven_tokens(["mvn", "package"])))
    cases.append(expect_failure("skip-tests-flag", "E_MAVEN_SKIP", lambda: validate_maven_tokens(["mvn", "jar:jar", "-DskipTests=true"])))
    cases.append(expect_failure("skip-external-flag", "E_MAVEN_SKIP", lambda: validate_maven_tokens(["mvn", "jar:jar", "--skip-external-db"])))
    cases.append(expect_failure("test-selector", "E_MAVEN_SELECTOR", lambda: validate_maven_tokens(["mvn", "jar:jar", "-Dtest=ForgedTest"])))
    cases.append(expect_failure("maven-args-indirection", "E_MAVEN_ENV", lambda: validate_maven_environment({"MAVEN_ARGS": "-Dmaven.repo.local=/tmp/forged"})))
    cases.append(expect_failure("maven-basedir-indirection", "E_MAVEN_ENV", lambda: validate_maven_environment({"MAVEN_BASEDIR": "/tmp/forged-project"})))
    cases.append(expect_failure("maven-config-indirection", "E_MAVEN_ENV", lambda: validate_maven_environment({"MAVEN_CONFIG": "/tmp/forged-config"})))
    cases.append(expect_failure("mavenrc-skip-indirection", "E_MAVEN_ENV", lambda: validate_maven_environment({"MAVEN_SKIP_RC": "1"})))
    cases.append(
        expect_failure(
            "maven-launcher-path-drift",
            "E_MAVEN_TOOLCHAIN",
            lambda: require_maven_launcher_identity(
                {"sha256": "a" * 64, "size": 1},
                {"sha256": "b" * 64, "size": 1},
            ),
        )
    )
    cases.append(
        expect_failure(
            "maven-settings-drift",
            "E_MAVEN_CONFIG",
            lambda: require_maven_configuration_unchanged(
                [{"label": "user-settings", "present": False}],
                [
                    {
                        "label": "user-settings",
                        "present": True,
                        "sha256": "a" * 64,
                        "size": 1,
                    }
                ],
            ),
        )
    )
    cases.append(expect_failure("maven-extension-injection", "E_MAVEN_ENV", lambda: validate_maven_environment({"MAVEN_OPTS": "-Dmaven.ext.class.path=/tmp/evil.jar"})))
    cases.append(expect_failure("java-tool-options-injection", "E_MAVEN_ENV", lambda: validate_maven_environment({"JAVA_TOOL_OPTIONS": "-javaagent:/tmp/evil.jar"})))
    cases.append(expect_failure("jdk-java-options-injection", "E_MAVEN_ENV", lambda: validate_maven_environment({"JDK_JAVA_OPTIONS": "-javaagent:/tmp/evil.jar"})))
    cases.append(expect_failure("java-options-injection", "E_MAVEN_ENV", lambda: validate_maven_environment({"_JAVA_OPTIONS": "-Duser.home=/tmp/forged"})))
    cases.append(
        expect_failure(
            "docker-cleanup-failure",
            "E_IMAGE_CLEANUP",
            lambda: require_image_cleanup(["synthetic-resource-survived"]),
        )
    )
    release_status = {
        "mode": "release",
        "last_phase": "completed",
        "exit_code": "0",
        "status": "release-passed",
    }
    release_summary = {
        "mode": "release",
        "threshold_status": "confirmed",
        "release_successor": RELEASE_SUCCESSOR_MARKER,
        "status": "release-candidate-ready",
    }
    release_final = {
        "schema_version": 1,
        "kind": "v934-step4-coverage-acceptance-artifact",
        "stage": "final",
        "status": "release-final",
        "run_id": "synthetic-release",
        "git_head": "a" * 40,
        "threshold": {},
        "coverage_gate": {},
        "candidate_manifest": {},
        "evidence": {},
        "bindings": {},
        "release_successor": RELEASE_SUCCESSOR_MARKER,
    }
    cases.append(
        expect_failure(
            "formal-step4-masquerade",
            "E_STEP4_STATUS",
            lambda: validate_step4_release_contract(
                {**release_status, "mode": "formal", "status": "formal-passed"},
                release_summary,
                release_final,
            ),
        )
    )
    cases.append(
        expect_failure(
            "formal-summary-masquerade",
            "E_STEP4_SUMMARY",
            lambda: validate_step4_release_contract(
                release_status,
                {
                    **release_summary,
                    "mode": "formal",
                    "status": "formal-candidate-ready",
                    "formalization_delta_sha256": "a" * 64,
                },
                release_final,
            ),
        )
    )
    formal_final = dict(release_final)
    formal_final.pop("release_successor")
    formal_final["status"] = "formal-final"
    cases.append(
        expect_failure(
            "formal-final-masquerade",
            "E_STEP4_FINAL",
            lambda: validate_step4_release_contract(
                release_status, release_summary, formal_final
            ),
        )
    )
    with tempfile.TemporaryDirectory(prefix="v934-package-negative-") as temporary_name:
        temporary = Path(temporary_name)
        receipt_root = temporary / "failure-receipts"
        receipt_root.mkdir()
        receipt_path = receipt_root / FAILURE_RECEIPT_NAME
        receipt_context = FailureReceiptContext(
            path=receipt_path,
            run_id="synthetic-receipt",
            operation="package",
            subphase="package-preflight",
        )
        cases.append(
            expect_failure(
                "missing-failure-receipt",
                "E_FAILURE_RECEIPT",
                lambda: read_failure_receipt(
                    receipt_path,
                    run_id="synthetic-receipt",
                    operation="package",
                ),
            )
        )
        for operation, subphases in (
            ("package", PACKAGE_FAILURE_SUBPHASES),
            ("verify", VERIFY_FAILURE_SUBPHASES),
        ):
            for subphase in subphases:
                error_code = (
                    "E_IMAGE"
                    if (
                        operation == "package"
                        and subphase in PACKAGE_IMAGE_EIMAGE_SUBPHASES
                    )
                    else "E_COMMAND"
                )
                payload = failure_receipt_bytes(
                    run_id="synthetic-receipt",
                    operation=operation,
                    subphase=subphase,
                    error_code=error_code,
                    tool_exit_code=1,
                )
                require(
                    b"fixture-only-raw-message-sentinel" not in payload,
                    "E_NEGATIVE",
                    "failure receipt payload exposed synthetic raw detail",
                )
        cases.append(
            {
                "case": "failure-receipt-subphase-enums",
                "expected": "all-allowed",
                "actual": "all-allowed",
                "status": "passed",
            }
        )
        publish_failure_receipt(
            receipt_context,
            error_code="E_COMMAND",
            tool_exit_code=1,
        )
        valid_failure_receipt = read_failure_receipt(
            receipt_path,
            run_id="synthetic-receipt",
            operation="package",
            tool_exit_code=1,
        )
        require(
            valid_failure_receipt["subphase"] == "package-preflight",
            "E_NEGATIVE",
            "synthetic failure receipt subphase differs",
        )
        cases.append(
            expect_failure(
                "failure-receipt-process-exit-mismatch",
                "E_FAILURE_RECEIPT",
                lambda: read_failure_receipt(
                    receipt_path,
                    run_id="synthetic-receipt",
                    operation="package",
                    tool_exit_code=2,
                ),
            )
        )
        require(
            validate_package_run_id("synthetic-receipt", "synthetic-receipt")
            == "synthetic-receipt",
            "E_NEGATIVE",
            "synthetic verify run-id binding did not accept an exact match",
        )
        cases.append(
            expect_failure(
                "verify-manifest-run-id-mismatch",
                "E_RUN_ID",
                lambda: validate_package_run_id(
                    "synthetic-receipt",
                    "other-synthetic-receipt",
                ),
            )
        )
        safe_failure_result = verify_failure_receipt_command(
            argparse.Namespace(
                failure_receipt=receipt_path,
                run_id="synthetic-receipt",
                operation="package",
                tool_exit_code=1,
                package_root=receipt_root / "package",
            )
        )
        require(
            set(safe_failure_result)
            == {
                "command",
                "error_code",
                "gate_phase",
                "operation",
                "receipt",
                "run_id",
                "status",
                "subphase",
                "tool_exit_code",
            },
            "E_NEGATIVE",
            "safe failure receipt result fields differ",
        )
        malformed_receipt = receipt_root / "malformed" / FAILURE_RECEIPT_NAME
        malformed_receipt.parent.mkdir()
        malformed_receipt.write_bytes(
            failure_receipt_bytes(
                run_id="synthetic-receipt",
                operation="package",
                subphase="package-preflight",
                error_code="E_COMMAND",
                tool_exit_code=1,
            ).replace(b"kind=" + FAILURE_RECEIPT_KIND.encode("ascii"), b"gate_phase=forged", 1)
        )
        cases.append(
            expect_failure(
                "malformed-failure-receipt-order",
                "E_FAILURE_RECEIPT",
                lambda: read_failure_receipt(
                    malformed_receipt,
                    run_id="synthetic-receipt",
                    operation="package",
                ),
            )
        )
        duplicate_receipt = receipt_root / "duplicate" / FAILURE_RECEIPT_NAME
        duplicate_receipt.parent.mkdir()
        duplicate_receipt.write_bytes(
            failure_receipt_bytes(
                run_id="synthetic-receipt",
                operation="package",
                subphase="package-preflight",
                error_code="E_COMMAND",
                tool_exit_code=1,
            ).replace(b"tool_exit_code=1", b"error_code=E_COMMAND", 1)
        )
        cases.append(
            expect_failure(
                "duplicate-failure-receipt-key",
                "E_FAILURE_RECEIPT",
                lambda: read_failure_receipt(
                    duplicate_receipt,
                    run_id="synthetic-receipt",
                    operation="package",
                ),
            )
        )
        for case_name, original, expected_code in (
            ("failure-receipt-run-id-mismatch", b"run_id=synthetic-receipt", b"run_id=other-run"),
            ("failure-receipt-gate-phase-mismatch", b"gate_phase=package-tested-tree", b"gate_phase=other-phase"),
            ("failure-receipt-subphase-mismatch", b"subphase=package-preflight", b"subphase=forged"),
            ("failure-receipt-exit-mismatch", b"tool_exit_code=1", b"tool_exit_code=0"),
        ):
            mutated_receipt = receipt_root / case_name / FAILURE_RECEIPT_NAME
            mutated_receipt.parent.mkdir()
            mutated_receipt.write_bytes(receipt_path.read_bytes().replace(original, expected_code, 1))
            cases.append(
                expect_failure(
                    case_name,
                    "E_FAILURE_RECEIPT",
                    lambda mutated_receipt=mutated_receipt: read_failure_receipt(
                        mutated_receipt,
                        run_id="synthetic-receipt",
                        operation="package",
                    ),
                )
            )
        symlink_receipt = receipt_root / "symlink" / FAILURE_RECEIPT_NAME
        symlink_receipt.parent.mkdir()
        symlink_receipt.symlink_to(receipt_path)
        cases.append(
            expect_failure(
                "symlink-failure-receipt",
                "E_FAILURE_RECEIPT",
                lambda: read_failure_receipt(
                    symlink_receipt,
                    run_id="synthetic-receipt",
                    operation="package",
                ),
            )
        )
        preexisting_receipt = receipt_root / "preexisting" / FAILURE_RECEIPT_NAME
        preexisting_receipt.parent.mkdir()
        preexisting_receipt.write_bytes(b"preserved\n")
        cases.append(
            expect_failure(
                "preexisting-failure-receipt-preserved",
                "E_OUTPUT_EXISTS",
                lambda: publish_failure_receipt(
                    FailureReceiptContext(
                        path=preexisting_receipt,
                        run_id="synthetic-receipt",
                        operation="package",
                        subphase="package-preflight",
                    ),
                    error_code="E_COMMAND",
                    tool_exit_code=1,
                ),
            )
        )
        require(
            preexisting_receipt.read_bytes() == b"preserved\n",
            "E_NEGATIVE",
            "failure receipt writer changed a preexisting target",
        )
        symlink_writer_receipt = receipt_root / "symlink-writer" / FAILURE_RECEIPT_NAME
        symlink_writer_receipt.parent.mkdir()
        symlink_writer_receipt.symlink_to(receipt_path)
        cases.append(
            expect_failure(
                "symlink-failure-receipt-writer",
                "E_OUTPUT_EXISTS",
                lambda: publish_failure_receipt(
                    FailureReceiptContext(
                        path=symlink_writer_receipt,
                        run_id="synthetic-receipt",
                        operation="package",
                        subphase="package-preflight",
                    ),
                    error_code="E_COMMAND",
                    tool_exit_code=1,
                ),
            )
        )
        package_output_root = temporary / "receipt-package-output"
        cases.append(
            expect_failure(
                "failure-receipt-inside-package-output",
                "E_FAILURE_RECEIPT",
                lambda: failure_receipt_target(
                    package_output_root / FAILURE_RECEIPT_NAME,
                    package_root=package_output_root,
                ),
            )
        )
        cases.append(
            expect_failure(
                "failure-receipt-wrong-run-root",
                "E_FAILURE_RECEIPT",
                lambda: read_failure_receipt(
                    receipt_path,
                    run_id="synthetic-receipt",
                    operation="package",
                    package_root=temporary / "other-run" / "package",
                ),
            )
        )
        cases.append(
            expect_failure(
                "failure-receipt-unknown-error-code",
                "E_FAILURE_RECEIPT",
                lambda: failure_receipt_bytes(
                    run_id="synthetic-receipt",
                    operation="package",
                    subphase="package-preflight",
                    error_code="E_FORGED",
                    tool_exit_code=1,
                ),
            )
        )
        raw_marker = "fixture-only-raw-message-sentinel"
        cases.append(
            expect_failure(
                "failure-receipt-refined-subphase-raw-suffix",
                "E_FAILURE_RECEIPT",
                lambda: failure_receipt_bytes(
                    run_id="synthetic-receipt",
                    operation="package",
                    subphase="package-image-runtime-inspect-raw-suffix",
                    error_code="E_IMAGE",
                    tool_exit_code=1,
                ),
            )
        )
        extra_field_receipt = receipt_root / "extra-field" / FAILURE_RECEIPT_NAME
        extra_field_receipt.parent.mkdir()
        extra_field_receipt.write_bytes(
            failure_receipt_bytes(
                run_id="synthetic-receipt",
                operation="package",
                subphase="package-image-runtime-inspect",
                error_code="E_IMAGE",
                tool_exit_code=1,
            )
            + f"detail={raw_marker}\n".encode("ascii")
        )
        cases.append(
            expect_failure(
                "failure-receipt-extra-field",
                "E_FAILURE_RECEIPT",
                lambda: read_failure_receipt(
                    extra_field_receipt,
                    run_id="synthetic-receipt",
                    operation="package",
                ),
            )
        )
        for subphase in PACKAGE_IMAGE_EIMAGE_SUBPHASES:
            cases.append(
                expect_failure(
                    f"failure-receipt-{subphase}-requires-eimage",
                    "E_FAILURE_RECEIPT",
                    lambda subphase=subphase: failure_receipt_bytes(
                        run_id="synthetic-receipt",
                        operation="package",
                        subphase=subphase,
                        error_code="E_COMMAND",
                        tool_exit_code=1,
                    ),
                )
            )
            mismatched_refined_receipt = (
                receipt_root / f"{subphase}-wrong-code" / FAILURE_RECEIPT_NAME
            )
            mismatched_refined_receipt.parent.mkdir()
            mismatched_refined_receipt.write_bytes(
                failure_receipt_bytes(
                    run_id="synthetic-receipt",
                    operation="package",
                    subphase="package-image",
                    error_code="E_COMMAND",
                    tool_exit_code=1,
                ).replace(
                    b"subphase=package-image\n",
                    f"subphase={subphase}\n".encode("ascii"),
                    1,
                )
            )
            cases.append(
                expect_failure(
                    f"failure-receipt-{subphase}-reader-requires-eimage",
                    "E_FAILURE_RECEIPT",
                    lambda mismatched_refined_receipt=mismatched_refined_receipt: read_failure_receipt(
                        mismatched_refined_receipt,
                        run_id="synthetic-receipt",
                        operation="package",
                    ),
                )
            )
        contained_receipt = receipt_root / "contained" / FAILURE_RECEIPT_NAME
        contained_receipt.parent.mkdir()
        contained_context = FailureReceiptContext(
            path=contained_receipt,
            run_id="synthetic-receipt",
            operation="package",
            subphase="package-maven-reactor",
        )
        contained_result, contained_exit = execute_with_failure_receipt(
            contained_context,
            lambda: (_ for _ in ()).throw(PackageError("E_COMMAND", raw_marker)),
        )
        require(
            contained_result is None and contained_exit == 1,
            "E_NEGATIVE",
            "synthetic receipted failure did not return a bounded failure",
        )
        require(
            raw_marker.encode("ascii") not in contained_receipt.read_bytes(),
            "E_NEGATIVE",
            "failure receipt exposed a raw exception message",
        )
        read_failure_receipt(
            contained_receipt,
            run_id="synthetic-receipt",
            operation="package",
        )

        def assert_receipted_image_subphase(
            case_name: str,
            action: Callable[[FailureReceiptContext], Any],
            expected_error: str,
            expected_subphase: str,
        ) -> None:
            image_receipt = receipt_root / case_name / FAILURE_RECEIPT_NAME
            image_receipt.parent.mkdir()
            image_context = FailureReceiptContext(
                path=image_receipt,
                run_id="synthetic-receipt",
                operation="package",
                subphase="package-image",
            )
            image_result, image_exit = execute_with_failure_receipt(
                image_context,
                lambda: action(image_context),
            )
            require(
                image_result is None and image_exit == 1,
                "E_NEGATIVE",
                f"synthetic {case_name} did not fail closed",
            )
            image_values = read_failure_receipt(
                image_receipt,
                run_id="synthetic-receipt",
                operation="package",
                tool_exit_code=1,
            )
            require(
                image_values["error_code"] == expected_error
                and image_values["subphase"] == expected_subphase,
                "E_NEGATIVE",
                f"synthetic {case_name} receipt classification differs",
            )
            require(
                image_context.pending_eimage_subphase is None,
                "E_NEGATIVE",
                f"synthetic {case_name} retained a pending image subphase",
            )
            require(
                raw_marker.encode("ascii") not in image_receipt.read_bytes(),
                "E_NEGATIVE",
                f"synthetic {case_name} receipt exposed raw detail",
            )
            image_cli = subprocess.run(
                [
                    sys.executable,
                    str(Path(__file__).resolve()),
                    "verify-failure-receipt",
                    "--failure-receipt",
                    str(image_receipt),
                    "--run-id",
                    "synthetic-receipt",
                    "--operation",
                    "package",
                    "--tool-exit-code",
                    "1",
                    "--package-root",
                    str(image_receipt.parent / "package"),
                ],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            image_cli_value = parse_json(
                image_cli.stdout,
                f"synthetic {case_name} receipt CLI result",
            )
            require(
                image_cli.returncode == 0
                and image_cli.stderr == b""
                and exact_keys(
                    image_cli_value,
                    (
                        "command",
                        "error_code",
                        "gate_phase",
                        "operation",
                        "receipt",
                        "run_id",
                        "status",
                        "subphase",
                        "tool_exit_code",
                    ),
                    "E_NEGATIVE",
                    f"synthetic {case_name} receipt CLI fields differ",
                )
                == image_cli_value
                and image_cli_value["command"] == "verify-failure-receipt"
                and image_cli_value["error_code"] == expected_error
                and image_cli_value["gate_phase"] == FAILURE_RECEIPT_GATE_PHASE
                and image_cli_value["operation"] == "package"
                and image_cli_value["receipt"] == "valid"
                and image_cli_value["run_id"] == "synthetic-receipt"
                and image_cli_value["status"] == "failed"
                and image_cli_value["subphase"] == expected_subphase
                and image_cli_value["tool_exit_code"] == 1
                and raw_marker.encode("ascii") not in image_cli.stdout
                and raw_marker.encode("ascii") not in image_cli.stderr,
                "E_NEGATIVE",
                f"synthetic {case_name} receipt CLI result differs",
            )
            cases.append(
                {
                    "case": case_name,
                    "expected": f"{expected_error}/{expected_subphase}",
                    "actual": f"{image_values['error_code']}/{image_values['subphase']}",
                    "status": "passed",
                }
            )

        legacy_image_receipt = receipt_root / "legacy-package-image" / FAILURE_RECEIPT_NAME
        legacy_image_receipt.parent.mkdir()
        write_new(
            legacy_image_receipt,
            b"schema_version=1\n"
            b"kind=v934-package-subphase-failure\n"
            b"run_id=synthetic-receipt\n"
            b"gate_phase=package-tested-tree\n"
            b"operation=package\n"
            b"subphase=package-image\n"
            b"error_code=E_IMAGE\n"
            b"tool_exit_code=1\n"
            b"status=failed\n",
        )
        legacy_image_values = read_failure_receipt(
            legacy_image_receipt,
            run_id="synthetic-receipt",
            operation="package",
            tool_exit_code=1,
        )
        require(
            legacy_image_values["error_code"] == "E_IMAGE"
            and legacy_image_values["subphase"] == "package-image",
            "E_NEGATIVE",
            "legacy package-image receipt is no longer readable",
        )
        cases.append(
            {
                "case": "legacy-package-image-reader",
                "expected": "E_IMAGE/package-image",
                "actual": "E_IMAGE/package-image",
                "status": "passed",
            }
        )
        for subphase in PACKAGE_IMAGE_EIMAGE_SUBPHASES:
            assert_receipted_image_subphase(
                f"controlled-{subphase}",
                lambda image_context, subphase=subphase: guarded_eimage_boundary(
                    image_context,
                    subphase,
                    lambda: reject("E_IMAGE", raw_marker),
                ),
                "E_IMAGE",
                subphase,
            )
        assert_receipted_image_subphase(
            "unknown-eimage-remains-legacy",
            lambda _image_context: reject("E_IMAGE", raw_marker),
            "E_IMAGE",
            "package-image",
        )
        for error_code in ("E_COMMAND", "E_BASE_IMAGE"):
            assert_receipted_image_subphase(
                f"{error_code.lower()}-remains-legacy",
                lambda image_context, error_code=error_code: guarded_eimage_boundary(
                    image_context,
                    "package-image-runtime-inspect",
                    lambda: reject(error_code, raw_marker),
                ),
                error_code,
                "package-image",
            )

        def pending_then_terminal_error(
            image_context: FailureReceiptContext,
            terminal_error: str,
        ) -> None:
            try:
                guarded_eimage_boundary(
                    image_context,
                    "package-image-runtime-inspect",
                    lambda: reject("E_IMAGE", raw_marker),
                )
            except PackageError:
                reject(terminal_error, raw_marker)

        for error_code in ("E_IMAGE_CLEANUP", "E_OUTPUT"):
            assert_receipted_image_subphase(
                f"pending-{error_code.lower()}-remains-legacy",
                lambda image_context, error_code=error_code: pending_then_terminal_error(
                    image_context,
                    error_code,
                ),
                error_code,
                "package-image",
            )
        wrong_phase_context = FailureReceiptContext(
            path=receipt_root / "wrong-phase" / FAILURE_RECEIPT_NAME,
            run_id="synthetic-receipt",
            operation="package",
            subphase="package-postconditions",
        )
        cases.append(
            expect_failure(
                "pending-eimage-requires-package-image-phase",
                "E_FAILURE_RECEIPT",
                lambda: wrong_phase_context.defer_eimage_subphase(
                    "package-image-runtime-inspect"
                ),
            )
        )
        phase_switch_context = FailureReceiptContext(
            path=receipt_root / "phase-switch" / FAILURE_RECEIPT_NAME,
            run_id="synthetic-receipt",
            operation="package",
            subphase="package-image",
        )
        phase_switch_context.defer_eimage_subphase("package-image-runtime-inspect")
        phase_switch_context.set_subphase("package-postconditions")
        require(
            phase_switch_context.pending_eimage_subphase is None,
            "E_NEGATIVE",
            "package phase transition retained pending image state",
        )
        cases.append(
            {
                "case": "pending-eimage-phase-transition-clear",
                "expected": "cleared",
                "actual": "cleared",
                "status": "passed",
            }
        )
        successful_pending_receipt = receipt_root / "successful-pending" / FAILURE_RECEIPT_NAME
        successful_pending_receipt.parent.mkdir()
        successful_pending_context = FailureReceiptContext(
            path=successful_pending_receipt,
            run_id="synthetic-receipt",
            operation="package",
            subphase="package-image",
        )
        successful_pending_context.defer_eimage_subphase(
            "package-image-runtime-inspect"
        )
        successful_pending_result, successful_pending_exit = execute_with_failure_receipt(
            successful_pending_context,
            lambda: {"command": "synthetic", "status": "passed"},
        )
        require(
            successful_pending_result == {"command": "synthetic", "status": "passed"}
            and successful_pending_exit == 0
            and successful_pending_context.pending_eimage_subphase is None
            and not successful_pending_receipt.exists(),
            "E_NEGATIVE",
            "successful action retained pending image state or published a receipt",
        )
        cases.append(
            {
                "case": "pending-eimage-success-clear",
                "expected": "cleared",
                "actual": "cleared",
                "status": "passed",
            }
        )
        signal_receipt = receipt_root / "signal" / FAILURE_RECEIPT_NAME
        signal_receipt.parent.mkdir()
        signal_result, signal_exit = execute_with_failure_receipt(
            FailureReceiptContext(
                path=signal_receipt,
                run_id="synthetic-receipt",
                operation="verify",
                subphase="verify-package",
            ),
            lambda: (_ for _ in ()).throw(KeyboardInterrupt()),
        )
        require(
            signal_result is None and signal_exit == 130,
            "E_NEGATIVE",
            "synthetic interrupted command did not return a bounded signal failure",
        )
        signal_values = read_failure_receipt(
            signal_receipt,
            run_id="synthetic-receipt",
            operation="verify",
            tool_exit_code=130,
        )
        require(
            signal_values["error_code"] == "E_SIGNAL",
            "E_NEGATIVE",
            "synthetic interrupted command did not publish E_SIGNAL",
        )
        internal_receipt = receipt_root / "internal" / FAILURE_RECEIPT_NAME
        internal_receipt.parent.mkdir()
        internal_result, internal_exit = execute_with_failure_receipt(
            FailureReceiptContext(
                path=internal_receipt,
                run_id="synthetic-receipt",
                operation="verify",
                subphase="verify-package",
            ),
            lambda: (_ for _ in ()).throw(SystemExit(7)),
        )
        require(
            internal_result is None and internal_exit == 1,
            "E_NEGATIVE",
            "synthetic BaseException did not return a bounded internal failure",
        )
        internal_values = read_failure_receipt(
            internal_receipt,
            run_id="synthetic-receipt",
            operation="verify",
            tool_exit_code=1,
        )
        require(
            internal_values["error_code"] == "E_INTERNAL",
            "E_NEGATIVE",
            "synthetic BaseException did not publish E_INTERNAL",
        )
        successful_receipt = receipt_root / "success" / FAILURE_RECEIPT_NAME
        successful_receipt.parent.mkdir()
        successful_result, successful_exit = execute_with_failure_receipt(
            FailureReceiptContext(
                path=successful_receipt,
                run_id="synthetic-receipt",
                operation="verify",
                subphase="verify-package",
            ),
            lambda: {"command": "synthetic", "status": "passed"},
        )
        require(
            successful_result == {"command": "synthetic", "status": "passed"}
            and successful_exit == 0
            and not successful_receipt.exists(),
            "E_NEGATIVE",
            "successful receipted command published a failure receipt",
        )
        cli_root = temporary / "receipt-cli"
        cli_root.mkdir()
        cli_run_root = cli_root / "package-run"
        cli_run_root.mkdir()
        cli_package_root = cli_run_root / "package"
        cli_receipt = cli_run_root / FAILURE_RECEIPT_NAME
        cli_command = [
            sys.executable,
            str(Path(__file__).resolve()),
            "package",
            "--repo-root",
            str(cli_root / "not-a-repository"),
            "--run-id",
            "synthetic-cli-package",
            "--step4-run-root",
            str(cli_root / "step4"),
            "--output-dir",
            str(cli_package_root),
            "--failure-receipt",
            str(cli_receipt),
        ]
        cli_package = subprocess.run(
            cli_command,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        require(
            cli_package.returncode == 1
            and cli_package.stdout == b""
            and cli_package.stderr == b""
            and not cli_package_root.exists()
            and not cli_package_root.is_symlink(),
            "E_NEGATIVE",
            "receipt package CLI exposed output or left a partial package directory",
        )
        cli_package_values = read_failure_receipt(
            cli_receipt,
            run_id="synthetic-cli-package",
            operation="package",
            tool_exit_code=1,
            package_root=cli_package_root,
        )
        require(
            cli_package_values["subphase"] == "package-preflight",
            "E_NEGATIVE",
            "receipt package CLI subphase differs",
        )
        cli_verify_receipt = subprocess.run(
            [
                sys.executable,
                str(Path(__file__).resolve()),
                "verify-failure-receipt",
                "--failure-receipt",
                str(cli_receipt),
                "--run-id",
                "synthetic-cli-package",
                "--operation",
                "package",
                "--tool-exit-code",
                "1",
                "--package-root",
                str(cli_package_root),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        cli_verify_value = parse_json(
            cli_verify_receipt.stdout,
            "synthetic receipt CLI verification result",
        )
        require(
            cli_verify_receipt.returncode == 0
            and cli_verify_receipt.stderr == b""
            and exact_keys(
                cli_verify_value,
                (
                    "command",
                    "error_code",
                    "gate_phase",
                    "operation",
                    "receipt",
                    "run_id",
                    "status",
                    "subphase",
                    "tool_exit_code",
                ),
                "E_NEGATIVE",
                "synthetic receipt CLI verification result",
            )
            == cli_verify_value,
            "E_NEGATIVE",
            "receipt CLI verification result differs",
        )
        cli_wrong_root = subprocess.run(
            [
                sys.executable,
                str(Path(__file__).resolve()),
                "verify-failure-receipt",
                "--failure-receipt",
                str(cli_receipt),
                "--run-id",
                "synthetic-cli-package",
                "--operation",
                "package",
                "--tool-exit-code",
                "1",
                "--package-root",
                str(cli_root / "other-run" / "package"),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        require(
            cli_wrong_root.returncode == 1
            and cli_wrong_root.stdout == b""
            and cli_wrong_root.stderr == b"",
            "E_NEGATIVE",
            "receipt CLI accepted a non-sibling package root",
        )
        cli_verify_run_root = cli_root / "verify-run"
        cli_verify_run_root.mkdir()
        cli_verify_package_root = cli_verify_run_root / "package"
        cli_verify_package_root.mkdir()
        cli_verify_sidecar = cli_verify_run_root / FAILURE_RECEIPT_NAME
        cli_verify = subprocess.run(
            [
                sys.executable,
                str(Path(__file__).resolve()),
                "verify",
                "--repo-root",
                str(cli_root / "not-a-repository"),
                "--manifest",
                str(cli_verify_package_root / PACKAGE_MANIFEST_NAME),
                "--jar",
                str(cli_verify_package_root / APP_JAR_NAME),
                "--run-id",
                "synthetic-cli-verify",
                "--failure-receipt",
                str(cli_verify_sidecar),
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        require(
            cli_verify.returncode == 1
            and cli_verify.stdout == b""
            and cli_verify.stderr == b"",
            "E_NEGATIVE",
            "receipt verify CLI exposed output",
        )
        cli_verify_values = read_failure_receipt(
            cli_verify_sidecar,
            run_id="synthetic-cli-verify",
            operation="verify",
            tool_exit_code=1,
            package_root=cli_verify_package_root,
        )
        require(
            cli_verify_values["subphase"] == "verify-package",
            "E_NEGATIVE",
            "receipt verify CLI subphase differs",
        )
        cases.append(
            {
                "case": "receipt-cli-suppression-and-sibling-binding",
                "expected": "bounded",
                "actual": "bounded",
                "status": "passed",
            }
        )
        staging_runs = temporary / "receipt-package-runs"
        staging_runs.mkdir()
        staging_run_root = staging_runs / "success-run"
        staging_run_root.mkdir()
        final_package = staging_run_root / "package"
        staging_package = create_receipt_package_staging(final_package)
        for name in PACKAGE_OUTPUT_NAMES:
            (staging_package / name).write_bytes(
                f"synthetic staged package:{name}\n".encode("ascii")
            )
        published_package = publish_staged_package(staging_package, final_package)
        require(
            published_package == final_package
            and not staging_package.exists()
            and validate_package_output(final_package)["file_count"] == 6,
            "E_NEGATIVE",
            "synthetic receipt package staging publication differs",
        )
        cleanup_flat_directory(final_package, "synthetic published receipt package")
        failed_run_root = staging_runs / "failed-run"
        failed_run_root.mkdir()
        failed_final = failed_run_root / "package"
        failed_staging = create_receipt_package_staging(failed_final)
        raw_staging_marker = b"fixture-only-staging-raw-marker\n"
        for name in PACKAGE_OUTPUT_NAMES:
            (failed_staging / name).write_bytes(
                raw_staging_marker if name == VALIDATOR_LOG_NAME else b"synthetic-safe\n"
            )
        original_link = os.link

        def failed_staged_link(
            source: Any, destination: Any, *link_args: Any, **link_kwargs: Any
        ) -> None:
            if Path(destination).name == PACKAGE_MANIFEST_NAME:
                raise OSError("synthetic staged publication failure")
            original_link(source, destination, *link_args, **link_kwargs)

        def failed_staged_publication_probe() -> None:
            from unittest import mock

            with mock.patch.object(os, "link", side_effect=failed_staged_link):
                publish_staged_package(failed_staging, failed_final)

        cases.append(
            expect_failure(
                "receipt-package-staged-publication-cleanup",
                "E_OUTPUT",
                failed_staged_publication_probe,
            )
        )
        require(
            not failed_final.exists() and not failed_final.is_symlink(),
            "E_NEGATIVE",
            "failed staged publication left a durable package directory",
        )
        cleanup_flat_directory(failed_staging, "synthetic failed receipt package staging")
        signal_run_root = staging_runs / "signal-run"
        signal_run_root.mkdir()
        signal_final = signal_run_root / "package"
        signal_staging = create_receipt_package_staging(signal_final)
        for name in PACKAGE_OUTPUT_NAMES:
            (signal_staging / name).write_bytes(b"synthetic-safe\n")

        def interrupted_staged_link(
            _source: Any, _destination: Any, *link_args: Any, **link_kwargs: Any
        ) -> None:
            raise KeyboardInterrupt()

        original_cleanup_flat_directory = cleanup_flat_directory

        def failed_partial_cleanup(path: Path, label: str) -> None:
            if label == "partial durable package directory":
                raise PackageError("E_OUTPUT", "synthetic cleanup failure")
            original_cleanup_flat_directory(path, label)

        def interrupted_staged_publication_probe() -> None:
            from unittest import mock

            with mock.patch.object(os, "link", side_effect=interrupted_staged_link):
                with mock.patch.object(
                    sys.modules[__name__],
                    "cleanup_flat_directory",
                    side_effect=failed_partial_cleanup,
                ):
                    publish_staged_package(signal_staging, signal_final)

        cases.append(
            expect_signal(
                "receipt-package-signal-preserves-signal-receipt",
                interrupted_staged_publication_probe,
            )
        )
        cleanup_flat_directory(signal_staging, "synthetic signal receipt package staging")
        cleanup_flat_directory(signal_final, "synthetic signal partial durable package")
        cleanup_probe_run_root = staging_runs / "cleanup-probe-run"
        cleanup_probe_run_root.mkdir()
        cleanup_probe_final = cleanup_probe_run_root / "package"
        cleanup_probe_staging = create_receipt_package_staging(cleanup_probe_final)
        (cleanup_probe_staging / "unexpected-directory").mkdir()
        cases.append(
            expect_failure(
                "receipt-package-staging-cleanup-directory",
                "E_OUTPUT",
                lambda: cleanup_flat_directory(
                    cleanup_probe_staging,
                    "synthetic malformed receipt package staging",
                ),
            )
        )
        (cleanup_probe_staging / "unexpected-directory").rmdir()
        cleanup_flat_directory(
            cleanup_probe_staging,
            "synthetic repaired receipt package staging",
        )
        concurrent_target = temporary / "concurrent-publication.txt"
        original_link = os.link

        def concurrent_link(source: Any, destination: Any, *link_args: Any, **link_kwargs: Any) -> None:
            Path(destination).write_bytes(b"concurrent-writer\n")
            original_link(source, destination, *link_args, **link_kwargs)

        def concurrent_publication_probe() -> None:
            from unittest import mock

            try:
                with mock.patch.object(os, "link", side_effect=concurrent_link):
                    write_new(concurrent_target, b"release-writer\n")
            except PackageError:
                require(
                    concurrent_target.read_bytes() == b"concurrent-writer\n",
                    "E_OUTPUT_RACE",
                    "failed publisher removed or changed the concurrent destination",
                )
                raise

        cases.append(
            expect_failure(
                "concurrent-publication-preserved",
                "E_OUTPUT_EXISTS",
                concurrent_publication_probe,
            )
        )
        source_docker_root = temporary / "source-docker-root"
        source_dockerfile = source_docker_root / DOCKERFILE
        source_dockerfile.parent.mkdir(parents=True)
        source_dockerfile.write_text(
            "FROM maven:3-eclipse-temurin-17\n"
            "WORKDIR /src\n"
            "COPY . /src\n"
            "RUN mvn -DskipTests package\n"
            "ENTRYPOINT [\"java\",\"-jar\",\"/src/app.jar\"]\n",
            encoding="utf-8",
        )
        cases.append(
            expect_failure(
                "source-building-dockerfile",
                "E_DOCKERFILE",
                lambda: validate_dockerfile(source_docker_root),
            )
        )

        pinned_dockerfile_raw = (root / DOCKERFILE).read_bytes()

        def mutated_dockerfile_root(name: str, payload: bytes) -> Path:
            case_root = temporary / name
            case_path = case_root / DOCKERFILE
            case_path.parent.mkdir(parents=True)
            case_path.write_bytes(payload)
            return case_root

        mutable_base_root = mutated_dockerfile_root(
            "mutable-base-root",
            pinned_dockerfile_raw.replace(
                RUNTIME_BASE_FROM.encode("ascii"),
                f"FROM --platform=linux/amd64 {RUNTIME_BASE_TAG_REFERENCE}".encode(
                    "ascii"
                ),
                1,
            ),
        )
        cases.append(
            expect_failure(
                "mutable-base-reference",
                "E_BASE_IMAGE",
                lambda: validate_dockerfile(mutable_base_root),
            )
        )
        wrong_manifest_root = mutated_dockerfile_root(
            "wrong-base-manifest-root",
            pinned_dockerfile_raw.replace(
                RUNTIME_BASE_MANIFEST_DIGEST.encode("ascii"),
                ("sha256:" + "0" * 64).encode("ascii"),
                1,
            ),
        )
        cases.append(
            expect_failure(
                "wrong-base-manifest",
                "E_BASE_IMAGE",
                lambda: validate_dockerfile(wrong_manifest_root),
            )
        )
        wrong_platform_root = mutated_dockerfile_root(
            "wrong-base-platform-root",
            pinned_dockerfile_raw.replace(b"--platform=linux/amd64", b"--platform=linux/arm64", 1),
        )
        cases.append(
            expect_failure(
                "wrong-base-platform",
                "E_BASE_IMAGE",
                lambda: validate_dockerfile(wrong_platform_root),
            )
        )
        cases.append(
            expect_failure(
                "missing-base-identity",
                "E_BASE_IMAGE",
                lambda: validate_runtime_base_identity({}, "missing synthetic base"),
            )
        )
        wrong_config_identity = runtime_base_identity()
        wrong_config_identity["config_digest"] = "sha256:" + "0" * 64
        cases.append(
            expect_failure(
                "wrong-base-config",
                "E_BASE_IMAGE",
                lambda: validate_runtime_base_identity(
                    wrong_config_identity, "wrong synthetic base config"
                ),
            )
        )
        spliced_identity = runtime_base_identity()
        spliced_identity["index_digest"] = "sha256:" + "1" * 64
        spliced_identity["config_digest"] = "sha256:" + "2" * 64
        cases.append(
            expect_failure(
                "spliced-base-index-manifest-config",
                "E_BASE_IMAGE",
                lambda: validate_runtime_base_identity(
                    spliced_identity, "spliced synthetic base identity"
                ),
            )
        )

        synthetic_entries: list[dict[str, Any]] = []
        synthetic_descriptors: dict[str, dict[str, str]] = {}
        for index, module in enumerate(RUNTIME_REACTOR_MODULES):
            artifact = f"fixture-reactor-{index:02d}"
            file_name = f"{artifact}-1.jar"
            relative = f"example/{artifact}.class"
            payload = f"tested:{module}\n".encode("utf-8")
            synthetic_entries.append(
                {
                    "path": f"{module}/target/classes/{relative}",
                    "sha256": sha256_bytes(payload),
                    "size": len(payload),
                }
            )
            synthetic_descriptors[file_name] = {
                "artifact_id": artifact,
                "file_name": file_name,
                "module": module,
                "nested_path": f"BOOT-INF/lib/{file_name}",
                "source_path": f"{module}/target/{file_name}",
            }
        launcher_payload = b"tested-launcher\n"
        synthetic_entries.append(
            {
                "path": f"{LAUNCHER}/target/classes/App.class",
                "sha256": sha256_bytes(launcher_payload),
                "size": len(launcher_payload),
            }
        )
        synthetic_tested_tree = {"entries": synthetic_entries}

        def zip_bytes(files: Sequence[tuple[str, bytes]]) -> bytes:
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, "w") as bundle:
                for name, payload in files:
                    info = zipfile.ZipInfo(name)
                    info.create_system = 3
                    info.external_attr = (stat.S_IFREG | 0o644) << 16
                    info.compress_type = zipfile.ZIP_DEFLATED
                    bundle.writestr(info, payload)
            return buffer.getvalue()

        synthetic_modules: dict[str, bytes] = {}
        for file_name, descriptor in synthetic_descriptors.items():
            module_tree = module_output_tree(
                synthetic_tested_tree, descriptor["module"]
            )
            payload_entry = module_tree["entries"][0]
            source_payload = f"tested:{descriptor['module']}\n".encode("utf-8")
            require(
                sha256_bytes(source_payload) == payload_entry["sha256"],
                "E_NEGATIVE",
                "synthetic reactor payload fixture differs",
            )
            synthetic_modules[file_name] = zip_bytes(
                [
                    (payload_entry["path"], source_payload),
                    ("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n\n"),
                    (
                        f"META-INF/maven/{MAVEN_METADATA_GROUP}/{descriptor['artifact_id']}/pom.properties",
                        b"version=1\n",
                    ),
                    (
                        f"META-INF/maven/{MAVEN_METADATA_GROUP}/{descriptor['artifact_id']}/pom.xml",
                        b"<project/>\n",
                    ),
                ]
            )

        manifest_bytes = (
            b"Manifest-Version: 1.0\n"
            b"Main-Class: org.springframework.boot.loader.launch.JarLauncher\n"
            b"Start-Class: com.foggyframework.mcp.launcher.McpLauncherApplication\n"
            b"Spring-Boot-Version: 3.5.0\n\n"
        )

        def write_synthetic_fat(
            path: Path,
            *,
            include_launcher: bool = True,
            extra_reactor: bool = False,
            empty_module: str | None = None,
        ) -> None:
            files: list[tuple[str, bytes]] = [("META-INF/MANIFEST.MF", manifest_bytes)]
            if include_launcher:
                files.append(("BOOT-INF/classes/App.class", launcher_payload))
            for file_name, payload in synthetic_modules.items():
                if empty_module == file_name:
                    descriptor = synthetic_descriptors[file_name]
                    payload = zip_bytes(
                        [
                            ("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n\n"),
                            (
                                f"META-INF/maven/{MAVEN_METADATA_GROUP}/{descriptor['artifact_id']}/pom.properties",
                                b"version=1\n",
                            ),
                            (
                                f"META-INF/maven/{MAVEN_METADATA_GROUP}/{descriptor['artifact_id']}/pom.xml",
                                b"<project/>\n",
                            ),
                        ]
                    )
                files.append((f"BOOT-INF/lib/{file_name}", payload))
            if extra_reactor:
                files.append(("BOOT-INF/lib/unexpected-reactor-1.jar", next(iter(synthetic_modules.values()))))
            path.write_bytes(zip_bytes(files))

        synthetic_reactor_names = set(synthetic_descriptors) | {"unexpected-reactor-1.jar"}
        valid_fat = temporary / "valid-same-tested-tree.jar"
        write_synthetic_fat(valid_fat)
        valid_audit = audit_jar(
            valid_fat,
            synthetic_reactor_names,
            synthetic_descriptors,
            synthetic_tested_tree,
        )
        synthetic_sources = sorted(
            (
                {
                    **row["reactor_binding"],
                    "sha256": row["sha256"],
                    "size": row["size"],
                }
                for row in valid_audit["nested_libraries"]
                if "reactor_binding" in row
            ),
            key=lambda row: row["module"].encode("utf-8"),
        )
        bind_reactor_source_artifacts(valid_audit, synthetic_sources)

        missing_classes_fat = temporary / "missing-launcher-classes.jar"
        write_synthetic_fat(missing_classes_fat, include_launcher=False)
        cases.append(
            expect_failure(
                "fat-jar-missing-tested-classes",
                "E_JAR_CLASS_TREE",
                lambda: audit_jar(
                    missing_classes_fat,
                    synthetic_reactor_names,
                    synthetic_descriptors,
                    synthetic_tested_tree,
                ),
            )
        )
        empty_reactor_fat = temporary / "empty-reactor-payload.jar"
        write_synthetic_fat(
            empty_reactor_fat, empty_module=next(iter(synthetic_descriptors))
        )
        cases.append(
            expect_failure(
                "fat-jar-empty-reactor-payload",
                "E_JAR_CLASS_TREE",
                lambda: audit_jar(
                    empty_reactor_fat,
                    synthetic_reactor_names,
                    synthetic_descriptors,
                    synthetic_tested_tree,
                ),
            )
        )
        extra_reactor_fat = temporary / "extra-reactor.jar"
        write_synthetic_fat(extra_reactor_fat, extra_reactor=True)
        cases.append(
            expect_failure(
                "fat-jar-extra-reactor-library",
                "E_JAR_LIBRARY",
                lambda: audit_jar(
                    extra_reactor_fat,
                    synthetic_reactor_names,
                    synthetic_descriptors,
                    synthetic_tested_tree,
                ),
            )
        )
        drifted_sources = [dict(row) for row in synthetic_sources]
        drifted_sources[0]["sha256"] = "f" * 64
        cases.append(
            expect_failure(
                "fat-jar-reactor-source-hash-drift",
                "E_JAR_LIBRARY",
                lambda: bind_reactor_source_artifacts(valid_audit, drifted_sources),
            )
        )
        target = temporary / "target"
        target.mkdir()
        real = target / f"{LAUNCHER}-1.jar"
        real.write_bytes(b"jar")
        link = target / f"{LAUNCHER}-2.jar"
        link.symlink_to(real.name)
        cases.append(expect_failure("symlink-jar", "E_SYMLINK", lambda: launcher_candidates(target)))
        link.unlink()
        second = target / f"{LAUNCHER}-2.jar"
        second.write_bytes(b"jar2")
        cases.append(expect_failure("multiple-launcher-jars", "E_JAR_CARDINALITY", lambda: require(len(launcher_candidates(target)) == 1, "E_JAR_CARDINALITY", "multiple JARs")))
        tested_root = temporary / "tested-output"
        tested_root.mkdir()
        (tested_root / "A.class").write_bytes(b"class")
        resource = tested_root / "application.yml"
        resource.write_bytes(b"value: before\n")
        tested_before = tested_tree_snapshot_from_roots(
            [("synthetic-module", tested_root)]
        )
        resource.write_bytes(b"value: after\n")
        tested_after = tested_tree_snapshot_from_roots(
            [("synthetic-module", tested_root)]
        )
        cases.append(
            expect_failure(
                "resource-drift",
                "E_TESTED_TREE_DRIFT",
                lambda: require_unchanged(
                    "synthetic tested output tree",
                    tested_before,
                    tested_after,
                    "E_TESTED_TREE_DRIFT",
                ),
            )
        )
        resource_link = tested_root / "linked-resource.properties"
        resource_link.symlink_to(resource.name)
        cases.append(
            expect_failure(
                "symlink-resource",
                "E_SYMLINK",
                lambda: tested_tree_snapshot_from_roots(
                    [("synthetic-module", tested_root)]
                ),
            )
        )
        receipt_root = temporary / "receipts"
        receipt_root.mkdir()
        missing_identity = {"sha256": "a" * 64, "size": 1}
        cases.append(
            expect_failure(
                "missing-image-manifest-receipt",
                "E_FILE_MISSING",
                lambda: verify_bound_sibling(
                    receipt_root,
                    {"path": IMAGE_MANIFEST_NAME, **missing_identity},
                    IMAGE_MANIFEST_NAME,
                    "synthetic image manifest",
                ),
            )
        )
        image_receipt_path = receipt_root / IMAGE_MANIFEST_NAME
        image_receipt_path.write_bytes(b"image-one\n")
        image_receipt_binding = binding(
            image_receipt_path,
            relative_to=receipt_root,
            label="synthetic image manifest",
        )
        image_receipt_path.write_bytes(b"image-two\n")
        cases.append(
            expect_failure(
                "tampered-image-manifest-receipt",
                "E_RECEIPT_DRIFT",
                lambda: verify_bound_sibling(
                    receipt_root,
                    image_receipt_binding,
                    IMAGE_MANIFEST_NAME,
                    "synthetic image manifest",
                ),
            )
        )
        for receipt_name, case_stem in (
            (MAVEN_LOG_NAME, "maven-log"),
            (DOCKER_LOG_NAME, "docker-log"),
            (VALIDATOR_LOG_NAME, "tested-tree-validation-log"),
        ):
            receipt_path = receipt_root / receipt_name
            cases.append(
                expect_failure(
                    f"missing-{case_stem}-receipt",
                    "E_FILE_MISSING",
                    lambda receipt_name=receipt_name, case_stem=case_stem: verify_bound_sibling(
                        receipt_root,
                        {"path": receipt_name, **missing_identity},
                        receipt_name,
                        f"synthetic {case_stem}",
                    ),
                )
            )
            receipt_path.write_bytes(b"log-before\n")
            receipt_binding = binding(
                receipt_path,
                relative_to=receipt_root,
                label=f"synthetic {case_stem}",
            )
            receipt_path.write_bytes(b"log-after!\n")
            cases.append(
                expect_failure(
                    f"tampered-{case_stem}-receipt",
                    "E_RECEIPT_DRIFT",
                    lambda receipt_name=receipt_name, case_stem=case_stem, receipt_binding=receipt_binding: verify_bound_sibling(
                        receipt_root,
                        receipt_binding,
                        receipt_name,
                        f"synthetic {case_stem}",
                    ),
                )
            )
        context_root = temporary / "docker-context"
        context_root.mkdir()
        context_dockerfile_path = context_root / "Dockerfile"
        context_jar_path = context_root / APP_JAR_NAME
        context_dockerfile_path.write_bytes(b"FROM scratch\n")
        context_jar_path.write_bytes(b"tested-jar\n")
        synthetic_dockerfile = {
            "path": "synthetic/Dockerfile",
            **file_identity(context_dockerfile_path, "synthetic Dockerfile"),
        }
        synthetic_jar = {
            "path": APP_JAR_NAME,
            **file_identity(context_jar_path, "synthetic tested JAR"),
        }
        context_receipt = validate_docker_context(
            context_root, synthetic_jar, synthetic_dockerfile
        )
        validate_docker_context_receipt(
            context_receipt,
            synthetic_jar,
            synthetic_dockerfile,
            "synthetic context receipt",
        )
        (context_root / DOCKER_LOG_NAME).write_bytes(b"must-not-enter-context\n")
        cases.append(
            expect_failure(
                "docker-log-in-build-context",
                "E_CONTEXT_POLICY",
                lambda: validate_docker_context(
                    context_root, synthetic_jar, synthetic_dockerfile
                ),
            )
        )
        exact_output_root = temporary / "exact-package-output"
        exact_output_root.mkdir()
        for output_name in PACKAGE_OUTPUT_NAMES:
            (exact_output_root / output_name).write_bytes(
                f"synthetic:{output_name}\n".encode("utf-8")
            )
        exact_output_receipt = validate_package_output(exact_output_root)
        require(
            exact_output_receipt["file_count"] == 6,
            "E_NEGATIVE",
            "synthetic exact package output did not contain six files",
        )
        (exact_output_root / "unexpected.txt").write_bytes(b"unexpected\n")
        cases.append(
            expect_failure(
                "extra-package-output",
                "E_OUTPUT_CONTRACT",
                lambda: validate_package_output(exact_output_root),
            )
        )
    result = {
        "schema_version": 1,
        "kind": "v934-release-package-negative-result",
        "status": "passed",
        "case_count": len(cases),
        "cases": cases,
    }
    path = output / "negative-result.json"
    write_new(path, canonical_json(result))
    return {
        "command": "negative",
        "status": "passed",
        "cases": len(cases),
        "result": str(path),
        "result_sha256": file_identity(path, "negative result")["sha256"],
    }


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="command", required=True)
    package = commands.add_parser("package", help="package the exact tested output tree and audit the runtime image")
    package.add_argument("--repo-root", type=Path, required=True)
    package.add_argument("--run-id", required=True)
    package.add_argument("--step4-run-root", type=Path, required=True)
    package.add_argument("--output-dir", type=Path, required=True)
    package.add_argument("--failure-receipt", type=Path)
    verify = commands.add_parser("verify", help="durably verify package-manifest.json and app.jar")
    verify.add_argument("--repo-root", type=Path, required=True)
    verify.add_argument("--manifest", type=Path, required=True)
    verify.add_argument("--jar", type=Path, required=True)
    verify.add_argument("--run-id")
    verify.add_argument("--failure-receipt", type=Path)
    verify_failure_receipt = commands.add_parser(
        "verify-failure-receipt",
        help="validate a bounded package failure receipt without exposing raw failure detail",
    )
    verify_failure_receipt.add_argument("--failure-receipt", type=Path, required=True)
    verify_failure_receipt.add_argument("--run-id", required=True)
    verify_failure_receipt.add_argument("--operation", choices=FAILURE_RECEIPT_OPERATIONS, required=True)
    verify_failure_receipt.add_argument("--tool-exit-code", type=int, required=True)
    verify_failure_receipt.add_argument("--package-root", type=Path, required=True)
    negative = commands.add_parser("negative", help="run synthetic fail-closed package mutations")
    negative.add_argument("--repo-root", type=Path, required=True)
    negative.add_argument("--output-dir", type=Path, required=True)
    return result


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    if args.command == "verify-failure-receipt":
        try:
            result = verify_failure_receipt_command(args)
        except BaseException:
            return 1
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
        return 0
    if getattr(args, "failure_receipt", None) is not None:
        try:
            context = failure_receipt_context(args)
        except BaseException:
            return 1
        assert context is not None
        if args.command == "package":
            result, exit_code = execute_with_failure_receipt(
                context,
                lambda: package_command(args, context),
            )
        else:
            result, exit_code = execute_with_failure_receipt(
                context,
                lambda: verify_package(
                    repo_root(args.repo_root),
                    args.manifest,
                    args.jar,
                    expected_run_id=args.run_id,
                ),
            )
        if result is None:
            return exit_code
        print(json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
        return 0
    try:
        if args.command == "package":
            result = package_command(args)
        elif args.command == "verify":
            root = repo_root(args.repo_root)
            result = verify_package(root, args.manifest, args.jar)
        else:
            result = negative_command(args)
    except PackageError as exc:
        print(
            json.dumps(
                {"command": getattr(args, "command", "unknown"), "status": "failed", "error": exc.code, "message": exc.message},
                ensure_ascii=False,
                separators=(",", ":"),
                sort_keys=True,
            ),
            file=sys.stderr,
        )
        return 1
    except KeyboardInterrupt:
        print(json.dumps({"command": getattr(args, "command", "unknown"), "status": "failed", "error": "E_SIGNAL", "message": "interrupted"}, separators=(",", ":"), sort_keys=True), file=sys.stderr)
        return 130
    print(json.dumps(result, ensure_ascii=False, separators=(",", ":"), sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
