#!/usr/bin/env python3
"""Fast, hermetic negatives for Step 4 exact coverage threshold tooling."""

from __future__ import annotations

import argparse
import ast
import copy
from fractions import Fraction
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from types import SimpleNamespace
from typing import Any, Callable


SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
import coverage_xml_tool as tool  # noqa: E402


def fixture_git_environment() -> dict[str, str]:
    environment = {
        name: os.environ[name]
        for name in ("PATH", "SYSTEMROOT", "TMPDIR", "TMP", "TEMP")
        if name in os.environ
    }
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "LANG": "C",
            "LC_ALL": "C",
        }
    )
    return environment


def fixture_git(repository: Path, arguments: list[str]) -> str:
    process = subprocess.run(
        ["git", "-C", str(repository), *arguments],
        env=fixture_git_environment(),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=30,
        check=False,
    )
    if process.returncode != 0:
        raise RuntimeError(
            f"frozen Git fixture command failed rc={process.returncode}: {arguments[0]}"
        )
    return process.stdout


def initialize_git_repository(repository: Path, contents: tuple[bytes, ...]) -> list[str]:
    repository.mkdir(mode=0o700)
    fixture_git(repository, ["init", "-q", "--object-format=sha1"])
    fixture_git(repository, ["config", "user.name", "v934-xml-negative"])
    fixture_git(
        repository,
        ["config", "user.email", "v934-xml-negative@example.invalid"],
    )
    heads: list[str] = []
    tracked = repository / "tracked.txt"
    for number, payload in enumerate(contents, 1):
        tracked.write_bytes(payload)
        tracked.chmod(0o644)
        fixture_git(repository, ["add", "--", "tracked.txt"])
        fixture_git(repository, ["commit", "-q", "-m", f"fixture-{number}"])
        heads.append(
            fixture_git(
                repository, ["rev-parse", "--verify", "HEAD^{commit}"]
            ).strip()
        )
    return heads


def with_git_overrides(
    overrides: dict[str, str],
    action: Callable[[], Any],
) -> Any:
    previous = {name: os.environ.get(name) for name in overrides}
    try:
        os.environ.update(overrides)
        return action()
    finally:
        for name, value in previous.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value


def expect_failure(
    cases: dict[str, dict[str, str]],
    name: str,
    expected_code: str,
    action: Callable[[], Any],
) -> None:
    try:
        action()
    except tool.CoverageXmlError as exc:
        if exc.code != expected_code:
            raise RuntimeError(
                f"{name}: expected {expected_code}, got {exc.code}: {exc.message}"
            ) from exc
        cases[name] = {
            "expected_code": expected_code,
            "observed_code": exc.code,
            "status": "passed",
        }
        return
    raise RuntimeError(f"{name}: false green; expected {expected_code}")


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def observation_counter(covered: int, total: int) -> dict[str, Any]:
    missed = total - covered
    return {
        "missed": missed,
        "covered": covered,
        "total": total,
        "ratio": round(covered / total, 12) if total else None,
        "fraction": f"{covered}/{total}" if total else None,
    }


def critical_observation_counter(
    covered: int,
    total: int,
    floor: float,
) -> dict[str, Any]:
    counter = observation_counter(covered, total)
    if total == 0:
        outcome = "not-applicable"
        gap = None
    elif covered / total >= floor:
        outcome = "at-or-above-floor"
        gap = 0.0
    else:
        outcome = "below-floor"
        gap = round(floor - covered / total, 12)
    return {
        **counter,
        "floor": floor,
        "outcome": outcome,
        "gap": gap,
    }


def synthetic_freeze_validation(
    *,
    line: tuple[int, int] = (4, 5),
    branch: tuple[int, int] = (7, 10),
    fqcn: str = "example.Critical",
    module: str = "example-module",
) -> tuple[dict[str, Any], dict[str, Any]]:
    line_counter = critical_observation_counter(*line, 0.8)
    branch_counter = critical_observation_counter(*branch, 0.7)
    candidate_floor_outcome = (
        "below-floor"
        if "below-floor" in (line_counter["outcome"], branch_counter["outcome"])
        else "at-or-above-floor"
    )
    evidence = {
        "run_id": "negative-diagnostic",
        "git_head": "1" * 40,
        "source_sha256": "2" * 64,
        "run_status_sha256": "3" * 64,
        "summary_sha256": "4" * 64,
        "observation_sha256": "5" * 64,
        "coverage_contract_sha256": "6" * 64,
        "threshold_sha256": tool.EXPECTED_DIAGNOSTIC_THRESHOLD_SHA256,
        "exec_manifest_sha256": "8" * 64,
        "aggregate_exec_sha256": "9" * 64,
        "aggregate_xml_sha256": "a" * 64,
        "workspace_class_tree_sha256": "b" * 64,
    }
    validation = {
        "evidence": evidence,
        "observation": {
            "aggregate_observed": {
                "line": observation_counter(90, 100),
                "branch": observation_counter(80, 100),
            },
            "critical_classes": [
                {
                    "fqcn": fqcn,
                    "module": module,
                    "artifact_id": module.rsplit("/", 1)[-1],
                    "line": line_counter,
                    "branch": branch_counter,
                    "candidate_floor_outcome": candidate_floor_outcome,
                }
            ],
        },
    }
    step1 = {"critical_classes": [{"fqcn": fqcn, "module": module}]}
    return validation, step1


def record_positive(
    cases: dict[str, dict[str, str]],
    name: str,
    observed: str = "validated",
) -> None:
    cases[name] = {
        "expected_code": observed,
        "observed_code": observed,
        "status": "passed",
    }


def build_child_lifecycle_fixture(run_root: Path, run_id: str) -> dict[str, Any]:
    ready_root = run_root / "child-ready"
    completion_root = run_root / "child-lifecycle"
    ready_root.mkdir(parents=True)
    completion_root.mkdir()
    children: list[dict[str, Any]] = []
    ready_values: dict[str, dict[str, Any]] = {}
    completion_values: dict[str, dict[str, str]] = {}
    for number, child in enumerate(tool.CHILD_NAMES, 1):
        ready = {
            "schema_version": 1,
            "kind": "v934-step4-child-ready",
            "run_id": run_id,
            "child": child,
            "pid": 20_000 + number,
            "pgid": 20_000 + number,
            "sid": 20_000 + number,
            "starttime_ticks": 30_000 + number,
            "boot_id": f"{number:08x}-1111-2222-3333-{number:012x}",
            "status": "ready",
        }
        ready_path = ready_root / f"{child}.json"
        write_json(ready_path, ready)
        ready_path.chmod(0o600)
        ready_sha = tool.sha256_file(ready_path, "E_NEGATIVE_FIXTURE")
        completion = {
            "run_id": run_id,
            "child": child,
            "leader_pid": str(ready["pid"]),
            "leader_sid": str(ready["sid"]),
            "leader_starttime_ticks": str(ready["starttime_ticks"]),
            "boot_id": ready["boot_id"],
            "leader_exit_code": "0",
            "leader_reaped": "1",
            "ready_receipt_sha256": ready_sha,
            "process_group_residue": "0",
            "status": "passed",
        }
        completion_path = completion_root / f"{child}-complete.env"
        completion_path.write_bytes(
            tool.encode_env(
                completion,
                tool.CHILD_COMPLETION_FIELDS,
                "E_NEGATIVE_FIXTURE",
            )
        )
        completion_path.chmod(0o644)
        children.append(
            {
                "child": child,
                "complete_sha256": tool.sha256_file(
                    completion_path, "E_NEGATIVE_FIXTURE"
                ),
                "leader_pid": ready["pid"],
                "leader_sid": ready["sid"],
                "leader_starttime_ticks": ready["starttime_ticks"],
                "boot_id": ready["boot_id"],
                "leader_reaped": 1,
                "process_group_residue": 0,
                "ready_receipt_sha256": ready_sha,
                "status": "passed",
            }
        )
        ready_values[child] = ready
        completion_values[child] = completion
    manifest = {
        "schema_version": 1,
        "kind": "v934-step4-child-lifecycle",
        "run_id": run_id,
        "child_count": len(tool.CHILD_NAMES),
        "children": children,
        "status": "passed",
    }
    write_json(run_root / "child-lifecycle.json", manifest)
    return {
        "ready": ready_values,
        "completion": completion_values,
        "manifest": manifest,
    }


def run(args: argparse.Namespace) -> dict[str, Any]:
    repo_root = args.repo_root.resolve()
    tool.real_directory(repo_root, "E_REPO_ROOT")
    cases: dict[str, dict[str, str]] = {}

    capsule_self_test = tool.diagnostic_capsule.run_self_test()
    if (
        capsule_self_test.get("status") != "passed"
        or capsule_self_test.get("case_count") != 8
        or len(capsule_self_test.get("cases", [])) != 8
    ):
        raise RuntimeError("portable diagnostic capsule self-test differs")
    record_positive(cases, "frozen-diagnostic-capsule-self-test", "8-cases")

    identity_manifest = {
        "class_id_consistency_scope": tool.EXPECTED_CLASS_ID_CONSISTENCY_SCOPE,
        "unique_execution_classes": 2,
    }
    identity_provenance = {
        "class_id_consistency_scope": tool.EXPECTED_CLASS_ID_CONSISTENCY_SCOPE,
        "merge_semantics": tool.EXPECTED_AGGREGATE_MERGE_SEMANTICS,
        "aggregate_exec": {"execution_class_count": 2},
    }
    observed_identity_count = tool.validate_jacoco_execution_identity_contract(
        identity_manifest,
        identity_provenance,
    )
    if observed_identity_count != 2:
        raise RuntimeError(
            f"JaCoCo identity baseline count differs: {observed_identity_count}"
        )
    record_positive(cases, "jacoco-class-id-identity-positive", "exact-class-id-scope")

    manifest_scope_drift = copy.deepcopy(identity_manifest)
    manifest_scope_drift["class_id_consistency_scope"] = "all-loaded-classes-by-name"
    expect_failure(
        cases,
        "jacoco-manifest-class-id-scope-drift",
        "E_MANIFEST_CLASS_ID_SCOPE",
        lambda: tool.validate_jacoco_execution_identity_contract(manifest_scope_drift),
    )
    provenance_scope_drift = copy.deepcopy(identity_provenance)
    provenance_scope_drift["class_id_consistency_scope"] = "all-loaded-classes-by-name"
    expect_failure(
        cases,
        "jacoco-aggregate-class-id-scope-drift",
        "E_AGGREGATE_CLASS_ID_SCOPE",
        lambda: tool.validate_jacoco_execution_identity_contract(
            identity_manifest,
            provenance_scope_drift,
        ),
    )
    merge_semantics_drift = copy.deepcopy(identity_provenance)
    merge_semantics_drift["merge_semantics"] = "exact-session-and-probe-bitmap-union"
    expect_failure(
        cases,
        "jacoco-aggregate-merge-semantics-drift",
        "E_AGGREGATE_MERGE_SEMANTICS",
        lambda: tool.validate_jacoco_execution_identity_contract(
            identity_manifest,
            merge_semantics_drift,
        ),
    )
    aggregate_count_drift = copy.deepcopy(identity_provenance)
    aggregate_count_drift["aggregate_exec"]["execution_class_count"] = 1
    expect_failure(
        cases,
        "jacoco-aggregate-class-id-count-drift",
        "E_AGGREGATE_CLASS_ID_COUNT",
        lambda: tool.validate_jacoco_execution_identity_contract(
            identity_manifest,
            aggregate_count_drift,
        ),
    )

    huge_actual = tool.exact_counter(
        9_007_199_254_740_992,
        9_007_199_254_740_993,
        "E_EXACT_COUNTER",
        "huge actual",
    )
    one = tool.exact_counter(1, 1, "E_EXACT_COUNTER", "one")
    if tool.counter_at_least(huge_actual, one, "E_EXACT_COUNTER", "huge comparison"):
        raise RuntimeError("exact comparison rounded a strict sub-unit fraction to one")
    cases["exact-huge-fraction"] = {
        "expected_code": "strict-less-than-one",
        "observed_code": "strict-less-than-one",
        "status": "passed",
    }
    if not tool.exact_json_identity(
        {"integer": 0, "ratio": 0.0}, {"integer": 0, "ratio": 0.0}
    ):
        raise RuntimeError("strict JSON identity rejected identical typed values")
    record_positive(cases, "exact-json-identity-positive")
    expect_failure(
        cases,
        "exact-json-identity-numeric-alias",
        "E_JSON_IDENTITY",
        lambda: tool.require(
            tool.exact_json_identity({"zero": False}, {"zero": 0}),
            "E_JSON_IDENTITY",
            "boolean must not alias an integer",
        ),
    )
    tool_source = ast.parse(
        (SCRIPT_DIR / "coverage_xml_tool.py").read_text(encoding="utf-8"),
        filename="coverage_xml_tool.py",
    )
    functions = {
        node.name: node
        for node in tool_source.body
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
    }
    expected_size_calls = {
        "validate_aggregate_provenance": 2,
        "validate_report_provenance": 1,
    }
    observed_size_calls = {
        name: sum(
            1
            for node in ast.walk(functions[name])
            if isinstance(node, ast.Call)
            and isinstance(node.func, ast.Name)
            and node.func.id == "exact_file_size"
        )
        for name in expected_size_calls
    }
    if observed_size_calls != expected_size_calls:
        raise RuntimeError(
            "provenance size validators are not bound to all canonical fields: "
            f"{observed_size_calls}"
        )
    record_positive(cases, "provenance-size-validator-call-binding")
    for case_name, alias in (
        ("provenance-size-float-alias", 1.0),
        ("provenance-size-boolean-alias", True),
    ):
        expect_failure(
            cases,
            case_name,
            "E_PROVENANCE_SIZE",
            lambda alias=alias: tool.exact_file_size(
                alias,
                1,
                "E_PROVENANCE_SIZE",
                "typed provenance size",
            ),
        )
    aggregate_ratio_integer = observation_counter(5, 5)
    aggregate_ratio_integer["ratio"] = 1
    expect_failure(
        cases,
        "aggregate-observation-ratio-integer-alias",
        "E_OBSERVATION_COUNTER",
        lambda: tool.observation_exact_counter(
            aggregate_ratio_integer,
            "E_OBSERVATION_COUNTER",
            "typed aggregate line",
        ),
    )
    malformed_fraction = copy.deepcopy(huge_actual)
    malformed_fraction["fraction"] = "1/1"
    expect_failure(
        cases,
        "noncanonical-fraction",
        "E_EXACT_COUNTER",
        lambda: tool.validate_exact_counter(
            malformed_fraction, "E_EXACT_COUNTER", "malformed fraction"
        ),
    )
    expect_failure(
        cases,
        "zero-total-counter",
        "E_EXACT_COUNTER",
        lambda: tool.exact_counter(0, 0, "E_EXACT_COUNTER", "zero"),
    )
    expect_failure(
        cases,
        "formal-class-tree-drift",
        "E_FORMAL_CLASS_TREE",
        lambda: tool.require_formal_class_tree("1" * 64, "2" * 64),
    )
    expect_failure(
        cases,
        "formal-denominator-drift",
        "E_FORMAL_DENOMINATOR",
        lambda: tool.formal_metric_result(
            tool.exact_counter(90, 100, "E_NEGATIVE_FIXTURE", "formal actual"),
            tool.exact_counter(9, 10, "E_NEGATIVE_FIXTURE", "diagnostic observed"),
            tool.exact_counter(9, 10, "E_NEGATIVE_FIXTURE", "reviewed minimum"),
            "formal aggregate line",
        ),
    )
    expect_failure(
        cases,
        "formal-applicability-drift",
        "E_FORMAL_APPLICABILITY_DRIFT",
        lambda: tool.require_formal_applicability(
            "required-positive-total",
            "not-applicable-zero-total-only",
            "formal critical branch",
        ),
    )
    expect_failure(
        cases,
        "formal-applicability-drift-reverse",
        "E_FORMAL_APPLICABILITY_DRIFT",
        lambda: tool.require_formal_applicability(
            "not-applicable-zero-total-only",
            "required-positive-total",
            "formal critical branch",
        ),
    )

    valid_validation, step1 = synthetic_freeze_validation()
    candidate = tool.threshold_candidate_data(valid_validation, step1)
    if candidate["critical_reviewed_thresholds"][0]["line"]["minimum"]["fraction"] != "4/5":
        raise RuntimeError("freeze candidate did not retain the exact line fraction")
    cases["freeze-exact-fraction"] = {
        "expected_code": "4/5",
        "observed_code": "4/5",
        "status": "passed",
    }
    for case_name, field, replacement in (
        ("freeze-critical-wrong-fqcn", "fqcn", "example.OtherCritical"),
        ("freeze-critical-wrong-module", "module", "other-module"),
        ("freeze-critical-wrong-artifact", "artifact_id", "other-artifact"),
        (
            "freeze-critical-wrong-candidate-outcome",
            "candidate_floor_outcome",
            "below-floor",
        ),
    ):
        mutated_validation = copy.deepcopy(valid_validation)
        mutated_validation["observation"]["critical_classes"][0][field] = replacement
        expect_failure(
            cases,
            case_name,
            "E_FREEZE_CRITICAL",
            lambda mutated_validation=mutated_validation: tool.threshold_candidate_data(
                mutated_validation, step1
            ),
        )
    extra_row_field = copy.deepcopy(valid_validation)
    extra_row_field["observation"]["critical_classes"][0]["unexpected"] = True
    expect_failure(
        cases,
        "freeze-critical-extra-row-field",
        "E_FREEZE_CRITICAL",
        lambda: tool.threshold_candidate_data(extra_row_field, step1),
    )
    wrong_metric_key = copy.deepcopy(valid_validation)
    wrong_metric_row = wrong_metric_key["observation"]["critical_classes"][0]
    wrong_metric_row["method"] = wrong_metric_row.pop("line")
    expect_failure(
        cases,
        "freeze-critical-wrong-metric-key",
        "E_FREEZE_CRITICAL",
        lambda: tool.threshold_candidate_data(wrong_metric_key, step1),
    )
    gap_boolean = copy.deepcopy(valid_validation)
    gap_boolean["observation"]["critical_classes"][0]["line"]["gap"] = False
    expect_failure(
        cases,
        "freeze-enriched-gap-boolean",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(gap_boolean, step1),
    )
    gap_non_finite = copy.deepcopy(valid_validation)
    gap_non_finite["observation"]["critical_classes"][0]["line"]["gap"] = float("inf")
    expect_failure(
        cases,
        "freeze-enriched-gap-non-finite",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(gap_non_finite, step1),
    )
    gap_mismatch = copy.deepcopy(valid_validation)
    gap_mismatch["observation"]["critical_classes"][0]["line"]["gap"] = 0.1
    expect_failure(
        cases,
        "freeze-enriched-gap-mismatch",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(gap_mismatch, step1),
    )
    ratio_integer_validation, ratio_integer_step1 = synthetic_freeze_validation(
        line=(5, 5)
    )
    ratio_integer_validation["observation"]["critical_classes"][0]["line"][
        "ratio"
    ] = 1
    expect_failure(
        cases,
        "freeze-enriched-ratio-integer-alias",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(
            ratio_integer_validation, ratio_integer_step1
        ),
    )
    gap_integer = copy.deepcopy(valid_validation)
    gap_integer["observation"]["critical_classes"][0]["line"]["gap"] = 0
    expect_failure(
        cases,
        "freeze-enriched-gap-integer-alias",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(gap_integer, step1),
    )
    floor_integer_alias = critical_observation_counter(5, 5, 1.0)
    floor_integer_alias["floor"] = 1
    expect_failure(
        cases,
        "freeze-enriched-floor-integer-alias",
        "E_FREEZE_COUNTER",
        lambda: tool.observation_critical_counter(
            floor_integer_alias,
            "example.Critical",
            "example-module",
            "line",
            Fraction(1, 1),
            "E_FREEZE_COUNTER",
            "E_FREEZE_ZERO_COUNTER",
            "integer-alias critical line",
        ),
    )
    enriched_extra = copy.deepcopy(valid_validation)
    enriched_extra["observation"]["critical_classes"][0]["line"][
        "applicability"
    ] = "required-positive-total"
    expect_failure(
        cases,
        "freeze-enriched-extra-field",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(enriched_extra, step1),
    )
    enriched_floor = copy.deepcopy(valid_validation)
    enriched_floor["observation"]["critical_classes"][0]["line"]["floor"] = 0.7
    expect_failure(
        cases,
        "freeze-enriched-floor-mutation",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(enriched_floor, step1),
    )
    enriched_outcome = copy.deepcopy(valid_validation)
    enriched_outcome["observation"]["critical_classes"][0]["line"][
        "outcome"
    ] = "below-floor"
    expect_failure(
        cases,
        "freeze-enriched-outcome-mutation",
        "E_FREEZE_COUNTER",
        lambda: tool.threshold_candidate_data(enriched_outcome, step1),
    )
    low_validation, low_step1 = synthetic_freeze_validation(branch=(6, 10))
    expect_failure(
        cases,
        "freeze-below-floor",
        "E_FREEZE_FLOOR",
        lambda: tool.threshold_candidate_data(low_validation, low_step1),
    )
    zero_validation, zero_step1 = synthetic_freeze_validation(branch=(0, 0))
    expect_failure(
        cases,
        "freeze-zero-counter",
        "E_FREEZE_ZERO_COUNTER",
        lambda: tool.threshold_candidate_data(zero_validation, zero_step1),
    )
    namespace_validation, namespace_step1 = synthetic_freeze_validation(
        branch=(0, 0),
        fqcn="com.foggyframework.dataset.db.model.spi.NamespaceScope",
        module="foggy-dataset-model",
    )
    namespace_candidate = tool.threshold_candidate_data(
        namespace_validation,
        namespace_step1,
    )
    namespace_branch = namespace_candidate["critical_reviewed_thresholds"][0][
        "branch"
    ]
    if namespace_branch != {
        "applicability": "not-applicable-zero-total-only",
        "observed": {"covered": 0, "total": 0, "fraction": None},
        "minimum": None,
    }:
        raise RuntimeError("freeze candidate did not retain structural branch N/A")
    tool.validate_reviewed_critical_metric(
        namespace_branch,
        "com.foggyframework.dataset.db.model.spi.NamespaceScope",
        "foggy-dataset-model",
        "branch",
        7,
        10,
        "E_THRESHOLD",
        "confirmed namespace branch",
    )
    record_positive(cases, "freeze-structural-branch-not-applicable")
    namespace_observation = namespace_validation["observation"]["critical_classes"][
        0
    ]["branch"]
    namespace_formal = tool.formal_critical_metric_result(
        namespace_observation,
        namespace_branch,
        "com.foggyframework.dataset.db.model.spi.NamespaceScope",
        "foggy-dataset-model",
        "branch",
        7,
        10,
        Fraction(7, 10),
        "formal namespace branch",
    )
    if namespace_formal["applicability"] != "not-applicable-zero-total-only":
        raise RuntimeError("formal helper did not retain structural branch N/A")
    record_positive(cases, "formal-structural-branch-not-applicable")

    for case_name, field, replacement in (
        ("freeze-not-applicable-covered-boolean", "covered", False),
        ("freeze-not-applicable-covered-float-zero", "covered", 0.0),
        ("freeze-not-applicable-total-boolean", "total", False),
        ("freeze-not-applicable-total-float-zero", "total", 0.0),
    ):
        mutated_reviewed = copy.deepcopy(namespace_branch)
        mutated_reviewed["observed"][field] = replacement
        expect_failure(
            cases,
            case_name,
            "E_THRESHOLD",
            lambda mutated_reviewed=mutated_reviewed: tool.validate_reviewed_critical_metric(
                mutated_reviewed,
                "com.foggyframework.dataset.db.model.spi.NamespaceScope",
                "foggy-dataset-model",
                "branch",
                7,
                10,
                "E_THRESHOLD",
                "typed namespace branch",
            ),
        )
    for case_name, field, replacement in (
        ("freeze-observation-not-applicable-covered-boolean", "covered", False),
        ("freeze-observation-not-applicable-covered-float-zero", "covered", 0.0),
        ("freeze-observation-not-applicable-total-boolean", "total", False),
        ("freeze-observation-not-applicable-total-float-zero", "total", 0.0),
    ):
        mutated_observation = copy.deepcopy(namespace_validation)
        mutated_observation["observation"]["critical_classes"][0]["branch"][
            field
        ] = replacement
        expect_failure(
            cases,
            case_name,
            "E_FREEZE_COUNTER",
            lambda mutated_observation=mutated_observation: tool.threshold_candidate_data(
                mutated_observation, namespace_step1
            ),
        )

    for case_name, fqcn, module, metric in (
        (
            "freeze-not-applicable-wrong-fqcn",
            "com.foggyframework.dataset.db.model.spi.OtherScope",
            "foggy-dataset-model",
            "branch",
        ),
        (
            "freeze-not-applicable-wrong-module",
            "com.foggyframework.dataset.db.model.spi.NamespaceScope",
            "other-module",
            "branch",
        ),
        (
            "freeze-not-applicable-wrong-metric",
            "com.foggyframework.dataset.db.model.spi.NamespaceScope",
            "foggy-dataset-model",
            "line",
        ),
    ):
        expect_failure(
            cases,
            case_name,
            "E_THRESHOLD",
            lambda fqcn=fqcn, module=module, metric=metric: tool.validate_reviewed_critical_metric(
                namespace_branch,
                fqcn,
                module,
                metric,
                7,
                10,
                "E_THRESHOLD",
                "misidentified namespace branch",
            ),
        )

    required_branch_counter = tool.exact_counter(
        7, 10, "E_NEGATIVE_FIXTURE", "required namespace branch"
    )
    required_branch = {
        "applicability": "required-positive-total",
        "observed": required_branch_counter,
        "minimum": required_branch_counter,
    }
    expect_failure(
        cases,
        "formal-full-helper-required-to-not-applicable-drift",
        "E_FORMAL_APPLICABILITY_DRIFT",
        lambda: tool.formal_critical_metric_result(
            critical_observation_counter(7, 10, 0.7),
            namespace_branch,
            "com.foggyframework.dataset.db.model.spi.NamespaceScope",
            "foggy-dataset-model",
            "branch",
            7,
            10,
            Fraction(7, 10),
            "formal namespace branch",
        ),
    )
    expect_failure(
        cases,
        "formal-full-helper-not-applicable-to-required-drift",
        "E_FORMAL_APPLICABILITY_DRIFT",
        lambda: tool.formal_critical_metric_result(
            namespace_observation,
            required_branch,
            "com.foggyframework.dataset.db.model.spi.NamespaceScope",
            "foggy-dataset-model",
            "branch",
            7,
            10,
            Fraction(7, 10),
            "formal namespace branch",
        ),
    )
    unapproved_not_applicable = copy.deepcopy(namespace_branch)
    expect_failure(
        cases,
        "freeze-unapproved-not-applicable",
        "E_THRESHOLD",
        lambda: tool.validate_reviewed_critical_metric(
            unapproved_not_applicable,
            "example.Critical",
            "example-module",
            "branch",
            7,
            10,
            "E_THRESHOLD",
            "unapproved branch",
        ),
    )
    nonzero_not_applicable = copy.deepcopy(namespace_branch)
    nonzero_not_applicable["observed"] = {
        "covered": 1,
        "total": 1,
        "fraction": "1/1",
    }
    expect_failure(
        cases,
        "freeze-not-applicable-nonzero",
        "E_THRESHOLD",
        lambda: tool.validate_reviewed_critical_metric(
            nonzero_not_applicable,
            "com.foggyframework.dataset.db.model.spi.NamespaceScope",
            "foggy-dataset-model",
            "branch",
            7,
            10,
            "E_THRESHOLD",
            "nonzero namespace branch",
        ),
    )
    minimum_not_applicable = copy.deepcopy(namespace_branch)
    minimum_not_applicable["minimum"] = {
        "covered": 0,
        "total": 0,
        "fraction": None,
    }
    expect_failure(
        cases,
        "freeze-not-applicable-minimum",
        "E_THRESHOLD",
        lambda: tool.validate_reviewed_critical_metric(
            minimum_not_applicable,
            "com.foggyframework.dataset.db.model.spi.NamespaceScope",
            "foggy-dataset-model",
            "branch",
            7,
            10,
            "E_THRESHOLD",
            "namespace branch minimum",
        ),
    )

    confirmed_projection = {
        "aggregate_observed": copy.deepcopy(candidate["aggregate_observed"]),
        "aggregate_reviewed_thresholds": copy.deepcopy(
            candidate["aggregate_reviewed_thresholds"]
        ),
        "critical_reviewed_thresholds": copy.deepcopy(
            candidate["critical_reviewed_thresholds"]
        ),
    }
    tool.validate_frozen_candidate_equivalence(confirmed_projection, candidate)
    record_positive(cases, "frozen-candidate-equivalence-positive")

    frozen_evidence_tamper = copy.deepcopy(candidate)
    frozen_evidence_tamper["aggregate_observed"]["evidence"]["summary_sha256"] = "f" * 64
    expect_failure(
        cases,
        "frozen-evidence-tamper",
        "E_FROZEN_EVIDENCE",
        lambda: tool.validate_frozen_candidate_equivalence(
            confirmed_projection, frozen_evidence_tamper
        ),
    )
    frozen_aggregate_tamper = copy.deepcopy(candidate)
    frozen_aggregate_tamper["aggregate_observed"]["line"]["covered"] -= 1
    expect_failure(
        cases,
        "frozen-aggregate-tamper",
        "E_FROZEN_RECOMPUTE",
        lambda: tool.validate_frozen_candidate_equivalence(
            confirmed_projection, frozen_aggregate_tamper
        ),
    )
    frozen_reviewed_tamper = copy.deepcopy(candidate)
    frozen_reviewed_tamper["aggregate_reviewed_thresholds"]["line"]["covered"] -= 1
    expect_failure(
        cases,
        "frozen-reviewed-tamper",
        "E_FROZEN_RECOMPUTE",
        lambda: tool.validate_frozen_candidate_equivalence(
            confirmed_projection, frozen_reviewed_tamper
        ),
    )
    frozen_critical_tamper = copy.deepcopy(candidate)
    frozen_critical_tamper["critical_reviewed_thresholds"][0]["line"]["minimum"][
        "covered"
    ] -= 1
    expect_failure(
        cases,
        "frozen-critical-tamper",
        "E_FROZEN_RECOMPUTE",
        lambda: tool.validate_frozen_candidate_equivalence(
            confirmed_projection, frozen_critical_tamper
        ),
    )
    frozen_critical_type_alias = copy.deepcopy(candidate)
    frozen_critical_type_alias["critical_reviewed_thresholds"][0]["line"][
        "observed"
    ]["covered"] = 4.0
    frozen_critical_type_alias["critical_reviewed_thresholds"][0]["line"][
        "minimum"
    ]["covered"] = 4.0
    expect_failure(
        cases,
        "frozen-critical-numeric-type-alias",
        "E_FROZEN_RECOMPUTE",
        lambda: tool.validate_frozen_candidate_equivalence(
            confirmed_projection, frozen_critical_type_alias
        ),
    )

    current_head = tool.git_current_head(repo_root)
    tool.require_git_ancestor(repo_root, current_head, current_head)
    frozen_threshold_blob = tool.git_show_blob(
        repo_root,
        current_head,
        "scripts/v934/step4/coverage-thresholds.json",
    )
    frozen_threshold_sha = hashlib.sha256(frozen_threshold_blob).hexdigest()
    if (
        tool.require_frozen_blob_hash(
            frozen_threshold_blob,
            frozen_threshold_sha,
            "negative fixture threshold",
        )
        != frozen_threshold_sha
    ):
        raise RuntimeError("frozen blob positive control returned the wrong SHA")
    record_positive(cases, "frozen-git-helpers-positive")
    expect_failure(
        cases,
        "frozen-blob-sha-tamper",
        "E_FROZEN_BLOB",
        lambda: tool.require_frozen_blob_hash(
            frozen_threshold_blob,
            "0" * 64 if frozen_threshold_sha != "0" * 64 else "1" * 64,
            "negative fixture threshold",
        ),
    )
    expect_failure(
        cases,
        "frozen-git-path-not-allowlisted",
        "E_FROZEN_GIT",
        lambda: tool.git_show_blob(repo_root, current_head, "README.md"),
    )
    expect_failure(
        cases,
        "frozen-git-identity-invalid",
        "E_FROZEN_GIT",
        lambda: tool.require_git_ancestor(repo_root, "g" * 40, current_head),
    )

    hostile_git_environment = {
        "GIT_ALTERNATE_OBJECT_DIRECTORIES": "/v934/missing/objects",
        "GIT_COMMON_DIR": "/v934/missing/common",
        "GIT_CONFIG_COUNT": "1",
        "GIT_CONFIG_GLOBAL": "/v934/missing/config-global",
        "GIT_CONFIG_KEY_0": "core.repositoryformatversion",
        "GIT_CONFIG_NOSYSTEM": "0",
        "GIT_CONFIG_SYSTEM": "/v934/missing/config-system",
        "GIT_CONFIG_VALUE_0": "999",
        "GIT_DIR": "/v934/missing/git-dir",
        "GIT_EXEC_PATH": "/v934/missing/exec-path",
        "GIT_GRAFT_FILE": "/v934/missing/grafts",
        "GIT_INDEX_FILE": "/v934/missing/index",
        "GIT_NAMESPACE": "v934-hostile",
        "GIT_OBJECT_DIRECTORY": "/v934/missing/object-directory",
        "GIT_REPLACE_REF_BASE": "refs/v934-hostile",
        "GIT_SHALLOW_FILE": "/v934/missing/shallow",
        "GIT_WORK_TREE": "/v934/missing/worktree",
    }

    def frozen_git_hostile_environment_control() -> None:
        observed_head = tool.git_current_head(repo_root)
        if observed_head != current_head:
            raise RuntimeError("hostile Git environment changed frozen HEAD")
        tool.require_git_ancestor(repo_root, current_head, observed_head)
        observed_blob = tool.git_show_blob(
            repo_root,
            observed_head,
            "scripts/v934/step4/coverage-thresholds.json",
        )
        if hashlib.sha256(observed_blob).hexdigest() != frozen_threshold_sha:
            raise RuntimeError("hostile Git environment changed frozen blob")

    with_git_overrides(
        hostile_git_environment,
        frozen_git_hostile_environment_control,
    )
    record_positive(cases, "frozen-git-ambient-overrides-denied")

    with tempfile.TemporaryDirectory(
        prefix="v934-step4-frozen-git-environment-"
    ) as git_temporary_name:
        git_temporary = Path(git_temporary_name)
        missing = git_temporary / "must-not-exist"

        origin = git_temporary / "origin"
        initialize_git_repository(origin, (b"first\n", b"second\n"))
        shallow = git_temporary / "real-shallow"
        fixture_git(
            git_temporary,
            [
                "clone",
                "-q",
                "--depth=1",
                "--no-local",
                origin.as_uri(),
                str(shallow),
            ],
        )
        if fixture_git(
            shallow, ["rev-parse", "--is-shallow-repository"]
        ) != "true\n":
            raise RuntimeError("frozen Git shallow fixture is not shallow")
        expect_failure(
            cases,
            "frozen-git-real-shallow-override",
            "E_FROZEN_GIT",
            lambda: with_git_overrides(
                {"GIT_SHALLOW_FILE": str(missing)},
                lambda: tool.git_current_head(shallow.resolve()),
            ),
        )

        grafted = git_temporary / "real-graft"
        graft_head = initialize_git_repository(grafted, (b"grafted\n",))[0]
        graft_common = Path(
            fixture_git(
                grafted,
                ["rev-parse", "--path-format=absolute", "--git-common-dir"],
            ).strip()
        )
        graft_info = graft_common / "info"
        graft_info.mkdir(exist_ok=True)
        (graft_info / "grafts").write_text(
            f"{graft_head}\n", encoding="ascii"
        )
        expect_failure(
            cases,
            "frozen-git-real-graft-override",
            "E_FROZEN_GIT",
            lambda: with_git_overrides(
                {"GIT_GRAFT_FILE": str(missing)},
                lambda: tool.git_current_head(grafted.resolve()),
            ),
        )

        replaced = git_temporary / "real-replace"
        replace_heads = initialize_git_repository(
            replaced, (b"replace-old\n", b"replace-new\n")
        )
        fixture_git(
            replaced,
            ["update-ref", f"refs/replace/{replace_heads[0]}", replace_heads[1]],
        )
        expect_failure(
            cases,
            "frozen-git-real-replace-override",
            "E_FROZEN_GIT",
            lambda: with_git_overrides(
                {"GIT_REPLACE_REF_BASE": "refs/v934-hidden-replace"},
                lambda: tool.git_current_head(replaced.resolve()),
            ),
        )

    contract = tool.load_json(
        repo_root / "scripts/v934/step4/coverage-contract.json",
        "E_NEGATIVE_FIXTURE",
    )
    formal_policy = contract["threshold_successor"]["formalization_delta"]
    valid_formal_changes = sorted(
        [*tool.FORMALIZATION_EXACT_PATHS, "docs/9.3.4/formal-review.md"]
    )
    tool.validate_formal_delta_policy(formal_policy, valid_formal_changes)
    record_positive(cases, "formal-delta-policy-positive")

    summary_values = {field: "fixture" for field in tool.RELEASE_SUMMARY_FIELDS}
    summary_values.update(
        {
            "mode": "release",
            "release_successor": tool.RELEASE_SUCCESSOR_MARKER,
            "status": "release-candidate-ready",
        }
    )
    release_summary_payload = tool.encode_env(
        summary_values,
        tool.RELEASE_SUMMARY_FIELDS,
        "E_RUN_SUMMARY",
    )
    tool.parse_env_bytes(
        release_summary_payload,
        tool.RELEASE_SUMMARY_FIELDS,
        "E_RUN_SUMMARY",
        "release summary positive",
    )
    record_positive(cases, "release-summary-schema-positive")
    release_as_formal = release_summary_payload.replace(
        b"mode=release\n", b"mode=formal\n", 1
    ).replace(
        b"status=release-candidate-ready\n",
        b"status=formal-candidate-ready\n",
        1,
    )
    expect_failure(
        cases,
        "release-summary-cannot-masquerade-as-formal",
        "E_RUN_SUMMARY",
        lambda: tool.parse_env_bytes(
            release_as_formal,
            tool.FORMAL_SUMMARY_FIELDS,
            "E_RUN_SUMMARY",
            "release-as-formal summary",
        ),
    )
    formal_without_delta_values = {
        field: "fixture" for field in tool.DIAGNOSTIC_SUMMARY_FIELDS
    }
    formal_without_delta_values.update(
        {"mode": "formal", "status": "formal-candidate-ready"}
    )
    formal_without_delta = tool.encode_env(
        formal_without_delta_values,
        tool.DIAGNOSTIC_SUMMARY_FIELDS,
        "E_RUN_SUMMARY",
    )
    expect_failure(
        cases,
        "formal-summary-still-requires-formalization-delta",
        "E_RUN_SUMMARY",
        lambda: tool.parse_env_bytes(
            formal_without_delta,
            tool.FORMAL_SUMMARY_FIELDS,
            "E_RUN_SUMMARY",
            "formal summary without delta",
        ),
    )
    expect_failure(
        cases,
        "formal-delta-forbidden-path",
        "E_FORMAL_DELTA_FORBIDDEN",
        lambda: tool.validate_formal_delta_policy(
            formal_policy,
            sorted([*valid_formal_changes, "scripts/unreviewed.py"]),
        ),
    )
    expect_failure(
        cases,
        "formal-delta-missing-required",
        "E_FORMAL_DELTA_MISSING",
        lambda: tool.validate_formal_delta_policy(
            formal_policy,
            sorted(
                [
                    *tool.FORMALIZATION_EXACT_PATHS[:-1],
                    "docs/9.3.4/formal-review.md",
                ]
            ),
        ),
    )
    formal_policy_tamper = copy.deepcopy(formal_policy)
    formal_policy_tamper["repository_identity"]["commit_relation"] = "ancestor"
    expect_failure(
        cases,
        "formal-delta-identity-policy-tamper",
        "E_FORMAL_DELTA_POLICY",
        lambda: tool.validate_formal_delta_policy(
            formal_policy_tamper, valid_formal_changes
        ),
    )

    # These probes must remain meaningful after the canonical successor moves
    # to confirmed.  Isolate a pending threshold view so the fast negative
    # suite proves the formal entry points reject on state before consulting
    # any run-owned or attacker-controlled artifact path.
    original_load_thresholds = tool.load_thresholds
    tool.load_thresholds = lambda _repo_root, *args, **kwargs: (
        {},
        {"status": "diagnostic-pending"},
        {},
    )
    try:
        expect_failure(
            cases,
            "formal-pending-gate",
            "E_FORMAL_THRESHOLD_STATUS",
            lambda: tool.formal_check_data(repo_root, "negative-formal"),
        )
        expect_failure(
            cases,
            "frozen-pending-successor",
            "E_FROZEN_THRESHOLD_STATUS",
            lambda: tool.validate_frozen_diagnostic_data(repo_root),
        )
        for stage in ("candidate", "final"):
            namespace = SimpleNamespace(
                repo_root=repo_root,
                stage=stage,
                run_id="negative-formal" if stage == "candidate" else None,
                coverage_gate=repo_root / "missing-gate.json" if stage == "candidate" else None,
                candidate=repo_root / "missing-candidate.json" if stage == "final" else None,
                output=repo_root / "must-not-exist.json",
            )
            expect_failure(
                cases,
                f"formal-pending-build-{stage}",
                "E_FORMAL_THRESHOLD_STATUS",
                lambda namespace=namespace: tool.build_artifact_command(namespace),
            )
    finally:
        tool.load_thresholds = original_load_thresholds

    target = repo_root / "target"
    target.mkdir(exist_ok=True)

    threshold_alias_root = Path(
        tempfile.mkdtemp(prefix="v934-threshold-schema-negative-", dir=target)
    )
    try:
        threshold_alias_path = threshold_alias_root / "coverage-thresholds.json"
        threshold_alias = tool.load_json(
            repo_root / "scripts/v934/step4/coverage-thresholds.json",
            "E_NEGATIVE_FIXTURE",
        )
        threshold_alias["schema_version"] = True
        write_json(threshold_alias_path, threshold_alias)
        expect_failure(
            cases,
            "threshold-schema-version-boolean-alias",
            "E_THRESHOLD",
            lambda: tool.load_thresholds(
                repo_root,
                _step4_path_override=threshold_alias_path,
            ),
        )
    finally:
        shutil.rmtree(threshold_alias_root)

    runs_root = target / "v934-step4-coverage/runs"
    runs_root.mkdir(parents=True, exist_ok=True)
    provenance_root = Path(
        tempfile.mkdtemp(prefix="source-context-negative-", dir=runs_root)
    )
    try:
        provenance_run_id = provenance_root.name
        source_payload = (
            tool.SOURCE_INVENTORY_HEADER
            + b"100644\tsynthetic.txt\t"
            + b"1" * 64
            + b"\t1\n"
        )
        source_sha = hashlib.sha256(source_payload).hexdigest()
        for name in ("source-before.tsv", "source-after.tsv"):
            source_path = provenance_root / name
            source_path.write_bytes(source_payload)
            source_path.chmod(0o644)
        context_value = {
            "schema_version": 1,
            "kind": "v934-step4-run-context",
            "authority_kind": "step4-coverage",
            "run_id": provenance_run_id,
            "git_head": "a" * 40,
            "contract_sha256": tool.sha256_file(
                repo_root / "scripts/v934/step4/coverage-contract.json",
                "E_NEGATIVE_FIXTURE",
            ),
            "source_sha256": source_sha,
            "not_before_ns": 1,
            "started_at": "2026-07-16T00:00:00Z",
        }
        context_path = provenance_root / "run-context.json"
        write_json(context_path, context_value)
        context_path.chmod(0o644)
        validated_context, context_sha = tool.validate_run_context(
            repo_root,
            context_path,
            provenance_run_id,
            context_value["contract_sha256"],
        )
        source_receipt = tool.validate_run_source_seals(
            provenance_root,
            validated_context,
            context_sha,
            require_after=True,
        )
        if (
            source_receipt["status"] != "exact-before-after-context-bound"
            or source_receipt["source_sha256"] != source_sha
        ):
            raise RuntimeError("source/context seal positive control differs")
        record_positive(cases, "source-context-seal-positive")

        mismatched_context = copy.deepcopy(context_value)
        mismatched_context["source_sha256"] = (
            "f" * 64 if source_sha != "f" * 64 else "e" * 64
        )
        write_json(context_path, mismatched_context)
        context_path.chmod(0o644)
        mismatched_context, mismatched_context_sha = tool.validate_run_context(
            repo_root,
            context_path,
            provenance_run_id,
            context_value["contract_sha256"],
        )
        expect_failure(
            cases,
            "source-tsv-equal-context-source-mismatch",
            "E_RUN_SOURCE",
            lambda: tool.validate_run_source_seals(
                provenance_root,
                mismatched_context,
                mismatched_context_sha,
                require_after=True,
            ),
        )
        write_json(context_path, context_value)
        context_path.chmod(0o644)
        validated_context, context_sha = tool.validate_run_context(
            repo_root,
            context_path,
            provenance_run_id,
            context_value["contract_sha256"],
        )

        manifest_binding = {
            "run_context_sha256": context_sha,
            "git_head": validated_context["git_head"],
            "source_sha256": validated_context["source_sha256"],
            "not_before_ns": validated_context["not_before_ns"],
        }
        tool.validate_manifest_context_binding(
            manifest_binding,
            validated_context,
            context_sha,
        )
        tool.require_expected_run_git_head(
            validated_context,
            validated_context["git_head"],
            "E_NEGATIVE_FIXTURE",
            "source/context fixture",
        )
        record_positive(cases, "manifest-context-binding-positive")
        for case_name, field, replacement in (
            ("manifest-run-context-sha-mismatch", "run_context_sha256", "b" * 64),
            ("manifest-source-sha-mismatch", "source_sha256", "c" * 64),
            ("manifest-not-before-mismatch", "not_before_ns", 2),
            ("manifest-git-head-mismatch", "git_head", "d" * 40),
        ):
            tampered_binding = copy.deepcopy(manifest_binding)
            if tampered_binding[field] == replacement:
                replacement = "e" * len(replacement) if isinstance(replacement, str) else 3
            tampered_binding[field] = replacement
            expect_failure(
                cases,
                case_name,
                "E_MANIFEST_PROVENANCE",
                lambda tampered_binding=tampered_binding: tool.validate_manifest_context_binding(
                    tampered_binding,
                    validated_context,
                    context_sha,
                ),
            )
        expect_failure(
            cases,
            "run-context-expected-git-head-splice",
            "E_RUN_CONTEXT",
            lambda: tool.require_expected_run_git_head(
                validated_context,
                "e" * 40
                if validated_context["git_head"] != "e" * 40
                else "f" * 40,
                "E_RUN_CONTEXT",
                "source/context fixture",
            ),
        )

        raw_root = provenance_root / "exec"
        raw_root.mkdir()
        raw_names = ["jacoco-ut.exec"] + [
            f"jacoco-it-fixture-{number:02d}.exec" for number in range(1, 23)
        ]
        raw_rows: list[dict[str, Any]] = []
        raw_payloads: dict[str, bytes] = {}
        for number, name in enumerate(raw_names, 1):
            payload = f"raw-exec-{number:02d}\n".encode("ascii")
            path = raw_root / name
            path.write_bytes(payload)
            path.chmod(0o644)
            raw_payloads[name] = payload
            file_stat = path.stat()
            raw_rows.append(
                {
                    "exec_file": name,
                    "sha256": hashlib.sha256(payload).hexdigest(),
                    "size": len(payload),
                    "mtime_ns": file_stat.st_mtime_ns,
                }
            )
        raw_receipt = tool.validate_raw_exec_replay(
            provenance_root,
            raw_rows,
            1,
        )
        if (
            raw_receipt["mode"] != "exact-retained-raw-exec-byte-replay"
            or raw_receipt["exec_count"] != 23
        ):
            raise RuntimeError("raw exec replay positive control differs")
        record_positive(cases, "raw-exec-byte-replay-positive")

        first_row = raw_rows[0]
        first_path = raw_root / first_row["exec_file"]
        first_path.write_bytes(b"X" * first_row["size"])
        first_path.chmod(0o644)
        os.utime(
            first_path,
            ns=(first_row["mtime_ns"], first_row["mtime_ns"]),
        )
        expect_failure(
            cases,
            "raw-exec-byte-tamper",
            "E_RAW_EXEC_REPLAY",
            lambda: tool.validate_raw_exec_replay(provenance_root, raw_rows, 1),
        )
        first_path.write_bytes(raw_payloads[first_row["exec_file"]])
        first_path.chmod(0o644)
        os.utime(
            first_path,
            ns=(first_row["mtime_ns"], first_row["mtime_ns"]),
        )

        os.utime(
            first_path,
            ns=(first_row["mtime_ns"] + 1, first_row["mtime_ns"] + 1),
        )
        expect_failure(
            cases,
            "raw-exec-mtime-tamper",
            "E_RAW_EXEC_REPLAY",
            lambda: tool.validate_raw_exec_replay(provenance_root, raw_rows, 1),
        )
        os.utime(
            first_path,
            ns=(first_row["mtime_ns"], first_row["mtime_ns"]),
        )

        last_row = raw_rows[-1]
        last_path = raw_root / last_row["exec_file"]
        last_path.unlink()
        expect_failure(
            cases,
            "raw-exec-missing",
            "E_RAW_EXEC_REPLAY",
            lambda: tool.validate_raw_exec_replay(provenance_root, raw_rows, 1),
        )
        last_path.write_bytes(raw_payloads[last_row["exec_file"]])
        last_path.chmod(0o644)
        os.utime(last_path, ns=(last_row["mtime_ns"], last_row["mtime_ns"]))

        extra_path = raw_root / "unexpected.exec"
        extra_path.write_bytes(b"unexpected\n")
        extra_path.chmod(0o644)
        expect_failure(
            cases,
            "raw-exec-extra",
            "E_RAW_EXEC_REPLAY",
            lambda: tool.validate_raw_exec_replay(provenance_root, raw_rows, 1),
        )
    finally:
        shutil.rmtree(provenance_root)

    atomic_root = Path(tempfile.mkdtemp(prefix="v934-coverage-xml-atomic-", dir=target))
    try:
        atomic_path = atomic_root / "atomic-result.json"
        atomic_value = {"kind": "atomic-positive", "status": "passed"}
        tool.atomic_json(atomic_path, atomic_value)
        if tool.load_json(atomic_path, "E_NEGATIVE_FIXTURE") != atomic_value:
            raise RuntimeError("atomic publication positive control differs")
        record_positive(cases, "atomic-publication-positive")
        expect_failure(
            cases,
            "atomic-no-clobber",
            "E_OUTPUT_EXISTS",
            lambda: tool.atomic_json(atomic_path, atomic_value),
        )
        expect_failure(
            cases,
            "atomic-relative-path",
            "E_OUTPUT_PATH",
            lambda: tool.atomic_json(Path("relative-output.json"), atomic_value),
        )
        real_parent = atomic_root / "real-parent"
        nested_parent = real_parent / "nested"
        nested_parent.mkdir(parents=True)
        linked_parent = atomic_root / "linked-parent"
        linked_parent.symlink_to(real_parent, target_is_directory=True)
        expect_failure(
            cases,
            "atomic-symlinked-ancestor",
            "E_OUTPUT_DIR",
            lambda: tool.atomic_json(
                linked_parent / "nested" / "symlink-output.json", atomic_value
            ),
        )

        final_status_path = atomic_root / "run-status.env"
        final_status_payload = b"status=formal-passed\n"
        tool.final_publish_bytes(final_status_path, final_status_payload)
        if final_status_path.read_bytes() != final_status_payload:
            raise RuntimeError("final publication positive control differs")
        record_positive(cases, "final-publication-positive")
        expect_failure(
            cases,
            "final-publication-no-clobber",
            "E_FINAL_OUTPUT_EXISTS",
            lambda: tool.final_publish_bytes(
                final_status_path,
                b"status=forged\n",
            ),
        )
    finally:
        shutil.rmtree(atomic_root)

    lifecycle_base = Path(
        tempfile.mkdtemp(prefix="v934-coverage-xml-lifecycle-", dir=target)
    )
    lifecycle_run_id = "synthetic-child-lifecycle"
    try:
        lifecycle_positive = lifecycle_base / "positive"
        fixture = build_child_lifecycle_fixture(lifecycle_positive, lifecycle_run_id)
        tool.validate_child_lifecycle(lifecycle_positive, lifecycle_run_id)
        record_positive(cases, "child-lifecycle-positive")

        ready_tamper_root = lifecycle_base / "ready-tamper"
        shutil.copytree(lifecycle_positive, ready_tamper_root)
        ready_tamper = copy.deepcopy(fixture["ready"]["unit"])
        ready_tamper["pid"] = str(ready_tamper["pid"])
        write_json(ready_tamper_root / "child-ready/unit.json", ready_tamper)
        expect_failure(
            cases,
            "child-ready-typed-tamper",
            "E_CHILD_LIFECYCLE",
            lambda: tool.validate_child_lifecycle(
                ready_tamper_root, lifecycle_run_id
            ),
        )

        residue_root = lifecycle_base / "completion-residue"
        shutil.copytree(lifecycle_positive, residue_root)
        residue_completion = copy.deepcopy(fixture["completion"]["integration"])
        residue_completion["process_group_residue"] = "1"
        residue_path = residue_root / "child-lifecycle/integration-complete.env"
        residue_path.write_bytes(
            tool.encode_env(
                residue_completion,
                tool.CHILD_COMPLETION_FIELDS,
                "E_NEGATIVE_FIXTURE",
            )
        )
        expect_failure(
            cases,
            "child-completion-residue",
            "E_CHILD_LIFECYCLE",
            lambda: tool.validate_child_lifecycle(residue_root, lifecycle_run_id),
        )

        extra_residue_root = lifecycle_base / "extra-residue"
        shutil.copytree(lifecycle_positive, extra_residue_root)
        (extra_residue_root / "child-lifecycle/unit-residue.tsv").write_text(
            "pid\tpgid\tsid\n20001\t20001\t20001\n",
            encoding="utf-8",
        )
        expect_failure(
            cases,
            "child-lifecycle-extra-residue-file",
            "E_CHILD_LIFECYCLE",
            lambda: tool.validate_child_lifecycle(
                extra_residue_root, lifecycle_run_id
            ),
        )

        manifest_tamper_root = lifecycle_base / "manifest-tamper"
        shutil.copytree(lifecycle_positive, manifest_tamper_root)
        manifest_tamper = copy.deepcopy(fixture["manifest"])
        manifest_tamper["children"][0]["leader_reaped"] = 0
        write_json(manifest_tamper_root / "child-lifecycle.json", manifest_tamper)
        expect_failure(
            cases,
            "child-lifecycle-manifest-tamper",
            "E_CHILD_LIFECYCLE",
            lambda: tool.validate_child_lifecycle(
                manifest_tamper_root, lifecycle_run_id
            ),
        )
        for case_name, path, replacement in (
            (
                "child-lifecycle-schema-boolean-alias",
                ("schema_version",),
                True,
            ),
            (
                "child-lifecycle-count-float-alias",
                ("child_count",),
                float(fixture["manifest"]["child_count"]),
            ),
            (
                "child-lifecycle-nested-boolean-alias",
                ("children", 0, "leader_reaped"),
                True,
            ),
            (
                "child-lifecycle-nested-float-alias",
                ("children", 0, "leader_pid"),
                float(fixture["manifest"]["children"][0]["leader_pid"]),
            ),
        ):
            alias_root = lifecycle_base / case_name
            shutil.copytree(lifecycle_positive, alias_root)
            alias_manifest = copy.deepcopy(fixture["manifest"])
            alias_target: Any = alias_manifest
            for segment in path[:-1]:
                alias_target = alias_target[segment]
            alias_target[path[-1]] = replacement
            write_json(alias_root / "child-lifecycle.json", alias_manifest)
            expect_failure(
                cases,
                case_name,
                "E_CHILD_LIFECYCLE",
                lambda alias_root=alias_root: tool.validate_child_lifecycle(
                    alias_root, lifecycle_run_id
                ),
            )
    finally:
        shutil.rmtree(lifecycle_base)

    temporary_root = Path(tempfile.mkdtemp(prefix="formal-negative-", dir=runs_root))
    cross_run_root = Path(tempfile.mkdtemp(prefix="formal-cross-run-", dir=runs_root))
    original_formal_check = tool.formal_check_data
    try:
        threshold_path = temporary_root / "threshold.json"
        write_json(threshold_path, {"status": "confirmed"})
        run_id = temporary_root.name
        cross_run_id = cross_run_root.name
        fake_gate: dict[str, Any] = {
            "schema_version": 1,
            "kind": "v934-step4-coverage-gate",
            "status": "passed",
            "run_id": run_id,
            "git_head": "c" * 40,
            "threshold": tool.artifact_record(
                repo_root, threshold_path, "E_NEGATIVE_FIXTURE"
            ),
            "formal_evidence": {
                "schema_version": 1,
                "observation_sha256": "d" * 64,
            },
            "bindings": {"aggregate_xml": {"sha256": "e" * 64}},
        }

        def fake_formal_check(
            _repo_root: Path,
            run_id: str,
            mode: str = "formal",
        ) -> dict[str, Any]:
            if run_id != fake_gate["run_id"]:
                tool.reject("E_COVERAGE_GATE", "fake gate run mismatch")
            result = copy.deepcopy(fake_gate)
            if mode == "release":
                result["release_successor"] = tool.RELEASE_SUCCESSOR_MARKER
            return result

        tool.formal_check_data = fake_formal_check
        gate_path = temporary_root / "coverage-gate.json"
        write_json(gate_path, fake_gate)
        candidate_value = tool.acceptance_candidate_data(
            repo_root, fake_gate["run_id"], gate_path
        )
        candidate_path = temporary_root / "candidate-manifest.json"
        write_json(candidate_path, candidate_value)
        tool.validate_acceptance_candidate(repo_root, candidate_path)
        final_value = tool.acceptance_final_data(repo_root, candidate_path)
        final_path = temporary_root / "final-manifest.json"
        write_json(final_path, final_value)
        tool.validate_acceptance_final(repo_root, final_path)
        record_positive(cases, "canonical-gate-candidate-final-positive")

        release_gate = fake_formal_check(repo_root, run_id, "release")
        write_json(gate_path, release_gate)
        release_candidate = tool.acceptance_candidate_data(
            repo_root, run_id, gate_path, "release"
        )
        write_json(candidate_path, release_candidate)
        tool.validate_acceptance_candidate(repo_root, candidate_path, "release")
        release_final = tool.acceptance_final_data(
            repo_root, candidate_path, "release"
        )
        write_json(final_path, release_final)
        tool.validate_acceptance_final(repo_root, final_path, "release")
        record_positive(cases, "release-gate-candidate-final-positive")

        release_as_formal_candidate = copy.deepcopy(release_candidate)
        release_as_formal_candidate["status"] = "formal-candidate"
        release_as_formal_candidate.pop("release_successor")
        write_json(candidate_path, release_as_formal_candidate)
        expect_failure(
            cases,
            "release-candidate-cannot-masquerade-as-formal",
            "E_COVERAGE_GATE_MODE",
            lambda: tool.validate_acceptance_candidate(
                repo_root, candidate_path, "formal"
            ),
        )

        write_json(gate_path, fake_gate)
        write_json(candidate_path, candidate_value)
        write_json(final_path, final_value)

        gate_numeric_alias = copy.deepcopy(fake_gate)
        gate_numeric_alias["schema_version"] = True
        write_json(gate_path, gate_numeric_alias)
        expect_failure(
            cases,
            "coverage-gate-numeric-type-alias",
            "E_COVERAGE_GATE",
            lambda: tool.validate_coverage_gate(repo_root, gate_path),
        )
        write_json(gate_path, fake_gate)

        candidate_numeric_alias = copy.deepcopy(candidate_value)
        candidate_numeric_alias["evidence"]["schema_version"] = True
        write_json(candidate_path, candidate_numeric_alias)
        expect_failure(
            cases,
            "candidate-nested-numeric-type-alias",
            "E_ACCEPTANCE_CANDIDATE",
            lambda: tool.validate_acceptance_candidate(repo_root, candidate_path),
        )
        write_json(candidate_path, candidate_value)

        final_numeric_alias = copy.deepcopy(final_value)
        final_numeric_alias["evidence"]["schema_version"] = True
        write_json(final_path, final_numeric_alias)
        expect_failure(
            cases,
            "final-nested-numeric-type-alias",
            "E_ACCEPTANCE_FINAL",
            lambda: tool.validate_acceptance_final(repo_root, final_path),
        )
        write_json(final_path, final_value)

        original_load_thresholds_for_final = tool.load_thresholds
        tool.load_thresholds = lambda *_args, **_kwargs: (
            {},
            {"status": "confirmed"},
            {},
        )
        try:
            expect_failure(
                cases,
                "final-public-verify-requires-run-status",
                "E_RUN_STATUS",
                lambda: tool.verify_artifact_command(
                    SimpleNamespace(
                        repo_root=repo_root,
                        artifact=final_path,
                        run_status=None,
                    )
                ),
            )
        finally:
            tool.load_thresholds = original_load_thresholds_for_final

        alternate_gate_path = temporary_root / "alternate-coverage-gate.json"
        write_json(alternate_gate_path, fake_gate)
        expect_failure(
            cases,
            "coverage-gate-alternate-path",
            "E_COVERAGE_GATE_PATH",
            lambda: tool.validate_coverage_gate(repo_root, alternate_gate_path),
        )

        alternate_candidate_path = temporary_root / "alternate-candidate-manifest.json"
        write_json(alternate_candidate_path, candidate_value)
        expect_failure(
            cases,
            "candidate-alternate-path",
            "E_ACCEPTANCE_CANDIDATE_PATH",
            lambda: tool.validate_acceptance_candidate(
                repo_root, alternate_candidate_path
            ),
        )

        alternate_final_path = temporary_root / "alternate-final-manifest.json"
        write_json(alternate_final_path, final_value)
        expect_failure(
            cases,
            "final-alternate-path",
            "E_ACCEPTANCE_FINAL_PATH",
            lambda: tool.validate_acceptance_final(repo_root, alternate_final_path),
        )

        cross_gate_path = cross_run_root / "coverage-gate.json"
        cross_gate = copy.deepcopy(fake_gate)
        cross_gate["run_id"] = cross_run_id
        write_json(cross_gate_path, cross_gate)
        candidate_cross_record = copy.deepcopy(candidate_value)
        candidate_cross_record["coverage_gate"] = tool.artifact_record(
            repo_root, cross_gate_path, "E_NEGATIVE_FIXTURE"
        )
        write_json(candidate_path, candidate_cross_record)
        expect_failure(
            cases,
            "candidate-cross-run-gate-record",
            "E_ACCEPTANCE_CANDIDATE",
            lambda: tool.validate_acceptance_candidate(repo_root, candidate_path),
        )
        write_json(candidate_path, candidate_value)

        cross_candidate_path = cross_run_root / "candidate-manifest.json"
        write_json(cross_candidate_path, candidate_value)
        final_cross_record = copy.deepcopy(final_value)
        final_cross_record["candidate_manifest"] = tool.artifact_record(
            repo_root, cross_candidate_path, "E_NEGATIVE_FIXTURE"
        )
        write_json(final_path, final_cross_record)
        expect_failure(
            cases,
            "final-cross-run-candidate-record",
            "E_ACCEPTANCE_FINAL",
            lambda: tool.validate_acceptance_final(repo_root, final_path),
        )
        write_json(final_path, final_value)

        candidate_tamper = copy.deepcopy(candidate_value)
        candidate_tamper["run_id"] = cross_run_id
        write_json(candidate_path, candidate_tamper)
        expect_failure(
            cases,
            "candidate-cross-run-splice",
            "E_ACCEPTANCE_CANDIDATE_PATH",
            lambda: tool.validate_acceptance_candidate(repo_root, candidate_path),
        )
        write_json(candidate_path, candidate_value)

        final_tamper = copy.deepcopy(final_value)
        final_tamper["candidate_manifest"]["sha256"] = "f" * 64
        write_json(final_path, final_tamper)
        expect_failure(
            cases,
            "final-candidate-sha-tamper",
            "E_ACCEPTANCE_FINAL",
            lambda: tool.validate_acceptance_final(repo_root, final_path),
        )
        write_json(final_path, final_value)

        final_run_tamper = copy.deepcopy(final_value)
        final_run_tamper["run_id"] = cross_run_id
        write_json(final_path, final_run_tamper)
        expect_failure(
            cases,
            "final-cross-run-splice",
            "E_ACCEPTANCE_FINAL_PATH",
            lambda: tool.validate_acceptance_final(repo_root, final_path),
        )
    finally:
        tool.formal_check_data = original_formal_check
        shutil.rmtree(temporary_root)
        shutil.rmtree(cross_run_root)

    return {
        "schema_version": 1,
        "kind": "v934-step4-coverage-xml-fast-negative-result",
        "case_count": len(cases),
        "cases": cases,
        "status": "passed",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        result = run(args)
        if args.output is not None:
            tool.atomic_json(args.output, result)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (RuntimeError, tool.CoverageXmlError) as exc:
        print(f"[v934-coverage-xml-negative] ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
