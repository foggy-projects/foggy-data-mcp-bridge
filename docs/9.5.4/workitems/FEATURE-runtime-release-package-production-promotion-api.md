---
doc_type: delivery-spec
delivery_type: feature
version: 9.5.4
ticket: FEATURE-runtime-release-package-production-promotion-api
status: ACCEPTED
canonical: true
execution_mode: ultra
assurance_level: elevated
approved_by: user
approved_at: 2026-08-01
open_questions: []
---

# Delivery Spec: Release Package 与 Production Promotion API

## Document Purpose

- intended_for: normal-analysis / ultra-implementation / independent-signoff
- purpose: 冻结 portable JSON package、production import/revalidation、exact apply、一步 rollback 与失败恢复的
  Runtime 管理 API 和持久化不变量。
- canonical_path: `docs/9.5.4/workitems/FEATURE-runtime-release-package-production-promotion-api.md`

## Goal

- version_goal: 让模型 revision 能在无 Git 场景安全跨 Runtime 搬运，并在生产依赖/权限上重验后受控 apply。
- target_outcome: 开发 Runtime 导出 deterministic package；production-enabled Runtime 原子导入为 immutable
  candidate workspace，生产 validate/query 后 exact apply，并可回到直接前一 base。
- critical_outcomes:
  - package schema/hash/content/provenance 自洽且不泄密。
  - import 在任何持久化前完整验证，创建 target-base + package-candidate 的单一原子 workspace。
  - production validation/query 不复用开发通过状态；apply 不可由普通 publish 绕过。
  - rollback durable、exact、single-step；失败优先 forward recovery，未知现场 fail closed。
  - Engine/SPI、launcher、依赖图和数据库零改动。
- success_is_sufficient_when: AC-1 至 AC-12 有 focused/affected Runtime API 证据，真实 temp filesystem + SQLite
  覆盖 export→import→validate/query→apply→rollback 和失败恢复，auth/capability/compatibility 全绿。

## Scope

- in_scope:
  - additive release package DTO/routes/capabilities；export/import/apply/rollback/rollback-recover。
  - additive `productionPromotionEnabled=false` 配置和 fail-closed route guard。
  - workspace release import provenance、immutable imported candidate、store additive compatibility。
  - package canonical hashing、path/UTF-8/hash/quota/case/overlay validation。
  - apply 复用 publication artifact/source/registry/full refresh coordinator。
  - durable rollback states/evidence、restart reconciliation、forward recovery 和 stable errors。
  - architecture、dev guide、9.5.4 tests/docs。
- affected_modules: `foggy-runtime-api`、`docs/architecture`、`docs/dev-guide`、`docs/9.5.4`。
- external_dependencies: none。

## Non-Goals

- out_of_scope: Console、ZIP/multipart、签名/KMS、package registry/GC、审批/RBAC/用户审计、完整 history、任意
  rollback、Git、跨 Runtime control plane、JAR binding、Engine query modes。
- do_not_touch: Engine/Model SPI、launcher、POM/Maven dependencies、数据库 schema、9.5.3 历史验收。
- non_blocking_or_waivable_items: 无签名、无 GC、仅一步 rollback；必须在 capability/docs 中明确。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| package v1 为 canonical JSON | 资源均为 UTF-8 文本，避免 ZIP traversal/bomb 和新依赖 | 只含 TM/QM/FSScript；format version fail closed |
| auth-code 操作者是 v1 trust root | 当前无用户 IAM/签名 infrastructure | hash 不宣称发布者身份；所有 route 强制 configured auth |
| promotion 默认关闭 | 生产写入必须显式 opt-in | false 时 import/apply/rollback unavailable；export 仍可用 |
| promotion mode 禁止普通 publish | 避免手工草稿绕过 package provenance | 开发默认行为不变 |
| imported candidate immutable | apply 必须对应 portable package exact bytes | 可 diff/validate/query/discard，不可 save/delete |
| 生产必须重新 validate/query | 环境依赖、数据源与权限不同 | 开发 validation 只作 provenance，不改变 imported state |
| apply 复用 publication coordinator | 已有 artifact/source/refresh/recovery 原语 | 独立 route pin package + candidate + target base |
| rollback 仅直接前一 base | v1 不建设 revision history | exact current candidate/attempt；第三方漂移零覆盖 |
| rollback 失败前向恢复 candidate | failed rollback 不应默认留下旧/新未知状态 | 无法证明时 `ROLLBACK_REQUIRED`，需显式 recover |

## Acceptance Criteria

- [ ] AC-1: export 仅接受 exact `VALIDATED`/`PUBLISHED` current validation，packageId deterministic，资源和
  validation/dependency provenance 完整且无 path/secret/catalog/query data。
- [ ] AC-2: import schema/package/resource hash/candidate revision/path/type/UTF-8/case/quota/overlay 任一失败在
  workspace/store mutation 前拒绝，稳定错误且不留下目录/record。
- [ ] AC-3: import 仅在 production promotion enabled、explicit target Namespace 与 current eligible Bundle 上
  原子创建 workspace；base 等于生产 current，candidate 等于 package，release provenance 持久且 restart 可读。
- [ ] AC-4: imported workspace save/delete 均拒绝，diff/read/discard 可用；开发 validation 不被当作 production
  current evidence，必须实际 validate 后才能 query/apply。
- [ ] AC-5: apply request pin packageId、candidate、base Bundle、base Namespace source revision；普通 publish 在
  promotion mode 拒绝，apply 之前任何 target/source drift 零 live mutation。
- [ ] AC-6: apply 成功后 artifact/registry/inventory/source/catalog/workspace evidence 收敛，live query 使用 package
  content；失败沿用已验收自动/显式 publication recovery。
- [ ] AC-7: rollback 只接受 PUBLISHED imported workspace 的 exact package/candidate/apply attempt，且 live/
  registry/catalog current；首次 mutation 前 durable `ROLLING_BACK`。
- [ ] AC-8: rollback 成功恢复直接前一 base registry/source/full catalog，workspace terminal `ROLLED_BACK`，记录
  rolled-back source/catalog evidence；candidate artifact 保留不变。
- [ ] AC-9: rollback 任一失败优先 forward-recover candidate 并返回失败；不能证明则 `ROLLBACK_REQUIRED`，
  explicit recover 只 pin 同 attempt/candidate/package，第三方漂移零覆盖/delete。
- [ ] AC-10: restart 将中断 `ROLLING_BACK` 收敛为 `ROLLBACK_REQUIRED`，不自动 mutation；并发 apply/rollback/
  Bundle mutation 单赢家，live query 仅 old/new/not-current，无 mixed catalog。
- [ ] AC-11: capability/auth/envelope/phase/secret redaction 稳定；旧 Runtime default config、workspace、publish、
  Bundle/resource/model/query compatibility 保持。
- [ ] AC-12: focused + affected Runtime API tests、真实 filesystem/SQLite、compile、diff/untracked checks 通过；
  changed paths 无 Engine/launcher/POM/dependency/database 越界。

## Contract / Data / Security Constraints

- API or event contract: additive JSON routes；`RuntimeEnvelope`、`X-NS`、management auth 不变。package content 不
  进入 URL/log；candidate query 的业务 Authorization 只走现有 workspace query route。
- data and migration: workspace record additive `releaseImport`；旧 record null 兼容。publication attempt/evidence
  additive rollback fields/status；旧 attempt 无损读取。无 DB migration。
- compatibility and rollback: 默认配置下新生产能力关闭、现有开发 publish 行为不变；回退旧程序前停用
  promotion，保留 package/artifact/workspace 数据供 operator 备份。
- permissions and secrets: 所有 authoring release routes在任何 scope 下强制 configured auth-code；package、API、
  diagnostics、tests 不包含 token、Authorization、JDBC secret、absolute path、storeId 或 stack trace。

## Test and Evidence Obligations

| Item | Classification | Risk | Required Validation | Reusable Evidence | Required Evidence |
|---|---|---|---|---|---|
| AC-1/2 | must-pass | critical | package hash/tamper/quota/path unit | workspace store limits | deterministic roundtrip + zero mutation snapshots |
| AC-3/4 | must-pass | critical | import store/restart + real validation/query | workspace real execution | production base/candidate/provenance and immutable guards |
| AC-5/6 | must-pass | critical | real apply/live query + fault matrix | publish/recovery R2 | exact route, convergence and recovery |
| AC-7/9 | must-pass | critical | rollback/forward recovery/drift faults | publication source classifier | durable exact rollback and no overwrite |
| AC-10 | must-pass | critical | restart/concurrency/query race | accepted publication lock | single winner, old/new/not-current only |
| AC-11/12 | must-pass | major | random-port auth/capabilities + affected lane | current Runtime compatibility | config off/on matrix, exact counts/commands |

## Validation Budget and Evidence Sufficiency

- assurance_level: elevated；公共管理 API、portable artifact、生产 source/catalog mutation 与 rollback。
- lightweight_validation: `git diff --check`、untracked no-index、compile、package/store/service/controller unit。
- medium_validation: focused Runtime API Maven lane、真实 temp filesystem + SQLite、auth random-port tests，5–30m。
- expensive_validation: none by default；不运行完整 reactor/DB matrix/release chain。
- large_authority_or_replay_policy: prohibited-unless-user-approved
- full_chain_recommendation_trigger: none；非 tag/release，focused+affected Runtime lane 足以判断。
- estimated_full_chain_wall_clock: not-estimated
- full_chain_prerequisites: none
- user_approval_status: not-requested
- decision_if_not_approved: proceed-with-focused-and-affected-validation
- expensive_validation_trigger: 若必须修改 Engine/SPI、依赖、launcher、数据库或引入签名/KMS，`NEEDS_REPLAN`。
- maximum_expensive_attempts: 0
- reusable_evidence: 9.5.3 workspace store/candidate/publish/recovery/auth/live-query isolation R2。
- stop_when_evidence_is_sufficient: AC-1–12 focused/affected 绿、real execution 证明 apply/rollback、source review
  证明 auth/zero leak/no scope expansion 后停止。
- validation_not_required: full reactor、Console、launcher、DB matrix、authority/replay/rehearsal/source-seal、tag/release。

## Waiver Policy

- waivable_items: 仅签名、GC、完整历史 UI（均未实现且明确 non-goal）。
- authorized_role: product owner / release owner
- non_waivable_guards: package integrity、production opt-in、exact identity、revalidation、durable rollback、drift zero
  overwrite、auth/secret、atomic catalog 与真实 live result。
- required_risk_record: 不得 waiver 数据丢失、错 target、未验证 apply、mixed catalog 或权限绕过。

## Risks and Open Questions

- known_risks: v1 hash 不证明发布者身份；immutable artifact 无 GC；一致性仍限单进程/非 shared-NFS。
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、9.5.4 design、项目规则和 9.5.3 accepted workspace/publish evidence。
- 开始实现时改 `ULTRA_EXECUTING`；优先 RED package tamper/import/apply/rollback/restart/auth tests。
- 如需 Engine/SPI/依赖/launcher/DB/签名或任意历史 rollback，设置 `NEEDS_REPLAN`。
- 完成后填写 Implementation Result，改 `READY_FOR_SIGNOFF`，不得自行 `ACCEPTED`。

## Implementation Result

- implementation_summary: 已交付 deterministic `foggy-authoring-release/v1` export/import、production opt-in、
  imported candidate immutability 与生产重验、exact promote、一步 pinned rollback、失败前向恢复和 restart
  reconciliation；普通 publish 在 promotion mode 由服务端拒绝。
- changed_paths: `foggy-runtime-api` additive routes/config/DTO/controller/service/store/artifact 与 focused tests；
  `docs/9.5.4`、runtime lifecycle architecture 和 Bundle/Namespace dev guide。未修改 Engine/SPI、launcher、POM、
  Maven/npm 依赖或数据库 schema。
- tests_and_results:
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipTests compile`：BUILD SUCCESS。
  - focused Runtime API lane（package/publish/real execution/auth/capability）：92 tests，0 failure/error/skip。
  - affected `-Dtest=Runtime*Test` lane：231 tests，0 failure/error/skip。
  - 关键类：release package 4、publish/promotion/rollback 15、real SQLite execution 7、workspace store 18、
    capabilities 51，均通过。
  - `git diff --check` 与全部 untracked `git diff --no-index --check /dev/null <file>` 通过。
- manual_or_experience_evidence: 源码审计确认 package 不携带 absolute path/secret/catalog/query result，所有
  `/api/v1/authoring/**` route 强制 configured management auth；promotion pins package/candidate/base/source，
  rollback 在首次 live mutation 前持久化 intent，并对 third-party drift fail closed。
- deviations: 无。
- residual_risks: v1 hash 只证明内容完整性、不证明发布者身份；artifact 无 GC；一致性边界仍为单 Runtime
  进程且不支持 shared-NFS 多 writer；这些均为已冻结 non-goal。
- reused_evidence: 复用 9.5.3 已验收 workspace candidate query、publication coordinator、artifact/store、
  auth 与 query isolation 基线，并用 9.5.4 focused/affected/real-execution 证据覆盖新增路径。
- omitted_validation_and_reason: 按 elevated spec 不运行完整 reactor、Console、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal、tag/release；新增能力完全局限 Runtime API，focused + affected lane
  已达到停止条件。
- readiness: READY_FOR_SIGNOFF

## References

- architecture / glossary:
  - `docs/9.5.4/runtime-release-package-promotion-design.md`
  - `docs/architecture/runtime-and-model-lifecycle.md`
- related work items:
  - `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`
  - `docs/9.5.3/workitems/BUG-runtime-authoring-published-base-republish-and-query-race-evidence.md`

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.4/acceptance/FEATURE-runtime-release-package-production-promotion-api-signoff.md`
- blocking_items: none
- follow_up_required: yes
