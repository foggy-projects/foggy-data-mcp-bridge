---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.0
ticket: v950-release-authority-evidence-hardening
status: ACCEPTED
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

- [x] AC-1: resume/reuse 不复制 Maven 日志、原始 test report 目录或数据库原始输出。
- [x] AC-2: 复用只接受祖先候选，且整段 diff 全部属于冻结治理 allowlist；contract 或其他路径变化 fail closed。
- [x] AC-3: finalizer 对每个 key 的 kind、lane/database、tests/failures/errors/skipped、candidate 和 contract digest 精确校验。
- [x] AC-4: source-before/source-after 的 kind/status/candidate/count/digest 一致；archive 与 extraction 的 hash、文件数和源码封印一致。
- [x] AC-5: 负向单测覆盖 swapped lane、tampered totals、archive mismatch、越界复用和最小证据打包。
- [x] AC-6: 聚焦工具验证通过；新候选上的受控 authority manifest passed，且未启动产品 Maven/DB matrix 重跑。
- [x] AC-7: 原始脏工作区保持精确不变；无 install、CI、tag、release、publish、push。

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

- implementation_summary:
  - `--resume-from` 只复制最小源 JSON receipt，不再复制 root Maven 日志、semantic/SQLite
    raw reports 或数据库原始输出；
  - 新增 `--reuse-product-from`，只复用 root、semantic、七个数据库 variant 和四个
    database cell 的 passed receipt，归档、解包、portable、source seal、敏感扫描和最终
    manifest 始终重新生成；
  - 复用候选从 direct-parent 扩展为 ancestor，但整段 diff 必须严格命中
    `scripts/v950/**`（冻结 contract 除外）和本 BUG workitem；
  - finalizer 对有效源 receipt 的 key/kind/lane/database/report set/totals/candidate/contract
    逐项复核，并交叉绑定 source seal、archive 和 extraction 的 hash 与文件数。
- changed_paths:
  `scripts/v950/release_authority_tool.py`,
  `scripts/v950/verify-release-authority.sh`,
  `scripts/v950/tests/test_release_authority_tool.py` 和本 BUG workitem。
- tests_and_results:
  - `python3 -m py_compile scripts/v950/release_authority_tool.py
    scripts/v950/tests/test_release_authority_tool.py`：passed；
  - `bash -n scripts/v950/verify-release-authority.sh`：passed；
  - `python3 -m unittest -v scripts.v950.tests.test_release_authority_tool`：
    14/14 passed；覆盖 ancestor allowlist、contract 变化拒绝、最小 JSON 打包、
    swapped lane、tampered totals、archive/extraction mismatch 和完整 manifest；
  - contract validation：31 modules、32 projects、semantic 63、database 7 variants /
    370 tests，contract SHA-256
    `282d06d79456406224d7810400c11c030f31f10e7d17a2fb0848486f926cd9e0`；
  - 新 finalizer 对原 r3 的真实 17-receipt corpus 复核通过；
  - partial authority：
    `target/v950-release-authority/runs/v950-release-authority-hardening-20260724-r1`
    在候选 `03af64da7330d70d3364c9e92992710a2ed3a111` passed；
    portable 63 tests，F0/E0/S0，最终 17-receipt manifest passed。
- manual_or_experience_evidence:
  新 run 的复用产品证据仅含 13 个 wrapper 与 13 个 `.reused-source.json`；
  没有复制 root/semantic/database Maven 日志或 raw report 目录。fresh portable evidence
  保留自己的 Maven 日志和 11 个 JUnit XML。敏感扫描检查 46 个文件、3 个 pattern，
  只排除一个 hash-bound source archive，并通过。
- deviations:
  批准预算为 `20-40m`；实际 partial authority 在 warm Maven cache 下从
  `2026-07-24T14:57:08Z` 到 `14:58:39Z`，约 1 分 31 秒。未启动 Docker 或数据库
  provisioner，也未发生额外 authority 尝试。
- residual_risks:
  技术证据缺口已关闭；最终 review 仍在同一 Codex 会话执行，不声称组织独立性。
- reused_evidence:
  `v950-release-authority-20260724-r3` 的 passed root、semantic、七个 database variant
  和四个 external database cell receipt。root/semantic/SQLite 原始产品证据候选为
  `6a3c3bb3dd0d650ee0a514187f802d2e66ee9c60`；其余产品证据候选为
  `bbd8601df80e1734927eeac7351fe295cb75d74f`。两者到新候选的全部变化均通过治理
  allowlist 复核，frozen contract 未变。
- omitted_validation_and_reason:
  未重跑 root reactor、semantic、SQLite/外部数据库矩阵、Step 5/7、GitHub CI、
  `mvn install`、tag/release/publish/push；本 BUG 只改变 receipt packaging 与
  finalizer，产品证据前提未变化，批准的 partial authority 已重新生成所有直接受影响证据。
- readiness:
  `ACCEPTED`；AC-1 至 AC-7 已由 focused tests、真实 r3 corpus 复核、partial
  authority、clean-state、独立 hash audit 和原始工作区检查覆盖。

## Code Review Result

- reviewed_candidate:
  `03af64da7330d70d3364c9e92992710a2ed3a111`
- reviewed_range:
  `bbd8601df80e1734927eeac7351fe295cb75d74f..03af64da7330d70d3364c9e92992710a2ed3a111`
- changed_surface:
  本 BUG workitem 和三个 `scripts/v950` 文件；无产品、contract、`scripts/v934`、CI、
  API/SPI、数据或安全路径变化。
- findings:
  - reuse wrapper 在创建和 finalization 时都重算 ancestor 关系与完整 changed-path set；
  - source receipt 的 report identity、lane/database、totals、contract、candidate 和 digest
    均 fail closed；
  - archive/extraction/source seal 的 hash 与文件数形成双向一致性约束；
  - runner 的复用路径只复制 JSON receipt；fresh portable evidence 保留自己的日志和报告；
  - 未发现 debug、测试绕过、未解释 TODO、contract 漂移或越界兼容放宽。
- evidence_audit:
  final manifest 17/17 receipt SHA-256 独立重算一致；archive SHA-256
  `45bd6d33d7f2c5b17295c8cf9a28bc14bb4e1060bcc738fabe99c941e23cc945`
  一致；source-before/source-after 4432-file inventory 字节级一致。
- conclusion:
  所有 must-pass 项满足；同会话 review 是已披露的过程属性，不影响本 tooling-only
  standard assurance 结论，也不声称组织独立性。

## References

- requirement / issue: 2026-07-24 9.5.0 authority r3 evidence review.
- architecture / glossary: `docs/9.5.0/workitems/FEATURE-v950-release-authority-modernization.md`
- related work items: `docs/9.5.0/workitems/FEATURE-v950-release-authority-modernization.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: codex-reviewer
- signed_off_at: 2026-07-24
- acceptance_record: `docs/9.5.0/acceptance/version-signoff.md`
- blocking_items: none
- follow_up_required: no
