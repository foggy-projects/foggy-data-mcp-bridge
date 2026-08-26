from __future__ import annotations

import functools
import hashlib
import http.server
import json
import os
import subprocess
import tempfile
import threading
import unittest
from pathlib import Path


BRIDGE_ROOT = Path(__file__).resolve().parents[3]
SYNC_SCRIPT = BRIDGE_ROOT / "scripts" / "sync-release-to-obs.sh"


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format: str, *args: object) -> None:
        return


class SyncReleaseToObsTest(unittest.TestCase):

    def test_cli_checksum_contract_keeps_legacy_and_requires_new_full_coverage(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            legacy = root / "legacy"
            current = root / "current"
            future_legacy = root / "future-legacy"
            incomplete = root / "incomplete"
            self._write_cli_assets(legacy, "0.1.22", full_coverage=False)
            self._write_cli_assets(current, "0.1.23", full_coverage=True)
            self._write_cli_assets(future_legacy, "0.1.23", full_coverage=False)
            self._write_cli_assets(incomplete, "0.1.23", full_coverage=False)
            invalid_manifest = json.loads(
                (incomplete / "release-manifest.json").read_text(encoding="utf-8")
            )
            invalid_manifest["checksumCoverage"] = "all-release-assets"
            (incomplete / "release-manifest.json").write_text(
                json.dumps(invalid_manifest, indent=2) + "\n", encoding="utf-8"
            )

            for version, assets in (("0.1.22", legacy), ("0.1.23", current)):
                with self.subTest(version=version):
                    result = self._validate_cli(assets, version)
                    self.assertEqual(result.returncode, 0, result.stderr)

            rejected = self._validate_cli(future_legacy, "0.1.23")
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn(
                "Only the known CLI v0.1.22 release may omit checksumCoverage",
                rejected.stderr,
            )

            rejected = self._validate_cli(incomplete, "0.1.23")
            self.assertNotEqual(rejected.returncode, 0)
            self.assertIn("Checksum coverage mismatch", rejected.stderr)

    def test_component_indexes_assemble_shared_index_without_losing_peers(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            assets = root / "assets"
            obs_root = root / "obs"
            assets.mkdir()
            obs_root.mkdir()
            self._write_launcher_assets(assets, "1.2.3")
            self._seed_component(obs_root, "cli", "0.1.22", "v0.1.22")
            self._seed_component(obs_root, "skills", "0.1.17", "v0.1.17")
            obsutil = self._write_fake_obsutil(root)

            handler = functools.partial(QuietHandler, directory=str(obs_root))
            server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                public_base = f"http://127.0.0.1:{server.server_port}"
                result = subprocess.run(
                    [
                        "bash",
                        str(SYNC_SCRIPT),
                        "--component",
                        "launcher",
                        "--tag",
                        "foggy-runtime-launcher-v1.2.3",
                        "--repo",
                        "foggy-projects/foggy-data-mcp-bridge",
                        "--source-dir",
                        str(assets),
                        "--obsutil",
                        str(obsutil),
                        "--bucket",
                        "test-bucket",
                        "--prefix",
                        "foggy-runtime",
                        "--public-base-url",
                        public_base,
                    ],
                    cwd=BRIDGE_ROOT,
                    env={**os.environ, "FAKE_OBS_ROOT": str(obs_root)},
                    text=True,
                    capture_output=True,
                    check=False,
                    timeout=30,
                )
            finally:
                server.shutdown()
                thread.join(timeout=5)
                server.server_close()

            self.assertEqual(result.returncode, 0, result.stderr)
            component = json.loads(
                (obs_root / "foggy-runtime/latest/launcher.json").read_text()
            )
            shared = json.loads(
                (obs_root / "foggy-runtime/latest.json").read_text()
            )
            self.assertEqual(
                component["schemaVersion"], "foggy-runtime-component-release/v1"
            )
            self.assertEqual(component["release"]["version"], "1.2.3")
            self.assertEqual(
                {
                    name: value["version"]
                    for name, value in shared["components"].items()
                },
                {"cli": "0.1.22", "skills": "0.1.17", "launcher": "1.2.3"},
            )

    @staticmethod
    def _write_launcher_assets(destination: Path, version: str) -> None:
        names = [
            f"foggy-runtime-launcher-{version}.jar",
            "start-foggy-runtime.ps1",
            "start-foggy-runtime.sh",
            "README-foggy-runtime-launcher.md",
            "runtime-launcher-manifest.json",
            "SHA256SUMS",
        ]
        for name in names[:4]:
            (destination / name).write_text(f"{name} fixture\n", encoding="utf-8")
        jar_content = (destination / names[0]).read_bytes()
        manifest = {
            "schemaVersion": "foggy-runtime-launcher/v1",
            "releaseVersion": version,
            "releaseReady": True,
            "source": {"dirty": False},
            "jar": {
                "file": names[0],
                "sha256": hashlib.sha256(jar_content).hexdigest(),
                "bytes": len(jar_content),
            },
            "features": {
                "analyticsConsole": {
                    "embedded": True,
                    "enabledByDefault": False,
                    "fapEnabledByDefault": False,
                },
                "analyticsRuntimeApi": {
                    "embedded": True,
                    "enabledByDefault": False,
                },
            },
            "assets": names,
        }
        (destination / names[4]).write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        checksum_lines = []
        for name in names[:5]:
            digest = hashlib.sha256((destination / name).read_bytes()).hexdigest()
            checksum_lines.append(f"{digest}  {name}")
        (destination / names[5]).write_text(
            "\n".join(checksum_lines) + "\n", encoding="utf-8"
        )

    @staticmethod
    def _write_cli_assets(
        destination: Path, version: str, full_coverage: bool
    ) -> None:
        destination.mkdir()
        wheel = f"foggy_runtime_cli-{version}-py3-none-any.whl"
        sdist = f"foggy_runtime_cli-{version}.tar.gz"
        names = [
            wheel,
            sdist,
            "install-foggy-runtime-cli.ps1",
            "install-foggy-runtime-cli.sh",
            "release-manifest.json",
            "SHA256SUMS",
        ]
        for name in names[:4]:
            (destination / name).write_text(f"{name} fixture\n", encoding="utf-8")
        artifacts = []
        for name in (wheel, sdist):
            content = (destination / name).read_bytes()
            artifacts.append(
                {
                    "file": name,
                    "sha256": hashlib.sha256(content).hexdigest(),
                    "bytes": len(content),
                }
            )
        manifest = {
            "schemaVersion": "foggy-runtime-cli-release/v1",
            "version": version,
            "artifacts": artifacts,
            "checksums": "SHA256SUMS",
        }
        if full_coverage:
            manifest["checksumCoverage"] = "all-release-assets"
        (destination / names[4]).write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        checksum_names = names[:5] if full_coverage else names[:2]
        checksum_lines = [
            f"{hashlib.sha256((destination / name).read_bytes()).hexdigest()}  {name}"
            for name in checksum_names
        ]
        (destination / names[5]).write_text(
            "\n".join(checksum_lines) + "\n", encoding="utf-8"
        )

    @staticmethod
    def _validate_cli(assets: Path, version: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [
                "bash",
                str(SYNC_SCRIPT),
                "--component",
                "cli",
                "--tag",
                f"v{version}",
                "--repo",
                "foggy-projects/foggy-runtime-cli",
                "--source-dir",
                str(assets),
                "--validate-assets-only",
            ],
            cwd=BRIDGE_ROOT,
            text=True,
            capture_output=True,
            check=False,
            timeout=10,
        )

    @staticmethod
    def _seed_component(
        obs_root: Path, component: str, version: str, tag: str
    ) -> None:
        destination = obs_root / f"foggy-runtime/latest/{component}.json"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(
            json.dumps(
                {
                    "schemaVersion": "foggy-runtime-component-release/v1",
                    "component": component,
                    "updatedAt": "2026-08-25T00:00:00Z",
                    "bucket": "test-bucket",
                    "prefix": "foggy-runtime",
                    "release": {
                        "version": version,
                        "tag": tag,
                        "repository": f"foggy-projects/foggy-{component}",
                        "releaseUrl": f"https://github.test/{component}/{tag}",
                        "obsPrefix": f"foggy-runtime/{component}/{version}",
                        "assets": {
                            "fixture": {
                                "sha256": "0" * 64,
                                "size": 1,
                                "url": f"https://example.test/{component}/fixture",
                            }
                        },
                    },
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )

    @staticmethod
    def _write_fake_obsutil(root: Path) -> Path:
        script = root / "obsutil"
        script.write_text(
            """#!/usr/bin/env python3
import os
import pathlib
import shutil
import sys

if len(sys.argv) > 1 and sys.argv[1] == "config":
    raise SystemExit(0)
if len(sys.argv) < 4 or sys.argv[1] != "cp":
    raise SystemExit(2)
source = pathlib.Path(sys.argv[2])
target = sys.argv[3]
prefix = "obs://test-bucket/"
if not target.startswith(prefix):
    raise SystemExit(3)
destination = pathlib.Path(os.environ["FAKE_OBS_ROOT"]) / target[len(prefix):]
destination.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(source, destination)
""",
            encoding="utf-8",
        )
        script.chmod(0o755)
        return script


if __name__ == "__main__":
    unittest.main()
