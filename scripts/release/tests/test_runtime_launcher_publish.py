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
        with self.assertRaisesRegex(
            release.ReleaseError, "invalid launcher release version"
        ):
            release.normalize_version("latest")

    def test_obs_replay_is_opt_in(self) -> None:
        parser = release.build_parser()
        default = parser.parse_args(["--release-version", "0.1.19"])
        requested = parser.parse_args(["--release-version", "0.1.19", "--verify-obs"])

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
        self.assertEqual("foggy-runtime-launcher-v0.1.20", args.docs_only_base_ref)

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

    def test_field_binding_smoke_failure_prevents_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, mock.patch.object(
            release.sys,
            "argv",
            [
                "publish",
                "--release-version",
                "0.1.28",
                "--reuse-package",
                "--output-dir",
                temporary,
            ],
        ), mock.patch.object(
            release, "command_output", return_value="a" * 40
        ), mock.patch.object(
            release, "verify_package", return_value={}
        ), mock.patch.object(
            release, "run_smoke", return_value=[]
        ), mock.patch.object(
            release,
            "run_field_binding_smoke",
            side_effect=release.ReleaseError("binding rejected"),
        ), mock.patch.object(
            release, "publish_release"
        ) as publish:
            self.assertEqual(1, release.main())
            publish.assert_not_called()

    def test_field_binding_smoke_requires_complete_matching_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            package = root / "package"
            package.mkdir()
            jar = package / "foggy-runtime-launcher-0.1.28.jar"
            jar.write_bytes(b"fixture jar")
            summary = {
                "status": "passed",
                "checks": [],
                "processStopped": True,
                "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
            }

            def fake_run(command):
                output = Path(command[-1])
                output.mkdir(parents=True)
                (output / "summary.json").write_text(json.dumps(summary))

            with mock.patch.object(release, "REPOSITORY_ROOT", root), mock.patch.object(
                release, "run", side_effect=fake_run
            ):
                with self.assertRaises(release.ReleaseError):
                    release.run_field_binding_smoke(package, "0.1.28")
                summary["checks"] = [str(n) for n in range(13)]
                summary["jarSha256"] = "wrong"
                with self.assertRaises(release.ReleaseError):
                    release.run_field_binding_smoke(package, "0.1.28")
                summary["jarSha256"] = hashlib.sha256(jar.read_bytes()).hexdigest()
                result = release.run_field_binding_smoke(package, "0.1.28")
                self.assertEqual("passed", result["status"])
                self.assertEqual(
                    1,
                    len(list(package.iterdir())),
                    "smoke evidence must not alter release assets",
                )

    def test_compose_failure_prevents_release_notes_and_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, mock.patch.object(
            release.sys, "argv", ["publish", "--release-version", "0.1.32",
                                  "--reuse-package", "--output-dir", temporary]
        ), mock.patch.object(release, "command_output", return_value="a" * 40), \
                mock.patch.object(release, "verify_package", return_value={}), \
                mock.patch.object(release, "run_smoke", return_value=[]), \
                mock.patch.object(release, "run_field_binding_smoke", return_value={}), \
                mock.patch.object(release, "run_compose_binding_smoke",
                                  side_effect=release.ReleaseError("Compose rejected")), \
                mock.patch.object(release, "release_notes") as notes, \
                mock.patch.object(release, "publish_release") as publish:
            self.assertEqual(1, release.main())
            notes.assert_not_called()
            publish.assert_not_called()

    def test_compose_gate_rejects_missing_cases_modes_hash_or_live_process(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            package = root / "package"
            package.mkdir()
            jar = package / "foggy-runtime-launcher-0.1.32.jar"
            jar.write_bytes(b"fixture jar")
            valid = {
                "status": "passed", "processStopped": True,
                "launcherSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
                "modes": ["validate", "preview", "execute"],
                "checks": sorted(release.COMPOSE_REQUIRED_CHECKS),
            }
            summaries = [
                {**valid, "checks": valid["checks"][1:]},
                {**valid, "checks": ["unrelated"] * len(valid["checks"])},
                {**valid, "checks": valid["checks"] + [valid["checks"][0]]},
                {**valid, "checks": [1]},
                {**valid, "modes": ["validate", "preview"]},
                {**valid, "launcherSha256": "wrong"},
                {**valid, "processStopped": False},
                {**valid, "status": "failed"},
                [],
            ]
            def write_summary(command):
                destination = Path(command[-1])
                destination.mkdir(parents=True)
                (destination / "summary.json").write_text(json.dumps(summary))
            with mock.patch.object(release, "REPOSITORY_ROOT", root), \
                    mock.patch.object(release, "run", side_effect=write_summary):
                for summary in summaries:
                    with self.subTest(summary=summary), self.assertRaises(release.ReleaseError):
                        release.run_compose_binding_smoke(package, "0.1.32")
                summary = valid
                result = release.run_compose_binding_smoke(package, "0.1.32")
                self.assertEqual("compose-binding", result["profile"])
                self.assertEqual("passed", result["status"])
                self.assertEqual([jar], list(package.iterdir()))

    def test_compose_gate_requires_fresh_summary_and_successful_process(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with mock.patch.object(release, "REPOSITORY_ROOT", root), \
                    mock.patch.object(release, "run") as run:
                with self.assertRaises(FileNotFoundError):
                    release.run_compose_binding_smoke(root, "0.1.32")
                old = Path(run.call_args.args[0][-1])
                old.mkdir(parents=True)
                (old / "summary.json").write_text('{"status":"passed"}')
                with self.assertRaises(FileNotFoundError):
                    release.run_compose_binding_smoke(root, "0.1.32")
                self.assertNotEqual(old, Path(run.call_args.args[0][-1]))
                run.side_effect = release.subprocess.CalledProcessError(1, ["smoke"])
                with self.assertRaises(release.subprocess.CalledProcessError):
                    release.run_compose_binding_smoke(root, "0.1.32")

    def test_stability_failure_prevents_notes_and_publication(self) -> None:
        with tempfile.TemporaryDirectory() as temporary, mock.patch.object(
            release.sys, "argv", ["publish", "--release-version", "0.1.45", "--reuse-package", "--output-dir", temporary]
        ), mock.patch.object(release, "command_output", return_value="a" * 40), \
                mock.patch.object(release, "verify_package", return_value={}), \
                mock.patch.object(release, "run_smoke", return_value=[]), \
                mock.patch.object(release, "run_field_binding_smoke", return_value={}), \
                mock.patch.object(release, "run_compose_binding_smoke", return_value={}), \
                mock.patch.object(release, "run_compose_stability_smoke", side_effect=release.ReleaseError("SQL counters missing")), \
                mock.patch.object(release, "release_notes") as notes, \
                mock.patch.object(release, "publish_release") as publish:
            self.assertEqual(1, release.main())
            notes.assert_not_called()
            publish.assert_not_called()

    def test_stability_publisher_requires_complete_counters_and_artifact_identity(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            package = root / "package"
            package.mkdir()
            jar = package / "foggy-runtime-launcher-0.1.45.jar"
            jar.write_bytes(b"immutable artifact")
            contract = release.COMPOSE_STABILITY_GATE
            valid = {"status": "passed", "processStopped": True, "modes": contract.MODES, "g10": False,
                     "counterFilterSelfTest": True, "launcherSha256": release.sha256_file(jar),
                     "checks": sorted(contract.REQUIRED_CHECKS),
                     "readonlySql": [{"case": case, "mode": mode, "prepared": 0, "executed": 0}
                                     for case in sorted(contract.READONLY_CASES) for mode in contract.MODES[:2]],
                     "positiveSql": [{"case": "native-plain-window", "mode": "execute", "prepared": 1, "executed": 1}]}
            def write_summary(command):
                evidence = Path(command[-1])
                evidence.mkdir(parents=True)
                (evidence / "summary.json").write_text(json.dumps(summary), encoding="utf-8")
            with mock.patch.object(release, "REPOSITORY_ROOT", root), mock.patch.object(release, "run", side_effect=write_summary):
                for summary in [{**valid, "status": "failed"}, {**valid, "readonlySql": []},
                                {**valid, "readonlySql": [{"case": "missing", "mode": "preview"}]},
                                {**valid, "positiveSql": []}, {**valid, "launcherSha256": "wrong"}]:
                    with self.subTest(summary=summary), self.assertRaises(release.ReleaseError):
                        release.run_compose_stability_smoke(package, "0.1.45")
                summary = valid
                self.assertEqual("compose-stability", release.run_compose_stability_smoke(package, "0.1.45")["profile"])
                self.assertEqual([jar], list(package.iterdir()))

    def test_stability_gate_requires_fresh_output_and_successful_process(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            with mock.patch.object(release, "REPOSITORY_ROOT", root), mock.patch.object(release, "run") as run:
                with self.assertRaises(FileNotFoundError):
                    release.run_compose_stability_smoke(root, "0.1.45")
                first = Path(run.call_args.args[0][-1])
                first.mkdir(parents=True)
                (first / "summary.json").write_text('{"status":"passed"}', encoding="utf-8")
                with self.assertRaises(FileNotFoundError):
                    release.run_compose_stability_smoke(root, "0.1.45")
                self.assertNotEqual(first, Path(run.call_args.args[0][-1]))
                run.side_effect = release.subprocess.CalledProcessError(1, ["gate"])
                with self.assertRaises(release.subprocess.CalledProcessError):
                    release.run_compose_stability_smoke(root, "0.1.45")

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
