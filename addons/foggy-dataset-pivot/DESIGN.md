# Foggy Dataset Pivot - 设计文档

> **状态**: 设计阶段，待用户反馈后决定是否实现

## 1. 背景与需求

### 1.1 问题描述

当前 DSL 查询返回扁平的行列表格式：

```json
{
  "items": [
    { "year": 2024, "month": 1, "amount": 100 },
    { "year": 2024, "month": 2, "amount": 200 },
    { "year": 2025, "month": 1, "amount": 120 },
    { "year": 2025, "month": 2, "amount": 180 }
  ]
}
```

用户需要**交叉表/透视表**格式，类似 Excel PivotTable 或 MDX 多轴查询：

```
           1月    2月    3月    ...   12月
2024年     100    200    150    ...   300
2025年     120    180    160    ...   350
```

### 1.2 典型场景

- 年度同比分析：按年份对比各月销售额
- 产品矩阵：行是产品类别，列是地区，值是销量
- 时间序列对比：多个指标按时间维度展开

---

## 2. 设计目标

| 目标 | 说明 |
|------|------|
| **低侵入** | 核心模块改动最小化，pivot 逻辑在 addon 中 |
| **向后兼容** | 不传 pivot 参数时行为完全不变 |
| **可选引入** | 通过 Maven 依赖按需启用 |
| **易于理解** | DSL 语法简洁直观 |

---

## 3. DSL 语法设计

### 3.1 请求格式

在 `DbQueryRequestDef` 中新增 `pivot` 字段：

```json
{
  "page": 1,
  "pageSize": 100,
  "param": {
    "columns": ["orderDate$year", "orderDate$month", "totalAmount"],
    "groupBy": [
      { "field": "orderDate$year" },
      { "field": "orderDate$month" }
    ],
    "slice": [
      { "field": "orderDate$year", "op": "in", "value": [2024, 2025] }
    ],
    "pivot": {
      "rows": ["orderDate$year"],
      "columns": ["orderDate$month"],
      "values": ["totalAmount"]
    }
  }
}
```

### 3.2 PivotConfig 结构

```java
@Data
public class PivotConfig {
    /**
     * 行轴字段列表
     * 这些字段的值将作为结果的行标识
     */
    List<String> rows;

    /**
     * 列轴字段
     * 该字段的不同值将展开为结果的列
     * 注意：只支持单字段展开为列
     */
    String columns;

    /**
     * 值字段列表
     * 交叉点的数据值，支持多个度量
     */
    List<String> values;

    /**
     * 列排序方式
     * ASC: 升序（默认）
     * DESC: 降序
     * NATURAL: 按数据出现顺序
     */
    String columnOrder = "ASC";

    /**
     * 空值填充
     * 当某个交叉点无数据时的填充值
     */
    Object fillValue = null;

    /**
     * 是否包含行合计
     */
    boolean includeRowTotal = false;

    /**
     * 是否包含列合计
     */
    boolean includeColumnTotal = false;
}
```

### 3.3 响应格式

**方案 A：扁平化列名（推荐）**

将列轴值展开为列名，保持 items 结构：

```json
{
  "items": [
    {
      "orderDate$year": 2024,
      "totalAmount@1": 100,
      "totalAmount@2": 200,
      "totalAmount@3": 150,
      ...
      "totalAmount@12": 300
    },
    {
      "orderDate$year": 2025,
      "totalAmount@1": 120,
      "totalAmount@2": 180,
      ...
    }
  ],
  "pivotMeta": {
    "columnField": "orderDate$month",
    "columnValues": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
    "valueFields": ["totalAmount"],
    "columnNamePattern": "{value}@{column}"
  }
}
```

**方案 B：结构化 pivotData**

新增独立的 pivotData 结构：

```json
{
  "items": [...],
  "pivotData": {
    "columnHeaders": [1, 2, 3, ..., 12],
    "rows": [
      {
        "rowKey": { "orderDate$year": 2024 },
        "cells": {
          "1": { "totalAmount": 100 },
          "2": { "totalAmount": 200 },
          ...
        }
      },
      {
        "rowKey": { "orderDate$year": 2025 },
        "cells": { ... }
      }
    ],
    "totals": {
      "rowTotals": [...],
      "columnTotals": [...],
      "grandTotal": { "totalAmount": 12500 }
    }
  }
}
```

**建议**：方案 A 更简单，前端处理更方便；方案 B 结构更清晰，适合复杂场景。

---

## 4. 技术实现方案

### 4.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    QueryFacadeImpl                          │
├─────────────────────────────────────────────────────────────┤
│  1. beforeQuery(ctx)  ←── PivotValidationStep: 验证参数     │
│  2. query(ctx)        ←── 正常执行，返回扁平数据            │
│  3. process(ctx)      ←── PivotTransformStep: 转换结果      │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 核心类设计

```
foggy-dataset-pivot/
├── src/main/java/com/foggyframework/dataset/pivot/
│   ├── PivotConfig.java              # Pivot 配置定义
│   ├── PivotMeta.java                # Pivot 元数据（响应中）
│   ├── PivotValidationStep.java      # beforeQuery: 参数验证
│   ├── PivotTransformStep.java       # process: 结果转换
│   ├── PivotTransformer.java         # 核心转换逻辑
│   └── autoconfigure/
│       └── PivotAutoConfiguration.java  # Spring Boot 自动配置
└── src/main/resources/
    └── META-INF/
        └── spring/
            └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### 4.3 核心算法

```java
public class PivotTransformer {

    public PivotResult transform(List<Map<String, Object>> items, PivotConfig config) {
        // 1. 提取所有列轴值并排序
        Set<Object> columnValues = extractColumnValues(items, config.getColumns());
        List<Object> sortedColumns = sortColumns(columnValues, config.getColumnOrder());

        // 2. 按行轴分组
        Map<Object, List<Map<String, Object>>> rowGroups =
            items.stream().collect(Collectors.groupingBy(
                row -> extractRowKey(row, config.getRows())
            ));

        // 3. 构建 pivot 结果
        List<Map<String, Object>> pivotedItems = new ArrayList<>();
        for (Map.Entry<Object, List<Map<String, Object>>> entry : rowGroups.entrySet()) {
            Map<String, Object> pivotRow = new LinkedHashMap<>();

            // 添加行轴字段
            addRowFields(pivotRow, entry.getKey(), config.getRows());

            // 按列轴展开值字段
            for (Object colValue : sortedColumns) {
                for (String valueField : config.getValues()) {
                    String colName = valueField + "@" + colValue;
                    Object value = findValue(entry.getValue(), config.getColumns(),
                                            colValue, valueField, config.getFillValue());
                    pivotRow.put(colName, value);
                }
            }

            pivotedItems.add(pivotRow);
        }

        return new PivotResult(pivotedItems, buildMeta(config, sortedColumns));
    }
}
```

---

## 5. 改动点分析

### 5.1 核心模块改动（foggy-dataset-model）

| 文件 | 改动 | 影响 |
|------|------|------|
| `DbQueryRequestDef.java` | 新增 `PivotConfig pivot` 字段 | 极小，可选字段 |
| `PagingResultImpl.java` | 新增 `PivotMeta pivotMeta` 字段 | 极小，可选字段 |

### 5.2 Addon 模块（foggy-dataset-pivot）

| 文件 | 说明 |
|------|------|
| `PivotConfig.java` | Pivot 配置 DTO |
| `PivotMeta.java` | Pivot 元数据 DTO |
| `PivotValidationStep.java` | 参数验证 Step |
| `PivotTransformStep.java` | 结果转换 Step |
| `PivotTransformer.java` | 核心转换逻辑 |
| `PivotAutoConfiguration.java` | 自动配置 |

---

## 6. 使用方式

### 6.1 引入依赖

```xml
<dependency>
    <groupId>com.foggysource</groupId>
    <artifactId>foggy-dataset-pivot</artifactId>
</dependency>
```

### 6.2 查询示例

**场景：2024 vs 2025 年各月销售额对比**

```json
POST /jdbc-model/query-model/v2/FactOrderQueryModel

{
  "param": {
    "columns": ["orderDate$year", "orderDate$month", "totalAmount"],
    "slice": [
      { "field": "orderDate$year", "op": "in", "value": [2024, 2025] }
    ],
    "groupBy": [
      { "field": "orderDate$year" },
      { "field": "orderDate$month" }
    ],
    "pivot": {
      "rows": ["orderDate$year"],
      "columns": "orderDate$month",
      "values": ["totalAmount"]
    }
  }
}
```

**响应：**

```json
{
  "items": [
    { "orderDate$year": 2024, "totalAmount@1": 100, "totalAmount@2": 200, ... },
    { "orderDate$year": 2025, "totalAmount@1": 120, "totalAmount@2": 180, ... }
  ],
  "pivotMeta": {
    "columnField": "orderDate$month",
    "columnValues": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
    "valueFields": ["totalAmount"]
  }
}
```

---

## 7. 边界情况处理

| 场景 | 处理方式 |
|------|----------|
| 列轴值不连续（如只有1、3、5月有数据） | 只返回有数据的列，或通过 `fillValue` 填充 |
| 多值字段 | 列名格式：`{valueField}@{columnValue}` |
| 空结果 | 返回空 items，pivotMeta 包含空 columnValues |
| 列轴值过多（>100） | 建议限制或警告，避免列爆炸 |
| 行轴为空 | 所有数据聚合为一行 |

---

## 8. 与 MDX 的对比

| 特性 | MDX | Foggy Pivot |
|------|-----|-------------|
| 多轴支持 | ROWS, COLUMNS, PAGES, ... | rows, columns（2轴） |
| 层级展开 | 支持 | 暂不支持 |
| 计算成员 | 支持 | 通过 calculatedFields 实现 |
| 切片器 | SLICER | slice 过滤 |
| 复杂度 | 高 | 低，易于理解 |

---

## 9. 后续扩展方向

- [ ] 支持多列轴字段（如按年+季度展开）
- [ ] 支持层级展开（如产品类别 > 子类别）
- [ ] 支持小计/合计行列
- [ ] 支持自定义列名模板
- [ ] 支持列轴值预定义（确保列顺序和完整性）

---

## 10. 决策记录

| 日期 | 决策 | 原因 |
|------|------|------|
| 2025-01 | 采用 Step 模式实现 | 低侵入，符合现有架构 |
| 2025-01 | 响应格式采用方案 A（扁平化列名） | 简单，前端易处理 |
| 2025-01 | 暂不实现，等待用户反馈 | 避免过度设计 |

---

## 附录：相关文档

- [DSL 查询语法](https://foggy-projects.github.io/foggy-data-mcp-docs/zh/dataset-model/tm-qm/query-dsl)
- [DataSetResultStep 接口](../../foggy-dataset-model/src/main/java/com/foggyframework/dataset/db/model/plugins/result_set_filter/DataSetResultStep.java)
