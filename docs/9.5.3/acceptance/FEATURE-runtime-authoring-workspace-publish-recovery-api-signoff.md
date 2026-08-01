---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-authoring-workspace-publish-recovery-api
status: rejected
decision: rejected
signed_off_by: Codex independent reviewer
signed_off_at: 2026-08-01
reviewed_by: N/A
blocking_items:
  - published Bundle cannot complete a subsequent workspace publication
  - AC-9 concurrent live-query observation evidence is missing
follow_up_required: yes
evidence_count: 12
assurance_level: elevated
---

# Runtime authoring workspace publish 与失败恢复 API 正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.3 Runtime authoring workspace publish/recovery API 的当前未提交工作树形成独立、
  可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/FEATURE-runtime-authoring-workspace-publish-recovery-api.md`
- target_outcome: 把 exact validated candidate 发布为 immutable Runtime-managed Bundle artifact，完成
  full-Namespace refresh，并在失败或中断后确定恢复旧 live revision。
- signoff_scope: `main` base HEAD `80fe0fb2` 上的全部 tracked 与 untracked 实际改动。
- critical_outcomes: exact identity、immutable artifact、registry/source/catalog convergence、自动/显式恢复、
  third-party drift zero-overwrite、并发 old/new/not-current、管理 auth 与 secret guard 均不可豁免。
- non_blocking_or_waivable_items: artifact GC、单进程/非 shared-NFS 前提；本次没有 owner waiver。

## Acceptance Basis

- approved delivery spec: canonical spec 状态为 `READY_FOR_SIGNOFF`，assurance level 为 `elevated`。
- changed paths / diff: 生产改动只位于 `foggy-runtime-api`，另含 Runtime/model lifecycle 架构、9.5.3
  README 与 canonical spec；没有 Engine/Model SPI、Console、launcher、POM、依赖图或数据库 schema 改动。
- test records:
  - 独立 focused lane：7 classes / 57 tests，0 failures/errors/skips，54.634s。
  - 独立 affected lane：14 classes / 146 tests，0 failures/errors/skips，1m02s。
  - affected lane 中真实 filesystem + SQLite：4/4，覆盖首次成功发布、自动恢复与显式恢复。
- experience evidence: `git status --short`、完整 tracked diff、全部 9 个 untracked 文件及逐文件
  `git diff --no-index --check /dev/null <file>` 已检查；tracked `git diff --check` 通过。
- migration / compatibility evidence: additive DTO/state/registry fields、startup PUBLISHING reconciliation、
  random-port auth 与既有 Bundle/resource/model compatibility tests 均通过。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | exact preflight 且拒绝时零 live/artifact mutation | base/candidate/source pin、validated/source/overlay checks 在 durable/live mutation 前执行 | service source review；preflight zero-mutation test | pass |
| AC-2 | canonical immutable artifact、ownership/manifest/hash 原子提交 | 独立 owned root、只允许 TM/QM/FSScript、hash/manifest/symlink/foreign fail closed | publication store 6 tests；artifact source review | pass |
| AC-3 | durable intent、source/registry/full refresh 收敛到 `PUBLISHED` | 首次发布得到 immutable `watch=false` artifact，真实 live query 读取 candidate | real SQLite success；publish service success | pass |
| AC-4 | `PUBLISHED` 终态只读；后续 workspace 是再次修改入口 | 终态 guards 与可读资源/diff成立，也能创建下一 workspace；但下一 workspace 的 publish 被 immutable-source preflight 明确拒绝 | publication service lines 504-509；workspace service lines 738-742；success test only asserts creation | fail |
| AC-5 | immutable direct save 拒绝；Bundle mutation 不删 artifact | `RESOURCE_BUNDLE_IMMUTABLE`、mutable compatibility、replace/remove retention 均有测试 | resource/Bundle controller tests | pass |
| AC-6 | source/registry/refresh/final evidence fault 自动恢复 base | registry、refresh、final evidence fault 均恢复 source/registry/catalog，原 publish 返回失败 | publish service tests；real SQLite recovery | pass |
| AC-7 | restart 收敛、pinned explicit recovery、幂等恢复 | `PUBLISHING` restart 转 `RECOVERY_REQUIRED`，attempt/candidate pin 与重复 recover 有覆盖 | workspace store + publish service tests | pass |
| AC-8 | drift/corruption/recovery failure 保留现场且 unsafe | third-party live drift、artifact/manifest/attempt corruption fail closed，`safeToAutoRepair=false` | publication/store negative tests；source review | pass |
| AC-9 | publication/source mutation 单赢家；并发 live query 仅 old/new/not-current | publish 单赢家、Bundle/resource mutation 共用 lock；没有切换窗口并发 live-query 自动化证据 | concurrent publish + shared-lock tests；test inventory review | unknown |
| AC-10 | 管理 auth、稳定 envelope/redaction 与旧 API compatibility | 新 routes 纳入全部 workspace route gate，controller 不泄露 suppressed cause；affected lane 全绿 | auth/controller/compatibility tests | pass |

## Implementation Quality

- scope and changed surface: 模块边界符合 spec；没有越界修改 Engine、Console、launcher 或依赖图。
- maintainability and duplication: publication coordinator、artifact store、workspace store、registry 与 shared lock
  职责基本清晰，未见 debug/TODO 或测试跳过。
- error handling and edge cases: durable attempt、final-persist fault、third-party drift、foreign/symlink/hash/schema
  corruption 均 fail closed；但 immutable publication 被当作不可再次发布的 target，切断正常后续修改闭环。
- contract, data and compatibility: 新 route/DTO/state 为 additive；registry 兼容构造与旧字段默认值保留。
- terminology and documentation: 架构文档把“用户仍可从当前 published Bundle 创建新的 workspace”描述为修改
  入口，但没有披露该 workspace 无法再次 publish，文档与实现语义不完整一致。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | critical | exact preflight + zero mutation | new/rerun | pass |
| AC-2 | core-blocker | critical | owned artifact/hash/foreign/symlink matrix | new/rerun | pass |
| AC-3 | core-blocker | critical | durable first publish + real SQLite live query | new/rerun | pass |
| AC-4 | core-blocker | critical | next workspace creation succeeds but immutable-source publish is rejected | source review | fail |
| AC-5 | core-blocker | critical | immutable save rejection + artifact retention | new/rerun | pass |
| AC-6 | core-blocker | critical | automatic recovery fault matrix | new/rerun | pass |
| AC-7 | core-blocker | critical | restart/pinned/idempotent explicit recovery | new/rerun | pass |
| AC-8 | core-blocker | critical | drift/corruption zero-overwrite evidence | new/rerun | pass |
| AC-9 | core-blocker | critical | single-winner/shared lock present；live-query race evidence absent | new/rerun | unknown |
| AC-10 | core-blocker | critical | auth/envelope/redaction/compatibility | new/rerun | pass |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not: focused/affected evidence充分证明首次 publish、immutable guard、
  recovery、auth 与兼容性；源码可确定第二次 publish 必然被拒绝，因此无需修改测试或实现即可判定核心契约
  未满足。AC-9 的冻结并发 live-query 结果仍缺少直接证据。
- new_validation_that_could_change_decision: 修复后需新增“publish → 新 workspace → 修改/validate → 第二次
  publish → new live query”真实 SQLite 回归，以及 source switch/refresh 窗口的并发 live-query old/new/
  not-current 断言。
- expensive_validation_omitted_and_reason: 未运行完整 reactor、Console/Playwright、launcher、数据库矩阵、
  authority/replay/rehearsal/source-seal、tag/release/production publish；canonical spec 明确禁止，且不会改变
  当前已由源码确定的拒签结论。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 当前不是最终 release candidate，且 blocker 位于 focused Runtime lifecycle contract。
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

- `core-blocker`：首次 publish 后 registry record 为 immutable publication；后续 workspace 虽能创建，但
  `preflightPublication` 与 `requireMutableBaseRecord` 都拒绝 immutable source，无法完成第二次发布。该行为
  与“创建后续 workspace 是再次修改入口”及 Console 开发闭环不一致。
- `core-blocker`：AC-9 要求 publication/source mutation/query 的受控并发证据；当前只有 publish 单赢家与
  Bundle/resource lock tests，没有 live-query 切换窗口 old/new/not-current 断言。

## Risks / Follow-ups

- `scoped-risk`：published artifact 当前不做 GC；引用中的 artifact 必须继续保留。
- `scoped-risk`：一致性只承诺单 Runtime 进程、非 shared-NFS writer；外部 filesystem tampering 在后续操作
  才由 hash/manifest 检出。
- `process-gap`：artifact prepare 中断可能遗留 `.staging-*`，当前 root validation 会 fail closed，但没有
  ownership-proven restart reconciliation；不会产生半发布，可能需要 operator 处理后才能再次 publish。
- `out-of-scope`：成功发布后的历史 rollback、release package、生产 promotion、Git、JAR 多 Namespace、
  Console UI 与高级 candidate query 仍由后续 workitem 交付。

## Final Decision

- decision: rejected
- rationale: AC-4 的后续修改/发布闭环存在确定实现缺陷，且 AC-9 的不可豁免并发 live-query 结果缺少
  elevated 必需证据；没有 owner waiver，二者不能降级为普通残余风险。
- blocking_items: subsequent immutable-base publication；AC-9 concurrent live-query evidence。
- follow_up_owner_and_due: Runtime API owner；在同一 canonical scope 内修复并补齐 focused regression 后重新
  执行独立 signoff，Console publish 接入不得基于当前未签收 API 冻结。

## Signoff Marker

- acceptance_status: rejected
- acceptance_decision: rejected
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-08-01
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-authoring-workspace-publish-recovery-api-signoff.md`
- blocking_items: subsequent immutable-base publication；AC-9 concurrent live-query evidence
- follow_up_required: yes
