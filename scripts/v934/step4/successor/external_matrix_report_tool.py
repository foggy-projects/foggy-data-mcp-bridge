#!/usr/bin/env python3
"""Select the Step 4 successor external contract for the frozen Step 3 tool."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[4]
TOOL = ROOT / "scripts/v934/step3/external_matrix_report_tool.py"
CONTRACT = Path(__file__).with_name("external-matrix-contract.json")


def main() -> int:
    spec = importlib.util.spec_from_file_location("v934_step3_external_matrix_report_tool", TOOL)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load frozen external report tool: {TOOL}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    module.CONTRACT_PATH = CONTRACT
    return int(module.main())


if __name__ == "__main__":
    raise SystemExit(main())
