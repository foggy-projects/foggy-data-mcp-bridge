#!/usr/bin/env python3
"""Bind the frozen database-state harness to the Step 4 DB successor."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[4]
TOOL = ROOT / "scripts/v934/step3/database_state_negative_tool.py"
MATRIX_TOOL = ROOT / "scripts/v934/step3/database_matrix_report_tool.py"
PORTABLE_ADAPTER = Path(__file__).with_name("database_matrix_report_tool.py")
CONTRACT = Path(__file__).with_name("database-matrix-contract.json")


def main() -> int:
    adapter_spec = importlib.util.spec_from_file_location(
        "v934_step4_successor_database_matrix_adapter",
        PORTABLE_ADAPTER,
    )
    if adapter_spec is None or adapter_spec.loader is None:
        raise RuntimeError(f"cannot load successor database adapter: {PORTABLE_ADAPTER}")
    adapter = importlib.util.module_from_spec(adapter_spec)
    sys.modules[adapter_spec.name] = adapter
    adapter_spec.loader.exec_module(adapter)

    # The frozen state tool imports its frozen database report module by name.
    # Put that immutable module directory first, then change only the matrix
    # contract selection after the module has loaded.
    sys.path.insert(0, str(TOOL.parent))
    spec = importlib.util.spec_from_file_location(
        "v934_step3_database_state_negative_tool",
        TOOL,
    )
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load frozen database-state tool: {TOOL}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    if Path(module.matrix.__file__).resolve() != MATRIX_TOOL.resolve():
        raise RuntimeError(
            f"database-state tool loaded an unexpected matrix module: {module.matrix.__file__}"
        )
    adapter.install_portable_git_identity(module.matrix)
    module.MATRIX_CONTRACT_PATH = CONTRACT
    return int(module.main())


if __name__ == "__main__":
    raise SystemExit(main())
