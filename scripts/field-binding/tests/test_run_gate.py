import importlib.util
from pathlib import Path
import tempfile, unittest
from unittest import mock

spec = importlib.util.spec_from_file_location(
    "field_binding_gate", Path(__file__).parents[1] / "run_gate.py"
)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class GateEvidenceTest(unittest.TestCase):
    def report(self, folder, name="Required", tests=1, skipped=0):
        path = folder / ("TEST-" + name + ".xml")
        path.write_text(
            f'<testsuite name="example.{name}" tests="{tests}" failures="0" errors="0" skipped="{skipped}"/>'
        )
        return path

    def test_zero_tests_cannot_pass_even_when_maven_exits_zero(self):
        with tempfile.TemporaryDirectory() as tmp:
            summary = gate.summarize(Path(tmp), 0, ["Required"])
            with self.assertRaises(RuntimeError):
                gate.verify(summary, 0)

    def test_missing_required_class_is_not_hidden_by_other_passed_tests(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.report(Path(tmp), "Other", 100)
            summary = gate.summarize(Path(tmp), 0, ["Required"])
            self.assertEqual(["Required"], summary["missingClasses"])
            with self.assertRaises(RuntimeError):
                gate.verify(summary, 0)

    def test_old_reports_do_not_satisfy_current_run(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = self.report(Path(tmp))
            summary = gate.summarize(Path(tmp), path.stat().st_mtime + 1, ["Required"])
            with self.assertRaises(RuntimeError):
                gate.verify(summary, 0)

    def test_skips_and_nonzero_exit_are_failures(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.report(Path(tmp), skipped=1)
            summary = gate.summarize(Path(tmp), 0, ["Required"])
            with self.assertRaises(RuntimeError):
                gate.verify(summary, 0)
            self.report(Path(tmp))
            summary = gate.summarize(Path(tmp), 0, ["Required"])
            with self.assertRaises(RuntimeError):
                gate.verify(summary, 1)
            gate.verify(summary, 0)

    def test_nested_suites_satisfy_required_class_and_are_counted(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.report(Path(tmp), "Required", 0)
            self.report(Path(tmp), "Required$Cases", 3)
            summary = gate.summarize(Path(tmp), 0, ["Required"])
            self.assertEqual(3, summary["tests"])
            gate.verify(summary, 0)

    def test_external_database_never_silently_falls_back_to_sqlite(self):
        with mock.patch.dict(gate.os.environ, {}, clear=True):
            with self.assertRaises(RuntimeError):
                with gate.database("mysql8", True, Path(".")):
                    self.fail("must reject incomplete connection")


if __name__ == "__main__":
    unittest.main()
