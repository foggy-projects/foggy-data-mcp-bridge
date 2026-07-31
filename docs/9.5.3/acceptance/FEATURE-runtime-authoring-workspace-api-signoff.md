---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-authoring-workspace-api
status: rejected
decision: rejected
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items:
  - workspace store root has no ownership guard and startup orphan cleanup can recursively delete unrelated files
follow_up_required: yes
evidence_count: 11
assurance_level: elevated
---

# Runtime authoring workspace API 正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.3 Runtime-local authoring workspace 公共 API、持久 store、安全边界和兼容性形成
  独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-api.md`
- target_outcome: 为一个 Runtime-managed external Bundle 建立持久、不可变 revision 的 TM/QM/FSScript
  草稿工作区，提供 diff、完整 detached validation 和受治理 candidate query，且不修改 live source、
  catalog、cache 或 committed revision。
- signoff_scope: `main` 提交 `2dfbd806` 相对父提交 `0fcea8ab` 的 41 个 changed files；验收开始和测试
  结束时工作树均干净。
- critical_outcomes: path/symlink/quota、store ownership、全批次原子性、optimistic revision、source
  stale、overlay ownership、管理 auth 和 live-state isolation 均不可豁免。
- non_blocking_or_waivable_items: nested/fat-JAR 额外 packaging matrix 可保留为 scoped risk；本次没有
  owner waiver。

## Acceptance Basis

- approved delivery spec: 验收前 canonical spec 为 `READY_FOR_SIGNOFF`，assurance 保持 owner 批准的
  `elevated`。
- changed paths / diff:
  - 实际变更限于 `foggy-runtime-api`、必要的 `foggy-dataset-model-engine` 内部 candidate/detached
    边界、`docs/architecture` 和 `docs/9.5.3`，与批准范围一致。
  - 未修改 POM/Maven 依赖图、公共 Model SPI v2、Console、launcher、既有 live route 语义或数据库
    schema。
  - commit diff whitespace check 通过，未发现 debug bypass、`@Disabled` 或测试生成物进入工作树。
- test records:
  - `mvn -B -ntp -pl foggy-dataset-model-engine -DskipITs
    -Dtest=CandidateQuerySessionTest,CandidateQueryRealExecutionTest,DetachedModelAuthoringFoundationProbeTest,DetachedModelValidationSessionBuilderTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：4 classes / 22 tests，
    0 failures/errors/skips，33.671s。
  - `mvn -B -ntp -pl foggy-runtime-api -am -DskipITs
    -Dtest=RuntimeAuthoringWorkspaceStoreTest,RuntimeAuthoringWorkspaceServiceTest,RuntimeAuthoringWorkspaceRealExecutionTest,RuntimeAuthoringWorkspacesControllerTest,RuntimeCandidateQueryServiceTest,RuntimeApiAuthCodeGateTest,RuntimeBundlesControllerTest,RuntimeCapabilitiesControllerEnabledTest,RuntimeModelValidationIsolationTest,RuntimeModelsControllerCompatibilityTest
    -Dsurefire.failIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`：10 classes / 104 tests，
    0 failures/errors/skips；14-module affected reactor 成功，01:07 min。
- experience evidence: 真实 SQLite candidate query 返回草稿表数据；standard `jar:`、external filesystem、
  FSScript、重启、corruption、fault、并发、HTTP auth/envelope 和 compatibility evidence 均实际执行。
- migration / compatibility evidence: 无数据库 migration；additive DTO/capability 与既有 Runtime
  compatibility tests 通过。filesystem store 的 root ownership 缺口见下方失败项。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | external/JAR/classpath/inactive 全量事实 inventory，只有精确 managed directory 可编辑 | source type、opaque identity、Namespace binding 和 eligibility 均有正负向覆盖 | `RuntimeBundleInventoryService`；service/HTTP bundle tests | pass |
| AC-2 | 安全创建、持久恢复且不得写入或破坏 source Bundle | ID、snapshot、restart 和 drift guard 已实现；但 store root 未证明为专用目录，首次 load 会删除 root 中所有未知子项 | store `loadIfNeeded:638-664`、`cleanupOrphans:672-710` | fail |
| AC-3 | canonical path、symlink、quota、批次与 persistence fault 零部分写 | workspace resource 与 revision 级 guard、fault tests 通过 | `RuntimeAuthoringWorkspaceStoreTest` | pass |
| AC-4 | CAS、immutable revision、lease 和迟到 evidence fail closed | 两 writer 单赢家，旧 head lease，validation/query race 均有覆盖 | store/service concurrency tests | pass |
| AC-5 | pinned、排序的资源/diff 和终态 discard | restart diff、content、排序、tombstone 与不可执行状态有覆盖 | store/controller tests | pass |
| AC-6 | 生产 detached TM/QM/FSScript + external/JAR validation，live state 不变 | 全资源验证、标准 JAR dependency 和 live identity snapshots 通过 | engine probes；Runtime real execution | pass |
| AC-7 | exact validated revision 通过 candidate service 执行真实 JDBC 且不旁路权限/cache | 真实 SQLite 返回 `DRAFT-001/002`，Authorization 与 unsupported/cache guards 保持 | Runtime real execution；candidate tests | pass |
| AC-8 | selected Bundle 完整替换，其他 owner overlay 在三入口拒绝 | TM/QM/FSScript 删除不回退；save/validate/query overlay tests 通过 | engine deletion probe；service tests | pass |
| AC-9 | source/head 漂移 fail closed，任何结果不修改 live source/catalog/cache/inventory/revision | 通常路径与竞态测试通过；但当 configured store root 等于/包含 Bundle source 时，startup cleanup 可删除 source 文件，违反 live-state isolation | store `:638-687`；spec data constraint `:295-298` | fail |
| AC-10 | 所有 workspace routes 强制管理 auth，业务 Authorization 独立，既有契约兼容 | `none`、`mutations`、`management-all` 的 route/method 矩阵与 compatibility tests 通过 | auth random-port/interceptor tests；104-test lane | pass |

## Implementation Quality

- scope and changed surface: 模块边界与 non-goals 得到遵守；没有提前实现 publish、Console、Git、
  JAR binding 或高级 candidate mode。
- maintainability and duplication: candidate revision、workspace store/service/controller 分层总体清楚；
  source capability 与 overlay guard 集中，未发现测试绕过。
- error handling and edge cases: resource/revision corruption、symlink、CAS、fault 和竞态处理充分；但
  `cleanupOrphans` 把“配置根目录中的未知项”直接视为可删除 workspace orphan，没有 ownership marker、
  dedicated-root 初始化规则或与 live Bundle path 的 disjointness guard。
- contract, data and compatibility: 公共 API 为 additive 且 compatibility lane 通过；root cleanup 缺口可能
  造成不可恢复的数据删除并破坏 live Bundle，命中不可豁免持久化/数据安全红线。
- terminology and documentation: 文档没有误报 publish 等后续能力；但当前 store path 文档也没有声明
  或强制“只能使用由 Runtime 初始化的专用空目录”。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | inventory source trace + service/HTTP matrix | refreshed | pass |
| AC-2 | core-blocker | critical | create/restart tests通过；root cleanup source trace失败 | refreshed + new review | fail |
| AC-3 | core-blocker | critical | path/symlink/quota/persist-fault tests | refreshed | pass |
| AC-4 | core-blocker | critical | CAS/lease/late validation/query tests | refreshed | pass |
| AC-5 | core-blocker | major | deterministic resource/diff/discard tests | refreshed | pass |
| AC-6 | core-blocker | major | production detached external/JAR/FSScript + isolation | refreshed | pass |
| AC-7 | core-blocker | critical | real SQLite + governed candidate service | refreshed | pass |
| AC-8 | core-blocker | critical | selected deletion + three-entry overlay guard | refreshed | pass |
| AC-9 | core-blocker | critical | drift races通过；store/source overlap trace失败 | refreshed + new review | fail |
| AC-10 | core-blocker | critical | auth method/scope matrix + compatibility lane | refreshed | pass |
| store root ownership | core-blocker | critical | no marker/dedicated-root/disjointness guard；unknown children递归删除 | new review | fail |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: 126 项独立 focused/affected tests 足以证明其覆盖路径，
  但测试均使用新建专用临时目录；它们没有覆盖配置 root 已含非 workspace 文件或与 live Bundle
  重叠的情形。production source trace 确定地从 `Files.list(root)` 进入 `deleteTree(child)`，无需外部环境
  即可判定 destructive behavior。
- new_validation_that_could_change_decision: 修复后必须增加“已有目录 sentinel 不被删除”“store root
  等于/祖先/后代 live Bundle path 均 fail closed”“只清理带 ownership 证明的 staging/orphan”以及重启
  recovery tests；再重跑 store/service/Runtime affected lane。
- expensive_validation_omitted_and_reason: 未运行完整 Maven reactor、Console/Playwright、launcher、
  数据库矩阵、authority/replay/rehearsal/source-seal/tag/release/publish；spec 明确排除，且不能改变当前
  确定的源码级拒签结论。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: blocker 位于单一 filesystem store boundary，可由 focused remediation 和 affected
  Runtime lane确定。
- estimated_wall_clock_and_basis: N/A
- scope_and_prerequisites: N/A
- maximum_attempts: 1
- decision_impact: 不影响当前拒签结论。
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Failed Items

- AC-2 / store root ownership：`loadIfNeeded()` 在没有 registry 或 registry 未引用某个子项时调用
  `cleanupOrphans()`，后者对所有未知子项执行递归 `deleteTree`。配置路径没有 ownership marker、专用
  空目录门禁或非空目录拒绝规则，因此误配置可删除非 workspace 文件。
- AC-9 / live-state isolation：实现没有拒绝 workspace store root 与 selected/other live Bundle source
  的相等、祖先或后代关系。重叠时 store 初始化/清理可直接写入或删除 live source，source drift 检查只能
  事后报错，不能恢复已删除内容。

## Risks / Follow-ups

- `core-blocker`：建立 focused BUG delivery spec，冻结 root ownership marker、首次初始化、允许清理的
  精确文件名/identity、Bundle path disjointness 和向后兼容规则；修复前不得启用 workspace API。
- `scoped-risk`：standard `jar:` fixture 不代表全部 nested/fat-JAR packaging。
- `scoped-risk`：store 只支持单 Runtime 进程；共享 NFS/多进程 writer 不支持。
- `scoped-risk`：其他只读 dependency 被绕过 watcher 修改时可能无法立即观察。
- `out-of-scope`：publish/apply/rollback/rebase、release package、Git、Console、JAR 多 Namespace 与高级
  candidate query 仍未交付。

## Final Decision

- decision: rejected
- rationale: AC-2、AC-9 和不可豁免的 store ownership/data safety guard 未满足。即使 126 项 focused/
  affected tests 全部通过，配置 root 的确定性递归删除行为仍可能造成不可恢复的非 workspace/live Bundle
  数据损失；该风险不能 waiver 为普通残余风险。
- blocking_items:
  - workspace store 只能初始化/清理由显式 ownership 证明的专用目录和内部项，未知文件必须 fail closed，
    绝不能递归删除。
  - store root 必须与全部 live/managed Bundle filesystem source 做相等、祖先和后代冲突检查；任何重叠在
    首次写入前稳定拒绝。
- follow_up_owner_and_due: implementation owner；建立 remediation BUG spec，修复后刷新失败项及直接
  affected tests，日期待 owner 确认。

## Signoff Marker

- acceptance_status: rejected
- acceptance_decision: rejected
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-api-signoff.md`
- blocking_items: AC-2/AC-9 store root ownership and destructive orphan cleanup
- follow_up_required: yes
