#!/usr/bin/env python3
"""Run the frozen Step 3 DB reporter with a portable Step 4 source identity.

The Step 3 contract hashes repository files exactly as they appear in the
worktree.  That is intentionally immutable, but it makes the successor
contract depend on the checkout's EOL projection.  This adapter keeps every
Step 3 evidence and contract check intact while deriving the authority and
protected-tree identities from canonical ``HEAD`` blobs.  Before a blob is
trusted, the corresponding worktree file must be byte-identical to it or its
strict UTF-8 LF/CRLF projection; content drift, untracked files, symlinks and
special files remain fail-closed.
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
from types import ModuleType
from typing import Sequence


ROOT = Path(__file__).resolve().parents[4]
TOOL = ROOT / "scripts/v934/step3/database_matrix_report_tool.py"
CONTRACT = Path(__file__).with_name("database-matrix-contract.json")
CONTRACT_RELATIVE = "scripts/v934/step4/successor/database-matrix-contract.json"
AUTHORITY_MANIFEST = Path(__file__).with_name("database-authority-SHA256SUMS")
AUTHORITY_MANIFEST_RELATIVE = "scripts/v934/step4/successor/database-authority-SHA256SUMS"
EXPECTED_AUTHORITY_MANIFEST_SHA256 = (
    "495f2f549334d76faa06258925e3ad184dfb85572835134200d5316682dfd756"
)
GIT_OBJECT_PATTERN = re.compile(r"[0-9a-f]{40,64}")
GIT_MODE_PATTERN = re.compile(r"100(?:644|755)")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")


def _git_environment() -> dict[str, str]:
    """Drop every ambient Git redirect and retain fixed safety controls."""

    environment = {
        name: value for name, value in os.environ.items() if not name.startswith("GIT_")
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


def _git(repo: Path, *arguments: str) -> bytes:
    completed = subprocess.run(
        [
            "git",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-c",
            "core.hooksPath=/dev/null",
            "-C",
            str(repo),
            *arguments,
        ],
        env=_git_environment(),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"git {' '.join(arguments)} failed: {detail}")
    return completed.stdout


def _safe_relative(value: str) -> Path:
    relative = Path(value)
    if (
        not value
        or "\\" in value
        or relative.is_absolute()
        or ".." in relative.parts
        or relative.as_posix() != value
    ):
        raise ValueError(f"unsafe repository path: {value!r}")
    return relative


def _require_repository(repo: Path) -> tuple[Path, str]:
    resolved = repo.resolve()
    try:
        top_level = _git(resolved, "rev-parse", "--show-toplevel").decode("utf-8").strip()
        head = _git(resolved, "rev-parse", "--verify", "HEAD^{commit}").decode("ascii").strip()
    except (RuntimeError, UnicodeDecodeError) as error:
        raise ValueError(f"cannot resolve repository identity: {error}") from error
    if Path(top_level).resolve() != resolved or GIT_OBJECT_PATTERN.fullmatch(head) is None:
        raise ValueError(f"Git identity is not anchored to repository root: {resolved}")
    return resolved, head


def _require_unchanged_head(repo: Path, expected: str) -> None:
    try:
        actual = _git(repo, "rev-parse", "--verify", "HEAD^{commit}").decode("ascii").strip()
    except (RuntimeError, UnicodeDecodeError) as error:
        raise ValueError(f"cannot re-resolve repository HEAD: {error}") from error
    if actual != expected:
        raise ValueError(f"repository HEAD changed during validation: {expected} -> {actual}")


def _head_blob_record(repo: Path, commit: str, relative: str) -> tuple[str, bytes]:
    if GIT_OBJECT_PATTERN.fullmatch(commit) is None:
        raise ValueError(f"invalid anchored commit identity: {commit!r}")
    payload = _git(repo, "ls-tree", "-z", commit, "--", relative)
    if not payload.endswith(b"\0") or payload.count(b"\0") != 1:
        raise ValueError(f"HEAD blob lookup is not unique: {relative}")
    try:
        metadata, raw_path = payload[:-1].split(b"\t", 1)
        mode, kind, object_id = metadata.decode("ascii").split(" ")
        decoded_path = raw_path.decode("utf-8")
    except (UnicodeDecodeError, ValueError) as error:
        raise ValueError(f"malformed HEAD blob record: {relative}") from error
    if (
        decoded_path != relative
        or kind != "blob"
        or GIT_MODE_PATTERN.fullmatch(mode) is None
        or GIT_OBJECT_PATTERN.fullmatch(object_id) is None
    ):
        raise ValueError(f"unsupported HEAD blob record: {relative}")
    return mode, _git(repo, "cat-file", "blob", object_id)


def _secure_regular_worktree_payload(repo: Path, relative: str) -> bytes:
    relative_path = _safe_relative(relative)
    directory_flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
    directory_flags |= getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    file_flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    directory_fd = -1
    file_fd = -1
    try:
        directory_fd = os.open(repo, directory_flags)
        for component in relative_path.parts[:-1]:
            next_fd = os.open(component, directory_flags, dir_fd=directory_fd)
            os.close(directory_fd)
            directory_fd = next_fd
        file_fd = os.open(relative_path.name, file_flags, dir_fd=directory_fd)
        before = os.fstat(file_fd)
        if not stat.S_ISREG(before.st_mode):
            raise ValueError(f"worktree path is not a regular file: {relative}")
        chunks: list[bytes] = []
        remaining = before.st_size
        while remaining:
            chunk = os.read(file_fd, min(1024 * 1024, remaining))
            if not chunk:
                raise ValueError(f"short read from worktree file: {relative}")
            chunks.append(chunk)
            remaining -= len(chunk)
        if os.read(file_fd, 1) != b"":
            raise ValueError(f"worktree file grew during read: {relative}")
        after = os.fstat(file_fd)
        current = os.stat(relative_path.name, dir_fd=directory_fd, follow_symlinks=False)
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
        if identity_before != identity_after or (
            current.st_dev,
            current.st_ino,
            current.st_size,
            current.st_mtime_ns,
            current.st_ctime_ns,
        ) != identity_after:
            raise ValueError(f"worktree file changed during read: {relative}")
        lexical = repo / relative_path
        final = os.lstat(lexical)
        if stat.S_ISLNK(final.st_mode) or (
            final.st_dev,
            final.st_ino,
            final.st_size,
            final.st_mtime_ns,
            final.st_ctime_ns,
        ) != identity_after:
            raise ValueError(f"worktree path identity changed during read: {relative}")
        return b"".join(chunks)
    except OSError as error:
        raise ValueError(f"cannot read worktree file: {relative}: {error}") from error
    finally:
        if file_fd >= 0:
            os.close(file_fd)
        if directory_fd >= 0:
            os.close(directory_fd)


def _canonical_blob_at(repo: Path, commit: str, relative: str) -> bytes:
    """Return one anchored blob after proving exact worktree EOL equivalence."""

    _safe_relative(relative)
    _, blob = _head_blob_record(repo, commit, relative)
    worktree = _secure_regular_worktree_payload(repo, relative)
    if worktree == blob:
        return blob
    if b"\0" in worktree or b"\0" in blob:
        raise ValueError(f"binary worktree content differs from HEAD: {relative}")
    try:
        worktree.decode("utf-8", errors="strict")
        blob.decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        raise ValueError(f"non-UTF-8 worktree content differs from HEAD: {relative}") from error
    normalized = worktree.replace(b"\r\n", b"\n")
    if b"\r" in normalized or normalized != blob:
        raise ValueError(f"worktree content differs from HEAD beyond EOL projection: {relative}")
    return blob


def _canonical_blob(repo: Path, relative: str) -> bytes:
    """Public single-file helper used by focused portability self-tests."""

    repo, commit = _require_repository(repo)
    blob = _canonical_blob_at(repo, commit, relative)
    _require_unchanged_head(repo, commit)
    return blob


def _head_tree_files(repo: Path, commit: str, tree: str) -> list[str]:
    _safe_relative(tree)
    payload = _git(repo, "ls-tree", "-r", "-z", commit, "--", tree)
    records: list[str] = []
    prefix = f"{tree}/"
    for raw_record in filter(None, payload.split(b"\0")):
        try:
            metadata, raw_path = raw_record.split(b"\t", 1)
            mode, kind, object_id = metadata.decode("ascii").split(" ")
            relative = raw_path.decode("utf-8")
        except (UnicodeDecodeError, ValueError) as error:
            raise ValueError(f"malformed HEAD tree record: {tree}") from error
        if (
            not relative.startswith(prefix)
            or kind != "blob"
            or GIT_MODE_PATTERN.fullmatch(mode) is None
            or GIT_OBJECT_PATTERN.fullmatch(object_id) is None
            or relative in records
        ):
            raise ValueError(f"unsupported HEAD tree record: {relative!r}")
        records.append(relative)
    if not records:
        raise ValueError(f"protected HEAD tree is empty: {tree}")
    return sorted(records)


def _worktree_tree_files(repo: Path, tree: str) -> list[str]:
    relative_tree = _safe_relative(tree)
    lexical = repo / relative_tree
    current = repo
    for component in relative_tree.parts:
        current /= component
        if current.is_symlink():
            raise ValueError(f"protected tree contains a symlink component: {tree}")
    if not lexical.is_dir() or not lexical.resolve().is_relative_to(repo):
        raise ValueError(f"protected tree is not a repository directory: {tree}")
    files: list[str] = []
    for candidate in lexical.rglob("*"):
        relative = candidate.relative_to(repo).as_posix()
        if candidate.is_symlink():
            raise ValueError(f"protected tree contains a symlink: {relative}")
        try:
            mode = candidate.stat().st_mode
        except OSError as error:
            raise ValueError(f"cannot stat protected tree entry: {relative}: {error}") from error
        if stat.S_ISREG(mode):
            files.append(relative)
        elif not stat.S_ISDIR(mode):
            raise ValueError(f"protected tree contains a special file: {relative}")
    return sorted(files)


def _canonical_tree_sha256_at(repo: Path, commit: str, tree: str) -> str:
    head_files = _head_tree_files(repo, commit, tree)
    worktree_files = _worktree_tree_files(repo, tree)
    if worktree_files != head_files:
        missing = sorted(set(head_files) - set(worktree_files))
        extra = sorted(set(worktree_files) - set(head_files))
        raise ValueError(
            f"protected tree file set differs from HEAD: {tree}; "
            f"missing={missing[:3]} extra={extra[:3]}"
        )
    digest = hashlib.sha256()
    prefix = f"{tree}/"
    for relative in head_files:
        tree_relative = relative[len(prefix) :]
        blob = _canonical_blob_at(repo, commit, relative)
        for value in (tree_relative.encode("utf-8"), blob):
            digest.update(len(value).to_bytes(8, "big"))
            digest.update(value)
    if _worktree_tree_files(repo, tree) != worktree_files:
        raise ValueError(f"protected tree file set changed during validation: {tree}")
    return digest.hexdigest()


def _canonical_tree_sha256(repo: Path, tree: str) -> str:
    """Public single-tree helper used by focused portability self-tests."""

    repo, commit = _require_repository(repo)
    digest = _canonical_tree_sha256_at(repo, commit, tree)
    _require_unchanged_head(repo, commit)
    return digest


def _authority_manifest_entries(
    matrix: ModuleType,
    repo: Path,
    commit: str,
    path: Path,
    expected_manifest_sha256: str,
) -> tuple[dict[str, str], str]:
    expected_path = repo / AUTHORITY_MANIFEST_RELATIVE
    if path.absolute() != expected_path or path.is_symlink():
        raise ValueError(f"authority manifest path differs: {path}")
    payload = _secure_regular_worktree_payload(repo, AUTHORITY_MANIFEST_RELATIVE)
    manifest_sha256 = hashlib.sha256(payload).hexdigest()
    if manifest_sha256 != expected_manifest_sha256:
        raise ValueError(
            "authority manifest digest differs: "
            f"expected={expected_manifest_sha256} actual={manifest_sha256}"
        )
    try:
        text = payload.decode("ascii")
    except UnicodeDecodeError as error:
        raise ValueError(f"authority manifest is not ASCII: {error}") from error
    if not text or not text.endswith("\n") or "\r" in text:
        raise ValueError("authority manifest must use non-empty newline-terminated LF rows")
    entries: dict[str, str] = {}
    for line in text.splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._/-]+)", line)
        if match is None:
            raise ValueError(f"invalid authority hash entry: {line!r}")
        expected, relative = match.groups()
        if relative in entries:
            raise ValueError(f"duplicate authority path: {relative}")
        actual = hashlib.sha256(_canonical_blob_at(repo, commit, relative)).hexdigest()
        if actual != expected:
            raise ValueError(f"stale authority artifact: {relative}")
        entries[relative] = expected
    if list(entries) != matrix.EXACT_AUTHORITY_FILES:
        raise ValueError("authority artifact set or order differs")
    return entries, manifest_sha256


def _strict_json_object(payload: bytes, label: str) -> dict[str, object]:
    def unique(pairs: list[tuple[str, object]]) -> dict[str, object]:
        result: dict[str, object] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"{label} contains duplicate key: {key}")
            result[key] = value
        return result

    try:
        value = json.loads(
            payload.decode("utf-8"),
            object_pairs_hook=unique,
            parse_constant=lambda token: (_ for _ in ()).throw(
                ValueError(f"{label} contains non-finite number: {token}")
            ),
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot parse {label}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{label} root is not an object")
    return value


def install_portable_git_identity(matrix: ModuleType) -> None:
    """Install only the three successor source-identity validators."""

    def reject(code: str, message: str, error: Exception | None = None) -> None:
        detail = f"{message}: {error}" if error is not None else message
        matrix.fail(code, detail)

    def validate_authority_hash_manifest(repo: Path, path: Path) -> dict[str, str]:
        try:
            repo, commit = _require_repository(repo)
            entries, _ = _authority_manifest_entries(
                matrix,
                repo,
                commit,
                path,
                EXPECTED_AUTHORITY_MANIFEST_SHA256,
            )
            _require_unchanged_head(repo, commit)
        except (RuntimeError, ValueError) as error:
            reject("E_AUTHORITY_MANIFEST", "invalid authority manifest", error)
        return entries

    def validate_protected_tree_manifest(repo: Path, path: Path) -> list[dict[str, str]]:
        try:
            repo, commit = _require_repository(repo)
        except ValueError as error:
            reject("E_PROTECTED_TREE", "invalid repository identity", error)
        rows = matrix.read_tsv(path, matrix.PROTECTED_TREE_HEADER)
        if [row["path"] for row in rows] != matrix.EXACT_PROTECTED_TREES:
            reject("E_PROTECTED_TREE", "protected tree set or order differs")
        for row in rows:
            try:
                relative = matrix.safe_relative_path(row["path"], "protected tree")
                actual = _canonical_tree_sha256_at(repo, commit, relative.as_posix())
            except (RuntimeError, ValueError) as error:
                reject("E_PROTECTED_TREE", f"invalid protected tree: {row['path']}", error)
            if row["tree_sha256"] != actual:
                reject("E_PROTECTED_TREE", f"protected tree hash differs: {row['path']}")
        try:
            _require_unchanged_head(repo, commit)
        except (RuntimeError, ValueError) as error:
            reject("E_PROTECTED_TREE", "repository HEAD changed", error)
        return rows

    def combined_protected_source_hash(
        repo: Path, rows: Sequence[dict[str, str]]
    ) -> str:
        try:
            repo, commit = _require_repository(repo)
        except ValueError as error:
            reject("E_PROTECTED_TREE", "invalid repository identity", error)
        digest = hashlib.sha256()
        for row in rows:
            relative = row["path"]
            try:
                actual = _canonical_tree_sha256_at(repo, commit, relative)
            except (RuntimeError, ValueError) as error:
                reject("E_PROTECTED_TREE", f"invalid protected tree: {relative}", error)
            for value in (relative.encode("utf-8"), actual.encode("ascii")):
                digest.update(len(value).to_bytes(8, "big"))
                digest.update(value)
        try:
            _require_unchanged_head(repo, commit)
        except (RuntimeError, ValueError) as error:
            reject("E_PROTECTED_TREE", "repository HEAD changed", error)
        return digest.hexdigest()

    matrix._validate_authority_hash_manifest = validate_authority_hash_manifest
    matrix._validate_protected_tree_manifest = validate_protected_tree_manifest
    matrix.combined_protected_source_hash = combined_protected_source_hash


def _load_frozen_tool() -> ModuleType:
    spec = importlib.util.spec_from_file_location(
        "v934_step3_database_matrix_report_tool_successor_runtime",
        TOOL,
    )
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load frozen database matrix tool: {TOOL}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def _validate_authority_command(matrix: ModuleType, arguments: list[str]) -> int:
    if (
        len(arguments) != 7
        or arguments[0] != "validate-authority"
        or arguments[1] != "--expected-git-head"
        or arguments[3] != "--expected-contract-sha256"
        or arguments[5] != "--expected-manifest-sha256"
    ):
        print(
            f"{matrix.PREFIX} E_ARGUMENT: validate-authority requires exact expected "
            "Git HEAD, contract SHA-256, and manifest SHA-256 bindings",
            file=sys.stderr,
        )
        return 2
    expected_head, expected_contract, expected_manifest = (
        arguments[2],
        arguments[4],
        arguments[6],
    )
    try:
        if (
            GIT_OBJECT_PATTERN.fullmatch(expected_head) is None
            or SHA256_PATTERN.fullmatch(expected_contract) is None
            or SHA256_PATTERN.fullmatch(expected_manifest) is None
            or expected_manifest != EXPECTED_AUTHORITY_MANIFEST_SHA256
        ):
            raise ValueError("expected authority bindings are malformed or differ")
        repo, commit = _require_repository(ROOT)
        if commit != expected_head:
            raise ValueError(f"repository HEAD differs: expected={expected_head} actual={commit}")
        contract_payload = _secure_regular_worktree_payload(repo, CONTRACT_RELATIVE)
        contract_sha256 = hashlib.sha256(contract_payload).hexdigest()
        if contract_sha256 != expected_contract:
            raise ValueError(
                "database contract digest differs: "
                f"expected={expected_contract} actual={contract_sha256}"
            )
        contract = _strict_json_object(contract_payload, "database matrix contract")
        bindings = contract.get("bindings")
        if not isinstance(bindings, dict) or bindings.get("authority_hash_manifest") != {
            "path": AUTHORITY_MANIFEST_RELATIVE,
            "sha256": expected_manifest,
        }:
            raise ValueError("database contract authority-manifest binding differs")
        entries, manifest_sha256 = _authority_manifest_entries(
            matrix,
            repo,
            commit,
            AUTHORITY_MANIFEST,
            expected_manifest,
        )
        _require_unchanged_head(repo, commit)
    except (RuntimeError, ValueError) as error:
        print(f"{matrix.PREFIX} E_AUTHORITY_MANIFEST: {error}", file=sys.stderr)
        return 2
    print(
        json.dumps(
            {
                "authority_manifest_sha256": manifest_sha256,
                "contract_sha256": contract_sha256,
                "git_head": commit,
                "paths": len(entries),
                "status": "passed",
            },
            sort_keys=True,
        )
    )
    return 0


def main() -> int:
    matrix = _load_frozen_tool()
    install_portable_git_identity(matrix)
    if sys.argv[1:2] == ["validate-authority"]:
        return _validate_authority_command(matrix, sys.argv[1:])
    return int(
        matrix.main(
            [
                "--repo-root",
                str(ROOT),
                "--contract",
                str(CONTRACT),
                *sys.argv[1:],
            ]
        )
    )


if __name__ == "__main__":
    raise SystemExit(main())
