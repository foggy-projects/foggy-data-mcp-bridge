#!/usr/bin/env python3
"""Bind frozen Step 3 required reporting to the successor DB state tool."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
from typing import Sequence


ROOT = Path(__file__).resolve().parents[4]
TOOL = ROOT / "scripts/v934/step3/step3_required_report_tool.py"
FROZEN_STATE_TOOL = ROOT / "scripts/v934/step3/database_state_negative_tool.py"
SUCCESSOR_STATE_TOOL = Path(__file__).with_name("database_state_negative_tool.py")


def _rewrite_state_verifier(command: Sequence[str]) -> list[str]:
    rewritten = list(command)
    if len(rewritten) >= 2 and Path(rewritten[1]).absolute() == FROZEN_STATE_TOOL:
        rewritten[1] = str(SUCCESSOR_STATE_TOOL)
    return rewritten


def _self_test() -> int:
    frozen = [sys.executable, str(FROZEN_STATE_TOOL), "verify", "--manifest", "probe.json"]
    rewritten = _rewrite_state_verifier(frozen)
    if rewritten != [
        sys.executable,
        str(SUCCESSOR_STATE_TOOL),
        "verify",
        "--manifest",
        "probe.json",
    ]:
        raise RuntimeError("frozen database-state verifier was not rewritten exactly")
    unrelated = [sys.executable, str(TOOL), "verify-final"]
    if _rewrite_state_verifier(unrelated) != unrelated:
        raise RuntimeError("unrelated verifier command was rewritten")
    print(json.dumps({"state_verifier_rewrites": 1, "status": "passed"}, sort_keys=True))
    return 0


def main() -> int:
    if sys.argv[1:] == ["successor-self-test"]:
        return _self_test()
    spec = importlib.util.spec_from_file_location(
        "v934_step3_required_report_tool",
        TOOL,
    )
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load frozen Step 3 required report tool: {TOOL}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)

    frozen_run_verifier = module.run_verifier

    def run_verifier(command: list[str], label: str) -> None:
        frozen_run_verifier(_rewrite_state_verifier(command), label)

    module.run_verifier = run_verifier
    return int(module.main())


if __name__ == "__main__":
    raise SystemExit(main())
