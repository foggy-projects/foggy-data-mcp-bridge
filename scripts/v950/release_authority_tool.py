#!/usr/bin/env python3
"""Fail-closed evidence utilities for the Foggy 9.5.0 release authority."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import tarfile
import tempfile
import xml.etree.ElementTree as ET
from typing import Any


TOOL_DIR = Path(__file__).resolve().parent
CONTRACT_PATH = TOOL_DIR / "release-authority-contract.json"
HEX40 = re.compile(r"^[0-9a-f]{40}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
SAFE_KEY = re.compile(r"^[A-Za-z0-9._-]+$")


class AuthorityError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise AuthorityError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def canonical_json(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        + "\n"
    ).encode("utf-8")


def load_json(path: Path) -> dict[str, Any]:
    require(path.is_file() and not path.is_symlink(), f"unsafe/missing JSON: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        fail(f"invalid JSON {path}: {error}")
    require(type(value) is dict, f"JSON root must be object: {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    require(not path.exists() and not path.is_symlink(), f"output already exists: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_bytes(canonical_json(value))
    os.replace(temporary, path)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    require(path.is_file() and not path.is_symlink(), f"unsafe/missing file: {path}")
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run_git(root: Path, *args: str, binary: bool = False) -> bytes | str:
    process = subprocess.run(
        ["git", "-C", str(root), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if process.returncode != 0:
        fail(
            f"git {' '.join(args)} failed: "
            f"{process.stderr.decode('utf-8', errors='replace').strip()}"
        )
    return process.stdout if binary else process.stdout.decode("utf-8").strip()


def git_head(root: Path) -> str:
    value = str(run_git(root, "rev-parse", "HEAD"))
    require(HEX40.fullmatch(value) is not None, "invalid Git HEAD")
    return value


def contract() -> dict[str, Any]:
    return load_json(CONTRACT_PATH)


def xml_modules(root: Path) -> list[str]:
    tree = ET.parse(root / "pom.xml")
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    modules = [
        node.text.strip()
        for node in tree.getroot().findall("./m:modules/m:module", namespace)
        if node.text and node.text.strip()
    ]
    return modules


def resolved_reports(
    release_contract: dict[str, Any], lane: str
) -> tuple[dict[str, int], int]:
    if lane in ("semantic", "portable"):
        key = "semantic_replay" if lane == "semantic" else "portable_replay"
        item = release_contract[key]
        return dict(item["reports"]), int(item["expected_testcases"])
    database = release_contract["database_replay"]
    matches = [item for item in database["variants"] if item["key"] == lane]
    require(len(matches) == 1, f"unknown/duplicate database lane: {lane}")
    item = matches[0]
    reports = (
        database["standard_reports"]
        if item.get("report_set") == "standard"
        else item.get("reports")
    )
    require(type(reports) is dict and reports, f"lane reports missing: {lane}")
    return dict(reports), int(item["expected_testcases"])


def validate_contract(repo_root: Path) -> dict[str, Any]:
    release_contract = contract()
    require(
        set(release_contract)
        == {
            "schema_version",
            "kind",
            "version",
            "reactor",
            "root_verify",
            "semantic_replay",
            "database_replay",
            "portable_replay",
            "forbidden",
        },
        "contract top-level keys differ",
    )
    require(release_contract["schema_version"] == 1, "unsupported schema version")
    require(
        release_contract["kind"] == "v950-release-authority-contract",
        "contract kind differs",
    )
    require(release_contract["version"] == "9.5.0", "contract version differs")

    reactor = release_contract["reactor"]
    modules = reactor["modules"]
    require(
        type(modules) is list
        and len(modules) == 31
        and len(set(modules)) == 31,
        "contract must freeze 31 unique modules",
    )
    require(reactor["project_count"] == 32, "reactor project count differs")
    require(xml_modules(repo_root) == modules, "active root reactor differs from contract")
    for module in modules:
        require(
            type(module) is str
            and module
            and not module.startswith("/")
            and ".." not in module.split("/"),
            f"unsafe module path: {module!r}",
        )
        require((repo_root / module / "pom.xml").is_file(), f"module POM missing: {module}")
    require("foggy-dataset-model" not in modules, "legacy aggregate module is forbidden")

    database = release_contract["database_replay"]
    provisioner = database["provisioner"]
    provisioner_path = repo_root / provisioner["path"]
    require(
        sha256_file(provisioner_path) == provisioner["sha256"],
        "pinned database provisioner digest differs",
    )
    variants = database["variants"]
    keys = [item["key"] for item in variants]
    require(
        len(variants) == database["expected_variants"] == 7
        and len(set(keys)) == 7,
        "database variant set differs",
    )
    database_total = 0
    for key in keys:
        reports, expected = resolved_reports(release_contract, key)
        require(sum(reports.values()) == expected, f"database report total differs: {key}")
        database_total += expected
    require(
        database_total == database["expected_testcases"] == 370,
        "database matrix total differs",
    )

    semantic_reports, semantic_total = resolved_reports(release_contract, "semantic")
    portable_reports, portable_total = resolved_reports(release_contract, "portable")
    require(sum(semantic_reports.values()) == semantic_total == 63, "semantic total differs")
    require(portable_reports == semantic_reports, "portable report contract differs")
    require(portable_total == semantic_total, "portable testcase total differs")

    governed_payload = canonical_json(
        {key: value for key, value in release_contract.items() if key != "forbidden"}
    ).decode("utf-8")
    for token in release_contract["forbidden"]["legacy_contract_tokens"]:
        require(
            token not in governed_payload,
            f"legacy token present in governed v950 contract: {token}",
        )

    return {
        "schema_version": 1,
        "kind": "v950-contract-validation",
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "modules": len(modules),
        "projects": reactor["project_count"],
        "database_variants": len(variants),
        "database_testcases": database_total,
        "semantic_testcases": semantic_total,
        "status": "passed",
    }


def tracked_rows(repo_root: Path) -> list[tuple[str, str, str, int]]:
    raw = run_git(repo_root, "ls-files", "-s", "-z", binary=True)
    assert isinstance(raw, bytes)
    rows: list[tuple[str, str, str, int]] = []
    seen: set[str] = set()
    for record in raw.split(b"\0"):
        if not record:
            continue
        metadata, raw_path = record.split(b"\t", 1)
        mode, object_id, stage = metadata.decode("ascii").split(" ")
        require(stage == "0", "unmerged tracked file is forbidden")
        path = raw_path.decode("utf-8")
        require(path not in seen, f"duplicate tracked path: {path}")
        seen.add(path)
        absolute = repo_root / path
        observed = os.lstat(absolute)
        if mode == "120000":
            require(stat.S_ISLNK(observed.st_mode), f"tracked symlink type differs: {path}")
            data = os.readlink(absolute).encode("utf-8")
        else:
            require(stat.S_ISREG(observed.st_mode), f"tracked file type differs: {path}")
            data = absolute.read_bytes()
        digest = sha256_bytes(data)
        rows.append((mode, path, digest, len(data)))
    rows.sort(key=lambda row: row[1].encode("utf-8"))
    return rows


def source_seal(repo_root: Path, output: Path) -> dict[str, Any]:
    head = git_head(repo_root)
    rows = tracked_rows(repo_root)
    lines = ["mode\tpath\tsha256\tsize"]
    lines.extend(f"{mode}\t{path}\t{digest}\t{size}" for mode, path, digest, size in rows)
    payload = ("\n".join(lines) + "\n").encode("utf-8")
    require(not output.exists() and not output.is_symlink(), f"output exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(payload)
    return {
        "schema_version": 1,
        "kind": "v950-source-seal",
        "candidate": head,
        "file_count": len(rows),
        "inventory_sha256": sha256_bytes(payload),
        "status": "passed",
    }


def safe_archive_name(name: str, prefix: str) -> str:
    require("\\" not in name and "\x00" not in name, f"unsafe archive name: {name!r}")
    pure = PurePosixPath(name)
    require(not pure.is_absolute(), f"absolute archive name: {name}")
    require(all(part not in ("", ".", "..") for part in pure.parts), f"unsafe archive name: {name}")
    require(pure.parts[0] == prefix, f"archive prefix differs: {name}")
    return pure.as_posix()


def create_archive(
    repo_root: Path, candidate: str, output: Path, receipt: Path
) -> dict[str, Any]:
    release_contract = contract()
    require(candidate == git_head(repo_root), "archive candidate differs from HEAD")
    require(HEX40.fullmatch(candidate) is not None, "invalid archive candidate")
    require(not output.exists() and not output.is_symlink(), f"archive exists: {output}")
    prefix = "foggy-data-mcp-bridge-9.5.0"
    output.parent.mkdir(parents=True, exist_ok=True)
    rows = tracked_rows(repo_root)
    manifest = {
        "schema_version": 1,
        "kind": "v950-source-archive-candidate",
        "version": release_contract["version"],
        "candidate": candidate,
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "tracked_file_count": len(rows),
    }
    temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
    with temporary.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode="w", format=tarfile.PAX_FORMAT) as archive:
                entries: list[tuple[str, bytes, int, str]] = []
                entries.append((".v950-release-candidate.json", canonical_json(manifest), 0o644, "100644"))
                for mode, path, _digest, _size in rows:
                    blob = run_git(repo_root, "show", f"{candidate}:{path}", binary=True)
                    assert isinstance(blob, bytes)
                    file_mode = 0o755 if mode == "100755" else 0o644
                    entries.append((path, blob, file_mode, mode))
                for path, data, file_mode, git_mode in entries:
                    name = f"{prefix}/{path}"
                    info = tarfile.TarInfo(name)
                    info.uid = 0
                    info.gid = 0
                    info.uname = ""
                    info.gname = ""
                    info.mtime = 0
                    if git_mode == "120000":
                        info.type = tarfile.SYMTYPE
                        info.linkname = data.decode("utf-8")
                        info.mode = 0o777
                        info.size = 0
                        archive.addfile(info)
                    else:
                        info.type = tarfile.REGTYPE
                        info.mode = file_mode
                        info.size = len(data)
                        archive.addfile(info, io.BytesIO(data))
    os.replace(temporary, output)
    value = {
        "schema_version": 1,
        "kind": "v950-source-archive",
        "candidate": candidate,
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "archive": output.name,
        "archive_sha256": sha256_file(output),
        "archive_size": output.stat().st_size,
        "tracked_file_count": len(rows),
        "status": "passed",
    }
    write_json(receipt, value)
    return value


def extract_archive(
    archive_path: Path, destination: Path, expected_candidate: str, receipt: Path
) -> dict[str, Any]:
    require(archive_path.is_file() and not archive_path.is_symlink(), "unsafe archive")
    require(not destination.exists() and not destination.is_symlink(), "destination exists")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.mkdir(mode=0o755)
    prefix = "foggy-data-mcp-bridge-9.5.0"
    names: set[str] = set()
    files = 0
    with tarfile.open(archive_path, mode="r:gz") as archive:
        for member in archive:
            name = safe_archive_name(member.name, prefix)
            require(name not in names, f"duplicate archive entry: {name}")
            names.add(name)
            relative = PurePosixPath(name).relative_to(prefix)
            require(relative.parts, "archive root entry is forbidden")
            target = destination.joinpath(*relative.parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            if member.isdir():
                target.mkdir(exist_ok=True)
            elif member.issym():
                link = PurePosixPath(member.linkname)
                require(
                    not link.is_absolute()
                    and all(part not in ("", ".", "..") for part in link.parts),
                    f"unsafe symlink target: {member.linkname}",
                )
                os.symlink(member.linkname, target)
                files += 1
            else:
                require(member.isfile(), f"unsupported archive entry type: {name}")
                source = archive.extractfile(member)
                require(source is not None, f"archive file unreadable: {name}")
                data = source.read()
                require(len(data) == member.size, f"archive file size differs: {name}")
                target.write_bytes(data)
                target.chmod(member.mode & 0o777)
                files += 1
    marker = load_json(destination / ".v950-release-candidate.json")
    require(marker["candidate"] == expected_candidate, "archive candidate marker differs")
    require(marker["contract_sha256"] == sha256_file(CONTRACT_PATH), "archive contract differs")
    require(marker["tracked_file_count"] + 1 == files, "archive file count differs")
    cross_filesystem = archive_path.stat().st_dev != destination.stat().st_dev
    require(cross_filesystem, "portable extraction did not cross filesystems")
    value = {
        "schema_version": 1,
        "kind": "v950-source-archive-extraction",
        "candidate": expected_candidate,
        "archive_sha256": sha256_file(archive_path),
        "destination": str(destination),
        "files": files,
        "cross_filesystem": cross_filesystem,
        "status": "passed",
    }
    write_json(receipt, value)
    return value


def junit_summary(
    reports_dir: Path,
    lane: str,
    candidate: str,
    marker: Path,
    output: Path,
) -> dict[str, Any]:
    release_contract = contract()
    expected, expected_total = resolved_reports(release_contract, lane)
    require(reports_dir.is_dir() and not reports_dir.is_symlink(), f"reports dir missing: {reports_dir}")
    require(marker.is_file() and not marker.is_symlink(), f"marker missing: {marker}")
    marker_ns = marker.stat().st_mtime_ns
    observed: dict[str, dict[str, int | str]] = {}
    for path in sorted(reports_dir.glob("TEST-*.xml")):
        require(not path.is_symlink(), f"symlinked report: {path}")
        require(path.stat().st_mtime_ns > marker_ns, f"stale report: {path.name}")
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as error:
            fail(f"invalid JUnit XML {path}: {error}")
        name = root.attrib.get("name")
        require(type(name) is str and name, f"report name missing: {path}")
        require(name not in observed, f"duplicate report identity: {name}")
        values: dict[str, int] = {}
        for key in ("tests", "failures", "errors", "skipped"):
            try:
                values[key] = int(root.attrib.get(key, ""))
            except ValueError:
                fail(f"invalid {key} in report: {path}")
            require(values[key] >= 0, f"negative {key} in report: {path}")
        require(
            values["tests"] == len(root.findall(".//testcase")),
            f"testcase node count differs: {path}",
        )
        observed[name] = {"file": path.name, **values, "sha256": sha256_file(path)}
    require(set(observed) == set(expected), f"{lane} report set differs")
    totals = {key: sum(int(item[key]) for item in observed.values()) for key in ("tests", "failures", "errors", "skipped")}
    for name, tests in expected.items():
        require(observed[name]["tests"] == tests, f"{lane} test count differs: {name}")
    require(totals["tests"] == expected_total, f"{lane} total tests differ")
    require(
        totals["failures"] == totals["errors"] == totals["skipped"] == 0,
        f"{lane} contains non-passing outcomes",
    )
    value = {
        "schema_version": 1,
        "kind": "v950-junit-receipt",
        "lane": lane,
        "candidate": candidate,
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "reports": observed,
        "totals": totals,
        "status": "passed",
    }
    write_json(output, value)
    return value


def root_summary(
    log: Path, jar: Path, candidate: str, output: Path
) -> dict[str, Any]:
    release_contract = contract()
    require(log.is_file() and not log.is_symlink(), "root Maven log missing")
    text = log.read_text(encoding="utf-8", errors="replace")
    require("[INFO] BUILD SUCCESS" in text, "root Maven build did not report success")
    summary_lines = re.findall(r"^\[INFO\] .+? \.{3,} SUCCESS \[", text, re.MULTILINE)
    expected = release_contract["root_verify"]["expected_project_count"]
    require(len(summary_lines) == expected, f"root reactor success count differs: {len(summary_lines)}")
    require(jar.is_file() and not jar.is_symlink(), "launcher JAR missing")
    import zipfile

    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "launcher JAR contains duplicate entries")
        for entry in release_contract["root_verify"]["required_archive_entries"]:
            require(entry in names, f"launcher JAR required entry missing: {entry}")
        for fragment in release_contract["root_verify"]["forbidden_archive_fragments"]:
            require(not any(fragment in name for name in names), f"launcher JAR forbidden entry: {fragment}")
    value = {
        "schema_version": 1,
        "kind": "v950-root-reactor-receipt",
        "candidate": candidate,
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "projects": expected,
        "launcher_jar_sha256": sha256_file(jar),
        "status": "passed",
    }
    write_json(output, value)
    return value


def read_env(path: Path) -> dict[str, str]:
    require(path.is_file() and not path.is_symlink(), f"unsafe/missing env file: {path}")
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        require(line and "=" in line, f"malformed env line: {path}")
        key, value = line.split("=", 1)
        require(key and key not in values, f"duplicate/blank env key: {path}")
        values[key] = value
    return values


def cell_summary(
    cell_root: Path, database: str, candidate: str, output: Path
) -> dict[str, Any]:
    require(database in {"mysql57", "mysql8", "postgres15", "sqlserver2022"}, "unsupported cell")
    status = read_env(cell_root / "status.env")
    cleanup = read_env(cell_root / "cleanup.env")
    require(status.get("database") == database, "cell status database differs")
    require(status.get("status") == "passed", "cell status is not passed")
    require(status.get("cleanup_status") == "passed", "cell status cleanup differs")
    require(cleanup.get("database") == database, "cleanup database differs")
    require(cleanup.get("status") == "passed", "cleanup receipt is not passed")
    before = status.get("fixture_before_sha256", "")
    after = status.get("fixture_after_sha256", "")
    require(HEX64.fullmatch(before) is not None and before == after, "cell fixture seal differs")
    value = {
        "schema_version": 1,
        "kind": "v950-database-cell-receipt",
        "candidate": candidate,
        "database": database,
        "fixture_sha256": before,
        "cleanup": "passed",
        "status": "passed",
    }
    write_json(output, value)
    return value


def evidence_scan(root: Path, output: Path) -> dict[str, Any]:
    require(root.is_dir() and not root.is_symlink(), "evidence root missing")
    patterns = [
        re.compile(rb"foggy_test_123", re.IGNORECASE),
        re.compile(rb"Foggy_Test_123!", re.IGNORECASE),
        re.compile(rb"(?:password|passwd|credential)\s*[:=]\s*[^\s]+", re.IGNORECASE),
    ]
    scanned = 0
    excluded_archives = 0
    for path in sorted(root.rglob("*")):
        if path == output or not path.is_file() or path.is_symlink():
            continue
        if path.name.endswith(".tar.gz"):
            excluded_archives += 1
            continue
        data = path.read_bytes()
        scanned += 1
        for pattern in patterns:
            require(pattern.search(data) is None, f"sensitive value found in evidence: {path}")
    value = {
        "schema_version": 1,
        "kind": "v950-evidence-sensitive-scan",
        "files": scanned,
        "patterns": len(patterns),
        "excluded_bound_archives": excluded_archives,
        "status": "passed",
    }
    write_json(output, value)
    return value


def finalize(
    candidate: str,
    source_before: Path,
    source_after: Path,
    receipts: list[str],
    output: Path,
) -> dict[str, Any]:
    require(HEX40.fullmatch(candidate) is not None, "invalid final candidate")
    before = load_json(source_before)
    after = load_json(source_after)
    require(before["candidate"] == after["candidate"] == candidate, "source seal candidate differs")
    require(
        before["file_count"] == after["file_count"]
        and before["inventory_sha256"] == after["inventory_sha256"],
        "source seal changed during authority",
    )
    loaded: dict[str, dict[str, Any]] = {}
    for item in receipts:
        require("=" in item, f"invalid receipt binding: {item}")
        key, raw_path = item.split("=", 1)
        require(SAFE_KEY.fullmatch(key) is not None, f"unsafe receipt key: {key}")
        require(key not in loaded, f"duplicate receipt key: {key}")
        path = Path(raw_path)
        value = load_json(path)
        require(value.get("status") == "passed", f"receipt not passed: {key}")
        if "candidate" in value:
            require(value["candidate"] == candidate, f"receipt candidate differs: {key}")
        loaded[key] = {
            "kind": value.get("kind"),
            "path": str(path),
            "sha256": sha256_file(path),
        }
    expected_keys = {
        "root",
        "semantic",
        "archive",
        "archive-extraction",
        "portable",
        "db-sqlite",
        "db-mysql57",
        "db-mysql8",
        "mysql8-targeted",
        "db-postgres15",
        "postgres15-targeted",
        "db-sqlserver2022",
        "mysql57-cell",
        "mysql8-cell",
        "postgres15-cell",
        "sqlserver2022-cell",
        "sensitive-scan",
    }
    require(set(loaded) == expected_keys, "final receipt set differs")
    value = {
        "schema_version": 1,
        "kind": "v950-release-authority-manifest",
        "version": "9.5.0",
        "candidate": candidate,
        "contract_sha256": sha256_file(CONTRACT_PATH),
        "source": {
            "file_count": before["file_count"],
            "inventory_sha256": before["inventory_sha256"],
        },
        "receipts": loaded,
        "totals": {
            "reactor_projects": 32,
            "semantic_testcases": 63,
            "portable_testcases": 63,
            "database_variants": 7,
            "database_testcases": 370,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
        },
        "status": "passed",
    }
    write_json(output, value)
    return value


def print_value(value: dict[str, Any]) -> None:
    sys.stdout.buffer.write(canonical_json(value))


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    sub = result.add_subparsers(dest="command", required=True)

    validate = sub.add_parser("validate-contract")
    validate.add_argument("--repo-root", type=Path, required=True)

    seal = sub.add_parser("source-seal")
    seal.add_argument("--repo-root", type=Path, required=True)
    seal.add_argument("--output", type=Path, required=True)

    create = sub.add_parser("create-archive")
    create.add_argument("--repo-root", type=Path, required=True)
    create.add_argument("--candidate", required=True)
    create.add_argument("--output", type=Path, required=True)
    create.add_argument("--receipt", type=Path, required=True)

    extract = sub.add_parser("extract-archive")
    extract.add_argument("--archive", type=Path, required=True)
    extract.add_argument("--destination", type=Path, required=True)
    extract.add_argument("--candidate", required=True)
    extract.add_argument("--receipt", type=Path, required=True)

    junit = sub.add_parser("junit-summary")
    junit.add_argument("--reports-dir", type=Path, required=True)
    junit.add_argument("--lane", required=True)
    junit.add_argument("--candidate", required=True)
    junit.add_argument("--marker", type=Path, required=True)
    junit.add_argument("--output", type=Path, required=True)

    root = sub.add_parser("root-summary")
    root.add_argument("--log", type=Path, required=True)
    root.add_argument("--jar", type=Path, required=True)
    root.add_argument("--candidate", required=True)
    root.add_argument("--output", type=Path, required=True)

    cell = sub.add_parser("cell-summary")
    cell.add_argument("--cell-root", type=Path, required=True)
    cell.add_argument("--database", required=True)
    cell.add_argument("--candidate", required=True)
    cell.add_argument("--output", type=Path, required=True)

    scan = sub.add_parser("scan-evidence")
    scan.add_argument("--root", type=Path, required=True)
    scan.add_argument("--output", type=Path, required=True)

    final = sub.add_parser("finalize")
    final.add_argument("--candidate", required=True)
    final.add_argument("--source-before", type=Path, required=True)
    final.add_argument("--source-after", type=Path, required=True)
    final.add_argument("--receipt", action="append", default=[])
    final.add_argument("--output", type=Path, required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "validate-contract":
            value = validate_contract(args.repo_root.resolve())
        elif args.command == "source-seal":
            value = source_seal(args.repo_root.resolve(), args.output)
        elif args.command == "create-archive":
            value = create_archive(args.repo_root.resolve(), args.candidate, args.output, args.receipt)
        elif args.command == "extract-archive":
            value = extract_archive(args.archive, args.destination, args.candidate, args.receipt)
        elif args.command == "junit-summary":
            value = junit_summary(args.reports_dir, args.lane, args.candidate, args.marker, args.output)
        elif args.command == "root-summary":
            value = root_summary(args.log, args.jar, args.candidate, args.output)
        elif args.command == "cell-summary":
            value = cell_summary(
                args.cell_root, args.database, args.candidate, args.output
            )
        elif args.command == "scan-evidence":
            value = evidence_scan(args.root, args.output)
        elif args.command == "finalize":
            value = finalize(
                args.candidate,
                args.source_before,
                args.source_after,
                args.receipt,
                args.output,
            )
        else:
            raise AssertionError(args.command)
        print_value(value)
        return 0
    except AuthorityError as error:
        print(f"[v950-authority-tool] ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
