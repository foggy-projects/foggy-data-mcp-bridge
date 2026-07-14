---
doc_role: workitem
doc_purpose: Define the 9.3.1 production isolation and fail-closed implementation contract.
version: 9.3.1
priority: P0
status: signed-off
created_at: 2026-07-13
updated_at: 2026-07-13
---

# P0 生产隔离与 fail-closed

## 背景与问题

当前代码存在四条可能跨越生产隔离边界的路径：

1. main 源集中的测试/开发 Controller 可默认注册，并使用空语义上下文调用查询服务。
2. 显式 named datasource 或 namespace binding 解析失败后可回退全局默认数据源。
3. L1 cache lookup 早于字段权限和 `systemSlice`，命中后可跳过 SQL。
4. L1/L2 key 缺少 namespace、resolved datasource、安全策略和 model/catalog freshness；L2 参数拼接存在类型及分隔符歧义。

另有两个 P1 契约缺口：DataSetResultStep 混用两套排序协议；PreAgg 改写 SQL 后 L2 读取仍可能使用旧 paging SQL。

## 目标

- 所有生产入口、数据源选择和缓存命中都必须携带足够的隔离身份。
- 任何显式隔离信息无法解析时拒绝执行，不静默扩大到默认范围。
- 安全相关 Step 的执行顺序由代码契约和负向测试共同约束。

## 模块责任

| 模块 | 责任 |
|---|---|
| `foggy-dataset-model` | Step 排序、测试 Controller 门禁、严格数据源解析、L2 最终执行身份 |
| `foggy-dataset-mcp` | DevTools 默认关闭、显式 datasource 解析失败不回退 |
| `addons/foggy-dataset-model-cache` | L1/L2 隔离身份、canonical 参数编码、缓存缺身份时 fail closed |
| `foggy-runtime-api` | namespace datasource binding 与错误传播，不吞掉未绑定状态 |

## 实施分解

### A. Step 顺序

- DataSetResultStep 只保留一个有效排序协议，并记录 before/process 顺序语义。
- L1 lookup 必须位于权限解析、字段校验、`systemSlice` 合并和请求规范化之后。
- QueryExecutionStep 保持 Physical Permission → PreAgg → L2 的 before 顺序，after 反向执行。
- 重复的保留 order 必须在 executor 构造/应用启动阶段失败并列出冲突类。
- PreAgg 改写后刷新最终 paging SQL，L2 读写共用相同身份。

### B. 缓存隔离键

- 定义类型化 cache identity，至少包括 namespace、model、resolved datasource identity、安全策略 fingerprint、query identity。
- 9.3.3 generation 尚未上线前使用明确 freshness token/安全 fallback；缺关键身份时跳过缓存。
- L1 fingerprint 包含字段白名单、物理列黑名单和 `systemSlice` 的规范化签名。
- L2 参数按类型、长度和顺序编码，不使用逗号拼接的 `toString()`。
- 缓存日志不得输出 authorization、数据库凭据或原始安全属性。

### C. 测试/开发入口

- `SemanticServiceV3TestController` 默认关闭，沿用显式 `foggy.test.enabled=true` 才启用的测试组件约定。
- `DevToolsController` 默认关闭；仅 `foggy.dev-tools.enabled=true` 时注册。
- 生产上下文负向测试必须同时断言 Bean 不存在和路由不可达。

### D. 数据源解析

- 非空 `dataSourceName` 必须精确解析，否则抛出包含 namespace/model/name 的安全错误。
- 非空 namespace 没有默认 binding 时默认抛错。
- 兼容全局 datasource 回退只能通过显式配置开启，默认关闭并打印风险告警。
- 未指定 namespace 且未指定 named datasource 时保留默认 datasource 行为。

### E. 关键回归

- Step 顺序、重复 order、PreAgg/L2 一致性。
- 默认生产上下文无测试/开发路由。
- named datasource missing、namespace binding missing、legacy opt-in 三类解析测试。
- 相同 model/SQL/params 在不同 namespace、datasource、权限策略、freshness 下均不命中。
- SQLite 加至少一个外部数据库的真实结果对比，不只断言 key 或 SQL 字符串。

## 验收标准

- 不存在已知 fail-open 回退或宽缓存键。
- 所有负向测试先能复现旧行为，修复后稳定失败关闭。
- 定向 unit/integration 回归全绿，并记录执行命令、测试数、skip 和数据库版本。
- 完成实现质量检查、测试覆盖审计、正式验收三道后置门。

## 非目标

- 不实现 Catalog Snapshot/single-flight/NamespaceScope；归 9.3.3。
- 不完成完整 Failsafe/多数据库/覆盖率发布门；归 9.3.4。
- 不重构公共 QueryFacade 或拆大类；归 9.3.5。
- 不做 model-api/core/jdbc/starter/web 物理模块拆分；归 9.4.0。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex
- signed_off_at: 2026-07-13
- acceptance_record: docs/9.3.1/acceptance/P0-production-isolation-fail-closed-acceptance.md
- blocking_items: none
- follow_up_required: yes
