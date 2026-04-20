# S6-内部成员权限-implementation-plan

## 文档作用

- doc_type: implementation-plan
- intended_for: sub-agent
- purpose: 定义内部成员权限的实施步骤、前置条件、完成定义与验收标准

## 需求来源

- 需求文档：`docs/8.1.10.beta/P1-维度成员内部QM映射-需求.md` → "内部成员权限设计"
- 代码清单：`S6-内部成员权限-code-inventory.md`

## 前置条件

- [x] 阶段 1-5 已完成：synthetic member-QM 解析、QueryFacade 接入、嵌套/层级验证、external patch、缓存
- [x] `SyntheticMemberExternalPatchStep` 已运行稳定
- [x] FSScript loader 可正常解析 TM/QM 配置

## 模块职责

| 角色 | 负责范围 |
|---|---|
| root (foggy-data-mcp-bridge) | 所有改动在本仓完成，无跨仓依赖 |
| foggy-dataset-model | 配置定义、权限解析器、内部 patch/queryBuilder 步骤 |
| foggy-fsscript | TM/QM loader 中新增 memberPermission 配置块解析 |
| 测试 | foggy-dataset-model 单元测试 |

## 实施步骤

### Step 1：配置定义与 Def 类

**目标**：建立 TM/QM 成员权限的配置对象体系

**内容**：
1. 创建 `permission/` 子包，包含：
   - `MemberPermissionDef` — TM 维度级配置（patch + queryBuilder）
   - `MemberPermissionPatchDef` — patch 子结构（visibleColumns / forcedSlice / forcedOrderBy / hierarchyEnabled / allowedHierarchyOps）
   - `MemberPermissionSliceDef` — forcedSlice 单项（field / op / value / valueBuilder）
   - `QmMemberPermissionDef` — QM 级配置（dimension + patch + queryBuilder）
2. 在 `DbDimensionDef` 新增 `MemberPermissionDef memberPermission`
3. 在 `DbQueryModelDef` 新增 `List<QmMemberPermissionDef> memberPermissions`

**完成标准**：
- 编译通过
- Def 类字段与需求文档一致
- 单元测试覆盖序列化/反序列化

### Step 2：FSScript loader 解析承载

**目标**：让 TM/QM 脚本中声明的 memberPermission 配置能被 loader 正确解析

**内容**：
1. TM loader 解析 `dimensions[].memberPermission` 配置块，填充到 `DbDimensionDef`
2. QM loader 解析 `memberPermissions[]` 配置块，填充到 `DbQueryModelDef`
3. `valueBuilder` 和 `queryBuilder` 在 loader 阶段保留为 FSScript 函数引用，不立即求值

**完成标准**：
- 编写带 memberPermission 的 .tm/.qm 测试文件
- loader 解析后 Def 对象字段正确填充
- 不含 memberPermission 的旧 .tm/.qm 不受影响（向后兼容）

### Step 3：权限合并器 — SyntheticMemberPermissionResolver

**目标**：实现 TM + QM → effective permission 的合并逻辑

**内容**：
1. 创建 `SyntheticMemberPermissionResolver`
2. 输入：TM 维度的 `MemberPermissionDef` + QM 的 `QmMemberPermissionDef`（按 dimension 匹配）
3. 输出：`SyntheticMemberEffectivePermission`
4. 合并规则（按需求文档）：
   - `visibleColumns`：QM 覆盖 TM
   - `forcedSlice`：同字段 QM 完全替换 TM，不同字段合并保留
   - `forcedOrderBy`：同字段 QM 覆盖 TM
   - `hierarchyEnabled`：QM 覆盖 TM
   - `allowedHierarchyOps`：QM 覆盖 TM
   - `queryBuilder`：TM 和 QM 都保留，按 TM→QM 顺序执行

**完成标准**：
- 单元测试覆盖：
  - 仅 TM 有 patch
  - 仅 QM 有 patch
  - TM+QM 合并（同字段替换、不同字段保留）
  - TM+QM queryBuilder 列表收集
  - 两边都为空时返回空 effective

### Step 4：内部 patch 注入 — SyntheticMemberInternalPatchStep

**目标**：在 beforeQuery 阶段注入内部权限 patch，在 external patch 之前执行

**内容**：
1. 创建 `SyntheticMemberInternalPatchStep implements DataSetResultStep`
2. `@Order(-20)` — 在 external patch（-10）之前执行
3. 流程：
   a. 检测是否为 synthetic member-QM
   b. 从源 QM + TM 获取 effective permission
   c. 对 `forcedSlice` 中的 `valueBuilder` 求值
   d. 将内部 patch 写入 request（visibleColumns / forcedSlice / forcedOrderBy / hierarchy 校验）
4. 内部 patch 写入后，后续 external patch 再覆盖

**完成标准**：
- 内部 patch 可正确注入 visibleColumns / forcedSlice / forcedOrderBy
- valueBuilder 可正确求值（含 context 上下文）
- hierarchyEnabled=false 时层级操作被拦截
- 与 external patch 的叠加优先级正确：internal → external → request

### Step 5：queryBuilder 执行 — SyntheticMemberQueryBuilderStep

**目标**：在 SQL 构建阶段执行 TM+QM 的 queryBuilder 脚本

**内容**：
1. 创建 `SyntheticMemberQueryBuilderStep`
2. 在 SQL 构建阶段（beforeQuery 之后、实际 SQL 生成时）执行
3. 构建统一上下文：
   - `context.query` — JdbcQuery 代理
   - `context.member` — synthetic member-QM 字段代理
   - `context.queryModel` — 当前 queryModel
   - `context.request` — 当前 request
   - `context.security` — 安全上下文
   - `context.extData` — 扩展数据
   - `context.namespace` — 命名空间
4. 按 TM→QM 顺序执行 queryBuilder 列表

**完成标准**：
- queryBuilder 可在 JdbcQuery 上追加 where / in 条件
- context.member 只能访问 synthetic member-QM 字段空间
- TM 和 QM 的 queryBuilder 按顺序执行
- 无 queryBuilder 时不影响正常查询

### Step 6：集成测试与文档收口

**目标**：端到端验证完整链路、补齐文档

**内容**：
1. 编写集成测试覆盖：
   - TM 有权限 + QM 无权限
   - TM 无权限 + QM 有权限
   - TM+QM 合并 + external patch 叠加
   - valueBuilder 动态求值
   - queryBuilder SQL 级条件注入
   - hierarchyEnabled 开关
2. 更新 dev-logs 和 test-records
3. 更新需求文档状态

**完成标准**：
- 所有测试通过
- dev-logs 有本阶段记录
- test-records 有本阶段记录
- 需求文档状态更新

## 执行优先级链

```
schema → TM.patch → QM.patch → external patch → request → TM.queryBuilder → QM.queryBuilder
```

对应执行链路中的 Step 顺序：

```
SyntheticMemberInternalPatchStep (@Order -20)
  → SyntheticMemberExternalPatchStep (@Order -10)
    → 其他 beforeQuery Steps
      → SQL 生成
        → SyntheticMemberQueryBuilderStep
          → SQL 执行
```

## 安全边界

- queryBuilder 安全依赖开发者自律，不做运行时沙箱
- QM 权限配置仅面向技术人员
- 业务人员后续通过白名单函数二次开发，不直接接触脚本层
- forcedSlice 同字段以后者完全替换前者

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| valueBuilder 上下文不稳定 | Step 4 中严格定义 context 字段，测试覆盖 |
| 内部 patch 与 external patch 交互异常 | Step 4 测试覆盖叠加场景 |
| FSScript loader 改动影响旧模型 | Step 2 验证向后兼容 |
| queryBuilder 执行时机与现有 accesses 冲突 | Step 5 参照现有 accesses.queryBuilder 实现模式 |

## 完成后衔接

1. 更新 `docs/8.1.10.beta/P1-维度成员内部QM映射/dev-logs/`
2. 更新 `docs/8.1.10.beta/P1-维度成员内部QM映射/test-records/`
3. 更新需求文档 `P1-维度成员内部QM映射-需求.md` 状态
4. 执行 `foggy-implementation-quality-gate`
5. 执行 `foggy-test-coverage-audit`
