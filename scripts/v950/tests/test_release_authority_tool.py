from __future__ import annotations

import gzip
import importlib.util
import io
import json
from pathlib import Path
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

    def test_live_contract_matches_current_reactor(self) -> None:
        receipt = tool.validate_contract(self.repo)
        self.assertEqual(31, receipt["modules"])
        self.assertEqual(32, receipt["projects"])
        self.assertEqual("passed", receipt["status"])

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
            destination = Path(
                tempfile.mkdtemp(prefix="foggy-v950-negative-", dir="/dev/shm")
            )
            destination.rmdir()
            try:
                with self.assertRaises(tool.AuthorityError):
                    tool.extract_archive(
                        archive_path,
                        destination,
                        "2" * 40,
                        root / "receipt.json",
                    )
            finally:
                if destination.exists():
                    for path in sorted(destination.rglob("*"), reverse=True):
                        if path.is_file() or path.is_symlink():
                            path.unlink()
                        elif path.is_dir():
                            path.rmdir()
                    destination.rmdir()

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

    def test_final_manifest_requires_complete_receipt_set(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            root = Path(raw)
            source = {
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
                )


if __name__ == "__main__":
    unittest.main()
