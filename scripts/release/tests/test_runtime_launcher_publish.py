from __future__ import annotations

import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "runtime_launcher_publish.py"
SPEC = importlib.util.spec_from_file_location("runtime_launcher_publish", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class RuntimeLauncherPublishTest(unittest.TestCase):

    def test_normalizes_supported_release_tags(self) -> None:
        self.assertEqual("0.1.18", release.normalize_version("0.1.18"))
        self.assertEqual(
            "0.1.18", release.normalize_version("foggy-runtime-launcher-v0.1.18")
        )
        with self.assertRaisesRegex(release.ReleaseError, "invalid launcher release version"):
            release.normalize_version("latest")

    def test_obs_replay_is_opt_in(self) -> None:
        parser = release.build_parser()
        default = parser.parse_args(["--release-version", "0.1.19"])
        requested = parser.parse_args(
            ["--release-version", "0.1.19", "--verify-obs"]
        )

        self.assertFalse(default.verify_obs)
        self.assertTrue(requested.verify_obs)

    @mock.patch.object(release, "run")
    def test_verifies_exact_release_package(self, verify_jar: mock.Mock) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            package_dir = Path(temporary)
            commit = "a" * 40
            self._write_package(package_dir, "0.1.18", commit)

            manifest = release.verify_package(package_dir, "0.1.18", commit)

            self.assertTrue(manifest["releaseReady"])
            verify_jar.assert_called_once()

    @mock.patch.object(release, "run")
    def test_rejects_unlisted_release_asset(self, verify_jar: mock.Mock) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            package_dir = Path(temporary)
            self._write_package(package_dir, "0.1.18", "b" * 40)
            (package_dir / "smoke-result.json").write_text("{}\n", encoding="utf-8")

            with self.assertRaisesRegex(release.ReleaseError, "exactly"):
                release.verify_package(package_dir, "0.1.18", "b" * 40)
            verify_jar.assert_not_called()

    @mock.patch.object(release, "run")
    def test_rejects_non_release_ready_manifest(self, verify_jar: mock.Mock) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            package_dir = Path(temporary)
            commit = "c" * 40
            self._write_package(package_dir, "0.1.18", commit, release_ready=False)

            with self.assertRaisesRegex(release.ReleaseError, "releaseReady"):
                release.verify_package(package_dir, "0.1.18", commit)
            verify_jar.assert_not_called()

    @staticmethod
    def _write_package(
        package_dir: Path,
        version: str,
        commit: str,
        *,
        release_ready: bool = True,
    ) -> None:
        assets = release.expected_assets(version)
        for name in assets[:4]:
            (package_dir / name).write_bytes(f"fixture:{name}\n".encode())
        manifest = {
            "releaseVersion": version,
            "releaseReady": release_ready,
            "source": {"commit": commit, "dirty": False},
            "assets": list(assets),
            "features": {
                "analyticsConsole": {
                    "embedded": True,
                    "enabledByDefault": False,
                    "springProfile": "analytics-console",
                    "fapEnabledByDefault": False,
                },
                "analyticsRuntimeApi": {
                    "embedded": True,
                    "enabledByDefault": False,
                },
            },
        }
        (package_dir / "runtime-launcher-manifest.json").write_text(
            json.dumps(manifest) + "\n", encoding="utf-8"
        )
        checksums = []
        for name in assets[:-1]:
            digest = hashlib.sha256((package_dir / name).read_bytes()).hexdigest()
            checksums.append(f"{digest}  {name}")
        (package_dir / "SHA256SUMS").write_text(
            "\n".join(checksums) + "\n", encoding="utf-8"
        )


if __name__ == "__main__":
    unittest.main()
