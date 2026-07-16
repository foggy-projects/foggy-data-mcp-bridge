#!/usr/bin/env python3
"""Build one fail-closed, run-owned Step 4 report inventory.

The tool does not discover arbitrary report directories.  For one safe run id
it consumes only the canonical Unit, Integration, Step 3 required-matrix, and
Addon companion authorities.  Existing validators are reused before the
cross-authority union is constructed.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import importlib.util
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Iterable, Sequence


PREFIX = "[v934-step4-report-inventory]"
RUN_ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
SHA256_RE = re.compile(r"[0-9a-f]{64}")
GIT_HEAD_RE = re.compile(r"[0-9a-f]{40}")
ENV_KEY_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")

STEP2_VIEW_TOOL = Path("scripts/v934/step4/step2_report_view_tool.py")
STEP2_REPORT_TOOL = Path("scripts/v934/step2_report_tool.py")
STEP3_OVERLAY_TOOL = Path("scripts/v934/step4/successor/overlay_tool.py")
STEP3_REPORT_TOOL = Path("scripts/v934/step4/successor/step3_required_report_tool.py")
STEP3_CONTRACT = Path("scripts/v934/step4/successor/step3-required-contract.json")
ADDON_REPORT_TOOL = Path("scripts/v934/step3/preagg_addon_lifecycle_report_tool.py")
ADDON_CONTRACT = Path("scripts/v934/step4/successor/preagg-addon-lifecycle-contract.json")
COVERAGE_TOOL = Path("scripts/v934/step4/coverage_tool.py")
COVERAGE_CONTRACT = Path("scripts/v934/step4/coverage-contract.json")
DEFERRED_INVENTORY = Path("scripts/v934/successor/step2/deferred-step3.tsv")
SELF_TOOL = Path("scripts/v934/step4/report_inventory_tool.py")
TOOL_BINDING_PATHS = (
    SELF_TOOL,
    COVERAGE_TOOL,
    STEP2_VIEW_TOOL,
    STEP2_REPORT_TOOL,
    STEP3_OVERLAY_TOOL,
    STEP3_REPORT_TOOL,
    STEP3_CONTRACT,
    ADDON_REPORT_TOOL,
    ADDON_CONTRACT,
    DEFERRED_INVENTORY,
)

EXPECTED = {
    "unit": {
        "runner": "surefire",
        "positive": 681,
        "structural": 55,
        "testcases": 4941,
        "variants": ["unit"],
    },
    "integration": {
        "runner": "failsafe",
        "positive": 47,
        "structural": 4,
        "testcases": 320,
        "variants": [
            "caffeine-sqlite",
            "hermetic",
            "sqlite-broad",
            "sqlite-harness",
            "sqlite-lifecycle",
            "sqlite-refresh",
        ],
    },
    "step2": {"positive": 728, "structural": 59, "testcases": 5261},
    "step3": {"positive": 45, "testcases": 446},
    "required": {"positive": 773, "structural": 59, "testcases": 5707},
    "addon": {"reports": 2, "testcases": 6, "variants": 2},
}


class InventoryError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def reject(code: str, message: str) -> None:
    raise InventoryError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def safe_run_id(value: str) -> str:
    require(
        RUN_ID_RE.fullmatch(value or "") is not None and value not in {".", ".."},
        "E_PATH",
        f"unsafe run id: {value!r}",
    )
    return value


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        reject("E_IO", f"cannot hash {path}: {exc.__class__.__name__}")
    return digest.hexdigest()


def digest_strings(values: Sequence[str]) -> str:
    return hashlib.sha256("".join(f"{value}\n" for value in values).encode()).hexdigest()


def strict_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            reject("E_JSON", f"duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_constant(value: str) -> None:
    reject("E_JSON", f"non-finite JSON number: {value}")


def read_json(path: Path, code: str = "E_JSON") -> dict[str, Any]:
    regular_file(path, code, str(path))
    try:
        value = json.loads(
            path.read_text(encoding="utf-8"),
            object_pairs_hook=strict_object,
            parse_constant=reject_constant,
        )
    except InventoryError:
        raise
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        reject(code, f"cannot read strict JSON {path}: {exc.__class__.__name__}")
    require(type(value) is dict, code, f"JSON root is not an object: {path}")
    return value


def exact_keys(value: Any, expected: Iterable[str], code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict, code, f"{label} is not an object")
    expected_set = set(expected)
    actual = set(value)
    require(
        actual == expected_set,
        code,
        f"{label} fields differ missing={sorted(expected_set - actual)} extra={sorted(actual - expected_set)}",
    )
    return value


def exact_int(value: Any, code: str, label: str, *, minimum: int = 0) -> int:
    require(type(value) is int and value >= minimum, code, f"{label} is not an integer >= {minimum}")
    return value


def exact_string(value: Any, code: str, label: str) -> str:
    require(type(value) is str and value and "\x00" not in value and "\r" not in value and "\n" not in value, code, f"{label} is not a safe non-empty string")
    return value


def safe_repo_relative_text(value: Any, code: str, label: str) -> str:
    text = exact_string(value, code, label)
    require("\\" not in text, code, f"{label} is not a canonical POSIX path")
    candidate = PurePosixPath(text)
    require(
        not candidate.is_absolute()
        and candidate.as_posix() == text
        and text != "."
        and all(part not in {"", ".", ".."} for part in candidate.parts),
        code,
        f"{label} is not a canonical repository-relative path",
    )
    return text


def require_real_components(root: Path, path: Path, code: str, label: str) -> None:
    try:
        relative = path.relative_to(root)
    except ValueError:
        reject(code, f"{label} escapes repository root")
    current = root
    for part in relative.parts:
        current = current / part
        try:
            mode = current.lstat().st_mode
        except OSError as exc:
            reject(code, f"cannot inspect {label}: {exc.__class__.__name__}")
        require(not stat.S_ISLNK(mode), code, f"{label} contains a symlink: {current}")


def regular_file(path: Path, code: str, label: str) -> Path:
    try:
        mode = path.lstat().st_mode
    except OSError:
        reject(code, f"missing file: {label}")
    require(stat.S_ISREG(mode) and not stat.S_ISLNK(mode), code, f"not a real regular file: {label}")
    return path


def real_directory(path: Path, code: str, label: str) -> Path:
    try:
        mode = path.lstat().st_mode
    except OSError:
        reject(code, f"missing directory: {label}")
    require(stat.S_ISDIR(mode) and not stat.S_ISLNK(mode), code, f"not a real directory: {label}")
    return path


def repo_relative(root: Path, path: Path) -> str:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        reject("E_PATH", f"path escapes repository: {path}")


def read_env(path: Path, expected: set[str] | None = None, code: str = "E_STATUS") -> dict[str, str]:
    regular_file(path, code, str(path))
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as exc:
        reject(code, f"cannot read env evidence {path}: {exc.__class__.__name__}")
    result: dict[str, str] = {}
    for line in lines:
        require(line and "=" in line, code, f"malformed env line in {path}")
        key, value = line.split("=", 1)
        require(ENV_KEY_RE.fullmatch(key) is not None, code, f"unsafe env key: {key!r}")
        require(key not in result, code, f"duplicate env key: {key}")
        require("\r" not in value and "\n" not in value, code, f"multiline env value: {key}")
        result[key] = value
    if expected is not None:
        require(set(result) == expected, code, f"env fields differ: {path}")
    return result


def read_tsv(path: Path, code: str = "E_TSV") -> tuple[list[str], list[dict[str, str]]]:
    regular_file(path, code, str(path))
    try:
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            header = list(reader.fieldnames or [])
            rows = [dict(row) for row in reader]
    except (OSError, UnicodeError, csv.Error) as exc:
        reject(code, f"cannot read TSV {path}: {exc.__class__.__name__}")
    require(header and len(header) == len(set(header)), code, f"invalid TSV header: {path}")
    require(
        all(all(value is not None for value in row.values()) for row in rows),
        code,
        f"malformed TSV row: {path}",
    )
    return header, rows


def parse_timestamp(value: Any, code: str, label: str) -> dt.datetime:
    require(type(value) is str and value, code, f"missing timestamp: {label}")
    candidate = value[:-1] + "+00:00" if value.endswith("Z") else value
    try:
        parsed = dt.datetime.fromisoformat(candidate)
    except ValueError:
        reject(code, f"invalid timestamp: {label}")
    require(parsed.tzinfo is not None, code, f"timestamp has no timezone: {label}")
    return parsed


def json_bytes(value: dict[str, Any]) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n").encode()


def atomic_publish(path: Path, data: bytes) -> None:
    if path.exists() or path.is_symlink():
        reject("E_OUTPUT", f"refusing to overwrite output: {path}")
    real_directory(path.parent, "E_OUTPUT", "output parent")
    require(path.parent.resolve() == path.parent.absolute(), "E_OUTPUT", "output parent contains a symlink")
    temporary: Path | None = None
    fd = -1
    published = False
    try:
        fd, name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
        temporary = Path(name)
        os.fchmod(fd, 0o644)
        with os.fdopen(fd, "wb", closefd=True) as stream:
            fd = -1
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        # link(2) is the no-clobber publication primitive: unlike replace(), it
        # fails atomically when a file or symlink appeared after the first check.
        os.link(temporary, path, follow_symlinks=False)
        published = True
        temporary.unlink()
        temporary = None
        directory_fd = os.open(path.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    except FileExistsError:
        reject("E_OUTPUT", f"output appeared during publication: {path}")
    except InventoryError:
        raise
    except OSError as exc:
        if published:
            try:
                path.unlink()
                rollback_fd = os.open(path.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
                try:
                    os.fsync(rollback_fd)
                finally:
                    os.close(rollback_fd)
            except OSError:
                reject("E_OUTPUT", f"cannot publish or roll back {path}: {exc.__class__.__name__}")
        reject("E_OUTPUT", f"cannot publish {path}: {exc.__class__.__name__}")
    finally:
        if fd >= 0:
            os.close(fd)
        if temporary is not None:
            try:
                temporary.unlink()
            except OSError:
                pass


def run_checked(command: Sequence[str], root: Path, code: str, label: str) -> str:
    try:
        result = subprocess.run(
            list(command),
            cwd=root,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=900,
            check=False,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        reject(code, f"cannot run {label}: {exc.__class__.__name__}")
    require(result.returncode == 0, code, f"{label} rejected evidence (rc={result.returncode})")
    return result.stdout


def import_step2_tool(root: Path):
    path = root / STEP2_REPORT_TOOL
    regular_file(path, "E_TOOL", STEP2_REPORT_TOOL.as_posix())
    spec = importlib.util.spec_from_file_location("v934_step4_report_inventory_step2", path)
    require(spec is not None and spec.loader is not None, "E_TOOL", "cannot load Step 2 report tool")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    try:
        spec.loader.exec_module(module)
    except Exception as exc:
        reject("E_TOOL", f"cannot import Step 2 report tool: {exc.__class__.__name__}")
    return module


def validate_repo_root(supplied: Path) -> Path:
    root = supplied.absolute()
    real_directory(root, "E_ROOT", "repository root")
    require(root.resolve() == root, "E_ROOT", "repository root is not canonical")
    try:
        git_root = subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "--show-toplevel"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        reject("E_ROOT", "cannot resolve Git worktree root")
    require(git_root == str(root), "E_ROOT", "repository root differs from Git worktree root")
    return root


def current_git_head(root: Path) -> str:
    try:
        value = subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "--verify", "HEAD^{commit}"],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError):
        reject("E_GIT", "cannot resolve committed HEAD")
    require(GIT_HEAD_RE.fullmatch(value) is not None, "E_GIT", "unexpected Git HEAD")
    return value


def require_clean_worktree(root: Path) -> None:
    try:
        status = subprocess.check_output(
            ["git", "-C", str(root), "status", "--porcelain=v1", "--untracked-files=all"],
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except (OSError, subprocess.CalledProcessError):
        reject("E_GIT", "cannot inspect worktree status")
    require(not status, "E_GIT", "formal report inventory requires a clean committed worktree")


def current_source_seal(root: Path) -> dict[str, Any]:
    output = run_checked(
        [sys.executable, str(root / COVERAGE_TOOL), "source-hash", "--repo-root", str(root)],
        root,
        "E_PARENT_CONTEXT",
        "coverage source seal",
    )
    try:
        payload = json.loads(
            output,
            object_pairs_hook=strict_object,
            parse_constant=reject_constant,
        )
    except InventoryError:
        raise
    except json.JSONDecodeError:
        reject("E_PARENT_CONTEXT", "coverage source seal did not return strict JSON")
    payload = exact_keys(
        payload,
        {"command", "file_count", "git_head", "sha256", "status"},
        "E_PARENT_CONTEXT",
        "coverage source seal",
    )
    require(
        payload["command"] == "source-hash"
        and payload["status"] == "passed"
        and type(payload["file_count"]) is int
        and payload["file_count"] > 0
        and type(payload["git_head"]) is str
        and GIT_HEAD_RE.fullmatch(payload["git_head"]) is not None
        and type(payload["sha256"]) is str
        and SHA256_RE.fullmatch(payload["sha256"]) is not None,
        "E_PARENT_CONTEXT",
        "coverage source seal identity is invalid",
    )
    return payload


@dataclass(frozen=True)
class ParentAuthority:
    run_id: str
    git_head: str
    contract_sha256: str
    source_sha256: str
    marker_sha256: str
    not_before_ns: int
    marker: Path
    payload: dict[str, Any]


def validate_parent_authority(root: Path, run_id: str) -> ParentAuthority:
    run_root = root / "target/v934-step4-coverage/runs" / run_id
    real_directory(root / "target", "E_RUN_ROOT", "target")
    real_directory(root / "target/v934-step4-coverage", "E_RUN_ROOT", "Step 4 coverage root")
    real_directory(root / "target/v934-step4-coverage/runs", "E_RUN_ROOT", "Step 4 runs root")
    real_directory(run_root, "E_RUN_ROOT", "Step 4 run root")
    require_real_components(root, run_root, "E_RUN_ROOT", "Step 4 run root")
    marker = run_root / "run-context.json"
    payload = exact_keys(
        read_json(marker, "E_PARENT_CONTEXT"),
        {
            "schema_version", "kind", "authority_kind", "run_id", "git_head",
            "contract_sha256", "source_sha256", "not_before_ns", "started_at",
        },
        "E_PARENT_CONTEXT",
        "Step 4 parent marker",
    )
    contract = root / COVERAGE_CONTRACT
    regular_file(contract, "E_PARENT_CONTEXT", COVERAGE_CONTRACT.as_posix())
    current_head = current_git_head(root)
    source_payload = current_source_seal(root)
    source_sha = source_payload["sha256"]
    require(
        type(payload["schema_version"]) is int
        and payload["schema_version"] == 1
        and payload["kind"] == "v934-step4-run-context"
        and payload["authority_kind"] == "step4-coverage"
        and payload["run_id"] == run_id
        and payload["git_head"] == current_head
        and source_payload["git_head"] == current_head
        and payload["contract_sha256"] == sha256_file(contract)
        and payload["source_sha256"] == source_sha
        and type(payload["not_before_ns"]) is int
        and payload["not_before_ns"] > 0,
        "E_PARENT_CONTEXT",
        "Step 4 parent identity differs from the committed workspace",
    )
    parse_timestamp(payload["started_at"], "E_PARENT_CONTEXT", "parent started_at")
    require(payload["not_before_ns"] <= dt.datetime.now().timestamp() * 1_000_000_000, "E_PARENT_CONTEXT", "parent boundary is in the future")
    marker_stat = marker.stat()
    require(marker_stat.st_mtime_ns >= payload["not_before_ns"], "E_PARENT_CONTEXT", "parent marker predates its boundary")
    return ParentAuthority(
        run_id=run_id,
        git_head=current_head,
        contract_sha256=payload["contract_sha256"],
        source_sha256=source_sha,
        marker_sha256=sha256_file(marker),
        not_before_ns=payload["not_before_ns"],
        marker=marker,
        payload=payload,
    )


@dataclass(frozen=True)
class Step2Authority:
    name: str
    root: Path
    runner: str
    execution_keys: tuple[str, ...]
    report_fqcns: tuple[str, ...]
    structural_fqcns: tuple[str, ...]
    totals: dict[str, int]
    final_manifest: Path
    summary: Path
    run_status: Path
    negative: Path
    outer_marker: Path


def walk_regular_files(root: Path, code: str) -> set[str]:
    result: set[str] = set()
    for current_text, directories, files in os.walk(root, topdown=True, followlinks=False):
        current = Path(current_text)
        for name in list(directories):
            path = current / name
            mode = path.lstat().st_mode
            require(stat.S_ISDIR(mode) and not stat.S_ISLNK(mode), code, f"non-real directory in evidence: {path}")
        for name in files:
            path = current / name
            mode = path.lstat().st_mode
            require(stat.S_ISREG(mode) and not stat.S_ISLNK(mode), code, f"non-real file in evidence: {path}")
            result.add(path.relative_to(root).as_posix())
    return result


def tsv_rows_as_strings(path: Path, expected_header: Sequence[str], code: str) -> list[dict[str, str]]:
    header, rows = read_tsv(path, code)
    require(header == list(expected_header), code, f"TSV header differs: {path}")
    return rows


def string_row(row: dict[str, Any], header: Sequence[str]) -> dict[str, str]:
    return {name: str(row[name]) for name in header}


def validate_step2_final(
    module: Any,
    contract: Any,
    authority: Step2Authority,
    validated_runs: Sequence[Any],
) -> dict[str, Any]:
    manifest_path = authority.final_manifest
    final_root = manifest_path.parent
    manifest = exact_keys(
        read_json(manifest_path, "E_STEP2_FINAL"),
        {
            "schema_version", "kind", "status", "generated_at", "successor",
            "runner", "outer_run", "variant_keys", "expected_execution_count",
            "expected_structural_report_count", "report_count", "structural_report_count",
            "raw_report_count", "totals", "structural_totals", "source_manifests",
            "variant_markers", "reports", "structural_reports",
        },
        "E_STEP2_FINAL",
        f"{authority.name} final manifest",
    )
    parse_timestamp(manifest["generated_at"], "E_STEP2_FINAL", "Step 2 generated_at")
    require(
        manifest["schema_version"] == 3
        and manifest["kind"] == "v934-step2-report-merged"
        and manifest["status"] == "passed"
        and manifest["runner"] == authority.runner
        and manifest["successor"] == module.contract_identity(contract),
        "E_STEP2_FINAL",
        f"{authority.name} final identity differs",
    )

    positives: list[tuple[dict[str, str], dict[str, Any], Any, Path]] = []
    structurals: list[tuple[dict[str, str], dict[str, Any], Any, Path]] = []
    variant_by_name: dict[str, Any] = {}
    for run in validated_runs:
        require(run.variant not in variant_by_name, "E_IDENTITY_DUPLICATE", f"duplicate Step 2 variant: {run.variant}")
        variant_by_name[run.variant] = run
        positives.extend(run.reports)
        structurals.extend(run.structural_reports)
    positives.sort(key=lambda item: item[0]["execution_key"])
    structurals.sort(key=lambda item: item[0]["report_fqcn"])

    expected_reports: list[dict[str, Any]] = []
    expected_structural: list[dict[str, Any]] = []
    expected_testcases: list[dict[str, str]] = []
    expected_files = {
        "report-manifest.json", "outer-run-marker.json", "report-metrics.tsv",
        "structural-report-metrics.tsv", "testcases.tsv",
    }
    for expected, observed, metrics, raw in positives:
        relative = Path("raw-reports") / expected["variant_key"] / expected["owner"] / raw.name
        target = final_root / relative
        regular_file(target, "E_STEP2_FINAL", str(target))
        require(target.read_bytes() == raw.read_bytes(), "E_STEP2_FINAL", f"merged XML differs: {relative}")
        require(target.stat().st_mtime_ns == raw.stat().st_mtime_ns, "E_STEP2_FINAL", f"merged XML mtime differs: {relative}")
        row = dict(observed)
        row["evidence_report"] = relative.as_posix()
        expected_reports.append(row)
        expected_files.add(relative.as_posix())
        for classname, test_name in metrics.testcases:
            expected_testcases.append({
                "execution_key": expected["execution_key"],
                "report_fqcn": expected["report_fqcn"],
                "classname": module.sanitize_tsv(classname),
                "name": module.sanitize_tsv(test_name),
            })
    for expected, observed, _metrics, raw in structurals:
        relative = Path("structural-reports") / expected["variant_key"] / expected["owner"] / raw.name
        target = final_root / relative
        regular_file(target, "E_STEP2_FINAL", str(target))
        require(target.read_bytes() == raw.read_bytes(), "E_STEP2_FINAL", f"merged structural XML differs: {relative}")
        require(target.stat().st_mtime_ns == raw.stat().st_mtime_ns, "E_STEP2_FINAL", f"merged structural XML mtime differs: {relative}")
        row = dict(observed)
        row["evidence_report"] = relative.as_posix()
        expected_structural.append(row)
        expected_files.add(relative.as_posix())

    variants = sorted(variant_by_name)
    require(manifest["variant_keys"] == variants, "E_STEP2_FINAL", "merged variant set differs")
    expected_sources = []
    expected_markers = []
    for variant in variants:
        source = (
            authority.root / "unit/report-manifest.json"
            if authority.name == "unit"
            else authority.root / "variants" / variant / "evidence/report-manifest.json"
        )
        token = module.safe_filename_token(variant, "variant key")
        source_relative = Path("source-manifests") / f"{token}.json"
        marker_relative = Path("run-markers") / f"{token}.marker"
        source_copy = final_root / source_relative
        marker_copy = final_root / marker_relative
        regular_file(source_copy, "E_STEP2_FINAL", str(source_copy))
        regular_file(marker_copy, "E_STEP2_FINAL", str(marker_copy))
        require(source_copy.read_bytes() == source.read_bytes(), "E_STEP2_FINAL", f"source manifest splice: {variant}")
        marker = validated_runs[variants.index(variant)].variant_marker
        require(marker_copy.read_bytes() == marker.read_bytes(), "E_STEP2_FINAL", f"variant marker splice: {variant}")
        expected_sources.append({
            "variant_key": variant,
            "evidence_manifest": source_relative.as_posix(),
            "sha256": sha256_file(source),
        })
        context = read_json(marker, "E_STEP2_FINAL")
        expected_markers.append({
            "variant_key": variant,
            "context": context,
            "evidence_path": marker_relative.as_posix(),
            "mtime_ns": marker.stat().st_mtime_ns,
            "sha256": sha256_file(marker),
        })
        expected_files.update({source_relative.as_posix(), marker_relative.as_posix()})

    outer_copy = final_root / "outer-run-marker.json"
    regular_file(outer_copy, "E_STEP2_FINAL", str(outer_copy))
    require(outer_copy.read_bytes() == authority.outer_marker.read_bytes(), "E_CROSS_RUN_SPLICE", "merged outer marker differs")
    outer_context = read_json(authority.outer_marker, "E_STEP2_FINAL")
    expected_outer = {
        "context": outer_context,
        "evidence_path": "outer-run-marker.json",
        "mtime_ns": authority.outer_marker.stat().st_mtime_ns,
        "sha256": sha256_file(authority.outer_marker),
    }
    totals = {
        name: sum(int(row[name]) for row in expected_reports)
        for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
    }
    structural_totals = {
        name: sum(int(row[name]) for row in expected_structural)
        for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
    }
    require(
        manifest["outer_run"] == expected_outer
        and manifest["source_manifests"] == expected_sources
        and manifest["variant_markers"] == expected_markers
        and manifest["reports"] == expected_reports
        and manifest["structural_reports"] == expected_structural,
        "E_STEP2_FINAL",
        f"{authority.name} merged manifest differs from validated variant evidence",
    )
    require(
        manifest["expected_execution_count"] == len(expected_reports)
        and manifest["report_count"] == len(expected_reports)
        and manifest["expected_structural_report_count"] == len(expected_structural)
        and manifest["structural_report_count"] == len(expected_structural)
        and manifest["raw_report_count"] == len(expected_reports) + len(expected_structural)
        and manifest["totals"] == totals
        and manifest["structural_totals"] == structural_totals,
        "E_STEP2_TOTAL",
        f"{authority.name} merged totals differ",
    )
    require(
        totals["tests"] == totals["testcase_nodes"]
        and totals["failures"] == totals["errors"] == totals["skipped"] == 0
        and structural_totals == {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "testcase_nodes": 0},
        "E_REPORT_OUTCOME",
        f"{authority.name} is not F0/E0/S0 with exact testcase cardinality",
    )

    metric_rows = tsv_rows_as_strings(final_root / "report-metrics.tsv", module.METRICS_HEADER, "E_STEP2_TABLE")
    structural_rows = tsv_rows_as_strings(final_root / "structural-report-metrics.tsv", module.STRUCTURAL_METRICS_HEADER, "E_STEP2_TABLE")
    testcase_rows = tsv_rows_as_strings(final_root / "testcases.tsv", module.TESTCASE_HEADER, "E_STEP2_TABLE")
    require(
        metric_rows == [string_row(row, module.METRICS_HEADER) for row in expected_reports]
        and structural_rows == [string_row(row, module.STRUCTURAL_METRICS_HEADER) for row in expected_structural]
        and testcase_rows == expected_testcases,
        "E_STEP2_TABLE",
        f"{authority.name} evidence tables differ from validated XML",
    )
    require(walk_regular_files(final_root, "E_STEP2_FINAL") == expected_files, "E_STEP2_FINAL", "merged final artifact set differs")
    return {"totals": totals, "structural_totals": structural_totals}


def validate_step2_status(
    module: Any,
    contract: Any,
    authority: Step2Authority,
    parent: ParentAuthority,
    totals: dict[str, Any],
) -> None:
    status_fields = {
        "run_id", "runner", "git_head", "started_at", "finished_at", "last_phase",
        "exit_code", "source_before_sha256", "source_after_sha256",
        "outer_marker_sha256", "successor_manifest_sha256",
        "final_report_manifest_sha256", "status",
    }
    status = read_env(authority.run_status, status_fields)
    parse_timestamp(status["started_at"], "E_STATUS", f"{authority.name} started_at")
    parse_timestamp(status["finished_at"], "E_STATUS", f"{authority.name} finished_at")
    outer = read_json(authority.outer_marker, "E_STATUS")
    successor_hash = sha256_file(parent.marker.parent / "step2-report-view/SHA256SUMS")
    final_hash = sha256_file(authority.final_manifest)
    require(
        status["run_id"] == parent.run_id
        and status["runner"] == authority.runner
        and status["git_head"] == parent.git_head
        and status["started_at"] == outer["started_at"]
        and status["last_phase"] == "completed"
        and status["exit_code"] == "0"
        and status["source_before_sha256"] == parent.source_sha256
        and status["source_after_sha256"] == parent.source_sha256
        and status["outer_marker_sha256"] == sha256_file(authority.outer_marker)
        and status["successor_manifest_sha256"] == successor_hash
        and status["final_report_manifest_sha256"] == final_hash
        and status["status"] == "passed",
        "E_PARENT_BINDING",
        f"{authority.name} durable status differs from Step 4 parent",
    )
    expected = EXPECTED[authority.name]
    summary_expected = {
        "run_id": parent.run_id,
        "runner": authority.runner,
        "git_head": parent.git_head,
        **({"variants": ",".join(expected["variants"])} if authority.name == "integration" else {}),
        "execution_keys": str(expected["positive"]),
        "execution_reports": str(expected["positive"]),
        "structural_reports": str(expected["structural"]),
        "raw_reports": str(expected["positive"] + expected["structural"]),
        "tests": str(expected["testcases"]),
        "failures": "0",
        "errors": "0",
        "skipped": "0",
        "testcase_nodes": str(expected["testcases"]),
        "source_before": parent.source_sha256,
        "source_after": parent.source_sha256,
        "outer_marker_sha256": sha256_file(authority.outer_marker),
        "successor_manifest_sha256": successor_hash,
        "final_report_manifest_sha256": final_hash,
        "run_status_sha256": sha256_file(authority.run_status),
        "status": "passed",
    }
    require(read_env(authority.summary) == summary_expected, "E_STATUS", f"{authority.name} summary differs")
    require(
        totals == {
            "tests": expected["testcases"], "failures": 0, "errors": 0,
            "skipped": 0, "testcase_nodes": expected["testcases"],
        },
        "E_STEP2_TOTAL",
        f"{authority.name} exact total differs",
    )
    header, negatives = read_tsv(authority.negative, "E_NEGATIVE_PROBE")
    require(header == ["probe", "expected_error", "actual_error", "status"], "E_NEGATIVE_PROBE", "Step 2 negative header differs")
    expected_negatives = [
        {"probe": name, "expected_error": error, "actual_error": error, "status": "passed"}
        for name, error in module.NEGATIVE_PROBES
    ]
    require(negatives == expected_negatives, "E_NEGATIVE_PROBE", f"{authority.name} negative probes differ")


def validate_step2_authority(
    root: Path,
    parent: ParentAuthority,
    name: str,
    module: Any,
    contract: Any,
) -> Step2Authority:
    expected = EXPECTED[name]
    runner = expected["runner"]
    authority_root = root / (
        "target/v934-step2-unit/runs" if name == "unit" else "target/v934-step2-integration/runs"
    ) / parent.run_id
    real_directory(authority_root, "E_CHILD_ROOT", f"{name} authority root")
    require_real_components(root, authority_root, "E_CHILD_ROOT", f"{name} authority root")
    walk_regular_files(authority_root, "E_CHILD_ROOT")
    outer_path = authority_root / "run-context.json"
    outer = module.validate_outer_marker(outer_path, contract, runner)
    require(
        outer.context["run_id"] == parent.run_id
        and outer.context["git_head"] == parent.git_head
        and outer.context["source_before_sha256"] == parent.source_sha256
        and outer.mtime_ns >= parent.not_before_ns,
        "E_PARENT_BINDING",
        f"{name} outer tuple does not bind to the Step 4 parent",
    )
    variants = sorted({
        row["variant_key"]
        for row in (*contract.rows, *contract.structural_rows)
        if row["runner"] == runner
    })
    require(variants == expected["variants"], "E_STEP2_CONTRACT", f"{name} variant inventory differs")
    if name == "integration":
        variant_root = real_directory(authority_root / "variants", "E_CHILD_ROOT", "integration variants")
        actual = sorted(entry.name for entry in variant_root.iterdir())
        require(actual == variants, "E_CHILD_ROOT", "integration variant directory set differs")
        require(all(entry.is_dir() and not entry.is_symlink() for entry in variant_root.iterdir()), "E_CHILD_ROOT", "integration variant path is unsafe")
    manifest_paths = [
        authority_root / "unit/report-manifest.json"
        if name == "unit"
        else authority_root / "variants" / variant / "evidence/report-manifest.json"
        for variant in variants
    ]
    validated_runs = []
    try:
        for path in manifest_paths:
            read_json(path, "E_STEP2_EVIDENCE")
            validated_runs.append(module.validate_run_manifest(path, contract, runner, outer))
    except Exception as exc:
        code = getattr(exc, "code", "E_STEP2_EVIDENCE")
        reject(code, f"existing Step 2 validator rejected {name} evidence")
    keys = sorted(expected_row["execution_key"] for run in validated_runs for expected_row, *_ in run.reports)
    fqcns = sorted(expected_row["report_fqcn"] for run in validated_runs for expected_row, *_ in run.reports)
    structural = sorted(expected_row["report_fqcn"] for run in validated_runs for expected_row, *_ in run.structural_reports)
    expected_keys = sorted(row["execution_key"] for row in contract.rows if row["runner"] == runner)
    expected_structural = sorted(row["report_fqcn"] for row in contract.structural_rows if row["runner"] == runner)
    require(keys == expected_keys and len(keys) == len(set(keys)), "E_STEP2_SET", f"{name} positive identity set differs")
    require(structural == expected_structural and len(structural) == len(set(structural)), "E_STEP2_SET", f"{name} structural identity set differs")
    require(not (set(fqcns) & set(structural)), "E_IDENTITY_OVERLAP", f"{name} positive/structural FQCN overlap")
    authority = Step2Authority(
        name=name,
        root=authority_root,
        runner=runner,
        execution_keys=tuple(keys),
        report_fqcns=tuple(fqcns),
        structural_fqcns=tuple(structural),
        totals={},
        final_manifest=authority_root / "final/report-manifest.json",
        summary=authority_root / "summary.env",
        run_status=authority_root / "run-status.env",
        negative=authority_root / "negative/negative-probes.tsv",
        outer_marker=outer_path,
    )
    final = validate_step2_final(module, contract, authority, validated_runs)
    validate_step2_status(module, contract, authority, parent, final["totals"])
    return Step2Authority(**{**authority.__dict__, "totals": final["totals"]})


@dataclass(frozen=True)
class Step3Authority:
    root: Path
    execution_keys: tuple[str, ...]
    report_fqcns: tuple[str, ...]
    addon_identities: tuple[str, ...]
    candidate: Path
    final_manifest: Path
    parent_context: Path
    addon_candidate: Path


def validate_step3_authority(root: Path, parent: ParentAuthority) -> Step3Authority:
    step3_root = root / "target/v934-step3-required-matrix/runs" / parent.run_id
    addon_root = root / "target/v934-step3-preagg-addon/runs" / parent.run_id
    real_directory(step3_root, "E_CHILD_ROOT", "Step 3 required root")
    real_directory(addon_root, "E_CHILD_ROOT", "Addon companion root")
    require_real_components(root, step3_root, "E_CHILD_ROOT", "Step 3 required root")
    require_real_components(root, addon_root, "E_CHILD_ROOT", "Addon companion root")
    walk_regular_files(step3_root, "E_CHILD_ROOT")
    walk_regular_files(addon_root, "E_CHILD_ROOT")
    candidate = step3_root / "candidate-manifest.json"
    addon_candidate = addon_root / "candidate-manifest.json"
    run_checked([sys.executable, str(root / STEP3_OVERLAY_TOOL), "validate"], root, "E_STEP3_VALIDATOR", "Step 3 successor overlay validator")
    run_checked(
        [
            sys.executable, str(root / STEP3_REPORT_TOOL), "--repo-root", str(root),
            "--contract", str(root / STEP3_CONTRACT), "verify-candidate",
            "--candidate", str(candidate),
        ],
        root,
        "E_STEP3_EVIDENCE",
        "Step 3 required candidate validator",
    )
    run_checked(
        [
            sys.executable, str(root / ADDON_REPORT_TOOL), "--root", str(root),
            "--contract", str(root / ADDON_CONTRACT), "verify-candidate",
            "--run-root", str(addon_root),
        ],
        root,
        "E_ADDON_EVIDENCE",
        "Addon companion candidate validator",
    )
    candidate_payload = read_json(candidate, "E_STEP3_EVIDENCE")
    final_path = step3_root / "final/report-manifest.json"
    final = read_json(final_path, "E_STEP3_EVIDENCE")
    require(
        candidate_payload.get("run_id") == parent.run_id
        and candidate_payload.get("git_head") == parent.git_head
        and candidate_payload.get("totals") == {
            "database_variants": 7, "errors": 0, "execution_keys": 45,
            "external_variants": 7, "failures": 0, "reports": 45,
            "skipped": 0, "testcase_nodes": 446,
        },
        "E_PARENT_BINDING",
        "Step 3 candidate identity/totals differ from Step 4 parent",
    )
    parent_context_path = step3_root / "step4-parent-context.env"
    parent_context = read_env(
        parent_context_path,
        {
            "authority_kind", "run_id", "git_head", "contract_sha256",
            "source_sha256", "outer_marker_sha256", "outer_marker_path", "status",
        },
        "E_PARENT_BINDING",
    )
    require(
        parent_context == {
            "authority_kind": "step4-coverage",
            "run_id": parent.run_id,
            "git_head": parent.git_head,
            "contract_sha256": parent.contract_sha256,
            "source_sha256": parent.source_sha256,
            "outer_marker_sha256": parent.marker_sha256,
            "outer_marker_path": str(parent.marker),
            "status": "validated",
        },
        "E_PARENT_BINDING",
        "Step 3 persisted Step 4 parent receipt differs",
    )
    required_rows = final.get("required_execution_keys")
    require(isinstance(required_rows, list) and all(type(row) is dict for row in required_rows), "E_STEP3_SET", "Step 3 required rows are invalid")
    keys = sorted(row.get("execution_key", "") for row in required_rows)
    fqcns = sorted(key.rsplit("|", 1)[-1].split(":", 1)[-1] for key in keys)
    header, deferred = read_tsv(root / DEFERRED_INVENTORY, "E_STEP3_SET")
    require("execution_key" in header and "required" in header, "E_STEP3_SET", "deferred inventory schema differs")
    expected_keys = sorted(row["execution_key"] for row in deferred if row["required"] == "true")
    require(keys == expected_keys and len(keys) == 45 and len(set(keys)) == 45, "E_STEP3_SET", "Step 3 required execution-key set differs")
    require(
        sum(int(row.get("testcase_nodes", -1)) for row in required_rows) == 446
        and all(row.get("failures") == row.get("errors") == row.get("skipped") == 0 for row in required_rows),
        "E_REPORT_OUTCOME",
        "Step 3 required rows are not 45/446 F0/E0/S0",
    )
    addon_payload = read_json(addon_candidate, "E_ADDON_EVIDENCE")
    require(
        addon_payload.get("run_id") == parent.run_id
        and addon_payload.get("git_head") == parent.git_head
        and addon_payload.get("totals") == {
            "errors": 0, "failures": 0, "reports": 2,
            "skipped": 0, "testcase_nodes": 6, "variants": 2,
        },
        "E_ADDON_TOTAL",
        "Addon companion is not 2/6 F0/E0/S0",
    )
    addon_identities: list[str] = []
    for variant in ("mysql57", "sqlite"):
        variant_manifest = read_json(addon_root / "variants" / variant / "report-manifest.json", "E_ADDON_EVIDENCE")
        reports = variant_manifest.get("reports")
        require(isinstance(reports, list) and len(reports) == 1, "E_ADDON_EVIDENCE", f"Addon variant report count differs: {variant}")
        fqcn = reports[0].get("fqcn")
        require(type(fqcn) is str and fqcn, "E_ADDON_EVIDENCE", f"Addon report FQCN is invalid: {variant}")
        addon_identities.append(f"failsafe|preagg-addon-lifecycle|{variant}|{fqcn}")
    addon_identities.sort()
    return Step3Authority(
        root=step3_root,
        execution_keys=tuple(keys),
        report_fqcns=tuple(fqcns),
        addon_identities=tuple(addon_identities),
        candidate=candidate,
        final_manifest=final_path,
        parent_context=parent_context_path,
        addon_candidate=addon_candidate,
    )


def child_binding(parent: ParentAuthority, mode: str) -> dict[str, Any]:
    return {
        "run_id": parent.run_id,
        "git_head": parent.git_head,
        "parent_contract_sha256": parent.contract_sha256,
        "parent_source_sha256": parent.source_sha256,
        "parent_outer_marker_sha256": parent.marker_sha256,
        "binding_mode": mode,
    }


def tool_bindings(root: Path) -> list[dict[str, str]]:
    result = []
    for relative in TOOL_BINDING_PATHS:
        path = regular_file(root / relative, "E_TOOL", relative.as_posix())
        result.append({"path": relative.as_posix(), "sha256": sha256_file(path)})
    return result


def evidence_record(root: Path, path: Path) -> dict[str, Any]:
    regular_file(path, "E_EVIDENCE", str(path))
    return {"path": repo_relative(root, path), "sha256": sha256_file(path), "size_bytes": path.stat().st_size}


def validate_evidence_record(
    value: Any,
    expected_path: str,
    code: str,
    label: str,
) -> dict[str, Any]:
    record = exact_keys(value, {"path", "sha256", "size_bytes"}, code, label)
    path = safe_repo_relative_text(record["path"], code, f"{label}.path")
    require(path == expected_path, code, f"{label} path differs from its canonical authority")
    require(
        type(record["sha256"]) is str and SHA256_RE.fullmatch(record["sha256"]) is not None,
        code,
        f"{label}.sha256 is invalid",
    )
    exact_int(record["size_bytes"], code, f"{label}.size_bytes", minimum=0)
    return record


def validate_tool_bindings_schema(value: Any) -> None:
    require(type(value) is list, "E_TOOL_BINDING", "validator_bindings is not a list")
    expected_paths = [path.as_posix() for path in TOOL_BINDING_PATHS]
    require(len(value) == len(expected_paths), "E_TOOL_BINDING", "validator binding count differs")
    observed_paths: list[str] = []
    for index, (row_value, expected_path) in enumerate(zip(value, expected_paths, strict=True)):
        row = exact_keys(row_value, {"path", "sha256"}, "E_TOOL_BINDING", f"validator binding {index}")
        path = safe_repo_relative_text(row["path"], "E_TOOL_BINDING", f"validator binding {index}.path")
        require(path == expected_path, "E_TOOL_BINDING", f"validator binding order/path differs at index {index}")
        require(
            type(row["sha256"]) is str and SHA256_RE.fullmatch(row["sha256"]) is not None,
            "E_TOOL_BINDING",
            f"validator binding digest is invalid: {path}",
        )
        observed_paths.append(path)
    require(len(observed_paths) == len(set(observed_paths)), "E_TOOL_BINDING", "duplicate validator binding path")


def validate_parent_binding(
    value: Any,
    authority: dict[str, Any],
    outer_marker_sha256: str,
    expected_mode: str,
    label: str,
) -> None:
    binding = exact_keys(
        value,
        {
            "run_id", "git_head", "parent_contract_sha256",
            "parent_source_sha256", "parent_outer_marker_sha256",
            "binding_mode",
        },
        "E_SCHEMA",
        f"{label} parent binding",
    )
    require(
        type(binding["run_id"]) is str
        and binding["run_id"] == authority["run_id"]
        and type(binding["git_head"]) is str
        and binding["git_head"] == authority["git_head"]
        and type(binding["parent_contract_sha256"]) is str
        and binding["parent_contract_sha256"] == authority["contract_sha256"]
        and type(binding["parent_source_sha256"]) is str
        and binding["parent_source_sha256"] == authority["source_sha256"]
        and type(binding["parent_outer_marker_sha256"]) is str
        and binding["parent_outer_marker_sha256"] == outer_marker_sha256
        and type(binding["binding_mode"]) is str
        and binding["binding_mode"] == expected_mode,
        "E_PARENT_BINDING",
        f"spliced parent tuple or binding mode: {label}",
    )


def validate_sorted_unique_strings(value: Any, code: str, label: str) -> list[str]:
    require(type(value) is list, code, f"{label} is not a list")
    for index, item in enumerate(value):
        exact_string(item, code, f"{label}[{index}]")
    require(value == sorted(value) and len(value) == len(set(value)), code, f"{label} is not sorted and unique")
    return value


def validate_required_row(
    value: Any,
    label: str,
    expected: dict[str, Any],
    *,
    structural: bool,
) -> dict[str, Any]:
    fields = {
        "positive_reports", "testcase_nodes", "failures", "errors", "skipped",
        "execution_keys", "execution_keys_sha256",
    }
    if structural:
        fields.update({"structural_reports", "structural_report_fqcns"})
    row = exact_keys(value, fields, "E_SCHEMA", f"{label} required row")
    for field in ("positive_reports", "testcase_nodes", "failures", "errors", "skipped"):
        exact_int(row[field], "E_SCHEMA", f"{label}.{field}")
    keys = validate_sorted_unique_strings(row["execution_keys"], "E_IDENTITY_DUPLICATE", f"{label}.execution_keys")
    require(
        row["positive_reports"] == expected["positive"] and len(keys) == expected["positive"],
        "E_TOTAL",
        f"{label} positive count differs",
    )
    require(row["testcase_nodes"] == expected["testcases"], "E_TOTAL", f"{label} testcase count differs")
    require(
        row["failures"] == row["errors"] == row["skipped"] == 0,
        "E_REPORT_OUTCOME",
        f"{label} is not F0/E0/S0",
    )
    require(
        type(row["execution_keys_sha256"]) is str
        and SHA256_RE.fullmatch(row["execution_keys_sha256"]) is not None
        and row["execution_keys_sha256"] == digest_strings(keys),
        "E_IDENTITY_DIGEST",
        f"{label} key digest differs",
    )
    if structural:
        exact_int(row["structural_reports"], "E_SCHEMA", f"{label}.structural_reports")
        structural_fqcns = validate_sorted_unique_strings(
            row["structural_report_fqcns"],
            "E_IDENTITY_DUPLICATE",
            f"{label}.structural_report_fqcns",
        )
        require(
            row["structural_reports"] == expected["structural"]
            and len(structural_fqcns) == expected["structural"],
            "E_TOTAL",
            f"{label} structural count differs",
        )
    return row


def validate_union_semantics(payload: dict[str, Any]) -> None:
    payload = exact_keys(
        payload,
        {"schema_version", "kind", "status", "run_id", "authority", "validator_bindings", "evidence", "required_union", "addon_companion"},
        "E_SCHEMA",
        "inventory",
    )
    require(
        type(payload["schema_version"]) is int
        and payload["schema_version"] == 1
        and payload["kind"] == "v934-step4-report-inventory"
        and payload["status"] == "passed",
        "E_SCHEMA",
        "inventory identity differs",
    )
    run_id = safe_run_id(exact_string(payload["run_id"], "E_SCHEMA", "inventory.run_id"))
    authority = exact_keys(
        payload["authority"],
        {"kind", "run_id", "git_head", "contract_sha256", "source_sha256", "not_before_ns", "outer_marker"},
        "E_SCHEMA",
        "authority",
    )
    exact_string(authority["run_id"], "E_PARENT_BINDING", "authority.run_id")
    exact_int(authority["not_before_ns"], "E_PARENT_BINDING", "authority.not_before_ns", minimum=1)
    require(
        run_id == authority["run_id"]
        and authority["kind"] == "step4-coverage"
        and type(authority["git_head"]) is str
        and GIT_HEAD_RE.fullmatch(authority["git_head"]) is not None
        and type(authority["contract_sha256"]) is str
        and SHA256_RE.fullmatch(authority["contract_sha256"]) is not None
        and type(authority["source_sha256"]) is str
        and SHA256_RE.fullmatch(authority["source_sha256"]) is not None,
        "E_PARENT_BINDING",
        "inventory authority identity differs",
    )
    coverage_run_root = f"target/v934-step4-coverage/runs/{run_id}"
    outer_marker = validate_evidence_record(
        authority["outer_marker"],
        f"{coverage_run_root}/run-context.json",
        "E_PARENT_BINDING",
        "authority outer marker",
    )
    validate_tool_bindings_schema(payload["validator_bindings"])

    evidence = exact_keys(
        payload["evidence"],
        {"step2_report_view", "unit", "integration", "step3_required"},
        "E_SCHEMA",
        "evidence",
    )
    view = exact_keys(
        evidence["step2_report_view"],
        {"path", "hash_manifest", "positive_reports", "structural_reports", "testcase_nodes"},
        "E_SCHEMA",
        "Step 2 report view evidence",
    )
    require(
        safe_repo_relative_text(view["path"], "E_PATH", "Step 2 report view path") == f"{coverage_run_root}/step2-report-view",
        "E_PATH",
        "Step 2 report view path differs",
    )
    validate_evidence_record(
        view["hash_manifest"],
        f"{coverage_run_root}/step2-report-view/SHA256SUMS",
        "E_EVIDENCE",
        "Step 2 report view hash manifest",
    )
    for field, expected in (("positive_reports", 728), ("structural_reports", 59), ("testcase_nodes", 5261)):
        exact_int(view[field], "E_SCHEMA", f"Step 2 report view.{field}")
        require(view[field] == expected, "E_TOTAL", f"Step 2 report view {field} differs")

    child_layout = {
        "unit": (
            f"target/v934-step2-unit/runs/{run_id}",
            "canonical-run+head+source+not-before",
            {"root", "parent_binding", "outer_marker", "final_manifest", "summary", "run_status", "negative_probes"},
        ),
        "integration": (
            f"target/v934-step2-integration/runs/{run_id}",
            "canonical-run+head+source+not-before",
            {"root", "parent_binding", "outer_marker", "final_manifest", "summary", "run_status", "negative_probes"},
        ),
        "step3_required": (
            f"target/v934-step3-required-matrix/runs/{run_id}",
            "persisted-parent-receipt",
            {"root", "parent_binding", "parent_context", "candidate_manifest", "final_manifest", "addon_candidate_manifest"},
        ),
    }
    for child, (child_root, binding_mode, fields) in child_layout.items():
        record = exact_keys(evidence[child], fields, "E_SCHEMA", f"{child} evidence")
        require(
            safe_repo_relative_text(record["root"], "E_PATH", f"{child}.root") == child_root,
            "E_PATH",
            f"{child} root differs from its canonical authority",
        )
        validate_parent_binding(record["parent_binding"], authority, outer_marker["sha256"], binding_mode, child)
        if child in {"unit", "integration"}:
            validate_evidence_record(record["outer_marker"], f"{child_root}/run-context.json", "E_EVIDENCE", f"{child} outer marker")
            validate_evidence_record(record["final_manifest"], f"{child_root}/final/report-manifest.json", "E_EVIDENCE", f"{child} final manifest")
            validate_evidence_record(record["summary"], f"{child_root}/summary.env", "E_EVIDENCE", f"{child} summary")
            validate_evidence_record(record["run_status"], f"{child_root}/run-status.env", "E_EVIDENCE", f"{child} run status")
            validate_evidence_record(record["negative_probes"], f"{child_root}/negative/negative-probes.tsv", "E_EVIDENCE", f"{child} negative probes")
        else:
            addon_candidate_path = f"target/v934-step3-preagg-addon/runs/{run_id}/candidate-manifest.json"
            validate_evidence_record(record["parent_context"], f"{child_root}/step4-parent-context.env", "E_EVIDENCE", "Step 3 parent context")
            validate_evidence_record(record["candidate_manifest"], f"{child_root}/candidate-manifest.json", "E_EVIDENCE", "Step 3 candidate manifest")
            validate_evidence_record(record["final_manifest"], f"{child_root}/final/report-manifest.json", "E_EVIDENCE", "Step 3 final manifest")
            validate_evidence_record(record["addon_candidate_manifest"], addon_candidate_path, "E_EVIDENCE", "Addon candidate manifest")

    required = exact_keys(
        payload["required_union"],
        {"step2", "step3_required", "combined"},
        "E_SCHEMA",
        "required union",
    )
    step2 = validate_required_row(required["step2"], "step2", EXPECTED["step2"], structural=True)
    step3 = validate_required_row(required["step3_required"], "step3", EXPECTED["step3"], structural=False)
    combined = validate_required_row(required["combined"], "combined", EXPECTED["required"], structural=True)
    step2_keys = set(step2["execution_keys"])
    step3_keys = set(step3["execution_keys"])
    require(not (step2_keys & step3_keys), "E_IDENTITY_OVERLAP", "Step 2 and Step 3 required keys overlap")
    require(combined["execution_keys"] == sorted(step2_keys | step3_keys), "E_UNION", "combined required set is not the exact disjoint union")
    require(combined["structural_report_fqcns"] == step2["structural_report_fqcns"], "E_UNION", "combined structural set differs from Step 2")
    addon = exact_keys(
        payload["addon_companion"],
        {
            "included_in_required_union", "reports", "variants", "testcase_nodes",
            "failures", "errors", "skipped", "report_identities", "candidate_manifest",
        },
        "E_SCHEMA",
        "Addon companion",
    )
    require(type(addon["included_in_required_union"]) is bool and addon["included_in_required_union"] is False, "E_ADDON_SEPARATION", "Addon companion leaked into required union")
    for field in ("reports", "variants", "testcase_nodes", "failures", "errors", "skipped"):
        exact_int(addon[field], "E_SCHEMA", f"Addon companion.{field}")
    identities = validate_sorted_unique_strings(addon["report_identities"], "E_ADDON_TOTAL", "Addon report identities")
    addon_candidate = validate_evidence_record(
        addon["candidate_manifest"],
        f"target/v934-step3-preagg-addon/runs/{run_id}/candidate-manifest.json",
        "E_EVIDENCE",
        "Addon companion candidate manifest",
    )
    require(
        addon_candidate == evidence["step3_required"]["addon_candidate_manifest"],
        "E_EVIDENCE",
        "Addon companion candidate provenance differs between evidence sections",
    )
    require(
        addon["reports"] == EXPECTED["addon"]["reports"]
        and addon["testcase_nodes"] == EXPECTED["addon"]["testcases"]
        and addon["variants"] == EXPECTED["addon"]["variants"]
        and len(identities) == EXPECTED["addon"]["reports"]
        and addon["failures"] == addon["errors"] == addon["skipped"] == 0,
        "E_ADDON_TOTAL",
        "Addon companion is not separate 2/6 F0/E0/S0 evidence",
    )


def build_inventory(root: Path, run_id: str) -> dict[str, Any]:
    safe_run_id(run_id)
    run_checked([sys.executable, str(root / COVERAGE_TOOL), "validate-contract", "--repo-root", str(root)], root, "E_CONTRACT", "coverage contract validator")
    parent = validate_parent_authority(root, run_id)
    run_checked(
        [sys.executable, str(root / STEP2_VIEW_TOOL), "validate", "--repo-root", str(root), "--run-id", run_id],
        root,
        "E_STEP2_VIEW",
        "derived Step 2 report-view validator",
    )
    module = import_step2_tool(root)
    view = parent.marker.parent / "step2-report-view"
    try:
        contract = module.load_successor(view)
    except Exception as exc:
        reject(getattr(exc, "code", "E_STEP2_CONTRACT"), "Step 2 report tool rejected the derived view")
    unit = validate_step2_authority(root, parent, "unit", module, contract)
    integration = validate_step2_authority(root, parent, "integration", module, contract)
    step3 = validate_step3_authority(root, parent)

    step2_keys = sorted((*unit.execution_keys, *integration.execution_keys))
    step2_structural = sorted((*unit.structural_fqcns, *integration.structural_fqcns))
    require(len(step2_keys) == len(set(step2_keys)) == 728, "E_STEP2_SET", "Unit/Integration positive union differs")
    require(len(step2_structural) == len(set(step2_structural)) == 59, "E_STEP2_SET", "Unit/Integration structural union differs")
    expected_step2_keys = sorted(row["execution_key"] for row in contract.rows)
    expected_step2_structural = sorted(row["report_fqcn"] for row in contract.structural_rows)
    require(step2_keys == expected_step2_keys and step2_structural == expected_step2_structural, "E_STEP2_SET", "Step 2 exact report-view identity set differs")
    require(not (set(step2_keys) & set(step3.execution_keys)), "E_IDENTITY_OVERLAP", "Step 2 and Step 3 required identities overlap")
    require(
        not (
            set(step2_structural)
            & set((*unit.report_fqcns, *integration.report_fqcns, *step3.report_fqcns))
        ),
        "E_IDENTITY_OVERLAP",
        "structural report FQCN overlaps a required positive report",
    )
    combined = sorted((*step2_keys, *step3.execution_keys))

    view_manifest = view / "SHA256SUMS"
    payload = {
        "schema_version": 1,
        "kind": "v934-step4-report-inventory",
        "status": "passed",
        "run_id": run_id,
        "authority": {
            "kind": "step4-coverage",
            "run_id": run_id,
            "git_head": parent.git_head,
            "contract_sha256": parent.contract_sha256,
            "source_sha256": parent.source_sha256,
            "not_before_ns": parent.not_before_ns,
            "outer_marker": evidence_record(root, parent.marker),
        },
        "validator_bindings": tool_bindings(root),
        "evidence": {
            "step2_report_view": {
                "path": repo_relative(root, view),
                "hash_manifest": evidence_record(root, view_manifest),
                "positive_reports": 728,
                "structural_reports": 59,
                "testcase_nodes": 5261,
            },
            "unit": {
                "root": repo_relative(root, unit.root),
                "parent_binding": child_binding(parent, "canonical-run+head+source+not-before"),
                "outer_marker": evidence_record(root, unit.outer_marker),
                "final_manifest": evidence_record(root, unit.final_manifest),
                "summary": evidence_record(root, unit.summary),
                "run_status": evidence_record(root, unit.run_status),
                "negative_probes": evidence_record(root, unit.negative),
            },
            "integration": {
                "root": repo_relative(root, integration.root),
                "parent_binding": child_binding(parent, "canonical-run+head+source+not-before"),
                "outer_marker": evidence_record(root, integration.outer_marker),
                "final_manifest": evidence_record(root, integration.final_manifest),
                "summary": evidence_record(root, integration.summary),
                "run_status": evidence_record(root, integration.run_status),
                "negative_probes": evidence_record(root, integration.negative),
            },
            "step3_required": {
                "root": repo_relative(root, step3.root),
                "parent_binding": child_binding(parent, "persisted-parent-receipt"),
                "parent_context": evidence_record(root, step3.parent_context),
                "candidate_manifest": evidence_record(root, step3.candidate),
                "final_manifest": evidence_record(root, step3.final_manifest),
                "addon_candidate_manifest": evidence_record(root, step3.addon_candidate),
            },
        },
        "required_union": {
            "step2": {
                "positive_reports": 728,
                "structural_reports": 59,
                "testcase_nodes": 5261,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
                "execution_keys": step2_keys,
                "execution_keys_sha256": digest_strings(step2_keys),
                "structural_report_fqcns": step2_structural,
            },
            "step3_required": {
                "positive_reports": 45,
                "testcase_nodes": 446,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
                "execution_keys": list(step3.execution_keys),
                "execution_keys_sha256": digest_strings(step3.execution_keys),
            },
            "combined": {
                "positive_reports": 773,
                "structural_reports": 59,
                "testcase_nodes": 5707,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
                "execution_keys": combined,
                "execution_keys_sha256": digest_strings(combined),
                "structural_report_fqcns": step2_structural,
            },
        },
        "addon_companion": {
            "included_in_required_union": False,
            "reports": 2,
            "variants": 2,
            "testcase_nodes": 6,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
            "report_identities": list(step3.addon_identities),
            "candidate_manifest": evidence_record(root, step3.addon_candidate),
        },
    }
    validate_union_semantics(payload)
    return payload


def canonical_output(root: Path, run_id: str) -> Path:
    return root / "target/v934-step4-coverage/runs" / safe_run_id(run_id) / "report-inventory.json"


def revalidate_workspace_identity(root: Path, payload: dict[str, Any]) -> None:
    require_clean_worktree(root)
    authority = exact_keys(
        payload.get("authority"),
        {"kind", "run_id", "git_head", "contract_sha256", "source_sha256", "not_before_ns", "outer_marker"},
        "E_UNSTABLE_EVIDENCE",
        "inventory authority during final source recheck",
    )
    contract = regular_file(root / COVERAGE_CONTRACT, "E_UNSTABLE_EVIDENCE", COVERAGE_CONTRACT.as_posix())
    current_head = current_git_head(root)
    source = current_source_seal(root)
    require(
        authority["git_head"] == current_head
        and source["git_head"] == current_head
        and authority["contract_sha256"] == sha256_file(contract)
        and authority["source_sha256"] == source["sha256"],
        "E_UNSTABLE_EVIDENCE",
        "HEAD, coverage contract, or source seal changed while building report inventory",
    )


def command_verify(args: argparse.Namespace) -> None:
    root = validate_repo_root(args.repo_root)
    require_clean_worktree(root)
    output = canonical_output(root, args.run_id)
    require(not output.exists() and not output.is_symlink(), "E_OUTPUT", f"output already exists: {output}")
    first_payload = build_inventory(root, args.run_id)
    first_bytes = json_bytes(first_payload)
    require(not output.exists() and not output.is_symlink(), "E_OUTPUT", f"output appeared while building inventory: {output}")
    second_payload = build_inventory(root, args.run_id)
    second_bytes = json_bytes(second_payload)
    require(first_bytes == second_bytes, "E_UNSTABLE_EVIDENCE", "two complete inventory snapshots are not byte-identical")
    revalidate_workspace_identity(root, second_payload)
    require(not output.exists() and not output.is_symlink(), "E_OUTPUT", f"output appeared before publication: {output}")
    atomic_publish(output, second_bytes)
    print(f"{PREFIX} PASS run={args.run_id} required=773/59/5707 F0/E0/S0 addon=2/6 output={output}")


def command_validate(args: argparse.Namespace) -> None:
    root = validate_repo_root(args.repo_root)
    require_clean_worktree(root)
    output = canonical_output(root, args.run_id)
    observed = read_json(output, "E_OUTPUT")
    expected = build_inventory(root, args.run_id)
    require(observed == expected, "E_OUTPUT", "run-owned report inventory differs from live authority evidence")
    validate_union_semantics(observed)
    revalidate_workspace_identity(root, expected)
    print(f"{PREFIX} validate PASS run={args.run_id} required=773/59/5707 F0/E0/S0 addon=2/6")


def synthetic_payload() -> dict[str, Any]:
    step2_keys = [f"step2-{index:03d}" for index in range(728)]
    step3_keys = [f"step3-{index:02d}" for index in range(45)]
    combined = sorted((*step2_keys, *step3_keys))
    structural = [f"structural-{index:02d}" for index in range(59)]
    digest = "a" * 64
    run_id = "negative-run"
    coverage_root = f"target/v934-step4-coverage/runs/{run_id}"
    unit_root = f"target/v934-step2-unit/runs/{run_id}"
    integration_root = f"target/v934-step2-integration/runs/{run_id}"
    step3_root = f"target/v934-step3-required-matrix/runs/{run_id}"
    addon_root = f"target/v934-step3-preagg-addon/runs/{run_id}"

    def artifact(path: str) -> dict[str, Any]:
        return {"path": path, "sha256": digest, "size_bytes": 1}

    binding = {
        "run_id": run_id, "git_head": "b" * 40,
        "parent_contract_sha256": digest, "parent_source_sha256": digest,
        "parent_outer_marker_sha256": digest,
    }
    return {
        "schema_version": 1,
        "kind": "v934-step4-report-inventory",
        "status": "passed",
        "run_id": run_id,
        "authority": {
            "kind": "step4-coverage", "run_id": run_id, "git_head": "b" * 40,
            "contract_sha256": digest, "source_sha256": digest, "not_before_ns": 1,
            "outer_marker": artifact(f"{coverage_root}/run-context.json"),
        },
        "validator_bindings": [
            {"path": path.as_posix(), "sha256": digest}
            for path in TOOL_BINDING_PATHS
        ],
        "evidence": {
            "step2_report_view": {
                "path": f"{coverage_root}/step2-report-view",
                "hash_manifest": artifact(f"{coverage_root}/step2-report-view/SHA256SUMS"),
                "positive_reports": 728,
                "structural_reports": 59,
                "testcase_nodes": 5261,
            },
            "unit": {
                "root": unit_root,
                "parent_binding": {**binding, "binding_mode": "canonical-run+head+source+not-before"},
                "outer_marker": artifact(f"{unit_root}/run-context.json"),
                "final_manifest": artifact(f"{unit_root}/final/report-manifest.json"),
                "summary": artifact(f"{unit_root}/summary.env"),
                "run_status": artifact(f"{unit_root}/run-status.env"),
                "negative_probes": artifact(f"{unit_root}/negative/negative-probes.tsv"),
            },
            "integration": {
                "root": integration_root,
                "parent_binding": {**binding, "binding_mode": "canonical-run+head+source+not-before"},
                "outer_marker": artifact(f"{integration_root}/run-context.json"),
                "final_manifest": artifact(f"{integration_root}/final/report-manifest.json"),
                "summary": artifact(f"{integration_root}/summary.env"),
                "run_status": artifact(f"{integration_root}/run-status.env"),
                "negative_probes": artifact(f"{integration_root}/negative/negative-probes.tsv"),
            },
            "step3_required": {
                "root": step3_root,
                "parent_binding": {**binding, "binding_mode": "persisted-parent-receipt"},
                "parent_context": artifact(f"{step3_root}/step4-parent-context.env"),
                "candidate_manifest": artifact(f"{step3_root}/candidate-manifest.json"),
                "final_manifest": artifact(f"{step3_root}/final/report-manifest.json"),
                "addon_candidate_manifest": artifact(f"{addon_root}/candidate-manifest.json"),
            },
        },
        "required_union": {
            "step2": {
                "positive_reports": 728, "structural_reports": 59, "testcase_nodes": 5261,
                "failures": 0, "errors": 0, "skipped": 0, "execution_keys": step2_keys,
                "execution_keys_sha256": digest_strings(step2_keys), "structural_report_fqcns": structural,
            },
            "step3_required": {
                "positive_reports": 45, "testcase_nodes": 446, "failures": 0,
                "errors": 0, "skipped": 0, "execution_keys": step3_keys,
                "execution_keys_sha256": digest_strings(step3_keys),
            },
            "combined": {
                "positive_reports": 773, "structural_reports": 59, "testcase_nodes": 5707,
                "failures": 0, "errors": 0, "skipped": 0, "execution_keys": combined,
                "execution_keys_sha256": digest_strings(combined), "structural_report_fqcns": structural,
            },
        },
        "addon_companion": {
            "included_in_required_union": False, "reports": 2, "variants": 2,
            "testcase_nodes": 6, "failures": 0, "errors": 0, "skipped": 0,
            "report_identities": ["addon-mysql57", "addon-sqlite"],
            "candidate_manifest": artifact(f"{addon_root}/candidate-manifest.json"),
        },
    }


def expect_error(name: str, expected: str, action, rows: list[dict[str, str]]) -> None:
    try:
        action()
    except InventoryError as exc:
        actual = exc.code
    else:
        actual = "none"
    require(actual == expected, "E_NEGATIVE", f"probe {name} actual={actual} expected={expected}")
    rows.append({"probe": name, "expected_error": expected, "actual_error": actual, "status": "passed"})


def mutate_probe(mutator) -> None:
    payload = synthetic_payload()
    mutator(payload)
    validate_union_semantics(payload)


def write_negative_tsv(path: Path, rows: Sequence[dict[str, str]]) -> None:
    lines = ["probe\texpected_error\tactual_error\tstatus\n"]
    lines.extend("\t".join(row[name] for name in ("probe", "expected_error", "actual_error", "status")) + "\n" for row in rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    atomic_publish(path, "".join(lines).encode())


def command_negative(args: argparse.Namespace) -> None:
    rows: list[dict[str, str]] = []
    validate_union_semantics(synthetic_payload())
    expect_error("boolean-schema-version", "E_SCHEMA", lambda: mutate_probe(lambda p: p.__setitem__("schema_version", True)), rows)
    expect_error("extra-required-field", "E_SCHEMA", lambda: mutate_probe(lambda p: p["required_union"]["step2"].__setitem__("extra", "forged")), rows)
    expect_error("extra-evidence-field", "E_SCHEMA", lambda: mutate_probe(lambda p: p["evidence"]["integration"].__setitem__("extra", "forged")), rows)
    expect_error("extra-binding-field", "E_SCHEMA", lambda: mutate_probe(lambda p: p["evidence"]["unit"]["parent_binding"].__setitem__("extra", "forged")), rows)
    expect_error("malformed-validator-bindings", "E_TOOL_BINDING", lambda: mutate_probe(lambda p: p.__setitem__("validator_bindings", {"forged": True})), rows)
    expect_error("missing-self-validator-binding", "E_TOOL_BINDING", lambda: mutate_probe(lambda p: p["validator_bindings"].pop(0)), rows)
    expect_error("invalid-validator-binding-hash", "E_TOOL_BINDING", lambda: mutate_probe(lambda p: p["validator_bindings"][0].__setitem__("sha256", "not-a-sha")), rows)
    expect_error("wrong-unit-binding-mode", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["evidence"]["unit"]["parent_binding"].__setitem__("binding_mode", "standalone")), rows)
    expect_error("wrong-authority-head", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["authority"].__setitem__("git_head", "c" * 40)), rows)
    expect_error("wrong-authority-source", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["authority"].__setitem__("source_sha256", "c" * 64)), rows)
    expect_error("invalid-not-before", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["authority"].__setitem__("not_before_ns", 0)), rows)
    expect_error("spliced-outer-marker-hash", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["authority"]["outer_marker"].__setitem__("sha256", "c" * 64)), rows)
    expect_error("invalid-child-evidence-hash", "E_EVIDENCE", lambda: mutate_probe(lambda p: p["evidence"]["unit"]["outer_marker"].__setitem__("sha256", "not-a-sha")), rows)
    expect_error("spliced-child-evidence-path", "E_EVIDENCE", lambda: mutate_probe(lambda p: p["evidence"]["unit"]["outer_marker"].__setitem__("path", "target/other-run/run-context.json")), rows)
    expect_error("spliced-addon-candidate-hash", "E_EVIDENCE", lambda: mutate_probe(lambda p: p["addon_companion"]["candidate_manifest"].__setitem__("sha256", "c" * 64)), rows)
    expect_error("missing-step2-key", "E_TOTAL", lambda: mutate_probe(lambda p: p["required_union"]["step2"]["execution_keys"].pop()), rows)
    expect_error("duplicate-step2-key", "E_IDENTITY_DUPLICATE", lambda: mutate_probe(lambda p: p["required_union"]["step2"]["execution_keys"].__setitem__(-1, p["required_union"]["step2"]["execution_keys"][0])), rows)
    def overlap(payload: dict[str, Any]) -> None:
        keys = payload["required_union"]["step3_required"]["execution_keys"]
        keys[0] = payload["required_union"]["step2"]["execution_keys"][0]
        keys.sort()
        payload["required_union"]["step3_required"]["execution_keys_sha256"] = digest_strings(keys)
    expect_error("cross-lane-overlap", "E_IDENTITY_OVERLAP", lambda: mutate_probe(overlap), rows)
    def wrong_union(payload: dict[str, Any]) -> None:
        keys = payload["required_union"]["combined"]["execution_keys"]
        keys[-1] = "spliced-key"
        keys.sort()
        payload["required_union"]["combined"]["execution_keys_sha256"] = digest_strings(keys)
    expect_error("spliced-required-union", "E_UNION", lambda: mutate_probe(wrong_union), rows)
    expect_error("wrong-unit-run", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["evidence"]["unit"]["parent_binding"].__setitem__("run_id", "other-run")), rows)
    expect_error("wrong-integration-head", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["evidence"]["integration"]["parent_binding"].__setitem__("git_head", "c" * 40)), rows)
    expect_error("wrong-step3-source", "E_PARENT_BINDING", lambda: mutate_probe(lambda p: p["evidence"]["step3_required"]["parent_binding"].__setitem__("parent_source_sha256", "c" * 64)), rows)
    expect_error("addon-leaks-required", "E_ADDON_SEPARATION", lambda: mutate_probe(lambda p: p["addon_companion"].__setitem__("included_in_required_union", True)), rows)
    expect_error("addon-count-drift", "E_ADDON_TOTAL", lambda: mutate_probe(lambda p: p["addon_companion"].__setitem__("reports", 1)), rows)
    expect_error("unsafe-run-id", "E_PATH", lambda: safe_run_id("../splice"), rows)
    with tempfile.TemporaryDirectory(prefix="v934-step4-report-inventory-negative-") as temporary_name:
        temporary = Path(temporary_name)
        output = temporary / "inventory.json"
        atomic_publish(output, b"{}\n")
        expect_error("refuse-overwrite", "E_OUTPUT", lambda: atomic_publish(output, b"{}\n"), rows)
        target = temporary / "target.json"
        target.write_text("{}\n", encoding="utf-8")
        symlink = temporary / "symlink.json"
        symlink.symlink_to(target.name)
        expect_error("refuse-symlink-output", "E_OUTPUT", lambda: atomic_publish(symlink, b"{}\n"), rows)
    if args.output is not None:
        write_negative_tsv(args.output.absolute(), rows)
    for row in rows:
        print(f"{row['probe']}\t{row['expected_error']}\t{row['actual_error']}\t{row['status']}")
    print(f"{PREFIX} legacy-validator-e2e=formal-verify+validate")
    print(f"{PREFIX} negative PASS probes={len(rows)}/{len(rows)}")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    sub = result.add_subparsers(dest="command", required=True)
    for name in ("verify", "validate"):
        command = sub.add_parser(name)
        command.add_argument("--repo-root", type=Path, required=True)
        command.add_argument("--run-id", required=True)
        command.set_defaults(func=command_verify if name == "verify" else command_validate)
    negative = sub.add_parser("negative")
    negative.add_argument(
        "--output",
        type=Path,
        help="optional no-clobber TSV output; the top-level runner must supply its canonical run-owned negative path",
    )
    negative.set_defaults(func=command_negative)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        args.func(args)
    except InventoryError as exc:
        print(f"{PREFIX} ERROR {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print(f"{PREFIX} ERROR E_SIGNAL: interrupted", file=sys.stderr)
        return 130
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
