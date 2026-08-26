#!/usr/bin/env python3
"""Package, smoke-test, and optionally publish the standard Runtime Launcher.

All build artifacts, distribution files, checksums, and manifest content come from
``runtime_launcher_package.py``. This script only orchestrates release preflight,
runtime smoke checks, conservative GitHub tag/Release creation, and an optional
legacy OBS replay check.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import signal
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
PACKAGER = Path(__file__).with_name("runtime_launcher_package.py")
DEFAULT_REPOSITORY = "foggy-projects/foggy-data-mcp-bridge"
OBS_INDEX = "https://obs-fe55.obs.cn-north-4.myhuaweicloud.com/foggy-runtime/latest.json"
VERSION_PATTERN = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(?:[._-][A-Za-z0-9._-]+)?$")


class ReleaseError(RuntimeError):
    """Raised when a release preflight, smoke, or publication gate fails."""


def log(message: str) -> None:
    print(f">> {message}", flush=True)


def run(
    arguments: list[str],
    *,
    cwd: Path = REPOSITORY_ROOT,
    capture: bool = False,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        arguments,
        cwd=cwd,
        check=check,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
    )


def command_output(*arguments: str, cwd: Path = REPOSITORY_ROOT) -> str:
    return run(list(arguments), cwd=cwd, capture=True).stdout.strip()


def normalize_version(raw: str) -> str:
    version = raw.removeprefix("foggy-runtime-launcher-v").removeprefix("v")
    if not VERSION_PATTERN.fullmatch(version):
        raise ReleaseError(f"invalid launcher release version: {raw}")
    return version


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def expected_assets(version: str) -> tuple[str, ...]:
    return (
        f"foggy-runtime-launcher-{version}.jar",
        "start-foggy-runtime.ps1",
        "start-foggy-runtime.sh",
        "README-foggy-runtime-launcher.md",
        "runtime-launcher-manifest.json",
        "SHA256SUMS",
    )


def parse_checksums(path: Path) -> dict[str, str]:
    checksums: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._+-]+)", line)
        if not match:
            raise ReleaseError(f"invalid SHA256SUMS line: {line!r}")
        digest, name = match.groups()
        if name in checksums:
            raise ReleaseError(f"duplicate checksum entry: {name}")
        checksums[name] = digest
    return checksums


def verify_package(package_dir: Path, version: str, commit: str) -> dict[str, Any]:
    assets = expected_assets(version)
    actual = tuple(sorted(path.name for path in package_dir.iterdir() if path.is_file()))
    if set(actual) != set(assets):
        raise ReleaseError(
            f"release directory must contain exactly {list(assets)}; found {list(actual)}"
        )

    checksums = parse_checksums(package_dir / "SHA256SUMS")
    expected_checksum_names = set(assets) - {"SHA256SUMS"}
    if set(checksums) != expected_checksum_names:
        raise ReleaseError(
            "SHA256SUMS entries do not exactly match release assets: "
            f"expected={sorted(expected_checksum_names)} actual={sorted(checksums)}"
        )
    for name, expected in checksums.items():
        actual_digest = sha256_file(package_dir / name)
        if actual_digest != expected:
            raise ReleaseError(
                f"checksum mismatch for {name}: expected={expected} actual={actual_digest}"
            )

    manifest = json.loads(
        (package_dir / "runtime-launcher-manifest.json").read_text(encoding="utf-8")
    )
    assertions = {
        "releaseVersion": manifest.get("releaseVersion") == version,
        "releaseReady": manifest.get("releaseReady") is True,
        "source.commit": manifest.get("source", {}).get("commit") == commit,
        "source.dirty": manifest.get("source", {}).get("dirty") is False,
        "assets": manifest.get("assets") == list(assets),
        "analyticsConsole.embedded": manifest.get("features", {})
        .get("analyticsConsole", {})
        .get("embedded")
        is True,
        "analyticsConsole.enabledByDefault": manifest.get("features", {})
        .get("analyticsConsole", {})
        .get("enabledByDefault")
        is False,
        "analyticsConsole.springProfile": manifest.get("features", {})
        .get("analyticsConsole", {})
        .get("springProfile")
        == "analytics-console",
        "analyticsConsole.fapEnabledByDefault": manifest.get("features", {})
        .get("analyticsConsole", {})
        .get("fapEnabledByDefault")
        is False,
        "analyticsRuntimeApi.embedded": manifest.get("features", {})
        .get("analyticsRuntimeApi", {})
        .get("embedded")
        is True,
        "analyticsRuntimeApi.enabledByDefault": manifest.get("features", {})
        .get("analyticsRuntimeApi", {})
        .get("enabledByDefault")
        is False,
    }
    failed = [name for name, passed in assertions.items() if not passed]
    if failed:
        raise ReleaseError(f"runtime launcher manifest assertions failed: {failed}")

    jar = package_dir / assets[0]
    run([sys.executable, str(PACKAGER), "verify", "--jar", str(jar)])
    return manifest


def free_port(exclude: set[int] | None = None) -> int:
    excluded = exclude or set()
    while True:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.bind(("127.0.0.1", 0))
            port = int(sock.getsockname()[1])
        if port not in excluded:
            return port


def http_request(
    url: str,
    *,
    payload: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 5,
) -> tuple[int, bytes]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request_headers = dict(headers or {})
    if body is not None:
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=body, headers=request_headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def process_alive(pid: int) -> bool:
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False


def stop_process(pid: int) -> None:
    if not process_alive(pid):
        return
    if os.name == "nt":
        subprocess.run(
            ["taskkill", "/PID", str(pid), "/T", "/F"],
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        return
    os.kill(pid, signal.SIGTERM)
    for _ in range(30):
        if not process_alive(pid):
            return
        time.sleep(0.1)
    os.kill(pid, signal.SIGKILL)


def start_runtime(
    package_dir: Path, port: int, work_dir: Path, console_enabled: bool
) -> dict[str, Any]:
    if os.name == "nt":
        command = [
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(package_dir / "start-foggy-runtime.ps1"),
            "-Port",
            str(port),
            "-WorkDir",
            str(work_dir),
        ]
        if console_enabled:
            command.append("-AnalyticsConsole")
        result = run(command, cwd=package_dir, capture=True)
    else:
        environment = os.environ.copy()
        environment.update(
            {
                "PORT": str(port),
                "WORK_DIR": str(work_dir),
                "ANALYTICS_CONSOLE_ENABLED": "true" if console_enabled else "false",
            }
        )
        result = subprocess.run(
            [str(package_dir / "start-foggy-runtime.sh")],
            cwd=package_dir,
            env=environment,
            check=True,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise ReleaseError(
            f"launcher start script did not return JSON: {result.stdout!r} {result.stderr!r}"
        ) from exc


def wait_ready(runtime_url: str, pid: int, stdout_log: Path, stderr_log: Path) -> None:
    for _ in range(120):
        if not process_alive(pid):
            stdout = (
                stdout_log.read_text(encoding="utf-8", errors="replace")[-8000:]
                if stdout_log.exists()
                else ""
            )
            stderr = (
                stderr_log.read_text(encoding="utf-8", errors="replace")[-8000:]
                if stderr_log.exists()
                else ""
            )
            raise ReleaseError(
                f"runtime exited before ready; stdout tail={stdout!r}; stderr tail={stderr!r}"
            )
        status, body = http_request(f"{runtime_url}/readyz", timeout=2)
        if status == 200:
            payload = json.loads(body)
            if payload.get("status") == "ready":
                return
        time.sleep(1)
    raise ReleaseError(f"runtime did not become ready: {runtime_url}")


def smoke_mode(package_dir: Path, port: int, console_enabled: bool) -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="foggy-launcher-smoke-") as temporary:
        work_dir = Path(temporary)
        start = start_runtime(package_dir, port, work_dir, console_enabled)
        pid = int(start["pid"])
        runtime_url = f"http://127.0.0.1:{port}"
        try:
            wait_ready(
                runtime_url,
                pid,
                Path(start["stdoutLog"]),
                Path(start["stderrLog"]),
            )
            status, body = http_request(f"{runtime_url}/api/v1/capabilities")
            if status != 200:
                raise ReleaseError(f"Runtime API capabilities returned HTTP {status}")
            capabilities = json.loads(body)
            if capabilities.get("runtimeApiVersion") != "foggy-runtime-api/v1":
                raise ReleaseError("Runtime API capabilities contract mismatch")

            initialize_status, initialize_body = http_request(
                f"{runtime_url}/mcp/analyst/rpc",
                payload={"jsonrpc": "2.0", "id": 0, "method": "initialize", "params": {}},
                headers={"X-NS": "default"},
            )
            initialize = json.loads(initialize_body)
            if (
                initialize_status != 200
                or initialize.get("id") != 0
                or isinstance(initialize.get("id"), bool)
            ):
                raise ReleaseError("MCP initialize did not preserve numeric JSON-RPC id")

            tools_status, tools_body = http_request(
                f"{runtime_url}/mcp/analyst/rpc",
                payload={"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}},
                headers={"X-NS": "default"},
            )
            tools_payload = json.loads(tools_body)
            tool_names = {
                item.get("name") for item in tools_payload.get("result", {}).get("tools", [])
            }
            if tools_status != 200 or "dataset.list_models" not in tool_names:
                raise ReleaseError("MCP tools/list does not expose dataset.list_models")

            console_paths = (
                "/analytics-console/",
                "/analytics-console/api/v1/session",
                "/analytics-console/api/v1/integrations/fap/question-publication",
                "/analytics/api/v1/capabilities",
            )
            route_statuses = {
                path: http_request(f"{runtime_url}{path}")[0] for path in console_paths
            }
            if console_enabled:
                failed = [path for path, route_status in route_statuses.items() if route_status != 200]
                if failed:
                    raise ReleaseError(f"Console opt-in routes failed: {failed}")
                publication_status, publication_body = http_request(
                    f"{runtime_url}/analytics-console/api/v1/integrations/fap/"
                    "question-publication"
                )
                publication = json.loads(publication_body)
                publication_data = publication.get("data", {})
                if (
                    publication_status != 200
                    or publication.get("success") is not True
                    or publication_data.get("contractVersion")
                    != "foggy.analytics.question-host-sync-bundle.v1"
                    or publication_data.get("mutationPerformed") is not False
                    or len(publication_data.get("functions", [])) != 5
                ):
                    raise ReleaseError(
                        "Console FAP question publication handoff contract mismatch"
                    )
            else:
                exposed = [
                    path
                    for path, route_status in route_statuses.items()
                    if 200 <= route_status < 300
                ]
                if exposed:
                    raise ReleaseError(f"Console routes are exposed by default: {exposed}")

            return {
                "status": "passed",
                "runtimeUrl": runtime_url,
                "analyticsConsoleEnabled": console_enabled,
                "routeStatuses": route_statuses,
                "runtimeApiVersion": capabilities.get("runtimeApiVersion"),
                "containsListModels": True,
            }
        finally:
            stop_process(pid)


def run_smoke(package_dir: Path, requested_port: int) -> list[dict[str, Any]]:
    default_port = requested_port if requested_port > 0 else free_port()
    console_port = free_port({default_port})
    log(f"smoke: default profile on port {default_port}")
    default = smoke_mode(package_dir, default_port, False)
    log(f"smoke: Analytics Console opt-in on port {console_port}")
    console = smoke_mode(package_dir, console_port, True)
    return [default, console]


def release_notes(
    version: str, commit: str, manifest: dict[str, Any], checksums: dict[str, str]
) -> str:
    checksum_lines = "\n".join(
        f"- `{digest}  {name}`" for name, digest in checksums.items()
    )
    return f"""## Foggy Runtime Launcher {version}

Standard launcher built from `{commit}`.

### Added

- Embeds Analytics Console and Analytics Runtime API in the standard executable JAR.
- Keeps Analytics Console disabled by default; enable it explicitly with the packaged start script.
- Keeps FAP optional and never provisions or mutates host-managed FAP resources at startup.
- Ships the governed Analytics question Skill and current Function schema delivery.

### Function contract

- Live model Functions resolve the current valid model from `namespace + modelName` once per invocation.
- `expectedModelRevision` and `MODEL_REVISION_CONFLICT` are not part of the live Function contract.
- One invocation keeps its resolved CatalogResolution stable through validation or execution.

### Validation

- Release-ready manifest: `{str(manifest['releaseReady']).lower()}`.
- Default profile and Console opt-in runtime smokes passed.
- `SHA256SUMS`, embedded Console assets, Function delivery, Runtime API, and MCP contracts passed.

### Asset SHA256

{checksum_lines}
"""


def remote_tag_commit(tag: str) -> str | None:
    result = run(
        [
            "git",
            "ls-remote",
            "--tags",
            "origin",
            f"refs/tags/{tag}",
            f"refs/tags/{tag}^{{}}",
        ],
        capture=True,
        check=False,
    )
    if result.returncode != 0:
        raise ReleaseError(f"cannot inspect remote tag {tag}: {result.stderr.strip()}")
    lines = [line.split("\t", 1) for line in result.stdout.splitlines() if "\t" in line]
    peeled = [commit for commit, ref in lines if ref.endswith("^{}")]
    direct = [commit for commit, ref in lines if not ref.endswith("^{}")]
    return (peeled or direct or [None])[0]


def gh_release(tag: str, repository: str) -> dict[str, Any] | None:
    result = run(
        [
            "gh",
            "release",
            "view",
            tag,
            "--repo",
            repository,
            "--json",
            "isDraft,isPrerelease,url,assets,tagName",
        ],
        capture=True,
        check=False,
    )
    if result.returncode != 0:
        return None
    return json.loads(result.stdout)


def verify_remote_assets(
    release: dict[str, Any], package_dir: Path, version: str
) -> None:
    expected = set(expected_assets(version))
    assets = {asset["name"]: asset for asset in release.get("assets", [])}
    if set(assets) != expected:
        raise ReleaseError(
            f"GitHub Release asset set mismatch: expected={sorted(expected)} "
            f"actual={sorted(assets)}"
        )
    for name, asset in assets.items():
        digest = asset.get("digest")
        expected_digest = f"sha256:{sha256_file(package_dir / name)}"
        if digest and digest != expected_digest:
            raise ReleaseError(
                f"GitHub asset digest mismatch for {name}: expected={expected_digest} "
                f"actual={digest}"
            )


def publish_release(
    package_dir: Path,
    version: str,
    commit: str,
    repository: str,
    notes: str,
    replace_existing: bool,
    retarget_tag: bool,
    stable: bool,
) -> str:
    tag = f"foggy-runtime-launcher-v{version}"
    log("fetching origin/main and release tags")
    run(["git", "fetch", "origin", "--prune", "--tags"])
    if command_output("git", "status", "--porcelain"):
        raise ReleaseError("bridge worktree must be clean before publication")
    if command_output("git", "branch", "--show-current") != "main":
        raise ReleaseError("public launcher releases must be published from main")
    origin_main = command_output("git", "rev-parse", "origin/main")
    if commit != origin_main:
        raise ReleaseError(f"HEAD {commit} does not match origin/main {origin_main}")

    remote_commit = remote_tag_commit(tag)
    existing_release = gh_release(tag, repository)
    if remote_commit and remote_commit != commit and not (replace_existing and retarget_tag):
        raise ReleaseError(
            f"remote tag {tag} points to {remote_commit}; explicit replacement and "
            "retarget approval are required"
        )
    if existing_release and not replace_existing:
        raise ReleaseError(f"GitHub Release already exists: {tag}")

    with tempfile.TemporaryDirectory(prefix="foggy-launcher-publish-") as temporary:
        evidence_dir = Path(temporary)
        notes_path = evidence_dir / "RELEASE_NOTES.md"
        notes_path.write_text(notes, encoding="utf-8", newline="\n")
        tag_message = evidence_dir / "TAG_MESSAGE.txt"
        tag_message.write_text(
            f"Release {tag}\n\nSource commit: {commit}\n\n{notes}",
            encoding="utf-8",
            newline="\n",
        )

        if remote_commit is None:
            run(["git", "tag", "-a", tag, commit, "-F", str(tag_message)])
            run(["git", "push", "origin", tag])
        elif remote_commit != commit:
            run(["git", "tag", "-f", "-a", tag, commit, "-F", str(tag_message)])
            run(["git", "push", "--force", "origin", tag])

        asset_paths = [str(package_dir / name) for name in expected_assets(version)]
        if existing_release:
            run(
                [
                    "gh",
                    "release",
                    "upload",
                    tag,
                    *asset_paths,
                    "--repo",
                    repository,
                    "--clobber",
                ]
            )
            edit = [
                "gh",
                "release",
                "edit",
                tag,
                "--repo",
                repository,
                "--title",
                f"Foggy Runtime Launcher {version}",
                "--notes-file",
                str(notes_path),
            ]
            if not stable:
                edit.append("--prerelease")
            run(edit)
        else:
            create = [
                "gh",
                "release",
                "create",
                tag,
                *asset_paths,
                "--repo",
                repository,
                "--title",
                f"Foggy Runtime Launcher {version}",
                "--notes-file",
                str(notes_path),
                "--draft",
            ]
            run(create)
            draft = gh_release(tag, repository)
            if not draft or draft.get("isDraft") is not True:
                raise ReleaseError("GitHub draft release was not created")
            verify_remote_assets(draft, package_dir, version)
            publish = [
                "gh",
                "release",
                "edit",
                tag,
                "--repo",
                repository,
                "--draft=false",
            ]
            if not stable:
                publish.append("--prerelease")
            run(publish)

    published = gh_release(tag, repository)
    if not published or published.get("isDraft"):
        raise ReleaseError(f"GitHub Release was not published: {tag}")
    verify_remote_assets(published, package_dir, version)
    return str(published.get("url"))


def verify_obs(package_dir: Path, version: str, timeout_seconds: int) -> dict[str, Any]:
    tag = f"foggy-runtime-launcher-v{version}"
    commit = json.loads(
        (package_dir / "runtime-launcher-manifest.json").read_text(encoding="utf-8")
    )["source"]["commit"]
    deadline = time.monotonic() + timeout_seconds
    last_error = "OBS index did not update"
    while time.monotonic() < deadline:
        try:
            status, body = http_request(OBS_INDEX, timeout=10)
            if status == 200:
                index = json.loads(body)
                launcher = index.get("components", {}).get("launcher", {})
                if launcher.get("tag") == tag and launcher.get("version") == version:
                    expected = set(expected_assets(version))
                    remote_assets = launcher.get("assets", {})
                    if set(remote_assets) != expected:
                        raise ReleaseError(
                            "OBS launcher asset set mismatch: "
                            f"expected={sorted(expected)} actual={sorted(remote_assets)}"
                        )
                    with tempfile.TemporaryDirectory(prefix="foggy-launcher-obs-") as temporary:
                        replay_dir = Path(temporary)
                        for name, metadata in remote_assets.items():
                            url = metadata.get("url")
                            if not isinstance(url, str) or not url.startswith("https://"):
                                raise ReleaseError(f"invalid OBS asset URL for {name}")
                            with urllib.request.urlopen(url, timeout=120) as response:
                                destination = replay_dir / name
                                with destination.open("wb") as output:
                                    while chunk := response.read(1024 * 1024):
                                        output.write(chunk)
                            expected_digest = sha256_file(package_dir / name)
                            if sha256_file(destination) != expected_digest:
                                raise ReleaseError(f"OBS replay checksum mismatch for {name}")
                        verify_package(replay_dir, version, commit)
                    return launcher
            last_error = f"latest launcher in OBS is not {tag}"
        except (OSError, ValueError, json.JSONDecodeError, ReleaseError) as exc:
            last_error = str(exc)
        log(f"waiting for OBS sync: {last_error}")
        time.sleep(10)
    raise ReleaseError(
        f"OBS sync did not pass within {timeout_seconds}s: {last_error}"
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-version", "-r", required=True)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--target-commit")
    parser.add_argument("--repo-slug", default=DEFAULT_REPOSITORY)
    parser.add_argument(
        "--skip-upload", action="store_true", help="package and validate locally only"
    )
    parser.add_argument(
        "--reuse-package",
        action="store_true",
        help="reuse and revalidate an existing package directory",
    )
    parser.add_argument(
        "--skip-smoke",
        action="store_true",
        help="local development only; forbidden for publication",
    )
    parser.add_argument("--replace-existing", action="store_true")
    parser.add_argument("--retarget-tag", action="store_true")
    parser.add_argument(
        "--stable",
        action="store_true",
        help="publish a stable rather than prerelease GitHub Release",
    )
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument(
        "--verify-obs",
        action="store_true",
        help="opt in to the legacy OBS replay check after GitHub publication",
    )
    parser.add_argument("--obs-timeout-seconds", type=int, default=300)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        version = normalize_version(args.release_version)
        commit = command_output("git", "rev-parse", "HEAD")
        if args.target_commit:
            requested = command_output("git", "rev-parse", f"{args.target_commit}^{{commit}}")
            if requested != commit:
                raise ReleaseError(
                    f"target commit {requested} is not checked-out HEAD {commit}; "
                    "package source and tag must match"
                )
        if not args.skip_upload and args.skip_smoke:
            raise ReleaseError("publication forbids --skip-smoke")
        if args.skip_upload and args.verify_obs:
            raise ReleaseError("--verify-obs requires GitHub publication")
        if args.retarget_tag and not args.replace_existing:
            raise ReleaseError(
                "--retarget-tag requires --replace-existing and explicit approval"
            )

        package_dir = (
            args.output_dir
            or REPOSITORY_ROOT
            / ".foggy-runtime"
            / "releases"
            / f"foggy-runtime-launcher-{version}"
        ).resolve()
        if not args.reuse_package:
            packager = [
                sys.executable,
                str(PACKAGER),
                "package",
                "--release-version",
                version,
                "--output-dir",
                str(package_dir),
            ]
            log(f"packaging standard launcher {version}")
            run(packager)

        manifest = verify_package(package_dir, version, commit)
        smokes: list[dict[str, Any]] = []
        if not args.skip_smoke:
            smokes = run_smoke(package_dir, args.port)
        checksums = parse_checksums(package_dir / "SHA256SUMS")
        notes = release_notes(version, commit, manifest, checksums)

        release_url = None
        obs = None
        if not args.skip_upload:
            release_url = publish_release(
                package_dir,
                version,
                commit,
                args.repo_slug,
                notes,
                args.replace_existing,
                args.retarget_tag,
                args.stable,
            )
            if args.verify_obs:
                obs = verify_obs(package_dir, version, args.obs_timeout_seconds)

        print(
            json.dumps(
                {
                    "status": "published" if release_url else "validated",
                    "releaseVersion": version,
                    "tagName": f"foggy-runtime-launcher-v{version}",
                    "targetCommit": commit,
                    "outputDirectory": str(package_dir),
                    "jar": manifest["jar"],
                    "smoke": smokes,
                    "releaseUrl": release_url,
                    "primaryDistribution": "github-release",
                    "obs": obs,
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    except (ReleaseError, subprocess.CalledProcessError, OSError, json.JSONDecodeError) as exc:
        print(f"runtime launcher publication failed: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
