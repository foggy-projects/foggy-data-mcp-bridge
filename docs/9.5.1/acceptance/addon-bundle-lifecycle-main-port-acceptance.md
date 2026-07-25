---
acceptance_scope: feature
version: 9.5.1
target: addon-bundle-lifecycle-main-port
status: signed-off
decision: accepted
signed_off_by: independent-reviewer-codex
signed_off_at: 2026-07-25
reviewed_by: repository-owner-via-user-request
blocking_items: []
follow_up_required: no
evidence_count: 7
assurance_level: standard
---

# Add-on 多 Bundle 生命周期能力迁回 main 验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 v910beta 原型归档及其适用能力迁回 main 的实现形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `../workitems/FEATURE-addon-bundle-lifecycle-main-port.md`
- target_outcome: 在 main-after-9.5.0 模块边界上补齐同 namespace 多 Bundle 来源治理和
  Runtime Bundle/registry 一致性，不恢复已删除的 legacy 身份。
- signoff_scope: provenance、同名外部 Bundle 原子 replace、Runtime source/registry
  失败恢复、兼容回归和批准范围内的文档。
- critical_outcomes:
  - 来源准确且未知来源显式标记。
  - replace/remove 不影响同 namespace 的兄弟 Bundle。
  - registry 持久化失败恢复 source；恢复失败使用稳定错误。
  - 保持显式 validate/refresh 和既有 Catalog admission 语义。
- non_blocking_or_waivable_items: 未执行批准范围外的 release authority、外部数据库矩阵、
  source seal、artifact promotion 和远程 CI。

## Acceptance Basis

- approved delivery spec:
  `../workitems/FEATURE-addon-bundle-lifecycle-main-port.md`
- changed paths / diff: `foggy-dataset-model-engine`、`foggy-fsscript`、
  `foggy-runtime-api` 和 `docs/9.5.1` 的当前 feature diff。
- test records: 三个受影响模块的全量或聚焦 Maven 测试、SQLite 集成测试及
  `git diff --check` 的实际成功输出。
- experience evidence: N/A；本交付无 UI 或人工操作链路。
- migration / compatibility evidence: registry JSON schema 未升版；保留 `ModelProvenance`
  七参数构造器和 `stageQueryModel` 五参数兼容 overload；未恢复 legacy Maven 坐标或 Java 包。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 原型有可追溯归档，旧 worktree 干净 | 归档提交完成，旧 worktree 无修改 | commit `4a3a3ef4`；`git status --short` | pass |
| AC-2 | TM/QM 来源可选且 list/describe 显式 known/unknown | provenance 扩展到 TM、QM、synthetic QM，并暴露来源 | engine 单测/集成测试；runtime describe 测试 | pass |
| AC-3 | 同 namespace 多 Bundle 隔离 | 发布、替换、删除一个 Bundle 时兄弟模型继续可用 | `CatalogRefreshQueryIT` 3/3 | pass |
| AC-4 | 同名外部 Bundle 原子 replace 并正确处理失败 | 候选/watcher 失败恢复旧状态且 revision 不推进；listener 失败保留已提交 source | `BundleSourceCommitOrderingTest` 6/6；fsscript 406/406 | pass |
| AC-5 | registry 失败恢复 source/内存记录，回滚失败稳定报错 | add/update/remove 和 registry service 均实现事务性恢复 | runtime 聚焦测试 10/10；runtime-api 144/144 | pass |
| AC-6 | 冲突检测、显式 refresh、配置 Bundle 不可变保持 | 复用既有冲突检测和生命周期边界，兼容测试通过 | runtime-api 全量测试；engine 全量测试 | pass |
| AC-7 | 聚焦和受影响模块验证通过，diff 干净 | 所有约定命令成功，未发现 whitespace error | 下列 Evidence Matrix | pass |

## Implementation Quality

- scope and changed surface: 改动严格位于批准的三个模块和 9.5.1 交付文档，没有恢复已删除模块。
- maintainability and duplication: 复用现有 `RuntimeBundleModelConflictDetector`；来源统一存储在
  `ModelProvenance`；replace 作为单一 context 操作提供，未复制 admission 服务。
- error handling and edge cases: 覆盖 watcher 预提交失败、listener 提交后失败、registry
  磁盘失败、内存恢复以及 source rollback 失败。
- contract, data and compatibility: 仅扩展可空响应字段，不改变 registry schema、namespace
  迁移、datasource binding 或 query API 语义。
- terminology and documentation: delivery spec、实现命名和测试均使用 Bundle source、
  registry、provenance 与 admission/convergence 的既有术语。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | scoped-risk | minor | archive commit `4a3a3ef4`，旧 worktree clean | new | pass |
| AC-2 | core-blocker | major | engine 3159 tests；聚焦 unit 2 + IT 3；runtime describe 测试 | new | pass |
| AC-3 | core-blocker | major | `CatalogRefreshQueryIT` 多 Bundle 发布/替换/删除场景 | new | pass |
| AC-4 | core-blocker | major | fsscript 406 tests；最终聚焦 6 tests | new | pass |
| AC-5 | core-blocker | major | runtime-api 144 tests；最终聚焦 10 tests | new | pass |
| AC-6 | scoped-risk | major | engine/runtime 全量回归及兼容 overload 测试 | new | pass |
| AC-7 | process-gap | major | Maven BUILD SUCCESS；`git diff --check` | new | pass |

## Evidence Sufficiency

- assurance_level: standard
- why_existing_evidence_is_sufficient_or_not: 核心失败路径均有直接失败注入；同 namespace
  隔离有 SQLite 集成测试；三个受影响模块均完成全量或批准的聚焦回归。最终新增内容仅为测试
  断言时，已重跑对应聚焦测试，生产代码证据未失效。
- new_validation_that_could_change_decision: 当前无必需补充验证；只有发现新的跨模块公共 SPI
  变更或 release 范围扩大时才需升级 assurance。
- expensive_validation_omitted_and_reason: 外部数据库矩阵、release authority、source seal、
  artifact promotion 和远程 CI 均为批准的 non-goal，且不会提高本次 source/registry
  一致性结论的关键确定性。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 无 release/tag/publish 或广泛 SPI 变更。
- estimated_wall_clock_and_basis: N/A
- scope_and_prerequisites: N/A
- maximum_attempts: 1
- decision_impact: 不影响本次标准 assurance 验收。
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | N/A | N/A | N/A | source/registry 一致性、兄弟 Bundle 隔离、来源准确性均已验证 | none |

## Failed Items

- none

## Risks / Follow-ups

- source 提交后 Spring listener 失败仍采用 main 既有 admission/convergence 语义；已有直接测试，
  不构成阻断，也无需本交付追加 follow-up。

## Final Decision

- decision: accepted
- rationale: AC-1 至 AC-7 全部满足；核心状态一致性、隔离和来源准确性有直接运行证据；
  未发现范围漂移、legacy 恢复、数据迁移或未处理阻断。
- blocking_items: none
- follow_up_owner_and_due: none

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: independent-reviewer-codex
- signed_off_at: 2026-07-25
- acceptance_record:
  `docs/9.5.1/acceptance/addon-bundle-lifecycle-main-port-acceptance.md`
- blocking_items: none
- follow_up_required: no
