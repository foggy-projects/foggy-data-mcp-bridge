---
doc_role: version-readme
doc_type: progress
intended_for: root-controller / execution-agent / reviewer / signoff-owner
purpose: 定义 9.5.5 Runtime 模型交付稳定化与可运维性的范围、进入条件和非目标。
version: 9.5.5
theme: runtime-model-delivery-operability
status: ACTIVE
recorded_at: 2026-08-01
---

# 9.5.5 Runtime 模型交付稳定化与可运维性

## Work items

| Work item | 状态 | 目标 |
|---|---|---|
| [成员权限、Viewer 与 detached 身份](workitems/BUG-member-policy-and-detached-identity.md) | PUBLISHED | 八类修复及 Query Bean 注册验证完成；双平台全量、五数据库及 47/47 IT 类通过；[Launcher 0.1.31 / Maven 发布复验](../../release/launcher-0.1.31-member-policy-publication.md)通过 |
| [字段绑定与权限依赖一致性](workitems/BUG-field-binding-dependencies.md) | DONE | 优先修复窗口权限与 pivot 数据错误，统一计算/聚合命名、元数据、结果 schema 和依赖注入；专项后全量及多数据库验证 |
| [#146 QM 字段命名与维度列元数据](workitems/BUG-146-qm-field-names.md) | PUBLISHED | 命名合同、维度列注册、元数据及权限回归完成；专项、全量与五数据库验证通过，历史例外单列；Launcher 0.1.27 与私有 snapshot 已发布 |
| [Launcher clean 范围](workitems/BUG-launcher-clean-scope.md) | LOCALLY VERIFIED | 限定 Console/Launcher 清理；真实 Maven 文件系统回归及 29 项打包/发布测试通过 |
| [统一排序与字段绑定](workitems/REFACTOR-order-by-field-binding.md) | LOCALLY VERIFIED | 统一请求内投影绑定、排序优先级和去重；普通查询 SQL Server 重复排序修复，专项、五数据库及全量测试通过；Launcher 0.1.26 与私有 snapshot 已发布 |
| [DISTINCT 排序丢失 #145](workitems/BUG-145-distinct-order-by.md) | LOCALLY VERIFIED | 59 项专项、Windows/Linux 全量、五数据库、前端及原始 Chinook 重放通过；含 SQL Server 重复默认排序修复 |
| [Mongo 逻辑条件组执行](workitems/BUG-mongo-logical-condition-groups.md) | LOCALLY VERIFIED | 修复多字段 OR 条件崩溃；3 项定向回归及下游 4 组真实查询通过 |
| [Aggregate relation COUNT 外层汇总](workitems/BUG-aggregate-relation-count-rollup.md) | READY_FOR_REVIEW | COUNT 输出列外层改用 SUM，105 项定向回归与固定环境回读通过；显式聚合 alias 总计 null 单独记录 |
| [Artifact/store 生命周期基础探针](workitems/SPIKE-runtime-artifact-store-lifecycle-foundations.md) | ACCEPTED | 双 store inventory、跨 store/live 引用图、中断 characterization 与安全后续边界已正式验收；未修改生产实现 |
| [Artifact lifecycle 只读 inventory](workitems/FEATURE-runtime-artifact-lifecycle-inventory.md) | ACCEPTED | management-auth、redacted、cross-store/live 的只读容量、引用分类与 blocked reason 已正式验收；无 cleanup mutation |
| [Published store 中断写入恢复](workitems/BUG-runtime-published-store-interrupted-write-recovery.md) | ACCEPTED | 新 staging/temp 带 ownership evidence，只恢复可证明 owned 的中断写入；legacy unknown 保留，已独立正式验收 |
| [Current-main 生产基线](workitems/FEATURE-current-main-production-baseline.md) | ACCEPTED | 本地全量 reactor、CLI 干净 HEAD 构建与隔离 Keycloak/PostgreSQL 生产冒烟已正式验收；GitHub Actions 不作为门禁 |
| [MCP JWT/JWKS 生产认证适配器](workitems/FEATURE-mcp-jwt-jwks-auth-adapter.md) | ACCEPTED | 真实 JWT/JWKS、claim 身份映射、角色/scope 门禁、Header 防伪与 `kid` 轮换已通过本地生产冒烟 |
| [Analytics external-durable 与多实例恢复 Worker](workitems/FEATURE-analytics-external-durable-recovery-worker.md) | PROPOSED | PostgreSQL 持久化、CAS/lease 多实例恢复、FAP 幂等对账、人工处置与运维指标契约 |
| [Runtime API 自动配置装配缺失](workitems/BUG-runtime-api-auto-configuration-assembly.md) | READY_FOR_SIGNOFF | 已补齐 publication/lifecycle 显式装配并通过 focused 与隔离 launcher 验证 |
| [Console artifact lifecycle 运维视图](workitems/FEATURE-runtime-console-artifact-lifecycle-operability.md) | READY_FOR_SIGNOFF | 只读 lifecycle evidence ledger 已完成自动化与隔离 launcher 验证，等待用户体验验收 |

## 目标

9.5.5 不继续扩张模型创作功能面，而是收口 9.5.3～9.5.4 已接受的 workspace、publish、release
package 和 production promotion：确认磁盘 artifact/store 生命周期、重启遗留物、容量与诊断边界，形成
可安全实施和验收的运维契约。

## 当前范围

1. 只读盘点 workspace、published artifact、release package 的非持久化边界、promotion attempt 和
   published root 临时文件的 owner、引用关系、状态转换、保留条件与现有失败模式。
2. 定义 fail-closed cleanup/retention 策略，证明不会删除 live、rollback、recovery 或 foreign data
   仍需要的内容。
3. 识别应暴露的容量、状态和人工处置诊断；不把日志或目录猜测当作稳定 API。

用户于 2026-08-27 明确将 current-main、MCP 与 Analytics Console 三项提升为 P0；该范围修订
仅覆盖上述生产基线，不扩展为通用 Console Agent 或大型 Console 改版。
4. 技术探针签收后，再决定是否分别冻结 cleanup/retention API 与 Console 运维入口。
5. 保持单 Runtime 进程、非 shared-NFS writer 的当前一致性边界，除非后续独立 workitem 明确扩展。

## 非目标

- Git adapter、JAR 多 Namespace binding、JAR 升级或 fork。
- package signing/KMS、审批/RBAC、用户审计或跨 Runtime orchestration。
- 任意历史 rollback、package registry、共享存储协调或后台自动升级。
- VS Code 插件、Console Agent、通用 Web IDE 或与运维无关的大型 Console 改版。
- 在技术探针前直接实现删除、GC、自动清理或迁移现有 store。

## 进入与完成条件

- 首先使用 delivery-spec 流程冻结一个只读技术探针，覆盖真实源码、磁盘格式、引用图和重启路径。
- 实现 workitem 必须等待探针独立验收；cleanup 的 ownership、reference safety 和 failure recovery
  属于不可豁免项。
- 9.5.1 与 9.5.2 的历史 signoff backlog 独立处理，不把补签收伪装成 9.5.5 产品能力。
- 本迭代完成时更新 [9.5 主版本索引](../README.md)，历史平铺目录迁移仍由独立治理任务处理。

- [字段投影与依赖图复用优化](workitems/OPT-compiled-field-bindings.md)：DONE；#147 交付后的内部绑定、依赖图与 CI 门禁优化，双平台全量、四库和全部 IT 验收通过。

- [成员绑定与同目录隔离校验修复](workitems/BUG-member-binding-and-detached-validation.md)：DONE；#148/#149，成员及权限绑定、Runtime 接口与同目录隔离校验；双平台全量、四库、47/47 IT 类和实际 Launcher 验证通过。
