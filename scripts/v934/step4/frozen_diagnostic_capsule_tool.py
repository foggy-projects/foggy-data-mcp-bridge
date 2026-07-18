#!/usr/bin/env python3
"""Build and replay a deterministic, portable Step 4 diagnostic capsule."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import tarfile
import tempfile
from typing import Any, Iterable
import zlib


HERE = Path(__file__).resolve().parent
RUN_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
HEX40 = re.compile(r"[0-9a-f]{40}")
HEX64 = re.compile(r"[0-9a-f]{64}")
OCTAL_MODE = re.compile(r"0[0-7]{3}")
MAX_ARCHIVE_BYTES = 1024 * 1024 * 1024
MAX_EXTRACTED_BYTES = 2 * 1024 * 1024 * 1024
MAX_ENTRIES = 200_000

SUPPORT_FILES = (
    "scripts/v934/contract-freeze.json",
    "scripts/v934/coverage-thresholds.json",
    "scripts/v934/step4/coverage-exec-ledger.tsv",
    "scripts/v934/step4/coverage_xml_tool.py",
    "scripts/v934/step4/reporter_effective_pom_tool.py",
    "scripts/v934/step4/toolchain_receipt_tool.py",
)
NEGATIVE_FIXTURE_LINKS = {
    "negative/coverage-exec/symlink.exec": "report/jacoco-aggregate.exec",
    "negative/coverage-xml/symlink.xml": "report/jacoco-aggregate/jacoco.xml",
}


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
            reject("E_JSON_DUPLICATE", f"duplicate JSON key: {key}")
        result[key] = value
    return result


def parse_json_bytes(payload: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            payload.decode("utf-8"),
            object_pairs_hook=unique_object,
            parse_constant=lambda token: reject(
                "E_JSON", f"{label} contains non-finite number: {token}"
            ),
        )
    except CapsuleError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise CapsuleError("E_JSON", f"cannot parse {label}: {error}") from error
    require(type(value) is dict, "E_JSON", f"{label} must be an object")
    return value


def exact_keys(value: dict[str, Any], expected: Iterable[str], code: str, label: str) -> None:
    wanted = set(expected)
    require(
        set(value) == wanted,
        code,
        f"{label} keys differ: missing={sorted(wanted - set(value))} "
        f"extra={sorted(set(value) - wanted)}",
    )


def absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path)))


def real_directory(path: Path, label: str) -> Path:
    candidate = absolute(path)
    try:
        metadata = os.lstat(candidate)
    except OSError as error:
        raise CapsuleError("E_DIRECTORY", f"cannot inspect {label}: {error}") from error
    require(
        stat.S_ISDIR(metadata.st_mode) and not stat.S_ISLNK(metadata.st_mode),
        "E_DIRECTORY",
        f"{label} is not a real directory: {candidate}",
    )
    require(
        candidate.resolve(strict=True) == candidate,
        "E_SYMLINK",
        f"{label} has symlinked components: {candidate}",
    )
    return candidate


def regular_file(path: Path, label: str, maximum: int | None = None) -> Path:
    candidate = absolute(path)
    try:
        metadata = os.lstat(candidate)
    except OSError as error:
        raise CapsuleError("E_FILE", f"cannot inspect {label}: {error}") from error
    require(
        stat.S_ISREG(metadata.st_mode) and not stat.S_ISLNK(metadata.st_mode),
        "E_SPECIAL",
        f"{label} is not a regular file: {candidate}",
    )
    require(
        candidate.resolve(strict=True) == candidate,
        "E_SYMLINK",
        f"{label} has symlinked components: {candidate}",
    )
    if maximum is not None:
        require(metadata.st_size <= maximum, "E_SIZE", f"{label} exceeds size limit")
    return candidate


def sha256_file(path: Path, label: str, maximum: int | None = None) -> tuple[str, int]:
    candidate = regular_file(path, label, maximum)
    before = candidate.stat()
    digest = hashlib.sha256()
    size = 0
    with candidate.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
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
        f"invalid capsule path: {value!r}",
    )
    pure = PurePosixPath(value)
    require(
        not pure.is_absolute()
        and pure.as_posix() == value
        and all(part not in ("", ".", "..") for part in pure.parts),
        code,
        f"unsafe capsule path: {value!r}",
    )
    return pure


def load_json_file(path: Path, label: str, maximum: int = 16 * 1024 * 1024) -> dict[str, Any]:
    candidate = regular_file(path, label, maximum)
    return parse_json_bytes(candidate.read_bytes(), label)


def frozen_modules(repo_root: Path) -> list[str]:
    freeze = load_json_file(
        repo_root / "scripts/v934/contract-freeze.json", "Step 1 contract freeze"
    )
    reactor = freeze.get("reactor")
    require(type(reactor) is dict, "E_FREEZE", "Step 1 reactor is missing")
    modules = reactor.get("modules")
    require(
        reactor.get("module_count") == 24
        and type(reactor.get("module_count")) is int
        and isinstance(modules, list)
        and len(modules) == 24
        and len(set(modules)) == 24
        and modules == sorted(modules),
        "E_FREEZE",
        "Step 1 reactor must contain exact 24 sorted modules",
    )
    result: list[str] = []
    for module in modules:
        require(type(module) is str, "E_FREEZE", "module path is not a string")
        safe_relative(module, "E_FREEZE")
        result.append(module)
    return result


def mode_text(metadata: os.stat_result) -> str:
    mode = stat.S_IMODE(metadata.st_mode)
    require(mode & 0o7000 == 0, "E_MODE", "special permission bits are forbidden")
    return f"0{mode:03o}"


def add_ancestors(paths: set[str], relative: PurePosixPath) -> None:
    parent = relative.parent
    while parent != PurePosixPath("."):
        paths.add(parent.as_posix())
        parent = parent.parent


def allowed_negative_fixture_links(run_id: str) -> dict[str, str]:
    prefix = f"target/v934-step4-coverage/runs/{run_id}/"
    return {
        f"{prefix}{link}": f"{prefix}{target}"
        for link, target in NEGATIVE_FIXTURE_LINKS.items()
    }


def validate_omitted_link(
    repo_root: Path,
    relative: str,
    expected_target: str,
) -> None:
    link = repo_root.joinpath(*PurePosixPath(relative).parts)
    target = repo_root.joinpath(*PurePosixPath(expected_target).parts)
    require(link.is_symlink(), "E_SYMLINK", f"negative fixture link differs: {relative}")
    try:
        resolved = link.resolve(strict=True)
    except OSError as error:
        raise CapsuleError("E_SYMLINK", f"negative fixture link is broken: {relative}") from error
    require(
        resolved == target.resolve(strict=True),
        "E_SYMLINK",
        f"negative fixture link target differs: {relative}",
    )
    regular_file(target, f"negative fixture link target {expected_target}")


def scan_root(
    repo_root: Path,
    relative_value: str,
    collected: set[str],
    allowed_links: dict[str, str],
    omitted_links: set[str],
) -> None:
    relative = safe_relative(relative_value)
    candidate = repo_root.joinpath(*relative.parts)
    try:
        metadata = os.lstat(candidate)
    except OSError as error:
        raise CapsuleError("E_CLOSURE", f"missing capsule input {relative_value}: {error}") from error
    require(not stat.S_ISLNK(metadata.st_mode), "E_SYMLINK", f"capsule input is symlinked: {relative_value}")
    add_ancestors(collected, relative)
    if stat.S_ISREG(metadata.st_mode):
        collected.add(relative.as_posix())
        return
    require(stat.S_ISDIR(metadata.st_mode), "E_SPECIAL", f"capsule input is special: {relative_value}")
    for current, directories, files in os.walk(candidate, topdown=True, followlinks=False):
        directories.sort(key=lambda item: item.encode("utf-8"))
        files.sort(key=lambda item: item.encode("utf-8"))
        current_path = Path(current)
        current_relative = current_path.relative_to(repo_root).as_posix()
        collected.add(current_relative)
        for name in [*directories, *files]:
            child = current_path / name
            child_relative = child.relative_to(repo_root).as_posix()
            child_stat = os.lstat(child)
            if stat.S_ISLNK(child_stat.st_mode):
                expected_target = allowed_links.get(child_relative)
                require(
                    expected_target is not None,
                    "E_SYMLINK",
                    f"capsule closure contains symlink: {child_relative}",
                )
                validate_omitted_link(repo_root, child_relative, expected_target)
                omitted_links.add(child_relative)
                continue
            require(
                stat.S_ISDIR(child_stat.st_mode) or stat.S_ISREG(child_stat.st_mode),
                "E_SPECIAL",
                f"capsule closure contains special file: {child_relative}",
            )
            collected.add(child_relative)


def closure_paths(repo_root: Path, run_id: str) -> tuple[list[str], list[str]]:
    require(RUN_ID.fullmatch(run_id) is not None, "E_RUN_ID", "unsafe diagnostic run id")
    roots = [
        f"target/v934-step4-coverage/runs/{run_id}",
        *SUPPORT_FILES,
    ]
    for module in frozen_modules(repo_root):
        roots.extend((f"{module}/pom.xml", f"{module}/target/classes"))
    collected: set[str] = set()
    allowed_links = allowed_negative_fixture_links(run_id)
    omitted_links: set[str] = set()
    for relative in roots:
        scan_root(
            repo_root,
            relative,
            collected,
            allowed_links,
            omitted_links,
        )
    require(
        omitted_links == set(allowed_links),
        "E_CLOSURE",
        "diagnostic negative fixture symlink set differs",
    )
    result = sorted(collected, key=lambda item: item.encode("utf-8"))
    require(0 < len(result) <= MAX_ENTRIES, "E_ENTRY_COUNT", "capsule entry count differs")
    return result, sorted(omitted_links, key=lambda item: item.encode("utf-8"))


def entry_record(repo_root: Path, relative: str) -> dict[str, Any]:
    pure = safe_relative(relative)
    path = repo_root.joinpath(*pure.parts)
    metadata = os.lstat(path)
    mode = mode_text(metadata)
    if stat.S_ISDIR(metadata.st_mode):
        return {
            "path": relative,
            "kind": "directory",
            "mode": mode,
            "mtime_ns": metadata.st_mtime_ns,
            "size": 0,
            "sha256": None,
        }
    require(stat.S_ISREG(metadata.st_mode), "E_SPECIAL", f"unsupported capsule entry: {relative}")
    digest, size = sha256_file(path, f"capsule input {relative}")
    return {
        "path": relative,
        "kind": "file",
        "mode": mode,
        "mtime_ns": metadata.st_mtime_ns,
        "size": size,
        "sha256": digest,
    }


def run_identity(repo_root: Path, run_id: str) -> tuple[str, str]:
    context_path = repo_root / "target/v934-step4-coverage/runs" / run_id / "run-context.json"
    context = load_json_file(context_path, "diagnostic run context", 64 * 1024)
    require(context.get("run_id") == run_id, "E_IDENTITY", "run context run id differs")
    git_head = context.get("git_head")
    source_sha = context.get("source_sha256")
    require(type(git_head) is str and HEX40.fullmatch(git_head) is not None, "E_IDENTITY", "run Git HEAD differs")
    require(type(source_sha) is str and HEX64.fullmatch(source_sha) is not None, "E_IDENTITY", "run source SHA differs")
    return git_head, source_sha


def write_deterministic_archive(repo_root: Path, records: list[dict[str, Any]], output: Path) -> None:
    with output.open("xb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w", format=tarfile.GNU_FORMAT) as archive:
                for record in records:
                    relative = record["path"]
                    source = repo_root.joinpath(*PurePosixPath(relative).parts)
                    info = tarfile.TarInfo(relative)
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
                        with source.open("rb") as stream:
                            archive.addfile(info, stream)
        raw.flush()
        os.fsync(raw.fileno())


def publish_pair(archive_temp: Path, archive: Path, manifest_temp: Path, manifest: Path) -> None:
    archive = absolute(archive)
    manifest = absolute(manifest)
    require(archive != manifest, "E_OUTPUT", "archive and manifest paths must differ")
    require(archive.parent == manifest.parent, "E_OUTPUT", "archive and manifest must share one output directory")
    parent = real_directory(archive.parent, "capsule output directory")
    require(not archive.exists() and not archive.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {archive}")
    require(not manifest.exists() and not manifest.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {manifest}")
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
            try:
                manifest.unlink()
            except OSError:
                pass
        if archive_published:
            try:
                archive.unlink()
            except OSError:
                pass
        raise CapsuleError("E_OUTPUT", f"cannot publish capsule outputs: {error}") from error


def build_capsule(
    repo_root: Path,
    run_id: str,
    archive_path: Path,
    manifest_path: Path,
) -> dict[str, Any]:
    root = real_directory(repo_root, "capsule repository root")
    archive = absolute(archive_path)
    manifest = absolute(manifest_path)
    require(archive.parent == manifest.parent, "E_OUTPUT", "capsule outputs must share a directory")
    output_parent = real_directory(archive.parent, "capsule output directory")
    require(not archive.exists() and not archive.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {archive}")
    require(not manifest.exists() and not manifest.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {manifest}")
    git_head, source_sha = run_identity(root, run_id)
    relative_paths, omitted_links = closure_paths(root, run_id)
    records = [entry_record(root, relative) for relative in relative_paths]
    with tempfile.TemporaryDirectory(prefix=".v934-capsule-build-", dir=output_parent) as temporary_name:
        temporary = Path(temporary_name)
        archive_temp = temporary / "capsule.tar.gz"
        manifest_temp = temporary / "capsule.manifest.json"
        write_deterministic_archive(root, records, archive_temp)
        archive_sha, archive_size = sha256_file(
            archive_temp, "staged capsule archive", MAX_ARCHIVE_BYTES
        )
        value = {
            "schema_version": 1,
            "kind": "v934-step4-frozen-diagnostic-capsule",
            "status": "sealed",
            "run_id": run_id,
            "git_head": git_head,
            "source_sha256": source_sha,
            "archive": {"sha256": archive_sha, "size": archive_size},
            "omitted_negative_fixture_symlinks": omitted_links,
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
            expected_run_id=run_id,
            expected_git_head=git_head,
            expected_source_sha256=source_sha,
        )
        publish_pair(archive_temp, archive, manifest_temp, manifest)
    verify_capsule(
        archive,
        manifest,
        expected_run_id=run_id,
        expected_git_head=git_head,
        expected_source_sha256=source_sha,
    )
    return {
        "command": "build",
        "run_id": run_id,
        "git_head": git_head,
        "source_sha256": source_sha,
        "archive_sha256": archive_sha,
        "archive_size": archive_size,
        "entries": len(records),
        "status": "passed",
    }


def load_manifest(path: Path) -> dict[str, Any]:
    candidate = regular_file(path, "capsule manifest", 64 * 1024 * 1024)
    raw = candidate.read_bytes()
    value = parse_json_bytes(raw, "capsule manifest")
    require(raw == canonical_json(value), "E_MANIFEST_CANONICAL", "capsule manifest is not canonical JSON")
    exact_keys(
        value,
        (
            "schema_version", "kind", "status", "run_id", "git_head",
            "source_sha256", "archive", "entry_count", "entries",
            "omitted_negative_fixture_symlinks",
        ),
        "E_MANIFEST",
        "capsule manifest",
    )
    require(
        value["schema_version"] == 1
        and type(value["schema_version"]) is int
        and value["kind"] == "v934-step4-frozen-diagnostic-capsule"
        and value["status"] == "sealed",
        "E_MANIFEST",
        "capsule manifest identity/status differs",
    )
    require(type(value["run_id"]) is str and RUN_ID.fullmatch(value["run_id"]) is not None, "E_MANIFEST", "capsule run id differs")
    require(type(value["git_head"]) is str and HEX40.fullmatch(value["git_head"]) is not None, "E_MANIFEST", "capsule Git HEAD differs")
    require(type(value["source_sha256"]) is str and HEX64.fullmatch(value["source_sha256"]) is not None, "E_MANIFEST", "capsule source SHA differs")
    require(
        value["omitted_negative_fixture_symlinks"]
        == sorted(
            allowed_negative_fixture_links(value["run_id"]),
            key=lambda item: item.encode("utf-8"),
        ),
        "E_MANIFEST",
        "omitted diagnostic negative fixture symlink set differs",
    )
    archive = value["archive"]
    require(type(archive) is dict, "E_MANIFEST", "capsule archive binding is absent")
    exact_keys(archive, ("sha256", "size"), "E_MANIFEST", "capsule archive binding")
    require(type(archive["sha256"]) is str and HEX64.fullmatch(archive["sha256"]) is not None, "E_MANIFEST", "capsule archive SHA differs")
    require(type(archive["size"]) is int and 0 < archive["size"] <= MAX_ARCHIVE_BYTES, "E_MANIFEST", "capsule archive size differs")
    entries = value["entries"]
    require(
        isinstance(entries, list)
        and type(value["entry_count"]) is int
        and value["entry_count"] == len(entries)
        and 0 < len(entries) <= MAX_ENTRIES,
        "E_MANIFEST",
        "capsule entry count differs",
    )
    paths: list[str] = []
    total = 0
    for number, record in enumerate(entries, 1):
        require(type(record) is dict, "E_MANIFEST", f"capsule entry {number} is not an object")
        exact_keys(
            record,
            ("path", "kind", "mode", "mtime_ns", "size", "sha256"),
            "E_MANIFEST",
            f"capsule entry {number}",
        )
        safe_relative(record["path"], "E_MANIFEST")
        require(record["kind"] in ("directory", "file"), "E_MANIFEST", f"capsule entry kind differs: {number}")
        require(type(record["mode"]) is str and OCTAL_MODE.fullmatch(record["mode"]) is not None and int(record["mode"], 8) & 0o7000 == 0, "E_MANIFEST", f"capsule entry mode differs: {number}")
        require(
            type(record["mtime_ns"]) is int and record["mtime_ns"] > 0,
            "E_MANIFEST",
            f"capsule entry mtime differs: {number}",
        )
        require(type(record["size"]) is int and record["size"] >= 0, "E_MANIFEST", f"capsule entry size differs: {number}")
        if record["kind"] == "directory":
            require(record["size"] == 0 and record["sha256"] is None, "E_MANIFEST", f"directory binding differs: {record['path']}")
        else:
            require(type(record["sha256"]) is str and HEX64.fullmatch(record["sha256"]) is not None, "E_MANIFEST", f"file SHA differs: {record['path']}")
            total += record["size"]
        paths.append(record["path"])
    require(paths == sorted(set(paths), key=lambda item: item.encode("utf-8")), "E_MANIFEST", "capsule paths are not sorted unique")
    require(total <= MAX_EXTRACTED_BYTES, "E_MANIFEST", "capsule extracted size exceeds limit")
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
                raise CapsuleError("E_GZIP", f"capsule gzip stream is invalid: {error}") from error
            total += len(output)
            require(total <= MAX_EXTRACTED_BYTES, "E_GZIP_SIZE", "capsule gzip payload exceeds limit")
            require(not decompressor.unused_data, "E_GZIP_FRAMING", "capsule has concatenated/trailing gzip data")
            if decompressor.eof:
                require(stream.read(1) == b"", "E_GZIP_FRAMING", "capsule has a concatenated gzip member")
                break
    require(decompressor.eof and not decompressor.unconsumed_tail, "E_GZIP_FRAMING", "capsule gzip member is incomplete")
    try:
        tail = decompressor.flush()
    except zlib.error as error:
        raise CapsuleError("E_GZIP", f"cannot finalize capsule gzip stream: {error}") from error
    total += len(tail)
    require(total <= MAX_EXTRACTED_BYTES, "E_GZIP_SIZE", "capsule gzip payload exceeds limit")
    return total


def verify_tar_framing(path: Path, members: list[tarfile.TarInfo], payload_size: int) -> None:
    require(bool(members), "E_TAR_FRAMING", "capsule tar has no members")
    logical_end = max(
        member.offset_data + ((member.size + tarfile.BLOCKSIZE - 1) // tarfile.BLOCKSIZE) * tarfile.BLOCKSIZE
        for member in members
    )
    minimum = logical_end + 2 * tarfile.BLOCKSIZE
    expected_size = (
        (minimum + tarfile.RECORDSIZE - 1) // tarfile.RECORDSIZE
    ) * tarfile.RECORDSIZE
    require(
        payload_size == expected_size,
        "E_TAR_FRAMING",
        "capsule tar record framing/trailer size differs",
    )
    with gzip.open(regular_file(path, "capsule archive"), mode="rb") as stream:
        stream.seek(logical_end)
        remaining = payload_size - logical_end
        while remaining:
            chunk = stream.read(min(1024 * 1024, remaining))
            require(bool(chunk), "E_TAR_FRAMING", "capsule tar trailer is truncated")
            require(not any(chunk), "E_TAR_FRAMING", "capsule tar trailer contains non-zero data")
            remaining -= len(chunk)
        require(stream.read(1) == b"", "E_TAR_FRAMING", "capsule tar has trailing payload")


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
        require(manifest["run_id"] == expected_run_id, "E_IDENTITY", "capsule run id differs from expected")
    if expected_git_head is not None:
        require(manifest["git_head"] == expected_git_head, "E_IDENTITY", "capsule Git HEAD differs from expected")
    if expected_source_sha256 is not None:
        require(manifest["source_sha256"] == expected_source_sha256, "E_IDENTITY", "capsule source SHA differs from expected")
    archive_sha, archive_size = sha256_file(archive, "capsule archive", MAX_ARCHIVE_BYTES)
    require(
        manifest["archive"] == {"sha256": archive_sha, "size": archive_size},
        "E_ARCHIVE_BINDING",
        "capsule archive hash/size differs",
    )
    gzip_payload_size = verify_gzip_framing(archive)
    expected = manifest["entries"]
    observed_paths: list[str] = []
    extracted_size = 0
    try:
        with tarfile.open(archive, mode="r:gz") as bundle:
            members = bundle.getmembers()
            require(len(members) == len(expected), "E_ARCHIVE_SET", "capsule archive entry count differs")
            for number, (member, record) in enumerate(zip(members, expected), 1):
                path = member.name.rstrip("/") if member.isdir() else member.name
                safe_relative(path, "E_ARCHIVE_PATH")
                observed_paths.append(path)
                require(path == record["path"], "E_ARCHIVE_SET", f"capsule archive order/path differs at {number}")
                require(member.uid == 0 and member.gid == 0 and member.uname == "" and member.gname == "" and member.mtime == 0 and member.linkname == "" and not member.pax_headers, "E_ARCHIVE_META", f"capsule archive metadata differs: {path}")
                require((member.mode & 0o7777) == int(record["mode"], 8), "E_ARCHIVE_MODE", f"capsule archive mode differs: {path}")
                if record["kind"] == "directory":
                    require(member.isdir() and member.size == 0, "E_ARCHIVE_TYPE", f"capsule directory entry differs: {path}")
                    continue
                require(member.isreg() and member.size == record["size"], "E_ARCHIVE_TYPE", f"capsule file entry differs: {path}")
                extracted_size += member.size
                require(extracted_size <= MAX_EXTRACTED_BYTES, "E_ARCHIVE_SIZE", "capsule extracted size exceeds limit")
                stream = bundle.extractfile(member)
                require(stream is not None, "E_ARCHIVE_READ", f"cannot read capsule entry: {path}")
                digest = hashlib.sha256()
                size = 0
                for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                    digest.update(chunk)
                    size += len(chunk)
                require(size == record["size"] and digest.hexdigest() == record["sha256"], "E_ARCHIVE_DIGEST", f"capsule entry digest differs: {path}")
    except CapsuleError:
        raise
    except (OSError, EOFError, tarfile.TarError, gzip.BadGzipFile) as error:
        raise CapsuleError("E_ARCHIVE", f"cannot read capsule archive: {error}") from error
    verify_tar_framing(archive, members, gzip_payload_size)
    require(observed_paths == [record["path"] for record in expected], "E_ARCHIVE_SET", "capsule archive set differs")
    return {
        "command": "verify",
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
        raise CapsuleError("E_DESTINATION", f"cannot create capsule destination: {error}") from error
    require(destination.parent == parent, "E_DESTINATION", "capsule destination parent changed")
    return real_directory(destination, "capsule destination")


def materialize_capsule(
    archive_path: Path,
    manifest_path: Path,
    destination_root: Path,
    *,
    expected_run_id: str | None = None,
    expected_git_head: str | None = None,
    expected_source_sha256: str | None = None,
) -> dict[str, Any]:
    verified = verify_capsule(
        archive_path,
        manifest_path,
        expected_run_id=expected_run_id,
        expected_git_head=expected_git_head,
        expected_source_sha256=expected_source_sha256,
    )
    manifest = load_manifest(manifest_path)
    destination = empty_destination(destination_root)
    records = manifest["entries"]
    try:
        with tarfile.open(regular_file(archive_path, "capsule archive"), mode="r:gz") as bundle:
            members = bundle.getmembers()
            for member, record in zip(members, records):
                pure = safe_relative(record["path"], "E_MATERIALIZE_PATH")
                target = destination.joinpath(*pure.parts)
                require(target.resolve(strict=False).is_relative_to(destination), "E_MATERIALIZE_PATH", f"materialized path escapes: {record['path']}")
                if record["kind"] == "directory":
                    target.mkdir(mode=int(record["mode"], 8), parents=False, exist_ok=False)
                    os.chmod(target, int(record["mode"], 8), follow_symlinks=False)
                    continue
                require(target.parent.is_dir() and not target.parent.is_symlink(), "E_MATERIALIZE_PARENT", f"materialized parent differs: {record['path']}")
                stream = bundle.extractfile(member)
                require(stream is not None, "E_MATERIALIZE_READ", f"cannot read materialized entry: {record['path']}")
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
                digest, size = sha256_file(target, f"materialized {record['path']}")
                require(digest == record["sha256"] and size == record["size"], "E_MATERIALIZE_DIGEST", f"materialized entry differs: {record['path']}")
                os.utime(
                    target,
                    ns=(record["mtime_ns"], record["mtime_ns"]),
                    follow_symlinks=False,
                )
            for record in sorted(
                (row for row in records if row["kind"] == "directory"),
                key=lambda row: len(PurePosixPath(row["path"]).parts),
                reverse=True,
            ):
                target = destination.joinpath(*PurePosixPath(record["path"]).parts)
                os.chmod(target, int(record["mode"], 8), follow_symlinks=False)
                os.utime(
                    target,
                    ns=(record["mtime_ns"], record["mtime_ns"]),
                    follow_symlinks=False,
                )
    except CapsuleError:
        raise
    except (OSError, EOFError, tarfile.TarError) as error:
        raise CapsuleError("E_MATERIALIZE", f"cannot materialize capsule: {error}") from error
    return {
        **verified,
        "command": "materialize",
        "destination_root": os.fspath(destination),
        "status": "passed",
    }


def fixture_repo(root: Path, run_id: str, git_head: str, source_sha: str) -> None:
    modules = [f"module-{number:02d}" for number in range(24)]
    support = [*SUPPORT_FILES]
    for relative in support:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("fixture\n", encoding="utf-8")
    freeze = {"reactor": {"module_count": 24, "modules": modules}}
    (root / "scripts/v934/contract-freeze.json").write_bytes(canonical_json(freeze))
    for module in modules:
        module_root = root / module
        classes = module_root / "target/classes/example"
        classes.mkdir(parents=True)
        (module_root / "pom.xml").write_text(f"<project><artifactId>{module}</artifactId></project>\n", encoding="utf-8")
        (classes / "Fixture.class").write_bytes(f"{module}\n".encode("ascii"))
    run = root / "target/v934-step4-coverage/runs" / run_id
    (run / "report").mkdir(parents=True)
    (run / "run-context.json").write_bytes(
        canonical_json({"run_id": run_id, "git_head": git_head, "source_sha256": source_sha})
    )
    (run / "report/evidence.bin").write_bytes(b"diagnostic-evidence\n")
    (run / "report/jacoco-aggregate.exec").write_bytes(b"exec\n")
    (run / "report/jacoco-aggregate").mkdir()
    (run / "report/jacoco-aggregate/jacoco.xml").write_bytes(b"<report/>\n")
    for relative, target in NEGATIVE_FIXTURE_LINKS.items():
        link = run / relative
        link.parent.mkdir(parents=True, exist_ok=True)
        link.symlink_to(run / target)


def expect_failure(cases: list[dict[str, str]], name: str, code: str, action: Any) -> None:
    try:
        action()
    except CapsuleError as error:
        require(error.code == code, "E_SELF_TEST", f"{name} expected {code}, got {error.code}")
        cases.append({"name": name, "code": code, "status": "passed"})
        return
    reject("E_SELF_TEST", f"negative case unexpectedly passed: {name}")


def run_self_test() -> dict[str, Any]:
    run_id = "step4-capsule-self-test"
    git_head = "1" * 40
    source_sha = "2" * 64
    cases: list[dict[str, str]] = []
    with tempfile.TemporaryDirectory(prefix="v934-capsule-self-test-") as temporary_name:
        temporary = Path(temporary_name)
        repo = temporary / "repo"
        repo.mkdir()
        fixture_repo(repo, run_id, git_head, source_sha)
        output = temporary / "output"
        output.mkdir()
        first_archive = output / "first.tar.gz"
        first_manifest = output / "first.json"
        second_archive = output / "second.tar.gz"
        second_manifest = output / "second.json"
        build_capsule(repo, run_id, first_archive, first_manifest)
        build_capsule(repo, run_id, second_archive, second_manifest)
        require(first_archive.read_bytes() == second_archive.read_bytes(), "E_SELF_TEST", "capsule archive is not deterministic")
        require(first_manifest.read_bytes() == second_manifest.read_bytes(), "E_SELF_TEST", "capsule manifest is not deterministic")
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
        require((destination / f"target/v934-step4-coverage/runs/{run_id}/report/evidence.bin").read_bytes() == b"diagnostic-evidence\n", "E_SELF_TEST", "materialized fixture differs")
        cases.append({"name": "materialize", "code": "passed", "status": "passed"})
        tampered_archive = output / "tampered.tar.gz"
        payload = bytearray(first_archive.read_bytes())
        payload[-9] ^= 1
        tampered_archive.write_bytes(payload)
        expect_failure(cases, "archive-tamper", "E_ARCHIVE_BINDING", lambda: verify_capsule(tampered_archive, first_manifest))
        concatenated_archive = output / "concatenated.tar.gz"
        concatenated_archive.write_bytes(first_archive.read_bytes() * 2)
        concatenated_manifest = output / "concatenated.json"
        concatenated_value = load_manifest(first_manifest)
        concatenated_sha, concatenated_size = sha256_file(
            concatenated_archive,
            "concatenated negative archive",
            MAX_ARCHIVE_BYTES,
        )
        concatenated_value["archive"] = {
            "sha256": concatenated_sha,
            "size": concatenated_size,
        }
        concatenated_manifest.write_bytes(canonical_json(concatenated_value))
        expect_failure(
            cases,
            "gzip-concatenation",
            "E_GZIP_FRAMING",
            lambda: verify_capsule(concatenated_archive, concatenated_manifest),
        )
        bad_manifest = output / "bad.json"
        value = load_manifest(first_manifest)
        value["entries"][0]["path"] = "../escape"
        bad_manifest.write_bytes(canonical_json(value))
        expect_failure(cases, "path-traversal", "E_MANIFEST", lambda: verify_capsule(first_archive, bad_manifest))
        nonempty = temporary / "nonempty"
        nonempty.mkdir()
        (nonempty / "keep").write_text("keep", encoding="utf-8")
        expect_failure(cases, "nonempty-destination", "E_DESTINATION", lambda: materialize_capsule(first_archive, first_manifest, nonempty))
        link_repo = temporary / "link-repo"
        shutil.copytree(repo, link_repo)
        link_target = link_repo / "module-00/target/classes/example/Fixture.class"
        link_target.unlink()
        link_target.symlink_to(repo / "module-00/target/classes/example/Fixture.class")
        expect_failure(cases, "source-symlink", "E_SYMLINK", lambda: build_capsule(link_repo, run_id, output / "link.tar.gz", output / "link.json"))
    return {
        "schema_version": 1,
        "kind": "v934-step4-frozen-diagnostic-capsule-self-test",
        "case_count": len(cases),
        "cases": cases,
        "status": "passed",
    }


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(description=__doc__)
    commands = root.add_subparsers(dest="command", required=True)
    build = commands.add_parser("build")
    build.add_argument("--repo-root", type=Path, required=True)
    build.add_argument("--run-id", required=True)
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
    commands.add_parser("negative")
    return root


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "build":
            result = build_capsule(args.repo_root, args.run_id, args.archive, args.manifest)
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
        print(json.dumps(result, ensure_ascii=True, sort_keys=True))
        return 0
    except CapsuleError as error:
        print(f"[v934-frozen-diagnostic-capsule] {error.code}: {error}", file=os.sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
