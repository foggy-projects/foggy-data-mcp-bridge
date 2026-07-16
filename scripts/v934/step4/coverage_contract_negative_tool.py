#!/usr/bin/env python3
"""Run versioned fail-closed mutations against the Step 4 coverage contract.

Every probe copies all three governed POMs to an isolated temporary directory,
mutates exactly one copy, and invokes the public ``validate-contract`` CLI with
all POM override arguments.  Canonical project files are never edited.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Sequence


PREFIX = "[v934-step4-coverage-contract-negative]"
MAVEN_NS = "http://maven.apache.org/POM/4.0.0"
NS = {"m": MAVEN_NS}
TOOL_PATH = Path("scripts/v934/step4/coverage_contract_negative_tool.py")
VALIDATOR_PATH = Path("scripts/v934/step4/coverage_tool.py")
POM_PATHS = {
    "root": Path("pom.xml"),
    "model": Path("foggy-dataset-model/pom.xml"),
    "reporter": Path("build-support/foggy-coverage-report/pom.xml"),
}


class NegativeError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise NegativeError(message)


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
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise NegativeError("cannot resolve Git repository root") from exc
    require(observed == str(root), "supplied repository root differs from Git worktree root")
    return root


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


@dataclass(frozen=True)
class Probe:
    probe_id: str
    target: str
    mutation: str
    expected_error_contains: str
    mutate: Callable[[Path], None]


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
)


def validator_command(root: Path, copies: dict[str, Path]) -> list[str]:
    return [
        sys.executable,
        str(root / VALIDATOR_PATH),
        "validate-contract",
        "--repo-root",
        str(root),
        "--root-pom",
        str(copies["root"]),
        "--model-pom",
        str(copies["model"]),
        "--reporter-pom",
        str(copies["reporter"]),
    ]


def run_validator(root: Path, copies: dict[str, Path]) -> tuple[int, dict[str, Any]]:
    environment = {
        "HOME": os.environ.get("HOME", ""),
        "LANG": "C.UTF-8",
        "LC_ALL": "C.UTF-8",
        "PATH": os.environ.get("PATH", ""),
        "PYTHONDONTWRITEBYTECODE": "1",
    }
    try:
        completed = subprocess.run(
            validator_command(root, copies),
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


def copy_poms(root: Path, directory: Path) -> dict[str, Path]:
    copies: dict[str, Path] = {}
    for role, relative in POM_PATHS.items():
        source = root / relative
        require(source.is_file() and not source.is_symlink(), f"canonical {role} POM is missing or symlinked")
        destination = directory / f"{role}-pom.xml"
        try:
            shutil.copyfile(source, destination)
        except OSError as exc:
            raise NegativeError(f"cannot copy canonical {role} POM: {exc.__class__.__name__}") from exc
        require(destination.is_file() and not destination.is_symlink(), f"temporary {role} POM copy is unsafe")
        require(destination.read_bytes() == source.read_bytes(), f"temporary {role} POM copy differs")
        copies[role] = destination
    return copies


def run_baseline(root: Path, temporary_root: Path) -> dict[str, Any]:
    directory = temporary_root / "baseline"
    directory.mkdir(mode=0o700)
    copies = copy_poms(root, directory)
    return_code, payload = run_validator(root, copies)
    require(return_code == 0, f"unmodified copied POM baseline failed with rc={return_code}")
    require(payload.get("command") == "validate-contract" and payload.get("status") == "passed", "unmodified copied POM baseline was not accepted")
    return {"return_code": 0, "status": "passed"}


def run_probe(root: Path, temporary_root: Path, probe: Probe, source_hashes: dict[str, str]) -> dict[str, Any]:
    directory = temporary_root / probe.probe_id
    directory.mkdir(mode=0o700)
    copies = copy_poms(root, directory)
    before = {role: sha256_file(path) for role, path in copies.items()}
    require(before == source_hashes, f"{probe.probe_id}: copied POM hashes differ from canonical inputs")
    probe.mutate(copies[probe.target])
    after = {role: sha256_file(path) for role, path in copies.items()}
    require(after[probe.target] != before[probe.target], f"{probe.probe_id}: mutation did not change target POM bytes")
    require(
        all(after[role] == before[role] for role in POM_PATHS if role != probe.target),
        f"{probe.probe_id}: mutation changed a non-target POM",
    )
    return_code, payload = run_validator(root, copies)
    require(return_code == 2, f"{probe.probe_id}: validator returned unexpected rc={return_code}")
    require(payload.get("command") == "validate-contract" and payload.get("status") == "failed", f"{probe.probe_id}: validator did not return failed status")
    error = payload.get("error")
    require(type(error) is str and probe.expected_error_contains in error, f"{probe.probe_id}: unexpected validator error: {error!r}")
    require(str(temporary_root) not in error, f"{probe.probe_id}: validator error leaks temporary path")
    return {
        "expected_error_contains": probe.expected_error_contains,
        "mutation": probe.mutation,
        "mutated_sha256": after[probe.target],
        "observed_error": error,
        "probe_id": probe.probe_id,
        "return_code": return_code,
        "status": "passed",
        "target": probe.target,
    }


def build_result(root: Path) -> dict[str, Any]:
    tool = root / TOOL_PATH
    validator = root / VALIDATOR_PATH
    source_hashes = {role: sha256_file(root / relative) for role, relative in POM_PATHS.items()}
    tool_hash = sha256_file(tool)
    validator_hash = sha256_file(validator)
    with tempfile.TemporaryDirectory(prefix="v934-step4-coverage-contract-negative-") as temporary_name:
        temporary_root = Path(temporary_name)
        baseline = run_baseline(root, temporary_root)
        cases = [run_probe(root, temporary_root, probe, source_hashes) for probe in PROBES]
    require(
        source_hashes == {role: sha256_file(root / relative) for role, relative in POM_PATHS.items()},
        "canonical POM changed while running negative probes",
    )
    require(tool_hash == sha256_file(tool), "negative tool changed while running")
    require(validator_hash == sha256_file(validator), "coverage validator changed while running")
    return {
        "schema_version": 1,
        "kind": "v934-step4-coverage-contract-negative",
        "baseline": baseline,
        "inputs": {
            role: {"path": relative.as_posix(), "sha256": source_hashes[role]}
            for role, relative in POM_PATHS.items()
        },
        "probe_count": len(cases),
        "probes": cases,
        "status": "passed",
        "tool": {"path": TOOL_PATH.as_posix(), "sha256": tool_hash},
        "validator": {"path": VALIDATOR_PATH.as_posix(), "sha256": validator_hash},
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
