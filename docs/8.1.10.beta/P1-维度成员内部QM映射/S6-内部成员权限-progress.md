# S6-内部成员权限-progress

## 文档作用

- doc_type: progress
- intended_for: sub-agent / reviewer
- purpose: 跟踪内部成员权限实施进度，对照 implementation-plan 的每个 Step

## 基本信息

- 版本：8.1.10.beta
- 阶段：S6 — 内部成员权限
- 状态：核心实现已完成，集成测试待补充
- 上游文档：
  - 需求：`P1-维度成员内部QM映射-需求.md`
  - 代码清单：`S6-内部成员权限-code-inventory.md`
  - 实施计划：`S6-内部成员权限-implementation-plan.md`
  - 开发提示词：`S6-开发提示词.md`
- 完成日期：2026-04-12（核心实现）

## 前置条件检查

| 前置条件 | 状态 |
|---|---|
| 阶段 1-5 已完成 | ✅ |
| SyntheticMemberExternalPatchStep 运行稳定 | ✅ |
| FSScript loader 正常解析 TM/QM | ✅ |

## Development Progress

| Step | 内容 | 状态 | 备注 |
|---|---|---|---|
| Step 1 | 配置定义与 Def 类 | ✅ | 5 个 Def 类 + 2 个 runtime 字段 |
| Step 2 | FSScript loader 解析承载 | ✅ | TM 自动 copy，QM step42 显式加载 |
| Step 3 | 权限合并器 SyntheticMemberPermissionResolver | ✅ | 14 个单元测试全通过 |
| Step 4 | 内部 patch 注入 SyntheticMemberInternalPatchStep | ✅ | @Order(-20)，含 valueBuilder 求值和 hierarchy 校验 |
| Step 5 | queryBuilder 执行 SyntheticMemberQueryBuilderStep | ✅ | @Order(100)，TM→QM 顺序执行 |
| Step 6 | 集成测试与文档收口 | 🔶 | 单元测试已完成，集成测试待补充 |

## 计划外变更

- `DbDimensionSupport` 新增 `memberPermission` 字段，利用 `BeanUtils.copyProperties` 自动从 Def copy — 比原计划更简洁
- `QueryModelSupport` 新增 `memberPermissions` 字段 — 与 `accessBuilders` 对称设计
- 实施顺序调整：先做 Step 3（合并器）再做 Step 2（loader），因为合并器无依赖

## Testing Progress

| 用例 | 场景 | 结果 | 备注 |
|---|---|---|---|
| 两边都为空返回空 effective | Step 3 | ✅ | |
| 仅 TM visibleColumns | Step 3 | ✅ | |
| 仅 TM forcedSlice | Step 3 | ✅ | |
| 仅 QM visibleColumns | Step 3 | ✅ | |
| visibleColumns QM 覆盖 TM | Step 3 | ✅ | |
| forcedSlice 同字段 QM 替换 | Step 3 | ✅ | |
| forcedSlice 不同字段合并 | Step 3 | ✅ | |
| forcedOrderBy 同字段覆盖 | Step 3 | ✅ | |
| hierarchyEnabled QM 覆盖 | Step 3 | ✅ | |
| allowedHierarchyOps QM 覆盖 | Step 3 | ✅ | |
| queryBuilder 收集 | Step 3 | ✅ | |
| 静态 value 求值 | Step 3 | ✅ | |
| 混合综合场景 | Step 3 | ✅ | |
| 全量 SQLite 回归 | 回归 | ✅ | 798 passed, 0 failed |
| 旧 TM/QM 向后兼容 | 回归 | ✅ | 全量测试覆盖 |
| InternalPatchStep 集成 | Step 4 | 待补 | 需要 .tm/.qm 带权限配置 |
| QueryBuilderStep 集成 | Step 5 | 待补 | 需要 FsscriptFunction 脚本 |
| 内部+外部 patch 叠加 | Step 4 | 待补 | 需要 Spring 容器 |

## Experience Progress

- experience: N/A（纯后端 / 无 UI）

## 需求验收标准对照

| 验收标准 | 状态 | 证据 |
|---|---|---|
| TM.dimensions[].memberPermission 可声明配置 | ✅ | DbDimensionDef + DbDimensionSupport 字段已就位 |
| QM.memberPermissions[] 可声明配置 | ✅ | DbQueryModelDef + QueryModelSupport + loader step42 |
| patch 合并规则正确 | ✅ | 14 个单元测试全通过 |
| valueBuilder(context) 可动态求值 | ✅ | MemberPermissionSliceDef.resolveValue() 实现 |
| queryBuilder(context) 可在 SQL 级追加条件 | ✅ | SyntheticMemberQueryBuilderStep 实现 |
| TM.queryBuilder + QM.queryBuilder 按顺序执行 | ✅ | PermissionResolver 收集 + QueryBuilderStep 遍历 |
| 内部 patch → external patch → request 优先级正确 | ✅ | @Order(-20) → @Order(-10) → request |
| 不含 memberPermission 的旧模型不受影响 | ✅ | 798 全量测试零回归 |

## 阻塞项

无

## 后续衔接

- [x] dev-log 写入 `dev-logs/S6-20260412-内部成员权限.md`
- [x] test-record 写入 `test-records/T6-20260412-内部成员权限.md`
- [ ] 补充集成测试（.tm/.qm 带 memberPermission 配置 + Spring 容器测试）
- [ ] 执行 `foggy-implementation-quality-gate`
- [ ] 执行 `foggy-test-coverage-audit`
- [ ] 更新需求文档状态
