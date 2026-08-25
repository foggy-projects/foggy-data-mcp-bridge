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
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Iterable
from xml.etree import ElementTree


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
LAUNCHER_MODULE = REPOSITORY_ROOT / "foggy-mcp-launcher"
LAUNCHER_POM = LAUNCHER_MODULE / "pom.xml"
CONSOLE_POM = REPOSITORY_ROOT / "addons" / "foggy-analytics-console" / "pom.xml"
DIST_SOURCE = LAUNCHER_MODULE / "src" / "main" / "distribution"
SOURCE_REPOSITORY = "https://github.com/foggy-projects/foggy-data-mcp-bridge"

CONSOLE_JAR_PATTERN = re.compile(r"BOOT-INF/lib/foggy-analytics-console-[^/]+\.jar$")
ANALYTICS_RUNTIME_JAR_PATTERN = re.compile(
    r"BOOT-INF/lib/foggy-analytics-runtime-api-[^/]+\.jar$"
)
HTML_REFERENCE_PATTERN = re.compile(r"(?:src|href)=[\"']([^\"']+)[\"']")

REQUIRED_CONSOLE_RESOURCES = (
    "META-INF/foggy-analytics-console/index.html",
    "META-INF/foggy-analytics-console/theme-init.js",
    "fap/analytics-question-answering/SKILL.md",
    "fap/analytics-question-answering/function-schema-delivery.json",
    "fap/analytics-question-answering/references/query-model-dsl.md",
    "fap/analytics-question-answering/references/compose-script.md",
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
            try:
                json.loads(schema_delivery)
            except json.JSONDecodeError as exc:
                raise ReleaseValidationError(
                    "embedded Analytics Function schema delivery is not valid JSON"
                ) from exc
            if b"expectedModelRevision" in schema_delivery:
                raise ReleaseValidationError(
                    "embedded Analytics Function schema delivery still exposes expectedModelRevision"
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
    except zipfile.BadZipFile as exc:
        raise ReleaseValidationError(f"not a valid launcher JAR: {jar_path}") from exc

    console, console_assets = _verify_console_jar(console_content, console_path)
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
        maven_executable: str, skip_java_tests: bool) -> tuple[list[str], list[str]]:
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


def package_release(args: argparse.Namespace) -> Path:
    verify_release_sources()
    dirty = _worktree_dirty()
    if dirty and not args.allow_dirty:
        raise ReleaseValidationError(
            "release worktree is dirty; commit or isolate source changes before packaging"
        )

    output_dir = args.output_dir.resolve()
    if output_dir.exists() and any(output_dir.iterdir()):
        raise ReleaseValidationError(f"output directory must be empty: {output_dir}")
    output_dir.mkdir(parents=True, exist_ok=True)

    for command in maven_build_commands(args.maven_executable, args.skip_java_tests):
        subprocess.run(command, cwd=REPOSITORY_ROOT, check=True)
    if not dirty and _tracked_worktree_dirty():
        raise ReleaseValidationError(
            "release build changed tracked source/evidence files; refusing to package"
        )

    java_version = project_version()
    built_jar = LAUNCHER_MODULE / "target" / f"foggy-mcp-launcher-{java_version}.jar"
    evidence = verify_launcher_jar(built_jar)

    release_jar_name = f"foggy-runtime-launcher-{args.release_version}.jar"
    release_jar = output_dir / release_jar_name
    shutil.copy2(built_jar, release_jar)
    release_evidence = verify_launcher_jar(release_jar)

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
        "releaseReady": not dirty and not args.skip_java_tests,
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
