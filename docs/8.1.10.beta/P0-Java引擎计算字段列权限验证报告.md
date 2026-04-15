# P0 - Java 引擎计算字段列权限验证报告

## 文档作用

- doc_type: `verification-report`
- intended_for: `engine-owner | execution-agent | reviewer`
- purpose: 将 workspace root 的 Java 引擎验证结论下放到 `foggy-data-mcp-bridge`，作为本仓库后续能力整改的直接输入

## 基本信息

- 目标版本：`8.1.10.beta`
- 优先级：`P0`
- 状态：`handoff`
- 责任项目：`foggy-data-mcp-bridge`
- 上游目标文档：`docs/v1.3/P0-引擎计算字段列权限能力整改目标-需求.md`（workspace root）
- 上游验证报告：`docs/v1.3/java-engine-calculated-field-permission-verification.md`（workspace root）

## 结论

- 结论：**不支持**
- 适用范围：
  - Java 引擎具备 metadata 构建、字段过滤、`query_model`、inline expression、calculated field 等一般能力
  - 但没有发现 query-time 的 `fieldAccess` / `visibleFields` / 等价列权限输入与校验链路
  - 因而不存在“计算字段依赖源列权限”的引擎侧能力入口
- 风险点：
  - metadata `fields` 过滤只能裁剪 metadata 输出，不能等价成查询执行时的列权限
  - 现有表达式相关测试全部是“表达式功能测试”，不是“权限测试”
  - 若 bridge 把 `field.groups` 直接视为可由 Java 引擎兜底，会产生错误架构假设

## 问题定义

本报告验证 `foggy-dataset-model` Java 引擎侧是否真的支持以下能力：

1. metadata / describe_model / get_metadata 场景下的字段裁剪
2. query_model / DSL 执行时的列权限校验
3. 计算字段依赖源列权限的正确处理
4. 重点场景：
   - QM 预定义计算字段
   - DSL 内联表达式 `a + b as c`
   - 聚合表达式 `sum(a + b) as total`
   - 只在 `orderBy` / `filter` 中引用计算表达式

## 代码链路

### 1. metadata / get_metadata

- [SemanticMetadataRequest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticMetadataRequest.java)
  - 只有 `qmModels`、`fields`、`includeExamples`、`levels`
- [SemanticServiceV3Impl.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticServiceV3Impl.java)
  - metadata 构建时会按 `request.getFields()` 过滤字段

### 2. query_model / DSL 执行

- [SemanticQueryRequest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticQueryRequest.java)
  - 有 `columns`、`calculatedFields`、`slice`、`groupBy`、`orderBy`
  - **没有** `fieldAccess`
  - **没有** `visibleFields`
  - **没有** 等价的 query-time 列权限输入
- [SemanticQueryServiceV3Impl.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java)
  - `queryModel()` → `buildJdbcRequest()` → `QueryFacade.queryModelResult(...)`
  - 只做字段存在性、groupBy 对齐、普通语义校验
  - 未发现“用户可见字段白名单”或“源列权限校验”逻辑

### 3. 表达式 / 计算字段能力

- [InlineExpressionParserTest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/expression/InlineExpressionParserTest.java)
- [CalculatedFieldAggregationBugTest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/CalculatedFieldAggregationBugTest.java)

这类实现与测试证明 Java 引擎支持表达式功能，但**不等于**支持列权限。

## 已有测试证据

### 现有测试文件

- [SemanticServiceV3Test.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/SemanticServiceV3Test.java)
- [SemanticQueryValidationTest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/semantic/SemanticQueryValidationTest.java)
- [InlineExpressionParserTest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/expression/InlineExpressionParserTest.java)
- [CalculatedFieldAggregationBugTest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/test/java/com/foggyframework/dataset/db/model/ecommerce/CalculatedFieldAggregationBugTest.java)

### 实际运行命令

```powershell
mvn -pl foggy-dataset-model "-Dtest=SemanticServiceV3Test,SemanticQueryValidationTest,InlineExpressionParserTest,CalculatedFieldAggregationBugTest" test
```

### 测试结果摘要

- `Tests run: 65, Failures: 0, Errors: 0, Skipped: 0`

这些测试证明：

- Java 引擎的 metadata / query / expression 功能本身存在

这些测试没有证明：

- query_model 请求里可以传用户列权限
- 计算字段会按源列权限做拒绝/过滤
- `a + b as c` 且 `b` 无权限时有正确处理

## 分类型结论

### 1. QM 预定义计算字段

- 结论：**权限能力不支持**
- 说明：
  - Java 有 calculated field 功能
  - 但没有 query-time 列权限输入，无法谈“按源列权限处理”

### 2. 查询时 DSL 内联表达式 `a + b as c`

- 结论：**不支持**
- 说明：
  - 不是“证据还没补齐”的问题
  - 而是请求模型和执行链路里没有任何列权限输入/校验入口

### 3. 聚合表达式 `sum(a + b) as total`

- 结论：**不支持**

### 4. 只在 orderBy / filter 中引用计算表达式

- 结论：**不支持**

## 缺口分析

### 缺哪类测试

从“测试补齐”角度看，当前缺的是：

1. query-time 列权限输入 DTO 测试
2. query_model 拒绝无权限源字段表达式测试
3. orderBy / filter 中表达式权限测试

### 为什么不能只写“证据不足”

因为问题不只是“没测试”，而是能力入口本身不存在：

1. [SemanticQueryRequest.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/domain/SemanticQueryRequest.java) 里没有 permission DTO
2. [SemanticQueryServiceV3Impl.java](D:/foggy-projects/foggy-data-mcp/foggy-data-mcp-bridge/foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/semantic/service/impl/SemanticQueryServiceV3Impl.java) 里没有权限校验链路

### 建议最小整改

1. request DTO 增加 `fieldAccess` 或等价结构
2. service 层在 query 前校验 `columns` / `calculatedFields` / `slice` / `orderBy` / `filter`
3. 为表达式引入依赖字段提取
4. 对无法解析依赖的表达式 fail-closed
5. 补齐以下测试：
   - `a + b as c` 且 `b` 无权限
   - `sum(a + b)` 且 `b` 无权限
   - `orderBy` / `filter` 中表达式引用

## 是否可以支撑 `odoo-bridge-pro` 按 `field.groups` 落地列权限

- 结论：**不能**

原因：

1. Java 引擎当前没有 query-time 列权限输入能力
2. 没有计算字段源列权限校验逻辑
3. 已有测试只证明表达式功能，不证明权限能力

因此，bridge 不能把 Java 引擎当作 `field.groups` 的列权限兜底方。

## 关联文档

- [P0-引擎计算字段列权限能力整改目标-需求.md](D:/foggy-projects/foggy-data-mcp/docs/v1.3/P0-引擎计算字段列权限能力整改目标-需求.md)
- [java-engine-calculated-field-permission-verification.md](D:/foggy-projects/foggy-data-mcp/docs/v1.3/java-engine-calculated-field-permission-verification.md)
