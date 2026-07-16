#!/usr/bin/env python3
"""Own and verify the run-scoped MySQL 5.7 fixture used by Unit authority."""

from __future__ import annotations

import argparse
import csv
import errno
import hashlib
import io
import json
import os
import re
import shutil
import signal
import stat
import subprocess
import sys
import tempfile
import time
from collections.abc import Mapping
from pathlib import Path, PurePosixPath
from typing import Any, Callable


PREFIX = "[v934-step4-unit-mysql57]"
RUN_ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
SHA256_RE = re.compile(r"[0-9a-f]{64}")
ENV_KEY_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
PORT = 13306
DATABASE = "mysql57"
SERVICE = "mysql"
PROFILE = "docker"
IMAGE_REF = "mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
IMAGE_ID = "sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
DATABASE_IDENTITY = "foggy_test|5.7.44-log"
CANONICAL_FIXTURE_SHA256 = "70b1a5d755bd781004cd35abd8d11525a997b857335165e0b0e2754ae38950cf"
SELF_PATH = Path("scripts/v934/step4/unit_mysql_fixture_tool.py")
PROVISIONER_PATH = Path("scripts/v934/step3/provision-database-cell.sh")
FIXTURE_CONTRACT_PATH = Path("scripts/v934/step4/unit-mysql57-fixture-contract.json")
FIXTURE_CONTRACT_SHA256 = "7aa1e21aef85b51a13aacc8c134a1c363c595deffbfb3acf6aafdb942519b53a"
STEP2_EXECUTION_PATH = Path("scripts/v934/successor/step2/step2-required-execution.tsv")
STEP2_EXECUTION_SHA256 = "42a9467cdbcfbed5ed54d0bdfa276d92daa7fa2c83795cd13a21df931d0fc1d0"
STEP2_DISCOVERY_PATH = Path("scripts/v934/successor/step2/discovery-inventory.tsv")
STEP2_DISCOVERY_SHA256 = "634a5fbc5732676114bb4203498e20c032f8b34e113243705b1306569418404e"
MIGRATION_WORKITEM_PATH = Path("docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md")
MIGRATION_WORKITEM_SHA256 = "f2d83006864fd59687daed8952fd0cdb4625c7e4ea95715391c30a268e006d5c"
DATASOURCE_ADAPTER_PATH = Path("foggy-dataset/src/test/resources/application.yml")
DATASOURCE_ADAPTER_SHA256 = "9500cd4d50930b121a36798857cd0a1cc0c8b2190b0a2fc9ad0ea464394bb256"
REACTOR_FREEZE_PATH = Path("scripts/v934/contract-freeze.json")
REACTOR_FREEZE_SHA256 = "ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876"
FROZEN_REACTOR_SHA256 = "eff20373aa46e0c25747172ce1c2c59630451870335b175406ebedd361bb1809"
UNIT_DATABASE_USER = "v934_unit"
UNIT_DATABASE_PASSWORD = "v934_unit_934"
UNIT_DATABASE_URL = "jdbc:mysql://127.0.0.1:13306/foggy_test?useUnicode=true&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&enabledTLSProtocols=TLSv1.2"
CONNECTION_OBSERVATION_SCOPE = "unit-maven-invocation"
DATASOURCE_ENVIRONMENT = {
    "V934_UNIT_MYSQL57_URL": UNIT_DATABASE_URL,
    "V934_UNIT_MYSQL57_USERNAME": UNIT_DATABASE_USER,
    "V934_UNIT_MYSQL57_PASSWORD": UNIT_DATABASE_PASSWORD,
}
DATASOURCE_ENV_KEYS = tuple(DATASOURCE_ENVIRONMENT)
CELL_FILES = (
    "cleanup.env",
    "database-identity.txt",
    "fixture-after.txt",
    "fixture-before.txt",
    "fixture-first.txt",
    "resource.env",
    "runtime.env",
    "status.env",
    "unit-connection-receipt.json",
    "unit-fixture-after.json",
    "unit-fixture-before.json",
    "unit-fixture-status.env",
)

UNIT_DDL = """\
DROP TABLE IF EXISTS `M_ETL_TEST`;
CREATE TABLE `M_ETL_TEST` (
  `test_id` varchar(190) COLLATE utf8mb4_unicode_ci NOT NULL,
  `c1` varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `c2` varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `c3` int(11) DEFAULT NULL,
  `c4` varchar(88) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `c5` varchar(190) COLLATE utf8mb4_unicode_ci DEFAULT '2',
  PRIMARY KEY (`test_id`),
  KEY `idx_M_ETL_TEST_c3` (`c3`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
"""

SNAPSHOT_SQL = """\
SELECT 'table', LOWER(TABLE_NAME), ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = 'm_etl_test'
ORDER BY LOWER(TABLE_NAME);
SELECT 'column', LPAD(ORDINAL_POSITION, 2, '0'), LOWER(COLUMN_NAME),
       LOWER(COLUMN_TYPE), IS_NULLABLE, IFNULL(COLUMN_DEFAULT, '<NULL>'),
       EXTRA, IFNULL(CHARACTER_SET_NAME, '<NULL>'), IFNULL(COLLATION_NAME, '<NULL>')
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = 'm_etl_test'
ORDER BY ORDINAL_POSITION;
SELECT 'index', LOWER(INDEX_NAME), NON_UNIQUE, SEQ_IN_INDEX, LOWER(COLUMN_NAME)
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = 'm_etl_test'
ORDER BY LOWER(INDEX_NAME), SEQ_IN_INDEX;
SELECT 'rows', COUNT(*) FROM `M_ETL_TEST`;
"""

EXPECTED_SNAPSHOT = {
    "table": {
        "name": "m_etl_test",
        "engine": "InnoDB",
        "collation": "utf8mb4_unicode_ci",
    },
    "columns": [
        ["01", "test_id", "varchar(190)", "NO", "<NULL>", "", "utf8mb4", "utf8mb4_unicode_ci"],
        ["02", "c1", "varchar(190)", "YES", "<NULL>", "", "utf8mb4", "utf8mb4_unicode_ci"],
        ["03", "c2", "varchar(190)", "YES", "<NULL>", "", "utf8mb4", "utf8mb4_unicode_ci"],
        ["04", "c3", "int(11)", "YES", "<NULL>", "", "<NULL>", "<NULL>"],
        ["05", "c4", "varchar(88)", "YES", "<NULL>", "", "utf8mb4", "utf8mb4_unicode_ci"],
        ["06", "c5", "varchar(190)", "YES", "2", "", "utf8mb4", "utf8mb4_unicode_ci"],
    ],
    "indexes": [
        ["idx_m_etl_test_c3", "1", "1", "c3"],
        ["primary", "0", "1", "test_id"],
    ],
    "row_count": 0,
}

EXCEPTION_EXECUTION_KEYS = (
    "v934|8:surefire|4:unit|4:unit|50:com.foggyframework.dataset.db.dialect.FDialectTest",
    "v934|8:surefire|4:unit|4:unit|54:com.foggyframework.dataset.db.utils.JdbcTableUtilsTest",
    "v934|8:surefire|4:unit|4:unit|55:com.foggyframework.dataset.db.fsscript.SyncSqlTableTest",
    "v934|8:surefire|4:unit|4:unit|55:com.foggyframework.dataset.db.table.dll.JdbcUpdaterTest",
    "v934|8:surefire|4:unit|4:unit|60:com.foggyframework.dataset.db.data.dll.SqlTableRowEditorTest",
    "v934|8:surefire|4:unit|4:unit|63:com.foggyframework.dataset.table.curd.BugFixInsertUpdateMapTest",
)
EXCEPTION_PARENT_METADATA = {
    "runner": "surefire",
    "lane": "unit",
    "variant_key": "unit",
    "db_kind": "none",
    "infra_kind": "hermetic",
    "execution_step": "2",
    "required": "true",
    "owner": "foggy-dataset",
}
EXCEPTION_TESTCASE_NODES = {
    EXCEPTION_EXECUTION_KEYS[0]: 2,
    EXCEPTION_EXECUTION_KEYS[1]: 4,
    EXCEPTION_EXECUTION_KEYS[2]: 1,
    EXCEPTION_EXECUTION_KEYS[3]: 2,
    EXCEPTION_EXECUTION_KEYS[4]: 1,
    EXCEPTION_EXECUTION_KEYS[5]: 1,
}
NEGATIVE_PROBE_SPECS = (
    ("extra-manifest-field", "E_SCHEMA"),
    ("wrong-fixture-run", "E_IDENTITY"),
    ("wrong-project", "E_IDENTITY"),
    ("wrong-port", "E_IDENTITY"),
    ("wrong-image", "E_IDENTITY"),
    ("wrong-database-identity", "E_IDENTITY"),
    ("wrong-tool-binding", "E_BINDING"),
    ("wrong-contract-binding", "E_BINDING"),
    ("wrong-datasource-adapter-binding", "E_BINDING"),
    ("wrong-ddl-binding", "E_BINDING"),
    ("wrong-reactor-binding", "E_BINDING"),
    ("fixture-mutation", "E_FIXTURE"),
    ("connection-receipt-mutation", "E_FIXTURE"),
    ("boolean-connection-count", "E_FIXTURE"),
    ("boolean-connection-schema", "E_DATASOURCE"),
    ("boolean-connection-receipt-count", "E_DATASOURCE"),
    ("empty-connection-ids", "E_DATASOURCE"),
    ("duplicate-connection-ids", "E_DATASOURCE"),
    ("wrong-connection-observation-scope", "E_DATASOURCE"),
    ("open-connection-observation", "E_DATASOURCE"),
    ("wrong-observed-connection-user", "E_DATASOURCE"),
    ("ambient-fixture-environment", "E_DATASOURCE"),
    ("global-spring-override", "E_DATASOURCE"),
    ("dotted-fixture-environment", "E_DATASOURCE"),
    ("dotted-spring-environment", "E_DATASOURCE"),
    ("option-argument-indirection", "E_DATASOURCE"),
    ("cleanup-residue", "E_CLEANUP"),
    ("boolean-cleanup-count", "E_CLEANUP"),
    ("port-occupied", "E_CLEANUP"),
    ("integer-port-free", "E_CLEANUP"),
    ("missing-artifact", "E_EVIDENCE"),
    ("invalid-artifact-sha", "E_SCHEMA"),
    ("boolean-artifact-size", "E_SCHEMA"),
    ("publisher-refuse-overwrite", "E_OUTPUT"),
    ("publisher-refuse-symlink", "E_OUTPUT"),
    ("publisher-rollback-after-link", "E_OUTPUT"),
)


class FixtureError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def reject(code: str, message: str) -> None:
    raise FixtureError(code, message)


def require(condition: bool, code: str, message: str) -> None:
    if not condition:
        reject(code, message)


def safe_run_id(value: str, label: str = "run id") -> str:
    require(
        type(value) is str
        and RUN_ID_RE.fullmatch(value or "") is not None
        and value not in {".", ".."},
        "E_IDENTITY",
        f"unsafe {label}: {value!r}",
    )
    return value


def fixture_run_id(run_id: str) -> str:
    safe_run_id(run_id)
    scope = hashlib.sha256(f"{run_id}|unit-mysql57\n".encode()).hexdigest()[:16]
    return f"unit-mysql57-{scope}"


def project_name(child_id: str) -> str:
    safe_run_id(child_id, "fixture run id")
    scope = hashlib.sha256(f"{child_id}|mysql57\n".encode()).hexdigest()[:12]
    return f"v934db-mysql57-{scope}"


def sha256_file(path: Path) -> str:
    return hashlib.sha256(stable_file_bytes(path, "E_EVIDENCE")).hexdigest()


def file_identity(value: os.stat_result) -> tuple[int, ...]:
    return (
        value.st_dev,
        value.st_ino,
        value.st_mode,
        value.st_nlink,
        value.st_uid,
        value.st_gid,
        value.st_size,
        value.st_mtime_ns,
        value.st_ctime_ns,
    )


def directory_identity(value: os.stat_result) -> tuple[int, ...]:
    return (value.st_dev, value.st_ino, value.st_mode, value.st_uid, value.st_gid)


def stable_file_bytes(path: Path, code: str) -> bytes:
    descriptor = -1
    try:
        before = os.lstat(path)
        require(stat.S_ISREG(before.st_mode) and not stat.S_ISLNK(before.st_mode), code, f"not a real file: {path}")
        descriptor = os.open(path, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
        opened = os.fstat(descriptor)
        require(file_identity(opened) == file_identity(before), code, f"file identity changed before read: {path}")
        chunks: list[bytes] = []
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            chunks.append(chunk)
        after_fd = os.fstat(descriptor)
        after_path = os.lstat(path)
        require(
            file_identity(before) == file_identity(after_fd) == file_identity(after_path),
            code,
            f"file identity changed during read: {path}",
        )
        payload = b"".join(chunks)
        require(len(payload) == before.st_size, code, f"file size changed during read: {path}")
        return payload
    except OSError as exc:
        reject(code, f"cannot read {path}: {exc.__class__.__name__}")
    finally:
        if descriptor >= 0:
            os.close(descriptor)


def unique_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        require(key not in value, "E_EVIDENCE", f"duplicate JSON key: {key!r}")
        value[key] = item
    return value


def reject_json_constant(value: str) -> None:
    reject("E_EVIDENCE", f"non-finite JSON constant: {value}")


def strict_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(
            stable_file_bytes(path, "E_EVIDENCE").decode("utf-8", errors="strict"),
            object_pairs_hook=unique_json_object,
            parse_constant=reject_json_constant,
        )
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        reject("E_EVIDENCE", f"cannot read JSON {path}: {exc.__class__.__name__}")
    require(type(value) is dict, "E_EVIDENCE", f"JSON root differs: {path}")
    return value


def regular_file(path: Path, code: str) -> Path:
    try:
        mode = path.lstat().st_mode
    except OSError:
        reject(code, f"missing file: {path}")
    require(stat.S_ISREG(mode) and not stat.S_ISLNK(mode), code, f"not a real file: {path}")
    return path


def real_directory(path: Path, code: str) -> Path:
    try:
        mode = path.lstat().st_mode
    except OSError:
        reject(code, f"missing directory: {path}")
    require(stat.S_ISDIR(mode) and not stat.S_ISLNK(mode), code, f"not a real directory: {path}")
    require(path.resolve(strict=True) == path, code, f"directory is not canonical: {path}")
    return path


def exact_keys(value: Any, expected: set[str], code: str, label: str) -> dict[str, Any]:
    require(type(value) is dict, code, f"{label} is not an object")
    require(
        set(value) == expected,
        code,
        f"{label} fields differ missing={sorted(expected - set(value))} extra={sorted(set(value) - expected)}",
    )
    return value


def read_env(path: Path, fields: set[str]) -> dict[str, str]:
    rows: dict[str, str] = {}
    try:
        lines = stable_file_bytes(path, "E_EVIDENCE").decode("utf-8", errors="strict").splitlines()
    except (OSError, UnicodeError) as exc:
        reject("E_EVIDENCE", f"cannot read env {path}: {exc.__class__.__name__}")
    for line in lines:
        require("=" in line and "\r" not in line and "\n" not in line, "E_EVIDENCE", f"malformed env row: {path}")
        key, value = line.split("=", 1)
        require(ENV_KEY_RE.fullmatch(key) is not None and key not in rows, "E_EVIDENCE", f"unsafe env key: {key!r}")
        rows[key] = value
    require(set(rows) == fields, "E_EVIDENCE", f"env schema differs: {path}")
    return rows


def atomic_publish(path: Path, payload: bytes, mode: int = 0o644) -> None:
    require(path.is_absolute() and path.name not in {"", ".", ".."}, "E_OUTPUT", "unsafe output path")
    real_directory(path.parent, "E_OUTPUT")
    parent_before = os.lstat(path.parent)
    directory_fd = -1
    temporary_name = f".{path.name}.{os.getpid()}.{os.urandom(8).hex()}.tmp"
    published = False
    success = False
    published_identity: tuple[int, int] | None = None
    rollback_error: str | None = None
    try:
        directory_fd = os.open(path.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0))
        require(directory_identity(os.fstat(directory_fd)) == directory_identity(parent_before), "E_OUTPUT", "output parent identity changed")
        try:
            os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        except FileNotFoundError:
            pass
        else:
            reject("E_OUTPUT", f"refusing to overwrite: {path}")
        descriptor = os.open(
            temporary_name,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            mode,
            dir_fd=directory_fd,
        )
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        temporary_stat = os.stat(temporary_name, dir_fd=directory_fd, follow_symlinks=False)
        require(stat.S_ISREG(temporary_stat.st_mode) and temporary_stat.st_size == len(payload), "E_OUTPUT", "temporary output differs")
        published_identity = (temporary_stat.st_dev, temporary_stat.st_ino)
        os.link(temporary_name, path.name, src_dir_fd=directory_fd, dst_dir_fd=directory_fd, follow_symlinks=False)
        published = True
        final_stat = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        require((final_stat.st_dev, final_stat.st_ino) == published_identity, "E_OUTPUT", "published output inode differs")
        os.unlink(temporary_name, dir_fd=directory_fd)
        require(directory_identity(os.fstat(directory_fd)) == directory_identity(parent_before), "E_OUTPUT", "output parent identity changed during publish")
        os.fsync(directory_fd)
        success = True
    except FixtureError:
        raise
    except OSError as exc:
        reject("E_OUTPUT", f"cannot publish {path}: {exc.__class__.__name__}")
    finally:
        if directory_fd >= 0:
            try:
                os.unlink(temporary_name, dir_fd=directory_fd)
            except FileNotFoundError:
                pass
            except OSError:
                pass
            if published and not success and published_identity is not None:
                try:
                    current = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
                    if (current.st_dev, current.st_ino) != published_identity:
                        rollback_error = "published output was replaced during rollback"
                    else:
                        os.unlink(path.name, dir_fd=directory_fd)
                except FileNotFoundError:
                    pass
                except OSError as exc:
                    rollback_error = f"cannot roll back published output: {exc.__class__.__name__}"
            try:
                os.close(directory_fd)
            except OSError as exc:
                rollback_error = rollback_error or f"cannot close output parent: {exc.__class__.__name__}"
            if rollback_error is not None:
                reject("E_OUTPUT", rollback_error)


def json_bytes(value: Any) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()


def repo_root(value: Path) -> Path:
    try:
        root = value.expanduser().resolve(strict=True)
    except OSError as exc:
        reject("E_INPUT", f"cannot resolve repo root: {exc.__class__.__name__}")
    require(root.is_dir() and not root.is_symlink(), "E_INPUT", "repo root differs")
    for relative in (
        SELF_PATH,
        PROVISIONER_PATH,
        FIXTURE_CONTRACT_PATH,
        STEP2_EXECUTION_PATH,
        STEP2_DISCOVERY_PATH,
        MIGRATION_WORKITEM_PATH,
        REACTOR_FREEZE_PATH,
        Path("pom.xml"),
    ):
        regular_file(root / relative, "E_INPUT")
    return root


def expected_fixture_contract() -> dict[str, Any]:
    return {
        "schema_version": 1,
        "kind": "v934-step4-unit-mysql57-fixture-contract",
        "status": "confirmed-remediation-exception",
        "parent_inventory": {
            "path": STEP2_EXECUTION_PATH.as_posix(),
            "sha256": STEP2_EXECUTION_SHA256,
        },
        "discovery_inventory": {
            "path": STEP2_DISCOVERY_PATH.as_posix(),
            "sha256": STEP2_DISCOVERY_SHA256,
        },
        "migration_workitem": {
            "path": MIGRATION_WORKITEM_PATH.as_posix(),
            "sha256": MIGRATION_WORKITEM_SHA256,
        },
        "datasource_adapter": {
            "path": DATASOURCE_ADAPTER_PATH.as_posix(),
            "sha256": DATASOURCE_ADAPTER_SHA256,
            "environment_keys": list(DATASOURCE_ENV_KEYS),
            "scope": "foggy-dataset-test-resource-only",
            "inventory_authority": "committed-head-tree-with-scrubbed-git-environment",
            "default_compatibility": "preserved-with-placeholder-defaults",
        },
        "scope": {
            "authority": "step4-unit-lane-replacement",
            "fixture_kind": "run-owned-mysql57",
            "effective_db_kind": "mysql57",
            "effective_infra_kind": "run-owned-database",
            "effective_execution_step": 4,
            "lane_reports": 681,
            "lane_structural_reports": 55,
            "lane_testcase_nodes": 4941,
            "known_hidden_dependency_reports": 6,
            "known_hidden_dependency_testcase_nodes": 11,
        },
        "execution_keys": list(EXCEPTION_EXECUTION_KEYS),
        "execution_testcase_nodes": EXCEPTION_TESTCASE_NODES,
        "parent_metadata_expected": EXCEPTION_PARENT_METADATA,
        "invariants": {
            "frozen_unit_execution_keys": "unchanged-681",
            "single_maven_invocation": True,
            "unit_reports": 681,
            "unit_structural_reports": 55,
            "unit_testcase_nodes": 4941,
            "coverage_exec_files": 23,
            "coverage_sessions": 48,
            "ambient_listener_reuse": "forbidden",
            "global_spring_datasource_override": "forbidden",
            "profile_specific_datasources": "preserved",
            "restricted_credential_receipt": "exclusive-non-super-unit-maven-window",
            "connection_observation_close": "disable-init-connect-before-receipt-read",
            "connection_receipt_rows": "ordered-connection-id-and-observed-user",
            "fixture_cleanup": "container-volume-network-zero-and-port-free",
        },
        "supersession": {
            "parent_identity_and_cardinality": "retained-as-step2-structure",
            "parent_correctness_evidence": "replaced-by-fresh-step4-unit-for-entire-lane",
            "parent_infra_classification": "known-six-recorded;fixture-authority-applies-to-full-unit-invocation",
            "step2_historical_green": "structure-only-not-correctness-reusable-for-unit-lane",
            "migration_followup": "docs/9.3.4/workitems/DEBT-unit-mysql57-fixture-classification-migration.md",
            "migration_gate": "must-close-before-9.3.5-version-acceptance",
            "v934_acceptance": "allowed-only-after-fresh-step4-unit-and-formal-audit-pass",
        },
    }


def validate_fixture_contract(root: Path) -> dict[str, Any]:
    require(sha256_file(root / FIXTURE_CONTRACT_PATH) == FIXTURE_CONTRACT_SHA256, "E_CONTRACT", "Unit MySQL fixture contract hash differs")
    contract = strict_json(root / FIXTURE_CONTRACT_PATH)
    require(contract == expected_fixture_contract(), "E_CONTRACT", "Unit MySQL fixture contract differs")
    require(
        sha256_file(root / STEP2_EXECUTION_PATH) == contract["parent_inventory"]["sha256"],
        "E_CONTRACT",
        "fixture parent execution inventory hash differs",
    )
    require(
        sha256_file(root / STEP2_DISCOVERY_PATH) == contract["discovery_inventory"]["sha256"],
        "E_CONTRACT",
        "fixture parent discovery inventory hash differs",
    )
    require(
        sha256_file(root / MIGRATION_WORKITEM_PATH) == contract["migration_workitem"]["sha256"],
        "E_CONTRACT",
        "fixture migration workitem hash differs",
    )
    validate_datasource_adapter_scope(root)
    try:
        inventory_text = stable_file_bytes(root / STEP2_EXECUTION_PATH, "E_CONTRACT").decode("utf-8", errors="strict")
        rows = list(csv.DictReader(io.StringIO(inventory_text), delimiter="\t"))
    except (UnicodeError, csv.Error) as exc:
        reject("E_CONTRACT", f"cannot read fixture parent inventory: {exc.__class__.__name__}")
    selected = {row.get("execution_key", ""): row for row in rows if row.get("execution_key", "") in EXCEPTION_EXECUTION_KEYS}
    require(tuple(sorted(selected)) == EXCEPTION_EXECUTION_KEYS, "E_CONTRACT", "fixture execution key set differs")
    for key in EXCEPTION_EXECUTION_KEYS:
        row = selected[key]
        require(
            all(row.get(field) == expected for field, expected in EXCEPTION_PARENT_METADATA.items()),
            "E_CONTRACT",
            f"parent metadata differs for {key}",
        )
    try:
        discovery_text = stable_file_bytes(root / STEP2_DISCOVERY_PATH, "E_CONTRACT").decode("utf-8", errors="strict")
        discovery_rows = list(csv.DictReader(io.StringIO(discovery_text), delimiter="\t"))
    except (UnicodeError, csv.Error) as exc:
        reject("E_CONTRACT", f"cannot read fixture discovery inventory: {exc.__class__.__name__}")
    discovery_by_fqcn = {row.get("report_fqcn", ""): row for row in discovery_rows}
    observed_nodes: dict[str, int] = {}
    for key in EXCEPTION_EXECUTION_KEYS:
        fqcn = selected[key].get("report_fqcn", "")
        row = discovery_by_fqcn.get(fqcn)
        require(row is not None and row.get("discovered_test_nodes", "").isdigit(), "E_CONTRACT", f"missing discovery cardinality for {key}")
        observed_nodes[key] = int(row["discovered_test_nodes"])
    require(observed_nodes == EXCEPTION_TESTCASE_NODES and sum(observed_nodes.values()) == 11, "E_CONTRACT", "known hidden dependency cardinality differs")
    return contract


def validate_datasource_adapter_scope(root: Path) -> None:
    require(
        sha256_file(root / DATASOURCE_ADAPTER_PATH) == DATASOURCE_ADAPTER_SHA256,
        "E_CONTRACT",
        "Unit datasource adapter hash differs",
    )
    completed = run_command(
        [
            "git", "-c", "core.fsmonitor=false", "-c", "core.untrackedCache=false",
            "-c", "core.hooksPath=/dev/null", "-C", str(root),
            "ls-tree", "-r", "-z", "--name-only", "HEAD",
        ],
        environment=controlled_git_environment(),
    )
    require(completed.returncode == 0, "E_CONTRACT", "cannot enumerate committed resource adapters")
    try:
        tracked_paths = [item for item in completed.stdout.decode("utf-8", errors="strict").split("\0") if item]
    except UnicodeError:
        reject("E_CONTRACT", "tracked resource adapter inventory is not UTF-8")
    resources: list[str] = []
    for relative in tracked_paths:
        parts = PurePosixPath(relative).parts
        if any(
            parts[index] == "src"
            and parts[index + 1] in {"main", "test"}
            and parts[index + 2] == "resources"
            for index in range(max(0, len(parts) - 2))
        ):
            resources.append(relative)
    needles = tuple(key.encode() for key in DATASOURCE_ENV_KEYS)
    consumers: list[str] = []
    for relative in resources:
        payload = stable_file_bytes(root / relative, "E_CONTRACT")
        if any(needle in payload for needle in needles):
            consumers.append(relative)
    require(
        consumers == [DATASOURCE_ADAPTER_PATH.as_posix()],
        "E_CONTRACT",
        f"Unit datasource adapter scope differs: {consumers}",
    )


def frozen_reactor_modules(root: Path) -> list[str]:
    require(sha256_file(root / REACTOR_FREEZE_PATH) == REACTOR_FREEZE_SHA256, "E_CONTRACT", "reactor freeze hash differs")
    freeze = strict_json(root / REACTOR_FREEZE_PATH)
    reactor = freeze.get("reactor")
    require(type(reactor) is dict, "E_CONTRACT", "Step 2 reactor contract differs")
    modules = reactor.get("modules")
    require(
        type(modules) is list
        and reactor.get("module_count") == 24
        and len(modules) == 24
        and len(set(modules)) == 24
        and all(
            type(item) is str
            and PurePosixPath(item).as_posix() == item
            and not PurePosixPath(item).is_absolute()
            and all(part not in {"", ".", ".."} for part in PurePosixPath(item).parts)
            for item in modules
        ),
        "E_CONTRACT",
        "frozen production reactor differs",
    )
    ordered = sorted(modules)
    require(
        hashlib.sha256((",".join(ordered) + "\n").encode()).hexdigest() == FROZEN_REACTOR_SHA256,
        "E_CONTRACT",
        "frozen production reactor hash differs",
    )
    return ordered


def cell_root(root: Path, child_id: str) -> Path:
    return root / "target/v934-step3-database-matrix/runs" / child_id / "cells/mysql57"


def resource_identity(child_id: str) -> dict[str, str]:
    project = project_name(child_id)
    return {
        "project": project,
        "container": f"{project}-mysql57",
        "volume": f"{project}-mysql57-data",
        "network": f"{project}-network",
    }


def controlled_git_environment() -> dict[str, str]:
    environment = {
        key: value for key, value in os.environ.items()
        if not key.upper().startswith("GIT_") and key not in {"XDG_CONFIG_HOME"}
    }
    environment.update({
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_CONFIG_GLOBAL": os.devnull,
        "GIT_ATTR_NOSYSTEM": "1",
        "GIT_NO_REPLACE_OBJECTS": "1",
        "GIT_OPTIONAL_LOCKS": "0",
        "LC_ALL": "C",
        "LANG": "C",
    })
    return environment


def run_command(
    arguments: list[str],
    *,
    cwd: Path | None = None,
    input_bytes: bytes | None = None,
    environment: Mapping[str, str] | None = None,
) -> subprocess.CompletedProcess[bytes]:
    try:
        return subprocess.run(
            arguments,
            cwd=cwd,
            input=input_bytes,
            env=environment,
            stdout=subprocess.PIPE if input_bytes is None else None,
            stderr=subprocess.PIPE if input_bytes is None else None,
            check=False,
        )
    except OSError as exc:
        reject("E_RUNTIME", f"cannot execute {arguments[0]}: {exc.__class__.__name__}")


def mysql_command(container: str, sql: str, *, capture: bool) -> bytes:
    arguments = [
        "docker", "exec", "-i", "-e", "MYSQL_PWD=foggy_test_123", container,
        "mysql", "--batch", "--raw", "--skip-column-names", "-ufoggy", "foggy_test",
    ]
    try:
        completed = subprocess.run(
            arguments,
            input=sql.encode(),
            stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        reject("E_RUNTIME", f"cannot invoke Docker MySQL client: {exc.__class__.__name__}")
    require(completed.returncode == 0, "E_RUNTIME", f"Docker MySQL client failed rc={completed.returncode}")
    return completed.stdout if capture else b""


def mysql_root_command(container: str, sql: str, *, capture: bool) -> bytes:
    arguments = [
        "docker", "exec", "-i", "-e", "MYSQL_PWD=foggy_root_123", container,
        "mysql", "--batch", "--raw", "--skip-column-names", "-uroot", "foggy_test",
    ]
    try:
        completed = subprocess.run(
            arguments,
            input=sql.encode(),
            stdout=subprocess.PIPE if capture else subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            check=False,
        )
    except OSError as exc:
        reject("E_RUNTIME", f"cannot invoke Docker root MySQL client: {exc.__class__.__name__}")
    require(completed.returncode == 0, "E_RUNTIME", f"Docker root MySQL client failed rc={completed.returncode}")
    return completed.stdout if capture else b""


def validate_controlled_environment(
    environment: Mapping[str, str],
    config_values: Mapping[str, str],
) -> None:
    spring_keys = sorted(
        key for key in environment
        if key.lower().startswith(("spring_", "spring.", "spring-"))
    )
    require(not spring_keys, "E_DATASOURCE", f"ambient Spring environment is forbidden: {spring_keys}")
    fixture_keys = sorted(
        key for key in environment
        if key.lower().replace(".", "_").replace("-", "_").startswith("v934_unit_mysql57_")
    )
    require(not fixture_keys, "E_DATASOURCE", f"ambient Unit fixture environment is forbidden: {fixture_keys}")
    forbidden_tokens = (
        "spring.", "spring_", "spring-",
        "v934.unit.mysql57", "v934_unit_mysql57", "v934-unit-mysql57",
    )
    forbidden_indirection = ("@", "-xx:vmoptionsfile", "-javaagent:", "-agentlib:", "-agentpath:")
    for key in ("MAVEN_ARGS", "MAVEN_CONFIG", "MAVEN_OPTS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        value = environment.get(key, "").lower()
        require(not any(token in value for token in forbidden_tokens), "E_DATASOURCE", f"{key} contains a datasource override")
        require(not any(token in value for token in forbidden_indirection), "E_DATASOURCE", f"{key} contains option indirection")
    for label, value in config_values.items():
        lowered = value.lower()
        require(not any(token in lowered for token in forbidden_tokens), "E_DATASOURCE", f"{label} contains a datasource override")
        require(not any(token in lowered for token in forbidden_indirection), "E_DATASOURCE", f"{label} contains option indirection")


def validate_spring_environment(root: Path) -> None:
    config_values: dict[str, str] = {}
    for relative in (Path(".mvn/maven.config"), Path(".mvn/jvm.config")):
        path = root / relative
        if path.exists() or path.is_symlink():
            regular_file(path, "E_DATASOURCE")
            try:
                config_values[relative.as_posix()] = stable_file_bytes(path, "E_DATASOURCE").decode("utf-8", errors="strict")
            except UnicodeError:
                reject("E_DATASOURCE", f"{relative} is not UTF-8")
    validate_controlled_environment(os.environ, config_values)


def configure_connection_receipt(container: str) -> None:
    sql = f"""\
SET GLOBAL init_connect = '';
DROP TABLE IF EXISTS `V934_UNIT_CONNECTION_RECEIPT`;
CREATE TABLE `V934_UNIT_CONNECTION_RECEIPT` (
  `connection_id` bigint NOT NULL,
  `user_value` varchar(255) NOT NULL,
  PRIMARY KEY (`connection_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
DROP USER IF EXISTS '{UNIT_DATABASE_USER}'@'%';
CREATE USER '{UNIT_DATABASE_USER}'@'%' IDENTIFIED BY '{UNIT_DATABASE_PASSWORD}';
GRANT ALL PRIVILEGES ON `foggy_test`.* TO '{UNIT_DATABASE_USER}'@'%';
FLUSH PRIVILEGES;
SET GLOBAL init_connect = 'INSERT INTO foggy_test.V934_UNIT_CONNECTION_RECEIPT(connection_id,user_value) VALUES (CONNECTION_ID(), USER())';
"""
    mysql_root_command(container, sql, capture=False)


def collect_connection_receipt(container: str, output: Path) -> dict[str, Any]:
    sql = """\
SET GLOBAL init_connect = '';
SELECT connection_id, user_value
FROM `V934_UNIT_CONNECTION_RECEIPT`
ORDER BY connection_id;
"""
    raw = mysql_root_command(container, sql, capture=True)
    try:
        lines = raw.decode("utf-8", errors="strict").splitlines()
    except UnicodeError:
        reject("E_DATASOURCE", "connection receipt is not UTF-8")
    connection_ids: list[int] = []
    connections: list[dict[str, Any]] = []
    for line in lines:
        parts = line.split("\t")
        require(
            len(parts) == 2
            and parts[0].isdigit()
            and parts[1].startswith(f"{UNIT_DATABASE_USER}@"),
            "E_DATASOURCE",
            "a non-super MySQL connection did not use the restricted run-owned credential",
        )
        connection_id = int(parts[0])
        connection_ids.append(connection_id)
        connections.append({"connection_id": connection_id, "user_value": parts[1]})
    require(connection_ids and len(connection_ids) == len(set(connection_ids)), "E_DATASOURCE", "test JVM did not exclusively use the run-owned MySQL credential")
    payload = {
        "schema_version": 1,
        "kind": "v934-step4-unit-mysql57-connection-receipt",
        "status": "passed",
        "username": UNIT_DATABASE_USER,
        "jdbc_url": UNIT_DATABASE_URL,
        "observation_scope": CONNECTION_OBSERVATION_SCOPE,
        "observation_closed": True,
        "connection_count": len(connection_ids),
        "connection_ids": connection_ids,
        "connections": connections,
    }
    validate_connection_receipt(payload)
    atomic_publish(output, json_bytes(payload))
    return payload


def validate_connection_receipt(payload: Any) -> dict[str, Any]:
    payload = exact_keys(
        payload,
        {
            "schema_version", "kind", "status", "username", "jdbc_url",
            "observation_scope", "observation_closed", "connection_count", "connection_ids",
            "connections",
        },
        "E_DATASOURCE",
        "connection receipt",
    )
    require(type(payload["schema_version"]) is int and payload["schema_version"] == 1, "E_DATASOURCE", "connection receipt schema differs")
    require(payload["kind"] == "v934-step4-unit-mysql57-connection-receipt" and payload["status"] == "passed", "E_DATASOURCE", "connection receipt identity differs")
    require(payload["username"] == UNIT_DATABASE_USER and payload["jdbc_url"] == UNIT_DATABASE_URL, "E_DATASOURCE", "connection receipt coordinate differs")
    require(
        payload["observation_scope"] == CONNECTION_OBSERVATION_SCOPE
        and type(payload["observation_closed"]) is bool
        and payload["observation_closed"] is True,
        "E_DATASOURCE",
        "connection receipt observation window differs",
    )
    ids = payload["connection_ids"]
    connections = payload["connections"]
    require(
        type(ids) is list
        and ids
        and all(type(item) is int and item > 0 for item in ids)
        and len(ids) == len(set(ids))
        and type(payload["connection_count"]) is int
        and payload["connection_count"] == len(ids),
        "E_DATASOURCE",
        "connection receipt count differs",
    )
    require(
        type(connections) is list
        and len(connections) == len(ids)
        and all(
            type(item) is dict
            and set(item) == {"connection_id", "user_value"}
            and type(item["connection_id"]) is int
            and item["connection_id"] == ids[index]
            and type(item["user_value"]) is str
            and item["user_value"].startswith(f"{UNIT_DATABASE_USER}@")
            for index, item in enumerate(connections)
        ),
        "E_DATASOURCE",
        "connection receipt observed users differ",
    )
    return payload


def parse_snapshot(payload: bytes) -> dict[str, Any]:
    try:
        lines = payload.decode("utf-8", errors="strict").splitlines()
    except UnicodeError:
        reject("E_FIXTURE", "MySQL snapshot is not UTF-8")
    table: dict[str, str] | None = None
    columns: list[list[str]] = []
    indexes: list[list[str]] = []
    row_count: int | None = None
    for line in lines:
        parts = line.split("\t")
        if parts[0] == "table" and len(parts) == 4 and table is None:
            table = {"name": parts[1], "engine": parts[2], "collation": parts[3]}
        elif parts[0] == "column" and len(parts) == 9:
            columns.append(parts[1:])
        elif parts[0] == "index" and len(parts) == 5:
            indexes.append(parts[1:])
        elif parts[0] == "rows" and len(parts) == 2 and row_count is None and parts[1].isdigit():
            row_count = int(parts[1])
        else:
            reject("E_FIXTURE", f"unexpected MySQL snapshot row shape: {parts[:1]}")
    observed = {"table": table, "columns": columns, "indexes": indexes, "row_count": row_count}
    require(observed == EXPECTED_SNAPSHOT, "E_FIXTURE", f"M_ETL_TEST fixture differs: {observed}")
    return observed


def snapshot(container: str, output: Path) -> str:
    observed = parse_snapshot(mysql_root_command(container, SNAPSHOT_SQL, capture=True))
    atomic_publish(output, json_bytes(observed))
    return sha256_file(output)


def validate_callback_environment(root: Path, run_id: str, child_id: str) -> tuple[Path, dict[str, str]]:
    safe_run_id(run_id)
    safe_run_id(child_id, "fixture run id")
    require(child_id == fixture_run_id(run_id), "E_IDENTITY", "fixture run id differs from outer run")
    expected_cell = cell_root(root, child_id)
    require(os.environ.get("V934_DB_KIND") == DATABASE, "E_IDENTITY", "callback database kind differs")
    require(os.environ.get("V934_DB_EXPECTED_DATABASE") == DATABASE, "E_IDENTITY", "callback expected database differs")
    require(os.environ.get("V934_DB_PROFILE") == PROFILE, "E_IDENTITY", "callback profile differs")
    require(os.environ.get("V934_DB_CELL_ROOT") == str(expected_cell), "E_IDENTITY", "callback cell root differs")
    identity = resource_identity(child_id)
    require(os.environ.get("V934_DB_CONTAINER") == identity["container"], "E_IDENTITY", "callback container differs")
    lock_mode = os.environ.get("V934_AUTHORITY_LOCK_MODE", "standalone")
    require(lock_mode in {"inherited", "standalone"}, "E_IDENTITY", "callback lock authority differs")
    if lock_mode == "inherited":
        require(os.environ.get("V934_PARENT_RUN_ID") == run_id, "E_IDENTITY", "callback parent authority differs")
    else:
        require(not os.environ.get("V934_PARENT_RUN_ID"), "E_IDENTITY", "standalone callback inherited a parent run")
    real_directory(expected_cell, "E_EVIDENCE")
    resource = read_env(
        expected_cell / "resource.env",
        {"run_id", "database", "service", "project", "container", "volume", "network", "host_port", "container_port", "profile", "expected_image_ref", "expected_image_id"},
    )
    require(
        resource == {
            "run_id": child_id,
            "database": DATABASE,
            "service": SERVICE,
            **identity,
            "host_port": str(PORT),
            "container_port": "3306",
            "profile": PROFILE,
            "expected_image_ref": IMAGE_REF,
            "expected_image_id": IMAGE_ID,
        },
        "E_IDENTITY",
        "callback resource evidence differs",
    )
    runtime = read_env(
        expected_cell / "runtime.env",
        {"database", "actual_image_id", "actual_image_ref", "actual_repo_digest", "actual_project", "actual_service", "actual_mapped_port", "volume_project", "network_project", "volume_created", "database_identity", "status"},
    )
    require(
        runtime["database"] == DATABASE
        and runtime["actual_image_id"] == IMAGE_ID
        and runtime["actual_image_ref"] == runtime["actual_repo_digest"] == IMAGE_REF
        and runtime["actual_project"] == runtime["volume_project"] == runtime["network_project"] == identity["project"]
        and runtime["actual_service"] == SERVICE
        and runtime["actual_mapped_port"] == f"127.0.0.1:{PORT}"
        and runtime["database_identity"] == DATABASE_IDENTITY
        and runtime["status"] == "verified",
        "E_IDENTITY",
        "callback runtime evidence differs",
    )
    require(stable_file_bytes(expected_cell / "database-identity.txt", "E_IDENTITY") == (DATABASE_IDENTITY + "\n").encode(), "E_IDENTITY", "database identity receipt differs")
    return expected_cell, identity


def write_callback_status(path: Path, rows: list[str]) -> None:
    atomic_publish(path, "".join(f"{row}\n" for row in rows).encode())


def command_callback(args: argparse.Namespace) -> None:
    root = repo_root(args.repo_root)
    validate_fixture_contract(root)
    validate_spring_environment(root)
    run_id = safe_run_id(args.run_id)
    child_id = safe_run_id(args.fixture_run_id, "fixture run id")
    expected_reactor_modules = frozen_reactor_modules(root)
    actual_reactor_modules = args.reactor_modules.split(",")
    require(
        actual_reactor_modules == expected_reactor_modules,
        "E_INPUT",
        "reactor module list differs from frozen production24",
    )
    expected_reactor = ",".join(expected_reactor_modules)
    coverage_args: list[str] = []
    if args.coverage_exec == "disabled":
        require(args.session_id == "disabled" and not os.environ.get("V934_COVERAGE_EXEC_ROOT"), "E_IDENTITY", "disabled coverage coordinate differs")
        expected_exec: Path | None = None
    else:
        expected_exec = Path(args.coverage_exec)
        expected_coordinate = root / f"target/v934-step4-coverage/runs/{run_id}/exec/jacoco-ut.exec"
        require(expected_exec == expected_coordinate, "E_IDENTITY", "Unit exec coordinate differs")
        require(args.session_id == f"{run_id}-unit", "E_IDENTITY", "Unit session id differs")
        require(not expected_exec.exists() and not expected_exec.is_symlink(), "E_IDENTITY", "Unit exec already exists")
        coverage_args = [
            "-P!coverage,v934-coverage",
            f"-Djacoco.ut.destFile={expected_exec}",
            f"-Dv934.coverage.sessionId={args.session_id}",
        ]
    cell, identity = validate_callback_environment(root, run_id, child_id)
    before_path = cell / "unit-fixture-before.json"
    after_path = cell / "unit-fixture-after.json"
    connection_path = cell / "unit-connection-receipt.json"
    status_path = cell / "unit-fixture-status.env"
    for path in (before_path, after_path, connection_path, status_path):
        require(not path.exists() and not path.is_symlink(), "E_OUTPUT", f"callback evidence already exists: {path}")

    mysql_command(identity["container"], UNIT_DDL, capture=False)
    configure_connection_receipt(identity["container"])
    before_sha = snapshot(identity["container"], before_path)
    maven = [
        "mvn", "-q", "-f", str(root / "pom.xml"),
        "-P!multi-db,!model-lifecycle,!query-cache-real-query",
        "-pl", expected_reactor, "-am",
        "-DskipUnitTests=false", "-DskipITs=true", "-Dsurefire.failIfNoTests=false",
        *coverage_args, "test",
    ]
    maven_environment = dict(os.environ)
    maven_environment.update(DATASOURCE_ENVIRONMENT)
    try:
        completed = subprocess.run(maven, cwd=root, env=maven_environment, check=False)
    except OSError as exc:
        reject("E_RUNTIME", f"cannot execute Maven: {exc.__class__.__name__}")
    if completed.returncode != 0:
        write_callback_status(
            status_path,
            [
                f"run_id={run_id}", f"fixture_run_id={child_id}", f"database={DATABASE}",
                f"project={identity['project']}", f"maven_exit_code={completed.returncode}",
                f"before_sha256={before_sha}", "after_sha256=absent", "status=failed",
            ],
        )
        raise SystemExit(completed.returncode)

    after_sha = snapshot(identity["container"], after_path)
    require(before_sha == after_sha and stable_file_bytes(before_path, "E_FIXTURE") == stable_file_bytes(after_path, "E_FIXTURE"), "E_FIXTURE", "Unit fixture changed during Surefire")
    connection = collect_connection_receipt(identity["container"], connection_path)
    connection_sha = sha256_file(connection_path)
    write_callback_status(
        status_path,
        [
            f"run_id={run_id}", f"fixture_run_id={child_id}", f"database={DATABASE}",
            f"project={identity['project']}", "maven_exit_code=0",
            f"before_sha256={before_sha}", f"after_sha256={after_sha}", "status=passed",
            f"connection_receipt_sha256={connection_sha}",
            f"connection_count={connection['connection_count']}",
        ],
    )
    print(f"{PREFIX} callback PASS run={run_id} fixture={child_id}")


def command_lines(arguments: list[str], code: str, label: str) -> list[str]:
    completed = run_command(arguments)
    require(completed.returncode == 0, code, f"cannot inspect {label}")
    try:
        return [line for line in completed.stdout.decode("utf-8", errors="strict").splitlines() if line]
    except UnicodeError:
        reject(code, f"non-UTF-8 {label} inventory")


def owned_resource_names(child_id: str) -> dict[str, list[str]]:
    identity = resource_identity(child_id)
    container_ids = command_lines(
        ["docker", "ps", "-aq", "--filter", f"label=com.docker.compose.project={identity['project']}"],
        "E_CLEANUP",
        "project containers",
    )
    container_names: list[str] = []
    for container_id in container_ids:
        names = command_lines(["docker", "inspect", "--format", "{{.Name}}", container_id], "E_CLEANUP", "container identity")
        require(len(names) == 1 and names[0].startswith("/"), "E_CLEANUP", "container identity differs")
        container_names.append(names[0][1:])
    resources = {
        "containers": sorted(container_names),
        "volumes": sorted(command_lines(["docker", "volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={identity['project']}"], "E_CLEANUP", "project volumes")),
        "networks": sorted(command_lines(["docker", "network", "ls", "--format", "{{.Name}}", "--filter", f"label=com.docker.compose.project={identity['project']}"], "E_CLEANUP", "project networks")),
    }
    expected = {
        "containers": identity["container"],
        "volumes": identity["volume"],
        "networks": identity["network"],
    }
    all_names = {
        "containers": command_lines(["docker", "ps", "-a", "--format", "{{.Names}}"], "E_CLEANUP", "all containers"),
        "volumes": command_lines(["docker", "volume", "ls", "-q"], "E_CLEANUP", "all volumes"),
        "networks": command_lines(["docker", "network", "ls", "--format", "{{.Name}}"], "E_CLEANUP", "all networks"),
    }
    for kind, names in resources.items():
        require(len(names) <= 1 and all(name == expected[kind] for name in names), "E_CLEANUP", f"unexpected {kind} in derived project: {names}")
        exact_present = expected[kind] in all_names[kind]
        require(exact_present == (expected[kind] in names), "E_CLEANUP", f"{kind} exact-name/label identity differs")
    return resources


def inspect_cleanup_state(child_id: str, *, require_port_free: bool) -> dict[str, int | bool]:
    require(shutil.which("docker") is not None and shutil.which("ss") is not None, "E_RUNTIME", "docker/ss is required")
    resources = owned_resource_names(child_id)
    counts: dict[str, int | bool] = {kind: len(names) for kind, names in resources.items()}
    port = run_command(["ss", "-H", "-ltn", f"sport = :{PORT}"])
    require(port.returncode == 0, "E_CLEANUP", "cannot inspect frozen MySQL port")
    counts["port_free"] = not bool(port.stdout.strip())
    expected = {"containers": 0, "volumes": 0, "networks": 0, "port_free": True}
    if not require_port_free:
        expected["port_free"] = counts["port_free"]
    require(counts == expected, "E_CLEANUP", f"Unit fixture residue differs: {counts}")
    return counts


def ensure_absent_resources(child_id: str) -> dict[str, int | bool]:
    return inspect_cleanup_state(child_id, require_port_free=True)


def cleanup_owned_resources(child_id: str) -> dict[str, int | bool]:
    identity = resource_identity(child_id)
    for _attempt in range(3):
        resources = owned_resource_names(child_id)
        for name in resources["containers"]:
            completed = run_command(["docker", "rm", "-fv", "--", name])
            require(completed.returncode == 0, "E_CLEANUP", f"cannot remove owned container: {name}")
        for name in resources["volumes"]:
            completed = run_command(["docker", "volume", "rm", "--", name])
            require(completed.returncode == 0, "E_CLEANUP", f"cannot remove owned volume: {name}")
        for name in resources["networks"]:
            completed = run_command(["docker", "network", "rm", "--", name])
            require(completed.returncode == 0, "E_CLEANUP", f"cannot remove owned network: {name}")
        state = inspect_cleanup_state(child_id, require_port_free=False)
        if state["containers"] == state["volumes"] == state["networks"] == 0:
            return state
        time.sleep(0.25)
    reject("E_CLEANUP", f"derived project cleanup retries exhausted: {identity['project']}")


def wait_cleanup_state(child_id: str, *, attempts: int = 20) -> dict[str, int | bool]:
    for attempt in range(attempts):
        try:
            return inspect_cleanup_state(child_id, require_port_free=True)
        except FixtureError as exc:
            if exc.code != "E_CLEANUP" or attempt + 1 == attempts:
                raise
            time.sleep(0.25)
    reject("E_CLEANUP", "cleanup state retries exhausted")


def finalize_lifecycle_process(process: subprocess.Popen[bytes], child_id: str) -> None:
    if process.poll() is None:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            reject("E_LIFECYCLE", "lifecycle process resisted SIGKILL")
    cleanup_owned_resources(child_id)
    wait_cleanup_state(child_id)


def command_cleanup(args: argparse.Namespace) -> None:
    repo_root(args.repo_root)
    run_id = safe_run_id(args.run_id)
    child_id = fixture_run_id(run_id)
    state = cleanup_owned_resources(child_id)
    print(json.dumps({"run_id": run_id, "fixture_run_id": child_id, "project": project_name(child_id), "cleanup": state, "status": "passed"}, sort_keys=True))


def command_cleanup_lifecycle(args: argparse.Namespace) -> None:
    repo_root(args.repo_root)
    base_run_id = safe_run_id(args.run_id)
    states: list[dict[str, Any]] = []
    probe_names = [name for name, _signal, _code in LIFECYCLE_PROBE_SPECS] + ["callback-failure", "leader-kill-fallback"]
    for probe in probe_names:
        probe_run_id = lifecycle_probe_run_id(base_run_id, probe)
        child_id = fixture_run_id(probe_run_id)
        state = cleanup_owned_resources(child_id)
        states.append({"probe": probe, "fixture_run_id": child_id, "cleanup": state})
    print(json.dumps({"run_id": base_run_id, "probes": states, "status": "passed"}, sort_keys=True))


def lifecycle_ready_path(root: Path, child_id: str) -> Path:
    return cell_root(root, child_id) / "unit-lifecycle-ready.env"


def command_wait_callback(args: argparse.Namespace) -> None:
    root = repo_root(args.repo_root)
    run_id = safe_run_id(args.run_id)
    child_id = safe_run_id(args.fixture_run_id, "fixture run id")
    cell, identity = validate_callback_environment(root, run_id, child_id)
    ready = args.ready.expanduser().absolute()
    require(ready == lifecycle_ready_path(root, child_id), "E_OUTPUT", "lifecycle ready path differs")
    atomic_publish(ready, f"run_id={run_id}\nfixture_run_id={child_id}\nproject={identity['project']}\nstatus=ready\n".encode())
    while True:
        time.sleep(1)


def command_fail_callback(args: argparse.Namespace) -> None:
    root = repo_root(args.repo_root)
    run_id = safe_run_id(args.run_id)
    child_id = safe_run_id(args.fixture_run_id, "fixture run id")
    _cell, identity = validate_callback_environment(root, run_id, child_id)
    ready = args.ready.expanduser().absolute()
    require(ready == lifecycle_ready_path(root, child_id), "E_OUTPUT", "lifecycle ready path differs")
    atomic_publish(ready, f"run_id={run_id}\nfixture_run_id={child_id}\nproject={identity['project']}\nstatus=ready\n".encode())
    raise SystemExit(17)


def lifecycle_probe_run_id(base_run_id: str, probe: str) -> str:
    safe_run_id(base_run_id)
    scope = hashlib.sha256(f"{base_run_id}|{probe}\n".encode()).hexdigest()[:20]
    return f"unit-lifecycle-{scope}"


def prepare_lifecycle_probe(root: Path, run_id: str) -> tuple[str, Path]:
    child_id = fixture_run_id(run_id)
    run_root = root / "target/v934-step3-database-matrix/runs" / child_id
    require(not run_root.exists() and not run_root.is_symlink(), "E_OUTPUT", f"lifecycle run root already exists: {run_root}")
    (run_root / "cells").mkdir(parents=True)
    real_directory(run_root / "cells", "E_OUTPUT")
    return child_id, run_root / "cells/mysql57"


def start_lifecycle_provisioner(root: Path, run_id: str, callback_command: str) -> tuple[subprocess.Popen[bytes], str, Path]:
    child_id, cell = prepare_lifecycle_probe(root, run_id)
    ready = lifecycle_ready_path(root, child_id)
    arguments = [
        str(root / PROVISIONER_PATH), "run", DATABASE, child_id, str(cell), "--",
        sys.executable, str(root / SELF_PATH), callback_command,
        "--repo-root", str(root), "--run-id", run_id, "--fixture-run-id", child_id,
        "--ready", str(ready),
    ]
    environment = os.environ.copy()
    for key in ("V934_DB_STATE_AUTH", "V934_DB_STATE_PROBE", "V934_PARENT_RUN_ID"):
        environment.pop(key, None)
    environment["V934_AUTHORITY_LOCK_MODE"] = "standalone"
    try:
        process = subprocess.Popen(
            arguments,
            cwd=root,
            env=environment,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,
        )
    except OSError as exc:
        reject("E_LIFECYCLE", f"cannot launch lifecycle provisioner: {exc.__class__.__name__}")
    return process, child_id, ready


def wait_lifecycle_ready(process: subprocess.Popen[bytes], ready: Path, timeout_seconds: int = 180) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if ready.is_file() and not ready.is_symlink():
            return
        code = process.poll()
        require(code is None, "E_LIFECYCLE", f"lifecycle provisioner exited before callback ready: {code}")
        time.sleep(0.25)
    reject("E_LIFECYCLE", "lifecycle callback readiness timed out")


def normalized_exit_code(return_code: int) -> int:
    return 128 + (-return_code) if return_code < 0 else return_code


def lifecycle_row(probe: str, expected_exit: int, actual_exit: int, state: dict[str, int | bool]) -> dict[str, Any]:
    require(actual_exit == expected_exit, "E_LIFECYCLE", f"probe {probe} exit differs: {actual_exit}")
    require(state == {"containers": 0, "volumes": 0, "networks": 0, "port_free": True}, "E_LIFECYCLE", f"probe {probe} cleanup differs")
    return {
        "probe": probe,
        "expected_exit": expected_exit,
        "actual_exit": actual_exit,
        "containers": 0,
        "volumes": 0,
        "networks": 0,
        "port_free": True,
        "status": "passed",
    }


LIFECYCLE_PROBE_SPECS = (
    ("signal-int", signal.SIGINT, 130),
    ("signal-term", signal.SIGTERM, 143),
    ("signal-hup", signal.SIGHUP, 129),
)


def command_lifecycle_negative(args: argparse.Namespace) -> None:
    root = repo_root(args.repo_root)
    validate_fixture_contract(root)
    base_run_id = safe_run_id(args.run_id)
    require(inspect_cleanup_state(fixture_run_id(base_run_id), require_port_free=True)["port_free"] is True, "E_LIFECYCLE", "frozen MySQL port must be free")
    rows: list[dict[str, Any]] = []
    for probe, signal_number, expected_exit in LIFECYCLE_PROBE_SPECS:
        run_id = lifecycle_probe_run_id(base_run_id, probe)
        process: subprocess.Popen[bytes] | None = None
        child_id = ""
        try:
            process, child_id, ready = start_lifecycle_provisioner(root, run_id, "wait-callback")
            wait_lifecycle_ready(process, ready)
            os.killpg(process.pid, signal_number)
            try:
                return_code = process.wait(timeout=90)
            except subprocess.TimeoutExpired:
                reject("E_LIFECYCLE", f"probe {probe} did not exit after signal")
            state = wait_cleanup_state(child_id)
            rows.append(lifecycle_row(probe, expected_exit, normalized_exit_code(return_code), state))
        finally:
            if process is not None and child_id:
                finalize_lifecycle_process(process, child_id)

    failure_run = lifecycle_probe_run_id(base_run_id, "callback-failure")
    process = None
    child_id = ""
    try:
        process, child_id, ready = start_lifecycle_provisioner(root, failure_run, "fail-callback")
        wait_lifecycle_ready(process, ready)
        try:
            return_code = process.wait(timeout=90)
        except subprocess.TimeoutExpired:
            reject("E_LIFECYCLE", "callback-failure probe timed out")
        rows.append(lifecycle_row("callback-failure", 17, normalized_exit_code(return_code), wait_cleanup_state(child_id)))
    finally:
        if process is not None and child_id:
            finalize_lifecycle_process(process, child_id)

    kill_run = lifecycle_probe_run_id(base_run_id, "leader-kill-fallback")
    process = None
    child_id = ""
    try:
        process, child_id, ready = start_lifecycle_provisioner(root, kill_run, "wait-callback")
        wait_lifecycle_ready(process, ready)
        os.killpg(process.pid, signal.SIGKILL)
        return_code = process.wait(timeout=30)
        cleanup_owned_resources(child_id)
        rows.append(lifecycle_row("leader-kill-fallback", 137, normalized_exit_code(return_code), wait_cleanup_state(child_id)))
    finally:
        if process is not None and child_id:
            finalize_lifecycle_process(process, child_id)

    payload = {
        "schema_version": 1,
        "kind": "v934-step4-unit-mysql57-lifecycle-negative",
        "status": "passed",
        "run_id": base_run_id,
        "probes": rows,
        "totals": {"probes": len(rows), "passed": len(rows)},
    }
    validate_lifecycle_receipt(payload)
    output = args.output.expanduser().absolute()
    atomic_publish(output, json_bytes(payload))
    print(f"{PREFIX} lifecycle-negative PASS probes={len(rows)}/{len(rows)} output={output}")


def validate_lifecycle_receipt(payload: Any) -> dict[str, Any]:
    payload = exact_keys(payload, {"schema_version", "kind", "status", "run_id", "probes", "totals"}, "E_LIFECYCLE", "lifecycle receipt")
    require(type(payload["schema_version"]) is int and payload["schema_version"] == 1, "E_LIFECYCLE", "lifecycle schema differs")
    require(payload["kind"] == "v934-step4-unit-mysql57-lifecycle-negative" and payload["status"] == "passed", "E_LIFECYCLE", "lifecycle identity differs")
    safe_run_id(payload["run_id"])
    expected = [(name, code) for name, _signal, code in LIFECYCLE_PROBE_SPECS] + [("callback-failure", 17), ("leader-kill-fallback", 137)]
    probes = payload["probes"]
    require(type(probes) is list and len(probes) == len(expected), "E_LIFECYCLE", "lifecycle probe count differs")
    for row, (name, code) in zip(probes, expected, strict=True):
        row = exact_keys(row, {"probe", "expected_exit", "actual_exit", "containers", "volumes", "networks", "port_free", "status"}, "E_LIFECYCLE", "lifecycle probe")
        require(
            type(row["probe"]) is str
            and type(row["expected_exit"]) is int
            and type(row["actual_exit"]) is int
            and all(type(row[field]) is int for field in ("containers", "volumes", "networks"))
            and type(row["port_free"]) is bool
            and type(row["status"]) is str
            and row == {"probe": name, "expected_exit": code, "actual_exit": code, "containers": 0, "volumes": 0, "networks": 0, "port_free": True, "status": "passed"},
            "E_LIFECYCLE",
            f"lifecycle probe differs: {name}",
        )
    totals = exact_keys(payload["totals"], {"probes", "passed"}, "E_LIFECYCLE", "lifecycle totals")
    require(type(totals["probes"]) is int and type(totals["passed"]) is int and totals == {"probes": len(expected), "passed": len(expected)}, "E_LIFECYCLE", "lifecycle totals differ")
    return payload


def command_verify_lifecycle_negative(args: argparse.Namespace) -> None:
    payload = strict_json(args.path.expanduser().absolute())
    validate_lifecycle_receipt(payload)
    print(f"{PREFIX} verify-lifecycle-negative PASS probes={payload['totals']['passed']}")


def evidence_record(root: Path, path: Path) -> dict[str, Any]:
    payload = stable_file_bytes(path, "E_EVIDENCE")
    try:
        relative = path.relative_to(root).as_posix()
    except ValueError:
        reject("E_EVIDENCE", f"evidence escapes repository: {path}")
    return {"path": relative, "sha256": hashlib.sha256(payload).hexdigest(), "size_bytes": len(payload)}


def validate_cell(root: Path, run_id: str) -> dict[str, Any]:
    validate_fixture_contract(root)
    child_id = fixture_run_id(run_id)
    cell = cell_root(root, child_id)
    real_directory(cell, "E_EVIDENCE")
    actual_files = sorted(entry.name for entry in cell.iterdir())
    require(actual_files == sorted(CELL_FILES), "E_EVIDENCE", f"fixture cell exact files differ: {actual_files}")
    require(all(entry.is_file() and not entry.is_symlink() for entry in cell.iterdir()), "E_EVIDENCE", "fixture cell has a non-file descendant")
    identity = resource_identity(child_id)
    resource = read_env(cell / "resource.env", {"run_id", "database", "service", "project", "container", "volume", "network", "host_port", "container_port", "profile", "expected_image_ref", "expected_image_id"})
    expected_resource = {
        "run_id": child_id, "database": DATABASE, "service": SERVICE, **identity,
        "host_port": str(PORT), "container_port": "3306", "profile": PROFILE,
        "expected_image_ref": IMAGE_REF, "expected_image_id": IMAGE_ID,
    }
    require(resource == expected_resource, "E_IDENTITY", "fixture resource identity differs")
    runtime = read_env(cell / "runtime.env", {"database", "actual_image_id", "actual_image_ref", "actual_repo_digest", "actual_project", "actual_service", "actual_mapped_port", "volume_project", "network_project", "volume_created", "database_identity", "status"})
    require(
        runtime["database"] == DATABASE and runtime["actual_image_id"] == IMAGE_ID
        and runtime["actual_image_ref"] == runtime["actual_repo_digest"] == IMAGE_REF
        and runtime["actual_project"] == runtime["volume_project"] == runtime["network_project"] == identity["project"]
        and runtime["actual_service"] == SERVICE and runtime["actual_mapped_port"] == f"127.0.0.1:{PORT}"
        and runtime["database_identity"] == DATABASE_IDENTITY and runtime["status"] == "verified",
        "E_IDENTITY",
        "fixture runtime identity differs",
    )
    require(stable_file_bytes(cell / "database-identity.txt", "E_IDENTITY") == (DATABASE_IDENTITY + "\n").encode(), "E_IDENTITY", "database identity differs")
    status = read_env(cell / "status.env", {"run_id", "database", "project", "started_at", "finished_at", "last_phase", "exit_code", "cleanup_status", "fixture_before_sha256", "fixture_after_sha256", "status"})
    require(
        status["run_id"] == child_id and status["database"] == DATABASE and status["project"] == identity["project"]
        and status["last_phase"] == "completed" and status["exit_code"] == "0"
        and status["cleanup_status"] == status["status"] == "passed"
        and status["fixture_before_sha256"] == status["fixture_after_sha256"] == CANONICAL_FIXTURE_SHA256,
        "E_EVIDENCE",
        "provisioner status differs",
    )
    cleanup = read_env(cell / "cleanup.env", {"database", "project", "container", "volume", "network", "status"})
    require(cleanup == {"database": DATABASE, **identity, "status": "passed"}, "E_CLEANUP", "fixture cleanup receipt differs")
    for name in ("fixture-first.txt", "fixture-before.txt", "fixture-after.txt"):
        require(sha256_file(cell / name) == CANONICAL_FIXTURE_SHA256, "E_FIXTURE", f"canonical provisioner fixture differs: {name}")
    before = strict_json(cell / "unit-fixture-before.json")
    after = strict_json(cell / "unit-fixture-after.json")
    require(before == after == EXPECTED_SNAPSHOT, "E_FIXTURE", "Unit-specific fixture before/after differs")
    before_sha = sha256_file(cell / "unit-fixture-before.json")
    after_sha = sha256_file(cell / "unit-fixture-after.json")
    connection = validate_connection_receipt(strict_json(cell / "unit-connection-receipt.json"))
    connection_sha = sha256_file(cell / "unit-connection-receipt.json")
    callback = read_env(cell / "unit-fixture-status.env", {"run_id", "fixture_run_id", "database", "project", "maven_exit_code", "before_sha256", "after_sha256", "connection_receipt_sha256", "connection_count", "status"})
    require(
        callback == {
            "run_id": run_id, "fixture_run_id": child_id, "database": DATABASE,
            "project": identity["project"], "maven_exit_code": "0",
            "before_sha256": before_sha, "after_sha256": after_sha, "status": "passed",
            "connection_receipt_sha256": connection_sha,
            "connection_count": str(connection["connection_count"]),
        },
        "E_EVIDENCE",
        "Unit callback status differs",
    )
    cleanup_state = ensure_absent_resources(child_id)
    artifacts = [evidence_record(root, cell / name) for name in CELL_FILES]
    return {
        "schema_version": 1,
        "kind": "v934-step4-unit-mysql57-fixture",
        "status": "passed",
        "run_id": run_id,
        "fixture_run_id": child_id,
        "database": DATABASE,
        "host_port": PORT,
        "project": identity["project"],
        "image_ref": IMAGE_REF,
        "image_id": IMAGE_ID,
        "database_identity": DATABASE_IDENTITY,
        "bindings": {
            "tool": evidence_record(root, root / SELF_PATH),
            "provisioner": evidence_record(root, root / PROVISIONER_PATH),
            "fixture_contract": evidence_record(root, root / FIXTURE_CONTRACT_PATH),
            "datasource_adapter": evidence_record(root, root / DATASOURCE_ADAPTER_PATH),
            "step2_execution_inventory": evidence_record(root, root / STEP2_EXECUTION_PATH),
            "step2_discovery_inventory": evidence_record(root, root / STEP2_DISCOVERY_PATH),
            "reactor_freeze": evidence_record(root, root / REACTOR_FREEZE_PATH),
            "unit_ddl_sha256": hashlib.sha256(UNIT_DDL.encode()).hexdigest(),
            "reactor_modules_sha256": FROZEN_REACTOR_SHA256,
        },
        "fixture": {
            "before_sha256": before_sha,
            "after_sha256": after_sha,
            "table": "M_ETL_TEST",
            "row_count": 0,
            "connection_receipt_sha256": connection_sha,
            "connection_count": connection["connection_count"],
        },
        "cleanup": cleanup_state,
        "artifacts": artifacts,
    }


def validate_artifact_schema(record: Any) -> None:
    record = exact_keys(record, {"path", "sha256", "size_bytes"}, "E_SCHEMA", "artifact")
    require(type(record["path"]) is str and record["path"] and "\\" not in record["path"], "E_SCHEMA", "artifact path differs")
    pure = PurePosixPath(record["path"])
    require(not pure.is_absolute() and pure.as_posix() == record["path"] and all(part not in {"", ".", ".."} for part in pure.parts), "E_SCHEMA", "artifact path is unsafe")
    require(type(record["sha256"]) is str and SHA256_RE.fullmatch(record["sha256"]) is not None, "E_SCHEMA", "artifact SHA differs")
    require(type(record["size_bytes"]) is int and record["size_bytes"] > 0, "E_SCHEMA", "artifact size differs")


def validate_manifest_schema(payload: Any) -> None:
    payload = exact_keys(payload, {"schema_version", "kind", "status", "run_id", "fixture_run_id", "database", "host_port", "project", "image_ref", "image_id", "database_identity", "bindings", "fixture", "cleanup", "artifacts"}, "E_SCHEMA", "manifest")
    require(type(payload["schema_version"]) is int and payload["schema_version"] == 1 and payload["kind"] == "v934-step4-unit-mysql57-fixture" and payload["status"] == "passed", "E_SCHEMA", "manifest identity differs")
    run_id = safe_run_id(payload["run_id"])
    child_id = safe_run_id(payload["fixture_run_id"], "fixture run id")
    require(child_id == fixture_run_id(run_id), "E_IDENTITY", "manifest fixture run id differs")
    require(payload["database"] == DATABASE and type(payload["host_port"]) is int and payload["host_port"] == PORT, "E_IDENTITY", "manifest database coordinate differs")
    require(payload["project"] == project_name(child_id) and payload["image_ref"] == IMAGE_REF and payload["image_id"] == IMAGE_ID and payload["database_identity"] == DATABASE_IDENTITY, "E_IDENTITY", "manifest runtime identity differs")
    bindings = exact_keys(
        payload["bindings"],
        {"tool", "provisioner", "fixture_contract", "datasource_adapter", "step2_execution_inventory", "step2_discovery_inventory", "reactor_freeze", "unit_ddl_sha256", "reactor_modules_sha256"},
        "E_BINDING",
        "bindings",
    )
    for field in ("tool", "provisioner", "fixture_contract", "datasource_adapter", "step2_execution_inventory", "step2_discovery_inventory", "reactor_freeze"):
        validate_artifact_schema(bindings[field])
    require(
        bindings["tool"]["path"] == SELF_PATH.as_posix()
        and bindings["provisioner"]["path"] == PROVISIONER_PATH.as_posix()
        and bindings["fixture_contract"]["path"] == FIXTURE_CONTRACT_PATH.as_posix()
        and bindings["fixture_contract"]["sha256"] == FIXTURE_CONTRACT_SHA256
        and bindings["datasource_adapter"]["path"] == DATASOURCE_ADAPTER_PATH.as_posix()
        and bindings["datasource_adapter"]["sha256"] == DATASOURCE_ADAPTER_SHA256
        and bindings["step2_execution_inventory"]["path"] == STEP2_EXECUTION_PATH.as_posix()
        and bindings["step2_execution_inventory"]["sha256"] == STEP2_EXECUTION_SHA256
        and bindings["step2_discovery_inventory"]["path"] == STEP2_DISCOVERY_PATH.as_posix()
        and bindings["step2_discovery_inventory"]["sha256"] == STEP2_DISCOVERY_SHA256
        and bindings["reactor_freeze"]["path"] == REACTOR_FREEZE_PATH.as_posix()
        and bindings["reactor_freeze"]["sha256"] == REACTOR_FREEZE_SHA256
        and bindings["unit_ddl_sha256"] == hashlib.sha256(UNIT_DDL.encode()).hexdigest()
        and bindings["reactor_modules_sha256"] == FROZEN_REACTOR_SHA256,
        "E_BINDING",
        "manifest tool/contract/DDL binding differs",
    )
    fixture = exact_keys(payload["fixture"], {"before_sha256", "after_sha256", "table", "row_count", "connection_receipt_sha256", "connection_count"}, "E_FIXTURE", "fixture")
    require(
        type(fixture["before_sha256"]) is str
        and fixture["before_sha256"] == fixture["after_sha256"]
        and SHA256_RE.fullmatch(fixture["before_sha256"]) is not None
        and fixture["table"] == "M_ETL_TEST"
        and type(fixture["row_count"]) is int
        and fixture["row_count"] == 0
        and type(fixture["connection_receipt_sha256"]) is str
        and SHA256_RE.fullmatch(fixture["connection_receipt_sha256"]) is not None
        and type(fixture["connection_count"]) is int
        and fixture["connection_count"] > 0,
        "E_FIXTURE",
        "manifest fixture seal differs",
    )
    cleanup = exact_keys(payload["cleanup"], {"containers", "volumes", "networks", "port_free"}, "E_CLEANUP", "cleanup")
    require(
        type(cleanup["containers"]) is int
        and type(cleanup["volumes"]) is int
        and type(cleanup["networks"]) is int
        and type(cleanup["port_free"]) is bool
        and cleanup == {"containers": 0, "volumes": 0, "networks": 0, "port_free": True},
        "E_CLEANUP",
        "manifest cleanup differs",
    )
    require(type(payload["artifacts"]) is list and len(payload["artifacts"]) == len(CELL_FILES), "E_EVIDENCE", "manifest artifact count differs")
    for record in payload["artifacts"]:
        validate_artifact_schema(record)
    expected_prefix = f"target/v934-step3-database-matrix/runs/{child_id}/cells/mysql57/"
    require([record["path"] for record in payload["artifacts"]] == [expected_prefix + name for name in CELL_FILES], "E_EVIDENCE", "manifest artifact paths differ")
    artifact_by_path = {record["path"]: record for record in payload["artifacts"]}
    require(
        artifact_by_path[expected_prefix + "unit-connection-receipt.json"]["sha256"] == fixture["connection_receipt_sha256"],
        "E_FIXTURE",
        "connection receipt artifact binding differs",
    )


def command_build(args: argparse.Namespace) -> None:
    root = repo_root(args.repo_root)
    run_id = safe_run_id(args.run_id)
    output = args.output.expanduser().absolute()
    expected = root / f"target/v934-step2-unit/runs/{run_id}/mysql57-fixture-manifest.json"
    require(output == expected, "E_OUTPUT", f"manifest output must equal {expected}")
    payload = validate_cell(root, run_id)
    validate_manifest_schema(payload)
    atomic_publish(output, json_bytes(payload))
    print(f"{PREFIX} build PASS run={run_id} output={output}")


def command_verify(args: argparse.Namespace) -> None:
    root = repo_root(args.repo_root)
    run_id = safe_run_id(args.run_id)
    manifest = args.manifest.expanduser().absolute()
    expected_path = root / f"target/v934-step2-unit/runs/{run_id}/mysql57-fixture-manifest.json"
    require(manifest == expected_path, "E_EVIDENCE", "manifest path differs")
    observed = strict_json(manifest)
    validate_manifest_schema(observed)
    expected = validate_cell(root, run_id)
    require(observed == expected, "E_EVIDENCE", "fixture manifest differs from live evidence")
    print(f"{PREFIX} verify PASS run={run_id} residue=0/0/0 port={PORT}:free")


def synthetic_manifest() -> dict[str, Any]:
    run_id = "negative-run"
    child_id = fixture_run_id(run_id)
    digest = "a" * 64
    prefix = f"target/v934-step3-database-matrix/runs/{child_id}/cells/mysql57/"
    artifact = lambda path, sha=digest: {"path": path, "sha256": sha, "size_bytes": 1}
    return {
        "schema_version": 1, "kind": "v934-step4-unit-mysql57-fixture", "status": "passed",
        "run_id": run_id, "fixture_run_id": child_id, "database": DATABASE, "host_port": PORT,
        "project": project_name(child_id), "image_ref": IMAGE_REF, "image_id": IMAGE_ID,
        "database_identity": DATABASE_IDENTITY,
        "bindings": {
            "tool": artifact(SELF_PATH.as_posix()),
            "provisioner": artifact(PROVISIONER_PATH.as_posix()),
            "fixture_contract": artifact(FIXTURE_CONTRACT_PATH.as_posix(), FIXTURE_CONTRACT_SHA256),
            "datasource_adapter": artifact(DATASOURCE_ADAPTER_PATH.as_posix(), DATASOURCE_ADAPTER_SHA256),
            "step2_execution_inventory": artifact(STEP2_EXECUTION_PATH.as_posix(), STEP2_EXECUTION_SHA256),
            "step2_discovery_inventory": artifact(STEP2_DISCOVERY_PATH.as_posix(), STEP2_DISCOVERY_SHA256),
            "reactor_freeze": artifact(REACTOR_FREEZE_PATH.as_posix(), REACTOR_FREEZE_SHA256),
            "unit_ddl_sha256": hashlib.sha256(UNIT_DDL.encode()).hexdigest(),
            "reactor_modules_sha256": FROZEN_REACTOR_SHA256,
        },
        "fixture": {"before_sha256": digest, "after_sha256": digest, "table": "M_ETL_TEST", "row_count": 0, "connection_receipt_sha256": digest, "connection_count": 1},
        "cleanup": {"containers": 0, "volumes": 0, "networks": 0, "port_free": True},
        "artifacts": [artifact(prefix + name) for name in CELL_FILES],
    }


def command_negative(args: argparse.Namespace) -> None:
    probes: list[dict[str, str]] = []
    validate_manifest_schema(synthetic_manifest())

    def probe(name: str, code: str, mutate: Callable[[dict[str, Any]], None]) -> None:
        payload = synthetic_manifest()
        mutate(payload)
        try:
            validate_manifest_schema(payload)
        except FixtureError as exc:
            actual = exc.code
        else:
            actual = "none"
        require(actual == code, "E_NEGATIVE", f"probe {name} actual={actual} expected={code}")
        probes.append({"probe": name, "expected_error": code, "actual_error": actual, "status": "passed"})

    def direct_probe(name: str, code: str, action: Callable[[], None]) -> None:
        try:
            action()
        except FixtureError as exc:
            actual = exc.code
        else:
            actual = "none"
        require(actual == code, "E_NEGATIVE", f"probe {name} actual={actual} expected={code}")
        probes.append({"probe": name, "expected_error": code, "actual_error": actual, "status": "passed"})

    probe("extra-manifest-field", "E_SCHEMA", lambda p: p.__setitem__("forged", True))
    probe("wrong-fixture-run", "E_IDENTITY", lambda p: p.__setitem__("fixture_run_id", "unit-mysql57-forged"))
    probe("wrong-project", "E_IDENTITY", lambda p: p.__setitem__("project", "forged"))
    probe("wrong-port", "E_IDENTITY", lambda p: p.__setitem__("host_port", 3306))
    probe("wrong-image", "E_IDENTITY", lambda p: p.__setitem__("image_id", "sha256:" + "b" * 64))
    probe("wrong-database-identity", "E_IDENTITY", lambda p: p.__setitem__("database_identity", "foggy_test|8.0.0"))
    probe("wrong-tool-binding", "E_BINDING", lambda p: p["bindings"]["tool"].__setitem__("path", "forged.py"))
    probe("wrong-contract-binding", "E_BINDING", lambda p: p["bindings"]["fixture_contract"].__setitem__("path", "forged.json"))
    probe("wrong-datasource-adapter-binding", "E_BINDING", lambda p: p["bindings"]["datasource_adapter"].__setitem__("sha256", "b" * 64))
    probe("wrong-ddl-binding", "E_BINDING", lambda p: p["bindings"].__setitem__("unit_ddl_sha256", "b" * 64))
    probe("wrong-reactor-binding", "E_BINDING", lambda p: p["bindings"].__setitem__("reactor_modules_sha256", "b" * 64))
    probe("fixture-mutation", "E_FIXTURE", lambda p: p["fixture"].__setitem__("after_sha256", "b" * 64))
    probe("connection-receipt-mutation", "E_FIXTURE", lambda p: p["fixture"].__setitem__("connection_receipt_sha256", "b" * 64))
    probe("boolean-connection-count", "E_FIXTURE", lambda p: p["fixture"].__setitem__("connection_count", True))
    connection_receipt = {
        "schema_version": 1,
        "kind": "v934-step4-unit-mysql57-connection-receipt",
        "status": "passed",
        "username": UNIT_DATABASE_USER,
        "jdbc_url": UNIT_DATABASE_URL,
        "observation_scope": CONNECTION_OBSERVATION_SCOPE,
        "observation_closed": True,
        "connection_count": 1,
        "connection_ids": [1],
        "connections": [{"connection_id": 1, "user_value": f"{UNIT_DATABASE_USER}@localhost"}],
    }

    def connection_probe(name: str, mutate: Callable[[dict[str, Any]], None]) -> None:
        candidate = json.loads(json.dumps(connection_receipt))
        mutate(candidate)
        direct_probe(name, "E_DATASOURCE", lambda: validate_connection_receipt(candidate))

    connection_probe("boolean-connection-schema", lambda p: p.__setitem__("schema_version", True))
    connection_probe("boolean-connection-receipt-count", lambda p: p.__setitem__("connection_count", True))
    connection_probe("empty-connection-ids", lambda p: p.__setitem__("connection_ids", []))
    connection_probe("duplicate-connection-ids", lambda p: p.update({"connection_count": 2, "connection_ids": [1, 1]}))
    connection_probe("wrong-connection-observation-scope", lambda p: p.__setitem__("observation_scope", "fixture-lifetime"))
    connection_probe("open-connection-observation", lambda p: p.__setitem__("observation_closed", False))
    connection_probe("wrong-observed-connection-user", lambda p: p["connections"][0].__setitem__("user_value", "foggy@localhost"))
    direct_probe(
        "ambient-fixture-environment",
        "E_DATASOURCE",
        lambda: validate_controlled_environment({DATASOURCE_ENV_KEYS[0]: "forged"}, {}),
    )
    direct_probe(
        "global-spring-override",
        "E_DATASOURCE",
        lambda: validate_controlled_environment({"MAVEN_ARGS": "-Dspring.datasource.url=forged"}, {}),
    )
    direct_probe(
        "dotted-fixture-environment",
        "E_DATASOURCE",
        lambda: validate_controlled_environment({"v934.unit.mysql57.url": "forged"}, {}),
    )
    direct_probe(
        "dotted-spring-environment",
        "E_DATASOURCE",
        lambda: validate_controlled_environment({"spring.datasource.url": "forged"}, {}),
    )
    direct_probe(
        "option-argument-indirection",
        "E_DATASOURCE",
        lambda: validate_controlled_environment({"JDK_JAVA_OPTIONS": "@/tmp/forged.args"}, {}),
    )
    probe("cleanup-residue", "E_CLEANUP", lambda p: p["cleanup"].__setitem__("containers", 1))
    probe("boolean-cleanup-count", "E_CLEANUP", lambda p: p["cleanup"].__setitem__("containers", False))
    probe("port-occupied", "E_CLEANUP", lambda p: p["cleanup"].__setitem__("port_free", False))
    probe("integer-port-free", "E_CLEANUP", lambda p: p["cleanup"].__setitem__("port_free", 1))
    probe("missing-artifact", "E_EVIDENCE", lambda p: p["artifacts"].pop())
    probe("invalid-artifact-sha", "E_SCHEMA", lambda p: p["artifacts"][0].__setitem__("sha256", "not-a-sha"))
    probe("boolean-artifact-size", "E_SCHEMA", lambda p: p["artifacts"][0].__setitem__("size_bytes", True))
    with tempfile.TemporaryDirectory(prefix="v934-unit-mysql-publisher-negative-") as temporary_name:
        temporary = Path(temporary_name)
        existing = temporary / "existing.json"
        atomic_publish(existing, b"{}\n")
        direct_probe("publisher-refuse-overwrite", "E_OUTPUT", lambda: atomic_publish(existing, b"{}\n"))
        target = temporary / "target.json"
        target.write_bytes(b"{}\n")
        symlink = temporary / "symlink.json"
        symlink.symlink_to(target.name)
        direct_probe("publisher-refuse-symlink", "E_OUTPUT", lambda: atomic_publish(symlink, b"{}\n"))

        rollback = temporary / "rollback.json"

        def rollback_after_link() -> None:
            original_stat = os.stat
            calls = 0
            captured: FixtureError | None = None

            def injected_stat(path: Any, *args: Any, **kwargs: Any) -> os.stat_result:
                nonlocal calls
                if path == rollback.name and kwargs.get("dir_fd") is not None:
                    calls += 1
                    if calls == 2:
                        raise OSError(errno.EIO, "injected final-stat failure")
                return original_stat(path, *args, **kwargs)

            try:
                os.stat = injected_stat  # type: ignore[assignment]
                try:
                    atomic_publish(rollback, b"{}\n")
                except FixtureError as exc:
                    captured = exc
            finally:
                os.stat = original_stat  # type: ignore[assignment]
            require(captured is not None and captured.code == "E_OUTPUT", "E_NEGATIVE", "publisher rollback did not fail at the injected boundary")
            require(not rollback.exists() and not rollback.is_symlink(), "E_NEGATIVE", "publisher rollback left a destination file")
            raise captured

        direct_probe("publisher-rollback-after-link", "E_OUTPUT", rollback_after_link)
    require(tuple((row["probe"], row["expected_error"]) for row in probes) == NEGATIVE_PROBE_SPECS, "E_NEGATIVE", "negative probe inventory differs")
    payload = {"schema_version": 1, "kind": "v934-step4-unit-mysql57-negative", "status": "passed", "probes": probes, "totals": {"probes": len(probes), "passed": len(probes)}}
    validate_negative_receipt(payload)

    def receipt_probe(mutate: Callable[[dict[str, Any]], None]) -> None:
        candidate = json.loads(json.dumps(payload))
        mutate(candidate)
        try:
            validate_negative_receipt(candidate)
        except FixtureError as exc:
            actual = exc.code
        else:
            actual = "none"
        require(actual == "E_NEGATIVE", "E_NEGATIVE", f"negative receipt schema probe actual={actual}")

    receipt_probe(lambda p: p.__setitem__("schema_version", True))
    receipt_probe(lambda p: p["totals"].__setitem__("probes", float(len(probes))))
    receipt_probe(lambda p: p["probes"][0].__setitem__("status", True))
    receipt_probe(lambda p: p.__setitem__("forged", True))
    output = args.output.expanduser().absolute()
    atomic_publish(output, json_bytes(payload))
    print(f"{PREFIX} negative PASS probes={len(probes)}/{len(probes)} receipt-schema=4/4 connection-schema=7/7 profile-boundary=6/6 publisher=3/3 output={output}")


def validate_negative_receipt(payload: Any) -> dict[str, Any]:
    payload = exact_keys(payload, {"schema_version", "kind", "status", "probes", "totals"}, "E_NEGATIVE", "negative receipt")
    require(type(payload["schema_version"]) is int and payload["schema_version"] == 1, "E_NEGATIVE", "negative receipt schema differs")
    require(payload["kind"] == "v934-step4-unit-mysql57-negative" and payload["status"] == "passed", "E_NEGATIVE", "negative receipt identity differs")
    probes = payload["probes"]
    require(type(probes) is list and len(probes) == len(NEGATIVE_PROBE_SPECS), "E_NEGATIVE", "negative receipt probe count differs")
    for row, (name, code) in zip(probes, NEGATIVE_PROBE_SPECS, strict=True):
        row = exact_keys(row, {"probe", "expected_error", "actual_error", "status"}, "E_NEGATIVE", "negative probe")
        require(
            all(type(row[field]) is str for field in row)
            and row == {"probe": name, "expected_error": code, "actual_error": code, "status": "passed"},
            "E_NEGATIVE",
            f"negative probe differs: {name}",
        )
    totals = exact_keys(payload["totals"], {"probes", "passed"}, "E_NEGATIVE", "negative totals")
    require(
        type(totals["probes"]) is int
        and type(totals["passed"]) is int
        and totals == {"probes": len(NEGATIVE_PROBE_SPECS), "passed": len(NEGATIVE_PROBE_SPECS)},
        "E_NEGATIVE",
        "negative totals differ",
    )
    return payload


def command_verify_negative(args: argparse.Namespace) -> None:
    path = args.path.expanduser().absolute()
    observed = strict_json(path)
    validate_negative_receipt(observed)
    with tempfile.TemporaryDirectory(prefix="v934-unit-mysql-negative-") as temporary:
        expected_path = Path(temporary) / "negative.json"
        namespace = argparse.Namespace(output=expected_path)
        command_negative(namespace)
        expected = strict_json(expected_path)
        validate_negative_receipt(expected)
    require(observed == expected, "E_NEGATIVE", "negative evidence differs")
    print(f"{PREFIX} verify-negative PASS probes={observed['totals']['passed']}")


def command_derive(args: argparse.Namespace) -> None:
    run_id = safe_run_id(args.run_id)
    child_id = fixture_run_id(run_id)
    print(json.dumps({"run_id": run_id, "fixture_run_id": child_id, "project": project_name(child_id)}, sort_keys=True))


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="command", required=True)
    derive = commands.add_parser("derive")
    derive.add_argument("--run-id", required=True)
    derive.set_defaults(func=command_derive)
    callback = commands.add_parser("callback")
    callback.add_argument("--repo-root", type=Path, required=True)
    callback.add_argument("--run-id", required=True)
    callback.add_argument("--fixture-run-id", required=True)
    callback.add_argument("--reactor-modules", required=True)
    callback.add_argument("--coverage-exec", required=True)
    callback.add_argument("--session-id", required=True)
    callback.set_defaults(func=command_callback)
    wait_callback = commands.add_parser("wait-callback")
    wait_callback.add_argument("--repo-root", type=Path, required=True)
    wait_callback.add_argument("--run-id", required=True)
    wait_callback.add_argument("--fixture-run-id", required=True)
    wait_callback.add_argument("--ready", type=Path, required=True)
    wait_callback.set_defaults(func=command_wait_callback)
    fail_callback = commands.add_parser("fail-callback")
    fail_callback.add_argument("--repo-root", type=Path, required=True)
    fail_callback.add_argument("--run-id", required=True)
    fail_callback.add_argument("--fixture-run-id", required=True)
    fail_callback.add_argument("--ready", type=Path, required=True)
    fail_callback.set_defaults(func=command_fail_callback)
    build = commands.add_parser("build")
    build.add_argument("--repo-root", type=Path, required=True)
    build.add_argument("--run-id", required=True)
    build.add_argument("--output", type=Path, required=True)
    build.set_defaults(func=command_build)
    verify = commands.add_parser("verify")
    verify.add_argument("--repo-root", type=Path, required=True)
    verify.add_argument("--run-id", required=True)
    verify.add_argument("--manifest", type=Path, required=True)
    verify.set_defaults(func=command_verify)
    negative = commands.add_parser("negative")
    negative.add_argument("--output", type=Path, required=True)
    negative.set_defaults(func=command_negative)
    verify_negative = commands.add_parser("verify-negative")
    verify_negative.add_argument("--path", type=Path, required=True)
    verify_negative.set_defaults(func=command_verify_negative)
    cleanup = commands.add_parser("cleanup")
    cleanup.add_argument("--repo-root", type=Path, required=True)
    cleanup.add_argument("--run-id", required=True)
    cleanup.set_defaults(func=command_cleanup)
    cleanup_lifecycle = commands.add_parser("cleanup-lifecycle")
    cleanup_lifecycle.add_argument("--repo-root", type=Path, required=True)
    cleanup_lifecycle.add_argument("--run-id", required=True)
    cleanup_lifecycle.set_defaults(func=command_cleanup_lifecycle)
    lifecycle_negative = commands.add_parser("lifecycle-negative")
    lifecycle_negative.add_argument("--repo-root", type=Path, required=True)
    lifecycle_negative.add_argument("--run-id", required=True)
    lifecycle_negative.add_argument("--output", type=Path, required=True)
    lifecycle_negative.set_defaults(func=command_lifecycle_negative)
    verify_lifecycle_negative = commands.add_parser("verify-lifecycle-negative")
    verify_lifecycle_negative.add_argument("--path", type=Path, required=True)
    verify_lifecycle_negative.set_defaults(func=command_verify_lifecycle_negative)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        args.func(args)
    except FixtureError as exc:
        print(f"{PREFIX} ERROR {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print(f"{PREFIX} ERROR E_SIGNAL: interrupted", file=sys.stderr)
        return 130
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
