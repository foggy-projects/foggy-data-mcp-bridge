# 元数据工具

元数据工具用于获取数据模型和字段的定义信息，是查询前了解数据结构的重要工具。

## get_metadata

获取用户可访问的所有数据模型列表。

### 基本信息

- **工具名称**: `dataset.get_metadata`
- **分类**: 元数据
- **权限**: Admin, Analyst

### 参数说明

此工具无需参数。

### 调用示例

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset.get_metadata",
    "arguments": {}
  }
}
```

### 响应格式

```json
{
  "success": true,
  "data": {
    "models": [
      {
        "name": "FactSalesQueryModel",
        "caption": "销售分析",
        "description": "销售订单明细数据"
      },
      {
        "name": "FactOrderQueryModel",
        "caption": "订单分析",
        "description": "订单信息查询"
      }
    ],
    "fieldIndex": {
      "customerName": ["FactSalesQueryModel", "FactOrderQueryModel"],
      "totalAmount": ["FactSalesQueryModel"]
    },
    "dictionaries": {
      "customerType": {
        "10": "个人客户",
        "20": "企业客户",
        "30": "VIP客户"
      }
    }
  }
}
```

### 返回内容

| 字段 | 说明 |
|------|------|
| `models` | 可用模型列表 |
| `fieldIndex` | 字段语义索引（字段在哪些模型中出现） |
| `dictionaries` | 字典附录（字段值映射） |

### 使用场景

- 用户询问"有哪些可用的数据模型"
- 查询前需要了解数据范围
- 探索不同模型间的字段关联

---

## describe_model_internal

获取指定模型的详细字段定义。

### 基本信息

- **工具名称**: `dataset.describe_model_internal`
- **分类**: 元数据
- **权限**: Admin, Analyst

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `model` | string | ✅ | 模型名称 |

### 调用示例

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "method": "tools/call",
  "params": {
    "name": "dataset.describe_model_internal",
    "arguments": {
      "model": "FactSalesQueryModel"
    }
  }
}
```

### 响应格式

```json
{
  "success": true,
  "data": {
    "model": "FactSalesQueryModel",
    "caption": "销售分析",
    "description": "销售订单明细数据分析",
    "fields": [
      {
        "name": "customer$id",
        "type": "DIMENSION",
        "caption": "客户ID",
        "description": "用于精确查询"
      },
      {
        "name": "customer$caption",
        "type": "DIMENSION",
        "caption": "客户名称",
        "description": "用于展示"
      },
      {
        "name": "totalAmount",
        "type": "MEASURE",
        "caption": "销售金额",
        "dataType": "MONEY",
        "aggregation": "SUM"
      },
      {
        "name": "orderStatus",
        "type": "ATTRIBUTE",
        "caption": "订单状态",
        "dictionary": "orderStatus"
      }
    ],
    "dictionaries": {
      "orderStatus": {
        "1": "待支付",
        "2": "已支付",
        "3": "已发货",
        "4": "已完成",
        "5": "已取消"
      }
    }
  }
}
```

### 字段类型

| 类型 | 说明 | 用法 |
|------|------|------|
| `DIMENSION` | 维度 | 分组、过滤，有 `$id` 和 `$caption` 变体 |
| `MEASURE` | 度量 | 聚合计算（SUM、AVG、COUNT 等） |
| `ATTRIBUTE` | 属性 | 普通字段，直接使用 |

### 维度字段变体

维度字段会返回两个变体：

| 变体 | 说明 | 使用场景 |
|------|------|----------|
| `xxx$id` | 返回 ID 值 | 精确过滤、关联查询 |
| `xxx$caption` | 返回显示名称 | 展示给用户 |

**示例：**
```json
{
  "columns": ["customer$caption"],  // 展示客户名称
  "slice": [
    {"field": "customer$id", "op": "=", "value": 1001}  // 按ID过滤
  ]
}
```

### 父子维度

层级结构维度额外支持 `$hierarchy$` 视角：

| 变体 | 说明 |
|------|------|
| `team$id` | 精确匹配该节点 |
| `team$hierarchy$id` | 匹配该节点及所有后代 |

**示例：** 查询总公司及所有子部门
```json
{
  "slice": [
    {"field": "team$hierarchy$id", "op": "=", "value": "T001"}
  ]
}
```

### 使用场景

- 查询前确认字段名和类型
- 了解字典字段的可选值
- 确认维度的 `$id` / `$caption` 用法

---

## 典型工作流

```
1. get_metadata
   → 获取所有可用模型列表

2. describe_model_internal
   → 选择目标模型，获取详细字段

3. query_model_v2
   → 使用正确的字段名执行查询
```

## 下一步

- [查询工具](./query.md) - 执行数据查询
- [自然语言查询](./nl-query.md) - 智能查询
- [工具概述](./overview.md) - 返回工具列表
