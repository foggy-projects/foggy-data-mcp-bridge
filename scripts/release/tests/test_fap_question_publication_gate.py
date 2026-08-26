from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest import mock


RELEASE_SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RELEASE_SCRIPT_DIR))

import fap_question_publication_gate as gate  # noqa: E402


class FapQuestionPublicationGateTest(unittest.TestCase):

    def test_accepts_exact_canonical_host_catalog_without_mutation(self) -> None:
        publication = self._publication()
        with self._expected(publication):
            result = gate.validate_catalog([publication])

        self.assertEqual(result["status"], "READY_FOR_HOST_APPLY")
        self.assertFalse(result["mutationPerformed"])
        self.assertEqual(result["functionRefs"], ["foggy.test.question@v1"])

    def test_rejects_caller_model_revision_even_with_recomputed_digests(self) -> None:
        publication = self._publication()
        publication["inputSchema"]["properties"]["expectedModelRevision"] = {
            "type": "string"
        }
        self._refresh_digests(publication)
        with self._expected(publication):
            with self.assertRaisesRegex(
                gate.PublicationValidationError,
                "caller-visible model revision marker",
            ):
                gate.validate_catalog([publication])

    @staticmethod
    def _publication() -> dict[str, object]:
        publication: dict[str, object] = {
            "functionRef": "foggy.test.question@v1",
            "name": "foggy.test.question",
            "displayName": "Test question",
            "description": "Test governed current model query.",
            "searchText": "test question",
            "tags": ["analytics", "read"],
            "inputSchema": {
                "type": "object",
                "properties": {"modelName": {"type": "string"}},
                "required": ["modelName"],
                "additionalProperties": False,
            },
            "outputSchema": {"type": "object"},
            "examples": [{"modelName": "CurrentModel"}],
            "schemaDigest": "",
            "projectionDigest": "",
        }
        FapQuestionPublicationGateTest._refresh_digests(publication)
        return publication

    @staticmethod
    def _refresh_digests(publication: dict[str, object]) -> None:
        publication["schemaDigest"] = gate.canonical_digest(
            {
                "functionRef": publication["functionRef"],
                "name": publication["name"],
                "inputSchema": publication["inputSchema"],
                "outputSchema": publication["outputSchema"],
            }
        )
        publication["projectionDigest"] = gate.canonical_digest(
            {
                key: publication[key]
                for key in gate.PUBLICATION_FIELDS
                if key != "projectionDigest"
            }
        )

    @staticmethod
    def _expected(publication: dict[str, object]):
        return mock.patch.multiple(
            gate,
            EXPECTED_FAP_FUNCTION_SCHEMA_DELIVERY=("foggy.test.question@v1",),
            EXPECTED_FAP_FUNCTION_DIGESTS={
                "foggy.test.question@v1": (
                    publication["schemaDigest"],
                    publication["projectionDigest"],
                )
            },
        )


if __name__ == "__main__":
    unittest.main()
