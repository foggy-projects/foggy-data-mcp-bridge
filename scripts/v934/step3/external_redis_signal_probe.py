#!/usr/bin/env python3
"""Exercise external stateful runners against real INT/TERM/HUP signals."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[3]
RUNS_ROOT = ROOT / "target/v934-step3-external-matrix/runs"
EXPECTED = {"INT": 130, "TERM": 143, "HUP": 129}
RESOURCES = {
    "redis": {
        "runner": ROOT / "scripts/verify-v934-external-redis.sh",
        "environment": "V934_EXTERNAL_REDIS_SIGNAL_PROBE",
        "cell": "redis7",
        "ready_timeout": 90,
        "finalize_timeout": 45,
    },
    "mongo": {
        "runner": ROOT / "scripts/verify-v934-external-mongo.sh",
        "environment": "V934_EXTERNAL_MONGO_SIGNAL_PROBE",
        "cell": "mongo6",
        "ready_timeout": 90,
        "finalize_timeout": 45,
    },
    "mysql": {
        "runner": ROOT / "scripts/verify-v934-external-mysql.sh",
        "environment": "V934_EXTERNAL_MYSQL_SIGNAL_PROBE",
        "cell": "mysql57",
        "ready_timeout": 180,
        "finalize_timeout": 45,
    },
    "vector": {
        "runner": ROOT / "scripts/verify-v934-external-vector.sh",
        "environment": "V934_EXTERNAL_VECTOR_SIGNAL_PROBE",
        "cell": "milvus24",
        "ready_timeout": 240,
        "finalize_timeout": 45,
    },
    "matrix": {
        "runner": ROOT / "scripts/verify-v934-external-matrix.sh",
        "environment": "V934_EXTERNAL_MATRIX_SIGNAL_PROBE",
        "cell": "redis7",
        "ready_timeout": 120,
        "finalize_timeout": 60,
    },
}


def fail(message: str) -> None:
    raise RuntimeError(message)


def sha256(path: Path) -> str:
    if path.is_symlink() or not path.is_file():
        fail(f"required evidence is not a regular file: {path}")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_env(path: Path) -> dict[str, str]:
    if path.is_symlink() or not path.is_file():
        fail(f"environment evidence is missing: {path}")
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or "=" not in line:
            fail(f"malformed environment evidence: {path}")
        key, value = line.split("=", 1)
        if not key or key in values:
            fail(f"duplicate environment key: {path}")
        values[key] = value
    return values


def docker_count(args: list[str]) -> int:
    result = subprocess.run(
        ["docker", *args], cwd=ROOT, check=True, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    return len([line for line in result.stdout.splitlines() if line.strip()])


def wait_ready(process: subprocess.Popen[str], ready: Path, timeout: float) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if ready.is_file():
            return
        code = process.poll()
        if code is not None:
            stdout, stderr = process.communicate()
            fail(f"runner exited before signal readiness: code={code}\n{stdout}\n{stderr}")
        time.sleep(0.1)
    fail("runner did not reach signal readiness before timeout")


def terminate_probe(process: subprocess.Popen[str]) -> None:
    if process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def run_probe(prefix: str, signal_name: str, resource: str) -> dict[str, str]:
    configuration = RESOURCES[resource]
    expected_code = EXPECTED[signal_name]
    run_id = f"{prefix}-{signal_name.lower()}"
    if re.fullmatch(r"[A-Za-z0-9._-]+", run_id) is None:
        fail(f"unsafe signal probe run id: {run_id}")
    run_root = RUNS_ROOT / run_id
    if run_root.exists() or run_root.is_symlink():
        fail(f"signal probe run root already exists: {run_root}")
    environment = os.environ.copy()
    environment[str(configuration["environment"])] = "true"
    process = subprocess.Popen(
        [str(configuration["runner"]), run_id],
        cwd=ROOT,
        env=environment,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        start_new_session=True,
    )
    if resource == "matrix":
        ready_path = run_root / "lanes/external-redis/signal-probe-ready.env"
        status_path = run_root / "run-status.env"
        child_status_path = run_root / "lanes/external-redis/run-status.env"
        cleanup_path = run_root / "lanes/external-redis/cells/redis7/cleanup.env"
    else:
        ready_path = run_root / "signal-probe-ready.env"
        status_path = run_root / "run-status.env"
        child_status_path = None
        cleanup_path = run_root / f"cells/{configuration['cell']}/cleanup.env"
    try:
        wait_ready(
            process,
            ready_path,
            float(configuration["ready_timeout"]),
        )
        os.kill(process.pid, getattr(signal, f"SIG{signal_name}"))
        try:
            stdout, stderr = process.communicate(
                timeout=float(configuration["finalize_timeout"]),
            )
        except subprocess.TimeoutExpired as error:
            terminate_probe(process)
            raise RuntimeError(f"{signal_name} runner did not finalize after signal") from error
    finally:
        terminate_probe(process)
    if process.returncode != expected_code:
        fail(
            f"{signal_name} runner exit differs: {process.returncode} != {expected_code}\n"
            f"stdout={stdout[-2000:]}\nstderr={stderr[-2000:]}"
        )
    ready = parse_env(ready_path)
    status = parse_env(status_path)
    child_status = parse_env(child_status_path) if child_status_path else None
    cleanup = parse_env(cleanup_path)
    resource_cleanup_matches = (
        cleanup.get("container_residue") == "0"
        and cleanup.get("volume_residue") == "0"
        and cleanup.get("status") == "passed"
    )
    if resource in {"redis", "mysql", "matrix"}:
        resource_cleanup_matches = (
            resource_cleanup_matches
            and cleanup.get("container") == ready.get("container")
            and cleanup.get("volume") == ready.get("volume")
        )
    elif resource == "mongo":
        resource_cleanup_matches = (
            resource_cleanup_matches
            and cleanup.get("container") == ready.get("container")
            and cleanup.get("data_volume") == ready.get("data_volume")
            and cleanup.get("config_volume") == ready.get("config_volume")
        )
    elif resource == "vector":
        resource_cleanup_matches = (
            resource_cleanup_matches
            and cleanup.get("network_residue") == "0"
            and cleanup.get("cell") == configuration["cell"]
            and cleanup.get("network") == ready.get("network")
            and cleanup.get("milvus_container") == ready.get("milvus_container")
            and cleanup.get("etcd_container") == ready.get("etcd_container")
            and cleanup.get("minio_container") == ready.get("minio_container")
            and cleanup.get("milvus_volume") == ready.get("milvus_volume")
            and cleanup.get("etcd_volume") == ready.get("etcd_volume")
            and cleanup.get("minio_volume") == ready.get("minio_volume")
        )
    else:
        fail(f"unsupported signal-probe resource: {resource}")
    expected_last_phase = (
        "lane-external-redis" if resource == "matrix" else "signal-probe-ready"
    )
    if (
        status.get("run_id") != run_id
        or status.get("last_phase") != expected_last_phase
        or status.get("exit_code") != str(expected_code)
        or status.get("status") != "failed"
        or ready.get("run_id") != run_id
        or ready.get("status") != "ready"
        or not resource_cleanup_matches
    ):
        fail(f"{signal_name} durable signal evidence differs")
    if child_status is not None and (
        child_status.get("run_id") != run_id
        or child_status.get("last_phase") != "signal-probe-ready"
        or child_status.get("exit_code") != str(expected_code)
        or child_status.get("status") != "failed"
    ):
        fail(f"{signal_name} shared Redis child status evidence differs")
    absent = {
        "summary": not (run_root / "summary.env").exists(),
        "candidate": not (run_root / "candidate-manifest.json").exists(),
        "fifo": not (run_root / ".run-log.fifo").exists(),
    }
    if resource == "matrix":
        absent.update({
            "final": not (run_root / "final").exists(),
            "child_fifo": not (
                run_root / "lanes/external-redis/.run-log.fifo"
            ).exists(),
        })
    if not all(absent.values()):
        fail(f"{signal_name} retained forbidden success/FIFO artifacts: {absent}")
    container_residue = docker_count(
        ["ps", "-aq", "--filter", f"label=com.foggy.v934.external-run={run_id}"]
    )
    volume_residue = docker_count(
        ["volume", "ls", "-q", "--filter", f"label=com.foggy.v934.external-run={run_id}"]
    )
    network_residue = docker_count(
        ["network", "ls", "-q", "--filter", f"label=com.foggy.v934.external-run={run_id}"]
    )
    if container_residue or volume_residue or network_residue:
        fail(
            f"{signal_name} left Docker residue: "
            f"{container_residue}/{volume_residue}/{network_residue}"
        )
    return {
        "signal": signal_name,
        "expected_code": str(expected_code),
        "actual_code": str(process.returncode),
        "run_id": run_id,
        "status_sha256": sha256(status_path),
        "cleanup_sha256": sha256(cleanup_path),
        "container_residue": "0",
        "volume_residue": "0",
        "network_residue": "0",
        "summary_absent": "true",
        "candidate_absent": "true",
        "fifo_absent": "true",
        "status": "passed",
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--resource", choices=sorted(RESOURCES), default="redis")
    parser.add_argument("--run-prefix", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    if not output.is_absolute():
        output = ROOT / output
    if output.exists() or output.is_symlink():
        fail(f"signal probe output already exists: {output}")
    started = dt.datetime.now(dt.timezone.utc)
    rows = [run_probe(args.run_prefix, name, args.resource) for name in EXPECTED]
    header = [
        "signal", "expected_code", "actual_code", "run_id", "status_sha256",
        "cleanup_sha256", "container_residue", "volume_residue", "network_residue",
        "summary_absent", "candidate_absent", "fifo_absent", "status",
    ]
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
    content = "\t".join(header) + "\n" + "".join(
        "\t".join(row[key] for key in header) + "\n" for row in rows
    )
    temporary.write_text(content, encoding="utf-8")
    os.replace(temporary, output)
    elapsed = (dt.datetime.now(dt.timezone.utc) - started).total_seconds()
    print(
        f"V934_EXTERNAL_{args.resource.upper()}_SIGNAL "
        f"passed=3 total=3 elapsed_seconds={elapsed:.1f}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, OSError, subprocess.SubprocessError) as error:
        print(f"[E_SIGNAL_PROBE] {error}", file=sys.stderr)
        raise SystemExit(1)
