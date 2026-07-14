---
doc_role: workitem
doc_purpose: Define and close the 9.3.2 auto-configuration and Addon assembly implementation contract.
version: 9.3.2
priority: P0
status: completed
acceptance_status: signed-off
acceptance_decision: accepted-with-risks
created_at: 2026-07-13
updated_at: 2026-07-13
---

# P0 自动配置与 Addon 装配

## 背景

9.3.1 已关闭生产 Controller、数据源回退和缓存身份中的已知 fail-open 风险。9.3.2 继续消除 Launcher 根扫描、model/MCP 宽扫描、旧 spring.factories 与 Boot 3 imports 混合注册带来的隐式发现和重复装配风险。

## 目标

1. Launcher 仅扫描自身包，不再扫描 com.foggyframework。
2. model/MCP 基础模块不通过宽扫描发现 Addon，内部生产 Bean 使用显式 import/bean 注册。
3. 相关自动配置均由 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports 发现。
4. 清除 spring.factories 中 EnableAutoConfiguration 注册，保留合法 initializer 注册。
5. Mongo、Vector、Cache 在依赖缺失、配置关闭、条件齐备和用户 Bean back-off 场景中精确装配。
6. 包外应用、核心单 Addon、全 classpath/联合 Addon 和 Launcher 能启动且无重复 Bean或循环依赖。
7. 测试/开发 Controller 默认无 Bean且 route 404，显式启用时正常。
8. allow-global-fallback-for-namespace=true 产生稳定生产风险诊断。

## 约束

- 保留全部 9.3.1 未提交变更，不执行 reset、checkout、clean 或覆盖式还原。
- 上下文切片不连接真实 Mongo、Vector 或 Redis。
- 最终 Maven 证据串行执行；skipTests package 只算编译和装配证据。
- 可选依赖条件避免在条件解析阶段触发 linkage error。
- 不实施 9.3.3 的 Catalog Snapshot、single-flight、NamespaceScope，9.3.4 的完整 CI 门禁，9.3.5 的 QueryFacade 拆解或 9.4.0 的物理模块拆分。

## 验收标准与结果

| 验收标准 | 结果 | 主要证据 |
|---|---|---|
| 无 Launcher 根扫描和 model/MCP 跨 Addon 扫描 | completed | AutoConfigurationBoundaryContractTest；源码静态审计 |
| Boot 3 imports 完整且无旧新双注册 | completed | 17 个入口 exact-once；源码和最终 JAR 审计 |
| 缺类、关闭、启用、back-off 矩阵 | completed | Mongo、Vector、Cache、GraphQL、Cloud、DataViewer 切片 |
| Mongo Loader 条件化且不连接真实 Mongo | completed | dataset/model Mongo 各 6/6 |
| Vector 条件、入口和顺序正确 | completed | dataset/model Vector 各 7/7 |
| Cache provider/builder/controller/eviction 条件一致 | completed | ContextRunner 14/14；cache 回归 86/86 |
| 包外、单 Addon、全 Addon、Launcher | completed | Launcher 组合矩阵 9/9；21 模块 classpath |
| 默认测试/开发路由不可达，显式启用正常 | completed | 两个 Launcher MockMvc smoke |
| fallback 生产风险诊断 | completed | GlobalNamespaceFallbackRiskDiagnosticTest 2/2 |
| 定向、SQLite、数据库矩阵和根 package | completed | 27/27、1/1、5/5、2/2；3294；三库各 18；25/25 |
| 质量、覆盖与正式签收 | completed | quality、coverage、acceptance 文档 |

## 签收说明

- status: signed-off
- decision: accepted-with-risks
- blocker/high: none
- follow-up: DataViewer/Cloud 和部分次要 Addon 可在后续补齐更细粒度的独立正向与 back-off 切片；不构成本版本发布阻断。
