# 9.3.2 Implementation Plan

## 文档作用

- doc_type: implementation-plan
- intended_for: release owner / execution agent / reviewer
- purpose: 记录失败契约、扫描边界、Boot 3 注册、Addon 修复、Launcher 回归和后置门的执行顺序及完成状态。

## 前置门

- [x] 9.3.1 正式签收，decision=accepted-with-risks，无 blocker/high。
- [x] 执行 git status --short 并区分、保护既有 9.3.1 未提交变更。
- [x] 读取 9.3.1 roadmap、acceptance、test evidence、coverage audit。
- [x] 确认不实施 9.3.3–9.4.0 禁止项。

## Batch 1：失败契约与装配基线

Status: completed

1. 为 Mongo、Vector、Cache 建立 ApplicationContextRunner 切片。
2. 使用 FilteredClassLoader 覆盖 Mongo、VectorStore、Milvus、WebFlux、Redis、Caffeine、Servlet、AspectJ 和云 SDK 缺失。
3. 建立 io.foggytest 包外应用。
4. 固化配置关闭、条件齐备、用户 Bean back-off、单 Addon、全 Addon和 Launcher 预期。
5. 记录修复前扫描和注册问题；最终绿色测试成为回归契约。

## Batch 2：扫描边界

Status: completed

1. 移除 Launcher 根包扫描。
2. 将 model/MCP 内部组件改为显式 Import/Bean 清单。
3. Mongo/Vector model Loader 移除可被基础扫描发现的 stereotype。
4. 以关键 Bean 清单和 Launcher/包外 smoke 验证无静默丢失。

## Batch 3：Boot 3 注册

Status: completed

1. 相关配置使用 AutoConfiguration 并声明 before/after。
2. 补齐 AutoConfiguration.imports。
3. 删除 EnableAutoConfiguration factories 条目，保留 initializer 等合法 key。
4. 从源码、class path 和最终 main JAR 三层验证注册唯一性。

## Batch 4：Addon 修复

Status: completed

- Mongo：Mongo 类、连接 Bean 和基础 loader 条件齐备才创建 Loader；用户 Loader back-off；切片不连接真实 Mongo。
- Vector：开关、Vector/Milvus 类、基础 Bean 和装配顺序明确；可选依赖缺失安全跳过。
- Cache：enabled/provider 条件贯穿 provider、builder、controller 和 eviction；Redis/Caffeine 分支独立；用户 provider back-off。
- 相关 Addon：GraphQL、Cloud、DataViewer、PreAgg、Odoo、MemoryGrid、Demo 和 Dataset 的入口、顺序或可选依赖边界同步标准化。
- fallback risk：兼容开关为 true 时输出 FOGGY-SEC-932-001。

## Batch 5：Launcher 与回归

Status: completed

1. 完成 core-only、核心单 Addon、联合 Addon、全 class path、包外应用上下文。
2. 完成 Launcher 默认 Controller Bean 缺失/route 404 以及显式启用 smoke。
3. 串行完成 9.3.1 定向基线、SQLite 全量和三数据库真实回归。
4. 串行完成根 reactor package；明确不把 skipTests 结果视为测试通过。
5. 审计最终 JAR metadata 和 Launcher nested JAR checksum。

## 后置门

Status: completed

1. versioned doc execution-checkin 已回写。
2. implementation quality gate 完成，decision=passed-with-risks。
3. test coverage audit 完成，decision=passed-with-risks。
4. formal acceptance 完成，decision=accepted-with-risks。

## 执行结论

- 全部 Batch 和后置门按顺序完成。
- blocker/high: none。
- 保留风险仅涉及部分次要 Addon 的独立正向/back-off 切片粒度，不涉及已确认的生产实现缺陷。
