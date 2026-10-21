from __future__ import annotations

import gzip
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import time
import unittest
import zipfile


TOOL_PATH = Path(__file__).resolve().parents[1] / "release_authority_tool.py"
SPEC = importlib.util.spec_from_file_location("v950_release_authority_tool", TOOL_PATH)
assert SPEC and SPEC.loader
tool = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = tool
SPEC.loader.exec_module(tool)


class ReleaseAuthorityToolTest(unittest.TestCase):
    def setUp(self) -> None:
        self.repo = Path(__file__).resolve().parents[3]

    def _frozen_reactor(self, root: Path) -> Path:
        fixture = Path(__file__).parent / "fixtures/v950-reactor.pom.xml"
        (root / "pom.xml").write_bytes(fixture.read_bytes())
        for module in tool.xml_modules(root):
            pom = root / module / "pom.xml"
            pom.parent.mkdir(parents=True)
            pom.write_text("<project/>\n", encoding="utf-8")
        provisioner = tool.contract()["database_replay"]["provisioner"]["path"]
        target = root / provisioner
        target.parent.mkdir(parents=True)
        # Frozen source archives use LF, including on Windows checkouts.
        target.write_bytes((self.repo / provisioner).read_bytes().replace(b"\r\n", b"\n"))
        return root

    def test_frozen_contract_matches_signed_off_reactor_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            receipt = tool.validate_contract(self._frozen_reactor(Path(raw)))
        self.assertEqual(31, receipt["modules"])
        self.assertEqual(32, receipt["projects"])
        self.assertEqual("passed", receipt["status"])

    def _assert_reactor_mutation_rejected(self, mutation) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = self._frozen_reactor(Path(raw))
            pom = root / "pom.xml"
            pom.write_text(mutation(pom.read_text(encoding="utf-8")), encoding="utf-8")
            with self.assertRaisesRegex(tool.AuthorityError, "active root reactor differs from contract"):
                tool.validate_contract(root)

    def test_added_module_is_rejected(self) -> None:
        self._assert_reactor_mutation_rejected(lambda xml: xml.replace(
            "</modules>", "<module>new-module</module></modules>"))

    def test_removed_module_is_rejected(self) -> None:
        self._assert_reactor_mutation_rejected(lambda xml: xml.replace(
            "<module>foggy-core</module>", ""))

    def test_reordered_modules_are_rejected(self) -> None:
        self._assert_reactor_mutation_rejected(lambda xml: xml.replace(
            "<module>foggy-bean-copy</module>\n    <module>foggy-core</module>",
            "<module>foggy-core</module>\n    <module>foggy-bean-copy</module>"))

    def test_missing_module_pom_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = self._frozen_reactor(Path(raw))
            (root / "foggy-core/pom.xml").unlink()
            with self.assertRaisesRegex(tool.AuthorityError, "module POM missing: foggy-core"):
                tool.validate_contract(root)

    def test_changed_provisioner_digest_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = self._frozen_reactor(Path(raw))
            path = tool.contract()["database_replay"]["provisioner"]["path"]
            (root / path).write_text("changed\n", encoding="utf-8")
            with self.assertRaisesRegex(tool.AuthorityError, "pinned database provisioner digest differs"):
                tool.validate_contract(root)

    def test_cli_rejects_newer_reactor_without_success_receipt(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = self._frozen_reactor(Path(raw))
            pom = root / "pom.xml"
            pom.write_text(pom.read_text(encoding="utf-8").replace(
                "</modules>", "<module>new-module</module></modules>"), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(TOOL_PATH), "validate-contract", "--repo-root", str(root)],
                capture_output=True, text=True, check=False,
            )
            self.assertNotEqual(0, result.returncode)
            self.assertEqual("", result.stdout)
            self.assertIn("historical 9.5.0 authority requires 31 ordered modules; found 32", result.stderr)

    def test_frozen_totals_are_exact(self) -> None:
        contract = tool.contract()
        semantic, semantic_total = tool.resolved_reports(contract, "semantic")
        self.assertEqual(63, sum(semantic.values()))
        self.assertEqual(63, semantic_total)
        database_total = sum(
            tool.resolved_reports(contract, row["key"])[1]
            for row in contract["database_replay"]["variants"]
        )
        self.assertEqual(370, database_total)

    def test_archive_path_traversal_is_rejected(self) -> None:
        with self.assertRaises(tool.AuthorityError):
            tool.safe_archive_name(
                "foggy-data-mcp-bridge-9.5.0/../escape",
                "foggy-data-mcp-bridge-9.5.0",
            )

    def _write_report(self, root: Path, name: str, tests: int) -> None:
        cases = "".join(
            f'<testcase name="case-{index}" classname="{name}"/>'
            for index in range(tests)
        )
        payload = (
            f'<testsuite name="{name}" tests="{tests}" failures="0" '
            f'errors="0" skipped="0">{cases}</testsuite>'
        )
        (root / f"TEST-{name}.xml").write_text(payload, encoding="utf-8")

    def _valid_junit_receipt(
        self, lane: str, candidate: str
    ) -> dict[str, object]:
        reports, total = tool.resolved_reports(tool.contract(), lane)
        return {
            "schema_version": 1,
            "kind": "v950-junit-receipt",
            "lane": lane,
            "candidate": candidate,
            "contract_sha256": tool.sha256_file(tool.CONTRACT_PATH),
            "reports": {
                name: {
                    "file": f"TEST-{name}.xml",
                    "tests": tests,
                    "failures": 0,
                    "errors": 0,
                    "skipped": 0,
                    "sha256": "1" * 64,
                }
                for name, tests in reports.items()
            },
            "totals": {
                "tests": total,
                "failures": 0,
                "errors": 0,
                "skipped": 0,
            },
            "status": "passed",
        }

    def _valid_final_corpus(
        self, root: Path, candidate: str
    ) -> tuple[Path, Path, list[str]]:
        source = {
            "schema_version": 1,
            "kind": "v950-source-seal",
            "candidate": candidate,
            "file_count": 1,
            "inventory_sha256": "2" * 64,
            "status": "passed",
        }
        before = root / "before.json"
        after = root / "after.json"
        before.write_text(json.dumps(source), encoding="utf-8")
        after.write_text(json.dumps(source), encoding="utf-8")
        values: dict[str, dict[str, object]] = {
            "root": {
                "schema_version": 1,
                "kind": "v950-root-reactor-receipt",
                "candidate": candidate,
                "contract_sha256": tool.sha256_file(tool.CONTRACT_PATH),
                "projects": 32,
                "launcher_jar_sha256": "3" * 64,
                "status": "passed",
            },
            "archive": {
                "schema_version": 1,
                "kind": "v950-source-archive",
                "candidate": candidate,
                "contract_sha256": tool.sha256_file(tool.CONTRACT_PATH),
                "archive": tool.contract()["portable_replay"]["archive_name"],
                "archive_sha256": "4" * 64,
                "archive_size": 1,
                "tracked_file_count": 1,
                "status": "passed",
            },
            "archive-extraction": {
                "schema_version": 1,
                "kind": "v950-source-archive-extraction",
                "candidate": candidate,
                "archive_sha256": "4" * 64,
                "destination": "/dev/shm/foggy-v950-test",
                "files": 2,
                "cross_filesystem": True,
                "status": "passed",
            },
            "sensitive-scan": {
                "schema_version": 1,
                "kind": "v950-evidence-sensitive-scan",
                "files": 1,
                "patterns": 3,
                "excluded_bound_archives": 1,
                "status": "passed",
            },
        }
        for key, lane in tool.JUNIT_EVIDENCE_LANES.items():
            values[key] = self._valid_junit_receipt(lane, candidate)
        for key, database in tool.CELL_EVIDENCE_DATABASES.items():
            values[key] = {
                "schema_version": 1,
                "kind": "v950-database-cell-receipt",
                "candidate": candidate,
                "database": database,
                "fixture_sha256": "5" * 64,
                "cleanup": "passed",
                "status": "passed",
            }
        bindings: list[str] = []
        for key, value in values.items():
            path = root / f"{key}.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            bindings.append(f"{key}={path}")
        return before, after, bindings

    def test_junit_report_set_is_exact_and_fresh(self) -> None:
        contract = tool.contract()
        reports, _ = tool.resolved_reports(contract, "semantic")
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            marker = root / "marker"
            marker.touch()
            time.sleep(0.01)
            report_root = root / "reports"
            report_root.mkdir()
            for name, tests in reports.items():
                self._write_report(report_root, name, tests)
            receipt = root / "receipt.json"
            value = tool.junit_summary(
                report_root,
                "semantic",
                "0" * 40,
                marker,
                receipt,
            )
            self.assertEqual(63, value["totals"]["tests"])
            self._write_report(report_root, "unexpected.Report", 1)
            with self.assertRaises(tool.AuthorityError):
                tool.junit_summary(
                    report_root,
                    "semantic",
                    "0" * 40,
                    marker,
                    root / "second.json",
                )

    def test_archive_candidate_tamper_is_rejected(self) -> None:
        prefix = "foggy-data-mcp-bridge-9.5.0"
        marker = {
            "schema_version": 1,
            "kind": "v950-source-archive-candidate",
            "version": "9.5.0",
            "candidate": "1" * 40,
            "contract_sha256": tool.sha256_file(tool.CONTRACT_PATH),
            "tracked_file_count": 0,
        }
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            archive_path = root / "candidate.tar.gz"
            with archive_path.open("wb") as stream:
                with gzip.GzipFile(filename="", mode="wb", fileobj=stream, mtime=0) as gz:
                    with tarfile.open(fileobj=gz, mode="w") as archive:
                        data = tool.canonical_json(marker)
                        info = tarfile.TarInfo(
                            f"{prefix}/.v950-release-candidate.json"
                        )
                        info.size = len(data)
                        archive.addfile(info, io.BytesIO(data))
            # Marker validation precedes the cross-filesystem check. A normal
            # temporary directory exercises that exact rejection on every OS.
            with self.assertRaisesRegex(tool.AuthorityError, "archive candidate marker differs"):
                tool.extract_archive(
                    archive_path, root / "tampered", "2" * 40, root / "receipt.json",
                )
            self.assertFalse((root / "receipt.json").exists())
            with self.assertRaisesRegex(tool.AuthorityError, "portable extraction did not cross filesystems"):
                tool.extract_archive(
                    archive_path, root / "same-filesystem", "1" * 40, root / "same-fs.json",
                )
            self.assertFalse((root / "same-fs.json").exists())

    def test_root_summary_requires_exact_reactor_and_jar_boundary(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            log = root / "root.log"
            lines = [
                f"[INFO] project-{index:02d} ........................ SUCCESS [  0.100 s]"
                for index in range(32)
            ]
            lines.append("[INFO] BUILD SUCCESS")
            log.write_text("\n".join(lines) + "\n", encoding="utf-8")
            jar = root / "launcher.jar"
            with zipfile.ZipFile(jar, "w") as archive:
                archive.writestr(
                    "BOOT-INF/lib/foggy-dataset-model-engine-9.1.0.beta.jar", b"engine"
                )
                archive.writestr(
                    "org/springframework/boot/loader/launch/JarLauncher.class",
                    b"loader",
                )
            receipt = root / "receipt.json"
            value = tool.root_summary(log, jar, "0" * 40, receipt)
            self.assertEqual(32, value["projects"])
            self.assertEqual("passed", value["status"])

    def test_reuse_accepts_governance_only_ancestor_chain(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            subprocess.run(
                ["git", "-C", str(root), "config", "user.email", "test@example.invalid"],
                check=True,
            )
            subprocess.run(
                ["git", "-C", str(root), "config", "user.name", "Authority Test"],
                check=True,
            )
            script = root / "scripts/v950/tool.py"
            script.parent.mkdir(parents=True)
            script.write_text("version = 1\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "base"], check=True)
            source_candidate = subprocess.run(
                ["git", "-C", str(root), "rev-parse", "HEAD"],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            ).stdout.strip()
            script.write_text("version = 2\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "tool fix"], check=True)
            workitem = root / tool.REUSE_WORKITEM
            workitem.parent.mkdir(parents=True)
            workitem.write_text("status: executing\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "governance"], check=True)
            candidate = subprocess.run(
                ["git", "-C", str(root), "rev-parse", "HEAD"],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            ).stdout.strip()
            source = root / "root-receipt.reused-source.json"
            source.write_text(
                json.dumps(
                    {
                        "kind": "v950-root-reactor-receipt",
                        "candidate": source_candidate,
                        "contract_sha256": tool.sha256_file(tool.CONTRACT_PATH),
                        "projects": 32,
                        "launcher_jar_sha256": "1" * 64,
                        "status": "passed",
                    }
                ),
                encoding="utf-8",
            )
            output = root / "root-receipt.json"
            value = tool.reuse_receipt(root, source, "root", candidate, output)
            self.assertEqual(
                sorted([script.relative_to(root).as_posix(), tool.REUSE_WORKITEM]),
                value["changed_paths"],
            )

            docs = root / "docs/status.md"
            docs.parent.mkdir(exist_ok=True)
            docs.write_text("changed\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "docs"], check=True)
            next_candidate = subprocess.run(
                ["git", "-C", str(root), "rev-parse", "HEAD"],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            ).stdout.strip()
            next_source = root / "next-receipt.reused-source.json"
            next_source.write_text(
                json.dumps(
                    {
                        "kind": "v950-root-reactor-receipt",
                        "candidate": candidate,
                        "contract_sha256": tool.sha256_file(tool.CONTRACT_PATH),
                        "projects": 32,
                        "launcher_jar_sha256": "1" * 64,
                        "status": "passed",
                    }
                ),
                encoding="utf-8",
            )
            with self.assertRaises(tool.AuthorityError):
                tool.reuse_receipt(
                    root,
                    next_source,
                    "root",
                    next_candidate,
                    root / "next-receipt.json",
                )

    def test_reuse_rejects_frozen_contract_change(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            subprocess.run(
                ["git", "-C", str(root), "config", "user.email", "test@example.invalid"],
                check=True,
            )
            subprocess.run(
                ["git", "-C", str(root), "config", "user.name", "Authority Test"],
                check=True,
            )
            contract = root / "scripts/v950/release-authority-contract.json"
            contract.parent.mkdir(parents=True)
            contract.write_text("{}\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "base"], check=True)
            source = subprocess.run(
                ["git", "-C", str(root), "rev-parse", "HEAD"],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            ).stdout.strip()
            contract.write_text('{"changed":true}\n', encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "."], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "contract"], check=True)
            candidate = subprocess.run(
                ["git", "-C", str(root), "rev-parse", "HEAD"],
                check=True,
                text=True,
                stdout=subprocess.PIPE,
            ).stdout.strip()
            with self.assertRaises(tool.AuthorityError):
                tool.reuse_changed_paths(root, source, candidate)

    def test_runner_reuse_packages_only_json_receipts(self) -> None:
        script = (
            Path(__file__).resolve().parents[1] / "verify-release-authority.sh"
        ).read_text(encoding="utf-8")
        self.assertIn("--reuse-product-from", script)
        self.assertNotIn(
            'cp -p -- "$RESUME_FROM/root-clean-verify.log"', script
        )
        self.assertNotIn('cp -a -- "$RESUME_FROM/semantic/."', script)
        self.assertNotIn(
            'cp -a -- "$RESUME_FROM/database/variants/db-sqlite/."', script
        )

    def test_final_manifest_accepts_exact_semantics(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            candidate = "0" * 40
            before, after, bindings = self._valid_final_corpus(root, candidate)
            value = tool.finalize(
                candidate,
                before,
                after,
                bindings,
                root / "final.json",
                self.repo,
            )
            self.assertEqual("passed", value["status"])

    def test_final_manifest_rejects_swapped_lane(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            candidate = "0" * 40
            before, after, bindings = self._valid_final_corpus(root, candidate)
            semantic = root / "semantic.json"
            value = json.loads(semantic.read_text(encoding="utf-8"))
            value["lane"] = "portable"
            semantic.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(tool.AuthorityError):
                tool.finalize(
                    candidate,
                    before,
                    after,
                    bindings,
                    root / "final.json",
                    self.repo,
                )

    def test_final_manifest_rejects_tampered_totals(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            candidate = "0" * 40
            before, after, bindings = self._valid_final_corpus(root, candidate)
            semantic = root / "semantic.json"
            value = json.loads(semantic.read_text(encoding="utf-8"))
            value["totals"]["tests"] -= 1
            semantic.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(tool.AuthorityError):
                tool.finalize(
                    candidate,
                    before,
                    after,
                    bindings,
                    root / "final.json",
                    self.repo,
                )

    def test_final_manifest_rejects_archive_extraction_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            candidate = "0" * 40
            before, after, bindings = self._valid_final_corpus(root, candidate)
            extraction = root / "archive-extraction.json"
            value = json.loads(extraction.read_text(encoding="utf-8"))
            value["archive_sha256"] = "9" * 64
            extraction.write_text(json.dumps(value), encoding="utf-8")
            with self.assertRaises(tool.AuthorityError):
                tool.finalize(
                    candidate,
                    before,
                    after,
                    bindings,
                    root / "final.json",
                    self.repo,
                )

    def test_final_manifest_requires_complete_receipt_set(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            source = {
                "schema_version": 1,
                "kind": "v950-source-seal",
                "candidate": "0" * 40,
                "file_count": 1,
                "inventory_sha256": "1" * 64,
                "status": "passed",
            }
            before = root / "before.json"
            after = root / "after.json"
            before.write_text(json.dumps(source), encoding="utf-8")
            after.write_text(json.dumps(source), encoding="utf-8")
            with self.assertRaises(tool.AuthorityError):
                tool.finalize(
                    "0" * 40,
                    before,
                    after,
                    [],
                    root / "final.json",
                    self.repo,
                )


if __name__ == "__main__":
    unittest.main()
