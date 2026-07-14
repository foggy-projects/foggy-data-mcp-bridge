---
acceptance_scope: feature
version: 9.3.2
target: auto-configuration-addon-assembly
doc_role: acceptance-record
doc_purpose: Record the formal acceptance decision for 9.3.2 auto-configuration and Addon assembly.
status: signed-off
decision: accepted-with-risks
signed_off_by: Codex
signed_off_at: 2026-07-13
reviewed_by: Codex + independent read-only reviewers
blocking_items: []
follow_up_required: yes
evidence_count: 11
---

# 9.3.2 Auto-Configuration and Addon Assembly Acceptance

## Background

- Version: 9.3.2
- Scope: feature acceptance
- Goal: 移除隐式根扫描和跨 Addon 扫描，将相关模块迁移到 Boot 3 标准入口，修复 Mongo、Vector、Cache 装配边界，并以 Launcher、数据库和发布物证据完成版本收口。
- Version alignment: 直接完成 docs/9.3.1/roadmap-9.3.1-to-9.4.0.md 的 9.3.2 准出门，未提前实施 9.3.3–9.4.0。

## Acceptance Basis

- docs/9.3.2/README.md
- docs/9.3.2/requirement/P0-auto-configuration-addon-assembly.md
- docs/9.3.2/module-responsibility.md
- docs/9.3.2/code-inventory.md
- docs/9.3.2/implementation-plan.md
- docs/9.3.2/progress/auto-configuration-addon-assembly-progress.md
- docs/9.3.2/test/auto-configuration-addon-assembly-test-evidence.md
- docs/9.3.2/quality/auto-configuration-addon-assembly-implementation-quality.md
- docs/9.3.2/coverage/auto-configuration-addon-assembly-coverage-audit.md
- Surefire reports、真实数据库串行执行记录、根 package 与最终 JAR 审计。

## Checklist

- [x] 不再依赖 ComponentScan com.foggyframework 发现 Addon。
- [x] 相关 Addon 使用 Boot 3 AutoConfiguration.imports。
- [x] 不存在旧 EnableAutoConfiguration 与新 imports 双重注册。
- [x] 可选依赖缺失时上下文安全启动。
- [x] 配置关闭时不装配，条件齐备时核心 Addon 精确装配一次。
- [x] Mongo、Vector、Cache、GraphQL 核心用户 Bean back-off 正常。
- [x] 核心单 Addon、联合 Addon和全 class path 无循环依赖或重复 Bean。
- [x] com.foggyframework 包外应用自动配置正常。
- [x] Launcher 默认无测试/开发 Bean，相关 route 404；显式启用正常。
- [x] allow-global-fallback-for-namespace=true 有稳定生产风险诊断。
- [x] 定向回归、SQLite 全量、三数据库和根 reactor package 全绿。
- [x] 实现质量检查与测试证据覆盖审计均已完成。
- [x] experience 标记 N/A，本轮无 UI/浏览器交付。

## Evidence

- Requirement and plan：README、requirement、implementation plan、module responsibility、code inventory。
- Implementation：Launcher/model/MCP 边界、Mongo/Vector/Cache 和相关 Addon 自动配置、resources 与测试差异。
- Auto-configuration slices：55/55。
- Launcher and assembly：9/9，21/21 reactor modules success。
- Directed regression：cache 86/86、model 27/27、PreAgg 1/1、MCP 5/5、Semantic 2/2。
- SQLite：3294 tests，0 failures，0 errors，3 skipped。
- Real databases：MySQL 5.7、PostgreSQL 15、SQL Server 2022 各 18/18。
- Root package：25/25 modules success，仅作编译和装配证据。
- Artifact audit：17 个 imports exact-once，无 packaged EnableAutoConfiguration，nested JAR checksum 12/12。
- Quality gate：ready-with-risks，无 blocker/high。
- Coverage audit：ready-with-gaps，无 blocker/high。

## Failed Items

- none

## Risks / Open Items

- DataViewer 的独立正向 ContextRunner 证据弱于核心 Addon；owner: DataViewer；follow-up: 后续测试增强。
- Cloud 缺 provider 正向创建和用户 Bean back-off 的直接切片；owner: chart storage cloud；follow-up: 后续测试增强。
- 部分次要 Addon 未逐个建立完整关闭、正向、back-off 三联切片；owner: 各 owning module；follow-up: 后续统一自动配置契约套件。
- 少量 spring.factories 仅含说明注释，运行时无注册效果；owner: build/module maintainers；follow-up: 后续清理以减少误解。
- 修复前红色 Surefire artifact 未做不可变归档；当前测试可表达旧实现失败契约，但历史证据链留待 9.3.4 CI 证据治理。

## Final Decision

- decision: accepted-with-risks。
- 9.3.2 的 critical/major 完成门均已有自动化、真实数据库或发布物证据，无 blocker/high。
- 已列风险属于测试粒度和维护性增强，不是已确认的生产实现缺陷，不阻断正式签收。
- 9.3.3 可按 roadmap 进入下一迭代；本次签收不代表 9.3.3–9.4.0 范围已完成。

## Signoff Marker

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex
- signed_off_at: 2026-07-13
- acceptance_record: docs/9.3.2/acceptance/auto-configuration-addon-assembly-acceptance.md
- blocking_items: none
- follow_up_required: yes
