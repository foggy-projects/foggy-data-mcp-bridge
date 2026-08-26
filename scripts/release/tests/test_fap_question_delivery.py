from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


RELEASE_SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RELEASE_SCRIPT_DIR))

import fap_question_delivery as delivery  # noqa: E402


class FapQuestionDeliveryTest(unittest.TestCase):

    def test_bundle_matches_runtime_handoff_shape(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = root / "skill-metadata.json"
            schema = root / "function-schema-delivery.json"
            manifest = root / "host-publication-manifest.json"
            skill = root / "SKILL.md"
            reference = root / "references" / "query-model-dsl.md"
            reference.parent.mkdir()
            metadata.write_text('{"revision": 7}', encoding="utf-8")
            schema.write_text('{"functions": []}', encoding="utf-8")
            manifest.write_text('{"skill": {"revision": 7}}', encoding="utf-8")
            skill.write_text("# Skill\n", encoding="utf-8")
            reference.write_text("# Reference\n", encoding="utf-8")
            with (
                mock.patch.object(delivery, "QUESTION_ROOT", root),
                mock.patch.object(delivery, "SKILL_METADATA", metadata),
                mock.patch.object(delivery, "SCHEMA_DELIVERY", schema),
                mock.patch.object(delivery, "HOST_MANIFEST", manifest),
                mock.patch.object(delivery, "SKILL_DOCUMENT", skill),
                mock.patch.object(delivery, "_check"),
            ):
                result = delivery._bundle({"functions": [{"name": "example"}]})

            self.assertEqual(
                set(result),
                {
                    "contractVersion",
                    "publicationMode",
                    "mutationPerformed",
                    "providerCallback",
                    "skillMetadata",
                    "skillDocuments",
                    "functionSchemaDelivery",
                    "hostPublicationManifest",
                    "functions",
                },
            )
            self.assertEqual(result["skillMetadata"]["revision"], 7)
            self.assertNotIn("skill", result)
            self.assertNotIn("check", result)

    def test_sync_derives_delivery_and_revision_from_one_catalog(self) -> None:
        gate = {
            "functionRefs": ["foggy.analytics.example@v2"],
            "digests": {
                "foggy.analytics.example@v2": {
                    "schemaDigest": "sha256:" + "a" * 64,
                    "projectionDigest": "sha256:" + "b" * 64,
                }
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = root / "skill-metadata.json"
            schema = root / "function-schema-delivery.json"
            manifest = root / "host-publication-manifest.json"
            skill = root / "SKILL.md"
            metadata.write_text(
                json.dumps(
                    {
                        "contractVersion": "fap.skill.metadata.v1",
                        "name": "analytics-question-answering",
                        "revision": 7,
                    }
                ),
                encoding="utf-8",
            )
            skill.write_text("use foggy.analytics.example@v2\n", encoding="utf-8")
            with (
                mock.patch.object(delivery, "SKILL_METADATA", metadata),
                mock.patch.object(delivery, "SCHEMA_DELIVERY", schema),
                mock.patch.object(delivery, "HOST_MANIFEST", manifest),
                mock.patch.object(delivery, "SKILL_DOCUMENT", skill),
                mock.patch.object(delivery, "validate_catalog", return_value=gate),
            ):
                result = delivery._sync({"functions": [{}]}, skill_revision=8)
                repeated = delivery._sync({"functions": [{}]}, skill_revision=8)

            self.assertEqual(result["status"], "SYNCHRONIZED")
            self.assertTrue(result["mutationPerformed"])
            self.assertEqual(repeated["status"], "CURRENT")
            self.assertFalse(repeated["mutationPerformed"])
            self.assertEqual(json.loads(metadata.read_text())["revision"], 8)
            self.assertEqual(
                json.loads(manifest.read_text())["skill"]["revision"], 8
            )
            self.assertEqual(
                json.loads(schema.read_text())["functions"],
                [{"functionRef": "foggy.analytics.example@v2", "mode": "INLINE"}],
            )

    def test_check_rejects_skill_document_without_current_function_ref(self) -> None:
        gate = {
            "functionRefs": ["foggy.analytics.example@v2"],
            "digests": {
                "foggy.analytics.example@v2": {
                    "schemaDigest": "sha256:" + "a" * 64,
                    "projectionDigest": "sha256:" + "b" * 64,
                }
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata = root / "skill-metadata.json"
            schema = root / "function-schema-delivery.json"
            manifest = root / "host-publication-manifest.json"
            skill = root / "SKILL.md"
            metadata_value = {
                "contractVersion": "fap.skill.metadata.v1",
                "name": "analytics-question-answering",
                "revision": 7,
            }
            metadata.write_text(json.dumps(metadata_value), encoding="utf-8")
            with mock.patch.object(delivery, "validate_catalog", return_value=gate):
                expected_schema, expected_manifest = delivery._derived_delivery(
                    {"functions": [{}]}, metadata_value
                )
            schema.write_text(json.dumps(expected_schema), encoding="utf-8")
            manifest.write_text(json.dumps(expected_manifest), encoding="utf-8")
            skill.write_text("# Missing current reference\n", encoding="utf-8")
            with (
                mock.patch.object(delivery, "SKILL_METADATA", metadata),
                mock.patch.object(delivery, "SCHEMA_DELIVERY", schema),
                mock.patch.object(delivery, "HOST_MANIFEST", manifest),
                mock.patch.object(delivery, "SKILL_DOCUMENT", skill),
                mock.patch.object(delivery, "validate_catalog", return_value=gate),
                self.assertRaisesRegex(delivery.DeliveryError, "missing FunctionRefs"),
            ):
                delivery._check({"functions": [{}]})


if __name__ == "__main__":
    unittest.main()
