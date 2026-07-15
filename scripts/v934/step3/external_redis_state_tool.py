#!/usr/bin/env python3
"""Run and verify Step 3 Redis resource-state fail-closed probes.

The tool owns only resources whose deterministic name and three Docker labels
match the supplied immutable outer run id and probe.  It never reuses, stops,
or removes an unrelated container or volume.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from typing import Any


SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parents[2]
CONTRACT_PATH = SCRIPT_DIR / "external-redis-state-contract.json"
RUNS_ROOT = REPO_ROOT / "target/v934-step3-external-matrix/runs"

REDIS_IMAGE_REF = (
    "redis@sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98"
)
REDIS_IMAGE_ID = (
    "sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98"
)
REDIS_VERSION = "7.4.6"

EXACT_PROBES = (
    ("wrong-container-identity", "E_RESOURCE_IDENTITY", 1, "passed"),
    ("wrong-mount-identity", "E_RESOURCE_MOUNT", 1, "passed"),
    ("dirty-state", "E_RESOURCE_DIRTY", 1, "passed"),
    ("forced-cleanup-failure", "E_RESOURCE_CLEANUP", 1, "failed"),
)
OUTPUT_FIELDS = ("probe", "expected_error", "actual_error", "status")
RUN_LABEL = "com.foggy.v934.external-run"
CELL_LABEL = "com.foggy.v934.external-cell"
PROBE_LABEL = "com.foggy.v934.external-state-probe"


class StateError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


def fail(code: str, message: str) -> None:
    raise StateError(code, message)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_run_id(value: str) -> str:
    if re.fullmatch(r"[A-Za-z0-9._-]+", value) is None or value in {".", ".."}:
        fail("E_INPUT", f"unsafe run id: {value!r}")
    return value


def load_json(path: Path, code: str) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        fail(code, f"JSON input is not a regular file: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(code, f"cannot read JSON input {path}: {error}")
    if not isinstance(value, dict):
        fail(code, f"JSON root is not an object: {path}")
    return value


def load_contract() -> tuple[dict[str, Any], str]:
    raw = load_json(CONTRACT_PATH, "E_CONTRACT")
    if set(raw) != {
        "artifact_contract",
        "bindings",
        "image",
        "kind",
        "lane",
        "probes",
        "required",
        "schema_version",
        "totals",
        "version",
    }:
        fail("E_CONTRACT", "Redis resource-state contract field set differs")
    if (
        raw["kind"] != "v934-step3-external-redis-state-negative-contract"
        or raw["lane"] != "external-redis-state-negative"
        or raw["schema_version"] != 1
        or raw["version"] != "9.3.4"
        or raw["required"] is not True
    ):
        fail("E_CONTRACT", "Redis resource-state contract identity differs")
    if raw["artifact_contract"] != {
        "probe_table": "aggregate/redis-resource-state-negatives.tsv"
    }:
        fail("E_CONTRACT", "Redis resource-state artifact contract differs")
    if raw["image"] != {
        "id": REDIS_IMAGE_ID,
        "ref": REDIS_IMAGE_REF,
        "version": REDIS_VERSION,
    }:
        fail("E_CONTRACT", "Redis resource-state image identity differs")
    expected_probes = [
        {
            "probe": probe,
            "expected_error": error,
            "expected_exit": exit_code,
            "expected_cleanup": cleanup,
        }
        for probe, error, exit_code, cleanup in EXACT_PROBES
    ]
    if raw["probes"] != expected_probes:
        fail("E_CONTRACT", "Redis resource-state exact probes differ")
    if raw["totals"] != {
        "probes": 4,
        "failed_as_expected": 4,
        "resource_residue": "0/0",
    }:
        fail("E_CONTRACT", "Redis resource-state totals differ")
    bindings = raw["bindings"]
    if not isinstance(bindings, dict) or set(bindings) != {"tool"}:
        fail("E_CONTRACT", "Redis resource-state binding set differs")
    binding = bindings["tool"]
    relative = "scripts/v934/step3/external_redis_state_tool.py"
    if (
        not isinstance(binding, dict)
        or set(binding) != {"path", "sha256"}
        or binding["path"] != relative
        or re.fullmatch(r"[0-9a-f]{64}", str(binding["sha256"])) is None
    ):
        fail("E_CONTRACT", "Redis resource-state tool binding differs")
    tool = REPO_ROOT / relative
    if tool.is_symlink() or not tool.is_file() or sha256_file(tool) != binding["sha256"]:
        fail("E_BINDING", "Redis resource-state tool SHA-256 differs")
    return raw, sha256_file(CONTRACT_PATH)


def run_command(
    args: list[str],
    *,
    check: bool = True,
    timeout: float = 30.0,
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            args,
            check=check,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as error:
        fail("E_RUNTIME", f"command timed out: {args[0]} {args[1:3]}: {error}")
    except subprocess.CalledProcessError as error:
        stderr = (error.stderr or "").strip()
        fail(
            "E_RUNTIME",
            f"command failed ({error.returncode}): {args[0]} {args[1:3]}: {stderr}",
        )


def docker_lines(*args: str) -> list[str]:
    result = run_command(["docker", *args])
    return [line for line in result.stdout.splitlines() if line]


def resource_identity(run_id: str, probe: str) -> tuple[str, str, str]:
    scope = hashlib.sha256(f"{run_id}|redis-state|{probe}\n".encode()).hexdigest()[:12]
    container = f"v934ext-redis-state-{scope}"
    volume = f"{container}-data"
    cell = f"redis-state-{probe}"
    return container, volume, cell


def label_filters(run_id: str, probe: str) -> list[str]:
    return [
        "--filter",
        f"label={RUN_LABEL}={run_id}",
        "--filter",
        f"label={PROBE_LABEL}={probe}",
    ]


def residue(run_id: str, probe: str) -> tuple[int, int]:
    filters = label_filters(run_id, probe)
    containers = docker_lines("ps", "-aq", *filters)
    volumes = docker_lines("volume", "ls", "-q", *filters)
    return len(containers), len(volumes)


def ensure_absent(run_id: str, probe: str) -> None:
    container, volume, _ = resource_identity(run_id, probe)
    named_containers = docker_lines("ps", "-aq", "--filter", f"name=^/{container}$")
    named_volumes = docker_lines("volume", "ls", "-q", "--filter", f"name=^{volume}$")
    labelled = residue(run_id, probe)
    if named_containers or named_volumes or labelled != (0, 0):
        fail(
            "E_RESOURCE_COLLISION",
            f"Redis state resources already exist for {probe}: "
            f"named={len(named_containers)}/{len(named_volumes)} labelled={labelled}",
        )


def owned_labels(run_id: str, probe: str, cell: str) -> dict[str, str]:
    return {RUN_LABEL: run_id, CELL_LABEL: cell, PROBE_LABEL: probe}


def named_resource_presence(run_id: str, probe: str) -> tuple[bool, bool]:
    container, volume, _ = resource_identity(run_id, probe)
    return (
        bool(docker_lines("ps", "-aq", "--filter", f"name=^/{container}$")),
        bool(docker_lines("volume", "ls", "-q", "--filter", f"name=^{volume}$")),
    )


def verify_container_owned(run_id: str, probe: str, container: str) -> list[str]:
    _, _, cell = resource_identity(run_id, probe)
    expected = owned_labels(run_id, probe, cell)
    value = load_docker_json(["inspect", container], "cleanup container")[0]
    labels = (value.get("Config") or {}).get("Labels") or {}
    if value.get("Name") != f"/{container}" or any(
        labels.get(key) != expected_value for key, expected_value in expected.items()
    ):
        fail("E_RESOURCE_OWNERSHIP", f"container ownership differs for {probe}")
    named_volume = resource_identity(run_id, probe)[1]
    anonymous = [
        mount.get("Name")
        for mount in value.get("Mounts") or []
        if isinstance(mount, dict)
        and mount.get("Type") == "volume"
        and mount.get("Name") != named_volume
    ]
    if any(not isinstance(name, str) or not name for name in anonymous):
        fail("E_RESOURCE_OWNERSHIP", f"container has an invalid anonymous volume for {probe}")
    if probe == "wrong-mount-identity":
        destinations = {
            mount.get("Destination")
            for mount in value.get("Mounts") or []
            if isinstance(mount, dict) and mount.get("Name") in anonymous
        }
        if len(anonymous) != 1 or destinations != {"/data"}:
            fail("E_RESOURCE_OWNERSHIP", "wrong-mount anonymous volume fixture differs")
    elif anonymous:
        fail("E_RESOURCE_OWNERSHIP", f"unexpected anonymous volume for {probe}")
    return anonymous


def verify_volume_owned(run_id: str, probe: str, volume: str) -> None:
    _, _, cell = resource_identity(run_id, probe)
    expected = owned_labels(run_id, probe, cell)
    value = load_docker_json(["volume", "inspect", volume], "cleanup volume")[0]
    labels = value.get("Labels") or {}
    if value.get("Name") != volume or any(
        labels.get(key) != expected_value for key, expected_value in expected.items()
    ):
        fail("E_RESOURCE_OWNERSHIP", f"volume ownership differs for {probe}")


def cleanup_owned(run_id: str, probe: str, *, force_failure: bool = False) -> str | None:
    container, volume, _ = resource_identity(run_id, probe)
    container_exists, volume_exists = named_resource_presence(run_id, probe)
    anonymous_volumes: list[str] = []
    if container_exists:
        anonymous_volumes = verify_container_owned(run_id, probe, container)
    if volume_exists:
        verify_volume_owned(run_id, probe, volume)
    if force_failure:
        if not (container_exists and volume_exists):
            fail("E_PROBE", f"forced cleanup fixture is incomplete for {probe}")
        attempted = run_command(
            ["docker", "volume", "rm", volume], check=False, timeout=15.0
        )
        if attempted.returncode == 0:
            fail("E_PROBE", "forced cleanup unexpectedly removed an in-use volume")
        if named_resource_presence(run_id, probe) != (True, True) or residue(
            run_id, probe
        ) != (1, 1):
            fail("E_PROBE", "forced cleanup failure did not preserve the owned fixture")
        return "E_RESOURCE_CLEANUP"
    if container_exists:
        run_command(["docker", "rm", "-fv", container])
        for anonymous in anonymous_volumes:
            inspected = run_command(
                ["docker", "volume", "inspect", anonymous], check=False
            )
            if inspected.returncode == 0:
                fail(
                    "E_RESOURCE_CLEANUP",
                    f"container cleanup left anonymous volume for {probe}: {anonymous}",
                )
    if volume_exists:
        run_command(["docker", "volume", "rm", volume])
    if residue(run_id, probe) != (0, 0):
        fail("E_RESOURCE_CLEANUP", f"Redis state cleanup left labelled residue for {probe}")
    if docker_lines("ps", "-aq", "--filter", f"name=^/{container}$"):
        fail("E_RESOURCE_CLEANUP", f"Redis state cleanup left container for {probe}")
    if docker_lines("volume", "ls", "-q", "--filter", f"name=^{volume}$"):
        fail("E_RESOURCE_CLEANUP", f"Redis state cleanup left volume for {probe}")
    return None


def load_docker_json(args: list[str], label: str) -> list[dict[str, Any]]:
    result = run_command(["docker", *args])
    try:
        value = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        fail("E_RUNTIME", f"invalid Docker JSON for {label}: {error}")
    if not isinstance(value, list) or not value or not all(isinstance(row, dict) for row in value):
        fail("E_RUNTIME", f"unexpected Docker JSON for {label}")
    return value


def create_resource(run_id: str, probe: str) -> None:
    container, volume, cell = resource_identity(run_id, probe)
    labels = owned_labels(run_id, probe, cell)
    volume_args = ["docker", "volume", "create"]
    for key, value in labels.items():
        volume_args.extend(["--label", f"{key}={value}"])
    volume_args.append(volume)
    run_command(volume_args)

    image = REDIS_IMAGE_ID if probe == "wrong-container-identity" else REDIS_IMAGE_REF
    mount_target = "/probe-data" if probe == "wrong-mount-identity" else "/data"
    run_args = ["docker", "run", "-d", "--name", container]
    for key, value in labels.items():
        run_args.extend(["--label", f"{key}={value}"])
    run_args.extend(
        [
            "--network",
            "bridge",
            "-p",
            "127.0.0.1::6379",
            "--mount",
            f"type=volume,source={volume},target={mount_target}",
            image,
            "redis-server",
            "--appendonly",
            "no",
            "--save",
            "",
        ]
    )
    run_command(run_args)

    deadline = time.monotonic() + 30.0
    while time.monotonic() < deadline:
        ping = run_command(
            ["docker", "exec", container, "redis-cli", "ping"],
            check=False,
            timeout=5.0,
        )
        if ping.returncode == 0 and ping.stdout.strip() == "PONG":
            break
        time.sleep(0.25)
    else:
        fail("E_RESOURCE_IDENTITY", f"Redis state resource did not become ready: {probe}")


def verify_runtime(run_id: str, probe: str) -> str | None:
    container, volume, cell = resource_identity(run_id, probe)
    expected_labels = owned_labels(run_id, probe, cell)
    container_json = load_docker_json(["inspect", container], "Redis container")[0]
    config = container_json.get("Config") or {}
    host_config = container_json.get("HostConfig") or {}
    network = container_json.get("NetworkSettings") or {}
    attached_networks = network.get("Networks") or {}
    labels = config.get("Labels") or {}
    ports = network.get("Ports") or {}
    port_bindings = ports.get("6379/tcp")
    identity_ok = (
        container_json.get("Name") == f"/{container}"
        and container_json.get("Image") == REDIS_IMAGE_ID
        and config.get("Image") == REDIS_IMAGE_REF
        and host_config.get("NetworkMode") == "bridge"
        and isinstance(attached_networks, dict)
        and set(attached_networks) == {"bridge"}
        and all(labels.get(key) == value for key, value in expected_labels.items())
        and isinstance(port_bindings, list)
        and len(port_bindings) == 1
        and port_bindings[0].get("HostIp") == "127.0.0.1"
        and re.fullmatch(r"[0-9]+", str(port_bindings[0].get("HostPort", ""))) is not None
    )
    info = run_command(["docker", "exec", container, "redis-cli", "INFO", "server"])
    info_values: dict[str, str] = {}
    for line in info.stdout.replace("\r", "").splitlines():
        if ":" in line and not line.startswith("#"):
            key, value = line.split(":", 1)
            info_values[key] = value
    if (
        info_values.get("redis_version") != REDIS_VERSION
        or info_values.get("redis_mode") != "standalone"
    ):
        identity_ok = False
    if not identity_ok:
        return "E_RESOURCE_IDENTITY"

    volume_json = load_docker_json(["volume", "inspect", volume], "Redis volume")[0]
    volume_labels = volume_json.get("Labels") or {}
    mounts = container_json.get("Mounts") or []
    expected_mount = {
        "Type": "volume",
        "Name": volume,
        "Destination": "/data",
        "RW": True,
    }
    normalized_mounts = [
        {key: mount.get(key) for key in expected_mount}
        for mount in mounts
        if isinstance(mount, dict)
    ]
    if (
        volume_json.get("Name") != volume
        or any(volume_labels.get(key) != value for key, value in expected_labels.items())
        or normalized_mounts != [expected_mount]
    ):
        return "E_RESOURCE_MOUNT"

    dbsize = run_command(["docker", "exec", container, "redis-cli", "DBSIZE"])
    if dbsize.stdout.strip() != "0":
        return "E_RESOURCE_DIRTY"
    return None


def run_probe(run_id: str, probe: str, expected_error: str) -> dict[str, str]:
    ensure_absent(run_id, probe)
    actual_error: str | None = None
    cleanup_status = "passed"
    try:
        create_resource(run_id, probe)
        if probe == "dirty-state":
            container, _, _ = resource_identity(run_id, probe)
            run_command(
                ["docker", "exec", container, "redis-cli", "SET", "foreign:key", "dirty"]
            )
        observed = verify_runtime(run_id, probe)
        if probe == "forced-cleanup-failure":
            if observed is not None:
                fail(
                    "E_PROBE",
                    f"clean resource did not verify before forced cleanup: {observed}",
                )
            actual_error = cleanup_owned(run_id, probe, force_failure=True)
            cleanup_status = "failed"
            # The probe must leave the first cleanup attempt red, while the
            # harness still owns deterministic recovery and publishes zero
            # final residue.
            cleanup_owned(run_id, probe)
        else:
            actual_error = observed
            cleanup_error = cleanup_owned(run_id, probe)
            if cleanup_error is not None:
                fail("E_PROBE", f"unexpected cleanup error: {cleanup_error}")
        if actual_error != expected_error:
            fail(
                "E_PROBE",
                f"probe {probe} expected {expected_error}, observed {actual_error or 'success'}",
            )
        if residue(run_id, probe) != (0, 0):
            fail("E_RESOURCE_CLEANUP", f"probe {probe} left labelled residue")
        return {
            "probe": probe,
            "expected_error": expected_error,
            "actual_error": actual_error,
            "status": "passed",
            "_cleanup_status": cleanup_status,
        }
    finally:
        if named_resource_presence(run_id, probe) != (False, False) or residue(
            run_id, probe
        ) != (0, 0):
            cleanup_owned(run_id, probe)


def canonical_output(run_id: str, output: Path) -> Path:
    expected = RUNS_ROOT / run_id / "aggregate/redis-resource-state-negatives.tsv"
    absolute = output.absolute()
    if absolute != expected.absolute():
        fail("E_OUTPUT", f"Redis state output is not canonical: {output}")
    if output.exists() or output.is_symlink():
        fail("E_OUTPUT", f"Redis state output already exists: {output}")
    try:
        relative_parent = output.parent.absolute().relative_to(REPO_ROOT)
    except ValueError:
        fail("E_OUTPUT", f"Redis state output escapes the repository: {output}")
    current = REPO_ROOT
    for part in relative_parent.parts:
        current /= part
        if current.is_symlink():
            fail("E_OUTPUT", f"Redis state output contains a symlink component: {current}")
    if not output.parent.is_dir():
        fail("E_OUTPUT", f"Redis state aggregate directory is not canonical: {output.parent}")
    return output


def canonical_input(path: Path) -> Path:
    absolute = path.absolute()
    if absolute.name != "redis-resource-state-negatives.tsv" or absolute.parent.name != "aggregate":
        fail("E_EVIDENCE", f"Redis state table path is not canonical: {path}")
    run_root = absolute.parent.parent
    if run_root.parent != RUNS_ROOT or safe_run_id(run_root.name) != run_root.name:
        fail("E_EVIDENCE", f"Redis state table is outside the matrix runs root: {path}")
    current = REPO_ROOT
    for part in absolute.parent.relative_to(REPO_ROOT).parts:
        current /= part
        if current.is_symlink():
            fail("E_EVIDENCE", f"Redis state table contains a symlink component: {current}")
    return absolute


def write_rows(output: Path, rows: list[dict[str, str]]) -> None:
    temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
    with temporary.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=OUTPUT_FIELDS, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row[key] for key in OUTPUT_FIELDS})
    os.replace(temporary, output)


def read_rows(path: Path) -> list[dict[str, str]]:
    if path.is_symlink() or not path.is_file():
        fail("E_EVIDENCE", f"Redis state table is not regular: {path}")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if tuple(reader.fieldnames or ()) != OUTPUT_FIELDS:
            fail("E_EVIDENCE", "Redis state table header differs")
        rows = list(reader)
    expected_rows = [
        {
            "probe": probe,
            "expected_error": error,
            "actual_error": error,
            "status": "passed",
        }
        for probe, error, _, _ in EXACT_PROBES
    ]
    if rows != expected_rows:
        fail("E_EVIDENCE", f"Redis state table rows differ: {rows}")
    return rows


def run_all(run_id: str, output: Path) -> None:
    load_contract()
    safe_run_id(run_id)
    canonical_output(run_id, output)
    run_command(["docker", "info"], timeout=15.0)
    image = load_docker_json(["image", "inspect", REDIS_IMAGE_REF], "frozen Redis image")[0]
    if image.get("Id") != REDIS_IMAGE_ID or REDIS_IMAGE_REF not in (image.get("RepoDigests") or []):
        fail("E_RESOURCE_IDENTITY", "frozen Redis image is unavailable or has wrong identity")
    rows: list[dict[str, str]] = []
    for probe, expected_error, expected_exit, expected_cleanup in EXACT_PROBES:
        print(f"[v934-external-redis-state] running probe={probe}", flush=True)
        completed = run_command(
            [
                sys.executable,
                str(Path(__file__).resolve()),
                "_probe",
                "--run-id",
                run_id,
                "--probe",
                probe,
            ],
            check=False,
            timeout=90.0,
        )
        try:
            row = json.loads(completed.stdout.strip())
        except json.JSONDecodeError as error:
            fail(
                "E_PROBE",
                f"probe {probe} did not publish machine evidence: "
                f"rc={completed.returncode} stderr={completed.stderr.strip()!r}",
            )
        if not isinstance(row, dict):
            fail("E_PROBE", f"probe {probe} machine evidence is not an object")
        if (
            completed.returncode != expected_exit
            or row.pop("_cleanup_status", None) != expected_cleanup
        ):
            fail("E_PROBE", f"probe exit/cleanup contract differs: {probe}")
        if set(row) != set(OUTPUT_FIELDS) or row.get("status") != "passed":
            fail("E_PROBE", f"probe machine evidence schema differs: {probe}")
        rows.append(row)
    write_rows(output, rows)
    read_rows(output)
    print(
        "V934_EXTERNAL_REDIS_STATE probes=4 failed_as_expected=4 residue=0/0 status=passed",
        flush=True,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate")
    run = commands.add_parser("run")
    run.add_argument("--run-id", required=True)
    run.add_argument("--output", type=Path, required=True)
    verify = commands.add_parser("verify")
    verify.add_argument("--input", type=Path, required=True)
    probe = commands.add_parser("_probe", help=argparse.SUPPRESS)
    probe.add_argument("--run-id", required=True)
    probe.add_argument("--probe", choices=[row[0] for row in EXACT_PROBES], required=True)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        if args.command == "validate":
            _, digest = load_contract()
            print(
                "V934_EXTERNAL_REDIS_STATE_CONTRACT "
                f"probes=4 sha256={digest} status=verified"
            )
        elif args.command == "run":
            run_all(args.run_id, args.output)
        elif args.command == "verify":
            load_contract()
            rows = read_rows(canonical_input(args.input))
            print(f"V934_EXTERNAL_REDIS_STATE_VERIFY probes={len(rows)} status=verified")
        elif args.command == "_probe":
            load_contract()
            safe_run_id(args.run_id)
            expected = {probe: error for probe, error, _, _ in EXACT_PROBES}
            row = run_probe(args.run_id, args.probe, expected[args.probe])
            print(json.dumps(row, sort_keys=True))
            # Each fixture proves that the runtime validator rejected the
            # malformed state.  The parent accepts only this exact non-zero
            # process outcome together with the machine row above.
            return 1
    except (StateError, OSError) as error:
        if isinstance(error, StateError):
            print(f"[{error.code}] {error.message}", file=sys.stderr)
        else:
            print(f"[E_RUNTIME] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
