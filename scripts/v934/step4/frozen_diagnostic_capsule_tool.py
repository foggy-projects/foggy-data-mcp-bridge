#!/usr/bin/env python3
"""Build and verify a Git-safe, portable Step 4 diagnostic capsule.

The capsule deliberately contains only a validated diagnostic attestation and
the JaCoCo XML needed for semantic recomputation.  It never copies a run tree,
raw execution files, logs, host metadata, or process/resource identities.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import tarfile
import tempfile
from typing import Any, Iterable
import xml.etree.ElementTree as ET
import zlib


PROFILE = "git-safe-sanitized-attested-v1"
RUN_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")
OCTAL_MODE = re.compile(r"0[0-7]{3}")
MAX_ARCHIVE_BYTES = 512 * 1024 * 1024
MAX_EXTRACTED_BYTES = 512 * 1024 * 1024
MAX_ATTESTATION_BYTES = 2 * 1024 * 1024
MAX_XML_BYTES = 256 * 1024 * 1024
JACOCO_DOCTYPE = b'<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">'
TAR_BLOCK_SIZE = tarfile.BLOCKSIZE
TAR_RECORD_SIZE = tarfile.RECORDSIZE
USTAR_MAGIC = b"ustar\x0000"

RETENTION = {
    "runtime_closure": "forbidden",
    "execution_bytes": "forbidden",
    "unstructured_output": "forbidden",
}
ENTRY_LAYOUT = (
    ("evidence", "directory", "0755"),
    ("evidence/diagnostic-attestation.json", "file", "0644"),
    ("evidence/jacoco.xml", "file", "0644"),
)
MANIFEST_KEYS = (
    "schema_version",
    "kind",
    "profile",
    "status",
    "run_id",
    "git_head",
    "source_sha256",
    "retention",
    "archive",
    "entry_count",
    "entries",
)
ATTESTATION_KEYS = (
    "schema_version",
    "kind",
    "profile",
    "status",
    "identity",
    "execution_attestation",
    "xml",
    "source_attestation",
    "semantic_observation",
)
IDENTITY_KEYS = (
    "run_id",
    "git_head",
    "source_sha256",
    "run_context_sha256",
    "run_status_sha256",
    "summary_sha256",
    "coverage_contract_sha256",
    "threshold_predecessor_sha256",
    "observation_sha256",
)
EXECUTION_ATTESTATION_KEYS = (
    "mode",
    "retention",
    "exec_count",
    "session_count",
    "byte_tree_sha256",
    "aggregate_exec_sha256",
    "merge_semantics",
    "status",
)
XML_KEYS = ("sha256", "size", "deterministic_report_replay_count")
SOURCE_ATTESTATION_KEYS = (
    "class_universe_sha256",
    "workspace_class_tree_sha256",
    "workspace_bytecode_class_count",
    "toolchain_receipt_sha256",
    "coverage_ledger_sha256",
)
FORBIDDEN_KEYS = {
    "container",
    "containers",
    "container_id",
    "container_name",
    "container_identity",
    "container_ids",
    "container_names",
    "pid",
    "ppid",
    "pgid",
    "sid",
    "process",
    "process_id",
    "process_identity",
    "processes",
    "process_tree",
    "process_list",
    "command",
    "command_line",
    "argv",
    "boot_id",
    "hostname",
    "host",
    "platform",
    "run_log",
    "raw_log",
    "raw_logs",
    "log",
    "log_file",
    "log_path",
    "logs",
    "exec",
    "raw_exec",
    "exec_path",
    "exec_file",
    "exec_files",
    "mtime_ns",
    "not_before_ns",
    "path",
    "paths",
}
FORBIDDEN_VALUE_TOKENS = (
    ".exec",
    ".log",
    "containers.tsv",
    "container_id",
    "container-id",
    "containerid",
    "container_name",
    "container-name",
    "containername",
    "container identity",
    "container_identity",
    "containeridentity",
    "process_id",
    "process-id",
    "processid",
    "process identity",
    "process_identity",
    "processidentity",
    "pid=",
    "pid:",
    "ppid=",
    "ppid:",
    "pgid=",
    "pgid:",
    "raw exec",
    "raw_exec",
    "execution_bytes",
    "/proc/",
    "\\proc\\",
    "child-ready",
    "child-lifecycle",
    "docker inspect",
    "docker ps",
)
SENSITIVE_PATTERNS = (
    re.compile(r"(?i)(?:password|secret|token|api[-_]?key)\s*(?:=|:)\s*[^\s<]{4,}"),
    re.compile(r"(?i)\bbearer\s+[a-z0-9._~+/-]{8,}"),
    re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
)


class CapsuleError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def reject(code: str, message: str) -> None:
    raise CapsuleError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def canonical_json(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")


def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            reject("E_JSON_DUPLICATE", "duplicate JSON key")
        result[key] = value
    return result


def parse_json_bytes(payload: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            payload.decode("utf-8"),
            object_pairs_hook=unique_object,
            parse_constant=lambda token: reject(
                "E_JSON", f"{label} contains non-finite JSON"
            ),
        )
    except CapsuleError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CapsuleError("E_JSON", f"cannot parse {label}") from error
    require(type(value) is dict, "E_JSON", f"{label} must be an object")
    return value


def exact_keys(value: Any, expected: Iterable[str], code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict, code, f"{label} must be an object")
    wanted = set(expected)
    require(set(value) == wanted, code, f"{label} keys differ")
    return value


def absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path)))


def real_directory(path: Path, label: str) -> Path:
    candidate = absolute(path)
    try:
        metadata = os.lstat(candidate)
    except OSError as error:
        raise CapsuleError("E_DIRECTORY", f"cannot inspect {label}") from error
    require(
        stat.S_ISDIR(metadata.st_mode) and not stat.S_ISLNK(metadata.st_mode),
        "E_DIRECTORY",
        f"{label} is not a real directory",
    )
    require(
        candidate.resolve(strict=True) == candidate,
        "E_SYMLINK",
        f"{label} has symlinked components",
    )
    return candidate


def regular_file(path: Path, label: str, maximum: int | None = None) -> Path:
    candidate = absolute(path)
    try:
        metadata = os.lstat(candidate)
    except OSError as error:
        raise CapsuleError("E_FILE", f"cannot inspect {label}") from error
    require(
        stat.S_ISREG(metadata.st_mode) and not stat.S_ISLNK(metadata.st_mode),
        "E_SPECIAL",
        f"{label} is not a regular file",
    )
    require(
        candidate.resolve(strict=True) == candidate,
        "E_SYMLINK",
        f"{label} has symlinked components",
    )
    if maximum is not None:
        require(metadata.st_size <= maximum, "E_SIZE", f"{label} exceeds size limit")
    return candidate


def sha256_file(path: Path, label: str, maximum: int | None = None) -> tuple[str, int]:
    candidate = regular_file(path, label, maximum)
    before = candidate.stat()
    digest = hashlib.sha256()
    size = 0
    try:
        with candidate.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
                size += len(chunk)
    except OSError as error:
        raise CapsuleError("E_FILE", f"cannot hash {label}") from error
    after = candidate.stat()
    require(
        (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns)
        == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns)
        and size == before.st_size,
        "E_FILE_RACE",
        f"{label} changed while hashing",
    )
    return digest.hexdigest(), size


def safe_relative(value: str, code: str = "E_PATH") -> PurePosixPath:
    require(
        type(value) is str and value and "\\" not in value and "\x00" not in value,
        code,
        "invalid capsule path",
    )
    pure = PurePosixPath(value)
    require(
        not pure.is_absolute()
        and pure.as_posix() == value
        and all(part not in ("", ".", "..") for part in pure.parts),
        code,
        "unsafe capsule path",
    )
    return pure


def normalized_key(value: str) -> str:
    split_camel = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", value)
    return re.sub(r"[^a-z0-9]+", "_", split_camel.casefold()).strip("_")


def scan_text(value: str, *, code: str, label: str) -> None:
    lowered = value.casefold()
    require(
        not any(token in lowered for token in FORBIDDEN_VALUE_TOKENS),
        code,
        f"{label} contains forbidden runtime content",
    )
    for pattern in SENSITIVE_PATTERNS:
        require(
            pattern.search(value) is None,
            "E_CAPSULE_SENSITIVE",
            f"{label} contains sensitive content",
        )


def validate_no_runtime_metadata(
    value: Any,
    *,
    code: str = "E_ATTESTATION_FORBIDDEN",
    label: str = "attestation",
    depth: int = 0,
) -> None:
    require(depth <= 64, "E_ATTESTATION", f"{label} nesting exceeds limit")
    if type(value) is dict:
        for key, child in value.items():
            require(type(key) is str, "E_ATTESTATION", f"{label} key is invalid")
            scan_text(key, code=code, label=label)
            require(
                normalized_key(key) not in FORBIDDEN_KEYS,
                code,
                f"{label} contains forbidden runtime metadata",
            )
            validate_no_runtime_metadata(child, code=code, label=label, depth=depth + 1)
        return
    if type(value) is list:
        for child in value:
            validate_no_runtime_metadata(child, code=code, label=label, depth=depth + 1)
        return
    if type(value) is str:
        scan_text(value, code=code, label=label)
        return
    require(
        value is None or type(value) in (bool, int, float),
        "E_ATTESTATION",
        f"{label} contains an unsupported value",
    )


def require_hex(value: Any, code: str, label: str, *, commit: bool = False) -> str:
    pattern = HEX40 if commit else HEX64
    require(type(value) is str and pattern.fullmatch(value) is not None, code, f"{label} differs")
    return value


def load_attestation(path: Path) -> tuple[dict[str, Any], bytes]:
    candidate = regular_file(path, "diagnostic attestation", MAX_ATTESTATION_BYTES)
    try:
        raw = candidate.read_bytes()
    except OSError as error:
        raise CapsuleError("E_FILE", "cannot read diagnostic attestation") from error
    value = parse_json_bytes(raw, "diagnostic attestation")
    require(raw == canonical_json(value), "E_ATTESTATION_CANONICAL", "attestation is not canonical JSON")
    exact_keys(value, ATTESTATION_KEYS, "E_ATTESTATION", "diagnostic attestation")
    require(
        value["schema_version"] == 1
        and type(value["schema_version"]) is int
        and value["kind"] == "v934-step4-git-safe-diagnostic-attestation"
        and value["profile"] == PROFILE
        and value["status"] == "verified",
        "E_ATTESTATION",
        "attestation identity/status differs",
    )
    identity = exact_keys(value["identity"], IDENTITY_KEYS, "E_ATTESTATION", "attestation identity")
    require(
        type(identity["run_id"]) is str and RUN_ID.fullmatch(identity["run_id"]) is not None,
        "E_ATTESTATION",
        "attestation run id differs",
    )
    require_hex(identity["git_head"], "E_ATTESTATION", "attestation Git head", commit=True)
    for key in IDENTITY_KEYS:
        if key not in ("run_id", "git_head"):
            require_hex(identity[key], "E_ATTESTATION", f"attestation {key}")
    execution = exact_keys(
        value["execution_attestation"],
        EXECUTION_ATTESTATION_KEYS,
        "E_ATTESTATION",
        "execution attestation",
    )
    require(
        execution["mode"] == "source-validated-hash-only"
        and execution["retention"] == "no-execution-bytes"
        and type(execution["exec_count"]) is int
        and execution["exec_count"] == 23
        and type(execution["session_count"]) is int
        and execution["session_count"] == 48
        and execution["merge_semantics"]
        == "exact-session-and-jacoco-class-id-probe-bitmap-union"
        and execution["status"] == "verified",
        "E_ATTESTATION",
        "execution attestation differs",
    )
    require_hex(execution["byte_tree_sha256"], "E_ATTESTATION", "execution tree SHA")
    require_hex(execution["aggregate_exec_sha256"], "E_ATTESTATION", "aggregate exec SHA")
    xml = exact_keys(value["xml"], XML_KEYS, "E_ATTESTATION", "XML attestation")
    require_hex(xml["sha256"], "E_ATTESTATION", "XML SHA")
    require(
        type(xml["size"]) is int
        and 0 < xml["size"] <= MAX_XML_BYTES
        and type(xml["deterministic_report_replay_count"]) is int
        and xml["deterministic_report_replay_count"] == 2,
        "E_ATTESTATION",
        "XML attestation differs",
    )
    source = exact_keys(
        value["source_attestation"],
        SOURCE_ATTESTATION_KEYS,
        "E_ATTESTATION",
        "source attestation",
    )
    for key in (
        "class_universe_sha256",
        "workspace_class_tree_sha256",
        "toolchain_receipt_sha256",
        "coverage_ledger_sha256",
    ):
        require_hex(source[key], "E_ATTESTATION", f"source attestation {key}")
    require(
        type(source["workspace_bytecode_class_count"]) is int
        and source["workspace_bytecode_class_count"] > 0
        and type(value["semantic_observation"]) is dict,
        "E_ATTESTATION",
        "attestation semantic observation differs",
    )
    validate_no_runtime_metadata(value)
    return value, raw


def validate_xml(path: Path) -> tuple[bytes, str, int]:
    candidate = regular_file(path, "JaCoCo XML", MAX_XML_BYTES)
    try:
        payload = candidate.read_bytes()
    except OSError as error:
        raise CapsuleError("E_FILE", "cannot read JaCoCo XML") from error
    prolog = payload
    if prolog.startswith(b"\xef\xbb\xbf"):
        prolog = prolog[3:]
    prolog = prolog.lstrip(b" \t\r\n")
    if prolog.startswith(b"<?xml"):
        require(
            len(prolog) > 5 and prolog[5:6] in (b" ", b"\t", b"\r", b"\n"),
            "E_XML",
            "JaCoCo XML declaration differs",
        )
        declaration_end = prolog.find(b"?>")
        require(declaration_end >= 0, "E_XML", "JaCoCo XML declaration is incomplete")
        prolog = prolog[declaration_end + 2 :].lstrip(b" \t\r\n")
    require(
        prolog.startswith(JACOCO_DOCTYPE)
        and payload.count(JACOCO_DOCTYPE) == 1,
        "E_XML",
        "JaCoCo XML doctype differs",
    )
    after_doctype = prolog[len(JACOCO_DOCTYPE) :].lstrip(b" \t\r\n")
    require(
        after_doctype.startswith(b"<report")
        and b"<!" not in after_doctype
        and b"<?" not in after_doctype
        and b"<!ENTITY" not in payload.upper(),
        "E_XML",
        "JaCoCo XML contains an untrusted declaration",
    )
    try:
        text = payload.decode("utf-8")
        root = ET.fromstring(payload)
    except (UnicodeDecodeError, ET.ParseError) as error:
        raise CapsuleError("E_XML", "JaCoCo XML is invalid") from error
    require(root.tag == "report", "E_XML", "JaCoCo XML root differs")
    scan_text(text, code="E_CAPSULE_FORBIDDEN", label="JaCoCo XML")
    for element in root.iter():
        require(type(element.tag) is str, "E_XML", "JaCoCo XML element is invalid")
        scan_text(element.tag, code="E_CAPSULE_FORBIDDEN", label="JaCoCo XML")
        for key, value in element.attrib.items():
            scan_text(key, code="E_CAPSULE_FORBIDDEN", label="JaCoCo XML")
            scan_text(value, code="E_CAPSULE_FORBIDDEN", label="JaCoCo XML")
        if element.text is not None:
            scan_text(element.text, code="E_CAPSULE_FORBIDDEN", label="JaCoCo XML")
        if element.tail is not None:
            scan_text(element.tail, code="E_CAPSULE_FORBIDDEN", label="JaCoCo XML")
    return payload, hashlib.sha256(payload).hexdigest(), len(payload)


def build_records(attestation_path: Path, jacoco_xml_path: Path) -> tuple[list[dict[str, Any]], dict[str, Path], dict[str, Any]]:
    attestation, _raw = load_attestation(attestation_path)
    _xml_payload, xml_sha, xml_size = validate_xml(jacoco_xml_path)
    require(
        attestation["xml"] == {
            "sha256": xml_sha,
            "size": xml_size,
            "deterministic_report_replay_count": 2,
        },
        "E_ATTESTATION_XML",
        "attestation/XML binding differs",
    )
    source_paths = {
        "evidence/diagnostic-attestation.json": regular_file(
            attestation_path, "diagnostic attestation", MAX_ATTESTATION_BYTES
        ),
        "evidence/jacoco.xml": regular_file(
            jacoco_xml_path, "JaCoCo XML", MAX_XML_BYTES
        ),
    }
    records: list[dict[str, Any]] = []
    for relative, kind, mode in ENTRY_LAYOUT:
        if kind == "directory":
            records.append(
                {
                    "path": relative,
                    "kind": kind,
                    "mode": mode,
                    "size": 0,
                    "sha256": None,
                }
            )
            continue
        digest, size = sha256_file(source_paths[relative], f"capsule {relative}")
        records.append(
            {
                "path": relative,
                "kind": kind,
                "mode": mode,
                "size": size,
                "sha256": digest,
            }
        )
    return records, source_paths, attestation


def write_deterministic_archive(
    records: list[dict[str, Any]],
    source_paths: dict[str, Path],
    output: Path,
) -> None:
    with output.open("xb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w", format=tarfile.USTAR_FORMAT) as archive:
                for record in records:
                    info = tarfile.TarInfo(record["path"])
                    info.uid = 0
                    info.gid = 0
                    info.uname = ""
                    info.gname = ""
                    info.mtime = 0
                    info.mode = int(record["mode"], 8)
                    if record["kind"] == "directory":
                        info.type = tarfile.DIRTYPE
                        info.size = 0
                        archive.addfile(info)
                    else:
                        info.type = tarfile.REGTYPE
                        info.size = record["size"]
                        with source_paths[record["path"]].open("rb") as stream:
                            archive.addfile(info, stream)
        raw.flush()
        os.fsync(raw.fileno())


def publish_pair(archive_temp: Path, archive: Path, manifest_temp: Path, manifest: Path) -> None:
    archive = absolute(archive)
    manifest = absolute(manifest)
    require(archive != manifest, "E_OUTPUT", "archive and manifest paths must differ")
    require(archive.parent == manifest.parent, "E_OUTPUT", "outputs must share a directory")
    parent = real_directory(archive.parent, "capsule output directory")
    require(not archive.exists() and not archive.is_symlink(), "E_OUTPUT_EXISTS", "archive output exists")
    require(not manifest.exists() and not manifest.is_symlink(), "E_OUTPUT_EXISTS", "manifest output exists")
    archive_published = False
    manifest_published = False
    try:
        os.link(archive_temp, archive, follow_symlinks=False)
        archive_published = True
        os.link(manifest_temp, manifest, follow_symlinks=False)
        manifest_published = True
        os.chmod(archive, 0o644, follow_symlinks=False)
        os.chmod(manifest, 0o644, follow_symlinks=False)
        descriptor = os.open(parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
    except OSError as error:
        if manifest_published:
            manifest.unlink(missing_ok=True)
        if archive_published:
            archive.unlink(missing_ok=True)
        raise CapsuleError("E_OUTPUT", "cannot publish capsule outputs") from error


def build_capsule(
    attestation_path: Path,
    jacoco_xml_path: Path,
    archive_path: Path,
    manifest_path: Path,
) -> dict[str, Any]:
    archive = absolute(archive_path)
    manifest = absolute(manifest_path)
    require(archive.parent == manifest.parent, "E_OUTPUT", "outputs must share a directory")
    output_parent = real_directory(archive.parent, "capsule output directory")
    require(not archive.exists() and not archive.is_symlink(), "E_OUTPUT_EXISTS", "archive output exists")
    require(not manifest.exists() and not manifest.is_symlink(), "E_OUTPUT_EXISTS", "manifest output exists")
    records, source_paths, attestation = build_records(attestation_path, jacoco_xml_path)
    identity = attestation["identity"]
    with tempfile.TemporaryDirectory(prefix=".v934-capsule-build-", dir=output_parent) as temporary_name:
        temporary = Path(temporary_name)
        archive_temp = temporary / "capsule.tar.gz"
        manifest_temp = temporary / "capsule.manifest.json"
        write_deterministic_archive(records, source_paths, archive_temp)
        archive_sha, archive_size = sha256_file(
            archive_temp, "staged capsule archive", MAX_ARCHIVE_BYTES
        )
        value = {
            "schema_version": 2,
            "kind": "v934-step4-frozen-diagnostic-capsule",
            "profile": PROFILE,
            "status": "sealed",
            "run_id": identity["run_id"],
            "git_head": identity["git_head"],
            "source_sha256": identity["source_sha256"],
            "retention": RETENTION,
            "archive": {"sha256": archive_sha, "size": archive_size},
            "entry_count": len(records),
            "entries": records,
        }
        with manifest_temp.open("xb") as stream:
            stream.write(canonical_json(value))
            stream.flush()
            os.fsync(stream.fileno())
        verify_capsule(
            archive_temp,
            manifest_temp,
            expected_run_id=identity["run_id"],
            expected_git_head=identity["git_head"],
            expected_source_sha256=identity["source_sha256"],
        )
        publish_pair(archive_temp, archive, manifest_temp, manifest)
    verified = verify_capsule(
        archive,
        manifest,
        expected_run_id=identity["run_id"],
        expected_git_head=identity["git_head"],
        expected_source_sha256=identity["source_sha256"],
    )
    return {"command": "build", **verified}


def load_manifest(path: Path) -> dict[str, Any]:
    candidate = regular_file(path, "capsule manifest", 2 * 1024 * 1024)
    try:
        raw = candidate.read_bytes()
    except OSError as error:
        raise CapsuleError("E_FILE", "cannot read capsule manifest") from error
    value = parse_json_bytes(raw, "capsule manifest")
    require(raw == canonical_json(value), "E_MANIFEST_CANONICAL", "manifest is not canonical JSON")
    exact_keys(value, MANIFEST_KEYS, "E_MANIFEST", "capsule manifest")
    require(
        value["schema_version"] == 2
        and type(value["schema_version"]) is int
        and value["kind"] == "v934-step4-frozen-diagnostic-capsule"
        and value["profile"] == PROFILE
        and value["status"] == "sealed"
        and value["retention"] == RETENTION,
        "E_MANIFEST",
        "capsule manifest identity/status differs",
    )
    require(
        type(value["run_id"]) is str and RUN_ID.fullmatch(value["run_id"]) is not None,
        "E_MANIFEST",
        "capsule manifest run id differs",
    )
    require_hex(value["git_head"], "E_MANIFEST", "capsule manifest Git head", commit=True)
    require_hex(value["source_sha256"], "E_MANIFEST", "capsule manifest source SHA")
    archive = exact_keys(value["archive"], ("sha256", "size"), "E_MANIFEST", "archive binding")
    require_hex(archive["sha256"], "E_MANIFEST", "archive SHA")
    require(
        type(archive["size"]) is int and 0 < archive["size"] <= MAX_ARCHIVE_BYTES,
        "E_MANIFEST",
        "archive size differs",
    )
    entries = value["entries"]
    require(
        type(value["entry_count"]) is int
        and value["entry_count"] == len(ENTRY_LAYOUT)
        and type(entries) is list
        and len(entries) == len(ENTRY_LAYOUT),
        "E_MANIFEST",
        "capsule entry count differs",
    )
    for record, (expected_path, expected_kind, expected_mode) in zip(entries, ENTRY_LAYOUT):
        exact_keys(record, ("path", "kind", "mode", "size", "sha256"), "E_MANIFEST", "capsule entry")
        safe_relative(record["path"], "E_MANIFEST")
        require(
            record["path"] == expected_path
            and record["kind"] == expected_kind
            and record["mode"] == expected_mode,
            "E_MANIFEST",
            "capsule member is not allowlisted",
        )
        require(
            type(record["size"]) is int and record["size"] >= 0,
            "E_MANIFEST",
            "capsule entry size differs",
        )
        if expected_kind == "directory":
            require(record["size"] == 0 and record["sha256"] is None, "E_MANIFEST", "directory binding differs")
        else:
            require_hex(record["sha256"], "E_MANIFEST", "capsule entry SHA")
    return value


def verify_gzip_framing(path: Path) -> int:
    archive = regular_file(path, "capsule archive", MAX_ARCHIVE_BYTES)
    with archive.open("rb") as stream:
        header = stream.read(10)
    require(
        len(header) == 10
        and header[:4] == b"\x1f\x8b\x08\x00"
        and header[4:8] == b"\x00\x00\x00\x00",
        "E_GZIP",
        "capsule gzip header is not deterministic",
    )
    decompressor = zlib.decompressobj(16 + zlib.MAX_WBITS)
    total = 0
    with archive.open("rb") as stream:
        while True:
            chunk = stream.read(1024 * 1024)
            if not chunk:
                break
            try:
                output = decompressor.decompress(chunk)
            except zlib.error as error:
                raise CapsuleError("E_GZIP", "capsule gzip stream is invalid") from error
            total += len(output)
            require(total <= MAX_EXTRACTED_BYTES, "E_GZIP_SIZE", "capsule is too large")
            require(not decompressor.unused_data, "E_GZIP_FRAMING", "capsule has trailing gzip data")
            if decompressor.eof:
                require(stream.read(1) == b"", "E_GZIP_FRAMING", "capsule has multiple gzip members")
                break
    require(decompressor.eof and not decompressor.unconsumed_tail, "E_GZIP_FRAMING", "capsule gzip is incomplete")
    return total


def tar_octal(value: int, width: int) -> bytes:
    require(value >= 0, "E_TAR_HEADER", "tar numeric field is negative")
    encoded = f"{value:0{width - 1}o}".encode("ascii")
    require(len(encoded) == width - 1, "E_TAR_HEADER", "tar numeric field overflows")
    return encoded + b"\x00"


def tar_name(name: str, width: int = 100) -> bytes:
    encoded = name.encode("ascii")
    require(len(encoded) < width, "E_TAR_HEADER", "tar name is too long")
    return encoded + (b"\x00" * (width - len(encoded)))


def read_exact(stream: Any, size: int, code: str, label: str) -> bytes:
    result = bytearray()
    while len(result) < size:
        chunk = stream.read(size - len(result))
        require(bool(chunk), code, f"{label} is truncated")
        result.extend(chunk)
    return bytes(result)


def discard_exact(stream: Any, size: int, code: str, label: str) -> None:
    remaining = size
    while remaining:
        chunk = stream.read(min(1024 * 1024, remaining))
        require(bool(chunk), code, f"{label} is truncated")
        remaining -= len(chunk)


def verify_tar_header(header: bytes, record: dict[str, Any]) -> None:
    require(len(header) == TAR_BLOCK_SIZE and any(header), "E_TAR_HEADER", "tar header differs")
    expected_name = record["path"] + ("/" if record["kind"] == "directory" else "")
    expected_type = b"5" if record["kind"] == "directory" else b"0"
    require(
        header[:100] == tar_name(expected_name)
        and header[100:108] == tar_octal(int(record["mode"], 8), 8)
        and header[108:116] == tar_octal(0, 8)
        and header[116:124] == tar_octal(0, 8)
        and header[124:136] == tar_octal(record["size"], 12)
        and header[136:148] == tar_octal(0, 12)
        and header[156:157] == expected_type
        and header[157:257] == (b"\x00" * 100)
        and header[257:265] == USTAR_MAGIC
        and header[265:500] == (b"\x00" * 235)
        and header[500:512] == (b"\x00" * 12),
        "E_TAR_HEADER",
        "tar header is not the fixed capsule layout",
    )
    checksum = header[148:156]
    require(
        checksum[6:] == b"\x00 "
        and all(byte in b"01234567" for byte in checksum[:6]),
        "E_TAR_HEADER",
        "tar checksum field differs",
    )
    expected_checksum = sum(header[:148] + (b" " * 8) + header[156:])
    require(
        int(checksum[:6], 8) == expected_checksum,
        "E_TAR_HEADER",
        "tar checksum differs",
    )


def verify_tar_framing(path: Path, records: list[dict[str, Any]], payload_size: int) -> None:
    require(
        payload_size % TAR_RECORD_SIZE == 0,
        "E_TAR_FRAMING",
        "tar payload does not use deterministic record framing",
    )
    consumed = 0
    try:
        with gzip.open(regular_file(path, "capsule archive"), mode="rb") as stream:
            for record in records:
                header = read_exact(stream, TAR_BLOCK_SIZE, "E_TAR_FRAMING", "tar header")
                consumed += TAR_BLOCK_SIZE
                verify_tar_header(header, record)
                discard_exact(stream, record["size"], "E_TAR_FRAMING", "tar member")
                consumed += record["size"]
                padding = (-record["size"]) % TAR_BLOCK_SIZE
                if padding:
                    padding_bytes = read_exact(stream, padding, "E_TAR_FRAMING", "tar padding")
                    require(not any(padding_bytes), "E_TAR_FRAMING", "tar padding contains data")
                    consumed += padding
            trailer = read_exact(stream, TAR_BLOCK_SIZE * 2, "E_TAR_FRAMING", "tar trailer")
            consumed += len(trailer)
            require(not any(trailer), "E_TAR_FRAMING", "tar trailer contains data")
            require(consumed <= payload_size, "E_TAR_FRAMING", "tar payload is too short")
            remaining = payload_size - consumed
            while remaining:
                block = read_exact(
                    stream,
                    min(TAR_BLOCK_SIZE, remaining),
                    "E_TAR_FRAMING",
                    "tar record padding",
                )
                require(not any(block), "E_TAR_FRAMING", "tar record padding contains data")
                remaining -= len(block)
            require(stream.read(1) == b"", "E_TAR_FRAMING", "tar payload has trailing data")
    except CapsuleError:
        raise
    except (OSError, EOFError, gzip.BadGzipFile) as error:
        raise CapsuleError("E_TAR_FRAMING", "cannot read tar payload") from error


def verify_capsule(
    archive_path: Path,
    manifest_path: Path,
    *,
    expected_run_id: str | None = None,
    expected_git_head: str | None = None,
    expected_source_sha256: str | None = None,
) -> dict[str, Any]:
    archive = regular_file(archive_path, "capsule archive", MAX_ARCHIVE_BYTES)
    manifest = load_manifest(manifest_path)
    if expected_run_id is not None:
        require(manifest["run_id"] == expected_run_id, "E_IDENTITY", "capsule run id differs")
    if expected_git_head is not None:
        require(manifest["git_head"] == expected_git_head, "E_IDENTITY", "capsule Git head differs")
    if expected_source_sha256 is not None:
        require(manifest["source_sha256"] == expected_source_sha256, "E_IDENTITY", "capsule source SHA differs")
    archive_sha, archive_size = sha256_file(archive, "capsule archive", MAX_ARCHIVE_BYTES)
    require(
        manifest["archive"] == {"sha256": archive_sha, "size": archive_size},
        "E_ARCHIVE_BINDING",
        "capsule archive binding differs",
    )
    gzip_payload_size = verify_gzip_framing(archive)
    expected = manifest["entries"]
    verify_tar_framing(archive, expected, gzip_payload_size)
    observed: list[str] = []
    extracted_size = 0
    member_payloads: dict[str, bytes] = {}
    try:
        with tarfile.open(archive, mode="r:gz") as bundle:
            members = bundle.getmembers()
            require(len(members) == len(expected), "E_ARCHIVE_SET", "capsule member count differs")
            for member, record in zip(members, expected):
                path = member.name.rstrip("/") if member.isdir() else member.name
                safe_relative(path, "E_ARCHIVE_PATH")
                observed.append(path)
                require(
                    path == record["path"]
                    and member.uid == 0
                    and member.gid == 0
                    and member.uname == ""
                    and member.gname == ""
                    and member.mtime == 0
                    and member.linkname == ""
                    and not member.pax_headers
                    and (member.mode & 0o7777) == int(record["mode"], 8),
                    "E_ARCHIVE_META",
                    "capsule member metadata differs",
                )
                if record["kind"] == "directory":
                    require(member.isdir() and member.size == 0, "E_ARCHIVE_TYPE", "capsule directory differs")
                    continue
                require(member.isreg() and member.size == record["size"], "E_ARCHIVE_TYPE", "capsule file differs")
                extracted_size += member.size
                require(extracted_size <= MAX_EXTRACTED_BYTES, "E_ARCHIVE_SIZE", "capsule extracted size exceeds limit")
                stream = bundle.extractfile(member)
                require(stream is not None, "E_ARCHIVE_READ", "cannot read capsule member")
                payload = stream.read()
                require(
                    len(payload) == record["size"]
                    and hashlib.sha256(payload).hexdigest() == record["sha256"],
                    "E_ARCHIVE_DIGEST",
                    "capsule member digest differs",
                )
                member_payloads[path] = payload
    except CapsuleError:
        raise
    except (OSError, EOFError, tarfile.TarError, gzip.BadGzipFile) as error:
        raise CapsuleError("E_ARCHIVE", "cannot read capsule archive") from error
    require(observed == [item[0] for item in ENTRY_LAYOUT], "E_ARCHIVE_SET", "capsule members are not allowlisted")
    attestation = parse_json_bytes(
        member_payloads["evidence/diagnostic-attestation.json"], "archived attestation"
    )
    require(
        member_payloads["evidence/diagnostic-attestation.json"] == canonical_json(attestation),
        "E_ATTESTATION_CANONICAL",
        "archived attestation is not canonical",
    )
    with tempfile.TemporaryDirectory(prefix="v934-capsule-verify-") as temporary_name:
        temporary = Path(temporary_name)
        attestation_path = temporary / "diagnostic-attestation.json"
        xml_path = temporary / "jacoco.xml"
        attestation_path.write_bytes(member_payloads["evidence/diagnostic-attestation.json"])
        xml_path.write_bytes(member_payloads["evidence/jacoco.xml"])
        checked_attestation, _ = load_attestation(attestation_path)
        _xml_payload, xml_sha, xml_size = validate_xml(xml_path)
    require(
        checked_attestation["identity"]["run_id"] == manifest["run_id"]
        and checked_attestation["identity"]["git_head"] == manifest["git_head"]
        and checked_attestation["identity"]["source_sha256"] == manifest["source_sha256"]
        and checked_attestation["xml"]
        == {
            "sha256": xml_sha,
            "size": xml_size,
            "deterministic_report_replay_count": 2,
        },
        "E_ATTESTATION_XML",
        "capsule identity/attestation/XML binding differs",
    )
    return {
        "run_id": manifest["run_id"],
        "git_head": manifest["git_head"],
        "source_sha256": manifest["source_sha256"],
        "archive_sha256": archive_sha,
        "archive_size": archive_size,
        "entries": len(expected),
        "status": "passed",
    }


def empty_destination(path: Path) -> Path:
    destination = absolute(path)
    if destination.exists() or destination.is_symlink():
        root = real_directory(destination, "capsule destination")
        require(not any(root.iterdir()), "E_DESTINATION", "capsule destination must be empty")
        return root
    parent = real_directory(destination.parent, "capsule destination parent")
    try:
        destination.mkdir(mode=0o700)
    except OSError as error:
        raise CapsuleError("E_DESTINATION", "cannot create capsule destination") from error
    require(destination.parent == parent, "E_DESTINATION", "capsule destination parent changed")
    return real_directory(destination, "capsule destination")


def stat_identity(metadata: os.stat_result) -> tuple[int, int, int, int, int]:
    return (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_size,
        metadata.st_mtime_ns,
        metadata.st_ctime_ns,
    )


def snapshot_regular_file(source_path: Path, destination: Path, label: str, maximum: int) -> Path:
    source = regular_file(source_path, label, maximum)
    before = os.lstat(source)
    source_descriptor = -1
    destination_descriptor = -1
    copied = 0
    try:
        source_descriptor = os.open(
            source,
            os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
        )
        opened = os.fstat(source_descriptor)
        require(
            stat_identity(opened) == stat_identity(before),
            "E_FILE_RACE",
            f"{label} changed before snapshot",
        )
        destination_descriptor = os.open(
            destination,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        with os.fdopen(source_descriptor, "rb", closefd=False) as source_stream:
            with os.fdopen(destination_descriptor, "wb", closefd=False) as destination_stream:
                while True:
                    chunk = source_stream.read(1024 * 1024)
                    if not chunk:
                        break
                    copied += len(chunk)
                    require(copied <= maximum, "E_SIZE", f"{label} exceeds size limit")
                    destination_stream.write(chunk)
                destination_stream.flush()
                os.fsync(destination_stream.fileno())
        os.fchmod(destination_descriptor, 0o600)
        after = os.lstat(source)
        require(
            stat_identity(before) == stat_identity(opened) == stat_identity(after)
            and copied == before.st_size,
            "E_FILE_RACE",
            f"{label} changed during snapshot",
        )
    except CapsuleError:
        raise
    except OSError as error:
        raise CapsuleError("E_FILE", f"cannot snapshot {label}") from error
    finally:
        if destination_descriptor >= 0:
            os.close(destination_descriptor)
        if source_descriptor >= 0:
            os.close(source_descriptor)
    return regular_file(destination, f"snapshotted {label}", maximum)


def materialize_capsule(
    archive_path: Path,
    manifest_path: Path,
    destination_root: Path,
    *,
    expected_run_id: str | None = None,
    expected_git_head: str | None = None,
    expected_source_sha256: str | None = None,
) -> dict[str, Any]:
    requested_destination = absolute(destination_root)
    snapshot_parent = real_directory(
        requested_destination.parent,
        "capsule destination parent",
    )
    with tempfile.TemporaryDirectory(
        prefix=".v934-capsule-materialize-",
        dir=snapshot_parent,
    ) as temporary_name:
        temporary = Path(temporary_name)
        archive_snapshot = snapshot_regular_file(
            archive_path,
            temporary / "archive.tar.gz",
            "capsule archive",
            MAX_ARCHIVE_BYTES,
        )
        manifest_snapshot = snapshot_regular_file(
            manifest_path,
            temporary / "manifest.json",
            "capsule manifest",
            2 * 1024 * 1024,
        )
        verified = verify_capsule(
            archive_snapshot,
            manifest_snapshot,
            expected_run_id=expected_run_id,
            expected_git_head=expected_git_head,
            expected_source_sha256=expected_source_sha256,
        )
        manifest = load_manifest(manifest_snapshot)
        destination = empty_destination(destination_root)
        try:
            with tarfile.open(archive_snapshot, mode="r:gz") as bundle:
                members = bundle.getmembers()
                require(
                    len(members) == len(manifest["entries"]),
                    "E_MATERIALIZE_SET",
                    "materialized archive member count differs",
                )
                for member, record in zip(members, manifest["entries"]):
                    path = member.name.rstrip("/") if member.isdir() else member.name
                    require(
                        path == record["path"]
                        and member.uid == 0
                        and member.gid == 0
                        and member.uname == ""
                        and member.gname == ""
                        and member.mtime == 0
                        and member.linkname == ""
                        and not member.pax_headers
                        and (member.mode & 0o7777) == int(record["mode"], 8),
                        "E_MATERIALIZE_SET",
                        "materialized archive metadata differs",
                    )
                    target = destination.joinpath(*safe_relative(record["path"], "E_MATERIALIZE_PATH").parts)
                    require(
                        target.resolve(strict=False).is_relative_to(destination),
                        "E_MATERIALIZE_PATH",
                        "materialized path escapes destination",
                    )
                    if record["kind"] == "directory":
                        require(member.isdir() and member.size == 0, "E_MATERIALIZE_SET", "materialized directory differs")
                        target.mkdir(mode=int(record["mode"], 8), parents=False, exist_ok=False)
                        os.chmod(target, int(record["mode"], 8), follow_symlinks=False)
                        continue
                    require(
                        member.isreg() and member.size == record["size"],
                        "E_MATERIALIZE_SET",
                        "materialized file differs",
                    )
                    require(target.parent.is_dir() and not target.parent.is_symlink(), "E_MATERIALIZE_PARENT", "materialized parent differs")
                    stream = bundle.extractfile(member)
                    require(stream is not None, "E_MATERIALIZE_READ", "cannot read materialized member")
                    descriptor = os.open(
                        target,
                        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
                        int(record["mode"], 8),
                    )
                    try:
                        with os.fdopen(descriptor, "wb", closefd=False) as output:
                            shutil.copyfileobj(stream, output, length=1024 * 1024)
                            output.flush()
                            os.fsync(output.fileno())
                        os.fchmod(descriptor, int(record["mode"], 8))
                    finally:
                        os.close(descriptor)
                    digest, size = sha256_file(target, "materialized capsule member")
                    require(
                        digest == record["sha256"] and size == record["size"],
                        "E_MATERIALIZE_DIGEST",
                        "materialized capsule member differs",
                    )
        except CapsuleError:
            raise
        except (OSError, EOFError, tarfile.TarError) as error:
            raise CapsuleError("E_MATERIALIZE", "cannot materialize capsule") from error
    return {"command": "materialize", **verified, "status": "passed"}


def fixture_attestation(run_id: str, git_head: str, source_sha: str, xml_payload: bytes) -> dict[str, Any]:
    xml_sha = hashlib.sha256(xml_payload).hexdigest()
    return {
        "schema_version": 1,
        "kind": "v934-step4-git-safe-diagnostic-attestation",
        "profile": PROFILE,
        "status": "verified",
        "identity": {
            "run_id": run_id,
            "git_head": git_head,
            "source_sha256": source_sha,
            "run_context_sha256": "3" * 64,
            "run_status_sha256": "4" * 64,
            "summary_sha256": "5" * 64,
            "coverage_contract_sha256": "6" * 64,
            "threshold_predecessor_sha256": "7" * 64,
            "observation_sha256": "8" * 64,
        },
        "execution_attestation": {
            "mode": "source-validated-hash-only",
            "retention": "no-execution-bytes",
            "exec_count": 23,
            "session_count": 48,
            "byte_tree_sha256": "9" * 64,
            "aggregate_exec_sha256": "a" * 64,
            "merge_semantics": "exact-session-and-jacoco-class-id-probe-bitmap-union",
            "status": "verified",
        },
        "xml": {
            "sha256": xml_sha,
            "size": len(xml_payload),
            "deterministic_report_replay_count": 2,
        },
        "source_attestation": {
            "class_universe_sha256": "b" * 64,
            "workspace_class_tree_sha256": "c" * 64,
            "workspace_bytecode_class_count": 1,
            "toolchain_receipt_sha256": "d" * 64,
            "coverage_ledger_sha256": "e" * 64,
        },
        "semantic_observation": {
            "report_inventory": {"group_count": 24, "session_count": 48},
            "aggregate_observed": {"line": {"covered": 1, "total": 1, "fraction": "1/1"}},
        },
    }


def expect_failure(cases: list[dict[str, str]], name: str, code: str, action: Any) -> None:
    try:
        action()
    except CapsuleError as error:
        require(error.code == code, "E_SELF_TEST", f"{name} returned an unexpected code")
        cases.append({"name": name, "code": code, "status": "passed"})
        return
    reject("E_SELF_TEST", f"negative case unexpectedly passed: {name}")


def run_self_test() -> dict[str, Any]:
    run_id = "step4-capsule-self-test"
    git_head = "1" * 40
    source_sha = "2" * 64
    xml_payload = (
        JACOCO_DOCTYPE
        + b'\n<report name="fixture"><counter type="LINE" missed="0" covered="1"/></report>\n'
    )
    cases: list[dict[str, str]] = []
    with tempfile.TemporaryDirectory(prefix="v934-capsule-self-test-") as temporary_name:
        temporary = Path(temporary_name)
        input_root = temporary / "input"
        output = temporary / "output"
        input_root.mkdir()
        output.mkdir()
        attestation = fixture_attestation(run_id, git_head, source_sha, xml_payload)
        attestation_path = input_root / "attestation.json"
        xml_path = input_root / "jacoco.xml"
        attestation_path.write_bytes(canonical_json(attestation))
        xml_path.write_bytes(xml_payload)
        (input_root / "run.log").write_text("ignored\n", encoding="utf-8")
        first_archive = output / "first.tar.gz"
        first_manifest = output / "first.json"
        second_archive = output / "second.tar.gz"
        second_manifest = output / "second.json"
        build_capsule(attestation_path, xml_path, first_archive, first_manifest)
        build_capsule(attestation_path, xml_path, second_archive, second_manifest)
        require(first_archive.read_bytes() == second_archive.read_bytes(), "E_SELF_TEST", "archive is not deterministic")
        require(first_manifest.read_bytes() == second_manifest.read_bytes(), "E_SELF_TEST", "manifest is not deterministic")
        cases.append({"name": "deterministic-build", "code": "passed", "status": "passed"})
        destination = temporary / "materialized"
        materialize_capsule(
            first_archive,
            first_manifest,
            destination,
            expected_run_id=run_id,
            expected_git_head=git_head,
            expected_source_sha256=source_sha,
        )
        require(
            sorted(path.relative_to(destination).as_posix() for path in destination.rglob("*"))
            == ["evidence", "evidence/diagnostic-attestation.json", "evidence/jacoco.xml"],
            "E_SELF_TEST",
            "materialized member set differs",
        )
        cases.append({"name": "materialize-exact-members", "code": "passed", "status": "passed"})
        cases.append({"name": "unrelated-run-content-ignored", "code": "passed", "status": "passed"})

        tampered_archive = output / "tampered.tar.gz"
        payload = bytearray(first_archive.read_bytes())
        payload[-9] ^= 1
        tampered_archive.write_bytes(payload)
        expect_failure(cases, "archive-tamper", "E_ARCHIVE_BINDING", lambda: verify_capsule(tampered_archive, first_manifest))

        manifest_value = load_manifest(first_manifest)
        manifest_value["schema_version"] = 1
        v1_manifest = output / "v1.json"
        v1_manifest.write_bytes(canonical_json(manifest_value))
        expect_failure(cases, "schema-v1-rejected", "E_MANIFEST", lambda: verify_capsule(first_archive, v1_manifest))

        manifest_value = load_manifest(first_manifest)
        manifest_value["entries"][1]["path"] = "evidence/extra.json"
        extra_manifest = output / "extra.json"
        extra_manifest.write_bytes(canonical_json(manifest_value))
        expect_failure(cases, "extra-member-rejected", "E_MANIFEST", lambda: verify_capsule(first_archive, extra_manifest))

        with gzip.open(first_archive, mode="rb") as stream:
            original_tar = stream.read()
        trailing_archive = output / "trailing.tar.gz"
        trailing_tar = original_tar + b"X" + (b"\x00" * (TAR_RECORD_SIZE - 1))
        with trailing_archive.open("xb") as raw:
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
                compressed.write(trailing_tar)
        trailing_sha, trailing_size = sha256_file(
            trailing_archive,
            "trailing negative archive",
            MAX_ARCHIVE_BYTES,
        )
        trailing_manifest_value = load_manifest(first_manifest)
        trailing_manifest_value["archive"] = {"sha256": trailing_sha, "size": trailing_size}
        trailing_manifest = output / "trailing.json"
        trailing_manifest.write_bytes(canonical_json(trailing_manifest_value))
        expect_failure(
            cases,
            "tar-trailing-payload-rejected",
            "E_TAR_FRAMING",
            lambda: verify_capsule(trailing_archive, trailing_manifest),
        )

        with tarfile.open(first_archive, mode="r:gz") as bundle:
            last_member = bundle.getmembers()[-1]
            logical_end = last_member.offset_data + (
                (last_member.size + TAR_BLOCK_SIZE - 1) // TAR_BLOCK_SIZE
            ) * TAR_BLOCK_SIZE
        extra_member_bytes = io.BytesIO()
        with tarfile.open(fileobj=extra_member_bytes, mode="w", format=tarfile.USTAR_FORMAT) as bundle:
            extra_payload = b"extra\n"
            extra_member = tarfile.TarInfo("evidence/extra.txt")
            extra_member.uid = 0
            extra_member.gid = 0
            extra_member.uname = ""
            extra_member.gname = ""
            extra_member.mtime = 0
            extra_member.mode = 0o644
            extra_member.size = len(extra_payload)
            bundle.addfile(extra_member, io.BytesIO(extra_payload))
        extra_raw_size = TAR_BLOCK_SIZE + (
            (len(extra_payload) + TAR_BLOCK_SIZE - 1) // TAR_BLOCK_SIZE
        ) * TAR_BLOCK_SIZE
        extra_tar = original_tar[:logical_end] + extra_member_bytes.getvalue()[:extra_raw_size]
        extra_tar += b"\x00" * (TAR_BLOCK_SIZE * 2)
        extra_tar += b"\x00" * ((-len(extra_tar)) % TAR_RECORD_SIZE)
        extra_archive = output / "extra-member.tar.gz"
        with extra_archive.open("xb") as raw:
            with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
                compressed.write(extra_tar)
        extra_archive_sha, extra_archive_size = sha256_file(
            extra_archive,
            "extra member negative archive",
            MAX_ARCHIVE_BYTES,
        )
        extra_archive_manifest_value = load_manifest(first_manifest)
        extra_archive_manifest_value["archive"] = {
            "sha256": extra_archive_sha,
            "size": extra_archive_size,
        }
        extra_archive_manifest = output / "extra-member.json"
        extra_archive_manifest.write_bytes(canonical_json(extra_archive_manifest_value))
        expect_failure(
            cases,
            "tar-extra-member-rejected",
            "E_TAR_FRAMING",
            lambda: verify_capsule(extra_archive, extra_archive_manifest),
        )

        bad_attestation = fixture_attestation(run_id, git_head, source_sha, xml_payload)
        bad_attestation["identity"]["pid"] = 1
        bad_attestation_path = input_root / "bad-attestation.json"
        bad_attestation_path.write_bytes(canonical_json(bad_attestation))
        expect_failure(
            cases,
            "identity-extra-key-rejected",
            "E_ATTESTATION",
            lambda: build_capsule(bad_attestation_path, xml_path, output / "bad.tar.gz", output / "bad.json"),
        )

        for name, key, value, expected_code in (
            ("raw-exec-attestation-rejected", "note", "aggregate.exec", "E_ATTESTATION_FORBIDDEN"),
            ("raw-log-attestation-rejected", "note", "run.log", "E_ATTESTATION_FORBIDDEN"),
            ("container-identity-rejected", "containerIdentity", "opaque", "E_ATTESTATION_FORBIDDEN"),
            ("process-identity-rejected", "processIdentity", "opaque", "E_ATTESTATION_FORBIDDEN"),
            ("sensitive-attestation-rejected", "note", "token=very-secret-value", "E_CAPSULE_SENSITIVE"),
        ):
            forbidden_attestation = fixture_attestation(run_id, git_head, source_sha, xml_payload)
            forbidden_attestation["semantic_observation"][key] = value
            forbidden_attestation_path = input_root / f"{name}.json"
            forbidden_attestation_path.write_bytes(canonical_json(forbidden_attestation))
            expect_failure(
                cases,
                name,
                expected_code,
                lambda path=forbidden_attestation_path, suffix=name: build_capsule(
                    path,
                    xml_path,
                    output / f"{suffix}.tar.gz",
                    output / f"{suffix}.json",
                ),
            )

        untrusted_doctype_xml = input_root / "untrusted-doctype.xml"
        untrusted_doctype_payload = (
            b"<!-- "
            + JACOCO_DOCTYPE
            + b" -->\n<!DOCTYPE report SYSTEM \"untrusted.dtd\">\n<report name=\"fixture\"/>\n"
        )
        untrusted_doctype_xml.write_bytes(untrusted_doctype_payload)
        untrusted_doctype_attestation = input_root / "untrusted-doctype.json"
        untrusted_doctype_attestation.write_bytes(
            canonical_json(
                fixture_attestation(run_id, git_head, source_sha, untrusted_doctype_payload)
            )
        )
        expect_failure(
            cases,
            "untrusted-doctype-rejected",
            "E_XML",
            lambda: build_capsule(
                untrusted_doctype_attestation,
                untrusted_doctype_xml,
                output / "untrusted-doctype.tar.gz",
                output / "untrusted-doctype.json",
            ),
        )

        raw_exec_xml = input_root / "raw-exec.xml"
        raw_exec_payload = (
            JACOCO_DOCTYPE
            + b'\n<report name="aggregate.exec"><counter type="LINE" missed="0" covered="1"/></report>\n'
        )
        raw_exec_xml.write_bytes(raw_exec_payload)
        raw_exec_attestation = input_root / "raw-exec.json"
        raw_exec_attestation.write_bytes(
            canonical_json(fixture_attestation(run_id, git_head, source_sha, raw_exec_payload))
        )
        expect_failure(
            cases,
            "raw-exec-xml-rejected",
            "E_CAPSULE_FORBIDDEN",
            lambda: build_capsule(
                raw_exec_attestation,
                raw_exec_xml,
                output / "raw-exec.tar.gz",
                output / "raw-exec.json",
            ),
        )

        for name, marker in (
            ("raw-log-xml-rejected", "report.log"),
            ("container-identity-xml-rejected", "containerId=opaque"),
            ("process-identity-xml-rejected", "processIdentity=opaque"),
        ):
            forbidden_xml_payload = (
                JACOCO_DOCTYPE
                + f'\n<report name="{marker}"><counter type="LINE" missed="0" covered="1"/></report>\n'.encode("ascii")
            )
            forbidden_xml_path = input_root / f"{name}.xml"
            forbidden_xml_path.write_bytes(forbidden_xml_payload)
            forbidden_xml_attestation = input_root / f"{name}.json"
            forbidden_xml_attestation.write_bytes(
                canonical_json(
                    fixture_attestation(run_id, git_head, source_sha, forbidden_xml_payload)
                )
            )
            expect_failure(
                cases,
                name,
                "E_CAPSULE_FORBIDDEN",
                lambda attestation_file=forbidden_xml_attestation, xml_file=forbidden_xml_path, suffix=name: build_capsule(
                    attestation_file,
                    xml_file,
                    output / f"{suffix}.tar.gz",
                    output / f"{suffix}.json",
                ),
            )

        sensitive_xml = input_root / "sensitive.xml"
        sensitive_payload = (
            JACOCO_DOCTYPE
            + b'\n<report name="pass&#119;ord=very-secret-value"><counter type="LINE" missed="0" covered="1"/></report>\n'
        )
        sensitive_xml.write_bytes(
            sensitive_payload
        )
        sensitive_attestation = input_root / "sensitive.json"
        sensitive_attestation.write_bytes(
            canonical_json(fixture_attestation(run_id, git_head, source_sha, sensitive_payload))
        )
        expect_failure(
            cases,
            "encoded-sensitive-xml-rejected",
            "E_CAPSULE_SENSITIVE",
            lambda: build_capsule(sensitive_attestation, sensitive_xml, output / "sensitive.tar.gz", output / "sensitive.json"),
        )

        nonempty = temporary / "nonempty"
        nonempty.mkdir()
        (nonempty / "keep").write_text("keep", encoding="utf-8")
        expect_failure(cases, "nonempty-destination", "E_DESTINATION", lambda: materialize_capsule(first_archive, first_manifest, nonempty))
    return {
        "schema_version": 2,
        "kind": "v934-step4-frozen-diagnostic-capsule-self-test",
        "case_count": len(cases),
        "cases": cases,
        "status": "passed",
    }


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)
    build = commands.add_parser("build")
    build.add_argument("--attestation", type=Path, required=True)
    build.add_argument("--jacoco-xml", type=Path, required=True)
    build.add_argument("--archive", type=Path, required=True)
    build.add_argument("--manifest", type=Path, required=True)
    verify = commands.add_parser("verify")
    verify.add_argument("--archive", type=Path, required=True)
    verify.add_argument("--manifest", type=Path, required=True)
    materialize = commands.add_parser("materialize")
    materialize.add_argument("--archive", type=Path, required=True)
    materialize.add_argument("--manifest", type=Path, required=True)
    materialize.add_argument("--destination-root", type=Path, required=True)
    for command in (verify, materialize):
        command.add_argument("--expected-run-id")
        command.add_argument("--expected-git-head")
        command.add_argument("--expected-source-sha256")
    commands.add_parser("self-test")
    return root


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "build":
            result = build_capsule(args.attestation, args.jacoco_xml, args.archive, args.manifest)
        elif args.command == "verify":
            result = verify_capsule(
                args.archive,
                args.manifest,
                expected_run_id=args.expected_run_id,
                expected_git_head=args.expected_git_head,
                expected_source_sha256=args.expected_source_sha256,
            )
        elif args.command == "materialize":
            result = materialize_capsule(
                args.archive,
                args.manifest,
                args.destination_root,
                expected_run_id=args.expected_run_id,
                expected_git_head=args.expected_git_head,
                expected_source_sha256=args.expected_source_sha256,
            )
        else:
            result = run_self_test()
    except CapsuleError as error:
        print(f"[v934-frozen-diagnostic-capsule] {error.code}: {error}", file=os.sys.stderr)
        return 2
    print(json.dumps(result, ensure_ascii=True, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
