---
doc_role: version-readme
version: 9.5.3
theme: runtime-model-authoring-foundations
status: ACTIVE
recorded_at: 2026-07-31
---

# 9.5.3 Runtime 模型创作基础

9.5.3 在 [Runtime Console 产品章程](../design/runtime-console-product-charter.md)约束下，评估并分阶段
建立 TM/QM/FSScript 的有界创作闭环。Console 不建设大型 Web IDE；手工编辑、后续 AI Agent
和可选 VS Code 插件应复用同一工作区、验证与发布契约。

本迭代先验证现有 detached validation、跨 Bundle/JAR 只读依赖、FSScript 资源、候选查询和
Bundle inventory 的真实能力。技术结论形成前，不提前实现临时 Namespace、持久 workspace
catalog、Git 凭据管理、跨 Runtime 编排或 Agent。

| Work item | 状态 | 目标 |
|---|---|---|
| [模型创作基础技术探针](workitems/SPIKE-runtime-model-authoring-foundations.md) | ACCEPTED | 已用真实 external/JAR/FSScript 路径确认 detached overlay 与隔离能力，并完成当前缺口分类 |
| [Runtime candidate-query overlay](workitems/FEATURE-runtime-candidate-query-overlay.md) | ACCEPTED | 已验收 request-local candidate resolve/validate/query 原语，复用权限与执行链并隔离 live catalog/cache |
| [Runtime authoring workspace API](workitems/FEATURE-runtime-authoring-workspace-api.md) | ACCEPTED | R2 重验确认 ownership blocker 已关闭，workspace/revision/diff/validate/query 与 live-state isolation 全部签收 |
| [Workspace store root ownership 修复](workitems/BUG-runtime-authoring-workspace-store-root-ownership.md) | ACCEPTED | ownership-bearing v2、foreign data 零删除、v1 无损迁移和 Bundle path disjointness 已正式签收 |
| [Runtime Console authoring workspace 草稿闭环](workitems/FEATURE-runtime-console-authoring-workspace.md) | ACCEPTED | 已验收不含 publish 的创建、编辑、CAS 冲突恢复、diff、validate、candidate query 与 discard 草稿闭环 |
| [Runtime Console authoring workspace 有界清理](workitems/REF-runtime-console-authoring-workspace-cleanup.md) | ACCEPTED | 在 publish 前拆分 catalog/editor/inspector 职责，并补齐 candidate query execution 与安全 CSV |

技术基线和建议交付顺序见
[Runtime 模型创作工作区设计与路线](runtime-model-authoring-design.md)。

## 已确认产品边界

- Namespace 是运行上下文，Bundle 是源码和可编辑性边界。
- 首期优先一个可写 Bundle，其余同 Namespace Bundle 作为只读依赖。
- JAR/classpath Bundle 永远只读，可以被多个 Namespace 使用；修改和升级仍通过重新构建
  JAR/Launcher、发布并重启服务。
- Git 是首选但不是前提；无 Git 场景必须能形成可校验的本地 revision。
- Console 只操作当前同源 Runtime；跨环境通过 Git revision 或 release package 传递，不建设
  多 Runtime 中央控制台。
- AI Agent 和 VS Code 集成晚于稳定的工作区与发布 API。

## 技术验证结论

- `reusable-now`：草稿 TM/QM import 草稿 FSScript；草稿 QM 依赖同 Namespace external/JAR
  TM 及其 FSScript；source overlay；成功/失败后的 live catalog/cache/source revision 隔离。
- `small-extension`：完整 Bundle capability inventory、JAR/classpath 可见性、工作区
  `.fsscript` resource contract。
- `delivered-internal-primitive`：受治理的 request-local candidate query 已实现为 engine/Runtime
  内部端口；当前不新增 REST route，并显式拒绝尚未安全 pin 的高级查询模式。
- `new-runtime-primitive`：workspace revision/publish 边界，以及后续显式 JAR 多 Namespace
  binding。
- 当前 `/resources/save` 只适合作为 Runtime-managed external Bundle 的低层写入能力，不能直接
  充当“保存草稿并发布”。

## 建议推进顺序

1. Runtime candidate-query overlay（已实现并完成独立验收）。
2. Runtime authoring workspace/revision API（ownership blocker 修复后已完成 R2 独立验收）。
3. Console 最小手工创作与开发环境发布闭环。
4. release package、生产验证、apply 与 rollback。
5. 可选 Git adapter。
6. 有真实需求后增加 JAR 多 Namespace binding。
7. 最后接入 VS Code 插件与 Console Agent。

后续 implementation workitem 只在前一阶段契约和证据稳定后逐项创建，不把上述能力合成一个
大型交付。
