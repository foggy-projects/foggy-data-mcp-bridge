#!/usr/bin/env python3
"""Generate and validate the frozen 9.3.4 test/evidence inventory."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import fnmatch
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable, Sequence


_CLASSPATH_FILE_HASH_CACHE: dict[Path, str] = {}
_DIRECTORY_TREE_HASH_CACHE: dict[Path, str] = {}


SOURCE_HEADER = [
    "source_id",
    "module",
    "reactor_member",
    "source_root",
    "source_path",
    "top_level_fqcn",
    "kind",
    "discovery_patterns",
    "disposition",
    "owner",
    "reason",
]
DISCOVERY_HEADER = [
    "module",
    "source_id",
    "source_fqcn",
    "report_fqcn",
    "discovered_test_nodes",
    "runtime_deferred_containers",
    "engine_ids",
    "source_sha256",
    "test_classes_sha256",
    "main_classes_sha256",
]
CLASSPATH_HEADER = ["module", "ordinal", "entry_identity", "entry_sha256"]
EXECUTION_HEADER = [
    "execution_key",
    "source_id",
    "report_fqcn",
    "runner",
    "lane",
    "variant_key",
    "db_kind",
    "infra_kind",
    "execution_step",
    "required",
    "owner",
    "optional_reason",
    "review_at",
]
RENAME_HEADER = [
    "rename_group",
    "current_source_id",
    "current_source_path",
    "current_top_level_fqcn",
    "current_report_fqcn",
    "current_execution_key",
    "target_source_id",
    "target_source_path",
    "target_top_level_fqcn",
    "target_report_fqcn",
    "target_execution_key",
    "runner",
    "lane",
    "variant_key",
    "db_kind",
    "infra_kind",
    "execution_step",
    "required",
    "owner",
    "optional_reason",
    "review_at",
    "rationale",
    "reviewer",
]
PREDECESSOR_NODE_HEADER = [
    "predecessor_node",
    "criterion",
    "historical_lane",
    "variant_key",
    "report_fqcn",
    "raw_report_sha256",
    "authority_run_id",
]
MIGRATION_HEADER = [
    "mapping_group",
    "relation",
    "declared_old_count",
    "declared_successor_count",
    "criterion",
    "predecessor_node",
    "successor_execution_key",
    "disposition",
    "rationale",
    "owner",
    "reviewer",
]
DB_HEADER = [
    "db_kind",
    "required",
    "approved_reference",
    "version_contract",
    "digest_policy",
    "coordinate_contract",
    "catalog",
    "schema",
    "sentinel_contract",
    "observed_step",
    "owner",
]
PACKAGE_HEADER = [
    "surface",
    "predecessor_expectation",
    "v934_expected_delta",
    "v934_successor_rule",
    "observed_step",
    "owner",
]
MAVEN_VARIANT_HEADER = [
    "module",
    "profile",
    "activation",
    "plugin",
    "execution_id",
    "current_owner",
    "current_variant",
    "v934_disposition",
    "owner",
]

SOURCE_FILE = "source-inventory.tsv"
DISCOVERY_FILE = "discovery-inventory.tsv"
CLASSPATH_FILE = "discovery-classpath.tsv"
EXECUTION_FILE = "execution-inventory.tsv"
RENAME_FILE = "rename-successor-plan.tsv"
PREDECESSOR_NODE_FILE = "predecessor-node-inventory.tsv"
MIGRATION_FILE = "predecessor-regression-map.tsv"
DB_FILE = "database-contract.tsv"
PACKAGE_FILE = "package-successor-inventory.tsv"
MAVEN_VARIANT_FILE = "maven-variant-inventory.tsv"
COVERAGE_FILE = "coverage-thresholds.json"
FREEZE_FILE = "contract-freeze.json"
NEGATIVE_FILE = "negative-probes.tsv"
HASH_FILE = "SHA256SUMS"

NEGATIVE_PROBE_NAMES = [
    "orphan-source",
    "missing-source-owner",
    "missing-source-reason",
    "nonreactor-disposition",
    "zero-execution-owner",
    "duplicate-execution-key",
    "non-executable-owner",
    "runner-overlap",
    "sqlite-lane-overlap",
    "missing-report-owner",
    "unexpected-report-owner",
    "invalid-step",
    "optional-metadata",
    "unknown-successor",
    "migration-cardinality",
    "duplicate-migration-edge",
    "unmapped-predecessor",
    "invalid-classpath-hash",
    "classpath-module-gap",
    "duplicate-maven-variant",
    "orphan-discovery-row",
    "missing-discovery-row",
    "tampered-discovery-hash",
    "tampered-rename-successor",
    "tampered-successor-policy",
    "freeze-count-tamper",
    "missing-hash-entry",
    "stale-manifest",
]

PATTERNS = (
    "Test*",
    "*Test",
    "*Tests",
    "*TestCase",
    "IT*",
    "*IT",
    "*ITCase",
    "*E2E",
    "*E2ETest",
)
MANUAL_PATTERNS = ("*IntegrationTest", "IT*", "*IT", "*ITCase", "*E2E", "*E2ETest")
KINDS = {"executable", "helper", "generator"}
RUNNERS = {"surefire", "failsafe"}
LANES = {
    "unit",
    "hermetic-integration",
    "sqlite-broad-integration",
    "database-contract-matrix",
    "external-redis",
    "external-mongo",
    "external-vector",
    "external-mysql",
    "external-llm",
}
DB_KINDS = {"none", "sqlite", "mysql57", "mysql8", "postgres15", "sqlserver2022"}
INFRA_KINDS = {"hermetic", "sqlite", "database", "redis", "mongodb", "vector", "llm"}


class InventoryError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def fail(code: str, message: str) -> None:
    raise InventoryError(code, message)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def framed(values: Sequence[str], prefix: str) -> str:
    fields = [f"{len(value.encode('utf-8'))}:{value}" for value in values]
    return prefix + "|" + "|".join(fields)


def source_id(path: str) -> str:
    return framed([path], "v934-src")


def execution_key(runner: str, lane: str, variant: str, report_fqcn: str) -> str:
    return framed([runner, lane, variant, report_fqcn], "v934")


def predecessor_node(run_id: str, lane: str, variant: str, report_fqcn: str) -> str:
    return framed([run_id, lane, variant, report_fqcn], "v933-node")


def run(root: Path, *args: str) -> str:
    result = subprocess.run(
        list(args),
        cwd=root,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout.strip()


def version_line(root: Path, *args: str) -> str:
    result = subprocess.run(
        list(args),
        cwd=root,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    lines = result.stdout.splitlines()
    if not lines:
        fail("E_TOOLCHAIN_VERSION", f"empty version output: {args}")
    return lines[0].strip()


def git_status_hash(root: Path) -> tuple[str, bool]:
    raw = subprocess.run(
        ["git", "status", "--porcelain=v1", "-z", "--untracked-files=all"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    return sha256_bytes(raw), bool(raw)


def tree_hash(root: Path, paths: Iterable[Path]) -> str:
    digest = hashlib.sha256()
    for path in sorted(set(paths), key=lambda item: item.as_posix()):
        relative = path.relative_to(root).as_posix()
        content_hash = sha256_file(path)
        for value in (relative, content_hash):
            encoded = value.encode("utf-8")
            digest.update(str(len(encoded)).encode("ascii"))
            digest.update(b":")
            digest.update(encoded)
            digest.update(b"\0")
    return digest.hexdigest()


def directory_tree_hash(directory: Path) -> str:
    cached = _DIRECTORY_TREE_HASH_CACHE.get(directory)
    if cached is not None:
        return cached
    files = [path for path in directory.rglob("*") if path.is_file()]
    if not files:
        fail("E_TREE_HASH", f"directory contains no files: {directory}")
    result = tree_hash(directory, files)
    _DIRECTORY_TREE_HASH_CACHE[directory] = result
    return result


def protected_source_paths(root: Path) -> list[Path]:
    output = subprocess.run(
        ["git", "ls-files", "-co", "--exclude-standard", "-z"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    result: list[Path] = []
    for raw in output.split(b"\0"):
        if not raw:
            continue
        relative = raw.decode("utf-8")
        path = root / relative
        if not path.is_file() or "/target/" in f"/{relative}/":
            continue
        in_scope = (
            relative == "pom.xml"
            or relative.endswith("/pom.xml")
            or "/src/main/" in f"/{relative}"
            or "/src/test/" in f"/{relative}"
            or relative.startswith(".github/workflows/")
            or relative.startswith("foggy-dataset-demo/docker/")
            or relative.startswith("docker/")
            or relative.startswith("scripts/verify-v933-")
            or relative.startswith("scripts/assert-v933-")
            or relative == "scripts/verify-v934-test-inventory.sh"
            or relative
            in {
                "scripts/v934/JUnitDiscoveryInventory.java",
                "scripts/v934/inventory_tool.py",
                "scripts/v934/inventory-overrides.json",
            }
        )
        if in_scope:
            result.append(path)
    return result


def command_source_hash(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    print(tree_hash(root, protected_source_paths(root)))


def active_reactor_modules(root: Path) -> list[str]:
    result: list[str] = []
    queue = [Path(".")]
    seen: set[str] = set()
    while queue:
        aggregator = queue.pop(0)
        pom = root / aggregator / "pom.xml"
        if not pom.is_file():
            continue
        xml_root = ET.parse(pom).getroot()
        namespace = ""
        if xml_root.tag.startswith("{"):
            namespace = xml_root.tag.split("}", 1)[0] + "}"
        modules = xml_root.find(f"{namespace}modules")
        if modules is None:
            continue
        for element in modules.findall(f"{namespace}module"):
            value = (element.text or "").strip()
            if not value:
                continue
            module_path = (aggregator / value).as_posix().removeprefix("./")
            normalized = Path(os.path.normpath(module_path)).as_posix()
            if normalized in seen:
                continue
            if not (root / normalized / "pom.xml").is_file():
                fail("E_REACTOR_MODULE", f"active module has no pom.xml: {normalized}")
            seen.add(normalized)
            result.append(normalized)
            queue.append(Path(normalized))
    return sorted(result)


def pom_coordinates(pom: Path) -> tuple[str, str, str, str]:
    xml_root = ET.parse(pom).getroot()
    namespace = ""
    if xml_root.tag.startswith("{"):
        namespace = xml_root.tag.split("}", 1)[0] + "}"
    parent = xml_root.find(f"{namespace}parent")

    def parent_text(name: str) -> str | None:
        if parent is None:
            return None
        return parent.findtext(f"{namespace}{name}")

    group_id = xml_root.findtext(f"{namespace}groupId") or parent_text("groupId")
    artifact_id = xml_root.findtext(f"{namespace}artifactId")
    version = xml_root.findtext(f"{namespace}version") or parent_text("version")
    packaging = xml_root.findtext(f"{namespace}packaging") or "jar"
    if not group_id or not artifact_id or not version:
        fail("E_REACTOR_COORDINATE", f"incomplete Maven coordinates: {pom}")
    if any(value.startswith("${") for value in (group_id, artifact_id, version)):
        fail("E_REACTOR_COORDINATE", f"unresolved Maven coordinates: {pom}")
    return group_id, artifact_id, version, packaging


def reactor_artifacts(root: Path) -> dict[Path, tuple[str, str, str, str, str]]:
    maven_root = Path.home() / ".m2/repository"
    result: dict[Path, tuple[str, str, str, str, str]] = {}
    for module in active_reactor_modules(root):
        group_id, artifact_id, version, packaging = pom_coordinates(root / module / "pom.xml")
        coordinate_dir = maven_root / Path(*group_id.split(".")) / artifact_id / version
        if coordinate_dir in result:
            fail("E_REACTOR_COORDINATE", f"duplicate reactor GAV directory: {coordinate_dir}")
        result[coordinate_dir] = (module, group_id, artifact_id, version, packaging)
    return result


def classpath_entry_sha256(root: Path, path: Path) -> str:
    if path.is_file():
        cached = _CLASSPATH_FILE_HASH_CACHE.get(path)
        if cached is not None:
            return cached
        result = sha256_file(path)
        _CLASSPATH_FILE_HASH_CACHE[path] = result
        return result
    if path.is_dir():
        return directory_tree_hash(path)
    fail("E_CLASSPATH_HASH", f"classpath entry is missing: {path}")


def command_classpath(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    raw_path = args.input.resolve()
    output_path = args.output.resolve()
    modules = active_reactor_modules(root)
    if args.module not in modules:
        fail("E_REACTOR_MODULE", f"classpath owner is not an active reactor module: {args.module}")
    raw_entries = [
        Path(value)
        for value in raw_path.read_text(encoding="utf-8").strip().split(os.pathsep)
        if value
    ]
    if not raw_entries:
        fail("E_CLASSPATH_CARDINALITY", f"empty raw classpath: {raw_path}")
    artifacts = reactor_artifacts(root)
    normalized: list[Path] = []
    replacements = 0
    for entry in raw_entries:
        coordinate = artifacts.get(entry.parent)
        if coordinate is None:
            normalized.append(entry)
            continue
        module, _group_id, artifact_id, version, packaging = coordinate
        expected_name = f"{artifact_id}-{version}.{packaging}"
        if packaging != "jar" or entry.name != expected_name:
            fail(
                "E_REACTOR_CLASSPATH_CLASSIFIER",
                f"unsupported reactor classpath artifact: module={module} entry={entry}",
            )
        normalized.append(root / module / "target/classes")
        replacements += 1
    if len(normalized) != len(set(normalized)):
        fail("E_CLASSPATH_DUP", f"normalized classpath has duplicates: module={args.module}")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(os.pathsep.join(str(path) for path in normalized) + "\n", encoding="utf-8")
    manifest_rows = [
        {"entry": str(path), "sha256": classpath_entry_sha256(root, path)}
        for path in normalized
    ]
    write_tsv(output_path.with_name(output_path.name + ".sha256.tsv"), ["entry", "sha256"], manifest_rows)
    print(
        f"[v934-inventory] classpath module={args.module} entries={len(normalized)} "
        f"reactor_replacements={replacements}"
    )


def owning_module(root: Path, source: Path, reactor_modules: Sequence[str]) -> tuple[str, bool]:
    relative = source.relative_to(root).as_posix()
    matches = [module for module in reactor_modules if relative.startswith(module + "/")]
    if matches:
        module = max(matches, key=len)
        return module, True
    current = source.parent
    while current != root and root in current.parents:
        if (current / "pom.xml").is_file():
            return current.relative_to(root).as_posix(), False
        current = current.parent
    return ".", False


def discovery_patterns(name: str) -> list[str]:
    matched = [pattern for pattern in PATTERNS if fnmatch.fnmatchcase(name, pattern)]
    if name.endswith("IntegrationTest"):
        matched.append("*IntegrationTest")
    return matched


def strip_java_comments_and_literals(text: str) -> str:
    output = list(text)
    index = 0
    state = "code"
    while index < len(text):
        char = text[index]
        next_char = text[index + 1] if index + 1 < len(text) else ""
        if state == "code":
            if char == "/" and next_char == "/":
                output[index] = output[index + 1] = " "
                state = "line-comment"
                index += 2
                continue
            if char == "/" and next_char == "*":
                output[index] = output[index + 1] = " "
                state = "block-comment"
                index += 2
                continue
            if char == '"':
                output[index] = " "
                state = "string"
            elif char == "'":
                output[index] = " "
                state = "char"
        elif state == "line-comment":
            if char == "\n":
                state = "code"
            else:
                output[index] = " "
        elif state == "block-comment":
            output[index] = " "
            if char == "*" and next_char == "/":
                output[index + 1] = " "
                state = "code"
                index += 2
                continue
        elif state in {"string", "char"}:
            output[index] = " "
            delimiter = '"' if state == "string" else "'"
            if char == "\\" and next_char:
                output[index + 1] = " "
                index += 2
                continue
            if char == delimiter:
                state = "code"
        index += 1
    return "".join(output)


def java_package_and_top_level_type(path: Path) -> tuple[str, str]:
    original = path.read_text(encoding="utf-8")
    text = strip_java_comments_and_literals(original)
    package_match = re.search(r"\bpackage\s+([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)\s*;", text)
    package = package_match.group(1) if package_match else ""
    tokens = list(re.finditer(r"[A-Za-z_$][\w$]*|[{}.]", text))
    depth = 0
    names: list[str] = []
    for index, token in enumerate(tokens):
        value = token.group(0)
        if value == "{":
            depth += 1
            continue
        if value == "}":
            depth -= 1
            if depth < 0:
                fail("E_JAVA_PARSE", f"negative brace depth: {path}")
            continue
        if depth != 0 or value not in {"class", "interface", "enum", "record"}:
            continue
        if (
            index > 0
            and tokens[index - 1].group(0) == "."
            and tokens[index - 1].end() == token.start()
        ):
            continue
        if index + 1 >= len(tokens):
            fail("E_JAVA_PARSE", f"missing type name after {value}: {path}")
        name = tokens[index + 1].group(0)
        if re.fullmatch(r"[A-Za-z_$][\w$]*", name):
            names.append(name)
    if len(names) != 1:
        fail("E_JAVA_TOP_LEVEL", f"expected one top-level type in {path}, found {names}")
    fqcn = f"{package}.{names[0]}" if package else names[0]
    return package, fqcn


def candidate_sources(root: Path, reactor_modules: Sequence[str]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for path in sorted(root.glob("**/src/test/java/**/*.java")):
        if "target" in path.parts or not path.is_file():
            continue
        patterns = discovery_patterns(path.stem)
        if not patterns:
            continue
        module, reactor_member = owning_module(root, path, reactor_modules)
        package, fqcn = java_package_and_top_level_type(path)
        relative = path.relative_to(root).as_posix()
        source_root = relative.split("/src/test/java/", 1)[0] + "/src/test/java"
        text = path.read_text(encoding="utf-8")
        rows.append(
            {
                "source_id": source_id(relative),
                "module": module,
                "reactor_member": reactor_member,
                "source_root": source_root,
                "source_path": relative,
                "top_level_fqcn": fqcn,
                "package": package,
                "discovery_patterns": ",".join(patterns),
                "source_sha256": sha256_file(path),
                "contains_nested": "@Nested" in text,
                "contains_runtime_deferred": bool(
                    re.search(r"@(ParameterizedTest|TestFactory|TestTemplate)\b", text)
                ),
                "static_test_signal": bool(
                    re.search(r"@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b", text)
                ),
            }
        )
    return rows


def module_slug(module: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "__", module)


def command_scan(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    run_dir = args.run_dir.resolve()
    run_dir.mkdir(parents=True, exist_ok=True)
    if (run_dir / "source-scan.json").exists() or (run_dir / "selectors").exists():
        fail("E_SCAN_EXISTS", f"scan marker already exists: {run_dir}")
    (run_dir / "selectors").mkdir()
    modules = active_reactor_modules(root)
    sources = candidate_sources(root, modules)
    by_module: dict[str, list[str]] = defaultdict(list)
    for source in sources:
        if source["reactor_member"]:
            by_module[source["module"]].append(source["top_level_fqcn"])
    selector_index: list[dict[str, str]] = []
    for module in sorted(by_module):
        selector_path = run_dir / "selectors" / f"{module_slug(module)}.tsv"
        with selector_path.open("w", encoding="utf-8", newline="") as stream:
            stream.write("source_fqcn\n")
            for fqcn in sorted(by_module[module]):
                stream.write(fqcn + "\n")
        selector_index.append(
            {
                "module": module,
                "selectors": selector_path.relative_to(run_dir).as_posix(),
                "classpath": f"classpaths/{module_slug(module)}.txt",
                "discovery": f"discovery/{module_slug(module)}.tsv",
            }
        )
    status_hash, dirty = git_status_hash(root)
    scan = {
        "schema_version": 1,
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "git_head": run(root, "git", "rev-parse", "HEAD"),
        "git_branch": run(root, "git", "branch", "--show-current"),
        "worktree_status_sha256": status_hash,
        "worktree_dirty": dirty,
        "protected_source_sha256": tree_hash(root, protected_source_paths(root)),
        "reactor_policy": "root default module graph; XML comments ignored; -P!multi-db",
        "reactor_modules": modules,
        "sources": sources,
        "selector_index": selector_index,
    }
    (run_dir / "source-scan.json").write_text(
        json.dumps(scan, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(
        f"[v934-inventory] scan sources={len(sources)} reactor_sources="
        f"{sum(1 for row in sources if row['reactor_member'])} modules={len(modules)} run={run_dir}"
    )


def read_tsv(path: Path, expected_header: Sequence[str] | None = None) -> list[dict[str, str]]:
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        header = reader.fieldnames or []
        if expected_header is not None and list(expected_header) != header:
            fail("E_TSV_SCHEMA", f"{path} header={header}, expected={list(expected_header)}")
        rows = [dict(row) for row in reader]
    return rows


def write_tsv(path: Path, header: Sequence[str], rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(header), delimiter="\t", lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({column: row.get(column, "") for column in header})


def load_overrides(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1:
        fail("E_OVERRIDE_SCHEMA", f"unsupported overrides schema: {data.get('schema_version')}")
    return data


def load_discovery(run_dir: Path, scan: dict[str, Any]) -> dict[str, list[dict[str, str]]]:
    expected_header = [
        "module",
        "source_fqcn",
        "report_fqcn",
        "discovered_test_nodes",
        "runtime_deferred_containers",
        "engine_ids",
    ]
    result: dict[str, list[dict[str, str]]] = defaultdict(list)
    for item in scan["selector_index"]:
        path = run_dir / item["discovery"]
        if not path.is_file():
            fail("E_DISCOVERY_MISSING", f"missing discovery output: {path}")
        for row in read_tsv(path, expected_header):
            if row["module"] != item["module"]:
                fail("E_DISCOVERY_MODULE", f"module mismatch in {path}: {row['module']}")
            result[row["source_fqcn"]].append(row)
    return result


def load_classpath_rows(root: Path, run_dir: Path, scan: dict[str, Any]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    maven_root = Path.home() / ".m2/repository"
    for item in scan["selector_index"]:
        classpath_file = run_dir / item["classpath"]
        raw_entries = [
            value
            for value in classpath_file.read_text(encoding="utf-8").strip().split(os.pathsep)
            if value
        ]
        manifest = run_dir / f"{item['classpath']}.sha256.tsv"
        entries = read_tsv(manifest, ["entry", "sha256"])
        if len(raw_entries) != len(entries):
            fail(
                "E_CLASSPATH_CARDINALITY",
                f"module={item['module']} raw={len(raw_entries)} manifest={len(entries)}",
            )
        for ordinal, entry in enumerate(entries, start=1):
            raw_entry = raw_entries[ordinal - 1]
            if entry["entry"] != raw_entry:
                fail(
                    "E_CLASSPATH_ORDER",
                    f"module={item['module']} ordinal={ordinal} raw={raw_entry} manifest={entry['entry']}",
                )
            path = Path(entry["entry"])
            if classpath_entry_sha256(root, path) != entry["sha256"]:
                fail(
                    "E_CLASSPATH_HASH",
                    f"module={item['module']} ordinal={ordinal} entry={path}",
                )
            try:
                identity = "m2:" + path.relative_to(maven_root).as_posix()
            except ValueError:
                try:
                    identity = "repo:" + path.relative_to(root).as_posix()
                except ValueError:
                    fail("E_CLASSPATH_IDENTITY", f"classpath entry outside repo/m2: {path}")
            rows.append(
                {
                    "module": item["module"],
                    "ordinal": str(ordinal),
                    "entry_identity": identity,
                    "entry_sha256": entry["sha256"],
                }
            )
    return rows


def hash_class_tree(root: Path, module: str, tree_name: str) -> str:
    directory = root / module / f"target/{tree_name}"
    if not directory.is_dir():
        fail("E_TEST_CLASSES", f"missing {tree_name} for module {module}")
    return directory_tree_hash(directory)


def normalized_variant(spec: dict[str, Any], owner: str) -> dict[str, str]:
    required = bool(spec.get("required", True))
    return {
        "runner": str(spec["runner"]),
        "lane": str(spec["lane"]),
        "variant_key": str(spec["variant_key"]),
        "db_kind": str(spec.get("db_kind", "none")),
        "infra_kind": str(spec["infra_kind"]),
        "execution_step": str(spec["execution_step"]),
        "required": "true" if required else "false",
        "owner": str(spec.get("owner", owner)),
        "optional_reason": "none" if required else str(spec.get("optional_reason", "")),
        "review_at": "none" if required else str(spec.get("review_at", "")),
        "report_regex": str(spec.get("report_regex", ".*")),
    }


def variants_for_source(source: dict[str, Any], overrides: dict[str, Any]) -> list[dict[str, str]]:
    path = source["source_path"]
    configured = overrides.get("execution", {}).get(path)
    if configured is not None:
        return [normalized_variant(spec, source["module"]) for spec in configured]
    name = Path(path).stem
    if any(fnmatch.fnmatchcase(name, pattern) for pattern in MANUAL_PATTERNS):
        fail("E_MANUAL_CLASSIFICATION", f"manual execution classification missing: {path}")
    return [
        normalized_variant(
            {
                "runner": "surefire",
                "lane": "unit",
                "variant_key": "unit",
                "db_kind": "none",
                "infra_kind": "hermetic",
                "execution_step": 2,
                "required": True,
            },
            source["module"],
        )
    ]


def build_rows(
    root: Path,
    scan: dict[str, Any],
    discovery: dict[str, list[dict[str, str]]],
    overrides: dict[str, Any],
) -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    helper_overrides = overrides.get("source_disposition", {})
    source_rows: list[dict[str, str]] = []
    discovery_rows: list[dict[str, str]] = []
    execution_rows: list[dict[str, str]] = []
    test_class_hashes = {
        module: hash_class_tree(root, module, "test-classes")
        for module in sorted({row["module"] for row in scan["sources"] if row["reactor_member"]})
    }
    main_class_hashes = {
        module: hash_class_tree(root, module, "classes")
        for module in sorted({row["module"] for row in scan["sources"] if row["reactor_member"]})
    }
    for source in sorted(scan["sources"], key=lambda row: row["source_path"]):
        path = source["source_path"]
        config = helper_overrides.get(path, {})
        raw_reports = discovery.get(source["top_level_fqcn"], []) if source["reactor_member"] else []
        reports = sorted(
            [row for row in raw_reports if row["report_fqcn"] != "none"],
            key=lambda row: row["report_fqcn"],
        )
        if source["reactor_member"]:
            if source["top_level_fqcn"] not in discovery:
                fail("E_DISCOVERY_SOURCE", f"source missing from discovery ledger: {path}")
            kind = str(config.get("kind", "executable" if reports else ""))
            if not kind:
                fail("E_DISCOVERY_ZERO_UNREVIEWED", f"zero-report source needs disposition: {path}")
        else:
            kind = str(config.get("kind", "executable" if source["static_test_signal"] else "helper"))
        if kind not in KINDS:
            fail("E_SOURCE_KIND", f"invalid kind={kind}: {path}")
        if source["reactor_member"] and kind == "executable" and not reports:
            fail("E_EXEC_ZERO", f"executable source has zero discovered reports: {path}")
        if kind != "executable" and reports:
            fail("E_NONEXEC_REPORT", f"non-executable source discovered reports: {path}")
        if not source["reactor_member"]:
            disposition = "non-reactor-excluded"
            reason = str(config.get("reason", "outside active root reactor; reviewed source-only disposition"))
        elif kind == "helper":
            disposition = "non-executable-helper"
            reason = str(config.get("reason", ""))
        elif kind == "generator":
            disposition = "non-executable-generator"
            reason = str(config.get("reason", ""))
        else:
            disposition = "reactor-owned-executable"
            reason = str(config.get("reason", "JUnit Platform ClassSource discovery owner"))
        owner = str(config.get("owner", source["module"]))
        source_row = {
            "source_id": source["source_id"],
            "module": source["module"],
            "reactor_member": str(source["reactor_member"]).lower(),
            "source_root": source["source_root"],
            "source_path": path,
            "top_level_fqcn": source["top_level_fqcn"],
            "kind": kind,
            "discovery_patterns": source["discovery_patterns"],
            "disposition": disposition,
            "owner": owner,
            "reason": reason,
        }
        source_rows.append(source_row)
        if source["reactor_member"]:
            for report in raw_reports:
                discovery_rows.append(
                    {
                        "module": source["module"],
                        "source_id": source["source_id"],
                        "source_fqcn": source["top_level_fqcn"],
                        "report_fqcn": report["report_fqcn"],
                        "discovered_test_nodes": report["discovered_test_nodes"],
                        "runtime_deferred_containers": report["runtime_deferred_containers"],
                        "engine_ids": report["engine_ids"],
                        "source_sha256": source["source_sha256"],
                        "test_classes_sha256": test_class_hashes[source["module"]],
                        "main_classes_sha256": main_class_hashes[source["module"]],
                    }
                )
        if not source["reactor_member"] or kind != "executable":
            continue
        variants = variants_for_source(source, overrides)
        matched_variants: Counter[int] = Counter()
        for report in reports:
            matching = [
                (index, variant)
                for index, variant in enumerate(variants)
                if re.fullmatch(variant["report_regex"], report["report_fqcn"])
            ]
            if not matching:
                fail("E_REPORT_VARIANT", f"no execution variant owns {report['report_fqcn']} from {path}")
            for index, variant in matching:
                matched_variants[index] += 1
                row = {column: variant.get(column, "") for column in EXECUTION_HEADER}
                row["source_id"] = source["source_id"]
                row["report_fqcn"] = report["report_fqcn"]
                row["execution_key"] = execution_key(
                    row["runner"], row["lane"], row["variant_key"], row["report_fqcn"]
                )
                execution_rows.append(row)
        for index, variant in enumerate(variants):
            if matched_variants[index] == 0:
                fail(
                    "E_VARIANT_UNUSED",
                    f"variant {variant['variant_key']} matches no report for {path}",
                )
    return source_rows, discovery_rows, execution_rows


def build_rename_rows(
    source_rows: list[dict[str, str]],
    execution_rows: list[dict[str, str]],
    reviewer: str,
    contract: dict[str, Any],
) -> list[dict[str, str]]:
    current_suffix = str(contract["current_suffix"])
    target_suffix = str(contract["target_suffix"])
    source_by_id = {row["source_id"]: row for row in source_rows}
    current_paths = {row["source_path"] for row in source_rows}
    current_fqcns = {row["top_level_fqcn"] for row in source_rows}
    unaffected_execution_keys = {
        row["execution_key"]
        for row in execution_rows
        if not source_by_id[row["source_id"]]["top_level_fqcn"].endswith(current_suffix)
    }
    rows: list[dict[str, str]] = []
    target_paths: set[str] = set()
    target_fqcns: set[str] = set()
    target_execution_keys: set[str] = set()
    for execution in sorted(execution_rows, key=lambda row: row["execution_key"]):
        source = source_by_id[execution["source_id"]]
        current_fqcn = source["top_level_fqcn"]
        if not current_fqcn.endswith(current_suffix):
            continue
        if not source["source_path"].endswith(current_suffix + ".java"):
            fail("E_RENAME_PLAN", f"source path/class suffix differs: {source['source_path']}")
        if execution["runner"] != "failsafe":
            fail("E_RENAME_PLAN", f"IntegrationTest is not Failsafe-owned: {execution['execution_key']}")
        target_path = source["source_path"].removesuffix(current_suffix + ".java") + target_suffix + ".java"
        target_fqcn = current_fqcn.removesuffix(current_suffix) + target_suffix
        if not (
            execution["report_fqcn"] == current_fqcn
            or execution["report_fqcn"].startswith(current_fqcn + "$")
        ):
            fail("E_RENAME_PLAN", f"report is outside rename source: {execution['report_fqcn']}")
        target_report = target_fqcn + execution["report_fqcn"][len(current_fqcn) :]
        target_key = execution_key(
            execution["runner"],
            execution["lane"],
            execution["variant_key"],
            target_report,
        )
        if target_path in current_paths or target_fqcn in current_fqcns:
            fail("E_RENAME_PLAN", f"rename target already exists: {target_path}")
        if target_key in unaffected_execution_keys or target_key in target_execution_keys:
            fail("E_RENAME_PLAN", f"rename target execution key collides: {target_key}")
        target_paths.add(target_path)
        target_fqcns.add(target_fqcn)
        target_execution_keys.add(target_key)
        rows.append(
            {
                "rename_group": "v934-rename-" + source["source_id"],
                "current_source_id": source["source_id"],
                "current_source_path": source["source_path"],
                "current_top_level_fqcn": current_fqcn,
                "current_report_fqcn": execution["report_fqcn"],
                "current_execution_key": execution["execution_key"],
                "target_source_id": source_id(target_path),
                "target_source_path": target_path,
                "target_top_level_fqcn": target_fqcn,
                "target_report_fqcn": target_report,
                "target_execution_key": target_key,
                "runner": execution["runner"],
                "lane": execution["lane"],
                "variant_key": execution["variant_key"],
                "db_kind": execution["db_kind"],
                "infra_kind": execution["infra_kind"],
                "execution_step": execution["execution_step"],
                "required": execution["required"],
                "owner": execution["owner"],
                "optional_reason": execution["optional_reason"],
                "review_at": execution["review_at"],
                "rationale": "reviewed real integration owner; rename ambiguous IntegrationTest suffix to IT without semantic ownership changes",
                "reviewer": reviewer,
            }
        )
    if len(target_paths) != len(target_fqcns):
        fail("E_RENAME_PLAN", "rename source path/FQCN cardinality differs")
    return rows


def validate_rename_plan(
    source_rows: list[dict[str, str]],
    execution_rows: list[dict[str, str]],
    rename_rows: list[dict[str, str]],
    mappings: list[dict[str, str]],
    contract: dict[str, Any],
    expected_reviewer: str,
) -> dict[str, int]:
    reviewers = {row["reviewer"] for row in rename_rows}
    if reviewers != {expected_reviewer}:
        fail("E_RENAME_PLAN", "rename plan reviewer set is not exact")
    expected = build_rename_rows(source_rows, execution_rows, expected_reviewer, contract)
    if rename_rows != expected:
        fail("E_RENAME_PLAN", "rename plan differs from current source/execution exact transform")
    current_source_ids = {row["current_source_id"] for row in rename_rows}
    target_source_ids = {row["target_source_id"] for row in rename_rows}
    current_reports = {row["current_report_fqcn"] for row in rename_rows}
    target_reports = {row["target_report_fqcn"] for row in rename_rows}
    current_keys = {row["current_execution_key"] for row in rename_rows}
    target_keys = {row["target_execution_key"] for row in rename_rows}
    predecessor_edges = sum(row["successor_execution_key"] in current_keys for row in mappings)
    counts = {
        "rename_sources": len(current_source_ids),
        "rename_reports": len(current_reports),
        "rename_execution_keys": len(rename_rows),
        "rename_predecessor_edges": predecessor_edges,
    }
    expected_counts = {
        "rename_sources": int(contract["source_count"]),
        "rename_reports": int(contract["report_count"]),
        "rename_execution_keys": int(contract["execution_count"]),
        "rename_predecessor_edges": int(contract["predecessor_edge_count"]),
    }
    if counts != expected_counts:
        fail("E_RENAME_PLAN", f"rename cardinality differs expected={expected_counts} actual={counts}")
    if len(target_source_ids) != counts["rename_sources"]:
        fail("E_RENAME_PLAN", "rename target source cardinality differs")
    if len(target_reports) != counts["rename_reports"] or len(target_keys) != len(rename_rows):
        fail("E_RENAME_PLAN", "rename target report/execution cardinality differs")
    return counts


def parse_suite_name(path: Path) -> str:
    root = ET.parse(path).getroot()
    name = root.attrib.get("name", "")
    if not name:
        fail("E_PREDECESSOR_XML", f"testsuite name missing: {path}")
    return re.sub(r"\(batch7-[^)]+\)$", "", name)


def predecessor_variant_for_path(path: Path) -> tuple[str, str, str]:
    parts = path.parts
    if "v933-batch7-regression" in parts:
        lane_index = parts.index("lanes") + 1
        lane = parts[lane_index]
        variants = {
            "api-compat": "unit",
            "watcher-source-management": "unit",
            "binding-publication-lock": "unit",
            "isolation-main": "unit-or-sqlite",
            "autoconfig-launcher": "unit",
            "sqlite-full": "sqlite-successor",
            "multidb-mysql57": "db-mysql57",
            "multidb-postgres15": "db-postgres15",
            "multidb-sqlserver2022": "db-sqlserver2022",
        }
        return lane.upper().replace("-", "_"), lane, variants[lane]
    if "v933-batch6-real-query" in parts:
        lane_index = parts.index("lanes") + 1
        lane = parts[lane_index]
        variants = {
            "cache-caffeine": "caffeine-sqlite",
            "cache-redis": "redis7-sqlite",
            "database-mysql57": "db-mysql57",
            "database-postgres15": "db-postgres15",
            "database-sqlite": "db-sqlite",
            "model-lifecycle-sqlite": "sqlite-lifecycle",
        }
        return "REAL_QUERY", lane, variants[lane]
    fail("E_PREDECESSOR_PATH", f"unrecognized historical report path: {path}")


def resolve_successor(
    report_fqcn: str,
    historical_variant: str,
    execution_rows: list[dict[str, str]],
) -> dict[str, str]:
    candidates = [row for row in execution_rows if row["report_fqcn"] == report_fqcn]
    if not candidates:
        fail("E_MIGRATION_SUCCESSOR", f"no successor report for {report_fqcn}")
    exact = [row for row in candidates if row["variant_key"] == historical_variant]
    if exact:
        candidates = exact
    elif historical_variant == "sqlite-successor":
        preferred = [
            row
            for row in candidates
            if row["required"] == "true"
            and (row["db_kind"] == "sqlite" or row["variant_key"] == "unit")
        ]
        if preferred:
            candidates = preferred
    elif historical_variant == "unit-or-sqlite":
        preferred = [
            row
            for row in candidates
            if row["required"] == "true" and row["execution_step"] == "2"
        ]
        if preferred:
            candidates = preferred
    elif historical_variant == "unit":
        preferred = [row for row in candidates if row["variant_key"] == "unit"]
        if preferred:
            candidates = preferred
    if len(candidates) != 1:
        details = [(row["lane"], row["variant_key"], row["db_kind"]) for row in candidates]
        fail(
            "E_MIGRATION_AMBIGUOUS",
            f"successor for {report_fqcn}/{historical_variant} is ambiguous: {details}",
        )
    return candidates[0]


def build_predecessor_rows(
    authority_root: Path,
    execution_rows: list[dict[str, str]],
    reviewer: str,
) -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    if not authority_root.is_dir():
        fail("E_PREDECESSOR_ROOT", f"historical authority root missing: {authority_root}")
    run_id = authority_root.name
    reports = sorted((authority_root / "lanes").glob("*/reports/TEST-*.xml"))
    real_summary = authority_root / "lanes/real-query/summary.env"
    if not real_summary.is_file():
        fail("E_PREDECESSOR_REAL_QUERY", f"missing real-query summary: {real_summary}")
    child_root = ""
    for line in real_summary.read_text(encoding="utf-8").splitlines():
        if line.startswith("child_run_root="):
            child_root = line.split("=", 1)[1]
    if not child_root:
        fail("E_PREDECESSOR_REAL_QUERY", "child_run_root missing from real-query summary")
    reports.extend(sorted(Path(child_root).glob("lanes/*/failsafe-reports/TEST-*.xml")))
    nodes: list[dict[str, str]] = []
    mappings: list[dict[str, str]] = []
    seen_nodes: set[str] = set()
    for report_path in reports:
        criterion, lane, historical_variant = predecessor_variant_for_path(report_path)
        report_fqcn = parse_suite_name(report_path)
        node = predecessor_node(run_id, lane, historical_variant, report_fqcn)
        if node in seen_nodes:
            fail("E_PREDECESSOR_DUP", f"duplicate predecessor node: {node}")
        seen_nodes.add(node)
        successor = resolve_successor(report_fqcn, historical_variant, execution_rows)
        group = "v934-map-" + sha256_bytes(node.encode("utf-8"))[:20]
        nodes.append(
            {
                "predecessor_node": node,
                "criterion": criterion,
                "historical_lane": lane,
                "variant_key": historical_variant,
                "report_fqcn": report_fqcn,
                "raw_report_sha256": sha256_file(report_path),
                "authority_run_id": run_id,
            }
        )
        mappings.append(
            {
                "mapping_group": group,
                "relation": "1:1",
                "declared_old_count": "1",
                "declared_successor_count": "1",
                "criterion": criterion,
                "predecessor_node": node,
                "successor_execution_key": successor["execution_key"],
                "disposition": "current-source-successor",
                "rationale": "same report owner retained under the reviewed v934 runner/variant split",
                "owner": successor["owner"],
                "reviewer": reviewer,
            }
        )
    return nodes, mappings


def write_static_contracts(output_dir: Path, overrides: dict[str, Any]) -> None:
    write_tsv(output_dir / DB_FILE, DB_HEADER, overrides["database_contract"])
    write_tsv(output_dir / PACKAGE_FILE, PACKAGE_HEADER, overrides["package_contract"])
    write_tsv(
        output_dir / MAVEN_VARIANT_FILE,
        MAVEN_VARIANT_HEADER,
        overrides["maven_variant_contract"],
    )
    (output_dir / COVERAGE_FILE).write_text(
        json.dumps(overrides["coverage_contract"], indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def hash_input_names(output_dir: Path) -> list[str]:
    names = [
        "JUnitDiscoveryInventory.java",
        "inventory_tool.py",
        "inventory-overrides.json",
        SOURCE_FILE,
        DISCOVERY_FILE,
        CLASSPATH_FILE,
        EXECUTION_FILE,
        RENAME_FILE,
        PREDECESSOR_NODE_FILE,
        MIGRATION_FILE,
        DB_FILE,
        PACKAGE_FILE,
        MAVEN_VARIANT_FILE,
        COVERAGE_FILE,
        FREEZE_FILE,
        NEGATIVE_FILE,
    ]
    return sorted(names)


def write_hash_manifest(output_dir: Path) -> None:
    names = hash_input_names(output_dir)
    missing = [name for name in names if not (output_dir / name).is_file()]
    if missing:
        fail("E_HASH_INPUT", f"missing hash inputs: {missing}")
    lines = [f"{sha256_file(output_dir / name)}  {name}" for name in names]
    (output_dir / HASH_FILE).write_text("\n".join(lines) + "\n", encoding="utf-8")


def command_generate(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    run_dir = args.run_dir.resolve()
    output_dir = args.output_dir.resolve()
    overrides_path = args.overrides.resolve()
    overrides = load_overrides(overrides_path)
    scan = json.loads((run_dir / "source-scan.json").read_text(encoding="utf-8"))
    discovery = load_discovery(run_dir, scan)
    classpath_rows = load_classpath_rows(root, run_dir, scan)
    source_rows, discovery_rows, execution_rows = build_rows(root, scan, discovery, overrides)
    validate_rows(root, source_rows, discovery_rows, execution_rows, scan, overrides)
    rename_rows = build_rename_rows(
        source_rows,
        execution_rows,
        str(overrides["reviewer"]),
        overrides["rename_contract"],
    )
    nodes, mappings = build_predecessor_rows(
        args.authority_root.resolve(), execution_rows, str(overrides["reviewer"])
    )
    rename_counts = validate_rename_plan(
        source_rows,
        execution_rows,
        rename_rows,
        mappings,
        overrides["rename_contract"],
        str(overrides["reviewer"]),
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    if overrides_path.parent != output_dir:
        shutil.copy2(overrides_path, output_dir / "inventory-overrides.json")
    write_tsv(output_dir / SOURCE_FILE, SOURCE_HEADER, source_rows)
    write_tsv(output_dir / DISCOVERY_FILE, DISCOVERY_HEADER, discovery_rows)
    write_tsv(output_dir / CLASSPATH_FILE, CLASSPATH_HEADER, classpath_rows)
    write_tsv(output_dir / EXECUTION_FILE, EXECUTION_HEADER, execution_rows)
    write_tsv(output_dir / RENAME_FILE, RENAME_HEADER, rename_rows)
    write_tsv(output_dir / PREDECESSOR_NODE_FILE, PREDECESSOR_NODE_HEADER, nodes)
    write_tsv(output_dir / MIGRATION_FILE, MIGRATION_HEADER, mappings)
    write_static_contracts(output_dir, overrides)
    status_hash, dirty = git_status_hash(root)
    generator_class_files = sorted(
        path.relative_to(run_dir / "tool-classes").as_posix()
        for path in (run_dir / "tool-classes").rglob("*.class")
    )
    if not generator_class_files:
        fail("E_DISCOVERY_TOOL", "compiled discovery helper classes are missing")
    freeze = {
        "schema_version": 1,
        "version": "9.3.4",
        "step": 1,
        "status": "candidate",
        "generation": "step1-pre-rename",
        "decision": "pending-independent-review",
        "reviewer": overrides["reviewer"],
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "baseline": {
            "git_head": scan["git_head"],
            "git_branch": scan["git_branch"],
            "protected_source_sha256": scan["protected_source_sha256"],
            "worktree_status_sha256_at_freeze": status_hash,
            "worktree_dirty": dirty,
        },
        "reactor": {
            "profile_policy": "explicit -P!multi-db for discovery/test-compile",
            "modules": scan["reactor_modules"],
            "module_count": len(scan["reactor_modules"]),
            "path_policy": "repo-relative POSIX versioned src/test/java; generated roots require override",
        },
        "discovery": {
            "engine": "junit-jupiter",
            "platform_version": overrides["toolchain"]["junit_platform_version"],
            "launcher_sha256": overrides["toolchain"]["junit_platform_launcher_sha256"],
            "mode": "ClassSource containers from Launcher.discover; execute forbidden",
            "runtime_invocation_policy": "parameterized/template/factory cardinality deferred to owning lane",
            "sealed_xml_corroboration": overrides["predecessor_authority_run_id"],
        },
        "toolchain": {
            "wrapper_path": "scripts/verify-v934-test-inventory.sh",
            "wrapper_sha256": sha256_file(root / "scripts/verify-v934-test-inventory.sh"),
            "inventory_tool_sha256": sha256_file(output_dir / "inventory_tool.py"),
            "inventory_overrides_sha256": sha256_file(output_dir / "inventory-overrides.json"),
            "discovery_source_sha256": sha256_file(output_dir / "JUnitDiscoveryInventory.java"),
            "generator_class_files": generator_class_files,
            "generator_classes_sha256": directory_tree_hash(run_dir / "tool-classes"),
            "java_version": version_line(root, "java", "-version"),
            "javac_version": version_line(root, "javac", "-version"),
            "maven_version": version_line(root, "mvn", "-version"),
        },
        "execution_key": {
            "encoding": "v934|<utf8-byte-len>:runner|<len>:lane|<len>:variant_key|<len>:report_fqcn",
            "empty_variant": "default",
            "non_database_db_kind": "none",
        },
        "successor_policy": {
            "rename_plan": RENAME_FILE,
            "rename_plan_sha256": sha256_file(output_dir / RENAME_FILE),
            "current_suffix": overrides["rename_contract"]["current_suffix"],
            "target_suffix": overrides["rename_contract"]["target_suffix"],
            "successor_output": overrides["rename_contract"]["successor_output"],
            "required_parent_link": "confirmed Step1 summary contract_manifest_sha256 plus rename_plan_sha256",
            "refresh_rule": overrides["rename_contract"]["policy"],
        },
        "skip_manifest_schema": ["lane", "fqcn#method", "reason", "owner", "expiry", "required"],
        "required_skip_target": 0,
        "stable_required_check": "required / test-ci-evidence-chain",
        "evidence_layout": {
            "candidate": "target/v934-release-evidence/candidates/<source-state>-<run-id>/",
            "authority": "target/v934-release-evidence/runs/<commit-sha>-<workflow-run-id>-<attempt>/",
        },
        "counts": {
            "workspace_sources": len(source_rows),
            "reactor_sources": sum(row["reactor_member"] == "true" for row in source_rows),
            "discovery_modules": len(scan["selector_index"]),
            "classpath_entries": len(classpath_rows),
            "nested_sources": sum(bool(row["contains_nested"]) for row in scan["sources"]),
            "discovery_reports": sum(row["report_fqcn"] != "none" for row in discovery_rows),
            "execution_keys": len(execution_rows),
            "required_step2": sum(
                row["required"] == "true" and row["execution_step"] == "2" for row in execution_rows
            ),
            "required_step3": sum(
                row["required"] == "true" and row["execution_step"] == "3" for row in execution_rows
            ),
            "predecessor_nodes": len(nodes),
            "migration_edges": len(mappings),
            **rename_counts,
        },
    }
    (output_dir / FREEZE_FILE).write_text(
        json.dumps(freeze, indent=2, sort_keys=True, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    write_hash_manifest(output_dir)
    print(
        f"[v934-inventory] generated sources={len(source_rows)} reports="
        f"{freeze['counts']['discovery_reports']} executions={len(execution_rows)} "
        f"predecessors={len(nodes)} rename_executions={len(rename_rows)} output={output_dir}"
    )


def duplicate_values(rows: Sequence[dict[str, str]], key: str) -> list[str]:
    counts = Counter(row[key] for row in rows)
    return sorted(value for value, count in counts.items() if count > 1)


def validate_rows(
    root: Path,
    sources: list[dict[str, str]],
    discoveries: list[dict[str, str]],
    executions: list[dict[str, str]],
    scan: dict[str, Any] | None = None,
    overrides: dict[str, Any] | None = None,
) -> None:
    if duplicate_values(sources, "source_id") or duplicate_values(sources, "source_path"):
        fail("E_SOURCE_DUP", "duplicate source_id or source_path")
    actual_paths = {row["source_path"] for row in sources}
    reactor_modules = active_reactor_modules(root)
    workspace_paths = {row["source_path"] for row in candidate_sources(root, reactor_modules)}
    if actual_paths != workspace_paths:
        fail(
            "E_SOURCE_SET",
            f"source inventory diff missing={sorted(workspace_paths - actual_paths)[:5]} "
            f"orphan={sorted(actual_paths - workspace_paths)[:5]}",
        )
    source_by_id = {row["source_id"]: row for row in sources}
    for row in sources:
        if row["kind"] not in KINDS:
            fail("E_SOURCE_KIND", f"invalid kind: {row}")
        if not row["owner"]:
            fail("E_SOURCE_OWNER", f"missing owner: {row['source_path']}")
        if not row["reason"]:
            fail("E_SOURCE_REASON", f"missing reason: {row['source_path']}")
        if row["reactor_member"] == "false" and row["disposition"] != "non-reactor-excluded":
            fail("E_SOURCE_DISPOSITION", f"non-reactor disposition: {row['source_path']}")
        if row["source_id"] != source_id(row["source_path"]):
            fail("E_SOURCE_ID", f"source id mismatch: {row['source_path']}")
    discovery_keys: set[tuple[str, str]] = set()
    discoveries_by_source: dict[str, list[dict[str, str]]] = defaultdict(list)
    reactor_modules_with_sources = {
        row["module"] for row in sources if row["reactor_member"] == "true"
    }
    test_class_hashes = {
        module: hash_class_tree(root, module, "test-classes")
        for module in reactor_modules_with_sources
    }
    main_class_hashes = {
        module: hash_class_tree(root, module, "classes")
        for module in reactor_modules_with_sources
    }
    for row in discoveries:
        source = source_by_id.get(row["source_id"])
        if source is None:
            fail("E_DISCOVERY_ORPHAN", f"unknown discovery source id: {row['source_id']}")
        if source["reactor_member"] != "true":
            fail("E_DISCOVERY_NONREACTOR", f"non-reactor discovery row: {source['source_path']}")
        if row["module"] != source["module"] or row["source_fqcn"] != source["top_level_fqcn"]:
            fail("E_DISCOVERY_OWNER", f"discovery owner mismatch: {source['source_path']}")
        key = (row["source_id"], row["report_fqcn"])
        if key in discovery_keys:
            fail("E_DISCOVERY_DUP", f"duplicate discovery row: {key}")
        discovery_keys.add(key)
        discoveries_by_source[row["source_id"]].append(row)
        try:
            discovered_nodes = int(row["discovered_test_nodes"])
            deferred_nodes = int(row["runtime_deferred_containers"])
        except ValueError:
            fail("E_DISCOVERY_COUNT", f"non-integer discovery counts: {row}")
        if discovered_nodes < 0 or deferred_nodes < 0:
            fail("E_DISCOVERY_COUNT", f"negative discovery counts: {row}")
        if row["report_fqcn"] == "none":
            if source["kind"] == "executable" or discovered_nodes != 0 or deferred_nodes != 0:
                fail("E_DISCOVERY_NONE", f"invalid zero-report discovery row: {source['source_path']}")
            if row["engine_ids"] != "none":
                fail("E_DISCOVERY_ENGINE", f"zero-report row has an engine: {source['source_path']}")
        else:
            if not (
                row["report_fqcn"] == source["top_level_fqcn"]
                or row["report_fqcn"].startswith(source["top_level_fqcn"] + "$")
            ):
                fail("E_DISCOVERY_REPORT", f"report is outside source owner: {row['report_fqcn']}")
            if row["engine_ids"] != "junit-jupiter":
                fail("E_DISCOVERY_ENGINE", f"unexpected discovery engine: {row['engine_ids']}")
        expected_source_hash = sha256_file(root / source["source_path"])
        if (
            row["source_sha256"] != expected_source_hash
            or row["test_classes_sha256"] != test_class_hashes[source["module"]]
            or row["main_classes_sha256"] != main_class_hashes[source["module"]]
        ):
            fail("E_DISCOVERY_HASH", f"discovery hash mismatch: {source['source_path']}")
    for source in sources:
        owned_discoveries = discoveries_by_source[source["source_id"]]
        if source["reactor_member"] == "true" and not owned_discoveries:
            fail("E_DISCOVERY_MISSING", f"source has no discovery row: {source['source_path']}")
        if source["reactor_member"] == "false" and owned_discoveries:
            fail("E_DISCOVERY_NONREACTOR", f"non-reactor source has discovery rows: {source['source_path']}")
    if duplicate_values(executions, "execution_key"):
        fail("E_EXEC_KEY_DUP", "duplicate execution key")
    ownership: set[tuple[str, str, str]] = set()
    report_variant: set[tuple[str, str, str, str]] = set()
    by_source: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in executions:
        source = source_by_id.get(row["source_id"])
        if source is None:
            fail("E_EXEC_ORPHAN", f"unknown source id: {row['source_id']}")
        if source["reactor_member"] != "true" or source["kind"] != "executable":
            fail("E_NONEXEC_EXEC", f"execution row owned by non-executable source: {source['source_path']}")
        if row["runner"] not in RUNNERS:
            fail("E_RUNNER", f"invalid runner: {row['runner']}")
        if row["lane"] not in LANES:
            fail("E_LANE", f"invalid lane: {row['lane']}")
        if row["db_kind"] not in DB_KINDS or row["infra_kind"] not in INFRA_KINDS:
            fail("E_INFRA", f"invalid db/infra: {row['db_kind']}/{row['infra_kind']}")
        if row["execution_step"] not in {"2", "3"}:
            fail("E_EXEC_STEP", f"invalid execution step: {row['execution_step']}")
        if row["required"] not in {"true", "false"}:
            fail("E_REQUIRED", f"invalid required flag: {row['required']}")
        if not row["owner"]:
            fail("E_EXEC_OWNER", f"missing execution owner: {row['execution_key']}")
        if row["required"] == "true":
            if row["optional_reason"] != "none" or row["review_at"] != "none":
                fail("E_OPTIONAL_METADATA", f"required row has optional metadata: {row['execution_key']}")
        else:
            if not row["optional_reason"] or row["optional_reason"] == "none":
                fail("E_OPTIONAL_METADATA", f"optional reason missing: {row['execution_key']}")
            if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", row["review_at"]):
                fail("E_OPTIONAL_METADATA", f"optional review_at invalid: {row['execution_key']}")
        expected_key = execution_key(
            row["runner"], row["lane"], row["variant_key"], row["report_fqcn"]
        )
        if row["execution_key"] != expected_key:
            fail("E_EXEC_KEY", f"execution key mismatch: {row['execution_key']}")
        owner_key = (row["source_id"], row["report_fqcn"], row["variant_key"])
        if owner_key in ownership:
            fail("E_RUNNER_OVERLAP", f"report variant has multiple runner owners: {owner_key}")
        ownership.add(owner_key)
        variant_key = (row["report_fqcn"], row["db_kind"], row["lane"], row["variant_key"])
        if variant_key in report_variant:
            fail("E_REPORT_VARIANT_DUP", f"duplicate report/db/lane/variant: {variant_key}")
        report_variant.add(variant_key)
        by_source[row["source_id"]].append(row)
    for source in sources:
        owned = by_source[source["source_id"]]
        if source["reactor_member"] == "true" and source["kind"] == "executable" and not owned:
            fail("E_EXEC_ZERO", f"executable source has zero execution rows: {source['source_path']}")
        if (source["kind"] != "executable" or source["reactor_member"] == "false") and owned:
            fail("E_NONEXEC_EXEC", f"non-executable source has execution rows: {source['source_path']}")
    broad = {
        row["report_fqcn"]
        for row in executions
        if row["lane"] == "sqlite-broad-integration" and row["db_kind"] == "sqlite"
    }
    matrix_sqlite = {
        row["report_fqcn"]
        for row in executions
        if row["lane"] == "database-contract-matrix" and row["db_kind"] == "sqlite"
    }
    if broad & matrix_sqlite:
        fail("E_SQLITE_OVERLAP", f"SQLite broad/matrix overlap: {sorted(broad & matrix_sqlite)[:5]}")
    discovery_reports: dict[str, set[str]] = defaultdict(set)
    for row in discoveries:
        if row["report_fqcn"] != "none":
            discovery_reports[row["source_id"]].add(row["report_fqcn"])
    for source in sources:
        expected = discovery_reports[source["source_id"]]
        actual = {row["report_fqcn"] for row in by_source[source["source_id"]]}
        if source["reactor_member"] == "true" and source["kind"] == "executable":
            if expected - actual:
                fail("E_REPORT_MISSING", f"missing report owners for {source['source_path']}: {expected - actual}")
            if actual - expected:
                fail("E_REPORT_UNEXPECTED", f"unexpected report owners: {actual - expected}")
    required = [row for row in executions if row["required"] == "true"]
    if any(row["execution_step"] not in {"2", "3"} for row in required):
        fail("E_STEP_COVERAGE", "required execution outside Step 2/3")


def validate_migration(
    executions: list[dict[str, str]],
    nodes: list[dict[str, str]],
    mappings: list[dict[str, str]],
) -> None:
    execution_keys = {row["execution_key"] for row in executions}
    node_ids = {row["predecessor_node"] for row in nodes}
    if len(node_ids) != len(nodes):
        fail("E_PREDECESSOR_DUP", "duplicate predecessor nodes")
    mapped_nodes = {row["predecessor_node"] for row in mappings}
    if node_ids != mapped_nodes:
        fail(
            "E_MIGRATION_UNMAPPED",
            f"migration node diff unmapped={len(node_ids - mapped_nodes)} orphan={len(mapped_nodes - node_ids)}",
        )
    edge_tuples = [
        (row["mapping_group"], row["predecessor_node"], row["successor_execution_key"])
        for row in mappings
    ]
    if len(edge_tuples) != len(set(edge_tuples)):
        fail("E_MIGRATION_EDGE_DUP", "duplicate migration edge")
    groups: dict[str, list[dict[str, str]]] = defaultdict(list)
    node_group: dict[str, str] = {}
    for row in mappings:
        if row["relation"] not in {"1:1", "1:N", "N:1"}:
            fail("E_MIGRATION_RELATION", f"invalid relation: {row['relation']}")
        if row["successor_execution_key"] not in execution_keys:
            fail("E_MIGRATION_SUCCESSOR", f"unknown successor key: {row['successor_execution_key']}")
        previous = node_group.setdefault(row["predecessor_node"], row["mapping_group"])
        if previous != row["mapping_group"]:
            fail("E_MIGRATION_GROUP", f"predecessor belongs to multiple groups: {row['predecessor_node']}")
        if not row["rationale"] or not row["reviewer"]:
            fail("E_MIGRATION_REVIEW", f"migration rationale/reviewer missing: {row['mapping_group']}")
        groups[row["mapping_group"]].append(row)
    for group, rows in groups.items():
        old_count = len({row["predecessor_node"] for row in rows})
        successor_count = len({row["successor_execution_key"] for row in rows})
        declared_old = {row["declared_old_count"] for row in rows}
        declared_successor = {row["declared_successor_count"] for row in rows}
        relations = {row["relation"] for row in rows}
        if len(declared_old) != 1 or len(declared_successor) != 1 or len(relations) != 1:
            fail("E_MIGRATION_GROUP", f"inconsistent group metadata: {group}")
        if old_count != int(next(iter(declared_old))) or successor_count != int(next(iter(declared_successor))):
            fail(
                "E_MIGRATION_CARDINALITY",
                f"group={group} observed={old_count}:{successor_count} "
                f"declared={next(iter(declared_old))}:{next(iter(declared_successor))}",
            )


def validate_hashes(directory: Path) -> None:
    manifest = directory / HASH_FILE
    if not manifest.is_file():
        fail("E_HASH_MANIFEST", f"missing {manifest}")
    observed_names: list[str] = []
    for line in manifest.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if not match:
            fail("E_HASH_MANIFEST", f"invalid hash line: {line}")
        expected, name = match.groups()
        observed_names.append(name)
        path = directory / name
        if not path.is_file() or sha256_file(path) != expected:
            fail("E_STALE_HASH", f"hash mismatch: {name}")
    expected_names = hash_input_names(directory)
    if observed_names != expected_names:
        fail(
            "E_HASH_SET",
            f"hash input set/order differs expected={expected_names} actual={observed_names}",
        )


def command_validate(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    directory = args.directory.resolve()
    sources = read_tsv(directory / SOURCE_FILE, SOURCE_HEADER)
    discoveries = read_tsv(directory / DISCOVERY_FILE, DISCOVERY_HEADER)
    classpaths = read_tsv(directory / CLASSPATH_FILE, CLASSPATH_HEADER)
    executions = read_tsv(directory / EXECUTION_FILE, EXECUTION_HEADER)
    rename_rows = read_tsv(directory / RENAME_FILE, RENAME_HEADER)
    nodes = read_tsv(directory / PREDECESSOR_NODE_FILE, PREDECESSOR_NODE_HEADER)
    mappings = read_tsv(directory / MIGRATION_FILE, MIGRATION_HEADER)
    read_tsv(directory / DB_FILE, DB_HEADER)
    read_tsv(directory / PACKAGE_FILE, PACKAGE_HEADER)
    maven_variants = read_tsv(directory / MAVEN_VARIANT_FILE, MAVEN_VARIANT_HEADER)
    if not (directory / NEGATIVE_FILE).is_file():
        fail("E_NEGATIVE_LEDGER", f"missing {directory / NEGATIVE_FILE}")
    negative_rows = read_tsv(
        directory / NEGATIVE_FILE,
        ["probe", "expected_error", "actual_error", "status"],
    )
    json.loads((directory / COVERAGE_FILE).read_text(encoding="utf-8"))
    freeze = json.loads((directory / FREEZE_FILE).read_text(encoding="utf-8"))
    overrides = load_overrides(directory / "inventory-overrides.json")
    validate_rows(root, sources, discoveries, executions)
    classpath_keys = [(row["module"], row["ordinal"]) for row in classpaths]
    if len(classpath_keys) != len(set(classpath_keys)) or not classpaths:
        fail("E_CLASSPATH_LEDGER", "classpath ledger is empty or has duplicate module ordinals")
    if any(not re.fullmatch(r"[0-9a-f]{64}", row["entry_sha256"]) for row in classpaths):
        fail("E_CLASSPATH_LEDGER", "classpath ledger contains an invalid SHA-256")
    for row in classpaths:
        identity = row["entry_identity"]
        if identity.startswith("m2:"):
            relative = Path(identity.removeprefix("m2:"))
            base = Path.home() / ".m2/repository"
        elif identity.startswith("repo:"):
            relative = Path(identity.removeprefix("repo:"))
            base = root
        else:
            fail("E_CLASSPATH_IDENTITY", f"unknown classpath identity: {identity}")
        if relative.is_absolute() or ".." in relative.parts:
            fail("E_CLASSPATH_IDENTITY", f"unsafe classpath identity: {identity}")
        entry_path = base / relative
        if classpath_entry_sha256(root, entry_path) != row["entry_sha256"]:
            fail("E_CLASSPATH_HASH", f"classpath entry hash differs: {identity}")
    discovery_modules = {row["module"] for row in discoveries}
    classpath_modules = {row["module"] for row in classpaths}
    if classpath_modules != discovery_modules:
        fail(
            "E_CLASSPATH_MODULE_SET",
            f"classpath/discovery module diff missing={discovery_modules - classpath_modules} "
            f"orphan={classpath_modules - discovery_modules}",
        )
    for module in sorted(classpath_modules):
        module_rows = [row for row in classpaths if row["module"] == module]
        ordinals = [int(row["ordinal"]) for row in module_rows]
        if ordinals != list(range(1, len(module_rows) + 1)):
            fail("E_CLASSPATH_LEDGER", f"non-contiguous classpath ordinals: {module}")
        identities = [row["entry_identity"] for row in module_rows]
        if len(identities) != len(set(identities)):
            fail("E_CLASSPATH_LEDGER", f"duplicate classpath identities: {module}")
    maven_variant_keys = [
        (row["module"], row["profile"], row["plugin"], row["execution_id"])
        for row in maven_variants
    ]
    if not maven_variants or len(maven_variant_keys) != len(set(maven_variant_keys)):
        fail("E_MAVEN_VARIANT", "Maven variant ledger is empty or has duplicate owner keys")
    if any(not all(row[column] for column in MAVEN_VARIANT_HEADER) for row in maven_variants):
        fail("E_MAVEN_VARIANT", "Maven variant ledger contains empty contract fields")
    reactor_m2_prefixes = {
        "m2:" + coordinate.relative_to(Path.home() / ".m2/repository").as_posix() + "/"
        for coordinate in reactor_artifacts(root)
    }
    stale_reactor_entries = [
        row["entry_identity"]
        for row in classpaths
        if any(row["entry_identity"].startswith(prefix) for prefix in reactor_m2_prefixes)
    ]
    if stale_reactor_entries:
        fail("E_REACTOR_CLASSPATH", f"reactor dependencies resolved from m2: {stale_reactor_entries[:5]}")
    validate_migration(executions, nodes, mappings)
    rename_counts = validate_rename_plan(
        sources,
        executions,
        rename_rows,
        mappings,
        overrides["rename_contract"],
        str(overrides["reviewer"]),
    )
    validate_freeze(
        root,
        freeze,
        sources,
        discoveries,
        classpaths,
        executions,
        nodes,
        mappings,
        rename_counts,
        sha256_file(directory / RENAME_FILE),
        overrides["rename_contract"],
    )
    if not getattr(args, "skip_negative", False):
        if [row["probe"] for row in negative_rows] != NEGATIVE_PROBE_NAMES:
            fail("E_NEGATIVE_LEDGER", "negative probe set or order differs from the frozen contract")
        if any(
            row["status"] != "passed" or row["actual_error"] != row["expected_error"]
            for row in negative_rows
        ):
            fail("E_NEGATIVE_LEDGER", "negative probe result is not exact passed")
    if not args.skip_hashes:
        validate_hashes(directory)
    print(
        f"[v934-inventory] PASS sources={len(sources)} discoveries={len(discoveries)} "
        f"executions={len(executions)} predecessors={len(nodes)}"
    )


def validate_freeze(
    root: Path,
    freeze: dict[str, Any],
    sources: list[dict[str, str]],
    discoveries: list[dict[str, str]],
    classpaths: list[dict[str, str]],
    executions: list[dict[str, str]],
    nodes: list[dict[str, str]],
    mappings: list[dict[str, str]],
    rename_counts: dict[str, int],
    rename_plan_sha256: str,
    rename_contract: dict[str, Any],
) -> None:
    if freeze.get("schema_version") != 1 or freeze.get("version") != "9.3.4" or freeze.get("step") != 1:
        fail("E_FREEZE_SCHEMA", "freeze schema/version/step mismatch")
    if freeze.get("generation") != "step1-pre-rename":
        fail("E_FREEZE_SCHEMA", "freeze generation is not the immutable Step1 pre-rename baseline")
    status = freeze.get("status")
    decision = freeze.get("decision")
    if status == "candidate":
        if decision != "pending-independent-review":
            fail("E_FREEZE_STATUS", "candidate freeze must be pending independent review")
    elif status == "confirmed":
        if decision != "passed" or not freeze.get("reviewed_at"):
            fail("E_FREEZE_STATUS", "confirmed freeze requires passed decision and reviewed_at")
    else:
        fail("E_FREEZE_STATUS", f"unsupported freeze status: {status}")
    if not freeze.get("reviewer"):
        fail("E_FREEZE_STATUS", "freeze reviewer is empty")
    reactor_modules = active_reactor_modules(root)
    reactor = freeze.get("reactor", {})
    if reactor.get("modules") != reactor_modules or reactor.get("module_count") != len(reactor_modules):
        fail("E_FREEZE_REACTOR", "freeze reactor graph differs from active root reactor")
    current_sources = candidate_sources(root, reactor_modules)
    expected_counts = {
        "workspace_sources": len(sources),
        "reactor_sources": sum(row["reactor_member"] == "true" for row in sources),
        "discovery_modules": len({row["module"] for row in discoveries}),
        "classpath_entries": len(classpaths),
        "nested_sources": sum(bool(row["contains_nested"]) for row in current_sources),
        "discovery_reports": sum(row["report_fqcn"] != "none" for row in discoveries),
        "execution_keys": len(executions),
        "required_step2": sum(
            row["required"] == "true" and row["execution_step"] == "2" for row in executions
        ),
        "required_step3": sum(
            row["required"] == "true" and row["execution_step"] == "3" for row in executions
        ),
        "predecessor_nodes": len(nodes),
        "migration_edges": len(mappings),
        **rename_counts,
    }
    if freeze.get("counts") != expected_counts:
        fail(
            "E_FREEZE_COUNTS",
            f"freeze counts differ expected={expected_counts} actual={freeze.get('counts')}",
        )
    current_source_hash = tree_hash(root, protected_source_paths(root))
    if freeze.get("baseline", {}).get("protected_source_sha256") != current_source_hash:
        fail("E_FREEZE_SOURCE", "freeze protected source fingerprint differs from workspace")
    successor_policy = freeze.get("successor_policy", {})
    if (
        successor_policy.get("rename_plan") != RENAME_FILE
        or successor_policy.get("rename_plan_sha256") != rename_plan_sha256
        or successor_policy.get("current_suffix") != rename_contract["current_suffix"]
        or successor_policy.get("target_suffix") != rename_contract["target_suffix"]
        or successor_policy.get("successor_output") != rename_contract["successor_output"]
        or successor_policy.get("required_parent_link")
        != "confirmed Step1 summary contract_manifest_sha256 plus rename_plan_sha256"
        or successor_policy.get("refresh_rule") != rename_contract["policy"]
    ):
        fail("E_FREEZE_SUCCESSOR", "Step2 chained successor policy differs")
    toolchain = freeze.get("toolchain", {})
    expected_tool_hashes = {
        "wrapper_path": "scripts/verify-v934-test-inventory.sh",
        "wrapper_sha256": sha256_file(root / "scripts/verify-v934-test-inventory.sh"),
        "inventory_tool_sha256": sha256_file(root / "scripts/v934/inventory_tool.py"),
        "inventory_overrides_sha256": sha256_file(root / "scripts/v934/inventory-overrides.json"),
        "discovery_source_sha256": sha256_file(root / "scripts/v934/JUnitDiscoveryInventory.java"),
        "java_version": version_line(root, "java", "-version"),
        "javac_version": version_line(root, "javac", "-version"),
        "maven_version": version_line(root, "mvn", "-version"),
    }
    for field, expected in expected_tool_hashes.items():
        if toolchain.get(field) != expected:
            fail("E_FREEZE_TOOLCHAIN", f"toolchain field differs: {field}")
    expected_class_files = [
        "JUnitDiscoveryInventory$ReportDiscovery.class",
        "JUnitDiscoveryInventory.class",
    ]
    if toolchain.get("generator_class_files") != expected_class_files:
        fail("E_FREEZE_TOOLCHAIN", "compiled discovery helper class set differs")
    if not re.fullmatch(r"[0-9a-f]{64}", str(toolchain.get("generator_classes_sha256", ""))):
        fail("E_FREEZE_TOOLCHAIN", "compiled discovery helper tree hash is invalid")
    mapping_reviewers = {row["reviewer"] for row in mappings}
    if len(mapping_reviewers) != 1:
        fail("E_PREDECESSOR_AUTHORITY", "predecessor mapping reviewer set is not exact")
    authority_run_id = freeze.get("discovery", {}).get("sealed_xml_corroboration")
    authority_root = root / "target/v933-batch7-regression/runs" / str(authority_run_id)
    expected_nodes, expected_mappings = build_predecessor_rows(
        authority_root,
        executions,
        next(iter(mapping_reviewers)),
    )
    if nodes != expected_nodes or mappings != expected_mappings:
        fail("E_PREDECESSOR_AUTHORITY", "predecessor raw XML/node/mapping exact set differs")


def mutate_tsv(path: Path, mutation: Any) -> None:
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        header = reader.fieldnames or []
        rows = [dict(row) for row in reader]
    mutation(rows)
    write_tsv(path, header, rows)


def validate_directory(
    root: Path,
    directory: Path,
    check_hashes: bool,
    skip_negative: bool = False,
) -> None:
    namespace = argparse.Namespace(
        root=root,
        directory=directory,
        skip_hashes=not check_hashes,
        skip_negative=skip_negative,
    )
    command_validate(namespace)


def command_negative(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    source_dir = args.directory.resolve()
    probes: list[tuple[str, str, Any, bool]] = []

    probes.append(("orphan-source", "E_SOURCE_SET", lambda d: mutate_tsv(d / SOURCE_FILE, lambda r: r.pop()), False))
    probes.append(
        (
            "missing-source-owner",
            "E_SOURCE_OWNER",
            lambda d: mutate_tsv(d / SOURCE_FILE, lambda r: r[0].update(owner="")),
            False,
        )
    )
    probes.append(
        (
            "missing-source-reason",
            "E_SOURCE_REASON",
            lambda d: mutate_tsv(d / SOURCE_FILE, lambda r: r[0].update(reason="")),
            False,
        )
    )
    probes.append(
        (
            "nonreactor-disposition",
            "E_SOURCE_DISPOSITION",
            lambda d: mutate_tsv(
                d / SOURCE_FILE,
                lambda r: next(row for row in r if row["reactor_member"] == "false").update(disposition=""),
            ),
            False,
        )
    )
    probes.append(
        (
            "zero-execution-owner",
            "E_EXEC_ZERO",
            lambda d: _remove_first_source_executions(d),
            False,
        )
    )
    probes.append(("duplicate-execution-key", "E_EXEC_KEY_DUP", lambda d: mutate_tsv(d / EXECUTION_FILE, lambda r: r.append(dict(r[0]))), False))
    probes.append(("non-executable-owner", "E_NONEXEC_EXEC", lambda d: _assign_execution_to_helper(d), False))
    probes.append(("runner-overlap", "E_RUNNER_OVERLAP", lambda d: _add_runner_overlap(d), False))
    probes.append(("sqlite-lane-overlap", "E_SQLITE_OVERLAP", lambda d: _add_sqlite_overlap(d), False))
    probes.append(("missing-report-owner", "E_REPORT_MISSING", lambda d: _remove_one_report_owner(d), False))
    probes.append(("unexpected-report-owner", "E_REPORT_UNEXPECTED", lambda d: _add_unexpected_report(d), False))
    probes.append(("invalid-step", "E_EXEC_STEP", lambda d: mutate_tsv(d / EXECUTION_FILE, lambda r: r[0].update(execution_step="4")), False))
    probes.append(("optional-metadata", "E_OPTIONAL_METADATA", lambda d: _erase_optional_reason(d), False))
    probes.append(("unknown-successor", "E_MIGRATION_SUCCESSOR", lambda d: mutate_tsv(d / MIGRATION_FILE, lambda r: r[0].update(successor_execution_key="unknown")), False))
    probes.append(("migration-cardinality", "E_MIGRATION_CARDINALITY", lambda d: mutate_tsv(d / MIGRATION_FILE, lambda r: r[0].update(declared_successor_count="2")), False))
    probes.append(("duplicate-migration-edge", "E_MIGRATION_EDGE_DUP", lambda d: mutate_tsv(d / MIGRATION_FILE, lambda r: r.append(dict(r[0]))), False))
    probes.append(("unmapped-predecessor", "E_MIGRATION_UNMAPPED", lambda d: mutate_tsv(d / MIGRATION_FILE, lambda r: r.pop()), False))
    probes.append(("invalid-classpath-hash", "E_CLASSPATH_HASH", lambda d: mutate_tsv(d / CLASSPATH_FILE, lambda r: r[0].update(entry_sha256="0" * 64)), False))
    probes.append(("classpath-module-gap", "E_CLASSPATH_MODULE_SET", lambda d: _remove_classpath_module(d), False))
    probes.append(("duplicate-maven-variant", "E_MAVEN_VARIANT", lambda d: mutate_tsv(d / MAVEN_VARIANT_FILE, lambda r: r.append(dict(r[0]))), False))
    probes.append(("orphan-discovery-row", "E_DISCOVERY_ORPHAN", lambda d: mutate_tsv(d / DISCOVERY_FILE, lambda r: r[0].update(source_id="unknown")), False))
    probes.append(("missing-discovery-row", "E_DISCOVERY_MISSING", lambda d: _remove_zero_report_discovery(d), False))
    probes.append(("tampered-discovery-hash", "E_DISCOVERY_HASH", lambda d: mutate_tsv(d / DISCOVERY_FILE, lambda r: r[0].update(source_sha256="0" * 64)), False))
    probes.append(("tampered-rename-successor", "E_RENAME_PLAN", lambda d: mutate_tsv(d / RENAME_FILE, lambda r: r[0].update(target_report_fqcn=r[0]["target_report_fqcn"] + "$Tampered")), False))
    probes.append(("tampered-successor-policy", "E_FREEZE_SUCCESSOR", lambda d: _tamper_successor_policy(d), False))
    probes.append(("freeze-count-tamper", "E_FREEZE_COUNTS", lambda d: _tamper_freeze_count(d), False))
    probes.append(("missing-hash-entry", "E_HASH_SET", lambda d: _remove_hash_entry(d), True))
    probes.append(("stale-manifest", "E_STALE_HASH", lambda d: (d / COVERAGE_FILE).write_text("{}\n", encoding="utf-8"), True))

    results: list[dict[str, str]] = []
    for name, expected_code, mutation, check_hashes in probes:
        with tempfile.TemporaryDirectory(prefix="v934-negative-") as temporary:
            candidate = Path(temporary) / "contract"
            shutil.copytree(source_dir, candidate)
            mutation(candidate)
            actual_code = "PASS"
            try:
                validate_directory(root, candidate, check_hashes, skip_negative=True)
            except InventoryError as error:
                actual_code = error.code
            status = "passed" if actual_code == expected_code else "failed"
            results.append(
                {
                    "probe": name,
                    "expected_error": expected_code,
                    "actual_error": actual_code,
                    "status": status,
                }
            )
            if status != "passed":
                fail(
                    "E_NEGATIVE_PROBE",
                    f"probe={name} expected={expected_code} actual={actual_code}",
                )
    write_tsv(source_dir / NEGATIVE_FILE, ["probe", "expected_error", "actual_error", "status"], results)
    write_hash_manifest(source_dir)
    print(f"[v934-inventory] negative probes PASS count={len(results)}")


def command_confirm(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    directory = args.directory.resolve()
    summary_path = args.summary.resolve()
    evidence_path = root / args.evidence
    if not evidence_path.is_file():
        fail("E_FREEZE_CONFIRM", f"independent review evidence is missing: {evidence_path}")
    validate_directory(root, directory, True)
    path = directory / FREEZE_FILE
    freeze = json.loads(path.read_text(encoding="utf-8"))
    if freeze.get("status") != "candidate" or freeze.get("decision") != "pending-independent-review":
        fail("E_FREEZE_CONFIRM", "only a pending candidate freeze can be confirmed")
    summary = read_env_summary(summary_path)
    if summary.get("evidence_status") != "candidate" or summary.get("status") != "passed":
        fail("E_FREEZE_CONFIRM", "run summary is not a passed candidate")
    if summary.get("contract_freeze_sha256") != sha256_file(path):
        fail("E_FREEZE_CONFIRM", "candidate summary freeze digest is stale")
    if summary.get("contract_manifest_sha256") != sha256_file(directory / HASH_FILE):
        fail("E_FREEZE_CONFIRM", "candidate summary manifest digest is stale")
    validate_candidate_summary(root, directory, summary_path)
    original_freeze = path.read_bytes()
    original_manifest = (directory / HASH_FILE).read_bytes()
    original_summary = summary_path.read_bytes()
    reviewed_at = dt.datetime.now(dt.timezone.utc).isoformat()
    try:
        freeze["status"] = "confirmed"
        freeze["decision"] = "passed"
        freeze["reviewer"] = args.reviewer
        freeze["reviewed_at"] = reviewed_at
        freeze["independent_review_evidence"] = args.evidence
        path.write_text(
            json.dumps(freeze, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        write_hash_manifest(directory)
        validate_directory(root, directory, True)
        updates = {
            "contract_freeze_sha256": sha256_file(path),
            "contract_manifest_sha256": sha256_file(directory / HASH_FILE),
            "evidence_status": "confirmed",
            "decision": "passed",
            "reviewer": args.reviewer,
            "reviewed_at": reviewed_at,
            "independent_review_evidence": args.evidence,
        }
        write_env_summary(summary_path, summary, updates)
        validate_confirmed_summary(root, directory, summary_path)
    except BaseException:
        path.write_bytes(original_freeze)
        (directory / HASH_FILE).write_bytes(original_manifest)
        summary_path.write_bytes(original_summary)
        raise
    print(
        f"[v934-inventory] freeze confirmed reviewer={args.reviewer} evidence={args.evidence} "
        f"summary_sha256={sha256_file(summary_path)}"
    )


def read_env_summary(path: Path) -> dict[str, str]:
    if not path.is_file():
        fail("E_SUMMARY", f"summary is missing: {path}")
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" not in line:
            fail("E_SUMMARY", f"invalid summary line: {line}")
        key, value = line.split("=", 1)
        if not key or key in result:
            fail("E_SUMMARY", f"duplicate/empty summary key: {key}")
        result[key] = value
    return result


def write_env_summary(path: Path, current: dict[str, str], updates: dict[str, str]) -> None:
    merged = dict(current)
    merged.update(updates)
    ordered_keys = list(current) + [key for key in updates if key not in current]
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(
        "".join(f"{key}={merged[key]}\n" for key in ordered_keys),
        encoding="utf-8",
    )
    temporary.replace(path)


def validate_confirmed_summary(root: Path, directory: Path, summary_path: Path) -> None:
    summary = read_env_summary(summary_path)
    freeze = json.loads((directory / FREEZE_FILE).read_text(encoding="utf-8"))
    toolchain = freeze["toolchain"]
    expected = {
        "run_id": summary_path.parent.name,
        "git_head": freeze["baseline"]["git_head"],
        "source_before": freeze["baseline"]["protected_source_sha256"],
        "source_after": freeze["baseline"]["protected_source_sha256"],
        "discovery_modules": str(freeze["counts"]["discovery_modules"]),
        "classpath_entries": str(freeze["counts"]["classpath_entries"]),
        "rename_sources": str(freeze["counts"]["rename_sources"]),
        "rename_reports": str(freeze["counts"]["rename_reports"]),
        "rename_execution_keys": str(freeze["counts"]["rename_execution_keys"]),
        "rename_predecessor_edges": str(freeze["counts"]["rename_predecessor_edges"]),
        "rename_plan_sha256": freeze["successor_policy"]["rename_plan_sha256"],
        "launcher_sha256": freeze["discovery"]["launcher_sha256"],
        "wrapper_source_sha256": toolchain["wrapper_sha256"],
        "tool_source_sha256": toolchain["inventory_tool_sha256"],
        "discovery_source_sha256": toolchain["discovery_source_sha256"],
        "generator_class_files": ",".join(toolchain["generator_class_files"]),
        "generator_classes_sha256": toolchain["generator_classes_sha256"],
        "java_version": toolchain["java_version"],
        "javac_version": toolchain["javac_version"],
        "maven_version": toolchain["maven_version"],
        "contract_freeze_sha256": sha256_file(directory / FREEZE_FILE),
        "contract_manifest_sha256": sha256_file(directory / HASH_FILE),
        "evidence_status": "confirmed",
        "status": "passed",
        "decision": "passed",
        "reviewer": freeze["reviewer"],
        "reviewed_at": freeze["reviewed_at"],
        "independent_review_evidence": freeze["independent_review_evidence"],
    }
    for key, value in expected.items():
        if summary.get(key) != value:
            fail("E_SUMMARY", f"confirmed summary differs: {key}")
    if summary.get("source_before") != tree_hash(root, protected_source_paths(root)):
        fail("E_SUMMARY", "confirmed summary protected source fingerprint differs")


def validate_candidate_summary(root: Path, directory: Path, summary_path: Path) -> None:
    summary = read_env_summary(summary_path)
    freeze = json.loads((directory / FREEZE_FILE).read_text(encoding="utf-8"))
    toolchain = freeze["toolchain"]
    expected = {
        "run_id": summary_path.parent.name,
        "git_head": freeze["baseline"]["git_head"],
        "source_before": freeze["baseline"]["protected_source_sha256"],
        "source_after": freeze["baseline"]["protected_source_sha256"],
        "discovery_modules": str(freeze["counts"]["discovery_modules"]),
        "classpath_entries": str(freeze["counts"]["classpath_entries"]),
        "rename_sources": str(freeze["counts"]["rename_sources"]),
        "rename_reports": str(freeze["counts"]["rename_reports"]),
        "rename_execution_keys": str(freeze["counts"]["rename_execution_keys"]),
        "rename_predecessor_edges": str(freeze["counts"]["rename_predecessor_edges"]),
        "rename_plan_sha256": freeze["successor_policy"]["rename_plan_sha256"],
        "launcher_sha256": freeze["discovery"]["launcher_sha256"],
        "wrapper_source_sha256": toolchain["wrapper_sha256"],
        "tool_source_sha256": toolchain["inventory_tool_sha256"],
        "discovery_source_sha256": toolchain["discovery_source_sha256"],
        "generator_class_files": ",".join(toolchain["generator_class_files"]),
        "generator_classes_sha256": toolchain["generator_classes_sha256"],
        "java_version": toolchain["java_version"],
        "javac_version": toolchain["javac_version"],
        "maven_version": toolchain["maven_version"],
        "contract_freeze_sha256": sha256_file(directory / FREEZE_FILE),
        "contract_manifest_sha256": sha256_file(directory / HASH_FILE),
        "evidence_status": "candidate",
        "status": "passed",
    }
    for key, value in expected.items():
        if summary.get(key) != value:
            fail("E_SUMMARY", f"candidate summary differs: {key}")
    if summary.get("source_before") != tree_hash(root, protected_source_paths(root)):
        fail("E_SUMMARY", "candidate summary protected source fingerprint differs")


def command_validate_summary(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    directory = args.directory.resolve()
    validate_directory(root, directory, True)
    validate_confirmed_summary(root, directory, args.summary.resolve())
    print(f"[v934-inventory] confirmed summary PASS path={args.summary.resolve()}")


def _remove_first_source_executions(directory: Path) -> None:
    rows = read_tsv(directory / EXECUTION_FILE, EXECUTION_HEADER)
    victim = rows[0]["source_id"]
    write_tsv(directory / EXECUTION_FILE, EXECUTION_HEADER, [row for row in rows if row["source_id"] != victim])


def _erase_optional_reason(directory: Path) -> None:
    def mutation(rows: list[dict[str, str]]) -> None:
        optional = next((row for row in rows if row["required"] == "false"), None)
        if optional is None:
            fail("E_NEGATIVE_FIXTURE", "no optional row available for negative probe")
        optional["optional_reason"] = ""

    mutate_tsv(directory / EXECUTION_FILE, mutation)


def _assign_execution_to_helper(directory: Path) -> None:
    sources = read_tsv(directory / SOURCE_FILE, SOURCE_HEADER)
    helper = next(row for row in sources if row["kind"] != "executable")
    mutate_tsv(
        directory / EXECUTION_FILE,
        lambda rows: rows[0].update(source_id=helper["source_id"]),
    )


def _add_runner_overlap(directory: Path) -> None:
    def mutation(rows: list[dict[str, str]]) -> None:
        duplicate = dict(rows[0])
        duplicate["runner"] = "failsafe" if duplicate["runner"] == "surefire" else "surefire"
        duplicate["execution_key"] = execution_key(
            duplicate["runner"],
            duplicate["lane"],
            duplicate["variant_key"],
            duplicate["report_fqcn"],
        )
        rows.append(duplicate)

    mutate_tsv(directory / EXECUTION_FILE, mutation)


def _add_sqlite_overlap(directory: Path) -> None:
    def mutation(rows: list[dict[str, str]]) -> None:
        original = next(
            row
            for row in rows
            if row["lane"] == "sqlite-broad-integration" and row["db_kind"] == "sqlite"
        )
        duplicate = dict(original)
        duplicate["lane"] = "database-contract-matrix"
        duplicate["variant_key"] = "negative-sqlite-overlap"
        duplicate["execution_key"] = execution_key(
            duplicate["runner"],
            duplicate["lane"],
            duplicate["variant_key"],
            duplicate["report_fqcn"],
        )
        rows.append(duplicate)

    mutate_tsv(directory / EXECUTION_FILE, mutation)


def _remove_one_report_owner(directory: Path) -> None:
    rows = read_tsv(directory / EXECUTION_FILE, EXECUTION_HEADER)
    reports_by_source: dict[str, set[str]] = defaultdict(set)
    for row in rows:
        reports_by_source[row["source_id"]].add(row["report_fqcn"])
    source_id = next(source for source, reports in reports_by_source.items() if len(reports) > 1)
    victim_report = sorted(reports_by_source[source_id])[0]
    write_tsv(
        directory / EXECUTION_FILE,
        EXECUTION_HEADER,
        [
            row
            for row in rows
            if not (row["source_id"] == source_id and row["report_fqcn"] == victim_report)
        ],
    )


def _add_unexpected_report(directory: Path) -> None:
    def mutation(rows: list[dict[str, str]]) -> None:
        duplicate = dict(rows[0])
        duplicate["report_fqcn"] += "$NegativeInventoryProbe"
        duplicate["execution_key"] = execution_key(
            duplicate["runner"],
            duplicate["lane"],
            duplicate["variant_key"],
            duplicate["report_fqcn"],
        )
        rows.append(duplicate)

    mutate_tsv(directory / EXECUTION_FILE, mutation)


def _tamper_freeze_count(directory: Path) -> None:
    path = directory / FREEZE_FILE
    freeze = json.loads(path.read_text(encoding="utf-8"))
    freeze["counts"]["execution_keys"] += 1
    path.write_text(
        json.dumps(freeze, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _tamper_successor_policy(directory: Path) -> None:
    path = directory / FREEZE_FILE
    freeze = json.loads(path.read_text(encoding="utf-8"))
    freeze["successor_policy"]["refresh_rule"] = "tampered"
    path.write_text(
        json.dumps(freeze, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def _remove_classpath_module(directory: Path) -> None:
    rows = read_tsv(directory / CLASSPATH_FILE, CLASSPATH_HEADER)
    victim = rows[0]["module"]
    write_tsv(
        directory / CLASSPATH_FILE,
        CLASSPATH_HEADER,
        [row for row in rows if row["module"] != victim],
    )


def _remove_zero_report_discovery(directory: Path) -> None:
    rows = read_tsv(directory / DISCOVERY_FILE, DISCOVERY_HEADER)
    victim = next(row for row in rows if row["report_fqcn"] == "none")
    write_tsv(
        directory / DISCOVERY_FILE,
        DISCOVERY_HEADER,
        [row for row in rows if row is not victim],
    )


def _remove_hash_entry(directory: Path) -> None:
    path = directory / HASH_FILE
    lines = path.read_text(encoding="utf-8").splitlines()
    path.write_text("\n".join(lines[:-1]) + "\n", encoding="utf-8")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    source_hash = subparsers.add_parser("source-hash")
    source_hash.add_argument("--root", type=Path, required=True)
    source_hash.set_defaults(handler=command_source_hash)
    classpath = subparsers.add_parser("classpath")
    classpath.add_argument("--root", type=Path, required=True)
    classpath.add_argument("--module", required=True)
    classpath.add_argument("--input", type=Path, required=True)
    classpath.add_argument("--output", type=Path, required=True)
    classpath.set_defaults(handler=command_classpath)
    scan = subparsers.add_parser("scan")
    scan.add_argument("--root", type=Path, required=True)
    scan.add_argument("--run-dir", type=Path, required=True)
    scan.set_defaults(handler=command_scan)
    generate = subparsers.add_parser("generate")
    generate.add_argument("--root", type=Path, required=True)
    generate.add_argument("--run-dir", type=Path, required=True)
    generate.add_argument("--output-dir", type=Path, required=True)
    generate.add_argument("--overrides", type=Path, required=True)
    generate.add_argument("--authority-root", type=Path, required=True)
    generate.set_defaults(handler=command_generate)
    validate = subparsers.add_parser("validate")
    validate.add_argument("--root", type=Path, required=True)
    validate.add_argument("--directory", type=Path, required=True)
    validate.add_argument("--skip-hashes", action="store_true")
    validate.set_defaults(handler=command_validate)
    negative = subparsers.add_parser("negative")
    negative.add_argument("--root", type=Path, required=True)
    negative.add_argument("--directory", type=Path, required=True)
    negative.set_defaults(handler=command_negative)
    confirm = subparsers.add_parser("confirm")
    confirm.add_argument("--root", type=Path, required=True)
    confirm.add_argument("--directory", type=Path, required=True)
    confirm.add_argument("--reviewer", required=True)
    confirm.add_argument("--evidence", required=True)
    confirm.add_argument("--summary", type=Path, required=True)
    confirm.set_defaults(handler=command_confirm)
    validate_summary = subparsers.add_parser("validate-summary")
    validate_summary.add_argument("--root", type=Path, required=True)
    validate_summary.add_argument("--directory", type=Path, required=True)
    validate_summary.add_argument("--summary", type=Path, required=True)
    validate_summary.set_defaults(handler=command_validate_summary)
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        args.handler(args)
        return 0
    except InventoryError as error:
        print(f"[v934-inventory] ERROR {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
