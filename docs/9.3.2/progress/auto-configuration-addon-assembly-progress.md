---
doc_role: execution_progress
doc_purpose: Record the completed implementation, testing and signoff progress for 9.3.2.
version: 9.3.2
status: completed
acceptance_status: signed-off
acceptance_decision: accepted-with-risks
created_at: 2026-07-13
updated_at: 2026-07-13
---

# 9.3.2 自动配置与 Addon 装配进度

## 前置条件

| 条件 | 状态 | 证据 |
|---|---|---|
| 9.3.1 已签收 | completed | docs/9.3.1/acceptance/P0-production-isolation-fail-closed-acceptance.md |
| 工作区既有变更已保护 | completed | 初始和最终 git status；未执行破坏性 Git 命令 |
| 9.3.2 执行包已建立 | completed | docs/9.3.2 |

## Development Progress

| Step | 内容 | 状态 | 结果 |
|---|---|---|---|
| Batch 1 | 失败契约、包外应用、装配基线 | completed | ContextRunner、FilteredClassLoader、io.foggytest 应用和 Launcher smoke 已建立 |
| Batch 2 | Launcher/model/MCP 扫描边界 | completed | 根扫描和两处跨模块宽扫描已移除，关键 Bean 显式注册 |
| Batch 3 | Boot 3 imports 与旧入口清理 | completed | 17 个入口 exact-once，旧 EnableAutoConfiguration factories 为零 |
| Batch 4 | Mongo/Vector/Cache 与相关 Addon 条件修复 | completed | 核心切片 40/40；GraphQL、Cloud、DataViewer 13/13 |
| Batch 5 | Launcher smoke 与串行回归 | completed | Launcher 9/9；SQLite 3294；三库各 18；根 package 25/25 |

## Implementation Summary

- Launcher 不再扫描 com.foggyframework。
- DbModelAutoConfiguration 与 DatasetMcpAutoConfiguration 使用显式 Import 清单，基础模块不再发现 Addon。
- Mongo dataset/model 使用独立 Boot 3 入口；Loader 不再是无条件 Service，缺类、缺连接 Bean、关闭和用户 Bean场景均安全。
- Vector dataset/model 补 Boot 3 入口、开关、依赖类和 after 顺序；Milvus、WebFlux 或 VectorStore 缺失时安全跳过。
- Cache 拆分 QueryCacheAutoConfiguration、QueryCacheWebAutoConfiguration、QueryCacheEvictionAutoConfiguration；provider、builder、controller 和 eviction 共享 enabled/provider 条件。
- GraphQL、Cloud、DataViewer、PreAgg、Odoo、MemoryGrid、Demo 和基础 Dataset 入口完成标准化。
- allow-global-fallback-for-namespace=true 会输出 FOGGY-SEC-932-001 风险诊断。

## Testing Progress

| 验证层 | 状态 | 结果 |
|---|---|---|
| 自动配置核心切片 | completed | Mongo 6+6、Vector 7+7、Cache 14，共 40/40 |
| 其他 Addon 切片 | completed | GraphQL 5、Cloud 5、DataViewer 3，共 13/13 |
| fallback 诊断 | completed | 2/2 |
| 包外/注册/全 Addon/Launcher | completed | 9/9，21/21 reactor modules success |
| 9.3.1 定向回归 | completed | cache 86、model 27、PreAgg 1、MCP 5、Semantic 2 |
| SQLite 全量 | completed | 3294 tests，0 failure，0 error，3 skipped |
| 真实数据库 | completed | MySQL 5.7、PostgreSQL 15、SQL Server 2022 各 18/18 |
| 根 reactor package | completed | 25/25 modules success；仅作编译和装配证据 |
| 发布物元数据 | completed | main JAR 无旧 EnableAutoConfiguration；launcher nested JAR 12/12 checksum 匹配 |

## Acceptance Criteria Tracking

| 验收项 | 状态 |
|---|---|
| 无 Launcher 根扫描和基础模块跨 Addon 扫描 | completed |
| Boot 3 imports 完整且无旧新双注册 | completed |
| 缺依赖、关闭、启用、back-off 矩阵通过 | completed |
| Mongo、Vector、Cache 条件精确 | completed |
| 包外应用、单 Addon、全 Addon、Launcher 通过 | completed |
| 默认测试/开发路由 404，显式启用正常 | completed |
| fallback 生产风险诊断 | completed |
| 定向回归、数据库回归、reactor package、后置门完成 | completed |

## Deviations and Risks

- planned deviations: none。
- blocker/high: none。
- accepted risks:
  - DataViewer 的独立正向 ContextRunner 证据弱于核心 Addon。
  - Cloud 和部分次要 Addon 未逐个建立完整的关闭、正向、back-off 三联切片。
  - 部分 spring.factories 仅保留说明注释，运行时不注册任何自动配置。

## Downstream Readiness

- quality gate: completed / ready-with-risks
- coverage audit: completed / ready-with-gaps
- acceptance: signed-off / accepted-with-risks
- next version: 9.3.3 可按 roadmap 开工，但本轮未提前实现其范围。
