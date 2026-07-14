---
doc_role: version_followup_plan
doc_purpose: Track production isolation and fail-closed hardening for 9.3.1.
version: 9.3.1
status: signed-off
created_at: 2026-07-13
updated_at: 2026-07-13
---

# 9.3.1 生产隔离与 fail-closed

## 版本目标

在继续自动配置、模型生命周期和模块化之前，先消除可能导致跨 namespace、跨数据源、跨权限命中或错误回退的生产风险，并把关键执行顺序固化为可验证契约。

## 工作项

| 工作项 | 文档 | 状态 | Owner 模块 |
|---|---|---|---|
| P0 生产隔离与 fail-closed | `workitems/P0-production-isolation-fail-closed.md` | signed-off (`accepted-with-risks`) | `foggy-dataset-model`、`foggy-dataset-mcp`、`addons/foggy-dataset-model-cache` |

## 当前确认的 P0 风险

- 测试或开发 Controller 位于 main 源集且默认可装配。
- 显式 `dataSourceName` 或 namespace 数据源绑定失败后可能回退全局默认数据源。
- L1 cache lookup 早于字段权限和 `systemSlice` 合并，命中后可跳过真实查询。
- L1/L2 缓存身份缺少 namespace、resolved datasource 和安全策略信息。

## 版本完成门

- 默认生产上下文不注册任何测试/开发 Controller；显式启用时才可访问。
- 显式数据源名称解析失败必须报错；非空 namespace 未绑定数据源默认报错，兼容回退只能显式开启。
- Step 只使用一个排序协议；安全关键步骤有确定顺序，重复保留 order 在启动/构造阶段失败。
- L1 在权限解析、字段校验、系统切片和请求规范化完成后读取；缓存身份缺失时不缓存。
- L2 读写使用同一个最终执行身份，包含 namespace、resolved datasource、权限策略和无歧义参数编码。
- SQLite 加第二真实数据库回归证明相同模型/SQL 在不同 namespace 或 datasource 下不串缓存、不回退错库。
- 版本完成后依次执行实现质量检查、测试证据覆盖审计和正式验收签收。

## 跨版本约束

- 9.3.2 只在本版本完成门通过后开始。
- 9.3.3 的 snapshot/generation 必须建立在本版本确定的数据源解析和缓存隔离语义上。
- 9.3.1 不提前实施 SPI v2、物理模块拆分或大类重构。

## 总体迭代评审

9.3.1 至 9.4.0 的评审结论见 `roadmap-9.3.1-to-9.4.0.md`。

## 质量与证据

- implementation quality: `quality/production-isolation-fail-closed-implementation-quality.md`
- coverage audit: `coverage/production-isolation-fail-closed-coverage-audit.md`，结论 `ready-with-gaps`，允许进入正式验收。
- test evidence: `test/production-isolation-fail-closed-test-evidence.md`
- acceptance signoff: `signed-off / accepted-with-risks`；9.3.2 开工门已解锁。

## Acceptance Status

- acceptance_status: signed-off
- acceptance_decision: accepted-with-risks
- signed_off_by: Codex
- signed_off_at: 2026-07-13
- acceptance_record: docs/9.3.1/acceptance/P0-production-isolation-fail-closed-acceptance.md
- blocking_items: none
- follow_up_required: yes
