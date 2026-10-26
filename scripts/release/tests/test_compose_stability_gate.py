from __future__ import annotations

import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).resolve().parents[1] / "compose_stability_gate.py"
SPEC = importlib.util.spec_from_file_location("compose_stability_gate_contract_test", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
gate = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(gate)


def valid_summary():
    return {"status": "passed", "processStopped": True, "launcherSha256": "expected",
            "modes": gate.MODES, "g10": False, "counterFilterSelfTest": True,
            "checks": sorted(gate.REQUIRED_CHECKS),
            "readonlySql": [{"case": case, "mode": mode, "prepared": 0, "executed": 0}
                            for case in sorted(gate.READONLY_CASES) for mode in gate.MODES[:2]],
            "positiveSql": [{"case": "native-plain-window", "mode": "execute", "prepared": 1, "executed": 1}]}


class ComposeStabilityGateTest(unittest.TestCase):
    def test_complete_probe_is_accepted(self):
        gate.validate_summary(valid_summary(), "expected")

    def test_missing_or_failed_probe_and_wrong_artifact_are_rejected(self):
        for key, invalid in [("status", "failed"), ("processStopped", False), ("processStopped", 1),
                             ("launcherSha256", "wrong"), ("counterFilterSelfTest", False),
                             ("g10", True), ("modes", ["validate", "preview"]),
                             ("checks", []), ("readonlySql", []), ("positiveSql", [])]:
            summary = valid_summary()
            summary[key] = invalid
            with self.subTest(key=key, invalid=invalid), self.assertRaises(gate.GateError):
                gate.validate_summary(summary, "expected")
        for key in ["readonlySql", "positiveSql", "counterFilterSelfTest"]:
            summary = valid_summary()
            del summary[key]
            with self.subTest(missing=key), self.assertRaises(gate.GateError):
                gate.validate_summary(summary, "expected")
        with self.assertRaises(gate.GateError):
            gate.validate_summary([], "expected")

    def test_readonly_counts_must_be_explicit_zero_in_both_modes(self):
        for key in ["prepared", "executed"]:
            for value in [None, True, False, "0", -1, 1]:
                summary = valid_summary()
                summary["readonlySql"][0][key] = value
                with self.subTest(key=key, value=value), self.assertRaises(gate.GateError):
                    gate.validate_summary(summary, "expected")
            summary = valid_summary()
            del summary["readonlySql"][0][key]
            with self.assertRaises(gate.GateError):
                gate.validate_summary(summary, "expected")
        summary = valid_summary()
        summary["readonlySql"].pop()
        with self.assertRaisesRegex(gate.GateError, "mode matrix"):
            gate.validate_summary(summary, "expected")
        summary = valid_summary()
        summary["readonlySql"].append(copy.deepcopy(summary["readonlySql"][0]))
        with self.assertRaisesRegex(gate.GateError, "duplicated"):
            gate.validate_summary(summary, "expected")

    def test_zero_or_missing_positive_control_cannot_prove_instrumentation(self):
        for key in ["prepared", "executed"]:
            for value in [None, True, "1", 0, -1]:
                summary = valid_summary()
                summary["positiveSql"][0][key] = value
                with self.subTest(key=key, value=value), self.assertRaises(gate.GateError):
                    gate.validate_summary(summary, "expected")
        summary = valid_summary()
        summary["positiveSql"][0]["case"] = "unrelated"
        with self.assertRaisesRegex(gate.GateError, "native time-window"):
            gate.validate_summary(summary, "expected")

    def test_required_checks_and_readonly_scripts_cannot_drift(self):
        cases = gate.scripts()
        self.assertEqual(gate.READONLY_CASES, {label for label, _, _ in cases})
        self.assertEqual(len(cases), len({label for label, _, _ in cases}))
        for label in ["shared-group-forward", "shared-group-reverse", "shared-window-forward", "shared-window-reverse"]:
            script = next(script for name, script, _ in cases if name == label)
            self.assertIn("const good=", script)
            self.assertIn("const bad=", script)
            self.assertIn("good.union(bad" if label.endswith("forward") else "bad.union(good", script)

    def test_owner_rejection_requires_a_binding_code_not_an_unrelated_error(self):
        for code in ["compose-schema-error/derived-query/unknown-field", "compose-schema-error/column/plan-not-bound"]:
            response = {"diagnostics": {"attributes": {"composeErrorCode": code}}}
            self.assertTrue(gate.rejection_matches(response, "owner"))
        self.assertFalse(gate.rejection_matches({"error": {"message": "owner database connection failed"}}, "owner"))

    def test_build_failure_records_failed_status_and_preserves_candidate(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            jar = root / "candidate.jar"
            jar.write_bytes(b"immutable candidate")
            with mock.patch.object(gate, "build_fixture", side_effect=gate.GateError("compile rejected")):
                summary = gate.execute_gate(jar, root / "evidence")
            self.assertEqual("failed", summary["status"])
            self.assertIn("compile rejected", summary["error"])
            self.assertTrue(summary["processStopped"])
            self.assertEqual(b"immutable candidate", jar.read_bytes())
            self.assertTrue((root / "evidence/summary.json").is_file())


if __name__ == "__main__":
    unittest.main()
