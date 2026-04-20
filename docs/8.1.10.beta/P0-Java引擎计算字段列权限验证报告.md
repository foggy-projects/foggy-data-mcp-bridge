# P0 - Java 引擎计算字段列权限验证报告

## 文档作用

- doc_type: `verification-report`
- intended_for: `engine-owner | execution-agent | reviewer`
- purpose: 记录 `foggy-dataset-model` Java 引擎 fieldAccess 列权限能力的实现与测试验证结论

## 基本信息

- 目标版本：`8.1.10.beta`
- 优先级：`P0`
- 状态：`implemented`
- 责任项目：`foggy-data-mcp-bridge`
- 上游目标文档：`docs/v1.3/P0-引擎计算字段列权限能力整改目标-需求.md`（workspace root）
- 上游验证报告：`docs/v1.3/java-engine-calculated-field-permission-verification.md`（workspace root）
- 实现 commit：`cabdd34` feat(dataset-model): add query-time field access permission
- 测试 commit：`e9c7d21` test(dataset-model): comprehensive security test coverage

## 结论

- 结论：**已支持**
- 适用范围：
  - Java 引擎已具备 query-time `fieldAccess` 列权限输入与校验链路
  - 计算字段依赖源列权限校验已实现（AST 递归依赖提取 + fail-closed）
  - metadata 输出按 fieldAccess 过滤已实现（JSON + Markdown，含计算字段依赖检查）
  - 55 个 fieldAccess 专项测试全部通过，全量 889 测试 0 回归

## 问题定义

本报告验证 `foggy-dataset-model` Java 引擎侧是否支持以下能力：

1. metadata / describe_model / get_metadata 场景下按 fieldAccess 裁剪字段
2. query_model / DSL 执行时的列权限校验
3. 计算字段依赖源列权限的正确处理
4. 重点场景：
   - QM 预定义计算字段
   - DSL 内联表达式 `a + b as c`
   - 聚合表达式 `sum(a + b) as total`
   - 只在 `orderBy` / `filter` / `slice` 中引用计算表达式

## 实现摘要

### 新增核心组件

| 组件 | 职责 |
|---|---|
| `FieldAccessPermissionStep` (`@Order -25`) | beforeQuery 步骤，校验 columns / calculatedFields / slice / orderBy / groupBy 是否在 fieldAccess 白名单内 |
| `SemanticRequestContext.fieldAccess` | query-time 列权限输入（`null` = 无限制） |
| `ModelResultContext.fieldAccess` | pipeline 内传播 |
| `CalculatedFieldService.extractColumnReferences(String)` | 公共 API，AST 递归提取表达式依赖字段 |
| `SemanticServiceV3Impl` metadata 过滤 | JSON + Markdown 输出按 fieldAccess 裁剪，含 `isCalculatedFieldAccessible()` 依赖检查 |

### 安全措施

- **fail-closed**：无法解析依赖的表达式默认拒绝
- **维度后缀剥离**：`$id` / `$caption` / `$property` 后缀在权限检查前自动剥离
- **AST 递归依赖提取**：对计算字段表达式做完整递归遍历，提取所有引用列
- **防御性拷贝**：`SemanticRequestContext` 使用 `Set.copyOf` + `Collections.unmodifiableSet` 防止并发修改

## 代码链路

### 1. metadata / get_metadata

- `SemanticRequestContext.fieldAccess` — query-time 列权限白名单输入
- `SemanticServiceV3Impl` — metadata 构建时按 fieldAccess 过滤字段，计算字段通过 `isCalculatedFieldAccessible()` 检查依赖列是否全部在白名单内

### 2. query_model / DSL 执行

- `SemanticRequestContext.fieldAccess` — 列权限白名单
- `FieldAccessPermissionStep` (`@Order -25`) — beforeQuery 步骤，在查询执行前校验：
  - `columns` 中的每个字段
  - `calculatedFields` 中每个表达式的依赖列
  - `slice` 中引用的字段
  - `orderBy` 中引用的字段
  - `groupBy` 中引用的字段
- 校验失败时抛出异常，阻止查询执行

### 3. 表达式 / 计算字段依赖提取

- `CalculatedFieldService.extractColumnReferences(String)` — AST 递归遍历表达式树，提取所有列引用
- 支持嵌套函数调用、算术运算、聚合函数内的列引用提取
- 无法解析时返回空集，由调用方执行 fail-closed 策略

## 测试证据

### fieldAccess 专项测试（55 个）

| 测试类 | 用例数 | 覆盖范围 |
|---|---|---|
| `CalculatedFieldServiceTest` | 12 | 表达式依赖提取（简单/嵌套/聚合/不可解析） |
| `FieldAccessPermissionStepTest` | 24 | 单元测试：权限校验、安全边界、维度后缀、fail-closed |
| `FieldAccessPermissionIntegrationTest` | 8 | 全链路集成测试，真实 SQL 数据比对 |
| `SemanticServiceV3Test.MetadataFieldAccessTests` | 4 | metadata 按 fieldAccess 过滤输出 |
| `SemanticRequestContextTest` | 7 (新增) | factory 方法、防御性拷贝、不可变性 |

### 全量测试基线

```
Tests run: 889, Failures: 0, Errors: 0, Skipped: 0
```

0 回归。

## 分类型结论

### 1. QM 预定义计算字段

- 结论：**已支持**
- 说明：
  - `FieldAccessPermissionStep` 对计算字段调用 `extractColumnReferences()` 提取依赖列
  - 依赖列中任一不在 fieldAccess 白名单内则拒绝
  - 无法解析依赖时 fail-closed

### 2. 查询时 DSL 内联表达式 `a + b as c`

- 结论：**已支持**
- 说明：
  - 内联表达式经 AST 解析提取 `a`、`b` 两个依赖列
  - 两个依赖列均须在 fieldAccess 白名单内

### 3. 聚合表达式 `sum(a + b) as total`

- 结论：**已支持**
- 说明：
  - AST 递归进入聚合函数参数，提取 `a`、`b`
  - 权限校验逻辑与内联表达式一致

### 4. orderBy / filter / slice 中引用字段

- 结论：**已支持**
- 说明：
  - `FieldAccessPermissionStep` 对 orderBy、slice 中的字段引用逐一校验
  - 维度字段自动剥离 `$id` / `$caption` / `$property` 后缀后匹配

## 是否可以支撑 `odoo-bridge-pro` 按 `field.groups` 落地列权限

- 结论：**可以**

原因：

1. Java 引擎已具备 query-time `fieldAccess` 列权限输入能力
2. 计算字段源列权限校验已实现（AST 依赖提取 + fail-closed）
3. metadata 输出已按 fieldAccess 过滤
4. 55 个专项测试覆盖全部 4 类场景

bridge 可将 Odoo `field.groups` 解析为 `fieldAccess` 白名单，传递给 Java 引擎执行列权限校验。

## 关联文档

- [P0-引擎计算字段列权限能力整改目标-需求.md](D:/foggy-projects/foggy-data-mcp/docs/v1.3/P0-引擎计算字段列权限能力整改目标-需求.md)
- [java-engine-calculated-field-permission-verification.md](D:/foggy-projects/foggy-data-mcp/docs/v1.3/java-engine-calculated-field-permission-verification.md)
