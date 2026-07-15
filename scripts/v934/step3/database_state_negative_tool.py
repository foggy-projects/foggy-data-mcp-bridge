#!/usr/bin/env python3
"""Versioned DB/resource-state negative harness for 9.3.4 Step 3.

The harness deliberately separates three evidence layers:

* evidence-tamper: exact cell evidence is changed and the production matrix
  verifier must reject it;
* runtime-lightweight: port ownership and stale run-owned resource checks that
  do not start a database or alter an existing listener;
* runtime-dynamic: run-scoped MySQL 5.7 failure, mutation, cleanup and signal
  probes. These require the frozen 13306 endpoint to be free.

Every Docker resource created here is derived from an immutable child run id.
The harness never stops, starts, removes or reuses a resource that is not owned
by that child id.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import errno
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
from typing import Any

import database_matrix_report_tool as matrix


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
STATE_CONTRACT_PATH = SCRIPT_DIR / "database_state_contract.json"
MATRIX_CONTRACT_PATH = SCRIPT_DIR / "database-matrix-contract.json"
PROVISIONER = SCRIPT_DIR / "provision-database-cell.sh"
CALLBACK = SCRIPT_DIR / "database_state_probe_callback.sh"
STATE_RUNS_ROOT = REPO_ROOT / "target/v934-step3-database-state/runs"
MATRIX_RUNS_ROOT = REPO_ROOT / "target/v934-step3-database-matrix/runs"
AUTH_VALUE = "v934-database-state-negative-v1"
MYSQL57_PORT = 13306

TSV_FIELDS = (
    "probe",
    "layer",
    "database",
    "expected_error",
    "actual_error",
    "expected_exit",
    "actual_exit",
    "cleanup_status",
    "child_container_residue",
    "child_volume_residue",
    "child_network_residue",
    "final_container_residue",
    "final_volume_residue",
    "final_network_residue",
    "status",
)

EXACT_PROBES = (
    ("wrong-image-id", "evidence-tamper", "mysql57", "E_CELL_EVIDENCE", 1),
    ("wrong-image-ref", "evidence-tamper", "mysql57", "E_CELL_EVIDENCE", 1),
    ("wrong-version", "evidence-tamper", "mysql57", "E_CELL_EVIDENCE", 1),
    ("wrong-product", "evidence-tamper", "mysql57", "E_CELL_EVIDENCE", 1),
    ("wrong-major", "evidence-tamper", "mysql8", "E_CELL_EVIDENCE", 1),
    ("wrong-catalog", "evidence-tamper", "postgres15", "E_CELL_EVIDENCE", 1),
    ("wrong-schema", "evidence-tamper", "postgres15", "E_CELL_EVIDENCE", 1),
    ("wrong-sentinel", "evidence-tamper", "mysql57", "E_CELL_EVIDENCE", 1),
    ("sqlite-artifact", "evidence-tamper", "sqlite", "E_CELL_EVIDENCE", 1),
    ("sqlite-coordinate", "evidence-tamper", "sqlite", "E_CELL_EVIDENCE", 1),
    ("port-owned", "runtime-lightweight", "mysql57", "E_PORT_OWNED", 1),
    ("stale-run-resource", "runtime-lightweight", "mysql57", "E_RESOURCE_STALE", 1),
    ("unavailable", "runtime-dynamic", "mysql57", "E_CONTAINER_HEALTH", 1),
    ("fixture-mutation", "runtime-dynamic", "mysql57", "E_FIXTURE_MUTATION", 1),
    ("forced-cleanup-failure", "runtime-dynamic", "mysql57", "E_CLEANUP_FORCED", 1),
    ("signal-int", "runtime-dynamic", "mysql57", "E_SIGNAL_INT", 130),
    ("signal-term", "runtime-dynamic", "mysql57", "E_SIGNAL_TERM", 143),
    ("signal-hup", "runtime-dynamic", "mysql57", "E_SIGNAL_HUP", 129),
)

MODE_LAYERS = {
    "static": ("evidence-tamper", "runtime-lightweight"),
    "dynamic": ("runtime-dynamic",),
    "all": ("evidence-tamper", "runtime-lightweight", "runtime-dynamic"),
}

EXACT_CLEANUP_STATUS = {
    "evidence-tamper": "not-applicable",
    "port-owned": "passed",
    "stale-run-resource": "failed",
    "unavailable": "passed",
    "fixture-mutation": "passed",
    "forced-cleanup-failure": "failed",
    "signal-int": "passed",
    "signal-term": "passed",
    "signal-hup": "passed",
}

RUNTIME_LAST_PHASE = {
    "port-owned": "preflight",
    "stale-run-resource": "cleanup-failed",
    "unavailable": "health",
    "fixture-mutation": "fixture-after",
    "forced-cleanup-failure": "cleanup-failed",
    "signal-int": "callback",
    "signal-term": "callback",
    "signal-hup": "callback",
}

RUNTIME_RESOURCE_PROBES = (
    "unavailable",
    "fixture-mutation",
    "forced-cleanup-failure",
    "signal-int",
    "signal-term",
    "signal-hup",
)

MYSQL57_IMAGE_REF = (
    "mysql@sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
)
MYSQL57_IMAGE_ID = (
    "sha256:4bc6bc963e6d8443453676cae56536f4b8156d78bae03c0145cbe47c2aad73bb"
)

HANDLED_EXTERNAL_SIGNALS = (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)


class StateProbeError(RuntimeError):
    pass


class ExternalSignal(StateProbeError):
    def __init__(self, signum: int):
        self.signum = signum
        self.exit_code = 128 + signum
        super().__init__(f"E_EXTERNAL_SIGNAL: received {signal.Signals(signum).name}")


def fail(message: str) -> None:
    raise StateProbeError(message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def atomic_write(path: Path, payload: str) -> None:
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    temporary.write_text(payload, encoding="utf-8", newline="\n")
    os.replace(temporary, path)


def atomic_json(path: Path, value: dict[str, Any]) -> None:
    atomic_write(path, json.dumps(value, indent=2, sort_keys=True) + "\n")


def safe_token(value: str, label: str) -> str:
    if (
        not isinstance(value, str)
        or not re.fullmatch(r"[A-Za-z0-9._-]+", value)
        or value in {".", ".."}
    ):
        fail(f"E_INPUT: unsafe {label}: {value!r}")
    return value


def canonical_repo_path(path: Path, label: str) -> Path:
    lexical = path.absolute()
    repo = REPO_ROOT.resolve()
    if not lexical.is_relative_to(repo):
        fail(f"E_PATH: {label} is outside the repository: {lexical}")
    current = repo
    for part in lexical.relative_to(repo).parts:
        current /= part
        if current.is_symlink():
            fail(f"E_PATH: {label} contains a symlink component: {current}")
    return lexical


def current_git_head() -> str:
    result = subprocess.run(
        ["git", "-C", str(REPO_ROOT), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    )
    head = result.stdout.strip()
    if not re.fullmatch(r"[0-9a-f]{40}", head):
        fail(f"E_GIT: invalid Git HEAD: {head!r}")
    return head


def read_json(path: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        fail(f"E_CONTRACT: not a regular file: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"E_CONTRACT: cannot read {path}: {exc}")
    if not isinstance(value, dict):
        fail(f"E_CONTRACT: JSON root is not an object: {path}")
    return value


def load_state_contract() -> tuple[dict[str, Any], str]:
    raw = read_json(STATE_CONTRACT_PATH)
    if set(raw) != {
        "kind",
        "schema_version",
        "version",
        "lane",
        "database",
        "artifact_contract",
        "cleanup_contract",
        "residue_contract",
        "scopes",
        "runtime_contract",
        "modes",
        "probes",
        "totals",
    }:
        fail("E_CONTRACT: database-state contract field set differs")
    if (
        raw["kind"] != "v934-step3-database-state-negative-contract"
        or raw["schema_version"] != 1
        or raw["version"] != "9.3.4"
        or raw["lane"] != "database-state-negative"
        or raw["database"] != "mysql57"
    ):
        fail("E_CONTRACT: database-state contract identity differs")
    expected_artifacts = {
        "manifest": "manifest.json",
        "probe_table": "probes.tsv",
        "run_context": "run-context.json",
        "run_status": "run-status.env",
        "summary": "summary.env",
    }
    if raw["artifact_contract"] != expected_artifacts:
        fail("E_CONTRACT: database-state artifact contract differs")
    if raw["cleanup_contract"] != EXACT_CLEANUP_STATUS:
        fail("E_CONTRACT: database-state cleanup contract differs")
    if raw["residue_contract"] != {
        "observation_order": "child-before-safety-cleanup-then-final",
        "default_child_residue": "0/0/0",
        "stale-run-resource_child_residue": "0/1/0",
        "final_residue": "0/0/0",
    }:
        fail("E_CONTRACT: database-state residue contract differs")
    if raw["scopes"] != {
        "standalone": "target/v934-step3-database-state/runs/<run-id>",
        "database-companion": (
            "target/v934-step3-database-matrix/runs/<run-id>/state-negative"
        ),
    }:
        fail("E_CONTRACT: database-state evidence scopes differ")
    if raw["runtime_contract"] != {
        "dynamic_database": "mysql57",
        "frozen_host_port": MYSQL57_PORT,
        "dynamic_precondition": "port-free-before-output",
        "existing_listener_policy": "observe-only-never-stop-or-restart",
        "resource_policy": "exact-child-run-identity-and-zero-residue",
        "companion_parent_binding": "database-run-context-sha256",
        "child_identity_policy": "exact-status-cleanup-resource-env",
        "external_signal_cleanup_policy": "claimed-child-retry-with-signals-ignored",
        "child_status_last_phase": RUNTIME_LAST_PHASE,
        "resource_evidence_probes": list(RUNTIME_RESOURCE_PROBES),
    }:
        fail("E_CONTRACT: database-state runtime contract differs")

    observed: list[tuple[str, str, str, str, int]] = []
    probes = raw["probes"]
    if not isinstance(probes, list):
        fail("E_CONTRACT: probes must be a list")
    for probe in probes:
        if not isinstance(probe, dict) or set(probe) != {
            "probe",
            "layer",
            "database",
            "expected_error",
            "expected_exit",
        }:
            fail("E_CONTRACT: malformed database-state probe")
        observed.append(
            (
                probe["probe"],
                probe["layer"],
                probe["database"],
                probe["expected_error"],
                probe["expected_exit"],
            )
        )
    if tuple(observed) != EXACT_PROBES:
        fail("E_CONTRACT: database-state probe set or order differs")

    expected_modes = {
        "static": {
            "layers": ["evidence-tamper", "runtime-lightweight"],
            "expected_probes": 12,
        },
        "dynamic": {"layers": ["runtime-dynamic"], "expected_probes": 6},
        "all": {
            "layers": ["evidence-tamper", "runtime-lightweight", "runtime-dynamic"],
            "expected_probes": 18,
        },
    }
    if raw["modes"] != expected_modes:
        fail("E_CONTRACT: database-state modes differ")
    if raw["totals"] != {
        "probes": 18,
        "evidence_tamper": 10,
        "runtime_lightweight": 2,
        "runtime_dynamic": 6,
        "signals": 3,
    }:
        fail("E_CONTRACT: database-state totals differ")
    return raw, sha256_file(STATE_CONTRACT_PATH)


def read_env(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        fail(f"E_EVIDENCE: env is not a regular file: {path}")
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or "=" not in line:
            fail(f"E_EVIDENCE: malformed env line in {path}: {line!r}")
        key, value = line.split("=", 1)
        if not key or key in result:
            fail(f"E_EVIDENCE: duplicate/blank env key in {path}: {key!r}")
        result[key] = value
    return result


def rewrite_env(path: Path, updates: dict[str, str]) -> None:
    values = read_env(path)
    if not set(updates).issubset(values):
        fail(f"E_PROBE: unknown env field for {path}: {sorted(set(updates) - set(values))}")
    values.update(updates)
    original_order = [line.split("=", 1)[0] for line in path.read_text(encoding="utf-8").splitlines()]
    atomic_write(path, "".join(f"{key}={values[key]}\n" for key in original_order))


def probe_record(
    contract_row: tuple[str, str, str, str, int],
    actual_error: str,
    actual_exit: int,
    *,
    cleanup_status: str,
    child_residue: tuple[int, int, int] = (0, 0, 0),
    final_residue: tuple[int, int, int] = (0, 0, 0),
) -> dict[str, str]:
    probe, layer, database, expected_error, expected_exit = contract_row
    expected_cleanup = EXACT_CLEANUP_STATUS.get(probe, EXACT_CLEANUP_STATUS.get(layer))
    expected_child_residue = (0, 1, 0) if probe == "stale-run-resource" else (0, 0, 0)
    status = "passed" if (
        actual_error == expected_error
        and actual_exit == expected_exit
        and cleanup_status == expected_cleanup
        and child_residue == expected_child_residue
        and final_residue == (0, 0, 0)
    ) else "failed"
    return {
        "probe": probe,
        "layer": layer,
        "database": database,
        "expected_error": expected_error,
        "actual_error": actual_error,
        "expected_exit": str(expected_exit),
        "actual_exit": str(actual_exit),
        "cleanup_status": cleanup_status,
        "child_container_residue": str(child_residue[0]),
        "child_volume_residue": str(child_residue[1]),
        "child_network_residue": str(child_residue[2]),
        "final_container_residue": str(final_residue[0]),
        "final_volume_residue": str(final_residue[1]),
        "final_network_residue": str(final_residue[2]),
        "status": status,
    }


def mutate_evidence(probe: str, cells_root: Path) -> None:
    mysql57 = cells_root / "mysql57"
    mysql8 = cells_root / "mysql8"
    postgres = cells_root / "postgres15"
    sqlite = cells_root / "sqlite"
    identity_updates = {
        "wrong-version": (mysql57, "foggy_test|5.7.43-log"),
        "wrong-product": (mysql57, "foggy_test|PostgreSQL-15"),
        "wrong-major": (mysql8, "foggy_test|5.7.44-log"),
        "wrong-catalog": (postgres, "wrong_catalog|public|15.8 (Debian)"),
        "wrong-schema": (postgres, "foggy_test|wrong_schema|15.8 (Debian)"),
    }
    if probe == "wrong-image-id":
        rewrite_env(mysql57 / "runtime.env", {"actual_image_id": "sha256:" + "0" * 64})
    elif probe == "wrong-image-ref":
        rewrite_env(
            mysql57 / "runtime.env",
            {"actual_image_ref": "mysql@sha256:" + "1" * 64},
        )
    elif probe in identity_updates:
        cell, identity = identity_updates[probe]
        rewrite_env(cell / "runtime.env", {"database_identity": identity})
        atomic_write(cell / "database-identity.txt", identity + "\n")
    elif probe == "wrong-sentinel":
        atomic_write(
            mysql57 / "fixture-after.txt",
            matrix.CANONICAL_FIXTURE.replace("9.3.4", "9.3.4-mutated", 1),
        )
    elif probe == "sqlite-artifact":
        rewrite_env(
            sqlite / "resource.env",
            {"sqlite_jdbc_jar_before_sha256": "2" * 64},
        )
    elif probe == "sqlite-coordinate":
        rewrite_env(
            sqlite / "resource.env",
            {"jdbc_url": "jdbc:sqlite:file::memory:?cache=private"},
        )
    else:
        fail(f"E_PROBE: unsupported evidence mutation: {probe}")


def verify_evidence_mutation(
    probe: str,
    probe_root: Path,
    matrix_contract: matrix.MatrixContract,
) -> None:
    synthetic = probe_root / "synthetic"
    outer_path = synthetic / "outer-run-marker.json"
    outer = matrix.validate_outer_marker(outer_path, matrix_contract)
    if outer.context["run_id"] != f"state-evidence-{probe}":
        fail(f"E_MANIFEST: evidence outer run id differs: {probe}")
    cells = synthetic / "cells"
    try:
        matrix.validate_cells_root(cells, outer)
    except matrix.MatrixError as exc:
        if exc.code != "E_CELL_EVIDENCE":
            fail(f"E_MANIFEST: evidence probe returned {exc.code}: {probe}")
    else:
        fail(f"E_MANIFEST: evidence probe unexpectedly validates: {probe}")

    mysql57 = cells / "mysql57"
    mysql8 = cells / "mysql8"
    postgres = cells / "postgres15"
    sqlite = cells / "sqlite"
    expected_identities = {
        "wrong-version": (mysql57, "foggy_test|5.7.43-log"),
        "wrong-product": (mysql57, "foggy_test|PostgreSQL-15"),
        "wrong-major": (mysql8, "foggy_test|5.7.44-log"),
        "wrong-catalog": (postgres, "wrong_catalog|public|15.8 (Debian)"),
        "wrong-schema": (postgres, "foggy_test|wrong_schema|15.8 (Debian)"),
    }
    if probe == "wrong-image-id":
        observed = read_env(mysql57 / "runtime.env").get("actual_image_id")
        expected = "sha256:" + "0" * 64
    elif probe == "wrong-image-ref":
        observed = read_env(mysql57 / "runtime.env").get("actual_image_ref")
        expected = "mysql@sha256:" + "1" * 64
    elif probe in expected_identities:
        cell, expected = expected_identities[probe]
        observed = read_env(cell / "runtime.env").get("database_identity")
        if (cell / "database-identity.txt").read_text(encoding="utf-8") != expected + "\n":
            fail(f"E_MANIFEST: evidence identity file mutation differs: {probe}")
    elif probe == "wrong-sentinel":
        observed = (mysql57 / "fixture-after.txt").read_text(encoding="utf-8")
        expected = matrix.CANONICAL_FIXTURE.replace("9.3.4", "9.3.4-mutated", 1)
    elif probe == "sqlite-artifact":
        observed = read_env(sqlite / "resource.env").get("sqlite_jdbc_jar_before_sha256")
        expected = "2" * 64
    elif probe == "sqlite-coordinate":
        observed = read_env(sqlite / "resource.env").get("jdbc_url")
        expected = "jdbc:sqlite:file::memory:?cache=private"
    else:
        fail(f"E_MANIFEST: unsupported evidence probe: {probe}")
    if observed != expected:
        fail(f"E_MANIFEST: exact evidence mutation differs: {probe}")

    sqlite_resource = read_env(sqlite / "resource.env")
    expected_database_file = str(sqlite / "database.sqlite")
    if sqlite_resource.get("database_file") != expected_database_file:
        fail(f"E_MANIFEST: SQLite database coordinate differs: {probe}")
    if probe != "sqlite-coordinate" and sqlite_resource.get("jdbc_url") != (
        f"jdbc:sqlite:{expected_database_file}"
    ):
        fail(f"E_MANIFEST: unexpected SQLite JDBC mutation: {probe}")

    with tempfile.TemporaryDirectory(prefix=f"v934-state-verify-{probe}-") as temporary:
        repaired_root = Path(temporary) / "synthetic"
        shutil.copytree(synthetic, repaired_root, copy_function=shutil.copy2)
        repaired_outer_path = repaired_root / "outer-run-marker.json"
        repaired_cells = repaired_root / "cells"
        repaired_sqlite = repaired_cells / "sqlite"
        repaired_database_file = str(repaired_sqlite / "database.sqlite")
        rewrite_env(
            repaired_sqlite / "resource.env",
            {
                "database_file": repaired_database_file,
                "jdbc_url": f"jdbc:sqlite:{repaired_database_file}",
            },
        )
        if probe == "wrong-image-id":
            resource = read_env(repaired_cells / "mysql57" / "resource.env")
            rewrite_env(
                repaired_cells / "mysql57" / "runtime.env",
                {"actual_image_id": resource["expected_image_id"]},
            )
        elif probe == "wrong-image-ref":
            resource = read_env(repaired_cells / "mysql57" / "resource.env")
            rewrite_env(
                repaired_cells / "mysql57" / "runtime.env",
                {"actual_image_ref": resource["expected_image_ref"]},
            )
        elif probe in expected_identities:
            repaired_cell = repaired_cells / expected_identities[probe][0].name
            baseline_identities = {
                "wrong-version": "foggy_test|5.7.44-log",
                "wrong-product": "foggy_test|5.7.44-log",
                "wrong-major": "foggy_test|8.0.39",
                "wrong-catalog": "foggy_test|public|15.8 (Debian)",
                "wrong-schema": "foggy_test|public|15.8 (Debian)",
            }
            baseline = baseline_identities[probe]
            rewrite_env(repaired_cell / "runtime.env", {"database_identity": baseline})
            atomic_write(repaired_cell / "database-identity.txt", baseline + "\n")
        elif probe == "wrong-sentinel":
            atomic_write(
                repaired_cells / "mysql57" / "fixture-after.txt",
                matrix.CANONICAL_FIXTURE,
            )
        elif probe == "sqlite-artifact":
            rewrite_env(
                repaired_sqlite / "resource.env",
                {"sqlite_jdbc_jar_before_sha256": matrix.SQLITE_JAR_SHA256},
            )
        repaired_outer = matrix.validate_outer_marker(
            repaired_outer_path, matrix_contract
        )
        try:
            matrix.validate_cells_root(repaired_cells, repaired_outer)
        except matrix.MatrixError as exc:
            fail(f"E_MANIFEST: repaired evidence does not validate: {probe}/{exc}")


def run_evidence_probe(
    row: tuple[str, str, str, str, int],
    probe_root: Path,
    matrix_contract: matrix.MatrixContract,
) -> dict[str, str]:
    probe = row[0]
    synthetic = probe_root / "synthetic"
    base_ns = time.time_ns() - 10_000_000_000
    outer_path = matrix._fixture_outer_marker(
        synthetic,
        matrix_contract,
        f"state-evidence-{probe}",
        base_ns,
    )
    outer = matrix.validate_outer_marker(outer_path, matrix_contract)
    cells = matrix._fixture_cells(synthetic, outer_path, base_ns + 1_000_000_000)
    matrix.validate_cells_root(cells, outer)
    mutate_evidence(probe, cells)
    try:
        matrix.validate_cells_root(cells, outer)
    except matrix.MatrixError as exc:
        actual_error = exc.code
        atomic_write(probe_root / "observed-error.txt", str(exc) + "\n")
    else:
        actual_error = "E_UNEXPECTED_PASS"
        atomic_write(probe_root / "observed-error.txt", "probe unexpectedly passed\n")
    result = probe_record(
        row,
        actual_error,
        1 if actual_error != "E_UNEXPECTED_PASS" else 0,
        cleanup_status="not-applicable",
    )
    write_result_env(probe_root / "result.env", result)
    return result


def child_run_id(parent_run_id: str, probe: str) -> str:
    digest = hashlib.sha256(f"{parent_run_id}|{probe}\n".encode("utf-8")).hexdigest()[:12]
    return f"state-{probe}-{digest}"


def project_identity(run_id: str) -> tuple[str, str, str, str]:
    scope = hashlib.sha256(f"{run_id}|mysql57\n".encode("utf-8")).hexdigest()[:12]
    project = f"v934db-mysql57-{scope}"
    return (
        project,
        f"{project}-mysql57",
        f"{project}-mysql57-data",
        f"{project}-network",
    )


def docker_lines(*args: str) -> list[str]:
    result = subprocess.run(
        ["docker", *args], check=True, capture_output=True, text=True
    )
    return [line for line in result.stdout.splitlines() if line]


def residue(run_id: str) -> tuple[int, int, int]:
    project, container, volume, network = project_identity(run_id)
    containers = set(docker_lines("ps", "-aq", "--filter", f"label=com.docker.compose.project={project}"))
    if docker_lines("ps", "-aq", "--filter", f"name=^/{container}$"):
        containers.add(container)
    volumes = set(docker_lines("volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={project}"))
    if volume in set(docker_lines("volume", "ls", "-q")):
        volumes.add(volume)
    networks = set(docker_lines("network", "ls", "-q", "--filter", f"label=com.docker.compose.project={project}"))
    if network in set(docker_lines("network", "ls", "--format", "{{.Name}}")):
        networks.add(network)
    return len(containers), len(volumes), len(networks)


def cleanup_owned_resources(run_id: str) -> None:
    project, container, volume, network = project_identity(run_id)
    container_ids = docker_lines("ps", "-aq", "--filter", f"label=com.docker.compose.project={project}")
    exact_ids = docker_lines("ps", "-aq", "--filter", f"name=^/{container}$")
    for identifier in sorted(set(container_ids + exact_ids)):
        subprocess.run(["docker", "rm", "-fv", identifier], check=False, capture_output=True)
    volume_names = docker_lines("volume", "ls", "-q", "--filter", f"label=com.docker.compose.project={project}")
    if volume in set(docker_lines("volume", "ls", "-q")):
        volume_names.append(volume)
    for name in sorted(set(volume_names)):
        subprocess.run(["docker", "volume", "rm", name], check=False, capture_output=True)
    network_names = docker_lines("network", "ls", "-q", "--filter", f"label=com.docker.compose.project={project}")
    if network in set(docker_lines("network", "ls", "--format", "{{.Name}}")):
        network_names.append(network)
    for name in sorted(set(network_names)):
        subprocess.run(["docker", "network", "rm", name], check=False, capture_output=True)


def safety_cleanup_owned_children(child_ids: set[str]) -> list[str]:
    errors: list[str] = []
    for child_id in sorted(child_ids):
        try:
            cleanup_owned_resources(child_id)
        except (OSError, subprocess.SubprocessError) as exc:
            errors.append(f"{child_id}: cleanup command failed: {exc}")
        try:
            shutil.rmtree(MATRIX_RUNS_ROOT / child_id, ignore_errors=False)
        except FileNotFoundError:
            pass
        except OSError as exc:
            errors.append(f"{child_id}: child root cleanup failed: {exc}")
        try:
            observed = residue(child_id)
        except (OSError, subprocess.SubprocessError) as exc:
            errors.append(f"{child_id}: residue check failed: {exc}")
        else:
            if observed != (0, 0, 0):
                errors.append(f"{child_id}: residue remains: {observed}")
    return errors


def claim_child_identity(
    parent_run_id: str,
    probe: str,
    owned_child_ids: set[str],
) -> tuple[str, Path]:
    child_id = child_run_id(parent_run_id, probe)
    child_root = MATRIX_RUNS_ROOT / child_id
    if child_root.exists() or child_root.is_symlink():
        fail(f"E_OUTPUT_EXISTS: child run root exists: {child_root}")
    if residue(child_id) != (0, 0, 0):
        fail(f"E_RESOURCE_COLLISION: child resources already exist: {child_id}")

    previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, HANDLED_EXTERNAL_SIGNALS)
    created = False
    try:
        child_root.mkdir(parents=True, exist_ok=False)
        created = True
        if residue(child_id) != (0, 0, 0):
            child_root.rmdir()
            created = False
            fail(f"E_RESOURCE_COLLISION: child resources raced the claim: {child_id}")
        owned_child_ids.add(child_id)
    except BaseException:
        if created and child_id not in owned_child_ids:
            try:
                child_root.rmdir()
            except FileNotFoundError:
                pass
            except OSError as exc:
                raise StateProbeError(
                    f"E_CHILD_CLAIM: cannot remove unclaimed empty child root: {child_root}: {exc}"
                ) from exc
        raise
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
    return child_id, child_root


def consume_claimed_child_root(parent_run_id: str, probe: str) -> tuple[str, Path]:
    child_id = child_run_id(parent_run_id, probe)
    child_root = MATRIX_RUNS_ROOT / child_id
    if child_root.is_symlink() or not child_root.is_dir() or any(child_root.iterdir()):
        fail(f"E_CHILD_CLAIM: child root is not the empty preclaimed directory: {child_root}")
    return child_id, child_root


def require_dynamic_port_free() -> None:
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        listener.bind(("127.0.0.1", MYSQL57_PORT))
    except OSError as exc:
        if exc.errno == errno.EADDRINUSE:
            fail(
                "E_DYNAMIC_PRECONDITION: frozen mysql57 port 13306 is occupied; "
                "the dynamic state probes did not start or change the listener"
            )
        raise
    finally:
        listener.close()


def provision_command(
    action: str,
    child_id: str,
    child_root: Path,
    callback_mode: str | None = None,
) -> tuple[list[str], Path]:
    parent = "preflight" if action == "check" else "cells"
    cell_root = child_root / parent / "mysql57"
    cell_root.parent.mkdir(parents=True, exist_ok=True)
    command = [str(PROVISIONER), action, "mysql57", child_id, str(cell_root)]
    if action == "run":
        if callback_mode is None:
            fail("E_PROBE: run action requires a callback mode")
        command.extend(["--", str(CALLBACK), callback_mode])
    return command, cell_root


def run_process(
    command: list[str],
    log_path: Path,
    env: dict[str, str],
) -> int:
    with log_path.open("wb") as log:
        process = subprocess.run(
            command,
            cwd=REPO_ROOT,
            env=env,
            stdout=log,
            stderr=subprocess.STDOUT,
            check=False,
        )
    return process.returncode


def observed_error(log_path: Path) -> str:
    if log_path.is_symlink() or not log_path.is_file():
        fail(f"E_EVIDENCE: rejection log is not a regular file: {log_path}")
    text = log_path.read_text(encoding="utf-8", errors="replace")
    matches = re.findall(r"\bERROR (E_[A-Z0-9_]+):", text)
    return matches[-1] if matches else "E_MISSING_ERROR_CODE"


def capture_child(child_root: Path, probe_root: Path) -> None:
    evidence = probe_root / "runtime-evidence"
    if evidence.exists():
        fail(f"E_OUTPUT_EXISTS: {evidence}")
    shutil.copytree(child_root, evidence, copy_function=shutil.copy2)


def child_cleanup_status(child_root: Path) -> str:
    candidates = list(child_root.glob("preflight/mysql57/status.env")) + list(
        child_root.glob("cells/mysql57/status.env")
    )
    if len(candidates) != 1:
        return "missing"
    return read_env(candidates[0]).get("cleanup_status", "missing")


def run_port_owned_probe(
    row: tuple[str, str, str, str, int],
    parent_run_id: str,
    probe_root: Path,
) -> dict[str, str]:
    probe = row[0]
    child_id, child_root = consume_claimed_child_root(parent_run_id, probe)
    command, _ = provision_command("check", child_id, child_root)
    listener: socket.socket | None = None
    listener_owner = "existing"
    try:
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            listener.bind(("127.0.0.1", MYSQL57_PORT))
            listener.listen(1)
            listener_owner = "harness"
        except OSError as exc:
            listener.close()
            listener = None
            if exc.errno != errno.EADDRINUSE:
                raise
        atomic_write(probe_root / "listener.env", f"owner={listener_owner}\nport={MYSQL57_PORT}\n")
        env = os.environ.copy()
        env["V934_DB_STATE_AUTH"] = AUTH_VALUE
        log_path = probe_root / "process.log"
        actual_exit = run_process(command, log_path, env)
        actual_error = observed_error(log_path)
        cleanup_status = child_cleanup_status(child_root)
        capture_child(child_root, probe_root)
        child_residue = residue(child_id)
    finally:
        if listener is not None:
            listener.close()
        shutil.rmtree(child_root, ignore_errors=True)
    final_residue = residue(child_id)
    result = probe_record(
        row,
        actual_error,
        actual_exit,
        cleanup_status=cleanup_status,
        child_residue=child_residue,
        final_residue=final_residue,
    )
    write_result_env(probe_root / "result.env", result)
    return result


def run_stale_resource_probe(
    row: tuple[str, str, str, str, int],
    parent_run_id: str,
    probe_root: Path,
) -> dict[str, str]:
    probe = row[0]
    child_id, child_root = consume_claimed_child_root(parent_run_id, probe)
    command, _ = provision_command("check", child_id, child_root)
    _, _, volume, _ = project_identity(child_id)
    if volume in set(docker_lines("volume", "ls", "-q")):
        fail(f"E_RESOURCE_COLLISION: stale probe volume already exists: {volume}")
    try:
        subprocess.run(
            ["docker", "volume", "create", volume], check=True, capture_output=True
        )
        env = os.environ.copy()
        env["V934_DB_STATE_AUTH"] = AUTH_VALUE
        log_path = probe_root / "process.log"
        actual_exit = run_process(command, log_path, env)
        actual_error = observed_error(log_path)
        cleanup_status = child_cleanup_status(child_root)
        capture_child(child_root, probe_root)
        child_residue = residue(child_id)
    finally:
        try:
            cleanup_owned_resources(child_id)
        finally:
            shutil.rmtree(child_root, ignore_errors=True)
    final_residue = residue(child_id)
    result = probe_record(
        row,
        actual_error,
        actual_exit,
        cleanup_status=cleanup_status,
        child_residue=child_residue,
        final_residue=final_residue,
    )
    write_result_env(probe_root / "result.env", result)
    return result


SIGNALS = {
    "signal-int": (signal.SIGINT, "E_SIGNAL_INT"),
    "signal-term": (signal.SIGTERM, "E_SIGNAL_TERM"),
    "signal-hup": (signal.SIGHUP, "E_SIGNAL_HUP"),
}


def run_signal_process(
    command: list[str],
    log_path: Path,
    env: dict[str, str],
    ready_file: Path,
    sig: signal.Signals,
) -> int:
    with log_path.open("wb") as log:
        process = subprocess.Popen(
            command,
            cwd=REPO_ROOT,
            env=env,
            stdout=log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        try:
            deadline = time.monotonic() + 360
            while time.monotonic() < deadline:
                if ready_file.is_file():
                    break
                return_code = process.poll()
                if return_code is not None:
                    return return_code
                time.sleep(0.25)
            else:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=30)
                fail(f"E_PROBE_TIMEOUT: signal readiness was not published: {ready_file}")
            os.killpg(process.pid, sig)
            try:
                actual_exit = process.wait(timeout=120)
                atomic_write(
                    ready_file.parent / "signal-observed.env",
                    f"signal={sig.name}\nexit_code={actual_exit}\nstatus=observed\n",
                )
                return actual_exit
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=30)
                fail(f"E_PROBE_TIMEOUT: signal probe did not terminate: {sig.name}")
        finally:
            if process.poll() is None:
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
                try:
                    process.wait(timeout=120)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL)
                    process.wait(timeout=30)


def run_dynamic_probe(
    row: tuple[str, str, str, str, int],
    parent_run_id: str,
    probe_root: Path,
) -> dict[str, str]:
    probe = row[0]
    child_id, child_root = consume_claimed_child_root(parent_run_id, probe)
    callback_mode = {
        "unavailable": "noop",
        "fixture-mutation": "mutate-fixture",
        "forced-cleanup-failure": "noop",
        "signal-int": "wait-signal",
        "signal-term": "wait-signal",
        "signal-hup": "wait-signal",
    }[probe]
    command, cell_root = provision_command("run", child_id, child_root, callback_mode)
    env = os.environ.copy()
    env["V934_DB_STATE_AUTH"] = AUTH_VALUE
    env["V934_DB_STATE_PROBE"] = probe
    log_path = probe_root / "process.log"
    try:
        if probe in SIGNALS:
            sig, actual_error = SIGNALS[probe]
            actual_exit = run_signal_process(
                command,
                log_path,
                env,
                cell_root / "probe-ready.env",
                sig,
            )
        else:
            actual_exit = run_process(command, log_path, env)
            actual_error = observed_error(log_path)
        cleanup_status = child_cleanup_status(child_root)
        capture_child(child_root, probe_root)
        child_residue = residue(child_id)
    finally:
        try:
            cleanup_owned_resources(child_id)
        finally:
            shutil.rmtree(child_root, ignore_errors=True)
    final_residue = residue(child_id)
    result = probe_record(
        row,
        actual_error,
        actual_exit,
        cleanup_status=cleanup_status,
        child_residue=child_residue,
        final_residue=final_residue,
    )
    write_result_env(probe_root / "result.env", result)
    return result


def write_result_env(path: Path, result: dict[str, str]) -> None:
    atomic_write(path, "".join(f"{field}={result[field]}\n" for field in TSV_FIELDS))


def write_probe_table(path: Path, rows: list[dict[str, str]]) -> None:
    temporary = path.with_name(f".{path.name}.{os.getpid()}.tmp")
    with temporary.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=TSV_FIELDS, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    os.replace(temporary, path)


def read_probe_table(path: Path) -> list[dict[str, str]]:
    if path.is_symlink() or not path.is_file():
        fail(f"E_EVIDENCE: probe table is not a regular file: {path}")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if tuple(reader.fieldnames or ()) != TSV_FIELDS:
            fail(f"E_EVIDENCE: probe table header differs: {reader.fieldnames}")
        rows = list(reader)
    if any(set(row) != set(TSV_FIELDS) or None in row.values() for row in rows):
        fail("E_EVIDENCE: malformed probe table row")
    return rows


def artifact_records(run_root: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for path in sorted(
        run_root.rglob("*"), key=lambda candidate: candidate.relative_to(run_root).as_posix()
    ):
        if path == run_root / "manifest.json":
            continue
        if path.is_symlink() or (not path.is_file() and not path.is_dir()):
            fail(f"E_ARTIFACT: symlink/special artifact is forbidden: {path}")
        if path.is_file():
            records.append(
                {
                    "path": path.relative_to(run_root).as_posix(),
                    "sha256": sha256_file(path),
                    "size_bytes": path.stat().st_size,
                }
            )
    return records


def verify_rows(
    rows: list[dict[str, str]],
    expected_rows: list[tuple[str, str, str, str, int]],
) -> None:
    if any(
        not isinstance(row, dict)
        or set(row) != set(TSV_FIELDS)
        or any(not isinstance(value, str) for value in row.values())
        for row in rows
    ):
        fail("E_RESULT: malformed probe result row")
    if [row["probe"] for row in rows] != [row[0] for row in expected_rows]:
        fail("E_RESULT: probe result set or order differs")
    for result, expected in zip(rows, expected_rows, strict=True):
        probe, layer, database, expected_error, expected_exit = expected
        if (
            result["probe"] != probe
            or result["layer"] != layer
            or result["database"] != database
            or result["expected_error"] != expected_error
            or result["actual_error"] != expected_error
            or result["expected_exit"] != str(expected_exit)
            or result["actual_exit"] != str(expected_exit)
            or result["cleanup_status"]
            != EXACT_CLEANUP_STATUS.get(probe, EXACT_CLEANUP_STATUS.get(layer))
            or result["child_container_residue"] != "0"
            or result["child_volume_residue"]
            != ("1" if probe == "stale-run-resource" else "0")
            or result["child_network_residue"] != "0"
            or result["final_container_residue"] != "0"
            or result["final_volume_residue"] != "0"
            or result["final_network_residue"] != "0"
            or result["status"] != "passed"
        ):
            fail(f"E_RESULT: probe did not meet its exact contract: {probe}/{result}")


def run_harness(mode: str, run_id: str, *, companion: bool) -> Path:
    run_id = safe_token(run_id, "run id")
    state_contract, state_contract_sha = load_state_contract()
    matrix_contract = matrix.load_contract(REPO_ROOT, MATRIX_CONTRACT_PATH)
    selected_layers = MODE_LAYERS[mode]
    selected_rows = [row for row in EXACT_PROBES if row[1] in selected_layers]
    if len(selected_rows) != state_contract["modes"][mode]["expected_probes"]:
        fail("E_CONTRACT: selected probe count differs")
    if "runtime-dynamic" in selected_layers:
        require_dynamic_port_free()

    scope = "database-companion" if companion else "standalone"
    database_outer_marker_sha256 = ""
    if companion:
        matrix_run_root = canonical_repo_path(
            MATRIX_RUNS_ROOT / run_id, "database matrix run root"
        )
        if matrix_run_root.is_symlink() or not matrix_run_root.is_dir():
            fail(
                "E_COMPANION_ROOT: database companion requires its existing canonical "
                f"matrix run root: {matrix_run_root}"
            )
        database_outer = matrix.validate_outer_marker(
            matrix_run_root / "run-context.json", matrix_contract
        )
        if database_outer.context["run_id"] != run_id:
            fail("E_COMPANION_ROOT: database outer marker run id differs")
        database_outer_marker_sha256 = database_outer.sha256
        run_root = canonical_repo_path(
            matrix_run_root / "state-negative", "database companion root"
        )
    else:
        run_root = canonical_repo_path(STATE_RUNS_ROOT / run_id, "state run root")
    if run_root.exists() or run_root.is_symlink():
        fail(f"E_OUTPUT_EXISTS: state run root exists: {run_root}")
    run_root.mkdir(parents=True)
    probes_root = run_root / "probes"
    probes_root.mkdir()
    started_at = dt.datetime.now(dt.timezone.utc).isoformat()
    git_head = current_git_head()
    atomic_json(
        run_root / "run-context.json",
        {
            "schema_version": 1,
            "kind": "v934-step3-database-state-negative-run",
            "run_id": run_id,
            "mode": mode,
            "scope": scope,
            "lane": "database-state-negative",
            "git_head": git_head,
            "state_contract_sha256": state_contract_sha,
            "database_contract_sha256": matrix_contract.sha256,
            "database_outer_marker_sha256": database_outer_marker_sha256,
            "started_at": started_at,
            "status": "started",
        },
    )

    results: list[dict[str, str]] = []
    owned_child_ids: set[str] = set()
    try:
        for row in selected_rows:
            probe = row[0]
            probe_root = probes_root / probe
            probe_root.mkdir()
            if row[1] == "evidence-tamper":
                result = run_evidence_probe(row, probe_root, matrix_contract)
            else:
                # Ownership begins only after the child root is atomically
                # created and the resource namespace is checked a second
                # time with external signals blocked.  A failed claim must
                # never put another execution's identity in the cleanup set.
                claim_child_identity(run_id, probe, owned_child_ids)
                if probe == "port-owned":
                    result = run_port_owned_probe(row, run_id, probe_root)
                elif probe == "stale-run-resource":
                    result = run_stale_resource_probe(row, run_id, probe_root)
                else:
                    result = run_dynamic_probe(row, run_id, probe_root)
            results.append(result)
            if result["status"] != "passed":
                fail(f"E_PROBE_FAILED: {probe}/{result}")
        verify_rows(results, selected_rows)
        cleanup_errors = safety_cleanup_owned_children(owned_child_ids)
        if cleanup_errors:
            fail(f"E_SAFETY_CLEANUP: {'; '.join(cleanup_errors)}")
        write_probe_table(run_root / "probes.tsv", results)
        complete = mode == "all"
        finished_at = dt.datetime.now(dt.timezone.utc).isoformat()
        atomic_write(
            run_root / "summary.env",
            "".join(
                (
                    f"run_id={run_id}\n",
                    f"mode={mode}\n",
                    f"scope={scope}\n",
                    f"git_head={git_head}\n",
                    f"database_outer_marker_sha256={database_outer_marker_sha256}\n",
                    f"probes={len(results)}\n",
                    f"contract_probes={len(EXACT_PROBES)}\n",
                    f"evidence_tamper={sum(row['layer'] == 'evidence-tamper' for row in results)}\n",
                    f"runtime_lightweight={sum(row['layer'] == 'runtime-lightweight' for row in results)}\n",
                    f"runtime_dynamic={sum(row['layer'] == 'runtime-dynamic' for row in results)}\n",
                    f"signals={sum(row['probe'].startswith('signal-') for row in results)}\n",
                    f"complete={str(complete).lower()}\n",
                    "container_residue=0\n",
                    "volume_residue=0\n",
                    "network_residue=0\n",
                    "status=passed\n",
                )
            ),
        )
        atomic_write(
            run_root / "run-status.env",
            "".join(
                (
                    f"run_id={run_id}\n",
                    f"mode={mode}\n",
                    f"scope={scope}\n",
                    f"git_head={git_head}\n",
                    f"started_at={started_at}\n",
                    f"finished_at={finished_at}\n",
                    "exit_code=0\n",
                    "last_phase=completed\n",
                    "status=passed\n",
                )
            ),
        )
        manifest = {
            "schema_version": 1,
            "kind": "v934-step3-database-state-negative-manifest",
            "run_id": run_id,
            "mode": mode,
            "scope": scope,
            "lane": "database-state-negative",
            "git_head": git_head,
            "state_contract_sha256": state_contract_sha,
            "database_contract_sha256": matrix_contract.sha256,
            "database_outer_marker_sha256": database_outer_marker_sha256,
            "run_context_sha256": sha256_file(run_root / "run-context.json"),
            "run_status_sha256": sha256_file(run_root / "run-status.env"),
            "summary_sha256": sha256_file(run_root / "summary.env"),
            "probe_table_sha256": sha256_file(run_root / "probes.tsv"),
            "complete": complete,
            "totals": {
                "probes": len(results),
                "evidence_tamper": sum(row["layer"] == "evidence-tamper" for row in results),
                "runtime_lightweight": sum(row["layer"] == "runtime-lightweight" for row in results),
                "runtime_dynamic": sum(row["layer"] == "runtime-dynamic" for row in results),
                "signals": sum(row["probe"].startswith("signal-") for row in results),
                "failed": 0,
            },
            "probes": results,
            "artifacts": artifact_records(run_root),
        }
        atomic_json(run_root / "manifest.json", manifest)
        return run_root
    except BaseException as exc:
        # A first external signal raises into the active operation. Quiesce
        # subsequent signals, then retry cleanup for every child identity that
        # this invocation claimed before publishing failure state.
        for handled_signal in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
            signal.signal(handled_signal, signal.SIG_IGN)
        cleanup_errors = safety_cleanup_owned_children(owned_child_ids)
        finished_at = dt.datetime.now(dt.timezone.utc).isoformat()
        failure_exit = exc.exit_code if isinstance(exc, ExternalSignal) else 1
        failure_phase = (
            f"external-{signal.Signals(exc.signum).name.lower()}"
            if isinstance(exc, ExternalSignal)
            else "probe-failed"
        )
        if cleanup_errors:
            failure_phase += "-cleanup-failed"
            print(
                f"[v934-db-state] ERROR E_SAFETY_CLEANUP: {'; '.join(cleanup_errors)}",
                file=sys.stderr,
            )
        atomic_write(
            run_root / "run-status.env",
            "".join(
                (
                    f"run_id={run_id}\n",
                    f"mode={mode}\n",
                    f"scope={scope}\n",
                    f"git_head={git_head}\n",
                    f"started_at={started_at}\n",
                    f"finished_at={finished_at}\n",
                    f"exit_code={failure_exit}\n",
                    f"last_phase={failure_phase}\n",
                    "status=failed\n",
                )
            ),
        )
        raise


def verify_runtime_child_evidence(
    parent_run_id: str,
    result: dict[str, str],
    probe_root: Path,
) -> Path:
    probe = result["probe"]
    child_id = child_run_id(parent_run_id, probe)
    project, container, volume, network = project_identity(child_id)
    cell_parent = "preflight" if result["layer"] == "runtime-lightweight" else "cells"
    cell_root = probe_root / "runtime-evidence" / cell_parent / "mysql57"

    status = read_env(cell_root / "status.env")
    expected_status_fields = {
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
    }
    timestamp_pattern = r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z"
    if (
        set(status) != expected_status_fields
        or status["run_id"] != child_id
        or status["database"] != "mysql57"
        or status["project"] != project
        or not re.fullmatch(timestamp_pattern, status["started_at"])
        or not re.fullmatch(timestamp_pattern, status["finished_at"])
        or status["last_phase"] != RUNTIME_LAST_PHASE[probe]
        or status["exit_code"] != result["actual_exit"]
        or status["cleanup_status"] != result["cleanup_status"]
        or status["status"] != "failed"
    ):
        fail(f"E_MANIFEST: runtime child status identity differs: {probe}")

    before = status["fixture_before_sha256"]
    after = status["fixture_after_sha256"]
    reaches_fixture = probe in {
        "fixture-mutation",
        "forced-cleanup-failure",
        "signal-int",
        "signal-term",
        "signal-hup",
    }
    reaches_after = probe in {"fixture-mutation", "forced-cleanup-failure"}
    if (
        (reaches_fixture and not re.fullmatch(r"[0-9a-f]{64}", before))
        or (not reaches_fixture and before != "")
        or (reaches_after and not re.fullmatch(r"[0-9a-f]{64}", after))
        or (not reaches_after and after != "")
        or (probe == "fixture-mutation" and before == after)
        or (probe == "forced-cleanup-failure" and before != after)
    ):
        fail(f"E_MANIFEST: runtime child fixture status differs: {probe}")

    cleanup = read_env(cell_root / "cleanup.env")
    expected_cleanup = {
        "database": "mysql57",
        "project": project,
        "container": container,
        "volume": volume,
        "network": network,
        "status": result["cleanup_status"],
    }
    if cleanup != expected_cleanup:
        fail(f"E_MANIFEST: runtime child cleanup identity differs: {probe}")

    resource_path = cell_root / "resource.env"
    if probe in RUNTIME_RESOURCE_PROBES:
        resource = read_env(resource_path)
        expected_resource = {
            "run_id": child_id,
            "database": "mysql57",
            "service": "mysql",
            "project": project,
            "container": container,
            "volume": volume,
            "network": network,
            "host_port": str(MYSQL57_PORT),
            "container_port": "3306",
            "profile": "docker",
            "expected_image_ref": MYSQL57_IMAGE_REF,
            "expected_image_id": MYSQL57_IMAGE_ID,
        }
        if resource != expected_resource:
            fail(f"E_MANIFEST: runtime child resource identity differs: {probe}")
    elif resource_path.exists() or resource_path.is_symlink():
        fail(f"E_MANIFEST: unexpected runtime child resource evidence: {probe}")
    return cell_root


def verify_manifest(path: Path) -> dict[str, Any]:
    state_contract, state_contract_sha = load_state_contract()
    matrix_contract = matrix.load_contract(REPO_ROOT, MATRIX_CONTRACT_PATH)
    path = canonical_repo_path(path, "state manifest")
    manifest = read_json(path)
    run_root = path.parent
    expected_fields = {
        "schema_version",
        "kind",
        "run_id",
        "mode",
        "scope",
        "lane",
        "git_head",
        "state_contract_sha256",
        "database_contract_sha256",
        "database_outer_marker_sha256",
        "run_context_sha256",
        "run_status_sha256",
        "summary_sha256",
        "probe_table_sha256",
        "complete",
        "totals",
        "probes",
        "artifacts",
    }
    if set(manifest) != expected_fields:
        fail("E_MANIFEST: field set differs")
    mode = manifest["mode"]
    if not isinstance(mode, str) or mode not in MODE_LAYERS:
        fail("E_MANIFEST: invalid mode")
    selected_rows = [row for row in EXACT_PROBES if row[1] in MODE_LAYERS[mode]]
    probes = manifest["probes"]
    if not isinstance(probes, list):
        fail("E_MANIFEST: probes are not a list")
    verify_rows(probes, selected_rows)
    expected_totals = {
        "probes": len(selected_rows),
        "evidence_tamper": sum(row[1] == "evidence-tamper" for row in selected_rows),
        "runtime_lightweight": sum(row[1] == "runtime-lightweight" for row in selected_rows),
        "runtime_dynamic": sum(row[1] == "runtime-dynamic" for row in selected_rows),
        "signals": sum(row[0].startswith("signal-") for row in selected_rows),
        "failed": 0,
    }
    if (
        manifest["schema_version"] != 1
        or manifest["kind"] != "v934-step3-database-state-negative-manifest"
        or manifest["lane"] != "database-state-negative"
        or manifest["state_contract_sha256"] != state_contract_sha
        or manifest["database_contract_sha256"] != matrix_contract.sha256
        or manifest["git_head"] != current_git_head()
        or manifest["complete"] is not (mode == "all")
        or manifest["totals"] != expected_totals
    ):
        fail("E_MANIFEST: provenance or totals differ")
    safe_token(manifest["run_id"], "manifest run id")
    scope = manifest["scope"]
    if scope == "standalone":
        expected_root = STATE_RUNS_ROOT / manifest["run_id"]
    elif scope == "database-companion":
        expected_root = (
            MATRIX_RUNS_ROOT / manifest["run_id"] / "state-negative"
        )
    else:
        fail(f"E_MANIFEST: invalid evidence scope: {scope!r}")
    if run_root != expected_root:
        fail(f"E_MANIFEST: manifest is outside its canonical run root: {run_root}")
    if scope == "database-companion":
        database_outer = matrix.validate_outer_marker(
            run_root.parent / "run-context.json", matrix_contract
        )
        expected_database_outer_sha256 = database_outer.sha256
    else:
        expected_database_outer_sha256 = ""
    if manifest["database_outer_marker_sha256"] != expected_database_outer_sha256:
        fail("E_MANIFEST: database outer marker binding differs")
    bindings = {
        "run-context.json": manifest["run_context_sha256"],
        "run-status.env": manifest["run_status_sha256"],
        "summary.env": manifest["summary_sha256"],
        "probes.tsv": manifest["probe_table_sha256"],
    }
    for relative, digest in bindings.items():
        candidate = run_root / relative
        if sha256_file(candidate) != digest:
            fail(f"E_MANIFEST: bound artifact differs: {relative}")
    table_rows = read_probe_table(run_root / "probes.tsv")
    if table_rows != probes:
        fail("E_MANIFEST: probe table differs from manifest probes")
    signal_names = {
        "signal-int": "SIGINT",
        "signal-term": "SIGTERM",
        "signal-hup": "SIGHUP",
    }
    for result in probes:
        probe_root = run_root / "probes" / result["probe"]
        if read_env(probe_root / "result.env") != result:
            fail(f"E_MANIFEST: result env differs: {result['probe']}")
        if result["layer"] == "evidence-tamper":
            verify_evidence_mutation(result["probe"], probe_root, matrix_contract)
            observed_path = probe_root / "observed-error.txt"
            if observed_path.is_symlink() or not observed_path.is_file():
                fail(f"E_MANIFEST: evidence rejection is not regular: {result['probe']}")
            observed_text = observed_path.read_text(encoding="utf-8", errors="replace")
            observed_match = re.match(r"^(E_[A-Z0-9_]+):", observed_text)
            if not observed_match or observed_match.group(1) != result["actual_error"]:
                fail(f"E_MANIFEST: evidence-tamper rejection differs: {result['probe']}")
            continue
        cell_root = verify_runtime_child_evidence(
            manifest["run_id"], result, probe_root
        )
        if result["probe"] in signal_names:
            observed_signal = read_env(cell_root / "signal-observed.env")
            ready = read_env(cell_root / "probe-ready.env")
            if (
                observed_signal
                != {
                    "signal": signal_names[result["probe"]],
                    "exit_code": result["actual_exit"],
                    "status": "observed",
                }
                or ready.get("status") != "ready"
                or not re.fullmatch(r"[1-9][0-9]*", ready.get("pid", ""))
            ):
                fail(f"E_MANIFEST: durable signal evidence differs: {result['probe']}")
        elif observed_error(probe_root / "process.log") != result["actual_error"]:
            fail(f"E_MANIFEST: runtime rejection log differs: {result['probe']}")
    context = read_json(run_root / "run-context.json")
    expected_context_fields = {
        "schema_version",
        "kind",
        "run_id",
        "mode",
        "scope",
        "lane",
        "git_head",
        "state_contract_sha256",
        "database_contract_sha256",
        "database_outer_marker_sha256",
        "started_at",
        "status",
    }
    if (
        set(context) != expected_context_fields
        or context["schema_version"] != 1
        or context["kind"] != "v934-step3-database-state-negative-run"
        or context["run_id"] != manifest["run_id"]
        or context["mode"] != mode
        or context["scope"] != scope
        or context["lane"] != "database-state-negative"
        or context["git_head"] != manifest["git_head"]
        or context["state_contract_sha256"] != state_contract_sha
        or context["database_contract_sha256"] != matrix_contract.sha256
        or context["database_outer_marker_sha256"] != expected_database_outer_sha256
        or context["status"] != "started"
    ):
        fail("E_MANIFEST: run context differs")
    summary = read_env(run_root / "summary.env")
    expected_summary = {
        "run_id": manifest["run_id"],
        "mode": mode,
        "scope": scope,
        "git_head": manifest["git_head"],
        "database_outer_marker_sha256": expected_database_outer_sha256,
        "probes": str(expected_totals["probes"]),
        "contract_probes": str(len(EXACT_PROBES)),
        "evidence_tamper": str(expected_totals["evidence_tamper"]),
        "runtime_lightweight": str(expected_totals["runtime_lightweight"]),
        "runtime_dynamic": str(expected_totals["runtime_dynamic"]),
        "signals": str(expected_totals["signals"]),
        "complete": str(mode == "all").lower(),
        "container_residue": "0",
        "volume_residue": "0",
        "network_residue": "0",
        "status": "passed",
    }
    if summary != expected_summary:
        fail("E_MANIFEST: summary differs")
    run_status = read_env(run_root / "run-status.env")
    if (
        set(run_status)
        != {
            "run_id",
            "mode",
            "scope",
            "git_head",
            "started_at",
            "finished_at",
            "exit_code",
            "last_phase",
            "status",
        }
        or run_status["run_id"] != manifest["run_id"]
        or run_status["mode"] != mode
        or run_status["scope"] != scope
        or run_status["git_head"] != manifest["git_head"]
        or run_status["exit_code"] != "0"
        or run_status["last_phase"] != "completed"
        or run_status["status"] != "passed"
    ):
        fail("E_MANIFEST: run status differs")
    artifacts = manifest["artifacts"]
    if not isinstance(artifacts, list):
        fail("E_MANIFEST: artifacts are not a list")
    observed_paths: list[str] = []
    for record in artifacts:
        if (
            not isinstance(record, dict)
            or set(record) != {"path", "sha256", "size_bytes"}
            or not isinstance(record["path"], str)
            or not isinstance(record["sha256"], str)
            or not re.fullmatch(r"[0-9a-f]{64}", record["sha256"])
            or not isinstance(record["size_bytes"], int)
            or isinstance(record["size_bytes"], bool)
            or record["size_bytes"] < 0
        ):
            fail("E_MANIFEST: malformed artifact record")
        relative = Path(record["path"])
        if relative.is_absolute() or ".." in relative.parts or relative.as_posix() == "manifest.json":
            fail("E_MANIFEST: unsafe artifact path")
        candidate = run_root / relative
        if (
            candidate.is_symlink()
            or not candidate.is_file()
            or sha256_file(candidate) != record["sha256"]
            or candidate.stat().st_size != record["size_bytes"]
        ):
            fail(f"E_MANIFEST: artifact differs: {relative}")
        observed_paths.append(relative.as_posix())
    tree_paths = list(run_root.rglob("*"))
    if any(
        path.is_symlink() or (not path.is_file() and not path.is_dir())
        for path in tree_paths
    ):
        fail("E_MANIFEST: symlink/special artifact is forbidden")
    actual_paths = sorted(
        path.relative_to(run_root).as_posix()
        for path in tree_paths
        if path.is_file() and path != run_root / "manifest.json"
    )
    if observed_paths != sorted(observed_paths) or observed_paths != actual_paths:
        fail("E_MANIFEST: artifact set/order differs")
    expected_directories = sorted(
        {
            parent.as_posix()
            for relative in actual_paths
            for parent in Path(relative).parents
            if parent != Path(".")
        }
    )
    actual_directories = sorted(
        path.relative_to(run_root).as_posix()
        for path in tree_paths
        if path.is_dir()
    )
    if actual_directories != expected_directories:
        fail("E_MANIFEST: artifact directory set differs")
    if state_contract["modes"][mode]["expected_probes"] != len(probes):
        fail("E_MANIFEST: contract mode count differs")
    return manifest


def command_validate(_: argparse.Namespace) -> None:
    raw, digest = load_state_contract()
    matrix_contract = matrix.load_contract(REPO_ROOT, MATRIX_CONTRACT_PATH)
    print(
        json.dumps(
            {
                "status": "passed",
                "probes": raw["totals"]["probes"],
                "state_contract_sha256": digest,
                "database_contract_sha256": matrix_contract.sha256,
            },
            sort_keys=True,
        )
    )


def command_run(args: argparse.Namespace) -> None:
    root = run_harness(args.mode, args.run_id, companion=args.companion)
    manifest = verify_manifest(root / "manifest.json")
    print(
        "[v934-db-state] PASS "
        f"run={manifest['run_id']} mode={manifest['mode']} "
        f"probes={manifest['totals']['probes']} complete={str(manifest['complete']).lower()} "
        f"evidence={root}"
    )


def command_verify(args: argparse.Namespace) -> None:
    manifest = verify_manifest(args.manifest)
    print(
        "[v934-db-state] VERIFY PASS "
        f"run={manifest['run_id']} mode={manifest['mode']} probes={manifest['totals']['probes']}"
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    validate_parser = subparsers.add_parser("validate", help="validate frozen state and matrix contracts")
    validate_parser.set_defaults(func=command_validate)
    run_parser = subparsers.add_parser("run", help="run one immutable negative candidate")
    run_parser.add_argument("--mode", choices=tuple(MODE_LAYERS), required=True)
    run_parser.add_argument("--run-id", required=True)
    run_parser.add_argument(
        "--companion",
        action="store_true",
        help="publish under the existing canonical database-matrix run root",
    )
    run_parser.set_defaults(func=command_run)
    verify_parser = subparsers.add_parser("verify", help="verify one state-negative manifest")
    verify_parser.add_argument("--manifest", type=Path, required=True)
    verify_parser.set_defaults(func=command_verify)
    return parser


def main() -> int:
    args = build_parser().parse_args()

    def handle_external_signal(signum: int, _: Any) -> None:
        for handled in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
            signal.signal(handled, signal.SIG_IGN)
        raise ExternalSignal(signum)

    for handled_signal in (signal.SIGINT, signal.SIGTERM, signal.SIGHUP):
        signal.signal(handled_signal, handle_external_signal)
    try:
        args.func(args)
    except ExternalSignal as exc:
        print(f"[v934-db-state] ERROR: {exc}", file=sys.stderr)
        return exc.exit_code
    except (StateProbeError, matrix.MatrixError, OSError, subprocess.SubprocessError) as exc:
        print(f"[v934-db-state] ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
