---
doc_role: execution_progress
doc_purpose: Track implementation, testing, and acceptance readiness for the 9.3.1 P0 work item.
version: 9.3.1
status: signed-off
created_at: 2026-07-13
updated_at: 2026-07-13
---

# P0 生产隔离与 fail-closed 进度

## 开工记录

- review: completed，迭代顺序有条件通过。
- development: completed，声明范围内的 Controller、数据源解析、Step 顺序、缓存身份与快照隔离均已实现。
- testing: implementation regression completed；SQLite 全量回归和 MySQL/PostgreSQL/SQL Server 真实数据库矩阵均通过。
- experience: N/A，后端隔离与执行链治理。

## 已确认基线

- `SemanticServiceV3TestController` 无默认关闭条件。
- `DevToolsController` 的 `foggy.dev-tools.enabled` 为 `matchIfMissing=true`，显式 bean 名失败后回退主数据源。
- `TableModelLoaderManagerImpl.resolveDataSource` 接受显式 named datasource miss 和 namespace binding miss 后的默认源回退。
- `L1CacheStep.order()=900`，早于只声明 Spring `@Order` 的权限及系统切片步骤。
- Caffeine/Redis L2 key 仅由 model、SQL 和参数文本构成。
- PreAgg 修改 `ctx.sql/params` 时没有同步 `pagingSql`。
- Caffeine L1 正向用例存在无有效断言的伪绿色。

## 执行批次

| 批次 | 内容 | 状态 |
|---|---|---|
| 1 | 默认关闭测试/开发 Controller，数据源解析 fail closed | completed |
| 2 | 统一 Step 顺序并修复 L1/PreAgg/L2 身份时序 | completed |
| 3 | L1/L2 cache identity 与 canonical 参数编码 | completed |
| 4 | 真实数据库隔离回归与证据收口 | completed |

## 自检与测试状态

- model 关键定向回归：27 passed，0 failure，0 error；此前扩展定向集 63 passed。
- PreAgg→L2 生产 Bean 联合集成：1 passed，0 failure，0 error。
- MCP Controller 隔离回归：5 passed，0 failure，0 error。
- Semantic 测试 Controller 隔离回归：2 passed，0 failure，0 error。
- cache identity/canonical encoding/provider/真实双数据源回归：86 passed，0 failure，0 error。
- 新增 PreAgg 联合测试前的 `foggy-dataset-model` SQLite 全量基线：3289 tests，0 failure，0 error，3 skipped；新增用例另行 1/1 定向通过。
- `MultiDatabaseQueryTest` 真实数据库矩阵：MySQL 18/18、PostgreSQL 18/18、SQL Server 18/18。
- 根 reactor package：25/25 modules success。
- 独立代码复核：无 blocker/high；可进入测试证据覆盖审计。

## 验收准备度

- implementation quality gate: `ready-for-coverage-audit-with-risks`。
- coverage audit: `ready-with-gaps`，critical/major 正确性项已覆盖，可进入正式验收。
- acceptance signoff: `signed-off / accepted-with-risks`。

## 非阻断风险与后续

- Redis 的模型/数据源身份在 9.3.3 generation 上线前是进程内身份；跨 JVM 只会冷缓存，不会跨实例错误命中。
- 标准 `AbstractRoutingDataSource` 默认绕过缓存；真实 `DelegatingDataSource` target 切换的双 SQLite sentinel 隔离测试已通过。
- `allow-global-fallback-for-namespace` 只作为显式兼容开关保留，需纳入生产配置检查。
- 修复前基线与修复后负向用例已记录，但缺少不可变的“先红后绿” CI artifact，由 9.3.4 证据链收口。
- cache fail-closed 原因的限频日志或指标归入后续可观测性补强，不阻断本轮覆盖审计。
- 9.3.1 已正式签收；9.3.2 开工门已解锁，但本记录不声明 9.3.2 已开始实施。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex
- signed_off_at: 2026-07-13
- acceptance_record: docs/9.3.1/acceptance/P0-production-isolation-fail-closed-acceptance.md
- blocking_items: none
- follow_up_required: yes
