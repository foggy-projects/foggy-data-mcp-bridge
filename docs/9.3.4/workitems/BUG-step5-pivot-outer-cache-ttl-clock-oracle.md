---
doc_type: delivery-spec
delivery_type: bug
version: 9.3.4
ticket: BUG-934-STEP5-PIVOT-OUTER-CACHE-TTL-CLOCK-ORACLE
status: ULTRA_EXECUTING
canonical: true
execution_mode: ultra
approved_by: foggy-projects (continuation authority)
approved_at: 2026-07-20
open_questions: []
---

# Delivery Spec: Step 5 Pivot outer-cache TTL clock oracle

## Document Purpose

- intended_for: ultra-implementation / independent-signoff
- purpose: 固定 Step 5 fresh rehearsal 暴露的 Pivot TTL 测试时钟脆弱性，恢复可重复的 fail-closed release authority。
- canonical_path: docs/9.3.4/workitems/BUG-step5-pivot-outer-cache-ttl-clock-oracle.md

## Goal

- version_goal: 9.3.4 的 Step 5 rehearsal 必须在新鲜、隔离的 SQLite integration lane 中以行为 oracle 而非墙钟偶然性判定 Pivot outer-cache TTL。
- target_outcome: 保持实际本地缓存 TTL 和 Pipeline expiry/re-execution 语义，移除 `PivotIT` 中 1ms TTL 加固定 sleep 的不稳定触发方式。

## Scope

- in_scope:
  - `foggy-dataset-model` 的 `PivotIT` TTL expiry testcase 及其 test-only helper。
  - 受控时间的 test-only `PivotOuterCacheProvider` 包装器：委托真实 `PivotOuterResponseCache`，但把 Pipeline 传入的墙钟替换为由测试显式推进的时间。
  - 保留既有 testcase 名称和节点数；第一轮 query/store 后推进受控时间，再验证真实本地 provider 过期、Pipeline miss/re-execution/store diagnostics 与 payload correctness。
  - 新 Cdiag 后按 Step 4 冻结流程重新形成 diagnostic、Cfreeze、formal、quality/audit 与 Step 5 evidence；r38 只保留为其原始 source 的历史签收证据。
- affected_modules:
  - `foggy-dataset-model`
  - `docs/9.3.4/workitems`（唯一 canonical work item）
- external_dependencies: SQLite in-process test profile；不需要 Docker、MySQL 或其他宿主服务。

## Non-Goals

- out_of_scope:
  - 修改 `PivotPipeline`、`PivotOuterResponseCache`、`PivotOuterCacheProvider` 的生产行为、SPI 或 public API。
  - 修改 Maven POM、Step 4/5 runner、coverage floor、critical set、exclusion、r38 历史证据或 Addon context 修复。
  - 提前进入 9.3.5 Gate 0、QueryFacade 公共 API 或 9.4.0 SPI v2/module 拆分。
- do_not_touch:
  - `scripts/verify-v934-*`、`scripts/v934/step4/**`、coverage contract/freeze threshold。
  - 任何 Docker/宿主数据库配置。
  - `PivotIT` 的 flat row-order 语义；若原始 list equality 另行暴露顺序问题，必须新建事项或 `NEEDS_REPLAN`，不得在本 BUG 中静默扩展。

## Confirmed Decisions

| Decision | Rationale | Compatibility / Constraint |
|---|---|---|
| 用受控 test time 替代 `Thread.sleep(20)` | Pipeline 以独立 `System.currentTimeMillis()` 调用 lookup/store；1ms TTL 可在 WSL 墙钟回拨时仍命中，造成非产品伪红。 | 仅测试代码；不得向生产注入 clock seam。 |
| 包装器委托真实 `PivotOuterResponseCache` | 既验证 provider 的真实 expiry 语义，又验证 Pipeline 在 `expired` 分支的 diagnostics/re-execution。 | 既有 provider contract test 继续覆盖手工时间下的 TTL 行为。 |
| 保留原 testcase | 修复 oracle，不能通过删测、放宽 sleep 或降低 authority 门来获得绿色。 | testcase cardinality 不变。 |
| r38 签收不被重写 | r38 exact source 已通过；本修复改变 test source，后续 authority 必须从新 Cdiag 完整重走。 | 不得把 r38 artifact 拼接为新 Cdiag/formal 证据。 |

## Acceptance Criteria

- [ ] AC-1: `PivotIT#testOuterCacheTtlExpiryFlatPivotE1b` 不再使用 `Thread.sleep`、固定等待或直接墙钟采样来决定 TTL expiry。
- [ ] AC-2: 该 test 使用真实 `PivotOuterResponseCache` 的委托实现；初始受控时间 store 后显式推进至 TTL 外，第二次 lookup 必须 deterministic 地产生 `pivot.cache.evicted` 与 `pivot.cache.miss(reason=ttl_expired)`，并继续产生 execution/store diagnostics。
- [ ] AC-3: 不改生产源码、public API、SPI、POM、runner、coverage thresholds/critical/exclusion，且不新增或删除 JUnit testcase。
- [ ] AC-4: focused fresh Failsafe forks、完整 `PivotIT`、真实 local-provider contract coverage 与完整 Step 4 replacement authority 均实际通过；任一失败保持 fail-closed。
- [ ] AC-5: 新鲜 Step 5 rehearsal 和 portable replay 通过后，才恢复 Step 6/7 与后续版本入口；不得把本 BUG 的实现完成表述成 9.3.4 version signoff。

## Contract / Data / Security Constraints

- API or event contract: 无对外 API、事件或 SPI 变更；仅稳定测试时间来源。
- data and migration: 无数据、schema、fixture 或 Docker migration。
- compatibility and rollback: test-only 改动可由 Git revert 回滚；不得影响已发布运行时缓存的 TTL 语义。
- permissions and secrets: 不读取、记录或依赖任何凭证、容器标识、原始日志或原始 XML。

## Test and Evidence Obligations

| Item | Risk | Required Validation | Required Evidence |
|---|---|---|---|
| AC-1/AC-2 | major | 至少 5 个独立 fresh Maven/JVM Failsafe focused forks；完整 `PivotIT`；`PivotOuterResponseCacheTest`/provider contract 实测 | 结构化 testcase aggregate、run status、命令与结果摘要；不得导出原始日志/XML |
| AC-3 | major | source diff、testcase inventory、Maven reactor/runner static checks | diff/check summary，证明生产/POM/runner/floor 未变 |
| AC-4 | blocker | 新 Cdiag → fresh diagnostic → review → direct-child Cfreeze → fresh formal → quality/audit | 新 source seal、manifest/status、独立 review 结论 |
| AC-5 | blocker | 新鲜 Step 5 rehearsal 与 portable replay | Step 5 structured status/receipt；失败时不发布下游成功声明 |

## Bug Context

- bug_source: acceptance-found
- severity: major
- environment: WSL clean clone；SQLite `sqlite-broad` integration variant；不依赖宿主 Docker/MySQL。
- current_behavior: `PivotIT` 配置 1ms local TTL 后固定 sleep 20ms，并由 Pipeline 的独立墙钟调用尝试观察 expiry。新鲜 Step 5 `step5-rehearsal-20260720-r3` 因该 method 唯一失败而 fail closed。
- expected_behavior: TTL provider 的过期与 Pipeline expiry diagnostics 由可显式推进的 deterministic test time 证明，不受 NTP/WSL wall-clock 调整、调度或负载影响。
- reproduction_steps:
  1. clean HEAD 上执行 Step 5 rehearsal；
  2. nested integration reaches `variant-sqlite-broad` and stops fail-closed;
  3. structured aggregate identifies `PivotIT#testOuterCacheTtlExpiryFlatPivotE1b` as the sole failure.
- reproduction_status: confirmed
- existing_evidence:
  - `step5-rehearsal-20260720-r3`: release/Step4/integration structured status stops respectively at `step4-release-successor` / `child-integration` / `variant-sqlite-broad`.
  - canonical aggregate: 45 reports, 307 tests, F1/E0/S0; exact failed testcase is the TTL method above; its concrete failure class is `java.lang.AssertionError`.
  - r38 formal ran the same SQLite broad test on unchanged production/test source successfully; `62361688..079d7ecc` contains documentation-only paths.
- existing_tests:
  - `PivotIT#testOuterCacheTtlExpiryFlatPivotE1b` exercises Pipeline diagnostics and re-execution.
  - `PivotOuterCacheProviderContractTest#ttlExpiryReportsExpiredThenMisses` already verifies local/provider TTL with explicit timestamps.
- regression_protection: required
- waiver_reason_and_risk: N/A

## Risks and Open Questions

- known_risks:
  - `PivotIT` also compares raw flat result list order after re-execution. That is not causal for the current concrete `java.lang.AssertionError` and is excluded unless it independently fails under the corrected deterministic clock.
  - Any source change invalidates use of r38 artifacts as current authority and therefore requires the complete new Step 4 sequence.
- open_questions: none

## Ultra Execution Contract

- 先读取本文件、根 `CLAUDE.md`、9.3.4 requirement/test plan、`PivotIT`、`PivotOuterResponseCache` 与 provider contract tests。
- 在 scope 内自主决定 helper 的具体名称和局部结构；优先把受控 provider 作为 `PivotIT` test-only nested helper，且它必须委托真实 local provider。
- 不得修改 production/POM/runner/threshold，也不得用更长 sleep、retry-until-green、条件 skip 或降低 assertion 强度替代 deterministic oracle。
- 如发生 flat result row-order failure、需要 production clock injection、SPI/API change、测试节点变化或范围扩大，设置 `NEEDS_REPLAN` 并停止扩展。
- 运行与改动面匹配的验证，不得声称未实际运行的测试通过；完成后填写 `Implementation Result` 并更新为 `READY_FOR_SIGNOFF`，不得自行设置 `ACCEPTED`。

## Implementation Result

> 由 Ultra 执行会话填写。

- implementation_summary: `PivotIT` 的 TTL expiry case 改用 test-only `ControlledTimeOuterCacheProvider`；它委托真实 `PivotOuterResponseCache`，仅在 `lookup/store` 时使用测试显式推进的时间。首轮在 100 store，第二轮推进至 102（TTL=1），因此真实 local provider deterministic 地报告 expired，Pipeline 仍执行原有 evict/miss/re-execution/store diagnostics。
- changed_paths:
  - `foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/engine/pivot/PivotIT.java`
  - `docs/9.3.4/workitems/BUG-step5-pivot-outer-cache-ttl-clock-oracle.md`
- tests_and_results:
  - 5 个独立 fresh Maven/JVM Failsafe focused forks：`PivotIT#testOuterCacheTtlExpiryFlatPivotE1b` 均为 `1/F0E0S0`。
  - 完整 SQLite/Caffeine `PivotIT=55/F0E0S0`。
  - 真实 local provider suite `PivotOuterResponseCacheTest=8/F0E0S0`，其中 `ttlExpiryReportsExpiredThenMisses` 通过。
  - static: diff check 通过；`@Test` 节点 `56 -> 56`；production/POM/runner 路径变更数为 0；目标 method 不含 `Thread.sleep` 或直接 wall-clock 读取。
- manual_or_experience_evidence: N/A
- deviations: none
- residual_risks: `PivotIT` 的 flat raw list equality 仍是既有、非本次 failure 的潜在顺序语义风险；本轮未观察到失败，按 scope 保留，不改变生产排序语义。
- independent_implementation_review: `ACCEPT / B-H-M-L=0/0/0/0`；review 确认 wrapper 委托真实 local provider、TTL expiry/re-execution diagnostics 被实际覆盖、无 production/POM/runner/SPI/API 扩面。
- readiness: ULTRA_EXECUTING — test-only implementation 已通过独立审计；新源码的 Cdiag、fresh diagnostic/review、direct-child Cfreeze、fresh formal、quality/audit 及 Step 5 rehearsal/portable replay 仍是未完成的版本 authority。

## References

- requirement / issue: `docs/9.3.4/requirement/P0-test-ci-evidence-chain.md` (deterministic correctness boundary)
- architecture / glossary: `PivotOuterCacheProvider` local/provider contract
- related work items: `BUG-step4-bean2map-cache-timing-oracle.md`, `BUG-step4-addon-context-mtime-publication-order.md`
