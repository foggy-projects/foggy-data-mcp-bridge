---
doc_role: version_execution_index
doc_purpose: Index the signed-off 9.3.2 auto-configuration and Addon assembly delivery package.
version: 9.3.2
status: signed-off
decision: accepted-with-risks
created_at: 2026-07-13
updated_at: 2026-07-13
---

# 9.3.2 自动配置与 Addon 装配

## 文档作用

- doc_type: requirement+plan+progress+acceptance-index
- intended_for: release owner / module owner / reviewer
- purpose: 统一记录扫描边界、Boot 3 自动配置迁移、Addon 条件装配、Launcher 黑盒验证和正式签收。

## 版本目标

在不提前实施 9.3.3–9.4.0 的前提下，移除依赖根包和跨 Addon 扫描的隐式装配，将相关模块统一为 Boot 3 AutoConfiguration.imports、条件化装配和用户 Bean back-off，并补齐 Launcher 发布物 smoke。

## 执行资料

- requirement: requirement/P0-auto-configuration-addon-assembly.md
- module responsibility: module-responsibility.md
- code inventory: code-inventory.md
- implementation plan: implementation-plan.md
- progress: progress/auto-configuration-addon-assembly-progress.md
- test evidence: test/auto-configuration-addon-assembly-test-evidence.md
- implementation quality: quality/auto-configuration-addon-assembly-implementation-quality.md
- coverage audit: coverage/auto-configuration-addon-assembly-coverage-audit.md
- acceptance: acceptance/auto-configuration-addon-assembly-acceptance.md

## 最终状态

- predecessor: 9.3.1 signed-off / accepted-with-risks，无 blocker/high。
- implementation: completed。
- test: completed；SQLite 3294/0/0/3，三种真实数据库各 18/18，Launcher 9/9，根 reactor 25/25 package。
- quality gate: ready-with-risks，无 blocker/high。
- coverage audit: ready-with-gaps，无 blocker/high。
- acceptance: signed-off / accepted-with-risks。
- experience: N/A，本轮为后端启动、Bean 装配和 HTTP 路由隔离治理。

## 保留风险

- DataViewer 的正向独立 ApplicationContextRunner 证据弱于核心 Addon，当前由 Launcher、模块回归和负向切片共同覆盖。
- Cloud 和部分次要 Addon 未逐个建立完整的关闭、正向、back-off 三联切片；已通过 imports 唯一性、class path 共存和根 package 降低风险。
- 仅含说明注释的 spring.factories 不参与运行时注册，后续可删除以降低维护者误解。
