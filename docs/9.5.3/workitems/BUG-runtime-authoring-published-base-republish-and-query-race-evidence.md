---
doc_type: delivery-spec
delivery_type: bug
version: 9.5.3
ticket: BUG-runtime-authoring-published-base-republish-and-query-race-evidence
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: published base 再发布与并发 live-query 证据修复

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 关闭 publish/recovery API 首次正式验收发现的两个不可豁免 blocker：后续 workspace 无法从
  owned immutable publication 再次发布，以及缺少 publication 窗口并发 live-query 证据。
- canonical_path:
  `docs/9.5.3/workitems/BUG-runtime-authoring-published-base-republish-and-query-race-evidence.md`

## Goal

- version_goal: 使 9.5.3 Runtime-local authoring publish 成为可重复使用的开发闭环，并补齐 source switch/
  refresh 期间 live query 不观察 mixed catalog 的 elevated 证据。
- target_outcome: 首次发布后的 immutable Runtime-owned artifact 继续作为后续 workspace 的只读 base；新
  workspace 修改、完整验证并发布为新的 immutable artifact，失败恢复仍精确返回上一 immutable base。
- critical_outcomes:
  - 只允许当前 registry/live source 指向同一 owned、hash/manifest/attempt 可证明的 immutable artifact 时
    再发布；foreign、tampered、missing、unknown attempt 或 identity drift 在新 artifact/live mutation 前拒绝。
  - 第二次 publish 的 durable attempt 记录上一 immutable source 的完整 registry identity；成功收敛到新
    artifact，任一失败可恢复上一 artifact、registry 与 catalog，不把历史 artifact 原地修改或删除。
  - publication transaction 期间并发 live query 只观察完整 old、完整 new 或稳定 not-current，不观察
    TM/QM/FSScript 混合结果或 candidate request-local catalog。
- success_is_sufficient_when: AC-1 至 AC-5 全部通过，RED 回归证明修复前第二次 publish 被拒绝，修复后真实
  filesystem + SQLite 连续两次发布与并发 live-query 证据通过，原 feature focused/affected lanes 无回归。

## Scope

- in_scope:
  - 允许 verified Runtime-owned immutable publication 作为后续 workspace publication base。
  - 对 registry path、artifact revision、attempt、Namespace/Bundle、manifest/hash 和 live source 做一致性预检。
  - 保持旧 immutable artifact，只追加新 artifact；扩展自动/显式恢复证据覆盖 immutable base。
  - 增加连续 publish 与受控并发 live-query 的长期自动化回归。
  - 更新原 feature 状态引用、9.5.3 进度和必要架构说明；不改写首次拒签记录。
- affected_modules:
  - `foggy-runtime-api`
  - `docs/9.5.3`
  - 如当前架构事实变化则更新 `docs/architecture/runtime-and-model-lifecycle.md`
- external_dependencies: none。

## Non-Goals

- out_of_scope:
  - 成功发布后的用户选择历史 rollback、revision selector、artifact GC、release package、生产 promotion、
    Git、JAR/classpath publish、多 Namespace binding、Console UI 或跨 Runtime 编排。
  - 多进程/shared-NFS writer、分布式锁、远程 artifact store、签名或加密。
  - 修改 live query REST contract、candidate query mode、权限语义、模型语言或数据库 schema。
- do_not_touch:
  - `foggy-dataset-model-*`、Model SPI、Console、launcher、POM/Maven 依赖图。
  - 原 feature 的 Implementation Result 与首次拒签正文；修复结果写入本 BUG 和后续 R2 signoff。
- non_blocking_or_waivable_items:
  - artifact GC 与 `.staging-*` restart cleanup 仍是边界清楚的后续项，不得因此删除引用中的 artifact。
  - 单 Runtime 进程、非 shared-NFS writer 前提保持。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| immutable 表示 artifact bytes 只读，不表示 lifecycle 永久不可升级 | 后续 workspace 必须成为再次修改入口 | direct resource save 继续拒绝；升级只产生新 artifact 并切换 registry/source |
| 再发布只接受 Runtime published store 可证明 ownership 的 current artifact | 不能把 caller 或 registry 任意 path 提升为可信 recovery base | 必须验证 root ownership、attempt、manifest/hash、Bundle/Namespace/revision/path 对应 |
| 第二次 attempt 继续复用 previous immutable metadata | 现有 attempt schema 已能记录 previous immutable/artifact revision | 不引入历史 rollback API；失败仅补偿当前 attempt 的 base |
| 并发证据使用真实 Runtime filesystem + SQLite/live semantic query | AC-9 不能只由 mock lock 或源码推断 | 允许结果为 old/new 或稳定 not-current；不得放宽为任意异常 |
| 不修改 Engine 原子 refresh | 现有 snapshot publication/source lifecycle 是已签收基线 | 若 Runtime-only 测试证明 Engine 原语不足，设置 `NEEDS_REPLAN` |

## Acceptance Criteria

- [x] AC-1: 首次 publish 后创建第二个 workspace，修改 TM/QM/FSScript、完整 validate 并再次 publish 成功；
  registry/live inventory/artifact revision/catalog source revision 对应第二个 candidate，真实 live SQLite query
  只返回第二版数据，第一版 artifact 字节与 manifest 保持不变。
- [x] AC-2: immutable base 只有在 published root ownership、artifact path、attempt status、Namespace、Bundle、
  candidate/artifact revision 与 manifest/hash 全部一致时才可进入 durable intent；tamper、foreign path、missing/
  corrupt attempt、unknown status 或 registry/live drift 均零新 artifact、零 source/registry/catalog mutation。
- [x] AC-3: 第二次 publish 的 source/registry/refresh/final evidence 任一失败，自动或 pinned explicit recovery
  恢复第一版 immutable artifact、`watch=false` registry identity、full-Namespace catalog 与第一版 live query；
  两个 artifact 均不原地修改或删除。
- [x] AC-4: 受控并发测试覆盖 publish transaction 与 live semantic query；每个成功结果严格属于 old 或 new
  完整数据集，允许的失败只能是稳定 source/catalog not-current family，且最终 new catalog/source current。
- [x] AC-5: 既有 direct resource immutable guard、mutable external 首次 publish、publish 单赢家、Bundle/resource
  shared lock、restart recovery、auth/envelope/secret 与 workspace/model compatibility lanes 全部保持通过。

## Contract / Data / Security Constraints

- API or event contract: 不新增 route 或 request 字段；`publish`、`recover`、workspace/publication evidence 与
  RuntimeEnvelope 保持兼容。immutable published Bundle 仍 `editable=false`、`workspaceEligible=true`。
- data and migration: 优先复用 publication attempt v1 已有 previous immutable fields；如必须变更 schema，需
  additive、旧 attempt 可读、未知版本 fail closed，且不得迁移或删除历史 artifact。
- compatibility and rollback: mutable Runtime-managed external 的首次 publish 语义不变；旧 immutable source
  只有在新 attempt 失败时作为精确补偿目标，不形成任意历史 rollback 产品能力。
- permissions and secrets: route 继续要求 management auth；验证/错误/evidence 不返回 absolute path、storeId、
  auth-code、token、JDBC secret、stack trace 或文件内容。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1 | must-pass | critical | RED unit + real filesystem/SQLite double publish | first publish 57/146 lanes | v1→v2 live rows、artifact retention、identity convergence |
| AC-2 | must-pass | critical | immutable provenance/tamper/drift preflight matrix | artifact store ownership tests | exact zero-mutation snapshots and stable errors |
| AC-3 | must-pass | critical | immutable-base refresh/final/recovery fault test | mutable-base recovery matrix | old immutable registry/catalog/live query restored |
| AC-4 | must-pass | critical | multithread real live query during controlled publication window | atomic refresh/source revision baseline | every observation classified old/new/not-current |
| AC-5 | must-pass | critical | original feature focused + affected Runtime lanes | prior 57/146 results | actual rerun counts with no skipped specified tests |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated；修复 live source lifecycle 与不可豁免并发数据正确性证据。
- lightweight_validation:
  - tracked/untracked whitespace checks、targeted publication/artifact unit tests，单次 `<5m`。
- medium_validation:
  - 原 feature focused lane，预计 `5-15m`。
  - 原 feature affected Runtime lane，含真实 SQLite/auth/compatibility，预计 `5-20m`。
- expensive_validation: none by default。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；不是 release/tag/promotion，改动必须保持 Runtime-only。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若必须修改 Engine/Model SPI、依赖图、launcher、数据库、多进程语义或公共
  API schema，设置 `NEEDS_REPLAN`，不扩大实现或验证。
- maximum_expensive_attempts: 0
- reusable_evidence: 原 feature 首次 publish、mutable recovery、artifact ownership、auth/compatibility 证据；
  直接修改或断言变化的 publication/artifact/real execution tests 必须重跑。
- stop_when_evidence_is_sufficient: AC-1 至 AC-5 focused evidence 全绿，57-test focused 与 146-test affected
  等效选择重新通过，source review 确认 Runtime-only 且 tracked/untracked checks 通过后停止。
- validation_not_required: 完整 Maven reactor、Console build/Playwright、launcher、数据库矩阵、authority/replay/
  rehearsal/source-seal、tag/release/production publish。

## Waiver Policy

- waivable_items: 仅 artifact GC、staging cleanup 与非关键 diagnostics 文案。
- authorized_role: product owner / delivery owner
- non_waivable_guards: owned immutable provenance、hash/manifest、zero-overwrite/delete、exact recovery、old/new/
  not-current only、auth/secret 和证据真实性。
- required_risk_record: waiver 必须包含有界影响、可检测性和跟进 owner；不得 waiver mixed catalog、错误
  recovery base、foreign overwrite、权限绕过或把 failed publication 标为成功。

## Bug Context

- bug_source: acceptance-found
- severity: critical
- environment: 9.5.3 `main` base `80fe0fb2` 的未提交 publish/recovery 工作树。
- current_behavior: 首次 publish 后 Bundle registry 为 immutable；下一 workspace 可创建，但 publish preflight
  返回 `WORKSPACE_SOURCE_INELIGIBLE`。AC-9 只有 publish/Bundle/resource lock evidence，无 live-query race test。
- expected_behavior: verified owned immutable artifact 可作为下一 publication base；并发 live query 仅观察
  old/new/not-current。
- reproduction_steps:
  1. 从 mutable Runtime-managed external Bundle 创建、validate 并 publish workspace A。
  2. 从 A 发布后的 `workspaceEligible` Bundle 创建、修改并 validate workspace B。
  3. 对 B 调用 publish；当前在 immutable source guard 处被拒绝。
- reproduction_status: confirmed
- existing_evidence: 首次正式验收记录中的 AC-4 failure、AC-9 unknown；独立 57/146 lanes 均通过但未覆盖缺口。
- existing_tests: success test 只断言下一 workspace 能创建；并发测试只断言 publish 单赢家和 shared lock。
- regression_protection: required
- waiver_reason_and_risk: N/A；核心 lifecycle 与 mixed-catalog guard 不可 waiver。

## Risks and Open Questions

- known_risks:
  - 允许 immutable base 时不能仅信任 registry boolean/path；必须回到 owned attempt/manifest/hash 验证。
  - base 本身为 immutable 时，恢复必须保留其 `watch=false` 与 artifact revision，不能降级成 mutable record。
  - 并发测试需要受控窗口但不能改变生产同步语义或引入 test-only production bypass。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、项目 `CLAUDE.md`、原 feature spec/拒签记录、Runtime/model lifecycle architecture 与相关实现。
- 优先建立能稳定失败的第二次 publish 回归，再实现最小 Runtime-only 修复；不得直接修改断言掩盖拒绝。
- immutable provenance validation 必须在新 artifact/durable attempt/live mutation 前完成，且不接受 caller path。
- 并发 live-query 证据使用真实 filesystem + SQLite 与生产 query/refresh 路径；test double 只用于控制时序。
- 如需 Engine/Model SPI、Console、launcher、依赖、数据库、多进程或成功 rollback 语义，设置
  `NEEDS_REPLAN` 并停止扩展。
- 运行精确 focused/affected 命令并记录 classes/tests/failures/skips/耗时；不得用 no-test 容错参数掩盖
  指定新测试未执行。
- 未经用户明确批准，不得运行完整 reactor、Console/Playwright、launcher、数据库矩阵、authority/replay/
  rehearsal/source-seal、tag/release 或 production publish。
- 完成后填写 `Implementation Result`，将本 BUG 状态改为 `READY_FOR_SIGNOFF`；原 feature 保持拒签，等待
  R2 独立验收统一回写，不得自行 `ACCEPTED`。

## Implementation Result

- implementation_summary:
  - verified Runtime-owned immutable publication 现在可作为下一 workspace 的只读 base；每次 publish 追加新
    immutable artifact，绝不原地修改上一 artifact。
  - immutable provenance 在新 artifact、attempt 与 live mutation 前验证 owned root、精确 attempt path、
    `PUBLISHED` status、Bundle/Namespace/revision identity、manifest/hash、symlink 与 foreign entry。
  - 第二次 publish 失败会按 attempt 中既有 previous immutable metadata 恢复上一 artifact、`watch=false`
    registry identity 与 full-Namespace catalog；无需 schema 变更。
  - 新增真实 filesystem + SQLite 连续发布与受控并发 live semantic query 回归；成功观察只允许完整 old/new，
    失败只允许稳定 not-current family。
- changed_paths:
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeAuthoringWorkspaceService.java`
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimeAuthoringWorkspacePublicationService.java`
  - `foggy-runtime-api/src/main/java/com/foggyframework/runtime/api/service/RuntimePublishedBundleArtifactStore.java`
  - `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeAuthoringWorkspacePublishServiceTest.java`
  - `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeAuthoringPublicationStoreTest.java`
  - `foggy-runtime-api/src/test/java/com/foggyframework/runtime/api/service/RuntimeAuthoringWorkspaceRealExecutionTest.java`
  - `docs/architecture/runtime-and-model-lifecycle.md`、`docs/9.5.3/README.md` 与本 spec
- tests_and_results:
  - RED：第二次 publish 定向回归 1 test，修复前按预期以 `WORKSPACE_SOURCE_INELIGIBLE` 失败。
  - immutable provenance/tamper 定向回归 4 tests，0 failures/errors/skips；篡改 base 返回
    `WORKSPACE_ARTIFACT_CORRUPT`，artifact/attempt、workspace、registry/live source、source revision 与 refresh
    次数均不变。
  - real filesystem + SQLite：`RuntimeAuthoringWorkspaceRealExecutionTest` 6 tests，0 failures/errors/skips；
    第二 workspace 同时修改 TM/QM/FSScript，连续 publish 后只查询到 v2 rows，并保留 v1 artifact/manifest；
    publication 窗口每次观察均为 old/new/not-current，最终 new source/catalog current。
  - focused lane：7 classes / 61 tests，0 failures/errors/skips，56.768s。
  - 最终 affected lane：14 classes / 152 tests，0 failures/errors/skips，55.269s。
- manual_or_experience_evidence:
  - source review 确认 immutable base 只接受 owned completed matching attempt；foreign、tampered、missing、
    identity drift 和 corrupt metadata 均 fail closed，且校验发生在 durable intent/live mutation 前。
  - 生产改动仍只在 `foggy-runtime-api`；没有修改 Engine/Model SPI、Console、launcher、POM、依赖图或数据库
    schema。
- deviations: none
- residual_risks:
  - artifact GC 与 `.staging-*` restart cleanup 仍未实现；引用中的 artifact 必须保留，未知现场继续 fail closed。
  - 一致性仍限单 Runtime 进程、非 shared-NFS writer；绕过 Runtime 的外部 filesystem tampering 在下一次
    store/publication 操作中检测。
  - 成功发布后的历史 rollback、release package、生产 promotion、Git、JAR 多 Namespace、Console UI 和
    高级 candidate query mode 仍属后续 workitem。
- reused_evidence:
  - 原 feature 首次 publish、mutable recovery、auth/envelope、Bundle/resource shared lock 与 compatibility
    evidence；所有直接受影响的 focused/affected tests 已重新执行。
- omitted_validation_and_reason:
  - 按冻结 elevated 预算未运行完整 Maven reactor、Console build/Playwright、launcher package、数据库矩阵、
    authority/replay/rehearsal/source-seal、tag、release 或 production publish；本修复未修改这些边界。
- readiness: READY_FOR_SIGNOFF

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex R2 reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/BUG-runtime-authoring-published-base-republish-and-query-race-evidence-signoff.md`
- blocking_items: none
- follow_up_required: yes

## References

- requirement / issue:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-publish-recovery-api-signoff.md`
- architecture / glossary:
  `docs/architecture/runtime-and-model-lifecycle.md`
- related work items:
  `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`
