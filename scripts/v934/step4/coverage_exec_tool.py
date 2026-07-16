#!/usr/bin/env python3
"""Fail-closed JaCoCo exec inventory and provenance verifier for 9.3.4 Step 4."""

from __future__ import annotations

import argparse
import base64
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import time
from typing import Any


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
EXPECTED_JACOCO_VERSION = "0.8.12"
EXPECTED_AGENT_SHA256 = "115e8e6e6593ca3a9892dfef695df4d487c706e59e71e64dc0ab95716ee02622"
EXPECTED_CORE_SHA256 = "fca26db37c0c5fbd5dc4985237eb82866df9799d5082af899475a73f91f5b035"
EXPECTED_LEDGER_SHA256 = "10ddf85daa0426d530bec3ccd9bb1a10446aa426d920c6c5c433163455552711"


class CoverageError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code
        self.message = message


def reject(code: str, message: str) -> None:
    raise CoverageError(code, message)


def sha256_file(path: Path) -> str:
    regular_file(path, "E_FILE_MISSING")
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        reject("E_FILE_READ", f"cannot hash {path}: {exc.__class__.__name__}")
    return digest.hexdigest()


def regular_file(path: Path, code: str) -> os.stat_result:
    try:
        stat = path.lstat()
    except FileNotFoundError:
        reject(code, f"missing file: {path}")
    if path.is_symlink() or not path.is_file():
        reject("E_EXEC_TYPE", f"not a real regular file: {path}")
    return stat


def load_json(path: Path) -> dict[str, Any]:
    regular_file(path, "E_CONTRACT")

    def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                reject("E_JSON", f"duplicate JSON key: {key!r}")
            result[key] = value
        return result

    def reject_constant(value: str) -> None:
        reject("E_JSON", f"non-finite JSON number is forbidden: {value}")

    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=unique_object,
            parse_constant=reject_constant,
        )
    except CoverageError:
        raise
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        reject("E_CONTRACT", f"invalid JSON {path}: {exc}")
    if not isinstance(value, dict):
        reject("E_CONTRACT", f"JSON root must be an object: {path}")
    return value


def load_ledger(step4_dir: Path, contract: dict[str, Any]) -> list[dict[str, str]]:
    ledger_contract = contract.get("execution_ledger")
    if not isinstance(ledger_contract, dict):
        reject("E_CONTRACT", "execution_ledger contract is missing")
    path_value = ledger_contract.get("path")
    if path_value != "scripts/v934/step4/coverage-exec-ledger.tsv":
        reject("E_CONTRACT", f"unexpected ledger path: {path_value!r}")
    ledger = step4_dir / "coverage-exec-ledger.tsv"
    ledger_sha = sha256_file(ledger)
    if ledger_sha != EXPECTED_LEDGER_SHA256 or ledger_contract.get("sha256") != EXPECTED_LEDGER_SHA256:
        reject("E_CONTRACT_SHA", "coverage exec ledger hash differs")
    with ledger.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if tuple(reader.fieldnames or ()) != LEDGER_HEADER:
            reject("E_LEDGER", f"unexpected ledger header: {reader.fieldnames}")
        rows = list(reader)
    if len(rows) != 23 or ledger_contract.get("exec_files") != 23:
        reject("E_LEDGER", f"expected exact 23 exec rows, found {len(rows)}")
    names: set[str] = set()
    session_count = 0
    for number, row in enumerate(rows, 2):
        name = row["exec_file"]
        if not name or name != Path(name).name or not name.endswith(".exec") or name in names:
            reject("E_LEDGER", f"unsafe or duplicate exec identity at row {number}")
        names.add(name)
        if row["required"] != "true":
            reject("E_LEDGER", f"non-required exec row at line {number}")
        owners = row["expected_session_owners"].split(",")
        try:
            expected = int(row["expected_session_count"])
        except ValueError:
            reject("E_LEDGER", f"invalid session count at row {number}")
        if expected <= 0 or expected != len(owners) or len(owners) != len(set(owners)):
            reject("E_LEDGER", f"session owners/count differ at row {number}")
        session_count += expected
    if session_count != 48 or ledger_contract.get("expected_sessions") != 48:
        reject("E_LEDGER", f"expected exact 48 sessions, found {session_count}")
    return rows


def locate_jacoco_artifacts() -> tuple[Path, Path]:
    home = Path.home()
    agent = home / ".m2/repository/org/jacoco/org.jacoco.agent" / EXPECTED_JACOCO_VERSION / (
        f"org.jacoco.agent-{EXPECTED_JACOCO_VERSION}-runtime.jar"
    )
    core = home / ".m2/repository/org/jacoco/org.jacoco.core" / EXPECTED_JACOCO_VERSION / (
        f"org.jacoco.core-{EXPECTED_JACOCO_VERSION}.jar"
    )
    regular_file(agent, "E_AGENT_MISSING")
    regular_file(core, "E_CORE_MISSING")
    if sha256_file(agent) != EXPECTED_AGENT_SHA256:
        reject("E_AGENT_SHA", f"JaCoCo agent hash differs: {agent}")
    if sha256_file(core) != EXPECTED_CORE_SHA256:
        reject("E_CORE_SHA", f"JaCoCo core hash differs: {core}")
    return agent.resolve(), core.resolve()


def compile_inspector(repo_root: Path, core_jar: Path) -> tuple[Path, str, str]:
    source = repo_root / "scripts/v934/step4/JaCoCoExecInspector.java"
    regular_file(source, "E_INSPECTOR_SOURCE")
    source_sha = sha256_file(source)
    target_root = repo_root / "target"
    if target_root.exists() or target_root.is_symlink():
        if target_root.is_symlink() or not target_root.is_dir():
            reject("E_INSPECTOR_COMPILE", f"tooling target is not a real directory: {target_root}")
    else:
        target_root.mkdir(mode=0o755)
    tooling_root = target_root / "v934-step4-tooling"
    if tooling_root.exists() or tooling_root.is_symlink():
        if tooling_root.is_symlink() or not tooling_root.is_dir():
            reject("E_INSPECTOR_COMPILE", f"tooling root is not a real directory: {tooling_root}")
    else:
        tooling_root.mkdir(mode=0o755)
    inspector_root = tooling_root / "inspector"
    if inspector_root.exists() or inspector_root.is_symlink():
        if inspector_root.is_symlink() or not inspector_root.is_dir():
            reject("E_INSPECTOR_COMPILE", f"inspector root is not a real directory: {inspector_root}")
    else:
        inspector_root.mkdir(mode=0o755)
    try:
        classes = Path(tempfile.mkdtemp(prefix=f"{source_sha}-", dir=inspector_root))
    except OSError as exc:
        reject("E_INSPECTOR_COMPILE", f"cannot create fresh inspector output: {exc.__class__.__name__}")
    output = classes / "JaCoCoExecInspector.class"
    process = subprocess.run(
        [
            "javac",
            "-encoding",
            "UTF-8",
            "-cp",
            str(core_jar),
            "-d",
            str(classes),
            str(source),
        ],
        cwd=repo_root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if process.returncode != 0:
        reject("E_INSPECTOR_COMPILE", process.stderr.strip() or "javac failed")
    regular_file(output, "E_INSPECTOR_COMPILE")
    entries = list(classes.iterdir())
    if entries != [output]:
        reject("E_INSPECTOR_COMPILE", "fresh inspector output contains unexpected entries")
    return classes, source_sha, sha256_file(output)


def decode_field(value: str) -> str:
    try:
        padding = "=" * ((4 - len(value) % 4) % 4)
        return base64.urlsafe_b64decode(value + padding).decode("utf-8")
    except (ValueError, UnicodeDecodeError) as exc:
        reject("E_EXEC_PARSE", f"invalid inspector base64 field: {exc}")


def decode_probe_bitmap(value: str, probe_count: int) -> bytes:
    try:
        padding = "=" * ((4 - len(value) % 4) % 4)
        bitmap = base64.urlsafe_b64decode(value + padding)
    except (ValueError, base64.binascii.Error) as exc:
        reject("E_EXEC_PARSE", f"invalid probe bitmap: {exc}")
    expected_size = (probe_count + 7) // 8
    if len(bitmap) != expected_size:
        reject(
            "E_EXEC_PARSE",
            f"probe bitmap size differs: expected={expected_size} actual={len(bitmap)}",
        )
    unused_bits = expected_size * 8 - probe_count
    if unused_bits and bitmap and bitmap[-1] >> (8 - unused_bits):
        reject("E_EXEC_PARSE", "probe bitmap contains set padding bits")
    return bitmap


def inspect_exec(
    path: Path,
    *,
    repo_root: Path,
    classes: Path,
    core_jar: Path,
) -> dict[str, Any]:
    stat = regular_file(path, "E_EXEC_MISSING")
    if stat.st_size == 0:
        reject("E_EXEC_EMPTY", f"empty exec: {path}")
    process = subprocess.run(
        [
            "java",
            "-cp",
            f"{classes}{os.pathsep}{core_jar}",
            "JaCoCoExecInspector",
            str(path.resolve()),
        ],
        cwd=repo_root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if process.returncode != 0:
        detail = process.stderr.strip().splitlines()
        reject("E_EXEC_CORRUPT", detail[-1] if detail else f"reader failed: {path}")

    sessions: list[dict[str, Any]] = []
    classes_data: list[dict[str, Any]] = []
    for number, line in enumerate(process.stdout.splitlines(), 1):
        fields = line.split("\t")
        if len(fields) == 4 and fields[0] == "S":
            try:
                start = int(fields[2])
                dump = int(fields[3])
            except ValueError:
                reject("E_EXEC_PARSE", f"invalid session timestamp at line {number}")
            sessions.append({"id": decode_field(fields[1]), "start_ms": start, "dump_ms": dump})
        elif len(fields) == 6 and fields[0] == "C":
            try:
                probe_count = int(fields[3])
                covered_probes = int(fields[4])
                class_id = int(fields[1], 16)
            except ValueError:
                reject("E_EXEC_PARSE", f"invalid class data at line {number}")
            name = decode_field(fields[2])
            if probe_count <= 0 or covered_probes < 0 or covered_probes > probe_count:
                reject("E_EXEC_PARSE", f"invalid probe counters for {name}")
            probe_bitmap = decode_probe_bitmap(fields[5], probe_count)
            if sum(byte.bit_count() for byte in probe_bitmap) != covered_probes:
                reject("E_EXEC_PARSE", f"probe bitmap/count differs for {name}")
            classes_data.append(
                {
                    "id": f"{class_id:016x}",
                    "name": name,
                    "probe_count": probe_count,
                    "covered_probes": covered_probes,
                    "probe_bitmap": probe_bitmap,
                }
            )
        else:
            reject("E_EXEC_PARSE", f"unexpected inspector output at line {number}")
    if not sessions:
        reject("E_SESSION", f"exec contains no session info: {path}")
    if not classes_data or not any(row["covered_probes"] for row in classes_data):
        reject("E_EXEC_NO_COVERAGE", f"exec contains no covered probes: {path}")
    session_ids = [row["id"] for row in sessions]
    if len(session_ids) != len(set(session_ids)):
        reject("E_SESSION_DUPLICATE", f"duplicate session id in {path}")
    class_keys = [(row["name"], row["id"]) for row in classes_data]
    if len(class_keys) != len(set(class_keys)):
        reject("E_CLASS_DUPLICATE", f"duplicate execution class data in {path}")
    return {
        "stat": stat,
        "sessions": sessions,
        "classes": classes_data,
        "sha256": sha256_file(path),
    }


def inspect_class_tree(
    root: Path,
    *,
    repo_root: Path,
    classes: Path,
    core_jar: Path,
) -> list[dict[str, str]]:
    if root.is_symlink() or not root.is_dir():
        reject("E_CLASS_TREE", f"missing real class tree: {root}")
    process = subprocess.run(
        [
            "java",
            "-cp",
            f"{classes}{os.pathsep}{core_jar}",
            "JaCoCoExecInspector",
            "--class-tree",
            str(root.resolve()),
        ],
        cwd=repo_root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if process.returncode != 0:
        detail = process.stderr.strip().splitlines()
        reject("E_CLASS_TREE", detail[-1] if detail else f"class reader failed: {root}")
    result: list[dict[str, str]] = []
    for number, line in enumerate(process.stdout.splitlines(), 1):
        fields = line.split("\t")
        if len(fields) != 3 or fields[0] != "F":
            reject("E_CLASS_TREE", f"unexpected class inspector output at line {number}")
        try:
            class_id = int(fields[1], 16)
        except ValueError:
            reject("E_CLASS_TREE", f"invalid class ID at line {number}")
        result.append({"id": f"{class_id:016x}", "name": decode_field(fields[2])})
    if not result:
        reject("E_CLASS_TREE", f"class tree is empty: {root}")
    names = [row["name"] for row in result]
    if len(names) != len(set(names)):
        reject("E_CLASS_TREE", f"duplicate class name in tree: {root}")
    return result


def frozen_production_modules(repo_root: Path) -> list[str]:
    freeze = load_json(repo_root / "scripts/v934/contract-freeze.json")
    reactor = freeze.get("reactor")
    if not isinstance(reactor, dict) or reactor.get("module_count") != 24:
        reject("E_CLASS_TREE", "Step 1 frozen reactor is not an exact 24-module set")
    modules = reactor.get("modules")
    if not isinstance(modules, list) or len(modules) != 24 or len(set(modules)) != 24:
        reject("E_CLASS_TREE", "Step 1 frozen reactor module list is invalid")
    if not all(isinstance(module, str) and module and ".." not in Path(module).parts for module in modules):
        reject("E_CLASS_TREE", "Step 1 frozen reactor contains an unsafe module path")
    return modules


def class_file_freshness(
    root: Path,
    expected_names: set[str],
    *,
    not_before_ns: int | None,
) -> tuple[int, int, dict[str, str]]:
    actual_names: set[str] = set()
    class_hashes: dict[str, str] = {}
    oldest = 0
    newest = 0
    now_ns = time.time_ns()
    try:
        walk = os.walk(root, followlinks=False)
        for directory_text, directory_names, file_names in walk:
            directory = Path(directory_text)
            directory_stat = directory.lstat()
            if stat.S_ISLNK(directory_stat.st_mode) or not stat.S_ISDIR(directory_stat.st_mode):
                reject("E_CLASS_TREE", f"class tree contains a non-real directory: {directory}")
            for name in directory_names:
                child = directory / name
                child_stat = child.lstat()
                if stat.S_ISLNK(child_stat.st_mode) or not stat.S_ISDIR(child_stat.st_mode):
                    reject("E_CLASS_TREE", f"class tree contains a non-real directory: {child}")
            for name in file_names:
                if not name.endswith(".class"):
                    continue
                child = directory / name
                child_stat = child.lstat()
                if stat.S_ISLNK(child_stat.st_mode) or not stat.S_ISREG(child_stat.st_mode):
                    reject("E_CLASS_TREE", f"class tree contains a non-regular class file: {child}")
                if child_stat.st_size <= 0:
                    reject("E_CLASS_TREE", f"class tree contains an empty class file: {child}")
                relative = child.relative_to(root).as_posix()
                class_name = relative[:-len(".class")]
                if not class_name or class_name in actual_names:
                    reject("E_CLASS_TREE", f"duplicate or empty class identity: {class_name!r}")
                actual_names.add(class_name)
                class_hashes[class_name] = sha256_file(child)
                oldest = child_stat.st_mtime_ns if oldest == 0 else min(oldest, child_stat.st_mtime_ns)
                newest = max(newest, child_stat.st_mtime_ns)
                if not_before_ns is not None and (
                    child_stat.st_mtime_ns < not_before_ns
                    or child_stat.st_mtime_ns > now_ns + 5_000_000_000
                ):
                    reject("E_CLASS_TREE_STALE", f"class file is outside the fresh compile window: {child}")
    except OSError as exc:
        reject("E_CLASS_TREE", f"cannot walk class tree {root}: {exc.__class__.__name__}")
    if actual_names != expected_names:
        reject(
            "E_CLASS_TREE",
            "Python/JaCoCo class-tree inventories differ: "
            f"missing={sorted(expected_names - actual_names)[:10]} "
            f"unexpected={sorted(actual_names - expected_names)[:10]}",
        )
    if not actual_names or oldest <= 0 or newest < oldest:
        reject("E_CLASS_TREE", f"class tree has no valid class timestamps: {root}")
    return oldest, newest, class_hashes


def inspect_workspace_classes(
    repo_root: Path,
    *,
    classes: Path,
    core_jar: Path,
    not_before_ns: int | None,
) -> tuple[dict[str, dict[str, str]], dict[str, int], int, int]:
    workspace_classes: dict[str, dict[str, str]] = {}
    module_class_counts: dict[str, int] = {}
    oldest = 0
    newest = 0
    for module in frozen_production_modules(repo_root):
        module_root = repo_root / module / "target/classes"
        module_rows = inspect_class_tree(
            module_root,
            repo_root=repo_root,
            classes=classes,
            core_jar=core_jar,
        )
        module_oldest, module_newest, module_class_hashes = class_file_freshness(
            module_root,
            {row["name"] for row in module_rows},
            not_before_ns=not_before_ns,
        )
        oldest = module_oldest if oldest == 0 else min(oldest, module_oldest)
        newest = max(newest, module_newest)
        module_class_counts[module] = len(module_rows)
        for class_row in module_rows:
            existing = workspace_classes.get(class_row["name"])
            if existing is not None:
                reject(
                    "E_CLASS_TREE_DUPLICATE",
                    f"workspace class {class_row['name']} exists in {existing['module']} and {module}",
                )
            workspace_classes[class_row["name"]] = {
                "id": class_row["id"],
                "sha256": module_class_hashes[class_row["name"]],
                "module": module,
            }
    return workspace_classes, module_class_counts, oldest, newest


def workspace_class_tree_sha256(workspace_classes: dict[str, dict[str, str]]) -> str:
    return hashlib.sha256(
        "".join(
            f"{row['module']}\t{name}\t{row['sha256']}\t{row['id']}\n"
            for name, row in sorted(workspace_classes.items())
        ).encode("utf-8")
    ).hexdigest()


def atomic_json(path: Path, value: dict[str, Any]) -> None:
    if path.exists() or path.is_symlink():
        reject("E_OUTPUT_EXISTS", f"refusing to overwrite output: {path}")
    parent = path.parent
    try:
        parent_stat = parent.lstat()
    except FileNotFoundError:
        reject("E_OUTPUT_DIR", f"missing output directory: {parent}")
    if stat.S_ISLNK(parent_stat.st_mode) or not stat.S_ISDIR(parent_stat.st_mode):
        reject("E_OUTPUT_DIR", f"output parent is not a real directory: {parent}")
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
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


def require_exact_sessions(
    actual_sessions: set[str], expected_sessions: set[str], label: str
) -> None:
    if actual_sessions != expected_sessions:
        reject(
            "E_SESSION_SET",
            f"session set differs for {label}: "
            f"missing={sorted(expected_sessions - actual_sessions)} "
            f"unexpected={sorted(actual_sessions - expected_sessions)}",
        )


def require_consistent_class_ids(class_ids: dict[str, set[str]]) -> None:
    mismatches = {name: sorted(ids) for name, ids in class_ids.items() if len(ids) != 1}
    if mismatches:
        preview = dict(list(sorted(mismatches.items()))[:5])
        reject("E_CLASS_ID_MISMATCH", f"class IDs differ across exec files: {preview}")


def validate_run_context(
    repo_root: Path,
    path: Path,
    *,
    run_id: str,
    not_before_ns: int,
) -> dict[str, str]:
    if not path.is_absolute():
        reject("E_RUN_CONTEXT", "run context path must be absolute")
    expected = repo_root / "target/v934-step4-coverage/runs" / run_id / "run-context.json"
    if path.resolve() != expected.resolve():
        reject("E_RUN_CONTEXT", "run context is not at the canonical run-owned path")
    context = load_json(path)
    require_manifest_keys(
        context,
        {
            "schema_version",
            "kind",
            "authority_kind",
            "run_id",
            "git_head",
            "contract_sha256",
            "source_sha256",
            "not_before_ns",
            "started_at",
        },
        "run context",
    )
    if (
        context["schema_version"] != 1
        or type(context["schema_version"]) is not int
        or context["kind"] != "v934-step4-run-context"
        or context["authority_kind"] != "step4-coverage"
        or context["run_id"] != run_id
        or context["not_before_ns"] != not_before_ns
        or type(context["not_before_ns"]) is not int
        or context["not_before_ns"] <= 0
    ):
        reject("E_RUN_CONTEXT", "run context identity/boundary differs")
    for key, pattern in (
        ("git_head", r"[0-9a-f]{40}"),
        ("contract_sha256", r"[0-9a-f]{64}"),
        ("source_sha256", r"[0-9a-f]{64}"),
        ("started_at", r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z"),
    ):
        if not isinstance(context[key], str) or re.fullmatch(pattern, context[key]) is None:
            reject("E_RUN_CONTEXT", f"invalid run context field: {key}")
    current_head = subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "--verify", "HEAD^{commit}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if current_head.returncode != 0 or current_head.stdout.strip() != context["git_head"]:
        reject("E_RUN_CONTEXT", "run context Git HEAD differs from current HEAD")
    contract_path = repo_root / "scripts/v934/step4/coverage-contract.json"
    if context["contract_sha256"] != sha256_file(contract_path):
        reject("E_RUN_CONTEXT", "run context coverage contract hash differs")
    source_process = subprocess.run(
        [
            sys.executable,
            str(repo_root / "scripts/v934/step4/coverage_tool.py"),
            "source-hash",
            "--repo-root",
            str(repo_root),
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    if source_process.returncode != 0:
        reject("E_RUN_CONTEXT", "cannot reproduce clean tracked source seal")
    try:
        source_result = json.loads(source_process.stdout)
    except json.JSONDecodeError:
        reject("E_RUN_CONTEXT", "source seal tool returned invalid JSON")
    if source_result.get("sha256") != context["source_sha256"]:
        reject("E_RUN_CONTEXT", "run context source seal differs from current tracked bytes")
    return {
        "run_context_sha256": sha256_file(path),
        "git_head": context["git_head"],
        "source_sha256": context["source_sha256"],
    }


def require_manifest_keys(value: dict[str, Any], expected: set[str], label: str) -> None:
    if set(value) != expected:
        reject(
            "E_MANIFEST",
            f"{label} keys differ: expected={sorted(expected)} actual={sorted(value)}",
        )


def validate_toolchain_receipt(
    repo_root: Path,
    run_id: str,
    git_head: str,
) -> str:
    path = (
        repo_root
        / "target/v934-step4-coverage/runs"
        / run_id
        / "toolchain-receipt.json"
    )
    value = load_json(path)
    require_manifest_keys(
        value,
        {
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
        },
        "toolchain receipt",
    )
    if (
        value["schema_version"] != 1
        or type(value["schema_version"]) is not int
        or value["kind"] != "v934-step4-toolchain-receipt"
        or value["status"] != "verified"
        or value["run_id"] != run_id
        or value["git_head"] != git_head
    ):
        reject("E_TOOLCHAIN_RECEIPT", "toolchain receipt identity/status differs")
    tool_path = repo_root / "scripts/v934/step4/toolchain_receipt_tool.py"
    if value["tool_sha256"] != sha256_file(tool_path):
        reject("E_TOOLCHAIN_RECEIPT", "toolchain receipt tool hash differs")
    if value["step1_contract_freeze_sha256"] != sha256_file(
        repo_root / "scripts/v934/contract-freeze.json"
    ):
        reject("E_TOOLCHAIN_RECEIPT", "toolchain receipt Step 1 binding differs")
    return sha256_file(path)


def validate_class_universe(
    path: Path,
    *,
    repo_root: Path,
    run_id: str,
    not_before_ns: int,
    context_binding: dict[str, str],
    toolchain_receipt_sha256: str,
    inspector_source_sha256: str,
    inspector_class_sha256: str,
    core_jar_sha256: str,
    workspace_classes: dict[str, dict[str, str]],
    module_class_counts: dict[str, int],
) -> dict[str, Any]:
    expected_path = repo_root / "target/v934-step4-coverage/runs" / run_id / "class-universe.json"
    if not path.is_absolute() or path.resolve() != expected_path.resolve():
        reject("E_CLASS_UNIVERSE", "class-universe evidence is not at its canonical run-owned path")
    value = load_json(path)
    require_manifest_keys(
        value,
        {
            "schema_version",
            "kind",
            "status",
            "run_id",
            "not_before_ns",
            "run_context_sha256",
            "git_head",
            "source_sha256",
            "toolchain_receipt_sha256",
            "jacoco",
            "module_count",
            "class_count",
            "module_class_counts",
            "class_tree_sha256",
            "oldest_class_mtime_ns",
            "newest_class_mtime_ns",
            "freshness_semantics",
            "class_hash_semantics",
        },
        "class universe",
    )
    if (
        value["schema_version"] != 1
        or type(value["schema_version"]) is not int
        or value["kind"] != "v934-step4-fresh-class-universe"
        or value["status"] != "verified"
        or value["run_id"] != run_id
        or value["not_before_ns"] != not_before_ns
        or type(value["not_before_ns"]) is not int
        or value["not_before_ns"] <= 0
    ):
        reject("E_CLASS_UNIVERSE", "class-universe identity/boundary differs")
    for key, expected in context_binding.items():
        if value.get(key) != expected:
            reject("E_CLASS_UNIVERSE", f"class-universe {key} differs from run context")
    if value["toolchain_receipt_sha256"] != toolchain_receipt_sha256:
        reject("E_CLASS_UNIVERSE", "class-universe toolchain receipt binding differs")
    jacoco = value["jacoco"]
    if not isinstance(jacoco, dict) or set(jacoco) != {
        "version",
        "core_jar_sha256",
        "inspector_source_sha256",
        "inspector_class_sha256",
    }:
        reject("E_CLASS_UNIVERSE", "class-universe JaCoCo binding schema differs")
    if jacoco != {
        "version": EXPECTED_JACOCO_VERSION,
        "core_jar_sha256": core_jar_sha256,
        "inspector_source_sha256": inspector_source_sha256,
        "inspector_class_sha256": inspector_class_sha256,
    }:
        reject("E_CLASS_UNIVERSE", "class-universe JaCoCo binding differs")
    expected_tree_sha = workspace_class_tree_sha256(workspace_classes)
    if (
        value["module_count"] != 24
        or type(value["module_count"]) is not int
        or value["class_count"] != len(workspace_classes)
        or type(value["class_count"]) is not int
        or value["module_class_counts"] != module_class_counts
        or value["class_tree_sha256"] != expected_tree_sha
        or not isinstance(value["class_tree_sha256"], str)
        or re.fullmatch(r"[0-9a-f]{64}", value["class_tree_sha256"]) is None
    ):
        reject("E_CLASS_UNIVERSE_DRIFT", "fresh class universe differs from current production bytecode")
    oldest = value["oldest_class_mtime_ns"]
    newest = value["newest_class_mtime_ns"]
    if (
        type(oldest) is not int
        or type(newest) is not int
        or oldest < not_before_ns
        or newest < oldest
        or value["freshness_semantics"] != "controlled-main-bytecode-clean-then-full-reactor-compile"
        or value["class_hash_semantics"] != "module-name-class-sha256-and-jacoco-crc64-id"
    ):
        reject("E_CLASS_UNIVERSE", "class-universe freshness proof differs")
    return value


def seal_classes(args: argparse.Namespace) -> None:
    repo_root = args.repo_root.resolve()
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", args.run_id or "") is None:
        reject("E_CLASS_UNIVERSE", "unsafe run id")
    if type(args.not_before_ns) is not int or args.not_before_ns <= 0:
        reject("E_CLASS_UNIVERSE", "not-before boundary must be a positive integer")
    run_root = repo_root / "target/v934-step4-coverage/runs" / args.run_id
    expected_output = run_root / "class-universe.json"
    if not args.output.is_absolute() or args.output.resolve() != expected_output.resolve():
        reject("E_CLASS_UNIVERSE", "class-universe output is not canonical")
    context_binding = validate_run_context(
        repo_root,
        args.run_context,
        run_id=args.run_id,
        not_before_ns=args.not_before_ns,
    )
    toolchain_receipt_sha = validate_toolchain_receipt(
        repo_root,
        args.run_id,
        context_binding["git_head"],
    )
    _, core_jar = locate_jacoco_artifacts()
    inspector_classes, inspector_sha, inspector_class_sha = compile_inspector(repo_root, core_jar)
    workspace_classes, module_class_counts, oldest, newest = inspect_workspace_classes(
        repo_root,
        classes=inspector_classes,
        core_jar=core_jar,
        not_before_ns=args.not_before_ns,
    )
    result = {
        "schema_version": 1,
        "kind": "v934-step4-fresh-class-universe",
        "status": "verified",
        "run_id": args.run_id,
        "not_before_ns": args.not_before_ns,
        **context_binding,
        "toolchain_receipt_sha256": toolchain_receipt_sha,
        "jacoco": {
            "version": EXPECTED_JACOCO_VERSION,
            "core_jar_sha256": sha256_file(core_jar),
            "inspector_source_sha256": inspector_sha,
            "inspector_class_sha256": inspector_class_sha,
        },
        "module_count": len(module_class_counts),
        "class_count": len(workspace_classes),
        "module_class_counts": module_class_counts,
        "class_tree_sha256": workspace_class_tree_sha256(workspace_classes),
        "oldest_class_mtime_ns": oldest,
        "newest_class_mtime_ns": newest,
        "freshness_semantics": "controlled-main-bytecode-clean-then-full-reactor-compile",
        "class_hash_semantics": "module-name-class-sha256-and-jacoco-crc64-id",
    }
    atomic_json(args.output, result)
    print(
        f"[v934-coverage-exec] CLASS-UNIVERSE PASS modules={len(module_class_counts)} "
        f"classes={len(workspace_classes)} output={args.output}"
    )


def verify_classes(args: argparse.Namespace) -> None:
    repo_root = args.repo_root.resolve()
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", args.run_id or "") is None:
        reject("E_CLASS_UNIVERSE", "unsafe run id")
    if type(args.not_before_ns) is not int or args.not_before_ns <= 0:
        reject("E_CLASS_UNIVERSE", "not-before boundary must be a positive integer")
    context_binding = validate_run_context(
        repo_root,
        args.run_context,
        run_id=args.run_id,
        not_before_ns=args.not_before_ns,
    )
    toolchain_receipt_sha = validate_toolchain_receipt(
        repo_root,
        args.run_id,
        context_binding["git_head"],
    )
    _, core_jar = locate_jacoco_artifacts()
    inspector_classes, inspector_sha, inspector_class_sha = compile_inspector(repo_root, core_jar)
    workspace_classes, module_class_counts, _, _ = inspect_workspace_classes(
        repo_root,
        classes=inspector_classes,
        core_jar=core_jar,
        not_before_ns=args.not_before_ns,
    )
    value = validate_class_universe(
        args.class_universe,
        repo_root=repo_root,
        run_id=args.run_id,
        not_before_ns=args.not_before_ns,
        context_binding=context_binding,
        toolchain_receipt_sha256=toolchain_receipt_sha,
        inspector_source_sha256=inspector_sha,
        inspector_class_sha256=inspector_class_sha,
        core_jar_sha256=sha256_file(core_jar),
        workspace_classes=workspace_classes,
        module_class_counts=module_class_counts,
    )
    print(
        f"[v934-coverage-exec] CLASS-UNIVERSE VERIFY PASS modules={value['module_count']} "
        f"classes={value['class_count']} sha256={sha256_file(args.class_universe)}"
    )


def load_verified_exec_manifest(
    path: Path,
    *,
    repo_root: Path,
    rows: list[dict[str, str]],
) -> dict[str, Any]:
    manifest = load_json(path)
    require_manifest_keys(
        manifest,
        {
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
        },
        "exec manifest",
    )
    if manifest["schema_version"] != 1 or type(manifest["schema_version"]) is not int:
        reject("E_MANIFEST", "exec manifest schema differs")
    if manifest["kind"] != "v934-step4-exec-manifest" or manifest["status"] != "verified":
        reject("E_MANIFEST", "exec manifest identity/status differs")
    run_id = manifest["run_id"]
    session_prefix = manifest["session_prefix"]
    for label, value in (("run id", run_id), ("session prefix", session_prefix)):
        if not isinstance(value, str) or not value or not value[0].isalnum() or not all(
            character.isalnum() or character in "._-" for character in value
        ):
            reject("E_MANIFEST", f"unsafe {label}")
    if run_id != session_prefix:
        reject("E_MANIFEST", "run id and session prefix must be identical")
    if type(manifest["not_before_ns"]) is not int or manifest["not_before_ns"] <= 0:
        reject("E_MANIFEST", "not-before boundary must be a positive integer")
    contract_path = repo_root / "scripts/v934/step4/coverage-contract.json"
    ledger_path = repo_root / "scripts/v934/step4/coverage-exec-ledger.tsv"
    if manifest["coverage_contract_sha256"] != sha256_file(contract_path):
        reject("E_MANIFEST_PROVENANCE", "coverage contract hash differs")
    if manifest["coverage_ledger_sha256"] != sha256_file(ledger_path):
        reject("E_MANIFEST_PROVENANCE", "coverage ledger hash differs")
    if manifest["exec_count"] != 23 or manifest["session_count"] != 48:
        reject("E_MANIFEST", "exec/session totals must be exact 23/48")
    for key, pattern in (
        ("run_context_sha256", r"[0-9a-f]{64}"),
        ("git_head", r"[0-9a-f]{40}"),
        ("source_sha256", r"[0-9a-f]{64}"),
        ("fresh_class_universe_sha256", r"[0-9a-f]{64}"),
        ("toolchain_receipt_sha256", r"[0-9a-f]{64}"),
    ):
        if not isinstance(manifest[key], str) or re.fullmatch(pattern, manifest[key]) is None:
            reject("E_MANIFEST", f"invalid manifest provenance field: {key}")
    class_universe_path = (
        repo_root
        / "target/v934-step4-coverage/runs"
        / run_id
        / "class-universe.json"
    )
    if manifest["fresh_class_universe_sha256"] != sha256_file(class_universe_path):
        reject("E_MANIFEST_PROVENANCE", "fresh class-universe hash differs")
    toolchain_receipt_sha = validate_toolchain_receipt(
        repo_root,
        run_id,
        manifest["git_head"],
    )
    if manifest["toolchain_receipt_sha256"] != toolchain_receipt_sha:
        reject("E_MANIFEST_PROVENANCE", "toolchain receipt hash differs")
    jacoco = manifest["jacoco"]
    if not isinstance(jacoco, dict) or set(jacoco) != {
        "version",
        "agent_jar_sha256",
        "core_jar_sha256",
        "inspector_source_sha256",
        "inspector_class_sha256",
    }:
        reject("E_MANIFEST", "exec manifest JaCoCo tool binding differs")
    if jacoco["version"] != EXPECTED_JACOCO_VERSION:
        reject("E_MANIFEST", "exec manifest JaCoCo version differs")
    for key in (
        "agent_jar_sha256",
        "core_jar_sha256",
        "inspector_source_sha256",
        "inspector_class_sha256",
    ):
        if not isinstance(jacoco[key], str) or re.fullmatch(r"[0-9a-f]{64}", jacoco[key]) is None:
            reject("E_MANIFEST", f"invalid JaCoCo tool hash: {key}")
    exec_rows = manifest["exec_files"]
    if not isinstance(exec_rows, list) or len(exec_rows) != len(rows):
        reject("E_MANIFEST", "exec manifest rows differ from the exact ledger")
    expected_names = [row["exec_file"] for row in rows]
    actual_names = [row.get("exec_file") if isinstance(row, dict) else None for row in exec_rows]
    if actual_names != expected_names:
        reject("E_MANIFEST", "exec manifest file order/set differs from the ledger")
    return manifest


def verify_merged_execution_data(
    inputs: list[dict[str, Any]], aggregate: dict[str, Any]
) -> dict[str, int]:
    expected_sessions: set[tuple[str, int, int]] = set()
    expected_classes: dict[str, dict[str, Any]] = {}
    for inspected in inputs:
        for session in inspected["sessions"]:
            identity = (session["id"], session["start_ms"], session["dump_ms"])
            if identity in expected_sessions:
                reject("E_AGGREGATE_SESSION_DUPLICATE", f"duplicate input session: {identity[0]}")
            expected_sessions.add(identity)
        for row in inspected["classes"]:
            current = expected_classes.get(row["name"])
            if current is None:
                expected_classes[row["name"]] = {
                    "id": row["id"],
                    "probe_count": row["probe_count"],
                    "probe_bitmap": bytearray(row["probe_bitmap"]),
                }
                continue
            if current["id"] != row["id"] or current["probe_count"] != row["probe_count"]:
                reject(
                    "E_AGGREGATE_CLASS_SHAPE",
                    f"input class shape differs for {row['name']}",
                )
            for index, value in enumerate(row["probe_bitmap"]):
                current["probe_bitmap"][index] |= value

    actual_sessions = {
        (session["id"], session["start_ms"], session["dump_ms"])
        for session in aggregate["sessions"]
    }
    if actual_sessions != expected_sessions:
        expected_ids = {row[0] for row in expected_sessions}
        actual_ids = {row[0] for row in actual_sessions}
        reject(
            "E_AGGREGATE_SESSION_SET",
            "aggregate session rows differ from the input union: "
            f"missing={sorted(expected_ids - actual_ids)} unexpected={sorted(actual_ids - expected_ids)}",
        )
    actual_classes = {row["name"]: row for row in aggregate["classes"]}
    if set(actual_classes) != set(expected_classes):
        reject(
            "E_AGGREGATE_CLASS_SET",
            "aggregate class set differs from the input union: "
            f"missing={sorted(set(expected_classes) - set(actual_classes))[:10]} "
            f"unexpected={sorted(set(actual_classes) - set(expected_classes))[:10]}",
        )
    covered_probe_count = 0
    for name, expected in expected_classes.items():
        actual = actual_classes[name]
        if actual["id"] != expected["id"] or actual["probe_count"] != expected["probe_count"]:
            reject("E_AGGREGATE_CLASS_SHAPE", f"aggregate class shape differs for {name}")
        if actual["probe_bitmap"] != bytes(expected["probe_bitmap"]):
            reject("E_AGGREGATE_PROBE_UNION", f"aggregate probe union differs for {name}")
        covered_probe_count += actual["covered_probes"]
    return {
        "session_count": len(actual_sessions),
        "execution_class_count": len(actual_classes),
        "covered_probe_count": covered_probe_count,
    }


def verify_exec_set(args: argparse.Namespace) -> None:
    repo_root = args.repo_root.resolve()
    step4_dir = repo_root / "scripts/v934/step4"
    contract_path = step4_dir / "coverage-contract.json"
    contract = load_json(contract_path)
    rows = load_ledger(step4_dir, contract)
    for label, value in (("run id", args.run_id), ("session prefix", args.session_prefix)):
        if not isinstance(value, str) or re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]*", value) is None:
            reject("E_SESSION_PREFIX", f"unsafe {label}")
    if args.run_id != args.session_prefix:
        reject("E_SESSION_PREFIX", "run id and session prefix must be identical")
    if type(args.not_before_ns) is not int or args.not_before_ns <= 0:
        reject("E_RUN_CONTEXT", "not-before boundary must be a positive integer")
    exec_root = args.exec_root
    if not exec_root.is_absolute():
        reject("E_EXEC_ROOT", "exec root must be absolute")
    if exec_root.is_symlink() or not exec_root.is_dir():
        reject("E_EXEC_ROOT", f"exec root must be a real directory: {exec_root}")
    exec_root = exec_root.resolve()
    run_root = repo_root / "target/v934-step4-coverage/runs" / args.run_id
    expected_root = run_root / "exec"
    if exec_root != expected_root.resolve():
        reject("E_EXEC_ROOT", f"exec root is not the canonical run path: {exec_root}")
    if not args.output.is_absolute() or args.output.resolve() != (run_root / "exec-manifest.json").resolve():
        reject("E_EXEC_ROOT", "exec manifest output is not the canonical run-owned path")
    context_binding = validate_run_context(
        repo_root,
        args.run_context,
        run_id=args.run_id,
        not_before_ns=args.not_before_ns,
    )

    toolchain_receipt_sha = validate_toolchain_receipt(
        repo_root,
        args.run_id,
        context_binding["git_head"],
    )
    expected_names = {row["exec_file"] for row in rows}
    actual_entries = list(exec_root.iterdir())
    actual_names = {entry.name for entry in actual_entries}
    if actual_names != expected_names:
        reject(
            "E_EXEC_SET",
            f"exact exec set differs: missing={sorted(expected_names - actual_names)} "
            f"unexpected={sorted(actual_names - expected_names)}",
        )
    agent_jar, core_jar = locate_jacoco_artifacts()
    inspector_classes, inspector_sha, inspector_class_sha = compile_inspector(repo_root, core_jar)
    not_before_ns = args.not_before_ns
    now_ns = time.time_ns()
    class_ids: dict[str, set[str]] = {}
    execution_classes_by_file: dict[str, list[dict[str, Any]]] = {}
    manifests: list[dict[str, Any]] = []
    total_sessions = 0
    for row in rows:
        path = exec_root / row["exec_file"]
        inspected = inspect_exec(
            path, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
        )
        stat: os.stat_result = inspected["stat"]
        if stat.st_mtime_ns < not_before_ns or stat.st_mtime_ns > now_ns + 5_000_000_000:
            reject("E_EXEC_MTIME", f"exec mtime is outside the run window: {path}")
        owners = row["expected_session_owners"].split(",")
        try:
            expected_count = int(row["expected_session_count"])
        except ValueError:
            reject("E_LEDGER", f"invalid session count for {path.name}")
        expected_sessions = {
            f"{args.session_prefix}-{row['variant_key']}-{owner}" for owner in owners
        }
        actual_sessions = {session["id"] for session in inspected["sessions"]}
        if len(owners) != expected_count:
            reject("E_LEDGER", f"session owner cardinality differs for {path.name}")
        require_exact_sessions(actual_sessions, expected_sessions, path.name)
        for session in inspected["sessions"]:
            if session["start_ms"] <= 0 or session["dump_ms"] < session["start_ms"]:
                reject("E_SESSION_TIME", f"invalid session time in {path.name}")
            if session["start_ms"] * 1_000_000 < not_before_ns:
                reject("E_SESSION_STALE", f"session predates run marker in {path.name}")
        for class_row in inspected["classes"]:
            class_ids.setdefault(class_row["name"], set()).add(class_row["id"])
        execution_classes_by_file[path.name] = inspected["classes"]
        class_shape = hashlib.sha256(
            "".join(
                f"{item['name']}\t{item['id']}\t{item['probe_count']}\n"
                for item in inspected["classes"]
            ).encode("utf-8")
        ).hexdigest()
        manifests.append(
            {
                "exec_file": path.name,
                "runner": row["runner"],
                "lane": row["lane"],
                "variant_key": row["variant_key"],
                "sha256": inspected["sha256"],
                "size": stat.st_size,
                "mtime_ns": stat.st_mtime_ns,
                "sessions": sorted(actual_sessions),
                "session_count": len(actual_sessions),
                "execution_class_count": len(inspected["classes"]),
                "covered_probe_count": sum(
                    item["covered_probes"] for item in inspected["classes"]
                ),
                "class_shape_sha256": class_shape,
            }
        )
        total_sessions += len(actual_sessions)
    require_consistent_class_ids(class_ids)
    workspace_classes, module_class_counts, _, _ = inspect_workspace_classes(
        repo_root,
        classes=inspector_classes,
        core_jar=core_jar,
        not_before_ns=not_before_ns,
    )
    class_universe_path = (
        repo_root
        / "target/v934-step4-coverage/runs"
        / args.run_id
        / "class-universe.json"
    )
    validate_class_universe(
        class_universe_path,
        repo_root=repo_root,
        run_id=args.run_id,
        not_before_ns=not_before_ns,
        context_binding=context_binding,
        toolchain_receipt_sha256=toolchain_receipt_sha,
        inspector_source_sha256=inspector_sha,
        inspector_class_sha256=inspector_class_sha,
        core_jar_sha256=sha256_file(core_jar),
        workspace_classes=workspace_classes,
        module_class_counts=module_class_counts,
    )
    for manifest in manifests:
        matched = 0
        for class_row in execution_classes_by_file[manifest["exec_file"]]:
            workspace = workspace_classes.get(class_row["name"])
            if workspace is None:
                continue
            matched += 1
            if workspace["id"] != class_row["id"]:
                reject(
                    "E_CLASS_TREE_MISMATCH",
                    f"{manifest['exec_file']} class {class_row['name']} id={class_row['id']} "
                    f"current={workspace['id']}",
                )
        if matched == 0:
            reject(
                "E_CLASS_TREE_COVERAGE",
                f"exec contains no current workspace production class: {manifest['exec_file']}",
            )
        manifest["workspace_execution_class_count"] = matched
    if total_sessions != 48:
        reject("E_SESSION_COUNT", f"expected 48 sessions, found {total_sessions}")
    manifest = {
        "schema_version": 1,
        "kind": "v934-step4-exec-manifest",
        "run_id": args.run_id,
        "session_prefix": args.session_prefix,
        "not_before_ns": not_before_ns,
        **context_binding,
        "fresh_class_universe_sha256": sha256_file(class_universe_path),
        "toolchain_receipt_sha256": toolchain_receipt_sha,
        "coverage_contract_sha256": sha256_file(contract_path),
        "coverage_ledger_sha256": sha256_file(step4_dir / "coverage-exec-ledger.tsv"),
        "jacoco": {
            "version": EXPECTED_JACOCO_VERSION,
            "agent_jar_sha256": sha256_file(agent_jar),
            "core_jar_sha256": sha256_file(core_jar),
            "inspector_source_sha256": inspector_sha,
            "inspector_class_sha256": inspector_class_sha,
        },
        "exec_count": len(manifests),
        "session_count": total_sessions,
        "unique_execution_classes": len(class_ids),
        "workspace_class_count": len(workspace_classes),
        "module_class_counts": module_class_counts,
        "workspace_class_tree_sha256": workspace_class_tree_sha256(workspace_classes),
        "exec_files": manifests,
        "status": "verified",
    }
    atomic_json(args.output, manifest)
    print(
        f"[v934-coverage-exec] PASS exec={len(manifests)} "
        f"sessions={total_sessions} classes={len(class_ids)} output={args.output}"
    )


def verify_aggregate(args: argparse.Namespace) -> None:
    repo_root = args.repo_root.resolve()
    step4_dir = repo_root / "scripts/v934/step4"
    contract = load_json(step4_dir / "coverage-contract.json")
    rows = load_ledger(step4_dir, contract)
    manifest_path = args.exec_manifest
    aggregate_path = args.aggregate_exec
    output_path = args.output
    for label, path in (
        ("exec manifest", manifest_path),
        ("aggregate exec", aggregate_path),
        ("aggregate output", output_path),
    ):
        if not path.is_absolute():
            reject("E_AGGREGATE_PATH", f"{label} path must be absolute")
    manifest = load_verified_exec_manifest(
        manifest_path, repo_root=repo_root, rows=rows
    )
    run_id = manifest["run_id"]
    run_root = repo_root / "target/v934-step4-coverage/runs" / run_id
    context_binding = validate_run_context(
        repo_root,
        run_root / "run-context.json",
        run_id=run_id,
        not_before_ns=manifest["not_before_ns"],
    )
    for key, value in context_binding.items():
        if manifest[key] != value:
            reject("E_MANIFEST_PROVENANCE", f"exec manifest {key} differs from run context")
    expected_manifest = run_root / "exec-manifest.json"
    expected_aggregate = run_root / "report/jacoco-aggregate.exec"
    expected_output = run_root / "report/aggregate-provenance.json"
    if manifest_path.resolve() != expected_manifest.resolve():
        reject("E_AGGREGATE_PATH", "exec manifest is not the canonical run-owned path")
    if aggregate_path.resolve() != expected_aggregate.resolve():
        reject("E_AGGREGATE_PATH", "aggregate exec is not the canonical run-owned path")
    if output_path.resolve() != expected_output.resolve():
        reject("E_AGGREGATE_PATH", "aggregate provenance output is not canonical")
    exec_root = run_root / "exec"
    if exec_root.is_symlink() or not exec_root.is_dir():
        reject("E_EXEC_ROOT", f"exec root must be a real directory: {exec_root}")
    regular_file(aggregate_path, "E_AGGREGATE_MISSING")
    if aggregate_path.stat().st_size <= 0:
        reject("E_AGGREGATE_EMPTY", f"aggregate exec is empty: {aggregate_path}")

    agent_jar, core_jar = locate_jacoco_artifacts()
    inspector_classes, inspector_sha, inspector_class_sha = compile_inspector(repo_root, core_jar)
    if (
        manifest["jacoco"]["inspector_source_sha256"] != inspector_sha
        or manifest["jacoco"]["inspector_class_sha256"] != inspector_class_sha
        or manifest["jacoco"]["agent_jar_sha256"] != sha256_file(agent_jar)
        or manifest["jacoco"]["core_jar_sha256"] != sha256_file(core_jar)
    ):
        reject("E_MANIFEST_PROVENANCE", "fresh inspector source/class binding differs")
    workspace_classes, module_class_counts, _, _ = inspect_workspace_classes(
        repo_root,
        classes=inspector_classes,
        core_jar=core_jar,
        not_before_ns=manifest["not_before_ns"],
    )
    workspace_tree_sha = workspace_class_tree_sha256(workspace_classes)
    validate_class_universe(
        run_root / "class-universe.json",
        repo_root=repo_root,
        run_id=run_id,
        not_before_ns=manifest["not_before_ns"],
        context_binding=context_binding,
        toolchain_receipt_sha256=manifest["toolchain_receipt_sha256"],
        inspector_source_sha256=inspector_sha,
        inspector_class_sha256=inspector_class_sha,
        core_jar_sha256=sha256_file(core_jar),
        workspace_classes=workspace_classes,
        module_class_counts=module_class_counts,
    )
    if manifest["fresh_class_universe_sha256"] != sha256_file(run_root / "class-universe.json"):
        reject("E_CLASS_UNIVERSE_DRIFT", "exec manifest fresh class-universe hash differs")
    if (
        manifest["workspace_class_count"] != len(workspace_classes)
        or manifest["module_class_counts"] != module_class_counts
        or manifest["workspace_class_tree_sha256"] != workspace_tree_sha
    ):
        reject("E_CLASS_TREE_DRIFT", "workspace class tree changed after exec verification")
    manifest_rows = manifest["exec_files"]
    input_inspections: list[dict[str, Any]] = []
    input_provenance: list[dict[str, Any]] = []
    newest_input_mtime = 0
    for ledger_row, manifest_row in zip(rows, manifest_rows):
        if not isinstance(manifest_row, dict):
            reject("E_MANIFEST", "exec manifest row must be an object")
        path = exec_root / ledger_row["exec_file"]
        inspected = inspect_exec(
            path, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
        )
        stat: os.stat_result = inspected["stat"]
        newest_input_mtime = max(newest_input_mtime, stat.st_mtime_ns)
        if manifest_row.get("sha256") != inspected["sha256"]:
            reject("E_AGGREGATE_INPUT_DRIFT", f"input hash changed: {path.name}")
        if manifest_row.get("size") != stat.st_size or manifest_row.get("mtime_ns") != stat.st_mtime_ns:
            reject("E_AGGREGATE_INPUT_DRIFT", f"input stat changed: {path.name}")
        actual_sessions = sorted(session["id"] for session in inspected["sessions"])
        if manifest_row.get("sessions") != actual_sessions:
            reject("E_AGGREGATE_INPUT_DRIFT", f"input sessions changed: {path.name}")
        input_inspections.append(inspected)
        input_provenance.append(
            {
                "exec_file": path.name,
                "sha256": inspected["sha256"],
                "size": stat.st_size,
            }
        )

    aggregate = inspect_exec(
        aggregate_path,
        repo_root=repo_root,
        classes=inspector_classes,
        core_jar=core_jar,
    )
    aggregate_stat: os.stat_result = aggregate["stat"]
    if aggregate_stat.st_mtime_ns < newest_input_mtime:
        reject("E_AGGREGATE_STALE", "aggregate exec predates a verified input exec")
    totals = verify_merged_execution_data(input_inspections, aggregate)
    if totals["session_count"] != 48:
        reject(
            "E_AGGREGATE_SESSION_SET",
            f"aggregate exec must contain exact 48 sessions, found {totals['session_count']}",
        )
    result = {
        "schema_version": 1,
        "kind": "v934-step4-aggregate-exec-provenance",
        "run_id": run_id,
        "run_context_sha256": manifest["run_context_sha256"],
        "git_head": manifest["git_head"],
        "source_sha256": manifest["source_sha256"],
        "toolchain_receipt_sha256": manifest["toolchain_receipt_sha256"],
        "exec_manifest_sha256": sha256_file(manifest_path),
        "coverage_contract_sha256": manifest["coverage_contract_sha256"],
        "coverage_ledger_sha256": manifest["coverage_ledger_sha256"],
        "inspector_source_sha256": inspector_sha,
        "inspector_class_sha256": inspector_class_sha,
        "input_exec_count": len(input_provenance),
        "input_exec_files": input_provenance,
        "aggregate_exec": {
            "path": aggregate_path.relative_to(repo_root).as_posix(),
            "sha256": aggregate["sha256"],
            "size": aggregate_stat.st_size,
            **totals,
        },
        "merge_semantics": "exact-session-and-probe-bitmap-union",
        "status": "verified",
    }
    atomic_json(output_path, result)
    print(
        "[v934-coverage-exec] AGGREGATE PASS "
        f"inputs={len(input_provenance)} sessions={totals['session_count']} "
        f"classes={totals['execution_class_count']} output={output_path}"
    )


def expect_failure(code: str, action: Any) -> dict[str, str]:
    try:
        action()
    except CoverageError as exc:
        if exc.code != code:
            reject("E_NEGATIVE_CODE", f"expected {code}, got {exc.code}: {exc.message}")
        return {"expected_code": code, "observed_code": exc.code, "status": "passed"}
    reject("E_NEGATIVE_FALSE_GREEN", f"mutation did not fail: {code}")


def run_negatives(args: argparse.Namespace) -> None:
    repo_root = args.repo_root.resolve()
    fixture = args.fixture_exec.resolve()
    agent_jar, core_jar = locate_jacoco_artifacts()
    del agent_jar
    inspector_classes, inspector_sha, inspector_class_sha = compile_inspector(repo_root, core_jar)
    valid = inspect_exec(
        fixture, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
    )
    output_dir = args.output_dir
    if output_dir.exists() or output_dir.is_symlink():
        reject("E_OUTPUT_EXISTS", f"refusing to overwrite output directory: {output_dir}")
    output_dir.mkdir(parents=True)
    cases: dict[str, dict[str, str]] = {}

    missing = output_dir / "missing.exec"
    cases["missing"] = expect_failure(
        "E_EXEC_MISSING",
        lambda: inspect_exec(
            missing, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
        ),
    )
    empty = output_dir / "empty.exec"
    empty.touch()
    cases["empty"] = expect_failure(
        "E_EXEC_EMPTY",
        lambda: inspect_exec(
            empty, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
        ),
    )
    corrupt = output_dir / "corrupt.exec"
    corrupt.write_bytes(b"not-a-jacoco-exec\n")
    cases["corrupt"] = expect_failure(
        "E_EXEC_CORRUPT",
        lambda: inspect_exec(
            corrupt, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
        ),
    )
    truncated = output_dir / "truncated.exec"
    fixture_bytes = fixture.read_bytes()
    if len(fixture_bytes) < 2:
        reject("E_NEGATIVE_FIXTURE", "fixture is too small for a truncation probe")
    # Removing the final byte guarantees an incomplete terminal block. Cutting
    # at an arbitrary midpoint can accidentally land on a valid block boundary.
    truncated.write_bytes(fixture_bytes[:-1])
    cases["truncated"] = expect_failure(
        "E_EXEC_CORRUPT",
        lambda: inspect_exec(
            truncated, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
        ),
    )
    symlink = output_dir / "symlink.exec"
    symlink.symlink_to(fixture)
    cases["symlink"] = expect_failure(
        "E_EXEC_TYPE",
        lambda: inspect_exec(
            symlink, repo_root=repo_root, classes=inspector_classes, core_jar=core_jar
        ),
    )
    session_ids = {row["id"] for row in valid["sessions"]}
    cases["wrong-session"] = expect_failure(
        "E_SESSION_SET",
        lambda: require_exact_sessions(
            session_ids, {"intentionally-wrong-session"}, fixture.name
        ),
    )
    classes_by_name: dict[str, set[str]] = {}
    for row in valid["classes"]:
        classes_by_name.setdefault(row["name"], set()).add(row["id"])
    first_name = next(iter(classes_by_name))
    first_id = next(iter(classes_by_name[first_name]))
    forged_id = "0000000000000000" if first_id != "0000000000000000" else "ffffffffffffffff"
    classes_by_name[first_name].add(forged_id)
    cases["class-id-mismatch"] = expect_failure(
        "E_CLASS_ID_MISMATCH",
        lambda: require_consistent_class_ids(classes_by_name),
    )
    result = {
        "schema_version": 1,
        "kind": "v934-step4-exec-negative-result",
        "fixture_exec_sha256": sha256_file(fixture),
        "inspector_source_sha256": inspector_sha,
        "inspector_class_sha256": inspector_class_sha,
        "cases": cases,
        "case_count": len(cases),
        "status": "passed",
    }
    result_path = output_dir / "negative-result.json"
    atomic_json(result_path, result)
    print(f"[v934-coverage-exec] NEGATIVE PASS cases={len(cases)} output={result_path}")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="command", required=True)
    seal = commands.add_parser(
        "seal-classes",
        help="seal the freshly compiled exact 24-module production class universe",
    )
    seal.add_argument("--repo-root", type=Path, required=True)
    seal.add_argument("--run-id", required=True)
    seal.add_argument("--not-before-ns", type=int, required=True)
    seal.add_argument("--run-context", type=Path, required=True)
    seal.add_argument("--output", type=Path, required=True)
    seal.set_defaults(function=seal_classes)

    verify_class_tree = commands.add_parser(
        "verify-classes",
        help="recompute and verify the run-owned fresh production class universe",
    )
    verify_class_tree.add_argument("--repo-root", type=Path, required=True)
    verify_class_tree.add_argument("--run-id", required=True)
    verify_class_tree.add_argument("--not-before-ns", type=int, required=True)
    verify_class_tree.add_argument("--run-context", type=Path, required=True)
    verify_class_tree.add_argument("--class-universe", type=Path, required=True)
    verify_class_tree.set_defaults(function=verify_classes)

    verify = commands.add_parser("verify", help="verify the exact canonical 23-exec set")
    verify.add_argument("--repo-root", type=Path, required=True)
    verify.add_argument("--exec-root", type=Path, required=True)
    verify.add_argument("--run-id", required=True)
    verify.add_argument("--session-prefix", required=True)
    verify.add_argument("--not-before-ns", type=int, required=True)
    verify.add_argument("--run-context", type=Path, required=True)
    verify.add_argument("--output", type=Path, required=True)
    verify.set_defaults(function=verify_exec_set)

    aggregate = commands.add_parser(
        "verify-aggregate",
        help="prove an aggregate exec is the exact session/probe union of the 23 verified inputs",
    )
    aggregate.add_argument("--repo-root", type=Path, required=True)
    aggregate.add_argument("--exec-manifest", type=Path, required=True)
    aggregate.add_argument("--aggregate-exec", type=Path, required=True)
    aggregate.add_argument("--output", type=Path, required=True)
    aggregate.set_defaults(function=verify_aggregate)

    negative = commands.add_parser("negative", help="prove corrupt/missing evidence fails closed")
    negative.add_argument("--repo-root", type=Path, required=True)
    negative.add_argument("--fixture-exec", type=Path, required=True)
    negative.add_argument("--output-dir", type=Path, required=True)
    negative.set_defaults(function=run_negatives)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        args.function(args)
    except CoverageError as exc:
        print(f"[v934-coverage-exec] ERROR {exc.code}: {exc.message}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
