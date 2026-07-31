---
acceptance_scope: feature
version: 9.5.3
target: FEATURE-runtime-candidate-query-overlay
status: rejected
decision: rejected
signed_off_by: Codex independent reviewer
signed_off_at: 2026-07-31
reviewed_by: N/A
blocking_items:
  - AC-5: candidate validate/execute do not pin their matching permission actions
  - AC-8: selected Bundle managed/external provenance is not verified against the live Bundle
  - AC-9: non-JDBC candidate models are not rejected by the ordinary-JDBC mode guard
follow_up_required: yes
evidence_count: 10
assurance_level: elevated
---

# Runtime candidate-query overlay 正式验收

## Document Purpose

- intended_for: signoff-owner / reviewer / project-root-session
- purpose: 对 9.5.3 request-local candidate query 实现形成独立、可复核的正式签收结论。

## Background

- delivery_spec:
  `docs/9.5.3/workitems/FEATURE-runtime-candidate-query-overlay.md`
- target_outcome: 让一个不可变草稿 Bundle revision 通过 request-local catalog 完成受治理的普通
  JDBC validate/query，不回退 live model，也不改变 live catalog/cache/source state。
- signoff_scope: 当前 `main` 工作树中的 tracked 改动和全部 untracked 文件。
- critical_outcomes: AC-1 至 AC-9；candidate/live identity、权限 fail-closed、cache isolation、source
  stale guard 和 overlay ownership 不可豁免。
- non_blocking_or_waivable_items: pivot、Compose/CTE、Semantic SQL、memory-grid、synthetic member 等
  高级模式是明确 non-goal，但必须稳定拒绝。

## Acceptance Basis

- approved delivery spec: canonical spec 验收前状态为 `READY_FOR_SIGNOFF`，assurance 为 `elevated`。
- changed paths / diff:
  - 实际变更位于 `foggy-dataset-model-engine`、`foggy-runtime-api`、`docs/architecture` 和
    `docs/9.5.3`；与声明模块一致。
  - 未发现 POM、Model SPI v2、Controller/route、Console、launcher、数据库迁移或公共 REST DTO
    改动。
  - tracked `git diff --check` 通过；验收记录创建前的全部 untracked 文件逐项
    `git diff --no-index --check /dev/null <file>` 无 whitespace 诊断；未发现高置信 secret pattern。
- test records:
  - engine focused/permission lane：8 classes / 82 tests，0 failures/errors/skips，30.856s。
  - engine affected compatibility lane：5 classes / 52 tests，0 failures/errors/skips，29.602s。
  - Runtime affected lane：5 classes / 63 tests，0 failures/errors/skips，58.457s。
  - 以上是当前源码之后生成、且 surefire XML 可核对的实现会话证据；签收审计发现的三个缺口
    没有对应负向断言，重复运行现有选择不会改变拒绝结论，故未为扩大通过数量再次重跑。
- experience evidence: 不适用；本 workitem 没有 UI 或公共 endpoint。
- migration / compatibility evidence: affected compile 和既有 engine/Runtime compatibility tests 通过；
  无持久化或部署顺序变化。

## Contract Conformance

| Item | Expected | Delivered | Evidence | Result |
|---|---|---|---|---|
| AC-1 | 内容寻址 revision、base/Namespace/Bundle identity 与 content stale guard | SHA-256 覆盖 TM/QM/FSScript 相对路径和字节；调用前后重算 | `DefaultCandidateQueryFactory:348-430`；session tests | pass |
| AC-2 | detached production loader、同一 request-local resolution/model | session 缓存 `CatalogResolution`，semantic 与 facade 使用显式 pin 和 Bundle view | factory `:182-225`；semantic/facade integration；same-instance test | pass |
| AC-3 | SQLite 结果来自草稿 overlay，不重新加载同名 live QM | live fixture 指向不存在表，草稿查询返回 `fact_order` 的 `COMPLETED` 行 | `CandidateQueryRealExecutionTest:129-170,362-436` | pass |
| AC-4 | candidate QM 查询 external/JAR TM/FSScript 且依赖只读 | external filesystem 与标准 `jar:` fixture 均真实执行并返回数据 | real execution test `:172-198,377-464` | pass |
| AC-5 | validate/execute 使用各自权限 action，且权限链 fail closed | Authorization、row/field/physical guard 有通过证据；但 candidate 不设置 phase action，空 action 固定回落 `EXECUTE`，调用方还可向 execute 传入其他 action | candidate factory `:139-167`；Runtime service `:65-73`；`ModelPermissionEnforcementStep:63-73`；live Runtime 对照 `RuntimeQueryController:93-104` | fail |
| AC-6 | 零共享 cache 交互，成功/失败/close 后 live state 不变 | cache spy 为 0；catalog、script、Bundle cache/inventory、source revision 快照保持不变 | real execution test `:129-313,336-359,609-670` | pass |
| AC-7 | base/live/content stale 稳定失败，不回退 live 或发布 | open、调用前后均检查；执行窗口 source drift 和 active content drift 测试通过 | factory `:145-179,228-240,284-299`；session tests `:122-184` | pass |
| AC-8 | 只允许 selected Runtime-managed external Bundle 自身 overlay | 其他 Bundle 同名资源拒绝测试通过；但 selected source 只按 registry name/Namespace 与 live Bundle name/Namespace 关联，未校验 external 类型或 record/live path，历史 record 可把同名 configured/JAR 误认成 selected managed Bundle | Runtime service `:44-64`；factory `:264-280,301-345`；registry restore `RuntimeBundleRegistryService:43-57` | fail |
| AC-9 | Runtime metadata/phase、稳定 unsupported 与现有行为兼容 | metadata、列出的高级模式和 compatibility lanes 通过；但 guard 只检查 request 字段，detached loader 会复制 addon builders，execute 可进入 Mongo/vector QueryModel 而非稳定 `CANDIDATE_MODE_UNSUPPORTED` | factory `:438-469`；detached session `:182-254`；generic facade execute `QueryFacadeImpl:177-179` | fail |

## Implementation Quality

- scope and changed surface: 文件范围符合 spec，未新增 route、公共 Model SPI、Maven 依赖、Console 或
  launcher 改动。
- maintainability and duplication: candidate identity、session、Runtime orchestration 边界清楚；内容
  revision、stale 和 overlay helper 集中，未发现 debug/test bypass。
- error handling and edge cases: content/source stale、path/symlink、其他 Bundle overlay、DSL/DB 失败与
  advanced request guards 有覆盖；permission action、selected source provenance 与 model backend guard
  仍不完整。
- contract, data and compatibility: 现有 live 路径兼容测试通过；无 schema/migration。上述三个缺口
  触及不可豁免的权限、ownership 和 fail-closed 边界，不能作为普通残余风险签收。
- terminology and documentation: 文档正确区分内部 primitive 与 workspace/publish non-goal；但在修复
  前不应把当前实现描述为已验收交付。

## Evidence Matrix

| Item | Classification | Risk | Evidence | Reused / New | Result |
|---|---|---|---|---|---|
| AC-1 | core-blocker | major | SHA-256 identity/content drift tests + source audit | reused + new review | pass |
| AC-2 | core-blocker | major | detached production resolution、same-instance pin | reused + new review | pass |
| AC-3 | core-blocker | major | real SQLite overlay result | reused | pass |
| AC-4 | core-blocker | major | real external/standard `jar:` dependency query | reused | pass |
| AC-5 | core-blocker | critical | permission tests通过；phase/action source trace失败 | reused + new review | fail |
| AC-6 | core-blocker | critical | zero cache interaction + live snapshots | reused + new review | pass |
| AC-7 | core-blocker | critical | pre/post stale guards and drift tests | reused + new review | pass |
| AC-8 | core-blocker | critical | other-owner tests通过；selected provenance trace失败 | reused + new review | fail |
| AC-9 | core-blocker | major | Runtime metadata/compatibility通过；backend mode guard trace失败 | reused + new review | fail |
| session cleanup | scoped-risk | medium | resolution map/script/source cache清理；detached loader/catalog fields仍被 closed session 持有至 GC | new review | follow-up |

## Evidence Sufficiency

- assurance_level: elevated
- why_existing_evidence_is_sufficient_or_not:
  - 197 项 focused/affected tests 足以证明已覆盖路径，但没有验证 phase-specific permission action、
    selected source provenance mismatch 或非 JDBC backend rejection。
  - 三个失败均可由确定的 production 调用链推出，不依赖外部环境；其中 AC-5 可造成 action 级误放行，
    AC-8 可把 configured/JAR source 当作 writable selected Bundle，均命中不可豁免红线。
- new_validation_that_could_change_decision:
  - 修复后增加区分 `VALIDATE`/`EXECUTE` 决策的 candidate permission tests，并覆盖调用方伪造 action。
  - 增加 registry record 与 live configured/JAR/path mismatch 的 selected-source 拒绝测试。
  - 增加 Mongo/vector 或等价 non-JDBC QueryModel test double，断言 validate/execute 在查询前返回稳定
    `CANDIDATE_MODE_UNSUPPORTED`。
  - 若声明 close 主动释放全部引用，增加 closed-session retained-reference cleanup 断言。
- expensive_validation_omitted_and_reason: 未运行完整 reactor、Console/Playwright、launcher package、
  数据库矩阵、authority/replay/rehearsal/source-seal/tag/release/publish；均被 spec 排除，且不能改变
  当前源码级拒绝结论。

## Optional Full-Chain Recommendation

- recommendation: not-needed
- qualifying_condition: 当前缺陷可由局部修复及 focused/affected lanes 确定。
- estimated_wall_clock_and_basis: N/A
- scope_and_prerequisites: N/A
- maximum_attempts: 1
- decision_impact: 不影响当前拒绝结论。
- user_approval: not-requested
- execution_status: not-run

## Waivers

| Waived Item | Authority | Reason | Bounded Impact | Non-Waivable Guards | Follow-up |
|---|---|---|---|---|---|
| none | | | | | |

## Failed Items

- AC-5：candidate session 没有按方法固定 `VALIDATE`/`EXECUTE`，权限步骤在 action 为空时默认
  `EXECUTE`；validate 与 execute 因此不能证明使用正确 action，且 engine caller 可传入错误 action。
- AC-8：Runtime record 与实际 live Bundle 的 managed/external provenance、path 未绑定；registry restore
  对同名 live Bundle直接跳过，使 configured/JAR 同名冲突状态可进入 candidate selected-source 路径。
- AC-9：ordinary-JDBC guard 没有检查解析后的 QueryModel/backend；存在 addon builder 时，execute 可进入
  Mongo/vector 等非 JDBC QueryModel，validate 则以非稳定通用异常失败。

## Risks / Follow-ups

- `scoped-risk`：动态 JAR fixture 证明标准 `jar:` Resource，不代表 fat-JAR/nested-JAR；仅在对应
  packaging workitem 改变装配形态时补测。
- `scoped-risk`：`close()` 清理 candidate resolution map、FSScript map 和 source Bundle cache，但
  closed session 仍持有 detached loader/catalog 对象至 GC；修复时应清理 catalog/loader 引用，或收窄
  “close 主动释放全部引用”的文档承诺。
- `scoped-risk`：source revision 覆盖 Runtime 已知 committed mutation；绕过 registry/watcher 的 live
  filesystem 变更仍依赖既有部署约束和后续 workspace stale/revalidate 规则。
- `out-of-scope`：workspace store/resource/revision API、publish/rollback、Git、Console、JAR 多 Namespace
  及被显式拒绝的高级查询模式仍是后续 workitem，不是本 primitive 已交付能力。
- `process-gap`：现有测试名称与通过数量容易掩盖 action/provenance/backend 三个负向选择缺口；修复后
  应把这些情形纳入长期回归选择。

## Final Decision

- decision: rejected
- rationale: AC-5、AC-8、AC-9 未满足；其中 permission action 与 Bundle ownership 属于 elevated
  assurance 下不可豁免的安全/fail-closed guards。无 owner waiver，且本 spec 明确 `waivable_items:
  none`，因此不能 `accepted` 或 `accepted-with-risks`。
- blocking_items:
  - 固定并验证 candidate validate/execute 的 phase-specific `PermissionAction`，拒绝调用方 action
    混淆。
  - 将 Runtime registry record 与实际 live external Bundle identity/path fail-closed 绑定，拒绝
    configured/JAR masquerade。
  - 对解析后的非 JDBC candidate QueryModel/backend 返回稳定 unsupported，禁止进入执行。
- follow_up_owner_and_due: implementation owner；建立 focused BUG delivery spec，修复后仅重验失败项及
  其 engine/Runtime affected lanes，日期待 owner 确认。

## Signoff Marker

- acceptance_status: rejected
- acceptance_decision: rejected
- signed_off_by: Codex independent reviewer
- signed_off_at: 2026-07-31
- acceptance_record:
  `docs/9.5.3/acceptance/FEATURE-runtime-candidate-query-overlay-signoff.md`
- blocking_items: AC-5 permission action; AC-8 selected-source provenance; AC-9 non-JDBC mode guard
- follow_up_required: yes
