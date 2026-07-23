---
doc_type: delivery-spec
delivery_type: optimization
version: 9.3.4
ticket: v934-risk-tiered-release-exit-policy
status: APPROVED
canonical: true
execution_mode: ultra
approved_by: release-owner-user-directive
approved_at: 2026-07-22
open_questions: []
---

# Delivery Spec: v9.3.4 风险分级发布退出政策

## Document Purpose

- intended_for: ultra-implementation / release-owner / independent-signoff
- purpose: 取代“任意测试或证据字节变化自动导致 Steps 1–4 全链重认证”的强耦合规则，固定本轮 v9.3.4 收口的风险分级、最小重验和循环熔断条件。
- canonical_path: `docs/9.3.4/workitems/DECISION-v934-risk-tiered-release-exit-policy.md`
- precedence: 自 2026-07-22 起，本记录对 v9.3.4 后续执行优先于历史 requirement、contract、progress 和 workitem 中要求 B/C 类问题自动重走 `Cdiag -> diagnostic -> Cfreeze -> formal` 的条款；历史运行事实和失败记录保持不变。

## Goal

- version_goal: 在不降低 coverage floor、不扩大 exclusion、不删除/skip required test 且不损害证据真实性的前提下，完成 v9.3.4 release authority、CI/release 闭环和独立签收。
- target_outcome: 只让真实产品、兼容、安全、数据正确性或证据真实性问题阻断发布；低风险测试确定性与证据包装问题按最小范围验证或转入 9.3.5。

## Scope

- in_scope:
  - 复核远端最新 r40 candidate、source seal、direct-parent Cfreeze topology 和 formal-ready 状态。
  - 从 exact Cfreeze `b05dd0ec659c283b1a59a82c1c67710f4c10368e` 执行一次 fresh clean-clone formal。
  - formal 通过后执行一次 fresh Step 5 rehearsal 和一次 portable replay，再继续 Steps 6–7。
  - 按 A/B/C 风险分类决定 blocker、最小重验或 9.3.5 debt。
- affected_modules: `docs/9.3.4`、既有 v9.3.4 authority/CI/release runners 及其生成证据。
- external_dependencies: GitHub remote、Docker required database/external lanes、Maven/JDK/Python/Git 工具链。

## Non-Goals

- out_of_scope:
  - 重新打开已通过的 Steps 1–3。
  - 主动提高 evidence schema 严格度、追逐 coverage exact high-water 的非 floor 偶发分支或重构测试基础设施。
  - 修改生产缓存 TTL 语义、public API/SPI、POM 或扩大到 9.3.5/9.4.0 产品范围。
- do_not_touch:
  - 用户工作区的 `docs/9.3.5/README.md` 和 `docs/9.3.5/workitems/`。
  - 既有历史失败记录、历史 run artifact 和已签收 Steps 1–3 结论。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| A 类阻断发布 | 可重复生产错误、数据/隔离/安全/权限问题、API/SPI/兼容破坏、证据真实性不可证明或 required test 真实 F/E/S 都会直接损害发布正确性。 | 修复后明确提出与改动面匹配的最小重验；不得降低门槛。 |
| B 类局部修复不重启整链 | test-only deterministic oracle、时序/调度/遍历顺序非产品不确定性，以及不影响真实性的格式、mtime、mode、布局或工具可移植性问题，不自动推翻既有业务验证。 | 只要求 targeted regression、affected lane、source/delta identity 和最后一次 clean full formal；Steps 1–3 保持关闭。 |
| C 类转入 9.3.5 | evidence schema 继续加严、非安全 mode/mtime 完美化、coverage exact high-water 偶发一分支、测试基础设施重构、Unit MySQL fixture 永久分类迁移及无直接真实性影响的新负向探针。 | 记录 owner/期限；不降低当前 coverage floor，不扩大 exclusion。 |
| formal/Step 5 次数熔断 | 当前已具备 reviewed r40 与 direct-child Cfreeze，继续无限重认证的边际收益低且会制造治理循环。 | 最多一次 fresh clean-clone formal；通过后最多一次 fresh rehearsal + portable replay。B/C 类失败不得自动再次启动完整 Step 4。 |
| 历史证据按语义复用 | test-only 或包装变化不等于历史业务验证全部无效。 | 禁止伪造或拼接历史运行产物；允许把已签收且未受改动影响的业务结论作为版本证据背景。 |

## Acceptance Criteria

- [ ] AC-1: exact Cfreeze、r40 candidate/reviews、source seal、direct-parent topology、formal-ready/confirmed 和 Step 4/6 checksum closure 全部可机器复核。
- [ ] AC-2: 只执行一次 fresh clean-clone formal，required union、Addon、coverage floor/critical、source seal、cleanup 和 public artifact replay 全部通过；若失败先归类 A/B/C。
- [ ] AC-3: formal 通过后只执行一次 fresh Step 5 rehearsal 与 portable replay；通过后才进入 Steps 6–7。
- [ ] AC-4: Steps 6–7 使用同一 coherent source/evidence scope，完成 required CI aggregator、same-tested release artifact 和 final pointer/promotion 验证，不混入用户 9.3.5 改动。
- [ ] AC-5: 最终报告分别说明 implementation complete、release authority、independent signoff、accepted debt 和真实 blocker；签收由 `foggy-delivery-signoff` 独立阶段完成。

## Contract / Data / Security Constraints

- API or event contract: 不修改 public API、SPI 或产品运行时行为。
- data and migration: 无数据/schema 迁移；required DB identity、fixture 和隔离契约保持不变。
- compatibility and rollback: 本记录是 docs-only 治理调整，可独立 revert；不得改变历史 artifact 内容或哈希。
- permissions and secrets: evidence 和日志继续敏感信息 fail-closed；不得记录凭证或私有 token。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1 | critical | Git topology、candidate/review/capsule、source/manifest/checksum 和 formal-ready 静态复核 | 精确 SHA、命令与结果摘要 |
| AC-2 | critical | 一次 fresh clean-clone Step 4 formal | exact run id、tested commit、structured status、final replay |
| AC-3 | critical | 一次 fresh rehearsal + portable replay | candidate status、archive/root digest、portable replay receipt |
| AC-4 | critical | Step 6 CI contract/required checks 与 Step 7 same-artifact/promotion | workflow/run/artifact identity、final promotion receipt |
| AC-5 | critical | 独立 version signoff evidence matrix | version acceptance record 与 canonical status 回写 |

## Risks and Open Questions

- known_risks:
  - 外部 GitHub 权限或实际 required-check 配置不可用时必须如实标记 blocked，不能用本地 YAML 代替远端事实。
  - formal 或 Step 5 若暴露 A 类问题，本轮熔断不豁免真实 blocker。
- open_questions: none

## Ultra Execution Contract

- 先复核本文件、根 `CLAUDE.md`、v9.3.4 requirement/contract/progress 和相关 runner。
- 保护原工作区 9.3.5 改动；在独立 clean worktree/clone 中执行 v9.3.4。
- 严格执行一次 formal、一次 rehearsal/portable replay 的次数上限；失败先分类再行动。
- B/C 类问题不得自动触发新 Cdiag/diagnostic/Cfreeze/formal 循环；C 类登记 9.3.5 debt 后继续收口。
- 遇到生产缺陷、coverage floor 降低、required test 删除/skip、public API/SPI 修改、范围扩张、工作区保护失败或证据真实性不可证明时停止并请求 release owner 决策。
- 完成后填写 `Implementation Result`，状态改为 `READY_FOR_SIGNOFF`；不得自行设置 `ACCEPTED`。

## Implementation Result

- implementation_summary: pending
- changed_paths: pending
- tests_and_results: pending
- manual_or_experience_evidence: pending
- deviations: none
- residual_risks: pending
- readiness: ULTRA_EXECUTING

## References

- requirement / issue: `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md`
- architecture / glossary: `docs/9.3.4/contract/test-lane-evidence-contract.md`
- related work items: `BUG-step5-pivot-outer-cache-ttl-clock-oracle.md`、`FEATURE-v934-release-authority-and-ci.md`、`DEBT-unit-mysql57-fixture-classification-migration.md`
