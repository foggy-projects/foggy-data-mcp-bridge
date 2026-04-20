# S6-内部成员权限-code-inventory

## 文档作用

- doc_type: code-inventory
- intended_for: sub-agent
- purpose: 列出内部成员权限实现涉及的代码触点与改动预期，降低执行 agent 分析成本

## 需求来源

`docs/8.1.10.beta/P1-维度成员内部QM映射-需求.md` → "内部成员权限设计（2026-04-12 补充）"

## 代码清单

### 新增文件

| repo | path | role | expected_change | notes |
|---|---|---|---|---|
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/permission/MemberPermissionDef.java` | TM 维度成员权限配置定义 | create | 包含 patch + queryBuilder 两部分 |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/permission/MemberPermissionPatchDef.java` | patch 子结构定义 | create | visibleColumns / forcedSlice / forcedOrderBy / hierarchyEnabled / allowedHierarchyOps |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/permission/MemberPermissionSliceDef.java` | forcedSlice 单项定义 | create | field / op / value / valueBuilder |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/permission/QmMemberPermissionDef.java` | QM 成员权限配置定义 | create | dimension + patch + queryBuilder |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/permission/SyntheticMemberEffectivePermission.java` | TM+QM 合并后的运行时权限对象 | create | 供 PatchStep 和 QueryBuilderStep 消费 |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/permission/SyntheticMemberPermissionResolver.java` | 权限解析与合并器 | create | TM.patch → QM.patch 合并，queryBuilder 列表收集 |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/SyntheticMemberInternalPatchStep.java` | beforeQuery 内部 patch 步骤 | create | @Order(-20)，在 external patch 之前执行 |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/SyntheticMemberQueryBuilderStep.java` | SQL 级 queryBuilder 执行步骤 | create | 在 SQL 构建阶段执行 TM+QM queryBuilder |

> 包路径 `...` 代表 `com/foggyframework/dataset/db/model`，具体 package 由执行 agent 按现有目录结构决定。

### 改动文件

| repo | path | role | expected_change | notes |
|---|---|---|---|---|
| foggy-data-mcp-bridge | `foggy-dataset-model/.../def/dimension/DbDimensionDef.java` | TM 维度定义 | update | 新增 `MemberPermissionDef memberPermission` 字段 + getter/setter |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../def/query/DbQueryModelDef.java` | QM 定义 | update | 新增 `List<QmMemberPermissionDef> memberPermissions` 字段 + getter/setter |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/SyntheticMemberExternalPatchStep.java` | 外部 patch 步骤 | update | 可能需要调整 Order 或与内部 patch 协调 |

### FSScript loader 改动

| repo | path | role | expected_change | notes |
|---|---|---|---|---|
| foggy-data-mcp-bridge | `foggy-fsscript/` 下 TM loader 相关 | TM 脚本解析 | update | 解析 `dimensions[].memberPermission` 配置块 |
| foggy-data-mcp-bridge | `foggy-fsscript/` 下 QM loader 相关 | QM 脚本解析 | update | 解析 `memberPermissions[]` 配置块 |

> FSScript loader 的具体改动文件由执行 agent 按 loader v1/v2 实际结构决定。

### 只读参考

| repo | path | role | expected_change | notes |
|---|---|---|---|---|
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/SyntheticMemberQueryModelResolver.java` | schema 解析器 | read-only-analysis | 无需改动，但需理解 schema 字段边界 |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/SyntheticMemberQueryModelFactory.java` | 运行时工厂 | read-only-analysis | 无需改动，权限由 Step 注入 |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../semantic/member/SyntheticMemberExternalPatch.java` | 外部 patch 定义 | read-only-analysis | 参考其结构设计内部 patch |
| foggy-data-mcp-bridge | `foggy-dataset-model/.../plugins/result_set_filter/` | beforeQuery 插件机制 | read-only-analysis | 理解 DataSetResultStep 接口和 @Order 机制 |

### 测试文件

| repo | path | role | expected_change | notes |
|---|---|---|---|---|
| foggy-data-mcp-bridge | `foggy-dataset-model/src/test/.../member/permission/` | 单元测试 | create | 覆盖合并规则、valueBuilder 求值、queryBuilder 执行 |

> 测试具体文件名和组织方式由执行 agent 按现有测试目录结构决定。
