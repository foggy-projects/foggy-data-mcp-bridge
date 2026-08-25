from __future__ import annotations

import io
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock


RELEASE_SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RELEASE_SCRIPT_DIR))

import runtime_launcher_package as release  # noqa: E402


class RuntimeLauncherPackageTest(unittest.TestCase):

    def test_release_sources_embed_console_as_standard_launcher_dependency(self) -> None:
        release.verify_release_sources()

    def test_release_build_cleans_console_then_packages_affected_reactor(self) -> None:
        clean, package = release.maven_build_commands("mvn", skip_java_tests=True)

        self.assertEqual(
            clean,
            [
                "mvn",
                "-pl",
                "addons/foggy-analytics-console,foggy-mcp-launcher",
                "clean",
            ],
        )
        self.assertEqual(
            package[:7],
            [
                "mvn",
                "-Pruntime-api",
                "-pl",
                "foggy-mcp-launcher",
                "-am",
                "clean",
                "package",
            ],
        )
        self.assertNotIn("-Dfoggy.analytics-console.frontend.skip=true", package)

    def test_preserves_committed_root_target_evidence_across_maven_clean(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            repository_root = Path(directory)
            candidate = repository_root / "target" / "release" / "candidate.json"
            report = repository_root / "target" / "release" / "final" / "report.json"
            candidate.parent.mkdir(parents=True)
            report.parent.mkdir(parents=True)
            candidate.write_bytes(b"candidate\n")
            report.write_bytes(b"report\n")
            candidate.chmod(0o640)

            with (
                mock.patch.object(release, "REPOSITORY_ROOT", repository_root),
                mock.patch.object(
                    release,
                    "_git_output",
                    return_value="target/release/candidate.json\n"
                    "target/release/final/report.json",
                ),
            ):
                snapshot = release._snapshot_tracked_root_target_files()
                candidate.unlink()
                report.write_bytes(b"changed\n")

                release._restore_tracked_root_target_files(snapshot)

            self.assertEqual(candidate.read_bytes(), b"candidate\n")
            self.assertEqual(report.read_bytes(), b"report\n")
            self.assertEqual(candidate.stat().st_mode & 0o777, 0o640)

    def test_verifies_nested_console_static_assets_and_fap_delivery(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            self._write_launcher(jar)

            evidence = release.verify_launcher_jar(jar)

            self.assertTrue(evidence.analytics_console.path.startswith("BOOT-INF/lib/"))
            self.assertTrue(evidence.analytics_runtime_api.path.startswith("BOOT-INF/lib/"))
            self.assertIn(
                "META-INF/foggy-analytics-console/assets/index-current.js",
                evidence.console_assets,
            )

    def test_rejects_stale_console_index_assets(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            self._write_launcher(jar, stale_asset=True)

            with self.assertRaisesRegex(
                release.ReleaseValidationError, "stale, unreferenced index assets"
            ):
                release.verify_launcher_jar(jar)

    def test_rejects_launcher_without_console(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            with zipfile.ZipFile(jar, "w") as outer:
                outer.writestr("BOOT-INF/classes/application-analytics-console.yml", "foggy: {}")
                outer.writestr(
                    "BOOT-INF/lib/foggy-analytics-runtime-api-9.3.0-SNAPSHOT.jar",
                    self._empty_jar(),
                )

            with self.assertRaisesRegex(
                release.ReleaseValidationError,
                "exactly one embedded foggy-analytics-console JAR",
            ):
                release.verify_launcher_jar(jar)

    @staticmethod
    def _empty_jar() -> bytes:
        content = io.BytesIO()
        with zipfile.ZipFile(content, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        return content.getvalue()

    @classmethod
    def _console_jar(cls, stale_asset: bool) -> bytes:
        content = io.BytesIO()
        index = (
            '<script src="/analytics-console/theme-init.js"></script>'
            '<script type="module" src="/analytics-console/assets/index-current.js"></script>'
            '<link rel="stylesheet" href="/analytics-console/assets/index-current.css">'
        )
        with zipfile.ZipFile(content, "w") as archive:
            archive.writestr(
                "META-INF/foggy-analytics-console/index.html", index
            )
            archive.writestr(
                "META-INF/foggy-analytics-console/theme-init.js", "// theme"
            )
            archive.writestr(
                "META-INF/foggy-analytics-console/assets/index-current.js", "// app"
            )
            archive.writestr(
                "META-INF/foggy-analytics-console/assets/index-current.css", "/* app */"
            )
            if stale_asset:
                archive.writestr(
                    "META-INF/foggy-analytics-console/assets/index-stale.js", "// stale"
                )
            archive.writestr("fap/analytics-question-answering/SKILL.md", "# Skill")
            archive.writestr(
                "fap/analytics-question-answering/function-schema-delivery.json", "{}"
            )
            archive.writestr(
                "fap/analytics-question-answering/references/query-model-dsl.md", "# DSL"
            )
            archive.writestr(
                "fap/analytics-question-answering/references/compose-script.md", "# Compose"
            )
        return content.getvalue()

    @classmethod
    def _write_launcher(cls, path: Path, stale_asset: bool = False) -> None:
        with zipfile.ZipFile(path, "w") as outer:
            outer.writestr(
                "BOOT-INF/classes/application-analytics-console.yml", "foggy: {}"
            )
            outer.writestr(
                "BOOT-INF/lib/foggy-analytics-console-9.3.0-SNAPSHOT.jar",
                cls._console_jar(stale_asset),
            )
            outer.writestr(
                "BOOT-INF/lib/foggy-analytics-runtime-api-9.3.0-SNAPSHOT.jar",
                cls._empty_jar(),
            )


if __name__ == "__main__":
    unittest.main()
