---
audit_scope: feature
audit_mode: pre-acceptance-check
version: 9.3.1
target: P0-production-isolation-fail-closed
status: reviewed
conclusion: ready-with-gaps
reviewed_by: Codex
reviewed_at: 2026-07-13
follow_up_required: yes
---

# Test Coverage Audit

## Background

- 审计对象：9.3.1 P0 生产隔离与 fail-closed。
- 当前阶段：实现质量闸门通过后、正式验收前。
- 审计目标：确认 Step 顺序、缓存隔离键、测试 Controller、数据源解析和关键数据库回归均有可复核证据，并判断是否允许进入正式验收。

## Audit Basis

- requirement: `docs/9.3.1/workitems/P0-production-isolation-fail-closed.md`
- implementation plan: `docs/9.3.1/roadmap-9.3.1-to-9.4.0.md`
- progress: `docs/9.3.1/workitems/P0-production-isolation-fail-closed-progress.md`
- bug work items: N/A，本轮是版本级生产风险治理。
- acceptance basis: requirement 的“版本完成门”和“验收标准”。
- test records: `docs/9.3.1/test/production-isolation-fail-closed-test-evidence.md` 及对应 Surefire 报告。
- manual evidence: Docker 真实数据库容器状态和本地串行 Maven 执行记录；本功能无页面体验验收项。

## Coverage Matrix

| Item | Risk | Unit | Integration | E2E | Playwright | Manual | Evidence Path | Coverage |
|------|------|------|-------------|-----|------------|--------|---------------|----------|
| STEP-ORDER：统一排序、重复 order 拒绝、before/process/after 与 loop/terminal 顺序 | critical | yes | yes | no | no | no | `QueryExecutionStepOrderingTest`、`DataSetResultStepExecutorOrderingTest`、`PreAggregationL2CacheIntegrationTest` | covered |
| PREAGG-L2：PreAgg 改写后以最终 SQL/params/datasource 计算 L2 身份 | critical | yes | yes | no | no | no | `PreAggregationL2CacheIntegrationTest`、`QueryExecutionStepOrderingTest` | covered |
| CACHE-IDENTITY：namespace/model 实例/datasource/security/typed params/boolean tree 隔离 | critical | yes | yes | no | no | no | cache fingerprint/provider 专项；`CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest` | covered |
| CACHE-SNAPSHOT：读写结构快照，未知类型、循环、超深值和不安全路由 fail closed | critical | yes | yes | no | no | no | `StableCanonicalEncoderTest`、Caffeine/Redis provider 测试、Step ordering 测试 | covered |
| CONTROLLER：测试/开发 Controller 默认无 Bean 且 route 404，显式开关后才启用 | critical | no | yes | no | no | no | `DevToolsControllerIsolationTest`、`SemanticServiceV3TestControllerIsolationTest` | covered |
| DATASOURCE：named source 精确解析，namespace miss 拒绝，legacy fallback 仅显式开启 | critical | yes | yes | no | no | no | `TableModelLoaderManagerImplDataSourceResolutionTest`、`DevToolsControllerIsolationTest`、`DbModelAutoConfigurationTest` | covered |
| DS-CACHE-ISOLATION：同 model/namespace/SQL/params 在两个真实物理数据源不得串缓存 | critical | no | yes | no | no | no | `CaffeineQueryCacheProviderDataSourceIsolationIntegrationTest`，双 SQLite 文件各自 sentinel | covered |
| DB-MATRIX：SQLite 全量及 MySQL 5.7/PostgreSQL 15/SQL Server 2022 真实查询回归 | major | no | yes | no | no | yes | `MultiDatabaseQueryTest`、全量 SQLite 记录、Docker image/status 记录 | covered |
| POST-GATES：实现质量、证据覆盖、正式验收按顺序执行 | major | no | no | no | no | yes | implementation quality 文档、本审计文档；正式签收待下一步 | partially-covered |

## Evidence Summary

- 已有自动化测试：cache 专项 86/86；model 关键定向 27/27；PreAgg→L2 生产 Bean 联合测试 1/1；MCP Controller 5/5；Semantic 测试 Controller 2/2。
- 已有手工验证：新增 PreAgg 联合测试前的 SQLite 全量基线 3289 tests，0 failure/error，3 skipped，新增用例已另行定向通过；MySQL 5.7、PostgreSQL 15、SQL Server 2022 的 `MultiDatabaseQueryTest` 各 18/18；根 reactor package 25/25 modules success。
- 已有回归保护：显式 named datasource miss、namespace miss、compatibility opt-in、重复 Step order、最终缓存身份、认证隔离、真实双数据源 sentinel、Controller 默认 404 均已形成自动化用例。
- 证据执行采用串行 Maven；曾出现的并行测试发现失败源于多个 Maven 进程竞争共享 `target`，串行重跑全部通过，不计为产品缺陷。

## Gaps

- 尚无最终 launcher/starter 发布物级黑盒启动测试；当前 Spring 上下文已同时证明默认 Bean 不存在和 route 404，作为 9.3.1 非阻断缺口保留。
- 尚无不可变 CI job 链接、归档 Surefire/Failsafe/coverage artifact；属于 9.3.4“测试与 CI 证据链”的声明范围，不阻断本轮功能验收。
- 当前进度基线与修复后负向测试可复核，但没有不可变的“先红后绿”历史 artifact；作为 9.3.4 证据链的非阻断遗留，不将其描述为已具备完整历史证据。
- Redis 的 datasource/model 身份在 9.3.3 generation 前为进程内安全身份；跨 JVM 会冷缓存而不是错误命中，跨进程与刷新一致性由 9.3.3 补齐。
- 弱身份注册表的并发/GC 压力、真实 Redis 跨进程验证和 fail-closed 指标属于后续增强，不影响当前正确性结论。

## Recommended Next Skills

- `integration-test`: 当前无阻断性缺口；9.3.3/9.3.4 实施时补 generation 并发、跨进程 Redis 和发布物 smoke。
- `webapp-testing`: 不适用，本轮无浏览器页面或交互验收项。
- `foggy-bug-regression-workflow`: 仅在正式验收发现新缺陷时启用。
- `foggy-acceptance-signoff`: 立即执行，对 9.3.1 做正式签收。
- `plan-evaluator`: 迭代顺序已评审；范围变化时再复核。

## Conclusion

- conclusion: ready-with-gaps。
- can_enter_acceptance: yes；critical/major 的 9.3.1 正确性与隔离项已有充分自动化或真实数据库证据。
- follow_up_required: yes；将 launcher 黑盒、不可变 CI evidence、generation/跨进程缓存证据分别留给 9.3.2、9.3.3、9.3.4。
