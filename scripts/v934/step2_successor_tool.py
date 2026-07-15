#!/usr/bin/env python3
"""Generate and validate the immutable 9.3.4 Step 2 successor inventory."""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import fcntl
import fnmatch
import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Callable, Iterable, Sequence


class SuccessorError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(f"{code}: {message}")
        self.code = code


def fail(code: str, message: str) -> None:
    raise SuccessorError(code, message)


def load_step1_tool(root: Path):
    path = root / "scripts/v934/inventory_tool.py"
    spec = importlib.util.spec_from_file_location("v934_step1_inventory_tool", path)
    if spec is None or spec.loader is None:
        fail("E_TOOLCHAIN", f"cannot load Step 1 tool: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


SOURCE_FILE = "source-inventory.tsv"
DISCOVERY_FILE = "discovery-inventory.tsv"
CLASSPATH_FILE = "discovery-classpath.tsv"
EXECUTION_FILE = "execution-inventory.tsv"
STRUCTURAL_FILE = "structural-report-inventory.tsv"
RENAME_FILE = "applied-rename-delta.tsv"
MIGRATION_FILE = "predecessor-regression-map.tsv"
STEP2_FILE = "step2-required-execution.tsv"
DEFERRED_FILE = "deferred-step3.tsv"
PARENT_FILE = "parent-link.json"
RUNNER_FILE = "runner-contract.json"
FREEZE_FILE = "contract-freeze.json"
NEGATIVE_FILE = "negative-probes.tsv"
HASH_FILE = "SHA256SUMS"
CORRECTIVE_RENAME_FILE = "step2-corrective-rename-plan.tsv"
TEST_SOURCE_AMENDMENT_FILE = "step2-test-source-amendment.tsv"
R5_SOURCE_AMENDMENT_FILE = "step2-r5-source-amendment.tsv"
R6_SOURCE_AMENDMENT_FILE = "step2-r6-source-amendment.tsv"
R7_RUNNER_AMENDMENT_FILE = "step2-r7-runner-amendment.tsv"
R8_AUTHORITY_AMENDMENT_FILE = "step2-r8-authority-amendment.tsv"
AUTHORITY_RUNNER_LIB = "scripts/v934/authority_runner_lib.sh"

SUCCESSOR_FILES = sorted([
    SOURCE_FILE,
    DISCOVERY_FILE,
    CLASSPATH_FILE,
    EXECUTION_FILE,
    STRUCTURAL_FILE,
    RENAME_FILE,
    MIGRATION_FILE,
    STEP2_FILE,
    DEFERRED_FILE,
    PARENT_FILE,
    RUNNER_FILE,
    FREEZE_FILE,
    NEGATIVE_FILE,
])

STRUCTURAL_HEADER = [
    "module",
    "source_id",
    "source_fqcn",
    "report_fqcn",
    "runner",
    "lane",
    "variant_key",
    "owner",
    "discovered_test_nodes",
    "runtime_deferred_containers",
    "positive_sibling_execution_keys",
    "disposition",
    "rationale",
]
MIGRATION_STRUCTURAL_FIELD = "successor_structural_report_fqcn"

DEFERRED_HEADER: list[str] = []
NEGATIVE_HEADER = ["probe", "expected_error", "actual_error", "status"]
NEGATIVE_PROBES = [
    "orphan-source",
    "old-discovery-report",
    "execution-semantic-drift",
    "structural-report-drift",
    "migration-successor-drift",
    "typed-migration-ref-drift",
    "deferred-gap",
    "parent-manifest-drift",
    "rename-delta-drift",
    "corrective-plan-digest-drift",
    "test-source-amendment-drift",
    "r5-source-amendment-drift",
    "r6-source-amendment-drift",
    "r7-runner-amendment-drift",
    "r8-authority-amendment-drift",
    "runner-contract-drift",
    "failsafe-selector-override-drift",
    "surefire-fail-if-no-tests-default-drift",
    "failsafe-fail-if-no-tests-default-drift",
    "authority-lock-contract-drift",
    "authority-signal-contract-drift",
    "publish-cas-contract-drift",
    "report-schema-drift",
    "report-context-drift",
    "report-cardinality-drift",
    "nested-it-exclude-drift",
    "classpath-identity-drift",
    "classpath-amended-content-drift",
    "protected-source-hash-drift",
    "freeze-count-drift",
    "extra-successor-file",
    "missing-hash-entry",
    "stale-manifest",
]

EXPECTED_PARENT_MANIFEST = "e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f"
EXPECTED_PARENT_FREEZE = "ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876"
EXPECTED_PARENT_SUMMARY = "579e9430bea6f873e7c4465cd1a6e45c49d348d84a89d5d648d25e3a5a4bbc50"
EXPECTED_RENAME_PLAN = "acba7a9dc22c9c0fdbaa0438fe91018b61eee8a457a36f1918995f39aa74cfe2"
EXPECTED_CORRECTIVE_RENAME_PLAN = "8c9c73c801efa786a71f07541a1003f456405525aeb7ae7583b2b938445ded09"
EXPECTED_TEST_SOURCE_AMENDMENT = "771b08b3825778f5aab6f656e15de6dc568b8070811451e5b5c010f44ee69368"
EXPECTED_R5_SOURCE_AMENDMENT = "3a4c6d424a6a0568f2818ecf36337515904a51d5b54a5f88c9f04beead615391"
EXPECTED_R6_SOURCE_AMENDMENT = "0751dcd49d20f9722f9aab8d532db3ca105b20cd7970dc3a9d6169b614189c7d"
EXPECTED_R7_RUNNER_AMENDMENT = "11ff594bf3689112ae0c0cd8be8e68bc6a5be3bfe0150adb8a80485ba3b10ac2"
EXPECTED_R8_AUTHORITY_AMENDMENT = "a0856b7bd1d19d35acdf51af17b3017de1f9c98dc8e5bb0b72ba3f787d09928a"
STEP1_DELIVERY_COMMIT = "9f5428d8d15d08457d2d2d57296256178c224f5d"
SUPERSEDED_RUN_ID = "step2-candidate-r7c-20260715"
SUPERSEDED_FREEZE_SHA256 = "3fea72715f651755897cee4464fd6075d5ea2187672e9d0fe66c97bf6d02a5d6"
SUPERSEDED_MANIFEST_SHA256 = "d0dfc94e1aa9bb8018d6ac6b5ae4b5c73ae25f6f6f81778a64e3f7787e2a3ca2"
SUPERSEDED_SUMMARY_SHA256 = "69a94475ca4c9d7162e9e6b217f637026cc790d3de4b654d9d12eb4c0dbe4f61"
SUCCESSOR_SEMANTICS = "corrective-lane-and-authority-remediation-v6"
EXPECTED_R8_LEAF_SHA256 = {
    "pom.xml": "7ddff9e29ae7297b4888fbf2efc196ce9c38072162497e4f1cebf608392ec66b",
    AUTHORITY_RUNNER_LIB: "865ad2839374e652a68bda9635f8d7fe72c0613446a08428a88aad57730130d9",
    "scripts/verify-v934-unit.sh": "0063629b399123d57b69b1ee794597562583c9f93347ca8d3dd965a9f1543427",
    "scripts/verify-v934-integration.sh": "911f4ee87f4a884887e940233bb053a891faffe702248a8d8499f59fab06c8cd",
    "scripts/v934/step2_report_tool.py": "ec478fac5eab355e1aed99c2b7a8934f0d5103425abfdd3f15f6f0733977c1ea",
}
EXPECTED_R7_LEAF_SHA256 = {
    "pom.xml": "d3e771a80829f3ca066d484b6a32304846f54b2d2cf8880420137a958b471679",
    AUTHORITY_RUNNER_LIB: "none",
    "scripts/verify-v934-unit.sh": "76fa2f8f8a633872ea0e0b8ffa19408316e5fbcefc580c4c449da74af6b2c3a3",
    "scripts/verify-v934-integration.sh": "b0fe2ca91bb4dea2c8363e27bc1c5ae584db56fcc9f2cf80fa02049683ed7373",
    "scripts/v934/step2_report_tool.py": "03e713e0562c3e89f813ea7efbc4796525f1eb5fb02c5dd094d23060fd5c2196",
    "scripts/verify-v934-step2-successor.sh": "48533d82bb640117bfb4c506e877191125e8713c114d56ad8e3d5553538786de",
}
CLASSPATH_AMENDMENT_MODULE = "foggy-mcp-launcher"
CLASSPATH_AMENDMENT_ORDINAL = "150"
CLASSPATH_AMENDMENT_IDENTITY = "repo:addons/foggy-dataset-model-mongo/target/classes"
CLASSPATH_AMENDMENT_PARENT_SHA256 = "af9a2d3036ef831a70a0a74a11aade3e3651afdb17e5984877835d31570866db"
CLASSPATH_AMENDMENT_SUCCESSOR_SHA256 = "302eb382337c9daf6c91981b6373b53648afe81470d598fb0cc817837fcf292d"
CLASSPATH_AMENDMENT_SOURCE = (
    "addons/foggy-dataset-model-mongo/src/main/java/"
    "com/foggyframework/dataset/db/model/mongo/MongoModelAutoConfiguration.java"
)
CORE_CLASSPATH_IDENTITY = "repo:foggy-core/target/classes"
CORE_CLASSPATH_PARENT_SHA256 = "fcc8d1bed99f03b126690e4feb3b975bc48a4e88039854904461e0a6559d9e06"
CORE_CLASSPATH_SUCCESSOR_SHA256 = "1cd8887f0169d081e65db23b8370c931e03f270af67fa5edb8935c16ac367be1"
CORE_CLASSPATH_SOURCE = "foggy-core/src/main/java/com/foggyframework/core/thread/MultiThreadExecutor.java"
CORE_CLASSPATH_ROWS = 20
MODEL_CLASSPATH_IDENTITY = "repo:foggy-dataset-model/target/classes"
MODEL_CLASSPATH_PARENT_SHA256 = "4e9c6dceec3397a3620e41a4522542f1a05126973090f5c0c1c375c2eff64a65"
MODEL_CLASSPATH_SUCCESSOR_SHA256 = "2913758f4eea56f89ac92bfda52446e5995640af8326dc7a11e36e196acb9496"
MODEL_CLASSPATH_SOURCE = (
    "foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/engine/"
    "query_model/PredefinedCalculatedFieldInjector.java"
)
MODEL_CLASSPATH_ROWS = 13
MODEL_TEST_CLASSES_SUCCESSOR_SHA256 = "07d3e021d6ffe8b2fe5c9303fa99fe657f6fcebe175f7bb38ca960c2b8e56f42"

TEST_SOURCE_AMENDMENT_HEADER = [
    "path",
    "owning_module",
    "r3_source_sha256",
    "r4_source_sha256",
    "r3_test_classes_sha256",
    "r4_test_classes_sha256",
    "discovered_test_nodes",
    "workitem",
    "allowed_effect",
]
R5_SOURCE_AMENDMENT_HEADER = [
    "path",
    "owning_module",
    "source_kind",
    "r4_source_sha256",
    "r5_source_sha256",
    "output_tree",
    "r4_output_tree_sha256",
    "r5_output_tree_sha256",
    "affected_classpath_rows",
    "discovered_test_nodes",
    "workitem",
    "allowed_effect",
]
R6_SOURCE_AMENDMENT_HEADER = [
    "path",
    "owning_module",
    "source_kind",
    "r5_source_sha256",
    "r6_source_sha256",
    "output_tree",
    "r5_output_tree_sha256",
    "r6_output_tree_sha256",
    "affected_classpath_rows",
    "discovered_test_nodes",
    "workitem",
    "allowed_effect",
]
R7_RUNNER_AMENDMENT_HEADER = [
    "path",
    "r6_sha256",
    "r7_sha256",
    "contract_field",
    "r6_value",
    "r7_value",
    "default_value",
    "workitem",
    "allowed_effect",
]
R8_AUTHORITY_AMENDMENT_HEADER = [
    "transition_id",
    "path",
    "r7_state",
    "r7_sha256",
    "r8_sha256",
    "contract_field",
    "r7_value",
    "r8_value",
    "enforcement",
    "workitem",
    "allowed_effect",
]
CALCULATE_AMENDMENT_PATH = (
    "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/"
    "ecommerce/CalculateMvpIT.java"
)
DATA_VIEWER_AMENDMENT_PATH = (
    "foggy-mcp-launcher/src/test/java/com/foggyframework/mcp/launcher/"
    "DataViewerApiSmokeTest.java"
)

SUREFIRE_INCLUDES = ["**/*Test.java", "**/*Tests.java", "**/*TestCase.java"]
SUREFIRE_EXCLUDES = [
    "**/IT*.java",
    "**/*IT.java",
    "**/*IT$*.java",
    "**/*ITCase.java",
    "**/*ITCase$*.java",
    "**/*E2E.java",
    "**/*E2E$*.java",
    "**/*E2ETest.java",
    "**/*E2ETest$*.java",
    "**/MultiDatabaseQueryTest.java",
    "**/MultiDatabaseQueryTest$*.java",
]
FAILSAFE_INCLUDES = [
    "**/IT*.java",
    "**/*IT.java",
    "**/*ITCase.java",
    "**/*E2E.java",
    "**/*E2ETest.java",
    "**/MultiDatabaseQueryTest.java",
]

EXPECTED_STRUCTURAL_REPORTS = 59
EXPECTED_POSITIVE_EXECUTIONS = 770
EXPECTED_REQUIRED_STEP2 = 724
EXPECTED_DEFERRED_STEP3 = 46
EXPECTED_POSITIVE_PREDECESSOR_EDGES = 480
EXPECTED_STRUCTURAL_PREDECESSOR_EDGES = 39


def migration_header(step1: Any) -> list[str]:
    header = list(step1.MIGRATION_HEADER)
    header.insert(header.index("successor_execution_key") + 1, MIGRATION_STRUCTURAL_FIELD)
    return header


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail("E_JSON", f"cannot read {path}: {exc}")
    if not isinstance(value, dict):
        fail("E_JSON", f"JSON root is not an object: {path}")
    return value


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_tsv(path: Path, header: Sequence[str]) -> list[dict[str, str]]:
    try:
        with path.open(encoding="utf-8", newline="") as stream:
            reader = csv.DictReader(stream, delimiter="\t")
            if reader.fieldnames != list(header):
                fail("E_TSV_SCHEMA", f"unexpected header in {path}: {reader.fieldnames}")
            return [dict(row) for row in reader]
    except OSError as exc:
        fail("E_TSV", f"cannot read {path}: {exc}")


def write_tsv(path: Path, header: Sequence[str], rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(header), delimiter="\t", lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({name: row[name] for name in header})


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def child(element: ET.Element | None, name: str) -> ET.Element | None:
    if element is None:
        return None
    return next((item for item in element if local_name(item.tag) == name), None)


def children(element: ET.Element | None, name: str) -> list[ET.Element]:
    if element is None:
        return []
    return [item for item in element if local_name(item.tag) == name]


def text_of(element: ET.Element | None, name: str) -> str:
    value = child(element, name)
    return (value.text or "").strip() if value is not None else ""


def plugin(build: ET.Element, artifact_id: str) -> ET.Element:
    plugins = child(build, "plugins")
    matches = [item for item in children(plugins, "plugin") if text_of(item, "artifactId") == artifact_id]
    if len(matches) != 1:
        fail("E_POM_CONTRACT", f"plugin {artifact_id} count={len(matches)}, expected=1")
    return matches[0]


def config_values(plugin_element: ET.Element, section: str) -> list[str]:
    configuration = child(plugin_element, "configuration")
    return [(item.text or "").strip() for item in children(child(configuration, section), "include" if section == "includes" else "exclude")]


def config_value(plugin_element: ET.Element, name: str) -> str:
    return text_of(child(plugin_element, "configuration"), name)


REPORT_NEGATIVE_PROBES = [
    "missing-xml",
    "stale-marker",
    "zero-testcase",
    "duplicate-fqcn",
    "unexpected-extra",
    "skipped",
    "runner-overlap",
    "missing-structural",
    "structural-nonzero",
    "unexpected-structural-zero",
    "unowned-reactor-extra",
    "structural-stale",
    "forged-positive-mtime",
    "forged-structural-mtime",
    "cross-run-manifest-splice",
    "static-discovery-underflow",
    "static-discovery-overflow",
    "deferred-discovery-underflow",
    "manifest-discovery-drift",
    "discovery-binding-drift",
]


def report_evidence_contract() -> dict[str, Any]:
    return {
        "manifest_schema_version": 3,
        "run_manifest_kind": "v934-step2-report-run",
        "merged_manifest_kind": "v934-step2-report-merged",
        "outer_context": {
            "schema_version": 1,
            "kind": "v934-step2-outer-run",
            "fields": [
                "schema_version", "kind", "run_id", "runner", "git_head",
                "source_before_sha256", "started_at", "status", "successor",
            ],
            "status": "started",
            "binding": "run-id+runner+git-head+source-hash+successor-identity",
        },
        "variant_context": {
            "schema_version": 1,
            "kind": "v934-step2-variant-run",
            "fields": [
                "schema_version", "kind", "run_id", "runner", "variant_key",
                "outer_marker_sha256", "started_at", "status",
            ],
            "status": "started",
            "binding": "outer-marker-sha256+run-id+runner+variant",
        },
        "cardinality_policy": {
            "static": "testcase_nodes==discovered_test_nodes",
            "runtime_deferred": "testcase_nodes>=discovered_test_nodes+runtime_deferred_containers",
            "structural": "testcase_nodes==0",
        },
        "variant_report_cardinality": {
            "surefire/unit": {"positive": 677, "structural": 55, "raw": 732},
            "failsafe/caffeine-sqlite": {"positive": 1, "structural": 0, "raw": 1},
            "failsafe/hermetic": {"positive": 1, "structural": 0, "raw": 1},
            "failsafe/sqlite-broad": {"positive": 42, "structural": 4, "raw": 46},
            "failsafe/sqlite-harness": {"positive": 1, "structural": 0, "raw": 1},
            "failsafe/sqlite-lifecycle": {"positive": 1, "structural": 0, "raw": 1},
            "failsafe/sqlite-refresh": {"positive": 1, "structural": 0, "raw": 1},
        },
        "runner_report_cardinality": {
            "surefire": {"positive": 677, "structural": 55, "raw": 732},
            "failsafe": {"positive": 47, "structural": 4, "raw": 51},
            "step2": {"positive": 724, "structural": 59, "raw": 783},
        },
        "metrics_header": [
            "execution_key", "source_id", "report_fqcn", "runner", "variant_key", "module",
            "discovered_test_nodes", "runtime_deferred_containers", "cardinality_policy",
            "minimum_testcase_nodes", "evidence_report", "tests", "failures", "errors",
            "skipped", "testcase_nodes", "sha256",
        ],
        "testcase_header": ["execution_key", "report_fqcn", "classname", "name"],
        "negative_probe_count": 20,
        "negative_probes": REPORT_NEGATIVE_PROBES,
        "cross_run_splice_policy": "reject",
    }


def authority_lock_contract(root: Path, step1: Any) -> dict[str, Any]:
    paths = {
        "helper": AUTHORITY_RUNNER_LIB,
        "unit_runner": "scripts/verify-v934-unit.sh",
        "integration_runner": "scripts/verify-v934-integration.sh",
    }
    actual = {label: step1.sha256_file(root / relative) for label, relative in paths.items()}
    expected = {
        "helper": EXPECTED_R8_LEAF_SHA256[AUTHORITY_RUNNER_LIB],
        "unit_runner": EXPECTED_R8_LEAF_SHA256["scripts/verify-v934-unit.sh"],
        "integration_runner": EXPECTED_R8_LEAF_SHA256["scripts/verify-v934-integration.sh"],
    }
    if actual != expected:
        fail("E_AUTHORITY_LOCK", f"authority lock participants differ: {actual}")
    for label, relative in paths.items():
        mode = (root / relative).stat().st_mode & 0o777
        if mode != 0o755:
            fail("E_AUTHORITY_LOCK", f"authority lock participant mode differs: {label}={mode:04o}")
    helper_source = (root / AUTHORITY_RUNNER_LIB).read_text(encoding="utf-8")
    if (
        'V934_AUTHORITY_LOCK_PATH="$git_dir/v934-step2-authority.lock"' not in helper_source
        or 'flock -n "$V934_AUTHORITY_LOCK_FD"' not in helper_source
        or "trap 'v934_record_run_status \"$?\"' EXIT" not in helper_source
        or "trap 'v934_exit_on_signal 130' INT" not in helper_source
        or "trap 'v934_exit_on_signal 143' TERM" not in helper_source
        or "trap 'v934_exit_on_signal 129' HUP" not in helper_source
        or "trap '' INT TERM HUP\n  trap - EXIT" not in helper_source
        or 'rm -f -- "$RUN_ROOT/summary.env"' not in helper_source
    ):
        fail("E_AUTHORITY_LOCK", "shared authority helper lock/signal lifecycle differs")
    for relative in ("scripts/verify-v934-unit.sh", "scripts/verify-v934-integration.sh"):
        source = (root / relative).read_text(encoding="utf-8")
        source_index = source.find('source "$AUTHORITY_LIB"')
        lock_index = source.find("v934_acquire_authority_lock")
        run_root_index = source.find('[[ ! -e "$RUN_ROOT" ]]')
        if min(source_index, lock_index, run_root_index) < 0 or not source_index < lock_index < run_root_index:
            fail("E_AUTHORITY_LOCK", f"authority lock is not acquired before run-root access: {relative}")
        install_index = source.find("v934_install_run_status_traps")
        source_baseline_index = source.find('PHASE="source-baseline"')
        disarm_index = source.rfind("v934_disarm_run_status_traps")
        pass_index = source.rfind("PASS run=$RUN_ID")
        if min(install_index, source_baseline_index, disarm_index, pass_index) < 0:
            fail("E_AUTHORITY_LOCK", f"authority signal lifecycle is incomplete: {relative}")
        if not install_index < source_baseline_index < disarm_index < pass_index:
            fail("E_AUTHORITY_LOCK", f"authority signal lifecycle order differs: {relative}")
    signal_contract = authority_signal_contract(root)
    return {
        "schema_version": 2,
        "helper_path": AUTHORITY_RUNNER_LIB,
        "helper_sha256": actual["helper"],
        "helper_mode": "0755",
        "lock_path": "git-dir/v934-step2-authority.lock",
        "mechanism": "flock-nonblocking-exclusive-fd",
        "participants": ["successor", "surefire", "failsafe", "confirm"],
        "unit_runner_sha256": actual["unit_runner"],
        "integration_runner_sha256": actual["integration_runner"],
        "acquire_before": ["run-root-read", "protected-source-read", "superseded-provenance-read", "shared-workspace-write"],
        "signal_contract": signal_contract,
    }


def authority_signal_contract(root: Path) -> dict[str, Any]:
    helper = root / AUTHORITY_RUNNER_LIB
    expected = {"INT": 130, "TERM": 143, "HUP": 129}
    probe_script = r'''
source "$1"
RUN_ROOT="$2"
RUN_ID="signal-probe"
RUNNER_NAME="probe"
GIT_HEAD="probe"
STARTED_AT="2026-07-15T00:00:00Z"
PHASE="completed"
SOURCE_BEFORE="probe"
SOURCE_AFTER="probe"
OUTER_MARKER_SHA256="probe"
SUCCESSOR_MANIFEST_SHA256="probe"
FINAL_REPORT_MANIFEST_SHA256="probe"
printf 'must-be-removed\n' > "$RUN_ROOT/summary.env"
v934_install_run_status_traps
v934_write_run_status 0
kill -s "$3" "$$"
exit 0
'''
    results: dict[str, int] = {}
    for signal_name, expected_code in expected.items():
        with tempfile.TemporaryDirectory(prefix=f"v934-signal-{signal_name.lower()}-") as temporary:
            run_root = Path(temporary)
            completed = subprocess.run(
                ["bash", "-c", probe_script, "_", str(helper), str(run_root), signal_name],
                cwd=root,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            status_path = run_root / "run-status.env"
            if not status_path.is_file():
                fail("E_AUTHORITY_LOCK", f"{signal_name} probe did not write durable status")
            values = dict(
                line.split("=", 1)
                for line in status_path.read_text(encoding="utf-8").splitlines()
                if "=" in line
            )
            if (
                completed.returncode != expected_code
                or values.get("exit_code") != str(expected_code)
                or values.get("status") != "failed"
                or (run_root / "summary.env").exists()
            ):
                fail(
                    "E_AUTHORITY_LOCK",
                    f"{signal_name} probe fail-open return={completed.returncode} status={values}",
                )
            results[signal_name] = completed.returncode
    return {
        "signals": results,
        "status": "failed-with-signal-exit-code",
        "summary_on_failure": "absent",
        "finalizer": "signal-blocked-nonreentrant",
        "disarm_order": "ignore-signals-before-remove-exit",
    }


def publish_cas_contract() -> dict[str, Any]:
    return {
        "schema_version": 2,
        "comparison": "exact-r7-freeze+manifest+confirmed-summary-sha256",
        "checkpoints": ["startup", "pre-publish", "post-archive"],
        "swap": "archive-r7-then-rename-staging",
        "rollback": "exit-trap-restores-and-revalidates-exact-r7-before-completed-exit",
        "rollback_states": ["archive-started", "r7-archived", "candidate-published", "publish-validated"],
        "post_publish": ["validate", "validate-summary"],
        "confirm_requires": ["passed-outer-run-status", "exact-r7-archive", "exact-superseded-provenance"],
        "confirmed_bindings": ["run-log", "run-status", "archive-freeze", "archive-manifest", "superseded-provenance"],
        "lock_required": True,
    }


def validate_runner_static_contract(contract: dict[str, Any]) -> None:
    expected = {
        "schema_version": 4,
        "surefire_fail_if_no_tests": "${surefire.failIfNoTests}",
        "surefire_fail_if_no_tests_default": "true",
        "failsafe_fail_if_no_tests": "${failsafe.failIfNoTests}",
        "failsafe_fail_if_no_tests_default": "true",
        "surefire_fail_if_no_specified_tests": "true",
        "failsafe_fail_if_no_specified_tests": "${failsafe.failIfNoSpecifiedTests}",
        "failsafe_fail_if_no_specified_tests_default": "true",
        "surefire_excludes": SUREFIRE_EXCLUDES,
    }
    for name, value in expected.items():
        if contract.get(name) != value:
            fail("E_POM_CONTRACT", f"runner static contract differs: {name}")
    if contract.get("report_evidence_contract") != report_evidence_contract():
        fail("E_REPORT_CONTRACT", "runner report evidence contract differs")
    if contract.get("publish_cas_contract") != publish_cas_contract():
        fail("E_PUBLISH_CAS", "runner publish CAS contract differs")
    lock = contract.get("authority_lock_contract")
    if not isinstance(lock, dict) or lock.get("schema_version") != 2:
        fail("E_AUTHORITY_LOCK", "runner authority lock contract differs")


def effective_runner_contract(root: Path, step1: Any) -> dict[str, Any]:
    reactor_modules = step1.active_reactor_modules(root)
    if len(reactor_modules) != 24 or reactor_modules != sorted(set(reactor_modules)):
        fail("E_POM_CONTRACT", "active reactor must contain 24 sorted unique modules")
    root_pom = ET.parse(root / "pom.xml").getroot()
    root_properties = child(root_pom, "properties")
    root_build = child(root_pom, "build")
    if root_build is None:
        fail("E_POM_CONTRACT", "root build is missing")
    root_surefire = plugin(root_build, "maven-surefire-plugin")
    root_failsafe = plugin(root_build, "maven-failsafe-plugin")
    if child(root_failsafe, "executions") is not None:
        fail("E_POM_CONTRACT", "root Failsafe must inherit the Boot parent execution, not add another id")
    if text_of(root_surefire, "version") != "${maven-surefire-plugin.version}":
        fail("E_POM_CONTRACT", "root Surefire version is not pinned by property")
    if text_of(root_failsafe, "version") != "${maven-failsafe-plugin.version}":
        fail("E_POM_CONTRACT", "root Failsafe version is not pinned by property")
    if config_values(root_surefire, "includes") != SUREFIRE_INCLUDES:
        fail("E_POM_CONTRACT", "root Surefire includes differ")
    if config_values(root_surefire, "excludes") != SUREFIRE_EXCLUDES:
        fail("E_POM_CONTRACT", "root Surefire excludes differ")
    if config_values(root_failsafe, "includes") != FAILSAFE_INCLUDES:
        fail("E_POM_CONTRACT", "root Failsafe includes differ")
    if config_value(root_surefire, "skipTests") != "${skipUnitTests}":
        fail("E_POM_CONTRACT", "Surefire is not independently controlled by skipUnitTests")
    if config_value(root_failsafe, "skipITs") != "${skipITs}":
        fail("E_POM_CONTRACT", "Failsafe is not independently controlled by skipITs")
    if text_of(root_properties, "surefire.failIfNoTests") != "true":
        fail("E_POM_CONTRACT", "Surefire no-test default is not fail-closed")
    if config_value(root_surefire, "failIfNoTests") != "${surefire.failIfNoTests}":
        fail("E_POM_CONTRACT", "Surefire no-test default is not authority-overrideable")
    if text_of(root_properties, "failsafe.failIfNoTests") != "true":
        fail("E_POM_CONTRACT", "Failsafe no-test default is not fail-closed")
    if config_value(root_failsafe, "failIfNoTests") != "${failsafe.failIfNoTests}":
        fail("E_POM_CONTRACT", "Failsafe no-test default is not authority-overrideable")
    if config_value(root_surefire, "failIfNoSpecifiedTests") != "true":
        fail("E_POM_CONTRACT", "Surefire selector miss is not fail-closed")
    if text_of(root_properties, "failsafe.failIfNoSpecifiedTests") != "true":
        fail("E_POM_CONTRACT", "Failsafe selector miss default is not fail-closed")
    if config_value(root_failsafe, "failIfNoSpecifiedTests") != "${failsafe.failIfNoSpecifiedTests}":
        fail("E_POM_CONTRACT", "Failsafe selector miss is not authority-overrideable")

    for pom_path in sorted(root.rglob("pom.xml")):
        if pom_path == root / "pom.xml" or "/target/" in pom_path.as_posix():
            continue
        project = ET.parse(pom_path).getroot()
        default_build = child(project, "build")
        default_plugins = child(default_build, "plugins")
        default_runner_plugins = [
            text_of(item, "artifactId")
            for item in children(default_plugins, "plugin")
            if text_of(item, "artifactId") in {"maven-surefire-plugin", "maven-failsafe-plugin"}
        ]
        if default_runner_plugins:
            fail("E_POM_CONTRACT", f"module defines a second default runner config: {pom_path.relative_to(root)}")
        for profile in children(child(project, "profiles"), "profile"):
            if text_of(child(profile, "activation"), "activeByDefault") != "true":
                continue
            profile_plugins = child(child(profile, "build"), "plugins")
            active_runners = [
                text_of(item, "artifactId")
                for item in children(profile_plugins, "plugin")
                if text_of(item, "artifactId") in {"maven-surefire-plugin", "maven-failsafe-plugin"}
            ]
            if active_runners:
                fail("E_POM_CONTRACT", f"active-by-default profile owns a runner: {pom_path.relative_to(root)}:{text_of(profile, 'id')}")

    model_pom = ET.parse(root / "foggy-dataset-model/pom.xml").getroot()
    profiles = child(model_pom, "profiles")
    multi = [item for item in children(profiles, "profile") if text_of(item, "id") == "multi-db"]
    if len(multi) != 1 or child(multi[0], "activation") is not None:
        fail("E_POM_CONTRACT", "multi-db must exist only as an explicit legacy profile")
    executions = child(plugin(child(multi[0], "build"), "maven-surefire-plugin"), "executions")
    legacy_ids = [text_of(item, "id") for item in children(executions, "execution")]
    if legacy_ids != ["test-mysql", "test-postgres"]:
        fail("E_POM_CONTRACT", f"legacy multi-db executions differ: {legacy_ids}")

    with tempfile.TemporaryDirectory(prefix="v934-step2-effective-") as temporary:
        output = Path(temporary) / "effective.xml"
        subprocess.run(
            [
                "mvn", "-q", "-P!multi-db,!model-lifecycle", "-f", "foggy-dataset-model/pom.xml", "help:effective-pom",
                f"-Doutput={output}",
            ],
            cwd=root,
            check=True,
        )
        effective = ET.parse(output).getroot()
        build = child(effective, "build")
        if build is None:
            fail("E_POM_CONTRACT", "effective model build is missing")
        sure = plugin(build, "maven-surefire-plugin")
        fail_plugin = plugin(build, "maven-failsafe-plugin")

        def execution_contract(item: ET.Element) -> list[dict[str, Any]]:
            result = []
            for execution in children(child(item, "executions"), "execution"):
                result.append({
                    "id": text_of(execution, "id"),
                    "phase": text_of(execution, "phase"),
                    "goals": [(goal.text or "").strip() for goal in children(child(execution, "goals"), "goal")],
                })
            return result

        sure_exec = execution_contract(sure)
        failsafe_exec = execution_contract(fail_plugin)
        if text_of(sure, "version") != "3.5.3" or text_of(fail_plugin, "version") != "3.5.3":
            fail("E_POM_CONTRACT", "effective runner versions are not pinned to 3.5.3")
        if len(sure_exec) != 1 or sure_exec[0]["id"] != "default-test" or sure_exec[0]["goals"] != ["test"]:
            fail("E_POM_CONTRACT", f"effective Surefire execution differs: {sure_exec}")
        if failsafe_exec != [{"id": "", "phase": "", "goals": ["integration-test", "verify"]}]:
            fail("E_POM_CONTRACT", f"effective Failsafe execution differs: {failsafe_exec}")
        if config_value(sure, "failIfNoTests") != "true" or config_value(fail_plugin, "failIfNoTests") != "true":
            fail("E_POM_CONTRACT", "effective no-test defaults are not fail-closed")
        if config_value(sure, "failIfNoSpecifiedTests") != "true" or config_value(fail_plugin, "failIfNoSpecifiedTests") != "true":
            fail("E_POM_CONTRACT", "effective selector-miss defaults are not fail-closed")
        result = {
            "schema_version": 4,
            "root_pom_sha256": step1.sha256_file(root / "pom.xml"),
            "model_pom_sha256": step1.sha256_file(root / "foggy-dataset-model/pom.xml"),
            "reactor_modules": reactor_modules,
            "surefire_version": text_of(sure, "version"),
            "failsafe_version": text_of(fail_plugin, "version"),
            "surefire_includes": config_values(sure, "includes"),
            "surefire_excludes": config_values(sure, "excludes"),
            "failsafe_includes": config_values(fail_plugin, "includes"),
            "surefire_skip_property": config_value(root_surefire, "skipTests"),
            "failsafe_skip_property": config_value(root_failsafe, "skipITs"),
            "surefire_fail_if_no_tests": config_value(root_surefire, "failIfNoTests"),
            "surefire_fail_if_no_tests_default": text_of(root_properties, "surefire.failIfNoTests"),
            "failsafe_fail_if_no_tests": config_value(root_failsafe, "failIfNoTests"),
            "failsafe_fail_if_no_tests_default": text_of(root_properties, "failsafe.failIfNoTests"),
            "surefire_fail_if_no_specified_tests": config_value(root_surefire, "failIfNoSpecifiedTests"),
            "failsafe_fail_if_no_specified_tests": config_value(root_failsafe, "failIfNoSpecifiedTests"),
            "failsafe_fail_if_no_specified_tests_default": text_of(
                root_properties, "failsafe.failIfNoSpecifiedTests"
            ),
            "surefire_executions": sure_exec,
            "failsafe_executions": failsafe_exec,
            "failsafe_binding_source": "spring-boot-parent-effective-pom",
            "multi_db_activation": "explicit-only",
            "legacy_multi_db_executions": legacy_ids,
            "step3_legacy_failsafe_exception": "MultiDatabaseQueryTest",
            "authority_lock_contract": authority_lock_contract(root, step1),
            "publish_cas_contract": publish_cas_contract(),
            "report_evidence_contract": report_evidence_contract(),
        }
        validate_runner_static_contract(result)
        return result


def corrective_rename_rows(step1_dir: Path, step1: Any) -> list[dict[str, str]]:
    path = step1_dir / CORRECTIVE_RENAME_FILE
    if step1.sha256_file(path) != EXPECTED_CORRECTIVE_RENAME_PLAN:
        fail("E_CORRECTIVE_PLAN", "Step 2 corrective rename plan digest differs")
    rows = read_tsv(path, step1.RENAME_HEADER)
    frozen_sources = {
        row["source_id"]: row
        for row in read_tsv(step1_dir / step1.SOURCE_FILE, step1.SOURCE_HEADER)
    }
    frozen_executions = {
        row["execution_key"]: row
        for row in read_tsv(step1_dir / step1.EXECUTION_FILE, step1.EXECUTION_HEADER)
    }
    expected_current_keys = {
        "v934|8:surefire|4:unit|4:unit|62:com.foggyframework.dataset.db.model.mongo.McpAuditLogMongoTest",
        "v934|8:surefire|4:unit|4:unit|69:com.foggyframework.dataset.db.model.mongo.MongoArrayElementAccessTest",
    }
    if len(rows) != 2 or {row["current_execution_key"] for row in rows} != expected_current_keys:
        fail("E_CORRECTIVE_PLAN", "corrective plan is not the reviewed two-execution set")
    target_semantics = {
        "runner": "failsafe",
        "lane": "external-mongo",
        "variant_key": "mongo6",
        "db_kind": "none",
        "infra_kind": "mongodb",
        "execution_step": "3",
        "required": "true",
        "owner": "addons/foggy-dataset-model-mongo",
        "optional_reason": "none",
        "review_at": "none",
    }
    for row in rows:
        source = frozen_sources.get(row["current_source_id"])
        execution = frozen_executions.get(row["current_execution_key"])
        if source is None or execution is None:
            fail("E_CORRECTIVE_PLAN", "corrective plan does not reference a frozen Step 1 row")
        current_identity = {
            "current_source_path": source["source_path"],
            "current_top_level_fqcn": source["top_level_fqcn"],
            "current_report_fqcn": execution["report_fqcn"],
        }
        if any(row[name] != value for name, value in current_identity.items()):
            fail("E_CORRECTIVE_PLAN", f"frozen current identity differs: {row['current_execution_key']}")
        if execution["source_id"] != row["current_source_id"]:
            fail("E_CORRECTIVE_PLAN", f"frozen source/execution ownership differs: {row['current_execution_key']}")
        target_path = row["current_source_path"].removesuffix("Test.java") + "IT.java"
        target_fqcn = row["current_top_level_fqcn"].removesuffix("Test") + "IT"
        expected_target = {
            "target_source_id": step1.source_id(target_path),
            "target_source_path": target_path,
            "target_top_level_fqcn": target_fqcn,
            "target_report_fqcn": target_fqcn,
            "target_execution_key": step1.execution_key(
                target_semantics["runner"],
                target_semantics["lane"],
                target_semantics["variant_key"],
                target_fqcn,
            ),
        }
        if any(row[name] != value for name, value in {**target_semantics, **expected_target}.items()):
            fail("E_CORRECTIVE_PLAN", f"reviewed corrective target differs: {row['current_execution_key']}")
        if not row["rename_group"].startswith("v934-corrective-") or not row["rationale"] or not row["reviewer"]:
            fail("E_CORRECTIVE_PLAN", f"corrective review metadata is incomplete: {row['current_execution_key']}")

    predecessor_refs = {
        row["successor_execution_key"]
        for row in read_tsv(step1_dir / step1.MIGRATION_FILE, step1.MIGRATION_HEADER)
        if row["successor_execution_key"] != "none"
    }
    if predecessor_refs & expected_current_keys:
        fail("E_CORRECTIVE_PLAN", "corrected executions unexpectedly own predecessor regression edges")
    return rows


def rename_maps(
    step1_dir: Path,
    step1: Any,
) -> tuple[
    list[dict[str, str]],
    dict[str, dict[str, str]],
    dict[str, dict[str, str]],
    dict[str, str],
    set[str],
]:
    original_rows = read_tsv(step1_dir / step1.RENAME_FILE, step1.RENAME_HEADER)
    if step1.sha256_file(step1_dir / step1.RENAME_FILE) != EXPECTED_RENAME_PLAN:
        fail("E_RENAME_DELTA", "immutable Step 1 rename plan digest differs")
    corrections = corrective_rename_rows(step1_dir, step1)
    rows = original_rows + corrections
    corrective_execution_keys = {row["current_execution_key"] for row in corrections}
    source_map: dict[str, dict[str, str]] = {}
    execution_map: dict[str, dict[str, str]] = {}
    report_map: dict[tuple[str, str], str] = {}
    migration_key_map: dict[str, str] = {}
    for row in rows:
        existing = source_map.setdefault(row["current_source_id"], row)
        for name in ("current_source_path", "target_source_id", "target_source_path", "target_top_level_fqcn"):
            if existing[name] != row[name]:
                fail("E_RENAME_DELTA", f"inconsistent source rename field {name}")
        current_execution = row["current_execution_key"]
        if current_execution in execution_map:
            fail("E_RENAME_DELTA", f"duplicate current execution key: {current_execution}")
        execution_map[current_execution] = row
        key = (row["current_source_id"], row["current_report_fqcn"])
        previous = report_map.setdefault(key, row["target_report_fqcn"])
        if previous != row["target_report_fqcn"]:
            fail("E_RENAME_DELTA", f"inconsistent report rename: {key}")
        migration_key_map[current_execution] = row["target_execution_key"]
    original_source_count = len({row["current_source_id"] for row in original_rows})
    original_report_count = len({(row["current_source_id"], row["current_report_fqcn"]) for row in original_rows})
    if (original_source_count, original_report_count, len(original_rows)) != (33, 62, 74):
        fail("E_RENAME_DELTA", "immutable Step 1 rename cardinality differs from 33/62/74")
    if (len(source_map), len(report_map), len(execution_map)) != (35, 64, 76):
        fail("E_RENAME_DELTA", "composed rename cardinality differs from 35/64/76")
    return rows, source_map, execution_map, migration_key_map, corrective_execution_keys


def transformed_sources(step1_dir: Path, root: Path, step1: Any, source_map: dict[str, dict[str, str]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for original in read_tsv(step1_dir / step1.SOURCE_FILE, step1.SOURCE_HEADER):
        row = dict(original)
        rename = source_map.get(row["source_id"])
        if rename:
            row["source_id"] = rename["target_source_id"]
            row["source_path"] = rename["target_source_path"]
            row["top_level_fqcn"] = rename["target_top_level_fqcn"]
            stem = Path(row["source_path"]).stem
            row["discovery_patterns"] = ",".join(pattern for pattern in step1.PATTERNS if fnmatch.fnmatch(stem, pattern))
        path = root / row["source_path"]
        if not path.is_file():
            fail("E_SOURCE_SET", f"successor source is missing: {row['source_path']}")
        _, expected_fqcn = step1.java_package_and_top_level_type(path)
        if row["top_level_fqcn"] != expected_fqcn:
            fail("E_SOURCE_SET", f"source FQCN differs: {row['source_path']}")
        result.append(row)

    expected_paths = {row["source_path"] for row in result}
    actual_paths: set[str] = set()
    for path in root.rglob("*.java"):
        relative = path.relative_to(root).as_posix()
        if "/src/test/java/" not in f"/{relative}":
            continue
        if any(fnmatch.fnmatch(path.stem, pattern) for pattern in step1.PATTERNS):
            actual_paths.add(relative)
    if actual_paths != expected_paths:
        missing = sorted(expected_paths - actual_paths)[:5]
        extra = sorted(actual_paths - expected_paths)[:5]
        fail("E_SOURCE_SET", f"candidate source set differs missing={missing} extra={extra}")
    if any(Path(row["source_path"]).stem.endswith("IntegrationTest") for row in result):
        fail("E_SOURCE_SET", "ambiguous IntegrationTest source remains")
    return sorted(result, key=lambda row: row["source_path"])


def test_source_amendment(root: Path, step1: Any) -> dict[str, Any]:
    manifest_path = root / "scripts/v934" / TEST_SOURCE_AMENDMENT_FILE
    if not manifest_path.is_file():
        fail("E_TEST_SOURCE_AMENDMENT", f"test source amendment is missing: {manifest_path}")
    if step1.sha256_file(manifest_path) != EXPECTED_TEST_SOURCE_AMENDMENT:
        fail("E_TEST_SOURCE_AMENDMENT", "test source amendment digest differs")

    rows = read_tsv(manifest_path, TEST_SOURCE_AMENDMENT_HEADER)
    expected_previous = {
        CALCULATE_AMENDMENT_PATH: {
            "owning_module": "foggy-dataset-model",
            "r3_source_sha256": "e3904ab2e4bffca9ab54c363dd4619033ec0794d959c34499c0228bbac7265db",
            "r3_test_classes_sha256": "b6a9182004c6dc9bbf80c3335dd53f136f70e6c69928ce8fdd707aed5204d68e",
            "discovered_test_nodes": "14",
        },
        DATA_VIEWER_AMENDMENT_PATH: {
            "owning_module": "foggy-mcp-launcher",
            "r3_source_sha256": "7cbecb5127558329c4a3e2ef7a8f06cc0dd9b3d715423e98d4e7da3c45323a3f",
            "r3_test_classes_sha256": "646c021c518a9c9c1b84cb6ccc05c512d07d85b5488c50cf3212585c678aa21d",
            "discovered_test_nodes": "6",
        },
    }
    if len(rows) != 2 or {row["path"] for row in rows} != set(expected_previous):
        fail("E_TEST_SOURCE_AMENDMENT", "test source amendment must contain exactly two reviewed paths")

    for row in rows:
        previous = expected_previous[row["path"]]
        if any(row[name] != value for name, value in previous.items()):
            fail("E_TEST_SOURCE_AMENDMENT", f"r3 provenance differs: {row['path']}")
        source_path = root / row["path"]
        workitem_path = root / row["workitem"]
        if not source_path.is_file() or not workitem_path.is_file():
            fail("E_TEST_SOURCE_AMENDMENT", f"reviewed source/workitem is missing: {row['path']}")
        if step1.sha256_file(source_path) != row["r4_source_sha256"]:
            fail("E_TEST_SOURCE_AMENDMENT", f"reviewed source hash differs: {row['path']}")
        test_classes = root / row["owning_module"] / "target/test-classes"
        expected_tree_sha256 = (
            MODEL_TEST_CLASSES_SUCCESSOR_SHA256
            if row["owning_module"] == "foggy-dataset-model"
            else row["r4_test_classes_sha256"]
        )
        if step1.directory_tree_hash(test_classes) != expected_tree_sha256:
            fail("E_TEST_SOURCE_AMENDMENT", f"reviewed test-classes hash differs: {row['owning_module']}")

    return {
        "classification": "authority-red-green-test-source-amendment-v1",
        "manifest": f"scripts/v934/{TEST_SOURCE_AMENDMENT_FILE}",
        "manifest_sha256": EXPECTED_TEST_SOURCE_AMENDMENT,
        "sources": 2,
        "discovered_test_nodes": 20,
        "discovery_report_execution_delta": "0/0/0",
        "entries": rows,
    }


def r5_source_amendment(root: Path, step1: Any) -> dict[str, Any]:
    manifest_path = root / "scripts/v934" / R5_SOURCE_AMENDMENT_FILE
    if not manifest_path.is_file():
        fail("E_R5_SOURCE_AMENDMENT", f"r5 source amendment is missing: {manifest_path}")
    if step1.sha256_file(manifest_path) != EXPECTED_R5_SOURCE_AMENDMENT:
        fail("E_R5_SOURCE_AMENDMENT", "r5 source amendment digest differs")

    rows = read_tsv(manifest_path, R5_SOURCE_AMENDMENT_HEADER)
    expected = {
        CORE_CLASSPATH_SOURCE: {
            "owning_module": "foggy-core",
            "source_kind": "production",
            "r4_source_sha256": "906c00e51bdbfbff6627a7df6f23961c532bf6c713be48a7f2caf0c5c6c78cae",
            "output_tree": "main-classes",
            "r4_output_tree_sha256": CORE_CLASSPATH_PARENT_SHA256,
            "affected_classpath_rows": str(CORE_CLASSPATH_ROWS),
            "discovered_test_nodes": "none",
            "workitem": "docs/9.3.4/workitems/BUG-multi-thread-executor-premature-completion.md",
        },
        (
            "addons/foggy-fsscript-client/src/test/java/com/foggyframework/"
            "fsscript/client/test/support/FsscriptClientProxyTest.java"
        ): {
            "owning_module": "addons/foggy-fsscript-client",
            "source_kind": "test",
            "r4_source_sha256": "72409720aba005ee57dc9e610761976f1eac92d70ea92ec8b1943361a8ab3cef",
            "output_tree": "test-classes",
            "r4_output_tree_sha256": "fb561bc0af8c72e46caccde82aa668929007b1cb6513d667ee4f4ca374ed90f4",
            "affected_classpath_rows": "0",
            "discovered_test_nodes": "10",
            "workitem": "docs/9.3.4/workitems/BUG-multi-thread-executor-premature-completion.md",
        },
    }
    if len(rows) != 2 or {row["path"] for row in rows} != set(expected):
        fail("E_R5_SOURCE_AMENDMENT", "r5 source amendment must contain exactly two reviewed paths")
    for row in rows:
        if any(row[name] != value for name, value in expected[row["path"]].items()):
            fail("E_R5_SOURCE_AMENDMENT", f"r4 provenance differs: {row['path']}")
        source_path = root / row["path"]
        workitem_path = root / row["workitem"]
        if not source_path.is_file() or not workitem_path.is_file():
            fail("E_R5_SOURCE_AMENDMENT", f"reviewed source/workitem is missing: {row['path']}")
        if step1.sha256_file(source_path) != row["r5_source_sha256"]:
            fail("E_R5_SOURCE_AMENDMENT", f"reviewed source hash differs: {row['path']}")
        tree_name = "classes" if row["output_tree"] == "main-classes" else "test-classes"
        output_tree = root / row["owning_module"] / "target" / tree_name
        if step1.directory_tree_hash(output_tree) != row["r5_output_tree_sha256"]:
            fail("E_R5_SOURCE_AMENDMENT", f"reviewed output tree hash differs: {row['path']}")

    return {
        "classification": "authority-runtime-and-test-remediation-v1",
        "manifest": f"scripts/v934/{R5_SOURCE_AMENDMENT_FILE}",
        "manifest_sha256": EXPECTED_R5_SOURCE_AMENDMENT,
        "sources": 2,
        "production_sources": 1,
        "test_sources": 1,
        "classpath_content_rows": CORE_CLASSPATH_ROWS,
        "discovery_report_execution_delta": "0/0/0",
        "entries": rows,
    }


def r6_source_amendment(root: Path, step1: Any) -> dict[str, Any]:
    manifest_path = root / "scripts/v934" / R6_SOURCE_AMENDMENT_FILE
    if not manifest_path.is_file():
        fail("E_R6_SOURCE_AMENDMENT", f"r6 source amendment is missing: {manifest_path}")
    if step1.sha256_file(manifest_path) != EXPECTED_R6_SOURCE_AMENDMENT:
        fail("E_R6_SOURCE_AMENDMENT", "r6 source amendment digest differs")

    embedding_path = (
        "addons/foggy-dataset-model-vector/src/test/java/com/foggyframework/"
        "dataset/db/model/vector/EmbeddingServiceTest.java"
    )
    snapshot_path = (
        "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/"
        "parity/JavaQueryModelAggregateJoinSnapshotTest.java"
    )
    expected = {
        embedding_path: {
            "owning_module": "addons/foggy-dataset-model-vector",
            "source_kind": "test",
            "r5_source_sha256": "378be3787445ae40e450288ee0f734a19f4a011ca42bd3c18ba21f5ce995e1ab",
            "output_tree": "test-classes",
            "r5_output_tree_sha256": "57bd5d527ef4518092c8475f8bd44c3747336eb8c2e0062bdf035f06d2e30284",
            "affected_classpath_rows": "0",
            "discovered_test_nodes": "15",
            "workitem": "docs/9.3.4/workitems/BUG-embedding-service-disabled-unit.md",
        },
        snapshot_path: {
            "owning_module": "foggy-dataset-model",
            "source_kind": "test",
            "r5_source_sha256": "0c774aa4858b929d80acd2687bba4b01ef5b3c910dab557732f8960ac5502e24",
            "output_tree": "test-classes",
            "r5_output_tree_sha256": "099bec240a18627d810ee8aa88f3662ee0e54032ca0976bb5114efe49ad9c16d",
            "affected_classpath_rows": "0",
            "discovered_test_nodes": "1",
            "workitem": "docs/9.3.4/workitems/BUG-aggregate-join-snapshot-default-skip.md",
        },
        MODEL_CLASSPATH_SOURCE: {
            "owning_module": "foggy-dataset-model",
            "source_kind": "production",
            "r5_source_sha256": "a12168ca4535d066b6557c268f5ec053edd267ead7a329fe38947ecead453a52",
            "output_tree": "main-classes",
            "r5_output_tree_sha256": MODEL_CLASSPATH_PARENT_SHA256,
            "affected_classpath_rows": str(MODEL_CLASSPATH_ROWS),
            "discovered_test_nodes": "none",
            "workitem": "docs/9.3.4/workitems/BUG-predefined-calculated-field-immutable-list.md",
        },
    }
    rows = read_tsv(manifest_path, R6_SOURCE_AMENDMENT_HEADER)
    if len(rows) != 3 or {row["path"] for row in rows} != set(expected):
        fail("E_R6_SOURCE_AMENDMENT", "r6 source amendment must contain exactly three reviewed paths")
    for row in rows:
        if any(row[name] != value for name, value in expected[row["path"]].items()):
            fail("E_R6_SOURCE_AMENDMENT", f"r5 provenance differs: {row['path']}")
        source_path = root / row["path"]
        workitem_path = root / row["workitem"]
        if not source_path.is_file() or not workitem_path.is_file():
            fail("E_R6_SOURCE_AMENDMENT", f"reviewed source/workitem is missing: {row['path']}")
        if step1.sha256_file(source_path) != row["r6_source_sha256"]:
            fail("E_R6_SOURCE_AMENDMENT", f"reviewed source hash differs: {row['path']}")
        tree_name = "classes" if row["output_tree"] == "main-classes" else "test-classes"
        output_tree = root / row["owning_module"] / "target" / tree_name
        if step1.directory_tree_hash(output_tree) != row["r6_output_tree_sha256"]:
            fail("E_R6_SOURCE_AMENDMENT", f"reviewed output tree hash differs: {row['path']}")

    return {
        "classification": "authority-zero-skip-and-immutable-request-remediation-v1",
        "manifest": f"scripts/v934/{R6_SOURCE_AMENDMENT_FILE}",
        "manifest_sha256": EXPECTED_R6_SOURCE_AMENDMENT,
        "sources": 3,
        "production_sources": 1,
        "test_sources": 2,
        "classpath_content_rows": MODEL_CLASSPATH_ROWS,
        "discovered_test_nodes": 16,
        "discovery_report_execution_delta": "0/0/0",
        "entries": rows,
    }


def r7_runner_amendment(root: Path, step1: Any) -> dict[str, Any]:
    manifest_path = root / "scripts/v934" / R7_RUNNER_AMENDMENT_FILE
    if not manifest_path.is_file():
        fail("E_R7_RUNNER_AMENDMENT", f"r7 runner amendment is missing: {manifest_path}")
    if step1.sha256_file(manifest_path) != EXPECTED_R7_RUNNER_AMENDMENT:
        fail("E_R7_RUNNER_AMENDMENT", "r7 runner amendment digest differs")

    rows = read_tsv(manifest_path, R7_RUNNER_AMENDMENT_HEADER)
    expected = {
        "pom.xml": {
            "r6_sha256": "0bd2e0f4e31ecc0b5c8ab88b9aa20ec14e5cc65e6cd47d8c821057c22fcf8f9f",
            "r7_sha256": "d3e771a80829f3ca066d484b6a32304846f54b2d2cf8880420137a958b471679",
            "contract_field": "failsafe_fail_if_no_specified_tests",
            "r6_value": "true",
            "r7_value": "${failsafe.failIfNoSpecifiedTests}",
            "default_value": "true",
            "workitem": "docs/9.3.4/workitems/BUG-failsafe-reactor-selector-override.md",
        },
        "scripts/verify-v934-integration.sh": {
            "r6_sha256": "3ef7d12558f2fb242c6dda8e68c67973f4fd24659784e89f9f3188f44434c651",
            "r7_sha256": "b0fe2ca91bb4dea2c8363e27bc1c5ae584db56fcc9f2cf80fa02049683ed7373",
            "contract_field": "authority_failure_evidence",
            "r6_value": "ephemeral-console-only",
            "r7_value": "run.log+run-status.env",
            "default_value": "fail-closed",
            "workitem": "docs/9.3.4/workitems/BUG-failsafe-reactor-selector-override.md",
        },
    }
    if len(rows) != 2 or {row["path"] for row in rows} != set(expected):
        fail("E_R7_RUNNER_AMENDMENT", "r7 runner amendment must contain the two reviewed transitions")
    for row in rows:
        if any(row[name] != value for name, value in expected[row["path"]].items()):
            fail("E_R7_RUNNER_AMENDMENT", f"r6 runner provenance differs: {row['path']}")
        path = root / row["path"]
        workitem = root / row["workitem"]
        if not path.is_file() or not workitem.is_file():
            fail("E_R7_RUNNER_AMENDMENT", f"reviewed r7 file/workitem is missing: {row['path']}")
        expected_current_sha256 = EXPECTED_R8_LEAF_SHA256.get(row["path"], row["r7_sha256"])
        if step1.sha256_file(path) != expected_current_sha256:
            fail("E_R7_RUNNER_AMENDMENT", f"reviewed current successor file hash differs: {row['path']}")

    return {
        "classification": "failsafe-reactor-selector-override-v1",
        "manifest": f"scripts/v934/{R7_RUNNER_AMENDMENT_FILE}",
        "manifest_sha256": EXPECTED_R7_RUNNER_AMENDMENT,
        "files": 2,
        "default_fail_closed": True,
        "authority_override_scope": "selected-reactor-maven-selector-check",
        "target_exact_set_enforcement": "step2-report-verifier",
        "failure_evidence": "run.log+run-status.env",
        "entries": rows,
    }


def r8_authority_amendment(root: Path, step1: Any) -> dict[str, Any]:
    manifest_path = root / "scripts/v934" / R8_AUTHORITY_AMENDMENT_FILE
    if not manifest_path.is_file():
        fail("E_R8_AUTHORITY_AMENDMENT", f"r8 authority amendment is missing: {manifest_path}")
    if step1.sha256_file(manifest_path) != EXPECTED_R8_AUTHORITY_AMENDMENT:
        fail("E_R8_AUTHORITY_AMENDMENT", "r8 authority amendment digest differs")
    rows = read_tsv(manifest_path, R8_AUTHORITY_AMENDMENT_HEADER)
    expected_paths = [
        "pom.xml",
        AUTHORITY_RUNNER_LIB,
        "scripts/verify-v934-unit.sh",
        "scripts/verify-v934-integration.sh",
        "scripts/v934/step2_report_tool.py",
        "scripts/verify-v934-step2-successor.sh",
    ]
    expected_ids = [
        "pom-fail-if-no-tests",
        "shared-authority-lock",
        "unit-authority-context",
        "integration-authority-context",
        "report-context-cardinality",
        "successor-publish-cas",
    ]
    if [row["path"] for row in rows] != expected_paths or [row["transition_id"] for row in rows] != expected_ids:
        fail("E_R8_AUTHORITY_AMENDMENT", "r8 authority amendment path/transition order differs")
    if len({row["transition_id"] for row in rows}) != len(rows):
        fail("E_R8_AUTHORITY_AMENDMENT", "r8 authority transition ids are not unique")
    for row in rows:
        path = root / row["path"]
        workitem = root / row["workitem"]
        if not path.is_file() or not workitem.is_file():
            fail("E_R8_AUTHORITY_AMENDMENT", f"reviewed r8 file/workitem is missing: {row['path']}")
        expected_before = EXPECTED_R7_LEAF_SHA256[row["path"]]
        expected_state = "absent" if expected_before == "none" else "present"
        if row["r7_state"] != expected_state or row["r7_sha256"] != expected_before:
            fail("E_R8_AUTHORITY_AMENDMENT", f"r7 leaf provenance differs: {row['path']}")
        if not re.fullmatch(r"[0-9a-f]{64}", row["r8_sha256"]):
            fail("E_R8_AUTHORITY_AMENDMENT", f"invalid r8 leaf digest: {row['path']}")
        expected_after = EXPECTED_R8_LEAF_SHA256.get(row["path"])
        if expected_after is not None and row["r8_sha256"] != expected_after:
            fail("E_R8_AUTHORITY_AMENDMENT", f"reviewed r8 leaf digest differs: {row['path']}")
        if step1.sha256_file(path) != row["r8_sha256"]:
            fail("E_R8_AUTHORITY_AMENDMENT", f"current r8 leaf hash differs: {row['path']}")
        if not row["contract_field"] or not row["enforcement"] or not row["allowed_effect"]:
            fail("E_R8_AUTHORITY_AMENDMENT", f"r8 authority semantics are incomplete: {row['path']}")
    for relative in expected_paths[1:]:
        if (root / relative).stat().st_mode & 0o777 != 0o755:
            fail("E_R8_AUTHORITY_AMENDMENT", f"r8 executable mode differs: {relative}")
    return {
        "classification": "step2-authority-hardening-v1",
        "manifest": f"scripts/v934/{R8_AUTHORITY_AMENDMENT_FILE}",
        "manifest_sha256": EXPECTED_R8_AUTHORITY_AMENDMENT,
        "files": len(rows),
        "default_no_tests": "surefire=true+failsafe=true",
        "shared_lock": "git-dir/v934-step2-authority.lock",
        "publish_cas": "exact-r7-three-checkpoint+exit-trap-rollback+confirmed-publication-seal",
        "report_contract": "schema-v3+typed-context+discovery-cardinality+20-negative-probes",
        "inventory_cardinality_delta": "0/0/0",
        "entries": rows,
    }


def validate_canonical_rename_content(
    root: Path,
    rename_rows: list[dict[str, str]],
    step1: Any,
) -> None:
    """Prove that each move changes only the frozen rename tokens.

    Step 1 captured source hashes from a CRLF checkout. Git blobs are the portable
    authority, so compare the inverse-renamed LF content with the delivery commit
    instead of perpetuating a checkout-specific byte representation.
    """
    by_source: dict[str, list[dict[str, str]]] = defaultdict(list)
    replacements: set[tuple[str, str]] = set()
    for row in rename_rows:
        by_source[row["current_source_id"]].append(row)
        replacements.add((row["target_report_fqcn"], row["current_report_fqcn"]))
        replacements.add((row["target_top_level_fqcn"], row["current_top_level_fqcn"]))
        replacements.add((Path(row["target_source_path"]).stem, Path(row["current_source_path"]).stem))
    ordered = sorted(replacements, key=lambda item: len(item[0]), reverse=True)
    amendments = {row["path"]: row for row in test_source_amendment(root, step1)["entries"]}
    for rows in by_source.values():
        target_path = root / rows[0]["target_source_path"]
        canonical = target_path.read_text(encoding="utf-8")
        for target, current in ordered:
            canonical = canonical.replace(target, current)
        try:
            predecessor = subprocess.run(
                ["git", "show", f"{STEP1_DELIVERY_COMMIT}:{rows[0]['current_source_path']}"],
                cwd=root,
                check=True,
                stdout=subprocess.PIPE,
            ).stdout
        except subprocess.CalledProcessError:
            fail("E_RENAME_CONTENT", f"cannot read predecessor blob: {rows[0]['current_source_path']}")
        amendment = amendments.get(rows[0]["target_source_path"])
        if amendment is not None:
            baseline = predecessor.decode("utf-8")
            for target, current in ordered:
                baseline = baseline.replace(current, target)
            if hashlib.sha256(baseline.encode("utf-8")).hexdigest() != amendment["r3_source_sha256"]:
                fail("E_TEST_SOURCE_AMENDMENT", f"r3 renamed baseline differs: {target_path}")
            continue
        if canonical.encode("utf-8") != predecessor:
            fail("E_RENAME_CONTENT", f"unplanned content delta: {rows[0]['target_source_path']}")


def active_reference_paths(root: Path) -> list[Path]:
    output = subprocess.run(
        ["git", "ls-files", "-co", "--exclude-standard", "-z"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    active_docs = {
        "docs/issues-tracker.md",
        "docs/unit-test-analysis.md",
        "foggy-dataset-mcp/TEST_README.md",
    }
    result: list[Path] = []
    for raw in output.split(b"\0"):
        if not raw:
            continue
        relative = raw.decode("utf-8")
        path = root / relative
        if not path.is_file() or "/target/" in f"/{relative}/":
            continue
        source = "/src/" in f"/{relative}/" and path.suffix in {".java", ".md", ".txt"}
        workflow = relative.startswith(".github/workflows/") and path.suffix in {".yml", ".yaml"}
        current_script = (
            relative.startswith("scripts/")
            and not relative.startswith("scripts/v934/")
            and not relative.startswith("scripts/verify-v933-")
            and not relative.startswith("scripts/assert-v933-")
            and path.suffix in {".sh", ".ps1", ".py"}
        )
        current_guide = (
            relative.startswith("docs/dev-guide/")
            or relative in active_docs
            or (relative.startswith("addons/") and path.suffix == ".md")
        )
        if source or workflow or current_script or current_guide:
            result.append(path)
    return sorted(set(result))


def validate_legacy_token_residue(root: Path, rename_rows: list[dict[str, str]]) -> None:
    tokens = {
        row["current_top_level_fqcn"]
        for row in rename_rows
    } | {
        Path(row["current_source_path"]).stem
        for row in rename_rows
    }
    residues: list[str] = []
    for path in active_reference_paths(root):
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for token in sorted(tokens, key=len, reverse=True):
            if re.search(rf"(?<![A-Za-z0-9_$]){re.escape(token)}(?![A-Za-z0-9_$])", content):
                residues.append(f"{path.relative_to(root).as_posix()}:{token}")
    if residues:
        fail("E_LEGACY_TOKEN", f"active old-name references remain: {residues[:5]}")


def validate_git_delta_scope(root: Path, rename_rows: list[dict[str, str]]) -> None:
    allowed = {
        "pom.xml",
        "foggy-dataset-model/pom.xml",
        "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose/plan/ColumnObjectNormalizerF5Test.java",
        ".github/workflows/pivot-release-readiness.yml",
        "scripts/v934/step2_successor_tool.py",
        "scripts/v934/step2-corrective-rename-plan.tsv",
        "scripts/v934/step2-test-source-amendment.tsv",
        "scripts/v934/step2-r5-source-amendment.tsv",
        "scripts/v934/step2-r6-source-amendment.tsv",
        "scripts/v934/step2-r7-runner-amendment.tsv",
        "scripts/v934/step2-r8-authority-amendment.tsv",
        "scripts/v934/authority_runner_lib.sh",
        "scripts/v934/step2_report_tool.py",
        "scripts/verify-v934-step2-successor.sh",
        "scripts/verify-v934-unit.sh",
        "scripts/verify-v934-integration.sh",
        "scripts/run-ai-domain-direct.sh",
        "scripts/run-ai-llm-comparison.sh",
        "scripts/verify-pivot-v9-release.sh",
        "scripts/verify-pivot-v9-release.ps1",
        "scripts/verify-v310-field-permissions.sh",
        "scripts/verify-v38-engine-evidence.sh",
        "scripts/verify-v39-engine-production-gate.sh",
        "addons/foggy-data-viewer/frontend/USAGE.md",
        "addons/foggy-dataset-model-vector/OPTIMIZATION_PLAN.md",
        "addons/foggy-dataset-vector/src/test/resources/README.md",
        "docs/dev-guide/compose-query.md",
        "docs/dev-guide/preagg-optimization-issues.md",
        "docs/issues-tracker.md",
        "docs/unit-test-analysis.md",
        "foggy-dataset-mcp/TEST_README.md",
        "foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/integration/README.md",
        "addons/foggy-dataset-model-mongo/src/main/java/com/foggyframework/dataset/db/model/mongo/MongoModelAutoConfiguration.java",
        "addons/foggy-dataset-model-mongo/src/test/java/io/foggytest/autoconfigure/modelmongo/MongoModelAutoConfigurationContractTest.java",
        CORE_CLASSPATH_SOURCE,
        MODEL_CLASSPATH_SOURCE,
        "addons/foggy-fsscript-client/src/test/java/com/foggyframework/fsscript/client/test/support/FsscriptClientProxyTest.java",
        "addons/foggy-dataset-model-vector/src/test/java/com/foggyframework/dataset/db/model/vector/EmbeddingServiceTest.java",
        "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/parity/JavaQueryModelAggregateJoinSnapshotTest.java",
        CALCULATE_AMENDMENT_PATH,
        DATA_VIEWER_AMENDMENT_PATH,
    }
    old_paths = {row["current_source_path"] for row in rename_rows}
    new_paths = {row["target_source_path"] for row in rename_rows}
    allowed.update(old_paths)
    allowed.update(new_paths)

    changed: dict[str, str] = {}
    output = subprocess.run(
        ["git", "diff", "--name-status", "--no-renames", STEP1_DELIVERY_COMMIT, "--"],
        cwd=root,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout
    for line in output.splitlines():
        status, relative = line.split("\t", 1)
        changed[relative] = status
    untracked = subprocess.run(
        ["git", "ls-files", "--others", "--exclude-standard", "-z"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    for raw in untracked.split(b"\0"):
        if raw:
            changed[raw.decode("utf-8")] = "A"

    def governed(relative: str) -> bool:
        if relative.startswith("scripts/v934/successor/") or relative.startswith("docs/9.3.4/"):
            return False
        return (
            relative == "pom.xml"
            or relative.endswith("/pom.xml")
            or "/src/main/" in f"/{relative}/"
            or "/src/test/" in f"/{relative}/"
            or relative.startswith(".github/workflows/")
            or relative.startswith("scripts/")
            or relative.startswith("docs/dev-guide/")
            or relative in {"docs/issues-tracker.md", "docs/unit-test-analysis.md"}
            or (relative.startswith("addons/") and relative.endswith(".md"))
        )

    unexpected = sorted(relative for relative in changed if governed(relative) and relative not in allowed)
    if unexpected:
        fail("E_DELTA_SCOPE", f"unplanned governed paths changed: {unexpected[:5]}")
    crlf_paths = [
        relative
        for relative, status in changed.items()
        if status != "D" and governed(relative) and (root / relative).is_file() and b"\r" in (root / relative).read_bytes()
    ]
    if crlf_paths:
        fail("E_DELTA_SCOPE", f"non-canonical CR bytes in governed deltas: {sorted(crlf_paths)[:5]}")
    if {changed.get(path) for path in old_paths} != {"D"}:
        fail("E_DELTA_SCOPE", "all 35 predecessor source paths must be deleted")
    if {changed.get(path) for path in new_paths} != {"A"}:
        fail("E_DELTA_SCOPE", "all 35 successor source paths must be added")
    for required in (
        "pom.xml",
        "foggy-dataset-model/pom.xml",
        "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose/plan/ColumnObjectNormalizerF5Test.java",
    ):
        if changed.get(required) != "M":
            fail("E_DELTA_SCOPE", f"required Step 2 delta is missing: {required}")

    reference_path = root / "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/compose/plan/ColumnObjectNormalizerF5Test.java"
    canonical = reference_path.read_text(encoding="utf-8")
    replacements = {
        (row["target_report_fqcn"], row["current_report_fqcn"]) for row in rename_rows
    } | {
        (row["target_top_level_fqcn"], row["current_top_level_fqcn"]) for row in rename_rows
    } | {
        (Path(row["target_source_path"]).stem, Path(row["current_source_path"]).stem) for row in rename_rows
    }
    for target, current in sorted(replacements, key=lambda item: len(item[0]), reverse=True):
        canonical = canonical.replace(target, current)
    predecessor = subprocess.run(
        ["git", "show", f"{STEP1_DELIVERY_COMMIT}:{reference_path.relative_to(root).as_posix()}"],
        cwd=root,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    if canonical.encode("utf-8") != predecessor:
        fail("E_DELTA_SCOPE", "reference-only test source contains an unplanned content delta")


def validate_runner_ownership(
    sources: list[dict[str, str]],
    discovery: list[dict[str, str]],
    executions: list[dict[str, str]],
    structural: list[dict[str, str]],
) -> dict[str, Any]:
    source_by_id = {row["source_id"]: row for row in sources}
    owner_by_source: dict[str, str] = {}
    source_counts: Counter[str] = Counter()
    for source in sources:
        if source["reactor_member"] != "true" or source["kind"] != "executable":
            continue
        filename = Path(source["source_path"]).name
        surefire = (
            any(fnmatch.fnmatch(filename, pattern.removeprefix("**/")) for pattern in SUREFIRE_INCLUDES)
            and not any(fnmatch.fnmatch(filename, pattern.removeprefix("**/")) for pattern in SUREFIRE_EXCLUDES)
        )
        failsafe = any(fnmatch.fnmatch(filename, pattern.removeprefix("**/")) for pattern in FAILSAFE_INCLUDES)
        owners = [name for name, matched in (("surefire", surefire), ("failsafe", failsafe)) if matched]
        if len(owners) != 1:
            fail("E_RUNNER_OWNERSHIP", f"runner owner count={len(owners)} source={source['source_path']}")
        owner_by_source[source["source_id"]] = owners[0]
        source_counts[owners[0]] += 1

    discovery_report_counts: Counter[str] = Counter()
    positive_report_counts: Counter[str] = Counter()
    report_owners: dict[str, str] = {}
    structural_refs = {(row["source_id"], row["report_fqcn"]) for row in structural}
    for row in discovery:
        if row["report_fqcn"] == "none":
            continue
        owner = owner_by_source.get(row["source_id"])
        if owner is None:
            fail("E_RUNNER_OWNERSHIP", f"discovery report has no executable source owner: {row['report_fqcn']}")
        previous = report_owners.setdefault(row["report_fqcn"], owner)
        if previous != owner:
            fail("E_RUNNER_OWNERSHIP", f"report has multiple runner owners: {row['report_fqcn']}")
        discovery_report_counts[owner] += 1
        if (row["source_id"], row["report_fqcn"]) not in structural_refs:
            positive_report_counts[owner] += 1

    structural_counts: Counter[str] = Counter()
    for row in structural:
        expected = owner_by_source.get(row["source_id"])
        if expected != row["runner"] or report_owners.get(row["report_fqcn"]) != row["runner"]:
            fail("E_RUNNER_OWNERSHIP", f"structural report owner differs: {row['report_fqcn']}")
        structural_counts[row["runner"]] += 1

    execution_counts: Counter[str] = Counter()
    step_counts: Counter[tuple[str, str]] = Counter()
    for row in executions:
        if row["runner"] not in {"surefire", "failsafe"}:
            fail("E_RUNNER_OWNERSHIP", f"unknown execution runner: {row['runner']}")
        expected = owner_by_source.get(row["source_id"])
        if expected != row["runner"] or report_owners.get(row["report_fqcn"]) != row["runner"]:
            fail("E_RUNNER_OWNERSHIP", f"execution owner differs: {row['execution_key']}")
        execution_counts[row["runner"]] += 1
        step_counts[(row["execution_step"], row["runner"])] += 1

    expected = {
        "executable_reactor_sources": 514,
        "source_owners": {"surefire": 471, "failsafe": 43},
        "discovery_report_containers": {"surefire": 732, "failsafe": 72},
        "positive_report_owners": {"surefire": 677, "failsafe": 68},
        "structural_report_owners": {"surefire": 55, "failsafe": 4},
        "execution_owners": {"surefire": 677, "failsafe": 93},
        "step2_owners": {"surefire": 677, "failsafe": 47},
        "step3_owners": {"surefire": 0, "failsafe": 46},
        "overlap": 0,
        "orphan": 0,
    }
    actual = {
        "executable_reactor_sources": sum(source_counts.values()),
        "source_owners": {"surefire": source_counts["surefire"], "failsafe": source_counts["failsafe"]},
        "discovery_report_containers": {
            "surefire": discovery_report_counts["surefire"],
            "failsafe": discovery_report_counts["failsafe"],
        },
        "positive_report_owners": {
            "surefire": positive_report_counts["surefire"],
            "failsafe": positive_report_counts["failsafe"],
        },
        "structural_report_owners": {
            "surefire": structural_counts["surefire"],
            "failsafe": structural_counts["failsafe"],
        },
        "execution_owners": {"surefire": execution_counts["surefire"], "failsafe": execution_counts["failsafe"]},
        "step2_owners": {"surefire": step_counts[("2", "surefire")], "failsafe": step_counts[("2", "failsafe")]},
        "step3_owners": {"surefire": step_counts[("3", "surefire")], "failsafe": step_counts[("3", "failsafe")]},
        "overlap": 0,
        "orphan": 0,
    }
    if actual != expected:
        fail("E_RUNNER_OWNERSHIP", f"runner ownership distribution differs: {actual}")
    return actual


def transformed_execution_and_structural(
    step1_dir: Path,
    step1: Any,
    execution_map: dict[str, dict[str, str]],
    corrective_execution_keys: set[str],
    sources: list[dict[str, str]],
    discovery: list[dict[str, str]],
) -> tuple[list[dict[str, str]], list[dict[str, str]], dict[str, str]]:
    """Split positive executions from controlled zero-test outer containers.

    JUnit discovery intentionally retains every ClassSource container. Surefire
    also emits an XML suite for an outer class that only contains ``@Nested``
    tests, but that outer suite has ``tests=0``. Such XML is useful structural
    evidence; treating it as a positive execution key would contradict the
    fail-closed zero-test rule. The immutable Step 1 input is therefore
    preserved, while the Step 2 successor reclassifies exactly those reviewed
    outer containers into a separate structural inventory.
    """
    source_by_id = {row["source_id"]: row for row in sources}
    structural_discovery = {
        (row["source_id"], row["report_fqcn"]): row
        for row in discovery
        if row["report_fqcn"] != "none"
        and row["discovered_test_nodes"] == "0"
        and row["runtime_deferred_containers"] == "0"
    }
    if len(structural_discovery) != EXPECTED_STRUCTURAL_REPORTS:
        fail(
            "E_STRUCTURAL_REPORT",
            f"zero-container discovery count={len(structural_discovery)}, "
            f"expected={EXPECTED_STRUCTURAL_REPORTS}",
        )
    for (source_id, report_fqcn), discovery_row in structural_discovery.items():
        if report_fqcn != discovery_row["source_fqcn"]:
            fail("E_STRUCTURAL_REPORT", f"zero container is not the outer source: {report_fqcn}")
        if source_by_id.get(source_id, {}).get("top_level_fqcn") != report_fqcn:
            fail("E_STRUCTURAL_REPORT", f"zero container/source identity differs: {report_fqcn}")

    transformed: list[tuple[dict[str, str], dict[str, str]]] = []
    for original in read_tsv(step1_dir / step1.EXECUTION_FILE, step1.EXECUTION_HEADER):
        row = dict(original)
        rename = execution_map.get(row["execution_key"])
        if rename:
            semantic = {
                "runner": "runner", "lane": "lane", "variant_key": "variant_key",
                "db_kind": "db_kind", "infra_kind": "infra_kind",
                "execution_step": "execution_step", "required": "required",
                "owner": "owner", "optional_reason": "optional_reason", "review_at": "review_at",
            }
            is_corrective = row["execution_key"] in corrective_execution_keys
            for current, planned in semantic.items():
                if not is_corrective and row[current] != rename[planned]:
                    fail("E_RENAME_DELTA", f"rename semantic drift for {row['execution_key']} field={current}")
                row[current] = rename[planned]
            row["execution_key"] = rename["target_execution_key"]
            row["source_id"] = rename["target_source_id"]
            row["report_fqcn"] = rename["target_report_fqcn"]
        expected_key = step1.execution_key(row["runner"], row["lane"], row["variant_key"], row["report_fqcn"])
        if row["execution_key"] != expected_key:
            fail("E_EXEC_DELTA", f"execution key framing differs: {row['report_fqcn']}")
        transformed.append((original, row))

    positive = [
        row for _, row in transformed
        if (row["source_id"], row["report_fqcn"]) not in structural_discovery
    ]
    structural_pairs = [
        (original, row) for original, row in transformed
        if (row["source_id"], row["report_fqcn"]) in structural_discovery
    ]
    positive_keys = {row["execution_key"] for row in positive}
    if len(positive) != EXPECTED_POSITIVE_EXECUTIONS or len(positive_keys) != EXPECTED_POSITIVE_EXECUTIONS:
        fail(
            "E_EXEC_DELTA",
            f"positive execution cardinality/uniqueness differs: {len(positive)}/{len(positive_keys)}",
        )
    if len(structural_pairs) != EXPECTED_STRUCTURAL_REPORTS:
        fail("E_STRUCTURAL_REPORT", f"structural execution rows={len(structural_pairs)}")

    semantic_sibling_fields = (
        "source_id", "runner", "lane", "variant_key", "db_kind", "infra_kind",
        "execution_step", "required", "owner", "optional_reason", "review_at",
    )
    structural_rows: list[dict[str, str]] = []
    predecessor_to_structural: dict[str, str] = {}
    for original, row in structural_pairs:
        siblings = sorted(
            candidate["execution_key"]
            for candidate in positive
            if all(candidate[name] == row[name] for name in semantic_sibling_fields)
            and candidate["report_fqcn"].startswith(row["report_fqcn"] + "$")
        )
        if not siblings:
            fail("E_STRUCTURAL_REPORT", f"zero outer has no positive nested sibling: {row['report_fqcn']}")
        if original["execution_key"] in predecessor_to_structural:
            fail("E_STRUCTURAL_REPORT", f"duplicate predecessor structural key: {original['execution_key']}")
        predecessor_to_structural[original["execution_key"]] = row["report_fqcn"]
        source = source_by_id[row["source_id"]]
        discovery_row = structural_discovery[(row["source_id"], row["report_fqcn"])]
        if discovery_row["module"] != row["owner"]:
            fail("E_STRUCTURAL_REPORT", f"structural module/owner differs: {row['report_fqcn']}")
        structural_rows.append({
            "module": discovery_row["module"],
            "source_id": row["source_id"],
            "source_fqcn": source["top_level_fqcn"],
            "report_fqcn": row["report_fqcn"],
            "runner": row["runner"],
            "lane": row["lane"],
            "variant_key": row["variant_key"],
            "owner": row["owner"],
            "discovered_test_nodes": discovery_row["discovered_test_nodes"],
            "runtime_deferred_containers": discovery_row["runtime_deferred_containers"],
            "positive_sibling_execution_keys": ",".join(siblings),
            "disposition": "reviewed-structural-container",
            "rationale": "outer ClassSource has zero direct nodes and is represented by positive nested sibling reports",
        })

    structural_identities = [
        (row["runner"], row["lane"], row["variant_key"], row["report_fqcn"])
        for row in structural_rows
    ]
    if len(set(structural_identities)) != EXPECTED_STRUCTURAL_REPORTS:
        fail("E_STRUCTURAL_REPORT", "structural report identities are not unique")
    if len({row["report_fqcn"] for row in structural_rows}) != EXPECTED_STRUCTURAL_REPORTS:
        fail("E_STRUCTURAL_REPORT", "typed structural report FQCNs are not unique")
    if Counter(row["runner"] for row in structural_rows) != Counter({"surefire": 55, "failsafe": 4}):
        fail("E_STRUCTURAL_REPORT", "structural runner distribution differs from 55/4")
    if any(row["execution_step"] != "2" or row["required"] != "true" for _, row in structural_pairs):
        fail("E_STRUCTURAL_REPORT", "structural reports must be required Step 2 containers")

    positive_report_refs = {(row["source_id"], row["report_fqcn"]) for row in positive}
    discovered_positive_refs = {
        (row["source_id"], row["report_fqcn"])
        for row in discovery
        if row["report_fqcn"] != "none"
        and (row["source_id"], row["report_fqcn"]) not in structural_discovery
    }
    if positive_report_refs != discovered_positive_refs:
        fail("E_EXEC_DELTA", "positive execution/discovery report sets differ")
    return (
        sorted(positive, key=lambda item: item["execution_key"]),
        sorted(
            structural_rows,
            key=lambda item: (item["module"], item["source_fqcn"], item["variant_key"], item["report_fqcn"]),
        ),
        predecessor_to_structural,
    )


def transformed_migration(
    step1_dir: Path,
    step1: Any,
    positive_migration_key_map: dict[str, str],
    structural_parent_refs: dict[str, str],
    execution_map: dict[str, dict[str, str]],
) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    positive_renamed = 0
    structural_edges = 0
    structural_renamed = 0
    for original in read_tsv(step1_dir / step1.MIGRATION_FILE, step1.MIGRATION_HEADER):
        row = dict(original)
        predecessor_successor_key = row["successor_execution_key"]
        structural_ref = structural_parent_refs.get(predecessor_successor_key)
        successor = positive_migration_key_map.get(predecessor_successor_key)
        if structural_ref is not None:
            row["successor_execution_key"] = "none"
            row[MIGRATION_STRUCTURAL_FIELD] = structural_ref
            row["disposition"] = "structural-container-successor"
            row["rationale"] = (
                "historical zero-test outer suite is preserved as a typed structural report; "
                "positive regression ownership remains with its nested sibling reports"
            )
            structural_edges += 1
            structural_renamed += int(predecessor_successor_key in execution_map)
        else:
            row[MIGRATION_STRUCTURAL_FIELD] = "none"
        if successor is not None:
            row["successor_execution_key"] = successor
            positive_renamed += 1
        result.append(row)
    positive_edges = sum(row["successor_execution_key"] != "none" for row in result)
    xor_valid = all(
        (row["successor_execution_key"] != "none")
        != (row[MIGRATION_STRUCTURAL_FIELD] != "none")
        for row in result
    )
    if (
        len(result) != 519
        or positive_edges != EXPECTED_POSITIVE_PREDECESSOR_EDGES
        or structural_edges != EXPECTED_STRUCTURAL_PREDECESSOR_EDGES
        or positive_renamed != 46
        or structural_renamed != 4
        or not xor_valid
    ):
        fail(
            "E_MIGRATION_DELTA",
            "migration split differs "
            f"rows={len(result)} positive={positive_edges} structural={structural_edges} "
            f"positive_renamed={positive_renamed} structural_renamed={structural_renamed} xor={xor_valid}",
        )
    by_group: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in result:
        by_group[row["mapping_group"]].append(row)
    for row in result:
        if row[MIGRATION_STRUCTURAL_FIELD] == "none":
            continue
        group = by_group[row["mapping_group"]]
        if (
            len(group) != 1
            or row["relation"] != "1:1"
            or row["declared_old_count"] != "1"
            or row["declared_successor_count"] != "1"
        ):
            fail("E_MIGRATION_DELTA", f"structural predecessor group cardinality differs: {row['mapping_group']}")
    return sorted(
        result,
        key=lambda row: (
            row["mapping_group"],
            row["predecessor_node"],
            row["successor_execution_key"],
            row[MIGRATION_STRUCTURAL_FIELD],
        ),
    )


def transformed_discovery(
    step1_dir: Path,
    root: Path,
    step1: Any,
    source_rows: list[dict[str, str]],
    source_map: dict[str, dict[str, str]],
    rename_rows: list[dict[str, str]],
) -> list[dict[str, str]]:
    report_map = {
        (row["current_source_id"], row["current_report_fqcn"]): row["target_report_fqcn"]
        for row in rename_rows
    }
    source_by_id = {row["source_id"]: row for row in source_rows}
    module_hashes: dict[str, tuple[str, str]] = {}
    result: list[dict[str, str]] = []
    for original in read_tsv(step1_dir / step1.DISCOVERY_FILE, step1.DISCOVERY_HEADER):
        row = dict(original)
        rename = source_map.get(row["source_id"])
        if rename:
            old_id = row["source_id"]
            row["source_id"] = rename["target_source_id"]
            row["source_fqcn"] = rename["target_top_level_fqcn"]
            if row["report_fqcn"] != "none":
                try:
                    row["report_fqcn"] = report_map[(old_id, row["report_fqcn"])]
                except KeyError:
                    fail("E_DISCOVERY_DELTA", f"missing report rename: {original['report_fqcn']}")
        source = source_by_id.get(row["source_id"])
        if source is None:
            fail("E_DISCOVERY_DELTA", f"unknown discovery source: {row['source_id']}")
        row["source_sha256"] = step1.sha256_file(root / source["source_path"])
        if row["module"] not in module_hashes:
            module_hashes[row["module"]] = (
                step1.directory_tree_hash(root / row["module"] / "target/test-classes"),
                step1.directory_tree_hash(root / row["module"] / "target/classes"),
            )
        row["test_classes_sha256"], row["main_classes_sha256"] = module_hashes[row["module"]]
        result.append(row)
    if len(result) != 820:
        fail("E_DISCOVERY_DELTA", f"discovery row count={len(result)}, expected=820")
    return sorted(result, key=lambda row: (row["module"], row["source_fqcn"], row["report_fqcn"]))


def raw_discovery_rows(run_dir: Path, step1: Any) -> list[dict[str, str]]:
    scan = read_json(run_dir / "source-scan.json")
    result: list[dict[str, str]] = []
    for entry in scan.get("selector_index", []):
        result.extend(read_tsv(run_dir / entry["discovery"], [
            "module", "source_fqcn", "report_fqcn", "discovered_test_nodes",
            "runtime_deferred_containers", "engine_ids",
        ]))
    return result


def validate_raw_discovery(expected: list[dict[str, str]], actual: list[dict[str, str]]) -> None:
    fields = ["module", "source_fqcn", "report_fqcn", "discovered_test_nodes", "runtime_deferred_containers", "engine_ids"]
    expected_counter = Counter(tuple(row[name] for name in fields) for row in expected)
    actual_counter = Counter(tuple(row[name] for name in fields) for row in actual)
    if len(expected) != 820 or sum(row["report_fqcn"] == "none" for row in expected) != 16:
        fail("E_DISCOVERY_DELTA", "successor discovery cardinality is not 804 reports + 16 reviewed none rows")
    if len(actual) != 820 or sum(row["report_fqcn"] == "none" for row in actual) != 16 or expected_counter != actual_counter:
        fail("E_DISCOVERY_DELTA", "post-rename JUnit discovery differs beyond the approved report rename")


def classpath_rows(run_dir: Path, root: Path, step1: Any) -> list[dict[str, str]]:
    scan = read_json(run_dir / "source-scan.json")
    result = step1.load_classpath_rows(root, run_dir, scan)
    if len(result) != 2395:
        fail("E_CLASSPATH_DELTA", f"classpath entries={len(result)}, expected=2395")
    return result


def classpath_content_amendment(root: Path, step1: Any) -> dict[str, Any]:
    r5_source_amendment(root, step1)
    r6_source_amendment(root, step1)
    mongo_entry_path = root / CLASSPATH_AMENDMENT_IDENTITY.removeprefix("repo:")
    mongo_source_path = root / CLASSPATH_AMENDMENT_SOURCE
    core_entry_path = root / CORE_CLASSPATH_IDENTITY.removeprefix("repo:")
    core_source_path = root / CORE_CLASSPATH_SOURCE
    model_entry_path = root / MODEL_CLASSPATH_IDENTITY.removeprefix("repo:")
    model_source_path = root / MODEL_CLASSPATH_SOURCE
    if not mongo_entry_path.is_dir() or not mongo_source_path.is_file():
        fail("E_CLASSPATH_DELTA", "reviewed Mongo classpath amendment source/output is missing")
    if not core_entry_path.is_dir() or not core_source_path.is_file():
        fail("E_CLASSPATH_DELTA", "reviewed core classpath amendment source/output is missing")
    if not model_entry_path.is_dir() or not model_source_path.is_file():
        fail("E_CLASSPATH_DELTA", "reviewed model classpath amendment source/output is missing")
    mongo_sha256 = step1.classpath_entry_sha256(root, mongo_entry_path.resolve())
    core_sha256 = step1.classpath_entry_sha256(root, core_entry_path.resolve())
    model_sha256 = step1.classpath_entry_sha256(root, model_entry_path.resolve())
    if mongo_sha256 != CLASSPATH_AMENDMENT_SUCCESSOR_SHA256:
        fail(
            "E_CLASSPATH_DELTA",
            "reviewed Mongo target/classes hash differs "
            f"actual={mongo_sha256} expected={CLASSPATH_AMENDMENT_SUCCESSOR_SHA256}",
        )
    if core_sha256 != CORE_CLASSPATH_SUCCESSOR_SHA256:
        fail(
            "E_CLASSPATH_DELTA",
            "reviewed core target/classes hash differs "
            f"actual={core_sha256} expected={CORE_CLASSPATH_SUCCESSOR_SHA256}",
        )
    if model_sha256 != MODEL_CLASSPATH_SUCCESSOR_SHA256:
        fail(
            "E_CLASSPATH_DELTA",
            "reviewed model target/classes hash differs "
            f"actual={model_sha256} expected={MODEL_CLASSPATH_SUCCESSOR_SHA256}",
        )
    return {
        "classification": "reviewed-classpath-content-delta-v3",
        "reviewed_rows": 1 + CORE_CLASSPATH_ROWS + MODEL_CLASSPATH_ROWS,
        "amendments": [
            {
                "classification": "single-module-classpath-content-delta",
                "module": CLASSPATH_AMENDMENT_MODULE,
                "ordinal": CLASSPATH_AMENDMENT_ORDINAL,
                "entry_identity": CLASSPATH_AMENDMENT_IDENTITY,
                "occurrences": 1,
                "parent_entry_sha256": CLASSPATH_AMENDMENT_PARENT_SHA256,
                "successor_entry_sha256": mongo_sha256,
                "change_source": CLASSPATH_AMENDMENT_SOURCE,
                "change_source_sha256": step1.sha256_file(mongo_source_path),
                "reason": "Mongo loader fail-closed ordering fix changed one module main-classes row",
            },
            {
                "classification": "shared-runtime-classpath-content-delta",
                "entry_identity": CORE_CLASSPATH_IDENTITY,
                "occurrences": CORE_CLASSPATH_ROWS,
                "parent_entry_sha256": CORE_CLASSPATH_PARENT_SHA256,
                "successor_entry_sha256": core_sha256,
                "change_source": CORE_CLASSPATH_SOURCE,
                "change_source_sha256": step1.sha256_file(core_source_path),
                "reason": "MultiThreadExecutor completion fix changed the shared foggy-core main-classes row",
            },
            {
                "classification": "shared-runtime-classpath-content-delta",
                "entry_identity": MODEL_CLASSPATH_IDENTITY,
                "occurrences": MODEL_CLASSPATH_ROWS,
                "parent_entry_sha256": MODEL_CLASSPATH_PARENT_SHA256,
                "successor_entry_sha256": model_sha256,
                "change_source": MODEL_CLASSPATH_SOURCE,
                "change_source_sha256": step1.sha256_file(model_source_path),
                "reason": (
                    "PredefinedCalculatedFieldInjector immutable-list fix changed the shared "
                    "foggy-dataset-model main-classes row"
                ),
            },
        ],
    }


def validate_classpath_successor(
    step1_dir: Path,
    rows: list[dict[str, str]],
    step1: Any,
) -> dict[str, Any]:
    root = step1_dir.parent.parent
    parent = read_tsv(step1_dir / step1.CLASSPATH_FILE, step1.CLASSPATH_HEADER)
    if len(rows) != 2395 or len(parent) != 2395:
        fail("E_CLASSPATH_DELTA", f"classpath cardinality differs successor={len(rows)} parent={len(parent)}")
    identity_fields = ("module", "ordinal", "entry_identity")
    actual_identity = [tuple(row[name] for name in identity_fields) for row in rows]
    parent_identity = [tuple(row[name] for name in identity_fields) for row in parent]
    if actual_identity != parent_identity:
        fail("E_CLASSPATH_DELTA", "module/ordinal/identity differs from the confirmed Step 1 classpath")
    amendment = classpath_content_amendment(root, step1)
    mongo_amendment, core_amendment, model_amendment = amendment["amendments"]
    amended_coordinates = (
        CLASSPATH_AMENDMENT_MODULE,
        CLASSPATH_AMENDMENT_ORDINAL,
        CLASSPATH_AMENDMENT_IDENTITY,
    )
    mongo_rows = 0
    core_rows = 0
    model_rows = 0
    for actual, frozen in zip(rows, parent, strict=True):
        coordinates = tuple(actual[name] for name in identity_fields)
        if coordinates == amended_coordinates:
            mongo_rows += 1
            if frozen["entry_sha256"] != CLASSPATH_AMENDMENT_PARENT_SHA256:
                fail("E_CLASSPATH_DELTA", "reviewed classpath parent hash differs")
            if actual["entry_sha256"] != mongo_amendment["successor_entry_sha256"]:
                fail("E_CLASSPATH_DELTA", "reviewed classpath successor hash differs")
        elif actual["entry_identity"] == CORE_CLASSPATH_IDENTITY:
            core_rows += 1
            if frozen["entry_sha256"] != CORE_CLASSPATH_PARENT_SHA256:
                fail("E_CLASSPATH_DELTA", f"reviewed core classpath parent hash differs: {coordinates}")
            if actual["entry_sha256"] != core_amendment["successor_entry_sha256"]:
                fail("E_CLASSPATH_DELTA", f"reviewed core classpath successor hash differs: {coordinates}")
        elif actual["entry_identity"] == MODEL_CLASSPATH_IDENTITY:
            model_rows += 1
            if frozen["entry_sha256"] != MODEL_CLASSPATH_PARENT_SHA256:
                fail("E_CLASSPATH_DELTA", f"reviewed model classpath parent hash differs: {coordinates}")
            if actual["entry_sha256"] != model_amendment["successor_entry_sha256"]:
                fail("E_CLASSPATH_DELTA", f"reviewed model classpath successor hash differs: {coordinates}")
        elif actual["entry_sha256"] != frozen["entry_sha256"]:
            fail("E_CLASSPATH_DELTA", f"unreviewed classpath content hash differs: {coordinates}")
    if mongo_rows != 1 or core_rows != CORE_CLASSPATH_ROWS or model_rows != MODEL_CLASSPATH_ROWS:
        fail(
            "E_CLASSPATH_DELTA",
            f"reviewed classpath identity counts mongo/core/model={mongo_rows}/{core_rows}/{model_rows}, "
            f"expected=1/{CORE_CLASSPATH_ROWS}/{MODEL_CLASSPATH_ROWS}",
        )
    return amendment


def superseded_provenance() -> dict[str, str]:
    return {
        "run_id": SUPERSEDED_RUN_ID,
        "contract_freeze_sha256": SUPERSEDED_FREEZE_SHA256,
        "contract_manifest_sha256": SUPERSEDED_MANIFEST_SHA256,
        "confirmed_summary_sha256": SUPERSEDED_SUMMARY_SHA256,
        "reason": (
            "confirmed r7 authority lacked explicit no-test defaults, a shared workspace lock, "
            "publish compare-and-swap, and typed report run/cardinality provenance"
        ),
    }


def corrective_lane_amendment() -> dict[str, Any]:
    return {
        "classification": "external-mongo-lane-correction-v1",
        "source": "Step 2 fail-closed all-unit regression triage",
        "corrected_sources": 2,
        "corrected_positive_execution_keys": 2,
        "from": "surefire/unit/unit/hermetic/step2",
        "to": "failsafe/external-mongo/mongo6/mongodb/step3-required",
        "predecessor_edges_affected": 0,
    }


def parent_link(step1_dir: Path, step1: Any) -> dict[str, Any]:
    step1.validate_hashes(step1_dir)
    root = step1_dir.parent.parent
    ancestor = subprocess.run(
        ["git", "merge-base", "--is-ancestor", STEP1_DELIVERY_COMMIT, "HEAD"],
        cwd=root,
    )
    if ancestor.returncode != 0:
        fail("E_PARENT_LINK", f"Step 1 delivery commit is not an ancestor: {STEP1_DELIVERY_COMMIT}")
    try:
        committed_manifest = subprocess.run(
            ["git", "show", f"{STEP1_DELIVERY_COMMIT}:scripts/v934/{step1.HASH_FILE}"],
            cwd=root,
            check=True,
            stdout=subprocess.PIPE,
        ).stdout
    except subprocess.CalledProcessError:
        fail("E_PARENT_LINK", "Step 1 delivery commit does not contain the contract manifest")
    if step1.sha256_bytes(committed_manifest) != EXPECTED_PARENT_MANIFEST:
        fail("E_PARENT_LINK", "Step 1 delivery commit contains a different contract manifest")
    freeze = read_json(step1_dir / step1.FREEZE_FILE)
    values = {
        "step1_contract_manifest_sha256": step1.sha256_file(step1_dir / step1.HASH_FILE),
        "step1_contract_freeze_sha256": step1.sha256_file(step1_dir / step1.FREEZE_FILE),
        "step1_confirmed_summary_sha256": EXPECTED_PARENT_SUMMARY,
        "step1_rename_plan_sha256": step1.sha256_file(step1_dir / step1.RENAME_FILE),
    }
    expected = {
        "step1_contract_manifest_sha256": EXPECTED_PARENT_MANIFEST,
        "step1_contract_freeze_sha256": EXPECTED_PARENT_FREEZE,
        "step1_confirmed_summary_sha256": EXPECTED_PARENT_SUMMARY,
        "step1_rename_plan_sha256": EXPECTED_RENAME_PLAN,
    }
    if values != expected or freeze.get("status") != "confirmed" or freeze.get("decision") != "passed":
        fail("E_PARENT_LINK", f"confirmed Step 1 parent differs: {values}")
    corrective_digest = step1.sha256_file(step1_dir / CORRECTIVE_RENAME_FILE)
    if corrective_digest != EXPECTED_CORRECTIVE_RENAME_PLAN:
        fail("E_CORRECTIVE_PLAN", "Step 2 corrective rename plan digest differs")
    return {
        "schema_version": 6,
        "generation": "step2-post-rename",
        "successor_semantics": SUCCESSOR_SEMANTICS,
        **values,
        "step2_corrective_rename_plan_sha256": corrective_digest,
        "step2_test_source_amendment_sha256": EXPECTED_TEST_SOURCE_AMENDMENT,
        "step2_r5_source_amendment_sha256": EXPECTED_R5_SOURCE_AMENDMENT,
        "step2_r6_source_amendment_sha256": EXPECTED_R6_SOURCE_AMENDMENT,
        "step2_r7_runner_amendment_sha256": EXPECTED_R7_RUNNER_AMENDMENT,
        "step2_r8_authority_amendment_sha256": EXPECTED_R8_AUTHORITY_AMENDMENT,
        "step1_delivery_commit": STEP1_DELIVERY_COMMIT,
        "approved_delta": "33 sources / 62 planned reports / 74 planned execution keys / 50 predecessor edges",
        "corrective_approved_delta": "2 sources / 2 planned reports / 2 planned execution keys / 0 predecessor edges",
        "supersedes": superseded_provenance(),
        "corrective_lane_amendment": corrective_lane_amendment(),
        "test_source_amendment": test_source_amendment(root, step1),
        "r5_source_amendment": r5_source_amendment(root, step1),
        "r6_source_amendment": r6_source_amendment(root, step1),
        "r7_runner_amendment": r7_runner_amendment(root, step1),
        "r8_authority_amendment": r8_authority_amendment(root, step1),
        "classpath_content_amendment": classpath_content_amendment(root, step1),
        "zero_container_amendment": {
            "classification": "structural-split-v1",
            "discovery_contract": "preserved 820 rows / 804 ClassSource containers",
            "structural_reports": 59,
            "positive_execution_keys": 770,
            "required_step2": 724,
            "typed_predecessor_refs": "480 execution / 39 structural",
        },
    }


def successor_protected_paths(root: Path, step1: Any) -> list[Path]:
    result = list(step1.protected_source_paths(root))
    explicit = [
        "scripts/v934/step2_successor_tool.py",
        "scripts/v934/step2-corrective-rename-plan.tsv",
        "scripts/v934/step2-test-source-amendment.tsv",
        "scripts/v934/step2-r5-source-amendment.tsv",
        "scripts/v934/step2-r6-source-amendment.tsv",
        "scripts/v934/step2-r7-runner-amendment.tsv",
        "scripts/v934/step2-r8-authority-amendment.tsv",
        "scripts/v934/authority_runner_lib.sh",
        "scripts/verify-v934-step2-successor.sh",
        "scripts/verify-v934-unit.sh",
        "scripts/verify-v934-integration.sh",
        "scripts/v934/step2_report_tool.py",
        "scripts/run-ai-domain-direct.sh",
        "scripts/run-ai-llm-comparison.sh",
        "scripts/verify-pivot-v9-release.sh",
        "scripts/verify-pivot-v9-release.ps1",
        "scripts/verify-v310-field-permissions.sh",
        "scripts/verify-v38-engine-evidence.sh",
        "scripts/verify-v39-engine-production-gate.sh",
        "addons/foggy-data-viewer/frontend/USAGE.md",
        "addons/foggy-dataset-model-vector/OPTIMIZATION_PLAN.md",
        "addons/foggy-dataset-vector/src/test/resources/README.md",
        "docs/dev-guide/compose-query.md",
        "docs/dev-guide/preagg-optimization-issues.md",
        "docs/issues-tracker.md",
        "docs/unit-test-analysis.md",
        "foggy-dataset-mcp/TEST_README.md",
        "foggy-dataset-mcp/src/test/java/com/foggyframework/dataset/mcp/integration/README.md",
    ]
    for relative in explicit:
        path = root / relative
        if path.is_file():
            result.append(path)
    return sorted(set(result))


def executable_toolchain(root: Path, step1: Any) -> dict[str, str]:
    paths = {
        "step2_tool": "scripts/v934/step2_successor_tool.py",
        "report_tool": "scripts/v934/step2_report_tool.py",
        "authority_runner_lib": "scripts/v934/authority_runner_lib.sh",
        "successor_wrapper": "scripts/verify-v934-step2-successor.sh",
        "unit_runner": "scripts/verify-v934-unit.sh",
        "integration_runner": "scripts/verify-v934-integration.sh",
    }
    result: dict[str, str] = {}
    for label, relative in paths.items():
        path = root / relative
        if not path.is_file():
            fail("E_TOOLCHAIN", f"required executable is missing: {relative}")
        mode = path.stat().st_mode & 0o777
        if mode != 0o755:
            fail("E_TOOLCHAIN", f"required executable mode is {mode:04o}, expected 0755: {relative}")
        result[f"{label}_sha256"] = step1.sha256_file(path)
        result[f"{label}_mode"] = f"{mode:04o}"
    return result


def write_hash_manifest(directory: Path, step1: Any) -> None:
    actual = sorted(path.name for path in directory.iterdir() if path.is_file() and path.name != HASH_FILE)
    if actual != SUCCESSOR_FILES:
        fail("E_HASH_SET", f"successor file set differs expected={SUCCESSOR_FILES} actual={actual}")
    with (directory / HASH_FILE).open("w", encoding="utf-8", newline="") as stream:
        for name in SUCCESSOR_FILES:
            stream.write(f"{step1.sha256_file(directory / name)}  {name}\n")


def validate_hashes(directory: Path, step1: Any) -> None:
    actual = sorted(path.name for path in directory.iterdir() if path.is_file())
    expected_all = SUCCESSOR_FILES + [HASH_FILE]
    if actual != sorted(expected_all):
        fail("E_HASH_SET", f"successor file set differs expected={sorted(expected_all)} actual={actual}")
    manifest = directory / HASH_FILE
    if not manifest.is_file():
        fail("E_HASH_SET", "SHA256SUMS is missing")
    rows = []
    for line in manifest.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._-]+)", line)
        if not match:
            fail("E_HASH_SET", f"invalid manifest line: {line}")
        rows.append((match.group(1), match.group(2)))
    if [name for _, name in rows] != SUCCESSOR_FILES:
        fail("E_HASH_SET", "manifest file set/order differs")
    for digest, name in rows:
        if step1.sha256_file(directory / name) != digest:
            fail("E_STALE_HASH", f"stale hash: {name}")


def generate(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    run_dir = args.run_dir.resolve()
    output = args.output_dir.resolve()
    if output.exists() and any(output.iterdir()):
        fail("E_OUTPUT", f"successor output is not empty: {output}")
    output.mkdir(parents=True, exist_ok=True)
    step1 = load_step1_tool(root)
    step1_dir = root / "scripts/v934"
    rename_rows, source_map, execution_map, migration_key_map, corrective_keys = rename_maps(step1_dir, step1)
    validate_git_delta_scope(root, rename_rows)
    validate_canonical_rename_content(root, rename_rows, step1)
    validate_legacy_token_residue(root, rename_rows)
    sources = transformed_sources(step1_dir, root, step1, source_map)
    discovery = transformed_discovery(step1_dir, root, step1, sources, source_map, rename_rows)
    executions, structural, structural_parent_refs = transformed_execution_and_structural(
        step1_dir, step1, execution_map, corrective_keys, sources, discovery
    )
    positive_rename_rows = [
        row for row in rename_rows
        if row["current_execution_key"] not in structural_parent_refs
    ]
    if (
        len(positive_rename_rows) != 72
        or len({row["current_report_fqcn"] for row in positive_rename_rows}) != 60
    ):
        fail("E_RENAME_DELTA", "positive rename split differs from 72 executions / 60 reports")
    positive_migration_key_map = {
        key: value for key, value in migration_key_map.items()
        if key not in structural_parent_refs
    }
    migration = transformed_migration(
        step1_dir,
        step1,
        positive_migration_key_map,
        structural_parent_refs,
        execution_map,
    )
    scan = read_json(run_dir / "source-scan.json")
    if scan.get("protected_source_sha256") != step1.tree_hash(root, step1.protected_source_paths(root)):
        fail("E_SOURCE_HASH", "discovery run was not produced from the current protected source state")
    validate_raw_discovery(discovery, raw_discovery_rows(run_dir, step1))
    classpath = classpath_rows(run_dir, root, step1)
    classpath_amendment = validate_classpath_successor(step1_dir, classpath, step1)
    runner = effective_runner_contract(root, step1)
    runner["ownership"] = validate_runner_ownership(sources, discovery, executions, structural)
    parent = parent_link(step1_dir, step1)

    source_ids = {row["source_id"] for row in sources}
    discovery_reports = {row["report_fqcn"] for row in discovery if row["report_fqcn"] != "none"}
    execution_keys = {row["execution_key"] for row in executions}
    structural_fqcns = {row["report_fqcn"] for row in structural}
    if any(row["source_id"] not in source_ids or row["report_fqcn"] not in discovery_reports for row in executions):
        fail("E_EXEC_DELTA", "execution source/report is not in successor inventory")
    for row in migration:
        execution_ref = row["successor_execution_key"]
        structural_ref = row[MIGRATION_STRUCTURAL_FIELD]
        if execution_ref != "none":
            if execution_ref not in execution_keys or structural_ref != "none":
                fail("E_MIGRATION_DELTA", "positive migration successor ref is invalid")
        elif structural_ref not in structural_fqcns:
            fail("E_MIGRATION_DELTA", "structural migration successor ref is invalid")

    step2_rows = [row for row in executions if row["execution_step"] == "2" and row["required"] == "true"]
    deferred = [dict(row, disposition="deferred-to-step3") for row in executions if row["execution_step"] == "3"]
    if len(step2_rows) != EXPECTED_REQUIRED_STEP2 or len(deferred) != EXPECTED_DEFERRED_STEP3:
        fail("E_EXEC_DELTA", f"Step2/deferred cardinality differs: {len(step2_rows)}/{len(deferred)}")

    write_tsv(output / SOURCE_FILE, step1.SOURCE_HEADER, sources)
    write_tsv(output / DISCOVERY_FILE, step1.DISCOVERY_HEADER, discovery)
    write_tsv(output / CLASSPATH_FILE, step1.CLASSPATH_HEADER, classpath)
    write_tsv(output / EXECUTION_FILE, step1.EXECUTION_HEADER, executions)
    write_tsv(output / STRUCTURAL_FILE, STRUCTURAL_HEADER, structural)
    write_tsv(output / RENAME_FILE, step1.RENAME_HEADER, positive_rename_rows)
    write_tsv(output / MIGRATION_FILE, migration_header(step1), migration)
    write_tsv(output / STEP2_FILE, step1.EXECUTION_HEADER, step2_rows)
    write_tsv(output / DEFERRED_FILE, list(step1.EXECUTION_HEADER) + ["disposition"], deferred)
    write_json(output / PARENT_FILE, parent)
    write_json(output / RUNNER_FILE, runner)
    write_tsv(output / NEGATIVE_FILE, NEGATIVE_HEADER, [])

    source_hash = step1.tree_hash(root, successor_protected_paths(root, step1))
    freeze = {
        "schema_version": 6,
        "version": "9.3.4",
        "step": 2,
        "generation": "step2-post-rename",
        "successor_semantics": SUCCESSOR_SEMANTICS,
        "status": "candidate",
        "decision": "pending-independent-review",
        "generated_at": utc_now(),
        "git_head": subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, check=True, text=True, stdout=subprocess.PIPE).stdout.strip(),
        "protected_source_sha256": source_hash,
        "parent": parent,
        "step2_corrective_rename_plan_sha256": EXPECTED_CORRECTIVE_RENAME_PLAN,
        "step2_test_source_amendment_sha256": EXPECTED_TEST_SOURCE_AMENDMENT,
        "step2_r5_source_amendment_sha256": EXPECTED_R5_SOURCE_AMENDMENT,
        "step2_r6_source_amendment_sha256": EXPECTED_R6_SOURCE_AMENDMENT,
        "step2_r7_runner_amendment_sha256": EXPECTED_R7_RUNNER_AMENDMENT,
        "step2_r8_authority_amendment_sha256": EXPECTED_R8_AUTHORITY_AMENDMENT,
        "supersedes": superseded_provenance(),
        "counts": {
            "workspace_sources": len(sources),
            "reactor_sources": sum(row["reactor_member"] == "true" for row in sources),
            "discovery_rows": len(discovery),
            "discovery_reports": sum(row["report_fqcn"] != "none" for row in discovery),
            "structural_reports": len(structural),
            "classpath_entries": len(classpath),
            "execution_keys": len(executions),
            "required_step2": len(step2_rows),
            "deferred_step3": len(deferred),
            "predecessor_edges": len(migration),
            "predecessor_execution_refs": sum(row["successor_execution_key"] != "none" for row in migration),
            "predecessor_structural_refs": sum(row[MIGRATION_STRUCTURAL_FIELD] != "none" for row in migration),
            "renamed_sources": len(source_map),
            "planned_rename_reports": 64,
            "applied_positive_rename_reports": 60,
            "structural_rename_reports": 4,
            "planned_rename_execution_keys": len(execution_map),
            "applied_positive_rename_execution_keys": len(positive_rename_rows),
            "renamed_predecessor_edges": 50,
            "positive_renamed_predecessor_edges": 46,
            "structural_renamed_predecessor_edges": 4,
        },
        "zero_container_amendment": {
            "classification": "structural-split-v1",
            "parent_discovery_preserved": "820 rows / 804 ClassSource containers",
            "positive_execution_policy": "discovered_test_nodes > 0 or runtime_deferred_containers > 0",
            "structural_policy": "outer source FQCN, zero direct/deferred nodes, positive nested sibling required",
        },
        "corrective_lane_amendment": corrective_lane_amendment(),
        "test_source_amendment": test_source_amendment(root, step1),
        "r5_source_amendment": r5_source_amendment(root, step1),
        "r6_source_amendment": r6_source_amendment(root, step1),
        "r7_runner_amendment": r7_runner_amendment(root, step1),
        "r8_authority_amendment": r8_authority_amendment(root, step1),
        "classpath_content_amendment": classpath_amendment,
        "runner_contract_sha256": step1.sha256_file(output / RUNNER_FILE),
        "toolchain": {
            "step1_tool_sha256": step1.sha256_file(step1_dir / "inventory_tool.py"),
            "discovery_helper_sha256": step1.sha256_file(step1_dir / "JUnitDiscoveryInventory.java"),
            **executable_toolchain(root, step1),
        },
    }
    write_json(output / FREEZE_FILE, freeze)
    write_hash_manifest(output, step1)
    print(f"[v934-step2-successor] generated sources={len(sources)} discoveries={len(discovery)} executions={len(executions)}")


def compare_rows(actual: list[dict[str, str]], expected: list[dict[str, str]], code: str, label: str) -> None:
    if actual != expected:
        fail(code, f"{label} differs from deterministic Step 1 transform")


def validate_directory(
    root: Path,
    directory: Path,
    *,
    check_effective: bool = True,
    check_hashes: bool = True,
    require_negative: bool = True,
) -> None:
    root = root.resolve()
    directory = directory.resolve()
    step1 = load_step1_tool(root)
    step1_dir = root / "scripts/v934"
    rename_rows, source_map, execution_map, migration_key_map, corrective_keys = rename_maps(step1_dir, step1)
    validate_git_delta_scope(root, rename_rows)
    validate_canonical_rename_content(root, rename_rows, step1)
    validate_legacy_token_residue(root, rename_rows)
    parent = parent_link(step1_dir, step1)
    actual_parent = read_json(directory / PARENT_FILE)
    if actual_parent != parent:
        fail("E_PARENT_LINK", "successor parent link differs")

    sources = transformed_sources(step1_dir, root, step1, source_map)
    compare_rows(read_tsv(directory / SOURCE_FILE, step1.SOURCE_HEADER), sources, "E_SOURCE_SET", "source inventory")
    discovery = transformed_discovery(step1_dir, root, step1, sources, source_map, rename_rows)
    compare_rows(read_tsv(directory / DISCOVERY_FILE, step1.DISCOVERY_HEADER), discovery, "E_DISCOVERY_DELTA", "discovery inventory")
    executions, structural, structural_parent_refs = transformed_execution_and_structural(
        step1_dir, step1, execution_map, corrective_keys, sources, discovery
    )
    compare_rows(read_tsv(directory / EXECUTION_FILE, step1.EXECUTION_HEADER), executions, "E_EXEC_DELTA", "execution inventory")
    compare_rows(
        read_tsv(directory / STRUCTURAL_FILE, STRUCTURAL_HEADER),
        structural,
        "E_STRUCTURAL_REPORT",
        "structural report inventory",
    )
    positive_rename_rows = [
        row for row in rename_rows
        if row["current_execution_key"] not in structural_parent_refs
    ]
    compare_rows(
        read_tsv(directory / RENAME_FILE, step1.RENAME_HEADER),
        positive_rename_rows,
        "E_RENAME_DELTA",
        "applied positive rename delta",
    )
    positive_migration_key_map = {
        key: value for key, value in migration_key_map.items()
        if key not in structural_parent_refs
    }
    migration = transformed_migration(
        step1_dir,
        step1,
        positive_migration_key_map,
        structural_parent_refs,
        execution_map,
    )
    compare_rows(
        read_tsv(directory / MIGRATION_FILE, migration_header(step1)),
        migration,
        "E_MIGRATION_DELTA",
        "predecessor migration",
    )
    step2_rows = [row for row in executions if row["execution_step"] == "2" and row["required"] == "true"]
    compare_rows(read_tsv(directory / STEP2_FILE, step1.EXECUTION_HEADER), step2_rows, "E_STEP2_SET", "Step 2 required set")
    deferred = [dict(row, disposition="deferred-to-step3") for row in executions if row["execution_step"] == "3"]
    compare_rows(read_tsv(directory / DEFERRED_FILE, list(step1.EXECUTION_HEADER) + ["disposition"]), deferred, "E_DEFERRED_SET", "Step 3 deferred set")

    classpath = read_tsv(directory / CLASSPATH_FILE, step1.CLASSPATH_HEADER)
    classpath_amendment = validate_classpath_successor(step1_dir, classpath, step1)
    if len(classpath) != 2395:
        fail("E_CLASSPATH_DELTA", f"classpath count={len(classpath)}")
    m2 = Path.home() / ".m2/repository"
    module_ordinals: dict[str, int] = defaultdict(int)
    for row in classpath:
        module_ordinals[row["module"]] += 1
        if int(row["ordinal"]) != module_ordinals[row["module"]]:
            fail("E_CLASSPATH_DELTA", f"classpath ordinal gap: {row['module']}")
        identity = row["entry_identity"]
        path = root / identity[5:] if identity.startswith("repo:") else m2 / identity[3:] if identity.startswith("m2:") else None
        if path is None or step1.classpath_entry_sha256(root, path.resolve()) != row["entry_sha256"]:
            fail("E_CLASSPATH_DELTA", f"classpath hash differs: {identity}")

    ownership = validate_runner_ownership(sources, discovery, executions, structural)
    expected_runner = effective_runner_contract(root, step1) if check_effective else read_json(directory / RUNNER_FILE)
    if check_effective:
        expected_runner["ownership"] = ownership
    actual_runner = read_json(directory / RUNNER_FILE)
    validate_runner_static_contract(actual_runner)
    if actual_runner.get("authority_lock_contract") != authority_lock_contract(root, step1):
        fail("E_AUTHORITY_LOCK", "runner authority lock contract differs from current participants")
    if check_effective and actual_runner != expected_runner:
        fail("E_POM_CONTRACT", "runner contract differs from effective POM")
    if actual_runner.get("root_pom_sha256") != step1.sha256_file(root / "pom.xml"):
        fail("E_POM_CONTRACT", "root POM hash differs")
    if actual_runner.get("model_pom_sha256") != step1.sha256_file(root / "foggy-dataset-model/pom.xml"):
        fail("E_POM_CONTRACT", "model POM hash differs")

    freeze = read_json(directory / FREEZE_FILE)
    expected_counts = {
        "workspace_sources": 532, "reactor_sources": 530,
        "discovery_rows": 820, "discovery_reports": 804,
        "structural_reports": 59,
        "classpath_entries": 2395, "execution_keys": 770,
        "required_step2": 724, "deferred_step3": 46,
        "predecessor_edges": 519,
        "predecessor_execution_refs": 480,
        "predecessor_structural_refs": 39,
        "renamed_sources": 35,
        "planned_rename_reports": 64,
        "applied_positive_rename_reports": 60,
        "structural_rename_reports": 4,
        "planned_rename_execution_keys": 76,
        "applied_positive_rename_execution_keys": 72,
        "renamed_predecessor_edges": 50,
        "positive_renamed_predecessor_edges": 46,
        "structural_renamed_predecessor_edges": 4,
    }
    if freeze.get("counts") != expected_counts:
        fail("E_FREEZE_COUNTS", f"freeze counts differ: {freeze.get('counts')}")
    expected_amendment = {
        "classification": "structural-split-v1",
        "parent_discovery_preserved": "820 rows / 804 ClassSource containers",
        "positive_execution_policy": "discovered_test_nodes > 0 or runtime_deferred_containers > 0",
        "structural_policy": "outer source FQCN, zero direct/deferred nodes, positive nested sibling required",
    }
    if (
        freeze.get("schema_version") != 6
        or freeze.get("generation") != "step2-post-rename"
        or freeze.get("successor_semantics") != SUCCESSOR_SEMANTICS
        or freeze.get("parent") != parent
        or freeze.get("zero_container_amendment") != expected_amendment
        or freeze.get("corrective_lane_amendment") != corrective_lane_amendment()
        or freeze.get("test_source_amendment") != test_source_amendment(root, step1)
        or freeze.get("r5_source_amendment") != r5_source_amendment(root, step1)
        or freeze.get("r6_source_amendment") != r6_source_amendment(root, step1)
        or freeze.get("r7_runner_amendment") != r7_runner_amendment(root, step1)
        or freeze.get("r8_authority_amendment") != r8_authority_amendment(root, step1)
        or freeze.get("classpath_content_amendment") != classpath_amendment
        or freeze.get("step2_corrective_rename_plan_sha256") != EXPECTED_CORRECTIVE_RENAME_PLAN
        or freeze.get("step2_test_source_amendment_sha256") != EXPECTED_TEST_SOURCE_AMENDMENT
        or freeze.get("step2_r5_source_amendment_sha256") != EXPECTED_R5_SOURCE_AMENDMENT
        or freeze.get("step2_r6_source_amendment_sha256") != EXPECTED_R6_SOURCE_AMENDMENT
        or freeze.get("step2_r7_runner_amendment_sha256") != EXPECTED_R7_RUNNER_AMENDMENT
        or freeze.get("step2_r8_authority_amendment_sha256") != EXPECTED_R8_AUTHORITY_AMENDMENT
        or freeze.get("supersedes") != superseded_provenance()
    ):
        fail("E_FREEZE_SCHEMA", "successor freeze generation/parent differs")
    status = freeze.get("status")
    if status == "candidate":
        if freeze.get("decision") != "pending-independent-review" or "publication_evidence" in freeze:
            fail("E_FREEZE_STATUS", "candidate decision differs")
    elif status == "confirmed":
        if (
            freeze.get("decision") != "passed"
            or not freeze.get("reviewer")
            or not freeze.get("reviewed_at")
            or not freeze.get("independent_review_evidence")
            or not isinstance(freeze.get("publication_evidence"), dict)
        ):
            fail("E_FREEZE_STATUS", "confirmed review fields are incomplete")
    else:
        fail("E_FREEZE_STATUS", f"invalid successor status: {status}")
    if freeze.get("protected_source_sha256") != step1.tree_hash(root, successor_protected_paths(root, step1)):
        fail("E_SOURCE_HASH", "successor protected source hash differs")
    if freeze.get("runner_contract_sha256") != step1.sha256_file(directory / RUNNER_FILE):
        fail("E_POM_CONTRACT", "runner contract digest differs")
    toolchain = freeze.get("toolchain", {})
    expected_toolchain = {
        "step1_tool_sha256": step1.sha256_file(step1_dir / "inventory_tool.py"),
        "discovery_helper_sha256": step1.sha256_file(step1_dir / "JUnitDiscoveryInventory.java"),
        **executable_toolchain(root, step1),
    }
    if toolchain != expected_toolchain:
        fail("E_TOOLCHAIN", "successor toolchain differs")
    negative = read_tsv(directory / NEGATIVE_FILE, NEGATIVE_HEADER)
    if require_negative or negative:
        if [row["probe"] for row in negative] != NEGATIVE_PROBES or any(row["status"] != "passed" or row["expected_error"] != row["actual_error"] for row in negative):
            fail("E_NEGATIVE", "negative probes differ")
    if check_hashes:
        validate_hashes(directory, step1)
    print(f"[v934-step2-successor] PASS sources={len(sources)} discoveries={len(discovery)} executions={len(executions)} predecessors={len(migration)}")


def mutate_tsv(path: Path, header: Sequence[str], mutate: Callable[[list[dict[str, str]]], None]) -> None:
    rows = read_tsv(path, header)
    mutate(rows)
    write_tsv(path, header, rows)


def negative(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    directory = args.directory.resolve()
    step1 = load_step1_tool(root)
    write_tsv(directory / NEGATIVE_FILE, NEGATIVE_HEADER, [])
    write_hash_manifest(directory, step1)
    validate_directory(root, directory, check_effective=True, require_negative=False)
    probes: list[tuple[str, str, Callable[[Path], None], bool]] = [
        ("orphan-source", "E_SOURCE_SET", lambda d: mutate_tsv(d / SOURCE_FILE, step1.SOURCE_HEADER, lambda rows: rows.pop()), False),
        ("old-discovery-report", "E_DISCOVERY_DELTA", lambda d: mutate_tsv(d / DISCOVERY_FILE, step1.DISCOVERY_HEADER, lambda rows: rows[0].update(report_fqcn="old.IntegrationTest")), False),
        ("execution-semantic-drift", "E_EXEC_DELTA", lambda d: mutate_tsv(d / EXECUTION_FILE, step1.EXECUTION_HEADER, lambda rows: rows[0].update(lane="hermetic-integration")), False),
        ("structural-report-drift", "E_STRUCTURAL_REPORT", lambda d: mutate_tsv(d / STRUCTURAL_FILE, STRUCTURAL_HEADER, lambda rows: rows[0].update(positive_sibling_execution_keys="none")), False),
        ("migration-successor-drift", "E_MIGRATION_DELTA", lambda d: mutate_tsv(d / MIGRATION_FILE, migration_header(step1), lambda rows: rows[0].update(successor_execution_key="unknown")), False),
        ("typed-migration-ref-drift", "E_MIGRATION_DELTA", lambda d: mutate_tsv(d / MIGRATION_FILE, migration_header(step1), _mutate_typed_migration_ref), False),
        ("deferred-gap", "E_DEFERRED_SET", lambda d: mutate_tsv(d / DEFERRED_FILE, list(step1.EXECUTION_HEADER) + ["disposition"], lambda rows: rows.pop()), False),
        ("parent-manifest-drift", "E_PARENT_LINK", lambda d: _mutate_json(d / PARENT_FILE, "step1_contract_manifest_sha256", "0" * 64), False),
        ("rename-delta-drift", "E_RENAME_DELTA", lambda d: mutate_tsv(d / RENAME_FILE, step1.RENAME_HEADER, lambda rows: rows[0].update(target_report_fqcn=rows[0]["target_report_fqcn"] + "$Drift")), False),
        ("corrective-plan-digest-drift", "E_PARENT_LINK", lambda d: _mutate_json(d / PARENT_FILE, "step2_corrective_rename_plan_sha256", "0" * 64), False),
        ("test-source-amendment-drift", "E_PARENT_LINK", lambda d: _mutate_json(d / PARENT_FILE, "step2_test_source_amendment_sha256", "0" * 64), False),
        ("r5-source-amendment-drift", "E_PARENT_LINK", lambda d: _mutate_json(d / PARENT_FILE, "step2_r5_source_amendment_sha256", "0" * 64), False),
        ("r6-source-amendment-drift", "E_PARENT_LINK", lambda d: _mutate_json(d / PARENT_FILE, "step2_r6_source_amendment_sha256", "0" * 64), False),
        ("r7-runner-amendment-drift", "E_PARENT_LINK", lambda d: _mutate_json(d / PARENT_FILE, "step2_r7_runner_amendment_sha256", "0" * 64), False),
        ("r8-authority-amendment-drift", "E_PARENT_LINK", lambda d: _mutate_json(d / PARENT_FILE, "step2_r8_authority_amendment_sha256", "0" * 64), False),
        ("runner-contract-drift", "E_POM_CONTRACT", lambda d: _mutate_json(d / RUNNER_FILE, "root_pom_sha256", "0" * 64), False),
        ("failsafe-selector-override-drift", "E_POM_CONTRACT", lambda d: _mutate_json(d / RUNNER_FILE, "failsafe_fail_if_no_specified_tests", "true"), False),
        ("surefire-fail-if-no-tests-default-drift", "E_POM_CONTRACT", lambda d: _mutate_json(d / RUNNER_FILE, "surefire_fail_if_no_tests_default", "false"), False),
        ("failsafe-fail-if-no-tests-default-drift", "E_POM_CONTRACT", lambda d: _mutate_json(d / RUNNER_FILE, "failsafe_fail_if_no_tests_default", "false"), False),
        ("authority-lock-contract-drift", "E_AUTHORITY_LOCK", lambda d: _mutate_json(d / RUNNER_FILE, "authority_lock_contract", {}), False),
        ("authority-signal-contract-drift", "E_AUTHORITY_LOCK", lambda d: _mutate_json_path(d / RUNNER_FILE, ["authority_lock_contract", "signal_contract", "signals", "TERM"], 0), False),
        ("publish-cas-contract-drift", "E_PUBLISH_CAS", lambda d: _mutate_json(d / RUNNER_FILE, "publish_cas_contract", {}), False),
        ("report-schema-drift", "E_REPORT_CONTRACT", lambda d: _mutate_json_path(d / RUNNER_FILE, ["report_evidence_contract", "manifest_schema_version"], 2), False),
        ("report-context-drift", "E_REPORT_CONTRACT", lambda d: _mutate_json_path(d / RUNNER_FILE, ["report_evidence_contract", "outer_context", "kind"], "drift"), False),
        ("report-cardinality-drift", "E_REPORT_CONTRACT", lambda d: _mutate_json_path(d / RUNNER_FILE, ["report_evidence_contract", "runner_report_cardinality", "step2", "positive"], 723), False),
        ("nested-it-exclude-drift", "E_POM_CONTRACT", lambda d: _mutate_runner_nested_exclude(d / RUNNER_FILE), False),
        ("classpath-identity-drift", "E_CLASSPATH_DELTA", lambda d: mutate_tsv(d / CLASSPATH_FILE, step1.CLASSPATH_HEADER, lambda rows: rows[0].update(entry_identity="m2:unplanned/classpath.jar")), False),
        ("classpath-amended-content-drift", "E_CLASSPATH_DELTA", lambda d: mutate_tsv(d / CLASSPATH_FILE, step1.CLASSPATH_HEADER, _mutate_classpath_amended_content), False),
        ("protected-source-hash-drift", "E_SOURCE_HASH", lambda d: _mutate_json(d / FREEZE_FILE, "protected_source_sha256", "0" * 64), False),
        ("freeze-count-drift", "E_FREEZE_COUNTS", lambda d: _mutate_count(d / FREEZE_FILE), False),
        ("extra-successor-file", "E_HASH_SET", lambda d: (d / "unexpected.txt").write_text("unplanned\n", encoding="utf-8"), True),
        ("missing-hash-entry", "E_HASH_SET", lambda d: _remove_hash_line(d / HASH_FILE), True),
        ("stale-manifest", "E_STALE_HASH", lambda d: (d / PARENT_FILE).write_text((d / PARENT_FILE).read_text(encoding="utf-8") + "\n", encoding="utf-8"), True),
    ]
    results: list[dict[str, str]] = []
    for name, expected, mutation, hashes_only in probes:
        with tempfile.TemporaryDirectory(prefix=f"v934-step2-{name}-") as temporary:
            candidate = Path(temporary) / "step2"
            shutil.copytree(directory, candidate)
            mutation(candidate)
            try:
                validate_directory(root, candidate, check_effective=False, check_hashes=True, require_negative=False)
            except SuccessorError as exc:
                actual = exc.code
            else:
                actual = "none"
            if actual != expected:
                fail("E_NEGATIVE", f"probe {name} actual={actual}, expected={expected}")
            results.append({"probe": name, "expected_error": expected, "actual_error": actual, "status": "passed"})
    write_tsv(directory / NEGATIVE_FILE, NEGATIVE_HEADER, results)
    write_hash_manifest(directory, step1)
    validate_directory(root, directory, check_effective=True, require_negative=True)
    print(f"[v934-step2-successor] negative PASS probes={len(results)}")


def _mutate_json(path: Path, key: str, value: Any) -> None:
    data = read_json(path)
    data[key] = value
    write_json(path, data)


def _mutate_json_path(path: Path, keys: Sequence[str], value: Any) -> None:
    data = read_json(path)
    target = data
    for key in keys[:-1]:
        target = target[key]
    target[keys[-1]] = value
    write_json(path, data)


def _mutate_count(path: Path) -> None:
    data = read_json(path)
    data["counts"]["execution_keys"] += 1
    write_json(path, data)


def _mutate_runner_nested_exclude(path: Path) -> None:
    data = read_json(path)
    data["surefire_excludes"].remove("**/*IT$*.java")
    write_json(path, data)


def _mutate_typed_migration_ref(rows: list[dict[str, str]]) -> None:
    row = next(item for item in rows if item[MIGRATION_STRUCTURAL_FIELD] != "none")
    row["successor_execution_key"] = "unknown"


def _mutate_classpath_amended_content(rows: list[dict[str, str]]) -> None:
    row = next(
        item for item in rows
        if item["module"] == CLASSPATH_AMENDMENT_MODULE
        and item["ordinal"] == CLASSPATH_AMENDMENT_ORDINAL
        and item["entry_identity"] == CLASSPATH_AMENDMENT_IDENTITY
    )
    row["entry_sha256"] = "0" * 64


def _remove_hash_line(path: Path) -> None:
    lines = path.read_text(encoding="utf-8").splitlines()
    path.write_text("\n".join(lines[1:]) + "\n", encoding="utf-8")


def parse_summary(path: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if "=" not in line:
            fail("E_SUMMARY", f"invalid summary line: {line}")
        key, value = line.split("=", 1)
        if key in result:
            fail("E_SUMMARY", f"duplicate summary key: {key}")
        result[key] = value
    return result


def summary_key_order(path: Path) -> list[str]:
    return [line.split("=", 1)[0] for line in path.read_text(encoding="utf-8").splitlines()]


def parse_strict_env(path: Path, order: list[str], code: str) -> dict[str, str]:
    if not path.is_file():
        fail(code, f"required evidence file is missing: {path}")
    lines = path.read_text(encoding="utf-8").splitlines()
    if any("=" not in line for line in lines):
        fail(code, f"malformed evidence line: {path}")
    actual_order = [line.split("=", 1)[0] for line in lines]
    if actual_order != order or len(set(actual_order)) != len(actual_order):
        fail(code, f"evidence key set/order differs: {path}")
    return dict(line.split("=", 1) for line in lines)


def publication_confirmation_evidence(
    root: Path,
    summary_path: Path,
    freeze: dict[str, Any],
    step1: Any,
) -> dict[str, Any]:
    code = "E_PUBLICATION_EVIDENCE"
    run_id = summary_path.parent.name
    expected_run_root = (root / "target/v934-step2-successor/runs" / run_id).resolve()
    if summary_path.parent.resolve() != expected_run_root:
        fail(code, "summary is outside its canonical successor run root")

    status_path = expected_run_root / "run-status.env"
    log_path = expected_run_root / "run.log"
    provenance_path = expected_run_root / "superseded-provenance.env"
    archive = expected_run_root / "superseded-successor"
    status_order = [
        "run_id", "git_head", "started_at", "finished_at", "last_phase",
        "exit_code", "status", "publish_state", "rollback_outcome",
    ]
    status = parse_strict_env(status_path, status_order, code)
    expected_status = {
        "run_id": run_id,
        "git_head": freeze["git_head"],
        "last_phase": "completed",
        "exit_code": "0",
        "status": "passed",
        "publish_state": "publish-validated",
        "rollback_outcome": "not-required",
    }
    for key, value in expected_status.items():
        if status.get(key) != value:
            fail(code, f"outer run status differs: {key}")
    timestamp_pattern = r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z"
    if not re.fullmatch(timestamp_pattern, status["started_at"]) or not re.fullmatch(
        timestamp_pattern, status["finished_at"]
    ):
        fail(code, "outer run timestamps are not canonical UTC seconds")
    started_at = dt.datetime.fromisoformat(status["started_at"].replace("Z", "+00:00"))
    finished_at = dt.datetime.fromisoformat(status["finished_at"].replace("Z", "+00:00"))
    if finished_at < started_at:
        fail(code, "outer run finished before it started")
    if not log_path.is_file() or log_path.stat().st_size == 0:
        fail(code, "outer run log is missing or empty")

    provenance_order = [
        "run_id", "contract_freeze_sha256", "contract_manifest_sha256",
        "confirmed_summary_sha256", "original_summary",
    ]
    provenance = parse_strict_env(provenance_path, provenance_order, code)
    expected_provenance = {
        "run_id": SUPERSEDED_RUN_ID,
        "contract_freeze_sha256": SUPERSEDED_FREEZE_SHA256,
        "contract_manifest_sha256": SUPERSEDED_MANIFEST_SHA256,
        "confirmed_summary_sha256": SUPERSEDED_SUMMARY_SHA256,
        "original_summary": f"target/v934-step2-successor/runs/{SUPERSEDED_RUN_ID}/summary.env",
    }
    if provenance != expected_provenance:
        fail(code, "superseded provenance content differs")

    if not archive.is_dir() or archive.is_symlink():
        fail(code, "exact r7 archive is missing or is not a real directory")
    try:
        validate_hashes(archive, step1)
    except SuccessorError as exc:
        fail(code, f"archived r7 manifest is not self-consistent: {exc}")
    archive_freeze = archive / FREEZE_FILE
    archive_manifest = archive / HASH_FILE
    if step1.sha256_file(archive_freeze) != SUPERSEDED_FREEZE_SHA256:
        fail(code, "archived r7 freeze identity differs")
    if step1.sha256_file(archive_manifest) != SUPERSEDED_MANIFEST_SHA256:
        fail(code, "archived r7 manifest identity differs")
    archived_freeze = read_json(archive_freeze)
    if archived_freeze.get("status") != "confirmed" or archived_freeze.get("decision") != "passed":
        fail(code, "archived r7 freeze is not confirmed/passed")
    original_summary = root / expected_provenance["original_summary"]
    if not original_summary.is_file() or step1.sha256_file(original_summary) != SUPERSEDED_SUMMARY_SHA256:
        fail(code, "archived r7 confirmed summary identity differs")

    return {
        "schema_version": 1,
        "outer_run_log_sha256": step1.sha256_file(log_path),
        "outer_run_status_sha256": step1.sha256_file(status_path),
        "superseded_provenance_sha256": step1.sha256_file(provenance_path),
        "superseded_archive_freeze_sha256": step1.sha256_file(archive_freeze),
        "superseded_archive_manifest_sha256": step1.sha256_file(archive_manifest),
    }


def write_summary(path: Path, values: dict[str, str]) -> None:
    order = [
        "run_id", "git_head", "source_before", "source_after",
        "parent_manifest_sha256", "rename_plan_sha256", "corrective_rename_plan_sha256",
        "test_source_amendment_sha256", "r5_source_amendment_sha256", "r6_source_amendment_sha256",
        "r7_runner_amendment_sha256", "r8_authority_amendment_sha256",
        "successor_semantics", "supersedes_run_id", "superseded_contract_freeze_sha256",
        "superseded_contract_manifest_sha256", "superseded_confirmed_summary_sha256",
        "workspace_sources",
        "discovery_rows", "structural_reports", "execution_keys", "required_step2", "deferred_step3",
        "predecessor_edges", "predecessor_execution_refs", "predecessor_structural_refs",
        "planned_rename_reports", "applied_positive_rename_reports", "structural_rename_reports",
        "planned_rename_execution_keys", "applied_positive_rename_execution_keys",
        "contract_freeze_sha256", "contract_manifest_sha256",
        "publication_evidence_schema_version", "outer_run_log_sha256", "outer_run_status_sha256",
        "superseded_provenance_sha256", "superseded_archive_freeze_sha256",
        "superseded_archive_manifest_sha256",
        "evidence_status", "status", "decision", "reviewer", "reviewed_at",
        "independent_review_evidence",
    ]
    content = "".join(f"{key}={values[key]}\n" for key in order if key in values)
    descriptor, temporary_name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    os.close(descriptor)
    temporary = Path(temporary_name)
    try:
        temporary.write_text(content, encoding="utf-8")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def validate_summary(root: Path, directory: Path, summary_path: Path) -> None:
    step1 = load_step1_tool(root)
    validate_directory(root, directory, check_effective=True, require_negative=True)
    freeze = read_json(directory / FREEZE_FILE)
    summary = parse_summary(summary_path)
    if not summary.get("run_id") or summary["run_id"] != summary_path.parent.name:
        fail("E_SUMMARY", "summary run_id does not match its run directory")
    expected = {
        "run_id": summary_path.parent.name,
        "git_head": freeze["git_head"],
        "source_before": freeze["protected_source_sha256"],
        "source_after": freeze["protected_source_sha256"],
        "parent_manifest_sha256": EXPECTED_PARENT_MANIFEST,
        "rename_plan_sha256": EXPECTED_RENAME_PLAN,
        "corrective_rename_plan_sha256": EXPECTED_CORRECTIVE_RENAME_PLAN,
        "test_source_amendment_sha256": EXPECTED_TEST_SOURCE_AMENDMENT,
        "r5_source_amendment_sha256": EXPECTED_R5_SOURCE_AMENDMENT,
        "r6_source_amendment_sha256": EXPECTED_R6_SOURCE_AMENDMENT,
        "r7_runner_amendment_sha256": EXPECTED_R7_RUNNER_AMENDMENT,
        "r8_authority_amendment_sha256": EXPECTED_R8_AUTHORITY_AMENDMENT,
        "successor_semantics": SUCCESSOR_SEMANTICS,
        "supersedes_run_id": SUPERSEDED_RUN_ID,
        "superseded_contract_freeze_sha256": SUPERSEDED_FREEZE_SHA256,
        "superseded_contract_manifest_sha256": SUPERSEDED_MANIFEST_SHA256,
        "superseded_confirmed_summary_sha256": SUPERSEDED_SUMMARY_SHA256,
        "workspace_sources": "532",
        "discovery_rows": "820",
        "structural_reports": "59",
        "execution_keys": "770",
        "required_step2": "724",
        "deferred_step3": "46",
        "predecessor_edges": "519",
        "predecessor_execution_refs": "480",
        "predecessor_structural_refs": "39",
        "planned_rename_reports": "64",
        "applied_positive_rename_reports": "60",
        "structural_rename_reports": "4",
        "planned_rename_execution_keys": "76",
        "applied_positive_rename_execution_keys": "72",
        "contract_freeze_sha256": step1.sha256_file(directory / FREEZE_FILE),
        "contract_manifest_sha256": step1.sha256_file(directory / HASH_FILE),
        "status": "passed",
    }
    summary_prefix = [
        "run_id", "git_head", "source_before", "source_after",
        "parent_manifest_sha256", "rename_plan_sha256", "corrective_rename_plan_sha256",
        "test_source_amendment_sha256", "r5_source_amendment_sha256", "r6_source_amendment_sha256",
        "r7_runner_amendment_sha256", "r8_authority_amendment_sha256",
        "successor_semantics", "supersedes_run_id", "superseded_contract_freeze_sha256",
        "superseded_contract_manifest_sha256", "superseded_confirmed_summary_sha256",
        "workspace_sources",
        "discovery_rows", "structural_reports", "execution_keys", "required_step2", "deferred_step3",
        "predecessor_edges", "predecessor_execution_refs", "predecessor_structural_refs",
        "planned_rename_reports", "applied_positive_rename_reports", "structural_rename_reports",
        "planned_rename_execution_keys", "applied_positive_rename_execution_keys",
        "contract_freeze_sha256", "contract_manifest_sha256",
    ]
    publication_order = [
        "publication_evidence_schema_version", "outer_run_log_sha256", "outer_run_status_sha256",
        "superseded_provenance_sha256", "superseded_archive_freeze_sha256",
        "superseded_archive_manifest_sha256",
    ]
    decision_order = ["evidence_status", "status", "decision"]
    if freeze.get("status") == "candidate" and freeze.get("decision") == "pending-independent-review":
        if "publication_evidence" in freeze:
            fail("E_SUMMARY", "candidate freeze must not contain confirmed publication evidence")
        expected.update({
            "evidence_status": "candidate",
            "decision": "pending-independent-review",
        })
        expected_order = summary_prefix + decision_order
    elif freeze.get("status") == "confirmed" and freeze.get("decision") == "passed":
        publication = publication_confirmation_evidence(root, summary_path, freeze, step1)
        if freeze.get("publication_evidence") != publication:
            fail("E_PUBLICATION_EVIDENCE", "confirmed freeze publication evidence differs")
        expected.update({
            "publication_evidence_schema_version": str(publication["schema_version"]),
            "outer_run_log_sha256": publication["outer_run_log_sha256"],
            "outer_run_status_sha256": publication["outer_run_status_sha256"],
            "superseded_provenance_sha256": publication["superseded_provenance_sha256"],
            "superseded_archive_freeze_sha256": publication["superseded_archive_freeze_sha256"],
            "superseded_archive_manifest_sha256": publication["superseded_archive_manifest_sha256"],
            "evidence_status": "confirmed",
            "decision": "passed",
            "reviewer": freeze["reviewer"],
            "reviewed_at": freeze["reviewed_at"],
            "independent_review_evidence": freeze["independent_review_evidence"],
        })
        expected_order = summary_prefix + publication_order + decision_order + [
            "reviewer", "reviewed_at", "independent_review_evidence",
        ]
    else:
        fail("E_SUMMARY", "successor freeze status/decision is not a valid candidate or confirmation")
    if summary_key_order(summary_path) != expected_order or set(summary) != set(expected):
        fail("E_SUMMARY", "summary key set/order differs")
    for key, value in expected.items():
        if summary.get(key) != value:
            fail("E_SUMMARY", f"summary {key} differs")
    print(f"[v934-step2-successor] {freeze['status']} summary PASS path={summary_path}")


def acquire_authority_lock(root: Path) -> int:
    git_dir = subprocess.run(
        ["git", "rev-parse", "--absolute-git-dir"],
        cwd=root,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    ).stdout.strip()
    lock_path = Path(git_dir) / "v934-step2-authority.lock"
    try:
        descriptor = os.open(lock_path, os.O_RDWR | os.O_CREAT, 0o666)
    except OSError as exc:
        fail("E_AUTHORITY_LOCK", f"cannot open authority lock {lock_path}: {exc}")
    try:
        fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        os.close(descriptor)
        fail("E_AUTHORITY_LOCK", f"another Step 2 authority writer owns {lock_path}")
    except OSError as exc:
        os.close(descriptor)
        fail("E_AUTHORITY_LOCK", f"cannot acquire authority lock {lock_path}: {exc}")
    return descriptor


def confirm(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    descriptor = acquire_authority_lock(root)
    try:
        confirm_unlocked(args)
    finally:
        os.close(descriptor)


def confirm_unlocked(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    directory = args.directory.resolve()
    summary_path = args.summary.resolve()
    step1 = load_step1_tool(root)
    if not args.reviewer.strip():
        fail("E_FREEZE_CONFIRM", "independent reviewer is required")
    evidence_path = (root / args.evidence).resolve()
    if not evidence_path.is_relative_to(root):
        fail("E_FREEZE_CONFIRM", "independent review evidence must be inside the workspace")
    if not evidence_path.is_file():
        fail("E_FREEZE_CONFIRM", f"independent review evidence is missing: {args.evidence}")
    validate_summary(root, directory, summary_path)
    freeze_path = directory / FREEZE_FILE
    manifest_path = directory / HASH_FILE
    original_freeze = freeze_path.read_bytes()
    original_manifest = manifest_path.read_bytes()
    original_summary = summary_path.read_bytes()
    try:
        freeze = read_json(freeze_path)
        if freeze.get("status") != "candidate" or freeze.get("decision") != "pending-independent-review":
            fail("E_FREEZE_CONFIRM", "only a pending Step 2 candidate can be confirmed")
        publication = publication_confirmation_evidence(root, summary_path, freeze, step1)
        reviewed_at = utc_now()
        freeze.update({
            "status": "confirmed",
            "decision": "passed",
            "reviewer": args.reviewer,
            "reviewed_at": reviewed_at,
            "independent_review_evidence": args.evidence,
            "publication_evidence": publication,
        })
        write_json(freeze_path, freeze)
        write_hash_manifest(directory, step1)
        values = parse_summary(summary_path)
        values.update({
            "contract_freeze_sha256": step1.sha256_file(freeze_path),
            "contract_manifest_sha256": step1.sha256_file(manifest_path),
            "publication_evidence_schema_version": str(publication["schema_version"]),
            "outer_run_log_sha256": publication["outer_run_log_sha256"],
            "outer_run_status_sha256": publication["outer_run_status_sha256"],
            "superseded_provenance_sha256": publication["superseded_provenance_sha256"],
            "superseded_archive_freeze_sha256": publication["superseded_archive_freeze_sha256"],
            "superseded_archive_manifest_sha256": publication["superseded_archive_manifest_sha256"],
            "evidence_status": "confirmed",
            "status": "passed",
            "decision": "passed",
            "reviewer": args.reviewer,
            "reviewed_at": reviewed_at,
            "independent_review_evidence": args.evidence,
        })
        write_summary(summary_path, values)
        validate_summary(root, directory, summary_path)
    except BaseException:
        freeze_path.write_bytes(original_freeze)
        manifest_path.write_bytes(original_manifest)
        summary_path.write_bytes(original_summary)
        raise
    print(f"[v934-step2-successor] confirmed summary_sha256={step1.sha256_file(summary_path)}")


def command_validate(args: argparse.Namespace) -> None:
    validate_directory(args.root, args.directory, check_effective=True)


def command_validate_amendment(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    step1 = load_step1_tool(root)
    amendment = r8_authority_amendment(root, step1)
    authority_lock_contract(root, step1)
    print(
        "[v934-step2-successor] r8 amendment PASS "
        f"files={amendment['files']} sha256={amendment['manifest_sha256']}"
    )


def command_validate_summary(args: argparse.Namespace) -> None:
    validate_summary(args.root.resolve(), args.directory.resolve(), args.summary.resolve())


def command_source_hash(args: argparse.Namespace) -> None:
    root = args.root.resolve()
    step1 = load_step1_tool(root)
    print(step1.tree_hash(root, successor_protected_paths(root, step1)))


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(required=True)
    generate_parser = sub.add_parser("generate")
    generate_parser.add_argument("--root", type=Path, required=True)
    generate_parser.add_argument("--run-dir", type=Path, required=True)
    generate_parser.add_argument("--output-dir", type=Path, required=True)
    generate_parser.set_defaults(handler=generate)
    validate_parser = sub.add_parser("validate")
    validate_parser.add_argument("--root", type=Path, required=True)
    validate_parser.add_argument("--directory", type=Path, required=True)
    validate_parser.set_defaults(handler=command_validate)
    amendment_parser = sub.add_parser("validate-amendment")
    amendment_parser.add_argument("--root", type=Path, required=True)
    amendment_parser.set_defaults(handler=command_validate_amendment)
    negative_parser = sub.add_parser("negative")
    negative_parser.add_argument("--root", type=Path, required=True)
    negative_parser.add_argument("--directory", type=Path, required=True)
    negative_parser.set_defaults(handler=negative)
    confirm_parser = sub.add_parser("confirm")
    confirm_parser.add_argument("--root", type=Path, required=True)
    confirm_parser.add_argument("--directory", type=Path, required=True)
    confirm_parser.add_argument("--reviewer", required=True)
    confirm_parser.add_argument("--evidence", required=True)
    confirm_parser.add_argument("--summary", type=Path, required=True)
    confirm_parser.set_defaults(handler=confirm)
    summary_parser = sub.add_parser("validate-summary")
    summary_parser.add_argument("--root", type=Path, required=True)
    summary_parser.add_argument("--directory", type=Path, required=True)
    summary_parser.add_argument("--summary", type=Path, required=True)
    summary_parser.set_defaults(handler=command_validate_summary)
    source_hash_parser = sub.add_parser("source-hash")
    source_hash_parser.add_argument("--root", type=Path, required=True)
    source_hash_parser.set_defaults(handler=command_source_hash)
    return parser


def main() -> int:
    try:
        args = build_parser().parse_args()
        args.handler(args)
        return 0
    except SuccessorError as exc:
        print(f"[v934-step2-successor] ERROR {exc}", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as exc:
        print(f"[v934-step2-successor] ERROR command failed: {exc}", file=sys.stderr)
        return exc.returncode or 1


if __name__ == "__main__":
    raise SystemExit(main())
