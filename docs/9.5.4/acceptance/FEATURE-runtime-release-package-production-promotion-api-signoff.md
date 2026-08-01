---
acceptance_scope: feature
version: 9.5.4
target: FEATURE-runtime-release-package-production-promotion-api
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items: []
follow_up_required: yes
evidence_count: 12
assurance_level: elevated
---

# Release package 与 production promotion API 正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对当前 `main` 未提交工作树中的 9.5.4 Runtime production promotion API 形成独立正式结论。

## Background

- delivery_spec: `docs/9.5.4/workitems/FEATURE-runtime-release-package-production-promotion-api.md`
- target_outcome: deterministic package 跨 Runtime 搬运，在生产重验/query 后 exact apply，并可 pinned rollback
  到直接前一 base。
- signoff_scope: 当前工作树中 `foggy-runtime-api` 生产/测试增量及 architecture/dev-guide/9.5.4 文档。
- critical_outcomes: package integrity/zero mutation、production opt-in/auth、imported immutability/revalidation、exact
  apply、durable rollback/forward recovery、drift zero overwrite、兼容性。
- non_blocking_or_waivable_items: 无签名/KMS、无 GC、只支持一步 rollback、单进程/非 shared-NFS writer。

## Acceptance Basis

- approved delivery spec: canonical spec 为 `READY_FOR_SIGNOFF`，assurance level 为 `elevated`。
- changed paths / diff: 只修改 `foggy-runtime-api` 与 docs；没有 Engine/SPI、Console、launcher、POM、依赖或
  数据库 schema 改动。
- test records:
  - compile：`mvn -B -ntp -pl foggy-runtime-api -am -DskipTests compile`，BUILD SUCCESS。
  - 独立 focused lane：5 classes / 92 tests，0 failures/errors/skips。
  - 独立 affected lane：33 classes / 231 tests，0 failures/errors/skips。
- experience evidence: 完整 status/diff/changed-path 审计、tracked `git diff --check`、全部 untracked 逐文件
  no-index check 通过；源码审计覆盖 auth、secret、identity、rollback durability 与 additive JSON compatibility。
- migration / compatibility evidence: default promotion off；workspace/publication JSON 仅 additive nullable fields；
  restart/legacy store、capability off/on、既有 Runtime API affected tests 全绿。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | exact validated export、deterministic、安全 package | exportedAt 不入 identity，排序+length delimiter hash，只含安全 provenance 与 TM/QM/FSScript | package 4 tests；service review | pass |
| AC-2 | 全量 package 校验且失败零 mutation | schema/hash/revision/path/type/UTF-8/case/quota/overlay 在 create 前完成 | tamper/invalid/zero-mutation tests | pass |
| AC-3 | opt-in + explicit target 原子 import，provenance restart 可读 | target current base + exact candidate 单 workspace，releaseImport 持久化 | package/store tests；source review | pass |
| AC-4 | imported 不可编辑且必须生产重验 | save/delete guards；开发 validation 不继承，生产 validate 后才 query/apply | package + real SQLite tests | pass |
| AC-5 | exact apply，普通 publish 不可绕过，drift 零 mutation | request pins package/candidate/base/source；promotion mode server-side blocks `/publish` | publish tests；preflight review | pass |
| AC-6 | apply 后 artifact/source/registry/catalog/live query 收敛 | 复用 publication coordinator，真实 SQLite live query 读取 package candidate | real execution + recovery tests | pass |
| AC-7 | exact rollback 且首次 mutation 前 durable intent | package/candidate/attempt/current catalog pin，workspace 先进入 `ROLLING_BACK` | rollback service/store tests | pass |
| AC-8 | 恢复直接前一 base，candidate artifact 保留 | registry/source/full refresh 收敛，workspace `ROLLED_BACK`，真实 query 回到 base | real SQLite rollback；service tests | pass |
| AC-9 | 失败前向恢复；未知转 required；third-party drift 零覆盖 | 自动 forward recovery、pinned explicit recover、未知 `ROLLBACK_REQUIRED`、漂移 preflight 拒绝 | rollback fault/drift tests | pass |
| AC-10 | restart 不自动 mutation；共享单赢家边界 | `ROLLING_BACK` restart 仅转 required；publication lock 复用于 apply/rollback/Bundle mutation | store 18 tests；9.5.3 race evidence | pass |
| AC-11 | capability/auth/envelope/redaction/旧行为兼容 | 所有 `/authoring/**` 强制管理 auth，capability off/on，默认 publish 行为不变 | auth 15 + capability 51；affected lane | pass |
| AC-12 | focused/affected/real execution/diff 与路径边界 | compile、92 focused、231 affected、whitespace/changed paths 全部通过 | 独立重跑与工作树审计 | pass |

## Implementation Quality

- scope and changed surface: Controller/DTO、package service、workspace store/service、publication coordinator 与
  artifact evidence 职责符合现有 Runtime API 分层；无禁止模块改动。
- maintainability and duplication: apply/rollback 复用既有 publication lock、artifact、registry、refresh 与 source
  classifier，没有建立第二套 catalog 或 Engine 路径；未见 debug/TODO/测试绕过。
- error handling and edge cases: tamper、invalid UTF-8、case/path/quota、disabled mode、stale base、rollback fault、
  restart 与 third-party drift 均 fail closed；mutation 不自动 retry。
- contract, data and compatibility: additive route/config/DTO/state/JSON；旧 record null 兼容，默认关闭保持开发
  Runtime publish 语义。
- terminology and documentation: architecture、dev guide、design、routes、states 与 error phase 使用一致。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1/2 | core-blocker | critical | package identity/tamper/limits/zero mutation | new/rerun | pass |
| AC-3/4 | core-blocker | critical | atomic import/restart/immutability/production validation | new/rerun | pass |
| AC-5/6 | core-blocker | critical | exact promotion + real SQLite apply/live query/recovery | new + reused | pass |
| AC-7/8 | core-blocker | critical | durable rollback intent + exact base convergence | new/rerun | pass |
| AC-9 | core-blocker | critical | forward recovery/required/pinned recovery/drift matrix | new/rerun | pass |
| AC-10 | core-blocker | critical | restart reconciliation/shared lock/race baseline | new + reused | pass |
| AC-11 | core-blocker | critical | random-port auth/capability/default compatibility | new/rerun | pass |
| AC-12 | core-blocker | major | compile, 92 focused, 231 affected, diff/untracked checks | new/rerun | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 新增公共管理 API 和生产 mutation 的完整性、权限、真实数据结果、
  failure recovery、restart 与 compatibility 均有 focused/affected 证据；真实 SQLite 闭环验证最终 live 结果，
  已达到 spec 停止条件。
- new_validation_that_could_change_decision: none within approved scope。
- expensive_validation_omitted_and_reason: 完整 reactor、Console/Playwright、launcher、DB matrix、authority/replay/
  rehearsal/source-seal、tag/release 均被 spec 排除；改动局限 Runtime API，继续扩展不会改变当前判定。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 当前不是 tag/release 候选，focused 与 affected evidence 已覆盖全部公共契约和真实结果。
- estimated_wall_clock_and_basis: N/A
- scope_and_prerequisites: N/A
- maximum_attempts: 1
- decision_impact: N/A
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Failed Items

- none

## Risks / Follow-ups

- `scoped-risk`: package v1 hash 只证明完整性，不证明发布者身份；management auth-code 操作者是信任根。
- `scoped-risk`: immutable artifact 无 GC；rollback 仅一步；一致性限单 Runtime 进程、非 shared-NFS writer。
- `out-of-scope`: 签名/KMS、审批/RBAC、完整 history、任意 rollback、package registry、Git/JAR、跨 Runtime
  orchestration。
- `follow-up`: Console 只能在本 API accepted 后实现 capability-aware import/apply/rollback UX。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-12 全部有 elevated、真实且可追溯的通过证据；无 core blocker、无 deviation、无需
  owner waiver。
- blocking_items: none
- follow_up_owner_and_due: Runtime Console owner；按已批准 9.5.4 Console workitem 接入，不扩大 API scope。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record: `docs/9.5.4/acceptance/FEATURE-runtime-release-package-production-promotion-api-signoff.md`
- blocking_items: none
- follow_up_required: yes
