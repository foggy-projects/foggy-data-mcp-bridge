#!/usr/bin/env python3
"""Build and verify portable, deterministic v9.3.4 release evidence bundles."""

from __future__ import annotations

import argparse
import copy
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import unicodedata
import warnings
import zipfile
import zlib
from dataclasses import dataclass
from typing import BinaryIO, Callable, Iterable


TOOL_DIR = Path(__file__).resolve().parent
CONTRACT_PATH = TOOL_DIR / "release-artifact-contract.json"
HEX64 = re.compile(r"[0-9a-f]{64}")
CONTROL = re.compile(r"[\x00-\x1f\x7f]")
TEXT_EXTENSIONS = (
    "bash",
    "bat",
    "cfg",
    "cmd",
    "conf",
    "config",
    "csv",
    "css",
    "env",
    "fsscript",
    "gql",
    "gradle",
    "graphql",
    "hocon",
    "htm",
    "html",
    "http",
    "ini",
    "java",
    "js",
    "json",
    "kts",
    "log",
    "markdown",
    "md",
    "pom",
    "properties",
    "property",
    "ps1",
    "py",
    "qm",
    "rb",
    "sh",
    "sql",
    "svelte",
    "tm",
    "toml",
    "ts",
    "tsv",
    "tsx",
    "txt",
    "vue",
    "xml",
    "yaml",
    "yml",
    "zsh",
)
ARCHIVE_EXTENSIONS = ("jar", "zip")
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
ALLOWED_ZIP_METHODS = {
    zipfile.ZIP_STORED: "stored",
    zipfile.ZIP_DEFLATED: "deflated",
}


class ArtifactError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def reject(code: str, message: str) -> None:
    raise ArtifactError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def canonical_json(value: object) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        + "\n"
    ).encode("utf-8")


def unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            reject("E_JSON_DUPLICATE", f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_json_bytes(data: bytes, label: str) -> dict[str, object]:
    try:
        value = json.loads(
            data.decode("utf-8"),
            object_pairs_hook=unique_object,
            parse_constant=lambda value: reject(
                "E_JSON_TYPE", f"{label} contains non-finite number: {value}"
            ),
        )
    except ArtifactError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        reject("E_JSON", f"cannot parse {label}: {error}")
    require(type(value) is dict, "E_JSON_TYPE", f"{label} must be an object")
    return value


def exact_keys(value: dict[str, object], expected: Iterable[str], code: str, label: str) -> None:
    expected_set = set(expected)
    actual_set = set(value)
    require(
        actual_set == expected_set,
        code,
        f"{label} keys differ: missing={sorted(expected_set - actual_set)} "
        f"extra={sorted(actual_set - expected_set)}",
    )


def secure_regular_snapshot(
    path: Path, label: str, maximum: int | None = None
) -> tuple[bytes, os.stat_result]:
    absolute = Path(os.path.abspath(path))
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except FileNotFoundError:
        reject("E_FILE_MISSING", f"missing {label}: {absolute}")
    except OSError as error:
        reject("E_FILE_OPEN", f"cannot open {label}: {absolute}: {error}")
    try:
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not a regular file")
        if maximum is not None:
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
        current = os.lstat(absolute)
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
            (
                current.st_dev,
                current.st_ino,
                current.st_size,
                current.st_mtime_ns,
                current.st_ctime_ns,
            )
            == (
                after.st_dev,
                after.st_ino,
                after.st_size,
                after.st_mtime_ns,
                after.st_ctime_ns,
            ),
            "E_FILE_RACE",
            f"{label} path identity changed while read",
        )
        return b"".join(chunks), before
    finally:
        os.close(descriptor)


def secure_regular_bytes(path: Path, label: str, maximum: int | None = None) -> bytes:
    return secure_regular_snapshot(path, label, maximum)[0]


def file_digest(path: Path, label: str) -> tuple[str, int]:
    digest = hashlib.sha256()
    absolute = Path(os.path.abspath(path))
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(absolute, flags)
    except FileNotFoundError:
        reject("E_ARCHIVE_MISSING", f"missing {label}: {absolute}")
    except OSError as error:
        reject("E_FILE_OPEN", f"cannot open {label}: {absolute}: {error}")
    try:
        before = os.fstat(descriptor)
        require(stat.S_ISREG(before.st_mode), "E_SPECIAL", f"{label} is not a regular file")
        with os.fdopen(os.dup(descriptor), "rb") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                digest.update(chunk)
        after = os.fstat(descriptor)
        require(
            (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns)
            == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns),
            "E_FILE_RACE",
            f"{label} changed while hashed",
        )
        return digest.hexdigest(), before.st_size
    finally:
        os.close(descriptor)


def real_directory(path: Path, label: str) -> Path:
    absolute = Path(os.path.abspath(path))
    try:
        current = os.lstat(absolute)
    except FileNotFoundError:
        reject("E_DIRECTORY", f"missing {label}: {absolute}")
    require(stat.S_ISDIR(current.st_mode), "E_DIRECTORY", f"{label} is not a real directory")
    require(not stat.S_ISLNK(current.st_mode), "E_SYMLINK", f"{label} is symlinked")
    require(absolute.resolve(strict=True) == absolute, "E_SYMLINK", f"{label} has symlinked components")
    return absolute


def prepare_output_directory(path: Path) -> Path:
    absolute = Path(os.path.abspath(path))
    if not absolute.exists():
        parent = real_directory(absolute.parent, "output parent")
        require(parent == absolute.parent, "E_DIRECTORY", "output parent differs")
        try:
            absolute.mkdir(mode=0o755)
        except FileExistsError:
            pass
    return real_directory(absolute, "output directory")


def normalize_relative_path(raw: str, label: str) -> str:
    require(type(raw) is str and bool(raw), "E_PATH", f"{label} must be a non-empty string")
    require(raw == unicodedata.normalize("NFC", raw), "E_PATH", f"{label} is not NFC-normalized")
    require("\\" not in raw, "E_PATH", f"{label} contains a backslash")
    require(CONTROL.search(raw) is None, "E_PATH", f"{label} contains a control character")
    pure = PurePosixPath(raw)
    require(not pure.is_absolute(), "E_PATH", f"{label} is absolute")
    parts = raw.split("/")
    require(
        all(part not in ("", ".", "..") for part in parts),
        "E_PATH",
        f"{label} contains an empty/current/parent component",
    )
    require(pure.as_posix() == raw, "E_PATH", f"{label} is not canonical")
    return raw


def normalized_mode(st_mode: int, contract: dict[str, object], is_directory: bool) -> int:
    archive = contract["archive"]
    assert isinstance(archive, dict)
    if is_directory:
        return int(str(archive["directory_mode"]), 8)
    if st_mode & 0o111:
        return int(str(archive["executable_mode"]), 8)
    return int(str(archive["file_mode"]), 8)


@dataclass(frozen=True)
class Entry:
    path: str
    kind: str
    mode: int
    size: int = 0
    sha256: str = ""
    mtime_ns: int | None = None
    source: Path | None = None

    def manifest_value(self) -> dict[str, object]:
        value: dict[str, object] = {
            "mode": f"{self.mode:04o}",
            "path": self.path,
            "type": self.kind,
        }
        if self.kind == "file":
            require(
                type(self.mtime_ns) is int,
                "E_FILE_MTIME",
                f"file mtime binding is absent: {self.path}",
            )
            value["mtime_ns"] = self.mtime_ns
            value["sha256"] = self.sha256
            value["size"] = self.size
        return value


def path_sort(value: str) -> bytes:
    return value.encode("utf-8")


def register_path(path: str, seen: dict[str, str]) -> None:
    folded = path.casefold()
    previous = seen.get(folded)
    require(previous is None or previous == path, "E_PATH_COLLISION", f"path aliases collide: {previous!r}, {path!r}")
    seen[folded] = path


def is_generated_metadata(path: str, contract: dict[str, object]) -> bool:
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    metadata = str(manifests["metadata_directory"])
    return path == metadata or path.startswith(metadata + "/")


def payload_extension(path: str) -> str:
    leaf = path.rsplit("!/", 1)[-1]
    name = PurePosixPath(leaf).name
    extension = PurePosixPath(leaf).suffix.lower().removeprefix(".")
    if not extension and name.startswith("."):
        extension = name[1:].lower()
    return extension


def sensitive_policy(contract: dict[str, object]) -> dict[str, object]:
    policy = contract["sensitive_text_policy"]
    assert isinstance(policy, dict)
    return policy


def archive_limits(contract: dict[str, object]) -> dict[str, object]:
    limits = sensitive_policy(contract)["archive_limits"]
    assert isinstance(limits, dict)
    return limits


def normalization_limits(contract: dict[str, object]) -> dict[str, object]:
    limits = sensitive_policy(contract)["normalization_limits"]
    assert isinstance(limits, dict)
    return limits


def maximum_payload_file_bytes(contract: dict[str, object]) -> int:
    return int(archive_limits(contract)["max_payload_file_bytes"])


def declared_text_path(path: str, contract: dict[str, object]) -> bool:
    extensions = sensitive_policy(contract)["extensions"]
    assert isinstance(extensions, list)
    return payload_extension(path) in extensions


def declared_archive_path(path: str, contract: dict[str, object]) -> bool:
    extensions = sensitive_policy(contract)["archive_extensions"]
    assert isinstance(extensions, list)
    return payload_extension(path) in extensions


ENVIRONMENT_NAME = re.compile(rb"[A-Za-z_][A-Za-z0-9_.-]*")
CREDENTIAL_NAME = re.compile(
    rb"(?i)(?:username|password|passwd|pwd|credential|api[-_]?key|access[-_]?key|"
    rb"secret[-_]?access[-_]?key|access[-_]?key[-_]?(?:id|secret)|secret[-_]?id|"
    rb"secret[-_]?key|private[-_]?key|access[-_]?token|refresh[-_]?token|"
    rb"auth[-_]?token|client[-_]?token|client[-_]?secret|secret|authorization|"
    rb"MYSQL_PWD|SQLCMDPASSWORD|"
    rb"REDIS_PASSWORD|REDIS_USERNAME|REDIS_URI|MONGO(?:DB)?_(?:URI|PASSWORD|USERNAME)|"
    rb"MYSQL_(?:PASSWORD|ROOT_PASSWORD)|MINIO_ROOT_(?:USER|PASSWORD)|"
    rb"AWS_(?:ACCESS_KEY_ID|SECRET_ACCESS_KEY))"
)
SAFE_PLACEHOLDER_DEFAULT = re.compile(
    rb"(?i)(?:null|redacted|<redacted>|\[redacted\]|\.{3}|x{3,}|sk-x{2,}|\*{3,}|"
    rb"your[-_ ]?(?:api[-_ ]?key|token|secret|password)|"
    rb"(?:fixture|default)(?:[-_][A-Za-z0-9]+)+)"
)
QUOTED_CREDENTIAL_ASSIGNMENT = re.compile(
    rb"(?im)(?:^[ \t]*(?:-[ \t]+)?(?:[A-Za-z0-9_-]+\.)*|[,{]\s*)\"?"
    rb"(?:username|password|passwd|pwd|credential|api[-_]?key|access[-_]?key|"
    rb"secret[-_]?access[-_]?key|access[-_]?key[-_]?(?:id|secret)|secret[-_]?id|"
    rb"secret[-_]?key|private[-_]?key|access[-_]?token|refresh[-_]?token|"
    rb"auth[-_]?token|client[-_]?token|client[-_]?secret|secret|authorization)"
    rb"\"?\s*[:=]\s*(?:\"(?P<double>[^\"\r\n]*)\"|'(?P<single>[^'\r\n]*)')"
)
CREDENTIAL_KEY = re.compile(
    rb"(?i)(?:(?:[A-Za-z0-9_-]+)\.)*(?:username|password|passwd|pwd|credential|"
    rb"api[-_]?key|access[-_]?key|secret[-_]?access[-_]?key|"
    rb"access[-_]?key[-_]?(?:id|secret)|secret[-_]?id|secret[-_]?key|private[-_]?key|"
    rb"access[-_]?token|refresh[-_]?token|auth[-_]?token|client[-_]?token|"
    rb"client[-_]?secret|secret|authorization)"
)


@dataclass
class CredentialScanBudget:
    escape_sequences: int = 0
    key_tokens: int = 0
    placeholder_tokens: int = 0

    def key(self, path: str, size: int, contract: dict[str, object]) -> None:
        limits = normalization_limits(contract)
        self.key_tokens += 1
        require(
            self.key_tokens <= int(limits["max_key_tokens_per_payload"]),
            "E_SECRET_NORMALIZATION_LIMIT",
            f"credential key token budget exceeded: {path}",
        )
        require(
            size <= int(limits["max_key_token_bytes"]),
            "E_SECRET_NORMALIZATION_LIMIT",
            f"credential key token exceeds size limit: {path}",
        )

    def escape(self, path: str, contract: dict[str, object]) -> None:
        self.escape_sequences += 1
        require(
            self.escape_sequences
            <= int(normalization_limits(contract)["max_escape_sequences_per_payload"]),
            "E_SECRET_NORMALIZATION_LIMIT",
            f"credential escape budget exceeded: {path}",
        )

    def placeholder(self, path: str, contract: dict[str, object]) -> None:
        self.placeholder_tokens += 1
        require(
            self.placeholder_tokens
            <= int(normalization_limits(contract)["max_placeholder_tokens_per_payload"]),
            "E_SECRET_NORMALIZATION_LIMIT",
            f"environment placeholder budget exceeded: {path}",
        )


def canonical_credential_key(value: str) -> bytes | None:
    try:
        encoded = value.encode("ascii")
    except UnicodeEncodeError:
        return None
    return encoded if CREDENTIAL_KEY.fullmatch(encoded) is not None else None


def decoded_scalar(
    raw: bytes,
    cursor: int,
    digits: int,
    path: str,
    syntax: str,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> tuple[int, int]:
    budget.escape(path, contract)
    end = cursor + digits
    require(
        end <= len(raw),
        "E_SECRET_NORMALIZATION",
        f"truncated {syntax} Unicode escape in credential key: {path}",
    )
    token = raw[cursor:end]
    require(
        re.fullmatch(rb"[0-9A-Fa-f]+", token) is not None,
        "E_SECRET_NORMALIZATION",
        f"malformed {syntax} Unicode escape in credential key: {path}",
    )
    return end, int(token, 16)


def decode_quoted_key(
    raw: bytes,
    syntax: str,
    path: str,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> str:
    budget.key(path, len(raw), contract)
    output = bytearray()
    cursor = 0
    simple_json = {
        ord('"'): b'"',
        ord('\\'): b'\\',
        ord('/'): b'/',
        ord('b'): b'\b',
        ord('f'): b'\f',
        ord('n'): b'\n',
        ord('r'): b'\r',
        ord('t'): b'\t',
    }
    simple_yaml = {
        **simple_json,
        ord('0'): b'\x00',
        ord('a'): b'\x07',
        ord('v'): b'\x0b',
        ord('e'): b'\x1b',
        ord(' '): b' ',
        ord('N'): chr(0x85).encode("utf-8"),
        ord('_'): chr(0xA0).encode("utf-8"),
        ord('L'): chr(0x2028).encode("utf-8"),
        ord('P'): chr(0x2029).encode("utf-8"),
    }
    simple = simple_json if syntax == "JSON" else simple_yaml
    while cursor < len(raw):
        value = raw[cursor]
        if value != ord('\\'):
            require(
                not (syntax == "JSON" and value < 0x20),
                "E_SECRET_NORMALIZATION",
                f"control byte in {syntax} credential key: {path}",
            )
            output.append(value)
            cursor += 1
            continue
        require(
            cursor + 1 < len(raw),
            "E_SECRET_NORMALIZATION",
            f"truncated {syntax} escape in credential key: {path}",
        )
        marker = raw[cursor + 1]
        if marker in simple:
            budget.escape(path, contract)
            output.extend(simple[marker])
            cursor += 2
            continue
        if syntax == "YAML" and marker == ord('x'):
            cursor, scalar = decoded_scalar(
                raw, cursor + 2, 2, path, syntax, budget, contract
            )
        elif marker == ord('u'):
            cursor, scalar = decoded_scalar(
                raw, cursor + 2, 4, path, syntax, budget, contract
            )
            if syntax == "JSON" and 0xD800 <= scalar <= 0xDBFF:
                require(
                    raw[cursor : cursor + 2] == b"\\u",
                    "E_SECRET_NORMALIZATION",
                    f"unpaired JSON surrogate in credential key: {path}",
                )
                cursor, low = decoded_scalar(
                    raw, cursor + 2, 4, path, syntax, budget, contract
                )
                require(
                    0xDC00 <= low <= 0xDFFF,
                    "E_SECRET_NORMALIZATION",
                    f"unpaired JSON surrogate in credential key: {path}",
                )
                scalar = 0x10000 + ((scalar - 0xD800) << 10) + low - 0xDC00
        elif syntax == "YAML" and marker == ord('U'):
            cursor, scalar = decoded_scalar(
                raw, cursor + 2, 8, path, syntax, budget, contract
            )
        else:
            reject(
                "E_SECRET_NORMALIZATION",
                f"unsupported {syntax} escape in credential key: {path}",
            )
        require(
            scalar <= 0x10FFFF and not 0xD800 <= scalar <= 0xDFFF,
            "E_SECRET_NORMALIZATION",
            f"invalid {syntax} Unicode scalar in credential key: {path}",
        )
        output.extend(chr(scalar).encode("utf-8"))
    try:
        return bytes(output).decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        reject(
            "E_SECRET_NORMALIZATION",
            f"non-UTF-8 {syntax} credential key in {path}: {error}",
        )


def replace_quoted_keys(
    path: str,
    data: bytes,
    syntax: str,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> bytes:
    replacements: list[tuple[int, int, bytes]] = []
    cursor = 0
    while cursor < len(data):
        start = data.find(b'"', cursor)
        if start < 0:
            break
        end = start + 1
        while end < len(data):
            if data[end] == ord('\\'):
                end += 2
                continue
            if data[end] == ord('"'):
                break
            end += 1
        require(
            end < len(data),
            "E_SECRET_NORMALIZATION",
            f"unterminated {syntax} quoted token: {path}",
        )
        after = end + 1
        while after < len(data) and data[after] in b" \t\r\n":
            after += 1
        if after < len(data) and data[after] == ord(':'):
            decoded = decode_quoted_key(
                data[start + 1 : end], syntax, path, budget, contract
            )
            credential = canonical_credential_key(decoded)
            if credential is not None:
                replacements.append((start + 1, end, credential))
        cursor = end + 1
    if not replacements:
        return data
    output = bytearray()
    cursor = 0
    for start, end, replacement in replacements:
        output.extend(data[cursor:start])
        output.extend(replacement)
        cursor = end
    output.extend(data[cursor:])
    require(
        len(output) <= int(normalization_limits(contract)["max_normalized_bytes"]),
        "E_SECRET_NORMALIZATION_LIMIT",
        f"normalized credential view exceeds size limit: {path}",
    )
    return bytes(output)


def decode_properties_key(
    raw: bytes,
    path: str,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> str:
    budget.key(path, len(raw), contract)
    output = bytearray()
    cursor = 0
    mapped = {ord('t'): b'\t', ord('n'): b'\n', ord('r'): b'\r', ord('f'): b'\f'}
    while cursor < len(raw):
        if raw[cursor] != ord('\\'):
            output.append(raw[cursor])
            cursor += 1
            continue
        require(
            cursor + 1 < len(raw),
            "E_SECRET_NORMALIZATION",
            f"truncated Java properties key escape: {path}",
        )
        marker = raw[cursor + 1]
        if marker == ord('u'):
            cursor, scalar = decoded_scalar(
                raw, cursor + 2, 4, path, "Java properties", budget, contract
            )
            require(
                not 0xD800 <= scalar <= 0xDFFF,
                "E_SECRET_NORMALIZATION",
                f"surrogate Java properties key escape: {path}",
            )
            output.extend(chr(scalar).encode("utf-8"))
        else:
            budget.escape(path, contract)
            output.extend(mapped.get(marker, bytes((marker,))))
            cursor += 2
    try:
        return bytes(output).decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        reject("E_SECRET_NORMALIZATION", f"non-UTF-8 Java properties key in {path}: {error}")


def normalize_properties_keys(
    path: str,
    data: bytes,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> bytes:
    synthetic: list[bytes] = []
    logical = bytearray()
    continuing = False
    physical_lines = data.splitlines()
    for line in physical_lines:
        part = line.lstrip(b" \t\f") if continuing else line
        logical.extend(part)
        require(
            len(logical) <= int(normalization_limits(contract)["max_logical_line_bytes"]),
            "E_SECRET_NORMALIZATION_LIMIT",
            f"Java properties logical line exceeds size limit: {path}",
        )
        backslashes = len(logical) - len(logical.rstrip(b"\\"))
        if backslashes % 2:
            logical.pop()
            continuing = True
            continue
        continuing = False
        row = bytes(logical)
        logical.clear()
        stripped = row.lstrip(b" \t\f")
        if not stripped or stripped[:1] in (b"#", b"!"):
            continue
        key_start = len(row) - len(stripped)
        cursor = key_start
        escaped = False
        while cursor < len(row):
            value = row[cursor]
            if escaped:
                escaped = False
                cursor += 1
                continue
            if value == ord('\\'):
                escaped = True
                cursor += 1
                continue
            if value in b"=: \t\f":
                break
            cursor += 1
        key_end = cursor
        value_start = cursor
        while value_start < len(row) and row[value_start] in b" \t\f":
            value_start += 1
        if value_start < len(row) and row[value_start] in b"=:":
            value_start += 1
        while value_start < len(row) and row[value_start] in b" \t\f":
            value_start += 1
        decoded = decode_properties_key(
            row[key_start:key_end], path, budget, contract
        )
        credential = canonical_credential_key(decoded)
        if credential is not None:
            synthetic.append(credential + b"=" + row[value_start:])
    require(
        not continuing,
        "E_SECRET_NORMALIZATION",
        f"unterminated Java properties continuation: {path}",
    )
    if not synthetic:
        return data
    result = data + b"\n" + b"\n".join(synthetic) + b"\n"
    require(
        len(result) <= int(normalization_limits(contract)["max_normalized_bytes"]),
        "E_SECRET_NORMALIZATION_LIMIT",
        f"normalized Java properties view exceeds size limit: {path}",
    )
    return result


def decode_xml_key(
    raw: bytes,
    path: str,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> str:
    budget.key(path, len(raw), contract)
    output = bytearray()
    cursor = 0
    named = {b"amp": b"&", b"lt": b"<", b"gt": b">", b"quot": b'"', b"apos": b"'"}
    while cursor < len(raw):
        if raw[cursor] != ord('&'):
            output.append(raw[cursor])
            cursor += 1
            continue
        end = raw.find(b";", cursor + 1)
        require(
            end >= 0 and end - cursor <= 32,
            "E_SECRET_NORMALIZATION",
            f"malformed XML entity in credential key: {path}",
        )
        budget.escape(path, contract)
        token = raw[cursor + 1 : end]
        if token.startswith((b"#x", b"#X")):
            digits = token[2:]
            base = 16
            valid = rb"[0-9A-Fa-f]+"
        elif token.startswith(b"#"):
            digits = token[1:]
            base = 10
            valid = rb"[0-9]+"
        else:
            require(
                token in named,
                "E_SECRET_NORMALIZATION",
                f"unsupported XML entity in credential key: {path}",
            )
            output.extend(named[token])
            cursor = end + 1
            continue
        require(
            bool(digits) and re.fullmatch(valid, digits) is not None,
            "E_SECRET_NORMALIZATION",
            f"malformed XML numeric entity in credential key: {path}",
        )
        scalar = int(digits, base)
        require(
            scalar <= 0x10FFFF
            and not 0xD800 <= scalar <= 0xDFFF
            and scalar not in (0xFFFE, 0xFFFF),
            "E_SECRET_NORMALIZATION",
            f"invalid XML numeric entity in credential key: {path}",
        )
        output.extend(chr(scalar).encode("utf-8"))
        cursor = end + 1
    try:
        return bytes(output).decode("utf-8", errors="strict")
    except UnicodeDecodeError as error:
        reject("E_SECRET_NORMALIZATION", f"non-UTF-8 XML credential key in {path}: {error}")


def normalize_xml_keys(
    path: str,
    data: bytes,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> bytes:
    synthetic: list[bytes] = []
    cursor = 0
    lower = data.lower()
    attribute = re.compile(rb"(?is)(?:^|\s)([A-Za-z_:][A-Za-z0-9_.:-]*)\s*=\s*(['\"])(.*?)\2")
    while True:
        start = lower.find(b"<property", cursor)
        if start < 0:
            break
        boundary = start + len(b"<property")
        if boundary < len(data) and data[boundary] not in b" \t\r\n/>":
            cursor = boundary
            continue
        end = boundary
        quote = 0
        while end < len(data):
            value = data[end]
            if quote:
                if value == quote:
                    quote = 0
            elif value in (ord('"'), ord("'")):
                quote = value
            elif value == ord('>'):
                break
            end += 1
            require(
                end - start
                <= int(normalization_limits(contract)["max_markup_token_bytes"]),
                "E_SECRET_NORMALIZATION_LIMIT",
                f"XML property tag exceeds size limit: {path}",
            )
        require(end < len(data), "E_SECRET_NORMALIZATION", f"unterminated XML property tag: {path}")
        attrs: dict[bytes, bytes] = {}
        for match in attribute.finditer(data[boundary:end]):
            name = match.group(1).lower()
            require(name not in attrs, "E_SECRET_NORMALIZATION", f"duplicate XML property attribute: {path}")
            attrs[name] = match.group(3)
        if b"name" in attrs:
            decoded = decode_xml_key(attrs[b"name"], path, budget, contract)
            credential = canonical_credential_key(decoded)
            if credential is not None and b"value" in attrs:
                synthetic.append(credential + b"=" + attrs[b"value"])
        cursor = end + 1
    element = re.compile(
        rb"(?is)<(username|password|passwd|pwd|credential|api[-_]?key|access[-_]?key|"
        rb"secret[-_]?access[-_]?key|access[-_]?key[-_]?(?:id|secret)|secret[-_]?id|"
        rb"secret[-_]?key|private[-_]?key|access[-_]?token|refresh[-_]?token|"
        rb"auth[-_]?token|client[-_]?token|client[-_]?secret|secret|authorization)"
        rb"\s*>([^<]*)</\1\s*>"
    )
    for match in element.finditer(data):
        require(
            len(match.group(2))
            <= int(normalization_limits(contract)["max_logical_line_bytes"]),
            "E_SECRET_NORMALIZATION_LIMIT",
            f"XML credential element exceeds size limit: {path}",
        )
        synthetic.append(match.group(1) + b"=" + match.group(2))
    if not synthetic:
        return data
    result = data + b"\n" + b"\n".join(synthetic) + b"\n"
    require(
        len(result) <= int(normalization_limits(contract)["max_normalized_bytes"]),
        "E_SECRET_NORMALIZATION_LIMIT",
        f"normalized XML credential view exceeds size limit: {path}",
    )
    return result


def normalize_html_quote_entities(
    path: str,
    data: bytes,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> bytes:
    quotes = {
        b"&#34;": b'"',
        b"&#39;": b"'",
        b"&#x22;": b'"',
        b"&#x27;": b"'",
        b"&apos;": b"'",
        b"&quot;": b'"',
    }

    def replace(match: re.Match[bytes]) -> bytes:
        budget.escape(path, contract)
        return quotes[match.group(0).lower()]

    result = re.sub(
        rb"(?i)&#(?:34|39|x22|x27);|&(?:apos|quot);",
        replace,
        data,
    )
    require(
        len(result) <= int(normalization_limits(contract)["max_normalized_bytes"]),
        "E_SECRET_NORMALIZATION_LIMIT",
        f"normalized HTML credential view exceeds size limit: {path}",
    )
    return result


def normalize_credential_key_view(
    path: str,
    data: bytes,
    budget: CredentialScanBudget,
    contract: dict[str, object],
) -> bytes:
    extension = payload_extension(path)
    if extension == "json":
        result = replace_quoted_keys(path, data, "JSON", budget, contract)
    elif extension in ("yaml", "yml"):
        tagged = re.sub(
            rb"(?im)^(\s*(?:-\s+)?)(?:!!str|!<tag:yaml\.org,2002:str>)\s+",
            rb"\1",
            data,
        )
        result = replace_quoted_keys(path, tagged, "YAML", budget, contract)
    elif extension in ("properties", "property"):
        result = normalize_properties_keys(path, data, budget, contract)
    elif extension in ("xml", "pom"):
        result = normalize_xml_keys(path, data, budget, contract)
    elif extension in ("htm", "html"):
        result = normalize_html_quote_entities(path, data, budget, contract)
    else:
        result = data
    require(
        len(result) <= int(normalization_limits(contract)["max_normalized_bytes"]),
        "E_SECRET_NORMALIZATION_LIMIT",
        f"normalized credential view exceeds size limit: {path}",
    )
    return result


def parse_environment_placeholder(
    data: bytes, start: int
) -> tuple[int, bytes, bytes | None] | None:
    if data[start : start + 2] != b"${":
        return None
    depth = 1
    cursor = start + 2
    while cursor < len(data):
        if data[cursor : cursor + 2] == b"${":
            depth += 1
            cursor += 2
            continue
        if data[cursor : cursor + 1] == b"}":
            depth -= 1
            cursor += 1
            if depth == 0:
                body = data[start + 2 : cursor - 1]
                name, separator, default = body.partition(b":")
                if ENVIRONMENT_NAME.fullmatch(name) is None:
                    return None
                return cursor, name, default if separator else None
            continue
        cursor += 1
    return None


def safe_placeholder_default(default: bytes | None) -> bool:
    current = default
    depth = 0
    while True:
        if current is None or current == b"" or SAFE_PLACEHOLDER_DEFAULT.fullmatch(current):
            return True
        nested = parse_environment_placeholder(current, 0)
        if nested is None or nested[0] != len(current):
            return False
        depth += 1
        require(
            depth <= 64,
            "E_SECRET_NORMALIZATION_LIMIT",
            "environment placeholder nesting exceeds limit",
        )
        current = nested[2]


def safe_environment_function(value: bytes) -> bool:
    """Accept only a single environment-name argument and no literal default.

    JaCoCo source HTML encodes quote characters as entities, so the portable
    evidence scanner must recognize that representation without broadly
    decoding arbitrary markup or accepting a second/default argument.
    """

    if re.fullmatch(
        rb"env\([ \t]*(['\"])[A-Za-z_][A-Za-z0-9_.-]*\1[ \t]*\)", value
    ) is not None:
        return True
    for quote in (
        b"&#39;",
        b"&#x27;",
        b"&apos;",
        b"&#34;",
        b"&#x22;",
        b"&quot;",
    ):
        escaped = re.escape(quote)
        if re.fullmatch(
            rb"env\([ \t]*"
            + escaped
            + rb"[A-Za-z_][A-Za-z0-9_.-]*"
            + escaped
            + rb"[ \t]*\)",
            value,
            flags=re.IGNORECASE,
        ) is not None:
            return True
    return False


def sanitize_environment_functions(
    path: str,
    data: bytes,
    contract: dict[str, object],
    budget: CredentialScanBudget,
) -> bytes:
    patterns = [
        re.compile(rb"env\([ \t]*(['\"])[A-Za-z_][A-Za-z0-9_.-]*\1[ \t]*\)"),
    ]
    for quote in (
        b"&#39;",
        b"&#x27;",
        b"&apos;",
        b"&#34;",
        b"&#x22;",
        b"&quot;",
    ):
        escaped = re.escape(quote)
        patterns.append(
            re.compile(
                rb"env\([ \t]*"
                + escaped
                + rb"[A-Za-z_][A-Za-z0-9_.-]*"
                + escaped
                + rb"[ \t]*\)",
                flags=re.IGNORECASE,
            )
        )

    output = data
    for pattern in patterns:
        def replace(_: re.Match[bytes]) -> bytes:
            budget.placeholder(path, contract)
            # Ellipsis is a contract-declared safe marker in quoted and
            # unquoted assignment forms; using it also avoids the legacy
            # pattern engine's asymmetric handling of single-quoted ${ENV}.
            return b"..."

        output = pattern.sub(replace, output)
    require(
        len(output) <= int(normalization_limits(contract)["max_normalized_bytes"]),
        "E_SECRET_NORMALIZATION_LIMIT",
        f"normalized environment-function view exceeds size limit: {path}",
    )
    return output


def safe_assignment_value(value: bytes) -> bool:
    if value in (b"", b"{}") or SAFE_PLACEHOLDER_DEFAULT.fullmatch(value) is not None:
        return True
    placeholder = parse_environment_placeholder(value, 0)
    if placeholder is not None and placeholder[0] == len(value):
        return safe_placeholder_default(placeholder[2])
    if safe_environment_function(value):
        return True
    authorization = re.fullmatch(
        rb"(?i)(?:bearer|basic)\s+(\$\{[^{}\r\n]+\})", value
    )
    if authorization is None:
        return False
    placeholder = parse_environment_placeholder(authorization.group(1), 0)
    return placeholder is not None and placeholder[0] == len(authorization.group(1)) \
        and safe_placeholder_default(placeholder[2])


def safe_runtime_credential_value(value: bytes) -> bool:
    if value == b"" or re.fullmatch(
        rb"(?i)(?:null|redacted|<redacted>|\[redacted\]|\*{3,}|x{3,}|sk-x{2,}|\.{3})",
        value,
    ) is not None:
        return True
    placeholder = parse_environment_placeholder(value, 0)
    if placeholder is not None and placeholder[0] == len(value):
        return placeholder[2] in (None, b"")
    if safe_environment_function(value):
        return True
    authorization = re.fullmatch(
        rb"(?i)(?:bearer|basic)\s+(\$\{[^{}\r\n]+\})", value
    )
    if authorization is None:
        return False
    placeholder = parse_environment_placeholder(authorization.group(1), 0)
    return (
        placeholder is not None
        and placeholder[0] == len(authorization.group(1))
        and placeholder[2] in (None, b"")
    )


def credential_assignment_prefix(data: bytes, start: int) -> bool:
    prefix = data[max(0, start - 256) : start]
    return (
        re.search(
            rb"(?im)(?:^[ \t]*(?:-[ \t]+)?(?:[A-Za-z0-9_-]+\.)*|[,{]\s*)\"?"
            rb"(?:username|password|passwd|pwd|credential|api[-_]?key|access[-_]?key|"
            rb"secret[-_]?access[-_]?key|access[-_]?key[-_]?(?:id|secret)|secret[-_]?id|"
            rb"secret[-_]?key|private[-_]?key|access[-_]?token|refresh[-_]?token|"
            rb"auth[-_]?token|client[-_]?token|client[-_]?secret|secret|authorization)"
            rb"\"?\s*[:=]\s*\"?$",
            prefix,
        )
        is not None
    )


def sanitize_environment_placeholders(
    path: str,
    data: bytes,
    contract: dict[str, object],
    budget: CredentialScanBudget,
    depth: int = 0,
) -> bytes:
    require(
        depth <= int(normalization_limits(contract)["max_placeholder_depth"]),
        "E_SECRET_NORMALIZATION_LIMIT",
        f"environment placeholder nesting exceeds limit: {path}",
    )
    output: list[bytes] = []
    cursor = 0
    while cursor < len(data):
        start = data.find(b"${", cursor)
        if start < 0:
            output.append(data[cursor:])
            break
        output.append(data[cursor:start])
        parsed = parse_environment_placeholder(data, start)
        if parsed is None:
            output.append(data[start : start + 2])
            cursor = start + 2
            continue
        end, name, default = parsed
        budget.placeholder(path, contract)
        if default is None or safe_placeholder_default(default):
            output.append(b"${SAFE_ENVIRONMENT_PLACEHOLDER}")
        else:
            if CREDENTIAL_NAME.search(name) is not None or credential_assignment_prefix(data, start):
                reject(
                    "E_SECRET",
                    f"credential environment placeholder has a literal default: {path}",
                )
            # A generic placeholder must not hide a credential-shaped default.
            # Normalize the fragment exactly once and share the same recursion
            # and work budget with its owning payload.
            sanitized_default = sanitize_environment_placeholders(
                path, default, contract, budget, depth + 1
            )
            scan_sanitized_credential_patterns(path, sanitized_default, contract)
            output.append(sanitized_default)
        cursor = end
    return b"".join(output)


def scan_sanitized_credential_patterns(
    path: str, inspected: bytes, contract: dict[str, object]
) -> None:
    policy = sensitive_policy(contract)
    patterns = policy["patterns"]
    assert isinstance(patterns, list)
    for match in QUOTED_CREDENTIAL_ASSIGNMENT.finditer(inspected):
        value = match.group("double")
        if value is None:
            value = match.group("single")
        assert value is not None
        if not safe_assignment_value(value):
            reject(
                "E_SECRET",
                f"quoted credential assignment is forbidden in payload file: {path}",
            )
    for row in patterns:
        assert isinstance(row, dict)
        pattern = row["regex"]
        assert isinstance(pattern, str)
        if re.search(pattern.encode("ascii"), inspected) is not None:
            reject(
                "E_SECRET",
                f"credential-shaped text is forbidden in payload file: {path}",
            )


def scan_credential_patterns(path: str, data: bytes, contract: dict[str, object]) -> None:
    budget = CredentialScanBudget()
    normalized = normalize_credential_key_view(path, data, budget, contract)
    inspected = sanitize_environment_placeholders(
        path, normalized, contract, budget
    )
    inspected = sanitize_environment_functions(path, inspected, contract, budget)
    scan_sanitized_credential_patterns(path, inspected, contract)


def is_text_payload(
    path: str, data: bytes, contract: dict[str, object], declared: bool
) -> bool:
    classifier = sensitive_policy(contract)["content_classifier"]
    assert isinstance(classifier, dict)
    maximum = int(classifier["max_text_bytes"])
    if len(data) > maximum:
        if declared:
            reject("E_TEXT_SIZE", f"declared text payload exceeds scan limit: {path}")
        return False
    if b"\x00" in data:
        if declared:
            reject("E_TEXT_BINARY", f"declared text payload contains a NUL byte: {path}")
        return False
    try:
        text = data.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        # Java resource bundles commonly use ISO-8859-1. Pattern matching remains
        # byte-based; Latin-1 is used only for deterministic text/binary scoring.
        text = data.decode("latin-1")
    if not text:
        return True
    printable = sum(character.isprintable() or character in "\r\n\t" for character in text)
    ratio = printable / len(text)
    minimum = float(classifier["minimum_printable_ratio"])
    if ratio < minimum:
        if declared:
            reject(
                "E_TEXT_BINARY",
                f"declared text payload printable ratio {ratio:.4f} is below {minimum}: {path}",
            )
        return False
    return True


@dataclass
class ArchiveScanBudget:
    entries: int = 0
    uncompressed_bytes: int = 0


def validate_zip_framing(path: str, data: bytes) -> int:
    require(len(data) >= 22, "E_ZIP_FRAMING", f"ZIP archive is truncated: {path}")
    signature = b"PK\x05\x06"
    candidates: list[int] = []
    start = max(0, len(data) - (65_535 + 22))
    cursor = start
    while True:
        offset = data.find(signature, cursor)
        if offset < 0:
            break
        if offset + 22 <= len(data):
            comment_size = int.from_bytes(data[offset + 20 : offset + 22], "little")
            if offset + 22 + comment_size == len(data):
                candidates.append(offset)
        cursor = offset + 1
    require(
        len(candidates) == 1,
        "E_ZIP_FRAMING",
        f"ZIP end record is absent or ambiguous: {path}",
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
        "E_ZIP_FRAMING",
        f"multi-disk ZIP archive is forbidden: {path}",
    )
    require(
        total_entries != 0xFFFF
        and directory_size != 0xFFFFFFFF
        and directory_offset != 0xFFFFFFFF,
        "E_ZIP_FRAMING",
        f"ZIP64 archive is outside the governed size contract: {path}",
    )
    require(comment_size == 0, "E_ZIP_FRAMING", f"ZIP archive comment is forbidden: {path}")
    require(
        directory_offset + directory_size == eocd,
        "E_ZIP_FRAMING",
        f"ZIP central-directory framing differs: {path}",
    )
    if total_entries:
        require(
            data.startswith(b"PK\x03\x04")
            and directory_size > 0
            and data[directory_offset : directory_offset + 4] == b"PK\x01\x02",
            "E_ZIP_FRAMING",
            f"ZIP has a prefix or missing canonical records: {path}",
        )
    else:
        require(
            directory_offset == directory_size == eocd == 0,
            "E_ZIP_FRAMING",
            f"empty ZIP framing differs: {path}",
        )
    return total_entries


class TarFramingValidator:
    def __init__(self, maximum: int):
        self.maximum = maximum
        self.total = 0
        self.buffer = bytearray()
        self.data_blocks = 0
        self.zero_headers = 0
        self.eof = False

    def feed(self, data: bytes) -> None:
        self.total += len(data)
        require(
            self.total <= self.maximum,
            "E_ARCHIVE_FRAMING",
            "release tar stream exceeds framing budget",
        )
        self.buffer.extend(data)
        while len(self.buffer) >= 512:
            block = bytes(self.buffer[:512])
            del self.buffer[:512]
            if self.eof:
                require(
                    block == b"\x00" * 512,
                    "E_ARCHIVE_FRAMING",
                    "non-zero tar data follows the canonical EOF records",
                )
                continue
            if self.data_blocks:
                self.data_blocks -= 1
                continue
            if block == b"\x00" * 512:
                self.zero_headers += 1
                if self.zero_headers == 2:
                    self.eof = True
                continue
            require(
                self.zero_headers == 0,
                "E_ARCHIVE_FRAMING",
                "tar contains a single EOF block followed by another header",
            )
            size_field = block[124:136]
            require(
                not (size_field[0] & 0x80),
                "E_ARCHIVE_FRAMING",
                "base-256 tar sizes are outside the canonical release format",
            )
            token = size_field.strip(b"\x00 ")
            require(
                not token or re.fullmatch(rb"[0-7]+", token) is not None,
                "E_ARCHIVE_FRAMING",
                "tar header has a malformed size field",
            )
            size = int(token, 8) if token else 0
            self.data_blocks = (size + 511) // 512

    def finish(self) -> None:
        require(not self.buffer, "E_ARCHIVE_FRAMING", "tar stream is not block aligned")
        require(self.eof, "E_ARCHIVE_FRAMING", "tar stream lacks two EOF blocks")
        require(
            self.total % tarfile.RECORDSIZE == 0,
            "E_ARCHIVE_FRAMING",
            "tar stream is not canonically record padded",
        )


def validate_gzip_tar_framing(path: Path, contract: dict[str, object]) -> None:
    archive = Path(os.path.abspath(path))
    try:
        observed = os.lstat(archive)
    except OSError as error:
        reject("E_ARCHIVE_MISSING", f"cannot inspect release archive framing: {error}")
    require(
        stat.S_ISREG(observed.st_mode) and not stat.S_ISLNK(observed.st_mode),
        "E_SPECIAL",
        "release archive framing input is not a regular file",
    )
    maximum = (
        int(archive_limits(contract)["max_outer_uncompressed_bytes"])
        + int(archive_limits(contract)["max_outer_entries"]) * 1024
        + tarfile.RECORDSIZE
    )
    validator = TarFramingValidator(maximum)
    decompressor = zlib.decompressobj(16 + zlib.MAX_WBITS)
    with open(archive, "rb") as source:
        header = source.read(10)
        require(
            header == b"\x1f\x8b\x08\x00\x00\x00\x00\x00\x02\xff",
            "E_ARCHIVE_FRAMING",
            "release gzip header is not canonical",
        )
        pending = header
        while True:
            if not pending:
                pending = source.read(64 * 1024)
                if not pending:
                    break
            while pending:
                expanded = decompressor.decompress(pending, 1024 * 1024)
                pending = decompressor.unconsumed_tail
                validator.feed(expanded)
                if decompressor.eof:
                    require(
                        not decompressor.unused_data and not pending,
                        "E_ARCHIVE_FRAMING",
                        "release gzip has trailing or concatenated data",
                    )
                    require(
                        source.read(1) == b"",
                        "E_ARCHIVE_FRAMING",
                        "release gzip has trailing or concatenated data",
                    )
                    pending = b""
                    break
            if decompressor.eof:
                break
    require(decompressor.eof, "E_ARCHIVE_FRAMING", "release gzip member is truncated")
    validator.feed(decompressor.flush())
    validator.finish()


@dataclass
class ClassReader:
    data: bytes
    path: str
    cursor: int = 0

    def take(self, size: int) -> bytes:
        require(
            size >= 0 and self.cursor + size <= len(self.data),
            "E_CLASS_FORMAT",
            f"truncated JVM class structure: {self.path}",
        )
        value = self.data[self.cursor : self.cursor + size]
        self.cursor += size
        return value

    def u1(self) -> int:
        return int.from_bytes(self.take(1), "big")

    def u2(self) -> int:
        return int.from_bytes(self.take(2), "big")

    def u4(self) -> int:
        return int.from_bytes(self.take(4), "big")

    def finish(self) -> None:
        require(
            self.cursor == len(self.data),
            "E_CLASS_FORMAT",
            f"trailing JVM class structure bytes: {self.path}",
        )


def class_cp_entry(
    pool: list[tuple[object, ...] | None],
    index: int,
    tags: tuple[int, ...],
    path: str,
) -> tuple[object, ...]:
    require(
        0 < index < len(pool)
        and pool[index] is not None
        and int(pool[index][0]) in tags,
        "E_CLASS_FORMAT",
        f"invalid JVM constant-pool reference in {path}: {index}",
    )
    entry = pool[index]
    assert entry is not None
    return entry


def class_utf8(
    pool: list[tuple[object, ...] | None], index: int, path: str
) -> bytes:
    entry = class_cp_entry(pool, index, (1,), path)
    value = entry[1]
    assert isinstance(value, bytes)
    return value


def class_string(
    pool: list[tuple[object, ...] | None], index: int, path: str
) -> bytes | None:
    entry = class_cp_entry(pool, index, tuple(range(1, 21)), path)
    if int(entry[0]) != 8:
        return None
    return class_utf8(pool, int(entry[1]), path)


def class_field_name(
    pool: list[tuple[object, ...] | None], index: int, path: str
) -> bytes:
    reference = class_cp_entry(pool, index, (9,), path)
    class_cp_entry(pool, int(reference[1]), (7,), path)
    name_and_type = class_cp_entry(pool, int(reference[2]), (12,), path)
    return class_utf8(pool, int(name_and_type[1]), path)


def parse_class_code(
    data: bytes,
    pool: list[tuple[object, ...] | None],
    path: str,
) -> tuple[list[bytes], int]:
    reader = ClassReader(data, path)
    reader.u2()
    reader.u2()
    code_length = reader.u4()
    code = reader.take(code_length)
    bindings: list[bytes] = []
    cursor = 0
    pending_literal: bytes | None = None
    two = {0x10, 0x12, *range(0x15, 0x1A), *range(0x36, 0x3B), 0xA9, 0xBC}
    three = {
        0x11,
        0x13,
        0x14,
        0x84,
        *range(0x99, 0xA9),
        *range(0xB2, 0xB9),
        0xBB,
        0xBD,
        0xC0,
        0xC1,
        0xC6,
        0xC7,
    }
    while cursor < len(code):
        start = cursor
        opcode = code[cursor]
        cursor += 1
        literal: bytes | None = None
        field: bytes | None = None
        if opcode in two:
            cursor += 1
        elif opcode in three:
            cursor += 2
        elif opcode == 0xC5:
            cursor += 3
        elif opcode in (0xB9, 0xBA, 0xC8, 0xC9):
            cursor += 4
        elif opcode == 0xAA:
            padding = (4 - (cursor % 4)) % 4
            require(
                cursor + padding + 12 <= len(code)
                and code[cursor : cursor + padding] == b"\x00" * padding,
                "E_CLASS_FORMAT",
                f"malformed JVM tableswitch: {path}",
            )
            cursor += padding + 4
            low = int.from_bytes(code[cursor : cursor + 4], "big", signed=True)
            high = int.from_bytes(code[cursor + 4 : cursor + 8], "big", signed=True)
            cursor += 8
            require(high >= low, "E_CLASS_FORMAT", f"malformed JVM tableswitch range: {path}")
            cursor += (high - low + 1) * 4
        elif opcode == 0xAB:
            padding = (4 - (cursor % 4)) % 4
            require(
                cursor + padding + 8 <= len(code)
                and code[cursor : cursor + padding] == b"\x00" * padding,
                "E_CLASS_FORMAT",
                f"malformed JVM lookupswitch: {path}",
            )
            cursor += padding + 4
            pairs = int.from_bytes(code[cursor : cursor + 4], "big", signed=True)
            cursor += 4
            require(pairs >= 0, "E_CLASS_FORMAT", f"negative JVM lookupswitch pairs: {path}")
            cursor += pairs * 8
        elif opcode == 0xC4:
            require(cursor < len(code), "E_CLASS_FORMAT", f"truncated JVM wide opcode: {path}")
            widened = code[cursor]
            require(
                widened in {*range(0x15, 0x1A), *range(0x36, 0x3B), 0x84, 0xA9},
                "E_CLASS_FORMAT",
                f"invalid JVM wide opcode: {path}",
            )
            cursor += 5 if widened == 0x84 else 3
        else:
            require(opcode <= 0xC9, "E_CLASS_FORMAT", f"reserved JVM opcode: {path}")
        require(cursor <= len(code), "E_CLASS_FORMAT", f"truncated JVM instruction: {path}")
        if opcode == 0x12:
            literal = class_string(pool, code[start + 1], path)
        elif opcode in (0x13, 0x14):
            literal = class_string(
                pool, int.from_bytes(code[start + 1 : start + 3], "big"), path
            )
        elif opcode in (0xB3, 0xB5):
            field = class_field_name(
                pool, int.from_bytes(code[start + 1 : start + 3], "big"), path
            )
        if field is not None and pending_literal is not None:
            if CREDENTIAL_KEY.fullmatch(field) is not None:
                bindings.append(field + b"=" + pending_literal)
        pending_literal = literal
    exceptions = reader.u2()
    reader.take(exceptions * 8)
    nested = reader.u2()
    for _ in range(nested):
        class_utf8(pool, reader.u2(), path)
        reader.take(reader.u4())
    reader.finish()
    return bindings, code_length


def scan_class_file(path: str, data: bytes, contract: dict[str, object]) -> None:
    reader = ClassReader(data, path)
    require(reader.take(4) == b"\xca\xfe\xba\xbe", "E_CLASS_FORMAT", f"invalid JVM class magic: {path}")
    reader.u2()
    major = reader.u2()
    require(45 <= major <= 70, "E_CLASS_FORMAT", f"unsupported JVM class version: {path}")
    count = reader.u2()
    require(count > 1, "E_CLASS_FORMAT", f"empty JVM constant pool: {path}")
    pool: list[tuple[object, ...] | None] = [None] * count
    utf8_total = 0
    index = 1
    while index < count:
        tag = reader.u1()
        if tag == 1:
            value = reader.take(reader.u2())
            utf8_total += len(value)
            require(
                utf8_total
                <= int(normalization_limits(contract)["max_class_utf8_bytes"]),
                "E_CLASS_LIMIT",
                f"JVM class UTF-8 pool exceeds limit: {path}",
            )
            pool[index] = (tag, value)
        elif tag in (3, 4):
            pool[index] = (tag, reader.take(4))
        elif tag in (5, 6):
            pool[index] = (tag, reader.take(8))
            index += 1
            require(index < count, "E_CLASS_FORMAT", f"truncated wide JVM constant: {path}")
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, reader.u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, reader.u2(), reader.u2())
        elif tag == 15:
            pool[index] = (tag, reader.u1(), reader.u2())
        else:
            reject("E_CLASS_FORMAT", f"unsupported JVM constant-pool tag {tag}: {path}")
        index += 1
    reader.u2()
    this_class = reader.u2()
    super_class = reader.u2()
    this_class_entry = class_cp_entry(pool, this_class, (7,), path)
    internal_name = class_utf8(pool, int(this_class_entry[1]), path)
    class_policy = sensitive_policy(contract)["class_assignment_policy"]
    assert isinstance(class_policy, dict)
    prefixes = class_policy["first_party_internal_name_prefixes"]
    assert isinstance(prefixes, list)
    first_party_runtime_class = any(
        internal_name.startswith(str(prefix).encode("ascii")) for prefix in prefixes
    )
    if super_class:
        class_cp_entry(pool, super_class, (7,), path)
    for _ in range(reader.u2()):
        class_cp_entry(pool, reader.u2(), (7,), path)
    assignments: list[bytes] = []
    constant_literals: list[bytes] = []
    fields = reader.u2()
    for _ in range(fields):
        reader.u2()
        field_name = class_utf8(pool, reader.u2(), path)
        class_utf8(pool, reader.u2(), path)
        attributes = reader.u2()
        for _ in range(attributes):
            attribute_name = class_utf8(pool, reader.u2(), path)
            attribute_data = reader.take(reader.u4())
            if attribute_name == b"ConstantValue":
                require(len(attribute_data) == 2, "E_CLASS_FORMAT", f"malformed ConstantValue: {path}")
                literal = class_string(pool, int.from_bytes(attribute_data, "big"), path)
                if literal is not None:
                    constant_literals.append(literal)
                    if CREDENTIAL_KEY.fullmatch(field_name) is not None:
                        assignments.append(field_name + b"=" + literal)
    code_total = 0
    methods = reader.u2()
    for _ in range(methods):
        reader.u2()
        class_utf8(pool, reader.u2(), path)
        class_utf8(pool, reader.u2(), path)
        attributes = reader.u2()
        for _ in range(attributes):
            attribute_name = class_utf8(pool, reader.u2(), path)
            attribute_data = reader.take(reader.u4())
            if attribute_name == b"Code":
                bindings, code_size = parse_class_code(attribute_data, pool, path)
                assignments.extend(bindings)
                code_total += code_size
                require(
                    code_total
                    <= int(normalization_limits(contract)["max_class_code_bytes"]),
                    "E_CLASS_LIMIT",
                    f"JVM class code exceeds limit: {path}",
                )
    for _ in range(reader.u2()):
        class_utf8(pool, reader.u2(), path)
        reader.take(reader.u4())
    reader.finish()
    if first_party_runtime_class:
        for assignment in assignments:
            _, separator, value = assignment.partition(b"=")
            require(bool(separator), "E_CLASS_FORMAT", f"malformed class credential binding: {path}")
            require(
                safe_runtime_credential_value(value),
                "E_SECRET",
                f"literal runtime credential field is forbidden in JVM class: {path}",
            )
    # Third-party libraries often expose protocol/header constants whose field
    # names are credential-shaped but whose values are not secrets.  Preserve
    # framing, resource budgets and generic literal scanning for every class;
    # synthesize field=value bindings only for the governed first-party
    # namespace, where a literal credential default is forbidden.
    inspected = constant_literals + (assignments if first_party_runtime_class else [])
    if inspected:
        scan_credential_patterns(path + "!/.class-constants.txt", b"\n".join(inspected), contract)


def archive_member_path(
    info: zipfile.ZipInfo, seen: dict[str, str], archive_path: str
) -> tuple[str, bool]:
    original = info.orig_filename
    require(type(original) is str and bool(original), "E_ZIP_PATH", f"empty ZIP member path: {archive_path}")
    require(
        original == info.filename,
        "E_ZIP_PATH",
        f"ZIP member path was truncated or normalized by the reader: {archive_path}",
    )
    directory = info.is_dir()
    if directory:
        require(original.endswith("/") and original.count("/") >= 1, "E_ZIP_PATH", f"invalid ZIP directory path: {archive_path}!/{original}")
        raw = original[:-1]
    else:
        require(not original.endswith("/"), "E_ZIP_PATH", f"invalid ZIP file path: {archive_path}!/{original}")
        raw = original
    try:
        path = normalize_relative_path(raw, "ZIP member")
    except ArtifactError as error:
        reject("E_ZIP_PATH", f"invalid ZIP member in {archive_path}: {error}")
    folded = path.casefold()
    previous = seen.get(folded)
    require(
        previous is None,
        "E_ZIP_DUPLICATE",
        f"duplicate/colliding ZIP member in {archive_path}: {previous!r}, {path!r}",
    )
    seen[folded] = path
    return path, directory


def read_zip_member(
    archive: zipfile.ZipFile, info: zipfile.ZipInfo, display_path: str, maximum: int
) -> bytes:
    chunks: list[bytes] = []
    total = 0
    try:
        with archive.open(info, mode="r") as source:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                total += len(chunk)
                require(total <= maximum, "E_ZIP_ENTRY_SIZE", f"ZIP member exceeds size limit: {display_path}")
                require(total <= info.file_size, "E_ZIP_ENTRY_SIZE", f"ZIP member exceeds declared size: {display_path}")
                chunks.append(chunk)
    except ArtifactError:
        raise
    except (OSError, EOFError, RuntimeError, NotImplementedError, zipfile.BadZipFile) as error:
        reject("E_ZIP_READ", f"cannot read ZIP member {display_path}: {error}")
    require(total == info.file_size, "E_ZIP_ENTRY_SIZE", f"ZIP member size differs: {display_path}")
    return b"".join(chunks)


def scan_zip_archive(
    path: str,
    data: bytes,
    contract: dict[str, object],
    budget: ArchiveScanBudget,
    depth: int,
) -> None:
    limits = archive_limits(contract)
    require(depth <= int(limits["max_archive_depth"]), "E_ZIP_DEPTH", f"nested ZIP depth exceeds limit: {path}")
    require(len(data) <= int(limits["max_archive_bytes"]), "E_ZIP_SIZE", f"ZIP archive exceeds size limit: {path}")
    framed_entries = validate_zip_framing(path, data)
    try:
        archive = zipfile.ZipFile(io.BytesIO(data), mode="r")
    except (OSError, EOFError, RuntimeError, NotImplementedError, zipfile.BadZipFile) as error:
        reject("E_ZIP_FORMAT", f"cannot open ZIP archive {path}: {error}")
    with archive:
        try:
            members = archive.infolist()
        except (OSError, EOFError, RuntimeError, NotImplementedError, zipfile.BadZipFile) as error:
            reject("E_ZIP_FORMAT", f"cannot enumerate ZIP archive {path}: {error}")
        require(
            len(members) <= int(limits["max_entries_per_archive"]),
            "E_ZIP_ENTRIES",
            f"ZIP archive contains too many entries: {path}",
        )
        require(
            len(members) == framed_entries,
            "E_ZIP_FRAMING",
            f"ZIP central-directory entry count differs: {path}",
        )
        if members:
            require(
                min(info.header_offset for info in members) == 0,
                "E_ZIP_FRAMING",
                f"ZIP local records have a non-canonical prefix: {path}",
            )
        seen: dict[str, str] = {}
        for info in members:
            member_path, directory = archive_member_path(info, seen, path)
            display_path = f"{path}!/{member_path}"
            budget.entries += 1
            budget.uncompressed_bytes += info.file_size
            require(
                budget.entries <= int(limits["max_recursive_entries"]),
                "E_ZIP_ENTRIES",
                f"recursive ZIP entry count exceeds limit: {display_path}",
            )
            require(
                budget.uncompressed_bytes <= int(limits["max_recursive_uncompressed_bytes"]),
                "E_ZIP_BOMB",
                f"recursive ZIP expansion exceeds limit: {display_path}",
            )
            require(not (info.flag_bits & 0x0001), "E_ZIP_ENCRYPTED", f"encrypted ZIP member is forbidden: {display_path}")
            require(
                not info.comment,
                "E_ZIP_FRAMING",
                f"ZIP member comment is forbidden: {display_path}",
            )
            require(
                not (info.flag_bits & ~0x080E),
                "E_ZIP_FLAGS",
                f"unsupported ZIP flags are present: {display_path}",
            )
            method = ALLOWED_ZIP_METHODS.get(info.compress_type)
            allowed_methods = limits["allowed_compression_methods"]
            assert isinstance(allowed_methods, list)
            require(method in allowed_methods, "E_ZIP_COMPRESSION", f"unsupported ZIP compression method: {display_path}")
            unix_mode = (info.external_attr >> 16) & 0xFFFF if info.create_system == 3 else 0
            # 0xffff is Maven Archiver's common "mode unavailable" sentinel.
            if unix_mode not in (0, 0xFFFF):
                file_type = stat.S_IFMT(unix_mode)
                expected_type = stat.S_IFDIR if directory else stat.S_IFREG
                require(file_type == expected_type, "E_ZIP_SPECIAL", f"ZIP link/special member is forbidden: {display_path}")
            if directory:
                require(
                    info.file_size == 0 and info.compress_size <= 16,
                    "E_ZIP_ENTRY_SIZE",
                    f"ZIP directory has an abnormal payload: {display_path}",
                )
                require(
                    read_zip_member(archive, info, display_path, 16) == b"",
                    "E_ZIP_ENTRY_SIZE",
                    f"ZIP directory expanded to data: {display_path}",
                )
                continue
            require(
                info.file_size <= int(limits["max_entry_uncompressed_bytes"]),
                "E_ZIP_ENTRY_SIZE",
                f"ZIP member exceeds size limit: {display_path}",
            )
            if info.file_size:
                require(info.compress_size > 0, "E_ZIP_BOMB", f"non-empty ZIP member has zero compressed size: {display_path}")
                ratio = info.file_size / info.compress_size
                require(
                    ratio <= float(limits["max_compression_ratio"]),
                    "E_ZIP_BOMB",
                    f"ZIP member compression ratio exceeds limit: {display_path}",
                )
            member_data = read_zip_member(
                archive, info, display_path, int(limits["max_entry_uncompressed_bytes"])
            )
            if member_path.lower().endswith(".class"):
                scan_class_file(display_path, member_data, contract)
                continue
            scan_sensitive_payload(
                display_path,
                member_data,
                contract,
                budget=budget,
                archive_depth=depth + 1,
            )


def scan_sensitive_payload(
    path: str,
    data: bytes,
    contract: dict[str, object],
    budget: ArchiveScanBudget | None = None,
    archive_depth: int = 1,
) -> None:
    if is_generated_metadata(path, contract):
        return
    declared_class = path.lower().endswith(".class")
    detected_class = data.startswith(b"\xca\xfe\xba\xbe")
    if declared_class or detected_class:
        require(
            detected_class,
            "E_CLASS_FORMAT",
            f"declared JVM class payload has invalid magic: {path}",
        )
        scan_class_file(path, data, contract)
        return
    declared_archive = declared_archive_path(path, contract)
    try:
        detected_archive = zipfile.is_zipfile(io.BytesIO(data))
    except (OSError, EOFError, RuntimeError, zipfile.BadZipFile) as error:
        if declared_archive:
            reject("E_ZIP_FORMAT", f"cannot identify declared ZIP archive {path}: {error}")
        detected_archive = False
    if declared_archive or detected_archive:
        require(detected_archive, "E_ZIP_FORMAT", f"declared ZIP/JAR payload is not a valid archive: {path}")
        scan_zip_archive(
            path,
            data,
            contract,
            budget if budget is not None else ArchiveScanBudget(),
            archive_depth,
        )
        return
    declared_text = declared_text_path(path, contract)
    if is_text_payload(path, data, contract, declared_text):
        scan_credential_patterns(path, data, contract)


def scan_tree(root: Path, contract: dict[str, object], allow_metadata: bool) -> list[Entry]:
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    metadata = str(manifests["metadata_directory"])
    entries: list[Entry] = []
    seen: dict[str, str] = {}
    total_file_bytes = 0

    def add_entry(entry: Entry) -> None:
        nonlocal total_file_bytes
        limits = archive_limits(contract)
        require(
            len(entries) + 1 <= int(limits["max_outer_entries"]),
            "E_OUTER_ENTRIES",
            "release tree contains too many entries",
        )
        if entry.kind == "file":
            require(
                entry.size <= int(limits["max_outer_member_uncompressed_bytes"]),
                "E_OUTER_MEMBER_SIZE",
                f"release tree file exceeds outer member limit: {entry.path}",
            )
            total_file_bytes += entry.size
            require(
                total_file_bytes <= int(limits["max_outer_uncompressed_bytes"]),
                "E_OUTER_TOTAL_SIZE",
                "release tree exceeds outer uncompressed size limit",
            )
        entries.append(entry)

    def visit(directory: Path, prefix: str) -> None:
        try:
            children = sorted(os.scandir(directory), key=lambda item: item.name.encode("utf-8"))
        except OSError as error:
            reject("E_DIRECTORY", f"cannot scan {directory}: {error}")
        for child in children:
            relative = f"{prefix}/{child.name}" if prefix else child.name
            relative = normalize_relative_path(relative, "filesystem path")
            register_path(relative, seen)
            if not allow_metadata:
                require(
                    relative != metadata and not relative.startswith(metadata + "/"),
                    "E_RESERVED_PATH",
                    f"staging root contains reserved metadata path: {relative}",
                )
            try:
                child_stat = child.stat(follow_symlinks=False)
            except OSError as error:
                reject("E_FILE_RACE", f"cannot stat {relative}: {error}")
            if stat.S_ISLNK(child_stat.st_mode):
                reject("E_SYMLINK", f"symlink is forbidden: {relative}")
            if stat.S_ISDIR(child_stat.st_mode):
                add_entry(
                    Entry(relative, "directory", normalized_mode(child_stat.st_mode, contract, True), source=Path(child.path))
                )
                visit(Path(child.path), relative)
            elif stat.S_ISREG(child_stat.st_mode):
                data, snapshot = secure_regular_snapshot(
                    Path(child.path),
                    relative,
                    maximum_payload_file_bytes(contract),
                )
                scan_sensitive_payload(relative, data, contract)
                require(
                    0 <= snapshot.st_mtime_ns <= 9_223_372_036_854_775_807,
                    "E_FILE_MTIME",
                    f"source file mtime is outside the portable range: {relative}",
                )
                add_entry(
                    Entry(
                        relative,
                        "file",
                        normalized_mode(snapshot.st_mode, contract, False),
                        len(data),
                        sha256_bytes(data),
                        snapshot.st_mtime_ns,
                        Path(child.path),
                    )
                )
            else:
                reject("E_SPECIAL", f"special filesystem entry is forbidden: {relative}")
    visit(root, "")
    return sorted(entries, key=lambda entry: path_sort(entry.path))


def sensitive_scan_receipt(
    root_path: Path,
    allow_metadata: bool,
    env_output: Path | None = None,
) -> dict[str, object]:
    contract, _, contract_sha256 = validate_contract()
    root = real_directory(root_path, "sensitive scan root")
    entries = scan_tree(root, contract, allow_metadata=allow_metadata)
    files = [entry for entry in entries if entry.kind == "file"]
    policy = sensitive_policy(contract)
    patterns = policy["patterns"]
    extensions = policy["extensions"]
    archive_extensions = policy["archive_extensions"]
    assert isinstance(patterns, list)
    assert isinstance(extensions, list)
    assert isinstance(archive_extensions, list)
    receipt: dict[str, object] = {
        "archive_extension_count": len(archive_extensions),
        "bytes": sum(entry.size for entry in files),
        "command": "scan-root",
        "contract_sha256": contract_sha256,
        "files": len(files),
        "kind": "v934-release-sensitive-scan",
        "metadata_allowed": allow_metadata,
        "pattern_count": len(patterns),
        "policy_sha256": sha256_bytes(canonical_json(policy)),
        "schema_version": 2,
        "scope": policy["scope"],
        "status": "passed",
        "text_extension_count": len(extensions),
    }
    if env_output is not None:
        output = Path(os.path.abspath(env_output))
        require(
            output == root / "release/sensitive-scan.env",
            "E_OUTPUT",
            "sensitive scan env output must be release/sensitive-scan.env inside the scanned root",
        )
        real_directory(output.parent, "sensitive scan env parent")
        env_fields = (
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
        )
        env_data = "".join(f"{field}={receipt[field]}\n" for field in env_fields).encode(
            "ascii"
        )
        write_new_file(output, env_data)
    return receipt


RUNTIME_LINE_CREDENTIAL = re.compile(
    rb"(?im)^[ \t]*(?:(?:\*|//|#)[ \t]*)?(?:-[ \t]+)?"
    rb"(?P<key>(?:(?:[A-Za-z0-9_-]+)\.)*(?:username|password|passwd|pwd|"
    rb"credential|api[-_]?key|access[-_]?key|secret[-_]?access[-_]?key|"
    rb"access[-_]?key[-_]?(?:id|secret)|secret[-_]?id|secret[-_]?key|private[-_]?key|"
    rb"access[-_]?token|refresh[-_]?token|auth[-_]?token|client[-_]?token|"
    rb"client[-_]?secret|secret|authorization)|"
    rb"(?:[A-Z][A-Z0-9_]*_)?(?:USERNAME|PASSWORD|PASSWD|API_KEY|ACCESS_TOKEN|"
    rb"REFRESH_TOKEN|AUTH_TOKEN|CLIENT_SECRET))"
    rb"[ \t]*[:=][ \t]*(?:\"(?P<double>[^\"\r\n]*)\"|"
    rb"'(?P<single>[^'\r\n]*)'|(?P<bare>[^\r\n#;]*?))[ \t]*(?:[#;].*)?$"
)
RUNTIME_JAVA_FIELD_CREDENTIAL = re.compile(
    rb"(?im)^[ \t]*(?:(?:public|protected|private|static|final|volatile|transient)"
    rb"[ \t]+)*(?:String|char[ \t]*\[[ \t]*\])[ \t]+"
    rb"(?P<key>username|password|passwd|pwd|credential|apiKey|accessKey|"
    rb"secretAccessKey|accessKeyId|accessKeySecret|secretId|secretKey|privateKey|"
    rb"accessToken|refreshToken|authToken|clientToken|"
    rb"clientSecret|secret|authorization)[ \t]*=[ \t]*"
    rb"(?:\"(?P<double>[^\"\r\n]*)\"|'(?P<single>[^'\r\n]*)')"
)


def scan_runtime_source_credentials(path: str, data: bytes) -> None:
    data = data.replace(b"\r\n", b"\n").replace(b"\r", b"\n")
    cursor = 0
    while cursor < len(data):
        start = data.find(b"${", cursor)
        if start < 0:
            break
        parsed = parse_environment_placeholder(data, start)
        if parsed is None:
            cursor = start + 2
            continue
        end, name, default = parsed
        if CREDENTIAL_NAME.search(name) is not None:
            require(
                default in (None, b""),
                "E_SECRET",
                f"runtime source credential placeholder has a literal default: {path}",
            )
        cursor = end
    for pattern in (RUNTIME_LINE_CREDENTIAL, RUNTIME_JAVA_FIELD_CREDENTIAL):
        for match in pattern.finditer(data):
            if pattern is RUNTIME_LINE_CREDENTIAL and payload_extension(path) == "java":
                stripped_line = match.group(0).lstrip()
                if not stripped_line.startswith((b"*", b"//", b"#")):
                    continue
            value = match.group("double")
            if value is None:
                value = match.group("single")
            if value is None:
                value = match.groupdict().get("bare")
            assert value is not None
            value = value.strip()
            if value.endswith(b","):
                value = value[:-1].rstrip()
            require(
                safe_runtime_credential_value(value),
                "E_SECRET",
                f"runtime source contains a literal credential assignment: {path}",
            )


def scan_runtime_source_payload(
    path: str, data: bytes, contract: dict[str, object]
) -> None:
    extension = payload_extension(path)
    if extension == "java":
        scan_runtime_source_credentials(path, data)
        inspected = sanitize_environment_placeholders(
            path, data, contract, CredentialScanBudget()
        )
        for row in sensitive_policy(contract)["patterns"]:
            assert isinstance(row, dict)
            if row["id"] not in {
                "bearer-token",
                "credentialed-uri",
                "jdbc-query-credential",
                "password-cli-option",
            }:
                continue
            pattern = row["regex"]
            assert isinstance(pattern, str)
            require(
                re.search(pattern.encode("ascii"), inspected) is None,
                "E_SECRET",
                f"runtime Java source contains credential-shaped text: {path}",
            )
        return
    scan_sensitive_payload(path, data, contract)
    if declared_text_path(path, contract) or is_text_payload(path, data, contract, False):
        scan_runtime_source_credentials(path, data)


def run_git(repo: Path, arguments: list[str], label: str) -> bytes:
    forbidden = [name for name in os.environ if name.startswith("GIT_") and name != "GIT_PAGER"]
    require(not forbidden, "E_GIT_ENV", f"ambient Git control is forbidden: {sorted(forbidden)}")
    environment = {
        "LC_ALL": "C",
        "PATH": os.environ.get("PATH", ""),
    }
    try:
        process = subprocess.run(
            ["git", "-C", os.fspath(repo), *arguments],
            cwd=repo,
            env=environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as error:
        reject("E_GIT", f"cannot run {label}: {error}")
    require(
        process.returncode == 0 and not process.stderr,
        "E_GIT",
        f"{label} failed with exit {process.returncode}",
    )
    return process.stdout


def runtime_source_receipt(repo_path: Path) -> dict[str, object]:
    contract, _, contract_sha256 = validate_contract()
    repo = real_directory(repo_path, "runtime source repository")
    real_directory(repo / ".git", "runtime source Git metadata")
    source_roots = [f"{module}/src/main" for module in RUNTIME_SOURCE_MODULES]
    tracked_raw = run_git(repo, ["ls-files", "-z", "--", *source_roots], "runtime source inventory")
    try:
        tracked_rows = tracked_raw.decode("utf-8", errors="strict").split("\0")
    except UnicodeDecodeError as error:
        reject("E_GIT", f"runtime source inventory is not UTF-8: {error}")
    if tracked_rows and tracked_rows[-1] == "":
        tracked_rows.pop()
    tracked = {
        normalize_relative_path(path, "tracked runtime source path")
        for path in tracked_rows
    }
    require(
        len(tracked) == len(tracked_rows),
        "E_SOURCE_SET",
        "tracked runtime source inventory contains duplicates",
    )
    observed: dict[str, dict[str, object]] = {}
    folded: dict[str, str] = {}
    inodes: dict[tuple[int, int], str] = {}
    total_bytes = 0

    def visit(root: Path, relative_root: str, directory: Path, prefix: str) -> int:
        nonlocal total_bytes
        try:
            children = sorted(os.scandir(directory), key=lambda row: row.name.encode("utf-8"))
        except OSError as error:
            reject("E_DIRECTORY", f"cannot scan runtime source {directory}: {error}")
        files = 0
        for child in children:
            local = f"{prefix}/{child.name}" if prefix else child.name
            relative = normalize_relative_path(
                f"{relative_root}/{local}", "runtime source path"
            )
            alias = relative.casefold()
            require(
                alias not in folded,
                "E_PATH_COLLISION",
                f"runtime source path aliases collide: {folded.get(alias)}, {relative}",
            )
            folded[alias] = relative
            try:
                metadata = os.lstat(child.path)
            except OSError as error:
                reject("E_FILE_RACE", f"cannot inspect runtime source {relative}: {error}")
            require(
                not stat.S_ISLNK(metadata.st_mode),
                "E_SYMLINK",
                f"symlink in runtime source closure: {relative}",
            )
            if stat.S_ISDIR(metadata.st_mode):
                files += visit(root, relative_root, Path(child.path), local)
                continue
            require(
                stat.S_ISREG(metadata.st_mode),
                "E_SPECIAL",
                f"special file in runtime source closure: {relative}",
            )
            identity = (metadata.st_dev, metadata.st_ino)
            require(
                identity not in inodes,
                "E_HARDLINK",
                f"hard-linked runtime source files are forbidden: {inodes.get(identity)}, {relative}",
            )
            inodes[identity] = relative
            raw, snapshot = secure_regular_snapshot(
                Path(child.path),
                f"runtime source {relative}",
                maximum_payload_file_bytes(contract),
            )
            require(
                (snapshot.st_dev, snapshot.st_ino, snapshot.st_size, snapshot.st_mtime_ns)
                == (metadata.st_dev, metadata.st_ino, metadata.st_size, metadata.st_mtime_ns),
                "E_FILE_RACE",
                f"runtime source changed during scan: {relative}",
            )
            scan_runtime_source_payload(relative, raw, contract)
            total_bytes += len(raw)
            require(
                total_bytes <= int(archive_limits(contract)["max_outer_uncompressed_bytes"]),
                "E_OUTER_TOTAL_SIZE",
                "runtime source closure exceeds byte budget",
            )
            observed[relative] = {
                "path": relative,
                "sha256": sha256_bytes(raw),
                "size": len(raw),
            }
            files += 1
        return files

    for module, relative_root in zip(RUNTIME_SOURCE_MODULES, source_roots, strict=True):
        root = real_directory(repo / PurePosixPath(relative_root), f"runtime source root {module}")
        module_files = visit(root, relative_root, root, "")
        require(module_files > 0, "E_SOURCE_SET", f"runtime source root is empty: {module}")
    actual = set(observed)
    require(
        actual == tracked,
        "E_SOURCE_SET",
        f"runtime source set differs: missing={sorted(tracked - actual)} extra={sorted(actual - tracked)}",
    )
    rows = [observed[path] for path in sorted(observed, key=lambda value: value.encode("utf-8"))]
    head = run_git(repo, ["rev-parse", "--verify", "HEAD^{commit}"], "runtime source HEAD").decode("ascii").strip()
    require(re.fullmatch(r"[0-9a-f]{40}", head) is not None, "E_GIT", "runtime source HEAD is invalid")
    strict_policy = {
        "allowed": ["empty", "null", "redacted", "environment-without-literal-default"],
        "forbidden_markers": ["default", "fixture", "your"],
        "scope": "exact-launcher-runtime-source-closure",
    }
    return {
        "bytes": sum(int(row["size"]) for row in rows),
        "command": "scan-runtime-source",
        "contract_sha256": contract_sha256,
        "files": len(rows),
        "git_head": head,
        "kind": "v934-runtime-source-sensitive-scan",
        "module_count": len(RUNTIME_SOURCE_MODULES),
        "modules": list(RUNTIME_SOURCE_MODULES),
        "schema_version": 1,
        "set_sha256": sha256_bytes(canonical_json(rows)),
        "status": "passed",
        "strict_policy_sha256": sha256_bytes(canonical_json(strict_policy)),
    }


def validate_contract() -> tuple[dict[str, object], bytes, str]:
    data = secure_regular_bytes(CONTRACT_PATH, "release artifact contract", 1024 * 1024)
    value = load_json_bytes(data, "release artifact contract")
    exact_keys(
        value,
        (
            "schema_version",
            "kind",
            "archive",
            "manifests",
            "path_policy",
            "sensitive_text_policy",
        ),
        "E_CONTRACT",
        "contract",
    )
    require(value["schema_version"] == 1, "E_CONTRACT", "contract schema version differs")
    require(value["kind"] == "v934-release-artifact-contract", "E_CONTRACT", "contract kind differs")
    archive = value["archive"]
    manifests = value["manifests"]
    path_policy = value["path_policy"]
    sensitive = value["sensitive_text_policy"]
    require(
        type(archive) is dict
        and type(manifests) is dict
        and type(path_policy) is dict
        and type(sensitive) is dict,
        "E_CONTRACT",
        "contract sections must be objects",
    )
    exact_keys(
        archive,
        ("file_name", "format", "gzip_compresslevel", "mtime", "uid", "gid", "user_name", "group_name", "directory_mode", "file_mode", "executable_mode"),
        "E_CONTRACT",
        "archive contract",
    )
    exact_keys(
        manifests,
        ("metadata_directory", "embedded_contract", "file_manifest", "root_manifest", "archive_manifest", "archive_digest"),
        "E_CONTRACT",
        "manifest contract",
    )
    exact_keys(
        path_policy,
        ("unicode_normalization", "casefold_collision", "backslash", "control_characters", "absolute_or_parent", "symlink_or_special"),
        "E_CONTRACT",
        "path contract",
    )
    exact_keys(
        sensitive,
        (
            "scope",
            "extensions",
            "archive_extensions",
            "content_classifier",
            "class_assignment_policy",
            "normalization_limits",
            "archive_limits",
            "pattern_engine",
            "patterns",
            "safe_values",
        ),
        "E_CONTRACT",
        "sensitive text contract",
    )
    require(
        sensitive["scope"] == "payload-text-and-recursive-zip-archives",
        "E_CONTRACT",
        "sensitive text scope differs",
    )
    require(
        sensitive["extensions"] == list(TEXT_EXTENSIONS),
        "E_CONTRACT",
        "sensitive text extensions differ",
    )
    require(
        sensitive["archive_extensions"] == list(ARCHIVE_EXTENSIONS),
        "E_CONTRACT",
        "sensitive archive extensions differ",
    )
    classifier = sensitive["content_classifier"]
    class_policy = sensitive["class_assignment_policy"]
    normalization = sensitive["normalization_limits"]
    limits = sensitive["archive_limits"]
    require(
        type(classifier) is dict
        and type(class_policy) is dict
        and type(normalization) is dict
        and type(limits) is dict,
        "E_CONTRACT",
        "sensitive classifier/normalization/archive limits must be objects",
    )
    exact_keys(
        classifier,
        (
            "encoding",
            "nul_byte",
            "minimum_printable_ratio",
            "declared_text_binary",
            "max_text_bytes",
        ),
        "E_CONTRACT",
        "content classifier",
    )
    require(
        classifier
        == {
            "encoding": "utf-8-or-latin-1-printable",
            "nul_byte": "binary-unless-declared-text",
            "minimum_printable_ratio": 0.85,
            "declared_text_binary": "reject",
            "max_text_bytes": 134_217_728,
        },
        "E_CONTRACT",
        "content classifier differs",
    )
    require(
        class_policy
        == {
            "first_party_internal_name_prefixes": ["com/foggyframework/"],
            "first_party_field_bindings": "strict-no-literal-credential",
            "third_party_field_bindings": "not-synthesized",
            "all_class_constant_literals": "generic-sensitive-patterns",
        },
        "E_CONTRACT",
        "JVM class assignment policy differs",
    )
    exact_keys(
        normalization,
        (
            "decode_passes",
            "max_class_code_bytes",
            "max_class_utf8_bytes",
            "max_escape_sequences_per_payload",
            "max_key_token_bytes",
            "max_key_tokens_per_payload",
            "max_logical_line_bytes",
            "max_markup_token_bytes",
            "max_normalized_bytes",
            "max_placeholder_depth",
            "max_placeholder_tokens_per_payload",
        ),
        "E_CONTRACT",
        "credential normalization limits",
    )
    require(
        normalization
        == {
            "decode_passes": 1,
            "max_class_code_bytes": 16_777_216,
            "max_class_utf8_bytes": 16_777_216,
            "max_escape_sequences_per_payload": 100_000,
            "max_key_token_bytes": 4_096,
            "max_key_tokens_per_payload": 100_000,
            "max_logical_line_bytes": 1_048_576,
            "max_markup_token_bytes": 1_048_576,
            "max_normalized_bytes": 268_435_456,
            "max_placeholder_depth": 64,
            "max_placeholder_tokens_per_payload": 100_000,
        },
        "E_CONTRACT",
        "credential normalization limits differ",
    )
    exact_keys(
        limits,
        (
            "allowed_compression_methods",
            "max_archive_bytes",
            "max_archive_depth",
            "max_compression_ratio",
            "max_entries_per_archive",
            "max_entry_uncompressed_bytes",
            "max_outer_archive_bytes",
            "max_outer_compression_ratio",
            "max_outer_entries",
            "max_outer_member_uncompressed_bytes",
            "max_outer_uncompressed_bytes",
            "max_payload_file_bytes",
            "max_recursive_entries",
            "max_recursive_uncompressed_bytes",
        ),
        "E_CONTRACT",
        "archive limits",
    )
    require(
        limits
        == {
            "allowed_compression_methods": ["stored", "deflated"],
            "max_archive_bytes": 268_435_456,
            "max_archive_depth": 4,
            "max_compression_ratio": 100,
            "max_entries_per_archive": 10_000,
            "max_entry_uncompressed_bytes": 134_217_728,
            "max_outer_archive_bytes": 1_073_741_824,
            "max_outer_compression_ratio": 200,
            "max_outer_entries": 200_000,
            "max_outer_member_uncompressed_bytes": 268_435_456,
            "max_outer_uncompressed_bytes": 4_294_967_296,
            "max_payload_file_bytes": 268_435_456,
            "max_recursive_entries": 100_000,
            "max_recursive_uncompressed_bytes": 1_073_741_824,
        },
        "E_CONTRACT",
        "archive limits differ",
    )
    require(
        sensitive["pattern_engine"] == "python-re-bytes",
        "E_CONTRACT",
        "sensitive pattern engine differs",
    )
    require(
        sensitive["safe_values"]
        == [
            "null",
            "environment-placeholder-without-literal-default",
            "environment-function-without-literal-default",
            "redacted-marker",
            "ellipsis-example-marker",
            "slf4j-empty-placeholder",
            "fixture-marker",
            "default-marker",
        ],
        "E_CONTRACT",
        "sensitive safe values differ",
    )
    patterns = sensitive["patterns"]
    require(type(patterns) is list and len(patterns) == 8, "E_CONTRACT", "sensitive pattern cardinality differs")
    expected_pattern_ids = [
        "ambient-credential-name",
        "credential-assignment",
        "bearer-token",
        "credentialed-uri",
        "xml-credential-property",
        "environment-or-jvm-credential",
        "jdbc-query-credential",
        "password-cli-option",
    ]
    actual_pattern_ids: list[str] = []
    compiled_patterns: list[re.Pattern[bytes]] = []
    for index, row in enumerate(patterns):
        require(type(row) is dict, "E_CONTRACT", f"sensitive pattern {index} is not an object")
        exact_keys(row, ("id", "regex"), "E_CONTRACT", f"sensitive pattern {index}")
        require(type(row["id"]) is str and type(row["regex"]) is str, "E_CONTRACT", f"sensitive pattern {index} types differ")
        actual_pattern_ids.append(row["id"])
        try:
            compiled_patterns.append(re.compile(row["regex"].encode("ascii")))
        except (UnicodeEncodeError, re.error) as error:
            reject("E_CONTRACT", f"sensitive pattern {index} is invalid: {error}")
    require(actual_pattern_ids == expected_pattern_ids, "E_CONTRACT", "sensitive pattern identities differ")
    positive_probes = (
        b"MYSQL_PWD=fixture-only-value",
        b"Authorization: Bearer fixture.token.12345678",
        b"username=fixture-user",
        b"password=fixture-only-value",
        b"api_key: fixture-only-value",
        b"access-key-id: fixture-only-value",
        b"secret-access-key: fixture-only-value",
        b"accessKeySecret: fixture-only-value",
        b"secret-id: fixture-only-value",
        b"mongodb://fixture-user:fixture-password@localhost:27017/example",
        b"https://fixture-user:fixture-password@example.invalid/resource",
        b"jdbc:mysql://fixture-user@localhost/example",
        b'<property name="password" value="hunter2"/>',
        b"export PASSWORD=hunter2",
        b"java -Dpassword=hunter2 -jar app.jar",
        b"jdbc:mysql://localhost/db?user=fixture&password=hunter2",
        b"jdbc:mysql://localhost/db?user=fixture-user",
        b"--password fixture-only-value",
    )
    safe_probes = (
        b"Resolved demo identity: userId=user_fixture, deptId=dept_fixture, tenantId=tenant_fixture",
        b"MongoClientSettings{credential=null, applicationName=null}",
        b'{"password": null, "authorization": null}',
        b"username=${DATABASE_USER}",
        b"password=${SPRING_DATASOURCE_PASSWORD}",
        b"api-key: ${OPENAI_API_KEY:}",
        b"access-key-id: ${AWS_ACCESS_KEY_ID}",
        b"secret-access-key: ${AWS_SECRET_ACCESS_KEY}",
        b"accessKeySecret: ${ALIYUN_ACCESS_KEY_SECRET}",
        b"secret-id: ${TENCENT_SECRET_ID}",
        b"Authorization: Bearer ${SERVICE_TOKEN}",
        b"MYSQL_PWD=${MYSQL_PWD}",
        b"password=<redacted>",
        b"secret: ******",
        b'<property name="password" value="${DATABASE_PASSWORD}"/>',
        b"export PASSWORD=${DATABASE_PASSWORD}",
        b"java -Dpassword=${DATABASE_PASSWORD} -jar app.jar",
        b"jdbc:mysql://localhost/db?password=${DATABASE_PASSWORD}",
        b"jdbc:mysql://${DATABASE_USER}@localhost/db",
        b"jdbc:mysql://localhost/db?user=${DATABASE_USER}",
    )
    for index, probe in enumerate(positive_probes):
        require(
            any(pattern.search(probe) is not None for pattern in compiled_patterns),
            "E_CONTRACT",
            f"sensitive positive probe {index} did not match",
        )
    for index, probe in enumerate(safe_probes):
        require(
            all(pattern.search(probe) is None for pattern in compiled_patterns),
            "E_CONTRACT",
            f"sensitive safe probe {index} unexpectedly matched",
        )
    require(archive["format"] == "pax", "E_CONTRACT", "archive format differs")
    require(type(archive["gzip_compresslevel"]) is int and archive["gzip_compresslevel"] == 9, "E_CONTRACT", "gzip level differs")
    for field in ("mtime", "uid", "gid"):
        require(type(archive[field]) is int and archive[field] == 0, "E_CONTRACT", f"archive {field} differs")
    require(archive["user_name"] == archive["group_name"] == "", "E_CONTRACT", "archive owner names differ")
    for field, expected in (("directory_mode", "0755"), ("file_mode", "0644"), ("executable_mode", "0755")):
        require(archive[field] == expected, "E_CONTRACT", f"archive {field} differs")
    for field in manifests:
        normalize_relative_path(str(manifests[field]), f"manifest path {field}")
    require(str(manifests["archive_manifest"]).find("/") < 0, "E_CONTRACT", "outer manifest must be a basename")
    require(str(manifests["archive_digest"]).find("/") < 0, "E_CONTRACT", "digest must be a basename")
    require(str(archive["file_name"]).find("/") < 0, "E_CONTRACT", "archive must be a basename")
    return value, data, sha256_bytes(data)


def summary(entries: list[Entry]) -> dict[str, int]:
    files = [entry for entry in entries if entry.kind == "file"]
    directories = [entry for entry in entries if entry.kind == "directory"]
    return {
        "bytes": sum(entry.size for entry in files),
        "directories": len(directories),
        "entries": len(entries),
        "files": len(files),
    }


def file_manifest_value(entries: list[Entry]) -> dict[str, object]:
    return {
        "entries": [entry.manifest_value() for entry in entries],
        "kind": "v934-release-file-manifest",
        "order": "utf8-bytewise",
        "schema_version": 1,
        "summary": summary(entries),
    }


def metadata_directories(contract: dict[str, object]) -> list[str]:
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    paths = (
        str(manifests["embedded_contract"]),
        str(manifests["file_manifest"]),
        str(manifests["root_manifest"]),
    )
    directories: set[str] = set()
    for value in paths:
        parent = PurePosixPath(value).parent
        while parent != PurePosixPath("."):
            directories.add(parent.as_posix())
            parent = parent.parent
    return sorted(directories, key=path_sort)


def root_manifest_value(
    contract: dict[str, object],
    contract_data: bytes,
    contract_sha: str,
    file_manifest_data: bytes,
    entries: list[Entry],
    jar: Entry,
) -> dict[str, object]:
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    archive = contract["archive"]
    assert isinstance(archive, dict)
    return {
        "archive_policy": {
            "directory_mode": archive["directory_mode"],
            "executable_mode": archive["executable_mode"],
            "file_mode": archive["file_mode"],
            "format": archive["format"],
            "gid": archive["gid"],
            "group_name": archive["group_name"],
            "gzip_compresslevel": archive["gzip_compresslevel"],
            "mtime": archive["mtime"],
            "uid": archive["uid"],
            "user_name": archive["user_name"],
        },
        "contract": {
            "path": manifests["embedded_contract"],
            "sha256": contract_sha,
            "size": len(contract_data),
        },
        "file_manifest": {
            "path": manifests["file_manifest"],
            "sha256": sha256_bytes(file_manifest_data),
            "size": len(file_manifest_data),
        },
        "jar": {
            "path": jar.path,
            "sha256": jar.sha256,
            "size": jar.size,
        },
        "kind": "v934-release-root-manifest",
        "metadata": {
            "directories": metadata_directories(contract),
            "root_manifest": manifests["root_manifest"],
        },
        "payload": summary(entries),
        "schema_version": 1,
    }


def tar_info(name: str, kind: str, mode: int, size: int, contract: dict[str, object]) -> tarfile.TarInfo:
    archive = contract["archive"]
    assert isinstance(archive, dict)
    value = tarfile.TarInfo(name)
    value.type = tarfile.DIRTYPE if kind == "directory" else tarfile.REGTYPE
    value.mode = mode
    value.uid = int(archive["uid"])
    value.gid = int(archive["gid"])
    value.uname = str(archive["user_name"])
    value.gname = str(archive["group_name"])
    value.mtime = int(archive["mtime"])
    value.size = 0 if kind == "directory" else size
    value.pax_headers = {}
    return value


def verified_source(entry: Entry, contract: dict[str, object]) -> BinaryIO:
    assert entry.source is not None
    data, snapshot = secure_regular_snapshot(
        entry.source, entry.path, maximum_payload_file_bytes(contract)
    )
    scan_sensitive_payload(entry.path, data, contract)
    require(len(data) == entry.size, "E_SOURCE_DRIFT", f"source size changed: {entry.path}")
    require(sha256_bytes(data) == entry.sha256, "E_SOURCE_DRIFT", f"source hash changed: {entry.path}")
    require(snapshot.st_mtime_ns == entry.mtime_ns, "E_SOURCE_DRIFT", f"source mtime changed: {entry.path}")
    return io.BytesIO(data)


def write_archive(
    temporary: Path,
    contract: dict[str, object],
    entries: list[Entry],
    metadata: dict[str, bytes],
) -> None:
    archive = contract["archive"]
    assert isinstance(archive, dict)
    directory_mode = int(str(archive["directory_mode"]), 8)
    file_mode = int(str(archive["file_mode"]), 8)
    payload = {entry.path: entry for entry in entries}
    member_names = set(payload) | set(metadata) | set(metadata_directories(contract))
    with open(temporary, "xb") as raw:
        with gzip.GzipFile(
            filename="",
            mode="wb",
            compresslevel=int(archive["gzip_compresslevel"]),
            fileobj=raw,
            mtime=int(archive["mtime"]),
        ) as compressed:
            with tarfile.open(fileobj=compressed, mode="w", format=tarfile.PAX_FORMAT) as bundle:
                for name in sorted(member_names, key=path_sort):
                    if name in metadata:
                        data = metadata[name]
                        bundle.addfile(tar_info(name, "file", file_mode, len(data), contract), io.BytesIO(data))
                    elif name in payload:
                        entry = payload[name]
                        source = verified_source(entry, contract) if entry.kind == "file" else None
                        try:
                            bundle.addfile(tar_info(name, entry.kind, entry.mode, entry.size, contract), source)
                        finally:
                            if source is not None:
                                source.close()
                    else:
                        bundle.addfile(tar_info(name, "directory", directory_mode, 0, contract))
        raw.flush()
        os.fsync(raw.fileno())


def fsync_directory(path: Path, label: str) -> None:
    directory = real_directory(path, label)
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_DIRECTORY", 0)
    try:
        descriptor = os.open(directory, flags)
    except OSError as error:
        reject("E_OUTPUT", f"cannot open {label} for fsync: {directory}: {error}")
    try:
        current = os.fstat(descriptor)
        require(stat.S_ISDIR(current.st_mode), "E_OUTPUT", f"{label} descriptor is not a directory")
        os.fsync(descriptor)
    except OSError as error:
        reject("E_OUTPUT", f"cannot fsync {label}: {directory}: {error}")
    finally:
        os.close(descriptor)


def atomic_publish(source: Path, destination: Path) -> None:
    parent = real_directory(destination.parent, "publish parent")
    require(source.parent == parent, "E_OUTPUT", "publish source and destination must share one parent")
    require(not destination.exists() and not destination.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {destination}")
    try:
        os.link(source, destination, follow_symlinks=False)
    except FileExistsError:
        reject("E_OUTPUT_EXISTS", f"output exists: {destination}")
    except OSError as error:
        reject("E_OUTPUT", f"cannot publish {destination}: {error}")
    fsync_directory(parent, "publish parent after link")
    try:
        os.unlink(source)
    except OSError as error:
        reject("E_OUTPUT", f"cannot remove published temporary file {source}: {error}")
    fsync_directory(parent, "publish parent after temporary unlink")


def write_new_file(path: Path, data: bytes, mode: int = 0o644) -> None:
    require(not path.exists() and not path.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {path}")
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
    temporary = Path(temporary_name)
    try:
        os.fchmod(descriptor, mode)
        view = memoryview(data)
        while view:
            written = os.write(descriptor, view)
            require(written > 0, "E_OUTPUT", f"short write: {path}")
            view = view[written:]
        os.fsync(descriptor)
        os.close(descriptor)
        descriptor = -1
        atomic_publish(temporary, path)
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()


def build_bundle(staging_root: Path, output_dir: Path, jar_relative_path: str) -> dict[str, object]:
    contract, contract_data, contract_sha = validate_contract()
    staging = real_directory(staging_root, "release staging root")
    output = prepare_output_directory(output_dir)
    require(output != staging and staging not in output.parents, "E_OUTPUT", "output directory is inside staging root")
    jar_path = normalize_relative_path(jar_relative_path, "tested JAR path")
    require(jar_path.lower().endswith(".jar"), "E_JAR_PATH", "tested JAR path must end in .jar")
    entries = scan_tree(staging, contract, allow_metadata=False)
    entry_map = {entry.path: entry for entry in entries}
    jar = entry_map.get(jar_path)
    require(jar is not None and jar.kind == "file", "E_JAR_MISSING", f"tested JAR is absent: {jar_path}")
    assert jar is not None
    file_manifest = canonical_json(file_manifest_value(entries))
    root_manifest = canonical_json(
        root_manifest_value(contract, contract_data, contract_sha, file_manifest, entries, jar)
    )
    manifests = contract["manifests"]
    archive_contract = contract["archive"]
    assert isinstance(manifests, dict) and isinstance(archive_contract, dict)
    metadata_payloads = (contract_data, file_manifest, root_manifest)
    limits = archive_limits(contract)
    outer_member_count = len(entries) + len(metadata_directories(contract)) + len(metadata_payloads)
    outer_uncompressed_bytes = sum(
        entry.size for entry in entries if entry.kind == "file"
    ) + sum(len(data) for data in metadata_payloads)
    require(
        outer_member_count <= int(limits["max_outer_entries"]),
        "E_OUTER_ENTRIES",
        "release archive member count exceeds limit",
    )
    require(
        all(
            len(data) <= int(limits["max_outer_member_uncompressed_bytes"])
            for data in metadata_payloads
        ),
        "E_OUTER_MEMBER_SIZE",
        "release archive metadata exceeds member size limit",
    )
    require(
        outer_uncompressed_bytes <= int(limits["max_outer_uncompressed_bytes"]),
        "E_OUTER_TOTAL_SIZE",
        "release archive uncompressed bytes exceed limit",
    )
    archive_path = output / str(archive_contract["file_name"])
    outer_path = output / str(manifests["archive_manifest"])
    digest_path = output / str(manifests["archive_digest"])
    for target in (archive_path, outer_path, digest_path):
        require(not target.exists() and not target.is_symlink(), "E_OUTPUT_EXISTS", f"output exists: {target}")
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{archive_path.name}.", suffix=".tmp", dir=output)
    os.close(descriptor)
    temporary = Path(temporary_name)
    temporary.unlink()
    try:
        write_archive(
            temporary,
            contract,
            entries,
            {
                str(manifests["embedded_contract"]): contract_data,
                str(manifests["file_manifest"]): file_manifest,
                str(manifests["root_manifest"]): root_manifest,
            },
        )
        validate_gzip_tar_framing(temporary, contract)
        archive_sha, archive_size = file_digest(temporary, "staged release archive")
        require(
            archive_size <= int(limits["max_outer_archive_bytes"]),
            "E_OUTER_ARCHIVE_SIZE",
            "release archive compressed bytes exceed limit",
        )
        require(
            outer_uncompressed_bytes
            <= archive_size * int(limits["max_outer_compression_ratio"]),
            "E_OUTER_BOMB",
            "release archive compression ratio exceeds limit",
        )
        atomic_publish(temporary, archive_path)
    finally:
        if temporary.exists() or temporary.is_symlink():
            temporary.unlink()
    outer = {
        "archive": {
            "file_name": archive_path.name,
            "sha256": archive_sha,
            "size": archive_size,
        },
        "contract": {
            "path": str(manifests["embedded_contract"]),
            "sha256": contract_sha,
            "size": len(contract_data),
        },
        "file_manifest": {
            "path": str(manifests["file_manifest"]),
            "sha256": sha256_bytes(file_manifest),
            "size": len(file_manifest),
        },
        "jar": {"path": jar.path, "sha256": jar.sha256, "size": jar.size},
        "kind": "v934-release-archive-manifest",
        "payload": summary(entries),
        "root_manifest": {
            "path": str(manifests["root_manifest"]),
            "sha256": sha256_bytes(root_manifest),
            "size": len(root_manifest),
        },
        "schema_version": 1,
    }
    outer_data = canonical_json(outer)
    write_new_file(outer_path, outer_data)
    write_new_file(digest_path, f"{archive_sha}  {archive_path.name}\n".encode("ascii"))
    return {
        "archive": str(archive_path),
        "archive_manifest": str(outer_path),
        "archive_manifest_sha256": sha256_bytes(outer_data),
        "archive_sha256": archive_sha,
        "archive_size": archive_size,
        "command": "build",
        "file_manifest_sha256": sha256_bytes(file_manifest),
        "jar_sha256": jar.sha256,
        "payload": summary(entries),
        "root_manifest_sha256": sha256_bytes(root_manifest),
        "status": "passed",
    }


def validate_file_manifest(value: dict[str, object], contract: dict[str, object]) -> tuple[list[Entry], dict[str, int]]:
    exact_keys(value, ("entries", "kind", "order", "schema_version", "summary"), "E_FILE_MANIFEST", "file manifest")
    require(value["schema_version"] == 1 and value["kind"] == "v934-release-file-manifest" and value["order"] == "utf8-bytewise", "E_FILE_MANIFEST", "file manifest identity differs")
    rows = value["entries"]
    supplied_summary = value["summary"]
    require(type(rows) is list and type(supplied_summary) is dict, "E_FILE_MANIFEST", "file manifest rows/summary types differ")
    parsed: list[Entry] = []
    seen: dict[str, str] = {}
    for index, row in enumerate(rows):
        require(type(row) is dict, "E_FILE_MANIFEST", f"file manifest row {index} is not an object")
        kind = row.get("type")
        if kind == "directory":
            exact_keys(row, ("mode", "path", "type"), "E_FILE_MANIFEST", f"directory row {index}")
        elif kind == "file":
            exact_keys(
                row,
                ("mode", "mtime_ns", "path", "sha256", "size", "type"),
                "E_FILE_MANIFEST",
                f"file row {index}",
            )
        else:
            reject("E_FILE_MANIFEST", f"file manifest row {index} has invalid type")
        path = normalize_relative_path(row["path"], f"file manifest path {index}")  # type: ignore[arg-type]
        register_path(path, seen)
        mode_text = row["mode"]
        require(type(mode_text) is str and re.fullmatch(r"0[0-7]{3}", mode_text) is not None, "E_FILE_MANIFEST", f"row mode is invalid: {path}")
        mode = int(mode_text, 8)
        allowed_mode = normalized_mode(mode, contract, kind == "directory")
        require(mode == allowed_mode, "E_FILE_MANIFEST", f"row mode is not normalized: {path}")
        if kind == "file":
            require(type(row["size"]) is int and row["size"] >= 0, "E_FILE_MANIFEST", f"row size is invalid: {path}")
            require(type(row["sha256"]) is str and HEX64.fullmatch(row["sha256"]) is not None, "E_FILE_MANIFEST", f"row hash is invalid: {path}")
            require(
                type(row["mtime_ns"]) is int
                and 0 <= row["mtime_ns"] <= 9_223_372_036_854_775_807,
                "E_FILE_MANIFEST",
                f"row mtime is invalid: {path}",
            )
            parsed.append(
                Entry(path, kind, mode, row["size"], row["sha256"], row["mtime_ns"])
            )  # type: ignore[arg-type]
        else:
            parsed.append(Entry(path, kind, mode))
    require([entry.path for entry in parsed] == sorted((entry.path for entry in parsed), key=path_sort), "E_FILE_MANIFEST", "file manifest rows are not sorted")
    computed = summary(parsed)
    require(supplied_summary == computed, "E_FILE_MANIFEST", "file manifest summary differs")
    return parsed, computed


def validate_root_manifest(
    value: dict[str, object],
    contract: dict[str, object],
    contract_data: bytes,
    file_manifest_data: bytes,
    entries: list[Entry],
) -> dict[str, object]:
    exact_keys(value, ("archive_policy", "contract", "file_manifest", "jar", "kind", "metadata", "payload", "schema_version"), "E_ROOT_MANIFEST", "root manifest")
    require(value["schema_version"] == 1 and value["kind"] == "v934-release-root-manifest", "E_ROOT_MANIFEST", "root manifest identity differs")
    manifests = contract["manifests"]
    archive = contract["archive"]
    assert isinstance(manifests, dict) and isinstance(archive, dict)
    expected_policy = {
        "directory_mode": archive["directory_mode"], "executable_mode": archive["executable_mode"],
        "file_mode": archive["file_mode"], "format": archive["format"], "gid": archive["gid"],
        "group_name": archive["group_name"], "gzip_compresslevel": archive["gzip_compresslevel"],
        "mtime": archive["mtime"], "uid": archive["uid"], "user_name": archive["user_name"],
    }
    require(value["archive_policy"] == expected_policy, "E_ROOT_MANIFEST", "archive policy differs")
    require(value["payload"] == summary(entries), "E_ROOT_MANIFEST", "root payload summary differs")
    expected_contract = {"path": manifests["embedded_contract"], "sha256": sha256_bytes(contract_data), "size": len(contract_data)}
    expected_files = {"path": manifests["file_manifest"], "sha256": sha256_bytes(file_manifest_data), "size": len(file_manifest_data)}
    require(value["contract"] == expected_contract, "E_ROOT_MANIFEST", "embedded contract binding differs")
    require(value["file_manifest"] == expected_files, "E_ROOT_MANIFEST", "file manifest binding differs")
    metadata = value["metadata"]
    require(type(metadata) is dict, "E_ROOT_MANIFEST", "root metadata is not an object")
    require(metadata == {"directories": metadata_directories(contract), "root_manifest": manifests["root_manifest"]}, "E_ROOT_MANIFEST", "root metadata paths differ")
    jar = value["jar"]
    require(type(jar) is dict, "E_ROOT_MANIFEST", "root JAR binding is not an object")
    exact_keys(jar, ("path", "sha256", "size"), "E_ROOT_MANIFEST", "root JAR binding")
    jar_path = normalize_relative_path(jar["path"], "root JAR path")  # type: ignore[arg-type]
    require(jar_path.lower().endswith(".jar"), "E_JAR_PATH", "root JAR path must end in .jar")
    require(type(jar["sha256"]) is str and HEX64.fullmatch(jar["sha256"]) is not None, "E_ROOT_MANIFEST", "root JAR hash is invalid")
    require(type(jar["size"]) is int and jar["size"] >= 0, "E_ROOT_MANIFEST", "root JAR size is invalid")
    expected_entry = {entry.path: entry for entry in entries}.get(jar_path)
    require(expected_entry is not None and expected_entry.kind == "file", "E_JAR_MISSING", "root JAR is absent from file manifest")
    require(jar == {"path": expected_entry.path, "sha256": expected_entry.sha256, "size": expected_entry.size}, "E_JAR_BINDING", "root JAR differs from file manifest")
    return jar


def load_outer_manifest(path: Path, contract: dict[str, object]) -> tuple[dict[str, object], bytes]:
    data = secure_regular_bytes(path, "archive manifest", 4 * 1024 * 1024)
    value = load_json_bytes(data, "archive manifest")
    exact_keys(value, ("archive", "contract", "file_manifest", "jar", "kind", "payload", "root_manifest", "schema_version"), "E_ARCHIVE_MANIFEST", "archive manifest")
    require(value["schema_version"] == 1 and value["kind"] == "v934-release-archive-manifest", "E_ARCHIVE_MANIFEST", "archive manifest identity differs")
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    for field, expected_path in (("contract", manifests["embedded_contract"]), ("file_manifest", manifests["file_manifest"]), ("root_manifest", manifests["root_manifest"])):
        binding = value[field]
        require(type(binding) is dict, "E_ARCHIVE_MANIFEST", f"{field} binding is not an object")
        exact_keys(binding, ("path", "sha256", "size"), "E_ARCHIVE_MANIFEST", f"{field} binding")
        require(binding["path"] == expected_path, "E_ARCHIVE_MANIFEST", f"{field} path differs")
        require(type(binding["sha256"]) is str and HEX64.fullmatch(binding["sha256"]) is not None, "E_ARCHIVE_MANIFEST", f"{field} hash is invalid")
        require(type(binding["size"]) is int and binding["size"] >= 0, "E_ARCHIVE_MANIFEST", f"{field} size is invalid")
    archive_binding = value["archive"]
    require(type(archive_binding) is dict, "E_ARCHIVE_MANIFEST", "archive binding is not an object")
    exact_keys(archive_binding, ("file_name", "sha256", "size"), "E_ARCHIVE_MANIFEST", "archive binding")
    require(type(archive_binding["sha256"]) is str and HEX64.fullmatch(archive_binding["sha256"]) is not None, "E_ARCHIVE_MANIFEST", "archive hash is invalid")
    require(type(archive_binding["size"]) is int and archive_binding["size"] > 0, "E_ARCHIVE_MANIFEST", "archive size is invalid")
    return value, data


def expected_member_map(
    contract: dict[str, object],
    contract_data: bytes,
    file_manifest_data: bytes,
    root_manifest_data: bytes,
    entries: list[Entry],
) -> dict[str, Entry]:
    archive = contract["archive"]
    manifests = contract["manifests"]
    assert isinstance(archive, dict) and isinstance(manifests, dict)
    directory_mode = int(str(archive["directory_mode"]), 8)
    file_mode = int(str(archive["file_mode"]), 8)
    result = {entry.path: entry for entry in entries}
    for directory in metadata_directories(contract):
        result.setdefault(directory, Entry(directory, "directory", directory_mode))
    for path, data in (
        (str(manifests["embedded_contract"]), contract_data),
        (str(manifests["file_manifest"]), file_manifest_data),
        (str(manifests["root_manifest"]), root_manifest_data),
    ):
        result[path] = Entry(path, "file", file_mode, len(data), sha256_bytes(data))
    return result


def compare_entries(
    actual: dict[str, Entry],
    expected: dict[str, Entry],
    jar: dict[str, object],
    enforce_mtime: bool,
) -> None:
    jar_path = str(jar["path"])
    actual_jar = actual.get(jar_path)
    require(actual_jar is not None and actual_jar.kind == "file", "E_JAR_MISSING", "tested JAR is missing")
    require(actual_jar.size == jar["size"] and actual_jar.sha256 == jar["sha256"], "E_JAR_HASH", "tested JAR hash/size differs")
    require(set(actual) == set(expected), "E_ENTRY_SET", f"entry set differs: missing={sorted(set(expected) - set(actual))} extra={sorted(set(actual) - set(expected))}")
    for path in sorted(expected, key=path_sort):
        left = actual[path]
        right = expected[path]
        require(left.kind == right.kind, "E_ENTRY_TYPE", f"entry type differs: {path}")
        require(left.mode == right.mode, "E_ENTRY_MODE", f"entry mode differs: {path}")
        if right.kind == "file":
            require(left.size == right.size, "E_FILE_SIZE", f"file size differs: {path}")
            require(left.sha256 == right.sha256, "E_FILE_HASH", f"file hash differs: {path}")
            if enforce_mtime and right.mtime_ns is not None:
                require(
                    left.mtime_ns == right.mtime_ns,
                    "E_FILE_MTIME",
                    f"file mtime differs: {path}",
                )


def verify_inner(
    actual: dict[str, Entry],
    metadata_bytes: dict[str, bytes],
    contract: dict[str, object],
    contract_data: bytes,
    expected_root_sha: str,
    outer: dict[str, object] | None,
    enforce_mtime: bool,
) -> dict[str, object]:
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    embedded_path = str(manifests["embedded_contract"])
    file_path = str(manifests["file_manifest"])
    root_path = str(manifests["root_manifest"])
    for path in (embedded_path, file_path, root_path):
        require(path in metadata_bytes, "E_MANIFEST_MISSING", f"missing inner metadata: {path}")
    embedded = metadata_bytes[embedded_path]
    file_data = metadata_bytes[file_path]
    root_data = metadata_bytes[root_path]
    require(embedded == contract_data, "E_CONTRACT_HASH", "embedded release contract differs")
    require(sha256_bytes(root_data) == expected_root_sha, "E_ROOT_MANIFEST_HASH", "root manifest digest differs")
    file_value = load_json_bytes(file_data, "file manifest")
    entries, payload_summary = validate_file_manifest(file_value, contract)
    root_value = load_json_bytes(root_data, "root manifest")
    jar = validate_root_manifest(root_value, contract, contract_data, file_data, entries)
    if outer is not None:
        require(outer["contract"] == root_value["contract"], "E_ARCHIVE_MANIFEST", "outer/inner contract binding differs")
        require(outer["file_manifest"] == root_value["file_manifest"], "E_ARCHIVE_MANIFEST", "outer/inner file manifest binding differs")
        require(outer["root_manifest"] == {"path": root_path, "sha256": sha256_bytes(root_data), "size": len(root_data)}, "E_ARCHIVE_MANIFEST", "outer/inner root manifest binding differs")
        require(outer["jar"] == jar, "E_ARCHIVE_MANIFEST", "outer/inner JAR binding differs")
        require(outer["payload"] == payload_summary, "E_ARCHIVE_MANIFEST", "outer/inner payload summary differs")
    expected = expected_member_map(contract, contract_data, file_data, root_data, entries)
    compare_entries(actual, expected, jar, enforce_mtime)
    return {
        "file_manifest_sha256": sha256_bytes(file_data),
        "jar_sha256": jar["sha256"],
        "payload": payload_summary,
        "root_manifest_sha256": sha256_bytes(root_data),
    }


def validate_tar_member(member: tarfile.TarInfo, contract: dict[str, object], seen: dict[str, str]) -> tuple[str, str, int]:
    try:
        path = normalize_relative_path(member.name, "archive member")
    except ArtifactError as error:
        reject("E_MEMBER_PATH", str(error))
    folded = path.casefold()
    require(folded not in seen, "E_DUPLICATE_MEMBER", f"duplicate/colliding archive member: {seen.get(folded)!r}, {path!r}")
    seen[folded] = path
    archive = contract["archive"]
    assert isinstance(archive, dict)
    require(member.uid == archive["uid"] and member.gid == archive["gid"], "E_MEMBER_OWNER", f"archive owner differs: {path}")
    require(member.uname == archive["user_name"] and member.gname == archive["group_name"], "E_MEMBER_OWNER", f"archive owner name differs: {path}")
    require(member.mtime == archive["mtime"], "E_MEMBER_MTIME", f"archive mtime differs: {path}")
    require(set(member.pax_headers).issubset({"path"}), "E_MEMBER_PAX", f"unexpected PAX headers: {path}")
    if "path" in member.pax_headers:
        expected_pax_paths = {path, path + "/"} if member.isdir() else {path}
        require(
            member.pax_headers["path"] in expected_pax_paths,
            "E_MEMBER_PAX",
            f"PAX path differs: {path}",
        )
    if member.isdir():
        kind = "directory"
        require(member.size == 0, "E_MEMBER_SIZE", f"directory is non-empty: {path}")
        expected_mode = int(str(archive["directory_mode"]), 8)
    elif member.isfile():
        kind = "file"
        expected_mode = int(str(archive["executable_mode"] if member.mode & 0o111 else archive["file_mode"]), 8)
    elif member.issym() or member.islnk():
        reject("E_SYMLINK", f"archive link is forbidden: {path}")
    else:
        reject("E_SPECIAL", f"archive special member is forbidden: {path}")
    require(member.mode == expected_mode, "E_MEMBER_MODE", f"archive mode is not normalized: {path}")
    return path, kind, expected_mode


def stream_member(
    source: BinaryIO,
    destination: Path | None,
    capture: bool = False,
    maximum: int | None = None,
) -> tuple[str, int, bytes | None]:
    digest = hashlib.sha256()
    total = 0
    captured: list[bytes] | None = [] if capture else None
    target: BinaryIO | None = None
    if destination is not None:
        destination.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
        target = open(destination, "xb")
    try:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
            total += len(chunk)
            if maximum is not None:
                require(
                    total <= maximum,
                    "E_OUTER_MEMBER_SIZE",
                    "archive member expanded beyond its enforced limit",
                )
            if target is not None:
                target.write(chunk)
            if captured is not None:
                captured.append(chunk)
        if target is not None:
            target.flush()
            os.fsync(target.fileno())
    finally:
        if target is not None:
            target.close()
    return digest.hexdigest(), total, b"".join(captured) if captured is not None else None


def restore_payload_mtimes(
    root: Path, file_manifest_data: bytes, contract: dict[str, object]
) -> None:
    manifest = load_json_bytes(file_manifest_data, "file manifest for mtime restore")
    entries, _ = validate_file_manifest(manifest, contract)
    for entry in entries:
        if entry.kind != "file":
            continue
        assert entry.mtime_ns is not None
        target = root / PurePosixPath(entry.path)
        try:
            current = os.lstat(target)
        except FileNotFoundError:
            reject("E_FILE_MISSING", f"missing extracted file during mtime restore: {entry.path}")
        require(
            stat.S_ISREG(current.st_mode) and not stat.S_ISLNK(current.st_mode),
            "E_SPECIAL",
            f"extracted mtime target is not a regular file: {entry.path}",
        )
        try:
            os.utime(
                target,
                ns=(entry.mtime_ns, entry.mtime_ns),
                follow_symlinks=False,
            )
        except OSError as error:
            reject("E_FILE_MTIME", f"cannot restore extracted file mtime: {entry.path}: {error}")
        restored = os.lstat(target)
        require(
            restored.st_mtime_ns == entry.mtime_ns,
            "E_FILE_MTIME",
            f"filesystem did not preserve extracted file mtime: {entry.path}",
        )


def inspect_archive(archive_path: Path, outer_path: Path, extract_root: Path | None = None) -> dict[str, object]:
    contract, contract_data, _ = validate_contract()
    outer, _ = load_outer_manifest(outer_path, contract)
    archive_binding = outer["archive"]
    root_binding = outer["root_manifest"]
    assert isinstance(archive_binding, dict) and isinstance(root_binding, dict)
    archive = Path(os.path.abspath(archive_path))
    require(archive.name == archive_binding["file_name"], "E_ARCHIVE_NAME", "archive file name differs")
    digest, size = file_digest(archive, "release archive")
    require(digest == archive_binding["sha256"] and size == archive_binding["size"], "E_ARCHIVE_HASH", "archive digest/size differs")
    limits = archive_limits(contract)
    require(
        size <= int(limits["max_outer_archive_bytes"]),
        "E_OUTER_ARCHIVE_SIZE",
        "release archive compressed bytes exceed limit",
    )
    validate_gzip_tar_framing(archive, contract)
    destination: Path | None = None
    if extract_root is not None:
        destination = Path(os.path.abspath(extract_root))
        require(not destination.exists() and not destination.is_symlink(), "E_EXTRACT_EXISTS", f"extract destination exists: {destination}")
        real_directory(destination.parent, "extract parent")
        destination.mkdir(mode=0o755)
    actual: dict[str, Entry] = {}
    metadata_bytes: dict[str, bytes] = {}
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    metadata_paths = {str(manifests["embedded_contract"]), str(manifests["file_manifest"]), str(manifests["root_manifest"])}
    seen: dict[str, str] = {}
    outer_entries = 0
    outer_uncompressed_bytes = 0
    try:
        with tarfile.open(archive, mode="r:gz") as bundle:
            for member in bundle:
                path, kind, mode = validate_tar_member(member, contract, seen)
                outer_entries += 1
                require(
                    outer_entries <= int(limits["max_outer_entries"]),
                    "E_OUTER_ENTRIES",
                    "release archive member count exceeds limit",
                )
                require(
                    member.size <= int(limits["max_outer_member_uncompressed_bytes"]),
                    "E_OUTER_MEMBER_SIZE",
                    f"release archive member exceeds size limit: {path}",
                )
                outer_uncompressed_bytes += member.size
                require(
                    outer_uncompressed_bytes
                    <= int(limits["max_outer_uncompressed_bytes"]),
                    "E_OUTER_TOTAL_SIZE",
                    "release archive uncompressed bytes exceed limit",
                )
                output = destination / PurePosixPath(path) if destination is not None else None
                if kind == "directory":
                    if output is not None:
                        output.mkdir(mode=0o755, parents=True, exist_ok=True)
                        output.chmod(mode)
                    actual[path] = Entry(path, kind, mode)
                    continue
                source = bundle.extractfile(member)
                require(source is not None, "E_MEMBER_READ", f"cannot read archive member: {path}")
                # Every bounded payload is retained long enough for content/JAR inspection.
                if path in metadata_paths:
                    require(member.size <= 32 * 1024 * 1024, "E_MEMBER_SIZE", f"metadata member exceeds size limit: {path}")
                    hashed, member_size, data = stream_member(
                        source,
                        None,
                        capture=True,
                        maximum=min(
                            32 * 1024 * 1024,
                            int(limits["max_outer_member_uncompressed_bytes"]),
                        ),
                    )
                    assert data is not None
                    metadata_bytes[path] = data
                    if output is not None:
                        output.parent.mkdir(mode=0o755, parents=True, exist_ok=True)
                        write_new_file(output, data, mode)
                else:
                    require(
                        member.size <= maximum_payload_file_bytes(contract),
                        "E_MEMBER_SIZE",
                        f"payload member exceeds size limit: {path}",
                    )
                    hashed, member_size, data = stream_member(
                        source,
                        output,
                        capture=True,
                        maximum=int(limits["max_outer_member_uncompressed_bytes"]),
                    )
                    assert data is not None
                    scan_sensitive_payload(path, data, contract)
                    if output is not None:
                        output.chmod(mode)
                require(member_size == member.size, "E_MEMBER_SIZE", f"archive member size differs: {path}")
                actual[path] = Entry(path, kind, mode, member_size, hashed)
        require(
            outer_uncompressed_bytes
            <= size * int(limits["max_outer_compression_ratio"]),
            "E_OUTER_BOMB",
            "release archive compression ratio exceeds limit",
        )
        result = verify_inner(
            actual,
            metadata_bytes,
            contract,
            contract_data,
            str(root_binding["sha256"]),
            outer,
            enforce_mtime=False,
        )
        if destination is not None:
            restore_payload_mtimes(
                destination,
                metadata_bytes[str(manifests["file_manifest"])],
                contract,
            )
            replay = verify_root(destination, str(root_binding["sha256"]))
            require(replay["root_manifest_sha256"] == result["root_manifest_sha256"], "E_ROOT_REPLAY", "extracted replay differs")
        return {
            "archive_sha256": digest,
            "command": "extract-verify" if destination is not None else "verify-archive",
            **result,
            "status": "passed",
        }
    except (ArtifactError, OSError, tarfile.TarError) as error:
        if destination is not None and destination.exists() and destination.is_dir() and not destination.is_symlink():
            shutil.rmtree(destination)
        if isinstance(error, ArtifactError):
            raise
        reject("E_ARCHIVE", f"cannot read release archive: {error}")


def verify_root(root_path: Path, expected_root_sha: str) -> dict[str, object]:
    require(type(expected_root_sha) is str and HEX64.fullmatch(expected_root_sha) is not None, "E_ROOT_MANIFEST_HASH", "expected root manifest digest is invalid")
    contract, contract_data, _ = validate_contract()
    root = real_directory(root_path, "extracted release root")
    manifests = contract["manifests"]
    assert isinstance(manifests, dict)
    metadata_paths = {str(manifests["embedded_contract"]), str(manifests["file_manifest"]), str(manifests["root_manifest"])}
    scanned = scan_tree(root, contract, allow_metadata=True)
    actual = {entry.path: entry for entry in scanned}
    metadata_bytes: dict[str, bytes] = {}
    for path in metadata_paths:
        entry = actual.get(path)
        require(entry is not None and entry.kind == "file", "E_MANIFEST_MISSING", f"missing extracted metadata: {path}")
        metadata_bytes[path] = secure_regular_bytes(root / PurePosixPath(path), path, 32 * 1024 * 1024)
    result = verify_inner(
        actual,
        metadata_bytes,
        contract,
        contract_data,
        expected_root_sha,
        None,
        enforce_mtime=True,
    )
    return {"command": "verify-root", **result, "status": "passed"}


def clone_extracted(source: Path, target: Path) -> None:
    shutil.copytree(source, target, symlinks=True)


def rewrite_archive(base: Path, destination: Path, mutation: str, contract: dict[str, object]) -> None:
    if mutation in {"gzip-trailing", "gzip-concatenated", "tar-after-eof"}:
        base_data = secure_regular_bytes(base, "base archive for framing mutation")
        if mutation == "gzip-trailing":
            mutated = base_data + b"password=hunter2\nTRAILING"
        elif mutation == "gzip-concatenated":
            extra = io.BytesIO()
            with gzip.GzipFile(
                filename="", mode="wb", compresslevel=9, fileobj=extra, mtime=0
            ) as stream:
                stream.write(b"password=hunter2\n")
            mutated = base_data + extra.getvalue()
        else:
            expanded = bytearray(gzip.decompress(base_data))
            require(
                len(expanded) >= tarfile.RECORDSIZE
                and expanded[-tarfile.BLOCKSIZE :] == b"\x00" * tarfile.BLOCKSIZE,
                "E_SELF_TEST",
                "base tar lacks canonical padding for framing mutation",
            )
            marker = b"password=hunter2\nAFTER_EOF"
            expanded[-tarfile.BLOCKSIZE : -tarfile.BLOCKSIZE + len(marker)] = marker
            output = io.BytesIO()
            with gzip.GzipFile(
                filename="", mode="wb", compresslevel=9, fileobj=output, mtime=0
            ) as stream:
                stream.write(expanded)
            mutated = output.getvalue()
        write_new_file(destination, mutated)
        return
    records: list[tuple[tarfile.TarInfo, bytes | None]] = []
    with tarfile.open(base, "r:gz") as source:
        for member in source:
            data = source.extractfile(member).read() if member.isfile() else None  # type: ignore[union-attr]
            records.append((copy.copy(member), data))
    if mutation == "duplicate":
        records.append((copy.copy(next(member for member, _ in records if member.isfile())), next(data for member, data in records if member.isfile())))
    elif mutation == "traversal":
        value = tar_info("../escape", "file", 0o644, 1, contract)
        records.append((value, b"x"))
    elif mutation == "symlink":
        value = tarfile.TarInfo("forbidden-link")
        archive = contract["archive"]
        assert isinstance(archive, dict)
        value.type = tarfile.SYMTYPE
        value.linkname = "artifacts/app.jar"
        value.mode = 0o644
        value.uid = int(archive["uid"])
        value.gid = int(archive["gid"])
        value.uname = str(archive["user_name"])
        value.gname = str(archive["group_name"])
        value.mtime = int(archive["mtime"])
        records.append((value, None))
    elif mutation == "secret":
        changed = False
        for index, (member, data) in enumerate(records):
            if member.name == "reports/result.json" and member.isfile():
                replacement = b'{"password":"hunter2"}\n'
                member.size = len(replacement)
                records[index] = (member, replacement)
                changed = True
                break
        require(changed, "E_SELF_TEST", "secret mutation target is absent")
    elif mutation == "outer-bomb":
        payload = b"\x00" * (4 * 1024 * 1024)
        value = tar_info(
            "reports/outer-compression-bomb.bin",
            "file",
            0o644,
            len(payload),
            contract,
        )
        records.append((value, payload))
    else:
        reject("E_SELF_TEST", f"unknown archive mutation: {mutation}")
    with open(destination, "xb") as raw:
        archive = contract["archive"]
        assert isinstance(archive, dict)
        with gzip.GzipFile(filename="", mode="wb", compresslevel=9, fileobj=raw, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w", format=tarfile.PAX_FORMAT) as output:
                for member, data in records:
                    output.addfile(member, io.BytesIO(data) if data is not None else None)


def outer_for_mutated_archive(base_outer: Path, archive: Path, output: Path) -> None:
    data = secure_regular_bytes(base_outer, "base outer manifest")
    value = load_json_bytes(data, "base outer manifest")
    digest, size = file_digest(archive, "mutated archive")
    binding = value["archive"]
    assert isinstance(binding, dict)
    binding["file_name"] = archive.name
    binding["sha256"] = digest
    binding["size"] = size
    write_new_file(output, canonical_json(value))


def expect_failure(name: str, code: str, operation: Callable[[], object], results: list[dict[str, str]]) -> None:
    try:
        operation()
    except ArtifactError as error:
        require(error.code == code, "E_SELF_TEST", f"negative {name} expected {code}, got {error.code}: {error}")
        results.append({"case": name, "code": error.code, "status": "passed"})
        return
    reject("E_SELF_TEST", f"negative {name} unexpectedly passed")


def fixture_zip(
    records: list[tuple[str, bytes, int, int]]
) -> bytes:
    output = io.BytesIO()
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(output, mode="w", allowZip64=True) as archive:
            for name, data, unix_mode, compression in records:
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = unix_mode << 16
                info.compress_type = compression
                archive.writestr(info, data)
    return output.getvalue()


def fixture_regular_zip(records: list[tuple[str, bytes]]) -> bytes:
    return fixture_zip(
        [
            (name, data, stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED)
            for name, data in records
        ]
    )


def fixture_class_utf8(value: bytes) -> bytes:
    return b"\x01" + len(value).to_bytes(2, "big") + value


def fixture_empty_class() -> bytes:
    pool = (
        fixture_class_utf8(b"Safe")
        + b"\x07\x00\x01"
        + fixture_class_utf8(b"java/lang/Object")
        + b"\x07\x00\x03"
    )
    return (
        b"\xca\xfe\xba\xbe\x00\x00\x00\x3d"
        + (5).to_bytes(2, "big")
        + pool
        + b"\x00\x21\x00\x02\x00\x04\x00\x00\x00\x00\x00\x00\x00\x00"
    )


def fixture_credential_class(
    field_name: bytes,
    literal: bytes,
    *,
    constant: bool,
    internal_name: bytes = b"com/foggyframework/v934/CredentialFixture",
) -> bytes:
    entries = [
        fixture_class_utf8(internal_name),
        b"\x07\x00\x01",
        fixture_class_utf8(b"java/lang/Object"),
        b"\x07\x00\x03",
        fixture_class_utf8(field_name),
        fixture_class_utf8(b"Ljava/lang/String;"),
        fixture_class_utf8(literal),
        b"\x08\x00\x07",
    ]
    if constant:
        entries.append(fixture_class_utf8(b"ConstantValue"))
        tail = (
            b"\x00\x21\x00\x02\x00\x04\x00\x00"
            b"\x00\x01\x00\x19\x00\x05\x00\x06\x00\x01"
            b"\x00\x09\x00\x00\x00\x02\x00\x08"
            b"\x00\x00\x00\x00"
        )
    else:
        entries.extend(
            [
                fixture_class_utf8(b"<init>"),
                fixture_class_utf8(b"()V"),
                b"\x0c\x00\x09\x00\x0a",
                b"\x0a\x00\x04\x00\x0b",
                b"\x0c\x00\x05\x00\x06",
                b"\x09\x00\x02\x00\x0d",
                fixture_class_utf8(b"Code"),
            ]
        )
        code = b"\x2a\xb7\x00\x0c\x2a\x12\x08\xb5\x00\x0e\xb1"
        code_attribute = (
            b"\x00\x0f"
            + (12 + len(code)).to_bytes(4, "big")
            + b"\x00\x02\x00\x01"
            + len(code).to_bytes(4, "big")
            + code
            + b"\x00\x00\x00\x00"
        )
        tail = (
            b"\x00\x21\x00\x02\x00\x04\x00\x00"
            b"\x00\x01\x00\x02\x00\x05\x00\x06\x00\x00"
            b"\x00\x01\x00\x01\x00\x09\x00\x0a\x00\x01"
            + code_attribute
            + b"\x00\x00"
        )
    return (
        b"\xca\xfe\xba\xbe\x00\x00\x00\x3d"
        + (len(entries) + 1).to_bytes(2, "big")
        + b"".join(entries)
        + tail
    )


def mutate_zip_flags(data: bytes, mask: int) -> bytes:
    mutated = bytearray(data)
    found_local = False
    found_central = False
    for signature, offset in ((b"PK\x03\x04", 6), (b"PK\x01\x02", 8)):
        start = 0
        while True:
            index = mutated.find(signature, start)
            if index < 0:
                break
            flag_index = index + offset
            flags = int.from_bytes(mutated[flag_index : flag_index + 2], "little")
            mutated[flag_index : flag_index + 2] = (flags | mask).to_bytes(2, "little")
            found_local = found_local or signature == b"PK\x03\x04"
            found_central = found_central or signature == b"PK\x01\x02"
            start = index + 4
    require(found_local and found_central, "E_SELF_TEST", "ZIP flag mutation headers are absent")
    return bytes(mutated)


def nested_fixture_zip(depth: int, leaf: bytes) -> bytes:
    data = leaf
    for index in range(depth):
        data = fixture_regular_zip([(f"lib-{index}.jar", data)])
    return data


def transport_tree_links(root: Path, label: str) -> dict[str, str]:
    links: dict[str, str] = {}

    def visit(directory: Path, prefix: str) -> None:
        try:
            children = sorted(
                os.scandir(directory), key=lambda item: item.name.encode("utf-8")
            )
        except OSError as error:
            reject("E_STEP4_TRANSPORT_IO", f"cannot inventory {label}: {error}")
        for child in children:
            relative = normalize_relative_path(
                f"{prefix}/{child.name}" if prefix else child.name,
                f"{label} path",
            )
            try:
                child_stat = child.stat(follow_symlinks=False)
            except OSError as error:
                reject(
                    "E_STEP4_TRANSPORT_IO",
                    f"cannot lstat {label} path {relative}: {error}",
                )
            if stat.S_ISDIR(child_stat.st_mode):
                visit(Path(child.path), relative)
            elif stat.S_ISREG(child_stat.st_mode):
                continue
            elif stat.S_ISLNK(child_stat.st_mode):
                try:
                    target = os.readlink(child.path)
                except OSError as error:
                    reject(
                        "E_STEP4_TRANSPORT_IO",
                        f"cannot read {label} symlink {relative}: {error}",
                    )
                links[relative] = target
            else:
                reject(
                    "E_STEP4_TRANSPORT_SPECIAL",
                    f"special file in {label}: {relative}",
                )
        return None

    visit(root, "")
    return links


def simple_env_receipt(data: bytes, label: str) -> dict[str, str]:
    try:
        text = data.decode("ascii")
    except UnicodeDecodeError as error:
        reject("E_STEP4_TRANSPORT_IDENTITY", f"{label} is not ASCII: {error}")
    require("\r" not in text, "E_STEP4_TRANSPORT_IDENTITY", f"{label} contains CR")
    result: dict[str, str] = {}
    for line in text.splitlines():
        require(
            line != "" and "=" in line,
            "E_STEP4_TRANSPORT_IDENTITY",
            f"malformed {label} line",
        )
        key, value = line.split("=", 1)
        require(
            re.fullmatch(r"[a-z][a-z0-9_]*", key) is not None and key not in result,
            "E_STEP4_TRANSPORT_IDENTITY",
            f"invalid/duplicate {label} key: {key}",
        )
        result[key] = value
    return result


def verify_step4_transport(source_path: Path, destination_path: Path) -> dict[str, object]:
    source = real_directory(source_path, "Step 4 transport source")
    destination = real_directory(destination_path, "Step 4 copied transport tree")
    contract, _, contract_sha256 = validate_contract()
    maximum_receipt_bytes = maximum_payload_file_bytes(contract)
    require(
        source != destination,
        "E_STEP4_TRANSPORT_PATH",
        "Step 4 source and copied destination must differ",
    )
    manifest_path = source / "final-manifest.json"
    manifest_data = secure_regular_bytes(
        manifest_path, "Step 4 final manifest", maximum_receipt_bytes
    )
    copied_manifest_data = secure_regular_bytes(
        destination / "final-manifest.json",
        "copied Step 4 final manifest",
        maximum_receipt_bytes,
    )
    require(
        copied_manifest_data == manifest_data,
        "E_STEP4_TRANSPORT_IDENTITY",
        "copied Step 4 final manifest differs from source",
    )
    manifest = load_json_bytes(manifest_data, "Step 4 final manifest")
    run_id = manifest.get("run_id")
    git_head = manifest.get("git_head")
    require(
        manifest.get("schema_version") == 1
        and manifest.get("kind") == "v934-step4-coverage-acceptance-artifact"
        and manifest.get("stage") == "final"
        and manifest.get("status") == "release-final"
        and type(run_id) is str
        and run_id == source.name
        and type(git_head) is str
        and re.fullmatch(r"[0-9a-f]{40}", git_head) is not None,
        "E_STEP4_TRANSPORT_IDENTITY",
        "Step 4 final manifest identity differs",
    )
    run_status_path = source / "run-status.env"
    run_status_data = secure_regular_bytes(
        run_status_path, "Step 4 run status", maximum_receipt_bytes
    )
    copied_run_status_data = secure_regular_bytes(
        destination / "run-status.env",
        "copied Step 4 run status",
        maximum_receipt_bytes,
    )
    require(
        copied_run_status_data == run_status_data,
        "E_STEP4_TRANSPORT_IDENTITY",
        "copied Step 4 run status differs from source",
    )
    run_status = simple_env_receipt(run_status_data, "Step 4 run status")
    require(
        run_status.get("run_id") == run_id
        and run_status.get("mode") == "release"
        and run_status.get("git_head") == git_head
        and run_status.get("status") == "release-passed",
        "E_STEP4_TRANSPORT_IDENTITY",
        "Step 4 run status authority differs",
    )
    source_links = transport_tree_links(source, "Step 4 source")
    destination_links = transport_tree_links(destination, "Step 4 copied destination")
    require(
        source_links == {},
        "E_STEP4_TRANSPORT_SET",
        f"fresh Step 4 source contains symlinks: {sorted(source_links)}",
    )
    require(
        destination_links == {},
        "E_STEP4_TRANSPORT_SET",
        f"copied fresh Step 4 tree contains symlinks: {sorted(destination_links)}",
    )
    scanned_entries = scan_tree(destination, contract, allow_metadata=False)
    scanned_summary = summary(scanned_entries)
    receipt: dict[str, object] = {
        "command": "verify-step4-transport",
        "contract_sha256": contract_sha256,
        "kind": "v934-step4-transport-safety",
        "omitted": [],
        "post_copy_scan": {**scanned_summary, "status": "passed"},
        "schema_version": 1,
        "source_authority": {
            "final_manifest_sha256": sha256_bytes(manifest_data),
            "final_manifest_size": len(manifest_data),
            "git_head": git_head,
            "kind": manifest["kind"],
            "run_id": run_id,
            "run_status_sha256": sha256_bytes(run_status_data),
            "run_status_size": len(run_status_data),
            "stage": manifest["stage"],
            "status": manifest["status"],
        },
        "status": "passed",
    }
    receipt_data = canonical_json(receipt)
    scan_sensitive_payload("transport-safety.json", receipt_data, contract)
    write_new_file(destination / "transport-safety.json", receipt_data)
    return receipt


def write_step4_transport_fixture(root: Path) -> tuple[Path, Path]:
    source = root / "step4-release-fixture"
    (source / "negative/coverage-exec").mkdir(parents=True)
    (source / "negative/coverage-xml").mkdir(parents=True)
    (source / "report/jacoco-aggregate").mkdir(parents=True)
    (source / "report/jacoco-aggregate.exec").write_bytes(b"safe-exec-fixture\n")
    (source / "report/jacoco-aggregate/jacoco.xml").write_bytes(b"<report/>\n")
    git_head = "1" * 40
    final = {
        "git_head": git_head,
        "kind": "v934-step4-coverage-acceptance-artifact",
        "run_id": source.name,
        "schema_version": 1,
        "stage": "final",
        "status": "release-final",
    }
    (source / "final-manifest.json").write_bytes(canonical_json(final))
    (source / "run-status.env").write_text(
        f"run_id={source.name}\nmode=release\ngit_head={git_head}\nstatus=release-passed\n",
        encoding="ascii",
    )
    destination = root / "copied-step4-release-fixture"
    shutil.copytree(source, destination, symlinks=True)
    return source, destination


def write_fixture(staging: Path) -> None:
    (staging / "artifacts").mkdir(parents=True)
    (staging / "reports").mkdir()
    (staging / "bin").mkdir()
    (staging / "site").mkdir()
    (staging / "reports" / ("long-directory-" + "x" * 100)).mkdir()
    safe_nested = fixture_regular_zip(
        [("nested-safe.properties", b"password=${NESTED_DATABASE_PASSWORD}\n")]
    )
    (staging / "artifacts/app.jar").write_bytes(
        fixture_regular_zip(
            [
                ("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n"),
                (
                    "BOOT-INF/classes/application.yml",
                    b"password: ${SPRING_DATASOURCE_PASSWORD}\napi-key: ${OPENAI_API_KEY:}\nsecret: <redacted>\n",
                ),
                ("BOOT-INF/classes/Safe.class", fixture_empty_class()),
                ("BOOT-INF/lib/safe.jar", safe_nested),
            ]
        )
    )
    # A NUL-bearing, non-declared binary file is skipped by the content classifier.
    (staging / "reports/raw.exec").write_bytes(b"\x00\x01password=hunter2\xff")
    (staging / "site/report.html").write_text(
        "<p>password=${HTML_FIXTURE_PASSWORD}</p>\n", encoding="utf-8"
    )
    (staging / "site/app.js").write_text(
        'const fixture = "credential=null";\n', encoding="utf-8"
    )
    (staging / ".env").write_text("credential=null\n", encoding="utf-8")
    (staging / "reports/result.json").write_text(
        '{"authorization":null,"credential":null,"password":null,"status":"passed"}\n',
        encoding="utf-8",
    )
    (staging / "reports/localization.properties").write_text(
        "basicAuthenticator.invalidAuthorization=Invalid Authorization: [{0}]\n",
        encoding="utf-8",
    )
    script = staging / "bin/verify.sh"
    script.write_text("#!/usr/bin/env sh\nexit 0\n", encoding="utf-8")
    script.chmod(0o755)


def negative_matrix(work_root: Path | None = None) -> dict[str, object]:
    contract, _, _ = validate_contract()
    if work_root is None:
        context = tempfile.TemporaryDirectory(prefix="v934-release-negative-")
        base = Path(context.name)
    else:
        context = None
        base = Path(os.path.abspath(work_root))
        require(not base.exists() and not base.is_symlink(), "E_OUTPUT_EXISTS", f"negative work root exists: {base}")
        real_directory(base.parent, "negative work parent")
        base.mkdir()
    try:
        staging = base / "staging"
        staging.mkdir()
        write_fixture(staging)
        built = build_bundle(staging, base / "bundle", "artifacts/app.jar")
        archive = Path(str(built["archive"]))
        outer = Path(str(built["archive_manifest"]))
        extracted = base / "extracted"
        inspect_archive(archive, outer, extracted)
        expected_root = str(built["root_manifest_sha256"])
        results: list[dict[str, str]] = []
        transport_safe_probes = 0

        secret_build = base / "secret-build-staging"
        clone_extracted(staging, secret_build)
        (secret_build / "reports/secret.txt").write_text(
            "password=hunter2\n", encoding="utf-8"
        )
        expect_failure(
            "secret-build",
            "E_SECRET",
            lambda: build_bundle(
                secret_build, base / "secret-build-bundle", "artifacts/app.jar"
            ),
            results,
        )

        properties_build = base / "properties-build-staging"
        clone_extracted(staging, properties_build)
        (properties_build / "reports/leak.properties").write_text(
            "password=hunter2\n", encoding="utf-8"
        )
        expect_failure(
            "secret-properties-build",
            "E_SECRET",
            lambda: build_bundle(
                properties_build,
                base / "properties-build-bundle",
                "artifacts/app.jar",
            ),
            results,
        )

        expect_failure(
            "secret-disguised-extension",
            "E_SECRET",
            lambda: scan_sensitive_payload(
                "opaque.fixture", b"password=hunter2\n", contract
            ),
            results,
        )
        for case_name, path, payload in (
            ("secret-indented-yaml", "application.yml", b"  password: hunter2\n"),
            ("secret-tabbed-yaml", "application.yml", b"\tapi-key: literal-key\n"),
            ("secret-list-yaml", "application.yml", b"  - password: hunter2\n"),
            ("secret-json-field", "application.json", b'{"password":"hunter2"}\n'),
            (
                "secret-json-unicode-escaped-key",
                "application.json",
                b'{"pass\\u0077ord":"hunter2"}\n',
            ),
            (
                "secret-dotted-password-property",
                "application.properties",
                b"spring.datasource.password=hunter2\n",
            ),
            (
                "secret-dotted-api-key-property",
                "application.properties",
                b"openai.api-key=sk-live-secret\n",
            ),
            (
                "secret-dotted-client-secret-property",
                "application.properties",
                b"app.client-secret=literal-secret\n",
            ),
            (
                "secret-cloud-access-key-id",
                "application.yml",
                b"access-key-id: literal-secret\n",
            ),
            (
                "secret-cloud-access-key-id-camel",
                "application.yml",
                b"accessKeyId: literal-secret\n",
            ),
            (
                "secret-cloud-secret-access-key",
                "application.yml",
                b"secret-access-key: literal-secret\n",
            ),
            (
                "secret-cloud-secret-access-key-camel",
                "application.yml",
                b"secretAccessKey: literal-secret\n",
            ),
            (
                "secret-cloud-access-key-secret",
                "application.yml",
                b"access-key-secret: literal-secret\n",
            ),
            (
                "secret-cloud-access-key-secret-camel",
                "application.yml",
                b"accessKeySecret: literal-secret\n",
            ),
            (
                "secret-cloud-secret-id",
                "application.yml",
                b"secret-id: literal-secret\n",
            ),
            (
                "secret-cloud-secret-id-camel",
                "application.yml",
                b"secretId: literal-secret\n",
            ),
            (
                "secret-properties-unicode-escaped-key",
                "application.properties",
                b"spring.datasource.pass\\u0077ord=hunter2\n",
            ),
            (
                "secret-properties-whitespace-separator",
                "application.properties",
                b"password hunter2\n",
            ),
            (
                "secret-properties-key-continuation",
                "application.properties",
                b"pass\\\nword=hunter2\n",
            ),
            (
                "secret-yaml-unicode-escaped-key",
                "application.yml",
                b'"pass\\u0077ord": "hunter two"\n',
            ),
            (
                "secret-yaml-hex-escaped-key",
                "application.yml",
                b'"pass\\x77ord": hunter2\n',
            ),
            (
                "secret-yaml-tagged-key",
                "application.yml",
                b"!!str password: hunter2\n",
            ),
            (
                "secret-xml-decimal-entity-key",
                "application.xml",
                b'<property name="pass&#119;ord" value="hunter2"/>\n',
            ),
            (
                "secret-xml-hex-entity-key",
                "application.xml",
                b'<property value="hunter2" name="pass&#x77;ord"/>\n',
            ),
            (
                "secret-xml-credential-element",
                "application.xml",
                b"<password>hunter2</password>\n",
            ),
            (
                "secret-html-quoted-credential",
                "jacoco-source.html",
                b"username=&quot;admin&quot;\n",
            ),
            (
                "secret-placeholder-literal-default",
                "application.yml",
                b"password=${DB_PASSWORD:hunter2}\n",
            ),
            (
                "secret-username-placeholder-literal-default",
                "application.yml",
                b"username=${SPRING_DATASOURCE_USERNAME:foggy}\n",
            ),
            (
                "secret-generic-placeholder-uri-default",
                "application.yml",
                b"db.url=${DB_URL:mysql://admin:hunter2@localhost/app}\n",
            ),
            (
                "secret-generic-placeholder-jdbc-default",
                "application.yml",
                b"db.url=${DB_URL:jdbc:mysql://localhost/app?user=admin&password=hunter2}\n",
            ),
            (
                "secret-generic-placeholder-assignment-default",
                "application.yml",
                b"value=${CONFIG:password=hunter2}\n",
            ),
            (
                "secret-generic-placeholder-bearer-default",
                "application.yml",
                b"header=${HEADER:Bearer abcdefghijklmnop}\n",
            ),
            (
                "secret-generic-placeholder-nested-default",
                "application.yml",
                b"value=${OUTER:${INNER:password=hunter2}}\n",
            ),
            (
                "secret-quoted-whitespace-yaml",
                "application.yml",
                b'password: "hunter two"\n',
            ),
            (
                "secret-single-quoted-whitespace-yaml",
                "application.yml",
                b"password: 'hunter two'\n",
            ),
            (
                "secret-quoted-whitespace-json",
                "application.json",
                b'{"password":"hunter two"}\n',
            ),
            (
                "secret-jdbc-userinfo-only",
                "application.yml",
                b"url=${DB_URL:jdbc:mysql://admin@localhost/app}\n",
            ),
            (
                "secret-jdbc-user-query",
                "application.yml",
                b"url=${DB_URL:jdbc:mysql://localhost/app?user=admin}\n",
            ),
            (
                "secret-http-userinfo",
                "application.yml",
                b"url=${SERVICE_URL:https://admin:hunter2@example.invalid/app}\n",
            ),
            (
                "secret-xml-property",
                "application.xml",
                b'<property name="password" value="hunter2"/>\n',
            ),
            (
                "secret-exported-environment",
                "startup.sh",
                b"export PASSWORD=hunter2\n",
            ),
            (
                "secret-jvm-system-property",
                "startup.sh",
                b"java -Dpassword=hunter2 -jar app.jar\n",
            ),
            (
                "secret-jdbc-query",
                "application.properties",
                b"url=jdbc:mysql://localhost/db?user=fixture&password=hunter2\n",
            ),
            (
                "secret-jdbc-sqlserver-property",
                "application.properties",
                b"url=jdbc:sqlserver://localhost;user=fixture;password=hunter2\n",
            ),
        ):
            expect_failure(
                case_name,
                "E_SECRET",
                lambda p=path, d=payload: scan_sensitive_payload(p, d, contract),
                results,
            )
        safe_placeholder_probes = (
            b"port=${PORT:8080}\n",
            b"path=${OUTER:${INNER}/file}\n",
            b"username=${SPRING_DATASOURCE_USERNAME}\n",
            b"username=${SPRING_DATASOURCE_USERNAME:}\n",
            b"password=${DB_PASSWORD}\n",
            b"password=${DB_PASSWORD:}\n",
            b"password=${DB_PASSWORD:fixture-test-password}\n",
            b'password: "null"\n',
            b'authorization: "Bearer ${TOKEN}"\n',
            b"url=${DB_URL:jdbc:mysql://${DB_USER}@localhost/app}\n",
            b"url=${DB_URL:jdbc:mysql://localhost/app?user=${DB_USER}}\n",
            b'username: "env(\'MY_DB_USER\')"\n',
            b"password: 'env(&#39;MY_DB_PASS&#39;)'\n",
        )
        for probe in safe_placeholder_probes:
            scan_sensitive_payload("safe-placeholder-probe.yml", probe, contract)
        safe_semantic_probes = (
            ("safe.json", b'{"pass\\u0077ord":"${DB_PASSWORD}"}\n'),
            ("safe.properties", b"pass\\u0077ord=${DB_PASSWORD:}\n"),
            ("safe.yml", b'"pass\\u0077ord": "${DB_PASSWORD}"\n'),
            (
                "safe.xml",
                b'<property name="pass&#x77;ord" value="${DB_PASSWORD}"/>\n',
            ),
            ("safe.xml", b"<password>${DB_PASSWORD}</password>\n"),
            (
                "jacoco-source.html",
                b"log.info(&quot;url={}, username={}&quot;, url, username);\n",
            ),
            (
                "SafeStatic.class",
                fixture_credential_class(
                    b"PASSWORD", b"${DB_PASSWORD}", constant=True
                ),
            ),
            (
                "SafeInstance.class",
                fixture_credential_class(
                    b"password", b"${DB_PASSWORD}", constant=False
                ),
            ),
        )
        for path, probe in safe_semantic_probes:
            scan_sensitive_payload(path, probe, contract)
        safe_third_party_class_probes = (
            (
                "package/app.jar!/BOOT-INF/lib/tomcat-embed-core-10.1.40.jar!/org/apache/tomcat/util/net/jsse/PEMFile$Part.class",
                b"org/apache/tomcat/util/net/jsse/PEMFile$Part",
                b"PRIVATE_KEY",
                b"PRIVATE KEY",
            ),
            (
                "package/app.jar!/BOOT-INF/lib/spring-web-6.2.6.jar!/org/springframework/http/HttpHeaders.class",
                b"org/springframework/http/HttpHeaders",
                b"AUTHORIZATION",
                b"Authorization",
            ),
            (
                "package/app.jar!/BOOT-INF/lib/netty-codec-http-4.1.119.Final.jar!/io/netty/handler/codec/http/HttpHeaders$Names.class",
                b"io/netty/handler/codec/http/HttpHeaders$Names",
                b"AUTHORIZATION",
                b"Authorization",
            ),
            (
                "package/app.jar!/BOOT-INF/lib/HikariCP-5.1.0.jar!/com/zaxxer/hikari/util/DriverDataSource.class",
                b"com/zaxxer/hikari/util/DriverDataSource",
                b"PASSWORD",
                b"password",
            ),
            (
                "package/app.jar!/BOOT-INF/lib/spring-boot-autoconfigure-3.4.5.jar!/org/springframework/boot/autoconfigure/amqp/RabbitProperties.class",
                b"org/springframework/boot/autoconfigure/amqp/RabbitProperties",
                b"username",
                b"guest",
            ),
            (
                "package/app.jar!/BOOT-INF/lib/spring-boot-autoconfigure-3.4.5.jar!/org/springframework/boot/autoconfigure/amqp/RabbitProperties.class",
                b"org/springframework/boot/autoconfigure/amqp/RabbitProperties",
                b"password",
                b"guest",
            ),
            (
                "package/app.jar!/BOOT-INF/lib/spring-core-6.2.6.jar!/org/springframework/core/io/UrlResource.class",
                b"org/springframework/core/io/UrlResource",
                b"AUTHORIZATION",
                b"Authorization",
            ),
        )
        for path, internal_name, field_name, literal in safe_third_party_class_probes:
            scan_class_file(
                path,
                fixture_credential_class(
                    field_name,
                    literal,
                    constant=True,
                    internal_name=internal_name,
                ),
                contract,
            )
        safe_runtime_source_probes = (
            b"* secret-key: ${CLOUD_SECRET_KEY}\n",
            b"* access-key-id: ${AWS_ACCESS_KEY_ID}\n",
            b"* secret-access-key: ${AWS_SECRET_ACCESS_KEY}\r\n",
            b"* accessKeySecret: ${ALIYUN_ACCESS_KEY_SECRET}\r",
            b'private String secretId = "${TENCENT_SECRET_ID}";\n',
            b"* username: env('DATABASE_USER')\r\n",
            b"* password: env(\"DATABASE_PASSWORD\")\r",
        )
        for probe in safe_runtime_source_probes:
            scan_runtime_source_credentials("safe-runtime-source.java", probe)
        for case_name, line_ending in (
            ("runtime-source-secret-lf", b"\n"),
            ("runtime-source-secret-crlf", b"\r\n"),
            ("runtime-source-secret-cr", b"\r"),
        ):
            expect_failure(
                case_name,
                "E_SECRET",
                lambda e=line_ending: scan_runtime_source_credentials(
                    "runtime-source-example.md",
                    b"* secret-key: your-secret-key" + e,
                ),
                results,
            )
        expect_failure(
            "runtime-source-env-literal-default",
            "E_SECRET",
            lambda: scan_runtime_source_credentials(
                "runtime-source-example.java",
                b"* password: env('DATABASE_PASSWORD', 'hunter2')\r\n",
            ),
            results,
        )
        expect_failure(
            "generic-env-literal-default",
            "E_SECRET",
            lambda: scan_sensitive_payload(
                "generic-env-default.yml",
                b'username: "env(\'MY_DB_USER\', \'root\')"\n',
                contract,
            ),
            results,
        )
        expect_failure(
            "jacoco-html-env-literal-default",
            "E_SECRET",
            lambda: scan_sensitive_payload(
                "jacoco-source.html",
                b"password: 'env(&#39;MY_DB_PASS&#39;, &#39;hunter2&#39;)'\n",
                contract,
            ),
            results,
        )
        transport_positive_root = base / "step4-transport-positive"
        transport_positive_root.mkdir()
        transport_source, transport_destination = write_step4_transport_fixture(
            transport_positive_root
        )
        verify_step4_transport(transport_source, transport_destination)
        transport_safe_probes += 1

        transport_tamper_root = base / "step4-transport-authority-tamper"
        transport_tamper_root.mkdir()
        tamper_source, tamper_destination = write_step4_transport_fixture(
            transport_tamper_root
        )
        tampered_manifest = load_json_bytes(
            (tamper_destination / "final-manifest.json").read_bytes(),
            "tampered Step 4 fixture manifest",
        )
        tampered_manifest["status"] = "tampered-final"
        (tamper_destination / "final-manifest.json").write_bytes(
            canonical_json(tampered_manifest)
        )
        expect_failure(
            "step4-transport-authority-tamper",
            "E_STEP4_TRANSPORT_IDENTITY",
            lambda: verify_step4_transport(tamper_source, tamper_destination),
            results,
        )

        transport_extra_root = base / "step4-transport-extra-link"
        transport_extra_root.mkdir()
        extra_source, extra_destination = write_step4_transport_fixture(
            transport_extra_root
        )
        os.symlink(
            os.fspath(extra_source / "report/jacoco-aggregate.exec"),
            extra_destination / "unexpected.exec",
        )
        expect_failure(
            "step4-transport-extra-link",
            "E_STEP4_TRANSPORT_SET",
            lambda: verify_step4_transport(extra_source, extra_destination),
            results,
        )

        transport_source_link_root = base / "step4-transport-source-link"
        transport_source_link_root.mkdir()
        linked_source, linked_destination = write_step4_transport_fixture(
            transport_source_link_root
        )
        os.symlink(
            os.fspath(linked_source / "report/jacoco-aggregate.exec"),
            linked_source / "unexpected.exec",
        )
        expect_failure(
            "step4-transport-source-link",
            "E_STEP4_TRANSPORT_SET",
            lambda: verify_step4_transport(linked_source, linked_destination),
            results,
        )

        transport_special_root = base / "step4-transport-special"
        transport_special_root.mkdir()
        special_source, special_destination = write_step4_transport_fixture(
            transport_special_root
        )
        os.mkfifo(special_destination / "unexpected.fifo")
        expect_failure(
            "step4-transport-special",
            "E_STEP4_TRANSPORT_SPECIAL",
            lambda: verify_step4_transport(special_source, special_destination),
            results,
        )
        for case_name, path, payload in (
            (
                "malformed-json-key-escape",
                "application.json",
                b'{"pass\\u07G7ord":"hunter2"}\n',
            ),
            (
                "malformed-properties-key-escape",
                "application.properties",
                b"pass\\u00G0word=hunter2\n",
            ),
            (
                "malformed-yaml-key-escape",
                "application.yml",
                b'"pass\\U00110000word": hunter2\n',
            ),
            (
                "malformed-xml-key-entity",
                "application.xml",
                b'<property name="pass&credentialW;ord" value="hunter2"/>\n',
            ),
        ):
            expect_failure(
                case_name,
                "E_SECRET_NORMALIZATION",
                lambda p=path, d=payload: scan_sensitive_payload(p, d, contract),
                results,
            )
        tiny_normalization_contract = copy.deepcopy(contract)
        tiny_normalization_limits = tiny_normalization_contract[
            "sensitive_text_policy"
        ]["normalization_limits"]
        assert isinstance(tiny_normalization_limits, dict)
        tiny_normalization_limits["max_escape_sequences_per_payload"] = 0
        expect_failure(
            "credential-normalization-escape-budget",
            "E_SECRET_NORMALIZATION_LIMIT",
            lambda: scan_sensitive_payload(
                "application.json",
                b'{"pass\\u0077ord":"hunter2"}\n',
                tiny_normalization_contract,
            ),
            results,
        )
        nested_placeholder = (
            b"value="
            + b"".join([b"${A:" for _ in range(65)])
            + b"x"
            + b"}" * 65
        )
        expect_failure(
            "environment-placeholder-depth-budget",
            "E_SECRET_NORMALIZATION_LIMIT",
            lambda: scan_sensitive_payload(
                "application.yml", nested_placeholder, contract
            ),
            results,
        )
        top_secret_jar = fixture_regular_zip(
            [("leak.properties", b"password=hunter2\n")]
        )
        expect_failure(
            "jar-top-level-resource-secret",
            "E_SECRET",
            lambda: scan_sensitive_payload("app.jar", top_secret_jar, contract),
            results,
        )
        classes_secret_jar = fixture_regular_zip(
            [("BOOT-INF/classes/application.yml", b"api-key: literal-key\n")]
        )
        expect_failure(
            "jar-boot-classes-secret",
            "E_SECRET",
            lambda: scan_sensitive_payload("app.jar", classes_secret_jar, contract),
            results,
        )
        for case_name, class_data in (
            (
                "jar-class-static-credential",
                fixture_credential_class(b"PASSWORD", b"hunter2", constant=True),
            ),
            (
                "jar-class-instance-credential",
                fixture_credential_class(b"password", b"hunter2", constant=False),
            ),
            (
                "jar-class-config-assignment",
                fixture_credential_class(
                    b"CONFIG", b"password=hunter2", constant=True
                ),
            ),
            (
                "jar-class-default-marker-credential",
                fixture_credential_class(
                    b"authToken", b"default-render-token", constant=False
                ),
            ),
        ):
            class_jar = fixture_regular_zip(
                [("BOOT-INF/classes/CredentialFixture.class", class_data)]
            )
            expect_failure(
                case_name,
                "E_SECRET",
                lambda d=class_jar: scan_sensitive_payload("app.jar", d, contract),
                results,
            )
        malformed_class_jar = fixture_regular_zip(
            [("BOOT-INF/classes/Broken.class", b"\xca\xfe\xba\xbe\x00")]
        )
        expect_failure(
            "jar-class-malformed",
            "E_CLASS_FORMAT",
            lambda: scan_sensitive_payload("app.jar", malformed_class_jar, contract),
            results,
        )
        tiny_class_contract = copy.deepcopy(contract)
        tiny_class_limits = tiny_class_contract["sensitive_text_policy"]["normalization_limits"]
        assert isinstance(tiny_class_limits, dict)
        tiny_class_limits["max_class_utf8_bytes"] = 1
        expect_failure(
            "class-constant-pool-budget",
            "E_CLASS_LIMIT",
            lambda: scan_sensitive_payload(
                "Safe.class", fixture_empty_class(), tiny_class_contract
            ),
            results,
        )
        nested_secret = fixture_regular_zip(
            [("nested.properties", b"secret=literal-secret\n")]
        )
        nested_secret_jar = fixture_regular_zip(
            [("BOOT-INF/lib/unsafe.jar", nested_secret)]
        )
        expect_failure(
            "jar-nested-secret",
            "E_SECRET",
            lambda: scan_sensitive_payload("app.jar", nested_secret_jar, contract),
            results,
        )
        nested_default_secret = fixture_regular_zip(
            [
                (
                    "nested.yml",
                    b"password=${NESTED_DATABASE_PASSWORD:hunter2}\n",
                )
            ]
        )
        nested_default_jar = fixture_regular_zip(
            [("BOOT-INF/lib/unsafe-default.jar", nested_default_secret)]
        )
        expect_failure(
            "jar-nested-placeholder-literal-default",
            "E_SECRET",
            lambda: scan_sensitive_payload(
                "app.jar", nested_default_jar, contract
            ),
            results,
        )
        expect_failure(
            "jar-malformed",
            "E_ZIP_FORMAT",
            lambda: scan_sensitive_payload("app.jar", b"not-a-zip", contract),
            results,
        )
        framed_jar = fixture_regular_zip([("safe.txt", b"status=safe\n")])
        for case_name, payload in (
            ("jar-leading-bytes", b"password=hunter2\n" + framed_jar),
            ("jar-trailing-bytes", framed_jar + b"password=hunter2\n"),
            ("jar-concatenated-archive", framed_jar + framed_jar),
        ):
            expect_failure(
                case_name,
                "E_ZIP_FRAMING",
                lambda d=payload: scan_sensitive_payload("app.jar", d, contract),
                results,
            )
        traversal_jar = fixture_regular_zip([("../escape.properties", b"status=safe\n")])
        expect_failure(
            "jar-traversal-path",
            "E_ZIP_PATH",
            lambda: scan_sensitive_payload("app.jar", traversal_jar, contract),
            results,
        )
        duplicate_jar = fixture_regular_zip(
            [("duplicate.txt", b"first\n"), ("duplicate.txt", b"second\n")]
        )
        expect_failure(
            "jar-duplicate-entry",
            "E_ZIP_DUPLICATE",
            lambda: scan_sensitive_payload("app.jar", duplicate_jar, contract),
            results,
        )
        symlink_jar = fixture_zip(
            [
                (
                    "forbidden-link",
                    b"BOOT-INF/classes/application.yml",
                    stat.S_IFLNK | 0o777,
                    zipfile.ZIP_STORED,
                )
            ]
        )
        expect_failure(
            "jar-symlink-entry",
            "E_ZIP_SPECIAL",
            lambda: scan_sensitive_payload("app.jar", symlink_jar, contract),
            results,
        )
        encrypted_jar = mutate_zip_flags(
            fixture_regular_zip([("encrypted.txt", b"safe\n")]), 0x0001
        )
        expect_failure(
            "jar-encrypted-entry",
            "E_ZIP_ENCRYPTED",
            lambda: scan_sensitive_payload("app.jar", encrypted_jar, contract),
            results,
        )
        abnormal_compression_jar = fixture_zip(
            [
                (
                    "abnormal.txt",
                    b"safe\n",
                    stat.S_IFREG | 0o644,
                    zipfile.ZIP_BZIP2,
                )
            ]
        )
        expect_failure(
            "jar-abnormal-compression",
            "E_ZIP_COMPRESSION",
            lambda: scan_sensitive_payload(
                "app.jar", abnormal_compression_jar, contract
            ),
            results,
        )
        bomb_jar = fixture_regular_zip([("bomb.txt", b"A" * (1024 * 1024))])
        expect_failure(
            "jar-compression-bomb",
            "E_ZIP_BOMB",
            lambda: scan_sensitive_payload("app.jar", bomb_jar, contract),
            results,
        )
        tiny_archive_contract = copy.deepcopy(contract)
        tiny_archive_limits = tiny_archive_contract["sensitive_text_policy"]["archive_limits"]
        assert isinstance(tiny_archive_limits, dict)
        tiny_archive_limits["max_archive_bytes"] = 1
        expect_failure(
            "jar-archive-size",
            "E_ZIP_SIZE",
            lambda: scan_sensitive_payload(
                "app.jar",
                fixture_regular_zip([("safe.txt", b"safe\n")]),
                tiny_archive_contract,
            ),
            results,
        )
        tiny_entry_contract = copy.deepcopy(contract)
        tiny_entry_limits = tiny_entry_contract["sensitive_text_policy"]["archive_limits"]
        assert isinstance(tiny_entry_limits, dict)
        tiny_entry_limits["max_entry_uncompressed_bytes"] = 1
        expect_failure(
            "jar-entry-size",
            "E_ZIP_ENTRY_SIZE",
            lambda: scan_sensitive_payload(
                "app.jar",
                fixture_regular_zip([("safe.txt", b"safe\n")]),
                tiny_entry_contract,
            ),
            results,
        )
        tiny_outer_entries_contract = copy.deepcopy(contract)
        tiny_outer_entries_limits = tiny_outer_entries_contract["sensitive_text_policy"]["archive_limits"]
        assert isinstance(tiny_outer_entries_limits, dict)
        tiny_outer_entries_limits["max_outer_entries"] = 1
        expect_failure(
            "outer-tree-entry-count",
            "E_OUTER_ENTRIES",
            lambda: scan_tree(staging, tiny_outer_entries_contract, allow_metadata=False),
            results,
        )
        tiny_outer_member_contract = copy.deepcopy(contract)
        tiny_outer_member_limits = tiny_outer_member_contract["sensitive_text_policy"]["archive_limits"]
        assert isinstance(tiny_outer_member_limits, dict)
        tiny_outer_member_limits["max_outer_member_uncompressed_bytes"] = 1
        expect_failure(
            "outer-tree-member-size",
            "E_OUTER_MEMBER_SIZE",
            lambda: scan_tree(staging, tiny_outer_member_contract, allow_metadata=False),
            results,
        )
        tiny_outer_total_contract = copy.deepcopy(contract)
        tiny_outer_total_limits = tiny_outer_total_contract["sensitive_text_policy"]["archive_limits"]
        assert isinstance(tiny_outer_total_limits, dict)
        tiny_outer_total_limits["max_outer_uncompressed_bytes"] = 1
        expect_failure(
            "outer-tree-total-size",
            "E_OUTER_TOTAL_SIZE",
            lambda: scan_tree(staging, tiny_outer_total_contract, allow_metadata=False),
            results,
        )
        shallow_contract = copy.deepcopy(contract)
        shallow_limits = shallow_contract["sensitive_text_policy"]["archive_limits"]
        assert isinstance(shallow_limits, dict)
        shallow_limits["max_archive_depth"] = 1
        expect_failure(
            "jar-nesting-depth",
            "E_ZIP_DEPTH",
            lambda: scan_sensitive_payload(
                "app.jar",
                nested_fixture_zip(2, fixture_regular_zip([("safe.txt", b"safe\n")])),
                shallow_contract,
            ),
            results,
        )
        expect_failure(
            "declared-text-binary",
            "E_TEXT_BINARY",
            lambda: scan_sensitive_payload(
                "leak.properties", b"\x00password=hunter2\n", contract
            ),
            results,
        )

        tamper = base / "tamper"
        clone_extracted(extracted, tamper)
        with open(tamper / "reports/result.json", "ab") as target:
            target.write(b"tamper")
        expect_failure("tamper", "E_FILE_SIZE", lambda: verify_root(tamper, expected_root), results)

        missing = base / "missing"
        clone_extracted(extracted, missing)
        (missing / "reports/result.json").unlink()
        expect_failure("missing", "E_ENTRY_SET", lambda: verify_root(missing, expected_root), results)

        wrong_jar = base / "wrong-jar"
        clone_extracted(extracted, wrong_jar)
        with open(wrong_jar / "artifacts/app.jar", "ab") as target:
            target.write(b"wrong")
        expect_failure(
            "wrong-jar-hash",
            "E_ZIP_FRAMING",
            lambda: verify_root(wrong_jar, expected_root),
            results,
        )

        linked = base / "linked"
        clone_extracted(extracted, linked)
        (linked / "reports/result.json").unlink()
        os.symlink("../artifacts/app.jar", linked / "reports/result.json")
        expect_failure("root-symlink", "E_SYMLINK", lambda: verify_root(linked, expected_root), results)

        secret_root = base / "secret-root"
        clone_extracted(extracted, secret_root)
        (secret_root / ".env").write_text(
            "password=hunter2\n", encoding="utf-8"
        )
        expect_failure(
            "root-secret", "E_SECRET", lambda: verify_root(secret_root, expected_root), results
        )

        mtime_tamper = base / "mtime-tamper"
        clone_extracted(extracted, mtime_tamper)
        mtime_target = mtime_tamper / "reports/result.json"
        original_mtime = os.lstat(mtime_target).st_mtime_ns
        os.utime(
            mtime_target,
            ns=(original_mtime + 1_000_000_000, original_mtime + 1_000_000_000),
            follow_symlinks=False,
        )
        expect_failure(
            "root-mtime-tamper",
            "E_FILE_MTIME",
            lambda: verify_root(mtime_tamper, expected_root),
            results,
        )

        for mutation, expected_code in (
            ("duplicate", "E_DUPLICATE_MEMBER"),
            ("traversal", "E_MEMBER_PATH"),
            ("symlink", "E_SYMLINK"),
            ("secret", "E_SECRET"),
            ("outer-bomb", "E_OUTER_BOMB"),
            ("gzip-trailing", "E_ARCHIVE_FRAMING"),
            ("gzip-concatenated", "E_ARCHIVE_FRAMING"),
            ("tar-after-eof", "E_ARCHIVE_FRAMING"),
        ):
            evil_dir = base / f"archive-{mutation}"
            evil_dir.mkdir()
            evil_archive = evil_dir / archive.name
            evil_outer = evil_dir / outer.name
            rewrite_archive(archive, evil_archive, mutation, contract)
            outer_for_mutated_archive(outer, evil_archive, evil_outer)
            expect_failure(f"archive-{mutation}", expected_code, lambda a=evil_archive, o=evil_outer: inspect_archive(a, o), results)

        corrupt_dir = base / "archive-tamper"
        corrupt_dir.mkdir()
        corrupt_archive = corrupt_dir / archive.name
        shutil.copyfile(archive, corrupt_archive)
        with open(corrupt_archive, "ab") as target:
            target.write(b"tamper")
        corrupt_outer = corrupt_dir / outer.name
        shutil.copyfile(outer, corrupt_outer)
        expect_failure("archive-digest-tamper", "E_ARCHIVE_HASH", lambda: inspect_archive(corrupt_archive, corrupt_outer), results)

        return {
            "cases": results,
            "command": "negative",
            "passed": len(results),
            "safe_placeholder_probes": len(safe_placeholder_probes)
            + len(safe_semantic_probes)
            + len(safe_runtime_source_probes)
            + len(safe_third_party_class_probes)
            + transport_safe_probes,
            "status": "passed",
        }
    finally:
        if context is not None:
            context.cleanup()


def self_test() -> dict[str, object]:
    with tempfile.TemporaryDirectory(prefix="v934-release-self-test-") as temporary:
        root = Path(temporary)
        staging = root / "staging"
        staging.mkdir()
        write_fixture(staging)
        first = build_bundle(staging, root / "bundle-a", "artifacts/app.jar")
        second = build_bundle(staging, root / "bundle-b", "artifacts/app.jar")
        require(first["archive_sha256"] == second["archive_sha256"], "E_SELF_TEST", "deterministic archive hashes differ")
        require(Path(str(first["archive"])).read_bytes() == Path(str(second["archive"])).read_bytes(), "E_SELF_TEST", "deterministic archive bytes differ")
        archive_result = inspect_archive(Path(str(first["archive"])), Path(str(first["archive_manifest"])))
        extracted = root / "downloaded-and-extracted"
        extract_result = inspect_archive(Path(str(first["archive"])), Path(str(first["archive_manifest"])), extracted)
        root_result = verify_root(extracted, str(first["root_manifest_sha256"]))
        contract, _, _ = validate_contract()
        manifests = contract["manifests"]
        assert isinstance(manifests, dict)
        manifest_data = secure_regular_bytes(
            extracted / PurePosixPath(str(manifests["file_manifest"])),
            "self-test extracted file manifest",
        )
        manifest_entries, _ = validate_file_manifest(
            load_json_bytes(manifest_data, "self-test extracted file manifest"), contract
        )
        restored_files = 0
        for entry in manifest_entries:
            if entry.kind != "file":
                continue
            assert entry.mtime_ns is not None
            require(
                os.lstat(extracted / PurePosixPath(entry.path)).st_mtime_ns
                == entry.mtime_ns,
                "E_SELF_TEST",
                f"extracted mtime was not restored: {entry.path}",
            )
            restored_files += 1
        negatives = negative_matrix()
        return {
            "archive_sha256": first["archive_sha256"],
            "command": "self-test",
            "deterministic_rebuilds": 2,
            "mtime_restore_files": restored_files,
            "negative_cases": negatives["passed"],
            "positive_checks": [archive_result["command"], extract_result["command"], root_result["command"]],
            "root_manifest_sha256": first["root_manifest_sha256"],
            "status": "passed",
        }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    build = commands.add_parser("build", help="build deterministic archive and manifests")
    build.add_argument("--staging-root", type=Path, required=True)
    build.add_argument("--output-dir", type=Path, required=True)
    build.add_argument("--jar-relative-path", required=True)
    verify_archive = commands.add_parser("verify-archive", help="verify archive without extracting")
    verify_archive.add_argument("--archive", type=Path, required=True)
    verify_archive.add_argument("--archive-manifest", type=Path, required=True)
    extract = commands.add_parser("extract-verify", help="safely extract and verify in another directory")
    extract.add_argument("--archive", type=Path, required=True)
    extract.add_argument("--archive-manifest", type=Path, required=True)
    extract.add_argument("--destination", type=Path, required=True)
    verify_root_parser = commands.add_parser("verify-root", help="verify an already extracted root")
    verify_root_parser.add_argument("--root", type=Path, required=True)
    verify_root_parser.add_argument("--root-manifest-sha256", required=True)
    scan_root_parser = commands.add_parser(
        "scan-root", help="scan a release tree with the frozen sensitive-material policy"
    )
    scan_root_parser.add_argument("--root", type=Path, required=True)
    scan_root_parser.add_argument("--allow-metadata", action="store_true")
    scan_root_parser.add_argument("--env-output", type=Path)
    runtime_source_parser = commands.add_parser(
        "scan-runtime-source",
        help="scan the exact first-party Launcher runtime source closure",
    )
    runtime_source_parser.add_argument("--repo-root", type=Path, required=True)
    step4_transport_parser = commands.add_parser(
        "verify-step4-transport",
        help="verify that fresh signed Step 4 source and copied trees contain no links or special files",
    )
    step4_transport_parser.add_argument("--source", type=Path, required=True)
    step4_transport_parser.add_argument("--destination", type=Path, required=True)
    negative = commands.add_parser("negative", help="run the automatic fail-closed mutation matrix")
    negative.add_argument("--work-root", type=Path)
    commands.add_parser("self-test", help="run deterministic, portable and negative focused checks")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.command == "build":
            result = build_bundle(args.staging_root, args.output_dir, args.jar_relative_path)
        elif args.command == "verify-archive":
            result = inspect_archive(args.archive, args.archive_manifest)
        elif args.command == "extract-verify":
            result = inspect_archive(args.archive, args.archive_manifest, args.destination)
        elif args.command == "verify-root":
            result = verify_root(args.root, args.root_manifest_sha256)
        elif args.command == "scan-root":
            result = sensitive_scan_receipt(
                args.root, args.allow_metadata, args.env_output
            )
        elif args.command == "scan-runtime-source":
            result = runtime_source_receipt(args.repo_root)
        elif args.command == "verify-step4-transport":
            result = verify_step4_transport(args.source, args.destination)
        elif args.command == "negative":
            result = negative_matrix(args.work_root)
        elif args.command == "self-test":
            result = self_test()
        else:
            reject("E_ARGUMENT", f"unsupported command: {args.command}")
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0
    except ArtifactError as error:
        print(f"[v934-release-artifact] ERROR {error.code}: {error}", file=sys.stderr)
        return 1
    except (OSError, tarfile.TarError) as error:
        print(f"[v934-release-artifact] ERROR E_RUNTIME: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
