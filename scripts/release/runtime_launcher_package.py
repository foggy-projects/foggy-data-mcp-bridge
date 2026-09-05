#!/usr/bin/env python3
"""Build and verify the standard Foggy Runtime Launcher distribution.

The release path always cleans the Console and launcher modules before packaging
their affected reactor, so changed frontend source cannot reuse stale assets.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import io
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from time import monotonic
from typing import Iterable
from xml.etree import ElementTree


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
LAUNCHER_MODULE = REPOSITORY_ROOT / "foggy-mcp-launcher"
LAUNCHER_POM = LAUNCHER_MODULE / "pom.xml"
CONSOLE_POM = REPOSITORY_ROOT / "addons" / "foggy-analytics-console" / "pom.xml"
DIST_SOURCE = LAUNCHER_MODULE / "src" / "main" / "distribution"
SOURCE_REPOSITORY = "https://github.com/foggy-projects/foggy-data-mcp-bridge"
QUESTION_DELIVERY_ROOT = (
    REPOSITORY_ROOT
    / "addons"
    / "foggy-analytics-console"
    / "src"
    / "main"
    / "resources"
    / "fap"
    / "analytics-question-answering"
)
SOURCE_HOST_PUBLICATION_MANIFEST = (
    QUESTION_DELIVERY_ROOT / "host-publication-manifest.json"
)
FAP_DELIVERY_TOOL = Path(__file__).with_name("fap_question_delivery.py")

CONSOLE_JAR_PATTERN = re.compile(r"BOOT-INF/lib/foggy-analytics-console-[^/]+\.jar$")
ANALYTICS_RUNTIME_JAR_PATTERN = re.compile(
    r"BOOT-INF/lib/foggy-analytics-runtime-api-[^/]+\.jar$"
)
ANALYTICS_JAR_PATTERN = re.compile(r"BOOT-INF/lib/foggy-analytics-[^/]+\.jar$")
HTML_REFERENCE_PATTERN = re.compile(r"(?:src|href)=[\"']([^\"']+)[\"']")

def _source_fap_expectations() -> tuple[tuple[str, ...], dict[str, tuple[str, str]]]:
    try:
        manifest = json.loads(
            SOURCE_HOST_PUBLICATION_MANIFEST.read_text(encoding="utf-8")
        )
        functions = manifest["functions"]
        refs = tuple(item["functionRef"] for item in functions)
        digests = {
            item["functionRef"]: (
                item["schemaDigest"],
                item["projectionDigest"],
            )
            for item in functions
        }
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as exc:
        raise RuntimeError(
            "cannot load Analytics question publication expectations from "
            f"{SOURCE_HOST_PUBLICATION_MANIFEST}"
        ) from exc
    return refs, digests


EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY, EXPECTED_FAP_FUNCTION_DIGESTS = (
    _source_fap_expectations()
)
FORBIDDEN_LIVE_MODEL_REVISION_MARKERS = (
    b"expectedModelRevision",
    b"MODEL_REVISION_CONFLICT",
)

REQUIRED_CONSOLE_RESOURCES = (
    "META-INF/foggy-analytics-console/index.html",
    "META-INF/foggy-analytics-console/theme-init.js",
    "fap/analytics-question-answering/SKILL.md",
    "fap/analytics-question-answering/function-schema-delivery.json",
    "fap/analytics-question-answering/skill-metadata.json",
    "fap/analytics-question-answering/references/query-model-dsl.md",
    "fap/analytics-question-answering/references/compose-script.md",
)
CANDIDATE_JAVA_TESTS = (
    "AnalyticsFunctionContractTest",
    "FoggyAnalyticsModelDependencyOperationsTest",
    "FapAnalyticsFunctionRequestMappingTest",
    "FapAnalyticsFunctionCatalogTest",
    "FapAnalyticsFunctionOutcomeMappingTest",
    "AnalyticsConsoleQuestionSkillBundleTest",
    "AnalyticsConsoleFapPublicationControllerTest",
)
CANDIDATE_PYTHON_TESTS = (
    "scripts/release/tests/test_fap_question_delivery.py",
    "scripts/release/tests/test_fap_question_publication_gate.py",
    "scripts/release/tests/test_runtime_launcher_package.py",
)


class ReleaseValidationError(RuntimeError):
    """Raised when an artifact or release source violates the launcher contract."""


@dataclass(frozen=True)
class NestedArtifact:
    path: str
    sha256: str
    bytes: int

    def as_manifest(self) -> dict[str, object]:
        return {"path": self.path, "sha256": self.sha256, "bytes": self.bytes}


@dataclass(frozen=True)
class LauncherEvidence:
    launcher: NestedArtifact
    analytics_console: NestedArtifact
    analytics_runtime_api: NestedArtifact
    console_assets: tuple[str, ...]

    def as_json(self) -> dict[str, object]:
        return {
            "launcher": self.launcher.as_manifest(),
            "analyticsConsole": self.analytics_console.as_manifest(),
            "analyticsRuntimeApi": self.analytics_runtime_api.as_manifest(),
            "consoleAssets": list(self.console_assets),
        }


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _single_matching(names: Iterable[str], pattern: re.Pattern[str], label: str) -> str:
    matches = sorted(name for name in names if pattern.fullmatch(name))
    if len(matches) != 1:
        raise ReleaseValidationError(
            f"expected exactly one embedded {label} JAR, found {len(matches)}: {matches}"
        )
    return matches[0]


def _resolve_console_reference(reference: str) -> str | None:
    reference = html.unescape(reference).split("?", 1)[0].split("#", 1)[0]
    if not reference or reference.startswith(("data:", "http://", "https://", "//")):
        return None
    if reference.startswith("/analytics-console/"):
        relative = reference.removeprefix("/")
        relative = relative.removeprefix("analytics-console/")
        return f"META-INF/foggy-analytics-console/{relative}"
    if reference.startswith("./"):
        reference = reference[2:]
    return str(PurePosixPath("META-INF/foggy-analytics-console") / reference)


def _verify_function_schema_delivery(content: bytes) -> None:
    try:
        delivery = json.loads(content)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ReleaseValidationError(
            "embedded Analytics Function schema delivery is not valid UTF-8 JSON"
        ) from exc

    if not isinstance(delivery, dict):
        raise ReleaseValidationError(
            "embedded Analytics Function schema delivery must be a JSON object"
        )
    if delivery.get("contractVersion") != "fap.langbiz.function-schema-delivery.v1":
        raise ReleaseValidationError(
            "embedded Analytics Function schema delivery has an unsupported contractVersion"
        )
    functions = delivery.get("functions")
    if not isinstance(functions, list):
        raise ReleaseValidationError(
            "embedded Analytics Function schema delivery must contain a functions array"
        )

    actual_refs: list[str] = []
    for index, item in enumerate(functions):
        if not isinstance(item, dict) or set(item) != {"functionRef", "mode"}:
            raise ReleaseValidationError(
                "embedded Analytics Function schema delivery entry "
                f"{index} must contain only functionRef and mode"
            )
        function_ref = item.get("functionRef")
        if not isinstance(function_ref, str):
            raise ReleaseValidationError(
                f"embedded Analytics Function schema delivery entry {index} has no functionRef"
            )
        if item.get("mode") != "INLINE":
            raise ReleaseValidationError(
                f"embedded Analytics Function {function_ref} must use INLINE schema delivery"
            )
        actual_refs.append(function_ref)

    if tuple(actual_refs) != EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY:
        raise ReleaseValidationError(
            "embedded Analytics Function schema delivery must publish the exact governed "
            f"FunctionRef set in canonical order; actual={actual_refs}"
        )


def _verify_skill_metadata(content: bytes) -> tuple[str, int]:
    try:
        metadata = json.loads(content)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ReleaseValidationError(
            "embedded Analytics question Skill metadata is not valid UTF-8 JSON"
        ) from exc
    if not isinstance(metadata, dict):
        raise ReleaseValidationError(
            "embedded Analytics question Skill metadata must be a JSON object"
        )
    name = metadata.get("name")
    revision = metadata.get("revision")
    if name != "analytics-question-answering":
        raise ReleaseValidationError(
            "embedded Analytics question Skill metadata name mismatch"
        )
    if (
        not isinstance(revision, int)
        or isinstance(revision, bool)
        or revision < 1
    ):
        raise ReleaseValidationError(
            "embedded Analytics question Skill metadata revision is invalid"
        )
    return name, revision


def _verify_host_publication_manifest(
        content: bytes, expected_skill: tuple[str, int]) -> None:
    try:
        manifest = json.loads(content)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ReleaseValidationError(
            "embedded Analytics host publication manifest is not valid UTF-8 JSON"
        ) from exc

    if not isinstance(manifest, dict) or set(manifest) != {
        "contractVersion",
        "publicationMode",
        "launcherStartupMutationAllowed",
        "skill",
        "functions",
    }:
        raise ReleaseValidationError(
            "embedded Analytics host publication manifest has unexpected fields"
        )
    if manifest.get("contractVersion") != "foggy.analytics.question-host-publication.v1":
        raise ReleaseValidationError(
            "embedded Analytics host publication manifest has an unsupported contractVersion"
        )
    if manifest.get("publicationMode") != "HOST_MANAGED_EXPLICIT":
        raise ReleaseValidationError(
            "embedded Analytics Function publication must remain host-managed and explicit"
        )
    if manifest.get("launcherStartupMutationAllowed") is not False:
        raise ReleaseValidationError(
            "embedded Analytics host publication manifest must forbid launcher startup mutation"
        )

    skill = manifest.get("skill")
    if (
        not isinstance(skill, dict)
        or set(skill) != {"name", "revision"}
        or skill.get("name") != expected_skill[0]
        or not isinstance(skill.get("revision"), int)
        or isinstance(skill.get("revision"), bool)
        or skill["revision"] != expected_skill[1]
    ):
        raise ReleaseValidationError(
            "embedded Analytics host publication manifest Skill identity does not match "
            "skill-metadata.json"
        )

    functions = manifest.get("functions")
    if not isinstance(functions, list):
        raise ReleaseValidationError(
            "embedded Analytics host publication manifest must contain a functions array"
        )
    actual_refs: list[str] = []
    for index, item in enumerate(functions):
        if not isinstance(item, dict) or set(item) != {
            "functionRef",
            "schemaDigest",
            "projectionDigest",
        }:
            raise ReleaseValidationError(
                "embedded Analytics host publication Function entry "
                f"{index} has unexpected fields"
            )
        function_ref = item.get("functionRef")
        if not isinstance(function_ref, str):
            raise ReleaseValidationError(
                "embedded Analytics host publication Function entry "
                f"{index} has no functionRef"
            )
        actual_refs.append(function_ref)
        expected = EXPECTED_FAP_FUNCTION_DIGESTS.get(function_ref)
        if expected is None or (
            item.get("schemaDigest"), item.get("projectionDigest")
        ) != expected:
            raise ReleaseValidationError(
                "embedded Analytics host publication Function digest mismatch: "
                f"{function_ref}"
            )
    if tuple(actual_refs) != EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY:
        raise ReleaseValidationError(
            "embedded Analytics host publication manifest must contain the exact governed "
            f"FunctionRef set in canonical order; actual={actual_refs}"
        )


def _verify_no_forbidden_live_model_revision_markers(
        content: bytes, artifact_path: str) -> None:
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            for entry in archive.infolist():
                if entry.is_dir():
                    continue
                entry_content = archive.read(entry)
                for marker in FORBIDDEN_LIVE_MODEL_REVISION_MARKERS:
                    if marker in entry_content:
                        raise ReleaseValidationError(
                            "embedded live Analytics artifact contains forbidden caller-visible "
                            f"model version marker {marker.decode('ascii')}: "
                            f"{artifact_path}!/{entry.filename}"
                        )
    except zipfile.BadZipFile as exc:
        raise ReleaseValidationError(
            f"embedded Analytics artifact is not a valid JAR: {artifact_path}"
        ) from exc


def _verify_console_jar(content: bytes, nested_path: str) -> tuple[NestedArtifact, tuple[str, ...]]:
    try:
        with zipfile.ZipFile(io.BytesIO(content)) as archive:
            names = set(archive.namelist())
            missing = sorted(set(REQUIRED_CONSOLE_RESOURCES) - names)
            if missing:
                raise ReleaseValidationError(
                    f"embedded Analytics Console JAR is missing required resources: {missing}"
                )

            index_name = "META-INF/foggy-analytics-console/index.html"
            index = archive.read(index_name).decode("utf-8")
            references = {
                resolved
                for raw in HTML_REFERENCE_PATTERN.findall(index)
                if (resolved := _resolve_console_reference(raw)) is not None
            }
            missing_references = sorted(reference for reference in references if reference not in names)
            if missing_references:
                raise ReleaseValidationError(
                    "Analytics Console index.html references missing packaged assets: "
                    f"{missing_references}"
                )

            index_assets = {
                name
                for name in names
                if name.startswith("META-INF/foggy-analytics-console/assets/index-")
                and name.endswith((".js", ".css"))
            }
            if not any(name.endswith(".js") for name in index_assets):
                raise ReleaseValidationError("Analytics Console has no packaged index JavaScript asset")
            stale_index_assets = sorted(index_assets - references)
            if stale_index_assets:
                raise ReleaseValidationError(
                    "Analytics Console contains stale, unreferenced index assets; release builds must be clean: "
                    f"{stale_index_assets}"
                )

            schema_delivery = archive.read(
                "fap/analytics-question-answering/function-schema-delivery.json"
            )
            _verify_function_schema_delivery(schema_delivery)
            skill_metadata = _verify_skill_metadata(archive.read(
                "fap/analytics-question-answering/skill-metadata.json"
            ))
            host_manifest_name = (
                "fap/analytics-question-answering/host-publication-manifest.json"
            )
            if host_manifest_name in names:
                _verify_host_publication_manifest(
                    archive.read(host_manifest_name), skill_metadata
                )

            skill_content = archive.read(
                "fap/analytics-question-answering/SKILL.md"
            )
            missing_skill_refs = [
                function_ref
                for function_ref in EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY
                if function_ref.encode("utf-8") not in skill_content
            ]
            if missing_skill_refs:
                raise ReleaseValidationError(
                    "embedded Analytics question Skill does not describe every delivered "
                    f"FunctionRef: {missing_skill_refs}"
                )

            packaged_assets = tuple(
                sorted(
                    name
                    for name in names
                    if name.startswith("META-INF/foggy-analytics-console/")
                    and not name.endswith("/")
                )
            )
    except zipfile.BadZipFile as exc:
        raise ReleaseValidationError(
            f"embedded Analytics Console artifact is not a valid JAR: {nested_path}"
        ) from exc

    return (
        NestedArtifact(nested_path, sha256_bytes(content), len(content)),
        packaged_assets,
    )


def verify_launcher_jar(jar_path: Path) -> LauncherEvidence:
    jar_path = jar_path.resolve()
    if not jar_path.is_file():
        raise ReleaseValidationError(f"launcher JAR not found: {jar_path}")

    try:
        with zipfile.ZipFile(jar_path) as launcher:
            names = set(launcher.namelist())
            profile_config = "BOOT-INF/classes/application-analytics-console.yml"
            if profile_config not in names:
                raise ReleaseValidationError(
                    f"standard launcher is missing {profile_config}"
                )

            console_path = _single_matching(
                names, CONSOLE_JAR_PATTERN, "foggy-analytics-console"
            )
            runtime_path = _single_matching(
                names, ANALYTICS_RUNTIME_JAR_PATTERN, "foggy-analytics-runtime-api"
            )
            console_content = launcher.read(console_path)
            runtime_content = launcher.read(runtime_path)
            analytics_artifacts = {
                name: launcher.read(name)
                for name in names
                if ANALYTICS_JAR_PATTERN.fullmatch(name)
            }
    except zipfile.BadZipFile as exc:
        raise ReleaseValidationError(f"not a valid launcher JAR: {jar_path}") from exc

    console, console_assets = _verify_console_jar(console_content, console_path)
    for artifact_path, content in sorted(analytics_artifacts.items()):
        _verify_no_forbidden_live_model_revision_markers(content, artifact_path)
    return LauncherEvidence(
        launcher=NestedArtifact(jar_path.name, sha256_file(jar_path), jar_path.stat().st_size),
        analytics_console=console,
        analytics_runtime_api=NestedArtifact(
            runtime_path, sha256_bytes(runtime_content), len(runtime_content)
        ),
        console_assets=console_assets,
    )


def _pom_direct_dependencies(path: Path) -> set[str]:
    root = ElementTree.parse(path).getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    dependencies = root.find("m:dependencies", namespace)
    if dependencies is None:
        return set()
    result: set[str] = set()
    for dependency in dependencies.findall("m:dependency", namespace):
        group = dependency.findtext("m:groupId", default="", namespaces=namespace)
        artifact = dependency.findtext("m:artifactId", default="", namespaces=namespace)
        result.add(f"{group}:{artifact}")
    return result


def project_version() -> str:
    root = ElementTree.parse(LAUNCHER_POM).getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = root.findtext("m:version", default="", namespaces=namespace)
    if not version:
        version = root.findtext("m:parent/m:version", default="", namespaces=namespace)
    if not version:
        raise ReleaseValidationError("could not resolve foggy-mcp-launcher project version")
    return version.strip()


def verify_release_sources() -> None:
    dependencies = _pom_direct_dependencies(LAUNCHER_POM)
    required = {
        "com.foggysource:foggy-analytics-console",
        "com.foggysource:foggy-analytics-runtime-api",
    }
    missing = sorted(required - dependencies)
    if missing:
        raise ReleaseValidationError(
            "standard launcher POM is missing required direct dependencies: " + ", ".join(missing)
        )

    console_pom = CONSOLE_POM.read_text(encoding="utf-8")
    if "<foggy.analytics-console.frontend.skip>false</foggy.analytics-console.frontend.skip>" not in console_pom:
        raise ReleaseValidationError(
            "Analytics Console frontend must be rebuilt by default during Maven package"
        )

    required_distribution_files = {
        "start-foggy-runtime.sh",
        "start-foggy-runtime.ps1",
        "README-foggy-runtime-launcher.md",
    }
    missing_distribution = sorted(
        name for name in required_distribution_files if not (DIST_SOURCE / name).is_file()
    )
    if missing_distribution:
        raise ReleaseValidationError(
            f"launcher distribution templates are missing: {missing_distribution}"
        )


def maven_build_commands(
        maven_executable: str,
        skip_java_tests: bool,
        candidate: bool = False) -> tuple[list[str], list[str]]:
    clean = [
        maven_executable,
        "-pl",
        "addons/foggy-analytics-console,foggy-mcp-launcher",
        "clean",
    ]
    package = [
        maven_executable,
        "-Pruntime-api",
        "-pl",
        "foggy-mcp-launcher",
        "-am",
        "clean",
        "package",
        "-DskipITs=true",
    ]
    if skip_java_tests:
        package.extend(
            [
                "-Dmaven.test.skip=true",
                "-DskipTests=true",
                "-DskipUnitTests=true",
            ]
        )
    elif candidate:
        package.extend(
            [
                "-Dtest=" + ",".join(CANDIDATE_JAVA_TESTS),
                "-Dsurefire.failIfNoSpecifiedTests=false",
                "-Dsurefire.failIfNoTests=false",
                "-Dfoggy.analytics-console.frontend.test.skip=true",
            ]
        )
    return clean, package


def _git_output(*arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments],
        cwd=REPOSITORY_ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return result.stdout.strip()


def _worktree_dirty() -> bool:
    return bool(_git_output("status", "--porcelain", "--untracked-files=normal"))


def _tracked_worktree_dirty() -> bool:
    return bool(_git_output("status", "--porcelain", "--untracked-files=no"))


def _snapshot_tracked_root_target_files() -> dict[Path, tuple[bytes, int]]:
    """Preserve committed release evidence that Maven's root clean removes."""
    relative_paths = _git_output("ls-files", "--", "target").splitlines()
    snapshot: dict[Path, tuple[bytes, int]] = {}
    for relative_path in relative_paths:
        path = REPOSITORY_ROOT / relative_path
        snapshot[path] = (path.read_bytes(), stat.S_IMODE(path.stat().st_mode))
    return snapshot


def _restore_tracked_root_target_files(
        snapshot: dict[Path, tuple[bytes, int]]) -> None:
    for path, (content, mode) in snapshot.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        os.chmod(path, mode)


def _render_template(source: Path, destination: Path, replacements: dict[str, str]) -> None:
    content = source.read_text(encoding="utf-8")
    for placeholder, value in replacements.items():
        content = content.replace(f"@{placeholder}@", value)
    unresolved = sorted(set(re.findall(r"@[A-Z][A-Z0-9_]+@", content)))
    if unresolved:
        raise ReleaseValidationError(
            f"unresolved placeholders in {source.name}: {unresolved}"
        )
    destination.write_text(content, encoding="utf-8", newline="\n")


def _write_checksums(directory: Path, names: Iterable[str]) -> None:
    lines = [f"{sha256_file(directory / name)}  {name}" for name in names]
    (directory / "SHA256SUMS").write_text("\n".join(lines) + "\n", encoding="utf-8")


def fap_question_delivery_command(maven_executable: str) -> list[str]:
    return [
        sys.executable,
        str(FAP_DELIVERY_TOOL),
        "check",
        "--skip-compile",
        "--maven-executable",
        maven_executable,
    ]


def package_release(args: argparse.Namespace) -> Path:
    verify_release_sources()
    if args.candidate and args.skip_java_tests:
        raise ReleaseValidationError(
            "--candidate runs the focused Java lane and cannot be combined with "
            "--skip-java-tests"
        )
    dirty = _worktree_dirty()
    if dirty and not (args.allow_dirty or args.candidate):
        raise ReleaseValidationError(
            "release worktree is dirty; commit or isolate source changes before packaging"
        )

    output_dir = args.output_dir.resolve()
    if output_dir.exists() and any(output_dir.iterdir()):
        raise ReleaseValidationError(f"output directory must be empty: {output_dir}")
    output_dir.mkdir(parents=True, exist_ok=True)

    stages: list[dict[str, object]] = []

    def run_stage(name: str, command: list[str]) -> None:
        started = monotonic()
        status = "passed"
        try:
            subprocess.run(command, cwd=REPOSITORY_ROOT, check=True)
        except BaseException:
            status = "failed"
            raise
        finally:
            stages.append({
                "name": name,
                "durationMs": round((monotonic() - started) * 1000),
                "status": status,
            })

    tracked_root_target_files = _snapshot_tracked_root_target_files()
    try:
        clean, package = maven_build_commands(
            args.maven_executable, args.skip_java_tests, args.candidate
        )
        run_stage("maven-scoped-clean", clean)
        run_stage("maven-affected-package", package)
    finally:
        _restore_tracked_root_target_files(tracked_root_target_files)
    if not dirty and _tracked_worktree_dirty():
        raise ReleaseValidationError(
            "release build changed tracked source/evidence files; refusing to package"
        )

    if args.candidate:
        run_stage(
            "candidate-python-tests",
            [sys.executable, "-m", "unittest", *CANDIDATE_PYTHON_TESTS],
        )
    run_stage(
        "fap-question-delivery-check",
        fap_question_delivery_command(args.maven_executable),
    )

    java_version = project_version()
    built_jar = LAUNCHER_MODULE / "target" / f"foggy-mcp-launcher-{java_version}.jar"
    verification_started = monotonic()
    evidence = verify_launcher_jar(built_jar)

    release_jar_name = f"foggy-runtime-launcher-{args.release_version}.jar"
    release_jar = output_dir / release_jar_name
    shutil.copy2(built_jar, release_jar)
    release_evidence = verify_launcher_jar(release_jar)
    stages.append({
        "name": "launcher-artifact-verification",
        "durationMs": round((monotonic() - verification_started) * 1000),
        "status": "passed",
    })

    commit = _git_output("rev-parse", "HEAD")
    branch = _git_output("branch", "--show-current") or "detached"
    replacements = {
        "RELEASE_VERSION": args.release_version,
        "JAVA_PROJECT_VERSION": java_version,
        "SOURCE_COMMIT": commit,
    }
    for name in (
        "start-foggy-runtime.sh",
        "start-foggy-runtime.ps1",
        "README-foggy-runtime-launcher.md",
    ):
        _render_template(DIST_SOURCE / name, output_dir / name, replacements)
    os.chmod(output_dir / "start-foggy-runtime.sh", 0o755)

    manifest_name = "runtime-launcher-manifest.json"
    checksum_name = "SHA256SUMS"
    asset_names = [
        release_jar_name,
        "start-foggy-runtime.ps1",
        "start-foggy-runtime.sh",
        "README-foggy-runtime-launcher.md",
        manifest_name,
        checksum_name,
    ]
    validation_mode = "candidate" if args.candidate else (
        "development" if args.skip_java_tests or dirty else "release"
    )
    release_ready = (
        validation_mode == "release" and not dirty and not args.skip_java_tests
    )
    manifest = {
        "schemaVersion": "foggy-runtime-launcher/v1",
        "releaseVersion": args.release_version,
        "launcherName": "foggy-runtime-launcher",
        "javaProjectVersion": java_version,
        "artifactId": "foggy-mcp-launcher",
        "jar": {
            "file": release_jar_name,
            "sha256": release_evidence.launcher.sha256,
            "bytes": release_evidence.launcher.bytes,
        },
        "buildProfile": "runtime-api",
        "runtimeApiProfile": True,
        "defaultProfile": "lite",
        "defaultPort": 18066,
        "securityMode": "none-dev-test-only",
        "features": {
            "analyticsConsole": {
                "embedded": True,
                "enabledByDefault": False,
                "springProfile": "analytics-console",
                "bashOptIn": "ANALYTICS_CONSOLE_ENABLED=true",
                "powershellOptIn": "-AnalyticsConsole",
                "webPath": "/analytics-console/",
                "apiPath": "/analytics-console/api/v1",
                "analyticsRuntimeApiPath": "/analytics/api/v1",
                "fapEnabledByDefault": False,
                "artifact": release_evidence.analytics_console.as_manifest(),
            },
            "analyticsRuntimeApi": {
                "embedded": True,
                "enabledByDefault": False,
                "artifact": release_evidence.analytics_runtime_api.as_manifest(),
            },
        },
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "source": {
            "repository": SOURCE_REPOSITORY,
            "branch": branch,
            "commit": commit,
            "dirty": dirty,
        },
        "validation": {
            "mode": validation_mode,
            "javaTests": (
                "focused-analytics-fap"
                if args.candidate
                else "skipped" if args.skip_java_tests else "affected-reactor"
            ),
            "consoleUnitTests": "skipped" if args.candidate else (
                "skipped" if args.skip_java_tests else "affected-reactor"
            ),
            "consoleTypecheck": "required",
            "consoleProductionBuild": "required",
            "stages": stages,
        },
        "releaseReady": release_ready,
        "assets": asset_names,
    }
    (output_dir / manifest_name).write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    _write_checksums(output_dir, asset_names[:-1])

    print(
        json.dumps(
            {
                "status": "packaged",
                "outputDirectory": str(output_dir),
                "releaseReady": manifest["releaseReady"],
                "validation": manifest["validation"],
                "evidence": evidence.as_json(),
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    return output_dir


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)

    verify = subcommands.add_parser(
        "verify", help="verify an already built standard launcher JAR"
    )
    verify.add_argument("--jar", type=Path, required=True)

    package = subcommands.add_parser(
        "package", help="clean-build and assemble a publishable launcher directory"
    )
    package.add_argument("--release-version", required=True)
    package.add_argument("--output-dir", type=Path, required=True)
    package.add_argument("--maven-executable", default="mvn")
    package.add_argument(
        "--allow-dirty",
        action="store_true",
        help="development-only: package dirty sources and mark releaseReady=false",
    )
    package.add_argument(
        "--skip-java-tests",
        action="store_true",
        help=(
            "development-only: skip Java tests; the Console frontend is still rebuilt, "
            "and the manifest is marked releaseReady=false"
        ),
    )
    package.add_argument(
        "--candidate",
        action="store_true",
        help=(
            "build a dirty-worktree deployment candidate with the focused Analytics/FAP "
            "Java and Python lanes, Console typecheck/build, and releaseReady=false"
        ),
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        if args.command == "verify":
            verify_release_sources()
            print(json.dumps(verify_launcher_jar(args.jar).as_json(), indent=2))
        elif args.command == "package":
            package_release(args)
        else:  # pragma: no cover - argparse enforces the known commands.
            parser.error(f"unsupported command: {args.command}")
    except (ReleaseValidationError, subprocess.CalledProcessError) as exc:
        print(f"runtime launcher release gate failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
