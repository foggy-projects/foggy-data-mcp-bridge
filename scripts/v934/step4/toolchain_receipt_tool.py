#!/usr/bin/env python3
"""Seal and replay the exact 9.3.4 Step 4 Java/Maven toolchain."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import secrets
import shutil
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath
from typing import Any, Iterable


ANSI_RE = re.compile(r"\x1b\[[0-9;]*m")
RUN_ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

EXPECTED_COMMANDS = {
    "java": {
        "binary_sha256": "c0a86c03d2acbe2f389d5198142309f8e61db0c498920893977a494c4e9fd69b",
    },
    "javac": {
        "binary_sha256": "4e6c3635162235234bde92b0645e2df7e7340f1a21498a483c22d624aebffc7c",
    },
    "mvn": {
        "binary_sha256": "c9a491d16181737e76ee9d694ff3767a9f6671541c956b23d2f08e97672f3447",
    },
}

EXPECTED_JDK = {
    "release_sha256": "d11d538e6810095deff65fee2b1b4ddb53025b5f87b70ccf842bd7a550fa6ec8",
    "modules_sha256": "2d9de349be2aaa70adf53f78a88b8ca9bff70e910795655022808b4f61cbdc15",
    "jre_package": "openjdk-17-jre-headless",
    "jdk_package": "openjdk-17-jdk-headless",
    "package_version": "17.0.19+10-1~24.04.2",
}
EXPECTED_MAVEN_PACKAGE = {"package": "maven", "version": "3.8.7-2"}

# Order is the Maven plugin realm "Included" order. It is deliberately not
# sorted: order drift changes class loading and therefore changes the receipt.
COMPILER_REALM = (
    ("org.apache.maven.plugins:maven-compiler-plugin:3.13.0", "org/apache/maven/plugins/maven-compiler-plugin/3.13.0/maven-compiler-plugin-3.13.0.jar", "6f819947a2a773792dc91d17f906e22113bfb2fefb6b64dec836175715d7402a"),
    ("org.apache.maven.shared:maven-shared-utils:3.4.2", "org/apache/maven/shared/maven-shared-utils/3.4.2/maven-shared-utils-3.4.2.jar", "b613357e1bad4dfc1dead801691c9460f9585fe7c6b466bc25186212d7d18487"),
    ("commons-io:commons-io:2.11.0", "commons-io/commons-io/2.11.0/commons-io-2.11.0.jar", "961b2f6d87dbacc5d54abf45ab7a6e2495f89b75598962d8c723cea9bc210908"),
    ("org.apache.maven.shared:maven-shared-incremental:1.1", "org/apache/maven/shared/maven-shared-incremental/1.1/maven-shared-incremental-1.1.jar", "61988e54486a5dc38f06c70fdae5b108556c63bd433697b9f4305fcdb30fa40e"),
    ("org.codehaus.plexus:plexus-java:1.2.0", "org/codehaus/plexus/plexus-java/1.2.0/plexus-java-1.2.0.jar", "4d2d63cdcad46feba432110ef64bcdc8f8fad48538fda5cd2253686b45a94304"),
    ("org.ow2.asm:asm:9.6", "org/ow2/asm/asm/9.6/asm-9.6.jar", "3c6fac2424db3d4a853b669f4e3d1d9c3c552235e19a319673f887083c2303a1"),
    ("com.thoughtworks.qdox:qdox:2.0.3", "com/thoughtworks/qdox/qdox/2.0.3/qdox-2.0.3.jar", "ff70c10165714fe9546c418a65d74ecd5d57623ba408cecde9428f0a609b5d1c"),
    ("org.codehaus.plexus:plexus-compiler-api:2.15.0", "org/codehaus/plexus/plexus-compiler-api/2.15.0/plexus-compiler-api-2.15.0.jar", "d31d744eb69f77dffd3722dca4094758e0f90e79918a7b3b9fdc37ce49b60342"),
    ("org.codehaus.plexus:plexus-compiler-manager:2.15.0", "org/codehaus/plexus/plexus-compiler-manager/2.15.0/plexus-compiler-manager-2.15.0.jar", "c13b12c32a18b00e457de9b93cfc3d5593bfa1fb992b2c46a3498be1a77c4889"),
    ("org.codehaus.plexus:plexus-xml:3.0.0", "org/codehaus/plexus/plexus-xml/3.0.0/plexus-xml-3.0.0.jar", "d2622dc9339b16f5b8c9cad2add440e965831d0e16f19ae1de24e1202b0de536"),
    ("org.codehaus.plexus:plexus-compiler-javac:2.15.0", "org/codehaus/plexus/plexus-compiler-javac/2.15.0/plexus-compiler-javac-2.15.0.jar", "89603334988453b9cf4d7ec404d4b54f140de28b678d6a8e8edc448240dd0e90"),
    ("org.codehaus.plexus:plexus-utils:4.0.0", "org/codehaus/plexus/plexus-utils/4.0.0/plexus-utils-4.0.0.jar", "270cd703b48c6e5c8c691f1875f22d62d22cfe072c73ae2f5814d83d68c1da0b"),
)

JACOCO_REALM = (
    ("org.jacoco:jacoco-maven-plugin:0.8.12", "org/jacoco/jacoco-maven-plugin/0.8.12/jacoco-maven-plugin-0.8.12.jar", "b305a57535247cff2b7450c4dc1db505c7c246c838cec48c10e52fa71aa423bd"),
    ("org.codehaus.plexus:plexus-utils:3.0.24", "org/codehaus/plexus/plexus-utils/3.0.24/plexus-utils-3.0.24.jar", "83ee748b12d06afb0ad4050a591132b3e8025fbb1990f1ed002e8b73293e69b4"),
    ("org.apache.maven.shared:file-management:3.1.0", "org/apache/maven/shared/file-management/3.1.0/file-management-3.1.0.jar", "2e8cb2d546a01c2259cb17f1e06732db3d14b079d19622bf8400c82cb1ee6b96"),
    ("commons-io:commons-io:2.11.0", "commons-io/commons-io/2.11.0/commons-io-2.11.0.jar", "961b2f6d87dbacc5d54abf45ab7a6e2495f89b75598962d8c723cea9bc210908"),
    ("org.apache.maven.reporting:maven-reporting-api:3.0", "org/apache/maven/reporting/maven-reporting-api/3.0/maven-reporting-api-3.0.jar", "498949e5576b022559d1622e534c18e052f94dec883924b67e0a4e8676c07b17"),
    ("org.apache.maven.doxia:doxia-sink-api:1.0", "org/apache/maven/doxia/doxia-sink-api/1.0/doxia-sink-api-1.0.jar", "1cd68e9b4cf427a2b6b9a943a9bef6da879d25702334ea5addb0d153bb8f8911"),
    ("org.jacoco:org.jacoco.agent:0.8.12:runtime", "org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar", "115e8e6e6593ca3a9892dfef695df4d487c706e59e71e64dc0ab95716ee02622"),
    ("org.jacoco:org.jacoco.core:0.8.12", "org/jacoco/org.jacoco.core/0.8.12/org.jacoco.core-0.8.12.jar", "fca26db37c0c5fbd5dc4985237eb82866df9799d5082af899475a73f91f5b035"),
    ("org.ow2.asm:asm:9.7", "org/ow2/asm/asm/9.7/asm-9.7.jar", "adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d"),
    ("org.ow2.asm:asm-commons:9.7", "org/ow2/asm/asm-commons/9.7/asm-commons-9.7.jar", "389bc247958e049fc9a0408d398c92c6d370c18035120395d4cba1d9d9304b7a"),
    ("org.ow2.asm:asm-tree:9.7", "org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar", "62f4b3bc436045c1acb5c3ba2d8ec556ec3369093d7f5d06c747eb04b56d52b1"),
    ("org.jacoco:org.jacoco.report:0.8.12", "org/jacoco/org.jacoco.report/0.8.12/org.jacoco.report-0.8.12.jar", "f9c79ad66a66a0337c57849ad1287a2ab23b9b232d35314443e5ec49e6e3d20f"),
)

TEST_ASM_GUARD = (
    ("org.ow2.asm:asm:9.7.1", "org/ow2/asm/asm/9.7.1/asm-9.7.1.jar", "8cadd43ac5eb6d09de05faecca38b917a040bb9139c7edeb4cc81c740b713281"),
    ("org.ow2.asm:asm-commons:9.7.1", "org/ow2/asm/asm-commons/9.7.1/asm-commons-9.7.1.jar", "9a579b54d292ad9be171d4313fd4739c635592c2b5ac3a459bbd1049cddec6a0"),
    ("org.ow2.asm:asm-tree:9.7.1", "org/ow2/asm/asm-tree/9.7.1/asm-tree-9.7.1.jar", "9929881f59eb6b840e86d54570c77b59ce721d104e6dfd7a40978991c2d3b41f"),
)

HELP_PLUGIN = (
    "org.apache.maven.plugins:maven-help-plugin:3.5.1",
    "org/apache/maven/plugins/maven-help-plugin/3.5.1/maven-help-plugin-3.5.1.jar",
    "db1296f90c93cd1ac763f8262674dc376cf7670166dc2c270a498b1141a51865",
)

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


class ToolchainError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ToolchainError(message)


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_regular_bytes(path: Path, label: str) -> bytes:
    try:
        before = path.lstat()
    except FileNotFoundError as exc:
        raise ToolchainError(f"{label}: missing real file") from exc
    require(stat.S_ISREG(before.st_mode) and not stat.S_ISLNK(before.st_mode), f"{label}: missing real file")
    descriptor = os.open(
        path,
        os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0),
    )
    try:
        opened = os.fstat(descriptor)
        require(
            stat.S_ISREG(opened.st_mode)
            and (before.st_dev, before.st_ino) == (opened.st_dev, opened.st_ino),
            f"{label}: file identity changed while opening",
        )
        chunks = []
        while True:
            chunk = os.read(descriptor, 1024 * 1024)
            if not chunk:
                break
            chunks.append(chunk)
        after = os.fstat(descriptor)
        require(
            (opened.st_dev, opened.st_ino, opened.st_size, opened.st_mtime_ns)
            == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns),
            f"{label}: file changed while reading",
        )
    finally:
        os.close(descriptor)
    data = b"".join(chunks)
    require(len(data) == opened.st_size, f"{label}: short read")
    return data


def file_identity(path: Path, label: str) -> dict[str, Any]:
    data = read_regular_bytes(path, label)
    require(data, f"{label}: empty file")
    return {"size": len(data), "sha256": sha256_bytes(data)}


def run_output(command: list[str]) -> list[str]:
    process = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
        timeout=30,
        env={**os.environ, "LC_ALL": "C", "LANG": "C"},
    )
    require(process.returncode == 0, f"command failed: {' '.join(command)}")
    lines = [line.rstrip() for line in process.stdout.splitlines()]
    require(lines and lines[0], f"command returned no version: {' '.join(command)}")
    return lines


def command_receipt(
    name: str,
    expected_first_line: str,
) -> tuple[dict[str, Any], Path, list[str]]:
    located = shutil.which(name)
    require(located is not None, f"command missing: {name}")
    resolved = Path(located).resolve(strict=True)
    require(resolved.is_file() and not resolved.is_symlink(), f"unsafe command: {name}")
    lines = run_output([str(resolved), "-version"])
    expected = EXPECTED_COMMANDS[name]
    require(lines[0] == expected_first_line, f"{name} version differs: {lines[0]!r}")
    identity = file_identity(resolved, f"{name} binary")
    require(identity["sha256"] == expected["binary_sha256"], f"{name} binary hash differs")
    return {
        "version_lines": lines,
        **identity,
    }, resolved, lines


def package_version(name: str) -> str:
    process = subprocess.run(
        ["dpkg-query", "-W", "-f=${Version}", name],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
        timeout=30,
    )
    require(process.returncode == 0 and process.stdout.strip(), f"package missing: {name}")
    return process.stdout.strip()


def parse_json_unique(data: bytes, label: str) -> dict[str, Any]:
    def unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            require(key not in result, f"{label}: duplicate JSON key: {key!r}")
            result[key] = value
        return result

    def reject_constant(value: str) -> None:
        raise ToolchainError(f"{label}: non-finite JSON number: {value}")

    try:
        value = json.loads(
            data,
            object_pairs_hook=unique_object,
            parse_constant=reject_constant,
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ToolchainError(f"{label}: invalid JSON") from exc
    require(isinstance(value, dict), f"{label}: JSON root must be an object")
    return value


def load_json_unique(path: Path, label: str) -> dict[str, Any]:
    return parse_json_unique(read_regular_bytes(path, label), label)


def pom_path_for(m2: Path, coordinate: str) -> Path:
    parts = coordinate.split(":")
    require(len(parts) in (3, 4), f"invalid coordinate: {coordinate}")
    group, artifact, version = parts[:3]
    return m2.joinpath(*group.split("."), artifact, version, f"{artifact}-{version}.pom")


def realm_receipt(
    m2: Path,
    name: str,
    entries: Iterable[tuple[str, str, str]],
    *,
    imported_realm: str,
    resolved_coordinates: list[str] | None = None,
) -> dict[str, Any]:
    entries = tuple(entries)
    rows = []
    for coordinate, relative, expected_sha in entries:
        pure = PurePosixPath(relative)
        require(not pure.is_absolute() and ".." not in pure.parts, f"{name}: unsafe path")
        jar = m2.joinpath(*pure.parts)
        jar_identity = file_identity(jar, f"{name} {coordinate} JAR")
        require(jar_identity["sha256"] == expected_sha, f"{name} {coordinate}: JAR hash differs")
        pom = pom_path_for(m2, coordinate)
        pom_identity = file_identity(pom, f"{name} {coordinate} POM")
        rows.append(
            {
                "coordinate": coordinate,
                "repository_path": relative,
                "jar": jar_identity,
                "pom": {
                    "repository_path": pom.relative_to(m2).as_posix(),
                    **pom_identity,
                },
            }
        )
    encoded = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode("utf-8")
    if resolved_coordinates is not None:
        expected_coordinates = []
        for coordinate, _relative, _expected_sha in entries:
            parts = coordinate.split(":")
            if len(parts) == 3:
                group, artifact, version = parts
                expected_coordinates.append(f"{group}:{artifact}:jar:{version}")
            else:
                group, artifact, version, classifier = parts
                expected_coordinates.append(
                    f"{group}:{artifact}:jar:{classifier}:{version}"
                )
        require(
            resolved_coordinates == expected_coordinates,
            f"{name}: Maven resolved realm differs",
        )
    return {
        "imported_realm": imported_realm,
        "entry_count": len(rows),
        "ordered_entries": rows,
        "ordered_closure_sha256": sha256_bytes(encoded),
        **(
            {"maven_resolved_coordinates": resolved_coordinates}
            if resolved_coordinates is not None
            else {}
        ),
    }


def artifact_guard_receipt(
    m2: Path,
    name: str,
    entries: Iterable[tuple[str, str, str]],
) -> dict[str, Any]:
    receipt = realm_receipt(
        m2,
        name,
        entries,
        imported_realm="none",
    )
    receipt.pop("imported_realm")
    return {"scope": "project-test-classpath", **receipt}


def maven_runtime_receipt(home: Path) -> dict[str, Any]:
    rows: list[dict[str, Any]] = []
    for root_name in ("bin", "boot", "lib", "conf"):
        logical_root = home / root_name
        require(logical_root.exists(), f"Maven runtime root missing: {root_name}")
        if logical_root.is_symlink():
            rows.append(
                {
                    "path": root_name,
                    "kind": "directory-symlink",
                    "link_target": os.readlink(logical_root),
                }
            )
        walk_root = logical_root.resolve(strict=True)
        for current, directories, files in os.walk(walk_root, followlinks=False):
            directories.sort()
            files.sort()
            current_path = Path(current)
            for directory in directories:
                require(
                    not (current_path / directory).is_symlink(),
                    f"Maven runtime contains nested directory symlink: {root_name}/{directory}",
                )
            for filename in files:
                path = current_path / filename
                relative = path.relative_to(walk_root).as_posix()
                logical = f"{root_name}/{relative}"
                identity = file_identity(path.resolve(strict=True), f"Maven runtime {logical}")
                logical_path = walk_root / relative
                row: dict[str, Any] = {
                    "path": logical,
                    "kind": "symlink-file" if logical_path.is_symlink() else "file",
                    **identity,
                }
                if logical_path.is_symlink():
                    row["link_target"] = os.readlink(logical_path)
                rows.append(row)
    rows.sort(key=lambda item: (item["path"], item["kind"]))
    require(len(rows) >= 40, "Maven runtime closure is unexpectedly small")
    encoded = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "entry_count": len(rows),
        "entries": rows,
        "closure_sha256": sha256_bytes(encoded),
    }


def configuration_receipt(repo_root: Path, maven_home: Path, m2: Path) -> list[dict[str, Any]]:
    candidates = (
        ("global-settings", maven_home / "conf/settings.xml"),
        ("global-toolchains", maven_home / "conf/toolchains.xml"),
        ("user-settings", m2.parent / "settings.xml"),
        ("user-toolchains", m2.parent / "toolchains.xml"),
        ("user-mavenrc", Path.home() / ".mavenrc"),
        ("system-mavenrc", Path("/etc/mavenrc")),
        ("project-maven-config", repo_root / ".mvn/maven.config"),
        ("project-jvm-config", repo_root / ".mvn/jvm.config"),
        ("project-extensions", repo_root / ".mvn/extensions.xml"),
    )
    rows = []
    for label, path in candidates:
        if path.exists() or path.is_symlink():
            require(path.is_file() and not path.is_symlink(), f"unsafe Maven config: {label}")
            rows.append({"label": label, "present": True, **file_identity(path, label)})
        else:
            rows.append({"label": label, "present": False})
    return rows


def environment_receipt() -> list[dict[str, Any]]:
    names = (
        "JAVA_HOME", "JDK_HOME", "MAVEN_HOME", "M2_HOME", "MAVEN_OPTS",
        "MAVEN_ARGS", "MAVEN_CONFIG", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS",
        "_JAVA_OPTIONS",
    )
    rows = []
    for name in names:
        value = os.environ.get(name)
        rows.append(
            {
                "name": name,
                "present": value is not None,
                "value_sha256": None if value is None else sha256_bytes(value.encode("utf-8")),
            }
        )
    return rows


def step1_contract(repo_root: Path) -> tuple[str, dict[str, str], list[str]]:
    path = repo_root / "scripts/v934/contract-freeze.json"
    freeze = load_json_unique(path, "Step 1 contract freeze")
    require(
        freeze.get("schema_version") == 1
        and freeze.get("version") == "9.3.4"
        and freeze.get("step") == 1
        and freeze.get("status") == "confirmed",
        "Step 1 contract freeze identity/status differs",
    )
    toolchain = freeze.get("toolchain")
    require(isinstance(toolchain, dict), "Step 1 toolchain contract is missing")
    expected = {}
    for name, key in (
        ("java", "java_version"),
        ("javac", "javac_version"),
        ("mvn", "maven_version"),
    ):
        value = toolchain.get(key)
        require(isinstance(value, str) and value, f"Step 1 {key} is invalid")
        expected[name] = value
    reactor = freeze.get("reactor")
    require(
        isinstance(reactor, dict) and reactor.get("module_count") == 24,
        "Step 1 reactor contract is not exact 24 modules",
    )
    modules = reactor.get("modules")
    require(
        isinstance(modules, list)
        and len(modules) == 24
        and len(set(modules)) == 24
        and all(
            isinstance(module, str)
            and module
            and not Path(module).is_absolute()
            and ".." not in Path(module).parts
            for module in modules
        ),
        "Step 1 reactor module list is invalid",
    )
    return sha256_bytes(read_regular_bytes(path, "Step 1 contract freeze")), expected, modules


def maven_command(
    mvn_path: Path,
    repo_root: Path,
    arguments: list[str],
) -> list[str]:
    process = subprocess.run(
        [str(mvn_path), "-o", *arguments],
        cwd=repo_root,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=False,
        timeout=180,
        env={**os.environ, "LC_ALL": "C", "LANG": "C"},
    )
    require(process.returncode == 0, f"Maven evidence command failed: {' '.join(arguments)}")
    return process.stdout.splitlines()


def resolved_plugin_realm(
    mvn_path: Path,
    repo_root: Path,
    plugin: str,
    goal: str,
) -> list[str]:
    lines = maven_command(
        mvn_path,
        repo_root,
        [
            "-X",
            "-N",
            "-P!multi-db,!v934-coverage,!release",
            f"{plugin}:help",
            "-Ddetail=false",
            f"-Dgoal={goal}",
        ],
    )
    clean = [ANSI_RE.sub("", line) for line in lines]
    marker = f"Created new class realm plugin>{plugin}"
    starts = [index for index, line in enumerate(clean) if marker in line]
    require(len(starts) == 1, f"{plugin}: exact plugin realm marker not found")
    resolved = []
    for line in clean[starts[0] + 1 :]:
        if "Created new class realm plugin>" in line:
            break
        match = re.search(r"\]   Included: (\S+)\s*$", line)
        if match:
            resolved.append(match.group(1))
    require(resolved, f"{plugin}: Maven resolved no plugin artifacts")
    return resolved


def xml_root(data: bytes, label: str) -> ET.Element:
    require(b"<!DOCTYPE" not in data and b"<!ENTITY" not in data, f"{label}: XML declarations are forbidden")
    try:
        return ET.fromstring(data)
    except ET.ParseError as exc:
        raise ToolchainError(f"{label}: invalid XML") from exc


def pom_artifact_id(path: Path, label: str) -> str:
    root = xml_root(read_regular_bytes(path, label), label)
    artifact = root.findtext("m:artifactId", namespaces=NS)
    require(isinstance(artifact, str) and artifact.strip(), f"{label}: artifactId missing")
    return artifact.strip()


def effective_compiler_receipt(
    repo_root: Path,
    mvn_path: Path,
    m2: Path,
    modules: list[str],
) -> dict[str, Any]:
    output = repo_root / "target" / f"v934-toolchain-effective-{os.getpid()}-{secrets.token_hex(12)}.xml"
    require(output.parent.is_dir() and not output.parent.is_symlink(), "effective POM output parent is unsafe")
    require(not output.exists() and not output.is_symlink(), "effective POM output already exists")
    try:
        maven_command(
            mvn_path,
            repo_root,
            [
                "-q",
                "-P!multi-db,!v934-coverage,!release",
                "-DskipTests=true",
                "-DskipUnitTests=true",
                "-DskipITs=true",
                "org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom",
                f"-Doutput={output.relative_to(repo_root).as_posix()}",
            ],
        )
        data = read_regular_bytes(output, "effective reactor POM")
    finally:
        try:
            output.unlink(missing_ok=True)
        except OSError as exc:
            raise ToolchainError("cannot remove effective POM probe") from exc

    root = xml_root(data, "effective reactor POM")
    require(root.tag == "projects", "effective reactor POM root differs")
    projects = root.findall("m:project", NS)
    project_by_artifact: dict[str, ET.Element] = {}
    for project in projects:
        artifact = project.findtext("m:artifactId", namespaces=NS)
        require(isinstance(artifact, str) and artifact.strip(), "effective project artifactId missing")
        artifact = artifact.strip()
        require(artifact not in project_by_artifact, f"duplicate effective project artifactId: {artifact}")
        project_by_artifact[artifact] = project

    all_module_paths = [*modules, "build-support/foggy-coverage-report"]
    expected_artifacts = [pom_artifact_id(repo_root / path / "pom.xml", path) for path in all_module_paths]
    root_artifact = pom_artifact_id(repo_root / "pom.xml", "root POM")
    require(
        len(projects) == 26
        and set(project_by_artifact) == {root_artifact, *expected_artifacts},
        "effective reactor project set differs from root plus exact 25 modules",
    )

    rows = []
    for module in modules:
        artifact = pom_artifact_id(repo_root / module / "pom.xml", module)
        project = project_by_artifact[artifact]
        plugins = [
            plugin
            for plugin in project.findall("m:build/m:plugins/m:plugin", NS)
            if plugin.findtext("m:artifactId", namespaces=NS) == "maven-compiler-plugin"
        ]
        require(len(plugins) == 1, f"{module}: effective compiler plugin cardinality differs")
        plugin = plugins[0]
        group = plugin.findtext("m:groupId", namespaces=NS)
        require(group in (None, "org.apache.maven.plugins"), f"{module}: effective compiler group differs")
        require(
            plugin.findtext("m:version", namespaces=NS) == "3.13.0",
            f"{module}: effective compiler version differs",
        )
        configurations = plugin.findall("m:configuration", NS)
        require(len(configurations) == 1, f"{module}: effective compiler configuration cardinality differs")
        config_rows = [
            (child.tag.rsplit("}", 1)[-1], (child.text or "").strip())
            for child in list(configurations[0])
        ]
        require(
            config_rows
            == [
                ("source", "17"),
                ("target", "17"),
                ("encoding", "UTF-8"),
                ("parameters", "true"),
            ],
            f"{module}: effective compiler configuration differs",
        )
        rows.append(
            {
                "module": module,
                "artifact_id": artifact,
                "plugin": "org.apache.maven.plugins:maven-compiler-plugin:3.13.0",
                "configuration": {
                    "source": "17",
                    "target": "17",
                    "encoding": "UTF-8",
                    "parameters": True,
                },
            }
        )

    help_coordinate, help_relative, help_sha = HELP_PLUGIN
    help_jar = m2.joinpath(*PurePosixPath(help_relative).parts)
    help_identity = file_identity(help_jar, "Maven help plugin JAR")
    require(help_identity["sha256"] == help_sha, "Maven help plugin JAR hash differs")
    help_pom = pom_path_for(m2, help_coordinate)
    encoded = json.dumps(rows, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "validation": "Maven effective reactor model, exact compiler plugin/configuration",
        "project_count": len(projects),
        "production_module_count": len(rows),
        "module_models": rows,
        "module_models_sha256": sha256_bytes(encoded),
        "help_plugin": {
            "coordinate": help_coordinate,
            "jar": {"repository_path": help_relative, **help_identity},
            "pom": {
                "repository_path": help_pom.relative_to(m2).as_posix(),
                **file_identity(help_pom, "Maven help plugin POM"),
            },
        },
    }


def git_head(repo_root: Path) -> str:
    process = subprocess.run(
        ["git", "-C", str(repo_root), "rev-parse", "--verify", "HEAD^{commit}"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
        timeout=30,
    )
    value = process.stdout.strip()
    require(process.returncode == 0 and re.fullmatch(r"[0-9a-f]{40}", value) is not None, "Git HEAD unavailable")
    return value


def normalize_version_lines(
    lines: list[str],
    *,
    java_home: Path,
    maven_home: Path | None = None,
) -> list[str]:
    replacements = [(str(java_home), "$JAVA_HOME")]
    if maven_home is not None:
        replacements.append((str(maven_home), "$MAVEN_HOME"))
    normalized = []
    for line in lines:
        value = line
        for source, target in replacements:
            value = value.replace(source, target)
        require(str(Path.home()) not in value, "version output exposes the user home path")
        normalized.append(value)
    return normalized


def build_receipt(repo_root: Path, run_id: str) -> dict[str, Any]:
    require(RUN_ID_RE.fullmatch(run_id) is not None and run_id not in {".", ".."}, "unsafe run id")
    step1_sha, step1_versions, reactor_modules = step1_contract(repo_root)
    java, java_path, java_lines = command_receipt("java", step1_versions["java"])
    javac, javac_path, javac_lines = command_receipt("javac", step1_versions["javac"])
    mvn, mvn_path, mvn_lines = command_receipt("mvn", step1_versions["mvn"])
    java_home = java_path.parent.parent
    require(javac_path.parent.parent == java_home, "java and javac use different JDK homes")
    release = file_identity(java_home / "release", "JDK release")
    jdk_modules = file_identity(java_home / "lib/modules", "JDK modules")
    require(release["sha256"] == EXPECTED_JDK["release_sha256"], "JDK release hash differs")
    require(jdk_modules["sha256"] == EXPECTED_JDK["modules_sha256"], "JDK modules hash differs")
    require(package_version(EXPECTED_JDK["jre_package"]) == EXPECTED_JDK["package_version"], "JRE package version differs")
    require(package_version(EXPECTED_JDK["jdk_package"]) == EXPECTED_JDK["package_version"], "JDK package version differs")

    clean_mvn_lines = [ANSI_RE.sub("", line) for line in mvn_lines]
    maven_home_lines = [line for line in clean_mvn_lines if line.startswith("Maven home: ")]
    require(len(maven_home_lines) == 1, "Maven home line is not exact")
    maven_home = Path(maven_home_lines[0].split(": ", 1)[1]).resolve(strict=True)
    require(mvn_path == (maven_home / "bin/mvn").resolve(strict=True), "Maven launcher/home mismatch")
    require(package_version(EXPECTED_MAVEN_PACKAGE["package"]) == EXPECTED_MAVEN_PACKAGE["version"], "Maven package version differs")

    java_runtime_lines = [line for line in clean_mvn_lines if line.startswith("Java version: ")]
    require(len(java_runtime_lines) == 1, "Maven Java runtime line is not exact")
    runtime_match = re.fullmatch(
        r"Java version: ([^,]+), vendor: ([^,]+), runtime: (.+)",
        java_runtime_lines[0],
    )
    require(runtime_match is not None, "Maven Java runtime line cannot be parsed")
    maven_java_runtime = Path(runtime_match.group(3)).resolve(strict=True)
    require(maven_java_runtime == java_home, "Maven uses a different Java runtime")

    java["path"] = "$JAVA_HOME/bin/java"
    java["version_lines"] = normalize_version_lines(java_lines, java_home=java_home)
    javac["path"] = "$JAVA_HOME/bin/javac"
    javac["version_lines"] = normalize_version_lines(javac_lines, java_home=java_home)
    mvn["path"] = "$MAVEN_HOME/bin/mvn"
    mvn["version_lines"] = normalize_version_lines(
        mvn_lines,
        java_home=java_home,
        maven_home=maven_home,
    )
    mvn["java_runtime"] = {
        "version": runtime_match.group(1),
        "vendor": runtime_match.group(2),
        "runtime": "$JAVA_HOME",
    }

    m2 = (Path.home() / ".m2/repository").resolve(strict=True)
    require(m2.is_dir() and not m2.is_symlink(), "unsafe Maven local repository")
    compiler_resolved = resolved_plugin_realm(
        mvn_path,
        repo_root,
        "org.apache.maven.plugins:maven-compiler-plugin:3.13.0",
        "compile",
    )
    jacoco_resolved = resolved_plugin_realm(
        mvn_path,
        repo_root,
        "org.jacoco:jacoco-maven-plugin:0.8.12",
        "report",
    )
    return {
        "schema_version": 1,
        "kind": "v934-step4-toolchain-receipt",
        "status": "verified",
        "run_id": run_id,
        "git_head": git_head(repo_root),
        "tool_sha256": file_identity(Path(__file__).resolve(), "toolchain receipt tool")["sha256"],
        "step1_contract_freeze_sha256": step1_sha,
        "platform": {
            "system": platform.system(),
            "machine": platform.machine(),
            "release": platform.release(),
        },
        "commands": {"java": java, "javac": javac, "mvn": mvn},
        "jdk": {
            "home": "$JAVA_HOME",
            "release": release,
            "modules": jdk_modules,
            "packages": {
                EXPECTED_JDK["jre_package"]: EXPECTED_JDK["package_version"],
                EXPECTED_JDK["jdk_package"]: EXPECTED_JDK["package_version"],
            },
        },
        "maven": {
            "home": "$MAVEN_HOME",
            "package": EXPECTED_MAVEN_PACKAGE,
            "runtime": maven_runtime_receipt(maven_home),
            "configuration": configuration_receipt(repo_root, maven_home, m2),
            "environment": environment_receipt(),
        },
        "plugin_realms": {
            "compiler": realm_receipt(
                m2,
                "compiler realm",
                COMPILER_REALM,
                imported_realm="maven.api",
                resolved_coordinates=compiler_resolved,
            ),
            "jacoco_reporter": realm_receipt(
                m2,
                "JaCoCo reporter realm",
                JACOCO_REALM,
                imported_realm="maven.api",
                resolved_coordinates=jacoco_resolved,
            ),
        },
        "test_classpath_asm_guard": artifact_guard_receipt(
            m2,
            "test ASM guard",
            TEST_ASM_GUARD,
        ),
        "compiler_effective_contract": effective_compiler_receipt(
            repo_root,
            mvn_path,
            m2,
            reactor_modules,
        ),
    }


def canonical_bytes(payload: dict[str, Any]) -> bytes:
    return (json.dumps(payload, indent=2, sort_keys=True) + "\n").encode("utf-8")


def canonical_path(repo_root: Path, run_id: str) -> Path:
    return repo_root / "target/v934-step4-coverage/runs" / run_id / "toolchain-receipt.json"


def atomic_publish(path: Path, data: bytes) -> None:
    require(path.is_absolute(), "receipt output must be absolute")
    parent = path.parent
    parent_before = parent.lstat()
    require(
        stat.S_ISDIR(parent_before.st_mode)
        and not stat.S_ISLNK(parent_before.st_mode)
        and parent.resolve(strict=True) == parent,
        "unsafe receipt parent",
    )
    directory_fd = os.open(
        parent,
        os.O_RDONLY
        | getattr(os, "O_DIRECTORY", 0)
        | getattr(os, "O_CLOEXEC", 0)
        | getattr(os, "O_NOFOLLOW", 0),
    )
    bound_parent = os.fstat(directory_fd)
    require(
        (parent_before.st_dev, parent_before.st_ino)
        == (bound_parent.st_dev, bound_parent.st_ino),
        "receipt parent changed while opening",
    )
    try:
        os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
    except FileNotFoundError:
        pass
    else:
        os.close(directory_fd)
        raise ToolchainError("refusing to overwrite receipt")
    temporary_name = f".{path.name}.{os.getpid()}.{secrets.token_hex(12)}.tmp"
    published = False
    published_identity: tuple[int, int] | None = None
    descriptor = -1
    try:
        descriptor = os.open(
            temporary_name,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_CLOEXEC", 0)
            | getattr(os, "O_NOFOLLOW", 0),
            0o644,
            dir_fd=directory_fd,
        )
        view = memoryview(data)
        while view:
            written = os.write(descriptor, view)
            require(written > 0, "short write while staging receipt")
            view = view[written:]
        os.fsync(descriptor)
        staged = os.fstat(descriptor)
        published_identity = (staged.st_dev, staged.st_ino)
        os.close(descriptor)
        descriptor = -1
        os.link(
            temporary_name,
            path.name,
            src_dir_fd=directory_fd,
            dst_dir_fd=directory_fd,
            follow_symlinks=False,
        )
        published = True
        current = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
        require(
            stat.S_ISREG(current.st_mode)
            and (current.st_dev, current.st_ino) == published_identity,
            "published receipt identity differs from staged inode",
        )
        os.fsync(directory_fd)
        os.unlink(temporary_name, dir_fd=directory_fd)
        os.fsync(directory_fd)
        parent_after = parent.lstat()
        require(
            not stat.S_ISLNK(parent_after.st_mode)
            and (parent_after.st_dev, parent_after.st_ino)
            == (bound_parent.st_dev, bound_parent.st_ino),
            "receipt parent changed during publication",
        )
    except BaseException:
        if descriptor >= 0:
            os.close(descriptor)
            descriptor = -1
        try:
            os.unlink(temporary_name, dir_fd=directory_fd)
        except FileNotFoundError:
            pass
        if published and published_identity is not None:
            try:
                current = os.stat(path.name, dir_fd=directory_fd, follow_symlinks=False)
                if (current.st_dev, current.st_ino) == published_identity:
                    os.unlink(path.name, dir_fd=directory_fd)
            except FileNotFoundError:
                pass
        try:
            os.fsync(directory_fd)
        except OSError:
            pass
        raise
    finally:
        if descriptor >= 0:
            os.close(descriptor)
        os.close(directory_fd)


def validate_stored_receipt(payload: dict[str, Any], run_id: str) -> None:
    require(
        set(payload)
        == {
            "schema_version",
            "kind",
            "status",
            "run_id",
            "git_head",
            "tool_sha256",
            "step1_contract_freeze_sha256",
            "platform",
            "commands",
            "jdk",
            "maven",
            "plugin_realms",
            "test_classpath_asm_guard",
            "compiler_effective_contract",
        },
        "stored receipt top-level schema differs",
    )
    require(
        payload["schema_version"] == 1
        and type(payload["schema_version"]) is int
        and payload["kind"] == "v934-step4-toolchain-receipt"
        and payload["status"] == "verified"
        and payload["run_id"] == run_id
        and isinstance(payload["git_head"], str)
        and re.fullmatch(r"[0-9a-f]{40}", payload["git_head"]) is not None
        and isinstance(payload["tool_sha256"], str)
        and re.fullmatch(r"[0-9a-f]{64}", payload["tool_sha256"]) is not None
        and isinstance(payload["step1_contract_freeze_sha256"], str)
        and re.fullmatch(r"[0-9a-f]{64}", payload["step1_contract_freeze_sha256"])
        is not None,
        "stored receipt identity/status differs",
    )


def replay_receipt_bytes(
    stored_bytes: bytes,
    expected_bytes: bytes,
    run_id: str,
    label: str,
) -> None:
    stored = parse_json_unique(stored_bytes, label)
    validate_stored_receipt(stored, run_id)
    require(canonical_bytes(stored) == stored_bytes, f"{label}: JSON is not canonical")
    require(stored_bytes == expected_bytes, f"{label}: differs from current replay")


def negative_result(
    payload: dict[str, Any],
    expected_bytes: bytes,
    run_id: str,
) -> dict[str, Any]:
    cases: list[tuple[str, bytes]] = []

    extra_key = json.loads(json.dumps(payload))
    extra_key["forged"] = True
    cases.append(("extra-top-level-key", canonical_bytes(extra_key)))

    wrong_run = json.loads(json.dumps(payload))
    wrong_run["run_id"] = f"{run_id}-forged"
    cases.append(("wrong-run-id", canonical_bytes(wrong_run)))

    nested_tamper = json.loads(json.dumps(payload))
    nested_tamper["plugin_realms"]["compiler"]["ordered_entries"][0]["jar"][
        "sha256"
    ] = "0" * 64
    cases.append(("nested-realm-hash-tamper", canonical_bytes(nested_tamper)))

    compact = (json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n").encode()
    cases.append(("noncanonical-json", compact))

    duplicate = expected_bytes.replace(
        b'  "schema_version": 1,\n',
        b'  "schema_version": 1,\n  "schema_version": 1,\n',
        1,
    )
    require(duplicate != expected_bytes, "cannot construct duplicate-key negative")
    cases.append(("duplicate-json-key", duplicate))

    results = []
    for probe, candidate in cases:
        try:
            replay_receipt_bytes(candidate, expected_bytes, run_id, probe)
        except (ToolchainError, UnicodeError, json.JSONDecodeError) as exc:
            results.append(
                {
                    "probe": probe,
                    "candidate_sha256": sha256_bytes(candidate),
                    "observed_error": str(exc),
                    "status": "passed",
                }
            )
        else:
            raise ToolchainError(f"negative probe unexpectedly passed: {probe}")
    return {
        "schema_version": 1,
        "kind": "v934-step4-toolchain-receipt-negative",
        "run_id": run_id,
        "receipt_sha256": sha256_bytes(expected_bytes),
        "tool_sha256": payload["tool_sha256"],
        "probe_count": len(results),
        "probes": results,
        "status": "passed",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    seal = subparsers.add_parser("seal")
    seal.add_argument("--repo-root", type=Path, required=True)
    seal.add_argument("--run-id", required=True)
    seal.add_argument("--output", type=Path, required=True)
    verify = subparsers.add_parser("verify")
    verify.add_argument("--repo-root", type=Path, required=True)
    verify.add_argument("--run-id", required=True)
    verify.add_argument("--receipt", type=Path, required=True)
    negative = subparsers.add_parser("negative")
    negative.add_argument("--repo-root", type=Path, required=True)
    negative.add_argument("--run-id", required=True)
    negative.add_argument("--receipt", type=Path, required=True)
    negative.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        repo_root = args.repo_root.expanduser().resolve(strict=True)
        expected_path = canonical_path(repo_root, args.run_id)
        payload = build_receipt(repo_root, args.run_id)
        data = canonical_bytes(payload)
        if args.command == "seal":
            output = args.output.expanduser()
            if not output.is_absolute():
                output = repo_root / output
            require(output.resolve(strict=False) == expected_path, "non-canonical receipt output")
            atomic_publish(output, data)
            path = output
        elif args.command == "verify":
            path = args.receipt.expanduser()
            if not path.is_absolute():
                path = repo_root / path
            require(path.resolve(strict=False) == expected_path, "non-canonical receipt path")
            stored_bytes = read_regular_bytes(path, "toolchain receipt")
            replay_receipt_bytes(
                stored_bytes,
                data,
                args.run_id,
                "toolchain receipt",
            )
        else:
            path = args.receipt.expanduser()
            if not path.is_absolute():
                path = repo_root / path
            require(path.resolve(strict=False) == expected_path, "non-canonical receipt path")
            stored_bytes = read_regular_bytes(path, "toolchain receipt")
            replay_receipt_bytes(
                stored_bytes,
                data,
                args.run_id,
                "toolchain receipt",
            )
            result = negative_result(payload, data, args.run_id)
            output = args.output.expanduser()
            if not output.is_absolute():
                output = repo_root / output
            expected_output = expected_path.parent / "negative/toolchain-receipt.json"
            require(
                output.resolve(strict=False) == expected_output,
                "non-canonical negative output",
            )
            atomic_publish(output, canonical_bytes(result))
        print(
            json.dumps(
                {
                    "command": args.command,
                    "run_id": args.run_id,
                    "sha256": sha256_bytes(data),
                    "compiler_realm": len(COMPILER_REALM),
                    "jacoco_realm": len(JACOCO_REALM),
                    **(
                        {"negative_probes": 5}
                        if args.command == "negative"
                        else {}
                    ),
                    "status": "passed",
                },
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 0
    except (
        ToolchainError,
        OSError,
        UnicodeError,
        json.JSONDecodeError,
        subprocess.SubprocessError,
    ) as exc:
        print(
            json.dumps(
                {"command": getattr(args, "command", None), "error": str(exc), "status": "failed"},
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 2


if __name__ == "__main__":
    sys.exit(main())
