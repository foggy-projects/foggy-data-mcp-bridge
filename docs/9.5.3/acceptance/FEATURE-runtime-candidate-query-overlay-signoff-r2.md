---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-candidate-query-overlay
status: signed-off
decision: accepted
signed_off_by: Codex independent reviewer
signed_off_at: 2026-07-31
reviewed_by: N/A
blocking_items: []
follow_up_required: no
evidence_count: 14
assurance_level: elevated
---

# Runtime candidate-query overlay 正式验收 R2

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对首次拒签缺口修复后的 9.5.3 request-local candidate query 形成新的独立正式结论。

## Background

- delivery_specs:
  - `docs/9.5.3/workitems/FEATURE-runtime-candidate-query-overlay.md`
  - `docs/9.5.3/workitems/BUG-runtime-candidate-query-fail-closed-guards.md`
- prior_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-candidate-query-overlay-signoff.md`，保持 rejected 历史不变。
- target_outcome: 一个不可变草稿 Bundle revision 通过 request-local catalog 完成受治理的普通 JDBC
  validate/query，不回退 live model，也不改变 live catalog/cache/source state。
- signoff_scope: 当前 `main` 工作树中的 tracked 改动和全部 untracked 文件。
- critical_outcomes: phase-specific permission、selected Bundle provenance、JDBC-only backend、candidate/live
  identity、cache isolation、source stale guard 和 request-local cleanup 均不可豁免。
- non_blocking_or_waivable_items: unsupported advanced modes 是明确 non-goal；本次没有 waiver。

## Acceptance Basis

- approved delivery specs: 原 feature 与 remediation BUG 验收前均为 `READY_FOR_SIGNOFF`；assurance level
  保持 owner 批准的 `elevated`。
- changed paths / diff:
  - 实际变更位于 `foggy-dataset-model-engine`、`foggy-runtime-api`、`docs/architecture` 和
    `docs/9.5.3`，与两个 spec 的合并范围一致。
  - 未发现 POM、Model SPI v2、Controller/route、Console、launcher、数据库迁移或公共 REST DTO 改动。
  - 当前工作树的 tracked diff 和全部 untracked 文件均纳入源码与 whitespace 审计。
- test records:
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=CandidateQuerySessionTest,CandidateQueryRealExecutionTest,DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest,QueryFacadeCatalogIdentityTest,SemanticRequestContextTest,FieldAccessPermissionStepTest,ModelPermissionServiceTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    8 classes / 84 tests，0 failures/errors/skips，30.769s。
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=DbModelAutoConfigurationTest,QueryFacadeImplTest,PhysicalColumnPermissionIT,SemanticQueryServiceV3ValidatePipelineTest,QueryCacheProviderTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    5 classes / 52 tests，0 failures/errors/skips，29.890s。
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeCandidateQueryServiceTest,RuntimeModelValidationIsolationTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：
    5 classes / 64 tests，0 failures/errors/skips；14-module affected reactor 成功，58.454s。
- experience evidence: 真实 SQLite、external filesystem 与动态标准 `jar:` fixture 均走 production
  detached loader/session；本 workitem 没有 UI 或公共 endpoint。
- migration / compatibility evidence: affected engine/Runtime lanes 通过；无持久化、配置、部署顺序或
  公共契约迁移。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| BUG AC-1 | validate/execute 固定各自 permission action | caller 故意注入相反 action，session 分别覆盖为 `VALIDATE`/`EXECUTE` | factory phase mapping；真实 SQLite action-sensitive resolver | pass |
| BUG AC-2 | registry record 与实际 managed external Bundle fail-closed 绑定 | enabled、name、Namespace、definition/base/root real path 全部匹配后才 open | Runtime service source trace；relative/absolute 正向及 configured/JAR/absent/mismatch/symlink 负向测试 | pass |
| BUG AC-3 | 非 JDBC resolution 稳定 unsupported | resolved model 不是 `JdbcQueryModelImpl` 时 validate/execute 均在 semantic 调用前拒绝 | candidate unit test；semantic service 零调用 | pass |
| BUG AC-4 | close 幂等清理 request-local authority | catalog、TM/QM loader、FSScript loader/cache、source cache、validation context 清理，关闭后禁止复用 | 真实 detached load/close probe 与 live-state snapshots | pass |
| BUG AC-5 | 原 feature 证据保持有效 | SQLite/JAR、权限、stale、cache/live isolation 与 affected lanes 全部刷新通过 | 84 + 52 + 64 项独立重跑 | pass |

## Implementation Quality

- scope and changed surface: candidate port、detached execution pin、Runtime internal orchestration及直接测试位于
  正确模块；没有新增 route、公共 SPI、依赖图或 UI。
- maintainability and duplication: phase action、backend guard、source provenance 和 cleanup 各自集中在单一
  边界；未发现 debug、测试绕过、无解释 TODO 或重复实现。
- error handling and edge cases: null/disabled/absent、Namespace/name/path/symlink、non-JDBC、stale、close
  failure与重复 close 均按稳定 fail-closed 行为处理。
- contract, data and compatibility: live query 默认路径仍使用 live loader/context；candidate 只有显式 pin
  时启用 request-local Bundle view并关闭 cache/pre-aggregation/hybrid。
- terminology and documentation: 文档继续把本交付描述为 internal primitive，未误报 workspace、publish、
  Git 或 JAR 多 Namespace 已完成。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | content-addressed revision 与 source/content drift tests | refreshed | pass |
| AC-2 | core-blocker | major | detached production resolution、same-instance catalog pin | refreshed | pass |
| AC-3 | core-blocker | major | live 同名 QM 指向无效表，真实 SQLite 仍返回草稿结果 | refreshed | pass |
| AC-4 | core-blocker | major | external filesystem 与真实标准 `jar:` TM/FSScript 查询 | refreshed | pass |
| AC-5 | core-blocker | critical | opposite-action resolver、allow/deny/row/field/physical tests | refreshed + remediation | pass |
| AC-6 | core-blocker | critical | cache spy 为 0；成功/失败/close 后 live snapshots 不变 | refreshed | pass |
| AC-7 | core-blocker | critical | open/call 前后 source/content stale guards | refreshed | pass |
| AC-8 | core-blocker | critical | record/live provenance guard及其他 owner overlay 拒绝 | refreshed + remediation | pass |
| AC-9 | core-blocker | major | metadata/phase、non-JDBC stable unsupported、Runtime compatibility | refreshed + remediation | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 首次拒签未受影响的 AC-1～4、AC-6～7 源码结论仍成立，
  且本轮 200 项 focused/affected tests 已在最终实现上刷新；AC-5、AC-8、AC-9 与 cleanup 均新增直接
  负向选择和生产调用链证据，足以判断不可豁免 guards。
- new_validation_that_could_change_decision: none；批准的 must-pass 与 changed surface 已覆盖。
- expensive_validation_omitted_and_reason: 未运行完整 reactor、Console/Playwright、launcher package、
  数据库矩阵、authority/replay/rehearsal/source-seal/tag/release/publish；两个 spec 明确排除，且不会改变
  当前 internal primitive 的签收判断。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 当前不是最终 release candidate，focused/affected evidence 足以覆盖核心结果。
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

- `scoped-risk`：动态 JAR fixture 证明标准 `jar:` Resource，不代表全部 nested/fat-JAR packaging；装配
  形态变化时补对应测试。
- `scoped-risk`：source revision 覆盖 Runtime 已知 committed mutation；绕过 registry/watcher 的 live
  filesystem drift 继续受既有部署约束和后续 workspace revalidate 契约管理。
- `out-of-scope`：workspace store/resource/revision、publish/rollback、Git、Console、JAR 多 Namespace
  及被显式拒绝的高级查询模式仍是后续 workitem，不是本 primitive 的产品能力。

## Final Decision

- decision: accepted
- rationale: 原 feature AC-1 至 AC-9 与 remediation BUG AC-1 至 AC-5 全部有充分证据；首次拒签的
  permission action、selected-source provenance、non-JDBC backend 与 cleanup 缺口均已修复并形成长期
  回归保护。没有阻断项或需要 waiver 的风险。
- blocking_items: none
- follow_up_owner_and_due: none；后续能力按 9.5.3 路线建立独立 delivery spec。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-candidate-query-overlay-signoff-r2.md`
- blocking_items: none
- follow_up_required: no
