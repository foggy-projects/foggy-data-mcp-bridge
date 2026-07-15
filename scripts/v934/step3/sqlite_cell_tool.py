#!/usr/bin/env python3
"""Provision and verify the run-scoped SQLite cell for the V934 DB matrix."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import re
import sqlite3
import sys


EXPECTED_JAR_SHA256 = "53174d76087bb73cc29db9c02766fb921fd7fc652f7952f3609e0018e3dd5ded"
EXPECTED_SNAPSHOT = "\n".join(
    (
        "sentinel|contract_version|9.3.4",
        "parity|V934_PARITY_SENTINEL|1",
        "parity|V934_PARITY_SENTINEL|2",
        "preagg|V934_ALPHA|50.0000",
        "preagg|V934_BETA|40.0000",
        "preagg|V934_GAMMA|10.0000",
    )
) + "\n"

SCHEMA_SCRIPTS = (
    "01-schema.sql",
    "04-preagg-schema.sql",
    "06-odoo-schema.sql",
    "08-odoo-closure-schema.sql",
)
DATA_SCRIPTS = (
    "02-dict-data.sql",
    "03-test-data.sql",
    "11-v934-parity-fixture.sql",
    "12-v934-preagg-schema.sql",
    "13-v934-preagg-data.sql",
    "05-preagg-data.sql",
    "07-odoo-data.sql",
    "09-odoo-closure-data.sql",
    "10-v934-sentinel.sql",
)
V934_REAPPLY_SCRIPTS = (
    "11-v934-parity-fixture.sql",
    "12-v934-preagg-schema.sql",
    "13-v934-preagg-data.sql",
    "10-v934-sentinel.sql",
)


class ContractError(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, path)


def validate_paths(root: Path, cell_root: Path, database_file: Path) -> tuple[Path, Path, Path]:
    root = root.resolve(strict=True)
    expected_parent = (root / "target/v934-step3-database-matrix/runs").resolve()
    lexical_cell_root = cell_root.expanduser().absolute()
    lexical_database_file = database_file.expanduser().absolute()
    if lexical_cell_root.is_symlink() or lexical_database_file.is_symlink():
        raise ContractError("E_CELL_ROOT: SQLite authority paths must not be symlinks")
    cell_root = lexical_cell_root.resolve(strict=False)
    database_file = lexical_database_file.resolve(strict=False)
    try:
        relative_cell = cell_root.relative_to(expected_parent)
    except ValueError as error:
        raise ContractError(
            "E_CELL_ROOT: cell root is outside the V934 database run root"
        ) from error
    if (
        len(relative_cell.parts) != 3
        or not re.fullmatch(r"[A-Za-z0-9._-]+", relative_cell.parts[0])
        or relative_cell.parts[1:] != ("cells", "sqlite")
    ):
        raise ContractError(
            "E_CELL_ROOT: cell root must be exactly <run-id>/cells/sqlite"
        )
    run_root = expected_parent / relative_cell.parts[0]
    cell_parent = run_root / "cells"
    if (
        not run_root.is_dir()
        or run_root.is_symlink()
        or not cell_parent.is_dir()
        or cell_parent.is_symlink()
    ):
        raise ContractError("E_CELL_ROOT: run root and cells parent must be regular directories")
    if database_file.parent != cell_root:
        raise ContractError("E_SQLITE_PATH: SQLite file must be a direct child of the cell root")
    if database_file.name != "database.sqlite":
        raise ContractError("E_SQLITE_PATH: SQLite filename must be database.sqlite")
    return root, cell_root, database_file


def execute_scripts(connection: sqlite3.Connection, script_root: Path, names: tuple[str, ...]) -> None:
    for name in names:
        path = script_root / name
        if not path.is_file():
            raise ContractError(f"E_INPUT: missing SQLite fixture script: {path}")
        connection.executescript(path.read_text(encoding="utf-8"))
    connection.commit()


def canonical_snapshot(database_file: Path) -> str:
    if not database_file.is_file():
        raise ContractError(f"E_SQLITE_MISSING: database file is missing: {database_file}")
    uri = f"file:{database_file}?mode=ro"
    with sqlite3.connect(uri, uri=True) as connection:
        rows: list[str] = []
        rows.extend(
            f"sentinel|{key}|{value}"
            for key, value in connection.execute(
                "SELECT sentinel_key, sentinel_value "
                "FROM v934_test_sentinel ORDER BY sentinel_key"
            )
        )
        rows.extend(
            f"parity|{order_id}|{line_no}"
            for order_id, line_no in connection.execute(
                "SELECT order_id, order_line_no FROM fact_sales "
                "WHERE order_id = 'V934_PARITY_SENTINEL' ORDER BY order_line_no"
            )
        )
        rows.extend(
            f"preagg|{category}|{float(total):.4f}"
            for category, total in connection.execute(
                "SELECT category_name, SUM(sales_amount_sum) "
                "FROM v934_preagg_daily_product_sales "
                "GROUP BY category_name ORDER BY category_name"
            )
        )
    snapshot = "\n".join(rows) + "\n"
    if snapshot != EXPECTED_SNAPSHOT:
        raise ContractError("E_FIXTURE_SNAPSHOT: canonical SQLite fixture is not exact")
    return snapshot


def prepare(args: argparse.Namespace) -> None:
    root, cell_root, database_file = validate_paths(
        Path(args.root), Path(args.cell_root), Path(args.database_file)
    )
    if cell_root.exists():
        raise ContractError(f"E_CELL_ROOT: cell root already exists: {cell_root}")
    cell_root.mkdir(parents=True)
    if database_file.exists():
        raise ContractError(f"E_SQLITE_STALE: database file already exists: {database_file}")

    jar = Path(args.sqlite_jar).expanduser().resolve(strict=True)
    jar_sha = sha256_file(jar)
    if jar_sha != EXPECTED_JAR_SHA256:
        raise ContractError(
            f"E_SQLITE_JAR: artifact SHA-256 is {jar_sha}, expected {EXPECTED_JAR_SHA256}"
        )

    script_root = root / "foggy-dataset-model/src/test/resources/sqlite"
    with sqlite3.connect(database_file) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        execute_scripts(connection, script_root, SCHEMA_SCRIPTS)
        execute_scripts(connection, script_root, DATA_SCRIPTS)
    first = canonical_snapshot(database_file)
    atomic_write(cell_root / "fixture-first.txt", first)

    with sqlite3.connect(database_file) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        execute_scripts(connection, script_root, V934_REAPPLY_SCRIPTS)
    before = canonical_snapshot(database_file)
    if first != before:
        raise ContractError("E_FIXTURE_IDEMPOTENCY: first and second SQLite snapshots differ")
    atomic_write(cell_root / "fixture-before.txt", before)
    before_sha = hashlib.sha256(before.encode("utf-8")).hexdigest()
    atomic_write(
        cell_root / "resource.env",
        "".join(
            (
                "database=sqlite\n",
                f"database_file={database_file}\n",
                f"jdbc_url=jdbc:sqlite:{database_file}\n",
                f"sqlite_python_version={sqlite3.sqlite_version}\n",
                f"sqlite_jdbc_jar_before_sha256={jar_sha}\n",
                f"fixture_before_sha256={before_sha}\n",
                "status=prepared\n",
            )
        ),
    )
    print(f"V934_SQLITE_CELL status=prepared fixture_sha256={before_sha}")


def verify(args: argparse.Namespace) -> None:
    _, cell_root, database_file = validate_paths(
        Path(args.root), Path(args.cell_root), Path(args.database_file)
    )
    before_path = cell_root / "fixture-before.txt"
    if not before_path.is_file():
        raise ContractError("E_FIXTURE_BEFORE: prepared snapshot is missing")
    before = before_path.read_text(encoding="utf-8")
    if before != EXPECTED_SNAPSHOT:
        raise ContractError("E_FIXTURE_BEFORE: prepared snapshot is not exact")
    jar = Path(args.sqlite_jar).expanduser().resolve(strict=True)
    jar_sha = sha256_file(jar)
    if jar_sha != EXPECTED_JAR_SHA256:
        raise ContractError(
            f"E_SQLITE_JAR: post-execution artifact SHA-256 is {jar_sha}, "
            f"expected {EXPECTED_JAR_SHA256}"
        )
    after = canonical_snapshot(database_file)
    atomic_write(cell_root / "fixture-after.txt", after)
    before_sha = hashlib.sha256(before.encode("utf-8")).hexdigest()
    after_sha = hashlib.sha256(after.encode("utf-8")).hexdigest()
    if before_sha != after_sha or before != after:
        raise ContractError("E_FIXTURE_MUTATION: SQLite before/after snapshot differs")
    atomic_write(
        cell_root / "verification.env",
        "".join(
            (
                "database=sqlite\n",
                f"sqlite_jdbc_jar_after_sha256={jar_sha}\n",
                f"fixture_before_sha256={before_sha}\n",
                f"fixture_after_sha256={after_sha}\n",
                "status=passed\n",
            )
        ),
    )
    print(f"V934_SQLITE_CELL status=verified fixture_sha256={after_sha}")


def cleanup(args: argparse.Namespace) -> None:
    _, cell_root, database_file = validate_paths(
        Path(args.root), Path(args.cell_root), Path(args.database_file)
    )
    for suffix in ("", "-wal", "-shm", "-journal"):
        candidate = Path(f"{database_file}{suffix}")
        if candidate.is_symlink() or candidate.exists():
            if candidate.is_symlink() or not candidate.is_file():
                raise ContractError(f"E_SQLITE_CLEANUP: unsafe artifact: {candidate}")
            candidate.unlink()
    residue = [
        path
        for path in database_file.parent.glob("database.sqlite*")
        if path.is_symlink() or path.exists()
    ]
    if residue:
        raise ContractError(f"E_SQLITE_CLEANUP: SQLite residue remains: {residue}")
    atomic_write(cell_root / "cleanup.env", "database=sqlite\nstatus=passed\n")
    print("V934_SQLITE_CELL status=cleaned")


def parser() -> argparse.ArgumentParser:
    command_parser = argparse.ArgumentParser()
    subparsers = command_parser.add_subparsers(dest="command", required=True)
    for command, handler in (("prepare", prepare), ("verify", verify), ("cleanup", cleanup)):
        child = subparsers.add_parser(command)
        child.add_argument("--root", required=True)
        child.add_argument("--cell-root", required=True)
        child.add_argument("--database-file", required=True)
        if command in {"prepare", "verify"}:
            child.add_argument("--sqlite-jar", required=True)
        child.set_defaults(handler=handler)
    return command_parser


def main() -> int:
    args = parser().parse_args()
    try:
        args.handler(args)
    except (ContractError, OSError, sqlite3.Error) as error:
        print(f"[v934-sqlite-cell] ERROR: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
