#!/usr/bin/env python3
"""Select the Step 4 successor database contract for the frozen Step 3 tool."""

from __future__ import annotations

import os
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[4]
TOOL = ROOT / "scripts/v934/step3/database_matrix_report_tool.py"
CONTRACT = Path(__file__).with_name("database-matrix-contract.json")


def main() -> None:
    arguments = [
        sys.executable,
        str(TOOL),
        "--repo-root",
        str(ROOT),
        "--contract",
        str(CONTRACT),
        *sys.argv[1:],
    ]
    os.execv(sys.executable, arguments)


if __name__ == "__main__":
    main()
