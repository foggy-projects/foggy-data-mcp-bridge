#!/usr/bin/env python3
"""Validate and fingerprint the effective Step 4 coverage reporter POM."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import stat
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Iterable


MAVEN_NS = "http://maven.apache.org/POM/4.0.0"
NS = {"m": MAVEN_NS}
REPORTER_RELATIVE = Path("build-support/foggy-coverage-report")
SHA256_RE = re.compile(r"[0-9a-f]{64}")


class EffectivePomError(RuntimeError):
    """A deterministic effective-POM validation failure."""


def reject(code: str, detail: str) -> None:
    raise EffectivePomError(f"{code}: {detail}")


def require(condition: bool, code: str, detail: str) -> None:
    if not condition:
        reject(code, detail)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    require(path.is_file() and not path.is_symlink(), "E_FILE", f"not a real file: {path}")
    return sha256_bytes(path.read_bytes())


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def text_of(element: ET.Element | None, code: str, label: str) -> str:
    require(element is not None, code, f"missing {label}")
    value = (element.text or "").strip()
    require(value != "", code, f"empty {label}")
    return value


def only(elements: list[ET.Element], code: str, label: str) -> ET.Element:
    require(len(elements) == 1, code, f"expected one {label}, found {len(elements)}")
    return elements[0]


def child_names(element: ET.Element) -> tuple[str, ...]:
    return tuple(local_name(child.tag) for child in element)


def require_children(
    element: ET.Element,
    expected: Iterable[str],
    code: str,
    label: str,
) -> None:
    expected_tuple = tuple(expected)
    require(
        child_names(element) == expected_tuple,
        code,
        f"{label} children changed: expected={expected_tuple} actual={child_names(element)}",
    )


def normalized_node(element: ET.Element, repo_root: Path) -> list[Any]:
    root_text = str(repo_root)

    def normalize(value: str | None) -> str:
        compact = " ".join((value or "").split())
        return compact.replace(root_text, "${repo.root}")

    return [
        local_name(element.tag),
        normalize(element.text),
        sorted((local_name(key), normalize(value)) for key, value in element.attrib.items()),
        [normalized_node(child, repo_root) for child in element],
    ]


def execution_signature(plugin: ET.Element) -> tuple[tuple[str, str, tuple[str, ...]], ...]:
    result: list[tuple[str, str, tuple[str, ...]]] = []
    for execution in plugin.findall("m:executions/m:execution", NS):
        result.append(
            (
                (execution.findtext("m:id", default="", namespaces=NS) or "").strip(),
                (execution.findtext("m:phase", default="", namespaces=NS) or "").strip(),
                tuple(
                    text_of(goal, "E_EXECUTION", "execution goal")
                    for goal in execution.findall("m:goals/m:goal", NS)
                ),
            )
        )
    return tuple(result)


def plugin_coordinate(plugin: ET.Element) -> tuple[str, str, str]:
    group = (plugin.findtext("m:groupId", default="", namespaces=NS) or "").strip()
    artifact = text_of(plugin.find("m:artifactId", NS), "E_PLUGIN", "plugin artifactId")
    version = text_of(plugin.find("m:version", NS), "E_PLUGIN", f"{artifact} version")
    return group or "org.apache.maven.plugins", artifact, version


EXPECTED_EXECUTIONS: dict[tuple[str, str, str], tuple[tuple[str, str, tuple[str, ...]], ...]] = {
    ("org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0"): (),
    ("org.apache.maven.plugins", "maven-surefire-plugin", "3.5.3"): (),
    (
        "org.apache.maven.plugins",
        "maven-failsafe-plugin",
        "3.5.3",
    ): (("", "", ("integration-test", "verify")),),
    (
        "org.apache.maven.plugins",
        "maven-source-plugin",
        "3.3.1",
    ): (("attach-sources", "", ("jar-no-fork",)),),
    (
        "org.jacoco",
        "jacoco-maven-plugin",
        "0.8.12",
    ): (
        ("v934-merge-exec", "generate-resources", ("merge",)),
        ("v934-report-aggregate", "verify", ("report-aggregate",)),
    ),
    (
        "org.apache.maven.plugins",
        "maven-clean-plugin",
        "3.4.1",
    ): (("default-clean", "clean", ("clean",)),),
    (
        "org.apache.maven.plugins",
        "maven-install-plugin",
        "3.1.4",
    ): (("default-install", "install", ("install",)),),
    (
        "org.apache.maven.plugins",
        "maven-deploy-plugin",
        "3.1.4",
    ): (("default-deploy", "deploy", ("deploy",)),),
    (
        "org.apache.maven.plugins",
        "maven-site-plugin",
        "3.3",
    ): (
        ("default-site", "site", ("site",)),
        ("default-deploy", "site-deploy", ("deploy",)),
    ),
}


def validate_jacoco_effective_config(plugin: ET.Element, reporter_dir: Path) -> None:
    executions = {
        text_of(item.find("m:id", NS), "E_JACOCO_CONFIG", "JaCoCo execution id"): item
        for item in plugin.findall("m:executions/m:execution", NS)
    }
    require(
        set(executions) == {"v934-merge-exec", "v934-report-aggregate"},
        "E_JACOCO_CONFIG",
        "effective JaCoCo execution IDs changed",
    )
    target = reporter_dir / "target"
    merge = executions["v934-merge-exec"]
    merge_config = only(
        merge.findall("m:configuration", NS),
        "E_JACOCO_CONFIG",
        "merge configuration",
    )
    require_children(
        merge_config,
        ("fileSets", "destFile"),
        "E_JACOCO_CONFIG",
        "merge configuration",
    )
    file_set = only(
        merge_config.findall("m:fileSets/m:fileSet", NS),
        "E_JACOCO_CONFIG",
        "merge fileSet",
    )
    require_children(
        file_set,
        ("directory", "includes"),
        "E_JACOCO_CONFIG",
        "merge fileSet",
    )
    require(
        text_of(file_set.find("m:directory", NS), "E_JACOCO_CONFIG", "merge directory")
        == str(target / "coverage-input"),
        "E_JACOCO_CONFIG",
        "effective merge input directory changed",
    )
    require(
        tuple(
            text_of(item, "E_JACOCO_CONFIG", "merge include")
            for item in file_set.findall("m:includes/m:include", NS)
        )
        == ("*.exec",),
        "E_JACOCO_CONFIG",
        "effective merge include changed",
    )
    require(
        text_of(merge_config.find("m:destFile", NS), "E_JACOCO_CONFIG", "merge destFile")
        == str(target / "jacoco-aggregate.exec"),
        "E_JACOCO_CONFIG",
        "effective merge destination changed",
    )

    report = executions["v934-report-aggregate"]
    report_config = only(
        report.findall("m:configuration", NS),
        "E_JACOCO_CONFIG",
        "report configuration",
    )
    require_children(
        report_config,
        ("includeCurrentProject", "dataFileIncludes", "outputDirectory", "title"),
        "E_JACOCO_CONFIG",
        "report configuration",
    )
    require(
        text_of(
            report_config.find("m:includeCurrentProject", NS),
            "E_JACOCO_CONFIG",
            "includeCurrentProject",
        )
        == "false",
        "E_JACOCO_CONFIG",
        "effective reporter must exclude its own project",
    )
    require(
        tuple(
            text_of(item, "E_JACOCO_CONFIG", "dataFileInclude")
            for item in report_config.findall("m:dataFileIncludes/m:dataFileInclude", NS)
        )
        == ("target/jacoco-aggregate.exec",),
        "E_JACOCO_CONFIG",
        "effective aggregate data-file include changed",
    )
    require(
        text_of(
            report_config.find("m:outputDirectory", NS),
            "E_JACOCO_CONFIG",
            "report outputDirectory",
        )
        == str(target / "site/jacoco-aggregate"),
        "E_JACOCO_CONFIG",
        "effective report output directory changed",
    )
    require(
        text_of(report_config.find("m:title", NS), "E_JACOCO_CONFIG", "report title")
        == "Foggy 9.3.4 Aggregate Coverage",
        "E_JACOCO_CONFIG",
        "effective report title changed",
    )


def validate_effective_pom(repo_root: Path, path: Path) -> dict[str, Any]:
    require(repo_root.is_dir() and not repo_root.is_symlink(), "E_ROOT", "unsafe repository root")
    require(path.is_absolute(), "E_PATH", "effective POM path must be absolute")
    require(path.is_file() and not path.is_symlink(), "E_PATH", "effective POM must be a real file")
    raw = path.read_bytes()
    require(raw, "E_PATH", "effective POM is empty")
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        raise EffectivePomError("E_XML: malformed effective POM") from exc
    require(root.tag == f"{{{MAVEN_NS}}}project", "E_XML", "unexpected effective POM root")
    require(
        text_of(root.find("m:groupId", NS), "E_IDENTITY", "project groupId")
        == "com.foggysource"
        and text_of(root.find("m:artifactId", NS), "E_IDENTITY", "project artifactId")
        == "foggy-coverage-report"
        and text_of(root.find("m:version", NS), "E_IDENTITY", "project version")
        == "9.1.0.beta"
        and text_of(root.find("m:packaging", NS), "E_IDENTITY", "project packaging")
        == "pom",
        "E_IDENTITY",
        "effective reporter coordinates or packaging changed",
    )

    profiles = root.findall("m:profiles/m:profile", NS)
    profile_ids = tuple(
        text_of(item.find("m:id", NS), "E_PROFILE", "profile id") for item in profiles
    )
    require(
        profile_ids == ("v934-coverage-report",),
        "E_PROFILE",
        f"effective project profile set changed: {profile_ids}",
    )
    require(
        not profiles[0].findall("m:activation", NS),
        "E_PROFILE",
        "reporter profile activation is forbidden",
    )

    plugins = root.findall("m:build/m:plugins/m:plugin", NS)
    actual: dict[tuple[str, str, str], tuple[tuple[str, str, tuple[str, ...]], ...]] = {}
    ordered_coordinates: list[tuple[str, str, str]] = []
    jacoco_plugin: ET.Element | None = None
    compiler_plugin: ET.Element | None = None
    for plugin in plugins:
        coordinate = plugin_coordinate(plugin)
        require(coordinate not in actual, "E_PLUGIN", f"duplicate effective plugin: {coordinate}")
        ordered_coordinates.append(coordinate)
        actual[coordinate] = execution_signature(plugin)
        if coordinate == ("org.jacoco", "jacoco-maven-plugin", "0.8.12"):
            jacoco_plugin = plugin
        if coordinate == (
            "org.apache.maven.plugins",
            "maven-compiler-plugin",
            "3.13.0",
        ):
            compiler_plugin = plugin
    require(
        actual == EXPECTED_EXECUTIONS,
        "E_EXECUTION_SURFACE",
        f"effective plugin execution surface changed: {actual}",
    )
    require(
        tuple(ordered_coordinates) == tuple(EXPECTED_EXECUTIONS),
        "E_EXECUTION_SURFACE",
        "effective plugin order changed",
    )
    require(jacoco_plugin is not None, "E_JACOCO_CONFIG", "effective JaCoCo plugin missing")
    require(compiler_plugin is not None, "E_COMPILER_CONFIG", "effective compiler plugin missing")
    compiler_config = only(
        compiler_plugin.findall("m:configuration", NS),
        "E_COMPILER_CONFIG",
        "compiler configuration",
    )
    require_children(
        compiler_config,
        ("source", "target", "encoding", "parameters"),
        "E_COMPILER_CONFIG",
        "compiler configuration",
    )
    require(
        text_of(compiler_config.find("m:source", NS), "E_COMPILER_CONFIG", "source")
        == "17"
        and text_of(compiler_config.find("m:target", NS), "E_COMPILER_CONFIG", "target")
        == "17"
        and text_of(
            compiler_config.find("m:encoding", NS),
            "E_COMPILER_CONFIG",
            "encoding",
        )
        == "UTF-8"
        and text_of(
            compiler_config.find("m:parameters", NS),
            "E_COMPILER_CONFIG",
            "parameters",
        )
        == "true",
        "E_COMPILER_CONFIG",
        "effective compiler configuration changed",
    )
    validate_jacoco_effective_config(jacoco_plugin, repo_root / REPORTER_RELATIVE)

    normalized = json.dumps(
        normalized_node(root, repo_root),
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    execution_rows = [
        {
            "group_id": group,
            "artifact_id": artifact,
            "version": version,
            "executions": [
                {"id": execution_id, "phase": phase, "goals": list(goals)}
                for execution_id, phase, goals in actual[(group, artifact, version)]
            ],
        }
        for group, artifact, version in ordered_coordinates
    ]
    return {
        "schema_version": 1,
        "kind": "v934-step4-effective-reporter-pom-receipt",
        "validator_sha256": sha256_file(Path(__file__).resolve()),
        "raw_effective_pom_sha256": sha256_bytes(raw),
        "raw_effective_pom_size": len(raw),
        "normalized_effective_pom_sha256": sha256_bytes(normalized),
        "active_project_profiles": ["v934-coverage-report"],
        "build_plugins": execution_rows,
        "status": "verified",
    }


def atomic_json(output: Path, payload: dict[str, Any]) -> None:
    require(output.is_absolute(), "E_OUTPUT", "output path must be absolute")
    require(not output.exists() and not output.is_symlink(), "E_OUTPUT", "refusing overwrite")
    output.parent.mkdir(parents=True, exist_ok=True)
    require(
        output.parent.is_dir() and not output.parent.is_symlink(),
        "E_OUTPUT",
        "unsafe output parent",
    )
    temporary = output.with_name(f".{output.name}.{os.getpid()}.tmp")
    data = (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode("utf-8")
    published = False
    try:
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
            0o600,
        )
        try:
            with os.fdopen(descriptor, "wb") as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
                # Keep the staging inode private while its contents are being
                # written, then publish the contractually public receipt with
                # an explicit mode independent of the caller's umask.
                os.fchmod(stream.fileno(), 0o644)
                os.fsync(stream.fileno())
        except BaseException:
            try:
                os.close(descriptor)
            except OSError:
                pass
            raise
        os.link(temporary, output, follow_symlinks=False)
        published = True
        temporary.unlink()
        directory_fd = os.open(output.parent, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
        mode = stat.S_IMODE(output.stat().st_mode)
        require(mode == 0o644, "E_OUTPUT", f"unexpected output mode: {mode:04o}")
    except BaseException:
        temporary.unlink(missing_ok=True)
        if published:
            output.unlink(missing_ok=True)
        raise


def run_negative_probes(
    repo_root: Path,
    effective_pom: Path,
    baseline: dict[str, Any],
) -> dict[str, Any]:
    cases: list[dict[str, str]] = []

    def probe(
        probe_id: str,
        expected_code: str,
        mutate: Any,
    ) -> None:
        root = ET.parse(effective_pom).getroot()
        mutate(root)
        with tempfile.TemporaryDirectory(prefix="v934-effective-pom-negative-") as temp_text:
            fixture = Path(temp_text) / f"{probe_id}.xml"
            ET.ElementTree(root).write(
                fixture,
                encoding="utf-8",
                xml_declaration=True,
            )
            try:
                validate_effective_pom(repo_root, fixture)
            except EffectivePomError as exc:
                observed_code = str(exc).split(":", 1)[0]
                require(
                    observed_code == expected_code,
                    "E_NEGATIVE_CODE",
                    f"{probe_id}: expected {expected_code}, got {exc}",
                )
            else:
                reject(
                    "E_NEGATIVE_FALSE_GREEN",
                    f"{probe_id}: mutated effective POM was accepted",
                )
        cases.append(
            {
                "probe_id": probe_id,
                "expected_code": expected_code,
                "observed_code": expected_code,
                "status": "passed",
            }
        )

    def add_lifecycle_plugin(root: ET.Element) -> None:
        plugins = only(
            root.findall("m:build/m:plugins", NS),
            "E_NEGATIVE_FIXTURE",
            "build plugins",
        )
        plugin = ET.SubElement(plugins, f"{{{MAVEN_NS}}}plugin")
        ET.SubElement(plugin, f"{{{MAVEN_NS}}}groupId").text = "org.apache.maven.plugins"
        ET.SubElement(plugin, f"{{{MAVEN_NS}}}artifactId").text = "maven-antrun-plugin"
        ET.SubElement(plugin, f"{{{MAVEN_NS}}}version").text = "3.1.0"
        executions = ET.SubElement(plugin, f"{{{MAVEN_NS}}}executions")
        execution = ET.SubElement(executions, f"{{{MAVEN_NS}}}execution")
        ET.SubElement(execution, f"{{{MAVEN_NS}}}id").text = "forge-report"
        ET.SubElement(execution, f"{{{MAVEN_NS}}}phase").text = "verify"
        goals = ET.SubElement(execution, f"{{{MAVEN_NS}}}goals")
        ET.SubElement(goals, f"{{{MAVEN_NS}}}goal").text = "run"

    def add_report_skip(root: ET.Element) -> None:
        executions = root.findall(
            "m:build/m:plugins/m:plugin[m:artifactId='jacoco-maven-plugin']/m:executions/m:execution",
            NS,
        )
        report = only(
            [
                item
                for item in executions
                if (item.findtext("m:id", default="", namespaces=NS) or "").strip()
                == "v934-report-aggregate"
            ],
            "E_NEGATIVE_FIXTURE",
            "report execution",
        )
        config = only(
            report.findall("m:configuration", NS),
            "E_NEGATIVE_FIXTURE",
            "report configuration",
        )
        ET.SubElement(config, f"{{{MAVEN_NS}}}skip").text = "true"

    def add_project_profile(root: ET.Element) -> None:
        profiles = only(
            root.findall("m:profiles", NS),
            "E_NEGATIVE_FIXTURE",
            "project profiles",
        )
        profile = ET.SubElement(profiles, f"{{{MAVEN_NS}}}profile")
        ET.SubElement(profile, f"{{{MAVEN_NS}}}id").text = "unexpected-active-profile"

    def lower_compiler_target(root: ET.Element) -> None:
        targets = root.findall(
            "m:build/m:plugins/m:plugin[m:artifactId='maven-compiler-plugin']"
            "/m:configuration/m:target",
            NS,
        )
        target = only(
            targets,
            "E_NEGATIVE_FIXTURE",
            "compiler target",
        )
        target.text = "11"

    probe("extra-lifecycle-plugin", "E_EXECUTION_SURFACE", add_lifecycle_plugin)
    probe("report-skip", "E_JACOCO_CONFIG", add_report_skip)
    probe("extra-project-profile", "E_PROFILE", add_project_profile)
    probe("lower-compiler-target", "E_COMPILER_CONFIG", lower_compiler_target)
    return {
        "schema_version": 1,
        "kind": "v934-step4-effective-reporter-pom-negative",
        "validator_sha256": sha256_file(Path(__file__).resolve()),
        "baseline_raw_effective_pom_sha256": baseline["raw_effective_pom_sha256"],
        "baseline_normalized_effective_pom_sha256": baseline[
            "normalized_effective_pom_sha256"
        ],
        "case_count": len(cases),
        "cases": cases,
        "status": "passed",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--effective-pom", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--negative-output", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        repo_root = args.repo_root.expanduser().resolve(strict=True)
        effective_pom = args.effective_pom.expanduser()
        if not effective_pom.is_absolute():
            effective_pom = repo_root / effective_pom
        receipt = validate_effective_pom(repo_root, effective_pom)
        if args.output is not None:
            output = args.output.expanduser()
            if not output.is_absolute():
                output = repo_root / output
            atomic_json(output, receipt)
        if args.negative_output is not None:
            negative_output = args.negative_output.expanduser()
            if not negative_output.is_absolute():
                negative_output = repo_root / negative_output
            atomic_json(
                negative_output,
                run_negative_probes(repo_root, effective_pom, receipt),
            )
    except (EffectivePomError, FileNotFoundError, OSError, UnicodeError) as exc:
        print(
            json.dumps(
                {"error": str(exc) or exc.__class__.__name__, "status": "failed"},
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 2
    print(json.dumps(receipt, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
