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

    @mock.patch.object(release.subprocess, "run")
    def test_file_capture_does_not_use_inheritable_pipes(
        self, subprocess_run: mock.Mock
    ) -> None:
        def write_output(arguments: list[str], **kwargs: object) -> object:
            kwargs["stdout"].write('{"pid": 123}\n')
            kwargs["stderr"].write("launcher warning\n")
            return release.subprocess.CompletedProcess(arguments, 0)

        subprocess_run.side_effect = write_output

        result = release.run_file_captured(["powershell", "launcher.ps1"])

        self.assertEqual('{"pid": 123}\n', result.stdout)
        self.assertEqual("launcher warning\n", result.stderr)
        call = subprocess_run.call_args
        self.assertIsNot(call.kwargs["stdout"], release.subprocess.PIPE)
        self.assertIsNot(call.kwargs["stderr"], release.subprocess.PIPE)

    @mock.patch.object(release.time, "sleep")
    @mock.patch.object(release, "process_alive", side_effect=[True, False])
    @mock.patch.object(release.subprocess, "run")
    def test_windows_stop_waits_for_process_and_log_handle_release(
        self,
        subprocess_run: mock.Mock,
        process_alive: mock.Mock,
        sleep: mock.Mock,
    ) -> None:
        with mock.patch.object(release.os, "name", "nt"):
            release.stop_process(123)

        subprocess_run.assert_called_once()
        self.assertEqual(2, process_alive.call_count)
        sleep.assert_not_called()

    @mock.patch.object(release, "windows_process_alive", return_value=True)
    def test_process_alive_uses_non_signalling_windows_probe(
        self, windows_process_alive: mock.Mock
    ) -> None:
        with mock.patch.object(release.os, "name", "nt"):
            self.assertTrue(release.process_alive(123))

        windows_process_alive.assert_called_once_with(123)

    @mock.patch.object(release.time, "sleep")
    @mock.patch.object(
        release,
        "http_request",
        side_effect=[
            release.urllib.error.URLError("runtime is starting"),
            (200, b'{"status":"ready"}'),
        ],
    )
    @mock.patch.object(release, "process_alive", return_value=True)
    def test_wait_ready_retries_transient_connection_errors(
        self,
        process_alive: mock.Mock,
        http_request: mock.Mock,
        sleep: mock.Mock,
    ) -> None:
        release.wait_ready("http://127.0.0.1:18066", 123, Path("out"), Path("err"))

        self.assertEqual(2, process_alive.call_count)
        self.assertEqual(2, http_request.call_count)
        sleep.assert_called_once_with(1)

    @mock.patch.object(release.time, "sleep")
    def test_temporary_log_cleanup_retries_windows_handle_release(
        self, sleep: mock.Mock
    ) -> None:
        stdout_log = mock.Mock()
        stdout_log.unlink.side_effect = [PermissionError(), None]
        stderr_log = mock.Mock()

        release.remove_temporary_runtime_logs((stdout_log, stderr_log))

        self.assertEqual(2, stdout_log.unlink.call_count)
        stderr_log.unlink.assert_called_once_with(missing_ok=True)
        sleep.assert_called_once_with(0.1)

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

    def test_docs_only_authorization_and_maven_path_are_explicit(self) -> None:
        args = release.build_parser().parse_args(
            [
                "--release-version",
                "0.1.21",
                "--maven-executable",
                "C:/tools/maven/mvn.cmd",
                "--authorized-docs-only-skip-tests",
                "--docs-only-base-ref",
                "foggy-runtime-launcher-v0.1.20",
            ]
        )

        self.assertEqual("C:/tools/maven/mvn.cmd", args.maven_executable)
        self.assertTrue(args.authorized_docs_only_skip_tests)
        self.assertEqual(
            "foggy-runtime-launcher-v0.1.20", args.docs_only_base_ref
        )

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
