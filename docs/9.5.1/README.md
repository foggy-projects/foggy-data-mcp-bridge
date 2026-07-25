---
doc_role: version-readme
version: 9.5.1
theme: runtime-and-model-governance
status: IN_PROGRESS
recorded_at: 2026-07-25
---

# 9.5.1 Runtime 与模型治理

9.5.1 承接 9.5.0 Model SPI legacy exit 后的 Runtime 与模型治理工作。当前迭代包含两个彼此独立
但都建立在 main-after-9.5.0 架构上的 work item：

| Work item | 状态 | 目标 |
|---|---|---|
| [Add-on 多 Bundle 生命周期能力迁回 main](workitems/FEATURE-addon-bundle-lifecycle-main-port.md) | ACCEPTED | 补齐同 namespace 多 Bundle 来源、原子 replace/remove 和 Runtime registry 一致性 |
| [Runtime 内生权限与权限安全预聚合](workitems/FEATURE-runtime-internal-permissions-and-preaggregation.md) | APPROVED | 补齐可选 token、动作化 QM 权限、平台 `get/post`、字段/行权限、预聚合安全回源，并同步 CLI/Skill 与存量兼容 |

## 当前权限迭代边界

- Runtime 数据查询只透明传递可选 opaque Authorization，并始终建立匿名或 opaque-subject
  `RequestIdentity`；未声明权限的 QM 保持开放。
- `foggy-runtime` CLI 保留 `--auth-code`/`X-Foggy-Runtime-Code` 管理契约，并新增独立、可选的
  data-plane Authorization 输入；两套凭据可以共存但不能互相提升。
- QM 作者通过 `modelPermissions.resolver` 按 action/resource 返回 `allow`、attributes 和
  typed row predicates；裸 `visible` 不作为直接查询授权。
- 平台提供标准 HTTP Bean，TM/QM 作者正常使用 `get/post` 时无需自行创建 Bean。
- 完整 FSScript evaluator 属于作者/管理面，不能成为普通查询用户的网络调用旁路。
- 本迭代列权限只控制字段可发现与可引用，不实现列值脱敏或动态 masking。
- 模型元数据、字段建议、维度成员和缓存使用同一授权签名，不能跨权限身份枚举或串读。
- 行权限在叶子 scan、预聚合匹配和缓存前求值；候选无法完整表达权限时跳过并回源。
- 现有预聚合按 `GLOBAL` 规则处理；不支持 `SECURITY_SCOPED` 时配置必须显式失败，不能把用户
  权限快照降级成全局共享表。
- 存量 TM/QM 不要求迁移：未声明 `modelPermissions`、既有 `fieldPermissions`、`accesses`、
  member permissions 和旧 `get/post` 保持兼容。权限安全无法证明时允许预聚合回源，只改变
  执行计划，不改变授权结果。
- `foggy-ai-analysis` 中英文源及配套 `foggy-semantic-query` Skill 的 CLI、权限和预聚合说明
  属于同一交付范围；源码与包校验必须完成，release/publish 仍另行执行。
- `systemSlice` 等治理字段继续只属于可信宿主到引擎的内部契约。

本目录只记录 9.5.1 的交付范围、实现结果和验收证据。当前有效整体架构以
[docs/architecture](../architecture/README.md) 为唯一入口，详细权限决策见
[Runtime 内生权限与预聚合](../architecture/runtime-permissions-and-preaggregation.md)。

## 验收状态

- Add-on 多 Bundle 生命周期：已独立签收，记录见
  [acceptance/addon-bundle-lifecycle-main-port-acceptance.md](acceptance/addon-bundle-lifecycle-main-port-acceptance.md)。
- Runtime 内生权限与权限安全预聚合：`APPROVED`，等待 Ultra 实现与独立签收。
