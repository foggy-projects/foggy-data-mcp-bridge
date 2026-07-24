---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.0
ticket: v950-release-authority-evidence-hardening
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
assurance_level: standard
approved_by: repository-owner-via-user-request
approved_at: 2026-07-24
open_questions: []
---

# Delivery Spec: 9.5.0 Release Authority Evidence Hardening

## Document Purpose

- intended_for: ultra-implementation / independent-signoff
- purpose: 冻结 9.5.0 authority 恢复证据打包与最终回执语义校验缺口的最小修复边界。
- canonical_path: `docs/9.5.0/workitems/BUG-v950-release-authority-evidence-hardening.md`

## Goal

- version_goal: 在不重跑已通过产品矩阵的前提下，提高 9.5.0 release authority 的可审计性和 fail-closed 强度。
- target_outcome: 恢复运行只携带最小 JSON 回执，finalizer 对 lane、数据库、测试总数、归档及源码封印做精确交叉校验。
- critical_outcomes:
  - 不复制 Maven 日志、Surefire/Failsafe 原始报告或数据库原始输出到恢复证据包；
  - 只允许从祖先候选复用已通过的产品回执，且候选差异必须全部属于明确治理 allowlist；
  - 复用 wrapper 与原始回执、候选、contract digest、lane/database/totals 逐项绑定；
  - archive、extraction 与 source seal 的 SHA-256、文件数和候选身份一致；
  - 负向测试证明 swapped lane、tampered totals、archive mismatch 和越界复用会失败。
- success_is_sufficient_when: 聚焦工具验证通过，并在新候选上复用产品证据、重新生成归档/portable/source-seal/sensitive-scan/final manifest 后得到 passed authority manifest。

## Scope

- in_scope:
  - `scripts/v950/release_authority_tool.py`
  - `scripts/v950/verify-release-authority.sh`
  - `scripts/v950/tests/test_release_authority_tool.py`
  - 本 BUG 交付记录及最终 9.5.0 签收记录
- affected_modules: release-governance tooling only.
- external_dependencies: existing passed authority run `v950-release-authority-20260724-r3`.

## Non-Goals

- out_of_scope: 产品代码、API/SPI、查询语义、数据库行为、权限、安全策略、Maven 模块和 CI。
- do_not_touch:
  - `scripts/v934/**`
  - `scripts/v950/release-authority-contract.json`
  - 原始脏工作区及其 `docs/9.3.5/**` 用户内容
- non_blocking_or_waivable_items: none for receipt identity, totals, archive/source binding, or clean candidate enforcement.

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 恢复证据只复制源 JSON receipt | r3 的 Maven 日志复制触发敏感扫描，日志不是 final manifest 必需输入 | 不改变产品证据含义；原 authority run 保持原位 |
| 产品回执可跨治理-only祖先链复用 | authority 工具修复本身不改变产品/测试结果 | 全范围 diff 只能命中 `scripts/v950/**`（contract 除外）和本 BUG workitem |
| root/semantic/七数据库 variant/四 cell 可复用 | 这些产品验证已在 passed r3 中完成 | archive、extraction、portable、source seal、sensitive scan、final manifest 必须新生成 |
| finalizer 精确校验 contract 语义 | 仅校验 kind/status/hash 无法识别 lane 或 totals 交换/篡改 | contract 文件保持冻结，不扩大权威范围 |

## Acceptance Criteria

- [ ] AC-1: resume/reuse 不复制 Maven 日志、原始 test report 目录或数据库原始输出。
- [ ] AC-2: 复用只接受祖先候选，且整段 diff 全部属于冻结治理 allowlist；contract 或其他路径变化 fail closed。
- [ ] AC-3: finalizer 对每个 key 的 kind、lane/database、tests/failures/errors/skipped、candidate 和 contract digest 精确校验。
- [ ] AC-4: source-before/source-after 的 kind/status/candidate/count/digest 一致；archive 与 extraction 的 hash、文件数和源码封印一致。
- [ ] AC-5: 负向单测覆盖 swapped lane、tampered totals、archive mismatch、越界复用和最小证据打包。
- [ ] AC-6: 聚焦工具验证通过；新候选上的受控 authority manifest passed，且未启动产品 Maven/DB matrix 重跑。
- [ ] AC-7: 原始脏工作区保持精确不变；无 install、CI、tag、release、publish、push。

## Contract / Data / Security Constraints

- API or event contract: no change.
- data and migration: no DDL/DML, migration, fixture mutation, or database provisioning.
- compatibility and rollback: revert the single governance-tooling commit.
- permissions and secrets: no credential change; evidence scan remains fail closed.

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/2 | must-pass | major | shell/static checks and Python tests | r3 recovery failure context | exact assertions and changed-path validation |
| AC-3/4 | must-pass | major | finalizer positive/negative unit tests | r3 passed receipt corpus | semantic and cross-receipt rejection evidence |
| AC-5 | must-pass | major | Python unittest suite | none | named negative tests |
| AC-6 | must-pass | major | partial authority on committed clean candidate | r3 product receipts | fresh archive/extraction/portable/seals/scan/final manifest |
| AC-7 | must-pass | major | status/diff review | original workspace baseline | exact final status |

## Validation Budget and Evidence Sufficiency

- assurance_level: standard
- lightweight_validation: `py_compile`, `bash -n`, focused Python unit tests, contract validation and static diff; expected `<5m`.
- medium_validation: one partial authority using r3 passed product receipts; expected `20-40m`.
- expensive_validation: no fresh root reactor, semantic replay, external DB matrix, Step 5/7, or release-governance full rerun.
- large_authority_or_replay_policy: prohibited except for the approved partial authority defined here.
- user_approval_status: approved on 2026-07-24 for steps 1-5 and one partial authority attempt.
- maximum_expensive_attempts: one partial authority attempt; no full product authority attempt.
- reusable_evidence: passed product receipts from `v950-release-authority-20260724-r3`.
- stop_when_evidence_is_sufficient: tool tests pass and the partial authority final manifest passes on the committed candidate.
- validation_not_required: `mvn install`, fresh root/semantic/database matrix, CI, tag, release, publish, remote push.

## Waiver Policy

- waivable_items: none for AC-1 through AC-6.
- authorized_role: repository owner.
- non_waivable_guards: clean committed candidate, ancestor-only reuse, governance-only diff, frozen contract, exact receipt semantics.
- required_risk_record: any omitted or failed validation must remain explicit and prevents a passed final manifest.

## Bug Context

- bug_source: acceptance-found
- severity: major
- environment: 9.5.0 authority recovery run `v950-release-authority-20260724-r3`.
- current_behavior:
  - resume copies root Maven log and raw semantic/SQLite evidence directories into the new run;
  - finalizer validates receipt identity/hash but does not fully revalidate every key's expected lane/totals or all archive/source cross-bindings.
- expected_behavior: recovery packages only minimal receipts and finalization rejects all semantic or cross-receipt mismatches.
- reproduction_status: confirmed.
- existing_evidence:
  - r3 sensitive scan initially rejected the copied root Maven log because it contained a known public test fixture credential;
  - code review identified incomplete exact lane/totals/cross-receipt validation.
- regression_protection: required through focused negative tests.
- waiver_reason_and_risk: none.

## Risks and Open Questions

- known_risks: the final technical review occurs in the same session and does not claim organizational independence.
- open_questions: none.

## Ultra Execution Contract

- 先完成本文件的聚焦 review，再将状态改为 `ULTRA_EXECUTING`。
- 只修改冻结范围；contract、产品、API/SPI、数据、安全或 CI 变化必须 `NEEDS_REPLAN` 并停止。
- 先运行聚焦工具验证并提交 clean candidate，再运行一次批准的 partial authority。
- 完成后填写 `Implementation Result` 并设为 `READY_FOR_SIGNOFF`；不得伪造 `ACCEPTED`。
- 不 tag、release、publish、push。

## Implementation Result

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: pending
- residual_risks: pending
- reused_evidence: pending
- omitted_validation_and_reason: pending
- readiness: pending

## References

- requirement / issue: 2026-07-24 9.5.0 authority r3 evidence review.
- architecture / glossary: `docs/9.5.0/workitems/FEATURE-v950-release-authority-modernization.md`
- related work items: `docs/9.5.0/workitems/FEATURE-v950-release-authority-modernization.md`

## Acceptance Status

- acceptance_status: pending
- acceptance_decision: pending
- signed_off_by: pending
- signed_off_at: pending
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
