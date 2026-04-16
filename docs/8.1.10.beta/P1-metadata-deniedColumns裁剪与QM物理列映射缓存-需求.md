# P1 - metadata 按 deniedColumns 裁剪 + QM↔物理列映射缓存

## 文档作用

- doc_type: `requirement`
- intended_for: `engine-owner | execution-agent`
- purpose: 定义 metadata 按物理列黑名单裁剪的需求，以及支撑该功能的 QM↔物理列映射缓存架构

## 基本信息

- 目标版本：`8.1.10.beta`（后续）
- 需求等级：`P1`
- 状态：`draft`
- 前置依赖：`deniedColumns` 物理列权限已实现（commit `d9a37cb`）

## 背景

`PhysicalColumnPermissionStep` 已在查询路径按 `deniedColumns` 拦截受限物理列（SQL 构建后、执行前）。但 metadata 路径尚未按 deniedColumns 裁剪——AI 仍可看到受限字段的 metadata，可能尝试查询后被拒。

同时，多个功能场景需要 QM 字段名 ↔ 物理 table.column 的映射：
- S7 metadata 裁剪
- 未来 WHERE 条件物理列检查
- 未来审计日志（QM 字段→物理列追踪）

## 方案：QM 加载时预构建映射缓存

### 映射结构

在 `JdbcQueryModel`（或独立 `QmPhysicalColumnMapping` 类）中缓存：

```java
// QM 字段名 → 物理列引用列表
// 例：
//   "salesAmount" → [{table:"fact_sales", column:"sales_amount"}]
//   "product$id"  → [{table:"fact_sales", column:"product_id"},
//                     {table:"dim_product", column:"product_id"}]
//   "product$caption" → [{table:"dim_product", column:"product_name"}]
Map<String, List<PhysicalColumnRef>> qmFieldToPhysical;

// 物理列 → QM 字段名列表
// 例：
//   {table:"fact_sales", column:"sales_amount"} → ["salesAmount"]
//   {table:"dim_product", column:"product_name"} → ["product$caption"]
Map<PhysicalColumnRef, List<String>> physicalToQmFields;
```

### 构建时机

QM 模型加载完成后（`QueryModelLoaderImpl` 或 `JdbcQueryModelBuilder`），遍历：

| 字段类型 | 来源 | 映射方式 |
|---|---|---|
| 度量 | `TM.measures[].column` + `TM.tableName` | 一对一 |
| 属性 | `TM.properties[].column` + `TM.tableName` | 一对一 |
| 维度 $id | `dimension.foreignKey` + `TM.tableName`<br>`dimension.primaryKey` + 维度表名 | 一对多（FK+PK） |
| 维度 $caption | `dimension.captionColumn` + 维度表名 | 一对一 |
| 维度属性 | `dimension.properties[].column` + 维度表名 | 一对一 |
| 计算字段 | `resolveBaseColumnReferences()` → 递归到基础字段 → 查映射 | 传递展开 |

### S7 实现

有了映射缓存后，metadata 裁剪变为：

```java
// processModelFieldsV3 中
for (DbMeasure measure : jdbcModel.getMeasures()) {
    String fieldName = measure.getName();
    List<PhysicalColumnRef> physCols = qmPhysicalMapping.get(fieldName);
    if (isAnyDenied(physCols, deniedColumns)) {
        continue; // 跳过受限度量
    }
    // ... 输出字段
}
```

### 额外收益：WHERE 物理列检查

有了映射缓存，`PhysicalColumnPermissionStep` 可以增强：
- 在 `beforeExecute` 中，除了检查 JdbcQuery 的 SELECT/ORDER/GROUP
- 还可以从 `ModelResultContext.request.slice` 中取 QM 字段名
- 通过映射查物理列
- 检查 deniedColumns

这样无需解析 SQL 片段即可覆盖 WHERE 条件。

### systemSlice 安全边界

- Java 引擎当前无 systemSlice
- 等价机制 `forcedSlice` / `accesses.queryBuilder` 是系统注入的安全条件
- 这些条件**不应被 deniedColumns 拦截**
- 解决方案：只检查 `request.slice`（用户请求），不检查系统注入的条件

## 实现顺序建议

1. 先实现映射缓存（在 QM 加载时构建）
2. 用映射缓存实现 metadata 裁剪（S7）
3. 用映射缓存增强 WHERE 物理列检查（可选增强）
