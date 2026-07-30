---
doc_role: version-readme
version: 9.5.2
theme: runtime-web-console-mvp
status: READY_FOR_SIGNOFF
recorded_at: 2026-07-27
---

# 9.5.2 Runtime Web Console MVP

9.5.2 为 Java Runtime 规划一个同源、轻量、可选的 Web 管理端。Console 使用现有
`X-Foggy-Runtime-Code` 作为进入和操作凭据，不引入账号、RBAC、审计、版本管理、独立 BFF
或独立 Node 部署。

| Work item | 状态 | 目标 |
|---|---|---|
| [Runtime Web Console MVP](workitems/FEATURE-runtime-web-console-mvp.md) | READY_FOR_SIGNOFF | 已新增可选 `addons/foggy-runtime-console`；`foggy-runtime-api` 承担 access check、`management-all` 与真实鉴权，launcher 通过显式 profile 装配，Console 静态资源同源提供在 `/console/` |
| [Namespace 资源工作区](workitems/OPT-runtime-console-namespace-workspace.md) | ACCEPTED | 将 Namespace 收敛为“数据与模型空间”，统一 QM/Bundle 浏览与详情交互 |
| [签收后清理](workitems/REF-runtime-console-post-signoff-cleanup.md) | READY_FOR_SIGNOFF | 已清理不可达旧页面，并补齐交付路径规则与 CJK 体验证据 |
| [全局 Namespace 上下文一致性](workitems/OPT-runtime-console-global-namespace-context.md) | READY_FOR_SIGNOFF | 所有工作台切换空间后重载候选或清空结果，并以 revision/snapshot 隔离迟到响应 |

## 交互原型

- [Runtime Console 单页交互原型](prototype/runtime-console-prototype.html)：用于确认 Token 进入、运行概览、数据源、语义模型和查询工作台的信息架构与视觉方向。原型使用模拟数据，不连接真实 Runtime API，也不代表最终前端实现结构。

当前实现已完成管理页、查询/SQL/Compose/Fsscript 工作台、sessionStorage token 流程、
`foggy-data-viewer` 结果表格复用、响应式桌面/移动布局与 Maven 静态资源打包。交付状态为
`READY_FOR_SIGNOFF`，尚未设置 `ACCEPTED`；当前有效整体架构仍以
[docs/architecture](../architecture/README.md) 为唯一入口。

## 交付证据规则

Implementation Result 中的 `changed_paths` 必须从实际 Git 边界生成，不得只手工列举核心文件。
提交前使用 `git diff --name-only <base>...HEAD`；尚未提交时同时补充
`git diff --name-only` 与 `git ls-files --others --exclude-standard`。正式签收以 reviewer 对真实
commit 和工作树的审计结果为准，不回写或美化历史测试结果。
