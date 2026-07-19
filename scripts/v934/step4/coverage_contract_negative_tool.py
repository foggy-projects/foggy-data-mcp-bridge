#!/usr/bin/env python3
"""Run versioned fail-closed mutations against the Step 4 coverage contract.

Every probe copies all governed JSON/POM inputs to an isolated temporary
directory and mutates exactly one copy. Synthetic diagnostic and formal
fixtures invoke the explicitly named structure-only negative-fixture command
after proving that the full validator rejects non-canonical overrides. The
formal fixture uses forged all-``1`` evidence. Canonical files are never edited.
"""

from __future__ import annotations

import argparse
import ast
import copy
import hashlib
import importlib.util
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Callable, Sequence


PREFIX = "[v934-step4-coverage-contract-negative]"
MAVEN_NS = "http://maven.apache.org/POM/4.0.0"
NS = {"m": MAVEN_NS}
TOOL_PATH = Path("scripts/v934/step4/coverage_contract_negative_tool.py")
VALIDATOR_PATH = Path("scripts/v934/step4/coverage_tool.py")
REPORT_RUNNER_PATH = Path("scripts/v934/step4/coverage_report_runner.sh")
SUCCESSOR_OVERLAY_CONTRACT_PATH = Path(
    "scripts/v934/step4/successor/overlay-contract.json"
)
SUCCESSOR_OVERLAY_TOOL_PATH = Path("scripts/v934/step4/successor/overlay_tool.py")
PYTHON_DISPATCH_TOOLS = (
    ("EXEC_TOOL", Path("scripts/v934/step4/coverage_exec_tool.py")),
    ("CONTRACT_TOOL", Path("scripts/v934/step4/coverage_tool.py")),
    (
        "EFFECTIVE_POM_TOOL",
        Path("scripts/v934/step4/reporter_effective_pom_tool.py"),
    ),
    (
        "TOOLCHAIN_RECEIPT_TOOL",
        Path("scripts/v934/step4/toolchain_receipt_tool.py"),
    ),
)
PYTHON_DISPATCH_LOGICAL_COMMANDS = (
    ("assign-exec-tool", 'EXEC_TOOL="$SCRIPT_DIR/coverage_exec_tool.py"'),
    ("assign-contract-tool", 'CONTRACT_TOOL="$SCRIPT_DIR/coverage_tool.py"'),
    (
        "assign-effective-pom-tool",
        'EFFECTIVE_POM_TOOL="$SCRIPT_DIR/reporter_effective_pom_tool.py"',
    ),
    (
        "assign-toolchain-receipt-tool",
        'TOOLCHAIN_RECEIPT_TOOL="$SCRIPT_DIR/toolchain_receipt_tool.py"',
    ),
    ("require-exec-tool", 'require_real_file "$EXEC_TOOL"'),
    ("require-contract-tool", 'require_real_file "$CONTRACT_TOOL"'),
    ("require-effective-pom-tool", 'require_real_file "$EFFECTIVE_POM_TOOL"'),
    (
        "require-toolchain-receipt-tool",
        'require_real_file "$TOOLCHAIN_RECEIPT_TOOL"',
    ),
    (
        "toolchain-replay-pre",
        'if TOOLCHAIN_REPLAY_PRE_RESULT="$(python3 "$TOOLCHAIN_RECEIPT_TOOL" verify '
        '--repo-root "$REPO_ROOT" --run-id "$SESSION_PREFIX" '
        '--receipt "$TOOLCHAIN_RECEIPT")"; then',
    ),
    (
        "exec-verify",
        'python3 "$EXEC_TOOL" verify --repo-root "$REPO_ROOT" '
        '--exec-root "$EXEC_ROOT" --run-id "$SESSION_PREFIX" '
        '--session-prefix "$SESSION_PREFIX" --not-before-ns "$NOT_BEFORE_NS" '
        '--run-context "$RUN_DIR/run-context.json" --output "$EXEC_MANIFEST"',
    ),
    (
        "contract-validate",
        'python3 "$CONTRACT_TOOL" validate-contract --repo-root "$REPO_ROOT"',
    ),
    (
        "effective-pom-before",
        'python3 "$EFFECTIVE_POM_TOOL" --repo-root "$REPO_ROOT" '
        '--effective-pom "$REPORT_EFFECTIVE_BEFORE" '
        '--output "$REPORT_EFFECTIVE_RECEIPT_BEFORE" '
        '--negative-output "$REPORT_EFFECTIVE_NEGATIVE"',
    ),
    (
        "effective-pom-after",
        'python3 "$EFFECTIVE_POM_TOOL" --repo-root "$REPO_ROOT" '
        '--effective-pom "$REPORT_EFFECTIVE_AFTER" '
        '--output "$REPORT_EFFECTIVE_RECEIPT_AFTER"',
    ),
    (
        "toolchain-replay-post",
        'if TOOLCHAIN_REPLAY_POST_RESULT="$(python3 "$TOOLCHAIN_RECEIPT_TOOL" verify '
        '--repo-root "$REPO_ROOT" --run-id "$SESSION_PREFIX" '
        '--receipt "$TOOLCHAIN_RECEIPT")"; then',
    ),
    (
        "toolchain-receipt-provenance-argument",
        'python3 - "$RUN_REPORT_STAGE/toolchain-replay-pre.json" '
        '"$RUN_REPORT_STAGE/toolchain-replay-post.json" '
        '"$TOOLCHAIN_REPLAY_PRE_RESULT" "$TOOLCHAIN_REPLAY_POST_RESULT" '
        '"$SESSION_PREFIX" "$TOOLCHAIN_RECEIPT" "$TOOLCHAIN_RECEIPT_TOOL" <<\'PY\'',
    ),
    (
        "exec-verify-aggregate",
        'python3 "$EXEC_TOOL" verify-aggregate --repo-root "$REPO_ROOT" '
        '--exec-manifest "$EXEC_MANIFEST" '
        '--aggregate-exec "$RUN_REPORT/jacoco-aggregate.exec" '
        '--output "$AGGREGATE_PROVENANCE"',
    ),
)
PYTHON_DISPATCH_CALL_IDS = {
    "toolchain-replay-pre",
    "exec-verify",
    "contract-validate",
    "effective-pom-before",
    "effective-pom-after",
    "toolchain-replay-post",
    "exec-verify-aggregate",
}
PYTHON_DISPATCH_RUNNER_SHA256 = (
    "a83ed709ccbbf152cbbeba8d25c2fffb0bd3343ba5bf86a7a0a627562bad4d12"
)
PYTHON_DISPATCH_EXECUTABLE_STREAM_SHA256 = (
    "186d986fe6a13af7435190f6fbc8dcbacf614c9a53906e2ac48f18d140eb46e8"
)
POM_PATHS = {
    "root": Path("pom.xml"),
    "model": Path("foggy-dataset-model/pom.xml"),
    "reporter": Path("build-support/foggy-coverage-report/pom.xml"),
}
JSON_PATHS = {
    "contract": Path("scripts/v934/step4/coverage-contract.json"),
    "thresholds": Path("scripts/v934/step4/coverage-thresholds.json"),
}
INPUT_PATHS = {**POM_PATHS, **JSON_PATHS}
DIAGNOSTIC_THRESHOLD_SHA256 = "0df17a8774d2c0c0299146940f1e93453175263cda3f7ebfab9234c3e820ff96"


class NegativeError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise NegativeError(message)


def process_environment() -> dict[str, str]:
    environment = {
        name: os.environ[name]
        for name in ("PATH", "SYSTEMROOT", "TMPDIR", "TMP", "TEMP")
        if name in os.environ
    }
    environment.update(
        {
            "LANG": "C",
            "LC_ALL": "C",
            "PYTHONDONTWRITEBYTECODE": "1",
        }
    )
    return environment


def fixture_git_environment() -> dict[str, str]:
    environment = process_environment()
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
        }
    )
    return environment


def q(name: str) -> str:
    return f"{{{MAVEN_NS}}}{name}"


def sha256_file(path: Path) -> str:
    require(path.is_file() and not path.is_symlink(), f"not a real regular file: {path}")
    digest = hashlib.sha256()
    try:
        with path.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    except OSError as exc:
        raise NegativeError(f"cannot hash {path}: {exc.__class__.__name__}") from exc
    return digest.hexdigest()


@dataclass(frozen=True)
class LogicalShellCommand:
    line_number: int
    text: str
    scope_depth: int


HEREDOC_PATTERN = re.compile(
    r"<<(?P<strip>-)?\s*(?:'(?P<single>[^']+)'|\"(?P<double>[^\"]+)\"|"
    r"(?P<bare>[A-Za-z_][A-Za-z0-9_]*))"
)
SHELL_SCOPE_FUNCTION = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*\(\)\s*\{$")
PYTHON_DISPATCH_TARGET_PATTERN = re.compile(
    r"(?<![A-Za-z0-9_])(?:"
    + "|".join(
        re.escape(value)
        for value in (
            *(variable for variable, _ in PYTHON_DISPATCH_TOOLS),
            *(relative.name for _, relative in PYTHON_DISPATCH_TOOLS),
        )
    )
    + r")(?![A-Za-z0-9_])"
)


def strip_shell_comment(line: str) -> str:
    single = False
    double = False
    escaped = False
    for index, character in enumerate(line):
        if escaped:
            escaped = False
            continue
        if character == "\\" and not single:
            escaped = True
            continue
        if character == "'" and not double:
            single = not single
            continue
        if character == '"' and not single:
            double = not double
            continue
        if (
            character == "#"
            and not single
            and not double
            and (
                index == 0
                or line[index - 1].isspace()
                or line[index - 1] in ";|&(){}"
            )
        ):
            return line[:index]
    return line


def sensitive_shell_command_view(command: str) -> str:
    """Expose quote-spliced and empty-expansion command spellings."""
    candidate = re.sub(r"\$(?:''|\"\")", "", command)
    candidate = re.sub(r"\$\{[A-Za-z_][A-Za-z0-9_]*:-\}", "", candidate)
    candidate = candidate.replace("'", "").replace('"', "")
    return re.sub(r"\\([A-Za-z])", r"\1", candidate)


def logical_shell_commands(script: str) -> list[LogicalShellCommand]:
    result: list[LogicalShellCommand] = []
    parts: list[str] = []
    start_line = 0
    scope_depth = 0
    queued_heredocs: list[tuple[str, bool]] = []
    command_heredocs: list[tuple[str, bool]] = []

    for line_number, raw_line in enumerate(script.splitlines(), start=1):
        if queued_heredocs:
            delimiter, strip_tabs = queued_heredocs[0]
            candidate = raw_line.lstrip("\t") if strip_tabs else raw_line
            if candidate == delimiter:
                queued_heredocs.pop(0)
            continue

        code = strip_shell_comment(raw_line).rstrip()
        if not code.strip():
            continue
        if not parts:
            start_line = line_number
        for match in HEREDOC_PATTERN.finditer(code):
            delimiter = match.group("single") or match.group("double") or match.group("bare")
            require(bool(delimiter), "E_PYTHON_DISPATCH_PARSE: empty heredoc delimiter")
            command_heredocs.append((delimiter, bool(match.group("strip"))))

        continuation = False
        if code.endswith("\\"):
            code = code[:-1].rstrip()
            continuation = True
        elif code.endswith(("&&", "||", "|")):
            continuation = True
        parts.append(code.strip())
        if continuation:
            continue

        text = " ".join(parts)
        parts = []
        closing = text in {"}", ")", "fi", "esac"} or text.startswith("done")
        if closing:
            scope_depth = max(0, scope_depth - 1)
        result.append(LogicalShellCommand(start_line, text, scope_depth))

        opening = (
            bool(SHELL_SCOPE_FUNCTION.fullmatch(text))
            or bool(
                re.fullmatch(
                    r"function\s+[A-Za-z_][A-Za-z0-9_]*(?:\s*\(\))?\s*\{",
                    text,
                )
            )
            or text in {"{", "("}
            or bool(re.match(r"^if\b", text))
            or bool(re.match(r"^(?:for|while|until|select)\b", text))
            or bool(re.match(r"^case\b", text))
        )
        if opening:
            scope_depth += 1
        if command_heredocs:
            queued_heredocs.extend(command_heredocs)
            command_heredocs = []

    require(not parts, "E_PYTHON_DISPATCH_PARSE: unterminated logical command")
    require(not queued_heredocs, "E_PYTHON_DISPATCH_PARSE: unterminated heredoc")
    return result


def validate_bash_syntax(script: str) -> None:
    try:
        completed = subprocess.run(
            ["bash", "-n"],
            input=script,
            env=process_environment(),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
    except OSError as exc:
        raise NegativeError("E_PYTHON_DISPATCH_SYNTAX: cannot run bash -n") from exc
    require(
        completed.returncode == 0 and completed.stdout == "",
        "E_PYTHON_DISPATCH_SYNTAX: runner mutation is not valid Bash",
    )


def tracked_git_mode(root: Path, relative: Path) -> str:
    try:
        completed = subprocess.run(
            ["git", "-C", str(root), "ls-files", "--stage", "--", relative.as_posix()],
            env=fixture_git_environment(),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            check=False,
        )
    except OSError as exc:
        raise NegativeError(
            f"E_PYTHON_DISPATCH_GIT_MODE: cannot inspect {relative.as_posix()}"
        ) from exc
    require(
        completed.returncode == 0 and completed.stderr == "",
        f"E_PYTHON_DISPATCH_GIT_MODE: lookup failed for {relative.as_posix()}",
    )
    lines = completed.stdout.splitlines()
    if len(lines) != 1:
        disposition = (
            "untracked" if os.path.lexists(root / relative) else "missing"
        )
        raise NegativeError(
            f"E_PYTHON_DISPATCH_GIT_MODE: {disposition} path {relative.as_posix()}"
        )
    fields = lines[0].split(maxsplit=3)
    require(
        len(fields) == 4
        and bool(re.fullmatch(r"[0-9a-f]{40,64}", fields[1]))
        and fields[2] == "0"
        and fields[3] == relative.as_posix(),
        f"E_PYTHON_DISPATCH_GIT_MODE: staged identity differs for {relative.as_posix()}",
    )
    return fields[0]


def validate_python_dispatch_portability(
    root: Path,
    runner_text: str | None = None,
    enforce_raw_seal: bool = True,
    enforce_stream_seal: bool = True,
) -> dict[str, Any]:
    runner = root / REPORT_RUNNER_PATH
    if runner_text is None:
        try:
            runner_bytes = runner.read_bytes()
            runner_text = runner_bytes.decode("utf-8", errors="strict")
        except (OSError, UnicodeError) as exc:
            raise NegativeError(
                "E_PYTHON_DISPATCH_SOURCE: cannot read coverage report runner"
            ) from exc
    else:
        runner_bytes = runner_text.encode("utf-8", errors="strict")
    runner_sha256 = hashlib.sha256(runner_bytes).hexdigest()
    if enforce_raw_seal:
        require(
            runner_sha256 == PYTHON_DISPATCH_RUNNER_SHA256,
            "E_PYTHON_DISPATCH_RAW: report runner raw bytes differ",
        )
    validate_bash_syntax(runner_text)
    logical_commands = logical_shell_commands(runner_text)
    executable_stream = "".join(
        f"{command.text}\n" for command in logical_commands
    ).encode("utf-8")
    executable_stream_sha256 = hashlib.sha256(executable_stream).hexdigest()
    if enforce_stream_seal:
        require(
            executable_stream_sha256 == PYTHON_DISPATCH_EXECUTABLE_STREAM_SHA256,
            "E_PYTHON_DISPATCH_STREAM: logical executable stream differs",
        )
    target_commands = [
        command
        for command in logical_commands
        if (
            PYTHON_DISPATCH_TARGET_PATTERN.search(
                sensitive_shell_command_view(command.text)
            )
            or ".py" in sensitive_shell_command_view(command.text)
        )
    ]
    expected = list(PYTHON_DISPATCH_LOGICAL_COMMANDS)
    require(
        len(target_commands) == len(expected),
        "E_PYTHON_DISPATCH_COMMAND: target-reference command count differs",
    )
    bindings: list[dict[str, Any]] = []
    for command, (binding_id, expected_text) in zip(target_commands, expected):
        require(
            command.text == expected_text,
            f"E_PYTHON_DISPATCH_COMMAND: logical command differs for {binding_id}",
        )
        require(
            command.scope_depth == 0,
            f"E_PYTHON_DISPATCH_SCOPE: {binding_id} is not top-level executable code",
        )
        bindings.append(
            {
                "binding": binding_id,
                "line": command.line_number,
                "scope": "top-level",
                "status": "bound",
            }
        )

    git_modes = {REPORT_RUNNER_PATH.as_posix(): tracked_git_mode(root, REPORT_RUNNER_PATH)}
    require(
        git_modes[REPORT_RUNNER_PATH.as_posix()] == "100755",
        "E_PYTHON_DISPATCH_GIT_MODE: report runner must be 100755",
    )
    tool_bindings: list[dict[str, Any]] = []
    for variable, relative in PYTHON_DISPATCH_TOOLS:
        git_mode = tracked_git_mode(root, relative)
        git_modes[relative.as_posix()] = git_mode
        require(
            git_mode == "100644",
            f"E_PYTHON_DISPATCH_GIT_MODE: Python tool must be 100644: {relative.as_posix()}",
        )
        require(
            (root / relative).is_file() and not (root / relative).is_symlink(),
            f"E_PYTHON_DISPATCH_GIT_MODE: Python tool is not a real file: {relative.as_posix()}",
        )
        tool_bindings.append(
            {
                "git_mode": git_mode,
                "path": relative.as_posix(),
                "status": "bound",
                "variable": variable,
            }
        )

    dispatch_bindings = [
        binding
        for binding in bindings
        if binding["binding"] in PYTHON_DISPATCH_CALL_IDS
    ]
    require(
        len(dispatch_bindings) == 7,
        "E_PYTHON_DISPATCH_COMMAND: interpreter dispatch cardinality differs",
    )
    return {
        "call_binding_count": len(dispatch_bindings),
        "call_bindings": dispatch_bindings,
        "direct_command_position_calls": 0,
        "git_modes": git_modes,
        "logical_executable_stream_sha256": executable_stream_sha256,
        "logical_target_command_count": len(target_commands),
        "raw_runner_sha256": runner_sha256,
        "tool_binding_count": len(tool_bindings),
        "tool_bindings": tool_bindings,
        "status": "passed",
    }


def replace_occurrence(
    text: str,
    needle: str,
    replacement: str,
    occurrence: int,
) -> str:
    start = -1
    for _ in range(occurrence + 1):
        start = text.find(needle, start + 1)
        require(start >= 0, "E_PYTHON_DISPATCH_FIXTURE: mutation target is absent")
    return text[:start] + replacement + text[start + len(needle) :]


def rejected_dispatch_mutation(
    root: Path,
    case_id: str,
    mutated: str,
) -> dict[str, Any]:
    validate_bash_syntax(mutated)
    try:
        validate_python_dispatch_portability(root, mutated)
    except NegativeError as exc:
        raw_error = str(exc)
    else:
        raise NegativeError(
            f"E_PYTHON_DISPATCH_FALSE_GREEN: raw seal accepted mutation: {case_id}"
        )
    require(
        raw_error.startswith("E_PYTHON_DISPATCH_RAW:"),
        f"E_PYTHON_DISPATCH_ERROR: raw seal error differs: {case_id}",
    )
    try:
        validate_python_dispatch_portability(
            root,
            mutated,
            enforce_raw_seal=False,
        )
    except NegativeError as exc:
        stream_error = str(exc)
    else:
        raise NegativeError(
            f"E_PYTHON_DISPATCH_FALSE_GREEN: stream seal accepted mutation: {case_id}"
        )
    require(
        stream_error.startswith("E_PYTHON_DISPATCH_STREAM:"),
        f"E_PYTHON_DISPATCH_ERROR: stream seal error differs: {case_id}",
    )
    try:
        validate_python_dispatch_portability(
            root,
            mutated,
            enforce_raw_seal=False,
            enforce_stream_seal=False,
        )
    except NegativeError as exc:
        semantic_error = str(exc)
    else:
        raise NegativeError(
            f"E_PYTHON_DISPATCH_FALSE_GREEN: semantic check accepted mutation: {case_id}"
        )
    require(
        semantic_error.startswith("E_PYTHON_DISPATCH_")
        and not semantic_error.startswith("E_PYTHON_DISPATCH_RAW:")
        and not semantic_error.startswith("E_PYTHON_DISPATCH_STREAM:"),
        f"E_PYTHON_DISPATCH_ERROR: semantic error differs: {case_id}",
    )
    return {
        "case": case_id,
        "raw_seal_error": raw_error,
        "semantic_error": semantic_error,
        "status": "passed",
        "stream_seal_error": stream_error,
    }


def rejected_source_seal_mutation(
    root: Path,
    case_id: str,
    mutated: str,
    stream_must_change: bool,
) -> dict[str, Any]:
    validate_bash_syntax(mutated)
    try:
        validate_python_dispatch_portability(root, mutated)
    except NegativeError as exc:
        raw_error = str(exc)
    else:
        raise NegativeError(
            f"E_PYTHON_DISPATCH_FALSE_GREEN: raw seal accepted mutation: {case_id}"
        )
    require(
        raw_error.startswith("E_PYTHON_DISPATCH_RAW:"),
        f"E_PYTHON_DISPATCH_ERROR: raw seal error differs: {case_id}",
    )

    stream_error: str | None = None
    try:
        validate_python_dispatch_portability(
            root,
            mutated,
            enforce_raw_seal=False,
        )
    except NegativeError as exc:
        stream_error = str(exc)
    if stream_must_change:
        require(
            stream_error is not None
            and stream_error.startswith("E_PYTHON_DISPATCH_STREAM:"),
            f"E_PYTHON_DISPATCH_FALSE_GREEN: stream seal accepted mutation: {case_id}",
        )
        stream_observation = "rejected"
    else:
        require(
            stream_error is None,
            f"E_PYTHON_DISPATCH_ERROR: executable stream unexpectedly changed: {case_id}",
        )
        stream_observation = "unchanged"

    semantic_error: str | None = None
    try:
        validate_python_dispatch_portability(
            root,
            mutated,
            enforce_raw_seal=False,
            enforce_stream_seal=False,
        )
    except NegativeError as exc:
        semantic_error = str(exc)
    return {
        "case": case_id,
        "raw_seal_error": raw_error,
        "semantic_observation": "accepted" if semantic_error is None else "rejected",
        "semantic_observed_error": semantic_error,
        "status": "passed",
        "stream_observation": stream_observation,
        "stream_observed_error": stream_error,
    }


def run_fixture_git(root: Path, arguments: Sequence[str]) -> None:
    completed = subprocess.run(
        ["git", "-C", str(root), *arguments],
        env=fixture_git_environment(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )
    require(
        completed.returncode == 0,
        "E_PYTHON_DISPATCH_FIXTURE: Git fixture command failed",
    )


def create_dispatch_git_fixture(source_root: Path, fixture_root: Path) -> None:
    fixture_root.mkdir(mode=0o700)
    run_fixture_git(fixture_root, ["init", "-q"])
    for relative in (
        REPORT_RUNNER_PATH,
        *(path for _, path in PYTHON_DISPATCH_TOOLS),
    ):
        destination = fixture_root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_root / relative, destination)
        destination.chmod(0o755 if relative == REPORT_RUNNER_PATH else 0o644)
    run_fixture_git(
        fixture_root,
        [
            "add",
            "--",
            REPORT_RUNNER_PATH.as_posix(),
            *(path.as_posix() for _, path in PYTHON_DISPATCH_TOOLS),
        ],
    )


def git_mode_mutation_probes(
    root: Path,
    temporary_root: Path,
) -> list[dict[str, Any]]:
    cases: list[dict[str, Any]] = []
    relative = PYTHON_DISPATCH_TOOLS[0][1]
    for case_id in ("executable-100755", "symlink-120000", "untracked", "missing"):
        fixture = temporary_root / f"python-dispatch-mode-{case_id}"
        create_dispatch_git_fixture(root, fixture)
        destination = fixture / relative
        if case_id == "executable-100755":
            run_fixture_git(
                fixture,
                ["update-index", "--chmod=+x", "--", relative.as_posix()],
            )
        elif case_id == "symlink-120000":
            destination.unlink()
            destination.symlink_to("non-authoritative-target.py")
            run_fixture_git(fixture, ["add", "--", relative.as_posix()])
        else:
            run_fixture_git(
                fixture,
                ["update-index", "--force-remove", "--", relative.as_posix()],
            )
            if case_id == "missing":
                destination.unlink()
        try:
            validate_python_dispatch_portability(fixture)
        except NegativeError as exc:
            observed_error = str(exc)
        else:
            raise NegativeError(
                f"E_PYTHON_DISPATCH_FALSE_GREEN: Git mode mutation accepted: {case_id}"
            )
        require(
            observed_error.startswith("E_PYTHON_DISPATCH_GIT_MODE:"),
            f"E_PYTHON_DISPATCH_ERROR: Git mode mutation error differs: {case_id}",
        )
        cases.append(
            {"case": case_id, "observed_error": observed_error, "status": "passed"}
        )
    return cases


def python_dispatch_portability_probes(
    root: Path,
    temporary_root: Path,
) -> dict[str, Any]:
    runner = root / REPORT_RUNNER_PATH
    try:
        runner_text = runner.read_text(encoding="utf-8", errors="strict")
    except (OSError, UnicodeError) as exc:
        raise NegativeError(
            "E_PYTHON_DISPATCH_SOURCE: cannot read coverage report runner for probes"
        ) from exc

    positive = validate_python_dispatch_portability(root, runner_text)
    mutation_cases: list[dict[str, Any]] = []
    direct_mutations = (
        ("toolchain-replay-pre", 'python3 "$TOOLCHAIN_RECEIPT_TOOL"', '"$TOOLCHAIN_RECEIPT_TOOL"', 0),
        ("exec-verify", 'python3 "$EXEC_TOOL" verify ', '"$EXEC_TOOL" verify ', 0),
        ("contract-validate", 'python3 "$CONTRACT_TOOL"', '"$CONTRACT_TOOL"', 0),
        ("effective-pom-before", 'python3 "$EFFECTIVE_POM_TOOL"', '"$EFFECTIVE_POM_TOOL"', 0),
        ("effective-pom-after", 'python3 "$EFFECTIVE_POM_TOOL"', '"$EFFECTIVE_POM_TOOL"', 1),
        ("toolchain-replay-post", 'python3 "$TOOLCHAIN_RECEIPT_TOOL"', '"$TOOLCHAIN_RECEIPT_TOOL"', 1),
        ("exec-verify-aggregate", 'python3 "$EXEC_TOOL" verify-aggregate', '"$EXEC_TOOL" verify-aggregate', 0),
    )
    for call_id, needle, direct, occurrence in direct_mutations:
        mutated = replace_occurrence(runner_text, needle, direct, occurrence)
        mutation_cases.append(
            rejected_dispatch_mutation(root, f"direct-command-{call_id}", mutated)
        )

    for variable, relative in PYTHON_DISPATCH_TOOLS:
        required = f'{variable}="$SCRIPT_DIR/{relative.name}"'
        mutated = runner_text.replace(
            required,
            f'{variable}="$SCRIPT_DIR/non-authoritative.py"',
            1,
        )
        require(mutated != runner_text, "E_PYTHON_DISPATCH_FIXTURE: binding target absent")
        mutation_cases.append(
            rejected_dispatch_mutation(root, f"target-rebind-{variable.lower()}", mutated)
        )

    canonical_contract = (
        'python3 "$CONTRACT_TOOL" validate-contract --repo-root "$REPO_ROOT"'
    )
    direct_contract = '"$CONTRACT_TOOL" validate-contract --repo-root "$REPO_ROOT"'
    mutation_cases.append(
        rejected_dispatch_mutation(
            root,
            "comment-decoy",
            runner_text.replace(canonical_contract, direct_contract, 1)
            + f"\n# {canonical_contract}\n",
        )
    )
    mutation_cases.append(
        rejected_dispatch_mutation(
            root,
            "operator-comment-decoy",
            runner_text.replace(canonical_contract, direct_contract, 1)
            + f"\n:;# {canonical_contract}\n",
        )
    )
    mutation_cases.append(
        rejected_dispatch_mutation(
            root,
            "heredoc-decoy",
            runner_text.replace(canonical_contract, direct_contract, 1)
            + "\ncat <<'V934_PYTHON_DISPATCH_DECOY' >/dev/null\n"
            + canonical_contract
            + "\nV934_PYTHON_DISPATCH_DECOY\n",
        )
    )
    mutation_cases.append(
        rejected_dispatch_mutation(
            root,
            "dead-scope-decoy",
            runner_text.replace(
                canonical_contract,
                f"if false; then\n  {canonical_contract}\nfi",
                1,
            ),
        )
    )
    mutation_cases.append(
        rejected_dispatch_mutation(
            root,
            "multiline-dead-scope-decoy",
            runner_text.replace(
                canonical_contract,
                f"if false\nthen\n  {canonical_contract}\nfi",
                1,
            ),
        )
    )
    mutation_cases.append(
        rejected_dispatch_mutation(
            root,
            "function-dead-scope-decoy",
            runner_text.replace(
                canonical_contract,
                "v934_dispatch_decoy() {\n"
                f"  {canonical_contract}\n"
                "}",
                1,
            ),
        )
    )

    additive_commands = (
        ("wrapper-command", 'command "$EXEC_TOOL" --help'),
        ("wrapper-env", 'env "$CONTRACT_TOOL" --help'),
        ("wrapper-exec", 'exec "$EFFECTIVE_POM_TOOL" --help'),
        ("wrapper-if", 'if "$TOOLCHAIN_RECEIPT_TOOL" --help; then :; fi'),
        ("wrapper-negation", '! "$EXEC_TOOL" --help'),
        ("wrapper-command-substitution", 'V934_PROBE="$("$CONTRACT_TOOL" --help)"'),
        ("braced-variable-direct", '"${EFFECTIVE_POM_TOOL}" --help'),
        ("unquoted-variable-direct", '$TOOLCHAIN_RECEIPT_TOOL --help'),
    )
    for case_id, command in additive_commands:
        mutation_cases.append(
            rejected_dispatch_mutation(root, case_id, runner_text + f"\n{command}\n")
        )
    for _, relative in PYTHON_DISPATCH_TOOLS:
        literal = f'"$SCRIPT_DIR/{relative.name}" --help'
        mutation_cases.append(
            rejected_dispatch_mutation(
                root,
                f"literal-direct-{relative.stem}",
                runner_text + f"\n{literal}\n",
            )
        )
    spelling_mutations = (
        (
            "quote-spliced-exec-literal",
            '"$SCRIPT_DIR/coverage_"exec_tool.py --help',
        ),
        (
            "quote-spliced-contract-substitution",
            'V934_PROBE="$("$SCRIPT_DIR/coverage_"tool.py --help)"',
        ),
        (
            "indirect-variable-name",
            "V934_NAME=EXEC_''TOOL\n\"${!V934_NAME}\" --help",
        ),
        (
            "split-path-variable",
            'V934_PATH="$SCRIPT_DIR/coverage_"\n"$V934_PATH"exec_tool.py --help',
        ),
    )
    for case_id, commands in spelling_mutations:
        mutation_cases.append(
            rejected_dispatch_mutation(
                root,
                case_id,
                runner_text + f"\n{commands}\n",
            )
        )

    stream_source_mutations = (
        (
            "two-variable-path-concatenation",
            'V934_DISPATCH_PATH="$SCRIPT_DIR/coverage_exec_tool."\n'
            "V934_DISPATCH_EXTENSION=py\n"
            '"$V934_DISPATCH_PATH$V934_DISPATCH_EXTENSION" --help',
        ),
        (
            "printf-v-path-construction",
            "printf -v V934_DISPATCH_PATH '%s%s' "
            '"$SCRIPT_DIR/coverage_exec_tool." py\n'
            '"$V934_DISPATCH_PATH" --help',
        ),
        (
            "path-environment-construction",
            'V934_DISPATCH_PATH="$SCRIPT_DIR/coverage_exec_tool."\n'
            'V934_DISPATCH_PATH="${V934_DISPATCH_PATH}py"\n'
            '"$V934_DISPATCH_PATH" --help',
        ),
        (
            "empty-expansion-extension",
            '"$SCRIPT_DIR/coverage_exec_tool.p${V934_DISPATCH_EMPTY}y" --help',
        ),
        (
            "command-substitution-extension",
            'V934_DISPATCH_EXTENSION="$(printf p)y"\n'
            '"$SCRIPT_DIR/coverage_exec_tool.$V934_DISPATCH_EXTENSION" --help',
        ),
        (
            "ansi-c-path-literal",
            r"V934_DISPATCH_PATH=$'\x2e\x2f\x73\x63\x72\x69\x70\x74\x73\x2f"
            r"\x76\x39\x33\x34\x2f\x73\x74\x65\x70\x34\x2f"
            r"\x63\x6f\x76\x65\x72\x61\x67\x65\x5f\x65\x78\x65\x63"
            r"\x5f\x74\x6f\x6f\x6c\x2e\x70\x79'"
            "\n"
            '"$V934_DISPATCH_PATH" --help',
        ),
        (
            "dynamic-eval-command",
            "V934_DISPATCH_E=e\n"
            "V934_DISPATCH_VAL=val\n"
            "V934_DISPATCH_A='$EXEC_'\n"
            "V934_DISPATCH_B='TOOL --help'\n"
            '"$V934_DISPATCH_E$V934_DISPATCH_VAL" '
            '"$V934_DISPATCH_A$V934_DISPATCH_B"',
        ),
        (
            "dynamic-bash-c-command",
            "V934_DISPATCH_B=b\n"
            "V934_DISPATCH_ASH=ash\n"
            "V934_DISPATCH_A='$EXEC_'\n"
            "V934_DISPATCH_C='TOOL --help'\n"
            '"$V934_DISPATCH_B$V934_DISPATCH_ASH" -c '
            '"$V934_DISPATCH_A$V934_DISPATCH_C"',
        ),
        (
            "quoted-fake-heredoc-hides-direct-call",
            "printf '%s\\n' \"not a heredoc <<'V934_FAKE_HEREDOC'\"\n"
            '"$EXEC_TOOL" --help\n'
            "V934_FAKE_HEREDOC",
        ),
        (
            "multiline-command-substitution-concatenation",
            'V934_DISPATCH_CAPTURE="$(\n'
            '  V934_DISPATCH_PATH="$SCRIPT_DIR/coverage_exec_tool."\n'
            "  V934_DISPATCH_EXTENSION=py\n"
            '  "$V934_DISPATCH_PATH$V934_DISPATCH_EXTENSION" --help\n'
            ')"',
        ),
    )
    source_seal_cases: list[dict[str, Any]] = []
    for case_id, commands in stream_source_mutations:
        source_seal_cases.append(
            rejected_source_seal_mutation(
                root,
                case_id,
                runner_text + f"\n{commands}\n",
                stream_must_change=True,
            )
        )

    inline_python_marker = "import stat\nimport sys\n\n\ndef unique(pairs):"
    inline_python_mutation = (
        "import stat\n"
        "import subprocess\n"
        "import sys\n\n"
        "subprocess.run([sys.argv[7], '--help'], check=True)\n\n"
        "def unique(pairs):"
    )
    mutated_inline_python = runner_text.replace(
        inline_python_marker,
        inline_python_mutation,
        1,
    )
    require(
        mutated_inline_python != runner_text,
        "E_PYTHON_DISPATCH_FIXTURE: inline Python heredoc marker is absent",
    )
    source_seal_cases.append(
        rejected_source_seal_mutation(
            root,
            "inline-python-heredoc-direct-call",
            mutated_inline_python,
            stream_must_change=False,
        )
    )

    mode_mutations = git_mode_mutation_probes(root, temporary_root)
    smoke_root = temporary_root / "python-dispatch-nonexec-smoke"
    smoke_root.mkdir(mode=0o700)
    smoke_cases: list[dict[str, Any]] = []
    for _, relative in PYTHON_DISPATCH_TOOLS:
        source = root / relative
        destination = smoke_root / relative.name
        shutil.copyfile(source, destination)
        destination.chmod(0o644)
        file_mode = stat.S_IMODE(destination.stat().st_mode)
        require(
            file_mode == 0o644,
            f"E_PYTHON_DISPATCH_SMOKE: copied mode differs: {relative.as_posix()}",
        )
        interpreted = subprocess.run(
            ["python3", str(destination), "--help"],
            env=process_environment(),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        require(
            interpreted.returncode == 0,
            f"E_PYTHON_DISPATCH_SMOKE: interpreter failed: {relative.as_posix()}",
        )
        direct_rejected = False
        try:
            subprocess.run(
                [str(destination), "--help"],
                env=process_environment(),
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                check=False,
            )
        except PermissionError:
            direct_rejected = True
        require(
            direct_rejected,
            f"E_PYTHON_DISPATCH_SMOKE: direct execution succeeded: {relative.as_posix()}",
        )
        smoke_cases.append(
            {
                "direct_execution": "permission-denied",
                "file_mode": f"{file_mode:04o}",
                "git_mode": positive["git_modes"][relative.as_posix()],
                "interpreter_execution": "passed",
                "tool": relative.as_posix(),
            }
        )

    return {
        "positive": positive,
        "raw_only_mutation_count": 1,
        "raw_seal_mutation_count": len(mutation_cases) + len(source_seal_cases),
        "semantic_mutation_count": len(mutation_cases),
        "semantic_mutations": mutation_cases,
        "source_seal_mutation_count": len(source_seal_cases),
        "source_seal_mutations": source_seal_cases,
        "stream_seal_mutation_count": len(mutation_cases)
        + len(stream_source_mutations),
        "git_mode_mutation_count": len(mode_mutations),
        "git_mode_mutations": mode_mutations,
        "nonexec_smoke_count": len(smoke_cases),
        "nonexec_smoke": smoke_cases,
        "status": "passed",
    }


def reporter_effective_pom_umask_077_probe(
    root: Path,
    temporary_root: Path,
) -> None:
    tool_path = root / "scripts/v934/step4/reporter_effective_pom_tool.py"
    spec = importlib.util.spec_from_file_location(
        "v934_effective_pom_umask_probe",
        tool_path,
    )
    require(
        spec is not None and spec.loader is not None,
        "E_EFFECTIVE_POM_UMASK: cannot load effective-POM tool",
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    output = temporary_root / "strict-umask-effective-pom-receipt.json"
    payload = {"probe": "strict-umask-public-output"}
    previous_umask = os.umask(0o077)
    try:
        module.atomic_json(output, payload)
    except Exception as exc:  # the imported tool owns the precise error code
        raise NegativeError(
            f"E_EFFECTIVE_POM_UMASK: public receipt publication failed: {exc}"
        ) from exc
    finally:
        os.umask(previous_umask)

    try:
        observed = output.lstat()
        decoded = json.loads(output.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise NegativeError(
            f"E_EFFECTIVE_POM_UMASK: cannot inspect strict-umask receipt: {exc.__class__.__name__}"
        ) from exc
    require(
        stat.S_ISREG(observed.st_mode)
        and not output.is_symlink()
        and stat.S_IMODE(observed.st_mode) == 0o644
        and decoded == payload,
        "E_EFFECTIVE_POM_UMASK: strict umask receipt contract differs",
    )


def strict_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        require(key not in result, f"validator returned duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_constant(value: str) -> None:
    raise NegativeError(f"validator returned non-finite JSON number: {value}")


def parse_validator_json(stdout: str) -> dict[str, Any]:
    lines = stdout.splitlines()
    require(len(lines) == 1 and lines[0], "validator must return exactly one non-empty JSON line")
    try:
        value = json.loads(
            lines[0],
            object_pairs_hook=strict_object,
            parse_constant=reject_constant,
        )
    except NegativeError:
        raise
    except json.JSONDecodeError as exc:
        raise NegativeError("validator returned malformed JSON") from exc
    require(type(value) is dict, "validator JSON root is not an object")
    return value


def validate_repo_root(value: Path) -> Path:
    root = value.expanduser().absolute()
    require(root.is_dir() and not root.is_symlink(), "repository root is not a real directory")
    require(root.resolve() == root, "repository root is not canonical")
    try:
        observed = subprocess.check_output(
            ["git", "-C", str(root), "rev-parse", "--show-toplevel"],
            env=fixture_git_environment(),
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise NegativeError("cannot resolve Git repository root") from exc
    require(observed == str(root), "supplied repository root differs from Git worktree root")
    return root


def verify_successor_overlay_binding(root: Path) -> dict[str, Any]:
    """Exercise the exact Step 4 preflight binding before a real run can do so.

    A contract or coverage-validator change has to update the successor
    overlay's dual workflow identity.  The outer runner invokes this overlay
    before it starts test lanes, so the static contract suite must invoke the
    same canonical validator as a positive control.
    """

    contract = root / SUCCESSOR_OVERLAY_CONTRACT_PATH
    tool = root / SUCCESSOR_OVERLAY_TOOL_PATH
    validator = root / VALIDATOR_PATH
    for path in (contract, tool, validator):
        require(
            path.is_file() and not path.is_symlink(),
            f"E_SUCCESSOR_OVERLAY_BINDING: required file is unsafe: {path.name}",
        )
    identities = {
        "coverage_contract_sha256": sha256_file(root / JSON_PATHS["contract"]),
        "coverage_tool_sha256": sha256_file(validator),
        "overlay_contract_sha256": sha256_file(contract),
        "overlay_tool_sha256": sha256_file(tool),
    }
    try:
        completed = subprocess.run(
            [sys.executable, str(tool), "validate"],
            cwd=root,
            env=fixture_git_environment(),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=120,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise NegativeError(
            "E_SUCCESSOR_OVERLAY_BINDING: cannot execute canonical overlay validator"
        ) from exc
    require(
        completed.returncode == 0
        and completed.stderr == ""
        and len(completed.stdout.splitlines()) == 1
        and completed.stdout.startswith("V934_STEP4_SUCCESSOR_OVERLAY ")
        and completed.stdout.rstrip("\n").endswith(" status=passed"),
        "E_SUCCESSOR_OVERLAY_BINDING: canonical overlay validator rejected its bindings",
    )
    require(
        identities
        == {
            "coverage_contract_sha256": sha256_file(root / JSON_PATHS["contract"]),
            "coverage_tool_sha256": sha256_file(validator),
            "overlay_contract_sha256": sha256_file(contract),
            "overlay_tool_sha256": sha256_file(tool),
        },
        "E_SUCCESSOR_OVERLAY_BINDING: binding inputs changed during validation",
    )
    return {"command": "validate", **identities, "status": "passed"}


def only(values: Sequence[ET.Element], label: str) -> ET.Element:
    require(len(values) == 1, f"mutation fixture expected one {label}, found {len(values)}")
    return values[0]


def child_text(element: ET.Element, name: str) -> str:
    child = element.find(f"m:{name}", NS)
    return "" if child is None else (child.text or "").strip()


def profile(project: ET.Element, profile_id: str) -> ET.Element:
    return only(
        [item for item in project.findall("m:profiles/m:profile", NS) if child_text(item, "id") == profile_id],
        f"profile {profile_id}",
    )


def plugin(owner: ET.Element, artifact_id: str) -> ET.Element:
    return only(
        [item for item in owner.findall("m:build/m:plugins/m:plugin", NS) if child_text(item, "artifactId") == artifact_id],
        f"plugin {artifact_id}",
    )


def execution(owner: ET.Element, execution_id: str) -> ET.Element:
    return only(
        [item for item in owner.findall("m:executions/m:execution", NS) if child_text(item, "id") == execution_id],
        f"execution {execution_id}",
    )


def read_tree(path: Path) -> ET.ElementTree:
    try:
        return ET.parse(path)
    except (OSError, ET.ParseError) as exc:
        raise NegativeError(f"cannot parse mutation fixture {path.name}: {exc.__class__.__name__}") from exc


def write_tree(path: Path, tree: ET.ElementTree) -> None:
    ET.register_namespace("", MAVEN_NS)
    temporary = path.with_name(f".{path.name}.mutated.tmp")
    require(not temporary.exists() and not temporary.is_symlink(), "mutation temporary path already exists")
    try:
        tree.write(temporary, encoding="utf-8", xml_declaration=True, short_empty_elements=True)
        os.replace(temporary, path)
    except OSError as exc:
        temporary.unlink(missing_ok=True)
        raise NegativeError(f"cannot write mutated POM: {exc.__class__.__name__}") from exc


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise NegativeError(f"cannot parse mutation fixture {path.name}: {exc.__class__.__name__}") from exc
    require(type(value) is dict, f"mutation fixture is not a JSON object: {path.name}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    temporary = path.with_name(f".{path.name}.mutated.tmp")
    require(not temporary.exists() and not temporary.is_symlink(), "mutation temporary path already exists")
    encoded = (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")
    try:
        descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(encoded)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    except OSError as exc:
        temporary.unlink(missing_ok=True)
        raise NegativeError(f"cannot write mutated JSON: {exc.__class__.__name__}") from exc


def mutate_reporter_excludes(path: Path) -> None:
    tree = read_tree(path)
    root = tree.getroot()
    jacoco = plugin(profile(root, "v934-coverage-report"), "jacoco-maven-plugin")
    config = only(execution(jacoco, "v934-report-aggregate").findall("m:configuration", NS), "reporter report configuration")
    excludes = ET.SubElement(config, q("excludes"))
    ET.SubElement(excludes, q("exclude")).text = "**/ForgedExcludedClass*"
    write_tree(path, tree)


def mutate_reporter_skip(path: Path) -> None:
    tree = read_tree(path)
    root = tree.getroot()
    jacoco = plugin(profile(root, "v934-coverage-report"), "jacoco-maven-plugin")
    config = only(execution(jacoco, "v934-report-aggregate").findall("m:configuration", NS), "reporter report configuration")
    ET.SubElement(config, q("skip")).text = "true"
    write_tree(path, tree)


def mutate_reporter_extra_lifecycle_execution(path: Path) -> None:
    tree = read_tree(path)
    root = tree.getroot()
    jacoco = plugin(profile(root, "v934-coverage-report"), "jacoco-maven-plugin")
    executions = only(jacoco.findall("m:executions", NS), "reporter executions")
    forged = ET.SubElement(executions, q("execution"))
    ET.SubElement(forged, q("id")).text = "forged-lifecycle-check"
    ET.SubElement(forged, q("phase")).text = "verify"
    goals = ET.SubElement(forged, q("goals"))
    ET.SubElement(goals, q("goal")).text = "check"
    write_tree(path, tree)


def mutate_root_jacoco_skip(path: Path) -> None:
    tree = read_tree(path)
    properties = only(tree.getroot().findall("m:properties", NS), "root properties")
    ET.SubElement(properties, q("jacoco.skip")).text = "true"
    write_tree(path, tree)


def mutate_root_active_lifecycle_profile(path: Path) -> None:
    tree = read_tree(path)
    profiles = only(tree.getroot().findall("m:profiles", NS), "root profiles")
    forged = ET.SubElement(profiles, q("profile"))
    ET.SubElement(forged, q("id")).text = "forged-active-lifecycle"
    activation = ET.SubElement(forged, q("activation"))
    ET.SubElement(activation, q("activeByDefault")).text = "true"
    build = ET.SubElement(forged, q("build"))
    plugins = ET.SubElement(build, q("plugins"))
    lifecycle_plugin = ET.SubElement(plugins, q("plugin"))
    ET.SubElement(lifecycle_plugin, q("groupId")).text = "org.apache.maven.plugins"
    ET.SubElement(lifecycle_plugin, q("artifactId")).text = "maven-antrun-plugin"
    executions = ET.SubElement(lifecycle_plugin, q("executions"))
    lifecycle_execution = ET.SubElement(executions, q("execution"))
    ET.SubElement(lifecycle_execution, q("id")).text = "forged-active-validate"
    ET.SubElement(lifecycle_execution, q("phase")).text = "validate"
    goals = ET.SubElement(lifecycle_execution, q("goals"))
    ET.SubElement(goals, q("goal")).text = "run"
    write_tree(path, tree)


def mutate_model_enforcer_skip(path: Path) -> None:
    tree = read_tree(path)
    root = tree.getroot()
    enforcer = plugin(profile(root, "v934-coverage-model-check"), "maven-enforcer-plugin")
    config = only(
        execution(enforcer, "v934-coverage-model-require-external-data").findall("m:configuration", NS),
        "model enforcer configuration",
    )
    ET.SubElement(config, q("skip")).text = "true"
    write_tree(path, tree)


def mutate_model_rule_excludes(path: Path) -> None:
    tree = read_tree(path)
    root = tree.getroot()
    jacoco = plugin(profile(root, "v934-coverage-model-check"), "jacoco-maven-plugin")
    rules = only(
        execution(jacoco, "v934-coverage-model-check").findall("m:configuration/m:rules", NS),
        "model JaCoCo rules",
    )
    first_rule = only(rules.findall("m:rule", NS)[:1], "first model JaCoCo rule")
    excludes = ET.SubElement(first_rule, q("excludes"))
    ET.SubElement(excludes, q("exclude")).text = "com.foggyframework.dataset.db.model.*"
    write_tree(path, tree)


def mutate_model_missing_gate(path: Path) -> None:
    tree = read_tree(path)
    root = tree.getroot()
    profiles = only(root.findall("m:profiles", NS), "model profiles")
    gate = profile(root, "v934-coverage-model-check")
    profiles.remove(gate)
    write_tree(path, tree)


def mutate_contract_formal_while_pending(path: Path) -> None:
    value = read_json(path)
    value["status"] = "formal-ready"
    value["tooling_manifest"]["publication_status"] = "formal-ready"
    write_json(path, value)


def mutate_contract_class_id_scope(path: Path) -> None:
    value = read_json(path)
    value["jacoco"]["class_id_consistency_scope"] = "all-loaded-classes-by-name"
    write_json(path, value)


def mutate_threshold_premature_confirmed(path: Path) -> None:
    value = read_json(path)
    value["status"] = "confirmed"
    write_json(path, value)


def mutate_threshold_pending_observed(path: Path) -> None:
    value = read_json(path)
    value["aggregate_observed"] = {}
    write_json(path, value)


def mutate_threshold_parent_floor_lowering(path: Path) -> None:
    value = read_json(path)
    value["critical_candidate_floor"]["line"] = 0.79
    write_json(path, value)


def mutate_threshold_model_gate_lowering(path: Path) -> None:
    value = read_json(path)
    value["model_inherited_gate"]["bundle"]["line"] = 0.76
    write_json(path, value)


def mutate_contract_expand_formal_allowlist(path: Path) -> None:
    value = read_json(path)
    value["threshold_successor"]["formalization_delta"]["allowed_exact_paths"].append(
        "scripts/v934/step4/coverage_tool.py"
    )
    write_json(path, value)


def mutate_contract_expand_not_applicable(path: Path) -> None:
    value = read_json(path)
    value["threshold_successor"]["reviewed_threshold_policy"][
        "critical_metric_applicability"
    ]["exceptions"].append(
        {
            "fqcn": "example.NotCritical",
            "module": "example-module",
            "metric": "branch",
            "applicability": "not-applicable-zero-total-only",
        }
    )
    write_json(path, value)


def mutate_contract_capsule_retention(path: Path) -> None:
    value = read_json(path)
    value["threshold_successor"]["frozen_diagnostic_capsule"]["retention"][
        "execution_bytes"
    ] = "allowed"
    write_json(path, value)


def mutate_formal_aggregate_lowering(path: Path) -> None:
    value = read_json(path)
    value["aggregate_reviewed_thresholds"]["line"] = {
        "covered": 8,
        "total": 10,
        "fraction": "8/10",
    }
    write_json(path, value)


def mutate_formal_critical_lowering(path: Path) -> None:
    value = read_json(path)
    value["critical_reviewed_thresholds"][0]["line"]["minimum"] = {
        "covered": 8,
        "total": 10,
        "fraction": "8/10",
    }
    write_json(path, value)


def mutate_formal_critical_below_floor(path: Path) -> None:
    value = read_json(path)
    value["critical_reviewed_thresholds"][0]["branch"]["observed"] = {
        "covered": 6,
        "total": 10,
        "fraction": "6/10",
    }
    value["critical_reviewed_thresholds"][0]["branch"]["minimum"] = {
        "covered": 6,
        "total": 10,
        "fraction": "6/10",
    }
    write_json(path, value)


def mutate_formal_fraction_alias(path: Path) -> None:
    value = read_json(path)
    value["aggregate_observed"]["line"]["fraction"] = "90/100"
    write_json(path, value)


def mutate_formal_duplicate_critical(path: Path) -> None:
    value = read_json(path)
    value["critical_reviewed_thresholds"][1]["fqcn"] = value["critical_reviewed_thresholds"][0]["fqcn"]
    value["critical_reviewed_thresholds"][1]["module"] = value["critical_reviewed_thresholds"][0]["module"]
    write_json(path, value)


def mutate_formal_unapproved_not_applicable(path: Path) -> None:
    value = read_json(path)
    value["critical_reviewed_thresholds"][0]["line"] = {
        "applicability": "not-applicable-zero-total-only",
        "observed": {"covered": 0, "total": 0, "fraction": None},
        "minimum": None,
    }
    write_json(path, value)


def mutate_formal_not_applicable_nonzero(path: Path) -> None:
    value = read_json(path)
    row = next(
        candidate
        for candidate in value["critical_reviewed_thresholds"]
        if candidate["fqcn"]
        == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
    )
    row["branch"]["observed"] = fraction(1, 1)
    write_json(path, value)


def mutate_formal_not_applicable_minimum(path: Path) -> None:
    value = read_json(path)
    row = next(
        candidate
        for candidate in value["critical_reviewed_thresholds"]
        if candidate["fqcn"]
        == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
    )
    row["branch"]["minimum"] = {
        "covered": 0,
        "total": 0,
        "fraction": None,
    }
    write_json(path, value)


def mutate_formal_not_applicable_bool_zero(path: Path) -> None:
    value = read_json(path)
    row = next(
        candidate
        for candidate in value["critical_reviewed_thresholds"]
        if candidate["fqcn"]
        == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
    )
    row["branch"]["observed"] = {
        "covered": False,
        "total": False,
        "fraction": None,
    }
    write_json(path, value)


def mutate_formal_not_applicable_float_zero(path: Path) -> None:
    value = read_json(path)
    row = next(
        candidate
        for candidate in value["critical_reviewed_thresholds"]
        if candidate["fqcn"]
        == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
    )
    row["branch"]["observed"] = {
        "covered": 0.0,
        "total": 0.0,
        "fraction": None,
    }
    write_json(path, value)


def mutate_formal_contract_diagnostic(path: Path) -> None:
    value = read_json(path)
    value["status"] = "diagnostic-ready"
    value["tooling_manifest"]["publication_status"] = "diagnostic-ready"
    write_json(path, value)


@dataclass(frozen=True)
class Probe:
    probe_id: str
    target: str
    mutation: str
    expected_error_contains: str
    mutate: Callable[[Path], None]
    baseline: str = "diagnostic"


PROBES = (
    Probe(
        "reporter-excludes",
        "reporter",
        "add report-aggregate excludes",
        "coverage reporter report.configuration: child sequence must be",
        mutate_reporter_excludes,
    ),
    Probe(
        "reporter-skip",
        "reporter",
        "add report-aggregate skip=true",
        "coverage reporter report.configuration: child sequence must be",
        mutate_reporter_skip,
    ),
    Probe(
        "reporter-extra-lifecycle-execution",
        "reporter",
        "add third verify lifecycle execution",
        "coverage reporter POM: expected exactly merge and report-aggregate executions",
        mutate_reporter_extra_lifecycle_execution,
    ),
    Probe(
        "root-jacoco-skip",
        "root",
        "add root jacoco.skip=true property",
        "root POM: jacoco.skip is forbidden",
        mutate_root_jacoco_skip,
    ),
    Probe(
        "root-active-lifecycle-profile",
        "root",
        "add active-by-default profile with validate lifecycle plugin",
        "root POM.profiles: child sequence must be",
        mutate_root_active_lifecycle_profile,
    ),
    Probe(
        "model-enforcer-skip",
        "model",
        "add Step4 model enforcer skip=true",
        "model Step4 coverage enforcer configuration: child sequence must be",
        mutate_model_enforcer_skip,
    ),
    Probe(
        "model-rule-excludes",
        "model",
        "add excludes to Step4 model BUNDLE rule",
        "model Step4 coverage check.rule[BUNDLE]: child sequence must be",
        mutate_model_rule_excludes,
    ),
    Probe(
        "model-missing-gate",
        "model",
        "remove v934-coverage-model-check profile",
        "model POM: legacy and Step4 coverage profiles are required",
        mutate_model_missing_gate,
    ),
    Probe(
        "formal-contract-with-pending-threshold",
        "contract",
        "publish formal contract while threshold remains diagnostic-pending",
        "coverage workflow state: contract/publication/threshold status tuple is forbidden",
        mutate_contract_formal_while_pending,
    ),
    Probe(
        "production-class-id-scope-drift",
        "contract",
        "broaden class-ID consistency from the frozen production universe to all loaded classes",
        "coverage contract.jacoco.class_id_consistency_scope: expected frozen-24-module-production-class-universe",
        mutate_contract_class_id_scope,
    ),
    Probe(
        "premature-confirmed-threshold",
        "thresholds",
        "mark null diagnostic threshold confirmed",
        "coverage thresholds.aggregate_observed: expected object",
        mutate_threshold_premature_confirmed,
    ),
    Probe(
        "pending-threshold-with-observation",
        "thresholds",
        "add observation to diagnostic-pending threshold",
        "coverage thresholds.aggregate_observed: expected null before diagnostic",
        mutate_threshold_pending_observed,
    ),
    Probe(
        "threshold-parent-floor-lowering",
        "thresholds",
        "lower the immutable Step 1 critical line floor",
        "coverage thresholds critical line floor: expected 0.8",
        mutate_threshold_parent_floor_lowering,
    ),
    Probe(
        "threshold-model-gate-lowering",
        "thresholds",
        "lower the inherited model bundle line gate",
        "coverage thresholds model bundle line: expected 0.77",
        mutate_threshold_model_gate_lowering,
    ),
    Probe(
        "formal-allowlist-expansion",
        "contract",
        "allow coverage tooling changes during formalization",
        "coverage contract.threshold_successor: frozen values changed",
        mutate_contract_expand_formal_allowlist,
    ),
    Probe(
        "not-applicable-policy-expansion",
        "contract",
        "expand the exact structural not-applicable exception set",
        "coverage contract.threshold_successor: frozen values changed",
        mutate_contract_expand_not_applicable,
    ),
    Probe(
        "git-safe-capsule-retention-weakening",
        "contract",
        "allow execution bytes in the frozen diagnostic capsule",
        "coverage contract.threshold_successor: frozen values changed",
        mutate_contract_capsule_retention,
    ),
    Probe(
        "formal-aggregate-lowering",
        "thresholds",
        "lower aggregate reviewed line minimum below observed",
        "coverage thresholds aggregate line: reviewed minimum must exactly equal observed counter",
        mutate_formal_aggregate_lowering,
        "formal",
    ),
    Probe(
        "formal-critical-lowering",
        "thresholds",
        "lower one critical reviewed line minimum below observed",
        "minimum must exactly equal observed counter",
        mutate_formal_critical_lowering,
        "formal",
    ),
    Probe(
        "formal-critical-below-floor",
        "thresholds",
        "confirm one critical branch counter below the frozen floor",
        "observed counter is below frozen candidate floor",
        mutate_formal_critical_below_floor,
        "formal",
    ),
    Probe(
        "formal-fraction-alias",
        "thresholds",
        "replace canonical fraction identity with an equivalent alias",
        "fraction: expected canonical covered/total string",
        mutate_formal_fraction_alias,
        "formal",
    ),
    Probe(
        "formal-duplicate-critical",
        "thresholds",
        "duplicate a critical class identity",
        "identity/order differs from Step 1 policy",
        mutate_formal_duplicate_critical,
        "formal",
    ),
    Probe(
        "formal-unapproved-not-applicable",
        "thresholds",
        "mark an undeclared critical line metric as structurally not applicable",
        "not-applicable is not approved by the frozen policy",
        mutate_formal_unapproved_not_applicable,
        "formal",
    ),
    Probe(
        "formal-not-applicable-nonzero",
        "thresholds",
        "give the approved structural N/A metric a nonzero counter",
        "expected canonical not-applicable counter",
        mutate_formal_not_applicable_nonzero,
        "formal",
    ),
    Probe(
        "formal-not-applicable-minimum",
        "thresholds",
        "give the approved structural N/A metric a non-null minimum",
        "expected null for not-applicable metric",
        mutate_formal_not_applicable_minimum,
        "formal",
    ),
    Probe(
        "formal-not-applicable-bool-zero",
        "thresholds",
        "replace the approved structural N/A integer zeros with boolean aliases",
        "expected canonical not-applicable counter",
        mutate_formal_not_applicable_bool_zero,
        "formal",
    ),
    Probe(
        "formal-not-applicable-float-zero",
        "thresholds",
        "replace the approved structural N/A integer zeros with float aliases",
        "expected canonical not-applicable counter",
        mutate_formal_not_applicable_float_zero,
        "formal",
    ),
    Probe(
        "confirmed-threshold-with-diagnostic-contract",
        "contract",
        "downgrade formal contract while threshold remains confirmed",
        "coverage workflow state: contract/publication/threshold status tuple is forbidden",
        mutate_formal_contract_diagnostic,
        "formal",
    ),
)


def validator_command(
    root: Path,
    copies: dict[str, Path],
    *,
    structure_only_negative_fixture: bool = False,
) -> list[str]:
    return [
        sys.executable,
        str(root / VALIDATOR_PATH),
        (
            "validate-contract-structure-only-negative-fixture"
            if structure_only_negative_fixture
            else "validate-contract"
        ),
        "--repo-root",
        str(root),
        "--contract",
        str(copies["contract"]),
        "--thresholds",
        str(copies["thresholds"]),
        "--root-pom",
        str(copies["root"]),
        "--model-pom",
        str(copies["model"]),
        "--reporter-pom",
        str(copies["reporter"]),
    ]


def run_validator(
    root: Path,
    copies: dict[str, Path],
    *,
    structure_only_negative_fixture: bool = False,
    git_overrides: dict[str, str] | None = None,
) -> tuple[int, dict[str, Any]]:
    environment = process_environment()
    if git_overrides is not None:
        environment.update(git_overrides)
    try:
        completed = subprocess.run(
            validator_command(
                root,
                copies,
                structure_only_negative_fixture=structure_only_negative_fixture,
            ),
            cwd=root,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=180,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise NegativeError(f"cannot run coverage validator: {exc.__class__.__name__}") from exc
    require(completed.stderr == "", "coverage validator wrote unexpected stderr")
    return completed.returncode, parse_validator_json(completed.stdout)


def copy_inputs(root: Path, directory: Path) -> dict[str, Path]:
    copies: dict[str, Path] = {}
    for role, relative in INPUT_PATHS.items():
        source = root / relative
        require(source.is_file() and not source.is_symlink(), f"canonical {role} input is missing or symlinked")
        destination = directory / f"{role}{relative.suffix}"
        try:
            shutil.copyfile(source, destination)
        except OSError as exc:
            raise NegativeError(f"cannot copy canonical {role} input: {exc.__class__.__name__}") from exc
        require(destination.is_file() and not destination.is_symlink(), f"temporary {role} input copy is unsafe")
        require(destination.read_bytes() == source.read_bytes(), f"temporary {role} input copy differs")
        copies[role] = destination
    return copies


def fraction(covered: int, total: int) -> dict[str, Any]:
    return {"covered": covered, "total": total, "fraction": f"{covered}/{total}"}


def make_formal_fixture(root: Path, copies: dict[str, Path]) -> None:
    contract = read_json(copies["contract"])
    contract["status"] = "formal-ready"
    contract["tooling_manifest"]["publication_status"] = "formal-ready"
    write_json(copies["contract"], contract)

    step1 = read_json(root / "scripts/v934/coverage-thresholds.json")
    critical_rows = step1.get("critical_classes")
    require(type(critical_rows) is list and len(critical_rows) == 12, "formal fixture requires exact 12 Step 1 critical classes")
    review_path = root / "docs/9.3.4/README.md"
    require(review_path.is_file() and not review_path.is_symlink(), "formal fixture review evidence is missing")
    head = subprocess.check_output(
        ["git", "-C", str(root), "rev-parse", "--verify", "HEAD^{commit}"],
        env=fixture_git_environment(),
        text=True,
    ).strip()
    hashes = {
        name: (DIAGNOSTIC_THRESHOLD_SHA256 if name == "threshold_predecessor_sha256" else "1" * 64)
        for name in (
            "source_sha256",
            "run_status_sha256",
            "summary_sha256",
            "observation_sha256",
            "coverage_contract_sha256",
            "threshold_predecessor_sha256",
            "exec_manifest_sha256",
            "aggregate_exec_sha256",
            "aggregate_xml_sha256",
            "workspace_class_tree_sha256",
        )
    }
    thresholds = read_json(copies["thresholds"])
    thresholds["status"] = "confirmed"
    thresholds["aggregate_observed"] = {
        "evidence": {"run_id": "formal-negative-fixture", "git_head": head, **hashes},
        "line": fraction(9, 10),
        "branch": fraction(8, 10),
    }
    thresholds["aggregate_reviewed_thresholds"] = {
        "line": fraction(9, 10),
        "branch": fraction(8, 10),
    }
    critical_thresholds: list[dict[str, Any]] = []
    for row in critical_rows:
        line_counter = fraction(9, 10)
        branch_not_applicable = (
            row["fqcn"]
            == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
        )
        branch_counter = (
            {"covered": 0, "total": 0, "fraction": None}
            if branch_not_applicable
            else fraction(8, 10)
        )
        critical_thresholds.append(
            {
                "fqcn": row["fqcn"],
                "module": row["module"],
                "line": {
                    "applicability": "required-positive-total",
                    "observed": line_counter,
                    "minimum": line_counter,
                },
                "branch": {
                    "applicability": (
                        "not-applicable-zero-total-only"
                        if branch_not_applicable
                        else "required-positive-total"
                    ),
                    "observed": branch_counter,
                    "minimum": None if branch_not_applicable else branch_counter,
                },
            }
        )
    thresholds["critical_reviewed_thresholds"] = critical_thresholds
    thresholds["review"] = {
        "reviewer": "negative-tool-fixture",
        "reviewed_at": "2026-07-16T00:00:00Z",
        "diagnostic_run_id": "formal-negative-fixture",
        "evidence_path": "docs/9.3.4/README.md",
        "evidence_sha256": sha256_file(review_path),
        "decision": "confirm-observed-thresholds",
    }
    write_json(copies["thresholds"], thresholds)


def make_diagnostic_fixture(copies: dict[str, Path]) -> None:
    contract = read_json(copies["contract"])
    contract["status"] = "diagnostic-ready"
    contract["tooling_manifest"]["publication_status"] = "diagnostic-ready"
    write_json(copies["contract"], contract)

    thresholds = read_json(copies["thresholds"])
    thresholds["status"] = "diagnostic-pending"
    thresholds["aggregate_observed"] = None
    thresholds["aggregate_reviewed_thresholds"] = None
    thresholds["critical_reviewed_thresholds"] = None
    thresholds["review"] = {
        "reviewer": None,
        "reviewed_at": None,
        "diagnostic_run_id": None,
        "decision": "pending-all-lane-diagnostic",
    }
    write_json(copies["thresholds"], thresholds)


def make_workflow_fixture(root: Path, copies: dict[str, Path], baseline: str) -> None:
    require(baseline in ("diagnostic", "formal"), f"unsupported workflow fixture: {baseline}")
    if baseline == "diagnostic":
        make_diagnostic_fixture(copies)
    else:
        make_formal_fixture(root, copies)


def run_baseline(root: Path, temporary_root: Path, baseline: str) -> dict[str, Any]:
    directory = temporary_root / f"baseline-{baseline}"
    directory.mkdir(mode=0o700)
    copies = copy_inputs(root, directory)
    canonical_hashes = {role: sha256_file(path) for role, path in copies.items()}
    make_workflow_fixture(root, copies, baseline)
    fixture_hashes = {role: sha256_file(path) for role, path in copies.items()}
    full_return_code, full_payload = run_validator(root, copies)
    require(
        full_return_code == 2
        and full_payload.get("command") == "validate-contract"
        and full_payload.get("status") == "failed"
        and "requires canonical inputs and forbids overrides"
        in str(full_payload.get("error")),
        f"synthetic {baseline} copied-input fixture unexpectedly passed full validation",
    )
    return_code, payload = run_validator(
        root,
        copies,
        structure_only_negative_fixture=True,
    )
    require(return_code == 0, f"{baseline} copied-input baseline failed with rc={return_code}: {payload.get('error')!r}")
    expected_command = "validate-contract-structure-only-negative-fixture"
    require(payload.get("command") == expected_command and payload.get("status") == "passed", f"{baseline} copied-input baseline was not accepted")
    require(payload.get("workflow_state") == baseline, f"{baseline} baseline returned wrong workflow state")
    result = {
        "command": expected_command,
        "canonical_input_sha256": canonical_hashes,
        "fixture_input_sha256": fixture_hashes,
        "return_code": 0,
        "validation_scope": payload.get("validation_scope"),
        "workflow_state": baseline,
        "status": "passed",
    }
    result.update(
        {
            "full_validation_command": "validate-contract",
            "full_validation_error": full_payload.get("error"),
            "full_validation_return_code": full_return_code,
            "full_validation_status": "failed-closed",
        }
    )
    return result


def run_probe(root: Path, temporary_root: Path, probe: Probe) -> dict[str, Any]:
    directory = temporary_root / probe.probe_id
    directory.mkdir(mode=0o700)
    copies = copy_inputs(root, directory)
    make_workflow_fixture(root, copies, probe.baseline)
    before = {role: sha256_file(path) for role, path in copies.items()}
    probe.mutate(copies[probe.target])
    after = {role: sha256_file(path) for role, path in copies.items()}
    require(after[probe.target] != before[probe.target], f"{probe.probe_id}: mutation did not change target POM bytes")
    require(
        all(after[role] == before[role] for role in INPUT_PATHS if role != probe.target),
        f"{probe.probe_id}: mutation changed a non-target input",
    )
    structure_only = True
    return_code, payload = run_validator(
        root,
        copies,
        structure_only_negative_fixture=structure_only,
    )
    require(return_code == 2, f"{probe.probe_id}: validator returned unexpected rc={return_code}")
    expected_command = "validate-contract-structure-only-negative-fixture"
    require(payload.get("command") == expected_command and payload.get("status") == "failed", f"{probe.probe_id}: validator did not return failed status")
    error = payload.get("error")
    require(type(error) is str and probe.expected_error_contains in error, f"{probe.probe_id}: unexpected validator error: {error!r}")
    require(str(temporary_root) not in error, f"{probe.probe_id}: validator error leaks temporary path")
    return {
        "expected_error_contains": probe.expected_error_contains,
        "mutation": probe.mutation,
        "mutated_sha256": after[probe.target],
        "observed_error": error,
        "probe_id": probe.probe_id,
        "baseline": probe.baseline,
        "command": expected_command,
        "return_code": return_code,
        "status": "passed",
        "target": probe.target,
    }


def run_fixture_git(repository: Path, arguments: list[str]) -> subprocess.CompletedProcess[str]:
    environment = fixture_git_environment()
    try:
        completed = subprocess.run(
            ["git", "-C", str(repository), *arguments],
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise NegativeError(f"source-hash Git fixture command failed: {exc.__class__.__name__}") from exc
    require(
        completed.returncode == 0,
        f"source-hash Git fixture command returned rc={completed.returncode}: {arguments[0]}",
    )
    return completed


def run_source_hash_cli(
    root: Path,
    repository: Path,
    *,
    git_overrides: dict[str, str] | None = None,
) -> tuple[int, dict[str, Any]]:
    environment = process_environment()
    if git_overrides is not None:
        environment.update(git_overrides)
    try:
        completed = subprocess.run(
            [
                sys.executable,
                str(root / VALIDATOR_PATH),
                "source-hash",
                "--repo-root",
                str(repository),
            ],
            cwd=root,
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise NegativeError(f"cannot run source-hash Git identity probe: {exc.__class__.__name__}") from exc
    require(completed.stderr == "", "source-hash Git identity probe wrote unexpected stderr")
    return completed.returncode, parse_validator_json(completed.stdout)


def source_hash_git_identity_case(
    root: Path,
    temporary_root: Path,
    case_name: str,
    index_flag: str | None,
) -> dict[str, Any]:
    repository = temporary_root / f"source-hash-{case_name}"
    repository.mkdir(mode=0o700)
    run_fixture_git(repository, ["init", "-q", "--object-format=sha1"])
    run_fixture_git(repository, ["config", "user.name", "v934-negative"])
    run_fixture_git(repository, ["config", "user.email", "v934-negative@example.invalid"])
    tracked = repository / "tracked.txt"
    tracked.write_bytes(b"committed\n")
    tracked.chmod(0o644)
    run_fixture_git(repository, ["add", "--", "tracked.txt"])
    run_fixture_git(repository, ["commit", "-q", "-m", "fixture"])

    if index_flag is None:
        return_code, payload = run_source_hash_cli(root, repository)
        require(
            return_code == 0
            and payload.get("command") == "source-hash"
            and payload.get("file_count") == 1
            and payload.get("status") == "passed",
            f"source-hash normal control failed: {payload}",
        )
        return {
            "case": case_name,
            "expected_return_code": 0,
            "observed_return_code": return_code,
            "status": "passed",
        }

    require(
        index_flag in ("assume-unchanged", "skip-worktree"),
        f"unsupported source-hash index flag fixture: {index_flag}",
    )
    run_fixture_git(repository, ["update-index", f"--{index_flag}", "--", "tracked.txt"])
    tracked.write_bytes(f"hidden-{index_flag}\n".encode("ascii"))
    hidden_status = run_fixture_git(
        repository,
        ["status", "--porcelain=v1", "--untracked-files=all"],
    ).stdout
    require(hidden_status == "", f"{case_name}: Git did not hide the worktree mutation")
    return_code, payload = run_source_hash_cli(root, repository)
    error = payload.get("error")
    require(
        return_code == 2
        and payload.get("command") == "source-hash"
        and payload.get("status") == "failed"
        and isinstance(error, str)
        and "index flags must be ordinary H" in error,
        f"{case_name}: source-hash did not reject the hidden mutation: {payload}",
    )
    return {
        "case": case_name,
        "expected_error_contains": "index flags must be ordinary H",
        "expected_return_code": 2,
        "observed_error": error,
        "observed_return_code": return_code,
        "porcelain_was_empty": True,
        "status": "passed",
    }


def source_hash_git_identity_probes(root: Path, temporary_root: Path) -> dict[str, Any]:
    cases = [
        source_hash_git_identity_case(root, temporary_root, "normal", None),
        source_hash_git_identity_case(
            root,
            temporary_root,
            "assume-unchanged-hidden-mutation",
            "assume-unchanged",
        ),
        source_hash_git_identity_case(
            root,
            temporary_root,
            "skip-worktree-hidden-mutation",
            "skip-worktree",
        ),
    ]
    cases.extend(source_hash_clean_equivalence_probes(root, temporary_root))
    cases.extend(source_hash_worktree_permission_probes(root, temporary_root))
    cases.extend(source_hash_private_group_probes(root))
    cases.extend(source_hash_fsmonitor_probes(root, temporary_root))
    cases.extend(source_hash_git_override_probes(root, temporary_root))
    return {"case_count": len(cases), "cases": cases, "status": "passed"}


def source_hash_clean_equivalence_probes(
    root: Path,
    temporary_root: Path,
) -> list[dict[str, Any]]:
    crlf = temporary_root / "source-hash-crlf-clean-equivalence"
    crlf.mkdir(mode=0o700)
    run_fixture_git(crlf, ["init", "-q", "--object-format=sha1"])
    run_fixture_git(crlf, ["config", "user.name", "v934-negative"])
    run_fixture_git(crlf, ["config", "user.email", "v934-negative@example.invalid"])
    crlf_tracked = crlf / "tracked.txt"
    raw_payload = b"first\r\nsecond\r\n"
    crlf_tracked.write_bytes(raw_payload)
    crlf_tracked.chmod(0o644)
    run_fixture_git(
        crlf, ["-c", "core.autocrlf=input", "add", "--", "tracked.txt"]
    )
    run_fixture_git(crlf, ["commit", "-q", "-m", "crlf-fixture"])
    index_row = run_fixture_git(
        crlf, ["ls-files", "--stage", "--", "tracked.txt"]
    ).stdout
    index_object = index_row.split()[1]
    raw_object = hashlib.sha1(
        f"blob {len(raw_payload)}\0".encode("ascii") + raw_payload
    ).hexdigest()
    run_fixture_git(crlf, ["-c", "core.autocrlf=input", "diff", "--quiet"])
    ambient_config = temporary_root / "ambient-autocrlf.gitconfig"
    ambient_config.write_text(
        "[core]\n\tautocrlf = input\n\tsafecrlf = true\n", encoding="ascii"
    )
    local_config = run_fixture_git(crlf, ["config", "--local", "--list"]).stdout
    require(
        index_row.startswith("100644 ")
        and raw_object != index_object
        and "core.autocrlf" not in local_config,
        "CRLF Git-clean equivalence fixture differs",
    )
    # The repository has no local autocrlf value; the ambient global value is
    # deliberately stripped by the validator, so only its explicit candidate
    # computation can make this pass.
    return_code, payload = run_source_hash_cli(
        root,
        crlf,
        git_overrides={"GIT_CONFIG_GLOBAL": str(ambient_config)},
    )
    require(
        return_code == 0
        and payload.get("command") == "source-hash"
        and payload.get("status") == "passed",
        f"CRLF Git-clean worktree was rejected: {payload}",
    )
    cases: list[dict[str, Any]] = [
        {
            "case": "crlf-git-clean-equivalence",
            "ambient_core_autocrlf": "input-stripped",
            "expected_return_code": 0,
            "git_blob_differs_from_raw_blob": True,
            "observed_return_code": return_code,
            "repository_core_autocrlf": "unset",
            "status": "passed",
        }
    ]

    hostile_local = temporary_root / "source-hash-hostile-local-autocrlf"
    initialize_source_hash_repository(hostile_local, (b"hostile-local-config\n",))
    run_fixture_git(hostile_local, ["config", "core.autocrlf", "true"])
    run_fixture_git(hostile_local, ["config", "core.safecrlf", "true"])
    return_code, payload = run_source_hash_cli(root, hostile_local)
    require(
        return_code == 0 and payload.get("status") == "passed",
        f"explicit clean candidates did not override local autocrlf config: {payload}",
    )
    cases.append(
        {
            "case": "local-autocrlf-safecrlf-config-denied",
            "expected_return_code": 0,
            "local_core_autocrlf": "true",
            "local_core_safecrlf": "true",
            "observed_return_code": return_code,
            "status": "passed",
        }
    )

    filtered = temporary_root / "source-hash-external-filter"
    initialize_source_hash_repository(filtered, (b"external-filter\n",))
    attributes = filtered / ".gitattributes"
    attributes.write_text("tracked.txt filter=v934-negative\n", encoding="ascii")
    run_fixture_git(filtered, ["add", "--", ".gitattributes"])
    run_fixture_git(filtered, ["commit", "-q", "-m", "filter-attribute"])
    filter_hook = filtered / ".git" / "v934-filter-driver"
    filter_hook.write_text(
        '#!/bin/sh\n: > "${0}.invoked"\ncat\n', encoding="ascii"
    )
    filter_hook.chmod(0o700)
    marker = Path(f"{filter_hook}.invoked")
    run_fixture_git(
        filtered, ["config", "filter.v934-negative.clean", str(filter_hook)]
    )
    run_fixture_git(filtered, ["config", "filter.v934-negative.required", "true"])
    return_code, payload = run_source_hash_cli(root, filtered)
    error = payload.get("error")
    require(
        return_code == 2
        and payload.get("command") == "source-hash"
        and payload.get("status") == "failed"
        and isinstance(error, str)
        and "filter attribute must be unspecified or unset" in error
        and not marker.exists(),
        f"external filter was not rejected before execution: {payload}",
    )
    cases.append(
        {
            "case": "external-filter-rejected-before-execution",
            "expected_return_code": 2,
            "filter_hook_invoked": False,
            "observed_error": error,
            "observed_return_code": return_code,
            "status": "passed",
        }
    )

    fifo = temporary_root / "source-hash-tracked-fifo"
    initialize_source_hash_repository(fifo, (b"fifo\n",))
    fifo_tracked = fifo / "tracked.txt"
    fifo_tracked.unlink()
    os.mkfifo(fifo_tracked, 0o644)
    started = time.monotonic()
    return_code, payload = run_source_hash_cli(root, fifo)
    elapsed = time.monotonic() - started
    error = payload.get("error")
    require(
        return_code == 2
        and elapsed < 5
        and isinstance(error, str)
        and "not a canonical regular file" in error,
        f"tracked FIFO did not fail closed promptly: elapsed={elapsed:.3f} payload={payload}",
    )
    cases.append(
        {
            "case": "tracked-fifo-preflight",
            "elapsed_millis": int(elapsed * 1000),
            "expected_return_code": 2,
            "observed_error": error,
            "observed_return_code": return_code,
            "status": "passed",
        }
    )

    concurrent = temporary_root / "source-hash-concurrent-clean-equivalent-rewrite"
    concurrent.mkdir(mode=0o700)
    run_fixture_git(concurrent, ["init", "-q", "--object-format=sha1"])
    run_fixture_git(concurrent, ["config", "user.name", "v934-negative"])
    run_fixture_git(
        concurrent,
        ["config", "user.email", "v934-negative@example.invalid"],
    )
    concurrent_tracked = concurrent / "tracked.txt"
    before_payload = b"first\r\nsecond\r\n"
    after_payload = b"first\nsecond\r\n"
    concurrent_tracked.write_bytes(before_payload)
    concurrent_tracked.chmod(0o644)
    run_fixture_git(
        concurrent,
        ["-c", "core.autocrlf=input", "add", "--", "tracked.txt"],
    )
    run_fixture_git(concurrent, ["commit", "-q", "-m", "concurrent-fixture"])
    expected_object = run_fixture_git(
        concurrent, ["ls-files", "--stage", "--", "tracked.txt"]
    ).stdout.split()[1]
    before_clean_object = run_fixture_git(
        concurrent,
        ["-c", "core.autocrlf=input", "hash-object", "--", "tracked.txt"],
    ).stdout.strip()
    module = load_validator_module(
        root, "v934_coverage_tool_concurrent_clean_equivalent_probe"
    )
    original_capture_git_identity = module.capture_git_identity
    capture_count = 0

    def rewrite_before_second_git_identity(repo_root: Path) -> dict[str, Any]:
        nonlocal capture_count
        capture_count += 1
        if capture_count == 2:
            concurrent_tracked.write_bytes(after_payload)
        return original_capture_git_identity(repo_root)

    module.capture_git_identity = rewrite_before_second_git_identity
    try:
        concurrent_error = expect_validator_contract_error(
            module,
            lambda: module.tracked_source_inventory(concurrent),
            "raw worktree identity changed during audit",
        )
    finally:
        module.capture_git_identity = original_capture_git_identity
    after_clean_object = run_fixture_git(
        concurrent,
        ["-c", "core.autocrlf=input", "hash-object", "--", "tracked.txt"],
    ).stdout.strip()
    require(
        capture_count == 2
        and before_payload != after_payload
        and before_clean_object == expected_object
        and after_clean_object == expected_object,
        "concurrent Git-clean-equivalent rewrite fixture differs",
    )
    cases.append(
        {
            "case": "concurrent-git-clean-equivalent-raw-rewrite",
            "expected_exception": "ContractError",
            "git_clean_blob_stayed_equal_to_head": True,
            "observed_error": concurrent_error,
            "observed_exception": "ContractError",
            "raw_bytes_changed": True,
            "status": "passed",
        }
    )
    return cases


def source_hash_worktree_permission_probes(
    root: Path,
    temporary_root: Path,
) -> list[dict[str, Any]]:
    safe_mismatch = temporary_root / "source-hash-filemode-false-safe-mismatch"
    initialize_source_hash_repository(safe_mismatch, (b"safe-mode-mismatch\n",))
    run_fixture_git(safe_mismatch, ["config", "core.fileMode", "false"])
    safe_tracked = safe_mismatch / "tracked.txt"
    safe_tracked.chmod(0o775)
    safe_index = run_fixture_git(
        safe_mismatch, ["ls-files", "--stage", "--", "tracked.txt"]
    ).stdout
    safe_status = run_fixture_git(
        safe_mismatch,
        ["status", "--porcelain=v1", "--untracked-files=all"],
    ).stdout
    require(
        safe_index.startswith("100644 ")
        and stat.S_IMODE(safe_tracked.stat().st_mode) == 0o775
        and safe_status == "",
        "core.fileMode=false safe permission mismatch fixture differs",
    )
    return_code, payload = run_source_hash_cli(root, safe_mismatch)
    require(
        return_code == 0
        and payload.get("command") == "source-hash"
        and payload.get("file_count") == 1
        and payload.get("status") == "passed",
        f"safe core.fileMode=false permission mismatch was rejected: {payload}",
    )
    cases: list[dict[str, Any]] = [
        {
            "case": "core-filemode-false-safe-permission-mismatch",
            "expected_return_code": 0,
            "git_mode": "100644",
            "observed_return_code": return_code,
            "status": "passed",
            "worktree_mode": "0775",
        }
    ]

    world_writable = temporary_root / "source-hash-world-writable"
    initialize_source_hash_repository(world_writable, (b"world-writable\n",))
    run_fixture_git(world_writable, ["config", "core.fileMode", "false"])
    writable_tracked = world_writable / "tracked.txt"
    writable_tracked.chmod(0o666)
    writable_status = run_fixture_git(
        world_writable,
        ["status", "--porcelain=v1", "--untracked-files=all"],
    ).stdout
    require(
        stat.S_IMODE(writable_tracked.stat().st_mode) == 0o666
        and writable_status == "",
        "world-writable worktree fixture must remain Git-clean",
    )
    return_code, payload = run_source_hash_cli(root, world_writable)
    error = payload.get("error")
    expected_error = "world-writable or has special permission bits"
    require(
        return_code == 2
        and payload.get("command") == "source-hash"
        and payload.get("status") == "failed"
        and isinstance(error, str)
        and expected_error in error,
        f"world-writable tracked source did not fail closed: {payload}",
    )
    cases.append(
        {
            "case": "world-writable-tracked-source",
            "expected_error_contains": expected_error,
            "expected_return_code": 2,
            "git_status_was_clean": True,
            "observed_error": error,
            "observed_return_code": return_code,
            "status": "passed",
            "worktree_mode": "0666",
        }
    )

    executable_index = temporary_root / "source-hash-filemode-false-git-executable"
    initialize_source_hash_repository(executable_index, (b"git-executable\n",))
    executable_tracked = executable_index / "tracked.txt"
    executable_tracked.chmod(0o755)
    run_fixture_git(executable_index, ["add", "--", "tracked.txt"])
    run_fixture_git(executable_index, ["commit", "-q", "--amend", "--no-edit"])
    run_fixture_git(executable_index, ["config", "core.fileMode", "false"])
    executable_tracked.chmod(0o644)
    executable_index_row = run_fixture_git(
        executable_index, ["ls-files", "--stage", "--", "tracked.txt"]
    ).stdout
    executable_status = run_fixture_git(
        executable_index,
        ["status", "--porcelain=v1", "--untracked-files=all"],
    ).stdout
    require(
        executable_index_row.startswith("100755 ") and executable_status == "",
        "Git executable/worktree non-executable fixture differs",
    )
    return_code, payload = run_source_hash_cli(root, executable_index)
    require(
        return_code == 0 and payload.get("status") == "passed",
        f"safe Git100755/worktree0644 mismatch was rejected: {payload}",
    )
    cases.append(
        {
            "case": "core-filemode-false-git-executable-worktree-nonexec",
            "expected_return_code": 0,
            "git_mode": "100755",
            "observed_return_code": return_code,
            "status": "passed",
            "worktree_mode": "0644",
        }
    )

    hardlinked = temporary_root / "source-hash-hardlink"
    initialize_source_hash_repository(hardlinked, (b"hardlink\n",))
    hardlinked_tracked = hardlinked / "tracked.txt"
    os.link(hardlinked_tracked, hardlinked / ".git" / "tracked-hardlink")
    require(hardlinked_tracked.stat().st_nlink == 2, "hardlink fixture link count differs")
    return_code, payload = run_source_hash_cli(root, hardlinked)
    error = payload.get("error")
    require(
        return_code == 2
        and isinstance(error, str)
        and "current euid/egid with one link" in error,
        f"hardlinked tracked source did not fail closed: {payload}",
    )
    cases.append(
        {
            "case": "hardlinked-tracked-source",
            "expected_return_code": 2,
            "observed_error": error,
            "observed_return_code": return_code,
            "status": "passed",
        }
    )

    special = temporary_root / "source-hash-special-bit"
    initialize_source_hash_repository(special, (b"special-bit\n",))
    run_fixture_git(special, ["config", "core.fileMode", "false"])
    special_tracked = special / "tracked.txt"
    special_tracked.chmod(0o4744)
    require(
        special_tracked.stat().st_mode & stat.S_ISUID,
        "special-bit fixture did not retain setuid",
    )
    return_code, payload = run_source_hash_cli(root, special)
    error = payload.get("error")
    require(
        return_code == 2
        and isinstance(error, str)
        and "special permission bits" in error,
        f"special-bit tracked source did not fail closed: {payload}",
    )
    cases.append(
        {
            "case": "special-bit-tracked-source",
            "expected_return_code": 2,
            "observed_error": error,
            "observed_return_code": return_code,
            "status": "passed",
            "worktree_mode": "04744",
        }
    )
    return cases


def load_validator_module(root: Path, module_name: str) -> Any:
    spec = importlib.util.spec_from_file_location(module_name, root / VALIDATOR_PATH)
    require(spec is not None and spec.loader is not None, "cannot load coverage validator")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def expect_validator_contract_error(
    module: Any,
    callback: Callable[[], Any],
    expected_error: str,
) -> str:
    try:
        callback()
    except module.ContractError as exc:
        observed = str(exc)
        require(
            expected_error in observed,
            f"validator error differs: expected={expected_error!r} observed={observed!r}",
        )
        return observed
    raise NegativeError(f"validator accepted forbidden identity: {expected_error}")


def frozen_receipt_payload(
    thresholds_path: Path,
    thresholds: dict[str, Any],
) -> dict[str, Any]:
    aggregate = thresholds["aggregate_observed"]
    evidence = aggregate["evidence"]
    return {
        "schema_version": 1,
        "kind": "v934-step4-frozen-diagnostic-validation",
        "status": "passed",
        "run_id": evidence["run_id"],
        "diagnostic_git_head": evidence["git_head"],
        "current_git_head": evidence["git_head"],
        "ancestor_verified": True,
        "confirmed_threshold_sha256": sha256_file(thresholds_path),
        "frozen_blobs": {
            "threshold": {
                "git_path": "scripts/v934/step4/coverage-thresholds.json",
                "sha256": evidence["threshold_predecessor_sha256"],
                "status": "diagnostic-pending",
            },
            "contract": {
                "git_path": "scripts/v934/step4/coverage-contract.json",
                "sha256": evidence["coverage_contract_sha256"],
                "status": "diagnostic-ready",
            },
        },
        "replay_receipt": {
            "profile": "git-safe-sanitized-attested-v1",
            "capsule_manifest_sha256": "2" * 64,
            "attestation_sha256": "3" * 64,
            "aggregate_xml_sha256": evidence["aggregate_xml_sha256"],
            "execution_attestation": {
                "mode": "source-validated-hash-only",
                "retention": "no-execution-bytes",
                "exec_count": 23,
                "session_count": 48,
                "byte_tree_sha256": "4" * 64,
                "aggregate_exec_sha256": evidence["aggregate_exec_sha256"],
                "merge_semantics": "exact-session-and-jacoco-class-id-probe-bitmap-union",
                "status": "verified",
            },
            "scope": "sanitized-attested-semantic-replay",
            "status": "verified",
        },
        "evidence": copy.deepcopy(evidence),
        "aggregate_observed": copy.deepcopy(aggregate),
        "aggregate_reviewed_thresholds": copy.deepcopy(
            thresholds["aggregate_reviewed_thresholds"]
        ),
        "critical_reviewed_thresholds": copy.deepcopy(
            thresholds["critical_reviewed_thresholds"]
        ),
    }


def invoke_frozen_receipt_validator(
    module: Any,
    root: Path,
    thresholds_path: Path,
    thresholds: dict[str, Any],
    receipt: dict[str, Any],
) -> dict[str, Any]:
    encoded = (
        json.dumps(receipt, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("utf-8")
    original_run = subprocess.run

    def controlled_run(arguments: Any, *args: Any, **kwargs: Any) -> Any:
        if (
            isinstance(arguments, list)
            and len(arguments) >= 3
            and arguments[1]
            == str(root / "scripts/v934/step4/coverage_xml_tool.py")
            and arguments[2] == "validate-frozen-diagnostic"
        ):
            return subprocess.CompletedProcess(
                arguments,
                0,
                stdout=encoded,
                stderr=b"",
            )
        return original_run(arguments, *args, **kwargs)

    subprocess.run = controlled_run
    try:
        return module.validate_frozen_diagnostic_receipt(
            root,
            thresholds_path,
            thresholds,
        )
    finally:
        subprocess.run = original_run


def threshold_and_frozen_replay_probes(
    root: Path,
    temporary_root: Path,
) -> dict[str, Any]:
    module = load_validator_module(root, "v934_coverage_threshold_replay_probe")
    source = (root / VALIDATOR_PATH).read_text(encoding="utf-8")
    tree = ast.parse(source, filename=VALIDATOR_PATH.as_posix())
    validate_all = next(
        node
        for node in tree.body
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
        and node.name == "validate_all"
    )
    replay_calls = [
        node
        for node in ast.walk(validate_all)
        if isinstance(node, ast.Call)
        and isinstance(node.func, ast.Name)
        and node.func.id == "validate_frozen_diagnostic_receipt"
    ]
    require(
        len(replay_calls) == 1,
        "full formal validator must invoke frozen diagnostic replay exactly once",
    )

    cases: list[dict[str, Any]] = [
        {
            "case": "formal-validator-replay-call-bound",
            "observed_call_count": 1,
            "status": "passed",
        }
    ]
    approved = (
        "com.foggyframework.dataset.db.model.spi.NamespaceScope",
        "foggy-dataset-model",
        "branch",
    )
    require(module.critical_metric_allows_not_applicable(*approved), "approved N/A tuple was rejected")
    for case_name, candidate in (
        ("not-applicable-wrong-fqcn", ("example.NamespaceScope", approved[1], approved[2])),
        ("not-applicable-wrong-module", (approved[0], "example-module", approved[2])),
        ("not-applicable-wrong-metric", (approved[0], approved[1], "line")),
    ):
        require(
            not module.critical_metric_allows_not_applicable(*candidate),
            f"{case_name}: partial N/A identity was accepted",
        )
        cases.append({"case": case_name, "status": "passed"})

    for case_name, counter in (
        (
            "not-applicable-bool-zero-policy",
            {"covered": False, "total": False, "fraction": None},
        ),
        (
            "not-applicable-float-zero-policy",
            {"covered": 0.0, "total": 0.0, "fraction": None},
        ),
    ):
        error = expect_validator_contract_error(
            module,
            lambda value=counter: module.validate_not_applicable_fraction_counter(
                value, case_name
            ),
            "expected canonical not-applicable counter",
        )
        cases.append(
            {
                "case": case_name,
                "observed_error": error,
                "observed_exception": "ContractError",
                "status": "passed",
            }
        )

    fixture_directory = temporary_root / "frozen-replay-policy"
    fixture_directory.mkdir(mode=0o700)
    copies = copy_inputs(root, fixture_directory)
    make_formal_fixture(root, copies)
    thresholds_path = copies["thresholds"]
    thresholds = read_json(thresholds_path)
    receipt = frozen_receipt_payload(thresholds_path, thresholds)
    positive = invoke_frozen_receipt_validator(
        module, root, thresholds_path, thresholds, receipt
    )
    require(positive.get("status") == "passed", "canonical frozen receipt was rejected")
    cases.append({"case": "frozen-replay-receipt-positive", "status": "passed"})

    def expect_receipt_failure(
        case_name: str,
        threshold_value: dict[str, Any],
        receipt_value: dict[str, Any],
    ) -> None:
        path = fixture_directory / f"{case_name}.json"
        write_json(path, threshold_value)
        receipt_value["confirmed_threshold_sha256"] = sha256_file(path)
        error = expect_validator_contract_error(
            module,
            lambda: invoke_frozen_receipt_validator(
                module, root, path, threshold_value, receipt_value
            ),
            "frozen diagnostic validator:",
        )
        cases.append(
            {
                "case": case_name,
                "observed_error": error,
                "observed_exception": "ContractError",
                "status": "passed",
            }
        )

    rescaled = copy.deepcopy(receipt)
    rescaled["aggregate_observed"]["line"] = fraction(90, 100)
    rescaled["aggregate_reviewed_thresholds"]["line"] = fraction(90, 100)
    expect_receipt_failure(
        "frozen-replay-denominator-rescale",
        copy.deepcopy(thresholds),
        rescaled,
    )

    actual_required = copy.deepcopy(receipt)
    actual_required_row = next(
        row
        for row in actual_required["critical_reviewed_thresholds"]
        if row["fqcn"]
        == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
    )
    actual_required_row["branch"] = {
        "applicability": "required-positive-total",
        "observed": fraction(8, 10),
        "minimum": fraction(8, 10),
    }
    expect_receipt_failure(
        "frozen-replay-actual-required-reviewed-na",
        copy.deepcopy(thresholds),
        actual_required,
    )

    reviewed_required = copy.deepcopy(thresholds)
    reviewed_required_row = next(
        row
        for row in reviewed_required["critical_reviewed_thresholds"]
        if row["fqcn"]
        == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
    )
    reviewed_required_row["branch"] = {
        "applicability": "required-positive-total",
        "observed": fraction(8, 10),
        "minimum": fraction(8, 10),
    }
    expect_receipt_failure(
        "frozen-replay-actual-na-reviewed-required",
        reviewed_required,
        copy.deepcopy(receipt),
    )

    bool_alias = copy.deepcopy(receipt)
    bool_alias_row = next(
        row
        for row in bool_alias["critical_reviewed_thresholds"]
        if row["fqcn"]
        == "com.foggyframework.dataset.db.model.spi.NamespaceScope"
    )
    bool_alias_row["branch"]["observed"] = {
        "covered": False,
        "total": False,
        "fraction": None,
    }
    expect_receipt_failure(
        "frozen-replay-bool-zero-alias",
        copy.deepcopy(thresholds),
        bool_alias,
    )

    bad_replay_shape = copy.deepcopy(receipt)
    bad_replay_shape["replay_receipt"]["execution_attestation"]["exec_count"] = True
    expect_receipt_failure(
        "frozen-replay-bool-exec-count",
        copy.deepcopy(thresholds),
        bad_replay_shape,
    )
    return {"case_count": len(cases), "cases": cases, "status": "passed"}


def source_hash_private_group_probes(root: Path) -> list[dict[str, Any]]:
    module = load_validator_module(root, "v934_coverage_tool_private_group_probe")
    euid = 41001
    egid = 41002
    current_user = SimpleNamespace(pw_uid=euid, pw_gid=egid, pw_name="v934-current")
    current_group = SimpleNamespace(gr_gid=egid, gr_name="v934-private", gr_mem=())

    shared_error = expect_validator_contract_error(
        module,
        lambda: module.validate_private_primary_group_records(
            euid,
            egid,
            current_user,
            current_group,
            (
                current_user,
                SimpleNamespace(pw_uid=41003, pw_gid=egid, pw_name="v934-foreign"),
            ),
        ),
        "current primary group is shared by another NSS user",
    )
    member_error = expect_validator_contract_error(
        module,
        lambda: module.validate_private_primary_group_records(
            euid,
            egid,
            current_user,
            SimpleNamespace(
                gr_gid=egid,
                gr_name="v934-private",
                gr_mem=("v934-foreign",),
            ),
            (current_user,),
        ),
        "current primary group has another explicit NSS member",
    )
    gid_error = expect_validator_contract_error(
        module,
        lambda: module.validate_tracked_worktree_security(
            SimpleNamespace(
                st_uid=euid,
                st_gid=egid + 1,
                st_nlink=1,
                st_mode=stat.S_IFREG | 0o644,
            ),
            euid,
            egid,
            1,
        ),
        "must use current euid/egid with one link",
    )
    return [
        {
            "case": "shared-primary-gid",
            "expected_exception": "ContractError",
            "observed_error": shared_error,
            "observed_exception": "ContractError",
            "status": "passed",
            "validation_scope": "pure-policy-hook",
        },
        {
            "case": "explicit-foreign-primary-group-member",
            "expected_exception": "ContractError",
            "observed_error": member_error,
            "observed_exception": "ContractError",
            "status": "passed",
            "validation_scope": "pure-policy-hook",
        },
        {
            "case": "tracked-source-foreign-gid",
            "expected_exception": "ContractError",
            "observed_error": gid_error,
            "observed_exception": "ContractError",
            "status": "passed",
            "validation_scope": "pure-policy-hook",
        },
    ]


def source_hash_fsmonitor_probes(
    root: Path,
    temporary_root: Path,
) -> list[dict[str, Any]]:
    configured = temporary_root / "source-hash-fsmonitor-config"
    initialize_source_hash_repository(configured, (b"fsmonitor-config\n",))
    hook = configured / ".git" / "v934-fsmonitor-hook"
    hook.write_text('#!/bin/sh\n: > "${0}.invoked"\nexit 1\n', encoding="ascii")
    hook.chmod(0o700)
    marker = Path(f"{hook}.invoked")
    run_fixture_git(configured, ["config", "core.fsmonitor", str(hook)])
    run_fixture_git(configured, ["config", "core.untrackedCache", "true"])
    return_code, payload = run_source_hash_cli(root, configured)
    require(
        return_code == 0 and payload.get("status") == "passed" and not marker.exists(),
        f"local fsmonitor/untracked-cache config was not disabled: {payload}",
    )
    cases: list[dict[str, Any]] = [
        {
            "case": "local-fsmonitor-untracked-cache-config-disabled",
            "expected_return_code": 0,
            "fsmonitor_hook_invoked": False,
            "observed_return_code": return_code,
            "status": "passed",
        }
    ]

    valid = temporary_root / "source-hash-fsmonitor-valid"
    initialize_source_hash_repository(valid, (b"fsmonitor-valid\n",))
    valid_hook = valid / ".git" / "v934-fsmonitor-hook"
    # Git's fsmonitor hook protocol v2 requires a NUL-terminated response
    # token followed by zero or more NUL-terminated changed paths.  A newline
    # token can make the first flags read transiently mask a persisted valid
    # bit, turning this synthetic precondition into a timing oracle.
    valid_hook.write_text('#!/bin/sh\nprintf "token\\000"\n', encoding="ascii")
    valid_hook.chmod(0o700)
    run_fixture_git(valid, ["config", "core.fsmonitor", str(valid_hook)])
    run_fixture_git(valid, ["update-index", "--fsmonitor"])
    run_fixture_git(valid, ["update-index", "--fsmonitor-valid", "--", "tracked.txt"])
    flags = run_fixture_git(valid, ["ls-files", "-f", "-z"]).stdout
    require(flags == "h tracked.txt\0", "fsmonitor-valid fixture flag differs")
    return_code, payload = run_source_hash_cli(root, valid)
    error = payload.get("error")
    require(
        return_code == 2
        and isinstance(error, str)
        and "fsmonitor index flags must be ordinary H" in error,
        f"fsmonitor-valid index entry did not fail closed: {payload}",
    )
    cases.append(
        {
            "case": "fsmonitor-valid-index-entry",
            "expected_return_code": 2,
            "observed_error": error,
            "observed_return_code": return_code,
            "status": "passed",
        }
    )
    return cases


def initialize_source_hash_repository(
    repository: Path,
    contents: Sequence[bytes],
) -> list[str]:
    repository.mkdir(mode=0o700)
    run_fixture_git(repository, ["init", "-q", "--object-format=sha1"])
    run_fixture_git(repository, ["config", "user.name", "v934-negative"])
    run_fixture_git(
        repository,
        ["config", "user.email", "v934-negative@example.invalid"],
    )
    tracked = repository / "tracked.txt"
    heads: list[str] = []
    for number, payload in enumerate(contents, 1):
        tracked.write_bytes(payload)
        tracked.chmod(0o644)
        run_fixture_git(repository, ["add", "--", "tracked.txt"])
        run_fixture_git(repository, ["commit", "-q", "-m", f"fixture-{number}"])
        heads.append(
            run_fixture_git(
                repository, ["rev-parse", "--verify", "HEAD^{commit}"]
            ).stdout.strip()
        )
    return heads


def require_source_hash_rejection(
    root: Path,
    repository: Path,
    case_name: str,
    expected_error: str,
    overrides: dict[str, str],
) -> dict[str, Any]:
    return_code, payload = run_source_hash_cli(
        root,
        repository,
        git_overrides=overrides,
    )
    error = payload.get("error")
    require(
        return_code == 2
        and payload.get("command") == "source-hash"
        and payload.get("status") == "failed"
        and isinstance(error, str)
        and expected_error in error,
        f"{case_name}: source-hash did not fail closed: {payload}",
    )
    return {
        "case": case_name,
        "expected_error_contains": expected_error,
        "expected_return_code": 2,
        "hostile_git_environment": sorted(overrides),
        "observed_error": error,
        "observed_return_code": return_code,
        "status": "passed",
    }


def source_hash_git_override_probes(
    root: Path,
    temporary_root: Path,
) -> list[dict[str, Any]]:
    missing = temporary_root / "must-not-exist"
    require(not missing.exists() and not missing.is_symlink(), "hostile Git override sentinel exists")

    origin = temporary_root / "source-hash-shallow-origin"
    initialize_source_hash_repository(origin, (b"first\n", b"second\n"))
    shallow = temporary_root / "source-hash-real-shallow"
    run_fixture_git(
        temporary_root,
        [
            "clone",
            "-q",
            "--depth=1",
            "--no-local",
            origin.as_uri(),
            str(shallow),
        ],
    )
    require(
        run_fixture_git(shallow, ["rev-parse", "--is-shallow-repository"]).stdout
        == "true\n",
        "real shallow fixture was not shallow",
    )
    cases = [
        require_source_hash_rejection(
            root,
            shallow,
            "real-shallow-hidden-by-git-shallow-file",
            "shallow repositories are forbidden",
            {"GIT_SHALLOW_FILE": str(missing)},
        )
    ]

    grafted = temporary_root / "source-hash-real-graft"
    graft_head = initialize_source_hash_repository(grafted, (b"grafted\n",))[0]
    common_dir = Path(
        run_fixture_git(
            grafted,
            ["rev-parse", "--path-format=absolute", "--git-common-dir"],
        ).stdout.strip()
    )
    info_dir = common_dir / "info"
    info_dir.mkdir(exist_ok=True)
    (info_dir / "grafts").write_text(f"{graft_head}\n", encoding="ascii")
    cases.append(
        require_source_hash_rejection(
            root,
            grafted,
            "real-graft-hidden-by-git-graft-file",
            "non-empty grafts are forbidden",
            {"GIT_GRAFT_FILE": str(missing)},
        )
    )

    replaced = temporary_root / "source-hash-real-replace"
    replace_heads = initialize_source_hash_repository(
        replaced,
        (b"replace-old\n", b"replace-new\n"),
    )
    run_fixture_git(
        replaced,
        ["update-ref", f"refs/replace/{replace_heads[0]}", replace_heads[1]],
    )
    cases.append(
        require_source_hash_rejection(
            root,
            replaced,
            "real-replace-hidden-by-ref-base",
            "replace refs are forbidden",
            {"GIT_REPLACE_REF_BASE": "refs/v934-hidden-replace"},
        )
    )

    clean = temporary_root / "source-hash-hostile-environment-control"
    initialize_source_hash_repository(clean, (b"clean\n",))
    hostile = {
        "GIT_ALTERNATE_OBJECT_DIRECTORIES": str(missing),
        "GIT_ATTR_NOSYSTEM": "0",
        "GIT_ATTR_SOURCE": "0" * 40,
        "GIT_COMMON_DIR": str(missing),
        "GIT_CONFIG_COUNT": "1",
        "GIT_CONFIG_GLOBAL": str(missing),
        "GIT_CONFIG_KEY_0": "core.repositoryformatversion",
        "GIT_CONFIG_NOSYSTEM": "0",
        "GIT_CONFIG_SYSTEM": str(missing),
        "GIT_CONFIG_VALUE_0": "999",
        "GIT_DIR": str(missing),
        "GIT_EXEC_PATH": str(missing),
        "GIT_GRAFT_FILE": str(missing),
        "GIT_INDEX_FILE": str(missing),
        "GIT_NAMESPACE": "v934-hostile",
        "GIT_OBJECT_DIRECTORY": str(missing),
        "GIT_REPLACE_REF_BASE": "refs/v934-hostile",
        "GIT_SHALLOW_FILE": str(missing),
        "GIT_WORK_TREE": str(missing),
    }
    return_code, payload = run_source_hash_cli(
        root,
        clean,
        git_overrides=hostile,
    )
    require(
        return_code == 0
        and payload.get("command") == "source-hash"
        and payload.get("file_count") == 1
        and payload.get("status") == "passed",
        f"hostile Git environment was not fully ignored: {payload}",
    )
    cases.append(
        {
            "case": "ambient-high-risk-git-overrides-denied",
            "expected_return_code": 0,
            "hostile_git_environment": sorted(hostile),
            "observed_return_code": return_code,
            "status": "passed",
        }
    )
    return cases


def validator_git_environment_policy(root: Path) -> dict[str, Any]:
    spec = importlib.util.spec_from_file_location(
        "v934_coverage_tool_environment_probe",
        root / VALIDATOR_PATH,
    )
    require(spec is not None and spec.loader is not None, "cannot load validator environment policy")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    hostile_names = {
        "GIT_ALTERNATE_OBJECT_DIRECTORIES",
        "GIT_ATTR_NOSYSTEM",
        "GIT_ATTR_SOURCE",
        "GIT_COMMON_DIR",
        "GIT_CONFIG_COUNT",
        "GIT_CONFIG_GLOBAL",
        "GIT_CONFIG_KEY_0",
        "GIT_CONFIG_NOSYSTEM",
        "GIT_CONFIG_SYSTEM",
        "GIT_CONFIG_VALUE_0",
        "GIT_DIR",
        "GIT_EXEC_PATH",
        "GIT_GRAFT_FILE",
        "GIT_INDEX_FILE",
        "GIT_NAMESPACE",
        "GIT_OBJECT_DIRECTORY",
        "GIT_REPLACE_REF_BASE",
        "GIT_SHALLOW_FILE",
        "GIT_WORK_TREE",
    }
    previous = {name: os.environ.get(name) for name in hostile_names}
    try:
        os.environ.update({name: "v934-hostile-sentinel" for name in hostile_names})
        environment = module.git_environment()
    finally:
        for name, value in previous.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
    allowed = {
        "GIT_ATTR_NOSYSTEM": "1",
        "GIT_CONFIG_GLOBAL": os.devnull,
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_NO_REPLACE_OBJECTS": "1",
        "GIT_OPTIONAL_LOCKS": "0",
    }
    observed_git = {
        name: value for name, value in environment.items() if name.startswith("GIT_")
    }
    require(observed_git == allowed, "validator Git environment is not exact deny-by-default")
    require(
        all(name not in environment for name in hostile_names if name not in allowed),
        "validator forwarded an ambient Git override",
    )
    return {
        "allowed_git_environment": allowed,
        "denied_hostile_git_names": sorted(hostile_names - set(allowed)),
        "status": "passed",
    }


def build_result(root: Path) -> dict[str, Any]:
    tool = root / TOOL_PATH
    validator = root / VALIDATOR_PATH
    report_runner = root / REPORT_RUNNER_PATH
    source_hashes = {role: sha256_file(root / relative) for role, relative in INPUT_PATHS.items()}
    tool_hash = sha256_file(tool)
    validator_hash = sha256_file(validator)
    report_runner_hash = sha256_file(report_runner)
    with tempfile.TemporaryDirectory(prefix="v934-step4-coverage-contract-negative-") as temporary_name:
        temporary_root = Path(temporary_name)
        baselines = {
            baseline: run_baseline(root, temporary_root, baseline)
            for baseline in ("diagnostic", "formal")
        }
        cases = [run_probe(root, temporary_root, probe) for probe in PROBES]
        threshold_and_frozen_replay = threshold_and_frozen_replay_probes(
            root, temporary_root
        )
        source_hash_identity = source_hash_git_identity_probes(root, temporary_root)
        git_environment_policy = validator_git_environment_policy(root)
        python_dispatch_portability = python_dispatch_portability_probes(
            root, temporary_root
        )
        reporter_effective_pom_umask_077_probe(root, temporary_root)
        successor_overlay_binding = verify_successor_overlay_binding(root)
    require(
        source_hashes == {role: sha256_file(root / relative) for role, relative in INPUT_PATHS.items()},
        "canonical input changed while running negative probes",
    )
    require(tool_hash == sha256_file(tool), "negative tool changed while running")
    require(validator_hash == sha256_file(validator), "coverage validator changed while running")
    require(
        report_runner_hash == sha256_file(report_runner),
        "coverage report runner changed while running",
    )
    return {
        "schema_version": 1,
        "kind": "v934-step4-coverage-contract-negative",
        "baselines": baselines,
        "inputs": {
            role: {"path": relative.as_posix(), "sha256": source_hashes[role]}
            for role, relative in INPUT_PATHS.items()
        },
        "probe_count": len(cases),
        "probes": cases,
        "git_environment_policy": git_environment_policy,
        "python_dispatch_portability": python_dispatch_portability,
        "successor_overlay_binding": successor_overlay_binding,
        "source_hash_git_identity": source_hash_identity,
        "threshold_and_frozen_replay": threshold_and_frozen_replay,
        "status": "passed",
        "tool": {"path": TOOL_PATH.as_posix(), "sha256": tool_hash},
        "validator": {"path": VALIDATOR_PATH.as_posix(), "sha256": validator_hash},
        "report_runner": {
            "path": REPORT_RUNNER_PATH.as_posix(),
            "sha256": report_runner_hash,
        },
    }


def output_path(root: Path, value: Path) -> Path:
    candidate = value.expanduser()
    if not candidate.is_absolute():
        candidate = root / candidate
    parent = candidate.parent.absolute()
    require(parent.is_dir() and not parent.is_symlink(), "output parent must be an existing real directory")
    require(parent.resolve() == parent, "output parent path contains a symlink or is not canonical")
    candidate = parent / candidate.name
    require(not candidate.exists() and not candidate.is_symlink(), "refusing to overwrite negative JSON output")
    return candidate


def atomic_publish(path: Path, data: bytes) -> None:
    temporary: Path | None = None
    descriptor = -1
    published = False
    try:
        descriptor, name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
        temporary = Path(name)
        os.fchmod(descriptor, 0o644)
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            descriptor = -1
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, path, follow_symlinks=False)
        published = True
        temporary.unlink()
        temporary = None
        directory_fd = os.open(path.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    except FileExistsError as exc:
        raise NegativeError("negative JSON output appeared during publication") from exc
    except OSError as exc:
        if published:
            try:
                path.unlink()
            except OSError:
                pass
        raise NegativeError(f"cannot atomically publish negative JSON: {exc.__class__.__name__}") from exc
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--repo-root", type=Path, required=True)
    result.add_argument("--output", type=Path, required=True, help="new no-clobber JSON evidence path; the top-level runner supplies the canonical run-owned location")
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        root = validate_repo_root(args.repo_root)
        output = output_path(root, args.output)
        result = build_result(root)
        encoded = (json.dumps(result, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n").encode()
        atomic_publish(output, encoded)
        print(encoded.decode().rstrip("\n"))
        return 0
    except (NegativeError, OSError) as exc:
        error = {"kind": "v934-step4-coverage-contract-negative", "status": "failed", "error": str(exc) or exc.__class__.__name__}
        print(json.dumps(error, ensure_ascii=False, sort_keys=True, separators=(",", ":")), file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        error = {"kind": "v934-step4-coverage-contract-negative", "status": "failed", "error": "interrupted"}
        print(json.dumps(error, sort_keys=True, separators=(",", ":")), file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
