#!/usr/bin/env python3
"""Fail-closed report collector for the 9.3.4 Step 3 database matrix.

The tool deliberately does not start databases or Maven.  A runner creates one
outer marker, creates a fresh variant marker immediately before each Maven
invocation, and calls ``collect`` immediately after that invocation.  ``finalize``
then proves that exactly seven same-run manifests and five passed runtime cell
evidence trees cover 29 reports and 370 testcase nodes with
failures/errors/skips all zero.  It publishes complete copied variant and cell
subtrees; ``verify-final`` revalidates their exact paths, hashes, mtimes and
schemas without trusting the original evidence trees.

Outer marker JSON fields (no additional fields are accepted)::

    schema_version, kind, run_id, lane, runner, git_head,
    contract_sha256, source_amendment_sha256, started_at, status

Variant markers add ``variant_key``, ``db_kind`` and
``outer_marker_sha256`` to the same provenance tuple.  Their ``kind`` values
are frozen in database-matrix-contract.json and ``status`` must be ``started``.
Every marker must be a regular non-symlink file.  Every source XML mtime must be
strictly newer than its owning variant marker.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Sequence


PREFIX = "[v934-step3-db-report]"
SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_REPO = SCRIPT_DIR.parents[2]
DEFAULT_CONTRACT = SCRIPT_DIR / "database-matrix-contract.json"
MANIFEST_NAME = "report-manifest.json"
RAW_DIRECTORY = "raw-reports"
FINAL_VARIANTS_DIRECTORY = "variants"
FINAL_CELLS_DIRECTORY = "cells"
CANONICAL_FIXTURE = "\n".join(
    (
        "sentinel|contract_version|9.3.4",
        "parity|V934_PARITY_SENTINEL|1",
        "parity|V934_PARITY_SENTINEL|2",
        "preagg|V934_ALPHA|50.0000",
        "preagg|V934_BETA|40.0000",
        "preagg|V934_GAMMA|10.0000",
    )
) + "\n"
SQLITE_JAR_SHA256 = "53174d76087bb73cc29db9c02766fb921fd7fc652f7952f3609e0018e3dd5ded"

EXACT_DB_KINDS = {
    "sqlite",
    "mysql57",
    "mysql8",
    "postgres15",
    "sqlserver2022",
}
EXACT_DB_ORDER = (
    "sqlite",
    "mysql57",
    "mysql8",
    "postgres15",
    "sqlserver2022",
)
EXACT_VARIANTS = {
    "db-sqlite": "sqlite",
    "db-mysql57": "mysql57",
    "db-mysql8": "mysql8",
    "mysql8-targeted": "mysql8",
    "db-postgres15": "postgres15",
    "postgres15-targeted": "postgres15",
    "db-sqlserver2022": "sqlserver2022",
}
EXACT_VARIANT_ORDER = (
    "db-sqlite",
    "db-mysql57",
    "db-mysql8",
    "mysql8-targeted",
    "db-postgres15",
    "postgres15-targeted",
    "db-sqlserver2022",
)
EXACT_TOTALS = {
    "database_cells": 5,
    "variants": 7,
    "reports": 29,
    "testcase_nodes": 370,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
}
EXACT_NEGATIVE_PROBES = [
    ("missing-report", "E_MISSING_REPORT"),
    ("extra-report", "E_EXTRA_REPORT"),
    ("duplicate-report-identity", "E_DUPLICATE_REPORT"),
    ("wrong-test-count", "E_REPORT_COUNT"),
    ("failure-outcome", "E_REPORT_OUTCOME"),
    ("error-outcome", "E_REPORT_OUTCOME"),
    ("skipped-outcome", "E_REPORT_OUTCOME"),
    ("stale-report", "E_STALE_REPORT"),
    ("duplicate-variant-manifest", "E_DUPLICATE_VARIANT"),
    ("missing-variant-manifest", "E_MISSING_VARIANT"),
    ("cross-run-marker-splice", "E_CROSS_RUN_SPLICE"),
    ("cross-run-manifest-splice", "E_CROSS_RUN_SPLICE"),
    ("manifest-report-tamper", "E_EVIDENCE_REPORT"),
    ("source-amendment-drift", "E_SOURCE_AMENDMENT"),
]
EXACT_AUTHORITY_FILES = [
    "foggy-bean-copy/pom.xml",
    "foggy-core/pom.xml",
    "foggy-dataset-demo/docker/docker-compose-v934-authority.yml",
    "foggy-dataset-demo/docker/docker-compose-v934.yml",
    "foggy-dataset-demo/docker/docker-compose.yml",
    "foggy-dataset-demo/docker/mysql/conf/my.cnf",
    "foggy-dataset-demo/docker/mysql/init/01-schema.sql",
    "foggy-dataset-demo/docker/mysql/init/02-dict-data.sql",
    "foggy-dataset-demo/docker/mysql/init/03-test-data.sql",
    "foggy-dataset-demo/docker/mysql/init/04-seed-2025-sales.sql",
    "foggy-dataset-demo/docker/mysql/init/06-odoo-schema.sql",
    "foggy-dataset-demo/docker/mysql/init/07-odoo-data.sql",
    "foggy-dataset-demo/docker/mysql/init/08-odoo-closure-schema.sql",
    "foggy-dataset-demo/docker/mysql/init/09-odoo-closure-data.sql",
    "foggy-dataset-demo/docker/mysql/init/10-preagg-schema.sql",
    "foggy-dataset-demo/docker/mysql/init/11-preagg-testdata.sql",
    "foggy-dataset-demo/docker/postgres/init/01-schema.sql",
    "foggy-dataset-demo/docker/postgres/init/02-dict-data.sql",
    "foggy-dataset-demo/docker/postgres/init/03-test-data.sql",
    "foggy-dataset-demo/docker/postgres/init/06-odoo-schema.sql",
    "foggy-dataset-demo/docker/postgres/init/07-odoo-data.sql",
    "foggy-dataset-demo/docker/postgres/init/08-odoo-closure-schema.sql",
    "foggy-dataset-demo/docker/postgres/init/09-odoo-closure-data.sql",
    "foggy-dataset-demo/docker/sqlserver/init/01-schema.sql",
    "foggy-dataset-demo/docker/sqlserver/init/02-dict-data.sql",
    "foggy-dataset-demo/docker/sqlserver/init/03-test-data.sql",
    "foggy-dataset-demo/docker/v934/mysql/12-v934-sentinel.sql",
    "foggy-dataset-demo/docker/v934/mysql/13-v934-parity-fixture.sql",
    "foggy-dataset-demo/docker/v934/mysql/14-v934-preagg-fixture.sql",
    "foggy-dataset-demo/docker/v934/postgres/12-v934-sentinel.sql",
    "foggy-dataset-demo/docker/v934/postgres/13-v934-parity-fixture.sql",
    "foggy-dataset-demo/docker/v934/postgres/14-v934-preagg-fixture.sql",
    "foggy-dataset-demo/docker/v934/sqlserver/12-v934-sentinel.sql",
    "foggy-dataset-demo/docker/v934/sqlserver/13-v934-parity-fixture.sql",
    "foggy-dataset-demo/docker/v934/sqlserver/14-v934-preagg-fixture.sql",
    "foggy-dataset-demo/pom.xml",
    "foggy-dataset-model/pom.xml",
    "foggy-dataset-model/src/test/resources/application-docker.yml",
    "foggy-dataset-model/src/test/resources/application-mysql8.yml",
    "foggy-dataset-model/src/test/resources/application-postgres.yml",
    "foggy-dataset-model/src/test/resources/application-sqlite.yml",
    "foggy-dataset-model/src/test/resources/application-sqlserver.yml",
    "foggy-dataset-model/src/test/resources/sqlite/01-schema.sql",
    "foggy-dataset-model/src/test/resources/sqlite/02-dict-data.sql",
    "foggy-dataset-model/src/test/resources/sqlite/03-test-data.sql",
    "foggy-dataset-model/src/test/resources/sqlite/04-preagg-schema.sql",
    "foggy-dataset-model/src/test/resources/sqlite/05-preagg-data.sql",
    "foggy-dataset-model/src/test/resources/sqlite/06-odoo-schema.sql",
    "foggy-dataset-model/src/test/resources/sqlite/07-odoo-data.sql",
    "foggy-dataset-model/src/test/resources/sqlite/08-odoo-closure-schema.sql",
    "foggy-dataset-model/src/test/resources/sqlite/09-odoo-closure-data.sql",
    "foggy-dataset-model/src/test/resources/sqlite/10-v934-sentinel.sql",
    "foggy-dataset-model/src/test/resources/sqlite/11-v934-parity-fixture.sql",
    "foggy-dataset-model/src/test/resources/sqlite/12-v934-preagg-schema.sql",
    "foggy-dataset-model/src/test/resources/sqlite/13-v934-preagg-data.sql",
    "foggy-dataset/pom.xml",
    "foggy-fsscript/pom.xml",
    "pom.xml",
    "scripts/v934/authority_runner_lib.sh",
    "scripts/v934/inventory_tool.py",
    "scripts/v934/step3/database-matrix-protected-trees.tsv",
    "scripts/v934/step3/database-matrix-source-amendment.tsv",
    "scripts/v934/step3/database_matrix_report_tool.py",
    "scripts/v934/step3/provision-database-cell.sh",
    "scripts/v934/step3/sqlite_cell_tool.py",
    "scripts/verify-v934-database-matrix.sh",
]
EXACT_PROTECTED_TREES = [
    "foggy-bean-copy/src/main",
    "foggy-core/src/main",
    "foggy-dataset-demo/src/main",
    "foggy-dataset-model/src/main",
    "foggy-dataset-model/src/test/java",
    "foggy-dataset-model/src/test/resources",
    "foggy-dataset/src/main",
    "foggy-fsscript/src/main",
]

DEFERRED_HEADER = [
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
    "disposition",
]
DATABASE_HEADER = [
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
AMENDMENT_HEADER = [
    "path",
    "source_id",
    "frozen_source_sha256",
    "amended_source_sha256",
    "discovered_test_nodes",
    "affected_variants",
    "workitem",
    "allowed_effect",
]
METRICS_HEADER = [
    "execution_key",
    "variant_key",
    "db_kind",
    "report_fqcn",
    "tests",
    "failures",
    "errors",
    "skipped",
    "testcase_nodes",
    "sha256",
]
PROTECTED_TREE_HEADER = ["path", "tree_sha256"]

ARTIFACT_FIELDS = {"path", "sha256", "mtime_ns", "size_bytes"}
SQLITE_CELL_FILES = {
    "cleanup.env",
    "fixture-after.txt",
    "fixture-before.txt",
    "fixture-first.txt",
    "resource.env",
    "verification.env",
}
EXTERNAL_CELL_FILES = {
    "cleanup.env",
    "database-identity.txt",
    "fixture-after.txt",
    "fixture-before.txt",
    "fixture-first.txt",
    "resource.env",
    "runtime.env",
    "status.env",
}
SQLITE_RESOURCE_FIELDS = (
    "database",
    "database_file",
    "jdbc_url",
    "sqlite_python_version",
    "sqlite_jdbc_jar_before_sha256",
    "fixture_before_sha256",
    "status",
)
SQLITE_VERIFICATION_FIELDS = (
    "database",
    "sqlite_jdbc_jar_after_sha256",
    "fixture_before_sha256",
    "fixture_after_sha256",
    "status",
)
SQLITE_CLEANUP_FIELDS = ("database", "status")
EXTERNAL_RESOURCE_FIELDS = (
    "run_id",
    "database",
    "service",
    "project",
    "container",
    "volume",
    "network",
    "host_port",
    "container_port",
    "profile",
    "expected_image_ref",
    "expected_image_id",
)
EXTERNAL_RUNTIME_FIELDS = (
    "database",
    "actual_image_id",
    "actual_image_ref",
    "actual_repo_digest",
    "actual_project",
    "actual_service",
    "actual_mapped_port",
    "volume_project",
    "network_project",
    "volume_created",
    "database_identity",
    "status",
)
EXTERNAL_CLEANUP_FIELDS = (
    "database",
    "project",
    "container",
    "volume",
    "network",
    "status",
)
EXTERNAL_STATUS_FIELDS = (
    "run_id",
    "database",
    "project",
    "started_at",
    "finished_at",
    "last_phase",
    "exit_code",
    "cleanup_status",
    "fixture_before_sha256",
    "fixture_after_sha256",
    "status",
)
EXTERNAL_CELL_CONTRACTS = {
    "mysql57": {
        "service": "mysql",
        "resource": "mysql57",
        "host_port": "13306",
        "container_port": "3306",
        "profile": "docker",
        "version_contract": "5.7.44-log",
        "coordinate_contract": "127.0.0.1:13306/foggy_test",
        "catalog": "foggy_test",
        "schema": "none",
        "image_ref": "mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb",
        "image_id": "sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb",
    },
    "mysql8": {
        "service": "mysql8",
        "resource": "mysql8",
        "host_port": "13308",
        "container_port": "3306",
        "profile": "mysql8",
        "version_contract": "8.0.x",
        "coordinate_contract": "127.0.0.1:13308/foggy_test",
        "catalog": "foggy_test",
        "schema": "none",
        "image_ref": "mysql@sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21",
        "image_id": "sha256:f37951fc3753a6a22d6c7bf6978c5e5fefcf6f31814d98c582524f98eae52b21",
    },
    "postgres15": {
        "service": "postgres",
        "resource": "postgres15",
        "host_port": "15432",
        "container_port": "5432",
        "profile": "postgres",
        "version_contract": "15.x",
        "coordinate_contract": "127.0.0.1:15432/foggy_test",
        "catalog": "foggy_test",
        "schema": "public",
        "image_ref": "postgres@sha256:fceb6f86328c36f2438fae3b851b0cc57c4a7e69a58c866d9ce24281f2cf0c9c",
        "image_id": "sha256:fceb6f86328c36f2438fae3b851b0cc57c4a7e69a58c866d9ce24281f2cf0c9c",
    },
    "sqlserver2022": {
        "service": "sqlserver",
        "resource": "sqlserver2022",
        "host_port": "11433",
        "container_port": "1433",
        "profile": "sqlserver",
        "version_contract": "16.0.x SQL Server 2022",
        "coordinate_contract": "driver-aware 127.0.0.1:11433;databaseName=foggy_test",
        "catalog": "foggy_test",
        "schema": "dbo",
        "image_ref": "mcr.microsoft.com/mssql/server@sha256:0ec7739e1c5ec2f57861facbe1f2b74f1d3e147c7c97edf91eeea920c5944d9c",
        "image_id": "sha256:0ec7739e1c5ec2f57861facbe1f2b74f1d3e147c7c97edf91eeea920c5944d9c",
    },
}
EXACT_SENTINEL_CONTRACT = (
    "v934_test_sentinel|contract_version|9.3.4"
    "#sha256=cef04c4c1269e1293bf243e61e0a9672697bfd55b0bca48297943026bd82c191"
)
EXACT_DATABASE_CONTRACT_ROWS = {
    "sqlite": {
        "db_kind": "sqlite",
        "required": "true",
        "approved_reference": (
            "org.xerial:sqlite-jdbc:3.42.0.0#sha256="
            "53174d76087bb73cc29db9c02766fb921fd7fc652f7952f3609e0018e3dd5ded"
        ),
        "version_contract": "3.42.0.0",
        "digest_policy": "exact artifact SHA-256",
        "coordinate_contract": "driver-reported jdbc:sqlite coordinate; memory/file mode explicit",
        "catalog": "none",
        "schema": "none",
        "sentinel_contract": EXACT_SENTINEL_CONTRACT,
        "observed_step": "3",
        "owner": "foggy-dataset-model",
    },
    "mysql57": {
        "db_kind": "mysql57",
        "required": "true",
        "approved_reference": EXTERNAL_CELL_CONTRACTS["mysql57"]["image_ref"],
        "version_contract": EXTERNAL_CELL_CONTRACTS["mysql57"]["version_contract"],
        "digest_policy": "exact OCI digest",
        "coordinate_contract": EXTERNAL_CELL_CONTRACTS["mysql57"]["coordinate_contract"],
        "catalog": EXTERNAL_CELL_CONTRACTS["mysql57"]["catalog"],
        "schema": EXTERNAL_CELL_CONTRACTS["mysql57"]["schema"],
        "sentinel_contract": EXACT_SENTINEL_CONTRACT,
        "observed_step": "3",
        "owner": "foggy-dataset-demo/docker",
    },
    "mysql8": {
        "db_kind": "mysql8",
        "required": "true",
        "approved_reference": EXTERNAL_CELL_CONTRACTS["mysql8"]["image_ref"],
        "version_contract": EXTERNAL_CELL_CONTRACTS["mysql8"]["version_contract"],
        "digest_policy": "exact OCI digest",
        "coordinate_contract": EXTERNAL_CELL_CONTRACTS["mysql8"]["coordinate_contract"],
        "catalog": EXTERNAL_CELL_CONTRACTS["mysql8"]["catalog"],
        "schema": EXTERNAL_CELL_CONTRACTS["mysql8"]["schema"],
        "sentinel_contract": EXACT_SENTINEL_CONTRACT,
        "observed_step": "3",
        "owner": "foggy-dataset-demo/docker",
    },
    "postgres15": {
        "db_kind": "postgres15",
        "required": "true",
        "approved_reference": EXTERNAL_CELL_CONTRACTS["postgres15"]["image_ref"],
        "version_contract": EXTERNAL_CELL_CONTRACTS["postgres15"]["version_contract"],
        "digest_policy": "exact OCI digest",
        "coordinate_contract": EXTERNAL_CELL_CONTRACTS["postgres15"]["coordinate_contract"],
        "catalog": EXTERNAL_CELL_CONTRACTS["postgres15"]["catalog"],
        "schema": EXTERNAL_CELL_CONTRACTS["postgres15"]["schema"],
        "sentinel_contract": EXACT_SENTINEL_CONTRACT,
        "observed_step": "3",
        "owner": "foggy-dataset-demo/docker",
    },
    "sqlserver2022": {
        "db_kind": "sqlserver2022",
        "required": "true",
        "approved_reference": EXTERNAL_CELL_CONTRACTS["sqlserver2022"]["image_ref"],
        "version_contract": EXTERNAL_CELL_CONTRACTS["sqlserver2022"]["version_contract"],
        "digest_policy": "exact OCI digest",
        "coordinate_contract": EXTERNAL_CELL_CONTRACTS["sqlserver2022"]["coordinate_contract"],
        "catalog": EXTERNAL_CELL_CONTRACTS["sqlserver2022"]["catalog"],
        "schema": EXTERNAL_CELL_CONTRACTS["sqlserver2022"]["schema"],
        "sentinel_contract": EXACT_SENTINEL_CONTRACT,
        "observed_step": "3",
        "owner": "foggy-dataset-demo/docker",
    },
}


class MatrixError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def fail(code: str, message: str) -> None:
    raise MatrixError(code, message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        fail("E_IO", f"cannot hash {path}: {exc}")
    return digest.hexdigest()


def sha256_tree(path: Path) -> str:
    if path.is_symlink() or not path.is_dir():
        fail("E_PROTECTED_TREE", f"protected tree is not a regular directory: {path}")
    digest = hashlib.sha256()
    files: list[tuple[str, Path]] = []
    for candidate in path.rglob("*"):
        if candidate.is_symlink():
            fail("E_PROTECTED_TREE", f"protected tree contains a symlink: {candidate}")
        if candidate.is_file():
            files.append((candidate.relative_to(path).as_posix(), candidate))
        elif not candidate.is_dir():
            fail("E_PROTECTED_TREE", f"protected tree contains a special file: {candidate}")
    if not files:
        fail("E_PROTECTED_TREE", f"protected tree is empty: {path}")
    for relative, candidate in sorted(files):
        payload = candidate.read_bytes()
        for value in (relative.encode("utf-8"), payload):
            digest.update(len(value).to_bytes(8, "big"))
            digest.update(value)
    return digest.hexdigest()


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail("E_JSON", f"cannot read {path}: {exc}")
    if not isinstance(value, dict):
        fail("E_JSON", f"JSON root is not an object: {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def read_tsv(path: Path, header: Sequence[str]) -> list[dict[str, str]]:
    try:
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            if reader.fieldnames != list(header):
                fail("E_TSV_SCHEMA", f"unexpected header in {path}: {reader.fieldnames}")
            rows = [dict(row) for row in reader]
    except OSError as exc:
        fail("E_TSV", f"cannot read {path}: {exc}")
    if any(any(value is None for value in row.values()) for row in rows):
        fail("E_TSV_SCHEMA", f"malformed row in {path}")
    return rows


def write_tsv(path: Path, header: Sequence[str], rows: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=list(header),
            delimiter="\t",
            lineterminator="\n",
        )
        writer.writeheader()
        for row in rows:
            writer.writerow({name: row[name] for name in header})


def require_exact_fields(value: dict[str, Any], fields: set[str], label: str) -> None:
    if set(value) != fields:
        fail(
            "E_SCHEMA",
            f"{label} fields differ; missing={sorted(fields - set(value))} "
            f"extra={sorted(set(value) - fields)}",
        )


def safe_relative_path(value: str, label: str) -> Path:
    path = Path(value)
    if not value or path == Path(".") or path.is_absolute() or ".." in path.parts:
        fail("E_PATH", f"unsafe {label}: {value!r}")
    return path


def safe_token(value: str, label: str) -> str:
    if value in {".", ".."} or not re.fullmatch(r"[A-Za-z0-9._-]+", value or ""):
        fail("E_PATH", f"unsafe {label}: {value!r}")
    return value


def ensure_within(path: Path, root: Path, label: str) -> Path:
    resolved = path.resolve()
    base = root.resolve()
    if resolved == base or not resolved.is_relative_to(base):
        fail("E_RUN_ROOT", f"{label} is outside the run root {base}: {resolved}")
    return resolved


def reject_symlink_components(
    path: Path,
    boundary: Path,
    label: str,
    error_code: str,
) -> Path:
    lexical = Path(os.path.abspath(path))
    boundary = boundary.resolve()
    if lexical == boundary or not lexical.is_relative_to(boundary):
        fail(error_code, f"{label} is outside its boundary {boundary}: {lexical}")
    current = lexical
    while current != boundary:
        if current.is_symlink():
            fail(error_code, f"{label} contains a symlink path component: {current}")
        current = current.parent
    return lexical


def _parse_iso_timestamp(value: Any, label: str, error_code: str) -> dt.datetime:
    if not isinstance(value, str):
        fail(error_code, f"{label} timestamp is not a string")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        fail(error_code, f"{label} timestamp is invalid: {value!r}")
    if parsed.tzinfo is None:
        fail(error_code, f"{label} timestamp has no timezone: {value!r}")
    return parsed


def read_exact_env(path: Path, fields: Sequence[str]) -> dict[str, str]:
    lexical = path.absolute()
    if lexical.is_symlink() or not lexical.is_file():
        fail("E_CELL_EVIDENCE", f"cell env is not a regular file: {lexical}")
    try:
        payload = lexical.read_bytes()
    except OSError as exc:
        fail("E_CELL_EVIDENCE", f"cannot read cell env {lexical}: {exc}")
    if not payload.endswith(b"\n") or b"\r" in payload or b"\0" in payload:
        fail("E_CELL_EVIDENCE", f"cell env has non-canonical line encoding: {lexical}")
    try:
        lines = payload.decode("utf-8").splitlines()
    except UnicodeDecodeError as exc:
        fail("E_CELL_EVIDENCE", f"cell env is not UTF-8: {lexical}: {exc}")
    result: dict[str, str] = {}
    observed_fields: list[str] = []
    for line in lines:
        key, separator, value = line.partition("=")
        if not separator or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", key):
            fail("E_CELL_EVIDENCE", f"malformed cell env line in {lexical}: {line!r}")
        if key in result:
            fail("E_CELL_EVIDENCE", f"duplicate cell env field in {lexical}: {key}")
        result[key] = value
        observed_fields.append(key)
    if observed_fields != list(fields):
        fail(
            "E_CELL_EVIDENCE",
            f"cell env fields differ in {lexical}: {observed_fields}",
        )
    return result


def _scan_regular_tree(root: Path, label: str, error_code: str) -> tuple[set[Path], set[Path]]:
    lexical = root.absolute()
    if lexical.is_symlink() or not lexical.is_dir():
        fail(error_code, f"{label} is not a regular directory: {lexical}")
    root = lexical.resolve()
    files: set[Path] = set()
    directories: set[Path] = set()
    try:
        candidates = list(root.rglob("*"))
    except OSError as exc:
        fail(error_code, f"cannot scan {label} {root}: {exc}")
    for candidate in candidates:
        relative = candidate.relative_to(root)
        if candidate.is_symlink():
            fail(error_code, f"{label} contains a symlink: {candidate}")
        if candidate.is_file():
            files.add(relative)
        elif candidate.is_dir():
            directories.add(relative)
        else:
            fail(error_code, f"{label} contains a special file: {candidate}")
    return files, directories


def _directories_for_files(files: Iterable[Path]) -> set[Path]:
    directories: set[Path] = set()
    for relative in files:
        parent = relative.parent
        while parent != Path("."):
            directories.add(parent)
            parent = parent.parent
    return directories


def _assert_exact_tree(
    root: Path,
    expected_files: Iterable[Path],
    label: str,
    error_code: str,
) -> list[Path]:
    expected = set(expected_files)
    if not expected or any(path.is_absolute() or ".." in path.parts for path in expected):
        fail(error_code, f"{label} expected file set is empty or unsafe")
    observed_files, observed_directories = _scan_regular_tree(root, label, error_code)
    if observed_files != expected:
        fail(
            error_code,
            f"{label} file set differs missing={sorted(map(str, expected - observed_files))} "
            f"extra={sorted(map(str, observed_files - expected))}",
        )
    expected_directories = _directories_for_files(expected)
    if observed_directories != expected_directories:
        fail(
            error_code,
            f"{label} directory set differs missing="
            f"{sorted(map(str, expected_directories - observed_directories))} "
            f"extra={sorted(map(str, observed_directories - expected_directories))}",
        )
    return sorted(expected, key=lambda path: path.as_posix())


def _artifact_record(path: Path, bundle_root: Path) -> dict[str, Any]:
    lexical = path.absolute()
    if lexical.is_symlink() or not lexical.is_file():
        fail("E_FINAL_BUNDLE", f"artifact is not a regular file: {lexical}")
    path = lexical.resolve()
    bundle_root = bundle_root.resolve()
    if not path.is_relative_to(bundle_root):
        fail("E_FINAL_BUNDLE", f"artifact escapes final bundle: {path}")
    stat = path.stat()
    return {
        "path": path.relative_to(bundle_root).as_posix(),
        "sha256": sha256_file(path),
        "mtime_ns": stat.st_mtime_ns,
        "size_bytes": stat.st_size,
    }


def _artifact_tree_sha256(records: Sequence[dict[str, Any]]) -> str:
    digest = hashlib.sha256()
    for record in records:
        for field in ("path", "sha256", "mtime_ns", "size_bytes"):
            value = str(record[field]).encode("utf-8")
            digest.update(len(value).to_bytes(8, "big"))
            digest.update(value)
    return digest.hexdigest()


def _validate_artifact_record(
    record: Any,
    bundle_root: Path,
    label: str,
    error_code: str = "E_FINAL_BUNDLE",
) -> Path:
    if not isinstance(record, dict):
        fail(error_code, f"{label} artifact record is not an object")
    try:
        require_exact_fields(record, ARTIFACT_FIELDS, f"{label} artifact")
    except MatrixError as exc:
        fail(error_code, str(exc))
    if (
        not isinstance(record["path"], str)
        or not re.fullmatch(r"[0-9a-f]{64}", str(record["sha256"]))
        or not isinstance(record["mtime_ns"], int)
        or record["mtime_ns"] < 0
        or not isinstance(record["size_bytes"], int)
        or record["size_bytes"] < 0
    ):
        fail(error_code, f"{label} artifact record values are invalid")
    relative = safe_relative_path(record["path"], f"{label} artifact path")
    lexical = (bundle_root / relative).absolute()
    if lexical.is_symlink() or not lexical.is_file():
        fail(error_code, f"{label} artifact is missing or unsafe: {lexical}")
    path = lexical.resolve()
    if not path.is_relative_to(bundle_root.resolve()):
        fail(error_code, f"{label} artifact escapes final bundle: {path}")
    stat = path.stat()
    if (
        sha256_file(path) != record["sha256"]
        or stat.st_mtime_ns != record["mtime_ns"]
        or stat.st_size != record["size_bytes"]
    ):
        fail(error_code, f"{label} artifact binding differs: {path}")
    return path


def _copy_exact_tree(source: Path, target: Path, expected_files: Sequence[Path], label: str) -> None:
    _assert_exact_tree(source, expected_files, label, "E_FINAL_BUNDLE")
    try:
        shutil.copytree(source, target, copy_function=shutil.copy2)
    except OSError as exc:
        fail("E_IO", f"cannot copy {label}: {exc}")
    _assert_exact_tree(target, expected_files, f"copied {label}", "E_FINAL_BUNDLE")
    for relative in expected_files:
        source_path = source / relative
        target_path = target / relative
        source_stat = source_path.stat()
        target_stat = target_path.stat()
        if (
            sha256_file(source_path) != sha256_file(target_path)
            or source_stat.st_mtime_ns != target_stat.st_mtime_ns
            or source_stat.st_size != target_stat.st_size
        ):
            fail("E_FINAL_BUNDLE", f"copied {label} artifact differs: {relative}")


def bound_path(repo: Path, value: str, label: str) -> Path:
    relative = safe_relative_path(value, label)
    lexical = (repo / relative).absolute()
    if lexical.is_symlink() or not lexical.is_file():
        fail("E_BINDING", f"{label} is not a regular file: {lexical}")
    path = lexical.resolve()
    if not path.is_relative_to(repo.resolve()):
        fail("E_PATH", f"{label} escapes repository: {value}")
    return path


def framed(parts: Sequence[str], prefix: str) -> str:
    return prefix + "|" + "|".join(f"{len(part.encode('utf-8'))}:{part}" for part in parts)


def execution_key(variant: str, report_fqcn: str) -> str:
    return framed(["failsafe", "database-contract-matrix", variant, report_fqcn], "v934")


def source_path_from_id(source_id: str) -> str:
    match = re.fullmatch(r"v934-src\|(\d+):(.*)", source_id)
    if not match or len(match.group(2).encode("utf-8")) != int(match.group(1)):
        fail("E_SOURCE_ID", f"invalid source_id: {source_id}")
    return match.group(2)


def current_git_head(repo: Path) -> str:
    try:
        result = subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=repo,
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        fail("E_GIT", f"cannot resolve current Git HEAD: {exc}")
    head = result.stdout.strip()
    if not re.fullmatch(r"[0-9a-f]{40,64}", head):
        fail("E_GIT", f"invalid Git HEAD: {head!r}")
    return head


@dataclass(frozen=True)
class ExpectedReport:
    variant_key: str
    db_kind: str
    report_fqcn: str
    source_id: str
    testcase_nodes: int
    execution_key: str


@dataclass(frozen=True)
class MatrixContract:
    repo: Path
    path: Path
    raw: dict[str, Any]
    sha256: str
    amendment_path: Path
    amendment_sha256: str
    variants: dict[str, dict[str, Any]]
    expected: dict[str, dict[str, ExpectedReport]]
    amendments: list[dict[str, str]]
    protected_trees: list[dict[str, str]]


def _validate_successor_hash_manifest(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        fail("E_BINDING", f"cannot read successor hash manifest: {exc}")
    for line in lines:
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+)", line)
        if not match:
            fail("E_BINDING", f"invalid successor hash entry: {line!r}")
        digest, name = match.groups()
        if name in entries:
            fail("E_BINDING", f"duplicate successor hash entry: {name}")
        target = path.parent / name
        if not target.is_file() or target.is_symlink() or sha256_file(target) != digest:
            fail("E_BINDING", f"stale successor artifact: {name}")
        entries[name] = digest
    for required in ("contract-freeze.json", "deferred-step3.tsv", "discovery-inventory.tsv"):
        if required not in entries:
            fail("E_BINDING", f"successor manifest lacks {required}")
    return entries


def _validate_authority_hash_manifest(repo: Path, path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        fail("E_AUTHORITY_MANIFEST", f"cannot read authority hash manifest: {exc}")
    for line in lines:
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._/-]+)", line)
        if not match:
            fail("E_AUTHORITY_MANIFEST", f"invalid authority hash entry: {line!r}")
        digest, relative = match.groups()
        if relative in entries or Path(relative).is_absolute() or ".." in Path(relative).parts:
            fail("E_AUTHORITY_MANIFEST", f"unsafe or duplicate authority path: {relative}")
        lexical = repo / relative
        target = lexical.resolve()
        if (
            lexical.is_symlink()
            or not target.is_relative_to(repo)
            or not target.is_file()
            or sha256_file(target) != digest
        ):
            fail("E_AUTHORITY_MANIFEST", f"stale authority artifact: {relative}")
        entries[relative] = digest
    if list(entries) != EXACT_AUTHORITY_FILES:
        fail("E_AUTHORITY_MANIFEST", "authority artifact set or order differs")
    return entries


def _validate_protected_tree_manifest(repo: Path, path: Path) -> list[dict[str, str]]:
    rows = read_tsv(path, PROTECTED_TREE_HEADER)
    if [row["path"] for row in rows] != EXACT_PROTECTED_TREES:
        fail("E_PROTECTED_TREE", "protected tree set or order differs")
    for row in rows:
        relative = safe_relative_path(row["path"], "protected tree")
        lexical = repo / relative
        target = lexical.resolve()
        if lexical.is_symlink() or not target.is_relative_to(repo):
            fail("E_PROTECTED_TREE", f"unsafe protected tree: {row['path']}")
        actual = sha256_tree(target)
        if row["tree_sha256"] != actual:
            fail("E_PROTECTED_TREE", f"protected tree hash differs: {row['path']}")
    return rows


def combined_protected_source_hash(repo: Path, rows: Sequence[dict[str, str]]) -> str:
    digest = hashlib.sha256()
    for row in rows:
        relative = row["path"]
        actual = sha256_tree((repo / relative).resolve())
        for value in (relative.encode("utf-8"), actual.encode("ascii")):
            digest.update(len(value).to_bytes(8, "big"))
            digest.update(value)
    return digest.hexdigest()


def _parse_contract_reports(
    raw: dict[str, Any],
) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, ExpectedReport]]]:
    report_sets = raw.get("report_sets")
    variants_raw = raw.get("variants")
    if not isinstance(report_sets, dict) or set(report_sets) != {
        "standard",
        "mysql8-targeted",
        "postgres15-targeted",
    }:
        fail("E_CONTRACT", "report_sets are not the exact three reviewed sets")
    parsed_sets: dict[str, list[dict[str, Any]]] = {}
    for set_name, reports in report_sets.items():
        if not isinstance(reports, list) or not reports:
            fail("E_CONTRACT", f"report set is empty: {set_name}")
        seen: set[str] = set()
        parsed_sets[set_name] = []
        for index, report in enumerate(reports):
            if not isinstance(report, dict):
                fail("E_CONTRACT", f"non-object report in {set_name}")
            require_exact_fields(
                report,
                {"report_fqcn", "source_id", "testcase_nodes"},
                f"{set_name}[{index}]",
            )
            fqcn = report["report_fqcn"]
            source_id = report["source_id"]
            nodes = report["testcase_nodes"]
            if (
                not isinstance(fqcn, str)
                or not re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$.]*", fqcn)
                or fqcn in seen
                or not isinstance(source_id, str)
                or not isinstance(nodes, int)
                or nodes <= 0
            ):
                fail("E_CONTRACT", f"invalid report contract in {set_name}: {report}")
            source_path_from_id(source_id)
            seen.add(fqcn)
            parsed_sets[set_name].append(report)

    if not isinstance(variants_raw, list) or len(variants_raw) != 7:
        fail("E_CONTRACT", "variants must contain exactly seven entries")
    variants: dict[str, dict[str, Any]] = {}
    expected: dict[str, dict[str, ExpectedReport]] = {}
    for value in variants_raw:
        if not isinstance(value, dict):
            fail("E_CONTRACT", "variant entry is not an object")
        require_exact_fields(
            value,
            {
                "variant_key",
                "db_kind",
                "report_set",
                "expected_reports",
                "expected_testcase_nodes",
            },
            "variant",
        )
        variant = value["variant_key"]
        db_kind = value["db_kind"]
        set_name = value["report_set"]
        if variant in variants or EXACT_VARIANTS.get(variant) != db_kind:
            fail("E_CONTRACT", f"unexpected or duplicate variant mapping: {variant}/{db_kind}")
        if set_name not in parsed_sets:
            fail("E_CONTRACT", f"unknown report_set for {variant}: {set_name}")
        reports = parsed_sets[set_name]
        nodes = sum(report["testcase_nodes"] for report in reports)
        if value["expected_reports"] != len(reports) or value["expected_testcase_nodes"] != nodes:
            fail("E_CONTRACT", f"variant totals differ for {variant}")
        variants[variant] = dict(value)
        expected[variant] = {
            report["report_fqcn"]: ExpectedReport(
                variant_key=variant,
                db_kind=db_kind,
                report_fqcn=report["report_fqcn"],
                source_id=report["source_id"],
                testcase_nodes=report["testcase_nodes"],
                execution_key=execution_key(variant, report["report_fqcn"]),
            )
            for report in reports
        }
    if set(variants) != set(EXACT_VARIANTS):
        fail("E_CONTRACT", f"variant set differs: {sorted(variants)}")
    return variants, expected


def _validate_source_amendments(
    repo: Path,
    expected: dict[str, dict[str, ExpectedReport]],
    discovery_rows: list[dict[str, str]],
    amendment_rows: list[dict[str, str]],
) -> None:
    discovery: dict[tuple[str, str], dict[str, str]] = {}
    for row in discovery_rows:
        key = (row["source_id"], row["report_fqcn"])
        if key in discovery:
            fail("E_SOURCE_AMENDMENT", f"duplicate discovery identity: {key}")
        discovery[key] = row

    expected_by_source: dict[str, dict[str, Any]] = {}
    for variant_reports in expected.values():
        for report in variant_reports.values():
            row = discovery.get((report.source_id, report.report_fqcn))
            if row is None:
                fail("E_SOURCE_AMENDMENT", f"report is absent from successor discovery: {report.execution_key}")
            if (
                row["module"] != "foggy-dataset-model"
                or row["source_fqcn"] != report.report_fqcn
                or row["discovered_test_nodes"] != str(report.testcase_nodes)
                or row["runtime_deferred_containers"] != "0"
                or row["engine_ids"] != "junit-jupiter"
            ):
                fail("E_SOURCE_AMENDMENT", f"discovery contract differs: {report.execution_key}")
            item = expected_by_source.setdefault(
                report.source_id,
                {
                    "path": source_path_from_id(report.source_id),
                    "frozen": row["source_sha256"],
                    "nodes": report.testcase_nodes,
                    "variants": set(),
                },
            )
            if item["frozen"] != row["source_sha256"] or item["nodes"] != report.testcase_nodes:
                fail("E_SOURCE_AMENDMENT", f"inconsistent discovery source: {report.source_id}")
            item["variants"].add(report.variant_key)

    amendments: dict[str, dict[str, str]] = {}
    for row in amendment_rows:
        source_id = row["source_id"]
        if source_id in amendments or source_id not in expected_by_source:
            fail("E_SOURCE_AMENDMENT", f"duplicate or unrelated amendment: {source_id}")
        amendments[source_id] = row

    changed: set[str] = set()
    for source_id, item in expected_by_source.items():
        path = bound_path(repo, item["path"], "matrix source")
        actual = sha256_file(path)
        if actual == item["frozen"]:
            if source_id in amendments:
                fail("E_SOURCE_AMENDMENT", f"unchanged source has an amendment: {source_id}")
            continue
        changed.add(source_id)
        row = amendments.get(source_id)
        if row is None:
            fail("E_SOURCE_AMENDMENT", f"changed source lacks an amendment: {source_id}")
        expected_variants = set(item["variants"])
        observed_variants = set(filter(None, row["affected_variants"].split(",")))
        if (
            row["path"] != item["path"]
            or row["frozen_source_sha256"] != item["frozen"]
            or row["amended_source_sha256"] != actual
            or row["discovered_test_nodes"] != str(item["nodes"])
            or observed_variants != expected_variants
            or not row["allowed_effect"].strip()
        ):
            fail("E_SOURCE_AMENDMENT", f"amendment tuple differs: {source_id}")
        workitem = bound_path(repo, row["workitem"], "source amendment workitem")
        if not workitem.is_file():
            fail("E_SOURCE_AMENDMENT", f"workitem is missing: {row['workitem']}")
    if set(amendments) != changed:
        fail("E_SOURCE_AMENDMENT", "amendment set differs from changed matrix sources")


def load_contract(repo: Path, contract_path: Path) -> MatrixContract:
    repo = repo.resolve()
    lexical_contract = contract_path.absolute()
    if lexical_contract.is_symlink() or not lexical_contract.is_file():
        fail("E_CONTRACT", f"contract is not a regular file: {lexical_contract}")
    contract_path = lexical_contract.resolve()
    if not contract_path.is_relative_to(repo):
        fail("E_PATH", f"contract is outside repository: {contract_path}")
    raw = read_json(contract_path)
    if (
        raw.get("schema_version") != 1
        or raw.get("kind") != "v934-step3-database-matrix-contract"
        or raw.get("version") != "9.3.4"
        or raw.get("runner") != "failsafe"
        or raw.get("lane") != "database-contract-matrix"
        or raw.get("module") != "foggy-dataset-model"
        or raw.get("required") is not True
    ):
        fail("E_CONTRACT", "contract identity differs")
    if raw.get("totals") != EXACT_TOTALS:
        fail("E_CONTRACT", f"totals are not the frozen exact totals: {raw.get('totals')}")
    probes = raw.get("negative_probes")
    observed_probes = []
    if isinstance(probes, list):
        for probe in probes:
            if isinstance(probe, dict):
                observed_probes.append((probe.get("probe"), probe.get("expected_error")))
    if observed_probes != EXACT_NEGATIVE_PROBES:
        fail("E_CONTRACT", "negative probe contract differs")

    evidence = raw.get("evidence_contract")
    if not isinstance(evidence, dict) or evidence.get("manifest_name") != MANIFEST_NAME:
        fail("E_CONTRACT", "evidence contract is missing")

    bindings = raw.get("bindings")
    required_bindings = {
        "authority_hash_manifest",
        "database_contract",
        "deferred_inventory",
        "protected_tree_manifest",
        "source_amendment",
        "successor_freeze",
        "successor_hash_manifest",
    }
    if not isinstance(bindings, dict) or set(bindings) != required_bindings:
        fail("E_BINDING", "contract binding set differs")
    resolved_bindings: dict[str, Path] = {}
    for name, binding in bindings.items():
        if not isinstance(binding, dict) or "path" not in binding or "sha256" not in binding:
            fail("E_BINDING", f"invalid binding record: {name}")
        path = bound_path(repo, binding["path"], name)
        if sha256_file(path) != binding["sha256"]:
            fail("E_BINDING", f"binding hash differs: {name}")
        resolved_bindings[name] = path
    successor_entries = _validate_successor_hash_manifest(resolved_bindings["successor_hash_manifest"])
    _validate_authority_hash_manifest(repo, resolved_bindings["authority_hash_manifest"])
    protected_tree_rows = _validate_protected_tree_manifest(
        repo, resolved_bindings["protected_tree_manifest"]
    )
    if successor_entries["contract-freeze.json"] != bindings["successor_freeze"]["sha256"]:
        fail("E_BINDING", "successor freeze differs from its hash manifest")
    if successor_entries["deferred-step3.tsv"] != bindings["deferred_inventory"]["sha256"]:
        fail("E_BINDING", "deferred inventory differs from its hash manifest")
    freeze = read_json(resolved_bindings["successor_freeze"])
    if freeze.get("status") != "confirmed" or freeze.get("step") != 2:
        fail("E_BINDING", "successor freeze is not confirmed Step 2")

    variants, expected = _parse_contract_reports(raw)
    expanded_count = sum(len(reports) for reports in expected.values())
    expanded_nodes = sum(
        report.testcase_nodes for reports in expected.values() for report in reports.values()
    )
    if expanded_count != 29 or expanded_nodes != 370:
        fail("E_CONTRACT", f"expanded contract differs: reports={expanded_count} nodes={expanded_nodes}")

    deferred = read_tsv(resolved_bindings["deferred_inventory"], DEFERRED_HEADER)
    db_rows = [row for row in deferred if row["lane"] == "database-contract-matrix"]
    frozen_by_key: dict[str, dict[str, str]] = {}
    for row in db_rows:
        key = row["execution_key"]
        if key in frozen_by_key:
            fail("E_INVENTORY", f"duplicate deferred execution key: {key}")
        frozen_by_key[key] = row
    expanded_keys = {
        report.execution_key for reports in expected.values() for report in reports.values()
    }
    if len(db_rows) != 29 or set(frozen_by_key) != expanded_keys:
        fail("E_INVENTORY", "contract and deferred database execution sets differ")
    for reports in expected.values():
        for report in reports.values():
            row = frozen_by_key[report.execution_key]
            if (
                row["source_id"] != report.source_id
                or row["report_fqcn"] != report.report_fqcn
                or row["runner"] != "failsafe"
                or row["variant_key"] != report.variant_key
                or row["db_kind"] != report.db_kind
                or row["infra_kind"] != "database"
                or row["execution_step"] != "3"
                or row["required"] != "true"
                or row["owner"] != "foggy-dataset-model"
                or row["optional_reason"] != "none"
                or row["review_at"] != "none"
                or row["disposition"] != "deferred-to-step3"
            ):
                fail("E_INVENTORY", f"deferred execution tuple differs: {report.execution_key}")

    database_rows = read_tsv(resolved_bindings["database_contract"], DATABASE_HEADER)
    required_db_rows = {
        row["db_kind"]: row
        for row in database_rows
        if row["required"] == "true" and row["observed_step"] == "3"
    }
    if required_db_rows != EXACT_DATABASE_CONTRACT_ROWS or len(database_rows) != 5:
        fail("E_DATABASE_CONTRACT", "required Step 3 database contract tuples differ")
    for db_kind, runtime in EXTERNAL_CELL_CONTRACTS.items():
        database_row = required_db_rows[db_kind]
        if (
            database_row["approved_reference"] != runtime["image_ref"]
            or database_row["version_contract"] != runtime["version_contract"]
            or database_row["coordinate_contract"] != runtime["coordinate_contract"]
            or database_row["catalog"] != runtime["catalog"]
            or database_row["schema"] != runtime["schema"]
            or database_row["sentinel_contract"] != EXACT_SENTINEL_CONTRACT
        ):
            fail(
                "E_DATABASE_CONTRACT",
                f"runtime/database contract binding differs: {db_kind}",
            )
    sqlite_row = required_db_rows["sqlite"]
    if (
        SQLITE_JAR_SHA256 not in sqlite_row["approved_reference"]
        or sqlite_row["version_contract"] != "3.42.0.0"
        or sqlite_row["catalog"] != "none"
        or sqlite_row["schema"] != "none"
        or sqlite_row["sentinel_contract"] != EXACT_SENTINEL_CONTRACT
    ):
        fail("E_DATABASE_CONTRACT", "SQLite runtime/database contract binding differs")

    cells = raw.get("database_cells")
    if not isinstance(cells, list) or len(cells) != 5:
        fail("E_CONTRACT", "database_cells must contain exactly five entries")
    observed_cells: dict[str, dict[str, Any]] = {}
    for cell in cells:
        if not isinstance(cell, dict):
            fail("E_CONTRACT", "database cell is not an object")
        require_exact_fields(
            cell,
            {"db_kind", "variants", "expected_reports", "expected_testcase_nodes"},
            "database cell",
        )
        db_kind = cell["db_kind"]
        if db_kind in observed_cells or db_kind not in EXACT_DB_KINDS:
            fail("E_CONTRACT", f"unexpected or duplicate database cell: {db_kind}")
        cell_variants = cell["variants"]
        if not isinstance(cell_variants, list) or set(cell_variants) != {
            key for key, kind in EXACT_VARIANTS.items() if kind == db_kind
        }:
            fail("E_CONTRACT", f"database cell variants differ: {db_kind}")
        reports = sum(len(expected[key]) for key in cell_variants)
        nodes = sum(
            report.testcase_nodes for key in cell_variants for report in expected[key].values()
        )
        if cell["expected_reports"] != reports or cell["expected_testcase_nodes"] != nodes:
            fail("E_CONTRACT", f"database cell totals differ: {db_kind}")
        observed_cells[db_kind] = cell
    if set(observed_cells) != EXACT_DB_KINDS:
        fail("E_CONTRACT", "database cell set differs")

    discovery_path = resolved_bindings["successor_hash_manifest"].parent / "discovery-inventory.tsv"
    discovery_rows = read_tsv(discovery_path, DISCOVERY_HEADER)
    amendment_rows = read_tsv(resolved_bindings["source_amendment"], AMENDMENT_HEADER)
    _validate_source_amendments(repo, expected, discovery_rows, amendment_rows)

    return MatrixContract(
        repo=repo,
        path=contract_path,
        raw=raw,
        sha256=sha256_file(contract_path),
        amendment_path=resolved_bindings["source_amendment"],
        amendment_sha256=bindings["source_amendment"]["sha256"],
        variants=variants,
        expected=expected,
        amendments=amendment_rows,
        protected_trees=protected_tree_rows,
    )


@dataclass(frozen=True)
class Marker:
    path: Path
    context: dict[str, Any]
    sha256: str
    mtime_ns: int
    run_root: Path


def _regular_json_marker(path: Path, label: str) -> tuple[Path, dict[str, Any]]:
    lexical = path.absolute()
    if lexical.is_symlink() or not lexical.is_file():
        fail("E_MARKER", f"{label} is not a regular file: {lexical}")
    path = lexical.resolve()
    return path, read_json(path)


def _validate_timestamp(value: Any, label: str) -> None:
    if not isinstance(value, str):
        fail("E_MARKER", f"{label} timestamp is not a string")
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        fail("E_MARKER", f"{label} timestamp is invalid: {value!r}")
    if parsed.tzinfo is None:
        fail("E_MARKER", f"{label} timestamp has no timezone: {value!r}")


def validate_outer_marker(path: Path, contract: MatrixContract) -> Marker:
    path, context = _regular_json_marker(path, "outer marker")
    fields = {
        "schema_version",
        "kind",
        "run_id",
        "lane",
        "runner",
        "git_head",
        "contract_sha256",
        "source_amendment_sha256",
        "started_at",
        "status",
    }
    require_exact_fields(context, fields, "outer marker")
    evidence = contract.raw["evidence_contract"]
    if (
        context["schema_version"] != 1
        or context["kind"] != evidence["outer_marker_kind"]
        or context["lane"] != "database-contract-matrix"
        or context["runner"] != "failsafe"
        or context["contract_sha256"] != contract.sha256
        or context["source_amendment_sha256"] != contract.amendment_sha256
        or context["status"] != "started"
        or context["git_head"] != current_git_head(contract.repo)
    ):
        fail("E_MARKER", f"outer marker provenance differs: {path}")
    safe_token(context["run_id"], "run_id")
    _validate_timestamp(context["started_at"], "outer marker")
    return Marker(
        path=path,
        context=context,
        sha256=sha256_file(path),
        mtime_ns=path.stat().st_mtime_ns,
        run_root=path.parent.resolve(),
    )


def validate_variant_marker(path: Path, outer: Marker, contract: MatrixContract) -> Marker:
    lexical = reject_symlink_components(
        path, outer.run_root, "variant marker", "E_CROSS_RUN_SPLICE"
    )
    resolved = lexical.resolve()
    if resolved == outer.run_root or not resolved.is_relative_to(outer.run_root):
        fail("E_CROSS_RUN_SPLICE", f"variant marker is outside the outer run root: {resolved}")
    path, context = _regular_json_marker(lexical, "variant marker")
    fields = {
        "schema_version",
        "kind",
        "run_id",
        "lane",
        "runner",
        "git_head",
        "contract_sha256",
        "source_amendment_sha256",
        "started_at",
        "status",
        "variant_key",
        "db_kind",
        "outer_marker_sha256",
    }
    require_exact_fields(context, fields, "variant marker")
    variant = context.get("variant_key")
    evidence = contract.raw["evidence_contract"]
    if (
        context["schema_version"] != 1
        or context["kind"] != evidence["variant_marker_kind"]
        or context["run_id"] != outer.context["run_id"]
        or context["lane"] != outer.context["lane"]
        or context["runner"] != outer.context["runner"]
        or context["git_head"] != outer.context["git_head"]
        or context["contract_sha256"] != outer.context["contract_sha256"]
        or context["source_amendment_sha256"] != outer.context["source_amendment_sha256"]
        or context["outer_marker_sha256"] != outer.sha256
        or context["status"] != "started"
        or variant not in contract.variants
        or context["db_kind"] != EXACT_VARIANTS.get(variant)
    ):
        fail("E_CROSS_RUN_SPLICE", f"variant marker tuple differs from outer run: {path}")
    _validate_timestamp(context["started_at"], "variant marker")
    mtime_ns = path.stat().st_mtime_ns
    if mtime_ns < outer.mtime_ns:
        fail("E_STALE_REPORT", f"variant marker predates outer marker: {path}")
    return Marker(
        path=path,
        context=context,
        sha256=sha256_file(path),
        mtime_ns=mtime_ns,
        run_root=outer.run_root,
    )


@dataclass(frozen=True)
class ValidatedCell:
    db_kind: str
    root: Path
    source_root: Path
    fixture_sha256: str
    files: tuple[Path, ...]


def _validate_fixture_files(cell_root: Path) -> str:
    expected = CANONICAL_FIXTURE.encode("utf-8")
    for name in ("fixture-first.txt", "fixture-before.txt", "fixture-after.txt"):
        path = cell_root / name
        try:
            payload = path.read_bytes()
        except OSError as exc:
            fail("E_CELL_EVIDENCE", f"cannot read fixture evidence {path}: {exc}")
        if payload != expected:
            fail("E_CELL_EVIDENCE", f"cell fixture is not canonical: {path}")
    return hashlib.sha256(expected).hexdigest()


def _validate_cell_freshness(cell_root: Path, files: Sequence[Path], outer: Marker) -> None:
    for relative in files:
        path = cell_root / relative
        if path.stat().st_mtime_ns <= outer.mtime_ns:
            fail("E_CELL_EVIDENCE", f"cell evidence is not newer than outer marker: {path}")


def _validate_sqlite_cell(
    cell_root: Path,
    source_root: Path,
    outer: Marker,
) -> ValidatedCell:
    files = _assert_exact_tree(
        cell_root,
        (Path(name) for name in SQLITE_CELL_FILES),
        "sqlite cell evidence",
        "E_CELL_EVIDENCE",
    )
    fixture_sha256 = _validate_fixture_files(cell_root)
    resource = read_exact_env(cell_root / "resource.env", SQLITE_RESOURCE_FIELDS)
    verification = read_exact_env(
        cell_root / "verification.env", SQLITE_VERIFICATION_FIELDS
    )
    cleanup = read_exact_env(cell_root / "cleanup.env", SQLITE_CLEANUP_FIELDS)
    expected_database_file = source_root / "database.sqlite"
    if (
        resource["database"] != "sqlite"
        or resource["database_file"] != str(expected_database_file)
        or resource["jdbc_url"] != f"jdbc:sqlite:{expected_database_file}"
        or not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", resource["sqlite_python_version"])
        or resource["sqlite_jdbc_jar_before_sha256"] != SQLITE_JAR_SHA256
        or resource["fixture_before_sha256"] != fixture_sha256
        or resource["status"] != "prepared"
        or verification
        != {
            "database": "sqlite",
            "sqlite_jdbc_jar_after_sha256": SQLITE_JAR_SHA256,
            "fixture_before_sha256": fixture_sha256,
            "fixture_after_sha256": fixture_sha256,
            "status": "passed",
        }
        or cleanup != {"database": "sqlite", "status": "passed"}
    ):
        fail("E_CELL_EVIDENCE", "SQLite cell evidence tuple differs")
    _validate_cell_freshness(cell_root, files, outer)
    return ValidatedCell("sqlite", cell_root, source_root, fixture_sha256, tuple(files))


def _external_project(run_id: str, db_kind: str) -> str:
    scope = hashlib.sha256(f"{run_id}|{db_kind}\n".encode("utf-8")).hexdigest()[:12]
    return f"v934db-{db_kind}-{scope}"


def _validate_database_identity(db_kind: str, identity: str) -> None:
    valid = False
    if db_kind == "mysql57":
        valid = identity == "foggy_test|5.7.44-log"
    elif db_kind == "mysql8":
        valid = re.fullmatch(r"foggy_test\|8\.0\.[A-Za-z0-9._+-]+", identity) is not None
    elif db_kind == "postgres15":
        valid = identity.startswith("foggy_test|public|15.")
    elif db_kind == "sqlserver2022":
        valid = identity.startswith("foggy_test|dbo|16.0.")
    if not valid or "\n" in identity or "\r" in identity:
        fail("E_CELL_EVIDENCE", f"database identity differs for {db_kind}: {identity!r}")


def _validate_external_cell(
    db_kind: str,
    cell_root: Path,
    source_root: Path,
    outer: Marker,
) -> ValidatedCell:
    files = _assert_exact_tree(
        cell_root,
        (Path(name) for name in EXTERNAL_CELL_FILES),
        f"{db_kind} cell evidence",
        "E_CELL_EVIDENCE",
    )
    fixture_sha256 = _validate_fixture_files(cell_root)
    resource = read_exact_env(cell_root / "resource.env", EXTERNAL_RESOURCE_FIELDS)
    runtime = read_exact_env(cell_root / "runtime.env", EXTERNAL_RUNTIME_FIELDS)
    cleanup = read_exact_env(cell_root / "cleanup.env", EXTERNAL_CLEANUP_FIELDS)
    status = read_exact_env(cell_root / "status.env", EXTERNAL_STATUS_FIELDS)
    expected = EXTERNAL_CELL_CONTRACTS[db_kind]
    project = _external_project(outer.context["run_id"], db_kind)
    resource_token = expected["resource"]
    container = f"{project}-{resource_token}"
    volume = f"{project}-{resource_token}-data"
    network = f"{project}-network"
    expected_resource = {
        "run_id": outer.context["run_id"],
        "database": db_kind,
        "service": expected["service"],
        "project": project,
        "container": container,
        "volume": volume,
        "network": network,
        "host_port": expected["host_port"],
        "container_port": expected["container_port"],
        "profile": expected["profile"],
        "expected_image_ref": expected["image_ref"],
        "expected_image_id": expected["image_id"],
    }
    if resource != expected_resource:
        fail("E_CELL_EVIDENCE", f"resource identity differs for {db_kind}")
    identity = runtime["database_identity"]
    _validate_database_identity(db_kind, identity)
    if runtime != {
        "database": db_kind,
        "actual_image_id": expected["image_id"],
        "actual_image_ref": expected["image_ref"],
        "actual_repo_digest": expected["image_ref"],
        "actual_project": project,
        "actual_service": expected["service"],
        "actual_mapped_port": f"127.0.0.1:{expected['host_port']}",
        "volume_project": project,
        "network_project": project,
        "volume_created": runtime["volume_created"],
        "database_identity": identity,
        "status": "verified",
    }:
        fail("E_CELL_EVIDENCE", f"runtime identity differs for {db_kind}")
    volume_created = _parse_iso_timestamp(
        runtime["volume_created"], f"{db_kind} volume_created", "E_CELL_EVIDENCE"
    )
    if cleanup != {
        "database": db_kind,
        "project": project,
        "container": container,
        "volume": volume,
        "network": network,
        "status": "passed",
    }:
        fail("E_CELL_EVIDENCE", f"cleanup identity differs for {db_kind}")
    started = _parse_iso_timestamp(status["started_at"], f"{db_kind} started_at", "E_CELL_EVIDENCE")
    finished = _parse_iso_timestamp(
        status["finished_at"], f"{db_kind} finished_at", "E_CELL_EVIDENCE"
    )
    outer_started = _parse_iso_timestamp(
        outer.context["started_at"], "outer marker started_at", "E_CELL_EVIDENCE"
    )
    if (
        finished < started
        or volume_created < started
        or volume_created < outer_started
        or status
        != {
            "run_id": outer.context["run_id"],
            "database": db_kind,
            "project": project,
            "started_at": status["started_at"],
            "finished_at": status["finished_at"],
            "last_phase": "completed",
            "exit_code": "0",
            "cleanup_status": "passed",
            "fixture_before_sha256": fixture_sha256,
            "fixture_after_sha256": fixture_sha256,
            "status": "passed",
        }
    ):
        fail("E_CELL_EVIDENCE", f"terminal status differs for {db_kind}")
    try:
        identity_payload = (cell_root / "database-identity.txt").read_bytes()
    except OSError as exc:
        fail("E_CELL_EVIDENCE", f"cannot read database identity for {db_kind}: {exc}")
    if identity_payload != f"{identity}\n".encode("utf-8"):
        fail("E_CELL_EVIDENCE", f"database identity file differs for {db_kind}")
    _validate_cell_freshness(cell_root, files, outer)
    return ValidatedCell(db_kind, cell_root, source_root, fixture_sha256, tuple(files))


def validate_cells_root(cells_root: Path, outer: Marker) -> dict[str, ValidatedCell]:
    expected_root = (outer.run_root / FINAL_CELLS_DIRECTORY).resolve()
    lexical = reject_symlink_components(
        cells_root, outer.run_root, "cells root", "E_CELL_EVIDENCE"
    )
    if lexical.is_symlink() or not lexical.is_dir() or lexical.resolve() != expected_root:
        fail(
            "E_CELL_EVIDENCE",
            f"cells root must be the current run cells directory: {lexical}",
        )
    try:
        children = list(expected_root.iterdir())
    except OSError as exc:
        fail("E_CELL_EVIDENCE", f"cannot scan cells root {expected_root}: {exc}")
    observed_names = {child.name for child in children}
    if observed_names != set(EXACT_DB_ORDER):
        fail(
            "E_CELL_EVIDENCE",
            f"cell directory set differs missing={sorted(set(EXACT_DB_ORDER) - observed_names)} "
            f"extra={sorted(observed_names - set(EXACT_DB_ORDER))}",
        )
    if any(child.is_symlink() or not child.is_dir() for child in children):
        fail("E_CELL_EVIDENCE", "cells root contains a non-directory or symlink cell")
    result: dict[str, ValidatedCell] = {}
    for db_kind in EXACT_DB_ORDER:
        actual_root = expected_root / db_kind
        source_root = outer.run_root / FINAL_CELLS_DIRECTORY / db_kind
        if db_kind == "sqlite":
            cell = _validate_sqlite_cell(actual_root, source_root, outer)
        else:
            cell = _validate_external_cell(db_kind, actual_root, source_root, outer)
        result[db_kind] = cell
    return result


@dataclass(frozen=True)
class ReportMetrics:
    path: Path
    report_fqcn: str
    tests: int
    failures: int
    errors: int
    skipped: int
    testcase_nodes: int


def _integer_attribute(root: ET.Element, name: str, path: Path) -> int:
    value = root.get(name)
    if value is None or not re.fullmatch(r"[0-9]+", value):
        fail("E_REPORT_XML", f"invalid {name} attribute in {path}: {value!r}")
    return int(value)


def parse_report(path: Path) -> ReportMetrics:
    if path.is_symlink() or not path.is_file():
        fail("E_REPORT_XML", f"report is not a regular file: {path}")
    try:
        payload = path.read_bytes()
    except OSError as exc:
        fail("E_REPORT_XML", f"cannot read {path}: {exc}")
    upper = payload.upper()
    if b"<!DOCTYPE" in upper or b"<!ENTITY" in upper:
        fail("E_REPORT_XML", f"DTD/entity declarations are forbidden: {path}")
    try:
        root = ET.fromstring(payload)
    except ET.ParseError as exc:
        fail("E_REPORT_XML", f"cannot parse {path}: {exc}")
    if root.tag.rsplit("}", 1)[-1] != "testsuite":
        fail("E_REPORT_XML", f"root is not testsuite: {path}")
    fqcn = root.get("name")
    if not fqcn or not re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$.]*", fqcn):
        fail("E_REPORT_XML", f"invalid report identity in {path}: {fqcn!r}")
    testcases = [element for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "testcase"]
    failures = _integer_attribute(root, "failures", path)
    errors = _integer_attribute(root, "errors", path)
    skipped = _integer_attribute(root, "skipped", path)
    child_outcomes = {
        "failures": sum(
            1 for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "failure"
        ),
        "errors": sum(1 for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "error"),
        "skipped": sum(
            1 for element in root.iter() if element.tag.rsplit("}", 1)[-1] == "skipped"
        ),
    }
    if child_outcomes != {"failures": failures, "errors": errors, "skipped": skipped}:
        fail(
            "E_REPORT_XML",
            f"suite outcome attributes differ from testcase outcome nodes in {path}: "
            f"attributes=F{failures}/E{errors}/S{skipped} children={child_outcomes}",
        )
    return ReportMetrics(
        path=path,
        report_fqcn=fqcn,
        tests=_integer_attribute(root, "tests", path),
        failures=failures,
        errors=errors,
        skipped=skipped,
        testcase_nodes=len(testcases),
    )


def _validate_report(
    metrics: ReportMetrics,
    expected: ExpectedReport,
    marker_mtime_ns: int,
) -> None:
    if metrics.path.stat().st_mtime_ns <= marker_mtime_ns:
        fail("E_STALE_REPORT", f"report is not newer than its variant marker: {metrics.path}")
    if metrics.failures or metrics.errors or metrics.skipped:
        fail(
            "E_REPORT_OUTCOME",
            f"non-zero outcome for {expected.execution_key}: "
            f"F{metrics.failures}/E{metrics.errors}/S{metrics.skipped}",
        )
    if metrics.tests != expected.testcase_nodes or metrics.testcase_nodes != expected.testcase_nodes:
        fail(
            "E_REPORT_COUNT",
            f"test count differs for {expected.execution_key}: "
            f"suite={metrics.tests} testcase={metrics.testcase_nodes} expected={expected.testcase_nodes}",
        )
    expected_name = f"TEST-{expected.report_fqcn}.xml"
    if metrics.path.name != expected_name:
        fail("E_REPORT_IDENTITY", f"unexpected report filename: {metrics.path.name}")


def _scan_variant_reports(
    reports_dir: Path,
    expected: dict[str, ExpectedReport],
    marker_mtime_ns: int,
) -> dict[str, ReportMetrics]:
    lexical_reports_dir = reports_dir.absolute()
    if lexical_reports_dir.is_symlink() or not lexical_reports_dir.is_dir():
        fail(
            "E_REPORT_DIRECTORY",
            f"report directory is not a regular directory: {lexical_reports_dir}",
        )
    reports_dir = lexical_reports_dir.resolve()
    observed: dict[str, ReportMetrics] = {}
    for path in sorted(reports_dir.glob("TEST-*.xml")):
        metrics = parse_report(path)
        if metrics.report_fqcn in observed:
            fail("E_DUPLICATE_REPORT", f"duplicate report identity: {metrics.report_fqcn}")
        observed[metrics.report_fqcn] = metrics
    missing = sorted(set(expected) - set(observed))
    extra = sorted(set(observed) - set(expected))
    if missing:
        fail("E_MISSING_REPORT", f"missing reports: {missing}")
    if extra:
        fail("E_EXTRA_REPORT", f"unexpected reports: {extra}")
    for fqcn, report in expected.items():
        _validate_report(observed[fqcn], report, marker_mtime_ns)
    return observed


def _marker_record(marker: Marker, evidence_path: str) -> dict[str, Any]:
    return {
        "context": marker.context,
        "evidence_path": evidence_path,
        "mtime_ns": marker.mtime_ns,
        "sha256": marker.sha256,
    }


def _prepare_output(output: Path, outer: Marker) -> tuple[Path, Path]:
    output = ensure_within(output, outer.run_root, "evidence output")
    if output.exists() or output.is_symlink():
        fail("E_OUTPUT_EXISTS", f"output already exists: {output}")
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=f".{output.name}.", dir=output.parent))
    return output, temporary


def _publish_output(temporary: Path, output: Path) -> None:
    try:
        os.replace(temporary, output)
    except OSError as exc:
        fail("E_IO", f"cannot publish {output}: {exc}")


def collect_variant(
    contract: MatrixContract,
    variant_key: str,
    reports_dir: Path,
    outer_marker_path: Path,
    variant_marker_path: Path,
    output: Path,
) -> Path:
    if variant_key not in contract.variants:
        fail("E_VARIANT", f"unexpected variant: {variant_key}")
    outer = validate_outer_marker(outer_marker_path, contract)
    marker = validate_variant_marker(variant_marker_path, outer, contract)
    if marker.context["variant_key"] != variant_key:
        fail("E_CROSS_RUN_SPLICE", f"requested variant differs from marker: {variant_key}")
    reports = _scan_variant_reports(
        reports_dir,
        contract.expected[variant_key],
        marker.mtime_ns,
    )
    output, temporary = _prepare_output(output, outer)
    try:
        outer_copy = temporary / "outer-run-marker.json"
        marker_copy = temporary / "variant-run-marker.json"
        shutil.copy2(outer.path, outer_copy)
        shutil.copy2(marker.path, marker_copy)
        raw_root = temporary / RAW_DIRECTORY
        raw_root.mkdir()
        report_rows: list[dict[str, Any]] = []
        for fqcn in sorted(reports):
            metrics = reports[fqcn]
            expected = contract.expected[variant_key][fqcn]
            relative = Path(RAW_DIRECTORY) / metrics.path.name
            evidence_path = temporary / relative
            shutil.copy2(metrics.path, evidence_path)
            report_rows.append(
                {
                    "execution_key": expected.execution_key,
                    "source_id": expected.source_id,
                    "report_fqcn": fqcn,
                    "raw_report": relative.as_posix(),
                    "tests": metrics.tests,
                    "failures": metrics.failures,
                    "errors": metrics.errors,
                    "skipped": metrics.skipped,
                    "testcase_nodes": metrics.testcase_nodes,
                    "sha256": sha256_file(evidence_path),
                    "source_mtime_ns": metrics.path.stat().st_mtime_ns,
                    "evidence_mtime_ns": evidence_path.stat().st_mtime_ns,
                }
            )
        totals = {
            "reports": len(report_rows),
            "tests": sum(row["tests"] for row in report_rows),
            "failures": sum(row["failures"] for row in report_rows),
            "errors": sum(row["errors"] for row in report_rows),
            "skipped": sum(row["skipped"] for row in report_rows),
            "testcase_nodes": sum(row["testcase_nodes"] for row in report_rows),
        }
        manifest = {
            "schema_version": 1,
            "kind": contract.raw["evidence_contract"]["run_kind"],
            "status": "passed",
            "contract_sha256": contract.sha256,
            "source_amendment_sha256": contract.amendment_sha256,
            "run_id": outer.context["run_id"],
            "runner": "failsafe",
            "lane": "database-contract-matrix",
            "variant_key": variant_key,
            "db_kind": EXACT_VARIANTS[variant_key],
            "outer_run": _marker_record(outer, outer_copy.name),
            "variant_marker": _marker_record(marker, marker_copy.name),
            "expected_report_count": len(contract.expected[variant_key]),
            "report_count": len(report_rows),
            "reports": report_rows,
            "totals": totals,
            "created_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        }
        write_json(temporary / MANIFEST_NAME, manifest)
        _publish_output(temporary, output)
    except BaseException:
        if temporary.exists():
            shutil.rmtree(temporary)
        raise
    return output / MANIFEST_NAME


@dataclass(frozen=True)
class ValidatedRun:
    variant_key: str
    db_kind: str
    manifest_path: Path
    manifest: dict[str, Any]
    report_paths: dict[str, Path]


def _evidence_marker(
    record: Any,
    evidence_root: Path,
    label: str,
) -> tuple[Path, dict[str, Any]]:
    if not isinstance(record, dict):
        fail("E_EVIDENCE_MARKER", f"{label} record is not an object")
    require_exact_fields(record, {"context", "evidence_path", "mtime_ns", "sha256"}, label)
    if (
        not isinstance(record["context"], dict)
        or not isinstance(record["mtime_ns"], int)
        or not re.fullmatch(r"[0-9a-f]{64}", str(record["sha256"]))
    ):
        fail("E_EVIDENCE_MARKER", f"invalid {label} record")
    relative = safe_relative_path(record["evidence_path"], f"{label} evidence path")
    lexical = (evidence_root / relative).absolute()
    if lexical.is_symlink() or not lexical.is_file():
        fail("E_EVIDENCE_MARKER", f"invalid {label} evidence file: {lexical}")
    path = lexical.resolve()
    if not path.is_relative_to(evidence_root.resolve()):
        fail("E_EVIDENCE_MARKER", f"invalid {label} evidence file: {path}")
    context = read_json(path)
    if (
        context != record["context"]
        or path.stat().st_mtime_ns != record["mtime_ns"]
        or sha256_file(path) != record["sha256"]
    ):
        fail("E_EVIDENCE_MARKER", f"{label} evidence differs from manifest: {path}")
    return path, context


def validate_run_manifest(
    manifest_path: Path,
    contract: MatrixContract,
    outer: Marker,
) -> ValidatedRun:
    lexical_manifest = reject_symlink_components(
        manifest_path, outer.run_root, "variant evidence manifest", "E_CROSS_RUN_SPLICE"
    )
    if lexical_manifest.is_symlink() or not lexical_manifest.is_file():
        fail("E_EVIDENCE_SCHEMA", f"manifest is not a regular file: {lexical_manifest}")
    manifest_path = lexical_manifest.resolve()
    manifest = read_json(manifest_path)
    require_exact_fields(
        manifest,
        {
            "schema_version",
            "kind",
            "status",
            "contract_sha256",
            "source_amendment_sha256",
            "run_id",
            "runner",
            "lane",
            "variant_key",
            "db_kind",
            "outer_run",
            "variant_marker",
            "expected_report_count",
            "report_count",
            "reports",
            "totals",
            "created_at",
        },
        "variant evidence manifest",
    )
    if (
        manifest.get("schema_version") != 1
        or manifest.get("kind") != contract.raw["evidence_contract"]["run_kind"]
        or manifest.get("status") != "passed"
        or manifest.get("contract_sha256") != contract.sha256
        or manifest.get("source_amendment_sha256") != contract.amendment_sha256
        or manifest.get("runner") != "failsafe"
        or manifest.get("lane") != "database-contract-matrix"
    ):
        fail("E_EVIDENCE_SCHEMA", f"not a passed matrix run manifest: {manifest_path}")
    _parse_iso_timestamp(manifest["created_at"], "variant manifest created_at", "E_EVIDENCE_SCHEMA")
    evidence_root = manifest_path.parent
    outer_path, outer_context = _evidence_marker(manifest.get("outer_run"), evidence_root, "outer marker")
    outer_record = manifest["outer_run"]
    if (
        outer_context != outer.context
        or outer_record["sha256"] != outer.sha256
        or outer_record["mtime_ns"] != outer.mtime_ns
        or outer_path.read_bytes() != outer.path.read_bytes()
        or manifest.get("run_id") != outer.context["run_id"]
    ):
        fail("E_CROSS_RUN_SPLICE", f"manifest outer tuple differs: {manifest_path}")
    marker_path, _ = _evidence_marker(
        manifest.get("variant_marker"), evidence_root, "variant marker"
    )
    marker = validate_variant_marker(marker_path, outer, contract)
    marker_record = manifest["variant_marker"]
    if marker_record["sha256"] != marker.sha256 or marker_record["mtime_ns"] != marker.mtime_ns:
        fail("E_CROSS_RUN_SPLICE", f"manifest variant marker differs: {manifest_path}")
    variant = manifest.get("variant_key")
    if variant != marker.context["variant_key"]:
        fail("E_CROSS_RUN_SPLICE", f"manifest variant differs from marker: {manifest_path}")
    if variant not in contract.expected or manifest.get("db_kind") != EXACT_VARIANTS[variant]:
        fail("E_VARIANT", f"manifest variant is not expected: {variant}")
    expected = contract.expected[variant]
    rows = manifest.get("reports")
    if not isinstance(rows, list):
        fail("E_EVIDENCE_SCHEMA", f"manifest reports is not a list: {manifest_path}")
    if manifest.get("expected_report_count") != len(expected) or manifest.get("report_count") != len(rows):
        fail("E_EVIDENCE_SCHEMA", f"manifest report count differs: {manifest_path}")
    observed: dict[str, dict[str, Any]] = {}
    report_paths: dict[str, Path] = {}
    for row in rows:
        if not isinstance(row, dict):
            fail("E_EVIDENCE_SCHEMA", f"manifest report row is not an object: {manifest_path}")
        fields = {
            "execution_key",
            "source_id",
            "report_fqcn",
            "raw_report",
            "tests",
            "failures",
            "errors",
            "skipped",
            "testcase_nodes",
            "sha256",
            "source_mtime_ns",
            "evidence_mtime_ns",
        }
        require_exact_fields(row, fields, "manifest report")
        fqcn = row["report_fqcn"]
        if fqcn in observed:
            fail("E_DUPLICATE_REPORT", f"duplicate manifest report identity: {variant}/{fqcn}")
        observed[fqcn] = row
        expected_report = expected.get(fqcn)
        if expected_report is None:
            fail("E_EXTRA_REPORT", f"unexpected manifest report: {variant}/{fqcn}")
        if row["execution_key"] != expected_report.execution_key or row["source_id"] != expected_report.source_id:
            fail("E_EVIDENCE_IDENTITY", f"manifest identity differs: {variant}/{fqcn}")
        relative = safe_relative_path(row["raw_report"], "raw report")
        lexical_raw = (evidence_root / relative).absolute()
        if lexical_raw.is_symlink() or not lexical_raw.is_file():
            fail("E_MISSING_REPORT", f"raw evidence report is missing: {lexical_raw}")
        raw = lexical_raw.resolve()
        if not raw.is_relative_to(evidence_root.resolve()):
            fail("E_MISSING_REPORT", f"raw evidence report escapes its evidence root: {raw}")
        if sha256_file(raw) != row["sha256"]:
            fail("E_EVIDENCE_REPORT", f"raw report hash differs: {raw}")
        mtime_ns = raw.stat().st_mtime_ns
        if row["source_mtime_ns"] != mtime_ns or row["evidence_mtime_ns"] != mtime_ns:
            fail("E_EVIDENCE_MTIME", f"raw report mtime differs: {raw}")
        metrics = parse_report(raw)
        _validate_report(metrics, expected_report, marker.mtime_ns)
        if any(
            row[name] != getattr(metrics, name)
            for name in ("tests", "failures", "errors", "skipped", "testcase_nodes")
        ):
            fail("E_EVIDENCE_REPORT", f"manifest metrics differ from raw XML: {raw}")
        report_paths[fqcn] = raw
    missing = sorted(set(expected) - set(observed))
    if missing:
        fail("E_MISSING_REPORT", f"manifest reports are missing: {variant}/{missing}")
    raw_files = {
        path.resolve()
        for path in (evidence_root / RAW_DIRECTORY).glob("TEST-*.xml")
        if path.is_file()
    }
    if raw_files != set(report_paths.values()):
        if set(report_paths.values()) - raw_files:
            fail("E_MISSING_REPORT", f"raw report set is incomplete: {manifest_path}")
        fail("E_EXTRA_REPORT", f"raw report set contains extras: {manifest_path}")
    totals = {
        "reports": len(rows),
        "tests": sum(row["tests"] for row in rows),
        "failures": sum(row["failures"] for row in rows),
        "errors": sum(row["errors"] for row in rows),
        "skipped": sum(row["skipped"] for row in rows),
        "testcase_nodes": sum(row["testcase_nodes"] for row in rows),
    }
    if manifest.get("totals") != totals:
        fail("E_EVIDENCE_SCHEMA", f"manifest totals differ: {manifest_path}")
    return ValidatedRun(variant, EXACT_VARIANTS[variant], manifest_path, manifest, report_paths)


def finalize_runs(
    contract: MatrixContract,
    outer_marker_path: Path,
    manifest_paths: Sequence[Path],
    cells_root: Path,
    output: Path,
) -> Path:
    outer = validate_outer_marker(outer_marker_path, contract)
    if not manifest_paths:
        fail("E_MISSING_VARIANT", "no variant manifests were supplied")
    runs: dict[str, ValidatedRun] = {}
    for manifest_path in manifest_paths:
        run = validate_run_manifest(manifest_path, contract, outer)
        if run.variant_key in runs:
            fail("E_DUPLICATE_VARIANT", f"duplicate variant manifest: {run.variant_key}")
        runs[run.variant_key] = run
    missing = sorted(set(EXACT_VARIANTS) - set(runs))
    extra = sorted(set(runs) - set(EXACT_VARIANTS))
    if missing:
        fail("E_MISSING_VARIANT", f"variant manifests are missing: {missing}")
    if extra:
        fail("E_EXTRA_VARIANT", f"unexpected variant manifests: {extra}")

    for variant in EXACT_VARIANT_ORDER:
        run = runs[variant]
        expected_manifest = (
            outer.run_root
            / FINAL_VARIANTS_DIRECTORY
            / safe_token(variant, "variant key")
            / "evidence"
            / MANIFEST_NAME
        ).resolve()
        if run.manifest_path != expected_manifest:
            fail(
                "E_EVIDENCE_SCHEMA",
                f"variant source manifest is not at its canonical run path: {run.manifest_path}",
            )

    validated_cells = validate_cells_root(cells_root, outer)

    metric_rows: list[dict[str, Any]] = []
    cells: dict[str, dict[str, int]] = {
        db_kind: {"variants": 0, "reports": 0, "testcase_nodes": 0}
        for db_kind in sorted(EXACT_DB_KINDS)
    }
    for variant in EXACT_VARIANT_ORDER:
        run = runs[variant]
        cells[run.db_kind]["variants"] += 1
        for report in run.manifest["reports"]:
            cells[run.db_kind]["reports"] += 1
            cells[run.db_kind]["testcase_nodes"] += report["testcase_nodes"]
            metric_rows.append(
                {
                    "execution_key": report["execution_key"],
                    "variant_key": variant,
                    "db_kind": run.db_kind,
                    "report_fqcn": report["report_fqcn"],
                    "tests": report["tests"],
                    "failures": report["failures"],
                    "errors": report["errors"],
                    "skipped": report["skipped"],
                    "testcase_nodes": report["testcase_nodes"],
                    "sha256": report["sha256"],
                }
            )
    totals = {
        "database_cells": len(cells),
        "variants": len(runs),
        "reports": len(metric_rows),
        "testcase_nodes": sum(row["testcase_nodes"] for row in metric_rows),
        "failures": sum(row["failures"] for row in metric_rows),
        "errors": sum(row["errors"] for row in metric_rows),
        "skipped": sum(row["skipped"] for row in metric_rows),
    }
    if totals != EXACT_TOTALS:
        fail("E_MATRIX_TOTAL", f"merged matrix totals differ: {totals}")
    for cell_contract in contract.raw["database_cells"]:
        cell = cells[cell_contract["db_kind"]]
        if (
            cell["variants"] != len(cell_contract["variants"])
            or cell["reports"] != cell_contract["expected_reports"]
            or cell["testcase_nodes"] != cell_contract["expected_testcase_nodes"]
        ):
            fail("E_MATRIX_CELL", f"database cell totals differ: {cell_contract['db_kind']}/{cell}")

    output = output.resolve()
    if output.parent != outer.run_root:
        fail("E_RUN_ROOT", f"final output must be a direct child of the run root: {output}")
    output, temporary = _prepare_output(output, outer)
    try:
        outer_copy = temporary / "outer-run-marker.json"
        shutil.copy2(outer.path, outer_copy)
        variants_root = temporary / FINAL_VARIANTS_DIRECTORY
        cells_evidence_root = temporary / FINAL_CELLS_DIRECTORY
        variants_root.mkdir()
        cells_evidence_root.mkdir()
        variant_records: list[dict[str, Any]] = []
        for variant in EXACT_VARIANT_ORDER:
            run = runs[variant]
            token = safe_token(variant, "variant key")
            source_root = run.manifest_path.parent
            expected_files = {
                Path(MANIFEST_NAME),
                safe_relative_path(
                    run.manifest["outer_run"]["evidence_path"], "source outer marker"
                ),
                safe_relative_path(
                    run.manifest["variant_marker"]["evidence_path"], "source variant marker"
                ),
            }
            expected_files.update(
                safe_relative_path(row["raw_report"], "source raw report")
                for row in run.manifest["reports"]
            )
            ordered_files = _assert_exact_tree(
                source_root,
                expected_files,
                f"{variant} source evidence",
                "E_FINAL_BUNDLE",
            )
            final_variant_root = variants_root / token
            _copy_exact_tree(
                source_root,
                final_variant_root,
                ordered_files,
                f"{variant} evidence subtree",
            )
            file_records = [
                _artifact_record(final_variant_root / relative, temporary)
                for relative in ordered_files
            ]
            source_manifest_stat = run.manifest_path.stat()
            final_manifest_path = final_variant_root / MANIFEST_NAME
            final_manifest_stat = final_manifest_path.stat()
            source_manifest_record = {
                "source_path": run.manifest_path.relative_to(outer.run_root).as_posix(),
                "source_sha256": sha256_file(run.manifest_path),
                "source_mtime_ns": source_manifest_stat.st_mtime_ns,
                "source_size_bytes": source_manifest_stat.st_size,
                "final_path": final_manifest_path.relative_to(temporary).as_posix(),
                "final_sha256": sha256_file(final_manifest_path),
                "final_mtime_ns": final_manifest_stat.st_mtime_ns,
                "final_size_bytes": final_manifest_stat.st_size,
            }
            report_records: list[dict[str, Any]] = []
            for source_row in run.manifest["reports"]:
                relative = safe_relative_path(source_row["raw_report"], "source raw report")
                report_records.append(
                    {
                        "execution_key": source_row["execution_key"],
                        "source_id": source_row["source_id"],
                        "report_fqcn": source_row["report_fqcn"],
                        "source_raw_report": source_row["raw_report"],
                        "raw_report": _artifact_record(final_variant_root / relative, temporary),
                        "tests": source_row["tests"],
                        "failures": source_row["failures"],
                        "errors": source_row["errors"],
                        "skipped": source_row["skipped"],
                        "testcase_nodes": source_row["testcase_nodes"],
                    }
                )
            variant_records.append(
                {
                    "variant_key": variant,
                    "db_kind": run.db_kind,
                    "evidence_root": final_variant_root.relative_to(temporary).as_posix(),
                    "source_manifest": source_manifest_record,
                    "files": file_records,
                    "tree_sha256": _artifact_tree_sha256(file_records),
                    "reports": report_records,
                    "totals": run.manifest["totals"],
                }
            )

        cell_records: list[dict[str, Any]] = []
        cell_contracts = {row["db_kind"]: row for row in contract.raw["database_cells"]}
        for db_kind in EXACT_DB_ORDER:
            cell = validated_cells[db_kind]
            final_cell_root = cells_evidence_root / db_kind
            _copy_exact_tree(
                cell.root,
                final_cell_root,
                list(cell.files),
                f"{db_kind} cell evidence",
            )
            file_records = [
                _artifact_record(final_cell_root / relative, temporary)
                for relative in cell.files
            ]
            cell_metrics = cells[db_kind]
            cell_records.append(
                {
                    "db_kind": db_kind,
                    "status": "passed",
                    "source_evidence_root": cell.source_root.relative_to(outer.run_root).as_posix(),
                    "evidence_root": final_cell_root.relative_to(temporary).as_posix(),
                    "fixture_sha256": cell.fixture_sha256,
                    "variant_keys": list(cell_contracts[db_kind]["variants"]),
                    "files": file_records,
                    "tree_sha256": _artifact_tree_sha256(file_records),
                    "totals": {
                        "variants": cell_metrics["variants"],
                        "reports": cell_metrics["reports"],
                        "testcase_nodes": cell_metrics["testcase_nodes"],
                        "failures": 0,
                        "errors": 0,
                        "skipped": 0,
                    },
                }
            )
        write_tsv(temporary / "report-metrics.tsv", METRICS_HEADER, metric_rows)
        manifest = {
            "schema_version": 1,
            "kind": contract.raw["evidence_contract"]["merged_kind"],
            "status": "passed",
            "contract_sha256": contract.sha256,
            "source_amendment_sha256": contract.amendment_sha256,
            "run_id": outer.context["run_id"],
            "runner": "failsafe",
            "lane": "database-contract-matrix",
            "outer_run": _marker_record(outer, outer_copy.name),
            "variants": variant_records,
            "database_cells": cell_records,
            "totals": totals,
            "metrics": _artifact_record(temporary / "report-metrics.tsv", temporary),
            "created_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        }
        write_json(temporary / MANIFEST_NAME, manifest)
        verify_final(contract, outer.path, temporary / MANIFEST_NAME)
        _publish_output(temporary, output)
    except BaseException:
        if temporary.exists():
            shutil.rmtree(temporary)
        raise
    return output / MANIFEST_NAME


def _final_source_manifest_path(record: Any, bundle_root: Path, variant: str) -> Path:
    fields = {
        "source_path",
        "source_sha256",
        "source_mtime_ns",
        "source_size_bytes",
        "final_path",
        "final_sha256",
        "final_mtime_ns",
        "final_size_bytes",
    }
    if not isinstance(record, dict):
        fail("E_FINAL_SCHEMA", f"source manifest record is not an object: {variant}")
    require_exact_fields(record, fields, f"{variant} source manifest")
    expected_source = Path(FINAL_VARIANTS_DIRECTORY) / variant / "evidence" / MANIFEST_NAME
    expected_final = Path(FINAL_VARIANTS_DIRECTORY) / variant / MANIFEST_NAME
    if (
        record["source_path"] != expected_source.as_posix()
        or record["final_path"] != expected_final.as_posix()
        or not re.fullmatch(r"[0-9a-f]{64}", str(record["source_sha256"]))
        or not re.fullmatch(r"[0-9a-f]{64}", str(record["final_sha256"]))
        or any(
            not isinstance(record[name], int) or record[name] < 0
            for name in (
                "source_mtime_ns",
                "source_size_bytes",
                "final_mtime_ns",
                "final_size_bytes",
            )
        )
        or record["source_sha256"] != record["final_sha256"]
        or record["source_mtime_ns"] != record["final_mtime_ns"]
        or record["source_size_bytes"] != record["final_size_bytes"]
    ):
        fail("E_FINAL_SCHEMA", f"source/final manifest binding differs: {variant}")
    artifact = {
        "path": record["final_path"],
        "sha256": record["final_sha256"],
        "mtime_ns": record["final_mtime_ns"],
        "size_bytes": record["final_size_bytes"],
    }
    return _validate_artifact_record(artifact, bundle_root, f"{variant} source manifest")


def _validate_final_file_records(
    records: Any,
    bundle_root: Path,
    evidence_root: Path,
    label: str,
) -> dict[str, dict[str, Any]]:
    if not isinstance(records, list) or not records:
        fail("E_FINAL_SCHEMA", f"{label} files must be a non-empty list")
    observed: dict[str, dict[str, Any]] = {}
    for record in records:
        path = _validate_artifact_record(record, bundle_root, f"{label} file")
        relative = path.relative_to(bundle_root.resolve()).as_posix()
        if relative in observed or not path.is_relative_to(evidence_root.resolve()):
            fail("E_FINAL_SCHEMA", f"duplicate or escaped {label} file: {relative}")
        observed[relative] = record
    if list(observed) != sorted(observed):
        fail("E_FINAL_SCHEMA", f"{label} file records are not path-sorted")
    expected_relative = {
        Path(path).relative_to(evidence_root.relative_to(bundle_root.resolve()))
        for path in observed
    }
    _assert_exact_tree(evidence_root, expected_relative, label, "E_FINAL_BUNDLE")
    return observed


def verify_final(
    contract: MatrixContract,
    outer_marker_path: Path,
    manifest_path: Path,
) -> dict[str, Any]:
    outer = validate_outer_marker(outer_marker_path, contract)
    lexical_manifest = reject_symlink_components(
        manifest_path, outer.run_root, "final manifest", "E_FINAL_SCHEMA"
    )
    if lexical_manifest.is_symlink() or not lexical_manifest.is_file():
        fail("E_FINAL_SCHEMA", f"final manifest is not a regular file: {lexical_manifest}")
    manifest_path = lexical_manifest.resolve()
    bundle_root = manifest_path.parent
    if manifest_path.name != MANIFEST_NAME or not bundle_root.is_relative_to(outer.run_root):
        fail("E_FINAL_SCHEMA", f"final manifest is outside the current run: {manifest_path}")
    manifest = read_json(manifest_path)
    require_exact_fields(
        manifest,
        {
            "schema_version",
            "kind",
            "status",
            "contract_sha256",
            "source_amendment_sha256",
            "run_id",
            "runner",
            "lane",
            "outer_run",
            "variants",
            "database_cells",
            "totals",
            "metrics",
            "created_at",
        },
        "final manifest",
    )
    if (
        manifest["schema_version"] != 1
        or manifest["kind"] != contract.raw["evidence_contract"]["merged_kind"]
        or manifest["status"] != "passed"
        or manifest["contract_sha256"] != contract.sha256
        or manifest["source_amendment_sha256"] != contract.amendment_sha256
        or manifest["run_id"] != outer.context["run_id"]
        or manifest["runner"] != "failsafe"
        or manifest["lane"] != "database-contract-matrix"
    ):
        fail("E_FINAL_SCHEMA", "final manifest provenance differs")
    _parse_iso_timestamp(manifest["created_at"], "final manifest created_at", "E_FINAL_SCHEMA")
    outer_copy, outer_context = _evidence_marker(
        manifest["outer_run"], bundle_root, "final outer marker"
    )
    if (
        outer_context != outer.context
        or manifest["outer_run"]["sha256"] != outer.sha256
        or manifest["outer_run"]["mtime_ns"] != outer.mtime_ns
        or outer_copy.read_bytes() != outer.path.read_bytes()
    ):
        fail("E_CROSS_RUN_SPLICE", "final outer marker differs from current run")

    variant_rows = manifest["variants"]
    if not isinstance(variant_rows, list) or [row.get("variant_key") for row in variant_rows if isinstance(row, dict)] != list(EXACT_VARIANT_ORDER):
        fail("E_FINAL_SCHEMA", "final variant set or order differs")
    final_reports: list[dict[str, Any]] = []
    final_file_paths: set[Path] = {Path(MANIFEST_NAME), Path(manifest["outer_run"]["evidence_path"])}
    variant_totals: dict[str, dict[str, int]] = {}
    for row in variant_rows:
        require_exact_fields(
            row,
            {
                "variant_key",
                "db_kind",
                "evidence_root",
                "source_manifest",
                "files",
                "tree_sha256",
                "reports",
                "totals",
            },
            "final variant",
        )
        variant = row["variant_key"]
        expected_root_relative = Path(FINAL_VARIANTS_DIRECTORY) / variant
        if (
            row["db_kind"] != EXACT_VARIANTS[variant]
            or row["evidence_root"] != expected_root_relative.as_posix()
            or not re.fullmatch(r"[0-9a-f]{64}", str(row["tree_sha256"]))
        ):
            fail("E_FINAL_SCHEMA", f"final variant identity differs: {variant}")
        evidence_root = (bundle_root / expected_root_relative).resolve()
        final_manifest_path = _final_source_manifest_path(
            row["source_manifest"], bundle_root, variant
        )
        source_run = validate_run_manifest(final_manifest_path, contract, outer)
        if source_run.variant_key != variant:
            fail("E_CROSS_RUN_SPLICE", f"final source manifest variant differs: {variant}")
        file_records = _validate_final_file_records(
            row["files"], bundle_root, evidence_root, f"{variant} evidence"
        )
        expected_variant_files = {
            (expected_root_relative / MANIFEST_NAME).as_posix(),
            (
                expected_root_relative
                / safe_relative_path(
                    source_run.manifest["outer_run"]["evidence_path"],
                    "final source outer marker",
                )
            ).as_posix(),
            (
                expected_root_relative
                / safe_relative_path(
                    source_run.manifest["variant_marker"]["evidence_path"],
                    "final source variant marker",
                )
            ).as_posix(),
        }
        expected_variant_files.update(
            (
                expected_root_relative
                / safe_relative_path(source_row["raw_report"], "final source raw report")
            ).as_posix()
            for source_row in source_run.manifest["reports"]
        )
        if set(file_records) != expected_variant_files:
            fail("E_FINAL_SCHEMA", f"final variant file contract differs: {variant}")
        if _artifact_tree_sha256(row["files"]) != row["tree_sha256"]:
            fail("E_FINAL_BUNDLE", f"variant evidence tree binding differs: {variant}")
        final_file_paths.update(Path(path) for path in file_records)
        manifest_artifact = {
            "path": row["source_manifest"]["final_path"],
            "sha256": row["source_manifest"]["final_sha256"],
            "mtime_ns": row["source_manifest"]["final_mtime_ns"],
            "size_bytes": row["source_manifest"]["final_size_bytes"],
        }
        if file_records.get(manifest_artifact["path"]) != manifest_artifact:
            fail("E_FINAL_SCHEMA", f"source manifest is not bound by variant files: {variant}")
        report_rows = row["reports"]
        if not isinstance(report_rows, list) or len(report_rows) != len(source_run.manifest["reports"]):
            fail("E_FINAL_SCHEMA", f"final report list differs: {variant}")
        expected_report_fields = {
            "execution_key",
            "source_id",
            "report_fqcn",
            "source_raw_report",
            "raw_report",
            "tests",
            "failures",
            "errors",
            "skipped",
            "testcase_nodes",
        }
        for report_row, source_row in zip(report_rows, source_run.manifest["reports"]):
            if not isinstance(report_row, dict):
                fail("E_FINAL_SCHEMA", f"final report row is not an object: {variant}")
            require_exact_fields(report_row, expected_report_fields, "final raw report")
            for field in (
                "execution_key",
                "source_id",
                "report_fqcn",
                "tests",
                "failures",
                "errors",
                "skipped",
                "testcase_nodes",
            ):
                if report_row[field] != source_row[field]:
                    fail("E_EVIDENCE_REPORT", f"final/source report tuple differs: {variant}/{field}")
            if report_row["source_raw_report"] != source_row["raw_report"]:
                fail("E_EVIDENCE_REPORT", f"final/source report path differs: {variant}")
            raw = _validate_artifact_record(
                report_row["raw_report"], bundle_root, f"{variant} raw report", "E_EVIDENCE_REPORT"
            )
            expected_raw = evidence_root / safe_relative_path(
                source_row["raw_report"], "source raw report"
            )
            raw_relative = raw.relative_to(bundle_root).as_posix()
            if raw != expected_raw or file_records.get(raw_relative) != report_row["raw_report"]:
                fail("E_EVIDENCE_REPORT", f"raw report is not bound by variant files: {raw}")
            final_reports.append({**report_row, "variant_key": variant, "db_kind": row["db_kind"]})
        if row["totals"] != source_run.manifest["totals"]:
            fail("E_FINAL_SCHEMA", f"final/source variant totals differ: {variant}")
        variant_totals[variant] = row["totals"]

    cell_rows = manifest["database_cells"]
    if not isinstance(cell_rows, list) or [row.get("db_kind") for row in cell_rows if isinstance(row, dict)] != list(EXACT_DB_ORDER):
        fail("E_FINAL_SCHEMA", "final cell set or order differs")
    contract_cells = {row["db_kind"]: row for row in contract.raw["database_cells"]}
    for row in cell_rows:
        require_exact_fields(
            row,
            {
                "db_kind",
                "status",
                "source_evidence_root",
                "evidence_root",
                "fixture_sha256",
                "variant_keys",
                "files",
                "tree_sha256",
                "totals",
            },
            "final database cell",
        )
        db_kind = row["db_kind"]
        expected_relative = Path(FINAL_CELLS_DIRECTORY) / db_kind
        if (
            row["status"] != "passed"
            or row["source_evidence_root"] != expected_relative.as_posix()
            or row["evidence_root"] != expected_relative.as_posix()
            or row["variant_keys"] != contract_cells[db_kind]["variants"]
            or not re.fullmatch(r"[0-9a-f]{64}", str(row["fixture_sha256"]))
            or not re.fullmatch(r"[0-9a-f]{64}", str(row["tree_sha256"]))
        ):
            fail("E_FINAL_SCHEMA", f"final cell identity differs: {db_kind}")
        evidence_root = (bundle_root / expected_relative).resolve()
        file_records = _validate_final_file_records(
            row["files"], bundle_root, evidence_root, f"{db_kind} final cell"
        )
        if _artifact_tree_sha256(row["files"]) != row["tree_sha256"]:
            fail("E_CELL_EVIDENCE", f"final cell tree binding differs: {db_kind}")
        final_file_paths.update(Path(path) for path in file_records)
        source_root = outer.run_root / safe_relative_path(
            row["source_evidence_root"], "cell source root"
        )
        if db_kind == "sqlite":
            cell = _validate_sqlite_cell(evidence_root, source_root, outer)
        else:
            cell = _validate_external_cell(db_kind, evidence_root, source_root, outer)
        if cell.fixture_sha256 != row["fixture_sha256"]:
            fail("E_CELL_EVIDENCE", f"final cell fixture binding differs: {db_kind}")
        expected_cell_totals = {
            "variants": len(row["variant_keys"]),
            "reports": sum(variant_totals[variant]["reports"] for variant in row["variant_keys"]),
            "testcase_nodes": sum(
                variant_totals[variant]["testcase_nodes"] for variant in row["variant_keys"]
            ),
            "failures": sum(variant_totals[variant]["failures"] for variant in row["variant_keys"]),
            "errors": sum(variant_totals[variant]["errors"] for variant in row["variant_keys"]),
            "skipped": sum(variant_totals[variant]["skipped"] for variant in row["variant_keys"]),
        }
        if row["totals"] != expected_cell_totals:
            fail("E_MATRIX_CELL", f"final cell totals differ: {db_kind}")

    metrics_path = _validate_artifact_record(manifest["metrics"], bundle_root, "final metrics")
    if manifest["metrics"]["path"] != "report-metrics.tsv":
        fail("E_FINAL_SCHEMA", "final metrics path differs")
    final_file_paths.add(Path(manifest["metrics"]["path"]))
    metric_rows = read_tsv(metrics_path, METRICS_HEADER)
    expected_metrics = [
        {
            "execution_key": row["execution_key"],
            "variant_key": row["variant_key"],
            "db_kind": row["db_kind"],
            "report_fqcn": row["report_fqcn"],
            "tests": str(row["tests"]),
            "failures": str(row["failures"]),
            "errors": str(row["errors"]),
            "skipped": str(row["skipped"]),
            "testcase_nodes": str(row["testcase_nodes"]),
            "sha256": row["raw_report"]["sha256"],
        }
        for row in final_reports
    ]
    if metric_rows != expected_metrics:
        fail("E_FINAL_BUNDLE", "final metrics table differs from bound raw reports")
    totals = {
        "database_cells": len(cell_rows),
        "variants": len(variant_rows),
        "reports": len(final_reports),
        "testcase_nodes": sum(row["testcase_nodes"] for row in final_reports),
        "failures": sum(row["failures"] for row in final_reports),
        "errors": sum(row["errors"] for row in final_reports),
        "skipped": sum(row["skipped"] for row in final_reports),
    }
    if totals != EXACT_TOTALS or manifest["totals"] != totals:
        fail("E_MATRIX_TOTAL", f"final matrix totals differ: {totals}")
    _assert_exact_tree(bundle_root, final_file_paths, "final bundle", "E_FINAL_BUNDLE")
    return manifest


def _fixture_outer_marker(
    root: Path,
    contract: MatrixContract,
    run_id: str,
    mtime_ns: int,
) -> Path:
    root.mkdir(parents=True, exist_ok=True)
    marker = root / "outer-run-marker.json"
    write_json(
        marker,
        {
            "schema_version": 1,
            "kind": contract.raw["evidence_contract"]["outer_marker_kind"],
            "run_id": run_id,
            "lane": "database-contract-matrix",
            "runner": "failsafe",
            "git_head": current_git_head(contract.repo),
            "contract_sha256": contract.sha256,
            "source_amendment_sha256": contract.amendment_sha256,
            "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "status": "started",
        },
    )
    os.utime(marker, ns=(mtime_ns, mtime_ns))
    return marker


def _fixture_variant_marker(
    root: Path,
    contract: MatrixContract,
    outer_path: Path,
    variant: str,
    mtime_ns: int,
) -> Path:
    outer_context = read_json(outer_path)
    root.mkdir(parents=True, exist_ok=True)
    marker = root / "run-marker.json"
    write_json(
        marker,
        {
            "schema_version": 1,
            "kind": contract.raw["evidence_contract"]["variant_marker_kind"],
            "run_id": outer_context["run_id"],
            "lane": "database-contract-matrix",
            "runner": "failsafe",
            "git_head": outer_context["git_head"],
            "contract_sha256": contract.sha256,
            "source_amendment_sha256": contract.amendment_sha256,
            "started_at": dt.datetime.now(dt.timezone.utc).isoformat(),
            "status": "started",
            "variant_key": variant,
            "db_kind": EXACT_VARIANTS[variant],
            "outer_marker_sha256": sha256_file(outer_path),
        },
    )
    os.utime(marker, ns=(mtime_ns, mtime_ns))
    return marker


def _write_fixture_report(
    path: Path,
    fqcn: str,
    nodes: int,
    mtime_ns: int,
    *,
    failures: int = 0,
    errors: int = 0,
    skipped: int = 0,
) -> None:
    root = ET.Element(
        "testsuite",
        {
            "name": fqcn,
            "tests": str(nodes),
            "failures": str(failures),
            "errors": str(errors),
            "skipped": str(skipped),
        },
    )
    for index in range(nodes):
        testcase = ET.SubElement(root, "testcase", {"classname": fqcn, "name": f"test-{index}"})
        if index == 0 and failures:
            ET.SubElement(testcase, "failure", {"message": "negative probe"})
        if index == 0 and errors:
            ET.SubElement(testcase, "error", {"message": "negative probe"})
        if index == 0 and skipped:
            ET.SubElement(testcase, "skipped")
    ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)
    os.utime(path, ns=(mtime_ns, mtime_ns))


def _fixture_reports(
    root: Path,
    contract: MatrixContract,
    variant: str,
    mtime_ns: int,
) -> Path:
    reports = root / f"reports-{safe_token(variant, 'variant')}"
    reports.mkdir(parents=True)
    for expected in contract.expected[variant].values():
        _write_fixture_report(
            reports / f"TEST-{expected.report_fqcn}.xml",
            expected.report_fqcn,
            expected.testcase_nodes,
            mtime_ns,
        )
    return reports


def _write_fixture_env(path: Path, fields: Sequence[str], values: dict[str, str]) -> None:
    if set(values) != set(fields):
        fail("E_NEGATIVE_PROBE", f"fixture env fields differ for {path}")
    path.write_text(
        "".join(f"{field}={values[field]}\n" for field in fields),
        encoding="utf-8",
        newline="\n",
    )


def _fixture_cells(
    run_root: Path,
    outer_path: Path,
    mtime_ns: int,
) -> Path:
    cells_root = run_root / FINAL_CELLS_DIRECTORY
    cells_root.mkdir(parents=True)
    fixture_sha256 = hashlib.sha256(CANONICAL_FIXTURE.encode("utf-8")).hexdigest()
    outer_context = read_json(outer_path)
    outer_started = _parse_iso_timestamp(
        outer_context["started_at"], "fixture outer started_at", "E_NEGATIVE_PROBE"
    )
    started_at = outer_started + dt.timedelta(seconds=1)
    volume_created = started_at + dt.timedelta(seconds=1)
    finished_at = volume_created + dt.timedelta(seconds=1)

    sqlite_root = cells_root / "sqlite"
    sqlite_root.mkdir()
    for name in ("fixture-first.txt", "fixture-before.txt", "fixture-after.txt"):
        (sqlite_root / name).write_text(CANONICAL_FIXTURE, encoding="utf-8", newline="\n")
    sqlite_database = sqlite_root / "database.sqlite"
    _write_fixture_env(
        sqlite_root / "resource.env",
        SQLITE_RESOURCE_FIELDS,
        {
            "database": "sqlite",
            "database_file": str(sqlite_database),
            "jdbc_url": f"jdbc:sqlite:{sqlite_database}",
            "sqlite_python_version": "3.42.0",
            "sqlite_jdbc_jar_before_sha256": SQLITE_JAR_SHA256,
            "fixture_before_sha256": fixture_sha256,
            "status": "prepared",
        },
    )
    _write_fixture_env(
        sqlite_root / "verification.env",
        SQLITE_VERIFICATION_FIELDS,
        {
            "database": "sqlite",
            "sqlite_jdbc_jar_after_sha256": SQLITE_JAR_SHA256,
            "fixture_before_sha256": fixture_sha256,
            "fixture_after_sha256": fixture_sha256,
            "status": "passed",
        },
    )
    _write_fixture_env(
        sqlite_root / "cleanup.env",
        SQLITE_CLEANUP_FIELDS,
        {"database": "sqlite", "status": "passed"},
    )

    identities = {
        "mysql57": "foggy_test|5.7.44-log",
        "mysql8": "foggy_test|8.0.39",
        "postgres15": "foggy_test|public|15.8 (Debian)",
        "sqlserver2022": "foggy_test|dbo|16.0.1000.6",
    }
    for db_kind in EXACT_DB_ORDER[1:]:
        cell_root = cells_root / db_kind
        cell_root.mkdir()
        expected = EXTERNAL_CELL_CONTRACTS[db_kind]
        project = _external_project(outer_context["run_id"], db_kind)
        resource_token = expected["resource"]
        container = f"{project}-{resource_token}"
        volume = f"{project}-{resource_token}-data"
        network = f"{project}-network"
        identity = identities[db_kind]
        for name in ("fixture-first.txt", "fixture-before.txt", "fixture-after.txt"):
            (cell_root / name).write_text(CANONICAL_FIXTURE, encoding="utf-8", newline="\n")
        (cell_root / "database-identity.txt").write_text(
            f"{identity}\n", encoding="utf-8", newline="\n"
        )
        _write_fixture_env(
            cell_root / "resource.env",
            EXTERNAL_RESOURCE_FIELDS,
            {
                "run_id": outer_context["run_id"],
                "database": db_kind,
                "service": expected["service"],
                "project": project,
                "container": container,
                "volume": volume,
                "network": network,
                "host_port": expected["host_port"],
                "container_port": expected["container_port"],
                "profile": expected["profile"],
                "expected_image_ref": expected["image_ref"],
                "expected_image_id": expected["image_id"],
            },
        )
        _write_fixture_env(
            cell_root / "runtime.env",
            EXTERNAL_RUNTIME_FIELDS,
            {
                "database": db_kind,
                "actual_image_id": expected["image_id"],
                "actual_image_ref": expected["image_ref"],
                "actual_repo_digest": expected["image_ref"],
                "actual_project": project,
                "actual_service": expected["service"],
                "actual_mapped_port": f"127.0.0.1:{expected['host_port']}",
                "volume_project": project,
                "network_project": project,
                "volume_created": volume_created.isoformat(),
                "database_identity": identity,
                "status": "verified",
            },
        )
        _write_fixture_env(
            cell_root / "cleanup.env",
            EXTERNAL_CLEANUP_FIELDS,
            {
                "database": db_kind,
                "project": project,
                "container": container,
                "volume": volume,
                "network": network,
                "status": "passed",
            },
        )
        _write_fixture_env(
            cell_root / "status.env",
            EXTERNAL_STATUS_FIELDS,
            {
                "run_id": outer_context["run_id"],
                "database": db_kind,
                "project": project,
                "started_at": started_at.isoformat(),
                "finished_at": finished_at.isoformat(),
                "last_phase": "completed",
                "exit_code": "0",
                "cleanup_status": "passed",
                "fixture_before_sha256": fixture_sha256,
                "fixture_after_sha256": fixture_sha256,
                "status": "passed",
            },
        )
    for path in cells_root.rglob("*"):
        if path.is_file():
            os.utime(path, ns=(mtime_ns, mtime_ns))
    return cells_root


def _expect_error(probe: str, expected_code: str, action: Any) -> dict[str, str]:
    try:
        action()
    except MatrixError as exc:
        if exc.code != expected_code:
            fail("E_NEGATIVE_PROBE", f"{probe} returned {exc.code}, expected {expected_code}")
        return {
            "probe": probe,
            "expected_error": expected_code,
            "actual_error": exc.code,
            "status": "passed",
        }
    fail("E_NEGATIVE_PROBE", f"{probe} unexpectedly passed")


def run_negative_probes(contract: MatrixContract) -> list[dict[str, str]]:
    with tempfile.TemporaryDirectory(prefix="v934-step3-db-negative-") as temporary_name:
        temporary = Path(temporary_name)
        primary = temporary / "primary"
        base_ns = time.time_ns() - 30_000_000_000
        outer_path = _fixture_outer_marker(primary, contract, "negative-primary", base_ns)
        markers: dict[str, Path] = {}
        report_dirs: dict[str, Path] = {}
        manifests: dict[str, Path] = {}
        for index, variant in enumerate(EXACT_VARIANT_ORDER, start=1):
            marker_ns = base_ns + index * 100_000_000
            variant_root = primary / FINAL_VARIANTS_DIRECTORY / variant
            markers[variant] = _fixture_variant_marker(
                variant_root, contract, outer_path, variant, marker_ns
            )
            report_dirs[variant] = _fixture_reports(
                primary / "report-inputs",
                contract,
                variant,
                marker_ns + 10_000_000,
            )
            manifests[variant] = collect_variant(
                contract,
                variant,
                report_dirs[variant],
                outer_path,
                markers[variant],
                variant_root / "evidence",
            )
        cells_root = _fixture_cells(primary, outer_path, base_ns + 2_000_000_000)
        final_manifest = finalize_runs(
            contract,
            outer_path,
            list(manifests.values()),
            cells_root,
            primary / "final",
        )
        verify_final(contract, outer_path, final_manifest)

        probe_variant = "db-sqlite"
        probe_marker_ns = markers[probe_variant].stat().st_mtime_ns
        probe_report_ns = probe_marker_ns + 20_000_000

        def fresh_probe_reports(probe: str) -> Path:
            return _fixture_reports(
                primary / f"probe-{probe}",
                contract,
                probe_variant,
                probe_report_ns,
            )

        results: list[dict[str, str]] = []
        reports = fresh_probe_reports("missing")
        next(iter(sorted(reports.glob("TEST-*.xml")))).unlink()
        results.append(
            _expect_error(
                "missing-report",
                "E_MISSING_REPORT",
                lambda: collect_variant(
                    contract, probe_variant, reports, outer_path, markers[probe_variant], primary / "out-missing"
                ),
            )
        )

        reports = fresh_probe_reports("extra")
        _write_fixture_report(reports / "TEST-example.ExtraIT.xml", "example.ExtraIT", 1, probe_report_ns)
        results.append(
            _expect_error(
                "extra-report",
                "E_EXTRA_REPORT",
                lambda: collect_variant(
                    contract, probe_variant, reports, outer_path, markers[probe_variant], primary / "out-extra"
                ),
            )
        )

        reports = fresh_probe_reports("duplicate")
        victim = next(iter(contract.expected[probe_variant].values()))
        _write_fixture_report(
            reports / "TEST-duplicate.xml",
            victim.report_fqcn,
            victim.testcase_nodes,
            probe_report_ns,
        )
        results.append(
            _expect_error(
                "duplicate-report-identity",
                "E_DUPLICATE_REPORT",
                lambda: collect_variant(
                    contract, probe_variant, reports, outer_path, markers[probe_variant], primary / "out-duplicate"
                ),
            )
        )

        reports = fresh_probe_reports("count")
        victim = next(iter(contract.expected[probe_variant].values()))
        _write_fixture_report(
            reports / f"TEST-{victim.report_fqcn}.xml",
            victim.report_fqcn,
            max(0, victim.testcase_nodes - 1),
            probe_report_ns,
        )
        results.append(
            _expect_error(
                "wrong-test-count",
                "E_REPORT_COUNT",
                lambda: collect_variant(
                    contract, probe_variant, reports, outer_path, markers[probe_variant], primary / "out-count"
                ),
            )
        )

        for probe, field in (
            ("failure-outcome", "failures"),
            ("error-outcome", "errors"),
            ("skipped-outcome", "skipped"),
        ):
            reports = fresh_probe_reports(probe)
            victim = next(iter(contract.expected[probe_variant].values()))
            kwargs = {field: 1}
            _write_fixture_report(
                reports / f"TEST-{victim.report_fqcn}.xml",
                victim.report_fqcn,
                victim.testcase_nodes,
                probe_report_ns,
                **kwargs,
            )
            results.append(
                _expect_error(
                    probe,
                    "E_REPORT_OUTCOME",
                    lambda reports=reports, probe=probe: collect_variant(
                        contract,
                        probe_variant,
                        reports,
                        outer_path,
                        markers[probe_variant],
                        primary / f"out-{probe}",
                    ),
                )
            )

        reports = fresh_probe_reports("stale")
        stale = next(iter(sorted(reports.glob("TEST-*.xml"))))
        os.utime(stale, ns=(probe_marker_ns, probe_marker_ns))
        results.append(
            _expect_error(
                "stale-report",
                "E_STALE_REPORT",
                lambda: collect_variant(
                    contract, probe_variant, reports, outer_path, markers[probe_variant], primary / "out-stale"
                ),
            )
        )

        manifest_list = list(manifests.values())
        results.append(
            _expect_error(
                "duplicate-variant-manifest",
                "E_DUPLICATE_VARIANT",
                lambda: finalize_runs(
                    contract,
                    outer_path,
                    manifest_list + [manifest_list[0]],
                    cells_root,
                    primary / "out-duplicate-variant",
                ),
            )
        )
        results.append(
            _expect_error(
                "missing-variant-manifest",
                "E_MISSING_VARIANT",
                lambda: finalize_runs(
                    contract,
                    outer_path,
                    manifest_list[:-1],
                    cells_root,
                    primary / "out-missing-variant",
                ),
            )
        )

        secondary = temporary / "secondary"
        secondary_outer = _fixture_outer_marker(
            secondary, contract, "negative-secondary", base_ns + 5_000_000
        )
        secondary_variant_root = secondary / FINAL_VARIANTS_DIRECTORY / probe_variant
        secondary_marker = _fixture_variant_marker(
            secondary_variant_root,
            contract,
            secondary_outer,
            probe_variant,
            probe_marker_ns,
        )
        primary_outer = validate_outer_marker(outer_path, contract)
        results.append(
            _expect_error(
                "cross-run-marker-splice",
                "E_CROSS_RUN_SPLICE",
                lambda: validate_variant_marker(secondary_marker, primary_outer, contract),
            )
        )
        secondary_reports = _fixture_reports(
            secondary,
            contract,
            probe_variant,
            probe_report_ns,
        )
        secondary_manifest = collect_variant(
            contract,
            probe_variant,
            secondary_reports,
            secondary_outer,
            secondary_marker,
            secondary_variant_root / "evidence",
        )
        spliced_manifests = [
            secondary_manifest if variant == probe_variant else manifests[variant]
            for variant in EXACT_VARIANT_ORDER
        ]
        results.append(
            _expect_error(
                "cross-run-manifest-splice",
                "E_CROSS_RUN_SPLICE",
                lambda: finalize_runs(
                    contract,
                    outer_path,
                    spliced_manifests,
                    cells_root,
                    primary / "out-cross-run",
                ),
            )
        )

        final_json = read_json(final_manifest)
        raw_record = final_json["variants"][0]["reports"][0]["raw_report"]
        raw_path = final_manifest.parent / raw_record["path"]
        raw_path.write_bytes(raw_path.read_bytes() + b"\n")
        os.utime(
            raw_path,
            ns=(
                raw_record["mtime_ns"],
                raw_record["mtime_ns"],
            ),
        )
        results.append(
            _expect_error(
                "manifest-report-tamper",
                "E_EVIDENCE_REPORT",
                lambda: verify_final(contract, outer_path, final_manifest),
            )
        )

        successor = contract.raw["bindings"]["successor_hash_manifest"]["path"]
        discovery_path = (contract.repo / successor).resolve().parent / "discovery-inventory.tsv"
        discovery_rows = read_tsv(discovery_path, DISCOVERY_HEADER)
        drifted = [dict(row) for row in contract.amendments]
        drifted[0]["amended_source_sha256"] = "0" * 64
        results.append(
            _expect_error(
                "source-amendment-drift",
                "E_SOURCE_AMENDMENT",
                lambda: _validate_source_amendments(
                    contract.repo,
                    contract.expected,
                    discovery_rows,
                    drifted,
                ),
            )
        )
        if [(row["probe"], row["expected_error"]) for row in results] != EXACT_NEGATIVE_PROBES:
            fail("E_NEGATIVE_PROBE", "negative probe execution set differs")
        return results


def command_validate(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract)
    print(
        json.dumps(
            {
                "status": "passed",
                "contract_sha256": contract.sha256,
                "source_amendment_sha256": contract.amendment_sha256,
                "database_cells": 5,
                "variants": 7,
                "reports": 29,
                "testcase_nodes": 370,
            },
            sort_keys=True,
        )
    )


def command_source_hash(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract)
    print(combined_protected_source_hash(contract.repo, contract.protected_trees))


def command_collect(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract)
    manifest = collect_variant(
        contract,
        args.variant,
        args.reports_dir,
        args.outer_marker,
        args.run_marker,
        args.output,
    )
    print(f"{PREFIX} collected {args.variant}: {manifest}")


def command_finalize(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract)
    manifest = finalize_runs(
        contract,
        args.outer_marker,
        args.manifest,
        args.cells_root,
        args.output,
    )
    print(f"{PREFIX} finalized reports=29 testcase_nodes=370 F0/E0/S0: {manifest}")


def command_verify_final(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract)
    manifest = verify_final(contract, args.outer_marker, args.manifest)
    totals = manifest["totals"]
    print(
        f"{PREFIX} verified final reports={totals['reports']} "
        f"testcase_nodes={totals['testcase_nodes']} F0/E0/S0: {args.manifest}"
    )


def command_negative(args: argparse.Namespace) -> None:
    contract = load_contract(args.repo_root, args.contract)
    results = run_negative_probes(contract)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        write_tsv(
            args.output,
            ["probe", "expected_error", "actual_error", "status"],
            results,
        )
    print(f"{PREFIX} negative probes passed: {len(results)}/{len(EXACT_NEGATIVE_PROBES)}")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, default=DEFAULT_REPO)
    parser.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser("validate", help="validate frozen contract and source amendment")
    validate_parser.set_defaults(func=command_validate)

    source_hash_parser = subparsers.add_parser(
        "source-hash", help="hash all protected source/resource trees"
    )
    source_hash_parser.set_defaults(func=command_source_hash)

    collect_parser = subparsers.add_parser("collect", help="collect one fresh exact variant report set")
    collect_parser.add_argument("--variant", required=True)
    collect_parser.add_argument("--reports-dir", type=Path, required=True)
    collect_parser.add_argument("--outer-marker", type=Path, required=True)
    collect_parser.add_argument("--run-marker", type=Path, required=True)
    collect_parser.add_argument("--output", type=Path, required=True)
    collect_parser.set_defaults(func=command_collect)

    finalize_parser = subparsers.add_parser("finalize", help="merge exactly seven same-run variant manifests")
    finalize_parser.add_argument("--outer-marker", type=Path, required=True)
    finalize_parser.add_argument("--manifest", type=Path, action="append", required=True)
    finalize_parser.add_argument("--cells-root", type=Path, required=True)
    finalize_parser.add_argument("--output", type=Path, required=True)
    finalize_parser.set_defaults(func=command_finalize)

    verify_final_parser = subparsers.add_parser(
        "verify-final", help="verify one exact self-contained final evidence bundle"
    )
    verify_final_parser.add_argument("--outer-marker", type=Path, required=True)
    verify_final_parser.add_argument("--manifest", type=Path, required=True)
    verify_final_parser.set_defaults(func=command_verify_final)

    negative_parser = subparsers.add_parser("negative", help="run synthetic fail-closed self-probes")
    negative_parser.add_argument("--output", type=Path)
    negative_parser.set_defaults(func=command_negative)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        args.repo_root = args.repo_root.resolve()
        args.contract = args.contract.absolute()
        args.func(args)
        return 0
    except MatrixError as exc:
        print(f"{PREFIX} {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
