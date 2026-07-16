#!/usr/bin/env python3
"""Fail-closed verifier for the Step 4 successor overlay on Step 3 evidence."""

from __future__ import annotations

import argparse
import copy
import csv
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
DEFAULT_CONTRACT = HERE / "overlay-contract.json"
DEFAULT_MANIFEST = HERE / "SHA256SUMS"
SHA_PATTERN = re.compile(r"[0-9a-f]{64}")
COMMIT_PATTERN = re.compile(r"[0-9a-f]{40}")

EXPECTED_PARENT_COMMIT = "e1a2a275ae5f39ca0be641ef18ca5622fa4c7076"
EXPECTED_PARENT_MANIFESTS = {
    "step1": (
        "scripts/v934/SHA256SUMS",
        "e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f",
        "scripts/v934",
    ),
    "step2": (
        "scripts/v934/successor/step2/SHA256SUMS",
        "4259a452bf4282f85ebb8bfe092127ec3ebec95652e7c009792081f86b84b919",
        "scripts/v934/successor/step2",
    ),
    "step3": (
        "scripts/v934/step3/SHA256SUMS",
        "38248a6788d2c16b315f955fb0cd8fac00d4847eb20629a33e837e1411864aed",
        ".",
    ),
}
EXPECTED_PARENT_ARTIFACTS = {
    "scripts/v934/contract-freeze.json": "ff418e04f6a938a853ce7bbd0700223627f42520705530e819a53e5591e82876",
    "scripts/v934/coverage-thresholds.json": "45058f63b71558e4660f60e0cfda9a8a490fa8f96b532c6656c3d726eaad44fb",
    "scripts/v934/successor/step2/contract-freeze.json": "44b11ed756bf41e3b271ac57b59c2c882a0b31a56963f42ae154fdb5d37b2fb6",
    "scripts/v934/successor/step2/parent-link.json": "304168996f8596bf226a0b1bb6fec62c496d40508d9c24cb70537e15671b2b57",
    "scripts/v934/successor/step2/runner-contract.json": "a5a7364fe75af2668c2c85989295b1448834dc526604175f7614f93d30e0376a",
    "scripts/v934/step3/SHA256SUMS": "38248a6788d2c16b315f955fb0cd8fac00d4847eb20629a33e837e1411864aed",
    "scripts/v934/step3/step3-required-contract.json": "f2bd52df7ed2829051ad263f97d560d3f8babe048d25864edb725eb671ba4d1b",
    "scripts/v934/step3/database-matrix-contract.json": "70385c7351e7fd44bef9ec5ed24f6d64ed5aa4253e3e8ae38c2e07713aaf112e",
    "scripts/v934/step3/database-matrix-protected-trees.tsv": "12dd3968ee40ce78d28dde49cdba99f5a14c47b6dc519f421c9b384461d97036",
    "scripts/v934/step3/database-matrix-source-amendment.tsv": "38d92c2250252b0cd4eae296ec6bf4d36081f0968bf6c0a2f3faf039ce32ef0c",
    "scripts/v934/step3/external-matrix-contract.json": "bd6caec69adb3a9ca5a615c8bda4d4919e53dcdfd230b04c2eb6d1824e9526c7",
    "scripts/v934/step3/external-redis-state-contract.json": "70fdc5756552db2ba0964df94165b2610d957ffb342160b8ca5f03a430b68f31",
    "scripts/v934/step3/preagg-addon-lifecycle-contract.json": "27d8abe4944c3df8694ed37a89388d44058a9fed71080855ab524ebde9ad54e3",
    "scripts/v934/step3/step3_required_report_tool.py": "7856a5a14aaac2e6b182666097ab035446f98bdbeda6f4eadffbdb7503f17e58",
    "scripts/v934/step3/database_matrix_report_tool.py": "99ec9177fb8ff7aba52b6bedd90c5880b8a324ea85c367f19dd8b140bf716dac",
    "scripts/v934/step3/database_state_contract.json": "0fce8b5b8be15596672530f1d70a51e664a2f4a8e3f4059e7ab4cc496f5a28f1",
    "scripts/v934/step3/database_state_negative_tool.py": "7c6157bf453b7f707a18f59d1011fd60e135298b610e3e99dcba283569a4a548",
    "scripts/v934/step3/database_state_probe_callback.sh": "3f8b28da2d83f9dadb439e49f56140ced8da47133e4347af81debd6323118402",
    "scripts/v934/step3/external_lane_launcher.py": "8c8bdd25e462c6f70d9b5731dd1e7eb1c8fab7a23edf3591514986e140a498c4",
    "scripts/v934/step3/external_matrix_report_tool.py": "d15e3ad9d5f1e6001bab7a5d000ed0e25af241067dec2c9e3e5f159a6cef0531",
    "scripts/v934/step3/external_redis_signal_probe.py": "cf042bc137aab190c829f9537b1b90d32b9ceb2adedf53eab5d1171994006850",
    "scripts/v934/step3/external_redis_state_tool.py": "be3d319c1179fed85626935d4118057acd0ec86827060a666d62e61583c6941c",
    "scripts/v934/step3/external_shared_context.sh": "0674e905c019bd84af02c557f16f48e6b32cb3e9abebe5a8d8f9585f8b434875",
    "scripts/v934/step3/manage-database-foundation-fixtures.sh": "d5e342f87fba665efbea733581d592982c48b23695126ad4591c70489eb6ee22",
    "scripts/v934/step3/preagg_addon_lifecycle_report_tool.py": "3995c3bccdb0d69e5727c184990e4a1d1f9aac531ff981c067ab038cb4690cf1",
    "scripts/v934/step3/provision-database-cell.sh": "74d00d79628b0df9d438f1623d53e2617cc73c01c74a91a2c21cccdf2ea884e6",
    "scripts/v934/step3/sqlite_cell_tool.py": "6897440a62f1f72354b86691968a0a889d0972e3832ec1768785d9947953bc7f",
    "scripts/v934/step3/verify-v933-real-query-compat.sh": "f6837824a1f868dfe5398e663725de51d1909cd6ae6c0c1564f60cd550fd4409",
    "scripts/v934/authority_runner_lib.sh": "65f50005f0e71489fa263f1bbcf841bb646d89bb35dd6baac44f1c7480ed5434",
}
REDIS_SOURCE_PATH = (
    "addons/foggy-dataset-model-cache/src/test/java/com/foggyframework/"
    "dataset/db/model/cache/provider/RedisCrossJvmCacheIT.java"
)
EXPECTED_AMENDMENT_PATHS = (
    "pom.xml",
    "foggy-dataset-model/pom.xml",
    "scripts/verify-v934-unit.sh",
    "scripts/verify-v934-integration.sh",
    "scripts/verify-v934-step3-required-matrix.sh",
    "scripts/verify-v934-database-matrix.sh",
    "scripts/verify-v934-external-matrix.sh",
    "scripts/verify-v934-external-redis.sh",
    "scripts/verify-v934-external-mongo.sh",
    "scripts/verify-v934-external-mysql.sh",
    "scripts/verify-v934-external-vector.sh",
    "scripts/verify-v934-preagg-addon-lifecycle.sh",
    REDIS_SOURCE_PATH,
    "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationEdgeCaseTest.java",
    "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationDataValidationTest.java",
    "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/PreAggregationL2CacheIT.java",
    "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotSqlParityIT.java",
)
EXPECTED_SUCCESSOR_FILES = (
    "database-authority-SHA256SUMS",
    "database-matrix-contract.json",
    "database-matrix-protected-trees.tsv",
    "database-matrix-source-amendment.tsv",
    "database_matrix_report_tool.py",
    "database_state_negative_tool.py",
    "declared-amendments.tsv",
    "external-matrix-contract.json",
    "external_matrix_report_tool.py",
    "overlay-contract.json",
    "overlay_tool.py",
    "preagg-addon-lifecycle-contract.json",
    "step3-required-contract.json",
    "step3_required_report_tool.py",
)
EXPECTED_PAIR_RULES = {
    "required": {
        "parent": "scripts/v934/step3/step3-required-contract.json",
        "successor": "scripts/v934/step4/successor/step3-required-contract.json",
        "allowed": {
            "addon_contract", "addon_runner", "database_contract",
            "database_report_tool", "database_runner", "external_contract",
            "external_report_tool", "external_runner", "required_report_tool",
            "required_runner",
        },
        "paths": {
            "addon_contract": "scripts/v934/step4/successor/preagg-addon-lifecycle-contract.json",
            "addon_runner": "scripts/verify-v934-preagg-addon-lifecycle.sh",
            "database_contract": "scripts/v934/step4/successor/database-matrix-contract.json",
            "database_report_tool": "scripts/v934/step4/successor/database_matrix_report_tool.py",
            "database_runner": "scripts/verify-v934-database-matrix.sh",
            "external_contract": "scripts/v934/step4/successor/external-matrix-contract.json",
            "external_report_tool": "scripts/v934/step4/successor/external_matrix_report_tool.py",
            "external_runner": "scripts/verify-v934-external-matrix.sh",
            "required_report_tool": "scripts/v934/step4/successor/step3_required_report_tool.py",
            "required_runner": "scripts/verify-v934-step3-required-matrix.sh",
        },
    },
    "database": {
        "parent": "scripts/v934/step3/database-matrix-contract.json",
        "successor": "scripts/v934/step4/successor/database-matrix-contract.json",
        "allowed": {"authority_hash_manifest", "protected_tree_manifest", "source_amendment"},
        "paths": {
            "authority_hash_manifest": "scripts/v934/step4/successor/database-authority-SHA256SUMS",
            "protected_tree_manifest": "scripts/v934/step4/successor/database-matrix-protected-trees.tsv",
            "source_amendment": "scripts/v934/step4/successor/database-matrix-source-amendment.tsv",
        },
    },
    "external": {
        "parent": "scripts/v934/step3/external-matrix-contract.json",
        "successor": "scripts/v934/step4/successor/external-matrix-contract.json",
        "allowed": {
            "external_report_tool", "external_matrix_runner", "external_mongo_runner", "external_mysql_runner",
            "external_redis_runner", "external_vector_runner",
        },
        "paths": {
            "external_report_tool": "scripts/v934/step4/successor/external_matrix_report_tool.py",
            "external_matrix_runner": "scripts/verify-v934-external-matrix.sh",
            "external_mongo_runner": "scripts/verify-v934-external-mongo.sh",
            "external_mysql_runner": "scripts/verify-v934-external-mysql.sh",
            "external_redis_runner": "scripts/verify-v934-external-redis.sh",
            "external_vector_runner": "scripts/verify-v934-external-vector.sh",
        },
    },
    "addon": {
        "parent": "scripts/v934/step3/preagg-addon-lifecycle-contract.json",
        "successor": "scripts/v934/step4/successor/preagg-addon-lifecycle-contract.json",
        "allowed": {"runner"},
        "paths": {"runner": "scripts/verify-v934-preagg-addon-lifecycle.sh"},
    },
}
EXPECTED_FROZEN_SEMANTICS = {
    "required": {
        "variants": 14, "reports": 45, "testcase_nodes": 446,
        "failures": 0, "errors": 0, "skipped": 0, "negative_probes": 17,
    },
    "database": {
        "variants": 7, "reports": 29, "testcase_nodes": 370,
        "failures": 0, "errors": 0, "skipped": 0, "negative_probes": 14,
    },
    "external": {
        "variants": 7, "reports": 16, "testcase_nodes": 76,
        "failures": 0, "errors": 0, "skipped": 0, "negative_probes": 12,
    },
    "addon": {
        "variants": 2, "reports": 2, "testcase_nodes": 6,
        "failures": 0, "errors": 0, "skipped": 0, "negative_probes": 4,
    },
}
EXPECTED_PROTECTED_SCOPE = {
    "database_tree_manifest": "scripts/v934/step3/database-matrix-protected-trees.tsv",
    "deferred_inventory": "scripts/v934/successor/step2/deferred-step3.tsv",
    "source_inventory": "scripts/v934/successor/step2/source-inventory.tsv",
    "explicit_paths": [
        "pom.xml", "foggy-dataset-model/pom.xml",
        "scripts/verify-v934-unit.sh", "scripts/verify-v934-integration.sh",
    ],
}
EXPECTED_ACTIVATION_REQUIREMENTS = [
    "Step 4 required runner and report verifier must select the successor required contract and adapter.",
    "Database runner and state companion must select the successor database contract and adapters.",
    "External runner must select the successor external contract and external wrapper.",
    "Addon runner must select the successor Addon contract.",
]
EXPECTED_STEP4_RUNTIME_BINDINGS = {
    "scripts/v934/step4/authority_parent_lib.sh": "35024328ec6f181f4454a36b702f76c20dfd049af8a38a84b5f3117ac94254fa",
    "scripts/v934/step4/authority_parent_negative_test.sh": "2329d6211d51f673b29aae407e4e0a1f99af2f5bac0425d56cade3d1e1c148ca",
    "scripts/v934/step4/coverage-contract.json": {
        "kind": "workflow-dual-sha256",
        "diagnostic": {
            "contract_status": "diagnostic-ready",
            "publication_status": "diagnostic-ready",
            "sha256": "16677d3ae64a7d24aa5796e7c1bbb8ca5af347d6843878471a7e48bdc52c82af",
        },
        "formal": {
            "contract_status": "formal-ready",
            "publication_status": "formal-ready",
            "sha256": "d8e7efa775d021d42485f1ffa6cb51a98a3f3f6662b1793e6b06f69852d12463",
        },
    },
    "scripts/v934/step4/coverage-report-amendment.tsv": "937666fc1926ec1c4764ebb50d4b4d4bdd1f1013f0d63cc77d9a1856fae153d2",
    "scripts/v934/step4/coverage_runner_lib.sh": "ecbb9ce810d61280542a694a3e977d123ebfc3de83599252bdfd9dbe407ce383",
    "scripts/v934/step4/coverage_tool.py": "bf317dd09bb2f909773dba602ab00037acf112b835a166bfd64ef9709045179a",
    "scripts/v934/step4/step2-report-view-contract.json": "c016ec18fa0a637e5c5470385c3f26cce152c461eb1dc1b64b52f28f5e8b8a67",
    "scripts/v934/step4/step2_report_view_tool.py": "b828869dec191a6ded51e7b28654f8878c65455007ca002e247347a0cb5e217a",
}
REDIS_SOURCE_ID = f"v934-src|{len(REDIS_SOURCE_PATH)}:{REDIS_SOURCE_PATH}"


class OverlayError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def reject(code: str, message: str) -> None:
    raise OverlayError(code, message)


def safe_repo_path(value: str, code: str = "E_PATH") -> Path:
    if not isinstance(value, str) or not value or "\\" in value:
        reject(code, f"invalid repository path: {value!r}")
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts or relative.as_posix() != value:
        reject(code, f"unsafe repository path: {value}")
    lexical = ROOT / relative
    if lexical.is_symlink() or not lexical.is_file():
        reject(code, f"path is missing, non-regular, or a symlink: {value}")
    resolved = lexical.resolve()
    if not resolved.is_relative_to(ROOT):
        reject(code, f"path escapes repository: {value}")
    return resolved


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path, code: str = "E_SCHEMA") -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        reject(code, f"JSON input is not a regular file: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise OverlayError(code, f"invalid JSON: {path}") from error
    if not isinstance(value, dict):
        reject(code, f"JSON root is not an object: {path}")
    return value


def git_environment() -> dict[str, str]:
    """Return a Git environment that cannot redirect repository identity.

    Git has both documented and internal ``GIT_*`` overrides for the worktree,
    index, object store, namespaces, config, shallow boundary, and grafts.  A
    deny-list is easy to outgrow, so the overlay verifier drops the complete
    namespace and restores only the two fixed safety controls it owns.
    """
    environment = {
        name: value
        for name, value in os.environ.items()
        if not name.startswith("GIT_")
    }
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "LC_ALL": "C",
            "LANG": "C",
        }
    )
    return environment


def git(*arguments: str, binary: bool = False) -> bytes | str:
    completed = subprocess.run(
        [
            "git",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-c",
            "core.hooksPath=/dev/null",
            "-C",
            str(ROOT),
            *arguments,
        ],
        env=git_environment(),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=not binary,
    )
    if completed.returncode != 0:
        stderr = completed.stderr.decode() if binary else completed.stderr
        reject("E_GIT", f"git {' '.join(arguments)} failed: {stderr.strip()}")
    return completed.stdout


def git_blob_sha(commit: str, path: str) -> str:
    payload = git("show", f"{commit}:{path}", binary=True)
    assert isinstance(payload, bytes)
    return hashlib.sha256(payload).hexdigest()


def parse_hash_manifest(path: Path, expected_names: set[str] | None = None) -> dict[str, str]:
    rows: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64})  ([A-Za-z0-9._/-]+)", line)
        if match is None:
            reject("E_SUCCESSOR_MANIFEST", f"malformed hash manifest row: {line!r}")
        digest, name = match.groups()
        if name in rows or Path(name).is_absolute() or ".." in Path(name).parts:
            reject("E_SUCCESSOR_MANIFEST", f"unsafe or duplicate manifest path: {name}")
        rows[name] = digest
    if expected_names is not None and set(rows) != expected_names:
        reject("E_SUCCESSOR_MANIFEST", "successor manifest file set differs")
    return rows


def read_amendments(contract: dict[str, Any]) -> dict[str, dict[str, str]]:
    definition = contract["declared_amendments"]
    if definition.get("path") != "scripts/v934/step4/successor/declared-amendments.tsv":
        reject("E_AMENDMENT", "declared amendment inventory path differs")
    path = safe_repo_path(definition["path"], "E_AMENDMENT")
    if sha256(path) != definition["sha256"]:
        reject("E_AMENDMENT", "declared amendment inventory hash differs")
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        if reader.fieldnames != [
            "path", "parent_sha256", "successor_sha256", "kind", "owner", "allowed_effect"
        ]:
            reject("E_AMENDMENT", "declared amendment header differs")
        rows = list(reader)
    if [row["path"] for row in rows] != list(EXPECTED_AMENDMENT_PATHS):
        reject("E_AMENDMENT", "declared amendment path set or order differs")
    if definition["paths"] != list(EXPECTED_AMENDMENT_PATHS):
        reject("E_AMENDMENT", "overlay amendment path binding differs")
    result: dict[str, dict[str, str]] = {}
    for row in rows:
        path_value = row["path"]
        if (
            path_value in result
            or row["kind"] not in {"build-contract", "runner-binding", "source-amendment"}
            or not row["owner"]
            or not row["allowed_effect"].strip()
            or SHA_PATTERN.fullmatch(row["parent_sha256"]) is None
            or SHA_PATTERN.fullmatch(row["successor_sha256"]) is None
        ):
            reject("E_AMENDMENT", f"malformed amendment row: {path_value}")
        current = safe_repo_path(path_value, "E_AMENDMENT")
        if git_blob_sha(EXPECTED_PARENT_COMMIT, path_value) != row["parent_sha256"]:
            reject("E_PARENT_BLOB", f"parent blob digest differs: {path_value}")
        if sha256(current) != row["successor_sha256"]:
            reject("E_AMENDMENT", f"successor digest differs: {path_value}")
        if row["parent_sha256"] == row["successor_sha256"]:
            reject("E_AMENDMENT", f"amendment has no byte delta: {path_value}")
        result[path_value] = row
    return result


def verify_parent_manifests(contract: dict[str, Any], amendments: dict[str, dict[str, str]]) -> None:
    if contract["parent_manifests"] != {
        key: {"path": path, "sha256": digest, "entry_base": base}
        for key, (path, digest, base) in EXPECTED_PARENT_MANIFESTS.items()
    }:
        reject("E_PARENT_MANIFEST", "parent manifest bindings differ")
    for key, (relative, expected_digest, base) in EXPECTED_PARENT_MANIFESTS.items():
        path = safe_repo_path(relative, "E_PARENT_MANIFEST")
        if sha256(path) != expected_digest:
            reject("E_PARENT_MANIFEST", f"immutable {key} manifest differs")
        entries = parse_hash_manifest(path)
        for name, digest in entries.items():
            target_relative = (Path(base) / name).as_posix() if base != "." else name
            target = safe_repo_path(target_relative, "E_PARENT_MANIFEST")
            actual = sha256(target)
            if actual == digest:
                continue
            amendment = amendments.get(target_relative)
            if amendment is None or amendment["parent_sha256"] != digest or amendment["successor_sha256"] != actual:
                reject("E_UNDECLARED_DRIFT", f"undeclared {key} manifest drift: {target_relative}")


def verify_parent_artifacts(contract: dict[str, Any]) -> None:
    if contract["parent_artifacts"] != EXPECTED_PARENT_ARTIFACTS:
        reject("E_PARENT_ARTIFACT", "parent artifact binding set differs")
    for relative, digest in EXPECTED_PARENT_ARTIFACTS.items():
        if sha256(safe_repo_path(relative, "E_PARENT_ARTIFACT")) != digest:
            reject("E_PARENT_ARTIFACT", f"immutable parent artifact differs: {relative}")


def select_coverage_contract_digest(
    binding: dict[str, Any],
    workflow_contract: dict[str, Any],
) -> str:
    expected_keys = {"kind", "diagnostic", "formal"}
    if set(binding) != expected_keys or binding.get("kind") != "workflow-dual-sha256":
        reject("E_STEP4_BINDING", "coverage workflow binding schema differs")
    expected_states = {
        "diagnostic": ("diagnostic-ready", "diagnostic-ready"),
        "formal": ("formal-ready", "formal-ready"),
    }
    for state, (contract_status, publication_status) in expected_states.items():
        record = binding.get(state)
        if (
            not isinstance(record, dict)
            or set(record) != {"contract_status", "publication_status", "sha256"}
            or record.get("contract_status") != contract_status
            or record.get("publication_status") != publication_status
            or SHA_PATTERN.fullmatch(str(record.get("sha256", ""))) is None
        ):
            reject("E_STEP4_BINDING", f"coverage workflow binding differs: {state}")

    tooling_manifest = workflow_contract.get("tooling_manifest")
    if not isinstance(tooling_manifest, dict):
        reject("E_STEP4_BINDING", "coverage contract tooling manifest is missing")
    matches = [
        state
        for state, (contract_status, publication_status) in expected_states.items()
        if workflow_contract.get("status") == contract_status
        and tooling_manifest.get("publication_status") == publication_status
    ]
    if len(matches) != 1:
        reject("E_STEP4_BINDING", "coverage contract workflow state is forbidden")

    # Both allowed byte identities are derived from the same exact JSON object
    # by changing only the two workflow status fields.  This pre-authorizes the
    # direct-child formal freeze without weakening any other contract byte.
    for state, (contract_status, publication_status) in expected_states.items():
        projection = copy.deepcopy(workflow_contract)
        projection["status"] = contract_status
        projection["tooling_manifest"]["publication_status"] = publication_status
        projection_payload = (
            json.dumps(projection, indent=2, ensure_ascii=True) + "\n"
        ).encode("utf-8")
        projection_sha = hashlib.sha256(projection_payload).hexdigest()
        if projection_sha != binding[state]["sha256"]:
            reject("E_STEP4_BINDING", f"coverage contract {state} projection hash differs")
    return binding[matches[0]]["sha256"]


def verify_step4_runtime_bindings(contract: dict[str, Any]) -> None:
    if contract["step4_runtime_bindings"] != EXPECTED_STEP4_RUNTIME_BINDINGS:
        reject("E_STEP4_BINDING", "Step 4 authority/view binding set differs")
    for relative, binding in EXPECTED_STEP4_RUNTIME_BINDINGS.items():
        path = safe_repo_path(relative, "E_STEP4_BINDING")
        if relative == "scripts/v934/step4/coverage-contract.json":
            if not isinstance(binding, dict):
                reject("E_STEP4_BINDING", "coverage workflow binding must be typed")
            digest = select_coverage_contract_digest(
                binding,
                load_json(path, "E_STEP4_BINDING"),
            )
        else:
            if not isinstance(binding, str) or SHA_PATTERN.fullmatch(binding) is None:
                reject("E_STEP4_BINDING", f"Step 4 runtime binding is not a SHA: {relative}")
            digest = binding
        if sha256(path) != digest:
            reject("E_STEP4_BINDING", f"Step 4 authority/view input differs: {relative}")


def verify_binding_records(successor: dict[str, Any], expected_paths: dict[str, str]) -> None:
    bindings = successor.get("bindings")
    if not isinstance(bindings, dict):
        reject("E_BINDING_SCOPE", "successor bindings are missing")
    for key, expected_path in expected_paths.items():
        record = bindings.get(key)
        if not isinstance(record, dict) or record.get("path") != expected_path:
            reject("E_BINDING_SCOPE", f"successor binding path differs: {key}")
        if set(record) - {"path", "sha256", "disposition", "lane"}:
            reject("E_BINDING_SCOPE", f"successor binding fields differ: {key}")
        target = safe_repo_path(expected_path, "E_BINDING_SCOPE")
        if record.get("sha256") != sha256(target):
            reject("E_BINDING_SCOPE", f"successor binding digest differs: {key}")


def verify_pair_data(
    name: str,
    parent: dict[str, Any],
    successor: dict[str, Any],
    allowed: set[str],
    expected_paths: dict[str, str],
) -> None:
    parent_bindings = parent.get("bindings")
    successor_bindings = successor.get("bindings")
    if not isinstance(parent_bindings, dict) or not isinstance(successor_bindings, dict):
        reject("E_BINDING_SCOPE", f"{name} binding object is missing")
    if set(parent_bindings) != set(successor_bindings):
        reject("E_BINDING_SCOPE", f"{name} binding key set differs")
    changed = {key for key in parent_bindings if parent_bindings[key] != successor_bindings[key]}
    if changed != allowed:
        reject("E_BINDING_SCOPE", f"{name} changed binding set differs: {sorted(changed)}")
    verify_binding_records(successor, expected_paths)
    normalized = copy.deepcopy(successor)
    for key in allowed:
        normalized["bindings"][key] = copy.deepcopy(parent_bindings[key])
    if name == "external":
        parent_amendments = parent.get("source_amendments")
        successor_amendments = successor.get("source_amendments")
        if (
            not isinstance(parent_amendments, list)
            or not isinstance(successor_amendments, list)
            or successor_amendments[:-1] != parent_amendments
            or len(successor_amendments) != len(parent_amendments) + 1
        ):
            reject("E_SOURCE_AMENDMENT", "external source amendment append differs")
        redis = successor_amendments[-1]
        redis_path = REDIS_SOURCE_PATH
        if (
            not isinstance(redis, dict)
            or redis.get("source_id") != REDIS_SOURCE_ID
            or redis.get("source_path") != redis_path
            or redis.get("original_sha256") != git_blob_sha(EXPECTED_PARENT_COMMIT, redis_path)
            or redis.get("amended_sha256") != sha256(safe_repo_path(redis_path))
            or redis.get("report_fqcns")
            != ["com.foggyframework.dataset.db.model.cache.provider.RedisCrossJvmCacheIT"]
            or not isinstance(redis.get("reason"), str)
            or not redis["reason"].strip()
        ):
            reject("E_SOURCE_AMENDMENT", "Redis successor amendment tuple differs")
        normalized["source_amendments"] = copy.deepcopy(parent_amendments)
    if normalized != parent:
        reject("E_SEMANTIC_DRIFT", f"{name} changed outside declared bindings/source amendments")


def verify_successor_contracts(contract: dict[str, Any]) -> None:
    definitions = contract["successors"]
    if set(definitions) != set(EXPECTED_PAIR_RULES):
        reject("E_SCHEMA", "successor contract set differs")
    for name, rule in EXPECTED_PAIR_RULES.items():
        definition = definitions[name]
        expected_fields = {"parent", "successor", "allowed_binding_changes"}
        if name == "external":
            expected_fields.add("allowed_source_amendment_append")
        if set(definition) != expected_fields \
                or definition.get("parent") != rule["parent"] \
                or definition.get("successor") != rule["successor"]:
            reject("E_SCHEMA", f"{name} successor definition differs")
        if set(definition["allowed_binding_changes"]) != rule["allowed"]:
            reject("E_SCHEMA", f"{name} allowed binding declaration differs")
        if name == "external" and definition.get("allowed_source_amendment_append") != [REDIS_SOURCE_ID]:
            reject("E_SCHEMA", "external amendment declaration differs")
        parent_path = safe_repo_path(definition["parent"], "E_SCHEMA")
        successor_path = safe_repo_path(definition["successor"], "E_SCHEMA")
        verify_pair_data(
            name,
            load_json(parent_path),
            load_json(successor_path),
            rule["allowed"],
            rule["paths"],
        )


def protected_git_paths(contract: dict[str, Any]) -> set[str]:
    result = set(contract["protected_git_scope"]["explicit_paths"])
    step3_entries = parse_hash_manifest(safe_repo_path(EXPECTED_PARENT_MANIFESTS["step3"][0]))
    result.update(step3_entries)
    for relative in (
        "scripts/v934/step3/step3-required-contract.json",
        "scripts/v934/step3/database-matrix-contract.json",
        "scripts/v934/step3/external-matrix-contract.json",
        "scripts/v934/step3/preagg-addon-lifecycle-contract.json",
    ):
        value = load_json(safe_repo_path(relative))
        for binding in value["bindings"].values():
            if isinstance(binding, dict) and isinstance(binding.get("path"), str):
                result.add(binding["path"])
    tree_manifest = safe_repo_path(contract["protected_git_scope"]["database_tree_manifest"])
    with tree_manifest.open(encoding="utf-8", newline="") as stream:
        trees = [row["path"] for row in csv.DictReader(stream, delimiter="\t")]
    tracked = git("ls-tree", "-r", "--name-only", EXPECTED_PARENT_COMMIT, "--", *trees)
    assert isinstance(tracked, str)
    result.update(line for line in tracked.splitlines() if line)
    untracked = git("ls-files", "--others", "--exclude-standard", "--", *trees)
    assert isinstance(untracked, str)
    if untracked.strip():
        reject("E_UNDECLARED_DRIFT", f"untracked protected-tree content: {untracked.splitlines()[0]}")
    deferred_path = safe_repo_path(contract["protected_git_scope"]["deferred_inventory"])
    source_path = safe_repo_path(contract["protected_git_scope"]["source_inventory"])
    with source_path.open(encoding="utf-8", newline="") as stream:
        sources = {row["source_id"]: row["source_path"] for row in csv.DictReader(stream, delimiter="\t")}
    with deferred_path.open(encoding="utf-8", newline="") as stream:
        for row in csv.DictReader(stream, delimiter="\t"):
            if row.get("execution_step") == "3":
                result.add(sources[row["source_id"]])
    return result


def validate_drift_rows(changed: set[str], amendments: set[str]) -> None:
    if changed != amendments:
        missing = sorted(changed - amendments)
        extra = sorted(amendments - changed)
        reject("E_UNDECLARED_DRIFT", f"protected drift differs: missing={missing} extra={extra}")


def verify_protected_drift(contract: dict[str, Any], amendments: dict[str, dict[str, str]]) -> None:
    protected = protected_git_paths(contract)
    raw_changed = git(
        "diff", "--name-only", "--no-renames", EXPECTED_PARENT_COMMIT, "--"
    )
    assert isinstance(raw_changed, str)
    changed = {relative for relative in raw_changed.splitlines() if relative in protected}
    for relative in changed:
        current = safe_repo_path(relative, "E_PROTECTED_SCOPE")
        parent_digest = git_blob_sha(EXPECTED_PARENT_COMMIT, relative)
        current_digest = sha256(current)
        row = amendments.get(relative)
        if row is None or row["parent_sha256"] != parent_digest or row["successor_sha256"] != current_digest:
            reject("E_UNDECLARED_DRIFT", f"undeclared protected drift: {relative}")
    validate_drift_rows(changed, set(amendments))


def verify_successor_manifest(path: Path) -> None:
    if path.is_symlink() or not path.is_file():
        reject("E_SUCCESSOR_MANIFEST", f"successor manifest is missing/symlink: {path}")
    entries = parse_hash_manifest(path, set(EXPECTED_SUCCESSOR_FILES))
    if list(entries) != sorted(EXPECTED_SUCCESSOR_FILES):
        reject("E_SUCCESSOR_MANIFEST", "successor manifest order differs")
    for name, digest in entries.items():
        target = HERE / name
        if target.is_symlink() or not target.is_file() or sha256(target) != digest:
            reject("E_SUCCESSOR_MANIFEST", f"successor artifact differs: {name}")


def run_successor_adapter(arguments: list[str], label: str) -> dict[str, Any]:
    completed = subprocess.run(
        [sys.executable, *arguments],
        cwd=ROOT,
        env=git_environment(),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    if completed.returncode != 0:
        reject(
            "E_SUCCESSOR_RUNTIME",
            f"{label} failed: {completed.stderr.strip() or completed.stdout.strip()}",
        )
    try:
        payload = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise OverlayError("E_SUCCESSOR_RUNTIME", f"{label} output is not JSON") from error
    if not isinstance(payload, dict):
        reject("E_SUCCESSOR_RUNTIME", f"{label} output is not an object")
    return payload


def verify_successor_runtime_adapters() -> None:
    database_runner = safe_repo_path(
        "scripts/verify-v934-database-matrix.sh",
        "E_SUCCESSOR_RUNTIME",
    ).read_text(encoding="utf-8")
    required_runner = safe_repo_path(
        "scripts/verify-v934-step3-required-matrix.sh",
        "E_SUCCESSOR_RUNTIME",
    ).read_text(encoding="utf-8")
    report_inventory = safe_repo_path(
        "scripts/v934/step4/report_inventory_tool.py",
        "E_SUCCESSOR_RUNTIME",
    ).read_text(encoding="utf-8")
    if database_runner.count(
        'STATE_NEGATIVE_TOOL="$STEP4_SUCCESSOR_DIR/database_state_negative_tool.py"'
    ) != 1:
        reject("E_SUCCESSOR_RUNTIME", "database runner does not select the state adapter")
    if required_runner.count(
        'REPORT_TOOL="$STEP4_DIR/successor/step3_required_report_tool.py"'
    ) != 1:
        reject("E_SUCCESSOR_RUNTIME", "required runner does not select the report adapter")
    if report_inventory.count(
        'STEP3_REPORT_TOOL = Path("scripts/v934/step4/successor/step3_required_report_tool.py")'
    ) != 1:
        reject("E_SUCCESSOR_RUNTIME", "report inventory does not select the report adapter")

    state_tool = HERE / "database_state_negative_tool.py"
    state_contract = ROOT / "scripts/v934/step3/database_state_contract.json"
    database_contract = HERE / "database-matrix-contract.json"
    state = run_successor_adapter([str(state_tool), "validate"], "database-state adapter")
    if state != {
        "database_contract_sha256": sha256(database_contract),
        "probes": 18,
        "state_contract_sha256": sha256(state_contract),
        "status": "passed",
    }:
        reject("E_SUCCESSOR_RUNTIME", "database-state adapter selected the wrong contract")

    required_tool = HERE / "step3_required_report_tool.py"
    rewrite = run_successor_adapter(
        [str(required_tool), "successor-self-test"],
        "required-report state rewrite",
    )
    if rewrite != {"state_verifier_rewrites": 1, "status": "passed"}:
        reject("E_SUCCESSOR_RUNTIME", "required-report state rewrite differs")
    required_contract = HERE / "step3-required-contract.json"
    required = run_successor_adapter(
        [
            str(required_tool),
            "--repo-root", str(ROOT),
            "--contract", str(required_contract),
            "validate",
        ],
        "required-report adapter",
    )
    if (
        required.get("status") != "passed"
        or required.get("contract_sha256") != sha256(required_contract)
        or required.get("reports") != 45
        or required.get("testcase_nodes") != 446
    ):
        reject("E_SUCCESSOR_RUNTIME", "required-report adapter selected the wrong contract")


def validate(contract_path: Path, manifest_path: Path) -> None:
    contract = load_json(contract_path, "E_CONTRACT")
    expected_fields = {
        "schema_version", "kind", "parent_commit", "parent_manifests",
        "parent_artifacts", "declared_amendments", "successors", "frozen_semantics",
        "protected_git_scope", "activation_requirements", "step4_runtime_bindings",
    }
    if set(contract) != expected_fields or contract.get("schema_version") != 1 \
            or contract.get("kind") != "v934-step4-step3-successor-overlay-contract" \
            or contract.get("parent_commit") != EXPECTED_PARENT_COMMIT:
        reject("E_CONTRACT", "overlay contract identity or fields differ")
    if contract["frozen_semantics"] != EXPECTED_FROZEN_SEMANTICS:
        reject("E_CONTRACT", "frozen totals/negative semantics differ")
    if contract["protected_git_scope"] != EXPECTED_PROTECTED_SCOPE:
        reject("E_CONTRACT", "protected Git scope differs")
    if contract["activation_requirements"] != EXPECTED_ACTIVATION_REQUIREMENTS:
        reject("E_CONTRACT", "successor activation requirements differ")
    if COMMIT_PATTERN.fullmatch(contract["parent_commit"]) is None:
        reject("E_CONTRACT", "parent commit is malformed")
    completed = subprocess.run(
        [
            "git",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-c",
            "core.hooksPath=/dev/null",
            "-C",
            str(ROOT),
            "merge-base",
            "--is-ancestor",
            EXPECTED_PARENT_COMMIT,
            "HEAD",
        ],
        env=git_environment(),
        check=False,
    )
    if completed.returncode != 0:
        reject("E_PARENT_COMMIT", "accepted Step 3 parent is not an ancestor of HEAD")
    amendments = read_amendments(contract)
    verify_parent_artifacts(contract)
    verify_step4_runtime_bindings(contract)
    verify_parent_manifests(contract, amendments)
    verify_successor_contracts(contract)
    verify_protected_drift(contract, amendments)
    verify_successor_manifest(manifest_path)
    verify_successor_runtime_adapters()


def expect(code: str, callback: Callable[[], None]) -> None:
    try:
        callback()
    except OverlayError as error:
        if error.code == code:
            return
        reject("E_NEGATIVE", f"expected {code}, observed {error.code}")
    reject("E_NEGATIVE", f"expected {code}, mutation was accepted")


def publish_no_clobber(path: Path, payload: bytes) -> None:
    """Durably publish a new regular file without an overwrite race."""
    if path.exists() or path.is_symlink():
        reject("E_OUTPUT", f"refusing to overwrite output: {path}")
    parent = path.parent
    parent.mkdir(parents=True, exist_ok=True)
    if parent.is_symlink() or not parent.is_dir() or parent.resolve() != parent:
        reject("E_OUTPUT", f"output parent is not a regular directory: {parent}")
    temporary = parent / f".{path.name}.{os.getpid()}.tmp"
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor: int | None = None
    published = False
    completed = False
    published_identity: tuple[int, int] | None = None
    try:
        descriptor = os.open(temporary, flags, 0o600)
        with os.fdopen(descriptor, "wb", closefd=True) as stream:
            descriptor = None
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        staged = temporary.stat(follow_symlinks=False)
        published_identity = (staged.st_dev, staged.st_ino)
        # link(2) supplies the atomic no-clobber guarantee that replace(2) lacks.
        os.link(temporary, path, follow_symlinks=False)
        published = True
        current = path.stat(follow_symlinks=False)
        if (current.st_dev, current.st_ino) != published_identity:
            reject("E_OUTPUT", f"published output identity differs: {path}")
        temporary.unlink()
        directory_flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
        if hasattr(os, "O_NOFOLLOW"):
            directory_flags |= os.O_NOFOLLOW
        directory_descriptor = os.open(parent, directory_flags)
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
        current = path.stat(follow_symlinks=False)
        if (current.st_dev, current.st_ino) != published_identity:
            reject("E_OUTPUT", f"published output was replaced concurrently: {path}")
        completed = True
    except OverlayError:
        raise
    except OSError as error:
        reject("E_OUTPUT", f"cannot publish output {path}: {error.__class__.__name__}")
    finally:
        if descriptor is not None:
            os.close(descriptor)
        temporary.unlink(missing_ok=True)
        if published and not completed and published_identity is not None and path.exists():
            current = path.stat(follow_symlinks=False)
            if (current.st_dev, current.st_ino) == published_identity:
                path.unlink()
                rollback_descriptor = os.open(
                    parent,
                    os.O_RDONLY | getattr(os, "O_DIRECTORY", 0),
                )
                try:
                    os.fsync(rollback_descriptor)
                finally:
                    os.close(rollback_descriptor)
            else:
                reject("E_OUTPUT", f"published output was replaced concurrently: {path}")


def negative(output: Path) -> int:
    contract = load_json(DEFAULT_CONTRACT)
    probes: list[tuple[str, str, Callable[[], None]]] = []
    coverage_binding = contract["step4_runtime_bindings"][
        "scripts/v934/step4/coverage-contract.json"
    ]
    canonical_coverage_contract = load_json(
        safe_repo_path(
            "scripts/v934/step4/coverage-contract.json",
            "E_STEP4_BINDING",
        ),
        "E_STEP4_BINDING",
    )
    formal_projection = copy.deepcopy(canonical_coverage_contract)
    formal_projection["status"] = "formal-ready"
    formal_projection["tooling_manifest"]["publication_status"] = "formal-ready"
    if (
        select_coverage_contract_digest(coverage_binding, formal_projection)
        != coverage_binding["formal"]["sha256"]
    ):
        reject("E_STEP4_BINDING", "formal workflow binding positive control differs")
    verify_git_environment_isolation()
    crossed_projection = copy.deepcopy(formal_projection)
    crossed_projection["tooling_manifest"]["publication_status"] = "diagnostic-ready"
    probes.append((
        "coverage-workflow-crossed-state", "E_STEP4_BINDING",
        lambda: select_coverage_contract_digest(coverage_binding, crossed_projection),
    ))
    formal_hash_mutation = copy.deepcopy(coverage_binding)
    formal_hash_mutation["formal"]["sha256"] = "0" * 64
    probes.append((
        "coverage-workflow-formal-hash-drift", "E_STEP4_BINDING",
        lambda: select_coverage_contract_digest(formal_hash_mutation, formal_projection),
    ))
    parent_mutation = copy.deepcopy(contract)
    parent_mutation["parent_manifests"]["step1"]["sha256"] = "0" * 64
    probes.append((
        "parent-manifest-binding-drift", "E_PARENT_MANIFEST",
        lambda: verify_parent_manifests(parent_mutation, read_amendments(contract)),
    ))
    step4_mutation = copy.deepcopy(contract)
    step4_mutation["step4_runtime_bindings"][
        "scripts/v934/step4/authority_parent_lib.sh"
    ] = "0" * 64
    probes.append((
        "step4-runtime-binding-drift", "E_STEP4_BINDING",
        lambda: verify_step4_runtime_bindings(step4_mutation),
    ))
    required_def = contract["successors"]["required"]
    required_parent = load_json(safe_repo_path(required_def["parent"]))
    required_successor = load_json(safe_repo_path(required_def["successor"]))
    required_mutation = copy.deepcopy(required_successor)
    required_mutation["children"]["database-matrix"]["candidate"] = "forged.json"
    probes.append((
        "undeclared-semantic-drift", "E_SEMANTIC_DRIFT",
        lambda: verify_pair_data(
            "required", required_parent, required_mutation,
            EXPECTED_PAIR_RULES["required"]["allowed"], EXPECTED_PAIR_RULES["required"]["paths"],
        ),
    ))
    addon_def = contract["successors"]["addon"]
    addon_parent = load_json(safe_repo_path(addon_def["parent"]))
    addon_successor = load_json(safe_repo_path(addon_def["successor"]))
    addon_mutation = copy.deepcopy(addon_successor)
    addon_mutation["bindings"]["runner"]["path"] = "scripts/forged.sh"
    probes.append((
        "wrong-binding-path", "E_BINDING_SCOPE",
        lambda: verify_pair_data(
            "addon", addon_parent, addon_mutation,
            EXPECTED_PAIR_RULES["addon"]["allowed"], EXPECTED_PAIR_RULES["addon"]["paths"],
        ),
    ))
    external_def = contract["successors"]["external"]
    external_parent = load_json(safe_repo_path(external_def["parent"]))
    external_successor = load_json(safe_repo_path(external_def["successor"]))
    external_mutation = copy.deepcopy(external_successor)
    external_mutation["source_amendments"] = external_mutation["source_amendments"][:-1]
    probes.append((
        "missing-source-amendment", "E_SOURCE_AMENDMENT",
        lambda: verify_pair_data(
            "external", external_parent, external_mutation,
            EXPECTED_PAIR_RULES["external"]["allowed"], EXPECTED_PAIR_RULES["external"]["paths"],
        ),
    ))
    external_path_mutation = copy.deepcopy(external_successor)
    external_path_mutation["source_amendments"][-1]["source_path"] = (
        "foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/preagg/"
        "PreAggregationDataValidationTest.java"
    )
    probes.append((
        "wrong-source-amendment-path", "E_SOURCE_AMENDMENT",
        lambda: verify_pair_data(
            "external", external_parent, external_path_mutation,
            EXPECTED_PAIR_RULES["external"]["allowed"], EXPECTED_PAIR_RULES["external"]["paths"],
        ),
    ))
    manifest_rows = parse_hash_manifest(DEFAULT_MANIFEST)
    probes.append((
        "successor-manifest-extra", "E_SUCCESSOR_MANIFEST",
        lambda: parse_hash_manifest_rows_for_negative({**manifest_rows, "forged": "0" * 64}),
    ))
    probes.append((
        "undeclared-protected-drift", "E_UNDECLARED_DRIFT",
        lambda: validate_drift_rows({"protected/file"}, set()),
    ))
    rows = [
        ("coverage-workflow-formal-positive", "validated", "validated", "passed"),
        ("git-environment-isolation-positive", "validated", "validated", "passed"),
    ]
    for name, code, callback in probes:
        expect(code, callback)
        rows.append((name, code, code, "passed"))
    publish_no_clobber(
        output,
        (
            "probe\texpected_error\tactual_error\tstatus\n"
            + "".join("\t".join(row) + "\n" for row in rows)
        ).encode("utf-8"),
    )
    return len(rows)


def verify_git_environment_isolation() -> None:
    baseline_root = git("rev-parse", "--show-toplevel")
    baseline_shallow = git("rev-parse", "--is-shallow-repository")
    baseline_parent = git("rev-list", "--parents", "-n", "1", "HEAD")
    overrides = {
        "GIT_DIR": "/definitely-missing/v934-overlay-git-dir",
        "GIT_WORK_TREE": "/definitely-missing/v934-overlay-worktree",
        "GIT_INDEX_FILE": "/definitely-missing/v934-overlay-index",
        "GIT_COMMON_DIR": "/definitely-missing/v934-overlay-common-dir",
        "GIT_OBJECT_DIRECTORY": "/definitely-missing/v934-overlay-objects",
        "GIT_ALTERNATE_OBJECT_DIRECTORIES": "/definitely-missing/v934-overlay-alternates",
        "GIT_SHALLOW_FILE": "/definitely-missing/v934-overlay-shallow",
        "GIT_GRAFT_FILE": "/definitely-missing/v934-overlay-grafts",
        "GIT_REPLACE_REF_BASE": "refs/forged-replace/",
        "GIT_NAMESPACE": "forged-overlay-namespace",
        "GIT_CONFIG_PARAMETERS": "'core.worktree=/definitely-missing/v934-overlay-config'",
        "GIT_CONFIG_COUNT": "1",
        "GIT_CONFIG_KEY_0": "core.worktree",
        "GIT_CONFIG_VALUE_0": "/definitely-missing/v934-overlay-counted-config",
    }
    previous = {name: os.environ.get(name) for name in overrides}
    try:
        os.environ.update(overrides)
        sanitized = git_environment()
        retained_git_names = {
            name for name in sanitized if name.startswith("GIT_")
        }
        if retained_git_names != {
            "GIT_CONFIG_GLOBAL",
            "GIT_CONFIG_NOSYSTEM",
            "GIT_NO_REPLACE_OBJECTS",
            "GIT_OPTIONAL_LOCKS",
        }:
            reject("E_NEGATIVE", "unsafe ambient Git variable survived isolation")
        if (
            git("rev-parse", "--show-toplevel") != baseline_root
            or git("rev-parse", "--is-shallow-repository") != baseline_shallow
            or git("rev-list", "--parents", "-n", "1", "HEAD") != baseline_parent
        ):
            reject("E_NEGATIVE", "ambient Git override changed overlay repository identity")
    finally:
        for name, value in previous.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value


def parse_hash_manifest_rows_for_negative(rows: dict[str, str]) -> None:
    if set(rows) != set(EXPECTED_SUCCESSOR_FILES):
        reject("E_SUCCESSOR_MANIFEST", "synthetic successor manifest set differs")


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument("--contract", type=Path, default=DEFAULT_CONTRACT)
    result.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    commands = result.add_subparsers(dest="command", required=True)
    commands.add_parser("validate")
    negative_parser = commands.add_parser("negative")
    negative_parser.add_argument("--output", type=Path, required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "validate":
            validate(args.contract.resolve(), args.manifest.resolve())
            print(
                "V934_STEP4_SUCCESSOR_OVERLAY "
                "parents=3 contracts=4 amendments=17 step4_bindings=8 required=45/446 addon=2/6 status=passed"
            )
        else:
            count = negative(args.output.absolute())
            print(f"V934_STEP4_SUCCESSOR_NEGATIVE passed={count} total={count}")
    except (OverlayError, OSError) as error:
        if isinstance(error, OverlayError):
            print(f"[{error.code}] {error}", file=sys.stderr)
        else:
            print(f"[E_RUNTIME] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
