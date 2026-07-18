---
type: bug
bug_source: quality-gate-found
version: 9.3.4
ticket: BUG-934-STEP4-EVIDENCE-JSON-NUMERIC-TYPE-ALIAS-BYPASS
severity: high
status: closed
post_gate_confirmed_at: 2026-07-18
post_gate_evidence: docs/9.3.4/acceptance/step4-coverage-gate-acceptance.md
reproduction_status: confirmed
product_regression: false
test_strategy: canonical-evidence-type-alias-negative
automation_decision: required
owner: step4-coverage-tooling
---

# Step 4 canonical evidence 接受 JSON numeric type alias

## Background

r12 coverage remediation 的提交前全文件审查发现，部分 canonical evidence comparison 仍使用
Python 宽松数值相等。Python 会把 `true`、`1`、`1.0` 视为相等值；因此 JSON producer 的
integer/float/boolean 类型约束没有在所有 consumer 上保持 exact identity。

隔离复制 immutable r12 后可证明两条完整 public diagnostic 路径曾被放行：

1. 把 `child-lifecycle.json` 的 `schema_version`、`child_count`、child PID/ticks、
   `leader_reaped` 或 `process_group_residue` 改为等值 bool/float，并重算直接引用 hash；
2. 把 aggregate provenance input/aggregate exec size 和 effective-POM receipt size 改为
   等值 float，并重算 provenance、observation、summary 与 status hash 闭包。

这不能伪造真实失败 child，但会让非 producer schema 进入 diagnostic/formal evidence，属于
canonical provenance fail-open。

## Root Cause

- child lifecycle recomputation 使用 `manifest == expected_manifest`；
- 三个 provenance size 字段直接使用 `==` 比较 decoded JSON number 与 integer file size；
- threshold `schema_version` 与 aggregate observation `ratio` 还有同类 defense-in-depth
  宽松检查。

## Fix

- child lifecycle manifest 改用 recursive `exact_json_identity`；
- aggregate input、aggregate exec、effective-POM 三个 size 统一经
  `exact_file_size`，双方必须是 positive JSON integer；
- Step 1/Step 4 threshold schema version 必须 `type is int`；
- aggregate observation ratio 必须是 finite JSON float，且精确等于 producer 的
  `round(covered / total, 12)`；
- AST regression 固定 `validate_aggregate_provenance=2`、
  `validate_report_provenance=1` 三个 size-validator 调用，防止某一路脱钩。

## Regression Test Decision

`automation_decision=required`。XML fast negatives 新增 9 项：

- provenance size validator call binding；
- size float/boolean alias；
- aggregate ratio integer alias；
- threshold schema boolean alias；
- child lifecycle root schema bool、root count float、nested bool、nested float。

当前 XML negatives=`118/118`，所有变体命中稳定错误码；canonical positive 仍通过。

## Checklist

- [x] 完整 r12 clone public-path 绕过已复现并定级。
- [x] exact identity / exact integer / exact float consumer 已实现。
- [x] 9 个 targeted negatives 已通过。
- [x] post-fix full-closure 与正式实现质量复核均为 B/H/M/L=`0/0/0/0`。
- [x] Cdiag commit/push 后由 fresh r13 重算 canonical authority。

## Fresh authority closure

fresh r13 在 commit `b76552e21479c75111f648a4aa678abe018cc3f9` 上 sealed PASS，observation
SHA-256=`91992393cc2dba4db2e8ae8f8e5fc400273329001b5a3aa61c8df7d91cb7f542`。真实
threshold candidate 经 public verification 与独立 strict-type projection 复算通过：aggregate
exact、critical rows=`12`、positive metrics=`23`、唯一 N/A=`1`、minimum exact match=`23/23`；
candidate SHA-256=
`8bb47382444fd66893d250a8787416c9ce73f9590be4c66308fb7a2e3e014d00`。

这补齐了 checklist 中最后一项 fresh authority evidence；BUG 保持 `status=closed`。该记录时点
Cfreeze/formal/audit 尚未开放；后续 formal-r4、coverage audit 与 feature acceptance 已完成，
Step 5 现为 `ready / not-started`。

## References

- `scripts/v934/step4/coverage_xml_tool.py`
- `scripts/v934/step4/coverage_xml_negative_tool.py`
- `docs/9.3.4/workitems/BUG-step4-threshold-freeze-observation-applicability-gap.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r13-pass-20260717.md`
- `docs/9.3.4/evidence/step-4/step4-coverage-diagnostic-r13-threshold-review-20260717.md`
