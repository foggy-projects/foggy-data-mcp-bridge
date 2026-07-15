#!/usr/bin/env python3
"""Exec one shared external lane with a signal-safe process boundary."""

from __future__ import annotations

import argparse
import fcntl
import os
from pathlib import Path
import re
import signal
import sys


ROOT = Path(__file__).resolve().parents[3]
RUNNER_ENVIRONMENTS = {
    ROOT / "scripts/verify-v934-external-redis.sh":
        "V934_EXTERNAL_REDIS_SIGNAL_PROBE",
    ROOT / "scripts/verify-v934-external-mongo.sh":
        "V934_EXTERNAL_MONGO_SIGNAL_PROBE",
    ROOT / "scripts/verify-v934-external-mysql.sh":
        "V934_EXTERNAL_MYSQL_SIGNAL_PROBE",
    ROOT / "scripts/verify-v934-external-vector.sh":
        "V934_EXTERNAL_VECTOR_SIGNAL_PROBE",
}
RUN_ID_PATTERN = re.compile(r"[A-Za-z0-9._-]+")


def fail(message: str) -> None:
    raise RuntimeError(message)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--runner", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--signal-environment", required=True)
    parser.add_argument("--probe-value", choices=("false", "true"), required=True)
    parser.add_argument("--lock-fd", type=int, required=True)
    return parser.parse_args()


def reset_exec_signals() -> None:
    names = ("SIGINT", "SIGTERM", "SIGHUP", "SIGPIPE", "SIGXFZ", "SIGXFSZ")
    for name in names:
        value = getattr(signal, name, None)
        if value is not None:
            signal.signal(value, signal.SIG_DFL)


def main() -> int:
    args = parse_args()
    if (
        RUN_ID_PATTERN.fullmatch(args.run_id) is None
        or args.run_id in {".", ".."}
    ):
        fail(f"unsafe run id: {args.run_id}")
    runner = Path(args.runner)
    if not runner.is_absolute() or runner.is_symlink() or not runner.is_file():
        fail(f"lane runner is not a canonical regular file: {runner}")
    runner = runner.resolve(strict=True)
    expected_environment = RUNNER_ENVIRONMENTS.get(runner)
    if expected_environment is None:
        fail(f"unsupported shared lane runner: {runner}")
    if args.signal_environment != expected_environment:
        fail("shared lane signal environment does not match its runner")
    if args.lock_fd <= 2:
        fail("authority lock descriptor must be greater than stderr")
    if os.environ.get("V934_AUTHORITY_LOCK_FD") != str(args.lock_fd):
        fail("authority lock descriptor differs from the exported descriptor")
    try:
        os.fstat(args.lock_fd)
        os.set_inheritable(args.lock_fd, True)
        # flock locks belong to the open-file description. Re-locking the
        # inherited description succeeds; a different descriptor would be
        # blocked by the outer runner's exclusive lock.
        fcntl.flock(args.lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError as error:
        raise RuntimeError(
            "authority lock descriptor does not inherit the outer lock",
        ) from error
    except OSError as error:
        raise RuntimeError("authority lock descriptor is not open") from error

    # Non-job-control Bash starts asynchronous commands with SIGINT ignored.
    # Reset signal dispositions before exec so the lane runner's traps can own
    # INT/TERM/HUP; also undo the signals Python itself ignores by default.
    reset_exec_signals()
    os.setsid()
    if os.getsid(0) != os.getpid() or os.getpgrp() != os.getpid():
        fail("shared lane launcher did not establish its own session/process group")

    environment = os.environ.copy()
    environment[args.signal_environment] = args.probe_value
    argv = [str(runner), "--shared-child", args.run_id]
    os.execve(str(runner), argv, environment)
    return 70


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError) as error:
        print(f"[v934-external-lane-launcher] ERROR: {error}", file=sys.stderr)
        raise SystemExit(70)
