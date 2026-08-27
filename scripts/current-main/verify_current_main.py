#!/usr/bin/env python3
"""Verify the current-main compatibility contract without third-party packages."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def require_equal(errors: list[str], label: str, actual: str | None, expected: str) -> None:
    if actual != expected:
        errors.append(f"{label}: expected {expected!r}, found {actual!r}")


def xml_version(path: Path) -> str | None:
    root = ET.parse(path).getroot()
    namespace = root.tag.partition("}")[0].lstrip("{")
    return root.findtext(f"{{{namespace}}}version")


def xml_modules(path: Path) -> set[str]:
    root = ET.parse(path).getroot()
    namespace = root.tag.partition("}")[0].lstrip("{")
    return {
        element.text.strip()
        for element in root.findall(f"{{{namespace}}}modules/{{{namespace}}}module")
        if element.text
    }


def regex_value(path: Path, pattern: str) -> str | None:
    match = re.search(pattern, path.read_text(encoding="utf-8"), re.MULTILINE)
    return match.group(1) if match else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).parents[2])
    parser.add_argument("--cli-root", type=Path)
    args = parser.parse_args()
    root = args.repo_root.resolve()
    contract = json.loads((root / "current-main.json").read_text(encoding="utf-8"))
    errors: list[str] = []

    require_equal(errors, "root Maven coordinate", xml_version(root / "pom.xml"),
                  contract["mavenCoordinateVersion"])
    reactor_modules = xml_modules(root / "pom.xml")
    for module in contract["gates"]["bridgeModules"]:
        if module not in reactor_modules:
            errors.append(f"bridge gate module is not in the Maven reactor: {module}")
        elif not (root / module / "pom.xml").is_file():
            errors.append(f"bridge gate module POM is missing: {module}")
    require_equal(
        errors,
        "Runtime Console package",
        json.loads((root / "addons/foggy-runtime-console/frontend/package.json")
                   .read_text(encoding="utf-8"))["version"],
        contract["artifacts"]["foggy-runtime-console"],
    )
    require_equal(
        errors,
        "Analytics Console package",
        json.loads((root / "addons/foggy-analytics-console/frontend/package.json")
                   .read_text(encoding="utf-8"))["version"],
        contract["artifacts"]["foggy-analytics-console"],
    )
    if not (root / "docs" / "9.5" / contract["activeIteration"] / "README.md").is_file():
        errors.append("active iteration README is missing")

    protocol_source = (root / "foggy-dataset-mcp/src/main/java/com/foggyframework/"
                       "dataset/mcp/service/McpProtocolVersions.java").read_text(encoding="utf-8")
    for key, constant in (("modernStateless", "MODERN_STATELESS"),
                          ("latestLegacy", "LATEST_LEGACY"),
                          ("legacyCompatibility", "LEGACY_COMPAT")):
        actual = re.search(rf'{constant}\s*=\s*"([^"]+)"', protocol_source)
        require_equal(errors, f"MCP {key}", actual.group(1) if actual else None,
                      contract["mcp"][key])

    cli_root = args.cli_root
    if cli_root is None:
        candidate = root.parent / "foggy-runtime-cli"
        cli_root = candidate if candidate.is_dir() else None
    if cli_root is not None:
        cli_root = cli_root.resolve()
        require_equal(errors, "CLI project", regex_value(cli_root / "pyproject.toml",
                      r'^version\s*=\s*"([^"]+)"'), contract["artifacts"]["foggy-runtime-cli"])
        require_equal(errors, "CLI module", regex_value(
                      cli_root / "src/foggy_runtime_cli/__init__.py",
                      r'^__version__\s*=\s*"([^"]+)"'), contract["artifacts"]["foggy-runtime-cli"])
        stack = cli_root / "src/foggy_runtime_cli/stack_cli.py"
        require_equal(errors, "Launcher pin", regex_value(stack,
                      r'launcher_version\s*=\s*"([^"]+)"'),
                      contract["artifacts"]["foggy-runtime-launcher"])
        require_equal(errors, "Skill pin", regex_value(stack,
                      r'ANALYSIS_SKILL_RELEASE_VERSION\s*=\s*"([^"]+)"'),
                      contract["artifacts"]["foggy-ai-analysis-skill"])

    if errors:
        print("current-main contract failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("current-main contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
