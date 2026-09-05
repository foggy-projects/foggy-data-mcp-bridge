from __future__ import annotations

import io
import json
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

    def test_candidate_build_runs_focused_java_lane_and_keeps_frontend_build(self) -> None:
        _, package = release.maven_build_commands(
            "mvn", skip_java_tests=False, candidate=True
        )

        self.assertIn(
            "-Dtest=" + ",".join(release.CANDIDATE_JAVA_TESTS), package
        )
        self.assertIn(
            "-Dfoggy.analytics-console.frontend.test.skip=true", package
        )
        self.assertNotIn("-Dfoggy.analytics-console.frontend.skip=true", package)
        self.assertNotIn("-Dmaven.test.skip=true", package)

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
            expected_mode = candidate.stat().st_mode & 0o777

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
            self.assertEqual(candidate.stat().st_mode & 0o777, expected_mode)

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

    def test_rejects_incomplete_function_schema_delivery(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            functions = list(release.EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY[:-1])
            self._write_launcher(jar, delivered_functions=functions)

            with self.assertRaisesRegex(
                release.ReleaseValidationError,
                "exact governed FunctionRef set",
            ):
                release.verify_launcher_jar(jar)

    def test_rejects_non_inline_function_schema_delivery(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            self._write_launcher(jar, non_inline_function=True)

            with self.assertRaisesRegex(
                release.ReleaseValidationError,
                "must use INLINE schema delivery",
            ):
                release.verify_launcher_jar(jar)

    def test_rejects_host_publication_digest_drift(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            self._write_launcher(jar, publication_digest_drift=True)

            with self.assertRaisesRegex(
                release.ReleaseValidationError,
                "host publication Function digest mismatch",
            ):
                release.verify_launcher_jar(jar)

    def test_rejects_skill_revision_drift_between_metadata_and_host_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            self._write_launcher(jar, skill_revision_drift=True)

            with self.assertRaisesRegex(
                release.ReleaseValidationError,
                "Skill identity does not match skill-metadata.json",
            ):
                release.verify_launcher_jar(jar)

    def test_rejects_forbidden_model_version_marker_in_analytics_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            jar = Path(directory) / "launcher.jar"
            self._write_launcher(jar, forbidden_marker=True)

            with self.assertRaisesRegex(
                release.ReleaseValidationError,
                "forbidden caller-visible model version marker",
            ):
                release.verify_launcher_jar(jar)

    @staticmethod
    def _empty_jar(marker: bytes | None = None) -> bytes:
        content = io.BytesIO()
        with zipfile.ZipFile(content, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
            if marker is not None:
                archive.writestr("contract.bin", marker)
        return content.getvalue()

    @classmethod
    def _console_jar(
            cls,
            stale_asset: bool,
            delivered_functions: list[str] | None = None,
            non_inline_function: bool = False,
            publication_digest_drift: bool = False,
            skill_revision_drift: bool = False) -> bytes:
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
            function_refs = list(
                delivered_functions
                if delivered_functions is not None
                else release.EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY
            )
            archive.writestr(
                "fap/analytics-question-answering/SKILL.md",
                "# Skill\n" + "\n".join(function_refs),
            )
            functions = [
                {
                    "functionRef": function_ref,
                    "mode": (
                        "ON_DEMAND"
                        if non_inline_function and index == 0
                        else "INLINE"
                    ),
                }
                for index, function_ref in enumerate(function_refs)
            ]
            archive.writestr(
                "fap/analytics-question-answering/function-schema-delivery.json",
                json.dumps(
                    {
                        "contractVersion": "fap.langbiz.function-schema-delivery.v1",
                        "functions": functions,
                    }
                ),
            )
            publications = []
            for function_ref in function_refs:
                schema_digest, projection_digest = (
                    release.EXPECTED_FAP_FUNCTION_DIGESTS.get(
                        function_ref, ("sha256:" + "0" * 64, "sha256:" + "1" * 64)
                    )
                )
                publications.append(
                    {
                        "functionRef": function_ref,
                        "schemaDigest": (
                            "sha256:" + "f" * 64
                            if publication_digest_drift and not publications
                            else schema_digest
                        ),
                        "projectionDigest": projection_digest,
                    }
                )
            archive.writestr(
                "fap/analytics-question-answering/host-publication-manifest.json",
                json.dumps(
                    {
                        "contractVersion": "foggy.analytics.question-host-publication.v1",
                        "publicationMode": "HOST_MANAGED_EXPLICIT",
                        "launcherStartupMutationAllowed": False,
                        "skill": {
                            "name": "analytics-question-answering",
                            "revision": 6 if skill_revision_drift else 7,
                        },
                        "functions": publications,
                    }
                ),
            )
            archive.writestr(
                "fap/analytics-question-answering/skill-metadata.json",
                json.dumps(
                    {
                        "contractVersion": "fap.skill.metadata.v1",
                        "skillId": "skill.analytics-console-question-answering-v1",
                        "name": "analytics-question-answering",
                        "revision": 7,
                        "title": "Analytics question answering",
                        "description": "Governed Analytics guidance.",
                        "entryDocumentPath": "SKILL.md",
                    }
                ),
            )
            archive.writestr(
                "fap/analytics-question-answering/references/query-model-dsl.md", "# DSL"
            )
            archive.writestr(
                "fap/analytics-question-answering/references/compose-script.md", "# Compose"
            )
        return content.getvalue()

    @classmethod
    def _write_launcher(
            cls,
            path: Path,
            stale_asset: bool = False,
            delivered_functions: list[str] | None = None,
            non_inline_function: bool = False,
            forbidden_marker: bool = False,
            publication_digest_drift: bool = False,
            skill_revision_drift: bool = False) -> None:
        with zipfile.ZipFile(path, "w") as outer:
            outer.writestr(
                "BOOT-INF/classes/application-analytics-console.yml", "foggy: {}"
            )
            outer.writestr(
                "BOOT-INF/lib/foggy-analytics-console-9.3.0-SNAPSHOT.jar",
                cls._console_jar(
                    stale_asset,
                    delivered_functions=delivered_functions,
                    non_inline_function=non_inline_function,
                    publication_digest_drift=publication_digest_drift,
                    skill_revision_drift=skill_revision_drift,
                ),
            )
            outer.writestr(
                "BOOT-INF/lib/foggy-analytics-runtime-api-9.3.0-SNAPSHOT.jar",
                cls._empty_jar(
                    b"MODEL_REVISION_CONFLICT" if forbidden_marker else None
                ),
            )


if __name__ == "__main__":
    unittest.main()
