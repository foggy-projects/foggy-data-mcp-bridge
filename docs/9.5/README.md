---
doc_role: major-version-index
doc_type: progress
intended_for: root-controller / execution-agent / reviewer / signoff-owner
purpose: 汇总 9.5 版本线的子迭代状态、能力基线、未闭环风险与唯一活跃入口。
release_line: 9.5
status: ACTIVE
recorded_at: 2026-08-01
---

# 9.5 版本线索引

## 子迭代入口

既有 9.5.0～9.5.4 保留平铺目录，避免破坏历史链接；从 9.5.5 起采用主版本索引下的子迭代目录。

| 子迭代 | 状态 | 主题与入口 |
|---|---|---|
| 9.5.0 | `ACCEPTED` | [Model Legacy Exit](../9.5.0/README.md) |
| 9.5.1 | `READY_FOR_SIGNOFF` | [Runtime 与模型治理](../9.5.1/README.md)；权限与安全预聚合仍待独立签收 |
| 9.5.2 | `READY_FOR_SIGNOFF` | [Runtime Web Console MVP](../9.5.2/README.md)；MVP umbrella spec 仍待独立签收 |
| 9.5.3 | `COMPLETED` | [Runtime 模型创作基础](../9.5.3/README.md) |
| 9.5.4 | `COMPLETED` | [Release Package 与生产 Promotion](../9.5.4/README.md) |
| 9.5.5 | `ACTIVE` | [Runtime 模型交付稳定化与可运维性](9.5.5/README.md) |

`READY_FOR_SIGNOFF` 的历史项是 signoff backlog，不作为新的产品实现入口，也不得在未验收时改成
`ACCEPTED`。

## 当前能力基线

| 能力面 | 当前结论 | 证据入口 |
|---|---|---|
| Model SPI legacy exit | 已完成旧聚合身份、旧包根和受治理 bypass 清理 | [9.5.0](../9.5.0/README.md) |
| Runtime 权限与安全预聚合 | 实现记录已完成，canonical workitem 尚待独立签收 | [9.5.1](../9.5.1/README.md) |
| Runtime Console 管理与查询工作台 | 后续优化项均已接受，MVP umbrella spec 尚待独立签收 | [9.5.2](../9.5.2/README.md) |
| 模型创作 workspace | create/edit/diff/validate/candidate query/discard 已接受 | [9.5.3](../9.5.3/README.md) |
| 开发环境发布 | exact revision publish、恢复、连续 immutable-base 发布已接受 | [9.5.3](../9.5.3/README.md) |
| 跨环境交付 | immutable release package、生产重验、apply、一步 rollback 已接受 | [9.5.4](../9.5.4/README.md) |
| Console 创作与 promotion | 草稿、开发发布、导入、生产 evidence、apply/rollback 已接受 | [9.5.3](../9.5.3/README.md)、[9.5.4](../9.5.4/README.md) |

## 缺口与优先级

| 优先级 | 分类 | 缺口 | 处理原则 |
|---:|---|---|---|
| P0 | process-gap | 9.5.1 权限 workitem、9.5.2 Console MVP umbrella spec 尚未独立签收 | 保留原状态，单独按既定 delivery spec 验收 |
| P1 | scoped-risk | immutable published artifact 无 GC，其 `.staging-*` 缺少 ownership-proven restart cleanup | 9.5.5 先做只读 inventory 和生命周期策略，再冻结实现 spec；不得删除仍被引用或 ownership 不明的文件 |
| P1 | scoped-risk | 一致性当前限单 Runtime 进程、非 shared-NFS writer | 明确部署边界；真实需求出现前不扩展分布式协调 |
| P2 | scoped-risk | package hash 不证明 signer identity，本地 package 保管依赖操作者 | 保持管理 auth-code 信任边界；签名/KMS 另立安全 workitem |
| P2 | usability | drag/drop、history timeline、automatic polling 未实现 | 仅在运维闭环稳定后按用户价值拆分 |
| later | out-of-scope | Git adapter、JAR 多 Namespace、审批/RBAC、跨 Runtime orchestration、VS Code/Agent | 需要真实需求和独立 delivery spec，不并入稳定化迭代 |

## 执行规则

- 当前唯一新实施入口是 9.5.5；不得再创建平铺的 `docs/9.5.5/`。
- 9.5.5 首个 workitem 应是 artifact/store 生命周期与运维证据的只读技术探针。
- 技术探针完成前，不实现 GC、清理器、分布式锁、签名或新的 Console 大功能。
- 每个后续实现项单独冻结 canonical delivery spec，并按风险相称的最小验证半径验收。
