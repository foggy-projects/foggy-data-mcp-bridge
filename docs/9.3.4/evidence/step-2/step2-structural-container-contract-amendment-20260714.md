---
doc_role: step-evidence
doc_purpose: Record the Step 2 structural JUnit container contract amendment and candidate provenance.
version: 9.3.4
step: 2
status: passed
created_at: 2026-07-14
updated_at: 2026-07-15
---

# Step 2 structural container contract amendment

## Immutable parent

This amendment does not rewrite the confirmed Step 1 generation. The following
Step 1 facts remain immutable historical inputs:

- confirmed run: `step1-candidate-r8-20260714`;
- contract manifest SHA-256:
  `e601c6c70ff02e9e50b86fd2b14b14aba9cfede096b42c93ff6e5968a918640f`;
- rename plan SHA-256:
  `acba7a9dc22c9c0fdbaa0438fe91018b61eee8a457a36f1918995f39aa74cfe2`;
- frozen counts: 820 discovery rows, 829 pre-amendment report execution identities,
  785 Step 2 identities, 44 Step 3 identities and 519 predecessor edges. These are
  historical pre-amendment identities, not current positive execution counts.

## Finding and decision

Independent pre-confirmation review found 59 JUnit Jupiter top-level `ClassSource`
containers with `discovered_test_nodes=0` and
`runtime_deferred_containers=0`. Maven still emits a fresh outer
`TEST-<top-level-fqcn>.xml` for each of them, while the actual testcases are
reported by one or more positive `$Nested` sibling suites.

Counting those outer XML files as positive executions contradicts the Step 2
`missing/zero/stale` fail-closed exit. Adding sentinel tests or globally allowing
zero reports would create false-green evidence. The Step 2 successor therefore
uses two typed sets:

1. positive execution reports, each with at least one testcase;
2. reviewed structural container reports, each fresh and exact but with
   `tests/testcase/failure/error/skipped=0`.

The structural set is not included in execution or testcase totals. A normal
positive report with zero testcases still fails with `E_ZERO_REPORT`.

## Successor contract

`structural-report-inventory.tsv` uses the exact columns below:

```text
module | source_id | source_fqcn | report_fqcn | runner | lane | variant_key | owner | discovered_test_nodes | runtime_deferred_containers | positive_sibling_execution_keys | disposition | rationale
```

For every structural row:

- `report_fqcn == source_fqcn`;
- discovery node/deferred counts are `0/0`;
- runner, lane, variant and owner match the removed Step 1 identity;
- `positive_sibling_execution_keys` is the non-empty, sorted set of positive
  `$Nested` siblings for the same source/runner/variant;
- disposition is `reviewed-structural-container`.

The reviewed successor counts are:

| Item | Count |
|---|---:|
| discovery rows / report owners | 820 / 804 |
| positive execution keys | 770 |
| Step 2 positive required | 724 |
| Step 3 deferred | 46 |
| structural reports | 59 |
| Surefire positive / structural | 677 / 55 |
| Failsafe positive / structural | 93 / 4 |
| predecessor execution / structural refs | 480 / 39 |
| predecessor edges | 519 |

The immutable rename plan remains `33 sources / 62 discovery reports / 74
pre-amendment identities / 50 predecessor edges`. Its successor classification is
`58 positive reports / 70 positive execution-key renames / 4 structural report
renames`; no planned source or predecessor edge is dropped.

The later corrective lane amendment adds two Mongo `IntegrationTest→IT` source/report/
execution renames without predecessor-edge changes. Therefore the final r8e generation is
`35 sources / 64 planned reports / 76 planned keys`, classified as `60 positive reports /
72 positive keys / 4 structural reports`.

The successor predecessor map adds a typed
`successor_structural_report_fqcn` column immediately after
`successor_execution_key`. Exactly one of the two successor reference columns is
non-`none`: 480 rows reference a positive execution key and 39 rows reference a
structural FQCN with disposition `structural-container-successor`.

## Raw report evidence rule

For each runner variant, the exact fresh raw XML set is the union of its positive
and structural expected reports. The verifier records separate positive and
structural metrics. Missing/extra/duplicate/stale reports, runner overlap,
skips/outcome errors, a zero positive report, a non-zero structural report or a
structural row without a positive sibling all fail closed.

## Excluded r1 diagnostic

- run id: `step2-candidate-r1-20260714`;
- command: `scripts/verify-v934-step2-successor.sh step2-candidate-r1-20260714`;
- Git HEAD: `9f5428d8d15d08457d2d2d57296256178c224f5d`;
- run directory created: `2026-07-14T21:15:25+08:00`;
- observed directory contents: only empty `classpaths/`, `discovery/` and
  `tool-classes/` directories; zero files;
- absent: `source-scan.json`, candidate directory, hash manifest, summary and
  published successor;
- mechanical outcome: the command was manually interrupted during
  `test-compile`; the original console stream was not persisted, so no unrecorded
  error text is inferred;
- review outcome: parallel review identified the structural/positive contract
  defect before candidate generation.

r1 is diagnostic/failed, was never confirmed and must not be reused or combined
with later evidence. Its wrapper did not reach the source-after guard, so it is
not evidence that protected source state remained unchanged.

## Confirmation status

The final amended generation is `step2-candidate-r8e-20260715`, confirmed by two
independent reviewers on 2026-07-15. Its positive/structural/deferred identities
are `770/59/46`, with Step 2 positive=`724`. The exact confirmation and actual
runner evidence are recorded in:

- [step2-successor-r8e-independent-review-20260715.md](step2-successor-r8e-independent-review-20260715.md);
- [step2-runner-split-exit-r8e-20260715.md](step2-runner-split-exit-r8e-20260715.md).

The earlier r8d generation and its Unit/Integration runs were invalidated by the
completed-window signal fail-open finding and are excluded from current evidence.
